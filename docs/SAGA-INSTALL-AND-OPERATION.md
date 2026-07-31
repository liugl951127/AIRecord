# Saga 协调器 - 安装步骤与操作手册

> 分布式事务解决方案 · 顺向执行 + 逆序补偿 + 最终一致性

## 目录

- [1. 概述](#1-概述)
- [2. 核心概念](#2-核心概念)
- [3. 系统架构](#3-系统架构)
- [4. 安装步骤](#4-安装步骤)
- [5. 配置说明](#5-配置说明)
- [6. 业务接入指南](#6-业务接入指南)
- [7. API 操作手册](#7-api-操作手册)
- [8. 数据库表结构](#8-数据库表结构)
- [9. 监控与告警](#9-监控与告警)
- [10. 常见问题 FAQ](#10-常见问题-faq)

---

## 1. 概述

### 1.1 什么是 Saga

**Saga** 是一种管理跨服务/跨资源**分布式事务**的解决方案,核心思想是:

- **将长事务拆分为多个本地短事务**(每个 SagaStep)
- **顺向执行**:每个步骤在本地事务内完成
- **逆序补偿**:任一步骤失败,逆序调用补偿接口,撤销已完成的操作
- **最终一致性**:不保证强一致性,但保证业务最终状态正确

### 1.2 解决的痛点

| 场景 | 传统方案问题 | Saga 方案 |
|------|------------|----------|
| **视频合成 + 订单 + 区块链存证** 三步必须原子 | 分布式事务 TCC 实现复杂、性能差 | 每步独立事务,失败自动补偿 |
| **断网/超时**导致部分成功 | 数据不一致,需人工修复 | 自动补偿已完成的步骤 |
| **跨服务调用** 链路过长 | 任一环节失败整笔回滚困难 | 每步独立,失败精确补偿 |
| **重试**策略不统一 | 重复扣款/重放问题 | 基于幂等键 + SagaLog |

### 1.3 适用场景

✅ **推荐使用**:
- 双录流程完成(视频 + 订单 + 存证 3 步)
- 跨多个微服务的业务流程
- 可以接受最终一致性的业务
- 需要补偿机制的长事务

❌ **不推荐**:
- 必须强一致性的场景(银行转账,资金清算)
- 步骤间有强同步依赖
- 无法定义补偿操作的业务

---

## 2. 核心概念

### 2.1 Saga 状态机

```
STARTED ─┬─> STEP_EXECUTING ─> STEP_DONE ─> ... ─> COMPLETED
         │          │
         │          └─> FAILED ─> COMPENSATING ─> COMPENSATED
         └─> FAILED (初始化失败)
```

| 状态 | 含义 |
|------|------|
| `STARTED` | Saga 已创建,准备执行 |
| `STEP_EXECUTING` | 某一步骤正在执行 |
| `STEP_DONE` | 当前步骤执行成功 |
| `FAILED` | 某一步骤执行失败,等待补偿 |
| `COMPENSATING` | 正在执行补偿 |
| `COMPENSATED` | 补偿完成 |
| `COMPLETED` | 全部步骤成功完成 |

### 2.2 Saga 步骤 (SagaStep)

每个 Saga 由 1..N 个 `SagaStep` 组成,每个步骤包含:

```java
new SagaStep(
    "STEP_NAME",                                              // 步骤名称(必填)
    () -> { /* 顺向逻辑 */ return resultMap; },              // 顺向执行(必填)
    (result) -> { /* 补偿逻辑 */ }                            // 补偿执行(可选)
);
```

**关键设计**:
- ✅ 顺向函数返回 `Map<String, Object>`,作为后续步骤的输入
- ✅ 补偿函数接收顺向的返回值,可用于精确撤销
- ✅ 步骤间通过 `context` 共享数据(同 sessionId)

### 2.3 幂等性保证

- 每个步骤的**顺向操作**必须实现幂等(可通过业务 ID 去重)
- 补偿操作也必须幂等(可能被重复调用)
- 通过 `sagaId` 关联整个事务,避免重复执行

---

## 3. 系统架构

### 3.1 组件关系

```
┌────────────────────────────────────────────────────────┐
│                  SagaCoordinator                       │
│  ┌──────────────────────────────────────────────┐    │
│  │   execute(sessionId, sagaType, steps)        │    │
│  └──────────────────────────────────────────────┘    │
│           │                                            │
│           ├──> SagaLogRepository (持久化状态)          │
│           ├──> EventStore (事件溯源)                   │
│           └──> 业务 Step Functions (顺向/补偿)         │
└────────────────────────────────────────────────────────┘
                │                    │
                ↓                    ↓
        ┌──────────────┐    ┌──────────────────┐
        │  dr_saga_log │    │  dr_event_log    │
        │  (状态日志)   │    │  (全审计事件)    │
        └──────────────┘    └──────────────────┘
```

### 3.2 核心文件

| 文件 | 作用 |
|------|------|
| `saga/SagaCoordinator.java` | 协调器主体,负责顺向执行+逆序补偿 |
| `saga/SagaState.java` | 状态枚举 |
| `saga/SagaStep.java` | 步骤定义(顺向+补偿) |
| `domain/saga/SagaLog.java` | Saga 日志实体 |
| `domain/saga/SagaLogRepository.java` | 日志持久化 |

---

## 4. 安装步骤

### 4.1 环境要求

| 依赖 | 版本 | 检查命令 |
|------|------|----------|
| **JDK** | 17+ | `java -version` |
| **Maven** | 3.8+ | `mvn -version` |
| **MySQL/H2** | 5.7+ / 2.x | 已包含在项目中 |
| **磁盘** | 1 GB+ | `df -h` |

### 4.2 编译安装

```bash
# 1. 进入后端目录
cd backend-springboot

# 2. 编译
mvn clean compile

# 预期输出:
# [INFO] BUILD SUCCESS
# [INFO] Total time:  10-30 s
```

### 4.3 数据库初始化

#### 方式 A:使用项目自带的 H2 内存数据库(开发/演示)

```bash
# H2 数据由 Spring Boot 自动初始化,无需手动建表
mvn spring-boot:run
```

启动日志中会看到:

```
Hibernate: create table dr_saga_log (...)
Hibernate: create table dr_event_log (...)
```

#### 方式 B:生产环境使用 MySQL

**步骤 1**:创建数据库

```sql
CREATE DATABASE double_recording DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**步骤 2**:修改 `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/double_recording?useUnicode=true&characterEncoding=utf8mb4&useSSL=false&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: your_password
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: update  # 第一次启动用 update,后续改成 none
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
  sql:
    init:
      mode: never  # MySQL 不再自动执行 data.sql
```

**步骤 3**:执行 schema.sql 初始化 saga 表

```bash
mysql -u root -p double_recording < src/main/resources/schema.sql
```

**步骤 4**:启动

```bash
mvn spring-boot:run
```

### 4.4 验证安装

#### 4.4.1 健康检查

```bash
curl http://localhost:8080/actuator/health
```

预期:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "H2"}},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

#### 4.4.2 触发一次 Saga

运行项目自带的演示脚本:

```bash
python3 full_demo.py
```

预期输出包含:
```
[5] 完成录制 + 触发 Saga 分布式事务...
  V Saga 执行完成
  - 订单ID: ORD20260801...
  - 区块链存证: CHAIN-...
```

#### 4.4.3 查询 Saga 日志

```bash
# 通过 H2 控制台查看(开发环境)
# 浏览器访问:http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:double_recording
# 用户名:sa  密码:sa

# SQL:
SELECT saga_id, session_id, saga_type, current_step, state, 
       started_at, completed_at, error_message
FROM dr_saga_log
ORDER BY started_at DESC
LIMIT 10;
```

---

## 5. 配置说明

### 5.1 application.yml 配置

```yaml
app:
  saga:
    # 是否启用 Saga 自动补偿(生产建议 true)
    auto-compensate: true
    
    # 单个步骤最大执行时间(秒),超时触发补偿
    step-timeout-seconds: 30
    
    # 补偿最大重试次数
    compensate-retry-times: 3
    
    # 补偿失败后是否记录到待处理队列(人工介入)
    compensate-failure-queue: true
    
    # Saga 日志保留天数(用于审计)
    log-retention-days: 365
```

### 5.2 日志配置(logback-spring.xml)

```xml
<configuration>
  <!-- Saga 专用日志 -->
  <logger name="com.mavis.doublerecording.saga" level="INFO" 
          additivity="false">
    <appender name="SAGA_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
      <file>logs/saga.log</file>
      <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/saga.%d{yyyy-MM-dd}.log</fileNamePattern>
        <maxHistory>30</maxHistory>
      </rollingPolicy>
      <encoder>
        <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
      </encoder>
    </appender>
  </logger>
</configuration>
```

### 5.3 数据库连接池调优

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 最大连接数
      minimum-idle: 5             # 最小空闲
      connection-timeout: 30000   # 连接超时 30s
      idle-timeout: 600000        # 空闲超时 10min
      max-lifetime: 1800000       # 连接最大生命周期 30min
```

---

## 6. 业务接入指南

### 6.1 三步快速接入

#### Step 1:定义步骤 (新建 `BusinessSaga.java`)

```java
@Service
@RequiredArgsConstructor
public class OrderSaga {

    private final SagaCoordinator sagaCoordinator;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;

    /**
     * 提交订单的分布式事务
     */
    public Map<String, Object> submitOrder(String orderId, String userId) {
        List<SagaStep> steps = new ArrayList<>();

        // Step 1: 创建订单(顺向 + 补偿)
        steps.add(new SagaStep(
            "CREATE_ORDER",
            () -> {
                // 顺向:创建订单
                orderService.create(orderId, userId);
                return Map.of("orderId", orderId);
            },
            result -> {
                // 补偿:删除订单
                log.info("[补偿] 删除订单: {}", result.get("orderId"));
                orderService.deleteById((String) result.get("orderId"));
            }
        ));

        // Step 2: 扣减库存
        steps.add(new SagaStep(
            "DEDUCT_INVENTORY",
            () -> {
                inventoryService.deduct(orderId);
                return Map.of("orderId", orderId);
            },
            result -> {
                log.info("[补偿] 恢复库存: {}", result.get("orderId"));
                inventoryService.restore(orderId);
            }
        ));

        // Step 3: 处理支付
        steps.add(new SagaStep(
            "PROCESS_PAYMENT",
            () -> {
                paymentService.charge(orderId);
                return Map.of("orderId", orderId, "status", "PAID");
            },
            result -> {
                log.info("[补偿] 退款: {}", result.get("orderId"));
                paymentService.refund(orderId);
            }
        ));

        // 执行 Saga
        return sagaCoordinator.execute(orderId, "ORDER_SUBMIT", steps);
    }
}
```

#### Step 2:在 Controller 中调用

```java
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderSaga orderSaga;

    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@RequestBody OrderRequest req) {
        return Result.ok(orderSaga.submitOrder(req.getOrderId(), req.getUserId()));
    }
}
```

#### Step 3:验证

```bash
curl -X POST http://localhost:8080/api/order/submit \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORDER_001","userId":"USER_001"}'
```

### 6.2 进阶用法

#### 6.2.1 异步 Saga

```java
@Async
public CompletableFuture<Map<String, Object>> submitOrderAsync(String orderId) {
    return CompletableFuture.completedFuture(
        sagaCoordinator.execute(orderId, "ORDER_SUBMIT", steps)
    );
}
```

#### 6.2.2 条件步骤

```java
// 跳过某一步(返回 null 时 SagaCoordinator 会视为成功但不记录)
steps.add(new SagaStep(
    "OPTIONAL_STEP",
    () -> {
        if (someCondition()) {
            return doWork();
        }
        return null;  // 跳过
    },
    result -> {
        if (result != null) {
            undo();
        }
    }
));
```

#### 6.2.3 重试机制(在步骤内部实现)

```java
steps.add(new SagaStep(
    "CALL_REMOTE_API",
    () -> {
        return retryTemplate.execute(ctx -> {
            // 最多重试 3 次
            return remoteClient.call();
        });
    },
    result -> log.info("补偿:通知远程服务撤销")
));
```

---

## 7. API 操作手册

### 7.1 查询 Saga 状态

#### 7.1.1 通过 SessionID 查询

```bash
# 内部 Java API
sagaCoordinator.getSagaLog("DR20260801123456789")

# 返回 SagaLog 对象
```

#### 7.1.2 SQL 查询(常用)

```sql
-- 查询某会话的 Saga 状态
SELECT * FROM dr_saga_log WHERE session_id = 'DR20260801123456789';

-- 查询所有失败的 Saga
SELECT * FROM dr_saga_log 
WHERE state IN ('FAILED', 'COMPENSATING', 'COMPENSATED')
ORDER BY started_at DESC
LIMIT 100;

-- 统计各状态的 Saga 数量
SELECT state, COUNT(*) as count
FROM dr_saga_log
WHERE started_at > DATE_SUB(NOW(), INTERVAL 7 DAY)
GROUP BY state;

-- 查询补偿失败的 Saga(需人工介入)
SELECT * FROM dr_saga_log
WHERE error_message LIKE '%CompensationFailed%'
   OR current_step LIKE 'COMPENSATE_%'
ORDER BY started_at DESC;
```

### 7.2 手动重试 Saga

如果 Saga 失败后希望人工介入重试,可通过以下方式:

#### 方式 A:重新执行整个 Saga(幂等场景)

```java
// 适用于所有步骤都幂等的场景
sagaCoordinator.execute(sessionId, "ORDER_SUBMIT", steps);
```

#### 方式 B:从失败步骤继续(需自定义)

扩展 SagaCoordinator,增加 `resume(sagaId)` 方法:

```java
@Transactional
public void resume(String sagaId) {
    SagaLog log = sagaLogRepository.findBySagaId(sagaId)
        .orElseThrow(() -> new BizException("Saga 不存在"));
    
    if (!"FAILED".equals(log.getState())) {
        throw new BizException("只有 FAILED 状态的 Saga 可以重试");
    }
    
    // 从失败步骤重新执行
    log.setState("STARTED");
    sagaLogRepository.save(log);
    // 重新构造并执行步骤列表...
}
```

### 7.3 强制完成 Saga(慎用)

```java
// 紧急情况下,人工标记为完成
sagaLog.setState("COMPLETED");
sagaLog.setErrorMessage("人工强制完成 - 运维:XXX 时间:XXX");
sagaLogRepository.save(sagaLog);
```

⚠️ **注意**:强制完成会导致数据不一致,务必记录原因。

### 7.4 取消 Saga

```java
// 把状态改为 CANCELLED,后续步骤不再执行
sagaLog.setState("CANCELLED");
sagaLog.setErrorMessage("人工取消 - 原因:XXX");
sagaLogRepository.save(sagaLog);
```

---

## 8. 数据库表结构

### 8.1 `dr_saga_log` 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键自增 |
| `saga_id` | VARCHAR(48) | Saga 唯一 ID(全局唯一) |
| `session_id` | VARCHAR(32) | 业务会话 ID |
| `saga_type` | VARCHAR(32) | Saga 类型(如 `ORDER_SUBMIT`) |
| `current_step` | VARCHAR(32) | 当前执行/失败的步骤 |
| `state` | VARCHAR(16) | Saga 状态 |
| `payload` | TEXT | 上下文数据 JSON |
| `error_message` | VARCHAR(1024) | 错误信息 |
| `started_at` | TIMESTAMP | 启动时间 |
| `updated_at` | TIMESTAMP | 最后更新时间 |
| `completed_at` | TIMESTAMP | 完成时间 |

**索引**:
- `UNIQUE (saga_id)` - 快速按 SagaID 查询
- `INDEX (session_id)` - 按业务会话查询
- `INDEX (state, started_at)` - 按状态+时间统计

### 8.2 关联表 `dr_event_log`

Saga 执行的所有事件都会写入事件溯源表:

| 事件类型 | 触发时机 |
|----------|----------|
| `SagaStarted` | Saga 启动(可扩展) |
| `SagaCompleted` | 全部步骤成功 |
| `CompensationFailed` | 补偿失败(需人工介入) |

---

## 9. 监控与告警

### 9.1 关键指标

| 指标 | 阈值 | 告警级别 |
|------|------|----------|
| 失败率 (FAILED / 总数) | > 5% | P2 |
| 补偿失败率 (CompensationFailed / FAILED) | > 10% | P1 |
| 平均执行时长 | > 30s | P3 |
| 待人工处理 Saga 数 | > 10 | P1 |

### 9.2 Prometheus 集成(可选)

添加依赖:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

在 `SagaCoordinator` 中埋点:

```java
// 启动 MeterRegistry
MeterRegistry registry = ...;

// 记录执行次数
registry.counter("saga.executed", "type", sagaType, "state", "success").increment();
registry.counter("saga.executed", "type", sagaType, "state", "failed").increment();

// 记录执行时长
Timer timer = registry.timer("saga.duration", "type", sagaType);
timer.record(() -> {
    // saga 执行
});
```

### 9.3 Grafana Dashboard 建议

```yaml
panels:
  - title: "Saga 成功率"
    query: "sum(rate(saga_executed_total{state='success'}[5m])) / sum(rate(saga_executed_total[5m]))"
    
  - title: "平均 Saga 耗时"
    query: "histogram_quantile(0.95, rate(saga_duration_seconds_bucket[5m]))"
    
  - title: "补偿失败数"
    query: "sum(rate(saga_executed_total{state='compensation_failed'}[5m]))"
```

### 9.4 日常巡检 SQL

```sql
-- 1. 今日失败 Saga 数量
SELECT COUNT(*) AS today_failed
FROM dr_saga_log
WHERE state IN ('FAILED', 'COMPENSATED')
  AND started_at > CURRENT_DATE;

-- 2. 补偿失败的待处理 Saga
SELECT saga_id, session_id, error_message, started_at
FROM dr_saga_log
WHERE error_message LIKE '%CompensationFailed%'
  AND completed_at IS NULL
ORDER BY started_at;

-- 3. 平均执行时长
SELECT 
    AVG(TIMESTAMPDIFF(SECOND, started_at, completed_at)) AS avg_seconds,
    MAX(TIMESTAMPDIFF(SECOND, started_at, completed_at)) AS max_seconds
FROM dr_saga_log
WHERE completed_at IS NOT NULL
  AND started_at > DATE_SUB(NOW(), INTERVAL 1 DAY);
```

---

## 10. 常见问题 FAQ

### Q1: Saga 一直停留在 `STEP_EXECUTING` 怎么办?

**原因**:某一步骤执行卡住,可能死锁或远程调用超时。

**排查**:
```bash
# 1. 查看日志
grep "Saga SAGA-xxx" logs/saga.log

# 2. 查看数据库锁情况
SHOW ENGINE INNODB STATUS;

# 3. 检查步骤函数代码,看是否有阻塞操作
```

**解决**:
- 缩短步骤执行时间(加超时控制)
- 排查死锁
- 必要时手动将状态改为 `FAILED` 触发补偿

### Q2: 补偿失败了怎么办?

**现象**:日志中出现 `CompensationFailed`。

**影响**:数据可能出现不一致。

**解决流程**:
1. 立即查看 `error_message`,定位哪个步骤的补偿失败
2. 检查业务数据,确认实际状态
3. 人工执行补偿(写 SQL 或调用业务接口)
4. 完成后手动将 Saga 状态改为 `COMPENSATED`
5. 记录到运维文档,后续优化

### Q3: 如何测试 Saga 的失败场景?

**编写测试**:

```java
@SpringBootTest
public class SagaFailureTest {

    @Autowired
    private SagaCoordinator sagaCoordinator;

    @Test
    public void testCompensation() {
        // 构造一个会在第 2 步失败的 Saga
        List<SagaStep> steps = List.of(
            new SagaStep("STEP_1", () -> Map.of("ok", true), null),
            new SagaStep("STEP_2_FAIL", () -> {
                throw new RuntimeException("模拟失败");
            }, null),
            new SagaStep("STEP_3", () -> Map.of("ok", true), null)
        );

        // 验证:应该抛出异常
        assertThrows(BizException.class, 
            () -> sagaCoordinator.execute("TEST_SID", "TEST", steps));

        // 验证:Saga 状态应该是 COMPENSATED
        SagaLog log = sagaCoordinator.getSagaLog("TEST_SID");
        assertEquals("COMPENSATED", log.getState());
    }
}
```

### Q4: Saga 和 @Transactional 事务的关系?

- Saga 协调器本身在 `@Transactional` 中运行
- 每个步骤内部的业务逻辑应该**独立事务**,不要在顺向函数中开启大事务
- 避免 Saga 事务包含业务事务,否则补偿时业务事务可能不会真正回滚

**反例**(不要这样写):

```java
// 错误:大事务包整个 Saga
@Transactional
public void submitOrder(...) {
    sagaCoordinator.execute(...);  // 这个事务包含整个 Saga,补偿无效
}

// 正确:每步骤独立事务
public void submitOrder(...) {
    sagaCoordinator.execute(...);  // 协调器内部短事务
}
```

### Q5: 性能如何?能支撑多大并发?

**实测参考**(单实例):
- 简单 Saga(2-3 步骤): ~100 TPS
- 复杂 Saga(5+ 步骤,含外部调用): ~30-50 TPS
- 高并发场景:水平扩展 SagaCoordinator 实例(无状态)

**优化方向**:
- 减少步骤数
- 步骤内部使用批量操作
- 异步化外部调用
- 数据库读写分离

### Q6: 如何实现 Saga 的可视化?

**方案 A:基于 `dr_saga_log` 表 + 简单前端**

```sql
-- 查询所有 Saga 按时间分布
SELECT 
    DATE(started_at) as date,
    state,
    COUNT(*) as count
FROM dr_saga_log
GROUP BY DATE(started_at), state
ORDER BY date DESC;
```

**方案 B:基于事件流 + 时间轴展示**

参考 `EventController.getEvents(sessionId)` 接口,前端用时间轴组件展示。

### Q7: Saga 日志如何清理?

**自动清理**(推荐):

```sql
-- 每天凌晨清理 365 天前的数据
DELETE FROM dr_saga_log 
WHERE started_at < DATE_SUB(NOW(), INTERVAL 365 DAY);
```

可配合 Spring `@Scheduled`:

```java
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
public void cleanOldSagaLogs() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
    int deleted = sagaLogRepository.deleteByStartedAtBefore(cutoff);
    log.info("清理 {} 条历史 Saga 日志", deleted);
}
```

---

## 附录:完整 API 参考

| 操作 | API / 方法 | 说明 |
|------|------------|------|
| **执行 Saga** | `sagaCoordinator.execute(sessionId, type, steps)` | 主入口 |
| **查询日志** | `sagaCoordinator.getSagaLog(sessionId)` | 按 sessionId 查 |
| **查询日志(SQL)** | `SELECT * FROM dr_saga_log` | 灵活查询 |
| **事件流** | `GET /api/event/{sessionId}` | 查看所有事件 |
| **健康检查** | `GET /actuator/health` | 系统状态 |
| **完整演示** | `python3 full_demo.py` | 端到端跑通 |

---

## 联系与支持

- **项目地址**:https://github.com/liugl951127/AIRecord
- **问题反馈**:GitHub Issues
- **文档版本**:V1.0
- **最后更新**:2026-08-01

---

> **最佳实践提示**:
> 1. ✅ 每个 Saga 步骤函数必须实现**幂等**
> 2. ✅ 补偿函数必须能处理**部分执行**的情况
> 3. ✅ 关键业务 Saga 一定要做**集成测试**
> 4. ✅ 生产环境务必开启**慢日志**(>10s 告警)
> 5. ✅ 补偿失败必须进入**人工处理队列**(待办任务)
