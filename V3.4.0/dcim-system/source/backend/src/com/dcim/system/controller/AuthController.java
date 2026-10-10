package com.dcim.system.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.security.AuthCookies;
import com.dcim.security.ClientIp;
import com.dcim.security.CurrentUser;
import com.dcim.security.JwtService;
import com.dcim.security.LoginThrottle;
import com.dcim.security.LoginUser;
import com.dcim.security.LoginUserLoader;
import com.dcim.security.TokenRevocationService;
import com.dcim.security.TokenStore;
import com.dcim.security.ldap.LdapAuthService;
import com.dcim.security.ldap.LdapUserProvisioner;
import com.dcim.system.audit.AuditService;
import com.dcim.system.audit.AuditThrottle;
import com.dcim.system.entity.UserEntity;
import com.dcim.system.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth"})
@Validated
public class AuthController {
    private final UserMapper userMapper;
    private final LoginUserLoader loginUserLoader;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final AuditService auditService;
    private final AuditThrottle auditThrottle;
    private final LoginThrottle loginThrottle;
    private final PasswordEncoder passwordEncoder;
    private final LdapAuthService ldap;
    private final LdapUserProvisioner ldapProvisioner;
    private final ClientIp clientIpResolver;
    private final AuthCookies authCookies;
    private final TokenRevocationService revocation;
    @Value("${dcim.jwt.refresh-ttl-hours}")
    private long refreshTtlHours;
    @Value("${dcim.jwt.access-ttl-minutes}")
    private long accessTtlMinutes;
    @Value("${dcim.audit.login-fail-user-per-minute:5}")
    private int loginFailUserPerMinute;
    @Value("${dcim.audit.login-fail-ip-per-minute:30}")
    private int loginFailIpPerMinute;
    @Value("${dcim.auth.login-max-fail-per-account:10}")
    private int loginMaxFailPerAccount;
    @Value("${dcim.auth.login-max-fail-per-ip:30}")
    private int loginMaxFailPerIp;
    @Value("${dcim.auth.login-fail-window-minutes:15}")
    private int loginFailWindowMinutes;
    /** v3.4：账号级（跨 IP）失败上限，达到即锁定；可信 IP 不受影响 */
    @Value("${dcim.auth.login-max-fail-per-account-global:30}")
    private int loginMaxFailPerAccountGlobal;
    @Value("${dcim.auth.login-account-lock-minutes:15}")
    private int loginAccountLockMinutes;
    @Value("${dcim.auth.login-trusted-ip-days:30}")
    private int loginTrustedIpDays;

