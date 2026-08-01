package com.mavis.doublerecording.api;

import com.mavis.doublerecording.ai.AIRiskDetectionService;
import com.mavis.doublerecording.ai.AIRiskDetectionService.*;
import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 实时风险识别 API
 *
 * 接入录像服务,边录边识别,实时告警
 *
 * 风险监控:
 * - 语音: ASR 转写 + NLP 关键词/情感
 * - 视觉: 人脸识别 + 物体检测 + 姿态估计
 * - 行为: 关键动作时长/顺序
 * - 合规: 必说词/必做动作
 * - 异常: 中断/网络/设备
 */
@RestController
@RequestMapping("/api/ai-risk")
@RequiredArgsConstructor
public class AIRiskController {

    private final AIRiskDetectionService aiRiskService;
    private final EventStore eventStore;

    /**
     * 1. 启动 AI 风险检测
     */
    @PostMapping("/start")
    public Result<DetectionHandle> startDetection(@RequestBody Map<String, String> req) {
        String sessionId = req.get("sessionId");
        DetectionHandle handle = aiRiskService.startDetection(sessionId);
        eventStore.append(sessionId, "AI_RISK", sessionId, "AIRiskDetectionStarted", Map.of("sessionId", sessionId));
        return Result.ok(handle);
    }

    /**
     * 2. 停止 + 生成报告
     */
    @PostMapping("/stop/{sessionId}")
    public Result<DetectionReport> stopDetection(@PathVariable String sessionId) {
        DetectionReport report = aiRiskService.stopDetection(sessionId);
        eventStore.append(sessionId, "AI_RISK", sessionId, "AIRiskDetectionStopped", Map.of(
            "overallLevel", report.getOverallLevel() == null ? "" : report.getOverallLevel().name(),
            "totalEvents", report.getTotalRiskEvents(),
            "highRisk", report.getHighRiskCount(),
            "criticalRisk", report.getCriticalRiskCount()
        ));
        return Result.ok(report);
    }

    /**
     * 3. 实时语音风险检测(ASR 流式)
     *
     * 客户端每收到一段 ASR 识别结果就调用
     * 服务端返回该段的所有风险事件
     */
    @PostMapping("/audio")
    public Result<List<RiskEvent>> detectAudio(@RequestBody Map<String, String> req) {
        List<RiskEvent> events = aiRiskService.detectAudioRisk(
            req.get("sessionId"),
            req.get("text"),
            req.getOrDefault("speaker", "AGENT")
        );
        // 高危事件立即写审计
        for (RiskEvent e : events) {
            if (e.getLevel().getCode() >= AIRiskDetectionService.RiskLevel.HIGH.getCode()) {
                eventStore.append(e.getSessionId(), "AI_RISK", e.getEventId(),
                    "HighRiskEvent", Map.of(
                        "type", e.getTypeName(),
                        "level", e.getLevelName(),
                        "content", e.getContent(),
                        "source", e.getSource()
                    ));
            }
        }
        return Result.ok(events);
    }

    /**
     * 4. 实时视频帧分析(每 N 帧调用一次)
     */
    @PostMapping("/video")
    public Result<List<RiskEvent>> detectVideo(@RequestBody Map<String, Object> req) {
        FrameMetadata meta = new FrameMetadata();
        meta.setTimestamp(((Number) req.getOrDefault("timestamp", 0L)).longValue());
        meta.setFaceCount(((Number) req.getOrDefault("faceCount", 0)).intValue());
        meta.setMatchedFaceCount(((Number) req.getOrDefault("matchedFaceCount", 0)).intValue());
        meta.setCustomerInFrame((Boolean) req.getOrDefault("customerInFrame", true));
        meta.setHasSuspiciousObject((Boolean) req.getOrDefault("hasSuspiciousObject", false));
        meta.setSuspiciousObjects((String) req.getOrDefault("suspiciousObjects", ""));
        meta.setAbnormalPose((Boolean) req.getOrDefault("abnormalPose", false));
        meta.setPoseDetail((String) req.getOrDefault("poseDetail", ""));

        List<RiskEvent> events = aiRiskService.detectVideoRisk(
            (String) req.get("sessionId"), meta);

        for (RiskEvent e : events) {
            if (e.getLevel().getCode() >= AIRiskDetectionService.RiskLevel.HIGH.getCode()) {
                eventStore.append(e.getSessionId(), "AI_RISK", e.getEventId(),
                    "HighRiskEvent", Map.of(
                        "type", e.getTypeName(),
                        "level", e.getLevelName(),
                        "content", e.getContent()
                    ));
            }
        }
        return Result.ok(events);
    }

    /**
     * 5. 行为合规检测
     */
    @PostMapping("/behavior")
    public Result<List<RiskEvent>> detectBehavior(@RequestBody Map<String, Object> req) {
        List<RiskEvent> events = aiRiskService.detectBehaviorRisk(
            (String) req.get("sessionId"),
            ((Number) req.get("currentNodeSeq")).intValue(),
            (String) req.get("action"),
            ((Number) req.get("durationMs")).longValue()
        );
        return Result.ok(events);
    }

    /**
     * 6. 基础设施异常(中断/网络/设备)
     */
    @PostMapping("/infra")
    public Result<List<RiskEvent>> detectInfra(@RequestBody Map<String, String> req) {
        List<RiskEvent> events = aiRiskService.detectInfraRisk(
            req.get("sessionId"),
            req.get("type"),
            req.get("detail")
        );
        return Result.ok(events);
    }

    /**
     * 7. 查询检测状态
     */
    @GetMapping("/state/{sessionId}")
    public Result<DetectionState> getState(@PathVariable String sessionId) {
        return Result.ok(aiRiskService.getState(sessionId));
    }

    /**
     * 8. 列出所有风险类型(供前端展示)
     */
    @GetMapping("/risk-types")
    public Result<Map<String, Object>> listRiskTypes() {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (AIRiskDetectionService.RiskType t : AIRiskDetectionService.RiskType.values()) {
            result.put(t.name(), Map.of(
                "category", t.getCategory(),
                "description", t.getDescription()
            ));
        }
        return Result.ok(result);
    }
}
