package com.dcim.knowledge;

import com.dcim.common.BusinessException;
import com.dcim.common.JdbcRows;
import com.dcim.common.seq.SeqNoGenerator;
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
public class KbService {
    public static final Map<String, String> CATEGORIES;
    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");
    private static final int MAX_CONTENT = 100000;
    private static final String COLS = "a.id, a.kb_no AS \"kbNo\", a.title, a.category, a.status, a.tags, a.alertnames, a.model_codes AS \"modelCodes\",\na.category_codes AS \"categoryCodes\", a.source_problem AS \"sourceProblem\", a.views, a.helpful, a.unhelpful,\na.created_by AS \"createdBy\", a.updated_by AS \"updatedBy\", a.created_at AS \"createdAt\",\na.updated_at AS \"updatedAt\", a.published_at AS \"publishedAt\"\n";
    private final JdbcTemplate jdbc;
    private final SeqNoGenerator seq;

    public KbService(JdbcTemplate jdbc, SeqNoGenerator seq) {
        this.jdbc = jdbc;
        this.seq = seq;
    }

    public Map<String, Object> list(String keyword, String category, String status, String tag, boolean canWrite, long page, long size) {
        StringBuilder w = new StringBuilder(" WHERE a.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (!canWrite) {
            w.append(" AND a.status = 'PUBLISHED'");
        } else if (status != null && !status.isBlank()) {
            w.append(" AND a.status = ?");
            args.add(status.trim());
        } else {
            w.append(" AND a.status <> 'ARCHIVED'");
        }

        if (category != null && !category.isBlank()) {
            w.append(" AND a.category = ?");
            args.add(category.trim());
        }

        if (tag != null && !tag.isBlank()) {
            w.append(" AND a.tags @> ARRAY[?]::text[]");
            args.add(tag.trim());
        }

        String order = " ORDER BY a.updated_at DESC";
        List<Object> orderArgs = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            String like = "%" + kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (a.title ILIKE ? OR a.content ILIKE ? OR a.kb_no = ? OR ? = ANY(a.tags) OR ? = ANY(a.alertnames) OR ? = ANY(a.model_codes))");
            args.add(like);
            args.add(like);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            args.add(kw);
            order = " ORDER BY (a.title ILIKE ?) DESC, similarity(a.title, ?) DESC, a.helpful - a.unhelpful DESC, a.updated_at DESC";
            orderArgs.add(like);
            orderArgs.add(kw);
        }

        Long total = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM kb_article a" + w, Long.class, args.toArray());
        long sz = Math.max(1L, Math.min(size, 100L));
        List<Object> all = new ArrayList<>(args);
        all.addAll(orderArgs);
        all.add(sz);
        all.add((Math.max(page, 1L) - 1L) * sz);
        List<Map<String, Object>> rows = JdbcRows.list(
            this.jdbc,
            "SELECT a.id, a.kb_no AS \"kbNo\", a.title, a.category, a.status, a.tags, a.alertnames, a.model_codes AS \"modelCodes\",\na.category_codes AS \"categoryCodes\", a.source_problem AS \"sourceProblem\", a.views, a.helpful, a.unhelpful,\na.created_by AS \"createdBy\", a.updated_by AS \"updatedBy\", a.created_at AS \"createdAt\",\na.updated_at AS \"updatedAt\", a.published_at AS \"publishedAt\"\n, left(btrim(regexp_replace(regexp_replace(regexp_replace(a.content, '^\\s*(#+|>|[-*+]|\\d+[.)]|\\|)\\s*', '', 'gn'), '[*`|]', '', 'g'), '\\s+', ' ', 'g')), 160) AS excerpt FROM kb_article a"
                + w
                + order
                + " LIMIT ? OFFSET ?",
            all.toArray()
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("records", rows);
        return out;
    }

    public Map<String, Object> get(String idOrNo, boolean canWrite, boolean countView) {
        Map<String, Object> a = JdbcRows.one(
            this.jdbc,
            "SELECT a.id, a.kb_no AS \"kbNo\", a.title, a.category, a.status, a.tags, a.alertnames, a.model_codes AS \"modelCodes\",\na.category_codes AS \"categoryCodes\", a.source_problem AS \"sourceProblem\", a.views, a.helpful, a.unhelpful,\na.created_by AS \"createdBy\", a.updated_by AS \"updatedBy\", a.created_at AS \"createdAt\",\na.updated_at AS \"updatedAt\", a.published_at AS \"publishedAt\"\n, a.content FROM kb_article a WHERE a.deleted_at IS NULL AND (a.kb_no = ? OR a.id::text = ?)",
            idOrNo,
            idOrNo
        );
        if (a != null && (canWrite || "PUBLISHED".equals(a.get("status")))) {
            if (countView && "PUBLISHED".equals(a.get("status"))) {
                this.jdbc.update("UPDATE kb_article SET views = views + 1 WHERE id = ?", new Object[]{a.get("id")});
            }

            return a;
        } else {
            throw BusinessException.notFound("文章不存在或未发布");
        }
    }

