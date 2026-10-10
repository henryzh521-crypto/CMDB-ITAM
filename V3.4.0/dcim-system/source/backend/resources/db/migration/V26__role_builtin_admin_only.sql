-- ============================================================
-- V26__role_builtin_admin_only.sql — 仅「系统管理员」保持内置
-- 业务角色随实际业务增减，全部放开为普通角色（可改名、可删）：
--   - 删除仍受约束：内置 403（仅 ADMIN）、已分配用户 409、被工单审批链引用 409
-- R-ADMIN（财务管理员）原由 DataInitializer 每次启动时以 built_in=TRUE 补建，
--   迁到本迁移幂等补种（非内置）：业务删除后不会被启动逻辑复活。
-- 非内置角色的默认模块权限同迁入本迁移（ON CONFLICT DO NOTHING：
--   已有行不覆盖，业务自行改过的权限保留），DataInitializer 不再写这些角色。
-- ============================================================

-- 1) 除 ADMIN 外全部放开（含既有库历史种子的 LEADER / R-* / R-ADMIN）
UPDATE roles SET built_in = FALSE WHERE role_code <> 'ADMIN';

-- 2) 财务管理员角色行（V2/V6 未种，此处幂等补种；必须先于下方权限行插入）
INSERT INTO roles (role_code, name, description, built_in)
VALUES ('R-ADMIN', '财务管理员', '财务档案/折旧计提读写（PRD R-ADMIN）', FALSE)
ON CONFLICT (role_code) DO NOTHING;

-- 3) 非内置角色的默认模块权限（幂等；已有行=迁移或业务已配过，一律不覆盖）
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-FIN-VIEW', 'finance',   TRUE,  FALSE),  -- 财务只读
  ('R-ADMIN',    'finance',   TRUE,  TRUE),   -- 财务读写
  ('R-OPS',      'discovery', TRUE,  TRUE),   -- 采集发现任务（W2）
  ('R-OPS',      'gpu',       TRUE,  TRUE),   -- GPU 卡（W2）
  ('R-ADMIN',    'discovery', TRUE,  TRUE),
  ('R-ADMIN',    'gpu',       TRUE,  TRUE)
ON CONFLICT (role_code, module) DO NOTHING;
