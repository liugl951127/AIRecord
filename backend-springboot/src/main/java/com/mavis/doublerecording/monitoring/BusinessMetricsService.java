package com.mavis.doublerecording.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 业务指标采集服务(Micrometer + Prometheus)
 *
 * 关键业务指标:
 * - airecord.recording.start  录制启动次数
 * - airecord.recording.stop   录制停止次数
 * - airecord.recording.duration 录制时长
 * - airecord.risk.detected  风险事件次数
 * - airecord.risk.critical  严重风险次数
 * - airecord.chain.blocks    区块链高度
 * - airecord.chain.transactions 交易数量
 * - airecord.api.requests   API 请求计数
 *
 * Prometheus 抓取端点: /actuator/prometheus
 */
@Service
@RequiredArgsConstructor
public class BusinessMetricsService {

    private final MeterRegistry registry;

    private Counter recordingStartCounter;
    private Counter recordingStopCounter;
    private Counter riskEventCounter;
    private Counter riskCriticalCounter;
    private Counter riskHighCounter;
    private Counter riskMediumCounter;
    private Counter riskLowCounter;
    private Counter blockMinedCounter;
    private Counter apiRequestCounter;
    private Counter aiAsrCounter;
    private Counter aiVideoCounter;
    private Counter aiBehaviorCounter;

    private Timer recordingDurationTimer;
    private Timer aiRiskTimer;

    private final AtomicInteger activeRecordings = new AtomicInteger(0);
    private final AtomicInteger chainHeight = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        recordingStartCounter = Counter.builder("airecord.recording.start")
            .description("录制启动次数")
            .register(registry);
        recordingStopCounter = Counter.builder("airecord.recording.stop")
            .description("录制停止次数")
            .register(registry);
        riskEventCounter = Counter.builder("airecord.risk.detected")
            .description("风险事件总数")
            .register(registry);
        riskCriticalCounter = Counter.builder("airecord.risk.critical")
            .description("严重风险事件次数")
            .register(registry);
        riskHighCounter = Counter.builder("airecord.risk.high")
            .description("高风险事件次数")
            .register(registry);
        riskMediumCounter = Counter.builder("airecord.risk.medium")
            .description("中风险事件次数")
            .register(registry);
        riskLowCounter = Counter.builder("airecord.risk.low")
            .description("低风险事件次数")
            .register(registry);
        blockMinedCounter = Counter.builder("airecord.chain.blocks.mined")
            .description("已挖出区块数")
            .register(registry);
        apiRequestCounter = Counter.builder("airecord.api.requests")
            .description("API 请求总数")
            .register(registry);
        aiAsrCounter = Counter.builder("airecord.ai.asr")
            .description("AI ASR 语音分析次数")
            .register(registry);
        aiVideoCounter = Counter.builder("airecord.ai.video")
            .description("AI 视频分析次数")
            .register(registry);
        aiBehaviorCounter = Counter.builder("airecord.ai.behavior")
            .description("AI 行为分析次数")
            .register(registry);

        recordingDurationTimer = Timer.builder("airecord.recording.duration")
            .description("录制时长")
            .register(registry);
        aiRiskTimer = Timer.builder("airecord.ai.risk.duration")
            .description("AI 风险检测耗时")
            .register(registry);

        // Gauges(实时值)
        registry.gauge("airecord.recording.active", activeRecordings);
        registry.gauge("airecord.chain.height", chainHeight);
    }

    public void incRecordingStart() {
        recordingStartCounter.increment();
        activeRecordings.incrementAndGet();
    }

    public void incRecordingStop(long durationSeconds) {
        recordingStopCounter.increment();
        activeRecordings.decrementAndGet();
        recordingDurationTimer.record(java.time.Duration.ofSeconds(durationSeconds));
    }

    public void incRiskEvent(String level) {
        riskEventCounter.increment();
        switch (level) {
            case "CRITICAL" -> riskCriticalCounter.increment();
            case "HIGH" -> riskHighCounter.increment();
            case "MEDIUM" -> riskMediumCounter.increment();
            case "LOW" -> riskLowCounter.increment();
        }
    }

    public void incBlockMined() {
        blockMinedCounter.increment();
        chainHeight.incrementAndGet();
    }

    public void incApiRequest() {
        apiRequestCounter.increment();
    }

    public void incAiAsr() {
        aiAsrCounter.increment();
    }

    public void incAiVideo() {
        aiVideoCounter.increment();
    }

    public void incAiBehavior() {
        aiBehaviorCounter.increment();
    }

    public void recordAiDuration(long millis) {
        aiRiskTimer.record(java.time.Duration.ofMillis(millis));
    }

    public int getActiveRecordings() {
        return activeRecordings.get();
    }

    public int getChainHeight() {
        return chainHeight.get();
    }
}
