package io.github.yeheng.wiremock.integration;

import io.github.yeheng.wiremock.WiremockUiApplication;
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
 * P1 测试 - URL 匹配模式测试
 * 测试 CONTAINS, REGEX, PATH_TEMPLATE 等 URL 匹配模式
 */
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb_urlpatterns",
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
@DisplayName("P1 - URL 匹配模式测试")
class UrlMatchingPatternsTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() throws Exception {
        // 清理所有 stubs
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
    @DisplayName("P1场景1: URL CONTAINS 匹配 - URL包含指定字符串")
    void testUrlContainsMatching() throws Exception {
        Thread.sleep(2000);

        // 创建 stub：URL 包含 "product"
        String createStubJson = """
                {
                    "name": "产品相关API",
                    "description": "匹配所有包含product的URL",
                    "method": "GET",
                    "url": "product",
                    "urlMatchType": "CONTAINS",
                    "enabled": true,
                    "responseDefinition": "{\\"message\\": \\"product found\\", \\"matchType\\": \\"contains\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试多个包含 "product" 的 URL
        String[] testUrls = {
                "/api/product/list",
                "/api/v1/products",
                "/admin/product-management",
                "/product"
        };

        for (String testUrl : testUrls) {
            HttpRequest testRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + testUrl))
                    .GET()
                    .build();

            HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, testResponse.statusCode(),
                    "URL包含'product'应该匹配: " + testUrl);
            assertTrue(testResponse.body().contains("product found"),
                    "响应应该包含'product found': " + testUrl);
        }
    }

    @Test
    @DisplayName("P1场景2: URL REGEX 匹配 - 使用正则表达式匹配URL")
    void testUrlRegexMatching() throws Exception {
        Thread.sleep(2000);

        // 创建 stub：使用正则表达式匹配 /api/users/数字ID
        String createStubJson = """
                {
                    "name": "用户ID查询",
                    "description": "匹配 /api/users/{数字ID}",
                    "method": "GET",
                    "url": "/api/users/[0-9]+",
                    "urlMatchType": "REGEX",
                    "enabled": true,
                    "responseDefinition": "{\\"userId\\": \\"matched\\", \\"matchType\\": \\"regex\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试应该匹配的URL（数字ID）
        String[] shouldMatch = {
                "/api/users/1",
                "/api/users/123",
                "/api/users/999999"
        };

        for (String testUrl : shouldMatch) {
            HttpRequest testRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + testUrl))
                    .GET()
                    .build();

            HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, testResponse.statusCode(),
                    "数字ID应该匹配: " + testUrl);
            assertTrue(testResponse.body().contains("matched"),
                    "响应应该包含'matched': " + testUrl);
        }

        // 测试不应该匹配的URL（非数字ID）
        String[] shouldNotMatch = {
                "/api/users/abc",
                "/api/users/user123",
                "/api/users/"
        };

        for (String testUrl : shouldNotMatch) {
            HttpRequest testRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + testUrl))
                    .GET()
                    .build();

            HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, testResponse.statusCode(),
                    "非数字ID不应该匹配: " + testUrl);
        }
    }

    @Test
    @DisplayName("P1场景3: URL PATH_TEMPLATE 匹配 - 使用路径模板匹配")
    void testUrlPathTemplateMatching() throws Exception {
        Thread.sleep(2000);

        // 创建 stub：使用路径模板 /api/orders/{orderId}/items/{itemId}
        String createStubJson = """
                {
                    "name": "订单项查询",
                    "description": "匹配 /api/orders/{orderId}/items/{itemId}",
                    "method": "GET",
                    "url": "/api/orders/{orderId}/items/{itemId}",
                    "urlMatchType": "PATH_TEMPLATE",
                    "enabled": true,
                    "responseDefinition": "{\\"orderId\\": \\"{orderId}\\", \\"itemId\\": \\"{itemId}\\", \\"matchType\\": \\"path_template\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试应该匹配的URL
        String[] shouldMatch = {
                "/api/orders/123/items/456",
                "/api/orders/abc/items/xyz",
                "/api/orders/order-001/items/item-002"
        };

        for (String testUrl : shouldMatch) {
            HttpRequest testRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + testUrl))
                    .GET()
                    .build();

            HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, testResponse.statusCode(),
                    "路径模板应该匹配: " + testUrl);
            assertTrue(testResponse.body().contains("matchType"),
                    "响应应该包含matchType: " + testUrl);
        }

        // 测试不应该匹配的URL
        String[] shouldNotMatch = {
                "/api/orders/123",
                "/api/orders/123/items",
                "/api/orders/123/products/456"
        };

        for (String testUrl : shouldNotMatch) {
            HttpRequest testRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + testUrl))
                    .GET()
                    .build();

            HttpResponse<String> testResponse = httpClient.send(testRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, testResponse.statusCode(),
                    "不匹配路径模板的URL应该返回404: " + testUrl);
        }
    }

    @Test
    @DisplayName("P1场景4: 多个URL匹配模式组合测试")
    void testMultipleUrlMatchingPatterns() throws Exception {
        Thread.sleep(2000);

        // 创建多个不同匹配模式的 stubs
        String[] stubs = {
                """
                {
                    "name": "精确匹配",
                    "method": "GET",
                    "url": "/api/exact",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "priority": 1,
                    "responseDefinition": "{\\"type\\": \\"exact\\"}"
                }
                """,
                """
                {
                    "name": "包含匹配",
                    "method": "GET",
                    "url": "partial",
                    "urlMatchType": "CONTAINS",
                    "enabled": true,
                    "priority": 2,
                    "responseDefinition": "{\\"type\\": \\"contains\\"}"
                }
                """,
                """
                {
                    "name": "正则匹配",
                    "method": "GET",
                    "url": "/api/regex/[a-z]+",
                    "urlMatchType": "REGEX",
                    "enabled": true,
                    "priority": 3,
                    "responseDefinition": "{\\"type\\": \\"regex\\"}"
                }
                """
        };

        // 创建所有 stubs
        for (String stubJson : stubs) {
            HttpRequest createRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(stubJson))
                    .build();

            HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(201, createResponse.statusCode(), "创建stub应该成功");
        }

        // 验证精确匹配
        HttpRequest exactRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/exact"))
                .GET()
                .build();
        HttpResponse<String> exactResponse = httpClient.send(exactRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, exactResponse.statusCode());
        assertTrue(exactResponse.body().contains("\"type\": \"exact\""));

        // 验证包含匹配
        HttpRequest containsRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/any/partial/path"))
                .GET()
                .build();
        HttpResponse<String> containsResponse = httpClient.send(containsRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, containsResponse.statusCode());
        assertTrue(containsResponse.body().contains("\"type\": \"contains\""));

        // 验证正则匹配
        HttpRequest regexRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/regex/abc"))
                .GET()
                .build();
        HttpResponse<String> regexResponse = httpClient.send(regexRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, regexResponse.statusCode());
        assertTrue(regexResponse.body().contains("\"type\": \"regex\""));
    }
}
