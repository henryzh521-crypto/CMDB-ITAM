package com.dcim.warehouse.service;

import com.dcim.common.stock.StockMovementPort;
import org.springframework.stereotype.Component;

@Component
public class StockMovementAdapter implements StockMovementPort {
    private final StockMovementService stockMovementService;

    @Override
    public StockMovementPort.MovementResult apply(StockMovementPort.MovementCmd cmd) {
        return this.stockMovementService.apply(cmd);
    }

    
    public StockMovementAdapter(final StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }
}
