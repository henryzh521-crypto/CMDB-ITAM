-- ============================================================
-- V22__rack_unit_backfill_from_assets.sql — 机柜 U 位占用回填
--
-- 背景：ETL 导入的 1436 台在架资产只写了 asset.rack_code / unit_no / u_height，
--       从未回填 rack_unit，导致 39260 个 U 位全部显示「空闲」：
--         · GET /racks/{code}/free-units 把已占位当可选（工单可选到实际已占用的 U 位）
--         · RackOccupancyAdapter.occupy() 只校验 rack_unit 行状态，不查 asset 表，
--           校验形同虚设 → 两台资产可被放到同一物理位置（脏数据放大）
--
-- 冲突策略：同一物理 U 位被多台资产认领时（现存 1 处：203-3R02 U13-U21 被
--           两台 9U 服务器 SRV-QY26-2026-01822 / 01866 完全重叠），按 asset_no
--           字典序先到先得；被挤掉的资产不写 occupied_by，其 asset 表位置错误
--           保留给人工核对——本迁移只回填占用台账，不改 asset 位置。
--
-- 保护与幂等：
--   · 仅回填 status='空闲' 的行，不覆盖「预留/故障」等人工标记
--   · 重复执行结果一致（第二次跑 candidates 已为空 → UPDATE 0 行）
--
-- 范围：跳过 rack_code/unit_no 为 NULL 的资产（现存 10 台「有机柜无 unitNo」
--       的半上架状态，无法定位到具体 U 位）
-- ============================================================

WITH candidates AS (
    -- 同一物理位多资产认领 → DISTINCT ON 取 asset_no 最小者（先到先得）
    SELECT DISTINCT ON (ru.rack_code, ru.unit_no)
           ru.rack_code,
           ru.unit_no,
           a.asset_no
      FROM asset a
      JOIN rack_unit ru
        ON ru.rack_code = a.rack_code
       AND ru.unit_no BETWEEN a.unit_no
                          AND a.unit_no + COALESCE(a.u_height, 1) - 1
     WHERE a.rack_code IS NOT NULL
       AND a.unit_no IS NOT NULL
       AND ru.status = '空闲'
     ORDER BY ru.rack_code, ru.unit_no, a.asset_no
)
UPDATE rack_unit ru
   SET status = '占用',
       occupied_by = c.asset_no
  FROM candidates c
 WHERE ru.rack_code = c.rack_code
   AND ru.unit_no = c.unit_no;

DO $$
DECLARE
    occupied int;
    owners   int;
BEGIN
    SELECT count(*) INTO occupied FROM rack_unit WHERE status = '占用';
    SELECT count(DISTINCT occupied_by) INTO owners FROM rack_unit WHERE occupied_by IS NOT NULL;
    RAISE NOTICE '回填完成：占用 U 位 % 行，覆盖资产 % 台', occupied, owners;
END $$;
