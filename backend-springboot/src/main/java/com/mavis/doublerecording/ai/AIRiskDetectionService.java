package com.mavis.doublerecording.ai;

import com.mavis.doublerecording.common.IdGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * AI 实时风险识别服务
 *
 * 多模态风险检测(实际生产对接):
 * - 语音: 阿里云 ASR + 讯飞 NLP
 * - 视觉: 旷视/商汤人脸识别
 * - 行为: 自研风险模型
 *
 * 本类提供模拟实现,接口规范,可平滑对接真实 AI 服务
 *
 * 风险检测维度:
 * 1. 语音风险(违规表述/情绪激动/引导性话术)
 * 2. 视觉风险(客户离场/非本人/可疑物品/异常姿态)
 * 3. 行为风险(快速签字/异常操作/超时未响应)
 * 4. 合规风险(漏说关键话术/未明确同意)
 * 5. 异常风险(双录异常中断/网络异常/设备异常)
 */
@Slf4j
@Service
public class AIRiskDetectionService {

    /**
     * 风险等级
     */
    public enum RiskLevel {
        LOW(1, "低风险", "info"),
        MEDIUM(2, "中风险", "warning"),
        HIGH(3, "高风险", "error"),
        CRITICAL(4, "严重风险", "fatal");

        private final int code;
        private final String description;
        private final String tag;

        RiskLevel(int code, String description, String tag) {
            this.code = code;
            this.description = description;
            this.tag = tag;
        }
        public int getCode() { return code; }
        public String getDescription() { return description; }
        public String getTag() { return tag; }
    }

    /**
     * 风险类型
     */
    public enum RiskType {
        // 语音类
        FORBIDDEN_PHRASE("禁用表述", "客户经理说出违规话术,如'保本''稳赚'"),
        EMOTIONAL_AGITATED("情绪激动", "客户情绪激动,可能产生投诉"),
        GUIDED_SCRIPT("引导性话术", "话术有引导/暗示客户倾向"),
        KEYWORD_MISSING("关键词缺失", "未说关键话术如'风险揭示'"),
        // 视觉类
        CUSTOMER_AWAY("客户离场", "画面中客户长时间离场"),
        NON_CUSTOMER("非本人办理", "非客户本人办理业务"),
        SUSPICIOUS_OBJECT("可疑物品", "画面出现可疑物品(他人/工具)"),
        ABNORMAL_POSE("异常姿态", "客户/经理出现异常姿态"),
        // 行为类
        QUICK_SIGN("快速签字", "签字速度过快,可能未仔细阅读"),
        LONG_SILENCE("长时间沉默", "长时间无语音,可能存在问题"),
        // 合规类
        NO_EXPLICIT_CONSENT("无明确同意", "客户未明确回应'同意'"),
        MISSING_DISCLOSURE("未做风险揭示", "未进行风险揭示"),
        // 异常类
        RECORDING_INTERRUPTED("录制中断", "双录录制异常中断"),
        NETWORK_ANOMALY("网络异常", "音视频网络异常"),
        DEVICE_ANOMALY("设备异常", "音视频设备异常");

        private final String category;
        private final String description;
        RiskType(String category, String description) {
            this.category = category;
            this.description = description;
        }
        public String getCategory() { return category; }
        public String getDescription() { return description; }
    }

    /**
     * 风险检测缓存:sessionId -> 检测状态
     */
    private final Map<String, DetectionState> detectionStates = new ConcurrentHashMap<>();

    /**
     * 违规表述模式
     */
    private static final Map<RiskType, List<Pattern>> VIOLATION_PATTERNS = new HashMap<>();
    static {
        VIOLATION_PATTERNS.put(RiskType.FORBIDDEN_PHRASE, Arrays.asList(
            Pattern.compile("(保证|承诺).{0,5}(本金|收益|不亏)"),
            Pattern.compile("保证.{0,3}收益"),
            Pattern.compile("稳赚不赔"),
            Pattern.compile("(无|零|没有)风险"),
            Pattern.compile("肯定(能|会|可以).{0,5}(赚|收益|回报)"),
            Pattern.compile("(内部消息|内幕).{0,3}(股|基金|投资)"),
            Pattern.compile("一定(会|能).{0,5}(涨|赚|盈利)")
        ));
        VIOLATION_PATTERNS.put(RiskType.GUIDED_SCRIPT, Arrays.asList(
            Pattern.compile("(我(们|们公司).{0,3})?(强烈|建议).{0,3}(买|投|选)"),
            Pattern.compile("很多人(都|已经|都买了)"),
            Pattern.compile("只(剩|有).{0,3}(几|一点)"),
            Pattern.compile("错过(就|会).{0,3}(后悔|没机会)")
        ));
    }

