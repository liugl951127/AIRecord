package com.mavis.doublerecording.api.dto;

import lombok.Data;

@Data
public class SubmitNodeRequest {

    /** 节点序号 */
    private Integer nodeSeq;

    /** 销售员朗读内容 */
    private String agentContent;

    /** 客户回应 */
    private String customerResponse;

    /** 朗读时长(秒) */
    private Integer durationSec = 30;
}
