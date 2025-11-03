package io.github.yeheng.wiremock.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import io.github.yeheng.wiremock.entity.StubMapping;

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
        // 清理之前的数据 - 确保在事务中删除
        repository.deleteAll();
        repository.flush(); // 强制执行删除

        // 创建测试数据
        stub1 = createStub("用户查询接口", "获取用户信息", "GET", "/api/users", true, 0, "uuid-001");
        stub2 = createStub("创建用户接口", "创建新用户", "POST", "/api/users", true, 1, "uuid-002");
        stub3 = createStub("禁用接口", "已禁用的接口", "PUT", "/api/disabled", false, 2, "uuid-003");
        stub4 = createStub("产品查询接口", "查询产品信息", "GET", "/api/products", true, 0, "uuid-004");

        // 保存测试数据
        repository.saveAll(Arrays.asList(stub1, stub2, stub3, stub4));
        repository.flush(); // 强制执行保存
    }

    // 辅助方法：创建 StubMapping 并设置时间戳
    private StubMapping createStub(String name, String description, String method, String url,
                                  boolean enabled, int priority, String uuid) {
        StubMapping stub = new StubMapping();
        stub.setName(name);
        stub.setDescription(description);
        stub.setMethod(method);
        stub.setUrl(url);
        stub.setEnabled(enabled);
        stub.setPriority(priority);
        stub.setResponseDefinition("{\"status\": \"success\", \"data\": {}}");
        stub.setUuid(uuid);

        // 设置时间戳
        var now = java.time.LocalDateTime.now();
        stub.setCreatedAt(now);
        stub.setUpdatedAt(now);

        return stub;
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
        StubMapping saved = repository.save(stub1);
        Long savedId = saved.getId();
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
        assertFalse(results.isEmpty());
        boolean found = results.stream().anyMatch(s -> s.getName().contains("用户"));
        assertTrue(found, "应该找到包含'用户'的记录");
    }

    @Test
    @DisplayName("测试 findByNameContainingIgnoreCase - 不区分大小写")
    void testFindByNameContainingIgnoreCase_CaseInsensitive() {
        // 执行 - 使用小写搜索
        List<StubMapping> results = repository.findByNameContainingIgnoreCase("接口");

        // 验证
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    @DisplayName("测试 findByEnabled - 查询启用的 Stubs")
    void testFindByEnabled_True() {
        // 执行
        List<StubMapping> results = repository.findByEnabled(true);

        // 验证
        assertNotNull(results);
        assertFalse(results.isEmpty());
        results.forEach(stub -> assertTrue(stub.getEnabled()));
    }

    @Test
    @DisplayName("测试 findByEnabled - 查询禁用的 Stubs")
    void testFindByEnabled_False() {
        // 执行
        List<StubMapping> results = repository.findByEnabled(false);

        // 验证
        assertNotNull(results);
        results.forEach(stub -> assertFalse(stub.getEnabled()));
    }

    @Test
    @DisplayName("测试 findByMethod - 根据 HTTP 方法查询")
    void testFindByMethod() {
        // 执行
        List<StubMapping> results = repository.findByMethod("GET");

        // 验证
        assertNotNull(results);
        results.forEach(stub -> assertEquals("GET", stub.getMethod()));
    }

    @Test
    @DisplayName("测试 findByUuid - 根据 UUID 查找")
    void testFindByUuid() {
        // 执行
        StubMapping result = repository.findByUuid("uuid-001");

        // 验证
        assertNotNull(result);
        assertEquals("uuid-001", result.getUuid());
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

        // 验证 - 只有1个禁用的stub (stub3)
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("测试保存和删除 Stub")
    void testSaveAndDelete() {
        // 创建新 Stub
        StubMapping newStub = createStub("新建接口", "新建接口描述", "DELETE", "/api/new", true, 0, "uuid-new");

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
        // 准备 - 使用新的 stub 实例
        StubMapping originalStub = createStub("原始名称", "原始描述", "GET", "/api/original", true, 0, "uuid-original");
        StubMapping saved = repository.save(originalStub);

        // 更新
        saved.setName("更新后的名称");
        saved.setDescription("更新后的描述");
        StubMapping updated = repository.save(saved);

        // 验证
        assertNotNull(updated.getId());
        assertEquals("更新后的名称", updated.getName());
        assertEquals("更新后的描述", updated.getDescription());
        // 注意：originalStub 和 saved 引用同一个对象，所以会被修改
        // 我们从数据库重新读取来验证
        StubMapping reloaded = repository.findById(updated.getId()).get();
        assertEquals("更新后的名称", reloaded.getName());
        assertEquals("更新后的描述", reloaded.getDescription());
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
}
