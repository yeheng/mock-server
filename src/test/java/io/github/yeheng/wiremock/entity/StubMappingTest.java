package io.github.yeheng.wiremock.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StubMapping 实体单元测试
 */
@DisplayName("StubMapping 实体测试")
class StubMappingTest {

    private StubMapping stubMapping;

    @BeforeEach
    void setUp() {
        stubMapping = new StubMapping();
        stubMapping.setId(1L);
        stubMapping.setName("用户查询接口");
        stubMapping.setDescription("模拟用户查询接口的响应");
        stubMapping.setMethod("GET");
        stubMapping.setUrl("/api/users");
        stubMapping.setEnabled(true);
        stubMapping.setPriority(0);
        stubMapping.setResponseDefinition("{\"status\": \"success\", \"data\": {\"id\": 1, \"name\": \"张三\"}}");
    }

    @Test
    @DisplayName("测试 getRequestPattern 方法 - 启用状态的 GET 请求")
    void testGetRequestPattern_EnabledGetRequest() {
        stubMapping.setEnabled(true);
        stubMapping.setMethod("GET");
        stubMapping.setUrl("/api/users");

        // 当启用时，应该返回非空的 MappingBuilder
        assertNotNull(stubMapping.getRequestPattern());
    }

    @Test
    @DisplayName("测试 getRequestPattern 方法 - 禁用状态")
    void testGetRequestPattern_DisabledStub() {
        stubMapping.setEnabled(false);

        // 当禁用时，应该返回 null
        assertNull(stubMapping.getRequestPattern());
    }

    @Test
    @DisplayName("测试 getRequestPattern 方法 - 不同 HTTP 方法")
    void testGetRequestPattern_DifferentHttpMethods() {
        String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH", "HEAD"};
        String url = "/api/test";

        for (String method : methods) {
            stubMapping.setMethod(method);
            stubMapping.setUrl(url);
            stubMapping.setEnabled(true);

            assertNotNull(stubMapping.getRequestPattern(),
                    String.format("方法 %s 应该返回非空的 MappingBuilder", method));
        }
    }

    @Test
    @DisplayName("测试 getRequestPattern 方法 - URL 匹配类型")
    void testGetRequestPattern_UrlMatchTypes() {
        stubMapping.setEnabled(true);
        stubMapping.setMethod("GET");
        stubMapping.setUrl("/api/users");

        // 测试 EQUALS 类型
        stubMapping.setUrlMatchType(StubMapping.UrlMatchType.EQUALS);
        assertNotNull(stubMapping.getRequestPattern());

        // 测试 CONTAINS 类型
        stubMapping.setUrlMatchType(StubMapping.UrlMatchType.CONTAINS);
        stubMapping.setUrl("/api.*");
        assertNotNull(stubMapping.getRequestPattern());

        // 测试 REGEX 类型
        stubMapping.setUrlMatchType(StubMapping.UrlMatchType.REGEX);
        stubMapping.setUrl("/api/\\d+");
        assertNotNull(stubMapping.getRequestPattern());

        // 测试 PATH_TEMPLATE 类型
        stubMapping.setUrlMatchType(StubMapping.UrlMatchType.PATH_TEMPLATE);
        stubMapping.setUrl("/api/users/{id}");
        assertNotNull(stubMapping.getRequestPattern());
    }

    @Test
    @DisplayName("测试 getResponseBody 方法 - 有效 JSON 响应")
    void testGetResponseBody_ValidJson() {
        String responseJson = "{\"status\": \"success\", \"data\": {\"id\": 1}}";
        stubMapping.setResponseDefinition(responseJson);

        String result = stubMapping.getResponseBody();
        assertNotNull(result);
        assertTrue(result.contains("status"));
        assertTrue(result.contains("success"));
    }

    @Test
    @DisplayName("测试 getResponseBody 方法 - 无效 JSON 返回原字符串")
    void testGetResponseBody_InvalidJson() {
        String invalidJson = "这不是有效的JSON";
        stubMapping.setResponseDefinition(invalidJson);

        String result = stubMapping.getResponseBody();
        assertEquals(invalidJson, result);
    }

