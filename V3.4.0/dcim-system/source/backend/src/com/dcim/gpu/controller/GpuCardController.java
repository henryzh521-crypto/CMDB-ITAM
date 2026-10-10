package com.dcim.gpu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.gpu.entity.GpuCardEntity;
import com.dcim.gpu.entity.GpuHealthConfigEntity;
import com.dcim.gpu.mapper.GpuCardMapper;
import com.dcim.gpu.mapper.GpuHealthConfigMapper;
import com.dcim.gpu.service.HealthScoreCalculator;
import com.dcim.gpu.service.HealthScoreService;
import com.dcim.metrics.GpuMetric;
import com.dcim.metrics.MetricStore;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/gpu"})
public class GpuCardController {
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final GpuCardMapper cardMapper;
    private final GpuHealthConfigMapper configMapper;
    private final MetricStore metricStore;
    private final HealthScoreService healthScoreService;

    @GetMapping({"/cards"})
    @RequirePerm(
        module = "gpu"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String hostAssetNo,
        @RequestParam(required = false) String level,
        @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<GpuCardEntity> qw = Wrappers.lambdaQuery();
        if (hostAssetNo != null && !hostAssetNo.isBlank()) {
            qw.eq(GpuCardEntity::getHostAssetNo, hostAssetNo.trim());
        }

        if (level != null && !level.isBlank()) {
            qw.eq(GpuCardEntity::getHealthLevel, level.trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(
                w -> ((((((w.like(
                                                GpuCardEntity::getCardNo, kw
                                            ))
                                            .or())
                                        .like(GpuCardEntity::getGpuSn, kw))
                                    .or())
                                .like(GpuCardEntity::getModel, kw))
                            .or())
                        .like(GpuCardEntity::getHostAssetNo, kw)
            );
        }

        qw.orderByAsc(GpuCardEntity::getCardNo);
        Page<GpuCardEntity> p = new Page(page, size);
        this.cardMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, this::toRow));
    }

    @GetMapping({"/cards/{cardNo}/series"})
    @RequirePerm(
        module = "gpu"
    )
    public ApiResult<List<Map<String, Object>>> series(@PathVariable String cardNo, @RequestParam(defaultValue = "288") int points) {
        int limit = Math.min(Math.max(points, 1), 2000);
        List<GpuMetric> series = this.metricStore.recentSeries(cardNo.trim().toUpperCase(), limit);
        List<Map<String, Object>> rows = new ArrayList<>(series.size());

        for (GpuMetric m : series) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ts", m.ts().format(TS_FMT));
            row.put("tempC", m.tempC());
            row.put("powerW", m.powerW());
            row.put("utilPct", m.utilPct());
            row.put("eccDbe", m.eccDbe());
            row.put("xidErrors", m.xidErrors());
            rows.add(row);
        }

        return ApiResult.ok(rows);
    }

    @PostMapping({"/score-now"})
    @RequirePerm(
        module = "gpu",
        write = true
    )
    @Audit(
        module = "gpu",
        action = "手动评分"
    )
    public ApiResult<Integer> scoreNow() {
        return ApiResult.ok(this.healthScoreService.scoreAll());
    }

    @GetMapping({"/health-config"})
    @RequirePerm(
        module = "gpu"
    )
    public ApiResult<Map<String, Object>> getConfig() {
        HealthScoreCalculator.Weights w = this.healthScoreService.loadWeights();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("weightEcc", w.ecc());
        row.put("weightTemp", w.temp());
        row.put("weightXid", w.xid());
        row.put("weightPower", w.power());
        row.put("tempLimitC", w.tempLimitC());
        return ApiResult.ok(row);
    }

    @PutMapping({"/health-config"})
    @RequirePerm(
        module = "gpu",
        write = true
    )
    @Audit(
        module = "gpu",
        action = "修改评分权重"
    )
    public ApiResult<Boolean> putConfig(@RequestBody GpuHealthConfigEntity req) {
        int ecc = req.getWeightEcc() == null ? 0 : req.getWeightEcc();
        int temp = req.getWeightTemp() == null ? 0 : req.getWeightTemp();
        int xid = req.getWeightXid() == null ? 0 : req.getWeightXid();
        int power = req.getWeightPower() == null ? 0 : req.getWeightPower();
        if (ecc + temp + xid + power != 100) {
            throw new BusinessException(400, "四项权重之和必须等于 100");
        } else {
            int limit = req.getTempLimitC() == null ? 85 : req.getTempLimitC();
            if (limit >= 40 && limit <= 120) {
                GpuHealthConfigEntity upd = new GpuHealthConfigEntity();
                upd.setId(1);
                upd.setWeightEcc(ecc);
                upd.setWeightTemp(temp);
                upd.setWeightXid(xid);
                upd.setWeightPower(power);
                upd.setTempLimitC(limit);
                upd.setUpdatedAt(LocalDateTime.now());
                if (this.configMapper.selectById(1) == null) {
                    this.configMapper.insert(upd);
                } else {
                    this.configMapper.updateById(upd);
                }

                return ApiResult.ok(true);
            } else {
                throw new BusinessException(400, "温度阈值须在 40-120℃ 之间");
            }
        }
    }

    private Map<String, Object> toRow(GpuCardEntity c) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cardNo", c.getCardNo());
        row.put("hostAssetNo", c.getHostAssetNo());
        row.put("slot", c.getSlot());
        row.put("gpuSn", c.getGpuSn());
        row.put("model", c.getModel());
        row.put("memoryGb", c.getMemoryGb());
        row.put("fw", c.getFw());
        row.put("batchNo", c.getBatchNo());
        row.put("migCapable", c.getMigCapable());
        row.put("healthScore", c.getHealthScore());
        row.put("healthLevel", c.getHealthLevel());
        row.put("prevSn", c.getPrevSn());
        row.put("swapHistory", c.getSwapHistory());
        row.put("lastScoredAt", c.getLastScoredAt());
        row.put("updatedAt", c.getUpdatedAt());
        return row;
    }

    
    public GpuCardController(
        final GpuCardMapper cardMapper, final GpuHealthConfigMapper configMapper, final MetricStore metricStore, final HealthScoreService healthScoreService
    ) {
        this.cardMapper = cardMapper;
        this.configMapper = configMapper;
        this.metricStore = metricStore;
        this.healthScoreService = healthScoreService;
    }
}
