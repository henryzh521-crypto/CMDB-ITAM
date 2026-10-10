package com.dcim.finance.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.finance.dto.DepCandidateRow;
import com.dcim.finance.dto.DepreciationRow;
import com.dcim.finance.dto.PeriodRow;
import com.dcim.finance.entity.DepreciationRecordEntity;
import com.dcim.finance.entity.FinanceAssetEntity;
import com.dcim.finance.mapper.DepreciationRecordMapper;
import com.dcim.finance.mapper.FinanceAssetMapper;
import com.dcim.finance.mapper.FinanceQueryMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DepreciationService {
    
    private static final Logger log = LoggerFactory.getLogger(DepreciationService.class);
    private final DepreciationRecordMapper depRecordMapper;
    private final FinanceAssetMapper financeAssetMapper;
    private final FinanceQueryMapper financeQueryMapper;
    private final TransactionTemplate rowTx;

    public DepreciationService(
        DepreciationRecordMapper depRecordMapper,
        FinanceAssetMapper financeAssetMapper,
        FinanceQueryMapper financeQueryMapper,
        PlatformTransactionManager txManager
    ) {
        this.depRecordMapper = depRecordMapper;
        this.financeAssetMapper = financeAssetMapper;
        this.financeQueryMapper = financeQueryMapper;
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setPropagationBehavior(3);
        this.rowTx = t;
    }

    public Map<String, Object> runPeriod(String period) {
        YearMonth ym = this.parsePeriod(period);
        // 只允许计提到当前月（含）：与 autoRunPreviousMonth 的 YearMonth.now() 保持同一时区口径，禁止计提未来期间
        YearMonth cur = YearMonth.now();
        if (ym.isAfter(cur)) {
            throw BusinessException.badRequest("计提期间不可晚于当前月：" + period + "（当前月 " + cur + "）");
        }
        LocalDate periodDate = ym.atDay(1);
        List<DepCandidateRow> candidates = this.financeQueryMapper.selectDepCandidates();
        int processed = 0;
        int skipped = 0;
        BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);

        for (DepCandidateRow c : candidates) {
            Long exists = this.depRecordMapper
                .selectCount(
                    (Wrappers.<DepreciationRecordEntity>lambdaQuery().eq(DepreciationRecordEntity::getAssetNo, c.getAssetNo()))
                        .eq(DepreciationRecordEntity::getPeriod, periodDate)
                );
            if (exists != null && exists > 0L) {
                skipped++;
            } else if (!DepreciationCalculator.started(c.getDepStartDate(), ym)) {
                skipped++;
            } else {
                try {
                    DepreciationService.Outcome out = (DepreciationService.Outcome)this.rowTx.execute(tx -> this.applyOne(c, periodDate));
                    if (out != null && out.applied()) {
                        processed++;
                        totalAmount = totalAmount.add(out.depAmount());
                    } else {
                        skipped++;
                    }
                } catch (DuplicateKeyException var12) {
                    skipped++;
                    log.info("[finance] 计提并发去重 assetNo={} period={}", c.getAssetNo(), period);
                } catch (Exception var13) {
                    skipped++;
                    log.error("[finance] 计提失败已跳过 assetNo={} period={}：{}", new Object[]{c.getAssetNo(), period, var13.getMessage(), var13});
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("period", period);
        out.put("processed", processed);
        out.put("skipped", skipped);
        out.put("totalAmount", totalAmount);
        log.info("[finance] 折旧计提完成 period={} 计提 {} 笔 跳过 {} 笔 合计 {}", new Object[]{period, processed, skipped, totalAmount});
        return out;
    }

    private DepreciationService.Outcome applyOne(DepCandidateRow c, LocalDate periodDate) {
        FinanceAssetEntity f = (FinanceAssetEntity)this.financeAssetMapper.selectById(c.getAssetNo());
        if (f == null) {
            return DepreciationService.Outcome.SKIP;
        } else if (f.getOriginValue() != null && f.getOriginValue().signum() > 0) {
            Integer lifeMonths = f.getLifeMonths();
            if (lifeMonths != null && lifeMonths > 0) {
                BigDecimal acc = this.nvl(f.getAccumulatedDep());
                BigDecimal dep = DepreciationCalculator.monthDep(f.getOriginValue(), f.getResidualRate(), lifeMonths, acc);
                if (dep.signum() <= 0) {
                    this.financeQueryMapper.updateStatusFin(c.getAssetNo(), "折旧完毕");
                    log.info("[finance] 资产已提满补偿转态 assetNo={}", c.getAssetNo());
                    return DepreciationService.Outcome.SKIP;
                } else {
                    BigDecimal newAcc = acc.add(dep);
                    BigDecimal newNet = f.getOriginValue().subtract(newAcc).subtract(this.nvl(f.getImpairment()));
                    DepreciationRecordEntity r = new DepreciationRecordEntity();
                    r.setAssetNo(f.getAssetNo());
                    r.setPeriod(periodDate);
                    r.setOriginValue(f.getOriginValue());
                    r.setMonthDep(dep);
                    r.setAccumulated(newAcc);
                    r.setNetValue(newNet);
                    r.setMethod(f.getDepMethod());
                    r.setCreatedAt(LocalDateTime.now());
                    this.depRecordMapper.insert(r);
                    f.setAccumulatedDep(newAcc);
                    f.setNetValue(newNet);
                    f.setUpdatedAt(LocalDateTime.now());
                    this.financeAssetMapper.updateById(f);
                    if (DepreciationCalculator.finished(f.getOriginValue(), f.getResidualRate(), newAcc)) {
                        this.financeQueryMapper.updateStatusFin(f.getAssetNo(), "折旧完毕");
                        log.info("[finance] 折旧提满自动转态 assetNo={} 累计={} → 折旧完毕", f.getAssetNo(), newAcc);
                    }

                    log.info("[finance] 计提 assetNo={} period={} 本月={} 累计={} 净值={}", new Object[]{f.getAssetNo(), periodDate, dep, newAcc, newNet});
                    return new DepreciationService.Outcome(dep, true);
                }
            } else {
                log.warn("[finance] 档案使用年限非法，跳过计提 assetNo={} lifeMonths={}", c.getAssetNo(), lifeMonths);
                return DepreciationService.Outcome.SKIP;
            }
        } else {
            log.warn("[finance] 档案原值非法，跳过计提 assetNo={} originValue={}", c.getAssetNo(), f.getOriginValue());
            return DepreciationService.Outcome.SKIP;
        }
    }

    @Scheduled(
        cron = "0 0 2 1 * ?"
    )
    public void autoRunPreviousMonth() {
        String period = YearMonth.now().minusMonths(1L).toString();
        log.info("[finance] 定时折旧计提开始 period={}", period);

        try {
            Map<String, Object> result = this.runPeriod(period);
            log.info("[finance] 定时折旧计提完成 {}", result);
        } catch (Exception var3) {
            log.error("[finance] 定时折旧计提失败 period={}", period, var3);
        }
    }

    public PageResult<DepreciationRow> worksheet(String period, long page, long size) {
        LocalDate periodDate = this.parsePeriod(period).atDay(1);
        Page<DepreciationRow> p = new Page(page, size);
        List<DepreciationRow> rows = this.financeQueryMapper.selectWorksheetPage(p, periodDate);
        return new PageResult<>(p.getTotal(), rows);
    }

    public List<PeriodRow> periods() {
        return this.financeQueryMapper.selectPeriods();
    }

    public List<DepreciationRow> worksheetForExport(String period) {
        return this.financeQueryMapper.selectWorksheet(this.parsePeriod(period).atDay(1));
    }

    private YearMonth parsePeriod(String period) {
        if (period != null && !period.isBlank()) {
            try {
                return YearMonth.parse(period.trim());
            } catch (DateTimeParseException var3) {
                throw BusinessException.badRequest("期间格式错误，应为 yyyy-MM：" + period.trim());
            }
        } else {
            throw BusinessException.badRequest("计提期间不能为空（yyyy-MM）");
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static record Outcome(BigDecimal depAmount, boolean applied) {
        static final DepreciationService.Outcome SKIP = new DepreciationService.Outcome(BigDecimal.ZERO, false);
    }
}
