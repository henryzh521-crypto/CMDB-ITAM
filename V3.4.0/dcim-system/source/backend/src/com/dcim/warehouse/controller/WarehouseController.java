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
import com.dcim.room.entity.MachineRoomEntity;
import com.dcim.room.mapper.MachineRoomMapper;
import com.dcim.security.RequirePerm;
import com.dcim.system.audit.Audit;
import com.dcim.system.entity.DictEntity;
import com.dcim.system.mapper.DictMapper;
import com.dcim.warehouse.entity.WarehouseEntity;
import com.dcim.warehouse.entity.WarehouseLayerEntity;
import com.dcim.warehouse.entity.WarehouseShelfEntity;
import com.dcim.warehouse.mapper.WarehouseLayerMapper;
import com.dcim.warehouse.mapper.WarehouseMapper;
import com.dcim.warehouse.mapper.WarehouseShelfMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/warehouses"})
public class WarehouseController {
    private static final String ST_ACTIVE = "正常";
    private static final Pattern SEQ_CODE = Pattern.compile("^(WH|SH)-0*(\\d+)$");
    private final WarehouseMapper warehouseMapper;
    private final WarehouseShelfMapper shelfMapper;
    private final WarehouseLayerMapper layerMapper;
    private final MachineRoomMapper machineRoomMapper;
    private final SeqNoGenerator seqNoGenerator;
    private final DictMapper dictMapper;
    private final JdbcTemplate jdbc;

    @GetMapping
    @RequirePerm(
        module = "warehouses"
    )
    public ApiResult<PageResult<Map<String, Object>>> list(
        @RequestParam(defaultValue = "1") long page,
        @RequestParam(defaultValue = "20") long size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String status
    ) {
        LambdaQueryWrapper<WarehouseEntity> qw = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(
                w -> ((((w.like(WarehouseEntity::getWarehouseCode, kw)).or())
                                .like(WarehouseEntity::getName, kw))
                            .or())
                        .like(WarehouseEntity::getManager, kw)
            );
        }

        if (status != null && !status.isBlank()) {
            qw.eq(WarehouseEntity::getStatus, status.trim());
        }