    /**
     * 负面情绪关键词
     */
    private static final Set<String> NEGATIVE_EMOTION = new HashSet<>(Arrays.asList(
        "生气", "愤怒", "不满", "差评", "投诉", "退钱", "骗人", "欺诈",
        "不相信", "怀疑", "不靠谱", "垃圾", "废话", "啰嗦", "太快了",
        "听不懂", "算了吧", "不想买了"
    ));

    /**
     * 启动 AI 风险检测
     */
    public DetectionHandle startDetection(String sessionId) {
        DetectionState state = new DetectionState();
        state.setSessionId(sessionId);
        state.setStartTime(LocalDateTime.now());
        state.setActive(true);
        state.setRiskEvents(new ArrayList<>());
        state.setFrameCount(0);
        state.setAudioChunkCount(0);
        state.setHighRiskCount(0);
        state.setCriticalRiskCount(0);
        detectionStates.put(sessionId, state);
        log.info("[AI风控] 启动检测: session={}", sessionId);
        return new DetectionHandle(sessionId, state.getStartTime());
    }

    /**
     * 停止 AI 风险检测,生成检测报告
     */
    public DetectionReport stopDetection(String sessionId) {
        DetectionState state = detectionStates.remove(sessionId);
        if (state == null) {
            return new DetectionReport();
        }
        state.setActive(false);
        state.setEndTime(LocalDateTime.now());

        DetectionReport report = new DetectionReport();
        report.setSessionId(sessionId);
        report.setStartTime(state.getStartTime());
        report.setEndTime(state.getEndTime());
        report.setDurationSeconds(
            (int) (java.time.Duration.between(state.getStartTime(), state.getEndTime()).getSeconds()));
        report.setTotalFrames(state.getFrameCount());
        report.setTotalAudioChunks(state.getAudioChunkCount());
        report.setHighRiskCount(state.getHighRiskCount());
        report.setCriticalRiskCount(state.getCriticalRiskCount());
        report.setTotalRiskEvents(state.getRiskEvents().size());
        report.setEvents(state.getRiskEvents());
        // 综合风险等级
        if (state.getCriticalRiskCount() > 0) {
            report.setOverallLevel(RiskLevel.CRITICAL);
        } else if (state.getHighRiskCount() > 0) {
            report.setOverallLevel(RiskLevel.HIGH);
        } else if (state.getRiskEvents().size() > 3) {
            report.setOverallLevel(RiskLevel.MEDIUM);
        } else {
            report.setOverallLevel(RiskLevel.LOW);
        }
        log.info("[AI风控] 停止检测: session={}, 综合等级={}, 风险事件={}",
            sessionId, report.getOverallLevel().getDescription(), report.getTotalRiskEvents());
        return report;
    }

    /**
     * 1. 实时语音识别 + 风险检测
     *
     * 实际生产: ASR 流式识别 → NLP 模型 → 风险评分
     * 这里模拟: 接收 ASR 文本 → 模式匹配 + 情感分析
     *
     * @param sessionId 会话ID
     * @param asrText ASR 识别的文本
     * @param speaker 说话人(AGENT/CUSTOMER)
     * @return 风险事件(可能多个)
     */
    public List<RiskEvent> detectAudioRisk(String sessionId, String asrText, String speaker) {
        DetectionState state = detectionStates.get(sessionId);
        if (state == null || !state.isActive()) return Collections.emptyList();
        if (asrText == null || asrText.isEmpty()) return Collections.emptyList();

        state.setAudioChunkCount(state.getAudioChunkCount() + 1);
        List<RiskEvent> events = new ArrayList<>();

        // 1. 违规表述检测
        for (Map.Entry<RiskType, List<Pattern>> entry : VIOLATION_PATTERNS.entrySet()) {
            for (Pattern p : entry.getValue()) {
                if (p.matcher(asrText).find()) {
                    RiskEvent event = createEvent(
                        sessionId, entry.getKey(), asrText, speaker,
                        computeViolationLevel(p.pattern(), asrText));
                    events.add(event);
                }
            }
        }

        // 2. 情绪检测(基于关键词)
        RiskLevel emotionLevel = detectEmotion(asrText, speaker);
        if (emotionLevel.getCode() >= RiskLevel.MEDIUM.getCode()) {
            events.add(createEvent(sessionId, RiskType.EMOTIONAL_AGITATED,
                asrText, speaker, emotionLevel));
        }

        // 3. 异常沉默检测(连续 3 段无语音)
        if (asrText.equals("[SILENCE]") || asrText.length() < 2) {
            state.setSilenceCount(state.getSilenceCount() + 1);
            if (state.getSilenceCount() >= 3) {
                events.add(createEvent(sessionId, RiskType.LONG_SILENCE,
                    "连续 " + state.getSilenceCount() + " 段沉默", speaker, RiskLevel.MEDIUM));
            }
        } else {
            state.setSilenceCount(0);
        }

        // 记录
        state.getRiskEvents().addAll(events);
        return events;
    }

