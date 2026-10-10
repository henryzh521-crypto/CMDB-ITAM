package com.dcim.collector.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.collector.dto.CollectorDtos;
import com.dcim.collector.entity.BmcCredentialEntity;
import com.dcim.collector.entity.DiscoverResultEntity;
import com.dcim.collector.entity.DiscoverTaskEntity;
import com.dcim.collector.mapper.BmcCredentialMapper;
import com.dcim.collector.mapper.DiscoverResultMapper;
import com.dcim.collector.mapper.DiscoverTaskMapper;
import com.dcim.collector.service.DiscoverService;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.crypto.CryptBox;
import com.dcim.security.CurrentUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/discovery"})
public class DiscoverTaskController {
    private final DiscoverTaskMapper taskMapper;
    private final DiscoverResultMapper resultMapper;
    private final BmcCredentialMapper credentialMapper;
    private final DiscoverService discoverService;
    private final CryptBox cryptBox;

    @GetMapping({"/tasks"})
    @RequirePerm(
        module = "discovery"
    )
    public ApiResult<PageResult<Map<String, Object>>> tasks(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<DiscoverTaskEntity> qw = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) {
            qw.eq(DiscoverTaskEntity::getStatus, status.trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(
                w -> ((((w.like(DiscoverTaskEntity::getTaskNo, kw)).or())
                                .like(DiscoverTaskEntity::getName, kw))
                            .or())
                        .like(DiscoverTaskEntity::getCidr, kw)
            );
        }

        qw.orderByDesc(DiscoverTaskEntity::getId);
        Page<DiscoverTaskEntity> p = new Page(page, size);
        this.taskMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, t -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.getId());
            row.put("taskNo", t.getTaskNo());
            row.put("name", t.getName());
            row.put("cidr", t.getCidr());
            row.put("protocol", t.getProtocol());
            row.put("credentialId", t.getCredentialId());
            row.put("regionCode", t.getRegionCode());
            row.put("status", t.getStatus());
            row.put("totalIps", t.getTotalIps());
            row.put("scannedIps", t.getScannedIps());
            row.put("foundCount", t.getFoundCount());
            row.put("matchedCount", t.getMatchedCount());
            row.put("pendingCount", t.getPendingCount());
            row.put("lastRunAt", t.getLastRunAt());
            row.put("createdBy", t.getCreatedBy());
            row.put("createdAt", t.getCreatedAt());
            return row;
        }));
    }

    @PostMapping({"/tasks"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "创建发现任务"
    )
    public ApiResult<DiscoverTaskEntity> createTask(@Valid @RequestBody CollectorDtos.CreateTaskReq req) {
        String user = CurrentUser.get() == null ? "system" : CurrentUser.get().username();
        return ApiResult.ok(this.discoverService.createTask(req.name(), req.cidr(), req.protocol(), req.credentialId(), req.regionCode(), user));
    }

    @PostMapping({"/tasks/{taskNo}/run"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "触发发现任务",
        objIdSpEL = "#taskNo"
    )
    public ApiResult<Boolean> runNow(@PathVariable String taskNo) {
        this.discoverService.runNow(taskNo);
        return ApiResult.ok(true);
    }

    @DeleteMapping({"/tasks/{taskNo}"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "删除发现任务",
        objIdSpEL = "#taskNo"
    )
    public ApiResult<Boolean> deleteTask(@PathVariable String taskNo) {
        this.discoverService.deleteTask(taskNo);
        return ApiResult.ok(true);
    }

    @GetMapping({"/results"})
    @RequirePerm(
        module = "discovery"
    )
    public ApiResult<PageResult<Map<String, Object>>> results(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) Long taskId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<DiscoverResultEntity> qw = Wrappers.lambdaQuery();
        if (taskId != null) {
            qw.eq(DiscoverResultEntity::getTaskId, taskId);
        }

        if (status != null && !status.isBlank()) {
            qw.eq(DiscoverResultEntity::getStatus, status.trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(
                w -> ((((((w.like(
                                                DiscoverResultEntity::getBmcIp, kw
                                            ))
                                            .or())
                                        .like(DiscoverResultEntity::getSn, kw))
                                    .or())
                                .like(DiscoverResultEntity::getModel, kw))
                            .or())
                        .like(DiscoverResultEntity::getAssetNo, kw)
            );
        }

        qw.orderByDesc(DiscoverResultEntity::getId);
        Page<DiscoverResultEntity> p = new Page(page, size);
        this.resultMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("taskId", r.getTaskId());
            row.put("bmcIp", r.getBmcIp());
            row.put("sn", r.getSn());
            row.put("manufacturer", r.getManufacturer());
            row.put("model", r.getModel());
            row.put("endpointId", r.getEndpointId());
            row.put("assetNo", r.getAssetNo());
            row.put("status", r.getStatus());
            row.put("decision", r.getDecision());
            row.put("claimedAt", r.getClaimedAt());
            row.put("createdAt", r.getCreatedAt());
            return row;
        }));
    }

    @PostMapping({"/results/{id}/claim"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "认领发现结果",
        objIdSpEL = "#id"
    )
    public ApiResult<DiscoverResultEntity> claim(@PathVariable Long id, @RequestBody CollectorDtos.ClaimReq req) {
        return ApiResult.ok(this.discoverService.claimResult(id, req));
    }

    @PostMapping({"/results/{id}/ignore"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "忽略发现结果",
        objIdSpEL = "#id"
    )
    public ApiResult<Boolean> ignore(@PathVariable Long id) {
        this.discoverService.ignoreResult(id);
        return ApiResult.ok(true);
    }

    @GetMapping({"/credentials"})
    @RequirePerm(
        module = "discovery"
    )
    public ApiResult<List<Map<String, Object>>> credentials() {
        List<BmcCredentialEntity> list = this.credentialMapper.selectList(Wrappers.<BmcCredentialEntity>lambdaQuery().orderByDesc(BmcCredentialEntity::getId));
        ArrayList<Map<String, Object>> rows = new ArrayList<>(list.size());

        for (BmcCredentialEntity c : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("credName", c.getCredName());
            row.put("username", c.getUsername());
            row.put("createdAt", c.getCreatedAt());
            rows.add(row);
        }

        return ApiResult.ok(rows);
    }

    @PostMapping({"/credentials"})
    @RequirePerm(
        module = "discovery",
        write = true
    )
    @Audit(
        module = "discovery",
        action = "创建带外凭据"
    )
    public ApiResult<BmcCredentialEntity> createCredential(@Valid @RequestBody CollectorDtos.CreateCredentialReq req) {
        if (req.credName() != null
            && !req.credName().isBlank()
            && req.username() != null
            && !req.username().isBlank()
            && req.password() != null
            && !req.password().isBlank()) {
            Long dup = this.credentialMapper.selectCount(Wrappers.<BmcCredentialEntity>lambdaQuery().eq(BmcCredentialEntity::getCredName, req.credName().trim()));
            if (dup != null && dup > 0L) {
                throw BusinessException.conflict("凭据名称已存在：" + req.credName().trim());
            } else {
                BmcCredentialEntity c = new BmcCredentialEntity();
                c.setCredName(req.credName().trim());
                c.setUsername(req.username().trim());
                c.setPasswordEnc(this.cryptBox.encrypt(req.password()));
                c.setCreatedAt(LocalDateTime.now());
                this.credentialMapper.insert(c);
                c.setPasswordEnc(null);
                return ApiResult.ok(c);
            }
        } else {
            throw BusinessException.badRequest("凭据名称/用户名/密码均不能为空");
        }
    }

    
    public DiscoverTaskController(
        final DiscoverTaskMapper taskMapper,
        final DiscoverResultMapper resultMapper,
        final BmcCredentialMapper credentialMapper,
        final DiscoverService discoverService,
        final CryptBox cryptBox
    ) {
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.credentialMapper = credentialMapper;
        this.discoverService = discoverService;
        this.cryptBox = cryptBox;
    }
}
