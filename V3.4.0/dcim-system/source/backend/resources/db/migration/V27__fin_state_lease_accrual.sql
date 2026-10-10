-- ============================================================
-- V27__fin_state_lease_accrual.sql — 财务状态字典补齐租赁计提态「折旧+利息」
--
-- 背景：finance 包 FinanceStateMachine 的租赁轨是
--     使用权资产确认 → 折旧+利息 → [留购 / 折旧完毕 / 处置中]
--   FinanceAssetService.capitalize（起租）会把资产 status_fin 写成「折旧+利息」，
--   但 asset_status_fin 字典里没有这个键，导致：
--     1) 资产列表/流转弹窗无法把该状态作为目标或当前值正常展示；
--     2) AssetStateMachine 财务矩阵此前也没这个键 → 起租后的资产在资产页
--        所有财务动作被冻结、允许目标列表为空。
--
-- 本迁移：
--   1) 幂等插入字典项「折旧+利息」
--   2) 与 FinanceStateMachine.finStates() 同序：使用权资产确认(8) → 折旧+利息(9) → 留购(10)
-- ============================================================

INSERT INTO dict (dict_type, dict_key, dict_value, sort, status)
VALUES ('asset_status_fin', '折旧+利息', '折旧+利息', 9, 1)
ON CONFLICT (dict_type, dict_key) DO NOTHING;

-- 留购 让位到第 10 位，保持租赁轨语义顺序（已存在则调整，不存在则不受影响）
UPDATE dict
   SET sort = 10
 WHERE dict_type = 'asset_status_fin'
   AND dict_key = '留购'
   AND sort = 9;
