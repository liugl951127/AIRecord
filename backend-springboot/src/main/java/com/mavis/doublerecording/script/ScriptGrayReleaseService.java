package com.mavis.doublerecording.script;

import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.domain.script.ScriptTemplate;
import com.mavis.doublerecording.domain.script.ScriptTemplateRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 话术模板灰度发布服务
 *
 * 功能:
 * 1. 多版本管理(V1.0/V1.1/V2.0)
 * 2. 灰度发布规则:
 *    - 按用户分桶(10% → 50% → 100%)
 *    - 按渠道(APP 先 / BRANCH 后)
 *    - 按客户标签(VIP 先 / 普通后)
 * 3. 版本回滚(秒级)
 * 4. 灰度效果监控
 *
 * 解决问题:
 * - 话术更新不需要一刀切
 * - 新版本小流量验证,降低风险
 * - 出问题秒级回滚
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptGrayReleaseService {

    private final ScriptTemplateRepository templateRepository;

    /**
     * 灰度规则配置
     * 规则格式: 模板ID → 版本 → 灰度配置
     */
    private final Map<String, GrayRule> grayRules = new ConcurrentHashMap<>();

    /**
     * 灰度命中统计
     * 格式: 模板ID:版本:分桶 → 命中次数
     */
    private final Map<String, AtomicLong> grayHits = new ConcurrentHashMap<>();

    /**
     * 注册灰度规则
     */
    public void registerGrayRule(String templateId, String version, GrayRule rule) {
        String key = templateId + ":" + version;
        grayRules.put(key, rule);
        log.info("[灰度发布] 注册规则: template={}, version={}, bucket={}, channels={}, tags={}",
            templateId, version, rule.getBucket(), rule.getChannels(), rule.getCustomerTags());
    }

    /**
     * 选择话术版本(根据灰度规则)
     */
    public ScriptTemplate selectVersion(String templateId, String customerId, String channel, Set<String> customerTags) {
        List<ScriptTemplate> publishedVersions = templateRepository
            .findByTemplateIdAndStatusOrderByVersionDesc(templateId, "PUBLISHED");

        if (publishedVersions.isEmpty()) {
            return null;
        }

        // 多个版本时,按灰度规则选择
        if (publishedVersions.size() > 1) {
            for (ScriptTemplate ver : publishedVersions) {
                String key = templateId + ":" + ver.getVersion();
                GrayRule rule = grayRules.get(key);
                if (rule == null) {
                    // 没灰度规则,默认最高版本
                    continue;
                }
                if (matchRule(rule, customerId, channel, customerTags)) {
                    incrementHit(key);
                    log.debug("[灰度发布] 命中: template={}, version={}, customer={}",
                        templateId, ver.getVersion(), customerId);
                    return ver;
                }
            }
        }

        // 默认返回最高版本
        return publishedVersions.get(0);
    }

    /**
     * 灰度升级(从 10% → 50%)
     */
    @Transactional
    public void rampUp(String templateId, String version, int newBucketPercent) {
        String key = templateId + ":" + version;
        GrayRule rule = grayRules.get(key);
        if (rule == null) {
            rule = new GrayRule();
            grayRules.put(key, rule);
        }
        int oldBucket = rule.getBucket();
        rule.setBucket(newBucketPercent);
        log.info("[灰度发布] 升级: template={}, version={}, {}% → {}%",
            templateId, version, oldBucket, newBucketPercent);
    }

    /**
     * 紧急回滚到上一版本
     */
    @Transactional
    public ScriptTemplate rollback(String templateId, String reason) {
        log.warn("[灰度发布] 紧急回滚: template={}, reason={}", templateId, reason);

        List<ScriptTemplate> allVersions = templateRepository
            .findByTemplateIdOrderByVersionDesc(templateId);
        if (allVersions.size() < 2) {
            throw new RuntimeException("没有可回滚的历史版本");
        }

        // 当前最新版本降级为 DEPRECATED
        ScriptTemplate current = allVersions.get(0);
        current.setStatus("DEPRECATED");
        templateRepository.save(current);

        // 上一版本提升为 PUBLISHED
        ScriptTemplate previous = allVersions.get(1);
        previous.setStatus("PUBLISHED");
        templateRepository.save(previous);

        log.warn("[灰度发布] 回滚完成: {} → {}", current.getVersion(), previous.getVersion());
        return previous;
    }

    /**
     * 获取灰度命中统计
     */
    public Map<String, Long> getGrayStats() {
        Map<String, Long> result = new HashMap<>();
        grayHits.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }

    /**
     * 规则匹配
     */
    private boolean matchRule(GrayRule rule, String customerId, String channel, Set<String> customerTags) {
        // 1. 渠道匹配
        if (rule.getChannels() != null && !rule.getChannels().isEmpty()) {
            if (!rule.getChannels().contains(channel)) {
                return false;
            }
        }
        // 2. 客户标签匹配
        if (rule.getCustomerTags() != null && !rule.getCustomerTags().isEmpty()) {
            if (customerTags == null) return false;
            boolean anyMatch = rule.getCustomerTags().stream().anyMatch(customerTags::contains);
            if (!anyMatch) return false;
        }
        // 3. 分桶匹配:基于 customerId 哈希
        int hash = Math.abs(customerId.hashCode() % 100);
        return hash < rule.getBucket();
    }

    private void incrementHit(String key) {
        grayHits.computeIfAbsent(key, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
    }

    @Data
    public static class GrayRule {
        /** 灰度分桶(0-100),如 10 表示 10% 流量 */
        private int bucket = 100;
        /** 灰度渠道,空表示全渠道 */
        private Set<String> channels = new HashSet<>();
        /** 灰度客户标签,空表示全客户 */
        private Set<String> customerTags = new HashSet<>();
        /** 创建时间 */
        private LocalDateTime createdAt = LocalDateTime.now();
    }
}
