-- ============================================================
-- V39__monitoring.sql — v3.2 监控与事件管理
--
-- 设计：采用业界通用的 Prometheus + Alertmanager 体系，平台承担三件事：
--   1. 监控对象的唯一来源：Prometheus 通过 http_sd 从 CMDB 拉取监控目标（monitor_profile 决定
--      哪类资产用哪个采集任务、取哪个 IP），资产上下架/状态变化自动生效，无需手工维护 targets；
--   2. 告警接收与治理：Alertmanager webhook → alert_event（按 fingerprint 去重、关联资产、
--      维护窗口/变更窗口抑制、确认、自动建故障工单、恢复回写）；
--   3. 维护窗口：手工计划维护 + 实施中的变更自动视为维护，期间告警只记录不派单。
-- ============================================================

-- ──────────────────── 1. 监控模板：资产分类 → 采集任务 ────────────────────
CREATE TABLE IF NOT EXISTS monitor_profile (
    id               BIGSERIAL PRIMARY KEY,
    job              VARCHAR(30)  NOT NULL,             -- icmp / node / dcgm / ipmi / snmp / redfish ...
    category_pattern VARCHAR(100) NOT NULL DEFAULT '%', -- 匹配 category.path（LIKE），'%' = 全部分类
    ip_field         VARCHAR(20)  NOT NULL DEFAULT 'biz_ip', -- biz_ip / mgmt_ip
    port             INT,                               -- 目标端口（icmp/ipmi 远程模式为空）
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    remark           VARCHAR(200),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (job, category_pattern, ip_field)
);
COMMENT ON TABLE monitor_profile IS '监控模板：哪类资产（category.path LIKE）纳入哪个 Prometheus 采集任务，取哪个 IP';

-- 需要在被监控端额外部署 exporter 的任务默认关闭，部署后在「监控设置」中启用
INSERT INTO monitor_profile (job, category_pattern, ip_field, port, enabled, remark) VALUES
  ('icmp',  '%',          'biz_ip',  NULL, TRUE,  '业务网 Ping 存活（无需安装任何代理，开箱即用）'),
  ('icmp',  '%',          'mgmt_ip', NULL, TRUE,  '带外/管理网 Ping 存活（BMC、交换机管理口、PDU）'),
  ('node',  '/SRV/%',     'biz_ip',  9100, TRUE,  '服务器操作系统指标（需在主机部署 node_exporter）'),
  ('node',  '/STO/%',     'biz_ip',  9100, TRUE,  '存储节点操作系统指标'),
  ('dcgm',  '%GPU%',      'biz_ip',  9400, FALSE, 'GPU 指标（需部署 NVIDIA dcgm-exporter）'),
  ('ipmi',  '/SRV/%',     'mgmt_ip', NULL, FALSE, '服务器带外硬件传感器（ipmi_exporter 远程模式）'),
  ('snmp',  '/NET/%',     'mgmt_ip', NULL, FALSE, '网络设备 SNMP（snmp_exporter）'),
  ('snmp',  '/FAC/%',     'mgmt_ip', NULL, FALSE, '机房设施 PDU / UPS / 空调 SNMP')
ON CONFLICT (job, category_pattern, ip_field) DO NOTHING;

