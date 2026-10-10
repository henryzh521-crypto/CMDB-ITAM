#!/bin/sh
# ============================================================
# PostgreSQL 恢复验证脚本（双库：dcim / platform）
#
# 用法：
#   restore-verify.sh                 # 验证每个库各自最新的一份备份
#   restore-verify.sh dcim            # 只验证 dcim
#   restore-verify.sh platform        # 只验证 platform
#   restore-verify.sh /backups/daily/dcim_20260926_020000.dump   # 验证指定文件
#
# 环境变量：
#   BACKUP_DIR   备份根目录（默认 /backups）
#   PGHOST       默认 postgres
#   PGPORT       默认 5432
#   PGUSER       默认 dcim
#   MAX_AGE_DAYS 备份允许的最大天数（默认 7）
#
# 退出码：0 = 全部通过（可能含警告），1 = 存在失败项
#
# 说明：
#   * 关键表清单按库分别定义——旧脚本只写了 dcim 旧库表名（assets/racks/...），
#     验证 platform 备份时会全部显示“无数据”，误判为备份损坏。
#   * 使用 POSIX sh，既能在宿主机 bash 运行，也能在 backup 容器（alpine ash）运行。
# ============================================================

set -eu

BACKUP_DIR="${BACKUP_DIR:-/backups}"
PGHOST="${PGHOST:-postgres}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-dcim}"
MAX_AGE_DAYS="${MAX_AGE_DAYS:-7}"
STAMP="$(date +%Y%m%d_%H%M%S)"
LOG_FILE="${BACKUP_DIR}/restore-verify.log"

FAILURES=0
WARNINGS=0
CLEANUP_DBS=""
CLEANUP_DIRS=""

log() {
    printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$1" | tee -a "$LOG_FILE"
}

fail() {
    log "❌ $1"
    FAILURES=$((FAILURES + 1))
}

warn() {
    log "⚠️  $1"
    WARNINGS=$((WARNINGS + 1))
}

ok() {
    log "✅ $1"
}

# 每个库的“关键表”清单：用于恢复后逐表校验数据量
key_tables_for() {
    case "$1" in
        dcim)     echo "assets racks users workorders materials" ;;
        platform) echo "asset rack users workorder material" ;;
        *)        echo "" ;;
    esac
}

# 从备份文件名推断所属库：dcim_*.dump / platform_*.dump
db_of_file() {
    base="$(basename "$1")"
    case "$base" in
        dcim_*)     echo "dcim" ;;
        platform_*) echo "platform" ;;
        *)          echo "" ;;
    esac
}

# 在 BACKUP_DIR 下找某库最新的 .dump（找不到则找 .sql）
# 注：用 stat -c %Y 排序，兼容 GNU coreutils 与 alpine busybox（busybox 无 find -printf）
latest_backup_for() {
    db="$1"
    for ext in dump sql; do
        found="$(find "$BACKUP_DIR" -type f -name "${db}_*.${ext}" 2>/dev/null | while read -r f; do
                     printf '%s %s\n' "$(stat -c %Y "$f" 2>/dev/null || echo 0)" "$f"
                 done | sort -rn | head -1 | cut -d' ' -f2-)"
        if [ -n "$found" ]; then
            echo "$found"
            return 0
        fi
    done
    echo ""
}

cleanup() {
    for d in $CLEANUP_DBS; do
        log "清理临时数据库 $d ..."
        psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres \
             -c "DROP DATABASE IF EXISTS \"$d\";" >/dev/null 2>&1 || true
    done
    for d in $CLEANUP_DIRS; do
        rm -rf "$d" 2>/dev/null || true
    done
    log "清理完成（失败 $FAILURES，警告 $WARNINGS）"
}

trap cleanup EXIT