    /**
     * 2. 实时视频帧分析 + 风险检测
     *
     * 实际生产: 抽帧 → 人脸检测 + 物体检测 + 姿态估计
     * 这里模拟: 接收帧元数据 → 异常检测
     *
     * @param sessionId 会话ID
     * @param frameMeta 视频帧元数据(JSON 或结构化)
     * @return 风险事件
     */
    public List<RiskEvent> detectVideoRisk(String sessionId, FrameMetadata frameMeta) {
        DetectionState state = detectionStates.get(sessionId);
        if (state == null || !state.isActive()) return Collections.emptyList();
        if (frameMeta == null) return Collections.emptyList();

        state.setFrameCount(state.getFrameCount() + 1);
        List<RiskEvent> events = new ArrayList<>();

        // 1. 客户离场检测
        if (Boolean.FALSE.equals(frameMeta.getCustomerInFrame())) {
            state.setCustomerAwayCount(state.getCustomerAwayCount() + 1);
            if (state.getCustomerAwayCount() >= 10) {  // 连续 10 帧不在
                events.add(createEvent(sessionId, RiskType.CUSTOMER_AWAY,
                    "客户连续 " + state.getCustomerAwayCount() + " 帧不在画面中",
                    "VIDEO", RiskLevel.HIGH));
            }
        } else {
            state.setCustomerAwayCount(0);
        }

        // 2. 非本人办理(人脸不匹配)
        if (frameMeta.getMatchedFaceCount() == 0 && frameMeta.getFaceCount() > 0) {
            events.add(createEvent(sessionId, RiskType.NON_CUSTOMER,
                "画面中检测到人脸但与客户身份证不匹配",
                "VIDEO", RiskLevel.CRITICAL));
        }

        // 3. 可疑物品检测
        if (Boolean.TRUE.equals(frameMeta.getHasSuspiciousObject())) {
            events.add(createEvent(sessionId, RiskType.SUSPICIOUS_OBJECT,
                "画面中检测到可疑物品: " + frameMeta.getSuspiciousObjects(),
                "VIDEO", RiskLevel.HIGH));
        }

        // 4. 异常姿态(转头/离席)
        if (Boolean.TRUE.equals(frameMeta.getAbnormalPose())) {
            events.add(createEvent(sessionId, RiskType.ABNORMAL_POSE,
                "客户出现异常姿态(长时间离席/反复转头)",
                "VIDEO", RiskLevel.MEDIUM));
        }

        state.getRiskEvents().addAll(events);
        return events;
    }

    /**
     * 3. 行为合规检测
     *
     * @param sessionId 会话ID
     * @param currentNodeSeq 当前节点
     * @param action 行为动作(SIGN/SPEAK/WAIT/...)
     * @param durationMs 行为耗时(毫秒)
     */
    public List<RiskEvent> detectBehaviorRisk(String sessionId, int currentNodeSeq,
                                              String action, long durationMs) {
        DetectionState state = detectionStates.get(sessionId);
        if (state == null || !state.isActive()) return Collections.emptyList();

        List<RiskEvent> events = new ArrayList<>();

        // 1. 快速签字检测(签字 < 3 秒)
        if ("SIGN".equals(action) && durationMs < 3000) {
            events.add(createEvent(sessionId, RiskType.QUICK_SIGN,
                "签字耗时仅 " + durationMs + "ms(<3s),可能未仔细阅读",
                "BEHAVIOR", RiskLevel.HIGH));
        }

        // 2. 关键节点未做风险揭示
        if (currentNodeSeq >= 5 && "MISSING_DISCLOSURE".equals(action)) {
            events.add(createEvent(sessionId, RiskType.MISSING_DISCLOSURE,
                "节点 N" + currentNodeSeq + " 未进行风险揭示",
                "BEHAVIOR", RiskLevel.CRITICAL));
        }

        state.getRiskEvents().addAll(events);
        return events;
    }

