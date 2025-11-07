package io.github.yeheng.wiremock.controller;

import io.github.yeheng.wiremock.entity.StubMapping;
import io.github.yeheng.wiremock.service.StubMappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

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

    private <T> ResponseEntity<T> handleException(Supplier<ResponseEntity<T>> operation) {
        try {
            return operation.get();
        } catch (IllegalArgumentException e) {
            // 资源不存在返回404，其他参数错误返回400
            return e.getMessage() != null && e.getMessage().contains("不存在")
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("操作失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 创建新的 Stub
     */
    @PostMapping
    public ResponseEntity<StubMapping> createStub(@Valid @RequestBody StubMapping stub) {
        return handleException(() -> {
            StubMapping createdStub = stubMappingService.createStub(stub);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdStub);
        });
    }

    /**
     * 获取所有 Stubs
     */
    @GetMapping
    public ResponseEntity<List<StubMapping>> getAllStubs() {
        return handleException(() -> {
            List<StubMapping> stubs = stubMappingService.getAllStubs();
            return ResponseEntity.ok(stubs);
        });
    }

    /**
     * 分页获取所有 Stubs
     */
    @GetMapping("/page")
    public ResponseEntity<Page<StubMapping>> getAllStubs(Pageable pageable) {
        return handleException(() -> {
            Page<StubMapping> stubs = stubMappingService.getAllStubs(pageable);
            return ResponseEntity.ok(stubs);
        });
    }

    /**
     * 搜索 Stubs
     */
    @GetMapping("/search")
    public ResponseEntity<List<StubMapping>> searchStubs(@RequestParam String keyword) {
        return handleException(() -> {
            List<StubMapping> stubs = stubMappingService.searchStubs(keyword);
            return ResponseEntity.ok(stubs);
        });
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
        return handleException(() -> {
            StubMapping stub = stubMappingService.updateStub(stubId, updatedStub);
            return ResponseEntity.ok(stub);
        });
    }

    /**
     * 删除 Stub
     */
    @DeleteMapping("/{stubId}")
    public ResponseEntity<Void> deleteStub(@PathVariable Long stubId) {
        return handleException(() -> {
            stubMappingService.deleteStub(stubId);
            return ResponseEntity.noContent().build();
        });
    }

    /**
     * 启用/禁用 Stub
     */
    @PostMapping("/{stubId}/toggle")
    public ResponseEntity<StubMapping> toggleStubEnabled(@PathVariable Long stubId) {
        return handleException(() -> {
            StubMapping stub = stubMappingService.toggleStubEnabled(stubId);
            return ResponseEntity.ok(stub);
        });
    }

    /**
     * 重新加载所有 stubs
     */
    @PostMapping("/reload")
    public ResponseEntity<Void> reloadAllStubs() {
        return handleException(() -> {
            stubMappingService.reloadAllStubs();
            return ResponseEntity.ok().build();
        });
    }

    /**
     * 获取 stub 统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<StubMappingService.StubStatistics> getStatistics() {
        return handleException(() -> {
            StubMappingService.StubStatistics stats = stubMappingService.getStatistics();
            return ResponseEntity.ok(stats);
        });
    }
}