        qw.orderByAsc(WarehouseEntity::getWarehouseCode);
        Page<WarehouseEntity> p = new Page(page, size);
        this.warehouseMapper.selectPage(p, qw);
        List<String> codes = p.getRecords().stream().map(WarehouseEntity::getWarehouseCode).toList();
        Map<String, long[]> agg = this.loadAggregate(codes);
        return ApiResult.ok(PageResult.of(p, w -> this.toRow(w, agg.getOrDefault(w.getWarehouseCode(), new long[4]))));
    }

    @GetMapping({"/meta"})
    @RequirePerm(
        module = "warehouses"
    )
    public ApiResult<Map<String, Object>> meta() {
        List<DictEntity> dicts = this.dictMapper
            .selectList(
                ((Wrappers.<DictEntity>lambdaQuery().eq(DictEntity::getDictType, "warehouse_type"))
                        .eq(DictEntity::getStatus, 1))
                    .orderByAsc(DictEntity::getSort)
            );
        List<String> types = new ArrayList<>();

        for (DictEntity d : dicts) {
            types.add(d.getDictKey());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("warehouseTypes", types);
        meta.put("statuses", List.of("正常", "停用"));
        meta.put("shelfTypes", List.of("标准货架", "重力货架", "悬臂货架", "工作台", "周转架"));
        meta.put("layerStatuses", List.of("正常", "停用", "锁定"));
        meta.put("codeRules", Map.of("warehouse", "WH-001", "shelf", "SH-001", "layer", "LY-001（自动生成）"));
        return ApiResult.ok(meta);
    }

    @GetMapping({"/options"})
    @RequirePerm(
        module = "warehouses"
    )
    public ApiResult<List<Map<String, Object>>> options(@RequestParam(required = false) Boolean withDisabled) {
        LambdaQueryWrapper<WarehouseEntity> qw = (LambdaQueryWrapper<WarehouseEntity>)(Wrappers.<WarehouseEntity>lambdaQuery()
                .orderByDesc(WarehouseEntity::getIsDefault))
            .orderByAsc(WarehouseEntity::getWarehouseCode);
        if (withDisabled == null || !withDisabled) {
            qw.eq(WarehouseEntity::getStatus, "正常");
        }

        List<Map<String, Object>> out = new ArrayList<>();

        for (WarehouseEntity w : this.warehouseMapper.selectList(qw)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("warehouseCode", w.getWarehouseCode());
            row.put("name", w.getName());
            row.put("isDefault", Boolean.TRUE.equals(w.getIsDefault()));
            row.put("status", w.getStatus());
            out.add(row);
        }

        return ApiResult.ok(out);
    }

    @PostMapping
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "warehouses",
        objType = "warehouse",
        objIdSpEL = "#req.warehouseCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> create(@RequestBody @Valid WarehouseController.CreateWarehouseReq req) {
        String code = this.resolveCode(req.getWarehouseCode(), "WH");
        if (this.selectByCode(code) != null) {
            throw BusinessException.conflict("仓库编号已存在：" + code);
        } else {
            String roomCode = this.blankToNull(req.getRoomCode());
            if (roomCode != null) {
                this.requireRoom(roomCode);
            }

            boolean asDefault = Boolean.TRUE.equals(req.getIsDefault());
            if (asDefault) {
                this.clearDefault();
            }

            WarehouseEntity e = new WarehouseEntity();
            e.setWarehouseCode(code);
            e.setName(req.getName().trim());
            e.setWarehouseType(this.blankToNull(req.getWarehouseType()));
            e.setRoomCode(roomCode);
            e.setAddress(this.blankToNull(req.getAddress()));
            e.setManager(this.blankToNull(req.getManager()));
            e.setCapacityDesc(this.blankToNull(req.getCapacityDesc()));
            e.setStatus(req.getStatus() != null && !req.getStatus().isBlank() ? req.getStatus().trim() : "正常");
            e.setIsDefault(asDefault);
            e.setRemark(this.blankToNull(req.getRemark()));
            e.setVersion(1);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            this.warehouseMapper.insert(e);
            return ApiResult.ok(this.toRow(e, new long[4]));
        }
    }

    @PutMapping({"/{warehouseCode}"})
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "warehouses",
        objType = "warehouse",
        objIdSpEL = "#warehouseCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> update(@PathVariable String warehouseCode, @RequestBody @Valid WarehouseController.UpdateWarehouseReq req) {
        WarehouseEntity e = this.selectByCode(warehouseCode.trim());
        if (e == null) {
            throw BusinessException.notFound("仓库不存在：" + warehouseCode);
        } else {
            if (req.getRoomCode() != null) {
                String roomCode = this.blankToNull(req.getRoomCode());
                if (roomCode != null) {
                    this.requireRoom(roomCode);
                }

                e.setRoomCode(roomCode);
            }

            boolean wantDefault = Boolean.TRUE.equals(req.getIsDefault());
            if (wantDefault && !Boolean.TRUE.equals(e.getIsDefault())) {
                this.clearDefault();
            }

            if (!wantDefault && Boolean.TRUE.equals(e.getIsDefault())) {
                throw BusinessException.badRequest("必须保留一个默认仓，请先指定其他默认仓");
            } else {
                if (req.getName() != null && !req.getName().isBlank()) {
                    e.setName(req.getName().trim());
                }

                if (req.getWarehouseType() != null) {
                    e.setWarehouseType(this.blankToNull(req.getWarehouseType()));
                }

                if (req.getAddress() != null) {
                    e.setAddress(this.blankToNull(req.getAddress()));
                }

                if (req.getManager() != null) {
                    e.setManager(this.blankToNull(req.getManager()));
                }

                if (req.getCapacityDesc() != null) {
                    e.setCapacityDesc(this.blankToNull(req.getCapacityDesc()));
                }

                if (req.getStatus() != null && !req.getStatus().isBlank()) {
                    e.setStatus(req.getStatus().trim());
                }

                if (req.getRemark() != null) {
                    e.setRemark(this.blankToNull(req.getRemark()));
                }

                e.setIsDefault(wantDefault);
                e.setUpdatedAt(LocalDateTime.now());
                LambdaUpdateWrapper<WarehouseEntity> uw = (LambdaUpdateWrapper<WarehouseEntity>)Wrappers.<WarehouseEntity>lambdaUpdate().eq(WarehouseEntity::getId, e.getId());
                if (req.getVersion() != null) {
                    uw.eq(WarehouseEntity::getVersion, req.getVersion());
                }

                ((((((((((uw.set(
                                                            WarehouseEntity::getName, e.getName()
                                                        ))
                                                        .set(WarehouseEntity::getWarehouseType, e.getWarehouseType()))
                                                    .set(WarehouseEntity::getRoomCode, e.getRoomCode()))
                                                .set(WarehouseEntity::getAddress, e.getAddress()))
                                            .set(WarehouseEntity::getManager, e.getManager()))
                                        .set(WarehouseEntity::getCapacityDesc, e.getCapacityDesc()))
                                    .set(WarehouseEntity::getStatus, e.getStatus()))
                                .set(WarehouseEntity::getIsDefault, e.getIsDefault()))
                            .set(WarehouseEntity::getRemark, e.getRemark()))
                        .set(WarehouseEntity::getUpdatedAt, e.getUpdatedAt()))
                    .setSql("version = version + 1", new Object[0]);
                int rows = this.warehouseMapper.update(null, uw);
                if (rows == 0) {
                    throw BusinessException.conflict("仓库已被他人修改，请刷新后重试");
                } else {
                    e.setVersion((e.getVersion() == null ? 1 : e.getVersion()) + 1);
                    return ApiResult.ok(this.toRow(e, new long[4]));
                }
            }
        }
    }

    @DeleteMapping({"/{warehouseCode}"})
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "warehouses",
        objType = "warehouse",
        objIdSpEL = "#warehouseCode"
    )
    @Transactional
    public ApiResult<Void> delete(@PathVariable String warehouseCode) {
        WarehouseEntity e = this.selectByCode(warehouseCode.trim());
        if (e == null) {
            throw BusinessException.notFound("仓库不存在：" + warehouseCode);
        } else if (Boolean.TRUE.equals(e.getIsDefault())) {
            throw BusinessException.conflict("默认仓不可删除，请先将其他仓库设为默认仓");
        } else {
            Long shelves = this.shelfMapper.selectCount(Wrappers.<WarehouseShelfEntity>lambdaQuery().eq(WarehouseShelfEntity::getWarehouseCode, e.getWarehouseCode()));
            if (shelves != null && shelves > 0L) {
                throw BusinessException.conflict("仓库下存在 " + shelves + " 个货架，不允许删除");
            } else {
                Long materials = (Long)this.jdbc
                    .queryForObject("SELECT COUNT(*) FROM material WHERE warehouse_code = ?", Long.class, new Object[]{e.getWarehouseCode()});
                if (materials != null && materials > 0L) {
                    throw BusinessException.conflict("仓库已被 " + materials + " 个物料引用为默认仓库，不允许删除");
                } else {
                    Long levels = (Long)this.jdbc
                        .queryForObject("SELECT COUNT(*) FROM stock_level WHERE warehouse_code = ?", Long.class, new Object[]{e.getWarehouseCode()});
                    if (levels != null && levels > 0L) {
                        throw BusinessException.conflict("仓库存在 " + levels + " 条分仓库存记录，不允许删除");
                    } else {
                        this.warehouseMapper.deleteById(e.getId());
                        return ApiResult.ok();
                    }
                }
            }
        }
    }

    @GetMapping({"/{warehouseCode}/shelves"})
    @RequirePerm(
        module = "warehouses"
    )
    public ApiResult<List<Map<String, Object>>> shelves(@PathVariable String warehouseCode) {
        WarehouseEntity w = this.selectByCode(warehouseCode.trim());
        if (w == null) {
            throw BusinessException.notFound("仓库不存在：" + warehouseCode);
        } else {
            List<WarehouseShelfEntity> list = this.shelfMapper
                .selectList(
                    (Wrappers.<WarehouseShelfEntity>lambdaQuery().eq(WarehouseShelfEntity::getWarehouseCode, w.getWarehouseCode()))
                        .orderByAsc(WarehouseShelfEntity::getShelfCode)
                );
            Map<String, List<Map<String, Object>>> layersByShelf = this.loadLayers(list);
            List<Map<String, Object>> out = new ArrayList<>();

            for (WarehouseShelfEntity s : list) {
                Map<String, Object> row = this.toShelfRow(s);
                List<Map<String, Object>> layers = layersByShelf.getOrDefault(s.getShelfCode(), new ArrayList<>());
                row.put("layers", layers);
                row.put("layerTotal", layers.size());
                out.add(row);
            }

            return ApiResult.ok(out);
        }
    }

    @PostMapping({"/{warehouseCode}/shelves"})
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "warehouses",
        objType = "warehouse_shelf",
        objIdSpEL = "#req.shelfCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> createShelf(@PathVariable String warehouseCode, @RequestBody @Valid WarehouseController.CreateShelfReq req) {
        WarehouseEntity w = this.selectByCode(warehouseCode.trim());
        if (w == null) {
            throw BusinessException.notFound("仓库不存在：" + warehouseCode);
        } else {
            String code = this.resolveCode(req.getShelfCode(), "SH");
            if (this.selectShelfByCode(code) != null) {
                throw BusinessException.conflict("货架编号已存在：" + code);
            } else {
                int layers = req.getLayerCount() == null ? 4 : req.getLayerCount();
                if (layers < 1) {
                    throw BusinessException.badRequest("层数必须为正整数");
                } else {
                    WarehouseShelfEntity e = new WarehouseShelfEntity();
                    e.setShelfCode(code);
                    e.setWarehouseCode(w.getWarehouseCode());
                    e.setName(this.blankToNull(req.getName()));
                    e.setShelfType(req.getShelfType() != null && !req.getShelfType().isBlank() ? req.getShelfType().trim() : "标准货架");
                    e.setLayerCount(layers);
                    e.setStatus(req.getStatus() != null && !req.getStatus().isBlank() ? req.getStatus().trim() : "正常");
                    e.setRemark(this.blankToNull(req.getRemark()));
                    e.setVersion(1);
                    e.setCreatedAt(LocalDateTime.now());
                    e.setUpdatedAt(LocalDateTime.now());
                    this.shelfMapper.insert(e);
                    this.insertLayers(code, 1, layers);
                    Map<String, Object> row = this.toShelfRow(e);
                    row.put("layers", this.layersOf(code));
                    return ApiResult.ok(row);
                }
            }
        }
    }

    @PutMapping({"/shelves/{shelfCode}"})
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "warehouses",
        objType = "warehouse_shelf",
        objIdSpEL = "#shelfCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> updateShelf(@PathVariable String shelfCode, @RequestBody @Valid WarehouseController.UpdateShelfReq req) {
        WarehouseShelfEntity e = this.selectShelfByCode(shelfCode.trim());
        if (e == null) {
            throw BusinessException.notFound("货架不存在：" + shelfCode);
        } else {
            if (req.getLayerCount() != null) {
                this.resizeLayers(e, req.getLayerCount());
            }

            if (req.getName() != null) {
                e.setName(this.blankToNull(req.getName()));
            }

            if (req.getShelfType() != null && !req.getShelfType().isBlank()) {
                e.setShelfType(req.getShelfType().trim());
            }

            if (req.getStatus() != null && !req.getStatus().isBlank()) {
                e.setStatus(req.getStatus().trim());
            }

            if (req.getRemark() != null) {
                e.setRemark(this.blankToNull(req.getRemark()));
            }

            e.setUpdatedAt(LocalDateTime.now());
            LambdaUpdateWrapper<WarehouseShelfEntity> uw = (LambdaUpdateWrapper<WarehouseShelfEntity>)Wrappers.<WarehouseShelfEntity>lambdaUpdate()
                .eq(WarehouseShelfEntity::getId, e.getId());
            if (req.getVersion() != null) {
                uw.eq(WarehouseShelfEntity::getVersion, req.getVersion());
            }

            ((((((uw.set(
                                        WarehouseShelfEntity::getName, e.getName()
                                    ))
                                    .set(WarehouseShelfEntity::getShelfType, e.getShelfType()))
                                .set(WarehouseShelfEntity::getLayerCount, e.getLayerCount()))
                            .set(WarehouseShelfEntity::getStatus, e.getStatus()))
                        .set(WarehouseShelfEntity::getRemark, e.getRemark()))
                    .set(WarehouseShelfEntity::getUpdatedAt, e.getUpdatedAt()))
                .setSql("version = version + 1", new Object[0]);
            int rows = this.shelfMapper.update(null, uw);
            if (rows == 0) {
                throw BusinessException.conflict("货架已被他人修改，请刷新后重试");
            } else {
                e.setVersion((e.getVersion() == null ? 1 : e.getVersion()) + 1);
                Map<String, Object> row = this.toShelfRow(e);
                row.put("layers", this.layersOf(e.getShelfCode()));
                return ApiResult.ok(row);
            }
        }
    }

    @DeleteMapping({"/shelves/{shelfCode}"})
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "warehouses",
        objType = "warehouse_shelf",
        objIdSpEL = "#shelfCode"
    )
    @Transactional
    public ApiResult<Void> deleteShelf(@PathVariable String shelfCode) {
        WarehouseShelfEntity e = this.selectShelfByCode(shelfCode.trim());
        if (e == null) {
            throw BusinessException.notFound("货架不存在：" + shelfCode);
        } else {
            Long refs = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE shelf_code = ?", Long.class, new Object[]{e.getShelfCode()});
            if (refs != null && refs > 0L) {
                throw BusinessException.conflict("货架被 " + refs + " 个物料引用为默认货架，不允许删除");
            } else {
                this.jdbc.update("DELETE FROM warehouse_layer WHERE shelf_code = ?", new Object[]{e.getShelfCode()});
                this.shelfMapper.deleteById(e.getId());
                return ApiResult.ok();
            }
        }
    }

    @GetMapping({"/layers/options"})
    @RequirePerm(
        module = "warehouses"
    )
    public ApiResult<List<Map<String, Object>>> layerOptions(@RequestParam(required = false) String shelfCode) {
        LambdaQueryWrapper<WarehouseLayerEntity> qw = (LambdaQueryWrapper<WarehouseLayerEntity>)Wrappers.<WarehouseLayerEntity>lambdaQuery()
            .orderByAsc(WarehouseLayerEntity::getLayerNo);
        if (shelfCode != null && !shelfCode.isBlank()) {
            qw.eq(WarehouseLayerEntity::getShelfCode, shelfCode.trim());
        }

        List<Map<String, Object>> out = new ArrayList<>();

        for (WarehouseLayerEntity l : this.layerMapper.selectList(qw)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("layerCode", l.getLayerCode());
            row.put("shelfCode", l.getShelfCode());
            row.put("layerNo", l.getLayerNo());
            row.put("name", l.getName());
            row.put("status", l.getStatus());
            out.add(row);
        }

        return ApiResult.ok(out);
    }

    @PostMapping({"/shelves/{shelfCode}/layers"})
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "create"
    )
    @Audit(
        action = "CREATE",
        module = "warehouses",
        objType = "warehouse_layer"
    )
    @Transactional
    public ApiResult<Map<String, Object>> createLayer(@PathVariable String shelfCode, @RequestBody(required = false) WarehouseController.CreateLayerReq req) {
        WarehouseShelfEntity shelf = this.selectShelfByCode(shelfCode.trim());
        if (shelf == null) {
            throw BusinessException.notFound("货架不存在：" + shelfCode);
        } else {
            Integer maxNo = (Integer)this.jdbc
                .queryForObject(
                    "SELECT COALESCE(MAX(layer_no), 0) FROM warehouse_layer WHERE shelf_code = ?", Integer.class, new Object[]{shelf.getShelfCode()}
                );
            int layerNo = (maxNo == null ? 0 : maxNo) + 1;
            WarehouseLayerEntity e = new WarehouseLayerEntity();
            e.setLayerCode(this.seqNoGenerator.next("LY", 3));
            e.setShelfCode(shelf.getShelfCode());
            e.setLayerNo(layerNo);
            e.setName(req == null ? null : this.blankToNull(req.getName()));
            e.setCapacityDesc(req == null ? null : this.blankToNull(req.getCapacityDesc()));
            e.setStatus(req != null && req.getStatus() != null && !req.getStatus().isBlank() ? req.getStatus().trim() : "正常");
            e.setRemark(req == null ? null : this.blankToNull(req.getRemark()));
            e.setVersion(1);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            this.layerMapper.insert(e);
            this.jdbc.update("UPDATE warehouse_shelf SET layer_count = layer_count + 1, updated_at = now() WHERE id = ?", new Object[]{shelf.getId()});
            return ApiResult.ok(this.toLayerRow(e));
        }
    }

    @PutMapping({"/layers/{layerCode}"})
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "update"
    )
    @Audit(
        action = "UPDATE",
        module = "warehouses",
        objType = "warehouse_layer",
        objIdSpEL = "#layerCode"
    )
    @Transactional
    public ApiResult<Map<String, Object>> updateLayer(@PathVariable String layerCode, @RequestBody @Valid WarehouseController.UpdateLayerReq req) {
        WarehouseLayerEntity e = this.selectLayerByCode(layerCode.trim());
        if (e == null) {
            throw BusinessException.notFound("层位不存在：" + layerCode);
        } else {
            if (req.getName() != null) {
                e.setName(this.blankToNull(req.getName()));
            }

            if (req.getCapacityDesc() != null) {
                e.setCapacityDesc(this.blankToNull(req.getCapacityDesc()));
            }

            if (req.getStatus() != null && !req.getStatus().isBlank()) {
                e.setStatus(req.getStatus().trim());
            }

            if (req.getRemark() != null) {
                e.setRemark(this.blankToNull(req.getRemark()));
            }

            e.setUpdatedAt(LocalDateTime.now());
            LambdaUpdateWrapper<WarehouseLayerEntity> uw = (LambdaUpdateWrapper<WarehouseLayerEntity>)Wrappers.<WarehouseLayerEntity>lambdaUpdate()
                .eq(WarehouseLayerEntity::getId, e.getId());
            if (req.getVersion() != null) {
                uw.eq(WarehouseLayerEntity::getVersion, req.getVersion());
            }

            (((((uw.set(
                                    WarehouseLayerEntity::getName, e.getName()
                                ))
                                .set(WarehouseLayerEntity::getCapacityDesc, e.getCapacityDesc()))
                            .set(WarehouseLayerEntity::getStatus, e.getStatus()))
                        .set(WarehouseLayerEntity::getRemark, e.getRemark()))
                    .set(WarehouseLayerEntity::getUpdatedAt, e.getUpdatedAt()))
                .setSql("version = version + 1", new Object[0]);
            if (this.layerMapper.update(null, uw) == 0) {
                throw BusinessException.conflict("层位已被他人修改，请刷新后重试");
            } else {
                e.setVersion((e.getVersion() == null ? 1 : e.getVersion()) + 1);
                return ApiResult.ok(this.toLayerRow(e));
            }
        }
    }

    @DeleteMapping({"/layers/{layerCode}"})
    @RequirePerm(
        module = "warehouses",
        write = true,
        action = "delete"
    )
    @Audit(
        action = "DELETE",
        module = "warehouses",
        objType = "warehouse_layer",
        objIdSpEL = "#layerCode"
    )
    @Transactional
    public ApiResult<Void> deleteLayer(@PathVariable String layerCode) {
        WarehouseLayerEntity e = this.selectLayerByCode(layerCode.trim());
        if (e == null) {
            throw BusinessException.notFound("层位不存在：" + layerCode);
        } else {
            Long refs = (Long)this.jdbc.queryForObject("SELECT COUNT(*) FROM material WHERE layer_code = ?", Long.class, new Object[]{e.getLayerCode()});
            if (refs != null && refs > 0L) {
                throw BusinessException.conflict("层位被 " + refs + " 个物料引用，不允许删除");
            } else {
                this.layerMapper.deleteById(e.getId());
                this.jdbc
                    .update(
                        "UPDATE warehouse_shelf SET layer_count = GREATEST(layer_count - 1, 1), updated_at = now() WHERE id = (SELECT id FROM warehouse_shelf WHERE shelf_code = ?)",
                        new Object[]{e.getShelfCode()}
                    );
                return ApiResult.ok();
            }
        }
    }

    @GetMapping({"/{warehouseCode}/stock"})
    @RequirePerm(
        module = "warehouses"
    )
    public ApiResult<List<Map<String, Object>>> stock(@PathVariable String warehouseCode) {
        WarehouseEntity w = this.selectByCode(warehouseCode.trim());
        if (w == null) {
            throw BusinessException.notFound("仓库不存在：" + warehouseCode);
        } else {
            List<Map<String, Object>> rows = this.jdbc
                .queryForList(
                    "SELECT sl.material_code AS \"materialCode\", m.name AS \"name\", m.category AS \"category\", m.unit AS \"unit\", sl.qty AS \"qty\", m.safety_stock AS \"safetyStock\", m.max_stock AS \"maxStock\", m.price AS \"price\", m.warehouse_code AS \"defaultWarehouse\", m.shelf_code AS \"shelfCode\", m.layer_code AS \"layerCode\" FROM stock_level sl JOIN material m ON m.material_code = sl.material_code WHERE sl.warehouse_code = ? ORDER BY sl.material_code",
                    new Object[]{w.getWarehouseCode()}
                );

            for (Map<String, Object> r : rows) {
                int qty = r.get("qty") == null ? 0 : ((Number)r.get("qty")).intValue();
                Integer safety = r.get("safetyStock") == null ? null : ((Number)r.get("safetyStock")).intValue();
                r.put("alert", qty == 0 ? "OUT" : (safety != null && qty <= safety ? "LOW" : null));
            }

            return ApiResult.ok(rows);
        }
    }

    private WarehouseEntity selectByCode(String code) {
        return (WarehouseEntity)this.warehouseMapper.selectOne(Wrappers.<WarehouseEntity>lambdaQuery().eq(WarehouseEntity::getWarehouseCode, code));
    }

    private WarehouseShelfEntity selectShelfByCode(String code) {
        return (WarehouseShelfEntity)this.shelfMapper.selectOne(Wrappers.<WarehouseShelfEntity>lambdaQuery().eq(WarehouseShelfEntity::getShelfCode, code));
    }

    private WarehouseLayerEntity selectLayerByCode(String code) {
        return (WarehouseLayerEntity)this.layerMapper.selectOne(Wrappers.<WarehouseLayerEntity>lambdaQuery().eq(WarehouseLayerEntity::getLayerCode, code));
    }

    private void requireRoom(String roomCode) {
        MachineRoomEntity room = (MachineRoomEntity)this.machineRoomMapper
            .selectOne(Wrappers.<MachineRoomEntity>lambdaQuery().eq(MachineRoomEntity::getRoomCode, roomCode));
        if (room == null) {
            throw BusinessException.badRequest("机房不存在：" + roomCode);
        }
    }

    private void clearDefault() {
        this.jdbc.update("UPDATE warehouse SET is_default = FALSE, updated_at = now() WHERE is_default");
    }

    private String resolveCode(String raw, String prefix) {
        if (raw != null && !raw.isBlank()) {
            String code = raw.trim();
            Matcher m = SEQ_CODE.matcher(code);
            if (m.matches() && prefix.equals(m.group(1))) {
                this.seqNoGenerator.ensureAtLeast(prefix, Long.parseLong(m.group(2)) + 1L);
            }

            return code;
        } else {
            return this.seqNoGenerator.next(prefix, 3);
        }
    }

    private void insertLayers(String shelfCode, int from, int to) {
        for (int i = from; i <= to; i++) {
            Long exists = (Long)this.jdbc
                .queryForObject("SELECT COUNT(*) FROM warehouse_layer WHERE shelf_code = ? AND layer_no = ?", Long.class, new Object[]{shelfCode, i});
            if (exists == null || exists <= 0L) {
                String layerCode = this.seqNoGenerator.next("LY", 3);
                this.jdbc
                    .update(
                        "INSERT INTO warehouse_layer (layer_code, shelf_code, layer_no) VALUES (?, ?, ?) ON CONFLICT (shelf_code, layer_no) DO NOTHING",
                        new Object[]{layerCode, shelfCode, i}
                    );
            }
        }

        this.jdbc.update("UPDATE warehouse_shelf SET layer_count = ?, updated_at = now() WHERE shelf_code = ?", new Object[]{to, shelfCode});
    }

    private void resizeLayers(WarehouseShelfEntity shelf, int newCount) {
        if (newCount < 1) {
            throw BusinessException.badRequest("层数必须为正整数");
        } else {
            int oldCount = shelf.getLayerCount() == null ? 0 : shelf.getLayerCount();
            if (newCount > oldCount) {
                this.insertLayers(shelf.getShelfCode(), 1, newCount);
                shelf.setLayerCount(newCount);
            } else {
                if (newCount < oldCount) {
                    Long used = (Long)this.jdbc
                        .queryForObject(
                            "SELECT COUNT(*) FROM material WHERE layer_code IN (SELECT layer_code FROM warehouse_layer WHERE shelf_code = ? AND layer_no > ?)",
                            Long.class,
                            new Object[]{shelf.getShelfCode(), newCount}
                        );
                    if (used != null && used > 0L) {
                        throw BusinessException.conflict("缩容后将删除的层位仍被 " + used + " 个物料引用，不允许缩容");
                    }

                    this.jdbc.update("DELETE FROM warehouse_layer WHERE shelf_code = ? AND layer_no > ?", new Object[]{shelf.getShelfCode(), newCount});
                    shelf.setLayerCount(newCount);
                }
            }
        }
    }

    private List<Map<String, Object>> layersOf(String shelfCode) {
        List<WarehouseLayerEntity> list = this.layerMapper
            .selectList(
                (Wrappers.<WarehouseLayerEntity>lambdaQuery().eq(WarehouseLayerEntity::getShelfCode, shelfCode))
                    .orderByAsc(WarehouseLayerEntity::getLayerNo)
            );
        List<Map<String, Object>> out = new ArrayList<>();

        for (WarehouseLayerEntity l : list) {
            out.add(this.toLayerRow(l));
        }

        return out;
    }

    private Map<String, List<Map<String, Object>>> loadLayers(List<WarehouseShelfEntity> shelves) {
        Map<String, List<Map<String, Object>>> out = new HashMap<>();
        if (shelves != null && !shelves.isEmpty()) {
            List<String> codes = shelves.stream().map(WarehouseShelfEntity::getShelfCode).toList();
            String in = String.join(",", Collections.nCopies(codes.size(), "?"));

            for (Map<String, Object> r : this.jdbc
                .queryForList(
                    "SELECT l.layer_code AS \"layerCode\", l.shelf_code AS \"shelfCode\", l.layer_no AS \"layerNo\", l.name AS \"name\", l.capacity_desc AS \"capacityDesc\", l.status AS \"status\", l.version AS \"version\", (SELECT COUNT(*) FROM material m WHERE m.layer_code = l.layer_code) AS \"materialCount\" FROM warehouse_layer l WHERE l.shelf_code IN ("
                        + in
                        + ") ORDER BY l.layer_no",
                    codes.toArray()
                )) {
                out.computeIfAbsent(String.valueOf(r.get("shelfCode")), k -> new ArrayList<>()).add(r);
            }

            return out;
        } else {
            return out;
        }
    }

    private Map<String, long[]> loadAggregate(List<String> codes) {
        Map<String, long[]> out = new HashMap<>();
        if (codes != null && !codes.isEmpty()) {
            for (String c : codes) {
                out.put(c, new long[4]);
            }

            String in = String.join(",", Collections.nCopies(codes.size(), "?"));

            for (Map<String, Object> r : this.jdbc
                .queryForList(
                    "SELECT warehouse_code AS \"c\", COUNT(*) AS \"n\" FROM warehouse_shelf WHERE warehouse_code IN (" + in + ") GROUP BY warehouse_code",
                    codes.toArray()
                )) {
                out.get(String.valueOf(r.get("c")))[0] = ((Number)r.get("n")).longValue();
            }

            for (Map<String, Object> r : this.jdbc
                .queryForList(
                    "SELECT w.warehouse_code AS \"c\", COUNT(l.id) AS \"n\" FROM warehouse w JOIN warehouse_shelf s ON s.warehouse_code = w.warehouse_code LEFT JOIN warehouse_layer l ON l.shelf_code = s.shelf_code WHERE w.warehouse_code IN ("
                        + in
                        + ") GROUP BY w.warehouse_code",
                    codes.toArray()
                )) {
                out.get(String.valueOf(r.get("c")))[1] = ((Number)r.get("n")).longValue();
            }

            for (Map<String, Object> r : this.jdbc
                .queryForList(
                    "SELECT warehouse_code AS \"c\", COUNT(*) AS \"n\" FROM material WHERE warehouse_code IN (" + in + ") GROUP BY warehouse_code",
                    codes.toArray()
                )) {
                out.get(String.valueOf(r.get("c")))[2] = ((Number)r.get("n")).longValue();
            }

            for (Map<String, Object> r : this.jdbc
                .queryForList(
                    "SELECT warehouse_code AS \"c\", COALESCE(SUM(qty), 0) AS \"n\" FROM stock_level WHERE warehouse_code IN ("
                        + in
                        + ") GROUP BY warehouse_code",
                    codes.toArray()
                )) {
                out.get(String.valueOf(r.get("c")))[3] = ((Number)r.get("n")).longValue();
            }

            return out;
        } else {
            return out;
        }
    }

    private Map<String, Object> toRow(WarehouseEntity e, long[] agg) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("warehouseCode", e.getWarehouseCode());
        row.put("name", e.getName());
        row.put("warehouseType", e.getWarehouseType());
        row.put("roomCode", e.getRoomCode());
        row.put("address", e.getAddress());
        row.put("manager", e.getManager());
        row.put("capacityDesc", e.getCapacityDesc());
        row.put("status", e.getStatus());
        row.put("isDefault", Boolean.TRUE.equals(e.getIsDefault()));
        row.put("remark", e.getRemark());
        row.put("version", e.getVersion());
        row.put("shelfCount", agg[0]);
        row.put("layerCount", agg[1]);
        row.put("materialCount", agg[2]);
        row.put("stockQty", agg[3]);
        row.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        row.put("updatedAt", e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> toShelfRow(WarehouseShelfEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shelfCode", e.getShelfCode());
        row.put("warehouseCode", e.getWarehouseCode());
        row.put("name", e.getName());
        row.put("shelfType", e.getShelfType());
        row.put("layerCount", e.getLayerCount());
        row.put("status", e.getStatus());
        row.put("remark", e.getRemark());
        row.put("version", e.getVersion());
        row.put("createdAt", e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        row.put("updatedAt", e.getUpdatedAt() == null ? "" : e.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> toLayerRow(WarehouseLayerEntity e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("layerCode", e.getLayerCode());
        row.put("shelfCode", e.getShelfCode());
        row.put("layerNo", e.getLayerNo());
        row.put("name", e.getName());
        row.put("capacityDesc", e.getCapacityDesc());
        row.put("status", e.getStatus());
        row.put("remark", e.getRemark());
        row.put("version", e.getVersion());
        return row;
    }

    private String blankToNull(String s) {
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    
    public WarehouseController(
        final WarehouseMapper warehouseMapper,
        final WarehouseShelfMapper shelfMapper,
        final WarehouseLayerMapper layerMapper,
        final MachineRoomMapper machineRoomMapper,
        final SeqNoGenerator seqNoGenerator,
        final DictMapper dictMapper,
        final JdbcTemplate jdbc
    ) {
        this.warehouseMapper = warehouseMapper;
        this.shelfMapper = shelfMapper;
        this.layerMapper = layerMapper;
        this.machineRoomMapper = machineRoomMapper;
        this.seqNoGenerator = seqNoGenerator;
        this.dictMapper = dictMapper;
        this.jdbc = jdbc;
    }

    public static class CreateLayerReq {
        private String name;
        private String capacityDesc;
        private String status;
        private String remark;

        
        public String getName() {
            return this.name;
        }

        
        public String getCapacityDesc() {
            return this.capacityDesc;
        }

        
        public String getStatus() {
            return this.status;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setCapacityDesc(final String capacityDesc) {
            this.capacityDesc = capacityDesc;
        }

        
        public void setStatus(final String status) {
            this.status = status;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WarehouseController.CreateLayerReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$name = this.getName();
                Object other$name = other.getName();
                if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                    Object this$capacityDesc = this.getCapacityDesc();
                    Object other$capacityDesc = other.getCapacityDesc();
                    if (this$capacityDesc == null ? other$capacityDesc == null : this$capacityDesc.equals(other$capacityDesc)) {
                        Object this$status = this.getStatus();
                        Object other$status = other.getStatus();
                        if (this$status == null ? other$status == null : this$status.equals(other$status)) {
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
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WarehouseController.CreateLayerReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $capacityDesc = this.getCapacityDesc();
            result = result * 59 + ($capacityDesc == null ? 43 : $capacityDesc.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "WarehouseController.CreateLayerReq(name="
                + this.getName()
                + ", capacityDesc="
                + this.getCapacityDesc()
                + ", status="
                + this.getStatus()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class CreateShelfReq {
        private String shelfCode;
        private String name;
        private String shelfType;
        private Integer layerCount;
        private String status;
        private String remark;

        
        public String getShelfCode() {
            return this.shelfCode;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getShelfType() {
            return this.shelfType;
        }

        
        public Integer getLayerCount() {
            return this.layerCount;
        }

        
        public String getStatus() {
            return this.status;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setShelfCode(final String shelfCode) {
            this.shelfCode = shelfCode;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setShelfType(final String shelfType) {
            this.shelfType = shelfType;
        }

        
        public void setLayerCount(final Integer layerCount) {
            this.layerCount = layerCount;
        }

        
        public void setStatus(final String status) {
            this.status = status;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WarehouseController.CreateShelfReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$layerCount = this.getLayerCount();
                Object other$layerCount = other.getLayerCount();
                if (this$layerCount == null ? other$layerCount == null : this$layerCount.equals(other$layerCount)) {
                    Object this$shelfCode = this.getShelfCode();
                    Object other$shelfCode = other.getShelfCode();
                    if (this$shelfCode == null ? other$shelfCode == null : this$shelfCode.equals(other$shelfCode)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$shelfType = this.getShelfType();
                            Object other$shelfType = other.getShelfType();
                            if (this$shelfType == null ? other$shelfType == null : this$shelfType.equals(other$shelfType)) {
                                Object this$status = this.getStatus();
                                Object other$status = other.getStatus();
                                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
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
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WarehouseController.CreateShelfReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $layerCount = this.getLayerCount();
            result = result * 59 + ($layerCount == null ? 43 : $layerCount.hashCode());
            Object $shelfCode = this.getShelfCode();
            result = result * 59 + ($shelfCode == null ? 43 : $shelfCode.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $shelfType = this.getShelfType();
            result = result * 59 + ($shelfType == null ? 43 : $shelfType.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "WarehouseController.CreateShelfReq(shelfCode="
                + this.getShelfCode()
                + ", name="
                + this.getName()
                + ", shelfType="
                + this.getShelfType()
                + ", layerCount="
                + this.getLayerCount()
                + ", status="
                + this.getStatus()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class CreateWarehouseReq {
        private String warehouseCode;
        @NotBlank(
            message = "仓库名称不能为空"
        )
        private String name;
        private String warehouseType;
        private String roomCode;
        private String address;
        private String manager;
        private String capacityDesc;
        private String status;
        private Boolean isDefault;
        private String remark;

        
        public String getWarehouseCode() {
            return this.warehouseCode;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getWarehouseType() {
            return this.warehouseType;
        }

        
        public String getRoomCode() {
            return this.roomCode;
        }

        
        public String getAddress() {
            return this.address;
        }

        
        public String getManager() {
            return this.manager;
        }

        
        public String getCapacityDesc() {
            return this.capacityDesc;
        }

        
        public String getStatus() {
            return this.status;
        }

        
        public Boolean getIsDefault() {
            return this.isDefault;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setWarehouseCode(final String warehouseCode) {
            this.warehouseCode = warehouseCode;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setWarehouseType(final String warehouseType) {
            this.warehouseType = warehouseType;
        }

        
        public void setRoomCode(final String roomCode) {
            this.roomCode = roomCode;
        }

        
        public void setAddress(final String address) {
            this.address = address;
        }

        
        public void setManager(final String manager) {
            this.manager = manager;
        }

        
        public void setCapacityDesc(final String capacityDesc) {
            this.capacityDesc = capacityDesc;
        }

        
        public void setStatus(final String status) {
            this.status = status;
        }

        
        public void setIsDefault(final Boolean isDefault) {
            this.isDefault = isDefault;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WarehouseController.CreateWarehouseReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$isDefault = this.getIsDefault();
                Object other$isDefault = other.getIsDefault();
                if (this$isDefault == null ? other$isDefault == null : this$isDefault.equals(other$isDefault)) {
                    Object this$warehouseCode = this.getWarehouseCode();
                    Object other$warehouseCode = other.getWarehouseCode();
                    if (this$warehouseCode == null ? other$warehouseCode == null : this$warehouseCode.equals(other$warehouseCode)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$warehouseType = this.getWarehouseType();
                            Object other$warehouseType = other.getWarehouseType();
                            if (this$warehouseType == null ? other$warehouseType == null : this$warehouseType.equals(other$warehouseType)) {
                                Object this$roomCode = this.getRoomCode();
                                Object other$roomCode = other.getRoomCode();
                                if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                    Object this$address = this.getAddress();
                                    Object other$address = other.getAddress();
                                    if (this$address == null ? other$address == null : this$address.equals(other$address)) {
                                        Object this$manager = this.getManager();
                                        Object other$manager = other.getManager();
                                        if (this$manager == null ? other$manager == null : this$manager.equals(other$manager)) {
                                            Object this$capacityDesc = this.getCapacityDesc();
                                            Object other$capacityDesc = other.getCapacityDesc();
                                            if (this$capacityDesc == null ? other$capacityDesc == null : this$capacityDesc.equals(other$capacityDesc)) {
                                                Object this$status = this.getStatus();
                                                Object other$status = other.getStatus();
                                                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
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
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WarehouseController.CreateWarehouseReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $isDefault = this.getIsDefault();
            result = result * 59 + ($isDefault == null ? 43 : $isDefault.hashCode());
            Object $warehouseCode = this.getWarehouseCode();
            result = result * 59 + ($warehouseCode == null ? 43 : $warehouseCode.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $warehouseType = this.getWarehouseType();
            result = result * 59 + ($warehouseType == null ? 43 : $warehouseType.hashCode());
            Object $roomCode = this.getRoomCode();
            result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
            Object $address = this.getAddress();
            result = result * 59 + ($address == null ? 43 : $address.hashCode());
            Object $manager = this.getManager();
            result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
            Object $capacityDesc = this.getCapacityDesc();
            result = result * 59 + ($capacityDesc == null ? 43 : $capacityDesc.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "WarehouseController.CreateWarehouseReq(warehouseCode="
                + this.getWarehouseCode()
                + ", name="
                + this.getName()
                + ", warehouseType="
                + this.getWarehouseType()
                + ", roomCode="
                + this.getRoomCode()
                + ", address="
                + this.getAddress()
                + ", manager="
                + this.getManager()
                + ", capacityDesc="
                + this.getCapacityDesc()
                + ", status="
                + this.getStatus()
                + ", isDefault="
                + this.getIsDefault()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class UpdateLayerReq {
        private Integer version;
        private String name;
        private String capacityDesc;
        private String status;
        private String remark;

        
        public Integer getVersion() {
            return this.version;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getCapacityDesc() {
            return this.capacityDesc;
        }

        
        public String getStatus() {
            return this.status;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setVersion(final Integer version) {
            this.version = version;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setCapacityDesc(final String capacityDesc) {
            this.capacityDesc = capacityDesc;
        }

        
        public void setStatus(final String status) {
            this.status = status;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WarehouseController.UpdateLayerReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$version = this.getVersion();
                Object other$version = other.getVersion();
                if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                    Object this$name = this.getName();
                    Object other$name = other.getName();
                    if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                        Object this$capacityDesc = this.getCapacityDesc();
                        Object other$capacityDesc = other.getCapacityDesc();
                        if (this$capacityDesc == null ? other$capacityDesc == null : this$capacityDesc.equals(other$capacityDesc)) {
                            Object this$status = this.getStatus();
                            Object other$status = other.getStatus();
                            if (this$status == null ? other$status == null : this$status.equals(other$status)) {
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
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WarehouseController.UpdateLayerReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $version = this.getVersion();
            result = result * 59 + ($version == null ? 43 : $version.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $capacityDesc = this.getCapacityDesc();
            result = result * 59 + ($capacityDesc == null ? 43 : $capacityDesc.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "WarehouseController.UpdateLayerReq(version="
                + this.getVersion()
                + ", name="
                + this.getName()
                + ", capacityDesc="
                + this.getCapacityDesc()
                + ", status="
                + this.getStatus()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class UpdateShelfReq {
        private Integer version;
        private String name;
        private String shelfType;
        private Integer layerCount;
        private String status;
        private String remark;

        
        public Integer getVersion() {
            return this.version;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getShelfType() {
            return this.shelfType;
        }

        
        public Integer getLayerCount() {
            return this.layerCount;
        }

        
        public String getStatus() {
            return this.status;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setVersion(final Integer version) {
            this.version = version;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setShelfType(final String shelfType) {
            this.shelfType = shelfType;
        }

        
        public void setLayerCount(final Integer layerCount) {
            this.layerCount = layerCount;
        }

        
        public void setStatus(final String status) {
            this.status = status;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WarehouseController.UpdateShelfReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$version = this.getVersion();
                Object other$version = other.getVersion();
                if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                    Object this$layerCount = this.getLayerCount();
                    Object other$layerCount = other.getLayerCount();
                    if (this$layerCount == null ? other$layerCount == null : this$layerCount.equals(other$layerCount)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$shelfType = this.getShelfType();
                            Object other$shelfType = other.getShelfType();
                            if (this$shelfType == null ? other$shelfType == null : this$shelfType.equals(other$shelfType)) {
                                Object this$status = this.getStatus();
                                Object other$status = other.getStatus();
                                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
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
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WarehouseController.UpdateShelfReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $version = this.getVersion();
            result = result * 59 + ($version == null ? 43 : $version.hashCode());
            Object $layerCount = this.getLayerCount();
            result = result * 59 + ($layerCount == null ? 43 : $layerCount.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $shelfType = this.getShelfType();
            result = result * 59 + ($shelfType == null ? 43 : $shelfType.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "WarehouseController.UpdateShelfReq(version="
                + this.getVersion()
                + ", name="
                + this.getName()
                + ", shelfType="
                + this.getShelfType()
                + ", layerCount="
                + this.getLayerCount()
                + ", status="
                + this.getStatus()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }

    public static class UpdateWarehouseReq {
        private Integer version;
        private String name;
        private String warehouseType;
        private String roomCode;
        private String address;
        private String manager;
        private String capacityDesc;
        private String status;
        private Boolean isDefault;
        private String remark;

        
        public Integer getVersion() {
            return this.version;
        }

        
        public String getName() {
            return this.name;
        }

        
        public String getWarehouseType() {
            return this.warehouseType;
        }

        
        public String getRoomCode() {
            return this.roomCode;
        }

        
        public String getAddress() {
            return this.address;
        }

        
        public String getManager() {
            return this.manager;
        }

        
        public String getCapacityDesc() {
            return this.capacityDesc;
        }

        
        public String getStatus() {
            return this.status;
        }

        
        public Boolean getIsDefault() {
            return this.isDefault;
        }

        
        public String getRemark() {
            return this.remark;
        }

        
        public void setVersion(final Integer version) {
            this.version = version;
        }

        
        public void setName(final String name) {
            this.name = name;
        }

        
        public void setWarehouseType(final String warehouseType) {
            this.warehouseType = warehouseType;
        }

        
        public void setRoomCode(final String roomCode) {
            this.roomCode = roomCode;
        }

        
        public void setAddress(final String address) {
            this.address = address;
        }

        
        public void setManager(final String manager) {
            this.manager = manager;
        }

        
        public void setCapacityDesc(final String capacityDesc) {
            this.capacityDesc = capacityDesc;
        }

        
        public void setStatus(final String status) {
            this.status = status;
        }

        
        public void setIsDefault(final Boolean isDefault) {
            this.isDefault = isDefault;
        }

        
        public void setRemark(final String remark) {
            this.remark = remark;
        }

        
        @Override
        public boolean equals(final Object o) {
            if (o == this) {
                return true;
            } else if (!(o instanceof WarehouseController.UpdateWarehouseReq other)) {
                return false;
            } else if (!other.canEqual(this)) {
                return false;
            } else {
                Object this$version = this.getVersion();
                Object other$version = other.getVersion();
                if (this$version == null ? other$version == null : this$version.equals(other$version)) {
                    Object this$isDefault = this.getIsDefault();
                    Object other$isDefault = other.getIsDefault();
                    if (this$isDefault == null ? other$isDefault == null : this$isDefault.equals(other$isDefault)) {
                        Object this$name = this.getName();
                        Object other$name = other.getName();
                        if (this$name == null ? other$name == null : this$name.equals(other$name)) {
                            Object this$warehouseType = this.getWarehouseType();
                            Object other$warehouseType = other.getWarehouseType();
                            if (this$warehouseType == null ? other$warehouseType == null : this$warehouseType.equals(other$warehouseType)) {
                                Object this$roomCode = this.getRoomCode();
                                Object other$roomCode = other.getRoomCode();
                                if (this$roomCode == null ? other$roomCode == null : this$roomCode.equals(other$roomCode)) {
                                    Object this$address = this.getAddress();
                                    Object other$address = other.getAddress();
                                    if (this$address == null ? other$address == null : this$address.equals(other$address)) {
                                        Object this$manager = this.getManager();
                                        Object other$manager = other.getManager();
                                        if (this$manager == null ? other$manager == null : this$manager.equals(other$manager)) {
                                            Object this$capacityDesc = this.getCapacityDesc();
                                            Object other$capacityDesc = other.getCapacityDesc();
                                            if (this$capacityDesc == null ? other$capacityDesc == null : this$capacityDesc.equals(other$capacityDesc)) {
                                                Object this$status = this.getStatus();
                                                Object other$status = other.getStatus();
                                                if (this$status == null ? other$status == null : this$status.equals(other$status)) {
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
            }
        }

        
        protected boolean canEqual(final Object other) {
            return other instanceof WarehouseController.UpdateWarehouseReq;
        }

        
        @Override
        public int hashCode() {
            int PRIME = 59;
            int result = 1;
            Object $version = this.getVersion();
            result = result * 59 + ($version == null ? 43 : $version.hashCode());
            Object $isDefault = this.getIsDefault();
            result = result * 59 + ($isDefault == null ? 43 : $isDefault.hashCode());
            Object $name = this.getName();
            result = result * 59 + ($name == null ? 43 : $name.hashCode());
            Object $warehouseType = this.getWarehouseType();
            result = result * 59 + ($warehouseType == null ? 43 : $warehouseType.hashCode());
            Object $roomCode = this.getRoomCode();
            result = result * 59 + ($roomCode == null ? 43 : $roomCode.hashCode());
            Object $address = this.getAddress();
            result = result * 59 + ($address == null ? 43 : $address.hashCode());
            Object $manager = this.getManager();
            result = result * 59 + ($manager == null ? 43 : $manager.hashCode());
            Object $capacityDesc = this.getCapacityDesc();
            result = result * 59 + ($capacityDesc == null ? 43 : $capacityDesc.hashCode());
            Object $status = this.getStatus();
            result = result * 59 + ($status == null ? 43 : $status.hashCode());
            Object $remark = this.getRemark();
            return result * 59 + ($remark == null ? 43 : $remark.hashCode());
        }

        
        @Override
        public String toString() {
            return "WarehouseController.UpdateWarehouseReq(version="
                + this.getVersion()
                + ", name="
                + this.getName()
                + ", warehouseType="
                + this.getWarehouseType()
                + ", roomCode="
                + this.getRoomCode()
                + ", address="
                + this.getAddress()
                + ", manager="
                + this.getManager()
                + ", capacityDesc="
                + this.getCapacityDesc()
                + ", status="
                + this.getStatus()
                + ", isDefault="
                + this.getIsDefault()
                + ", remark="
                + this.getRemark()
                + ")";
        }
    }
}
