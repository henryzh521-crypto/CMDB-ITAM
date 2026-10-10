package com.dcim.room.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dcim.common.ApiResult;
import com.dcim.common.BusinessException;
import com.dcim.common.PageResult;
import com.dcim.common.csv.CsvSupport;
import com.dcim.room.entity.MachineRoomEntity;
import com.dcim.room.entity.RackEntity;
import com.dcim.room.entity.RackUnitEntity;
import com.dcim.room.mapper.MachineRoomMapper;
import com.dcim.room.mapper.RackMapper;
import com.dcim.room.mapper.RackUnitMapper;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
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
@RequestMapping({"/api/racks"})
public class RackController {
    private static final String ST_FREE = "空闲";
    private static final String ST_OCCUPIED = "占用";
    static final int MAX_U_CAPACITY = 1000;
    private final RackMapper rackMapper;
    private final RackUnitMapper rackUnitMapper;
    private final MachineRoomMapper machineRoomMapper;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate rowTx;
    private static final List<String> CSV_HEADERS = List.of("机柜编号", "机房编码", "位置名", "排号", "列号", "朝向", "U容量", "功率上限kW", "PDU数量", "制冷方式", "负责人", "备注");

    @GetMapping
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String roomCode
    ) {
        LambdaQueryWrapper<RackEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> ((w.like(RackEntity::getRackCode, kw)).or()).like(RackEntity::getLocationName, kw));
        }

        if (roomCode != null && !roomCode.isBlank()) {
            qw.eq(RackEntity::getRoomCode, roomCode);
        }

        qw.orderByAsc(RackEntity::getRackCode);
        Page<RackEntity> p = new Page(page, size);
        this.rackMapper.selectPage(p, qw);
        Map<String, Long> used = this.loadUsedUnits(p.getRecords().stream().map(RackEntity::getRackCode).toList());
        return ApiResult.ok(PageResult.of(p, r -> this.toRow(r, used.getOrDefault(r.getRackCode(), 0L))));
    }

    @PostMapping
    @RequirePerm(
        module = "racks",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "racks",
        objType = "rack",
        objIdSpEL = "#req.rackCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid RackController.CreateRackReq req) {
        String code = req.getRackCode().trim();
        String room = req.getRoomCode().trim();
        int cap = req.getUCapacity() == null ? 42 : req.getUCapacity();
        this.checkUCapacity(cap);
        MachineRoomEntity roomEntity = (MachineRoomEntity)this.machineRoomMapper
            .selectOne(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, room));
        if (roomEntity == null) {
            throw BusinessException.badRequest("机房不存在：" + room);
        } else if (this.selectByCode(code) != null) {
            throw BusinessException.conflict("机柜编号已存在：" + code);
        } else {
            RackEntity e = new RackEntity();
            e.setRackCode(code);
            e.setRoomCode(room);
            e.setLocationName(req.getLocationName());
            e.setRowNo(req.getRowNo());
            e.setColNo(req.getColNo());
            e.setOrientation(req.getOrientation());
            e.setUCapacity(cap);
            e.setPowerLimitKw(req.getPowerLimitKw());
            e.setPduCount(req.getPduCount());
            e.setCoolingType(req.getCoolingType());
            e.setManager(req.getManager());
            e.setRemark(req.getRemark());
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            this.rackMapper.insert(e);
            this.insertUnits(code, 1, cap);
            return ApiResult.ok(this.toRow(e, 0L));
        }
    }

    @PutMapping({"/{rackCode}"})
    @RequirePerm(
        module = "racks",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "racks",
        objType = "rack",
        objIdSpEL = "#rackCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> update(@PathVariable String rackCode, @RequestBody @Valid RackController.UpdateRackReq req) {
        RackEntity e = this.selectByCode(rackCode);
        if (e == null) {
            throw BusinessException.notFound("机柜不存在：" + rackCode);
        } else {
            if (req.getRoomCode() != null) {
                if (req.getRoomCode().isBlank()) {
                    throw BusinessException.badRequest("机房编码不能为空");
                }

                String room = req.getRoomCode().trim();
                if (!room.equals(e.getRoomCode())
                    && this.machineRoomMapper.selectOne(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, room)) == null) {
                    throw BusinessException.badRequest("机房不存在：" + room);
                }

                e.setRoomCode(room);
            }

            if (req.getUCapacity() != null) {
                this.resizeUnits(e, req.getUCapacity());
            }

            if (req.getLocationName() != null) {
                e.setLocationName(req.getLocationName());
            }

            e.setRowNo(req.getRowNo());
            e.setColNo(req.getColNo());
            e.setOrientation(req.getOrientation());
            e.setPowerLimitKw(req.getPowerLimitKw());
            e.setPduCount(req.getPduCount());
            e.setCoolingType(req.getCoolingType());
            e.setManager(req.getManager());
            e.setRemark(req.getRemark());
            e.setUpdatedAt(LocalDateTime.now());
            this.rackMapper.updateById(e);
            long used = this.countUsed(e.getRackCode());
            return ApiResult.ok(this.toRow(e, used));
        }
    }

    @DeleteMapping({"/{rackCode}"})
    @RequirePerm(
        module = "racks",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "racks",
        objType = "rack",
        objIdSpEL = "#rackCode"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable String rackCode) {
        RackEntity e = this.selectByCode(rackCode);
        if (e == null) {
            throw BusinessException.notFound("机柜不存在：" + rackCode);
        } else {
            // 修复 A5：先锁 rack 行再做非空闲检查——与 occupy 的锁序一致（先 rack 后 rack_unit），
            // 防止检查通过后并发上架、随后 DELETE 把已占用的 U 位一并删除
            this.jdbc.queryForList("SELECT rack_code FROM rack WHERE rack_code = ? FOR UPDATE", rackCode);
            Long nonFree = (Long)this.jdbc
                .queryForObject("SELECT COUNT(*) FROM rack_unit WHERE rack_code = ? AND status <> ?", Long.class, new Object[]{rackCode, "空闲"});
            if (nonFree != null && nonFree > 0L) {
                throw BusinessException.conflict("机柜存在 " + nonFree + " 个非空闲 U 位，不允许删除");
            } else {
                Long assetRefs = (Long)this.jdbc
                    .queryForObject("SELECT COUNT(*) FROM asset WHERE deleted_at IS NULL AND rack_code = ?", Long.class, new Object[]{rackCode});
                if (assetRefs != null && assetRefs > 0L) {
                    throw BusinessException.conflict("机柜已被 " + assetRefs + " 台资产引用，不允许删除");
                } else {
                    this.jdbc.update("DELETE FROM rack_unit WHERE rack_code = ?", new Object[]{rackCode});
                    this.rackMapper.deleteById(e.getId());
                    return ApiResult.ok();
                }
            }
        }
    }

    @GetMapping({"/template"})
    @RequirePerm(
        module = "racks"
    )
    public void template(HttpServletResponse response) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(CSV_HEADERS)).append('\n');
        csv.append(CsvSupport.line(List.of("RK-QY01-001", "RM-QY01", "主机房 A 区", "1", "1", "正面", "42", "8", "2", "风冷", "张三", "示例行"))).append('\n');
        this.writeCsv(response, "机柜导入模板.csv", csv.toString());
    }

    @PostMapping(
        value = {"/import"},
        consumes = {"multipart/form-data"}
    )
    @RequirePerm(
        module = "racks",
        write = true,
        action = "import"
    )
    @Audit(
        action = "IMPORT",
        module = "racks",
        objType = "rack"
    )
    public ApiResult<Map<String, Object>> importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        if (file != null && !file.isEmpty()) {
            String text = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<List<String>> rows = CsvSupport.parse(text);
            if (rows.isEmpty()) {
                throw BusinessException.badRequest("CSV 文件为空");
            } else {
                List<String> header = rows.get(0);
                String firstCell = header.isEmpty() ? "" : header.get(0).trim();
                boolean hasHeader = "机柜编号".equals(firstCell);
                Map<String, Integer> colIndex = new LinkedHashMap<>();
                if (hasHeader) {
                    for (int i = 0; i < header.size(); i++) {
                        colIndex.put(header.get(i).trim(), i);
                    }
                }

                List<List<String>> dataRows = rows.subList(hasHeader ? 1 : 0, rows.size());
                Set<String> fileCodes = new HashSet<>();
                int success = 0;
                List<Map<String, Object>> errors = new ArrayList<>();

                for (int i = 0; i < dataRows.size(); i++) {
                    int rowNo = (hasHeader ? 1 : 0) + i + 1;

                    try {
                        this.importRackRow(dataRows.get(i), colIndex, fileCodes);
                        success++;
                    } catch (BusinessException var16) {
                        Map<String, Object> err = new LinkedHashMap<>();
                        err.put("row", rowNo);
                        err.put("reason", var16.getMessage());
                        errors.add(err);
                    } catch (DataIntegrityViolationException var17) {
                        Map<String, Object> errx = new LinkedHashMap<>();
                        errx.put("row", rowNo);
                        errx.put("reason", "数据写入冲突（可能字段超长或已被并发导入）");
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

    private void importRackRow(List<String> cells, Map<String, Integer> colIndex, Set<String> fileCodes) {
        int[] ix = new int[]{
            col(colIndex, "机柜编号", 0),
            col(colIndex, "机房编码", 1),
            col(colIndex, "位置名", 2),
            col(colIndex, "排号", 3),
            col(colIndex, "列号", 4),
            col(colIndex, "朝向", 5),
            col(colIndex, "U容量", 6),
            col(colIndex, "功率上限kW", 7),
            col(colIndex, "PDU数量", 8),
            col(colIndex, "制冷方式", 9),
            col(colIndex, "负责人", 10),
            col(colIndex, "备注", 11)
        };
        String code = blankToNull(cellAt(cells, ix[0]));
        String roomCode = blankToNull(cellAt(cells, ix[1]));
        String locationName = blankToNull(cellAt(cells, ix[2]));
        String rowNo = blankToNull(cellAt(cells, ix[3]));
        Integer colNo = parseInt("列号", cellAt(cells, ix[4]));
        String orientation = blankToNull(cellAt(cells, ix[5]));
        Integer uCapacity = parseInt("U容量", cellAt(cells, ix[6]));
        BigDecimal powerLimitKw = parseDecimal("功率上限kW", cellAt(cells, ix[7]));
        Integer pduCount = parseInt("PDU数量", cellAt(cells, ix[8]));
        String coolingType = blankToNull(cellAt(cells, ix[9]));
        String manager = blankToNull(cellAt(cells, ix[10]));
        String remark = blankToNull(cellAt(cells, ix[11]));
        if (code == null) {
            throw BusinessException.badRequest("机柜编号不能为空");
        } else if (roomCode == null) {
            throw BusinessException.badRequest("机房编码不能为空");
        } else {
            maxLen("机柜编号", code, 50);
            maxLen("机房编码", roomCode, 50);
            maxLen("位置名", locationName, 200);
            maxLen("排号", rowNo, 10);
            maxLen("朝向", orientation, 20);
            maxLen("制冷方式", coolingType, 20);
            maxLen("负责人", manager, 50);
            int cap = uCapacity == null ? 42 : uCapacity;
            this.checkUCapacity(cap);
            MachineRoomEntity roomEntity = (MachineRoomEntity)this.machineRoomMapper
                .selectOne(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, roomCode));
            if (roomEntity == null) {
                throw BusinessException.badRequest("机房不存在：" + roomCode);
            } else if (this.selectByCode(code) != null) {
                throw BusinessException.conflict("机柜编号已存在：" + code);
            } else if (fileCodes.contains(code)) {
                throw BusinessException.conflict("机柜编号已存在（文件内重复）：" + code);
            } else {
                RackEntity e = new RackEntity();
                e.setRackCode(code);
                e.setRoomCode(roomCode);
                e.setLocationName(locationName);
                e.setRowNo(rowNo);
                e.setColNo(colNo);
                e.setOrientation(orientation);
                e.setUCapacity(cap);
                e.setPowerLimitKw(powerLimitKw);
                e.setPduCount(pduCount);
                e.setCoolingType(coolingType);
                e.setManager(manager);
                e.setRemark(remark);
                e.setCreatedAt(LocalDateTime.now());
                e.setUpdatedAt(LocalDateTime.now());

                try {
                    this.rowTx.executeWithoutResult(tx -> {
                        this.rackMapper.insert(e);
                        this.insertUnits(code, 1, cap);
                    });
                } catch (DuplicateKeyException var21) {
                    throw BusinessException.conflict("机柜编号已存在：" + code);
                }

                fileCodes.add(code);
            }
        }
    }

    private static int col(Map<String, Integer> colIndex, String name, int fallback) {
        Integer i = colIndex.get(name);
        return i == null ? fallback : i;
    }

    private static String cellAt(List<String> cells, int idx) {
        return idx >= 0 && idx < cells.size() ? cells.get(idx) : null;
    }

    private static String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    private static void maxLen(String label, String v, int max) {
        if (v != null && v.length() > max) {
            throw BusinessException.badRequest(label + "最长 " + max + " 字符，当前 " + v.length() + " 字符");
        }
    }

    private static BigDecimal parseDecimal(String label, String raw) {
        String s = blankToNull(raw);
        if (s == null) {
            return null;
        } else {
            try {
                return new BigDecimal(s);
            } catch (NumberFormatException var4) {
                throw BusinessException.badRequest(label + " 不是有效数字：" + raw.trim());
            }
        }
    }

    private static Integer parseInt(String label, String raw) {
        String s = blankToNull(raw);
        if (s == null) {
            return null;
        } else {
            try {
                return Integer.valueOf(s);
            } catch (NumberFormatException var4) {
                throw BusinessException.badRequest(label + " 不是有效整数：" + raw.trim());
            }
        }
    }

    private void writeCsv(HttpServletResponse response, String fileName, String csv) throws IOException {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setStatus(200);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.getOutputStream().write(csv.getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
    }

    @GetMapping({"/{rackCode}/units"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<Map<String, Object>> units(@PathVariable String rackCode) {
        RackEntity e = this.selectByCode(rackCode);
        if (e == null) {
            throw BusinessException.notFound("机柜不存在：" + rackCode);
        } else {
            List<Map<String, Object>> units = this.jdbc
                .queryForList(
                    "SELECT ru.unit_no AS \"unitNo\", ru.status AS \"status\", ru.occupied_by AS \"occupiedBy\", a.name AS \"assetName\", a.status_biz AS \"assetStatusBiz\" FROM rack_unit ru LEFT JOIN asset a ON a.asset_no = ru.occupied_by WHERE ru.rack_code = ? ORDER BY ru.unit_no DESC",
                    new Object[]{rackCode}
                );
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rackCode", e.getRackCode());
            data.put("uCapacity", e.getUCapacity());
            data.put("units", units);
            return ApiResult.ok(data);
        }
    }

    @GetMapping({"/{rackCode}/free-units"})
    @RequirePerm(
        module = "racks"
    )
    public ApiResult<List<Integer>> freeUnits(
        @PathVariable String rackCode, @RequestParam(defaultValue = "1") Integer uHeight, @RequestParam(required = false) String excludeAssetNo
    ) {
        RackEntity e = this.selectByCode(rackCode);
        if (e == null) {
            throw BusinessException.notFound("机柜不存在：" + rackCode);
        } else if (uHeight != null && uHeight >= 1) {
            int cap = e.getUCapacity();
            boolean[] usable = new boolean[cap + 1];

            for (RackUnitEntity u : this.rackUnitMapper.selectList(Wrappers.<RackUnitEntity>lambdaQuery().eq(RackUnitEntity::getRackCode, rackCode))) {
                if (u.getUnitNo() != null && u.getUnitNo() >= 1 && u.getUnitNo() <= cap) {
                    boolean free = "空闲".equals(u.getStatus())
                        || "占用".equals(u.getStatus()) && excludeAssetNo != null && excludeAssetNo.equals(u.getOccupiedBy());
                    usable[u.getUnitNo()] = free;
                }
            }

            List<Integer> starts = new ArrayList<>();
            if (uHeight <= cap) {
                for (int s = cap - uHeight + 1; s >= 1; s--) {
                    boolean all = true;

                    for (int i = s; i < s + uHeight; i++) {
                        if (!usable[i]) {
                            all = false;
                            break;
                        }
                    }

                    if (all) {
                        starts.add(s);
                    }
                }
            }

            return ApiResult.ok(starts);
        } else {
            throw BusinessException.badRequest("U位高度必须为正整数");
        }
    }

    private RackEntity selectByCode(String code) {
        return (RackEntity)this.rackMapper.selectOne(Wrappers.<RackEntity>lambdaQuery().eq(RackEntity::getRackCode, code));
    }

    private Map<String, Long> loadUsedUnits(List<String> rackCodes) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (rackCodes != null && !rackCodes.isEmpty()) {
            String in = String.join(",", Collections.nCopies(rackCodes.size(), "?"));
            Object[] args = new Object[rackCodes.size() + 1];
            args[0] = "占用";

            for (int i = 0; i < rackCodes.size(); i++) {
                args[i + 1] = rackCodes.get(i);
            }

            for (Map<String, Object> r : this.jdbc
                .queryForList(
                    "SELECT rack_code AS \"rackCode\", COUNT(*) AS \"n\" FROM rack_unit WHERE status = ? AND rack_code IN (" + in + ") GROUP BY rack_code",
                    args
                )) {
                out.put(String.valueOf(r.get("rackCode")), ((Number)r.get("n")).longValue());
            }

            return out;
        } else {
            return out;
        }
    }

    private long countUsed(String rackCode) {
        Long n = this.rackUnitMapper
            .selectCount((Wrappers.<RackUnitEntity>lambdaQuery().eq(RackUnitEntity::getRackCode, rackCode)).eq(RackUnitEntity::getStatus, "占用"));
        return n == null ? 0L : n;
    }

    private void checkUCapacity(Integer cap) {
        if (cap == null) {
            throw BusinessException.badRequest("U位容量不能为空");
        } else if (cap < 1) {
            throw BusinessException.badRequest("U位容量必须为正整数：" + cap);
        } else if (cap > 1000) {
            throw BusinessException.badRequest("U位容量不能超过 1000U：" + cap);
        }
    }

    private void insertUnits(String rackCode, int from, int to) {
        if (to >= from) {
            this.jdbc
                .update(
                    "INSERT INTO rack_unit (rack_code, unit_no) SELECT ?, g FROM generate_series(?, ?) AS g ON CONFLICT (rack_code, unit_no) DO NOTHING",
                    new Object[]{rackCode, from, to}
                );
        }
    }

    private void resizeUnits(RackEntity e, Integer newCap) {
        this.checkUCapacity(newCap);
        // 修复 A5：先锁 rack 行，使「maxUsed 检查 + DELETE」与资产上架 occupy（同样先锁 rack 行）串行化，
        // 防止检查通过后、DELETE 之前并发上架占用的 U 位被误删
        this.jdbc.queryForList("SELECT rack_code FROM rack WHERE rack_code = ? FOR UPDATE", e.getRackCode());
        int oldCap = e.getUCapacity() == null ? 0 : e.getUCapacity();
        if (newCap > oldCap) {
            this.insertUnits(e.getRackCode(), 1, newCap);
        } else if (newCap < oldCap) {
            Long maxUsed = (Long)this.jdbc
                .queryForObject(
                    "SELECT COALESCE(MAX(unit_no), 0) FROM rack_unit WHERE rack_code = ? AND status <> ?", Long.class, new Object[]{e.getRackCode(), "空闲"}
                );
            if (maxUsed != null && maxUsed > (long)newCap.intValue()) {
                throw BusinessException.conflict("缩容后容量 " + newCap + "U 低于已占用最大 U 位 U" + maxUsed + "，不允许缩容");
            }

            this.jdbc.update("DELETE FROM rack_unit WHERE rack_code = ? AND unit_no > ?", new Object[]{e.getRackCode(), newCap});
        }

        e.setUCapacity(newCap);
    }

    private Map<String, Object> toRow(RackEntity e, long usedUnits) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rackCode", e.getRackCode());
        row.put("roomCode", e.getRoomCode());
        row.put("locationName", e.getLocationName());
        row.put("rowNo", e.getRowNo());
        row.put("colNo", e.getColNo());
        row.put("orientation", e.getOrientation());
        row.put("uCapacity", e.getUCapacity());
        row.put("powerLimitKw", e.getPowerLimitKw());
        row.put("pduCount", e.getPduCount());
        row.put("coolingType", e.getCoolingType());
        row.put("manager", e.getManager());
        row.put("remark", e.getRemark());
        row.put("usedUnits", usedUnits);
        row.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        row.put("updatedAt", e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());
        return row;
    }

    
    public RackController(
        final RackMapper rackMapper,
        final RackUnitMapper rackUnitMapper,
        final MachineRoomMapper machineRoomMapper,
        final JdbcTemplate jdbc,
        final TransactionTemplate rowTx
    ) {
        this.rackMapper = rackMapper;
        this.rackUnitMapper = rackUnitMapper;
        this.machineRoomMapper = machineRoomMapper;
        this.jdbc = jdbc;
        this.rowTx = rowTx;
    }

    public static class CreateRackReq {
        @NotBlank(
            message = "机柜编号不能为空"
        )
        private String rackCode;
        @NotBlank(
            message = "机房编码不能为空"
        )
        private String roomCode;
        private String locationName;
        private String rowNo;
        private Integer colNo;
        private String orientation;
        @JsonProperty("uCapacity")
        private Integer uCapacity;
        private BigDecimal powerLimitKw;
        private Integer pduCount;
        private String coolingType;
        private String manager;
        private String remark;

        
        public String getRackCode() {
            return this.rackCode;
        }

        
        public String getRoomCode() {
            return this.roomCode;
        }

        
        public String getLocationName() {
            return this.locationName;
        }

        
        public String getRowNo() {
            return this.rowNo;
        }

        
        public Integer getColNo() {
            return this.colNo;
        }

        
        public String getOrientation() {
            return this.orientation;
        }

        
        public Integer getUCapacity() {
            return this.uCapacity;
        }

        
        public BigDecimal getPowerLimitKw() {
            return this.powerLimitKw;
        }

        
        public Integer getPduCount() {
            return this.pduCount;
        }

        
        public String getCoolingType() {
            return this.coolingType;
        }

        
        public String getManager() {
            return this.manager;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setRackCode(final String rackCode) {
            this.rackCode = rackCode;
        }

        
        public void setRoomCode(final String roomCode) {
            this.roomCode = roomCode;
        }

        
        public void setLocationName(final String locationName) {
            this.locationName = locationName;
        }

        
        public void setRowNo(final String rowNo) {
            this.rowNo = rowNo;
        }

        
        public void setColNo(final Integer colNo) {
            this.colNo = colNo;
        }

        
        public void setOrientation(final String orientation) {
            this.orientation = orientation;
        }

        @JsonProperty("uCapacity")
        
        public void setUCapacity(final Integer uCapacity) {
            this.uCapacity = uCapacity;
        }

        
        public void setPowerLimitKw(final BigDecimal powerLimitKw) {
            this.powerLimitKw = powerLimitKw;
        }

        
        public void setPduCount(final Integer pduCount) {
            this.pduCount = pduCount;
        }

        
        public void setCoolingType(final String coolingType) {
            this.coolingType = coolingType;
        }

        
        public void setManager(final String manager) {
            this.manager = manager;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof RackController.CreateRackReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$colNo = this.getColNo();
                Object other$colNo = other.getColNo();
                if (this$colNo == null ? other$colNo == null : this$colNo.equals(other$colNo)) {
                    Object this$uCapacity = this.getUCapacity();
                    Object other$uCapacity = other.getUCapacity();
                    if (this$uCapacity == null ? other$uCapacity == null : this$uCapacity.equals(other$uCapacity)) {
                        Object this$pduCount = this.getPduCount();
                        Object other$pduCount = other.getPduCount();
                        if (this$pduCount == null ? other$pduCount == null : this$pduCount.equals(other$pduCount)) {
                            Object this$rackCode = this.getRackCode();
                            Object other$rackCode = other.getRackCode();
                            if (this$rackCode == null ? other$rackCode == null : this$rackCode.equals(other$rackCode)) {
                                Object this$roomCode = this.getRoomCode();
                                Object other$roomCode = other.getRoomCode();
                                if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                    Object this$locationName = this.getLocationName();
                                    Object other$locationName = other.getLocationName();
                                    if (this$locationName == null ? other$locationName == null : this$locationName.equals(other$locationName)) {
                                        Object this$rowNo = this.getRowNo();
                                        Object other$rowNo = other.getRowNo();
                                        if (this$rowNo == null ? other$rowNo == null : this$rowNo.equals(other$rowNo)) {
                                            Object this$orientation = this.getOrientation();
                                            Object other$orientation = other.getOrientation();
                                            if (this$orientation == null ? other$orientation == null : this$orientation.equals(other$orientation)) {
                                                Object this$powerLimitKw = this.getPowerLimitKw();
                                                Object other$powerLimitKw = other.getPowerLimitKw();
                                                if (this$powerLimitKw == null ? other$powerLimitKw == null : this$powerLimitKw.equals(other$powerLimitKw)) {
                                                    Object this$coolingType = this.getCoolingType();
                                                    Object other$coolingType = other.getCoolingType();
                                                    if (this$coolingType == null ? other$coolingType == null : this$coolingType.equals(other$coolingType)) {
                                                        Object this$manager = this.getManager();
                                                        Object other$manager = other.getManager();
                                                        if (this$manager == null ? other$manager == null : this$manager.equals(other$manager)) {
                                                            Object this$remark = this.getRemark();
                                                            Object other$remark = other.getRemark();
                                                            return this$remark == null ? other$remark == null : this$remark.equals(other$remark);
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
            return other instanceof RackController.CreateRackReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $colNo = this.getColNo();
            result = result * 59 + ($colNo == null ? 43 : $colNo.hashCode());
            Object $uCapacity = this.getUCapacity();
            result = result * 59 + ($uCapacity == null ? 43 : $uCapacity.hashCode());
            Object $pduCount = this.getPduCount();
            result = result * 59 + ($pduCount == null ? 43 : $pduCount.hashCode());
            Object $rackCode = this.getRackCode();
            result = result * 59 + ($rackCode == null ? 43 : $rackCode.hashCode());
            Object $roomCode = this.getRoomCode();
            result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
            Object $locationName = this.getLocationName();
            result = result * 59 + ($locationName == null ? 43 : $locationName.hashCode());
            Object $rowNo = this.getRowNo();
            result = result * 59 + ($rowNo == null ? 43 : $rowNo.hashCode());
            Object $orientation = this.getOrientation();
            result = result * 59 + ($orientation == null ? 43 : $orientation.hashCode());
            Object $powerLimitKw = this.getPowerLimitKw();
            result = result * 59 + ($powerLimitKw == null ? 43 : $powerLimitKw.hashCode());
            Object $coolingType = this.getCoolingType();
            result = result * 59 + ($coolingType == null ? 43 : $coolingType.hashCode());
            Object $manager = this.getManager();
            result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "RackController.CreateRackReq(rackCode="
                + this.getRackCode()
                + ", roomCode="
                + this.getRoomCode()
                + ", locationName="
                + this.getLocationName()
                + ", rowNo="
                + this.getRowNo()
                + ", colNo="
                + this.getColNo()
                + ", orientation="
                + this.getOrientation()
                + ", uCapacity="
                + this.getUCapacity()
                + ", powerLimitKw="
                + this.getPowerLimitKw()
                + ", pduCount="
                + this.getPduCount()
                + ", coolingType="
                + this.getCoolingType()
                + ", manager="
                + this.getManager()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class UpdateRackReq {
        private String roomCode;
        private String locationName;
        private String rowNo;
        private Integer colNo;
        private String orientation;
        @JsonProperty("uCapacity")
        private Integer uCapacity;
        private BigDecimal powerLimitKw;
        private Integer pduCount;
        private String coolingType;
        private String manager;
        private String remark;

        
        public String getRoomCode() {
            return this.roomCode;
        }

        
        public String getLocationName() {
            return this.locationName;
        }

        
        public String getRowNo() {
            return this.rowNo;
        }

        
        public Integer getColNo() {
            return this.colNo;
        }

        
        public String getOrientation() {
            return this.orientation;
        }

        
        public Integer getUCapacity() {
            return this.uCapacity;
        }

        
        public BigDecimal getPowerLimitKw() {
            return this.powerLimitKw;
        }

        
        public Integer getPduCount() {
            return this.pduCount;
        }

        
        public String getCoolingType() {
            return this.coolingType;
        }

        
        public String getManager() {
            return this.manager;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setRoomCode(final String roomCode) {
            this.roomCode = roomCode;
        }

        
        public void setLocationName(final String locationName) {
            this.locationName = locationName;
        }

        
        public void setRowNo(final String rowNo) {
            this.rowNo = rowNo;
        }

        
        public void setColNo(final Integer colNo) {
            this.colNo = colNo;
        }

        
        public void setOrientation(final String orientation) {
            this.orientation = orientation;
        }

        @JsonProperty("uCapacity")
        
        public void setUCapacity(final Integer uCapacity) {
            this.uCapacity = uCapacity;
        }

        
        public void setPowerLimitKw(final BigDecimal powerLimitKw) {
            this.powerLimitKw = powerLimitKw;
        }

        
        public void setPduCount(final Integer pduCount) {
            this.pduCount = pduCount;
        }

        
        public void setCoolingType(final String coolingType) {
            this.coolingType = coolingType;
        }

        
        public void setManager(final String manager) {
            this.manager = manager;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof RackController.UpdateRackReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$colNo = this.getColNo();
                Object other$colNo = other.getColNo();
                if (this$colNo == null ? other$colNo == null : this$colNo.equals(other$colNo)) {
                    Object this$uCapacity = this.getUCapacity();
                    Object other$uCapacity = other.getUCapacity();
                    if (this$uCapacity == null ? other$uCapacity == null : this$uCapacity.equals(other$uCapacity)) {
                        Object this$pduCount = this.getPduCount();
                        Object other$pduCount = other.getPduCount();
                        if (this$pduCount == null ? other$pduCount == null : this$pduCount.equals(other$pduCount)) {
                            Object this$roomCode = this.getRoomCode();
                            Object other$roomCode = other.getRoomCode();
                            if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                Object this$locationName = this.getLocationName();
                                Object other$locationName = other.getLocationName();
                                if (this$locationName == null ? other$locationName == null : this$locationName.equals(other$locationName)) {
                                    Object this$rowNo = this.getRowNo();
                                    Object other$rowNo = other.getRowNo();
                                    if (this$rowNo == null ? other$rowNo == null : this$rowNo.equals(other$rowNo)) {
                                        Object this$orientation = this.getOrientation();
                                        Object other$orientation = other.getOrientation();
                                        if (this$orientation == null ? other$orientation == null : this$orientation.equals(other$orientation)) {
                                            Object this$powerLimitKw = this.getPowerLimitKw();
                                            Object other$powerLimitKw = other.getPowerLimitKw();
                                            if (this$powerLimitKw == null ? other$powerLimitKw == null : this$powerLimitKw.equals(other$powerLimitKw)) {
                                                Object this$coolingType = this.getCoolingType();
                                                Object other$coolingType = other.getCoolingType();
                                                if (this$coolingType == null ? other$coolingType == null : this$coolingType.equals(other$coolingType)) {
                                                    Object this$manager = this.getManager();
                                                    Object other$manager = other.getManager();
                                                    if (this$manager == null ? other$manager == null : this$manager.equals(other$manager)) {
                                                        Object this$remark = this.getRemark();
                                                        Object other$remark = other.getRemark();
                                                        return this$remark == null ? other$remark == null : this$remark.equals(other$remark);
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
            return other instanceof RackController.UpdateRackReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $colNo = this.getColNo();
            result = result * 59 + ($colNo == null ? 43 : $colNo.hashCode());
            Object $uCapacity = this.getUCapacity();
            result = result * 59 + ($uCapacity == null ? 43 : $uCapacity.hashCode());
            Object $pduCount = this.getPduCount();
            result = result * 59 + ($pduCount == null ? 43 : $pduCount.hashCode());
            Object $roomCode = this.getRoomCode();
            result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
            Object $locationName = this.getLocationName();
            result = result * 59 + ($locationName == null ? 43 : $locationName.hashCode());
            Object $rowNo = this.getRowNo();
            result = result * 59 + ($rowNo == null ? 43 : $rowNo.hashCode());
            Object $orientation = this.getOrientation();
            result = result * 59 + ($orientation == null ? 43 : $orientation.hashCode());
            Object $powerLimitKw = this.getPowerLimitKw();
            result = result * 59 + ($powerLimitKw == null ? 43 : $powerLimitKw.hashCode());
            Object $coolingType = this.getCoolingType();
            result = result * 59 + ($coolingType == null ? 43 : $coolingType.hashCode());
            Object $manager = this.getManager();
            result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "RackController.UpdateRackReq(roomCode="
                + this.getRoomCode()
                + ", locationName="
                + this.getLocationName()
                + ", rowNo="
                + this.getRowNo()
                + ", colNo="
                + this.getColNo()
                + ", orientation="
                + this.getOrientation()
                + ", uCapacity="
                + this.getUCapacity()
                + ", powerLimitKw="
                + this.getPowerLimitKw()
                + ", pduCount="
                + this.getPduCount()
                + ", coolingType="
                + this.getCoolingType()
                + ", manager="
                + this.getManager()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }
}
