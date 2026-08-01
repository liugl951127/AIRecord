package com.mavis.doublerecording.chain;

import lombok.extern.slf4j.Slf4j;

/**
 * 工作量证明 (Proof of Work, PoW)
 *
 * 比特币/早期区块链使用的共识算法:
 * - 挖矿:找到一个 nonce,使得 SHA-256(index + prevHash + merkleRoot + nonce)
 *   的前 N 位为 0(N = difficulty)
 * - 验证:任何节点都可以瞬间验证哈希是否满足难度
 *
 * 难度动态调整(简化版):
 * - 如果出块太快(实际生产 10 分钟),难度 +1
 * - 如果出块太慢,难度 -1
 * - 这里固定难度 4(测试用)
 *
 * 替代方案(已大规模生产):
 * - PoS (Proof of Stake) - 以太坊 2.0
 * - DPoS (Delegated PoS) - EOS
 * - PBFT (Practical Byzantine Fault Tolerance) - 联盟链
 *
 * 联盟链场景(FISCO BCOS/长安链):
 * - 用 PBFT/Raft 等许可制共识
 * - 不需要 PoW
 * - 出块更快(秒级)
 */
@Slf4j
public class ProofOfWork {

    /**
     * 挖矿: 寻找满足难度的 nonce
     *
     * @param block 待挖区块
     * @return 挖矿耗时(毫秒)
     */
    public static long mine(Block block) {
        long startTime = System.currentTimeMillis();
        String target = "0".repeat(block.getDifficulty());

        log.info("[PoW] 开始挖矿: block={}, difficulty={}", block.getIndex(), block.getDifficulty());

        // 从 0 开始尝试 nonce
        long nonce = 0;
        while (true) {
            block.setNonce(nonce);
            String hash = block.calculateHash();
            if (hash.startsWith(target)) {
                block.setHash(hash);
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("[PoW] 挖矿成功: block={}, nonce={}, hash={}, 耗时={}ms",
                    block.getIndex(), nonce, hash, elapsed);
                return elapsed;
            }
            nonce++;
            // 防止无限循环(测试用,实际挖矿会持续)
            if (nonce > 10_000_000) {
                log.warn("[PoW] 挖矿超时,降低难度");
                block.setDifficulty(Math.max(1, block.getDifficulty() - 1));
                nonce = 0;
            }
        }
    }

    /**
     * 快速验证:判断区块的 nonce 是否满足难度(无需重新挖矿)
     */
    public static boolean verify(Block block) {
        // 1. 重新计算哈希
        String hash = block.calculateHash();
        // 2. 检查哈希与存储一致
        if (!hash.equals(block.getHash())) {
            return false;
        }
        // 3. 检查前导 0
        return hash.startsWith("0".repeat(block.getDifficulty()));
    }
}
