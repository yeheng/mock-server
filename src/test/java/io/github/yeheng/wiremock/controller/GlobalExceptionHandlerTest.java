package io.github.yeheng.wiremock.controller;

import io.github.yeheng.wiremock.exception.BusinessException;
import io.github.yeheng.wiremock.exception.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        Map<String, Object> result = exceptionHandler.handleIllegalArgumentException(ex);

        // 验证
        assertNotNull(result);
        assertEquals("参数错误", result.get("message"));
        assertEquals("参数错误", result.get("error"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    @DisplayName("测试 handleIllegalStateException - 处理状态异常")
    void testHandleIllegalStateException() {
        // 准备
        IllegalStateException ex = new IllegalStateException("状态错误");

        // 执行
        Map<String, Object> result = exceptionHandler.handleIllegalStateException(ex);

        // 验证
        assertNotNull(result);
        assertEquals("状态错误", result.get("message"));
        assertEquals("状态错误", result.get("error"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    @DisplayName("测试 handleGenericException - 处理通用异常")
    void testHandleGenericException() {
        // 准备
        Exception ex = new RuntimeException("系统错误");

        // 执行
        Map<String, Object> result = exceptionHandler.handleGenericException(ex);

        // 验证
        assertNotNull(result);
        assertEquals("系统内部错误，请稍后重试", result.get("message"));
        assertEquals("UNEXPECTED_ERROR", result.get("errorCode"));
        assertEquals("系统内部错误，请稍后重试", result.get("error"));
        assertEquals(500, result.get("status"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    @DisplayName("测试 handleBusinessException - 处理业务异常")
    void testHandleBusinessException() {
        // 准备
        BusinessException ex = new BusinessException("业务逻辑错误", "INVALID_OPERATION");

        // 执行
        Map<String, Object> result = exceptionHandler.handleBusinessException(ex);

        // 验证
        assertNotNull(result);
        assertEquals("业务逻辑错误", result.get("message"));
        assertEquals("INVALID_OPERATION", result.get("errorCode"));
        assertEquals("业务逻辑错误", result.get("error"));
        assertEquals(400, result.get("status"));
        assertNotNull(result.get("timestamp"));
    }

    @Test
    @DisplayName("测试 handleSystemException - 处理系统异常")
    void testHandleSystemException() {
        // 准备
        SystemException ex = new SystemException("系统内部错误", "INTERNAL_ERROR");

        // 执行
        Map<String, Object> result = exceptionHandler.handleSystemException(ex);

        // 验证
        assertNotNull(result);
        assertEquals("系统内部错误", result.get("message"));
        assertEquals("INTERNAL_ERROR", result.get("errorCode"));
        assertEquals("系统内部错误", result.get("error"));
        assertEquals(500, result.get("status"));
        assertNotNull(result.get("timestamp"));
    }
}
