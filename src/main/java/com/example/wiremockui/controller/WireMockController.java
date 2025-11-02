package com.example.wiremockui.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wiremockui.service.WireMockManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WireMock 全局状态 REST API 控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/wiremock")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WireMockController {

    private final WireMockManager wireMockManager;
    
    /**
     * 获取WireMock服务器状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", wireMockManager.isRunning());
        status.put("port", wireMockManager.getPort());
        status.put("serverUrl", String.format("http://localhost:%d", wireMockManager.getPort()));
        status.put("adminUrl", String.format("http://localhost:%d/__admin", wireMockManager.getPort()));
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * 重置WireMock服务器
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        try {
            wireMockManager.reset();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("重置WireMock服务器失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 清空请求日志
     */
    @PostMapping("/clear-logs")
    public ResponseEntity<Void> clearLogs() {
        try {
            wireMockManager.clearRequestLogs();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("清空请求日志失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * 获取请求日志
     */
    @GetMapping("/logs")
    public ResponseEntity<?> getLogs() {
        try {
            return ResponseEntity.ok(wireMockManager.getRequestLogs());
        } catch (Exception e) {
            log.error("获取请求日志失败: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
