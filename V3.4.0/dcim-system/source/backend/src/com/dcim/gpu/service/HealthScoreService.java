package com.dcim.gpu.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.notify.NotificationPort;
import com.dcim.common.workorder.WorkorderPort;
import com.dcim.gpu.entity.GpuCardEntity;
import com.dcim.gpu.entity.GpuHealthConfigEntity;
import com.dcim.gpu.mapper.GpuCardMapper;
import com.dcim.gpu.mapper.GpuHealthConfigMapper;
import com.dcim.metrics.GpuMetric;
import com.dcim.metrics.MetricStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class HealthScoreService {
    
    private static final Logger log = LoggerFactory.getLogger(HealthScoreService.class);
    static final String NOTIFY_TYPE = "GPU_ALERT";
    private final GpuCardMapper cardMapper;
    private final GpuHealthConfigMapper configMapper;
    private final MetricStore metricStore;
    private final WorkorderPort workorderPort;
    private final NotificationPort notificationPort;

    @Scheduled(
        fixedDelayString = "${dcim.gpu.score-fixed-delay-ms:3600000}"
    )
    public void scheduledRun() {
        try {
            int n = this.scoreAll();
            if (n > 0) {
                log.info("[gpu] 本轮评分完成，高危建单 {} 张", n);
            }
        } catch (Exception var2) {
            log.warn("[gpu] 健康度评分失败（不影响下次调度）: {}", var2.getMessage(), var2);
        }
    }

    public int scoreAll() {
        HealthScoreCalculator.Weights w = this.loadWeights();
        LocalDateTime now = LocalDateTime.now();
        List<GpuCardEntity> cards = this.cardMapper.selectList(null);
        int created = 0;

        for (GpuCardEntity card : cards) {
            try {
                List<GpuMetric> window = this.metricStore.queryWindow(card.getCardNo(), now.minusHours(24L), now);
                HealthScoreCalculator.Score score = HealthScoreCalculator.compute(window, w);
                GpuCardEntity upd = new GpuCardEntity();
                upd.setHealthScore(score.total());
                upd.setHealthLevel(score.level());
                upd.setLastScoredAt(now);
                this.cardMapper.update(upd, Wrappers.<GpuCardEntity>lambdaUpdate().eq(GpuCardEntity::getCardNo, card.getCardNo()));
                if ("高危".equals(score.level()) && this.createCriticalWo(card, score)) {
                    created++;
                }
            } catch (Exception var10) {
                if (var10.getClass().getName().startsWith("org.springframework.dao.")) {
                    log.error("[gpu] 卡 {} 评分触发数据库异常（跳过该卡）: {}", new Object[]{card.getCardNo(), var10.getMessage(), var10});
                } else {
                    log.warn("[gpu] 卡 {} 评分失败（跳过继续）: {}", card.getCardNo(), var10.getMessage());
                }
            }
        }

        return created;
    }

    public HealthScoreCalculator.Weights loadWeights() {
        GpuHealthConfigEntity c = (GpuHealthConfigEntity)this.configMapper.selectById(1);
        return c == null
            ? HealthScoreCalculator.Weights.defaults()
            : new HealthScoreCalculator.Weights(
                c.getWeightEcc() == null ? 40 : c.getWeightEcc(),
                c.getWeightTemp() == null ? 25 : c.getWeightTemp(),
                c.getWeightXid() == null ? 20 : c.getWeightXid(),
                c.getWeightPower() == null ? 15 : c.getWeightPower(),
                c.getTempLimitC() == null ? 85 : c.getTempLimitC()
            );
    }

    private boolean createCriticalWo(GpuCardEntity card, HealthScoreCalculator.Score score) {
        String title = "GPU高危-" + card.getCardNo();
        if (this.workorderPort.hasOpenWoByTitle(title)) {
            return false;
        } else {
            Map<String, Object> bd = score.breakdown();
            String desc = "GPU 卡 "
                + card.getCardNo()
                + " 健康度评分 "
                + score.total()
                + "（高危），证据：ECC双位增量="
                + bd.get("eccDbeDelta")
                + "，XID增量="
                + bd.get("xidDelta")
                + "，温度越限样本="
                + bd.get("tempViolations")
                + "/"
                + bd.get("samples")
                + "（限 "
                + bd.get("tempLimitC")
                + "℃），功耗CV="
                + bd.get("powerCv")
                + "。请尽快检修，必要时隔离换卡。";
            List<Object> details = List.of(
                Map.of("cardNo", card.getCardNo(), "gpuSn", card.getGpuSn() == null ? "" : card.getGpuSn(), "score", score.total(), "breakdown", bd)
            );
            String woNo = this.workorderPort
                .createSystemWo("故障维修", title, desc, "高", card.getHostAssetNo() == null ? List.of() : List.of(card.getHostAssetNo()), details);
            this.notificationPort.notifyRole("R-OPS", "GPU_ALERT", "GPU 健康度高危：" + card.getCardNo(), "评分 " + score.total() + "（高危），已自动生成检修工单 " + woNo, woNo);
            log.warn("[gpu] 卡 {} 高危（{}），已建工单 {}", new Object[]{card.getCardNo(), score.total(), woNo});
            return true;
        }
    }

    
    public HealthScoreService(
        final GpuCardMapper cardMapper,
        final GpuHealthConfigMapper configMapper,
        final MetricStore metricStore,
        final WorkorderPort workorderPort,
        final NotificationPort notificationPort
    ) {
        this.cardMapper = cardMapper;
        this.configMapper = configMapper;
        this.metricStore = metricStore;
        this.workorderPort = workorderPort;
        this.notificationPort = notificationPort;
    }
}
