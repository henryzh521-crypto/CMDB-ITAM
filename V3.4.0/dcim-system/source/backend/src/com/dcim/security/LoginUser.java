package com.dcim.security;

import java.util.List;
import java.util.Map;

/**
 * 当前登录用户快照（Redis 缓存 30 分钟，角色/权限变更时逐出）。
 *
 * <p>v3.3 新增：
 * <ul>
 *   <li>{@code department}：数据范围 DEPT 判定依据；</li>
 *   <li>{@code tokensValidAfter}：令牌吊销水位线（epoch 毫秒），签发早于该时刻的 access token 视为失效。</li>
 * </ul>
 * 旧缓存 JSON 缺少这两个字段时按 null / 0 反序列化，不影响升级。
 */
public record LoginUser(
    String username,
    String name,
    boolean mustChangePwd,
    List<String> roles,
    boolean isAdmin,
    Map<String, LoginUser.ModulePerm> modulePerms,
    Map<String, Map<String, Boolean>> actionPerms,
    Map<String, Map<String, Object>> dataScopes,
    Map<String, Map<String, Object>> fieldPerms,
    String department,
    long tokensValidAfter
) {
    /** 兼容 v3.2 的 9 参构造（系统内置执行身份等场景）。 */
    public LoginUser(
        String username,
        String name,
        boolean mustChangePwd,
        List<String> roles,
        boolean isAdmin,
        Map<String, LoginUser.ModulePerm> modulePerms,
        Map<String, Map<String, Boolean>> actionPerms,
        Map<String, Map<String, Object>> dataScopes,
        Map<String, Map<String, Object>> fieldPerms
    ) {
        this(username, name, mustChangePwd, roles, isAdmin, modulePerms, actionPerms, dataScopes, fieldPerms, null, 0L);
    }

    public static record ModulePerm(boolean read, boolean write) {
        public static LoginUser.ModulePerm of(boolean read, boolean write) {
            return new LoginUser.ModulePerm(read, write);
        }
    }
}
