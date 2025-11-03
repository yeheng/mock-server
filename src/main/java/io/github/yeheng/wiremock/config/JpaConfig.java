package io.github.yeheng.wiremock.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 配置类
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.example.wiremockui.repository")
public class JpaConfig {
}
