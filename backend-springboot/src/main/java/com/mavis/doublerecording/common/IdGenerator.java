package com.mavis.doublerecording.common;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ID 生成器(简化版,生产请用雪花算法)
 */
public class IdGenerator {

    private static final AtomicLong SESSION_SEQ = new AtomicLong(1);
    private static final AtomicLong ORDER_SEQ = new AtomicLong(1);
    private static final AtomicLong REPORT_SEQ = new AtomicLong(1);
    private static final AtomicLong SAGA_SEQ = new AtomicLong(1);
    private static final AtomicLong EVENT_SEQ = new AtomicLong(1);

    private static final SimpleDateFormat FMT = new SimpleDateFormat("yyyyMMddHHmmss");

    public static String sessionId() {
        return "DR" + FMT.format(new Date()) + String.format("%06d", SESSION_SEQ.getAndIncrement());
    }

    public static String orderId() {
        return "ORD" + FMT.format(new Date()) + String.format("%06d", ORDER_SEQ.getAndIncrement());
    }

    public static String reportId() {
        return "QCR" + FMT.format(new Date()) + String.format("%05d", REPORT_SEQ.getAndIncrement());
    }

    public static String sagaId() {
        return "SAGA" + FMT.format(new Date()) + String.format("%05d", SAGA_SEQ.getAndIncrement());
    }

    public static String eventId() {
        return "EVT" + FMT.format(new Date()) + String.format("%08d", EVENT_SEQ.getAndIncrement());
    }

    public static String videoId(String sessionId) {
        return "VID" + sessionId;
    }

    public static String txHash() {
        return "0x" + Long.toHexString(System.nanoTime()) + Long.toHexString(System.currentTimeMillis());
    }
}