-- ──────────────────── 2. 告警事件 ────────────────────
CREATE TABLE IF NOT EXISTS alert_event (
    id                BIGSERIAL PRIMARY KEY,
    fingerprint       VARCHAR(64)  NOT NULL,             -- Alertmanager 指纹（同一告警的唯一标识）
    source            VARCHAR(30)  NOT NULL DEFAULT 'alertmanager', -- alertmanager / generic / zabbix
    alertname         VARCHAR(200) NOT NULL,
    severity          VARCHAR(20)  NOT NULL DEFAULT 'warning',      -- critical / warning / info
    status            VARCHAR(20)  NOT NULL DEFAULT 'FIRING',       -- FIRING / RESOLVED
    summary           VARCHAR(500),
    description       TEXT,
    labels            JSONB        NOT NULL DEFAULT '{}',
    instance          VARCHAR(200),
    asset_no          VARCHAR(50),
    rack_code         VARCHAR(50),
    room_code         VARCHAR(50),
    starts_at         TIMESTAMP    NOT NULL,
    ends_at           TIMESTAMP,
    last_received_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    fire_count        INT          NOT NULL DEFAULT 1,
    suppressed        BOOLEAN      NOT NULL DEFAULT FALSE,
    suppress_reason   VARCHAR(200),
    ack_by            VARCHAR(50),
    ack_at            TIMESTAMP,
    ack_note          VARCHAR(500),
    wo_no             VARCHAR(50),
    generator_url     VARCHAR(500),
    close_reason      VARCHAR(200),               -- 自动关闭原因（如超时未再收到）
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
-- 同一指纹同时只有一条活动告警；恢复后再次触发产生新记录（保留完整历史）
CREATE UNIQUE INDEX IF NOT EXISTS ux_alert_event_active ON alert_event (fingerprint) WHERE status = 'FIRING';
CREATE INDEX IF NOT EXISTS idx_alert_event_status ON alert_event (status, severity, starts_at DESC);
CREATE INDEX IF NOT EXISTS idx_alert_event_asset ON alert_event (asset_no, starts_at DESC);
CREATE INDEX IF NOT EXISTS idx_alert_event_starts ON alert_event (starts_at DESC);
COMMENT ON TABLE alert_event IS '告警事件：Alertmanager/通用 webhook 推送，按 fingerprint 去重，关联资产与工单';

-- ──────────────────── 3. 维护窗口 ────────────────────
CREATE TABLE IF NOT EXISTS maintenance_window (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(200) NOT NULL,
    starts_at    TIMESTAMP    NOT NULL,
    ends_at      TIMESTAMP    NOT NULL,
    asset_nos    TEXT[]       NOT NULL DEFAULT '{}',
    rack_codes   TEXT[]       NOT NULL DEFAULT '{}',
    room_codes   TEXT[]       NOT NULL DEFAULT '{}',
    alertnames   TEXT[]       NOT NULL DEFAULT '{}',     -- 为空 = 范围内所有告警
    reason       VARCHAR(500),
    created_by   VARCHAR(50),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMP,
    CHECK (ends_at > starts_at)
);
CREATE INDEX IF NOT EXISTS idx_maintenance_window_time ON maintenance_window (starts_at, ends_at) WHERE deleted_at IS NULL;
COMMENT ON TABLE maintenance_window IS '计划维护窗口：窗口内匹配范围的告警标记为已抑制、不自动派单（实施中的变更自动视同维护）';

-- ──────────────────── 4. 监控设置（键值） ────────────────────
CREATE TABLE IF NOT EXISTS monitoring_setting (
    setting_key   VARCHAR(50) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    remark        VARCHAR(200),
    updated_at    TIMESTAMP   NOT NULL DEFAULT NOW()
);
INSERT INTO monitoring_setting (setting_key, setting_value, remark) VALUES
  ('auto_ticket_severity', 'critical', '达到该级别自动创建故障工单：critical / warning / none(关闭)'),
  ('auto_ticket_delay_min', '5', '告警持续多少分钟仍未恢复才派单（避免抖动刷单）'),
  ('monitored_statuses', '在用(计费),维修中,停用中', '纳入监控的资产业务状态（逗号分隔）'),
  ('ticket_priority_critical', '高', '严重告警工单优先级'),
  ('ticket_priority_warning', '中', '警告告警工单优先级'),
  ('resolve_comment', 'true', '告警恢复时在关联工单追加评论'),
  ('prometheus_url', '', 'Prometheus 查询地址（为空则使用部署配置 dcim.monitoring.prometheus-url）')
ON CONFLICT (setting_key) DO NOTHING;

-- ──────────────────── 5. 权限：新模块 monitoring ────────────────────
INSERT INTO role_module_perm (role_code, module, can_read, can_write)
SELECT 'VIEWER', 'monitoring', TRUE, FALSE
WHERE EXISTS (SELECT 1 FROM roles WHERE role_code = 'VIEWER')
ON CONFLICT (role_code, module) DO NOTHING;
