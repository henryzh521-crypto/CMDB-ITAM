-- V37：软删除表的业务唯一键改为部分唯一索引（仅约束未删行）
--
-- 背景：V33 给 asset/users/roles/workorder/change_request/webhook_endpoint 加了
-- deleted_at，但业务唯一键仍是全表唯一。删一行（软删）后再建同业务键的行，
-- 应用层查重走 MyBatis-Plus（@TableLogic 自动排除已删行）会放行，
-- 随后撞 DB 唯一索引 → 500「违反唯一约束」。
--
-- 方案：
-- - 无外键依赖的业务唯一键 → 全量唯一索引改部分唯一索引
--   （WHERE deleted_at IS NULL，已删行不再占用业务键）。
-- - roles.role_code 被 user_role/role_module_perm/role_data_scope/role_field_perm/
--   role_action_perm 五个外键引用，PostgreSQL 外键必须依赖全量唯一约束，
--   无法改 partial 索引 → 保持现状，由应用层（RoleService.create）在重建同名
--   角色时先物理清理软删墓碑行。
-- - 主键（asset_no/wo_no/id）被外键引用且编号类主键本就不应复用，保持不变。

-- asset.sn（V1: sn VARCHAR(100) UNIQUE）
ALTER TABLE asset DROP CONSTRAINT IF EXISTS asset_sn_key;
CREATE UNIQUE INDEX IF NOT EXISTS asset_sn_key
    ON asset (sn)
    WHERE sn IS NOT NULL AND deleted_at IS NULL;

-- users.username（V1: username VARCHAR(50) NOT NULL UNIQUE）
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_username_key;
CREATE UNIQUE INDEX IF NOT EXISTS users_username_key
    ON users (username)
    WHERE deleted_at IS NULL;

-- users.wecom_userid（V7: 部分唯一索引，追加 deleted_at 条件）
DROP INDEX IF EXISTS ux_users_wecom;
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_wecom
    ON users (wecom_userid)
    WHERE wecom_userid IS NOT NULL AND deleted_at IS NULL;

-- change_request.cr_no（V36: cr_no VARCHAR(50) NOT NULL UNIQUE；外键只引用 id）
ALTER TABLE change_request DROP CONSTRAINT IF EXISTS change_request_cr_no_key;
CREATE UNIQUE INDEX IF NOT EXISTS change_request_cr_no_key
    ON change_request (cr_no)
    WHERE deleted_at IS NULL;
