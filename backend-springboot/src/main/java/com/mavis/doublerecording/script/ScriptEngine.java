package com.mavis.doublerecording.script;

import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.domain.script.ScriptKeyword;
import com.mavis.doublerecording.domain.script.ScriptKeywordRepository;
import com.mavis.doublerecording.domain.script.ScriptNode;
import com.mavis.doublerecording.domain.script.ScriptNodeRepository;
import com.mavis.doublerecording.domain.script.ScriptTemplate;
import com.mavis.doublerecording.domain.script.ScriptTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 话术引擎 - JDK 17 现代化版本
 *
 * 核心能力:
 * 1. 根据产品ID + 风险等级匹配话术模板
 * 2. 加载节点 + 动态变量替换
 * 3. 提供节点对应的合规关键词列表
 *
 * JDK 17 特性应用:
 * - 局部变量类型推断(var)
 * - Switch 表达式(case X ->)
 * - Pattern Matching for instanceof
 * - Record 替代部分 DTO
 * - Text Blocks 多行字符串
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptEngine {

    private final ScriptTemplateRepository templateRepository;
    private final ScriptNodeRepository nodeRepository;
    private final ScriptKeywordRepository keywordRepository;

    /**
     * 话术节点类型 - 用 switch expression 处理(JDK 14+)
     */
    public enum NodeType {
        OPENING, IDENTITY, RISK_CONFIRM, PRODUCT_INTRO, RETURN_DISCLOSURE,
        RISK_DISCLOSURE, FEE_DISCLOSURE, LIQUIDITY_DISCLOSURE, CUSTOMER_CONFIRM,
        SIGN_PROMPT, CLOSING;

        /**
         * 节点是否要求必读(强制完整朗读)
         */
        public boolean isMandatory() {
            return switch (this) {
                case RISK_DISCLOSURE, RETURN_DISCLOSURE, FEE_DISCLOSURE, LIQUIDITY_DISCLOSURE
                    -> true;
                default -> false;
            };
        }

        /**
         * 节点最低朗读时长(秒)
         */
        public int minDuration() {
            return switch (this) {
                case RISK_DISCLOSURE -> 45;
                case PRODUCT_INTRO, RETURN_DISCLOSURE -> 30;
                case FEE_DISCLOSURE, LIQUIDITY_DISCLOSURE -> 25;
                case CLOSING -> 5;
                default -> 15;
            };
        }
    }

    /**
     * 加载话术模板(自动匹配产品+风险等级)
     */
    @Transactional(readOnly = true)
    public ScriptTemplate loadTemplate(String productId, String riskLevel) {
        var templates = templateRepository.findActiveTemplates(productId, riskLevel);
        if (templates.isEmpty()) {
            templates = templateRepository.findActiveTemplates(productId, "R3");
        }
        if (templates.isEmpty()) {
            throw new BizException(404,
                "未找到匹配的话术模板: product=" + productId + ", risk=" + riskLevel);
        }
        return templates.get(0);
    }

    @Transactional(readOnly = true)
    public ScriptTemplate loadTemplateById(String templateId, String version) {
        return templateRepository.findByTemplateIdAndVersion(templateId, version)
            .orElseThrow(() -> new BizException(404, "话术模板不存在: " + templateId + "/" + version));
    }

    @Transactional(readOnly = true)
    public List<ScriptNode> loadNodes(String templateId, String version) {
        return nodeRepository.findByTemplateIdAndVersionOrderByNodeSeqAsc(templateId, version);
    }

    @Transactional(readOnly = true)
    public ScriptNode loadNode(String templateId, String version, int nodeSeq) {
        return nodeRepository.findByTemplateIdAndVersionAndNodeSeq(templateId, version, nodeSeq)
            .orElseThrow(() -> new BizException(404,
                "话术节点不存在: " + templateId + "/" + version + "/" + nodeSeq));
    }

    @Transactional(readOnly = true)
    public List<ScriptKeyword> loadKeywords(String templateId, String version, int nodeSeq) {
        return keywordRepository.findByTemplateIdAndVersionAndNodeSeq(templateId, version, nodeSeq);
    }

    /**
     * 动态变量替换
     * 支持 ${varName} 占位符
     */
    public String renderScript(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        var result = template;
        if (variables != null) {
            for (var entry : variables.entrySet()) {
                var placeholder = "${" + entry.getKey() + "}";
                if (result.contains(placeholder)) {
                    result = result.replace(placeholder, String.valueOf(entry.getValue()));
                }
            }
        }
        return result;
    }

    /**
     * 渲染并返回完整话术包(模板 + 节点 + 变量绑定)
     */
    public ScriptPackage renderPackage(ScriptTemplate template, Map<String, Object> variables) {
        var nodes = loadNodes(template.getTemplateId(), template.getVersion());
        for (var node : nodes) {
            node.setScriptContent(renderScript(node.getScriptContent(), variables));
        }
        return new ScriptPackage(template, nodes, variables);
    }

    /**
     * 话术包 - JDK 17 record(不可变 DTO)
     * 自动生成:构造器、equals、hashCode、toString、accessor
     */
    public record ScriptPackage(
        ScriptTemplate template,
        List<ScriptNode> nodes,
        Map<String, Object> variables
    ) {}

    /**
     * 节点配置 - record 形式(JDK 17 风格)
     */
    public record NodeConfig(
        String type,
        int minDuration,
        boolean mandatory,
        String description
    ) {
        /**
         * 紧凑构造器 - 参数校验
         */
        public NodeConfig {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("节点类型不能为空");
            }
        }

        public static NodeConfig of(NodeType type) {
            return new NodeConfig(
                type.name(),
                type.minDuration(),
                type.isMandatory(),
                switch (type) {
                    case RISK_DISCLOSURE -> "风险揭示必含本金损失";
                    case PRODUCT_INTRO -> "产品介绍";
                    case RETURN_DISCLOSURE -> "收益说明";
                    default -> type.name();
                }
            );
        }
    }
}
