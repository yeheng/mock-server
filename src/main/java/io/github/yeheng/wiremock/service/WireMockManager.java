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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 全局WireMock管理器
 * 集成到Spring Boot的嵌入式Undertow容器中，不使用独立端口
 * 所有stub请求都通过同一个Undertow容器处理
 * 已重构：拆分为多个组件，提高可维护性
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WireMockManager {

    @Value("${server.port:8080}")
    private int serverPort;

    // 依赖的组件
    private final RequestConverter requestConverter;
    private final ResponseConverter responseConverter;
    private final StubMappingConverter stubMappingConverter;
    private final PerformanceMonitor performanceMonitor;

    // 使用内存存储 stub mappings，使用 ConcurrentHashMap 替代 CopyOnWriteArrayList 以提升并发性能
    // key: UUID (如果有) 或 id (Long) 转字符串，value: StubMapping 对象
    private final Map<String, StubMapping> stubs = new ConcurrentHashMap<>();
    private final Map<String, List<LoggedRequest>> requestLogs = new ConcurrentHashMap<>();

    // Stub 预索引系统：性能优化，从 O(n) 降至 O(log n)
    private final StubIndex stubIndex = new StubIndex();

    // 并发控制锁：保护关键操作的线程安全
    private final Lock reloadLock = new ReentrantLock();
    private final Lock modifyLock = new ReentrantLock();

    /**
     * -- GETTER --
     * 获取WireMock服务器状态
     */
    @Getter
    private volatile boolean isRunning = false;

    // 内部 WireMockServer，用于实际匹配与生成响应
    private WireMockServer wireMockServer;
    private DirectCallHttpServer directCallServer;
    private int port;

    @PostConstruct
    public void initialize() {
        try {
            startServer();
            port = serverPort;

            warmupPerformanceCaches();

            log.info("WireMock 集成: 内部 WireMockServer 端口={}, 应用端口={}", wireMockServer.port(), port);
            log.info("所有非管理请求将代理到内部 WireMockServer 进行匹配");
            log.info("性能优化组件已创建，使用延迟初始化");
        } catch (Exception e) {
            log.error("WireMock 初始化失败", e);
            throw new RuntimeException("WireMock 初始化失败", e);
        }
    }

    /**
     * 预热性能优化缓存
     * 启动时初始化各种缓存，提高首次请求性能
     */
    private void warmupPerformanceCaches() {
        try {
            // 1. 预编译常用正则表达式
            RegexCache.warmupCommonPatterns();

            // 2. 预解析常用JSON结构
            JsonCache.warmupCommonJsons();

            // 3. 预分配常用大小的缓冲区
            ZeroCopyBuffer.warmupBufferPool();

            log.debug("性能缓存预热完成");
        } catch (Exception e) {
            // 缓存预热失败不影响核心功能，只记录警告
            log.warn("性能缓存预热失败，使用延迟初始化: {}", e.getMessage());
        }
    }

    private synchronized void startServer() throws IllegalAccessException {
        // 启动内部 WireMockServer（动态端口），用于匹配；应用端口仍为 serverPort

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

    /**
     * 获取WireMock服务器端口（实际就是Spring Boot端口）
     */
    public int getPort() {
        return isRunning ? port : 0;
    }

    /**
     * 处理 HTTP 请求 - 直接在当前进程中处理
     * 这个方法替代了原来的代理模式，现在直接匹配并返回响应
     * 零网络开销，高性能
     */
    public void handleRequest(jakarta.servlet.http.HttpServletRequest servletRequest,
                              jakarta.servlet.http.HttpServletResponse servletResponse)
            throws IOException {
        if (!isRunning()) {
            servletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            servletResponse.getWriter().write("WireMock server is not running");
            return;
        }

        // 性能监控：记录请求开始时间
        long requestStartTime = performanceMonitor.startRequest();
        boolean requestSuccess = false;

        try {
            ensureWireMockServerStarted();

            // 1. 将 Undertow 请求转换为 WireMock 的 Request 对象
            performanceMonitor.recordOperation("toWireMockRequest");
            Request wiremockRequest = requestConverter.convert(servletRequest);

            // 2. 【核心】根据请求类型，决定调用 stubRequest 还是 adminRequest
            Response wiremockResponse;
            if (wiremockRequest.getUrl().startsWith("/__admin")) {
                performanceMonitor.recordOperation("adminRequest");
                wiremockResponse = directCallServer.adminRequest(wiremockRequest);
            } else {
                performanceMonitor.recordOperation("stubRequest");
                wiremockResponse = directCallServer.stubRequest(wiremockRequest);
            }

            // 3. 将 WireMock 的 Response 对象转换并写回 Undertow 的 exchange
            performanceMonitor.recordOperation("fromWireMockResponse");
            responseConverter.convert(wiremockResponse, servletResponse);

            requestSuccess = true;

        } catch (Exception e) {
            log.error("处理WireMock请求时出错", e);
            servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setCharacterEncoding("UTF-8");
            servletResponse.getWriter()
                    .write("{\"error\": \"Internal server error\", \"message\": \"" + e.getMessage() + "\"}");
        } finally {
            // 性能监控：记录请求完成
            performanceMonitor.endRequest(requestStartTime, requestSuccess);
        }
    }

    /**
     * 添加Stub Mapping
     * 使用 ConcurrentHashMap 的 put 操作，O(1) 时间复杂度，性能优于 CopyOnWriteArrayList
     */
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

            // 确保 stub 有 UUID（用于 WireMock server 的内部管理）
            if (stubMapping.getUuid() == null || stubMapping.getUuid().trim().isEmpty()) {
                // 生成新的 UUID 并设置到 stub 中，确保删除时可以精确匹配
                stubMapping.setUuid(java.util.UUID.randomUUID().toString());
            }

            // 生成存储 key：优先使用 UUID
            String stubKey = stubMapping.getUuid();

            // 使用 Map 存储，O(1) 时间复杂度
            stubs.put(stubKey, stubMapping);

            // 添加到预索引系统：性能优化从 O(n) 降至 O(log n)
            stubIndex.addStub(stubMapping);

            // 确保内部 WireMockServer 可用（部分单测可能未调用 initialize）
            ensureWireMockServerStarted();

            // 转换并注册到内部 WireMockServer
            MappingBuilder builder = stubMappingConverter.convert(stubMapping);
            wireMockServer.stubFor(builder);

            log.debug("已注册 stub 到 WireMock server: {}", stubMapping.getUrl());

            log.info("已添加Stub Mapping: {} ({} {}) [uuid={}]",
                    stubMapping.getName(),
                    stubMapping.getMethod(),
                    stubMapping.getUrl(),
                    stubKey);
        } catch (Exception e) {
            log.error("添加Stub Mapping失败: {}", stubMapping.getName(), e);
            throw new RuntimeException("添加Stub Mapping失败", e);
        } finally {
            modifyLock.unlock();
        }
    }

    /**
     * 生成 Stub 的唯一 key
     * 优先使用 UUID，其次使用 id
     */
    private String generateStubKey(StubMapping stubMapping) {
        if (stubMapping.getUuid() != null && !stubMapping.getUuid().trim().isEmpty()) {
            return stubMapping.getUuid();
        }
        if (stubMapping.getId() != null) {
            return "id-" + stubMapping.getId();
        }
        return null;
    }

    /**
     * 删除Stub Mapping
     * 使用 ConcurrentHashMap 的 remove 操作，O(1) 时间复杂度，性能优于 CopyOnWriteArrayList 的 O(n) 操作
     */
    public void removeStubMapping(StubMapping stubMapping) {
        modifyLock.lock();
        try {
            if (!isRunning()) {
                return;
            }

            String stubKey = stubMapping.getUuid();
            if (stubKey == null || stubKey.trim().isEmpty()) {
                // 如果没有 UUID，尝试使用 generateStubKey（向后兼容）
                stubKey = generateStubKey(stubMapping);
            }

            boolean removed = false;

            if (stubKey != null && stubs.containsKey(stubKey)) {
                // 从 Map 中删除
                StubMapping removedStub = stubs.remove(stubKey);
                if (removedStub != null) {
                    // 从预索引系统中删除
                    stubIndex.removeStub(removedStub);

                    // 从 WireMock server 中删除
                    try {
                        wireMockServer.removeStubMapping(java.util.UUID.fromString(stubKey));
                        removed = true;
                        log.info("已从 WireMock server 中删除 stub: {}", stubKey);
                    } catch (IllegalArgumentException e) {
                        log.warn("Stub UUID 不是有效格式: {}", stubKey);
                        // 如果 UUID 无效，重载所有剩余的 stubs（不包含当前已删除的）
                        ensureWireMockServerStarted();
                        wireMockServer.resetMappings();
                        for (StubMapping s : stubs.values()) {
                            wireMockServer.stubFor(stubMappingConverter.convert(s));
                        }
                        removed = true;
                        log.info("UUID 无效，已重载剩余的 {} 个 stubs", stubs.size());
                    }
                }
            } else {
                // 兜底方案：尝试根据 UUID 或 URL+Method 匹配删除
                final String finalStubKey = stubKey;
                removed = stubs.entrySet().removeIf(entry -> {
                    StubMapping s = entry.getValue();
                    // 优先匹配 UUID，其次匹配 URL+Method
                    if (finalStubKey != null) {
                        return finalStubKey.equals(s.getUuid());
                    }
                    return s.getUrl().equals(stubMapping.getUrl())
                           && s.getMethod().equalsIgnoreCase(stubMapping.getMethod());
                });
                if (removed) {
                    // 如果通过 URL+Method 删除，需要重载所有剩余的 stubs
                    ensureWireMockServerStarted();
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
        } catch (Exception e) {
            log.error("删除Stub Mapping失败: {}", stubMapping.getName(), e);
            throw new RuntimeException("删除Stub Mapping失败", e);
        } finally {
            modifyLock.unlock();
        }
    }

    /**
     * 重新加载所有Stub Mappings
     * 清空所有旧的，然后重新添加启用的 stubs
     */
    public void reloadAllStubs(List<StubMapping> newStubs) {
        reloadLock.lock();
        try {
            if (!isRunning()) {
                return;
            }

            // 清空现有 stubs - ConcurrentHashMap.clear() 是原子操作，性能优于 CopyOnWriteArrayList
            stubs.clear();
            stubIndex.clear();  // 清空预索引
            ensureWireMockServerStarted();
            wireMockServer.resetMappings(); // 清空所有旧的

            // 重新一个个添加启用的 stubs
            newStubs.stream()
                    .filter(stub -> stub.getEnabled() != null && stub.getEnabled())
                    .forEach(this::addStubMapping);

            log.info("已重新加载所有Stub Mappings，数量: {}", stubs.size());
        } catch (Exception e) {
            log.error("重新加载Stub Mappings失败", e);
            throw new RuntimeException("重新加载Stub Mappings失败", e);
        } finally {
            reloadLock.unlock();
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
        stubIndex.clear();  // 清空预索引
        requestLogs.clear();
        // 清理内部WireMockServer的所有stub
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
        log.info("WireMock服务器已重置");
    }

    /**
     * 获取 Stub 预索引统计信息
     * 用于性能监控和调试
     */
    public StubIndex.IndexStats getIndexStats() {
        return stubIndex.getStats();
    }

    /**
     * 获取性能统计信息
     * 包含请求计数、响应时间、吞吐量等关键指标
     */
    public PerformanceMonitor.PerformanceStats getPerformanceStats() {
        return performanceMonitor.getStats();
    }

    /**
     * 执行性能基准测试
     */
    public PerformanceMonitor.BenchmarkResult runBenchmark(int requestCount, Runnable requestExecutor) {
        return performanceMonitor.runBenchmark(requestCount, requestExecutor);
    }

    /**
     * 重置所有统计信息
     */
    public void resetStats() {
        performanceMonitor.reset();
        // 清空性能优化缓存
        RegexCache.clear();
        JsonCache.clear();
        ZeroCopyBuffer.clear();
        log.debug("所有性能统计和缓存已重置");
    }

    /**
     * 获取性能优化统计
     * 包含正则缓存、JSON缓存、零拷贝缓冲区统计
     */
    public PerformanceOptimizationStats getOptimizationStats() {
        return PerformanceOptimizationStats.builder()
            .regexStats(RegexCache.getStats())
            .jsonStats(JsonCache.getStats())
            .bufferStats(ZeroCopyBuffer.getStats())
            .indexStats(stubIndex.getStats())
            .build();
    }

    /**
     * 获取所有 stubs
     * 使用 Map.values() 替代 List，时间复杂度从 O(n) 降低到 O(1)
     */
    public List<StubMapping> getAllStubs() {
        return new ArrayList<>(stubs.values());
    }

    private void ensureWireMockServerStarted() throws IllegalAccessException {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            startServer();
        }
    }

    /**
     * 性能优化统计信息
     * 整合所有性能优化组件的统计数据
     */
    public static class PerformanceOptimizationStats {
        private final RegexCache.CacheStats regexStats;
        private final JsonCache.CacheStats jsonStats;
        private final ZeroCopyBuffer.BufferStats bufferStats;
        private final StubIndex.IndexStats indexStats;

        private PerformanceOptimizationStats(Builder builder) {
            this.regexStats = builder.regexStats;
            this.jsonStats = builder.jsonStats;
            this.bufferStats = builder.bufferStats;
            this.indexStats = builder.indexStats;
        }

        public static Builder builder() {
            return new Builder();
        }

        public RegexCache.CacheStats getRegexStats() {
            return regexStats;
        }

        public JsonCache.CacheStats getJsonStats() {
            return jsonStats;
        }

        public ZeroCopyBuffer.BufferStats getBufferStats() {
            return bufferStats;
        }

        public StubIndex.IndexStats getIndexStats() {
            return indexStats;
        }

        @Override
        public String toString() {
            return String.format(
                "PerformanceOptimizationStats{\n" +
                "  正则缓存: %s,\n" +
                "  JSON缓存: %s,\n" +
                "  零拷贝缓冲区: %s,\n" +
                "  预索引: %s\n" +
                "}",
                regexStats, jsonStats, bufferStats, indexStats
            );
        }

        public static class Builder {
            private RegexCache.CacheStats regexStats;
            private JsonCache.CacheStats jsonStats;
            private ZeroCopyBuffer.BufferStats bufferStats;
            private StubIndex.IndexStats indexStats;

            public Builder regexStats(RegexCache.CacheStats regexStats) {
                this.regexStats = regexStats;
                return this;
            }

            public Builder jsonStats(JsonCache.CacheStats jsonStats) {
                this.jsonStats = jsonStats;
                return this;
            }

            public Builder bufferStats(ZeroCopyBuffer.BufferStats bufferStats) {
                this.bufferStats = bufferStats;
                return this;
            }

            public Builder indexStats(StubIndex.IndexStats indexStats) {
                this.indexStats = indexStats;
                return this;
            }

            public PerformanceOptimizationStats build() {
                return new PerformanceOptimizationStats(this);
            }
        }
    }
}
