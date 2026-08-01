package com.mavis.doublerecording.domain.script;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptTemplateRepository extends JpaRepository<ScriptTemplate, Long> {

    List<ScriptTemplate> findByProductIdAndRiskLevelAndStatus(String productId, String riskLevel, String status);

    Optional<ScriptTemplate> findByTemplateIdAndVersion(String templateId, String version);

    @Query("SELECT t FROM ScriptTemplate t WHERE t.productId = :productId AND t.riskLevel = :riskLevel AND t.status = 'PUBLISHED' ORDER BY t.effectiveTime DESC")
    List<ScriptTemplate> findActiveTemplates(@Param("productId") String productId, @Param("riskLevel") String riskLevel);

    /** 按模板ID查询所有版本,按版本降序 */
    @Query("SELECT t FROM ScriptTemplate t WHERE t.templateId = :templateId ORDER BY t.version DESC")
    List<ScriptTemplate> findByTemplateIdOrderByVersionDesc(@Param("templateId") String templateId);

    /** 按模板ID+状态查询,按版本降序 */
    List<ScriptTemplate> findByTemplateIdAndStatusOrderByVersionDesc(String templateId, String status);
}
