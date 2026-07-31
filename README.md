# AIRecord - 线上线下双录融合系统

> **AI**-powered **Record**ing System · 一套话术 · 一套流程 · 一套质检 · 一份证据

[![Vue](https://img.shields.io/badge/Vue-3.4-4FC08D?logo=vue.js)](https://vuejs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?logo=spring-boot)](https://spring.io/)
[![JDK](https://img.shields.io/badge/JDK-17-007396?logo=openjdk)](https://openjdk.org/)
[![Element Plus](https://img.shields.io/badge/Element%20Plus-2.7-409EFF)](https://element-plus.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.4-3178C6?logo=typescript)](https://www.typescriptlang.org/)

## 项目简介

AIRecord 是一套**完整可运行**的金融产品销售双录(录音录像)系统,覆盖保险、银行理财、基金、信托等强监管产品的销售合规要求。

- ✅ **一套话术**:按"产品 × 风险等级 × 流程节点"三维结构化,合规审核一次,全网生效
- ✅ **一套流程**:线上/线下统一状态机,断点续录无感切换
- ✅ **一套质检**:统一规则引擎 + 关键词检测,任何渠道结果一致
- ✅ **一份证据**:视频 + 业务数据 + 签字 + 风险评估,区块链存证

## 项目结构 (Monorepo)

```
AIRecord/
├── src/                          # 【前端 A】Vue 3 + TypeScript 工作台(主)
├── index.html                    # 前端 A 入口
├── package.json                  # 前端 A 依赖
├── vite.config.ts
├── tsconfig.json
│
└── backend-springboot/           # 【后端 + 前端 B】Spring Boot + Vue 3 JS 演示
    ├── pom.xml                   # Maven 配置
    ├── src/main/java/            # 50 个 Java 源文件
    ├── src/main/resources/       # schema.sql + data.sql + application.yml
    ├── web/                      # 【前端 B】Vue 3 + JavaScript 演示(可独立运行)
    └── full_demo.py              # Python 完整流程演示脚本
```

| 组件 | 技术栈 | 端口 | 说明 |
|------|--------|------|------|
| **前端 A**(主) | Vue 3 + TypeScript + Vite + Element Plus + Pinia | 5173 (dev) | 原"智能双录工作台前端" |
| **后端** | Spring Boot 3.2.5 + JDK 17 + H2 | 8080 | 完整业务后端 |
| **前端 B**(备) | Vue 3 + JavaScript + Vite + Element Plus + Pinia | 5173 (dev) | backend-springboot/web 演示前端 |

## 快速开始

### 方式 1:仅前端 A(主工作台)

```bash
npm install
npm run dev
# 访问 http://localhost:5173
```

### 方式 2:后端 + 前端 A 联动

```bash
# 终端 1:启动后端
cd backend-springboot
mvn spring-boot:run

# 终端 2:启动前端 A (需要配置 Vite proxy)
# 默认后端在 8080,前端会自动通过 proxy 转发 /api
npm run dev
```

### 方式 3:后端 + 前端 B(Vue 3 JS 演示)

```bash
# 终端 1:启动后端
cd backend-springboot
mvn spring-boot:run

# 终端 2:启动前端 B
cd backend-springboot/web
npm install
npm run dev
# 访问 http://localhost:5173
```

### 方式 4:命令行完整流程演示

```bash
cd backend-springboot
mvn spring-boot:run &
# 等待 30 秒启动完成后
python3 full_demo.py
```

输出示例:
```
[1] 创建会话 → DR20260731183148000001
[2] 11 节点全 PASS
[3] 启动视频录制
[4] 客户签字 → CFCA-DR...
[5] Saga 执行 → 订单 + 视频哈希 + 区块链存证
[6] 最终状态: COMPLETED / SUCCESS
[7] 质检报告: 11/11 PASS
[8] 事件流: 19 个事件
V 完整双录流程演示成功!
```

## 后端核心模块

| 模块 | 文件 | 作用 |
|------|------|------|
| 编排引擎 | `SessionOrchestrator` | 状态机推进 |
| 话术引擎 | `ScriptEngine` | 模板 + 变量绑定 |
| 风评引擎 | `RiskEngine` | 5 级风险评估 |
| 质检引擎 | `QualityEngine` | 必含/禁止/时长检测 |
| Saga 协调器 | `SagaCoordinator` | 分布式事务+补偿 |
| 事件溯源 | `EventStore` | 全流程审计 |
| 视频/签章/区块链 | `VideoService` 等 | 模拟实现(易替换) |

## 后端 REST API(30+ 端点)

- `POST /api/session/create` - 创建会话
- `GET /api/session/{id}/current-node` - 获取当前话术
- `POST /api/session/{id}/submit-node` - 提交节点+质检
- `POST /api/session/{id}/video/start` - 启动录制
- `POST /api/session/{id}/sign` - 签字
- `POST /api/session/{id}/video/complete` - 完成录制+触发 Saga
- `POST /api/session/{id}/pause` / `GET .../resume` - 暂停/断点续录
- `GET /api/event/{id}` - 事件流
- `GET /api/quality/report/{id}` - 质检报告
- 等等...

## 预置数据

- 2 套话术模板(R3 银行理财-平衡型 / R1 货币基金-谨慎型)
- 11 个标准节点
- 8 条 P0 质检规则
- 3 份风评问卷样例

## 环境要求

| 依赖 | 版本 |
|------|------|
| **JDK** | 17+ |
| **Maven** | 3.8+ |
| **Node.js** | 18+ |
| **npm** | 9+ |
| **Python** | 3.6+ (可选,用于运行演示脚本) |

## 升级到生产

| 模块 | 现状 | 生产建议 |
|------|------|----------|
| 数据库 | H2 内存 | MySQL 8 + 主从 |
| 消息队列 | Spring ApplicationEvent | RocketMQ / Kafka |
| 缓存 | Caffeine | Redis Cluster |
| 视频 | 模拟 | MinIO/OSS + WebRTC |
| 签章 | 模拟 | CFCA / 沃通 CA |
| 区块链 | 模拟 | FISCO BCOS / 长安链 |

## License

MIT
