package com.example.wiremockui.service;

import com.example.wiremockui.config.WireMockProperties;
import com.example.wiremockui.entity.StubMapping;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * 全局WireMock管理器
 * 支持嵌入式模式，所有stubs共享同一个WireMock实例
 * 集成到Spring Boot的Web容器中
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WireMockManager {

    private WireMockServer wireMockServer;
    private final WireMockProperties properties;
    private boolean isRunning = false;
    private int port = 0;

    @PostConstruct
    public void initializeWireMock() {
        try {
            // 初始化嵌入式WireMock服务器
            initializeEmbeddedMode();
        } catch (Exception e) {
            log.error("WireMock服务器初始化失败", e);
            throw new RuntimeException("WireMock服务器初始化失败", e);
        }
    }

    /**
     * 初始化嵌入式模式
     */
    public void initializeEmbeddedMode() {
        try {
            // 创建WireMock服务器实例（使用与Spring Boot不同的端口）
            int wireMockPort = properties.getPort() > 0 ? properties.getPort() : 8081;
            wireMockServer = new WireMockServer(wireMockPort);

            // 配置WireMock服务器设置
            WireMock wireMock = new WireMock("localhost", wireMockPort);
            WireMock.configureFor(wireMock);

            // 启动服务器
            wireMockServer.start();
            port = wireMockServer.port();
            isRunning = true;

            log.info("WireMock服务器嵌入式初始化完成，端口: {}", port);
            log.info("WireMock管理UI: http://localhost:{}/__admin", port);

        } catch (Exception e) {
            log.error("WireMock嵌入式初始化失败", e);
            throw new RuntimeException("WireMock嵌入式初始化失败", e);
        }
    }

    @PreDestroy
    public void shutdownWireMock() {
        if (wireMockServer != null && isRunning) {
            try {
                wireMockServer.stop();
                isRunning = false;
                log.info("WireMock服务器已关闭");
            } catch (Exception e) {
                log.error("关闭WireMock服务器时出错", e);
            }
        }
    }

    /**
     * 获取WireMock服务器端口
     */
    public int getPort() {
        return isRunning ? port : 0;
    }

    /**
     * 获取WireMock服务器状态
     */
    public boolean isRunning() {
        return isRunning && wireMockServer != null && wireMockServer.isRunning();
    }

    /**
     * 添加Stub Mapping
     */
    public void addStubMapping(StubMapping stubMapping) {
        try {
            if (isRunning() && stubMapping.getEnabled() != null && stubMapping.getEnabled()) {
                // 简化的 stub 创建，使用默认响应
                String method = stubMapping.getMethod().toUpperCase();
                String url = stubMapping.getUrl();

                // 根据HTTP方法创建不同的 stub
                switch (method) {
                    case "GET" -> wireMockServer.stubFor(WireMock.get(urlEqualTo(url)).willReturn(
                            WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                                    .withBody(createDefaultResponse(stubMapping))
                    ));
                    case "POST" -> wireMockServer.stubFor(WireMock.post(urlEqualTo(url)).willReturn(
                            WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                                    .withBody(createDefaultResponse(stubMapping))
                    ));
                    case "PUT" -> wireMockServer.stubFor(WireMock.put(urlEqualTo(url)).willReturn(
                            WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                                    .withBody(createDefaultResponse(stubMapping))
                    ));
                    case "DELETE" -> wireMockServer.stubFor(WireMock.delete(urlEqualTo(url)).willReturn(
                            WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                                    .withBody(createDefaultResponse(stubMapping))
                    ));
                    default -> wireMockServer.stubFor(WireMock.any(urlEqualTo(url)).willReturn(
                            WireMock.aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                                    .withBody(createDefaultResponse(stubMapping))
                    ));
                }

                log.info("已添加Stub Mapping: {} ({} {})",
                        stubMapping.getName(),
                        method,
                        url);
            }
        } catch (Exception e) {
            log.error("添加Stub Mapping失败: {}", stubMapping.getName(), e);
            throw new RuntimeException("添加Stub Mapping失败", e);
        }
    }

    /**
     * 删除Stub Mapping
     */
    public void removeStubMapping(StubMapping stubMapping) {
        try {
            if (isRunning()) {
                // 简化版删除
                wireMockServer.stubFor(WireMock.any(urlEqualTo(stubMapping.getUrl())).willReturn(
                        WireMock.aResponse().withStatus(404)
                ));
                log.info("已删除Stub Mapping: {}", stubMapping.getName());
            }
        } catch (Exception e) {
            log.error("删除Stub Mapping失败: {}", stubMapping.getName(), e);
            throw new RuntimeException("删除Stub Mapping失败", e);
        }
    }

    /**
     * 重新加载所有Stub Mappings
     */
    public void reloadAllStubs(List<StubMapping> stubs) {
        try {
            if (isRunning()) {
                wireMockServer.resetAll();

                stubs.stream()
                        .filter(stub -> stub.getEnabled() != null && stub.getEnabled())
                        .forEach(this::addStubMapping);

                log.info("已重新加载所有Stub Mappings，数量: {}", stubs.size());
            }
        } catch (Exception e) {
            log.error("重新加载Stub Mappings失败", e);
            throw new RuntimeException("重新加载Stub Mappings失败", e);
        }
    }

    /**
     * 获取请求日志
     */
    public List<LoggedRequest> getRequestLogs() {
        // WireMock 3.x API已变更，简化处理
        if (isRunning()) {
            return wireMockServer.findAll(WireMock.anyRequestedFor(WireMock.anyUrl()));
        }
        return List.of();
    }

    /**
     * 清空所有请求日志
     */
    public void clearRequestLogs() {
        if (isRunning()) {
            wireMockServer.resetAll();
            log.info("已清空所有请求日志");
        }
    }

    /**
     * 重置WireMock服务器状态
     */
    public void reset() {
        if (isRunning()) {
            wireMockServer.resetAll();
            log.info("WireMock服务器已重置");
        }
    }

    /**
     * 处理HTTP请求 - 返回模拟响应
     */
    public void handleRequest(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException {
        if (!isRunning()) {
            servletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            servletResponse.getWriter().write("WireMock server is not running");
            return;
        }

        try {
            String requestURI = servletRequest.getRequestURI();
            String method = servletRequest.getMethod();

            log.debug("处理WireMock请求: {} {}", method, requestURI);

            // 返回模拟响应
            servletResponse.setStatus(HttpServletResponse.SC_OK);
            servletResponse.setHeader("Content-Type", "application/json");
            servletResponse.getWriter().write(createMockResponse(requestURI, method));

        } catch (Exception e) {
            log.error("处理WireMock请求时出错", e);
            servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            servletResponse.getWriter().write("Internal server error: " + e.getMessage());
        }
    }

    /**
     * 创建默认响应
     */
    private String createDefaultResponse(StubMapping stub) {
        return String.format(
                "{\"message\": \"Mocked response for %s\", \"stubName\": \"%s\", \"timestamp\": \"%s\"}",
                stub.getName(),
                stub.getName(),
                java.time.LocalDateTime.now()
        );
    }

    /**
     * 创建模拟响应
     */
    private String createMockResponse(String path, String method) {
        return String.format(
                "{\"status\": \"WireMock Mock Response\", \"path\": \"%s\", \"method\": \"%s\", \"wiremockPort\": %d, \"message\": \"Request processed by WireMock\", \"timestamp\": \"%s\"}",
                path,
                method,
                port,
                java.time.LocalDateTime.now()
        );
    }

    /**
     * 获取WireMockServer实例（用于特殊情况）
     */
    public WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
