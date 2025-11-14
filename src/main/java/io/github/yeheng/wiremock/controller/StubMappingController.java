package io.github.yeheng.wiremock.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.yeheng.wiremock.entity.StubMapping;
import io.github.yeheng.wiremock.entity.StubMapping.UrlMatchType;
import io.github.yeheng.wiremock.service.StubMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * StubMapping REST API 控制器
 * 通过 /admin/stubs 路径提供CRUD操作
 */
@Slf4j
@RestController
@RequestMapping("/admin/stubs")
@RequiredArgsConstructor
public class StubMappingController {

    private final StubMappingService stubMappingService;
    private final ObjectMapper objectMapper;

    private <T> ResponseEntity<T> handleException(Supplier<ResponseEntity<T>> operation) {
        try {
            return operation.get();
        } catch (IllegalArgumentException e) {
            // 资源不存在返回404，其他参数错误返回400
            return e.getMessage() != null && e.getMessage().contains("不存在")
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("操作失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 标准化 Stub 数据 - 从多种JSON格式转换为标准格式
     * 支持：
     * 1. 单个 stub 对象（request/response 格式）
     * 2. WireMock 格式：{ "mappings": [...] }
     * 3. stub 数组格式
     */
    private StubMapping normalizeStub(Map<String, Object> stubData) {
        // 确保有基本字段
        Map<String, Object> request = (Map<String, Object>) stubData.getOrDefault("request", new HashMap<>());
        Map<String, Object> response = (Map<String, Object>) stubData.getOrDefault("response", new HashMap<>());

        // 处理 URL 字段
        String url = (String) (request.get("urlPattern") != null ? request.get("urlPattern")
                : request.get("url") != null ? request.get("url")
                : request.get("urlPathPattern") != null ? request.get("urlPathPattern")
                : request.get("urlPath") != null ? request.get("urlPath")
                : "/");

        // 确定 URL 匹配类型
        UrlMatchType urlMatchType = UrlMatchType.EQUALS;
        if (request.get("urlPattern") != null) {
            urlMatchType = UrlMatchType.REGEX;
        } else if (request.get("urlPathTemplate") != null) {
            urlMatchType = UrlMatchType.PATH_TEMPLATE;
        } else if (request.get("urlPath") != null) {
            urlMatchType = UrlMatchType.CONTAINS;
        }

        // 处理请求头
        Map<String, Object> requestHeaders = (Map<String, Object>) request.getOrDefault("headers", new HashMap<>());

        // 处理查询参数
        Map<String, Object> queryParameters = (Map<String, Object>) request.getOrDefault("queryParameters", new HashMap<>());

        // 处理请求体
        Map<String, Object> requestBodyPattern = new HashMap<>();
        if (request.containsKey("bodyPatterns")) {
            List<Map<String, Object>> bodyPatterns = (List<Map<String, Object>>) request.get("bodyPatterns");
            if (bodyPatterns != null && !bodyPatterns.isEmpty()) {
                requestBodyPattern = bodyPatterns.get(0);
            }
        }

        // 处理响应
        Map<String, Object> responseHeaders = (Map<String, Object>) response.getOrDefault("headers",
                new HashMap<String, Object>() {{ put("Content-Type", "application/json"); }});
        Integer responseStatus = (Integer) response.getOrDefault("status", 200);
        String responseBody = "";
        if (response.containsKey("jsonBody")) {
            try {
                responseBody = objectMapper.writeValueAsString(response.get("jsonBody"));
            } catch (Exception e) {
                responseBody = String.valueOf(response.get("jsonBody"));
            }
        } else if (response.containsKey("body")) {
            responseBody = String.valueOf(response.get("body"));
        }

        // 创建标准化 stub
        StubMapping stub = new StubMapping();
        stub.setName((String) stubData.getOrDefault("name", "Imported-" + System.currentTimeMillis()));
        stub.setMethod((String) request.getOrDefault("method", "GET"));
        stub.setUrl(url);
        stub.setUrlMatchType(urlMatchType);
        stub.setEnabled(stubData.get("enabled") != null ? (Boolean) stubData.get("enabled") : true);
        stub.setPriority(stubData.get("priority") != null ? ((Number) stubData.get("priority")).intValue() : 0);

        // 设置为标准格式（使用JSON字符串存储复杂结构）
        stub.setRequestHeadersPattern(objectMapper.valueToTree(requestHeaders).toString());
        stub.setQueryParametersPattern(objectMapper.valueToTree(queryParameters).toString());
        stub.setRequestBodyPattern(objectMapper.valueToTree(requestBodyPattern).toString());

        // 构建响应定义
        Map<String, Object> responseDefinition = new HashMap<>();
        responseDefinition.put("status", responseStatus);
        responseDefinition.put("headers", responseHeaders);
        responseDefinition.put("body", responseBody);
        try {
            stub.setResponseDefinition(objectMapper.writeValueAsString(responseDefinition));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法序列化响应定义: " + e.getMessage());
        }

        return stub;
    }

    /**
     * 创建新的 Stub - 接受标准化后的 StubMapping 对象
     */
    @PostMapping
    public ResponseEntity<StubMapping> createStub(@Valid @RequestBody StubMapping stub) {
        return handleException(() -> {
            StubMapping createdStub = stubMappingService.createStub(stub);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdStub);
        });
    }

    /**
     * 创建新的 Stub - 接受原始 JSON 格式并自动标准化
     */
    @PostMapping(value = "/import", consumes = "application/json")
    public ResponseEntity<StubMapping> createStubFromImport(@RequestBody Map<String, Object> stubData) {
        return handleException(() -> {
            // 标准化输入数据
            StubMapping normalizedStub = normalizeStub(stubData);
            StubMapping createdStub = stubMappingService.createStub(normalizedStub);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdStub);
        });
    }

    /**
     * 批量创建 Stubs - 接受标准化后的 StubMapping 对象数组
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<StubMapping>> createStubs(@Valid @RequestBody List<StubMapping> stubs) {
        return handleException(() -> {
            List<StubMapping> createdStubs = stubMappingService.createStubs(stubs);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdStubs);
        });
    }

    /**
     * 批量创建 Stubs - 接受多种格式并自动标准化
     * 支持：
     * 1. WireMock 格式：{ "mappings": [...] }
     * 2. 另一种格式：{ "stubs": [...] }
     */
    @PostMapping(value = "/bulk/import", consumes = "application/json")
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<StubMapping>> createStubsFromImport(@RequestBody Map<String, Object> importData) {
        return handleException(() -> {
            // 提取 stubs 列表
            List<Map<String, Object>> stubsToImport;

            if (importData.containsKey("mappings")) {
                // WireMock 格式：{ "mappings": [...] }
                Object mappings = importData.get("mappings");
                if (!(mappings instanceof List)) {
                    throw new IllegalArgumentException("'mappings' 字段必须是数组格式");
                }
                // 使用 @SuppressWarnings 压制类型转换警告
                stubsToImport = (List<Map<String, Object>>) mappings;
            } else if (importData.containsKey("stubs")) {
                // 另一种格式：{ "stubs": [...] }
                Object stubs = importData.get("stubs");
                if (!(stubs instanceof List)) {
                    throw new IllegalArgumentException("'stubs' 字段必须是数组格式");
                }
                // 使用 @SuppressWarnings 压制类型转换警告
                stubsToImport = (List<Map<String, Object>>) stubs;
            } else {
                throw new IllegalArgumentException("不支持的 JSON 格式：缺少 'mappings' 或 'stubs' 字段");
            }

            // 标准化所有 stubs
            List<StubMapping> normalizedStubs = stubsToImport.stream()
                    .map(this::normalizeStub)
                    .collect(java.util.stream.Collectors.toList());

            List<StubMapping> createdStubs = stubMappingService.createStubs(normalizedStubs);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdStubs);
        });
    }

