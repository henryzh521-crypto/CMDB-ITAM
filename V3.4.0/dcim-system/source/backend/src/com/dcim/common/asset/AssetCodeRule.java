package com.dcim.common.asset;

import java.time.LocalDate;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AssetCodeRule {
    public static final String DICT_TYPE = "code_rule";
    public static final String DICT_KEY = "asset";
    public static final String DEFAULT_TEMPLATE = "{CATEGORY}-{REGION}{YY}-{YYYY}-{SEQ:5}";
    public static final String DEFAULT_CATEGORY = "AST";
    public static final String DEFAULT_REGION = "NA";
    private static final int MAX_LENGTH = 200;
    private static final int MAX_SEQ_WIDTH = 10;
    private static final int DEFAULT_SEQ_WIDTH = 5;
    private static final Set<String> KNOWN = Set.of("CATEGORY", "REGION", "YYYY", "YY", "MM", "DD");
    private static final Pattern SEQ_TOKEN = Pattern.compile("\\{SEQ(?::([0-9]{1,2}))?\\}");
    private static final Pattern ANY_TOKEN = Pattern.compile("\\{([^{}]*)\\}");
    private final String template;
    private final String headTemplate;
    private final String seqToken;
    private final int seqWidth;

    private AssetCodeRule(String template, String headTemplate, String seqToken, int seqWidth) {
        this.template = template;
        this.headTemplate = headTemplate;
        this.seqToken = seqToken;
        this.seqWidth = seqWidth;
    }

    public static AssetCodeRule parse(String template) {
        if (template != null && !template.isBlank()) {
            String tpl = template.trim();
            if (tpl.length() > 200) {
                throw new IllegalArgumentException("编码规则模板最长 200 字符");
            } else {
                String seqToken = null;
                int seqWidth = 5;
                int seqCount = 0;
                int seqEnd = -1;
                Matcher m = SEQ_TOKEN.matcher(tpl);

                while (m.find()) {
                    seqCount++;
                    seqToken = m.group();
                    seqEnd = m.end();
                    if (m.group(1) != null) {
                        int w = Integer.parseInt(m.group(1));
                        if (w < 1 || w > 10) {
                            throw new IllegalArgumentException("序号位宽 {SEQ:" + w + "} 须在 1..10 之间");
                        }

                        seqWidth = w;
                    }
                }

                if (seqCount != 1) {
                    throw new IllegalArgumentException("模板必须且只能包含一个 {SEQ} 或 {SEQ:n} 占位符（n=1..10）");
                } else if (seqEnd != tpl.length()) {
                    throw new IllegalArgumentException("{SEQ} 必须位于模板末尾（流水号固定收尾）");
                } else {
                    Matcher any = ANY_TOKEN.matcher(tpl);

                    while (any.find()) {
                        String token = any.group(1);
                        boolean isSeq = "SEQ".equals(token) || token.matches("SEQ:[0-9]{1,2}");
                        if (!isSeq && !KNOWN.contains(token)) {
                            throw new IllegalArgumentException("未知占位符 {" + token + "}，可用：{CATEGORY} {REGION} {YYYY} {YY} {MM} {DD} {SEQ:n}");
                        }
                    }

                    String head = tpl.substring(0, tpl.length() - seqToken.length());
                    if (head.isBlank()) {
                        throw new IllegalArgumentException("模板除 {SEQ} 外至少还需包含一段内容");
                    } else {
                        return new AssetCodeRule(tpl, head, seqToken, seqWidth);
                    }
                }
            }
        } else {
            throw new IllegalArgumentException("编码规则模板不能为空");
        }
    }

    public static AssetCodeRule defaults() {
        return parse("{CATEGORY}-{REGION}{YY}-{YYYY}-{SEQ:5}");
    }

    public String template() {
        return this.template;
    }

    public String seqPrefix(String categoryCode, String regionCode, LocalDate date) {
        String key = this.substitute(this.headTemplate, categoryCode, regionCode, date).trim();

        while (key.endsWith("-")) {
            key = key.substring(0, key.length() - 1);
        }

        return key;
    }

    public String render(String categoryCode, String regionCode, LocalDate date, long seq) {
        return this.substitute(this.headTemplate, categoryCode, regionCode, date) + String.format("%0" + this.seqWidth + "d", seq);
    }

    private String substitute(String tpl, String categoryCode, String regionCode, LocalDate date) {
        String cat = categoryCode != null && !categoryCode.isBlank() ? categoryCode.trim() : "AST";
        String region = regionCode != null && !regionCode.isBlank() ? regionCode.trim() : "NA";
        String year = String.valueOf(date.getYear());
        return tpl.replace("{CATEGORY}", cat)
            .replace("{REGION}", region)
            .replace("{YYYY}", year)
            .replace("{YY}", String.format("%02d", date.getYear() % 100))
            .replace("{MM}", String.format("%02d", date.getMonthValue()))
            .replace("{DD}", String.format("%02d", date.getDayOfMonth()));
    }
}
