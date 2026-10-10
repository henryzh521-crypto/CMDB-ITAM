package com.dcim.ci.service;

import com.dcim.common.BusinessException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CiGraphService {
    public static final Set<String> CI_TYPES = Set.of("ASSET", "RACK", "ROOM");
    public static final Map<String, String> REL_TYPES;
    private static final int MAX_DEPTH = 6;
    private static final int MAX_NODES = 500;
    private final JdbcTemplate jdbc;
    private final com.dcim.security.DataScopeService dataScope;

    public CiGraphService(JdbcTemplate jdbc, com.dcim.security.DataScopeService dataScope) {
        this.jdbc = jdbc;
        this.dataScope = dataScope;
    }

    public void validateType(String type) {
        if (type == null || !CI_TYPES.contains(type)) {
            throw BusinessException.badRequest("不支持的配置项类型：" + type + "（可选 ASSET / RACK / ROOM）");
        }
    }

    public void validateRel(String rel) {
        if (rel == null || !REL_TYPES.containsKey(rel)) {
            throw BusinessException.badRequest("不支持的关系类型：" + rel + "（可选 " + String.join(" / ", REL_TYPES.keySet()) + "）");
        }
    }

    public CiGraphService.Node requireCi(String type, String id) {
        this.validateType(type);
        CiGraphService.Node n = this.describe(type, id, 0);
        if (n == null) {
            throw BusinessException.notFound("配置项不存在：" + type + " " + id);
        } else {
            return n;
        }
    }

    public CiGraphService.Node describe(String type, String id, int depth) {
        List<Map<String, Object>> rows = switch (type) {
            case "ASSET" -> this.jdbc.queryForList("SELECT name, status_biz AS status FROM asset WHERE asset_no = ? AND deleted_at IS NULL", new Object[]{id});
            case "RACK" -> this.jdbc
            .queryForList("SELECT COALESCE(NULLIF(location_name, ''), rack_code) AS name, NULL AS status FROM rack WHERE rack_code = ?", new Object[]{id});
            case "ROOM" -> this.jdbc.queryForList("SELECT name, NULL AS status FROM machine_room WHERE room_code = ?", new Object[]{id});
            default -> List.of();
        };
        if (rows.isEmpty()) {
            return null;
        } else {
            Map<String, Object> r = rows.get(0);
            return new CiGraphService.Node(
                type, id, r.get("name") == null ? id : String.valueOf(r.get("name")), r.get("status") == null ? null : String.valueOf(r.get("status")), depth
            );
        }
    }

    public CiGraphService.Graph traverse(String type, String id, boolean downstream, int maxDepth) {
        CiGraphService.Node root = this.requireCi(type, id);
        int depthLimit = Math.max(1, Math.min(maxDepth <= 0 ? 6 : maxDepth, 6));
        Map<String, CiGraphService.Node> seen = new LinkedHashMap<>();
        List<CiGraphService.Edge> edges = new ArrayList<>();
        Set<String> edgeKeys = new LinkedHashSet<>();
        Deque<CiGraphService.Node> queue = new ArrayDeque<>();
        seen.put(root.key(), root);
        queue.add(root);
        boolean truncated = false;

        while (!queue.isEmpty()) {
            CiGraphService.Node cur = queue.poll();
            if (cur.depth() < depthLimit) {
                for (CiGraphService.Neighbor nb : this.neighbors(cur.type(), cur.id(), downstream)) {
                    String k = nb.type() + ":" + nb.id();
                    String ek = cur.key() + ">" + k + ">" + nb.rel();
                    if (edgeKeys.add(ek)) {
                        edges.add(new CiGraphService.Edge(cur.key(), k, nb.rel(), nb.label(), nb.implicit()));
                    }

                    if (!seen.containsKey(k)) {
                        if (seen.size() >= 500) {
                            truncated = true;
                            break;
                        }

                        CiGraphService.Node n = this.describe(nb.type(), nb.id(), cur.depth() + 1);
                        if (n == null) {
                            n = new CiGraphService.Node(nb.type(), nb.id(), nb.id() + "（已删除）", null, cur.depth() + 1);
                        }

                        seen.put(k, n);
                        queue.add(n);
                    }
                }
            }
        }

        List<CiGraphService.Node> nodes = new ArrayList<>(seen.values());
        Set<String> keys = seen.keySet();
        edges.removeIf(e -> !keys.contains(e.from()) || !keys.contains(e.to()));
        return new CiGraphService.Graph(root, nodes, edges, truncated);
    }

    /**
     * v3.5：全量拓扑（网络拓扑页）。
     * 机房→机柜→设备隐含层级 + ci_relation 显式关系一次性组装；
     * roomCode 非空时仅返回该机房及其机柜/设备；沿用 MAX_NODES=500 截断防护。
     */
    public CiGraphService.FullGraph fullGraph(String roomCode) {
        String room = roomCode == null ? null : roomCode.trim();
        if (room != null && room.isEmpty()) {
            room = null;
        }

        Map<String, CiGraphService.Node> nodes = new LinkedHashMap<>();
        List<CiGraphService.Edge> edges = new ArrayList<>();
        boolean truncated = false;

        List<Map<String, Object>> rooms = room == null
            ? this.jdbc.queryForList("SELECT room_code, name FROM machine_room ORDER BY room_code")
            : this.jdbc.queryForList("SELECT room_code, name FROM machine_room WHERE room_code = ? ORDER BY room_code", new Object[]{room});
        if (room != null && rooms.isEmpty()) {
            throw BusinessException.notFound("机房不存在：" + room);
        }

        for (Map<String, Object> r : rooms) {
            if (nodes.size() >= MAX_NODES) {
                truncated = true;
                break;
            }

            String code = String.valueOf(r.get("room_code"));
            nodes.put("ROOM:" + code, new CiGraphService.Node("ROOM", code, r.get("name") == null ? code : String.valueOf(r.get("name")), null, 0));
        }

        if (!truncated) {
            List<Map<String, Object>> racks = room == null
                ? this.jdbc.queryForList("SELECT rack_code, room_code, COALESCE(NULLIF(location_name, ''), rack_code) AS name FROM rack ORDER BY rack_code")
                : this.jdbc.queryForList("SELECT rack_code, room_code, COALESCE(NULLIF(location_name, ''), rack_code) AS name FROM rack WHERE room_code = ? ORDER BY rack_code", new Object[]{room});

            for (Map<String, Object> r : racks) {
                if (nodes.size() >= MAX_NODES) {
                    truncated = true;
                    break;
                }

                String code = String.valueOf(r.get("rack_code"));
                nodes.put("RACK:" + code, new CiGraphService.Node("RACK", code, String.valueOf(r.get("name")), null, 0));
                if (r.get("room_code") != null) {
                    edges.add(new CiGraphService.Edge("ROOM:" + r.get("room_code"), "RACK:" + code, "CONTAINS", "机房内机柜", true));
                }
            }
        }

        if (!truncated) {
            int remaining = MAX_NODES - nodes.size();
            // v3.5：资产节点按数据范围过滤（SELF/DEPT），不可见资产不出现在图中，
            // 其 PART_OF / CONTAINS 边及 ci_relation 边因节点缺席而自然不出现，避免间接泄露
            List<Object> scopeArgs = new ArrayList<>();
            String scope = this.dataScope.assetSql(com.dcim.security.CurrentUser.get(), "", scopeArgs);
            String assetSql = room == null
                ? "SELECT asset_no, name, status_biz, rack_code, parent_asset_no FROM asset WHERE deleted_at IS NULL" + scope + " ORDER BY asset_no LIMIT ?"
                : "SELECT asset_no, name, status_biz, rack_code, parent_asset_no FROM asset WHERE deleted_at IS NULL AND rack_code IN (SELECT rack_code FROM rack WHERE room_code = ?)" + scope + " ORDER BY asset_no LIMIT ?";
            List<Object> args = new ArrayList<>();
            if (room != null) {
                args.add(room);
            }
            args.addAll(scopeArgs);
            args.add(remaining + 1);
            List<Map<String, Object>> assets = this.jdbc.queryForList(assetSql, args.toArray());
            if (assets.size() > remaining) {
                truncated = true;
                assets = new ArrayList<>(assets.subList(0, remaining));
            }

            for (Map<String, Object> a : assets) {
                String no = String.valueOf(a.get("asset_no"));
                String key = "ASSET:" + no;
                nodes.put(key, new CiGraphService.Node("ASSET", no, a.get("name") == null ? no : String.valueOf(a.get("name")), a.get("status_biz") == null ? null : String.valueOf(a.get("status_biz")), 0));
                if (a.get("rack_code") != null && a.get("parent_asset_no") == null) {
                    edges.add(new CiGraphService.Edge("RACK:" + a.get("rack_code"), key, "CONTAINS", "机柜内设备", true));
                }
                if (a.get("parent_asset_no") != null) {
                    edges.add(new CiGraphService.Edge(key, "ASSET:" + a.get("parent_asset_no"), "PART_OF", "部件所属宿主", true));
                }
            }
        }

        Set<String> keys = nodes.keySet();
        for (Map<String, Object> r : this.jdbc.queryForList("SELECT src_type, src_id, rel_type, dst_type, dst_id FROM ci_relation WHERE valid_to IS NULL ORDER BY id")) {
            String from = r.get("src_type") + ":" + r.get("src_id");
            String to = r.get("dst_type") + ":" + r.get("dst_id");
            if (keys.contains(from) && keys.contains(to)) {
                String rel = String.valueOf(r.get("rel_type"));
                edges.add(new CiGraphService.Edge(from, to, rel, REL_TYPES.getOrDefault(rel, rel), false));
            }
        }

        edges.removeIf(e -> !keys.contains(e.from()) || !keys.contains(e.to()));
        Map<String, Long> summary = new LinkedHashMap<>();

        for (CiGraphService.Node n : nodes.values()) {
            summary.merge(n.type(), 1L, Long::sum);
        }

        return new CiGraphService.FullGraph(new ArrayList<>(nodes.values()), edges, truncated, summary);
    }

    private List<CiGraphService.Neighbor> neighbors(String type, String id, boolean downstream) {
        List<CiGraphService.Neighbor> out = new ArrayList<>();
        String sql = downstream
            ? "SELECT src_type AS t, src_id AS i, rel_type AS r FROM ci_relation WHERE dst_type = ? AND dst_id = ? AND valid_to IS NULL AND rel_type <> 'CONNECTS_TO'"
            : "SELECT dst_type AS t, dst_id AS i, rel_type AS r FROM ci_relation WHERE src_type = ? AND src_id = ? AND valid_to IS NULL AND rel_type <> 'CONNECTS_TO'";

        for (Map<String, Object> r : this.jdbc.queryForList(sql, new Object[]{type, id})) {
            String rel = String.valueOf(r.get("r"));
            out.add(new CiGraphService.Neighbor(String.valueOf(r.get("t")), String.valueOf(r.get("i")), rel, REL_TYPES.getOrDefault(rel, rel), false));
        }

        for (Map<String, Object> r : this.jdbc
            .queryForList(
                "SELECT dst_type AS t, dst_id AS i FROM ci_relation WHERE src_type = ? AND src_id = ? AND valid_to IS NULL AND rel_type = 'CONNECTS_TO' UNION SELECT src_type, src_id FROM ci_relation WHERE dst_type = ? AND dst_id = ? AND valid_to IS NULL AND rel_type = 'CONNECTS_TO'",
                new Object[]{type, id, type, id}
            )) {
            out.add(new CiGraphService.Neighbor(String.valueOf(r.get("t")), String.valueOf(r.get("i")), "CONNECTS_TO", "网络连接", false));
        }

        if (downstream) {
            switch (type) {
                case "ROOM":
                    this.jdbc
                        .queryForList("SELECT rack_code FROM rack WHERE room_code = ?", String.class, new Object[]{id})
                        .forEach(c -> out.add(new CiGraphService.Neighbor("RACK", c, "CONTAINS", "机房内机柜", true)));
                    break;
                case "RACK":
                    this.jdbc
                        .queryForList(
                            "SELECT asset_no FROM asset WHERE rack_code = ? AND deleted_at IS NULL AND parent_asset_no IS NULL", String.class, new Object[]{id}
                        )
                        .forEach(a -> out.add(new CiGraphService.Neighbor("ASSET", a, "CONTAINS", "机柜内设备", true)));
                    break;
                case "ASSET":
                    this.jdbc
                        .queryForList(
                            "SELECT parent_asset_no FROM asset WHERE asset_no = ? AND parent_asset_no IS NOT NULL AND deleted_at IS NULL",
                            String.class,
                            new Object[]{id}
                        )
                        .forEach(p -> out.add(new CiGraphService.Neighbor("ASSET", p, "PART_OF", "部件所属宿主", true)));
            }
        } else {
            switch (type) {
                case "RACK":
                    this.jdbc
                        .queryForList("SELECT room_code FROM rack WHERE rack_code = ?", String.class, new Object[]{id})
                        .forEach(c -> out.add(new CiGraphService.Neighbor("ROOM", c, "LOCATED_IN", "所在机房", true)));
                    break;
                case "ASSET":
                    this.jdbc
                        .queryForList(
                            "SELECT rack_code FROM asset WHERE asset_no = ? AND rack_code IS NOT NULL AND deleted_at IS NULL", String.class, new Object[]{id}
                        )
                        .forEach(c -> out.add(new CiGraphService.Neighbor("RACK", c, "LOCATED_IN", "所在机柜", true)));
                    this.jdbc
                        .queryForList("SELECT asset_no FROM asset WHERE parent_asset_no = ? AND deleted_at IS NULL", String.class, new Object[]{id})
                        .forEach(c -> out.add(new CiGraphService.Neighbor("ASSET", c, "HAS_PART", "包含部件", true)));
            }
        }

        return out;
    }

    public List<Map<String, Object>> relationsOf(String type, String id) {
        this.validateType(type);
        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "SELECT id, src_type, src_id, rel_type, dst_type, dst_id, remark, created_by, created_at, CASE WHEN src_type = ? AND src_id = ? THEN 'OUT' ELSE 'IN' END AS direction FROM ci_relation WHERE valid_to IS NULL AND ((src_type = ? AND src_id = ?) OR (dst_type = ? AND dst_id = ?)) ORDER BY id DESC",
                new Object[]{type, id, type, id, type, id}
            );
        List<Map<String, Object>> out = new ArrayList<>();

        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("direction", r.get("direction"));
            m.put("srcType", r.get("src_type"));
            m.put("srcId", r.get("src_id"));
            m.put("srcName", this.nameOf(String.valueOf(r.get("src_type")), String.valueOf(r.get("src_id"))));
            m.put("relType", r.get("rel_type"));
            m.put("relLabel", REL_TYPES.getOrDefault(String.valueOf(r.get("rel_type")), String.valueOf(r.get("rel_type"))));
            m.put("dstType", r.get("dst_type"));
            m.put("dstId", r.get("dst_id"));
            m.put("dstName", this.nameOf(String.valueOf(r.get("dst_type")), String.valueOf(r.get("dst_id"))));
            m.put("remark", r.get("remark"));
            m.put("createdBy", r.get("created_by"));
            m.put("createdAt", r.get("created_at"));
            out.add(m);
        }

        return out;
    }

    private String nameOf(String type, String id) {
        CiGraphService.Node n = CI_TYPES.contains(type) ? this.describe(type, id, 0) : null;
        return n == null ? id : n.name();
    }

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("DEPENDS_ON", "依赖");
        m.put("RUNS_ON", "运行于");
        m.put("POWERED_BY", "供电来自");
        m.put("CONNECTS_TO", "网络连接");
        m.put("BACKUP_OF", "备份自");
        REL_TYPES = Collections.unmodifiableMap(m);
    }

    public static record Edge(String from, String to, String rel, String label, boolean implicit) {
    }

    public static record Graph(CiGraphService.Node root, List<CiGraphService.Node> nodes, List<CiGraphService.Edge> edges, boolean truncated) {
    }

    /** v3.5：全量拓扑（无根节点；summary = 各类型节点数） */
    public static record FullGraph(List<CiGraphService.Node> nodes, List<CiGraphService.Edge> edges, boolean truncated, Map<String, Long> summary) {
    }

    private static record Neighbor(String type, String id, String rel, String label, boolean implicit) {
    }

    public static record Node(String type, String id, String name, String status, int depth) {
        public String key() {
            return this.type + ":" + this.id;
        }
    }
}
