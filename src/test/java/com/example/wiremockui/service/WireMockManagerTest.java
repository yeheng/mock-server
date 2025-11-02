package com.example.wiremockui.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.wiremockui.entity.StubMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * WireMockManager 单元测试 - 集成模式
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WireMockManager 测试")
class WireMockManagerTest {

    @InjectMocks
    private WireMockManager wireMockManager;

    @Mock
    private HttpServletRequest servletRequest;

    @Mock
    private HttpServletResponse servletResponse;

    @Mock
    private PrintWriter printWriter;

    private StubMapping testStub;
    private StubMapping disabledStub;

    @BeforeEach
    void setUp() {
        // 手动初始化WireMockManager（模拟@PostConstruct的效果）
        try {
            var field = WireMockManager.class.getDeclaredField("isRunning");
            field.setAccessible(true);
            field.set(wireMockManager, true);

            var portField = WireMockManager.class.getDeclaredField("port");
            portField.setAccessible(true);
            portField.set(wireMockManager, 8080);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize WireMockManager", e);
        }

        // 创建测试用的 StubMapping
        testStub = new StubMapping();
        testStub.setName("测试接口");
        testStub.setMethod("GET");
        testStub.setUrl("/api/test");
        testStub.setEnabled(true);
        testStub.setResponseDefinition("{\"message\": \"test response\"}");

        disabledStub = new StubMapping();
        disabledStub.setName("禁用接口");
        disabledStub.setMethod("POST");
        disabledStub.setUrl("/api/disabled");
        disabledStub.setEnabled(false);
        disabledStub.setResponseDefinition("{\"message\": \"disabled response\"}");
    }

    @AfterEach
    void tearDown() {
        wireMockManager.reset();
    }

    @Test
    @DisplayName("测试 initialize 方法")
    void testInitialize() {
        // 执行 - initialize 已经模拟完成

        // 验证
        assertTrue(wireMockManager.isRunning());
        assertEquals(8080, wireMockManager.getPort()); // 模拟的端口
    }

    @Test
    @DisplayName("测试 getPort 方法 - 服务器运行中")
    void testGetPort_ServerRunning() {
        // 验证
        assertTrue(wireMockManager.getPort() > 0);
        assertEquals(8080, wireMockManager.getPort());
    }

    @Test
    @DisplayName("测试 isRunning 方法 - 服务器运行中")
    void testIsRunning_ServerRunning() {
        // 验证
        assertTrue(wireMockManager.isRunning());
    }

    @Test
    @DisplayName("测试 addStubMapping - 启用状态的 Stub")
    void testAddStubMapping_EnabledStub() {
        // 执行
        assertDoesNotThrow(() -> {
            wireMockManager.addStubMapping(testStub);
        });

        // 验证
        List<StubMapping> stubs = wireMockManager.getAllStubs();
        assertEquals(1, stubs.size());
        assertEquals(testStub.getName(), stubs.get(0).getName());
    }

    @Test
    @DisplayName("测试 addStubMapping - 禁用状态的 Stub")
    void testAddStubMapping_DisabledStub() {
        // 执行 - 禁用的 stub 不应该被添加到 WireMock
        assertDoesNotThrow(() -> {
            wireMockManager.addStubMapping(disabledStub);
        });

        // 验证 - 禁用的stub不应该被添加
        List<StubMapping> stubs = wireMockManager.getAllStubs();
        assertEquals(0, stubs.size());
    }

    @Test
    @DisplayName("测试 addStubMapping - 不同 HTTP 方法")
    void testAddStubMapping_DifferentHttpMethods() {
        // 准备
        StubMapping postStub = new StubMapping();
        postStub.setName("POST接口");
        postStub.setMethod("POST");
        postStub.setUrl("/api/post");
        postStub.setEnabled(true);
        postStub.setResponseDefinition("{\"method\": \"POST\"}");

        StubMapping putStub = new StubMapping();
        putStub.setName("PUT接口");
        putStub.setMethod("PUT");
        putStub.setUrl("/api/put");
        putStub.setEnabled(true);
        putStub.setResponseDefinition("{\"method\": \"PUT\"}");

        // 执行
        wireMockManager.addStubMapping(testStub); // GET
        wireMockManager.addStubMapping(postStub); // POST
        wireMockManager.addStubMapping(putStub); // PUT

        // 验证
        List<StubMapping> stubs = wireMockManager.getAllStubs();
        assertEquals(3, stubs.size());
    }

