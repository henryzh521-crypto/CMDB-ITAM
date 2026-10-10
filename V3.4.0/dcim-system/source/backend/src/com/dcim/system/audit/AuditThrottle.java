package com.dcim.system.audit;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AuditThrottle {
    
    private static final Logger log = LoggerFactory.getLogger(AuditThrottle.class);
    private static final int MAX_TRACKED = 10000;
    private final ConcurrentHashMap<String, AuditThrottle.Window> counters = new ConcurrentHashMap<>();

    public boolean allow(String key, int max, Duration window) {
        if (key != null && max > 0 && window != null && !window.isZero() && !window.isNegative()) {
            long now = System.currentTimeMillis();
            long windowMs = window.toMillis();
            if (this.counters.size() > 10000) {
                this.prune(now, windowMs);
            }

            AuditThrottle.Window current = this.counters
                .compute(key, (k, cur) -> (AuditThrottle.Window)(cur != null && now - cur.start < windowMs ? cur : new AuditThrottle.Window(now)));
            int n = current.count.incrementAndGet();
            if (n <= max) {
                return true;
            } else {
                if (n == max + 1 && current.warned.compareAndSet(false, true)) {
                    log.warn("审计写入触发限流：key={} 窗口={}s 上限={}，后续同类记录暂不写入", new Object[]{key, window.getSeconds(), max});
                }

                return false;
            }
        } else {
            return false;
        }
    }

    private void prune(long now, long windowMs) {
        Iterator<Entry<String, AuditThrottle.Window>> it = this.counters.entrySet().iterator();

        while (it.hasNext()) {
            Entry<String, AuditThrottle.Window> e = it.next();
            if (now - e.getValue().start >= windowMs) {
                it.remove();
            }
        }
    }

    public int trackedKeys() {
        return this.counters.size();
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();
        final AtomicBoolean warned = new AtomicBoolean();

        Window(long start) {
            this.start = start;
        }
    }
}
