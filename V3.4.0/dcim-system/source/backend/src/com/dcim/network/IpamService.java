package com.dcim.network;

import com.dcim.common.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IpamService {
    
    private static final Logger log = LoggerFactory.getLogger(IpamService.class);
    public static final Set<String> PURPOSES = Set.of("BIZ", "MGMT", "BMC", "STORAGE", "IB", "OTHER");
    private static final int MIN_MASK_V4 = 16;
    private final JdbcTemplate jdbc;

    public IpamService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String normalizePrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BusinessException.badRequest("请填写网段，如 10.10.0.0/24");
        } else if (!raw.contains("/")) {
            throw BusinessException.badRequest("网段须带掩码，如 10.10.0.0/24");
        } else {
            try {
                return (String)this.jdbc.queryForObject("SELECT network(?::inet)::cidr::text", String.class, new Object[]{raw.trim()});
            } catch (DataAccessException var3) {
                throw BusinessException.badRequest("网段格式不正确：" + raw);
            }
        }
    }

    public String normalizeIp(String raw) {
        if (raw != null && !raw.isBlank()) {
            String s = (String)this.jdbc.queryForObject("SELECT host(try_inet(?))", String.class, new Object[]{raw.trim()});
            if (s == null) {
                throw BusinessException.badRequest("IP 地址格式不正确：" + raw);
            } else {
                return s;
            }
        } else {
            throw BusinessException.badRequest("请填写 IP 地址");
        }
    }

    public List<Map<String, Object>> prefixes(String keyword, String roomCode, String purpose) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            String ip = (String)this.jdbc.queryForObject("SELECT host(try_inet(?))", String.class, new Object[]{kw});
            if (ip != null) {
                where.append(" AND p.prefix >>= ?::inet");
                args.add(ip);
            } else {
                where.append(" AND (p.name ILIKE ? OR p.prefix::text LIKE ? OR p.remark ILIKE ?)");
                String like = "%" + kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                args.add(like);
                args.add(like);
                args.add(like);
            }
        }

        if (roomCode != null && !roomCode.isBlank()) {
            where.append(" AND p.room_code = ?");
            args.add(roomCode.trim());
        }

        if (purpose != null && !purpose.isBlank()) {
            where.append(" AND p.purpose = ?");
            args.add(purpose.trim());
        }

        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "SELECT p.id, p.prefix::text AS prefix, p.name, p.purpose, p.room_code AS \"roomCode\",\n       host(p.gateway) AS gateway, p.dns, p.remark, p.vlan_ref AS \"vlanRef\",\n       v.vlan_id AS \"vlanId\", v.name AS \"vlanName\",\n       family(p.prefix) AS family, masklen(p.prefix) AS masklen,\n       (SELECT pp.prefix::text FROM ipam_prefix pp WHERE pp.prefix >> p.prefix\n        ORDER BY masklen(pp.prefix) DESC LIMIT 1) AS parent,\n       (SELECT COUNT(*) FROM ipam_address a WHERE a.ip << p.prefix) AS used,\n       (SELECT COUNT(*) FROM ipam_address a WHERE a.ip << p.prefix AND a.status = 'RESERVED') AS reserved\nFROM ipam_prefix p LEFT JOIN ipam_vlan v ON v.id = p.vlan_ref\n"
                    + where
                    + " ORDER BY p.prefix",
                args.toArray()
            );

        for (Map<String, Object> r : rows) {
            int fam = ((Number)r.get("family")).intValue();
            int mask = ((Number)r.get("masklen")).intValue();
            double size = usableSize(fam, mask);
            long used = ((Number)r.get("used")).longValue();
            r.put("size", size > 1.0E15 ? null : (long)size);
            r.put("usedPct", size <= 0.0 ? null : (double)Math.round((double)used * 1000.0 / size) / 10.0);
        }

        return rows;
    }

    static double usableSize(int family, int mask) {
        int bits = (family == 4 ? 32 : 128) - mask;
        double n = Math.pow(2.0, (double)bits);
        return family == 4 && bits >= 2 ? n - 2.0 : n;
    }

    @Transactional
    public long savePrefix(Long id, IpamService.PrefixReq r, String user) {
        String prefix = this.normalizePrefix(r.prefix());
        if (r.name() != null && !r.name().isBlank() && r.name().length() <= 100) {
            String purpose = r.purpose() != null && !r.purpose().isBlank() ? r.purpose().trim() : "BIZ";
            if (!PURPOSES.contains(purpose)) {
                throw BusinessException.badRequest("用途须为 " + String.join(" / ", PURPOSES));
            } else {
                String gw = null;
                if (r.gateway() != null && !r.gateway().isBlank()) {
                    gw = this.normalizeIp(r.gateway());
                    if (!Boolean.TRUE.equals(this.jdbc.queryForObject("SELECT ?::inet << ?::cidr", Boolean.class, new Object[]{gw, prefix}))) {
                        throw BusinessException.badRequest("网关 " + gw + " 不在网段 " + prefix + " 内");
                    }
                }

                if (r.vlanRef() != null
                    && Boolean.FALSE
                        .equals(this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM ipam_vlan WHERE id = ?)", Boolean.class, new Object[]{r.vlanRef()}))) {
                    throw BusinessException.badRequest("VLAN 不存在");
                } else {
                    try {
                        if (id == null) {
                            Long newId = (Long)this.jdbc
                                .queryForObject(
                                    "INSERT INTO ipam_prefix (prefix, name, purpose, vlan_ref, room_code, gateway, dns, remark, created_by)\nVALUES (?::cidr, ?, ?, ?, ?, ?::inet, ?, ?, ?) RETURNING id\n",
                                    Long.class,
                                    new Object[]{
                                        prefix, r.name().trim(), purpose, r.vlanRef(), blank(r.roomCode()), gw, blank(r.dns()), blank(r.remark()), user
                                    }
                                );
                            return newId == null ? 0L : newId;
                        } else {
                            int n = this.jdbc
                                .update(
                                    "UPDATE ipam_prefix SET prefix = ?::cidr, name = ?, purpose = ?, vlan_ref = ?, room_code = ?,\n       gateway = ?::inet, dns = ?, remark = ?, updated_at = NOW() WHERE id = ?\n",
                                    new Object[]{prefix, r.name().trim(), purpose, r.vlanRef(), blank(r.roomCode()), gw, blank(r.dns()), blank(r.remark()), id}
                                );
                            if (n == 0) {
                                throw BusinessException.notFound("网段不存在");
                            } else {
                                return id;
                            }
                        }
                    } catch (DuplicateKeyException var8) {
                        throw BusinessException.conflict("网段 " + prefix + " 已存在");
                    }
                }
            }
        } else {
            throw BusinessException.badRequest("请填写网段名称（不超过 100 字）");
        }
    }

    public void deletePrefix(long id) {
        Map<String, Object> p = (Map<String, Object>)this.jdbc
            .queryForList("SELECT prefix::text AS prefix FROM ipam_prefix WHERE id = ?", new Object[]{id})
            .stream()
            .findFirst()
            .orElseThrow(() -> BusinessException.notFound("网段不存在"));
        Long manual = (Long)this.jdbc
            .queryForObject("SELECT COUNT(*) FROM ipam_address WHERE ip << ?::cidr AND source = 'MANUAL'", Long.class, new Object[]{p.get("prefix")});
        if (manual != null && manual > 0L) {
            throw BusinessException.conflict("网段内还有 " + manual + " 条手工登记的地址（保留/DHCP 等），请先释放");
        } else {
            this.jdbc.update("DELETE FROM ipam_prefix WHERE id = ?", new Object[]{id});
        }
    }

    public Map<String, Object> addresses(long prefixId, String status, long page, long size) {
        String prefix = this.prefixText(prefixId);
        List<Object> args = new ArrayList<>();
        args.add(prefix);
        String st = "";
        if (status != null && !status.isBlank()) {
            st = " AND a.status = ?";
            args.add(status.trim());
        }

        Long total = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM ipam_address a WHERE a.ip << ?::cidr" + st, Long.class, args.toArray());
        args.add(Math.min(size, 500L));
        args.add((Math.max(page, 1L) - 1L) * Math.min(size, 500L));
        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "SELECT a.id, host(a.ip) AS ip, a.status, a.asset_no AS \"assetNo\", s.name AS \"assetName\",\n       s.rack_code AS \"rackCode\", a.ip_role AS \"ipRole\", a.hostname, a.source, a.remark,\n       a.updated_at AS \"updatedAt\"\nFROM ipam_address a LEFT JOIN asset s ON s.asset_no = a.asset_no\nWHERE a.ip << ?::cidr"
                    + st
                    + " ORDER BY a.ip LIMIT ? OFFSET ?",
                args.toArray()
            );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("records", rows);
        return out;
    }

    public List<String> nextFree(long prefixId, int count) {
        Map<String, Object> p = (Map<String, Object>)this.jdbc
            .queryForList(
                "SELECT prefix::text AS prefix, family(prefix) AS fam, masklen(prefix) AS mask, host(gateway) AS gw\nFROM ipam_prefix WHERE id = ?",
                new Object[]{prefixId}
            )
            .stream()
            .findFirst()
            .orElseThrow(() -> BusinessException.notFound("网段不存在"));
        int fam = ((Number)p.get("fam")).intValue();
        int mask = ((Number)p.get("mask")).intValue();
        if (fam != 4) {
            throw BusinessException.badRequest("IPv6 网段请手工指定地址");
        } else if (mask < 16) {
            throw BusinessException.badRequest("网段过大（/" + mask + "），请在其下划分子网段后再分配");
        } else {
            int bits = 32 - mask;
            long first = bits >= 2 ? 1L : 0L;
            long last = bits >= 2 ? (1L << bits) - 2L : (1L << bits) - 1L;
            int n = Math.max(1, Math.min(count, 256));
            return this.jdbc
                .queryForList(
                    "SELECT host(network(?::cidr) + g) FROM generate_series(?::bigint, ?::bigint) g\nWHERE NOT EXISTS (SELECT 1 FROM ipam_address a WHERE a.ip = host(network(?::cidr) + g)::inet)\n  AND NOT EXISTS (SELECT 1 FROM asset s WHERE s.deleted_at IS NULL\n                  AND (try_inet(s.biz_ip) = host(network(?::cidr) + g)::inet\n                       OR try_inet(s.mgmt_ip) = host(network(?::cidr) + g)::inet))\n  AND host(network(?::cidr) + g) IS DISTINCT FROM ?\nORDER BY g LIMIT ?\n",
                    String.class,
                    new Object[]{p.get("prefix"), first, last, p.get("prefix"), p.get("prefix"), p.get("prefix"), p.get("prefix"), p.get("gw"), n}
                );
        }
    }

    @Transactional
    public Map<String, Object> allocate(IpamService.AddressReq r, String user) {
        String ip = r.ip() != null && !r.ip().isBlank() ? this.normalizeIp(r.ip()) : null;
        if (ip == null) {
            if (r.prefixId() == null) {
                throw BusinessException.badRequest("请填写 IP，或选择网段自动分配");
            }

            // v3.5：先锁定网段行再 nextFree，串行化同一网段的自动分配，
            // 消除「查到空闲 IP 后、落库前被并发抢占」的 check-then-act 竞态（IP 重复分配）
            List<Map<String, Object>> locked = this.jdbc
                .queryForList("SELECT id FROM ipam_prefix WHERE id = ? FOR UPDATE", new Object[]{r.prefixId()});
            if (locked.isEmpty()) {
                throw BusinessException.notFound("网段不存在");
            }

            List<String> free = this.nextFree(r.prefixId(), 1);
            if (free.isEmpty()) {
                throw BusinessException.conflict("网段已无可用地址");
            }

            ip = free.get(0);
        }

        String status = r.status() != null && !r.status().isBlank() ? r.status().trim() : "USED";
        if (!Set.of("USED", "RESERVED", "DHCP").contains(status)) {
            throw BusinessException.badRequest("状态须为 USED / RESERVED / DHCP");
        } else {
            List<Map<String, Object>> exists = this.jdbc.queryForList("SELECT status, asset_no FROM ipam_address WHERE ip = ?::inet", new Object[]{ip});
            List<String> holders = this.assetsUsing(ip);
            String assetNo = blank(r.assetNo());
            if (assetNo != null && "USED".equals(status) && ("biz".equals(r.ipRole()) || "mgmt".equals(r.ipRole()))) {
                if (!holders.isEmpty() && (holders.size() != 1 || !holders.get(0).equals(assetNo))) {
                    throw BusinessException.conflict("IP " + ip + " 已被资产 " + String.join("、", holders) + " 使用");
                }

                if (!exists.isEmpty() && !"ASSET".equals(this.sourceOf(ip)) && !assetNo.equals(exists.get(0).get("asset_no"))) {
                    throw BusinessException.conflict("IP " + ip + " 已登记为 " + exists.get(0).get("status"));
                }

                String col = "biz".equals(r.ipRole()) ? "biz_ip" : "mgmt_ip";
                int n = this.jdbc
                    .update(
                        "UPDATE asset SET " + col + " = ?, updated_at = NOW(), version = version + 1 WHERE asset_no = ? AND deleted_at IS NULL",
                        new Object[]{ip, assetNo}
                    );
                if (n == 0) {
                    throw BusinessException.notFound("资产不存在：" + assetNo);
                }

                this.jdbc.update("DELETE FROM ipam_address WHERE ip = ?::inet AND source = 'MANUAL'", new Object[]{ip});
                this.syncAsset(assetNo);
                if (r.hostname() != null || r.remark() != null) {
                    this.jdbc
                        .update(
                            "UPDATE ipam_address SET hostname = COALESCE(?, hostname), remark = COALESCE(?, remark) WHERE ip = ?::inet",
                            new Object[]{blank(r.hostname()), blank(r.remark()), ip}
                        );
                }
            } else {
                if (!holders.isEmpty()) {
                    throw BusinessException.conflict("IP " + ip + " 已被资产 " + String.join("、", holders) + " 使用");
                }

                if (!exists.isEmpty()) {
                    throw BusinessException.conflict("IP " + ip + " 已登记（" + exists.get(0).get("status") + "）");
                }

                String role = r.ipRole() != null && !r.ipRole().isBlank() ? r.ipRole() : null;
                if (role != null && !Set.of("biz", "mgmt", "other").contains(role)) {
                    throw BusinessException.badRequest("IP 角色须为 biz / mgmt / other");
                }

                this.jdbc
                    .update(
                        "INSERT INTO ipam_address (ip, status, asset_no, ip_role, hostname, source, remark, created_by)\nVALUES (?::inet, ?, ?, ?, ?, 'MANUAL', ?, ?)\n",
                        new Object[]{ip, status, assetNo, role, blank(r.hostname()), blank(r.remark()), user}
                    );
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ip", ip);
            out.put("status", status);
            out.put("assetNo", assetNo);
            return out;
        }
    }

    @Transactional
    public void release(long id, boolean clearAsset) {
        Map<String, Object> a = (Map<String, Object>)this.jdbc
            .queryForList("SELECT host(ip) AS ip, source, asset_no, ip_role FROM ipam_address WHERE id = ?", new Object[]{id})
            .stream()
            .findFirst()
            .orElseThrow(() -> BusinessException.notFound("地址不存在"));
        if ("ASSET".equals(a.get("source"))) {
            if (!clearAsset) {
                throw BusinessException.conflict("该地址来自资产 " + a.get("asset_no") + " 的 IP 字段，释放将清空资产 IP，请确认");
            }

            String col = "biz".equals(a.get("ip_role")) ? "biz_ip" : "mgmt_ip";
            this.jdbc
                .update(
                    "UPDATE asset SET " + col + " = NULL, updated_at = NOW(), version = version + 1 WHERE asset_no = ? AND try_inet(" + col + ") = ?::inet",
                    new Object[]{a.get("asset_no"), a.get("ip")}
                );
        }

        this.jdbc.update("DELETE FROM ipam_address WHERE id = ?", new Object[]{id});
    }

    public void updateAddress(long id, IpamService.AddressReq r) {
        Map<String, Object> a = (Map<String, Object>)this.jdbc
            .queryForList("SELECT source FROM ipam_address WHERE id = ?", new Object[]{id})
            .stream()
            .findFirst()
            .orElseThrow(() -> BusinessException.notFound("地址不存在"));
        if ("ASSET".equals(a.get("source"))) {
            this.jdbc
                .update(
                    "UPDATE ipam_address SET hostname = ?, remark = ?, updated_at = NOW() WHERE id = ?",
                    new Object[]{blank(r.hostname()), blank(r.remark()), id}
                );
        } else {
            String status = r.status() != null && !r.status().isBlank() ? r.status().trim() : "RESERVED";
            if (!Set.of("USED", "RESERVED", "DHCP").contains(status)) {
                throw BusinessException.badRequest("状态须为 USED / RESERVED / DHCP");
            } else {
                this.jdbc
                    .update(
                        "UPDATE ipam_address SET status = ?, hostname = ?, remark = ?, updated_at = NOW() WHERE id = ?",
                        new Object[]{status, blank(r.hostname()), blank(r.remark()), id}
                    );
            }
        }
    }

    public Map<String, Object> lookup(String raw) {
        String ip = this.normalizeIp(raw);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ip", ip);
        out.put(
            "record",
            this.jdbc
                .queryForList(
                    "SELECT id, status, asset_no AS \"assetNo\", ip_role AS \"ipRole\", hostname, source, remark\nFROM ipam_address WHERE ip = ?::inet",
                    new Object[]{ip}
                )
                .stream()
                .findFirst()
                .orElse(null)
        );
        out.put(
            "assets",
            this.jdbc
                .queryForList(
                    "SELECT asset_no AS \"assetNo\", name, rack_code AS \"rackCode\", status_biz AS \"statusBiz\",\n       CASE WHEN try_inet(biz_ip) = ?::inet THEN 'biz' ELSE 'mgmt' END AS \"ipRole\"\nFROM asset WHERE deleted_at IS NULL AND (try_inet(biz_ip) = ?::inet OR try_inet(mgmt_ip) = ?::inet)\n",
                    new Object[]{ip, ip, ip}
                )
        );
        out.put(
            "prefixes",
            this.jdbc
                .queryForList(
                    "SELECT id, prefix::text AS prefix, name, purpose FROM ipam_prefix WHERE prefix >>= ?::inet\nORDER BY masklen(prefix) DESC",
                    new Object[]{ip}
                )
        );
        return out;
    }

    @Scheduled(
        initialDelayString = "${dcim.ipam.sync-initial-ms:120000}",
        fixedDelayString = "${dcim.ipam.sync-ms:600000}"
    )
    public void scheduledSync() {
        try {
            Map<String, Integer> r = this.syncAll();
            if (r.get("inserted") + r.get("updated") + r.get("deleted") > 0) {
                log.info("[ipam] 资产 IP 同步：{}", r);
            }
        } catch (RuntimeException var2) {
            log.warn("[ipam] 资产 IP 同步失败：{}", var2.getMessage());
        }
    }

    @Transactional
    public Map<String, Integer> syncAll() {
        String src = "SELECT DISTINCT ON (ip) ip, asset_no, ip_role FROM (\n    SELECT try_inet(biz_ip) AS ip, asset_no, 'biz' AS ip_role FROM asset WHERE deleted_at IS NULL\n    UNION ALL\n    SELECT try_inet(mgmt_ip), asset_no, 'mgmt' FROM asset WHERE deleted_at IS NULL\n) x WHERE ip IS NOT NULL ORDER BY ip, asset_no, ip_role\n";
        int deleted = this.jdbc
            .update(
                "DELETE FROM ipam_address d WHERE d.source = 'ASSET' AND NOT EXISTS (SELECT 1 FROM ("
                    + src
                    + ") s WHERE s.ip = d.ip AND s.asset_no = d.asset_no AND s.ip_role = d.ip_role)"
            );
        int updated = 0;
        int inserted = this.jdbc
            .update(
                "INSERT INTO ipam_address (ip, status, asset_no, ip_role, source) SELECT s.ip, 'USED', s.asset_no, s.ip_role, 'ASSET' FROM ("
                    + src
                    + ") s ON CONFLICT (ip) DO NOTHING"
            );
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("inserted", inserted);
        out.put("updated", updated);
        out.put("deleted", deleted);
        return out;
    }

    public void syncAsset(String assetNo) {
        this.jdbc.update("DELETE FROM ipam_address WHERE source = 'ASSET' AND asset_no = ?", new Object[]{assetNo});
        this.jdbc
            .update(
                "INSERT INTO ipam_address (ip, status, asset_no, ip_role, source)\nSELECT ip, 'USED', asset_no, ip_role, 'ASSET' FROM (\n    SELECT try_inet(biz_ip) AS ip, asset_no, 'biz' AS ip_role FROM asset WHERE asset_no = ? AND deleted_at IS NULL\n    UNION ALL\n    SELECT try_inet(mgmt_ip), asset_no, 'mgmt' FROM asset WHERE asset_no = ? AND deleted_at IS NULL\n) x WHERE ip IS NOT NULL\nON CONFLICT (ip) DO NOTHING\n",
                new Object[]{assetNo, assetNo}
            );
    }

    public Map<String, Object> conflicts() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
            "duplicates",
            this.jdbc
                .queryForList(
                    "SELECT host(ip) AS ip, COUNT(DISTINCT asset_no) AS n,\n       string_agg(DISTINCT asset_no || '(' || ip_role || ')', '、') AS assets\nFROM (SELECT try_inet(biz_ip) AS ip, asset_no, '业务' AS ip_role FROM asset WHERE deleted_at IS NULL\n      UNION ALL\n      SELECT try_inet(mgmt_ip), asset_no, '管理' FROM asset WHERE deleted_at IS NULL) x\nWHERE ip IS NOT NULL GROUP BY ip HAVING COUNT(*) > 1 ORDER BY ip LIMIT 500\n"
                )
        );
        out.put(
            "invalid",
            this.jdbc
                .queryForList(
                    "SELECT asset_no AS \"assetNo\", name, biz_ip AS \"bizIp\", mgmt_ip AS \"mgmtIp\" FROM asset\nWHERE deleted_at IS NULL AND (\n      (NULLIF(btrim(biz_ip), '') IS NOT NULL AND try_inet(biz_ip) IS NULL)\n   OR (NULLIF(btrim(mgmt_ip), '') IS NOT NULL AND try_inet(mgmt_ip) IS NULL))\nORDER BY asset_no LIMIT 500\n"
                )
        );
        boolean hasPrefix = Boolean.TRUE.equals(this.jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM ipam_prefix)", Boolean.class));
        out.put(
            "outsidePrefix",
            hasPrefix
                ? this.jdbc
                    .queryForList(
                        "SELECT host(a.ip) AS ip, a.asset_no AS \"assetNo\", a.ip_role AS \"ipRole\"\nFROM ipam_address a WHERE NOT EXISTS (SELECT 1 FROM ipam_prefix p WHERE p.prefix >>= a.ip)\nORDER BY a.ip LIMIT 500\n"
                    )
                : List.of()
        );
        out.put(
            "reservedInUse",
            this.jdbc
                .queryForList(
                    "SELECT host(d.ip) AS ip, d.status, d.remark,\n       string_agg(s.asset_no, '、') AS assets\nFROM ipam_address d JOIN asset s ON s.deleted_at IS NULL\n     AND (try_inet(s.biz_ip) = d.ip OR try_inet(s.mgmt_ip) = d.ip)\nWHERE d.source = 'MANUAL' AND d.status IN ('RESERVED', 'DHCP')\nGROUP BY d.ip, d.status, d.remark ORDER BY d.ip LIMIT 500\n"
                )
        );
        out.put("prefixConfigured", hasPrefix);
        return out;
    }

    public List<Map<String, Object>> vlans(String roomCode) {
        List<Object> args = new ArrayList<>();
        String w = "";
        if (roomCode != null && !roomCode.isBlank()) {
            w = " WHERE v.room_code = ? OR v.room_code IS NULL";
            args.add(roomCode.trim());
        }

        return this.jdbc
            .queryForList(
                "SELECT v.id, v.vlan_id AS \"vlanId\", v.name, v.room_code AS \"roomCode\", v.purpose, v.remark,\n       (SELECT string_agg(p.prefix::text, ', ' ORDER BY p.prefix) FROM ipam_prefix p WHERE p.vlan_ref = v.id) AS prefixes\nFROM ipam_vlan v"
                    + w
                    + " ORDER BY v.vlan_id, v.room_code NULLS FIRST",
                args.toArray()
            );
    }

    public void saveVlan(Long id, IpamService.VlanReq r) {
        if (r.vlanId() == null || r.vlanId() < 1 || r.vlanId() > 4094) {
            throw BusinessException.badRequest("VLAN ID 须在 1-4094");
        } else if (r.name() != null && !r.name().isBlank() && r.name().length() <= 100) {
            try {
                if (id == null) {
                    this.jdbc
                        .update(
                            "INSERT INTO ipam_vlan (vlan_id, name, room_code, purpose, remark) VALUES (?, ?, ?, ?, ?)",
                            new Object[]{r.vlanId(), r.name().trim(), blank(r.roomCode()), blank(r.purpose()), blank(r.remark())}
                        );
                } else if (this.jdbc
                        .update(
                            "UPDATE ipam_vlan SET vlan_id = ?, name = ?, room_code = ?, purpose = ?, remark = ?, updated_at = NOW() WHERE id = ?",
                            new Object[]{r.vlanId(), r.name().trim(), blank(r.roomCode()), blank(r.purpose()), blank(r.remark()), id}
                        )
                    == 0) {
                    throw BusinessException.notFound("VLAN 不存在");
                }
            } catch (DuplicateKeyException var4) {
                throw BusinessException.conflict("VLAN " + r.vlanId() + " 在该机房已存在");
            }
        } else {
            throw BusinessException.badRequest("请填写 VLAN 名称");
        }
    }

    public void deleteVlan(long id) {
        this.jdbc.update("DELETE FROM ipam_vlan WHERE id = ?", new Object[]{id});
    }

    private String prefixText(long id) {
        return (String)this.jdbc
            .queryForList("SELECT prefix::text FROM ipam_prefix WHERE id = ?", String.class, new Object[]{id})
            .stream()
            .findFirst()
            .orElseThrow(() -> BusinessException.notFound("网段不存在"));
    }

    private List<String> assetsUsing(String ip) {
        return this.jdbc
            .queryForList(
                "SELECT asset_no FROM asset WHERE deleted_at IS NULL AND (try_inet(biz_ip) = ?::inet OR try_inet(mgmt_ip) = ?::inet) ORDER BY asset_no",
                String.class,
                new Object[]{ip, ip}
            );
    }

    private String sourceOf(String ip) {
        return (String)this.jdbc
            .queryForList("SELECT source FROM ipam_address WHERE ip = ?::inet", String.class, new Object[]{ip})
            .stream()
            .findFirst()
            .orElse(null);
    }

    private static String blank(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    public static record AddressReq(String ip, Long prefixId, String status, String assetNo, String ipRole, String hostname, String remark) {
    }

    public static record PrefixReq(String prefix, String name, String purpose, Long vlanRef, String roomCode, String gateway, String dns, String remark) {
    }

    public static record VlanReq(Integer vlanId, String name, String roomCode, String purpose, String remark) {
    }
}
