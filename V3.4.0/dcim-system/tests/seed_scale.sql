-- 规模化数据：10 机房 / 2500 机柜 / 100000 资产 / 20000 工单
INSERT INTO machine_room(room_code, name) SELECT 'PR'||lpad(g::text,2,'0'), '压测机房'||g FROM generate_series(1,10) g ON CONFLICT DO NOTHING;
INSERT INTO rack(rack_code, room_code, u_capacity) SELECT 'PR'||lpad(((g-1)%10+1)::text,2,'0')||'-R'||lpad(g::text,4,'0'), 'PR'||lpad(((g-1)%10+1)::text,2,'0'), 42 FROM generate_series(1,2500) g ON CONFLICT DO NOTHING;
INSERT INTO asset(asset_no, sn, name, category_code, owner_type, status_biz, status_fin, region_code, room_code, rack_code, unit_no, u_height, mgmt_ip, biz_ip, attrs)
SELECT 'PT-'||lpad(g::text,6,'0'), 'PTSN'||lpad(g::text,8,'0'),
       (ARRAY['GPU服务器 H800','存储节点','计算节点 2288H','交换机 CE6865','GPU服务器 A800'])[1+g%5]||' #'||g,
       (ARRAY['SRV','SRV','SRV','NET','STO'])[1+g%5], 'SELF',
       (ARRAY['在用(计费)','在用(计费)','在用(计费)','维修中','待上架','停用中'])[1+g%6], '未入账',
       (ARRAY['QY','WLCB','SG'])[1+g%3],
       'PR'||lpad((((g-1)/40)%10+1)::text,2,'0'),
       'PR'||lpad((((g-1)/40)%10+1)::text,2,'0')||'-R'||lpad((((g-1)/40)%2500+1)::text,4,'0'),
       ((g-1)%40)+1, 1,
       '10.'||(g/65536)%256||'.'||(g/256)%256||'.'||g%256,
       '172.'||(16+(g/65536)%16)||'.'||(g/256)%256||'.'||g%256,
       jsonb_build_object('monitor', true)
FROM generate_series(1,100000) g ON CONFLICT DO NOTHING;
INSERT INTO workorder(wo_no, wo_type, title, status, priority, applicant, applicant_username, apply_time, related_assets, version, created_at, updated_at, auto_deducted, sla_escalations, approval_level, details, cc, attachments, replacements)
SELECT 'WO-PT-'||lpad(g::text,6,'0'), (ARRAY['故障维修','巡检','部件更换','上架部署'])[1+g%4], '压测工单 '||g,
       (ARRAY['待处理','处理中','已完成','已关闭'])[1+g%4], (ARRAY['高','中','低'])[1+g%3],
       '压测', (ARRAY['admin','tok1','self1','hd1'])[1+g%4], now() - (g||' minutes')::interval,
       'PT-'||lpad((1+g%100000)::text,6,'0'), 1, now(), now(), false, 0, 0, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb, '[]'::jsonb
FROM generate_series(1,20000) g ON CONFLICT DO NOTHING;
ANALYZE;
