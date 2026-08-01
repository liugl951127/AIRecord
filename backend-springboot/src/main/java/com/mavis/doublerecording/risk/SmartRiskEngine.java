package com.mavis.doublerecording.risk;

import com.mavis.doublerecording.domain.risk.RiskQuestionnaire;
import com.mavis.doublerecording.domain.risk.RiskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 智能风评引擎 - JDK 17 现代化版本
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
 * JDK 17 特性:
 * - Switch Expression (levelToRank)
 * - Record (AssessmentResult, MatchResult, QuestionDef)
 * - Pattern Matching (instanceof, 简化赋值)
 * - var 局部变量
 * - Text Blocks (用于配置文档)
 * - Sealed 类型层级(题目分类)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartRiskEngine {

    private final RiskRepository riskRepository;

    private static final int VALID_DAYS = 30;

    /**
     * 风险等级枚举 - 带等级排序能力
     */
    public enum RiskLevel {
        R1(1, "保守型"), R2(2, "稳健型"), R3(3, "平衡型"),
        R4(4, "成长型"), R5(5, "激进型");

        private final int rank;
        private final String description;

        RiskLevel(int rank, String description) {
            this.rank = rank;
            this.description = description;
        }

        public int rank() { return rank; }
        public String description() { return description; }

        /**
         * 静态工厂 - 安全解析
         */
        public static Optional<RiskLevel> parse(String s) {
            return switch (s == null ? "" : s.toUpperCase()) {
                case "R1" -> Optional.of(R1);
                case "R2" -> Optional.of(R2);
                case "R3" -> Optional.of(R3);
                case "R4" -> Optional.of(R4);
                case "R5" -> Optional.of(R5);
                default -> Optional.empty();
            };
        }
    }

    /**
     * 题目定义 - 不可变 record
     */
    public record QuestionDef(
        String code,           // Q1/Q2/Q3...
        String title,
        String[] options,
        int[] scores,
        double weight
    ) {
        public QuestionDef {
            // 紧凑构造器 - 校验
            if (options.length != scores.length) {
                throw new IllegalArgumentException(
                    "选项和分数数量不匹配: " + code);
            }
            if (weight < 0 || weight > 1) {
                throw new IllegalArgumentException(
                    "权重必须在 0-1 之间: " + code + " weight=" + weight);
            }
        }

        /**
         * 工厂方法 - 题目分类(年龄/收入/经验/态度)
         */
        public Category category() {
            return switch (code) {
                case "Q1" -> Category.PERSONAL;
                case "Q2", "Q3" -> Category.FINANCIAL;
                case "Q4", "Q5" -> Category.EXPERIENCE;
                case "Q6", "Q7" -> Category.ATTITUDE;
                default -> Category.OTHER;
            };
        }
    }

    public enum Category {
        PERSONAL, FINANCIAL, EXPERIENCE, ATTITUDE, OTHER
    }

    /**
     * 评估结果 - record
     */
    public record AssessmentResult(
        String questionnaireId,
        String customerId,
        Integer score,
        String riskLevel,
        Map<String, Integer> answerScores,
        Map<String, String> warnings,
        LocalDateTime assessTime,
        LocalDateTime expireTime,
        boolean valid
    ) {
        /**
         * 便捷方法 - 是否有警告
         */
        public boolean hasWarnings() {
            return warnings != null && !warnings.isEmpty();
        }

        /**
         * 便捷方法 - 风险等级枚举
         */
        public Optional<RiskLevel> riskLevelEnum() {
            return RiskLevel.parse(riskLevel);
        }
    }

    /**
     * 等级匹配结果 - record
     */
    public record MatchResult(
        String customerLevel,
        String productLevel,
        boolean matched,           // 等级匹配(允许)
        boolean requiresWarning,   // 需强提示
        boolean forbidden          // 禁止销售
    ) {
        /**
         * 建议操作描述 - 静态文本(用 text block)
         */
        public String recommendation() {
            return switch ((matched, requiresWarning, forbidden)) {
                case (true, false, false) -> """
                    客户风险等级与产品匹配,允许直接销售。
                    """;
                case (true, true, false) -> """
                    客户风险等级低于产品等级(差 1 级),
                    销售前必须进行强提示,客户二次确认后方可继续。
                    """;
                case (false, true, true) -> """
                    客户风险等级远低于产品等级(差 2 级及以上),
                    监管禁止销售此类产品给该客户。
                    """;
                default -> "未知匹配状态";
            };
        }
    }

    /**
     * 7 道题的标准定义
     */
    private static final Map<String, QuestionDef> QUESTIONS = new LinkedHashMap<>();
    static {
        QUESTIONS.put("Q1", new QuestionDef("Q1", "您的年龄区间?",
            new String[]{"A. 18-30", "B. 31-45", "C. 46-55", "D. 56-65", "E. 65以上"},
            new int[]{85, 75, 60, 45, 25}, 0.10));
        QUESTIONS.put("Q2", new QuestionDef("Q2", "您的家庭年收入?",
            new String[]{"A. 5万以下", "B. 5-15万", "C. 15-30万", "D. 30-100万", "E. 100万以上"},
            new int[]{20, 40, 60, 80, 95}, 0.15));
        QUESTIONS.put("Q3", new QuestionDef("Q3", "您的金融资产规模?",
            new String[]{"A. 5万以下", "B. 5-20万", "C. 20-50万", "D. 50-200万", "E. 200万以上"},
            new int[]{20, 40, 60, 80, 95}, 0.15));
        QUESTIONS.put("Q4", new QuestionDef("Q4", "您的投资经验?",
            new String[]{"A. 无经验", "B. 1-3年", "C. 3-5年", "D. 5-10年", "E. 10年以上"},
            new int[]{20, 40, 60, 80, 95}, 0.20));
        QUESTIONS.put("Q5", new QuestionDef("Q5", "您的投资期限偏好?",
            new String[]{"A. 随时可取", "B. 3个月内", "C. 3-12个月", "D. 1-3年", "E. 3年以上"},
            new int[]{25, 40, 60, 80, 90}, 0.10));
        QUESTIONS.put("Q6", new QuestionDef("Q6", "您的风险承受态度?",
            new String[]{"A. 完全不能亏", "B. 亏10%以内", "C. 亏20%以内", "D. 亏30%以内", "E. 亏30%以上可接受"},
            new int[]{20, 40, 60, 80, 95}, 0.20));
        QUESTIONS.put("Q7", new QuestionDef("Q7", "您的流动性需求?",
            new String[]{"A. 随时可能用", "B. 半年内可能用", "C. 1年内可能用", "D. 1-3年", "E. 3年以上不用"},
            new int[]{25, 40, 60, 80, 90}, 0.10));
    }

    /**
     * 执行风险评估
     */
    @Transactional
    public AssessmentResult assess(String customerId, Map<String, String> answers) {
        log.info("[智能风评] 客户: {}, 答案: {}", customerId, answers);

        // 1. 校验答案完整性
        for (var q : QUESTIONS.keySet()) {
            if (!answers.containsKey(q)) {
                throw new IllegalArgumentException("缺少问题答案: " + q);
            }
            var ans = answers.get(q);
            if (ans == null || !ans.matches("[A-E]")) {
                throw new IllegalArgumentException("答案格式错误: " + q + "=" + ans);
            }
        }

        // 2. 计算加权总分
        var answerScores = new LinkedHashMap<String, Integer>();
        var warnings = new LinkedHashMap<String, String>();
        double totalScore = 0;
        double totalWeight = 0;

        for (var entry : QUESTIONS.entrySet()) {
            var def = entry.getValue();
            var answer = answers.get(entry.getKey());
            var idx = answer.charAt(0) - 'A';
            var score = def.scores()[idx];
            answerScores.put(entry.getKey(), score);
            totalScore += score * def.weight();
            totalWeight += def.weight();

            // 异常项提示 - Pattern matching (JDK 16+)
            switch (entry.getKey()) {
                case "Q6" -> {
                    if (idx == 0) warnings.put("Q6", "客户选择完全不能亏,仅适合 R1 产品");
                }
                case "Q4" -> {
                    if (idx == 0 && "A".equals(answers.get("Q2"))) {
                        warnings.put("Q4", "客户无投资经验且年收入较低,建议优先低风险产品");
                    }
                }
            }
        }

        var finalScore = (int) Math.round(totalScore / totalWeight);
        var riskLevel = scoreToLevel(finalScore);

        // 3. 持久化
        var q = new RiskQuestionnaire();
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

        return new AssessmentResult(
            q.getQuestionnaireId(),
            customerId,
            finalScore,
            riskLevel,
            answerScores,
            warnings,
            q.getAssessTime(),
            q.getExpireTime(),
            true
        );
    }

    /**
     * 获取客户最近的有效评估
     */
    public Optional<AssessmentResult> getLatestValidAssessment(String customerId) {
        var opt = riskRepository.findTopByCustomerIdOrderByAssessTimeDesc(customerId);
        if (opt.isEmpty()) return Optional.empty();

        var q = opt.get();
        if (q.getExpireTime() != null && q.getExpireTime().isBefore(LocalDateTime.now())) {
            log.info("[智能风评] 评估已过期: customer={}, expiredAt={}", customerId, q.getExpireTime());
            return Optional.empty();
        }

        return Optional.of(new AssessmentResult(
            q.getQuestionnaireId(),
            customerId,
            q.getScore(),
            q.getRiskLevel(),
            Map.of(),
            Map.of(),
            q.getAssessTime(),
            q.getExpireTime(),
            true
        ));
    }

    /**
     * 检查产品风险等级与客户等级的匹配性
     */
    public MatchResult checkMatch(String customerLevel, String productLevel) {
        var customerRank = levelToRank(customerLevel);
        var productRank = levelToRank(productLevel);
        return new MatchResult(
            customerLevel,
            productLevel,
            customerRank >= productRank,
            customerRank < productRank,
            customerRank < productRank - 1
        );
    }

    /**
     * 获取所有题目
     */
    public Map<String, QuestionDef> getQuestions() {
        return Collections.unmodifiableMap(QUESTIONS);
    }

    // ========== 私有方法 ==========

    /**
     * 分数到等级 - 用 var 局部变量
     */
    private String scoreToLevel(int score) {
        var level = score <= 20 ? "R1"
                  : score <= 40 ? "R2"
                  : score <= 60 ? "R3"
                  : score <= 80 ? "R4"
                  : "R5";
        return level;
    }

    /**
     * Switch Expression (JDK 14+)
     */
    private int levelToRank(String level) {
        return switch (level) {
            case "R1" -> 1;
            case "R2" -> 2;
            case "R3" -> 3;
            case "R4" -> 4;
            case "R5" -> 5;
            default -> 3;
        };
    }

    private String toJson(Map<String, String> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return map.toString();
        }
    }
}
