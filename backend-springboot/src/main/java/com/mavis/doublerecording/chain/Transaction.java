package com.mavis.doublerecording.chain;

import com.mavis.doublerecording.common.IdGenerator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 区块链交易
 *
 * 交易类型:
 * - DOUBLE_RECORDING_EVIDENCE: 双录存证(主用)
 * - VIDEO_HASH_REGISTER: 视频哈希登记
 * - SIGNATURE_CERT: 签名证书
 * - ORDER_COMMIT: 订单提交
 * - QUALITY_REPORT: 质检报告
 *
 * 交易结构:
 * {
 *   "txId": "TX-xxxxx",
 *   "type": "DOUBLE_RECORDING_EVIDENCE",
 *   "timestamp": "2026-08-01T12:00:00",
 *   "from": "0x...",           // 发送方
 *   "to": "0x...",             // 接收方
 *   "payload": {...},          // 业务数据
 *   "nonce": 12345,            // 防重放
 *   "signature": "0x...",      // 签名
 *   "hash": "sha256..."        // 交易哈希
 * }
 */
@Slf4j
@Data
@NoArgsConstructor
public class Transaction {

    private String txId;
    private String type;
    private LocalDateTime timestamp;
    private String from;
    private String to;
    private Map<String, Object> payload = new LinkedHashMap<>();
    private long nonce;
    private String signature;
    private String hash;

    public Transaction(String type, String from, String to, Map<String, Object> payload) {
        this.txId = "TX-" + IdGenerator.snowflakeHex();
        this.type = type;
        this.timestamp = LocalDateTime.now();
        this.from = from;
        this.to = to;
        this.payload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        this.nonce = System.nanoTime();
        this.hash = calculateHash();
        // 简化签名:用 txId + hash 作为"签名"
        this.signature = sign();
    }

    /**
     * 计算交易哈希
     */
    public String calculateHash() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(txId).append('|');
            sb.append(type).append('|');
            sb.append(timestamp).append('|');
            sb.append(from).append('|');
            sb.append(to).append('|');
            sb.append(payload).append('|');
            sb.append(nonce);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("交易哈希计算失败", e);
        }
    }

    /**
     * 简化签名(实际应使用椭圆曲线数字签名)
     */
    private String sign() {
        try {
            // 用 hash + txId 做一次哈希作为"签名"
            String data = hash + ":" + txId;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] sig = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return "0x" + toHex(sig);
        } catch (Exception e) {
            throw new RuntimeException("签名失败", e);
        }
    }

    /**
     * 验证交易签名(简化版)
     */
    public boolean verifySignature() {
        String expectedSig = sign();
        return expectedSig.equals(signature);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    // ========== 工厂方法 - 常见交易类型 ==========

    /**
     * 双录存证交易
     */
    public static Transaction doubleRecordingEvidence(String sessionId, String videoHash, String signHash,
                                                       String orderId, long blockHeight) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        payload.put("videoHash", videoHash);
        payload.put("signHash", signHash);
        payload.put("orderId", orderId);
        payload.put("blockHeight", blockHeight);
        return new Transaction("DOUBLE_RECORDING_EVIDENCE", "0xSYSTEM", "0xCUSTODIAN", payload);
    }

    /**
     * 视频哈希登记
     */
    public static Transaction videoHashRegister(String videoId, String sha256, long fileSize, int durationSec) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("videoId", videoId);
        payload.put("sha256", sha256);
        payload.put("fileSize", fileSize);
        payload.put("durationSec", durationSec);
        return new Transaction("VIDEO_HASH_REGISTER", "0xSYSTEM", "0xCUSTODIAN", payload);
    }

    /**
     * 签名证书交易
     */
    public static Transaction signatureCert(String certNo, String sessionId, String certHash) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("certNo", certNo);
        payload.put("sessionId", sessionId);
        payload.put("certHash", certHash);
        return new Transaction("SIGNATURE_CERT", "0xCA", "0xCUSTODIAN", payload);
    }

    /**
     * 订单提交交易
     */
    public static Transaction orderCommit(String orderId, String customerId, double amount, String productId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("customerId", customerId);
        payload.put("amount", amount);
        payload.put("productId", productId);
        return new Transaction("ORDER_COMMIT", "0xCUSTOMER", "0xMERCHANT", payload);
    }

    /**
     * 质检报告交易
     */
    public static Transaction qualityReport(String reportId, String sessionId, String status, int score) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportId", reportId);
        payload.put("sessionId", sessionId);
        payload.put("status", status);
        payload.put("score", score);
        return new Transaction("QUALITY_REPORT", "0xSYSTEM", "0xAUDITOR", payload);
    }
}
