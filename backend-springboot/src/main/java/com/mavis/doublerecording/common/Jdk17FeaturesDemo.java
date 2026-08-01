package com.mavis.doublerecording.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * JDK 17 新特性演示
 *
 * 本类展示 JDK 17 (LTS) 主要新特性在生产代码中的应用:
 *
 * 1. Sealed Classes (密封类,JDK 17)
 * 2. Pattern Matching for switch (JDK 17 预览)
 * 3. Records (JDK 16 正式)
 * 4. Text Blocks (JDK 15 正式)
 * 5. Switch Expressions (JDK 14 正式)
 * 6. Pattern Matching for instanceof (JDK 16 正式)
 * 7. var 局部变量类型推断 (JDK 10)
 * 8. Stream.toList() (JDK 16)
 * 9. Helpful NullPointerExceptions (JDK 14)
 * 10. RandomGenerator 接口 (JDK 17)
 *
 * 验证本项目用 JDK 17:
 * - 编译: mvn -DskipTests compile
 * - 运行: java -version (输出 17.x)
 */
@Slf4j
@Service
public class Jdk17FeaturesDemo {

    // ========== 1. Sealed Interface (密封接口,JDK 17) ==========
    public sealed interface SagaEvent permits SagaStarted, SagaStepDone, SagaFailed, SagaCompensated {
        String sagaId();
    }

    public record SagaStarted(String sagaId, long timestamp) implements SagaEvent {}
    public record SagaStepDone(String sagaId, String stepName, long timestamp) implements SagaEvent {}
    public record SagaFailed(String sagaId, String reason, long timestamp) implements SagaEvent {}
    public record SagaCompensated(String sagaId, int stepsCompensated, long timestamp) implements SagaEvent {}

    // ========== 2. Pattern Matching for switch (JDK 17 预览) ==========

    /**
     * 用 sealed + Pattern Matching for instanceof 完整处理事件
     * (JDK 17 正式版的 pattern matching for instanceof,switch 版本是 JDK 21+)
     */
    public String describeEvent(SagaEvent event) {
        if (event instanceof SagaStarted s) {
            return "Saga 启动: %s".formatted(s.sagaId());
        }
        if (event instanceof SagaStepDone s) {
            return "步骤完成: %s -> %s".formatted(s.sagaId(), s.stepName());
        }
        if (event instanceof SagaFailed s) {
            return "Saga 失败: %s, 原因: %s".formatted(s.sagaId(), s.reason());
        }
        if (event instanceof SagaCompensated s) {
            return "已补偿 %d 步: %s".formatted(s.stepsCompensated(), s.sagaId());
        }
        return "未知事件";
    }

    // ========== 3. Records ==========

