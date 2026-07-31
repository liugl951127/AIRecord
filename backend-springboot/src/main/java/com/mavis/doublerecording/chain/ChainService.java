package com.mavis.doublerecording.chain;

import com.mavis.doublerecording.common.IdGenerator;
import com.mavis.doublerecording.event.EventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 区块链存证服务(简化实现)
 *
 * 真实生产中应该:
 * 1. 对接 FISCO BCOS / 长安链 / 蚂蚁链 等
 * 2. 业务数据 + 视频哈希 + 签名 + 时间戳打包上链
 * 3. 返回交易哈希 + 区块高度
 * 4. 异步补链机制
 *
 * 这里模拟整个存证流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainService {

    private final EventStore eventStore;

    @Value("${app.double-recording.chain-mock-enabled:true}")
    private boolean mockEnabled;

    /**
     * 提交存证
     *
     * @return 存证回执(交易哈希、区块高度、存证号)
     */
    public Map<String, Object> commit(String sessionId, String videoHash, String signHash, String orderId) {
        log.info("[区块链存证] 提交: sessionId={}, mock={}", sessionId, mockEnabled);

        String txHash = IdGenerator.txHash();
        long blockHeight = System.currentTimeMillis() / 1000;
        String certNo = "CHAIN-" + sessionId + "-" + blockHeight;

        // 真实场景:组装交易 + 签名 + 提交到区块链网络
        // 这里只记录事件
        Map<String, Object> payload = new HashMap<>();
        payload.put("txHash", txHash);
        payload.put("blockHeight", blockHeight);
        payload.put("certNo", certNo);
        payload.put("videoHash", videoHash);
        payload.put("signHash", signHash);
        payload.put("orderId", orderId);
        eventStore.append(sessionId, "CHAIN", certNo, "ChainCommitted", payload);

        Map<String, Object> receipt = new HashMap<>();
        receipt.put("txHash", txHash);
        receipt.put("blockHeight", blockHeight);
        receipt.put("certNo", certNo);
        receipt.put("committedAt", System.currentTimeMillis());
        return receipt;
    }

    /**
     * 查询存证
     */
    public Map<String, Object> query(String certNo) {
        // 真实场景:从区块链查询交易
        Map<String, Object> result = new HashMap<>();
        result.put("certNo", certNo);
        result.put("status", "CONFIRMED");
        return result;
    }
}
