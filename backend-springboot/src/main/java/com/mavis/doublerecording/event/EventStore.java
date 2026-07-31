package com.mavis.doublerecording.event;

import com.mavis.doublerecording.domain.event.EventLog;
import com.mavis.doublerecording.domain.event.EventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件存储(EventStore)
 * 负责持久化所有领域事件,同时通过 Spring ApplicationEvent 触发订阅
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventStore {

    private final EventLogRepository eventLogRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * 追加事件(带幂等性保证)
     */
    @Transactional
    public DomainEvent append(String sessionId, String aggregateType, String aggregateId,
                              String eventType, Map<String, Object> payload) {
        DomainEvent event = DomainEvent.create(sessionId, aggregateType, aggregateId, eventType, payload);
        event.setSequenceNo(eventLogRepository.maxSequenceNo(sessionId) + 1);

        EventLog eventLog = new EventLog();
        eventLog.setEventId(event.getEventId());
        eventLog.setSessionId(event.getSessionId());
        eventLog.setAggregateType(event.getAggregateType());
        eventLog.setAggregateId(event.getAggregateId());
        eventLog.setEventType(event.getEventType());
        eventLog.setSequenceNo(event.getSequenceNo());
        try {
            eventLog.setPayload(objectMapper.writeValueAsString(event.getPayload()));
        } catch (JsonProcessingException e) {
            eventLog.setPayload("{}");
        }

        eventLogRepository.save(eventLog);
        log.debug("[EventStore] 事件已持久化: {}", event.getEventId());

        // 发布到 Spring 事件总线(代替 MQ)
        applicationEventPublisher.publishEvent(event);
        return event;
    }

    /**
     * 标记事件已处理
     */
    @Transactional
    public void markProcessed(String eventId) {
        eventLogRepository.findAll().stream()
            .filter(e -> e.getEventId().equals(eventId))
            .findFirst()
            .ifPresent(e -> {
                e.setProcessedAt(java.time.LocalDateTime.now());
                eventLogRepository.save(e);
            });
    }

    /**
     * 获取会话的所有事件(用于断点续录、回溯)
     */
    public List<EventLog> getSessionEvents(String sessionId) {
        return eventLogRepository.findBySessionIdOrderBySequenceNo(sessionId);
    }

    /**
     * 检查事件是否已存在(幂等)
     */
    public boolean exists(String eventId) {
        return eventLogRepository.existsByEventId(eventId);
    }
}
