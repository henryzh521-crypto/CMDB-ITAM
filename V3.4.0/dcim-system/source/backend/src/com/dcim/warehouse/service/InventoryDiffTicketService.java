package com.dcim.warehouse.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.event.DomainEvent;
import com.dcim.common.event.EventTypes;
import com.dcim.common.event.UnifiedEventPublisher;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.warehouse.entity.InventoryItemEntity;
import com.dcim.warehouse.entity.InventoryTaskEntity;
import com.dcim.warehouse.mapper.InventoryItemMapper;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.service.WoAssetLineInput;
import com.dcim.workorder.service.WorkorderService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 盘点差异 → 「盘点差异」工单联动（v3.5）。
 *
 * <p>盘点任务完成时，若存在差异明细（资产盘亏 / 盘盈 / 错位，或物料实盘数量与账面快照
 * 不一致），自动创建一张「盘点差异」工单汇总差异，避免差异只停留在盘点报表里无人跟进。
 *
 * <p><b>一致性优先：</b>与 complete 在同一事务内执行——建单失败（如工单类型未配置、
 * 校验不过）会连带回滚整个完成操作，「任务已完成」与「差异已建单」要么都发生要么都
 * 不发生。因此调用方必须处于事务中（InventoryTaskController.complete 为 @Transactional）。
 *
 * <p><b>开关：</b>{@code dcim.linkage.inventory-diff-ticket.enabled=false} 时直接跳过
 * （回退 3.4 行为）。工单类型「盘点差异」由 V46 迁移注册到 workflow_config（免审）。
 */
@Service
public class InventoryDiffTicketService {
    private static final Logger log = LoggerFactory.getLogger(InventoryDiffTicketService.class);
    private static final String WO_TYPE = "盘点差异";
    /** 描述中差异明细的最大行数（防超大任务的描述失控，超出部分引导去盘点明细页看全量） */
    private static final int MAX_DETAIL_LINES = 50;
    private static final Set<String> ASSET_DIFF_RESULTS = Set.of("盘亏", "盘盈", "错位");

    private final InventoryItemMapper itemMapper;
    private final WorkorderService workorderService;
    private final UnifiedEventPublisher events;
    private final boolean enabled;

    public InventoryDiffTicketService(
        InventoryItemMapper itemMapper,
        WorkorderService workorderService,
        UnifiedEventPublisher events,
        @Value("${dcim.linkage.inventory-diff-ticket.enabled:true}") boolean enabled
    ) {
        this.itemMapper = itemMapper;
        this.workorderService = workorderService;
        this.events = events;
        this.enabled = enabled;
    }

