package com.mavis.doublerecording.api;

import com.mavis.doublerecording.ai.AIRiskDetectionService;
import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.video.RecordingComplianceService;
import com.mavis.doublerecording.video.RecordingComplianceService.RecordingState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 录制流程时间轴 API
 *
 * 提供 Server-Sent Events (SSE) 流式推送:
 * - 节点切换事件
 * - 录制状态变更
 * - AI 风控告警
 * - 设备/网络异常
 *
 * 客户端通过 EventSource 订阅,实现时间轴实时刷新
 */
@Slf4j
@RestController
@RequestMapping("/api/recording-stream")
@RequiredArgsConstructor
public class RecordingStreamController {

    private final RecordingComplianceService recordingCompliance;
    private final AIRiskDetectionService aiRiskService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 活跃的 SSE 连接:sessionId -> List<SseEmitter>
     */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 时间轴事件队列:sessionId -> List<TimelineEvent>
     */
    private final Map<String, List<TimelineEvent>> timelines = new ConcurrentHashMap<>();

    /**
     * 定时器:每 2 秒推送一次心跳 + 状态
     */
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 订阅时间轴事件流
     */
    @GetMapping(value = "/subscribe/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String sessionId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);  // 30 分钟超时
        emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // 连接断开清理
        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessionId, emitter));
        emitter.onError(t -> removeEmitter(sessionId, emitter));

        // 立即发送当前已有事件
        try {
            List<TimelineEvent> existing = timelines.getOrDefault(sessionId, new ArrayList<>());
            emitter.send(SseEmitter.event()
                .name("history")
                .data(objectMapper.writeValueAsString(existing)));
        } catch (IOException e) {
            log.warn("[SSE] 发送历史事件失败: {}", e.getMessage());
        }

        log.info("[SSE] 新订阅: session={}, 当前连接数={}", sessionId, emitters.get(sessionId).size());
        return emitter;
    }

    /**
     * 推送到时间轴(供其他 Controller 调用)
     */
    public void pushEvent(String sessionId, TimelineEvent event) {
        timelines.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(event);
        // 限制最大长度(避免内存爆炸)
        List<TimelineEvent> events = timelines.get(sessionId);
        if (events.size() > 500) {
            synchronized (events) {
                while (events.size() > 500) events.remove(0);
            }
        }
        broadcast(sessionId, event);
    }

    /**
     * 推送到所有订阅者
     */
    private void broadcast(String sessionId, TimelineEvent event) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                String json = objectMapper.writeValueAsString(event);
                emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .id(UUID.randomUUID().toString())
                    .data(json));
            } catch (Exception e) {
                log.warn("[SSE] 发送失败,移除连接: {}", e.getMessage());
                removeEmitter(sessionId, emitter);
            }
        }
    }

    private void removeEmitter(String sessionId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(sessionId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(sessionId);
        }
    }

    /**
     * 查询完整时间轴(不订阅,一次性获取)
     */
    @GetMapping("/timeline/{sessionId}")
    public Result<List<TimelineEvent>> getTimeline(@PathVariable String sessionId) {
        return Result.ok(timelines.getOrDefault(sessionId, new ArrayList<>()));
    }

    /**
     * 清除时间轴
     */
    @DeleteMapping("/timeline/{sessionId}")
    public Result<Void> clearTimeline(@PathVariable String sessionId) {
        timelines.remove(sessionId);
        return Result.ok();
    }

    /**
     * 推入节点切换事件(供 RecordingCompliance 调用)
     */
    public void pushNodeSwitch(String sessionId, int oldNode, int newNode, int nodeDur) {
        TimelineEvent event = new TimelineEvent();
        event.setType("NODE_SWITCH");
        event.setSessionId(sessionId);
        event.setTimestamp(LocalDateTime.now());
        event.setTitle(String.format("N%02d → N%02d", oldNode, newNode));
        event.setDescription(String.format("节点切换,上节点时长 %d 秒", nodeDur));
        event.setLevel("INFO");
        event.setNodeSeq(newNode);
        event.setIcon("node");
        event.setData(Map.of("oldNode", oldNode, "newNode", newNode, "duration", nodeDur));
        pushEvent(sessionId, event);
    }

    /**
     * 推入风控事件
     */
    public void pushRiskEvent(String sessionId, AIRiskDetectionService.RiskEvent riskEvent) {
        TimelineEvent event = new TimelineEvent();
        event.setType("RISK_EVENT");
        event.setSessionId(sessionId);
        event.setTimestamp(LocalDateTime.now());
        event.setTitle(riskEvent.getTypeName() + " - " + riskEvent.getLevelName());
        event.setDescription(riskEvent.getContent());
        event.setLevel(riskEvent.getLevel().getTag().toUpperCase());
        event.setNodeSeq(0);
        event.setIcon("warning");
        event.setData(Map.of(
            "eventId", riskEvent.getEventId(),
            "type", riskEvent.getTypeName(),
            "level", riskEvent.getLevelName(),
            "content", riskEvent.getContent(),
            "source", riskEvent.getSource()
        ));
        pushEvent(sessionId, event);
    }

    /**
     * 心跳推送(每 2 秒)
     */
    @Scheduled(fixedRate = 2000)
    public void heartbeat() {
        emitters.forEach((sessionId, list) -> {
            RecordingState state = recordingCompliance.getState(sessionId);
            if (state == null) return;
            TimelineEvent event = new TimelineEvent();
            event.setType("HEARTBEAT");
            event.setSessionId(sessionId);
            event.setTimestamp(LocalDateTime.now());
            event.setTitle("心跳");
            event.setDescription("录制中: N" + state.getCurrentNodeSeq()
                + (state.isPaused() ? " (暂停)" : ""));
            event.setLevel("DEBUG");
            event.setNodeSeq(state.getCurrentNodeSeq());
            event.setIcon("pulse");
            for (SseEmitter emitter : list) {
                try {
                    String json = objectMapper.writeValueAsString(event);
                    emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data(json));
                } catch (Exception e) {
                    removeEmitter(sessionId, emitter);
                }
            }
        });
    }

    /**
     * 时间轴事件
     */
    @lombok.Data
    public static class TimelineEvent {
        private String type;        // NODE_SWITCH / RISK_EVENT / HEARTBEAT / INFO / WARN
        private String sessionId;
        private LocalDateTime timestamp;
        private String title;
        private String description;
        private String level;       // INFO / WARN / ERROR / FATAL / DEBUG
        private int nodeSeq;
        private String icon;        // node / warning / pulse / check
        private Map<String, Object> data = new LinkedHashMap<>();
    }
}
