package io.github.yeheng.wiremock.test;

import lombok.extern.slf4j.Slf4j;

/**
 * 测试等待工具类
 * 替代不稳定的 Thread.sleep，提供条件等待机制
 */
@Slf4j
public class WaitUtils {

    /**
     * 等待直到条件为真（最大超时时间）
     *
     * @param condition 条件检查函数
     * @param timeoutMs 最大等待时间（毫秒）
     * @param intervalMs 检查间隔（毫秒）
     * @return 条件是否满足
     */
    public static boolean waitForCondition(BooleanSupplier condition, long timeoutMs, long intervalMs) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMs;

        while (System.currentTimeMillis() < endTime) {
            try {
                if (condition.getAsBoolean()) {
                    long actualWait = System.currentTimeMillis() - startTime;
                    log.debug("条件满足，等待时间: {}ms", actualWait);
                    return true;
                }
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待被中断", e);
                return false;
            }
        }

        log.warn("等待超时: {}ms", timeoutMs);
        return false;
    }

    /**
     * 快速等待（默认500ms）
     *
     * @param condition 条件检查函数
     * @return 条件是否满足
     */
    public static boolean waitForCondition(BooleanSupplier condition) {
        return waitForCondition(condition, 500, 10);
    }

    /**
     * 等待 WireMock 启动
     *
     * @param wireMockManager WireMockManager 实例
     * @return 是否启动成功
     */
    public static boolean waitForWireMockStart(io.github.yeheng.wiremock.service.WireMockManager wireMockManager) {
        return waitForCondition(() -> {
            try {
                return wireMockManager.isRunning();
            } catch (Exception e) {
                return false;
            }
        }, 5000, 50);
    }

    /**
     * 等待 HTTP 端点就绪
     *
     * @param url 检查的 URL
     * @param timeoutMs 超时时间
     * @return 是否就绪
     */
    public static boolean waitForHttpEndpoint(String url, long timeoutMs) {
        return waitForCondition(() -> {
            try {
                java.net.HttpURLConnection connection =
                    (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(500);
                connection.setReadTimeout(500);
                int responseCode = connection.getResponseCode();
                connection.disconnect();
                return responseCode == 200;
            } catch (Exception e) {
                return false;
            }
        }, timeoutMs, 100);
    }

    @FunctionalInterface
    public interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
