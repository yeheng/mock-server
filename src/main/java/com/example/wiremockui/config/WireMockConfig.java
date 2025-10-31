package com.example.wiremockui.config;

import com.example.wiremockui.service.WireMockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.undertow.UndertowServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * WireMock 配置类
 * 将 WireMock 集成到 Spring Boot 的嵌入式 Undertow 容器中
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(WireMockProperties.class)
@RequiredArgsConstructor
public class WireMockConfig {
    
    private final WireMockManager wireMockManager;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    /**
     * 配置嵌入式 Undertow
     */
    @Bean
    public WebServerFactoryCustomizer<UndertowServletWebServerFactory> undertowCustomizer() {
        return factory -> {
            // 添加监听器来初始化WireMock
            factory.addDeploymentInfoCustomizers(deploymentInfo -> {
                // 简化的初始化方式
                log.info("初始化嵌入式 WireMock 集成...");
                try {
                    // 延迟初始化，等待Spring完全启动
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000); // 等待2秒让Spring Boot完全启动
                            wireMockManager.initializeEmbeddedMode();
                            log.info("WireMock 集成到 Spring Boot 成功，端口: {}", wireMockManager.getPort());
                        } catch (Exception e) {
                            log.error("WireMock 集成失败", e);
                        }
                    }).start();
                } catch (Exception e) {
                    log.error("WireMock 启动失败", e);
                }
            });
        };
    }
    
    /**
     * 注册 WireMock Servlet Filter
     */
    @Bean
    public FilterRegistrationBean<WireMockRequestFilter> wireMockRequestFilter() {
        FilterRegistrationBean<WireMockRequestFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new WireMockRequestFilter(wireMockManager));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE);
        registrationBean.setName("wireMockRequestFilter");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        
        log.info("WireMock Request Filter 已注册");
        return registrationBean;
    }
    
    /**
     * WireMock 请求处理 Filter
     */
    public static class WireMockRequestFilter extends OncePerRequestFilter {
        
        private final WireMockManager wireMockManager;
        
        public WireMockRequestFilter(WireMockManager wireMockManager) {
            this.wireMockManager = wireMockManager;
        }
        
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            
            String requestURI = request.getRequestURI();
            
            // 跳过静态资源和 API 请求
            if (isStaticResource(requestURI) || isApiRequest(requestURI)) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // 如果是根路径，返回 WireMock 信息
            if ("/".equals(requestURI)) {
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_OK);
                int port = wireMockManager.getPort();
                int serverPortNum = request.getServerPort();
                response.getWriter().write(String.format(
                    "{\"status\": \"WireMock UI Manager\", \"wiremockPort\": %d, \"serverPort\": %d, \"message\": \"WireMock 服务器运行中\"}",
                    port,
                    serverPortNum
                ));
                return;
            }
            
            // 处理 WireMock 请求
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
