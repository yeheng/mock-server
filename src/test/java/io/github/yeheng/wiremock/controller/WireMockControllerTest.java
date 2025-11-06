package io.github.yeheng.wiremock.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.yeheng.wiremock.service.WireMockManager;

/**
 * WireMockController 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WireMockController 测试")
class WireMockControllerTest {

    @Mock
    private WireMockManager wireMockManager;

    @InjectMocks
    private WireMockController controller;

    @Test
    @DisplayName("测试 getStatus - 获取 WireMock 状态")
    void testGetStatus() {
        // 准备
        when(wireMockManager.isRunning()).thenReturn(true);
        when(wireMockManager.getPort()).thenReturn(8081);

        // 执行
        var result = controller.getStatus();

        // 验证
        assertNotNull(result);
        assertTrue(result.getStatusCode().is2xxSuccessful());
        var body = result.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("running"));
        assertEquals(8081, body.get("port"));
        verify(wireMockManager).isRunning();
        verify(wireMockManager, atLeast(1)).getPort();
    }

    @Test
    @DisplayName("测试 reset - 重置 WireMock 服务器")
    void testReset() {
        // 准备
        doNothing().when(wireMockManager).reset();

        // 执行
        var result = controller.reset();

        // 验证
        assertNotNull(result);
        assertTrue(result.getStatusCode().is2xxSuccessful());
        verify(wireMockManager).reset();
    }

    @Test
    @DisplayName("测试 reset - 重置失败")
    void testReset_Failure() {
        // 准备
        doThrow(new RuntimeException("重置失败")).when(wireMockManager).reset();

        // 执行
        var result = controller.reset();

        // 验证
        assertNotNull(result);
        assertTrue(result.getStatusCode().is4xxClientError());
        verify(wireMockManager).reset();
    }
}
