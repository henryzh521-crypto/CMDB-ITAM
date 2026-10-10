#!/bin/sh
# ============================================================
# init-databases.sh — postgres 官方镜像首次初始化时创建新平台库
# 挂载为 /docker-entrypoint-initdb.d/90-platform-db.sh（仅空数据目录首启时执行）
#
# 注意：若 pgdata 是旧部署遗留（已初始化、只有 dcim 库），本脚本不会执行，
# 需手工补一次：
#   docker exec -it dcim-postgres psql -U dcim -d dcim \
#     -c "CREATE DATABASE platform OWNER dcim;"
# （详见 deploy/CUTOVER.md 前置检查）
# ============================================================
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  SELECT 'CREATE DATABASE platform OWNER dcim'
  WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'platform')\gexec
EOSQL

echo "[init-databases] platform 数据库已就绪"
