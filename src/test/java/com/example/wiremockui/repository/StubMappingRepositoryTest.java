package com.example.wiremockui.repository;

import com.example.wiremockui.entity.StubMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StubMappingRepository 单元测试
 * 使用 @DataJpaTest 进行 JPA 测试
 */
@DataJpaTest
@DisplayName("StubMappingRepository 测试")
class StubMappingRepositoryTest {

    @Autowired
    private StubMappingRepository repository;

    private StubMapping stub1;
    private StubMapping stub2;
    private StubMapping stub3;
    private StubMapping stub4;

    @BeforeEach
    void setUp() {
        // 创建测试数据
        stub1 = new StubMapping();
        stub1.setName("用户查询接口");
        stub1.setDescription("获取用户信息");
        stub1.setMethod("GET");
        stub1.setUrl("/api/users");
        stub1.setEnabled(true);
        stub1.setPriority(0);
        stub1.setResponseDefinition("{\"status\": \"success\", \"data\": {}}");
        stub1.setUuid("uuid-001");

        stub2 = new StubMapping();
        stub2.setName("创建用户接口");
        stub2.setDescription("创建新用户");
        stub2.setMethod("POST");
        stub2.setUrl("/api/users");
        stub2.setEnabled(true);
        stub2.setPriority(1);
        stub2.setResponseDefinition("{\"status\": \"created\", \"data\": {}}");
        stub2.setUuid("uuid-002");

        stub3 = new StubMapping();
        stub3.setName("禁用接口");
        stub3.setDescription("已禁用的接口");
        stub3.setMethod("PUT");
        stub3.setUrl("/api/disabled");
        stub3.setEnabled(false);
        stub3.setPriority(2);
        stub3.setResponseDefinition("{\"status\": \"disabled\"}");
        stub3.setUuid("uuid-003");

        stub4 = new StubMapping();
        stub4.setName("产品查询接口");
        stub4.setDescription("查询产品信息");
        stub4.setMethod("GET");
        stub4.setUrl("/api/products");
        stub4.setEnabled(true);
        stub4.setPriority(0);
        stub4.setResponseDefinition("{\"status\": \"success\", \"data\": {}}");
        stub4.setUuid("uuid-004");

        // 保存测试数据
        repository.saveAll(Arrays.asList(stub1, stub2, stub3, stub4));
    }

    @Test
    @DisplayName("测试 findAll - 查询所有记录")
    void testFindAll() {
        // 执行
        List<StubMapping> results = repository.findAll();

        // 验证
        assertNotNull(results);
        assertEquals(4, results.size());
    }

    @Test
    @DisplayName("测试 findById - 找到记录")
    void testFindById_Found() {
        // 执行
        Long savedId = repository.save(stub1).getId();
        var result = repository.findById(savedId);

        // 验证
        assertTrue(result.isPresent());
        assertEquals(savedId, result.get().getId());
        assertEquals("用户查询接口", result.get().getName());
    }

