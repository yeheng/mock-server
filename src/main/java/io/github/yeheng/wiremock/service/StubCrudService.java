package io.github.yeheng.wiremock.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;

import io.github.yeheng.wiremock.entity.StubMapping;
import lombok.extern.slf4j.Slf4j;

/**
 * 负责 StubMapping 的 CRUD 与 WireMock 注册/删除
 * 与请求处理解耦
 */
@Slf4j
@Service
public class StubCrudService {

    private final Map<String, StubMapping> stubs = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 清空所有 stubs（仅清内存，不触碰 server） */
    public void clear() {
        stubs.clear();
    }

    public List<StubMapping> getAllStubs() {
        return new ArrayList<>(stubs.values());
    }

    public void addStubMapping(StubMapping stubMapping, WireMockServer wireMockServer) {
        if (stubMapping.getEnabled() == null || !stubMapping.getEnabled()) {
            log.debug("Stub 已禁用，跳过: {}", stubMapping.getName());
            return;
        }

        if (stubMapping.getUuid() == null || stubMapping.getUuid().trim().isEmpty()) {
            stubMapping.setUuid(java.util.UUID.randomUUID().toString());
        }

        String stubKey = stubMapping.getUuid();
        stubs.put(stubKey, stubMapping);

        MappingBuilder builder = toWireMockMapping(stubMapping);
        wireMockServer.stubFor(builder);

        log.info("已添加Stub Mapping: {} ({} {}) [uuid={}]",
                stubMapping.getName(), stubMapping.getMethod(), stubMapping.getUrl(), stubKey);
    }

    public void removeStubMapping(StubMapping stubMapping, WireMockServer wireMockServer) {
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
                    wireMockServer.resetMappings();
                    for (StubMapping s : stubs.values()) {
                        wireMockServer.stubFor(toWireMockMapping(s));
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
    }

    public void reloadAllStubs(List<StubMapping> newStubs, WireMockServer wireMockServer) {
        stubs.clear();
        wireMockServer.resetMappings();

        newStubs.stream()
                .filter(stub -> stub.getEnabled() != null && stub.getEnabled())
                .forEach(stub -> addStubMapping(stub, wireMockServer));

        log.info("已重新加载所有Stub Mappings，数量: {}", stubs.size());
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

    // 将实体 StubMapping 转换为 WireMock 的 MappingBuilder
    private MappingBuilder toWireMockMapping(StubMapping stub) {
        UrlPattern urlPattern;
        String url = stub.getUrl();
        urlPattern = switch (stub.getUrlMatchType()) {
            case EQUALS -> WireMock.urlPathEqualTo(url);
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

        // 使用实体 UUID 作为 WireMock 映射 ID，确保删除/禁用时精确移除
        String uuid = stub.getUuid();
        if (uuid != null && !uuid.trim().isEmpty()) {
            try {
                builder = builder.withId(java.util.UUID.fromString(uuid));
            } catch (IllegalArgumentException ignore) {
                // 非法 UUID 跳过设置，删除逻辑有兜底重载
            }
        }

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
                try {
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

    private String createDefaultResponse(StubMapping stub) {
        return String.format(
                "{\"message\": \"Mocked response for %s\", \"stubName\": \"%s\", \"timestamp\": \"%s\"}",
                stub.getName(),
                stub.getName(),
                java.time.LocalDateTime.now());
    }
}