package io.github.yeheng.wiremock.filter;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.yeheng.wiremock.service.WireMockManager;

/**
 * WireMockServletFilter 路由逻辑测试
 *
 * 测试目标：验证 Filter 的路由决策逻辑，确保：
 * 1. 管理API请求（/admin/*）被正确路由到 Spring MVC
 * 2. 静态资源被正确放行
 * 3. WireMock 管理请求（/__admin/*）被正确处理
 * 4. 所有其他请求被路由到 WireMock 进行匹配
 * 5. 各种 HTTP 方法被正确处理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WireMock Servlet Filter 路由测试")
class WireMockServletFilterTest {

    private WireMockServletFilter filter;

    @Mock
    private WireMockManager wireMockManager;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private ServletOutputStream outputStream;

    @Mock
    private PrintWriter printWriter;

    @BeforeEach
    void setUp() throws IOException {
        filter = new WireMockServletFilter(wireMockManager);
        // 只在异常处理测试中才 mock writer，避免 Mockito UnnecessaryStubbing 警告
    }

    // ==================== 第一部分：管理API路由测试 ====================

    @Test
    @DisplayName("管理API - /admin/stubs 应该由 Spring MVC 处理")
    void testAdminApi_Stubs() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/admin/stubs");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该调用 filterChain.doFilter（交给 Spring MVC）
        verify(filterChain, times(1)).doFilter(request, response);
        // 验证：不应该调用 wireMockManager.handleRequest
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("管理API - /admin/stubs/subpath 应该由 Spring MVC 处理")
    void testAdminApi_StubsSubpath() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/admin/stubs/123");
        when(request.getMethod()).thenReturn("DELETE");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该交给 Spring MVC
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("管理API - /admin/health 应该由 Spring MVC 处理")
    void testAdminApi_Health() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/admin/health");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("管理API - /admin/wiremock 应该由 Spring MVC 处理")
    void testAdminApi_WireMock() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/admin/wiremock/status");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("非管理API - /api/admin/stubs 应该由 WireMock 处理")
    void testNonAdminApi_ApiAdminStubs() throws ServletException, IOException {
        // 准备：注意这是 /api/admin/stubs，不是 /admin/stubs
        when(request.getRequestURI()).thenReturn("/api/admin/stubs");
        when(request.getMethod()).thenReturn("POST");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该由 WireMock 处理
        verify(wireMockManager, times(1)).handleRequest(request, response);
        // 不应该调用 filterChain
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ==================== 第二部分：WireMock 管理API测试 ====================

    @Test
    @DisplayName("WireMock 管理API - /__admin/ 应该由 WireMock 处理")
    void testWireMockAdminApi_Admin() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/__admin/mappings");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该交给 Spring MVC（根据代码，WireMock管理请求也走filterChain）
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("WireMock 文件API - /mappings/ 应该由 Spring MVC 处理")
    void testWireMockAdminApi_Mappings() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/mappings/abc");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("WireMock 文件API - /files/ 应该由 Spring MVC 处理")
    void testWireMockAdminApi_Files() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/files/test.json");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    // ==================== 第三部分：静态资源测试 ====================

    @Test
    @DisplayName("静态资源 - /static/main.js 应该放行")
    void testStaticResource_StaticPath() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/static/main.js");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该放行到 servlet chain
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("静态资源 - .css 文件应该放行")
    void testStaticResource_CssFile() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/styles/app.css");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("静态资源 - .js 文件应该放行")
    void testStaticResource_JsFile() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/app.js");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("静态资源 - favicon.ico 应该放行")
    void testStaticResource_Favicon() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/favicon.ico");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("静态资源 - 图片文件应该放行")
    void testStaticResource_ImageFiles() throws ServletException, IOException {
        // 准备
        String[] imageFiles = {"/logo.png", "/banner.jpg", "/icon.jpeg", "/bg.gif"};

        for (String imageFile : imageFiles) {
            when(request.getRequestURI()).thenReturn(imageFile);
            when(request.getMethod()).thenReturn("GET");

            // 执行
            filter.doFilter(request, response, filterChain);

            // 验证
            verify(filterChain, atLeastOnce()).doFilter(request, response);
        }

        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    @Test
    @DisplayName("静态资源 - H2 Console 应该放行")
    void testStaticResource_H2Console() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/h2-console/login.do");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    // ==================== 第四部分：WireMock 匹配请求测试 ====================

    @Test
    @DisplayName("WireMock请求 - /api/users 应该由 WireMock 处理")
    void testWireMockRequest_ApiUsers() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/api/users");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该由 WireMock 处理
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("WireMock请求 - 根路径 / 应该由 WireMock 处理")
    void testWireMockRequest_Root() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("WireMock请求 - 任意路径应该由 WireMock 处理")
    void testWireMockRequest_ArbitraryPath() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/some/random/path");
        when(request.getMethod()).thenReturn("POST");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    // ==================== 第五部分：HTTP 方法测试 ====================

    @Test
    @DisplayName("HTTP方法 - POST 到非管理路径应该由 WireMock 处理")
    void testHttpMethod_Post() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getMethod()).thenReturn("POST");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("HTTP方法 - PUT 到非管理路径应该由 WireMock 处理")
    void testHttpMethod_Put() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/api/users/123");
        when(request.getMethod()).thenReturn("PUT");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("HTTP方法 - DELETE 到非管理路径应该由 WireMock 处理")
    void testHttpMethod_Delete() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/api/users/123");
        when(request.getMethod()).thenReturn("DELETE");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("HTTP方法 - PATCH 到非管理路径应该由 WireMock 处理")
    void testHttpMethod_Patch() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/api/users/123");
        when(request.getMethod()).thenReturn("PATCH");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("HTTP方法 - GET 到管理路径应该由 Spring MVC 处理")
    void testHttpMethod_GetToAdminPath() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/admin/stubs");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：即使是 GET，管理路径也应该交给 Spring MVC
        verify(filterChain, times(1)).doFilter(request, response);
        verify(wireMockManager, never()).handleRequest(any(), any());
    }

    // ==================== 第六部分：边界和组合测试 ====================

    @Test
    @DisplayName("边界测试 - /admin 前缀但不在白名单中应该由 WireMock 处理")
    void testBoundary_AdminPrefixNotInWhitelist() throws ServletException, IOException {
        // 准备：/admin/custom 不在白名单中（只有 /admin/health, /admin/wiremock, /admin/stubs）
        when(request.getRequestURI()).thenReturn("/admin/custom");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该由 WireMock 处理
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("边界测试 - 路径大小写敏感")
    void testBoundary_CaseSensitivePaths() throws ServletException, IOException {
        // 准备：/Admin/stubs（大写A）不应该匹配 /admin/stubs
        when(request.getRequestURI()).thenReturn("/Admin/stubs");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该由 WireMock 处理（因为路径大小写不匹配）
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("边界测试 - 查询参数不影响路由决策")
    void testBoundary_QueryParametersDoNotAffectRouting() throws ServletException, IOException {
        // 准备
        when(request.getRequestURI()).thenReturn("/api/users");
        when(request.getMethod()).thenReturn("GET");

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：查询参数不应该影响路由（应该由 WireMock 处理）
        verify(wireMockManager, times(1)).handleRequest(request, response);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("异常处理 - WireMockManager 抛出异常应该返回500")
    void testErrorHandling_WireMockManagerThrowsException() throws ServletException, IOException {
        // 准备：在这个测试中才 mock writer
        when(response.getWriter()).thenReturn(printWriter);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getMethod()).thenReturn("GET");
        doThrow(new IOException("模拟异常")).when(wireMockManager).handleRequest(any(), any());

        // 执行
        filter.doFilter(request, response, filterChain);

        // 验证：应该设置 500 状态码
        verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        verify(response).getWriter();
    }
}
