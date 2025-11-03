package io.github.yeheng.wiremock.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.yeheng.wiremock.entity.StubMapping;
import io.github.yeheng.wiremock.entity.StubMapping.UrlMatchType;

/**
 * WireMockManager.toWireMockMapping() 方法的边界和错误处理测试
 *
 * 测试目标：验证所有模式解析逻辑的健壮性，包括：
 * 1. 无效JSON输入处理
 * 2. JSON转义字符处理
 * 3. 所有匹配类型覆盖（equalTo, contains, matches, equalToJson, matchesJsonPath）
 * 4. 所有URL匹配类型（EQUALS, CONTAINS, REGEX, PATH_TEMPLATE）
 */
@DisplayName("WireMock 映射转换器测试 - toWireMockMapping() 边界场景")
class WireMockMappingConverterTest {

    private WireMockManager wireMockManager;

    @BeforeEach
    void setUp() throws Exception {
        wireMockManager = new WireMockManager();

        // 手动初始化 WireMockManager（模拟 @PostConstruct）
        var isRunningField = WireMockManager.class.getDeclaredField("isRunning");
        isRunningField.setAccessible(true);
        isRunningField.set(wireMockManager, true);

        var portField = WireMockManager.class.getDeclaredField("port");
        portField.setAccessible(true);
        portField.set(wireMockManager, 8080);
    }

    // ==================== 第一部分：无效JSON测试 ====================

