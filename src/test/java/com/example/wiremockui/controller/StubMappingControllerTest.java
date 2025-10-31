package com.example.wiremockui.controller;

import com.example.wiremockui.entity.StubMapping;
import com.example.wiremockui.service.StubMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * StubMappingController 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StubMappingController 测试")
class StubMappingControllerTest {

    @Mock
    private StubMappingService stubMappingService;

    @InjectMocks
    private StubMappingController controller;

    private StubMapping testStub;
    private List<StubMapping> stubList;

    @BeforeEach
    void setUp() {
        // 创建测试数据
        testStub = new StubMapping();
        testStub.setId(1L);
        testStub.setName("用户查询接口");
        testStub.setDescription("模拟用户查询接口");
        testStub.setMethod("GET");
        testStub.setUrl("/api/users");
        testStub.setEnabled(true);
        testStub.setResponseDefinition("{\"status\": \"success\"}");

        StubMapping stub2 = new StubMapping();
        stub2.setId(2L);
        stub2.setName("创建用户接口");
        stub2.setMethod("POST");
        stub2.setUrl("/api/users");
        stub2.setEnabled(true);
        stub2.setResponseDefinition("{\"status\": \"created\"}");

        stubList = Arrays.asList(testStub, stub2);
    }

    @Test
    @DisplayName("测试 createStub - 成功创建")
    void testCreateStub_Success() {
        // 准备
        when(stubMappingService.createStub(any(StubMapping.class))).thenReturn(testStub);

        // 执行
        ResponseEntity<StubMapping> response = controller.createStub(testStub);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(testStub, response.getBody());
    }

