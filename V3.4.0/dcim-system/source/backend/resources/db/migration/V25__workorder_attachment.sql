-- ============================================================
-- V25: 工单附件（通用归属设计 owner_type/owner_id，先只做工单）
-- ============================================================

CREATE TABLE attachment (
    id            BIGSERIAL PRIMARY KEY,
    owner_type    VARCHAR(32)  NOT NULL,              -- workorder（预留 asset / change ...）
    owner_id      VARCHAR(64)  NOT NULL,              -- 工单号 wo_no
    original_name VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100),
    size_bytes    BIGINT       NOT NULL,
    storage_key   VARCHAR(255) NOT NULL,              -- minio object key 或本地相对路径
    uploaded_by   VARCHAR(64),
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_attachment_owner ON attachment (owner_type, owner_id);

COMMENT ON TABLE attachment IS '附件（对象存储元数据；文件本体在 MinIO/本地目录，按 storage_key 寻址）';
COMMENT ON COLUMN attachment.owner_type IS '归属类型（workorder / 预留）';
COMMENT ON COLUMN attachment.owner_id IS '归属主键（工单号）';
COMMENT ON COLUMN attachment.storage_key IS '存储键：workorder/{woNo}/{uuid}.{ext}';
