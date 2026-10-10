package com.dcim.gpu.service;

import com.dcim.metrics.GpuMetric;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class HealthScoreCalculator {
    static final int TH_GOOD = 90;
    static final int TH_FINE = 75;
    static final int TH_WATCH = 60;
    static final int TH_DEGRADED = 40;

    private HealthScoreCalculator() {
    }

    public static HealthScoreCalculator.Score compute(List<GpuMetric> window, HealthScoreCalculator.Weights w) {
        Map<String, Object> bd = new LinkedHashMap<>();
        if (window != null && !window.isEmpty()) {
            int dbeDelta = delta(window, GpuMetric::eccDbe);
            int xidDelta = delta(window, GpuMetric::xidErrors);
            int samples = window.size();
            long tempViolations = window.stream().filter(m -> m.tempC() != null && m.tempC() > (double)w.tempLimitC()).count();
            double meanPower = window.stream().map(GpuMetric::powerW).filter(p -> p != null).mapToDouble(Double::doubleValue).average().orElse(0.0);
            double stdPower = 0.0;
            if (meanPower > 0.0) {
                double var = window.stream().map(GpuMetric::powerW).filter(p -> p != null).mapToDouble(p -> (p - meanPower) * (p - meanPower)).sum()
                    / (double)Math.max(1L, window.stream().filter(m -> m.powerW() != null).count());
                stdPower = Math.sqrt(var);
            }

            double sevEcc = Math.min(1.0, (double)Math.max(0, dbeDelta) / 2.0);
            double sevXid = Math.min(1.0, (double)Math.max(0, xidDelta) / 5.0);
            double sevTemp = samples == 0 ? 0.0 : Math.min(1.0, (double)tempViolations / (double)samples);
            double sevPower = meanPower <= 0.0 ? 0.0 : Math.min(1.0, stdPower / meanPower / 0.5);
            int total = (int)Math.max(
                0L, Math.round(100.0 - sevEcc * (double)w.ecc() - sevTemp * (double)w.temp() - sevXid * (double)w.xid() - sevPower * (double)w.power())
            );
            bd.put("samples", samples);
            bd.put("eccDbeDelta", dbeDelta);
            bd.put("xidDelta", xidDelta);
            bd.put("tempViolations", tempViolations);
            bd.put("tempLimitC", w.tempLimitC());
            bd.put("powerCv", meanPower > 0.0 ? (double)Math.round(stdPower / meanPower * 1000.0) / 1000.0 : 0.0);
            bd.put("sevEcc", round2(sevEcc));
            bd.put("sevXid", round2(sevXid));
            bd.put("sevTemp", round2(sevTemp));
            bd.put("sevPower", round2(sevPower));
            return new HealthScoreCalculator.Score(total, levelOf(total), bd);
        } else {
            bd.put("samples", 0);
            return new HealthScoreCalculator.Score(100, levelOf(100), bd);
        }
    }

    public static String levelOf(int total) {
        if (total >= 90) {
            return "优";
        } else if (total >= 75) {
            return "良";
        } else if (total >= 60) {
            return "观察";
        } else {
            return total >= 40 ? "劣化" : "高危";
        }
    }

    private static int delta(List<GpuMetric> window, Function<GpuMetric, Integer> f) {
        Integer min = null;
        Integer max = null;

        for (GpuMetric m : window) {
            Integer v = f.apply(m);
            if (v != null) {
                min = min == null ? v : Math.min(min, v);
                max = max == null ? v : Math.max(max, v);
            }
        }

        return min != null && max != null ? max - min : 0;
    }

    private static double round2(double v) {
        return (double)Math.round(v * 100.0) / 100.0;
    }

    private static final class GpuCardEntityLevel {
        static final String GOOD = "优";
        static final String FINE = "良";
        static final String WATCH = "观察";
        static final String DEGRADED = "劣化";
        static final String CRITICAL = "高危";
    }

    public static record Score(int total, String level, Map<String, Object> breakdown) {
    }

    public static record Weights(int ecc, int temp, int xid, int power, int tempLimitC) {
        public static HealthScoreCalculator.Weights defaults() {
            return new HealthScoreCalculator.Weights(40, 25, 20, 15, 85);
        }
    }
}
