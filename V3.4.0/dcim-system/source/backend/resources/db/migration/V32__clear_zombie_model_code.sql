-- 清理型号库下线后残留的占位型号值。
-- GPU001(1472 台) / B300(200 台) 是 2026-09-26~27 批量导入时，为通过旧「型号存在性校验」
-- 而在 device_model 中临时创建的假型号；库内不存在可回填的真实型号信息
-- （asset.attrs 全为空对象、gpu_card / material 无数据、资产名称仅为类别名）。
-- model_code 现为资产自由文本，登记时按实际型号填写。
UPDATE asset
   SET model_code = NULL,
       updated_at = now()
 WHERE model_code IN ('GPU001', 'B300');
