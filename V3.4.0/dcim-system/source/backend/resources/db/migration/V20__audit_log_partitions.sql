-- V20: audit_log 按月分区滚动 + 存量数据迁入按月分区
--
-- 背景：audit_log 自建表起只有 audit_log_default 一个 DEFAULT 分区，所有行都堆在里面，
-- 时间范围查询无法做分区裁剪，数据量增长后按时间检索会全分区扫描。
-- 本迁移：
--   1) 定义幂等函数 dcim_audit_ensure_partition(月) / dcim_audit_ensure_partitions(提前月数)
--   2) DETACH DEFAULT → 按存量数据所在月份逐月建分区并把行迁回父表（自动落分区）
--      → 预建未来分区 → 重新 ATTACH DEFAULT（兜底：分区缺失时新行仍不丢）

-- 单月分区（幂等；返回分区名，无则返回 NULL）
CREATE OR REPLACE FUNCTION dcim_audit_ensure_partition(p_month timestamp)
RETURNS text AS $$
DECLARE
    m_start timestamp := date_trunc('month', p_month);
    m_end   timestamp := m_start + interval '1 month';
    part    text := 'audit_log_' || to_char(m_start, 'YYYY_MM');
BEGIN
    IF EXISTS (SELECT 1
               FROM pg_class c
               JOIN pg_inherits i ON i.inhrelid = c.oid
               WHERE i.inhparent = 'audit_log'::regclass AND c.relname = part) THEN
        RETURN NULL;
    END IF;
    EXECUTE format('CREATE TABLE %I PARTITION OF audit_log FOR VALUES FROM (%L) TO (%L)',
                   part, m_start, m_end);
    RETURN part;
END;
$$ LANGUAGE plpgsql;

-- 预建 [当月, 当月+ahead_months] 的按月分区（幂等），返回本次新建的分区名
CREATE OR REPLACE FUNCTION dcim_audit_ensure_partitions(ahead_months int DEFAULT 3)
RETURNS text[] AS $$
DECLARE
    i       int;
    created text[] := '{}';
    part    text;
BEGIN
    IF ahead_months IS NULL OR ahead_months < 0 THEN
        ahead_months := 3;
    END IF;
    FOR i IN 0..ahead_months LOOP
        -- 显式转 timestamp：date_trunc(now()) 是 timestamptz，函数参数为 timestamp
        --（timestamptz→timestamp 只有赋值转换，隐式转换不参与函数选择，必须显式）
        part := dcim_audit_ensure_partition(
                (date_trunc('month', now()) + make_interval(months => i))::timestamp);
        IF part IS NOT NULL THEN
            created := created || part;
        END IF;
    END LOOP;
    RETURN created;
END;
$$ LANGUAGE plpgsql;

-- 存量迁移：默认分区里的行按所在月份逐个建分区迁回
DO $$
DECLARE
    m          timestamp;
    moved      bigint;
    detached   boolean := false;
BEGIN
    -- 1) 摘下 DEFAULT（之后父表 audit_log 为空，可安全建分区）
    IF EXISTS (SELECT 1 FROM pg_inherits i
               JOIN pg_class c ON c.oid = i.inhrelid
               WHERE i.inhparent = 'audit_log'::regclass AND c.relname = 'audit_log_default') THEN
        EXECUTE 'ALTER TABLE audit_log DETACH PARTITION audit_log_default';
        detached := true;
    END IF;

    -- 2) 逐月建分区并迁回该月数据（分区键为 timestamptz，比较时按会话时区换算）
    IF detached THEN
        FOR m IN SELECT DISTINCT date_trunc('month', created_at)::timestamp
                 FROM audit_log_default
                 WHERE created_at IS NOT NULL
                 ORDER BY 1 LOOP
            PERFORM dcim_audit_ensure_partition(m);
            EXECUTE format('INSERT INTO audit_log SELECT * FROM audit_log_default WHERE date_trunc(%L, created_at) = %L',
                           'month', m);
            EXECUTE format('DELETE FROM audit_log_default WHERE date_trunc(%L, created_at) = %L',
                           'month', m);
        END LOOP;
    END IF;

    -- 3) 预建未来分区（含当月，确保当月数据有归属）
    PERFORM dcim_audit_ensure_partitions(3);

    -- 4) 重新挂回 DEFAULT 兜底
    IF detached AND to_regclass('audit_log_default') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE audit_log ATTACH PARTITION audit_log_default DEFAULT';
    END IF;

    SELECT count(*) INTO moved FROM audit_log;
    RAISE NOTICE 'audit_log 分区迁移完成，父表行数=%', moved;
END $$;
