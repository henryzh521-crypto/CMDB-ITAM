package com.dcim.monitoring.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.monitoring.entity.MaintenanceWindow;
import com.dcim.monitoring.mapper.MaintenanceWindowMapper;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
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
@RequestMapping({"/api/maintenance-windows"})
public class MaintenanceWindowController {
    private final MaintenanceWindowMapper mapper;
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size, @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        LambdaQueryWrapper<MaintenanceWindow> q = Wrappers.lambdaQuery();
        LocalDateTime now = LocalDateTime.now();
        if (activeOnly) {
            (q.le(MaintenanceWindow::getStartsAt, now)).gt(MaintenanceWindow::getEndsAt, now);
        }

        q.orderByDesc(MaintenanceWindow::getStartsAt);
        Page<MaintenanceWindow> p = (Page<MaintenanceWindow>)this.mapper.selectPage(Page.of(page, Math.min(size, 200L)), q);
        List<Map<String, Object>> rows = new ArrayList<>();

        for (MaintenanceWindow w : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.getId());
            m.put("name", w.getName());
            m.put("startsAt", w.getStartsAt());
            m.put("endsAt", w.getEndsAt());
            m.put("assetNos", w.getAssetNos());
            m.put("rackCodes", w.getRackCodes());
            m.put("roomCodes", w.getRoomCodes());
            m.put("alertnames", w.getAlertnames());
            m.put("reason", w.getReason());
            m.put("createdBy", w.getCreatedBy());
            m.put("state", now.isBefore(w.getStartsAt()) ? "PLANNED" : (now.isBefore(w.getEndsAt()) ? "ACTIVE" : "ENDED"));
            rows.add(m);
        }

        return ApiResult.ok(new PageResult<>(p.getTotal(), rows));
    }

    @GetMapping({"/changes"})
    @RequirePerm(
        module = "monitoring"
    )
    public ApiResult<List<Map<String, Object>>> activeChanges() {
        return ApiResult.ok(
            this.jdbc
                .queryForList(
                    "SELECT c.id, c.cr_no AS \"crNo\", c.title, c.status, c.scheduled_start AS \"scheduledStart\",\n       c.scheduled_end AS \"scheduledEnd\", COUNT(ca.id) AS \"assetCount\"\nFROM change_request c LEFT JOIN change_asset ca ON ca.cr_id = c.id\nWHERE c.deleted_at IS NULL AND (c.status = 'IN_PROGRESS'\n   OR (c.status = 'APPROVED' AND c.scheduled_start <= NOW() AND c.scheduled_end > NOW()))\nGROUP BY c.id ORDER BY c.scheduled_start\n"
                )
        );
    }

    @PostMapping
    @RequirePerm(
        module = "monitoring",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "monitoring",
        objType = "maintenance_window",
        objIdSpEL = "#req.name()"
    )
    public ApiResult<MaintenanceWindow> create(@RequestBody MaintenanceWindowController.WindowReq req) {
        LoginUser u = CurrentUser.get();
        MaintenanceWindow w = new MaintenanceWindow();
        this.apply(w, req);
        w.setCreatedBy(u == null ? null : u.username());
        this.mapper.insert(w);
        return ApiResult.ok((MaintenanceWindow)this.mapper.selectById(w.getId()));
    }

    @PutMapping({"/{id}"})
    @RequirePerm(
        module = "monitoring",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "monitoring",
        objType = "maintenance_window",
        objIdSpEL = "#id"
    )
    public ApiResult<MaintenanceWindow> update(@PathVariable Long id, @RequestBody MaintenanceWindowController.WindowReq req) {
        MaintenanceWindow w = this.require(id);
        this.apply(w, req);
        this.mapper.updateById(w);
        return ApiResult.ok((MaintenanceWindow)this.mapper.selectById(id));
    }

    @PostMapping({"/{id}/end"})
    @RequirePerm(
        module = "monitoring",
        write = true,
        action = "update"
    )
    @Audit(
        action = "END",
        module = "monitoring",
        objType = "maintenance_window",
        objIdSpEL = "#id"
    )
    public ApiResult<MaintenanceWindow> end(@PathVariable Long id) {
        MaintenanceWindow w = this.require(id);
        LocalDateTime now = LocalDateTime.now();
        if (!now.isBefore(w.getEndsAt())) {
            throw BusinessException.badRequest("维护窗口已结束");
        } else {
            this.mapper
                .update(
                    null,
                    (Wrappers.<MaintenanceWindow>lambdaUpdate().eq(MaintenanceWindow::getId, id))
                        .set(MaintenanceWindow::getEndsAt, now.isAfter(w.getStartsAt()) ? now : w.getStartsAt().plusSeconds(1L))
                );
            return ApiResult.ok((MaintenanceWindow)this.mapper.selectById(id));
        }
    }

    @DeleteMapping({"/{id}"})
    @RequirePerm(
        module = "monitoring",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "monitoring",
        objType = "maintenance_window",
        objIdSpEL = "#id"
    )
    public ApiResult<Void> delete(@PathVariable Long id) {
        this.require(id);
        this.mapper.deleteById(id);
        return ApiResult.ok();
    }

    private MaintenanceWindow require(Long id) {
        MaintenanceWindow w = (MaintenanceWindow)this.mapper.selectById(id);
        if (w == null) {
            throw BusinessException.notFound("维护窗口不存在");
        } else {
            return w;
        }
    }

    private void apply(MaintenanceWindow w, MaintenanceWindowController.WindowReq r) {
        if (r.name() == null || r.name().isBlank() || r.name().length() > 200) {
            throw BusinessException.badRequest("名称不能为空且不超过 200 字");
        } else if (r.startsAt() == null || r.endsAt() == null || !r.endsAt().isAfter(r.startsAt())) {
            throw BusinessException.badRequest("请填写有效的开始/结束时间（结束须晚于开始）");
        } else if (Duration.between(r.startsAt(), r.endsAt()).toDays() > 31L) {
            throw BusinessException.badRequest("单个维护窗口不超过 31 天");
        } else {
            w.setName(r.name().trim());
            w.setStartsAt(r.startsAt());
            w.setEndsAt(r.endsAt());
            w.setAssetNos(clean(r.assetNos()));
            w.setRackCodes(clean(r.rackCodes()));
            w.setRoomCodes(clean(r.roomCodes()));
            w.setAlertnames(clean(r.alertnames()));
            w.setReason(r.reason() == null ? null : r.reason().trim());
            if (w.getAssetNos().isEmpty() && w.getRackCodes().isEmpty() && w.getRoomCodes().isEmpty() && !Boolean.TRUE.equals(r.global())) {
                throw BusinessException.badRequest("请指定维护范围（资产/机柜/机房），或明确勾选「全局维护」");
            }
        }
    }

    private static List<String> clean(List<String> in) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        if (in != null) {
            for (String v : in) {
                if (v != null && !v.isBlank()) {
                    s.add(v.trim());
                }
            }
        }

        if (s.size() > 1000) {
            throw BusinessException.badRequest("单个维护窗口范围不超过 1000 项");
        } else {
            return new ArrayList<>(s);
        }
    }

    
    public MaintenanceWindowController(final MaintenanceWindowMapper mapper, final JdbcTemplate jdbc) {
        this.mapper = mapper;
        this.jdbc = jdbc;
    }

    public static record WindowReq(
        String name,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        List<String> assetNos,
        List<String> rackCodes,
        List<String> roomCodes,
        List<String> alertnames,
        String reason,
        Boolean global
    ) {
    }
}
