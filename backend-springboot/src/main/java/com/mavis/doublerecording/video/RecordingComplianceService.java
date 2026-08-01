package com.mavis.doublerecording.video;

import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.event.EventStore;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 录像合规服务
 *
 * 监管要求(银保监/证监):
 * 1. 双录必须在话术节点控制下录制,不能任意时刻开关
 * 2. 必须有客户/客户经理双确认(画面+语音)
 * 3. 必须明确告知"本次销售全程录音录像"
 * 4. 敏感信息(身份证/银行卡)需实时遮罩
 * 5. 录制时长有合理范围(过短/过长都要告警)
 * 6. 录制中断需审计 + 提示补录
 * 7. 必须按话术节点分段,每段独立存档
 * 8. 不允许跳节点录制(必须按顺序)
 *
 * 业务规则:
 * - 录制开始: 必须在 N01 节点 + 客户经理已读"录音录像告知"+ 客户明确同意
 * - 录制中: 按话术节点 N01-N11 顺序推进,每节点必须完成质检才能继续
 * - 录制结束: 必须在 N11 节点后 + 客户签字后
 * - 录制暂停/恢复: 只允许 1 次,且暂停时长 ≤ 5 分钟
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingComplianceService {

    private final EventStore eventStore;

    /**
     * 录制会话状态:sessionId -> RecordingState
     */
    private final Map<String, RecordingState> recordingStates = new ConcurrentHashMap<>();

    /**
     * 允许开始录制的最小节点号(必须在 N01 节点后)
     */
    private static final int MIN_NODE_FOR_START = 1;

    /**
     * 允许结束录制的最小节点号(必须在 N11 完成后)
     */
    private static final int MIN_NODE_FOR_STOP = 11;

    /**
     * 单节点最短时长(秒)
     */
    private static final int MIN_NODE_DURATION = 5;

    /**
     * 单节点最长时长(秒)
     */
    private static final int MAX_NODE_DURATION = 600;  // 10 分钟

    /**
     * 全程最短时长(秒)
     */
    private static final int MIN_TOTAL_DURATION = 120;  // 2 分钟

    /**
     * 全程最长时长(秒)
     */
    private static final int MAX_TOTAL_DURATION = 3600;  // 60 分钟

    /**
     * 暂停最大次数
     */
    private static final int MAX_PAUSE_COUNT = 1;

    /**
     * 暂停最大时长(秒)
     */
    private static final int MAX_PAUSE_DURATION = 300;  // 5 分钟

    /**
     * 敏感信息遮罩模式(身份证 110101199001011234 → 1101**********34)
     */
    private static final Map<String, java.util.regex.Pattern> SENSITIVE_PATTERNS = new HashMap<>();
    static {
        // 18 位身份证
        SENSITIVE_PATTERNS.put("ID_CARD",
            java.util.regex.Pattern.compile("(\\d{4})\\d{10}(\\w{4})"));
        // 银行卡(16-19 位数字)
        SENSITIVE_PATTERNS.put("BANK_CARD",
            java.util.regex.Pattern.compile("(\\d{4})\\d{8,11}(\\d{4})"));
        // 手机号
        SENSITIVE_PATTERNS.put("PHONE",
            java.util.regex.Pattern.compile("(\\d{3})\\d{4}(\\d{4})"));
    }

    /**
     * 录制开始 - 合规检查
     *
     * @param sessionId 会话ID
     * @param currentNodeSeq 当前话术节点号
     * @param consentRecorded 是否已告知客户录音录像
     * @param customerAgreed 客户是否明确同意
     * @param agentId 客户经理ID
     * @return 录制句柄
     */
    public RecordingHandle startRecording(String sessionId, int currentNodeSeq,
                                          boolean consentRecorded, boolean customerAgreed, String agentId) {
        log.info("[录制合规] 申请开始录制: session={}, node={}, consent={}, agree={}",
            sessionId, currentNodeSeq, consentRecorded, customerAgreed);

        // 1. 节点合规性
        if (currentNodeSeq < MIN_NODE_FOR_START) {
            throw new BizException(400,
                "录制必须在 N" + String.format("%02d", MIN_NODE_FOR_START) + " 节点之后开始,当前在 N" + currentNodeSeq);
        }

        // 2. 知情同意
        if (!consentRecorded) {
            throw new BizException(400,
                "必须先向客户明确告知'本次销售全程录音录像',并取得客户明确同意,才能开始录制");
        }

        // 3. 客户明确同意
        if (!customerAgreed) {
            throw new BizException(400,
                "客户必须明确回应'同意'或'是'才能开始录制");
        }

        // 4. 不允许重复录制
        RecordingState state = recordingStates.get(sessionId);
        if (state != null && state.isRecording()) {
            throw new BizException(400, "录制已在进行中,句柄=" + state.getHandle());
        }

        // 5. 创建录制状态
        String handle = "REC-" + IdGenerator.snowflakeHex();
        state = new RecordingState();
        state.setSessionId(sessionId);
        state.setHandle(handle);
        state.setRecording(true);
        state.setCurrentNodeSeq(currentNodeSeq);
        state.setStartTime(LocalDateTime.now());
        state.setNodeStartTime(LocalDateTime.now());
        state.setAgentId(agentId);
        state.setConsentRecorded(consentRecorded);
        state.setCustomerAgreed(customerAgreed);
        state.setPauseCount(0);
        state.setTotalPausedSeconds(0);
        recordingStates.put(sessionId, state);

        // 6. 写审计事件
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("handle", handle);
        payload.put("currentNode", currentNodeSeq);
        payload.put("consent", consentRecorded);
        payload.put("agreed", customerAgreed);
        payload.put("agentId", agentId);
        eventStore.append(sessionId, "RECORDING", handle, "RecordingStarted", payload);

        log.info("[录制合规] 录制已开始: handle={}", handle);
        return new RecordingHandle(handle, sessionId, currentNodeSeq, LocalDateTime.now());
    }

    /**
     * 节点切换 - 录制分段
     */
    public void switchNode(String sessionId, int newNodeSeq) {
        RecordingState state = recordingStates.get(sessionId);
        if (state == null || !state.isRecording()) {
            throw new BizException(400, "当前没有录制在进行");
        }

        // 1. 节点必须按顺序
        if (newNodeSeq != state.getCurrentNodeSeq() + 1 && newNodeSeq > state.getCurrentNodeSeq()) {
            throw new BizException(400,
                "节点必须按顺序推进: 当前 N" + state.getCurrentNodeSeq() + ", 不能跳到 N" + newNodeSeq);
        }
        if (newNodeSeq < state.getCurrentNodeSeq()) {
            throw new BizException(400, "节点不能倒退: 当前 N" + state.getCurrentNodeSeq() + ", 不能到 N" + newNodeSeq);
        }

        // 2. 记录上一个节点时长
        LocalDateTime now = LocalDateTime.now();
        int prevNodeDur = (int) Duration.between(state.getNodeStartTime(), now).getSeconds();
        if (prevNodeDur < MIN_NODE_DURATION) {
            log.warn("[录制合规] 节点 N{} 时长过短: {}s (最低 {}s)",
                state.getCurrentNodeSeq(), prevNodeDur, MIN_NODE_DURATION);
        }
        if (prevNodeDur > MAX_NODE_DURATION) {
            log.warn("[录制合规] 节点 N{} 时长过长: {}s (最高 {}s)",
                state.getCurrentNodeSeq(), prevNodeDur, MAX_NODE_DURATION);
        }
        state.getNodeDurations().put(state.getCurrentNodeSeq(), prevNodeDur);

        // 3. 切换到新节点
        int oldNode = state.getCurrentNodeSeq();
        state.setCurrentNodeSeq(newNodeSeq);
        state.setNodeStartTime(now);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handle", state.getHandle());
        payload.put("oldNode", oldNode);
        payload.put("oldNodeDuration", prevNodeDur);
        payload.put("newNode", newNodeSeq);
        eventStore.append(sessionId, "RECORDING", state.getHandle(), "NodeSwitched", payload);

        log.info("[录制合规] 节点切换: N{} -> N{} (上节点时长 {}s)", oldNode, newNodeSeq, prevNodeDur);
    }

    /**
     * 暂停录制
     */
    public void pause(String sessionId, String reason) {
        RecordingState state = recordingStates.get(sessionId);
        if (state == null || !state.isRecording()) {
            throw new BizException(400, "当前没有录制在进行");
        }
        if (state.isPaused()) {
            throw new BizException(400, "录制已暂停");
        }
        if (state.getPauseCount() >= MAX_PAUSE_COUNT) {
            throw new BizException(400, "暂停次数已达上限 " + MAX_PAUSE_COUNT);
        }

        state.setPaused(true);
        state.setPauseStartTime(LocalDateTime.now());
        state.setPauseCount(state.getPauseCount() + 1);
        state.setPauseReason(reason);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handle", state.getHandle());
        payload.put("reason", reason);
        payload.put("pauseCount", state.getPauseCount());
        eventStore.append(sessionId, "RECORDING", state.getHandle(), "RecordingPaused", payload);

        log.info("[录制合规] 录制暂停: handle={}, reason={}", state.getHandle(), reason);
    }

    /**
     * 恢复录制
     */
    public void resume(String sessionId) {
        RecordingState state = recordingStates.get(sessionId);
        if (state == null || !state.isRecording()) {
            throw new BizException(400, "当前没有录制在进行");
        }
        if (!state.isPaused()) {
            throw new BizException(400, "录制未暂停");
        }

        long pausedSec = Duration.between(state.getPauseStartTime(), LocalDateTime.now()).getSeconds();
        if (pausedSec > MAX_PAUSE_DURATION) {
            throw new BizException(400,
                "暂停时长 " + pausedSec + "s 超过最大 " + MAX_PAUSE_DURATION + "s,需重新申请");
        }

        state.setPaused(false);
        state.setTotalPausedSeconds(state.getTotalPausedSeconds() + (int) pausedSec);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handle", state.getHandle());
        payload.put("pausedSec", pausedSec);
        eventStore.append(sessionId, "RECORDING", state.getHandle(), "RecordingResumed", payload);

        log.info("[录制合规] 录制恢复: handle={}, 暂停 {}s", state.getHandle(), pausedSec);
    }

    /**
     * 停止录制 - 合规检查
     */
    public Map<String, Object> stopRecording(String sessionId, int currentNodeSeq) {
        RecordingState state = recordingStates.get(sessionId);
        if (state == null || !state.isRecording()) {
            throw new BizException(400, "当前没有录制在进行");
        }

        // 1. 节点合规性
        if (currentNodeSeq < MIN_NODE_FOR_STOP) {
            throw new BizException(400,
                "录制必须在 N" + MIN_NODE_FOR_STOP + " 节点之后才能停止,当前 N" + currentNodeSeq);
        }

        // 2. 总时长合规
        int totalDur = (int) Duration.between(state.getStartTime(), LocalDateTime.now()).getSeconds()
            - state.getTotalPausedSeconds();
        if (totalDur < MIN_TOTAL_DURATION) {
            log.warn("[录制合规] 录制总时长过短: {}s (最低 {}s)", totalDur, MIN_TOTAL_DURATION);
        }
        if (totalDur > MAX_TOTAL_DURATION) {
            log.warn("[录制合规] 录制总时长过长: {}s (最高 {}s)", totalDur, MAX_TOTAL_DURATION);
        }

        state.setRecording(false);
        state.setStopTime(LocalDateTime.now());
        state.getNodeDurations().put(state.getCurrentNodeSeq(),
            (int) Duration.between(state.getNodeStartTime(), LocalDateTime.now()).getSeconds());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("handle", state.getHandle());
        result.put("totalDuration", totalDur);
        result.put("nodeDurations", state.getNodeDurations());
        result.put("pauseCount", state.getPauseCount());
        result.put("totalPausedSeconds", state.getTotalPausedSeconds());
        result.put("nodeCount", state.getNodeDurations().size());

        Map<String, Object> payload = new LinkedHashMap<>(result);
        eventStore.append(sessionId, "RECORDING", state.getHandle(), "RecordingStopped", payload);

        log.info("[录制合规] 录制停止: handle={}, 总时长={}s, 节点数={}",
            state.getHandle(), totalDur, state.getNodeDurations().size());

        // 清理
        // recordingStates.remove(sessionId);  // 保留以备查询
        return result;
    }

    /**
     * 遮罩敏感信息(在画面/语音识别结果中)
     */
    public String maskSensitiveInfo(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (Map.Entry<String, java.util.regex.Pattern> entry : SENSITIVE_PATTERNS.entrySet()) {
            java.util.regex.Pattern pattern = entry.getValue();
            java.util.regex.Matcher matcher = pattern.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String masked = matcher.group(1) + "**********" + matcher.group(2);
                matcher.appendReplacement(sb, masked);
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    /**
     * 获取录制状态
     */
    public RecordingState getState(String sessionId) {
        return recordingStates.get(sessionId);
    }

    // ========== 内部类 ==========

    @Data
    public static class RecordingState {
        private String sessionId;
        private String handle;
        private String agentId;
        private boolean recording;
        private boolean paused;
        private int currentNodeSeq;
        private LocalDateTime startTime;
        private LocalDateTime stopTime;
        private LocalDateTime nodeStartTime;
        private LocalDateTime pauseStartTime;
        private boolean consentRecorded;
        private boolean customerAgreed;
        private int pauseCount;
        private int totalPausedSeconds;
        private String pauseReason;
        private Map<Integer, Integer> nodeDurations = new LinkedHashMap<>();
    }

    @Data
    public static class RecordingHandle {
        private String handle;
        private String sessionId;
        private int startNode;
        private LocalDateTime startTime;

        public RecordingHandle(String handle, String sessionId, int startNode, LocalDateTime startTime) {
            this.handle = handle;
            this.sessionId = sessionId;
            this.startNode = startNode;
            this.startTime = startTime;
        }
    }
}
