package com.dcim.asset.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.asset.entity.AssetEntity;
import com.dcim.asset.entity.AssetEventEntity;
import com.dcim.asset.entity.AssetFinanceEntity;
import com.dcim.asset.mapper.AssetEventMapper;
import com.dcim.asset.mapper.AssetFinanceMapper;
import com.dcim.asset.mapper.AssetMapper;
import com.dcim.asset.statemachine.AssetStateMachine;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.asset.AssetNoGenerator;
import com.dcim.common.csv.CsvSupport;
import com.dcim.common.rack.RackOccupancyPort;
import com.dcim.masterdata.entity.CategoryEntity;
import com.dcim.masterdata.mapper.CategoryMapper;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RbacService;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.system.entity.DictEntity;
import com.dcim.system.mapper.DictMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/assets"})
public class AssetController {
    private final com.dcim.security.DataScopeService dataScope;
    private static final String INIT_BIZ = "待上架";
    private static final String INIT_FIN = "未入账";
    private static final List<String> DELETABLE_BIZ = List.of("待上架");
    /** v3.5：可删除的财务状态白名单——已进入折旧/减值/处置流程的资产删除会破坏财务台账，须先走财务处置 */
    private static final List<String> DELETABLE_FIN = List.of("未入账", "已入账");
    /** v3.5：资产导出行数上限（对齐工单侧导出 EXPORT_MAX_ROWS=10000 的既有约定） */
    private static final int EXPORT_MAX_ROWS = 10000;
    /** v3.5：CSV 导入上限：10MB / 50000 行，超限直接拒绝，避免全量读入内存 */
    private static final long IMPORT_MAX_BYTES = 10485760L;
    private static final int IMPORT_MAX_ROWS = 50000;
    /** v3.4：与 CSV_HEADERS 一一对应的字段名（导出时按字段权限过滤列） */
    private static final String[] CSV_FIELDS = {
        "assetNo", "parentAssetNo", "name", "sn", "categoryCode", "modelCode", "ownerType", "statusBiz", "statusFin",
        "regionCode", "roomCode", "rackCode", "unitNo", "uHeight", "department", "owner", "mgmtIp", "bizIp",
        "purchaseDate", "purchasePrice", "warrantyEnd", "rackDate", "contractNo", "attrs"
    };
    @org.springframework.beans.factory.annotation.Autowired
    private com.dcim.security.field.FieldPolicyService fieldPolicies;
    private static final List<String> CSV_HEADERS = List.of(
        "资产编号",
        "父资产编号",
        "资产名称",
        "SN",
        "分类编码",
        "型号编码",
        "权属",
        "业务状态",
        "财务状态",
        "区域编码",
        "机房编码",
        "机柜编号",
        "起始U位",
        "U位高度",
        "使用部门",
        "责任人",
        "管理IP",
        "业务IP",
        "采购日期",
        "采购单价",
        "保修到期",
        "上架日期",
        "合同号",
        "备注"
    );
    private final AssetMapper assetMapper;
    private final AssetFinanceMapper assetFinanceMapper;
    private final AssetEventMapper assetEventMapper;
    private final CategoryMapper categoryMapper;
    private final DictMapper dictMapper;
    private final AssetNoGenerator assetNoGenerator;
    private final RbacService rbacService;
    private final RackOccupancyPort rackOccupancyPort;
    private final TransactionTemplate rowTx;
    private final JdbcTemplate jdbc;

    public AssetController(
        AssetMapper assetMapper,
        AssetFinanceMapper assetFinanceMapper,
        AssetEventMapper assetEventMapper,
        CategoryMapper categoryMapper,
        DictMapper dictMapper,
        AssetNoGenerator assetNoGenerator,
        RbacService rbacService,
        RackOccupancyPort rackOccupancyPort,
        PlatformTransactionManager txManager,
        JdbcTemplate jdbc
    ,
        final com.dcim.security.DataScopeService dataScope) {
        this.dataScope = dataScope;
        this.assetMapper = assetMapper;
        this.assetFinanceMapper = assetFinanceMapper;
        this.assetEventMapper = assetEventMapper;
        this.categoryMapper = categoryMapper;
        this.dictMapper = dictMapper;
        this.assetNoGenerator = assetNoGenerator;
        this.rbacService = rbacService;
        this.rackOccupancyPort = rackOccupancyPort;
        this.jdbc = jdbc;
        TransactionTemplate t = new TransactionTemplate(txManager);
        t.setPropagationBehavior(3);
        this.rowTx = t;
    }

    private static Map<String, Object> batchItem(String assetNo, String name, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assetNo", assetNo);
        if (name != null) {
            m.put("name", name);
        }

        if (reason != null) {
            m.put("reason", reason);
        }

        return m;
    }

    private List<String> normalizeBatchNos(List<String> assetNos) {
        List<String> nos = new ArrayList<>(new LinkedHashSet<>(assetNos));
        if (nos.size() > 500) {
            throw BusinessException.badRequest("单次批量操作最多 500 台，当前 " + nos.size() + " 台");
        } else {
            return nos;
        }
    }

