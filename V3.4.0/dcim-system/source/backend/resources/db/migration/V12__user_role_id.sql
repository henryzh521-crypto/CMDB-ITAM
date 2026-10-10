-- V12__user_role_id.sql — user_role 代理主键
-- 背景：MyBatis-Plus 要求实体有单列 @TableId；原表仅业务复合主键 (user_id, role_code)，
-- 启动时报 "Not found @TableId annotation" 警告，且 xxById 方法不可用。
-- 方案：增加自增 id 作为代理主键；保留 (user_id, role_code) 唯一约束与既有 FK。

ALTER TABLE user_role ADD COLUMN IF NOT EXISTS id BIGSERIAL;

-- 首次为已有行回填（BIGSERIAL 默认已为新行生成；重复执行安全）
UPDATE user_role SET id = nextval(pg_get_serial_sequence('user_role', 'id'))
WHERE id IS NULL;

-- 代理主键（幂等：若已存在同名约束则跳过）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'user_role_pkey_id' AND conrelid = 'user_role'::regclass
    ) THEN
        ALTER TABLE user_role DROP CONSTRAINT IF EXISTS user_role_pkey;
        ALTER TABLE user_role
            ADD CONSTRAINT user_role_pkey_id PRIMARY KEY (id);
    END IF;
END $$;

-- 业务唯一（幂等）
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_role_user_role
    ON user_role (user_id, role_code);

CREATE INDEX IF NOT EXISTS idx_user_role_role ON user_role (role_code);
