# Saga 自定义注解 + AOP 切面 - 编程指南

> 线上线下双录融合系统(AIRecord) - 声明式 Saga 编排方案
>
> 用法:`@Saga` + `@SagaStep` 注解 → 框架自动编排 + 事务隔离 + 失败补偿
>
> 版本:V1.0 | 更新:2026-08-01

---

## 一、为什么需要 Saga 注解?

### 1.1 传统分布式事务的问题

```
┌──────────────────────────────────────┐
│  传统做法:写业务代码 + 手动调用补偿     │
├──────────────────────────────────────┤
│  ❌ 步骤硬编码,业务方要管 Saga 编排   │
│  ❌ 业务代码里散落 try-catch 补偿逻辑  │
│  ❌ 步骤顺序/重试/补偿容易遗漏         │
│  ❌ 事务隔离性需要每个步骤手动写       │
│  ❌ 没有可视化监控,失败要查日志       │
└──────────────────────────────────────┘
```

### 1.2 注解式 Saga 的优势

```
┌──────────────────────────────────────┐
│  注解式:声明业务,框架处理编排         │
├──────────────────────────────────────┤
│  ✅ 业务方只写 @SagaStep 步骤        │
│  ✅ 框架自动按 order 排序执行          │
│  ✅ 失败自动逆序调用 compensate        │
│  ✅ 每个步骤独立事务(REQUIRES_NEW)    │
│  ✅ 自动写 SagaLog,可视化监控         │
│  ✅ 失败重试、关键步骤等高级特性       │
└──────────────────────────────────────┘
```

---

## 二、三个注解

### 2.1 `@Saga` - Saga 入口

```java
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface Saga {
    String type();                      // Saga 类型(必填)
    int timeoutSeconds() default 30;    // 超时(秒)
    boolean autoCompensate() default true;  // 自动补偿
    String sessionKey() default "";     // SpEL:提取会话标识
    String operator() default "";       // SpEL:提取操作人
}
```

### 2.2 `@SagaStep` - 步骤

```java
@Target(METHOD)
@Retention(RUNTIME)
public @interface SagaStep {
    String name();                              // 步骤名(必填,唯一)
    int order() default Integer.MAX_VALUE;      // 顺序
    String compensate() default "";             // 补偿方法名
    boolean retryable() default false;          // 失败重试
    int maxRetries() default 3;                 // 最大重试次数
    int retryIntervalMs() default 100;          // 重试间隔
    boolean critical() default true;            // 关键步骤(失败是否终止)
}
```

### 2.3 `@Compensate` - 补偿方法(可选)

```java
@Target(METHOD)
@Retention(RUNTIME)
public @interface Compensate {
    String forStep();  // 对应的步骤名
    int order() default 0;
}
```

> 实际使用中通常**不需要** `@Compensate` 注解,直接在 `@SagaStep.compensate` 中指定方法名即可。

---

## 三、完整业务示例

### 3.1 订单 Saga(4 步骤)