    @PostMapping({"/login"})
    public ApiResult<Map<String, Object>> login(@RequestBody @Valid AuthController.LoginReq req, HttpServletRequest request, HttpServletResponse response) {
        String ip = this.clientIp(request);
        Duration blockWindow = Duration.ofMinutes((long)Math.max(1, this.loginFailWindowMinutes));
        String ipKey = "login:fail:ip:" + ip;
        String acctKey = "login:fail:acct:" + req.username() + ":ip:" + ip;
        boolean trusted = this.loginThrottle.isTrusted(req.username(), ip);
        if (!trusted && this.loginThrottle.isAccountLocked(req.username(), this.loginMaxFailPerAccountGlobal, blockWindow)) {
            this.auditService.record(req.username(), "LOGIN_BLOCKED", "auth", "user", req.username(), Map.of("result", "拒绝", "reason", "账号已临时锁定"), ip);
            throw BusinessException.tooManyRequests("登录尝试次数过多，请稍后再试");
        }
        if (!this.loginThrottle.isBlocked(ipKey, this.loginMaxFailPerIp, blockWindow)
            && !this.loginThrottle.isBlocked(acctKey, this.loginMaxFailPerAccount, blockWindow)) {
            UserEntity user = this.selectByUsername(req.username());
            boolean ok;
            if (user != null && "LDAP".equals(user.getAuthSource())) {
                LdapAuthService.DirUser d = user.getStatus() != null && user.getStatus() == 1 ? this.ldap.authenticate(req.username(), req.password()) : null;
                ok = d != null;
                if (ok) {
                    user = this.ldapProvisioner.sync(user, d);
                }
            } else if (user != null) {
                // 先比对口令再判断状态：停用账号与正常账号耗时一致，避免借响应时间探测账号状态
                boolean pwOk = user.getPasswordHash() != null && this.passwordEncoder.matches(req.password(), user.getPasswordHash());
                ok = pwOk && user.getStatus() != null && user.getStatus() == 1;
            } else if (this.ldap.enabled()) {
                LdapAuthService.DirUser d = this.ldap.authenticate(req.username(), req.password());
                ok = d != null;
                if (ok) {
                    user = this.ldapProvisioner.sync(null, d);
                }
            } else {
                // 账号不存在也执行一次同成本的 bcrypt：消除“存在 ~90ms / 不存在 ~5ms”的用户名枚举时序差
                this.passwordEncoder.matches(req.password(), this.dummyHash());
                ok = false;
            }

            if (ok) {
                this.loginThrottle.reset(acctKey);
                this.loginThrottle.trust(user.getUsername(), ip, Duration.ofDays(Math.max(1, this.loginTrustedIpDays)));
                LoginUser lu = this.loginUserLoader.load(user.getUsername());
                Map<String, Object> data = this.issueTokens(user.getUsername(), user.getName(), lu, request, response);
                this.userMapper
                    .update(
                        null,
                        (Wrappers.<UserEntity>lambdaUpdate().eq(UserEntity::getId, user.getId()))
                            .set(UserEntity::getLastLoginAt, LocalDateTime.now())
                    );
                this.auditService.record(user.getUsername(), "LOGIN", "auth", "user", user.getUsername(), Map.of("result", "成功"), ip);
                return ApiResult.ok(data);
            } else {
                this.loginThrottle.recordFailure(ipKey, blockWindow);
                this.loginThrottle.recordFailure(acctKey, blockWindow);
                long acctFails = this.loginThrottle.recordAccountFailure(
                    req.username(), blockWindow, this.loginMaxFailPerAccountGlobal, Duration.ofMinutes(Math.max(1, this.loginAccountLockMinutes))
                );
                if (acctFails == this.loginMaxFailPerAccountGlobal && user != null) {
                    this.auditService.record(req.username(), "ACCOUNT_LOCKED", "auth", "user", req.username(),
                        Map.of("result", "锁定", "reason", "跨 IP 登录失败达到 " + acctFails + " 次", "minutes", this.loginAccountLockMinutes), ip);
                }
                boolean allowWrite = this.auditThrottle.allow("login-fail:user:" + req.username(), this.loginFailUserPerMinute, Duration.ofMinutes(1L))
                    && this.auditThrottle.allow("login-fail:ip:" + ip, this.loginFailIpPerMinute, Duration.ofMinutes(1L));
                if (allowWrite) {
                    this.auditService.record(req.username(), "LOGIN_FAIL", "auth", "user", req.username(), Map.of("result", "失败"), ip);
                }

                throw BusinessException.unauthorized("用户名或密码错误");
            }
        } else {
            this.auditService.record(req.username(), "LOGIN_BLOCKED", "auth", "user", req.username(), Map.of("result", "拒绝", "reason", "失败次数超限"), ip);
            throw BusinessException.tooManyRequests("登录尝试次数过多，请稍后再试");
        }
    }

    @PostMapping({"/refresh"})
    public ApiResult<Map<String, Object>> refresh(
        @RequestBody(required = false) @Valid AuthController.RefreshReq req, HttpServletRequest request, HttpServletResponse response
    ) {
        String refreshToken = this.authCookies.read(request, "refresh_token");
        if (refreshToken == null && req != null) {
            refreshToken = req.refreshToken();
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            // 原子消费：同一 refresh token 只能换发一次（并发重放只有一个请求成功）
            java.util.Optional<String> consumed = this.tokenStore.consume(refreshToken);
            if (consumed.isEmpty()) {
                if (this.tokenStore.recentlyRotated(refreshToken)) {
                    // 并发续期（多个标签页同时 401）：另一个请求已完成换发并写入 Cookie，这里不再签发，客户端直接重试即可
                    return ApiResult.ok(Map.of("rotated", true));
                }
                throw BusinessException.unauthorized("登录已过期，请重新登录");
            }
            String username = consumed.get();
            UserEntity user = this.selectByUsername(username);
            if (user != null && user.getStatus() != null && user.getStatus() == 1) {
                LoginUser lu = this.loginUserLoader.load(username);
                return ApiResult.ok(this.issueTokens(username, user.getName(), lu, request, response));
            } else {
                throw BusinessException.unauthorized("登录已过期，请重新登录");
            }
        } else {
            throw BusinessException.unauthorized("登录已过期，请重新登录");
        }
    }

    @PostMapping({"/logout"})
    public ApiResult<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        LoginUser lu = CurrentUser.get();
        if (lu != null) {
            // 登出只吊销当前终端：当前 access token 进 jti 黑名单 + 删除当前 refresh token
            String access = this.bearerOrCookie(request);
            String jti = null;
            long exp = 0L;
            if (access != null) {
                try {
                    io.jsonwebtoken.Claims c = this.jwtService.parse(access);
                    jti = c.getId();
                    exp = c.getExpiration() == null ? 0L : c.getExpiration().getTime();
                } catch (RuntimeException ignored) {
                    // 令牌已不可解析：无需加入黑名单
                }
            }
            this.revocation.revokeCurrent(jti, exp, this.authCookies.read(request, "refresh_token"));
            this.auditService.record(lu.username(), "LOGOUT", "auth", "user", lu.username(), Map.of("result", "成功"), this.clientIp(request));
        }

