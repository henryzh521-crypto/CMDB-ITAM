package com.dcim.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

public final class DepreciationCalculator {
    public static final String METHOD_AVERAGE = "平均年限法";

    private DepreciationCalculator() {
    }

    public static BigDecimal totalDep(BigDecimal originValue, BigDecimal residualRate) {
        BigDecimal rate = residualRate == null ? BigDecimal.ZERO : residualRate;
        return originValue.multiply(BigDecimal.ONE.subtract(rate)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal monthDep(BigDecimal originValue, BigDecimal residualRate, int lifeMonths, BigDecimal accumulated) {
        BigDecimal total = totalDep(originValue, residualRate);
        BigDecimal acc = accumulated == null ? BigDecimal.ZERO : accumulated;
        if (acc.compareTo(total) >= 0) {
            return BigDecimal.ZERO.setScale(2);
        } else {
            BigDecimal monthly = total.divide(BigDecimal.valueOf((long)lifeMonths), 2, RoundingMode.HALF_UP);
            // 小原值长年限：月折旧按 HALF_UP 收敛为 0 时，剩余折旧额一次性计提，
            // 避免累计永远提不满而在计提流程中被提前判为“折旧完毕”
            if (monthly.signum() <= 0) {
                return total.subtract(acc);
            } else {
                return acc.add(monthly).compareTo(total) > 0 ? total.subtract(acc) : monthly;
            }
        }
    }

    public static boolean finished(BigDecimal originValue, BigDecimal residualRate, BigDecimal accumulated) {
        BigDecimal acc = accumulated == null ? BigDecimal.ZERO : accumulated;
        return acc.compareTo(totalDep(originValue, residualRate)) >= 0;
    }

    public static boolean started(LocalDate depStartDate, YearMonth period) {
        return depStartDate == null ? false : !period.atEndOfMonth().isBefore(depStartDate);
    }
}
