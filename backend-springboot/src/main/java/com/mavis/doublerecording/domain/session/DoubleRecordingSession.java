package com.mavis.doublerecording.domain.session;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_session")
public class DoubleRecordingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", unique = true, nullable = false, length = 32)
    private String sessionId;

    @Column(name = "customer_id", nullable = false, length = 32)
    private String customerId;

    @Column(name = "customer_name", length = 64)
    private String customerName;

    @Column(name = "product_id", nullable = false, length = 32)
    private String productId;

    @Column(name = "product_name", length = 128)
    private String productName;

    @Column(name = "channel", nullable = false, length = 16)
    private String channel;

    @Column(name = "current_state", nullable = false, length = 32)
    private String currentState;

    @Column(name = "current_node_seq", columnDefinition = "INT DEFAULT 0")
    private Integer currentNodeSeq = 0;

    @Column(name = "risk_level", length = 8)
    private String riskLevel;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "script_template_id", length = 32)
    private String scriptTemplateId;

    @Column(name = "script_version", length = 16)
    private String scriptVersion;

    @Column(name = "video_file_id", length = 64)
    private String videoFileId;

    @Column(name = "video_hash", length = 64)
    private String videoHash;

    @Column(name = "sign_image_hash", length = 64)
    private String signImageHash;

    @Column(name = "chain_tx_hash", length = 128)
    private String chainTxHash;

    @Column(name = "chain_block_height")
    private Long chainBlockHeight;

    @Column(name = "chain_cert_no", length = 64)
    private String chainCertNo;

    @Column(name = "order_id", length = 32)
    private String orderId;

    @Column(name = "order_amount")
    private BigDecimal orderAmount;

    @Column(name = "quality_report_id", length = 32)
    private String qualityReportId;

    @Column(name = "final_status", length = 16)
    private String finalStatus;

    @Column(name = "remark", length = 512)
    private String remark;

    @Version
    @Column(name = "version", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer version = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
