-- ============================================================
-- V10__workorder_item.sql — 工单资产行项目（多资产、多位置）
--   1. workorder_item 子表：按行关联资产 + 源/目标位置快照
--   2. 主表 related_assets CSV 保留（兼容），按存量回填子表
--   3. UNIQUE(wo_no, asset_no) 防同单重复关联
-- ============================================================

CREATE TABLE IF NOT EXISTS workorder_item (
    item_no      VARCHAR(60)  PRIMARY KEY,                 -- {woNo}-{seq:04d}
    wo_no        VARCHAR(50)  NOT NULL REFERENCES workorder(wo_no),
    asset_no     VARCHAR(50)  NOT NULL,
    -- 源位置快照（创建时资产台账位置；资产可尚未上架则为空）
    src_room     VARCHAR(50),
    src_rack     VARCHAR(50),
    src_unit     INT,
    src_u_height INT,
    -- 目标位置（仅上架部署/变更按行填写；空则完工回退主表 target_*）
    dst_room     VARCHAR(50),
    dst_rack     VARCHAR(50),
    dst_unit     INT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (wo_no, asset_no)
);
CREATE INDEX IF NOT EXISTS idx_wo_item_wo    ON workorder_item (wo_no);
CREATE INDEX IF NOT EXISTS idx_wo_item_asset ON workorder_item (asset_no);

-- 存量回填：related_assets CSV → 行；src 取资产台账快照；dst 取主表（单资产带回 target_unit，多资产 dst_unit 留空走完工顺延）
INSERT INTO workorder_item (
    item_no, wo_no, asset_no,
    src_room, src_rack, src_unit, src_u_height,
    dst_room, dst_rack, dst_unit
)
SELECT
    w.wo_no || '-' || lpad(ord::text, 4, '0'),
    w.wo_no,
    trim(t.asset_no),
    a.room_code, a.rack_code, a.unit_no, a.u_height,
    r.room_code,
    w.target_rack,
    CASE WHEN w.related_assets IS NOT NULL
              AND position(',' in btrim(w.related_assets)) = 0
         THEN w.target_unit
         ELSE NULL
    END
FROM workorder w
CROSS JOIN LATERAL unnest(string_to_array(w.related_assets, ',')) WITH ORDINALITY AS t(asset_no, ord)
LEFT JOIN asset a ON a.asset_no = trim(t.asset_no)
LEFT JOIN rack  r ON r.rack_code = w.target_rack
WHERE w.related_assets IS NOT NULL
  AND btrim(w.related_assets) <> ''
  AND trim(t.asset_no) <> ''
ON CONFLICT (wo_no, asset_no) DO NOTHING;
