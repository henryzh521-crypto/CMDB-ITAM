package com.dcim.common.event;

import java.time.OffsetDateTime;
import java.util.Map;

public record DomainEvent(String type, String objType, String objId, String actor, Map<String, Object> data, OffsetDateTime occurredAt) {
    public static DomainEvent of(String type, String objType, String objId, String actor, Map<String, Object> data) {
        return new DomainEvent(type, objType, objId, actor == null ? "system" : actor, data == null ? Map.of() : data, OffsetDateTime.now());
    }
}
