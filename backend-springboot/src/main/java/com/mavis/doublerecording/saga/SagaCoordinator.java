package com.mavis.doublerecording.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.domain.saga.SagaLog;
import com.mavis.doublerecording.domain.saga.SagaLogRepository;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saga 协调器
 *
 * 负责分布式事务的编排:
 * 1. 顺序执行多个步骤
 * 2. 失败时按逆序执行补偿
 * 3. 保证最终一致性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaCoordinator {

    private final SagaLogRepository sagaLogRepository;
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;

    /**
     * 执行 Saga
     *
     * @param sessionId  业务会话ID
     * @param sagaType   Saga 类型
     * @param steps      步骤列表(按顺序执行)
     * @return 最终结果
     */
    @Transactional
    public Map<String, Object> execute(String sessionId, String sagaType, List<SagaStep> steps) {
        String sagaId = IdGenerator.sagaId();
        log.info("[Saga {}] 开始执行,步骤数:{}", sagaId, steps.size());

        SagaLog sagaLog = new SagaLog();
        sagaLog.setSagaId(sagaId);
        sagaLog.setSessionId(sessionId);
        sagaLog.setSagaType(sagaType);
        sagaLog.setCurrentStep("INIT");
        sagaLog.setState(SagaState.STARTED.name());
        sagaLogRepository.save(sagaLog);

        Map<String, Object> context = new HashMap<>();
        context.put("sagaId", sagaId);
        context.put("sessionId", sessionId);

        // 顺序执行所有步骤
        for (int i = 0; i < steps.size(); i++) {
            SagaStep step = steps.get(i);
            log.info("[Saga {}] 执行步骤 [{}/{}]: {}", sagaId, i + 1, steps.size(), step.getName());

            sagaLog.setCurrentStep(step.getName());
            sagaLog.setState(SagaState.STEP_EXECUTING.name());
            sagaLogRepository.save(sagaLog);

            try {
                // 正向执行
                Map<String, Object> stepResult = step.getForward().get();
                step.setResult(stepResult);
                if (stepResult != null) {
                    context.putAll(stepResult);
                }

                sagaLog.setState(SagaState.STEP_DONE.name());
                try {
                    sagaLog.setPayload(objectMapper.writeValueAsString(context));
                } catch (JsonProcessingException ignore) {
                }
                sagaLogRepository.save(sagaLog);

                log.info("[Saga {}] 步骤 {} 执行成功", sagaId, step.getName());
            } catch (Exception e) {
                log.error("[Saga {}] 步骤 {} 执行失败,启动补偿", sagaId, step.getName(), e);
                sagaLog.setState(SagaState.FAILED.name());
                sagaLog.setErrorMessage(e.getMessage());
                sagaLogRepository.save(sagaLog);

                // 启动补偿
                compensate(sagaId, sessionId, steps, i);
                sagaLog.setState(SagaState.COMPENSATED.name());
                sagaLog.setCompletedAt(LocalDateTime.now());
                sagaLogRepository.save(sagaLog);

                throw new BizException(500, "Saga 执行失败: " + e.getMessage(), e);
            }
        }

        // 全部成功
        sagaLog.setState(SagaState.COMPLETED.name());
        sagaLog.setCompletedAt(LocalDateTime.now());
        try {
            sagaLog.setPayload(objectMapper.writeValueAsString(context));
        } catch (JsonProcessingException ignore) {
        }
        sagaLogRepository.save(sagaLog);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sagaId", sagaId);
        payload.put("steps", steps.size());
        eventStore.append(sessionId, "SAGA", sagaId, "SagaCompleted", payload);

        log.info("[Saga {}] 全部步骤执行完成", sagaId);
        return context;
    }

    /**
     * 补偿执行(逆序)
     */
    private void compensate(String sagaId, String sessionId, List<SagaStep> steps, int failedIndex) {
        log.warn("[Saga {}] 启动补偿,失败步骤:{}/{}", sagaId, failedIndex + 1, steps.size());

        for (int i = failedIndex; i >= 0; i--) {
            SagaStep step = steps.get(i);
            if (step.getCompensate() != null) {
                try {
                    log.info("[Saga {}] 补偿步骤: {}", sagaId, step.getName());
                    step.getCompensate().accept(step.getResult());
                } catch (Exception e) {
                    log.error("[Saga {}] 补偿步骤 {} 失败", sagaId, step.getName(), e);
                    // 补偿失败,记录到事件,人工介入
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("sagaId", sagaId);
                    payload.put("step", step.getName());
                    payload.put("error", e.getMessage());
                    eventStore.append(sessionId, "SAGA", sagaId, "CompensationFailed", payload);
                }
            }
        }
    }

    /**
     * 查询 Saga 状态
     */
    public SagaLog getSagaLog(String sessionId) {
        return sagaLogRepository.findBySessionId(sessionId).orElse(null);
    }
}
