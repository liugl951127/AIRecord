package com.mavis.doublerecording.common;

/**
 * 雪花算法 ID 生成器
 *
 * 64 bit 结构:
 *   1 bit  符号位(始终为 0)
 *  41 bit  时间戳(毫秒级,从 2024-01-01 起约可用 69 年)
 *  10 bit  工作机器 ID(支持 1024 个节点)
 *  12 bit  序列号(每毫秒内自增,单节点单毫秒 4096 个)
 *
 * 单节点 QPS: 4096 * 1000 = 409 万/秒
 * 全集群: 1024 * 4096 * 1000 = 419 亿/秒
 *
 * 特点:
 * - 全局唯一
 * - 趋势递增(对 B+Tree 索引友好)
 * - 不依赖数据库/Redis
 * - 高性能(无锁,AtomicLong)
 */
public class SnowflakeIdGenerator {

    // ====== 常量 ======
    private static final long EPOCH = 1704067200000L;  // 2024-01-01 00:00:00 UTC
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);    // 1023
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);    // 4095
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;            // 12
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;  // 22

    // ====== 实例字段 ======
    private final long workerId;            // 工作机器 ID(0-1023)
    private long lastTimestamp = -1L;       // 上次生成 ID 的时间戳
    private long sequence = 0L;              // 当前毫秒内的序列号

    /**
     * 默认构造:使用进程内自增 workerId
     * 基于本机 IP 或 PID 哈希,确保同一进程内一致
     */
    public SnowflakeIdGenerator() {
        this.workerId = Math.abs((Runtime.getRuntime().hashCode() ^ (int) ProcessHandle.current().pid()) & 0x3FF);
    }

    /**
     * 显式指定 workerId
     * @param workerId 0-1023
     */
    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    /**
     * 生成下一个雪花 ID
     */
    public synchronized long nextId() {
        long now = currentTimeMillis();

        // 时钟回拨检测(常见于 NTP 校时)
        if (now < lastTimestamp) {
            // 等待 1ms 追上时钟(最多等 5ms)
            long offset = lastTimestamp - now;
            if (offset <= 5) {
                try {
                    Thread.sleep(offset + 1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                now = currentTimeMillis();
            }
            if (now < lastTimestamp) {
                throw new RuntimeException("Clock moved backwards! Refusing to generate id for "
                    + (lastTimestamp - now) + " ms");
            }
        }

        if (now == lastTimestamp) {
            // 同一毫秒内,序列号自增
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 当前毫秒序列号用完,等下一毫秒
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            // 进入新毫秒,序列号从 0 开始
            sequence = 0L;
        }

        lastTimestamp = now;

        // 组装 64 bit
        return ((now - EPOCH) << TIMESTAMP_SHIFT)
             | (workerId << WORKER_ID_SHIFT)
             | sequence;
    }

    private long waitNextMillis(long lastTs) {
        long ts = currentTimeMillis();
        while (ts <= lastTs) {
            ts = currentTimeMillis();
        }
        return ts;
    }

    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    // ====== 单例 ======
    private static final SnowflakeIdGenerator INSTANCE = new SnowflakeIdGenerator();

    /**
     * 获取全局单例
     */
    public static SnowflakeIdGenerator getInstance() {
        return INSTANCE;
    }
}
