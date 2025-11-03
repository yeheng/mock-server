package com.example.wiremockui.integration;

import com.example.wiremockui.WiremockUiApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2 测试 - Stub 优先级测试
 * 测试当多个 stub 匹配同一请求时的优先级处理
 */
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb_priority",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.h2.console.enabled=false",
                "spring.jpa.show-sql=false",
                "wiremock.integrated-mode=true"
        })
@TestPropertySource(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "spring.jpa.show-sql=false",
        "wiremock.integrated-mode=true"
})
@DisplayName("P2 - Stub 优先级测试")
class StubPriorityTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() throws Exception {
        try {
            HttpRequest deleteRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/admin/wiremock/reset"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // 忽略清理错误
        }
    }

    @Test
    @DisplayName("P2场景5: 基本优先级测试 - 高优先级stub先匹配")
    void testBasicPriorityMatching() throws Exception {
        Thread.sleep(2000);

        // 创建低优先级 stub
        String lowPriorityStub = """
                {
                    "name": "低优先级stub",
                    "description": "优先级为10的stub",
                    "method": "GET",
                    "url": "/api/priority-test",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 10,
                    "responseDefinition": "{\\"priority\\": \\"low\\", \\"value\\": 10}"
                }
                """;

        HttpRequest createLowRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(lowPriorityStub))
                .build();

        HttpResponse<String> lowResponse = httpClient.send(createLowRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, lowResponse.statusCode(), "创建低优先级stub应该成功");

        // 创建高优先级 stub（优先级数字越小，优先级越高）
        String highPriorityStub = """
                {
                    "name": "高优先级stub",
                    "description": "优先级为1的stub",
                    "method": "GET",
                    "url": "/api/priority-test",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 1,
                    "responseDefinition": "{\\"priority\\": \\"high\\", \\"value\\": 1}"
                }
                """;

        HttpRequest createHighRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(highPriorityStub))
                .build();

        HttpResponse<String> highResponse = httpClient.send(createHighRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, highResponse.statusCode(), "创建高优先级stub应该成功");

        // 调用API，应该匹配高优先级的 stub
        HttpRequest testRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/priority-test"))
                .GET()
                .build();

        HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, testResponse.statusCode(), "请求应该成功");
        assertTrue(testResponse.body().contains("\"priority\": \"high\""),
                "应该匹配高优先级stub");
        assertTrue(testResponse.body().contains("\"value\": 1"),
                "应该返回高优先级stub的响应");
    }

    @Test
    @DisplayName("P2场景6: 相同优先级时的匹配顺序")
    void testSamePriorityMatching() throws Exception {
        Thread.sleep(2000);

        // 创建多个相同优先级的 stubs
        String[] stubs = {
                """
                {
                    "name": "第一个stub",
                    "method": "GET",
                    "url": "/api/same-priority",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 5,
                    "responseDefinition": "{\\"order\\": \\"first\\"}"
                }
                """,
                """
                {
                    "name": "第二个stub",
                    "method": "GET",
                    "url": "/api/same-priority",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 5,
                    "responseDefinition": "{\\"order\\": \\"second\\"}"
                }
                """,
                """
                {
                    "name": "第三个stub",
                    "method": "GET",
                    "url": "/api/same-priority",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 5,
                    "responseDefinition": "{\\"order\\": \\"third\\"}"
                }
                """
        };

        // 按顺序创建所有 stubs
        for (String stubJson : stubs) {
            HttpRequest createRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(stubJson))
                    .build();

            HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, createResponse.statusCode(), "创建stub应该成功");
            Thread.sleep(100); // 确保创建顺序
        }

        // 调用API，验证匹配结果
        HttpRequest testRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/same-priority"))
                .GET()
                .build();

        HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, testResponse.statusCode(), "请求应该成功");

        // WireMock在优先级相同时，通常选择最后添加的或最先匹配的
        // 这里验证确实匹配到了其中一个
        String body = testResponse.body();
        assertTrue(body.contains("\"order\":") &&
                        (body.contains("first") || body.contains("second") || body.contains("third")),
                "应该匹配到其中一个stub");
    }

    @Test
    @DisplayName("P2场景7: 不同匹配类型的优先级组合")
    void testPriorityWithDifferentMatchTypes() throws Exception {
        Thread.sleep(2000);

        // 创建不同匹配类型但相同URL模式的 stubs
        String[] stubs = {
                """
                {
                    "name": "精确匹配-低优先级",
                    "method": "GET",
                    "url": "/api/users/123",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 10,
                    "responseDefinition": "{\\"matchType\\": \\"exact\\", \\"priority\\": 10}"
                }
                """,
                """
                {
                    "name": "包含匹配-高优先级",
                    "method": "GET",
                    "url": "users",
                    "urlMatchType": "CONTAINS",
                    "enabled": true,
                    "priority": 1,
                    "responseDefinition": "{\\"matchType\\": \\"contains\\", \\"priority\\": 1}"
                }
                """,
                """
                {
                    "name": "正则匹配-中优先级",
                    "method": "GET",
                    "url": "/api/users/[0-9]+",
                    "urlMatchType": "REGEX",
                    "enabled": true,
                    "priority": 5,
                    "responseDefinition": "{\\"matchType\\": \\"regex\\", \\"priority\\": 5}"
                }
                """
        };

        for (String stubJson : stubs) {
            HttpRequest createRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(stubJson))
                    .build();

            HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, createResponse.statusCode(), "创建stub应该成功");
        }

        // 调用会被多个 stub 匹配的 URL
        HttpRequest testRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/123"))
                .GET()
                .build();

        HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, testResponse.statusCode(), "请求应该成功");

        // 应该匹配到高优先级的包含匹配（priority: 1）
        assertTrue(testResponse.body().contains("\"priority\": 1"),
                "应该匹配最高优先级的stub");
    }

    @Test
    @DisplayName("P2场景8: 优先级与启用状态的组合")
    void testPriorityWithEnabledStatus() throws Exception {
        Thread.sleep(2000);

        // 创建高优先级但禁用的 stub
        String disabledHighPriorityStub = """
                {
                    "name": "高优先级但禁用",
                    "method": "GET",
                    "url": "/api/status-test",
                    "urlMatchType": "EQUALS",
                    "enabled": false,
                    "priority": 1,
                    "responseDefinition": "{\\"status\\": \\"disabled\\", \\"priority\\": 1}"
                }
                """;

        HttpRequest createDisabledRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(disabledHighPriorityStub))
                .build();

        HttpResponse<String> disabledResponse = httpClient.send(createDisabledRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, disabledResponse.statusCode(), "创建禁用stub应该成功");

        // 创建低优先级但启用的 stub
        String enabledLowPriorityStub = """
                {
                    "name": "低优先级但启用",
                    "method": "GET",
                    "url": "/api/status-test",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 10,
                    "responseDefinition": "{\\"status\\": \\"enabled\\", \\"priority\\": 10}"
                }
                """;

        HttpRequest createEnabledRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(enabledLowPriorityStub))
                .build();

        HttpResponse<String> enabledResponse = httpClient.send(createEnabledRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, enabledResponse.statusCode(), "创建启用stub应该成功");

        // 调用API，应该匹配启用的低优先级 stub（禁用的高优先级stub不应匹配）
        HttpRequest testRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/status-test"))
                .GET()
                .build();

        HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, testResponse.statusCode(), "请求应该成功");
        assertTrue(testResponse.body().contains("\"status\": \"enabled\""),
                "应该匹配启用的stub");
        assertTrue(testResponse.body().contains("\"priority\": 10"),
                "应该匹配低优先级但启用的stub");
    }

    @Test
    @DisplayName("P2场景9: 复杂优先级场景 - 多层级匹配")
    void testComplexPriorityScenario() throws Exception {
        Thread.sleep(2000);

        // 创建多个不同优先级和匹配条件的 stubs
        String[] stubs = {
                """
                {
                    "name": "最低优先级-通用匹配",
                    "method": "GET",
                    "url": "api",
                    "urlMatchType": "CONTAINS",
                    "enabled": true,
                    "priority": 100,
                    "responseDefinition": "{\\"level\\": \\"fallback\\", \\"priority\\": 100}"
                }
                """,
                """
                {
                    "name": "中等优先级-路径匹配",
                    "method": "GET",
                    "url": "/api/products/[0-9]+",
                    "urlMatchType": "REGEX",
                    "enabled": true,
                    "priority": 50,
                    "responseDefinition": "{\\"level\\": \\"medium\\", \\"priority\\": 50}"
                }
                """,
                """
                {
                    "name": "高优先级-精确匹配",
                    "method": "GET",
                    "url": "/api/products/999",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 10,
                    "responseDefinition": "{\\"level\\": \\"high\\", \\"priority\\": 10}"
                }
                """,
                """
                {
                    "name": "最高优先级-特殊产品",
                    "method": "GET",
                    "url": "/api/products/999",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 1,
                    "requestHeadersPattern": "{\\"X-Special\\": {\\"equalTo\\": \\"true\\"}}",
                    "responseDefinition": "{\\"level\\": \\"vip\\", \\"priority\\": 1}"
                }
                """
        };

        for (String stubJson : stubs) {
            HttpRequest createRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(stubJson))
                    .build();

            HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, createResponse.statusCode(), "创建stub应该成功");
        }

        // 测试1: 带特殊头的请求，应该匹配最高优先级
        HttpRequest vipRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products/999"))
                .header("X-Special", "true")
                .GET()
                .build();

        HttpResponse<String> vipResponse = httpClient.send(vipRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, vipResponse.statusCode());
        assertTrue(vipResponse.body().contains("\"level\": \"vip\""),
                "带特殊头应该匹配VIP级别");

        // 测试2: 不带特殊头的请求到999，应该匹配高优先级精确匹配
        HttpRequest highRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products/999"))
                .GET()
                .build();

        HttpResponse<String> highResponse = httpClient.send(highRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, highResponse.statusCode());
        assertTrue(highResponse.body().contains("\"level\": \"high\""),
                "不带特殊头应该匹配高优先级");

        // 测试3: 其他产品ID，应该匹配中等优先级正则匹配
        HttpRequest mediumRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products/123"))
                .GET()
                .build();

        HttpResponse<String> mediumResponse = httpClient.send(mediumRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, mediumResponse.statusCode());
        assertTrue(mediumResponse.body().contains("\"level\": \"medium\""),
                "其他产品ID应该匹配中等优先级");

        // 测试4: 其他API路径，应该匹配最低优先级通用匹配
        HttpRequest fallbackRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/other"))
                .GET()
                .build();

        HttpResponse<String> fallbackResponse = httpClient.send(fallbackRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, fallbackResponse.statusCode());
        assertTrue(fallbackResponse.body().contains("\"level\": \"fallback\""),
                "其他路径应该匹配兜底规则");
    }
}
