package io.github.yeheng.wiremock.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.github.yeheng.wiremock.entity.StubMapping;

/**
 * StubMapping 数据访问层
 * 提供高性能的数据库查询方法，支持分页和索引优化
 */
@Repository
public interface StubMappingRepository extends JpaRepository<StubMapping, Long> {

    /**
     * 根据名称模糊查找（保持向后兼容）
     */
    List<StubMapping> findByNameContainingIgnoreCase(String name);

    /**
     * 根据名称模糊查找（分页支持）
     * 使用@Query注解避免Spring Data JPA方法名解析问题
     */
    @Query("SELECT s FROM StubMapping s WHERE LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<StubMapping> findByNameWithPagination(@Param("name") String name, Pageable pageable);

    /**
     * 根据启用状态查找（保持向后兼容）
     */
    List<StubMapping> findByEnabled(Boolean enabled);

    /**
     * 根据启用状态查找（分页支持）
     * 使用@Query注解避免Spring Data JPA方法名解析问题
     */
    @Query("SELECT s FROM StubMapping s WHERE s.enabled = :enabled")
    Page<StubMapping> findByEnabledWithPagination(@Param("enabled") Boolean enabled, Pageable pageable);

    /**
     * 根据 HTTP 方法查找（保持向后兼容）
     */
    List<StubMapping> findByMethod(String method);

    /**
     * 根据 HTTP 方法查找（分页支持）
     * 使用@Query注解避免Spring Data JPA方法名解析问题
     */
    @Query("SELECT s FROM StubMapping s WHERE s.method = :method")
    Page<StubMapping> findByMethodWithPagination(@Param("method") String method, Pageable pageable);

    /**
     * 根据 UUID 查找
     */
    StubMapping findByUuid(String uuid);

    /**
     * 根据启用状态统计 Stub 数量
     * 使用long而不是Long避免空指针问题
     */
    @Query("SELECT COUNT(s) FROM StubMapping s WHERE s.enabled = :enabled")
    long countByEnabled(@Param("enabled") Boolean enabled);

    /**
     * 根据方法和URL查找（用于检查重复）
     */
    @Query("SELECT s FROM StubMapping s WHERE s.method = :method AND s.url = :url")
    List<StubMapping> findByMethodAndUrl(@Param("method") String method, @Param("url") String url);

    /**
     * 分页获取所有启用的Stub
     */
    @Query("SELECT s FROM StubMapping s WHERE s.enabled = true ORDER BY s.createdAt DESC")
    Page<StubMapping> findAllEnabled(Pageable pageable);
}
