package io.github.yeheng.wiremock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.github.yeheng.wiremock.config.WireMockProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * WireMock UI 管理应用主类
 * 提供 Web UI 界面来管理 WireMock stubs
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties(WireMockProperties.class)
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "io.github.yeheng.wiremock.repository")
public class WiremockUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WiremockUiApplication.class, args);
        log.info("""

                ╔══════════════════════════════════════════════════════════════╗
                ║                    WireMock UI Manager                        ║
                ║                    启动成功! 🎉                                ║
                ║                                                              ║
                ║  🌐 Web UI: http://localhost:8080                           ║
                ║  📊 Actuator: http://localhost:8080/actuator                ║
                ║  🎯 WireMock: http://localhost:8080/__wiremock              ║
                ╚══════════════════════════════════════════════════════════════╝
                """);
    }
}
