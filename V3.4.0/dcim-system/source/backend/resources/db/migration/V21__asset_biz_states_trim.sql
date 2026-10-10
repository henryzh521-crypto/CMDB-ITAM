-- V21: 资产业务状态机精简 16 → 11 态
--
-- 背景：16 态链路里的采购/测试过程态（已采购/在途/已到货/测试压测中/待验收）在实际运维中
-- 从未被使用（线上 1446 台资产只落在「规划中」「在用(计费)」两态），状态机流转弹窗里
-- 却暴露着 16 个候选目标，操作者需要在中间态里挑，徒增困惑。
--
-- 本迁移：权威矩阵以 AssetStateMachine.java 为准（V1/V2 为历史 16 态记录，Flyway
-- checksum 不可改），此处只清理字典并兜底存量数据：
--   1) 存量兜底：仍停留在这 5 态的资产按语义归并（当前线上为 0 条，防御性写法）
--   2) 删除 5 条字典项（前端状态下拉 / 导入校验数据源）
--   3) sort 重排，消除删除后的序号空洞
--
-- 精简后链路（11 态）：
--   规划中 → [待上架, 已退租]
--   待上架 → [上架中, 已退租]
--   上架中 → [验收通过, 维修中, 待上架]
--   验收通过 → [在用(计费)] → 维修中/停用中/待退役 → 已退役 → 已报废/已退租

-- ────────────────────── 1) 存量兜底 ──────────────────────
-- 已采购 / 在途：尚未到货，退回「规划中」
UPDATE asset SET status_biz = '规划中'
 WHERE status_biz IN ('已采购', '在途');

-- 已到货：与「待上架」同义（RFID 确认入库本就直接落待上架）
UPDATE asset SET status_biz = '待上架'
 WHERE status_biz = '已到货';

-- 测试压测中 / 待验收：归并到链路上游的「上架中」
UPDATE asset SET status_biz = '上架中'
 WHERE status_biz IN ('测试压测中', '待验收');

-- ────────────────────── 2) 删除字典项 ──────────────────────
DELETE FROM dict
 WHERE dict_type = 'asset_status_biz'
   AND dict_key IN ('已采购', '在途', '已到货', '测试压测中', '待验收');

-- ────────────────────── 3) sort 重排 ──────────────────────
-- 按 AssetStateMachine.bizStates() 的链路顺序重排，保证前端下拉与状态机矩阵同序
UPDATE dict d
   SET sort = u.rn
  FROM (SELECT dict_key,
               row_number() OVER (ORDER BY CASE dict_key
                   WHEN '规划中'     THEN 1
                   WHEN '待上架'     THEN 2
                   WHEN '上架中'     THEN 3
                   WHEN '验收通过'   THEN 4
                   WHEN '在用(计费)' THEN 5
                   WHEN '维修中'     THEN 6
                   WHEN '停用中'     THEN 7
                   WHEN '待退役'     THEN 8
                   WHEN '已退役'     THEN 9
                   WHEN '已报废'     THEN 10
                   WHEN '已退租'     THEN 11
                   ELSE 99 END) AS rn
          FROM dict
         WHERE dict_type = 'asset_status_biz') u
 WHERE d.dict_type = 'asset_status_biz'
   AND d.dict_key = u.dict_key
   AND d.sort IS DISTINCT FROM u.rn;
