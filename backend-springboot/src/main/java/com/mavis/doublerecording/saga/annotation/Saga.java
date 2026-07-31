package com.mavis.doublerecording.saga.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Saga 注解
 *
 * 标在业务类或方法上,声明该方法需要 Saga 编排管理。
 * 配合 {@link SagaStep} 注解使用,框架会自动:
 * 1. 收集所有 @SagaStep 标注的方法
 * 2. 按 order 顺序顺向执行
 * 3. 任一失败逆序执行补偿
 * 4. 事务隔离:每步骤独立事务,Saga 整体不参与大事务
 *
 * 用法示例:
 * <pre>
 * &#64;Service
 * public class OrderSaga {
 *
 *     &#64;Saga(type = "ORDER_SUBMIT")
 *     public void submitOrder(OrderDTO dto) {
 *         createOrder(dto);
 *         deductStock(dto);
 *         chargePayment(dto);
 *     }
 *
 *     &#64;SagaStep(name = "CREATE_ORDER", compensate = "compensateCreate", order = 1)
 *     public void createOrder(OrderDTO dto) { ... }
 *
 *     &#64;SagaStep(name = "DEDUCT_STOCK", compensate = "compensateDeduct", order = 2)
 *     public void deductStock(OrderDTO dto) { ... }
 *
 *     &#64;SagaStep(name = "CHARGE", compensate = "compensateCharge", order = 3)
 *     public void chargePayment(OrderDTO dto) { ... }
 *
 *     public void compensateCreate(OrderDTO dto, Map context) { ... }
 *     public void compensateDeduct(OrderDTO dto, Map context) { ... }
 *     public void compensateCharge(OrderDTO dto, Map context) { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Saga {

    String type();

    int timeoutSeconds() default 30;

    boolean autoCompensate() default true;

    String sessionKey() default "";

    String operator() default "";
}
