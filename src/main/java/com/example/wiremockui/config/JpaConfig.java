package com.example.wiremockui.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA 配置类
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories
public class JpaConfig {
}
