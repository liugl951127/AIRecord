# backend-springboot - 双录系统后端

> Spring Boot 3.2.5 + JDK 17 + H2 内存数据库(开箱即用)

## 快速启动

```bash
# 启动后端
mvn spring-boot:run

# 完整流程演示(另开终端)
python3 full_demo.py
```

## 访问地址

| 地址 | 说明 |
|------|------|
| http://localhost:8080/ | API 根路径 |
| http://localhost:8080/actuator/health | 健康检查 |
| http://localhost:8080/h2-console | H2 数据库控制台 |

## 模块列表

| 模块 | 路径 | 作用 |
|------|------|------|
| 启动类 | `DoubleRecordingApplication.java` | Spring Boot 入口 |
| 公共 | `common/` | 响应包装、异常处理、ID 生成 |
| 领域模型 | `domain/` | 11 张表的 JPA 实体 |
| 编排引擎 | `orchestrator/` | 流程编排 + 状态机 |
| 话术引擎 | `script/` | 话术模板加载 + 变量渲染 |
| 风评引擎 | `risk/` | 5 级风险评估 |
| 质检引擎 | `quality/` | 必含/禁止/时长/回应检测 |
| Saga 协调器 | `saga/` | 分布式事务 + 补偿 |
| 事件溯源 | `event/` | 全流程审计 |
| 视频服务 | `video/` | 模拟(可替换为 MinIO/OSS) |
| 签章服务 | `signature/` | 模拟(可对接 CFCA) |
| 区块链 | `chain/` | 模拟(可对接 FISCO BCOS) |
| REST API | `api/` | 30+ 端点 |

## 数据库表(11 张)

- `dr_session` - 双录会话主表
- `dr_session_node` - 节点执行明细
- `dr_script_template` / `dr_script_node` / `dr_script_keyword` - 话术模板
- `dr_quality_rule` / `dr_quality_report` - 质检
- `dr_event_log` - 事件溯源
- `dr_saga_log` - Saga 日志
- `dr_video` - 视频元数据
- `dr_risk_questionnaire` - 风评问卷

## API 端点

详见根目录 [README.md](../README.md) 的 API 列表。

## 目录结构

```
backend-springboot/
├── pom.xml
├── full_demo.py              # Python 完整流程演示
├── README.md
└── src/
    └── main/
        ├── java/com/mavis/doublerecording/
        │   ├── DoubleRecordingApplication.java
        │   ├── common/
        │   ├── domain/         # 实体 + Repository
        │   ├── orchestrator/   # 编排引擎
        │   ├── script/         # 话术引擎
        │   ├── risk/           # 风评引擎
        │   ├── quality/        # 质检引擎
        │   ├── saga/           # Saga 协调器
        │   ├── event/          # 事件溯源
        │   ├── video/          # 视频服务
        │   ├── signature/      # 签章服务
        │   ├── chain/          # 区块链存证
        │   └── api/            # REST 控制器
        └── resources/
            ├── application.yml
            ├── schema.sql      # 11 张表
            ├── data.sql        # 初始数据
            └── static/         # Vue 打包后的静态文件(生产集成)
```

## 配套 Vue 前端(web 子目录)

`web/` 是一个独立的 Vue 3 + Vite + Element Plus + JavaScript 前端实现,
作为后端的演示前端,可以使用 Vite dev 模式开发:

```bash
cd web
npm install
npm run dev  # 启动开发服务器
```

## License

MIT
