package com.dcim.common.seq;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SeqNoGenerator {
    private final JdbcTemplate jdbc;

    @Transactional
    public String next(String prefix, int width) {
        Long nextNo = (Long)this.jdbc
            .queryForObject(
                "INSERT INTO asset_no_seq(prefix, next_no) VALUES (?, 1) ON CONFLICT (prefix) DO UPDATE SET next_no = asset_no_seq.next_no + 1 RETURNING next_no",
                Long.class,
                new Object[]{prefix}
            );
        if (nextNo == null) {
            throw new IllegalStateException("流水号发号失败：" + prefix);
        } else {
            return prefix + "-" + String.format("%0" + width + "d", nextNo);
        }
    }

    @Transactional
    public void ensureAtLeast(String prefix, long nextValue) {
        this.jdbc
            .update(
                "INSERT INTO asset_no_seq(prefix, next_no) VALUES (?, ?) ON CONFLICT (prefix) DO UPDATE SET next_no = GREATEST(asset_no_seq.next_no, EXCLUDED.next_no)",
                new Object[]{prefix, nextValue}
            );
    }

    public static String todaySegment() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    
    public SeqNoGenerator(final JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
}
