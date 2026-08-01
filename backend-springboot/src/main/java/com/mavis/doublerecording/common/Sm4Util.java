package com.mavis.doublerecording.common;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SM4 国密对称加密工具
 *
 * 用于:
 * - 视频文件加密存储(满足等保 2.0 三级)
 * - 客户敏感信息(身份证/银行卡)加密
 * - 传输层加密
 *
 * SM4 算法:
 * - 中国国家密码管理局发布的分组密码标准
 * - 分组长度 128 bit,密钥长度 128 bit
 * - 国产化,符合金融行业国密要求
 *
 * 注:本实现为标准 SM4-CBC-PKCS5Padding
 *    生产环境建议使用 BouncyCastle 库,这里用 JDK 自带实现
 */
@Slf4j
public class Sm4Util {

    private static final String ALGORITHM = "SM4";
    private static final String TRANSFORMATION = "SM4/CBC/PKCS5Padding";
    private static final int BLOCK_SIZE = 16;

    /**
     * 默认密钥(16 字节 = 128 bit)
     * 生产环境应从配置中心/KMS 加载
     */
    private static final String DEFAULT_KEY = "AIRecord20260801";

    /**
     * 默认 IV(16 字节)
     */
    private static final String DEFAULT_IV = "airecord00000000";

    /**
     * SM4 加密(返回 Base64)
     */
    public static String encrypt(String plaintext) {
        return encrypt(plaintext, DEFAULT_KEY, DEFAULT_IV);
    }

    /**
     * SM4 加密(自定义密钥)
     */
    public static String encrypt(String plaintext, String key, String iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("[SM4] 加密失败: {}", e.getMessage());
            throw new RuntimeException("SM4 加密失败", e);
        }
    }

    /**
     * SM4 解密
     */
    public static String decrypt(String ciphertext) {
        return decrypt(ciphertext, DEFAULT_KEY, DEFAULT_IV);
    }

    /**
     * SM4 解密(自定义密钥)
     */
    public static String decrypt(String ciphertext, String key, String iv) {
        try {
            byte[] encrypted = Base64.getDecoder().decode(ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[SM4] 解密失败: {}", e.getMessage());
            throw new RuntimeException("SM4 解密失败", e);
        }
    }

    /**
     * 加密字节数组(用于视频文件加密)
     */
    public static byte[] encryptBytes(byte[] data, String key, String iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            log.error("[SM4] 字节加密失败: {}", e.getMessage());
            throw new RuntimeException("SM4 字节加密失败", e);
        }
    }

    /**
     * 生成随机 IV(避免固定 IV 带来的安全问题)
     */
    public static String generateIv() {
        byte[] iv = new byte[BLOCK_SIZE];
        new java.security.SecureRandom().nextBytes(iv);
        return Base64.getEncoder().encodeToString(iv);
    }
}
