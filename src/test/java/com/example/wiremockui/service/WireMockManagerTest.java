package com.example.wiremockui.service;

import com.example.wiremockui.config.WireMockProperties;
import com.example.wiremockui.entity.StubMapping;
import com.github.tomakehurst.wiremock.WireMockServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WireMockManager 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WireMockManager 测试")
class WireMockManagerTest {

    @Mock
    private WireMockProperties properties;

    @InjectMocks
    private WireMockManager wireMockManager;

    @Mock
    private WireMockServer wireMockServer;

    private StubMapping testStub;
    private StubMapping disabledStub;

    @BeforeEach
    void setUp() {
        // 创建测试用的 StubMapping
        testStub = new StubMapping();
        testStub.setId(1L);
        testStub.setName("用户查询接口");
        testStub.setMethod("GET");
        testStub.setUrl("/api/users");
        testStub.setEnabled(true);

        disabledStub = new StubMapping();
        disabledStub.setId(2L);
        disabledStub.setName("禁用接口");
        disabledStub.setMethod("POST");
        disabledStub.setUrl("/api/disabled");
        disabledStub.setEnabled(false);
    }

    @AfterEach
    void tearDown() {
        // 清理资源
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("测试 initializeEmbeddedMode - 正常初始化")
    void testInitializeEmbeddedMode_Success() {
        // 准备
        when(properties.getPort()).thenReturn(8081);
        wireMockManager = spy(wireMockManager);

        // 验证 - 方法调用成功，没有抛出异常
        assertDoesNotThrow(() -> {
            wireMockManager.initializeEmbeddedMode();
        });
    }

    @Test
    @DisplayName("测试 initializeEmbeddedMode - 端口为0使用默认端口")
    void testInitializeEmbeddedMode_DefaultPort() {
        // 准备
        when(properties.getPort()).thenReturn(0);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.initializeEmbeddedMode();
        });
    }

    @Test
    @DisplayName("测试 getPort - 服务器运行中")
    void testGetPort_ServerRunning() {
        // 设置 WireMockServer 状态
        setServerRunning(true, 8081);

        // 验证
        assertEquals(8081, wireMockManager.getPort());
    }

    @Test
    @DisplayName("测试 getPort - 服务器未运行")
    void testGetPort_ServerNotRunning() {
        // 设置服务器未运行
        setServerRunning(false, 0);

        // 验证
        assertEquals(0, wireMockManager.getPort());
    }

    @Test
    @DisplayName("测试 isRunning - 服务器运行中")
    void testIsRunning_ServerRunning() {
        // 设置 WireMockServer 状态
        setServerRunning(true, 8081);

        // 验证
        assertTrue(wireMockManager.isRunning());
    }

    @Test
    @DisplayName("测试 isRunning - 服务器未运行")
    void testIsRunning_ServerNotRunning() {
        // 设置服务器未运行
        setServerRunning(false, 0);

        // 验证
        assertFalse(wireMockManager.isRunning());
    }

    @Test
    @DisplayName("测试 addStubMapping - 启用状态的 Stub")
    void testAddStubMapping_EnabledStub() {
        // 准备
        setServerRunning(true, 8081);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.addStubMapping(testStub);
        });
    }

    @Test
    @DisplayName("测试 addStubMapping - 禁用状态的 Stub")
    void testAddStubMapping_DisabledStub() {
        // 执行 - 禁用的 stub 不应该被添加到 WireMock
        assertDoesNotThrow(() -> {
            wireMockManager.addStubMapping(disabledStub);
        });
    }

    @Test
    @DisplayName("测试 addStubMapping - 不同 HTTP 方法")
    void testAddStubMapping_DifferentHttpMethods() {
        // 准备
        setServerRunning(true, 8081);

        String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH"};
        for (String method : methods) {
            StubMapping stub = new StubMapping();
            stub.setName(method + " 接口");
            stub.setMethod(method);
            stub.setUrl("/api/test");
            stub.setEnabled(true);

            // 执行 & 验证
            assertDoesNotThrow(() -> {
                wireMockManager.addStubMapping(stub);
            });
        }
    }

    @Test
    @DisplayName("测试 addStubMapping - 服务器未运行抛出异常")
    void testAddStubMapping_ServerNotRunning() {
        // 设置服务器未运行
        setServerRunning(false, 0);

        // 执行 & 验证
        assertThrows(RuntimeException.class, () -> {
            wireMockManager.addStubMapping(testStub);
        });
    }

    @Test
    @DisplayName("测试 removeStubMapping - 成功删除")
    void testRemoveStubMapping_Success() {
        // 准备
        setServerRunning(true, 8081);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.removeStubMapping(testStub);
        });
    }

    @Test
    @DisplayName("测试 removeStubMapping - 服务器未运行")
    void testRemoveStubMapping_ServerNotRunning() {
        // 设置服务器未运行
        setServerRunning(false, 0);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.removeStubMapping(testStub);
        });
    }

    @Test
    @DisplayName("测试 reloadAllStubs - 重新加载所有 Stubs")
    void testReloadAllStubs() {
        // 准备
        List<StubMapping> stubs = Arrays.asList(testStub, disabledStub);
        setServerRunning(true, 8081);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.reloadAllStubs(stubs);
        });
    }

    @Test
    @DisplayName("测试 reloadAllStubs - 服务器未运行")
    void testReloadAllStubs_ServerNotRunning() {
        // 准备
        List<StubMapping> stubs = Arrays.asList(testStub);
        setServerRunning(false, 0);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.reloadAllStubs(stubs);
        });
    }

    @Test
    @DisplayName("测试 getRequestLogs - 服务器运行中")
    void testGetRequestLogs_ServerRunning() {
        // 准备
        setServerRunning(true, 8081);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            List<?> logs = wireMockManager.getRequestLogs();
            assertNotNull(logs);
        });
    }

    @Test
    @DisplayName("测试 getRequestLogs - 服务器未运行返回空列表")
    void testGetRequestLogs_ServerNotRunning() {
        // 设置服务器未运行
        setServerRunning(false, 0);

        // 执行
        List<?> logs = wireMockManager.getRequestLogs();

        // 验证
        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }

    @Test
    @DisplayName("测试 clearRequestLogs - 服务器运行中")
    void testClearRequestLogs_ServerRunning() {
        // 准备
        setServerRunning(true, 8081);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.clearRequestLogs();
        });
    }

    @Test
    @DisplayName("测试 clearRequestLogs - 服务器未运行")
    void testClearRequestLogs_ServerNotRunning() {
        // 设置服务器未运行
        setServerRunning(false, 0);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.clearRequestLogs();
        });
    }

    @Test
    @DisplayName("测试 reset - 服务器运行中")
    void testReset_ServerRunning() {
        // 准备
        setServerRunning(true, 8081);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.reset();
        });
    }

    @Test
    @DisplayName("测试 reset - 服务器未运行")
    void testReset_ServerNotRunning() {
        // 设置服务器未运行
        setServerRunning(false, 0);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.reset();
        });
    }

    @Test
    @DisplayName("测试 handleRequest - 服务器运行中")
    void testHandleRequest_ServerRunning() throws IOException {
        // 准备
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");

        // 设置服务器运行
        setServerRunning(true, 8081);

        // 执行
        wireMockManager.handleRequest(request, response);

        // 验证
        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).setHeader("Content-Type", "application/json");
        writer.flush();
        String responseBody = stringWriter.toString();
        assertNotNull(responseBody);
        assertTrue(responseBody.contains("WireMock Mock Response"));
    }

    @Test
    @DisplayName("测试 handleRequest - 服务器未运行")
    void testHandleRequest_ServerNotRunning() throws IOException {
        // 准备
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        // 设置服务器未运行
        setServerRunning(false, 0);

        // 执行
        wireMockManager.handleRequest(request, response);

        // 验证
        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        writer.flush();
        String responseBody = stringWriter.toString();
        assertEquals("WireMock server is not running", responseBody);
    }

    @Test
    @DisplayName("测试 handleRequest - 发生异常")
    void testHandleRequest_Exception() throws IOException {
        // 准备
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenThrow(new RuntimeException("模拟异常"));
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);

        // 设置服务器运行
        setServerRunning(true, 8081);

        // 执行
        wireMockManager.handleRequest(request, response);

        // 验证
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        writer.flush();
        String responseBody = stringWriter.toString();
        assertTrue(responseBody.contains("Internal server error"));
    }

    @Test
    @DisplayName("测试 getWireMockServer")
    void testGetWireMockServer() {
        // 准备
        setServerRunning(true, 8081);

        // 执行
        WireMockServer server = wireMockManager.getWireMockServer();

        // 验证
        assertNotNull(server);
    }

    @Test
    @DisplayName("测试 shutdownWireMock - 服务器运行中")
    void testShutdownWireMock_ServerRunning() {
        // 准备
        setServerRunning(true, 8081);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.shutdownWireMock();
        });
    }

    @Test
    @DisplayName("测试 shutdownWireMock - 服务器未运行")
    void testShutdownWireMock_ServerNotRunning() {
        // 设置服务器未运行
        setServerRunning(false, 0);

        // 执行 & 验证
        assertDoesNotThrow(() -> {
            wireMockManager.shutdownWireMock();
        });
    }

    // 辅助方法：设置服务器运行状态
    private void setServerRunning(boolean running, int port) {
        try {
            var field = WireMockManager.class.getDeclaredField("isRunning");
            field.setAccessible(true);
            field.set(wireMockManager, running);

            field = WireMockManager.class.getDeclaredField("port");
            field.setAccessible(true);
            field.set(wireMockManager, port);

            field = WireMockManager.class.getDeclaredField("wireMockServer");
            field.setAccessible(true);
            if (running) {
                field.set(wireMockManager, wireMockServer);
            } else {
                field.set(wireMockManager, null);
            }
        } catch (Exception e) {
            throw new RuntimeException("设置服务器状态失败", e);
        }
    }
}
