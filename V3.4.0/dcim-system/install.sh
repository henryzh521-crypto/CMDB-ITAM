#!/usr/bin/env bash
# ============================================================
# 智算资产管理平台 v3.5 —— 一键安装 / 升级
#
# 用法（在解压后的安装包根目录执行，需 root 或 docker 组权限）：
#   ./install.sh                         全新安装（Docker Compose，推荐）
#   ./install.sh --upgrade-from /opt/dcim-system
#                                        从旧版本目录升级：复用旧的 deploy/.env、TLS 证书与数据目录
#   ./install.sh --native                原生部署（systemd + 已有 PostgreSQL/Redis，无 Docker 环境）
#
# 常用选项：
#   --host <IP或域名>    对外访问地址（写入 CORS 与自签证书 SAN），默认自动探测本机 IP
#   --data-dir <路径>    数据目录（默认 ./data）
#   --http-port <端口>   默认 80          --https-port <端口>  默认 443
#   --no-collector       不随栈启动采集器（采集器在其他管理节点部署时）
#   --no-monitoring      不启动内置监控栈（Prometheus / Alertmanager / Blackbox），已有监控系统时可用
#   --with-collector / --with-monitoring   重装 / 升级时显式启用（默认沿用原配置）
#   --yes                非交互（所有确认默认通过）
#   --skip-start         只生成配置，不启动
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"
VERSION="$(cat VERSION 2>/dev/null || echo 3.5.2)"

MODE=docker
UPGRADE_FROM=""
HOST=""
DATA_DIR=""
HTTP_PORT=80
HTTPS_PORT=443
COLLECTOR=""     # 空 = 新装默认启用；已有配置时沿用原选择
MONITORING=""    # 空 = 新装 / 从 v3.1 升级默认启用；已有 v3.2 配置时沿用
FRESH_ENV=0
ASSUME_YES=0
SKIP_START=0

c_red() { printf '\033[31m%s\033[0m\n' "$*"; }
c_grn() { printf '\033[32m%s\033[0m\n' "$*"; }
c_ylw() { printf '\033[33m%s\033[0m\n' "$*"; }
step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die() { c_red "✗ $*"; exit 1; }
confirm() {
  [ "$ASSUME_YES" = 1 ] && return 0
  read -r -p "$1 [y/N] " a
  [[ "$a" =~ ^[Yy]$ ]]
}

while [ $# -gt 0 ]; do
  case "$1" in
    --native) MODE=native ;;
    --upgrade-from) UPGRADE_FROM="${2:?缺少目录}"; shift ;;
    --host) HOST="${2:?}"; shift ;;
    --data-dir) DATA_DIR="${2:?}"; shift ;;
    --http-port) HTTP_PORT="${2:?}"; shift ;;
    --https-port) HTTPS_PORT="${2:?}"; shift ;;
    --no-collector) COLLECTOR=0 ;;
    --with-collector) COLLECTOR=1 ;;
    --no-monitoring) MONITORING=0 ;;
    --with-monitoring) MONITORING=1 ;;
    --yes|-y) ASSUME_YES=1 ;;
    --skip-start) SKIP_START=1 ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) die "未知参数: $1（--help 查看用法）" ;;
  esac
  shift
done

rand_hex() { openssl rand -hex "$1"; }
rand_b64() { openssl rand -base64 "$1" | tr -d '\n'; }
# 字母数字口令（避免特殊字符在 shell/URL/TDengine 中转义问题），保证含大小写与数字
rand_pw() {
  local n="${1:-24}" p
  while :; do
    p="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c "$n")"
    [[ "$p" =~ [A-Z] && "$p" =~ [a-z] && "$p" =~ [0-9] ]] && { echo "$p"; return; }
  done
}

detect_host() {
  if [ -n "$HOST" ]; then echo "$HOST"; return; fi
  local ip
  ip="$(ip route get 1.1.1.1 2>/dev/null | awk '/src/ {for (i=1;i<=NF;i++) if ($i=="src") print $(i+1)}' | head -n1)"
  [ -n "$ip" ] || ip="$(hostname -I 2>/dev/null | awk '{print $1}')"
  echo "${ip:-localhost}"
}

