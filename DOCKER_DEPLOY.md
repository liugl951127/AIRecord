# 🐳 Docker 部署指南

## 一键启动完整服务栈

```bash
# 1. 进入项目
cd AIRecord

# 2. 构建并启动(应用 + MySQL + Redis + Nginx + Prometheus + Grafana)
docker compose up -d --build

# 3. 查看状态
docker compose ps

# 4. 查看应用日志
docker compose logs -f airecord

# 5. 访问服务
# 应用:    http://localhost
# API:     http://localhost/api/
# Swagger: http://localhost/swagger-ui.html
# H2:      http://localhost:8080/h2-console
# Grafana: http://localhost:3000  (admin/admin)
# Prom:    http://localhost:9090
```

## 服务端口映射

| 服务 | 容器端口 | 主机端口 | 用途 |
|------|---------|---------|------|
| AIRecord | 8080 | 8080 | 应用主服务 |
| MySQL | 3306 | 3306 | 关系数据库 |
| Redis | 6379 | 6379 | 缓存 |
| Nginx | 80/443 | 80/443 | 反向代理 + 静态资源 |
| Prometheus | 9090 | 9090 | 监控数据 |
| Grafana | 3000 | 3000 | 监控面板 |

## JVM 调优参数(已配 Dockerfile)

```
-Xms512m -Xmx2048m        # 堆内存
-XX:+UseG1GC              # G1 垃圾回收器
-XX:MaxGCPauseMillis=200  # 最大 GC 暂停 200ms
-XX:+UseStringDeduplication
-XX:+HeapDumpOnOutOfMemoryError
```

## 仅启动应用(快速验证)

```bash
# 不带 MySQL/Redis(默认 H2 内存数据库)
docker build -t airecord:1.6.0 backend-springboot/
docker run -d --name airecord \
  -p 8080:8080 \
  -v $PWD/data:/app/data \
  airecord:1.6.0
```

## 镜像大小优化

- **构建阶段**:maven:3.9.6-eclipse-temurin-17 (~1.5GB)
- **运行阶段**:eclipse-temurin:17-jre-jammy (~250MB)
- **最终镜像**:~280MB(包含 JDK + 应用 jar)

## 健康检查

```bash
# 容器内
docker exec airecord wget -qO- http://localhost:8080/actuator/health

# 宿主机
curl http://localhost:8080/actuator/health
```

## 数据持久化

| 路径 | 说明 |
|------|------|
| `/app/data` | 区块链 JSON 持久化 |
| `/app/uploads` | 视频文件上传 |
| `/app/logs` | 应用日志 |
| `mysql-data` (卷) | MySQL 数据库 |
| `redis-data` (卷) | Redis 持久化 |

## 监控集成

- **Prometheus**:抓取 `/actuator/prometheus`
- **Grafana**:导入 Spring Boot 仪表板(ID: 11378)
- **业务指标**:
  - `airecord_recording_start_total`
  - `airecord_risk_critical_total`
  - `airecord_chain_blocks_mined_total`
  - `airecord_api_requests_total`

## 生产部署建议

1. **关闭 H2 Web Console**
2. **使用 MySQL 替代 H2**
3. **启用 HTTPS** (Nginx + Let's Encrypt)
4. **配置日志持久化** (Loki/ELK)
5. **启用 Prometheus + AlertManager 告警**
6. **多实例部署** (K8s / Docker Swarm)
