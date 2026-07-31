package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.domain.script.ScriptKeywordRepository;
import com.mavis.doublerecording.domain.script.ScriptNodeRepository;
import com.mavis.doublerecording.domain.script.ScriptTemplate;
import com.mavis.doublerecording.domain.script.ScriptTemplateRepository;
import com.mavis.doublerecording.script.ScriptEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 话术模板管理 API
 */
@RestController
@RequestMapping("/api/script")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptTemplateRepository templateRepository;
    private final ScriptNodeRepository nodeRepository;
    private final ScriptKeywordRepository keywordRepository;
    private final ScriptEngine scriptEngine;

    /**
     * 列出所有模板
     */
    @GetMapping("/templates")
    public Result<List<ScriptTemplate>> listTemplates(
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String riskLevel) {
        if (productId != null && riskLevel != null) {
            return Result.ok(templateRepository.findActiveTemplates(productId, riskLevel));
        }
        return Result.ok(templateRepository.findAll());
    }

    /**
     * 模板详情
     */
    @GetMapping("/template/{templateId}")
    public Result<Map<String, Object>> getTemplate(@PathVariable String templateId,
                                                    @RequestParam String version) {
        ScriptTemplate template = templateRepository.findByTemplateIdAndVersion(templateId, version)
            .orElseThrow(() -> new RuntimeException("模板不存在"));

        Map<String, Object> result = new HashMap<>();
        result.put("template", template);
        result.put("nodes", nodeRepository.findByTemplateIdAndVersionOrderByNodeSeqAsc(templateId, version));

        // 加载每个节点的关键词
        Map<Integer, Object> keywords = new HashMap<>();
        for (Integer seq : List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)) {
            try {
                keywords.put(seq, keywordRepository.findByTemplateIdAndVersionAndNodeSeq(templateId, version, seq));
            } catch (Exception ignore) {}
        }
        result.put("keywords", keywords);

        return Result.ok(result);
    }

    /**
     * 加载话术(自动匹配)
     */
    @GetMapping("/load")
    public Result<Map<String, Object>> loadScript(
            @RequestParam String productId,
            @RequestParam String riskLevel) {
        ScriptTemplate template = scriptEngine.loadTemplate(productId, riskLevel);
        Map<String, Object> result = new HashMap<>();
        result.put("template", template);
        result.put("nodes", scriptEngine.loadNodes(template.getTemplateId(), template.getVersion()));
        return Result.ok(result);
    }
}
