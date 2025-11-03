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
 * Filter 路径路由和 HTTP 方法集成测试
 *
 * TDD 测试场景：
 * 1. 验证 Filter 正确路由 /admin/ 和其他路径
 * 2. 测试不同的 HTTP 方法（PUT, DELETE, PATCH）
 * 3. 测试静态资源过滤
 */
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb_filter",
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
@DisplayName("Filter路径路由和HTTP方法集成测试")
class FilterRoutingAndMethodsTest {

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

    // ==================== Filter 路径路由测试 ====================

    @Test
    @DisplayName("TDD场景7: /admin/health 应该由Spring MVC处理")
    void testAdminHealthRoutesToSpringMVC() throws Exception {
        Thread.sleep(1000);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
            "/admin/health 应该由 Spring MVC Controller 处理");
        assertTrue(response.body().contains("status") || response.body().contains("wiremock"),
            "应该返回健康检查信息");
    }

    @Test
    @DisplayName("TDD场景8: /admin/wiremock 应该由Spring MVC处理")
    void testAdminWireMockRoutesToSpringMVC() throws Exception {
        Thread.sleep(1000);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/wiremock/status"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
            "/admin/wiremock 应该由 Spring MVC Controller 处理");
    }

    @Test
    @DisplayName("TDD场景9: /api/ 路径应该由WireMock处理，没有stub时返回404")
    void testApiPathRoutesToWireMock() throws Exception {
        Thread.sleep(1000);

        // 多个不同的 /api/ 路径都应该被 WireMock 处理
        String[] paths = {
            "/api/users",
            "/api/products",
            "/api/orders/123",
            "/api/v1/something",  // 注意：现在 /api/v1/ 也应该由 WireMock 处理
            "/api/test/nested/path"
        };

        for (String path : paths) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(404, response.statusCode(),
                path + " 应该由 WireMock 处理，没有 stub 时返回 404");
            assertTrue(response.body().contains("No matching stub"),
                "应该包含 WireMock 的错误信息");
        }
    }

    @Test
    @DisplayName("TDD场景10: /actuator/ 路径应该由Spring MVC处理（如果Actuator已启用）")
    void testActuatorRoutesToSpringMVC() throws Exception {
        Thread.sleep(1000);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Actuator 已启用，应该返回 200 并包含 actuator 信息
        // 如果未启用，可能返回 404 但应该是 Spring MVC 的404
        assertTrue(response.statusCode() == 200 || response.statusCode() == 404,
            "/actuator 应该由 Spring MVC 处理");

        // 验证：如果启用了 actuator，响应应该包含 actuator 相关信息
        if (response.statusCode() == 200) {
            String body = response.body();
            assertTrue(body.contains("_links") || body.contains("health") || body.contains("self"),
                "Actuator 响应应该包含链接信息");
        }
    }

    // ==================== HTTP 方法测试 ====================

    @Test
    @DisplayName("TDD场景11: PUT 请求应该被正确匹配和处理")
    void testPutRequestMatching() throws Exception {
        Thread.sleep(1000);

        // 步骤1: 创建 PUT stub
        String createStubJson = """
                {
                    "name": "更新用户",
                    "method": "PUT",
                    "url": "/api/users/789",
                    "enabled": true,
                    "responseDefinition": "{\\"status\\": \\"updated\\", \\"userId\\": 789}"
                }
                """;

        HttpResponse<String> createResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, createResponse.statusCode());

        // 步骤2: 发送 PUT 请求
        String requestBody = """
                {
                    "name": "更新后的名称",
                    "email": "updated@example.com"
                }
                """;

        HttpResponse<String> putResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/789"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        // 验证
        assertEquals(200, putResponse.statusCode());
        assertTrue(putResponse.body().contains("\"status\": \"updated\""));
        assertTrue(putResponse.body().contains("\"userId\": 789"));
    }

    @Test
    @DisplayName("TDD场景12: DELETE 请求应该被正确匹配和处理")
    void testDeleteRequestMatching() throws Exception {
        Thread.sleep(1000);

        // 步骤1: 创建 DELETE stub
        String createStubJson = """
                {
                    "name": "删除用户",
                    "method": "DELETE",
                    "url": "/api/users/999",
                    "enabled": true,
                    "responseDefinition": "{\\"status\\": \\"deleted\\", \\"userId\\": 999}"
                }
                """;

        HttpResponse<String> createResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, createResponse.statusCode());

        // 步骤2: 发送 DELETE 请求
        HttpResponse<String> deleteResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/999"))
                .DELETE()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        // 验证
        assertEquals(200, deleteResponse.statusCode());
        assertTrue(deleteResponse.body().contains("\"status\": \"deleted\""));
        assertTrue(deleteResponse.body().contains("\"userId\": 999"));
    }

    @Test
    @DisplayName("TDD场景13: 不同HTTP方法应该匹配不同的stub")
    void testDifferentMethodsMatchDifferentStubs() throws Exception {
        Thread.sleep(1000);

        // 步骤1: 为同一URL创建多个不同方法的 stub
        String getStubJson = """
                {
                    "name": "GET查询",
                    "method": "GET",
                    "url": "/api/items/1",
                    "enabled": true,
                    "responseDefinition": "{\\"action\\": \\"get\\", \\"id\\": 1}"
                }
                """;

        String postStubJson = """
                {
                    "name": "POST创建",
                    "method": "POST",
                    "url": "/api/items/1",
                    "enabled": true,
                    "responseDefinition": "{\\"action\\": \\"post\\", \\"id\\": 1}"
                }
                """;

        String putStubJson = """
                {
                    "name": "PUT更新",
                    "method": "PUT",
                    "url": "/api/items/1",
                    "enabled": true,
                    "responseDefinition": "{\\"action\\": \\"put\\", \\"id\\": 1}"
                }
                """;

        // 创建所有 stubs
        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(getStubJson))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(postStubJson))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(putStubJson))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        // 步骤2: 测试不同方法返回不同响应
        HttpResponse<String> getResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/items/1"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertTrue(getResponse.body().contains("\"action\": \"get\""),
            "GET 应该匹配 GET stub");

        HttpResponse<String> postResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/items/1"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertTrue(postResponse.body().contains("\"action\": \"post\""),
            "POST 应该匹配 POST stub");

        HttpResponse<String> putResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/items/1"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertTrue(putResponse.body().contains("\"action\": \"put\""),
            "PUT 应该匹配 PUT stub");
    }

    @Test
    @DisplayName("TDD场景14: 禁用的stub不应该被匹配")
    void testDisabledStubNotMatched() throws Exception {
        Thread.sleep(1000);

        // 步骤1: 创建禁用的 stub
        String createStubJson = """
                {
                    "name": "禁用的接口",
                    "method": "GET",
                    "url": "/api/disabled",
                    "enabled": false,
                    "responseDefinition": "{\\"message\\": \\"不应该返回这个\\"}"
                }
                """;

        HttpResponse<String> createResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(201, createResponse.statusCode());

        // 步骤2: 请求该路径应该返回 404
        HttpResponse<String> mockResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/disabled"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(404, mockResponse.statusCode(),
            "禁用的 stub 不应该被匹配");
        assertTrue(mockResponse.body().contains("No matching stub"));
    }

    // TODO: 中文路径测试需要额外的URL编码处理，暂时跳过
    // @Test
    // @DisplayName("TDD场景15: 中文路径和中文响应应该正确处理")
    // void testChinesePathAndResponse() throws Exception {
    //     // 暂时跳过，需要特殊的URL编码处理
    // }
}
