package com.dcim.metrics;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"dcim.metrics.store"},
    havingValue = "memory",
    matchIfMissing = true
)
public class MemoryMetricStore implements MetricStore {
    /** 每 series 保留最近 N 个点，容量满时丢弃最旧点 */
    private static final int MAX_POINTS_PER_SERIES = 720;
    /** 全局 series（卡号）数软上限，新建 series 超限时淘汰最久未写入的 series */
    private static final int MAX_SERIES = 2000;
    private final Map<String, List<GpuMetric>> data = new ConcurrentHashMap<>();

    @Override
    public void ensureSchema() {
    }

    @Override
    public void saveGpuMetrics(List<GpuMetric> metrics) {
        if (metrics != null && !metrics.isEmpty()) {
            for (GpuMetric m : metrics) {
                if (!this.data.containsKey(m.cardNo()) && this.data.size() >= MAX_SERIES) {
                    this.evictStalestSeries();
                }

                List<GpuMetric> list = this.data.computeIfAbsent(m.cardNo(), k -> new ArrayList<>());
                synchronized (list) {
                    // 同一时刻重复上报以最新为准（去重），写入后保持按 ts 升序，查询语义不变
                    for (int i = list.size() - 1; i >= 0; i--) {
                        if (((GpuMetric)list.get(i)).ts().equals(m.ts())) {
                            list.remove(i);
                            break;
                        }
                    }

                    list.add(m);
                    list.sort(Comparator.comparing(GpuMetric::ts));
                    if (list.size() > MAX_POINTS_PER_SERIES) {
                        list.subList(0, list.size() - MAX_POINTS_PER_SERIES).clear();
                    }
                }
            }
        }
    }

    @Override
    public List<GpuMetric> queryWindow(String cardNo, LocalDateTime from, LocalDateTime to) {
        List<GpuMetric> list = this.data.get(cardNo);
        if (list == null) {
            return List.of();
        } else {
            List<GpuMetric> out = new ArrayList<>();

            synchronized (list) {
                for (GpuMetric m : list) {
                    if (!m.ts().isBefore(from) && !m.ts().isAfter(to)) {
                        out.add(m);
                    }
                }
            }

            return out;
        }
    }

    @Override
    public List<GpuMetric> recentSeries(String cardNo, int limitPoints) {
        List<GpuMetric> list = this.data.get(cardNo);
        if (list != null && !list.isEmpty()) {
            synchronized (list) {
                int n = list.size();
                int from = Math.max(0, n - Math.max(1, limitPoints));
                return new ArrayList<>(list.subList(from, n));
            }
        } else {
            return List.of();
        }
    }

    /** 淘汰最新点最旧的 series（软上限，并发下允许短暂略超，不追求精确） */
    private void evictStalestSeries() {
        String target = null;
        LocalDateTime oldest = null;

        for (Map.Entry<String, List<GpuMetric>> entry : this.data.entrySet()) {
            List<GpuMetric> series = entry.getValue();
            LocalDateTime newest;
            synchronized (series) {
                newest = series.isEmpty() ? null : series.get(series.size() - 1).ts();
            }

            if (newest == null) {
                this.data.remove(entry.getKey());
                return;
            }

            if (oldest == null || newest.isBefore(oldest)) {
                oldest = newest;
                target = entry.getKey();
            }
        }

        if (target != null) {
            this.data.remove(target);
        }
    }
}
