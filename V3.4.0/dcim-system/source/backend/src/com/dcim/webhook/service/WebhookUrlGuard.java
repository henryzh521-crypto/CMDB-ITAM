package com.dcim.webhook.service;

import com.dcim.common.BusinessException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookUrlGuard {
    private static final Set<String> BLOCKED_HOSTS = Set.of(
        "localhost",
        "localhost.localdomain",
        "ip6-localhost",
        "metadata",
        "metadata.google.internal",
        "postgres",
        "redis",
        "minio",
        "tdengine",
        "platform-be",
        "platform-web",
        "caddy",
        "backup",
        "collector-agent",
        "dcim-postgres",
        "dcim-redis",
        "dcim-minio",
        "dcim-tdengine",
        "dcim-platform-be",
        "dcim-platform-web",
        "dcim-caddy",
        "dcim-backup",
        "dcim-collector-agent",
        // v3.2 新增的监控栈容器：Prometheus 开了 --web.enable-lifecycle（POST /-/quit 可直接停机），
        // Alertmanager POST /api/v2/silences 可静默全部告警，必须禁止作为 Webhook 目标
        "prometheus",
        "alertmanager",
        "blackbox-exporter",
        "ipmi-exporter",
        "snmp-exporter",
        "grafana",
        "tdengine-init",
        "dcim-prometheus",
        "dcim-alertmanager",
        "dcim-blackbox",
        "dcim-ipmi-exporter",
        "dcim-snmp-exporter",
        "dcim-grafana",
        "dcim-tdengine-init",
        "host.docker.internal",
        "gateway.docker.internal"
    );
    private final boolean allowPrivate;
    private final boolean allowLoopback;
    private final Set<String> extraBlocked;
    /** 本机网卡所在网段（容器部署时即 dcim-network 网段），按 IP 直连其它容器同样禁止 */
    private final java.util.List<byte[][]> localSubnets;

    public WebhookUrlGuard(
        @Value("${dcim.webhook.allow-private-network:true}") boolean allowPrivate,
        @Value("${dcim.webhook.blocked-hosts:}") String extraBlocked,
        @Value("${dcim.webhook.test-allow-loopback:false}") boolean allowLoopback,
        // 修复：代码默认值此前为 false，原生部署漏配即失效（仅 Compose 注入 true）。该开关语义是
        //「禁止推送平台所在网段」，默认开启是安全底线；仅当原生部署且平台与目标系统（如企业内网
        // OA）确实同网段、并接受相应风险时，才应显式配置为 false 放开。
        @Value("${dcim.webhook.block-local-subnets:true}") boolean blockLocalSubnets
    ) {
        this.localSubnets = blockLocalSubnets ? localInterfaceSubnets() : java.util.List.of();
        this.allowPrivate = allowPrivate;
        this.allowLoopback = allowLoopback;
        this.extraBlocked = Arrays.stream(extraBlocked.split(","))
            .map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public URI check(String rawUrl) {
        if (rawUrl != null && !rawUrl.isBlank()) {
            if (rawUrl.length() > 500) {
                throw BusinessException.badRequest("Webhook 地址过长（上限 500 字符）");
            } else {
                URI uri;
                try {
                    uri = URI.create(rawUrl.trim());
                } catch (IllegalArgumentException var13) {
                    throw BusinessException.badRequest("Webhook 地址格式不正确");
                }

                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                if (!scheme.equals("http") && !scheme.equals("https")) {
                    throw BusinessException.badRequest("Webhook 地址仅支持 http/https");
                } else if (uri.getRawUserInfo() != null) {
                    throw BusinessException.badRequest("Webhook 地址不允许内嵌账号口令，请改用签名密钥");
                } else {
                    String host = uri.getHost();
                    if (host != null && !host.isBlank()) {
                        String h = IDN.toASCII(host.replace("[", "").replace("]", "")).toLowerCase(Locale.ROOT);
                        if (h.endsWith(".")) {
                            h = h.substring(0, h.length() - 1);
                        }
                        if (isMalformedHost(h)) {
                            throw BusinessException.badRequest("Webhook 目标主机名不合法：" + host);
                        }

                        // 修复：test-allow-loopback 开关此前用 OR 绑定整个黑名单检查，误配为 true 时
                        // prometheus / alertmanager / minio 等全部内网主机被放行。现在该开关只对回环目标生效
                        boolean blockedHost = BLOCKED_HOSTS.contains(h) || h.endsWith(".localhost");
                        boolean hostAllowed = this.extraBlocked.contains(h)
                            ? false
                            : this.allowLoopback ? (!blockedHost || isLoopbackHost(h)) : !blockedHost;

                        if (hostAllowed) {
                            InetAddress[] addrs;
                            try {
                                addrs = InetAddress.getAllByName(h);
                            } catch (UnknownHostException var12) {
                                throw BusinessException.badRequest("Webhook 目标主机无法解析：" + host);
                            }

                            for (InetAddress a : addrs) {
                                String reason = this.forbiddenReason(a);
                                if (reason != null) {
                                    throw BusinessException.badRequest("Webhook 目标地址不允许（" + reason + "）：" + a.getHostAddress());
                                }
                            }

                            // 注意：返回的 URI 仍含主机名。本方法解析校验过的 IP 与投递方实际连接的 IP
                            // 之间存在 DNS rebinding TOCTOU 残余风险（HttpClient 建连时会做第二次独立解析），
                            // 因此投递前必须紧邻 send 重新走一次本方法（见 WebhookService.send 注释）。
                            return uri;
                        } else {
                            throw BusinessException.badRequest("Webhook 目标主机不允许：" + host);
                        }
                    } else {
                        throw BusinessException.badRequest("Webhook 地址缺少主机名");
                    }
                }
            }
        } else {
            throw BusinessException.badRequest("Webhook 地址不能为空");
        }
    }

    private static boolean isMalformedHost(String h) {
        if (h == null || h.isBlank() || h.startsWith(".") || h.endsWith(".") || h.contains("..") || h.indexOf('_') >= 0) {
            return true;
        }
        int dotCount = 0;
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c == '.') {
                dotCount++;
            }
            if (!(Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == ':')) {
                return true;
            }
        }
        return dotCount > 127;
    }

    /** 判断主机名是否为回环目标（test-allow-loopback 仅对这类目标放行） */
    private static boolean isLoopbackHost(String h) {
        return "localhost".equals(h) || h.endsWith(".localhost") || h.startsWith("127.") || "::1".equals(h) || "0:0:0:0:0:0:0:1".equals(h);
    }

    /** 枚举本机各网卡的 IPv4/IPv6 网段（地址 + 掩码），用于识别“同一容器网络”的目标。 */
    private static java.util.List<byte[][]> localInterfaceSubnets() {
        java.util.List<byte[][]> out = new java.util.ArrayList<>();
        try {
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (java.net.InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    int prefix = ia.getNetworkPrefixLength();
                    if (addr == null || addr.isLinkLocalAddress() || prefix <= 0) {
                        continue;
                    }
                    byte[] a = addr.getAddress();
                    byte[] mask = new byte[a.length];
                    for (int i = 0; i < prefix && i < a.length * 8; i++) {
                        mask[i / 8] |= (byte) (0x80 >>> (i % 8));
                    }
                    out.add(new byte[][]{a, mask});
                }
            }
        } catch (Exception e) {
            // 枚举失败不影响其余校验
        }
        return java.util.List.copyOf(out);
    }

    private boolean inLocalSubnet(InetAddress a) {
        byte[] b = a.getAddress();
        for (byte[][] net : this.localSubnets) {
            if (net[0].length != b.length) {
                continue;
            }
            boolean match = true;
            for (int i = 0; i < b.length; i++) {
                if ((b[i] & net[1][i]) != (net[0][i] & net[1][i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    String forbiddenReason(InetAddress a) {
        if (!this.localSubnets.isEmpty() && this.inLocalSubnet(a)) {
            return "平台自身所在网段（容器内网）";
        }
        if (a.isLoopbackAddress()) {
            return this.allowLoopback ? null : "回环地址";
        } else if (a.isAnyLocalAddress()) {
            return "未指定地址";
        } else if (a.isLinkLocalAddress()) {
            return "链路本地/云元数据地址";
        } else if (a.isMulticastAddress()) {
            return "组播地址";
        } else {
            byte[] b = a.getAddress();
            if (a instanceof Inet4Address) {
                int b0 = b[0] & 255;
                int b1 = b[1] & 255;
                if (b0 == 0 || b0 >= 240) {
                    return "保留地址";
                }

                if (b0 == 100 && b1 >= 64 && b1 <= 127) {
                    return "运营商级 NAT 地址";
                }

                boolean priv = b0 == 10 || b0 == 172 && b1 >= 16 && b1 <= 31 || b0 == 192 && b1 == 168;
                if (priv && !this.allowPrivate) {
                    return "内网地址（已禁用 dcim.webhook.allow-private-network）";
                }
            } else if (a instanceof Inet6Address) {
                int b0x = b[0] & 255;
                if ((b0x & 254) == 252 && !this.allowPrivate) {
                    return "IPv6 ULA 内网地址";
                }

                boolean mapped = true;

                for (int i = 0; i < 10; i++) {
                    if (b[i] != 0) {
                        mapped = false;
                        break;
                    }
                }

                if (mapped && (b[10] & 255) == 255 && (b[11] & 255) == 255) {
                    try {
                        return this.forbiddenReason(InetAddress.getByAddress(Arrays.copyOfRange(b, 12, 16)));
                    } catch (UnknownHostException var6) {
                        return "非法地址";
                    }
                }
            }

            return null;
        }
    }
}
