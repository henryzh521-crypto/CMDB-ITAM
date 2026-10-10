package com.dcim.inspection.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.common.notify.NotificationPort;
import com.dcim.common.seq.SeqNoGenerator;
import com.dcim.inspection.entity.InspectionPlan;
import com.dcim.inspection.mapper.InspectionPlanMapper;
import com.dcim.security.ActorContext;
import com.dcim.security.LoginUser;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.service.WorkorderService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InspectionService {
    
    private static final Logger log = LoggerFactory.getLogger(InspectionService.class);
    public static final Set<String> FREQUENCIES = Set.of("DAILY", "WEEKLY", "MONTHLY");
    public static final Set<String> SCOPES = Set.of("ROOM", "RACK");
    private static final Set<String> PRIORITIES = Set.of("高", "中", "低");
    private final InspectionPlanMapper planMapper;
    private final WorkorderService workorderService;
    private final ActorContext actor;
    private final SeqNoGenerator seq;
    private final NotificationPort notify;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    /** 手动触发防抖：planId → 最近一次触发时间戳（毫秒），内存实现，集群下双实例各限一份可接受 */
    private final ConcurrentHashMap<Long, Long> manualRunAt = new ConcurrentHashMap<>();

    public InspectionService(
        InspectionPlanMapper planMapper,
        WorkorderService workorderService,
        ActorContext actor,
        SeqNoGenerator seq,
        NotificationPort notify,
        JdbcTemplate jdbc,
        TransactionTemplate tx
    ) {
        this.planMapper = planMapper;
        this.workorderService = workorderService;
        this.actor = actor;
        this.seq = seq;
        this.notify = notify;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public InspectionPlan create(InspectionService.PlanReq req, LoginUser user) {
        InspectionPlan p = new InspectionPlan();
        this.apply(p, req, true);
        p.setPlanNo((String)this.tx.execute(s -> this.seq.next("IP", 4)));
        p.setCreatedBy(user.username());
        p.setNextRunAt(Boolean.TRUE.equals(p.getEnabled()) ? this.nextOf(p, LocalDateTime.now()) : null);
        this.planMapper.insert(p);
        return (InspectionPlan)this.planMapper.selectById(p.getId());
    }

    public InspectionPlan update(Long id, InspectionService.PlanReq req) {
        InspectionPlan p = this.require(id);
        this.apply(p, req, false);
        p.setNextRunAt(Boolean.TRUE.equals(p.getEnabled()) ? this.nextOf(p, LocalDateTime.now()) : null);
        p.setUpdatedAt(LocalDateTime.now());
        this.planMapper.updateById(p);
        if (!Boolean.TRUE.equals(p.getEnabled())) {
            this.planMapper
                .update(null, (Wrappers.<InspectionPlan>lambdaUpdate().eq(InspectionPlan::getId, id)).set(InspectionPlan::getNextRunAt, null));
        }

        return (InspectionPlan)this.planMapper.selectById(id);
    }

    public InspectionPlan require(Long id) {
        InspectionPlan p = (InspectionPlan)this.planMapper.selectById(id);
        if (p == null) {
            throw BusinessException.notFound("巡检计划不存在");
        } else {
            return p;
        }
    }

    private void apply(InspectionPlan p, InspectionService.PlanReq r, boolean creating) {
        if (creating || r.name() != null) {
            if (r.name() == null || r.name().isBlank() || r.name().length() > 200) {
                throw BusinessException.badRequest("计划名称不能为空且不超过 200 字");
            }

            p.setName(r.name().trim());
        }

        if (creating || r.scopeType() != null) {
            String st = r.scopeType() == null ? "ROOM" : r.scopeType().trim().toUpperCase();
            if (!SCOPES.contains(st)) {
                throw BusinessException.badRequest("巡检范围类型须为 ROOM / RACK");
            }

            p.setScopeType(st);
        }

        if (creating || r.scopeCodes() != null) {
            List<String> codes = new ArrayList<>(
                new LinkedHashSet<>(
                    (r.scopeCodes() == null ? List.<String>of() : r.scopeCodes()).stream().filter(cx -> cx != null && !cx.isBlank()).map(String::trim).toList()
                )
            );
            if (codes.isEmpty()) {
                throw BusinessException.badRequest("请至少选择一个巡检对象");
            }

            String sql = "ROOM".equals(p.getScopeType())
                ? "SELECT COUNT(*) FROM machine_room WHERE room_code = ?"
                : "SELECT COUNT(*) FROM rack WHERE rack_code = ?";

            for (String c : codes) {
                Integer n = (Integer)this.jdbc.queryForObject(sql, Integer.class, new Object[]{c});
                if (n == null || n == 0) {
                    throw BusinessException.badRequest(("ROOM".equals(p.getScopeType()) ? "机房" : "机柜") + "不存在：" + c);
                }
            }

            p.setScopeCodes(codes);
        }

        if (creating || r.frequency() != null) {
            String f = r.frequency() == null ? "WEEKLY" : r.frequency().trim().toUpperCase();
            if (!FREQUENCIES.contains(f)) {
                throw BusinessException.badRequest("巡检频率须为 DAILY / WEEKLY / MONTHLY");
            }

            p.setFrequency(f);
        }

        if (creating || r.runHour() != null) {
            int h = r.runHour() == null ? 9 : r.runHour();
            if (h < 0 || h > 23) {
                throw BusinessException.badRequest("执行时刻须在 0-23 点之间");
            }

            p.setRunHour(h);
        }

        if (r.runWeekday() != null) {
            if (r.runWeekday() < 1 || r.runWeekday() > 7) {
                throw BusinessException.badRequest("周几须在 1-7 之间");
            }

            p.setRunWeekday(r.runWeekday());
        }

        if (r.runMonthDay() != null) {
            if (r.runMonthDay() < 1 || r.runMonthDay() > 28) {
                throw BusinessException.badRequest("每月执行日须在 1-28 之间（避开大小月差异）");
            }

            p.setRunMonthDay(r.runMonthDay());
        }

        if ("WEEKLY".equals(p.getFrequency()) && p.getRunWeekday() == null) {
            p.setRunWeekday(1);
        }

        if ("MONTHLY".equals(p.getFrequency()) && p.getRunMonthDay() == null) {
            p.setRunMonthDay(1);
        }

        if (creating || r.checklist() != null) {
            List<Object> items = new ArrayList<>();

            for (String s : r.checklist() == null ? List.<String>of() : r.checklist()) {
                if (s != null && !s.isBlank()) {
                    if (s.length() > 200) {
                        throw BusinessException.badRequest("单个检查项不超过 200 字");
                    }

                    items.add(s.trim());
                }
            }

            if (items.size() > 100) {
                throw BusinessException.badRequest("检查项不超过 100 条");
            }

            p.setChecklist(items);
        }

        if (creating || r.priority() != null) {
            String pr = r.priority() != null && !r.priority().isBlank() ? r.priority().trim() : "中";
            if (!PRIORITIES.contains(pr)) {
                throw BusinessException.badRequest("优先级仅支持 高/中/低");
            }

            p.setPriority(pr);
        }

        if (creating || r.assignee() != null) {
            String a = r.assignee() != null && !r.assignee().isBlank() ? r.assignee().trim() : null;
            if (a != null) {
                Integer n = (Integer)this.jdbc
                    .queryForObject("SELECT COUNT(*) FROM users WHERE username = ? AND status = 1 AND deleted_at IS NULL", Integer.class, new Object[]{a});
                if (n == null || n == 0) {
                    throw BusinessException.badRequest("执行人不存在或已停用：" + a);
                }
            }

            p.setAssignee(a);
        }

        if (creating || r.enabled() != null) {
            p.setEnabled(r.enabled() == null || r.enabled());
        }
    }

    private LocalDateTime nextOf(InspectionPlan p, LocalDateTime after) {
        return InspectionSchedule.next(p.getFrequency(), p.getRunHour(), p.getRunWeekday(), p.getRunMonthDay(), after);
    }

    public int runDue() {
        int count = 0;

        for (int round = 0; round < 20; round++) {
            List<Long> ids = (List<Long>)this.tx
                .execute(
                    s -> {
                        List<Long> locked = this.planMapper.lockDue(10);
                        if (!locked.isEmpty()) {
                            this.planMapper
                                .update(
                                    null,
                                    (Wrappers.<InspectionPlan>lambdaUpdate().in(InspectionPlan::getId, locked))
                                        .set(InspectionPlan::getNextRunAt, LocalDateTime.now().plusMinutes(10L))
                                );
                        }

                        return locked;
                    }
                );
            if (ids == null || ids.isEmpty()) {
                break;
            }

            for (Long id : ids) {
                try {
                    this.tx.executeWithoutResult(s -> this.generate((InspectionPlan)this.planMapper.selectById(id), "SCHEDULE"));
                    count++;
                } catch (RuntimeException var8) {
                    log.warn("[inspection] 计划 #{} 生成工单失败：{}", id, var8.getMessage());
                    InspectionPlan p = (InspectionPlan)this.planMapper.selectById(id);
                    if (p != null) {
                        this.planMapper
                            .update(
                                null,
                                (Wrappers.<InspectionPlan>lambdaUpdate().eq(InspectionPlan::getId, id))
                                    .set(InspectionPlan::getNextRunAt, this.nextOf(p, LocalDateTime.now()))
                            );
                    }
                }
            }
        }

        return count;
    }

    public String runNow(Long id) {
        this.require(id);
        // 手动触发防抖：同一计划 60 秒内重复触发返回 409；生成失败不占用防抖窗口
        long now = System.currentTimeMillis();
        Long prev = this.manualRunAt.put(id, now);
        if (prev != null && now - prev < 60_000L) {
            this.manualRunAt.put(id, prev);
            throw BusinessException.conflict("该巡检计划 60 秒内已手动触发过，请稍后再试");
        }

        try {
            return (String)this.tx.execute(s -> {
                InspectionPlan p = this.require(id);
                return this.generate(p, "MANUAL");
            });
        } catch (RuntimeException e) {
            this.manualRunAt.remove(id);
            throw e;
        }
    }

    private String generate(InspectionPlan p, String trigger) {
        LocalDateTime now = LocalDateTime.now();
        String title = "【巡检】" + p.getName() + " " + LocalDate.now();
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        StringBuilder desc = new StringBuilder();
        desc.append("巡检计划：").append(p.getPlanNo()).append(" ").append(p.getName()).append('\n');
        desc.append("巡检范围：").append("ROOM".equals(p.getScopeType()) ? "机房 " : "机柜 ").append(String.join("、", p.getScopeCodes())).append('\n');
        if (p.getAssignee() != null) {
            desc.append("执行人：").append(p.getAssignee()).append('\n');
        }

        List<Object> items = p.getChecklist() == null ? List.of() : p.getChecklist();
        if (!items.isEmpty()) {
            desc.append("\n检查清单：\n");
            int i = 1;

            for (Object it : items) {
                desc.append(i++).append(". □ ").append(it).append('\n');
            }
        }

        String finalTitle = title;
        String woNo = this.runAs(
            p.getCreatedBy(),
            () -> {
                WorkorderEntity wo = this.workorderService
                    .create(
                        "巡检",
                        finalTitle,
                        desc.toString(),
                        p.getPriority(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        this.expectFinish(p),
                        null,
                        null,
                        null,
                        null,
                        null
                    );
                return wo.getWoNo();
            }
        );
        this.jdbc.update("INSERT INTO inspection_run (plan_id, wo_no, run_at, trigger) VALUES (?, ?, ?, ?)", new Object[]{p.getId(), woNo, now, trigger});
        LambdaUpdateWrapper<InspectionPlan> upd = (LambdaUpdateWrapper<InspectionPlan>)((Wrappers.<InspectionPlan>lambdaUpdate()
                    .eq(InspectionPlan::getId, p.getId()))
                .set(InspectionPlan::getLastRunAt, now))
            .set(InspectionPlan::getLastWoNo, woNo);
        if ("SCHEDULE".equals(trigger)) {
            upd.set(InspectionPlan::getNextRunAt, this.nextOf(p, now));
        }

        this.planMapper.update(null, upd);
        if (p.getAssignee() != null) {
            try {
                this.notify.notifyUser(p.getAssignee(), "INSPECTION", "新的巡检任务", "巡检计划「" + p.getName() + "」已生成工单 " + woNo + "，请按清单完成巡检", woNo);
            } catch (RuntimeException var11) {
                log.warn("[inspection] 通知执行人失败：{}", var11.getMessage());
            }
        }

        log.info("[inspection] 计划 {} 生成巡检工单 {}（{}）", new Object[]{p.getPlanNo(), woNo, trigger});
        return woNo;
    }

    private LocalDate expectFinish(InspectionPlan p) {
        String var2 = p.getFrequency();

        return switch (var2) {
            case "DAILY" -> LocalDate.now();
            case "WEEKLY" -> LocalDate.now().plusDays(2L);
            default -> LocalDate.now().plusDays(5L);
        };
    }

    private <T> T runAs(String username, Supplier<T> body) {
        return this.actor.runAs(username, body);
    }

    public List<Map<String, Object>> runs(Long planId, int limit) {
        return this.jdbc
            .queryForList(
                "SELECT r.wo_no AS \"woNo\", r.run_at AS \"runAt\", r.trigger, w.status, w.title\nFROM inspection_run r LEFT JOIN workorder w ON w.wo_no = r.wo_no\nWHERE r.plan_id = ? ORDER BY r.run_at DESC LIMIT ?\n",
                new Object[]{planId, Math.max(1, Math.min(limit, 100))}
            );
    }

    public static record PlanReq(
        String name,
        String scopeType,
        List<String> scopeCodes,
        String frequency,
        Integer runHour,
        Integer runWeekday,
        Integer runMonthDay,
        List<String> checklist,
        String priority,
        String assignee,
        Boolean enabled
    ) {
    }
}
