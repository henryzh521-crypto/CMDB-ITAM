-- ============================================================
-- V42  个人访问令牌（API Token）+ LDAP / AD 登录
-- ============================================================

-- 个人访问令牌：供脚本 / 第三方系统以某个用户身份调用 API（权限 = 该用户权限，可限只读）
-- 明文只在创建时返回一次，库中只存 SHA-256 摘要
CREATE TABLE IF NOT EXISTS api_token (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    token_hash    CHAR(64)     NOT NULL UNIQUE,
    token_prefix  VARCHAR(20)  NOT NULL,
    username      VARCHAR(50)  NOT NULL,
    read_only     BOOLEAN      NOT NULL DEFAULT TRUE,
    expires_at    TIMESTAMP,
    last_used_at  TIMESTAMP,
    last_used_ip  VARCHAR(64),
    created_by    VARCHAR(50),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    revoked_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_api_token_user ON api_token (username);

-- 账号来源：LOCAL 本地口令 / LDAP 目录认证（LDAP 账号不保存可用口令，改密在域控完成）
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_source VARCHAR(10) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_auth_source;
ALTER TABLE users ADD CONSTRAINT ck_users_auth_source CHECK (auth_source IN ('LOCAL', 'LDAP'));
