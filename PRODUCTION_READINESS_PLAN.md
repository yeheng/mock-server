# WireMock UI - Production-Ready 改进计划

**制定日期**: 2025-11-12
**当前评分**: 60/100
**目标评分**: 85+/100
**预计工期**: 3-4 周

---

## 【优先级分类】

🔴 **P0 - 阻塞性问题**（不上线就上坟）
🟡 **P1 - 重要问题**（严重影响生产质量）
🟢 **P2 - 优化建议**（提升可维护性和性能）

---

## 📋 Phase 1: 安全加固和紧急修复（1-2 天）

### 🔴 P0-01: 立即禁用 H2 控制台

**风险等级**: 🔴 CRITICAL - 生产环境启用 H2 Console 等于开放数据库后门

**问题分析**:
```yaml
# 当前配置（自杀式配置）
h2:
  console:
    enabled: true      # ← 任何人都可以访问 /h2-console
    path: /h2-console  # ← 使用默认路径，容易被扫描攻击
```

**实施步骤**:

1. **创建环境分离配置**

```bash
# 创建配置文件
src/main/resources/
├── application.yml          # 默认配置（开发环境）
├── application-dev.yml      # 开发环境
├── application-prod.yml     # 生产环境
└── application-test.yml     # 测试环境
```

2. **修改 application-prod.yml**

```yaml
# src/main/resources/application-prod.yml
spring:
  # 生产环境禁用 H2 控制台
  h2:
    console:
      enabled: false

  # 使用文件数据库而非内存数据库
  datasource:
    url: jdbc:h2:file:./data/wiremockdb;DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE
    driver-class-name: org.h2.Driver
    username: ${DB_USERNAME:sa}  # 从环境变量读取
    password: ${DB_PASSWORD:}    # 从环境变量读取

  jpa:
    hibernate:
      ddl-auto: validate  # 生产环境使用 validate，不使用 create-drop

# 生产环境日志级别
logging:
  level:
    root: WARN
    io.github.yeheng.wiremock: INFO  # 生产环境只输出 INFO 级别
    org.springframework.web: WARN    # 关闭 Web DEBUG 日志

management:
  endpoints:
    web:
      exposure:
        include: health,info  # 生产环境只开放必要端点
  endpoint:
    health:
      show-details: never    # 不显示健康检查详情
```

3. **修改 Dockerfile**

```dockerfile
# 设置生产环境
ENV SPRING_PROFILES_ACTIVE=prod

# 添加卷映射，持久化数据
VOLUME ["/app/data", "/app/logs"]
```

**验收标准**:
- [ ] 生产环境 H2 Console 无法访问
- [ ] 使用文件数据库，数据可以持久化
- [ ] 敏感信息（密码）从环境变量读取
- [ ] 只有开发环境可以使用 `ddl-auto: create-drop`

---

### 🟡 P1-01: 统一异常处理

**当前问题**：异常处理分散在多个地方

```java
// WireMockManager.java 中有重复的代码
private void write503(HttpServletResponse response, String message)
private void write500(HttpServletResponse response, String message)
```

**实施步骤**:

1. **使用统一的全局异常处理器**（已部分实现）

```java
// src/main/java/io/github/yeheng/wiremock/controller/GlobalExceptionHandler.java

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<ErrorResponse> handleSystemException(SystemException e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("SYSTEM_ERROR", "系统内部错误"));
    }
}
```

**验收标准**:
- [ ] 删除 WireMockManager 中的 write503/write500 方法
- [ ] 所有异常通过 GlobalExceptionHandler 处理
- [ ] 日志记录使用 SLF4J 的占位符风格

---

## 📋 Phase 2: 架构重构和数据持久化（1 周）

### 🔴 P0-03: 重构 WireMockManager（单一职责原则）

**当前问题**: 一个类做了 5 件事，代码复杂度爆表

**重构方案**:

