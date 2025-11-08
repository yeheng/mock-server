package io.github.yeheng.wiremock.controller;

import io.github.yeheng.wiremock.exception.BusinessException;
import io.github.yeheng.wiremock.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * 统一处理业务异常和系统异常，提供统一的错误响应格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常 - 返回400 Bad Request
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBusinessException(BusinessException e) {
        log.warn("业务错误 [{}]: {}", e.getErrorCode(), e.getMessage());
        return buildErrorResponse(e.getErrorCode(), e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }

    /**
     * 处理IllegalArgumentException - 返回400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数错误: {}", e.getMessage());
        return buildErrorResponse("PARAM_ERROR", e.getMessage(), HttpStatus.BAD_REQUEST.value());
    }

    /**
     * 处理IllegalStateException - 返回409 Conflict
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleIllegalStateException(IllegalStateException e) {
        log.warn("状态错误: {}", e.getMessage());
        return buildErrorResponse("STATE_ERROR", e.getMessage(), HttpStatus.CONFLICT.value());
    }

    /**
     * 处理系统异常 - 返回500 Internal Server Error
     */
    @ExceptionHandler(SystemException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleSystemException(SystemException e) {
        log.error("系统错误 [{}]: {}", e.getErrorCode(), e.getMessage(), e);
        return buildErrorResponse(e.getErrorCode(), e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    /**
     * 处理所有其他未预期异常 - 返回500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGenericException(Exception e) {
        log.error("未预期的系统异常: {}", e.getMessage(), e);
        return buildErrorResponse("UNEXPECTED_ERROR", "系统内部错误，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    private Map<String, Object> buildErrorResponse(String errorCode, String message, int status) {
        Map<String, Object> response = new HashMap<>();
        response.put("errorCode", errorCode);
        response.put("error", message);
        response.put("message", message);
        response.put("status", status);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
