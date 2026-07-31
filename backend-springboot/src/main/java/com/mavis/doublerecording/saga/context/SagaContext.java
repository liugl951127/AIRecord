package com.mavis.doublerecording.saga.context;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Saga 上下文
 * 在 Saga 执行过程中跨步骤共享数据,通过 ThreadLocal 传递
 */
@Data
@NoArgsConstructor
public class SagaContext {

    private String sagaId;
    private String sessionId;
    private String sagaType;
    private String operator;
    private Map<String, Object> data = new HashMap<>();
    private Map<String, Object> stepResults = new HashMap<>();
    private int currentStepIndex = 0;

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return (T) data.get(key);
    }

    public void recordStepResult(String stepName, Object result) {
        stepResults.put(stepName, result);
    }
}
