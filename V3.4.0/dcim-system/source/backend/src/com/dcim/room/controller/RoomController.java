package com.dcim.room.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
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
@RequestMapping({"/api/rooms"})
public class RoomController {
    private final MachineRoomMapper machineRoomMapper;
    private final RackMapper rackMapper;
    private final RackUnitMapper rackUnitMapper;
    private final TransactionTemplate rowTx;
    private static final List<String> CSV_HEADERS = List.of(
        "机房编码", "机房名称", "数据中心", "区域编码", "城市", "位置", "面积㎡", "承重kg", "冷量kW", "功率kW", "机柜数", "AB路", "环境监控", "负责人", "备注"
    );

    @GetMapping
    @RequirePerm(
        module = "machineRooms"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page, @RequestParam(defaultValue = "20") long size, @RequestParam(required = false) String keyword
    ) {
        LambdaQueryWrapper<MachineRoomEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(
                w -> ((((w.like(MachineRoomEntity::getRoomCode, kw)).or())
                                .like(MachineRoomEntity::getName, kw))
                            .or())
                        .like(MachineRoomEntity::getDataCenter, kw)
            );
        }

        qw.orderByAsc(MachineRoomEntity::getRoomCode);
        Page<MachineRoomEntity> p = new Page(page, size);
        this.machineRoomMapper.selectPage(p, qw);
        return ApiResult.ok(PageResult.of(p, this::toRow));
    }

    @PostMapping
    @RequirePerm(
        module = "machineRooms",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "machineRooms",
        objType = "machineRoom",
        objIdSpEL = "#req.roomCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid RoomController.CreateRoomReq req) {
        String code = req.getRoomCode().trim();
        if (this.selectByCode(code) != null) {
            throw BusinessException.conflict("机房编码已存在：" + code);
        } else {
            MachineRoomEntity e = new MachineRoomEntity();
            e.setRoomCode(code);
            this.applyUpdate(
                e,
                req.getName(),
                req.getDataCenter(),
                req.getRegionCode(),
                req.getCity(),
                req.getLocation(),
                req.getAreaSqm(),
                req.getLoadKg(),
                req.getCoolingKw(),
                req.getPowerKw(),
                req.getCabinetCount(),
                req.getAbPath(),
                req.getEnvMonitor(),
                req.getManager(),
                req.getRemark()
            );
            if (e.getRegionCode() == null || e.getRegionCode().isBlank()) {
                e.setRegionCode(inferRegionCode(code, e.getDataCenter()));
            }

            e.setCreatedAt(LocalDateTime.now());
            this.machineRoomMapper.insert(e);
            return ApiResult.ok(this.toRow(e));
        }
    }

    @PutMapping({"/{roomCode}"})
    @RequirePerm(
        module = "machineRooms",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "machineRooms",
        objType = "machineRoom",
        objIdSpEL = "#roomCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> update(@PathVariable String roomCode, @RequestBody @Valid RoomController.UpdateRoomReq req) {
        MachineRoomEntity e = this.selectByCode(roomCode);
        if (e == null) {
            throw BusinessException.notFound("机房不存在：" + roomCode);
        } else {
            this.applyUpdate(
                e,
                req.getName(),
                req.getDataCenter(),
                req.getRegionCode(),
                req.getCity(),
                req.getLocation(),
                req.getAreaSqm(),
                req.getLoadKg(),
                req.getCoolingKw(),
                req.getPowerKw(),
                req.getCabinetCount(),
                req.getAbPath(),
                req.getEnvMonitor(),
                req.getManager(),
                req.getRemark()
            );
            this.machineRoomMapper.updateById(e);
            return ApiResult.ok(this.toRow(e));
        }
    }

    @DeleteMapping({"/{roomCode}"})
    @RequirePerm(
        module = "machineRooms",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "machineRooms",
        objType = "machineRoom",
        objIdSpEL = "#roomCode"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable String roomCode) {
        MachineRoomEntity e = this.selectByCode(roomCode);
        if (e == null) {
            throw BusinessException.notFound("机房不存在：" + roomCode);
        } else {
            Long racks = this.rackMapper.selectCount(Wrappers.<RackEntity>lambdaQuery().eq(RackEntity::getRoomCode, roomCode));
            if (racks != null && racks > 0L) {
                throw BusinessException.conflict("机房下存在 " + racks + " 台机柜，不允许删除");
            } else {
                this.machineRoomMapper.deleteById(e.getId());
                return ApiResult.ok();
            }
        }
    }

    @GetMapping({"/template"})
    @RequirePerm(
        module = "machineRooms"
    )
    public void template(HttpServletResponse response) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff').append(CsvSupport.line(CSV_HEADERS)).append('\n');
        csv.append(
                CsvSupport.line(
                    List.of("RM-QY01", "庆阳一号机房", "庆阳数据中心", "", "庆阳", "西峰区产业园 1 号楼", "1200.50", "1500", "80", "200", "40", "A+B", "机房管家", "张三", "示例行")
                )
            )
            .append('\n');
        this.writeCsv(response, "机房导入模板.csv", csv.toString());
    }

    @PostMapping(
        value = {"/import"},
        consumes = {"multipart/form-data"}
    )
    @RequirePerm(
        module = "machineRooms",
        write = true,
        action = "import"
    )
    @Audit(
        action = "IMPORT",
        module = "machineRooms",
        objType = "machineRoom"
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
                boolean hasHeader = "机房编码".equals(firstCell);
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
                        this.importRoomRow(dataRows.get(i), colIndex, fileCodes);
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

    private void importRoomRow(List<String> cells, Map<String, Integer> colIndex, Set<String> fileCodes) {
        int[] ix = new int[]{
            col(colIndex, "机房编码", 0),
            col(colIndex, "机房名称", 1),
            col(colIndex, "数据中心", 2),
            col(colIndex, "区域编码", 3),
            col(colIndex, "城市", 4),
            col(colIndex, "位置", 5),
            col(colIndex, "面积㎡", 6),
            col(colIndex, "承重kg", 7),
            col(colIndex, "冷量kW", 8),
            col(colIndex, "功率kW", 9),
            col(colIndex, "机柜数", 10),
            col(colIndex, "AB路", 11),
            col(colIndex, "环境监控", 12),
            col(colIndex, "负责人", 13),
            col(colIndex, "备注", 14)
        };
        String code = blankToNull(cellAt(cells, ix[0]));
        String name = blankToNull(cellAt(cells, ix[1]));
        String dataCenter = blankToNull(cellAt(cells, ix[2]));
        String regionCode = blankToNull(cellAt(cells, ix[3]));
        String city = blankToNull(cellAt(cells, ix[4]));
        String location = blankToNull(cellAt(cells, ix[5]));
        BigDecimal areaSqm = parseDecimal("面积㎡", cellAt(cells, ix[6]));
        BigDecimal loadKg = parseDecimal("承重kg", cellAt(cells, ix[7]));
        BigDecimal coolingKw = parseDecimal("冷量kW", cellAt(cells, ix[8]));
        BigDecimal powerKw = parseDecimal("功率kW", cellAt(cells, ix[9]));
        Integer cabinetCount = parseInt("机柜数", cellAt(cells, ix[10]));
        String abPath = blankToNull(cellAt(cells, ix[11]));
        String envMonitor = blankToNull(cellAt(cells, ix[12]));
        String manager = blankToNull(cellAt(cells, ix[13]));
        String remark = blankToNull(cellAt(cells, ix[14]));
        if (code == null) {
            throw BusinessException.badRequest("机房编码不能为空");
        } else if (name == null) {
            throw BusinessException.badRequest("机房名称不能为空");
        } else {
            maxLen("机房编码", code, 50);
            maxLen("机房名称", name, 100);
            maxLen("数据中心", dataCenter, 100);
            maxLen("区域编码", regionCode, 20);
            maxLen("城市", city, 50);
            maxLen("位置", location, 100);
            maxLen("AB路", abPath, 20);
            maxLen("环境监控", envMonitor, 100);
            maxLen("负责人", manager, 50);
            if (this.selectByCode(code) != null) {
                throw BusinessException.conflict("机房编码已存在：" + code);
            } else if (fileCodes.contains(code)) {
                throw BusinessException.conflict("机房编码已存在（文件内重复）：" + code);
            } else {
                MachineRoomEntity e = new MachineRoomEntity();
                e.setRoomCode(code);
                this.applyUpdate(
                    e, name, dataCenter, regionCode, city, location, areaSqm, loadKg, coolingKw, powerKw, cabinetCount, abPath, envMonitor, manager, remark
                );
                if (e.getRegionCode() == null || e.getRegionCode().isBlank()) {
                    e.setRegionCode(inferRegionCode(code, e.getDataCenter()));
                }

                e.setCreatedAt(LocalDateTime.now());

                try {
                    this.rowTx.executeWithoutResult(tx -> this.machineRoomMapper.insert(e));
                } catch (DuplicateKeyException var22) {
                    throw BusinessException.conflict("机房编码已存在：" + code);
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

    @GetMapping({"/{roomCode}/floor"})
    @RequirePerm(
        module = "machineRooms"
    )
    public ApiResult<Map<String, Object>> floor(@PathVariable String roomCode) {
        MachineRoomEntity room = this.selectByCode(roomCode);
        if (room == null) {
            throw BusinessException.notFound("机房不存在：" + roomCode);
        } else {
            List<RackEntity> racks = this.rackMapper
                .selectList(
                    (((Wrappers.<RackEntity>lambdaQuery().eq(RackEntity::getRoomCode, roomCode))
                                .orderByAsc(RackEntity::getRowNo))
                            .orderByAsc(RackEntity::getColNo))
                        .orderByAsc(RackEntity::getRackCode)
                );
            Map<String, Long> usedMap = new HashMap<>();
            if (!racks.isEmpty()) {
                List<String> codes = racks.stream().map(RackEntity::getRackCode).collect(Collectors.toList());
                QueryWrapper<RackUnitEntity> uw = new QueryWrapper();
                (((uw.select(new String[]{"rack_code", "COUNT(*) AS used_cnt"})).eq("status", "占用"))
                        .in("rack_code", codes))
                    .groupBy("rack_code");

                for (Map<String, Object> row : this.rackUnitMapper.selectMaps(uw)) {
                    Object code = row.get("rack_code");
                    Object cnt = row.get("used_cnt");
                    if (code != null && cnt instanceof Number) {
                        usedMap.put(code.toString(), ((Number)cnt).longValue());
                    }
                }
            }

            List<RackEntity> sorted = new ArrayList<>(racks);
            sorted.sort(
                Comparator.comparing(RackEntity::getRowNo, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(RackEntity::getColNo, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(RackEntity::getRackCode)
            );
            Map<String, Integer> effColByCode = new HashMap<>();
            String curRow = null;
            int seq = 0;

            for (RackEntity r : sorted) {
                String rowKey = r.getRowNo() == null ? "" : r.getRowNo();
                if (!rowKey.equals(curRow)) {
                    curRow = rowKey;
                    seq = 0;
                }

                seq++;
                effColByCode.put(r.getRackCode(), r.getColNo() != null ? r.getColNo() : seq);
            }

            long totalUnits = 0L;
            long usedUnits = 0L;
            List<Map<String, Object>> rackRows = new ArrayList<>();

            for (RackEntity r : sorted) {
                int cap = r.getUCapacity() == null ? 42 : r.getUCapacity();
                long used = usedMap.getOrDefault(r.getRackCode(), 0L);
                if (used > (long)cap) {
                    used = (long)cap;
                }

                totalUnits += (long)cap;
                usedUnits += used;
                Map<String, Object> rowx = new LinkedHashMap<>();
                rowx.put("rackCode", r.getRackCode());
                rowx.put("roomCode", r.getRoomCode());
                rowx.put("rowNo", r.getRowNo());
                rowx.put("colNo", r.getColNo());
                rowx.put("effRow", r.getRowNo());
                rowx.put("effCol", effColByCode.get(r.getRackCode()));
                rowx.put("locationName", r.getLocationName());
                rowx.put("orientation", r.getOrientation());
                rowx.put("uCapacity", cap);
                rowx.put("usedUnits", used);
                rowx.put("freeUnits", (long)cap - used);
                rowx.put("usageRate", cap == 0 ? 0 : BigDecimal.valueOf(used).divide(BigDecimal.valueOf((long)cap), 4, RoundingMode.HALF_UP));
                rowx.put("powerLimitKw", r.getPowerLimitKw());
                rowx.put("pduCount", r.getPduCount());
                rowx.put("coolingType", r.getCoolingType());
                rowx.put("manager", r.getManager());
                rackRows.add(rowx);
            }

            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("rackCount", rackRows.size());
            stats.put("totalUnits", totalUnits);
            stats.put("usedUnits", usedUnits);
            stats.put("freeUnits", totalUnits - usedUnits);
            stats.put("usageRate", totalUnits == 0L ? 0 : BigDecimal.valueOf(usedUnits).divide(BigDecimal.valueOf(totalUnits), 4, RoundingMode.HALF_UP));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("room", this.toRow(room));
            result.put("stats", stats);
            result.put("racks", rackRows);
            return ApiResult.ok(result);
        }
    }

    private MachineRoomEntity selectByCode(String code) {
        return (MachineRoomEntity)this.machineRoomMapper.selectOne(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, code));
    }

    private static String inferRegionCode(String roomCode, String dataCenter) {
        if (roomCode != null) {
            String u = roomCode.trim().toUpperCase();
            if (u.startsWith("WLCB")) {
                return "WLCB";
            }

            if (u.startsWith("QY")) {
                return "QY";
            }

            if (u.startsWith("SG")) {
                return "SG";
            }
        }

        if (dataCenter != null) {
            if (dataCenter.contains("庆阳")) {
                return "QY";
            }

            if (dataCenter.contains("乌兰察布")) {
                return "WLCB";
            }

            if (dataCenter.contains("韶关")) {
                return "SG";
            }
        }

        return null;
    }

    private void applyUpdate(
        MachineRoomEntity e,
        String name,
        String dataCenter,
        String regionCode,
        String city,
        String location,
        BigDecimal areaSqm,
        BigDecimal loadKg,
        BigDecimal coolingKw,
        BigDecimal powerKw,
        Integer cabinetCount,
        String abPath,
        String envMonitor,
        String manager,
        String remark
    ) {
        if (name != null) {
            if (name.isBlank()) {
                throw BusinessException.badRequest("机房名称不能为空");
            }

            e.setName(name.trim());
        }

        e.setDataCenter(dataCenter);
        if (regionCode != null) {
            e.setRegionCode(regionCode.trim().isBlank() ? null : regionCode.trim());
        }

        e.setCity(city);
        e.setLocation(location);
        e.setAreaSqm(areaSqm);
        e.setLoadKg(loadKg);
        e.setCoolingKw(coolingKw);
        e.setPowerKw(powerKw);
        e.setCabinetCount(cabinetCount);
        e.setAbPath(abPath);
        e.setEnvMonitor(envMonitor);
        e.setManager(manager);
        e.setRemark(remark);
    }

    private Map<String, Object> toRow(MachineRoomEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roomCode", e.getRoomCode());
        row.put("name", e.getName());
        row.put("dataCenter", e.getDataCenter());
        row.put("regionCode", e.getRegionCode());
        row.put("city", e.getCity());
        row.put("location", e.getLocation());
        row.put("areaSqm", e.getAreaSqm());
        row.put("loadKg", e.getLoadKg());
        row.put("coolingKw", e.getCoolingKw());
        row.put("powerKw", e.getPowerKw());
        row.put("cabinetCount", e.getCabinetCount());
        row.put("abPath", e.getAbPath());
        row.put("envMonitor", e.getEnvMonitor());
        row.put("manager", e.getManager());
        row.put("remark", e.getRemark());
        row.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        return row;
    }

    
    public RoomController(
        final MachineRoomMapper machineRoomMapper, final RackMapper rackMapper, final RackUnitMapper rackUnitMapper, final TransactionTemplate rowTx
    ) {
        this.machineRoomMapper = machineRoomMapper;
        this.rackMapper = rackMapper;
        this.rackUnitMapper = rackUnitMapper;
        this.rowTx = rowTx;
    }

    public static class CreateRoomReq {
        @NotBlank(
            message = "机房编码不能为空"
        )
        private String roomCode;
        @NotBlank(
            message = "机房名称不能为空"
        )
        private String name;
        private String dataCenter;
        private String regionCode;
        private String city;
        private String location;
        private BigDecimal areaSqm;
        private BigDecimal loadKg;
        private BigDecimal coolingKw;
        private BigDecimal powerKw;
        private Integer cabinetCount;
        private String abPath;
        private String envMonitor;
        private String manager;
        private String remark;

        
        public String getRoomCode() {
            return this.roomCode;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getDataCenter() {
            return this.dataCenter;
        }

        
        public String getRegionCode() {
            return this.regionCode;
        }

        
        public String getCity() {
            return this.city;
        }

        
        public String getLocation() {
            return this.location;
        }

        
        public BigDecimal getAreaSqm() {
            return this.areaSqm;
        }

        
        public BigDecimal getLoadKg() {
            return this.loadKg;
        }

        
        public BigDecimal getCoolingKw() {
            return this.coolingKw;
        }

        
        public BigDecimal getPowerKw() {
            return this.powerKw;
        }

        
        public Integer getCabinetCount() {
            return this.cabinetCount;
        }

        
        public String getAbPath() {
            return this.abPath;
        }

        
        public String getEnvMonitor() {
            return this.envMonitor;
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

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setDataCenter(final String dataCenter) {
            this.dataCenter = dataCenter;
        }

        
        public void setRegionCode(final String regionCode) {
            this.regionCode = regionCode;
        }

        
        public void setCity(final String city) {
            this.city = city;
        }

        
        public void setLocation(final String location) {
            this.location = location;
        }

        
        public void setAreaSqm(final BigDecimal areaSqm) {
            this.areaSqm = areaSqm;
        }

        
        public void setLoadKg(final BigDecimal loadKg) {
            this.loadKg = loadKg;
        }

        
        public void setCoolingKw(final BigDecimal coolingKw) {
            this.coolingKw = coolingKw;
        }

        
        public void setPowerKw(final BigDecimal powerKw) {
            this.powerKw = powerKw;
        }

        
        public void setCabinetCount(final Integer cabinetCount) {
            this.cabinetCount = cabinetCount;
        }

        
        public void setAbPath(final String abPath) {
            this.abPath = abPath;
        }

        
        public void setEnvMonitor(final String envMonitor) {
            this.envMonitor = envMonitor;
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
            } else if (!(o instanceof RoomController.CreateRoomReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$cabinetCount = this.getCabinetCount();
                Object other$cabinetCount = other.getCabinetCount();
                if (this$cabinetCount == null ? other$cabinetCount == null : this$cabinetCount.equals(other$cabinetCount)) {
                    Object this$roomCode = this.getRoomCode();
                    Object other$roomCode = other.getRoomCode();
                    if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$dataCenter = this.getDataCenter();
                            Object other$dataCenter = other.getDataCenter();
                            if (this$dataCenter == null ? other$dataCenter == null : this$dataCenter.equals(other$dataCenter)) {
                                Object this$regionCode = this.getRegionCode();
                                Object other$regionCode = other.getRegionCode();
                                if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                    Object this$city = this.getCity();
                                    Object other$city = other.getCity();
                                    if (this$city == null ? other$city == null : this$city.equals(other$city)) {
                                        Object this$location = this.getLocation();
                                        Object other$location = other.getLocation();
                                        if (this$location == null ? other$location == null : this$location.equals(other$location)) {
                                            Object this$areaSqm = this.getAreaSqm();
                                            Object other$areaSqm = other.getAreaSqm();
                                            if (this$areaSqm == null ? other$areaSqm == null : this$areaSqm.equals(other$areaSqm)) {
                                                Object this$loadKg = this.getLoadKg();
                                                Object other$loadKg = other.getLoadKg();
                                                if (this$loadKg == null ? other$loadKg == null : this$loadKg.equals(other$loadKg)) {
                                                    Object this$coolingKw = this.getCoolingKw();
                                                    Object other$coolingKw = other.getCoolingKw();
                                                    if (this$coolingKw == null ? other$coolingKw == null : this$coolingKw.equals(other$coolingKw)) {
                                                        Object this$powerKw = this.getPowerKw();
                                                        Object other$powerKw = other.getPowerKw();
                                                        if (this$powerKw == null ? other$powerKw == null : this$powerKw.equals(other$powerKw)) {
                                                            Object this$abPath = this.getAbPath();
                                                            Object other$abPath = other.getAbPath();
                                                            if (this$abPath == null ? other$abPath == null : this$abPath.equals(other$abPath)) {
                                                                Object this$envMonitor = this.getEnvMonitor();
                                                                Object other$envMonitor = other.getEnvMonitor();
                                                                if (this$envMonitor == null
                                                                    ? other$envMonitor == null
                                                                    : this$envMonitor.equals(other$envMonitor)) {
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
            return other instanceof RoomController.CreateRoomReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $cabinetCount = this.getCabinetCount();
            result = result * 59 + ($cabinetCount == null ? 43 : $cabinetCount.hashCode());
            Object $roomCode = this.getRoomCode();
            result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $dataCenter = this.getDataCenter();
            result = result * 59 + ($dataCenter == null ? 43 : $dataCenter.hashCode());
            Object $regionCode = this.getRegionCode();
            result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
            Object $city = this.getCity();
            result = result * 59 + ($city == null ? 43 : $city.hashCode());
            Object $location = this.getLocation();
            result = result * 59 + ($location == null ? 43 : $location.hashCode());
            Object $areaSqm = this.getAreaSqm();
            result = result * 59 + ($areaSqm == null ? 43 : $areaSqm.hashCode());
            Object $loadKg = this.getLoadKg();
            result = result * 59 + ($loadKg == null ? 43 : $loadKg.hashCode());
            Object $coolingKw = this.getCoolingKw();
            result = result * 59 + ($coolingKw == null ? 43 : $coolingKw.hashCode());
            Object $powerKw = this.getPowerKw();
            result = result * 59 + ($powerKw == null ? 43 : $powerKw.hashCode());
            Object $abPath = this.getAbPath();
            result = result * 59 + ($abPath == null ? 43 : $abPath.hashCode());
            Object $envMonitor = this.getEnvMonitor();
            result = result * 59 + ($envMonitor == null ? 43 : $envMonitor.hashCode());
            Object $manager = this.getManager();
            result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "RoomController.CreateRoomReq(roomCode="
                + this.getRoomCode()
                + ", name="
                + this.getName()
                + ", dataCenter="
                + this.getDataCenter()
                + ", regionCode="
                + this.getRegionCode()
                + ", city="
                + this.getCity()
                + ", location="
                + this.getLocation()
                + ", areaSqm="
                + this.getAreaSqm()
                + ", loadKg="
                + this.getLoadKg()
                + ", coolingKw="
                + this.getCoolingKw()
                + ", powerKw="
                + this.getPowerKw()
                + ", cabinetCount="
                + this.getCabinetCount()
                + ", abPath="
                + this.getAbPath()
                + ", envMonitor="
                + this.getEnvMonitor()
                + ", manager="
                + this.getManager()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class UpdateRoomReq {
        private String name;
        private String dataCenter;
        private String regionCode;
        private String city;
        private String location;
        private BigDecimal areaSqm;
        private BigDecimal loadKg;
        private BigDecimal coolingKw;
        private BigDecimal powerKw;
        private Integer cabinetCount;
        private String abPath;
        private String envMonitor;
        private String manager;
        private String remark;

        
        public String getName() {
            return this.name;
        }

        
        public String getDataCenter() {
            return this.dataCenter;
        }

        
        public String getRegionCode() {
            return this.regionCode;
        }

        
        public String getCity() {
            return this.city;
        }

        
        public String getLocation() {
            return this.location;
        }

        
        public BigDecimal getAreaSqm() {
            return this.areaSqm;
        }

        
        public BigDecimal getLoadKg() {
            return this.loadKg;
        }

        
        public BigDecimal getCoolingKw() {
            return this.coolingKw;
        }

        
        public BigDecimal getPowerKw() {
            return this.powerKw;
        }

        
        public Integer getCabinetCount() {
            return this.cabinetCount;
        }

        
        public String getAbPath() {
            return this.abPath;
        }

        
        public String getEnvMonitor() {
            return this.envMonitor;
        }

        
        public String getManager() {
            return this.manager;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setDataCenter(final String dataCenter) {
            this.dataCenter = dataCenter;
        }

        
        public void setRegionCode(final String regionCode) {
            this.regionCode = regionCode;
        }

        
        public void setCity(final String city) {
            this.city = city;
        }

        
        public void setLocation(final String location) {
            this.location = location;
        }

        
        public void setAreaSqm(final BigDecimal areaSqm) {
            this.areaSqm = areaSqm;
        }

        
        public void setLoadKg(final BigDecimal loadKg) {
            this.loadKg = loadKg;
        }

        
        public void setCoolingKw(final BigDecimal coolingKw) {
            this.coolingKw = coolingKw;
        }

        
        public void setPowerKw(final BigDecimal powerKw) {
            this.powerKw = powerKw;
        }

        
        public void setCabinetCount(final Integer cabinetCount) {
            this.cabinetCount = cabinetCount;
        }

        
        public void setAbPath(final String abPath) {
            this.abPath = abPath;
        }

        
        public void setEnvMonitor(final String envMonitor) {
            this.envMonitor = envMonitor;
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
            } else if (!(o instanceof RoomController.UpdateRoomReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$cabinetCount = this.getCabinetCount();
                Object other$cabinetCount = other.getCabinetCount();
                if (this$cabinetCount == null ? other$cabinetCount == null : this$cabinetCount.equals(other$cabinetCount)) {
                    Object this$name = this.getName();
                    Object other$name = other.getName();
                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                        Object this$dataCenter = this.getDataCenter();
                        Object other$dataCenter = other.getDataCenter();
                        if (this$dataCenter == null ? other$dataCenter == null : this$dataCenter.equals(other$dataCenter)) {
                            Object this$regionCode = this.getRegionCode();
                            Object other$regionCode = other.getRegionCode();
                            if (this$regionCode == null ? other$regionCode == null : this$regionCode.equals(other$regionCode)) {
                                Object this$city = this.getCity();
                                Object other$city = other.getCity();
                                if (this$city == null ? other$city == null : this$city.equals(other$city)) {
                                    Object this$location = this.getLocation();
                                    Object other$location = other.getLocation();
                                    if (this$location == null ? other$location == null : this$location.equals(other$location)) {
                                        Object this$areaSqm = this.getAreaSqm();
                                        Object other$areaSqm = other.getAreaSqm();
                                        if (this$areaSqm == null ? other$areaSqm == null : this$areaSqm.equals(other$areaSqm)) {
                                            Object this$loadKg = this.getLoadKg();
                                            Object other$loadKg = other.getLoadKg();
                                            if (this$loadKg == null ? other$loadKg == null : this$loadKg.equals(other$loadKg)) {
                                                Object this$coolingKw = this.getCoolingKw();
                                                Object other$coolingKw = other.getCoolingKw();
                                                if (this$coolingKw == null ? other$coolingKw == null : this$coolingKw.equals(other$coolingKw)) {
                                                    Object this$powerKw = this.getPowerKw();
                                                    Object other$powerKw = other.getPowerKw();
                                                    if (this$powerKw == null ? other$powerKw == null : this$powerKw.equals(other$powerKw)) {
                                                        Object this$abPath = this.getAbPath();
                                                        Object other$abPath = other.getAbPath();
                                                        if (this$abPath == null ? other$abPath == null : this$abPath.equals(other$abPath)) {
                                                            Object this$envMonitor = this.getEnvMonitor();
                                                            Object other$envMonitor = other.getEnvMonitor();
                                                            if (this$envMonitor == null ? other$envMonitor == null : this$envMonitor.equals(other$envMonitor)) {
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
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof RoomController.UpdateRoomReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $cabinetCount = this.getCabinetCount();
            result = result * 59 + ($cabinetCount == null ? 43 : $cabinetCount.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $dataCenter = this.getDataCenter();
            result = result * 59 + ($dataCenter == null ? 43 : $dataCenter.hashCode());
            Object $regionCode = this.getRegionCode();
            result = result * 59 + ($regionCode == null ? 43 : $regionCode.hashCode());
            Object $city = this.getCity();
            result = result * 59 + ($city == null ? 43 : $city.hashCode());
            Object $location = this.getLocation();
            result = result * 59 + ($location == null ? 43 : $location.hashCode());
            Object $areaSqm = this.getAreaSqm();
            result = result * 59 + ($areaSqm == null ? 43 : $areaSqm.hashCode());
            Object $loadKg = this.getLoadKg();
            result = result * 59 + ($loadKg == null ? 43 : $loadKg.hashCode());
            Object $coolingKw = this.getCoolingKw();
            result = result * 59 + ($coolingKw == null ? 43 : $coolingKw.hashCode());
            Object $powerKw = this.getPowerKw();
            result = result * 59 + ($powerKw == null ? 43 : $powerKw.hashCode());
            Object $abPath = this.getAbPath();
            result = result * 59 + ($abPath == null ? 43 : $abPath.hashCode());
            Object $envMonitor = this.getEnvMonitor();
            result = result * 59 + ($envMonitor == null ? 43 : $envMonitor.hashCode());
            Object $manager = this.getManager();
            result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "RoomController.UpdateRoomReq(name="
                + this.getName()
                + ", dataCenter="
                + this.getDataCenter()
                + ", regionCode="
                + this.getRegionCode()
                + ", city="
                + this.getCity()
                + ", location="
                + this.getLocation()
                + ", areaSqm="
                + this.getAreaSqm()
                + ", loadKg="
                + this.getLoadKg()
                + ", coolingKw="
                + this.getCoolingKw()
                + ", powerKw="
                + this.getPowerKw()
                + ", cabinetCount="
                + this.getCabinetCount()
                + ", abPath="
                + this.getAbPath()
                + ", envMonitor="
                + this.getEnvMonitor()
                + ", manager="
                + this.getManager()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }
}
