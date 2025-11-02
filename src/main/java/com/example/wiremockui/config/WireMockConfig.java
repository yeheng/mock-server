package com.example.wiremockui.config;

import java.io.IOException;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.wiremockui.service.WireMockManager;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WireMock 配置类
 * 将 WireMock 集成到 Spring Boot 的嵌入式 Undertow 容器中
 * 不使用独立端口，所有请求都通过同一个 Undertow 容器处理
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WireMockConfig {

    private final WireMockManager wireMockManager;

    /**
     * 注册 WireMock Servlet Filter
     * 这个 Filter 会在同一个 Undertow 容器中拦截和处理所有 stub 请求
     */
    @Bean
    public FilterRegistrationBean<WireMockRequestFilter> wireMockRequestFilter() {
        FilterRegistrationBean<WireMockRequestFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new WireMockRequestFilter(wireMockManager));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE);
        registrationBean.setName("wireMockRequestFilter");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        log.info("WireMock Request Filter 已注册 - 集成到 Undertow 容器");
        return registrationBean;
    }

    /**
     * WireMock 请求处理 Filter
     * 拦截请求并在同一个 Undertow 容器中处理所有 stub 请求
     */
    public static class WireMockRequestFilter extends OncePerRequestFilter {

        private final WireMockManager wireMockManager;

        public WireMockRequestFilter(WireMockManager wireMockManager) {
            this.wireMockManager = wireMockManager;
        }

        @Override
        protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                @NonNull FilterChain filterChain)
                throws ServletException, IOException {

            String requestURI = request.getRequestURI();

            // 跳过静态资源和 API 请求，让 Spring MVC 处理
            if (isStaticResource(requestURI) || isApiRequest(requestURI)) {
                filterChain.doFilter(request, response);
                return;
            }

            // 如果是根路径，返回应用信息
            if ("/".equals(requestURI)) {
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_OK);
                int serverPort = request.getServerPort();
                response.getWriter().write(String.format(
                        "{\"status\": \"WireMock UI Manager - 集成模式\", \"mode\": \"embedded\", \"serverPort\": %d, \"message\": \"WireMock 已集成到 Spring Boot 容器\"}",
                        serverPort));
                return;
            }

            // 所有其他请求都交给 WireMock 处理
            // 在同一个 Undertow 容器中处理，不使用独立端口
            log.debug("移交请求给 WireMock 处理: {}", requestURI);
            wireMockManager.handleRequest(request, response);
        }

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
                    requestURI.contains(".gif");
        }

        private boolean isApiRequest(String requestURI) {
            return requestURI.startsWith("/api/") ||
                    requestURI.startsWith("/actuator/") ||
                    requestURI.startsWith("/swagger") ||
                    requestURI.startsWith("/v3/api-docs");
        }
    }
}
