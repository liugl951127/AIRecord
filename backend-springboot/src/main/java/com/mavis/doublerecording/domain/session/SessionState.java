package com.mavis.doublerecording.domain.session;

/**
 * 双录会话状态枚举
 */
public enum SessionState {

    CREATED("已创建"),
    IDENTITY_VERIFYING("身份核验中"),
    IDENTITY_FAILED("身份核验失败"),
    RISK_EVALUATING("风险评估中"),
    RISK_MISMATCH("风险不匹配"),
    RISK_RE_CONFIRM("风险强化确认"),
    RECORDING("双录录制中"),
    QUALITY_BLOCKED("质检阻断"),
    KEY_INFO_CONFIRM("关键信息确认"),
    SIGN_PENDING("待签字"),
    SIGNING("签字中"),
    SIGN_FAILED("签字失败"),
    PAYMENT_PENDING("待支付"),
    PAYING("支付中"),
    PAYMENT_FAILED("支付失败"),
    VIDEO_MERGING("视频合成中"),
    CHAINING("存证中"),
    COMPLETED("已完成"),
    PAUSED("暂停中"),
    FAILED("已失败"),
    CANCELLED("已取消");

    private final String description;

    SessionState(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFinal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
