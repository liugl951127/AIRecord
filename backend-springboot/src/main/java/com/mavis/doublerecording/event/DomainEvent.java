package com.mavis.doublerecording.event;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 领域事件
 */
@Data
@NoArgsConstructor
public class DomainEvent {

    private String eventId;
    private String sessionId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Long sequenceNo;
    private Map<String, Object> payload;
    private LocalDateTime occurredAt;

    public static DomainEvent create(String sessionId, String aggregateType, String aggregateId,
                                     String eventType, Map<String, Object> payload) {
        DomainEvent event = new DomainEvent();
        event.eventId = "EVT-" + UUID.randomUUID().toString().replace("-", "");
        event.sessionId = sessionId;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.payload = payload;
        event.occurredAt = LocalDateTime.now();
        return event;
    }
}
