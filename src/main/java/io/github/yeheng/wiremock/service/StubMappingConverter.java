package io.github.yeheng.wiremock.service;

import java.util.Iterator;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.EqualToJsonPattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;

import io.github.yeheng.wiremock.entity.StubMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * StubMapping 到 WireMock MappingBuilder 的转换器
 * 单一职责：将实体转换为 WireMock 可用的映射规则
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StubMappingConverter {

    private final ObjectMapper objectMapper;

    /**
     * 将 StubMapping 转换为 WireMock 的 MappingBuilder
     */
    public MappingBuilder convert(StubMapping stub) {
        // 1. 构建基础请求匹配（URL + 方法 + 优先级）
        MappingBuilder builder = buildBaseRequest(stub);

        // 2. 设置 UUID
        builder = setUuid(builder, stub);

        // 3. 添加请求头匹配
        builder = addHeaderMatching(builder, stub);

        // 4. 添加查询参数匹配
        builder = addQueryParamMatching(builder, stub);

        // 5. 添加请求体匹配
        builder = addBodyMatching(builder, stub);

        // 6. 设置响应
        builder = setResponse(builder, stub);

        return builder;
    }

    /**
     * 构建基础请求匹配（URL + 方法 + 优先级）
     */
    private MappingBuilder buildBaseRequest(StubMapping stub) {
        UrlPattern urlPattern = buildUrlPattern(stub);
        RequestMethod method = buildRequestMethod(stub);
        int priority = stub.getPriority() != null ? stub.getPriority() : 0;

        return WireMock.request(method.getName(), urlPattern).atPriority(priority);
    }

    /**
     * 构建 URL 匹配模式
     * 使用 RegexCache 缓存正则表达式编译结果，提升性能
     */
    private UrlPattern buildUrlPattern(StubMapping stub) {
        String url = stub.getUrl();
        return switch (stub.getUrlMatchType()) {
            case EQUALS -> WireMock.urlPathEqualTo(url);  // 使用 urlPathEqualTo 仅匹配路径部分，不包括查询字符串
            case CONTAINS -> {
                String regex = ".*" + RegexCache.escapeRegex(url) + ".*";
                yield WireMock.urlMatching(RegexCache.getOrCreate(regex).pattern());
            }
            case REGEX -> WireMock.urlMatching(RegexCache.getOrCreate(url).pattern());
            case PATH_TEMPLATE -> {
                String regex = RegexCache.convertPathTemplateToRegex(url);
                String fullRegex = "^" + regex + "$";
                log.debug("路径模板转换: {} -> 正则: {}", url, fullRegex);
                yield WireMock.urlPathMatching(RegexCache.getOrCreate(fullRegex).pattern());
            }
        };
    }

    /**
     * 构建请求方法
     */
    private RequestMethod buildRequestMethod(StubMapping stub) {
        try {
            String methodStr = stub.getMethod() != null ? stub.getMethod().toUpperCase() : "ANY";
            return RequestMethod.fromString(methodStr);
        } catch (Exception e) {
            log.debug("无效的请求方法，使用 ANY: {}", stub.getMethod());
            return RequestMethod.ANY;
        }
    }

    /**
     * 设置 UUID 到 MappingBuilder
     * 用于保证删除/禁用时可精确移除
     */
    private MappingBuilder setUuid(MappingBuilder builder, StubMapping stub) {
        String uuid = stub.getUuid();
        if (uuid != null && !uuid.trim().isEmpty()) {
            try {
                builder = builder.withId(java.util.UUID.fromString(uuid));
            } catch (IllegalArgumentException ignore) {
                // 如果 UUID 非法，跳过设置ID，后续删除逻辑会走全量重载兜底
                log.debug("无效的 UUID 格式，跳过设置: {}", uuid);
            }
        }
        return builder;
    }

    /**
     * 添加请求头匹配规则
     */
    private MappingBuilder addHeaderMatching(MappingBuilder builder, StubMapping stub) {
        String headersPattern = stub.getRequestHeadersPattern();
        if (headersPattern == null || headersPattern.trim().isEmpty()) {
            return builder;
        }

        try {
            JsonNode headerPatterns = objectMapper.readTree(headersPattern);
            Iterator<Map.Entry<String, JsonNode>> fields = headerPatterns.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode rule = entry.getValue();

                // 根据规则类型添加匹配条件
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
            log.debug("解析请求头匹配模式失败，跳过: {}", headersPattern);
        }

        return builder;
    }

    /**
     * 添加查询参数匹配规则
     */
    private MappingBuilder addQueryParamMatching(MappingBuilder builder, StubMapping stub) {
        String queryParamsPattern = stub.getQueryParametersPattern();
        if (queryParamsPattern == null || queryParamsPattern.trim().isEmpty()) {
            return builder;
        }

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
            log.debug("解析查询参数匹配模式失败，跳过: {}", queryParamsPattern);
        }

        return builder;
    }

    /**
     * 添加请求体匹配规则
     * 包含多种匹配策略和错误恢复机制
     */
    private MappingBuilder addBodyMatching(MappingBuilder builder, StubMapping stub) {
        String bodyPattern = stub.getRequestBodyPattern();
        if (bodyPattern == null || bodyPattern.trim().isEmpty()) {
            return builder;
        }

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

        return builder;
    }

    /**
     * 设置响应定义
     */
    private MappingBuilder setResponse(MappingBuilder builder, StubMapping stub) {
        String responseBody = stub.getResponseDefinition();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            responseBody = createDefaultResponse(stub);
        }

        return builder.willReturn(
                WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=UTF-8")
                        .withBody(responseBody));
    }

    /**
     * 创建默认响应
     */
    private String createDefaultResponse(StubMapping stub) {
        return String.format(
                "{\"message\": \"Mocked response for %s\", \"stubName\": \"%s\", \"timestamp\": \"%s\"}",
                stub.getName(),
                stub.getName(),
                java.time.LocalDateTime.now());
    }
}
