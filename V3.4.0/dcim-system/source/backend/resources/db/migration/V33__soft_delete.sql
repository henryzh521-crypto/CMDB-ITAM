-- 软删除：为关键业务表添加 deleted_at 列
-- 合规要求：资产/用户/角色/工单删除后可恢复、可审计

ALTER TABLE asset ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE roles ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_asset_deleted_at ON asset(deleted_at);
CREATE INDEX IF NOT EXISTS idx_users_deleted_at ON users(deleted_at);
CREATE INDEX IF NOT EXISTS idx_roles_deleted_at ON roles(deleted_at);
CREATE INDEX IF NOT EXISTS idx_workorder_deleted_at ON workorder(deleted_at);
