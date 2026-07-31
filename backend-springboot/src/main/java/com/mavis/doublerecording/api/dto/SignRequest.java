package com.mavis.doublerecording.api.dto;

import lombok.Data;

@Data
public class SignRequest {
    /** 签字图片 base64(可选,空则模拟) */
    private String signImage;
}
