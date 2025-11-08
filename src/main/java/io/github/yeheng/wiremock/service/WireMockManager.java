package io.github.yeheng.wiremock.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

import io.github.yeheng.wiremock.entity.StubMapping;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
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

    @Getter
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

    public void handleRequest(jakarta.servlet.http.HttpServletRequest servletRequest,
            jakarta.servlet.http.HttpServletResponse servletResponse)
            throws IOException {
        if (!isRunning()) {
            write503(servletResponse, "WireMock server is not running");
            return;
        }

        try {
            Request request = requestConverter.convert(servletRequest);
            Response response = routeRequest(request);
            responseConverter.convert(response, servletResponse);
        } catch (Exception e) {
            log.error("处理WireMock请求时出错", e);
            write500(servletResponse, e.getMessage());
        }
    }

    private void write503(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\": \"Service Unavailable\", \"message\": \"" + message + "\"}");
    }

    private void write500(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter()
                .write("{\"error\": \"Internal server error\", \"message\": \"" + message + "\"}");
    }

    private Response routeRequest(Request request) {
        return request.getUrl().startsWith("/__admin")
                ? directCallServer.adminRequest(request)
                : directCallServer.stubRequest(request);
    }

    public void addStubMapping(StubMapping stubMapping) {
        if (!isRunning()) {
            throw new IllegalStateException("WireMock服务器未运行");
        }

        if (!isEnabled(stubMapping)) {
            log.debug("Stub 已禁用，跳过: {}", stubMapping.getName());
            return;
        }

        String stubKey = ensureUuid(stubMapping);
        stubs.put(stubKey, stubMapping);

        registerWithWireMock(stubMapping);

        log.info("已添加Stub Mapping: {} ({} {}) [uuid={}]",
                stubMapping.getName(),
                stubMapping.getMethod(),
                stubMapping.getUrl(),
                stubKey);
    }

    private boolean isEnabled(StubMapping stub) {
        return Boolean.TRUE.equals(stub.getEnabled());
    }

    private String ensureUuid(StubMapping stub) {
        if (stub.getUuid() == null || stub.getUuid().trim().isEmpty()) {
            stub.setUuid(java.util.UUID.randomUUID().toString());
        }
        return stub.getUuid();
    }

    private void registerWithWireMock(StubMapping stub) {
        try {
            ensureWireMockServerStarted();
        } catch (IllegalAccessException e) {
            log.error("启动 WireMockServer 失败", e);
            throw new RuntimeException("启动 WireMockServer 失败", e);
        }

        MappingBuilder builder = stubMappingConverter.convert(stub);
        wireMockServer.stubFor(builder);
        log.debug("已注册 stub 到 WireMock server: {}", stub.getUrl());
    }

    private String generateStubKey(StubMapping stubMapping) {
        String uuid = stubMapping.getUuid();
        if (uuid != null && !uuid.trim().isEmpty()) {
            return uuid;
        }
        return stubMapping.getId() != null ? "id-" + stubMapping.getId() : null;
    }

    public void removeStubMapping(StubMapping stubMapping) {
        if (!isRunning()) {
            return;
        }

        String stubKey = stubMapping.getUuid();
        if (stubKey == null || stubKey.trim().isEmpty()) {
            stubKey = generateStubKey(stubMapping);
        }

        boolean removed = removeByKeyOrPattern(stubKey, stubMapping);

        if (removed) {
            reloadAllStubs();
            log.info("已删除Stub Mapping: {}", stubMapping.getName());
        } else {
            log.warn("未找到要删除的Stub Mapping: {}", stubMapping.getName());
        }
    }

    private boolean removeByKeyOrPattern(String stubKey, StubMapping stub) {
        if (stubKey != null) {
            return stubs.remove(stubKey) != null;
        }

        return stubs.entrySet().removeIf(entry -> {
            StubMapping s = entry.getValue();
            return s.getUrl().equals(stub.getUrl())
                    && s.getMethod().equalsIgnoreCase(stub.getMethod());
        });
    }

    private void reloadAllStubs() {
        try {
            ensureWireMockServerStarted();
            wireMockServer.resetMappings();
            for (StubMapping s : stubs.values()) {
                wireMockServer.stubFor(stubMappingConverter.convert(s));
            }
        } catch (IllegalAccessException e) {
            log.error("重载 Stubs 失败", e);
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
                .filter(this::isEnabled)
                .forEach(this::addStubMapping);

        log.info("已重新加载所有Stub Mappings，数量: {}", stubs.size());
    }

    public void reset() {
        stubs.clear();
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