        this.authCookies.clear(request, response);
        return ApiResult.ok();
    }

    @GetMapping({"/me"})
    public ApiResult<Map<String, Object>> me() {
        LoginUser lu = CurrentUser.get();
        Map<String, Object> modules = new LinkedHashMap<>();
        lu.modulePerms().forEach((k, v) -> modules.put(k, Map.of("read", v.read(), "write", v.write())));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", lu.username());
        data.put("name", lu.name());
        data.put("isAdmin", lu.isAdmin());
        data.put("mustChangePwd", lu.mustChangePwd());
        data.put("roles", lu.roles());
        data.put("modules", modules);
        // v3.4：前端按这些信息决定按钮与字段的显示（后端仍会逐一校验）
        Map<String, Object> actions = new LinkedHashMap<>();
        lu.modulePerms().forEach((k, v) -> {
            Map<String, Boolean> cfg = lu.actionPerms() == null ? null : lu.actionPerms().get(k);
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("delete", lu.isAdmin() || (v.write() && cfg != null && Boolean.TRUE.equals(cfg.get("delete"))));
            for (String act : List.of("create", "update", "import", "export", "approve")) {
                a.put(act, lu.isAdmin() || cfg == null || !Boolean.FALSE.equals(cfg.get(act)));
            }
            actions.put(k, a);
        });
        data.put("actions", actions);
        data.put("fields", lu.fieldPerms() == null ? Map.of() : lu.fieldPerms());
        Map<String, Object> scopes = new LinkedHashMap<>();
        if (lu.dataScopes() != null && !lu.isAdmin()) {
            lu.dataScopes().forEach((k, v) -> scopes.put(k, v == null ? "ALL" : String.valueOf(v.getOrDefault("type", "ALL"))));
        }
        data.put("dataScopes", scopes);
        return ApiResult.ok(data);
    }

    private volatile String dummyHash;

    private String dummyHash() {
        String h = this.dummyHash;
        if (h == null) {
            h = this.passwordEncoder.encode(java.util.UUID.randomUUID().toString());
            this.dummyHash = h;
        }
        return h;
    }

    private UserEntity selectByUsername(String username) {
        return (UserEntity)this.userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
    }

    private Map<String, Object> issueTokens(String username, String name, LoginUser lu, HttpServletRequest request, HttpServletResponse response) {
        String accessToken = this.jwtService.createAccessToken(username, name, lu.roles(), lu.isAdmin());
        String refreshToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        this.tokenStore.save(refreshToken, username, Duration.ofHours(this.refreshTtlHours));
        this.authCookies.write(request, response, "access_token", accessToken, (int)(this.accessTtlMinutes * 60L));
        this.authCookies.write(request, response, "refresh_token", refreshToken, (int)(this.refreshTtlHours * 3600L));
        Map<String, Object> userMap = new LinkedHashMap<>();
        userMap.put("username", username);
        userMap.put("name", name);
        userMap.put("isAdmin", lu.isAdmin());
        userMap.put("mustChangePwd", lu.mustChangePwd());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", userMap);
        return data;
    }

    private String bearerOrCookie(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            return h.substring(7);
        }
        return this.authCookies.read(request, "access_token");
    }

    private String clientIp(HttpServletRequest request) {
        return this.clientIpResolver.resolve(request);
    }

    
    public AuthController(
        final UserMapper userMapper,
        final LoginUserLoader loginUserLoader,
        final JwtService jwtService,
        final TokenStore tokenStore,
        final AuditService auditService,
        final AuditThrottle auditThrottle,
        final LoginThrottle loginThrottle,
        final PasswordEncoder passwordEncoder,
        final LdapAuthService ldap,
        final LdapUserProvisioner ldapProvisioner,
        final ClientIp clientIpResolver,
        final AuthCookies authCookies,
        final TokenRevocationService revocation
    ) {
        this.revocation = revocation;
        this.userMapper = userMapper;
        this.loginUserLoader = loginUserLoader;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.auditService = auditService;
        this.auditThrottle = auditThrottle;
        this.loginThrottle = loginThrottle;
        this.passwordEncoder = passwordEncoder;
        this.ldap = ldap;
        this.ldapProvisioner = ldapProvisioner;
        this.clientIpResolver = clientIpResolver;
        this.authCookies = authCookies;
    }

    public static record LoginReq(@NotBlank(message = "用户名不能为空") String username, @NotBlank(message = "密码不能为空") String password) {
    }

    public static record RefreshReq(@NotBlank(message = "refreshToken 不能为空") String refreshToken) {
    }
}
