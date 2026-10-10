package com.dcim.common.event;

import com.dcim.asset.entity.AssetEventEntity;
import com.dcim.warehouse.entity.StockLogEntity;
import com.dcim.workorder.entity.WorkorderEntity;
import com.dcim.workorder.entity.WorkorderFlowEntity;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class DomainEventCaptureAspect {
    
    private static final Logger log = LoggerFactory.getLogger(DomainEventCaptureAspect.class);
    private final DomainEventPublisher publisher;

    @AfterReturning("execution(* insert(..)) && target(com.dcim.asset.mapper.AssetEventMapper) && args(e)")
    public void onAssetEvent(AssetEventEntity e) {
        this.safe(() -> {
            String evt = String.valueOf(e.getEventType()).toUpperCase(Locale.ROOT);

            String type = switch (evt) {
                case "CREATE", "IMPORT", "REPLACE_IN" -> "asset.created";
                case "STATUS_CHANGE", "REPLACE_OUT" -> "asset.status_changed";
                case "RACKED", "UNRACKED", "MOVED", "TRANSFER" -> "asset.moved";
                case "DELETE" -> "asset.deleted";
                default -> "asset.updated";
            };
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("assetNo", e.getAssetNo());
            data.put("eventType", e.getEventType());
            if (e.getRefDoc() != null) {
                data.put("refDoc", e.getRefDoc());
            }

            if (e.getSnapshot() != null) {
                data.put("snapshot", e.getSnapshot());
            }

            this.publisher.publish(DomainEvent.of(type, "asset", e.getAssetNo(), e.getOperator(), data));
        });
    }

    @AfterReturning("execution(* insert(..)) && target(com.dcim.workorder.mapper.WorkorderMapper) && args(wo)")
    public void onWorkorderCreated(WorkorderEntity wo) {
        this.safe(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("woNo", wo.getWoNo());
            data.put("woType", wo.getWoType());
            data.put("title", wo.getTitle());
            data.put("priority", wo.getPriority());
            data.put("status", wo.getStatus());
            data.put("applicant", wo.getApplicant());
            this.publisher.publish(DomainEvent.of("workorder.created", "workorder", wo.getWoNo(), wo.getApplicantUsername(), data));
        });
    }

    @AfterReturning("execution(* insert(..)) && target(com.dcim.workorder.mapper.WorkorderFlowMapper) && args(f)")
    public void onWorkorderFlow(WorkorderFlowEntity f) {
        this.safe(() -> {
            if (!Objects.equals(f.getFromStatus(), f.getToStatus())) {
                String type = "已完成".equals(f.getToStatus()) ? "workorder.completed" : "workorder.status_changed";
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("woNo", f.getWoNo());
                data.put("action", f.getAction());
                data.put("fromStatus", f.getFromStatus());
                data.put("toStatus", f.getToStatus());
                if (f.getComment() != null) {
                    data.put("comment", f.getComment());
                }

                this.publisher.publish(DomainEvent.of(type, "workorder", f.getWoNo(), f.getActor(), data));
            }
        });
    }

    @AfterReturning("execution(* insert(..)) && target(com.dcim.warehouse.mapper.StockLogMapper) && args(s)")
    public void onStockLog(StockLogEntity s) {
        this.safe(() -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("logNo", s.getLogNo());
            data.put("logType", s.getLogType());
            data.put("materialCode", s.getMaterialCode());
            data.put("qty", s.getQty());
            data.put("warehouseCode", s.getWarehouseCode());
            if (s.getFromWarehouseCode() != null) {
                data.put("fromWarehouseCode", s.getFromWarehouseCode());
            }

            if (s.getRefWo() != null) {
                data.put("refWo", s.getRefWo());
            }

            this.publisher.publish(DomainEvent.of("stock.movement", "material", s.getMaterialCode(), s.getOperator(), data));
        });
    }

    private void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException var3) {
            log.warn("[event] 事件捕获失败（不影响业务）：{}", var3.toString());
        }
    }

    
    public DomainEventCaptureAspect(final DomainEventPublisher publisher) {
        this.publisher = publisher;
    }
}
