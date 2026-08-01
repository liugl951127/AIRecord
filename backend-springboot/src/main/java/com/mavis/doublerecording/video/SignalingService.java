package com.mavis.doublerecording.video;

import com.mavis.doublerecording.common.IdGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebRTC 信令服务
 *
 * 用于线上线下双录的实时音视频通信:
 * 1. 协商 SDP offer/answer
 * 2. 转发 ICE candidate
 * 3. 房间管理(创建/加入/离开)
 * 4. 多端参与者管理(客户/客户经理/AI 质检)
 *
 * 信令流程:
 *   Client A  ──(offer)──>  SignalingServer  ──>  Client B
 *   Client B  ──(answer)─>  SignalingServer  ──>  Client A
 *   A/B  ──(ICE candidate)─> SignalingServer ──> 对方
 *
 * 实际生产应使用专业 SFU/MCU(mediasoup/janus),这里模拟实现
 */
@Slf4j
@Service
public class SignalingService {

    /**
     * 房间存储:roomId -> SignalingRoom
     */
    private final Map<String, SignalingRoom> roomMap = new ConcurrentHashMap<>();

    /**
     * 用户房间映射:userId -> roomId
     */
    private final Map<String, String> userRoomMap = new ConcurrentHashMap<>();

    /**
     * 创建信令房间(双录会话开始时调用)
     */
    public SignalingRoom createRoom(String sessionId, String customerId, String agentId) {
        String roomId = "ROOM-" + IdGenerator.snowflakeHex();
        SignalingRoom room = new SignalingRoom();
        room.setRoomId(roomId);
        room.setSessionId(sessionId);
        room.setCreatedAt(LocalDateTime.now());
        room.setStatus("ACTIVE");

        // 客户加入
        Participant customer = new Participant();
        customer.setUserId(customerId);
        customer.setRole("CUSTOMER");
        customer.setJoinedAt(LocalDateTime.now());
        room.getParticipants().put(customerId, customer);

        // 客户经理加入
        Participant agent = new Participant();
        agent.setUserId(agentId);
        agent.setRole("AGENT");
        agent.setJoinedAt(LocalDateTime.now());
        room.getParticipants().put(agentId, agent);

        roomMap.put(roomId, room);
        userRoomMap.put(customerId, roomId);
        userRoomMap.put(agentId, roomId);

        log.info("[WebRTC] 创建房间: roomId={}, sessionId={}, 参与者={}",
            roomId, sessionId, room.getParticipants().size());
        return room;
    }

    /**
     * 加入房间(支持网点端加入)
     */
    public SignalingRoom joinRoom(String roomId, String userId, String role) {
        SignalingRoom room = roomMap.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在: " + roomId);
        }
        Participant p = new Participant();
        p.setUserId(userId);
        p.setRole(role);
        p.setJoinedAt(LocalDateTime.now());
        room.getParticipants().put(userId, p);
        userRoomMap.put(userId, roomId);
        log.info("[WebRTC] 用户加入: roomId={}, userId={}, role={}", roomId, userId, role);
        return room;
    }

    /**
     * 转发 SDP offer
     */
    public void relayOffer(String roomId, String fromUserId, String toUserId, SdpMessage sdp) {
        SignalingRoom room = roomMap.get(roomId);
        if (room == null) return;
        sdp.setFrom(fromUserId);
        sdp.setTo(toUserId);
        sdp.setTimestamp(LocalDateTime.now());
        room.getMessageQueue().add(sdp);
        log.debug("[WebRTC] 转发 SDP: room={}, {} -> {}", roomId, fromUserId, toUserId);
    }

    /**
     * 转发 ICE candidate
     */
    public void relayIceCandidate(String roomId, String fromUserId, String toUserId, IceCandidate candidate) {
        SignalingRoom room = roomMap.get(roomId);
        if (room == null) return;
        candidate.setFrom(fromUserId);
        candidate.setTo(toUserId);
        room.getMessageQueue().add(candidate);
        log.debug("[WebRTC] 转发 ICE: room={}, {} -> {}", roomId, fromUserId, toUserId);
    }

    /**
     * 离开房间
     */
    public void leaveRoom(String roomId, String userId) {
        SignalingRoom room = roomMap.get(roomId);
        if (room == null) return;
        room.getParticipants().remove(userId);
        userRoomMap.remove(userId);
        log.info("[WebRTC] 用户离开: roomId={}, userId={}", roomId, userId);
        if (room.getParticipants().isEmpty()) {
            room.setStatus("CLOSED");
            log.info("[WebRTC] 房间关闭: roomId={}", roomId);
        }
    }

    /**
     * 获取房间信息
     */
    public SignalingRoom getRoom(String roomId) {
        return roomMap.get(roomId);
    }

    /**
     * 获取用户的房间
     */
    public String getUserRoom(String userId) {
        return userRoomMap.get(userId);
    }

    /**
     * 列出所有活跃房间
     */
    public List<SignalingRoom> listActiveRooms() {
        return roomMap.values().stream()
            .filter(r -> "ACTIVE".equals(r.getStatus()))
            .collect(Collectors.toList());
    }

    // ========== 内部类 ==========

    @Data
    public static class SignalingRoom {
        private String roomId;
        private String sessionId;
        private String status;        // ACTIVE/CLOSED
        private LocalDateTime createdAt;
        private Map<String, Participant> participants = new ConcurrentHashMap<>();
        private List<Object> messageQueue = Collections.synchronizedList(new ArrayList<>());
    }

    @Data
    public static class Participant {
        private String userId;
        private String role;          // CUSTOMER/AGENT/AI/OBSERVER
        private LocalDateTime joinedAt;
        private String connectionState = "CONNECTING";  // CONNECTING/CONNECTED/DISCONNECTED
    }

    @Data
    public static class SdpMessage {
        private String type;          // offer/answer
        private String sdp;
        private String from;
        private String to;
        private LocalDateTime timestamp;
    }

    @Data
    public static class IceCandidate {
        private String candidate;
        private String sdpMid;
        private int sdpMLineIndex;
        private String from;
        private String to;
    }
}