```java
package com.mavis.doublerecording.saga.example;

import com.mavis.doublerecording.saga.annotation.Saga;
import com.mavis.doublerecording.saga.annotation.SagaStep;
import com.mavis.doublerecording.saga.context.SagaContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class OrderSagaExample {

    // 模拟数据存储
    private final Map<String, Integer> orderStorage = new HashMap<>();
    private final Map<String, Integer> stockStorage = new HashMap<>();
    private final Map<String, Double> paymentStorage = new HashMap<>();

    public OrderSagaExample() {
        stockStorage.put("P001", 100);
    }

    /**
     * Saga 入口
     */
    @Saga(type = "ORDER_SUBMIT", sessionKey = "#dto.orderId", operator = "'system'")
    public String submitOrder(OrderDTO dto) {
        log.info("[OrderSaga] 业务入口: orderId={}", dto.getOrderId());
        return "提交成功 - 订单号:" + dto.getOrderId();
    }

    /**
     * 步骤 1:创建订单
     */
    @SagaStep(name = "CREATE_ORDER", order = 1,
              compensate = "compensateCreateOrder",
              retryable = true, maxRetries = 3)
    public void createOrder(OrderDTO dto) {
        if (orderStorage.containsKey(dto.getOrderId())) {
            throw new RuntimeException("订单已存在: " + dto.getOrderId());
        }
        orderStorage.put(dto.getOrderId(), dto.getQuantity());
    }

    public void compensateCreateOrder(OrderDTO dto) {
        orderStorage.remove(dto.getOrderId());
    }

    /**
     * 步骤 2:扣减库存
     */
    @SagaStep(name = "DEDUCT_STOCK", order = 2,
              compensate = "compensateDeductStock",
              critical = true)
    public void deductStock(OrderDTO dto) {
        Integer stock = stockStorage.getOrDefault(dto.getProductId(), 0);
        if (stock < dto.getQuantity()) {
            throw new RuntimeException(
                "库存不足: 商品 " + dto.getProductId() + " 现有 " + stock + ", 需要 " + dto.getQuantity());
        }
        stockStorage.put(dto.getProductId(), stock - dto.getQuantity());
    }

    public void compensateDeductStock(OrderDTO dto) {
        Integer current = stockStorage.getOrDefault(dto.getProductId(), 0);
        stockStorage.put(dto.getProductId(), current + dto.getQuantity());
    }

    /**
     * 步骤 3:扣款
     */
    @SagaStep(name = "CHARGE", order = 3,
              compensate = "compensateCharge",
              critical = true)
    public void charge(OrderDTO dto) {
        if (dto.getAmount() <= 0 || dto.getAmount() > 100000) {
            throw new RuntimeException("扣款金额非法: " + dto.getAmount());
        }
        paymentStorage.put(dto.getOrderId(), dto.getAmount());
    }

    public void compensateCharge(OrderDTO dto) {
        paymentStorage.remove(dto.getOrderId());
    }

    /**
     * 步骤 4:通知用户(非关键步骤)
     */
    @SagaStep(name = "NOTIFY", order = 4,
              compensate = "compensateNotify",
              critical = false)
    public void notify(OrderDTO dto) {
        log.info("[OrderSaga] 通知用户: orderId={}", dto.getOrderId());
    }

    public void compensateNotify(OrderDTO dto) {
        log.info("[OrderSaga] 重发通知");
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
```

### 3.2 Controller 调用

```java
@RestController
@RequestMapping("/api/saga-example")
public class SagaExampleController {

    @Autowired
    private OrderSagaExample orderSaga;

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody OrderDTO dto) {
        try {
            String result = orderSaga.submitOrder(dto);
            return Map.of("code", 200, "success", true, "data", result);
        } catch (Exception e) {
            return Map.of("code", 500, "success", false, "message", e.getMessage());
        }
    }
}
```

> ⚠️ 注意:`orderSaga.submitOrder()` 由 Spring AOP 代理拦截,业务代码不感知 Saga 细节。

---

## 四、事务隔离性设计(核心)

### 4.1 三层事务隔离

```
┌────────────────────────────────────────────────────┐
│  层 1:@Saga 入口方法                                │
│  实际不需要事务 - 切面用 reflection 调步骤         │
└────────────────────────────────────────────────────┘
                ↓ 反射调用
┌────────────────────────────────────────────────────┐
│  层 2:@SagaStep 步骤(REQUIRES_NEW)                │
│  独立事务:成功提交 / 失败回滚                       │
│  互不影响                                          │
└────────────────────────────────────────────────────┘
                ↓ 任一失败
┌────────────────────────────────────────────────────┐
│  层 3:补偿方法(REQUIRES_NEW)                       │
│  独立事务:成功提交 / 失败不影响业务事务             │
└────────────────────────────────────────────────────┘
```

### 4.2 为什么这样设计?

```java
// SagaStepExecutor.java
@Component
public class SagaStepExecutor {
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Object executeInNewTx(Object bean, Method method, Object[] args) throws Throwable {
        return method.invoke(bean, args);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public Object executeCompensateInNewTx(Object bean, Method method, Object[] args) throws Throwable {
        return method.invoke(bean, args);
    }
}
```