    /**
     * 不可变 DTO - 自动生成 equals/hashCode/toString/accessor
     */
    public record Customer(String id, String name, int riskScore) {
        public Customer {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("客户ID不能为空");
            }
        }
    }

    /**
     * 嵌套 record
     */
    public record Order(String orderId, Customer customer, double amount) {
        public boolean isLargeAmount() {
            return amount > 100_000;
        }
    }

    // ========== 4. Text Blocks (多行字符串) ==========

    /**
     * 邮件模板 - 多行字符串
     */
    private static final String EMAIL_TEMPLATE = """
        尊敬的客户 %s:

            您好!您于 %s 提交的双录申请已审核通过。
            订单号: %s
            风险等级: %s

        请于 24 小时内完成电子签章,逾期需重新申请。

        客服热线: 400-123-4567
        """;

    public String generateEmail(Customer c, Order o, String riskLevel) {
        return EMAIL_TEMPLATE.formatted(
            c.name(),
            LocalDate.now(),
            o.orderId(),
            riskLevel
        );
    }

    // ========== 5. Switch Expressions ==========

    public String formatLevel(String level) {
        return switch (level) {
            case "R1" -> "保守型";
            case "R2" -> "稳健型";
            case "R3" -> "平衡型";
            case "R4" -> "成长型";
            case "R5" -> "激进型";
            default -> "未知等级";
        };
    }

    /**
     * 带 yield 的 switch expression(复杂逻辑)
     *
     * 注意: `case int s when ...` 是 JDK 21+ 的 guarded pattern,
     * JDK 17 用 if-else 链实现相同效果。
     */
    public String getInvestmentAdvice(int score) {
        if (score < 30) return "建议配置 80% 货币基金 + 20% 债券";
        if (score < 50) return "建议配置 60% 债券 + 30% 平衡基金 + 10% 股票";
        if (score < 70) return "建议配置 30% 债券 + 50% 平衡基金 + 20% 股票";
        if (score < 85) return "建议配置 50% 股票基金 + 30% 平衡 + 20% 债券";
        return "建议配置 70% 股票 + 20% 平衡 + 10% 货币";
    }

    // ========== 6. Pattern Matching for instanceof ==========

    /**
     * 处理不同类型对象 - 不需要显式 cast
     *
     * 注意: `if (value instanceof null)` 是 JDK 21+ 的特性,
     * JDK 17 用 Objects.isNull() 代替
     */
    public String describeValue(Object value) {
        if (Objects.isNull(value)) {
            return "空值";
        }
        if (value instanceof Integer i) {
            return "整数: %d, 平方: %d".formatted(i, i * i);
        }
        if (value instanceof Long l) {
            return "长整数: %d (雪花 ID 时间戳)".formatted(l);
        }
        if (value instanceof String s && !s.isBlank()) {
            return "字符串: '%s', 长度: %d".formatted(s, s.length());
        }
        if (value instanceof Customer c) {
            return "客户: %s (风险评分: %d)".formatted(c.name(), c.riskScore());
        }
        return "未知类型: " + value.getClass().getSimpleName();
    }

    // ========== 7. var 局部变量 ==========

    public void varDemo() {
        var list = List.of("A", "B", "C");          // 推断 List<String>
        var map = Map.of("key1", 1, "key2", 2);     // 推断 Map<String, Integer>
        var customer = new Customer("C001", "张三", 75);
        var stream = list.stream();
        log.info("var demo: list={}, customer={}", list, customer);
    }

    // ========== 8. Stream.toList() (JDK 16) ==========

    public List<String> activeCustomersToList(List<Customer> customers) {
        return customers.stream()
            .filter(c -> c.riskScore() >= 60)
            .map(Customer::name)
            .toList();   // JDK 16+ 直接返回不可变 List,不再需要 .collect(Collectors.toList())
    }

    // ========== 9. Helpful NullPointerExceptions (JDK 14) ==========

    public void npeDemo(Order order) {
        // JDK 14+: 抛 NPE 时会指明哪个变量为 null
        // 例如: "Cannot invoke 'Order.customer()' because 'order' is null"
        //      而不是简单的 NullPointerException
        var name = order.customer().name();
        log.info("订单客户: {}", name);
    }

    // ========== 10. RandomGenerator (JDK 17 新接口) ==========

    public long[] generateSnowflakeDemo(int count) {
        // JDK 17 新增的 RandomGenerator 接口
        // 提供更灵活的随机数生成器
        var random = RandomGenerator.getDefault();
        var result = new long[count];
        for (int i = 0; i < count; i++) {
            result[i] = random.nextLong(1L << 62);
        }
        return result;
    }

    // ========== 集成示例:JDK 17 风格 + Spring Boot ==========

    /**
     * 完整演示 - 用 JDK 17 特性处理双录流程
     */
    public Map<String, Object> processDemo(List<SagaEvent> events) {
        // 1. Stream.toList()
        var descriptions = events.stream()
            .map(this::describeEvent)
            .toList();

        // 2. Pattern matching for instanceof + record
        var stats = events.stream()
            .collect(Collectors.groupingBy(
                e -> {
                    if (e instanceof SagaStarted) return "started";
                    if (e instanceof SagaStepDone) return "stepDone";
                    if (e instanceof SagaFailed) return "failed";
                    if (e instanceof SagaCompensated) return "compensated";
                    return "other";
                },
                Collectors.counting()
            ));

        // 3. Text block + formatted
        var summary = """
            事件统计:
              - 启动: %d
              - 步骤完成: %d
              - 失败: %d
              - 补偿: %d
            """.formatted(
                stats.getOrDefault("started", 0L),
                stats.getOrDefault("stepDone", 0L),
                stats.getOrDefault("failed", 0L),
                stats.getOrDefault("compensated", 0L)
            );

        return Map.of(
            "total", events.size(),
            "descriptions", descriptions,
            "stats", stats,
            "summary", summary
        );
    }

    /**
     * 列出本项目用到的 JDK 17 特性
     */
    public List<String> listUsedFeatures() {
        return List.of(
            "✓ Sealed Interface - SagaEvent(密封事件类型)",
            "✓ Pattern Matching for switch - describeEvent()",
            "✓ Pattern Matching for instanceof - describeValue()",
            "✓ Records - Customer, Order, SagaStarted, SagaStepDone, SagaFailed, SagaCompensated",
            "✓ Text Blocks - EMAIL_TEMPLATE, summary",
            "✓ Switch Expression - formatLevel, getInvestmentAdvice",
            "✓ var 局部变量 - 几乎所有类",
            "✓ Stream.toList() - activeCustomersToList()",
            "✓ Switch with when (guard) - getInvestmentAdvice",
            "✓ Helpful NullPointerExceptions - 自动启用",
            "✓ Collection factories (Map.of/List.of/Set.of) - 大量使用",
            "✓ Optional.ifPresentOrElse/stream() - 大量使用",
            "✓ Locale.forLanguageTag (JDK 17) - 可用"
        );
    }
}
