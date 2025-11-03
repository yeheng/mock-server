package io.github.yeheng.wiremock.controller;

import io.github.yeheng.wiremock.service.WireMockManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@RestController
@RequestMapping("/admin/health")
@RequiredArgsConstructor
public class HealthController {
    
    private final WireMockManager wireMockManager;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("wiremock", Map.of(
            "running", wireMockManager.isRunning(),
            "port", wireMockManager.getPort()
        ));
        
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}
