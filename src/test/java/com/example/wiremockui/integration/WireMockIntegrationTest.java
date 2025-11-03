package com.example.wiremockui.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import com.example.wiremockui.WiremockUiApplication;
import com.example.wiremockui.entity.StubMapping;
import com.example.wiremockui.repository.StubMappingRepository;
import com.example.wiremockui.service.StubMappingService;
import com.example.wiremockui.service.WireMockManager;

/**
 * WireMock 集成测试
 * 测试真实的 stub 添加、刷新和调用流程
 * 集成模式：WireMock 和 Spring Boot 运行在同一个 Undertow 容器中
 */
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb",
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
@DisplayName("WireMock 集成测试 - 集成模式")
class WireMockIntegrationTest {

    @Autowired
    private StubMappingRepository stubMappingRepository;

    @Autowired
    private StubMappingService stubMappingService;

    @Autowired
    private WireMockManager wireMockManager;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() {
        // 清理所有 stubs
        try {
            stubMappingRepository.deleteAll();
            wireMockManager.reset();
        } catch (Exception e) {
            // 忽略清理错误
        }
    }

    @Test
    @DisplayName("测试创建 stub 并通过 WireMock 调用 - 集成模式")
    void testCreateStubAndCall_IntegratedMode() throws Exception {
        // 等待 WireMock 启动
        Thread.sleep(2000);

        // 1. 创建 stub 配置
        StubMapping stub = new StubMapping();
        stub.setName("测试 Stub");
        stub.setDescription("集成测试创建的 stub");
        stub.setMethod("GET");
        stub.setUrl("/api/test");
        stub.setEnabled(true);
        stub.setResponseDefinition("""
                {
                    "status": "success",
                    "message": "集成测试成功",
                    "data": {
                        "id": 1,
                        "name": "测试用户"
                    }
                }
                """);

        // 2. 使用服务层创建 stub
        StubMapping savedStub = stubMappingService.createStub(stub);
        assertNotNull(savedStub.getId());

        // 3. 手动刷新 WireMock（加载所有 stubs）
        wireMockManager.reloadAllStubs(stubMappingRepository.findAll());

        // 4. 等待 WireMock 刷新
        Thread.sleep(1000);

        // 5. 通过 HTTP 调用 stub 端点
        // 使用相同的端口和容器
        String stubUrl = "http://localhost:" + port + "/api/test";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(stubUrl))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 6. 验证响应
        assertEquals(200, response.statusCode());
        String responseBody = response.body();
        System.out.println("GET响应: " + responseBody);
        assertNotNull(responseBody);

        // 验证响应包含期望的内容
        assertTrue(responseBody.contains("status"), "缺少status字段");
        assertTrue(responseBody.contains("success"), "缺少success值");
        assertTrue(responseBody.contains("message"), "缺少message字段");
        assertTrue(responseBody.contains("集成测试成功"), "缺少message值");
        assertTrue(responseBody.contains("id"), "缺少id字段");
        assertTrue(responseBody.contains("1"), "缺少id值");
        assertTrue(responseBody.contains("name"), "缺少name字段");
        assertTrue(responseBody.contains("测试用户"), "缺少name值");
    }

    @Test
    @DisplayName("测试创建 POST stub 并调用 - 集成模式")
    void testCreatePostStubAndCall_IntegratedMode() throws Exception {
        // 等待 WireMock 启动
        Thread.sleep(2000);

        // 1. 创建 POST stub
        StubMapping stub = new StubMapping();
        stub.setName("POST 测试 Stub");
        stub.setDescription("测试 POST 请求");
        stub.setMethod("POST");
        stub.setUrl("/api/users");
        stub.setEnabled(true);
        stub.setResponseDefinition("""
                {
                    "status": "created",
                    "userId": 123,
                    "timestamp": "2023-10-31T12:00:00"
                }
                """);

        // 2. 使用服务层创建 stub
        StubMapping savedStub = stubMappingService.createStub(stub);
        assertNotNull(savedStub.getId());

        // 3. 刷新 WireMock
        wireMockManager.reloadAllStubs(stubMappingRepository.findAll());
        Thread.sleep(1000);

        // 4. 调用 WireMock
        String stubUrl = "http://localhost:" + port + "/api/users";
        String requestBody = "{\"name\": \"张三\", \"email\": \"zhangsan@example.com\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(stubUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 5. 验证响应
        assertEquals(200, response.statusCode());
        String responseBody = response.body();
        System.out.println("POST响应: " + responseBody);
        assertNotNull(responseBody);

        // 验证响应包含期望的内容
        assertTrue(responseBody.contains("status"), "缺少status字段");
        assertTrue(responseBody.contains("created"), "缺少created值");
        assertTrue(responseBody.contains("userId"), "缺少userId字段");
        assertTrue(responseBody.contains("123"), "缺少userId值");
        assertTrue(responseBody.contains("timestamp"), "缺少timestamp字段");
        assertTrue(responseBody.contains("2023-10-31T12:00:00"), "缺少timestamp值");
    }
}
