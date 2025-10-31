package com.example.wiremockui.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Web 配置类
 * 配置静态资源访问和页面路由
 */
@Configuration
@EnableWebMvc
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态资源处理
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0);
        
        // CSS 资源
        registry.addResourceHandler("/styles/**")
                .addResourceLocations("classpath:/static/styles/")
                .setCachePeriod(0);
        
        // JavaScript 资源
        registry.addResourceHandler("/scripts/**")
                .addResourceLocations("classpath:/static/scripts/")
                .setCachePeriod(0);
    }
    
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径映射到 index.html
        registry.addViewController("/").setViewName("forward:/index.html");
        
        // API 路径不处理，让 @RestController 处理
        registry.addViewController("/api").setViewName("forward:/index.html");
        registry.addViewController("/api/v1").setViewName("forward:/index.html");
    }
}
