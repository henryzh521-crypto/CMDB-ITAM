-- ============================================================
-- V14__wo_replacements.sql — 故障维修子件更换明细
--   workorder.replacements JSONB：[{parentAssetNo,oldAssetNo,newAssetNo}]
--   完成工单时按明细联动换件（旧件解绑→维修中；新件挂父→在用(计费)）
-- ============================================================

ALTER TABLE workorder ADD COLUMN IF NOT EXISTS replacements JSONB NOT NULL DEFAULT '[]';

COMMENT ON COLUMN workorder.replacements IS '子件更换明细 [{parentAssetNo,oldAssetNo,newAssetNo}]（故障维修完成时联动换件）';
