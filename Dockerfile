# 使用 Eclipse Temurin 作为基础镜像
# 这是一个轻量级的 OpenJDK 发行版，特别适合生产环境
FROM eclipse-temurin:21-jre-alpine

# 设置维护者信息
LABEL maintainer="your-email@example.com"
LABEL description="WireMock UI Manager - Spring Boot Application"
LABEL version="1.0.0"

# 创建应用用户
# 使用非 root 用户运行应用是一个重要的安全最佳实践
RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

# 设置工作目录
WORKDIR /app

# 复制 JAR 文件
# 我们使用通配符来匹配 JAR 文件名
COPY target/wiremock-ui-*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app

# 切换到应用用户
USER appuser

# 暴露端口
# Spring Boot 应用默认运行在 8080 端口
EXPOSE 8080

# 添加健康检查
# 这有助于容器编排系统了解应用的健康状态
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 环境变量配置
# 这些变量可以被覆盖以适应不同的部署环境
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# 启动命令
# 我们使用 exec 形式来确保信号正确传递
# 这允许优雅关闭
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
