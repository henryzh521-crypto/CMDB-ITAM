-- ============================================================
-- V1__baseline.sql — 基线全表（对齐 PRD V2.0 第 9 章数据模型）
-- 约定：
--   * 英文 snake_case 列名；业务编号为自然键/主键（asset_no / sn / wo_no ...）
--   * 流水/审计/事件/折旧表按月 RANGE 分区，唯一约束必须包含分区键
--   * 状态值直接存 PRD 中文状态名（与状态机文档一一对应，降低迁移语义丢失）
--   * 资产业务状态机：规划中→已采购→在途→已到货→待上架→上架中→测试压测中
--     →待验收→验收通过→在用(计费)⇄维修中/停用中→待退役→已退役→已报废/已退租
--   * 财务状态机：未入账→已入账→转固计提→减值→折旧完毕→处置中→核销
--     （租赁：使用权资产确认→折旧+利息→留购/退租）
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ──────────────────── 1. 权限与组织 ────────────────────

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    department      VARCHAR(100),
    phone           VARCHAR(30),
    email           VARCHAR(100),
    status          SMALLINT     NOT NULL DEFAULT 1,          -- 1=启用 0=停用
    must_change_pwd BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE departments (
    id          BIGSERIAL PRIMARY KEY,
    dept_code   VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    parent_code VARCHAR(50),
    path        VARCHAR(500),                               -- /ROOT/西北区域/...
    sort        INT          NOT NULL DEFAULT 0,
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    role_code   VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(200),
    built_in    BOOLEAN      NOT NULL DEFAULT FALSE,        -- 内置角色不可删
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_role (
    user_id   BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code VARCHAR(50) NOT NULL REFERENCES roles (role_code),
    PRIMARY KEY (user_id, role_code)
);

-- 权限四层之 1：模块权限（读/写）
CREATE TABLE role_module_perm (
    id        BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL REFERENCES roles (role_code) ON DELETE CASCADE,
    module    VARCHAR(50) NOT NULL,                         -- 对齐 security 模块注册表
    can_read  BOOLEAN     NOT NULL DEFAULT FALSE,
    can_write BOOLEAN     NOT NULL DEFAULT FALSE,
    UNIQUE (role_code, module)
);

-- 权限四层之 2：数据范围（按模块配置）
CREATE TABLE role_data_scope (
    id        BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL REFERENCES roles (role_code) ON DELETE CASCADE,
    module    VARCHAR(50) NOT NULL,
    scope     JSONB       NOT NULL DEFAULT '{}',            -- {"type":"ALL|DEPT|SELF","departments":[...]}
    UNIQUE (role_code, module)
);

-- 权限四层之 3：字段权限（隐藏/只读）
CREATE TABLE role_field_perm (
    id        BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL REFERENCES roles (role_code) ON DELETE CASCADE,
    module    VARCHAR(50) NOT NULL,
    fields    JSONB       NOT NULL DEFAULT '{}',            -- {"hidden":[...],"readonly":[...]}
    UNIQUE (role_code, module)
);

-- 权限四层之 4：操作开关（新增/编辑/删除/导入/导出/上架/下架...）
CREATE TABLE role_action_perm (
    id        BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL REFERENCES roles (role_code) ON DELETE CASCADE,
    module    VARCHAR(50) NOT NULL,
    actions   JSONB       NOT NULL DEFAULT '{}',            -- {"create":true,"delete":false,"export":true}
    UNIQUE (role_code, module)
);

-- ──────────────────── 2. 审计（按月分区） ────────────────────

CREATE TABLE audit_log (
    id         BIGSERIAL,
    actor      VARCHAR(50)  NOT NULL,
    action     VARCHAR(50)  NOT NULL,                        -- CREATE/UPDATE/DELETE/LOGIN/EXPORT...
    module     VARCHAR(50),
    obj_type   VARCHAR(50),
    obj_id     VARCHAR(100),
    detail     JSONB,
    ip         VARCHAR(50),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;
CREATE INDEX idx_audit_actor ON audit_log (actor, created_at DESC);
CREATE INDEX idx_audit_module ON audit_log (module, created_at DESC);

-- ──────────────────── 3. 主数据 ────────────────────

-- 五级分类树：大类→中类→小类→细类→型号（SRV/GPU/NET/STO/FAC/SPR/CON/SOF）
CREATE TABLE category (
    id            BIGSERIAL PRIMARY KEY,
    category_code VARCHAR(50)  NOT NULL UNIQUE,               -- SRV / SRV-GPU8 / ...
    name          VARCHAR(100) NOT NULL,
    parent_code   VARCHAR(50)  REFERENCES category (category_code),
    level         INT          NOT NULL DEFAULT 1,            -- 1..5
    path          VARCHAR(500) NOT NULL DEFAULT '/',          -- /SRV/SRV-GPU8/
    sort          INT          NOT NULL DEFAULT 0,
    status        SMALLINT     NOT NULL DEFAULT 1,            -- 停用不影响历史
    remark        VARCHAR(200),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_category_parent ON category (parent_code);

-- 型号库：建卡选型号自动带出规格；EOL 跟踪
CREATE TABLE device_model (
    id            BIGSERIAL PRIMARY KEY,
    model_code    VARCHAR(80)  NOT NULL UNIQUE,
    category_code VARCHAR(50),
    brand         VARCHAR(100),
    vendor        VARCHAR(100),
    model_name    VARCHAR(200) NOT NULL,
    pn            VARCHAR(100),
    specs         JSONB        NOT NULL DEFAULT '{}',         -- {"cpu":...,"gpu":...,"u_size":2,"rated_power_w":1200,...}
    eol_date      DATE,                                        -- EOL 前 180 天提醒
    status        SMALLINT     NOT NULL DEFAULT 1,
    remark        VARCHAR(200),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE supplier (
    id            BIGSERIAL PRIMARY KEY,
    supplier_code VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    type          VARCHAR(50),                                 -- 设备/备件/服务/租赁
    contact       VARCHAR(50),
    phone         VARCHAR(50),
    email         VARCHAR(100),
    hotline       VARCHAR(50),
    products      TEXT,
    address       TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    remark        TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE dict (
    id         BIGSERIAL PRIMARY KEY,
    dict_type  VARCHAR(50) NOT NULL,
    dict_key   VARCHAR(100) NOT NULL,
    dict_value VARCHAR(200) NOT NULL,
    sort       INT         NOT NULL DEFAULT 0,
    status     SMALLINT    NOT NULL DEFAULT 1,
    UNIQUE (dict_type, dict_key)
);

-- ──────────────────── 4. 资产域（PRD F01/F02/F03/F07） ────────────────────

-- 资产主表：asset_no 系统生成终身不变；SN 全局唯一硬约束（无 SN 走 NB 内部码，也存 sn 列）
CREATE TABLE asset (
    asset_no       VARCHAR(50)   PRIMARY KEY,                 -- SRV-QY26-2026-00001
    sn             VARCHAR(100)  UNIQUE,                      -- 身份锚点；NB 段内部码亦存此列
    name           VARCHAR(200)  NOT NULL,
    category_code  VARCHAR(50),
    model_code     VARCHAR(80),
    owner_type     VARCHAR(20)   NOT NULL DEFAULT 'SELF',     -- SELF 自持 / LEASE 融资租赁
    status_biz     VARCHAR(20)   NOT NULL DEFAULT '规划中',
    status_fin     VARCHAR(20)   NOT NULL DEFAULT '未入账',
    region_code    VARCHAR(10),                                -- QY/WLCB/SG
    room_code      VARCHAR(50),
    rack_code      VARCHAR(50),
    unit_no        INT,
    u_height       INT           DEFAULT 1,
    department     VARCHAR(100),
    owner          VARCHAR(50),                                -- 责任人
    mgmt_ip        VARCHAR(50),
    biz_ip         VARCHAR(50),
    purchase_date  DATE,
    purchase_price NUMERIC(15,2),
    warranty_end   DATE,
    rack_date      DATE,
    contract_no    VARCHAR(50),                                -- 权属=融资租赁时关联租赁合同
    attrs          JSONB         NOT NULL DEFAULT '{}',        -- 扩展属性 + legacy_* 迁移字段
    version        INT           NOT NULL DEFAULT 1,           -- 乐观锁
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_asset_rack ON asset (rack_code, unit_no);
CREATE INDEX idx_asset_status ON asset (status_biz);
CREATE INDEX idx_asset_category ON asset (category_code);
CREATE INDEX idx_asset_name_trgm ON asset USING gin (name gin_trgm_ops);
CREATE INDEX idx_asset_sn_trgm ON asset USING gin (sn gin_trgm_ops);
CREATE INDEX idx_asset_attrs ON asset USING gin (attrs);

-- 资产财务 1:1（业务-财务双轨：财务状态独立流转）
CREATE TABLE asset_finance (
    asset_no         VARCHAR(50)   PRIMARY KEY REFERENCES asset (asset_no),
    origin_value     NUMERIC(15,2),
    residual_rate    NUMERIC(5,4),
    life_months      INT,
    dep_method       VARCHAR(30),                               -- 平均年限法...
    dep_start_date   DATE,
    accumulated_dep  NUMERIC(15,2) NOT NULL DEFAULT 0,
    net_value        NUMERIC(15,2),
    impairment       NUMERIC(15,2) NOT NULL DEFAULT 0,
    lease_flag       BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- GPU 卡部件（ADR-05：独立建卡；换卡保留新旧 SN 链）
CREATE TABLE gpu_card (
    card_no       VARCHAR(80)  PRIMARY KEY,                    -- {asset_no}-G{slot}
    host_asset_no VARCHAR(50)  NOT NULL REFERENCES asset (asset_no),
    slot          INT          NOT NULL,
    sn            VARCHAR(100) UNIQUE,
    model         VARCHAR(200),
    mem_gb        INT,
    fw_version    VARCHAR(100),
    batch_no      VARCHAR(50),
    warranty_end  DATE,
    health_score  INT,
    mig_profile   VARCHAR(50),
    status        VARCHAR(20)  NOT NULL DEFAULT '在位',
    rma_ref       VARCHAR(50),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (host_asset_no, slot)
);

-- CI 关系（Installed_In / Contains / Connected_To / Runs_On / Depends_On / Serves / Belongs_To / Owned_By / Hosts / Leased_To）
CREATE TABLE ci_relation (
    id         BIGSERIAL PRIMARY KEY,
    src_type   VARCHAR(30) NOT NULL,
    src_id     VARCHAR(50) NOT NULL,
    rel_type   VARCHAR(30) NOT NULL,
    dst_type   VARCHAR(30) NOT NULL,
    dst_id     VARCHAR(50) NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (src_type, src_id, rel_type, dst_type, dst_id, valid_from)
);
CREATE INDEX idx_ci_src ON ci_relation (src_type, src_id);
CREATE INDEX idx_ci_dst ON ci_relation (dst_type, dst_id);

-- 服务树（蓝鲸式：集群→项目→服务；成本分摊/权限继承/告警路由）
CREATE TABLE service_tree (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    node_type  VARCHAR(20)  NOT NULL,                         -- 集群/项目/服务
    parent_id  BIGINT       REFERENCES service_tree (id),
    path       VARCHAR(500) NOT NULL DEFAULT '/',
    owner      VARCHAR(50),
    status     SMALLINT     NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 资产生命周期事件（全量留存，按月分区；去重键含分区键）
CREATE TABLE asset_event (
    id         BIGSERIAL,
    asset_no   VARCHAR(50)  NOT NULL,
    event_type VARCHAR(30)  NOT NULL,                          -- RACKED/MOVED/REPAIRED/ACCEPTED/MIGRATED...
    operator   VARCHAR(50),
    ref_doc    VARCHAR(100),                                   -- 关联工单号/验收单号
    snapshot   JSONB,                                          -- 迁移前后快照
    event_time TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, event_time)
) PARTITION BY RANGE (event_time);

CREATE TABLE asset_event_default PARTITION OF asset_event DEFAULT;
CREATE INDEX idx_asset_event_asset ON asset_event (asset_no, event_time DESC);

-- ──────────────────── 5. 机房与 U 位（PRD F05） ────────────────────

CREATE TABLE machine_room (
    id            BIGSERIAL PRIMARY KEY,
    room_code     VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    data_center   VARCHAR(100),                                -- 庆阳一期/乌兰察布/韶关
    city          VARCHAR(50),
    location      VARCHAR(100),
    area_sqm      NUMERIC(10,2),
    load_kg       NUMERIC(12,2),
    cooling_kw    NUMERIC(12,2),
    power_kw      NUMERIC(12,2),
    cabinet_count INT,
    ab_path       VARCHAR(20),                                 -- A路/B路/AB双路
    env_monitor   VARCHAR(100),
    manager       VARCHAR(50),
    remark        TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE rack (
    id              BIGSERIAL PRIMARY KEY,
    rack_code       VARCHAR(50)  NOT NULL UNIQUE,
    room_code       VARCHAR(50)  NOT NULL,
    location_name   VARCHAR(200),
    row_no          VARCHAR(10),
    col_no          INT,
    orientation     VARCHAR(20),
    u_capacity      INT          NOT NULL DEFAULT 42,
    power_limit_kw  NUMERIC(10,2),
    pdu_count       INT,
    cooling_type    VARCHAR(20),                               -- 风冷/液冷/CDU
    manager         VARCHAR(50),
    remark          TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_rack_room ON rack (room_code);

CREATE TABLE rack_unit (
    id          BIGSERIAL PRIMARY KEY,
    rack_code   VARCHAR(50) NOT NULL REFERENCES rack (rack_code),
    unit_no     INT         NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT '空闲',            -- 空闲/占用/预留/故障
    occupied_by VARCHAR(50),                                    -- asset_no
    UNIQUE (rack_code, unit_no)
);
CREATE INDEX idx_rack_unit_asset ON rack_unit (occupied_by);

-- ──────────────────── 6. 仓储与出入库（PRD F04） ────────────────────

CREATE TABLE material (
    id             BIGSERIAL PRIMARY KEY,
    material_code  VARCHAR(50)  NOT NULL UNIQUE,
    name           VARCHAR(200) NOT NULL,
    category       VARCHAR(50),
    type           VARCHAR(50),
    brand          VARCHAR(100),
    model          VARCHAR(100),
    pn             VARCHAR(100),
    spec           TEXT,
    unit           VARCHAR(20),
    stock          INT          NOT NULL DEFAULT 0,            -- 实时库存
    safety_stock   INT          NOT NULL DEFAULT 0,
    max_stock      INT          NOT NULL DEFAULT 0,
    total_in       INT          NOT NULL DEFAULT 0,
    total_out      INT          NOT NULL DEFAULT 0,
    price          NUMERIC(15,2),
    location       VARCHAR(100),
    supplier_code  VARCHAR(50),
    version        INT          NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 出入库流水（按月分区；log_no 由应用保证唯一，索引非唯一以兼容分区）
CREATE TABLE stock_log (
    id          BIGSERIAL,
    log_no      VARCHAR(50) NOT NULL,
    log_type    VARCHAR(20) NOT NULL,                           -- IN/OUT/ADJUST
    material_code VARCHAR(50) NOT NULL,
    qty         INT         NOT NULL,
    biz_date    DATE,
    operator    VARCHAR(50),
    department  VARCHAR(100),
    ref_wo      VARCHAR(50),
    remark      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE stock_log_default PARTITION OF stock_log DEFAULT;
CREATE INDEX idx_stock_log_material ON stock_log (material_code, created_at DESC);
CREATE INDEX idx_stock_log_no ON stock_log (log_no);

-- ──────────────────── 7. 工单域（PRD F09，9 类工单） ────────────────────

CREATE TABLE workorder (
    wo_no            VARCHAR(50)  PRIMARY KEY,
    wo_type          VARCHAR(50)  NOT NULL,                     -- 故障维修/变更/巡检/入库验收/资产盘点/耗材申领/上架部署/下架退库/报废处置
    title            VARCHAR(200),
    description      TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT '待处理',    -- 待处理/待审批/处理中/已完成/已驳回/已关闭
    priority         VARCHAR(20)  NOT NULL DEFAULT '中',
    applicant        VARCHAR(50),
    applicant_username VARCHAR(50),
    handler          VARCHAR(50),
    apply_time       TIMESTAMPTZ,
    expect_finish    DATE,
    finish_time      TIMESTAMPTZ,
    related_assets   VARCHAR(500),
    related_materials VARCHAR(500),
    apply_qty        INT,
    target_location  VARCHAR(200),
    target_rack      VARCHAR(50),
    target_unit      INT,
    before_location  VARCHAR(200),
    process          TEXT,
    complete_note    TEXT,
    accept_result    TEXT,
    acceptor         VARCHAR(50),
    accept_time      TIMESTAMPTZ,
    accept_note      TEXT,
    auto_deducted    BOOLEAN      NOT NULL DEFAULT FALSE,
    replaced_asset   VARCHAR(200),
    parent_wo        VARCHAR(50),
    root_cause       VARCHAR(50),                               -- 关闭必填根因分类
    details          JSONB      NOT NULL DEFAULT '[]',         -- 用料明细
    cc               JSONB      NOT NULL DEFAULT '[]',         -- 抄送人
    attachments      JSONB      NOT NULL DEFAULT '[]',
    version          INT          NOT NULL DEFAULT 1,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_wo_status ON workorder (status);
CREATE INDEX idx_wo_type ON workorder (wo_type);
CREATE INDEX idx_wo_applicant ON workorder (applicant_username);

-- 工单流转记录（全量留痕）
CREATE TABLE workorder_flow (
    id          BIGSERIAL PRIMARY KEY,
    wo_no       VARCHAR(50) NOT NULL,
    seq         INT         NOT NULL,
    action      VARCHAR(30) NOT NULL,                           -- 提交/审批通过/驳回/接单/完成/关闭/催办/评论
    actor       VARCHAR(50),
    from_status VARCHAR(20),
    to_status   VARCHAR(20),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (wo_no, seq)
);

-- 工作流配置（轻量审批链：W1 自研，Flowable 延后）
CREATE TABLE workflow_config (
    id             BIGSERIAL PRIMARY KEY,
    wo_type        VARCHAR(50) NOT NULL UNIQUE,
    need_approval  BOOLEAN     NOT NULL DEFAULT FALSE,
    approval_roles JSONB       NOT NULL DEFAULT '[]',          -- ["部门负责人",...] 按级
    template       VARCHAR(100),
    built_in       BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- SLA 配置（类型 × 优先级 → 响应/解决时限）
CREATE TABLE sla_config (
    id              BIGSERIAL PRIMARY KEY,
    wo_type         VARCHAR(50) NOT NULL,
    priority        VARCHAR(20) NOT NULL,
    response_minutes INT,
    resolve_minutes INT,
    escalate_to     VARCHAR(50),
    UNIQUE (wo_type, priority)
);

-- ──────────────────── 8. RFID 批量验收（PRD F04/F16 + 旧 v2.6.0 对等） ────────────────────

CREATE TABLE rfid_batch (
    batch_no        VARCHAR(50) PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    supplier_code   VARCHAR(50),
    source_type     VARCHAR(30),                                -- 采购到货/RMA返修/调拨
    item_count      INT         NOT NULL DEFAULT 0,
    scanned_count   INT         NOT NULL DEFAULT 0,
    confirmed_count INT         NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT '草稿',        -- 草稿/扫描中/已确认/已取消
    creator         VARCHAR(50),
    confirmed_at    TIMESTAMPTZ,
    remark          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rfid_batch_item (
    id            BIGSERIAL PRIMARY KEY,
    batch_no      VARCHAR(50) NOT NULL REFERENCES rfid_batch (batch_no) ON DELETE CASCADE,
    material_code VARCHAR(50) NOT NULL,
    expected_qty  INT         NOT NULL DEFAULT 0,
    received_qty  INT         NOT NULL DEFAULT 0,
    UNIQUE (batch_no, material_code)
);

CREATE TABLE rfid_scan_record (
    id             BIGSERIAL PRIMARY KEY,
    batch_no       VARCHAR(50) NOT NULL,
    epc            VARCHAR(200) NOT NULL,
    asset_no       VARCHAR(50),
    material_code  VARCHAR(50),
    sn             VARCHAR(100),
    scan_status    VARCHAR(20)  NOT NULL DEFAULT '已扫描',     -- 已扫描/异常/待入库
    exception_type VARCHAR(30),
    exception_note TEXT,
    operator       VARCHAR(50),
    scan_time      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (batch_no, epc)
);
CREATE INDEX idx_scan_batch ON rfid_scan_record (batch_no, scan_time);

CREATE TABLE rfid_reader (
    id              BIGSERIAL PRIMARY KEY,
    reader_code     VARCHAR(50) NOT NULL UNIQUE,
    name            VARCHAR(100),
    ip              VARCHAR(50),
    port            INT,
    location        VARCHAR(200),
    status          VARCHAR(20) NOT NULL DEFAULT '离线',
    last_heartbeat  TIMESTAMPTZ,
    remark          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ──────────────────── 9. 合同与折旧（PRD F12，CAS21 支撑） ────────────────────

CREATE TABLE contract (
    id            BIGSERIAL PRIMARY KEY,
    contract_no   VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(200) NOT NULL,
    type          VARCHAR(20)  NOT NULL,                       -- 采购/销售/融资租赁
    party         VARCHAR(200),                                -- 供应商/出租方/客户
    amount        NUMERIC(15,2),
    sign_date     DATE,
    start_date    DATE,
    end_date      DATE,
    discount_rate NUMERIC(8,6),                                -- CAS21 折现率
    purchase_option BOOLEAN    NOT NULL DEFAULT FALSE,         -- 留购权
    payment_plan  JSONB       NOT NULL DEFAULT '[]',           -- 租金计划
    status        VARCHAR(20) NOT NULL DEFAULT '生效',
    remark        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE contract_asset (
    id          BIGSERIAL PRIMARY KEY,
    contract_no VARCHAR(50) NOT NULL REFERENCES contract (contract_no) ON DELETE CASCADE,
    asset_no    VARCHAR(50) NOT NULL REFERENCES asset (asset_no),
    UNIQUE (contract_no, asset_no)
);
CREATE INDEX idx_contract_asset_asset ON contract_asset (asset_no);

-- 折旧记录（按月分区；asset_no+period 唯一且含分区键）
CREATE TABLE depreciation_record (
    id           BIGSERIAL,
    asset_no     VARCHAR(50)  NOT NULL,
    period       DATE         NOT NULL,                       -- 每月 1 日
    origin_value NUMERIC(15,2),
    month_dep    NUMERIC(15,2),
    accumulated  NUMERIC(15,2),
    net_value    NUMERIC(15,2),
    method       VARCHAR(30),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (id, period),
    UNIQUE (asset_no, period)
) PARTITION BY RANGE (period);

CREATE TABLE depreciation_record_default PARTITION OF depreciation_record DEFAULT;
CREATE INDEX idx_dep_asset ON depreciation_record (asset_no, period DESC);

-- ──────────────────── 10. 盘点（PRD F04-005） ────────────────────

CREATE TABLE inventory_task (
    id           BIGSERIAL PRIMARY KEY,
    task_no      VARCHAR(50) NOT NULL UNIQUE,
    name         VARCHAR(200) NOT NULL,
    scope        TEXT,                                        -- 区域/机房/批次描述
    filter       JSONB,                                       -- 结构化筛选条件
    inspector    VARCHAR(50),
    status       VARCHAR(20) NOT NULL DEFAULT '盘点中',       -- 盘点中/已完成/已取消
    total_count  INT         NOT NULL DEFAULT 0,
    checked_count INT        NOT NULL DEFAULT 0,
    diff_count   INT         NOT NULL DEFAULT 0,
    creator      VARCHAR(50),
    plan_finish  DATE,
    finish_time  TIMESTAMPTZ,
    version      INT         NOT NULL DEFAULT 1,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory_item (
    id          BIGSERIAL PRIMARY KEY,
    item_no     VARCHAR(50) NOT NULL UNIQUE,
    task_no     VARCHAR(50) NOT NULL REFERENCES inventory_task (task_no) ON DELETE CASCADE,
    asset_no    VARCHAR(50) NOT NULL,
    expected    JSONB,                                       -- 账面快照
    result      VARCHAR(20) NOT NULL DEFAULT '未盘',         -- 一致/盘亏/盘盈/错位/未盘
    checked_at  TIMESTAMPTZ,
    checker     VARCHAR(50),
    diff_note   TEXT,
    UNIQUE (task_no, asset_no)
);
CREATE INDEX idx_inv_item_task ON inventory_item (task_no, result);
