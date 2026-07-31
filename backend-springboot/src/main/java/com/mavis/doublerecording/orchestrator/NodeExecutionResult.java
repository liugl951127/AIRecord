package com.mavis.doublerecording.orchestrator;

import com.mavis.doublerecording.domain.script.ScriptNode;
import com.mavis.doublerecording.quality.QualityCheckResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionResult {

    /** 话术节点 */
    private ScriptNode node;

    /** 渲染后的话术内容(已替换变量) */
    private String renderedContent;

    /** 是否必读 */
    private boolean requiredReading;

    /** 已完成的质检 */
    private QualityCheckResult qualityResult;

    /** 是否需要客户确认(只有"是""清楚"等才能进入下一步) */
    private boolean requiresCustomerAgree;

    /** 下一步动作描述 */
    private String nextAction;
}
