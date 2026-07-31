package com.mavis.doublerecording.saga.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 补偿方法注解
 *
 * 标在方法上,标记该方法是某个 SagaStep 的补偿方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Compensate {

    String forStep();

    int order() default 0;
}
