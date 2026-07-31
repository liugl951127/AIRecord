package com.mavis.doublerecording.saga.example;

import com.mavis.doublerecording.saga.annotation.Saga;
import com.mavis.doublerecording.saga.annotation.SagaStep;
import com.mavis.doublerecording.saga.context.SagaContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 订单 Saga 示例 - 完整业务示例
 *
 * 4 个步骤:
 * 1. CREATE_ORDER    - 创建订单
 * 2. DEDUCT_STOCK    - 扣减库存
 * 3. CHARGE          - 扣款
 * 4. NOTIFY          - 通知用户
 *
 * 每个步骤有对应的补偿方法,失败时自动逆序补偿
 */
@Slf4j
@Service
public class OrderSagaExample {

    // 模拟数据存储(线程安全)
    private final Map<String, Integer> orderStorage = new HashMap<>();
    private final Map<String, Integer> stockStorage = new HashMap<>();
    private final Map<String, Double> paymentStorage = new HashMap<>();

    public OrderSagaExample() {
        // 初始化模拟库存
        stockStorage.put("P001", 100);
        stockStorage.put("P002", 50);
        stockStorage.put("P003", 200);
    }

    /**
     * Saga 入口方法
     * @Saga 注解声明:类型 + 会话 key 表达式
     */
    @Saga(type = "ORDER_SUBMIT", sessionKey = "#dto.orderId", operator = "'system'")
    public String submitOrder(OrderDTO dto) {
        log.info("[OrderSaga] 业务入口: orderId={}", dto.getOrderId());
        return "提交成功 - 订单号:" + dto.getOrderId();
    }

    /**
     * 步骤 1: 创建订单
     */
    @SagaStep(name = "CREATE_ORDER", order = 1,
              compensate = "compensateCreateOrder",
              retryable = true, maxRetries = 3)
    public void createOrder(OrderDTO dto) {
        log.info("[OrderSaga] 步骤1 - 创建订单: {}", dto.getOrderId());
        if (orderStorage.containsKey(dto.getOrderId())) {
            throw new RuntimeException("订单已存在: " + dto.getOrderId());
        }
        orderStorage.put(dto.getOrderId(), dto.getQuantity());
        log.info("[OrderSaga] 订单已创建, 当前订单数: {}", orderStorage.size());
    }

    public void compensateCreateOrder(OrderDTO dto) {
        log.info("[OrderSaga] 补偿1 - 取消订单: {}", dto.getOrderId());
        orderStorage.remove(dto.getOrderId());
    }

    /**
     * 步骤 2: 扣减库存
     */
    @SagaStep(name = "DEDUCT_STOCK", order = 2,
              compensate = "compensateDeductStock",
              critical = true)
    public void deductStock(OrderDTO dto) {
        log.info("[OrderSaga] 步骤2 - 扣减库存: {} x {}", dto.getProductId(), dto.getQuantity());
        Integer stock = stockStorage.getOrDefault(dto.getProductId(), 0);
        if (stock < dto.getQuantity()) {
            throw new RuntimeException(
                String.format("库存不足: 商品 %s 现有 %d, 需要 %d", dto.getProductId(), stock, dto.getQuantity()));
        }
        stockStorage.put(dto.getProductId(), stock - dto.getQuantity());
        log.info("[OrderSaga] 库存扣减完成, 剩余: {}", stockStorage.get(dto.getProductId()));
    }

    public void compensateDeductStock(OrderDTO dto) {
        log.info("[OrderSaga] 补偿2 - 恢复库存: {} +{}", dto.getProductId(), dto.getQuantity());
        Integer current = stockStorage.getOrDefault(dto.getProductId(), 0);
        stockStorage.put(dto.getProductId(), current + dto.getQuantity());
    }

    /**
     * 步骤 3: 扣款
     */
    @SagaStep(name = "CHARGE", order = 3,
              compensate = "compensateCharge",
              critical = true)
    public void charge(OrderDTO dto) {
        log.info("[OrderSaga] 步骤3 - 扣款: {} 元", dto.getAmount());
        if (dto.getAmount() <= 0 || dto.getAmount() > 100000) {
            throw new RuntimeException("扣款金额非法: " + dto.getAmount());
        }
        paymentStorage.put(dto.getOrderId(), dto.getAmount());
        log.info("[OrderSaga] 扣款成功");
    }

    public void compensateCharge(OrderDTO dto) {
        log.info("[OrderSaga] 补偿3 - 退款: {} 元", dto.getAmount());
        paymentStorage.remove(dto.getOrderId());
    }

    /**
     * 步骤 4: 通知用户(非关键步骤,失败不影响)
     */
    @SagaStep(name = "NOTIFY", order = 4,
              compensate = "compensateNotify",
              critical = false)
    public void notify(OrderDTO dto) {
        log.info("[OrderSaga] 步骤4 - 通知用户: orderId={}", dto.getOrderId());
        if (dto.getOrderId() != null && dto.getOrderId().contains("FAIL_NOTIFY")) {
            throw new RuntimeException("通知服务不可用");
        }
        log.info("[OrderSaga] 通知已发送");
    }

    public void compensateNotify(OrderDTO dto) {
        log.info("[OrderSaga] 补偿4 - 重发通知");
    }

    @Data
    public static class OrderDTO {
        private String orderId;
        private String userId;
        private String productId;
        private Integer quantity;
        private Double amount;
    }
}
