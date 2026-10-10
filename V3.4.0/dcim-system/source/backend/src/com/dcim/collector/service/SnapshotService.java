package com.dcim.collector.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.collector.dto.CollectorDtos;
import com.dcim.collector.entity.BmcCredentialEntity;
import com.dcim.collector.entity.BmcEndpointEntity;
import com.dcim.collector.entity.HwSnapshotEntity;
import com.dcim.collector.mapper.BmcCredentialMapper;
import com.dcim.collector.mapper.BmcEndpointMapper;
import com.dcim.collector.mapper.HwSnapshotMapper;
import com.dcim.common.BusinessException;
import com.dcim.common.asset.AssetOpsPort;
import com.dcim.common.crypto.CryptBox;
import com.dcim.common.gpu.GpuCardPort;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotService {
    
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private final BmcEndpointMapper endpointMapper;
    private final BmcCredentialMapper credentialMapper;
    private final HwSnapshotMapper snapshotMapper;
    private final AssetOpsPort assetOpsPort;
    private final GpuCardPort gpuCardPort;
    private final CryptBox cryptBox;
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> dueEndpoints(int limit) {
        if (limit >= 1 && limit <= 50) {
            List<Map<String, Object>> rows = this.jdbc
                .queryForList(
                    "UPDATE bmc_endpoint SET next_collect_at = now() + make_interval(mins => collect_interval_min), updated_at = now() WHERE id IN (SELECT id FROM bmc_endpoint WHERE NOT disabled AND asset_no IS NOT NULL   AND (next_collect_at IS NULL OR next_collect_at <= now())   ORDER BY next_collect_at NULLS FIRST LIMIT "
                        + limit
                        + " FOR UPDATE SKIP LOCKED) RETURNING id, bmc_ip, asset_no, credential_id, collect_interval_min"
                );
            List<Map<String, Object>> out = new ArrayList<>();

            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("bmcIp", row.get("bmc_ip"));
                item.put("assetNo", row.get("asset_no"));
                if (row.get("credential_id") instanceof Number n) {
                    BmcCredentialEntity cred = (BmcCredentialEntity)this.credentialMapper.selectById(n.longValue());
                    if (cred != null) {
                        item.put("credential", Map.of("username", cred.getUsername(), "password", this.cryptBox.decrypt(cred.getPasswordEnc())));
                    }
                }

                out.add(item);
            }

            return out;
        } else {
            throw BusinessException.badRequest("limit 仅支持 1-50：" + limit);
        }
    }

    @Transactional
    public Map<String, Object> pushSnapshot(CollectorDtos.PushSnapshotReq req) {
        if (req.bmcIp() != null && !req.bmcIp().isBlank()) {
            BmcEndpointEntity endpoint = (BmcEndpointEntity)this.endpointMapper
                .selectOne(Wrappers.<BmcEndpointEntity>lambdaQuery().eq(BmcEndpointEntity::getBmcIp, req.bmcIp().trim()));
            if (endpoint == null) {
                throw BusinessException.badRequest("带外端点不存在（须先经发现建档或手工建）：" + req.bmcIp());
            } else {
                boolean ok = !Boolean.FALSE.equals(req.ok());
                HwSnapshotEntity snap = new HwSnapshotEntity();
                snap.setEndpointId(endpoint.getId());
                snap.setAssetNo(endpoint.getAssetNo());
                snap.setProtocol(req.protocol());
                snap.setSnapshot(req.snapshot() == null ? Map.of() : req.snapshot());
                snap.setOk(ok);
                snap.setError(ok ? null : req.error());
                snap.setCollectedAt(LocalDateTime.now());
                this.snapshotMapper.insert(snap);
                int failures = ok ? 0 : Math.min((endpoint.getConsecutiveFailures() == null ? 0 : endpoint.getConsecutiveFailures()) + 1, 1000000);
                this.jdbc
                    .update(
                        "UPDATE bmc_endpoint SET last_collect_at = now(), last_collect_ok = ?, consecutive_failures = ?, next_collect_at = now() + make_interval(mins => ?), updated_at = now() WHERE id = ?",
                        new Object[]{
                            ok, failures, ok ? (endpoint.getCollectIntervalMin() == null ? 60 : endpoint.getCollectIntervalMin()) : 5, endpoint.getId()
                        }
                    );
                int gpuCount = 0;
                if (ok && endpoint.getAssetNo() != null && req.gpuCards() != null && !req.gpuCards().isEmpty()) {
                    List<GpuCardPort.CardSnapshot> cards = new ArrayList<>();

                    for (CollectorDtos.GpuCardDto c : req.gpuCards()) {
                        cards.add(new GpuCardPort.CardSnapshot(c.slot(), c.gpuSn(), c.model(), c.memoryGb(), c.fw(), c.batchNo(), c.migCapable()));
                    }

                    gpuCount = this.gpuCardPort.upsertFromSnapshot(endpoint.getAssetNo(), cards, "SNAP-" + snap.getId());
                    this.assetOpsPort
                        .recordEvent(
                            endpoint.getAssetNo(),
                            "SNAPSHOT",
                            "SNAP-" + snap.getId(),
                            Map.of("protocol", req.protocol() == null ? "" : req.protocol(), "gpuCards", gpuCount)
                        );
                }

                return Map.of("snapshotId", snap.getId(), "gpuCards", gpuCount, "consecutiveFailures", failures);
            }
        } else {
            throw BusinessException.badRequest("bmcIp 不能为空");
        }
    }

    
    public SnapshotService(
        final BmcEndpointMapper endpointMapper,
        final BmcCredentialMapper credentialMapper,
        final HwSnapshotMapper snapshotMapper,
        final AssetOpsPort assetOpsPort,
        final GpuCardPort gpuCardPort,
        final CryptBox cryptBox,
        final JdbcTemplate jdbc
    ) {
        this.endpointMapper = endpointMapper;
        this.credentialMapper = credentialMapper;
        this.snapshotMapper = snapshotMapper;
        this.assetOpsPort = assetOpsPort;
        this.gpuCardPort = gpuCardPort;
        this.cryptBox = cryptBox;
        this.jdbc = jdbc;
    }
}
