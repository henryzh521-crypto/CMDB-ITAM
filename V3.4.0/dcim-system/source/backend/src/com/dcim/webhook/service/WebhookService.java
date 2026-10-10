package com.dcim.webhook.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.common.crypto.CryptBox;
import com.dcim.common.event.DomainEvent;
import com.dcim.common.event.EventTypes;
import com.dcim.common.notify.NotificationPort;
import com.dcim.webhook.entity.WebhookDelivery;
import com.dcim.webhook.entity.WebhookEndpoint;
import com.dcim.webhook.mapper.WebhookDeliveryMapper;
import com.dcim.webhook.mapper.WebhookEndpointMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WebhookService {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String RETRY = "RETRY";
    public static final String DEAD = "DEAD";
    static final Duration[] BACKOFF = new Duration[]{
        Duration.ofMinutes(1L), Duration.ofMinutes(5L), Duration.ofMinutes(30L), Duration.ofHours(2L), Duration.ofHours(6L)
    };
    static final int MAX_ATTEMPTS = BACKOFF.length + 1;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };
    private final WebhookEndpointMapper endpointMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final WebhookUrlGuard urlGuard;
    private final WebhookPayloadRenderer renderer;
    private final CryptBox cryptBox;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;
    private final NotificationPort notificationPort;
    private final int autoDisableThreshold;
    private final HttpClient httpClient;

    public WebhookService(
        WebhookEndpointMapper endpointMapper,
        WebhookDeliveryMapper deliveryMapper,
        WebhookUrlGuard urlGuard,
        WebhookPayloadRenderer renderer,
        CryptBox cryptBox,
        ObjectMapper objectMapper,
        TransactionTemplate tx,
        NotificationPort notificationPort,
        @Value("${dcim.webhook.auto-disable-after-failures:50}") int autoDisableThreshold,
        @Value("${dcim.webhook.connect-timeout-seconds:5}") int connectTimeoutSeconds
    ) {
        this.endpointMapper = endpointMapper;
        this.deliveryMapper = deliveryMapper;
        this.urlGuard = urlGuard;
        this.renderer = renderer;
        this.cryptBox = cryptBox;
        this.objectMapper = objectMapper;
        this.tx = tx;
        this.notificationPort = notificationPort;
        this.autoDisableThreshold = autoDisableThreshold;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds((long)connectTimeoutSeconds)).followRedirects(Redirect.NEVER).build();
    }

    public int enqueue(DomainEvent event) {
        List<WebhookEndpoint> endpoints = this.endpointMapper.selectList(Wrappers.<WebhookEndpoint>lambdaQuery().eq(WebhookEndpoint::getEnabled, true));
        Map<String, Object> envelope = null;
        int n = 0;

        for (WebhookEndpoint ep : endpoints) {
            List<String> types = ep.getEventTypes();
            if (types != null && (types.contains(event.type()) || types.contains("*"))) {
                if (envelope == null) {
                    envelope = this.envelope(event);
                }

                WebhookDelivery d = new WebhookDelivery();
                d.setEndpointId(ep.getId());
                d.setEventId(String.valueOf(envelope.get("id")));
                d.setEventType(event.type());
                d.setPayload(envelope);
                d.setStatus("PENDING");
                d.setAttempts(0);
                d.setNextAttemptAt(LocalDateTime.now());
                this.deliveryMapper.insert(d);
                n++;
            }
        }

        return n;
    }

    Map<String, Object> envelope(DomainEvent event) {
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("id", UUID.randomUUID().toString());
        env.put("type", event.type());
        env.put("objType", event.objType());
        env.put("objId", event.objId());
        env.put("actor", event.actor());
        env.put("occurredAt", event.occurredAt());
        env.put("data", event.data());
        return (Map<String, Object>)this.objectMapper.convertValue(env, MAP_TYPE);
    }

    public int processDue(int batch) {
        List<Long> ids = (List<Long>)this.tx
            .execute(
                status -> {
                    List<Long> locked = this.deliveryMapper.lockDue(batch);
                    if (!locked.isEmpty()) {
                        this.deliveryMapper
                            .update(
                                null,
                                (Wrappers.<WebhookDelivery>lambdaUpdate().in(WebhookDelivery::getId, locked))
                                    .set(WebhookDelivery::getNextAttemptAt, LocalDateTime.now().plusMinutes(5L))
                            );
                    }

                    return locked;
                }
            );
        if (ids != null && !ids.isEmpty()) {
            for (Long id : ids) {
                try {
                    this.deliverNow(id);
                } catch (RuntimeException var6) {
                    log.error("[webhook] 投递 #{} 处理异常：{}", id, var6.toString());
                }
            }

            return ids.size();
        } else {
            return 0;
        }
    }

    public WebhookDelivery deliverNow(Long deliveryId) {
        WebhookDelivery d = (WebhookDelivery)this.deliveryMapper.selectById(deliveryId);
        if (d == null) {
            return null;
        } else {
            WebhookEndpoint ep = (WebhookEndpoint)this.endpointMapper.selectById(d.getEndpointId());
            WebhookService.Attempt result;
            if (ep == null) {
                result = WebhookService.Attempt.fail(null, "端点已删除", 0);
                d.setAttempts(MAX_ATTEMPTS - 1);
            } else if (!Boolean.TRUE.equals(ep.getEnabled()) && !"ping".equals(d.getEventType())) {
                result = WebhookService.Attempt.fail(null, "端点已停用", 0);
                d.setAttempts(MAX_ATTEMPTS - 1);
            } else {
                result = this.send(ep, d);
            }

            this.applyResult(d, ep, result);
            return (WebhookDelivery)this.deliveryMapper.selectById(deliveryId);
        }
    }

    private WebhookService.Attempt send(WebhookEndpoint ep, WebhookDelivery d) {
        long start = System.nanoTime();

        try {
            // SSRF 纵深（DNS rebinding TOCTOU）：本行 check() 会解析并校验目标 IP，但 java.net.http
            // HttpClient 建立连接时会做第二次独立 DNS 解析，攻击者理论上可用超短 TTL/逐次应答在两次
            // 解析之间把主机重绑定到内网地址。彻底方案（按已校验 IP 直连 + Host 头保留原域名）不可行：
            // java.net.http 把 Host 列为受限头，手设会抛异常；放开需全局系统属性
            // jdk.httpclient.allowRestrictedHeaders（削弱整个 JVM 的 HTTP 防护），不予采用。
            // 故按降级方案维持「投递前紧邻 send 重新走守卫校验」（注册/改端点时是第一次，本行是第二次，
            // 与实际建连仅隔内存操作）。残余风险：µs~ms 窗口内的逐次重绑定。缓解因素：JVM InetAddress
            // 默认缓存成功解析 30s，第二次解析通常命中本行已校验的同一批地址；https 目标另受 TLS
            // 主机名校验约束（证书须匹配原域名），等效于钉扎，按原 URI 直连。
            URI uri = this.urlGuard.check(ep.getUrl());
            String secret = ep.getSecret() != null && !ep.getSecret().isBlank() ? this.cryptBox.decrypt(ep.getSecret()) : null;
            WebhookPayloadRenderer.Rendered r = this.renderer.render(ep.getFormat(), uri.toString(), secret, String.valueOf(d.getId()), d.getPayload());
            Builder b = HttpRequest.newBuilder(URI.create(r.url())).timeout(Duration.ofSeconds(15L)).POST(BodyPublishers.ofString(r.body()));
            r.headers().forEach(b::header);
            // 修复：ofString() 会把响应体全量读入内存，异常端点返回超大 body 时有内存风险；
            // 改为流式读取并截断到 64KB（足够 botError 解析 errcode）
            HttpResponse<java.io.InputStream> resp = this.httpClient.send(b.build(), BodyHandlers.ofInputStream());
            String body;
            try (java.io.InputStream in = resp.body()) {
                body = readCapped(in, 64 * 1024);
            }
            int ms = (int)((System.nanoTime() - start) / 1000000L);
            // 只留 200 字摘要：足够排查机器人返回码，又不至于把内网页面内容回显到投递记录
            String snippet = body.length() > 200 ? body.substring(0, 200) : body;
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                String botErr = this.botError(ep.getFormat(), body);
                return botErr == null
                    ? new WebhookService.Attempt(true, resp.statusCode(), snippet, null, ms)
                    : new WebhookService.Attempt(false, resp.statusCode(), snippet, botErr, ms);
            } else {
                return new WebhookService.Attempt(false, resp.statusCode(), snippet, "HTTP " + resp.statusCode(), ms);
            }
        } catch (InterruptedException var14) {
            Thread.currentThread().interrupt();
            return WebhookService.Attempt.fail(null, "投递被中断", elapsed(start));
        } catch (Exception var15) {
            String msg = var15.getMessage() == null ? var15.getClass().getSimpleName() : var15.getMessage();
            return WebhookService.Attempt.fail(null, msg.length() > 300 ? msg.substring(0, 300) : msg, elapsed(start));
        }
    }

    /** 最多读取 max 字节后停止（超出部分丢弃并截断），按 UTF-8 解码 */
    private static String readCapped(java.io.InputStream in, int max) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while (buf.size() < max && (n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, Math.min(n, max - buf.size()));
        }
        return buf.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String botError(String format, String body) {
        if (format != null && !"GENERIC".equals(format) && !body.isBlank()) {
            try {
                JsonNode n = this.objectMapper.readTree(body);
                JsonNode code = n.has("errcode") ? n.get("errcode") : (n.has("code") ? n.get("code") : n.get("StatusCode"));
                if (code != null && code.asInt(0) != 0) {
                    JsonNode msg = n.has("errmsg") ? n.get("errmsg") : n.get("msg");
                    return "机器人返回错误 " + code.asText() + (msg == null ? "" : "：" + msg.asText());
                }
            } catch (Exception var6) {
            }

            return null;
        } else {
            return null;
        }
    }

    private void applyResult(WebhookDelivery d, WebhookEndpoint ep, WebhookService.Attempt a) {
        LocalDateTime now = LocalDateTime.now();
        int attempts = (d.getAttempts() == null ? 0 : d.getAttempts()) + 1;
        LocalDateTime next = null;
        String status;
        if (a.ok()) {
            status = "SUCCESS";
        } else if (attempts < MAX_ATTEMPTS && !"ping".equals(d.getEventType())) {
            status = "RETRY";
            // 修复：固定间隔退避会让同一时刻失败的大量端点/多条投递在完全相同的时刻集体重试
            // （同步重试风暴），对目标服务瞬时压力成倍放大；加 ±20% 随机抖动把各重试时刻打散
            long baseMs = BACKOFF[attempts - 1].toMillis();
            next = now.plus(Duration.ofMillis((long)((double)baseMs * (0.8D + ThreadLocalRandom.current().nextDouble(0.4D)))));
        } else {
            status = "DEAD";
        }

        WebhookDelivery upd = new WebhookDelivery();
        upd.setId(d.getId());
        upd.setStatus(status);
        upd.setAttempts(attempts);
        upd.setResponseCode(a.code());
        upd.setResponseSnippet(a.snippet());
        upd.setLastError(a.error());
        upd.setDurationMs(a.ms());
        upd.setNextAttemptAt(next == null ? now : next);
        upd.setDeliveredAt(a.ok() ? now : null);
        upd.setUpdatedAt(now);
        this.deliveryMapper.updateById(upd);
        if (ep != null) {
            int failures = a.ok() ? 0 : (ep.getConsecutiveFailures() == null ? 0 : ep.getConsecutiveFailures()) + 1;
            boolean disable = !a.ok() && failures >= this.autoDisableThreshold && Boolean.TRUE.equals(ep.getEnabled());
            this.endpointMapper
                .update(
                    null,
                    ((((Wrappers.<WebhookEndpoint>lambdaUpdate()
                                        .eq(WebhookEndpoint::getId, ep.getId()))
                                    .set(WebhookEndpoint::getLastStatus, a.ok() ? "SUCCESS" : "FAILED"))
                                .set(WebhookEndpoint::getLastDeliveryAt, now))
                            .set(WebhookEndpoint::getConsecutiveFailures, failures))
                        .set(disable, WebhookEndpoint::getEnabled, false)
                );
            if (disable) {
                log.warn("[webhook] 端点 #{} {} 连续失败 {} 次，已自动停用", new Object[]{ep.getId(), ep.getName(), failures});

                try {
                    this.notificationPort
                        .notifyRole(
                            "ADMIN",
                            "WEBHOOK",
                            "Webhook 已自动停用",
                            "端点「" + ep.getName() + "」连续投递失败 " + failures + " 次，已自动停用，请检查目标服务后手动启用。",
                            String.valueOf(ep.getId())
                        );
                } catch (RuntimeException var12) {
                    log.warn("[webhook] 停用通知发送失败：{}", var12.toString());
                }
            }
        }
    }

    public WebhookDelivery ping(WebhookEndpoint ep, String actor) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message", "这是一条来自 DCIM 平台的 Webhook 连通性测试");
        DomainEvent ev = DomainEvent.of("ping", "webhook", String.valueOf(ep.getId()), actor, data);
        WebhookDelivery d = new WebhookDelivery();
        d.setEndpointId(ep.getId());
        Map<String, Object> env = this.envelope(ev);
        d.setEventId(String.valueOf(env.get("id")));
        d.setEventType("ping");
        d.setPayload(env);
        d.setStatus("PENDING");
        d.setAttempts(0);
        // 修复：此前设为 +1 年，若同步投递阶段崩溃该行会永久停留 PENDING 且永不清理。
        // 改为 5 分钟后到期，processDue 会接手把它标记 DEAD，随 purge 正常清理
        d.setNextAttemptAt(LocalDateTime.now().plusMinutes(5L));
        this.deliveryMapper.insert(d);
        return this.deliverNow(d.getId());
    }

    public void redeliver(WebhookDelivery d) {
        this.deliveryMapper
            .update(
                null,
                ((((Wrappers.<WebhookDelivery>lambdaUpdate()
                                    .eq(WebhookDelivery::getId, d.getId()))
                                .set(WebhookDelivery::getStatus, "PENDING"))
                            .set(WebhookDelivery::getAttempts, 0))
                        .set(WebhookDelivery::getNextAttemptAt, LocalDateTime.now()))
                    .set(WebhookDelivery::getUpdatedAt, LocalDateTime.now())
            );
    }

    public int purge(int days) {
        return this.deliveryMapper.purgeOlderThan(days);
    }

    public List<String> validateEventTypes(List<String> types) {
        List<String> out = new ArrayList<>();

        for (String t : types) {
            String v = t == null ? "" : t.trim();
            if (!"*".equals(v) && !EventTypes.isKnown(v)) {
                throw BusinessException.badRequest("不支持的事件类型：" + v);
            }

            if (!out.contains(v)) {
                out.add(v);
            }
        }

        if (out.isEmpty()) {
            throw BusinessException.badRequest("至少订阅一个事件类型");
        } else {
            return out;
        }
    }

    private static int elapsed(long start) {
        return (int)((System.nanoTime() - start) / 1000000L);
    }

    static record Attempt(boolean ok, Integer code, String snippet, String error, int ms) {
        static WebhookService.Attempt fail(Integer code, String error, int ms) {
            return new WebhookService.Attempt(false, code, null, error, ms);
        }
    }
}
