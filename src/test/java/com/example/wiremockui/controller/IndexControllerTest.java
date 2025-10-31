package com.example.wiremockui.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IndexController 单元测试
 */
@DisplayName("IndexController 测试")
class IndexControllerTest {

    private final IndexController controller = new IndexController();

    @Test
    @DisplayName("测试 index - 返回欢迎页面")
    void testIndex() {
        // 执行
        String result = controller.index();

        // 验证
        assertNotNull(result);
        assertTrue(result.contains("WireMock") || result.contains("index"));
    }
}
