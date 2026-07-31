package com.mavis.doublerecording.saga.aspect;

import com.mavis.doublerecording.domain.saga.SagaLog;
import com.mavis.doublerecording.domain.saga.SagaLogRepository;
import com.mavis.doublerecording.saga.annotation.Saga;
import com.mavis.doublerecording.saga.annotation.SagaStep;
import com.mavis.doublerecording.saga.context.SagaContext;
import com.mavis.doublerecording.saga.context.SagaContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Saga 切面 - 拦截 @Saga 注解方法,自动编排 @SagaStep 步骤
 *
 * 事务隔离性(关键设计):
 * - @Saga 入口:由 SagaStepExecutor.executeInNewTx 强制 REQUIRES_NEW
 * - @SagaStep 步骤:REQUIRES_NEW - 独立事务
 * - 补偿方法:REQUIRES_NEW - 独立事务
 * - SagaLog 持久化:REQUIRES_NEW - 不影响业务
 */
@Slf4j
@Aspect
@Component
public class SagaAspect {

    @Autowired
    private SagaStepExecutor stepExecutor;

    @Autowired
    private SagaLogRepository sagaLogRepository;

    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<Class<?>, List<StepInfo>> stepInfoCacheMap = new ConcurrentHashMap<>();

    public SagaAspect() {
        log.info("[Saga] SagaAspect 已初始化 - 切面已加载");
    }

    @Around("execution(* com.mavis.doublerecording.saga.example..*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object target = pjp.getTarget();
        Object[] args = pjp.getArgs();

        // 找 @Saga 注解(方法级或类级)
        Saga saga = method.getAnnotation(Saga.class);
        if (saga == null) {
            saga = target.getClass().getAnnotation(Saga.class);
        }
        if (saga == null) {
            return pjp.proceed();
        }

        // 收集所有步骤
        List<StepInfo> steps = collectSteps(target.getClass());
        if (steps.isEmpty()) {
            log.warn("[Saga] {} 未定义 @SagaStep 步骤,直接执行业务方法", saga.type());
            return pjp.proceed();
        }

        // 创建 SagaContext
        SagaContext ctx = new SagaContext();
        ctx.setSagaId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        ctx.setSagaType(saga.type());
        ctx.setSessionId(evalSpel(saga.sessionKey(), method, args,
            args.length > 0 && args[0] != null ? args[0].toString() : ""));
        ctx.setOperator(evalSpel(saga.operator(), method, args, "system"));

        SagaContextHolder.set(ctx);
        List<StepInfo> executedSteps = new ArrayList<>();

        // 记录 Saga 启动
        SagaLog sagaLog = new SagaLog();
        sagaLog.setSagaId(ctx.getSagaId());
        sagaLog.setSessionId(ctx.getSessionId());
        sagaLog.setSagaType(ctx.getSagaType());
        sagaLog.setCurrentStep(steps.get(0).name);
        sagaLog.setState("STARTED");
        sagaLog.setErrorMessage(ctx.getOperator());
        saveSagaLog(sagaLog);

        try {
            log.info("[Saga] 开始执行 Saga [{}] type={}, sessionId={}, operator={}, 共 {} 步",
                ctx.getSagaId(), saga.type(), ctx.getSessionId(), ctx.getOperator(), steps.size());

            // 1. 顺向执行每个步骤(独立事务)
            for (int i = 0; i < steps.size(); i++) {
                StepInfo step = steps.get(i);
                ctx.setCurrentStepIndex(i);
                log.info("[Saga] {}/{} 执行步骤: {} (order={})", i + 1, steps.size(), step.name, step.order);

                sagaLog.setCurrentStep(step.name);
                sagaLog.setState("RUNNING");
                saveSagaLog(sagaLog);

                try {
                    Method m = step.method;
                    m.setAccessible(true);
                    Object result = stepExecutor.executeInNewTx(target, m, args);
                    ctx.recordStepResult(step.name, result);
                    executedSteps.add(step);
                    log.info("[Saga] 步骤 {} 成功", step.name);

                    sagaLog.setState("STEP_DONE");
                    saveSagaLog(sagaLog);

                } catch (Throwable stepEx) {
                    String errMsg = stepEx.getMessage();
                    if (errMsg == null || errMsg.isEmpty()) {
                        errMsg = stepEx.getClass().getSimpleName();
                        if (stepEx.getCause() != null) {
                            errMsg += ": " + stepEx.getCause().getMessage();
                        }
                    }
                    log.error("[Saga] 步骤 {} 失败: {}", step.name, errMsg);

                    sagaLog.setState("FAILED");
                    sagaLog.setErrorMessage("步骤 " + step.name + " 失败: " + errMsg);
                    saveSagaLog(sagaLog);

                    if (!step.critical) {
                        log.warn("[Saga] 步骤 {} 非关键,继续执行", step.name);
                        continue;
                    }

                    // 关键步骤失败,触发逆序补偿
                    if (saga.autoCompensate()) {
                        log.info("[Saga] 触发自动补偿,已成功 {} 步", executedSteps.size());
                        compensate(executedSteps, target, args, ctx, sagaLog);
                    } else {
                        log.warn("[Saga] autoCompensate=false,跳过补偿");
                    }
                    sagaLog.setState("COMPENSATED");
                    sagaLog.setErrorMessage("步骤 " + step.name + " 失败,已补偿");
                    saveSagaLog(sagaLog);
                    // 抛运行时异常,SagaResult 不返回(避免污染业务方法签名)
                    throw new RuntimeException("Saga [" + saga.type() + "] 失败: 步骤 "
                        + step.name + " 执行失败 - " + errMsg, stepEx);
                }
            }

            // 全部成功
            sagaLog.setState("COMPLETED");
            saveSagaLog(sagaLog);
            log.info("[Saga] Saga [{}] 全部 {} 步成功", ctx.getSagaId(), steps.size());

            // 调用原方法(返回业务结果) - 返回原方法返回值,不包 SagaResult
            return pjp.proceed();

        } finally {
            SagaContextHolder.clear();
        }
    }

