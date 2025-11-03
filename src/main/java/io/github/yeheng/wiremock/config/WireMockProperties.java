package io.github.yeheng.wiremock.config;

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

}
