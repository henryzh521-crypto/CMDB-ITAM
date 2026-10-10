package com.dcim.workorder.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.common.asset.AssetOpsPort;
import com.dcim.common.stock.StockMovementPort;
import com.dcim.room.entity.MachineRoomEntity;
import com.dcim.room.mapper.MachineRoomMapper;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.entity.WorkorderItemEntity;
import com.dcim.workorder.mapper.WorkorderItemMapper;
import com.dcim.workorder.mapper.WorkorderMapper;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WoCompletionService {
    
    private static final Logger log = LoggerFactory.getLogger(WoCompletionService.class);
    static final String OFFLINE_REASON = "调拨临时停用";
    static final String REPAIR_HOLD_REASON = "维修占用";
    private final WorkorderMapper workorderMapper;
    private final WorkorderItemMapper itemMapper;
    private final AssetOpsPort assetOpsPort;
    private final StockMovementPort stockMovementPort;
    private final MachineRoomMapper machineRoomMapper;
    private final JdbcTemplate jdbc;

    @Transactional
    public void complete(String woNo, String actor) {
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo == null) {
            throw BusinessException.notFound("工单不存在：" + woNo);
        } else {
            List<String> assets = this.splitCsv(wo.getRelatedAssets());
            List<WorkorderItemEntity> rows = this.itemMapper
                .selectList(
                    (Wrappers.<WorkorderItemEntity>lambdaQuery().eq(WorkorderItemEntity::getWoNo, woNo)).orderByAsc(WorkorderItemEntity::getItemNo)
                );
            List<String> assetList = rows.isEmpty() ? assets : rows.stream().map(WorkorderItemEntity::getAssetNo).toList();
            String var7 = wo.getWoType() == null ? "" : wo.getWoType();
            switch (var7) {
                case "耗材申领":
                    this.deductMaterials(wo, actor, "耗材申领出库");
                    break;
                case "上架部署":
                    int i = 0;

                    for (String assetNoxxx : assetList) {
                        WorkorderItemEntity rowx = i < rows.size() ? rows.get(i) : null;
                        this.assetOpsPort.placeAsset(assetNoxxx, this.placeRack(wo, rowx, assetNoxxx), this.placeUnit(wo, rowx, i, assetNoxxx), null, woNo);
                        this.assetOpsPort.transitBiz(assetNoxxx, "在用(计费)", "上架部署工单完成", woNo);
                        i++;
                    }
                    break;
                case "下架退库":
                    for (String assetNoxx : assetList) {
                        this.assetOpsPort.unplaceAsset(assetNoxx, woNo);
                        if (!"停用中".equals(this.assetOpsPort.bizStatusOf(assetNoxx))) {
                            this.assetOpsPort.transitBiz(assetNoxx, "停用中", "下架退库工单完成", woNo);
                        }
                    }
                    break;
                case "故障维修":
                case "部件更换":
                    String woType = wo.getWoType();
                    Long openChildren = this.workorderMapper
                        .selectCount(
                            (Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getParentWo, woNo))
                                .notIn(WorkorderEntity::getStatus, List.of("已完成", "已关闭"))
                        );
                    if (openChildren != null && openChildren > 0L) {
                        throw BusinessException.conflict("存在 " + openChildren + " 张未完成的关联替换子工单，请先完成后再完成" + woType + "工单：" + woNo);
                    }

                    Set<String> swappedOut = new LinkedHashSet<>();

                    for (Map<String, Object> rep : this.replacementMaps(wo)) {
                        String parentNo = trimToNull(rep.get("parentAssetNo"));
                        String oldNo = trimToNull(rep.get("oldAssetNo"));
                        String newNo = trimToNull(rep.get("newAssetNo"));
                        String oldStatus = trimToNull(rep.get("oldStatus"));
                        this.assetOpsPort.replaceComponent(parentNo, oldNo, newNo, woNo, oldStatus);
                        if (oldNo != null) {
                            swappedOut.add(oldNo);
                        }
                    }

                    if (!swappedOut.isEmpty() && isBlank(wo.getReplacedAsset())) {
                        this.jdbc
                            .update(
                                "UPDATE workorder SET replaced_asset = ? WHERE wo_no = ? AND (replaced_asset IS NULL OR replaced_asset = '')",
                                new Object[]{String.join(",", swappedOut), woNo}
                            );
                    }

                    if (!this.materialDetailRows(wo).isEmpty()) {
                        this.deductMaterials(wo, actor, woType + "用料出库");
                    }

                    for (String assetNox : assetList) {
                        if (!swappedOut.contains(assetNox)) {
                            String st = this.assetOpsPort.bizStatusOf(assetNox);
                            boolean held = this.wasHeldByThisWo(assetNox, woNo, "维修占用");
                            if ("维修中".equals(st) && held) {
                                this.assetOpsPort.transitBiz(assetNox, "在用(计费)", woType + "工单完成", woNo);
                            } else if ("故障维修".equals(woType) && "停用中".equals(st) && !held) {
                                this.assetOpsPort.transitBiz(assetNox, "在用(计费)", "故障维修工单完成", woNo);
                            }
                        }
                    }
                    break;
                case "报废处置":
                    for (String assetNo : assetList) {
                        String st = this.assetOpsPort.bizStatusOf(assetNo);
                        if (!"已报废".equals(st)) {
                            if ("在用(计费)".equals(st) || "停用中".equals(st) || "维修中".equals(st)) {
                                this.assetOpsPort.transitBiz(assetNo, "待退役", "报废处置工单完成", woNo);
                                st = "待退役";
                            }

                            if ("待退役".equals(st)) {
                                this.assetOpsPort.transitBiz(assetNo, "已退役", "报废处置工单完成", woNo);
                            }

                            this.assetOpsPort.transitBiz(assetNo, "已报废", "报废处置工单完成", woNo);
                        }
                    }
                    break;
                case "变更": {
                    int ci = 0;

                    for (String assetNo : assetList) {
                        WorkorderItemEntity rowx = ci < rows.size() ? rows.get(ci) : null;
                        this.assetOpsPort.placeAsset(assetNo, this.placeRack(wo, rowx, assetNo), this.placeUnit(wo, rowx, ci, assetNo), null, woNo);
                        ci++;
                    }
                    break;
                }
                case "调拨":
                    int ti = 0;

                    for (String assetNo : assetList) {
                        WorkorderItemEntity row = ti < rows.size() ? rows.get(ti) : null;
                        String dstRack = this.rackOrNull(wo, row);
                        String dstRoom = row != null && row.getDstRoom() != null && !row.getDstRoom().isBlank() ? row.getDstRoom() : null;
                        if (dstRoom == null && dstRack != null) {
                            dstRoom = this.resolveRoomOfRack(dstRack);
                        }

                        if (dstRoom == null) {
                            throw BusinessException.badRequest("调拨缺少目标机房（行 dstRoom 或机柜所属机房），资产 " + assetNo);
                        }

                        String dstRegion = row != null && row.getDstRegion() != null && !row.getDstRegion().isBlank()
                            ? row.getDstRegion()
                            : this.regionOfRoom(dstRoom);
                        if (dstRack == null) {
                            this.assetOpsPort.transferAssetToRoom(assetNo, dstRoom, dstRegion, woNo);
                        } else {
                            Integer dstUnit = this.placeUnit(wo, row, ti, assetNo);
                            this.assetOpsPort.transferAsset(assetNo, dstRoom, dstRack, dstUnit, dstRegion, woNo);
                        }

                        if (this.wasOfflinedForTransfer(assetNo, woNo) && "停用中".equals(this.assetOpsPort.bizStatusOf(assetNo))) {
                            this.assetOpsPort.transitBiz(assetNo, "在用(计费)", "调拨完工恢复", woNo);
                        }

                        ti++;
                    }
                    // 修复：补充 break，防止后续维护时误向空的巡检/盘点/验收 case 落入
                    break;
                case "巡检":
                case "资产盘点":
                case "入库验收":
            }
        }
    }

    @Transactional
    public void offlineForTransfer(String woNo) {
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo != null) {
            boolean forceDowntime = this.transferNeedDowntime(wo);

            for (WorkorderItemEntity row : this.itemMapper
                .selectList(
                    (Wrappers.<WorkorderItemEntity>lambdaQuery().eq(WorkorderItemEntity::getWoNo, woNo)).orderByAsc(WorkorderItemEntity::getItemNo)
                )) {
                if ((forceDowntime || this.needsOffline(row)) && "在用(计费)".equals(this.assetOpsPort.bizStatusOf(row.getAssetNo()))) {
                    this.assetOpsPort.transitBiz(row.getAssetNo(), "停用中", "调拨临时停用", woNo);
                }
            }
        }
    }

    @Transactional
    public void onProcessing(String woNo) {
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo != null) {
            String type = wo.getWoType();
            if ("故障维修".equals(type) || "部件更换".equals(type)) {
                for (String assetNo : this.splitCsv(wo.getRelatedAssets())) {
                    if ("在用(计费)".equals(this.assetOpsPort.bizStatusOf(assetNo))) {
                        this.assetOpsPort.transitBiz(assetNo, "维修中", "维修占用", woNo);
                    }
                }
            }
        }
    }

    @Transactional
    public void onClose(String woNo) {
        WorkorderEntity wo = (WorkorderEntity)this.workorderMapper
            .selectOne((Wrappers.<WorkorderEntity>lambdaQuery().eq(WorkorderEntity::getWoNo, woNo)).last("FOR UPDATE"));
        if (wo != null) {
            for (String assetNo : this.splitCsv(wo.getRelatedAssets())) {
                this.restoreHoldBefore(assetNo, woNo);
            }
        }
    }

    private boolean wasHeldByThisWo(String assetNo, String woNo, String reason) {
        Long n = (Long)this.jdbc
            .queryForObject(
                "SELECT COUNT(*) FROM asset_event WHERE asset_no = ? AND ref_doc = ? AND event_type = 'STATUS_CHANGE' AND snapshot->>'reason' = ?",
                Long.class,
                new Object[]{assetNo, woNo, reason}
            );
        return n != null && n > 0L;
    }

    private void restoreHoldBefore(String assetNo, String woNo) {
        List<Map<String, Object>> held = this.jdbc
            .queryForList(
                "SELECT snapshot->'before'->>'statusBiz' AS before_st, snapshot->'after'->>'statusBiz' AS after_st FROM asset_event WHERE asset_no = ? AND ref_doc = ? AND event_type = 'STATUS_CHANGE' AND snapshot->>'reason' IN (?, ?, ?) ORDER BY id DESC LIMIT 1",
                new Object[]{assetNo, woNo, "维修占用", "调拨临时停用", "调拨跨地域临时停用"}
            );
        if (!held.isEmpty()) {
            String beforeSt = held.get(0).get("before_st") == null ? null : String.valueOf(held.get(0).get("before_st"));
            String afterSt = held.get(0).get("after_st") == null ? null : String.valueOf(held.get(0).get("after_st"));
            if (beforeSt != null && !beforeSt.isBlank() && !beforeSt.equals(afterSt)) {
                if (afterSt.equals(this.assetOpsPort.bizStatusOf(assetNo))) {
                    this.assetOpsPort.transitBiz(assetNo, beforeSt, "工单关闭回补占用", woNo);
                }
            }
        }
    }

    private boolean transferNeedDowntime(WorkorderEntity wo) {
        List<Object> details = wo.getDetails();
        if (details != null && !details.isEmpty()) {
            for (Object d : details) {
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

    private boolean needsOffline(WorkorderItemEntity row) {
        if (row == null) {
            return false;
        } else {
            String src = row.getSrcRegion();
            String dst = row.getDstRegion();
            if (src != null && !src.isBlank() && dst != null && !dst.isBlank() && !src.trim().equalsIgnoreCase(dst.trim())) {
                return true;
            } else {
                String srcRoom = row.getSrcRoom();
                String dstRoom = row.getDstRoom();
                if (dstRoom == null || dstRoom.isBlank()) {
                    dstRoom = this.resolveRoomOfRack(row.getDstRack());
                }

                if (srcRoom != null && !srcRoom.isBlank() && dstRoom != null && !dstRoom.isBlank()) {
                    String srcDc = this.dataCenterOfRoom(srcRoom);
                    String dstDc = this.dataCenterOfRoom(dstRoom);
                    return srcDc != null && !srcDc.isBlank() && dstDc != null && !dstDc.isBlank() ? !srcDc.trim().equals(dstDc.trim()) : false;
                } else {
                    return false;
                }
            }
        }
    }

    private boolean wasOfflinedForTransfer(String assetNo, String woNo) {
        Long n = (Long)this.jdbc
            .queryForObject(
                "SELECT COUNT(*) FROM asset_event WHERE asset_no = ? AND ref_doc = ? AND event_type = 'STATUS_CHANGE' AND snapshot->>'reason' IN (?, ?)",
                Long.class,
                new Object[]{assetNo, woNo, "调拨临时停用", "调拨跨地域临时停用"}
            );
        return n != null && n > 0L;
    }

    private String dataCenterOfRoom(String roomCode) {
        if (roomCode != null && !roomCode.isBlank()) {
            MachineRoomEntity room = (MachineRoomEntity)this.machineRoomMapper
                .selectOne(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, roomCode.trim()));
            return room == null ? null : room.getDataCenter();
        } else {
            return null;
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

    private String resolveRoomOfRack(String rackCode) {
        if (rackCode != null && !rackCode.isBlank()) {
            List<String> rooms = this.jdbc.queryForList("SELECT room_code FROM rack WHERE rack_code = ?", String.class, new Object[]{rackCode.trim()});
            return rooms.isEmpty() ? null : rooms.get(0);
        } else {
            return null;
        }
    }

    private void deductMaterials(WorkorderEntity wo, String actor, String remark) {
        List<Map<String, Object>> items = this.materialDetails(wo);
        int claimed = this.jdbc.update("UPDATE workorder SET auto_deducted = TRUE WHERE wo_no = ? AND auto_deducted = FALSE", new Object[]{wo.getWoNo()});
        if (claimed == 0) {
            log.info("[wo] 工单 {} 用料已扣减过（auto_deducted 幂等抢占失败），跳过出库", wo.getWoNo());
        } else {
            for (Map<String, Object> item : items) {
                String materialCode = String.valueOf(item.get("materialCode"));
                Object qtyRaw = item.get("qty");
                if (item.get("materialCode") == null || qtyRaw == null) {
                    throw BusinessException.badRequest("用料明细缺少 materialCode/qty：" + item);
                }

                // 修复：details 来自 JSON，qty 可能为字符串（如 "5"），与 WorkorderService.deriveMaterialSummary 的容错保持一致
                int qty;
                if (qtyRaw instanceof Number n) {
                    qty = n.intValue();
                } else {
                    try {
                        qty = Integer.parseInt(String.valueOf(qtyRaw).trim());
                    } catch (NumberFormatException e) {
                        throw BusinessException.badRequest("用料明细 qty 不是有效整数：" + item);
                    }
                }
                this.stockMovementPort.apply(new StockMovementPort.MovementCmd("OUT", materialCode, qty, null, null, wo.getWoNo(), actor, remark));
            }
        }
    }

    private List<Map<String, Object>> materialDetails(WorkorderEntity wo) {
        if (wo.getDetails() != null && !wo.getDetails().isEmpty()) {
            return this.mapRows(wo.getDetails());
        } else {
            throw BusinessException.badRequest("耗材申领工单缺少用料明细（details）");
        }
    }

    private List<Map<String, Object>> materialDetailRows(WorkorderEntity wo) {
        return wo.getDetails() != null && !wo.getDetails().isEmpty() ? this.mapRows(wo.getDetails()) : List.of();
    }

    private List<Map<String, Object>> replacementMaps(WorkorderEntity wo) {
        List<Object> reps = wo.getReplacements();
        return reps != null && !reps.isEmpty() ? this.mapRows(reps) : List.of();
    }

    private List<Map<String, Object>> mapRows(List<Object> details) {
        return details.stream().filter(d -> d instanceof Map).map(d -> (Map<String, Object>)d).toList();
    }

    private static String trimToNull(Object v) {
        if (v == null) {
            return null;
        } else {
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : s;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String rackOrNull(WorkorderEntity wo, WorkorderItemEntity row) {
        if (row != null && row.getDstRack() != null && !row.getDstRack().isBlank()) {
            return row.getDstRack();
        } else {
            return wo.getTargetRack() != null && !wo.getTargetRack().isBlank() ? wo.getTargetRack() : null;
        }
    }

    private String placeRack(WorkorderEntity wo, WorkorderItemEntity row, String assetNo) {
        String rack = this.rackOrNull(wo, row);
        if (rack == null) {
            throw BusinessException.badRequest("工单缺少目标机柜（行 dstRack 或 targetRack），无法为资产 " + assetNo + " 占位");
        } else {
            return rack;
        }
    }

    private Integer placeUnit(WorkorderEntity wo, WorkorderItemEntity row, int index, String assetNo) {
        return row != null && row.getDstUnit() != null ? row.getDstUnit() : this.staggeredUnit(wo, index, assetNo);
    }

    private Integer staggeredUnit(WorkorderEntity wo, int index, String assetNo) {
        if (wo.getTargetUnit() == null) {
            throw BusinessException.badRequest("工单缺少目标 U 位（targetUnit），无法为资产 " + assetNo + " 占位");
        } else {
            return wo.getTargetUnit() + index;
        }
    }

    private List<String> splitCsv(String csv) {
        return csv != null && !csv.isBlank() ? Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList() : List.of();
    }

    
    public WoCompletionService(
        final WorkorderMapper workorderMapper,
        final WorkorderItemMapper itemMapper,
        final AssetOpsPort assetOpsPort,
        final StockMovementPort stockMovementPort,
        final MachineRoomMapper machineRoomMapper,
        final JdbcTemplate jdbc
    ) {
        this.workorderMapper = workorderMapper;
        this.itemMapper = itemMapper;
        this.assetOpsPort = assetOpsPort;
        this.stockMovementPort = stockMovementPort;
        this.machineRoomMapper = machineRoomMapper;
        this.jdbc = jdbc;
    }
}
