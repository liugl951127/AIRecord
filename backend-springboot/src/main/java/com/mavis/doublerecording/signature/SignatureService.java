package com.mavis.doublerecording.signature;

import com.mavis.doublerecording.common.BizException;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 电子签章服务(简化实现)
 *
 * 真实生产中应该:
 * 1. 对接 CFCA / 沃通 等 CA 机构
 * 2. 数字证书 + 时间戳
 * 3. 符合《电子签名法》
 *
 * 这里模拟签名,返回证书号
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignatureService {

    private final EventStore eventStore;

    /**
     * 客户签字(模拟)
     *
     * @param sessionId   会话ID
     * @param signImageData 签字图片 base64(模拟)
     * @return 签字结果(含证书号、签名哈希、时间戳)
     */
    public Map<String, Object> sign(String sessionId, String signImageData) {
        if (signImageData == null || signImageData.isEmpty()) {
            // 模拟默认签名
            signImageData = "MOCK_SIGN_" + sessionId + "_" + System.currentTimeMillis();
        }

        // 计算签字图片哈希
        String signHash = sha256(signImageData);

        // 模拟 CA 证书号
        String certNo = "CFCA-" + sessionId.substring(0, Math.min(10, sessionId.length())) + "-" + System.currentTimeMillis();

        // 时间戳
        String timestamp = LocalDateTime.now().toString();

        log.info("[电子签章] 客户签字: sessionId={}, certNo={}", sessionId, certNo);

        Map<String, Object> payload = new HashMap<>();
        payload.put("signHash", signHash);
        payload.put("certNo", certNo);
        payload.put("timestamp", timestamp);
        eventStore.append(sessionId, "SIGN", certNo, "CustomerSigned", payload);

        Map<String, Object> result = new HashMap<>();
        result.put("signHash", signHash);
        result.put("certNo", certNo);
        result.put("timestamp", timestamp);
        result.put("signedAt", LocalDateTime.now());
        return result;
    }

    /**
     * 验证签名
     */
    public boolean verify(String sessionId, String signHash) {
        // 真实场景:对接 CA 验证接口
        return signHash != null && !signHash.isEmpty();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new BizException("SHA256 计算失败", e);
        }
    }
}
