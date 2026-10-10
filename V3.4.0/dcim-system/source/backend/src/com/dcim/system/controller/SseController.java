package com.dcim.system.controller;

import com.dcim.common.BusinessException;
import com.dcim.common.sse.SseConnectionRegistry;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 实时推送端点（v3.5）。
 *
 * <p>浏览器 EventSource 无法携带 Authorization 头，JwtAuthFilter 仅对本路径
 * 额外接受 {@code ?token=} 查询参数（泄露面控制：严格限定路径 + 网关层对该
 * 路径的访问日志去除 query，见 deploy/Caddyfile 与 UPGRADE-3.5 文档）。
 *
 * <p>连接注册进 {@link SseConnectionRegistry} 后由服务端心跳维持；停用 / 超限
 * 返回 503，前端 EventSource onerror 后降级为 30s 轮询。
 */
@RestController
@RequestMapping({"/api/notifications"})
public class SseController {
    private final SseConnectionRegistry registry;
    private final boolean enabled;

    public SseController(SseConnectionRegistry registry, @Value("${dcim.sse.enabled:true}") boolean enabled) {
        this.registry = registry;
        this.enabled = enabled;
    }

    @GetMapping({"/sse"})
    public SseEmitter sse() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else if (!this.enabled) {
            throw new BusinessException(503, "SSE 推送已停用");
        } else {
            return this.registry.register(u.username());
        }
    }
}
