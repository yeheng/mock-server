package io.github.yeheng.wiremock.controller;

import io.github.yeheng.wiremock.service.WireMockManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * HealthController 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HealthController 测试")
class HealthControllerTest {

    @Mock
    private WireMockManager wireMockManager;

    @InjectMocks
    private HealthController controller;

    @Test
    @DisplayName("测试 health - 系统健康")
    void testHealth_Healthy() {
        // 准备
        when(wireMockManager.isRunning()).thenReturn(true);
        when(wireMockManager.getPort()).thenReturn(8081);

        // 执行
        var result = controller.health();

        // 验证
        assertNotNull(result);
        assertTrue(result.getStatusCode().is2xxSuccessful());
        verify(wireMockManager).isRunning();
        verify(wireMockManager).getPort();
    }

    @Test
    @DisplayName("测试 health - 系统不健康")
    void testHealth_Unhealthy() {
        // 准备
        when(wireMockManager.isRunning()).thenReturn(false);
        when(wireMockManager.getPort()).thenReturn(0);

        // 执行
        var result = controller.health();

        // 验证
        assertNotNull(result);
        assertTrue(result.getStatusCode().is2xxSuccessful());
        verify(wireMockManager).isRunning();
        verify(wireMockManager).getPort();
    }

    @Test
    @DisplayName("测试 ping - 健康检查端点")
    void testPing() {
        // 执行
        var result = controller.ping();

        // 验证
        assertNotNull(result);
        assertTrue(result.getStatusCode().is2xxSuccessful());
        assertEquals("pong", result.getBody());
    }
}
