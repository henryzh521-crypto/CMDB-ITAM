package com.dcim.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.finance.dto.FinanceAssetVO;
import com.dcim.finance.entity.FinanceAssetEntity;
import com.dcim.finance.mapper.FinanceAssetMapper;
import com.dcim.finance.mapper.FinanceQueryMapper;
import com.dcim.finance.statemachine.FinanceStateMachine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceAssetService {
    
    private static final Logger log = LoggerFactory.getLogger(FinanceAssetService.class);
    private final FinanceAssetMapper financeAssetMapper;
    private final FinanceQueryMapper financeQueryMapper;

    public PageResult<FinanceAssetVO> list(long page, long size, String keyword, String statusFin, Boolean leaseFlag) {
        Page<FinanceAssetVO> p = new Page(page, size);
        List<FinanceAssetVO> records = this.financeQueryMapper.selectFinancePage(p, this.blankToNull(keyword), this.blankToNull(statusFin), leaseFlag);
        return new PageResult<>(p.getTotal(), records);
    }

    @Transactional
    public FinanceAssetVO register(
        String assetNo, BigDecimal originValue, BigDecimal residualRate, Integer lifeMonths, String depMethod, String depStartDate, Boolean leaseFlag
    ) {
        if (assetNo != null && !assetNo.isBlank()) {
            String cur = this.currentStatus(assetNo);
            if (cur == null) {
                throw BusinessException.notFound("资产不存在：" + assetNo);
            } else if (this.financeAssetMapper.selectById(assetNo) != null) {
                throw BusinessException.conflict("该资产已建立财务档案：" + assetNo);
            } else if (originValue != null && originValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rate = residualRate == null ? BigDecimal.ZERO : residualRate;
                if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
                    throw BusinessException.badRequest("残值率必须在 [0,1) 区间：" + rate);
                } else if (lifeMonths != null && lifeMonths > 0) {
                    LocalDate start = this.parseDate(depStartDate);
                    if (start == null) {
                        throw BusinessException.badRequest("折旧起始日期不能为空（yyyy-MM-dd）");
                    } else {
                        String method = depMethod != null && !depMethod.isBlank() ? depMethod.trim() : "平均年限法";
                        if (!"平均年限法".equals(method)) {
                            throw BusinessException.badRequest("暂仅支持「平均年限法」，收到：" + method);
                        } else {
                            boolean lease = Boolean.TRUE.equals(leaseFlag);
                            String target = lease ? "使用权资产确认" : "已入账";
                            FinanceStateMachine.assertTransit(cur, target);
                            FinanceAssetEntity f = new FinanceAssetEntity();
                            f.setAssetNo(assetNo);
                            f.setOriginValue(originValue);
                            f.setResidualRate(rate);
                            f.setLifeMonths(lifeMonths);
                            f.setDepMethod(method);
                            f.setDepStartDate(start);
                            f.setAccumulatedDep(BigDecimal.ZERO.setScale(2));
                            f.setImpairment(BigDecimal.ZERO.setScale(2));
                            f.setNetValue(originValue);
                            f.setLeaseFlag(lease);
                            f.setUpdatedAt(LocalDateTime.now());
                            this.financeAssetMapper.insert(f);
                            this.financeQueryMapper.updateStatusFin(assetNo, target);
                            log.info("[finance] 建档完成 assetNo={} 原值={} 年限={}月 轨道={}", new Object[]{assetNo, originValue, lifeMonths, lease ? "租赁" : "普通"});
                            return this.getByAssetNo(assetNo);
                        }
                    }
                } else {
                    throw BusinessException.badRequest("折旧年限（月）必须大于 0");
                }
            } else {
                throw BusinessException.badRequest("原值必须大于 0");
            }
        } else {
            throw BusinessException.badRequest("资产编号不能为空");
        }
    }

    @Transactional
    public FinanceAssetVO capitalize(String assetNo) {
        FinanceAssetEntity f = this.requireFinance(assetNo);
        String cur = this.requireAssetStatus(assetNo);
        String target = Boolean.TRUE.equals(f.getLeaseFlag()) ? "折旧+利息" : "转固计提";
        FinanceStateMachine.assertActionAllowed("capitalize", cur);
        FinanceStateMachine.assertTransit(cur, target);
        this.financeQueryMapper.updateStatusFin(assetNo, target);
        log.info("[finance] 转固/起租 assetNo={} {} → {}", new Object[]{assetNo, cur, target});
        return this.getByAssetNo(assetNo);
    }

    @Transactional
    public FinanceAssetVO impair(String assetNo, BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            FinanceAssetEntity f = this.requireFinance(assetNo);
            String cur = this.requireAssetStatus(assetNo);
            FinanceStateMachine.assertActionAllowed("impair", cur);
            FinanceStateMachine.assertTransit(cur, "减值");
            BigDecimal newImpair = this.nvl(f.getImpairment()).add(amount);
            BigDecimal newNet = f.getOriginValue().subtract(this.nvl(f.getAccumulatedDep())).subtract(newImpair);
            if (newNet.compareTo(BigDecimal.ZERO) < 0) {
                // 减值上限校验：减值后净值不可为负，提示当前账面净值（即可减值上限）
                BigDecimal curNet = f.getOriginValue().subtract(this.nvl(f.getAccumulatedDep())).subtract(this.nvl(f.getImpairment()));
                throw BusinessException.badRequest("减值金额超出可减值上限：当前账面净值 " + curNet + "，本次最多可减值 " + curNet);
            }
            f.setImpairment(newImpair);
            f.setNetValue(newNet);
            f.setUpdatedAt(LocalDateTime.now());
            this.financeAssetMapper.updateById(f);
            this.financeQueryMapper.updateStatusFin(assetNo, "减值");
            log.info("[finance] 减值 assetNo={} 本次={} 累计减值={} 净值={}", new Object[]{assetNo, amount, newImpair, f.getNetValue()});
            return this.getByAssetNo(assetNo);
        } else {
            throw BusinessException.badRequest("减值金额必须大于 0：" + amount);
        }
    }

    @Transactional
    public FinanceAssetVO dispose(String assetNo) {
        this.requireFinance(assetNo);
        String cur = this.requireAssetStatus(assetNo);
        FinanceStateMachine.assertTransit(cur, "处置中");
        this.financeQueryMapper.updateStatusFin(assetNo, "处置中");
        log.info("[finance] 转处置中 assetNo={} {} → 处置中", assetNo, cur);
        return this.getByAssetNo(assetNo);
    }

    @Transactional
    public FinanceAssetVO writeOff(String assetNo) {
        this.requireFinance(assetNo);
        String cur = this.requireAssetStatus(assetNo);
        FinanceStateMachine.assertTransit(cur, "核销");
        this.financeQueryMapper.updateStatusFin(assetNo, "核销");
        log.info("[finance] 核销完成 assetNo={} {} → 核销", assetNo, cur);
        return this.getByAssetNo(assetNo);
    }

    private FinanceAssetVO getByAssetNo(String assetNo) {
        FinanceAssetVO vo = this.financeQueryMapper.selectFinanceByAssetNo(assetNo);
        if (vo != null && vo.getOriginValue() != null) {
            return vo;
        } else {
            throw BusinessException.notFound("财务档案不存在：" + assetNo);
        }
    }

    private FinanceAssetEntity requireFinance(String assetNo) {
        FinanceAssetEntity f = (FinanceAssetEntity)this.financeAssetMapper.selectById(assetNo);
        if (f == null) {
            throw BusinessException.badRequest("资产尚未建立财务档案，请先建档：" + assetNo);
        } else {
            return f;
        }
    }

    private String requireAssetStatus(String assetNo) {
        String status = this.currentStatus(assetNo);
        if (status == null) {
            throw BusinessException.notFound("资产不存在：" + assetNo);
        } else {
            return status;
        }
    }

    private String currentStatus(String assetNo) {
        return this.financeQueryMapper.selectStatusFin(assetNo);
    }

    private LocalDate parseDate(String s) {
        if (s != null && !s.isBlank()) {
            try {
                return LocalDate.parse(s.trim());
            } catch (DateTimeParseException var3) {
                throw BusinessException.badRequest("日期格式错误，应为 yyyy-MM-dd：" + s.trim());
            }
        } else {
            return null;
        }
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    
    public FinanceAssetService(final FinanceAssetMapper financeAssetMapper, final FinanceQueryMapper financeQueryMapper) {
        this.financeAssetMapper = financeAssetMapper;
        this.financeQueryMapper = financeQueryMapper;
    }
}
