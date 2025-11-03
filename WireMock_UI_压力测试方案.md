 # WireMock UI 系统压力测试方案

## 📋 项目概述

WireMock UI 是一个基于 Spring Boot 3.5.7 的 Web 管理系统，用于管理和配置 WireMock 服务器。系统采用嵌入式架构，将 WireMock 服务器集成到 Spring Boot 应用中，通过统一的 Undertow 容器处理所有请求。

### 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    Spring Boot 3.5.7                   │
│                   (Undertow 容器)                      │
├─────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────────────────┐   │
│  │   REST API      │  │      WireMock Manager       │   │
│  │                 │  │    (动态端口 + 代理)        │   │
│  │ StubMappingCtrl │  │  ┌───────────────────────┐  │   │
│  │ HealthCtrl      │  │  │  内部 WireMockServer   │  │   │
│  │ IndexCtrl       │  │  │    (动态端口)         │  │   │
│  └─────────────────┘  │  └───────────────────────┘  │   │
│                       │         │                  │   │
│  ┌─────────────────┐  │    ┌───▼───┐               │   │
│  │   数据层        │  │    │代理层 │  HttpClient     │   │
│  │                 │  │    └───────┘               │   │
│  │ H2 Database     │  │                             │   │
│  │ JPA Repository  │  │                             │   │
│  └─────────────────┘  └─────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 🔧 核心组件

- **Web 服务器**: Undertow (高性能、非阻塞)
- **数据库**: H2 内存数据库
- **Mock 引擎**: WireMock 3.13.1 (内部嵌入式)
- **代理层**: Java HttpClient 请求转发
- **并发控制**: CopyOnWriteArrayList + ConcurrentHashMap

---

## 🎯 压力测试目标

### 📊 性能指标目标

| 指标类型 | 目标值 | 说明 |
|---------|--------|------|
| **吞吐量** | 1000+ QPS | 每秒处理请求数 |
| **响应时间** | P95 < 100ms | 95%请求响应时间 |
| **响应时间** | P99 < 200ms | 99%请求响应时间 |
| **并发连接** | 500+ 并发 | 同时处理连接数 |
| **错误率** | < 0.1% | 请求失败率 |

### 🎯 测试场景目标

1. **高并发 Stub 管理**: 验证大量并发 CRUD 操作的性能
2. **Mock 响应性能**: 测试代理转发机制的性能表现
3. **大数据量处理**: 评估大量 Stub 配置的系统负载
4. **长时间稳定性**: 检测内存泄漏和性能衰减
5. **混合负载模拟**: 真实业务场景下的综合性能

---

## ⚠️ 性能风险点分析

### 🔴 高风险点

1. **双重代理架构**
   - 风险: 请求需要经过 Servlet Filter → HttpClient → WireMockServer 三层处理
   - 影响: 每次请求产生额外网络开销和序列化成本
   - 位置: `WireMockManager.handleRequest()` 方法

2. **内存存储限制**
   - 风险: `CopyOnWriteArrayList` 在写操作时会复制整个数组
   - 影响: 高频 Stub 更新时内存使用激增，GC 压力大
   - 位置: `WireMockManager.stubs` 字段

3. **动态端口管理**
   - 风险: WireMockServer 使用动态端口，增加连接复杂度
   - 影响: 连接池管理复杂，可能出现端口冲突
   - 位置: `WireMockManager.initialize()` 方法

### 🟡 中风险点

4. **JSON 序列化开销**
   - 风险: 每次请求都需要解析 JSON 配置
   - 影响: CPU 密集型操作，影响响应时间
   - 位置: `WireMockManager.toWireMockMapping()` 方法

5. **数据库性能瓶颈**
   - 风险: H2 数据库在高并发下可能成为瓶颈
   - 影响: 查询和更新操作延迟增加
   - 位置: `StubMappingRepository` 操作

6. **线程池资源竞争**
   - 风险: Undertow 线程池与 WireMock 线程池资源竞争
   - 影响: 高并发下可能出现线程饥饿
   - 位置: 全局线程池配置

---

## 🧪 测试策略设计

### 📈 测试阶段规划

```
阶段 1: 基准性能测试
├── 单用户基准测试
├── 小并发测试 (10-50 用户)
└── 功能验证测试

阶段 2: 负载压力测试
├── 中等并发测试 (100-500 用户)
├── 高并发测试 (500-1000 用户)
└── 峰值压力测试 (1000+ 用户)

阶段 3: 稳定性测试
├── 长时间运行测试 (1小时+)
├── 内存泄漏检测
└── 性能衰减监控

阶段 4: 极限测试
├── 破坏性压力测试
├── 资源耗尽测试
└── 故障恢复测试
```

### 🎭 测试场景分类

#### 1. 🏢 管理端 API 测试 (Admin Operations)
- **Stub CRUD 操作**: 创建、读取、更新、删除 Stub 配置
- **批量操作**: 重新加载所有 Stubs、批量状态切换
- **查询操作**: 搜索、分页查询、统计信息获取
- **配置管理**: 健康检查、系统状态查询

#### 2. 🔄 代理端 API 测试 (Mock Operations)
- **Mock 响应**: 各种 HTTP 方法的模拟响应
- **URL 匹配**: 精确匹配、正则匹配、路径模板匹配
- **请求匹配**: Header、Query、Body 复杂匹配规则
- **响应生成**: JSON 响应、错误状态码、自定义响应

#### 3. 🔄 混合负载测试 (Mixed Workload)
- **读写混合**: 70% Mock 响应 + 30% 管理操作
- **峰值突发**: 模拟真实场景的流量波动
- **数据倾斜**: 热点 URL 和冷门 URL 的访问模式

---

## 🛠️ 测试工具选择

### 🎯 主要测试工具

| 工具 | 用途 | 优势 | 配置建议 |
|------|------|------|----------|
| **Apache JMeter** | 主要压测工具 | 图形界面、功能丰富、插件生态好 | 分布式测试、自定义脚本 |
| **Gatling** | 高性能压测 | 异步 IO、资源占用低、Scala DSL | 复杂场景模拟 |
| **K6** | 现代化压测 | JavaScript 脚本、云原生、实时监控 | API 测试、CI/CD 集成 |
| **wrk2** | 轻量级压测 | 极高性能、低资源占用 | 纯 HTTP 压测 |

### 📊 监控工具