    /**
     * 逆序补偿
     */
    private void compensate(List<StepInfo> executedSteps, Object target, Object[] args, SagaContext ctx, SagaLog sagaLog) {
        for (int i = executedSteps.size() - 1; i >= 0; i--) {
            StepInfo step = executedSteps.get(i);
            if (step.compensateMethod == null) {
                log.warn("[Saga] 步骤 {} 未配置补偿方法,跳过", step.name);
                continue;
            }
            try {
                log.info("[Saga] 补偿步骤: {} -> {}", step.name, step.compensateMethod.getName());
                sagaLog.setState("COMPENSATING");
                sagaLog.setCurrentStep("compensate-" + step.name);
                saveSagaLog(sagaLog);

                Method m = step.compensateMethod;
                m.setAccessible(true);
                Object[] compArgs = buildCompensateArgs(args, ctx, m);
                stepExecutor.executeCompensateInNewTx(target, m, compArgs);

                log.info("[Saga] 补偿 {} 成功", step.name);
            } catch (Throwable e) {
                log.error("[Saga] 补偿 {} 失败: {}", step.name, e.getMessage());
            }
        }
    }

    private void saveSagaLog(SagaLog log) {
        try {
            sagaLogRepository.save(log);
        } catch (Exception e) {
            // 日志保存失败不影响 Saga 流程
            this.log.error("[Saga] 保存 SagaLog 失败: {}", e.getMessage());
        }
    }

    private Object[] buildCompensateArgs(Object[] originalArgs, SagaContext ctx, Method compMethod) {
        Class<?>[] compParamTypes = compMethod.getParameterTypes();
        if (compParamTypes.length == originalArgs.length + 1
                && compParamTypes[compParamTypes.length - 1] == SagaContext.class) {
            Object[] newArgs = Arrays.copyOf(originalArgs, originalArgs.length + 1);
            newArgs[originalArgs.length] = ctx;
            return newArgs;
        }
        return originalArgs;
    }

    /**
     * 收集并缓存类的 @SagaStep
     */
    private List<StepInfo> collectSteps(Class<?> targetClass) {
        if (stepInfoCacheMap.containsKey(targetClass)) {
            return stepInfoCacheMap.get(targetClass);
        }
        // 处理 CGLIB 代理
        Class<?> realClass = targetClass;
        while (realClass != null && realClass.getName().contains("$$")) {
            realClass = realClass.getSuperclass();
        }
        if (realClass == null) realClass = targetClass;

        List<StepInfo> steps = new ArrayList<>();
        for (Method m : realClass.getDeclaredMethods()) {
            SagaStep ss = m.getAnnotation(SagaStep.class);
            if (ss == null) continue;
            StepInfo info = new StepInfo();
            info.name = ss.name();
            info.order = ss.order();
            info.method = m;
            info.critical = ss.critical();
            info.retryable = ss.retryable();
            info.maxRetries = ss.maxRetries();
            if (!ss.compensate().isEmpty()) {
                try {
                    info.compensateMethod = findCompensateMethod(realClass, ss.compensate(), m);
                } catch (Exception e) {
                    log.warn("[Saga] 找不到补偿方法 {}.{}", realClass.getSimpleName(), ss.compensate());
                }
            }
            steps.add(info);
        }
        steps.sort(Comparator.comparingInt(s -> s.order));
        stepInfoCacheMap.put(targetClass, steps);
        log.info("[Saga] 收集到 {} 个步骤: {}", steps.size(),
            steps.stream().map(s -> s.name).reduce("", (a, b) -> a + "," + b));
        return steps;
    }

    private Method findCompensateMethod(Class<?> cls, String name, Method stepMethod) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), stepMethod.getParameterTypes())) {
                return m;
            }
        }
        Class<?>[] stepParams = stepMethod.getParameterTypes();
        Class<?>[] ctxParams = Arrays.copyOf(stepParams, stepParams.length + 1);
        ctxParams[stepParams.length] = SagaContext.class;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), ctxParams)) {
                return m;
            }
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new RuntimeException("找不到补偿方法: " + name);
    }

    private String evalSpel(String expr, Method method, Object[] args, String defaultValue) {
        if (expr == null || expr.isEmpty()) return defaultValue;
        try {
            String[] paramNames = paramNameDiscoverer.getParameterNames(method);
            EvaluationContext ec = new org.springframework.expression.spel.support.StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    ec.setVariable(paramNames[i], args[i]);
                }
            }
            Expression e = spelParser.parseExpression(expr);
            Object val = e.getValue(ec);
            return val != null ? val.toString() : defaultValue;
        } catch (Exception ex) {
            log.warn("[Saga] SpEL 解析失败: {}, 使用默认", ex.getMessage());
            return defaultValue;
        }
    }

    @Data
    public static class StepInfo {
        String name;
        int order;
        Method method;
        Method compensateMethod;
        boolean critical;
        boolean retryable;
        int maxRetries;
    }

    @Data
    public static class SagaResult {
        private boolean success;
        private String sagaId;
        private String message;
        private Object data;

        public SagaResult(boolean success, String sagaId, String message, Object data) {
            this.success = success;
            this.sagaId = sagaId;
            this.message = message;
            this.data = data;
        }
    }
}
