package com.mavis.doublerecording.saga;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Saga 步骤定义
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaStep {

    /** 步骤名称 */
    private String name;

    /** 正向执行(返回结果) */
    private Supplier<Map<String, Object>> forward;

    /** 补偿执行(失败时调用) */
    private java.util.function.Consumer<Map<String, Object>> compensate;

    /** 步骤执行结果(供后续步骤使用) */
    private Map<String, Object> result;

    public SagaStep(String name,
                    Supplier<Map<String, Object>> forward,
                    java.util.function.Consumer<Map<String, Object>> compensate) {
        this.name = name;
        this.forward = forward;
        this.compensate = compensate;
    }
}
