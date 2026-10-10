-- ============================================================
-- V18__wo_part_replace.sql — 部件更换工单（第 11 类）
--   背景：计划性/预防性换件此前无处落单，只能谎报「故障维修」。
--         旧系统本有独立「备件更换」（spare_replace / TPL-SPARE），
--         ETL 迁移时被并入故障维修；本次恢复为独立类型。
--   1. workflow_config：部件更换 需审批 [R-ASSET]（资产归口把关配置变更）
--   2. sla_config：部件更换 高/中/低（escalate_to 对齐全库统一 R-LEADER）
--   3. 注释与实现对齐（10 类 → 11 类；replacements 补 oldStatus）
-- 换件机制本身复用既有 workorder.replacements JSONB + AssetOpsPort.replaceComponent，
-- 本次仅把 JSONB 每行的旧件换下状态由写死「维修中」改为可选 oldStatus。
-- 全部幂等
-- ============================================================

-- ──────────────────── 1. 部件更换工作流 ────────────────────
INSERT INTO workflow_config (wo_type, need_approval, approval_roles, built_in) VALUES
  ('部件更换', TRUE, '["R-ASSET"]', TRUE)
ON CONFLICT (wo_type) DO NOTHING;

-- ──────────────────── 2. 部件更换 SLA ────────────────────
INSERT INTO sla_config (wo_type, priority, response_minutes, resolve_minutes, escalate_to) VALUES
  ('部件更换', '高',   60,  480, 'R-LEADER'),
  ('部件更换', '中',  240, 1440, 'R-LEADER'),
  ('部件更换', '低',  480, 2880, 'R-LEADER')
ON CONFLICT (wo_type, priority) DO NOTHING;

-- ──────────────────── 3. 注释与实现对齐 ────────────────────
COMMENT ON TABLE workorder IS '运维工单主表（11 类类型 × 6 态状态机 × 多级审批 × SLA）';
COMMENT ON COLUMN workorder.wo_type IS
    '工单类型（workflow_config 唯一约束）：故障维修/部件更换/变更/巡检/入库验收/资产盘点/耗材申领/上架部署/下架退库/报废处置/调拨';
COMMENT ON COLUMN workorder.replacements IS
    '子件更换明细 [{parentAssetNo,oldAssetNo,newAssetNo,oldStatus?}]（故障维修/部件更换完成时联动换件；oldStatus 空则换下为「维修中」，可选 维修中/停用中/待退役）';
