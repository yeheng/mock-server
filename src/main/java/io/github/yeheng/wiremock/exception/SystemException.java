package io.github.yeheng.wiremock.exception;

/**
 * 系统异常
 * 用于处理系统级别的错误
 */
public class SystemException extends RuntimeException {
    private final String errorCode;

    public SystemException(String message) {
        super(message);
        this.errorCode = "SYSTEM_ERROR";
    }

    public SystemException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "SYSTEM_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