| 传播行为 | 含义 | Saga 场景使用 |
|----------|------|---------------|
| **REQUIRED**(默认) | 有事务就加入,无就新建 | ❌ 不适合 |
| **REQUIRES_NEW** | **强制开启新事务** | ✅ **步骤 / 补偿** |
| **NESTED** | 嵌套事务(SAVEPOINT) | ❌ 部分数据库不支持 |
| **NEVER** | 非事务执行 | ❌ Saga 步骤需事务 |

### 4.3 避免 AOP 自调用

```java
// ❌ 错误:SagaAspect 不会拦截
public class OrderSaga {
    public void submitOrder() {
        this.createOrder();  // 自调用,绕过代理
    }
}

// ✅ 正确:SagaStepExecutor 反射调用
public class OrderSaga {
    public void submitOrder() {
        stepExecutor.executeInNewTx(this, getCreateOrderMethod(), args);
    }
}
```

---

## 五、SpEL 表达式支持

### 5.1 `sessionKey` - 会话标识

```java
@Saga(type = "ORDER", sessionKey = "#dto.orderId")
public void submit(OrderDTO dto) { ... }

// 实际 sessionId = dto.getOrderId() 的值
```

支持嵌套:

```java
@Saga(type = "ORDER", sessionKey = "#req.body.orderId")
public void submit(HttpRequest<Req> req) { ... }
```

### 5.2 `operator` - 操作人

```java
@Saga(type = "ORDER", operator = "'admin'")           // 固定值
@Saga(type = "ORDER", operator = "#dto.userId")        // 动态
@Saga(type = "ORDER", operator = "#user.id")           // 嵌套对象
public void submit(OrderDTO dto) { ... }
```

---

## 六、补偿方法签名灵活性

切面按以下顺序查找:

1. **完全相同签名**:`void compensate(OrderDTO)` ↔ `void step(OrderDTO)`
2. **加 SagaContext**:`void compensate(OrderDTO, SagaContext)`
3. **名称匹配**:`void compensate(...)`(任意签名)

```java
// ✅ 方式 1:原样补偿
public void compensateCreateOrder(OrderDTO dto) { ... }

// ✅ 方式 2:需要 SagaContext
public void compensateCreateOrder(OrderDTO dto, SagaContext ctx) {
    log.info("补偿会话: {}", ctx.getSagaId());
    ...
}
```

---

## 七、关键步骤 vs 非关键步骤

```java
@SagaStep(name = "NOTIFY", order = 4, critical = false)
public void notify(OrderDTO dto) { ... }
```

| 场景 | 行为 |
|------|------|
| `critical = true`(默认) | 失败 → 触发逆序补偿 → Saga 标记 COMPENSATED |
| `critical = false` | 失败 → **记日志但继续** → 后续步骤仍执行 |

**适用场景**:
- 关键:核心业务(创建订单、扣款),失败必须补偿
- 非关键:辅助业务(发送通知、记录日志),失败不影响主流程

---

## 八、运行时 SagaContext

### 8.1 共享数据传递

```java
// 步骤 1 写
public void createOrder(OrderDTO dto) {
    SagaContext ctx = SagaContextHolder.get();
    ctx.put("orderId", dto.getOrderId());
    ctx.put("createdAt", LocalDateTime.now());
}

// 步骤 2 读
public void deductStock(OrderDTO dto) {
    SagaContext ctx = SagaContextHolder.get();
    String orderId = ctx.get("orderId", String.class);
}
```

### 8.2 记录步骤结果

```java
public void createOrder(OrderDTO dto) {
    Order result = orderService.create(dto);
    SagaContext ctx = SagaContextHolder.get();
    ctx.recordStepResult("CREATE_ORDER", result);
}
```

---

## 九、实战案例 - 双录业务

