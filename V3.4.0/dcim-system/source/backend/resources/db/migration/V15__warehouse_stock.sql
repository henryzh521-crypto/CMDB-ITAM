-- ============================================================
-- V15__warehouse_stock.sql — 仓库管理 + 分仓库存
--
-- 背景：新平台此前没有任何仓库实体，物料「存放地点」仅是 material.location
--       自由文本（无外键/无索引/无校验），无法回答「某仓还有多少」。
--       旧库 dcim 已有 warehouses/shelves/shelfLayers 三级结构与
--       WH/SH/LY 编码规则，但 ETL 迁移时被整体丢弃。
--
-- 设计要点（对齐业界 Snipe-IT/Odoo/NetBox 的教训与本项目既有范式）：
--   1. 三级独立表 warehouse → warehouse_shelf → warehouse_layer，
--      与 machine_room → rack → rack_unit 范式一致，子→父加真 FK。
--   2. Catalog / Stock / Ledger 三层分离：
--        material     = 是什么（不含数量语义的位置）
--        stock_level  = 现在多少、在哪个仓（唯一数量来源）
--        stock_log    = 怎么变成这样的（append-only 账本）
--      —— 物品表上不放 location_id 是 Snipe-IT issue #5881 的核心教训。
--   3. stock_level 粒度 (material_code, warehouse_code)：仓库级。
--      层位是「默认存放位」归属，不是库存维度。
--   4. material.stock 保留为跨仓汇总物化列，与 stock_level 同事务维护，
--      使报表/工单扣料/删除守卫等既有逻辑零改动。
--   5. 并发扣减：UPDATE ... WHERE qty >= n 条件更新 + CHECK(qty>=0) 兜底。
--
-- 完全可重复执行（DDL 用 DROP IF EXISTS + ADD / IF NOT EXISTS，种子与回填为幂等写法）。
-- 存量数据回填到默认仓 WH-001。
-- ============================================================

-- ──────────────────── 1. 仓库主数据 ────────────────────