    @Test
    @DisplayName("requestBodyPattern 无效JSON - 应该使用 containing 兜底")
    void testInvalidJson_RequestBodyPattern() {
        // 准备：创建一个包含无效JSON的stub
        StubMapping stub = createBaseStub();
        stub.setRequestBodyPattern("这不是一个有效的JSON"); // 无效JSON

        // 执行：添加stub（内部会调用 toWireMockMapping）
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));

        // 验证：stub应该被成功添加（使用兜底的 containing 匹配）
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestHeadersPattern 无效JSON - 应该静默跳过")
    void testInvalidJson_RequestHeadersPattern() {
        // 准备：创建一个包含无效JSON的stub
        StubMapping stub = createBaseStub();
        stub.setRequestHeadersPattern("这不是一个有效的JSON"); // 无效JSON

        // 执行：添加stub
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));

        // 验证：stub应该被成功添加（头部匹配被跳过）
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("queryParametersPattern 无效JSON - 应该静默跳过")
    void testInvalidJson_QueryParametersPattern() {
        // 准备：创建一个包含无效JSON的stub
        StubMapping stub = createBaseStub();
        stub.setQueryParametersPattern("这不是一个有效的JSON"); // 无效JSON

        // 执行：添加stub
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));

        // 验证：stub应该被成功添加（查询参数匹配被跳过）
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    // ==================== 第二部分：JSON转义字符测试 ====================

    @Test
    @DisplayName("requestBodyPattern 包含转义字符 - equalToJson")
    void testEscapeCharacters_EqualToJson() {
        // 准备：包含转义字符的JSON
        StubMapping stub = createBaseStub();
        stub.setRequestBodyPattern("{\"equalToJson\": \"{\\\"name\\\": \\\"test\\\"}\"}");

        // 执行
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));

        // 验证
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestBodyPattern 包含正则转义 - matches 模式")
    void testEscapeCharacters_RegexMatches() {
        // 准备：包含正则表达式的JSON（需要转义）
        StubMapping stub = createBaseStub();
        // 注意：这里测试的是代码第542-558行的转义修复逻辑
        stub.setRequestBodyPattern("{\"matches\": \"test\\\\d+\"}"); // 包含反斜杠

        // 执行
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));

        // 验证
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestBodyPattern 无效JSON但包含反斜杠 - 触发转义修复逻辑")
    void testInvalidJsonWithBackslash_TriggerEscapeFix() {
        // 准备：无效JSON（缺少反斜杠转义）
        StubMapping stub = createBaseStub();
        stub.setRequestBodyPattern("{\"matches\": \"test\\d+\"}"); // 单个反斜杠会导致JSON解析失败

        // 执行：应该触发 542-558 行的转义修复逻辑
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));

        // 验证：即使JSON无效，也应该通过转义修复或兜底逻辑成功添加
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    // ==================== 第三部分：所有匹配类型覆盖测试 ====================

    @Test
    @DisplayName("requestBodyPattern - equalToJson 匹配")
    void testBodyPattern_EqualToJson() {
        StubMapping stub = createBaseStub();
        stub.setRequestBodyPattern("{\"equalToJson\": \"{\\\"userId\\\": 123}\"}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestBodyPattern - matchesJsonPath 匹配")
    void testBodyPattern_MatchesJsonPath() {
        StubMapping stub = createBaseStub();
        stub.setRequestBodyPattern("{\"matchesJsonPath\": \"$.userId\"}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestBodyPattern - contains 匹配")
    void testBodyPattern_Contains() {
        StubMapping stub = createBaseStub();
        stub.setRequestBodyPattern("{\"contains\": \"test\"}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestBodyPattern - matches (正则) 匹配")
    void testBodyPattern_Matches() {
        StubMapping stub = createBaseStub();
        stub.setRequestBodyPattern("{\"matches\": \".*test.*\"}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestHeadersPattern - equalTo 匹配")
    void testHeaderPattern_EqualTo() {
        StubMapping stub = createBaseStub();
        stub.setRequestHeadersPattern("{\"Content-Type\": {\"equalTo\": \"application/json\"}}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestHeadersPattern - contains 匹配")
    void testHeaderPattern_Contains() {
        StubMapping stub = createBaseStub();
        stub.setRequestHeadersPattern("{\"User-Agent\": {\"contains\": \"Chrome\"}}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("requestHeadersPattern - matches (正则) 匹配")
    void testHeaderPattern_Matches() {
        StubMapping stub = createBaseStub();
        stub.setRequestHeadersPattern("{\"Authorization\": {\"matches\": \"Bearer .*\"}}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("queryParametersPattern - equalTo 匹配")
    void testQueryPattern_EqualTo() {
        StubMapping stub = createBaseStub();
        stub.setQueryParametersPattern("{\"userId\": {\"equalTo\": \"123\"}}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("queryParametersPattern - contains 匹配")
    void testQueryPattern_Contains() {
        StubMapping stub = createBaseStub();
        stub.setQueryParametersPattern("{\"search\": {\"contains\": \"test\"}}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("queryParametersPattern - matches (正则) 匹配")
    void testQueryPattern_Matches() {
        StubMapping stub = createBaseStub();
        stub.setQueryParametersPattern("{\"page\": {\"matches\": \"\\\\d+\"}}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    // ==================== 第四部分：URL匹配类型全覆盖测试 ====================

    @Test
    @DisplayName("urlMatchType - EQUALS (精确匹配)")
    void testUrlMatchType_Equals() {
        StubMapping stub = createBaseStub();
        stub.setUrl("/api/users");
        stub.setUrlMatchType(UrlMatchType.EQUALS);

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("urlMatchType - CONTAINS (包含匹配)")
    void testUrlMatchType_Contains() {
        StubMapping stub = createBaseStub();
        stub.setUrl("users");
        stub.setUrlMatchType(UrlMatchType.CONTAINS);

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("urlMatchType - REGEX (正则匹配)")
    void testUrlMatchType_Regex() {
        StubMapping stub = createBaseStub();
        stub.setUrl("/api/users/\\d+");
        stub.setUrlMatchType(UrlMatchType.REGEX);

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("urlMatchType - PATH_TEMPLATE (路径模板，最复杂)")
    void testUrlMatchType_PathTemplate() {
        StubMapping stub = createBaseStub();
        stub.setUrl("/api/users/{id}/posts/{postId}");
        stub.setUrlMatchType(UrlMatchType.PATH_TEMPLATE);

        // 这个应该被转换为正则：/api/users/[^/]+/posts/[^/]+
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("urlMatchType - PATH_TEMPLATE 边界：单个参数")
    void testUrlMatchType_PathTemplate_SingleParam() {
        StubMapping stub = createBaseStub();
        stub.setUrl("/api/users/{id}");
        stub.setUrlMatchType(UrlMatchType.PATH_TEMPLATE);

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("urlMatchType - PATH_TEMPLATE 边界：嵌套路径")
    void testUrlMatchType_PathTemplate_NestedPath() {
        StubMapping stub = createBaseStub();
        stub.setUrl("/api/{version}/users/{userId}/orders/{orderId}");
        stub.setUrlMatchType(UrlMatchType.PATH_TEMPLATE);

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    // ==================== 第五部分：组合场景测试 ====================

    @Test
    @DisplayName("组合测试：同时包含所有类型的匹配规则")
    void testCombination_AllPatterns() {
        StubMapping stub = createBaseStub();
        stub.setUrl("/api/users/{id}");
        stub.setUrlMatchType(UrlMatchType.PATH_TEMPLATE);
        stub.setRequestHeadersPattern("{\"Authorization\": {\"matches\": \"Bearer .*\"}}");
        stub.setQueryParametersPattern("{\"active\": {\"equalTo\": \"true\"}}");
        stub.setRequestBodyPattern("{\"matchesJsonPath\": \"$.email\"}");

        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("组合测试：部分无效JSON + 部分有效JSON")
    void testCombination_MixedValidInvalidJson() {
        StubMapping stub = createBaseStub();
        stub.setRequestHeadersPattern("无效JSON"); // 无效
        stub.setQueryParametersPattern("{\"page\": {\"equalTo\": \"1\"}}"); // 有效
        stub.setRequestBodyPattern("{\"contains\": \"test\"}"); // 有效

        // 应该成功添加，无效的headers会被跳过，其他正常工作
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("边界测试：空字符串模式")
    void testBoundary_EmptyPatternStrings() {
        StubMapping stub = createBaseStub();
        stub.setRequestHeadersPattern("   "); // 仅空格
        stub.setQueryParametersPattern(""); // 空字符串
        stub.setRequestBodyPattern(null); // null

        // 根据代码逻辑（476、498、520行），空字符串会被跳过
        assertDoesNotThrow(() -> wireMockManager.addStubMapping(stub));
        assertEquals(1, wireMockManager.getAllStubs().size());
    }

    @Test
    @DisplayName("边界测试：优先级和HTTP方法")
    void testBoundary_PriorityAndMethod() {
        // 测试不同优先级
        StubMapping stub1 = createBaseStub();
        stub1.setName("高优先级");
        stub1.setPriority(1);
        stub1.setMethod("GET");

        StubMapping stub2 = createBaseStub();
        stub2.setName("低优先级");
        stub2.setUrl("/api/test2");
        stub2.setPriority(10);
        stub2.setMethod("POST");

        assertDoesNotThrow(() -> {
            wireMockManager.addStubMapping(stub1);
            wireMockManager.addStubMapping(stub2);
        });

        assertEquals(2, wireMockManager.getAllStubs().size());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建基础 stub，避免重复代码
     */
    private StubMapping createBaseStub() {
        StubMapping stub = new StubMapping();
        stub.setName("测试Stub-" + System.currentTimeMillis());
        stub.setMethod("GET");
        stub.setUrl("/api/test");
        stub.setUrlMatchType(UrlMatchType.EQUALS);
        stub.setEnabled(true);
        stub.setResponseDefinition("{\"message\": \"test\"}");
        stub.setPriority(0);
        return stub;
    }
}
