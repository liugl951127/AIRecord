#!/usr/bin/env python3
"""完整双录流程演示 - 客户端脚本"""
import requests
import json

BASE = "http://localhost:8080"

# 每个节点需要包含的 P0 关键词(覆盖话术模板里定义的所有 P0 词)
NODE_KEYWORDS = {
    1: "录音录像 依据 客户经理 工号",
    2: "身份证 人脸识别",
    3: "风险评估 R3 匹配 平衡型",
    4: "非保本 浮动收益 业绩比较基准 投资范围",
    5: "不代表 不预示 非保本 浮动收益 全部本金",
    6: "本金损失 最不利 全部本金 市场风险 流动性风险 信用风险",
    7: "管理费 托管费 申购费 赎回费",
    8: "封闭期 不可赎回",
    9: "清楚 自愿 同意 是",
    10: "签名 无法撤销 撤销",
    11: "保存 录音录像",
}

def api(url, method="GET", body=None):
    r = requests.request(method, BASE + url, json=body)
    j = r.json()
    if j.get("code") != 200:
        raise Exception(f"API错误 [{j.get('code')}]: {j.get('message')} | URL: {url}")
    return j.get("data")

print("=" * 60)
print("双录系统完整流程演示")
print("=" * 60)

# 1. 创建会话
print("\n[1] 创建会话...")
session = api("/api/session/create", "POST", {
    "customerId": "CUST_2026_0001",
    "customerName": "张三",
    "productId": "PROD_FIN_R3_001",
    "productName": "稳赢系列-平衡型理财",
    "channel": "APP",
    "orderAmount": 100000
})
session_id = session["sessionId"]
print(f"  V 会话创建: {session_id}")
print(f"  - 话术模板: {session['scriptTemplateId']} v{session['scriptVersion']}")
print(f"  - 风险等级: {session['riskLevel']}")

# 2. 走完 11 个话术节点
print("\n[2] 走完 11 个话术节点(每节点含质检)...")
for i in range(1, 12):
    # 获取当前节点
    node = api(f"/api/session/{session_id}/current-node")
    content = node["renderedContent"]

    # 补充合规关键词
    kw = NODE_KEYWORDS.get(i, "")
    full_content = f"{content} {kw}"

    # 提交
    result = api(f"/api/session/{session_id}/submit-node", "POST", {
        "nodeSeq": node["node"]["nodeSeq"],
        "agentContent": full_content,
        "customerResponse": "是,我清楚了,自愿购买",
        "durationSec": 45
    })
    status = result.get("status", "?")
    blocked = result.get("blockedCount", 0)
    print(f"  N{node['node']['nodeSeq']:02d} [{node['node']['nodeTitle']}]: {status} (阻断:{blocked})")
    if status != "PASS":
        print(f"    消息: {result.get('message', '')}")
        break

# 3. 启动视频录制
print("\n[3] 启动视频录制...")
handle = api(f"/api/session/{session_id}/video/start", "POST", {})
print(f"  V 录制句柄: {handle}")

# 4. 客户签字
print("\n[4] 客户签字...")
sign = api(f"/api/session/{session_id}/sign", "POST", {})
print(f"  V 签字证书: {sign['certNo']}")
print(f"  - 签名哈希: {sign['signHash'][:32]}...")

# 5. 完成录制 + 触发 Saga
print("\n[5] 完成录制 + 触发 Saga 分布式事务...")
saga = api(f"/api/session/{session_id}/video/complete", "POST", {"duration": 300})
print(f"  V Saga 完成")
print(f"  - 视频哈希: {saga.get('sha256', '?')[:32]}...")
print(f"  - 订单ID: {saga.get('orderId')}")
print(f"  - 区块链存证: {saga.get('certNo')}")
print(f"  - 区块高度: {saga.get('blockHeight')}")

# 6. 查询最终结果
print("\n[6] 查询最终结果...")
final = api(f"/api/session/{session_id}")
print(f"  状态: {final['currentState']} / {final['finalStatus']}")
print(f"  订单: {final['orderId']} = {final['orderAmount']}元")
print(f"  视频: {final['videoFileId']}")
print(f"  链: {final['chainCertNo']}")
print(f"  完成时间: {final['completedAt']}")

# 7. 质检报告
print("\n[7] 质检报告...")
report = api(f"/api/quality/report/{session_id}")
print(f"  报告ID: {report['reportId']}")
print(f"  总节点: {report['totalNodes']}")
print(f"  通过: {report['passedNodes']}")
print(f"  失败: {report['failedNodes']}")
print(f"  最终判定: {report['finalStatus']}")

# 8. 事件流
print("\n[8] 事件流(部分)...")
events = api(f"/api/event/{session_id}")
print(f"  共记录 {len(events)} 个事件")
for e in events[:5]:
    print(f"  - [{e['eventType']}] seq={e['sequenceNo']}")
print(f"  ...")
for e in events[-3:]:
    print(f"  - [{e['eventType']}] seq={e['sequenceNo']}")

print("\n" + "=" * 60)
print("V 完整双录流程演示成功!")
print("=" * 60)
