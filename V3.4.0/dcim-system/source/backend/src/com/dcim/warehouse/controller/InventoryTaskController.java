package com.dcim.warehouse.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.seq.SeqNoGenerator;
import com.dcim.common.stock.StockMovementPort;
import com.dcim.security.CurrentUser;
import com.dcim.security.LoginUser;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.warehouse.entity.InventoryAssetRow;
import com.dcim.warehouse.entity.InventoryItemEntity;
import com.dcim.warehouse.entity.InventoryTaskEntity;
import com.dcim.warehouse.mapper.InventoryItemMapper;
import com.dcim.warehouse.mapper.InventoryTaskMapper;
import com.dcim.warehouse.mapper.WarehouseAssetQueryMapper;
import com.dcim.warehouse.service.InventoryDiffTicketService;
import com.dcim.warehouse.service.StockMovementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/inventory-tasks"})
public class InventoryTaskController {
    private static final Set<String> RESULTS = Set.of("一致", "盘亏", "盘盈", "错位");
    private static final String ST_RUNNING = "盘点中";
    private static final String ST_DONE = "已完成";
    private static final String ST_CANCELLED = "已取消";
    private static final String R_PENDING = "未盘";
    private static final String TT_ASSET = "ASSET";
    private static final String TT_MATERIAL = "MATERIAL";
    private final InventoryTaskMapper taskMapper;
    private final InventoryItemMapper itemMapper;
    private final WarehouseAssetQueryMapper assetQueryMapper;
    private final StockMovementService stockMovementService;
    private final InventoryDiffTicketService inventoryDiffTicketService;
    private final SeqNoGenerator seqNoGenerator;
    private final JdbcTemplate jdbc;

