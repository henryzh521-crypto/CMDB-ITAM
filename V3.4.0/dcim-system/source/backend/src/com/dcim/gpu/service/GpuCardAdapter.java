package com.dcim.gpu.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dcim.common.BusinessException;
import com.dcim.common.asset.AssetOpsPort;
import com.dcim.common.gpu.GpuCardPort;
import com.dcim.gpu.entity.GpuCardEntity;
import com.dcim.gpu.mapper.GpuCardMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GpuCardAdapter implements GpuCardPort {
    private final GpuCardMapper cardMapper;
    private final AssetOpsPort assetOpsPort;

    @Transactional
    @Override
    public int upsertFromSnapshot(String hostAssetNo, List<GpuCardPort.CardSnapshot> cards, String refDoc) {
        if (hostAssetNo != null && !hostAssetNo.isBlank() && cards != null && !cards.isEmpty()) {
            int count = 0;

            for (GpuCardPort.CardSnapshot c : cards) {
                if (c.slot() != null) {
                    String cardNo = hostAssetNo + "-G" + c.slot();
                    String newSn = this.normSn(c.gpuSn());
                    if (newSn != null) {
                        Long dup = this.cardMapper
                            .selectCount(
                                (Wrappers.<GpuCardEntity>lambdaQuery().eq(GpuCardEntity::getGpuSn, newSn)).ne(GpuCardEntity::getCardNo, cardNo)
                            );
                        if (dup != null && dup > 0L) {
                            throw BusinessException.conflict("GPU SN 已存在于其他卡：" + newSn);
                        }
                    }

                    GpuCardEntity cur = (GpuCardEntity)this.cardMapper.selectById(cardNo);

                    try {
                        if (cur == null) {
                            GpuCardEntity e = new GpuCardEntity();
                            e.setCardNo(cardNo);
                            e.setHostAssetNo(hostAssetNo);
                            e.setSlot(c.slot());
                            e.setGpuSn(newSn);
                            this.applyProfile(e, c);
                            e.setSwapHistory(new ArrayList<>());
                            e.setUpdatedAt(LocalDateTime.now());
                            this.cardMapper.insert(e);
                        } else if (newSn != null && cur.getGpuSn() != null && !newSn.equals(cur.getGpuSn())) {
                            Map<String, Object> swap = new LinkedHashMap<>();
                            swap.put("oldSn", cur.getGpuSn());
                            swap.put("newSn", newSn);
                            swap.put("at", LocalDateTime.now().toString());
                            swap.put("refDoc", refDoc);
                            List<Object> history = cur.getSwapHistory() == null ? new ArrayList<>() : new ArrayList<>(cur.getSwapHistory());
                            history.add(swap);
                            cur.setPrevSn(cur.getGpuSn());
                            cur.setGpuSn(newSn);
                            cur.setSwapHistory(history);
                            this.applyProfile(cur, c);
                            cur.setUpdatedAt(LocalDateTime.now());
                            this.cardMapper.updateById(cur);
                            Map<String, Object> ev = new LinkedHashMap<>();
                            ev.put("cardNo", cardNo);
                            ev.put("swap", swap);
                            this.assetOpsPort.recordEvent(hostAssetNo, "GPU_SWAP", refDoc, ev);
                        } else {
                            if (newSn != null && cur.getGpuSn() == null) {
                                cur.setGpuSn(newSn);
                            }

                            this.applyProfile(cur, c);
                            cur.setUpdatedAt(LocalDateTime.now());
                            this.cardMapper.updateById(cur);
                        }

                        count++;
                    } catch (DuplicateKeyException var13) {
                        throw BusinessException.conflict("GPU 卡写入冲突（SN 或槽位重复）：" + cardNo);
                    }
                }
            }

            return count;
        } else {
            return 0;
        }
    }

    private String normSn(String sn) {
        return sn != null && !sn.isBlank() ? sn.trim().toUpperCase() : null;
    }

    private void applyProfile(GpuCardEntity e, GpuCardPort.CardSnapshot c) {
        if (c.model() != null) {
            e.setModel(c.model());
        }

        if (c.memoryGb() != null) {
            e.setMemoryGb(c.memoryGb());
        }

        if (c.fw() != null) {
            e.setFw(c.fw());
        }

        if (c.batchNo() != null) {
            e.setBatchNo(c.batchNo());
        }

        if (c.migCapable() != null) {
            e.setMigCapable(c.migCapable());
        }
    }

    
    public GpuCardAdapter(final GpuCardMapper cardMapper, final AssetOpsPort assetOpsPort) {
        this.cardMapper = cardMapper;
        this.assetOpsPort = assetOpsPort;
    }
}
