-- v3.5.2 硬化：hw_snapshot 保留策略支撑索引
-- SnapshotRetentionJob（dcim.collector.snapshot-retention-days，默认 180 天）按 collected_at
-- 过滤分批删除过期快照；此前的 (endpoint_id, collected_at DESC) 索引无法支撑该时间过滤路径。
CREATE INDEX IF NOT EXISTS idx_hw_snapshot_collected ON hw_snapshot (collected_at);
