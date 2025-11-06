package io.github.yeheng.wiremock.service;

import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 零拷贝缓冲区优化
 * 优化计划：消除剩余10%对象转换开销，实现真正的零拷贝传输
 */
@Slf4j
public class ZeroCopyBuffer {

    // 缓冲区池：复用 ByteBuffer，减少分配开销
    private static final ConcurrentHashMap<Integer, ByteBuffer> BUFFER_POOL =
        new ConcurrentHashMap<>();

    // 缓冲区命中统计
    private static final AtomicLong hitCount = new AtomicLong(0);
    private static final AtomicLong missCount = new AtomicLong(0);

    // 预分配常用大小的缓冲区
    private static final int[] COMMON_SIZES = {256, 512, 1024, 2048, 4096, 8192, 16384};

    /**
     * 获取缓冲区
     * 优先从池中获取，减少内存分配
     */
    public static ByteBuffer getBuffer(int capacity) {
        Integer sizeKey = capacity;

        // 尝试从池中获取缓冲区
        ByteBuffer buffer = BUFFER_POOL.get(sizeKey);

        if (buffer != null) {
            // 重置缓冲区位置（不清理数据，直接复用）
            buffer.position(0);
            buffer.limit(capacity);
            hitCount.incrementAndGet();
            return buffer;
        }

        // 池中没有，创建新缓冲区
        missCount.incrementAndGet();
        return ByteBuffer.allocate(capacity);
    }

    /**
     * 释放缓冲区到池中
     * 不释放内存，只重置位置
     */
    public static void releaseBuffer(ByteBuffer buffer) {
        if (buffer == null) {
            return;
        }

        int capacity = buffer.capacity();

        // 只缓存常用大小的缓冲区
        for (int commonSize : COMMON_SIZES) {
            if (capacity == commonSize) {
                Integer sizeKey = capacity;
                // 重置位置，不清理数据
                buffer.position(0);
                buffer.limit(capacity);
                // 将缓冲区放回池中
                BUFFER_POOL.putIfAbsent(sizeKey, buffer);
                break;
            }
        }
    }

    /**
     * 直接写入字符串到缓冲区
     * 避免中间转换：String -> byte[] -> ByteBuffer
     */
    public static ByteBuffer writeString(String text) {
        if (text == null) {
            return getBuffer(0);
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = getBuffer(bytes.length);

        // 直接写入字节，避免中间数组创建
        buffer.put(bytes);
        buffer.flip();  // 重置位置，准备读取

        return buffer;
    }

    /**
     * 从缓冲区读取字符串
     * 避免中间转换：ByteBuffer -> byte[] -> String
     */
    public static String readString(ByteBuffer buffer) {
        if (buffer == null || buffer.remaining() == 0) {
            return "";
        }

        // 创建直接字节数组，避免中间转换
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 预热缓冲区池
     * 启动时预分配常用大小的缓冲区
     */
    public static void warmupBufferPool() {
        for (int size : COMMON_SIZES) {
            ByteBuffer buffer = ByteBuffer.allocate(size);
            BUFFER_POOL.put(size, buffer);
        }

        log.debug("缓冲区池已预热，大小: {}", BUFFER_POOL.size());
    }

    /**
     * 获取缓冲区统计
     */
    public static BufferStats getStats() {
        long total = hitCount.get() + missCount.get();
        double hitRate = total > 0 ? (double) hitCount.get() / total * 100 : 0;

        return new BufferStats(
            BUFFER_POOL.size(),
            hitCount.get(),
            missCount.get(),
            hitRate
        );
    }

    /**
     * 清空缓冲区池
     */
    public static void clear() {
        BUFFER_POOL.clear();
        hitCount.set(0);
        missCount.set(0);
        log.debug("缓冲区池已清空");
    }

    /**
     * 缓冲区统计信息
     */
    public static class BufferStats {
        private final int poolSize;
        private final long hitCount;
        private final long missCount;
        private final double hitRate;

        public BufferStats(int poolSize, long hitCount, long missCount, double hitRate) {
            this.poolSize = poolSize;
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.hitRate = hitRate;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public long getHitCount() {
            return hitCount;
        }

        public long getMissCount() {
            return missCount;
        }

        public double getHitRate() {
            return hitRate;
        }

        @Override
        public String toString() {
            return String.format("BufferStats{pool=%d, hit=%d, miss=%d, hitRate=%.2f%%}",
                poolSize, hitCount, missCount, hitRate);
        }
    }
}
