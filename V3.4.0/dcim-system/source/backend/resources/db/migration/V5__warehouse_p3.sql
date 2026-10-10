-- ============================================================
-- V5__warehouse_p3.sql — P3 仓储盘点域种子数据（全部幂等，ON CONFLICT DO NOTHING）
--   1. dict unit：计量单位 7 项（物料主数据 unit 字段合法值域）
--   2. dict material_cat：物料分类 5 项（物料主数据 category 字段合法值域）
-- ============================================================

INSERT INTO dict (dict_type, dict_key, dict_value, sort) VALUES
  ('unit', '个', '个', 1),
  ('unit', '台', '台', 2),
  ('unit', '件', '件', 3),
  ('unit', '盒', '盒', 4),
  ('unit', '根', '根', 5),
  ('unit', '米', '米', 6),
  ('unit', '卷', '卷', 7)
ON CONFLICT (dict_type, dict_key) DO NOTHING;

INSERT INTO dict (dict_type, dict_key, dict_value, sort) VALUES
  ('material_cat', '耗材',     '耗材',     1),
  ('material_cat', '备品备件', '备品备件', 2),
  ('material_cat', '线缆',     '线缆',     3),
  ('material_cat', '网络配件', '网络配件', 4),
  ('material_cat', '机房耗材', '机房耗材', 5)
ON CONFLICT (dict_type, dict_key) DO NOTHING;
