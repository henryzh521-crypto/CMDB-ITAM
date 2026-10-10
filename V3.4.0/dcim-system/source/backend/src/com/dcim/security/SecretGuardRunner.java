package com.dcim.security;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class SecretGuardRunner implements ApplicationRunner {
    
    private static final Logger log = LoggerFactory.getLogger(SecretGuardRunner.class);
    static final String DEFAULT_JWT_SECRET = "dev-only-secret-key-change-me-in-production-0123456789abcdef-0123456789abcdef";
    static final String DEFAULT_CRYPTO_KEY = "ZGNpbS13Mi1kZWZhdWx0LWFlczI1Ni1rZXktMzJieCE=";
    static final String DEFAULT_AGENT_KEY = "dev-agent-key";
    static final String DEFAULT_MINIO_KEY = "minioadmin";
    static final String DEFAULT_TDENGINE_PASSWORD = "taosdata";
    private final Environment environment;
    /**
     * 已随 v3.2.0 安装包（deploy/.env）外泄的密钥 SHA-256 指纹（只存摘要，不存原值）。
     * 用该安装包部署的环境共用这些密钥：持有安装包者可伪造任意用户 JWT、解密 BMC 凭据、冒充采集器。
     */
    static final java.util.Map<String, String> LEAKED_FINGERPRINTS = java.util.Map.of(
        "dcim.jwt.secret", "840534c11a20f5f19771a371e701f7e09a1aac41973f4e2900246b061fab3f64",
        "dcim.crypto.key", "d8328efb13b1b17785f1a8b7e365ff6c41c9cd9691c530f50353b3da83f4d697",
        "dcim.collector.agent-key", "e30bdadb6837ed2183e544a641c9b6989cf874af909570e7fc82791231620575",
        "dcim.monitoring.token", "76d9df2d9e97658d34d49f778b32e807b0421cdff0b6a4cd1622bed08f935a7d"
    );

    public SecretGuardRunner(Environment environment) {
        this.environment = environment;
    }

    public void run(ApplicationArguments args) {
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        this.check(violations, warnings, "dcim.jwt.secret", "dev-only-secret-key-change-me-in-production-0123456789abcdef-0123456789abcdef");
        this.check(violations, warnings, "dcim.crypto.key", "ZGNpbS13Mi1kZWZhdWx0LWFlczI1Ni1rZXktMzJieCE=");
        this.check(violations, warnings, "dcim.collector.agent-key", "dev-agent-key");
        if ("minio".equalsIgnoreCase(this.environment.getProperty("dcim.storage.type", "local"))) {
            this.check(violations, warnings, "dcim.storage.minio.access-key", "minioadmin");
            this.check(violations, warnings, "dcim.storage.minio.secret-key", "minioadmin");
        }

        if ("tdengine".equalsIgnoreCase(this.environment.getProperty("dcim.metrics.store", "memory"))) {
            this.check(violations, warnings, "dcim.metrics.tdengine.password", "taosdata");
        }

        boolean prod = this.isProd();
        List<String> leaked = new ArrayList<>();
        LEAKED_FINGERPRINTS.forEach((prop, fp) -> {
            String v = this.environment.getProperty(prop);
            if (v != null && fp.equals(sha256(v.trim()))) {
                leaked.add(prop);
            }
        });
        if (!leaked.isEmpty()) {
            String msg = "检测到已随 v3.2.0 安装包外泄的密钥：" + String.join(", ", leaked)
                + "。请执行 ./dcimctl rotate-secrets 轮换（加密密钥会自动重加密已存凭据）";
            boolean allow = "true".equalsIgnoreCase(this.environment.getProperty("dcim.security.allow-leaked-secrets", "false"));
            if (prod && !allow) {
                throw new IllegalStateException(msg + "；确需临时启动可设置 DCIM_ALLOW_LEAKED_SECRETS=true");
            }
            log.error("[secret] {}", msg);
        }

        if (!violations.isEmpty()) {
            String msg = "检测到内置默认密钥，拒绝启动（请通过环境变量覆盖）: " + String.join(", ", violations);
            if (prod) {
                throw new IllegalStateException(msg);
            }

            log.warn("[secret] {}", msg);
        }

        if (!warnings.isEmpty()) {
            log.warn("[secret] 仍在使用示例值（非 prod 仅告警）: {}", String.join(", ", warnings));
        }

        if (prod) {
            log.info("[secret] 默认密钥熔断已生效（prod profile）");
        }
    }

    private void check(List<String> violations, List<String> warnings, String property, String knownDefault) {
        String value = this.environment.getProperty(property);
        if (value != null && !value.isBlank()) {
            if (knownDefault.equals(value.trim()) || value.contains("change-me")) {
                (this.isProd() ? violations : warnings).add(property);
            }
        } else {
            violations.add(property + "(未配置)");
        }
    }

    static String sha256(String s) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isProd() {
        for (String p : this.environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(p)) {
                return true;
            }
        }

        return false;
    }
}
