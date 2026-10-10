package com.dcim.security;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.system.audit.AuditService;
import com.dcim.system.entity.UserEntity;
import com.dcim.system.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth/wecom"})
@Validated
public class WecomAuthController {
    private static final List<String> LOOPBACK = List.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");
    private final WecomProperties wecomProperties;
    private final ObjectProvider<WecomApiClient> wecomClient;
    private final UserMapper userMapper;
    private final LoginUserLoader loginUserLoader;
    private final JwtService jwtService;
    private final TokenStore tokenStore;
    private final AuditService auditService;
    private final Environment environment;
    private final ClientIp clientIpResolver;
    private final AuthCookies authCookies;
    @Value("${dcim.jwt.refresh-ttl-hours}")
    private long refreshTtlHours;
    @Value("${dcim.jwt.access-ttl-minutes}")
    private long accessTtlMinutes;
    @Value("${dcim.auth.dev-login-enabled:false}")
    private boolean devLoginEnabled;

    @GetMapping({"/config"})
    public ApiResult<Map<String, Object>> config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", this.wecomProperties.isEnabled());
        data.put("corpId", this.blankToNull(this.wecomProperties.getCorpId()));
        data.put("agentId", this.blankToNull(this.wecomProperties.getAgentId()));
        data.put("devLoginEnabled", this.devLoginUsable());
        return ApiResult.ok(data);
    }

    @PostMapping({"/dev-login"})
    public ApiResult<Map<String, Object>> devLogin(
        @RequestBody @Valid WecomAuthController.DevLoginReq req, HttpServletRequest request, HttpServletResponse response
    ) {
        if (this.devLoginDenial() != null) {
            throw BusinessException.forbidden(this.devLoginDenial());
            // 修复：改用 TCP 层来源地址（getRemoteAddr）判断是否本机。原实现采信 X-Forwarded-For，
            // 内网机器伪造 `X-Forwarded-For: 127.0.0.1` 即可绕过本机限制，免密登录任意账号（含 admin）
        } else if (!LOOPBACK.contains(request.getRemoteAddr())) {
            throw BusinessException.forbidden("开发联调登录仅限本机访问");
        } else {
            UserEntity user = (UserEntity)this.userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, req.username().trim()));
            if (user == null) {
                throw BusinessException.notFound("用户不存在");
            } else if (user.getStatus() != null && user.getStatus() == 1) {
                LoginUser lu = this.loginUserLoader.load(user.getUsername());
                Map<String, Object> data = this.issueTokens(user.getUsername(), user.getName(), lu, request, response);
                data.put("devMode", true);
                // 修复：dev-login 成功此前无审计记录，登录行为不可追溯
                this.auditService
                    .record(user.getUsername(), "LOGIN", "auth", "user", user.getUsername(), Map.of("result", "成功", "channel", "dev"), request.getRemoteAddr());
                return ApiResult.ok(data);
            } else {
                throw BusinessException.unauthorized("用户已停用，请联系管理员");
            }
        }
    }

    private boolean devLoginUsable() {
        return this.devLoginDenial() == null;
    }

    private String devLoginDenial() {
        if (Arrays.stream(this.environment.getActiveProfiles()).anyMatch(p -> "prod".equalsIgnoreCase(p))) {
            return "仅开发环境可用";
        } else if (this.wecomProperties.isEnabled()) {
            return "仅开发环境可用";
        } else {
            return !this.devLoginEnabled ? "开发联调登录未启用" : null;
        }
    }

    @PostMapping({"/login"})
    public ApiResult<Map<String, Object>> login(
        @RequestBody @Valid WecomAuthController.WecomLoginReq req, HttpServletRequest request, HttpServletResponse response
    ) {
        if (!this.wecomProperties.isEnabled()) {
            throw BusinessException.badRequest("企微登录未启用");
        } else {
            WecomApiClient.WecomUserInfo info = ((WecomApiClient)this.wecomClient.getObject()).getUserInfoByCode(req.code().trim());
            // 修复：移除「按 username == 企微 userid 隐式匹配」的回退绑定。企微 userid 可能被企业内成员
            // 自行设置为与管理员等同名值，原实现可借此接管未绑定企微的平台账号。企微登录必须显式绑定。
            UserEntity user = (UserEntity)this.userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getWecomUserid, info.userid()));

            if (user == null) {
                throw BusinessException.unauthorized("企微账号未绑定平台用户，请联系管理员绑定");
            } else if (user.getStatus() != null && user.getStatus() == 1) {
                LoginUser lu = this.loginUserLoader.load(user.getUsername());
                Map<String, Object> data = this.issueTokens(user.getUsername(), user.getName(), lu, request, response);
                this.userMapper
                    .update(
                        null,
                        (Wrappers.<UserEntity>lambdaUpdate().eq(UserEntity::getId, user.getId()))
                            .set(UserEntity::getLastLoginAt, LocalDateTime.now())
                    );
                this.auditService
                    .record(user.getUsername(), "LOGIN", "auth", "user", user.getUsername(), Map.of("result", "成功", "channel", "wecom"), this.clientIp(request));
                return ApiResult.ok(data);
            } else {
                throw BusinessException.unauthorized("用户已停用，请联系管理员");
            }
        }
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

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private String clientIp(HttpServletRequest request) {
        return this.clientIpResolver.resolve(request);
    }

    
    public WecomAuthController(
        final WecomProperties wecomProperties,
        final ObjectProvider<WecomApiClient> wecomClient,
        final UserMapper userMapper,
        final LoginUserLoader loginUserLoader,
        final JwtService jwtService,
        final TokenStore tokenStore,
        final AuditService auditService,
        final Environment environment,
        final ClientIp clientIpResolver,
        final AuthCookies authCookies
    ) {
        this.wecomProperties = wecomProperties;
        this.wecomClient = wecomClient;
        this.userMapper = userMapper;
        this.loginUserLoader = loginUserLoader;
        this.jwtService = jwtService;
        this.tokenStore = tokenStore;
        this.auditService = auditService;
        this.environment = environment;
        this.clientIpResolver = clientIpResolver;
        this.authCookies = authCookies;
    }

    public static record DevLoginReq(@NotBlank(message = "username 不能为空") String username) {
    }

    public static record WecomLoginReq(@NotBlank(message = "code 不能为空") String code) {
    }
}
