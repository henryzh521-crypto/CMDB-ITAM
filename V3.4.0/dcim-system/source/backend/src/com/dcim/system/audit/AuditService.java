package com.dcim.system.audit;

import com.dcim.system.entity.AuditLogEntity;
import com.dcim.system.mapper.AuditLogMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Async("auditExecutor")
    public void record(String actor, String action, String module, String objType, String objId, Map<String, Object> detail, String ip) {
        try {
            AuditLogEntity entity = new AuditLogEntity();
            entity.setActor(actor == null ? "anonymous" : actor);
            entity.setAction(action);
            entity.setModule(module);
            entity.setObjType(objType);
            entity.setObjId(objId);
            entity.setDetail(detail);
            entity.setIp(ip);
            entity.setCreatedAt(LocalDateTime.now());
            this.auditLogMapper.insert(entity);
        } catch (Exception var9) {
            log.error("审计日志写入失败: actor={}, action={}, module={}", new Object[]{actor, action, module, var9});
        }
    }
}
