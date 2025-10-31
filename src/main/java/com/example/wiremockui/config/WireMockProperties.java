package com.example.wiremockui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WireMock 配置属性
 */
@Data
@ConfigurationProperties(prefix = "wiremock")
public class WireMockProperties {

    /**
     * WireMock 服务器端口
     */
    private int port = 8081;

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
    private boolean enableAdminUi = true;

    /**
     * 是否启用请求日志
     */
    private boolean requestLoggingEnabled = true;
}
