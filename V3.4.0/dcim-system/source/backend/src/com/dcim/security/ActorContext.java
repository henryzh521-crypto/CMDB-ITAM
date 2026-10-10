package com.dcim.security;

import com.dcim.common.BusinessException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ActorContext {
    private final LoginUserLoader userLoader;

    public ActorContext(LoginUserLoader userLoader) {
        this.userLoader = userLoader;
    }

    public <T> T runAs(String username, Supplier<T> body) {
        SecurityContext previous = SecurityContextHolder.getContext();
        // 修复：显式指定的操作人加载失败时快速失败并给出明确原因。
        // 原实现静默回退为 admin 执行，配置错误（如自动派单操作人被删除）会导致权限被静默放大。
        if (username != null && !username.isBlank()) {
            LoginUser specified = this.userLoader.load(username);
            if (specified == null) {
                throw BusinessException.badRequest("系统操作人不存在：" + username + "，请检查相关配置");
            }
            return this.runWithContext(specified, previous, body);
        }
        LoginUser u = this.userLoader.load("admin");
        if (u == null) {
            throw BusinessException.badRequest("无可用的系统操作人");
        }
        return this.runWithContext(u, previous, body);
    }

    private <T> T runWithContext(LoginUser u, SecurityContext previous, Supplier<T> body) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, List.of(new SimpleGrantedAuthority(u.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER")))
        );
        SecurityContextHolder.setContext(ctx);

        T result;
        try {
            result = body.get();
        } finally {
            SecurityContextHolder.setContext(previous);
        }

        return result;
    }
}
