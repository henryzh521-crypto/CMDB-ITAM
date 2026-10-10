package com.dcim.collector.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.collector.BmcIp;
import com.dcim.collector.Cidr;
import com.dcim.collector.IreEngine;
import com.dcim.collector.dto.CollectorDtos;
import com.dcim.collector.entity.BmcCredentialEntity;
import com.dcim.collector.entity.BmcEndpointEntity;
import com.dcim.collector.entity.DiscoverAssetRef;
import com.dcim.collector.entity.DiscoverResultEntity;
import com.dcim.collector.entity.DiscoverTaskEntity;
import com.dcim.collector.mapper.BmcCredentialMapper;
import com.dcim.collector.mapper.BmcEndpointMapper;
import com.dcim.collector.mapper.DiscoverAssetQueryMapper;
import com.dcim.collector.mapper.DiscoverResultMapper;
import com.dcim.collector.mapper.DiscoverTaskMapper;
import com.dcim.common.BusinessException;
import com.dcim.common.asset.AssetOpsPort;
import com.dcim.common.crypto.CryptBox;
import com.dcim.common.seq.SeqNoGenerator;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscoverService {
    
    private static final Logger log = LoggerFactory.getLogger(DiscoverService.class);
    private final DiscoverTaskMapper taskMapper;
    private final DiscoverResultMapper resultMapper;
    private final BmcEndpointMapper endpointMapper;
    private final BmcCredentialMapper credentialMapper;
    private final DiscoverAssetQueryMapper assetQueryMapper;
    private final AssetOpsPort assetOpsPort;
    private final CryptBox cryptBox;
    private final SeqNoGenerator seqNoGenerator;
    private final JdbcTemplate jdbc;
    @Value("${dcim.ire.conflict-policy:manual}")
    private String conflictPolicy;

    @Transactional
    public DiscoverTaskEntity createTask(String name, String cidr, String protocol, Long credentialId, String regionCode, String createdBy) {
        if (name != null && !name.isBlank()) {
            Cidr.validate(cidr);
            String proto = protocol == null ? "" : protocol.trim().toUpperCase();
            if (!"REDFISH".equals(proto) && !"IPMI".equals(proto)) {
                throw BusinessException.badRequest("协议仅支持 REDFISH / IPMI：" + protocol);
            } else if (credentialId != null && this.credentialMapper.selectById(credentialId) == null) {
                throw BusinessException.badRequest("凭据不存在：" + credentialId);
            } else {
                DiscoverTaskEntity t = new DiscoverTaskEntity();
                t.setTaskNo(this.seqNoGenerator.next("DS-" + SeqNoGenerator.todaySegment(), 4));
                t.setName(name.trim());
                t.setCidr(cidr.trim());
                t.setProtocol(proto);
                t.setCredentialId(credentialId);
                t.setRegionCode(regionCode != null && !regionCode.isBlank() ? regionCode.trim() : null);
                t.setStatus("待执行");
                t.setScannedIps(0);
                t.setFoundCount(0);
                t.setMatchedCount(0);
                t.setPendingCount(0);
                t.setCreatedBy(createdBy);
                t.setCreatedAt(LocalDateTime.now());
                this.taskMapper.insert(t);
                return t;
            }
        } else {
            throw BusinessException.badRequest("任务名称不能为空");
        }
    }

    @Transactional
    public void runNow(String taskNo) {
        DiscoverTaskEntity t = this.requireTask(taskNo);
        if (!"待执行".equals(t.getStatus())) {
            DiscoverTaskEntity upd = new DiscoverTaskEntity();
            upd.setStatus("待执行");
            this.taskMapper.update(upd, Wrappers.<DiscoverTaskEntity>lambdaUpdate().eq(DiscoverTaskEntity::getId, t.getId()));
        }
    }

    @Transactional
    public void deleteTask(String taskNo) {
        DiscoverTaskEntity t = this.requireTask(taskNo);
        if ("扫描中".equals(t.getStatus())) {
            throw BusinessException.conflict("任务「" + taskNo + "」扫描中，不允许删除");
        } else {
            Long pending = this.resultMapper
                .selectCount(
                    (Wrappers.<DiscoverResultEntity>lambdaQuery().eq(DiscoverResultEntity::getTaskId, t.getId()))
                        .eq(DiscoverResultEntity::getStatus, "待认领")
                );
            if (pending != null && pending > 0L) {
                throw BusinessException.conflict("任务「" + taskNo + "」尚有 " + pending + " 条待认领结果，请先处理或忽略");
            } else {
                this.resultMapper.delete(Wrappers.<DiscoverResultEntity>lambdaQuery().eq(DiscoverResultEntity::getTaskId, t.getId()));
                this.taskMapper.deleteById(t.getId());
            }
        }
    }

    public Map<String, Object> claimNextTask() {
        List<Map<String, Object>> rows = this.jdbc
            .queryForList(
                "UPDATE discover_task SET status = '扫描中', last_run_at = now() WHERE id = (SELECT id FROM discover_task WHERE status = '待执行' ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id, task_no, cidr, protocol, credential_id, region_code"
            );
        if (rows.isEmpty()) {
            return null;
        } else {
            Map<String, Object> row = rows.get(0);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("taskNo", row.get("task_no"));
            out.put("cidr", row.get("cidr"));
            out.put("protocol", row.get("protocol"));
            out.put("regionCode", row.get("region_code"));
            if (row.get("credential_id") instanceof Number n) {
                BmcCredentialEntity cred = (BmcCredentialEntity)this.credentialMapper.selectById(n.longValue());
                if (cred != null) {
                    out.put("credential", Map.of("username", cred.getUsername(), "password", this.cryptBox.decrypt(cred.getPasswordEnc())));
                }
            }

            return out;
        }
    }

    @Transactional
    public Map<String, Object> pushResults(String taskNo, CollectorDtos.PushResultsReq req) {
        DiscoverTaskEntity task = this.requireTask(taskNo);
        if (!"扫描中".equals(task.getStatus())) {
            throw BusinessException.conflict("任务「" + taskNo + "」当前状态「" + task.getStatus() + "」，不接受结果推送（仅扫描中）");
        } else {
            int matched = 0;
            int pending = 0;
            int skipped = 0;
            if (req.found() != null) {
                for (CollectorDtos.FoundDevice d : req.found()) {
                    if (d.bmcIp() != null && !d.bmcIp().isBlank()) {
                        String outcome = this.ingestDevice(task, d);
                        switch (outcome) {
                            case "MATCH":
                                matched++;
                                break;
                            case "PENDING":
                                pending++;
                                break;
                            default:
                                skipped++;
                        }
                    } else {
                        skipped++;
                    }
                }
            }

            this.jdbc
                .update(
                    "UPDATE discover_task SET scanned_ips = ?, found_count = (SELECT COUNT(*) FROM discover_result WHERE task_id = ?), matched_count = (SELECT COUNT(*) FROM discover_result WHERE task_id = ?   AND status IN ('已匹配','已认领')), pending_count = (SELECT COUNT(*) FROM discover_result WHERE task_id = ? AND status = '待认领') WHERE id = ?",
                    new Object[]{req.scannedIps() == null ? 0 : req.scannedIps(), task.getId(), task.getId(), task.getId(), task.getId()}
                );
            if (Boolean.TRUE.equals(req.done())) {
                DiscoverTaskEntity upd = new DiscoverTaskEntity();
                upd.setStatus(Boolean.FALSE.equals(req.ok()) ? "失败" : "已完成");
                if (req.error() != null && !req.error().isBlank()) {
                    log.warn("[discover] 任务 {} 失败：{}", taskNo, req.error());
                }

                this.taskMapper.update(upd, Wrappers.<DiscoverTaskEntity>lambdaUpdate().eq(DiscoverTaskEntity::getId, task.getId()));
            }

            return Map.of("matched", matched, "pending", pending, "skipped", skipped);
        }
    }

    @Transactional
    public DiscoverResultEntity claimResult(Long resultId, CollectorDtos.ClaimReq req) {
        DiscoverResultEntity r = (DiscoverResultEntity)this.resultMapper.selectById(resultId);
        if (r == null) {
            throw BusinessException.notFound("发现结果不存在：" + resultId);
        } else if (!"待认领".equals(r.getStatus())) {
            throw BusinessException.conflict("结果当前状态「" + r.getStatus() + "」，仅待认领可认领");
        } else {
            DiscoverTaskEntity task = (DiscoverTaskEntity)this.taskMapper.selectById(r.getTaskId());
            String normSn = IreEngine.normSn(r.getSn());
            DiscoverAssetRef hit = normSn == null ? null : this.assetQueryMapper.selectBySn(normSn);
            if (hit != null) {
                this.doMatch(r, task, hit.getAssetNo());
                r.setStatus("已匹配");
                r.setDecision(this.decision("SN", "MATCH", hit.getAssetNo(), "人工确认匹配（原裁决待人工）", List.of()));
            } else {
                if (req.name() == null || req.name().isBlank()) {
                    throw BusinessException.badRequest("认领建档需提供资产名称（name）");
                }

                String region = req.regionCode() != null && !req.regionCode().isBlank()
                    ? req.regionCode().trim()
                    : (task == null ? null : task.getRegionCode());
                Map<String, Object> attrs = new LinkedHashMap<>();
                attrs.put("source", "自动发现");
                attrs.put("bmcIp", r.getBmcIp());
                if (r.getManufacturer() != null) {
                    attrs.put("manufacturer", r.getManufacturer());
                }

                if (r.getModel() != null) {
                    attrs.put("model", r.getModel());
                }

                String assetNo = this.assetOpsPort
                    .createAssetForRfid(
                        req.name().trim(), normSn, req.categoryCode(), req.modelCode(), "SELF", region, attrs, task == null ? null : task.getTaskNo()
                    );
                r.setAssetNo(assetNo);
                r.setStatus("已认领");
                r.setClaimedAt(LocalDateTime.now());
                r.setDecision(this.decision("NONE", "CLAIM", assetNo, "人工认领建档", List.of()));
                this.linkEndpoint(r, assetNo, task);
            }

            this.resultMapper.updateById(r);
            return r;
        }
    }

    @Transactional
    public void ignoreResult(Long resultId) {
        DiscoverResultEntity r = (DiscoverResultEntity)this.resultMapper.selectById(resultId);
        if (r == null) {
            throw BusinessException.notFound("发现结果不存在：" + resultId);
        } else if (!"待认领".equals(r.getStatus())) {
            throw BusinessException.conflict("结果当前状态「" + r.getStatus() + "」，仅待认领可忽略");
        } else {
            r.setStatus("已忽略");
            this.resultMapper.updateById(r);
        }
    }

    private String ingestDevice(DiscoverTaskEntity task, CollectorDtos.FoundDevice d) {
        // 修复：agent 上报的 bmcIp 此前原样入库（BmcIp.validate 是死代码），接入校验（IPv4 +
        // 保留段）；违例设备拒绝并记 warn（跳过该台，不中断整批结果推送）
        String bmcIp;
        try {
            bmcIp = BmcIp.validate(d.bmcIp());
        } catch (BusinessException ex) {
            log.warn("[discover] 任务 {} 拒绝非法 bmcIp 设备上报：{}（{}）", new Object[]{task.getTaskNo(), d.bmcIp(), ex.getMessage()});
            return "SKIP";
        }
        String normSn = IreEngine.normSn(d.sn());
        BmcEndpointEntity endpoint = (BmcEndpointEntity)this.endpointMapper.selectOne(Wrappers.<BmcEndpointEntity>lambdaQuery().eq(BmcEndpointEntity::getBmcIp, bmcIp));
        if (endpoint == null) {
            endpoint = new BmcEndpointEntity();
            endpoint.setBmcIp(bmcIp);
            endpoint.setRedfishVersion(d.redfishVersion());
            endpoint.setIpmiVersion(d.ipmiVersion());
            endpoint.setCollectIntervalMin(60);
            endpoint.setConsecutiveFailures(0);
            endpoint.setDisabled(false);
            endpoint.setCreatedAt(LocalDateTime.now());
            endpoint.setUpdatedAt(LocalDateTime.now());
            try {
                this.endpointMapper.insert(endpoint);
            } catch (org.springframework.dao.DuplicateKeyException var10) {
                // v3.5.2：多 agent 并发上报同一 bmcIp 时的竞态由唯一约束兜底，跳过该台（下轮会走更新分支）
                log.warn("[discover] 任务 {} 并发建档冲突，跳过 bmcIp={}（下轮采集自动合并）", task.getTaskNo(), bmcIp);
                return "SKIP";
            }
        } else if (d.redfishVersion() != null || d.ipmiVersion() != null) {
            BmcEndpointEntity upd = new BmcEndpointEntity();
            if (d.redfishVersion() != null) {
                upd.setRedfishVersion(d.redfishVersion());
            }

            if (d.ipmiVersion() != null) {
                upd.setIpmiVersion(d.ipmiVersion());
            }

            upd.setUpdatedAt(LocalDateTime.now());
            this.endpointMapper.update(upd, Wrappers.<BmcEndpointEntity>lambdaUpdate().eq(BmcEndpointEntity::getId, endpoint.getId()));
        }

        DiscoverResultEntity exist = (DiscoverResultEntity)this.resultMapper
            .selectOne(
                (Wrappers.<DiscoverResultEntity>lambdaQuery().eq(DiscoverResultEntity::getBmcIp, bmcIp))
                    .eq(DiscoverResultEntity::getSn, normSn == null ? "" : normSn)
            );
        if (exist == null || !"已忽略".equals(exist.getStatus()) && exist.getAssetNo() == null) {
            DiscoverAssetRef hit = normSn == null ? null : this.assetQueryMapper.selectBySn(normSn);
            IreEngine.IreResult ire = IreEngine.resolve(
                d.sn(), d.model(), this.conflictPolicy, hit == null ? null : new IreEngine.AssetHit(hit.getAssetNo(), hit.getSn(), hit.getModelCode())
            );
            DiscoverResultEntity r = exist == null ? new DiscoverResultEntity() : exist;
            r.setTaskId(task.getId());
            r.setBmcIp(bmcIp);
            r.setSn(normSn == null ? "" : normSn);
            r.setManufacturer(d.manufacturer());
            r.setModel(d.model());
            r.setRaw(d.raw() == null ? Map.of() : d.raw());
            r.setEndpointId(endpoint.getId());
            r.setDecision(ire.toDecision());
            if ("MATCH".equals(ire.action())) {
                this.doMatch(r, task, ire.matchedAssetNo());
                r.setStatus("已匹配");
            } else {
                r.setStatus("待认领");
            }

            r.setCreatedAt(exist == null ? LocalDateTime.now() : r.getCreatedAt());
            if (exist == null) {
                this.resultMapper.insert(r);
            } else {
                this.resultMapper.updateById(r);
            }

            return "MATCH".equals(ire.action()) ? "MATCH" : "PENDING";
        } else {
            return "SKIP";
        }
    }

    private void doMatch(DiscoverResultEntity r, DiscoverTaskEntity task, String assetNo) {
        Map<String, Object> hw = new LinkedHashMap<>();
        hw.put("manufacturer", r.getManufacturer());
        hw.put("model", r.getModel());
        this.assetOpsPort.enrichFromDiscovery(assetNo, r.getBmcIp(), hw, task == null ? null : task.getTaskNo());
        r.setAssetNo(assetNo);
        this.linkEndpoint(r, assetNo, task);
    }

    private void linkEndpoint(DiscoverResultEntity r, String assetNo, DiscoverTaskEntity task) {
        if (r.getEndpointId() != null) {
            BmcEndpointEntity endpoint = (BmcEndpointEntity)this.endpointMapper.selectById(r.getEndpointId());
            if (endpoint != null) {
                BmcEndpointEntity upd = new BmcEndpointEntity();
                upd.setAssetNo(assetNo);
                if (endpoint.getCredentialId() == null && task != null && task.getCredentialId() != null) {
                    upd.setCredentialId(task.getCredentialId());
                }

                upd.setNextCollectAt(LocalDateTime.now());
                upd.setUpdatedAt(LocalDateTime.now());
                this.endpointMapper.update(upd, Wrappers.<BmcEndpointEntity>lambdaUpdate().eq(BmcEndpointEntity::getId, endpoint.getId()));
            }
        }
    }

    private Map<String, Object> decision(String matchType, String action, String assetNo, String reason, List<Map<String, Object>> conflicts) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("matchType", matchType);
        m.put("action", action);
        m.put("matchedAssetNo", assetNo);
        m.put("reason", reason);
        m.put("conflicts", conflicts);
        return m;
    }

    private DiscoverTaskEntity requireTask(String taskNo) {
        DiscoverTaskEntity t = (DiscoverTaskEntity)this.taskMapper.selectOne(Wrappers.<DiscoverTaskEntity>lambdaQuery().eq(DiscoverTaskEntity::getTaskNo, taskNo));
        if (t == null) {
            throw BusinessException.notFound("发现任务不存在：" + taskNo);
        } else {
            return t;
        }
    }

    
    public DiscoverService(
        final DiscoverTaskMapper taskMapper,
        final DiscoverResultMapper resultMapper,
        final BmcEndpointMapper endpointMapper,
        final BmcCredentialMapper credentialMapper,
        final DiscoverAssetQueryMapper assetQueryMapper,
        final AssetOpsPort assetOpsPort,
        final CryptBox cryptBox,
        final SeqNoGenerator seqNoGenerator,
        final JdbcTemplate jdbc
    ) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.endpointMapper = endpointMapper;
        this.credentialMapper = credentialMapper;
        this.assetQueryMapper = assetQueryMapper;
        this.assetOpsPort = assetOpsPort;
        this.cryptBox = cryptBox;
        this.seqNoGenerator = seqNoGenerator;
        this.jdbc = jdbc;
    }
}
