package com.dcim.collector.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.collector.BmcIp;
import com.dcim.collector.dto.CollectorDtos;
import com.dcim.collector.entity.BmcCredentialEntity;
import com.dcim.collector.entity.BmcEndpointEntity;
import com.dcim.collector.entity.DiscoverAssetRef;
import com.dcim.collector.entity.HwSnapshotEntity;
import com.dcim.collector.mapper.BmcCredentialMapper;
import com.dcim.collector.mapper.BmcEndpointMapper;
import com.dcim.collector.mapper.DiscoverAssetQueryMapper;
import com.dcim.collector.mapper.HwSnapshotMapper;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
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
@RequestMapping({"/api/oob"})
public class EndpointController {
    private final BmcEndpointMapper endpointMapper;
    private final BmcCredentialMapper credentialMapper;
    private final HwSnapshotMapper snapshotMapper;
    private final DiscoverAssetQueryMapper assetQueryMapper;

    @GetMapping({"/endpoints"})
    @RequirePerm(
        module = "discovery"
    )
    public ApiResult<PageResult<Map<String, Object>>> endpoints(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean disabled,
        @RequestParam(required = false) Boolean unmanaged
    ) {
        LambdaQueryWrapper<BmcEndpointEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> ((w.like(BmcEndpointEntity::getBmcIp, kw)).or()).like(BmcEndpointEntity::getAssetNo, kw));
        }

        if (disabled != null) {
            qw.eq(BmcEndpointEntity::getDisabled, disabled);
        }

        if (Boolean.TRUE.equals(unmanaged)) {
            qw.isNull(BmcEndpointEntity::getAssetNo);
        }

        qw.orderByDesc(BmcEndpointEntity::getId);
        Page<BmcEndpointEntity> p = new Page(page, size);
        this.endpointMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, this::toRow));
    }

    @PostMapping({"/endpoints"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "创建带外端点",
        objIdSpEL = "#req.bmcIp"
    )
    public ApiResult<Map<String, Object>> create(@RequestBody EndpointController.UpsertEndpointReq req) {
        if (req.getBmcIp() != null && !req.getBmcIp().isBlank()) {
            // 修复：bmcIp 此前仅做非空/去重校验，任意字符串（主机名、保留段地址）都能入库，
            // 采集任务会拿着这些值直接外呼；接入 BmcIp.validate（IPv4 + 保留段校验），违例 400
            String bmcIp = BmcIp.validate(req.getBmcIp());
            Long dup = this.endpointMapper.selectCount(Wrappers.<BmcEndpointEntity>lambdaQuery().eq(BmcEndpointEntity::getBmcIp, bmcIp));
            if (dup != null && dup > 0L) {
                throw BusinessException.conflict("带外端点已存在：" + bmcIp);
            } else {
                this.validateRefs(req.getAssetNo(), req.getCredentialId());
                // 修复：create 漏了 update 已有的采集间隔校验，任意值（含负数/超大值）可入库
                if (req.getCollectIntervalMin() != null && (req.getCollectIntervalMin() < 5 || req.getCollectIntervalMin() > 1440)) {
                    throw BusinessException.badRequest("采集间隔仅支持 5-1440 分钟");
                }

                BmcEndpointEntity e = new BmcEndpointEntity();
                e.setBmcIp(bmcIp);
                e.setMac(this.blankToNull(req.getMac()));
                e.setAssetNo(this.blankToNull(req.getAssetNo()));
                e.setCredentialId(req.getCredentialId());
                e.setRedfishVersion(req.getRedfishVersion());
                e.setIpmiVersion(req.getIpmiVersion());
                e.setCollectIntervalMin(req.getCollectIntervalMin() == null ? 60 : req.getCollectIntervalMin());
                e.setConsecutiveFailures(0);
                e.setDisabled(false);
                e.setCreatedAt(LocalDateTime.now());
                e.setUpdatedAt(LocalDateTime.now());
                try {
                    this.endpointMapper.insert(e);
                } catch (org.springframework.dao.DuplicateKeyException var9) {
                    // v3.5.2：bmc_ip 唯一约束兜底——并发创建同一 bmcIp 时上面的 selectCount
                    // check-then-act 存在竞态窗口，由数据库约束拦下并转 409（此前会 500）
                    throw BusinessException.conflict("带外端点已存在：" + bmcIp);
                }
                return ApiResult.ok(this.toRow(e));
            }
        } else {
            throw BusinessException.badRequest("bmcIp 不能为空");
        }
    }

    @PutMapping({"/endpoints/{id}"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "修改带外端点",
        objIdSpEL = "#id"
    )
    public ApiResult<Map<String, Object>> update(@PathVariable Long id, @RequestBody EndpointController.UpsertEndpointReq req) {
        BmcEndpointEntity e = (BmcEndpointEntity)this.endpointMapper.selectById(id);
        if (e == null) {
            throw BusinessException.notFound("带外端点不存在：" + id);
        } else {
            this.validateRefs(req.getAssetNo(), req.getCredentialId());
            BmcEndpointEntity upd = new BmcEndpointEntity();
            upd.setId(id);
            upd.setMac(req.getMac() == null ? e.getMac() : this.blankToNull(req.getMac()));
            if (req.getAssetNo() != null) {
                upd.setAssetNo(this.blankToNull(req.getAssetNo()));
            }

            if (req.getCredentialId() != null) {
                upd.setCredentialId(req.getCredentialId());
            }

            if (req.getRedfishVersion() != null) {
                upd.setRedfishVersion(req.getRedfishVersion());
            }

            if (req.getIpmiVersion() != null) {
                upd.setIpmiVersion(req.getIpmiVersion());
            }

            if (req.getCollectIntervalMin() != null) {
                if (req.getCollectIntervalMin() < 5 || req.getCollectIntervalMin() > 1440) {
                    throw BusinessException.badRequest("采集间隔仅支持 5-1440 分钟");
                }

                upd.setCollectIntervalMin(req.getCollectIntervalMin());
            }

            if (req.getDisabled() != null) {
                upd.setDisabled(req.getDisabled());
            }

            upd.setUpdatedAt(LocalDateTime.now());
            this.endpointMapper.updateById(upd);
            return ApiResult.ok(this.toRow((BmcEndpointEntity)this.endpointMapper.selectById(id)));
        }
    }

    @DeleteMapping({"/endpoints/{id}"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "删除带外端点",
        objIdSpEL = "#id"
    )
    public ApiResult<Boolean> delete(@PathVariable Long id) {
        BmcEndpointEntity e = (BmcEndpointEntity)this.endpointMapper.selectById(id);
        if (e == null) {
            throw BusinessException.notFound("带外端点不存在：" + id);
        } else {
            Long snaps = this.snapshotMapper.selectCount(Wrappers.<HwSnapshotEntity>lambdaQuery().eq(HwSnapshotEntity::getEndpointId, id));
            if (snaps != null && snaps > 0L) {
                throw BusinessException.conflict("端点已有 " + snaps + " 条快照历史，不允许删除（可停用）");
            } else {
                this.endpointMapper.deleteById(id);
                return ApiResult.ok(true);
            }
        }
    }

    @PostMapping({"/endpoints/import"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "批量导入带外端点"
    )
    @Transactional
    public ApiResult<Map<String, Object>> importEndpoints(@RequestBody List<CollectorDtos.EndpointImportRow> rows) {
        if (rows != null && !rows.isEmpty()) {
            int created = 0;
            int updated = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < rows.size(); i++) {
                CollectorDtos.EndpointImportRow row = rows.get(i);
                int line = i + 1;
                if (row != null && row.bmcIp() != null && !row.bmcIp().isBlank()) {
                    String bmcIp = row.bmcIp().trim();
                    String assetNo = row.assetNo() != null && !row.assetNo().isBlank() ? row.assetNo().trim() : null;
                    if (assetNo != null && this.assetQueryMapper.selectByAssetNo(assetNo) == null) {
                        errors.add("第 " + line + " 行：资产不存在 " + assetNo);
                    } else {
                        BmcEndpointEntity exist = (BmcEndpointEntity)this.endpointMapper
                            .selectOne(Wrappers.<BmcEndpointEntity>lambdaQuery().eq(BmcEndpointEntity::getBmcIp, bmcIp));
                        if (exist == null) {
                            BmcEndpointEntity e = new BmcEndpointEntity();
                            e.setBmcIp(bmcIp);
                            e.setAssetNo(assetNo);
                            e.setCollectIntervalMin(60);
                            e.setConsecutiveFailures(0);
                            e.setDisabled(false);
                            e.setCreatedAt(LocalDateTime.now());
                            e.setUpdatedAt(LocalDateTime.now());
                            try {
                                this.endpointMapper.insert(e);
                                created++;
                            } catch (org.springframework.dao.DuplicateKeyException var10) {
                                // v3.5.2：行内查重与插入之间的竞态由唯一约束兜底，记为该行错误而非整批 500
                                errors.add("第 " + line + " 行：bmcIp 已存在（并发冲突）" + bmcIp);
                            }
                        } else {
                            BmcEndpointEntity upd = new BmcEndpointEntity();
                            upd.setId(exist.getId());
                            upd.setAssetNo(assetNo);
                            upd.setUpdatedAt(LocalDateTime.now());
                            this.endpointMapper.updateById(upd);
                            updated++;
                        }
                    }
                } else {
                    errors.add("第 " + line + " 行：bmcIp 不能为空");
                }
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("created", created);
            out.put("updated", updated);
            out.put("errors", errors);
            return ApiResult.ok(out);
        } else {
            throw BusinessException.badRequest("导入内容不能为空");
        }
    }

    @PostMapping({"/endpoints/{id}/collect-now"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "触发立即采集",
        objIdSpEL = "#id"
    )
    public ApiResult<Boolean> collectNow(@PathVariable Long id) {
        BmcEndpointEntity e = (BmcEndpointEntity)this.endpointMapper.selectById(id);
        if (e == null) {
            throw BusinessException.notFound("带外端点不存在：" + id);
        } else if (e.getAssetNo() != null && !e.getAssetNo().isBlank()) {
            if (Boolean.TRUE.equals(e.getDisabled())) {
                throw BusinessException.badRequest("端点已停用，请先启用");
            } else {
                BmcEndpointEntity upd = new BmcEndpointEntity();
                upd.setId(id);
                upd.setNextCollectAt(LocalDateTime.now());
                upd.setUpdatedAt(LocalDateTime.now());
                this.endpointMapper.updateById(upd);
                return ApiResult.ok(true);
            }
        } else {
            throw BusinessException.badRequest("端点未纳管（无关联资产），不参与采集");
        }
    }

    @GetMapping({"/endpoints/{id}/snapshots"})
    @RequirePerm(
        module = "discovery"
    )
    public ApiResult<PageResult<Map<String, Object>>> snapshots(
        @PathVariable Long id, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "10") long size
    ) {
        BmcEndpointEntity e = (BmcEndpointEntity)this.endpointMapper.selectById(id);
        if (e == null) {
            throw BusinessException.notFound("带外端点不存在：" + id);
        } else {
            LambdaQueryWrapper<HwSnapshotEntity> qw = (LambdaQueryWrapper<HwSnapshotEntity>)(Wrappers.<HwSnapshotEntity>lambdaQuery()
                    .eq(HwSnapshotEntity::getEndpointId, id))
                .orderByDesc(HwSnapshotEntity::getCollectedAt);
            Page<HwSnapshotEntity> p = new Page(page, size);
            this.snapshotMapper.selectPage(p, qw);
            return ApiResult.ok(PageResult.of(p, s -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", s.getId());
                row.put("endpointId", s.getEndpointId());
                row.put("assetNo", s.getAssetNo());
                row.put("protocol", s.getProtocol());
                row.put("snapshot", s.getSnapshot());
                row.put("ok", s.getOk());
                row.put("error", s.getError());
                row.put("collectedAt", s.getCollectedAt());
                return row;
            }));
        }
    }

    private void validateRefs(String assetNo, Long credentialId) {
        String a = this.blankToNull(assetNo);
        if (a != null && this.assetQueryMapper.selectByAssetNo(a) == null) {
            throw BusinessException.badRequest("资产不存在：" + a);
        } else if (credentialId != null && this.credentialMapper.selectById(credentialId) == null) {
            throw BusinessException.badRequest("凭据不存在：" + credentialId);
        }
    }

    private Map<String, Object> toRow(BmcEndpointEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("bmcIp", e.getBmcIp());
        row.put("mac", e.getMac());
        row.put("assetNo", e.getAssetNo());
        if (e.getAssetNo() != null) {
            DiscoverAssetRef asset = this.assetQueryMapper.selectByAssetNo(e.getAssetNo());
            row.put("assetName", asset == null ? null : asset.getName());
        }

        row.put("credentialId", e.getCredentialId());
        if (e.getCredentialId() != null) {
            BmcCredentialEntity cred = (BmcCredentialEntity)this.credentialMapper.selectById(e.getCredentialId());
            row.put("credentialName", cred == null ? null : cred.getCredName());
        }

        row.put("redfishVersion", e.getRedfishVersion());
        row.put("ipmiVersion", e.getIpmiVersion());
        row.put("collectIntervalMin", e.getCollectIntervalMin());
        row.put("consecutiveFailures", e.getConsecutiveFailures());
        row.put("lastCollectAt", e.getLastCollectAt());
        row.put("lastCollectOk", e.getLastCollectOk());
        row.put("nextCollectAt", e.getNextCollectAt());
        row.put("disabled", e.getDisabled());
        row.put("createdAt", e.getCreatedAt());
        return row;
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    
    public EndpointController(
        final BmcEndpointMapper endpointMapper,
        final BmcCredentialMapper credentialMapper,
        final HwSnapshotMapper snapshotMapper,
        final DiscoverAssetQueryMapper assetQueryMapper
    ) {
        this.endpointMapper = endpointMapper;
        this.credentialMapper = credentialMapper;
        this.snapshotMapper = snapshotMapper;
        this.assetQueryMapper = assetQueryMapper;
    }

    public static class UpsertEndpointReq {
        private String bmcIp;
        private String mac;
        private String assetNo;
        private Long credentialId;
        private String redfishVersion;
        private String ipmiVersion;
        private Integer collectIntervalMin;
        private Boolean disabled;

        
        public String getBmcIp() {
            return this.bmcIp;
        }

        
        public String getMac() {
            return this.mac;
        }

        
        public String getAssetNo() {
            return this.assetNo;
        }

        
        public Long getCredentialId() {
            return this.credentialId;
        }

        
        public String getRedfishVersion() {
            return this.redfishVersion;
        }

        
        public String getIpmiVersion() {
            return this.ipmiVersion;
        }

        
        public Integer getCollectIntervalMin() {
            return this.collectIntervalMin;
        }

        
        public Boolean getDisabled() {
            return this.disabled;
        }

        
        public void setBmcIp(final String bmcIp) {
            this.bmcIp = bmcIp;
        }

        
        public void setMac(final String mac) {
            this.mac = mac;
        }

        
        public void setAssetNo(final String assetNo) {
            this.assetNo = assetNo;
        }

        
        public void setCredentialId(final Long credentialId) {
            this.credentialId = credentialId;
        }

        
        public void setRedfishVersion(final String redfishVersion) {
            this.redfishVersion = redfishVersion;
        }

        
        public void setIpmiVersion(final String ipmiVersion) {
            this.ipmiVersion = ipmiVersion;
        }

        
        public void setCollectIntervalMin(final Integer collectIntervalMin) {
            this.collectIntervalMin = collectIntervalMin;
        }

        
        public void setDisabled(final Boolean disabled) {
            this.disabled = disabled;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof EndpointController.UpsertEndpointReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$credentialId = this.getCredentialId();
                Object other$credentialId = other.getCredentialId();
                if (this$credentialId == null ? other$credentialId == null : this$credentialId.equals(other$credentialId)) {
                    Object this$collectIntervalMin = this.getCollectIntervalMin();
                    Object other$collectIntervalMin = other.getCollectIntervalMin();
                    if (this$collectIntervalMin == null ? other$collectIntervalMin == null : this$collectIntervalMin.equals(other$collectIntervalMin)) {
                        Object this$disabled = this.getDisabled();
                        Object other$disabled = other.getDisabled();
                        if (this$disabled == null ? other$disabled == null : this$disabled.equals(other$disabled)) {
                            Object this$bmcIp = this.getBmcIp();
                            Object other$bmcIp = other.getBmcIp();
                            if (this$bmcIp == null ? other$bmcIp == null : this$bmcIp.equals(other$bmcIp)) {
                                Object this$mac = this.getMac();
                                Object other$mac = other.getMac();
                                if (this$mac == null ? other$mac == null : this$mac.equals(other$mac)) {
                                    Object this$assetNo = this.getAssetNo();
                                    Object other$assetNo = other.getAssetNo();
                                    if (this$assetNo == null ? other$assetNo == null : this$assetNo.equals(other$assetNo)) {
                                        Object this$redfishVersion = this.getRedfishVersion();
                                        Object other$redfishVersion = other.getRedfishVersion();
                                        if (this$redfishVersion == null ? other$redfishVersion == null : this$redfishVersion.equals(other$redfishVersion)) {
                                            Object this$ipmiVersion = this.getIpmiVersion();
                                            Object other$ipmiVersion = other.getIpmiVersion();
                                            return this$ipmiVersion == null ? other$ipmiVersion == null : this$ipmiVersion.equals(other$ipmiVersion);
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof EndpointController.UpsertEndpointReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $credentialId = this.getCredentialId();
            result = result * 59 + ($credentialId == null ? 43 : $credentialId.hashCode());
            Object $collectIntervalMin = this.getCollectIntervalMin();
            result = result * 59 + ($collectIntervalMin == null ? 43 : $collectIntervalMin.hashCode());
            Object $disabled = this.getDisabled();
            result = result * 59 + ($disabled == null ? 43 : $disabled.hashCode());
            Object $bmcIp = this.getBmcIp();
            result = result * 59 + ($bmcIp == null ? 43 : $bmcIp.hashCode());
            Object $mac = this.getMac();
            result = result * 59 + ($mac == null ? 43 : $mac.hashCode());
            Object $assetNo = this.getAssetNo();
            result = result * 59 + ($assetNo == null ? 43 : $assetNo.hashCode());
            Object $redfishVersion = this.getRedfishVersion();
            result = result * 59 + ($redfishVersion == null ? 43 : $redfishVersion.hashCode());
            Object $ipmiVersion = this.getIpmiVersion();
            return result * 59 + ($ipmiVersion == null ? 43 : $ipmiVersion.hashCode());
        }

        
        @Override
        public String toString() {
            return "EndpointController.UpsertEndpointReq(bmcIp="
                + this.getBmcIp()
                + ", mac="
                + this.getMac()
                + ", assetNo="
                + this.getAssetNo()
                + ", credentialId="
                + this.getCredentialId()
                + ", redfishVersion="
                + this.getRedfishVersion()
                + ", ipmiVersion="
                + this.getIpmiVersion()
                + ", collectIntervalMin="
                + this.getCollectIntervalMin()
                + ", disabled="
                + this.getDisabled()
                + ")";
        }
    }
}
