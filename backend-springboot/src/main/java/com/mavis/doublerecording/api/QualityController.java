package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.domain.quality.QualityReport;
import com.mavis.doublerecording.domain.quality.QualityReportRepository;
import com.mavis.doublerecording.domain.quality.QualityRule;
import com.mavis.doublerecording.domain.quality.QualityRuleRepository;
import com.mavis.doublerecording.orchestrator.SessionOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final QualityRuleRepository ruleRepository;
    private final QualityReportRepository reportRepository;
    private final SessionOrchestrator orchestrator;

    /**
     * 列出所有启用的规则
     */
    @GetMapping("/rules")
    public Result<List<QualityRule>> listRules() {
        return Result.ok(ruleRepository.findByEnabledTrue());
    }

    /**
     * 获取会话的质检报告
     */
    @GetMapping("/report/{sessionId}")
    public Result<QualityReport> getReport(@PathVariable String sessionId) {
        return Result.ok(reportRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new RuntimeException("质检报告不存在")));
    }

    /**
     * 手动触发生成质检报告
     */
    @PostMapping("/report/{sessionId}/generate")
    public Result<QualityReport> generateReport(@PathVariable String sessionId) {
        return Result.ok(orchestrator.generateFinalQualityReport(sessionId));
    }
}
