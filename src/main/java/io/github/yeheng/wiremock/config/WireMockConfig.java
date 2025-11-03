package io.github.yeheng.wiremock.config;

import org.springframework.context.annotation.Configuration;

/**
 * WireMock 配置类
 * 简化配置：Filter 现在通过 WireMockServletFilter 处理
 * 不再需要额外的 Filter 配置
 */
@Configuration
public class WireMockConfig {
    // 此配置类现在为空，仅作标记用途
    // WireMockServletFilter 已通过 @Component 注解自动注册
}