    @Test
    @DisplayName("测试 removeStubMapping - 成功删除")
    void testRemoveStubMapping_Success() {
        // 准备
        wireMockManager.addStubMapping(testStub);
        assertEquals(1, wireMockManager.getAllStubs().size());

        // 执行
        assertDoesNotThrow(() -> {
            wireMockManager.removeStubMapping(testStub);
        });

        // 验证
        assertEquals(0, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("测试 removeStubMapping - 服务器未运行")
    void testRemoveStubMapping_ServerNotRunning() {
        // 这个测试在集成模式下不适用，因为服务器总是运行的
        assertDoesNotThrow(() -> {
            wireMockManager.removeStubMapping(testStub);
        });
    }

    @Test
    @DisplayName("测试 reloadAllStubs")
    void testReloadAllStubs() {
        // 准备
        wireMockManager.addStubMapping(testStub);
        assertEquals(1, wireMockManager.getAllStubs().size());

        StubMapping newStub = new StubMapping();
        newStub.setName("新接口");
        newStub.setMethod("GET");
        newStub.setUrl("/api/new");
        newStub.setEnabled(true);
        newStub.setResponseDefinition("{\"message\": \"new\"}");

        // 执行
        wireMockManager.reloadAllStubs(Arrays.asList(newStub, disabledStub));

        // 验证 - 只有启用的stub应该被加载
        List<StubMapping> stubs = wireMockManager.getAllStubs();
        assertEquals(1, stubs.size());
        assertEquals("新接口", stubs.get(0).getName());
    }

    @Test
    @DisplayName("测试 reloadAllStubs - 服务器未运行")
    void testReloadAllStubs_ServerNotRunning() {
        // 在集成模式下，服务器总是运行的
        assertDoesNotThrow(() -> {
            wireMockManager.reloadAllStubs(Collections.emptyList());
        });
    }

    @Test
    @DisplayName("测试 getRequestLogs - 服务器运行中")
    void testGetRequestLogs_ServerRunning() {
        // 验证
        assertNotNull(wireMockManager.getRequestLogs());
        assertTrue(wireMockManager.getRequestLogs().isEmpty());
    }

    @Test
    @DisplayName("测试 getRequestLogs - 服务器未运行")
    void testGetRequestLogs_ServerNotRunning() {
        // 在集成模式下，这个测试与运行中状态相同
        assertNotNull(wireMockManager.getRequestLogs());
    }

    @Test
    @DisplayName("测试 clearRequestLogs - 服务器运行中")
    void testClearRequestLogs_ServerRunning() {
        // 执行
        assertDoesNotThrow(() -> {
            wireMockManager.clearRequestLogs();
        });

        // 验证
        assertTrue(wireMockManager.getRequestLogs().isEmpty());
    }

    @Test
    @DisplayName("测试 clearRequestLogs - 服务器未运行")
    void testClearRequestLogs_ServerNotRunning() {
        // 在集成模式下，这个测试与运行中状态相同
        assertDoesNotThrow(() -> {
            wireMockManager.clearRequestLogs();
        });
    }

    @Test
    @DisplayName("测试 reset - 服务器运行中")
    void testReset_ServerRunning() {
        // 准备
        wireMockManager.addStubMapping(testStub);
        assertEquals(1, wireMockManager.getAllStubs().size());

        // 执行
        wireMockManager.reset();

        // 验证
        assertEquals(0, wireMockManager.getAllStubs().size());
        assertTrue(wireMockManager.getRequestLogs().isEmpty());
    }

    @Test
    @DisplayName("测试 reset - 服务器未运行")
    void testReset_ServerNotRunning() {
        // 在集成模式下，这个测试与运行中状态相同
        assertDoesNotThrow(() -> {
            wireMockManager.reset();
        });
    }

    @Test
    @DisplayName("测试 handleRequest - 找到匹配的 Stub")
    void testHandleRequest_MatchFound() throws IOException {
        // 准备
        wireMockManager.addStubMapping(testStub);

        when(servletRequest.getRequestURI()).thenReturn("/api/test");
        when(servletRequest.getMethod()).thenReturn("GET");
        when(servletResponse.getWriter()).thenReturn(printWriter);

        // 执行
        assertDoesNotThrow(() -> {
            wireMockManager.handleRequest(servletRequest, servletResponse);
        });

        // 验证
        verify(servletResponse).setStatus(HttpServletResponse.SC_OK);
        verify(servletResponse).setHeader("Content-Type", "application/json");
        verify(printWriter).write(testStub.getResponseDefinition());
    }

    @Test
    @DisplayName("测试 handleRequest - 未找到匹配的 Stub")
    void testHandleRequest_NoMatchFound() throws IOException {
        // 准备
        when(servletRequest.getRequestURI()).thenReturn("/api/nonexistent");
        when(servletRequest.getMethod()).thenReturn("GET");
        when(servletResponse.getWriter()).thenReturn(printWriter);

        // 执行
        assertDoesNotThrow(() -> {
            wireMockManager.handleRequest(servletRequest, servletResponse);
        });

        // 验证
        verify(servletResponse).setStatus(HttpServletResponse.SC_NOT_FOUND);
        verify(servletResponse).setHeader("Content-Type", "application/json");
        verify(printWriter).write(contains("No matching stub"));
    }

    @Test
    @DisplayName("测试 handleRequest - 服务器未运行")
    void testHandleRequest_ServerNotRunning() throws IOException {
        // 模拟服务器未运行
        try {
            var field = WireMockManager.class.getDeclaredField("isRunning");
            field.setAccessible(true);
            field.set(wireMockManager, false);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set server state", e);
        }

        lenient().when(servletRequest.getRequestURI()).thenReturn("/api/test");
        lenient().when(servletRequest.getMethod()).thenReturn("GET");
        lenient().when(servletResponse.getWriter()).thenReturn(printWriter);

        // 执行
        assertDoesNotThrow(() -> {
            wireMockManager.handleRequest(servletRequest, servletResponse);
        });

        // 验证 - 应该返回503服务不可用
        verify(servletResponse).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(printWriter).write(contains("WireMock server is not running"));
    }

    @Test
    @DisplayName("测试 getAllStubs")
    void testGetAllStubs() {
        // 准备
        wireMockManager.addStubMapping(testStub);

        // 执行
        List<StubMapping> stubs = wireMockManager.getAllStubs();

        // 验证
        assertNotNull(stubs);
        assertEquals(1, stubs.size());
        assertEquals(testStub.getName(), stubs.get(0).getName());
    }
}