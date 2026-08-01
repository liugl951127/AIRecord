package com.mavis.doublerecording.customer;

import com.mavis.doublerecording.common.IdGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户服务体验增强服务
 *
 * 核心能力:
 * 1. 客户会话接入(扫码/链接)
 * 2. 设备诊断(摄像头/麦克风/网络)
 * 3. 手写签字图像存储
 * 4. 服务评价聚合
 * 5. 流失预测(基于停留时长/网络切换/情绪变化)
 * 6. 服务质量报告
 *
 * 设计原则:
 * - 全异步,不影响主流程
 * - 实时推送(WebSocket) + REST 查询
 * - 隐私优先:签字图 base64 不持久化原始内容
 */
@Slf4j
@Service
public class CustomerExperienceService {

    /**
     * 客户接入会话:sessionId -> CustomerSession
     */
    private final Map<String, CustomerSession> customerSessions = new ConcurrentHashMap<>();

    /**
     * 设备诊断结果
     */
    private final Map<String, DeviceDiagnostic> diagnostics = new ConcurrentHashMap<>();

    /**
     * 签字图(Base64 简化为哈希)
     */
    private final Map<String, SignatureRecord> signatures = new ConcurrentHashMap<>();

    /**
     * 流失预测
     */
    private final Map<String, ChurnPrediction> predictions = new ConcurrentHashMap<>();

    /**
     * 客户接入
     */
    public CustomerSession joinSession(String sessionId, String customerId, String deviceId) {
        CustomerSession session = new CustomerSession();
        session.setSessionId(sessionId);
        session.setCustomerId(customerId);
        session.setDeviceId(deviceId);
        session.setJoinTime(LocalDateTime.now());
        session.setLastActiveTime(LocalDateTime.now());
        session.setStatus("ONLINE");
        session.setCurrentStep(0);
        session.setStepCompleted(new ArrayList<>());
        customerSessions.put(sessionId, session);
        log.info("[客户服务] 客户接入: session={}, customer={}, device={}",
            sessionId, customerId, deviceId);
        return session;
    }

    /**
     * 设备诊断
     */
    public DeviceDiagnostic diagnose(String sessionId, DeviceCheck check) {
        DeviceDiagnostic diag = new DeviceDiagnostic();
        diag.setSessionId(sessionId != null ? sessionId : check.getSessionId());
        diag.setCheckTime(LocalDateTime.now());
        diag.setCameraOk(check.isCameraOk());
        diag.setMicrophoneOk(check.isMicrophoneOk());
        diag.setSpeakerOk(check.isSpeakerOk());
        diag.setNetworkType(check.getNetworkType());
        diag.setBandwidthKbps(check.getBandwidthKbps());
        diag.setLatencyMs(check.getLatencyMs());
        diag.setBattery(check.getBattery());

        // 评分(0-100)
        int score = 0;
        if (check.isCameraOk()) score += 30;
        if (check.isMicrophoneOk()) score += 30;
        if (check.isSpeakerOk()) score += 20;
        if (check.getBandwidthKbps() != null && check.getBandwidthKbps() > 1000) score += 20;
        else if (check.getBandwidthKbps() != null && check.getBandwidthKbps() > 500) score += 10;
        diag.setQualityScore(score);

        // 建议
        List<String> suggestions = new ArrayList<>();
        if (!check.isCameraOk()) suggestions.add("请检查摄像头权限");
        if (!check.isMicrophoneOk()) suggestions.add("请检查麦克风权限");
        if (check.getBandwidthKbps() != null && check.getBandwidthKbps() < 500) {
            suggestions.add("建议切换到 Wi-Fi 网络,保证录制质量");
        }
        if (check.getLatencyMs() != null && check.getLatencyMs() > 200) {
            suggestions.add("网络延迟较高,请靠近路由器或更换网络");
        }
        if (check.getBattery() != null && check.getBattery() < 20) {
            suggestions.add("电量较低,建议接入电源");
        }
        diag.setSuggestions(suggestions);
        diagnostics.put(sessionId, diag);
        return diag;
    }

    /**
     * 提交签字(实际生产存 OSS,这里存哈希)
     */
    public SignatureRecord submitSignature(String sessionId, String nodeId, String imageBase64) {
        SignatureRecord sig = new SignatureRecord();
        sig.setSessionId(sessionId);
        sig.setNodeId(nodeId);
        sig.setSignatureId("SIG-" + IdGenerator.snowflakeHex());
        sig.setImageHash(hashBase64(imageBase64));
        sig.setImageLength(imageBase64 == null ? 0 : imageBase64.length());
        sig.setSubmitTime(LocalDateTime.now());
        sig.setQualityScore(evaluateSignatureQuality(imageBase64));
        signatures.put(sig.getSignatureId(), sig);
        log.info("[客户服务] 签字提交: session={}, node={}, hash={}",
            sessionId, nodeId, sig.getImageHash().substring(0, 12));
        return sig;
    }

    /**
     * 更新客户进度
     */
    public CustomerSession updateProgress(String sessionId, int currentStep, String stepName) {
        CustomerSession session = customerSessions.get(sessionId);
        if (session == null) {
            session = joinSession(sessionId, null, null);
        }
        session.setCurrentStep(currentStep);
        session.setLastActiveTime(LocalDateTime.now());
        if (stepName != null) {
            if (!session.getStepCompleted().contains(stepName)) {
                session.getStepCompleted().add(stepName);
            }
        }
        // 重新评估流失风险
        reEvaluateChurn(session);
        return session;
    }

