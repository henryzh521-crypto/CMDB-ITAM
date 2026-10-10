-- ============================================================
-- V17__workorder_p5.sql — 工单模块 P0/P1/P2 迭代
--   1. workorder 检索 / 审批 / SLA 扫描 索引补齐（V1 只有 3 个索引）
--   2. sla_config 补齐 4 类缺失类型的 SLA 矩阵 + escalate_to 兜底
--      （V2 种子 escalate_to 全为 NULL → SLA 升级的「升级到角色」通知永不触发）
--   3. 列/表注释与实现对齐（V1 遗留「9 类工单」、flow.action 列了不存在的接单等）
-- 全部幂等（IF NOT EXISTS / ON CONFLICT DO NOTHING / 条件 UPDATE）
-- ============================================================

-- ──────────────────── 1. 索引 ────────────────────

-- 父子替换子单：childWosOf 按 parent_wo 查询（V1 起无索引，逐单全表扫）
CREATE INDEX IF NOT EXISTS idx_wo_parent ON workorder (parent_wo);

-- 列表默认排序 + 按申请时间区间筛选
CREATE INDEX IF NOT EXISTS idx_wo_apply_time ON workorder (apply_time DESC);

-- 新增的处理人筛选
CREATE INDEX IF NOT EXISTS idx_wo_handler ON workorder (handler);

-- 待我审批 tab：status=待审批 已有 idx_wo_status，再按 (类型, 级别) 精确定位
CREATE INDEX IF NOT EXISTS idx_wo_approve_level ON workorder (wo_type, approval_level);

-- SLA 升级扫描每 60 秒跑一次，条件是 sla_resolve_deadline <= now()：
-- V1 起没有任何索引 → 每分钟一次全表扫。部分索引只覆盖有 SLA 的行，体积很小。
CREATE INDEX IF NOT EXISTS idx_wo_sla_deadline ON workorder (sla_resolve_deadline)
    WHERE sla_resolve_deadline IS NOT NULL;

-- 关闭/完成类按 finish_time 的统计与筛选
CREATE INDEX IF NOT EXISTS idx_wo_finish_time ON workorder (finish_time DESC);

-- 催办节流按 (wo_no, action='催办') 取最近一条
CREATE INDEX IF NOT EXISTS idx_wo_flow_action ON workorder_flow (wo_no, action);

-- ──────────────────── 2. SLA 配置补齐 ────────────────────
-- 时限档位对齐既有种子：高 = 快速响应，中 = 常规，低 = 宽松
INSERT INTO sla_config (wo_type, priority, response_minutes, resolve_minutes, escalate_to) VALUES
  ('耗材申领', '高',  60,  480, 'R-LEADER'),
  ('耗材申领', '中', 240, 1440, 'R-LEADER'),
  ('耗材申领', '低', 480, 2880, 'R-LEADER'),
  ('资产盘点', '高', 240, 1440, 'R-LEADER'),
  ('资产盘点', '中', 480, 2880, 'R-LEADER'),
  ('资产盘点', '低', 480, 2880, 'R-LEADER'),
  ('报废处置', '高',  60,  480, 'R-LEADER'),
  ('报废处置', '中', 240, 1440, 'R-LEADER'),
  ('报废处置', '低', 480, 2880, 'R-LEADER'),
  ('入库验收', '高', 120, 1440, 'R-LEADER'),
  ('入库验收', '中', 480, 2880, 'R-LEADER'),
  ('入库验收', '低', 480, 2880, 'R-LEADER')
ON CONFLICT (wo_type, priority) DO NOTHING;

-- 既有 12 行 escalate_to 全为 NULL → 升级只通知申请人+处理人，永远不会升级到角色
UPDATE sla_config SET escalate_to = 'R-LEADER' WHERE escalate_to IS NULL;

-- ──────────────────── 3. 注释与实现对齐 ────────────────────

COMMENT ON TABLE workorder IS '运维工单主表（10 类类型 × 6 态状态机 × 多级审批 × SLA）';
COMMENT ON COLUMN workorder.wo_type IS
    '工单类型（workflow_config 唯一约束）：故障维修/变更/巡检/入库验收/资产盘点/耗材申领/上架部署/下架退库/报废处置/调拨';
COMMENT ON COLUMN workorder.status IS
    '状态（WorkorderStateMachine 决策表）：待处理/待审批/处理中/已完成/已驳回/已关闭';
COMMENT ON COLUMN workorder.approval_level IS
    '当前审批级别（0 起算，序 = workflow_config.approval_roles 数组序；提交=0、每过一级+1、驳回/重提清 0）';
COMMENT ON COLUMN workorder.complete_note IS '完成说明（完成工单动作可填，WorkorderController.TransitionReq.completeNote）';
COMMENT ON COLUMN workorder.process IS '处理过程（历史遗留列，当前无写入口，展示已下线）';
COMMENT ON COLUMN workorder.related_assets IS
    '关联资产编号 CSV；workorder_item 行表是权威来源，本列由 syncRelatedAssetsCsv 回写（上限 500 字符，超长业务 400）';
COMMENT ON COLUMN workorder.related_materials IS '关联物料编码 CSV；创建时若未显式传入，由 details 用料明细回填';

COMMENT ON TABLE workorder_flow IS '工单流转与讨论留痕（append-only；seq 同单内递增，UNIQUE(wo_no, seq)）';
COMMENT ON COLUMN workorder_flow.action IS
    '状态机动作：开始处理/提交审批/审批通过/驳回/重新提交/完成工单/退回/关闭/重新打开；非流转留痕：催办/评论';
COMMENT ON COLUMN workorder_flow.comment IS '审批/驳回意见、关闭根因、催办附言、评论正文';

COMMENT ON TABLE workorder_item IS '工单资产行项目（item_no = woNo-四位序号；src 为创建时台账快照，dst 空则完工回退主表）';
COMMENT ON TABLE workflow_config IS '工作流配置：wo_type 唯一；need_approval 开关；approval_roles JSONB 数组序 = 审批级别序';
COMMENT ON TABLE sla_config IS 'SLA 配置：wo_type × priority → 响应/解决时限；escalate_to = 超期升级通知的角色编码';
COMMENT ON COLUMN sla_config.escalate_to IS '超期升级通知的角色编码（空 = 只通知申请人与处理人）';
