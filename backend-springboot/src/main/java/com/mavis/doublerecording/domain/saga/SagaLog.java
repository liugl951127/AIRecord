package com.mavis.doublerecording.domain.saga;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_saga_log")
public class SagaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", unique = true, nullable = false, length = 48)
    private String sagaId;

    @Column(name = "session_id", nullable = false, length = 32)
    private String sessionId;

    @Column(name = "saga_type", nullable = false, length = 32)
    private String sagaType;

    @Column(name = "current_step", nullable = false, length = 32)
    private String currentStep;

    @Column(name = "state", nullable = false, length = 16)
    private String state;  // STARTED/STEP_DONE/FAILED/COMPENSATING/COMPENSATED/COMPLETED

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
