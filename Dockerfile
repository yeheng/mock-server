# ================================
# 构建阶段 - 编译 Java 代码和构建前端资源
# ================================
FROM eclipse-temurin:21-jdk-alpine AS builder

# 设置维护者信息
LABEL maintainer="hengheng8848@gmail.com"
LABEL description="WireMock UI Manager - Spring Boot Application (Builder Stage)"
LABEL version="1.0.0"

# 安装必要的构建工具
RUN apk add --no-cache curl maven

# 设置工作目录
WORKDIR /build

# 构建应用
# 使用 Maven 构建整个项目，包括前端资源
RUN mvn clean package -DskipTests

# ================================
# 运行阶段 - 创建最终的生产镜像
# ================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# 设置维护者信息
LABEL maintainer="hengheng8848@gmail.com"
LABEL description="WireMock UI Manager - Spring Boot Application"
LABEL version="1.0.0"

# 安装 curl 用于健康检查
RUN apk add --no-cache curl

# 创建应用用户
# 使用非 root 用户运行应用是一个重要的安全最佳实践
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

# 设置工作目录
WORKDIR /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /build/target/wiremock-ui.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app

# 切换到应用用户
USER appuser

# 暴露端口
# Spring Boot 应用默认运行在 8080 端口
EXPOSE 8080

# 添加卷映射，持久化数据
VOLUME ["/app/data", "/app/logs"]

# 添加健康检查
# 这有助于容器编排系统了解应用的健康状态
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 环境变量配置
# 这些变量可以被覆盖以适应不同的部署环境
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# 启动命令
# 我们使用 exec 形式来确保信号正确传递
# 这允许优雅关闭
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
