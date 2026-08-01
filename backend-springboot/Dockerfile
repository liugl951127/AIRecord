# ============================================
# AIRecord - 多阶段构建 Dockerfile
# 阶段 1:构建(maven + JDK 17)
# 阶段 2:运行时(Eclipse Temurin JRE 17,体积小)
# ============================================

# ============ 阶段 1:构建 ============
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# 先复制 pom 利用 Docker 缓存
COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests

# 复制源代码并打包
COPY src ./src
RUN mvn -B clean package -DskipTests

# ============ 阶段 2:运行时 ============
FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="AIRecord Team <ai@double-recording.com>"
LABEL version="1.6.0"
LABEL description="线上线下双录融合系统"

# 中文环境
ENV LANG=zh_CN.UTF-8 \
    LANGUAGE=zh_CN:zh \
    LC_ALL=zh_CN.UTF-8 \
    TZ=Asia/Shanghai

# 安装字体(PDF 中文需要)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        fontconfig \
        fonts-noto-cjk \
        fonts-wqy-zenhei \
        tzdata && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    rm -rf /var/lib/apt/lists/*

# 复制应用
WORKDIR /app
COPY --from=builder /build/target/double-recording-1.0.0.jar /app/app.jar

# 创建上传目录
RUN mkdir -p /app/uploads /app/logs /app/data

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM 参数(生产优化)
ENV JAVA_OPTS="\
    -server \
    -Xms512m \
    -Xmx2048m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/app/logs/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
