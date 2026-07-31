package com.mavis.doublerecording.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSessionRequest {

    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    private String customerName;

    @NotBlank(message = "产品ID不能为空")
    private String productId;

    private String productName;

    private String channel = "APP";

    private String riskLevel;

    private java.math.BigDecimal orderAmount;
}
