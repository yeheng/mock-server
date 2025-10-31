package com.example.wiremockui.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GlobalExceptionHandler 单元测试
 */
@DisplayName("GlobalExceptionHandler 测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("测试 handleIllegalArgumentException - 处理参数异常")
    void testHandleIllegalArgumentException() {
        // 准备
        IllegalArgumentException ex = new IllegalArgumentException("参数错误");

        // 执行
        var result = exceptionHandler.handleIllegalArgumentException(ex);

        // 验证
        assertNotNull(result);
        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("参数错误", result.getBody().get("message"));
    }

    @Test
    @DisplayName("测试 handleIllegalStateException - 处理状态异常")
    void testHandleIllegalStateException() {
        // 准备
        IllegalStateException ex = new IllegalStateException("状态错误");

        // 执行
        var result = exceptionHandler.handleIllegalStateException(ex);

        // 验证
        assertNotNull(result);
        assertEquals(HttpStatus.CONFLICT, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("状态错误", result.getBody().get("message"));
    }
}