    /**
     * 4. 设备/网络异常检测
     */
    public List<RiskEvent> detectInfraRisk(String sessionId, String type, String detail) {
        DetectionState state = detectionStates.get(sessionId);
        if (state == null || !state.isActive()) return Collections.emptyList();

        RiskType riskType = switch (type) {
            case "INTERRUPT" -> RiskType.RECORDING_INTERRUPTED;
            case "NETWORK" -> RiskType.NETWORK_ANOMALY;
            case "DEVICE" -> RiskType.DEVICE_ANOMALY;
            default -> null;
        };
        if (riskType == null) return Collections.emptyList();
        return List.of(createEvent(sessionId, riskType, detail, "INFRA", RiskLevel.HIGH));
    }

    /**
     * 5. 情绪分析(基于关键词)
     */
    private RiskLevel detectEmotion(String text, String speaker) {
        int count = 0;
        for (String neg : NEGATIVE_EMOTION) {
            if (text.contains(neg)) count++;
        }
        if (count >= 3) return RiskLevel.CRITICAL;
        if (count >= 2) return RiskLevel.HIGH;
        if (count >= 1) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    /**
     * 计算违规表述的风险等级
     */
    private RiskLevel computeViolationLevel(String pattern, String text) {
        if (text.contains("保证") || text.contains("承诺")) return RiskLevel.CRITICAL;
        if (text.contains("稳赚") || text.contains("无风险")) return RiskLevel.HIGH;
        if (text.contains("肯定") || text.contains("一定")) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    /**
     * 创建风险事件
     */
    private RiskEvent createEvent(String sessionId, RiskType type, String content,
                                   String source, RiskLevel level) {
        RiskEvent event = new RiskEvent();
        event.setEventId("RISK-" + IdGenerator.snowflakeHex());
        event.setSessionId(sessionId);
        event.setType(type);
        event.setTypeName(type.name());
        event.setCategory(type.getCategory());
        event.setDescription(type.getDescription());
        event.setContent(content);
        event.setSource(source);
        event.setLevel(level);
        event.setLevelName(level.getDescription());
        event.setTimestamp(LocalDateTime.now());
        event.setHandled(false);

        if (level == RiskLevel.HIGH) {
            detectionStates.get(sessionId).setHighRiskCount(
                detectionStates.get(sessionId).getHighRiskCount() + 1);
        } else if (level == RiskLevel.CRITICAL) {
            detectionStates.get(sessionId).setCriticalRiskCount(
                detectionStates.get(sessionId).getCriticalRiskCount() + 1);
        }
        log.warn("[AI风控] 风险事件: session={}, type={}, level={}, content={}",
            sessionId, type, level.getDescription(), content);
        return event;
    }

    /**
     * 获取检测状态
     */
    public DetectionState getState(String sessionId) {
        return detectionStates.get(sessionId);
    }

    // ========== 数据类 ==========

    @Data
    public static class DetectionState {
        private String sessionId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private boolean active;
        private long frameCount;
        private long audioChunkCount;
        private int highRiskCount;
        private int criticalRiskCount;
        private int silenceCount;
        private int customerAwayCount;
        private List<RiskEvent> riskEvents;
    }

    @Data
    public static class DetectionHandle {
        private String sessionId;
        private LocalDateTime startTime;
        public DetectionHandle(String sessionId, LocalDateTime startTime) {
            this.sessionId = sessionId;
            this.startTime = startTime;
        }
    }

    @Data
    public static class RiskEvent {
        private String eventId;
        private String sessionId;
        private RiskType type;
        private String typeName;
        private String category;
        private String description;
        private String content;
        private String source;       // AGENT/CUSTOMER/VIDEO/BEHAVIOR/INFRA
        private RiskLevel level;
        private String levelName;
        private LocalDateTime timestamp;
        private boolean handled;
    }

    @Data
    public static class DetectionReport {
        private String sessionId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int durationSeconds;
        private long totalFrames;
        private long totalAudioChunks;
        private int totalRiskEvents;
        private int highRiskCount;
        private int criticalRiskCount;
        private RiskLevel overallLevel;
        private List<RiskEvent> events;
    }

    /**
     * 视频帧元数据
     */
    @Data
    public static class FrameMetadata {
        private long timestamp;
        private int faceCount;               // 检测到的人脸数
        private int matchedFaceCount;         // 与客户匹配的人脸数
        private Boolean customerInFrame;      // 客户是否在画面中
        private Boolean hasSuspiciousObject;  // 是否有可疑物品
        private String suspiciousObjects;     // 可疑物品列表
        private Boolean abnormalPose;         // 异常姿态
        private String poseDetail;            // 姿态详情
    }
}
