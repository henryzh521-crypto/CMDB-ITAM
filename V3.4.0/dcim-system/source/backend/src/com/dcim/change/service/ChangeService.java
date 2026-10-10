package com.dcim.change.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.change.entity.ChangeAsset;
import com.dcim.change.entity.ChangeRequest;
import com.dcim.change.mapper.ChangeAssetMapper;
import com.dcim.change.mapper.ChangeRequestMapper;
import com.dcim.ci.service.CiGraphService;
import com.dcim.common.BusinessException;
import com.dcim.common.event.DomainEvent;
import com.dcim.common.event.DomainEventPublisher;
import com.dcim.common.notify.NotificationPort;
import com.dcim.common.seq.SeqNoGenerator;
import com.dcim.security.LoginUser;
import com.dcim.security.RbacService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChangeService {
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_CAB = "PENDING_CAB";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";
    public static final Set<String> TYPES = Set.of("STANDARD", "NORMAL", "EMERGENCY");
    public static final Set<String> RISKS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> ACTIVE = Set.of("PENDING_CAB", "APPROVED", "IN_PROGRESS");
    private static final Map<String, String> STATUS_LABEL = Map.of(
        "DRAFT",
        "草稿",
        "PENDING_CAB",
        "待CAB审批",
        "APPROVED",
        "已批准",
        "REJECTED",
        "已驳回",
        "IN_PROGRESS",
        "实施中",
        "COMPLETED",
        "已完成",
        "FAILED",
        "失败已回退",
        "CANCELLED",
        "已取消"
    );
    private final ChangeRequestMapper crMapper;
    private final ChangeAssetMapper caMapper;
    private final SeqNoGenerator seq;
    private final RbacService rbac;
    private final CiGraphService ciGraph;
    private final NotificationPort notify;
    private final DomainEventPublisher events;
    private final JdbcTemplate jdbc;

    public ChangeService(
        ChangeRequestMapper crMapper,
        ChangeAssetMapper caMapper,
        SeqNoGenerator seq,
        RbacService rbac,
        CiGraphService ciGraph,
        NotificationPort notify,
        DomainEventPublisher events,
        JdbcTemplate jdbc
    ) {
        this.crMapper = crMapper;
        this.caMapper = caMapper;
        this.seq = seq;
        this.rbac = rbac;
        this.ciGraph = ciGraph;
        this.notify = notify;
        this.events = events;
        this.jdbc = jdbc;
    }

    public static String label(String status) {
        return STATUS_LABEL.getOrDefault(status, status);
    }

    @Transactional
    public ChangeRequest create(ChangeService.Draft d, LoginUser user) {
        ChangeRequest cr = new ChangeRequest();
        this.apply(cr, d, true);
        cr.setCrNo(this.seq.next("CR-" + SeqNoGenerator.todaySegment(), 4));
        cr.setStatus("DRAFT");
        cr.setRequester(user.username());
        this.crMapper.insert(cr);
        this.replaceAssets(cr.getId(), d.assetNos());
        return (ChangeRequest)this.crMapper.selectById(cr.getId());
    }

    @Transactional
    public ChangeRequest update(Long id, ChangeService.Draft d, LoginUser user) {
        ChangeRequest cr = this.require(id);
        this.requireOwnerOrAdmin(cr, user, "编辑");
        if (!"DRAFT".equals(cr.getStatus()) && !"REJECTED".equals(cr.getStatus())) {
            throw BusinessException.badRequest("仅草稿或已驳回的变更可编辑（当前：" + label(cr.getStatus()) + "）");
        } else {
            this.apply(cr, d, false);
            cr.setUpdatedAt(LocalDateTime.now());
            this.crMapper.updateById(cr);
            if (d.assetNos() != null) {
                this.replaceAssets(id, d.assetNos());
            }

            return (ChangeRequest)this.crMapper.selectById(id);
        }
    }

    @Transactional
    public ChangeRequest submit(Long id, LoginUser user) {
        ChangeRequest cr = this.require(id);
        this.requireOwnerOrAdmin(cr, user, "提交");
        if (!"DRAFT".equals(cr.getStatus()) && !"REJECTED".equals(cr.getStatus())) {
            throw BusinessException.badRequest("只有草稿或已驳回的变更可以提交");
        } else if ("STANDARD".equals(cr.getChangeType()) || cr.getScheduledStart() != null && cr.getScheduledEnd() != null) {
            if (!"HIGH".equals(cr.getRiskLevel()) && !"CRITICAL".equals(cr.getRiskLevel()) || cr.getRollbackPlan() != null && !cr.getRollbackPlan().isBlank()) {
                if ("STANDARD".equals(cr.getChangeType())) {
                    this.transit(
                        cr,
                        cr.getStatus(),
                        "APPROVED",
                        user,
                        u -> (((u.set(ChangeRequest::getApprovedBy, "STANDARD"))
                                        .set(ChangeRequest::getApprovedAt, LocalDateTime.now()))
                                    .set(ChangeRequest::getCabApproval, true))
                                .set(ChangeRequest::getCabComment, "标准变更（预授权）自动批准")
                    );
                } else {
                    this.transit(
                        cr,
                        cr.getStatus(),
                        "PENDING_CAB",
                        user,
                        u -> (u.set(ChangeRequest::getCabApproval, null)).set(ChangeRequest::getCabComment, null)
                    );
                    this.safeNotifyRole("ADMIN", "变更待审批", "变更 " + cr.getCrNo() + "「" + cr.getTitle() + "」已提交，等待 CAB 审批", cr.getCrNo());
                }

                return (ChangeRequest)this.crMapper.selectById(id);
            } else {
                throw BusinessException.badRequest("高风险/极高风险变更必须填写回退方案");
            }
        } else {
            throw BusinessException.badRequest("常规/紧急变更提交前须填写计划实施窗口");
        }
    }

    @Transactional
    public ChangeRequest review(Long id, boolean approved, String comment, LoginUser user) {
        ChangeRequest cr = this.require(id);
        if (!"PENDING_CAB".equals(cr.getStatus())) {
            throw BusinessException.badRequest("只有待 CAB 审批的变更可以审批");
        } else {
            this.rbac.assertAction(user, "changes", "approve");
            if (!this.rbac.isAdmin(user) && user.username().equals(cr.getRequester())) {
                throw BusinessException.forbidden("职责分离：不能审批自己提交的变更");
            } else if (approved || comment != null && !comment.isBlank()) {
                List<String> approvers = new ArrayList<>(cr.getCabApprovers() == null ? List.of() : cr.getCabApprovers());
                if (!approvers.contains(user.username())) {
                    approvers.add(user.username());
                }

                LocalDateTime now = LocalDateTime.now();
                this.transit(
                    cr,
                    "PENDING_CAB",
                    approved ? "APPROVED" : "REJECTED",
                    user,
                    u -> ((((u.set(ChangeRequest::getCabApproval, approved))
                                        .set(ChangeRequest::getCabComment, comment == null ? null : comment.trim()))
                                    .set(ChangeRequest::getApprovedBy, user.username()))
                                .set(ChangeRequest::getApprovedAt, now))
                            .set(ChangeRequest::getCabApprovers, approvers, "typeHandler=com.dcim.common.StringArrayTypeHandler")
                );
                this.safeNotifyUser(
                    cr.getRequester(),
                    approved ? "变更已批准" : "变更被驳回",
                    "变更 " + cr.getCrNo() + "「" + cr.getTitle() + "」" + (approved ? "已批准，可按计划实施" : "被驳回：" + comment),
                    cr.getCrNo()
                );
                return (ChangeRequest)this.crMapper.selectById(id);
            } else {
                throw BusinessException.badRequest("驳回须填写意见");
            }
        }
    }

    @Transactional
    public ChangeRequest start(Long id, LoginUser user) {
        ChangeRequest cr = this.require(id);
        if (!"APPROVED".equals(cr.getStatus())) {
            throw BusinessException.badRequest("只有已批准的变更可以开始实施");
        } else {
            this.transit(cr, "APPROVED", "IN_PROGRESS", user, u -> u.set(ChangeRequest::getActualStart, LocalDateTime.now()));
            return (ChangeRequest)this.crMapper.selectById(id);
        }
    }

    @Transactional
    public ChangeRequest finish(Long id, boolean success, String note, LoginUser user) {
        ChangeRequest cr = this.require(id);
        if (!"IN_PROGRESS".equals(cr.getStatus())) {
            throw BusinessException.badRequest("只有实施中的变更可以关闭");
        } else if (success || note != null && !note.isBlank()) {
            this.transit(
                cr,
                "IN_PROGRESS",
                success ? "COMPLETED" : "FAILED",
                user,
                u -> (u.set(ChangeRequest::getActualEnd, LocalDateTime.now()))
                        .set(ChangeRequest::getCloseNote, note == null ? null : note.trim())
            );
            this.safeNotifyUser(
                cr.getRequester(),
                success ? "变更已完成" : "变更失败已回退",
                "变更 " + cr.getCrNo() + "「" + cr.getTitle() + "」" + (success ? "已完成" : "失败：" + note),
                cr.getCrNo()
            );
            return (ChangeRequest)this.crMapper.selectById(id);
        } else {
            throw BusinessException.badRequest("变更失败须说明失败原因与回退情况");
        }
    }

    @Transactional
    public ChangeRequest cancel(Long id, String reason, LoginUser user) {
        ChangeRequest cr = this.require(id);
        this.requireOwnerOrAdmin(cr, user, "取消");
        Set<String> cancellable = Set.of("DRAFT", "PENDING_CAB", "APPROVED", "REJECTED");
        if (!cancellable.contains(cr.getStatus())) {
            throw BusinessException.badRequest("当前状态「" + label(cr.getStatus()) + "」不可取消");
        } else {
            this.transit(cr, cr.getStatus(), "CANCELLED", user, u -> u.set(ChangeRequest::getCancelReason, reason == null ? null : reason.trim()));
            return (ChangeRequest)this.crMapper.selectById(id);
        }
    }

    @Transactional
    public void delete(Long id, LoginUser user) {
        ChangeRequest cr = this.require(id);
        if (!this.rbac.canDelete(user, "changes")) {
            throw BusinessException.forbidden("当前角色未授予「变更」的删除权限");
        } else if (!"DRAFT".equals(cr.getStatus()) && !"CANCELLED".equals(cr.getStatus())) {
            throw BusinessException.badRequest("仅草稿或已取消的变更可删除，其余请保留审计轨迹");
        } else {
            // v3.5：软删主记录时同步清理 change_asset 关联行，避免悬挂孤儿数据
            this.caMapper.delete(Wrappers.<ChangeAsset>lambdaQuery().eq(ChangeAsset::getCrId, id));
            this.crMapper.deleteById(id);
        }
    }

    public ChangeRequest require(Long id) {
        ChangeRequest cr = (ChangeRequest)this.crMapper.selectById(id);
        if (cr == null) {
            throw BusinessException.notFound("变更请求不存在");
        } else {
            return cr;
        }
    }

    public List<String> assetsOf(Long crId) {
        return this.caMapper
            .selectList((Wrappers.<ChangeAsset>lambdaQuery().eq(ChangeAsset::getCrId, crId)).orderByAsc(ChangeAsset::getAssetNo))
            .stream()
            .map(ChangeAsset::getAssetNo)
            .toList();
    }

    public List<Map<String, Object>> conflicts(ChangeRequest cr) {
        return cr.getScheduledStart() != null && cr.getScheduledEnd() != null
            ? this.jdbc
                .queryForList(
                    "SELECT DISTINCT c.id, c.cr_no AS \"crNo\", c.title, c.status,\n       c.scheduled_start AS \"scheduledStart\", c.scheduled_end AS \"scheduledEnd\"\nFROM change_request c\nJOIN change_asset ca ON ca.cr_id = c.id\nWHERE c.deleted_at IS NULL AND c.id <> ?\n  AND c.status IN ('PENDING_CAB', 'APPROVED', 'IN_PROGRESS')\n  AND c.scheduled_start < ? AND c.scheduled_end > ?\n  AND ca.asset_no IN (SELECT asset_no FROM change_asset WHERE cr_id = ?)\nORDER BY \"scheduledStart\"\n",
                    new Object[]{cr.getId(), cr.getScheduledEnd(), cr.getScheduledStart(), cr.getId()}
                )
            : List.of();
    }

    public Map<String, Object> impact(List<String> assetNos) {
        Map<String, CiGraphService.Node> affected = new LinkedHashMap<>();
        Set<String> self = new LinkedHashSet<>();

        for (String a : assetNos) {
            self.add("ASSET:" + a);
        }

        for (String a : assetNos) {
            try {
                CiGraphService.Graph g = this.ciGraph.traverse("ASSET", a, true, 4);

                for (CiGraphService.Node n : g.nodes()) {
                    if (!self.contains(n.key())) {
                        affected.putIfAbsent(n.key(), n);
                    }
                }
            } catch (BusinessException var9) {
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", affected.size());
        out.put("nodes", new ArrayList<>(affected.values()));
        return out;
    }

    private void apply(ChangeRequest cr, ChangeService.Draft d, boolean creating) {
        if (creating || d.title() != null) {
            if (d.title() == null || d.title().isBlank()) {
                throw BusinessException.badRequest("标题不能为空");
            }

            cr.setTitle(d.title().trim());
        }

        if (creating || d.changeType() != null) {
            String t = d.changeType() == null ? "NORMAL" : d.changeType().trim().toUpperCase();
            if (!TYPES.contains(t)) {
                throw BusinessException.badRequest("变更类型须为 STANDARD / NORMAL / EMERGENCY");
            }

            cr.setChangeType(t);
        }

        if (creating || d.riskLevel() != null) {
            String r = d.riskLevel() != null && !d.riskLevel().isBlank() ? d.riskLevel().trim().toUpperCase() : "MEDIUM";
            if (!RISKS.contains(r)) {
                throw BusinessException.badRequest("风险等级须为 LOW / MEDIUM / HIGH / CRITICAL");
            }

            cr.setRiskLevel(r);
        }

        if (d.description() != null || creating) {
            cr.setDescription(trimToNull(d.description()));
        }

        if (d.impactScope() != null || creating) {
            cr.setImpactScope(trimToNull(d.impactScope()));
        }

        if (d.rollbackPlan() != null || creating) {
            cr.setRollbackPlan(trimToNull(d.rollbackPlan()));
        }

        if (d.scheduledStart() != null || creating) {
            cr.setScheduledStart(d.scheduledStart());
        }

        if (d.scheduledEnd() != null || creating) {
            cr.setScheduledEnd(d.scheduledEnd());
        }

        if (cr.getScheduledStart() != null && cr.getScheduledEnd() != null && !cr.getScheduledEnd().isAfter(cr.getScheduledStart())) {
            throw BusinessException.badRequest("计划结束时间必须晚于开始时间");
        }
    }

    private void replaceAssets(Long crId, List<String> assetNos) {
        if (assetNos != null) {
            Set<String> uniq = new LinkedHashSet<>();

            for (String a : assetNos) {
                if (a != null && !a.isBlank()) {
                    uniq.add(a.trim());
                }
            }

            if (uniq.size() > 500) {
                throw BusinessException.badRequest("单个变更关联资产不超过 500 台");
            } else {
                for (String ax : uniq) {
                    Integer n = (Integer)this.jdbc
                        .queryForObject("SELECT COUNT(*) FROM asset WHERE asset_no = ? AND deleted_at IS NULL", Integer.class, new Object[]{ax});
                    if (n == null || n == 0) {
                        throw BusinessException.badRequest("关联资产不存在：" + ax);
                    }
                }

                this.caMapper.delete(Wrappers.<ChangeAsset>lambdaQuery().eq(ChangeAsset::getCrId, crId));

                for (String axx : uniq) {
                    ChangeAsset ca = new ChangeAsset();
                    ca.setCrId(crId);
                    ca.setAssetNo(axx);
                    this.caMapper.insert(ca);
                }
            }
        }
    }

    private void transit(ChangeRequest cr, String expect, String target, LoginUser user, Consumer<LambdaUpdateWrapper<ChangeRequest>> extra) {
        LambdaUpdateWrapper<ChangeRequest> u = (LambdaUpdateWrapper<ChangeRequest>)(((Wrappers.<ChangeRequest>lambdaUpdate()
                        .eq(ChangeRequest::getId, cr.getId()))
                    .eq(ChangeRequest::getStatus, expect))
                .set(ChangeRequest::getStatus, target))
            .set(ChangeRequest::getUpdatedAt, LocalDateTime.now());
        extra.accept(u);
        int rows = this.crMapper.update(null, u);
        if (rows == 0) {
            throw BusinessException.conflict("变更状态已被他人更新，请刷新后重试");
        } else {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("crNo", cr.getCrNo());
            data.put("title", cr.getTitle());
            data.put("changeType", cr.getChangeType());
            data.put("riskLevel", cr.getRiskLevel());
            data.put("fromStatus", expect);
            data.put("toStatus", target);
            this.events.publish(DomainEvent.of("change.status_changed", "change", cr.getCrNo(), user.username(), data));
        }
    }

    private void requireOwnerOrAdmin(ChangeRequest cr, LoginUser user, String op) {
        if (!this.rbac.isAdmin(user) && !user.username().equals(cr.getRequester())) {
            throw BusinessException.forbidden("仅申请人或管理员可" + op + "该变更");
        }
    }

    private void safeNotifyUser(String username, String title, String content, String ref) {
        try {
            this.notify.notifyUser(username, "CHANGE", title, content, ref);
        } catch (RuntimeException var6) {
        }
    }

    private void safeNotifyRole(String role, String title, String content, String ref) {
        try {
            this.notify.notifyRole(role, "CHANGE", title, content, ref);
        } catch (RuntimeException var6) {
        }
    }

    private static String trimToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    public static record Draft(
        String title,
        String description,
        String changeType,
        String riskLevel,
        String impactScope,
        String rollbackPlan,
        LocalDateTime scheduledStart,
        LocalDateTime scheduledEnd,
        List<String> assetNos
    ) {
    }
}