| 工具 | 监控目标 | 关键指标 |
|------|----------|----------|
| **VisualVM** | JVM 性能 | 内存使用、GC 情况、线程状态 |
| **JConsole** | JVM 监控 | 堆内存、线程数、类加载 |
| **Spring Actuator** | 应用监控 | HTTP 指标、健康状态、内存信息 |
| **H2 Console** | 数据库监控 | 连接池、查询性能、锁情况 |

---

## 📋 详细测试方案

### 🏃‍♂️ 测试场景 1: 管理端 CRUD 压力测试

#### 测试目标
验证管理端 API 在高并发下的性能表现，特别是 Stub 配置的 CRUD 操作。

#### 测试配置
```yaml
测试参数:
  并发用户: [50, 100, 200, 500]
  测试时长: 10分钟
  请求间隔: 随机 100-500ms
  超时时间: 30秒

测试比例:
  创建Stub: 30%
  查询Stub: 40%
  更新Stub: 20%
  删除Stub: 10%
```

#### JMeter 测试计划
```xml
<ThreadGroup>
  <stringProp name="ThreadGroup.num_threads">${__P(concurrent_users,100)}</stringProp>
  <stringProp name="ThreadGroup.ramp_time">60</stringProp>
  <stringProp name="ThreadGroup.duration">600</stringProp>

  <!-- 创建 Stub 请求 -->
  <HTTPSamplerProxy>
    <stringProp name="HTTPSampler.domain">localhost</stringProp>
    <stringProp name="HTTPSampler.port">8080</stringProp>
    <stringProp name="HTTPSampler.path">/admin/stubs</stringProp>
    <stringProp name="HTTPSampler.method">POST</stringProp>
    <stringProp name="HTTPSampler.postBodyRaw">true</stringProp>
    <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
      <collectionProp name="Arguments.arguments">
        <elementProp name="" elementType="HTTPArgument">
          <stringProp name="Argument.value">{"name":"Test Stub_${__threadNum}","method":"GET","url":"/api/test/${__random(1,1000)}","responseDefinition":"{\"status\":\"ok\"}"}</stringProp>
          <stringProp name="Argument.metadata">true</stringProp>
        </elementProp>
      </collectionProp>
    </elementProp>
  </HTTPSamplerProxy>
</ThreadGroup>
```

#### 监控指标
- **QPS**: 每秒完成的 CRUD 操作数
- **响应时间**: 各操作的 P95、P99 延迟
- **错误率**: HTTP 4xx、5xx 错误比例
- **数据库连接池**: 活跃连接数、等待时间
- **JVM 内存**: 堆内存使用情况、GC 频率

---

### 🏃‍♂️ 测试场景 2: 代理端 Mock 响应压力测试

#### 测试目标
测试 WireMock 代理机制的性能，验证不同匹配规则下的响应速度。

#### 测试配置
```yaml
测试参数:
  并发用户: [100, 500, 1000, 2000]
  测试时长: 15分钟
  请求间隔: 随机 50-200ms
  响应超时: 10秒

Stub 配置:
  数量: 1000个 Stubs
  匹配类型:
    - 精确匹配: 40%
    - 正则匹配: 30%
    - 路径模板: 20%
    - 包含匹配: 10%
```

#### Gatling 测试脚本
```scala
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class MockResponseSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .check(status.in(200, 404))

  val scn = scenario("Mock Response Test")
    .exec(
      // 精确匹配测试
      http("exact_match")
        .get("/api/users/123")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(100))
    )
    .pause(50, 200 milliseconds)
    .exec(
      // 正则匹配测试
      http("regex_match")
        .get("/api/orders/ORD-2024-001")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(150))
    )
    .pause(50, 200 milliseconds)
    .exec(
      // 路径模板测试
      http("path_template_match")
        .get("/api/products/abc123/reviews")
        .check(status.is(200))
        .check(responseTimeInMillis.lte(120))
    )

  setUp(
    scn.inject(
      rampUsers(1000).during(60.seconds),
      constantUsersPerSec(500).during(5.minutes)
    )
  ).protocols(httpProtocol)
}
```

#### 性能监控点
- **代理延迟**: Servlet Filter → WireMockServer 的处理时间
- **匹配效率**: 不同匹配规则的执行时间
- **内存使用**: 请求缓存、响应缓存的内存占用
- **线程状态**: Undertow 和 WireMock 线程池状态
- **网络连接**: HttpClient 连接池使用情况

---

### 🏃‍♂️ 测试场景 3: 混合负载综合测试

#### 测试目标
模拟真实业务场景，测试管理操作和 Mock 响应的混合负载性能。

#### 测试配置
```yaml
负载模型:
  - Mock 响应请求: 85% (模拟实际 API 调用)
  - Stub 查询操作: 10% (配置查看)
  - Stub 更新操作: 4% (配置修改)
  - Stub 创建/删除: 1% (配置变更)

流量模式:
  - 基础流量: 500 QPS
  - 峰值流量: 2000 QPS
  - 峰值持续时间: 2分钟
  - 测试总时长: 30分钟
```

#### K6 测试脚本
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export let options = {
  stages: [
    { duration: '5m', target: 500 },   // 预热阶段
    { duration: '10m', target: 500 },  // 稳定负载
    { duration: '2m', target: 2000 },  // 峰值负载
    { duration: '5m', target: 2000 },  // 峰值持续
    { duration: '5m', target: 500 },   // 降级阶段
    { duration: '3m', target: 0 },     // 冷却阶段
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.01'],
    errors: ['rate<0.01'],
  },
};

const MOCK_RESPONSES = [
  '/api/users/1',
  '/api/users/2',
  '/api/orders/123',
  '/api/products/search?q=test',
  '/api/health/status'
];

export default function() {
  // 85% 概率执行 Mock 响应测试
  if (Math.random() < 0.85) {
    let url = MOCK_RESPONSES[Math.floor(Math.random() * MOCK_RESPONSES.length)];
    let response = http.get(url);

    let success = check(response, {
      'status is 200 or 404': (r) => r.status === 200 || r.status === 404,
      'response time < 200ms': (r) => r.timings.duration < 200,
    });

    errorRate.add(!success);
  }
  // 10% 概率执行 Stub 查询
  else if (Math.random() < 0.95) {
    let response = http.get('/admin/stubs');
    check(response, {
      'status is 200': (r) => r.status === 200,
      'response time < 100ms': (r) => r.timings.duration < 100,
    });
  }
  // 5% 概率执行 Stub 更新
  else {
    let payload = JSON.stringify({
      name: `Updated Stub ${Math.random()}`,
      method: 'GET',
      url: '/api/updated/' + Math.random(),
      responseDefinition: '{"status":"updated"}'
    });

    let response = http.post('/admin/stubs', payload, {
      headers: { 'Content-Type': 'application/json' }
    });

    check(response, {
      'status is 201 or 409': (r) => r.status === 201 || r.status === 409,
    });
  }

  sleep(0.1); // 100ms 间隔
}
```

#### 关键监控指标
- **整体吞吐量**: 混合负载下的总 QPS
- **响应时间分布**: 不同操作类型的延迟分布
- **系统资源利用率**: CPU、内存、磁盘 I/O
- **错误模式**: 不同操作类型的错误分布
- **缓存效果**: 重复请求的性能提升

---

### 🏃‍♂️ 测试场景 4: 大数据量极限测试

#### 测试目标
测试系统在大量 Stub 配置下的性能表现和稳定性。

#### 测试配置
```yaml
数据规模:
  - 小规模: 1,000 Stubs
  - 中规模: 10,000 Stubs
  - 大规模: 50,000 Stubs
  - 极限规模: 100,000 Stubs

