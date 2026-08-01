package com.mavis.doublerecording.risk;

import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.domain.risk.RiskQuestionnaire;
import com.mavis.doublerecording.domain.risk.RiskRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 智能风评引擎
 *
 * 实现 7 题智能风险评估,完整覆盖 C1-C7 维度:
 * Q1: 年龄区间
 * Q2: 家庭年收入
 * Q3: 资产规模
 * Q4: 投资经验
 * Q5: 投资期限偏好
 * Q6: 风险承受态度
 * Q7: 流动性需求
 *
 * 每题 A-E 选项对应不同分数,加权求和后映射到 R1-R5 等级
 *
 * 核心特性:
 * 1. 防绕过:同一客户 30 天内评估结果复用
 * 2. 超期重测:超过 30 天强制重测
 * 3. 强提示:客户等级 < 产品等级时,二次确认
 * 4. 不匹配拦截:监管禁止销售(预留)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartRiskEngine {

    private final RiskRepository riskRepository;

    /**
     * 风险评估有效期(天)
     */
    private static final int VALID_DAYS = 30;

    /**
     * 7 道题的标准定义
     * 每题 5 个选项,各对应不同分数
     */
    private static final Map<String, QuestionDef> QUESTIONS = new LinkedHashMap<>();
    static {
        QUESTIONS.put("Q1", new QuestionDef(
            "您的年龄区间?",
            new String[]{"A. 18-30", "B. 31-45", "C. 46-55", "D. 56-65", "E. 65以上"},
            new int[]{85, 75, 60, 45, 25},
            0.10  // 权重
        ));
        QUESTIONS.put("Q2", new QuestionDef(
            "您的家庭年收入?",
            new String[]{"A. 5万以下", "B. 5-15万", "C. 15-30万", "D. 30-100万", "E. 100万以上"},
            new int[]{20, 40, 60, 80, 95},
            0.15
        ));
        QUESTIONS.put("Q3", new QuestionDef(
            "您的金融资产规模?",
            new String[]{"A. 5万以下", "B. 5-20万", "C. 20-50万", "D. 50-200万", "E. 200万以上"},
            new int[]{20, 40, 60, 80, 95},
            0.15
        ));
        QUESTIONS.put("Q4", new QuestionDef(
            "您的投资经验?",
            new String[]{"A. 无经验", "B. 1-3年", "C. 3-5年", "D. 5-10年", "E. 10年以上"},
            new int[]{20, 40, 60, 80, 95},
            0.20
        ));
        QUESTIONS.put("Q5", new QuestionDef(
            "您的投资期限偏好?",
            new String[]{"A. 随时可取", "B. 3个月内", "C. 3-12个月", "D. 1-3年", "E. 3年以上"},
            new int[]{25, 40, 60, 80, 90},
            0.10
        ));
        QUESTIONS.put("Q6", new QuestionDef(
            "您的风险承受态度?",
            new String[]{"A. 完全不能亏", "B. 亏10%以内", "C. 亏20%以内", "D. 亏30%以内", "E. 亏30%以上可接受"},
            new int[]{20, 40, 60, 80, 95},
            0.20
        ));
        QUESTIONS.put("Q7", new QuestionDef(
            "您的流动性需求?",
            new String[]{"A. 随时可能用", "B. 半年内可能用", "C. 1年内可能用", "D. 1-3年", "E. 3年以上不用"},
            new int[]{25, 40, 60, 80, 90},
            0.10
        ));
    }

    /**
     * 执行风险评估
     */
    @Transactional
    public AssessmentResult assess(String customerId, Map<String, String> answers) {
        log.info("[智能风评] 客户: {}, 答案: {}", customerId, answers);

        // 1. 校验答案完整性
        for (String q : QUESTIONS.keySet()) {
            if (!answers.containsKey(q)) {
                throw new RuntimeException("缺少问题答案: " + q);
            }
            String ans = answers.get(q);
            if (!ans.matches("[A-E]")) {
                throw new RuntimeException("答案格式错误: " + q + "=" + ans);
            }
        }

        // 2. 计算加权总分
        double totalScore = 0;
        double totalWeight = 0;
        Map<String, Integer> answerScores = new LinkedHashMap<>();
        Map<String, String> warnings = new LinkedHashMap<>();

        for (Map.Entry<String, QuestionDef> entry : QUESTIONS.entrySet()) {
            String qId = entry.getKey();
            QuestionDef def = entry.getValue();
            String answer = answers.get(qId);
            int idx = answer.charAt(0) - 'A';
            int score = def.getScores()[idx];
            answerScores.put(qId, score);
            totalScore += score * def.getWeight();
            totalWeight += def.getWeight();

            // 异常项提示
            if ("Q6".equals(qId) && idx == 0) {
                warnings.put("Q6", "客户选择完全不能亏,仅适合 R1 产品");
            }
            if ("Q4".equals(qId) && idx == 0 && "A".equals(answers.get("Q2"))) {
                warnings.put("Q4", "客户无投资经验且年收入较低,建议优先低风险产品");
            }
        }

        int finalScore = (int) Math.round(totalScore / totalWeight);
        String riskLevel = scoreToLevel(finalScore);

        // 3. 持久化
        RiskQuestionnaire q = new RiskQuestionnaire();
        q.setCustomerId(customerId);
        q.setScore(finalScore);
        q.setRiskLevel(riskLevel);
        q.setAnswers(toJson(answers));
        q.setAssessTime(LocalDateTime.now());
        q.setExpireTime(LocalDateTime.now().plusDays(VALID_DAYS));
        q.setVersion(1);
        riskRepository.save(q);

        log.info("[智能风评] 评估完成: customer={}, score={}, level={}, warnings={}",
            customerId, finalScore, riskLevel, warnings.size());

        // 4. 构造结果
        AssessmentResult result = new AssessmentResult();
        result.setQuestionnaireId(q.getQuestionnaireId());
        result.setCustomerId(customerId);
        result.setScore(finalScore);
        result.setRiskLevel(riskLevel);
        result.setAnswerScores(answerScores);
        result.setWarnings(warnings);
        result.setAssessTime(q.getAssessTime());
        result.setExpireTime(q.getExpireTime());
        result.setValid(true);
        return result;
    }

    /**
     * 获取客户最近的有效评估
     */
    public Optional<AssessmentResult> getLatestValidAssessment(String customerId) {
        Optional<RiskQuestionnaire> opt = riskRepository
            .findTopByCustomerIdOrderByAssessTimeDesc(customerId);
        if (opt.isEmpty()) return Optional.empty();

        RiskQuestionnaire q = opt.get();
        if (q.getExpireTime() != null && q.getExpireTime().isBefore(LocalDateTime.now())) {
            log.info("[智能风评] 评估已过期: customer={}, expiredAt={}", customerId, q.getExpireTime());
            return Optional.empty();
        }

        AssessmentResult r = new AssessmentResult();
        r.setQuestionnaireId(q.getQuestionnaireId());
        r.setCustomerId(customerId);
        r.setScore(q.getScore());
        r.setRiskLevel(q.getRiskLevel());
        r.setAssessTime(q.getAssessTime());
        r.setExpireTime(q.getExpireTime());
        r.setValid(true);
        return Optional.of(r);
    }

    /**
     * 检查产品风险等级与客户等级的匹配性
     */
    public MatchResult checkMatch(String customerLevel, String productLevel) {
        int customerRank = levelToRank(customerLevel);
        int productRank = levelToRank(productLevel);
        MatchResult result = new MatchResult();
        result.setCustomerLevel(customerLevel);
        result.setProductLevel(productLevel);
        result.setMatched(customerRank >= productRank);
        result.setRequiresWarning(customerRank < productRank);
        result.setForbidden(customerRank < productRank - 1);
        return result;
    }

    /**
     * 获取所有题目(供前端展示)
     */
    public Map<String, QuestionDef> getQuestions() {
        return Collections.unmodifiableMap(QUESTIONS);
    }

    // ========== 私有方法 ==========

    private String scoreToLevel(int score) {
        if (score <= 20) return "R1";
        if (score <= 40) return "R2";
        if (score <= 60) return "R3";
        if (score <= 80) return "R4";
        return "R5";
    }

    private int levelToRank(String level) {
        switch (level) {
            case "R1": return 1;
            case "R2": return 2;
            case "R3": return 3;
            case "R4": return 4;
            case "R5": return 5;
            default: return 3;
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }

    // ========== 内部类 ==========

    @Data
    public static class QuestionDef {
        private final String title;
        private final String[] options;
        private final int[] scores;
        private final double weight;
        public QuestionDef(String title, String[] options, int[] scores, double weight) {
            this.title = title;
            this.options = options;
            this.scores = scores;
            this.weight = weight;
        }
    }

    @Data
    public static class AssessmentResult {
        private String questionnaireId;
        private String customerId;
        private Integer score;
        private String riskLevel;
        private Map<String, Integer> answerScores;
        private Map<String, String> warnings;
        private LocalDateTime assessTime;
        private LocalDateTime expireTime;
        private boolean valid;
    }

    @Data
    public static class MatchResult {
        private String customerLevel;
        private String productLevel;
        private boolean matched;           // 等级匹配(允许)
        private boolean requiresWarning;   // 需强提示
        private boolean forbidden;         // 禁止销售
    }
}
