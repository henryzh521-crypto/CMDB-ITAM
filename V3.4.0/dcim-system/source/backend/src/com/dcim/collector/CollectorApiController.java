package com.dcim.collector;

import com.dcim.collector.dto.CollectorDtos;
import com.dcim.collector.service.DiscoverService;
import com.dcim.collector.service.SnapshotService;
import com.dcim.common.ApiResult;
import com.dcim.metrics.GpuMetric;
import com.dcim.metrics.MetricStore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/collector"})
public class CollectorApiController {
    private final DiscoverService discoverService;
    private final SnapshotService snapshotService;
    private final MetricStore metricStore;

    @GetMapping({"/tasks/next"})
    public ApiResult<Map<String, Object>> nextTask() {
        return ApiResult.ok(this.discoverService.claimNextTask());
    }

    @PostMapping({"/tasks/{taskNo}/results"})
    public ApiResult<Map<String, Object>> pushResults(@PathVariable String taskNo, @RequestBody CollectorDtos.PushResultsReq req) {
        return ApiResult.ok(this.discoverService.pushResults(taskNo, req));
    }

    @GetMapping({"/endpoints/due"})
    public ApiResult<List<Map<String, Object>>> dueEndpoints(@RequestParam(defaultValue = "10") int limit) {
        return ApiResult.ok(this.snapshotService.dueEndpoints(limit));
    }

    @PostMapping({"/snapshots"})
    public ApiResult<Map<String, Object>> pushSnapshot(@RequestBody CollectorDtos.PushSnapshotReq req) {
        return ApiResult.ok(this.snapshotService.pushSnapshot(req));
    }

    @PostMapping({"/gpu-metrics"})
    public ApiResult<Integer> pushMetrics(@RequestBody CollectorDtos.PushMetricsReq req) {
        if (req.metrics() != null && !req.metrics().isEmpty()) {
            List<GpuMetric> batch = new ArrayList<>(req.metrics().size());

            for (CollectorDtos.GpuMetricDto m : req.metrics()) {
                if (m.cardNo() != null && !m.cardNo().isBlank()) {
                    LocalDateTime ts;
                    try {
                        ts = m.ts() == null ? LocalDateTime.now() : LocalDateTime.parse(m.ts());
                    } catch (Exception var7) {
                        ts = LocalDateTime.now();
                    }

                    batch.add(
                        new GpuMetric(ts, m.cardNo().trim().toUpperCase(), m.hostAssetNo(), m.tempC(), m.powerW(), m.utilPct(), m.eccDbe(), m.xidErrors())
                    );
                }
            }

            this.metricStore.saveGpuMetrics(batch);
            return ApiResult.ok(batch.size());
        } else {
            return ApiResult.ok(0);
        }
    }

    
    public CollectorApiController(final DiscoverService discoverService, final SnapshotService snapshotService, final MetricStore metricStore) {
        this.discoverService = discoverService;
        this.snapshotService = snapshotService;
        this.metricStore = metricStore;
    }
}