    @Test
    @DisplayName("测试 getResponseBody 方法 - 空响应")
    void testGetResponseBody_EmptyResponse() {
        stubMapping.setResponseDefinition("");
        String result = stubMapping.getResponseBody();
        // 空字符串在异常处理中会被返回
        assertNotNull(result);
    }

    @Test
    @DisplayName("测试 toWireMockResponseDefinition 方法 - 有效响应定义")
    void testToWireMockResponseDefinition_Valid() {
        String responseJson = """
            {
                "status": 200,
                "body": "测试响应"
            }
            """;
        stubMapping.setResponseDefinition(responseJson);

        assertDoesNotThrow(() -> {
            var responseDef = stubMapping.toWireMockResponseDefinition();
            assertNotNull(responseDef);
        });
    }

    @Test
    @DisplayName("测试 toWireMockResponseDefinition 方法 - 无效 JSON 抛出异常")
    void testToWireMockResponseDefinition_InvalidJson() {
        String invalidJson = "无效的JSON";
        stubMapping.setResponseDefinition(invalidJson);

        assertThrows(RuntimeException.class, () -> {
            stubMapping.toWireMockResponseDefinition();
        });
    }

    @Test
    @DisplayName("测试 equals 方法 - 相等对象")
    void testEquals_SameId() {
        StubMapping stub1 = new StubMapping();
        stub1.setId(1L);

        StubMapping stub2 = new StubMapping();
        stub2.setId(1L);

        assertEquals(stub1, stub2);
    }

    @Test
    @DisplayName("测试 equals 方法 - 不同 ID")
    void testEquals_DifferentId() {
        StubMapping stub1 = new StubMapping();
        stub1.setId(1L);

        StubMapping stub2 = new StubMapping();
        stub2.setId(2L);

        assertNotEquals(stub1, stub2);
    }

    @Test
    @DisplayName("测试 equals 方法 - 空对象")
    void testEquals_Null() {
        assertNotEquals(stubMapping, null);
    }

    @Test
    @DisplayName("测试 equals 方法 - 自身比较")
    void testEquals_Self() {
        assertEquals(stubMapping, stubMapping);
    }

    @Test
    @DisplayName("测试 hashCode 方法 - 相同 ID 返回相同哈希码")
    void testHashCode_SameId() {
        StubMapping stub1 = new StubMapping();
        stub1.setId(1L);

        StubMapping stub2 = new StubMapping();
        stub2.setId(1L);

        assertEquals(stub1.hashCode(), stub2.hashCode());
    }

    @Test
    @DisplayName("测试 toString 方法")
    void testToString() {
        String toStringResult = stubMapping.toString();
        assertNotNull(toStringResult);
        // toString 可能包含字段值，但格式可能不是我们预期的
        assertTrue(toStringResult.contains("StubMapping") ||
                  toStringResult.contains("用户查询接口"));
    }

    @Test
    @DisplayName("测试 RequiredArgsConstructor 构造")
    void testRequiredArgsConstructor() {
        // StubMapping 使用 @RequiredArgsConstructor 注解
        // 但由于实体类中有 final 字段，需要通过 setter 设置
        StubMapping stub = new StubMapping();
        stub.setName("测试名称");
        assertNotNull(stub);
        assertEquals("测试名称", stub.getName());
    }

    @Test
    @DisplayName("测试字段设置和获取")
    void testFields() {
        assertEquals(1L, stubMapping.getId());
        assertEquals("用户查询接口", stubMapping.getName());
        assertEquals("模拟用户查询接口的响应", stubMapping.getDescription());
        assertEquals("GET", stubMapping.getMethod());
        assertEquals("/api/users", stubMapping.getUrl());
        assertTrue(stubMapping.getEnabled());
        assertEquals(0, stubMapping.getPriority());
        // createdAt 和 updatedAt 在单元测试中可能为 null，跳过这些断言
        assertNotNull(stubMapping.getResponseDefinition());
    }
}
