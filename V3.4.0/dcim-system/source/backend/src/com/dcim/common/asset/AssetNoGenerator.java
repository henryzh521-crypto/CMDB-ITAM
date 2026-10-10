package com.dcim.common.asset;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AssetNoGenerator {
    
    private static final Logger log = LoggerFactory.getLogger(AssetNoGenerator.class);
    private static final String SELECT_RULE = "SELECT dict_value FROM dict WHERE dict_type = ? AND dict_key = ? AND status = 1 ORDER BY id LIMIT 1";
    private final JdbcTemplate jdbc;

    @Transactional
    public String generate(String categoryCode, String regionCode) {
        LocalDate today = LocalDate.now();
        AssetCodeRule rule = this.loadRule();
        String cat = categoryCode != null && !categoryCode.isBlank() ? categoryCode.trim() : "AST";
        String region = regionCode != null && !regionCode.isBlank() ? regionCode.trim() : "NA";
        String prefix = rule.seqPrefix(cat, region, today);
        Long nextNo = (Long)this.jdbc
            .queryForObject(
                "INSERT INTO asset_no_seq(prefix, next_no) VALUES (?, 1) ON CONFLICT (prefix) DO UPDATE SET next_no = asset_no_seq.next_no + 1 RETURNING next_no",
                Long.class,
                new Object[]{prefix}
            );
        if (nextNo == null) {
            throw new IllegalStateException("资产编码发号失败：" + prefix);
        } else {
            return rule.render(cat, region, today, nextNo);
        }
    }

    private AssetCodeRule loadRule() {
        String template;
        try {
            template = (String)this.jdbc
                .queryForObject(
                    "SELECT dict_value FROM dict WHERE dict_type = ? AND dict_key = ? AND status = 1 ORDER BY id LIMIT 1",
                    String.class,
                    new Object[]{"code_rule", "asset"}
                );
        } catch (EmptyResultDataAccessException var4) {
            return AssetCodeRule.defaults();
        }

        try {
            return AssetCodeRule.parse(template);
        } catch (IllegalArgumentException var3) {
            log.warn("字典 {} / {} 的编码规则非法（{}），回退缺省模板 {}", new Object[]{"code_rule", "asset", var3.getMessage(), "{CATEGORY}-{REGION}{YY}-{YYYY}-{SEQ:5}"});
            return AssetCodeRule.defaults();
        }
    }

    
    public AssetNoGenerator(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