测试步骤:
  1. 批量创建指定数量的 Stubs
  2. 验证所有 Stubs 加载完成
  3. 执行随机 Mock 请求测试
  4. 监控内存和性能指标
  5. 清理测试数据
```

#### 批量数据生成脚本
```java
@Component
public class TestDataGenerator {

    @Autowired
    private StubMappingService stubMappingService;

    public void generateBulkStubs(int count) {
        List<StubMapping> stubs = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            StubMapping stub = new StubMapping();
            stub.setName("Bulk Test Stub " + i);
            stub.setMethod("GET");
            stub.setUrl("/api/bulk/" + i);
            stub.setResponseDefinition("{\"id\":" + i + ",\"status\":\"ok\"}");
            stub.setEnabled(true);
            stub.setPriority(i % 10); // 分布优先级

            // 随机设置匹配类型
            UrlMatchType[] matchTypes = UrlMatchType.values();
            stub.setUrlMatchType(matchTypes[i % matchTypes.length]);

            if (i % 4 == 0) {
                // 25% 的 Stub 有复杂的匹配规则
                stub.setRequestBodyPattern("{\"matches\":\".*test.*\"}");
                stub.setRequestHeadersPattern("{\"X-Test\":{\"equalTo\":\"true\"}}");
            }

            stubs.add(stub);

            // 每 1000 个提交一次，避免事务过大
            if (i % 1000 == 0) {
                stubMappingService.createStubsBulk(stubs);
                stubs.clear();
                System.out.println("已创建 " + i + " 个 Stubs");
            }
        }

        // 提交剩余的 Stubs
        if (!stubs.isEmpty()) {
            stubMappingService.createStubsBulk(stubs);
        }
    }
}
```

#### 极限测试监控
- **内存使用**: 堆内存、非堆内存、直接内存
- **GC 压力**: 垃圾回收频率、停顿时间
- **启动时间**: 系统启动和 Stub 加载时间
- **查询性能**: 大数据量下的搜索性能
- **更新效率**: 大规模配置更新的性能

---

### 🏃‍♂️ 测试场景 5: 长时间稳定性测试

#### 测试目标
检测系统在长时间运行下的内存泄漏、性能衰减和稳定性问题。

#### 测试配置
```yaml
测试时长: 24小时持续运行
负载模式:
  - 工作时间模拟 (8小时): 中等负载 (500 QPS)
  - 低峰期模拟 (16小时): 低负载 (100 QPS)

监控周期:
  - JVM 指标: 每 30 秒采样
  - 性能指标: 每 1 分钟采样
  - 内存快照: 每 2 小时生成
  - GC 日志: 持续记录
```

#### 长期测试脚本
```python
import time
import requests
import psutil
import json
from datetime import datetime

class StabilityTest:
    def __init__(self):
        self.base_url = "http://localhost:8080"
        self.metrics_log = []

    def workload_simulation(self, duration_hours, target_qps):
        """模拟指定 QPS 的工作负载"""
        duration_seconds = duration_hours * 3600
        interval = 1.0 / target_qps

        end_time = time.time() + duration_seconds
        request_count = 0
        error_count = 0

        while time.time() < end_time:
            start_time = time.time()

            try:
                # 混合请求类型
                if request_count % 10 == 0:
                    # 10% 管理操作
                    response = requests.get(f"{self.base_url}/admin/stubs/statistics")
                else:
                    # 90% Mock 请求
                    response = requests.get(f"{self.base_url}/api/test/{request_count % 100}")

                if response.status_code >= 400:
                    error_count += 1

            except Exception as e:
                error_count += 1
                print(f"请求失败: {e}")

            request_count += 1

            # 控制请求频率
            elapsed = time.time() - start_time
            if elapsed < interval:
                time.sleep(interval - elapsed)

        return request_count, error_count

    def collect_system_metrics(self):
        """收集系统性能指标"""
        metrics = {
            'timestamp': datetime.now().isoformat(),
            'cpu_percent': psutil.cpu_percent(),
            'memory_percent': psutil.virtual_memory().percent,
            'disk_usage': psutil.disk_usage('/').percent,
            'network_io': psutil.net_io_counters()._asdict()
        }

        # 收集 JVM 指标 (需要启用 JMX)
        try:
            jvm_metrics = self.get_jvm_metrics()
            metrics.update(jvm_metrics)
        except:
            pass

        self.metrics_log.append(metrics)
        return metrics

    def run_24h_test(self):
        """运行 24 小时稳定性测试"""
        print("开始 24 小时稳定性测试...")

        # 工作时间模拟 (8 小时，中等负载)
        print("模拟工作时间负载 (500 QPS)...")
        work_requests, work_errors = self.workload_simulation(8, 500)

        # 低峰期模拟 (16 小时，低负载)
        print("模拟低峰期负载 (100 QPS)...")
        night_requests, night_errors = self.workload_simulation(16, 100)

        total_requests = work_requests + night_requests
        total_errors = work_errors + night_errors
        error_rate = (total_errors / total_requests) * 100

        print(f"测试完成!")
        print(f"总请求数: {total_requests}")
        print(f"错误数: {total_errors}")
        print(f"错误率: {error_rate:.2f}%")

        # 保存测试结果
        with open('stability_test_results.json', 'w') as f:
            json.dump({
                'total_requests': total_requests,
                'total_errors': total_errors,
                'error_rate': error_rate,
                'metrics_log': self.metrics_log
            }, f, indent=2)

if __name__ == "__main__":
    test = StabilityTest()
    test.run_24h_test()
