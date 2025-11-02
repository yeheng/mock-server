package com.example.wiremockui.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.wiremockui.entity.StubMapping;

/**
 * StubMapping 数据访问层
 */
@Repository
public interface StubMappingRepository extends JpaRepository<StubMapping, Long> {

    /**
     * 根据名称模糊查找
     */
    List<StubMapping> findByNameContainingIgnoreCase(String name);

    /**
     * 根据启用状态查找
     */
    List<StubMapping> findByEnabled(Boolean enabled);

    /**
     * 根据 HTTP 方法查找
     */
    List<StubMapping> findByMethod(String method);

    /**
     * 根据 UUID 查找
     */
    StubMapping findByUuid(String uuid);

    /**
     * 统计启用的 Stub 数量
     */
    @Query("SELECT COUNT(s) FROM StubMapping s WHERE s.enabled = true")
    Long countByEnabled(Boolean enabled);
}
