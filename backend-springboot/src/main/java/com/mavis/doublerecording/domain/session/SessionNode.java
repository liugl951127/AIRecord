package com.mavis.doublerecording.domain.session;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_session_node",
       uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "node_seq"}))
public class SessionNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 32)
    private String sessionId;

    @Column(name = "node_seq", nullable = false)
    private Integer nodeSeq;

    @Column(name = "node_type", nullable = false, length = 32)
    private String nodeType;

    @Column(name = "node_title", length = 64)
    private String nodeTitle;

    @Column(name = "script_content", columnDefinition = "TEXT")
    private String scriptContent;

    @Column(name = "customer_response", columnDefinition = "TEXT")
    private String customerResponse;

    @Column(name = "quality_status", length = 16)
    private String qualityStatus;

    @Column(name = "quality_message", length = 512)
    private String qualityMessage;

    @Column(name = "missing_keywords", length = 512)
    private String missingKeywords;

    @Column(name = "retry_count", columnDefinition = "INT DEFAULT 0")
    private Integer retryCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
