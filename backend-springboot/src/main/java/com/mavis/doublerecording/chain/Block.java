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
 * 区块 - 区块链核心数据结构
 *
 * 区块结构:
 * ┌──────────────────────────────────────────────┐
 * │ 区块头 (Block Header)                         │
 * │  - index: 区块高度                            │
 * │  - timestamp: 时间戳                          │
 * │  - previousHash: 上一个区块的哈希             │
 * │  - merkleRoot: 交易 Merkle 树根              │
 * │  - difficulty: 挖矿难度                       │
 * │  - nonce: 工作量证明随机数                    │
 * │  - hash: 本区块哈希(由上述字段计算)          │
 * ├──────────────────────────────────────────────┤
 * │ 区块体 (Block Body)                           │
 * │  - transactions: 交易列表                     │
 * │  - transactionCount: 交易数量                 │
 * └──────────────────────────────────────────────┘
 *
 * 哈希算法: SHA-256
 * 共识机制: Proof of Work (PoW)
 */
@Slf4j
@Data
@NoArgsConstructor
public class Block {

    /** 区块高度(从 0 开始) */
    private long index;

    /** 区块创建时间 */
    private LocalDateTime timestamp;

    /** 上一个区块的 SHA-256 哈希(创世区块为 64 个 0) */
    private String previousHash;

    /** 当前区块的 SHA-256 哈希 */
    private String hash;

    /** 交易 Merkle 树根哈希 */
    private String merkleRoot;

    /** 挖矿难度(哈希前导 0 的个数) */
    private int difficulty;

    /** 工作量证明随机数(找到满足难度的值) */
    private long nonce;

    /** 区块中的交易 */
    private List<Transaction> transactions = new ArrayList<>();

    /** 区块签名(用管理员私钥,这里简化用雪花 ID 模拟) */
    private String signer;

    /**
     * 计算本区块的 SHA-256 哈希
     *
     * 哈希内容包括: index + previousHash + timestamp + merkleRoot + difficulty + nonce
     * 任何字段改动都会导致哈希变化
     */
    public String calculateHash() {
        try {
            String data = index
                + previousHash
                + (timestamp == null ? "" : timestamp.toString())
                + merkleRoot
                + difficulty
                + nonce;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 哈希计算失败", e);
        }
    }

    /**
     * 验证区块的哈希是否正确(防篡改)
     */
    public boolean isValid() {
        // 1. 哈希必须已设置
        if (hash == null || hash.isEmpty()) return false;
        // 2. 重新计算的哈希必须等于存储的哈希
        String recalculated = calculateHash();
        if (!recalculated.equals(hash)) {
            log.warn("[Block#{}] 哈希校验失败: expected={}, actual={}", index, hash, recalculated);
            return false;
        }
        // 3. 哈希必须满足难度(前导 0 的个数)
        if (!hash.startsWith("0".repeat(difficulty))) {
            log.warn("[Block#{}] PoW 校验失败: 哈希 {} 不满足难度 {}", index, hash, difficulty);
            return false;
        }
        // 4. Merkle 根必须正确
        if (transactions != null && !transactions.isEmpty()) {
            String recalculatedMerkle = MerkleTree.computeRoot(transactions);
            if (!recalculatedMerkle.equals(merkleRoot)) {
                log.warn("[Block#{}] Merkle 根校验失败", index);
                return false;
            }
        }
        return true;
    }

    /**
     * 简化的 hex 编码
     */
    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * 工厂方法 - 创建新区块
     */
    public static Block newBlock(long index, String previousHash, List<Transaction> transactions, int difficulty) {
        Block block = new Block();
        block.index = index;
        block.previousHash = previousHash;
        block.timestamp = LocalDateTime.now();
        block.difficulty = difficulty;
        block.transactions = transactions == null ? new ArrayList<>() : new ArrayList<>(transactions);
        block.merkleRoot = MerkleTree.computeRoot(block.transactions);
        block.signer = "MINER-" + IdGenerator.snowflakeHex().substring(0, 8);
        return block;
    }

    /**
     * 创世区块
     */
    public static Block genesis() {
        Block genesis = new Block();
        genesis.index = 0;
        genesis.previousHash = "0".repeat(64);
        genesis.timestamp = LocalDateTime.of(2024, 1, 1, 0, 0, 0);
        genesis.difficulty = 4;  // 创世区块难度 4 个 0
        genesis.merkleRoot = "0".repeat(64);
        genesis.nonce = 0;
        genesis.hash = genesis.calculateHash();
        genesis.signer = "GENESIS";
        return genesis;
    }
}
