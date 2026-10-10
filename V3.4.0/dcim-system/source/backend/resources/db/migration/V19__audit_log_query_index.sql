-- V19: 操作日志查询索引
--
-- 现有索引只有 (actor, created_at) 与 (module, created_at)，
-- 而操作日志页的主查询是「时间范围过滤 + created_at DESC 分页」，
-- 补一条 created_at 单列索引，配合 RANGE 分区裁剪保证数据量增长后仍走索引。
-- 分区表上的 CREATE INDEX 会自动递归建到各分区（含 default 分区）。

CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log (created_at DESC);
