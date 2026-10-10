-- ============================================================
-- V4__room_p2.sql — P2 机房 U 位域种子数据（全部幂等，ON CONFLICT DO NOTHING）
--   1. 机房 1 个：QY01 庆阳一期1号机房
--   2. 机柜 2 个：QY01-R01 / QY01-R02（42U）
--   3. 每机柜 generate_series 自动生成 U 位行 1..u_capacity（status 默认 空闲）
-- ============================================================

INSERT INTO machine_room (room_code, name, data_center, city, cabinet_count) VALUES
  ('QY01', '庆阳一期1号机房', '庆阳一期', '庆阳', 2)
ON CONFLICT (room_code) DO NOTHING;

INSERT INTO rack (rack_code, room_code, location_name, u_capacity) VALUES
  ('QY01-R01', 'QY01', 'A01 排 01 列', 42),
  ('QY01-R02', 'QY01', 'A01 排 02 列', 42)
ON CONFLICT (rack_code) DO NOTHING;

-- U 位行 1..u_capacity（机柜创建/扩容同款补行语句，已有行跳过）
INSERT INTO rack_unit (rack_code, unit_no)
SELECT r.rack_code, g
FROM rack r
CROSS JOIN generate_series(1, r.u_capacity) AS g
ON CONFLICT (rack_code, unit_no) DO NOTHING;