# ---------- 汇总统计（表存在性 + 恢复后行数 vs 线上行数） ----------
verify_one() {
    db="$1"
    backup_file="$2"
    verify_db="${db}_verify_${STAMP}"
    verify_dir="${BACKUP_DIR}/.verify_tmp_$$"

    log "---------- 库 [$db] ----------"
    log "备份文件: $backup_file"

    if [ ! -f "$backup_file" ]; then
        fail "[$db] 备份文件不存在"
        return 0
    fi

    size="$(du -h "$backup_file" | awk '{print $1}')"
    mtime="$(stat -c %y "$backup_file" 2>/dev/null | cut -d. -f1)"
    log "大小: $size，时间: $mtime"

    # 备份时效（直接用 epoch，避免依赖 GNU date -d）
    mtime_epoch="$(stat -c %Y "$backup_file" 2>/dev/null || echo 0)"
    age_days=$(( ( $(date +%s) - mtime_epoch ) / 86400 ))
    if [ "$age_days" -lt 0 ]; then
        age_days=0
    fi
    if [ "$age_days" -le "$MAX_AGE_DAYS" ]; then
        ok "[$db] 备份时效: ${age_days} 天前（阈值 ${MAX_AGE_DAYS}）"
    else
        warn "[$db] 备份时效: ${age_days} 天前，超过 ${MAX_AGE_DAYS} 天阈值"
    fi

    # 文件为空即失败
    if [ ! -s "$backup_file" ]; then
        fail "[$db] 备份文件为空"
        return 0
    fi

    # 建临时库
    log "创建临时库 $verify_db ..."
    CLEANUP_DBS="$CLEANUP_DBS $verify_db"
    if ! psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres \
              -c "CREATE DATABASE \"$verify_db\";" >/dev/null 2>&1; then
        fail "[$db] 创建临时库失败"
        return 0
    fi

# 恢复：先手动创建依赖函数，再恢复（忽略索引创建失败，事后补建）
    mkdir -p "$verify_dir"
    CLEANUP_DIRS="$CLEANUP_DIRS $verify_dir"
    log "恢复数据..."
    # 先创建 try_inet6 和 try_inet（使用 dump 中的确切定义，含 PARALLEL SAFE）
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$verify_db" -v ON_ERROR_STOP=1 >/dev/null 2>&1 <<'SQL'
CREATE OR REPLACE FUNCTION public.try_inet6(t text) RETURNS inet
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
BEGIN
    RETURN host(t::inet)::inet;
EXCEPTION WHEN others THEN
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.try_inet(t text) RETURNS inet
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT CASE
        WHEN t ~ '^\s*((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])(/(3[0-2]|[12]?[0-9]))?\s*$'
            THEN host(btrim(t)::inet)::inet
        WHEN t ~ '^\s*[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*(/[0-9]{1,3})?\s*$'
            THEN try_inet6(btrim(t))
        ELSE NULL
    END
$$;
SQL
    restore_ok=1
    case "$backup_file" in
        *.dump)
            # 恢复：忽略索引创建失败（事后补建）
            if ! pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" \
                            -d "$verify_db" --no-owner --no-privileges "$backup_file" >"$verify_dir/restore.log" 2>&1; then
                # 检查是否只是 "already exists" 或 "function does not exist" 索引错误
                if grep -q "already exists\|function try_inet6.*does not exist" "$verify_dir/restore.log"; then
                    # 只有其他错误才算失败
                    if grep -v "already exists\|function try_inet6.*does not exist" "$verify_dir/restore.log" | grep -q "ERROR:"; then
                        restore_ok=0
                        cat "$verify_dir/restore.log"
                    fi
                else
                    restore_ok=0
                    cat "$verify_dir/restore.log"
                fi
            fi
            # 事后补建两个依赖 try_inet 的索引
            psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$verify_db" -v ON_ERROR_STOP=1 >/dev/null 2>&1 <<'SQL'