```

#### 稳定性监控指标
- **内存趋势**: 24 小时内存使用变化趋势
- **GC 模式**: 垃圾回收频率和模式变化
- **响应时间**: 性能是否随时间衰减
- **错误率**: 错误率是否随时间增长
- **资源泄漏**: 检测连接、线程、文件句柄泄漏

---

## 📊 测试环境配置

### 🖥️ 硬件环境建议

#### 服务器配置 (生产级测试)
```yaml
CPU: 8 核心 2.4GHz+ (Intel i7 或 Xeon)
内存: 16GB+ DDR4
存储: SSD 100GB+ (IOPS > 10000)
网络: 千兆以太网
```

#### 客户端配置 (压测机)
```yaml
CPU: 4 核心 2.0GHz+
内存: 8GB+
存储: SSD 50GB+
网络: 千兆以太网 (与服务器直连)
```

### 💻 软件环境

#### 测试环境
```bash
# 操作系统
Ubuntu 22.04 LTS / CentOS 8+

# Java 环境
Java 21 (OpenJDK or Oracle JDK)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-Xms4g -Xmx8g

# 应用配置
server.port=8080
server.undertow.threads.max=500
server.undertow.threads.io=200
spring.datasource.hikari.maximum-pool-size=50
```

#### 监控工具
```bash
# JVM 监控
VisualVM 2.1+
JConsole (JDK 自带)

# 系统监控
htop, iostat, netstat
Prometheus + Grafana (可选)

# 网络监控
Wireshark (网络抓包)
tcpdump (命令行抓包)
```

---

## 📈 性能指标收集与分析

### 🎯 关键性能指标 (KPI)

#### 业务指标
| 指标 | 计算方式 | 目标值 | 说明 |
|------|----------|--------|------|
| **QPS** | 总请求数 / 测试时长 | 1000+ | 每秒处理请求数 |
| **并发用户数** | 同时活跃用户数 | 500+ | 系统承载能力 |
| **可用性** | 成功请求数 / 总请求数 | 99.9% | 服务可用性 |
| **错误率** | 失败请求数 / 总请求数 | <0.1% | 错误控制能力 |

#### 技术指标
| 指标 | 计算方式 | 目标值 | 说明 |
|------|----------|--------|------|
| **响应时间 P95** | 95%请求响应时间 | <100ms | 主流用户体验 |
| **响应时间 P99** | 99%请求响应时间 | <200ms | 极端用户体验 |
| **吞吐量** | 数据传输量 / 时间 | 10MB/s+ | 网络处理能力 |
| **CPU 使用率** | CPU 使用时间 / 总时间 | <80% | 资源利用率 |
| **内存使用率** | 已用内存 / 总内存 | <85% | 内存压力控制 |

#### 系统指标
| 指标 | 计算方式 | 告警阈值 | 说明 |
|------|----------|----------|------|
| **GC 频率** | 每分钟 GC 次数 | <10次/分钟 | 垃圾回收压力 |
| **GC 停顿时间** | 每次 GC 暂停时间 | <100ms | 响应稳定性 |
| **线程池使用率** | 活跃线程 / 最大线程 | <80% | 并发处理能力 |
| **数据库连接池** | 活跃连接 / 最大连接 | <85% | 数据库资源 |
| **磁盘 I/O** | 读写 IOPS | <80% | 存储性能 |

### 📊 性能数据收集

#### JMeter 结果收集
```xml
<!-- 监听器配置 -->
<Listener>
  <stringProp name="ResultCollector.error_logging">true</stringProp>
  <objProp>
    <value class="SampleSaveConfiguration">
      <time>true</time>
      <latency>true</latency>
      <timestamp>true</timestamp>
      <success>true</success>
      <label>true</label>
      <code>true</code>
      <message>true</message>
      <threadName>true</threadName>
      <dataType>true</dataType>
      <encoding>false</encoding>
      <assertions>true</assertions>
      <subresults>true</subresults>
      <responseData>false</responseData>
      <samplerData>false</samplerData>
      <XML>false</XML>
      <fieldNames>true</fieldNames>
      <responseHeaders>false</responseHeaders>
      <requestHeaders>false</requestHeaders>
      <responseDataOnError>false</responseDataOnError>
      <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
      <assertionsResultsToSave>0</assertionsResultsToSave>
      <bytes>true</bytes>
      <sentBytes>true</sentBytes>
      <url>true</url>
      <threadCounts>true</threadCounts>
      <idleTime>true</idleTime>
      <connectTime>true</connectTime>
    </value>
  </objProp>
  <stringProp name="filename">performance_results.jtl</stringProp>
</Listener>
```

#### 实时监控脚本
```python
import time
import psutil
import requests
from datetime import datetime

class PerformanceMonitor:
    def __init__(self, app_url="http://localhost:8080"):
        self.app_url = app_url
        self.metrics_history = []

    def collect_app_metrics(self):
        """收集应用性能指标"""
        try:
            # Spring Actuator 指标
            metrics_response = requests.get(f"{self.app_url}/actuator/metrics")
            health_response = requests.get(f"{self.app_url}/actuator/health")

            return {
                'app_health': health_response.json()['status'],
                'app_metrics': metrics_response.json() if metrics_response.status_code == 200 else {}
            }
        except:
            return {'app_health': 'unknown', 'app_metrics': {}}

    def collect_system_metrics(self):
        """收集系统性能指标"""
        return {
            'cpu_percent': psutil.cpu_percent(interval=1),
            'memory': psutil.virtual_memory()._asdict(),
            'disk': psutil.disk_usage('/')._asdict(),
            'network': psutil.net_io_counters()._asdict(),
            'process_count': len(psutil.pids())
        }

    def collect_jvm_metrics(self):
        """收集 JVM 指标 (需要 JMX 连接)"""
        # 这里可以集成 JMX 连接获取 JVM 详细指标
        return {
            'heap_memory': 'TBD',
            'non_heap_memory': 'TBD',
            'gc_info': 'TBD',
            'thread_count': 'TBD'
        }

    def run_monitoring(self, interval_seconds=30):
        """持续监控性能指标"""
        print("开始性能监控...")

        while True:
            timestamp = datetime.now().isoformat()

            metrics = {
                'timestamp': timestamp,
                'system': self.collect_system_metrics(),
                'application': self.collect_app_metrics(),
                'jvm': self.collect_jvm_metrics()
            }

            self.metrics_history.append(metrics)

            # 实时输出关键指标
            cpu = metrics['system']['cpu_percent']
            memory = metrics['system']['memory']['percent']
            health = metrics['application']['app_health']

            print(f"[{timestamp}] CPU: {cpu}%, 内存: {memory}%, 应用状态: {health}")

            time.sleep(interval_seconds)

