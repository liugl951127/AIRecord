package com.mavis.doublerecording.chain;

import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 区块链存证服务 - 完整实现
 *
 * 整合:
 * - Blockchain: 链式区块管理 + 挖矿
 * - Block/Transaction/MerkleTree/ProofOfWork
 * - EventStore: 链下事件审计
 *
 * 业务流程:
 * 1. 业务调用 commit() 添加交易到待打包池
 * 2. 调用 mine() 触发挖矿
 * 3. 区块上链,返回 txHash + blockHeight + certNo
 * 4. 业务可查询 certNo 验证存证真实性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainService {

    private final Blockchain blockchain;
    private final EventStore eventStore;

    @Value("${app.chain.auto-mine:true}")
    private boolean autoMine;

    /**
     * 提交存证 - 双录主流程
     *
     * @return 存证回执
     */
    public Map<String, Object> commit(String sessionId, String videoHash, String signHash, String orderId) {
        log.info("[区块链] 提交双录存证: session={}, video={}, sign={}, order={}",
            sessionId, videoHash, signHash, orderId);

        // 1. 创建交易 - 双录存证
        Transaction evidenceTx = Transaction.doubleRecordingEvidence(
            sessionId, videoHash, signHash, orderId, 0);

        // 2. 加入待打包池
        blockchain.addTransaction(evidenceTx);

        // 3. 挖矿(将交易打包上链)
        Block block;
        if (autoMine) {
            block = blockchain.minePendingTransactions("MINER-AUTO");
        } else {
            // 不自动挖矿,业务方手动触发
            log.info("[区块链] autoMine=false,交易已入池但未挖矿: {}", evidenceTx.getTxId());
            return Map.of(
                "txHash", evidenceTx.getHash(),
                "txId", evidenceTx.getTxId(),
                "status", "PENDING",
                "message", "交易已入池,等待挖矿"
            );
        }

        // 4. 构造回执
        String certNo = "CHAIN-" + sessionId + "-" + block.getIndex();
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("txHash", evidenceTx.getHash());
        receipt.put("txId", evidenceTx.getTxId());
        receipt.put("blockHash", block.getHash());
        receipt.put("blockIndex", block.getIndex());
        receipt.put("blockHeight", block.getIndex());
        receipt.put("certNo", certNo);
        receipt.put("videoHash", videoHash);
        receipt.put("signHash", signHash);
        receipt.put("orderId", sessionId);  // 兼容旧字段
        receipt.put("timestamp", block.getTimestamp().toString());
        receipt.put("committedAt", System.currentTimeMillis());

        // 5. 写链下事件
        Map<String, Object> payload = new LinkedHashMap<>(receipt);
        eventStore.append(sessionId, "CHAIN", certNo, "ChainCommitted", payload);

        log.info("[区块链] 存证完成: txHash={}, block#{}, certNo={}",
            evidenceTx.getHash().substring(0, 16) + "...",
            block.getIndex(), certNo);

        return receipt;
    }

    /**
     * 注册视频哈希
     */
    public Map<String, Object> registerVideoHash(String videoId, String sha256, long fileSize, int durationSec) {
        Transaction tx = Transaction.videoHashRegister(videoId, sha256, fileSize, durationSec);
        blockchain.addTransaction(tx);
        Block block = autoMine ? blockchain.minePendingTransactions("MINER-VIDEO") : null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("txId", tx.getTxId());
        result.put("txHash", tx.getHash());
        if (block != null) {
            result.put("blockIndex", block.getIndex());
            result.put("blockHash", block.getHash());
        }
        return result;
    }

    /**
     * 签名证书存证
     */
    public Map<String, Object> certifySignature(String certNo, String sessionId, String certHash) {
        Transaction tx = Transaction.signatureCert(certNo, sessionId, certHash);
        blockchain.addTransaction(tx);
        Block block = autoMine ? blockchain.minePendingTransactions("MINER-CERT") : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("txId", tx.getTxId());
        result.put("txHash", tx.getHash());
        if (block != null) {
            result.put("blockIndex", block.getIndex());
            result.put("blockHash", block.getHash());
        }
        return result;
    }

    /**
     * 订单提交存证
     */
    public Map<String, Object> commitOrder(String orderId, String customerId, double amount, String productId) {
        Transaction tx = Transaction.orderCommit(orderId, customerId, amount, productId);
        blockchain.addTransaction(tx);
        Block block = autoMine ? blockchain.minePendingTransactions("MINER-ORDER") : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("txId", tx.getTxId());
        result.put("txHash", tx.getHash());
        if (block != null) {
            result.put("blockIndex", block.getIndex());
            result.put("blockHash", block.getHash());
        }
        return result;
    }

    /**
     * 质检报告存证
     */
    public Map<String, Object> reportQuality(String reportId, String sessionId, String status, int score) {
        Transaction tx = Transaction.qualityReport(reportId, sessionId, status, score);
        blockchain.addTransaction(tx);
        Block block = autoMine ? blockchain.minePendingTransactions("MINER-QUALITY") : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("txId", tx.getTxId());
        result.put("txHash", tx.getHash());
        if (block != null) {
            result.put("blockIndex", block.getIndex());
            result.put("blockHash", block.getHash());
        }
        return result;
    }

    /**
     * 手动触发挖矿(把待打包池打包)
     */
    public Map<String, Object> mine(String minerAddress) {
        Block block = blockchain.minePendingTransactions(minerAddress);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("blockIndex", block.getIndex());
        result.put("blockHash", block.getHash());
        result.put("txCount", block.getTransactions().size());
        result.put("merkleRoot", block.getMerkleRoot());
        result.put("nonce", block.getNonce());
        result.put("difficulty", block.getDifficulty());
        result.put("miner", block.getSigner());
        result.put("timestamp", block.getTimestamp().toString());
        return result;
    }

    /**
     * 查询存证(从链上)
     */
    public Map<String, Object> query(String certNo) {
        // 解析 certNo 找区块
        Blockchain.ValidationResult vr = blockchain.validateChain();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("certNo", certNo);
        result.put("chainValid", vr.isValid());
        result.put("chainLength", blockchain.getChainLength());

        // 查找匹配的区块
        for (Block block : blockchain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                if (certNo.contains(tx.getHash().substring(0, 8)) ||
                    tx.getPayload().toString().contains(certNo)) {
                    result.put("found", true);
                    result.put("blockIndex", block.getIndex());
                    result.put("blockHash", block.getHash());
                    result.put("txId", tx.getTxId());
                    result.put("txHash", tx.getHash());
                    result.put("type", tx.getType());
                    result.put("payload", tx.getPayload());
                    result.put("timestamp", tx.getTimestamp().toString());
                    result.put("status", "CONFIRMED");
                    return result;
                }
            }
        }
        result.put("found", false);
        result.put("status", "NOT_FOUND");
        return result;
    }

    /**
     * 链统计
     */
    public Map<String, Object> getStatistics() {
        return blockchain.getStatistics();
    }

    /**
     * 验证链
     */
    public Blockchain.ValidationResult validateChain() {
        return blockchain.validateChain();
    }

    /**
     * 查询区块
     */
    public Block getBlock(long index) {
        return blockchain.getBlock(index);
    }

    /**
     * 获取所有区块(用于浏览)
     */
    public List<Block> getAllBlocks() {
        return blockchain.getChain();
    }

    /**
     * 按 txId 查交易
     */
    public Transaction findTransaction(String txId) {
        return blockchain.findTransaction(txId);
    }
}
