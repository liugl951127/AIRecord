package com.mavis.doublerecording.saga.aspect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

/**
 * Saga 步骤执行器
 *
 * 通过反射调用原方法,强制使用 REQUIRES_NEW 独立事务
 * 避免 AOP 自调用问题(自调用时 @Transactional 失效)
 *
 * 这是事务隔离的关键:即使切面是外层,每个步骤的事务也是独立的
 */
@Slf4j
@Component
public class SagaStepExecutor {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 在新事务中执行 Saga 步骤
     * REQUIRES_NEW:无论外层是否有事务,都强制开启新事务
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Object executeInNewTx(Object bean, Method method, Object[] args) throws Throwable {
        log.debug("[Saga] 执行步骤: {}.{}", bean.getClass().getSimpleName(), method.getName());
        return method.invoke(bean, args);
    }

    /**
     * 在新事务中执行补偿方法
     * REQUIRES_NEW:独立事务,即使失败也不影响其他业务事务
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Object executeCompensateInNewTx(Object bean, Method method, Object[] args) throws Throwable {
        log.debug("[Saga] 执行补偿: {}.{}", bean.getClass().getSimpleName(), method.getName());
        return method.invoke(bean, args);
    }
}