    @GetMapping
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String statusBiz,
        @RequestParam(required = false) String categoryCode,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) String roomCode,
        @RequestParam(required = false) String parentAssetNo
    ) {
        Page<AssetEntity> p = new Page(page, size);
        LambdaQueryWrapper<AssetEntity> qw = this.buildListWrapper(keyword, statusBiz, categoryCode, regionCode, roomCode, parentAssetNo);
        if ((page - 1L) * size >= 500L) {
            // 深分页（offset ≥ 500）：直接 OFFSET 需要对跳过的每一行回表（10 万行级表第 4000 页实测 54ms）。
            // 先只取排序列上的主键——命中 (created_at, asset_no) WHERE deleted_at IS NULL 部分索引的
            // index-only scan（免回表，实测 ~12ms），再按主键批量取整行并还原排序。
            Page<AssetEntity> keyPage = new Page(page, size);
            this.assetMapper.selectPage(keyPage, qw.select(AssetEntity::getAssetNo));
            List<String> nos = keyPage.getRecords().stream().map(AssetEntity::getAssetNo).toList();
            List<AssetEntity> rows;
            if (nos.isEmpty()) {
                rows = List.of();
            } else {
                Map<String, AssetEntity> byNo = new java.util.HashMap<>();
                for (AssetEntity e : this.assetMapper.selectList(Wrappers.<AssetEntity>lambdaQuery().in(AssetEntity::getAssetNo, nos))) {
                    byNo.put(e.getAssetNo(), e);
                }
                rows = nos.stream().map(byNo::get).filter(e -> e != null).toList();
            }
            Page<AssetEntity> rp = new Page(page, size, keyPage.getTotal());
            rp.setRecords(rows);
            return ApiResult.ok(PageResult.of(rp, this::toRow));
        } else {
            this.assetMapper.selectPage(p, qw);
            return ApiResult.ok(PageResult.of(p, this::toRow));
        }
    }

    @GetMapping({"/{assetNo}"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> detail(@PathVariable String assetNo) {
        AssetEntity a = this.requireAsset(assetNo);
        Map<String, Object> row = this.toRow(a);
        AssetFinanceEntity fin = (AssetFinanceEntity)this.assetFinanceMapper.selectById(assetNo);
        row.put("finance", fin == null ? null : this.financeRow(fin));
        if (a.getParentAssetNo() != null && !a.getParentAssetNo().isBlank()) {
            AssetEntity parent = (AssetEntity)this.assetMapper.selectById(a.getParentAssetNo());
            if (parent != null) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("assetNo", parent.getAssetNo());
                p.put("name", parent.getName());
                p.put("statusBiz", parent.getStatusBiz());
                p.put("categoryCode", parent.getCategoryCode());
                row.put("parent", p);
            } else {
                row.put("parent", null);
            }
        } else {
            row.put("parent", null);
        }

        List<AssetEntity> children = this.assetMapper
            .selectList(
                ((Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getParentAssetNo, assetNo))
                        .orderByAsc(AssetEntity::getAssetNo))
                    .last("LIMIT 200")
            );
        row.put("children", children.stream().map(this::toRow).toList());
        row.put("childCount", this.assetMapper.selectCount(Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getParentAssetNo, assetNo)));
        return ApiResult.ok(row);
    }

    @GetMapping({"/{assetNo}/children"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<PageResult<Map<String, Object>>> children(
        @PathVariable String assetNo, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "50") long size
    ) {
        this.requireAsset(assetNo);
        Page<AssetEntity> p = new Page(page, size);
        this.assetMapper
            .selectPage(p, (Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getParentAssetNo, assetNo)).orderByAsc(AssetEntity::getAssetNo));
        return ApiResult.ok(PageResult.of(p, this::toRow));
    }

    @GetMapping({"/tree"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<List<Map<String, Object>>> tree(@RequestParam(required = false) String rootAssetNo) {
        List<AssetEntity> all;
        if (rootAssetNo != null && !rootAssetNo.isBlank()) {
            AssetEntity root = this.requireAsset(rootAssetNo.trim());
            all = this.collectSubtree(root.getAssetNo());
        } else {
            // v3.5：全量树同样受数据范围约束（与 list 的 buildListWrapper 用法一致）
            LambdaQueryWrapper<AssetEntity> qw = Wrappers.<AssetEntity>lambdaQuery().orderByAsc(AssetEntity::getAssetNo);
            this.dataScope.applyAssetScope(qw, com.dcim.security.CurrentUser.get());
            all = this.assetMapper.selectList(qw.last("LIMIT 2000"));
        }

        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        Map<String, List<String>> kids = new HashMap<>();

        for (AssetEntity a : all) {
            Map<String, Object> n = this.toRow(a);
            n.put("children", new ArrayList());
            nodes.put(a.getAssetNo(), n);
            kids.computeIfAbsent(a.getParentAssetNo() == null ? "" : a.getParentAssetNo(), k -> new ArrayList<>()).add(a.getAssetNo());
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        if (rootAssetNo != null && !rootAssetNo.isBlank()) {
            roots.add(nodes.get(rootAssetNo.trim()));
            this.attachChildren(rootAssetNo.trim(), kids, nodes);
        } else {
            for (String no : kids.getOrDefault("", List.of())) {
                Map<String, Object> n = nodes.get(no);
                roots.add(n);
                this.attachChildren(no, kids, nodes);
            }
        }

        return ApiResult.ok(roots);
    }

    private void attachChildren(String parentNo, Map<String, List<String>> kids, Map<String, Map<String, Object>> nodes) {
        Map<String, Object> parentNode = nodes.get(parentNo);
        if (parentNode != null) {
            List<Map<String, Object>> childList = (List<Map<String, Object>>)parentNode.get("children");

            for (String cno : kids.getOrDefault(parentNo, List.of())) {
                Map<String, Object> cn = nodes.get(cno);
                if (cn != null) {
                    childList.add(cn);
                    this.attachChildren(cno, kids, nodes);
                }
            }
        }
    }

    private List<AssetEntity> collectSubtree(String rootNo) {
        return this.assetMapper.selectSubtree(rootNo);
    }

    @GetMapping({"/meta"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<Map<String, Object>> meta() {
        List<DictEntity> dicts = this.dictMapper
            .selectList(
                ((Wrappers.<DictEntity>lambdaQuery()
                            .in(DictEntity::getDictType, List.of("asset_status_biz", "asset_status_fin", "region")))
                        .eq(DictEntity::getStatus, 1))
                    .orderByAsc(DictEntity::getSort)
            );
        List<String> bizStates = new ArrayList<>();
        List<String> finStates = new ArrayList<>();
        List<Map<String, Object>> regions = new ArrayList<>();

        for (DictEntity d : dicts) {
            String var7 = d.getDictType();
            switch (var7) {
                case "asset_status_biz":
                    bizStates.add(d.getDictKey());
                    break;
                case "asset_status_fin":
                    finStates.add(d.getDictKey());
                    break;
                case "region":
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("key", d.getDictKey());
                    r.put("label", d.getDictValue());
                    regions.add(r);
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("bizStates", bizStates);
        meta.put("finStates", finStates);
        meta.put("regions", regions);
        meta.put("ownerTypes", List.of("SELF", "LEASE"));
        return ApiResult.ok(meta);
    }

    @GetMapping({"/{assetNo}/events"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<PageResult<Map<String, Object>>> events(
        @PathVariable String assetNo, @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size
    ) {
        this.requireAsset(assetNo);
        Page<AssetEventEntity> p = new Page(page, size);
        this.assetEventMapper
            .selectPage(
                p,
                ((Wrappers.<AssetEventEntity>lambdaQuery().eq(AssetEventEntity::getAssetNo, assetNo))
                        .orderByDesc(AssetEventEntity::getEventTime))
                    .orderByDesc(AssetEventEntity::getId)
            );
        List<Map<String, Object>> records = p.getRecords().stream().map(e -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("eventType", e.getEventType());
            row.put("operator", e.getOperator());
            row.put("refDoc", e.getRefDoc());
            row.put("snapshot", e.getSnapshot());
            row.put("eventTime", e.getEventTime());
            return row;
        }).toList();
        return ApiResult.ok(new PageResult<>(p.getTotal(), records));
    }

    /**
     * v3.6：资产全生命周期时间线——聚合台账事件（asset_event）、关联工单（发起/完成）、
     * 月度折旧计提（depreciation_record）、告警触发/恢复（alert_event）四类来源，
     * 统一为 {source, eventType, operator, refDoc, detail, eventTime} 按时间倒序分页。
     * source 过滤为逗号分隔白名单（asset/workorder/fin/alert）；detail 为 JSON 字符串（::text 惯例）。
     */
    @GetMapping({"/{assetNo}/timeline"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<PageResult<Map<String, Object>>> timeline(
        @PathVariable String assetNo,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String source
    ) {
        this.requireAsset(assetNo);
        long s = Math.max(1L, Math.min(size, 100L));
        long p = Math.max(1L, page);
        List<String> sources = new ArrayList<>();
        if (source != null && !source.isBlank()) {
            for (String tok : source.split(",")) {
                String t = tok.trim();
                if (!t.isEmpty()) {
                    if (!List.of("asset", "workorder", "fin", "alert").contains(t)) {
                        throw BusinessException.badRequest("未知来源：" + t);
                    }
                    sources.add(t);
                }
            }
        }

        String union = "SELECT e.id, 'asset' AS source, e.event_type, e.operator, e.ref_doc, e.snapshot AS detail, e.event_time "
            + "FROM asset_event e WHERE e.asset_no = ? "
            + "UNION ALL "
            // workorder 主键是 wo_no（无 id 列），id 置 NULL；前端 key 用 source+eventType+refDoc 组合
            + "SELECT NULL::bigint, 'workorder', 'WO_SUBMIT', COALESCE(w.applicant_username, w.applicant), w.wo_no, "
            + "jsonb_build_object('woNo', w.wo_no, 'woType', w.wo_type, 'title', w.title, 'status', w.status, 'priority', w.priority), "
            + "COALESCE(w.apply_time, w.created_at) FROM workorder w WHERE (',' || w.related_assets || ',') LIKE ('%,' || ? || ',%') "
            + "UNION ALL "
            + "SELECT NULL::bigint, 'workorder', 'WO_FINISH', w.handler, w.wo_no, "
            + "jsonb_build_object('woNo', w.wo_no, 'woType', w.wo_type, 'title', w.title, 'status', w.status), "
            + "w.finish_time FROM workorder w WHERE (',' || w.related_assets || ',') LIKE ('%,' || ? || ',%') AND w.finish_time IS NOT NULL "
            + "UNION ALL "
            + "SELECT d.id, 'fin', 'DEPRECIATION', NULL, to_char(d.period, 'YYYY-MM'), "
            + "jsonb_build_object('period', to_char(d.period, 'YYYY-MM'), 'monthDep', d.month_dep, 'accumulated', d.accumulated, "
            + "'netValue', d.net_value, 'method', d.method), d.period::timestamptz "
            + "FROM depreciation_record d WHERE d.asset_no = ? "
            + "UNION ALL "
            + "SELECT a.id, 'alert', 'ALERT_FIRING', NULL, a.fingerprint, "
            + "jsonb_build_object('alertname', a.alertname, 'severity', a.severity, 'summary', a.summary, 'instance', a.instance), "
            + "a.starts_at::timestamptz FROM alert_event a WHERE a.asset_no = ? "
            + "UNION ALL "
            + "SELECT a.id, 'alert', 'ALERT_RESOLVED', a.ack_by, a.fingerprint, "
            + "jsonb_build_object('alertname', a.alertname, 'severity', a.severity, 'summary', a.summary, 'closeReason', a.close_reason), "
            + "a.ends_at::timestamptz FROM alert_event a WHERE a.asset_no = ? AND a.status = 'RESOLVED' AND a.ends_at IS NOT NULL";
        String srcFilter = sources.isEmpty() ? "" : " WHERE source IN (" + String.join(",", sources.stream().map(x -> "?").toList()) + ")";

        List<Object> countArgs = new ArrayList<>(List.of(assetNo, assetNo, assetNo, assetNo, assetNo, assetNo));
        countArgs.addAll(sources);
        Long total = this.jdbc.queryForObject("SELECT COUNT(*) FROM (" + union + ") t" + srcFilter, Long.class, countArgs.toArray());
        List<Object> args = new ArrayList<>(countArgs);
        args.add(s);
        args.add((p - 1L) * s);
        List<Map<String, Object>> rows = this.jdbc.queryForList(
            "SELECT t.id, t.source, t.event_type AS \"eventType\", t.operator, t.ref_doc AS \"refDoc\", t.detail::text AS \"detail\", "
                + "t.event_time AS \"eventTime\" FROM (" + union + ") t" + srcFilter
                + " ORDER BY t.event_time DESC, t.source, t.ref_doc, t.id DESC LIMIT ? OFFSET ?",
            args.toArray()
        );
        return ApiResult.ok(new PageResult<>(total == null ? 0L : total, rows));
    }

    @GetMapping({"/lookup"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<List<AssetController.LookupVO>> lookup(@RequestParam String q) {
        if (q != null && !q.isBlank()) {
            String kw = q.trim();
            LoginUser who = CurrentUser.get();
            List<AssetEntity> hits = List.of();
            // v3.5：三个分支统一叠加数据范围（参照 list 的 buildListWrapper），范围外资产按不存在处理
            LambdaQueryWrapper<AssetEntity> byNoW = Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getAssetNo, kw);
            this.dataScope.applyAssetScope(byNoW, who);
            AssetEntity byNo = (AssetEntity)this.assetMapper.selectOne(byNoW);
            if (byNo != null) {
                hits = List.of(byNo);
            }

            // v3.5：sn 为当前用户隐藏字段时不允许以 sn 作为查询条件（参照 GlobalSearchController 的隐藏字段处理）
            if (hits.isEmpty() && !this.fieldPolicies.isHidden(who, "assets", "sn")) {
                LambdaQueryWrapper<AssetEntity> bySnW = Wrappers.<AssetEntity>lambdaQuery().apply("UPPER(sn) = {0}", new Object[]{kw.toUpperCase()});
                this.dataScope.applyAssetScope(bySnW, who);
                AssetEntity bySn = (AssetEntity)this.assetMapper.selectOne(bySnW);
                if (bySn != null) {
                    hits = List.of(bySn);
                }
            }

            if (hits.isEmpty()) {
                LambdaQueryWrapper<AssetEntity> likeW = Wrappers.<AssetEntity>lambdaQuery()
                    .and(
                        w -> ((w.like(AssetEntity::getName, kw)).or())
                                .like(AssetEntity::getAssetNo, kw)
                    );
                this.dataScope.applyAssetScope(likeW, who);
                hits = this.assetMapper.selectList(likeW.orderByAsc(AssetEntity::getAssetNo).last("LIMIT 5"));
            }

            return ApiResult.ok(hits.stream().limit(5L).map(AssetController.LookupVO::of).toList());
        } else {
            throw BusinessException.badRequest("查询参数 q 不能为空");
        }
    }

    @GetMapping({"/model-codes"})
    @RequirePerm(
        module = "assets"
    )
    public ApiResult<List<String>> modelCodes(@RequestParam(required = false) String keyword, @RequestParam(required = false) String categoryCode) {
        String kw = keyword != null && !keyword.isBlank() ? keyword.trim() : null;
        String cat = categoryCode != null && !categoryCode.isBlank() ? categoryCode.trim() : null;
        List<AssetEntity> rows = this.assetMapper
            .selectList(
                ((((((Wrappers.<AssetEntity>lambdaQuery()
                                            .select(AssetEntity::getModelCode)
                                            .isNotNull(AssetEntity::getModelCode))
                                        .ne(AssetEntity::getModelCode, ""))
                                    .eq(cat != null, AssetEntity::getCategoryCode, cat))
                                .like(kw != null, AssetEntity::getModelCode, kw))
                            .groupBy(AssetEntity::getModelCode))
                        .orderByAsc(AssetEntity::getModelCode))
                    .last("LIMIT 50")
            );
        List<String> codes = new ArrayList<>();

        for (AssetEntity r : rows) {
            if (r.getModelCode() != null && !r.getModelCode().isBlank()) {
                codes.add(r.getModelCode());
            }
        }

        return ApiResult.ok(codes);
    }

    @PostMapping
    @RequirePerm(
        module = "assets",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "assets",
        objType = "asset"
    )
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid AssetController.CreateAssetReq req) {
        AssetEntity a = new AssetEntity();
        a.setName(req.getName().trim());
        this.applyRefs(a, req.getCategoryCode(), req.getModelCode(), req.getRegionCode());
        a.setSn(this.normalizeSn(req.getSn()));
        if (a.getSn() != null && this.snExists(a.getSn(), null)) {
            throw BusinessException.conflict("SN 已存在：" + a.getSn());
        } else {
            a.setOwnerType(this.normalizeOwnerType(req.getOwnerType()));
            a.setStatusBiz("待上架");
            a.setStatusFin("未入账");
            a.setRoomCode(req.getRoomCode());
            a.setRackCode(req.getRackCode());
            a.setUnitNo(req.getUnitNo());
            a.setUHeight(req.getUHeight());
            a.setDepartment(req.getDepartment());
            a.setOwner(req.getOwner());
            a.setMgmtIp(req.getMgmtIp());
            a.setBizIp(req.getBizIp());
            a.setPurchaseDate(this.parseDate(req.getPurchaseDate(), "采购日期"));
            a.setPurchasePrice(req.getPurchasePrice());
            a.setWarrantyEnd(this.parseDate(req.getWarrantyEnd(), "保修到期"));
            a.setRackDate(this.parseDate(req.getRackDate(), "上架日期"));
            a.setContractNo(req.getContractNo());
            a.setRatedPowerW(checkPower(req.getRatedPowerW()));
            a.setParentAssetNo(this.blankToNull(req.getParentAssetNo()));
            if (a.getParentAssetNo() != null) {
                this.validateParent(a.getAssetNo() == null ? "__new__" : a.getAssetNo(), a.getParentAssetNo());
            }

            a.setAttrs(req.getAttrs() == null ? new HashMap<>() : new HashMap<>(req.getAttrs()));
            a.setVersion(1);
            a.setCreatedAt(LocalDateTime.now());
            a.setUpdatedAt(LocalDateTime.now());
            a.setAssetNo(this.assetNoGenerator.generate(req.getCategoryCode(), req.getRegionCode()));
            if (a.getParentAssetNo() != null && a.getParentAssetNo().equals(a.getAssetNo())) {
                throw BusinessException.badRequest("父资产不能指向自身");
            } else {
                this.assetMapper.insert(a);
                int effHeight = a.getUHeight() == null ? 1 : a.getUHeight();
                if (a.getRackCode() != null && a.getUnitNo() != null) {
                    this.rackOccupancyPort.syncAssetPlacement(a.getAssetNo(), null, null, null, a.getRackCode(), a.getUnitNo(), effHeight);
                }

                this.recordEvent(a.getAssetNo(), "CREATE", null, Map.of("after", this.toRow(a)));
                if (a.getRackCode() != null && a.getUnitNo() != null) {
                    this.recordEvent(a.getAssetNo(), "RACKED", null, Map.of("after", this.placementSnapshot(a.getRackCode(), a.getUnitNo(), effHeight)));
                }

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("assetNo", a.getAssetNo());
                return ApiResult.ok(out);
            }
        }
    }

    @PutMapping({"/{assetNo}"})
    @RequirePerm(
        module = "assets",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "assets",
        objType = "asset",
        objIdSpEL = "#assetNo"
    )
    @Transactional
    public ApiResult<Map<String, Object>> update(@PathVariable String assetNo, @RequestBody @Valid AssetController.UpdateAssetReq req) {
        if (req.getVersion() == null) {
            throw BusinessException.badRequest("请求体缺少 version（乐观锁校验需要）");
        } else {
            AssetEntity cur = this.requireAsset(assetNo);
            Map<String, Object> before = this.toRow(cur);
            if (req.getStatusBiz() != null && !req.getStatusBiz().equals(cur.getStatusBiz())) {
                throw BusinessException.badRequest("业务状态不允许直接修改，请使用状态流转接口");
            } else if (req.getStatusFin() != null && !req.getStatusFin().equals(cur.getStatusFin())) {
                throw BusinessException.badRequest("财务状态不允许直接修改，请使用状态流转接口");
            } else {
                String newCategory = cur.getCategoryCode();
                String newModel = cur.getModelCode();
                String newRegion = cur.getRegionCode();
                if (req.getCategoryCode() != null || req.getModelCode() != null || req.getRegionCode() != null) {
                    if (req.getCategoryCode() != null) {
                        newCategory = req.getCategoryCode().isBlank() ? null : req.getCategoryCode().trim();
                    }

                    if (req.getModelCode() != null) {
                        newModel = req.getModelCode().isBlank() ? null : req.getModelCode().trim();
                    }

                    if (req.getRegionCode() != null) {
                        newRegion = req.getRegionCode().isBlank() ? null : req.getRegionCode().trim();
                    }

                    this.validateRefs(newCategory, newModel, newRegion);
                }

                String newSn = cur.getSn();
                boolean clearSn = false;
                if (req.getSn() != null) {
                    String normalized = this.normalizeSn(req.getSn());
                    if (normalized == null) {
                        newSn = null;
                        clearSn = true;
                    } else if (!normalized.equals(cur.getSn())) {
                        if (this.snExists(normalized, assetNo)) {
                            throw BusinessException.conflict("SN 已存在：" + normalized);
                        }

                        newSn = normalized;
                    }
                }

                boolean parentProvided = req.getParentAssetNo() != null;
                boolean clearParent = false;
                if (parentProvided) {
                    String np = req.getParentAssetNo().isBlank() ? null : req.getParentAssetNo().trim();
                    if (np == null) {
                        clearParent = true;
                    } else if (!np.equals(cur.getParentAssetNo())) {
                        this.validateParent(assetNo, np);
                    }
                }

                AssetEntity upd = new AssetEntity();
                upd.setVersion(cur.getVersion() + 1);
                upd.setUpdatedAt(LocalDateTime.now());
                if (req.getName() != null) {
                    if (req.getName().isBlank()) {
                        throw BusinessException.badRequest("资产名称不能为空");
                    }

                    upd.setName(req.getName().trim());
                }

                upd.setSn(newSn);
                upd.setCategoryCode(newCategory);
                upd.setModelCode(newModel);
                upd.setRegionCode(newRegion);
                if (req.getOwnerType() != null) {
                    upd.setOwnerType(this.normalizeOwnerType(req.getOwnerType()));
                }

                upd.setRoomCode(req.getRoomCode());
                boolean rackProvided = req.getRackCode() != null;
                boolean clearPlacement = false;
                String newRack = cur.getRackCode();
                if (rackProvided) {
                    if (req.getRackCode().isBlank()) {
                        newRack = null;
                        clearPlacement = true;
                    } else {
                        newRack = req.getRackCode().trim();
                    }
                }

                Integer newUnit = req.getUnitNo() != null ? req.getUnitNo() : cur.getUnitNo();
                Integer newUHeight = req.getUHeight() != null ? req.getUHeight() : cur.getUHeight();
                if (!clearPlacement) {
                    if (rackProvided) {
                        upd.setRackCode(newRack);
                    }

                    upd.setUnitNo(req.getUnitNo());
                    upd.setUHeight(req.getUHeight());
                }

                upd.setDepartment(req.getDepartment());
                upd.setOwner(req.getOwner());
                upd.setMgmtIp(req.getMgmtIp());
                upd.setBizIp(req.getBizIp());
                if (req.getPurchaseDate() != null) {
                    upd.setPurchaseDate(this.parseDate(req.getPurchaseDate(), "采购日期"));
                }

                upd.setPurchasePrice(req.getPurchasePrice());
                if (req.getWarrantyEnd() != null) {
                    upd.setWarrantyEnd(this.parseDate(req.getWarrantyEnd(), "保修到期"));
                }

                if (req.getRackDate() != null) {
                    upd.setRackDate(this.parseDate(req.getRackDate(), "上架日期"));
                }

                upd.setContractNo(req.getContractNo());
                if (req.getRatedPowerW() != null) {
                    upd.setRatedPowerW(checkPower(req.getRatedPowerW()));
                }

                if (parentProvided && !clearParent) {
                    upd.setParentAssetNo(req.getParentAssetNo().trim());
                }

                if (req.getAttrs() != null) {
                    upd.setAttrs(req.getAttrs());
                }

                LambdaUpdateWrapper<AssetEntity> uw = (LambdaUpdateWrapper<AssetEntity>)(Wrappers.<AssetEntity>lambdaUpdate()
                        .eq(AssetEntity::getAssetNo, assetNo))
                    .eq(AssetEntity::getVersion, req.getVersion());
                if (clearSn) {
                    uw.set(AssetEntity::getSn, null);
                }

                if (clearParent) {
                    uw.set(AssetEntity::getParentAssetNo, null);
                }

                if (clearPlacement) {
                    uw.set(AssetEntity::getRackCode, null);
                    uw.set(AssetEntity::getUnitNo, null);
                }

                int rows = this.assetMapper.update(upd, uw);
                if (rows == 0) {
                    throw BusinessException.conflict("数据已被他人修改，请刷新后重试");
                } else {
                    int oldHeight = cur.getUHeight() == null ? 1 : cur.getUHeight();
                    int effNewHeight = newUHeight == null ? 1 : newUHeight;
                    boolean oldActive = cur.getRackCode() != null && cur.getUnitNo() != null;
                    boolean newActive = newRack != null && newUnit != null;
                    boolean placementChanged = oldActive != newActive
                        || oldActive && (!Objects.equals(cur.getRackCode(), newRack) || !Objects.equals(cur.getUnitNo(), newUnit) || oldHeight != effNewHeight);
                    if (placementChanged) {
                        this.rackOccupancyPort
                            .syncAssetPlacement(
                                assetNo, cur.getRackCode(), cur.getUnitNo(), oldHeight, newActive ? newRack : null, newActive ? newUnit : null, effNewHeight
                            );
                    }

                    AssetEntity after = this.requireAsset(assetNo);
                    this.recordEvent(assetNo, "UPDATE", null, Map.of("before", before, "after", this.toRow(after)));
                    if (placementChanged) {
                        Map<String, Object> beforePlacement = this.placementSnapshot(cur.getRackCode(), cur.getUnitNo(), oldHeight);
                        if (oldActive && newActive) {
                            Map<String, Object> snapshot = new LinkedHashMap<>();
                            snapshot.put("before", beforePlacement);
                            snapshot.put("after", this.placementSnapshot(newRack, newUnit, effNewHeight));
                            this.recordEvent(assetNo, "MOVED", null, snapshot);
                        } else if (oldActive) {
                            this.recordEvent(assetNo, "UNRACKED", null, Map.of("before", beforePlacement));
                        } else {
                            this.recordEvent(assetNo, "RACKED", null, Map.of("after", this.placementSnapshot(newRack, newUnit, effNewHeight)));
                        }
                    }

                    return ApiResult.ok(this.toRow(after));
                }
            }
        }
    }

    @PostMapping({"/{assetNo}/transitions"})
    @RequirePerm(
        module = "assets",
        write = true
    )
    @Audit(
        action = "STATUS_CHANGE",
        module = "assets",
        objType = "asset",
        objIdSpEL = "#assetNo"
    )
    @Transactional
    public ApiResult<Map<String, Object>> transitions(@PathVariable String assetNo, @RequestBody AssetController.TransitionReq req) {
        boolean hasBiz = req.getTargetBiz() != null && !req.getTargetBiz().isBlank();
        boolean hasFin = req.getTargetFin() != null && !req.getTargetFin().isBlank();
        if (hasBiz == hasFin) {
            throw BusinessException.badRequest("targetBiz 与 targetFin 必须提供且只能提供一个（双轨状态分开流转）");
        } else {
            AssetEntity cur = this.requireAsset(assetNo);
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("statusBiz", cur.getStatusBiz());
            before.put("statusFin", cur.getStatusFin());
            AssetEntity upd = new AssetEntity();
            upd.setVersion(cur.getVersion() + 1);
            upd.setUpdatedAt(LocalDateTime.now());
            if (hasBiz) {
                AssetStateMachine.assertTransit(cur.getStatusBiz(), req.getTargetBiz().trim());
                upd.setStatusBiz(req.getTargetBiz().trim());
            } else {
                AssetStateMachine.assertTransit(cur.getStatusFin(), req.getTargetFin().trim());
                upd.setStatusFin(req.getTargetFin().trim());
            }

            int rows = this.assetMapper
                .update(
                    upd,
                    (Wrappers.<AssetEntity>lambdaUpdate().eq(AssetEntity::getAssetNo, assetNo)).eq(AssetEntity::getVersion, cur.getVersion())
                );
            if (rows == 0) {
                throw BusinessException.conflict("数据已被他人修改，请刷新后重试");
            } else {
                AssetEntity after = this.requireAsset(assetNo);
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("before", before);
                Map<String, Object> afterSt = new LinkedHashMap<>();
                afterSt.put("statusBiz", after.getStatusBiz());
                afterSt.put("statusFin", after.getStatusFin());
                snapshot.put("after", afterSt);
                snapshot.put("reason", req.getReason());
                snapshot.put("refDoc", req.getRefDoc());
                this.recordEvent(assetNo, "STATUS_CHANGE", req.getRefDoc(), snapshot);
                Map<String, Object> out = this.toRow(after);
                if (Boolean.TRUE.equals(req.getCascadeChildren())) {
                    List<Map<String, Object>> cascaded = new ArrayList<>();
                    List<Map<String, Object>> skipped = new ArrayList<>();
                    String target = hasBiz ? req.getTargetBiz().trim() : req.getTargetFin().trim();

                    for (AssetEntity d : this.collectSubtree(assetNo)) {
                        if (!d.getAssetNo().equals(assetNo)) {
                            String from = hasBiz ? d.getStatusBiz() : d.getStatusFin();
                            if (!Objects.equals(from, target)) {
                                if (!AssetStateMachine.canTransit(from, target)) {
                                    Map<String, Object> s = new LinkedHashMap<>();
                                    s.put("assetNo", d.getAssetNo());
                                    s.put("name", d.getName());
                                    s.put("from", from);
                                    s.put("reason", "状态机不允许 " + from + " → " + target);
                                    skipped.add(s);
                                } else {
                                    AssetEntity cupd = new AssetEntity();
                                    cupd.setVersion(d.getVersion() + 1);
                                    cupd.setUpdatedAt(LocalDateTime.now());
                                    if (hasBiz) {
                                        cupd.setStatusBiz(target);
                                    } else {
                                        cupd.setStatusFin(target);
                                    }

                                    int crows = this.assetMapper
                                        .update(
                                            cupd,
                                            (Wrappers.<AssetEntity>lambdaUpdate().eq(AssetEntity::getAssetNo, d.getAssetNo()))
                                                .eq(AssetEntity::getVersion, d.getVersion())
                                        );
                                    if (crows == 0) {
                                        Map<String, Object> s = new LinkedHashMap<>();
                                        s.put("assetNo", d.getAssetNo());
                                        s.put("name", d.getName());
                                        s.put("reason", "并发冲突，已跳过");
                                        skipped.add(s);
                                    } else {
                                        Map<String, Object> cSnap = new LinkedHashMap<>();
                                        cSnap.put("cascadeFrom", assetNo);
                                        if (hasBiz) {
                                            cSnap.put("beforeBiz", from);
                                            cSnap.put("afterBiz", target);
                                        } else {
                                            cSnap.put("beforeFin", from);
                                            cSnap.put("afterFin", target);
                                        }

                                        cSnap.put("reason", req.getReason());
                                        this.recordEvent(d.getAssetNo(), "STATUS_CHANGE", req.getRefDoc(), cSnap);
                                        Map<String, Object> ok = new LinkedHashMap<>();
                                        ok.put("assetNo", d.getAssetNo());
                                        ok.put("name", d.getName());
                                        ok.put("from", from);
                                        ok.put("to", target);
                                        cascaded.add(ok);
                                    }
                                }
                            }
                        }
                    }

                    out.put("cascade", Map.of("cascaded", cascaded, "skipped", skipped, "target", target));
                }

                return ApiResult.ok(out);
            }
        }
    }

    @DeleteMapping({"/{assetNo}"})
    @RequirePerm(
        module = "assets",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "assets",
        objType = "asset",
        objIdSpEL = "#assetNo"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable String assetNo) {
        AssetEntity cur = this.requireAsset(assetNo);
        if (!DELETABLE_BIZ.contains(cur.getStatusBiz())) {
            throw BusinessException.badRequest("当前业务状态「" + cur.getStatusBiz() + "」不允许删除，仅" + String.join("/", DELETABLE_BIZ) + "可删除");
        } else {
            LoginUser current = CurrentUser.get();
            if (!this.rbacService.canDelete(current, "assets")) {
                throw BusinessException.forbidden("当前角色未授予「资产」的删除权限");
            } else {
                long childCount = this.assetMapper.selectCount(Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getParentAssetNo, assetNo));
                if (childCount > 0L) {
                    throw BusinessException.badRequest("该资产下还有 " + childCount + " 个子资产（部件），请先解除或删除子资产后再删父资产");
                } else {
                    // v3.5：被未完结工单引用的资产不允许删除（防悬挂引用），409 并列工单号
                    this.assertNoOpenWorkorderRef(assetNo);
                    // v3.5：已进入折旧/减值/处置流程的财务档案不允许随资产删除直接抹除，须先走财务处置流程
                    this.assertFinanceDeletable(cur);
                    if (cur.getRackCode() != null) {
                        this.rackOccupancyPort
                            .syncAssetPlacement(assetNo, cur.getRackCode(), cur.getUnitNo(), cur.getUHeight() == null ? 1 : cur.getUHeight(), null, null, null);
                    }

                    this.assetFinanceMapper.deleteById(assetNo);
                    this.assetMapper.deleteById(assetNo);
                    this.recordEvent(assetNo, "DELETE", null, Map.of("before", this.toRow(cur)));
                    return ApiResult.ok();
                }
            }
        }
    }

    /** v3.5：删除前校验资产是否被未完结工单（非已关闭/已完成）引用，命中则拒绝删除并列出工单号 */
    private void assertNoOpenWorkorderRef(String assetNo) {
        List<String> woNos = this.jdbc
            .queryForList(
                "SELECT DISTINCT w.wo_no FROM workorder_item i JOIN workorder w ON w.wo_no = i.wo_no\nWHERE i.asset_no = ? AND w.deleted_at IS NULL AND w.status NOT IN ('已完成', '已关闭')\nORDER BY w.wo_no LIMIT 10\n",
                String.class,
                new Object[]{assetNo}
            );
        if (!woNos.isEmpty()) {
            throw BusinessException.conflict("资产已被未完结工单引用：" + String.join("、", woNos) + "，请先处理相关工单后再删除");
        }
    }

    /**
     * v3.5：财务状态校验——status_fin 已进入折旧/减值/处置等流程的资产，其 asset_finance 档案
     * 已承载财务台账数据，不允许随资产删除直接物理抹除，须先走财务处置/核销流程。
     * null（未初始化财务状态）与「未入账」「已入账」放行。
     */
    private void assertFinanceDeletable(AssetEntity cur) {
        String fin = cur.getStatusFin();
        if (fin != null && !DELETABLE_FIN.contains(fin)) {
            throw BusinessException.conflict("资产财务状态为「" + fin + "」，已进入财务核算流程，不允许直接删除，请先完成财务处置/核销后再删除");
        }
    }

    @PostMapping({"/batch-transitions"})
    @RequirePerm(
        module = "assets",
        write = true
    )
    @Audit(
        action = "BATCH_STATUS_CHANGE",
        module = "assets",
        objType = "asset"
    )
    public ApiResult<Map<String, Object>> batchTransitions(@RequestBody @Valid AssetController.BatchTransitionReq req) {
        boolean hasBiz = req.getTargetBiz() != null && !req.getTargetBiz().isBlank();
        boolean hasFin = req.getTargetFin() != null && !req.getTargetFin().isBlank();
        if (hasBiz == hasFin) {
            throw BusinessException.badRequest("targetBiz 与 targetFin 必须提供且只能提供一个（双轨状态分开流转）");
        } else {
            String target = (hasBiz ? req.getTargetBiz() : req.getTargetFin()).trim();
            List<String> nos = this.normalizeBatchNos(req.getAssetNos());
            List<Map<String, Object>> changed = new ArrayList<>();
            List<Map<String, Object>> skipped = new ArrayList<>();

            for (String assetNo : nos) {
                try {
                    this.rowTx
                        .executeWithoutResult(
                            tx -> {
                                AssetEntity curx = (AssetEntity)this.assetMapper.selectById(assetNo);
                                if (curx == null) {
                                    throw BusinessException.notFound("资产不存在：" + assetNo);
                                } else {
                                    String from = hasBiz ? curx.getStatusBiz() : curx.getStatusFin();
                                    if (Objects.equals(from, target)) {
                                        throw new BusinessException(400, "已是目标状态，无需流转");
                                    } else {
                                        AssetStateMachine.assertTransit(from, target);
                                        AssetEntity upd = new AssetEntity();
                                        upd.setVersion(curx.getVersion() + 1);
                                        upd.setUpdatedAt(LocalDateTime.now());
                                        if (hasBiz) {
                                            upd.setStatusBiz(target);
                                        } else {
                                            upd.setStatusFin(target);
                                        }

                                        int rows = this.assetMapper
                                            .update(
                                                upd,
                                                (Wrappers.<AssetEntity>lambdaUpdate().eq(AssetEntity::getAssetNo, assetNo))
                                                    .eq(AssetEntity::getVersion, curx.getVersion())
                                            );
                                        if (rows == 0) {
                                            throw new BusinessException(409, "并发冲突，已跳过");
                                        } else {
                                            Map<String, Object> snap = new LinkedHashMap<>();
                                            Map<String, Object> before = new LinkedHashMap<>();
                                            Map<String, Object> after = new LinkedHashMap<>();
                                            if (hasBiz) {
                                                before.put("statusBiz", from);
                                                after.put("statusBiz", target);
                                            } else {
                                                before.put("statusFin", from);
                                                after.put("statusFin", target);
                                            }

                                            snap.put("before", before);
                                            snap.put("after", after);
                                            snap.put("reason", req.getReason());
                                            snap.put("refDoc", req.getRefDoc());
                                            snap.put("batch", true);
                                            AssetEventEntity ev = new AssetEventEntity();
                                            ev.setAssetNo(assetNo);
                                            ev.setEventType("STATUS_CHANGE");
                                            LoginUser u = CurrentUser.get();
                                            ev.setOperator(u == null ? null : u.username());
                                            ev.setRefDoc(req.getRefDoc());
                                            ev.setSnapshot(snap);
                                            ev.setEventTime(LocalDateTime.now());
                                            this.assetEventMapper.insert(ev);
                                            Map<String, Object> ok = batchItem(assetNo, curx.getName(), null);
                                            ok.put("from", from);
                                            ok.put("to", target);
                                            changed.add(ok);
                                        }
                                    }
                                }
                            }
                        );
                } catch (Exception var12) {
                    // v3.5：扩大捕获范围，其他 RuntimeException 也计入失败行，已成功行继续返回
                    AssetEntity cur = (AssetEntity)this.assetMapper.selectById(assetNo);
                    skipped.add(batchItem(assetNo, cur == null ? null : cur.getName(), var12.getMessage() == null ? var12.getClass().getSimpleName() : var12.getMessage()));
                }
            }

            return ApiResult.ok(batchResult(nos.size(), changed, skipped, target));
        }
    }

    @PostMapping({"/batch-update"})
    @RequirePerm(
        module = "assets",
        write = true,
        action = "update"
    )
    @Audit(
        action = "BATCH_UPDATE",
        module = "assets",
        objType = "asset"
    )
    public ApiResult<Map<String, Object>> batchUpdate(@RequestBody @Valid AssetController.BatchUpdateReq req) {
        boolean anyField = req.getCategoryCode() != null
            || req.getModelCode() != null
            || req.getRegionCode() != null
            || req.getOwnerType() != null
            || req.getDepartment() != null
            || req.getOwner() != null
            || req.getContractNo() != null;
        if (!anyField) {
            throw BusinessException.badRequest("请至少填写一个要修改的字段（留空字段不修改）");
        } else {
            String cat = req.getCategoryCode() == null ? null : this.blankToNull(req.getCategoryCode());
            String model = req.getModelCode() == null ? null : this.blankToNull(req.getModelCode());
            String region = req.getRegionCode() == null ? null : this.blankToNull(req.getRegionCode());
            if (req.getCategoryCode() != null || req.getModelCode() != null || req.getRegionCode() != null) {
                this.validateRefs(cat, model, region);
            }

            String ownerType;
            if (req.getOwnerType() != null) {
                ownerType = req.getOwnerType().isBlank() ? null : this.normalizeOwnerType(req.getOwnerType());
            } else {
                ownerType = null;
            }

            List<String> nos = this.normalizeBatchNos(req.getAssetNos());
            List<Map<String, Object>> changed = new ArrayList<>();
            List<Map<String, Object>> skipped = new ArrayList<>();

            for (String assetNo : nos) {
                try {
                    this.rowTx
                        .executeWithoutResult(
                            tx -> {
                                AssetEntity curx = (AssetEntity)this.assetMapper.selectById(assetNo);
                                if (curx == null) {
                                    throw BusinessException.notFound("资产不存在：" + assetNo);
                                } else {
                                    Map<String, Object> before = this.toRow(curx);
                                    AssetEntity upd = new AssetEntity();
                                    upd.setVersion(curx.getVersion() + 1);
                                    upd.setUpdatedAt(LocalDateTime.now());
                                    LambdaUpdateWrapper<AssetEntity> uw = (LambdaUpdateWrapper<AssetEntity>)(Wrappers.<AssetEntity>lambdaUpdate()
                                            .eq(AssetEntity::getAssetNo, assetNo))
                                        .eq(AssetEntity::getVersion, curx.getVersion());
                                    if (req.getCategoryCode() != null) {
                                        upd.setCategoryCode(cat);
                                    }

                                    if (req.getModelCode() != null) {
                                        upd.setModelCode(model);
                                    }

                                    if (req.getRegionCode() != null) {
                                        upd.setRegionCode(region);
                                    }

                                    if (ownerType != null) {
                                        upd.setOwnerType(ownerType);
                                    } else if (req.getOwnerType() != null && req.getOwnerType().isBlank()) {
                                        uw.set(AssetEntity::getOwnerType, null);
                                    }

                                    if (req.getDepartment() != null) {
                                        if (req.getDepartment().isBlank()) {
                                            uw.set(AssetEntity::getDepartment, null);
                                        } else {
                                            upd.setDepartment(req.getDepartment().trim());
                                        }
                                    }

                                    if (req.getOwner() != null) {
                                        if (req.getOwner().isBlank()) {
                                            uw.set(AssetEntity::getOwner, null);
                                        } else {
                                            upd.setOwner(req.getOwner().trim());
                                        }
                                    }

                                    if (req.getContractNo() != null) {
                                        if (req.getContractNo().isBlank()) {
                                            uw.set(AssetEntity::getContractNo, null);
                                        } else {
                                            upd.setContractNo(req.getContractNo().trim());
                                        }
                                    }

                                    int rows = this.assetMapper.update(upd, uw);
                                    if (rows == 0) {
                                        throw new BusinessException(409, "并发冲突，已跳过");
                                    } else {
                                        AssetEntity after = (AssetEntity)this.assetMapper.selectById(assetNo);
                                        Map<String, Object> snap = new LinkedHashMap<>();
                                        snap.put("before", before);
                                        snap.put("after", this.toRow(after));
                                        snap.put("batch", true);
                                        AssetEventEntity ev = new AssetEventEntity();
                                        ev.setAssetNo(assetNo);
                                        ev.setEventType("UPDATE");
                                        LoginUser u = CurrentUser.get();
                                        ev.setOperator(u == null ? null : u.username());
                                        ev.setSnapshot(snap);
                                        ev.setEventTime(LocalDateTime.now());
                                        this.assetEventMapper.insert(ev);
                                        changed.add(batchItem(assetNo, curx.getName(), null));
                                    }
                                }
                            }
                        );
                } catch (Exception var14) {
                    // v3.5：扩大捕获范围，其他 RuntimeException 也计入失败行，已成功行继续返回
                    AssetEntity cur = (AssetEntity)this.assetMapper.selectById(assetNo);
                    skipped.add(batchItem(assetNo, cur == null ? null : cur.getName(), var14.getMessage() == null ? var14.getClass().getSimpleName() : var14.getMessage()));
                }
            }

            return ApiResult.ok(batchResult(nos.size(), changed, skipped, null));
        }
    }

    @PostMapping({"/batch-delete"})
    @RequirePerm(
        module = "assets",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "BATCH_DELETE",
        module = "assets",
        objType = "asset"
    )
    public ApiResult<Map<String, Object>> batchDelete(@RequestBody @Valid AssetController.BatchNosReq req) {
        LoginUser current = CurrentUser.get();
        if (!this.rbacService.canDelete(current, "assets")) {
            throw BusinessException.forbidden("当前角色未授予「资产」的删除权限");
        } else {
            List<String> nos = this.normalizeBatchNos(req.getAssetNos());
            List<Map<String, Object>> changed = new ArrayList<>();
            List<Map<String, Object>> skipped = new ArrayList<>();

            for (String assetNo : nos) {
                try {
                    this.rowTx
                        .executeWithoutResult(
                            tx -> {
                                AssetEntity curx = (AssetEntity)this.assetMapper.selectById(assetNo);
                                if (curx == null) {
                                    throw BusinessException.notFound("资产不存在：" + assetNo);
                                } else if (!DELETABLE_BIZ.contains(curx.getStatusBiz())) {
                                    throw new BusinessException(400, "状态「" + curx.getStatusBiz() + "」不允许删除（仅" + String.join("/", DELETABLE_BIZ) + "）");
                                } else {
                                    long childCount = this.assetMapper.selectCount(Wrappers.<AssetEntity>lambdaQuery().eq(AssetEntity::getParentAssetNo, assetNo));
                                    if (childCount > 0L) {
                                        throw new BusinessException(400, "还有 " + childCount + " 个子资产，不能删除");
                                    } else {
                                        // v3.5：被未完结工单引用的资产跳过删除（计入错误行，不中断整批）
                                        this.assertNoOpenWorkorderRef(assetNo);
                                        // v3.5：财务状态已进入折旧/处置流程的跳过删除（计入错误行，不中断整批）
                                        this.assertFinanceDeletable(curx);
                                        if (curx.getRackCode() != null) {
                                            this.rackOccupancyPort
                                                .syncAssetPlacement(
                                                    assetNo,
                                                    curx.getRackCode(),
                                                    curx.getUnitNo(),
                                                    curx.getUHeight() == null ? 1 : curx.getUHeight(),
                                                    null,
                                                    null,
                                                    null
                                                );
                                        }

                                        this.assetFinanceMapper.deleteById(assetNo);
                                        this.assetMapper.deleteById(assetNo);
                                        Map<String, Object> snap = new LinkedHashMap<>();
                                        snap.put("before", this.toRow(curx));
                                        snap.put("batch", true);
                                        AssetEventEntity ev = new AssetEventEntity();
                                        ev.setAssetNo(assetNo);
                                        ev.setEventType("DELETE");
                                        ev.setOperator(current == null ? null : current.username());
                                        ev.setSnapshot(snap);
                                        ev.setEventTime(LocalDateTime.now());
                                        this.assetEventMapper.insert(ev);
                                        changed.add(batchItem(assetNo, curx.getName(), "已删除"));
                                    }
                                }
                            }
                        );
                } catch (Exception var10) {
                    // v3.5：扩大捕获范围，其他 RuntimeException 也计入失败行，已成功行继续返回
                    AssetEntity cur = (AssetEntity)this.assetMapper.selectById(assetNo);
                    skipped.add(batchItem(assetNo, cur == null ? null : cur.getName(), var10.getMessage() == null ? var10.getClass().getSimpleName() : var10.getMessage()));
                }
            }

            return ApiResult.ok(batchResult(nos.size(), changed, skipped, null));
        }
    }

    private static Map<String, Object> batchResult(int total, List<Map<String, Object>> changed, List<Map<String, Object>> skipped, String target) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("changed", changed);
        out.put("skipped", skipped);
        if (target != null) {
            out.put("target", target);
        }

        return out;
    }

    @GetMapping({"/export"})
    @RequirePerm(
        module = "assets",
        action = "export"
    )
    public void export(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String statusBiz,
        @RequestParam(required = false) String categoryCode,
        @RequestParam(required = false) String regionCode,
        @RequestParam(required = false) String roomCode,
        @RequestParam(required = false) String parentAssetNo,
        @RequestParam(required = false) String assetNos,
        HttpServletResponse response
    ) throws IOException {
        List<AssetEntity> rows;
        if (assetNos != null && !assetNos.isBlank()) {
            List<String> nos = new ArrayList<>();

            for (String s : assetNos.split(",")) {
                if (!s.isBlank()) {
                    nos.add(s.trim());
                }
            }

            // v3.4：按编号导出也要受数据范围约束（v3.3 直接 selectBatchIds，绕过了数据范围）
            LambdaQueryWrapper<AssetEntity> byNos = Wrappers.<AssetEntity>lambdaQuery().in(AssetEntity::getAssetNo, nos);
            this.dataScope.applyAssetScope(byNos, com.dcim.security.CurrentUser.get());
            rows = nos.isEmpty() ? List.of() : this.assetMapper.selectList(byNos);
        } else {
            // v3.5：对齐工单导出行数上限 EXPORT_MAX_ROWS=10000；多取一行探测超限并提示，而非静默截断
            rows = this.assetMapper
                .selectList(this.buildListWrapper(keyword, statusBiz, categoryCode, regionCode, roomCode, parentAssetNo).last("LIMIT " + (EXPORT_MAX_ROWS + 1)));
            if (rows.size() > EXPORT_MAX_ROWS) {
                throw BusinessException.badRequest("导出数据超过 " + EXPORT_MAX_ROWS + " 行上限，请缩小筛选范围后再导出");
            }
        }

        int[] keep = this.fieldPolicies.keepColumns(com.dcim.security.CurrentUser.get(), "assets", CSV_FIELDS);
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(com.dcim.security.field.FieldPolicyService.pick(CSV_HEADERS, keep))).append('\n');

        for (AssetEntity a : rows) {
            Map<String, Object> attrs = a.getAttrs() == null ? Map.of() : a.getAttrs();
            List<Object> cells = Arrays.asList(
                a.getAssetNo(),
                a.getParentAssetNo(),
                a.getName(),
                a.getSn(),
                a.getCategoryCode(),
                a.getModelCode(),
                a.getOwnerType(),
                a.getStatusBiz(),
                a.getStatusFin(),
                a.getRegionCode(),
                a.getRoomCode(),
                a.getRackCode(),
                a.getUnitNo(),
                a.getUHeight(),
                a.getDepartment(),
                a.getOwner(),
                a.getMgmtIp(),
                a.getBizIp(),
                this.dateText(a.getPurchaseDate()),
                a.getPurchasePrice(),
                this.dateText(a.getWarrantyEnd()),
                this.dateText(a.getRackDate()),
                a.getContractNo(),
                attrs.get("remark")
            );
            csv.append(CsvSupport.line(com.dcim.security.field.FieldPolicyService.pick(cells, keep))).append('\n');
        }

        this.writeCsv(response, "资产-" + LocalDate.now() + ".csv", csv.toString());
    }

    @GetMapping({"/template"})
    @RequirePerm(
        module = "assets"
    )
    public void template(HttpServletResponse response) throws IOException {
        int[] keep = this.fieldPolicies.keepColumns(com.dcim.security.CurrentUser.get(), "assets", CSV_FIELDS);
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(com.dcim.security.field.FieldPolicyService.pick(CSV_HEADERS, keep))).append('\n');
        csv.append(
                CsvSupport.line(
                    List.of(
                        "SRV-QY26-2026-00001",
                        "",
                        "示例服务器",
                        "SN-EXAMPLE-0001",
                        "SRV",
                        "EXAMPLE-MODEL",
                        "SELF",
                        "待上架",
                        "未入账",
                        "QY",
                        "ROOM-01",
                        "RK-001",
                        1,
                        2,
                        "运维部",
                        "张三",
                        "10.0.0.1",
                        "10.0.0.2",
                        LocalDate.now(),
                        "50000.00",
                        LocalDate.now().plusYears(3L),
                        LocalDate.now(),
                        "HT-2026-001",
                        "示例备注"
                    )
                )
            )
            .append('\n');
        this.writeCsv(response, "资产导入模板.csv", csv.toString());
    }

    @PostMapping(
        value = {"/import"},
        consumes = {"multipart/form-data"}
    )
    @RequirePerm(
        module = "assets",
        write = true,
        action = "import"
    )
    @Audit(
        action = "IMPORT",
        module = "assets",
        objType = "asset"
    )
    public ApiResult<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            // v3.5：解析前校验大小与行数上限（10MB / 50000 行），超限 400 提示，避免全量读入内存
            if (file.getSize() > IMPORT_MAX_BYTES) {
                throw BusinessException.badRequest("导入文件不能超过 10MB");
            }
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<List<String>> rows = CsvSupport.parse(text);
            if (rows.size() > IMPORT_MAX_ROWS) {
                throw BusinessException.badRequest("导入数据超过 " + IMPORT_MAX_ROWS + " 行上限，请拆分文件后重试");
            }
            if (rows.isEmpty()) {
                throw BusinessException.badRequest("CSV 文件为空");
            } else {
                List<String> header = rows.get(0);
                String firstCell = header.isEmpty() ? "" : header.get(0).trim();
                boolean hasHeader = "资产名称".equals(firstCell) || "资产编号".equals(firstCell);
                Map<String, Integer> colIndex = new LinkedHashMap<>();
                if (hasHeader) {
                    for (int i = 0; i < header.size(); i++) {
                        colIndex.put(header.get(i).trim(), i);
                    }
                }

                LoginUser lu = CurrentUser.get();
                boolean customStatusAllowed = lu != null && lu.isAdmin();
                List<List<String>> dataRows = rows.subList(hasHeader ? 1 : 0, rows.size());
                int success = 0;
                List<Map<String, Object>> errors = new ArrayList<>();
                Set<String> fileSns = new HashSet<>();

                for (int i = 0; i < dataRows.size(); i++) {
                    int rowNo = (hasHeader ? 1 : 0) + i + 1;

                    try {
                        this.importAssetRow(dataRows.get(i), colIndex, fileSns, customStatusAllowed);
                        success++;
                    } catch (BusinessException var18) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("row", rowNo);
                        err.put("reason", var18.getMessage());
                        errors.add(err);
                    } catch (DataIntegrityViolationException var19) {
                        Map<String, Object> errx = new LinkedHashMap<>();
                        errx.put("row", rowNo);
                        errx.put("reason", "数据写入冲突（可能已被并发导入）");
                        errors.add(errx);
                    }
                }

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("total", dataRows.size());
                out.put("success", success);
                out.put("failed", errors.size());
                out.put("errors", errors);
                return ApiResult.ok(out);
            }
        } else {
            throw BusinessException.badRequest("请上传要导入的 CSV 文件");
        }
    }

    private String importAssetRow(List<String> cells, Map<String, Integer> colIndex, Set<String> fileSns, boolean customStatusAllowed) {
        int[] ix = new int[]{
            this.col(colIndex, "资产名称", 0),
            this.col(colIndex, "SN", 1),
            this.col(colIndex, "分类编码", 2),
            this.col(colIndex, "型号编码", 3),
            this.col(colIndex, "权属", 4),
            this.col(colIndex, "区域编码", 5),
            this.col(colIndex, "机房编码", 6),
            this.col(colIndex, "机柜编号", 7),
            this.col(colIndex, "起始U位", 8),
            this.col(colIndex, "U位高度", 9),
            this.col(colIndex, "使用部门", 10),
            this.col(colIndex, "责任人", 11),
            this.col(colIndex, "管理IP", 12),
            this.col(colIndex, "业务IP", 13),
            this.col(colIndex, "采购日期", 14),
            this.col(colIndex, "采购单价", 15),
            this.col(colIndex, "保修到期", 16),
            this.col(colIndex, "上架日期", 17),
            this.col(colIndex, "合同号", 18),
            this.col(colIndex, "备注", 19)
        };
        String name = this.cellAt(cells, ix[0]);
        String sn = this.cellAt(cells, ix[1]);
        String categoryCode = this.cellAt(cells, ix[2]);
        String parentAssetNo = null;
        if (!colIndex.isEmpty()) {
            Integer pIdx = colIndex.get("父资产编号");
            if (pIdx != null) {
                parentAssetNo = this.blankToNull(this.cellAt(cells, pIdx));
            }
        }

        String modelCode = this.cellAt(cells, ix[3]);
        String ownerType = this.cellAt(cells, ix[4]);
        String regionCode = this.cellAt(cells, ix[5]);
        String roomCode = this.cellAt(cells, ix[6]);
        String rackCode = this.cellAt(cells, ix[7]);
        String unitNo = this.cellAt(cells, ix[8]);
        String uHeight = this.cellAt(cells, ix[9]);
        String department = this.cellAt(cells, ix[10]);
        String owner = this.cellAt(cells, ix[11]);
        String mgmtIp = this.cellAt(cells, ix[12]);
        String bizIp = this.cellAt(cells, ix[13]);
        String purchaseDate = this.cellAt(cells, ix[14]);
        String purchasePrice = this.cellAt(cells, ix[15]);
        String warrantyEnd = this.cellAt(cells, ix[16]);
        String rackDate = this.cellAt(cells, ix[17]);
        String contractNo = this.cellAt(cells, ix[18]);
        String remark = this.cellAt(cells, ix[19]);
        if (name != null && !name.isBlank()) {
            String normalizedSn = this.normalizeSn(sn);
            if (normalizedSn == null) {
                throw BusinessException.badRequest("SN 不能为空");
            } else if (this.snExists(normalizedSn, null)) {
                throw BusinessException.conflict("SN 已存在（库内）：" + normalizedSn);
            } else if (fileSns.contains(normalizedSn)) {
                throw BusinessException.conflict("SN 已存在（文件内重复）：" + normalizedSn);
            } else {
                this.validateRefs(this.blankToNull(categoryCode), this.blankToNull(modelCode), this.blankToNull(regionCode));
                if (parentAssetNo != null) {
                    this.validateParent("__new__", parentAssetNo);
                }

                AssetEntity a = new AssetEntity();
                a.setParentAssetNo(parentAssetNo);
                a.setName(name.trim());
                a.setSn(normalizedSn);
                a.setCategoryCode(this.blankToNull(categoryCode));
                a.setModelCode(this.blankToNull(modelCode));
                a.setOwnerType(ownerType != null && !ownerType.isBlank() ? this.normalizeOwnerType(ownerType) : "SELF");
                a.setRegionCode(this.blankToNull(regionCode));
                a.setRoomCode(this.blankToNull(roomCode));
                a.setRackCode(this.blankToNull(rackCode));
                a.setUnitNo(this.parseInt(unitNo, "起始U位"));
                a.setUHeight(this.parseInt(uHeight, "U位高度"));
                a.setDepartment(this.blankToNull(department));
                a.setOwner(this.blankToNull(owner));
                a.setMgmtIp(this.blankToNull(mgmtIp));
                a.setBizIp(this.blankToNull(bizIp));
                a.setPurchaseDate(this.parseDate(this.blankToNull(purchaseDate), "采购日期"));
                a.setPurchasePrice(this.parseDecimal(this.blankToNull(purchasePrice), "采购单价"));
                a.setWarrantyEnd(this.parseDate(this.blankToNull(warrantyEnd), "保修到期"));
                a.setRackDate(this.parseDate(this.blankToNull(rackDate), "上架日期"));
                a.setContractNo(this.blankToNull(contractNo));
                Map<String, Object> attrs = new HashMap<>();
                if (remark != null && !remark.isBlank()) {
                    attrs.put("remark", remark.trim());
                }

                a.setAttrs(attrs);
                a.setStatusBiz("待上架");
                a.setStatusFin("未入账");
                if (customStatusAllowed) {
                    String bizStatus = this.resolveStatusColumn(cells, colIndex, "业务状态", "asset_status_biz");
                    String finStatus = this.resolveStatusColumn(cells, colIndex, "财务状态", "asset_status_fin");
                    if (bizStatus != null) {
                        a.setStatusBiz(bizStatus);
                    }

                    if (finStatus != null) {
                        a.setStatusFin(finStatus);
                    }
                }

                a.setVersion(1);
                a.setCreatedAt(LocalDateTime.now());
                a.setUpdatedAt(LocalDateTime.now());

                try {
                    this.rowTx.executeWithoutResult(tx -> {
                        a.setAssetNo(this.assetNoGenerator.generate(a.getCategoryCode(), a.getRegionCode()));
                        this.assetMapper.insert(a);
                        this.recordEvent(a.getAssetNo(), "IMPORT", null, Map.of("after", this.toRow(a)));
                    });
                } catch (DuplicateKeyException var32) {
                    throw BusinessException.conflict("SN 已存在（并发冲突）：" + normalizedSn);
                }

                fileSns.add(normalizedSn);
                return normalizedSn;
            }
        } else {
            throw BusinessException.badRequest("资产名称不能为空");
        }
    }

    private String resolveStatusColumn(List<String> cells, Map<String, Integer> colIndex, String header, String dictType) {
        Integer idx = colIndex.get(header);
        if (idx == null) {
            return null;
        } else {
            String raw = this.blankToNull(this.cellAt(cells, idx));
            if (raw == null) {
                return null;
            } else {
                String value = raw.trim();
                Long hits = this.dictMapper
                    .selectCount(
                        ((Wrappers.<DictEntity>lambdaQuery().eq(DictEntity::getDictType, dictType))
                                .eq(DictEntity::getDictKey, value))
                            .eq(DictEntity::getStatus, 1)
                    );
                if (hits != null && hits != 0L) {
                    return value;
                } else {
                    throw BusinessException.badRequest("无效的" + header + "：" + value + "（须为数据字典启用项）");
                }
            }
        }
    }

    private LambdaQueryWrapper<AssetEntity> buildListWrapper(
        String keyword, String statusBiz, String categoryCode, String regionCode, String roomCode, String parentAssetNo
    ) {
        LambdaQueryWrapper<AssetEntity> qw = Wrappers.lambdaQuery();
        LoginUser who = com.dcim.security.CurrentUser.get();
        // v3.4：不能按隐藏字段筛选 / 搜索，否则可借此反推隐藏字段的值
        Map<String, Object> filters = new java.util.HashMap<>();
        filters.put("statusBiz", statusBiz);
        filters.put("categoryCode", categoryCode);
        filters.put("regionCode", regionCode);
        filters.put("roomCode", roomCode);
        filters.put("parentAssetNo", parentAssetNo);
        this.fieldPolicies.assertFilterAllowed(who, "assets", filters);
        boolean snVisible = !this.fieldPolicies.isHidden(who, "assets", "sn");
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> {
                w.like(AssetEntity::getAssetNo, kw).or().like(AssetEntity::getName, kw);
                if (snVisible) {
                    w.or().like(AssetEntity::getSn, kw);
                }
            });
        }

        if (statusBiz != null && !statusBiz.isBlank()) {
            qw.eq(AssetEntity::getStatusBiz, statusBiz);
        }

        if (categoryCode != null && !categoryCode.isBlank()) {
            qw.eq(AssetEntity::getCategoryCode, categoryCode);
        }

        if (regionCode != null && !regionCode.isBlank()) {
            qw.eq(AssetEntity::getRegionCode, regionCode);
        }

        if (roomCode != null && !roomCode.isBlank()) {
            qw.eq(AssetEntity::getRoomCode, roomCode);
        }

        if (parentAssetNo != null && !parentAssetNo.isBlank()) {
            String p = parentAssetNo.trim();
            if ("ROOT".equalsIgnoreCase(p)) {
                qw.isNull(AssetEntity::getParentAssetNo);
            } else if ("CHILD".equalsIgnoreCase(p)) {
                qw.isNotNull(AssetEntity::getParentAssetNo);
            } else {
                qw.eq(AssetEntity::getParentAssetNo, p);
            }
        }

        this.dataScope.applyAssetScope(qw, com.dcim.security.CurrentUser.get());
        (qw.orderByDesc(AssetEntity::getCreatedAt)).orderByDesc(AssetEntity::getAssetNo);
        return qw;
    }

    private void validateParent(String selfNo, String parentAssetNo) {
        if (parentAssetNo != null && !parentAssetNo.isBlank()) {
            if (!"__new__".equals(selfNo) && parentAssetNo.equals(selfNo)) {
                throw BusinessException.badRequest("父资产不能指向自身");
            } else {
                AssetEntity parent = (AssetEntity)this.assetMapper.selectById(parentAssetNo);
                if (parent == null) {
                    throw BusinessException.badRequest("父资产不存在：" + parentAssetNo);
                } else if (!"__new__".equals(selfNo)) {
                    String cursor = parentAssetNo;
                    int guard = 0;

                    while (cursor != null && guard++ < 64) {
                        if (cursor.equals(selfNo)) {
                            throw BusinessException.badRequest("父资产关系不能成环（" + selfNo + " 的祖先链回到自身）");
                        }

                        AssetEntity up = (AssetEntity)this.assetMapper.selectById(cursor);
                        if (up == null) {
                            break;
                        }

                        cursor = up.getParentAssetNo();
                    }
                }
            }
        }
    }

    private AssetEntity requireAsset(String assetNo) {
        AssetEntity a = (AssetEntity)this.assetMapper.selectById(assetNo);
        // 数据范围：不在本人可见范围内的资产按“不存在”处理
        if (a != null && !this.dataScope.canSeeAsset(com.dcim.security.CurrentUser.get(), a)) {
            a = null;
        }
        if (a == null) {
            throw BusinessException.notFound("资产不存在：" + assetNo);
        } else {
            return a;
        }
    }

    private void applyRefs(AssetEntity a, String categoryCode, String modelCode, String regionCode) {
        a.setCategoryCode(this.blankToNull(categoryCode));
        a.setModelCode(this.blankToNull(modelCode));
        a.setRegionCode(this.blankToNull(regionCode));
        this.validateRefs(a.getCategoryCode(), a.getModelCode(), a.getRegionCode());
    }

    private void validateRefs(String categoryCode, String modelCode, String regionCode) {
        if (categoryCode != null && this.categoryMapper.selectOne(Wrappers.<CategoryEntity>lambdaQuery().eq(CategoryEntity::getCategoryCode, categoryCode)) == null) {
            throw BusinessException.badRequest("分类不存在：" + categoryCode);
        } else if (regionCode != null
            && this.dictMapper
                    .selectCount(
                        (Wrappers.<DictEntity>lambdaQuery().eq(DictEntity::getDictType, "region")).eq(DictEntity::getDictKey, regionCode)
                    )
                == 0L) {
            throw BusinessException.badRequest("区域编码不在区域字典中：" + regionCode);
        }
    }

    private String normalizeSn(String sn) {
        return sn != null && !sn.isBlank() ? sn.trim().toUpperCase() : null;
    }

    private boolean snExists(String normalizedSn, String excludeAssetNo) {
        LambdaQueryWrapper<AssetEntity> qw = Wrappers.<AssetEntity>lambdaQuery().apply("UPPER(sn) = {0}", new Object[]{normalizedSn});
        if (excludeAssetNo != null) {
            qw.ne(AssetEntity::getAssetNo, excludeAssetNo);
        }

        return this.assetMapper.selectCount(qw) > 0L;
    }

    private String normalizeOwnerType(String ownerType) {
        if (ownerType != null && !ownerType.isBlank()) {
            String v = ownerType.trim().toUpperCase();
            if (!"SELF".equals(v) && !"LEASE".equals(v)) {
                throw BusinessException.badRequest("权属类型无效（仅支持 SELF/LEASE）：" + ownerType);
            } else {
                return v;
            }
        } else {
            return "SELF";
        }
    }

    private Map<String, Object> placementSnapshot(String rackCode, Integer unitNo, Integer uHeight) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("rackCode", rackCode);
        s.put("unitNo", unitNo);
        s.put("uHeight", uHeight);
        return s;
    }

    private void recordEvent(String assetNo, String eventType, String refDoc, Map<String, Object> snapshot) {
        AssetEventEntity e = new AssetEventEntity();
        e.setAssetNo(assetNo);
        e.setEventType(eventType);
        LoginUser u = CurrentUser.get();
        e.setOperator(u == null ? null : u.username());
        e.setRefDoc(refDoc);
        e.setSnapshot(snapshot);
        e.setEventTime(LocalDateTime.now());
        this.assetEventMapper.insert(e);
    }

    private Map<String, Object> toRow(AssetEntity a) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assetNo", a.getAssetNo());
        row.put("sn", a.getSn());
        row.put("name", a.getName());
        row.put("categoryCode", a.getCategoryCode());
        row.put("modelCode", a.getModelCode());
        row.put("ownerType", a.getOwnerType());
        row.put("statusBiz", a.getStatusBiz());
        row.put("statusFin", a.getStatusFin());
        row.put("regionCode", a.getRegionCode());
        row.put("roomCode", a.getRoomCode());
        row.put("rackCode", a.getRackCode());
        row.put("unitNo", a.getUnitNo());
        row.put("uHeight", a.getUHeight());
        row.put("department", a.getDepartment());
        row.put("owner", a.getOwner());
        row.put("mgmtIp", a.getMgmtIp());
        row.put("bizIp", a.getBizIp());
        row.put("purchaseDate", this.dateText(a.getPurchaseDate()));
        row.put("purchasePrice", a.getPurchasePrice());
        row.put("warrantyEnd", this.dateText(a.getWarrantyEnd()));
        row.put("rackDate", this.dateText(a.getRackDate()));
        row.put("contractNo", a.getContractNo());
        row.put("ratedPowerW", a.getRatedPowerW());
        row.put("parentAssetNo", a.getParentAssetNo());
        row.put("attrs", a.getAttrs());
        row.put("version", a.getVersion());
        row.put("createdAt", this.timeText(a.getCreatedAt()));
        row.put("updatedAt", this.timeText(a.getUpdatedAt()));
        return row;
    }

    private static Integer checkPower(Integer w) {
        if (w == null || w >= 0 && w <= 200000) {
            return w;
        } else {
            throw BusinessException.badRequest("额定功率须在 0-200000 W");
        }
    }

    private Map<String, Object> financeRow(AssetFinanceEntity f) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assetNo", f.getAssetNo());
        row.put("originValue", f.getOriginValue());
        row.put("residualRate", f.getResidualRate());
        row.put("lifeMonths", f.getLifeMonths());
        row.put("depMethod", f.getDepMethod());
        row.put("depStartDate", this.dateText(f.getDepStartDate()));
        row.put("accumulatedDep", f.getAccumulatedDep());
        row.put("netValue", f.getNetValue());
        row.put("impairment", f.getImpairment());
        row.put("leaseFlag", f.getLeaseFlag());
        row.put("updatedAt", this.timeText(f.getUpdatedAt()));
        return row;
    }

    private LocalDate parseDate(String s, String label) {
        if (s != null && !s.isBlank()) {
            try {
                return LocalDate.parse(s.trim());
            } catch (DateTimeParseException var4) {
                throw BusinessException.badRequest(label + "格式错误，应为 yyyy-MM-dd：" + s.trim());
            }
        } else {
            return null;
        }
    }

    private Integer parseInt(String s, String label) {
        if (s != null && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException var4) {
                throw BusinessException.badRequest(label + "应为整数：" + s.trim());
            }
        } else {
            return null;
        }
    }

    private BigDecimal parseDecimal(String s, String label) {
        if (s != null && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException var4) {
                throw BusinessException.badRequest(label + "应为数字：" + s.trim());
            }
        } else {
            return null;
        }
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private int col(Map<String, Integer> colIndex, String name, int fallback) {
        if (colIndex.isEmpty()) {
            return fallback;
        } else {
            Integer idx = colIndex.get(name);
            return idx == null ? -1 : idx;
        }
    }

    private String cellAt(List<String> cells, int idx) {
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : "";
    }

    private String dateText(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    private String timeText(LocalDateTime t) {
        return t == null ? "" : t.toString();
    }

    private void writeCsv(HttpServletResponse response, String fileName, String csv) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setStatus(200);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    public static class BatchNosReq {
        @NotEmpty(
            message = "请勾选资产"
        )
        private List<String> assetNos;

        
        public List<String> getAssetNos() {
            return this.assetNos;
        }

        
        public void setAssetNos(final List<String> assetNos) {
            this.assetNos = assetNos;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof AssetController.BatchNosReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$assetNos = this.getAssetNos();
                Object other$assetNos = other.getAssetNos();
                return this$assetNos == null ? other$assetNos == null : this$assetNos.equals(other$assetNos);
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof AssetController.BatchNosReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $assetNos = this.getAssetNos();
            return result * 59 + ($assetNos == null ? 43 : $assetNos.hashCode());
        }

        
        @Override
        public String toString() {
            return "AssetController.BatchNosReq(assetNos=" + this.getAssetNos() + ")";
        }
    }

    public static class BatchTransitionReq {
        @NotEmpty(
            message = "请勾选资产"
        )
        private List<String> assetNos;
        private String targetBiz;
        private String targetFin;
        private String reason;
        private String refDoc;

        
        public List<String> getAssetNos() {
            return this.assetNos;
        }

        
        public String getTargetBiz() {
            return this.targetBiz;
        }

        
        public String getTargetFin() {
            return this.targetFin;
        }

        
        public String getReason() {
            return this.reason;
        }

        
        public String getRefDoc() {
            return this.refDoc;
        }

        
        public void setAssetNos(final List<String> assetNos) {
            this.assetNos = assetNos;
        }

        
        public void setTargetBiz(final String targetBiz) {
            this.targetBiz = targetBiz;
        }

        
        public void setTargetFin(final String targetFin) {
            this.targetFin = targetFin;
        }

        
        public void setReason(final String reason) {
            this.reason = reason;
        }

        
        public void setRefDoc(final String refDoc) {
            this.refDoc = refDoc;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof AssetController.BatchTransitionReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$assetNos = this.getAssetNos();
                Object other$assetNos = other.getAssetNos();
                if (this$assetNos == null ? other$assetNos == null : this$assetNos.equals(other$assetNos)) {
                    Object this$targetBiz = this.getTargetBiz();
                    Object other$targetBiz = other.getTargetBiz();
                    if (this$targetBiz == null ? other$targetBiz == null : this$targetBiz.equals(other$targetBiz)) {
                        Object this$targetFin = this.getTargetFin();
                        Object other$targetFin = other.getTargetFin();
                        if (this$targetFin == null ? other$targetFin == null : this$targetFin.equals(other$targetFin)) {
                            Object this$reason = this.getReason();
                            Object other$reason = other.getReason();
                            if (this$reason == null ? other$reason == null : this$reason.equals(other$reason)) {
                                Object this$refDoc = this.getRefDoc();
                                Object other$refDoc = other.getRefDoc();
                                return this$refDoc == null ? other$refDoc == null : this$refDoc.equals(other$refDoc);
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof AssetController.BatchTransitionReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $assetNos = this.getAssetNos();
            result = result * 59 + ($assetNos == null ? 43 : $assetNos.hashCode());
            Object $targetBiz = this.getTargetBiz();
            result = result * 59 + ($targetBiz == null ? 43 : $targetBiz.hashCode());
            Object $targetFin = this.getTargetFin();
            result = result * 59 + ($targetFin == null ? 43 : $targetFin.hashCode());
            Object $reason = this.getReason();
            result = result * 59 + ($reason == null ? 43 : $reason.hashCode());
            Object $refDoc = this.getRefDoc();
            return result * 59 + ($refDoc == null ? 43 : $refDoc.hashCode());
        }

        
        @Override
        public String toString() {
            return "AssetController.BatchTransitionReq(assetNos="
                + this.getAssetNos()
                + ", targetBiz="
                + this.getTargetBiz()
                + ", targetFin="
                + this.getTargetFin()
                + ", reason="
                + this.getReason()
                + ", refDoc="
                + this.getRefDoc()
                + ")";
        }
    }

    public static class BatchUpdateReq {
        @NotEmpty(
            message = "请勾选资产"
        )
        private List<String> assetNos;
        private String categoryCode;
        private String modelCode;
        private String regionCode;
        private String ownerType;
        private String department;
        private String owner;
        private String contractNo;

        
        public List<String> getAssetNos() {
            return this.assetNos;
        }

        
        public String getCategoryCode() {
            return this.categoryCode;
        }

        
        public String getModelCode() {
            return this.modelCode;
        }

        
        public String getRegionCode() {
            return this.regionCode;
        }

        
        public String getOwnerType() {
            return this.ownerType;
        }

        
        public String getDepartment() {
            return this.department;
        }

        
        public String getOwner() {
            return this.owner;
        }

        
        public String getContractNo() {
            return this.contractNo;
        }

        
        public void setAssetNos(final List<String> assetNos) {
            this.assetNos = assetNos;
        }

        
        public void setCategoryCode(final String categoryCode) {
            this.categoryCode = categoryCode;
        }

        
        public void setModelCode(final String modelCode) {
            this.modelCode = modelCode;
        }

        
        public void setRegionCode(final String regionCode) {
            this.regionCode = regionCode;
        }

        
        public void setOwnerType(final String ownerType) {
            this.ownerType = ownerType;
        }

        
        public void setDepartment(final String department) {
            this.department = department;
        }

        
        public void setOwner(final String owner) {
            this.owner = owner;
        }

        
        public void setContractNo(final String contractNo) {
            this.contractNo = contractNo;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof AssetController.BatchUpdateReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$assetNos = this.getAssetNos();
                Object other$assetNos = other.getAssetNos();
                if (this$assetNos == null ? other$assetNos == null : this$assetNos.equals(other$assetNos)) {
                    Object this$categoryCode = this.getCategoryCode();
                    Object other$categoryCode = other.getCategoryCode();
                    if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                        Object this$modelCode = this.getModelCode();
                        Object other$modelCode = other.getModelCode();
                        if (this$modelCode == null ? other$modelCode == null : this$modelCode.equals(other$modelCode)) {
                            Object this$regionCode = this.getRegionCode();
                            Object other$regionCode = other.getRegionCode();
                            if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                Object this$ownerType = this.getOwnerType();
                                Object other$ownerType = other.getOwnerType();
                                if (this$ownerType == null ? other$ownerType == null : this$ownerType.equals(other$ownerType)) {
                                    Object this$department = this.getDepartment();
                                    Object other$department = other.getDepartment();
                                    if (this$department == null ? other$department == null : this$department.equals(other$department)) {
                                        Object this$owner = this.getOwner();
                                        Object other$owner = other.getOwner();
                                        if (this$owner == null ? other$owner == null : this$owner.equals(other$owner)) {
                                            Object this$contractNo = this.getContractNo();
                                            Object other$contractNo = other.getContractNo();
                                            return this$contractNo == null ? other$contractNo == null : this$contractNo.equals(other$contractNo);
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof AssetController.BatchUpdateReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $assetNos = this.getAssetNos();
            result = result * 59 + ($assetNos == null ? 43 : $assetNos.hashCode());
            Object $categoryCode = this.getCategoryCode();
            result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
            Object $modelCode = this.getModelCode();
            result = result * 59 + ($modelCode == null ? 43 : $modelCode.hashCode());
            Object $regionCode = this.getRegionCode();
            result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
            Object $ownerType = this.getOwnerType();
            result = result * 59 + ($ownerType == null ? 43 : $ownerType.hashCode());
            Object $department = this.getDepartment();
            result = result * 59 + ($department == null ? 43 : $department.hashCode());
            Object $owner = this.getOwner();
            result = result * 59 + ($owner == null ? 43 : $owner.hashCode());
            Object $contractNo = this.getContractNo();
            return result * 59 + ($contractNo == null ? 43 : $contractNo.hashCode());
        }

        
        @Override
        public String toString() {
            return "AssetController.BatchUpdateReq(assetNos="
                + this.getAssetNos()
                + ", categoryCode="
                + this.getCategoryCode()
                + ", modelCode="
                + this.getModelCode()
                + ", regionCode="
                + this.getRegionCode()
                + ", ownerType="
                + this.getOwnerType()
                + ", department="
                + this.getDepartment()
                + ", owner="
                + this.getOwner()
                + ", contractNo="
                + this.getContractNo()
                + ")";
        }
    }

    public static class CreateAssetReq {
        @NotBlank(
            message = "资产名称不能为空"
        )
        private String name;
        private String sn;
        private String categoryCode;
        private String modelCode;
        private String ownerType;
        private String regionCode;
        private String roomCode;
        private String rackCode;
        private Integer unitNo;
        @JsonProperty("uHeight")
        private Integer uHeight;
        private String department;
        private String owner;
        private String mgmtIp;
        private String bizIp;
        private String purchaseDate;
        private BigDecimal purchasePrice;
        private String warrantyEnd;
        private String rackDate;
        private String contractNo;
        private Integer ratedPowerW;
        private String parentAssetNo;
        private Map<String, Object> attrs;

        
        public String getName() {
            return this.name;
        }

        
        public String getSn() {
            return this.sn;
        }

        
        public String getCategoryCode() {
            return this.categoryCode;
        }

        
        public String getModelCode() {
            return this.modelCode;
        }

        
        public String getOwnerType() {
            return this.ownerType;
        }

        
        public String getRegionCode() {
            return this.regionCode;
        }

        
        public String getRoomCode() {
            return this.roomCode;
        }

        
        public String getRackCode() {
            return this.rackCode;
        }

        
        public Integer getUnitNo() {
            return this.unitNo;
        }

        
        public Integer getUHeight() {
            return this.uHeight;
        }

        
        public String getDepartment() {
            return this.department;
        }

        
        public String getOwner() {
            return this.owner;
        }

        
        public String getMgmtIp() {
            return this.mgmtIp;
        }

        
        public String getBizIp() {
            return this.bizIp;
        }

        
        public String getPurchaseDate() {
            return this.purchaseDate;
        }

        
        public BigDecimal getPurchasePrice() {
            return this.purchasePrice;
        }

        
        public String getWarrantyEnd() {
            return this.warrantyEnd;
        }

        
        public String getRackDate() {
            return this.rackDate;
        }

        
        public String getContractNo() {
            return this.contractNo;
        }

        
        public Integer getRatedPowerW() {
            return this.ratedPowerW;
        }

        
        public String getParentAssetNo() {
            return this.parentAssetNo;
        }

        
        public Map<String, Object> getAttrs() {
            return this.attrs;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setSn(final String sn) {
            this.sn = sn;
        }

        
        public void setCategoryCode(final String categoryCode) {
            this.categoryCode = categoryCode;
        }

        
        public void setModelCode(final String modelCode) {
            this.modelCode = modelCode;
        }

        
        public void setOwnerType(final String ownerType) {
            this.ownerType = ownerType;
        }

        
        public void setRegionCode(final String regionCode) {
            this.regionCode = regionCode;
        }

        
        public void setRoomCode(final String roomCode) {
            this.roomCode = roomCode;
        }

        
        public void setRackCode(final String rackCode) {
            this.rackCode = rackCode;
        }

        
        public void setUnitNo(final Integer unitNo) {
            this.unitNo = unitNo;
        }

        @JsonProperty("uHeight")
        
        public void setUHeight(final Integer uHeight) {
            this.uHeight = uHeight;
        }

        
        public void setDepartment(final String department) {
            this.department = department;
        }

        
        public void setOwner(final String owner) {
            this.owner = owner;
        }

        
        public void setMgmtIp(final String mgmtIp) {
            this.mgmtIp = mgmtIp;
        }

        
        public void setBizIp(final String bizIp) {
            this.bizIp = bizIp;
        }

        
        public void setPurchaseDate(final String purchaseDate) {
            this.purchaseDate = purchaseDate;
        }

        
        public void setPurchasePrice(final BigDecimal purchasePrice) {
            this.purchasePrice = purchasePrice;
        }

        
        public void setWarrantyEnd(final String warrantyEnd) {
            this.warrantyEnd = warrantyEnd;
        }

        
        public void setRackDate(final String rackDate) {
            this.rackDate = rackDate;
        }

        
        public void setContractNo(final String contractNo) {
            this.contractNo = contractNo;
        }

        
        public void setRatedPowerW(final Integer ratedPowerW) {
            this.ratedPowerW = ratedPowerW;
        }

        
        public void setParentAssetNo(final String parentAssetNo) {
            this.parentAssetNo = parentAssetNo;
        }

        
        public void setAttrs(final Map<String, Object> attrs) {
            this.attrs = attrs;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof AssetController.CreateAssetReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$unitNo = this.getUnitNo();
                Object other$unitNo = other.getUnitNo();
                if (this$unitNo == null ? other$unitNo == null : this$unitNo.equals(other$unitNo)) {
                    Object this$uHeight = this.getUHeight();
                    Object other$uHeight = other.getUHeight();
                    if (this$uHeight == null ? other$uHeight == null : this$uHeight.equals(other$uHeight)) {
                        Object this$ratedPowerW = this.getRatedPowerW();
                        Object other$ratedPowerW = other.getRatedPowerW();
                        if (this$ratedPowerW == null ? other$ratedPowerW == null : this$ratedPowerW.equals(other$ratedPowerW)) {
                            Object this$name = this.getName();
                            Object other$name = other.getName();
                            if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                Object this$sn = this.getSn();
                                Object other$sn = other.getSn();
                                if (this$sn == null ? other$sn == null : this$sn.equals(other$sn)) {
                                    Object this$categoryCode = this.getCategoryCode();
                                    Object other$categoryCode = other.getCategoryCode();
                                    if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                                        Object this$modelCode = this.getModelCode();
                                        Object other$modelCode = other.getModelCode();
                                        if (this$modelCode == null ? other$modelCode == null : this$modelCode.equals(other$modelCode)) {
                                            Object this$ownerType = this.getOwnerType();
                                            Object other$ownerType = other.getOwnerType();
                                            if (this$ownerType == null ? other$ownerType == null : this$ownerType.equals(other$ownerType)) {
                                                Object this$regionCode = this.getRegionCode();
                                                Object other$regionCode = other.getRegionCode();
                                                if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                                    Object this$roomCode = this.getRoomCode();
                                                    Object other$roomCode = other.getRoomCode();
                                                    if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                                        Object this$rackCode = this.getRackCode();
                                                        Object other$rackCode = other.getRackCode();
                                                        if (this$rackCode == null ? other$rackCode == null : this$rackCode.equals(other$rackCode)) {
                                                            Object this$department = this.getDepartment();
                                                            Object other$department = other.getDepartment();
                                                            if (this$department == null ? other$department == null : this$department.equals(other$department)) {
                                                                Object this$owner = this.getOwner();
                                                                Object other$owner = other.getOwner();
                                                                if (this$owner == null ? other$owner == null : this$owner.equals(other$owner)) {
                                                                    Object this$mgmtIp = this.getMgmtIp();
                                                                    Object other$mgmtIp = other.getMgmtIp();
                                                                    if (this$mgmtIp == null ? other$mgmtIp == null : this$mgmtIp.equals(other$mgmtIp)) {
                                                                        Object this$bizIp = this.getBizIp();
                                                                        Object other$bizIp = other.getBizIp();
                                                                        if (this$bizIp == null ? other$bizIp == null : this$bizIp.equals(other$bizIp)) {
                                                                            Object this$purchaseDate = this.getPurchaseDate();
                                                                            Object other$purchaseDate = other.getPurchaseDate();
                                                                            if (this$purchaseDate == null
                                                                                ? other$purchaseDate == null
                                                                                : this$purchaseDate.equals(other$purchaseDate)) {
                                                                                Object this$purchasePrice = this.getPurchasePrice();
                                                                                Object other$purchasePrice = other.getPurchasePrice();
                                                                                if (this$purchasePrice == null
                                                                                    ? other$purchasePrice == null
                                                                                    : this$purchasePrice.equals(other$purchasePrice)) {
                                                                                    Object this$warrantyEnd = this.getWarrantyEnd();
                                                                                    Object other$warrantyEnd = other.getWarrantyEnd();
                                                                                    if (this$warrantyEnd == null
                                                                                        ? other$warrantyEnd == null
                                                                                        : this$warrantyEnd.equals(other$warrantyEnd)) {
                                                                                        Object this$rackDate = this.getRackDate();
                                                                                        Object other$rackDate = other.getRackDate();
                                                                                        if (this$rackDate == null
                                                                                            ? other$rackDate == null
                                                                                            : this$rackDate.equals(other$rackDate)) {
                                                                                            Object this$contractNo = this.getContractNo();
                                                                                            Object other$contractNo = other.getContractNo();
                                                                                            if (this$contractNo == null
                                                                                                ? other$contractNo == null
                                                                                                : this$contractNo.equals(other$contractNo)) {
                                                                                                Object this$parentAssetNo = this.getParentAssetNo();
                                                                                                Object other$parentAssetNo = other.getParentAssetNo();
                                                                                                if (this$parentAssetNo == null
                                                                                                    ? other$parentAssetNo == null
                                                                                                    : this$parentAssetNo.equals(other$parentAssetNo)) {
                                                                                                    Object this$attrs = this.getAttrs();
                                                                                                    Object other$attrs = other.getAttrs();
                                                                                                    return this$attrs == null
                                                                                                        ? other$attrs == null
                                                                                                        : this$attrs.equals(other$attrs);
                                                                                                } else {
                                                                                                    return false;
                                                                                                }
                                                                                            } else {
                                                                                                return false;
                                                                                            }
                                                                                        } else {
                                                                                            return false;
                                                                                        }
                                                                                    } else {
                                                                                        return false;
                                                                                    }
                                                                                } else {
                                                                                    return false;
                                                                                }
                                                                            } else {
                                                                                return false;
                                                                            }
                                                                        } else {
                                                                            return false;
                                                                        }
                                                                    } else {
                                                                        return false;
                                                                    }
                                                                } else {
                                                                    return false;
                                                                }
                                                            } else {
                                                                return false;
                                                            }
                                                        } else {
                                                            return false;
                                                        }
                                                    } else {
                                                        return false;
                                                    }
                                                } else {
                                                    return false;
                                                }
                                            } else {
                                                return false;
                                            }
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof AssetController.CreateAssetReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $unitNo = this.getUnitNo();
            result = result * 59 + ($unitNo == null ? 43 : $unitNo.hashCode());
            Object $uHeight = this.getUHeight();
            result = result * 59 + ($uHeight == null ? 43 : $uHeight.hashCode());
            Object $ratedPowerW = this.getRatedPowerW();
            result = result * 59 + ($ratedPowerW == null ? 43 : $ratedPowerW.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $sn = this.getSn();
            result = result * 59 + ($sn == null ? 43 : $sn.hashCode());
            Object $categoryCode = this.getCategoryCode();
            result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
            Object $modelCode = this.getModelCode();
            result = result * 59 + ($modelCode == null ? 43 : $modelCode.hashCode());
            Object $ownerType = this.getOwnerType();
            result = result * 59 + ($ownerType == null ? 43 : $ownerType.hashCode());
            Object $regionCode = this.getRegionCode();
            result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
            Object $roomCode = this.getRoomCode();
            result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
            Object $rackCode = this.getRackCode();
            result = result * 59 + ($rackCode == null ? 43 : $rackCode.hashCode());
            Object $department = this.getDepartment();
            result = result * 59 + ($department == null ? 43 : $department.hashCode());
            Object $owner = this.getOwner();
            result = result * 59 + ($owner == null ? 43 : $owner.hashCode());
            Object $mgmtIp = this.getMgmtIp();
            result = result * 59 + ($mgmtIp == null ? 43 : $mgmtIp.hashCode());
            Object $bizIp = this.getBizIp();
            result = result * 59 + ($bizIp == null ? 43 : $bizIp.hashCode());
            Object $purchaseDate = this.getPurchaseDate();
            result = result * 59 + ($purchaseDate == null ? 43 : $purchaseDate.hashCode());
            Object $purchasePrice = this.getPurchasePrice();
            result = result * 59 + ($purchasePrice == null ? 43 : $purchasePrice.hashCode());
            Object $warrantyEnd = this.getWarrantyEnd();
            result = result * 59 + ($warrantyEnd == null ? 43 : $warrantyEnd.hashCode());
            Object $rackDate = this.getRackDate();
            result = result * 59 + ($rackDate == null ? 43 : $rackDate.hashCode());
            Object $contractNo = this.getContractNo();
            result = result * 59 + ($contractNo == null ? 43 : $contractNo.hashCode());
            Object $parentAssetNo = this.getParentAssetNo();
            result = result * 59 + ($parentAssetNo == null ? 43 : $parentAssetNo.hashCode());
            Object $attrs = this.getAttrs();
            return result * 59 + ($attrs == null ? 43 : $attrs.hashCode());
        }

        
        @Override
        public String toString() {
            return "AssetController.CreateAssetReq(name="
                + this.getName()
                + ", sn="
                + this.getSn()
                + ", categoryCode="
                + this.getCategoryCode()
                + ", modelCode="
                + this.getModelCode()
                + ", ownerType="
                + this.getOwnerType()
                + ", regionCode="
                + this.getRegionCode()
                + ", roomCode="
                + this.getRoomCode()
                + ", rackCode="
                + this.getRackCode()
                + ", unitNo="
                + this.getUnitNo()
                + ", uHeight="
                + this.getUHeight()
                + ", department="
                + this.getDepartment()
                + ", owner="
                + this.getOwner()
                + ", mgmtIp="
                + this.getMgmtIp()
                + ", bizIp="
                + this.getBizIp()
                + ", purchaseDate="
                + this.getPurchaseDate()
                + ", purchasePrice="
                + this.getPurchasePrice()
                + ", warrantyEnd="
                + this.getWarrantyEnd()
                + ", rackDate="
                + this.getRackDate()
                + ", contractNo="
                + this.getContractNo()
                + ", ratedPowerW="
                + this.getRatedPowerW()
                + ", parentAssetNo="
                + this.getParentAssetNo()
                + ", attrs="
                + this.getAttrs()
                + ")";
        }
    }

    public static record LookupVO(
        String assetNo,
        String sn,
        String name,
        String statusBiz,
        String statusFin,
        String roomCode,
        String rackCode,
        Integer unitNo,
        String owner,
        String categoryCode,
        String modelCode
    ) {
        static AssetController.LookupVO of(AssetEntity a) {
            return new AssetController.LookupVO(
                a.getAssetNo(),
                a.getSn(),
                a.getName(),
                a.getStatusBiz(),
                a.getStatusFin(),
                a.getRoomCode(),
                a.getRackCode(),
                a.getUnitNo(),
                a.getOwner(),
                a.getCategoryCode(),
                a.getModelCode()
            );
        }
    }

    public static class TransitionReq {
        private String targetBiz;
        private String targetFin;
        private String reason;
        private String refDoc;
        private Boolean cascadeChildren;

        
        public String getTargetBiz() {
            return this.targetBiz;
        }

        
        public String getTargetFin() {
            return this.targetFin;
        }

        
        public String getReason() {
            return this.reason;
        }

        
        public String getRefDoc() {
            return this.refDoc;
        }

        
        public Boolean getCascadeChildren() {
            return this.cascadeChildren;
        }

        
        public void setTargetBiz(final String targetBiz) {
            this.targetBiz = targetBiz;
        }

        
        public void setTargetFin(final String targetFin) {
            this.targetFin = targetFin;
        }

        
        public void setReason(final String reason) {
            this.reason = reason;
        }

        
        public void setRefDoc(final String refDoc) {
            this.refDoc = refDoc;
        }

        
        public void setCascadeChildren(final Boolean cascadeChildren) {
            this.cascadeChildren = cascadeChildren;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof AssetController.TransitionReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$cascadeChildren = this.getCascadeChildren();
                Object other$cascadeChildren = other.getCascadeChildren();
                if (this$cascadeChildren == null ? other$cascadeChildren == null : this$cascadeChildren.equals(other$cascadeChildren)) {
                    Object this$targetBiz = this.getTargetBiz();
                    Object other$targetBiz = other.getTargetBiz();
                    if (this$targetBiz == null ? other$targetBiz == null : this$targetBiz.equals(other$targetBiz)) {
                        Object this$targetFin = this.getTargetFin();
                        Object other$targetFin = other.getTargetFin();
                        if (this$targetFin == null ? other$targetFin == null : this$targetFin.equals(other$targetFin)) {
                            Object this$reason = this.getReason();
                            Object other$reason = other.getReason();
                            if (this$reason == null ? other$reason == null : this$reason.equals(other$reason)) {
                                Object this$refDoc = this.getRefDoc();
                                Object other$refDoc = other.getRefDoc();
                                return this$refDoc == null ? other$refDoc == null : this$refDoc.equals(other$refDoc);
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof AssetController.TransitionReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $cascadeChildren = this.getCascadeChildren();
            result = result * 59 + ($cascadeChildren == null ? 43 : $cascadeChildren.hashCode());
            Object $targetBiz = this.getTargetBiz();
            result = result * 59 + ($targetBiz == null ? 43 : $targetBiz.hashCode());
            Object $targetFin = this.getTargetFin();
            result = result * 59 + ($targetFin == null ? 43 : $targetFin.hashCode());
            Object $reason = this.getReason();
            result = result * 59 + ($reason == null ? 43 : $reason.hashCode());
            Object $refDoc = this.getRefDoc();
            return result * 59 + ($refDoc == null ? 43 : $refDoc.hashCode());
        }

        
        @Override
        public String toString() {
            return "AssetController.TransitionReq(targetBiz="
                + this.getTargetBiz()
                + ", targetFin="
                + this.getTargetFin()
                + ", reason="
                + this.getReason()
                + ", refDoc="
                + this.getRefDoc()
                + ", cascadeChildren="
                + this.getCascadeChildren()
                + ")";
        }
    }

    public static class UpdateAssetReq {
        private Integer version;
        private String name;
        private String sn;
        private String categoryCode;
        private String modelCode;
        private String ownerType;
        private String statusBiz;
        private String statusFin;
        private String regionCode;
        private String roomCode;
        private String rackCode;
        private Integer unitNo;
        @JsonProperty("uHeight")
        private Integer uHeight;
        private String department;
        private String owner;
        private String mgmtIp;
        private String bizIp;
        private String purchaseDate;
        private BigDecimal purchasePrice;
        private String warrantyEnd;
        private String rackDate;
        private String contractNo;
        private Integer ratedPowerW;
        private String parentAssetNo;
        private Map<String, Object> attrs;

        
        public Integer getVersion() {
            return this.version;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getSn() {
            return this.sn;
        }

        
        public String getCategoryCode() {
            return this.categoryCode;
        }

        
        public String getModelCode() {
            return this.modelCode;
        }

        
        public String getOwnerType() {
            return this.ownerType;
        }

        
        public String getStatusBiz() {
            return this.statusBiz;
        }

        
        public String getStatusFin() {
            return this.statusFin;
        }

        
        public String getRegionCode() {
            return this.regionCode;
        }

        
        public String getRoomCode() {
            return this.roomCode;
        }

        
        public String getRackCode() {
            return this.rackCode;
        }

        
        public Integer getUnitNo() {
            return this.unitNo;
        }

        
        public Integer getUHeight() {
            return this.uHeight;
        }

        
        public String getDepartment() {
            return this.department;
        }

        
        public String getOwner() {
            return this.owner;
        }

        
        public String getMgmtIp() {
            return this.mgmtIp;
        }

        
        public String getBizIp() {
            return this.bizIp;
        }

        
        public String getPurchaseDate() {
            return this.purchaseDate;
        }

        
        public BigDecimal getPurchasePrice() {
            return this.purchasePrice;
        }

        
        public String getWarrantyEnd() {
            return this.warrantyEnd;
        }

        
        public String getRackDate() {
            return this.rackDate;
        }

        
        public String getContractNo() {
            return this.contractNo;
        }

        
        public Integer getRatedPowerW() {
            return this.ratedPowerW;
        }

        
        public String getParentAssetNo() {
            return this.parentAssetNo;
        }

        
        public Map<String, Object> getAttrs() {
            return this.attrs;
        }

        
        public void setVersion(final Integer version) {
            this.version = version;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setSn(final String sn) {
            this.sn = sn;
        }

        
        public void setCategoryCode(final String categoryCode) {
            this.categoryCode = categoryCode;
        }

        
        public void setModelCode(final String modelCode) {
            this.modelCode = modelCode;
        }

        
        public void setOwnerType(final String ownerType) {
            this.ownerType = ownerType;
        }

        
        public void setStatusBiz(final String statusBiz) {
            this.statusBiz = statusBiz;
        }

        
        public void setStatusFin(final String statusFin) {
            this.statusFin = statusFin;
        }

        
        public void setRegionCode(final String regionCode) {
            this.regionCode = regionCode;
        }

        
        public void setRoomCode(final String roomCode) {
            this.roomCode = roomCode;
        }

        
        public void setRackCode(final String rackCode) {
            this.rackCode = rackCode;
        }

        
        public void setUnitNo(final Integer unitNo) {
            this.unitNo = unitNo;
        }

        @JsonProperty("uHeight")
        
        public void setUHeight(final Integer uHeight) {
            this.uHeight = uHeight;
        }

        
        public void setDepartment(final String department) {
            this.department = department;
        }

        
        public void setOwner(final String owner) {
            this.owner = owner;
        }

        
        public void setMgmtIp(final String mgmtIp) {
            this.mgmtIp = mgmtIp;
        }

        
        public void setBizIp(final String bizIp) {
            this.bizIp = bizIp;
        }

        
        public void setPurchaseDate(final String purchaseDate) {
            this.purchaseDate = purchaseDate;
        }

        
        public void setPurchasePrice(final BigDecimal purchasePrice) {
            this.purchasePrice = purchasePrice;
        }

        
        public void setWarrantyEnd(final String warrantyEnd) {
            this.warrantyEnd = warrantyEnd;
        }

        
        public void setRackDate(final String rackDate) {
            this.rackDate = rackDate;
        }

        
        public void setContractNo(final String contractNo) {
            this.contractNo = contractNo;
        }

        
        public void setRatedPowerW(final Integer ratedPowerW) {
            this.ratedPowerW = ratedPowerW;
        }

        
        public void setParentAssetNo(final String parentAssetNo) {
            this.parentAssetNo = parentAssetNo;
        }

        
        public void setAttrs(final Map<String, Object> attrs) {
            this.attrs = attrs;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof AssetController.UpdateAssetReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$version = this.getVersion();
                Object other$version = other.getVersion();
                if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                    Object this$unitNo = this.getUnitNo();
                    Object other$unitNo = other.getUnitNo();
                    if (this$unitNo == null ? other$unitNo == null : this$unitNo.equals(other$unitNo)) {
                        Object this$uHeight = this.getUHeight();
                        Object other$uHeight = other.getUHeight();
                        if (this$uHeight == null ? other$uHeight == null : this$uHeight.equals(other$uHeight)) {
                            Object this$ratedPowerW = this.getRatedPowerW();
                            Object other$ratedPowerW = other.getRatedPowerW();
                            if (this$ratedPowerW == null ? other$ratedPowerW == null : this$ratedPowerW.equals(other$ratedPowerW)) {
                                Object this$name = this.getName();
                                Object other$name = other.getName();
                                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                                    Object this$sn = this.getSn();
                                    Object other$sn = other.getSn();
                                    if (this$sn == null ? other$sn == null : this$sn.equals(other$sn)) {
                                        Object this$categoryCode = this.getCategoryCode();
                                        Object other$categoryCode = other.getCategoryCode();
                                        if (this$categoryCode == null ? other$categoryCode == null : this$categoryCode.equals(other$categoryCode)) {
                                            Object this$modelCode = this.getModelCode();
                                            Object other$modelCode = other.getModelCode();
                                            if (this$modelCode == null ? other$modelCode == null : this$modelCode.equals(other$modelCode)) {
                                                Object this$ownerType = this.getOwnerType();
                                                Object other$ownerType = other.getOwnerType();
                                                if (this$ownerType == null ? other$ownerType == null : this$ownerType.equals(other$ownerType)) {
                                                    Object this$statusBiz = this.getStatusBiz();
                                                    Object other$statusBiz = other.getStatusBiz();
                                                    if (this$statusBiz == null ? other$statusBiz == null : this$statusBiz.equals(other$statusBiz)) {
                                                        Object this$statusFin = this.getStatusFin();
                                                        Object other$statusFin = other.getStatusFin();
                                                        if (this$statusFin == null ? other$statusFin == null : this$statusFin.equals(other$statusFin)) {
                                                            Object this$regionCode = this.getRegionCode();
                                                            Object other$regionCode = other.getRegionCode();
                                                            if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                                                Object this$roomCode = this.getRoomCode();
                                                                Object other$roomCode = other.getRoomCode();
                                                                if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                                                    Object this$rackCode = this.getRackCode();
                                                                    Object other$rackCode = other.getRackCode();
                                                                    if (this$rackCode == null ? other$rackCode == null : this$rackCode.equals(other$rackCode)) {
                                                                        Object this$department = this.getDepartment();
                                                                        Object other$department = other.getDepartment();
                                                                        if (this$department == null
                                                                            ? other$department == null
                                                                            : this$department.equals(other$department)) {
                                                                            Object this$owner = this.getOwner();
                                                                            Object other$owner = other.getOwner();
                                                                            if (this$owner == null ? other$owner == null : this$owner.equals(other$owner)) {
                                                                                Object this$mgmtIp = this.getMgmtIp();
                                                                                Object other$mgmtIp = other.getMgmtIp();
                                                                                if (this$mgmtIp == null
                                                                                    ? other$mgmtIp == null
                                                                                    : this$mgmtIp.equals(other$mgmtIp)) {
                                                                                    Object this$bizIp = this.getBizIp();
                                                                                    Object other$bizIp = other.getBizIp();
                                                                                    if (this$bizIp == null
                                                                                        ? other$bizIp == null
                                                                                        : this$bizIp.equals(other$bizIp)) {
                                                                                        Object this$purchaseDate = this.getPurchaseDate();
                                                                                        Object other$purchaseDate = other.getPurchaseDate();
                                                                                        if (this$purchaseDate == null
                                                                                            ? other$purchaseDate == null
                                                                                            : this$purchaseDate.equals(other$purchaseDate)) {
                                                                                            Object this$purchasePrice = this.getPurchasePrice();
                                                                                            Object other$purchasePrice = other.getPurchasePrice();
                                                                                            if (this$purchasePrice == null
                                                                                                ? other$purchasePrice == null
                                                                                                : this$purchasePrice.equals(other$purchasePrice)) {
                                                                                                Object this$warrantyEnd = this.getWarrantyEnd();
                                                                                                Object other$warrantyEnd = other.getWarrantyEnd();
                                                                                                if (this$warrantyEnd == null
                                                                                                    ? other$warrantyEnd == null
                                                                                                    : this$warrantyEnd.equals(other$warrantyEnd)) {
                                                                                                    Object this$rackDate = this.getRackDate();
                                                                                                    Object other$rackDate = other.getRackDate();
                                                                                                    if (this$rackDate == null
                                                                                                        ? other$rackDate == null
                                                                                                        : this$rackDate.equals(other$rackDate)) {
                                                                                                        Object this$contractNo = this.getContractNo();
                                                                                                        Object other$contractNo = other.getContractNo();
                                                                                                        if (this$contractNo == null
                                                                                                            ? other$contractNo == null
                                                                                                            : this$contractNo.equals(other$contractNo)) {
                                                                                                            Object this$parentAssetNo = this.getParentAssetNo();
                                                                                                            Object other$parentAssetNo = other.getParentAssetNo();
                                                                                                            if (this$parentAssetNo == null
                                                                                                                ? other$parentAssetNo == null
                                                                                                                : this$parentAssetNo.equals(other$parentAssetNo)
                                                                                                                )
                                                                                                             {
                                                                                                                Object this$attrs = this.getAttrs();
                                                                                                                Object other$attrs = other.getAttrs();
                                                                                                                return this$attrs == null
                                                                                                                    ? other$attrs == null
                                                                                                                    : this$attrs.equals(other$attrs);
                                                                                                            } else {
                                                                                                                return false;
                                                                                                            }
                                                                                                        } else {
                                                                                                            return false;
                                                                                                        }
                                                                                                    } else {
                                                                                                        return false;
                                                                                                    }
                                                                                                } else {
                                                                                                    return false;
                                                                                                }
                                                                                            } else {
                                                                                                return false;
                                                                                            }
                                                                                        } else {
                                                                                            return false;
                                                                                        }
                                                                                    } else {
                                                                                        return false;
                                                                                    }
                                                                                } else {
                                                                                    return false;
                                                                                }
                                                                            } else {
                                                                                return false;
                                                                            }
                                                                        } else {
                                                                            return false;
                                                                        }
                                                                    } else {
                                                                        return false;
                                                                    }
                                                                } else {
                                                                    return false;
                                                                }
                                                            } else {
                                                                return false;
                                                            }
                                                        } else {
                                                            return false;
                                                        }
                                                    } else {
                                                        return false;
                                                    }
                                                } else {
                                                    return false;
                                                }
                                            } else {
                                                return false;
                                            }
                                        } else {
                                            return false;
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    return false;
                                }
                            } else {
                                return false;
                            }
                        } else {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof AssetController.UpdateAssetReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $version = this.getVersion();
            result = result * 59 + ($version == null ? 43 : $version.hashCode());
            Object $unitNo = this.getUnitNo();
            result = result * 59 + ($unitNo == null ? 43 : $unitNo.hashCode());
            Object $uHeight = this.getUHeight();
            result = result * 59 + ($uHeight == null ? 43 : $uHeight.hashCode());
            Object $ratedPowerW = this.getRatedPowerW();
            result = result * 59 + ($ratedPowerW == null ? 43 : $ratedPowerW.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $sn = this.getSn();
            result = result * 59 + ($sn == null ? 43 : $sn.hashCode());
            Object $categoryCode = this.getCategoryCode();
            result = result * 59 + ($categoryCode == null ? 43 : $categoryCode.hashCode());
            Object $modelCode = this.getModelCode();
            result = result * 59 + ($modelCode == null ? 43 : $modelCode.hashCode());
            Object $ownerType = this.getOwnerType();
            result = result * 59 + ($ownerType == null ? 43 : $ownerType.hashCode());
            Object $statusBiz = this.getStatusBiz();
            result = result * 59 + ($statusBiz == null ? 43 : $statusBiz.hashCode());
            Object $statusFin = this.getStatusFin();
            result = result * 59 + ($statusFin == null ? 43 : $statusFin.hashCode());
            Object $regionCode = this.getRegionCode();
            result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
            Object $roomCode = this.getRoomCode();
            result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
            Object $rackCode = this.getRackCode();
            result = result * 59 + ($rackCode == null ? 43 : $rackCode.hashCode());
            Object $department = this.getDepartment();
            result = result * 59 + ($department == null ? 43 : $department.hashCode());
            Object $owner = this.getOwner();
            result = result * 59 + ($owner == null ? 43 : $owner.hashCode());
            Object $mgmtIp = this.getMgmtIp();
            result = result * 59 + ($mgmtIp == null ? 43 : $mgmtIp.hashCode());
            Object $bizIp = this.getBizIp();
            result = result * 59 + ($bizIp == null ? 43 : $bizIp.hashCode());
            Object $purchaseDate = this.getPurchaseDate();
            result = result * 59 + ($purchaseDate == null ? 43 : $purchaseDate.hashCode());
            Object $purchasePrice = this.getPurchasePrice();
            result = result * 59 + ($purchasePrice == null ? 43 : $purchasePrice.hashCode());
            Object $warrantyEnd = this.getWarrantyEnd();
            result = result * 59 + ($warrantyEnd == null ? 43 : $warrantyEnd.hashCode());
            Object $rackDate = this.getRackDate();
            result = result * 59 + ($rackDate == null ? 43 : $rackDate.hashCode());
            Object $contractNo = this.getContractNo();
            result = result * 59 + ($contractNo == null ? 43 : $contractNo.hashCode());
            Object $parentAssetNo = this.getParentAssetNo();
            result = result * 59 + ($parentAssetNo == null ? 43 : $parentAssetNo.hashCode());
            Object $attrs = this.getAttrs();
            return result * 59 + ($attrs == null ? 43 : $attrs.hashCode());
        }

        
        @Override
        public String toString() {
            return "AssetController.UpdateAssetReq(version="
                + this.getVersion()
                + ", name="
                + this.getName()
                + ", sn="
                + this.getSn()
                + ", categoryCode="
                + this.getCategoryCode()
                + ", modelCode="
                + this.getModelCode()
                + ", ownerType="
                + this.getOwnerType()
                + ", statusBiz="
                + this.getStatusBiz()
                + ", statusFin="
                + this.getStatusFin()
                + ", regionCode="
                + this.getRegionCode()
                + ", roomCode="
                + this.getRoomCode()
                + ", rackCode="
                + this.getRackCode()
                + ", unitNo="
                + this.getUnitNo()
                + ", uHeight="
                + this.getUHeight()
                + ", department="
                + this.getDepartment()
                + ", owner="
                + this.getOwner()
                + ", mgmtIp="
                + this.getMgmtIp()
                + ", bizIp="
                + this.getBizIp()
                + ", purchaseDate="
                + this.getPurchaseDate()
                + ", purchasePrice="
                + this.getPurchasePrice()
                + ", warrantyEnd="
                + this.getWarrantyEnd()
                + ", rackDate="
                + this.getRackDate()
                + ", contractNo="
                + this.getContractNo()
                + ", ratedPowerW="
                + this.getRatedPowerW()
                + ", parentAssetNo="
                + this.getParentAssetNo()
                + ", attrs="
                + this.getAttrs()
                + ")";
        }
    }
}
