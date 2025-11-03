package com.example.wiremockui.filter;

import java.io.IOException;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.wiremockui.service.WireMockManager;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WireMock Servlet Filter
 * 将匹配的请求路由到 WireMock 服务器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // 高优先级
public class WireMockServletFilter implements Filter {

    private final WireMockManager wireMockManager;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String requestURI = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // 跳过静态资源和 Spring Boot 管理 API 请求（白名单）
        if (isStaticResource(requestURI) || isApiRequest(requestURI) || isWireMockAdminRequest(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        // 所有其他请求都交给 WireMockManager 直接处理
        // 现在使用本地处理，避免了原来的复杂代理逻辑
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
     * 判断是否为管理 API 请求（Spring Boot 应用自己的 API）
     * 管理API使用 /admin/ 前缀，其他路径交给 WireMock 处理
     */
    private boolean isApiRequest(String requestURI) {
        // 仅允许具体的管理端点由 Spring MVC 处理，其他 /admin/* 交给 WireMock
        return requestURI.startsWith("/admin/health") ||
               requestURI.startsWith("/admin/wiremock") ||
               requestURI.startsWith("/admin/stubs") ||
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