```
WireMockManager (264行) 拆分为:
├── WireMockServerManager     - 管理 WireMock 服务器生命周期
├── StubRegistry              - Stub 的内存注册表和数据库同步
├── RequestRouter             - HTTP 请求路由
├── StubConverter             - 实体转换（已存在）
└── StubMappingService        - 业务逻辑（已存在）
```

**实施步骤**:

1. **创建 WireMockServerManager**

```java
/**
 * 管理 WireMock 服务器的生命周期
 * 职责：启动、停止、健康检查
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WireMockServerManager {

    @Value("${wiremock.server.port:0}")
    private int wireMockPort;

    private final RequestConverter requestConverter;
    private final ResponseConverter responseConverter;

    private WireMockServer wireMockServer;
    private DirectCallHttpServer directCallServer;
    private volatile boolean isRunning = false;

    @PostConstruct
    public void start() {
        try {
            DirectCallHttpServerFactory factory = new DirectCallHttpServerFactory();
            WireMockConfiguration config = WireMockConfiguration.options()
                .port(wireMockPort)  // 可配置端口，0 表示动态端口
                .httpServerFactory(factory);

            wireMockServer = new WireMockServer(config);
            wireMockServer.start();
            directCallServer = factory.getHttpServer();
            isRunning = true;

            log.info("WireMock 服务器启动成功，端口: {}", getPort());
        } catch (Exception e) {
            log.error("WireMock 服务器启动失败", e);
            throw new SystemException("WIREMOCK_START_FAILED", "WireMock 服务器启动失败", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        isRunning = false;
        if (wireMockServer != null && wireMockServer.isRunning()) {
            try {
                wireMockServer.stop();
                log.info("WireMock 服务器已停止");
            } catch (Exception e) {
                log.warn("停止 WireMock 服务器失败", e);
            }
        }
    }

    public Response routeRequest(Request request) {
        if (!isRunning) {
            throw new SystemException("WIREMOCK_NOT_RUNNING", "WireMock 服务器未运行");
        }

        return request.getUrl().startsWith("/__admin")
            ? directCallServer.adminRequest(request)
            : directCallServer.stubRequest(request);
    }

    public void resetMappings() {
        wireMockServer.resetMappings();
    }

    public int getPort() {
        return wireMockServer != null ? wireMockServer.port() : 0;
    }

    public boolean isRunning() {
        return isRunning;
    }
}
```

