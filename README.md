# WireMock UI Manager

Web UI for managing WireMock stubs - 提供可视化的 WireMock 存根管理和测试接口模拟服务。

## 项目概述

WireMock UI Manager 是一个基于 Spring Boot 的 Web 应用程序，为 WireMock 提供直观的用户界面，使得管理 API 存根和测试接口模拟变得更加简单高效。

## 核心特性

- **直观的 Web 界面**: 通过浏览器轻松管理 WireMock 存根
- **存根管理**: 创建、编辑、删除和测试 API 存根
- **实时监控**: 查看请求历史和响应统计
- **Spring Boot 集成**: 基于 Spring Boot 3.5.3 构建，提供企业级特性
- **数据持久化**: 使用 H2 数据库存储配置信息
- **Actuator 监控**: 提供应用健康检查和监控端点
- **测试覆盖**: 集成 JaCoCo 测试覆盖率报告

## 技术栈

- **Java**: 21
- **Spring Boot**: 3.5.3
- **Spring Framework**: Web, Validation, Data JPA, Actuator
- **WireMock**: 3.13.1 (用于 API 模拟)
- **数据库**: H2 (内存数据库)
- **构建工具**: Maven
- **Web 服务器**: Undertow
- **JSON 处理**: Jackson

## 安装与运行

### 前置要求

- Java 21 或更高版本
- Maven 3.6 或更高版本

### 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd wiremock-ui

# 构建项目
mvn clean package

# 运行测试
mvn test

# 生成测试覆盖率报告
mvn jacoco:report
```

### 运行应用

```bash
# 运行 Spring Boot 应用
mvn spring-boot:run

# 或者运行 JAR 文件
java -jar target/wiremock-ui-1.0.0.jar
```

应用将在 `http://localhost:8080` 启动。

## 使用指南

### 1. 访问 Web 界面

打开浏览器，访问 `http://localhost:8080` 进入管理界面。

### 2. 创建存根

- 点击 "新建存根" 按钮
- 配置请求匹配规则（URL、方法、头部等）
- 设置响应内容（状态码、响应体、延迟等）
- 保存配置

### 3. 测试存根

- 在存根列表中选择要测试的存根
- 发送测试请求
- 查看响应结果和匹配情况

## 测试

### 运行测试

```bash
# 运行所有测试
mvn test

# 生成测试覆盖率报告
mvn jacoco:report
```

## 项目结构

```
wiremock-ui/
├── src/main/java/
│   └── com/example/wiremockui/
│       ├── WiremockUiApplication.java
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       └── config/
├── src/test/java/
├── pom.xml
└── README.md
```

## 相关链接

- [WireMock 官方网站](https://wiremock.org/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)

## 许可证

本项目采用 MIT 许可证。

---

**注意**: 这是一个开源项目，欢迎社区贡献和改进建议。
