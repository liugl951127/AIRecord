package com.mavis.doublerecording.api;

import com.mavis.doublerecording.agent.AgentAssistantService;
import com.mavis.doublerecording.agent.AgentAssistantService.AgentDashboard;
import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.customer.CustomerExperienceService;
import com.mavis.doublerecording.customer.CustomerExperienceService.ChurnPrediction;
import com.mavis.doublerecording.customer.CustomerExperienceService.CustomerSession;
import com.mavis.doublerecording.customer.CustomerExperienceService.DeviceCheck;
import com.mavis.doublerecording.customer.CustomerExperienceService.DeviceDiagnostic;
import com.mavis.doublerecording.customer.CustomerExperienceService.SignatureRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * H5 客户客户端 API + 坐席辅助 API
 */
@RestController
@RequestMapping("/api/customer-h5")
@RequiredArgsConstructor
public class CustomerH5Controller {

    private final CustomerExperienceService customerService;
    private final AgentAssistantService agentService;

    // ========== H5 客户端 API ==========

    @PostMapping("/join")
    public Result<CustomerSession> join(@RequestBody Map<String, String> req) {
        return Result.ok(customerService.joinSession(
            req.get("sessionId"), req.get("customerId"), req.get("deviceId")));
    }

    @PostMapping("/diagnose")
    public Result<DeviceDiagnostic> diagnose(@RequestBody DeviceCheck check) {
        return Result.ok(customerService.diagnose(check.getSessionId(), check));
    }

    @PostMapping("/signature")
    public Result<SignatureRecord> submitSignature(@RequestBody Map<String, String> req) {
        return Result.ok(customerService.submitSignature(
            req.get("sessionId"), req.get("nodeId"), req.get("imageBase64")));
    }

    @PostMapping("/progress")
    public Result<CustomerSession> updateProgress(@RequestBody Map<String, Object> req) {
        return Result.ok(customerService.updateProgress(
            (String) req.get("sessionId"),
            ((Number) req.get("currentStep")).intValue(),
            (String) req.get("stepName")));
    }

    @PostMapping("/rating")
    public Result<Map<String, Object>> submitRating(@RequestBody Map<String, Object> req) {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) req.get("tags");
        return Result.ok(customerService.submitRating(
            (String) req.get("sessionId"),
            ((Number) req.get("stars")).intValue(),
            (String) req.get("comment"),
            tags == null ? List.of() : tags));
    }

    @PostMapping("/leave/{sessionId}")
    public Result<Void> leave(@PathVariable String sessionId) {
        customerService.leaveSession(sessionId);
        return Result.ok();
    }

    @GetMapping("/session/{sessionId}")
    public Result<CustomerSession> getSession(@PathVariable String sessionId) {
        return Result.ok(customerService.getSession(sessionId));
    }

    @GetMapping("/diagnostic/{sessionId}")
    public Result<DeviceDiagnostic> getDiagnostic(@PathVariable String sessionId) {
        return Result.ok(customerService.getDiagnostic(sessionId));
    }

    @GetMapping("/churn/{sessionId}")
    public Result<ChurnPrediction> getChurn(@PathVariable String sessionId) {
        return Result.ok(customerService.predictChurn(sessionId));
    }

    // ========== 坐席辅助 API ==========

    @GetMapping("/agent/dashboard/{sessionId}")
    public Result<AgentDashboard> dashboard(
            @PathVariable String sessionId,
            @RequestParam int currentNode,
            @RequestParam long elapsed) {
        return Result.ok(agentService.buildDashboard(sessionId, currentNode, elapsed));
    }

    @GetMapping("/agent/scripts")
    public Result<List<AgentAssistantService.ScriptTemplate>> recommendScripts(
            @RequestParam String sessionId,
            @RequestParam String currentNode,
            @RequestParam(required = false, defaultValue = "NORMAL") String mood) {
        return Result.ok(agentService.recommendScripts(sessionId, currentNode, mood));
    }

    @GetMapping("/agent/urge")
    public Result<String> urgeScript(
            @RequestParam String sessionId,
            @RequestParam long idleSeconds) {
        return Result.ok(agentService.generateUrgeScript(sessionId, idleSeconds));
    }

    @GetMapping("/agent/calm/{sessionId}")
    public Result<String> calmScript(@PathVariable String sessionId) {
        return Result.ok(agentService.generateCalmScript(sessionId));
    }

    @GetMapping("/agent/retention/{sessionId}")
    public Result<List<String>> retentionActions(@PathVariable String sessionId) {
        return Result.ok(agentService.generateRetentionActions(
            customerService.predictChurn(sessionId)));
    }
}