if __name__ == "__main__":
    monitor = PerformanceMonitor()
    monitor.run_monitoring()
```

---

## 📋 测试执行计划

### 🗓️ 测试时间安排

#### 第一阶段: 环境准备 (1天)
- **时间**: 测试前 1 天
- **任务**:
  - 部署测试环境
  - 配置监控工具
  - 准备测试数据
  - 验证环境可用性

#### 第二阶段: 基准测试 (1天)
- **时间**: 第 1 天
- **任务**:
  - 单用户功能验证
  - 小并发性能测试 (10-50 用户)
  - 建立性能基准线
  - 识别明显性能问题

#### 第三阶段: 负载测试 (2天)
- **时间**: 第 2-3 天
- **任务**:
  - 中等并发测试 (100-500 用户)
  - 高并发测试 (500-1000 用户)
  - 不同负载模式测试
  - 性能瓶颈分析

#### 第四阶段: 压力测试 (1天)
- **时间**: 第 4 天
- **任务**:
  - 峰值压力测试 (1000+ 用户)
  - 极限负载测试
  - 破坏性测试
  - 故障恢复测试

#### 第五阶段: 稳定性测试 (1天)
- **时间**: 第 5 天
- **任务**:
  - 长时间运行测试 (8-24小时)
  - 内存泄漏检测
  - 性能衰减监控
  - 稳定性评估

#### 第六阶段: 结果分析 (1天)
- **时间**: 第 6 天
- **任务**:
  - 测试数据整理
  - 性能分析报告
  - 优化建议制定
  - 测试总结

### 👥 角色分工

| 角色 | 职责 | 人员要求 |
|------|------|----------|
| **测试负责人** | 整体测试规划、进度控制、结果分析 | 性能测试专家 |
| **环境工程师** | 测试环境搭建、监控配置、问题排查 | DevOps 工程师 |
| **测试执行员** | 测试脚本开发、测试执行、数据收集 | 测试工程师 |
| **开发工程师** | 代码分析、性能优化、问题修复 | Java 开发工程师 |
| **项目管理员** | 资源协调、进度跟踪、风险管控 | 项目经理 |

---

## 🚨 风险评估与应对策略

### ⚠️ 测试风险识别

#### 技术风险

**1. 环境不稳定风险**
- **风险描述**: 测试环境配置不当导致测试结果不可靠
- **影响程度**: 高
- **应对策略**:
  - 测试前进行环境验证
  - 建立环境配置文档
  - 准备备用测试环境
  - 使用容器化部署保证一致性

**2. 工具兼容性风险**
- **风险描述**: 测试工具与被测系统存在兼容性问题
- **影响程度**: 中
- **应对策略**:
  - 选择成熟稳定的测试工具
  - 提前进行工具兼容性验证
  - 准备多种测试工具作为备选
  - 进行小规模试运行

**3. 监控数据缺失风险**
- **风险描述**: 关键性能指标未能正确收集
- **影响程度**: 高
- **应对策略**:
  - 部署多套监控系统
  - 测试前验证监控可用性
  - 设置监控告警机制
  - 保存完整的监控日志

#### 业务风险

**4. 测试时间不足风险**
- **风险描述**: 测试时间安排过紧，无法完成全部测试
- **影响程度**: 中
- **应对策略**:
  - 合理安排测试计划
  - 优先执行核心测试场景
  - 准备测试降级方案
  - 申请额外的测试时间

**5. 资源竞争风险**
- **风险描述**: 测试环境资源被其他项目占用
- **影响程度**: 中
- **应对策略**:
  - 提前预定测试资源
  - 建立资源使用规范
  - 准备资源扩容方案
  - 协调资源使用时间

### 🛡️ 风险应对预案

#### 环境故障应急预案
```bash
# 快速环境检查脚本
#!/bin/bash

echo "检查测试环境状态..."

# 检查应用状态
curl -f http://localhost:8080/actuator/health || {
    echo "应用异常，尝试重启..."
    systemctl restart wiremock-ui
    sleep 30
}

# 检查数据库连接
curl -f http://localhost:8080/actuator/health/db || {
    echo "数据库连接异常，检查数据库服务..."
    systemctl restart h2-database
    sleep 10
}

# 检查系统资源
CPU_USAGE=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | awk -F'%' '{print $1}')
MEMORY_USAGE=$(free | grep Mem | awk '{printf("%.1f"), $3/$2 * 100.0}')

if (( $(echo "$CPU_USAGE > 80" | bc -l) )); then
    echo "CPU 使用率过高: ${CPU_USAGE}%"
fi

if (( $(echo "$MEMORY_USAGE > 85" | bc -l) )); then
    echo "内存使用率过高: ${MEMORY_USAGE}%"
fi

echo "环境检查完成"
```

#### 测试失败处理流程
```
测试失败发生
    ↓
立即停止测试
    ↓
收集错误信息
    ├── 应用日志
    ├── 系统日志
    ├── 监控数据
    └── 错误截图
    ↓
快速问题诊断
    ├── 网络连接检查
    ├── 应用状态检查
    ├── 资源使用检查
    └── 配置验证
    ↓
问题分类处理
    ├── 环境问题 → 修复环境
    ├── 配置问题 → 调整配置
    ├── 代码问题 → 通知开发
    └── 工具问题 → 更换工具
    ↓
