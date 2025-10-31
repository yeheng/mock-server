package com.example.wiremockui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WireMock 配置属性
 * 已调整为集成模式，不再使用独立端口
 */
@Data
@ConfigurationProperties(prefix = "wiremock")
public class WireMockProperties {

    /**
     * WireMock 集成模式配置
     * 集成模式下，WireMock 使用 Spring Boot 相同的端口
     * 此配置保留以兼容现有代码，但不再使用独立端口
     */
    private int port = 0; // 0 表示使用 Spring Boot 端口

    /**
     * 目标服务器 URL (用于代理模式)
     */
    private String targetServerUrl = "";

    /**
     * Stub 文件存储目录
     */
    private String stubStorageDirectory = "./wiremock-stubs";

    /**
     * 是否启用管理 UI
     */
    private boolean enableAdminUi = false; // 集成模式下默认禁用独立的 admin UI

    /**
     * 是否启用请求日志
     */
    private boolean requestLoggingEnabled = true;

    /**
     * 是否为集成模式
     * true 表示 WireMock 集成到 Spring Boot 容器中，不使用独立端口
     */
    private boolean integratedMode = true;
}
