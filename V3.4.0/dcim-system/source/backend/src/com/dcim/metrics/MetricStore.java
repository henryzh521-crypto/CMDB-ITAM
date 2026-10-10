package com.dcim.metrics;

import java.time.LocalDateTime;
import java.util.List;

public interface MetricStore {
    void ensureSchema();

    void saveGpuMetrics(List<GpuMetric> metrics);

    List<GpuMetric> queryWindow(String cardNo, LocalDateTime from, LocalDateTime to);

    List<GpuMetric> recentSeries(String cardNo, int limitPoints);
}
