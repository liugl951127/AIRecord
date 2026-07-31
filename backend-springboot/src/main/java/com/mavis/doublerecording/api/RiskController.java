package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.domain.risk.RiskQuestionnaire;
import com.mavis.doublerecording.risk.RiskEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController {

    private final RiskEngine riskEngine;

    /**
     * 获取客户风险评估
     */
    @GetMapping("/{customerId}")
    public Result<RiskQuestionnaire> getAssessment(@PathVariable String customerId) {
        return Result.ok(riskEngine.getAssessment(customerId));
    }

    /**
     * 提交风险评估
     */
    @PostMapping("/submit")
    public Result<RiskQuestionnaire> submit(@RequestBody Map<String, Object> request) {
        String customerId = (String) request.get("customerId");
        Integer score = (Integer) request.get("score");
        String answers = (String) request.getOrDefault("answers", "{}");
        return Result.ok(riskEngine.submitAssessment(customerId, score, answers));
    }

    /**
     * 检查产品匹配
     */
    @GetMapping("/match")
    public Result<Map<String, Object>> match(@RequestParam String customerLevel,
                                              @RequestParam String productLevel) {
        return Result.ok(Map.of(
            "match", riskEngine.matchProduct(customerLevel, productLevel),
            "customerLevel", customerLevel,
            "productLevel", productLevel
        ));
    }
}
