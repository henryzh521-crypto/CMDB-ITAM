package com.dcim.webhook.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.crypto.CryptBox;
import com.dcim.common.event.EventTypes;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.webhook.entity.WebhookDelivery;
import com.dcim.webhook.entity.WebhookEndpoint;
import com.dcim.webhook.mapper.WebhookDeliveryMapper;
import com.dcim.webhook.mapper.WebhookEndpointMapper;
import com.dcim.webhook.service.WebhookPayloadRenderer;
import com.dcim.webhook.service.WebhookService;
import com.dcim.webhook.service.WebhookUrlGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/webhooks"})
public class WebhookController {
    private final WebhookEndpointMapper endpointMapper;
    private final WebhookDeliveryMapper deliveryMapper;
    private final WebhookService webhookService;
    private final WebhookUrlGuard urlGuard;
    private final CryptBox cryptBox;

    @GetMapping
    @RequirePerm(
        module = "system"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size) {
        Page<WebhookEndpoint> p = (Page<WebhookEndpoint>)this.endpointMapper
            .selectPage(Page.of(page, Math.min(size, 200L)), Wrappers.<WebhookEndpoint>lambdaQuery().orderByDesc(WebhookEndpoint::getId));
        List<Map<String, Object>> rows = p.getRecords().stream().map(WebhookController::toRow).toList();
        return ApiResult.ok(new PageResult<>(p.getTotal(), rows));
    }

    @GetMapping({"/meta"})
    @RequirePerm(
        module = "system"
    )
    public ApiResult<Map<String, Object>> meta() {
        List<Map<String, String>> events = new ArrayList<>();
        EventTypes.CATALOG.forEach((k, v) -> events.add(Map.of("value", k, "label", v)));
        List<Map<String, String>> formats = List.of(
            Map.of("value", "GENERIC", "label", "通用 JSON（HMAC-SHA256 签名）"),
            Map.of("value", "WECOM_BOT", "label", "企业微信群机器人"),
            Map.of("value", "DINGTALK_BOT", "label", "钉钉群机器人"),
            Map.of("value", "FEISHU_BOT", "label", "飞书群机器人")
        );
        return ApiResult.ok(Map.of("eventTypes", events, "formats", formats));
    }

