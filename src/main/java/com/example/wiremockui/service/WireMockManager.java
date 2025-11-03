package com.example.wiremockui.service;

import com.example.wiremockui.entity.StubMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServer;
import com.github.tomakehurst.wiremock.direct.DirectCallHttpServerFactory;
import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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

    // 使用内存存储 stub mappings，使用 ConcurrentHashMap 替代 CopyOnWriteArrayList 以提升并发性能
    // key: UUID (如果有) 或 id (Long) 转字符串，value: StubMapping 对象
    private final Map<String, StubMapping> stubs = new ConcurrentHashMap<>();
    private final Map<String, List<LoggedRequest>> requestLogs = new ConcurrentHashMap<>();

    /**
     * -- GETTER --
     * 获取WireMock服务器状态
     */
    @Getter
    private volatile boolean isRunning = false;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内部 WireMockServer，用于实际匹配与生成响应
    private WireMockServer wireMockServer;
    private DirectCallHttpServer directCallServer;

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

    private int port;

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

        try {
            ensureWireMockServerStarted();

            // 1. 将 Undertow 请求转换为 WireMock 的 Request 对象
            Request wiremockRequest = toWireMockRequest(servletRequest);

            // 2. 【核心】在这里，我们调用的是 DirectCallHttpServer 上的 stubRequest 方法！
            Response wiremockResponse = directCallServer.stubRequest(wiremockRequest);

            // 3. 将 WireMock 的 Response 对象转换并写回 Undertow 的 exchange
            fromWireMockResponse(wiremockResponse, servletResponse);


        } catch (Exception e) {
            log.error("处理WireMock请求时出错", e);
            servletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setCharacterEncoding("UTF-8");
            servletResponse.getWriter()
                    .write("{\"error\": \"Internal server error\", \"message\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 将 WireMock Response 转换并写入 Servlet Response
     */
    private void fromWireMockResponse(Response wiremockResponse, HttpServletResponse servletResponse)
            throws IOException {

        // 1. 设置状态码
        servletResponse.setStatus(wiremockResponse.getStatus());

        // 2. 设置响应头
        HttpHeaders headers = wiremockResponse.getHeaders();
        if (headers != null) {
            for (HttpHeader header : headers.all()) {
                String headerName = header.key();
                for (String headerValue : header.values()) {
                    // 使用 addHeader 支持多值头
                    servletResponse.addHeader(headerName, headerValue);
                }
            }
        }

        // 3. 写入响应体
        byte[] body = wiremockResponse.getBody();
        if (body != null && body.length > 0) {
            servletResponse.getOutputStream().write(body);
            servletResponse.getOutputStream().flush();
        } else {
            // 处理空响应体的情况，特别是404错误
            if (wiremockResponse.getStatus() == 404) {
                String jsonBody = "{\"error\": \"No matching stub\", \"status\": 404, \"message\": \"No stub matching the request was found\"}";
                servletResponse.setContentType("application/json;charset=UTF-8");
                servletResponse.setCharacterEncoding("UTF-8");
                servletResponse.getWriter().write(jsonBody);
                servletResponse.getWriter().flush();
            }
        }
    }

    /**
     * 将 Servlet 请求转换为 WireMock Request 对象
     */
    private Request toWireMockRequest(HttpServletRequest servletRequest) throws IOException {
        // 1. 提取请求方法
        RequestMethod method = RequestMethod.fromString(servletRequest.getMethod());

        // 2. 构建绝对 URL
        String absoluteUrl = servletRequest.getRequestURL().toString();
        String queryString = servletRequest.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            absoluteUrl = absoluteUrl + "?" + queryString;
        }

        // 3. 提取 Headers - 转换为 WireMock 的 HttpHeaders
        List<HttpHeader> headerList = new ArrayList<>();
        Enumeration<String> headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            Enumeration<String> headerValues = servletRequest.getHeaders(headerName);
            List<String> values = Collections.list(headerValues);
            headerList.add(new HttpHeader(headerName, values));
        }
        HttpHeaders headers = new HttpHeaders(headerList);

        // 4. 提取请求体
        byte[] body;
        try (InputStream inputStream = servletRequest.getInputStream()) {
            body = inputStream.readAllBytes();
        }

        // 5. 获取客户端 IP
        String clientIp = servletRequest.getRemoteAddr();

        // 6. 获取协议
        String protocol = servletRequest.getProtocol();

        // 7. 使用 ImmutableRequest.Builder 构造 Request 对象
        return ImmutableRequest.create()
                .withAbsoluteUrl(absoluteUrl)
                .withMethod(method)
                .withProtocol(protocol)
                .withClientIp(clientIp)
                .withHeaders(headers)
                .withBody(body)
                .withBrowserProxyRequest(false)
                .build();
    }

    /**
     * 添加Stub Mapping
     * 使用 ConcurrentHashMap 的 put 操作，O(1) 时间复杂度，性能优于 CopyOnWriteArrayList
     */
    public void addStubMapping(@NonNull StubMapping stubMapping) {
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

            // 确保内部 WireMockServer 可用（部分单测可能未调用 initialize）
            ensureWireMockServerStarted();

            // 转换并注册到内部 WireMockServer
            MappingBuilder builder = toWireMockMapping(stubMapping);
            wireMockServer.stubFor(builder);

            log.info("已添加Stub Mapping: {} ({} {}) [uuid={}]",
                    stubMapping.getName(),
                    stubMapping.getMethod(),
                    stubMapping.getUrl(),
                    stubKey);
        } catch (Exception e) {
            log.error("添加Stub Mapping失败: {}", stubMapping.getName(), e);
            throw new RuntimeException("添加Stub Mapping失败", e);
        }
    }

    /**
     * 生成 Stub 的唯一 key
     * 优先使用 UUID，其次使用 id
     */
    private String generateStubKey(@NonNull StubMapping stubMapping) {
        if (stubMapping.getUuid() != null && !stubMapping.getUuid().trim().isEmpty()) {
            return stubMapping.getUuid();
        }
        if (stubMapping.getId() != null) {
            return "id-" + stubMapping.getId();
        }
        return null;
    }

    /**
     * 生成兜底唯一 key（当 UUID 和 id 都不可用时）
     */
    private String generateFallbackKey() {
        return "temp-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID();
    }

    /**
     * 删除Stub Mapping
     * 使用 ConcurrentHashMap 的 remove 操作，O(1) 时间复杂度，性能优于 CopyOnWriteArrayList 的 O(n) 操作
     */
    public void removeStubMapping(@NonNull StubMapping stubMapping) {
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
                            wireMockServer.stubFor(toWireMockMapping(s));
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
                        wireMockServer.stubFor(toWireMockMapping(s));
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
        }
    }

    /**
     * 重新加载所有Stub Mappings
     * 清空所有旧的，然后重新添加启用的 stubs
     */
    public void reloadAllStubs(@NonNull List<StubMapping> newStubs) {
        try {
            if (!isRunning()) {
                return;
            }

            // 清空现有 stubs - ConcurrentHashMap.clear() 是原子操作，性能优于 CopyOnWriteArrayList
            stubs.clear();
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
        // 清理内部WireMockServer的所有stub
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
        log.info("WireMock服务器已重置");
    }

    /**
     * 创建默认响应
     */
    private String createDefaultResponse(@NonNull StubMapping stub) {
        return String.format(
                "{\"message\": \"Mocked response for %s\", \"stubName\": \"%s\", \"timestamp\": \"%s\"}",
                stub.getName(),
                stub.getName(),
                java.time.LocalDateTime.now());
    }

    /**
     * 获取所有 stubs
     * 使用 Map.values() 替代 List，时间复杂度从 O(n) 降低到 O(1)
     */
    public List<StubMapping> getAllStubs() {
        return new ArrayList<>(stubs.values());
    }

    // 将实体 StubMapping 转换为 WireMock 的 MappingBuilder
    private MappingBuilder toWireMockMapping(@NonNull StubMapping stub) {
        UrlPattern urlPattern;
        String url = stub.getUrl();
        urlPattern = switch (stub.getUrlMatchType()) {
            case EQUALS ->
                // 使用 urlPathEqualTo 仅匹配路径部分，不包括查询字符串
                    WireMock.urlPathEqualTo(url);
            case CONTAINS -> WireMock.urlMatching(".*" + java.util.regex.Pattern.quote(url) + ".*");
            case REGEX -> WireMock.urlMatching(url);
            case PATH_TEMPLATE -> {
                String regex = url.replaceAll("\\{[^}]+}", "[^/]+");
                log.debug("路径模板转换: {} -> 正则: {}", url, regex);
                yield WireMock.urlPathMatching("^" + regex + "$");
            }
        };

        RequestMethod method;
        try {
            method = RequestMethod.fromString(stub.getMethod() != null ? stub.getMethod().toUpperCase() : "ANY");
        } catch (Exception e) {
            method = RequestMethod.ANY;
        }

        MappingBuilder builder = WireMock.request(method.getName(), urlPattern)
                .atPriority(stub.getPriority() != null ? stub.getPriority() : 0);

        // Headers
        String headersPattern = stub.getRequestHeadersPattern();
        if (headersPattern != null && !headersPattern.trim().isEmpty()) {
            try {
                JsonNode headerPatterns = objectMapper.readTree(headersPattern);
                Iterator<Map.Entry<String, JsonNode>> fields = headerPatterns.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String name = entry.getKey();
                    JsonNode rule = entry.getValue();
                    if (rule.has("equalTo")) {
                        builder = builder.withHeader(name, WireMock.equalTo(rule.get("equalTo").asText()));
                    } else if (rule.has("contains")) {
                        builder = builder.withHeader(name, WireMock.containing(rule.get("contains").asText()));
                    } else if (rule.has("matches")) {
                        builder = builder.withHeader(name, WireMock.matching(rule.get("matches").asText()));
                    }
                }
            } catch (Exception ignore) {
                // 宽松处理：不阻塞注册
            }
        }

        // Query params
        String queryParamsPattern = stub.getQueryParametersPattern();
        if (queryParamsPattern != null && !queryParamsPattern.trim().isEmpty()) {
            try {
                JsonNode paramPatterns = objectMapper.readTree(queryParamsPattern);
                Iterator<Map.Entry<String, JsonNode>> fields = paramPatterns.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    String name = entry.getKey();
                    JsonNode rule = entry.getValue();
                    if (rule.has("equalTo")) {
                        builder = builder.withQueryParam(name, WireMock.equalTo(rule.get("equalTo").asText()));
                    } else if (rule.has("contains")) {
                        builder = builder.withQueryParam(name, WireMock.containing(rule.get("contains").asText()));
                    } else if (rule.has("matches")) {
                        builder = builder.withQueryParam(name, WireMock.matching(rule.get("matches").asText()));
                    }
                }
            } catch (Exception ignore) {
            }
        }

        // Request body
        String bodyPattern = stub.getRequestBodyPattern();
        if (bodyPattern != null && !bodyPattern.trim().isEmpty()) {
            try {
                // 尝试直接解析JSON
                JsonNode bodyRule = objectMapper.readTree(bodyPattern);
                if (bodyRule.has("equalToJson")) {
                    builder = builder
                            .withRequestBody(new EqualToJsonPattern(bodyRule.get("equalToJson").asText(), true, true));
                    log.debug("添加请求体匹配: equalToJson");
                } else if (bodyRule.has("matchesJsonPath")) {
                    builder = builder
                            .withRequestBody(WireMock.matchingJsonPath(bodyRule.get("matchesJsonPath").asText()));
                    log.debug("添加请求体匹配: matchesJsonPath");
                } else if (bodyRule.has("contains")) {
                    String containsText = bodyRule.get("contains").asText();
                    builder = builder.withRequestBody(WireMock.containing(containsText));
                    log.debug("添加请求体匹配: contains={}", containsText);
                } else if (bodyRule.has("matches")) {
                    String regex = bodyRule.get("matches").asText();
                    builder = builder.withRequestBody(WireMock.matching(regex));
                    log.debug("添加请求体正则匹配: regex={}", regex);
                }
            } catch (com.fasterxml.jackson.core.JsonParseException e) {
                // JSON解析失败，尝试修复常见的转义问题后重试
                try {
                    // 将单个反斜杠转换为双反斜杠（JSON转义）
                    String fixedPattern = bodyPattern.replace("\\", "\\\\");
                    JsonNode bodyRule = objectMapper.readTree(fixedPattern);
                    if (bodyRule.has("matches")) {
                        String regex = bodyRule.get("matches").asText();
                        builder = builder.withRequestBody(WireMock.matching(regex));
                        log.debug("添加请求体正则匹配（修复转义后）: regex={}", regex);
                    } else if (bodyRule.has("contains")) {
                        builder = builder.withRequestBody(WireMock.containing(bodyRule.get("contains").asText()));
                    }
                } catch (Exception ex) {
                    log.warn("转义解析 json请求体匹配模式失败，使用包含匹配作为兜底: pattern={}, error={}", bodyPattern, ex.getMessage());
                    builder = builder.withRequestBody(WireMock.containing(bodyPattern));
                }
            } catch (Exception e) {
                // 其他异常：作为包含匹配
                log.warn("解析请求体匹配模式失败，使用包含匹配作为兜底: pattern={}, error={}", bodyPattern, e.getMessage());
                builder = builder.withRequestBody(WireMock.containing(bodyPattern));
            }
        }

        // Response
        String responseBody = stub.getResponseDefinition();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            responseBody = createDefaultResponse(stub);
        }
        builder = builder.willReturn(
                WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=UTF-8")
                        .withBody(responseBody));

        return builder;
    }

    private void ensureWireMockServerStarted() throws IllegalAccessException {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            startServer();
        }
    }
}
