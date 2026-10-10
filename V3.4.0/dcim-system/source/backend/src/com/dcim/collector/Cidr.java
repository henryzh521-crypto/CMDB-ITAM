package com.dcim.collector;

import com.dcim.common.BusinessException;
import java.util.regex.Pattern;

public final class Cidr {
    private static final Pattern P = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)/(\\d{1,2})$");

    private Cidr() {
    }

    public static void validate(String cidr) {
        String v = cidr == null ? "" : cidr.trim();
        if (!P.matcher(v).matches()) {
            throw BusinessException.badRequest("CIDR 格式无效：" + cidr + "（示例 10.0.0.0/24）");
        } else {
            int prefix = Integer.parseInt(v.substring(v.indexOf(47) + 1));
            if (prefix >= 24 && prefix <= 32) {
                String netAddr = v.substring(0, v.indexOf(47));
                if (BmcIp.isBlocked(netAddr)) {
                    throw BusinessException.badRequest("CIDR 落在保留地址段（0/8、127/8、169.254/16、224/4 及以上），不允许作为发现网段：" + v);
                }
            } else {
                throw BusinessException.badRequest("CIDR 网段过大，仅支持 /24 至 /32（发现任务按小网段分批执行）");
            }
        }
    }
}
