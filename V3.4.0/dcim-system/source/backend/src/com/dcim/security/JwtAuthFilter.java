package com.dcim.security;

import com.dcim.apitoken.ApiTokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final Set<String> MUST_CHANGE_PWD_ALLOW_PREFIX = Set.of("/api/auth/", "/api/healthz", "/api/collector/");
    private static final Set<String> MUST_CHANGE_PWD_ALLOW_EXACT = Set.of("/api/auth/me");
    private final JwtService jwtService;
    private final LoginUserLoader loginUserLoader;
    private final ApiTokenService apiTokens;
    private final ClientIp clientIp;
    private final boolean enforceMustChangePwd;
    private final TokenRevocationService revocation;

    public JwtAuthFilter(
        JwtService jwtService,
        LoginUserLoader loginUserLoader,
        ApiTokenService apiTokens,
        ClientIp clientIp,
        @Value("${dcim.auth.enforce-must-change-pwd:true}") boolean enforceMustChangePwd,
        TokenRevocationService revocation
    ) {
        this.revocation = revocation;
        this.jwtService = jwtService;
        this.loginUserLoader = loginUserLoader;
        this.apiTokens = apiTokens;
        this.clientIp = clientIp;
        this.enforceMustChangePwd = enforceMustChangePwd;
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = this.resolveToken(request);
        if (ApiTokenService.looksLikePat(token)) {
            if (!this.authenticatePat(token, request, response)) {
                return;
            }
        } else if (token != null) {
            try {
                Claims claims = this.jwtService.parse(token);
                LoginUser user = this.loginUserLoader.load(claims.getSubject());
                if (user != null && (JwtService.issuedAtMillis(claims) < user.tokensValidAfter() || this.revocation.isRevoked(claims.getId()))) {
                    // 令牌签发早于吊销水位线（已登出 / 改密 / 被重置或停用）：按未登录处理
                    log.debug("JWT revoked for {}", claims.getSubject());
                    user = null;
                }

                if (user != null) {
                    if (this.enforceMustChangePwd && user.mustChangePwd() && !this.allowWhileMustChangePwd(request)) {
                        SecurityContextHolder.clearContext();
                        this.writeMustChangePwd(response);
                        return;
                    }

                    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER"));
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (IllegalArgumentException | JwtException var13) {
                log.debug("JWT parse failed: {}", var13.getMessage());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean authenticatePat(String token, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!path.startsWith("/api/tokens") && !path.startsWith("/api/auth/") && (!path.startsWith("/api/users/") || !path.endsWith("/password"))) {
            ApiTokenService.Principal p = this.apiTokens.verify(token, this.clientIp.resolve(request));
            if (p == null) {
                return true;
            } else if (p.readOnly() && !"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method)) {
                this.writeForbidden(response, "只读令牌不能执行写操作");
                return false;
            } else {
                LoginUser user = this.loginUserLoader.load(p.username());
                if (user != null && (!this.enforceMustChangePwd || !user.mustChangePwd())) {
                    List<GrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority(user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER"), new SimpleGrantedAuthority("AUTH_PAT")
                    );
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
                    return true;
                } else {
                    this.writeForbidden(response, "令牌所属账号不可用（已停用或需先修改初始密码）");
                    return false;
                }
            }
        } else {
            this.writeForbidden(response, "访问令牌不能用于登录、令牌管理或修改口令");
            return false;
        }
    }

    private void writeForbidden(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":403,\"msg\":\"" + msg + "\"}");
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        } else {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("access_token".equals(cookie.getName())) {
                        return cookie.getValue();
                    }
                }
            }

            // v3.5：EventSource 无法携带 Authorization 头，仅 SSE 端点额外接受 ?token= 查询参数。
            // 令牌进 URL 有泄露面（访问日志 / 浏览器历史），故严格限定路径，且网关层对该
            // 路径的访问日志已去除 query（见 deploy/Caddyfile 与 UPGRADE-3.5 文档）。
            if (this.isSseEndpoint(request)) {
                String queryToken = request.getParameter("token");
                if (queryToken != null && !queryToken.isBlank()) {
                    return queryToken;
                }
            }

            return null;
        }
    }

    private boolean isSseEndpoint(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/api/notifications/sse".equals(path) || "/api/v1/notifications/sse".equals(path);
    }

    private boolean allowWhileMustChangePwd(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (MUST_CHANGE_PWD_ALLOW_EXACT.contains(path)) {
            return true;
        } else if (path.startsWith("/api/users/") && path.endsWith("/password")) {
            return "PUT".equalsIgnoreCase(request.getMethod());
        } else {
            for (String prefix : MUST_CHANGE_PWD_ALLOW_PREFIX) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }

            return false;
        }
    }

    private void writeMustChangePwd(HttpServletResponse response) throws IOException {
        response.setStatus(403);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":403,\"msg\":\"请先修改初始密码\"}");
    }
}
