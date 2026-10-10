-- ============================================================
-- V16__warehouse_read_dep.sql — 仓库模块读权限补齐
--
-- 背景：V15 只给 R-WAREHOUSE / R-ASSET / R-LEADER 三个角色授了 warehouses，
--       但下列四个页面在打开时都会请求 GET /api/warehouses/options
--       （@RequirePerm(module = "warehouses")）：
--         · 物料管理   （materials）    — 三级货位级联的仓库下拉
--         · 出入库     （stockLogs）    — 发生仓/转出仓下拉
--         · 盘点       （inventories）  — 物料盘点的仓库筛选
--       · 报表中心     （report）      — 仓储报表的仓库维度筛选
--       读权限缺失时下拉会静默拿到空列表：编辑历史货位会因缺项丢数据、
--       转仓/分仓筛选不可用。故凡能读上述任一模块的角色，都补齐 warehouses 只读。
--
-- 边界：
--   * 只补「缺失的行」，不覆盖已显式设为 can_read=false 的拒绝配置；
--   * 只给读、不给写——仓库/货架/层位主数据仍由仓库管理员维护，
--     其他角色仅用于下拉与筛选回显；
--   * 幂等：NOT EXISTS + ON CONFLICT (role_code, module) DO NOTHING。
-- ============================================================

INSERT INTO role_module_perm (role_code, module, can_read, can_write)
SELECT DISTINCT r.role_code, 'warehouses', TRUE, FALSE
FROM role_module_perm r
WHERE r.can_read
  AND r.module IN ('materials', 'stockLogs', 'inventories', 'report')
  AND NOT EXISTS (
        SELECT 1
        FROM role_module_perm x
        WHERE x.role_code = r.role_code
          AND x.module = 'warehouses')
ON CONFLICT (role_code, module) DO NOTHING;
