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

@Slf4j
@Component
@RequiredArgsConstructor
public class StubMappingConverter {
    private final ObjectMapper objectMapper;
    public MappingBuilder convert(StubMapping stub) {
        var builder = buildBaseRequest(stub);
        setUuid(builder, stub);
        addHeaderMatching(builder, stub);
        addQueryParamMatching(builder, stub);
        addBodyMatching(builder, stub);
        setResponse(builder, stub);
        return builder;
    }

    private MappingBuilder buildBaseRequest(StubMapping stub) {
        UrlPattern urlPattern = buildUrlPattern(stub);
        RequestMethod method = buildRequestMethod(stub);
        int priority = stub.getPriority() != null ? stub.getPriority() : 0;

        return WireMock.request(method.getName(), urlPattern).atPriority(priority);
    }

    private UrlPattern buildUrlPattern(StubMapping stub) {
        String url = stub.getUrl();
        return switch (stub.getUrlMatchType()) {
            case EQUALS -> WireMock.urlPathEqualTo(url);
            case CONTAINS -> {
                String escaped = RegexCache.escapeRegex(url);
                yield WireMock.urlMatching(RegexCache.getPattern(".*" + escaped + ".*").pattern());
            }
            case REGEX -> WireMock.urlMatching(RegexCache.getPattern(url).pattern());
            case PATH_TEMPLATE -> {
                String regex = RegexCache.convertPathTemplateToRegex(url);
                yield WireMock.urlPathMatching(RegexCache.getPattern("^" + regex + "$").pattern());
            }
        };
    }

    private RequestMethod buildRequestMethod(StubMapping stub) {
        try {
            String methodStr = stub.getMethod() != null ? stub.getMethod().toUpperCase() : "ANY";
            return RequestMethod.fromString(methodStr);
        } catch (Exception e) {
            return RequestMethod.ANY;
        }
    }

    private void setUuid(MappingBuilder builder, StubMapping stub) {
        String uuid = stub.getUuid();
        if (uuid != null && !uuid.trim().isEmpty()) {
            try {
                builder.withId(java.util.UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID format: {}", uuid);
            }
        }
    }

    private void addHeaderMatching(MappingBuilder builder, StubMapping stub) {
        String headersPattern = stub.getRequestHeadersPattern();
        if (headersPattern == null || headersPattern.trim().isEmpty()) {
            return;
        }

        try {
            JsonNode headerPatterns = objectMapper.readTree(headersPattern);
            Iterator<Map.Entry<String, JsonNode>> fields = headerPatterns.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode rule = entry.getValue();

                if (rule.has("equalTo")) {
                    builder.withHeader(name, WireMock.equalTo(rule.get("equalTo").asText()));
                } else if (rule.has("contains")) {
                    builder.withHeader(name, WireMock.containing(rule.get("contains").asText()));
                } else if (rule.has("matches")) {
                    builder.withHeader(name, WireMock.matching(rule.get("matches").asText()));
                }
            }
        } catch (Exception e) {
            log.warn("Invalid header pattern: {}", e.getMessage());
        }
    }

    private void addQueryParamMatching(MappingBuilder builder, StubMapping stub) {
        String queryParamsPattern = stub.getQueryParametersPattern();
        if (queryParamsPattern == null || queryParamsPattern.trim().isEmpty()) {
            return;
        }

        try {
            JsonNode paramPatterns = objectMapper.readTree(queryParamsPattern);
            Iterator<Map.Entry<String, JsonNode>> fields = paramPatterns.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode rule = entry.getValue();

                if (rule.has("equalTo")) {
                    builder.withQueryParam(name, WireMock.equalTo(rule.get("equalTo").asText()));
                } else if (rule.has("contains")) {
                    builder.withQueryParam(name, WireMock.containing(rule.get("contains").asText()));
                } else if (rule.has("matches")) {
                    builder.withQueryParam(name, WireMock.matching(rule.get("matches").asText()));
                }
            }
        } catch (Exception e) {
            log.warn("Invalid query param pattern: {}", e.getMessage());
        }
    }

    private void addBodyMatching(MappingBuilder builder, StubMapping stub) {
        String bodyPattern = stub.getRequestBodyPattern();
        if (bodyPattern == null || bodyPattern.trim().isEmpty()) {
            return;
        }

        try {
            JsonNode bodyRule = objectMapper.readTree(bodyPattern);
            if (bodyRule.has("equalToJson")) {
                builder.withRequestBody(new EqualToJsonPattern(bodyRule.get("equalToJson").asText(), true, true));
            } else if (bodyRule.has("matchesJsonPath")) {
                builder.withRequestBody(WireMock.matchingJsonPath(bodyRule.get("matchesJsonPath").asText()));
            } else if (bodyRule.has("contains")) {
                String containsText = bodyRule.get("contains").asText();
                builder.withRequestBody(WireMock.containing(containsText));
            } else if (bodyRule.has("matches")) {
                String regex = bodyRule.get("matches").asText();
                builder.withRequestBody(WireMock.matching(regex));
            }
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            tryFallbackBodyMatching(builder, bodyPattern);
        } catch (Exception e) {
            log.warn("Invalid body pattern: {}", e.getMessage());
            builder.withRequestBody(WireMock.containing(bodyPattern));
        }
    }

    private void tryFallbackBodyMatching(MappingBuilder builder, String bodyPattern) {
        try {
            String fixedPattern = bodyPattern.replace("\\", "\\\\");
            JsonNode bodyRule = objectMapper.readTree(fixedPattern);
            if (bodyRule.has("matches")) {
                String regex = bodyRule.get("matches").asText();
                builder.withRequestBody(WireMock.matching(regex));
            } else if (bodyRule.has("contains")) {
                builder.withRequestBody(WireMock.containing(bodyRule.get("contains").asText()));
            } else {
                builder.withRequestBody(WireMock.containing(bodyPattern));
            }
        } catch (Exception e) {
            log.warn("Fallback body pattern parsing failed: {}", e.getMessage());
            builder.withRequestBody(WireMock.containing(bodyPattern));
        }
    }

    private void setResponse(MappingBuilder builder, StubMapping stub) {
        String responseBody = stub.getResponseDefinition();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            responseBody = createDefaultResponse(stub);
        }

        builder.willReturn(
                WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json;charset=UTF-8")
                        .withBody(responseBody));
    }

    private String createDefaultResponse(StubMapping stub) {
        return String.format(
                "{\"message\": \"Mocked response for %s\", \"stubName\": \"%s\", \"timestamp\": \"%s\"}",
                stub.getName(),
                stub.getName(),
                java.time.LocalDateTime.now());
    }
}
