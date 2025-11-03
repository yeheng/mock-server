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
 * P1 测试 - 查询参数匹配测试
 * 测试基于 URL 查询参数的匹配功能
 */
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb_queryparams",
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
@DisplayName("P1 - 查询参数匹配测试")
class QueryParameterMatchingTest {

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
    @DisplayName("P1场景9: 单个查询参数精确匹配")
    void testSingleQueryParameterExactMatch() throws Exception {
        Thread.sleep(2000);

        // 创建需要特定查询参数的 stub
        String createStubJson = """
                {
                    "name": "搜索API",
                    "description": "匹配带有query参数的请求",
                    "method": "GET",
                    "url": "/api/search",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "queryParametersPattern": "{\\"query\\": {\\"equalTo\\": \\"test\\"}}",
                    "responseDefinition": "{\\"results\\": [\\"item1\\", \\"item2\\"], \\"query\\": \\"test\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试带正确查询参数的请求
        HttpRequest validRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/search?query=test"))
                .GET()
                .build();

        HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, validResponse.statusCode(), "正确的查询参数应该匹配");
        assertTrue(validResponse.body().contains("\"query\": \"test\""));

        // 测试不带查询参数的请求
        HttpRequest noParamRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/search"))
                .GET()
                .build();

        HttpResponse<String> noParamResponse = httpClient.send(noParamRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, noParamResponse.statusCode(), "不带查询参数应该返回404");

        // 测试带错误查询参数值的请求
        HttpRequest wrongValueRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/search?query=wrong"))
                .GET()
                .build();

        HttpResponse<String> wrongValueResponse = httpClient.send(wrongValueRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, wrongValueResponse.statusCode(), "错误的查询参数值应该返回404");
    }

    @Test
    @DisplayName("P1场景10: 多个查询参数组合匹配")
    void testMultipleQueryParametersMatching() throws Exception {
        Thread.sleep(2000);

        // 创建需要多个查询参数的 stub
        String createStubJson = """
                {
                    "name": "分页API",
                    "description": "匹配带有page和size参数的请求",
                    "method": "GET",
                    "url": "/api/items",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "queryParametersPattern": "{\\"page\\": {\\"equalTo\\": \\"1\\"}, \\"size\\": {\\"equalTo\\": \\"10\\"}}",
                    "responseDefinition": "{\\"page\\": 1, \\"size\\": 10, \\"items\\": [\\"a\\", \\"b\\", \\"c\\"]}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试带所有查询参数的请求
        HttpRequest validRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/items?page=1&size=10"))
                .GET()
                .build();

        HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, validResponse.statusCode(), "所有查询参数正确应该匹配");
        assertTrue(validResponse.body().contains("\"page\": 1"));

        // 测试只带部分查询参数的请求
        HttpRequest partialRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/items?page=1"))
                .GET()
                .build();

        HttpResponse<String> partialResponse = httpClient.send(partialRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, partialResponse.statusCode(), "缺少查询参数应该返回404");

        // 测试参数顺序不同的请求（应该也能匹配）
        HttpRequest reorderedRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/items?size=10&page=1"))
                .GET()
                .build();

        HttpResponse<String> reorderedResponse = httpClient.send(reorderedRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reorderedResponse.statusCode(), "参数顺序不同也应该匹配");
    }

    @Test
    @DisplayName("P1场景11: 查询参数正则匹配")
    void testQueryParameterRegexMatching() throws Exception {
        Thread.sleep(2000);

        // 创建使用正则匹配查询参数的 stub
        String createStubJson = """
                {
                    "name": "ID查询API",
                    "description": "匹配id参数为数字的请求",
                    "method": "GET",
                    "url": "/api/resource",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "queryParametersPattern": "{\\"id\\": {\\"matches\\": \\"[0-9]+\\"}}",
                    "responseDefinition": "{\\"resource\\": \\"found\\", \\"idType\\": \\"numeric\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试数字ID（应该匹配）
        String[] numericIds = {"123", "1", "999999"};
        for (String id : numericIds) {
            HttpRequest validRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/resource?id=" + id))
                    .GET()
                    .build();

            HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, validResponse.statusCode(), "数字ID应该匹配: " + id);
            assertTrue(validResponse.body().contains("numeric"));
        }

        // 测试非数字ID（不应该匹配）
        String[] nonNumericIds = {"abc", "id123", "123abc"};
        for (String id : nonNumericIds) {
            HttpRequest invalidRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/resource?id=" + id))
                    .GET()
                    .build();

            HttpResponse<String> invalidResponse = httpClient.send(invalidRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(404, invalidResponse.statusCode(), "非数字ID不应该匹配: " + id);
        }
    }

    @Test
    @DisplayName("P1场景12: 查询参数包含匹配")
    void testQueryParameterContainsMatching() throws Exception {
        Thread.sleep(2000);

        // 创建使用包含匹配的 stub
        String createStubJson = """
                {
                    "name": "标签搜索API",
                    "description": "匹配tags参数包含java的请求",
                    "method": "GET",
                    "url": "/api/posts",
                    "urlMatchType": "EQUALS",
                    "enabled": true,
                    "queryParametersPattern": "{\\"tags\\": {\\"contains\\": \\"java\\"}}",
                    "responseDefinition": "{\\"posts\\": [\\"post1\\", \\"post2\\"], \\"tag\\": \\"java\\"}"
                }
                """;

        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/stubs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(createStubJson))
                .build();

        HttpResponse<String> createResponse = httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createResponse.statusCode(), "创建stub应该成功");

        // 测试包含 "java" 的参数值（应该匹配）
        String[] validTags = {"java", "java,python", "spring-java", "javascript"};
        for (String tag : validTags) {
            HttpRequest validRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/api/posts?tags=" + tag))
                    .GET()
                    .build();

            HttpResponse<String> validResponse = httpClient.send(validRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, validResponse.statusCode(), "包含java的标签应该匹配: " + tag);
        }

        // 测试不包含 "java" 的参数值（不应该匹配）
        HttpRequest invalidRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/posts?tags=python"))
                .GET()
                .build();

        HttpResponse<String> invalidResponse = httpClient.send(invalidRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, invalidResponse.statusCode(), "不包含java的标签不应该匹配");
    }
}
