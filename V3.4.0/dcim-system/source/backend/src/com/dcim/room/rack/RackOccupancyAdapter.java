package com.dcim.room.rack;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.common.rack.RackOccupancyPort;
import com.dcim.room.entity.RackEntity;
import com.dcim.room.entity.RackUnitEntity;
import com.dcim.room.entity.RoomAssetRef;
import com.dcim.room.mapper.RackMapper;
import com.dcim.room.mapper.RackUnitMapper;
import com.dcim.room.mapper.RoomAssetQueryMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class RackOccupancyAdapter implements RackOccupancyPort {
    private static final String ST_FREE = "空闲";
    private static final String ST_OCCUPIED = "占用";
    private final RackMapper rackMapper;
    private final RackUnitMapper rackUnitMapper;
    private final RoomAssetQueryMapper roomAssetQueryMapper;
    private final JdbcTemplate jdbc;

    @Override
    public void syncAssetPlacement(String assetNo, String oldRack, Integer oldUnit, Integer oldUHeight, String newRack, Integer newUnit, Integer newUHeight) {
        this.release(assetNo, oldRack, oldUnit, oldUHeight);
        if (newRack != null && !newRack.isBlank() && newUnit != null) {
            this.occupy(assetNo, newRack.trim(), newUnit, this.normalizeHeight(newUHeight));
        }
    }

    private void release(String assetNo, String rack, Integer unit, Integer uHeight) {
        if (rack != null && !rack.isBlank() && unit != null && unit >= 1) {
            int h = this.normalizeHeight(uHeight);
            this.jdbc
                .update(
                    "UPDATE rack_unit SET status = ?, occupied_by = NULL WHERE rack_code = ? AND occupied_by = ? AND unit_no BETWEEN ? AND ?",
                    new Object[]{"空闲", rack.trim(), assetNo, unit, unit + h - 1}
                );
        }
    }

    private void occupy(String assetNo, String rack, Integer unit, int h) {
        if (unit < 1) {
            throw BusinessException.badRequest("起始U位必须为正整数");
        } else {
            // 修复 A5：锁读 rack 行——容量校验基于当前已提交容量（不再按旧容量放行），
            // 且与 resizeUnits/delete 的「锁 rack → 检查 → 删 U 位」互斥，锁序一致（先 rack 后 rack_unit）无交叉死锁
            RackEntity r = (RackEntity)this.rackMapper
                .selectOne(Wrappers.<RackEntity>lambdaQuery().eq(RackEntity::getRackCode, rack).last("FOR UPDATE"));
            if (r == null) {
                throw BusinessException.badRequest("机柜不存在：" + rack);
            } else {
                int end = unit + h - 1;
                if (end > r.getUCapacity()) {
                    throw BusinessException.badRequest(rack + " 容量为 " + r.getUCapacity() + "U，当前占用 U" + unit + "~U" + end + " 已越界");
                } else {
                    List<RackUnitEntity> rows = this.rackUnitMapper
                        .selectList(
                            (((Wrappers.<RackUnitEntity>lambdaQuery()
                                            .eq(RackUnitEntity::getRackCode, rack))
                                        .between(RackUnitEntity::getUnitNo, unit, end))
                                    .orderByAsc(RackUnitEntity::getUnitNo))
                                .last("FOR UPDATE")
                        );
                    if (rows != null && rows.size() == h) {
                        this.assertRangeAvailable(assetNo, rack, rows);
                        int updated = this.jdbc
                            .update(
                                "UPDATE rack_unit SET status = ?, occupied_by = ? WHERE rack_code = ? AND unit_no BETWEEN ? AND ?",
                                new Object[]{"占用", assetNo, rack, unit, end}
                            );
                        if (updated != h) {
                            throw BusinessException.badRequest("机柜U位数据异常：" + rack + " U" + unit + "~U" + end);
                        }
                    } else {
                        throw BusinessException.badRequest("机柜U位数据异常：" + rack + " U" + unit + "~U" + end + " 行缺失，请先修复U位数据");
                    }
                }
            }
        }
    }

    private void assertRangeAvailable(String assetNo, String rack, List<RackUnitEntity> rows) {
        Map<String, int[]> conflicts = new LinkedHashMap<>();

        for (RackUnitEntity row : rows) {
            String st = row.getStatus();
            if (!"空闲".equals(st)) {
                if (!"占用".equals(st)) {
                    throw BusinessException.conflict("U位 U" + row.getUnitNo() + " 为「" + st + "」状态，不可占用");
                }

                if (!Objects.equals(row.getOccupiedBy(), assetNo)) {
                    conflicts.putIfAbsent(row.getOccupiedBy() == null ? "" : row.getOccupiedBy(), new int[]{row.getUnitNo(), row.getUnitNo()});
                }
            }
        }

        if (!conflicts.isEmpty()) {
            List<String> parts = new ArrayList<>();

            for (Entry<String, int[]> e : conflicts.entrySet()) {
                Map<String, Object> extent = this.jdbc
                    .queryForMap(
                        "SELECT COALESCE(MIN(unit_no), 0) AS lo, COALESCE(MAX(unit_no), 0) AS hi FROM rack_unit WHERE rack_code = ? AND occupied_by = ? AND status = ?",
                        new Object[]{rack, e.getKey(), "占用"}
                    );
                int lo = ((Number)extent.get("lo")).intValue();
                int hi = ((Number)extent.get("hi")).intValue();
                String name = e.getKey();
                RoomAssetRef ref = this.roomAssetQueryMapper.selectRefByAssetNo(e.getKey());
                if (ref != null && ref.getName() != null) {
                    name = ref.getName();
                }

                parts.add("与已上架资产「" + name + "」(U" + lo + "~U" + hi + ") 位置冲突");
            }

            throw BusinessException.conflict(String.join("；", parts));
        }
    }

    private int normalizeHeight(Integer uHeight) {
        if (uHeight == null) {
            return 1;
        } else if (uHeight < 1) {
            throw BusinessException.badRequest("U位高度必须为正整数");
        } else {
            return uHeight;
        }
    }

    
    public RackOccupancyAdapter(
        final RackMapper rackMapper, final RackUnitMapper rackUnitMapper, final RoomAssetQueryMapper roomAssetQueryMapper, final JdbcTemplate jdbc
    ) {
        this.rackMapper = rackMapper;
        this.rackUnitMapper = rackUnitMapper;
        this.roomAssetQueryMapper = roomAssetQueryMapper;
        this.jdbc = jdbc;
    }
}
