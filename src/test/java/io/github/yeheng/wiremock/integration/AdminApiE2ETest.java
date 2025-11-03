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
 * Admin API 端到端集成测试
 * 测试通过 HTTP Admin API 创建 stub，然后验证 WireMock 能立即处理请求
 *
 * TDD 测试场景：
 * 1. 通过 /admin/stubs 创建 stub
 * 2. 验证 stub 立即生效
 * 3. 调用 WireMock 端点，验证返回配置的响应
 */
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb_admin",
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
@DisplayName("Admin API 端到端集成测试")
class AdminApiE2ETest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() throws Exception {
        // 清理所有 stubs - 通过 Admin API
        // 临时禁用，看看是否会触发重置
        //  try {
        //      HttpRequest deleteRequest = HttpRequest.newBuilder()
        //              .uri(URI.create("http://localhost:" + port + "/admin/wiremock/reset"))
        //              .POST(HttpRequest.BodyPublishers.noBody())
        //              .build();
        //      httpClient.send(deleteRequest, HttpResponse.BodyHandlers.ofString());
        //  } catch (Exception e) {
        //      // 忽略清理错误
        //  }
    }

    @Test
    @DisplayName("TDD场景1: 通过Admin API创建stub后，WireMock立即能处理GET请求")
    void testCreateStubViaAdminAPI_ThenCallWireMock_GET() throws Exception {
        // 等待应用启动
        Thread.sleep(2000);

        // 步骤1: 通过 Admin API 创建 stub
        String createStubJson = """
                {
                    "name": "用户查询接口",
                    "description": "通过Admin API创建的stub",
                    "method": "GET",
                    "url": "/api/users/123",
                    "enabled": true,
                    "responseDefinition": "{\\"id\\": 123, \\"name\\": \\"张三\\", \\"email\\": \\"zhangsan@example.com\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());

        // 验证创建成功
        assertEquals(201, createResponse.statusCode(),
            "Admin API 创建 stub 应该返回 201 Created");
        assertTrue(createResponse.body().contains("用户查询接口"),
            "响应应该包含 stub 名称");

        // 步骤2: 立即调用 WireMock 端点，验证 stub 已生效
        HttpRequest mockRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users/123"))
                .GET()
                .build();

        HttpResponse<String> mockResponse = httpClient.send(mockRequest, HttpResponse.BodyHandlers.ofString());

        // 验证 WireMock 返回配置的响应
        assertEquals(200, mockResponse.statusCode(),
            "WireMock 应该返回 200 OK");

        String responseBody = mockResponse.body();
        assertNotNull(responseBody, "响应体不应为null");
        assertTrue(responseBody.contains("\"id\": 123"),
            "响应应该包含配置的 id");
        assertTrue(responseBody.contains("\"name\": \"张三\""),
            "响应应该包含配置的 name");
        assertTrue(responseBody.contains("\"email\": \"zhangsan@example.com\""),
            "响应应该包含配置的 email");
    }

    @Test
    @DisplayName("TDD场景2: 通过Admin API创建stub后，WireMock立即能处理POST请求")
    void testCreateStubViaAdminAPI_ThenCallWireMock_POST() throws Exception {
        Thread.sleep(2000);

        // 步骤1: 通过 Admin API 创建 POST stub
        String createStubJson = """
                {
                    "name": "创建用户接口",
                    "method": "POST",
                    "url": "/api/users",
                    "enabled": true,
                    "responseDefinition": "{\\"status\\": \\"created\\", \\"userId\\": 456, \\"message\\": \\"用户创建成功\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode());

        // 步骤2: 调用 WireMock POST 端点
        String requestBody = """
                {
                    "name": "测试用户",
                    "email": "test@example.com"
                }
                """;

        HttpRequest mockRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> mockResponse = httpClient.send(mockRequest, HttpResponse.BodyHandlers.ofString());

        // 验证响应
        assertEquals(200, mockResponse.statusCode());
        String responseBody = mockResponse.body();
        assertTrue(responseBody.contains("\"status\": \"created\""));
        assertTrue(responseBody.contains("\"userId\": 456"));
        assertTrue(responseBody.contains("用户创建成功"));
    }

    @Test
    @DisplayName("TDD场景3: Admin API路径应该由Spring MVC处理，不被WireMock拦截")
    void testAdminApiRoutingToSpringMVC() throws Exception {
        Thread.sleep(2000);

        // /admin/stubs 应该由 Spring MVC Controller 处理
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 应该返回 Spring MVC 的响应（空列表或正常响应），不是 WireMock 的 404
        assertEquals(200, response.statusCode(),
            "Admin API 应该由 Spring MVC 处理，返回 200");

        // 响应应该是 JSON 数组格式（可能为空）
        String body = response.body();
        assertTrue(body.startsWith("[") || body.startsWith("{"),
            "响应应该是有效的 JSON 格式");
    }

    @Test
    @DisplayName("TDD场景4: 非Admin路径在没有stub时应该返回WireMock的404")
    void testNonAdminPathReturnsWireMock404WhenNoStub() throws Exception {
        Thread.sleep(2000);

        // 调用一个不存在 stub 的路径
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/nonexistent"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 应该返回 WireMock 的 404
        assertEquals(404, response.statusCode(),
            "没有匹配的 stub 时，WireMock 应该返回 404");

        String body = response.body();
        assertTrue(body.contains("No matching stub") || body.contains("error"),
            "404响应应该包含错误信息");
    }

    @Test
    @DisplayName("TDD场景5: 通过Admin API更新stub后，WireMock返回新的响应")
    void testUpdateStubViaAdminAPI_ThenVerifyNewResponse() throws Exception {
        Thread.sleep(2000);

        // 步骤1: 创建初始 stub
        String createStubJson = """
                {
                    "name": "产品查询",
                    "method": "GET",
                    "url": "/api/products/100",
                    "enabled": true,
                    "responseDefinition": "{\\"id\\": 100, \\"name\\": \\"旧产品\\", \\"price\\": 99}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode());

        // 从响应中提取 stub ID
        String createBody = createResponse.body();
        Long stubId = extractIdFromJson(createBody);
        assertNotNull(stubId, "应该能从响应中提取到 stub ID");

        // 步骤2: 验证初始响应
        HttpResponse<String> initialResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products/100"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertTrue(initialResponse.body().contains("旧产品"));

        // 步骤3: 通过 Admin API 更新 stub
        String updateStubJson = """
                {
                    "id": %d,
                    "name": "产品查询",
                    "method": "GET",
                    "url": "/api/products/100",
                    "enabled": true,
                    "responseDefinition": "{\\"id\\": 100, \\"name\\": \\"新产品\\", \\"price\\": 199}"
                }
                """.formatted(stubId);

        HttpRequest updateRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs/" + stubId))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updateStubJson))
                .build();

        HttpResponse<String> updateResponse = httpClient.send(updateRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, updateResponse.statusCode(), "更新应该成功");

        // 步骤4: 验证 WireMock 返回更新后的响应
        HttpResponse<String> newResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/products/100"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );

        String newBody = newResponse.body();
        assertTrue(newBody.contains("新产品"), "应该返回更新后的产品名称");
        assertTrue(newBody.contains("\"price\": 199"), "应该返回更新后的价格");
        assertFalse(newBody.contains("旧产品"), "不应该包含旧的产品名称");
    }

    @Test
    @DisplayName("TDD场景6: 通过Admin API删除stub后，WireMock返回404")
    void testDeleteStubViaAdminAPI_ThenVerify404() throws Exception {
        Thread.sleep(2000);

        // 步骤1: 创建 stub
        String createStubJson = """
                {
                    "name": "待删除接口",
                    "method": "GET",
                    "url": "/api/temp/123",
                    "enabled": true,
                    "responseDefinition": "{\\"message\\": \\"临时数据\\"}"
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

        Long stubId = extractIdFromJson(createResponse.body());

        // 步骤2: 验证 stub 生效
        HttpResponse<String> beforeDelete = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/temp/123"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, beforeDelete.statusCode());

        // 步骤3: 通过 Admin API 删除 stub
        HttpResponse<String> deleteResponse = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs/" + stubId))
                .DELETE()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(204, deleteResponse.statusCode(), "删除应该返回 204 No Content");

        // 步骤4: 验证 WireMock 返回 404
        HttpResponse<String> afterDelete = httpClient.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/temp/123"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(404, afterDelete.statusCode(),
            "删除 stub 后，WireMock 应该返回 404");
    }

    /**
     * 从 JSON 响应中提取 ID
     */
    private Long extractIdFromJson(String json) {
        try {
            // 简单的 JSON 解析，提取 "id": 数字
            int idIndex = json.indexOf("\"id\":");
            if (idIndex == -1) return null;

            int start = idIndex + 5;
            while (start < json.length() && !Character.isDigit(json.charAt(start))) {
                start++;
            }

            int end = start;
            while (end < json.length() && Character.isDigit(json.charAt(end))) {
                end++;
            }

            return Long.parseLong(json.substring(start, end));
        } catch (Exception e) {
            return null;
        }
    }
}
