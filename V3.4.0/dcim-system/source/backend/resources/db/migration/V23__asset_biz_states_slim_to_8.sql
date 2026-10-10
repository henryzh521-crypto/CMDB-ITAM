-- V23: 资产业务状态机精简 11 → 8 态（删除 规划中 / 上架中 / 验收通过）
--
-- 背景（业务侧反馈，2026-09-27）：
--   1) 上架部署工单完工后资产只推进到「上架中」，还要手工流转 验收通过 → 在用，
--      多点两次且与实际流程不符——运维提上架单、机房加电、完工即在用运行；
--   2) 规划中/上架中/验收通过 三态现实中从不单独操作：设备买回来即「放着准备上架」
--      （待上架），上架完工即在用，状态太多徒增操作负担。
--
-- 本迁移：权威矩阵以 AssetStateMachine.java 为准（V1/V2/V21 为历史 Flyway
-- checksum 不可改），此处只兜底存量数据并清理字典：
--   1) 存量兜底：规划中 → 待上架（买回来放着准备上架）；上架中/验收通过 → 在用(计费)
--   2) 删除 3 条字典项（前端状态下拉 / 导入校验数据源）
--   3) sort 重排，消除删除后的序号空洞
--
-- 精简后链路（8 态）：
--   待上架 → [在用(计费), 已退租]        （上架部署工单完工直接进入在用）
--   在用(计费) → 维修中/停用中/待退役 → 已退役 → 已报废/已退租

-- ────────────────────── 1) 存量兜底 ──────────────────────
-- 规划中：采购即建资产，语义并入「待上架」
UPDATE asset SET status_biz = '待上架'
 WHERE status_biz = '规划中';

-- 上架中 / 验收通过：上架完工即在用（若历史上停在中间态，视为已上架完成）
UPDATE asset SET status_biz = '在用(计费)'
 WHERE status_biz IN ('上架中', '验收通过');

-- ────────────────────── 2) 删除字典项 ──────────────────────
DELETE FROM dict
 WHERE dict_type = 'asset_status_biz'
   AND dict_key IN ('规划中', '上架中', '验收通过');

-- ────────────────────── 3) sort 重排 ──────────────────────
-- 按 AssetStateMachine.bizStates() 的链路顺序重排，保证前端下拉与状态机矩阵同序
UPDATE dict d
   SET sort = u.rn
  FROM (SELECT dict_key,
               row_number() OVER (ORDER BY CASE dict_key
                   WHEN '待上架'     THEN 1
                   WHEN '在用(计费)' THEN 2
                   WHEN '维修中'     THEN 3
                   WHEN '停用中'     THEN 4
                   WHEN '待退役'     THEN 5
                   WHEN '已退役'     THEN 6
                   WHEN '已报废'     THEN 7
                   WHEN '已退租'     THEN 8
                   ELSE 99 END) AS rn
          FROM dict
         WHERE dict_type = 'asset_status_biz') u
 WHERE d.dict_type = 'asset_status_biz'
   AND d.dict_key = u.dict_key
   AND d.sort IS DISTINCT FROM u.rn;
