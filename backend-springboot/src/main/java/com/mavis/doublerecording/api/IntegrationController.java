package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.common.Sm4Util;
import com.mavis.doublerecording.orchestrator.ChannelSwitchService;
import com.mavis.doublerecording.orchestrator.ChannelSwitchService.ChannelSwitchResult;
import com.mavis.doublerecording.orchestrator.ChannelSwitchService.NodeExecutionStatus;
import com.mavis.doublerecording.quality.AiQualityEngine;
import com.mavis.doublerecording.quality.AiQualityEngine.RealTimeCheckResult;
import com.mavis.doublerecording.risk.SmartRiskEngine;
import com.mavis.doublerecording.risk.SmartRiskEngine.AssessmentResult;
import com.mavis.doublerecording.risk.SmartRiskEngine.MatchResult;
import com.mavis.doublerecording.script.ScriptGrayReleaseService;
import com.mavis.doublerecording.video.SignalingService;
import com.mavis.doublerecording.video.SignalingService.SignalingRoom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 集成能力 API - 统一暴露扩展功能
 *
 * 包含:
 * - SM4 国密加密
 * - WebRTC 信令
 * - AI 实时质检
 * - 渠道切换
 * - 灰度发布
 * - 智能风评
 */
@Slf4j
@RestController
@RequestMapping("/api/integration")
@RequiredArgsConstructor
public class IntegrationController {

    private final SignalingService signalingService;
    private final AiQualityEngine aiQualityEngine;
    private final ChannelSwitchService channelSwitchService;
    private final ScriptGrayReleaseService grayReleaseService;
    private final SmartRiskEngine smartRiskEngine;

    // ========== 1. SM4 国密加密 ==========

    @PostMapping("/sm4/encrypt")
    public Result<Map<String, String>> sm4Encrypt(@RequestBody Map<String, String> req) {
        String plain = req.get("plaintext");
        String encrypted = Sm4Util.encrypt(plain);
        return Result.ok(Map.of(
            "plaintext", plain,
            "ciphertext", encrypted,
            "algorithm", "SM4/CBC/PKCS5Padding"
        ));
    }

    @PostMapping("/sm4/decrypt")
    public Result<Map<String, String>> sm4Decrypt(@RequestBody Map<String, String> req) {
        String cipher = req.get("ciphertext");
        String decrypted = Sm4Util.decrypt(cipher);
        return Result.ok(Map.of(
            "ciphertext", cipher,
            "plaintext", decrypted
        ));
    }

    // ========== 2. WebRTC 信令 ==========

    @PostMapping("/webrtc/room/create")
    public Result<SignalingRoom> createRoom(@RequestBody Map<String, String> req) {
        String sessionId = req.get("sessionId");
        String customerId = req.get("customerId");
        String agentId = req.get("agentId");
        SignalingRoom room = signalingService.createRoom(sessionId, customerId, agentId);
        return Result.ok(room);
    }

    @GetMapping("/webrtc/rooms/active")
    public Result<List<SignalingRoom>> listActiveRooms() {
        return Result.ok(signalingService.listActiveRooms());
    }

    @PostMapping("/webrtc/room/{roomId}/leave")
    public Result<Void> leaveRoom(@PathVariable String roomId, @RequestParam String userId) {
        signalingService.leaveRoom(roomId, userId);
        return Result.ok();
    }

    // ========== 3. AI 实时质检 ==========

    @PostMapping("/ai-quality/real-time")
    public Result<RealTimeCheckResult> realTimeCheck(@RequestBody Map<String, String> req) {
        String sessionId = req.get("sessionId");
        String text = req.get("text");
        String speaker = req.getOrDefault("speaker", "AGENT");
        RealTimeCheckResult r = aiQualityEngine.realTimeCheck(sessionId, text, speaker);
        return Result.ok(r);
    }

