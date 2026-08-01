package com.mavis.doublerecording.quality;

import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 录制质量指标服务
 *
 * 实时统计录制过程中的:
 * - 视频码率(kbps)
 * - 视频帧率(fps)
 * - 视频丢帧率
 * - 音频码率
 * - 网络延迟 / 抖动
 * - 带宽占用
 * - 客户端 CPU/内存
 *
 * 实际生产对接:
 * - WebRTC getStats() API
 * - mediasoup 事件上报
 * - 浏览器 navigator.connection
 */
@Service
public class QualityMetricsService {

    private final Map<String, QualityMetrics> metricsMap = new ConcurrentHashMap<>();

    /**
     * 启动质量监控
     */
    public QualityMetrics startMetrics(String sessionId) {
        QualityMetrics metrics = new QualityMetrics();
        metrics.setSessionId(sessionId);
        metrics.setStartTime(LocalDateTime.now());
        metrics.setVideoBitrate(2500L);     // 初始 2.5 Mbps
        metrics.setVideoFramerate(30);     // 30 fps
        metrics.setVideoDroppedFrames(0);
        metrics.setVideoDroppedRate(0.0);
        metrics.setAudioBitrate(128L);      // 128 kbps
        metrics.setNetworkLatency(50);     // 50 ms
        metrics.setNetworkJitter(5);
        metrics.setBandwidthUsage(2628L);   // 2.5+0.128 Mbps
        metrics.setClientCpuUsage(15.0);   // 15%
        metrics.setClientMemoryUsage(256.0); // 256 MB
        metricsMap.put(sessionId, metrics);
        return metrics;
    }

    /**
     * 推一次采样(实际由 WebRTC 回调/定时器触发)
     */
    public QualityMetrics updateSample(String sessionId, QualitySample sample) {
        QualityMetrics metrics = metricsMap.get(sessionId);
        if (metrics == null) {
            metrics = startMetrics(sessionId);
        }
        if (sample.getVideoBitrate() != null) {
            metrics.setVideoBitrate(sample.getVideoBitrate());
        }
        if (sample.getVideoFramerate() != null) {
            metrics.setVideoFramerate(sample.getVideoFramerate());
        }
        if (sample.getVideoDroppedFrames() != null) {
            metrics.setVideoDroppedFrames(sample.getVideoDroppedFrames());
            // 计算丢帧率
            long total = metrics.getVideoFramerate() != null ?
                metrics.getVideoFramerate() * 60 : 1800;  // 估算 60s
            metrics.setVideoDroppedRate((double) sample.getVideoDroppedFrames() / total);
        }
        if (sample.getAudioBitrate() != null) {
            metrics.setAudioBitrate(sample.getAudioBitrate());
        }
        if (sample.getNetworkLatency() != null) {
            metrics.setNetworkLatency(sample.getNetworkLatency());
        }
        if (sample.getNetworkJitter() != null) {
            metrics.setNetworkJitter(sample.getNetworkJitter());
        }
        metrics.setBandwidthUsage(
            (metrics.getVideoBitrate() != null ? metrics.getVideoBitrate() : 0) +
            (metrics.getAudioBitrate() != null ? metrics.getAudioBitrate() : 0));
        metrics.setLastUpdateTime(LocalDateTime.now());
        return metrics;
    }

    /**
     * 模拟 WebRTC 实时数据(给前端时间轴显示)
     */
    public QualityMetrics simulateTick(String sessionId, long elapsedSeconds) {
        QualityMetrics metrics = metricsMap.get(sessionId);
        if (metrics == null) {
            metrics = startMetrics(sessionId);
        }
        // 真实场景从 getStats() 获取,这里用波动的模拟值
        long tick = elapsedSeconds / 5;
        metrics.setVideoBitrate(2200L + (long) (Math.sin(tick) * 300));
        metrics.setVideoFramerate((int) (28 + Math.sin(tick / 2) * 2));
        metrics.setAudioBitrate(96L + (long) (Math.sin(tick) * 16));
        metrics.setNetworkLatency((int) (45 + Math.sin(tick / 3) * 15));
        metrics.setNetworkJitter((int) (3 + Math.abs(Math.sin(tick / 2)) * 5));
        metrics.setVideoDroppedFrames((int) (tick / 30));
        metrics.setClientCpuUsage(12.0 + Math.abs(Math.sin(tick / 2)) * 8);
        metrics.setClientMemoryUsage(220.0 + Math.abs(Math.sin(tick / 3)) * 40);
        metrics.setBandwidthUsage(
            (metrics.getVideoBitrate() != null ? metrics.getVideoBitrate() : 0) +
            (metrics.getAudioBitrate() != null ? metrics.getAudioBitrate() : 0));
        metrics.setLastUpdateTime(LocalDateTime.now());
        return metrics;
    }

    /**
     * 结束监控
     */
    public QualityMetrics stopMetrics(String sessionId) {
        return metricsMap.remove(sessionId);
    }

    /**
     * 获取指标
     */
    public QualityMetrics getMetrics(String sessionId) {
        return metricsMap.get(sessionId);
    }

    @Data
    public static class QualityMetrics {
        private String sessionId;
        private LocalDateTime startTime;
        private LocalDateTime lastUpdateTime;
        // 视频
        private Long videoBitrate;       // kbps
        private Integer videoFramerate;   // fps
        private Integer videoDroppedFrames;
        private Double videoDroppedRate;  // 0.0-1.0
        private String videoResolution = "1280x720";
        // 音频
        private Long audioBitrate;       // kbps
        // 网络
        private Integer networkLatency;  // ms
        private Integer networkJitter;   // ms
        private Long bandwidthUsage;     // kbps
        // 客户端
        private Double clientCpuUsage;    // %
        private Double clientMemoryUsage; // MB

        public String getQualityGrade() {
            if (videoDroppedRate != null && videoDroppedRate > 0.05) return "POOR";
            if (videoDroppedRate != null && videoDroppedRate > 0.02) return "FAIR";
            if (networkLatency != null && networkLatency > 200) return "FAIR";
            if (videoFramerate != null && videoFramerate < 20) return "FAIR";
            return "EXCELLENT";
        }
    }

    @Data
    public static class QualitySample {
        private Long videoBitrate;
        private Integer videoFramerate;
        private Integer videoDroppedFrames;
        private Long audioBitrate;
        private Integer networkLatency;
        private Integer networkJitter;
    }
}
