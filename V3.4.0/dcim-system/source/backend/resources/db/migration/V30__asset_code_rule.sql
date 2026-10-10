-- ─────────────────────────────────────────────────────────────
-- V30 资产编码规则改为「可配置」（字典 code_rule）
--
-- 背景：V15 往 code_rule 塞的 3 条是仓储编码备注（WH/SH/LY），没有任何代码读取；
--       真正会被用到的资产编号规则此前硬编码在 AssetNoGenerator（写死
--       {类别码}-{区域码}{年2位}-{年4位}-{5位流水}，如 SRV-QY26-2026-00001）。
-- 本迁移：
--   1) 清掉 code_rule 下的仓储说明条目（仓储编码规则在 WarehouseController 里
--      硬编码发号，字典里留着只会误导成"改这里能改编号"）
--   2) 只留全局一条资产规则 dict_key='asset'，dict_value 即模板；
--      默认值与原硬编码规则逐字符一致 → 存量编号、asset_no_seq 前缀行全部延续
-- ─────────────────────────────────────────────────────────────

DELETE FROM dict WHERE dict_type = 'code_rule';

INSERT INTO dict (dict_type, dict_key, dict_value, sort) VALUES
  ('code_rule', 'asset', '{CATEGORY}-{REGION}{YY}-{YYYY}-{SEQ:5}', 1)
ON CONFLICT (dict_type, dict_key) DO NOTHING;
