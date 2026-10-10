package com.dcim.expiry;

import com.dcim.common.event.DomainEvent;
import com.dcim.common.event.DomainEventPublisher;
import com.dcim.common.notify.NotificationPort;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    prefix = "dcim.expiry",
    name = {"enabled"},
    havingValue = "true",
    matchIfMissing = true
)
public class ExpiryService {
    
    private static final Logger log = LoggerFactory.getLogger(ExpiryService.class);
    private static final int MAX_EVENTS = 2000;
    private final JdbcTemplate jdbc;
    private final NotificationPort notify;
    private final DomainEventPublisher events;

    public ExpiryService(JdbcTemplate jdbc, NotificationPort notify, DomainEventPublisher events) {
        this.jdbc = jdbc;
        this.notify = notify;
        this.events = events;
    }

    @Scheduled(
        cron = "${dcim.expiry.cron:0 50 8 * * *}"
    )
    public void daily() {
        try {
            Map<String, Object> r = this.run();
            log.info("[expiry] 到期提醒完成：{}", r);
        } catch (RuntimeException var2) {
            log.warn("[expiry] 到期提醒失败：{}", var2.getMessage(), var2);
        }
    }

    public Map<String, Object> run() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("warranty", this.remind("WARRANTY", this.stages("warranty_remind_days", "60,30,7")));
        out.put("contract", this.remind("CONTRACT", this.stages("contract_remind_days", "90,30,7")));
        return out;
    }

    private int remind(String kind, TreeSet<Integer> stages) {
        int maxStage = stages.isEmpty() ? 0 : stages.last();
        List<Map<String, Object>> due = "WARRANTY".equals(kind)
            ? this.jdbc
                .queryForList(
                    "SELECT a.asset_no AS ref, a.name, a.warranty_end AS end_date, a.rack_code AS rack, a.room_code AS room,\n       a.model_code AS model, (a.warranty_end - CURRENT_DATE) AS left_days\nFROM asset a\nWHERE a.deleted_at IS NULL AND a.warranty_end IS NOT NULL\n  AND a.warranty_end BETWEEN CURRENT_DATE AND CURRENT_DATE + ?::int\n  AND a.status_biz NOT IN ('报废处置', '待退役')\nORDER BY a.warranty_end, a.asset_no\n",
                    new Object[]{maxStage}
                )
            : this.jdbc
                .queryForList(
                    "SELECT c.contract_no AS ref, c.name, c.end_date, c.party, c.type, (c.end_date - CURRENT_DATE) AS left_days\nFROM contract c\nWHERE c.end_date IS NOT NULL AND c.end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + ?::int\n  AND c.status = '生效'\nORDER BY c.end_date, c.contract_no\n",
                    new Object[]{maxStage}
                );
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        int sent = 0;

        for (Map<String, Object> r : due) {
            LocalDate end = ((Date)r.get("end_date")).toLocalDate();
            // left 与 SQL 的 CURRENT_DATE 同源计算，消除 JVM/DB 跨时区错位
            int left = ((Number)r.get("left_days")).intValue();
            Integer stage = stages.ceiling(left);
            if (stage != null) {
                int n = this.jdbc
                    .update(
                        "INSERT INTO expiry_notice (kind, ref_no, due_date, stage_days) VALUES (?, ?, ?, ?)\nON CONFLICT (kind, ref_no, due_date, stage_days) DO NOTHING\n",
                        new Object[]{kind, r.get("ref"), end, stage}
                    );
                if (n != 0) {
                    r.put("daysLeft", left);
                    r.put("endDate", end.toString());
                    groups.computeIfAbsent(end + "|" + stage, k -> new ArrayList<>()).add(r);
                    if (sent < 2000) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("name", r.get("name"));
                        data.put("endDate", end.toString());
                        data.put("daysLeft", left);
                        data.put("stage", stage);
                        if ("WARRANTY".equals(kind)) {
                            data.put("assetNo", r.get("ref"));
                            data.put("rack", r.get("rack"));
                            data.put("room", r.get("room"));
                        } else {
                            data.put("contractNo", r.get("ref"));
                            data.put("party", r.get("party"));
                        }

                        this.events
                            .publish(
                                DomainEvent.of(
                                    "WARRANTY".equals(kind) ? "asset.warranty_expiring" : "contract.expiring",
                                    "WARRANTY".equals(kind) ? "asset" : "contract",
                                    String.valueOf(r.get("ref")),
                                    "system",
                                    data
                                )
                            );
                    }

                    sent++;
                }
            }
        }

        List<String> roles = this.roles();

        for (List<Map<String, Object>> g : groups.values()) {
            Map<String, Object> first = g.get(0);
            String what = "WARRANTY".equals(kind) ? "资产保修" : "合同";
            String title = g.size() == 1
                ? what + "将于 " + first.get("endDate") + " 到期（剩余 " + first.get("daysLeft") + " 天）"
                : g.size() + " 项" + what + "将于 " + first.get("endDate") + " 到期（剩余 " + first.get("daysLeft") + " 天）";
            StringBuilder content = new StringBuilder();

            for (int i = 0; i < Math.min(g.size(), 20); i++) {
                Map<String, Object> rx = g.get(i);
                content.append(rx.get("ref")).append(" ").append(rx.get("name"));
                if (rx.get("rack") != null) {
                    content.append("（").append(rx.get("rack")).append("）");
                }

                content.append("\n");
            }

            if (g.size() > 20) {
                content.append("…等共 ").append(g.size()).append(" 项，请在「到期提醒」页查看全部");
            }

            String ref = g.size() == 1 ? String.valueOf(first.get("ref")) : null;
            String type = "WARRANTY".equals(kind) ? "WARRANTY_EXPIRING" : "CONTRACT_EXPIRING";

            for (String role : roles) {
                this.notify.notifyRole(role, type, title, truncate(content.toString(), 1000), ref);
            }
        }

        return sent;
    }

    public Map<String, Object> upcoming(int days, String kind) {
        int d = Math.max(1, Math.min(days, 730));
        Map<String, Object> out = new LinkedHashMap<>();
        if (kind == null || "WARRANTY".equals(kind)) {
            out.put(
                "warranty",
                this.jdbc
                    .queryForList(
                        "SELECT a.asset_no AS \"assetNo\", a.name, a.model_code AS \"modelCode\", a.sn, a.status_biz AS \"statusBiz\",\n       a.room_code AS \"roomCode\", a.rack_code AS \"rackCode\", a.warranty_end AS \"endDate\",\n       (a.warranty_end - CURRENT_DATE) AS \"daysLeft\", a.contract_no AS \"contractNo\"\nFROM asset a WHERE a.deleted_at IS NULL AND a.warranty_end IS NOT NULL\n  AND a.warranty_end <= CURRENT_DATE + ?::int AND a.warranty_end >= CURRENT_DATE - 365\n  AND a.status_biz NOT IN ('报废处置', '待退役')\nORDER BY a.warranty_end, a.asset_no LIMIT 2000\n",
                        new Object[]{d}
                    )
            );
        }

        if (kind == null || "CONTRACT".equals(kind)) {
            out.put(
                "contract",
                this.jdbc
                    .queryForList(
                        "SELECT c.contract_no AS \"contractNo\", c.name, c.type, c.party, c.amount, c.end_date AS \"endDate\",\n       (c.end_date - CURRENT_DATE) AS \"daysLeft\", c.status,\n       (SELECT COUNT(*) FROM contract_asset ca WHERE ca.contract_no = c.contract_no) AS \"assetCount\"\nFROM contract c WHERE c.end_date IS NOT NULL AND c.status = '生效'\n  AND c.end_date <= CURRENT_DATE + ?::int AND c.end_date >= CURRENT_DATE - 365\nORDER BY c.end_date LIMIT 2000\n",
                        new Object[]{d}
                    )
            );
        }

        out.put(
            "summary",
            this.jdbc
                .queryForMap(
                    "SELECT\n  (SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND warranty_end < CURRENT_DATE\n     AND status_biz IN ('在用(计费)', '维修中', '停用中')) AS \"outOfWarrantyInUse\",\n  (SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND warranty_end BETWEEN CURRENT_DATE AND CURRENT_DATE + 30\n     AND status_biz NOT IN ('报废处置', '待退役')) AS \"warranty30\",\n  (SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND warranty_end BETWEEN CURRENT_DATE AND CURRENT_DATE + 90\n     AND status_biz NOT IN ('报废处置', '待退役')) AS \"warranty90\",\n  (SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND warranty_end IS NULL\n     AND status_biz IN ('在用(计费)', '维修中', '停用中')) AS \"warrantyUnknown\",\n  (SELECT COUNT(*) FROM contract WHERE status = '生效' AND end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 30) AS \"contract30\",\n  (SELECT COUNT(*) FROM contract WHERE status = '生效' AND end_date BETWEEN CURRENT_DATE AND CURRENT_DATE + 90) AS \"contract90\",\n  (SELECT COUNT(*) FROM contract WHERE status = '生效' AND end_date < CURRENT_DATE) AS \"contractExpiredActive\"\n"
                )
        );
        return out;
    }

    private TreeSet<Integer> stages(String key, String def) {
        String v = this.setting(key, def);
        TreeSet<Integer> s = new TreeSet<>();

        for (String p : v.split("\\s*,\\s*")) {
            try {
                int n = Integer.parseInt(p.trim());
                if (n >= 0 && n <= 730) {
                    s.add(n);
                }
            } catch (NumberFormatException var10) {
            }
        }

        s.add(0);
        return s;
    }

    private List<String> roles() {
        List<String> out = new ArrayList<>();

        for (String r : this.setting("remind_roles", "R-ASSET").split("\\s*,\\s*")) {
            if (!r.isBlank()) {
                out.add(r.trim());
            }
        }

        return out;
    }

    private String setting(String key, String def) {
        List<String> v = this.jdbc.queryForList("SELECT setting_value FROM capacity_setting WHERE setting_key = ?", String.class, new Object[]{key});
        return !v.isEmpty() && v.get(0) != null && !v.get(0).isBlank() ? v.get(0) : def;
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
