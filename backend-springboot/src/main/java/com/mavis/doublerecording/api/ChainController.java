package com.mavis.doublerecording.api;

import com.mavis.doublerecording.chain.Block;
import com.mavis.doublerecording.chain.Blockchain;
import com.mavis.doublerecording.chain.ChainService;
import com.mavis.doublerecording.chain.Transaction;
import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.video.RecordingComplianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 区块链 + 录制合规 API
 */
@RestController
@RequestMapping("/api/chain")
@RequiredArgsConstructor
public class ChainController {

    private final ChainService chainService;
    private final Blockchain blockchain;
    private final RecordingComplianceService recordingCompliance;

    // ========== 区块链查询 ==========

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        return Result.ok(chainService.getStatistics());
    }

    @GetMapping("/list")
    public Result<List<Block>> listAll() {
        return Result.ok(blockchain.getChain());
    }

    @PostMapping("/add-transaction")
    public Result<Map<String, Object>> addTransaction(@RequestBody Map<String, Object> req) {
        String type = (String) req.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) req.get("payload");
        ChainService.TransactionType txType;
        try {
            txType = ChainService.TransactionType.valueOf(type);
        } catch (Exception e) {
            return Result.fail("交易类型无效: " + type);
        }
        return Result.ok(chainService.addTransaction(txType, payload == null ? Map.of() : payload));
    }

    @GetMapping("/blocks")
    public Result<List<Block>> getAllBlocks() {
        return Result.ok(chainService.getAllBlocks());
    }

    @GetMapping("/block/{index}")
    public Result<Block> getBlock(@PathVariable long index) {
        Block block = chainService.getBlock(index);
        if (block == null) return Result.fail(404, "区块不存在: " + index);
        return Result.ok(block);
    }

    @GetMapping("/tx/{txId}")
    public Result<Transaction> getTransaction(@PathVariable String txId) {
        Transaction tx = chainService.findTransaction(txId);
        if (tx == null) return Result.fail(404, "交易不存在: " + txId);
        return Result.ok(tx);
    }

    @GetMapping("/validate")
    public Result<Blockchain.ValidationResult> validate() {
        return Result.ok(chainService.validateChain());
    }

    @GetMapping("/pending")
    public Result<List<Transaction>> getPending() {
        return Result.ok(blockchain.getPendingPool());
    }

    // ========== 区块链操作 ==========

    @PostMapping("/mine")
    public Result<Map<String, Object>> mine(@RequestBody Map<String, String> req) {
        String miner = req.getOrDefault("miner", "MINER-MANUAL");
        return Result.ok(chainService.mine(miner));
    }

    @PostMapping("/commit")
    public Result<Map<String, Object>> commit(@RequestBody Map<String, String> req) {
        return Result.ok(chainService.commit(
            req.get("sessionId"),
            req.get("videoHash"),
            req.get("signHash"),
            req.get("orderId")
        ));
    }

    @PostMapping("/commit-order")
    public Result<Map<String, Object>> commitOrder(@RequestBody Map<String, Object> req) {
        return Result.ok(chainService.commitOrder(
            (String) req.get("orderId"),
            (String) req.get("customerId"),
            ((Number) req.get("amount")).doubleValue(),
            (String) req.get("productId")
        ));
    }

    @PostMapping("/register-video")
    public Result<Map<String, Object>> registerVideo(@RequestBody Map<String, Object> req) {
        return Result.ok(chainService.registerVideoHash(
            (String) req.get("videoId"),
            (String) req.get("sha256"),
            ((Number) req.get("fileSize")).longValue(),
            ((Number) req.get("durationSec")).intValue()
        ));
    }

    @GetMapping("/query/{certNo}")
    public Result<Map<String, Object>> query(@PathVariable String certNo) {
        return Result.ok(chainService.query(certNo));
    }

    // ========== 录制合规 API ==========

    @PostMapping("/recording/start")
    public Result<RecordingComplianceService.RecordingHandle> startRecording(@RequestBody Map<String, Object> req) {
        return Result.ok(recordingCompliance.startRecording(
            (String) req.get("sessionId"),
            (Integer) req.get("currentNodeSeq"),
            (Boolean) req.getOrDefault("consentRecorded", false),
            (Boolean) req.getOrDefault("customerAgreed", false),
            (String) req.get("agentId")
        ));
    }

    @PostMapping("/recording/switch-node")
    public Result<Void> switchNode(@RequestBody Map<String, Object> req) {
        recordingCompliance.switchNode(
            (String) req.get("sessionId"),
            (Integer) req.get("newNodeSeq"));
        return Result.ok();
    }

    @PostMapping("/recording/pause")
    public Result<Void> pause(@RequestBody Map<String, Object> req) {
        recordingCompliance.pause(
            (String) req.get("sessionId"),
            (String) req.getOrDefault("reason", "未说明"));
        return Result.ok();
    }

    @PostMapping("/recording/resume")
    public Result<Void> resume(@RequestBody Map<String, String> req) {
        recordingCompliance.resume(req.get("sessionId"));
        return Result.ok();
    }

    @PostMapping("/recording/stop")
    public Result<Map<String, Object>> stopRecording(@RequestBody Map<String, Object> req) {
        return Result.ok(recordingCompliance.stopRecording(
            (String) req.get("sessionId"),
            (Integer) req.get("currentNodeSeq")
        ));
    }

    @GetMapping("/recording/state/{sessionId}")
    public Result<RecordingComplianceService.RecordingState> getRecordingState(@PathVariable String sessionId) {
        return Result.ok(recordingCompliance.getState(sessionId));
    }

    @PostMapping("/recording/mask")
    public Result<Map<String, String>> maskSensitiveInfo(@RequestBody Map<String, String> req) {
        String original = req.get("text");
        String masked = recordingCompliance.maskSensitiveInfo(original);
        return Result.ok(Map.of("original", original, "masked", masked));
    }
}
