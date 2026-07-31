package com.mavis.doublerecording.saga;

/**
 * Saga 状态
 */
public enum SagaState {
    STARTED,
    STEP_EXECUTING,
    STEP_DONE,
    FAILED,
    COMPENSATING,
    COMPENSATED,
    COMPLETED
}
