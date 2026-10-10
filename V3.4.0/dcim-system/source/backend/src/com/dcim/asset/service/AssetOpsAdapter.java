package com.dcim.asset.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.asset.entity.AssetEntity;
import com.dcim.asset.entity.AssetEventEntity;
import com.dcim.asset.mapper.AssetEventMapper;
import com.dcim.asset.mapper.AssetMapper;
import com.dcim.asset.statemachine.AssetStateMachine;
import com.dcim.common.BusinessException;
import com.dcim.common.asset.AssetNoGenerator;
import com.dcim.common.asset.AssetOpsPort;
import com.dcim.common.rack.RackOccupancyPort;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AssetOpsAdapter implements AssetOpsPort {
    private final AssetMapper assetMapper;
    private final AssetEventMapper assetEventMapper;
    private final RackOccupancyPort rackOccupancyPort;
    private final AssetNoGenerator assetNoGenerator;
    private static final String SWAP_IN_STATUS = "在用(计费)";
    private static final Set<String> SWAP_IN_SOURCES = Set.of("待上架", "停用中", "在用(计费)");
    private static final String SWAP_OUT_DEFAULT = "维修中";
    private static final Set<String> SWAP_OUT_STATUSES = Set.of("维修中", "停用中", "待退役");

    @Transactional
    @Override
    public String bizStatusOf(String assetNo) {
        AssetEntity a = (AssetEntity)this.assetMapper.selectOne(Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getAssetNo, assetNo));
        return a == null ? null : a.getStatusBiz();
    }

    @Transactional
    @Override
    public void transitBiz(String assetNo, String target, String reason, String refWo) {
        AssetEntity cur = this.requireAsset(assetNo);
        String from = cur.getStatusBiz();
        if (from == null || !from.equals(target)) {
            if (!AssetStateMachine.canTransit(from, target)) {
                throw BusinessException.conflict("资产「" + this.displayName(cur) + "」（" + assetNo + "）当前状态「" + from + "」不允许变更为「" + target + "」，无法完成工单");
            } else {
                AssetStateMachine.assertTransit(from, target);
                AssetEntity upd = new AssetEntity();
                upd.setStatusBiz(target);
                upd.setVersion(cur.getVersion() + 1);
                upd.setUpdatedAt(LocalDateTime.now());
                int rows = this.assetMapper
                    .update(
                        upd,
                        (Wrappers.<AssetEntity>lambdaUpdate().eq(AssetEntity::getAssetNo, assetNo))
                            .eq(AssetEntity::getVersion, cur.getVersion())
                    );
                if (rows == 0) {
                    throw BusinessException.conflict("资产「" + assetNo + "」数据已被他人修改，请重试");
                } else {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("before", Map.of("statusBiz", from));
                    snapshot.put("after", Map.of("statusBiz", target));
                    snapshot.put("reason", reason);
                    this.recordEvent(assetNo, "STATUS_CHANGE", refWo, snapshot);
                }
            }
        }
    }

    @Transactional
    @Override
    public void placeAsset(String assetNo, String rackCode, Integer unitNo, Integer uHeight, String refWo) {
        AssetEntity cur = this.requireAsset(assetNo);
        if (rackCode != null && !rackCode.isBlank() && unitNo != null) {
            int height = uHeight == null ? (cur.getUHeight() == null ? 1 : cur.getUHeight()) : uHeight;
            String oldRack = cur.getRackCode();
            Integer oldUnit = cur.getUnitNo();
            int oldHeight = cur.getUHeight() == null ? 1 : cur.getUHeight();
            this.rackOccupancyPort.syncAssetPlacement(assetNo, oldRack, oldUnit, oldHeight, rackCode.trim(), unitNo, height);
            AssetEntity upd = new AssetEntity();
            upd.setRackCode(rackCode.trim());
            upd.setUnitNo(unitNo);
            upd.setUHeight(height);
            upd.setVersion(cur.getVersion() + 1);
            upd.setUpdatedAt(LocalDateTime.now());
            int rows = this.assetMapper
                .update(
                    upd,
                    (Wrappers.<AssetEntity>lambdaUpdate().eq(AssetEntity::getAssetNo, assetNo)).eq(AssetEntity::getVersion, cur.getVersion())
                );
            if (rows == 0) {
                throw BusinessException.conflict("资产「" + assetNo + "」数据已被他人修改，请重试");
            } else {
                boolean wasPlaced = oldRack != null && oldUnit != null;
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("rackCode", rackCode.trim());
                after.put("unitNo", unitNo);
                after.put("uHeight", height);
                if (wasPlaced) {
                    Map<String, Object> snapshot = new LinkedHashMap<>();
                    snapshot.put("before", Map.of("rackCode", oldRack, "unitNo", oldUnit, "uHeight", oldHeight));
                    snapshot.put("after", after);
                    this.recordEvent(assetNo, "MOVED", refWo, snapshot);
                } else {
                    this.recordEvent(assetNo, "RACKED", refWo, Map.of("after", after));
                }
            }
        } else {
            throw BusinessException.badRequest("目标机柜与起始U位不能为空（工单 targetRack/targetUnit）");
        }
    }

    @Transactional
    @Override
    public void unplaceAsset(String assetNo, String refWo) {
        AssetEntity cur = this.requireAsset(assetNo);
        String oldRack = cur.getRackCode();
        Integer oldUnit = cur.getUnitNo();
        int oldHeight = cur.getUHeight() == null ? 1 : cur.getUHeight();
        if (oldRack != null || oldUnit != null) {
            this.rackOccupancyPort.syncAssetPlacement(assetNo, oldRack, oldUnit, oldHeight, null, null, null);
            int rows = this.assetMapper
                .update(
                    null,
                    (((((Wrappers.<AssetEntity>lambdaUpdate()
                                            .eq(AssetEntity::getAssetNo, assetNo))
                                        .eq(AssetEntity::getVersion, cur.getVersion()))
                                    .set(AssetEntity::getRackCode, null))
                                .set(AssetEntity::getUnitNo, null))
                            .set(AssetEntity::getUpdatedAt, LocalDateTime.now()))
                        .set(AssetEntity::getVersion, cur.getVersion() + 1)
                );
            if (rows == 0) {
                throw BusinessException.conflict("资产「" + assetNo + "」数据已被他人修改，请重试");
            } else {
                Map<String, Object> before = new LinkedHashMap<>();
                before.put("rackCode", oldRack);
                before.put("unitNo", oldUnit);
                before.put("uHeight", oldHeight);
                this.recordEvent(assetNo, "UNRACKED", refWo, Map.of("before", before));
            }
        }
    }

    @Transactional
    @Override
    public void transferAsset(String assetNo, String dstRoom, String dstRack, Integer dstUnit, String dstRegion, String refWo) {
        AssetEntity cur = this.requireAsset(assetNo);
        if (dstRoom == null || dstRoom.isBlank()) {
            throw BusinessException.badRequest("调拨目标机房不能为空（行 dstRoom）");
        } else if (dstRack != null && !dstRack.isBlank() && dstUnit != null) {
            int height = cur.getUHeight() == null ? 1 : cur.getUHeight();
            String oldRoom = cur.getRoomCode();
            String oldRegion = cur.getRegionCode();
            String oldRack = cur.getRackCode();
            Integer oldUnit = cur.getUnitNo();
            int oldHeight = cur.getUHeight() == null ? 1 : cur.getUHeight();
            this.rackOccupancyPort.syncAssetPlacement(assetNo, oldRack, oldUnit, oldHeight, dstRack.trim(), dstUnit, height);
            String newRegion = dstRegion != null && !dstRegion.isBlank() ? dstRegion.trim() : cur.getRegionCode();
            AssetEntity upd = new AssetEntity();
            upd.setRoomCode(dstRoom.trim());
            upd.setRackCode(dstRack.trim());
            upd.setUnitNo(dstUnit);
            upd.setUHeight(height);
            upd.setRegionCode(newRegion);
            upd.setVersion(cur.getVersion() + 1);
            upd.setUpdatedAt(LocalDateTime.now());
            int rows = this.assetMapper
                .update(
                    upd,
                    (Wrappers.<AssetEntity>lambdaUpdate().eq(AssetEntity::getAssetNo, assetNo)).eq(AssetEntity::getVersion, cur.getVersion())
                );
            if (rows == 0) {
                throw BusinessException.conflict("资产「" + assetNo + "」数据已被他人修改，请重试");
            } else {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                Map<String, Object> before = new LinkedHashMap<>();
                before.put("roomCode", oldRoom);
                before.put("regionCode", oldRegion);
                before.put("rackCode", oldRack);
                before.put("unitNo", oldUnit);
                before.put("uHeight", oldHeight);
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("roomCode", dstRoom.trim());
                after.put("regionCode", newRegion);
                after.put("rackCode", dstRack.trim());
                after.put("unitNo", dstUnit);
                after.put("uHeight", height);
                snapshot.put("before", before);
                snapshot.put("after", after);
                this.recordEvent(assetNo, "TRANSFER", refWo, snapshot);
            }
        } else {
            throw BusinessException.badRequest("调拨目标机柜与起始U位不能为空（行 dstRack/dstUnit）");
        }
    }

    @Transactional
    @Override
    public void transferAssetToRoom(String assetNo, String dstRoom, String dstRegion, String refWo) {
        AssetEntity cur = this.requireAsset(assetNo);
        if (dstRoom != null && !dstRoom.isBlank()) {
            String oldRoom = cur.getRoomCode();
            String oldRegion = cur.getRegionCode();
            String oldRack = cur.getRackCode();
            Integer oldUnit = cur.getUnitNo();
            int oldHeight = cur.getUHeight() == null ? 1 : cur.getUHeight();
            if (oldRack != null || oldUnit != null) {
                this.rackOccupancyPort.syncAssetPlacement(assetNo, oldRack, oldUnit, oldHeight, null, null, null);
            }

            String newRegion = dstRegion != null && !dstRegion.isBlank() ? dstRegion.trim() : cur.getRegionCode();
            int rows = this.assetMapper
                .update(
                    null,
                    (((((((Wrappers.<AssetEntity>lambdaUpdate()
                                                    .eq(AssetEntity::getAssetNo, assetNo))
                                                .eq(AssetEntity::getVersion, cur.getVersion()))
                                            .set(AssetEntity::getRoomCode, dstRoom.trim()))
                                        .set(AssetEntity::getRegionCode, newRegion))
                                    .set(AssetEntity::getRackCode, null))
                                .set(AssetEntity::getUnitNo, null))
                            .set(AssetEntity::getUpdatedAt, LocalDateTime.now()))
                        .set(AssetEntity::getVersion, cur.getVersion() + 1)
                );
            if (rows == 0) {
                throw BusinessException.conflict("资产「" + assetNo + "」数据已被他人修改，请重试");
            } else {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                Map<String, Object> before = new LinkedHashMap<>();
                before.put("roomCode", oldRoom);
                before.put("regionCode", oldRegion);
                before.put("rackCode", oldRack);
                before.put("unitNo", oldUnit);
                before.put("uHeight", oldHeight);
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("roomCode", dstRoom.trim());
                after.put("regionCode", newRegion);
                after.put("rackCode", null);
                after.put("unitNo", null);
                after.put("uHeight", cur.getUHeight());
                snapshot.put("before", before);
                snapshot.put("after", after);
                snapshot.put("staging", true);
                this.recordEvent(assetNo, "TRANSFER", refWo, snapshot);
            }
        } else {
            throw BusinessException.badRequest("调拨目标机房不能为空（行 dstRoom）");
        }
    }

    @Transactional
    @Override
    public String createAssetForRfid(
        String name, String sn, String categoryCode, String modelCode, String ownerType, String regionCode, Map<String, Object> attrs, String refDoc
    ) {
        if (name != null && !name.isBlank()) {
            String normSn = sn != null && !sn.isBlank() ? sn.trim().toUpperCase() : null;
            if (normSn != null && this.assetMapper.selectCount(Wrappers.<AssetEntity>lambdaQuery().apply("UPPER(sn) = {0}", new Object[]{normSn})) > 0L) {
                throw BusinessException.conflict("SN 已存在：" + normSn);
            } else {
                AssetEntity a = new AssetEntity();
                a.setName(name.trim());
                a.setSn(normSn);
                a.setCategoryCode(categoryCode);
                a.setModelCode(modelCode);
                if (ownerType != null && !ownerType.isBlank()) {
                    String v = ownerType.trim().toUpperCase();
                    if (!"SELF".equals(v) && !"LEASE".equals(v)) {
                        throw BusinessException.badRequest("权属类型无效（仅支持 SELF/LEASE）：" + ownerType);
                    }

                    a.setOwnerType(v);
                } else {
                    a.setOwnerType("SELF");
                }

                a.setStatusBiz("待上架");
                a.setStatusFin("未入账");
                a.setRegionCode(regionCode);
                a.setAttrs(attrs == null ? new HashMap<>() : new HashMap<>(attrs));
                a.setVersion(1);
                a.setCreatedAt(LocalDateTime.now());
                a.setUpdatedAt(LocalDateTime.now());
                a.setAssetNo(this.assetNoGenerator.generate(categoryCode, regionCode));
                this.assetMapper.insert(a);
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("assetNo", a.getAssetNo());
                after.put("name", a.getName());
                after.put("sn", a.getSn());
                after.put("statusBiz", a.getStatusBiz());
                after.put("statusFin", a.getStatusFin());
                after.put("attrs", a.getAttrs());
                this.recordEvent(a.getAssetNo(), "CREATE", refDoc, Map.of("after", after));
                return a.getAssetNo();
            }
        } else {
            throw BusinessException.badRequest("资产名称不能为空（RFID 批次入库）");
        }
    }

    @Transactional
    @Override
    public void replaceComponent(String parentAssetNo, String oldAssetNo, String newAssetNo, String refWo, String oldStatus) {
        if (!isBlank(parentAssetNo) && !isBlank(oldAssetNo) && !isBlank(newAssetNo)) {
            parentAssetNo = parentAssetNo.trim();
            oldAssetNo = oldAssetNo.trim();
            newAssetNo = newAssetNo.trim();
            this.requireAsset(parentAssetNo);
            AssetEntity old = this.requireAsset(oldAssetNo);
            AssetEntity neu = this.requireAsset(newAssetNo);
            if (!oldAssetNo.equals(newAssetNo) && !oldAssetNo.equals(parentAssetNo) && !newAssetNo.equals(parentAssetNo)) {
                boolean oldDetached = isBlank(old.getParentAssetNo());
                String newParent = neu.getParentAssetNo();
                boolean newHasParent = !isBlank(newParent);
                if (!oldDetached || !newHasParent || !parentAssetNo.equals(newParent.trim())) {
                    if (!parentAssetNo.equals(old.getParentAssetNo())) {
                        throw BusinessException.conflict("旧件「" + oldAssetNo + "」当前未挂载到父资产「" + parentAssetNo + "」，无法换件");
                    } else if (newHasParent) {
                        throw BusinessException.conflict("新件「" + newAssetNo + "」已挂载到父资产「" + newParent.trim() + "」，无法换入");
                    } else {
                        String newSt = neu.getStatusBiz();
                        if (!SWAP_IN_SOURCES.contains(newSt)) {
                            throw BusinessException.conflict("新件「" + newAssetNo + "」当前状态「" + newSt + "」不可作为换入件（仅 待上架/停用中/在用(计费)）");
                        } else {
                            String oldSt = old.getStatusBiz();
                            String target = oldStatus != null && !oldStatus.isBlank() ? oldStatus.trim() : "维修中";
                            if (!SWAP_OUT_STATUSES.contains(target)) {
                                throw BusinessException.badRequest("换下后状态「" + target + "」不合法（仅 维修中/停用中/待退役）");
                            } else if (!target.equals(oldSt) && !AssetStateMachine.canTransit(oldSt, target)) {
                                throw BusinessException.conflict("旧件「" + oldAssetNo + "」当前状态「" + oldSt + "」不允许变更为「" + target + "」，无法换件");
                            } else {
                                if (old.getRackCode() != null && old.getUnitNo() != null) {
                                    this.unplaceAsset(oldAssetNo, refWo);
                                }

                                if (!target.equals(oldSt)) {
                                    this.transitBiz(oldAssetNo, target, "子件更换换下", refWo);
                                }

                                AssetEntity oldCur = this.requireAsset(oldAssetNo);
                                int oldRows = this.assetMapper
                                    .update(
                                        null,
                                        ((((Wrappers.<AssetEntity>lambdaUpdate()
                                                            .eq(AssetEntity::getAssetNo, oldAssetNo))
                                                        .eq(AssetEntity::getVersion, oldCur.getVersion()))
                                                    .set(AssetEntity::getParentAssetNo, null))
                                                .set(AssetEntity::getVersion, oldCur.getVersion() + 1))
                                            .set(AssetEntity::getUpdatedAt, LocalDateTime.now())
                                    );
                                if (oldRows == 0) {
                                    throw BusinessException.conflict("资产「" + oldAssetNo + "」数据已被他人修改，请重试");
                                } else {
                                    Map<String, Object> outBefore = new LinkedHashMap<>();
                                    outBefore.put("parentAssetNo", parentAssetNo);
                                    outBefore.put("statusBiz", oldSt);
                                    outBefore.put("rackCode", old.getRackCode());
                                    outBefore.put("unitNo", old.getUnitNo());
                                    Map<String, Object> outAfter = new LinkedHashMap<>();
                                    outAfter.put("parentAssetNo", null);
                                    outAfter.put("statusBiz", target);
                                    Map<String, Object> outSnap = new LinkedHashMap<>();
                                    outSnap.put("before", outBefore);
                                    outSnap.put("after", outAfter);
                                    this.recordEvent(oldAssetNo, "REPLACE_OUT", refWo, outSnap);
                                    AssetEntity newCur = this.requireAsset(newAssetNo);
                                    if (newCur.getRackCode() != null && newCur.getUnitNo() != null) {
                                        this.unplaceAsset(newAssetNo, refWo);
                                        newCur = this.requireAsset(newAssetNo);
                                    }

                                    int newRows = this.assetMapper
                                        .update(
                                            null,
                                            (((((Wrappers.<AssetEntity>lambdaUpdate()
                                                                    .eq(AssetEntity::getAssetNo, newAssetNo))
                                                                .eq(AssetEntity::getVersion, newCur.getVersion()))
                                                            .set(AssetEntity::getParentAssetNo, parentAssetNo))
                                                        .set(AssetEntity::getStatusBiz, "在用(计费)"))
                                                    .set(AssetEntity::getVersion, newCur.getVersion() + 1))
                                                .set(AssetEntity::getUpdatedAt, LocalDateTime.now())
                                        );
                                    if (newRows == 0) {
                                        throw BusinessException.conflict("资产「" + newAssetNo + "」数据已被他人修改，请重试");
                                    } else {
                                        Map<String, Object> inBefore = new LinkedHashMap<>();
                                        inBefore.put("parentAssetNo", null);
                                        inBefore.put("statusBiz", newSt);
                                        Map<String, Object> inAfter = new LinkedHashMap<>();
                                        inAfter.put("parentAssetNo", parentAssetNo);
                                        inAfter.put("statusBiz", "在用(计费)");
                                        Map<String, Object> inSnap = new LinkedHashMap<>();
                                        inSnap.put("before", inBefore);
                                        inSnap.put("after", inAfter);
                                        this.recordEvent(newAssetNo, "REPLACE_IN", refWo, inSnap);
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                throw BusinessException.badRequest("换件三方资产编号不能相同：" + parentAssetNo + "/" + oldAssetNo + "/" + newAssetNo);
            }
        } else {
            throw BusinessException.badRequest("换件三方资产编号不能为空（parentAssetNo/oldAssetNo/newAssetNo）");
        }
    }

    @Transactional
    @Override
    public void enrichFromDiscovery(String assetNo, String mgmtIp, Map<String, Object> hwAttrs, String refDoc) {
        AssetEntity cur = this.requireAsset(assetNo);
        Map<String, Object> attrs = cur.getAttrs() == null ? new HashMap<>() : new HashMap<>(cur.getAttrs());
        attrs.put("hw", hwAttrs == null ? new HashMap() : new HashMap<>(hwAttrs));
        AssetEntity upd = new AssetEntity();
        upd.setAttrs(attrs);
        if (mgmtIp != null && !mgmtIp.isBlank()) {
            upd.setMgmtIp(mgmtIp.trim());
        }

        upd.setVersion(cur.getVersion() + 1);
        upd.setUpdatedAt(LocalDateTime.now());
        int rows = this.assetMapper
            .update(
                upd, (Wrappers.<AssetEntity>lambdaUpdate().eq(AssetEntity::getAssetNo, assetNo)).eq(AssetEntity::getVersion, cur.getVersion())
            );
        if (rows == 0) {
            throw BusinessException.conflict("资产「" + assetNo + "」数据已被他人修改，请重试");
        } else {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("mgmtIp", mgmtIp == null ? null : mgmtIp.trim());
            after.put("hw", hwAttrs == null ? Map.of() : hwAttrs);
            snapshot.put("after", after);
            this.insertEvent(assetNo, "DISCOVERY_MATCH", refDoc, snapshot);
        }
    }

    @Transactional
    @Override
    public void recordEvent(String assetNo, String eventType, String refDoc, Map<String, Object> snapshot) {
        this.insertEvent(assetNo, eventType, refDoc, snapshot);
    }

    private AssetEntity requireAsset(String assetNo) {
        AssetEntity a = (AssetEntity)this.assetMapper.selectOne(Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getAssetNo, assetNo));
        if (a == null) {
            throw BusinessException.notFound("资产不存在：" + assetNo);
        } else {
            return a;
        }
    }

    private String displayName(AssetEntity a) {
        return a.getName() == null ? "" : a.getName();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void insertEvent(String assetNo, String eventType, String refDoc, Map<String, Object> snapshot) {
        AssetEventEntity e = new AssetEventEntity();
        e.setAssetNo(assetNo);
        e.setEventType(eventType);
        LoginUser u = CurrentUser.get();
        e.setOperator(u == null ? null : u.username());
        e.setRefDoc(refDoc);
        e.setSnapshot(snapshot);
        e.setEventTime(LocalDateTime.now());
        this.assetEventMapper.insert(e);
    }

    
    public AssetOpsAdapter(
        final AssetMapper assetMapper,
        final AssetEventMapper assetEventMapper,
        final RackOccupancyPort rackOccupancyPort,
        final AssetNoGenerator assetNoGenerator
    ) {
        this.assetMapper = assetMapper;
        this.assetEventMapper = assetEventMapper;
        this.rackOccupancyPort = rackOccupancyPort;
        this.assetNoGenerator = assetNoGenerator;
    }
}
