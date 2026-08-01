package com.mavis.doublerecording.chain;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Merkle 树 - 区块链中用于高效验证交易完整性的数据结构
 *
 * 工作原理:
 * - 将一组交易哈希两两配对
 * - 每对哈希拼接后再哈希,形成上一层节点
 * - 递归直到只剩一个根哈希(Merkle Root)
 *
 * 优势:
 * - 验证某笔交易是否在区块中: O(log n) 而不是 O(n)
 * - 任何交易被篡改都会导致 Merkle Root 改变
 *
 * 示例(4 笔交易):
 *        Root
 *       /    \
 *    H(AB)   H(CD)
 *    / \      / \
 *   A   B    C   D
 *
 * - A,B,C,D 是交易哈希
 * - H(AB) = SHA256(A+B), H(CD) = SHA256(C+D)
 * - Root = SHA256(H(AB)+H(CD))
 */
@Slf4j
public class MerkleTree {

    /**
     * 计算交易的 Merkle 根
     *
     * @param transactions 交易列表
     * @return Merkle 根哈希(64 字符 SHA-256)
     */
    public static String computeRoot(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return "0".repeat(64);
        }
        // 1. 提取所有交易哈希
        List<String> hashes = new ArrayList<>();
        for (Transaction tx : transactions) {
            hashes.add(tx.getHash() == null ? tx.calculateHash() : tx.getHash());
        }
        // 2. 逐层向上哈希
        return computeRootFromHashes(hashes);
    }

    /**
     * 从哈希列表计算 Merkle 根
     */
    private static String computeRootFromHashes(List<String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return "0".repeat(64);
        }
        if (hashes.size() == 1) {
            return hashes.get(0);
        }
        List<String> nextLevel = new ArrayList<>();
        // 两两配对哈希
        for (int i = 0; i < hashes.size(); i += 2) {
            String left = hashes.get(i);
            String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;  // 奇数个时,复制最后一个
            nextLevel.add(sha256(left + right));
        }
        return computeRootFromHashes(nextLevel);
    }

    /**
     * 验证某笔交易是否在 Merkle 树中
     *
     * @param targetHash 目标交易哈希
     * @param proof 验证路径(其他节点的哈希)
     * @param root Merkle 根
     * @return true=验证通过
     */
    public static boolean verifyProof(String targetHash, List<String> proof, String root) {
        String current = targetHash;
        for (String sibling : proof) {
            // 简化:总是 left + right 拼接(实际需根据位置判断)
            current = sha256(current + sibling);
        }
        return current.equals(root);
    }

    /**
     * SHA-256 哈希
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 计算失败", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }
}
