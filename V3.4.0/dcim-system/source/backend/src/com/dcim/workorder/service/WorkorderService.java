package com.dcim.workorder.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.asset.entity.AssetEntity;
import com.dcim.asset.mapper.AssetMapper;
import com.dcim.asset.statemachine.AssetStateMachine;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.notify.NotificationPort;
import com.dcim.common.seq.SeqNoGenerator;
import com.dcim.room.entity.MachineRoomEntity;
import com.dcim.room.entity.RackEntity;
import com.dcim.room.mapper.MachineRoomMapper;
import com.dcim.room.mapper.RackMapper;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RbacService;
import com.dcim.system.entity.RoleEntity;
import com.dcim.system.entity.UserEntity;
import com.dcim.system.mapper.RoleMapper;
import com.dcim.system.mapper.UserMapper;
import com.dcim.workorder.entity.SlaConfigEntity;
import com.dcim.workorder.entity.WorkflowConfigEntity;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.entity.WorkorderFlowEntity;
import com.dcim.workorder.entity.WorkorderItemEntity;
import com.dcim.workorder.mapper.SlaConfigMapper;
import com.dcim.workorder.mapper.WorkflowConfigMapper;
import com.dcim.workorder.mapper.WorkorderFlowMapper;
import com.dcim.workorder.mapper.WorkorderItemMapper;
import com.dcim.workorder.mapper.WorkorderMapper;
import com.dcim.workorder.sla.SlaPolicy;
import com.dcim.workorder.statemachine.WorkorderStateMachine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkorderService {
    private final com.dcim.security.DataScopeService dataScope;
    static final String NOTIFY_TYPE_APPROVAL = "WO_APPROVAL";
    static final String NOTIFY_TYPE_APPROVED = "WO_APPROVED";
    static final String NOTIFY_TYPE_REJECT = "WO_REJECT";
    static final String NOTIFY_TYPE_DONE = "WO_DONE";
    static final String NOTIFY_TYPE_REMIND = "WO_REMIND";
    static final String ACT_REMIND = "催办";
    static final String ACT_COMMENT = "评论";
    private static final List<String> PRIORITIES = List.of("高", "中", "低");
    private static final String DEFAULT_PRIORITY = "中";
    private static final Set<String> SWAP_WO_TYPES = Set.of("故障维修", "部件更换");
    private static final Set<String> SWAP_OUT_STATUSES = Set.of("维修中", "停用中", "待退役");
    static final long REMIND_THROTTLE_MINUTES = 10L;
    private static final int MAX_CSV_LEN = 500;
    private static final int MAX_TITLE_LEN = 200;
    private static final int MAX_ROOT_CAUSE_LEN = 50;
    private static final BigDecimal MAX_COST = new BigDecimal("99999999.99");
    private final WorkorderMapper workorderMapper;
    private final WorkorderFlowMapper flowMapper;
    private final WorkorderItemMapper itemMapper;
    private final WorkflowConfigMapper workflowConfigMapper;
    private final SlaConfigMapper slaConfigMapper;
    private final AssetMapper assetMapper;
    private final RackMapper rackMapper;
    private final MachineRoomMapper machineRoomMapper;
    private final WoCompletionService completionService;
    private final NotificationPort notificationPort;
    private final SeqNoGenerator seqNoGenerator;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final RbacService rbac;
    static final int EXPORT_MAX_ROWS = 10000;

    @Transactional
    public WorkorderEntity create(
        String woType,
        String title,
        String description,
        String priority,
        List<WoAssetLineInput> assetLines,
        List<String> relatedMaterials,
        Integer applyQty,
        String targetLocation,
        String targetRack,
        Integer targetUnit,
        LocalDate expectFinish,
        List<Object> details,
        String parentWo,
        String replacedAsset,
        List<Object> replacements,
        List<String> approvalApprovers
    ) {
        LoginUser user = this.requireUser();
        if (woType != null && !woType.isBlank()) {
            WorkflowConfigEntity cfg = this.requireConfig(woType.trim());
            String prio = priority != null && !priority.isBlank() ? priority.trim() : "中";
            if (!PRIORITIES.contains(prio)) {
                throw BusinessException.badRequest("优先级无效，仅支持 高/中/低：" + prio);
            } else {
                WorkorderEntity wo = new WorkorderEntity();
                wo.setWoNo(this.seqNoGenerator.next("WO-" + SeqNoGenerator.todaySegment(), 4));
                wo.setWoType(cfg.getWoType());
                wo.setTitle(title.trim());
                wo.setDescription(this.blankToNull(description));
                wo.setStatus("待处理");
                wo.setPriority(prio);
                wo.setApplicant(user.name());
                wo.setApplicantUsername(user.username());
                wo.setApplyTime(LocalDateTime.now());
                wo.setExpectFinish(expectFinish);
                List<WoAssetLineInput> normalizedLines = this.normalizeAssetLines(assetLines);
                wo.setRelatedAssets(this.joinCsv(normalizedLines.stream().map(WoAssetLineInput::getAssetNo).toList()));
                wo.setRelatedMaterials(this.joinCsv(relatedMaterials));
                wo.setApplyQty(applyQty);
                wo.setTargetLocation(this.blankToNull(targetLocation));
                wo.setTargetRack(this.blankToNull(targetRack));
                wo.setTargetUnit(targetUnit);
                wo.setAutoDeducted(false);
                wo.setDetails((List<Object>)(details == null ? new ArrayList<>() : details));
                this.deriveMaterialSummary(wo);
                checkLen("工单标题", wo.getTitle(), 200);
                checkCsvLen("关联资产", wo.getRelatedAssets());
                checkCsvLen("关联物料", wo.getRelatedMaterials());
                wo.setCc(new ArrayList<>());
                wo.setAttachments(new ArrayList<>());
                String parent = this.blankToNull(parentWo);
                if (parent != null) {
                    WorkorderEntity parentWoEntity = (WorkorderEntity)this.workorderMapper
                        .selectOne(Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, parent));
                    if (parentWoEntity == null) {
                        throw BusinessException.badRequest("父工单不存在：" + parent);
                    }

                    if (!SWAP_WO_TYPES.contains(parentWoEntity.getWoType())) {
                        throw BusinessException.badRequest("父工单须为故障维修或部件更换类型：" + parent);
                    }

                    String parentStatus = parentWoEntity.getStatus();
                    if ("已完成".equals(parentStatus) || "已关闭".equals(parentStatus)) {
                        throw BusinessException.badRequest("父工单已终态（" + parentStatus + "），不能创建关联子单：" + parent);
                    }

                    wo.setParentWo(parent);
                }

                wo.setReplacedAsset(this.blankToNull(replacedAsset));
                wo.setReplacements(new ArrayList<>(this.normalizeAndValidateReplacements(wo, replacements)));
                if ("调拨".equals(cfg.getWoType())) {
                    this.validateTransferTargets(normalizedLines, targetRack);
                }

                SlaConfigEntity sla = (SlaConfigEntity)this.slaConfigMapper
                    .selectOne(
                        (Wrappers.<SlaConfigEntity>lambdaQuery().eq(SlaConfigEntity::getWoType, cfg.getWoType()))
                            .eq(SlaConfigEntity::getPriority, prio)
                    );
                LocalDateTime now = wo.getApplyTime();
                wo.setSlaResponseDeadline(SlaEscalationService.computeDeadline(now, sla == null ? null : sla.getResponseMinutes()));
                wo.setSlaResolveDeadline(SlaEscalationService.computeDeadline(now, sla == null ? null : sla.getResolveMinutes()));
                wo.setSlaEscalations(0);
                wo.setApprovalLevel(0);
                List<String> approvers = this.normalizeUserList(approvalApprovers, "指定审批人");
                if (!approvers.isEmpty()) {
                    if (!Boolean.TRUE.equals(cfg.getNeedApproval())) {
                        throw BusinessException.badRequest("该工单类型无需审批，不能指定审批人：" + cfg.getWoType());
                    }

                    for (String u : approvers) {
                        this.requireActiveUser(u);
                    }

                    List<Object> chain = new ArrayList<>();
                    chain.add(new ArrayList<>(approvers));
                    wo.setApprovalUsers(chain);
                }

                wo.setVersion(1);
                wo.setCreatedAt(now);
                wo.setUpdatedAt(now);
                this.workorderMapper.insert(wo);
                this.insertAssetItems(wo.getWoNo(), normalizedLines, targetRack, targetUnit);
                if ("调拨".equals(cfg.getWoType())) {
                    this.snapshotBeforeLocation(wo.getWoNo());
                }

                return wo;
            }
        } else {
            throw BusinessException.badRequest("工单类型（woType）不能为空");
        }
    }

    @Transactional
    public List<Map<String, Object>> updateReplacements(String woNo, List<Object> replacements) {
        WorkorderEntity wo = this.requireEditableWo(woNo);
        List<Map<String, Object>> normalized = this.normalizeAndValidateReplacements(wo, replacements);
        WorkorderEntity upd = new WorkorderEntity();
        upd.setReplacements(new ArrayList<>(normalized));
        upd.setUpdatedAt(LocalDateTime.now());
        this.workorderMapper.update(upd, Wrappers.<WorkorderEntity>lambdaUpdate().eq(WorkorderEntity::getWoNo, woNo));
        return normalized;
    }

    private List<Map<String, Object>> normalizeAndValidateReplacements(WorkorderEntity wo, List<Object> input) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (input != null && !input.isEmpty()) {
            if (!SWAP_WO_TYPES.contains(wo.getWoType())) {
                throw BusinessException.badRequest("仅故障维修/部件更换工单支持子件更换");
            } else {
                List<String> related = splitCsv(wo.getRelatedAssets());
                Set<String> oldSeen = new HashSet<>();
                Set<String> newSeen = new HashSet<>();

                for (Object e : input) {
                    if (!(e instanceof Map<?, ?> m)) {
                        throw BusinessException.badRequest("子件更换明细格式错误（须为对象）");
                    }

                    String parent = trimToNull(m.get("parentAssetNo"));
                    String oldNo = trimToNull(m.get("oldAssetNo"));
                    String newNo = trimToNull(m.get("newAssetNo"));
                    if (parent != null && oldNo != null && newNo != null) {
                        if (!related.contains(parent)) {
                            throw BusinessException.badRequest("父资产须在工单关联资产中：" + parent);
                        }

                        if (!oldSeen.add(oldNo)) {
                            throw BusinessException.conflict("子件更换明细旧件重复：" + oldNo);
                        }

                        if (!newSeen.add(newNo)) {
                            throw BusinessException.conflict("子件更换明细新件重复：" + newNo);
                        }

                        AssetEntity parentA = (AssetEntity)this.assetMapper.selectById(parent);
                        if (parentA == null) {
                            throw BusinessException.badRequest("资产不存在：" + parent);
                        }

                        AssetEntity oldA = (AssetEntity)this.assetMapper.selectById(oldNo);
                        if (oldA == null) {
                            throw BusinessException.badRequest("资产不存在：" + oldNo);
                        }

                        AssetEntity newA = (AssetEntity)this.assetMapper.selectById(newNo);
                        if (newA == null) {
                            throw BusinessException.badRequest("资产不存在：" + newNo);
                        }

                        String oldParent = oldA.getParentAssetNo();
                        if (oldParent != null && parent.equals(oldParent.trim())) {
                            String newParent = newA.getParentAssetNo();
                            if (newParent != null && !newParent.isBlank()) {
                                throw BusinessException.conflict("新件已挂载到父资产「" + newParent.trim() + "」：" + newNo);
                            }

                            String newSt = newA.getStatusBiz();
                            if (!Set.of("待上架", "停用中", "在用(计费)").contains(newSt)) {
                                throw BusinessException.conflict("新件「" + newNo + "」当前状态「" + newSt + "」不可作为换入件（仅 待上架/停用中/在用(计费)）");
                            }

                            String oldSt = oldA.getStatusBiz();
                            if (!"已报废".equals(oldSt) && !"已退租".equals(oldSt)) {
                                String oldStatus = trimToNull(m.get("oldStatus"));
                                if (oldStatus != null) {
                                    if (!SWAP_OUT_STATUSES.contains(oldStatus)) {
                                        throw BusinessException.badRequest("换下后状态「" + oldStatus + "」不合法（仅 维修中/停用中/待退役）：旧件 " + oldNo);
                                    }

                                    if (!oldStatus.equals(oldSt) && !AssetStateMachine.canTransit(oldSt, oldStatus)) {
                                        throw BusinessException.conflict("旧件「" + oldNo + "」当前状态「" + oldSt + "」不允许变更为「" + oldStatus + "」");
                                    }
                                }

                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("parentAssetNo", parent);
                                row.put("oldAssetNo", oldNo);
                                row.put("newAssetNo", newNo);
                                if (oldStatus != null) {
                                    row.put("oldStatus", oldStatus);
                                }

                                out.add(row);
                                continue;
                            }

                            throw BusinessException.conflict("旧件「" + oldNo + "」当前状态「" + oldSt + "」不可换下");
                        }

                        throw BusinessException.badRequest("旧件未挂载到该父资产：" + oldNo);
                    }

                    throw BusinessException.badRequest("子件更换明细缺少 parentAssetNo/oldAssetNo/newAssetNo");
                }

                return out;
            }
        } else {
            return out;
        }
    }

    @Transactional
    public Map<String, Object> addItem(String woNo, WoAssetLineInput line) {
        WorkorderEntity wo = this.requireEditableWo(woNo);
        if (line.getAssetNo() != null && !line.getAssetNo().isBlank()) {
            String assetNo = line.getAssetNo().trim();
            if (this.itemMapper
                    .selectCount(
                        (Wrappers.<WorkorderItemEntity>lambdaQuery().eq(WorkorderItemEntity::getWoNo, woNo))
                            .eq(WorkorderItemEntity::getAssetNo, assetNo)
                    )
                > 0L) {
                throw BusinessException.conflict("工单已关联该资产：" + assetNo);
            } else {
                int next = this.nextItemSeq(woNo);
                WorkorderItemEntity item = this.buildItem(wo, assetNo, line, next);
                this.assertItemTargetValid(wo, item);
                this.itemMapper.insert(item);
                this.syncRelatedAssetsCsv(woNo);
                return itemRow(item);
            }
        } else {
            throw BusinessException.badRequest("资产编号不能为空");
        }
    }

    @Transactional
    public Map<String, Object> updateItem(String woNo, String itemNo, WoAssetLineInput line) {
        WorkorderEntity wo = this.requireEditableWo(woNo);
        WorkorderItemEntity item = this.requireItem(woNo, itemNo);
        item.setDstRoom(this.blankToNull(line.getDstRoom()));
        item.setDstRack(this.blankToNull(line.getDstRack()));
        item.setDstUnit(line.getDstUnit());
        if (item.getDstRack() != null) {
            String room = this.resolveRoomOfRack(item.getDstRack());
            if (room == null) {
                throw BusinessException.badRequest("目标机柜不存在或未挂机房：" + item.getDstRack());
            }

            item.setDstRoom(room);
        } else if (item.getDstRoom() != null) {
            Long n = this.machineRoomMapper.selectCount(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, item.getDstRoom()));
            if (n == null || n == 0L) {
                throw BusinessException.badRequest("目标机房不存在：" + item.getDstRoom());
            }
        }

        item.setDstRegion(this.regionOfRoom(item.getDstRoom()));
        item.setUpdatedAt(LocalDateTime.now());
        this.assertItemTargetValid(wo, item);
        this.itemMapper
            .update(
                null,
                (((((Wrappers.<WorkorderItemEntity>lambdaUpdate()
                                        .eq(WorkorderItemEntity::getItemNo, itemNo))
                                    .set(WorkorderItemEntity::getDstRoom, item.getDstRoom()))
                                .set(WorkorderItemEntity::getDstRack, item.getDstRack()))
                            .set(WorkorderItemEntity::getDstUnit, item.getDstUnit()))
                        .set(WorkorderItemEntity::getDstRegion, item.getDstRegion()))
                    .set(WorkorderItemEntity::getUpdatedAt, item.getUpdatedAt())
            );
        return itemRow(item);
    }

    private void assertItemTargetValid(WorkorderEntity wo, WorkorderItemEntity item) {
        if ("调拨".equals(this.requireConfig(wo.getWoType()).getWoType())) {
            if (item.getDstRack() != null) {
                if (item.getDstRoom() == null) {
                    throw BusinessException.badRequest("调拨目标机柜不存在或未挂机房：" + item.getDstRack());
                }
            } else if (item.getDstRoom() == null) {
                throw BusinessException.badRequest("调拨必须指定目标机房（上架与到场暂存都需先到机房），资产：" + item.getAssetNo());
            } else {
                Long n = this.machineRoomMapper.selectCount(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, item.getDstRoom()));
                if (n == null || n == 0L) {
                    throw BusinessException.badRequest("调拨目标机房不存在：" + item.getDstRoom());
                }
            }
        }
    }

    @Transactional
    public void deleteItem(String woNo, String itemNo) {
        this.requireEditableWo(woNo);
        WorkorderItemEntity item = this.requireItem(woNo, itemNo);
        this.itemMapper.deleteById(item.getItemNo());
        this.syncRelatedAssetsCsv(woNo);
    }

    private WorkorderEntity requireEditableWo(String woNo) {
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo != null && !this.visibleToCurrentUser(wo)) {
            wo = null;
        }

        if (wo == null) {
            throw BusinessException.notFound("工单不存在：" + woNo);
        } else {
            String deny = this.operatorDeny(wo, this.requireConfig(wo.getWoType()), this.requireUser());
            if (deny != null) {
                throw BusinessException.forbidden(deny);
            } else {
                String st = wo.getStatus();
                if (!"已完成".equals(st) && !"已关闭".equals(st)) {
                    return wo;
                } else {
                    throw BusinessException.conflict("工单已终态（" + st + "），不能修改行项目：" + woNo);
                }
            }
        }
    }

    private WorkorderItemEntity requireItem(String woNo, String itemNo) {
        WorkorderItemEntity item = (WorkorderItemEntity)this.itemMapper
            .selectOne((Wrappers.<WorkorderItemEntity>lambdaQuery().eq(WorkorderItemEntity::getWoNo, woNo)).eq(WorkorderItemEntity::getItemNo, itemNo));
        if (item == null) {
            throw BusinessException.notFound("工单行不存在：" + itemNo);
        } else {
            return item;
        }
    }

    private int nextItemSeq(String woNo) {
        int max = 0;

        for (WorkorderItemEntity e : this.itemMapper
            .selectList(Wrappers.<WorkorderItemEntity>lambdaQuery().select(WorkorderItemEntity::getItemNo).eq(WorkorderItemEntity::getWoNo, woNo))) {
            String no = e.getItemNo();
            int dash = no == null ? -1 : no.lastIndexOf(45);
            if (dash >= 0 && dash != no.length() - 1) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(dash + 1).trim()));
                } catch (NumberFormatException var8) {
                }
            }
        }

        return max + 1;
    }

    private void syncRelatedAssetsCsv(String woNo) {
        List<String> assetNos = this.itemMapper
            .selectList((Wrappers.<WorkorderItemEntity>lambdaQuery().eq(WorkorderItemEntity::getWoNo, woNo)).orderByAsc(WorkorderItemEntity::getItemNo))
            .stream()
            .map(WorkorderItemEntity::getAssetNo)
            .toList();
        String csv = this.joinCsv(assetNos);
        checkCsvLen("关联资产", csv);
        // v3.3.1：显式 set（可为 NULL）。原写法用实体更新，MyBatis-Plus 默认跳过 null 字段——删掉最后一条资产行后
        // related_assets 不会被清空，详情页仍显示已删除的资产。
        this.workorderMapper
            .update(
                null,
                ((Wrappers.<WorkorderEntity>lambdaUpdate().eq(WorkorderEntity::getWoNo, woNo)).set(WorkorderEntity::getRelatedAssets, csv))
                    .set(WorkorderEntity::getUpdatedAt, LocalDateTime.now())
            );
    }

    public PageResult<Map<String, Object>> list(WoListQuery q) {
        LambdaQueryWrapper<WorkorderEntity> qw = this.buildListWrapper(q, this.requireUser());
        (qw.orderByDesc(WorkorderEntity::getApplyTime)).orderByDesc(WorkorderEntity::getWoNo);
        Page<WorkorderEntity> p = new Page(q.getPage(), q.getSize());
        this.workorderMapper.selectPage(p, qw);
        return PageResult.of(p, this::toRow);
    }

    public List<Map<String, Object>> exportRows(WoListQuery q) {
        LambdaQueryWrapper<WorkorderEntity> qw = this.buildListWrapper(q, this.requireUser());
        (qw.orderByDesc(WorkorderEntity::getApplyTime)).orderByDesc(WorkorderEntity::getWoNo);
        return this.workorderMapper.selectList(qw.last("LIMIT 10000")).stream().map(this::toRow).toList();
    }

    private LambdaQueryWrapper<WorkorderEntity> buildListWrapper(WoListQuery q, LoginUser user) {
        LambdaQueryWrapper<WorkorderEntity> qw = Wrappers.lambdaQuery();
        String status = this.blankToNull(q.getStatus());
        String keyword = this.blankToNull(q.getKeyword());
        String tab = this.blankToNull(q.getTab());
        if ("approve".equals(tab)) {
            if (status != null && !"待审批".equals(status)) {
                qw.apply("FALSE", new Object[0]);
                return qw;
            }

            qw.eq(WorkorderEntity::getStatus, "待审批");
            if (!user.isAdmin()) {
                String username = user.username();
                List<Entry<String, Integer>> approvable = this.approvableTypeLevels(user);
                qw.and(
                    root -> {
                        if (!approvable.isEmpty()) {
                            root.or(
                                branch -> {
                                    branch.isNull(WorkorderEntity::getApprovalUsers);
                                    branch.apply(
                                        "(approval_delegate IS NULL OR (approval_delegate->>'mode') IS DISTINCT FROM 'transfer' OR (approval_delegate->>'level')::int IS DISTINCT FROM approval_level)",
                                        new Object[0]
                                    );
                                    branch.and(
                                        pairs -> {
                                            for (Entry<String, Integer> e : approvable) {
                                                pairs.or(
                                                    p -> (p.eq(WorkorderEntity::getWoType, e.getKey()))
                                                            .eq(WorkorderEntity::getApprovalLevel, e.getValue())
                                                );
                                            }
                                        }
                                    );
                                }
                            );
                        }

                        root.or(
                            branch -> {
                                branch.apply("(jsonb_exists(approval_users -> approval_level, {0}))", new Object[]{username});
                                branch.or(
                                    b -> b.apply(
                                            "(jsonb_exists(approval_delegate -> 'members', {0}) AND (approval_delegate->>'level')::int = approval_level)",
                                            new Object[]{username}
                                        )
                                );
                            }
                        );
                    }
                );
            }
        } else if (status != null) {
            qw.eq(WorkorderEntity::getStatus, status);
        }

        String woType = this.blankToNull(q.getWoType());
        if (woType != null) {
            qw.eq(WorkorderEntity::getWoType, woType);
        }

        String priority = this.blankToNull(q.getPriority());
        if (priority != null) {
            qw.eq(WorkorderEntity::getPriority, priority);
        }

        String handler = this.blankToNull(q.getHandler());
        if (handler != null) {
            qw.like(WorkorderEntity::getHandler, handler);
        }

        LocalDate from = parseFilterDate("申请时间起", q.getApplyFrom());
        if (from != null) {
            qw.ge(WorkorderEntity::getApplyTime, from.atStartOfDay());
        }

        LocalDate to = parseFilterDate("申请时间止", q.getApplyTo());
        if (to != null) {
            qw.le(WorkorderEntity::getApplyTime, to.plusDays(1L).atStartOfDay().minusNanos(1L));
        }

        this.applySlaFilter(qw, q.getSlaOverdue());
        if (!"approve".equals(tab)) {
            // 审批页签已按“可审批”过滤，不再叠加数据范围（否则按角色审批的人看不到待办）
            this.dataScope.applyWorkorderScope(qw, user);
        }
        if (keyword != null) {
            qw.and(
                w -> ((((w.like(WorkorderEntity::getWoNo, keyword)).or())
                                .like(WorkorderEntity::getTitle, keyword))
                            .or())
                        .like(WorkorderEntity::getApplicant, keyword)
            );
        }

        return qw;
    }

    private void applySlaFilter(LambdaQueryWrapper<WorkorderEntity> qw, Boolean slaOverdue) {
        if (slaOverdue != null) {
            LocalDateTime now = LocalDateTime.now();
            if (Boolean.TRUE.equals(slaOverdue)) {
                ((qw.isNotNull(WorkorderEntity::getSlaResolveDeadline)).lt(WorkorderEntity::getSlaResolveDeadline, now))
                    .notIn(WorkorderEntity::getStatus, SlaPolicy.SLA_EXCLUDED_STATES);
            } else {
                qw.and(
                    w -> ((((w.isNull(WorkorderEntity::getSlaResolveDeadline))
                                        .or())
                                    .ge(WorkorderEntity::getSlaResolveDeadline, now))
                                .or())
                            .in(WorkorderEntity::getStatus, SlaPolicy.SLA_EXCLUDED_STATES)
                );
            }
        }
    }

    private static LocalDate parseFilterDate(String label, String s) {
        if (s != null && !s.isBlank()) {
            try {
                return LocalDate.parse(s.trim());
            } catch (DateTimeParseException var3) {
                throw BusinessException.badRequest(label + "格式错误，应为 yyyy-MM-dd：" + s.trim());
            }
        } else {
            return null;
        }
    }

    public Map<String, Object> detail(String woNo) {
        LoginUser user = this.requireUser();
        WorkorderEntity wo = this.requireWo(woNo);
        Map<String, Object> row = this.toRow(wo);
        List<WorkorderFlowEntity> flows = this.flowMapper
            .selectList((Wrappers.<WorkorderFlowEntity>lambdaQuery().eq(WorkorderFlowEntity::getWoNo, woNo)).orderByAsc(WorkorderFlowEntity::getSeq));
        row.put("flows", flows.stream().map(WorkorderService::flowRow).toList());
        WorkflowConfigEntity cfg = this.requireConfig(wo.getWoType());
        boolean needApproval = Boolean.TRUE.equals(cfg.getNeedApproval());
        List<String> roleCodes = this.rolesAsStrings(cfg);
        List<List<String>> customChain = this.customChain(wo);
        List<List<String>> cfgUserLevels = this.configUserLevels(cfg);
        row.put("needApproval", needApproval);
        row.put("approvalRoles", roleCodes);
        row.put("approvalRoleNames", this.roleNameList(roleCodes));
        row.put("customApprovers", customChain);
        row.put("customApproverNames", this.nestedDisplayNames(customChain));
        row.put("approvalUserLevels", cfgUserLevels);
        row.put("approvalUserLevelNames", this.nestedDisplayNames(cfgUserLevels));
        row.put("delegate", this.delegateInfo(wo, cfg));
        row.put("currentLevelName", this.currentLevelName(wo, cfg, roleCodes));
        List<String> actions = WorkorderStateMachine.actionsOf(wo.getStatus(), needApproval);
        row.put("actions", actions);
        row.put("myActions", actions.stream().map(a -> this.actionMeta(wo, cfg, user, a, needApproval)).toList());
        row.put("perm", this.permOf(wo, cfg, user));
        row.put("childWos", this.childWosOf(woNo));
        List<Map<String, Object>> items = this.itemRowsOf(woNo);
        row.put("items", items);
        if ("调拨".equals(wo.getWoType())) {
            row.put("transferSummary", this.transferSummary(wo, items));
        }

        return row;
    }

    private String currentLevelName(WorkorderEntity wo, WorkflowConfigEntity cfg, List<String> roleCodes) {
        int level = wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel();
        Map<String, Object> delegate = this.delegateOf(wo);
        if (delegate != null && level == asInt(delegate.get("level"), -1)) {
            List<String> members = this.stringList(delegate.get("members"));
            if ("transfer".equals(delegate.get("mode"))) {
                return "已转交给「" + String.join("、", this.displayNames(members)) + "」";
            }
        }

        List<List<String>> custom = this.customChain(wo);
        if (!custom.isEmpty()) {
            return level >= 0 && level < custom.size() ? "指定审批人「" + String.join("、", this.displayNames(custom.get(level))) + "」" : null;
        } else {
            List<List<String>> cfgUsers = this.configUserLevels(cfg);
            String role = level >= 0 && level < roleCodes.size() ? this.roleName(roleCodes.get(level)) : null;
            List<String> users = level >= 0 && level < cfgUsers.size() ? cfgUsers.get(level) : List.of();
            if (role == null && users.isEmpty()) {
                return null;
            } else if (role != null && users.isEmpty()) {
                return role;
            } else {
                return role == null
                    ? "指定审批人「" + String.join("、", this.displayNames(users)) + "」"
                    : role + " 或指定审批人「" + String.join("、", this.displayNames(users)) + "」";
            }
        }
    }

    private Map<String, Object> actionMeta(WorkorderEntity wo, WorkflowConfigEntity cfg, LoginUser user, String action, boolean needApproval) {
        String from = wo.getStatus();
        boolean requiresOpinion = "驳回".equals(action);
        boolean requiresRootCause = "关闭".equals(action) && "处理中".equals(from);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", action);
        m.put("danger", isDangerAction(action));
        m.put("requiresOpinion", requiresOpinion);
        m.put("requiresRootCause", requiresRootCause);
        String deny = this.denyReason(wo, cfg, user, action);
        m.put("visible", deny == null);
        m.put("hint", deny);
        return m;
    }

    private static boolean isDangerAction(String action) {
        return "驳回".equals(action) || "关闭".equals(action) || "撤销".equals(action) || "重新打开".equals(action) || "退回".equals(action);
    }

    private String denyReason(WorkorderEntity wo, WorkflowConfigEntity cfg, LoginUser user, String action) {
        if (isApprovingAction(action)) {
            return this.approverDeny(wo, cfg, user);
        } else if ("关闭".equals(action) && "处理中".equals(wo.getStatus())) {
            return user.isAdmin() ? null : "仅管理员可关闭工单";
        } else if ("撤销".equals(action)) {
            // v3.5：待处理工单仅申请人（或管理员）可撤销
            return user.isAdmin() || user.username().equals(wo.getApplicantUsername()) ? null : "仅申请人可撤销工单";
        } else if ("重新打开".equals(action)) {
            return user.isAdmin() ? null : "仅管理员可重新打开工单";
        } else {
            return this.operatorDeny(wo, cfg, user);
        }
    }

    private static boolean isApprovingAction(String action) {
        return "审批通过".equals(action) || "驳回".equals(action);
    }

    private Map<String, Object> permOf(WorkorderEntity wo, WorkflowConfigEntity cfg, LoginUser user) {
        boolean editable = !"已完成".equals(wo.getStatus()) && !"已关闭".equals(wo.getStatus());
        boolean related = user.isAdmin()
            || user.username().equals(wo.getApplicantUsername())
            || user.username().equals(wo.getHandler())
            || this.rolesAsStrings(cfg).stream().anyMatch(user.roles()::contains);
        boolean canApproveNow = "待审批".equals(wo.getStatus()) && this.approverDeny(wo, cfg, user) == null;
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("edit", editable && related);
        p.put("addItem", editable && related);
        p.put("remind", !"已关闭".equals(wo.getStatus()) && related);
        p.put("comment", !"已关闭".equals(wo.getStatus()) && related);
        p.put("uploadAtt", !"已关闭".equals(wo.getStatus()) && !"已完成".equals(wo.getStatus()) && this.canUpdateWorkorders(user));
        p.put("transfer", canApproveNow);
        p.put("addApprover", canApproveNow);
        return p;
    }

    private boolean canUpdateWorkorders(LoginUser user) {
        if (!this.rbac.canWrite(user, "workorders", "update")) {
            return false;
        } else if (user.isAdmin()) {
            return true;
        } else {
            Map<String, Boolean> cfg = user.actionPerms().get("workorders");
            return cfg == null || !Boolean.FALSE.equals(cfg.get("update"));
        }
    }

    public List<Map<String, Object>> itemRowsOf(String woNo) {
        List<WorkorderItemEntity> rows = this.itemMapper
            .selectList((Wrappers.<WorkorderItemEntity>lambdaQuery().eq(WorkorderItemEntity::getWoNo, woNo)).orderByAsc(WorkorderItemEntity::getItemNo));
        Map<String, String> dcCache = new LinkedHashMap<>();
        Function<String, String> dcOf = code -> dcCache.computeIfAbsent(
                code,
                c -> {
                    MachineRoomEntity room = (MachineRoomEntity)this.machineRoomMapper
                        .selectOne(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, c));
                    return room != null && room.getDataCenter() != null ? room.getDataCenter() : "";
                }
            );
        List<Map<String, Object>> out = new ArrayList<>();

        for (WorkorderItemEntity e : rows) {
            Map<String, Object> row = itemRow(e);
            row.put("scope", TransferScope.of(e, dcOf));
            out.add(row);
        }

        return out;
    }

    private void insertAssetItems(String woNo, List<WoAssetLineInput> lines, String mainTargetRack, Integer mainTargetUnit) {
        int seq = 1;

        for (WoAssetLineInput line : lines) {
            this.itemMapper.insert(this.buildItemAt(woNo, line, seq++, mainTargetRack, mainTargetUnit));
        }
    }

    private WorkorderItemEntity buildItem(WorkorderEntity wo, String assetNo, WoAssetLineInput line, int seq) {
        return this.buildItemAt(wo.getWoNo(), this.lineWithAsset(line, assetNo), seq, wo.getTargetRack(), wo.getTargetUnit());
    }

    private WoAssetLineInput lineWithAsset(WoAssetLineInput src, String assetNo) {
        WoAssetLineInput out = new WoAssetLineInput();
        out.setAssetNo(assetNo);
        out.setDstRoom(src.getDstRoom());
        out.setDstRack(src.getDstRack());
        out.setDstUnit(src.getDstUnit());
        return out;
    }

    private WorkorderItemEntity buildItemAt(String woNo, WoAssetLineInput line, int seq, String mainTargetRack, Integer mainTargetUnit) {
        String assetNo = line.getAssetNo().trim();
        AssetEntity asset = (AssetEntity)this.assetMapper.selectById(assetNo);
        if (asset == null) {
            throw BusinessException.badRequest("资产不存在：" + assetNo);
        } else {
            WorkorderItemEntity item = new WorkorderItemEntity();
            item.setItemNo(woNo + "-" + String.format("%04d", seq));
            item.setWoNo(woNo);
            item.setAssetNo(assetNo);
            item.setSrcRoom(asset.getRoomCode());
            item.setSrcRack(asset.getRackCode());
            item.setSrcUnit(asset.getUnitNo());
            item.setSrcUHeight(asset.getUHeight());
            item.setSrcRegion(this.regionOfRoom(asset.getRoomCode()) != null ? this.regionOfRoom(asset.getRoomCode()) : asset.getRegionCode());
            String dstRack = this.blankToNull(line.getDstRack());
            Integer dstUnit = line.getDstUnit();
            if (dstRack == null) {
                dstRack = this.blankToNull(mainTargetRack);
                if (dstUnit == null && seq == 1) {
                    dstUnit = mainTargetUnit;
                }
            }

            item.setDstRack(dstRack);
            item.setDstUnit(dstUnit);
            item.setDstRoom(dstRack != null ? this.resolveRoomOfRack(dstRack) : this.blankToNull(line.getDstRoom()));
            item.setDstRegion(this.regionOfRoom(item.getDstRoom()));
            LocalDateTime now = LocalDateTime.now();
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            return item;
        }
    }

    private String resolveRoomOfRack(String rackCode) {
        RackEntity rack = (RackEntity)this.rackMapper.selectOne(Wrappers.<RackEntity>lambdaQuery().eq(RackEntity::getRackCode, rackCode));
        return rack == null ? null : rack.getRoomCode();
    }

    private List<WoAssetLineInput> normalizeAssetLines(List<WoAssetLineInput> assetLines) {
        if (assetLines != null && !assetLines.isEmpty()) {
            Map<String, WoAssetLineInput> byAsset = new LinkedHashMap<>();

            for (WoAssetLineInput line : assetLines) {
                if (line != null && line.getAssetNo() != null && !line.getAssetNo().isBlank()) {
                    byAsset.put(line.getAssetNo().trim(), line);
                }
            }

            return new ArrayList<>(byAsset.values());
        } else {
            return List.of();
        }
    }

    private static Map<String, Object> itemRow(WorkorderItemEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemNo", e.getItemNo());
        row.put("woNo", e.getWoNo());
        row.put("assetNo", e.getAssetNo());
        row.put("srcRoom", e.getSrcRoom());
        row.put("srcRack", e.getSrcRack());
        row.put("srcUnit", e.getSrcUnit());
        row.put("srcUHeight", e.getSrcUHeight());
        row.put("dstRoom", e.getDstRoom());
        row.put("dstRack", e.getDstRack());
        row.put("dstUnit", e.getDstUnit());
        row.put("srcRegion", e.getSrcRegion());
        row.put("dstRegion", e.getDstRegion());
        row.put("scope", TransferScope.of(e, null));
        return row;
    }

    private void validateTransferTargets(List<WoAssetLineInput> lines, String mainTargetRack) {
        if (lines.isEmpty()) {
            throw BusinessException.badRequest("调拨工单至少关联 1 项资产");
        } else {
            String mainRack = this.blankToNull(mainTargetRack);

            for (WoAssetLineInput line : lines) {
                String rack = this.blankToNull(line.getDstRack());
                if (rack == null) {
                    rack = mainRack;
                }

                if (rack == null) {
                    String room = this.blankToNull(line.getDstRoom());
                    if (room == null) {
                        throw BusinessException.badRequest("调拨必须指定目标机房（行 dstRoom）——上架与到场暂存都需先到机房，资产：" + line.getAssetNo());
                    }

                    Long n = this.machineRoomMapper.selectCount(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, room));
                    if (n == null || n == 0L) {
                        throw BusinessException.badRequest("调拨目标机房不存在：" + room);
                    }
                } else {
                    String roomx = this.resolveRoomOfRack(rack);
                    if (roomx == null) {
                        throw BusinessException.badRequest("调拨目标机柜不存在或未挂机房：" + rack);
                    }
                }
            }
        }
    }

    private void snapshotBeforeLocation(String woNo) {
        List<WorkorderItemEntity> rows = this.itemMapper
            .selectList((Wrappers.<WorkorderItemEntity>lambdaQuery().eq(WorkorderItemEntity::getWoNo, woNo)).orderByAsc(WorkorderItemEntity::getItemNo));
        if (!rows.isEmpty()) {
            List<Map<String, Object>> snaps = new ArrayList<>();

            for (WorkorderItemEntity r : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("assetNo", r.getAssetNo());
                m.put("room", r.getSrcRoom());
                m.put("rack", r.getSrcRack());
                m.put("unit", r.getSrcUnit());
                m.put("region", r.getSrcRegion());
                snaps.add(m);
            }

            String json;
            try {
                json = new ObjectMapper().writeValueAsString(snaps);
            } catch (JsonProcessingException var7) {
                json = String.valueOf(snaps);
            }

            WorkorderEntity upd = new WorkorderEntity();
            upd.setBeforeLocation(json.length() > 4000 ? json.substring(0, 4000) : json);
            upd.setUpdatedAt(LocalDateTime.now());
            this.workorderMapper.update(upd, Wrappers.<WorkorderEntity>lambdaUpdate().eq(WorkorderEntity::getWoNo, woNo));
        }
    }

    private String regionOfRoom(String roomCode) {
        if (roomCode != null && !roomCode.isBlank()) {
            MachineRoomEntity room = (MachineRoomEntity)this.machineRoomMapper
                .selectOne(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, roomCode.trim()));
            return room == null ? null : room.getRegionCode();
        } else {
            return null;
        }
    }

    private List<Map<String, Object>> childWosOf(String parentWo) {
        return this.workorderMapper
            .selectList(
                ((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getParentWo, parentWo))
                        .orderByDesc(WorkorderEntity::getApplyTime))
                    .orderByDesc(WorkorderEntity::getWoNo)
            )
            .stream()
            .map(this::toRow)
            .toList();
    }

    public List<Map<String, Object>> workflowConfig() {
        List<Map<String, Object>> out = new ArrayList<>();

        for (WorkflowConfigEntity cfg : this.workflowConfigMapper.selectList(Wrappers.<WorkflowConfigEntity>lambdaQuery().orderByAsc(WorkflowConfigEntity::getId))) {
            boolean needApproval = Boolean.TRUE.equals(cfg.getNeedApproval());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("woType", cfg.getWoType());
            row.put("needApproval", needApproval);
            row.put("approvalRoles", this.rolesAsStrings(cfg));
            row.put("approvalRoleNames", this.roleNameList(this.rolesAsStrings(cfg)));
            row.put("approvalUserLevels", this.configUserLevels(cfg));
            Map<String, Object> actions = new LinkedHashMap<>();

            for (String state : WorkorderStateMachine.states()) {
                actions.put(state, WorkorderStateMachine.actionsOf(state, needApproval));
            }

            row.put("actions", actions);
            out.add(row);
        }

        return out;
    }

    @Transactional
    public Map<String, Object> updateBasic(String woNo, String title, String description, String priority, String expectFinish) {
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo != null && !this.visibleToCurrentUser(wo)) {
            wo = null;
        }

        if (wo == null) {
            throw BusinessException.notFound("工单不存在：" + woNo);
        } else {
            String deny = this.operatorDeny(wo, this.requireConfig(wo.getWoType()), this.requireUser());
            if (deny != null) {
                throw BusinessException.forbidden(deny);
            } else {
                String st = wo.getStatus();
                if ("已完成".equals(st) || "已关闭".equals(st)) {
                    throw BusinessException.conflict("工单已终态（" + st + "），不能修改基本信息：" + woNo);
                } else if (title != null && !title.isBlank()) {
                    String trimmedTitle = title.trim();
                    checkLen("工单标题", trimmedTitle, 200);
                    String prio = this.blankToNull(priority);
                    if (prio == null) {
                        prio = wo.getPriority();
                    }

                    if (!PRIORITIES.contains(prio)) {
                        throw BusinessException.badRequest("优先级无效，仅支持 高/中/低：" + prio);
                    } else {
                        LocalDate expect = expectFinish != null && !expectFinish.isBlank() ? parseDate(expectFinish) : wo.getExpectFinish();
                        WorkorderEntity upd = new WorkorderEntity();
                        upd.setTitle(trimmedTitle);
                        upd.setDescription(this.blankToNull(description));
                        upd.setPriority(prio);
                        upd.setExpectFinish(expect);
                        upd.setVersion(wo.getVersion() + 1);
                        upd.setUpdatedAt(LocalDateTime.now());
                        boolean priorityChanged = !prio.equals(wo.getPriority());
                        if (priorityChanged) {
                            SlaConfigEntity sla = (SlaConfigEntity)this.slaConfigMapper
                                .selectOne(
                                    (Wrappers.<SlaConfigEntity>lambdaQuery().eq(SlaConfigEntity::getWoType, wo.getWoType()))
                                        .eq(SlaConfigEntity::getPriority, prio)
                                );
                            upd.setSlaResponseDeadline(SlaEscalationService.computeDeadline(wo.getApplyTime(), sla == null ? null : sla.getResponseMinutes()));
                            upd.setSlaResolveDeadline(SlaEscalationService.computeDeadline(wo.getApplyTime(), sla == null ? null : sla.getResolveMinutes()));
                        }

                        int rows = this.workorderMapper
                            .update(
                                upd,
                                (Wrappers.<WorkorderEntity>lambdaUpdate().eq(WorkorderEntity::getWoNo, woNo))
                                    .eq(WorkorderEntity::getVersion, wo.getVersion())
                            );
                        if (rows == 0) {
                            throw BusinessException.conflict("工单已被他人处理，请刷新后重试");
                        } else {
                            return this.detail(woNo);
                        }
                    }
                } else {
                    throw BusinessException.badRequest("工单标题不能为空");
                }
            }
        }
    }

    /**
     * v3.3.1：PUT /workorders/{woNo} 携带 relatedAssets（或 assetNos 等别名）时，基本信息与关联资产在同一事务内更新。
     */
    @Transactional
    public Map<String, Object> updateBasicAndAssets(
        String woNo, String title, String description, String priority, String expectFinish, List<String> assetNos
    ) {
        this.updateBasic(woNo, title, description, priority, expectFinish);
        this.replaceAssets(woNo, assetNos);
        return this.detail(woNo);
    }

    /**
     * 把工单关联资产整体替换为给定列表：新增的按 addItem 建行（含资产存在性、调拨目标校验），
     * 移除的按 deleteItem 删行；已在工单上的资产保持原行不动（不丢目标机柜等明细）。
     */
    @Transactional
    public Map<String, Object> replaceAssets(String woNo, List<String> assetNos) {
        WorkorderEntity wo = this.requireEditableWo(woNo);
        LinkedHashSet<String> want = new LinkedHashSet<>();
        if (assetNos != null) {
            for (String a : assetNos) {
                if (a != null && !a.isBlank()) {
                    want.add(a.trim());
                }
            }
        }
        if (want.isEmpty() && "调拨".equals(this.requireConfig(wo.getWoType()).getWoType())) {
            throw BusinessException.badRequest("调拨工单至少关联 1 项资产");
        }
        List<WorkorderItemEntity> current = this.itemMapper
            .selectList((Wrappers.<WorkorderItemEntity>lambdaQuery().eq(WorkorderItemEntity::getWoNo, woNo)).orderByAsc(WorkorderItemEntity::getItemNo));
        Set<String> have = new HashSet<>();
        int removed = 0;
        for (WorkorderItemEntity it : current) {
            have.add(it.getAssetNo());
            if (!want.contains(it.getAssetNo())) {
                this.deleteItem(woNo, it.getItemNo());
                removed++;
            }
        }
        int added = 0;
        for (String a : want) {
            if (!have.contains(a)) {
                WoAssetLineInput line = new WoAssetLineInput();
                line.setAssetNo(a);
                this.addItem(woNo, line);
                added++;
            }
        }
        if (added == 0 && removed == 0) {
            this.syncRelatedAssetsCsv(woNo);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("woNo", woNo);
        out.put("added", added);
        out.put("removed", removed);
        out.put("relatedAssets", String.join(",", want));
        return out;
    }

    @Transactional
    public Map<String, Object> updateRepairCost(String woNo, BigDecimal costLabor, BigDecimal costService, BigDecimal costOther) {
        WorkorderEntity wo = this.requireWo(woNo);
        if (!SWAP_WO_TYPES.contains(wo.getWoType())) {
            throw BusinessException.badRequest("仅故障维修/部件更换工单可登记维修费用：" + woNo);
        } else {
            String deny = this.operatorDeny(wo, this.requireConfig(wo.getWoType()), this.requireUser());
            if (deny != null) {
                throw BusinessException.forbidden(deny);
            } else {
                WorkorderEntity upd = new WorkorderEntity();
                upd.setCostLabor(costLabor == null ? BigDecimal.ZERO : checkCost("维修人工费", costLabor));
                upd.setCostService(costService == null ? BigDecimal.ZERO : checkCost("外委服务费", costService));
                upd.setCostOther(costOther == null ? BigDecimal.ZERO : checkCost("其他费用", costOther));
                upd.setUpdatedAt(LocalDateTime.now());
                // 修复：与状态流转一致使用乐观锁，防止与并发「完成工单」等操作互相覆盖费用字段
                upd.setVersion(wo.getVersion() == null ? 1 : wo.getVersion() + 1);
                int rows = this.workorderMapper
                    .update(
                        upd,
                        (Wrappers.<WorkorderEntity>lambdaUpdate().eq(WorkorderEntity::getWoNo, woNo))
                            .eq(wo.getVersion() != null, WorkorderEntity::getVersion, wo.getVersion())
                    );
                if (rows == 0) {
                    throw BusinessException.conflict("工单已被他人处理，请刷新后重试");
                }
                return this.detail(woNo);
            }
        }
    }

    @Transactional
    public Map<String, Object> remind(String woNo, String opinion) {
        LoginUser user = this.requireUser();
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo != null && !this.visibleToCurrentUser(wo)) {
            wo = null;
        }

        if (wo == null) {
            throw BusinessException.notFound("工单不存在：" + woNo);
        } else if ("已关闭".equals(wo.getStatus())) {
            throw BusinessException.conflict("工单已关闭，无需催办：" + woNo);
        } else {
            WorkflowConfigEntity cfg = this.requireConfig(wo.getWoType());
            String deny = this.operatorDeny(wo, cfg, user);
            if (deny != null) {
                throw BusinessException.forbidden(deny);
            } else {
                this.assertRemindThrottled(woNo);
                LocalDateTime now = LocalDateTime.now();
                this.insertFlow(woNo, "催办", user.username(), wo.getStatus(), wo.getStatus(), opinion, now);
                String summary = "工单 " + woNo + "「" + wo.getTitle() + "」已被催办，请尽快处理";
                if (opinion != null && !opinion.isBlank()) {
                    summary = summary + "（" + opinion.trim() + "）";
                }

                if ("待审批".equals(wo.getStatus())) {
                    for (String roleCode : this.rolesBefore(cfg, wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel())) {
                        this.notificationPort.notifyRole(roleCode, "WO_REMIND", "工单催办", summary, woNo);
                    }
                }

                if (wo.getHandler() != null && !wo.getHandler().isBlank() && !wo.getHandler().equals(user.username())) {
                    this.notificationPort.notifyUser(wo.getHandler(), "WO_REMIND", "工单催办", summary, woNo);
                }

                if (wo.getApplicantUsername() != null && !wo.getApplicantUsername().equals(user.username())) {
                    this.notificationPort.notifyUser(wo.getApplicantUsername(), "WO_REMIND", "工单催办", summary, woNo);
                }

                return this.detail(woNo);
            }
        }
    }

    private void assertRemindThrottled(String woNo) {
        WorkorderFlowEntity last = (WorkorderFlowEntity)this.flowMapper
            .selectOne(
                (((Wrappers.<WorkorderFlowEntity>lambdaQuery().eq(WorkorderFlowEntity::getWoNo, woNo))
                            .eq(WorkorderFlowEntity::getAction, "催办"))
                        .orderByDesc(WorkorderFlowEntity::getSeq))
                    .last("LIMIT 1")
            );
        if (last != null && last.getCreatedAt() != null) {
            long waited = Duration.between(last.getCreatedAt(), LocalDateTime.now()).toMinutes();
            if (waited < 10L) {
                throw BusinessException.conflict("工单已于 " + waited + " 分钟内催办过，请 " + (10L - waited) + " 分钟后再催");
            }
        }
    }

    @Transactional
    public Map<String, Object> comment(String woNo, String content) {
        LoginUser user = this.requireUser();
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo != null && !this.visibleToCurrentUser(wo)) {
            wo = null;
        }

        if (wo == null) {
            throw BusinessException.notFound("工单不存在：" + woNo);
        } else if ("已关闭".equals(wo.getStatus())) {
            throw BusinessException.conflict("工单已关闭，不能评论：" + woNo);
        } else {
            WorkflowConfigEntity cfg = this.requireConfig(wo.getWoType());
            String deny = this.operatorDeny(wo, cfg, user);
            if (deny != null) {
                throw BusinessException.forbidden(deny);
            } else if (content != null && !content.isBlank()) {
                String text = content.trim();
                checkLen("评论内容", text, 500);
                this.insertFlow(woNo, "评论", user.username(), wo.getStatus(), wo.getStatus(), text, LocalDateTime.now());
                return this.detail(woNo);
            } else {
                throw BusinessException.badRequest("评论内容不能为空");
            }
        }
    }

    private void insertFlow(String woNo, String action, String actor, String from, String to, String comment, LocalDateTime now) {
        WorkorderFlowEntity flow = new WorkorderFlowEntity();
        flow.setWoNo(woNo);
        flow.setSeq(this.nextSeq(woNo));
        flow.setAction(action);
        flow.setActor(actor);
        flow.setFromStatus(from);
        flow.setToStatus(to);
        flow.setComment(comment != null && !comment.isBlank() ? comment.trim() : null);
        flow.setCreatedAt(now);
        this.flowMapper.insert(flow);
    }

    @Transactional
    public Map<String, Object> workflowConfigUpdate(String woType, boolean needApproval, List<String> approvalRoles, List<List<String>> approvalUserLevels) {
        this.requireAdmin("工作流");
        WorkflowConfigEntity cfg = this.requireConfig(woType);
        List<String> roles = approvalRoles == null
            ? List.of()
            : approvalRoles.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();

        for (String r : roles) {
            this.requireRoleCode(r);
        }

        List<List<String>> levels = new ArrayList<>();
        if (approvalUserLevels != null) {
            for (List<String> levelUsers : approvalUserLevels) {
                levels.add(this.normalizeUserList(levelUsers, "指定审批人"));
            }
        }

        while (!levels.isEmpty() && levels.get(levels.size() - 1).isEmpty()) {
            levels.remove(levels.size() - 1);
        }

        for (List<String> levelUsers : levels) {
            for (String u : levelUsers) {
                this.requireActiveUser(u);
            }
        }

        int chainLen = Math.max(roles.size(), levels.size());
        if (chainLen > 5) {
            throw BusinessException.badRequest("审批链最多 5 级，当前 " + chainLen + " 级");
        } else if (needApproval && chainLen == 0) {
            throw BusinessException.badRequest("需审批的工单类型必须至少配置 1 个审批角色或指定审批人");
        } else {
            WorkflowConfigEntity upd = new WorkflowConfigEntity();
            upd.setNeedApproval(needApproval);
            upd.setApprovalRoles(new ArrayList<>(roles));
            upd.setApprovalUserLevels(new ArrayList<>(levels));
            upd.setUpdatedAt(LocalDateTime.now());
            this.workflowConfigMapper.update(upd, Wrappers.<WorkflowConfigEntity>lambdaUpdate().eq(WorkflowConfigEntity::getWoType, cfg.getWoType()));
            return this.workflowConfig().stream().filter(r -> cfg.getWoType().equals(r.get("woType"))).findFirst().orElseThrow();
        }
    }

    public List<Map<String, Object>> slaConfigList() {
        Map<String, SlaConfigEntity> byKey = new LinkedHashMap<>();

        for (SlaConfigEntity c : this.slaConfigMapper.selectList(null)) {
            byKey.put(c.getWoType() + "|" + c.getPriority(), c);
        }

        List<Map<String, Object>> out = new ArrayList<>();

        for (WorkflowConfigEntity cfg : this.workflowConfigMapper.selectList(Wrappers.<WorkflowConfigEntity>lambdaQuery().orderByAsc(WorkflowConfigEntity::getId))) {
            for (String prio : PRIORITIES) {
                SlaConfigEntity c = byKey.get(cfg.getWoType() + "|" + prio);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("woType", cfg.getWoType());
                row.put("priority", prio);
                row.put("responseMinutes", c == null ? null : c.getResponseMinutes());
                row.put("resolveMinutes", c == null ? null : c.getResolveMinutes());
                row.put("escalateTo", c == null ? null : c.getEscalateTo());
                row.put("escalateToName", c != null && c.getEscalateTo() != null ? this.roleName(c.getEscalateTo()) : null);
                row.put("configured", c != null);
                out.add(row);
            }
        }

        return out;
    }

    @Transactional
    public Map<String, Object> slaConfigUpdate(String woType, String priority, Integer responseMinutes, Integer resolveMinutes, String escalateTo) {
        this.requireAdmin("SLA");
        this.requireConfig(woType);
        String prio = priority == null ? "" : priority.trim();
        if (!PRIORITIES.contains(prio)) {
            throw BusinessException.badRequest("优先级无效，仅支持 高/中/低：" + prio);
        } else {
            checkMinutes("响应时限（responseMinutes）", responseMinutes);
            checkMinutes("解决时限（resolveMinutes）", resolveMinutes);
            String role = this.blankToNull(escalateTo);
            if (role != null) {
                this.requireRoleCode(role);
            }

            SlaConfigEntity existing = (SlaConfigEntity)this.slaConfigMapper
                .selectOne((Wrappers.<SlaConfigEntity>lambdaQuery().eq(SlaConfigEntity::getWoType, woType)).eq(SlaConfigEntity::getPriority, prio));
            if (existing == null) {
                SlaConfigEntity c = new SlaConfigEntity();
                c.setWoType(woType);
                c.setPriority(prio);
                c.setResponseMinutes(responseMinutes);
                c.setResolveMinutes(resolveMinutes);
                c.setEscalateTo(role);
                this.slaConfigMapper.insert(c);
            } else {
                SlaConfigEntity upd = new SlaConfigEntity();
                upd.setResponseMinutes(responseMinutes);
                upd.setResolveMinutes(resolveMinutes);
                upd.setEscalateTo(role);
                this.slaConfigMapper
                    .update(
                        null,
                        ((((Wrappers.<SlaConfigEntity>lambdaUpdate()
                                            .eq(SlaConfigEntity::getWoType, woType))
                                        .eq(SlaConfigEntity::getPriority, prio))
                                    .set(SlaConfigEntity::getResponseMinutes, responseMinutes))
                                .set(SlaConfigEntity::getResolveMinutes, resolveMinutes))
                            .set(SlaConfigEntity::getEscalateTo, role)
                    );
            }

            return this.slaConfigList()
                .stream()
                .filter(r -> woType.equals(r.get("woType")) && prio.equals(r.get("priority")))
                .findFirst()
                .orElseThrow(() -> BusinessException.conflict("SLA 配置写入后读取失败：" + woType + "/" + prio));
        }
    }

    private void requireAdmin(String what) {
        LoginUser user = this.requireUser();
        if (!user.isAdmin()) {
            throw BusinessException.forbidden("仅管理员可修改" + what + "配置");
        }
    }

    private void requireRoleCode(String roleCode) {
        Long n = this.roleMapper.selectCount(Wrappers.<RoleEntity>lambdaQuery().eq(RoleEntity::getRoleCode, roleCode));
        if (n == null || n == 0L) {
            throw BusinessException.badRequest("未知角色编码：" + roleCode);
        }
    }

    private static void checkMinutes(String label, Integer minutes) {
        if (minutes != null && minutes < 0) {
            throw BusinessException.badRequest(label + " 不能为负数：" + minutes);
        } else if (minutes != null && minutes == 0) {
            throw BusinessException.badRequest(label + " 不能为 0，清空时限请留空：" + minutes);
        }
    }

    public Map<String, Object> transferLocations() {
        List<Map<String, Object>> rooms = new ArrayList<>();

        for (MachineRoomEntity r : this.machineRoomMapper.selectList(Wrappers.<MachineRoomEntity>lambdaQuery().orderByAsc(MachineRoomEntity::getRoomCode))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roomCode", r.getRoomCode());
            m.put("name", r.getName());
            m.put("dataCenter", r.getDataCenter());
            m.put("regionCode", r.getRegionCode());
            rooms.add(m);
        }

        List<Map<String, Object>> racks = new ArrayList<>();

        for (RackEntity k : this.rackMapper.selectList(Wrappers.<RackEntity>lambdaQuery().orderByAsc(RackEntity::getRackCode))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rackCode", k.getRackCode());
            m.put("roomCode", k.getRoomCode());
            m.put("locationName", k.getLocationName());
            racks.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rooms", rooms);
        out.put("racks", racks);
        return out;
    }

    @Transactional
    public Map<String, Object> transition(
        String woNo,
        String action,
        String opinion,
        String rootCause,
        String replacedAsset,
        String completeNote,
        BigDecimal costLabor,
        BigDecimal costService,
        BigDecimal costOther
    ) {
        LoginUser user = this.requireUser();
        if (action != null && !action.isBlank()) {
            action = action.trim();
            WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
                .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
            if (wo != null && !this.visibleToCurrentUser(wo)) {
                wo = null;
            }

            if (wo == null) {
                throw BusinessException.notFound("工单不存在：" + woNo);
            } else {
                WorkflowConfigEntity cfg = this.requireConfig(wo.getWoType());
                boolean needApproval = Boolean.TRUE.equals(cfg.getNeedApproval());
                String from = wo.getStatus();
                if (!WorkorderStateMachine.canAct(from, action, needApproval)) {
                    throw this.illegalAction(from, action, needApproval);
                } else {
                    boolean closingFromProcessing = "处理中".equals(from) && "关闭".equals(action);
                    if (closingFromProcessing) {
                        if (rootCause == null || rootCause.isBlank()) {
                            throw BusinessException.badRequest("关闭工单必须填写根因分类（rootCause）");
                        }

                        if (!user.isAdmin()) {
                            throw BusinessException.forbidden("仅管理员可关闭工单");
                        }
                    }

                    // v3.5：撤销（待处理→已关闭）仅申请人或管理员可执行
                    if ("撤销".equals(action) && !user.isAdmin() && !user.username().equals(wo.getApplicantUsername())) {
                        throw BusinessException.forbidden("仅申请人可撤销工单");
                    }

                    if (rootCause != null && !rootCause.isBlank()) {
                        checkLen("根因分类", rootCause.trim(), 50);
                    }

                    if ("已完成".equals(from) && "重新打开".equals(action) && !user.isAdmin()) {
                        throw BusinessException.forbidden("仅管理员可重新打开工单");
                    } else if (!"驳回".equals(action) || opinion != null && !opinion.isBlank()) {
                        String to;
                        if (!"审批通过".equals(action) && !"驳回".equals(action)) {
                            to = WorkorderStateMachine.targetsOf(from, action, needApproval).get(0);
                            this.checkOperator(wo, cfg, user);
                        } else {
                            this.checkApprover(wo, cfg, user);
                            to = WorkorderStateMachine.targetsOf(from, action, needApproval).get(0);
                            if ("审批通过".equals(action)) {
                                int level = wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel();
                                boolean lastLevel = level + 1 >= this.approvalChainLen(wo, cfg);
                                to = lastLevel ? "处理中" : "待审批";
                            }
                        }

                        WorkorderStateMachine.assertTransit(from, to, needApproval);
                        LocalDateTime now = LocalDateTime.now();
                        WorkorderEntity upd = new WorkorderEntity();
                        upd.setStatus(to);
                        upd.setVersion(wo.getVersion() + 1);
                        upd.setUpdatedAt(now);
                        int level = wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel();
                        switch (action) {
                            case "开始处理":
                                upd.setHandler(user.username());
                                break;
                            case "审批通过":
                                upd.setApprovalLevel(level + 1);
                                upd.setApprovalDelegate(new LinkedHashMap<>());
                                break;
                            case "驳回":
                            case "重新提交":
                                upd.setApprovalLevel(0);
                                upd.setApprovalDelegate(new LinkedHashMap<>());
                                break;
                            case "完成工单":
                                upd.setFinishTime(now);
                                if (replacedAsset != null && !replacedAsset.isBlank()) {
                                    upd.setReplacedAsset(replacedAsset.trim());
                                }

                                if (completeNote != null && !completeNote.isBlank()) {
                                    upd.setCompleteNote(completeNote.trim());
                                }

                                if (rootCause != null && !rootCause.isBlank()) {
                                    upd.setRootCause(rootCause.trim());
                                }

                                if (SWAP_WO_TYPES.contains(wo.getWoType())) {
                                    applyRepairCost(upd, costLabor, costService, costOther);
                                } else if (nonNullCost(costLabor, costService, costOther)) {
                                    throw BusinessException.badRequest("仅故障维修/部件更换工单可登记维修费用：" + woNo);
                                }
                                break;
                            case "关闭":
                            case "撤销":
                                if (rootCause != null && !rootCause.isBlank()) {
                                    upd.setRootCause(rootCause.trim());
                                }

                                upd.setFinishTime(now);
                        }

                        if (!user.username().equals(wo.getApplicantUsername()) && wo.getFirstResponseAt() == null) {
                            upd.setFirstResponseAt(now);
                        }

                        int rows = this.workorderMapper
                            .update(
                                upd,
                                (Wrappers.<WorkorderEntity>lambdaUpdate().eq(WorkorderEntity::getWoNo, woNo))
                                    .eq(WorkorderEntity::getVersion, wo.getVersion())
                            );
                        if (rows == 0) {
                            throw BusinessException.conflict("工单已被他人处理，请刷新后重试");
                        } else {
                            WorkorderFlowEntity flow = new WorkorderFlowEntity();
                            flow.setWoNo(woNo);
                            flow.setSeq(this.nextSeq(woNo));
                            flow.setAction(action);
                            flow.setActor(user.username());
                            flow.setFromStatus(from);
                            flow.setToStatus(to);
                            flow.setComment(
                                opinion != null && !opinion.isBlank() ? opinion.trim() : (rootCause != null && !rootCause.isBlank() ? rootCause.trim() : null)
                            );
                            flow.setCreatedAt(now);
                            this.flowMapper.insert(flow);
                            if ("完成工单".equals(action)) {
                                this.completionService.complete(woNo, user.username());
                            }

                            if ("调拨".equals(wo.getWoType()) && "处理中".equals(to) && !"处理中".equals(from)) {
                                this.completionService.offlineForTransfer(woNo);
                            }

                            if ("处理中".equals(to) && !"处理中".equals(from)) {
                                this.completionService.onProcessing(woNo);
                            }

                            if ("已关闭".equals(to) && !"已关闭".equals(from)) {
                                this.completionService.onClose(woNo);
                            }

                            this.notifyOnTransition(wo, cfg, action, to, level, opinion);
                            return this.detail(woNo);
                        }
                    } else {
                        throw BusinessException.badRequest("驳回必须填写审批意见（opinion）");
                    }
                }
            }
        } else {
            throw BusinessException.badRequest("流转动作（action）不能为空");
        }
    }

    private void checkApprover(WorkorderEntity wo, WorkflowConfigEntity cfg, LoginUser user) {
        String deny = this.approverDeny(wo, cfg, user);
        if (deny != null) {
            throw BusinessException.forbidden(deny);
        }
    }

    private void checkOperator(WorkorderEntity wo, WorkflowConfigEntity cfg, LoginUser user) {
        String deny = this.operatorDeny(wo, cfg, user);
        if (deny != null) {
            throw BusinessException.forbidden(deny);
        }
    }

    private String approverDeny(WorkorderEntity wo, WorkflowConfigEntity cfg, LoginUser user) {
        if (user.isAdmin()) {
            return null;
        } else {
            String username = user.username();
            int level = wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel();
            Map<String, Object> delegate = this.delegateOf(wo);
            boolean delegateActive = delegate != null && level == asInt(delegate.get("level"), -1);
            if (delegateActive && "transfer".equals(delegate.get("mode"))) {
                List<String> members = this.stringList(delegate.get("members"));
                return members.contains(username) ? null : "此工单已转交给「" + String.join("、", this.displayNames(members)) + "」审批，您无权处理";
            } else if (delegateActive && "add".equals(delegate.get("mode")) && this.stringList(delegate.get("members")).contains(username)) {
                return null;
            } else {
                List<List<String>> custom = this.customChain(wo);
                if (!custom.isEmpty()) {
                    if (level >= 0 && level < custom.size()) {
                        List<String> members = custom.get(level);
                        return members.contains(username)
                            ? null
                            : "此工单指定由「" + String.join("、", this.displayNames(members)) + "」审批（第 " + (level + 1) + " 级），您无权处理";
                    } else {
                        return "此工单的审批链已配置完毕，仅管理员可执行该操作";
                    }
                } else {
                    List<String> roles = this.rolesAsStrings(cfg);
                    List<List<String>> cfgUsers = this.configUserLevels(cfg);
                    if (level >= 0 && level < Math.max(roles.size(), cfgUsers.size())) {
                        boolean roleHit = level < roles.size() && user.roles().contains(roles.get(level));
                        List<String> levelUsers = level < cfgUsers.size() ? cfgUsers.get(level) : List.of();
                        if (roleHit || levelUsers.contains(username)) {
                            return null;
                        } else if (level < roles.size()) {
                            String current = roles.get(level);
                            String extra = levelUsers.isEmpty() ? "" : "或指定审批人「" + String.join("、", this.displayNames(levelUsers)) + "」";
                            return "此工单需由「" + this.roleName(current) + "」角色（" + current + "）" + extra + "处理";
                        } else {
                            return "此工单需由指定审批人「" + String.join("、", this.displayNames(levelUsers)) + "」处理";
                        }
                    } else {
                        return "此工单的审批链已配置完毕，仅管理员可执行该操作";
                    }
                }
            }
        }
    }

    private String operatorDeny(WorkorderEntity wo, WorkflowConfigEntity cfg, LoginUser user) {
        if (user.isAdmin()) {
            return null;
        } else if (!user.username().equals(wo.getApplicantUsername()) && !user.username().equals(wo.getHandler())) {
            for (String r : this.rolesAsStrings(cfg)) {
                if (user.roles().contains(r)) {
                    return null;
                }
            }

            return "只有申请人、处理人、该类工单的审批人或管理员可以流转这张工单";
        } else {
            return null;
        }
    }

    private List<Entry<String, Integer>> approvableTypeLevels(LoginUser user) {
        Map<Entry<String, Integer>, Boolean> seen = new LinkedHashMap<>();

        for (WorkflowConfigEntity cfg : this.workflowConfigMapper.selectList(null)) {
            List<String> roles = this.rolesAsStrings(cfg);

            for (int i = 0; i < roles.size(); i++) {
                if (user.roles().contains(roles.get(i))) {
                    seen.put(Map.entry(cfg.getWoType(), i), Boolean.TRUE);
                }
            }

            List<List<String>> userLevels = this.configUserLevels(cfg);

            for (int ix = 0; ix < userLevels.size(); ix++) {
                if (userLevels.get(ix).contains(user.username())) {
                    seen.put(Map.entry(cfg.getWoType(), ix), Boolean.TRUE);
                }
            }
        }

        return new ArrayList<>(seen.keySet());
    }

    @Transactional
    public Map<String, Object> approvalTransfer(String woNo, String target, String opinion) {
        LoginUser user = this.requireUser();
        if (opinion != null && !opinion.isBlank()) {
            String targetUser = target == null ? "" : target.trim();
            if (targetUser.isEmpty()) {
                throw BusinessException.badRequest("转办目标审批人不能为空");
            } else if (targetUser.equals(user.username())) {
                throw BusinessException.badRequest("无需转办给自己");
            } else {
                this.requireActiveUser(targetUser);
                WorkorderEntity wo = this.lockForApprovalOps(woNo, "转办");
                WorkflowConfigEntity cfg = this.requireConfig(wo.getWoType());
                this.checkApprover(wo, cfg, user);
                int level = wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel();
                LocalDateTime now = LocalDateTime.now();
                Map<String, Object> d = new LinkedHashMap<>();
                d.put("level", level);
                d.put("mode", "transfer");
                d.put("members", List.of(targetUser));
                d.put("by", user.username());
                d.put("byName", user.name());
                d.put("opinion", opinion.trim());
                d.put("at", now.toString());
                this.updateDelegate(wo, d, now);
                this.insertFlow(woNo, "转办", user.username(), wo.getStatus(), wo.getStatus(), "转办给「" + targetUser + "」：" + opinion.trim(), now);
                String summary = "工单 " + woNo + "「" + wo.getTitle() + "」";
                this.notificationPort.notifyUser(targetUser, "WO_APPROVAL", "工单转办", summary + " 已转办给你审批（第 " + (level + 1) + " 级），请及时处理", woNo);
                return this.detail(woNo);
            }
        } else {
            throw BusinessException.badRequest("转办必须填写转办说明（opinion）");
        }
    }

    @Transactional
    public Map<String, Object> approvalAdd(String woNo, List<String> usernames, String opinion) {
        LoginUser user = this.requireUser();
        List<String> added = this.normalizeUserList(usernames, "加签审批人");
        if (added.isEmpty()) {
            throw BusinessException.badRequest("加签审批人不能为空");
        } else {
            for (String u : added) {
                if (u.equals(user.username())) {
                    throw BusinessException.badRequest("无需给自己加签");
                }

                this.requireActiveUser(u);
            }

            WorkorderEntity wo = this.lockForApprovalOps(woNo, "加签");
            WorkflowConfigEntity cfg = this.requireConfig(wo.getWoType());
            this.checkApprover(wo, cfg, user);
            int level = wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel();
            Map<String, Object> existing = this.delegateOf(wo);
            boolean sameLevel = existing != null && level == asInt(existing.get("level"), -1);
            String mode = sameLevel && "transfer".equals(existing.get("mode")) ? "transfer" : "add";
            List<String> members = new ArrayList<>();
            if (sameLevel) {
                members.addAll(this.stringList(existing.get("members")));
            }

            for (String u : added) {
                if (!members.contains(u)) {
                    members.add(u);
                }
            }

            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("level", level);
            d.put("mode", mode);
            d.put("members", members);
            d.put("by", user.username());
            d.put("byName", user.name());
            d.put("opinion", opinion != null && !opinion.isBlank() ? opinion.trim() : null);
            d.put("at", now.toString());
            this.updateDelegate(wo, d, now);
            String comment = "加签「" + String.join("、", added) + "」" + (opinion != null && !opinion.isBlank() ? "：" + opinion.trim() : "");
            this.insertFlow(woNo, "加签", user.username(), wo.getStatus(), wo.getStatus(), comment, now);
            String summary = "工单 " + woNo + "「" + wo.getTitle() + "」";

            for (String ux : added) {
                this.notificationPort.notifyUser(ux, "WO_APPROVAL", "工单加签", summary + " 已加签你为第 " + (level + 1) + " 级审批人，请及时处理", woNo);
            }

            return this.detail(woNo);
        }
    }

    private WorkorderEntity lockForApprovalOps(String woNo, String verb) {
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo != null && !this.visibleToCurrentUser(wo)) {
            wo = null;
        }

        if (wo == null) {
            throw BusinessException.notFound("工单不存在：" + woNo);
        } else if (!"待审批".equals(wo.getStatus())) {
            throw BusinessException.conflict("仅待审批的工单可" + verb + "（当前状态：" + wo.getStatus() + "）");
        } else {
            return wo;
        }
    }

    private void updateDelegate(WorkorderEntity wo, Map<String, Object> delegate, LocalDateTime now) {
        WorkorderEntity upd = new WorkorderEntity();
        upd.setApprovalDelegate(delegate);
        upd.setVersion(wo.getVersion() + 1);
        upd.setUpdatedAt(now);
        int rows = this.workorderMapper
            .update(
                upd,
                (Wrappers.<WorkorderEntity>lambdaUpdate().eq(WorkorderEntity::getWoNo, wo.getWoNo()))
                    .eq(WorkorderEntity::getVersion, wo.getVersion())
            );
        if (rows == 0) {
            throw BusinessException.conflict("工单已被他人处理，请刷新后重试");
        }
    }

    private void notifyOnTransition(WorkorderEntity before, WorkflowConfigEntity cfg, String action, String to, int levelBefore, String opinion) {
        String woNo = before.getWoNo();
        String summary = "工单 " + woNo + "「" + before.getTitle() + "」";
        switch (action) {
            case "提交审批":
                String content = summary + " 已提交审批（第 " + (levelBefore + 1) + " 级），请及时处理";
                this.notifyApprovalLevel(before, cfg, levelBefore, content);
                break;
            case "审批通过":
                this.notificationPort.notifyUser(before.getApplicantUsername(), "WO_APPROVED", "工单审批通过", summary + " 审批已通过，当前状态：" + to, woNo);
                if ("待审批".equals(to)) {
                    String contentx = summary + " 已通过第 " + (levelBefore + 1) + " 级审批，待您审批";
                    this.notifyApprovalLevel(before, cfg, levelBefore + 1, contentx);
                }
                break;
            case "驳回":
                this.notificationPort
                    .notifyUser(before.getApplicantUsername(), "WO_REJECT", "工单被驳回", summary + " 被驳回，意见：" + (opinion == null ? "" : opinion), woNo);
                break;
            case "开始处理":
                this.notificationPort.notifyUser(before.getApplicantUsername(), "WO_STARTED", "工单已开始处理", summary + " 已开始处理，当前状态：" + to, woNo);
                break;
            case "完成工单":
                this.notificationPort.notifyUser(before.getApplicantUsername(), "WO_DONE", "工单已完成", summary + " 已完成", woNo);
                break;
            case "关闭": {
                // v3.5：补全关闭通知（申请人与处理人都需知情，关闭意见随通知带出）
                String closedContent = summary + " 已关闭" + (opinion == null || opinion.isBlank() ? "" : "，关闭意见：" + opinion.trim());
                this.notificationPort.notifyUser(before.getApplicantUsername(), "WO_CLOSED", "工单已关闭", closedContent, woNo);
                this.notificationPort.notifyUser(before.getHandler(), "WO_CLOSED", "工单已关闭", closedContent, woNo);
                break;
            }
        }
    }

    private List<String> rolesBefore(WorkflowConfigEntity cfg, int level) {
        List<String> roles = this.rolesAsStrings(cfg);
        return level >= 0 && level < roles.size() ? List.of(roles.get(level)) : List.of();
    }

    private void notifyApprovalLevel(WorkorderEntity wo, WorkflowConfigEntity cfg, int level, String content) {
        String woNo = wo.getWoNo();
        List<List<String>> custom = this.customChain(wo);
        if (!custom.isEmpty()) {
            if (level >= 0 && level < custom.size()) {
                for (String u : custom.get(level)) {
                    this.notificationPort.notifyUser(u, "WO_APPROVAL", "工单待审批", content, woNo);
                }
            }
        } else {
            for (String roleCode : this.rolesBefore(cfg, level)) {
                this.notificationPort.notifyRole(roleCode, "WO_APPROVAL", "工单待审批", content, woNo);
            }

            List<List<String>> users = this.configUserLevels(cfg);
            if (level >= 0 && level < users.size()) {
                for (String u : users.get(level)) {
                    this.notificationPort.notifyUser(u, "WO_APPROVAL", "工单待审批", content, woNo);
                }
            }
        }
    }

    private BusinessException illegalAction(String from, String action, boolean needApproval) {
        List<String> actions = WorkorderStateMachine.actionsOf(from, needApproval);
        if (actions.isEmpty()) {
            throw BusinessException.conflict("工单当前状态「" + from + "」为终态，无可用操作");
        } else {
            throw BusinessException.conflict("工单当前状态「" + from + "」不支持动作「" + action + "」，可用操作：" + String.join("、", actions));
        }
    }

    private WorkflowConfigEntity requireConfig(String woType) {
        WorkflowConfigEntity cfg = (WorkflowConfigEntity)this.workflowConfigMapper
            .selectOne(Wrappers.<WorkflowConfigEntity>lambdaQuery().eq(WorkflowConfigEntity::getWoType, woType));
        if (cfg == null) {
            throw BusinessException.badRequest("未知的工单类型（须在 workflow_config 配置）：" + woType);
        } else {
            return cfg;
        }
    }

    public WorkorderEntity requireWo(String woNo) {
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper.selectOne(Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo));
        if (wo != null && !this.visibleToCurrentUser(wo)) {
            wo = null;
        }
        if (wo == null) {
            throw BusinessException.notFound("工单不存在：" + woNo);
        } else {
            return wo;
        }
    }

    /** 数据范围可见性：范围内，或当前用户是该工单的合格审批人 */
    private boolean visibleToCurrentUser(WorkorderEntity wo) {
        LoginUser u = CurrentUser.get();
        if (u == null || this.dataScope.canSeeWorkorder(u, wo)) {
            return true;
        }
        if ("待审批".equals(wo.getStatus())) {
            try {
                return this.approverDeny(wo, this.requireConfig(wo.getWoType()), u) == null;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    public WorkorderEntity requireUploadableWo(String woNo) {
        WorkorderEntity wo = this.requireWo(woNo);
        String st = wo.getStatus();
        // v3.5：已完成与已关闭同为终态，附件冻结（禁止增删）
        if ("已关闭".equals(st) || "已完成".equals(st)) {
            throw BusinessException.conflict("工单" + st + "，附件已冻结，不能上传或删除：" + woNo);
        } else {
            return wo;
        }
    }

    private int nextSeq(String woNo) {
        WorkorderFlowEntity last = (WorkorderFlowEntity)this.flowMapper
            .selectOne(
                ((Wrappers.<WorkorderFlowEntity>lambdaQuery().eq(WorkorderFlowEntity::getWoNo, woNo))
                        .orderByDesc(WorkorderFlowEntity::getSeq))
                    .last("LIMIT 1")
            );
        return last == null ? 1 : last.getSeq() + 1;
    }

    private List<String> rolesAsStrings(WorkflowConfigEntity cfg) {
        List<String> out = new ArrayList<>();
        if (cfg.getApprovalRoles() != null) {
            for (Object o : cfg.getApprovalRoles()) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim());
                }
            }
        }

        return out;
    }

    private String roleName(String roleCode) {
        RoleEntity role = (RoleEntity)this.roleMapper.selectOne(Wrappers.<RoleEntity>lambdaQuery().eq(RoleEntity::getRoleCode, roleCode));
        return role != null && role.getName() != null ? role.getName() : roleCode;
    }

    private List<String> roleNameList(List<String> roleCodes) {
        List<String> names = new ArrayList<>();

        for (String code : roleCodes) {
            names.add(this.roleName(code));
        }

        return names;
    }

    private List<List<String>> customChain(WorkorderEntity wo) {
        List<List<String>> out = new ArrayList<>();
        if (wo.getApprovalUsers() == null) {
            return out;
        } else {
            for (Object level : wo.getApprovalUsers()) {
                out.add(this.stringList(level));
            }

            return out;
        }
    }

    private Map<String, Object> delegateOf(WorkorderEntity wo) {
        Map<String, Object> d = wo.getApprovalDelegate();
        return d != null && !d.isEmpty() ? d : null;
    }

    private List<List<String>> configUserLevels(WorkflowConfigEntity cfg) {
        List<List<String>> out = new ArrayList<>();
        if (cfg.getApprovalUserLevels() == null) {
            return out;
        } else {
            for (Object level : cfg.getApprovalUserLevels()) {
                out.add(this.stringList(level));
            }

            return out;
        }
    }

    private int approvalChainLen(WorkorderEntity wo, WorkflowConfigEntity cfg) {
        List<List<String>> custom = this.customChain(wo);
        return !custom.isEmpty() ? custom.size() : Math.max(this.rolesAsStrings(cfg).size(), this.configUserLevels(cfg).size());
    }

    private Map<String, Object> delegateInfo(WorkorderEntity wo, WorkflowConfigEntity cfg) {
        Map<String, Object> d = this.delegateOf(wo);
        if (d == null) {
            return null;
        } else {
            int level = wo.getApprovalLevel() == null ? 0 : wo.getApprovalLevel();
            if (level != asInt(d.get("level"), -1)) {
                return null;
            } else {
                List<String> members = this.stringList(d.get("members"));
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("level", d.get("level"));
                out.put("mode", d.get("mode"));
                out.put("modeLabel", "transfer".equals(d.get("mode")) ? "转办" : "加签");
                out.put("members", members);
                out.put("memberNames", this.displayNames(members));
                out.put("by", d.get("by"));
                out.put("byName", d.get("byName"));
                out.put("opinion", d.get("opinion"));
                out.put("at", d.get("at"));
                return out;
            }
        }
    }

    private List<String> normalizeUserList(List<String> input, String label) {
        if (input == null) {
            return List.of();
        } else {
            List<String> out = new ArrayList<>();

            for (String s : input) {
                if (s != null && !s.isBlank()) {
                    String t = s.trim();
                    checkLen(label, t, 50);
                    if (!out.contains(t)) {
                        out.add(t);
                    }
                }
            }

            if (out.size() > 20) {
                throw BusinessException.badRequest(label + "最多 20 人，当前 " + out.size() + " 人");
            } else {
                return out;
            }
        }
    }

    public List<Map<String, Object>> approverOptions(String keyword) {
        LambdaQueryWrapper<UserEntity> qw = (LambdaQueryWrapper<UserEntity>)(Wrappers.<UserEntity>lambdaQuery().ne(UserEntity::getStatus, 0))
            .orderByAsc(UserEntity::getUsername);
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            qw.and(w -> ((w.like(UserEntity::getUsername, k)).or()).like(UserEntity::getName, k));
        }

        qw.last("LIMIT 50");
        List<Map<String, Object>> out = new ArrayList<>();

        for (UserEntity u : this.userMapper.selectList(qw)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", u.getUsername());
            row.put("name", u.getName());
            out.add(row);
        }

        return out;
    }

    private void requireActiveUser(String username) {
        UserEntity u = (UserEntity)this.userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
        if (u == null) {
            throw BusinessException.badRequest("用户不存在：" + username);
        } else if (u.getStatus() != null && u.getStatus() == 0) {
            throw BusinessException.badRequest("用户已停用，不能作为审批人：" + username);
        }
    }

    private String displayName(String username) {
        UserEntity u = (UserEntity)this.userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
        return u != null && u.getName() != null && !u.getName().isBlank() ? u.getName() : username;
    }

    private List<String> displayNames(List<String> usernames) {
        List<String> out = new ArrayList<>();

        for (String u : usernames) {
            out.add(this.displayName(u));
        }

        return out;
    }

    private List<List<String>> nestedDisplayNames(List<List<String>> chain) {
        List<List<String>> out = new ArrayList<>();

        for (List<String> level : chain) {
            out.add(this.displayNames(level));
        }

        return out;
    }

    private List<String> stringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object x : (List)o) {
                if (x != null && !String.valueOf(x).isBlank()) {
                    out.add(String.valueOf(x).trim());
                }
            }
        }

        return out;
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) {
            return n.intValue();
        } else {
            if (o != null) {
                try {
                    return Integer.parseInt(String.valueOf(o).trim());
                } catch (NumberFormatException var3) {
                }
            }

            return def;
        }
    }

    private LoginUser requireUser() {
        LoginUser u = CurrentUser.get();
        if (u == null) {
            throw BusinessException.unauthorized("未登录或会话已过期");
        } else {
            return u;
        }
    }

    private String joinCsv(List<String> items) {
        if (items == null) {
            return null;
        } else {
            List<String> trimmed = items.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).toList();
            return trimmed.isEmpty() ? null : String.join(",", trimmed);
        }
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private void deriveMaterialSummary(WorkorderEntity wo) {
        List<String> codes = new ArrayList<>();
        int qty = 0;
        boolean anyQty = false;

        for (Object d : wo.getDetails() == null ? List.of() : wo.getDetails()) {
            if (d instanceof Map) {
                Map<?, ?> m = (Map<?, ?>)d;
                String code = trimToNull(m.get("materialCode"));
                if (code != null) {
                    codes.add(code);
                    Object q = m.get("qty");
                    if (q instanceof Number n) {
                        qty += n.intValue();
                        anyQty = true;
                    } else if (q != null) {
                        try {
                            qty += Integer.parseInt(String.valueOf(q).trim());
                            anyQty = true;
                        } catch (NumberFormatException var12) {
                        }
                    }
                }
            }
        }

        if (wo.getRelatedMaterials() == null || wo.getRelatedMaterials().isBlank()) {
            wo.setRelatedMaterials(this.joinCsv(codes));
        }

        if (wo.getApplyQty() == null && anyQty) {
            wo.setApplyQty(qty);
        }
    }

    private static void checkLen(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw BusinessException.badRequest(field + "超长（最多 " + max + " 字符），当前 " + value.length() + " 字符");
        }
    }

    private static boolean nonNullCost(BigDecimal a, BigDecimal b, BigDecimal c) {
        return a != null || b != null || c != null;
    }

    private static void applyRepairCost(WorkorderEntity upd, BigDecimal labor, BigDecimal service, BigDecimal other) {
        if (labor != null) {
            upd.setCostLabor(checkCost("维修人工费", labor));
        }

        if (service != null) {
            upd.setCostService(checkCost("外委服务费", service));
        }

        if (other != null) {
            upd.setCostOther(checkCost("其他费用", other));
        }
    }

    private static BigDecimal checkCost(String field, BigDecimal value) {
        if (value.signum() < 0) {
            throw BusinessException.badRequest(field + "不能为负数：" + value.toPlainString());
        } else if (value.compareTo(MAX_COST) > 0) {
            throw BusinessException.badRequest(field + "超过单笔上限 " + MAX_COST.toPlainString() + " 元");
        } else {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static void checkCsvLen(String field, String csv) {
        if (csv != null) {
            checkLen(field, csv, 500);
        }
    }

    private static String trimToNull(Object v) {
        if (v == null) {
            return null;
        } else {
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : s;
        }
    }

    private static List<String> splitCsv(String csv) {
        return csv != null && !csv.isBlank() ? Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList() : List.of();
    }

    private Map<String, Object> toRow(WorkorderEntity wo) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("woNo", wo.getWoNo());
        row.put("woType", wo.getWoType());
        row.put("title", wo.getTitle());
        row.put("description", wo.getDescription());
        row.put("status", wo.getStatus());
        row.put("priority", wo.getPriority());
        row.put("applicant", wo.getApplicant());
        row.put("applicantUsername", wo.getApplicantUsername());
        row.put("handler", wo.getHandler());
        row.put("applyTime", wo.getApplyTime());
        row.put("expectFinish", wo.getExpectFinish());
        row.put("finishTime", wo.getFinishTime());
        row.put("relatedAssets", wo.getRelatedAssets());
        row.put("relatedMaterials", wo.getRelatedMaterials());
        row.put("applyQty", wo.getApplyQty());
        row.put("targetLocation", wo.getTargetLocation());
        row.put("targetRack", wo.getTargetRack());
        row.put("targetUnit", wo.getTargetUnit());
        row.put("beforeLocation", wo.getBeforeLocation());
        row.put("process", wo.getProcess());
        row.put("completeNote", wo.getCompleteNote());
        row.put("autoDeducted", wo.getAutoDeducted());
        row.put("rootCause", wo.getRootCause());
        row.put("costLabor", wo.getCostLabor());
        row.put("costService", wo.getCostService());
        row.put("costOther", wo.getCostOther());
        row.put("parentWo", wo.getParentWo());
        row.put("replacedAsset", wo.getReplacedAsset());
        row.put("details", wo.getDetails());
        row.put("replacements", wo.getReplacements());
        row.put("slaResponseDeadline", wo.getSlaResponseDeadline());
        row.put("slaResolveDeadline", wo.getSlaResolveDeadline());
        row.put("firstResponseAt", wo.getFirstResponseAt());
        row.put("slaEscalations", wo.getSlaEscalations());
        row.put("lastEscalationAt", wo.getLastEscalationAt());
        row.put("approvalLevel", wo.getApprovalLevel());
        row.put("slaOverdue", SlaPolicy.isOverdue(wo.getSlaResolveDeadline(), wo.getStatus(), LocalDateTime.now()));
        row.put("version", wo.getVersion());
        row.put("createdAt", wo.getCreatedAt());
        row.put("updatedAt", wo.getUpdatedAt());
        return row;
    }

    private Map<String, Object> transferSummary(WorkorderEntity wo, List<Map<String, Object>> items) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("itemCount", items == null ? 0 : items.size());
        String worst = null;
        int staged = 0;
        if (items != null) {
            for (Map<String, Object> item : items) {
                Object sc = item.get("scope");
                if (sc != null) {
                    String s = String.valueOf(sc);
                    if (worst == null || rank(s) > rank(worst)) {
                        worst = s;
                    }
                }

                if (!hasText(item.get("dstRack")) && hasText(item.get("dstRoom"))) {
                    staged++;
                }
            }
        }

        out.put("scope", worst);
        out.put("needDowntime", this.transferNeedDowntime(wo));
        out.put("stagedCount", staged);
        return out;
    }

    private static boolean hasText(Object v) {
        return v != null && !String.valueOf(v).isBlank();
    }

    private static int rank(String scope) {
        if ("跨地域".equals(scope)) {
            return 3;
        } else if ("跨数据中心".equals(scope)) {
            return 2;
        } else {
            return "跨机房".equals(scope) ? 1 : 0;
        }
    }

    private boolean transferNeedDowntime(WorkorderEntity wo) {
        if (wo != null && wo.getDetails() != null) {
            for (Object d : wo.getDetails()) {
                if (d instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>)d;
                    if (!m.containsKey("materialCode")) {
                        Object v = m.get("needDowntime");
                        if (Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v))) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private static Map<String, Object> flowRow(WorkorderFlowEntity f) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", f.getId());
        row.put("woNo", f.getWoNo());
        row.put("seq", f.getSeq());
        row.put("action", f.getAction());
        row.put("actor", f.getActor());
        row.put("fromStatus", f.getFromStatus());
        row.put("toStatus", f.getToStatus());
        row.put("comment", f.getComment());
        row.put("createdAt", f.getCreatedAt());
        return row;
    }

    public static LocalDate parseDate(String s) {
        if (s != null && !s.isBlank()) {
            try {
                return LocalDate.parse(s.trim());
            } catch (DateTimeParseException var2) {
                throw BusinessException.badRequest("期望完成日期格式错误，应为 yyyy-MM-dd：" + s.trim());
            }
        } else {
            return null;
        }
    }

    
    public WorkorderService(
        final WorkorderMapper workorderMapper,
        final WorkorderFlowMapper flowMapper,
        final WorkorderItemMapper itemMapper,
        final WorkflowConfigMapper workflowConfigMapper,
        final SlaConfigMapper slaConfigMapper,
        final AssetMapper assetMapper,
        final RackMapper rackMapper,
        final MachineRoomMapper machineRoomMapper,
        final WoCompletionService completionService,
        final NotificationPort notificationPort,
        final SeqNoGenerator seqNoGenerator,
        final RoleMapper roleMapper,
        final UserMapper userMapper,
        final RbacService rbac
    ,
        final com.dcim.security.DataScopeService dataScope) {
        this.dataScope = dataScope;
        this.workorderMapper = workorderMapper;
        this.flowMapper = flowMapper;
        this.itemMapper = itemMapper;
        this.workflowConfigMapper = workflowConfigMapper;
        this.slaConfigMapper = slaConfigMapper;
        this.assetMapper = assetMapper;
        this.rackMapper = rackMapper;
        this.machineRoomMapper = machineRoomMapper;
        this.completionService = completionService;
        this.notificationPort = notificationPort;
        this.seqNoGenerator = seqNoGenerator;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.rbac = rbac;
    }
}
