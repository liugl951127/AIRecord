package com.mavis.doublerecording.api;

import com.mavis.doublerecording.saga.example.OrderSagaExample;
import com.mavis.doublerecording.saga.example.OrderSagaExample.OrderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Saga 注解示例 API
 *
 * 提供两个端点:
 * - POST /api/saga-example/submit: 正常 Saga 流程(应全部成功)
 * - POST /api/saga-example/submit-fail-demo: 失败演示(库存不足)
 */
@Slf4j
@RestController
@RequestMapping("/api/saga-example")
public class SagaExampleController {

    @Autowired
    private OrderSagaExample orderSaga;

    /**
     * 提交订单 - 正常流程
     */
    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody OrderDTO dto) {
        log.info("[API] 收到订单提交: {}", dto.getOrderId());
        try {
            String result = orderSaga.submitOrder(dto);
            return Map.of(
                "code", 200,
                "success", true,
                "message", "Saga 执行成功",
                "data", result
            );
        } catch (Exception e) {
            log.error("[API] Saga 失败: {}", e.getMessage());
            return Map.of(
                "code", 500,
                "success", false,
                "message", e.getMessage()
            );
        }
    }

    /**
     * 失败演示 - 设置大数量触发库存不足
     */
    @PostMapping("/submit-fail-demo")
    public Map<String, Object> submitFailDemo(@RequestBody OrderDTO dto) {
        dto.setQuantity(99999);  // 强制触发库存不足
        log.info("[API] 失败演示: orderId={}, quantity={}", dto.getOrderId(), dto.getQuantity());
        try {
            String result = orderSaga.submitOrder(dto);
            return Map.of("code", 200, "success", true, "message", "异常:未失败", "data", result);
        } catch (Exception e) {
            return Map.of(
                "code", 500,
                "success", false,
                "message", e.getMessage()
            );
        }
    }
}
