package com.dcim.common.gpu;

import java.util.List;

public interface GpuCardPort {
    int upsertFromSnapshot(String hostAssetNo, List<GpuCardPort.CardSnapshot> cards, String refDoc);

    public static record CardSnapshot(Integer slot, String gpuSn, String model, Integer memoryGb, String fw, String batchNo, Boolean migCapable) {
    }
}