    @Test
    @DisplayName("测试 createStub - WireMock 未运行返回 409")
    void testCreateStub_WireMockNotRunning() {
        // 准备
        when(stubMappingService.createStub(any(StubMapping.class)))
                .thenThrow(new IllegalStateException("WireMock服务器未运行"));

        // 执行
        ResponseEntity<StubMapping> response = controller.createStub(testStub);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("测试 createStub - 参数无效返回 400")
    void testCreateStub_InvalidParameter() {
        // 准备
        when(stubMappingService.createStub(any(StubMapping.class)))
                .thenThrow(new IllegalArgumentException("参数无效"));

        // 执行
        ResponseEntity<StubMapping> response = controller.createStub(testStub);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 getAllStubs - 成功获取")
    void testGetAllStubs_Success() {
        // 准备
        when(stubMappingService.getAllStubs()).thenReturn(stubList);

        // 执行
        ResponseEntity<List<StubMapping>> response = controller.getAllStubs();

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    @DisplayName("测试 getAllStubs - 发生异常返回 400")
    void testGetAllStubs_Exception() {
        // 准备
        when(stubMappingService.getAllStubs()).thenThrow(new RuntimeException("数据库错误"));

        // 执行
        ResponseEntity<List<StubMapping>> response = controller.getAllStubs();

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 getAllStubs_Paged - 成功分页查询")
    void testGetAllStubs_Paged_Success() {
        // 准备
        Page<StubMapping> stubPage = new PageImpl<>(stubList);
        when(stubMappingService.getAllStubs(any())).thenReturn(stubPage);

        // 执行
        ResponseEntity<Page<StubMapping>> response = controller.getAllStubs(null);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().getContent().size());
    }

    @Test
    @DisplayName("测试 searchStubs - 成功搜索")
    void testSearchStubs_Success() {
        // 准备
        when(stubMappingService.searchStubs("用户")).thenReturn(Arrays.asList(testStub));

        // 执行
        ResponseEntity<List<StubMapping>> response = controller.searchStubs("用户");

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    @DisplayName("测试 searchStubs - 发生异常返回 400")
    void testSearchStubs_Exception() {
        // 准备
        when(stubMappingService.searchStubs("用户")).thenThrow(new RuntimeException("搜索失败"));

        // 执行
        ResponseEntity<List<StubMapping>> response = controller.searchStubs("用户");

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 getStubById - 找到 Stub")
    void testGetStubById_Found() {
        // 准备
        when(stubMappingService.getStubById(1L)).thenReturn(Optional.of(testStub));

        // 执行
        ResponseEntity<StubMapping> response = controller.getStubById(1L);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getId());
    }

    @Test
    @DisplayName("测试 getStubById - 未找到 Stub")
    void testGetStubById_NotFound() {
        // 准备
        when(stubMappingService.getStubById(99L)).thenReturn(Optional.empty());

        // 执行
        ResponseEntity<StubMapping> response = controller.getStubById(99L);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 updateStub - 成功更新")
    void testUpdateStub_Success() {
        // 准备
        StubMapping updatedStub = new StubMapping();
        updatedStub.setName("更新后的接口");
        updatedStub.setMethod("PUT");
        updatedStub.setUrl("/api/users");
        updatedStub.setEnabled(true);
        updatedStub.setResponseDefinition("{\"status\": \"updated\"}");

        when(stubMappingService.updateStub(eq(1L), any(StubMapping.class))).thenReturn(updatedStub);

        // 执行
        ResponseEntity<StubMapping> response = controller.updateStub(1L, updatedStub);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("更新后的接口", response.getBody().getName());
    }

    @Test
    @DisplayName("测试 updateStub - Stub 不存在返回 404")
    void testUpdateStub_NotFound() {
        // 准备
        when(stubMappingService.updateStub(eq(99L), any(StubMapping.class)))
                .thenThrow(new IllegalArgumentException("Stub 不存在"));

        // 执行
        ResponseEntity<StubMapping> response = controller.updateStub(99L, testStub);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 updateStub - 发生异常返回 400")
    void testUpdateStub_Exception() {
        // 准备
        when(stubMappingService.updateStub(eq(1L), any(StubMapping.class)))
                .thenThrow(new RuntimeException("更新失败"));

        // 执行
        ResponseEntity<StubMapping> response = controller.updateStub(1L, testStub);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 deleteStub - 成功删除")
    void testDeleteStub_Success() {
        // 准备
        // deleteStub 方法不返回异常表示成功

        // 执行
        ResponseEntity<Void> response = controller.deleteStub(1L);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 deleteStub - Stub 不存在返回 404")
    void testDeleteStub_NotFound() {
        // 准备
        // 使用 mock 验证异常抛出
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Stub 不存在"))
                .when(stubMappingService).deleteStub(99L);

        // 执行
        ResponseEntity<Void> response = controller.deleteStub(99L);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 deleteStub - 发生异常返回 400")
    void testDeleteStub_Exception() {
        // 准备
        org.mockito.Mockito.doThrow(new RuntimeException("删除失败"))
                .when(stubMappingService).deleteStub(1L);

        // 执行
        ResponseEntity<Void> response = controller.deleteStub(1L);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 toggleStubEnabled - 成功切换状态")
    void testToggleStubEnabled_Success() {
        // 准备
        testStub.setEnabled(false);
        when(stubMappingService.toggleStubEnabled(1L)).thenReturn(testStub);

        // 执行
        ResponseEntity<StubMapping> response = controller.toggleStubEnabled(1L);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(testStub, response.getBody());
    }

    @Test
    @DisplayName("测试 toggleStubEnabled - Stub 不存在返回 404")
    void testToggleStubEnabled_NotFound() {
        // 准备
        when(stubMappingService.toggleStubEnabled(99L))
                .thenThrow(new IllegalArgumentException("Stub 不存在"));

        // 执行
        ResponseEntity<StubMapping> response = controller.toggleStubEnabled(99L);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 toggleStubEnabled - 发生异常返回 400")
    void testToggleStubEnabled_Exception() {
        // 准备
        when(stubMappingService.toggleStubEnabled(1L))
                .thenThrow(new RuntimeException("切换失败"));

        // 执行
        ResponseEntity<StubMapping> response = controller.toggleStubEnabled(1L);

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 reloadAllStubs - 成功重新加载")
    void testReloadAllStubs_Success() {
        // 准备
        // reloadAllStubs 方法不返回异常表示成功

        // 执行
        ResponseEntity<Void> response = controller.reloadAllStubs();

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 reloadAllStubs - 发生异常返回 400")
    void testReloadAllStubs_Exception() {
        // 准备
        org.mockito.Mockito.doThrow(new RuntimeException("重新加载失败"))
                .when(stubMappingService).reloadAllStubs();

        // 执行
        ResponseEntity<Void> response = controller.reloadAllStubs();

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("测试 getStatistics - 成功获取统计信息")
    void testGetStatistics_Success() {
        // 准备
        StubMappingService.StubStatistics stats = new StubMappingService.StubStatistics(10, 7, 3);
        when(stubMappingService.getStatistics()).thenReturn(stats);

        // 执行
        ResponseEntity<StubMappingService.StubStatistics> response = controller.getStatistics();

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10, response.getBody().getTotalStubs());
        assertEquals(7, response.getBody().getEnabledStubs());
        assertEquals(3, response.getBody().getDisabledStubs());
    }

    @Test
    @DisplayName("测试 getStatistics - 发生异常返回 400")
    void testGetStatistics_Exception() {
        // 准备
        when(stubMappingService.getStatistics()).thenThrow(new RuntimeException("获取统计信息失败"));

        // 执行
        ResponseEntity<StubMappingService.StubStatistics> response = controller.getStatistics();

        // 验证
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