    /**
     * 获取所有 Stubs
     */
    @GetMapping
    public ResponseEntity<List<StubMapping>> getAllStubs() {
        return handleException(() -> {
            List<StubMapping> stubs = stubMappingService.getAllStubs();
            return ResponseEntity.ok(stubs);
        });
    }

    /**
     * 分页获取所有 Stubs
     */
    @GetMapping("/page")
    public ResponseEntity<Page<StubMapping>> getAllStubs(Pageable pageable) {
        return handleException(() -> {
            Page<StubMapping> stubs = stubMappingService.getAllStubs(pageable);
            return ResponseEntity.ok(stubs);
        });
    }

    /**
     * 搜索 Stubs
     */
    @GetMapping("/search")
    public ResponseEntity<List<StubMapping>> searchStubs(@RequestParam String keyword) {
        return handleException(() -> {
            List<StubMapping> stubs = stubMappingService.searchStubs(keyword);
            return ResponseEntity.ok(stubs);
        });
    }

    /**
     * 根据 ID 获取 Stub
     */
    @GetMapping("/{stubId}")
    public ResponseEntity<StubMapping> getStubById(@PathVariable Long stubId) {
        return stubMappingService.getStubById(stubId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新 Stub
     */
    @PutMapping("/{stubId}")
    public ResponseEntity<StubMapping> updateStub(@PathVariable Long stubId,
                                                  @Valid @RequestBody StubMapping updatedStub) {
        return handleException(() -> {
            StubMapping stub = stubMappingService.updateStub(stubId, updatedStub);
            return ResponseEntity.ok(stub);
        });
    }

    /**
     * 删除 Stub
     */
    @DeleteMapping("/{stubId}")
    public ResponseEntity<Void> deleteStub(@PathVariable Long stubId) {
        return handleException(() -> {
            stubMappingService.deleteStub(stubId);
            return ResponseEntity.noContent().build();
        });
    }

    /**
     * 启用/禁用 Stub
     */
    @PostMapping("/{stubId}/toggle")
    public ResponseEntity<StubMapping> toggleStubEnabled(@PathVariable Long stubId) {
        return handleException(() -> {
            StubMapping stub = stubMappingService.toggleStubEnabled(stubId);
            return ResponseEntity.ok(stub);
        });
    }

    /**
     * 重新加载所有 stubs
     */
    @PostMapping("/reload")
    public ResponseEntity<Void> reloadAllStubs() {
        return handleException(() -> {
            stubMappingService.reloadAllStubs();
            return ResponseEntity.ok().build();
        });
    }

    /**
     * 获取 stub 统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<StubMappingService.StubStatistics> getStatistics() {
        return handleException(() -> {
            StubMappingService.StubStatistics stats = stubMappingService.getStatistics();
            return ResponseEntity.ok(stats);
        });
    }
}
