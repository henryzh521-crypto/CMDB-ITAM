package com.dcim.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class DomainEventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);
    private final ApplicationEventPublisher eventPublisher;

    public void publish(DomainEvent event) {
        log.debug("[event] {} {}:{} by {}", new Object[]{event.type(), event.objType(), event.objId(), event.actor()});
        this.eventPublisher.publishEvent(event);
    }

    
    public DomainEventPublisher(final ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
}
