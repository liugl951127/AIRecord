package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.quality.QualityMetricsService;
import com.mavis.doublerecording.quality.QualityMetricsService.QualityMetrics;
import com.mavis.doublerecording.quality.QualityMetricsService.QualitySample;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 录制质量指标 API
 *
 * 提供录制过程的实时质量数据:
 * - 视频码率/帧率/丢帧率
 * - 音频码率
 * - 网络延迟/抖动/带宽
 * - 客户端 CPU/内存
 */
@RestController
@RequestMapping("/api/quality-metrics")
@RequiredArgsConstructor
public class QualityMetricsController {

    private final QualityMetricsService qualityMetricsService;

    @PostMapping("/start/{sessionId}")
    public Result<QualityMetrics> start(@PathVariable String sessionId) {
        return Result.ok(qualityMetricsService.startMetrics(sessionId));
    }

    @GetMapping("/{sessionId}")
    public Result<QualityMetrics> get(@PathVariable String sessionId) {
        return Result.ok(qualityMetricsService.getMetrics(sessionId));
    }

    @PostMapping("/{sessionId}/sample")
    public Result<QualityMetrics> update(@PathVariable String sessionId, @RequestBody QualitySample sample) {
        return Result.ok(qualityMetricsService.updateSample(sessionId, sample));
    }

    @PostMapping("/{sessionId}/simulate-tick")
    public Result<QualityMetrics> simulateTick(@PathVariable String sessionId, @RequestParam Long elapsed) {
        return Result.ok(qualityMetricsService.simulateTick(sessionId, elapsed));
    }

    @DeleteMapping("/{sessionId}")
    public Result<QualityMetrics> stop(@PathVariable String sessionId) {
        return Result.ok(qualityMetricsService.stopMetrics(sessionId));
    }
}
