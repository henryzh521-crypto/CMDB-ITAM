-- ============================================================
-- V11__workorder_transfer.sql — 调拨工单（第 10 类）
--   1. workflow_config：调拨 需审批 [R-ASSET]（对齐旧「调拨迁移/资产管理员」）
--   2. sla_config：调拨 高/中/低
--   3. machine_room.region_code：机房→区域（调拨跨地域判定）
--   4. workorder_item.src_region / dst_region：行级区域快照
--   5. workorder.before_location 扩为 TEXT（多资产源位置 JSON 快照）
-- 全部幂等
-- ============================================================

-- ──────────────────── 1. 调拨工作流 ────────────────────
INSERT INTO workflow_config (wo_type, need_approval, approval_roles, built_in) VALUES
  ('调拨', TRUE, '["R-ASSET"]', TRUE)
ON CONFLICT (wo_type) DO NOTHING;

-- ──────────────────── 2. 调拨 SLA ────────────────────
INSERT INTO sla_config (wo_type, priority, response_minutes, resolve_minutes) VALUES
  ('调拨', '高',   60,  480),
  ('调拨', '中',   240, 1440),
  ('调拨', '低',   480, 2880)
ON CONFLICT (wo_type, priority) DO NOTHING;

-- ──────────────────── 3. 机房区域码 ────────────────────
ALTER TABLE machine_room ADD COLUMN IF NOT EXISTS region_code VARCHAR(20);

-- 回填：room_code 前缀 → 区域字典键（QY/WLCB/SG）
UPDATE machine_room SET region_code = 'QY'
WHERE region_code IS NULL AND room_code ILIKE 'QY%';
UPDATE machine_room SET region_code = 'WLCB'
WHERE region_code IS NULL AND room_code ILIKE 'WLCB%';
UPDATE machine_room SET region_code = 'SG'
WHERE region_code IS NULL AND room_code ILIKE 'SG%';

-- 回填：data_center 文本 → 区域
UPDATE machine_room SET region_code = 'QY'
WHERE region_code IS NULL AND data_center LIKE '%庆阳%';
UPDATE machine_room SET region_code = 'WLCB'
WHERE region_code IS NULL AND data_center LIKE '%乌兰察布%';
UPDATE machine_room SET region_code = 'SG'
WHERE region_code IS NULL AND data_center LIKE '%韶关%';

-- ──────────────────── 4. 行级区域 ────────────────────
ALTER TABLE workorder_item ADD COLUMN IF NOT EXISTS src_region VARCHAR(20);
ALTER TABLE workorder_item ADD COLUMN IF NOT EXISTS dst_region VARCHAR(20);

-- 存量回填：src_region ← 资产 region_code
UPDATE workorder_item i SET src_region = a.region_code
FROM asset a
WHERE a.asset_no = i.asset_no AND i.src_region IS NULL AND a.region_code IS NOT NULL;

-- 存量回填：dst_region ← 目标机房 region_code（dst_room 已填时）
UPDATE workorder_item i SET dst_region = r.region_code
FROM machine_room r
WHERE r.room_code = i.dst_room AND i.dst_region IS NULL AND r.region_code IS NOT NULL;

-- ──────────────────── 5. 源位置快照扩容 ────────────────────
ALTER TABLE workorder ALTER COLUMN before_location TYPE TEXT;