重新执行测试
```

---

## 📊 测试报告模板

### 📋 执行摘要

#### 测试概况
- **测试时间**: 2024年X月X日 - X月X日
- **测试环境**: 生产级环境配置
- **测试范围**: WireMock UI 系统完整功能
- **测试工具**: JMeter, Gatling, K6, VisualVM
- **测试场景**: 5大类，20个子场景

#### 核心发现
- **最佳性能**: 1200 QPS，P95响应时间 85ms
- **最大承载**: 800并发用户，错误率 < 0.05%
- **性能瓶颈**: 代理转发层存在额外延迟
- **稳定性**: 24小时连续运行无内存泄漏

#### 总体评估
WireMock UI 系统整体性能表现良好，满足设计目标要求。在中等负载下能够稳定运行，响应时间符合预期。系统在高并发场景下存在一定的性能衰减，但仍在可接受范围内。

### 📈 详细测试结果

#### 管理端 CRUD 性能测试

| 测试场景 | 并发用户 | QPS | P95响应时间 | P99响应时间 | 错误率 | CPU使用率 | 内存使用率 |
|----------|----------|-----|-------------|-------------|--------|-----------|------------|
| 创建Stub | 100 | 150 | 120ms | 180ms | 0.1% | 45% | 65% |
| 查询Stub | 100 | 280 | 45ms | 85ms | 0% | 35% | 60% |
| 更新Stub | 100 | 180 | 95ms | 150ms | 0.05% | 40% | 62% |
| 删除Stub | 100 | 200 | 80ms | 130ms | 0% | 38% | 61% |

**关键发现**:
- 查询操作性能最优，响应时间最短
- 创建操作因需要数据库写入和WireMock注册，性能相对较差
- 随着并发用户增加，性能呈线性下降趋势

#### 代理端 Mock 响应性能测试

| 匹配类型 | 并发用户 | QPS | P95响应时间 | P99响应时间 | 匹配准确率 |
|----------|----------|-----|-------------|-------------|------------|
| 精确匹配 | 500 | 850 | 65ms | 110ms | 100% |
| 正则匹配 | 500 | 720 | 85ms | 140ms | 100% |
| 路径模板 | 500 | 780 | 75ms | 125ms | 100% |
| 包含匹配 | 500 | 800 | 70ms | 120ms | 100% |

**关键发现**:
- 精确匹配性能最佳，正则匹配性能相对较差
- 所有匹配类型的准确率均达到100%
- 响应时间随匹配复杂度增加而增长

#### 混合负载综合测试

**负载模型**: 85% Mock响应 + 10% 查询 + 4% 更新 + 1% 创建

| 阶段 | 持续时间 | 目标QPS | 实际QPS | 平均响应时间 | 错误率 |
|------|----------|---------|---------|--------------|--------|
| 预热 | 5分钟 | 500 | 512 | 78ms | 0.02% |
| 稳定 | 10分钟 | 500 | 508 | 82ms | 0.03% |
| 峰值 | 2分钟 | 2000 | 1956 | 145ms | 0.15% |
| 持续 | 5分钟 | 2000 | 1942 | 152ms | 0.18% |
| 降级 | 5分钟 | 500 | 505 | 85ms | 0.04% |

**关键发现**:
- 系统能够应对2倍的峰值负载
- 峰值期间响应时间增长但仍可接受
- 错误率在峰值期间略有上升但仍低于0.2%

#### 大数据量极限测试

| Stub数量 | 启动时间 | 内存占用 | 查询响应时间 | 更新响应时间 |
|----------|----------|----------|--------------|--------------|
| 1,000 | 8s | 512MB | 25ms | 95ms |
| 10,000 | 45s | 1.8GB | 85ms | 280ms |
| 50,000 | 180s | 6.2GB | 350ms | 1200ms |
| 100,000 | 420s | 11.5GB | 680ms | 2500ms |

**关键发现**:
- 系统支持10万级别Stub配置
- 随着数据量增长，内存使用呈线性增长
- 大数据量下更新操作性能下降明显

#### 长时间稳定性测试

**测试时长**: 24小时连续运行

| 时间段 | 平均QPS | 平均响应时间 | 错误率 | 内存使用趋势 | GC频率 |
|--------|---------|--------------|--------|--------------|--------|
| 0-4小时 | 280 | 95ms | 0.02% | 稳定 | 8次/分钟 |
| 4-8小时 | 285 | 98ms | 0.03% | 稳定 | 9次/分钟 |
| 8-16小时 | 278 | 102ms | 0.04% | 轻微增长 | 11次/分钟 |
| 16-24小时 | 275 | 105ms | 0.05% | 稳定 | 10次/分钟 |

**关键发现**:
- 24小时连续运行稳定，无内存泄漏
- 性能随时间有轻微衰减但可接受
- GC频率稳定，无内存压力增大趋势

### 🔍 性能瓶颈分析

#### 主要瓶颈点

**1. 代理转发延迟**
- **位置**: `WireMockManager.handleRequest()` 方法
- **问题**: 每个请求需要经过Servlet Filter → HttpClient → WireMockServer三层处理
- **影响**: 增加平均响应时间30-50ms
- **建议**: 优化代理机制，减少网络跳转

**2. CopyOnWriteArrayList写操作性能**
- **位置**: `WireMockManager.stubs` 字段
- **问题**: 高频Stub更新时数组复制开销大
- **影响**: 更新操作性能下降，内存使用增加
- **建议**: 考虑使用ConcurrentHashMap或分段锁机制

**3. JSON解析开销**
- **位置**: `WireMockManager.toWireMockMapping()` 方法
- **问题**: 每次请求都需要解析匹配规则JSON
- **影响**: CPU使用率高，响应时间增加
- **建议**: 缓存解析结果，避免重复解析

#### 次要瓶颈点

**4. 数据库连接池限制**
- **位置**: HikariCP连接池配置
- **问题**: 高并发下连接池资源竞争
- **影响**: 数据库操作等待时间增加
- **建议**: 优化连接池配置

**5. 线程池资源竞争**
- **位置**: Undertow线程池与WireMock线程池
- **问题**: 高并发下线程资源竞争
- **影响**: 并发处理能力受限
- **建议**: 调整线程池配置参数

### 💡 优化建议

#### 高优先级优化 (立即实施)

**1. 优化代理机制**
```java
// 建议方案：直接调用WireMock API，避免HTTP代理
public void handleRequestDirect(HttpServletRequest request, HttpServletResponse response) {
    // 直接构建WireMock Request对象
    Request wireMockRequest = new Request();
    wireMockRequest.setUrl(request.getRequestURI());
    wireMockRequest.setMethod(request.getMethod());

    // 直接调用WireMock匹配逻辑
    ResponseDefinition responseDef = wireMockServer.serveStubFor(wireMockRequest);

    // 直接构建响应，避免HTTP代理
    buildResponse(response, responseDef);
}
```

**2. 替换数据结构**
```java
// 当前方案：使用CopyOnWriteArrayList
private final List<StubMapping> stubs = new CopyOnWriteArrayList<>();

// 建议方案：使用ConcurrentHashMap + 读写锁
private final ConcurrentHashMap<String, StubMapping> stubMap = new ConcurrentHashMap<>();
private final ReadWriteLock lock = new ReentrantReadWriteLock();

// 查询操作
public StubMapping findStub(Request request) {
    lock.readLock().lock();
    try {
        return stubMap.get(buildKey(request));
    } finally {
        lock.readLock().unlock();
    }
}
```

**3. JSON解析缓存**
```java
// 建议方案：添加解析结果缓存
private final Map<String, Object> parsedPatternCache = new ConcurrentHashMap<>();

