package com.mavis.doublerecording.chain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mavis.doublerecording.common.IdGenerator;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 区块链 - 完整实现
 *
 * 功能:
 * 1. 维护链式区块(创世区块 → 最新区块)
 * 2. 接收交易并打包到新区块
 * 3. 通过 PoW 共识完成挖矿
 * 4. 验证整条链的完整性
 * 5. 查询区块/交易
 * 6. 持久化(内存 + 可选文件)
 *
 * 链式结构:
 *   Genesis → Block#1 → Block#2 → ... → Block#N
 *     │         │          │                │
 *     │         │          │                └─ prev = Block#N-1
 *     │         │          └─ prev = Block#N-1.hash
 *     │         └─ prev = Block#N-2.hash
 *     └─ prev = "0...0"
 */
@Slf4j
@Component
public class Blockchain {

    private final List<Block> chain = new ArrayList<>();
    private final List<Transaction> pendingPool = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    @Value("${app.chain.difficulty:3}")
    private int difficulty;

    @Value("${app.chain.storage-path:./chain-data}")
    private String storagePath;

    @Value("${app.chain.persistence-enabled:false}")
    private boolean persistenceEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化:加载已有链 或 创建创世区块
     */
    @PostConstruct
    public void init() {
        if (persistenceEnabled) {
            try {
                loadFromDisk();
                if (!chain.isEmpty()) {
                    log.info("[Blockchain] 从磁盘加载: {} 个区块", chain.size());
                    return;
                }
            } catch (Exception e) {
                log.warn("[Blockchain] 从磁盘加载失败: {}", e.getMessage());
            }
        }
        // 创建创世区块
        Block genesis = Block.genesis();
        chain.add(genesis);
        log.info("[Blockchain] 创世区块已创建: hash={}", genesis.getHash().substring(0, 16) + "...");
    }

