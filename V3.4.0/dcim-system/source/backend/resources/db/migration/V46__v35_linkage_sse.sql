-- ============================================================
-- V46__v35_linkage_sse.sql — v3.5 联动工单类型
--   1. workflow_config 注册两个免审联动工单类型：
--      - 盘点差异：InventoryDiffTicketService 在盘点完成且存在差异时
--        自动创建（与完成同事务，差异明细附在描述里）
--      - 补货：LowStockService ticket 模式在库存低于安全线时
--        自动创建（建议量 = 安全库存 - 当前库存，幂等防重）
--   2. notification (username, read_flag) 索引已由 V6 idx_notification_user
--      覆盖（SSE「我的未读数」与通知列表同一查询模式），无需新增
--   3. 不建新表：差异工单 / 补货工单复用 workorder，联动配置走 yml
-- 全部幂等（ON CONFLICT DO NOTHING）
-- ============================================================

INSERT INTO workflow_config (wo_type, need_approval, approval_roles, built_in) VALUES
  ('盘点差异', FALSE, '[]', TRUE),
  ('补货',     FALSE, '[]', TRUE)
ON CONFLICT (wo_type) DO NOTHING;
