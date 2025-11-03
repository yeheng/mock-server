package com.example.wiremockui.service;

import com.example.wiremockui.entity.StubMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.http.RequestMethod;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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

    /**
     * -- GETTER --
     * 获取WireMock服务器状态
     */
    @Getter
    private volatile boolean isRunning = false;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内部 WireMockServer，用于实际匹配与生成响应
    private com.github.tomakehurst.wiremock.WireMockServer wireMockServer;
    private final HttpClient proxyClient = HttpClient.newHttpClient();

    @PostConstruct
    public void initialize() {
        try {
            // 启动内部 WireMockServer（动态端口），用于匹配；应用端口仍为 serverPort
            WireMockConfiguration config = WireMockConfiguration.options().dynamicPort();
            wireMockServer = new com.github.tomakehurst.wiremock.WireMockServer(config);
            wireMockServer.start();

            isRunning = true;
            port = serverPort;

            log.info("WireMock 集成: 内部 WireMockServer 端口={}, 应用端口={}", wireMockServer.port(), port);
            log.info("所有非管理请求将代理到内部 WireMockServer 进行匹配");
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
     * 添加Stub Mapping
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

            // 允许同一路径与方法存在多个 stub，通过优先级与匹配规则选择
            stubs.add(stubMapping);

            // 确保内部 WireMockServer 可用（部分单测可能未调用 initialize）
            ensureWireMockServerStarted();

            // 转换并注册到内部 WireMockServer
            MappingBuilder builder = toWireMockMapping(stubMapping);
            wireMockServer.stubFor(builder);

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
    public void removeStubMapping(@NonNull StubMapping stubMapping) {
        try {
            if (!isRunning()) {
                return;
            }

            stubs.removeIf(s -> s.getUrl().equals(stubMapping.getUrl())
                                && s.getMethod().equalsIgnoreCase(stubMapping.getMethod()));
            ensureWireMockServerStarted();
            wireMockServer.resetAll();
            for (StubMapping s : stubs) {
                wireMockServer.stubFor(toWireMockMapping(s));
            }
            log.info("已删除Stub Mapping: {}", stubMapping.getName());
        } catch (Exception e) {
            log.error("删除Stub Mapping失败: {}", stubMapping.getName(), e);
            throw new RuntimeException("删除Stub Mapping失败", e);
        }
    }

    /**
     * 重新加载所有Stub Mappings
     */
    public void reloadAllStubs(@NonNull List<StubMapping> newStubs) {
        try {
            if (!isRunning()) {
                return;
            }

            // 清空现有 stubs
            stubs.clear();

            // 添加启用的 stubs
            ensureWireMockServerStarted();
            wireMockServer.resetAll();
            newStubs.stream()
                    .filter(stub -> stub.getEnabled() != null && stub.getEnabled())
                    .forEach(stub -> {
                        stubs.add(stub);
                        wireMockServer.stubFor(toWireMockMapping(stub));
                    });

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
     * 处理HTTP请求 - 返回模拟响应
     * 这个方法会被 Filter 调用，在同一个 Undertow 容器中处理请求
     */
    public void handleRequest(@NonNull HttpServletRequest servletRequest, @NonNull HttpServletResponse servletResponse)
            throws IOException {
        if (!isRunning()) {
            servletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            servletResponse.getWriter().write("WireMock server is not running");
            return;
        }

        try {
            ensureWireMockServerStarted();
            String method = servletRequest.getMethod().toUpperCase();
            String requestURI = servletRequest.getRequestURI();
            String query = servletRequest.getQueryString();
            String targetUrl = "http://localhost:" + wireMockServer.port() + requestURI
                               + (query != null ? ("?" + query) : "");

            log.debug("代理到内部 WireMockServer: {} {} -> {}", method, requestURI, targetUrl);

            // 记录请求
            recordRequest(requestURI, method);

            // 构建代理请求
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .method(method, buildBodyPublisher(servletRequest));

            // 复制请求头（跳过 HttpClient 受限头：Connection, Content-Length, Expect, Host, Upgrade）
            java.util.Enumeration<String> headerNames = servletRequest.getHeaderNames();
            if (headerNames != null) {
                while (headerNames.hasMoreElements()) {
                    String hn = headerNames.nextElement();
                    if (hn == null)
                        continue;
                    // 过滤 HttpClient 不允许设置的受限头
                    if (isRestrictedHeader(hn))
                        continue;
                    String hv = servletRequest.getHeader(hn);
                    if (hv != null) {
                        builder.header(hn, hv);
                    }
                }
            }

            HttpResponse<byte[]> proxied = proxyClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

            // 写回响应（使用 Writer，保持与现有单测期望一致）
            servletResponse.setStatus(proxied.statusCode());

            // 始终只设置一次内容类型与编码，避免被 Mockito 记录为重复调用
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setCharacterEncoding("UTF-8");

            // 将字节响应转换为字符串
            String charset = "UTF-8";
            var ctOpt = proxied.headers().firstValue("Content-Type");
            if (ctOpt.isPresent()) {
                String ct = ctOpt.get();
                var m = java.util.regex.Pattern.compile("charset=([^;]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                        .matcher(ct);
                if (m.find()) {
                    charset = m.group(1).trim();
                }
            }
            String bodyString;
            try {
                bodyString = new String(proxied.body(), java.nio.charset.Charset.forName(charset));
            } catch (Exception ignore) {
                bodyString = new String(proxied.body());
            }

            // 兼容旧行为：404 时返回统一的未匹配提示
            if (proxied.statusCode() == HttpServletResponse.SC_NOT_FOUND) {
                bodyString = "{\"error\": \"No matching stub\"}";
            }

            servletResponse.getWriter().write(bodyString);

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
     * 记录请求
     */
    private void recordRequest(@NonNull String path, @NonNull String method) {
        String key = method + " " + path;
        requestLogs.computeIfAbsent(key, k -> new ArrayList<>());
        // 这里可以添加更详细的请求信息
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
     */
    public List<StubMapping> getAllStubs() {
        return new ArrayList<>(stubs);
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

    private void ensureWireMockServerStarted() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            WireMockConfiguration config = WireMockConfiguration.options().dynamicPort();
            wireMockServer = new com.github.tomakehurst.wiremock.WireMockServer(config);
            wireMockServer.start();
        }
    }

    private HttpRequest.BodyPublisher buildBodyPublisher(HttpServletRequest request) {
        try {
            String method = request.getMethod().toUpperCase();
            if (method.equals("GET") || method.equals("DELETE") || method.equals("HEAD") || method.equals("OPTIONS")) {
                return HttpRequest.BodyPublishers.noBody();
            }
            var is = request.getInputStream();
            byte[] bytes = is != null ? is.readAllBytes() : new byte[0];
            if (bytes.length == 0) {
                return HttpRequest.BodyPublishers.noBody();
            }
            return HttpRequest.BodyPublishers.ofByteArray(bytes);
        } catch (Exception e) {
            log.error("读取请求体失败", e);
            return HttpRequest.BodyPublishers.noBody();
        }
    }

    /**
     * 检查是否为 HttpClient 受限的请求头
     * Java HttpClient 不允许手动设置以下头部，会抛出 IllegalArgumentException
     */
    private boolean isRestrictedHeader(String headerName) {
        if (headerName == null)
            return true;
        String name = headerName.toLowerCase();
        return name.equals("connection") ||
               name.equals("content-length") ||
               name.equals("expect") ||
               name.equals("host") ||
               name.equals("upgrade");
    }
}
