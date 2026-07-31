package com.mavis.doublerecording.video;

import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.domain.video.Video;
import com.mavis.doublerecording.domain.video.VideoRepository;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 视频服务(简化实现)
 *
 * 真实生产中应该:
 * 1. WebRTC 分片录制 → MinIO/OSS
 * 2. 合成 MP4 + 计算 SHA256
 * 3. SM4 加密存储
 * 4. 异地灾备
 *
 * 这里模拟整个流程,返回哈希
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final EventStore eventStore;

    @Value("${app.double-recording.video-storage-path}")
    private String storagePath;

    @Value("${app.double-recording.demo-mode:true}")
    private boolean demoMode;

    /**
     * 启动录制(模拟)
     */
    public String startRecording(String sessionId) {
        log.info("[视频服务] 启动录制: sessionId={}, demo={}", sessionId, demoMode);

        Map<String, Object> payload = new HashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("action", "START");
        eventStore.append(sessionId, "VIDEO", sessionId, "VideoRecordingStarted", payload);

        // 真实场景:返回 WebRTC 录制句柄
        return "REC_HANDLE_" + sessionId;
    }

    /**
     * 停止录制 + 合成视频(模拟)
     */
    @Transactional
    public Map<String, Object> stopAndMerge(String sessionId, int totalDurationSec, int segmentCount) {
        log.info("[视频服务] 停止录制并合成: sessionId={}, duration={}s, segments={}",
            sessionId, totalDurationSec, segmentCount);

        String videoId = IdGenerator.videoId(sessionId);

        // 模拟:计算 SHA256
        String hashInput = sessionId + totalDurationSec + System.nanoTime();
        String sha256 = sha256(hashInput);

        Video video = new Video();
        video.setVideoId(videoId);
        video.setSessionId(sessionId);
        video.setFilePath(storagePath + "/" + videoId + ".mp4");
        video.setFileSize(demoMode ? 1024L * 1024 * 5 : 0L);  // 模拟 5MB
        video.setDurationSec(totalDurationSec);
        video.setSha256(sha256);
        video.setEncrypted(true);
        video.setSegments(segmentCount);
        videoRepository.save(video);

        // 完整性校验
        verifyIntegrity(videoId, sha256);

        Map<String, Object> payload = new HashMap<>();
        payload.put("videoId", videoId);
        payload.put("sha256", sha256);
        payload.put("duration", totalDurationSec);
        payload.put("fileSize", video.getFileSize());
        payload.put("segments", segmentCount);
        eventStore.append(sessionId, "VIDEO", videoId, "VideoMerged", payload);

        return payload;
    }

    /**
     * 完整性校验
     */
    private void verifyIntegrity(String videoId, String expectedHash) {
        // 真实场景:从存储读取文件,重新计算哈希比对
        log.debug("[视频服务] 完整性校验: videoId={}, hash={}", videoId, expectedHash);
    }

    /**
     * 计算 SHA256
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new BizException("SHA256 计算失败", e);
        }
    }

    /**
     * 查询视频
     */
    public Video getVideo(String videoId) {
        return videoRepository.findByVideoId(videoId)
            .orElseThrow(() -> new BizException(404, "视频不存在: " + videoId));
    }
}
