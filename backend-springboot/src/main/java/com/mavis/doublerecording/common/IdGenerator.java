package com.mavis.doublerecording.common;

/**
 * 业务 ID 生成器 - 统一门面
 *
 * 内部基于 {@link SnowflakeIdGenerator} 雪花算法:
 * - 64 bit 数值,趋势递增,全局唯一
 * - 兼容现有业务代码(sessionId/orderId 等),API 签名不变
 * - 前缀保留,便于日志/调试识别
 *
 * 变更历史:
 * - V2.0 (2026-08-01): 改用雪花算法
 * - V1.0: 简单日期+AtomicLong 序列号
 */
public class IdGenerator {

    private static final SnowflakeIdGenerator SNOW = SnowflakeIdGenerator.getInstance();

    // ============== 业务 ID ==============

    /**
     * 双录会话 ID:DR + 14 位十进制雪花 (可读、易对账)
     */
    public static String sessionId() {
        return "DR" + toBase32(SNOW.nextId());
    }

    /**
     * 订单 ID:ORD + 14 位
     */
    public static String orderId() {
        return "ORD" + toBase32(SNOW.nextId());
    }

    /**
     * 质检报告 ID:QCR + 14 位
     */
    public static String reportId() {
        return "QCR" + toBase32(SNOW.nextId());
    }

    /**
     * Saga ID:SAGA- + 16 位十六进制
     */
    public static String sagaId() {
        return "SAGA-" + toHex(SNOW.nextId());
    }

    /**
     * 事件 ID:EVT- + 16 位十六进制
     */
    public static String eventId() {
        return "EVT-" + toHex(SNOW.nextId());
    }

    /**
     * 视频 ID:VID + sessionId
     */
    public static String videoId(String sessionId) {
        return "VID" + sessionId;
    }

    /**
     * 区块链交易哈希:0x + 16 位十六进制雪花
     */
    public static String txHash() {
        return "0x" + toHex(SNOW.nextId());
    }

    // ============== 通用方法 ==============

    /**
     * 原始雪花 ID(long)
     * 用于数据库主键
     */
    public static long nextId() {
        return SNOW.nextId();
    }

    /**
     * 16 位十六进制字符串(纯雪花)
     */
    public static String snowflakeHex() {
        return toHex(SNOW.nextId());
    }

    /**
     * 带前缀的字符串 ID
     */
    public static String prefixed(String prefix) {
        return prefix + toHex(SNOW.nextId());
    }

    // ============== 编码工具 ==============

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final char[] BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** 16 位十六进制(64bit) */
    private static String toHex(long id) {
        char[] buf = new char[16];
        for (int i = 15; i >= 0; i--) {
            buf[i] = HEX[(int) (id & 0xF)];
            id >>>= 4;
        }
        return new String(buf);
    }

    /** 13 位 Base32(去歧义,适合人眼) */
    private static String toBase32(long id) {
        char[] buf = new char[13];
        for (int i = 12; i >= 0; i--) {
            buf[i] = BASE32[(int) (id & 0x1F)];
            id >>>= 5;
        }
        return new String(buf);
    }
}
