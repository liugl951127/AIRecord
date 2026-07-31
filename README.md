# 智能双录工作台 · Smart Dual Record UI

> Vue 3 + Element Plus + TypeScript + Vite
> 对应后端：[github.com/liugl951127/SmartDualRecord](https://github.com/liugl951127/SmartDualRecord)

## 功能

- 🎬 **录制工作台** - 8 节点可视化流程 + 实时禁播词扫描 + 关键节点高亮
- 📝 **业务创建** - 4 渠道 + 3 套产品快捷选择 + 风险评估自动触发
- 🔍 **全景查询** - 业务 ID 一键查询全流程状态 / 节点明细 / 录像信息
- 📜 **话术管理** - 通用配置浏览 + 禁播词动态新增
- 🔄 **状态机** - 16 个状态 + 合法转移明细可视化

## 快速开始

```bash
# 1. 安装依赖
npm install

# 2. 启动开发服务器
npm run dev
# 访问 http://localhost:5173

# 3. 生产构建
npm run build
npm run preview
```

> **重要**：需要后端在 `http://localhost:8080` 同时运行。Vite 已配代理 `/api` 和 `/ws`。

## 关键页面

### 1. 录制工作台

主流程：
- 顶部：业务概况 + 进度条
- 中部：8 节点流程图（关键节点 ★ 高亮）
- 左下：当前节点 + ASR 输入 + 实时禁播词检测
- 右下：必播项 / 风险匹配 / 实时日志
- 底部：终检 + 签字

**演示场景**：
- 正常流程：在 ASR 输入框点"填充示例"→ 完成 8 节点 → 终检 → 签字
- 阻断演示：点"填充禁播词"→ ASR 红框告警 → 完成节点被阻断

### 2. 业务创建

- 业务类型：保险 / 理财 / 基金
- 4 渠道：线下 / 远程 / AI 数字人 / 互联网
- 销售方：真人 / AI 数字人
- 产品快捷选择：3 套专属 YAML + 1 个"新产品无 YAML"演示

### 3. 全景查询

输入业务 ID（如 `BNK20260801-000001`）查询：
- 业务概况（11 个字段）
- 8 节点明细表
- 录像信息表

### 4. 话术管理

- 全局默认：禁播词 / 必问 / 必播 + 4 渠道差分
- 运行时新增禁播词（立即生效）
- 所有话术模板列表

### 5. 状态机

16 个状态可视化 + 合法转移明细

## 技术栈

| 项 | 版本 |
|---|---|
| Vue | 3.4.x |
| Vite | 5.x |
| TypeScript | 5.4.x |
| Element Plus | 2.7.x |
| Pinia | 2.1.x |
| Axios | 1.7.x |

## 主题色

```
--primary: #1e2a47   深海军蓝（主色）
--accent:  #b8860b   金（强调）
--green:   #2f6f5e   合规
--accent-2:#c1453a   砖红（警示）
--blue:    #3b6b8c   钢蓝
--purple:  #6b4a8a   紫
```

## 工程结构

```
smart-dual-record-ui/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
├── src/
│   ├── main.ts                # 入口
│   ├── App.vue                # 主框架 + 标签页
│   ├── style.css              # 全局样式
│   ├── api/
│   │   └── index.ts           # 所有后端 API 封装
│   ├── stores/
│   │   └── recording.ts       # Pinia 全局状态
│   ├── types/
│   │   └── index.ts           # TypeScript 类型
│   ├── utils/
│   │   └── nodes.ts           # 8 节点元数据
│   └── components/
│       ├── BusinessCreate.vue       # 业务创建
│       ├── RecordingWorkbench.vue   # 录制工作台（核心）
│       ├── OverviewPanel.vue        # 全景查询
│       ├── ScriptManager.vue        # 话术管理
│       └── StateMachineViewer.vue   # 状态机可视化
```

## 后端对应 API

| 页面 | 调用的 API |
|---|---|
| 录制工作台 | `recording/*` + `compliance/scan` + `script-config/product` |
| 业务创建 | `recording/start` + `recording/script/load` + `recording/risk/assess` + `recording/begin` |
| 全景查询 | `recording/overview/{id}` |
| 话术管理 | `script-config/global` + `script-config/all` + `script-config/forbidden-phrase` |
| 状态机 | `statemachine/transitions` + `health` |

## Vite 代理配置

```ts
server: {
  port: 5173,
  proxy: {
    '/api': { target: 'http://localhost:8080' },
    '/ws':  { target: 'ws://localhost:8080', ws: true }
  }
}
```

开发环境无需 CORS 配置。
