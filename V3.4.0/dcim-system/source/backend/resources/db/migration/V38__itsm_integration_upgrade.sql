-- ============================================================
-- V38__itsm_integration_upgrade.sql — v3.1 迭代：集成与 ITSM 能力补全
--
-- 1. Webhook：投递队列化（重试 + 退避 + 死信）、IM 机器人格式、端点健康状态
-- 2. 变更管理：审批人/审批时间/关闭说明/取消原因；变更-资产关联唯一
-- 3. CI 关系：活动关系去重（部分唯一索引）、创建人与备注
-- 4. 巡检计划：按计划周期自动生成「巡检」工单
-- 5. 清理 V34 重复建的索引（与 V1 / 主键完全同列，白占写放大与存储）
-- 6. 只读角色 VIEWER 补齐「变更管理」模块读权限（V28 种子早于 changes 模块）
--
-- 全部语句可重复执行（IF NOT EXISTS / IF EXISTS / ON CONFLICT）。
-- ============================================================

-- ──────────────────── 1. Webhook ────────────────────
ALTER TABLE webhook_endpoint ADD COLUMN IF NOT EXISTS format VARCHAR(20) NOT NULL DEFAULT 'GENERIC';
ALTER TABLE webhook_endpoint ADD COLUMN IF NOT EXISTS last_status VARCHAR(20);
ALTER TABLE webhook_endpoint ADD COLUMN IF NOT EXISTS last_delivery_at TIMESTAMP;
ALTER TABLE webhook_endpoint ADD COLUMN IF NOT EXISTS consecutive_failures INT NOT NULL DEFAULT 0;
-- secret 改为 AES-GCM 密文存储（CryptBox，base64(IV+密文)），长度放宽
ALTER TABLE webhook_endpoint ALTER COLUMN secret TYPE VARCHAR(512);
COMMENT ON COLUMN webhook_endpoint.format IS 'GENERIC=标准 JSON+HMAC 签名 / WECOM_BOT=企业微信群机器人 / DINGTALK_BOT=钉钉群机器人 / FEISHU_BOT=飞书群机器人';
COMMENT ON COLUMN webhook_endpoint.secret IS 'HMAC 签名密钥（CryptBox 加密存储，接口永不回显）';

ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS event_id VARCHAR(40);
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMP NOT NULL DEFAULT NOW();
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS response_code INT;
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS response_snippet VARCHAR(500);
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS duration_ms INT;
ALTER TABLE webhook_delivery ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT NOW();
COMMENT ON COLUMN webhook_delivery.status IS 'PENDING=待投递 / SUCCESS=成功 / RETRY=失败待重试 / DEAD=重试耗尽';
CREATE INDEX IF NOT EXISTS idx_webhook_delivery_due ON webhook_delivery (status, next_attempt_at)
    WHERE status IN ('PENDING', 'RETRY');

-- ──────────────────── 2. 变更管理 ────────────────────
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS approved_by VARCHAR(50);
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS close_note TEXT;
ALTER TABLE change_request ADD COLUMN IF NOT EXISTS cancel_reason TEXT;
COMMENT ON COLUMN change_request.status IS
    'DRAFT 草稿 / PENDING_CAB 待CAB审批 / APPROVED 已批准 / REJECTED 已驳回 / IN_PROGRESS 实施中 / COMPLETED 已完成 / FAILED 失败已回退 / CANCELLED 已取消';
COMMENT ON COLUMN change_request.change_type IS 'STANDARD 标准变更（预授权，提交即批准）/ NORMAL 常规变更 / EMERGENCY 紧急变更';
CREATE INDEX IF NOT EXISTS idx_change_request_window ON change_request (scheduled_start, scheduled_end)
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_change_asset ON change_asset (cr_id, asset_no);

