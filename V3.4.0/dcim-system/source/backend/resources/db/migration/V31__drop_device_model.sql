-- 型号库下线：device_model 全表仅 2 行僵尸数据（GPU001/B300），且无任何外键引用。
-- asset.model_code 保留为自由文本（1700 台资产已填值，导入/导出/采集/RFID 链路沿用）。
-- 采集硬件快照的「型号规格差异比对」一并移除（hw_snapshot.spec_diff 全表 0 行，从未产生数据）。

DROP TABLE IF EXISTS device_model;

ALTER TABLE hw_snapshot DROP COLUMN IF EXISTS spec_diff;