    @PostMapping
    @RequirePerm(
        module = "inventories",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "inventories",
        objType = "inventory_task"
    )
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid InventoryTaskController.CreateTaskReq req) {
        if (req.getFilter() == null) {
            throw BusinessException.badRequest("筛选条件 filter 不能为空");
        } else {
            String targetType = req.getTargetType() != null && !req.getTargetType().isBlank() ? req.getTargetType().trim().toUpperCase() : "ASSET";
            if (!"ASSET".equals(targetType) && !"MATERIAL".equals(targetType)) {
                throw BusinessException.badRequest("盘点对象无效，仅支持 ASSET/MATERIAL：" + req.getTargetType());
            } else if ("MATERIAL".equals(targetType)) {
                return this.createMaterialTask(req, targetType);
            } else {
                List<String> regionCodes = this.stringsOf(req.getFilter().get("regionCodes"));
                List<String> roomCodes = this.stringsOf(req.getFilter().get("roomCodes"));
                List<String> categoryCodes = this.stringsOf(req.getFilter().get("categoryCodes"));
                List<String> statusBiz = this.stringsOf(req.getFilter().get("statusBiz"));
                List<InventoryAssetRow> assets = this.assetQueryMapper.selectByFilter(regionCodes, roomCodes, categoryCodes, statusBiz);
                if (assets.isEmpty()) {
                    throw BusinessException.badRequest("所选条件下无匹配资产，请调整筛选条件");
                } else {
                    String taskNo = this.seqNoGenerator.next("INV-" + SeqNoGenerator.todaySegment(), 4);
                    LoginUser u = CurrentUser.get();
                    InventoryTaskEntity task = new InventoryTaskEntity();
                    task.setTaskNo(taskNo);
                    task.setName(req.getName().trim());
                    task.setScope(this.blankToNull(req.getScope()));
                    Map<String, Object> filterJson = new LinkedHashMap<>();
                    if (!regionCodes.isEmpty()) {
                        filterJson.put("regionCodes", regionCodes);
                    }

                    if (!roomCodes.isEmpty()) {
                        filterJson.put("roomCodes", roomCodes);
                    }

                    if (!categoryCodes.isEmpty()) {
                        filterJson.put("categoryCodes", categoryCodes);
                    }

                    if (!statusBiz.isEmpty()) {
                        filterJson.put("statusBiz", statusBiz);
                    }

                    task.setFilter(filterJson);
                    task.setInspector(req.getInspector().trim());
                    task.setStatus("盘点中");
                    task.setTargetType("ASSET");
                    task.setTotalCount(assets.size());
                    task.setCheckedCount(0);
                    task.setDiffCount(0);
                    task.setCreator(u == null ? null : u.username());
                    task.setPlanFinish(this.parseDate(req.getPlanFinish(), "计划完成日期"));
                    task.setVersion(1);
                    task.setCreatedAt(LocalDateTime.now());
                    this.taskMapper.insert(task);

                    for (int i = 0; i < assets.size(); i++) {
                        InventoryAssetRow a = assets.get(i);
                        InventoryItemEntity item = new InventoryItemEntity();
                        item.setItemNo(taskNo + "-" + String.format("%04d", i + 1));
                        item.setTaskNo(taskNo);
                        item.setAssetNo(a.getAssetNo());
                        Map<String, Object> expected = new LinkedHashMap<>();
                        expected.put("rackCode", a.getRackCode());
                        expected.put("unitNo", a.getUnitNo());
                        expected.put("uHeight", a.getUHeight());
                        expected.put("statusBiz", a.getStatusBiz());
                        expected.put("sn", a.getSn());
                        expected.put("categoryCode", a.getCategoryCode());
                        expected.put("modelCode", a.getModelCode());
                        item.setExpected(expected);
                        item.setResult("未盘");
                        this.itemMapper.insert(item);
                    }

                    return ApiResult.ok(this.toRow(this.requireTask(taskNo)));
                }
            }
        }
    }

    private ApiResult<Map<String, Object>> createMaterialTask(InventoryTaskController.CreateTaskReq req, String targetType) {
        List<String> warehouseCodes = this.stringsOf(req.getFilter().get("warehouseCodes"));
        List<String> categoryCodes = this.stringsOf(req.getFilter().get("categoryCodes"));
        List<String> materialCodes = this.stringsOf(req.getFilter().get("materialCodes"));
        StringBuilder sql = new StringBuilder(
            "SELECT m.material_code AS code, m.name, m.unit, m.safety_stock, sl.warehouse_code, sl.qty FROM stock_level sl JOIN material m ON m.material_code = sl.material_code WHERE 1=1"
        );
        List<Object> args = new ArrayList<>();
        this.appendIn(sql, args, "sl.warehouse_code", warehouseCodes);
        this.appendIn(sql, args, "m.category", categoryCodes);
        this.appendIn(sql, args, "m.material_code", materialCodes);
        sql.append(" ORDER BY sl.warehouse_code, m.material_code");
        List<Map<String, Object>> rows = this.jdbc.queryForList(sql.toString(), args.toArray());
        if (rows.isEmpty()) {
            throw BusinessException.badRequest("所选条件下无匹配物料库存，请调整筛选条件");
        } else {
            String taskNo = this.seqNoGenerator.next("INV-" + SeqNoGenerator.todaySegment(), 4);
            LoginUser u = CurrentUser.get();
            InventoryTaskEntity task = new InventoryTaskEntity();
            task.setTaskNo(taskNo);
            task.setName(req.getName().trim());
            task.setScope(this.blankToNull(req.getScope()));
            Map<String, Object> filterJson = new LinkedHashMap<>();
            if (!warehouseCodes.isEmpty()) {
                filterJson.put("warehouseCodes", warehouseCodes);
            }

            if (!categoryCodes.isEmpty()) {
                filterJson.put("categoryCodes", categoryCodes);
            }

            if (!materialCodes.isEmpty()) {
                filterJson.put("materialCodes", materialCodes);
            }

            task.setFilter(filterJson);
            task.setTargetType(targetType);
            task.setInspector(req.getInspector().trim());
            task.setStatus("盘点中");
            task.setTotalCount(rows.size());
            task.setCheckedCount(0);
            task.setDiffCount(0);
            task.setCreator(u == null ? null : u.username());
            task.setPlanFinish(this.parseDate(req.getPlanFinish(), "计划完成日期"));
            task.setVersion(1);
            task.setCreatedAt(LocalDateTime.now());
            this.taskMapper.insert(task);

            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> r = rows.get(i);
                InventoryItemEntity item = new InventoryItemEntity();
                item.setItemNo(taskNo + "-" + String.format("%04d", i + 1));
                item.setTaskNo(taskNo);
                item.setMaterialCode(String.valueOf(r.get("code")));
                item.setWarehouseCode(String.valueOf(r.get("warehouse_code")));
                Map<String, Object> expected = new LinkedHashMap<>();
                expected.put("warehouseCode", item.getWarehouseCode());
                expected.put("qty", r.get("qty"));
                expected.put("unit", r.get("unit"));
                expected.put("materialName", r.get("name"));
                expected.put("safetyStock", r.get("safety_stock"));
                item.setExpected(expected);
                item.setResult("未盘");
                this.itemMapper.insert(item);
            }

            return ApiResult.ok(this.toRow(this.requireTask(taskNo)));
        }
    }

    private void appendIn(StringBuilder sql, List<Object> args, String column, List<String> values) {
        if (values != null && !values.isEmpty()) {
            sql.append(" AND ").append(column).append(" IN (").append(String.join(",", Collections.nCopies(values.size(), "?"))).append(')');
            args.addAll(values);
        }
    }

    @GetMapping
    @RequirePerm(
        module = "inventories"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<InventoryTaskEntity> qw = Wrappers.lambdaQuery();
        if (status != null && !status.isBlank()) {
            qw.eq(InventoryTaskEntity::getStatus, status.trim());
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(
                w -> ((((w.like(InventoryTaskEntity::getTaskNo, kw)).or())
                                .like(InventoryTaskEntity::getName, kw))
                            .or())
                        .like(InventoryTaskEntity::getInspector, kw)
            );
        }

        (qw.orderByDesc(InventoryTaskEntity::getCreatedAt)).orderByDesc(InventoryTaskEntity::getTaskNo);
        Page<InventoryTaskEntity> p = new Page(page, size);
        this.taskMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, this::toRow));
    }

    @GetMapping({"/{taskNo}"})
    @RequirePerm(
        module = "inventories"
    )
    public ApiResult<Map<String, Object>> detail(@PathVariable String taskNo) {
        return ApiResult.ok(this.toRow(this.requireTask(taskNo)));
    }

    @GetMapping({"/{taskNo}/items"})
    @RequirePerm(
        module = "inventories"
    )
    public ApiResult<PageResult<Map<String, Object>>> items(
        @PathVariable String taskNo,
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String result
    ) {
        this.requireTask(taskNo);
        LambdaQueryWrapper<InventoryItemEntity> qw = Wrappers.lambdaQuery();
        qw.eq(InventoryItemEntity::getTaskNo, taskNo);
        if (result != null && !result.isBlank()) {
            qw.eq(InventoryItemEntity::getResult, result.trim());
        }

        qw.orderByAsc(InventoryItemEntity::getItemNo);
        Page<InventoryItemEntity> p = new Page(page, size);
        this.itemMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, this::itemRow));
    }

    @PutMapping({"/{taskNo}/items/{itemNo}"})
    @RequirePerm(
        module = "inventories",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "inventories",
        objType = "inventory_item",
        objIdSpEL = "#itemNo"
    )
    @Transactional
    public ApiResult<Map<String, Object>> submitItem(
        @PathVariable String taskNo, @PathVariable String itemNo, @RequestBody InventoryTaskController.SubmitItemReq req
    ) {
        // 修复 A2：任务行 FOR UPDATE 锁读并复查状态——与 complete/cancel 的状态占位更新互斥，
        // 任务一旦完成/取消，迟到的明细提交以 409 拒绝，避免污染已定稿的 checked/diff 统计
        InventoryTaskEntity task = this.requireTaskForUpdate(taskNo);
        if (!"盘点中".equals(task.getStatus())) {
            throw BusinessException.conflict("任务已完成，提交被拒绝（任务状态：「" + task.getStatus() + "」）");
        } else {
            String result = req.getResult() == null ? "" : req.getResult().trim();
            boolean isMaterialTask = "MATERIAL".equals(task.getTargetType());
            InventoryItemEntity item = (InventoryItemEntity)this.itemMapper
                .selectOne((Wrappers.<InventoryItemEntity>lambdaQuery().eq(InventoryItemEntity::getItemNo, itemNo)).last("FOR UPDATE"));
            if (item != null && taskNo.equals(item.getTaskNo())) {
                if (result.isEmpty()) {
                    if (!isMaterialTask || req.getCountedQty() == null) {
                        throw BusinessException.badRequest("盘点结果不能为空，仅支持 一致/盘亏/盘盈/错位");
                    }

                    result = this.deriveMaterialResult(item, req.getCountedQty());
                } else if (!RESULTS.contains(result)) {
                    throw BusinessException.badRequest("盘点结果无效，仅支持 一致/盘亏/盘盈/错位：" + req.getResult());
                }

                if (isMaterialTask) {
                    if (req.getCountedQty() == null) {
                        throw BusinessException.badRequest("物料盘点必须填写实盘数量");
                    }

                    if (req.getCountedQty() < 0) {
                        throw BusinessException.badRequest("实盘数量不能为负数");
                    }

                    item.setCountedQty(req.getCountedQty());
                }

                String old = item.getResult() == null ? "未盘" : item.getResult();
                LoginUser u = CurrentUser.get();
                item.setResult(result);
                item.setDiffNote(this.blankToNull(req.getDiffNote()));
                item.setCheckedAt(LocalDateTime.now());
                item.setChecker(u == null ? null : u.username());
                this.itemMapper.updateById(item);
                int checkedDelta = "未盘".equals(old) ? 1 : 0;
                int diffDelta;
                if ("未盘".equals(old)) {
                    diffDelta = "一致".equals(result) ? 0 : 1;
                } else if ("一致".equals(old) && !"一致".equals(result)) {
                    diffDelta = 1;
                } else if (!"一致".equals(old) && "一致".equals(result)) {
                    diffDelta = -1;
                } else {
                    diffDelta = 0;
                }

                if (checkedDelta != 0 || diffDelta != 0) {
                    this.jdbc
                        .update(
                            "UPDATE inventory_task SET checked_count = checked_count + ?, diff_count = diff_count + ? WHERE task_no = ?",
                            new Object[]{checkedDelta, diffDelta, taskNo}
                        );
                }

                return ApiResult.ok(this.itemRow(item));
            } else {
                throw BusinessException.notFound("盘点明细不存在：" + itemNo);
            }
        }
    }

    @PostMapping({"/{taskNo}/complete"})
    @RequirePerm(
        module = "inventories",
        write = true
    )
    @Audit(
        action = "COMPLETE",
        module = "inventories",
        objType = "inventory_task",
        objIdSpEL = "#taskNo"
    )
    @Transactional
    public ApiResult<Map<String, Object>> complete(@PathVariable String taskNo) {
        // 修复：先以「状态=盘点中」为条件原子占位，防止并发双重 complete / complete 与 cancel 竞态
        // 导致 ADJUST 重复执行（重复流水、totalIn/Out 重复累计）
        int claimed = this.taskMapper
            .update(
                null,
                ((Wrappers.<InventoryTaskEntity>lambdaUpdate().eq(InventoryTaskEntity::getTaskNo, taskNo))
                        .eq(InventoryTaskEntity::getStatus, "盘点中"))
                    .set(InventoryTaskEntity::getStatus, "已完成")
            );
        if (claimed == 0) {
            InventoryTaskEntity current = this.requireTask(taskNo);
            throw BusinessException.badRequest("任务状态为「" + (current == null ? "未知" : current.getStatus()) + "」，不允许完成");
        }
        Long unchecked = this.itemMapper
            .selectCount(
                (Wrappers.<InventoryItemEntity>lambdaQuery().eq(InventoryItemEntity::getTaskNo, taskNo)).eq(InventoryItemEntity::getResult, "未盘")
            );
        if (unchecked != null && unchecked > 0L) {
            // 抛异常回滚上方占位更新，任务保持盘点中
            throw BusinessException.badRequest("尚有 " + unchecked + " 条未盘");
        }
        InventoryTaskEntity task = this.requireTask(taskNo);
        if ("MATERIAL".equals(task.getTargetType())) {
            this.applyCountAdjustments(task);
        }

        // v3.5 联动：存在差异则自动生成「盘点差异」工单（与完成同事务，
        // 建单失败连带回滚完成操作——一致性优先；开关可关）
        this.inventoryDiffTicketService.createDiffTicketIfNeeded(task);

        this.taskMapper
            .update(
                null,
                (Wrappers.<InventoryTaskEntity>lambdaUpdate().eq(InventoryTaskEntity::getTaskNo, taskNo))
                    .set(InventoryTaskEntity::getFinishTime, LocalDateTime.now())
            );
        return ApiResult.ok(this.toRow(this.requireTask(taskNo)));
    }

    @PostMapping({"/{taskNo}/cancel"})
    @RequirePerm(
        module = "inventories",
        write = true
    )
    @Audit(
        action = "CANCEL",
        module = "inventories",
        objType = "inventory_task",
        objIdSpEL = "#taskNo"
    )
    @Transactional
    public ApiResult<Map<String, Object>> cancel(@PathVariable String taskNo) {
        // 修复：以「状态<>已完成」为条件原子占位，关闭 cancel 与 complete 的竞态窗口
        int claimed = this.taskMapper
            .update(
                null,
                (Wrappers.<InventoryTaskEntity>lambdaUpdate().eq(InventoryTaskEntity::getTaskNo, taskNo))
                    .ne(InventoryTaskEntity::getStatus, "已完成")
                    .set(InventoryTaskEntity::getStatus, "已取消")
            );
        if (claimed == 0) {
            throw BusinessException.badRequest("已完成的任务不允许取消");
        }
        return ApiResult.ok(this.toRow(this.requireTask(taskNo)));
    }

    private String deriveMaterialResult(InventoryItemEntity item, int counted) {
        Object book = item.getExpected() == null ? null : item.getExpected().get("qty");
        int bookQty = book == null ? 0 : ((Number)book).intValue();
        if (counted == bookQty) {
            return "一致";
        } else {
            return counted < bookQty ? "盘亏" : "盘盈";
        }
    }

    private void applyCountAdjustments(InventoryTaskEntity task) {
        LoginUser u = CurrentUser.get();
        String operator = u == null ? null : u.username();

        // 修复 A1：明细按物料编码排序——多任务并发完成时以一致顺序加 material 行锁，避免交叉死锁
        List<InventoryItemEntity> items = this.itemMapper
            .selectList(
                Wrappers.<InventoryItemEntity>lambdaQuery()
                    .eq(InventoryItemEntity::getTaskNo, task.getTaskNo())
                    .orderByAsc(InventoryItemEntity::getMaterialCode)
            );

        for (InventoryItemEntity item : items) {
            if (item.getCountedQty() != null && item.getExpected() != null) {
                Object book = item.getExpected().get("qty");
                if (book != null) {
                    int bookQty = ((Number)book).intValue();
                    int counted = item.getCountedQty();
                    if (bookQty != counted) {
                        // 修复 A1：先锁 material 行再读当前账面（stock_level 的全部写入都以先锁 material 为前提），
                        // 使「读账面→算差异→写目标」与 apply 锁内重读的 whBefore 使用同一数值——
                        // 否则锁外读取与锁内写入之间提交的在途出入库会被基于旧账面的目标值回抹，
                        // 且 stock_log 备注中的 before/after 与实际写入不一致
                        this.jdbc.queryForList("SELECT material_code FROM material WHERE material_code = ? FOR UPDATE", item.getMaterialCode());
                        // 修复：改为「按差额修正当前库存」，避免把建任务快照之后的正常出入库整量覆盖。
                        // 目标 = 当前库存 + (实盘 - 快照账面)
                        int current = this.currentLevel(item.getMaterialCode(), item.getWarehouseCode());
                        int target = Math.max(0, current + (counted - bookQty));
                        if (target != current) {
                            this.stockMovementService
                                .apply(
                                    new StockMovementPort.MovementCmd(
                                        "ADJUST",
                                        item.getMaterialCode(),
                                        target,
                                        null,
                                        null,
                                        task.getTaskNo(),
                                        operator,
                                        "盘点 " + task.getTaskNo() + " 差异调整（快照账面 " + bookQty + "，实盘 " + counted
                                            + "，按差额修正 " + current + " → " + target + "）",
                                        item.getWarehouseCode(),
                                        null
                                    )
                                );
                        }
                    }
                }
            }
        }
    }

    /** 读取当前 (物料, 仓库) 库存量，行不存在时按 0 处理（与 StockMovementService.levelQty 同一数据源） */
    private int currentLevel(String materialCode, String warehouseCode) {
        List<Integer> rows = this.jdbc
            .queryForList(
                "SELECT qty FROM stock_level WHERE material_code = ? AND warehouse_code = ?", Integer.class, new Object[]{materialCode, warehouseCode}
            );
        return !rows.isEmpty() && rows.get(0) != null ? rows.get(0) : 0;
    }

    private List<String> stringsOf(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List) {
            for (Object o : (List)v) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    out.add(String.valueOf(o).trim());
                }
            }
        } else if (v instanceof String s && !s.isBlank()) {
            out.add(s.trim());
        }

        return out;
    }

    private InventoryTaskEntity requireTask(String taskNo) {
        InventoryTaskEntity t = (InventoryTaskEntity)this.taskMapper.selectOne(Wrappers.<InventoryTaskEntity>lambdaQuery().eq(InventoryTaskEntity::getTaskNo, taskNo));
        if (t == null) {
            throw BusinessException.notFound("盘点任务不存在：" + taskNo);
        } else {
            return t;
        }
    }

    /** 修复 A2：任务行 FOR UPDATE 锁读——submitItem 与 complete/cancel 并发时以行锁串行化并复查状态 */
    private InventoryTaskEntity requireTaskForUpdate(String taskNo) {
        InventoryTaskEntity t = (InventoryTaskEntity)this.taskMapper
            .selectOne(Wrappers.<InventoryTaskEntity>lambdaQuery().eq(InventoryTaskEntity::getTaskNo, taskNo).last("FOR UPDATE"));
        if (t == null) {
            throw BusinessException.notFound("盘点任务不存在：" + taskNo);
        } else {
            return t;
        }
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

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private Map<String, Object> toRow(InventoryTaskEntity t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskNo", t.getTaskNo());
        row.put("name", t.getName());
        row.put("scope", t.getScope());
        row.put("filter", t.getFilter());
        row.put("targetType", t.getTargetType() == null ? "ASSET" : t.getTargetType());
        row.put("inspector", t.getInspector());
        row.put("status", t.getStatus());
        row.put("totalCount", t.getTotalCount());
        row.put("checkedCount", t.getCheckedCount());
        row.put("diffCount", t.getDiffCount());
        row.put("creator", t.getCreator());
        row.put("planFinish", t.getPlanFinish() == null ? "" : t.getPlanFinish().toString());
        row.put("finishTime", t.getFinishTime() == null ? "" : t.getFinishTime().toString());
        row.put("createdAt", t.getCreatedAt() == null ? "" : t.getCreatedAt().toString());
        return row;
    }

    private Map<String, Object> itemRow(InventoryItemEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("itemNo", e.getItemNo());
        row.put("taskNo", e.getTaskNo());
        row.put("assetNo", e.getAssetNo());
        row.put("materialCode", e.getMaterialCode());
        row.put("warehouseCode", e.getWarehouseCode());
        row.put("countedQty", e.getCountedQty());
        row.put("expected", e.getExpected());
        row.put("result", e.getResult());
        row.put("checkedAt", e.getCheckedAt() == null ? "" : e.getCheckedAt().toString());
        row.put("checker", e.getChecker());
        row.put("diffNote", e.getDiffNote());
        return row;
    }

    
    public InventoryTaskController(
        final InventoryTaskMapper taskMapper,
        final InventoryItemMapper itemMapper,
        final WarehouseAssetQueryMapper assetQueryMapper,
        final StockMovementService stockMovementService,
        final InventoryDiffTicketService inventoryDiffTicketService,
        final SeqNoGenerator seqNoGenerator,
        final JdbcTemplate jdbc
    ) {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.assetQueryMapper = assetQueryMapper;
        this.stockMovementService = stockMovementService;
        this.inventoryDiffTicketService = inventoryDiffTicketService;
        this.seqNoGenerator = seqNoGenerator;
        this.jdbc = jdbc;
    }

    public static class CreateTaskReq {
        @NotBlank(
            message = "任务名称不能为空"
        )
        private String name;
        private Map<String, Object> filter;
        @NotBlank(
            message = "盘点人不能为空"
        )
        private String inspector;
        private String planFinish;
        private String scope;
        private String targetType;

        
        public String getName() {
            return this.name;
        }

        
        public Map<String, Object> getFilter() {
            return this.filter;
        }

        
        public String getInspector() {
            return this.inspector;
        }

        
        public String getPlanFinish() {
            return this.planFinish;
        }

        
        public String getScope() {
            return this.scope;
        }

        
        public String getTargetType() {
            return this.targetType;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setFilter(final Map<String, Object> filter) {
            this.filter = filter;
        }

        
        public void setInspector(final String inspector) {
            this.inspector = inspector;
        }

        
        public void setPlanFinish(final String planFinish) {
            this.planFinish = planFinish;
        }

        
        public void setScope(final String scope) {
            this.scope = scope;
        }

        
        public void setTargetType(final String targetType) {
            this.targetType = targetType;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof InventoryTaskController.CreateTaskReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$name = this.getName();
                Object other$name = other.getName();
                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                    Object this$filter = this.getFilter();
                    Object other$filter = other.getFilter();
                    if (this$filter == null ? other$filter == null : this$filter.equals(other$filter)) {
                        Object this$inspector = this.getInspector();
                        Object other$inspector = other.getInspector();
                        if (this$inspector == null ? other$inspector == null : this$inspector.equals(other$inspector)) {
                            Object this$planFinish = this.getPlanFinish();
                            Object other$planFinish = other.getPlanFinish();
                            if (this$planFinish == null ? other$planFinish == null : this$planFinish.equals(other$planFinish)) {
                                Object this$scope = this.getScope();
                                Object other$scope = other.getScope();
                                if (this$scope == null ? other$scope == null : this$scope.equals(other$scope)) {
                                    Object this$targetType = this.getTargetType();
                                    Object other$targetType = other.getTargetType();
                                    return this$targetType == null ? other$targetType == null : this$targetType.equals(other$targetType);
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
            return other instanceof InventoryTaskController.CreateTaskReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $filter = this.getFilter();
            result = result * 59 + ($filter == null ? 43 : $filter.hashCode());
            Object $inspector = this.getInspector();
            result = result * 59 + ($inspector == null ? 43 : $inspector.hashCode());
            Object $planFinish = this.getPlanFinish();
            result = result * 59 + ($planFinish == null ? 43 : $planFinish.hashCode());
            Object $scope = this.getScope();
            result = result * 59 + ($scope == null ? 43 : $scope.hashCode());
            Object $targetType = this.getTargetType();
            return result * 59 + ($targetType == null ? 43 : $targetType.hashCode());
        }

        
        @Override
        public String toString() {
            return "InventoryTaskController.CreateTaskReq(name="
                + this.getName()
                + ", filter="
                + this.getFilter()
                + ", inspector="
                + this.getInspector()
                + ", planFinish="
                + this.getPlanFinish()
                + ", scope="
                + this.getScope()
                + ", targetType="
                + this.getTargetType()
                + ")";
        }
    }

    public static class SubmitItemReq {
        private String result;
        private String diffNote;
        private Integer countedQty;

        
        public String getResult() {
            return this.result;
        }

        
        public String getDiffNote() {
            return this.diffNote;
        }

        
        public Integer getCountedQty() {
            return this.countedQty;
        }

        
        public void setResult(final String result) {
            this.result = result;
        }

        
        public void setDiffNote(final String diffNote) {
            this.diffNote = diffNote;
        }

        
        public void setCountedQty(final Integer countedQty) {
            this.countedQty = countedQty;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof InventoryTaskController.SubmitItemReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$countedQty = this.getCountedQty();
                Object other$countedQty = other.getCountedQty();
                if (this$countedQty == null ? other$countedQty == null : this$countedQty.equals(other$countedQty)) {
                    Object this$result = this.getResult();
                    Object other$result = other.getResult();
                    if (this$result == null ? other$result == null : this$result.equals(other$result)) {
                        Object this$diffNote = this.getDiffNote();
                        Object other$diffNote = other.getDiffNote();
                        return this$diffNote == null ? other$diffNote == null : this$diffNote.equals(other$diffNote);
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof InventoryTaskController.SubmitItemReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $countedQty = this.getCountedQty();
            result = result * 59 + ($countedQty == null ? 43 : $countedQty.hashCode());
            Object $result = this.getResult();
            result = result * 59 + ($result == null ? 43 : $result.hashCode());
            Object $diffNote = this.getDiffNote();
            return result * 59 + ($diffNote == null ? 43 : $diffNote.hashCode());
        }

        
        @Override
        public String toString() {
            return "InventoryTaskController.SubmitItemReq(result="
                + this.getResult()
                + ", diffNote="
                + this.getDiffNote()
                + ", countedQty="
                + this.getCountedQty()
                + ")";
        }
    }
}