2. **创建 StubRegistry（统一缓存管理）**

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class StubRegistry {

    private final StubMappingRepository repository;
    private final WireMockServerManager wireMockServerManager;
    private final StubMappingConverter converter;

    // 内存缓存 - 标识符:数据库ID
    private final Map<String, StubMapping> stubCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadStubsFromDatabase() {
        log.info("从数据库加载 Stub Mappings...");
        List<StubMapping> stubs = repository.findAllByEnabled(true);

        stubCache.clear();
        wireMockServerManager.resetMappings();

        for (StubMapping stub : stubs) {
            String cacheKey = getCacheKey(stub);
            stubCache.put(cacheKey, stub);
            registerToWireMock(stub);
        }

        log.info("成功加载 {} 个 Stub Mappings", stubCache.size());
    }

    public void register(StubMapping stub) {
        StubMapping saved = repository.save(stub);
        String cacheKey = getCacheKey(saved);
        stubCache.put(cacheKey, saved);
        registerToWireMock(saved);

        log.info("注册 Stub: {} ({}", saved.getName(), saved.getMethod());
    }

    public void unregister(String identifier) {
        stubCache.remove(identifier);
        repository.deleteByIdentifier(identifier);
        reloadFromDatabase();  // 重新加载以保持 WireMock 同步
    }

    public void update(StubMapping stub) {
        String cacheKey = getCacheKey(stub);
        stubCache.put(cacheKey, stub);
        repository.save(stub);
        reloadFromDatabase();
    }

    public Optional<StubMapping> get(String identifier) {
        return Optional.ofNullable(stubCache.get(identifier));
    }

    public List<StubMapping> getAll() {
        return new ArrayList<>(stubCache.values());
    }

    public void toggleEnable(String identifier) {
        get(identifier).ifPresent(stub -> {
            stub.setEnabled(!stub.getEnabled());
            repository.save(stub);
            reloadFromDatabase();
        });
    }

    private void registerToWireMock(StubMapping stub) {
        if (!Boolean.TRUE.equals(stub.getEnabled())) {
            return;  // 禁用的不注册
        }

        try {
            MappingBuilder builder = converter.convert(stub);
            wireMockServerManager.registerStub(builder);
        } catch (Exception e) {
            log.error("注册 Stub 到 WireMock 失败: {}", stub.getName(), e);
        }
    }

    private void reloadFromDatabase() {
        wireMockServerManager.resetMappings();
        List<StubMapping> enabledStubs = repository.findAllByEnabled(true);
        for (StubMapping stub : enabledStubs) {
            registerToWireMock(stub);
        }
    }

    private String getCacheKey(StubMapping stub) {
        return stub.getIdentifier() != null ? stub.getIdentifier()
            : "id-" + stub.getId();
    }
}
```

3. **修改 WireMockManager 使用新组件**

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class WireMockRequestHandler {

    private final WireMockServerManager serverManager;
    private final RequestConverter requestConverter;
    private final ResponseConverter responseConverter;

    public void handle(jakarta.servlet.http.HttpServletRequest servletRequest,
                       jakarta.servlet.http.HttpServletResponse servletResponse) throws IOException {

        if (!serverManager.isRunning()) {
            throw new SystemException("WIREMOCK_NOT_RUNNING", "WireMock 服务未运行");
        }

        try {
            Request request = requestConverter.convert(servletRequest);
            Response response = serverManager.routeRequest(request);
            responseConverter.convert(response, servletResponse);
        } catch (Exception e) {
            log.error("处理请求失败", e);
            throw new SystemException("REQUEST_PROCESS_ERROR", "请求处理失败", e);
        }
    }
}
```

**验收标准**:
- [ ] WireMockManager 业务逻辑少于 100 行
- [ ] 每个类职责单一，易于测试
- [ ] 删除所有重复的代码
- [ ] 所有依赖通过构造函数注入

---

### 🟡 P1-02: 优化 Virtual Threads 配置

**当前问题**: 启用了 Virtual Threads 但手动设置 worker 线程数 = 200

**Linus 点评**: 这是个矛盾配置，Virtual Threads 的优势就是不需要预先设置线程数

**实施步骤**:

```yaml
# application.yml
server:
  undertow:
    threads:
      # IO 线程数 = CPU 核心数（合理）
      io: 4
      # WORKER 线程应该交给 Virtual Threads 管理
      # worker: 200  ← 删除这行！
    # 其他配置保留
    buffer-size: 16384
    direct-buffers: true
```

Spring Boot 3.2+ 启用了 Virtual Threads 后，Undertow 自动使用虚拟线程，不需要手动配置。

**验收标准**:
- [ ] 删除 `server.undertow.threads.worker` 配置
- [ ] 通过 JMX 或 Actuator 验证使用的是 Virtual Threads

---

## 📋 Phase 3: 测试覆盖提升（1 周）

### 🔴 P0-05: 核心业务逻辑覆盖率达到 80%+

**当前状态**: service 包只有 54% 覆盖率

**目标**:
- 整体覆盖率达到 80%+
- service 包达到 90%+
- 关键分支（异常处理）100% 覆盖

**实施步骤**:

1. **生成覆盖率报告，识别薄弱环节**

```bash
mvn test jacoco:report
open target/site/jacoco/index.html
```

2. **Generate 详细的测试计划**

