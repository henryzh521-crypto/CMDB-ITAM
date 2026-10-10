package com.dcim.quality;

import com.dcim.common.ApiResult;
import com.dcim.security.RequirePerm;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/data-quality"})
public class DataQualityController {
    private final DataQualityService quality;

    @GetMapping
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> summary() {
        return ApiResult.ok(this.quality.summary());
    }

    @GetMapping({"/rules/{ruleId}/assets"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> failed(
        @PathVariable String ruleId, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "50") long size
    ) {
        return ApiResult.ok(this.quality.failed(ruleId, page, size));
    }

    
    public DataQualityController(final DataQualityService quality) {
        this.quality = quality;
    }
}
