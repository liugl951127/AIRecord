package com.mavis.doublerecording.saga.context;

/**
 * Saga 上下文 ThreadLocal 持有者
 * 在 Saga 执行过程中,任何地方都可以通过静态方法获取/设置上下文
 */
public class SagaContextHolder {

    private static final ThreadLocal<SagaContext> CONTEXT = new ThreadLocal<>();

    public static void set(SagaContext context) {
        CONTEXT.set(context);
    }

    public static SagaContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static boolean isInSaga() {
        return CONTEXT.get() != null;
    }
}