针对核心类 WireMockServerManager:
```java
@ExtendWith(MockitoExtension.class)
class WireMockServerManagerTest {

    @Mock
    private RequestConverter requestConverter;

    @Mock
    private ResponseConverter responseConverter;

    @InjectMocks
    private WireMockServerManager manager;

    @Test
    void shouldSuccessfullyStartServer() {
        // given

        // when
        manager.start();

        // then
        assertThat(manager.isRunning()).isTrue();
        assertThat(manager.getPort()).isGreaterThan(0);
    }

    @Test
    void shouldRouteToAdminEndpoint() throws Exception {
        // given
        Request request = mock(Request.class);
        when(request.getUrl()).thenReturn("/__admin/mappings");

        // when
        Response response = manager.routeRequest(request);

        // then
        assertThat(response).isNotNull();
    }

    @Test
    void shouldThrowExceptionWhenServerNotRunning() {
        // when & then
        assertThatThrownBy(() -> manager.routeRequest(mock(Request.class)))
            .isInstanceOf(SystemException.class)
            .hasMessageContaining("WireMock 服务未运行");
    }
}
```

3. **扩展集成测试覆盖**

```java
@SpringBootTest
@Transactional
class StubRegistryIntegrationTest {

    @Autowired
    private StubRegistry registry;

    @Autowired
    private StubMappingRepository repository;

    @Test
    void shouldPersistStubToDatabase() {
        // given
        StubMapping stub = new StubMapping();
        stub.setName("测试接口");
        stub.setMethod("GET");
        stub.setUrl("/api/test");
        stub.setEnabled(true);

        // when
        registry.register(stub);

        // then
        List<StubMapping> saved = repository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getName()).isEqualTo("测试接口");
    }

    @Test
    void shouldReloadStubsFromDatabaseOnStartup() {
        // given: 预先插入数据
        StubMapping stub = new StubMapping();
    stub.setName("已存在接口");
        stub.setEnabled(true);
        repository.save(stub);

        // when: 启动时加载
        registry.loadStubsFromDatabase();

        // then: 应该加载到缓存
        List<StubMapping> all = registry.getAll();
        assertThat(all).hasSize(1);
    }
}
```

**测试场景清单**:

- [ ] **WireMockServerManager**: 启动、停止、路由、异常场景
- [ ] **StubRegistry**: CRUD 操作、数据库同步、缓存一致性
- [ ] **StubMappingConverter**: 各种请求/响应转换
- [ ] **RequestConverter**: Servlet 请求转换的所有边界情况
- [ ] **异常处理**: BusinessException, SystemException 的所有分支
- [ ] **并发场景**: 多个线程同时操作 Stub

**自动化覆盖率检查**:

修改 JaCoCo 配置，提高最低要求：

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <rules>
            <rule>
                <element>PACKAGE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>  <!-- 提高到 80% -->
                    </limit>
                    <limit>
                        <counter>BRANCH</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.70</minimum>  <!-- 分支覆盖 70% -->
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</plugin>
```

**验收标准**:
- [ ] 总体覆盖率 >= 80%
- [ ] service 包覆盖率 >= 90%
- [ ] 所有异常分支都有测试
- [ ] 构建失败如果覆盖率不达标

---

### 🟡 P1-03: 添加并发测试

**当前问题**: 使用 ConcurrentHashMap 但没有并发测试

**实施步骤**:

```java
class ConcurrentStubTest {

    @Autowired
    private StubRegistry registry;

    @Test
    void shouldHandleConcurrentStubOperations() throws InterruptedException {
        // given: 多个线程同时操作
        int threadCount = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // when: 并发创建 stub
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    StubMapping stub = createTestStub("concurrent-" + index);
                    registry.register(stub);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(10, TimeUnit.SECONDS);

        // then: 没有异常，所有 stub 都成功创建
        assertThat(exceptions).isEmpty();
        assertThat(registry.getAll()).hasSize(threadCount);
    }
}
```

**验收标准**:
- [ ] 并发测试通过，无死锁
- [ ] 压力测试支持 100+ 并发
