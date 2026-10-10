package com.dcim.collector;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AgentKeyFilter extends OncePerRequestFilter {
    private final byte[] expectedKey;
    private final boolean disabled;

    public AgentKeyFilter(@Value("${dcim.collector.agent-key}") String agentKey) {
        String trimmed = agentKey == null ? "" : agentKey.trim();
        this.disabled = trimmed.isEmpty();
        this.expectedKey = trimmed.getBytes(StandardCharsets.UTF_8);
        if (this.disabled) {
            System.err.println("[AgentKeyFilter] dcim.collector.agent-key 未配置，/api/collector/** 已全部拒绝（fail-closed）");
        }
    }

    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/collector/");
    }

    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader("X-Agent-Key");
        boolean ok = !this.disabled && key != null && MessageDigest.isEqual(this.expectedKey, key.trim().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":401,\"msg\":\"采集器密钥无效（X-Agent-Key）\"}");
        } else {
            chain.doFilter(request, response);
        }
    }
}
