package com.example.wiremockui.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.wiremockui.entity.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局WireMock管理器
 * 集成到Spring Boot的嵌入式Undertow容器中，不使用独立端口
 * 所有stub请求都通过同一个Undertow容器处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WireMockManager {

    @Value("${server.port:8080}")
    private int serverPort;

    // 使用内存存储 stub mappings，而不是独立的 WireMockServer
    private final List<StubMapping> stubs = new CopyOnWriteArrayList<>();
    private final Map<String, List<LoggedRequest>> requestLogs = new ConcurrentHashMap<>();

    private boolean isRunning = false;

    @PostConstruct
    public void initialize() {
        try {
            // 集成模式：不需要启动独立服务器
            isRunning = true;
            port = serverPort;

            log.info("WireMock 集成到 Spring Boot Undertow 容器，端口: {}", port);
            log.info("所有 stub 请求将通过同一个容器处理");
        } catch (Exception e) {
            log.error("WireMock 初始化失败", e);
            throw new RuntimeException("WireMock 初始化失败", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        isRunning = false;
        stubs.clear();
        requestLogs.clear();
        log.info("WireMock 已关闭");
    }

    private int port;

    /**
     * 获取WireMock服务器端口（实际就是Spring Boot端口）
     */
    public int getPort() {
        return isRunning ? port : 0;
    }

    /**
     * 获取WireMock服务器状态
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 添加Stub Mapping
     */
    public void addStubMapping(StubMapping stubMapping) {
        try {
            if (!isRunning()) {
                throw new IllegalStateException("WireMock服务器未运行");
            }

            if (stubMapping.getEnabled() == null || !stubMapping.getEnabled()) {
                log.debug("Stub 已禁用，跳过: {}", stubMapping.getName());
                return;
            }

            // 移除相同URL的旧stub
            stubs.removeIf(s -> s.getUrl().equals(stubMapping.getUrl()) && s.getMethod().equalsIgnoreCase(stubMapping.getMethod()));

            // 添加新stub
            stubs.add(stubMapping);

            log.info("已添加Stub Mapping: {} ({} {})",
                    stubMapping.getName(),
                    stubMapping.getMethod(),
                    stubMapping.getUrl());
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
            if (!isRunning()) {
                return;
            }

            stubs.removeIf(s -> s.getUrl().equals(stubMapping.getUrl()) && s.getMethod().equalsIgnoreCase(stubMapping.getMethod()));
            log.info("已删除Stub Mapping: {}", stubMapping.getName());
        } catch (Exception e) {
            log.error("删除Stub Mapping失败: {}", stubMapping.getName(), e);
            throw new RuntimeException("删除Stub Mapping失败", e);
        }
    }

    /**
     * 重新加载所有Stub Mappings
     */
    public void reloadAllStubs(List<StubMapping> newStubs) {
        try {
            if (!isRunning()) {
                return;
            }

            // 清空现有 stubs
            stubs.clear();

            // 添加启用的 stubs
            newStubs.stream()
                    .filter(stub -> stub.getEnabled() != null && stub.getEnabled())
                    .forEach(this::addStubMapping);

            log.info("已重新加载所有Stub Mappings，数量: {}", stubs.size());
        } catch (Exception e) {
            log.error("重新加载Stub Mappings失败", e);
            throw new RuntimeException("重新加载Stub Mappings失败", e);
        }
    }

    /**
     * 获取请求日志
     */
    public List<LoggedRequest> getRequestLogs() {
        List<LoggedRequest> allRequests = new ArrayList<>();
        requestLogs.values().forEach(allRequests::addAll);
        return allRequests;
    }

    /**
     * 清空所有请求日志
     */
    public void clearRequestLogs() {
        requestLogs.clear();
        log.info("已清空所有请求日志");
    }

    /**
     * 重置WireMock服务器状态
     */
    public void reset() {
        stubs.clear();
        requestLogs.clear();
        log.info("WireMock服务器已重置");
    }

    /**
     * 处理HTTP请求 - 返回模拟响应
     * 这个方法会被 Filter 调用，在同一个 Undertow 容器中处理请求
     */
    public void handleRequest(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException {
        if (!isRunning()) {
            servletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            servletResponse.getWriter().write("WireMock server is not running");
            return;
        }

        try {
            String requestURI = servletRequest.getRequestURI();
            String method = servletRequest.getMethod().toUpperCase();

            log.debug("处理WireMock请求: {} {}", method, requestURI);

            // 记录请求
            recordRequest(requestURI, method);

            // 查找匹配的 stub
            StubMapping matchedStub = findMatchingStub(requestURI, method);

            if (matchedStub != null) {
                // 找到匹配的 stub，返回响应
                servletResponse.setStatus(HttpServletResponse.SC_OK);
                servletResponse.setContentType("application/json;charset=UTF-8");
                servletResponse.setCharacterEncoding("UTF-8");

                String responseBody = matchedStub.getResponseDefinition();
                if (responseBody == null || responseBody.trim().isEmpty()) {
                    responseBody = createDefaultResponse(matchedStub);
                }

                servletResponse.getWriter().write(responseBody);
                log.debug("Stub 匹配成功: {}", matchedStub.getName());
            } else {
                // 未找到匹配的 stub，返回 404
                servletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                servletResponse.setContentType("application/json;charset=UTF-8");
                servletResponse.setCharacterEncoding("UTF-8");
                servletResponse.getWriter().write(String.format(
                    "{\"error\": \"No matching stub\", \"path\": \"%s\", \"method\": \"%s\", \"message\": \"请检查 stub 配置\"}",
                    requestURI,
                    method
                ));
                log.debug("未找到匹配的 stub: {} {}", method, requestURI);
            }

        } catch (Exception e) {
            log.error("处理WireMock请求时出错", e);
            servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setCharacterEncoding("UTF-8");
            servletResponse.getWriter().write("{\"error\": \"Internal server error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 查找匹配的 stub
     */
    private StubMapping findMatchingStub(String path, String method) {
        return stubs.stream()
                .filter(stub -> {
                    // 验证 stub 是否启用
                    if (stub.getEnabled() == null || !stub.getEnabled()) {
                        return false;
                    }

                    // 验证 HTTP 方法匹配
                    if (!stub.getMethod().equalsIgnoreCase(method)) {
                        return false;
                    }

                    // 验证 URL 匹配（简单匹配，支持精确匹配和前缀匹配）
                    String stubUrl = stub.getUrl();
                    String requestPath = path;

                    // 精确匹配
                    if (stubUrl.equals(requestPath)) {
                        return true;
                    }

                    // 前缀匹配（如果 stubUrl 以 / 结尾）
                    if (stubUrl.endsWith("/") && requestPath.startsWith(stubUrl)) {
                        return true;
                    }

                    // 带 * 的简单通配符匹配
                    if (stubUrl.contains("*")) {
                        String pattern = stubUrl.replace("*", ".*");
                        return requestPath.matches(pattern);
                    }

                    return false;
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 记录请求
     */
    private void recordRequest(String path, String method) {
        String key = method + " " + path;
        requestLogs.computeIfAbsent(key, k -> new ArrayList<>());
        // 这里可以添加更详细的请求信息
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
     * 获取所有 stubs
     */
    public List<StubMapping> getAllStubs() {
        return new ArrayList<>(stubs);
    }
}
