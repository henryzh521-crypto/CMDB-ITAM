-- ============================================================
-- V29__wo_repair_analysis.sql — 维修数据分析三件套
--   背景：报表中心此前只有「按状态/类型/优先级计数 + SLA」，
--         维修时长、时间趋势、按工单备件消耗、维修费用、根因分布
--         要么算不出来（无字段/无索引），要么没接口。
--   1. 根因分类字典化：root_cause 此前是自由文本，分布统计必然失真
--   2. 维修费用字段：人工/外委/其他（备件费由 stock_log 按工单反算，不重复存）
--   3. 聚合索引：工单按类型×申请时间的趋势扫描、stock_log 按工单反查
-- 全部幂等
-- ============================================================

-- ──────────────────── 1. 根因分类字典 ────────────────────
-- dict_key 即 workorder.root_cause 的落库值（列长 VARCHAR(50)，故 key ≤ 50）。
-- 维度按「部件级硬件 / 软件 / 网络 / 环境 / 人为 / 耗损 / 无法定位」划分，
-- 覆盖算力集群常见故障归因；可在 系统管理→数据字典(wo_root_cause) 自行增删。
INSERT INTO dict (dict_type, dict_key, dict_value, sort) VALUES
  ('wo_root_cause', '硬件故障-GPU',       '硬件故障-GPU',       1),
  ('wo_root_cause', '硬件故障-CPU',       '硬件故障-CPU',       2),
  ('wo_root_cause', '硬件故障-内存',      '硬件故障-内存',      3),
  ('wo_root_cause', '硬件故障-硬盘',      '硬件故障-硬盘',      4),
  ('wo_root_cause', '硬件故障-网卡',      '硬件故障-网卡',      5),
  ('wo_root_cause', '硬件故障-电源',      '硬件故障-电源',      6),
  ('wo_root_cause', '硬件故障-风扇散热',  '硬件故障-风扇散热',  7),
  ('wo_root_cause', '硬件故障-主板',      '硬件故障-主板',      8),
  ('wo_root_cause', '硬件故障-其他部件',  '硬件故障-其他部件',  9),
  ('wo_root_cause', '软件故障-驱动',      '软件故障-驱动',     10),
  ('wo_root_cause', '软件故障-系统',      '软件故障-系统',     11),
  ('wo_root_cause', '软件故障-中间件',    '软件故障-中间件',   12),
  ('wo_root_cause', '网络故障',           '网络故障',          13),
  ('wo_root_cause', '散热/环境异常',      '散热/环境异常',     14),
  ('wo_root_cause', '配置错误/误操作',    '配置错误/误操作',   15),
  ('wo_root_cause', '正常老化/寿命到期',  '正常老化/寿命到期', 16),
  ('wo_root_cause', '无法复现',           '无法复现',          17),
  ('wo_root_cause', '其他',               '其他',              18)
ON CONFLICT (dict_type, dict_key) DO NOTHING;

-- ──────────────────── 2. 维修费用字段 ────────────────────
-- 只对维修类工单（故障维修/部件更换）有意义，其余类型恒为 0。
-- 备件费不落列：由 stock_log.ref_wo = wo_no + material.price 实时反算，
-- 避免「出库流水改了、工单上的备件费没跟着改」的双写不一致。
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS cost_labor  NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS cost_service NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE workorder ADD COLUMN IF NOT EXISTS cost_other  NUMERIC(14,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN workorder.cost_labor  IS '维修人工费（元）；仅故障维修/部件更换，完成工单或事后补录';
COMMENT ON COLUMN workorder.cost_service IS '外委/厂商服务费（元）；仅故障维修/部件更换';
COMMENT ON COLUMN workorder.cost_other  IS '其他费用（元，差旅/加急/杂项）；仅故障维修/部件更换';

-- 非负约束（DEFAULT 0 已保证存量；CHECK 名带表名避免跨表重名）
ALTER TABLE workorder DROP CONSTRAINT IF EXISTS chk_workorder_cost_nonneg;
ALTER TABLE workorder ADD CONSTRAINT chk_workorder_cost_nonneg
    CHECK (cost_labor >= 0 AND cost_service >= 0 AND cost_other >= 0);

-- ──────────────────── 3. 聚合索引 ────────────────────
-- 工单趋势/时长/费用报表固定按 wo_type IN (维修类) + apply_time 区间扫描；
-- 既有 idx_wo_type 只有类型、idx_wo_applicant 只有申请人，都走不到时间范围。
CREATE INDEX IF NOT EXISTS idx_wo_type_apply_time
    ON workorder (wo_type, apply_time DESC);

-- 按工单聚合备件消耗（stock_log 是 RANGE 分区表，父表建索引会同步建到各分区）
CREATE INDEX IF NOT EXISTS idx_stock_log_ref_wo
    ON stock_log (ref_wo, created_at DESC);