CREATE INDEX IF NOT EXISTS idx_asset_biz_inet ON public.asset USING btree (public.try_inet((biz_ip)::text)) WHERE (deleted_at IS NULL);
CREATE INDEX IF NOT EXISTS idx_asset_mgmt_inet ON public.asset USING btree (public.try_inet((mgmt_ip)::text)) WHERE (deleted_at IS NULL);
SQL
            ;;
        *)
            if ! psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" \
                      -v ON_ERROR_STOP=1 -d "$verify_db" \
                      -f "$backup_file" >"$verify_dir/restore.log" 2>&1; then
                restore_ok=0
            fi
            ;;
    esac

    if [ "$restore_ok" -ne 1 ]; then
        fail "[$db] 恢复执行出错（详见日志尾部）"
        tail -5 "$verify_dir/restore.log" 2>/dev/null | sed 's/^/    /' | tee -a "$LOG_FILE" || true
        return 0
    fi
    ok "[$db] 恢复完成"

    # 表数量
    table_count="$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$verify_db" -t -A -c \
        "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE';" \
        2>/dev/null | tr -d '[:space:]')"
    log "表数量: ${table_count:-0}"

    # 逐个关键表校验（清单按库定义）
    for table in $(key_tables_for "$db"); do
        restored="$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$verify_db" -t -A -c \
            "SELECT count(*) FROM \"$table\";" 2>/dev/null | tr -d '[:space:]')"
        if [ -z "$restored" ]; then
            fail "[$db] 关键表 $table 不存在于备份中"
            continue
        fi

        live="$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$db" -t -A -c \
            "SELECT count(*) FROM \"$table\";" 2>/dev/null | tr -d '[:space:]')"
        live="${live:-0}"

        if [ "$restored" -eq 0 ] && [ "$live" -gt 0 ]; then
            fail "[$db] $table 恢复后 0 行，但线上有 $live 行"
        elif [ "$restored" -gt "$live" ]; then
            warn "[$db] $table 恢复后 $restored 行 > 线上 $live 行（备份早于线上删除，属正常）"
        else
            ok "[$db] $table: 恢复 $restored 行 / 线上 $live 行"
        fi
    done
}

# ---------- 入口 ----------
log "========== 恢复验证开始 =========="

target="${1:-all}"

case "$target" in
    dcim|platform)
        files="$(latest_backup_for "$target")"
        if [ -z "$files" ]; then
            fail "[$target] 未找到任何备份文件（$BACKUP_DIR/${target}_*.dump|sql）"
        else
            verify_one "$target" "$files"
        fi
        ;;
    all)
        found_any=0
        # platform 为主库必须有备份；dcim 为切换前的旧库（新装环境不存在），没有备份只提示不判失败
        for db in platform dcim; do
            f="$(latest_backup_for "$db")"
            if [ -n "$f" ]; then
                found_any=1
                verify_one "$db" "$f"
            elif [ "$db" = "platform" ]; then
                fail "[$db] 未找到任何备份文件（$BACKUP_DIR/${db}_*.dump|sql）"
            else
                log "[$db] 无旧库备份（新装环境正常），跳过"
            fi
        done
        [ "$found_any" -eq 1 ] || log "提示: BACKUP_DIR=$BACKUP_DIR 下无任何备份"
        ;;
    *)
        # 视为具体文件路径
        if [ -f "$target" ]; then
            db="$(db_of_file "$target")"
            if [ -z "$db" ]; then
                fail "无法从文件名识别所属库（期望 ${db}_*.dump 或 platform_*.dump）: $(basename "$target")"
            else
                verify_one "$db" "$target"
            fi
        else
            fail "参数无效（既不是 dcim/platform，也不是存在的文件）: $target"
        fi
        ;;
esac

log "========== 恢复验证完成 =========="
log "失败: $FAILURES，警告: $WARNINGS"

if [ "$FAILURES" -gt 0 ]; then
    log "结果: 失败"
    exit 1
fi
if [ "$WARNINGS" -gt 0 ]; then
    log "结果: 通过（有警告）"
    exit 0
fi
log "结果: 通过"
exit 0
