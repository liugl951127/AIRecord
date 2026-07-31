package com.mavis.doublerecording.risk;

import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.domain.risk.RiskQuestionnaire;
import com.mavis.doublerecording.domain.risk.RiskRepository;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 风险评估引擎
 *
 * 5级风险分类(R1-R5):
 * R1 谨慎型:0-40 分
 * R2 稳健型:41-60 分
 * R3 平衡型:61-75 分
 * R4 进取型:76-90 分
 * R5 激进型:91-100 分
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEngine {

    private final RiskRepository riskRepository;
    private final EventStore eventStore;

    /**
     * 根据分数判定风险等级
     */
    public String getRiskLevel(int score) {
        if (score < 0 || score > 100) {
            throw new BizException("风险分数必须在 0-100 之间,实际: " + score);
        }
        if (score <= 40) return "R1";
        if (score <= 60) return "R2";
        if (score <= 75) return "R3";
        if (score <= 90) return "R4";
        return "R5";
    }

    /**
     * 获取客户风险评估
     */
    @Transactional(readOnly = true)
    public RiskQuestionnaire getAssessment(String customerId) {
        return riskRepository.findByCustomerId(customerId)
            .orElseThrow(() -> new BizException(404, "客户风险评估不存在: " + customerId));
    }

    /**
     * 提交并保存风险评估
     */
    @Transactional
    public RiskQuestionnaire submitAssessment(String customerId, int score, String answers) {
        RiskQuestionnaire questionnaire = riskRepository.findByCustomerId(customerId)
            .orElseGet(RiskQuestionnaire::new);
        questionnaire.setCustomerId(customerId);
        questionnaire.setScore(score);
        questionnaire.setRiskLevel(getRiskLevel(score));
        questionnaire.setAnswers(answers);
        RiskQuestionnaire saved = riskRepository.save(questionnaire);

        Map<String, Object> payload = new HashMap<>();
        payload.put("customerId", customerId);
        payload.put("score", score);
        payload.put("riskLevel", saved.getRiskLevel());
        eventStore.append(customerId, "RISK", customerId, "RiskEvaluated", payload);

        log.info("[风评引擎] 客户 {} 风险评估完成: {} ({}分)", customerId, saved.getRiskLevel(), score);
        return saved;
    }

    /**
     * 校验产品风险等级与客户风险等级是否匹配
     * 规则:客户风险等级 >= 产品风险等级
     */
    public boolean matchProduct(String customerRiskLevel, String productRiskLevel) {
        if (customerRiskLevel == null || productRiskLevel == null) {
            return false;
        }
        int customer = Integer.parseInt(customerRiskLevel.substring(1));
        int product = Integer.parseInt(productRiskLevel.substring(1));
        return customer >= product;
    }

    /**
     * 获取风险等级描述
     */
    public String getLevelDescription(String riskLevel) {
        return switch (riskLevel) {
            case "R1" -> "谨慎型(低风险)";
            case "R2" -> "稳健型(中低风险)";
            case "R3" -> "平衡型(中等风险)";
            case "R4" -> "进取型(中高风险)";
            case "R5" -> "激进型(高风险)";
            default -> "未知";
        };
    }
}
