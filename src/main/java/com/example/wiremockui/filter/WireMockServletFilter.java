package com.example.wiremockui.filter;

import com.example.wiremockui.service.WireMockManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * WireMock Servlet Filter
 * 将匹配的请求路由到 WireMock 服务器
 */
@Component
@RequiredArgsConstructor
@Order(1) // 高优先级
public class WireMockServletFilter implements Filter {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WireMockServletFilter.class);

    private final WireMockManager wireMockManager;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        
        // 跳过静态资源和 Spring Boot API 请求
        if (isStaticResource(requestURI) || isApiRequest(requestURI) || isWireMockAdminRequest(requestURI)) {
            chain.doFilter(request, response);
            return;
        }
        
        // 处理 WireMock 请求 - 路由到 WireMock 服务器
        try {
            wireMockManager.handleRequest(httpRequest, httpResponse);
        } catch (Exception e) {
            log.error("处理 WireMock 请求时出错: {} {}", method, requestURI, e);
            if (!httpResponse.isCommitted()) {
                httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                httpResponse.getWriter().write("WireMock 处理错误: " + e.getMessage());
            }
        }
    }
    
    /**
     * 判断是否为静态资源
     */
    private boolean isStaticResource(String requestURI) {
        return requestURI.startsWith("/static/") ||
               requestURI.startsWith("/webjars/") ||
               requestURI.startsWith("/h2-console/") ||
               requestURI.contains(".css") ||
               requestURI.contains(".js") ||
               requestURI.contains(".png") ||
               requestURI.contains(".jpg") ||
               requestURI.contains(".jpeg") ||
               requestURI.contains(".ico") ||
               requestURI.contains(".html") ||
               requestURI.contains(".gif") ||
               requestURI.endsWith(".ico");
    }
    
    /**
     * 判断是否为 API 请求
     */
    private boolean isApiRequest(String requestURI) {
        return requestURI.startsWith("/api/") ||
               requestURI.startsWith("/actuator/") ||
               requestURI.startsWith("/swagger") ||
               requestURI.startsWith("/v3/api-docs");
    }
    
    /**
     * 判断是否为 WireMock 管理请求
     */
    private boolean isWireMockAdminRequest(String requestURI) {
        return requestURI.startsWith("/__admin/") ||
               requestURI.startsWith("/mappings/") ||
               requestURI.startsWith("/files/");
    }
}
