package com.dcim.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientIp {
    // v3.5.2：默认信任面收敛为「回环 + Docker 默认网桥段」。此前默认包含全部 RFC1918
    // （10/8、192.168/16），内网客户端直连后端端口时可伪造 X-Forwarded-For 冒充任意来源 IP，
    // 绕过登录跨 IP 锁定（SE-06）与审计定位。原生部署且网关与后端同处 10/8、192.168/16
    // 网段时，需在配置中显式声明 dcim.trusted-proxies（见 .env.example 说明）。
    private static final String DEFAULT_TRUSTED = "127.0.0.0/8,::1/128,172.16.0.0/12";
    private final List<ClientIp.Net> trusted;

    public ClientIp(@Value("${dcim.trusted-proxies:127.0.0.0/8,::1/128,172.16.0.0/12}") String trustedProxies) {
        this.trusted = parse(trustedProxies);
    }

    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (remote != null && this.isTrusted(remote)) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] hops = xff.split(",");
                // v3.5.2：标准算法——从最右一跳向左跳过可信代理，取第一个非可信地址即真实客户端；
                // 客户端伪造的前缀条目天然被忽略。全链可信时退回最右一跳（最近的直接对端）。
                String rightmost = null;

                for (int i = hops.length - 1; i >= 0; i--) {
                    String hop = hops[i].trim();
                    if (!hop.isEmpty()) {
                        if (rightmost == null) {
                            rightmost = hop;
                        }
                        if (!this.isTrusted(hop)) {
                            return hop;
                        }
                    }
                }

                return rightmost != null ? rightmost : remote;
            } else {
                return remote;
            }
        } else {
            return remote;
        }
    }

    public boolean isTrustedPeer(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        return remote != null && this.isTrusted(remote);
    }

    private boolean isTrusted(String ip) {
        for (ClientIp.Net net : this.trusted) {
            if (net.contains(ip)) {
                return true;
            }
        }

        return false;
    }

    private static byte[] toBytes(String ip) {
        try {
            String v = ip.trim();
            if (v.startsWith("[")) {
                int end = v.indexOf(93);
                v = end > 0 ? v.substring(1, end) : v;
            }

            return !isLiteral(v) ? null : InetAddress.getByName(v).getAddress();
        } catch (UnknownHostException var3) {
            return null;
        }
    }

    private static boolean isLiteral(String v) {
        if (v.indexOf(58) >= 0) {
            return true;
        } else {
            int dots = 0;

            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (c == '.') {
                    dots++;
                } else if (c < '0' || c > '9') {
                    return false;
                }
            }

            return dots == 3;
        }
    }

    private static List<ClientIp.Net> parse(String csv) {
        if (csv != null && !csv.isBlank()) {
            List<ClientIp.Net> out = new ArrayList<>();

            for (String raw : csv.split(",")) {
                String entry = raw.trim();
                if (!entry.isEmpty()) {
                    String hostPart = entry;
                    int prefix = -1;
                    int slash = entry.lastIndexOf(47);
                    if (slash >= 0) {
                        hostPart = entry.substring(0, slash).trim();
                        String p = entry.substring(slash + 1).trim();

                        try {
                            prefix = Integer.parseInt(p);
                        } catch (NumberFormatException var12) {
                            throw new IllegalStateException("dcim.trusted-proxies 网段前缀非法：" + entry);
                        }
                    }

                    byte[] addr = toBytes(hostPart);
                    if (addr == null) {
                        throw new IllegalStateException("dcim.trusted-proxies 不是合法 IP 字面量：" + entry);
                    }

                    int max = addr.length * 8;
                    if (prefix < 0) {
                        prefix = max;
                    }

                    if (prefix > max) {
                        throw new IllegalStateException("dcim.trusted-proxies 前缀越界（/" + prefix + " 超过 " + addr.length * 8 + " 位）：" + entry);
                    }

                    out.add(new ClientIp.Net(addr, prefix));
                }
            }

            if (out.isEmpty()) {
                throw new IllegalStateException("dcim.trusted-proxies 未解析出任何网段：" + csv);
            } else {
                return List.copyOf(out);
            }
        } else {
            throw new IllegalStateException("dcim.trusted-proxies 不能为空（无可信代理时所有客户端都会被记成同一个地址）");
        }
    }

    private static record Net(byte[] addr, int prefix) {
        boolean contains(String ip) {
            byte[] a = ClientIp.toBytes(ip);
            if (a != null && a.length == this.addr.length) {
                int full = this.prefix / 8;

                for (int i = 0; i < full; i++) {
                    if (a[i] != this.addr[i]) {
                        return false;
                    }
                }

                int rem = this.prefix % 8;
                if (rem > 0) {
                    int mask = 255 << 8 - rem & 0xFF;
                    if ((a[full] & mask) != (this.addr[full] & mask)) {
                        return false;
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }
}
