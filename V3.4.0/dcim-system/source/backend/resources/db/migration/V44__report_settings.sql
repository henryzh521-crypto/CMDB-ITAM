-- V44 —— v3.3.0 报表参数
-- 电价（元/kWh）：配置后运营总览的「运维成本结构」「TCO」按机柜额定/实测功率估算电费；留空则不计电费。
INSERT INTO capacity_setting (setting_key, setting_value, remark)
VALUES ('electricity_price', '', '电价（元/kWh），用于成本结构与 TCO 中的电费估算；留空不计')
ON CONFLICT (setting_key) DO NOTHING;
