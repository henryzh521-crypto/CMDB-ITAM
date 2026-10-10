package com.dcim.finance.controller;

import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.csv.CsvSupport;
import com.dcim.finance.dto.DepreciationRow;
import com.dcim.finance.dto.PeriodRow;
import com.dcim.finance.service.DepreciationService;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/finance/depreciation"})
public class DepreciationController {
    private static final List<String> CSV_HEADERS = List.of("资产编号", "资产名称", "期间", "期初净值", "本月折旧", "累计折旧", "期末净值");
    private final DepreciationService depreciationService;

    @PostMapping({"/run"})
    @RequirePerm(
        module = "finance",
        write = true
    )
    @Audit(
        action = "DEPRECIATE",
        module = "finance",
        objType = "depreciation"
    )
    public ApiResult<Map<String, Object>> run(@RequestBody DepreciationController.RunReq req) {
        return ApiResult.ok(this.depreciationService.runPeriod(req.getPeriod()));
    }

    @GetMapping
    @RequirePerm(
        module = "finance"
    )
    public ApiResult<PageResult<DepreciationRow>> worksheet(
        @RequestParam(required = false) String period, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size
    ) {
        return period != null && !period.isBlank()
            ? ApiResult.ok(this.depreciationService.worksheet(period, page, size))
            : ApiResult.ok(new PageResult<>(0L, List.of()));
    }

    @GetMapping({"/periods"})
    @RequirePerm(
        module = "finance"
    )
    public ApiResult<List<PeriodRow>> periods() {
        return ApiResult.ok(this.depreciationService.periods());
    }

    @GetMapping({"/export"})
    @RequirePerm(
        module = "finance",
        action = "export"
    )
    public void export(@RequestParam(required = false) String period, HttpServletResponse response) throws IOException {
        if (period != null && !period.isBlank()) {
            List<DepreciationRow> rows = this.depreciationService.worksheetForExport(period);
            StringBuilder csv = new StringBuilder();
            csv.append('\ufeff').append(CsvSupport.line(CSV_HEADERS)).append('\n');

            for (DepreciationRow r : rows) {
                csv.append(
                        CsvSupport.line(
                            Arrays.asList(
                                r.getAssetNo(),
                                r.getAssetName(),
                                r.getPeriod() == null ? "" : r.getPeriod().toString(),
                                r.getOpeningNetValue(),
                                r.getDepAmount(),
                                r.getAccumulatedDep(),
                                r.getClosingNetValue()
                            )
                        )
                    )
                    .append('\n');
            }

            String fileName = "折旧底稿-" + period + "-" + LocalDate.now() + ".csv";
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setStatus(200);
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
            response.getOutputStream().write(csv.toString().getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
        } else {
            throw BusinessException.badRequest("请先选择期间（yyyy-MM）");
        }
    }

    
    public DepreciationController(final DepreciationService depreciationService) {
        this.depreciationService = depreciationService;
    }

    public static class RunReq {
        private String period;

        
        public String getPeriod() {
            return this.period;
        }

        
        public void setPeriod(final String period) {
            this.period = period;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof DepreciationController.RunReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$period = this.getPeriod();
                Object other$period = other.getPeriod();
                return this$period == null ? other$period == null : this$period.equals(other$period);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof DepreciationController.RunReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $period = this.getPeriod();
            return result * 59 + ($period == null ? 43 : $period.hashCode());
        }

        
        @Override
        public String toString() {
            return "DepreciationController.RunReq(period=" + this.getPeriod() + ")";
        }
    }
}