    @Transactional
    public Map<String, Object> save(Long id, KbService.ArticleReq r, String user) {
        if (r.title() != null && !r.title().isBlank() && r.title().length() <= 200) {
            String cat = r.category() != null && !r.category().isBlank() ? r.category() : "TROUBLESHOOT";
            if (!CATEGORIES.containsKey(cat)) {
                throw BusinessException.badRequest("分类须为 " + String.join(" / ", CATEGORIES.keySet()));
            } else {
                String content = r.content() == null ? "" : r.content();
                if (content.length() > 100000) {
                    throw BusinessException.badRequest("正文不超过 100000 字符");
                } else {
                    String status = r.status() != null && !r.status().isBlank() ? r.status() : "DRAFT";
                    if (!STATUSES.contains(status)) {
                        throw BusinessException.badRequest("状态须为 DRAFT / PUBLISHED / ARCHIVED");
                    } else if ("PUBLISHED".equals(status) && content.isBlank()) {
                        throw BusinessException.badRequest("发布前请填写正文");
                    } else {
                        String[] tags = JdbcRows.textArray(r.tags(), 20, 30);
                        String[] alertnames = JdbcRows.textArray(r.alertnames(), 50, 200);
                        String[] models = JdbcRows.textArray(r.modelCodes(), 50, 80);
                        String[] cats = JdbcRows.textArray(r.categoryCodes(), 20, 50);
                        if (id == null) {
                            String no = this.seq.next("KB", 6);
                            Long newId = (Long)this.jdbc
                                .queryForObject(
                                    "INSERT INTO kb_article (kb_no, title, category, content, status, tags, alertnames, model_codes,\n                        category_codes, source_problem, created_by, updated_by, published_at)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'PUBLISHED' THEN NOW() END) RETURNING id\n",
                                    Long.class,
                                    new Object[]{
                                        no,
                                        r.title().trim(),
                                        cat,
                                        content,
                                        status,
                                        tags,
                                        alertnames,
                                        models,
                                        cats,
                                        blank(r.sourceProblem()),
                                        user,
                                        user,
                                        status
                                    }
                                );
                            return this.get(String.valueOf(newId), true, false);
                        } else {
                            int n = this.jdbc
                                .update(
                                    "UPDATE kb_article SET title = ?, category = ?, content = ?, status = ?, tags = ?, alertnames = ?,\n       model_codes = ?, category_codes = ?, updated_by = ?, updated_at = NOW(),\n       published_at = CASE WHEN ? = 'PUBLISHED' THEN COALESCE(published_at, NOW()) ELSE published_at END\nWHERE id = ? AND deleted_at IS NULL\n",
                                    new Object[]{r.title().trim(), cat, content, status, tags, alertnames, models, cats, user, status, id}
                                );
                            if (n == 0) {
                                throw BusinessException.notFound("文章不存在");
                            } else {
                                return this.get(String.valueOf(id), true, false);
                            }
                        }
                    }
                }
            }
        } else {
            throw BusinessException.badRequest("请填写标题（不超过 200 字）");
        }
    }

    public void delete(long id) {
        if (this.jdbc.update("UPDATE kb_article SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL", new Object[]{id}) == 0) {
            throw BusinessException.notFound("文章不存在");
        }
    }

    @Transactional
    public Map<String, Object> feedback(long id, boolean helpful, String user) {
        this.get(String.valueOf(id), false, false);
        this.jdbc
            .update(
                "INSERT INTO kb_feedback (article_id, username, helpful) VALUES (?, ?, ?)\nON CONFLICT (article_id, username) DO UPDATE SET helpful = EXCLUDED.helpful, created_at = NOW()\n",
                new Object[]{id, user, helpful}
            );
        this.jdbc
            .update(
                "UPDATE kb_article SET helpful = (SELECT COUNT(*) FROM kb_feedback WHERE article_id = ? AND helpful),\n       unhelpful = (SELECT COUNT(*) FROM kb_feedback WHERE article_id = ? AND NOT helpful) WHERE id = ?\n",
                new Object[]{id, id, id}
            );
        return JdbcRows.one(this.jdbc, "SELECT helpful, unhelpful FROM kb_article WHERE id = ?", id);
    }

