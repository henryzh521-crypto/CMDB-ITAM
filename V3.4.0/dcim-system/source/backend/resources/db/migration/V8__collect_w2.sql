-- ============================================================
-- V8__collect_w2.sql — W2 采集自动化（PRD F02 发现+IRE / F06 带外 / F07 GPU）
--   带外凭据（AES-GCM 密文）+ 带外档案 + 发现任务/结果（IRE 待认领池）
--   + 配置快照（型号规格差异）+ GPU 卡档案（{整机}-G{槽位} + 换卡链）
--   + GPU 健康度权重配置（单行可配）
--   指标时序数据不落 PG：经 MetricStore Port 写 TDengine（dcim.metrics.store 切换）
-- ============================================================

-- 1. 带外凭据：密码密文存储，任何 API 不回显明文
CREATE TABLE bmc_credential (
    id           BIGSERIAL PRIMARY KEY,
    cred_name    VARCHAR(100) NOT NULL UNIQUE,
    username     VARCHAR(100) NOT NULL,
    password_enc TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE bmc_credential IS '带外凭据（BMC 用户名/密码，AES-GCM 密文）';

-- 2. 带外档案：发现到的 BMC 端点，认领/匹配后关联资产
CREATE TABLE bmc_endpoint (
    id                   BIGSERIAL PRIMARY KEY,
    bmc_ip               VARCHAR(50)  NOT NULL UNIQUE,
    mac                  VARCHAR(50),
    asset_no             VARCHAR(50) REFERENCES asset (asset_no),
    credential_id        BIGINT REFERENCES bmc_credential (id),
    redfish_version      VARCHAR(20),
    ipmi_version         VARCHAR(20),
    collect_interval_min INT         NOT NULL DEFAULT 60,   -- 采集间隔（分钟）
    consecutive_failures INT         NOT NULL DEFAULT 0,    -- 连续失败次数（≥3 前端标黄）
    last_collect_at      TIMESTAMPTZ,
    last_collect_ok      BOOL,
    next_collect_at      TIMESTAMPTZ,                        -- 到期时间；NULL=尚未纳入采集
    disabled             BOOL         NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_bmc_endpoint_next_collect ON bmc_endpoint (next_collect_at);
COMMENT ON TABLE bmc_endpoint IS '带外档案（BMC 端点，资产关联后为已纳管）';

-- 3. 发现任务：agent 拉取式（无平台侧网段展开，total/scanned 由 agent 回报）
CREATE TABLE discover_task (
    id            BIGSERIAL PRIMARY KEY,
    task_no       VARCHAR(30)  NOT NULL UNIQUE,               -- DS-yyyyMMdd-NNNN
    name          VARCHAR(200) NOT NULL,
    cidr          VARCHAR(50)  NOT NULL,                      -- 最大 /24
    protocol      VARCHAR(20)  NOT NULL,                      -- REDFISH / IPMI
    credential_id BIGINT REFERENCES bmc_credential (id),
    region_code   VARCHAR(10),
    status        VARCHAR(20)  NOT NULL DEFAULT '待执行',     -- 待执行/扫描中/已完成/失败
    total_ips     INT,
    scanned_ips   INT          NOT NULL DEFAULT 0,
    found_count   INT          NOT NULL DEFAULT 0,
    matched_count INT          NOT NULL DEFAULT 0,
    pending_count INT          NOT NULL DEFAULT 0,
    last_run_at   TIMESTAMPTZ,
    created_by    VARCHAR(50),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON TABLE discover_task IS '自动发现任务（网段 CIDR + 协议 + 凭据）';

-- 4. 发现结果：IRE 归一化落点（命中=已匹配回填；未命中=待认领池）
CREATE TABLE discover_result (
    id           BIGSERIAL PRIMARY KEY,
    task_id      BIGINT NOT NULL REFERENCES discover_task (id),
    bmc_ip       VARCHAR(50)  NOT NULL,
    sn           VARCHAR(100) NOT NULL DEFAULT '',             -- 空串=未读到 SN
    manufacturer VARCHAR(100),
    model        VARCHAR(100),
    raw          JSONB,                                        -- 发现原始报文摘要（可回放）
    endpoint_id  BIGINT REFERENCES bmc_endpoint (id),
    asset_no     VARCHAR(50) UNIQUE,                           -- 命中/认领后回填
    status       VARCHAR(20) NOT NULL DEFAULT '待认领',        -- 待认领/已匹配/已认领/已忽略
    decision     JSONB,                                        -- IRE 裁决 {matchType,action,reason,conflicts}
    claimed_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_discover_result_task ON discover_result (task_id);
CREATE INDEX idx_discover_result_status ON discover_result (status);
CREATE UNIQUE INDEX ux_discover_result_ip_sn ON discover_result (bmc_ip, sn);
COMMENT ON TABLE discover_result IS '发现结果/待认领池（IRE 裁决可回放）';

-- 5. 配置快照：带外采集的硬件清单历史（Redfish 优先，IPMI FRU 兜底）
CREATE TABLE hw_snapshot (
    id          BIGSERIAL PRIMARY KEY,
    endpoint_id BIGINT NOT NULL REFERENCES bmc_endpoint (id),
    asset_no    VARCHAR(50),
    protocol    VARCHAR(20),
    snapshot    JSONB,                                         -- {cpu,gpu,memory,disk,fw,psu,nic}
    spec_diff   JSONB,                                         -- 与 device_model.specs 差异 {key:{expected,actual}}
    ok          BOOL NOT NULL DEFAULT true,
    error       TEXT,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_hw_snapshot_endpoint ON hw_snapshot (endpoint_id, collected_at DESC);
COMMENT ON TABLE hw_snapshot IS '带外配置快照历史（首全量+每日增量）';

-- 6. GPU 卡档案升级：V1 基线已建 gpu_card（sn/mem_gb/fw_version 列名），W2 对齐补列（幂等）
--    - 保留 V1 的 UNIQUE(sn)（SN 全局唯一）与 UNIQUE(host_asset_no, slot)
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS gpu_sn         VARCHAR(100);
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS memory_gb      INT;
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS fw             VARCHAR(100);
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS batch_no       VARCHAR(100);
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS mig_capable    BOOL;
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS health_level   VARCHAR(10);   -- 优/良/观察/劣化/高危
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS prev_sn        VARCHAR(100);  -- 换卡后旧 SN 留在 prev_sn/swap_history
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS swap_history   JSONB;         -- [{oldSn,newSn,at,snapshotId}]
ALTER TABLE gpu_card ADD COLUMN IF NOT EXISTS last_scored_at TIMESTAMPTZ;
-- 旧数据迁移（若有）：V1 列 → W2 列
UPDATE gpu_card SET gpu_sn = COALESCE(gpu_sn, sn) WHERE sn IS NOT NULL;
UPDATE gpu_card SET memory_gb = COALESCE(memory_gb, mem_gb) WHERE mem_gb IS NOT NULL;
UPDATE gpu_card SET fw = COALESCE(fw, fw_version) WHERE fw_version IS NOT NULL;
-- W2 侧按 gpu_sn 做全局唯一硬约束（V1 UNIQUE(sn) 保留不动）
CREATE UNIQUE INDEX IF NOT EXISTS ux_gpu_card_gpu_sn ON gpu_card (gpu_sn);
COMMENT ON TABLE gpu_card IS 'GPU 卡全息档案（F07-REQ-001，健康度小时级更新）';

-- 7. GPU 健康度权重配置（单行，R-ADMIN 可配；四权重和=100）
CREATE TABLE gpu_health_config (
    id           INT PRIMARY KEY DEFAULT 1,
    weight_ecc   INT NOT NULL DEFAULT 40,
    weight_temp  INT NOT NULL DEFAULT 25,
    weight_xid   INT NOT NULL DEFAULT 20,
    weight_power INT NOT NULL DEFAULT 15,
    temp_limit_c INT NOT NULL DEFAULT 85,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_gpu_health_weights CHECK (weight_ecc + weight_temp + weight_xid + weight_power = 100)
);
INSERT INTO gpu_health_config (id) VALUES (1) ON CONFLICT DO NOTHING;
COMMENT ON TABLE gpu_health_config IS 'GPU 健康度评分权重（单行配置）';
