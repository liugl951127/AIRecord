package com.mavis.doublerecording.domain.video;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "dr_video")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", unique = true, nullable = false, length = 64)
    private String videoId;

    @Column(name = "session_id", nullable = false, length = 32)
    private String sessionId;

    @Column(name = "file_path", length = 512)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "sha256", length = 64)
    private String sha256;

    @Column(name = "encrypted")
    private Boolean encrypted = true;

    @Column(name = "segments", columnDefinition = "INT DEFAULT 0")
    private Integer segments = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