    public List<Map<String, Object>> recommend(String alertname, String assetNo, String text, int limit) {
        String model = null;
        String catPath = null;
        if (assetNo != null && !assetNo.isBlank()) {
            Map<String, Object> a = JdbcRows.one(
                this.jdbc,
                "SELECT a.model_code, c.path FROM asset a LEFT JOIN category c ON c.category_code = a.category_code\nWHERE a.asset_no = ?",
                assetNo.trim()
            );
            if (a != null) {
                model = (String)a.get("model_code");
                catPath = (String)a.get("path");
            }
        }

        String t = text == null ? "" : text.trim();
        if (t.length() > 200) {
            t = t.substring(0, 200);
        }

        String an = alertname == null ? "" : alertname.trim();
        return JdbcRows.list(
            this.jdbc,
            "SELECT * FROM (\n  SELECT a.id, a.kb_no AS \"kbNo\", a.title, a.category, a.helpful, a.views,\n         (CASE WHEN ? <> '' AND ? = ANY(a.alertnames) THEN 5 ELSE 0 END\n        + CASE WHEN ?::text IS NOT NULL AND ?::text = ANY(a.model_codes) THEN 4 ELSE 0 END\n        + CASE WHEN ?::text IS NOT NULL AND EXISTS (\n               SELECT 1 FROM unnest(a.category_codes) cc JOIN category c ON c.category_code = cc\n               WHERE ?::text LIKE c.path || '%') THEN 2 ELSE 0 END\n        + CASE WHEN ? <> '' THEN 3 * GREATEST(similarity(a.title, ?), word_similarity(?, a.title)) ELSE 0 END\n         )::float8 AS score\n  FROM kb_article a WHERE a.deleted_at IS NULL AND a.status = 'PUBLISHED'\n) x WHERE score >= 1 ORDER BY score DESC, helpful DESC, views DESC LIMIT ?\n",
            an,
            an,
            model,
            model,
            catPath,
            catPath,
            t,
            t,
            t,
            Math.max(1, Math.min(limit, 20))
        );
    }

    @Transactional
    public String upsertKnownError(
        String kbNo, String prbNo, String title, String rootCause, String workaround, String resolution, String alertname, String modelCode, String user
    ) {
        StringBuilder md = new StringBuilder();
        md.append("> 由问题单 ").append(prbNo).append(" 自动生成，随问题处理进展更新。\n\n");
        md.append("## 现象\n\n").append(title).append("\n\n");
        md.append("## 根本原因\n\n").append(nz(rootCause)).append("\n\n");
        md.append("## 临时解决办法（Workaround）\n\n").append(nz(workaround)).append("\n\n");
        if (resolution != null && !resolution.isBlank()) {
            md.append("## 永久解决方案\n\n").append(resolution).append("\n");
        }

        List<String> alerts = alertname != null && !alertname.isBlank() ? List.of(alertname) : List.of();
        List<String> models = modelCode != null && !modelCode.isBlank() ? List.of(modelCode) : List.of();
        Map<String, Object> existing = kbNo == null ? null : JdbcRows.one(this.jdbc, "SELECT id FROM kb_article WHERE kb_no = ? AND deleted_at IS NULL", kbNo);
        KbService.ArticleReq req = new KbService.ArticleReq(
            "【已知错误】" + truncate(title, 190), "KNOWN_ERROR", md.toString(), "PUBLISHED", List.of("已知错误"), alerts, models, List.of(), prbNo
        );
        Map<String, Object> saved = this.save(existing == null ? null : ((Number)existing.get("id")).longValue(), req, user);
        return (String)saved.get("kbNo");
    }

    private static String nz(String s) {
        return s != null && !s.isBlank() ? s : "（待补充）";
    }

    private static String truncate(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n));
    }

    private static String blank(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("TROUBLESHOOT", "故障处理");
        m.put("SOP", "操作规程");
        m.put("KNOWN_ERROR", "已知错误");
        m.put("FAQ", "常见问题");
        m.put("OTHER", "其他");
        CATEGORIES = Collections.unmodifiableMap(m);
    }

    public static record ArticleReq(
        String title,
        String category,
        String content,
        String status,
        List<String> tags,
        List<String> alertnames,
        List<String> modelCodes,
        List<String> categoryCodes,
        String sourceProblem
    ) {
    }
}