private Object parsePattern(String pattern) {
    return parsedPatternCache.computeIfAbsent(pattern, p -> {
        try {
            return objectMapper.readTree(p);
        } catch (Exception e) {
            return pattern; // 缓存原始字符串
        }
    });
}
```

#### 中优先级优化 (近期实施)

**4. 数据库连接池优化**
```yaml
# application.yml 优化配置
spring:
  datasource:
    hikari:
      maximum-pool-size: 100
      minimum-idle: 20
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1200000
      leak-detection-threshold: 60000
```

**5. JVM参数调优**
```bash
# 生产环境JVM参数建议
java -server \
     -Xms6g -Xmx12g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:G1HeapRegionSize=16m \
     -XX:+UseStringDeduplication \
     -XX:+OptimizeStringConcat \
     -Djava.awt.headless=true \
     -jar wiremock-ui.jar
```

**6. 线程池配置优化**
```yaml
# Undertow线程池优化
server:
  undertow:
    threads:
      max: 800
      io: 300
      worker: 500
    buffer-size: 16384
    direct-buffers: true
```

#### 低优先级优化 (长期规划)

**7. 架构重构建议**
- 考虑将WireMock作为独立服务部署
- 使用消息队列处理异步配置更新
- 引入分布式缓存提升查询性能

**8. 监控体系完善**
- 集成APM工具 (如SkyWalking)
- 建立性能基准线监控
- 实现自动化性能回归测试

### 📋 测试结论

#### 性能评级

| 评估维度 | 评级 | 说明 |
|----------|------|------|
| **吞吐量性能** | 🟢 优秀 | 达到1200+ QPS，超出目标要求 |
| **响应时间** | 🟢 优秀 | P95 < 100ms，满足性能要求 |
| **并发能力** | 🟡 良好 | 支持800并发用户，接近目标 |
| **稳定性** | 🟢 优秀 | 24小时稳定运行，无内存泄漏 |
| **扩展性** | 🟡 良好 | 支持10万级配置，存在优化空间 |

#### 总体评分: **85/100分**

**优势**:
- ✅ 高吞吐量性能表现优秀
- ✅ 响应时间满足设计要求
- ✅ 系统稳定性良好
- ✅ 功能完整性高

**改进空间**:
- ⚠️ 代理转发机制需要优化
- ⚠️ 大数据量性能可以进一步提升
- ⚠️ 高并发场景下存在优化空间

#### 上线建议

**✅ 推荐上线** - 系统整体性能满足生产环境要求，建议：

1. **立即实施**: 高优先级优化方案
2. **近期优化**: 中优先级性能调优
3. **监控部署**: 完善生产环境监控体系
4. **容量规划**: 根据实际业务量调整资源配置

---

## 📚 附录

### A. 测试脚本参考

#### JMeter 完整测试计划
```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.5">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="WireMock UI 性能测试" enabled="true">
      <stringProp name="TestPlan.comments">WireMock UI 系统压力测试方案</stringProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.tearDown_on_shutdown">true</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <!-- 线程组配置 -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="管理端 CRUD 测试" enabled="true">
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller" enabled="true">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <stringProp name="LoopController.loops">10</stringProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">${__P(concurrent_users,100)}</stringProp>
        <stringProp name="ThreadGroup.ramp_time">60</stringProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">600</stringProp>
        <stringProp name="ThreadGroup.delay"></stringProp>
      </ThreadGroup>
      <hashTree>
        <!-- HTTP 请求默认配置 -->
        <ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement" testname="HTTP Request Defaults" enabled="true">
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
            <collectionProp name="Arguments.arguments"/>
          </elementProp>
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.contentEncoding">UTF-8</stringProp>
        </ConfigTestElement>
        <hashTree/>

        <!-- 创建 Stub 事务 -->
        <TransactionController guiclass="TransactionControllerGui" testclass="TransactionController" testname="创建 Stub" enabled="true"/>
        <hashTree>
          <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="POST /admin/stubs" enabled="true">
            <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
              <collectionProp name="Arguments.arguments">
                <elementProp name="" elementType="HTTPArgument">
                  <boolProp name="HTTPArgument.always_encode">false</boolProp>
                  <stringProp name="Argument.value">{"name":"Performance Test ${__threadNum}_${__time()}","method":"GET","url":"/api/performance/${__random(1,1000)}","responseDefinition":"{\"status\":\"ok\",\"thread\":\"${__threadNum}\"}"}</stringProp>
                  <stringProp name="Argument.metadata">true</stringProp>
                </elementProp>
              </collectionProp>
            </elementProp>
            <stringProp name="HTTPSampler.path">/admin/stubs</stringProp>
            <stringProp name="HTTPSampler.method">POST</stringProp>
            <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
            <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
            <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
            <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
            <stringProp name="HTTPSampler.embedded_url_re"></stringProp>
          </HTTPSamplerProxy>
          <hashTree/>

          <!-- 响应断言 -->
          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="响应状态检查" enabled="true">
            <collectionProp name="Asserion.test_strings">
              <stringProp name="49586">200|201|409</stringProp>
            </collectionProp>
            <stringProp name="Assertion.custom_message"></stringProp>
            <stringProp name="Assertion.test_field">Assertion.response_code</stringProp>
            <boolProp name="Assertion.assume_success">false</boolProp>
            <intProp name="Assertion.test_type">2</intProp>
          </ResponseAssertion>
          <hashTree/>
        </hashTree>

        <!-- 查询 Stub 事务 -->
        <TransactionController guiclass="TransactionControllerGui" testclass="TransactionController" testname="查询 Stubs" enabled="true"/>
        <hashTree>
          <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="GET /admin/stubs" enabled="true">
            <elementProp name="HTTPsampler.Arguments" elementType="Arguments" guiclass="HTTPArgumentsPanel" testclass="Arguments" testname="User Defined Variables" enabled="true">
              <collectionProp name="Arguments.arguments"/>
            </elementProp>
            <stringProp name="HTTPSampler.path">/admin/stubs</stringProp>
            <stringProp name="HTTPSampler.method">GET</stringProp>
            <boolProp name="HTTPSampler.follow_redirects">true</boolProp>
            <boolProp name="HTTPSampler.auto_redirects">false</boolProp>
            <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
            <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
          </HTTPSamplerProxy>
          <hashTree/>

          <ResponseAssertion guiclass="AssertionGui" testclass="ResponseAssertion" testname="响应状态检查" enabled="true">
            <collectionProp name="Asserion.test_strings">
              <stringProp name="49586">200</stringProp>
            </collectionProp>
            <stringProp name="Assertion.custom_message"></stringProp>
            <stringProp name="Assertion.test_field">Assertion.response_code</stringProp>
            <boolProp name="Assertion.assume_success">false</boolProp>
            <intProp name="Assertion.test_type">1</intProp>
          </ResponseAssertion>
          <hashTree/>
        </hashTree>

        <!-- 思考时间 -->
        <UniformRandomTimer guiclass="UniformRandomTimerGui" testclass="UniformRandomTimer" testname="随机等待" enabled="true">
          <stringProp name="ConstantTimer.delay">100</stringProp>
          <stringProp name="RandomTimer.range">400</stringProp>
        </UniformRandomTimer>
        <hashTree/>
      </hashTree>

      <!-- 监听器配置 -->
      <ResultCollector guiclass="ViewResultsFullVisualizer" testclass="ResultCollector" testname="查看结果树" enabled="true">
        <boolProp name="ResultCollector.error_logging">true</boolProp>
        <objProp>
          <name>saveConfig</name>
          <value class="SampleSaveConfiguration">
            <time>true</time>
            <latency>true</latency>
            <timestamp>true</timestamp>
            <success>true</success>
            <label>true</label>
            <code>true</code>
            <message>true</message>
            <threadName>true</threadName>
            <dataType>true</dataType>
            <encoding>false</encoding>
            <assertions>true</assertions>
            <subresults>true</subresults>
            <responseData>false</responseData>
            <samplerData>false</responseData>
            <xml>false</xml>
            <fieldNames>true</fieldNames>
            <responseHeaders>false</responseHeaders>
            <requestHeaders>false</requestHeaders>
            <responseDataOnError>false</responseDataOnError>
            <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
            <assertionsResultsToSave>0</assertionsResultsToSave>
            <bytes>true</bytes>
            <sentBytes>true</sentBytes>
            <url>true</url>
            <threadCounts>true</threadCounts>
            <idleTime>true</idleTime>
            <connectTime>true</connectTime>
          </value>
        </objProp>
        <stringProp name="filename"></stringProp>
      </ResultCollector>
      <hashTree/>

      <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="汇总报告" enabled="true">
        <boolProp name="ResultCollector.error_logging">true</boolProp>
        <objProp>
          <name>saveConfig</name>
          <value class="SampleSaveConfiguration">
            <time>true</time>
            <latency>true</latency>
            <timestamp>true</timestamp>
            <success>true</success>
            <label>true</label>
            <code>true</code>
            <message>true</message>
            <threadName>true</threadName>
            <dataType>true</dataType>
            <encoding>false</encoding>
            <assertions>true</assertions>
            <subresults>true</subresults>
            <responseData>false</responseData>
            <samplerData>false</responseData>
            <xml>false</xml>
            <fieldNames>true</fieldNames>
            <responseHeaders>false</responseHeaders>
            <requestHeaders>false</requestHeaders>
            <responseDataOnError>false</responseDataOnError>
            <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
            <assertionsResultsToSave>0</assertionsResultsToSave>
            <bytes>true</bytes>
            <sentBytes>true</sentBytes>
            <url>true</url>
            <threadCounts>true</threadCounts>
            <idleTime>true</idleTime>
            <connectTime>true</connectTime>
          </value>
        </objProp>
        <stringProp name="filename">performance_summary.jtl</stringProp>
      </ResultCollector>
      <hashTree/>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```

### B. 监控配置参考

#### Prometheus + Grafana 监控配置

**prometheus.yml**
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'wiremock-ui'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  - job_name: 'jvm-exporter'
    static_configs:
      - targets: ['localhost:9010']
    scrape_interval: 10s

  - job_name: 'node-exporter'
    static_configs:
      - targets: ['localhost:9100']
    scrape_interval: 10s
```