-- ──────────────────── 3. CI 关系 ────────────────────
ALTER TABLE ci_relation ADD COLUMN IF NOT EXISTS created_by VARCHAR(50);
ALTER TABLE ci_relation ADD COLUMN IF NOT EXISTS remark VARCHAR(200);
-- 历史数据可能已有重复的活动关系：先把重复者（保留 id 最小的一条）置为失效，再建唯一索引
UPDATE ci_relation r SET valid_to = NOW()
WHERE r.valid_to IS NULL
  AND EXISTS (SELECT 1 FROM ci_relation o
              WHERE o.valid_to IS NULL AND o.id < r.id
                AND o.src_type = r.src_type AND o.src_id = r.src_id AND o.rel_type = r.rel_type
                AND o.dst_type = r.dst_type AND o.dst_id = r.dst_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ci_relation_active
    ON ci_relation (src_type, src_id, rel_type, dst_type, dst_id) WHERE valid_to IS NULL;

-- ──────────────────── 4. 巡检计划 ────────────────────
CREATE TABLE IF NOT EXISTS inspection_plan (
    id            BIGSERIAL PRIMARY KEY,
    plan_no       VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    scope_type    VARCHAR(20)  NOT NULL DEFAULT 'ROOM',     -- ROOM / RACK
    scope_codes   TEXT[]       NOT NULL DEFAULT '{}',
    frequency     VARCHAR(20)  NOT NULL DEFAULT 'WEEKLY',   -- DAILY / WEEKLY / MONTHLY
    run_hour      INT          NOT NULL DEFAULT 9 CHECK (run_hour BETWEEN 0 AND 23),
    run_weekday   INT          CHECK (run_weekday BETWEEN 1 AND 7),    -- WEEKLY：1=周一
    run_month_day INT          CHECK (run_month_day BETWEEN 1 AND 28), -- MONTHLY：避开 29-31
    checklist     JSONB        NOT NULL DEFAULT '[]',
    priority      VARCHAR(10)  NOT NULL DEFAULT '中',
    assignee      VARCHAR(50),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    next_run_at   TIMESTAMP,
    last_run_at   TIMESTAMP,
    last_wo_no    VARCHAR(50),
    created_by    VARCHAR(50),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_inspection_plan_due ON inspection_plan (next_run_at)
    WHERE enabled AND deleted_at IS NULL;
CREATE TABLE IF NOT EXISTS inspection_run (
    id         BIGSERIAL PRIMARY KEY,
    plan_id    BIGINT      NOT NULL REFERENCES inspection_plan (id),
    wo_no      VARCHAR(50) NOT NULL,
    run_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    trigger    VARCHAR(20) NOT NULL DEFAULT 'SCHEDULE'   -- SCHEDULE / MANUAL
);
CREATE INDEX IF NOT EXISTS idx_inspection_run_plan ON inspection_run (plan_id, run_at DESC);
COMMENT ON TABLE inspection_plan IS '巡检计划：调度器按 next_run_at 生成 wo_type=巡检 的工单，检查项写入工单明细';

-- ──────────────────── 5. 重复索引清理 ────────────────────
DROP INDEX IF EXISTS idx_ci_relation_src;          -- = V1 idx_ci_src
DROP INDEX IF EXISTS idx_ci_relation_dst;          -- = V1 idx_ci_dst
DROP INDEX IF EXISTS idx_asset_event_asset_no;     -- ⊂ V1 idx_asset_event_asset (asset_no, event_time DESC)
DROP INDEX IF EXISTS idx_contract_asset_asset_no;  -- = V1 idx_contract_asset_asset
DROP INDEX IF EXISTS idx_asset_finance_asset_no;   -- asset_finance.asset_no 本身是主键

-- ──────────────────── 6. VIEWER 补齐 changes 读权限 ────────────────────
INSERT INTO role_module_perm (role_code, module, can_read, can_write)
SELECT 'VIEWER', 'changes', TRUE, FALSE
WHERE EXISTS (SELECT 1 FROM roles WHERE role_code = 'VIEWER')
ON CONFLICT (role_code, module) DO NOTHING;