# ────────────────── 预检 ──────────────────
preflight_common() {
  step "环境预检"
  command -v openssl >/dev/null || die "缺少 openssl"
  [ -f "artifacts/backend/dcim-platform-${VERSION}.jar" ] || ls artifacts/backend/dcim-platform-*.jar >/dev/null 2>&1 \
    || die "未找到后端产物 artifacts/backend/（请使用完整安装包，或先执行 ./build.sh）"
  [ -f artifacts/web/dist/index.html ] || die "未找到前端产物 artifacts/web/dist（请使用完整安装包，或先执行 ./build.sh）"
  # v3.4：新包解压覆盖到旧目录时会残留旧版后端 jar（v3.3.0 的启动脚本会误用它），移到备份目录
  if [ -f "artifacts/backend/dcim-platform-${VERSION}.jar" ]; then
    local j stale=()
    for j in artifacts/backend/dcim-platform-*.jar; do
      [ "$(basename "$j")" = "dcim-platform-${VERSION}.jar" ] || stale+=("$j")
    done
    if [ ${#stale[@]} -gt 0 ]; then
      local sd; sd="artifacts/stale-jars-$(date +%Y%m%d%H%M%S)"
      mkdir -p "$sd" && mv "${stale[@]}" "$sd/"
      c_ylw "! 已把残留的旧版后端包移出 artifacts/backend/：${stale[*]##*/}"
    fi
  fi
  local mem_kb disk_kb
  mem_kb="$(awk '/MemTotal/ {print $2}' /proc/meminfo 2>/dev/null || echo 0)"
  [ "$mem_kb" -ge 3800000 ] || c_ylw "! 内存约 $((mem_kb/1024)) MB，建议 ≥ 4 GB（推荐 8 GB）"
  disk_kb="$(df -Pk "$ROOT" | awk 'NR==2 {print $4}')"
  [ "$disk_kb" -ge 10000000 ] || c_ylw "! 安装目录可用磁盘约 $((disk_kb/1024/1024)) GB，建议 ≥ 20 GB"
  c_grn "✓ 安装包完整（版本 $VERSION）"
}

port_busy() { (ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null) | awk '{print $4}' | grep -Eq "[:.]$1\$"; }

# ────────────────── Docker 模式 ──────────────────
ENV_FILE="$ROOT/deploy/.env"

set_env() { # set_env KEY VALUE —— 存在则替换，不存在则追加
  local k="$1" v="$2"
  if grep -qE "^${k}=" "$ENV_FILE"; then
    local esc; esc="$(printf '%s' "$v" | sed -e 's/[\/&|]/\\&/g')"
    sed -i "s|^${k}=.*|${k}=${esc}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$k" "$v" >> "$ENV_FILE"
  fi
}
get_env() { grep -E "^$1=" "$ENV_FILE" 2>/dev/null | tail -n1 | cut -d= -f2- || true; }
# shellcheck source=scripts/lib-secrets.sh
. "$ROOT/scripts/lib-secrets.sh"

# 外泄密钥检查：v3.2.0 安装包携带了一套真实环境的 .env，沿用它的环境必须轮换
check_leaked_secrets() {
  local keys; keys="$(leaked_keys)"
  [ -n "$keys" ] || return 0
  c_red "✗ 检测到与 v3.2.0 安装包一同外泄的密钥：$keys"
  echo "  持有该安装包的人可伪造任意用户登录令牌、解密已存的 BMC 凭据、冒充采集器。"
  echo "  v3.3 后端在生产模式下会拒绝使用这些密钥启动。"
  if confirm "现在自动轮换（JWT/加密密钥/采集器密钥/监控令牌/Redis/PostgreSQL 口令）？"; then
    rotate_secrets docker
    write_monitoring_token "$(get_env MONITORING_TOKEN)"
  else
    die "已取消。可稍后执行 ./dcimctl rotate-secrets；确需临时沿用请在 deploy/.env 设置 DCIM_ALLOW_LEAKED_SECRETS=true"
  fi
}

# 采集器源码目录随安装包提供；缺失时（例如 v3.2.0 安装包）不能构建 collector 镜像
collector_available() { [ -f "$ROOT/collector-agent/Dockerfile" ]; }

docker_preflight() {
  command -v docker >/dev/null || die "未安装 Docker（要求 Docker 20.10+ 与 Compose v2）。无 Docker 环境请使用 ./install.sh --native"
  docker info >/dev/null 2>&1 || die "无法连接 Docker 守护进程（是否已启动？当前用户是否在 docker 组？）"
  docker compose version >/dev/null 2>&1 || die "缺少 docker compose v2 插件"
  c_grn "✓ $(docker --version | cut -d, -f1)，$(docker compose version --short 2>/dev/null || echo compose v2)"
}

prepare_env() {
  step "生成运行配置 deploy/.env"
  if [ -n "$UPGRADE_FROM" ]; then
    [ -f "$UPGRADE_FROM/deploy/.env" ] || die "旧目录中没有 deploy/.env：$UPGRADE_FROM"
    if [ -f "$ENV_FILE" ] && ! cmp -s "$ENV_FILE" "$UPGRADE_FROM/deploy/.env"; then
      cp "$ENV_FILE" "$ENV_FILE.bak.$(date +%Y%m%d%H%M%S)"
    fi
    # 以新模板为底，逐项覆盖为旧环境的值（新增配置项取模板默认）
    cp deploy/.env.example "$ENV_FILE"
    while IFS= read -r line; do
      [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]] || continue
      set_env "${line%%=*}" "${line#*=}"
    done < "$UPGRADE_FROM/deploy/.env"
    # 旧 BASE_IMAGE 键 → 新键
    [ -n "$(get_env BASE_IMAGE)" ] && set_env BACKEND_BASE_IMAGE "$(get_env BASE_IMAGE)"
    set_env DCIM_VERSION "$VERSION"
    # 数据目录：原地复用旧数据（绝不移动/复制）
    local old_data; old_data="$(cd "$UPGRADE_FROM" && pwd)/data"
    [ -d "$old_data/pgdata" ] || die "旧目录中没有 data/pgdata：$old_data"
    set_env DATA_DIR "${DATA_DIR:-$old_data}"
    # 旧环境缺省为 minio，保持不变
    [ -n "$(get_env STORAGE_TYPE)" ] || set_env STORAGE_TYPE minio
    if [ -d "$UPGRADE_FROM/deploy/tls" ] && [ ! -f deploy/tls/server.crt ]; then
      if [ -f "$UPGRADE_FROM/deploy/tls/server.key" ] && \
         [ "$(sha256sum "$UPGRADE_FROM/deploy/tls/server.key" | cut -c1-64)" = "$DCIM_LEAKED_TLS_KEY_FP" ]; then
        c_ylw "! 旧环境的 TLS 私钥随 v3.2.0 安装包外泄，不再沿用，将重新生成自签证书（正式证书请重新签发后放入 deploy/tls/）"
      else
        mkdir -p deploy/tls && cp -p "$UPGRADE_FROM"/deploy/tls/* deploy/tls/
      fi
    fi
    # 采集器源码：新安装包未附带时沿用旧目录中的版本，避免升级后采集器被停用
    if [ ! -f collector-agent/Dockerfile ] && [ -f "$UPGRADE_FROM/collector-agent/Dockerfile" ]; then
      cp -a "$UPGRADE_FROM/collector-agent" ./collector-agent && c_grn "✓ 已沿用旧目录的 collector-agent/"
    fi
    c_grn "✓ 已沿用旧环境的全部密钥与数据目录：$(get_env DATA_DIR)"
  elif [ -f "$ENV_FILE" ]; then
    c_ylw "! deploy/.env 已存在，保留现有配置（重新生成请先删除该文件）"
    set_env DCIM_VERSION "$VERSION"
  else
    cp deploy/.env.example "$ENV_FILE"
    FRESH_ENV=1
    # 模板中的 COMPOSE_PROFILES 默认值不代表用户选择，新装按参数决定
    set_env COMPOSE_PROFILES ""
    local host; host="$(detect_host)"
    set_env PG_PASSWORD "$(rand_pw 32)"
    set_env REDIS_PASSWORD "$(rand_pw 32)"
    set_env JWT_SECRET "$(rand_hex 64)"
    set_env DCIM_CRYPTO_KEY "$(rand_b64 32)"
    set_env DCIM_AGENT_KEY "$(rand_hex 32)"
    set_env MINIO_ACCESS_KEY "dcim$(rand_hex 8)"
    set_env MINIO_SECRET_KEY "$(rand_pw 32)"
    set_env TDENGINE_PASSWORD "$(rand_pw 16)"
    set_env DCIM_ADMIN_INITIAL_PASSWORD "Dc$(rand_pw 14)#9"
    set_env CORS_ORIGINS "https://${host},http://${host},http://localhost"
    set_env DATA_DIR "${DATA_DIR:-../data}"
    c_grn "✓ 已生成随机强密钥（访问地址 ${host}）"
  fi
  set_env HTTP_PORT "$HTTP_PORT"
  set_env HTTPS_PORT "$HTTPS_PORT"
  # v3.3：容器部署默认禁止 Webhook 推送到平台自身所在的容器网段
  [ -n "$(get_env WEBHOOK_BLOCK_LOCAL_SUBNETS)" ] || set_env WEBHOOK_BLOCK_LOCAL_SUBNETS true
  if weak_admin_pw "$(get_env DCIM_ADMIN_INITIAL_PASSWORD)"; then
    c_ylw "! DCIM_ADMIN_INITIAL_PASSWORD 为常见弱口令，已替换为随机口令（仅首次建库生效）"
    set_env DCIM_ADMIN_INITIAL_PASSWORD "Dc$(rand_pw 14)#9"
  fi
  # 删除 v3.2 遗留的无效键
  sed -i '/^DCIM_AUTH_ADMIN_INITIAL_PASSWORD=/d' "$ENV_FILE"
  # 监控接入令牌（v3.2 新增；升级时旧 .env 没有则补生成）
  local had_monitoring_token=1
  case "$(get_env MONITORING_TOKEN)" in ""|CHANGEME) had_monitoring_token=0; set_env MONITORING_TOKEN "$(rand_hex 32)" ;; esac
  case "$(get_env GRAFANA_ADMIN_PASSWORD)" in ""|CHANGEME) set_env GRAFANA_ADMIN_PASSWORD "$(rand_pw 20)" ;; esac
  # COMPOSE_PROFILES：保留用户自加的可选项（ipmi / snmp / grafana），按参数增减 collector / monitoring
  local profiles=() p had_collector=0 had_monitoring=0
  IFS=',' read -r -a cur <<< "$(get_env COMPOSE_PROFILES)"
  for p in "${cur[@]:-}"; do
    p="${p// /}"
    [ "$p" = collector ] && had_collector=1
    [ "$p" = monitoring ] && had_monitoring=1
    [ -n "$p" ] && [ "$p" != collector ] && [ "$p" != monitoring ] && profiles+=("$p")
  done
  # 未显式指定时：新装默认启用；升级 / 重装沿用原选择（监控栈为 v3.2 新增，从 v3.1 升级默认启用）
  if [ -z "$COLLECTOR" ]; then
    if [ "$FRESH_ENV" = 1 ]; then COLLECTOR=1; else COLLECTOR=$had_collector; fi
  fi
  if [ "$COLLECTOR" = 1 ] && ! collector_available; then
    # v3.2：compose 引用 ../collector-agent 但安装包未包含，新装默认启用 collector 时镜像构建必然失败
    c_ylw "! 安装包未包含 collector-agent/（采集器源码），已跳过采集器；采集器可在管理节点单独部署"
    COLLECTOR=0
  fi
  if [ -z "$MONITORING" ]; then
    if [ "$FRESH_ENV" = 1 ] || [ "$had_monitoring_token" = 0 ]; then MONITORING=1; else MONITORING=$had_monitoring; fi
  fi
  [ "$COLLECTOR" = 1 ] && profiles+=(collector)
  [ "$MONITORING" = 1 ] && profiles+=(monitoring)
  set_env COMPOSE_PROFILES "$(IFS=,; echo "${profiles[*]:-}")"
  if [ "$MONITORING" = 1 ]; then
    [ -n "$(get_env PROMETHEUS_URL)" ] || set_env PROMETHEUS_URL "http://prometheus:9090"
  elif [ "$(get_env PROMETHEUS_URL)" = "http://prometheus:9090" ]; then
    # 未启用内置监控栈：清空默认地址（对接已有 Prometheus 时在 .env 或「监控设置」中填写）
    set_env PROMETHEUS_URL ""
  fi
  # v3.4：v3.3 及以前默认「每 IP 每分钟 600 次」，多人共用出口 IP 时容易误拦；仍是旧默认值的改为新的防洪闸默认值
  if [ "$(get_env RATE_LIMIT_PER_MINUTE)" = "600" ]; then
    set_env RATE_LIMIT_PER_MINUTE 3000
    c_ylw "! RATE_LIMIT_PER_MINUTE 由旧默认 600 调整为 3000（v3.4 起另有按用户的限流）"
  fi
  chmod 600 "$ENV_FILE"
  grep -qE "^[A-Z_]+=CHANGEME" "$ENV_FILE" && die "deploy/.env 仍有 CHANGEME 占位值，请补全后重试"
  [ "$(get_env DCIM_ALLOW_LEAKED_SECRETS)" = true ] || check_leaked_secrets
  return 0
}

prepare_tls() {
  step "准备 HTTPS 证书"
  mkdir -p deploy/tls
  if [ -f deploy/tls/server.crt ] && [ -f deploy/tls/server.key ]; then
    c_grn "✓ 使用已有证书 deploy/tls/server.crt（替换为正式证书后执行 ./dcimctl restart caddy）"
    return
  fi
  local host san ip
  host="$(detect_host)"
  san="DNS:localhost,IP:127.0.0.1"
  for ip in $(hostname -I 2>/dev/null); do san="$san,IP:$ip"; done
  if [[ "$host" =~ ^[0-9.]+$ ]]; then
    [[ "$san" == *"IP:$host"* ]] || san="$san,IP:$host"
  else
    san="$san,DNS:$host"
  fi
  san="$san,DNS:$(hostname)"
  openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 825 \
    -keyout deploy/tls/server.key -out deploy/tls/server.crt \
    -subj "/CN=${host}/O=DCIM" -addext "subjectAltName=${san}" >/dev/null 2>&1 \
    || die "自签证书生成失败（openssl 需 ≥ 1.1.1）"
  chmod 600 deploy/tls/server.key
  chmod 644 deploy/tls/server.crt
  c_grn "✓ 已生成自签证书（825 天，SAN=${san}）"
}

prepare_dirs() {
  step "准备数据目录"
  local d
  d="$(get_env DATA_DIR)"
  [[ "$d" = /* ]] || d="$ROOT/deploy/$d"
  mkdir -p "$d"/{pgdata,pg-backups,redis-data,minio-data,tdengine-data,tdengine-log,uploads,caddy-logs}
  # bitnami minio 以 1001 运行；后端以 10001 运行（上传目录）
  chown -R 1001:1001 "$d/minio-data" 2>/dev/null || true
  chown -R 10001:10001 "$d/uploads" 2>/dev/null || true
  chmod +x scripts/*.sh deploy/init-databases.sh deploy/docker/*.sh dcimctl 2>/dev/null || true
  [ -d tools/monitoring ] && chmod +x tools/monitoring/*.sh 2>/dev/null || true
  # 监控栈：prometheus / alertmanager 以 nobody(65534) 运行，grafana 以 472 运行
  mkdir -p "$d"/{prometheus-data,alertmanager-data,grafana-data}
  chown -R 65534:65534 "$d/prometheus-data" "$d/alertmanager-data" 2>/dev/null || true
  chown -R 472:472 "$d/grafana-data" 2>/dev/null || true
  c_grn "✓ 数据目录：$(cd "$d" && pwd)"
}

prepare_monitoring() {
  step "准备监控配置"
  write_monitoring_token "$(get_env MONITORING_TOKEN)"
  [ -f deploy/monitoring/ipmi_remote.yml ] || cp deploy/monitoring/ipmi_remote.yml.example deploy/monitoring/ipmi_remote.yml
  if [ "$MONITORING" = 1 ]; then
    c_grn "✓ 监控栈随平台启动（Prometheus 自动从 CMDB 发现监控目标；告警进入「监控告警 → 告警中心」）"
  else
    c_ylw "! 已跳过内置监控栈；外部监控系统可按「监控设置 → 接入说明」对接"
  fi
}

# 令牌文件：Prometheus / Alertmanager 容器以 nobody(65534) 只读挂载
write_monitoring_token() {
  local tok="$1" f=deploy/monitoring/secrets/monitoring_token
  [ -n "$tok" ] || die "MONITORING_TOKEN 为空"
  mkdir -p deploy/monitoring/secrets
  ( umask 077; printf '%s' "$tok" > "$f" )
  chown 65534:65534 "$f" 2>/dev/null || chmod 644 "$f"
  chmod 400 "$f" 2>/dev/null || true
  # 非 root 安装无法 chown 时，容器内 nobody 需要可读
  [ "$(stat -c %u "$f" 2>/dev/null)" = 65534 ] || chmod 644 "$f"
}

check_ports() {
  local p
  for p in "$HTTP_PORT" "$HTTPS_PORT"; do
    if port_busy "$p" && ! docker ps --format '{{.Names}}' | grep -q '^dcim-caddy$'; then
      die "端口 $p 已被占用（可用 --http-port / --https-port 指定其他端口）"
    fi
  done
}

docker_up() {
  local DC=(docker compose --env-file "$ENV_FILE" -f deploy/docker-compose.yml)
  step "校验编排文件"
  "${DC[@]}" config -q || die "docker-compose 配置校验失败"
  # 先构建镜像（升级时旧栈仍在服务，构建失败不会造成停机）
  step "构建镜像（使用预编译产物，首次需拉取基础镜像）"
  "${DC[@]}" build || die "镜像构建失败（检查 IMAGE_REGISTRY / *_BASE_IMAGE 是否可拉取；离线环境见 docs/DEPLOY.md「离线安装」）"
  if [ -n "$UPGRADE_FROM" ]; then
    step "升级前全量备份"
    if docker ps --format '{{.Names}}' | grep -q '^dcim-postgres$'; then
      local stamp; stamp="$(date +%Y%m%d_%H%M%S)"
      if docker exec -i -e STAMP="$stamp" dcim-postgres sh -s <<'SH'
set -e
mkdir -p /backups/manual
for db in $(psql -U dcim -d postgres -tAc "SELECT datname FROM pg_database WHERE datname IN ('platform','dcim')"); do
  pg_dump -U dcim -d "$db" -F c -f "/backups/manual/${db}_pre_upgrade_${STAMP}.dump"
  echo "  ✓ ${db} → pg-backups/manual/${db}_pre_upgrade_${STAMP}.dump"
done
SH
      then c_grn "✓ 升级前备份完成"
      else c_red "备份失败"; confirm "仍然继续升级？" || exit 1
      fi
    else
      c_ylw "! 未检测到运行中的 dcim-postgres，跳过升级前备份（请确认已有可用备份）"
      confirm "继续升级？" || exit 1
    fi
    step "停止旧版本容器（数据目录原样保留）"
    if [ -f "$UPGRADE_FROM/deploy/docker-compose.platform.yml" ]; then
      (cd "$UPGRADE_FROM" && docker compose --env-file deploy/.env -f deploy/docker-compose.platform.yml down --remove-orphans) || true
    fi
  fi
  step "启动服务"
  "${DC[@]}" up -d --remove-orphans
  step "等待后端就绪（首次启动会执行数据库迁移，约 1-3 分钟）"
  local i
  for i in $(seq 1 90); do
    if docker exec dcim-platform-be /app/healthcheck.sh >/dev/null 2>&1; then
      c_grn "✓ 后端健康"
      break
    fi
    sleep 4
    [ "$i" = 90 ] && { "${DC[@]}" ps; die "后端 6 分钟内未就绪，请执行 ./dcimctl logs platform-be 查看原因"; }
  done
  pin_images
  "${DC[@]}" ps
}

# v3.4：把仍用 latest 标签的镜像锁定为当前已拉取的摘要，避免日后重建时悄悄换版本（当时仅 MinIO）
# v3.5.2：推广到全部外部镜像——固定版本 tag 同样可能被 registry 端覆盖指向，摘要不可变。
# 首次安装后把各镜像锁定为实际拉取的 RepoDigest 写入 .env（*_IMAGE 变量）；
# 已是 @sha256: 引用的跳过。升级镜像版本：删掉 .env 中对应 *_IMAGE 行后重新拉取，
# 或改为新 tag 再执行本脚本（见 docs/UPGRADE-3.5.2.md）。
pin_images() {
  local e svc var cur ref digest
  local entries=(
    "postgres|POSTGRES_IMAGE"
    "redis|REDIS_IMAGE"
    "tdengine|TDENGINE_IMAGE"
    "caddy|CADDY_IMAGE"
    "prometheus|PROMETHEUS_IMAGE"
    "alertmanager|ALERTMANAGER_IMAGE"
    "blackbox-exporter|BLACKBOX_IMAGE"
    "ipmi-exporter|IPMI_EXPORTER_IMAGE"
    "snmp-exporter|SNMP_EXPORTER_IMAGE"
    "grafana|GRAFANA_IMAGE"
  )
  for e in "${entries[@]}"; do
    svc="${e%%|*}"; var="${e##*|}"
    cur="$(get_env "$var")"
    case "$cur" in *@sha256:*) continue ;; esac
    # 从运行中的容器取 compose 解析后的完整镜像引用（含用户自设的 IMAGE_REGISTRY 前缀），再取其仓库摘要
    ref="$(docker inspect "dcim-$svc" --format '{{.Config.Image}}' 2>/dev/null)"
    [ -z "$ref" ] && continue
    digest="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$ref" 2>/dev/null | head -n1)"
    if [ -n "$digest" ]; then
      set_env "$var" "$digest"
      c_grn "✓ $svc 镜像已锁定为当前版本：$digest"
    fi
  done
  # MinIO（bitnamilegacy 仓库只提供 latest 标签，v3.4 起锁定）
  cur="$(get_env MINIO_IMAGE)"
  case "$cur" in
    *@sha256:*) ;;
    *)
      ref="${cur:-docker.1ms.run/bitnamilegacy/minio:latest}"
      digest="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$ref" 2>/dev/null | head -n1)"
      if [ -n "$digest" ]; then
        set_env MINIO_IMAGE "$digest"
        c_grn "✓ MinIO 镜像已锁定为当前版本：$digest"
      fi
      ;;
  esac
}

summary_docker() {
  local host pw
  host="$(detect_host)"
  pw="$(get_env DCIM_ADMIN_INITIAL_PASSWORD)"
  echo
  c_grn "================ 安装完成 ================"
  echo "访问地址：  https://${host}$( [ "$HTTPS_PORT" != 443 ] && echo ":$HTTPS_PORT")   （自签证书首次访问需在浏览器确认）"
  echo "           http://${host}$( [ "$HTTP_PORT" != 80 ] && echo ":$HTTP_PORT")"
  if [ -z "$UPGRADE_FROM" ] && [ -n "$pw" ]; then
    echo "管理员账号：admin"
    echo "初始密码：  ${pw}    （首次登录强制修改；修改后请从 deploy/.env 删除 DCIM_ADMIN_INITIAL_PASSWORD）"
  fi
  if [[ ",$(get_env COMPOSE_PROFILES)," == *",monitoring,"* ]]; then
    echo "监控告警：  平台菜单「监控告警」；被监控服务器部署 node_exporter（默认端口 9100）后在资产上标记即可自动发现"
  fi
  echo "运维命令：  ./dcimctl status | logs | backup | health | upgrade | monitoring（./dcimctl help 查看全部）"
  echo "密钥文件：  deploy/.env（0600，请妥善备份，DCIM_CRYPTO_KEY 丢失将无法解密已存凭据）"
}

# ────────────────── 原生模式 ──────────────────
native_install() {
  step "原生部署预检"
  command -v java >/dev/null || die "未找到 java（需要 JDK/JRE 17+）"
  local jv; jv="$(java -version 2>&1 | awk -F'"' '/version/ {print $2}' | cut -d. -f1)"
  [ "${jv:-0}" -ge 17 ] || die "Java 版本过低（$jv），需要 17+"
  command -v systemctl >/dev/null || c_ylw "! 未检测到 systemd，将只生成配置，请自行以 deploy/native/run-backend.sh 启动"
  c_grn "✓ Java $jv"

  local envf=/etc/dcim/platform.env
  mkdir -p /etc/dcim
  if [ ! -f "$envf" ]; then
    local db_url db_user db_pass redis_host redis_port redis_pass
    read -r -p "PostgreSQL JDBC URL [jdbc:postgresql://127.0.0.1:5432/platform]: " db_url
    read -r -p "PostgreSQL 用户 [dcim]: " db_user
    read -r -s -p "PostgreSQL 口令: " db_pass; echo
    read -r -p "Redis 地址（留空则不用 Redis，会话存内存、单实例） [127.0.0.1]: " redis_host
    read -r -p "Redis 端口 [6379]: " redis_port
    read -r -s -p "Redis 口令（无则回车）: " redis_pass; echo
    umask 077
    sed -e "s|@DB_URL@|${db_url:-jdbc:postgresql://127.0.0.1:5432/platform}|" \
        -e "s|@DB_USER@|${db_user:-dcim}|" \
        -e "s|@DB_PASS@|${db_pass}|" \
        -e "s|@REDIS_HOST@|${redis_host-127.0.0.1}|" \
        -e "s|@REDIS_PORT@|${redis_port:-6379}|" \
        -e "s|@REDIS_PASS@|${redis_pass}|" \
        -e "s|@JWT_SECRET@|$(rand_hex 64)|" \
        -e "s|@CRYPTO_KEY@|$(rand_b64 32)|" \
        -e "s|@AGENT_KEY@|$(rand_hex 32)|" \
        -e "s|@ADMIN_PW@|Dc$(rand_pw 10)#9|" \
        -e "s|@MONITORING_TOKEN@|$(rand_hex 32)|" \
        -e "s|@UPLOAD_DIR@|${DATA_DIR:-/var/lib/dcim/uploads}|" \
        -e "s|@CORS@|https://$(detect_host),http://$(detect_host)|" \
        deploy/native/platform.env.template > "$envf"
    c_grn "✓ 已生成 $envf（0600）"
  else
    c_ylw "! 保留已有 $envf"
    local k v
    for k in JWT_SECRET DCIM_CRYPTO_KEY DCIM_AGENT_KEY MONITORING_TOKEN; do
      v="$(grep -E "^$k=" "$envf" | tail -n1 | cut -d= -f2-)"
      if [ -n "$v" ] && [ "$(printf '%s' "$v" | sha256sum | cut -c1-64)" = "${DCIM_LEAKED_FP[$k]}" ]; then
        c_red "✗ $envf 中的 $k 与 v3.2.0 安装包外泄值相同，后端将拒绝启动，请更换（加密密钥更换方法见 docs/UPGRADE-3.3.md）"
      fi
    done
  fi
  # shellcheck disable=SC1090
  mkdir -p "$(. "$envf"; echo "${UPLOAD_DIR:-/var/lib/dcim/uploads}")"
  id dcim >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin -d /var/lib/dcim dcim 2>/dev/null || true
  chown -R dcim: "$(. "$envf"; echo "${UPLOAD_DIR:-/var/lib/dcim/uploads}")" 2>/dev/null || true
  chmod +x deploy/native/run-backend.sh
  # 监控接入令牌：旧版 platform.env 没有则补上；同时写出供 Prometheus/Alertmanager 使用的令牌文件
  if ! grep -q '^MONITORING_TOKEN=' "$envf"; then
    printf '\n# 监控接入令牌（Prometheus 服务发现 / Alertmanager 回调）\nMONITORING_TOKEN=%s\nPROMETHEUS_URL=\n' "$(rand_hex 32)" >> "$envf"
  fi
  write_monitoring_token "$(grep -E '^MONITORING_TOKEN=' "$envf" | tail -n1 | cut -d= -f2-)"

  if command -v systemctl >/dev/null; then
    sed "s|@ROOT@|$ROOT|g" deploy/native/dcim-platform.service > /etc/systemd/system/dcim-platform.service
    systemctl daemon-reload
    systemctl enable dcim-platform >/dev/null 2>&1 || true
    [ "$SKIP_START" = 1 ] || systemctl restart dcim-platform
    c_grn "✓ systemd 服务 dcim-platform 已安装"
  fi
  if command -v nginx >/dev/null; then
    sed -e "s|@WEB_ROOT@|$ROOT/artifacts/web/dist|g" deploy/native/nginx-dcim.conf > /etc/nginx/conf.d/dcim.conf
    nginx -t && (systemctl reload nginx 2>/dev/null || nginx -s reload) && c_grn "✓ nginx 站点已配置：/etc/nginx/conf.d/dcim.conf"
  else
    c_ylw "! 未检测到 nginx：请参考 deploy/native/nginx-dcim.conf 配置前端静态站点与 /api 反向代理"
  fi
  echo
  c_grn "================ 原生部署完成 ================"
  echo "后端日志：journalctl -u dcim-platform -f"
  echo "管理员：admin / 初始密码见 $envf 中 DCIM_ADMIN_INITIAL_PASSWORD（首次登录强制修改）"
  echo "监控：  原生模式不内置 Prometheus。如需监控，自行安装 Prometheus/Alertmanager（配置参考 deploy/monitoring/），"
  echo "        令牌文件已生成：deploy/monitoring/secrets/monitoring_token，并在 $envf 设置 PROMETHEUS_URL"
}

# ────────────────── 主流程 ──────────────────
echo "智算资产管理平台 安装程序 v${VERSION}（模式：${MODE}${UPGRADE_FROM:+，升级自 $UPGRADE_FROM}）"
preflight_common
if [ "$MODE" = native ]; then
  native_install
  exit 0
fi
docker_preflight
[ -z "$UPGRADE_FROM" ] && check_ports
prepare_env
prepare_tls
prepare_dirs
prepare_monitoring
if [ "$SKIP_START" = 1 ]; then
  c_grn "✓ 配置已生成（--skip-start），启动请执行 ./dcimctl start"
  exit 0
fi
docker_up
summary_docker
