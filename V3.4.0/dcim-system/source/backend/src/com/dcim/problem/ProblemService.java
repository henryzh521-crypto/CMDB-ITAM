package com.dcim.problem;

import com.dcim.common.BusinessException;
import com.dcim.common.JdbcRows;
import com.dcim.common.event.DomainEvent;
import com.dcim.common.event.DomainEventPublisher;
import com.dcim.common.notify.NotificationPort;
import com.dcim.common.seq.SeqNoGenerator;
import com.dcim.knowledge.KbService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemService {
    public static final Map<String, String> STATUS_LABEL;
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "NEW",
        Set.of("ANALYZING", "KNOWN_ERROR", "RESOLVED", "CLOSED"),
        "ANALYZING",
        Set.of("KNOWN_ERROR", "RESOLVED", "CLOSED"),
        "KNOWN_ERROR",
        Set.of("ANALYZING", "RESOLVED", "CLOSED"),
        "RESOLVED",
        Set.of("ANALYZING", "CLOSED"),
        "CLOSED",
        Set.of()
    );
    private static final String COLS = "p.id, p.prb_no AS \"prbNo\", p.title, p.description, p.status, p.priority, p.owner, p.asset_nos AS \"assetNos\",\np.model_code AS \"modelCode\", p.alertname, p.root_cause AS \"rootCause\", p.workaround, p.resolution,\np.kb_no AS \"kbNo\", p.source, p.created_by AS \"createdBy\", p.created_at AS \"createdAt\",\np.updated_at AS \"updatedAt\", p.resolved_at AS \"resolvedAt\", p.closed_at AS \"closedAt\"\n";
    private final JdbcTemplate jdbc;
    private final SeqNoGenerator seq;
    private final KbService kb;
    private final DomainEventPublisher events;
    private final NotificationPort notify;

    public ProblemService(JdbcTemplate jdbc, SeqNoGenerator seq, KbService kb, DomainEventPublisher events, NotificationPort notify) {
        this.jdbc = jdbc;
        this.seq = seq;
        this.kb = kb;
        this.events = events;
        this.notify = notify;
    }

    public Map<String, Object> list(String status, String keyword, String owner, String assetNo, long page, long size) {
        StringBuilder w = new StringBuilder(" WHERE p.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if ("OPEN".equals(status)) {
            w.append(" AND p.status IN ('NEW', 'ANALYZING', 'KNOWN_ERROR')");
        } else if (status != null && !status.isBlank()) {
            w.append(" AND p.status = ?");
            args.add(status.trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (p.title ILIKE ? OR p.prb_no ILIKE ? OR p.root_cause ILIKE ? OR p.alertname ILIKE ? OR p.model_code ILIKE ?)");

            for (int i = 0; i < 5; i++) {
                args.add(like);
            }
        }

        if (owner != null && !owner.isBlank()) {
            w.append(" AND p.owner = ?");
            args.add(owner.trim());
        }

        if (assetNo != null && !assetNo.isBlank()) {
            w.append(" AND p.asset_nos @> ARRAY[?]::text[]");
            args.add(assetNo.trim());
        }

        Long total = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM problem p" + w, Long.class, args.toArray());
        long sz = Math.max(1L, Math.min(size, 100L));
        args.add(sz);
        args.add((Math.max(page, 1L) - 1L) * sz);
        List<Map<String, Object>> rows = JdbcRows.list(
            this.jdbc,
            "SELECT p.id, p.prb_no AS \"prbNo\", p.title, p.description, p.status, p.priority, p.owner, p.asset_nos AS \"assetNos\",\np.model_code AS \"modelCode\", p.alertname, p.root_cause AS \"rootCause\", p.workaround, p.resolution,\np.kb_no AS \"kbNo\", p.source, p.created_by AS \"createdBy\", p.created_at AS \"createdAt\",\np.updated_at AS \"updatedAt\", p.resolved_at AS \"resolvedAt\", p.closed_at AS \"closedAt\"\n, (SELECT COUNT(*) FROM problem_link l WHERE l.problem_id = p.id AND l.link_type = 'WORKORDER') AS \"woCount\", (SELECT COUNT(*) FROM problem_link l WHERE l.problem_id = p.id AND l.link_type = 'ALERT') AS \"alertCount\" FROM problem p"
                + w
                + " ORDER BY CASE p.status WHEN 'NEW' THEN 0 WHEN 'ANALYZING' THEN 1 WHEN 'KNOWN_ERROR' THEN 2 ELSE 3 END, CASE p.priority WHEN '高' THEN 0 WHEN '中' THEN 1 ELSE 2 END, p.updated_at DESC LIMIT ? OFFSET ?",
            args.toArray()
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("records", rows);
        return out;
    }

    public Map<String, Object> stats() {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Long> by = new LinkedHashMap<>();
        STATUS_LABEL.keySet().forEach(s -> by.put(s, 0L));
        this.jdbc
            .queryForList("SELECT status, COUNT(*) AS n FROM problem WHERE deleted_at IS NULL GROUP BY status")
            .forEach(r -> by.put((String)r.get("status"), ((Number)r.get("n")).longValue()));
        out.put("byStatus", by);
        out.put(
            "resolved30d",
            this.jdbc.queryForObject("SELECT COUNT(*) FROM problem WHERE deleted_at IS NULL AND resolved_at > NOW() - interval '30 days'", Long.class)
        );
        out.put(
            "avgResolveDays",
            this.jdbc
                .queryForObject(
                    "SELECT ROUND(COALESCE(AVG(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 86400), 0)::numeric, 1)\nFROM problem WHERE deleted_at IS NULL AND resolved_at > NOW() - interval '180 days'\n",
                    Double.class
                )
        );
        return out;
    }

    public Map<String, Object> detail(String idOrNo) {
        Map<String, Object> p = this.require(idOrNo);
        long id = ((Number)p.get("id")).longValue();
        Map<String, Object> out = new LinkedHashMap<>(p);
        out.put("links", this.links(id));
        out.put(
            "flow",
            this.jdbc
                .queryForList(
                    "SELECT from_status AS \"fromStatus\", to_status AS \"toStatus\", actor, comment, created_at AS \"createdAt\"\nFROM problem_flow WHERE problem_id = ? ORDER BY id",
                    new Object[]{id}
                )
        );
        List<String> assets = (List<String>)p.get("assetNos");
        out.put(
            "assets",
            assets != null && !assets.isEmpty()
                ? this.jdbc
                    .queryForList(
                        "SELECT asset_no AS \"assetNo\", name, model_code AS \"modelCode\", rack_code AS \"rackCode\", status_biz AS \"statusBiz\"\nFROM asset WHERE asset_no = ANY(?)",
                        new Object[]{assets.toArray(new String[0])}
                    )
                : List.of()
        );
        out.put(
            "recommend", this.kb.recommend((String)p.get("alertname"), assets != null && !assets.isEmpty() ? assets.get(0) : null, (String)p.get("title"), 5)
        );
        return out;
    }

    private List<Map<String, Object>> links(long id) {
        return this.jdbc
            .queryForList(
                "SELECT l.id, l.link_type AS \"linkType\", l.ref_no AS \"refNo\", l.created_at AS \"createdAt\",\n       CASE l.link_type\n         WHEN 'WORKORDER' THEN (SELECT w.title FROM workorder w WHERE w.wo_no = l.ref_no)\n         WHEN 'ALERT' THEN (SELECT a.alertname || '：' || COALESCE(a.summary, '') FROM alert_event a WHERE a.id::text = l.ref_no)\n         WHEN 'CHANGE' THEN (SELECT c.title FROM change_request c WHERE c.cr_no = l.ref_no) END AS title,\n       CASE l.link_type\n         WHEN 'WORKORDER' THEN (SELECT w.status FROM workorder w WHERE w.wo_no = l.ref_no)\n         WHEN 'ALERT' THEN (SELECT a.status FROM alert_event a WHERE a.id::text = l.ref_no)\n         WHEN 'CHANGE' THEN (SELECT c.status FROM change_request c WHERE c.cr_no = l.ref_no) END AS status,\n       CASE l.link_type\n         WHEN 'WORKORDER' THEN (SELECT w.apply_time FROM workorder w WHERE w.wo_no = l.ref_no)\n         WHEN 'ALERT' THEN (SELECT a.starts_at::timestamptz FROM alert_event a WHERE a.id::text = l.ref_no)\n         WHEN 'CHANGE' THEN (SELECT c.created_at::timestamptz FROM change_request c WHERE c.cr_no = l.ref_no) END AS \"refTime\"\nFROM problem_link l WHERE l.problem_id = ? ORDER BY l.link_type, l.id\n",
                new Object[]{id}
            );
    }

    @Transactional
    public Map<String, Object> create(ProblemService.ProblemReq r, String user) {
        this.validate(r);
        String no = this.seq.next("PRB-" + SeqNoGenerator.todaySegment(), 3);
        Long id = (Long)this.jdbc
            .queryForObject(
                "INSERT INTO problem (prb_no, title, description, priority, owner, asset_nos, model_code, alertname, source, created_by)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id\n",
                Long.class,
                new Object[]{
                    no,
                    r.title().trim(),
                    blank(r.description()),
                    prio(r.priority()),
                    blank(r.owner()),
                    JdbcRows.textArray(r.assetNos(), 200, 50),
                    blank(r.modelCode()),
                    blank(r.alertname()),
                    r.source() != null && !r.source().isBlank() ? r.source() : "MANUAL",
                    user
                }
            );
        this.flow(id, null, "NEW", user, "创建问题单");
        if (r.links() != null) {
            for (ProblemService.LinkReq l : r.links()) {
                this.addLink(id, l, user);
            }
        }

        if (blank(r.owner()) != null && !r.owner().trim().equals(user)) {
            this.notify.notifyUser(r.owner().trim(), "PROBLEM", "你被指派为问题单 " + no + " 负责人", r.title(), no);
        }

        return this.detail(no);
    }

    @Transactional
    public Map<String, Object> update(String idOrNo, ProblemService.ProblemReq r, String user) {
        Map<String, Object> p = this.require(idOrNo);
        if ("CLOSED".equals(p.get("status"))) {
            throw BusinessException.conflict("问题单已关闭，不能修改");
        } else {
            this.validate(r);
            String oldOwner = (String)p.get("owner");
            this.jdbc
                .update(
                    "UPDATE problem SET title = ?, description = ?, priority = ?, owner = ?, asset_nos = ?, model_code = ?,\n       alertname = ?, root_cause = COALESCE(?, root_cause), workaround = COALESCE(?, workaround),\n       updated_at = NOW() WHERE id = ?\n",
                    new Object[]{
                        r.title().trim(),
                        blank(r.description()),
                        prio(r.priority()),
                        blank(r.owner()),
                        JdbcRows.textArray(r.assetNos(), 200, 50),
                        blank(r.modelCode()),
                        blank(r.alertname()),
                        blank(r.rootCause()),
                        blank(r.workaround()),
                        p.get("id")
                    }
                );
            String owner = blank(r.owner());
            if (owner != null && !owner.equals(oldOwner) && !owner.equals(user)) {
                this.notify.notifyUser(owner, "PROBLEM", "你被指派为问题单 " + p.get("prbNo") + " 负责人", r.title(), (String)p.get("prbNo"));
            }

            return this.detail((String)p.get("prbNo"));
        }
    }

    @Transactional
    public Map<String, Object> transition(String idOrNo, ProblemService.TransitionReq r, String user) {
        Map<String, Object> p = this.require(idOrNo);
        long id = ((Number)p.get("id")).longValue();
        String from = (String)p.get("status");
        String to = r == null ? null : r.to();
        if (to != null && TRANSITIONS.containsKey(to)) {
            if (!TRANSITIONS.get(from).contains(to)) {
                throw BusinessException.conflict("不能从「" + STATUS_LABEL.get(from) + "」流转到「" + STATUS_LABEL.get(to) + "」");
            } else {
                String rootCause = firstNonBlank(r.rootCause(), (String)p.get("rootCause"));
                String workaround = firstNonBlank(r.workaround(), (String)p.get("workaround"));
                String resolution = firstNonBlank(r.resolution(), (String)p.get("resolution"));
                String kbNo = (String)p.get("kbNo");
                switch (to) {
                    case "KNOWN_ERROR":
                        if (rootCause == null || workaround == null) {
                            throw BusinessException.badRequest("转为已知错误须填写根本原因与临时解决办法");
                        }

                        kbNo = this.kb
                            .upsertKnownError(
                                kbNo,
                                (String)p.get("prbNo"),
                                (String)p.get("title"),
                                rootCause,
                                workaround,
                                null,
                                (String)p.get("alertname"),
                                (String)p.get("modelCode"),
                                user
                            );
                        break;
                    case "RESOLVED":
                        if (resolution == null) {
                            throw BusinessException.badRequest("解决问题须填写永久解决方案");
                        }

                        if (kbNo != null || Boolean.TRUE.equals(r.createKb())) {
                            kbNo = this.kb
                                .upsertKnownError(
                                    kbNo,
                                    (String)p.get("prbNo"),
                                    (String)p.get("title"),
                                    rootCause,
                                    workaround,
                                    resolution,
                                    (String)p.get("alertname"),
                                    (String)p.get("modelCode"),
                                    user
                                );
                        }
                        break;
                    case "CLOSED":
                        if (!"RESOLVED".equals(from) && (r.comment() == null || r.comment().isBlank())) {
                            throw BusinessException.badRequest("未解决直接关闭须填写关闭原因（如重复问题、无法复现）");
                        }
                }

                int updated = this.jdbc
                    .update(
                        "UPDATE problem SET status = ?, root_cause = ?, workaround = ?, resolution = ?, kb_no = ?, updated_at = NOW(),\n       resolved_at = CASE WHEN ? = 'RESOLVED' THEN NOW() WHEN ? = 'ANALYZING' THEN NULL ELSE resolved_at END,\n       closed_at = CASE WHEN ? = 'CLOSED' THEN NOW() ELSE closed_at END\nWHERE id = ? AND status = ?\n",
                        new Object[]{to, rootCause, workaround, resolution, kbNo, to, to, to, id, from}
                    );
                if (updated == 0) {
                    // CAS：状态已被并发流转改写，拒绝本次提交（事务回滚已产生的副作用）
                    throw BusinessException.conflict("问题单状态已被并发变更，请刷新后重试");
                }
                this.flow(id, from, to, user, blank(r.comment()));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("prbNo", p.get("prbNo"));
                data.put("title", p.get("title"));
                data.put("from", from);
                data.put("to", to);
                data.put("kbNo", kbNo);
                this.events.publish(DomainEvent.of("problem.status_changed", "problem", (String)p.get("prbNo"), user, data));
                String owner = (String)p.get("owner");
                if (owner != null && !owner.equals(user)) {
                    this.notify
                        .notifyUser(
                            owner,
                            "PROBLEM",
                            "问题单 " + p.get("prbNo") + " " + STATUS_LABEL.get(from) + " → " + STATUS_LABEL.get(to),
                            (String)p.get("title"),
                            (String)p.get("prbNo")
                        );
                }

                return this.detail((String)p.get("prbNo"));
            }
        } else {
            throw BusinessException.badRequest("目标状态无效");
        }
    }

    @Transactional
    public Map<String, Object> link(String idOrNo, ProblemService.LinkReq l, String user) {
        Map<String, Object> p = this.require(idOrNo);
        this.addLink(((Number)p.get("id")).longValue(), l, user);
        return this.detail((String)p.get("prbNo"));
    }

    public void unlink(String idOrNo, long linkId) {
        Map<String, Object> p = this.require(idOrNo);
        if ("CLOSED".equals(p.get("status"))) {
            // 与 update 的关闭态校验保持一致：已关闭问题单不允许解除关联
            throw BusinessException.conflict("问题单已关闭，不能修改");
        } else {
            this.jdbc.update("DELETE FROM problem_link WHERE id = ? AND problem_id = ?", new Object[]{linkId, p.get("id")});
        }
    }

    public void delete(String idOrNo) {
        Map<String, Object> p = this.require(idOrNo);
        if (!"NEW".equals(p.get("status"))) {
            throw BusinessException.conflict("只能删除「新建」状态的问题单，其余请走关闭流程");
        } else {
            this.jdbc.update("UPDATE problem SET deleted_at = NOW() WHERE id = ?", new Object[]{p.get("id")});
        }
    }

    public List<Map<String, Object>> byRef(String type, String ref) {
        return this.jdbc
            .queryForList(
                "SELECT p.prb_no AS \"prbNo\", p.title, p.status FROM problem p JOIN problem_link l ON l.problem_id = p.id\nWHERE l.link_type = ? AND l.ref_no = ? AND p.deleted_at IS NULL ORDER BY p.id DESC\n",
                new Object[]{type, ref}
            );
    }

    public Map<String, Object> suggestions(int faultThreshold, int alertThreshold, int modelThreshold) {
        int ft = Math.max(2, faultThreshold);
        int at = Math.max(2, alertThreshold);
        int mt = Math.max(2, modelThreshold);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
            "repeatFaults",
            JdbcRows.list(
                this.jdbc,
                "SELECT x.asset_no AS \"assetNo\", a.name AS \"assetName\", a.model_code AS \"modelCode\", a.rack_code AS \"rackCode\",\n       COUNT(*) AS n, array_agg(x.wo_no ORDER BY x.apply_time DESC) AS \"woNos\",\n       MAX(x.apply_time) AS \"lastAt\"\nFROM (SELECT w.wo_no, w.apply_time, btrim(unnest(string_to_array(w.related_assets, ','))) AS asset_no\n      FROM workorder w\n      WHERE w.deleted_at IS NULL AND w.wo_type = '故障维修' AND w.apply_time > NOW() - interval '30 days'\n        AND w.related_assets IS NOT NULL) x\nJOIN asset a ON a.asset_no = x.asset_no\nWHERE NOT EXISTS (SELECT 1 FROM problem p WHERE p.deleted_at IS NULL\n                  AND p.status IN ('NEW', 'ANALYZING', 'KNOWN_ERROR') AND p.asset_nos @> ARRAY[x.asset_no::text])\nGROUP BY x.asset_no, a.name, a.model_code, a.rack_code HAVING COUNT(*) >= ?\nORDER BY COUNT(*) DESC LIMIT 50\n",
                ft
            )
        );
        out.put(
            "repeatAlerts",
            JdbcRows.list(
                this.jdbc,
                "SELECT e.asset_no AS \"assetNo\", a.name AS \"assetName\", a.model_code AS \"modelCode\", e.alertname,\n       COUNT(*) AS episodes, MAX(e.starts_at) AS \"lastAt\", array_agg(e.id::text ORDER BY e.id DESC) AS \"alertIds\"\nFROM alert_event e JOIN asset a ON a.asset_no = e.asset_no\nWHERE e.starts_at > NOW() - interval '7 days' AND NOT e.suppressed\n  AND NOT EXISTS (SELECT 1 FROM problem p WHERE p.deleted_at IS NULL\n                  AND p.status IN ('NEW', 'ANALYZING', 'KNOWN_ERROR')\n                  AND p.asset_nos @> ARRAY[e.asset_no::text] AND p.alertname = e.alertname)\nGROUP BY e.asset_no, a.name, a.model_code, e.alertname HAVING COUNT(*) >= ?\nORDER BY COUNT(*) DESC LIMIT 50\n",
                at
            )
        );
        out.put(
            "modelDefects",
            JdbcRows.list(
                this.jdbc,
                "SELECT a.model_code AS \"modelCode\", COUNT(DISTINCT a.asset_no) AS assets, COUNT(*) AS faults,\n       (array_agg(DISTINCT a.asset_no))[1:20] AS \"assetNos\"\nFROM (SELECT w.wo_no, btrim(unnest(string_to_array(w.related_assets, ','))) AS asset_no\n      FROM workorder w\n      WHERE w.deleted_at IS NULL AND w.wo_type = '故障维修' AND w.apply_time > NOW() - interval '30 days'\n        AND w.related_assets IS NOT NULL) x\nJOIN asset a ON a.asset_no = x.asset_no\nWHERE a.model_code IS NOT NULL AND a.model_code <> ''\n  AND NOT EXISTS (SELECT 1 FROM problem p WHERE p.deleted_at IS NULL\n                  AND p.status IN ('NEW', 'ANALYZING', 'KNOWN_ERROR') AND p.model_code = a.model_code)\nGROUP BY a.model_code HAVING COUNT(DISTINCT a.asset_no) >= ?\nORDER BY COUNT(DISTINCT a.asset_no) DESC LIMIT 30\n",
                mt
            )
        );
        return out;
    }

    private void addLink(long problemId, ProblemService.LinkReq l, String user) {
        if (l != null && l.type() != null && Set.of("WORKORDER", "ALERT", "CHANGE").contains(l.type())) {
            String ref = l.ref() == null ? "" : l.ref().trim();
            String var7 = l.type();

            boolean exists = switch (var7) {
                case "WORKORDER" -> Boolean.TRUE
                .equals(
                    this.jdbc
                        .queryForObject("SELECT EXISTS (SELECT 1 FROM workorder WHERE wo_no = ? AND deleted_at IS NULL)", Boolean.class, new Object[]{ref})
                );
                case "ALERT" -> ref.matches("\\d{1,18}")
                && Boolean.TRUE
                    .equals(
                        this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM alert_event WHERE id = ?)", Boolean.class, new Object[]{Long.parseLong(ref)})
                    );
                default -> Boolean.TRUE
                .equals(
                    this.jdbc
                        .queryForObject("SELECT EXISTS (SELECT 1 FROM change_request WHERE cr_no = ? AND deleted_at IS NULL)", Boolean.class, new Object[]{ref})
                );
            };
            if (!exists) {
                throw BusinessException.badRequest("关联对象不存在：" + ref);
            } else {
                this.jdbc
                    .update(
                        "INSERT INTO problem_link (problem_id, link_type, ref_no, created_by) VALUES (?, ?, ?, ?)\nON CONFLICT (problem_id, link_type, ref_no) DO NOTHING\n",
                        new Object[]{problemId, l.type(), ref, user}
                    );
                this.jdbc.update("UPDATE problem SET updated_at = NOW() WHERE id = ?", new Object[]{problemId});
            }
        } else {
            throw BusinessException.badRequest("关联类型须为 WORKORDER / ALERT / CHANGE");
        }
    }

    private void flow(long id, String from, String to, String actor, String comment) {
        this.jdbc
            .update(
                "INSERT INTO problem_flow (problem_id, from_status, to_status, actor, comment) VALUES (?, ?, ?, ?, ?)",
                new Object[]{id, from, to, actor, comment}
            );
    }

    private Map<String, Object> require(String idOrNo) {
        Map<String, Object> p = JdbcRows.one(
            this.jdbc,
            "SELECT p.id, p.prb_no AS \"prbNo\", p.title, p.description, p.status, p.priority, p.owner, p.asset_nos AS \"assetNos\",\np.model_code AS \"modelCode\", p.alertname, p.root_cause AS \"rootCause\", p.workaround, p.resolution,\np.kb_no AS \"kbNo\", p.source, p.created_by AS \"createdBy\", p.created_at AS \"createdAt\",\np.updated_at AS \"updatedAt\", p.resolved_at AS \"resolvedAt\", p.closed_at AS \"closedAt\"\n FROM problem p WHERE p.deleted_at IS NULL AND (p.prb_no = ? OR p.id::text = ?)",
            idOrNo,
            idOrNo
        );
        if (p == null) {
            throw BusinessException.notFound("问题单不存在：" + idOrNo);
        } else {
            return p;
        }
    }

    private void validate(ProblemService.ProblemReq r) {
        if (r == null || r.title() == null || r.title().isBlank() || r.title().length() > 200) {
            throw BusinessException.badRequest("请填写问题标题（不超过 200 字）");
        } else if (r.description() != null && r.description().length() > 20000) {
            throw BusinessException.badRequest("描述过长");
        } else if (blank(r.owner()) != null
            && Boolean.FALSE
                .equals(
                    this.jdbc
                        .queryForObject("SELECT EXISTS (SELECT 1 FROM users WHERE username = ? AND status = 1)", Boolean.class, new Object[]{r.owner().trim()})
                )) {
            throw BusinessException.badRequest("负责人不存在或已停用：" + r.owner());
        }
    }

    private static String prio(String p) {
        String v = p != null && !p.isBlank() ? p.trim() : "中";
        if (!Set.of("高", "中", "低").contains(v)) {
            throw BusinessException.badRequest("优先级须为 高 / 中 / 低");
        } else {
            return v;
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : (b != null && !b.isBlank() ? b : null);
    }

    private static String blank(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("NEW", "新建");
        m.put("ANALYZING", "分析中");
        m.put("KNOWN_ERROR", "已知错误");
        m.put("RESOLVED", "已解决");
        m.put("CLOSED", "已关闭");
        STATUS_LABEL = Collections.unmodifiableMap(m);
    }

    public static record LinkReq(String type, String ref) {
    }

    public static record ProblemReq(
        String title,
        String description,
        String priority,
        String owner,
        List<String> assetNos,
        String modelCode,
        String alertname,
        String rootCause,
        String workaround,
        String source,
        List<ProblemService.LinkReq> links
    ) {
    }

    public static record TransitionReq(String to, String rootCause, String workaround, String resolution, String comment, Boolean createKb) {
    }
}