    /**
     * 盘点完成时调用（须处于 complete 的事务内）：存在差异则建「盘点差异」工单，
     * 并发布 {@code inventory.diff} 领域事件（webhook 发件箱 + SSE 广播）。
     *
     * @return 创建的工单号；无差异或开关关闭返回 null
     */
    public String createDiffTicketIfNeeded(InventoryTaskEntity task) {
        if (!this.enabled || task == null) {
            return null;
        }
        List<InventoryItemEntity> items = this.itemMapper
            .selectList(Wrappers.<InventoryItemEntity>lambdaQuery().eq(InventoryItemEntity::getTaskNo, task.getTaskNo()));
        List<InventoryItemEntity> diffItems = new ArrayList<>();
        for (InventoryItemEntity item : items) {
            if (this.isDiff(item)) {
                diffItems.add(item);
            }
        }
        if (diffItems.isEmpty()) {
            return null;
        }
        String title = "盘点差异 " + task.getTaskNo() + "「" + task.getName() + "」";
        WorkorderEntity wo = this.workorderService
            .create(
                WO_TYPE,
                title,
                this.buildDescription(task, diffItems),
                "高",
                this.assetLinesOf(diffItems),
                this.materialCodesOf(diffItems),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        this.events.publish(DomainEvent.of(EventTypes.INVENTORY_DIFF, "inventory_task", task.getTaskNo(), this.actor(), this.eventData(task, diffItems)));
        String woNo = wo.getWoNo();
        log.info("[linkage] 盘点 {} 发现 {} 项差异，已生成「{}」工单 {}", task.getTaskNo(), diffItems.size(), WO_TYPE, woNo);
        return woNo;
    }

    /** 差异判定：资产行看盘点结论（盘亏/盘盈/错位）；物料行看实盘数量与账面快照是否一致。 */
    private boolean isDiff(InventoryItemEntity item) {
        if (item.getAssetNo() != null && !item.getAssetNo().isBlank()) {
            return item.getResult() != null && ASSET_DIFF_RESULTS.contains(item.getResult());
        }
        if (item.getCountedQty() == null || item.getExpected() == null) {
            return false;
        }
        Object book = item.getExpected().get("qty");
        return book != null && ((Number) book).intValue() != item.getCountedQty();
    }

    private String buildDescription(InventoryTaskEntity task, List<InventoryItemEntity> diffItems) {
        StringBuilder sb = new StringBuilder();
        sb
            .append("盘点任务 ")
            .append(task.getTaskNo())
            .append("「")
            .append(task.getName())
            .append("」已完成，共发现 ")
            .append(diffItems.size())
            .append(" 项差异，请核查差异原因并跟进处理（补录 / 赔偿 / 账面修正 / 报废）。\n\n差异清单：\n");
        int lines = 0;
        for (InventoryItemEntity item : diffItems) {
            if (lines >= MAX_DETAIL_LINES) {
                sb.append("…… 其余 ").append(diffItems.size() - lines).append(" 项略，详见盘点任务明细页\n");
                break;
            }
            sb.append("- ").append(this.describe(item)).append('\n');
            lines++;
        }
        return sb.toString();
    }

    private String describe(InventoryItemEntity item) {
        if (item.getAssetNo() != null && !item.getAssetNo().isBlank()) {
            Map<String, Object> exp = item.getExpected() == null ? Map.of() : item.getExpected();
            String where = exp.get("rackCode") + "-" + exp.get("unitNo");
            String note = item.getDiffNote() == null || item.getDiffNote().isBlank() ? "" : "，备注：" + item.getDiffNote().trim();
            return "资产 " + item.getAssetNo() + "：" + item.getResult() + "（应位 " + where + note + "）";
        }
        Object book = item.getExpected() == null ? null : item.getExpected().get("qty");
        int bookQty = book == null ? 0 : ((Number) book).intValue();
        int counted = item.getCountedQty() == null ? 0 : item.getCountedQty();
        int d = counted - bookQty;
        return "物料 " + item.getMaterialCode() + " @ " + item.getWarehouseCode() + "：账面 " + bookQty + "，实盘 " + counted + "，差 " + (d > 0 ? "+" : "") + d;
    }

    private List<WoAssetLineInput> assetLinesOf(List<InventoryItemEntity> diffItems) {
        List<WoAssetLineInput> lines = new ArrayList<>();
        for (InventoryItemEntity item : diffItems) {
            if (item.getAssetNo() != null && !item.getAssetNo().isBlank()) {
                WoAssetLineInput line = new WoAssetLineInput();
                line.setAssetNo(item.getAssetNo().trim());
                lines.add(line);
            }
        }
        return lines;
    }

    private List<String> materialCodesOf(List<InventoryItemEntity> diffItems) {
        Set<String> codes = new LinkedHashSet<>();
        for (InventoryItemEntity item : diffItems) {
            if (item.getMaterialCode() != null && !item.getMaterialCode().isBlank()) {
                codes.add(item.getMaterialCode().trim());
            }
        }
        return new ArrayList<>(codes);
    }

    private Map<String, Object> eventData(InventoryTaskEntity task, List<InventoryItemEntity> diffItems) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskNo", task.getTaskNo());
        data.put("taskName", task.getName());
        data.put("targetType", task.getTargetType());
        data.put("diffCount", diffItems.size());
        Map<String, Integer> byResult = new LinkedHashMap<>();
        for (InventoryItemEntity item : diffItems) {
            boolean isAsset = item.getAssetNo() != null && !item.getAssetNo().isBlank();
            byResult.merge(isAsset && item.getResult() != null ? item.getResult() : "物料差额", 1, Integer::sum);
        }
        data.put("byResult", byResult);
        return data;
    }

    private String actor() {
        LoginUser u = CurrentUser.get();
        return u == null ? "system" : u.username();
    }
}
