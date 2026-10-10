-- ============================================================
-- v3.4.0
--   1) 删除权限改为显式授予（ADR-02）
--   2) 资产功率视图：分类功率按分类预算一次再关联（ADR-09，10 万资产下约快 30%）
--   3) job_lock 增加执行次数，便于核对多实例下每个定时任务每周期只执行一次（ADR-11）
-- ============================================================

ALTER TABLE job_lock ADD COLUMN IF NOT EXISTS run_count BIGINT NOT NULL DEFAULT 0;

-- 1) v3.3 及以前后端写死「仅管理员可删除」，非管理员角色上勾选的 delete 从未生效。
--    v3.4 起勾选即生效；为避免升级即放大权限，统一复位为不允许，并写一条审计记录列出受影响的角色，供管理员复核后重新授予。
DO $$
DECLARE
  affected text;
BEGIN
  SELECT string_agg(role_code || ':' || module, ', ' ORDER BY role_code, module) INTO affected
  FROM role_action_perm
  WHERE role_code <> 'ADMIN' AND actions ->> 'delete' = 'true';

  IF affected IS NOT NULL THEN
    UPDATE role_action_perm
       SET actions = jsonb_set(actions, '{delete}', 'false'::jsonb)
     WHERE role_code <> 'ADMIN' AND actions ->> 'delete' = 'true';

    INSERT INTO audit_log (actor, action, module, obj_type, obj_id, detail, created_at)
    VALUES ('system-upgrade', 'RESET_DELETE_PERM', 'system', 'role', NULL,
            jsonb_build_object('reason', 'v3.4 删除权限改为显式授予；以下角色的删除勾选此前从未生效，已复位，请按需重新授予',
                               'roles', affected),
            now());
  END IF;
END $$;

-- 2) 功率视图改写：列名、类型、语义与原视图完全一致
CREATE OR REPLACE VIEW v_asset_power AS
WITH cat_power AS (
  SELECT c.category_code,
         (SELECT p.rated_w
            FROM power_profile p
            JOIN category pc2 ON pc2.category_code::text = p.match_value::text
           WHERE p.match_type::text = 'CATEGORY'
             AND c.path::text ~~ (pc2.path::text || '%')
           ORDER BY length(pc2.path::text) DESC
           LIMIT 1) AS rated_w
    FROM category c
)
SELECT a.asset_no,
       a.rack_code,
       a.room_code,
       a.status_biz,
       COALESCE(a.rated_power_w, pm.rated_w, cp.rated_w) AS power_w,
       CASE
         WHEN a.rated_power_w IS NOT NULL THEN 'ASSET'::text
         WHEN pm.rated_w IS NOT NULL THEN 'MODEL'::text
         WHEN cp.rated_w IS NOT NULL THEN 'CATEGORY'::text
         ELSE NULL::text
       END AS power_source
  FROM asset a
  LEFT JOIN power_profile pm ON pm.match_type::text = 'MODEL'::text AND pm.match_value::text = a.model_code::text
  LEFT JOIN cat_power cp ON cp.category_code::text = a.category_code::text
 WHERE a.deleted_at IS NULL;
