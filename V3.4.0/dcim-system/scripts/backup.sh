#!/bin/bash
# ============================================================
# PostgreSQL 定时备份（backup 容器 crond 调用，也可在宿主机手工执行）
#
# 用法: backup.sh [daily|weekly|monthly|manual]
#
# 备份对象：
#   * platform —— 新平台主库，必备
#   * dcim     —— 切换前的旧系统库（仅老环境存在）；存在才备份，新装环境自动跳过
#   可用 PGDATABASES="platform" 显式指定
#
# 每库产出两份：custom 格式（pg_restore 用，体积小）+ 纯 SQL（应急可读）。
# 保留策略：daily 30 天 / weekly 12 周 / monthly 12 个月；7 天前的文件 gzip 压缩。
# ============================================================
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/backups}"
PGHOST="${PGHOST:-postgres}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-dcim}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
RETENTION_WEEKLY="${RETENTION_WEEKLY:-12}"
RETENTION_MONTHLY="${RETENTION_MONTHLY:-12}"
export PGHOST PGPORT PGUSER

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_TYPE=${1:-daily}
case "$BACKUP_TYPE" in daily|weekly|monthly|manual) ;; *) echo "未知备份类型: $BACKUP_TYPE" >&2; exit 2 ;; esac
TARGET="${BACKUP_DIR}/${BACKUP_TYPE}"
mkdir -p "$TARGET"

if [ -z "${PGDATABASES:-}" ]; then
    PGDATABASES="platform"
    if psql -d platform -tAc "SELECT 1 FROM pg_database WHERE datname = 'dcim'" 2>/dev/null | grep -q 1; then
        PGDATABASES="platform dcim"
    fi
fi

echo "[$(date)] 开始 ${BACKUP_TYPE} 备份：${PGDATABASES}"
for db in ${PGDATABASES}; do
    f="${TARGET}/${db}_${TIMESTAMP}"
    # 先写临时文件，成功后再改名：避免半截文件被当成有效备份
    pg_dump -d "$db" -F c -b -f "${f}.dump.part"
    mv "${f}.dump.part" "${f}.dump"
    pg_dump -d "$db" -F p -b -f "${f}.sql.part"
    mv "${f}.sql.part" "${f}.sql"
    echo "[$(date)] ✓ ${db}: $(du -h "${f}.dump" | cut -f1) (custom) / $(du -h "${f}.sql" | cut -f1) (sql)"
done

# 7 天前的未压缩文件压缩
find "$TARGET" -type f \( -name '*.dump' -o -name '*.sql' \) -mtime +7 -exec gzip -f {} \;

case ${BACKUP_TYPE} in
    daily)   KEEP=$RETENTION_DAYS ;;
    weekly)  KEEP=$((RETENTION_WEEKLY * 7)) ;;
    monthly) KEEP=$((RETENTION_MONTHLY * 31)) ;;
    manual)  KEEP=3650 ;;
esac
find "$TARGET" -type f \( -name '*.dump*' -o -name '*.sql*' \) -mtime +"$KEEP" -delete

echo "[$(date)] 备份类型: ${BACKUP_TYPE}, 库: ${PGDATABASES}, 时间戳: ${TIMESTAMP}" >> "${BACKUP_DIR}/backup.log"
echo "[$(date)] 完成"
