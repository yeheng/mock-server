package io.github.yeheng.wiremock.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控和基准测试工具
 * 跟踪关键性能指标和请求统计
 */
@Slf4j
@Component
public class PerformanceMonitor {

    // 请求计数器
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    // 响应时间统计
    private final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());

    // 吞吐量统计（每秒请求数）
    private final AtomicLong requestsPerSecond = new AtomicLong(0);
    private final AtomicLong lastSecondTimestamp = new AtomicLong(System.currentTimeMillis());

    // 操作计数
    private final Map<String, AtomicLong> operationCounts = new ConcurrentHashMap<>();

    // GC 统计
    private long lastGcCount = 0;

    /**
     * 记录请求开始时间
     */
    public long startRequest() {
        totalRequests.incrementAndGet();
        return System.nanoTime();
    }

    /**
     * 记录请求完成
     */
    public void endRequest(long startTime, boolean success) {
        long duration = System.nanoTime() - startTime;
        long durationMs = duration / 1_000_000;

        // 记录响应时间
        synchronized (responseTimes) {
            responseTimes.add(durationMs);
            // 保留最近10000次请求的响应时间
            int maxResponseTimes = 10000;
            if (responseTimes.size() > maxResponseTimes) {
                responseTimes.removeFirst();
            }
        }

        // 更新计数器
        if (success) {
            successfulRequests.incrementAndGet();
        } else {
            failedRequests.incrementAndGet();
        }

        // 更新每秒请求数
        updateRequestsPerSecond();
    }

    /**
     * 记录操作
     */
    public void recordOperation(String operation) {
        operationCounts.computeIfAbsent(operation, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 获取性能统计报告
     */
    public PerformanceStats getStats() {
        synchronized (responseTimes) {
            long[] times = responseTimes.stream().mapToLong(Long::longValue).toArray();
            Arrays.sort(times);

            return PerformanceStats.builder()
                .totalRequests(totalRequests.get())
                .successfulRequests(successfulRequests.get())
                .failedRequests(failedRequests.get())
                .successRate(totalRequests.get() > 0 ?
                    (double) successfulRequests.get() / totalRequests.get() * 100 : 0)
                .avgResponseTime(times.length > 0 ?
                    Arrays.stream(times).average().orElse(0) : 0)
                .minResponseTime(times.length > 0 ? times[0] : 0)
                .maxResponseTime(times.length > 0 ? times[times.length - 1] : 0)
                .p95ResponseTime(times.length > 0 ?
                    times[(int) (times.length * 0.95)] : 0)
                .p99ResponseTime(times.length > 0 ?
                    times[(int) (times.length * 0.99)] : 0)
                .requestsPerSecond(requestsPerSecond.get())
                .operationCounts(operationCounts.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get())))
                .build();
        }
    }

    /**
     * 执行简单的性能基准测试
     */
    public BenchmarkResult runBenchmark(int requestCount, Runnable requestExecutor) {
        log.info("开始执行性能基准测试：{} 次请求", requestCount);

        long startTime = System.currentTimeMillis();
        long gcBefore = getGcCount();

        // 重置计数器
        long beforeTotal = totalRequests.get();
        long beforeSuccess = successfulRequests.get();

        // 执行请求
        for (int i = 0; i < requestCount; i++) {
            requestExecutor.run();
        }

        long endTime = System.currentTimeMillis();
        long gcAfter = getGcCount();

        long actualRequests = totalRequests.get() - beforeTotal;
        long actualSuccess = successfulRequests.get() - beforeSuccess;
        long duration = endTime - startTime;
        double throughput = (double) actualRequests / (duration / 1000.0);

        PerformanceStats stats = getStats();

        BenchmarkResult result = BenchmarkResult.builder()
            .requestCount((int) actualRequests)
            .successCount(actualSuccess)
            .durationMs(duration)
            .throughputRps(throughput)
            .avgResponseTime(stats.getAvgResponseTime())
            .p95ResponseTime(stats.getP95ResponseTime())
            .p99ResponseTime(stats.getP99ResponseTime())
            .gcCount(gcAfter - gcBefore)
            .build();

        log.info("基准测试完成: {}", result);
        return result;
    }

    /**
     * 重置所有统计信息
     */
    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        responseTimes.clear();
        operationCounts.clear();
        lastGcCount = getGcCount();
    }

    /**
     * 更新每秒请求数
     */
    private void updateRequestsPerSecond() {
        long now = System.currentTimeMillis();
        long last = lastSecondTimestamp.get();

        if (now - last >= 1000) {
            requestsPerSecond.set(totalRequests.get());
            lastSecondTimestamp.set(now);
        }
    }

    /**
     * 获取 GC 次数
     */
    private long getGcCount() {
        long totalGcCount = 0;
        for (java.lang.management.GarbageCollectorMXBean gc :
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = gc.getCollectionCount();
            if (count >= 0) {
                totalGcCount += count;
            }
        }
        return totalGcCount;
    }

    public long getLastGcCount() {
        return lastGcCount;
    }

    public void setLastGcCount(long lastGcCount) {
        this.lastGcCount = lastGcCount;
    }

    /**
     * 性能统计结果
     */
    @Getter
    public static class PerformanceStats {
        private final long totalRequests;
        private final long successfulRequests;
        private final long failedRequests;
        private final double successRate;
        private final double avgResponseTime;
        private final long minResponseTime;
        private final long maxResponseTime;
        private final long p95ResponseTime;
        private final long p99ResponseTime;
        private final long requestsPerSecond;
        private final Map<String, Long> operationCounts;

        private PerformanceStats(Builder builder) {
            this.totalRequests = builder.totalRequests;
            this.successfulRequests = builder.successfulRequests;
            this.failedRequests = builder.failedRequests;
            this.successRate = builder.successRate;
            this.avgResponseTime = builder.avgResponseTime;
            this.minResponseTime = builder.minResponseTime;
            this.maxResponseTime = builder.maxResponseTime;
            this.p95ResponseTime = builder.p95ResponseTime;
            this.p99ResponseTime = builder.p99ResponseTime;
            this.requestsPerSecond = builder.requestsPerSecond;
            this.operationCounts = builder.operationCounts;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public String toString() {
            return String.format(
                "PerformanceStats{total=%d, success=%d, failed=%d, successRate=%.2f%%, " +
                "avgTime=%.2fms, p95=%dms, p99=%dms, rps=%d}",
                totalRequests, successfulRequests, failedRequests, successRate,
                avgResponseTime, p95ResponseTime, p99ResponseTime, requestsPerSecond
            );
        }

        public static class Builder {
            private long totalRequests;
            private long successfulRequests;
            private long failedRequests;
            private double successRate;
            private double avgResponseTime;
            private long minResponseTime;
            private long maxResponseTime;
            private long p95ResponseTime;
            private long p99ResponseTime;
            private long requestsPerSecond;
            private Map<String, Long> operationCounts;

            public Builder totalRequests(long totalRequests) { this.totalRequests = totalRequests; return this; }
            public Builder successfulRequests(long successfulRequests) { this.successfulRequests = successfulRequests; return this; }
            public Builder failedRequests(long failedRequests) { this.failedRequests = failedRequests; return this; }
            public Builder successRate(double successRate) { this.successRate = successRate; return this; }
            public Builder avgResponseTime(double avgResponseTime) { this.avgResponseTime = avgResponseTime; return this; }
            public Builder minResponseTime(long minResponseTime) { this.minResponseTime = minResponseTime; return this; }
            public Builder maxResponseTime(long maxResponseTime) { this.maxResponseTime = maxResponseTime; return this; }
            public Builder p95ResponseTime(long p95ResponseTime) { this.p95ResponseTime = p95ResponseTime; return this; }
            public Builder p99ResponseTime(long p99ResponseTime) { this.p99ResponseTime = p99ResponseTime; return this; }
            public Builder requestsPerSecond(long requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; return this; }
            public Builder operationCounts(Map<String, Long> operationCounts) { this.operationCounts = operationCounts; return this; }

            public PerformanceStats build() {
                return new PerformanceStats(this);
            }
        }
    }

    /**
     * 基准测试结果
     */
    @Getter
    public static class BenchmarkResult {
        private final int requestCount;
        private final long successCount;
        private final long durationMs;
        private final double throughputRps;
        private final double avgResponseTime;
        private final long p95ResponseTime;
        private final long p99ResponseTime;
        private final long gcCount;

        private BenchmarkResult(Builder builder) {
            this.requestCount = builder.requestCount;
            this.successCount = builder.successCount;
            this.durationMs = builder.durationMs;
            this.throughputRps = builder.throughputRps;
            this.avgResponseTime = builder.avgResponseTime;
            this.p95ResponseTime = builder.p95ResponseTime;
            this.p99ResponseTime = builder.p99ResponseTime;
            this.gcCount = builder.gcCount;
        }

        public static Builder builder() {
            return new Builder();
        }

        @Override
        public String toString() {
            return String.format(
                "BenchmarkResult{requests=%d, success=%d, duration=%dms, throughput=%.2f rps, " +
                "avgTime=%.2fms, p95=%dms, p99=%dms, gc=%d}",
                requestCount, successCount, durationMs, throughputRps,
                avgResponseTime, p95ResponseTime, p99ResponseTime, gcCount
            );
        }

        public static class Builder {
            private int requestCount;
            private long successCount;
            private long durationMs;
            private double throughputRps;
            private double avgResponseTime;
            private long p95ResponseTime;
            private long p99ResponseTime;
            private long gcCount;

            public Builder requestCount(int requestCount) { this.requestCount = requestCount; return this; }
            public Builder successCount(long successCount) { this.successCount = successCount; return this; }
            public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
            public Builder throughputRps(double throughputRps) { this.throughputRps = throughputRps; return this; }
            public Builder avgResponseTime(double avgResponseTime) { this.avgResponseTime = avgResponseTime; return this; }
            public Builder p95ResponseTime(long p95ResponseTime) { this.p95ResponseTime = p95ResponseTime; return this; }
            public Builder p99ResponseTime(long p99ResponseTime) { this.p99ResponseTime = p99ResponseTime; return this; }
            public Builder gcCount(long gcCount) { this.gcCount = gcCount; return this; }

            public BenchmarkResult build() {
                return new BenchmarkResult(this);
            }
        }
    }
}
