package com.dcim.inspection.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

public final class InspectionSchedule {
    private InspectionSchedule() {
    }

    public static LocalDateTime next(String frequency, int hour, Integer weekday, Integer monthDay, LocalDateTime after) {
        LocalTime at = LocalTime.of(hour, 0);
        LocalDate d = after.toLocalDate();
        switch (frequency) {
            case "DAILY": {
                LocalDateTime c = d.atTime(at);
                return c.isAfter(after) ? c : c.plusDays(1L);
            }
            case "WEEKLY": {
                DayOfWeek dow = DayOfWeek.of(weekday == null ? 1 : weekday);
                LocalDateTime c = d.with(TemporalAdjusters.nextOrSame(dow)).atTime(at);
                return c.isAfter(after) ? c : c.plusWeeks(1L);
            }
            case "MONTHLY": {
                int md = monthDay == null ? 1 : monthDay;
                LocalDateTime c = d.withDayOfMonth(md).atTime(at);
                return c.isAfter(after) ? c : c.plusMonths(1L);
            }
            default:
                throw new IllegalArgumentException("未知频率：" + frequency);
        }
    }
}
