package com.dcim.common.stock;

import java.time.LocalDate;

public interface StockMovementPort {
    StockMovementPort.MovementResult apply(StockMovementPort.MovementCmd cmd);

    public static record MovementCmd(
        String logType,
        String materialCode,
        int qty,
        LocalDate bizDate,
        String department,
        String refWo,
        String operator,
        String remark,
        String warehouseCode,
        String fromWarehouseCode
    ) {
        public MovementCmd(String logType, String materialCode, int qty, LocalDate bizDate, String department, String refWo, String operator, String remark) {
            this(logType, materialCode, qty, bizDate, department, refWo, operator, remark, null, null);
        }
    }

    public static record MovementResult(
        String logNo, String materialCode, String logType, int qty, int stockAfter, String remark, String warehouseCode, int warehouseQtyAfter
    ) {
        public MovementResult(String logNo, String materialCode, String logType, int qty, int stockAfter, String remark) {
            this(logNo, materialCode, logType, qty, stockAfter, remark, null, stockAfter);
        }
    }
}
