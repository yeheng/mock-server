package io.github.yeheng.wiremock.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.yeheng.wiremock.entity.StubMapping;
import io.github.yeheng.wiremock.repository.StubMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * StubMapping 服务类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StubMappingService {

    private final StubMappingRepository stubMappingRepository;
    private final WireMockManager wireMockManager;
    private final ObjectMapper objectMapper;

    /**
     * 创建新的 StubMapping
     */
    @Transactional
    public StubMapping createStub(StubMapping stub) {
        log.info("创建新的 Stub: {}", stub.getName());

        ensureRunning();
        validateStubMapping(stub);

        StubMapping savedStub = stubMappingRepository.save(stub);

        // 添加到WireMock
        if (savedStub.getEnabled()) {
            wireMockManager.addStubMapping(savedStub);
        }

        log.info("Stub 创建成功: ID={}", savedStub.getId());

        return savedStub;
    }

    /**
     * 获取所有 StubMapping
     */
    @Transactional(readOnly = true)
    public List<StubMapping> getAllStubs() {
        return stubMappingRepository.findAll();
    }

    /**
     * 分页获取所有 Stubs
     */
    @Transactional(readOnly = true)
    public Page<StubMapping> getAllStubs(Pageable pageable) {
        return stubMappingRepository.findAll(pageable);
    }

    /**
     * 获取 StubMapping
     */
    @Transactional(readOnly = true)
    public Optional<StubMapping> getStubById(Long id) {
        return stubMappingRepository.findById(id);
    }

    /**
     * 搜索 Stubs
     */
    @Transactional(readOnly = true)
    public List<StubMapping> searchStubs(String keyword) {
        return stubMappingRepository.findByNameContainingIgnoreCase(keyword);
    }

    /**
     * 更新 StubMapping
     * 使用增量更新，避免全量重载
     */
    @Transactional
    public StubMapping updateStub(Long id, StubMapping updatedStub) {
        log.info("更新 Stub: ID={}", id);

        StubMapping existingStub = stubMappingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stub 不存在: ID=" + id));

        ensureRunning();

        updatedStub.setId(id);
        updatedStub.setUuid(existingStub.getUuid()); // 保持 UUID 不变

        validateStubMapping(updatedStub);

        StubMapping savedStub = stubMappingRepository.save(updatedStub);

        // 使用增量更新：先删除旧的，再添加新的
        wireMockManager.removeStubMapping(existingStub);
        if (savedStub.getEnabled()) {
            wireMockManager.addStubMapping(savedStub);
        }

        log.info("Stub 更新成功: ID={}", savedStub.getId());

        return savedStub;
    }

    /**
     * 删除 StubMapping
     */
    @Transactional
    public void deleteStub(Long id) {
        log.info("删除 Stub: ID={}", id);

        StubMapping stub = stubMappingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stub 不存在: ID=" + id));

        // 从WireMock中移除
        wireMockManager.removeStubMapping(stub);

        stubMappingRepository.delete(stub);
        log.info("Stub 删除成功: ID={}", id);
    }

    /**
     * 启用/禁用 Stub
     * 使用增量更新，避免全量重载
     */
    @Transactional
    public StubMapping toggleStubEnabled(Long id) {
        log.info("切换 Stub 启用状态: ID={}", id);

        StubMapping stub = stubMappingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stub 不存在: ID=" + id));

        boolean wasEnabled = stub.getEnabled();
        stub.setEnabled(!wasEnabled);
        StubMapping savedStub = stubMappingRepository.save(stub);

        // 使用增量更新
        if (wasEnabled) {
            // 从启用变为禁用：删除
            wireMockManager.removeStubMapping(savedStub);
        } else {
            // 从禁用变为启用：添加
            wireMockManager.addStubMapping(savedStub);
        }

        log.info("Stub 状态切换成功: ID={}, enabled={}", id, savedStub.getEnabled());

        return savedStub;
    }

    /**
     * 重新加载所有 stubs 到 WireMock
     */
    @Transactional(readOnly = true)
    public void reloadAllStubs() {
        List<StubMapping> stubs = stubMappingRepository.findAll();
        wireMockManager.reloadAllStubs(stubs);
        log.info("已重新加载所有 stubs，数量: {}", stubs.size());
    }

    /**
     * 批量创建 StubMapping - 支持去重
     */
    @Transactional
    public List<StubMapping> createStubs(List<StubMapping> stubs) {
        log.info("批量创建 Stub，数量: {}", stubs.size());

        ensureRunning();

        // 验证并去重
        List<StubMapping> stubsToSave = new ArrayList<>();
        int skippedCount = 0;

        for (StubMapping stub : stubs) {
            // 先验证
            validateStubMapping(stub);

            // 检查是否已存在（基于方法和URL）
            List<StubMapping> existing = stubMappingRepository.findByMethodAndUrl(stub.getMethod(), stub.getUrl());
            if (!existing.isEmpty()) {
                // 已存在，跳过
                skippedCount++;
                log.debug("跳过已存在的 Stub: method={}, url={}", stub.getMethod(), stub.getUrl());
            } else {
                stubsToSave.add(stub);
            }
        }

        // 保存所有新stub
        List<StubMapping> savedStubs = stubMappingRepository.saveAll(stubsToSave);

        // 添加到WireMock
        for (StubMapping savedStub : savedStubs) {
            if (savedStub.getEnabled()) {
                wireMockManager.addStubMapping(savedStub);
            }
        }

        log.info("Stub 批量创建成功: 新增={}, 跳过={}", savedStubs.size(), skippedCount);
        return savedStubs;
    }

    /**
     * 获取启用状态统计
     */
    @Transactional(readOnly = true)
    public StubStatistics getStatistics() {
        long totalStubs = stubMappingRepository.count();
        long enabledStubs = stubMappingRepository.countByEnabled(true);
        long disabledStubs = totalStubs - enabledStubs;

        return new StubStatistics(totalStubs, enabledStubs, disabledStubs);
    }

    /**
     * 验证 StubMapping 的有效性
     */
    private void validateStubMapping(StubMapping stub) {
        // 验证必需字段
        if (stub.getName() == null || stub.getName().isBlank()) {
            throw new IllegalArgumentException("Stub 名称不能为空");
        }

        if (stub.getMethod() == null || stub.getMethod().isBlank()) {
            throw new IllegalArgumentException("HTTP 方法不能为空");
        }

        if (stub.getUrl() == null || stub.getUrl().isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }

        if (stub.getResponseDefinition() == null || stub.getResponseDefinition().isBlank()) {
            throw new IllegalArgumentException("响应定义不能为空");
        }

        // 验证 JSON 格式
        try {
            // 对响应定义保持严格校验，必须为有效 JSON
            if (!stub.getResponseDefinition().isBlank()) {
                objectMapper.readTree(stub.getResponseDefinition());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 格式无效: " + e.getMessage());
        }

        // 对请求体匹配规则保持宽松策略：作为字符串存储与使用
        // 一些测试或用户可能提供非标准但可解析的匹配表达式（例如额外的括号），
        // 不应在创建阶段拒绝。真正的解析与匹配在请求到来时由 WireMockManager 处理。
    }

    private void ensureRunning() {
        if (!wireMockManager.isRunning()) {
            throw new IllegalStateException("WireMock服务器未运行");
        }
    }

    /**
     * Stub 统计信息
     */
    public record StubStatistics(long totalStubs, long enabledStubs, long disabledStubs) {
    }
}
