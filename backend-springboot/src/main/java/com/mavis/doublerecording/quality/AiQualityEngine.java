package com.mavis.doublerecording.quality;

import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.domain.quality.QualityCheckResult;
import com.mavis.doublerecording.domain.quality.QualityReport;
import com.mavis.doublerecording.domain.quality.QualityRule;
import com.mavis.doublerecording.domain.quality.QualityRuleRepository;
import com.mavis.doublerecording.domain.script.ScriptKeyword;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * AI 智能质检引擎
 *
 * 区别于 QualityEngine(基础规则引擎),本引擎提供:
 * 1. 实时语音流质检(边录边检)
 * 2. 语义理解(模拟):基于关键词+句式模式
 * 3. 违规等级评估(严重/警告/通过)
 * 4. 实时阻断:严重违规立即告警
 *
 * 实际生产可对接:
 * - 阿里云/腾讯云 语音识别 ASR
 * - 讯飞/百度 NLP 语义分析
 * - 自研大模型做合规审查
 *
 * 这里提供简化实现,完整接口可平滑替换
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQualityEngine {

    private final QualityRuleRepository qualityRuleRepository;

    /**
     * 实时质检缓存:sessionId -> 累积文本
     */
    private final Map<String, StringBuffer> sessionTextBuffer = new ConcurrentHashMap<>();

    /**
     * 违规模式(正则表达式)
     */
    private static final Map<String, Pattern> FORBIDDEN_PATTERNS = new HashMap<>();
    static {
        FORBIDDEN_PATTERNS.put("保本承诺", Pattern.compile("(保证|承诺).{0,5}(本金|收益|不亏)"));
        FORBIDDEN_PATTERNS.put("保收益", Pattern.compile("保证.{0,3}收益"));
        FORBIDDEN_PATTERNS.put("稳赚不赔", Pattern.compile("稳赚不赔"));
        FORBIDDEN_PATTERNS.put("无风险", Pattern.compile("(无|零|没有)风险"));
        FORBIDDEN_PATTERNS.put("肯定收益", Pattern.compile("肯定(能|会|可以).{0,5}(赚|收益|回报)"));
    }

    /**
     * 实时流式质检(每收到一段语音片段就调用)
     *
     * @param sessionId 会话ID
     * @param text ASR 识别出的文本
     * @param speaker 说话人(AGENT/CUSTOMER)
     * @return 实时质检结果
     */
    public RealTimeCheckResult realTimeCheck(String sessionId, String text, String speaker) {
        if (text == null || text.isEmpty()) {
            return RealTimeCheckResult.pass();
        }

        // 累积文本到会话缓冲
        StringBuffer buf = sessionTextBuffer.computeIfAbsent(sessionId, k -> new StringBuffer());
        synchronized (buf) {
            buf.append(text).append(" ");
        }

        // 1. 检查禁用表述(最高优先级)
        for (Map.Entry<String, Pattern> entry : FORBIDDEN_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(text).find()) {
                String violationId = IdGenerator.eventId();
                log.warn("[AI质检] 检测到禁用表述: session={}, type={}, text={}",
                    sessionId, entry.getKey(), text);
                return RealTimeCheckResult.block(violationId, "FORBIDDEN_" + entry.getKey(),
                    "客户经理使用了违规表述: " + entry.getKey() + " - " + text, "P0");
            }
        }

        // 2. 检查规则引擎
        List<QualityRule> rules = qualityRuleRepository.findAll();
        for (QualityRule rule : rules) {
            if (!rule.getEnabled()) continue;
            RealTimeCheckResult result = checkSingleRule(rule, text, sessionId);
            if (result != null && !result.isPass()) {
                return result;
            }
        }

        return RealTimeCheckResult.pass();
    }

    /**
     * 节点级完整质检(节点完成时调用)
     */
    public QualityCheckResult checkNode(String sessionId, int nodeSeq, String speaker, String fullText,
                                         int durationSec) {
        log.info("[AI质检] 节点质检: session={}, node={}, speaker={}, textLen={}, dur={}s",
            sessionId, nodeSeq, speaker, fullText.length(), durationSec);

        QualityCheckResult result = new QualityCheckResult();
        result.setCheckId("QC-" + IdGenerator.snowflakeHex());
        result.setSessionId(sessionId);
        result.setNodeSeq(nodeSeq);
        result.setCheckTime(LocalDateTime.now());
        result.setSpeaker(speaker);

        List<QualityCheckResult.Issue> issues = new ArrayList<>();

        // 1. 关键词检测
        List<QualityRule> keywordRules = qualityRuleRepository.findAll();
        for (QualityRule rule : keywordRules) {
            if (!rule.getEnabled()) continue;
            if ("KEYWORD_REQUIRED".equals(rule.getRuleType())) {
                List<String> keywords = parseKeywords(rule.getRuleConfig());
                for (String kw : keywords) {
                    if (!fullText.contains(kw)) {
                        QualityCheckResult.Issue issue = new QualityCheckResult.Issue();
                        issue.setType("KEYWORD_MISSING");
                        issue.setRuleCode(rule.getRuleCode());
                        issue.setSeverity(rule.getSeverity());
                        issue.setMessage(rule.getRuleName() + ": 缺少关键词 '" + kw + "'");
                        issues.add(issue);
                    }
                }
            } else if ("KEYWORD_FORBIDDEN".equals(rule.getRuleType())) {
                List<String> forbidden = parseKeywords(rule.getRuleConfig());
                for (String word : forbidden) {
                    if (fullText.contains(word)) {
                        QualityCheckResult.Issue issue = new QualityCheckResult.Issue();
                        issue.setType("KEYWORD_FORBIDDEN");
                        issue.setRuleCode(rule.getRuleCode());
                        issue.setSeverity(rule.getSeverity());
                        issue.setMessage(rule.getRuleName() + ": 包含禁用词 '" + word + "'");
                        issues.add(issue);
                    }
                }
            } else if ("DURATION_CHECK".equals(rule.getRuleType())) {
                Integer minDuration = parseMinDuration(rule.getRuleConfig());
                if (minDuration != null && durationSec < minDuration) {
                    QualityCheckResult.Issue issue = new QualityCheckResult.Issue();
                    issue.setType("DURATION_INSUFFICIENT");
                    issue.setRuleCode(rule.getRuleCode());
                    issue.setSeverity(rule.getSeverity());
                    issue.setMessage(rule.getRuleName() + ": 时长 " + durationSec + "s < 最低 " + minDuration + "s");
                    issues.add(issue);
                }
            }
        }

        // 2. 客户响应有效性检测
        if ("CUSTOMER".equals(speaker)) {
            boolean hasPositiveResponse = fullText.matches(".*(是|清楚|明白|同意|好的|嗯|对).*");
            if (!hasPositiveResponse && nodeSeq >= 9) {
                // 购买确认/签字类节点必须有明确同意
                QualityCheckResult.Issue issue = new QualityCheckResult.Issue();
                issue.setType("CUSTOMER_NO_RESPONSE");
                issue.setSeverity("P1");
                issue.setMessage("客户未明确回应(是/清楚/明白/同意)");
                issues.add(issue);
            }
        }

        result.setIssues(issues);
        result.setPass(issues.isEmpty() || issues.stream().allMatch(i -> "P2".equals(i.getSeverity())));
        result.setScore(100 - issues.size() * 20);  // 每个问题扣 20 分
        return result;
    }

    /**
     * 完整会话质检(回调用)
     */
    public QualityReport checkFullSession(String sessionId) {
        log.info("[AI质检] 完整会话质检: session={}", sessionId);
        QualityReport report = new QualityReport();
        report.setReportId("QCR-" + IdGenerator.snowflakeHex());
        report.setSessionId(sessionId);
        report.setReportTime(LocalDateTime.now());

        String fullText = sessionTextBuffer.getOrDefault(sessionId, new StringBuffer()).toString();
        report.setFullText(fullText);
        report.setTextLength(fullText.length());
        // 统计关键词命中
        Map<String, Integer> keywordHits = new HashMap<>();
        for (QualityRule rule : qualityRuleRepository.findAll()) {
            if ("KEYWORD_REQUIRED".equals(rule.getRuleType())) {
                for (String kw : parseKeywords(rule.getRuleConfig())) {
                    int count = (fullText.length() - fullText.replace(kw, "").length()) / kw.length();
                    if (count > 0) keywordHits.put(kw, count);
                }
            }
        }
        report.setKeywordHits(keywordHits);
        report.setTotalScore(100);  // 默认满分
        report.setPassed(true);
        return report;
    }

    /**
     * 清理会话缓存
     */
    public void clearSession(String sessionId) {
        sessionTextBuffer.remove(sessionId);
    }

    // ========== 私有方法 ==========

    private RealTimeCheckResult checkSingleRule(QualityRule rule, String text, String sessionId) {
        if ("KEYWORD_FORBIDDEN".equals(rule.getRuleType())) {
            List<String> forbidden = parseKeywords(rule.getRuleConfig());
            for (String word : forbidden) {
                if (text.contains(word)) {
                    return RealTimeCheckResult.block(
                        "QC-" + IdGenerator.snowflakeHex(),
                        rule.getRuleCode(),
                        rule.getRuleName() + ": 检测到禁用词 '" + word + "'",
                        rule.getSeverity()
                    );
                }
            }
        }
        return null;
    }

    private List<String> parseKeywords(String config) {
        // 简单 JSON 解析:{"keywords":["a","b"]} 或 {"forbidden":["a","b"]}
        if (config == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        int start = config.indexOf('[');
        int end = config.indexOf(']');
        if (start < 0 || end < 0) return result;
        String content = config.substring(start + 1, end);
        for (String s : content.split(",")) {
            String trimmed = s.trim().replaceAll("[\"']", "");
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private Integer parseMinDuration(String config) {
        if (config == null) return null;
        int idx = config.indexOf("minDuration");
        if (idx < 0) return null;
        String sub = config.substring(idx);
        int colonIdx = sub.indexOf(':');
        int endIdx = sub.indexOf('}', colonIdx);
        if (colonIdx < 0 || endIdx < 0) return null;
        try {
            return Integer.parseInt(sub.substring(colonIdx + 1, endIdx).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 实时质检结果
     */
    @lombok.Data
    public static class RealTimeCheckResult {
        private boolean pass = true;
        private boolean block = false;       // true=立即阻断
        private String violationId;
        private String ruleCode;
        private String message;
        private String severity;             // P0/P1/P2

        public static RealTimeCheckResult pass() {
            return new RealTimeCheckResult();
        }

        public static RealTimeCheckResult block(String violationId, String ruleCode, String message, String severity) {
            RealTimeCheckResult r = new RealTimeCheckResult();
            r.pass = false;
            r.block = "P0".equals(severity);
            r.violationId = violationId;
            r.ruleCode = ruleCode;
            r.message = message;
            r.severity = severity;
            return r;
        }
    }
}
