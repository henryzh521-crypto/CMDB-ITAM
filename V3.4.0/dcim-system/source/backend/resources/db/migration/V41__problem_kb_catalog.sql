-- ============================================================
-- V41  ITIL 问题管理 + 知识库 + 服务目录（自助服务门户）
-- ============================================================

-- ──────────────────── 1. 知识库 ────────────────────
CREATE TABLE IF NOT EXISTS kb_article (
    id               BIGSERIAL PRIMARY KEY,
    kb_no            VARCHAR(30)  NOT NULL UNIQUE,
    title            VARCHAR(200) NOT NULL,
    category         VARCHAR(30)  NOT NULL DEFAULT 'TROUBLESHOOT'
                     CHECK (category IN ('TROUBLESHOOT', 'SOP', 'KNOWN_ERROR', 'FAQ', 'OTHER')),
    content          TEXT         NOT NULL DEFAULT '',
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    tags             TEXT[]       NOT NULL DEFAULT '{}',
    -- 适用范围：用于在告警 / 工单 / 问题中自动推荐
    alertnames       TEXT[]       NOT NULL DEFAULT '{}',
    model_codes      TEXT[]       NOT NULL DEFAULT '{}',
    category_codes   TEXT[]       NOT NULL DEFAULT '{}',
    source_problem   VARCHAR(30),
    views            INTEGER      NOT NULL DEFAULT 0,
    helpful          INTEGER      NOT NULL DEFAULT 0,
    unhelpful        INTEGER      NOT NULL DEFAULT 0,
    created_by       VARCHAR(50),
    updated_by       VARCHAR(50),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at     TIMESTAMP,
    deleted_at       TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kb_title_trgm ON kb_article USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_kb_content_trgm ON kb_article USING gin (content gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_kb_alertnames ON kb_article USING gin (alertnames);
CREATE INDEX IF NOT EXISTS idx_kb_models ON kb_article USING gin (model_codes);
CREATE INDEX IF NOT EXISTS idx_kb_tags ON kb_article USING gin (tags);

-- 同一用户对同一文章只记一次评价
CREATE TABLE IF NOT EXISTS kb_feedback (
    article_id  BIGINT      NOT NULL REFERENCES kb_article (id) ON DELETE CASCADE,
    username    VARCHAR(50) NOT NULL,
    helpful     BOOLEAN     NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (article_id, username)
);

-- ──────────────────── 2. 问题管理 ────────────────────
CREATE TABLE IF NOT EXISTS problem (
    id            BIGSERIAL PRIMARY KEY,
    prb_no        VARCHAR(30)  NOT NULL UNIQUE,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'NEW'
                  CHECK (status IN ('NEW', 'ANALYZING', 'KNOWN_ERROR', 'RESOLVED', 'CLOSED')),
    priority      VARCHAR(10)  NOT NULL DEFAULT '中' CHECK (priority IN ('高', '中', '低')),
    owner         VARCHAR(50),
    asset_nos     TEXT[]       NOT NULL DEFAULT '{}',
    model_code    VARCHAR(80),
    alertname     VARCHAR(200),
    root_cause    TEXT,
    workaround    TEXT,
    resolution    TEXT,
    kb_no         VARCHAR(30),
    source        VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',   -- MANUAL / SUGGESTION
    created_by    VARCHAR(50),
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    resolved_at   TIMESTAMP,
    closed_at     TIMESTAMP,
    deleted_at    TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_problem_status ON problem (status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_problem_assets ON problem USING gin (asset_nos);

CREATE TABLE IF NOT EXISTS problem_link (
    id          BIGSERIAL PRIMARY KEY,
    problem_id  BIGINT      NOT NULL REFERENCES problem (id) ON DELETE CASCADE,
    link_type   VARCHAR(20) NOT NULL CHECK (link_type IN ('WORKORDER', 'ALERT', 'CHANGE')),
    ref_no      VARCHAR(50) NOT NULL,
    created_by  VARCHAR(50),
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (problem_id, link_type, ref_no)
);
CREATE INDEX IF NOT EXISTS idx_problem_link_ref ON problem_link (link_type, ref_no);

-- 问题状态流转记录
CREATE TABLE IF NOT EXISTS problem_flow (
    id          BIGSERIAL PRIMARY KEY,
    problem_id  BIGINT      NOT NULL REFERENCES problem (id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status   VARCHAR(20) NOT NULL,
    actor       VARCHAR(50),
    comment     TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_problem_flow ON problem_flow (problem_id, id);

-- ──────────────────── 3. 服务目录 ────────────────────
CREATE TABLE IF NOT EXISTS catalog_item (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    category      VARCHAR(50)  NOT NULL DEFAULT '通用服务',
    description   TEXT,
    icon          VARCHAR(40),
    wo_type       VARCHAR(50)  NOT NULL,
    priority      VARCHAR(10)  NOT NULL DEFAULT '中' CHECK (priority IN ('高', '中', '低')),
    -- 表单：[{key,label,type(text|textarea|number|select|date|asset|assets|rack),required,options[],placeholder,maxLength}]
    form_schema   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    visible_roles TEXT[]       NOT NULL DEFAULT '{}',      -- 空 = 所有登录用户可见
    sla_hint      VARCHAR(100),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    sort          INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS catalog_request (
    id          BIGSERIAL PRIMARY KEY,
    item_id     BIGINT       NOT NULL REFERENCES catalog_item (id),
    wo_no       VARCHAR(50)  NOT NULL,
    requester   VARCHAR(50)  NOT NULL,
    form_values JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_catalog_request_user ON catalog_request (requester, id DESC);

INSERT INTO catalog_item (code, name, category, description, icon, wo_type, priority, form_schema, sla_hint, sort) VALUES
('FAULT', '设备故障报修', '运维服务', '服务器、网络、存储等设备故障报修，运维工程师接单处理。', 'Warning', '故障维修', '高',
 '[{"key":"assets","label":"故障设备","type":"assets","required":true},
   {"key":"symptom","label":"故障现象","type":"select","required":true,"options":["无法开机","宕机/无响应","硬件告警灯","GPU 掉卡","磁盘故障","网络不通","性能异常","其他"]},
   {"key":"impact","label":"影响范围","type":"select","required":true,"options":["单台设备","业务降级","业务中断"]},
   {"key":"detail","label":"详细描述","type":"textarea","required":true,"maxLength":2000,"placeholder":"发生时间、报错信息、已尝试的操作"}]'::jsonb,
 '按优先级 SLA 响应', 10),
('RACK', '设备上架部署', '交付服务', '新到货设备上架、加电、布线与初始化。', 'Upload', '上架部署', '中',
 '[{"key":"assets","label":"待上架设备","type":"assets","required":true},
   {"key":"targetRack","label":"目标机柜","type":"rack","required":true},
   {"key":"network","label":"网络需求","type":"textarea","required":false,"placeholder":"业务网 / 管理网 / IB 端口与 VLAN"},
   {"key":"expectFinish","label":"期望完成日期","type":"date","required":false}]'::jsonb,
 '3 个工作日', 20),
('MOVE', '设备调拨 / 搬迁', '交付服务', '设备在机柜、机房之间调拨或搬迁。', 'Switch', '调拨', '中',
 '[{"key":"assets","label":"调拨设备","type":"assets","required":true},
   {"key":"targetRack","label":"目标机柜","type":"rack","required":true},
   {"key":"reason","label":"调拨原因","type":"textarea","required":true},
   {"key":"expectFinish","label":"期望完成日期","type":"date","required":false}]'::jsonb,
 '需审批', 30),
('OFFLINE', '设备下架退库', '交付服务', '设备下架、数据擦除并退回仓库。', 'Download', '下架退库', '中',
 '[{"key":"assets","label":"下架设备","type":"assets","required":true},
   {"key":"wipe","label":"数据处理","type":"select","required":true,"options":["无需处理","逻辑擦除","物理销毁硬盘"]},
   {"key":"reason","label":"下架原因","type":"textarea","required":true}]'::jsonb,
 '需审批', 40),
('INSPECT', '临时巡检申请', '运维服务', '重大活动保障、迁移前后等临时巡检。', 'View', '巡检', '中',
 '[{"key":"scope","label":"巡检范围","type":"text","required":true,"placeholder":"机房 / 机柜 / 设备清单"},
   {"key":"items","label":"重点检查项","type":"textarea","required":false},
   {"key":"expectFinish","label":"期望完成日期","type":"date","required":true}]'::jsonb,
 NULL, 50)
ON CONFLICT (code) DO NOTHING;

-- ──────────────────── 4. 权限 ────────────────────
-- 新模块 problems（问题管理）、knowledge（知识库）；服务目录对所有登录用户开放，目录维护归 system
INSERT INTO role_module_perm (role_code, module, can_read, can_write)
SELECT r.role_code, m.module, TRUE, r.role_code IN ('R-OPS', 'R-ASSET')
FROM roles r CROSS JOIN (VALUES ('problems'), ('knowledge')) AS m(module)
WHERE r.role_code IN ('R-OPS', 'R-ASSET', 'R-DELIVERY', 'R-LEADER', 'LEADER', 'VIEWER')
ON CONFLICT (role_code, module) DO NOTHING;