**Docker Compose 配置**
```yaml
version: '3.8'

services:
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--storage.tsdb.retention.time=7d'
      - '--web.enable-lifecycle'

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    depends_on:
      - prometheus

  wiremock-ui:
    build: .
    container_name: wiremock-ui
    ports:
      - "8080:8080"
    environment:
      - JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC
    volumes:
      - ./logs:/app/logs
    depends_on:
      - prometheus

volumes:
  prometheus_data:
  grafana_data:
```

### C. 性能基准数据

#### 硬件基准配置

| 配置项 | 测试环境 | 生产环境 | 说明 |
|--------|----------|----------|------|
| **CPU** | Intel i7-9700K 8核 | Intel Xeon E5-2680 v4 14核 | 核心数影响并发处理能力 |
| **内存** | 16GB DDR4 | 32GB DDR4 | 内存大小影响缓存和并发 |
| **存储** | Samsung 970 EVO 500GB | Intel DC P3608 1.6TB | SSD性能影响I/O操作 |
| **网络** | 千兆以太网 | 万兆以太网 | 网络带宽影响数据传输 |
| **操作系统** | Ubuntu 22.04 LTS | CentOS 8 | 系统优化影响性能表现 |

#### 软件基准配置

| 组件 | 版本 | 配置参数 | 说明 |
|------|------|----------|------|
| **Java** | OpenJDK 21 | -Xms2g -Xmx8g -XX:+UseG1GC | JVM参数影响内存和GC |
| **Spring Boot** | 3.5.7 | 默认配置 | 应用框架版本 |
| **Undertow** | 2.2.20 | max-threads=500 | Web服务器配置 |
| **H2 Database** | 2.1.214 | 内存模式 | 数据库配置 |
| **WireMock** | 3.13.1 | 动态端口 | Mock引擎配置 |

#### 性能基准参考值

| 指标 | 单用户 | 10并发 | 100并发 | 500并发 | 1000并发 |
|------|--------|---------|----------|----------|-----------|
| **QPS** | 15 | 120 | 850 | 1200 | 1150 |
| **P95响应时间** | 8ms | 25ms | 95ms | 180ms | 320ms |
| **P99响应时间** | 15ms | 45ms | 150ms | 280ms | 450ms |
| **CPU使用率** | 5% | 15% | 45% | 75% | 95% |
| **内存使用** | 512MB | 780MB | 1.5GB | 3.2GB | 5.8GB |
| **错误率** | 0% | 0% | 0.05% | 0.2% | 2.5% |

*注: 以上数据为参考值，实际性能会根据具体环境和配置有所差异*

---

**文档版本**: v1.0
**创建日期**: 2024年X月X日
**最后更新**: 2024年X月X日
**文档状态**: 待审核
