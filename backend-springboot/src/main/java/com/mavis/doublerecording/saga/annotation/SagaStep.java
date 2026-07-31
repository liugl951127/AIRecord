package com.mavis.doublerecording.saga.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Saga 步骤注解
 *
 * 标在业务方法上,标记该方法是 Saga 的一个步骤。
 * 配合 {@link Saga} 使用,框架会自动:
 * 1. 收集该类下所有 @SagaStep 方法
 * 2. 按 order 升序执行
 * 3. 任一失败时,按配置的 compensate 方法逆序补偿
 *
 * 每个步骤必须有独立事务(由框架通过 Propagation.REQUIRES_NEW 保证)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SagaStep {

    String name();

    int order() default Integer.MAX_VALUE;

    String compensate() default "";

    boolean retryable() default false;

    int maxRetries() default 3;

    int retryIntervalMs() default 100;

    boolean critical() default true;
}
