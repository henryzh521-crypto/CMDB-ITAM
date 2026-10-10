package com.dcim.workorder.controller;

import com.dcim.common.BusinessException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 工单「关联资产」入参的宽松解析（v3.3.1 热补丁）。
 *
 * <p>背景：v3.2 只认 {@code relatedAssets}（字符串数组）和 {@code assets}（对象数组），第三方脚本 / 测试工具常用的
 * {@code assetNos} 被 Jackson 当作未知字段<b>静默丢弃</b>，工单建成了却没有关联资产。v3.3.0 加了别名，
 * 这里进一步把常见写法都收下来，并且对认不出的「像资产字段」的键直接报 400，不再静默丢弃：
 * <ul>
 *   <li>字段名：relatedAssets / assetNos / assetNoList / assetList / assetNo / relatedAssetNos / asset_nos / related_assets；</li>
 *   <li>取值：字符串数组、单个字符串、逗号（中英文）/ 分号 / 顿号 / 空白分隔的字符串、数字、带 assetNo 字段的对象（或对象数组）；</li>
 *   <li>{@code assets} 明细同时接受对象数组与字符串数组。</li>
 * </ul>
 */
public final class WoAssetJson {
    private static final Pattern SEP = Pattern.compile("[,，;；、\\s]+");
    /** 由 relatedAssets 的 @JsonAlias 接收的字段名（小写比较），其余含 asset 的未知字段一律拒绝 */
    static final String ACCEPTED_HINT = "relatedAssets（资产编号数组）或 assets（明细对象数组），也兼容 assetNos / assetNoList";

    private WoAssetJson() {
    }

    static void addSplit(List<String> out, String s) {
        if (s == null) {
            return;
        }
        for (String part : SEP.split(s.trim())) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
    }

    static String assetNoOf(JsonNode obj) {
        for (String k : new String[]{"assetNo", "asset_no", "assetCode", "no"}) {
            JsonNode v = obj.get(k);
            if (v != null && !v.isNull() && v.isValueNode()) {
                return v.asText();
            }
        }
        return null;
    }

    /**
     * 请求体里出现了认不出的、名字含 asset 的字段 → 400（以前会被静默丢弃）。
     * 其他未知字段保持 v3.2 行为（忽略），避免影响已有调用方。
     */
    public static void rejectUnknownAssetFields(Map<String, Object> unknown) {
        if (unknown == null || unknown.isEmpty()) {
            return;
        }
        TreeSet<String> bad = new TreeSet<>();
        for (String k : unknown.keySet()) {
            if (k != null && k.toLowerCase(Locale.ROOT).contains("asset")) {
                bad.add(k);
            }
        }
        if (!bad.isEmpty()) {
            throw BusinessException.badRequest("无法识别的关联资产字段：" + String.join("、", bad) + "；请使用 " + ACCEPTED_HINT);
        }
    }

    /** relatedAssets 及其别名 → 资产编号列表 */
    public static final class AssetNoList extends JsonDeserializer<List<String>> {
        @Override
        public List<String> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();
            List<String> out = new ArrayList<>();
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            collect(node, out, p, ctxt, true);
            return out;
        }

        private static void collect(JsonNode node, List<String> out, JsonParser p, DeserializationContext ctxt, boolean top) throws IOException {
            if (node.isTextual()) {
                addSplit(out, node.asText());
            } else if (node.isNumber()) {
                out.add(node.asText());
            } else if (node.isObject()) {
                String no = assetNoOf(node);
                if (no == null) {
                    ctxt.reportInputMismatch(List.class, "关联资产对象缺少 assetNo 字段");
                }
                addSplit(out, no);
            } else if (node.isArray() && top) {
                Iterator<JsonNode> it = node.elements();
                while (it.hasNext()) {
                    JsonNode e = it.next();
                    if (e != null && !e.isNull()) {
                        collect(e, out, p, ctxt, false);
                    }
                }
            } else {
                ctxt.reportInputMismatch(List.class, "关联资产格式不正确：" + node.getNodeType());
            }
        }

        @Override
        public List<String> getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }

    /** assets 明细：对象数组（标准）、字符串数组、单个对象、逗号分隔字符串 */
    public static final class AssetLines extends JsonDeserializer<List<WorkorderController.AssetLineReq>> {
        @Override
        public List<WorkorderController.AssetLineReq> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();
            if (node == null || node.isNull() || node.isMissingNode()) {
                return null;
            }
            List<WorkorderController.AssetLineReq> out = new ArrayList<>();
            if (node.isArray()) {
                Iterator<JsonNode> it = node.elements();
                while (it.hasNext()) {
                    one(it.next(), out, p, ctxt);
                }
            } else {
                one(node, out, p, ctxt);
            }
            return out;
        }

        private static void one(JsonNode e, List<WorkorderController.AssetLineReq> out, JsonParser p, DeserializationContext ctxt) throws IOException {
            if (e == null || e.isNull()) {
                return;
            }
            if (e.isTextual() || e.isNumber()) {
                List<String> nos = new ArrayList<>();
                addSplit(nos, e.asText());
                for (String no : nos) {
                    WorkorderController.AssetLineReq line = new WorkorderController.AssetLineReq();
                    line.setAssetNo(no);
                    out.add(line);
                }
            } else if (e.isObject()) {
                WorkorderController.AssetLineReq line = p.getCodec().treeToValue(e, WorkorderController.AssetLineReq.class);
                if ((line.getAssetNo() == null || line.getAssetNo().isBlank()) && assetNoOf(e) != null) {
                    line.setAssetNo(assetNoOf(e));
                }
                out.add(line);
            } else {
                ctxt.reportInputMismatch(List.class, "资产明细格式不正确：" + e.getNodeType());
            }
        }

        @Override
        public List<WorkorderController.AssetLineReq> getNullValue(DeserializationContext ctxt) {
            return null;
        }
    }
}
