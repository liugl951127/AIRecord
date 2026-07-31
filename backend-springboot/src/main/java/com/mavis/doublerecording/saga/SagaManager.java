package com.mavis.doublerecording.saga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.domain.saga.SagaLog;
import com.mavis.doublerecording.domain.saga.SagaLogRepository;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Saga 管理服务
 * 提供图形化界面所需的所有操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaManager {

    private final SagaLogRepository sagaLogRepository;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询 Saga 列表
     */
    public Map<String, Object> listSagas(String state, String sagaType, String sessionId,
                                          LocalDateTime startTime, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(size, 1), 200));
        Page<SagaLog> pageResult = sagaLogRepository.search(
            state, sagaType, sessionId, startTime, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("total", pageResult.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        result.put("pages", pageResult.getTotalPages());
        result.put("items", pageResult.getContent());
        return result;
    }

    /**
     * 查询单个 Saga 详情(含解析后的步骤信息)
     */
    public Map<String, Object> getSagaDetail(String sagaId) {
        SagaLog sagaLog = sagaLogRepository.findBySagaId(sagaId)
            .orElseThrow(() -> new BizException(404, "Saga 不存在: " + sagaId));

        Map<String, Object> result = new HashMap<>();
        result.put("saga", sagaLog);

        // 解析 payload
        Map<String, Object> context = new HashMap<>();
        if (sagaLog.getPayload() != null && !sagaLog.getPayload().isEmpty()) {
            try {
                context = objectMapper.readValue(sagaLog.getPayload(), new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("解析 payload 失败: {}", e.getMessage());
            }
        }
        result.put("context", context);

        // 计算耗时
        if (sagaLog.getCompletedAt() != null && sagaLog.getStartedAt() != null) {
            long durationMs = java.time.Duration.between(sagaLog.getStartedAt(), sagaLog.getCompletedAt()).toMillis();
            result.put("durationMs", durationMs);
        }

        return result;
    }

    /**
     * 查询某会话的 Saga 时间线(基于事件流)
     */
    public List<Map<String, Object>> getTimeline(String sessionId) {
        List<com.mavis.doublerecording.domain.event.EventLog> events = eventStore.getSessionEvents(sessionId);
        List<Map<String, Object>> timeline = new ArrayList<>();

        for (com.mavis.doublerecording.domain.event.EventLog e : events) {
            if (!"SAGA".equals(e.getAggregateType())) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("eventId", e.getEventId());
            item.put("eventType", e.getEventType());
            item.put("aggregateId", e.getAggregateId());
            item.put("sequenceNo", e.getSequenceNo());
            item.put("occurredAt", e.getOccurredAt());

            try {
                item.put("payload", objectMapper.readValue(e.getPayload(), new TypeReference<>() {}));
            } catch (Exception ex) {
                item.put("payload", e.getPayload());
            }
            timeline.add(item);
        }
        return timeline;
    }

    /**
     * 统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last24h = now.minusHours(24);
        LocalDateTime last7d = now.minusDays(7);

        // 总体统计
        long total = sagaLogRepository.count();
        stats.put("total", total);
        stats.put("completed", sagaLogRepository.countByState("COMPLETED"));
        stats.put("failed", sagaLogRepository.countByState("FAILED"));
        stats.put("compensated", sagaLogRepository.countByState("COMPENSATED"));
        stats.put("started", sagaLogRepository.countByState("STARTED"));
        stats.put("compensating", sagaLogRepository.countByState("COMPENSATING"));

        // 成功率
        if (total > 0) {
            long success = stats.get("completed") instanceof Long ? (Long) stats.get("completed") : 0L;
            stats.put("successRate", String.format("%.2f", success * 100.0 / total));
        } else {
            stats.put("successRate", "0.00");
        }

        // 24h 各状态分布
        List<Map<String, Object>> stateDistribution = new ArrayList<>();
        for (Object[] row : sagaLogRepository.countByStateSince(last24h)) {
            Map<String, Object> item = new HashMap<>();
            item.put("state", row[0]);
            item.put("count", row[1]);
            stateDistribution.add(item);
        }
        stats.put("stateDistribution24h", stateDistribution);

        // 各类型分布
        List<Map<String, Object>> typeDistribution = new ArrayList<>();
        for (Object[] row : sagaLogRepository.countByTypeSince(last7d)) {
            Map<String, Object> item = new HashMap<>();
            item.put("type", row[0]);
            item.put("count", row[1]);
            typeDistribution.add(item);
        }
        stats.put("typeDistribution7d", typeDistribution);

        // 24h 趋势(按小时,在 Java 端聚合,避免 H2 不支持 date_format)
        List<Map<String, Object>> hourlyTrend = new ArrayList<>();
        DateTimeFormatter hourFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        Map<String, Integer> totalMap = new TreeMap<>();
        Map<String, Integer> successMap = new TreeMap<>();
        for (SagaLog sl : sagaLogRepository.findAll()) {
            if (sl.getStartedAt() == null || sl.getStartedAt().isBefore(last24h)) continue;
            String hour = sl.getStartedAt().format(hourFmt);
            totalMap.merge(hour, 1, Integer::sum);
            if ("COMPLETED".equals(sl.getState())) {
                successMap.merge(hour, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : totalMap.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("hour", entry.getKey());
            item.put("count", entry.getValue());
            item.put("success", successMap.getOrDefault(entry.getKey(), 0));
            hourlyTrend.add(item);
        }
        stats.put("hourlyTrend24h", hourlyTrend);

        // 待人工处理数
        stats.put("pendingManual", sagaLogRepository.findPendingManual().size());

        return stats;
    }

    /**
     * 获取待人工处理的 Saga 列表(补偿失败)
     */
    public List<SagaLog> getPendingManual() {
        return sagaLogRepository.findPendingManual();
    }

    /**
     * 手动重试 Saga(仅 FAILED/COMPENSATED 状态可重试)
     */
    @Transactional
    public SagaLog retry(String sagaId, String operator) {
        SagaLog sagaLog = sagaLogRepository.findBySagaId(sagaId)
            .orElseThrow(() -> new BizException(404, "Saga 不存在"));

        if (!Arrays.asList("FAILED", "COMPENSATED").contains(sagaLog.getState())) {
            throw new BizException(400, "当前状态 [" + sagaLog.getState() + "] 不允许重试,仅 FAILED/COMPENSATED 状态可重试");
        }

        sagaLog.setState("STARTED");
        sagaLog.setCurrentStep("RETRY_INIT");
        sagaLog.setErrorMessage("[" + operator + "] 手动重试于 " + LocalDateTime.now());
        sagaLog.setCompletedAt(null);
        sagaLogRepository.save(sagaLog);

        eventStore.append(sagaLog.getSessionId(), "SAGA", sagaId, "SagaRetryRequested",
            Map.of("operator", operator, "retryAt", LocalDateTime.now().toString()));

        log.info("[Saga {}] 操作员 [{}] 触发手动重试", sagaId, operator);
        return sagaLog;
    }

    /**
     * 取消 Saga
     */
    @Transactional
    public SagaLog cancel(String sagaId, String reason, String operator) {
        SagaLog sagaLog = sagaLogRepository.findBySagaId(sagaId)
            .orElseThrow(() -> new BizException(404, "Saga 不存在"));

        if (Arrays.asList("COMPLETED", "COMPENSATED", "CANCELLED").contains(sagaLog.getState())) {
            throw new BizException(400, "当前状态 [" + sagaLog.getState() + "] 不允许取消");
        }

        sagaLog.setState("CANCELLED");
        sagaLog.setErrorMessage("[" + operator + "] 取消 - " + reason);
        sagaLog.setCompletedAt(LocalDateTime.now());
        sagaLogRepository.save(sagaLog);

        eventStore.append(sagaLog.getSessionId(), "SAGA", sagaId, "SagaCancelled",
            Map.of("operator", operator, "reason", reason, "cancelledAt", LocalDateTime.now().toString()));

        log.info("[Saga {}] 操作员 [{}] 取消 Saga,原因:{}", sagaId, operator, reason);
        return sagaLog;
    }

    /**
     * 强制标记完成(慎用)
     */
    @Transactional
    public SagaLog forceComplete(String sagaId, String reason, String operator) {
        SagaLog sagaLog = sagaLogRepository.findBySagaId(sagaId)
            .orElseThrow(() -> new BizException(404, "Saga 不存在"));

        if ("COMPLETED".equals(sagaLog.getState())) {
            throw new BizException(400, "Saga 已是 COMPLETED 状态");
        }

        sagaLog.setState("COMPLETED");
        sagaLog.setErrorMessage("[强制完成 - " + operator + "] " + reason);
        sagaLog.setCompletedAt(LocalDateTime.now());
        sagaLogRepository.save(sagaLog);

        eventStore.append(sagaLog.getSessionId(), "SAGA", sagaId, "SagaForceCompleted",
            Map.of("operator", operator, "reason", reason, "forcedAt", LocalDateTime.now().toString()));

        log.warn("[Saga {}] 操作员 [{}] 强制完成 Saga,原因:{}", sagaId, operator, reason);
        return sagaLog;
    }

    /**
     * 列出所有 Saga 类型(去重)
     */
    public List<String> listSagaTypes() {
        return sagaLogRepository.findAll().stream()
            .map(SagaLog::getSagaType)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
    }
}
