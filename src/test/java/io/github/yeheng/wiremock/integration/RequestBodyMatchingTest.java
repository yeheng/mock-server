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
 * P1 测试 - 请求体模式匹配测试
 * 测试基于 HTTP 请求体内容的匹配功能
 */
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb_requestbody",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "spring.jpa.show-sql=false",
        "wiremock.integrated-mode=true"
})
@DisplayName("P1 - 请求体模式匹配测试")
class RequestBodyMatchingTest {

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
    @DisplayName("P1场景13: JSON请求体精确匹配")
    void testJsonBodyExactMatch() throws Exception {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 创建需要特定JSON请求体的 stub
        String createStubJson = """
                {
                    "name": "登录API",
                    "description": "匹配特定JSON格式的登录请求",
                    "method": "POST",
                    "url": "/api/login",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "requestBodyPattern": "{\\"equalToJson\\": \\"{\\\\\\"username\\\\\\":\\\\\\"admin\\\\\\",\\\\\\"password\\\\\\":\\\\\\"secret\\\\\\"}\\"}}",
                    "responseDefinition": "{\\"token\\": \\"abc123\\", \\"status\\": \\"success\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试正确的请求体
        HttpRequest validRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .build();

        HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, validResponse.statusCode(), "正确的请求体应该匹配");
        assertTrue(validResponse.body().contains("abc123"));

        // 测试错误的请求体
        HttpRequest wrongRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"user\",\"password\":\"wrong\"}"))
                .build();

        HttpResponse<String> wrongResponse = httpClient.send(wrongRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, wrongResponse.statusCode(), "错误的请求体应该返回404");
    }

    @Test
    @DisplayName("P1场景14: JSON请求体部分匹配")
    void testJsonBodyPartialMatch() throws Exception {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 创建部分匹配JSON的 stub
        String createStubJson = """
                {
                    "name": "用户创建API",
                    "description": "匹配包含特定字段的JSON",
                    "method": "POST",
                    "url": "/api/users",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "requestBodyPattern": "{\\"matchesJsonPath\\": \\"$[?(@.email)]\\"}",
                    "responseDefinition": "{\\"userId\\": \\"123\\", \\"created\\": true}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试包含email字段的请求（应该匹配）
        String[] validBodies = {
                "{\"email\":\"test@example.com\"}",
                "{\"email\":\"test@example.com\",\"name\":\"Test User\"}",
                "{\"name\":\"User\",\"email\":\"user@test.com\",\"age\":25}"
        };

        for (String body : validBodies) {
            HttpRequest validRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/users"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, validResponse.statusCode(), "包含email字段应该匹配: " + body);
        }

        // 测试不包含email字段的请求（不应该匹配）
        HttpRequest noEmailRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Test\",\"age\":25}"))
                .build();

        HttpResponse<String> noEmailResponse = httpClient.send(noEmailRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, noEmailResponse.statusCode(), "不包含email字段不应该匹配");
    }

    @Test
    @DisplayName("P1场景15: 请求体包含字符串匹配")
    void testBodyContainsString() throws Exception {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 创建包含特定字符串的 stub
        String createStubJson = """
                {
                    "name": "日志提交API",
                    "description": "匹配包含ERROR关键字的日志",
                    "method": "POST",
                    "url": "/api/logs",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "requestBodyPattern": "{\\"contains\\": \\"ERROR\\"}",
                    "responseDefinition": "{\\"logged\\": true, \\"level\\": \\"error\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试包含ERROR的请求体
        String[] validBodies = {
                "ERROR: Something went wrong",
                "Log entry: ERROR in module X",
                "{\"message\":\"ERROR occurred\",\"level\":\"error\"}"
        };

        for (String body : validBodies) {
            HttpRequest validRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/logs"))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, validResponse.statusCode(), "包含ERROR应该匹配: " + body);
        }

        // 测试不包含ERROR的请求体
        HttpRequest noErrorRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/logs"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("INFO: Everything is fine"))
                .build();

        HttpResponse<String> noErrorResponse = httpClient.send(noErrorRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, noErrorResponse.statusCode(), "不包含ERROR不应该匹配");
    }

    @Test
    @DisplayName("P1场景16: 请求体正则表达式匹配")
    void testBodyRegexMatch() throws Exception {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 创建使用正则表达式匹配的 stub
        String createStubJson = """
                {
                    "name": "邮箱验证API",
                    "description": "匹配包含邮箱格式的请求",
                    "method": "POST",
                    "url": "/api/validate",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "requestBodyPattern": "{\\"matches\\": \\".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}.*\\"}",
                    "responseDefinition": "{\\"valid\\": true, \\"format\\": \\"email\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试包含邮箱格式的请求体
        String[] validBodies = {
                "test@example.com",
                "Email: user@domain.org",
                "{\"email\":\"admin@company.co.uk\"}"
        };

        for (String body : validBodies) {
            HttpRequest validRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/validate"))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, validResponse.statusCode(), "包含邮箱格式应该匹配: " + body);
        }

        // 测试不包含邮箱格式的请求体
        HttpRequest noEmailRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/validate"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("No email here, just text"))
                .build();

        HttpResponse<String> noEmailResponse = httpClient.send(noEmailRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, noEmailResponse.statusCode(), "不包含邮箱格式不应该匹配");
    }
}
