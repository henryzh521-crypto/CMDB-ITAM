-- ============================================================
-- V7__wecom_login.sql — 企微移动端认证支持
--   1. users 增量列：wecom_userid（企业微信成员 userid，NULL=未绑定）
--   2. 部分唯一索引：仅对非空 wecom_userid 保证全局唯一
--      （允许多个用户同时未绑定；重复绑定同一企微账号由数据库拒绝）
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS wecom_userid VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_wecom
    ON users (wecom_userid)
    WHERE wecom_userid IS NOT NULL;