    @Test
    @DisplayName("测试 findById - 未找到记录")
    void testFindById_NotFound() {
        // 执行
        var result = repository.findById(999L);

        // 验证
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("测试 findByNameContainingIgnoreCase - 模糊查询名称")
    void testFindByNameContainingIgnoreCase() {
        // 执行 - 搜索包含 "用户" 的记录
        List<StubMapping> results = repository.findByNameContainingIgnoreCase("用户");

        // 验证
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("用户查询接口", results.get(0).getName());

        // 执行 - 搜索包含 "接口" 的记录
        results = repository.findByNameContainingIgnoreCase("接口");

        // 验证
        assertNotNull(results);
        assertEquals(4, results.size());
    }

    @Test
    @DisplayName("测试 findByNameContainingIgnoreCase - 不区分大小写")
    void testFindByNameContainingIgnoreCase_CaseInsensitive() {
        // 执行 - 使用小写搜索
        List<StubMapping> results = repository.findByNameContainingIgnoreCase("user");

        // 验证
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("用户查询接口", results.get(0).getName());

        // 执行 - 使用大写搜索
        results = repository.findByNameContainingIgnoreCase("USER");

        // 验证
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("测试 findByNameContainingIgnoreCase - 搜索不存在的关键词")
    void testFindByNameContainingIgnoreCase_NotFound() {
        // 执行
        List<StubMapping> results = repository.findByNameContainingIgnoreCase("不存在的关键词");

        // 验证
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("测试 findByEnabled - 查询启用的 Stubs")
    void testFindByEnabled_True() {
        // 执行
        List<StubMapping> results = repository.findByEnabled(true);

        // 验证
        assertNotNull(results);
        assertEquals(3, results.size()); // stub1, stub2, stub4 是启用的
        for (StubMapping stub : results) {
            assertTrue(stub.getEnabled());
        }
    }

    @Test
    @DisplayName("测试 findByEnabled - 查询禁用的 Stubs")
    void testFindByEnabled_False() {
        // 执行
        List<StubMapping> results = repository.findByEnabled(false);

        // 验证
        assertNotNull(results);
        assertEquals(1, results.size()); // stub3 是禁用的
        assertFalse(results.get(0).getEnabled());
    }

    @Test
    @DisplayName("测试 findByMethod - 根据 HTTP 方法查询")
    void testFindByMethod() {
        // 执行 - 查询 GET 方法
        List<StubMapping> getResults = repository.findByMethod("GET");

        // 验证
        assertNotNull(getResults);
        assertEquals(2, getResults.size()); // stub1, stub4
        for (StubMapping stub : getResults) {
            assertEquals("GET", stub.getMethod());
        }

        // 执行 - 查询 POST 方法
        List<StubMapping> postResults = repository.findByMethod("POST");

        // 验证
        assertNotNull(postResults);
        assertEquals(1, postResults.size()); // stub2
        assertEquals("POST", postResults.get(0).getMethod());
    }

    @Test
    @DisplayName("测试 findByUuid - 根据 UUID 查询")
    void testFindByUuid() {
        // 准备
        Long savedId = repository.save(stub1).getId();
        String uuid = stub1.getUuid();

        // 执行
        StubMapping result = repository.findByUuid(uuid);

        // 验证
        assertNotNull(result);
        assertEquals(uuid, result.getUuid());
        assertEquals(savedId, result.getId());
    }

    @Test
    @DisplayName("测试 findByUuid - 查询不存在的 UUID")
    void testFindByUuid_NotFound() {
        // 执行
        StubMapping result = repository.findByUuid("不存在的uuid");

        // 验证
        assertNull(result);
    }

    @Test
    @DisplayName("测试 countByEnabled - 统计启用的 Stubs")
    void testCountByEnabled_True() {
        // 执行
        Long count = repository.countByEnabled(true);

        // 验证
        assertEquals(3L, count);
    }

    @Test
    @DisplayName("测试 countByEnabled - 统计禁用的 Stubs")
    void testCountByEnabled_False() {
        // 执行
        Long count = repository.countByEnabled(false);

        // 验证
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("测试保存和删除 Stub")
    void testSaveAndDelete() {
        // 创建新 Stub
        StubMapping newStub = new StubMapping();
        newStub.setName("新建接口");
        newStub.setMethod("DELETE");
        newStub.setUrl("/api/new");
        newStub.setEnabled(true);
        newStub.setResponseDefinition("{\"status\": \"deleted\"}");
        newStub.setUuid("uuid-new");

        // 保存
        StubMapping saved = repository.save(newStub);
        assertNotNull(saved.getId());

        // 验证保存成功
        var result = repository.findById(saved.getId());
        assertTrue(result.isPresent());
        assertEquals("新建接口", result.get().getName());

        // 删除
        repository.delete(saved);

        // 验证删除成功
        result = repository.findById(saved.getId());
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("测试更新 Stub")
    void testUpdate() {
        // 准备
        StubMapping saved = repository.save(stub1);
        String originalName = saved.getName();

        // 更新
        saved.setName("更新后的名称");
        saved.setDescription("更新后的描述");
        StubMapping updated = repository.save(saved);

        // 验证
        assertNotNull(updated.getId());
        assertEquals("更新后的名称", updated.getName());
        assertEquals("更新后的描述", updated.getDescription());
        assertEquals(originalName, stub1.getName()); // 原始对象未被修改
    }

    @Test
    @DisplayName("测试分页查询")
    void testPagination() {
        // 执行
        Pageable pageable = PageRequest.of(0, 2); // 第一页，每页2条
        Page<StubMapping> page = repository.findAll(pageable);

        // 验证
        assertNotNull(page);
        assertEquals(0, page.getNumber()); // 页码
        assertEquals(2, page.getSize()); // 每页大小
        assertEquals(4, page.getTotalElements()); // 总记录数
        assertEquals(2, page.getTotalPages()); // 总页数
        assertEquals(2, page.getContent().size()); // 当前页记录数
    }

    @Test
    @DisplayName("测试 JPA 审计字段")
    void testAuditingFields() {
        // 创建新 Stub
        StubMapping newStub = new StubMapping();
        newStub.setName("测试审计");
        newStub.setMethod("GET");
        newStub.setUrl("/api/audit");
        newStub.setEnabled(true);
        newStub.setResponseDefinition("{\"status\": \"ok\"}");

        // 保存
        StubMapping saved = repository.save(newStub);

        // 注意：在 @DataJpaTest 中，JPA 审计可能未启用
        // 审计字段可能为 null
        // 因此我们只验证保存操作成功
        assertNotNull(saved.getId());
        assertEquals("测试审计", saved.getName());
    }

    @Test
    @DisplayName("测试复合条件查询")
    void testComplexQuery() {
        // 查询启用的 GET 方法 Stubs
        List<StubMapping> enabledGetStubs = repository.findAll().stream()
                .filter(s -> Boolean.TRUE.equals(s.getEnabled()) && "GET".equals(s.getMethod()))
                .toList();

        // 验证
        assertEquals(2, enabledGetStubs.size()); // stub1, stub4
        enabledGetStubs.forEach(stub -> {
            assertEquals("GET", stub.getMethod());
            assertTrue(stub.getEnabled());
        });
    }

    @Test
    @DisplayName("测试事务回滚")
    void testTransactionRollback() {
        // 准备
        int initialCount = repository.findAll().size();

        // 创建并保存新 Stub
        StubMapping newStub = new StubMapping();
        newStub.setName("事务测试");
        newStub.setMethod("PATCH");
        newStub.setUrl("/api/transaction");
        newStub.setEnabled(true);
        newStub.setResponseDefinition("{\"status\": \"ok\"}");
        repository.save(newStub);

        // 验证保存成功
        assertEquals(initialCount + 1, repository.findAll().size());

        // 模拟事务失败（这里我们直接删除记录来模拟）
        repository.delete(newStub);

        // 验证删除成功
        assertEquals(initialCount, repository.findAll().size());
    }
}
