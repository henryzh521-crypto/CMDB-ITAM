package com.dcim.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dcim.asset.entity.AssetEntity;
import com.dcim.workorder.entity.WorkorderEntity;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 数据范围（行级权限）——v3.3 新增的真正执行层。
 *
 * <p>v3.2 角色管理里可以配置 ALL / DEPT / SELF，配置也会加载进 {@link LoginUser#dataScopes()}，
 * 但没有任何查询使用它（测试报告误判为“通过”）。现在对两个核心模块生效：
 * <ul>
 *   <li><b>workorders</b>：SELF = 我申请的 / 我处理的 / 抄送我的 / 当前级审批人是我；
 *       DEPT = 本部门同事申请的 + SELF；</li>
 *   <li><b>assets</b>：SELF = 责任人是我（用户名或姓名）；DEPT = 使用部门是我的部门 + SELF。</li>
 * </ul>
 * 多角色取最宽范围（ALL &gt; DEPT &gt; SELF）；未配置视为 ALL（与 v3.2 行为一致）；管理员恒为 ALL。
 * 不可见的单据按“不存在”处理（404），避免通过编号枚举探测。
 */
@Service
public class DataScopeService {
    public enum Scope {
        SELF, DEPT, ALL
    }

    public static final String WORKORDERS = "workorders";
    public static final String ASSETS = "assets";
    private final JdbcTemplate jdbc;

    public DataScopeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Scope scopeOf(LoginUser u, String module) {
        if (u == null || u.isAdmin()) {
            return Scope.ALL;
        }
        Map<String, Object> cfg = u.dataScopes() == null ? null : u.dataScopes().get(module);
        return parse(cfg == null ? null : cfg.get("type"));
    }

    static Scope parse(Object v) {
        if (v == null) {
            return Scope.ALL;
        }
        try {
            return Scope.valueOf(String.valueOf(v).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // 非法配置值按最窄范围处理（fail-closed），避免脏数据放大为全量可见；null（未配置）仍视为 ALL
            org.slf4j.LoggerFactory.getLogger(DataScopeService.class)
                .warn("[datascope] 非法数据范围配置值：{}，按 SELF 处理", v);
            return Scope.SELF;
        }
    }

    /** 多角色合并：取更宽的范围 */
    public static Map<String, Object> widest(Map<String, Object> a, Map<String, Object> b) {
        Scope sa = parse(a == null ? null : a.get("type"));
        Scope sb = parse(b == null ? null : b.get("type"));
        return sa.ordinal() >= sb.ordinal() ? a : b;
    }

    private static boolean hasDept(LoginUser u) {
        return u.department() != null && !u.department().isBlank();
    }

    // ───────────────────────── 工单 ─────────────────────────

    public void applyWorkorderScope(LambdaQueryWrapper<WorkorderEntity> qw, LoginUser u) {
        Scope s = this.scopeOf(u, WORKORDERS);
        if (s == Scope.ALL) {
            return;
        }
        String me = u.username();
        boolean dept = s == Scope.DEPT && hasDept(u);
        qw.and(w -> {
            w.eq(WorkorderEntity::getApplicantUsername, me)
                .or().eq(WorkorderEntity::getHandler, me)
                .or().apply("jsonb_exists(COALESCE(cc, '[]'::jsonb), {0})", me)
                .or().apply("(approval_users IS NOT NULL AND jsonb_typeof(approval_users) = 'array' AND jsonb_exists(approval_users -> approval_level, {0}))", me);
            if (dept) {
                w.or().apply(
                    "applicant_username IN (SELECT username FROM users WHERE deleted_at IS NULL AND department = {0})", u.department()
                );
            }
        });
    }

    public boolean canSeeWorkorder(LoginUser u, WorkorderEntity wo) {
        Scope s = this.scopeOf(u, WORKORDERS);
        if (s == Scope.ALL || wo == null) {
            return true;
        }
        String me = u.username();
        if (me.equals(wo.getApplicantUsername()) || me.equals(wo.getHandler())) {
            return true;
        }
        if (wo.getCc() != null && wo.getCc().stream().anyMatch(x -> me.equals(String.valueOf(x)))) {
            return true;
        }
        List<Object> chain = wo.getApprovalUsers();
        int lv = wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel();
        if (chain != null && lv >= 0 && lv < chain.size() && chain.get(lv) instanceof List<?> l && l.stream().anyMatch(x -> me.equals(String.valueOf(x)))) {
            return true;
        }
        if (s == Scope.DEPT && hasDept(u) && wo.getApplicantUsername() != null) {
            String d = this.departmentOf(wo.getApplicantUsername());
            return Objects.equals(d, u.department());
        }
        return false;
    }

    // ───────────────────────── 资产 ─────────────────────────

    public void applyAssetScope(LambdaQueryWrapper<AssetEntity> qw, LoginUser u) {
        Scope s = this.scopeOf(u, ASSETS);
        if (s == Scope.ALL) {
            return;
        }
        boolean dept = s == Scope.DEPT && hasDept(u);
        qw.and(w -> {
            w.eq(AssetEntity::getOwner, u.username());
            if (u.name() != null && !u.name().isBlank()) {
                w.or().eq(AssetEntity::getOwner, u.name());
            }
            if (dept) {
                w.or().eq(AssetEntity::getDepartment, u.department());
            }
        });
    }

    public boolean canSeeAsset(LoginUser u, AssetEntity a) {
        Scope s = this.scopeOf(u, ASSETS);
        if (s == Scope.ALL || a == null) {
            return true;
        }
        if (a.getOwner() != null && (a.getOwner().equals(u.username()) || a.getOwner().equals(u.name()))) {
            return true;
        }
        return s == Scope.DEPT && hasDept(u) && Objects.equals(a.getDepartment(), u.department());
    }

    /**
     * 原生 SQL 场景（全局搜索、报表）：返回 " AND (...)" 片段，参数追加到 args。
     * @param alias 表别名，空串表示不加前缀
     */
    public String assetSql(LoginUser u, String alias, List<Object> args) {
        Scope s = this.scopeOf(u, ASSETS);
        if (s == Scope.ALL) {
            return "";
        }
        String p = alias == null || alias.isEmpty() ? "" : alias + ".";
        StringBuilder sb = new StringBuilder(" AND (").append(p).append("owner = ? OR ").append(p).append("owner = ?");
        args.add(u.username());
        args.add(u.name() == null ? u.username() : u.name());
        if (s == Scope.DEPT && hasDept(u)) {
            sb.append(" OR ").append(p).append("department = ?");
            args.add(u.department());
        }
        return sb.append(")").toString();
    }

    public String workorderSql(LoginUser u, String alias, List<Object> args) {
        Scope s = this.scopeOf(u, WORKORDERS);
        if (s == Scope.ALL) {
            return "";
        }
        String p = alias == null || alias.isEmpty() ? "" : alias + ".";
        StringBuilder sb = new StringBuilder(" AND (")
            .append(p).append("applicant_username = ? OR ")
            .append(p).append("handler = ? OR jsonb_exists(COALESCE(").append(p).append("cc, '[]'::jsonb), ?)");
        args.add(u.username());
        args.add(u.username());
        args.add(u.username());
        if (s == Scope.DEPT && hasDept(u)) {
            sb.append(" OR ").append(p).append("applicant_username IN (SELECT username FROM users WHERE deleted_at IS NULL AND department = ?)");
            args.add(u.department());
        }
        return sb.append(")").toString();
    }

    private String departmentOf(String username) {
        List<String> rows = this.jdbc.queryForList(
            "SELECT department FROM users WHERE username = ? AND deleted_at IS NULL", String.class, username
        );
        return rows.isEmpty() ? null : rows.get(0);
    }
}
