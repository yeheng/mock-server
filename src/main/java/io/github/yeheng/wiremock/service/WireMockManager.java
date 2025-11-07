package io.github.yeheng.wiremock.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import io.github.yeheng.wiremock.entity.StubMapping;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WireMockManager {

    @Value("${server.port:8080}")
    private int serverPort;

    private final RequestConverter requestConverter;
    private final ResponseConverter responseConverter;
    private final StubMappingConverter stubMappingConverter;

    private final Map<String, StubMapping> stubs = new ConcurrentHashMap<>();
    private final Map<String, List<LoggedRequest>> requestLogs = new ConcurrentHashMap<>();

    private final Lock modifyLock = new ReentrantLock();

    private volatile boolean isRunning = false;
    private WireMockServer wireMockServer;
    private DirectCallHttpServer directCallServer;
    private int port;

    @PostConstruct
    public void initialize() {
        try {
            startServer();
            port = serverPort;
            log.info("WireMock 集成: 内部 WireMockServer 端口={}, 应用端口={}", wireMockServer.port(), port);
            log.info("所有非管理请求将代理到内部 WireMockServer 进行匹配");
        } catch (Exception e) {
            log.error("WireMock 初始化失败", e);
            throw new RuntimeException("WireMock 初始化失败", e);
        }
    }

    private synchronized void startServer() {
        DirectCallHttpServerFactory factory = new DirectCallHttpServerFactory();

        WireMockConfiguration config = WireMockConfiguration.options().dynamicPort()
                .httpServerFactory(factory);
        wireMockServer = new WireMockServer(config);
        wireMockServer.start();

        directCallServer = factory.getHttpServer();

        isRunning = true;
    }

    @PreDestroy
    public void shutdown() {
        isRunning = false;
        stubs.clear();
        requestLogs.clear();
        try {
            if (wireMockServer != null && wireMockServer.isRunning()) {
                wireMockServer.stop();
            }
        } catch (Exception e) {
            log.warn("停止内部 WireMockServer 失败", e);
        }
        log.info("WireMock 已关闭");
    }

    public int getPort() {
        return isRunning ? port : 0;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void handleRequest(jakarta.servlet.http.HttpServletRequest servletRequest,
                              jakarta.servlet.http.HttpServletResponse servletResponse)
            throws IOException {
        if (!isRunning()) {
            servletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            servletResponse.getWriter().write("WireMock server is not running");
            return;
        }

        try {
            ensureWireMockServerStarted();

            Request wiremockRequest = requestConverter.convert(servletRequest);

            Response wiremockResponse;
            if (wiremockRequest.getUrl().startsWith("/__admin")) {
                wiremockResponse = directCallServer.adminRequest(wiremockRequest);
            } else {
                wiremockResponse = directCallServer.stubRequest(wiremockRequest);
            }

            responseConverter.convert(wiremockResponse, servletResponse);

        } catch (Exception e) {
            log.error("处理WireMock请求时出错", e);
            servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setCharacterEncoding("UTF-8");
            servletResponse.getWriter()
                    .write("{\"error\": \"Internal server error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    public void addStubMapping(StubMapping stubMapping) {
        modifyLock.lock();
        try {
            if (!isRunning()) {
                throw new IllegalStateException("WireMock服务器未运行");
            }

            if (stubMapping.getEnabled() == null || !stubMapping.getEnabled()) {
                log.debug("Stub 已禁用，跳过: {}", stubMapping.getName());
                return;
            }

            if (stubMapping.getUuid() == null || stubMapping.getUuid().trim().isEmpty()) {
                stubMapping.setUuid(java.util.UUID.randomUUID().toString());
            }

            String stubKey = stubMapping.getUuid();
            stubs.put(stubKey, stubMapping);

            try {
                ensureWireMockServerStarted();
            } catch (IllegalAccessException e) {
                log.error("启动 WireMockServer 失败", e);
                throw new RuntimeException("启动 WireMockServer 失败", e);
            }

            MappingBuilder builder = stubMappingConverter.convert(stubMapping);
            wireMockServer.stubFor(builder);

            log.debug("已注册 stub 到 WireMock server: {}", stubMapping.getUrl());
            log.info("已添加Stub Mapping: {} ({} {}) [uuid={}]",
                    stubMapping.getName(),
                    stubMapping.getMethod(),
                    stubMapping.getUrl(),
                    stubKey);
        } finally {
            modifyLock.unlock();
        }
    }

    private String generateStubKey(StubMapping stubMapping) {
        if (stubMapping.getUuid() != null && !stubMapping.getUuid().trim().isEmpty()) {
            return stubMapping.getUuid();
        }
        if (stubMapping.getId() != null) {
            return "id-" + stubMapping.getId();
        }
        return null;
    }

    public void removeStubMapping(StubMapping stubMapping) {
        modifyLock.lock();
        try {
            if (!isRunning()) {
                return;
            }

            String stubKey = stubMapping.getUuid();
            if (stubKey == null || stubKey.trim().isEmpty()) {
                stubKey = generateStubKey(stubMapping);
            }

            boolean removed = false;

            if (stubKey != null && stubs.containsKey(stubKey)) {
                StubMapping removedStub = stubs.remove(stubKey);
                if (removedStub != null) {
                    try {
                        wireMockServer.removeStubMapping(java.util.UUID.fromString(stubKey));
                        removed = true;
                        log.info("已从 WireMock server 中删除 stub: {}", stubKey);
                    } catch (IllegalArgumentException e) {
                        log.warn("Stub UUID 不是有效格式: {}", stubKey);
                        try {
                            ensureWireMockServerStarted();
                        } catch (IllegalAccessException ex) {
                            log.error("启动 WireMockServer 失败", ex);
                        }
                        wireMockServer.resetMappings();
                        for (StubMapping s : stubs.values()) {
                            wireMockServer.stubFor(stubMappingConverter.convert(s));
                        }
                        removed = true;
                        log.info("UUID 无效，已重载剩余的 {} 个 stubs", stubs.size());
                    }
                }
            } else {
                final String finalStubKey = stubKey;
                removed = stubs.entrySet().removeIf(entry -> {
                    StubMapping s = entry.getValue();
                    if (finalStubKey != null) {
                        return finalStubKey.equals(s.getUuid());
                    }
                    return s.getUrl().equals(stubMapping.getUrl())
                           && s.getMethod().equalsIgnoreCase(stubMapping.getMethod());
                });
                if (removed) {
                    try {
                        ensureWireMockServerStarted();
                    } catch (IllegalAccessException e) {
                        log.error("启动 WireMockServer 失败", e);
                    }
                    wireMockServer.resetMappings();
                    for (StubMapping s : stubs.values()) {
                        wireMockServer.stubFor(stubMappingConverter.convert(s));
                    }
                }
            }

            if (removed) {
                log.info("已删除Stub Mapping: {}", stubMapping.getName());
            } else {
                log.warn("未找到要删除的Stub Mapping: {}", stubMapping.getName());
            }
        } finally {
            modifyLock.unlock();
        }
    }

    public void reloadAllStubs(List<StubMapping> newStubs) {
        if (!isRunning()) {
            return;
        }

        stubs.clear();
        try {
            ensureWireMockServerStarted();
        } catch (IllegalAccessException e) {
            log.error("启动 WireMockServer 失败", e);
            return;
        }
        wireMockServer.resetMappings();

        newStubs.stream()
                .filter(stub -> stub.getEnabled() != null && stub.getEnabled())
                .forEach(this::addStubMapping);

        log.info("已重新加载所有Stub Mappings，数量: {}", stubs.size());
    }

    public List<LoggedRequest> getRequestLogs() {
        List<LoggedRequest> allRequests = new ArrayList<>();
        requestLogs.values().forEach(allRequests::addAll);
        return allRequests;
    }

    public void clearRequestLogs() {
        requestLogs.clear();
        log.info("已清空所有请求日志");
    }

    public void reset() {
        stubs.clear();
        requestLogs.clear();
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
        log.info("WireMock服务器已重置");
    }

    public List<StubMapping> getAllStubs() {
        return new ArrayList<>(stubs.values());
    }

    private void ensureWireMockServerStarted() throws IllegalAccessException {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            startServer();
        }
    }
}
