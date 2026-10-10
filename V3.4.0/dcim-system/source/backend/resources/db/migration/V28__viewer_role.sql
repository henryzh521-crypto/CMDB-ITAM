-- ============================================================
-- V28__viewer_role.sql — 幂等补种「只读用户」VIEWER 角色
--
-- 背景：VIEWER 原先只由手工脚本 sql/closeout-observation-20260926.sql 建出，
--       没进 Flyway 迁移历史 —— 全新安装（以及 IT 用的内嵌库）根本没有这个角色，
--       于是「用 VIEWER 建用户」会撞 user_role.role_code 外键，接口 500。
--       只读账号 healthcheck 同样依赖 VIEWER。
--
-- 说明：
--   * built_in=FALSE：沿用 V26「仅 ADMIN 保持内置」的口径（历史手工脚本建成了 TRUE，
--     未在本迁移中回改，避免动既有库数据；如需对齐可单独执行 UPDATE）。
--   * 权限行 ON CONFLICT DO NOTHING：沿用 V26「已有行=业务已配过，不覆盖」。
-- ============================================================

INSERT INTO roles (role_code, name, description, built_in)
VALUES ('VIEWER', '只读用户', '全模块只读，用于替代历史「测试」角色', FALSE)
ON CONFLICT (role_code) DO NOTHING;

INSERT INTO role_module_perm (role_code, module, can_read, can_write)
SELECT 'VIEWER', m, TRUE, FALSE
FROM unnest(ARRAY[
    'assets','categories','codeRules','contracts','departments','depreciation','discovery',
    'finance','gpu','inventories','knowledge','machineRooms','materialCats','materials',
    'people','racks','report','rfid','stockLogs','suppliers','system','units','warehouses','workorders'
]) AS m
ON CONFLICT (role_code, module) DO NOTHING;
