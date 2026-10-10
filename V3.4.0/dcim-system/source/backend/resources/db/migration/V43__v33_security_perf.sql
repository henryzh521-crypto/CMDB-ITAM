-- ============================================================
-- V43 —— v3.3.0 安全与性能修复
--   1. try_inet：函数体内对 try_inet6 做 schema 限定。
--      pg_dump / pg_restore 以 search_path='' 执行，未限定的 try_inet6 解析失败，
--      导致 idx_asset_biz_inet / idx_asset_mgmt_inet 两个表达式索引恢复失败（v3.2 测试报告 P0-2）。
--      仍为可内联的 SQL 函数、IMMUTABLE，语义与旧版完全一致，已有索引无需重建。
--   2. users.token_valid_after：令牌吊销水位线。登出 / 改密 / 重置密码 / 停用 / 删除时推进，
--      签发时间早于水位线的 access token 一律失效（此前登出后 2 小时内旧令牌仍可用）。
--   3. job_lock：集群定时任务互斥（多实例部署时同一任务同一时刻只在一个实例上执行）。
--   4. 10 万级资产规模下的列表排序 / 全局搜索索引。
-- ============================================================

-- ── 1. try_inet schema 限定 ──
CREATE OR REPLACE FUNCTION public.try_inet6(t TEXT) RETURNS INET
    LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
BEGIN
    RETURN host(t::inet)::inet;
EXCEPTION WHEN others THEN
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.try_inet(t TEXT) RETURNS INET
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
SELECT CASE
         WHEN t ~ '^\s*((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])(/(3[0-2]|[12]?[0-9]))?\s*$'
           THEN host(btrim(t)::inet)::inet
         WHEN t ~ '^\s*[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*(/[0-9]{1,3})?\s*$'
           THEN public.try_inet6(btrim(t))
         ELSE NULL
       END
$$;

-- ── 2. 令牌吊销水位线 ──
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_valid_after TIMESTAMPTZ;
-- 新建账号默认以建号时刻为水位线：同名账号删除后重建，旧账号残留的令牌不会被新账号继承
ALTER TABLE users ALTER COLUMN token_valid_after SET DEFAULT NOW();
COMMENT ON COLUMN users.token_valid_after IS '早于该时刻签发的 access token 全部失效（登出/改密/重置/停用时推进）';

-- ── 3. 集群定时任务锁（ShedLock 语义：locked_until 为租约到期时刻）──
CREATE TABLE IF NOT EXISTS job_lock (
    name         VARCHAR(100) PRIMARY KEY,
    locked_until TIMESTAMPTZ  NOT NULL,
    locked_at    TIMESTAMPTZ  NOT NULL,
    locked_by    VARCHAR(255) NOT NULL
);
COMMENT ON TABLE job_lock IS '定时任务集群互斥锁：多实例部署时保证同一任务只在一个实例执行';

-- ── 4. 性能索引 ──
-- 资产台账默认排序（created_at DESC, asset_no DESC）：第一页由全表排序变为索引扫描
CREATE INDEX IF NOT EXISTS idx_asset_list_order ON asset (created_at DESC, asset_no DESC) WHERE deleted_at IS NULL;
-- 关键字 / 全局搜索：asset_no、管理/业务 IP 的模糊匹配（name、sn 已有 trgm 索引）
CREATE INDEX IF NOT EXISTS idx_asset_no_trgm   ON asset USING gin (asset_no gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_asset_mgmt_trgm ON asset USING gin (mgmt_ip gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_asset_biz_trgm  ON asset USING gin (biz_ip gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_asset_room      ON asset (room_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_asset_region    ON asset (region_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_asset_dept      ON asset (department) WHERE deleted_at IS NULL;
-- 工单关键字搜索（标题 / 单号）
CREATE INDEX IF NOT EXISTS idx_wo_title_trgm   ON workorder USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_wo_no_trgm      ON workorder USING gin (wo_no gin_trgm_ops);
-- 用户部门（数据范围 DEPT 判定）
CREATE INDEX IF NOT EXISTS idx_users_department ON users (department) WHERE deleted_at IS NULL;
