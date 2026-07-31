package com.mavis.doublerecording.domain.quality;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_quality_report")
public class QualityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", unique = true, nullable = false, length = 32)
    private String reportId;

    @Column(name = "session_id", nullable = false, length = 32)
    private String sessionId;

    @Column(name = "rule_version", nullable = false, length = 16)
    private String ruleVersion;

    @Column(name = "model_version", nullable = false, length = 16)
    private String modelVersion = "v1.0";

    @Column(name = "total_nodes", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer totalNodes = 0;

    @Column(name = "passed_nodes", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer passedNodes = 0;

    @Column(name = "failed_nodes", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer failedNodes = 0;

    @Column(name = "blocked_count", columnDefinition = "INT DEFAULT 0")
    private Integer blockedCount = 0;

    @Column(name = "alert_count", columnDefinition = "INT DEFAULT 0")
    private Integer alertCount = 0;

    @Column(name = "p0_missing", columnDefinition = "TEXT")
    private String p0Missing;

    @Column(name = "p1_missing", columnDefinition = "TEXT")
    private String p1Missing;

    @Column(name = "final_status", nullable = false, length = 16)
    private String finalStatus;  // PASS/FAIL

    @Column(name = "detail_json", columnDefinition = "TEXT")
    private String detailJson;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;
}
