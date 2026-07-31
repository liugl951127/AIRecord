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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 话术引擎
 *
 * 核心能力:
 * 1. 根据产品ID + 风险等级匹配话术模板
 * 2. 加载节点 + 动态变量替换
 * 3. 提供节点对应的合规关键词列表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptEngine {

    private final ScriptTemplateRepository templateRepository;
    private final ScriptNodeRepository nodeRepository;
    private final ScriptKeywordRepository keywordRepository;

    /**
     * 加载话术模板(自动匹配产品+风险等级)
     */
    @Transactional(readOnly = true)
    public ScriptTemplate loadTemplate(String productId, String riskLevel) {
        List<ScriptTemplate> templates = templateRepository.findActiveTemplates(productId, riskLevel);
        if (templates.isEmpty()) {
            // 降级:尝试任意 R3 模板
            templates = templateRepository.findActiveTemplates(productId, "R3");
        }
        if (templates.isEmpty()) {
            throw new BizException(404, "未找到匹配的话术模板: product=" + productId + ", risk=" + riskLevel);
        }
        return templates.get(0);
    }

    /**
     * 加载话术模板(指定版本)
     */
    @Transactional(readOnly = true)
    public ScriptTemplate loadTemplateById(String templateId, String version) {
        return templateRepository.findByTemplateIdAndVersion(templateId, version)
            .orElseThrow(() -> new BizException(404, "话术模板不存在: " + templateId + "/" + version));
    }

    /**
     * 加载话术所有节点
     */
    @Transactional(readOnly = true)
    public List<ScriptNode> loadNodes(String templateId, String version) {
        return nodeRepository.findByTemplateIdAndVersionOrderByNodeSeqAsc(templateId, version);
    }

    /**
     * 加载指定节点
     */
    @Transactional(readOnly = true)
    public ScriptNode loadNode(String templateId, String version, int nodeSeq) {
        return nodeRepository.findByTemplateIdAndVersionAndNodeSeq(templateId, version, nodeSeq)
            .orElseThrow(() -> new BizException(404, "话术节点不存在: " + templateId + "/" + version + "/" + nodeSeq));
    }

    /**
     * 加载节点的合规关键词
     */
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
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
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
        List<ScriptNode> nodes = loadNodes(template.getTemplateId(), template.getVersion());

        // 渲染所有节点
        for (ScriptNode node : nodes) {
            node.setScriptContent(renderScript(node.getScriptContent(), variables));
        }

        ScriptPackage pkg = new ScriptPackage();
        pkg.template = template;
        pkg.nodes = nodes;
        pkg.variables = variables;
        return pkg;
    }

    /**
     * 话术包(模板+节点+变量)
     */
    @lombok.Data
    public static class ScriptPackage {
        private ScriptTemplate template;
        private List<ScriptNode> nodes;
        private Map<String, Object> variables;
    }
}
