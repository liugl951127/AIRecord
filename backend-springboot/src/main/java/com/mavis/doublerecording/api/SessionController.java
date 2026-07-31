package com.mavis.doublerecording.api;

import com.mavis.doublerecording.api.dto.CreateSessionRequest;
import com.mavis.doublerecording.api.dto.SignRequest;
import com.mavis.doublerecording.api.dto.SubmitNodeRequest;
import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.domain.session.DoubleRecordingSession;
import com.mavis.doublerecording.domain.session.SessionNodeRepository;
import com.mavis.doublerecording.orchestrator.NodeExecutionResult;
import com.mavis.doublerecording.orchestrator.SessionOrchestrator;
import com.mavis.doublerecording.quality.QualityCheckResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 双录会话 API
 */
@Slf4j
@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionOrchestrator orchestrator;
    private final SessionNodeRepository sessionNodeRepository;

    /**
     * 创建双录会话
     */
    @PostMapping("/create")
    public Result<DoubleRecordingSession> create(@Valid @RequestBody CreateSessionRequest request) {
        DoubleRecordingSession session = orchestrator.createSession(request);
        return Result.ok(session);
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/{sessionId}")
    public Result<DoubleRecordingSession> get(@PathVariable String sessionId) {
        return Result.ok(orchestrator.getSession(sessionId));
    }

    /**
     * 获取当前应该执行的话术节点
     */
    @GetMapping("/{sessionId}/current-node")
    public Result<NodeExecutionResult> getCurrentNode(@PathVariable String sessionId) {
        return Result.ok(orchestrator.getCurrentNode(sessionId));
    }

    /**
     * 提交节点完成(包含质检)
     */
    @PostMapping("/{sessionId}/submit-node")
    public Result<QualityCheckResult> submitNode(@PathVariable String sessionId,
                                                  @RequestBody SubmitNodeRequest request) {
        QualityCheckResult result = orchestrator.submitNode(
            sessionId, request.getNodeSeq(),
            request.getAgentContent(), request.getCustomerResponse(),
            request.getDurationSec());
        return Result.ok(result);
    }

    /**
     * 提交风险评估分数
     */
    @PostMapping("/{sessionId}/risk-evaluate")
    public Result<Map<String, Object>> evaluateRisk(@PathVariable String sessionId,
                                                     @RequestBody Map<String, Object> request) {
        Integer score = (Integer) request.get("score");
        return Result.ok(orchestrator.evaluateRisk(sessionId, score));
    }

    /**
     * 启动视频录制
     */
    @PostMapping("/{sessionId}/video/start")
    public Result<String> startVideo(@PathVariable String sessionId) {
        return Result.ok(orchestrator.startVideoRecording(sessionId));
    }

    /**
     * 完成录制 + 触发 Saga
     */
    @PostMapping("/{sessionId}/video/complete")
    public Result<Map<String, Object>> completeVideo(@PathVariable String sessionId,
                                                      @RequestBody Map<String, Object> request) {
        Integer duration = (Integer) request.getOrDefault("duration", 300);
        return Result.ok(orchestrator.completeRecording(sessionId, duration));
    }

    /**
     * 签字
     */
    @PostMapping("/{sessionId}/sign")
    public Result<Map<String, Object>> sign(@PathVariable String sessionId,
                                             @RequestBody(required = false) SignRequest request) {
        String signImage = request != null ? request.getSignImage() : null;
        return Result.ok(orchestrator.sign(sessionId, signImage));
    }

    /**
     * 断点续录
     */
    @GetMapping("/{sessionId}/resume")
    public Result<Map<String, Object>> resume(@PathVariable String sessionId) {
        return Result.ok(orchestrator.resume(sessionId));
    }

    /**
     * 暂停会话
     */
    @PostMapping("/{sessionId}/pause")
    public Result<Void> pause(@PathVariable String sessionId) {
        orchestrator.pause(sessionId);
        return Result.ok();
    }

    /**
     * 获取节点执行明细
     */
    @GetMapping("/{sessionId}/nodes")
    public Result<?> getNodes(@PathVariable String sessionId) {
        return Result.ok(sessionNodeRepository.findBySessionIdOrderByNodeSeqAsc(sessionId));
    }
}