```java
@Saga(type = "DOUBLE_RECORDING_COMPLETE", sessionKey = "#sessionId")
public class DoubleRecordingSaga {

    @SagaStep(name = "VIDEO_MERGE", order = 1, compensate = "compensateVideoMerge")
    public void videoMerge(String sessionId) {
        videoService.merge(sessionId);
    }

    public void compensateVideoMerge(String sessionId) {
        videoService.cleanTempFiles(sessionId);
    }

    @SagaStep(name = "QUALITY_CHECK", order = 2, compensate = "compensateQualityCheck")
    public void qualityCheck(String sessionId) {
        qualityService.check(sessionId);
    }

    public void compensateQualityCheck(String sessionId) {
        // 质检失败补偿:清空质检报告
    }

    @SagaStep(name = "SIGN", order = 3, compensate = "compensateSign")
    public void sign(String sessionId) {
        signatureService.sign(sessionId);
    }

    public void compensateSign(String sessionId) {
        signatureService.revokeSign(sessionId);
    }

    @SagaStep(name = "CHAIN_COMMIT", order = 4, compensate = "compensateChain")
    public void chainCommit(String sessionId) {
        chainService.commit(sessionId);
    }

    public void compensateChain(String sessionId) {
        chainService.rollback(sessionId);
    }
}
```

调用:

```java
@PostMapping("/sessions/{id}/complete")
public void completeSession(@PathVariable String id) {
    doubleRecordingSaga.videoMerge(id);  // ← AOP 拦截,自动编排
}
```

> 注意:虽然调用 `videoMerge`,但 AOP 看到 `@Saga` 类级注解,会收集所有 `@SagaStep` 步骤并执行整个 Saga。

---

## 十、SagaLog 自动记录

每个 Saga 都会在 `dr_saga_log` 表写入审计日志:

| state | 含义 |
|-------|------|
| STARTED | Saga 启动 |
| RUNNING | 步骤执行中 |
| STEP_DONE | 步骤成功 |
| FAILED | 步骤失败 |
| COMPENSATING | 补偿中 |
| COMPENSATED | 补偿完成 |
| COMPLETED | 全部成功 |

可视化:`http://localhost:8080/#/saga/list`

---

## 十一、API 端点(演示)

```bash
# 正常订单
curl -X POST http://localhost:8080/api/saga-example/submit \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD_001","userId":"U001","productId":"P001","quantity":1,"amount":50}'

# 触发失败(库存不足)
curl -X POST http://localhost:8080/api/saga-example/submit-fail-demo \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD_002","userId":"U001","productId":"P001","quantity":1,"amount":50}'
```

---

## 十二、常见问题(FAQ)

### Q1:为什么我的 @Saga 方法没被切面拦截?

**A:** 检查:
1. `pom.xml` 是否包含 `spring-boot-starter-aop`
2. `OrderSagaExample` 是否标注 `@Service`
3. 类路径在 `com.mavis.doublerecording.saga.example` 下
4. `@Saga` 注解是否在方法或类上

### Q2:补偿方法没被调用?

**A:** 检查:
1. `@SagaStep.compensate()` 是否正确指定方法名
2. 补偿方法可见性(public)
3. `autoCompensate = true`
4. 失败步骤是 `critical = true`

### Q3:事务没回滚?

**A:** 检查:
1. `SagaStepExecutor` 的 `@Transactional(rollbackFor = Exception.class)` 是否生效
2. 步骤方法抛出的异常是否继承自 `Exception`
3. 业务方法中是否有 `try-catch` 把异常吞了

### Q4:同一 Bean 多个 @Saga 方法?

**A:** 支持。每个方法独立 Saga,按方法签名匹配 @Saga。

---

## 十三、最佳实践

| 实践 | 说明 |
|------|------|
| **步骤原子性** | 每个步骤操作单一资源,避免大事务 |
| **幂等补偿** | 补偿方法必须可重入,重复调用不报错 |
| **重试关键步骤** | `retryable=true, maxRetries=3` |
| **非关键步骤** | 通知/日志等用 `critical=false` |
| **SagaContext 慎用** | 只传必要的标识/结果,不要塞大对象 |
| **业务异常分类** | 业务异常用 BizException,系统异常用 RuntimeException |

---

## 十四、版本历史

| 版本 | 日期 | 变更 |
|------|------|------|
| V1.0 | 2026-08-01 | 初版:@Saga / @SagaStep / SagaContext / SagaAspect |

---

**作者**:Mavis AI
**项目**:AIRecord - 线上线下双录融合系统
**GitHub**:https://github.com/liugl951127/AIRecord
