package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.saga.SagaManager;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Saga 管理 API(供图形化界面调用)
 */
@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
public class SagaAdminController {

    private final SagaManager sagaManager;

    /**
     * 统计信息
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        return Result.ok(sagaManager.getStatistics());
    }

    /**
     * 列表分页查询
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String sagaType,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(sagaManager.listSagas(state, sagaType, sessionId, startTime, page, size));
    }

    /**
     * 所有 Saga 类型
     */
    @GetMapping("/types")
    public Result<List<String>> types() {
        return Result.ok(sagaManager.listSagaTypes());
    }

    /**
     * Saga 详情
     */
    @GetMapping("/{sagaId}")
    public Result<Map<String, Object>> detail(@PathVariable String sagaId) {
        return Result.ok(sagaManager.getSagaDetail(sagaId));
    }

    /**
     * Saga 时间线(基于事件流)
     */
    @GetMapping("/{sagaId}/timeline")
    public Result<List<Map<String, Object>>> timeline(@PathVariable String sagaId) {
        Map<String, Object> detail = sagaManager.getSagaDetail(sagaId);
        com.mavis.doublerecording.domain.saga.SagaLog saga = (com.mavis.doublerecording.domain.saga.SagaLog) detail.get("saga");
        return Result.ok(sagaManager.getTimeline(saga.getSessionId()));
    }

    /**
     * 待人工处理列表
     */
    @GetMapping("/pending-manual")
    public Result<List<com.mavis.doublerecording.domain.saga.SagaLog>> pendingManual() {
        return Result.ok(sagaManager.getPendingManual());
    }

    /**
     * 手动重试
     */
    @PostMapping("/{sagaId}/retry")
    public Result<com.mavis.doublerecording.domain.saga.SagaLog> retry(
            @PathVariable String sagaId,
            @RequestBody(required = false) Map<String, Object> body) {
        String operator = body != null && body.get("operator") != null
            ? (String) body.get("operator") : "anonymous";
        return Result.ok(sagaManager.retry(sagaId, operator));
    }

    /**
     * 取消
     */
    @PostMapping("/{sagaId}/cancel")
    public Result<com.mavis.doublerecording.domain.saga.SagaLog> cancel(
            @PathVariable String sagaId,
            @RequestBody Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null ? (String) body.get("reason") : "未填写原因";
        String operator = body != null && body.get("operator") != null ? (String) body.get("operator") : "anonymous";
        return Result.ok(sagaManager.cancel(sagaId, reason, operator));
    }

    /**
     * 强制完成
     */
    @PostMapping("/{sagaId}/force-complete")
    public Result<com.mavis.doublerecording.domain.saga.SagaLog> forceComplete(
            @PathVariable String sagaId,
            @RequestBody Map<String, Object> body) {
        String reason = body != null && body.get("reason") != null ? (String) body.get("reason") : "未填写原因";
        String operator = body != null && body.get("operator") != null ? (String) body.get("operator") : "anonymous";
        return Result.ok(sagaManager.forceComplete(sagaId, reason, operator));
    }
}
