package io.github.yeheng.wiremock.service;

import io.github.yeheng.wiremock.entity.StubMapping;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stub 匹配预索引系统
 * 简化设计：只保留方法索引和精确 URL 索引
 * PathTrie 被删除（WireMock 内置匹配逻辑足够高效）
 */
@Slf4j
public class StubIndex {

    // 方法索引：HTTP方法 -> Stub 列表
    private final Map<String, List<StubMapping>> methodIndex = new ConcurrentHashMap<>();

    // 完整 URL 索引：精确匹配时使用
    private final Map<String, StubMapping> exactUrlIndex = new ConcurrentHashMap<>();

    /**
     * 添加 Stub 到索引
     */
    public synchronized void addStub(StubMapping stub) {
        if (stub == null || !Boolean.TRUE.equals(stub.getEnabled())) {
            return;
        }

        // 1. 添加到方法索引
        String method = Optional.ofNullable(stub.getMethod()).orElse("ANY").toUpperCase();
        methodIndex.computeIfAbsent(method, k -> new ArrayList<>()).add(stub);

        // 2. 添加到精确 URL 索引（用于 equals 匹配）
        String url = stub.getUrl();
        if (url != null && !url.isEmpty()) {
            String cleanPath = url.split("\\?")[0];
            if (stub.getUrlMatchType() == StubMapping.UrlMatchType.EQUALS) {
                exactUrlIndex.put(cleanPath, stub);
            }
        }

        log.debug("Stub 已添加到索引: {} {}", method, url);
    }

    /**
     * 从索引中移除 Stub
     */
    public synchronized void removeStub(StubMapping stub) {
        if (stub == null) {
            return;
        }

        String method = Optional.ofNullable(stub.getMethod()).orElse("ANY").toUpperCase();
        String url = stub.getUrl();
        String cleanPath = url != null ? url.split("\\?")[0] : null;

        // 从方法索引中移除
        List<StubMapping> methodStubs = methodIndex.get(method);
        if (methodStubs != null) {
            methodStubs.remove(stub);
            if (methodStubs.isEmpty()) {
                methodIndex.remove(method);
            }
        }

        // 从精确 URL 索引中移除
        if (cleanPath != null && stub.getUrlMatchType() == StubMapping.UrlMatchType.EQUALS) {
            exactUrlIndex.remove(cleanPath);
        }

        log.debug("Stub 已从索引中移除: {} {}", method, url);
    }

    /**
     * 清空所有索引
     */
    public synchronized void clear() {
        methodIndex.clear();
        exactUrlIndex.clear();
    }

    /**
     * 根据方法获取候选 Stub 列表
     */
    public List<StubMapping> getCandidatesByMethod(String method) {
        // 首先尝试精确匹配
        List<StubMapping> candidates = methodIndex.get(method.toUpperCase());
        if (candidates != null && !candidates.isEmpty()) {
            return new ArrayList<>(candidates);
        }

        // 如果没有精确匹配，尝试 ANY 方法
        candidates = methodIndex.get("ANY");
        return candidates != null ? new ArrayList<>(candidates) : Collections.emptyList();
    }

    /**
     * 根据精确 URL 获取 Stub
     */
    public StubMapping getStubByExactUrl(String url) {
        String cleanPath = url.split("\\?")[0];
        return exactUrlIndex.get(cleanPath);
    }

    /**
     * 获取索引统计信息
     */
    public IndexStats getStats() {
        return new IndexStats(
            methodIndex.size(),
            0,  // 删除 PathTrie，固定为 0
            exactUrlIndex.size()
        );
    }

    /**
     * 索引统计信息
     */
    public static class IndexStats {
        private final int methodCount;
        private final int pathTrieSize;
        private final int exactUrlCount;

        public IndexStats(int methodCount, int pathTrieSize, int exactUrlCount) {
            this.methodCount = methodCount;
            this.pathTrieSize = pathTrieSize;
            this.exactUrlCount = exactUrlCount;
        }

        public int getMethodCount() {
            return methodCount;
        }

        public int getPathTrieSize() {
            return pathTrieSize;
        }

        public int getExactUrlCount() {
            return exactUrlCount;
        }

        @Override
        public String toString() {
            return String.format("IndexStats{methods=%d, pathTrieNodes=%d, exactUrls=%d}",
                methodCount, pathTrieSize, exactUrlCount);
        }
    }
}
