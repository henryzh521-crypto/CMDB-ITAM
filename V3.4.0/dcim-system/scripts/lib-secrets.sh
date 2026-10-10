#!/usr/bin/env bash
# ============================================================
# 密钥工具（install.sh / dcimctl 共用，需先定义 ENV_FILE、set_env、get_env）
#
# v3.2.0 安装包误把一套真实环境的 deploy/.env 一并打包外发，
# 用该包安装 / 升级的环境共用同一组密钥。这里按 SHA-256 指纹识别（只存摘要，不存原值）。
# ============================================================

# 键 → 已外泄值的 SHA-256
declare -A DCIM_LEAKED_FP=(
  [JWT_SECRET]=840534c11a20f5f19771a371e701f7e09a1aac41973f4e2900246b061fab3f64
  [DCIM_CRYPTO_KEY]=d8328efb13b1b17785f1a8b7e365ff6c41c9cd9691c530f50353b3da83f4d697
  [DCIM_AGENT_KEY]=e30bdadb6837ed2183e544a641c9b6989cf874af909570e7fc82791231620575
  [MONITORING_TOKEN]=76d9df2d9e97658d34d49f778b32e807b0421cdff0b6a4cd1622bed08f935a7d
  [PG_PASSWORD]=057b54080d4d40212bfd02b7a308c98dfe29717864d8f8e5a46760ec7b699a5e
  [REDIS_PASSWORD]=e7c5a269438a69d22f0cebb5f7330502e6d10fe6459637032e8e809ab50462a1
  [MINIO_SECRET_KEY]=945f15275c6322d99580f5512ab6c8545297185fc30db7ffefe394e80f016588
  [TDENGINE_PASSWORD]=dbd9c48105aa8ebc6ec6b92ab6bde28f9316d962e9a3a525c885c97aa6e30c30
  [GRAFANA_ADMIN_PASSWORD]=0a991e84360b681a67f316c07aafef86be77fe2f110fe502dbea4bcab288a7c2
)
# 随 v3.2.0 安装包外泄的 TLS 私钥（deploy/tls/server.key）SHA-256
DCIM_LEAKED_TLS_KEY_FP=e58f5ab9d31b1862a3123808df330d59d1bd6bb4445b674441b4ab1951610943
# 已知弱口令（管理员初始口令）；随 v3.2.0 外泄的那个只存 SHA-256
DCIM_WEAK_ADMIN_PW_RE='^(admin123|Admin123|123456|Passw0rd!?)$'
DCIM_LEAKED_ADMIN_PW_FP=ad89b64d66caa8e30e5d5ce4a9763f4ecc205814c412175f3e2c50027471426d

_sha256() { printf '%s' "$1" | sha256sum | cut -c1-64; }
# 管理员初始口令是否为已知弱口令（含 v3.2.0 外泄值）
weak_admin_pw() { [[ "$1" =~ $DCIM_WEAK_ADMIN_PW_RE ]] || [ "$(_sha256 "$1")" = "$DCIM_LEAKED_ADMIN_PW_FP" ]; }
_rand_hex() { openssl rand -hex "$1"; }
_rand_b64() { openssl rand -base64 "$1" | tr -d '\n'; }
_rand_pw() {
  local n="${1:-24}" p
  while :; do
    p="$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c "$n")"
    [[ "$p" =~ [A-Z] && "$p" =~ [a-z] && "$p" =~ [0-9] ]] && { echo "$p"; return; }
  done
}

# 输出 .env 中命中外泄指纹的键（空格分隔）
leaked_keys() {
  local k v out=()
  for k in "${!DCIM_LEAKED_FP[@]}"; do
    v="$(get_env "$k")"
    [ -n "$v" ] && [ "$(_sha256 "$v")" = "${DCIM_LEAKED_FP[$k]}" ] && out+=("$k")
  done
  echo "${out[*]:-}"
}

# 轮换应用层密钥 + Redis / PostgreSQL 口令（MinIO / TDengine / Grafana 只提示，需人工）
#   $1 = docker | native；docker 模式下 PG 口令通过运行中的 dcim-postgres 修改
rotate_secrets() {
  local mode="${1:-docker}" k changed=()
  local keys; keys="$(leaked_keys)"
  [ -n "$keys" ] || { echo "未发现外泄密钥"; return 0; }
  for k in $keys; do
    case "$k" in
      JWT_SECRET) set_env JWT_SECRET "$(_rand_hex 64)"; changed+=("JWT_SECRET（全体用户需重新登录）") ;;
      DCIM_AGENT_KEY) set_env DCIM_AGENT_KEY "$(_rand_hex 32)"; changed+=("DCIM_AGENT_KEY（异地部署的采集器需同步新值）") ;;
      MONITORING_TOKEN) set_env MONITORING_TOKEN "$(_rand_hex 32)"; changed+=("MONITORING_TOKEN（令牌文件随后自动同步）") ;;
      DCIM_CRYPTO_KEY)
        local old cur; cur="$(get_env DCIM_CRYPTO_KEY)"; old="$(get_env DCIM_CRYPTO_KEY_OLD)"
        set_env DCIM_CRYPTO_KEY_OLD "${old:+$old,}$cur"
        set_env DCIM_CRYPTO_KEY "$(_rand_b64 32)"
        changed+=("DCIM_CRYPTO_KEY（旧值已移入 DCIM_CRYPTO_KEY_OLD，后端启动时自动重加密 BMC 凭据 / Webhook 密钥）") ;;
      REDIS_PASSWORD) set_env REDIS_PASSWORD "$(_rand_pw 32)"; changed+=("REDIS_PASSWORD") ;;
      PG_PASSWORD)
        local npw; npw="$(_rand_pw 32)"
        if [ "$mode" = docker ] && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx dcim-postgres; then
          if docker exec -i dcim-postgres psql -v ON_ERROR_STOP=1 -U dcim -d postgres -c "ALTER USER dcim PASSWORD '${npw}'" >/dev/null; then
            set_env PG_PASSWORD "$npw"; changed+=("PG_PASSWORD")
          else
            echo "! PG_PASSWORD 修改失败，保持原值（请稍后执行 ./dcimctl rotate-secrets 重试）" >&2
          fi
        else
          echo "! dcim-postgres 未运行，PG_PASSWORD 本次未轮换（启动后执行 ./dcimctl rotate-secrets）" >&2
        fi ;;
      MINIO_SECRET_KEY|TDENGINE_PASSWORD|GRAFANA_ADMIN_PASSWORD)
        echo "! $k 与 v3.2.0 安装包外泄值相同，请按 docs/UPGRADE-3.3.md「密钥轮换」手工修改" >&2 ;;
    esac
  done
  if [ ${#changed[@]} -gt 0 ]; then
    echo "已轮换："
    printf '  - %s\n' "${changed[@]}"
  fi
}
