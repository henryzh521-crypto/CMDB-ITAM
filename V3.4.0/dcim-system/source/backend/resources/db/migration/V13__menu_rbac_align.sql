-- ============================================================
-- V13__menu_rbac_align.sql — 菜单重组后的 RBAC 对齐
--   背景：侧边栏按业务域重组（仓储归并、RFID 拆分、报表去重）后，
--   部分内置角色缺少对应模块权限，导致「菜单可见但 403」或「功能存在但永不可见」。
--   全部幂等（ON CONFLICT DO NOTHING）。
-- ============================================================

-- ──────────────────── 1. 内置角色 report 读（运营总览 /api/report/dashboard 依赖；菜单恒显示） ────────────────────
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-ASSET',     'report', TRUE, FALSE),
  ('R-WAREHOUSE', 'report', TRUE, FALSE),
  ('R-DELIVERY',  'report', TRUE, FALSE),
  ('R-OPS',       'report', TRUE, FALSE),
  ('R-FIN-VIEW',  'report', TRUE, FALSE),
  ('R-LEADER',    'report', TRUE, FALSE)
ON CONFLICT (role_code, module) DO NOTHING;

-- R-LEADER 补齐 V6 未覆盖的新模块只读（rfid/finance/discovery/gpu；report 已在上表）
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-LEADER', 'rfid',      TRUE, FALSE),
  ('R-LEADER', 'finance',   TRUE, FALSE),
  ('R-LEADER', 'discovery', TRUE, FALSE),
  ('R-LEADER', 'gpu',       TRUE, FALSE)
ON CONFLICT (role_code, module) DO NOTHING;

-- ──────────────────── 2. R-WAREHOUSE：RFID 批次验收（现挂资产管理菜单，仓储执行） ────────────────────
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-WAREHOUSE', 'rfid', TRUE, TRUE)
ON CONFLICT (role_code, module) DO NOTHING;

-- ──────────────────── 3. 财务页统一走 finance 码（depreciation/contracts 为历史码，页面未消费） ────────────────────
-- R-ASSET：折旧底稿/财务台账
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-ASSET', 'finance', TRUE, TRUE)
ON CONFLICT (role_code, module) DO NOTHING;

-- R-FIN-VIEW：财务只读
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-FIN-VIEW', 'finance', TRUE, FALSE)
ON CONFLICT (role_code, module) DO NOTHING;

-- ──────────────────── 4. R-OPS：采集自动化（DataInitializer 可能已补，幂等双保险） ────────────────────
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-OPS', 'discovery', TRUE, TRUE),
  ('R-OPS', 'gpu',       TRUE, TRUE)
ON CONFLICT (role_code, module) DO NOTHING;

-- ──────────────────── 5. R-ASSET：供应商/合同（资产管理专员管合同；页面码为 suppliers） ────────────────────
INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-ASSET', 'suppliers', TRUE, TRUE)
ON CONFLICT (role_code, module) DO NOTHING;
