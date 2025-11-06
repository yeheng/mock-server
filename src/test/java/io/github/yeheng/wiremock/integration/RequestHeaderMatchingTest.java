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
 * P1 测试 - 请求头匹配测试
 * 测试基于 HTTP 请求头的匹配功能
 */
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb_headers",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "spring.jpa.show-sql=false",
        "wiremock.integrated-mode=true"
})
@DisplayName("P1 - 请求头匹配测试")
class RequestHeaderMatchingTest {

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
    @DisplayName("P1场景5: Authorization 请求头匹配")
    void testAuthorizationHeaderMatching() throws Exception {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 创建需要特定 Authorization 头的 stub
        String createStubJson = """
                {
                    "name": "需要授权的API",
                    "description": "匹配带有特定Authorization头的请求",
                    "method": "GET",
                    "url": "/api/protected",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "requestHeadersPattern": "{\\"Authorization\\": {\\"equalTo\\": \\"Bearer token123\\"}}",
                    "responseDefinition": "{\\"message\\": \\"authorized\\", \\"data\\": \\"protected content\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试带正确 Authorization 头的请求
        HttpRequest authorizedRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/protected"))
                .header("Authorization", "Bearer token123")
                .GET()
                .build();

        HttpResponse<String> authorizedResponse = httpClient.send(authorizedRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, authorizedResponse.statusCode(), "带正确Authorization头应该成功");
        assertTrue(authorizedResponse.body().contains("authorized"));

        // 测试不带 Authorization 头的请求
        HttpRequest unauthorizedRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/protected"))
                .GET()
                .build();

        HttpResponse<String> unauthorizedResponse = httpClient.send(unauthorizedRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, unauthorizedResponse.statusCode(), "不带Authorization头应该返回404");

        // 测试带错误 Authorization 头的请求
        HttpRequest wrongAuthRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/protected"))
                .header("Authorization", "Bearer wrong-token")
                .GET()
                .build();

        HttpResponse<String> wrongAuthResponse = httpClient.send(wrongAuthRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, wrongAuthResponse.statusCode(), "错误的Authorization头应该返回404");
    }

    @Test
    @DisplayName("P1场景6: Content-Type 请求头匹配")
    void testContentTypeHeaderMatching() throws Exception {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 创建只接受 JSON 的 stub
        String createStubJson = """
                {
                    "name": "JSON API",
                    "description": "只接受JSON格式的请求",
                    "method": "POST",
                    "url": "/api/data",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "requestHeadersPattern": "{\\"Content-Type\\": {\\"equalTo\\": \\"application/json\\"}}",
                    "responseDefinition": "{\\"message\\": \\"json accepted\\", \\"status\\": \\"success\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试带正确 Content-Type 的请求
        HttpRequest jsonRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/data"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"key\": \"value\"}"))
                .build();

        HttpResponse<String> jsonResponse = httpClient.send(jsonRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, jsonResponse.statusCode(), "JSON Content-Type应该匹配");
        assertTrue(jsonResponse.body().contains("json accepted"));

        // 测试不带 Content-Type 的请求
        HttpRequest noContentTypeRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/data"))
                .POST(HttpRequest.BodyPublishers.ofString("data"))
                .build();

        HttpResponse<String> noContentTypeResponse = httpClient.send(noContentTypeRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, noContentTypeResponse.statusCode(), "不带Content-Type应该返回404");
    }

    @Test
    @DisplayName("P1场景7: 自定义请求头匹配")
    void testCustomHeaderMatching() throws Exception {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 创建需要自定义头的 stub
        String createStubJson = """
                {
                    "name": "需要API Key",
                    "description": "匹配带有X-API-Key头的请求",
                    "method": "GET",
                    "url": "/api/service",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "requestHeadersPattern": "{\\"X-API-Key\\": {\\"equalTo\\": \\"secret-key-123\\"}}",
                    "responseDefinition": "{\\"service\\": \\"available\\", \\"authenticated\\": true}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试带正确自定义头的请求
        HttpRequest validKeyRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/service"))
                .header("X-API-Key", "secret-key-123")
                .GET()
                .build();

        HttpResponse<String> validKeyResponse = httpClient.send(validKeyRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, validKeyResponse.statusCode(), "正确的API Key应该匹配");
        assertTrue(validKeyResponse.body().contains("available"));

        // 测试带错误自定义头的请求
        HttpRequest invalidKeyRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/service"))
                .header("X-API-Key", "wrong-key")
                .GET()
                .build();

        HttpResponse<String> invalidKeyResponse = httpClient.send(invalidKeyRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, invalidKeyResponse.statusCode(), "错误的API Key应该返回404");
    }

    @Test
    @DisplayName("P1场景8: 多个请求头组合匹配")
    void testMultipleHeadersMatching() throws Exception {
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        // 创建需要多个请求头的 stub
        String createStubJson = """
                {
                    "name": "多头验证API",
                    "description": "需要同时匹配多个请求头",
                    "method": "POST",
                    "url": "/api/secure",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "requestHeadersPattern": "{\\"Authorization\\": {\\"equalTo\\": \\"Bearer token123\\"}, \\"X-Request-ID\\": {\\"matches\\": \\"[0-9a-f-]+\\"}}",
                    "responseDefinition": "{\\"status\\": \\"success\\", \\"message\\": \\"all headers matched\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试带所有必需头的请求
        HttpRequest validRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/secure"))
                .header("Authorization", "Bearer token123")
                .header("X-Request-ID", "123e4567-e89b-12d3-a456")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, validResponse.statusCode(), "所有头都正确应该匹配");
        assertTrue(validResponse.body().contains("all headers matched"));

        // 测试只带部分头的请求
        HttpRequest partialRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/secure"))
                .header("Authorization", "Bearer token123")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> partialResponse = httpClient.send(partialRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, partialResponse.statusCode(), "缺少请求头应该返回404");
    }
}