    @PostMapping("/ai-quality/node")
    public Result<Object> nodeCheck(@RequestBody Map<String, Object> req) {
        String sessionId = (String) req.get("sessionId");
        int nodeSeq = (Integer) req.get("nodeSeq");
        String speaker = (String) req.getOrDefault("speaker", "AGENT");
        String text = (String) req.get("text");
        int duration = (Integer) req.getOrDefault("duration", 0);
        Object r = aiQualityEngine.checkNode(sessionId, nodeSeq, speaker, text, duration);
        return Result.ok(r);
    }

    // ========== 4. 渠道切换 ==========

    @PostMapping("/channel/switch")
    public Result<ChannelSwitchResult> switchChannel(@RequestBody Map<String, String> req) {
        ChannelSwitchResult r = channelSwitchService.switchChannel(
            req.get("sessionId"), req.get("newChannel"), req.get("operator"));
        return Result.ok(r);
    }

    @PostMapping("/session/resume")
    public Result<Object> resumeSession(@RequestBody Map<String, String> req) {
        Object session = channelSwitchService.resumeSession(req.get("sessionId"), req.get("channel"));
        return Result.ok(session);
    }

    @GetMapping("/session/{sessionId}/nodes")
    public Result<List<NodeExecutionStatus>> getNodeStatus(@PathVariable String sessionId) {
        return Result.ok(channelSwitchService.getNodeExecutionStatus(sessionId));
    }

    // ========== 5. 话术灰度发布 ==========

    @PostMapping("/gray/register")
    public Result<Void> registerGrayRule(@RequestBody Map<String, Object> req) {
        String templateId = (String) req.get("templateId");
        String version = (String) req.get("version");
        int bucket = (Integer) req.getOrDefault("bucket", 10);
        @SuppressWarnings("unchecked")
        List<String> channels = (List<String>) req.getOrDefault("channels", List.of());
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) req.getOrDefault("customerTags", List.of());
        ScriptGrayReleaseService.GrayRule rule = new ScriptGrayReleaseService.GrayRule();
        rule.setBucket(bucket);
        rule.getChannels().addAll(channels);
        rule.getCustomerTags().addAll(tags);
        grayReleaseService.registerGrayRule(templateId, version, rule);
        return Result.ok();
    }

    @PostMapping("/gray/ramp-up")
    public Result<Void> rampUp(@RequestBody Map<String, Object> req) {
        grayReleaseService.rampUp(
            (String) req.get("templateId"),
            (String) req.get("version"),
            (Integer) req.get("bucket"));
        return Result.ok();
    }

    @GetMapping("/gray/stats")
    public Result<Map<String, Long>> grayStats() {
        return Result.ok(grayReleaseService.getGrayStats());
    }

    // ========== 6. 智能风评 ==========

    @PostMapping("/risk/assess")
    public Result<AssessmentResult> assessRisk(@RequestBody Map<String, Object> req) {
        String customerId = (String) req.get("customerId");
        @SuppressWarnings("unchecked")
        Map<String, String> answers = (Map<String, String>) req.get("answers");
        AssessmentResult r = smartRiskEngine.assess(customerId, answers);
        return Result.ok(r);
    }

    @GetMapping("/risk/{customerId}/latest")
    public Result<AssessmentResult> getLatest(@PathVariable String customerId) {
        return smartRiskEngine.getLatestValidAssessment(customerId)
            .map(Result::ok)
            .orElseGet(() -> Result.<AssessmentResult>fail(404, "无有效评估记录"));
    }

    @PostMapping("/risk/match-check")
    public Result<MatchResult> checkMatch(@RequestBody Map<String, String> req) {
        MatchResult r = smartRiskEngine.checkMatch(req.get("customerLevel"), req.get("productLevel"));
        return Result.ok(r);
    }

    @GetMapping("/risk/questions")
    public Result<Map<String, SmartRiskEngine.QuestionDef>> getQuestions() {
        return Result.ok(smartRiskEngine.getQuestions());
    }
}
