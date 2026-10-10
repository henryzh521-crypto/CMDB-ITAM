-- ============================================================
-- V9__asset_parent.sql — 资产父子（整机-部件）层级
--   parent_asset_no：父资产编号（自引用 asset.asset_no）
--   子资产（GPU卡/内存/硬盘/网卡等）挂到父整机下，支持关联联动：
--     - 列表筛选/树形展示
--     - 状态流转可选级联到子资产
--     - 删除父前必须先处理子资产
--   回填：ETL 已把旧「父资产」写入 attrs.legacy_parent_no（旧资产编号），
--   通过 attrs.legacy_no ↔ 新 asset_no 映射回填（仅能匹配到新编号的行）。
-- ============================================================

ALTER TABLE asset ADD COLUMN IF NOT EXISTS parent_asset_no VARCHAR(50);

COMMENT ON COLUMN asset.parent_asset_no IS '父资产编号（整机-部件层级，自引用 asset.asset_no）';

CREATE INDEX IF NOT EXISTS idx_asset_parent ON asset (parent_asset_no);

-- 自引用 FK：父必须存在；不允许环（应用层校验 + 删除前校验子）
-- 已有数据 parent 为空，FK 安全；若父被删需先清子（与应用层一致）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_asset_parent'
    ) THEN
        ALTER TABLE asset
            ADD CONSTRAINT fk_asset_parent
            FOREIGN KEY (parent_asset_no)
            REFERENCES asset (asset_no)
            ON DELETE RESTRICT;
    END IF;
END $$;

-- 回填：attrs.legacy_parent_no（旧编号）→ 通过 child.attrs.legacy_no 找到的新编号
-- 若 legacy_parent_no 本身已是新编号（手工维护过）也直接接受
UPDATE asset c
SET parent_asset_no = COALESCE(
    (SELECT p.asset_no FROM asset p
      WHERE p.attrs->>'legacy_no' = c.attrs->>'legacy_parent_no'
        AND c.attrs->>'legacy_parent_no' IS NOT NULL
        AND c.attrs->>'legacy_parent_no' <> ''
        AND p.asset_no <> c.asset_no
      LIMIT 1),
    CASE
        WHEN c.attrs->>'legacy_parent_no' IS NOT NULL
             AND c.attrs->>'legacy_parent_no' <> ''
             AND EXISTS (SELECT 1 FROM asset p2 WHERE p2.asset_no = c.attrs->>'legacy_parent_no')
             AND c.attrs->>'legacy_parent_no' <> c.asset_no
        THEN c.attrs->>'legacy_parent_no'
        ELSE NULL
    END
)
WHERE c.attrs ? 'legacy_parent_no'
  AND c.parent_asset_no IS NULL
  AND c.attrs->>'legacy_parent_no' IS NOT NULL
  AND c.attrs->>'legacy_parent_no' <> ''
  AND c.attrs->>'legacy_parent_no' <> c.asset_no;

-- 去掉误把子指向自己的行（应用层已挡，库内兜底）
UPDATE asset SET parent_asset_no = NULL
WHERE parent_asset_no IS NOT NULL AND parent_asset_no = asset_no;
