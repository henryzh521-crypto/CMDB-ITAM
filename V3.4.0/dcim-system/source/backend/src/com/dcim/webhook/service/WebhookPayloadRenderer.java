package com.dcim.webhook.service;

import com.dcim.common.event.EventTypes;
import com.dcim.problem.ProblemService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class WebhookPayloadRenderer {
    public static final String GENERIC = "GENERIC";
    public static final String WECOM_BOT = "WECOM_BOT";
    public static final String DINGTALK_BOT = "DINGTALK_BOT";
    public static final String FEISHU_BOT = "FEISHU_BOT";
    private final ObjectMapper objectMapper;

    public WebhookPayloadRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static boolean isKnownFormat(String f) {
        return "GENERIC".equals(f) || "WECOM_BOT".equals(f) || "DINGTALK_BOT".equals(f) || "FEISHU_BOT".equals(f);
    }

    public WebhookPayloadRenderer.Rendered render(String format, String url, String secret, String deliveryId, Map<String, Object> envelope) {
        long ts = System.currentTimeMillis();
        String fmt = format == null ? "GENERIC" : format;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=utf-8");
        headers.put("User-Agent", "DCIM-Webhook/3.1");
        String text = this.summarize(envelope);

        try {
            switch (fmt) {
                case "WECOM_BOT": {
                    Map<String, Object> body = Map.of("msgtype", "markdown", "markdown", Map.of("content", text));
                    return new WebhookPayloadRenderer.Rendered(url, this.objectMapper.writeValueAsString(body), headers);
                }
                case "DINGTALK_BOT": {
                    Map<String, Object> body = Map.of("msgtype", "markdown", "markdown", Map.of("title", this.titleOf(envelope), "text", text));
                    String finalUrl = url;
                    if (secret != null && !secret.isBlank()) {
                        String sign = Base64.getEncoder().encodeToString(hmac(secret, ts + "\n" + secret));
                        finalUrl = url + (url.contains("?") ? "&" : "?") + "timestamp=" + ts + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
                    }

                    return new WebhookPayloadRenderer.Rendered(finalUrl, this.objectMapper.writeValueAsString(body), headers);
                }
                case "FEISHU_BOT": {
                    Map<String, Object> body = new LinkedHashMap<>();
                    if (secret != null && !secret.isBlank()) {
                        long sec = ts / 1000L;
                        body.put("timestamp", String.valueOf(sec));
                        body.put("sign", Base64.getEncoder().encodeToString(hmacKeyOnly(sec + "\n" + secret)));
                    }

                    body.put("msg_type", "text");
                    body.put("content", Map.of("text", text.replace("**", "")));
                    return new WebhookPayloadRenderer.Rendered(url, this.objectMapper.writeValueAsString(body), headers);
                }
                default: {
                    String body = this.objectMapper.writeValueAsString(envelope);
                    headers.put("X-DCIM-Event", String.valueOf(envelope.get("type")));
                    headers.put("X-DCIM-Delivery", deliveryId);
                    headers.put("X-DCIM-Timestamp", String.valueOf(ts / 1000L));
                    if (secret != null && !secret.isBlank()) {
                        headers.put("X-DCIM-Signature", "sha256=" + HexFormat.of().formatHex(hmac(secret, ts / 1000L + "." + body)));
                    }

                    return new WebhookPayloadRenderer.Rendered(url, body, headers);
                }
            }
        } catch (JsonProcessingException var16) {
            throw new IllegalStateException("Webhook 载荷序列化失败", var16);
        }
    }

    String summarize(Map<String, Object> env) {
        String type = String.valueOf(env.get("type"));
        Map<?, ?> data = env.get("data") instanceof Map<?, ?> m ? m : Map.of();
        StringBuilder sb = new StringBuilder();
        sb.append("**【DCIM】").append(this.titleOf(env)).append("**\n");
        sb.append("> 对象：").append(env.get("objType")).append(" ").append(env.get("objId")).append("\n");
        switch (type) {
            case "workorder.created":
                sb.append("> 工单：")
                    .append(data.get("title"))
                    .append("（")
                    .append(data.get("woType"))
                    .append(" / 优先级 ")
                    .append(data.get("priority"))
                    .append("）\n");
                break;
            case "workorder.status_changed":
            case "workorder.completed":
            case "change.status_changed":
                sb.append("> 状态：").append(data.get("fromStatus")).append(" → ").append(data.get("toStatus")).append("\n");
                break;
            case "alert.firing":
            case "alert.resolved":
                String sev = String.valueOf(data.get("severity"));
                sb.append("> 告警：")
                    .append(data.get("alertname"))
                    .append("（")
                    .append("critical".equals(sev) ? "严重" : ("warning".equals(sev) ? "警告" : sev))
                    .append("）\n");
                sb.append("> 摘要：").append(data.get("summary")).append("\n");
                if (data.get("assetNo") != null) {
                    sb.append("> 资产：").append(data.get("assetNo"));
                    if (data.get("assetName") != null) {
                        sb.append(" ").append(data.get("assetName"));
                    }

                    sb.append("\n");
                } else if (data.get("instance") != null) {
                    sb.append("> 实例：").append(data.get("instance")).append("\n");
                }

                if (data.get("rack") != null) {
                    sb.append("> 位置：").append(data.get("room")).append(" / ").append(data.get("rack")).append("\n");
                }
                break;
            case "asset.warranty_expiring":
            case "contract.expiring":
                sb.append("> ").append(data.get("name")).append("，").append(data.get("endDate")).append(" 到期（剩余 ").append(data.get("daysLeft")).append(" 天）\n");
                break;
            case "problem.status_changed":
                sb.append("> ").append(data.get("title")).append("\n");
                sb.append("> 状态：").append(problemStatus(data.get("from"))).append(" → ").append(problemStatus(data.get("to"))).append("\n");
                if (data.get("kbNo") != null) {
                    sb.append("> 知识库：").append(data.get("kbNo")).append("\n");
                }
                break;
            case "stock.low":
                sb.append("> 物料：")
                    .append(data.get("name"))
                    .append("，当前库存 ")
                    .append(data.get("stock"))
                    .append(" / 安全库存 ")
                    .append(data.get("safetyStock"))
                    .append("\n");
                break;
            case "stock.movement":
                sb.append("> ")
                    .append(data.get("logType"))
                    .append(" 数量 ")
                    .append(data.get("qty"))
                    .append("，仓库 ")
                    .append(data.get("warehouseCode"))
                    .append("\n");
                break;
            case "asset.status_changed":
                if (data.get("snapshot") instanceof Map<?, ?> s && s.get("before") instanceof Map<?, ?> b && s.get("after") instanceof Map<?, ?> a) {
                    sb.append("> 状态：").append(b.get("statusBiz")).append(" → ").append(a.get("statusBiz")).append("\n");
                }
        }

        sb.append("> 操作人：").append(env.get("actor")).append("\n");
        sb.append("> 时间：").append(env.get("occurredAt"));
        return sb.toString();
    }

    private String titleOf(Map<String, Object> env) {
        String type = String.valueOf(env.get("type"));
        return "ping".equals(type) ? "连通性测试" : EventTypes.CATALOG.getOrDefault(type, type);
    }

    static byte[] hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception var3) {
            throw new IllegalStateException("HMAC 计算失败", var3);
        }
    }

    private static byte[] hmacKeyOnly(String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(new byte[0]);
        } catch (Exception var2) {
            throw new IllegalStateException("HMAC 计算失败", var2);
        }
    }

    private static String problemStatus(Object s) {
        String v = String.valueOf(s);
        return ProblemService.STATUS_LABEL.getOrDefault(v, v);
    }

    public static record Rendered(String url, String body, Map<String, String> headers) {
    }
}
