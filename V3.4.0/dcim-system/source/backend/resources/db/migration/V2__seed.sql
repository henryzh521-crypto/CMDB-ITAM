-- ============================================================
-- V2__seed.sql — 种子数据（内置角色 + 工作流免审名单 + SLA 模板）
-- 注意：admin 用户由 DataInitializer 在应用启动时创建（BCrypt 运行时生成），
--       避免在 SQL 里硬编码密码哈希。
-- ============================================================

-- 内置角色：系统管理员（DataInitializer 绑定 admin 并授予全部模块权限）
INSERT INTO roles (role_code, name, description, built_in) VALUES
  ('ADMIN',   '系统管理员', '全部权限，内置不可删', TRUE),
  ('LEADER',  '部门负责人', '审批 + 全数据只读（PRD R-LEADER）', TRUE)
ON CONFLICT (role_code) DO NOTHING;

-- 工作流默认配置（对齐旧系统 9 类工单免审名单：故障维修/入库验收/资产盘点免审）
INSERT INTO workflow_config (wo_type, need_approval, approval_roles, built_in) VALUES
  ('故障维修', FALSE, '[]', TRUE),
  ('变更',     TRUE,  '["部门负责人"]', TRUE),
  ('巡检',     FALSE, '[]', TRUE),
  ('入库验收', FALSE, '[]', TRUE),
  ('资产盘点', FALSE, '[]', TRUE),
  ('耗材申领', TRUE,  '["部门负责人"]', TRUE),
  ('上架部署', FALSE, '[]', TRUE),
  ('下架退库', TRUE,  '["部门负责人"]', TRUE),
  ('报废处置', TRUE,  '["资产管理专员","部门负责人"]', TRUE)
ON CONFLICT (wo_type) DO NOTHING;

-- SLA 默认模板（响应/解决时限，分钟）
INSERT INTO sla_config (wo_type, priority, response_minutes, resolve_minutes) VALUES
  ('故障维修', '高',   15,  240),
  ('故障维修', '中',   60,  480),
  ('故障维修', '低',   240, 1440),
  ('变更',     '高',   60,  480),
  ('变更',     '中',   240, 1440),
  ('变更',     '低',   480, 2880),
  ('巡检',     '中',   480, 2880),
  ('上架部署', '中',   480, 2880),
  ('下架退库', '中',   480, 2880)
ON CONFLICT (wo_type, priority) DO NOTHING;

-- 数据字典：资产业务/财务状态机（与 V1 注释及前端下拉一致）
INSERT INTO dict (dict_type, dict_key, dict_value, sort) VALUES
  ('asset_status_biz', '规划中',     '规划中',     1),
  ('asset_status_biz', '已采购',     '已采购',     2),
  ('asset_status_biz', '在途',       '在途',       3),
  ('asset_status_biz', '已到货',     '已到货',     4),
  ('asset_status_biz', '待上架',     '待上架',     5),
  ('asset_status_biz', '上架中',     '上架中',     6),
  ('asset_status_biz', '测试压测中', '测试压测中', 7),
  ('asset_status_biz', '待验收',     '待验收',     8),
  ('asset_status_biz', '验收通过',   '验收通过',   9),
  ('asset_status_biz', '在用(计费)', '在用(计费)', 10),
  ('asset_status_biz', '维修中',     '维修中',     11),
  ('asset_status_biz', '停用中',     '停用中',     12),
  ('asset_status_biz', '待退役',     '待退役',     13),
  ('asset_status_biz', '已退役',     '已退役',     14),
  ('asset_status_biz', '已报废',     '已报废',     15),
  ('asset_status_biz', '已退租',     '已退租',     16),
  ('asset_status_fin', '未入账',         '未入账',         1),
  ('asset_status_fin', '已入账',         '已入账',         2),
  ('asset_status_fin', '转固计提',       '转固计提',       3),
  ('asset_status_fin', '减值',           '减值',           4),
  ('asset_status_fin', '折旧完毕',       '折旧完毕',       5),
  ('asset_status_fin', '处置中',         '处置中',         6),
  ('asset_status_fin', '核销',           '核销',           7),
  ('asset_status_fin', '使用权资产确认', '使用权资产确认', 8),
  ('asset_status_fin', '留购',           '留购',           9)
ON CONFLICT (dict_type, dict_key) DO NOTHING;