    /**
     * 添加交易到待打包池
     */
    public void addTransaction(Transaction tx) {
        lock.lock();
        try {
            // 验证交易
            if (!tx.verifySignature()) {
                throw new RuntimeException("交易签名验证失败: " + tx.getTxId());
            }
            // 防重放:同一 txId 不重复添加
            if (pendingPool.stream().anyMatch(t -> t.getTxId().equals(tx.getTxId()))) {
                log.warn("[Blockchain] 交易重复,忽略: {}", tx.getTxId());
                return;
            }
            pendingPool.add(tx);
            log.debug("[Blockchain] 交易已加入待打包池: {} ({})", tx.getTxId(), tx.getType());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 挖矿:打包待打包池 + 工作量证明
     */
    public Block minePendingTransactions(String minerAddress) {
        lock.lock();
        try {
            if (pendingPool.isEmpty()) {
                throw new RuntimeException("待打包池为空,无需挖矿");
            }
            Block lastBlock = chain.get(chain.size() - 1);
            Block newBlock = Block.newBlock(
                lastBlock.getIndex() + 1,
                lastBlock.getHash(),
                new ArrayList<>(pendingPool),
                difficulty
            );
            log.info("[Blockchain] 开始挖矿: index={}, txs={}", newBlock.getIndex(), pendingPool.size());

            // PoW 挖矿
            long elapsed = ProofOfWork.mine(newBlock);
            newBlock.setSigner(minerAddress);

            // 校验挖矿结果
            if (!newBlock.isValid()) {
                throw new RuntimeException("挖矿结果校验失败");
            }

            // 加入链
            chain.add(newBlock);
            pendingPool.clear();

            log.info("[Blockchain] 区块 #{} 已上链: hash={}, 交易={}, 耗时={}ms",
                newBlock.getIndex(),
                newBlock.getHash().substring(0, 16) + "...",
                newBlock.getTransactions().size(),
                elapsed);

            // 持久化
            if (persistenceEnabled) {
                try {
                    saveToDisk();
                } catch (Exception e) {
                    log.warn("[Blockchain] 持久化失败: {}", e.getMessage());
                }
            }
            return newBlock;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 验证整条链
     */
    public ValidationResult validateChain() {
        lock.lock();
        try {
            List<String> errors = new ArrayList<>();
            for (int i = 1; i < chain.size(); i++) {
                Block current = chain.get(i);
                Block previous = chain.get(i - 1);

                // 1. 当前区块哈希是否正确
                if (!current.isValid()) {
                    errors.add(String.format("Block#%d 哈希/Merkle 校验失败", current.getIndex()));
                }
                // 2. previousHash 是否匹配上一个区块
                if (!current.getPreviousHash().equals(previous.getHash())) {
                    errors.add(String.format("Block#%d previousHash 不匹配 Block#%d",
                        current.getIndex(), previous.getIndex()));
                }
            }
            return new ValidationResult(errors.isEmpty(), errors, chain.size());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 按索引查询区块
     */
    public Block getBlock(long index) {
        if (index < 0 || index >= chain.size()) return null;
        return chain.get((int) index);
    }

    /**
     * 按哈希查询区块
     */
    public Block getBlockByHash(String hash) {
        return chain.stream()
            .filter(b -> b.getHash().equals(hash))
            .findFirst()
            .orElse(null);
    }

    /**
     * 按 txId 查找交易
     */
    public Transaction findTransaction(String txId) {
        for (Block block : chain) {
            for (Transaction tx : block.getTransactions()) {
                if (tx.getTxId().equals(txId)) {
                    return tx;
                }
            }
        }
        return null;
    }

    /**
     * 获取整条链
     */
    public List<Block> getChain() {
        return Collections.unmodifiableList(chain);
    }

    /**
     * 获取最新区块
     */
    public Block getLatestBlock() {
        if (chain.isEmpty()) return null;
        return chain.get(chain.size() - 1);
    }

    /**
     * 获取链长度
     */
    public int getChainLength() {
        return chain.size();
    }

    /**
     * 获取待打包池
     */
    public List<Transaction> getPendingPool() {
        return Collections.unmodifiableList(pendingPool);
    }

    /**
     * 链统计
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("chainLength", chain.size());
        stats.put("pendingTxs", pendingPool.size());
        stats.put("latestBlockHash", getLatestBlock() == null ? "" : getLatestBlock().getHash());
        stats.put("latestBlockIndex", chain.size() - 1);
        stats.put("difficulty", difficulty);

        // 交易类型统计
        Map<String, Long> txTypeCount = new LinkedHashMap<>();
        for (Block block : chain) {
            for (Transaction tx : block.getTransactions()) {
                txTypeCount.merge(tx.getType(), 1L, Long::sum);
            }
        }
        stats.put("txTypeDistribution", txTypeCount);

        ValidationResult vr = validateChain();
        stats.put("chainValid", vr.isValid());
        stats.put("chainErrors", vr.getErrors());
        return stats;
    }

    // ========== 持久化 ==========

    private void saveToDisk() throws Exception {
        File dir = new File(storagePath);
        if (!dir.exists()) dir.mkdirs();
        File f = new File(storagePath + "/chain.json");
        objectMapper.writeValue(f, chain);
        log.debug("[Blockchain] 已保存 {} 个区块到 {}", chain.size(), f.getAbsolutePath());
    }

    private void loadFromDisk() throws Exception {
        File f = new File(storagePath + "/chain.json");
        if (!f.exists()) return;
        byte[] data = Files.readAllBytes(Paths.get(f.toURI()));
        List<Block> loaded = objectMapper.readValue(data, new TypeReference<List<Block>>() {});
        chain.clear();
        chain.addAll(loaded);
    }

    /**
     * 链验证结果
     */
    @Data
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private int chainLength;

        public ValidationResult(boolean valid, List<String> errors, int chainLength) {
            this.valid = valid;
            this.errors = errors;
            this.chainLength = chainLength;
        }
    }
}
