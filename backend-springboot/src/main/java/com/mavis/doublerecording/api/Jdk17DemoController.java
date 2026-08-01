package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Jdk17FeaturesDemo;
import com.mavis.doublerecording.common.Jdk17FeaturesDemo.Customer;
import com.mavis.doublerecording.common.Jdk17FeaturesDemo.Order;
import com.mavis.doublerecording.common.Jdk17FeaturesDemo.SagaCompensated;
import com.mavis.doublerecording.common.Jdk17FeaturesDemo.SagaEvent;
import com.mavis.doublerecording.common.Jdk17FeaturesDemo.SagaFailed;
import com.mavis.doublerecording.common.Jdk17FeaturesDemo.SagaStarted;
import com.mavis.doublerecording.common.Jdk17FeaturesDemo.SagaStepDone;
import com.mavis.doublerecording.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * JDK 17 特性演示 API
 *
 * 展示项目用到的 JDK 17 新特性,供开发参考
 */
@RestController
@RequestMapping("/api/jdk17")
@RequiredArgsConstructor
public class Jdk17DemoController {

    private final Jdk17FeaturesDemo demo;

    /**
     * 1. 列出本项目用到的 JDK 17 特性
     */
    @GetMapping("/features")
    public Result<List<String>> listFeatures() {
        return Result.ok(demo.listUsedFeatures());
    }

    /**
     * 2. Pattern Matching for switch (sealed interface)
     */
    @PostMapping("/describe-event")
    public Result<String> describeEvent(@RequestBody Map<String, Object> req) {
        String type = (String) req.get("type");
        String sagaId = (String) req.get("sagaId");
        SagaEvent event = switch (type) {
            case "STARTED" -> new SagaStarted(sagaId, System.currentTimeMillis());
            case "STEP_DONE" -> new SagaStepDone(sagaId, (String) req.get("stepName"), System.currentTimeMillis());
            case "FAILED" -> new SagaFailed(sagaId, (String) req.get("reason"), System.currentTimeMillis());
            case "COMPENSATED" -> new SagaCompensated(sagaId, (Integer) req.get("steps"), System.currentTimeMillis());
            default -> throw new IllegalArgumentException("未知事件类型: " + type);
        };
        return Result.ok(demo.describeEvent(event));
    }

    /**
     * 3. Pattern Matching for instanceof
     */
    @PostMapping("/describe-value")
    public Result<String> describeValue(@RequestBody Map<String, Object> req) {
        Object value = req.get("value");
        return Result.ok(demo.describeValue(value));
    }

    /**
     * 4. Text Blocks
     */
    @PostMapping("/email-template")
    public Result<String> generateEmail(@RequestBody Map<String, String> req) {
        var customer = new Customer("C001", req.get("customerName"), Integer.parseInt(req.get("riskScore")));
        var order = new Order(req.get("orderId"), customer, Double.parseDouble(req.get("amount")));
        return Result.ok(demo.generateEmail(customer, order, req.get("riskLevel")));
    }

    /**
     * 5. Switch Expression
     */
    @GetMapping("/format-level/{level}")
    public Result<String> formatLevel(@PathVariable String level) {
        return Result.ok(demo.formatLevel(level));
    }

    /**
     * 6. Switch with guard
     */
    @GetMapping("/advice/{score}")
    public Result<String> getAdvice(@PathVariable int score) {
        return Result.ok(demo.getInvestmentAdvice(score));
    }

    /**
     * 7. 综合 demo - 处理一批事件
     */
    @PostMapping("/process-demo")
    public Result<Map<String, Object>> processDemo() {
        List<SagaEvent> events = new java.util.ArrayList<>();
        events.add(new SagaStarted("SAGA-001", System.currentTimeMillis()));
        events.add(new SagaStepDone("SAGA-001", "CREATE_ORDER", System.currentTimeMillis()));
        events.add(new SagaStepDone("SAGA-001", "DEDUCT_STOCK", System.currentTimeMillis()));
        events.add(new SagaStepDone("SAGA-001", "CHARGE", System.currentTimeMillis()));
        events.add(new SagaCompensated("SAGA-002", 2, System.currentTimeMillis()));
        return Result.ok(demo.processDemo(events));
    }
}
