#!/bin/bash
# ============================================================
# 从备份恢复 platform 库（危险操作：会覆盖当前数据）
#
# 用法（宿主机，安装目录下执行）:
#   ./dcimctl restore data/pg-backups/daily/platform_20261001_020000.dump
#
# 流程：校验文件 → 停后端 → 现库改名为 platform_before_restore_<时间>（保留，可回退）
#      → 新建空库 → pg_restore → 启动后端（Flyway 自动补齐更高版本的迁移）
# ============================================================
set -euo pipefail
FILE="${1:-}"
[ -f "$FILE" ] || { echo "用法: restore.sh <platform_xxx.dump|.sql|.gz>"; exit 2; }
COMPOSE="${COMPOSE:-docker compose --env-file deploy/.env -f deploy/docker-compose.yml}"
STAMP=$(date +%Y%m%d_%H%M%S)

read -r -p "将用 $FILE 覆盖当前 platform 库（现库会保留为 platform_before_restore_$STAMP）。输入 YES 继续: " ans
[ "$ans" = "YES" ] || { echo "已取消"; exit 1; }

$COMPOSE stop platform-be collector-agent >/dev/null 2>&1 || true
PSQL="$COMPOSE exec -T postgres psql -U dcim -d postgres -v ON_ERROR_STOP=1"
$PSQL -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='platform' AND pid <> pg_backend_pid();" >/dev/null
$PSQL -c "ALTER DATABASE platform RENAME TO platform_before_restore_$STAMP;"
$PSQL -c "CREATE DATABASE platform OWNER dcim;"

# v3.2 及更早的备份里 try_inet 函数体引用了未限定 schema 的 try_inet6，pg_restore（search_path=''）
# 重建两个表达式索引时会失败；而本脚本 set -e，pg_restore 非零退出即中断，后端停在关机状态。
# 处理：恢复前先建好 schema 限定版本的函数，恢复中忽略这两个已知错误，恢复后补建索引。
PLATFORM_PSQL="$COMPOSE exec -T postgres psql -U dcim -d platform -v ON_ERROR_STOP=1 -q"
$PLATFORM_PSQL <<'SQL' >/dev/null
CREATE OR REPLACE FUNCTION public.try_inet6(t text) RETURNS inet LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
BEGIN RETURN host(t::inet)::inet; EXCEPTION WHEN others THEN RETURN NULL; END; $$;
CREATE OR REPLACE FUNCTION public.try_inet(t text) RETURNS inet LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
SELECT CASE
  WHEN t ~ '^\s*((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])(/(3[0-2]|[12]?[0-9]))?\s*$' THEN host(btrim(t)::inet)::inet
  WHEN t ~ '^\s*[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*(/[0-9]{1,3})?\s*$' THEN public.try_inet6(btrim(t))
  ELSE NULL END $$;
SQL

LOG="$(mktemp)"
set +e
case "$FILE" in
  *.dump)    $COMPOSE exec -T postgres pg_restore -U dcim -d platform --no-owner < "$FILE" 2>"$LOG" ;;
  *.dump.gz) gunzip -c "$FILE" | $COMPOSE exec -T postgres pg_restore -U dcim -d platform --no-owner 2>"$LOG" ;;
  *.sql)     $COMPOSE exec -T postgres psql -U dcim -d platform -q < "$FILE" 2>"$LOG" ;;
  *.sql.gz)  gunzip -c "$FILE" | $COMPOSE exec -T postgres psql -U dcim -d platform -q 2>"$LOG" ;;
  *) echo "不支持的文件类型: $FILE"; set -e; exit 2 ;;
esac
set -e
# 已知可忽略：函数已存在（上面预建）、try_inet 相关索引（下面补建）
UNEXPECTED="$(grep -E "ERROR" "$LOG" | grep -vE "already exists|try_inet|idx_asset_(biz|mgmt)_inet" || true)"
$PLATFORM_PSQL <<'SQL' >/dev/null || echo "! 表达式索引补建失败（不影响数据，可稍后手工重建）"
CREATE INDEX IF NOT EXISTS idx_asset_biz_inet ON public.asset USING btree (public.try_inet((biz_ip)::text)) WHERE (deleted_at IS NULL);
CREATE INDEX IF NOT EXISTS idx_asset_mgmt_inet ON public.asset USING btree (public.try_inet((mgmt_ip)::text)) WHERE (deleted_at IS NULL);
SQL
if [ -n "$UNEXPECTED" ]; then
  echo "! 恢复过程中出现以下错误（数据可能不完整，原库保留为 platform_before_restore_$STAMP）："
  echo "$UNEXPECTED" | head -20
fi
rm -f "$LOG"

# 清除 Redis 中缓存的用户权限快照（TTL 30 分钟），否则恢复后仍按恢复前的角色/改密状态放行
$COMPOSE exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning --scan --pattern "dcim:loginuser:*" | xargs -r redis-cli -a "$REDIS_PASSWORD" --no-auth-warning del' >/dev/null || true

$COMPOSE start platform-be collector-agent
echo "恢复完成。确认无误后可删除旧库： $COMPOSE exec postgres dropdb -U dcim platform_before_restore_$STAMP"
