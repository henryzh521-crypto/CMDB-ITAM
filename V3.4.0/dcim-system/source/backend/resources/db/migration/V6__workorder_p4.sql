-- ============================================================
-- V6__workorder_p4.sql — P4 工单域（SLA 列 + 通知表 + PRD 内置角色/权限种子）
--   1. workorder 增量列：SLA 时限/首次响应/升级计数 + 当前审批级别 approval_level
--   2. notification 表（站内通知，username+read_flag 索引）
--   3. 种子 PRD 内置角色（built_in=TRUE）
--   4. workflow_config 审批角色改用角色编码（按级数组，9 类类型名不变）
--   5. role_module_perm 种子（各内置角色模块读写权限）
-- 全部幂等（ON CONFLICT DO NOTHING / 条件更新）
-- ============================================================

-- ──────────────────── 1. workorder 增量列 ────────────────────
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS sla_response_deadline TIMESTAMPTZ;   -- SLA 响应时限 = apply_time + response_minutes
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS sla_resolve_deadline  TIMESTAMPTZ;   -- SLA 解决时限 = apply_time + resolve_minutes
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS first_response_at     TIMESTAMPTZ;   -- 首次响应：申请人以外的人第一次流转时置 now（一次）
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS sla_escalations       INT NOT NULL DEFAULT 0;    -- SLA 升级次数（封顶 5）
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS last_escalation_at    TIMESTAMPTZ;   -- 最近升级时间（60 分钟节流）
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS approval_level        INT NOT NULL DEFAULT 0;    -- 当前审批级别（0 起算；提交审批=0，每通过一级 +1，驳回清 0）

-- ──────────────────── 2. 站内通知表 ────────────────────
CREATE TABLE IF NOT EXISTS notification (
    id         BIGSERIAL PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL,                 -- 接收人登录名
    type       VARCHAR(30),                           -- WO_APPROVAL/WO_REJECT/WO_APPROVED/WO_DONE/SLA...
    title      VARCHAR(200),
    content    TEXT,
    ref_id     VARCHAR(50),                           -- 关联工单号
    read_flag  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_notification_user ON notification (username, read_flag);

-- ──────────────────── 3. PRD 内置角色 ────────────────────
INSERT INTO roles (role_code, name, description, built_in) VALUES
  ('R-ASSET',     '资产管理专员', '资产台账/合同/折旧/机柜管理，工单审批与办理（PRD R-ASSET）', TRUE),
  ('R-WAREHOUSE', '仓库管理员',   '物料/出入库/盘点管理，耗材申领一级审批（PRD R-WAREHOUSE）', TRUE),
  ('R-DELIVERY',  '交付专员',     '资产交付上架，发起部署类工单（PRD R-DELIVERY）', TRUE),
  ('R-OPS',       '机房运维工程师','机房/机柜/资产运维，故障维修处理（PRD R-OPS）', TRUE),
  ('R-FIN-VIEW',  '财务只读',     '资产/折旧/合同只读（PRD R-FIN-VIEW）', TRUE),
  ('R-LEADER',    '部门负责人',   '全模块只读 + 工单读写，多级审批的负责人级（PRD R-LEADER）', TRUE)
ON CONFLICT (role_code) DO NOTHING;

-- ──────────────────── 4. workflow_config 审批角色改用角色编码（按级） ────────────────────
-- 数组顺序 = 审批级别顺序（第 1 级 / 第 2 级 ...）；免审类保持 '[]'
UPDATE workflow_config SET approval_roles = '["R-LEADER"]',             updated_at = now() WHERE wo_type = '变更';
UPDATE workflow_config SET approval_roles = '["R-WAREHOUSE","R-ASSET"]', updated_at = now() WHERE wo_type = '耗材申领';
UPDATE workflow_config SET approval_roles = '["R-ASSET"]',               updated_at = now() WHERE wo_type = '下架退库';
UPDATE workflow_config SET approval_roles = '["R-ASSET","R-LEADER"]',    updated_at = now() WHERE wo_type = '报废处置';
UPDATE workflow_config SET approval_roles = '[]',                        updated_at = now()
  WHERE wo_type IN ('故障维修', '巡检', '入库验收', '资产盘点', '上架部署');

-- ──────────────────── 5. role_module_perm 种子 ────────────────────
-- R-ASSET 资产管理专员：资产/分类/折旧/合同/机柜/机房 r+w + 工单 r+w
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-ASSET', 'assets',       TRUE, TRUE),
  ('R-ASSET', 'categories',   TRUE, TRUE),
  ('R-ASSET', 'depreciation', TRUE, TRUE),
  ('R-ASSET', 'contracts',    TRUE, TRUE),
  ('R-ASSET', 'racks',        TRUE, TRUE),
  ('R-ASSET', 'machineRooms', TRUE, TRUE),
  ('R-ASSET', 'workorders',   TRUE, TRUE)
ON CONFLICT (role_code, module) DO NOTHING;

-- R-OPS 机房运维工程师：机房/机柜 r+w + 资产/工单 r+w，其余无
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-OPS', 'racks',        TRUE, TRUE),
  ('R-OPS', 'machineRooms', TRUE, TRUE),
  ('R-OPS', 'assets',       TRUE, TRUE),
  ('R-OPS', 'workorders',   TRUE, TRUE)
ON CONFLICT (role_code, module) DO NOTHING;

-- R-WAREHOUSE 仓库管理员：物料/出入库/盘点 r+w + 工单 r+w
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-WAREHOUSE', 'materials',   TRUE, TRUE),
  ('R-WAREHOUSE', 'stockLogs',   TRUE, TRUE),
  ('R-WAREHOUSE', 'inventories', TRUE, TRUE),
  ('R-WAREHOUSE', 'workorders',  TRUE, TRUE)
ON CONFLICT (role_code, module) DO NOTHING;

-- R-DELIVERY 交付专员：资产 r+w + 工单 r+w
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-DELIVERY', 'assets',     TRUE, TRUE),
  ('R-DELIVERY', 'workorders', TRUE, TRUE)
ON CONFLICT (role_code, module) DO NOTHING;

-- R-FIN-VIEW 财务只读：资产/折旧/合同只读
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-FIN-VIEW', 'assets',       TRUE, FALSE),
  ('R-FIN-VIEW', 'depreciation', TRUE, FALSE),
  ('R-FIN-VIEW', 'contracts',    TRUE, FALSE)
ON CONFLICT (role_code, module) DO NOTHING;

-- R-LEADER 部门负责人：全部模块只读 + 工单 r+w（审批需要写权限）
INSERT INTO role_module_perm (role_code, module, can_read, can_write)
SELECT 'R-LEADER', m.code, TRUE, (m.code = 'workorders')
FROM (VALUES
  ('departments'), ('racks'), ('machineRooms'), ('assets'), ('suppliers'),
  ('materials'), ('stockLogs'), ('workorders'), ('inventories'), ('knowledge'),
  ('categories'), ('contracts'), ('depreciation'), ('people'), ('units'),
  ('materialCats'), ('codeRules'), ('system')
) AS m(code)
ON CONFLICT (role_code, module) DO NOTHING;
