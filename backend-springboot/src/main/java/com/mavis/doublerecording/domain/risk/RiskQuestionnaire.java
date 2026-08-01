package com.mavis.doublerecording.domain.risk;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_risk_questionnaire")
public class RiskQuestionnaire {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "questionnaire_id", unique = true, nullable = false, length = 32)
    private String questionnaireId;

    @Column(name = "customer_id", nullable = false, length = 32)
    private String customerId;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "risk_level", nullable = false, length = 8)
    private String riskLevel;

    @Column(name = "answers", columnDefinition = "TEXT")
    private String answers;

    @Column(name = "assess_time")
    private LocalDateTime assessTime;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "version")
    private Integer version;

    @CreationTimestamp
    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private LocalDateTime evaluatedAt;
}
