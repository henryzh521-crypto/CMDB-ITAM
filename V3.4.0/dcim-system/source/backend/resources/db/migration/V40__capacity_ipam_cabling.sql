-- ============================================================
-- V40  容量管理（功率）+ IP 地址管理（IPAM）+ 线缆管理 + 到期提醒
-- ============================================================

-- ──────────────────── 1. 功率容量 ────────────────────
-- 资产额定/预估功率（W）。为空时依次取「型号默认」→「分类默认」（power_profile）
ALTER TABLE asset ADD COLUMN IF NOT EXISTS rated_power_w INTEGER;
ALTER TABLE asset DROP CONSTRAINT IF EXISTS ck_asset_rated_power;
ALTER TABLE asset ADD CONSTRAINT ck_asset_rated_power CHECK (rated_power_w IS NULL OR rated_power_w BETWEEN 0 AND 200000);

-- 功率模板：按型号或分类给出默认功率，10 万台规模下不必逐台填写
CREATE TABLE IF NOT EXISTS power_profile (
    id          BIGSERIAL PRIMARY KEY,
    match_type  VARCHAR(10)  NOT NULL CHECK (match_type IN ('MODEL', 'CATEGORY')),
    match_value VARCHAR(80)  NOT NULL,
    rated_w     INTEGER      NOT NULL CHECK (rated_w BETWEEN 0 AND 200000),
    remark      VARCHAR(200),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (match_type, match_value)
);

-- 资产生效功率：资产值 > 型号模板 > 分类模板（逐级向上匹配父分类）
CREATE OR REPLACE VIEW v_asset_power AS
SELECT a.asset_no,
       a.rack_code,
       a.room_code,
       a.status_biz,
       COALESCE(a.rated_power_w, pm.rated_w, pc.rated_w) AS power_w,
       CASE WHEN a.rated_power_w IS NOT NULL THEN 'ASSET'
            WHEN pm.rated_w IS NOT NULL THEN 'MODEL'
            WHEN pc.rated_w IS NOT NULL THEN 'CATEGORY' END AS power_source
FROM asset a
LEFT JOIN power_profile pm ON pm.match_type = 'MODEL' AND pm.match_value = a.model_code
LEFT JOIN category c ON c.category_code = a.category_code
LEFT JOIN LATERAL (
    SELECT p.rated_w
    FROM power_profile p
    JOIN category pc2 ON pc2.category_code = p.match_value
    WHERE p.match_type = 'CATEGORY' AND c.path LIKE pc2.path || '%'
    ORDER BY length(pc2.path) DESC
    LIMIT 1
) pc ON TRUE
WHERE a.deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS capacity_setting (
    setting_key   VARCHAR(50) PRIMARY KEY,
    setting_value VARCHAR(200) NOT NULL,
    remark        VARCHAR(200),
    updated_at    TIMESTAMP NOT NULL DEFAULT NOW()
);
INSERT INTO capacity_setting (setting_key, setting_value, remark) VALUES
  ('power_warn_pct', '80', '机柜功率分配率预警阈值（%）'),
  ('power_max_pct', '95', '机柜功率分配率上限（%），超过则禁止上架推荐'),
  ('power_statuses', '在用(计费),维修中,停用中,待验收,待上架', '计入机柜功率的资产状态'),
  ('warranty_remind_days', '60,30,7', '保修到期提醒节点（提前天数，逗号分隔）'),
  ('contract_remind_days', '90,30,7', '合同到期提醒节点（提前天数，逗号分隔）'),
  ('remind_roles', 'R-ASSET', '到期提醒通知的角色（逗号分隔）')
ON CONFLICT (setting_key) DO NOTHING;

-- ──────────────────── 2. IPAM ────────────────────
-- 容错转换：非法 IP 字符串返回 NULL（资产表 IP 字段历史数据可能不规范）。
-- IPv4 走正则 + 直接转换（SQL 函数可内联、无子事务，10 万级扫描毫秒级）；含冒号的按 IPv6 走带异常保护的慢路径。
CREATE OR REPLACE FUNCTION try_inet6(t TEXT) RETURNS INET
    LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
BEGIN
    RETURN host(t::inet)::inet;
EXCEPTION WHEN others THEN
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION try_inet(t TEXT) RETURNS INET
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
SELECT CASE
         WHEN t ~ '^\s*((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])(/(3[0-2]|[12]?[0-9]))?\s*$'
           THEN host(btrim(t)::inet)::inet
         WHEN t ~ '^\s*[0-9A-Fa-f:.]*:[0-9A-Fa-f:.]*(/[0-9]{1,3})?\s*$'
           THEN try_inet6(btrim(t))
         ELSE NULL
       END
$$;

