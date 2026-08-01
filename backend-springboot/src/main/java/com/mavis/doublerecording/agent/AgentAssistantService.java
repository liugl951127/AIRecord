package com.mavis.doublerecording.agent;

import com.mavis.doublerecording.customer.CustomerExperienceService;
import com.mavis.doublerecording.customer.CustomerExperienceService.ChurnPrediction;
import com.mavis.doublerecording.customer.CustomerExperienceService.CustomerSession;
import com.mavis.doublerecording.customer.CustomerExperienceService.DeviceDiagnostic;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 坐席服务辅助(提升效率,减少流失)
 *
 * 核心能力:
 * 1. 智能话术推荐(根据当前节点 + 客户情绪 + 产品)
 * 2. 服务倒计时(每节点最佳时长)
 * 3. 实时客户情绪仪表盘
 * 4. 流失预警(多因素模型)
 * 5. 一键操作模板(快速同意/确认/警示话术)
 * 6. 服务质量评分
 */
@Slf4j
@Service
public class AgentAssistantService {

    @Autowired
    private CustomerExperienceService customerService;

    /**
     * 话术模板:nodeId -> List<ScriptTemplate>
     */
    private final Map<String, List<ScriptTemplate>> scriptTemplates = new ConcurrentHashMap<>();

    /**
     * 节点最佳时长
     */
    private final Map<String, Integer> nodeBestDuration = new HashMap<>();

    public AgentAssistantService() {
        initScriptTemplates();
        initNodeBestDuration();
    }

    /**
     * 初始化话术模板
     */
    private void initScriptTemplates() {
        // N01 知情同意
        addScript("N01", List.of(
            new ScriptTemplate("STANDARD", "您好,我是 {agentName},工号 {agentId}。" +
                "为保障您的权益,本次销售过程将全程录音录像,录像内容将作为日后争议处理依据。请问您是否同意?", 1),
            new ScriptTemplate("FRIENDLY", "您好~我是 {agentName},很高兴为您服务!为保护双方权益,我们会进行双录,您看可以吗?", 1),
            new ScriptTemplate("PROFESSIONAL", "您好,根据监管要求,本次销售需要进行双录,我会向您说明相关条款,请问您是否同意?", 1)
        ));
        // N04 风险揭示
        addScript("N04", List.of(
            new ScriptTemplate("STANDARD", "这款产品属于{productType},存在投资风险,可能会损失本金。您是否充分了解并愿意承担?", 3),
            new ScriptTemplate("WARNING", "⚠ 重要提示:本产品非保本浮动收益,过往业绩不代表未来表现。请确认您已了解相关风险。", 3),
            new ScriptTemplate("DETAILED", "我需要详细为您说明:第一,本产品本金可能受损;第二,市场波动可能导致亏损;第三,流动性可能受限。请问您理解了吗?", 3)
        ));
        // N05 产品介绍
        addScript("N05", List.of(
            new ScriptTemplate("STANDARD", "本产品的预期年化收益率为{expectedReturn}%,投资期限 {duration} 天,主要投向 {investmentTarget}。", 1),
            new ScriptTemplate("RISK_FOCUS", "在追求收益的同时,请您注意:本产品不保本,实际收益可能低于预期。", 1)
        ));
        // N10 客户答疑
        addScript("N10", List.of(
            new ScriptTemplate("EMPATHY", "我理解您的顾虑,这是非常正常的反应。让我为您详细解答...", 1),
            new ScriptTemplate("EVIDENCE", "根据监管要求和行业数据,这款产品...请问还有其他疑问吗?", 1)
        ));
    }

    private void addScript(String nodeId, List<ScriptTemplate> templates) {
        scriptTemplates.put(nodeId, templates);
    }

    private void initNodeBestDuration() {
        // 单位:秒(最佳时长)
        nodeBestDuration.put("N01", 60);
        nodeBestDuration.put("N02", 90);
        nodeBestDuration.put("N03", 120);
        nodeBestDuration.put("N04", 180);
        nodeBestDuration.put("N05", 300);
        nodeBestDuration.put("N06", 180);
        nodeBestDuration.put("N07", 120);
        nodeBestDuration.put("N08", 90);
        nodeBestDuration.put("N09", 60);
        nodeBestDuration.put("N10", 240);
        nodeBestDuration.put("N11", 30);
    }

    /**
     * 推荐话术
     */
    public List<ScriptTemplate> recommendScripts(String sessionId, String currentNode, String customerMood) {
        List<ScriptTemplate> templates = scriptTemplates.getOrDefault(currentNode, List.of());
        // 根据客户情绪排序
        if ("AGITATED".equals(customerMood)) {
            // 客户情绪激动,推荐安抚话术
            return templates.stream()
                .filter(t -> t.getStyle().contains("EMPATHY") || t.getStyle().contains("STANDARD"))
                .toList();
        }
        return templates;
    }

