package io.github.yeheng.wiremock.benchmark;

import io.github.yeheng.wiremock.WiremockUiApplication;
import io.github.yeheng.wiremock.entity.StubMapping;
import io.github.yeheng.wiremock.repository.StubMappingRepository;
import io.github.yeheng.wiremock.service.WireMockManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 综合性能基准测试
 * 验证所有性能优化措施的效果
 */
@Slf4j
@SpringBootTest(classes = WiremockUiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.h2.console.enabled=false",
        "spring.jpa.show-sql=false",
        "wiremock.integrated-mode=true"
})
@DisplayName("综合性能基准测试")
class ComprehensiveBenchmarkTest {

    @Autowired
    private WireMockManager wireMockManager;

    @Autowired
    private StubMappingRepository stubMappingRepository;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() {
        try {
            stubMappingRepository.deleteAll();
            wireMockManager.reset();
            wireMockManager.resetStats();
        } catch (Exception e) {
            log.warn("清理失败", e);
        }
    }

    @Test
    @DisplayName("场景1：单一Stub性能测试")
    void testSingleStubPerformance() throws Exception {
        log.info("=== 场景1：单一Stub性能测试 ===");

        // 创建单个stub
        StubMapping stub = createStub("test-1", "GET", "/api/test1");
        stubMappingRepository.save(stub);
        wireMockManager.reloadAllStubs(stubMappingRepository.findAll());

        // 等待WireMock启动
        Thread.sleep(1000);

        // 执行基准测试
        wireMockManager.runBenchmark(1000, () -> {
            try {
                String url = "http://localhost:" + port + "/api/test1";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                log.error("请求失败", e);
            }
        });

        // 输出性能统计
        printPerformanceStats();
        printOptimizationStats();
    }

    @Test
    @DisplayName("场景2：多Stub并发性能测试")
    void testMultipleStubPerformance() throws Exception {
        log.info("=== 场景2：多Stub并发性能测试 ===");

        // 创建多个stubs
        List<StubMapping> stubs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            stubs.add(createStub("test-" + i, "GET", "/api/test" + i));
        }
        stubMappingRepository.saveAll(stubs);
        wireMockManager.reloadAllStubs(stubMappingRepository.findAll());

        Thread.sleep(1000);

        // 并发基准测试
        wireMockManager.runBenchmark(1000, () -> {
            int index = (int) (Math.random() * 10);
            try {
                String url = "http://localhost:" + port + "/api/test" + index;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                log.error("请求失败", e);
            }
        });

        printPerformanceStats();
        printOptimizationStats();
    }

    @Test
    @DisplayName("场景3：高并发压力测试")
    void testHighConcurrencyPerformance() throws Exception {
        log.info("=== 场景3：高并发压力测试 ===");

        // 创建单个stub
        StubMapping stub = createStub("pressure-test", "GET", "/api/pressure");
        stubMappingRepository.save(stub);
        wireMockManager.reloadAllStubs(stubMappingRepository.findAll());

        Thread.sleep(1000);

        // 高并发测试
        int concurrentThreads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 1000; i++) {
            executor.submit(() -> {
                try {
                    String url = "http://localhost:" + port + "/api/pressure";
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .build();
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        while (!executor.isTerminated()) {
            Thread.sleep(10);
        }

        long duration = System.currentTimeMillis() - startTime;
        double throughput = (double) (successCount.get() + failCount.get()) / (duration / 1000.0);

        log.info("高并发测试完成: 成功={}, 失败={}, 耗时={}ms, 吞吐量={:.2f} rps",
            successCount.get(), failCount.get(), duration, throughput);

        printPerformanceStats();
        printOptimizationStats();
    }

    @Test
    @DisplayName("场景4：正则匹配性能测试")
    void testRegexMatchingPerformance() throws Exception {
        log.info("=== 场景4：正则匹配性能测试 ===");

        // 创建使用正则匹配的stub
        StubMapping stub = createStub("regex-test", "GET", "/api/users/\\d+");
        stub.setUrlMatchType(StubMapping.UrlMatchType.REGEX);
        stubMappingRepository.save(stub);
        wireMockManager.reloadAllStubs(stubMappingRepository.findAll());

        Thread.sleep(1000);

        // 正则匹配测试
        wireMockManager.runBenchmark(500, () -> {
            try {
                String url = "http://localhost:" + port + "/api/users/12345";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                log.error("请求失败", e);
            }
        });

        printPerformanceStats();
        printOptimizationStats();
    }

    private StubMapping createStub(String name, String method, String url) {
        StubMapping stub = new StubMapping();
        stub.setName(name);
        stub.setDescription("性能测试stub: " + name);
        stub.setMethod(method);
        stub.setUrl(url);
        stub.setEnabled(true);
        stub.setResponseDefinition(String.format(
            "{\"stub\": \"%s\", \"method\": \"%s\", \"url\": \"%s\", \"timestamp\": \"%s\"}",
            name, method, url, System.currentTimeMillis()
        ));
        return stub;
    }

    private void printPerformanceStats() {
        try {
            var stats = wireMockManager.getPerformanceStats();
            log.info("性能统计: {}", stats);
        } catch (Exception e) {
            log.warn("获取性能统计失败", e);
        }
    }

    private void printOptimizationStats() {
        try {
            var optStats = wireMockManager.getOptimizationStats();
            log.info("优化统计: {}", optStats);
        } catch (Exception e) {
            log.warn("获取优化统计失败", e);
        }
    }
}