    @PostMapping
    @RequirePerm(
        module = "system",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "system",
        objType = "webhook",
        objIdSpEL = "#req.name()"
    )
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid WebhookController.CreateReq req) {
        LoginUser user = this.requireAdmin();
        WebhookEndpoint ep = new WebhookEndpoint();
        ep.setName(req.name().trim());
        ep.setUrl(this.urlGuard.check(req.url()).toString());
        ep.setFormat(this.format(req.format()));
        ep.setSecret(this.encryptSecret(req.secret()));
        ep.setEventTypes(this.webhookService.validateEventTypes(req.eventTypes()));
        ep.setEnabled(req.enabled() == null || req.enabled());
        ep.setConsecutiveFailures(0);
        ep.setCreatedBy(user.username());
        this.endpointMapper.insert(ep);
        return ApiResult.ok(toRow((WebhookEndpoint)this.endpointMapper.selectById(ep.getId())));
    }

    @PutMapping({"/{id}"})
    @RequirePerm(
        module = "system",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "system",
        objType = "webhook",
        objIdSpEL = "#id"
    )
    @Transactional
    public ApiResult<Map<String, Object>> update(@PathVariable Long id, @RequestBody @Valid WebhookController.UpdateReq req) {
        this.requireAdmin();
        WebhookEndpoint ep = this.requireEndpoint(id);
        if (req.name() != null) {
            if (req.name().isBlank()) {
                throw BusinessException.badRequest("名称不能为空");
            }

            ep.setName(req.name().trim());
        }

        if (req.url() != null) {
            ep.setUrl(this.urlGuard.check(req.url()).toString());
        }

        if (req.format() != null) {
            ep.setFormat(this.format(req.format()));
        }

        if (Boolean.TRUE.equals(req.clearSecret())) {
            ep.setSecret(null);
        } else if (req.secret() != null && !req.secret().isBlank()) {
            ep.setSecret(this.encryptSecret(req.secret()));
        }

        if (req.eventTypes() != null) {
            ep.setEventTypes(this.webhookService.validateEventTypes(req.eventTypes()));
        }

        if (req.enabled() != null) {
            ep.setEnabled(req.enabled());
            if (req.enabled()) {
                ep.setConsecutiveFailures(0);
            }
        }

        ep.setUpdatedAt(LocalDateTime.now());
        this.endpointMapper.updateById(ep);
        if (Boolean.TRUE.equals(req.clearSecret())) {
            this.endpointMapper
                .update(null, (Wrappers.<WebhookEndpoint>lambdaUpdate().eq(WebhookEndpoint::getId, id)).set(WebhookEndpoint::getSecret, null));
        }

        return ApiResult.ok(toRow((WebhookEndpoint)this.endpointMapper.selectById(id)));
    }

    @DeleteMapping({"/{id}"})
    @RequirePerm(
        module = "system",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "system",
        objType = "webhook",
        objIdSpEL = "#id"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable Long id) {
        this.requireAdmin();
        this.requireEndpoint(id);
        this.endpointMapper.deleteById(id);
        return ApiResult.ok();
    }

    @PostMapping({"/{id}/test"})
    @RequirePerm(
        module = "system",
        write = true,
        action = "update"
    )
    @Audit(
        action = "TEST",
        module = "system",
        objType = "webhook",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> test(@PathVariable Long id) {
        LoginUser user = this.requireAdmin();
        WebhookEndpoint ep = this.requireEndpoint(id);
        WebhookDelivery d = this.webhookService.ping(ep, user.username());
        return ApiResult.ok(this.deliveryRow(d));
    }

    @GetMapping({"/{id}/deliveries"})
    @RequirePerm(
        module = "system"
    )
    public ApiResult<PageResult<Map<String, Object>>> deliveries(
        @PathVariable Long id,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status
    ) {
        this.requireEndpoint(id);
        LambdaQueryWrapper<WebhookDelivery> qw = (LambdaQueryWrapper<WebhookDelivery>)Wrappers.<WebhookDelivery>lambdaQuery().eq(WebhookDelivery::getEndpointId, id);
        if (status != null && !status.isBlank()) {
            qw.eq(WebhookDelivery::getStatus, status.trim());
        }

        qw.orderByDesc(WebhookDelivery::getId);
        Page<WebhookDelivery> p = (Page<WebhookDelivery>)this.deliveryMapper.selectPage(Page.of(page, Math.min(size, 100L)), qw);
        return ApiResult.ok(new PageResult<>(p.getTotal(), p.getRecords().stream().map(this::deliveryRow).toList()));
    }

    @PostMapping({"/deliveries/{deliveryId}/redeliver"})
    @RequirePerm(
        module = "system",
        write = true,
        action = "update"
    )
    @Audit(
        action = "REDELIVER",
        module = "system",
        objType = "webhook_delivery",
        objIdSpEL = "#deliveryId"
    )
    public ApiResult<Void> redeliver(@PathVariable Long deliveryId) {
        this.requireAdmin();
        WebhookDelivery d = (WebhookDelivery)this.deliveryMapper.selectById(deliveryId);
        if (d == null) {
            throw BusinessException.notFound("投递记录不存在");
        } else {
            this.webhookService.redeliver(d);
            return ApiResult.ok();
        }
    }

    private LoginUser requireAdmin() {
        LoginUser u = CurrentUser.get();
        if (u != null && u.isAdmin()) {
            return u;
        } else {
            throw BusinessException.forbidden("仅管理员可配置 Webhook");
        }
    }

    private WebhookEndpoint requireEndpoint(Long id) {
        WebhookEndpoint ep = (WebhookEndpoint)this.endpointMapper.selectById(id);
        if (ep == null) {
            throw BusinessException.notFound("Webhook 端点不存在");
        } else {
            return ep;
        }
    }

    private String format(String f) {
        String v = f != null && !f.isBlank() ? f.trim().toUpperCase() : "GENERIC";
        if (!WebhookPayloadRenderer.isKnownFormat(v)) {
            throw BusinessException.badRequest("不支持的推送格式：" + f);
        } else {
            return v;
        }
    }

    private String encryptSecret(String secret) {
        if (secret != null && !secret.isBlank()) {
            if (secret.length() > 200) {
                throw BusinessException.badRequest("签名密钥过长（上限 200 字符）");
            } else {
                return this.cryptBox.encrypt(secret.trim());
            }
        } else {
            return null;
        }
    }

    static Map<String, Object> toRow(WebhookEndpoint e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("name", e.getName());
        row.put("url", maskUrl(e.getUrl()));
        row.put("format", e.getFormat());
        row.put("eventTypes", e.getEventTypes());
        row.put("enabled", e.getEnabled());
        row.put("hasSecret", e.getSecret() != null && !e.getSecret().isBlank());
        row.put("lastStatus", e.getLastStatus());
        row.put("lastDeliveryAt", e.getLastDeliveryAt());
        row.put("consecutiveFailures", e.getConsecutiveFailures());
        row.put("createdBy", e.getCreatedBy());
        row.put("createdAt", e.getCreatedAt());
        return row;
    }

    static String maskUrl(String url) {
        return url == null ? null : url.replaceAll("(?i)((?:key|access_token|token|hook)[=/])([A-Za-z0-9_\\-]{4})[A-Za-z0-9_\\-]+", "$1$2****");
    }

    private Map<String, Object> deliveryRow(WebhookDelivery d) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (d == null) {
            return row;
        } else {
            row.put("id", d.getId());
            row.put("eventId", d.getEventId());
            row.put("eventType", d.getEventType());
            row.put("status", d.getStatus());
            row.put("attempts", d.getAttempts());
            row.put("responseCode", d.getResponseCode());
            row.put("responseSnippet", d.getResponseSnippet());
            row.put("lastError", d.getLastError());
            row.put("durationMs", d.getDurationMs());
            row.put("nextAttemptAt", d.getNextAttemptAt());
            row.put("deliveredAt", d.getDeliveredAt());
            row.put("createdAt", d.getCreatedAt());
            row.put("payload", d.getPayload());
            return row;
        }
    }

    
    public WebhookController(
        final WebhookEndpointMapper endpointMapper,
        final WebhookDeliveryMapper deliveryMapper,
        final WebhookService webhookService,
        final WebhookUrlGuard urlGuard,
        final CryptBox cryptBox
    ) {
        this.endpointMapper = endpointMapper;
        this.deliveryMapper = deliveryMapper;
        this.webhookService = webhookService;
        this.urlGuard = urlGuard;
        this.cryptBox = cryptBox;
    }

    public static record CreateReq(
        @NotBlank @Size(max = 100) String name, @NotBlank String url, String format, String secret, @NotEmpty List<String> eventTypes, Boolean enabled
    ) {
    }

    public static record UpdateReq(
        @Size(max = 100) String name, String url, String format, String secret, Boolean clearSecret, List<String> eventTypes, Boolean enabled
    ) {
    }
}
