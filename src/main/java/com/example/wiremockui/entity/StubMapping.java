package com.example.wiremockui.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * WireMock Stub 映射实体
 */
@Entity
@Table(name = "stub_mappings")
@Data
@EqualsAndHashCode(of = "id")
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class StubMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 50)
    private String uuid;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Integer priority = 0;

    @Column(length = 10, nullable = false)
    private String method;

    @Column(length = 1000, nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UrlMatchType urlMatchType = UrlMatchType.EQUALS;

    @Column(columnDefinition = "CLOB")
    private String requestBodyPattern;

    @Column(columnDefinition = "CLOB")
    private String requestHeadersPattern;

    @Column(columnDefinition = "CLOB")
    private String queryParametersPattern;

    @Column(columnDefinition = "CLOB", nullable = false)
    private String responseDefinition;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum UrlMatchType {
        EQUALS,
        CONTAINS,
        REGEX,
        PATH_TEMPLATE
    }

    @Transient
    public String getRequestPattern() {
        if (!Boolean.TRUE.equals(enabled)) {
            return null;
        }
        
        return String.format("%s %s (%s)", 
                           method != null ? method.toUpperCase() : "GET", 
                           url, urlMatchType);
    }

    @Transient
    public String getResponseBody() {
        if (responseDefinition == null) {
            return null;
        }
        
        return responseDefinition.trim();
    }

    @Transient
    public String toWireMockResponseDefinition() {
        if (responseDefinition == null) {
            throw new RuntimeException("响应定义不能为null");
        }

        // 验证JSON格式
        String trimmedResponse = responseDefinition.trim();
        if (trimmedResponse.isEmpty()) {
            throw new RuntimeException("响应定义不能为空");
        }

        // 简单的JSON格式验证
        if (!(trimmedResponse.startsWith("{") && trimmedResponse.endsWith("}")) &&
            !(trimmedResponse.startsWith("[") && trimmedResponse.endsWith("]"))) {
            throw new RuntimeException("响应定义必须是有效的JSON格式");
        }

        return responseDefinition;
    }
}
