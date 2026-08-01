package com.mavis.doublerecording.api;

import com.mavis.doublerecording.common.Result;
import com.mavis.doublerecording.video.SignalingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * WebRTC 视频流 API(双录开画录制)
 *
 * 浏览器端通过 WebRTC API:
 * 1. getUserMedia() 获取本地摄像头+麦克风
 * 2. RTCPeerConnection 与对端建立 P2P
 * 3. 通过信令服务交换 SDP/ICE
 *
 * 后端职责:
 * - 信令转发(createRoom/join/leave)
 * - 房间管理(状态查询)
 * - 不直接处理媒体流(P2P 传输)
 *
 * 实际部署可选:
 * - SFU (mediasoup/janus) - 大规模会议
 * - TURN 服务器 - NAT 穿透
 * - 录制服务(后端录制 P2P 流)
 */
@RestController
@RequestMapping("/api/webrtc")
@RequiredArgsConstructor
public class WebRTCStreamController {

    private final SignalingService signalingService;

    /**
     * 创建录制房间(双录开画)
     */
    @PostMapping("/room/create")
    public Result<SignalingService.SignalingRoom> createRoom(@RequestBody Map<String, String> req) {
        return Result.ok(signalingService.createRoom(
            req.get("sessionId"),
            req.get("customerId"),
            req.get("agentId")
        ));
    }

    /**
     * 加入房间
     */
    @PostMapping("/room/{roomId}/join")
    public Result<SignalingService.SignalingRoom> joinRoom(
            @PathVariable String roomId,
            @RequestBody Map<String, String> req) {
        return Result.ok(signalingService.joinRoom(
            roomId, req.get("userId"), req.get("role")
        ));
    }

    /**
     * 离开房间
     */
    @PostMapping("/room/{roomId}/leave")
    public Result<Void> leaveRoom(@PathVariable String roomId, @RequestParam String userId) {
        signalingService.leaveRoom(roomId, userId);
        return Result.ok();
    }

    /**
     * 房间信息
     */
    @GetMapping("/room/{roomId}")
    public Result<SignalingService.SignalingRoom> getRoom(@PathVariable String roomId) {
        return Result.ok(signalingService.getRoom(roomId));
    }

    /**
     * 活跃房间列表
     */
    @GetMapping("/rooms/active")
    public Result<List<SignalingService.SignalingRoom>> activeRooms() {
        return Result.ok(signalingService.listActiveRooms());
    }

    /**
     * 用户所在房间
     */
    @GetMapping("/user/{userId}/room")
    public Result<String> getUserRoom(@PathVariable String userId) {
        return Result.ok(signalingService.getUserRoom(userId));
    }

    /**
     * 转发 SDP offer
     */
    @PostMapping("/sdp/offer")
    public Result<Void> relayOffer(@RequestBody Map<String, Object> req) {
        SignalingService.SdpMessage sdp = new SignalingService.SdpMessage();
        sdp.setType((String) req.get("type"));
        sdp.setSdp((String) req.get("sdp"));
        signalingService.relayOffer(
            (String) req.get("roomId"),
            (String) req.get("fromUserId"),
            (String) req.get("toUserId"),
            sdp
        );
        return Result.ok();
    }

    /**
     * 转发 SDP answer
     */
    @PostMapping("/sdp/answer")
    public Result<Void> relayAnswer(@RequestBody Map<String, Object> req) {
        SignalingService.SdpMessage sdp = new SignalingService.SdpMessage();
        sdp.setType("answer");
        sdp.setSdp((String) req.get("sdp"));
        signalingService.relayOffer(
            (String) req.get("roomId"),
            (String) req.get("fromUserId"),
            (String) req.get("toUserId"),
            sdp
        );
        return Result.ok();
    }

    /**
     * 转发 ICE candidate
     */
    @PostMapping("/ice")
    public Result<Void> relayIce(@RequestBody Map<String, Object> req) {
        SignalingService.IceCandidate ice = new SignalingService.IceCandidate();
        ice.setCandidate((String) req.get("candidate"));
        ice.setSdpMid((String) req.get("sdpMid"));
        ice.setSdpMLineIndex((Integer) req.get("sdpMLineIndex"));
        signalingService.relayIceCandidate(
            (String) req.get("roomId"),
            (String) req.get("fromUserId"),
            (String) req.get("toUserId"),
            ice
        );
        return Result.ok();
    }
}
