package com.dcim.monitoring.controller;

import com.dcim.common.ApiResult;
import com.dcim.monitoring.service.AlertIngestService;
import com.dcim.monitoring.service.MonitoringSettings;
import com.dcim.monitoring.service.ServiceDiscoveryService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/monitoring"})
public class MonitoringIngestController {
    private static final Pattern JOB = Pattern.compile("^[a-z0-9_\\-]{1,30}$");
    private static final Pattern SOURCE = Pattern.compile("^[a-z0-9_\\-]{1,30}$");
    private final ServiceDiscoveryService sd;
    private final AlertIngestService ingest;
    private final MonitoringSettings settings;

    @GetMapping({"/sd/{job}"})
    public ResponseEntity<?> discover(
        @PathVariable String job, HttpServletRequest req, @RequestParam(defaultValue = "1") int shards, @RequestParam(defaultValue = "0") int shard
    ) {
        ResponseEntity<?> denied = this.authorize(req);
        if (denied != null) {
            return denied;
        } else if (!JOB.matcher(job).matches() || shards < 1 || shards > 64 || shard < 0 || shard >= shards) {
            return ResponseEntity.badRequest().body(ApiResult.error(400, "参数非法"));
        } else {
            ServiceDiscoveryService.SdPayload p = this.sd.cachedPayload(job, shards, shard);
            String ae = req.getHeader("Accept-Encoding");
            boolean gzip = ae != null && ae.toLowerCase(java.util.Locale.ROOT).contains("gzip");
            org.springframework.http.ResponseEntity.BodyBuilder b = ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header("X-SD-Groups", String.valueOf(p.groups()))
                .header("Vary", "Accept-Encoding");
            if (gzip) {
                b.header("Content-Encoding", "gzip");
            }
            return b.body(gzip ? p.gzip() : p.json());
        }
    }

    @PostMapping({"/ingest/alertmanager"})
    public ResponseEntity<?> alertmanager(@RequestBody(required = false) Map<String, Object> payload, HttpServletRequest req) {
        ResponseEntity<?> denied = this.authorize(req);
        if (denied != null) {
            return denied;
        } else {
            AlertIngestService.Result r = this.ingest.ingestAlertmanager(payload);
            return ResponseEntity.ok(ApiResult.ok(r));
        }
    }

    @PostMapping({"/ingest/generic"})
    public ResponseEntity<?> generic(
        @RequestBody(required = false) Map<String, Object> payload, @RequestParam(defaultValue = "generic") String source, HttpServletRequest req
    ) {
        ResponseEntity<?> denied = this.authorize(req);
        if (denied != null) {
            return denied;
        } else {
            String src = SOURCE.matcher(source).matches() ? source : "generic";
            return ResponseEntity.ok(ApiResult.ok(this.ingest.ingestGeneric(payload, src)));
        }
    }

    @GetMapping({"/sd"})
    public ResponseEntity<?> jobs(HttpServletRequest req) {
        ResponseEntity<?> denied = this.authorize(req);
        return denied != null ? denied : ResponseEntity.ok(ApiResult.ok(Map.of("jobs", this.sd.jobs())));
    }

    private ResponseEntity<?> authorize(HttpServletRequest req) {
        String expected = this.settings.ingestToken();
        if (expected != null && !expected.isBlank()) {
            String h = req.getHeader("Authorization");
            String token = h != null && h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null;
            return token != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))
                ? null
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResult.error(401, "监控接入令牌无效"));
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResult.error(503, "未配置监控接入令牌 dcim.monitoring.token"));
        }
    }

    
    public MonitoringIngestController(final ServiceDiscoveryService sd, final AlertIngestService ingest, final MonitoringSettings settings) {
        this.sd = sd;
        this.ingest = ingest;
        this.settings = settings;
    }
}