    /**
     * 坐席工作台仪表盘数据
     */
    public AgentDashboard buildDashboard(String sessionId, int currentNode, long elapsedSeconds) {
        AgentDashboard dash = new AgentDashboard();
        dash.setSessionId(sessionId);
        dash.setCurrentNode(currentNode);
        dash.setCurrentNodeName("N" + String.format("%02d", currentNode));
        dash.setElapsedSeconds(elapsedSeconds);
        dash.setUpdateTime(LocalDateTime.now());

        // 1. 节点最佳时长 + 进度
        int bestDuration = nodeBestDuration.getOrDefault("N" + String.format("%02d", currentNode), 120);
        dash.setNodeBestDuration(bestDuration);
        dash.setNodeProgress(Math.min(100, (int) (elapsedSeconds * 100.0 / bestDuration)));
        dash.setIsOvertime(elapsedSeconds > bestDuration);

        // 2. 客户会话信息
        CustomerSession customer = customerService.getSession(sessionId);
        if (customer != null) {
            dash.setCustomerOnline("ONLINE".equals(customer.getStatus()));
            dash.setCustomerJoinTime(customer.getJoinTime());
            dash.setStepsCompleted(customer.getStepCompleted() == null ? 0 : customer.getStepCompleted().size());
        }

        // 3. 设备诊断
        DeviceDiagnostic diag = customerService.getDiagnostic(sessionId);
        if (diag != null) {
            dash.setDeviceQualityScore(diag.getQualityScore());
            dash.setDeviceSuggestions(diag.getSuggestions());
        }

        // 4. 流失预测
        ChurnPrediction pred = customerService.getPrediction(sessionId);
        if (pred != null) {
            dash.setChurnRisk(pred.getChurnRisk());
            dash.setChurnLevel(pred.getRiskLevel());
            dash.setChurnReasons(pred.getReasons());
        }

        // 5. 服务质量评分
        dash.setServiceQualityScore(computeQuality(dash));

        return dash;
    }

    private int computeQuality(AgentDashboard dash) {
        int score = 100;
        if (dash.getIsOvertime() != null && dash.getIsOvertime()) score -= 20;
        if (dash.getDeviceQualityScore() != null && dash.getDeviceQualityScore() < 60) score -= 15;
        if (dash.getChurnLevel() != null && "HIGH".equals(dash.getChurnLevel())) score -= 25;
        else if (dash.getChurnLevel() != null && "MEDIUM".equals(dash.getChurnLevel())) score -= 10;
        return Math.max(score, 0);
    }

    /**
     * 一键催促话术(客户长时间无响应时)
     */
    public String generateUrgeScript(String sessionId, long idleSeconds) {
        if (idleSeconds > 300) {
            return "我注意到您有 {n} 分钟没有操作,是否遇到什么困难?需要我协助您吗?".replace("{n}", String.valueOf(idleSeconds / 60));
        } else if (idleSeconds > 120) {
            return "请问您还在吗?有任何疑问都可以告诉我";
        } else if (idleSeconds > 60) {
            return "请继续操作,如有需要请告诉我";
        }
        return "";
    }

    /**
     * 一键安抚话术(客户情绪激动)
     */
    public String generateCalmScript(String sessionId) {
        return "非常理解您的心情,我会尽全力为您解决问题。请问您目前最关心的是哪方面?";
    }

    /**
     * 流失挽回建议
     */
    public List<String> generateRetentionActions(ChurnPrediction pred) {
        if (pred == null || pred.getChurnRisk() < 0.3) {
            return List.of("客户状态良好,继续按流程服务");
        }
        List<String> actions = new ArrayList<>();
        if (pred.getChurnRisk() > 0.6) {
            actions.add("🚨 高流失风险!立即电话联系客户");
            actions.add("💡 提供专属优惠或增值服务");
            actions.add("⏸  暂停流程,优先解决客户问题");
        } else {
            actions.add("⚠  主动发送关怀消息");
            actions.add("💡 简化当前节点操作");
        }
        return actions;
    }

    // ========== 数据类 ==========

    @Data
    public static class ScriptTemplate {
        private String style;        // STANDARD/FRIENDLY/EMPATHY/WARNING/...
        private String template;
        private int priority;        // 1=最高

        public ScriptTemplate(String style, String template, int priority) {
            this.style = style;
            this.template = template;
            this.priority = priority;
        }
    }

    @Data
    public static class AgentDashboard {
        private String sessionId;
        private int currentNode;
        private String currentNodeName;
        private long elapsedSeconds;
        private LocalDateTime updateTime;
        private Integer nodeBestDuration;
        private Integer nodeProgress;
        private Boolean isOvertime;
        private Boolean customerOnline;
        private LocalDateTime customerJoinTime;
        private Integer stepsCompleted;
        private Integer deviceQualityScore;
        private List<String> deviceSuggestions = new ArrayList<>();
        private Double churnRisk;
        private String churnLevel;
        private List<String> churnReasons = new ArrayList<>();
        private Integer serviceQualityScore;
    }
}