CREATE TABLE IF NOT EXISTS warehouse (
    id             BIGSERIAL PRIMARY KEY,
    warehouse_code VARCHAR(50)  NOT NULL UNIQUE,           -- WH-001
    name           VARCHAR(200) NOT NULL,
    warehouse_type VARCHAR(50)  NOT NULL DEFAULT '常温仓',  -- dict warehouse_type
    room_code      VARCHAR(50),                            -- 可空，挂到既有机房
    address        VARCHAR(500),
    manager        VARCHAR(100),
    capacity_desc  TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT '正常',    -- 正常/停用
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,    -- 工单扣料默认仓
    remark         TEXT,
    version        INT          NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 全局最多一个默认仓
CREATE UNIQUE INDEX IF NOT EXISTS uq_warehouse_default
    ON warehouse (is_default) WHERE is_default;
CREATE INDEX IF NOT EXISTS idx_warehouse_room ON warehouse (room_code);

COMMENT ON TABLE warehouse IS '仓库（仓储域顶层，物料分仓库存的库存维度之一）';
COMMENT ON COLUMN warehouse.is_default IS '默认仓：工单自动扣料/物料未指定仓库时的兜底目标，全局唯一';

-- ──────────────────── 2. 货架 ────────────────────

CREATE TABLE IF NOT EXISTS warehouse_shelf (
    id             BIGSERIAL PRIMARY KEY,
    shelf_code     VARCHAR(50)  NOT NULL UNIQUE,           -- SH-001
    warehouse_code VARCHAR(50)  NOT NULL REFERENCES warehouse (warehouse_code),
    name           VARCHAR(200),
    shelf_type     VARCHAR(50)  NOT NULL DEFAULT '标准货架',
    layer_count    INT          NOT NULL DEFAULT 4,        -- 创建时自动生成层位 1..N
    status         VARCHAR(20)  NOT NULL DEFAULT '正常',
    remark         TEXT,
    version        INT          NOT NULL DEFAULT 1,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_shelf_layer_count CHECK (layer_count >= 1)
);

CREATE INDEX IF NOT EXISTS idx_shelf_warehouse ON warehouse_shelf (warehouse_code);

COMMENT ON TABLE warehouse_shelf IS '货架（仓库下二级；创建/扩容时自动生成层位行）';

-- ──────────────────── 3. 层位 ────────────────────

CREATE TABLE IF NOT EXISTS warehouse_layer (
    id            BIGSERIAL PRIMARY KEY,
    layer_code    VARCHAR(50)  NOT NULL UNIQUE,            -- LY-001
    shelf_code    VARCHAR(50)  NOT NULL REFERENCES warehouse_shelf (shelf_code),
    layer_no      INT          NOT NULL,
    name          VARCHAR(100),
    capacity_desc TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT '正常',
    remark        TEXT,
    version       INT          NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (shelf_code, layer_no),
    CONSTRAINT ck_layer_no CHECK (layer_no >= 1)
);

CREATE INDEX IF NOT EXISTS idx_layer_shelf ON warehouse_layer (shelf_code);

COMMENT ON TABLE warehouse_layer IS '层位（货架下三级，物料默认存放位的叶子节点）';

-- ──────────────────── 4. 分仓库存 ────────────────────

CREATE TABLE IF NOT EXISTS stock_level (
    id             BIGSERIAL PRIMARY KEY,
    material_code  VARCHAR(50) NOT NULL REFERENCES material (material_code),
    warehouse_code VARCHAR(50) NOT NULL REFERENCES warehouse (warehouse_code),
    qty            INT         NOT NULL DEFAULT 0,
    version        INT         NOT NULL DEFAULT 1,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (material_code, warehouse_code),
    CONSTRAINT ck_stock_level_qty CHECK (qty >= 0)
);

CREATE INDEX IF NOT EXISTS idx_stock_level_wh ON stock_level (warehouse_code);

COMMENT ON TABLE stock_level IS '分仓库存（(物料,仓库) 唯一；数量的唯一权威来源，material.stock 为其跨仓汇总）';
COMMENT ON COLUMN stock_level.qty IS '该仓现存量；扣减用条件 UPDATE ... WHERE qty >= n 保证原子不超扣';

-- ──────────────────── 5. 物料挂默认货位 ────────────────────

ALTER TABLE material ADD COLUMN IF NOT EXISTS warehouse_code VARCHAR(50);
ALTER TABLE material ADD COLUMN IF NOT EXISTS shelf_code     VARCHAR(50);
ALTER TABLE material ADD COLUMN IF NOT EXISTS layer_code     VARCHAR(50);
ALTER TABLE material ADD COLUMN IF NOT EXISTS remark         TEXT;

COMMENT ON COLUMN material.warehouse_code IS '默认仓库（工单扣料优先从此仓扣；与 stock_level 联动）';
COMMENT ON COLUMN material.shelf_code IS '默认货架（货位归属，不参与库存数量维度）';
COMMENT ON COLUMN material.layer_code IS '默认层位（货位归属，不参与库存数量维度）';
COMMENT ON COLUMN material.location IS '【历史遗留】旧系统自由文本存放位置，仅只读回退展示，新写入不再产生';
COMMENT ON COLUMN material.remark IS '备注（此前前端可提交但后端缺列，导致静默丢失）';

ALTER TABLE material DROP CONSTRAINT IF EXISTS fk_material_warehouse;
ALTER TABLE material
    ADD CONSTRAINT fk_material_warehouse FOREIGN KEY (warehouse_code)
        REFERENCES warehouse (warehouse_code) ON DELETE RESTRICT;

ALTER TABLE material DROP CONSTRAINT IF EXISTS fk_material_shelf;
ALTER TABLE material
    ADD CONSTRAINT fk_material_shelf FOREIGN KEY (shelf_code)
        REFERENCES warehouse_shelf (shelf_code) ON DELETE RESTRICT;

ALTER TABLE material DROP CONSTRAINT IF EXISTS fk_material_layer;
ALTER TABLE material
    ADD CONSTRAINT fk_material_layer FOREIGN KEY (layer_code)
        REFERENCES warehouse_layer (layer_code) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_material_warehouse ON material (warehouse_code);

-- ──────────────────── 6. 出入库流水带仓库 ────────────────────

-- stock_log 为分区表：仅加可空列，避免全分区写默认值
ALTER TABLE stock_log ADD COLUMN IF NOT EXISTS warehouse_code      VARCHAR(50);
ALTER TABLE stock_log ADD COLUMN IF NOT EXISTS from_warehouse_code VARCHAR(50);

COMMENT ON COLUMN stock_log.warehouse_code IS '本条流水的发生仓：IN=入库仓 / OUT=出库仓 / ADJUST=调整仓 / TRANSFER=目标仓';
COMMENT ON COLUMN stock_log.from_warehouse_code IS '仅 TRANSFER 的源仓，其余类型为 NULL';

-- ──────────────────── 7. 盘点支持物料 ────────────────────

ALTER TABLE inventory_task ADD COLUMN IF NOT EXISTS
    target_type VARCHAR(10) NOT NULL DEFAULT 'ASSET';
COMMENT ON COLUMN inventory_task.target_type IS '盘点对象：ASSET 资产 / MATERIAL 物料';

-- 物料盘点的粒度是 (物料, 仓库)：同一物料可出现在多个仓库的盘点明细中，
-- 故 asset_no 之外补 material_code + warehouse_code，并用 COALESCE 统一成二/三选一的唯一键
ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS material_code VARCHAR(50);
ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS warehouse_code VARCHAR(50);
ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS counted_qty   INT;
COMMENT ON COLUMN inventory_item.material_code IS '盘点对象为物料时的物料编码（与 asset_no 二选一）';
COMMENT ON COLUMN inventory_item.warehouse_code IS '物料盘点所在仓库（资产盘点为 NULL）';
COMMENT ON COLUMN inventory_item.counted_qty IS '实盘数量（物料盘点）；资产盘点为 NULL';

ALTER TABLE inventory_item DROP CONSTRAINT IF EXISTS inventory_item_task_no_asset_no_key;
ALTER TABLE inventory_item ALTER COLUMN asset_no DROP NOT NULL;
ALTER TABLE inventory_item DROP CONSTRAINT IF EXISTS ck_inv_item_subject;
ALTER TABLE inventory_item
    ADD CONSTRAINT ck_inv_item_subject CHECK (asset_no IS NOT NULL OR material_code IS NOT NULL);

DROP INDEX IF EXISTS uq_inv_item_subject;
CREATE UNIQUE INDEX uq_inv_item_subject
    ON inventory_item (task_no, COALESCE(asset_no, material_code), COALESCE(warehouse_code, ''));

-- ──────────────────── 8. 字典与编码规则 ────────────────────

INSERT INTO dict (dict_type, dict_key, dict_value, sort) VALUES
  ('warehouse_type', '常温仓',   '常温仓',   1),
  ('warehouse_type', '恒温仓',   '恒温仓',   2),
  ('warehouse_type', '冷藏仓',   '冷藏仓',   3),
  ('warehouse_type', '危化品仓', '危化品仓', 4),
  ('warehouse_type', '备件仓',   '备件仓',   5),
  ('warehouse_type', '线缆仓',   '线缆仓',   6),
  ('warehouse_type', '临时仓',   '临时仓',   7)
ON CONFLICT (dict_type, dict_key) DO NOTHING;

-- 对齐旧库 settings.data->'codeRules' 预留的前缀/位宽（不含日期）
INSERT INTO dict (dict_type, dict_key, dict_value, sort) VALUES
  ('code_rule', 'warehouses',  '前缀 WH，位宽 3 → WH-001',  1),
  ('code_rule', 'shelves',     '前缀 SH，位宽 3 → SH-001',  2),
  ('code_rule', 'shelfLayers', '前缀 LY，位宽 3 → LY-001',  3)
ON CONFLICT (dict_type, dict_key) DO NOTHING;

-- ──────────────────── 9. 默认仓 + 存量回填 ────────────────────

INSERT INTO warehouse (warehouse_code, name, warehouse_type, status, is_default, remark)
VALUES ('WH-001', '综合仓库', '常温仓', '正常', TRUE, '系统默认仓：历史物料与未指定仓库的领用兜底')
ON CONFLICT (warehouse_code) DO NOTHING;

-- WH-001 已占用（发号器 next_no 语义为「已发出的最后一个号」），置 1 使首个自动生成落到 WH-002
INSERT INTO asset_no_seq (prefix, next_no) VALUES ('WH', 1)
ON CONFLICT (prefix) DO UPDATE SET next_no = GREATEST(asset_no_seq.next_no, EXCLUDED.next_no);

-- 存量物料挂到默认仓（未指定者）
UPDATE material SET warehouse_code = 'WH-001', updated_at = now()
WHERE warehouse_code IS NULL;

-- 存量库存回填分仓账（仅 stock>0 建行；负库存为历史脏数据不入账，保留 material.stock 原值）
INSERT INTO stock_level (material_code, warehouse_code, qty)
SELECT m.material_code, 'WH-001', GREATEST(m.stock, 0)
FROM material m
WHERE m.stock > 0
ON CONFLICT (material_code, warehouse_code) DO NOTHING;

-- ──────────────────── 10. 权限 ────────────────────

INSERT INTO role_module_perm (role_code, module, can_read, can_write) VALUES
  ('R-WAREHOUSE', 'warehouses', TRUE, TRUE),   -- 仓库管理员：建仓/货架/层位
  ('R-ASSET',     'warehouses', TRUE, FALSE),  -- 资产管理专员：只读（跨域查询货位）
  ('R-LEADER',    'warehouses', TRUE, FALSE)   -- 部门负责人：全模块只读
ON CONFLICT (role_code, module) DO NOTHING;
