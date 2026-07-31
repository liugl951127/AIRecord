package com.mavis.doublerecording.orchestrator;

import com.mavis.doublerecording.api.dto.CreateSessionRequest;
import com.mavis.doublerecording.chain.ChainService;
import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.domain.event.EventLog;
import com.mavis.doublerecording.domain.quality.QualityReport;
import com.mavis.doublerecording.domain.quality.QualityReportRepository;
import com.mavis.doublerecording.domain.script.ScriptTemplate;
import com.mavis.doublerecording.domain.session.DoubleRecordingSession;
import com.mavis.doublerecording.domain.session.SessionNode;
import com.mavis.doublerecording.domain.session.SessionNodeRepository;
import com.mavis.doublerecording.domain.session.SessionRepository;
import com.mavis.doublerecording.domain.session.SessionState;
import com.mavis.doublerecording.event.EventStore;
import com.mavis.doublerecording.quality.QualityCheckResult;
import com.mavis.doublerecording.quality.QualityEngine;
import com.mavis.doublerecording.risk.RiskEngine;
import com.mavis.doublerecording.saga.SagaCoordinator;
import com.mavis.doublerecording.saga.SagaStep;
import com.mavis.doublerecording.script.ScriptEngine;
import com.mavis.doublerecording.signature.SignatureService;
import com.mavis.doublerecording.video.VideoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 流程编排引擎 - 核心
 *
 * 负责:
 * 1. 创建/恢复双录会话
 * 2. 状态机推进
 * 3. 节点调度
 * 4. 触发 Saga
 * 5. 断点续录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionOrchestrator {

    private final SessionRepository sessionRepository;
    private final SessionNodeRepository sessionNodeRepository;
    private final ScriptEngine scriptEngine;
    private final RiskEngine riskEngine;
    private final QualityEngine qualityEngine;
    private final VideoService videoService;
    private final SignatureService signatureService;
    private final ChainService chainService;
    private final SagaCoordinator sagaCoordinator;
    private final EventStore eventStore;
    private final QualityReportRepository qualityReportRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建双录会话
     */
    @Transactional
    public DoubleRecordingSession createSession(CreateSessionRequest request) {
        log.info("[编排引擎] 创建双录会话: customer={}, product={}, channel={}",
            request.getCustomerId(), request.getProductId(), request.getChannel());

        // 1. 加载话术模板(根据产品+风险等级)
        String riskLevel = request.getRiskLevel();
        if (riskLevel == null) {
            // 兜底:从风评获取
            try {
                riskLevel = riskEngine.getAssessment(request.getCustomerId()).getRiskLevel();
            } catch (Exception e) {
                riskLevel = "R3";
            }
        }
        ScriptTemplate template = scriptEngine.loadTemplate(request.getProductId(), riskLevel);

        // 2. 创建会话
        DoubleRecordingSession session = new DoubleRecordingSession();
        session.setSessionId(IdGenerator.sessionId());
        session.setCustomerId(request.getCustomerId());
        session.setCustomerName(request.getCustomerName());
        session.setProductId(request.getProductId());
        session.setProductName(request.getProductName() != null ? request.getProductName() : template.getTemplateName());
        session.setChannel(request.getChannel() != null ? request.getChannel() : "APP");
        session.setCurrentState(SessionState.CREATED.name());
        session.setCurrentNodeSeq(0);
        session.setRiskLevel(riskLevel);
        session.setScriptTemplateId(template.getTemplateId());
        session.setScriptVersion(template.getVersion());
        session.setOrderAmount(request.getOrderAmount());
        session = sessionRepository.save(session);

        // 3. 发布事件
        Map<String, Object> payload = new HashMap<>();
        payload.put("customerId", request.getCustomerId());
        payload.put("productId", request.getProductId());
        payload.put("channel", session.getChannel());
        payload.put("templateId", template.getTemplateId());
        payload.put("version", template.getVersion());
        eventStore.append(session.getSessionId(), "SESSION", session.getSessionId(), "SessionCreated", payload);

        log.info("[编排引擎] 会话创建成功: sessionId={}", session.getSessionId());
        return session;
    }

    /**
     * 获取当前应该执行的话术节点
     */
    public NodeExecutionResult getCurrentNode(String sessionId) {
        DoubleRecordingSession session = getSession(sessionId);

        // 加载话术节点
        com.mavis.doublerecording.domain.script.ScriptNode scriptNode =
            scriptEngine.loadNode(session.getScriptTemplateId(), session.getScriptVersion(),
                session.getCurrentNodeSeq() + 1);

        // 变量绑定
        Map<String, Object> variables = buildVariables(session, scriptNode);

        // 渲染话术
        String rendered = scriptEngine.renderScript(scriptNode.getScriptContent(), variables);

        NodeExecutionResult result = new NodeExecutionResult();
        result.setNode(scriptNode);
        result.setRenderedContent(rendered);
        result.setRequiredReading("AGENT_MUST_READ_FULL".equals(scriptNode.getTriggerAction())
            || "AGENT_READ".equals(scriptNode.getTriggerAction()));
        result.setRequiresCustomerAgree(
            "WAIT_CUSTOMER_AGREE".equals(scriptNode.getTriggerAction()));
        result.setNextAction(scriptNode.getNextNodeRule());
        return result;
    }

    /**
     * 提交节点完成(包含质检)
     *
     * @param sessionId    会话ID
     * @param nodeSeq      节点序号
     * @param agentContent 销售员实际朗读内容
     * @param customerResponse 客户回应
     * @param durationSec  朗读时长
     * @return 质检结果
     */
    @Transactional
    public QualityCheckResult submitNode(String sessionId, int nodeSeq, String agentContent,
                                          String customerResponse, int durationSec) {
        DoubleRecordingSession session = getSession(sessionId);
        log.info("[编排引擎] 提交节点: sessionId={}, nodeSeq={}", sessionId, nodeSeq);

        if (session.getCurrentNodeSeq() != null && nodeSeq != session.getCurrentNodeSeq() + 1) {
            throw new BizException("节点序号不匹配,期望:" + (session.getCurrentNodeSeq() + 1) + ", 实际:" + nodeSeq);
        }

        // 1. 质检
        QualityCheckResult result = qualityEngine.checkNode(
            session.getScriptTemplateId(), session.getScriptVersion(),
            nodeSeq, agentContent, customerResponse, durationSec);

        // 2. 记录节点执行
        SessionNode nodeRecord = sessionNodeRepository
            .findBySessionIdAndNodeSeq(sessionId, nodeSeq)
            .orElseGet(SessionNode::new);
        nodeRecord.setSessionId(sessionId);
        nodeRecord.setNodeSeq(nodeSeq);
        nodeRecord.setNodeType(getCurrentNode(sessionId).getNode().getNodeType());
        nodeRecord.setNodeTitle(getCurrentNode(sessionId).getNode().getNodeTitle());
        nodeRecord.setScriptContent(agentContent);
        nodeRecord.setCustomerResponse(customerResponse);
        nodeRecord.setQualityStatus(result.getStatus());
        nodeRecord.setQualityMessage(result.getMessage());
        nodeRecord.setMissingKeywords(String.join(",", result.getP0Missing()));
        nodeRecord.setStartedAt(LocalDateTime.now());
        nodeRecord.setCompletedAt(LocalDateTime.now());
        sessionNodeRepository.save(nodeRecord);

        // 3. 阻断处理
        if (result.isBlocked()) {
            session.setCurrentState(SessionState.QUALITY_BLOCKED.name());
            sessionRepository.save(session);

            Map<String, Object> payload = new HashMap<>();
            payload.put("nodeSeq", nodeSeq);
            payload.put("missingKeywords", result.getP0Missing());
            eventStore.append(sessionId, "SESSION", sessionId, "QualityCheckFailed", payload);

            throw new BizException(400, "质检阻断: " + result.getMessage());
        }

        // 4. 推进状态
        if (result.isPassed()) {
            // 推进到下一节点
            session.setCurrentNodeSeq(nodeSeq);
            session.setCurrentState(SessionState.RECORDING.name());
            sessionRepository.save(session);

            Map<String, Object> payload = new HashMap<>();
            payload.put("nodeSeq", nodeSeq);
            payload.put("status", "PASS");
            eventStore.append(sessionId, "SESSION", sessionId, "ScriptNodeCompleted", payload);
        }

        return result;
    }

    /**
     * 风险评估并匹配
     */
    @Transactional
    public Map<String, Object> evaluateRisk(String sessionId, int score) {
        DoubleRecordingSession session = getSession(sessionId);
        log.info("[编排引擎] 风险评估: sessionId={}, score={}", sessionId, score);

        // 保存评估
        var assessment = riskEngine.submitAssessment(session.getCustomerId(), score, "{}");

        session.setRiskLevel(assessment.getRiskLevel());
        session.setRiskScore(score);

        // 校验匹配
        boolean match = riskEngine.matchProduct(assessment.getRiskLevel(),
            getProductRiskLevel(session.getProductId()));

        if (!match) {
            session.setCurrentState(SessionState.RISK_MISMATCH.name());
            sessionRepository.save(session);
            Map<String, Object> result = new HashMap<>();
            result.put("customerLevel", assessment.getRiskLevel());
            result.put("match", false);
            result.put("message", "客户风险等级与产品不匹配,需加载强化话术或客户放弃");
            return result;
        }

        session.setCurrentState(SessionState.RECORDING.name());
        sessionRepository.save(session);

        Map<String, Object> result = new HashMap<>();
        result.put("customerLevel", assessment.getRiskLevel());
        result.put("match", true);
        result.put("message", "风险匹配,可以继续销售流程");
        return result;
    }

    /**
     * 启动视频录制
     */
    public String startVideoRecording(String sessionId) {
        DoubleRecordingSession session = getSession(sessionId);
        return videoService.startRecording(sessionId);
    }

    /**
     * 完成录制 + 触发 Saga(下单+存证)
     */
    @Transactional
    public Map<String, Object> completeRecording(String sessionId, int totalDurationSec) {
        DoubleRecordingSession session = getSession(sessionId);
        log.info("[编排引擎] 完成录制: sessionId={}, duration={}s", sessionId, totalDurationSec);

        session.setCurrentState(SessionState.VIDEO_MERGING.name());
        sessionRepository.save(session);

        // 启动 Saga
        List<SagaStep> steps = new ArrayList<>();
        int segments = Math.max(1, totalDurationSec / 30);

        // Step 1: 视频合成
        steps.add(new SagaStep("VIDEO_MERGE",
            () -> videoService.stopAndMerge(sessionId, totalDurationSec, segments),
            result -> log.warn("[补偿] 视频废弃: {}", result)));

        // Step 2: 订单创建
        steps.add(new SagaStep("ORDER_CREATE",
            () -> {
                Map<String, Object> result = new HashMap<>();
                String orderId = IdGenerator.orderId();
                session.setOrderId(orderId);
                session.setOrderAmount(session.getOrderAmount() != null ? session.getOrderAmount() : new BigDecimal("100000"));
                sessionRepository.save(session);
                result.put("orderId", orderId);
                eventStore.append(sessionId, "ORDER", orderId, "OrderCreated",
                    Map.of("orderId", orderId, "amount", session.getOrderAmount()));
                return result;
            },
            result -> log.warn("[补偿] 订单回滚: {}", result)));

        // Step 3: 区块链存证
        steps.add(new SagaStep("CHAIN_COMMIT",
            () -> {
                Map<String, Object> result = new HashMap<>();
                Map<String, Object> chainResult = chainService.commit(sessionId,
                    session.getVideoHash(), session.getSignImageHash(), session.getOrderId());
                result.putAll(chainResult);
                session.setChainTxHash((String) chainResult.get("txHash"));
                session.setChainBlockHeight((Long) chainResult.get("blockHeight"));
                session.setChainCertNo((String) chainResult.get("certNo"));
                sessionRepository.save(session);
                return result;
            },
            result -> log.warn("[补偿] 区块链存证作废: {}", result)));

        Map<String, Object> sagaResult = sagaCoordinator.execute(sessionId, "DOUBLE_RECORDING_COMPLETE", steps);

        // 触发离线质检
        generateFinalQualityReport(sessionId);

        // 完成
        session.setCurrentState(SessionState.COMPLETED.name());
        session.setFinalStatus("SUCCESS");
        session.setCompletedAt(LocalDateTime.now());
        session.setVideoHash((String) sagaResult.get("sha256"));
        sessionRepository.save(session);

        Map<String, Object> payload = new HashMap<>();
        payload.putAll(sagaResult);
        eventStore.append(sessionId, "SESSION", sessionId, "SessionCompleted", payload);

        return sagaResult;
    }

    /**
     * 签字
     */
    @Transactional
    public Map<String, Object> sign(String sessionId, String signImage) {
        DoubleRecordingSession session = getSession(sessionId);
        log.info("[编排引擎] 客户签字: sessionId={}", sessionId);

        session.setCurrentState(SessionState.SIGNING.name());
        sessionRepository.save(session);

        Map<String, Object> signResult = signatureService.sign(sessionId, signImage);
        session.setSignImageHash((String) signResult.get("signHash"));
        session.setCurrentState(SessionState.PAYMENT_PENDING.name());
        sessionRepository.save(session);

        return signResult;
    }

    /**
     * 断点续录 - 获取会话当前状态
     */
    public Map<String, Object> resume(String sessionId) {
        DoubleRecordingSession session = getSession(sessionId);
        log.info("[编排引擎] 断点续录: sessionId={}, currentNode={}", sessionId, session.getCurrentNodeSeq());

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("currentState", session.getCurrentState());
        result.put("currentNodeSeq", session.getCurrentNodeSeq());
        result.put("riskLevel", session.getRiskLevel());
        result.put("scriptTemplateId", session.getScriptTemplateId());
        result.put("scriptVersion", session.getScriptVersion());

        // 加载已完成节点
        List<SessionNode> completed = sessionNodeRepository.findBySessionIdOrderByNodeSeqAsc(sessionId);
        result.put("completedNodes", completed);

        // 加载事件流
        List<EventLog> events = eventStore.getSessionEvents(sessionId);
        result.put("events", events);

        return result;
    }

    /**
     * 暂停会话
     */
    @Transactional
    public void pause(String sessionId) {
        DoubleRecordingSession session = getSession(sessionId);
        session.setCurrentState(SessionState.PAUSED.name());
        sessionRepository.save(session);

        Map<String, Object> payload = new HashMap<>();
        payload.put("pausedAt", LocalDateTime.now());
        eventStore.append(sessionId, "SESSION", sessionId, "SessionPaused", payload);
        log.info("[编排引擎] 会话已暂停: sessionId={}", sessionId);
    }

    /**
     * 生成最终质检报告
     */
    @Transactional
    public QualityReport generateFinalQualityReport(String sessionId) {
        List<SessionNode> nodes = sessionNodeRepository.findBySessionIdOrderByNodeSeqAsc(sessionId);
        DoubleRecordingSession session = getSession(sessionId);

        int total = nodes.size();
        long passed = nodes.stream().filter(n -> "PASS".equals(n.getQualityStatus())).count();
        long failed = total - passed;
        long blocked = nodes.stream().filter(n -> "BLOCKED".equals(n.getQualityStatus())).count();
        long alert = nodes.stream().filter(n -> "FAIL".equals(n.getQualityStatus())).count();

        QualityReport report = new QualityReport();
        report.setReportId(IdGenerator.reportId());
        report.setSessionId(sessionId);
        report.setRuleVersion("V1.0");
        report.setModelVersion("v1.0");
        report.setTotalNodes(total);
        report.setPassedNodes((int) passed);
        report.setFailedNodes((int) failed);
        report.setBlockedCount((int) blocked);
        report.setAlertCount((int) alert);
        report.setFinalStatus(failed == 0 ? "PASS" : "FAIL");

        // 汇总 P0/P1 缺失
        Set<String> p0Missing = new LinkedHashSet<>();
        Set<String> p1Missing = new LinkedHashSet<>();
        for (SessionNode node : nodes) {
            if (node.getMissingKeywords() != null && !node.getMissingKeywords().isEmpty()) {
                p0Missing.add(node.getMissingKeywords());
            }
        }
        report.setP0Missing(String.join("; ", p0Missing));
        report.setP1Missing(String.join("; ", p1Missing));

        qualityReportRepository.save(report);

        session.setQualityReportId(report.getReportId());
        sessionRepository.save(session);

        return report;
    }

    /**
     * 获取会话
     */
    public DoubleRecordingSession getSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new BizException(404, "会话不存在: " + sessionId));
    }

    /**
     * 构建话术变量
     */
    private Map<String, Object> buildVariables(DoubleRecordingSession session,
                                                com.mavis.doublerecording.domain.script.ScriptNode node) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("agent_name", "客户经理小李");
        vars.put("agent_no", "A2026001");
        vars.put("customer_name", session.getCustomerName() != null ? session.getCustomerName() : "尊敬的客户");
        vars.put("customer_id", session.getCustomerId());
        vars.put("product_name", session.getProductName() != null ? session.getProductName() : "本产品");
        vars.put("risk_level", session.getRiskLevel() != null ? session.getRiskLevel() : "R3");
        vars.put("benchmark", "4.5%/年");

        if (node.getScriptContent() != null && node.getScriptContent().contains("${orderAmount}")) {
            vars.put("orderAmount", session.getOrderAmount() != null ? session.getOrderAmount() : "100000");
        }
        return vars;
    }

    /**
     * 获取产品风险等级(简化:从产品ID推断)
     */
    private String getProductRiskLevel(String productId) {
        if (productId == null) return "R3";
        if (productId.contains("R1")) return "R1";
        if (productId.contains("R2")) return "R2";
        if (productId.contains("R3")) return "R3";
        if (productId.contains("R4")) return "R4";
        if (productId.contains("R5")) return "R5";
        return "R3";
    }
}
