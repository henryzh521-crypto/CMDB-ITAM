package com.dcim.workorder.service;

import com.dcim.workorder.entity.WorkorderItemEntity;
import java.util.function.Function;

final class TransferScope {
    static final String SAME_ROOM = "同机房";
    static final String CROSS_ROOM = "跨机房";
    static final String CROSS_DC = "跨数据中心";
    static final String CROSS_REGION = "跨地域";

    private TransferScope() {
    }

    static String of(WorkorderItemEntity row, Function<String, String> dcOf) {
        if (row == null) {
            return null;
        } else {
            String srcRoom = row.getSrcRoom();
            String dstRoom = row.getDstRoom();
            String srcRegion = row.getSrcRegion();
            String dstRegion = row.getDstRegion();
            if (blank(srcRegion) && blank(dstRegion) && blank(srcRoom) && blank(dstRoom)) {
                return null;
            } else if (!blank(srcRegion) && !blank(dstRegion) && !srcRegion.trim().equalsIgnoreCase(dstRegion.trim())) {
                return "跨地域";
            } else if (!blank(srcRoom) && !blank(dstRoom)) {
                if (srcRoom.trim().equals(dstRoom.trim())) {
                    return "同机房";
                } else {
                    if (dcOf != null) {
                        String srcDc = dcOf.apply(srcRoom.trim());
                        String dstDc = dcOf.apply(dstRoom.trim());
                        if (!blank(srcDc) && !blank(dstDc) && !srcDc.trim().equals(dstDc.trim())) {
                            return "跨数据中心";
                        }
                    }

                    return "跨机房";
                }
            } else {
                return blank(srcRoom) && blank(dstRoom) ? "同机房" : null;
            }
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
