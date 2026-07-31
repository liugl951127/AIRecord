package com.mavis.doublerecording.quality;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 质检结果
 */
@Data
@NoArgsConstructor
public class QualityCheckResult {

    /** PASS/FAIL/BLOCKED */
    private String status;

    /** 严重度 P0/P1/P2 */
    private String severity;

    /** 通过的规则数 */
    private int passedRules;

    /** 失败的规则数 */
    private int failedRules;

    /** 阻断规则数(P0) */
    private int blockedCount;

    /** 告警规则数(P1) */
    private int alertCount;

    /** 缺失的 P0 关键词 */
    private List<String> p0Missing = new ArrayList<>();

    /** 缺失的 P1 关键词 */
    private List<String> p1Missing = new ArrayList<>();

    /** 命中的禁止性表述 */
    private List<String> forbiddenHit = new ArrayList<>();

    /** 详情 */
    private List<RuleHit> details = new ArrayList<>();

    /** 消息 */
    private String message;

    public boolean isBlocked() {
        return "BLOCKED".equals(status);
    }

    public boolean isPassed() {
        return "PASS".equals(status);
    }

    @Data
    @NoArgsConstructor
    public static class RuleHit {
        private String ruleCode;
        private String ruleName;
        private String severity;
        private String result;  // PASS/FAIL
        private String message;
    }
}
