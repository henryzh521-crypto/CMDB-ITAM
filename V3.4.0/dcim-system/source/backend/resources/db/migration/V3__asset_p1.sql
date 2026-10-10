-- ============================================================
-- V3__asset_p1.sql — P1 资产域配套数据
--   1. asset_no_seq：资产编码发号器（按 前缀段 分行加锁，并发安全）
--   2. dict region 字典：区域编码 → 中文名（资产编码区域段 + meta 接口下拉）
--   3. category 一级分类 8 条（PRD 五级树的第一层，path = '/'+code+'/'）
-- ============================================================

-- 资产编码发号器：prefix = {类别码}-{区域码}{年2位}-{年4位}，next_no 为下一可用流水
CREATE TABLE asset_no_seq (
    prefix  VARCHAR(50) PRIMARY KEY,
    next_no BIGINT      NOT NULL DEFAULT 0
);

-- 区域字典（QY=庆阳、WLCB=乌兰察布、SG=韶关，与 PRD 数据中心布局一致）
INSERT INTO dict (dict_type, dict_key, dict_value, sort) VALUES
  ('region', 'QY',   '庆阳',     1),
  ('region', 'WLCB', '乌兰察布', 2),
  ('region', 'SG',   '韶关',     3)
ON CONFLICT (dict_type, dict_key) DO NOTHING;

-- 一级分类 8 条（SRV/GPU/NET/STO/FAC/SPR/CON/SOF，编码与 V1 注释一致）
INSERT INTO category (category_code, name, parent_code, level, path, sort) VALUES
  ('SRV', '服务器',   NULL, 1, '/SRV/', 1),
  ('GPU', '算力卡',   NULL, 1, '/GPU/', 2),
  ('NET', '网络设备', NULL, 1, '/NET/', 3),
  ('STO', '存储设备', NULL, 1, '/STO/', 4),
  ('FAC', '机房设施', NULL, 1, '/FAC/', 5),
  ('SPR', '备品备件', NULL, 1, '/SPR/', 6),
  ('CON', '耗材',     NULL, 1, '/CON/', 7),
  ('SOF', '软件资产', NULL, 1, '/SOF/', 8)
ON CONFLICT (category_code) DO NOTHING;