CREATE TABLE IF NOT EXISTS ipam_vlan (
    id          BIGSERIAL PRIMARY KEY,
    vlan_id     INTEGER      NOT NULL CHECK (vlan_id BETWEEN 1 AND 4094),
    name        VARCHAR(100) NOT NULL,
    room_code   VARCHAR(50),
    purpose     VARCHAR(20),
    remark      VARCHAR(200),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
-- 同一机房（或全局）内 VLAN ID 唯一
CREATE UNIQUE INDEX IF NOT EXISTS ux_ipam_vlan ON ipam_vlan (vlan_id, COALESCE(room_code, ''));

CREATE TABLE IF NOT EXISTS ipam_prefix (
    id          BIGSERIAL PRIMARY KEY,
    prefix      CIDR         NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    purpose     VARCHAR(20)  NOT NULL DEFAULT 'BIZ' CHECK (purpose IN ('BIZ', 'MGMT', 'BMC', 'STORAGE', 'IB', 'OTHER')),
    vlan_ref    BIGINT       REFERENCES ipam_vlan (id) ON DELETE SET NULL,
    room_code   VARCHAR(50),
    gateway     INET,
    dns         VARCHAR(200),
    remark      VARCHAR(200),
    created_by  VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ipam_prefix_gist ON ipam_prefix USING gist (prefix inet_ops);

CREATE TABLE IF NOT EXISTS ipam_address (
    id          BIGSERIAL PRIMARY KEY,
    ip          INET         NOT NULL UNIQUE,
    status      VARCHAR(20)  NOT NULL DEFAULT 'USED' CHECK (status IN ('USED', 'RESERVED', 'DHCP')),
    asset_no    VARCHAR(50),
    ip_role     VARCHAR(10)  CHECK (ip_role IN ('biz', 'mgmt', 'other')),
    hostname    VARCHAR(200),
    source      VARCHAR(10)  NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('ASSET', 'MANUAL')),
    remark      VARCHAR(200),
    created_by  VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ipam_address_gist ON ipam_address USING gist (ip inet_ops);
CREATE INDEX IF NOT EXISTS idx_ipam_address_asset ON ipam_address (asset_no);

-- 资产 IP 的函数索引：冲突检测 / 反查
CREATE INDEX IF NOT EXISTS idx_asset_biz_inet ON asset (try_inet(biz_ip)) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_asset_mgmt_inet ON asset (try_inet(mgmt_ip)) WHERE deleted_at IS NULL;

-- ──────────────────── 3. 线缆 ────────────────────
CREATE TABLE IF NOT EXISTS cable (
    id          BIGSERIAL PRIMARY KEY,
    cable_no    VARCHAR(50)  NOT NULL UNIQUE,
    cable_type  VARCHAR(20)  NOT NULL CHECK (cable_type IN ('POWER', 'COPPER', 'FIBER', 'DAC', 'AOC', 'IB', 'OTHER')),
    a_asset_no  VARCHAR(50)  NOT NULL,
    a_port      VARCHAR(50)  NOT NULL,
    b_asset_no  VARCHAR(50)  NOT NULL,
    b_port      VARCHAR(50)  NOT NULL,
    length_m    NUMERIC(8, 2),
    color       VARCHAR(20),
    speed       VARCHAR(20),
    status      VARCHAR(20)  NOT NULL DEFAULT 'CONNECTED' CHECK (status IN ('PLANNED', 'CONNECTED', 'REMOVED')),
    remark      VARCHAR(200),
    created_by  VARCHAR(50),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CHECK (NOT (a_asset_no = b_asset_no AND a_port = b_port))
);
-- 一个端口同时只能接一根线（A/B 两端统一判断，见 CableService；此处兜底同端唯一）
CREATE UNIQUE INDEX IF NOT EXISTS ux_cable_a_port ON cable (a_asset_no, lower(a_port)) WHERE status <> 'REMOVED';
CREATE UNIQUE INDEX IF NOT EXISTS ux_cable_b_port ON cable (b_asset_no, lower(b_port)) WHERE status <> 'REMOVED';
CREATE INDEX IF NOT EXISTS idx_cable_b_asset ON cable (b_asset_no);

-- ──────────────────── 4. 到期提醒去重 ────────────────────
CREATE TABLE IF NOT EXISTS expiry_notice (
    id          BIGSERIAL PRIMARY KEY,
    kind        VARCHAR(20) NOT NULL,     -- WARRANTY / CONTRACT
    ref_no      VARCHAR(50) NOT NULL,     -- 资产编号 / 合同编号
    due_date    DATE        NOT NULL,
    stage_days  INTEGER     NOT NULL,     -- 提醒节点（提前天数，0 = 当天到期）
    notified_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE (kind, ref_no, due_date, stage_days)
);

-- ──────────────────── 5. 权限 ────────────────────
-- 新模块 network（IP 地址 / VLAN / 线缆）；容量归 racks；到期提醒归 assets / contracts
INSERT INTO role_module_perm (role_code, module, can_read, can_write)
SELECT r.role_code, 'network', TRUE, r.role_code IN ('R-OPS', 'R-ASSET', 'R-DELIVERY')
FROM roles r
WHERE r.role_code IN ('R-OPS', 'R-ASSET', 'R-DELIVERY', 'R-LEADER', 'LEADER', 'VIEWER')
ON CONFLICT (role_code, module) DO NOTHING;

-- V39 只给了 VIEWER 监控只读：补齐运维角色（运维工程师读写，资产/交付/负责人只读）
INSERT INTO role_module_perm (role_code, module, can_read, can_write)
SELECT r.role_code, 'monitoring', TRUE, r.role_code = 'R-OPS'
FROM roles r
WHERE r.role_code IN ('R-OPS', 'R-ASSET', 'R-DELIVERY', 'R-LEADER', 'LEADER')
ON CONFLICT (role_code, module) DO NOTHING;
