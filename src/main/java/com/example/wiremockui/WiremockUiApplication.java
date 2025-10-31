package com.example.wiremockui;

import com.example.wiremockui.config.WireMockProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * WireMock UI 管理应用主类
 * 提供 Web UI 界面来管理 WireMock stubs
 */
@SpringBootApplication
@EnableConfigurationProperties(WireMockProperties.class)
@ConfigurationPropertiesScan
public class WiremockUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(WiremockUiApplication.class, args);
        System.out.println("""
                
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
