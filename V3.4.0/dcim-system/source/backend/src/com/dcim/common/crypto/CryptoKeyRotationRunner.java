package com.dcim.common.crypto;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时把旧密钥加密的敏感字段重加密为当前密钥（仅在配置了 DCIM_CRYPTO_KEY_OLD 时执行，幂等）。
 * 覆盖：bmc_credential.password_enc（带外凭据）、webhook_endpoint.secret（签名密钥）。
 */
@Component
public class CryptoKeyRotationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CryptoKeyRotationRunner.class);
    private static final String[][] TARGETS = {
        {"bmc_credential", "id", "password_enc"},
        {"webhook_endpoint", "id", "secret"},
    };
    private final CryptBox box;
    private final JdbcTemplate jdbc;

    public CryptoKeyRotationRunner(CryptBox box, JdbcTemplate jdbc) {
        this.box = box;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!this.box.hasOldKeys()) {
            return;
        }
        int rotated = 0;
        int failed = 0;
        for (String[] t : TARGETS) {
            List<Map<String, Object>> rows;
            try {
                rows = this.jdbc.queryForList("SELECT " + t[1] + " AS id, " + t[2] + " AS v FROM " + t[0] + " WHERE " + t[2] + " IS NOT NULL AND " + t[2] + " <> ''");
            } catch (RuntimeException e) {
                log.warn("[crypto] 读取 {} 失败，跳过：{}", t[0], e.getMessage());
                continue;
            }
            for (Map<String, Object> r : rows) {
                String v = String.valueOf(r.get("v"));
                if (this.box.isCurrent(v)) {
                    continue;
                }
                try {
                    String plain = this.box.decrypt(v);
                    this.jdbc.update("UPDATE " + t[0] + " SET " + t[2] + " = ? WHERE " + t[1] + " = ?", this.box.encrypt(plain), r.get("id"));
                    rotated++;
                } catch (RuntimeException e) {
                    failed++;
                    log.error("[crypto] {}#{} 无法用当前或旧密钥解密，保持原值", t[0], r.get("id"));
                }
            }
        }
        log.warn("[crypto] 密钥轮换：已重加密 {} 条，失败 {} 条。{}", rotated, failed,
            failed == 0 ? "确认无误后可从配置中移除 DCIM_CRYPTO_KEY_OLD" : "存在无法解密的数据，请勿移除旧密钥");
    }
}
