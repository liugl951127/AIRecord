package com.mavis.doublerecording.orchestrator;

import com.mavis.doublerecording.domain.session.DoubleRecordingSession;
import com.mavis.doublerecording.domain.session.SessionNode;
import com.mavis.doublerecording.domain.session.SessionNodeRepository;
import com.mavis.doublerecording.domain.session.SessionRepository;
import com.mavis.doublerecording.event.EventStore;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 渠道切换服务
 *
 * 支持客户在购买过程中无缝切换渠道:
 * 1. 线下开始 → 线上完成(回家继续)
 * 2. 线上开始 → 线下签字(到网点补签字)
 * 3. 客户经理端 → 客户APP端(转交)
 *
 * 实现要点:
 * - 会话状态全平台共享(同一 sessionId)
 * - 节点进度实时同步
 * - 已完成节点不重复执行
 * - 切换时记录审计事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSwitchService {

    private final SessionRepository sessionRepository;
    private final SessionNodeRepository sessionNodeRepository;
    private final EventStore eventStore;

    /**
     * 切换渠道
     */
    @Transactional
    public ChannelSwitchResult switchChannel(String sessionId, String newChannel, String operator) {
        DoubleRecordingSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        String oldChannel = session.getChannel();
        log.info("[渠道切换] session={}, {} → {}, operator={}", sessionId, oldChannel, newChannel, operator);

        // 1. 记录切换事件
        Map<String, Object> event = new HashMap<>();
        event.put("oldChannel", oldChannel);
        event.put("newChannel", newChannel);
        event.put("operator", operator);
        event.put("switchTime", LocalDateTime.now());
        event.put("currentNodeSeq", session.getCurrentNodeSeq());
        eventStore.append(sessionId, "CHANNEL_SWITCH", sessionId, "ChannelSwitched", event);

        // 2. 更新会话渠道(remark 字段暂存切换时间)
        session.setChannel(newChannel);
        String existingRemark = session.getRemark();
        String switchMark = "[切换:" + LocalDateTime.now() + " " + oldChannel + "→" + newChannel + "]";
        session.setRemark(existingRemark == null ? switchMark : existingRemark + " " + switchMark);
        sessionRepository.save(session);

        // 3. 构造返回
        ChannelSwitchResult result = new ChannelSwitchResult();
        result.setSessionId(sessionId);
        result.setOldChannel(oldChannel);
        result.setNewChannel(newChannel);
        result.setSwitchedAt(LocalDateTime.now());
        result.setResumeNodeSeq(session.getCurrentNodeSeq());
        result.setCurrentState(session.getCurrentState());

        // 4. 列出已完成/待执行节点
        List<SessionNode> allNodes = sessionNodeRepository
            .findBySessionIdOrderByNodeSeqAsc(sessionId);
        int completed = (int) allNodes.stream()
            .filter(n -> n.getCompletedAt() != null)
            .count();
        int pending = allNodes.size() - completed;
        result.setCompletedNodeCount(completed);
        result.setPendingNodeCount(pending);
        result.setTotalNodeCount(allNodes.size());

        log.info("[渠道切换] 切换完成: session={}, 恢复节点={}, 已完成={}/{}",
            sessionId, result.getResumeNodeSeq(), completed, allNodes.size());

        return result;
    }

    /**
     * 断点续录(异地恢复会话)
     */
    @Transactional
    public DoubleRecordingSession resumeSession(String sessionId, String channel) {
        DoubleRecordingSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        if ("COMPLETED".equals(session.getFinalStatus())) {
            throw new RuntimeException("会话已完成,无法恢复: " + sessionId);
        }

        log.info("[断点续录] session={}, channel={}, currentNode={}, state={}",
            sessionId, channel, session.getCurrentNodeSeq(), session.getCurrentState());

        if (!channel.equals(session.getChannel())) {
            switchChannel(sessionId, channel, "SYSTEM");
        }

        Map<String, Object> event = new HashMap<>();
        event.put("channel", channel);
        event.put("resumedAt", LocalDateTime.now());
        event.put("resumedNodeSeq", session.getCurrentNodeSeq());
        eventStore.append(sessionId, "SESSION_RESUME", sessionId, "SessionResumed", event);

        return session;
    }

    /**
     * 转交会话(从客户经理A转给客户经理B)
     */
    @Transactional
    public void transferAgent(String sessionId, String fromAgentId, String toAgentId, String reason) {
        DoubleRecordingSession session = sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("会话不存在: " + sessionId));

        log.info("[客户经理转交] session={}, {} → {}, reason={}",
            sessionId, fromAgentId, toAgentId, reason);

        Map<String, Object> event = new HashMap<>();
        event.put("fromAgent", fromAgentId);
        event.put("toAgent", toAgentId);
        event.put("reason", reason);
        event.put("transferTime", LocalDateTime.now());
        eventStore.append(sessionId, "AGENT_TRANSFER", sessionId, "AgentTransferred", event);
    }

    /**
     * 获取会话所有节点的执行状态
     */
    public List<NodeExecutionStatus> getNodeExecutionStatus(String sessionId) {
        List<SessionNode> nodes = sessionNodeRepository.findBySessionIdOrderByNodeSeqAsc(sessionId);
        return nodes.stream().map(n -> {
            NodeExecutionStatus s = new NodeExecutionStatus();
            s.setNodeSeq(n.getNodeSeq());
            s.setNodeType(n.getNodeType());
            s.setNodeTitle(n.getNodeTitle());
            s.setStatus(n.getCompletedAt() != null ? "COMPLETED" : "PENDING");
            s.setQualityStatus(n.getQualityStatus());
            s.setCompletedAt(n.getCompletedAt());
            return s;
        }).collect(Collectors.toList());
    }

    @Data
    public static class ChannelSwitchResult {
        private String sessionId;
        private String oldChannel;
        private String newChannel;
        private LocalDateTime switchedAt;
        private int resumeNodeSeq;
        private String currentState;
        private int completedNodeCount;
        private int pendingNodeCount;
        private int totalNodeCount;
    }

    @Data
    public static class NodeExecutionStatus {
        private int nodeSeq;
        private String nodeType;
        private String nodeTitle;
        private String status;       // COMPLETED/PENDING
        private String qualityStatus;  // PASS/FAIL/PENDING
        private LocalDateTime completedAt;
    }
}
