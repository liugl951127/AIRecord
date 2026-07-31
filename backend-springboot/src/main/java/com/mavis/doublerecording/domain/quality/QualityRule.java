package com.mavis.doublerecording.domain.quality;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_quality_rule")
public class QualityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", unique = true, nullable = false, length = 32)
    private String ruleCode;

    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    @Column(name = "rule_type", nullable = false, length = 32)
    private String ruleType;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "severity", nullable = false, length = 8)
    private String severity;  // P0/P1/P2

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "rule_config", columnDefinition = "TEXT")
    private String ruleConfig;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
