-- ============================================================
-- V24: 工单自定义审批人（三形态：按单指定 / 配置层按人 / 转办加签）
-- ============================================================

-- 1) 本单自定义审批链（二维数组，下标=审批级，级内为或签用户名）：
--    [["zhangsan","lisi"],["wangwu"]]; NULL = 按 workflow_config 角色链（现状不变）
ALTER TABLE workorder ADD COLUMN approval_users JSONB;
COMMENT ON COLUMN workorder.approval_users IS
  '本单自定义审批链（按级用户名二维数组；NULL=按 workflow_config 角色链）';

-- 2) 当前级转办/加签委托（对象；level 匹配 approval_level 时生效）：
--    {"level":0,"mode":"transfer","members":["u1"],"by":"x","opinion":"…","at":"…"}
--    transfer=本级可批人替换为 members（角色链失效）；add=在基础链上追加 members（并集或签）
ALTER TABLE workorder ADD COLUMN approval_delegate JSONB;
COMMENT ON COLUMN workorder.approval_delegate IS
  '当前级转办/加签（level=approval_level 时生效；transfer=替换本级可批人，add=追加可批人；{} 或 NULL=无）';

-- 3) 工作流配置：按级指定审批人（与 approval_roles 下标对齐取并集；NULL/[]=未启用）
ALTER TABLE workflow_config ADD COLUMN approval_user_levels JSONB;
COMMENT ON COLUMN workflow_config.approval_user_levels IS
  '按级指定审批人用户名（与 approval_roles 下标对齐取并集；NULL/[]=未启用）';
