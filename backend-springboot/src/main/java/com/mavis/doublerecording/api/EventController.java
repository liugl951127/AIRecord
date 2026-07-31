package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.domain.event.EventLog;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
public class EventController {

    private final EventStore eventStore;

    /**
     * 查询会话的所有事件
     */
    @GetMapping("/{sessionId}")
    public Result<List<EventLog>> getEvents(@PathVariable String sessionId) {
        return Result.ok(eventStore.getSessionEvents(sessionId));
    }
}