    /**
     * 流失预测(简化模型)
     * 风险因素:
     * - 设备质量低 +20%
     * - 长时间停留无进展 +15%
     * - 频繁切换页面 +25%
     * - 网络质量下降 +20%
     * - 已完成节点 < 50% +10%
     */
    public ChurnPrediction predictChurn(String sessionId) {
        CustomerSession session = customerSessions.get(sessionId);
        if (session == null) return null;
        return reEvaluateChurn(session);
    }

    private ChurnPrediction reEvaluateChurn(CustomerSession session) {
        ChurnPrediction pred = new ChurnPrediction();
        pred.setSessionId(session.getSessionId());
        pred.setPredictTime(LocalDateTime.now());
        double risk = 0.0;
        List<String> reasons = new ArrayList<>();

        // 1. 设备质量
        DeviceDiagnostic diag = diagnostics.get(session.getSessionId());
        if (diag != null && diag.getQualityScore() < 60) {
            risk += 0.20;
            reasons.add("设备质量评分 " + diag.getQualityScore() + "/100");
        }

        // 2. 长时间停留无进展
        if (session.getJoinTime() != null && session.getLastActiveTime() != null) {
            long idleSeconds = java.time.Duration.between(
                session.getLastActiveTime(), LocalDateTime.now()).getSeconds();
            if (idleSeconds > 60) {
                risk += 0.15;
                reasons.add("已 " + idleSeconds + " 秒无操作");
            }
        }

        // 3. 网络切换
        if (diag != null && diag.getNetworkType() != null
            && (diag.getNetworkType().equals("2G") || diag.getNetworkType().equals("3G"))) {
            risk += 0.20;
            reasons.add("网络类型 " + diag.getNetworkType() + " 较慢");
        }

        // 4. 节点完成度低
        if (session.getStepCompleted() != null && session.getStepCompleted().size() < 3) {
            risk += 0.10;
            reasons.add("已完成节点 " + session.getStepCompleted().size() + " 个");
        }

        risk = Math.min(risk, 1.0);
        pred.setChurnRisk(risk);
        pred.setRiskLevel(risk > 0.6 ? "HIGH" : risk > 0.3 ? "MEDIUM" : "LOW");
        pred.setReasons(reasons);
        pred.setSuggestion(risk > 0.6
            ? "建议坐席主动联系客户,提供协助"
            : risk > 0.3
                ? "建议发送引导提示"
                : "客户状态良好");
        predictions.put(session.getSessionId(), pred);
        return pred;
    }

    /**
     * 客户评价
     */
    public Map<String, Object> submitRating(String sessionId, int stars, String comment, List<String> tags) {
        Map<String, Object> rating = new LinkedHashMap<>();
        rating.put("ratingId", "RAT-" + IdGenerator.snowflakeHex());
        rating.put("sessionId", sessionId);
        rating.put("stars", stars);
        rating.put("comment", comment);
        rating.put("tags", tags);
        rating.put("submitTime", LocalDateTime.now());
        // 实际存 DB
        log.info("[客户服务] 客户评价: session={}, stars={}", sessionId, stars);
        return rating;
    }

    /**
     * 客户离开
     */
    public void leaveSession(String sessionId) {
        CustomerSession session = customerSessions.get(sessionId);
        if (session != null) {
            session.setStatus("OFFLINE");
            session.setLeaveTime(LocalDateTime.now());
        }
    }

    /**
     * 获取客户状态
     */
    public CustomerSession getSession(String sessionId) {
        return customerSessions.get(sessionId);
    }

    public DeviceDiagnostic getDiagnostic(String sessionId) {
        return diagnostics.get(sessionId);
    }

    public ChurnPrediction getPrediction(String sessionId) {
        return predictions.get(sessionId);
    }

    private String hashBase64(String s) {
        if (s == null) return "";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().substring(0, 32);
        } catch (Exception e) {
            return "ERR";
        }
    }

    private int evaluateSignatureQuality(String base64) {
        if (base64 == null || base64.length() < 100) return 30;
        if (base64.length() < 500) return 60;
        if (base64.length() < 5000) return 85;
        return 95;
    }

    // ========== 数据类 ==========

    @Data
    public static class CustomerSession {
        private String sessionId;
        private String customerId;
        private String deviceId;
        private LocalDateTime joinTime;
        private LocalDateTime lastActiveTime;
        private LocalDateTime leaveTime;
        private String status;  // ONLINE/OFFLINE/IDLE
        private int currentStep;
        private List<String> stepCompleted = new ArrayList<>();
    }

    @Data
    public static class DeviceCheck {
        private String sessionId;
        private boolean cameraOk;
        private boolean microphoneOk;
        private boolean speakerOk;
        private String networkType;  // WiFi/4G/5G/3G/2G
        private Integer bandwidthKbps;
        private Integer latencyMs;
        private Integer battery;
    }

    @Data
    public static class DeviceDiagnostic {
        private String sessionId;
        private LocalDateTime checkTime;
        private boolean cameraOk;
        private boolean microphoneOk;
        private boolean speakerOk;
        private String networkType;
        private Integer bandwidthKbps;
        private Integer latencyMs;
        private Integer battery;
        private int qualityScore;
        private List<String> suggestions = new ArrayList<>();
    }

    @Data
    public static class SignatureRecord {
        private String sessionId;
        private String nodeId;
        private String signatureId;
        private String imageHash;
        private int imageLength;
        private LocalDateTime submitTime;
        private int qualityScore;
    }

    @Data
    public static class ChurnPrediction {
        private String sessionId;
        private LocalDateTime predictTime;
        private double churnRisk;  // 0.0-1.0
        private String riskLevel;  // LOW/MEDIUM/HIGH
        private List<String> reasons = new ArrayList<>();
        private String suggestion;
    }
}
