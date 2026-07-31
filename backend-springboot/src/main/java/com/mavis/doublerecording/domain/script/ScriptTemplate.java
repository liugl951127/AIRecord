package com.mavis.doublerecording.domain.script;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_script_template",
       uniqueConstraints = @UniqueConstraint(columnNames = {"template_id", "version"}))
public class ScriptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false, length = 32)
    private String templateId;

    @Column(name = "template_name", nullable = false, length = 128)
    private String templateName;

    @Column(name = "product_id", nullable = false, length = 32)
    private String productId;

    @Column(name = "product_type", nullable = false, length = 32)
    private String productType;

    @Column(name = "risk_level", nullable = false, length = 8)
    private String riskLevel;

    @Column(name = "version", nullable = false, length = 16)
    private String version;

    @Column(name = "status", nullable = false, length = 16)
    private String status;  // DRAFT/REVIEW/PUBLISHED/DEPRECATED

    @Column(name = "effective_time", nullable = false)
    private LocalDateTime effectiveTime;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "created_by", length = 32)
    private String createdBy = "admin";

    @Column(name = "reviewed_by", length = 32)
    private String reviewedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
