package com.mavis.doublerecording.quality;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.domain.quality.QualityRule;
import com.mavis.doublerecording.domain.quality.QualityRuleRepository;
import com.mavis.doublerecording.domain.script.ScriptKeyword;
import com.mavis.doublerecording.script.ScriptEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 统一质检引擎
 *
 * 检查项:
 * 1. 必含关键词(P0/P1)
 * 2. 禁止性表述
 * 3. 客户回应有效性
 * 4. 朗读时长
 * 5. 状态完整性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityEngine {

    private final QualityRuleRepository ruleRepository;
    private final ScriptEngine scriptEngine;
    private final ObjectMapper objectMapper;

    /**
     * 实时质检 - 检查单个节点
     *
     * @param templateId     模板ID
     * @param version        模板版本
     * @param nodeSeq        节点序号
     * @param agentContent   销售员朗读内容
     * @param customerResponse 客户回应
     * @param durationSec    朗读时长(秒)
     */
    public QualityCheckResult checkNode(String templateId, String version, int nodeSeq,
                                        String agentContent, String customerResponse, int durationSec) {
        QualityCheckResult result = new QualityCheckResult();

        // 1. 必含关键词检测
        checkRequiredKeywords(templateId, version, nodeSeq, agentContent, result);

        // 2. 禁止性表述检测
        checkForbiddenPhrases(agentContent, result);

        // 3. 客户回应检测
        checkCustomerResponse(agentContent, nodeSeq, customerResponse, result);

        // 4. 时长检测
        checkDuration(templateId, version, nodeSeq, durationSec, result);

        // 汇总
        summarizeResult(result);
        return result;
    }

    /**
     * 检查必含关键词
     */
    private void checkRequiredKeywords(String templateId, String version, int nodeSeq,
                                       String content, QualityCheckResult result) {
        if (content == null) content = "";

        List<ScriptKeyword> keywords = scriptEngine.loadKeywords(templateId, version, nodeSeq);
        for (ScriptKeyword kw : keywords) {
            QualityCheckResult.RuleHit hit = new QualityCheckResult.RuleHit();
            hit.setRuleCode("KEYWORD_" + kw.getKeyword());
            hit.setRuleName("必含关键词:" + kw.getKeyword());
            hit.setSeverity(kw.getPriority());

            if (content.contains(kw.getKeyword())) {
                hit.setResult("PASS");
                hit.setMessage("关键词 '" + kw.getKeyword() + "' 已包含");
                result.setPassedRules(result.getPassedRules() + 1);
            } else {
                hit.setResult("FAIL");
                hit.setMessage("缺少关键词 '" + kw.getKeyword() + "'");
                result.setFailedRules(result.getFailedRules() + 1);
                if ("P0".equals(kw.getPriority())) {
                    result.getP0Missing().add(kw.getKeyword());
                } else {
                    result.getP1Missing().add(kw.getKeyword());
                }
            }
            result.getDetails().add(hit);
        }
    }

    /**
     * 检查禁止性表述
     * 注意:否定形式(如"非保本")不触发
     */
    private void checkForbiddenPhrases(String content, QualityCheckResult result) {
        if (content == null) return;

        // 禁止词及对应的否定白名单前缀
        List<ForbiddenPhrase> forbidden = List.of(
            new ForbiddenPhrase("保本", "非"),
            new ForbiddenPhrase("保证收益", "不"),
            new ForbiddenPhrase("稳赚不赔", null),
            new ForbiddenPhrase("无风险", "非"),
            new ForbiddenPhrase("零风险", "非")
        );

        for (ForbiddenPhrase fp : forbidden) {
            int idx = content.indexOf(fp.phrase);
            if (idx >= 0) {
                // 检查是否在否定白名单中
                if (fp.negatePrefix != null && idx > 0) {
                    String prefix = String.valueOf(content.charAt(idx - 1));
                    if (fp.negatePrefix.contains(prefix)) {
                        continue;  // 跳过否定形式
                    }
                }
                QualityCheckResult.RuleHit hit = new QualityCheckResult.RuleHit();
                hit.setRuleCode("FORBIDDEN_" + fp.phrase);
                hit.setRuleName("禁止性表述:" + fp.phrase);
                hit.setSeverity("P0");
                hit.setResult("FAIL");
                hit.setMessage("检测到禁止性表述 '" + fp.phrase + "'");
                result.getDetails().add(hit);
                result.getForbiddenHit().add(fp.phrase);
                result.setFailedRules(result.getFailedRules() + 1);
                result.setBlockedCount(result.getBlockedCount() + 1);
            }
        }
    }

    /** 禁止词 + 否定白名单前缀 */
    private static class ForbiddenPhrase {
        final String phrase;
        final String negatePrefix;
        ForbiddenPhrase(String phrase, String negatePrefix) {
            this.phrase = phrase;
            this.negatePrefix = negatePrefix;
        }
    }

    /**
     * 客户回应检测
     */
    private void checkCustomerResponse(String agentContent, int nodeSeq,
                                       String customerResponse, QualityCheckResult result) {
        if (customerResponse == null) customerResponse = "";

        // 客户确认类节点(签字前、风险揭示后)必须明确同意
        boolean needsExplicitAgree = agentContent != null
            && (agentContent.contains("是否") || agentContent.contains("请问您"));

        if (needsExplicitAgree) {
            QualityCheckResult.RuleHit hit = new QualityCheckResult.RuleHit();
            hit.setRuleCode("CUSTOMER_AGREE");
            hit.setRuleName("客户明确回应");
            hit.setSeverity("P0");

            if (customerResponse.contains("是") || customerResponse.contains("清楚")
                || customerResponse.contains("明白") || customerResponse.contains("同意")
                || customerResponse.contains("OK") || customerResponse.contains("好")) {
                hit.setResult("PASS");
                hit.setMessage("客户已明确回应");
                result.setPassedRules(result.getPassedRules() + 1);
            } else {
                hit.setResult("FAIL");
                hit.setMessage("客户未明确回应: " + customerResponse);
                result.setFailedRules(result.getFailedRules() + 1);
                result.setBlockedCount(result.getBlockedCount() + 1);
                result.getP0Missing().add("客户明确同意");
            }
            result.getDetails().add(hit);
        }
    }

    /**
     * 时长检测
     */
    private void checkDuration(String templateId, String version, int nodeSeq,
                               int durationSec, QualityCheckResult result) {
        // 风险揭示节点必须 ≥30 秒
        boolean isRiskDisclosure = nodeSeq > 0 && scriptEngine.loadNode(templateId, version, nodeSeq)
            .getNodeType().contains("RISK");

        if (isRiskDisclosure && durationSec < 30) {
            QualityCheckResult.RuleHit hit = new QualityCheckResult.RuleHit();
            hit.setRuleCode("DURATION_RISK");
            hit.setRuleName("风险揭示时长");
            hit.setSeverity("P0");
            hit.setResult("FAIL");
            hit.setMessage("风险揭示节点朗读时长不足30秒,实际:" + durationSec + "s");
            result.getDetails().add(hit);
            result.setFailedRules(result.getFailedRules() + 1);
            result.setBlockedCount(result.getBlockedCount() + 1);
            result.getP0Missing().add("风险揭示时长≥30s");
        }
    }

    /**
     * 汇总质检结果
     */
    private void summarizeResult(QualityCheckResult result) {
        if (!result.getP0Missing().isEmpty() || !result.getForbiddenHit().isEmpty()) {
            result.setStatus("BLOCKED");
            result.setSeverity("P0");
            result.setBlockedCount(result.getBlockedCount());
            result.setMessage("质检阻断,缺失P0项: " + String.join(",", result.getP0Missing())
                + (result.getForbiddenHit().isEmpty() ? "" : " 禁止表述:" + result.getForbiddenHit()));
        } else if (!result.getP1Missing().isEmpty()) {
            result.setStatus("FAIL");
            result.setSeverity("P1");
            result.setAlertCount(result.getAlertCount() + 1);
            result.setMessage("质检告警,缺失P1项: " + String.join(",", result.getP1Missing()));
        } else {
            result.setStatus("PASS");
            result.setSeverity("P2");
            result.setMessage("质检通过");
        }
    }

    /**
     * 离线质检 - 对整段会话生成最终报告
     */
    public Map<String, Object> generateOfflineReport(String sessionId, List<QualityCheckResult> nodeResults) {
        Map<String, Object> report = new HashMap<>();
        int total = nodeResults.size();
        long passed = nodeResults.stream().filter(QualityCheckResult::isPassed).count();
        long blocked = nodeResults.stream().filter(r -> r.getBlockedCount() > 0).count();
        long alert = nodeResults.stream().filter(r -> r.getAlertCount() > 0).count();
        long failed = total - passed;

        report.put("totalNodes", total);
        report.put("passedNodes", passed);
        report.put("failedNodes", failed);
        report.put("blockedCount", blocked);
        report.put("alertCount", alert);
        report.put("finalStatus", failed == 0 ? "PASS" : "FAIL");
        report.put("nodeResults", nodeResults);
        return report;
    }
}
