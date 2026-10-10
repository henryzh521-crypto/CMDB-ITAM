package com.dcim.collector;

import com.dcim.common.BusinessException;
import java.util.regex.Pattern;

public final class BmcIp {
    private static final Pattern IPV4 = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    private BmcIp() {
    }

    public static String validate(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) {
            throw BusinessException.badRequest("bmcIp 不能为空");
        } else if (!IPV4.matcher(v).matches()) {
            throw BusinessException.badRequest("bmcIp 必须是 IPv4 地址（不接受主机名，防止 DNS 重绑定）：" + raw);
        } else if (isBlocked(v)) {
            throw BusinessException.badRequest("bmcIp 不允许指向保留地址段（0/8、127/8、169.254/16、224/4 及以上）：" + v);
        } else {
            return v;
        }
    }

    public static boolean isBlocked(String ipv4) {
        String[] parts = ipv4.split("\\.");
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        return a == 0 || a == 127 || a == 169 && b == 254 || a >= 224;
    }
}
