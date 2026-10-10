package com.dcim.metrics;

import java.time.LocalDateTime;

public record GpuMetric(LocalDateTime ts, String cardNo, String hostAssetNo, Double tempC, Double powerW, Double utilPct, Integer eccDbe, Integer xidErrors) {
}
