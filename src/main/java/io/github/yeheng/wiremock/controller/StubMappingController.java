package io.github.yeheng.wiremock.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.yeheng.wiremock.entity.StubMapping;
import io.github.yeheng.wiremock.service.StubMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * StubMapping REST API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/stubs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StubMappingController {

    private final StubMappingService stubMappingService;

    /**
     * 创建新的 Stub
     */
    @PostMapping
    public ResponseEntity<StubMapping> createStub(@Valid @RequestBody StubMapping stub) {
        try {
            StubMapping createdStub = stubMappingService.createStub(stub);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdStub);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        } catch (Exception e) {
            log.error("创建 Stub 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取所有 Stubs
     */
    @GetMapping
    public ResponseEntity<List<StubMapping>> getAllStubs() {
        try {
            List<StubMapping> stubs = stubMappingService.getAllStubs();
            return ResponseEntity.ok(stubs);
        } catch (Exception e) {
            log.error("获取 Stubs 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 分页获取所有 Stubs
     */
    @GetMapping("/page")
    public ResponseEntity<Page<StubMapping>> getAllStubs(Pageable pageable) {
        try {
            Page<StubMapping> stubs = stubMappingService.getAllStubs(pageable);
            return ResponseEntity.ok(stubs);
        } catch (Exception e) {
            log.error("分页获取 Stubs 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 搜索 Stubs
     */
    @GetMapping("/search")
    public ResponseEntity<List<StubMapping>> searchStubs(@RequestParam String keyword) {
        try {
            List<StubMapping> stubs = stubMappingService.searchStubs(keyword);
            return ResponseEntity.ok(stubs);
        } catch (Exception e) {
            log.error("搜索 Stubs 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 根据 ID 获取 Stub
     */
    @GetMapping("/{stubId}")
    public ResponseEntity<StubMapping> getStubById(@PathVariable Long stubId) {
        return stubMappingService.getStubById(stubId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新 Stub
     */
    @PutMapping("/{stubId}")
    public ResponseEntity<StubMapping> updateStub(@PathVariable Long stubId,
                                                  @Valid @RequestBody StubMapping updatedStub) {
        try {
            StubMapping stub = stubMappingService.updateStub(stubId, updatedStub);
            return ResponseEntity.ok(stub);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("更新 Stub 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 删除 Stub
     */
    @DeleteMapping("/{stubId}")
    public ResponseEntity<Void> deleteStub(@PathVariable Long stubId) {
        try {
            stubMappingService.deleteStub(stubId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("删除 Stub 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 启用/禁用 Stub
     */
    @PostMapping("/{stubId}/toggle")
    public ResponseEntity<StubMapping> toggleStubEnabled(@PathVariable Long stubId) {
        try {
            StubMapping stub = stubMappingService.toggleStubEnabled(stubId);
            return ResponseEntity.ok(stub);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("切换 Stub 状态失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 重新加载所有 stubs
     */
    @PostMapping("/reload")
    public ResponseEntity<Void> reloadAllStubs() {
        try {
            stubMappingService.reloadAllStubs();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("重新加载 stubs 失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 获取 stub 统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<StubMappingService.StubStatistics> getStatistics() {
        try {
            StubMappingService.StubStatistics stats = stubMappingService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("获取统计信息失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
