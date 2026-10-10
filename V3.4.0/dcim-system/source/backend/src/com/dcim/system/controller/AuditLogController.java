package com.dcim.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.csv.CsvSupport;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RbacService;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.AuditRetentionService;
import com.dcim.system.entity.AuditLogEntity;
import com.dcim.system.mapper.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/audit-logs"})
public class AuditLogController {
    private static final int MAX_PAGE_SIZE = 200;
    private static final int EXPORT_LIMIT = 10000;
    private static final List<String> CSV_HEADERS = List.of("时间", "操作人", "动作", "模块", "对象类型", "对象ID", "IP", "详情");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AuditLogMapper auditLogMapper;
    private final RbacService rbacService;
    private final AuditRetentionService auditRetentionService;

    @GetMapping
    @RequirePerm(
        module = "system"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String module,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String objType,
        @RequestParam(required = false) String objId,
        @RequestParam(required = false) String ip,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String timeFrom,
        @RequestParam(required = false) String timeTo
    ) {
        this.requireAdmin();
        Page<AuditLogEntity> p = new Page(Math.max(1L, page), clampSize(size));
        this.auditLogMapper.selectPage(p, buildWrapper(actor, module, action, objType, objId, ip, keyword, timeFrom, timeTo));
        return ApiResult.ok(PageResult.of(p, AuditLogController::toRow));
    }

    @GetMapping({"/options"})
    @RequirePerm(
        module = "system"
    )
    public ApiResult<Map<String, Object>> options() {
        this.requireAdmin();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("modules", this.auditLogMapper.distinctModules());
        out.put("actions", this.auditLogMapper.distinctActions());
        out.put("objTypes", this.auditLogMapper.distinctObjTypes());
        out.put("actors", this.auditLogMapper.distinctActors());
        return ApiResult.ok(out);
    }

    @GetMapping({"/export"})
    @RequirePerm(
        module = "system",
        action = "export"
    )
    public ResponseEntity<byte[]> export(
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String module,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String objType,
        @RequestParam(required = false) String objId,
        @RequestParam(required = false) String ip,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String timeFrom,
        @RequestParam(required = false) String timeTo
    ) {
        this.requireAdmin();
        Page<AuditLogEntity> p = new Page(1L, 10000L, false);
        this.auditLogMapper.selectPage(p, buildWrapper(actor, module, action, objType, objId, ip, keyword, timeFrom, timeTo));
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(CSV_HEADERS)).append('\n');

        for (AuditLogEntity e : p.getRecords()) {
            List<Object> cells = Arrays.asList(
                e.getCreatedAt(),
                e.getActor(),
                e.getAction(),
                e.getModule(),
                nullToEmpty(e.getObjType()),
                nullToEmpty(e.getObjId()),
                nullToEmpty(e.getIp()),
                detailJson(e.getDetail())
            );
            csv.append(CsvSupport.line(cells)).append('\n');
        }

        String filename = "操作日志-" + LocalDate.now() + ".csv";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ((BodyBuilder)ResponseEntity.ok().header("Content-Disposition", new String[]{"attachment; filename*=UTF-8''" + encoded}))
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping({"/retention"})
    @RequirePerm(
        module = "system"
    )
    public ApiResult<Map<String, Object>> retention() {
        this.requireAdmin();
        return ApiResult.ok(this.auditRetentionService.run());
    }

    private void requireAdmin() {
        LoginUser user = CurrentUser.get();
        if (!this.rbacService.isAdmin(user)) {
            throw BusinessException.forbidden("仅管理员可查看操作日志");
        }
    }

    private static long clampSize(long size) {
        return size < 1L ? 20L : Math.min(size, 200L);
    }

    private static LambdaQueryWrapper<AuditLogEntity> buildWrapper(
        String actor, String module, String action, String objType, String objId, String ip, String keyword, String timeFrom, String timeTo
    ) {
        LambdaQueryWrapper<AuditLogEntity> qw = (LambdaQueryWrapper<AuditLogEntity>)(((((Wrappers.<AuditLogEntity>lambdaQuery()
                                .eq(!isBlank(actor), AuditLogEntity::getActor, trimToNull(actor)))
                            .eq(!isBlank(module), AuditLogEntity::getModule, trimToNull(module)))
                        .eq(!isBlank(action), AuditLogEntity::getAction, trimToNull(action)))
                    .eq(!isBlank(objType), AuditLogEntity::getObjType, trimToNull(objType)))
                .eq(!isBlank(objId), AuditLogEntity::getObjId, trimToNull(objId)))
            .eq(!isBlank(ip), AuditLogEntity::getIp, trimToNull(ip));
        LocalDateTime from = parseTime(timeFrom, false);
        if (from != null) {
            qw.ge(AuditLogEntity::getCreatedAt, from);
        }

        LocalDateTime to = parseTime(timeTo, true);
        if (to != null) {
            qw.le(AuditLogEntity::getCreatedAt, to);
        }

        String kw = trimToNull(keyword);
        if (kw != null) {
            String like = "%" + kw + "%";
            qw.and(
                w -> ((((w.like(AuditLogEntity::getActor, like)).or())
                                .like(AuditLogEntity::getObjId, like))
                            .or())
                        .like(AuditLogEntity::getObjType, like)
            );
        }

        return (LambdaQueryWrapper<AuditLogEntity>)(qw.orderByDesc(AuditLogEntity::getCreatedAt)).orderByDesc(AuditLogEntity::getId);
    }

    private static LocalDateTime parseTime(String raw, boolean endOfDay) {
        String s = trimToNull(raw);
        if (s == null) {
            return null;
        } else {
            try {
                if (s.length() == 10) {
                    LocalDate d = LocalDate.parse(s);
                    return endOfDay ? d.atTime(LocalTime.MAX) : d.atStartOfDay();
                } else {
                    return LocalDateTime.parse(s);
                }
            } catch (DateTimeParseException var4) {
                throw BusinessException.badRequest("时间格式不正确（yyyy-MM-dd 或 yyyy-MM-ddTHH:mm:ss）：" + raw);
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String detailJson(Map<String, Object> detail) {
        if (detail != null && !detail.isEmpty()) {
            try {
                return MAPPER.writeValueAsString(detail);
            } catch (Exception var2) {
                return "";
            }
        } else {
            return "";
        }
    }

    private static Map<String, Object> toRow(AuditLogEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("actor", e.getActor());
        row.put("action", e.getAction());
        row.put("module", e.getModule());
        row.put("objType", e.getObjType());
        row.put("objId", e.getObjId());
        row.put("ip", e.getIp());
        row.put("detail", e.getDetail());
        row.put("createdAt", e.getCreatedAt());
        return row;
    }

    
    public AuditLogController(final AuditLogMapper auditLogMapper, final RbacService rbacService, final AuditRetentionService auditRetentionService) {
        this.auditLogMapper = auditLogMapper;
        this.rbacService = rbacService;
        this.auditRetentionService = auditRetentionService;
    }
}
