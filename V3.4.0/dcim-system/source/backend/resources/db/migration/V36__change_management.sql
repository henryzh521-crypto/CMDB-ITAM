CREATE TABLE IF NOT EXISTS change_request (
    id BIGSERIAL PRIMARY KEY,
    cr_no VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    change_type VARCHAR(50) NOT NULL,
    risk_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    impact_scope TEXT,
    rollback_plan TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    requester VARCHAR(50) NOT NULL,
    cab_approval BOOLEAN,
    cab_approvers TEXT[],
    cab_comment TEXT,
    scheduled_start TIMESTAMP,
    scheduled_end TIMESTAMP,
    actual_start TIMESTAMP,
    actual_end TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS change_asset (
    id BIGSERIAL PRIMARY KEY,
    cr_id BIGINT NOT NULL REFERENCES change_request(id),
    asset_no VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_change_request_status ON change_request(status);
CREATE INDEX IF NOT EXISTS idx_change_request_requester ON change_request(requester);
CREATE INDEX IF NOT EXISTS idx_change_asset_cr ON change_asset(cr_id);
CREATE INDEX IF NOT EXISTS idx_change_asset_asset ON change_asset(asset_no);
