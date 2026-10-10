package com.dcim.network;

import com.dcim.common.BusinessException;
import com.dcim.common.seq.SeqNoGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CableService {
    public static final Map<String, String> TYPES;
    private static final Set<String> STATUSES = Set.of("PLANNED", "CONNECTED", "REMOVED");
    private final JdbcTemplate jdbc;
    private final SeqNoGenerator seq;

    public CableService(JdbcTemplate jdbc, SeqNoGenerator seq) {
        this.jdbc = jdbc;
        this.seq = seq;
    }

    public Map<String, Object> list(String assetNo, String rackCode, String type, String status, String keyword, long page, long size) {
        StringBuilder w = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (assetNo != null && !assetNo.isBlank()) {
            w.append(" AND (c.a_asset_no = ? OR c.b_asset_no = ?)");
            args.add(assetNo.trim());
            args.add(assetNo.trim());
        }

        if (rackCode != null && !rackCode.isBlank()) {
            w.append(" AND (a.rack_code = ? OR b.rack_code = ?)");
            args.add(rackCode.trim());
            args.add(rackCode.trim());
        }

        if (type != null && !type.isBlank()) {
            w.append(" AND c.cable_type = ?");
            args.add(type.trim());
        }

        if (status != null && !status.isBlank()) {
            w.append(" AND c.status = ?");
            args.add(status.trim());
        } else {
            w.append(" AND c.status <> 'REMOVED'");
        }

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            w.append(" AND (c.cable_no ILIKE ? OR c.a_port ILIKE ? OR c.b_port ILIKE ? OR c.remark ILIKE ?)");

            for (int i = 0; i < 4; i++) {
                args.add(like);
            }
        }

        String from = " FROM cable c LEFT JOIN asset a ON a.asset_no = c.a_asset_no LEFT JOIN asset b ON b.asset_no = c.b_asset_no";
        Long total = (Long)this.jdbc.queryForObject("SELECT COUNT(*)" + from + w, Long.class, args.toArray());
        long sz = Math.max(1L, Math.min(size, 500L));
        args.add(sz);
        args.add((Math.max(page, 1L) - 1L) * sz);
        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "SELECT c.id, c.cable_no AS \"cableNo\", c.cable_type AS \"cableType\",\n       c.a_asset_no AS \"aAssetNo\", a.name AS \"aAssetName\", a.rack_code AS \"aRack\", a.unit_no AS \"aUnit\", c.a_port AS \"aPort\",\n       c.b_asset_no AS \"bAssetNo\", b.name AS \"bAssetName\", b.rack_code AS \"bRack\", b.unit_no AS \"bUnit\", c.b_port AS \"bPort\",\n       c.length_m AS \"lengthM\", c.color, c.speed, c.status, c.remark, c.created_by AS \"createdBy\",\n       c.updated_at AS \"updatedAt\"\n"
                    + from
                    + w
                    + " ORDER BY c.updated_at DESC, c.id DESC LIMIT ? OFFSET ?",
                args.toArray()
            );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("records", rows);
        return out;
    }

    public List<Map<String, Object>> ports(String assetNo) {
        return this.jdbc
            .queryForList(
                "SELECT c.id, c.cable_no AS \"cableNo\", c.cable_type AS \"cableType\", c.status,\n       CASE WHEN c.a_asset_no = ? THEN c.a_port ELSE c.b_port END AS port,\n       CASE WHEN c.a_asset_no = ? THEN c.b_asset_no ELSE c.a_asset_no END AS \"peerAssetNo\",\n       CASE WHEN c.a_asset_no = ? THEN c.b_port ELSE c.a_port END AS \"peerPort\",\n       p.name AS \"peerName\", p.rack_code AS \"peerRack\"\nFROM cable c\nLEFT JOIN asset p ON p.asset_no = CASE WHEN c.a_asset_no = ? THEN c.b_asset_no ELSE c.a_asset_no END\nWHERE (c.a_asset_no = ? OR c.b_asset_no = ?) AND c.status <> 'REMOVED'\nORDER BY port\n",
                new Object[]{assetNo, assetNo, assetNo, assetNo, assetNo, assetNo}
            );
    }

    @Transactional
    public Map<String, Object> create(CableService.CableReq r, String user) {
        this.validate(r);
        String no = r.cableNo() != null && !r.cableNo().isBlank() ? r.cableNo().trim() : this.seq.next("CBL-" + SeqNoGenerator.todaySegment(), 4);
        String status = r.status() != null && !r.status().isBlank() ? r.status() : "CONNECTED";
        this.checkPorts(null, r);

        Long id;
        try {
            id = (Long)this.jdbc
                .queryForObject(
                    "INSERT INTO cable (cable_no, cable_type, a_asset_no, a_port, b_asset_no, b_port, length_m, color,\n                   speed, status, remark, created_by)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id\n",
                    Long.class,
                    new Object[]{
                        no,
                        r.cableType(),
                        r.aAssetNo().trim(),
                        r.aPort().trim(),
                        r.bAssetNo().trim(),
                        r.bPort().trim(),
                        r.lengthM(),
                        blank(r.color()),
                        blank(r.speed()),
                        status,
                        blank(r.remark()),
                        user
                    }
                );
        } catch (DuplicateKeyException var7) {
            throw BusinessException.conflict("线缆编号已存在或端口已被占用");
        }

        this.syncRelation(r.aAssetNo().trim(), r.bAssetNo().trim(), user);
        return this.get(id == null ? 0L : id);
    }

    @Transactional
    public Map<String, Object> update(long id, CableService.CableReq r, String user) {
        Map<String, Object> old = this.get(id);
        this.validate(r);
        String status = r.status() != null && !r.status().isBlank() ? r.status() : (String)old.get("status");
        if (!"REMOVED".equals(status)) {
            this.checkPorts(id, r);
        }

        try {
            this.jdbc
                .update(
                    "UPDATE cable SET cable_type = ?, a_asset_no = ?, a_port = ?, b_asset_no = ?, b_port = ?, length_m = ?,\n       color = ?, speed = ?, status = ?, remark = ?, updated_at = NOW() WHERE id = ?\n",
                    new Object[]{
                        r.cableType(),
                        r.aAssetNo().trim(),
                        r.aPort().trim(),
                        r.bAssetNo().trim(),
                        r.bPort().trim(),
                        r.lengthM(),
                        blank(r.color()),
                        blank(r.speed()),
                        status,
                        blank(r.remark()),
                        id
                    }
                );
        } catch (DuplicateKeyException var8) {
            throw BusinessException.conflict("端口已被占用");
        }

        this.syncRelation((String)old.get("aAssetNo"), (String)old.get("bAssetNo"), user);
        this.syncRelation(r.aAssetNo().trim(), r.bAssetNo().trim(), user);
        return this.get(id);
    }

    @Transactional
    public void remove(long id, String user) {
        Map<String, Object> old = this.get(id);
        this.jdbc.update("UPDATE cable SET status = 'REMOVED', updated_at = NOW() WHERE id = ?", new Object[]{id});
        this.syncRelation((String)old.get("aAssetNo"), (String)old.get("bAssetNo"), user);
    }

    @Transactional
    public void delete(long id, String user) {
        Map<String, Object> old = this.get(id);
        this.jdbc.update("DELETE FROM cable WHERE id = ?", new Object[]{id});
        this.syncRelation((String)old.get("aAssetNo"), (String)old.get("bAssetNo"), user);
    }

    public Map<String, Object> get(long id) {
        return (Map<String, Object>)this.jdbc
            .queryForList(
                "SELECT id, cable_no AS \"cableNo\", cable_type AS \"cableType\", a_asset_no AS \"aAssetNo\", a_port AS \"aPort\",\n       b_asset_no AS \"bAssetNo\", b_port AS \"bPort\", length_m AS \"lengthM\", color, speed, status, remark\nFROM cable WHERE id = ?",
                new Object[]{id}
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> BusinessException.notFound("线缆不存在"));
    }

    private void validate(CableService.CableReq r) {
        if (r == null || r.cableType() == null || !TYPES.containsKey(r.cableType())) {
            throw BusinessException.badRequest("线缆类型须为 " + String.join(" / ", TYPES.keySet()));
        } else if (blank(r.aAssetNo()) == null || blank(r.bAssetNo()) == null || blank(r.aPort()) == null || blank(r.bPort()) == null) {
            throw BusinessException.badRequest("请填写两端设备与端口");
        } else if (r.aPort().trim().length() > 50 || r.bPort().trim().length() > 50) {
            throw BusinessException.badRequest("端口名称不超过 50 字符");
        } else if (r.aAssetNo().trim().equals(r.bAssetNo().trim()) && r.aPort().trim().equalsIgnoreCase(r.bPort().trim())) {
            throw BusinessException.badRequest("两端不能是同一个端口");
        } else if (r.status() != null && !r.status().isBlank() && !STATUSES.contains(r.status())) {
            throw BusinessException.badRequest("状态须为 PLANNED / CONNECTED / REMOVED");
        } else if (r.lengthM() == null || r.lengthM().signum() >= 0 && r.lengthM().compareTo(BigDecimal.valueOf(100000L)) <= 0) {
            for (String a : List.of(r.aAssetNo().trim(), r.bAssetNo().trim())) {
                if (Boolean.FALSE
                    .equals(
                        this.jdbc
                            .queryForObject("SELECT EXISTS (SELECT 1 FROM asset WHERE asset_no = ? AND deleted_at IS NULL)", Boolean.class, new Object[]{a})
                    )) {
                    throw BusinessException.badRequest("资产不存在：" + a);
                }
            }
        } else {
            throw BusinessException.badRequest("长度不合法");
        }
    }

    private void checkPorts(Long selfId, CableService.CableReq r) {
        for (String[] end : List.of(new String[]{r.aAssetNo().trim(), r.aPort().trim()}, new String[]{r.bAssetNo().trim(), r.bPort().trim()})) {
            List<String> used = this.jdbc
                .queryForList(
                    "SELECT cable_no FROM cable WHERE status <> 'REMOVED' AND id IS DISTINCT FROM ?\n  AND ((a_asset_no = ? AND lower(a_port) = lower(?)) OR (b_asset_no = ? AND lower(b_port) = lower(?)))\n",
                    String.class,
                    new Object[]{selfId, end[0], end[1], end[0], end[1]}
                );
            if (!used.isEmpty()) {
                throw BusinessException.conflict(end[0] + " 的端口 " + end[1] + " 已被线缆 " + used.get(0) + " 占用");
            }
        }
    }

    void syncRelation(String a, String b, String user) {
        if (a != null && b != null && !a.equals(b)) {
            Long data = (Long)this.jdbc
                .queryForObject(
                    "SELECT COUNT(*) FROM cable WHERE status = 'CONNECTED' AND cable_type <> 'POWER'\n  AND ((a_asset_no = ? AND b_asset_no = ?) OR (a_asset_no = ? AND b_asset_no = ?))\n",
                    Long.class,
                    new Object[]{a, b, b, a}
                );
            String[] pair = a.compareTo(b) < 0 ? new String[]{a, b} : new String[]{b, a};
            this.upsertRel(pair[0], "CONNECTS_TO", pair[1], data != null && data > 0L, user);

            for (String[] d : List.of(new String[]{a, b}, new String[]{b, a})) {
                Long pw = (Long)this.jdbc
                    .queryForObject(
                        "SELECT COUNT(*) FROM cable WHERE status = 'CONNECTED' AND cable_type = 'POWER'\n  AND a_asset_no = ? AND b_asset_no = ?",
                        Long.class,
                        new Object[]{d[0], d[1]}
                    );
                this.upsertRel(d[0], "POWERED_BY", d[1], pw != null && pw > 0L, user);
            }
        }
    }

    private void upsertRel(String src, String rel, String dst, boolean want, String user) {
        if (want) {
            this.jdbc
                .update(
                    "INSERT INTO ci_relation (src_type, src_id, rel_type, dst_type, dst_id, created_by, remark)\nVALUES ('ASSET', ?, ?, 'ASSET', ?, ?, 'cable：线缆自动维护')\nON CONFLICT (src_type, src_id, rel_type, dst_type, dst_id) WHERE valid_to IS NULL DO NOTHING\n",
                    new Object[]{src, rel, dst, user}
                );
        } else {
            this.jdbc
                .update(
                    "UPDATE ci_relation SET valid_to = NOW()\nWHERE src_type = 'ASSET' AND src_id = ? AND rel_type = ? AND dst_type = 'ASSET' AND dst_id = ?\n  AND valid_to IS NULL AND remark LIKE 'cable%'\n",
                    new Object[]{src, rel, dst}
                );
        }
    }

    private static String blank(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("POWER", "电源线");
        m.put("COPPER", "网线（铜缆）");
        m.put("FIBER", "光纤");
        m.put("DAC", "DAC 高速线缆");
        m.put("AOC", "AOC 有源光缆");
        m.put("IB", "InfiniBand");
        m.put("OTHER", "其他");
        TYPES = Collections.unmodifiableMap(m);
    }

    public static record CableReq(
        String cableNo,
        String cableType,
        String aAssetNo,
        String aPort,
        String bAssetNo,
        String bPort,
        BigDecimal lengthM,
        String color,
        String speed,
        String status,
        String remark
    ) {
    }
}
