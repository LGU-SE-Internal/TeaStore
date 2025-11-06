# TeaStore OpenTelemetry 迁移说明（中文版）

## 项目概述

TeaStore 是一个微服务参考应用程序，包含6个微服务：
- **webui** - Web界面服务
- **auth** - 认证服务
- **image** - 图片提供服务
- **persistence** - 数据持久化服务
- **recommender** - 推荐服务
- **registry** - 服务注册中心

## 问题分析

### 1. 仓库结构

```
TeaStore/
├── services/           # 6个微服务
│   ├── webui/
│   ├── auth/
│   ├── image/
│   ├── persistence/
│   ├── recommender/
│   └── registry/
├── utilities/          # 共享工具库
│   └── registryclient/ # 包含OpenTracing代码
├── interfaces/         # 共享接口和实体
└── pom.xml            # Maven父项目配置
```

**编译流程：**
```bash
mvn clean package  # 编译所有模块，生成WAR文件
```

每个服务都是独立的WAR文件，部署到Tomcat容器中运行。

### 2. 原有的问题

你遇到的问题确实存在，主要有三个方面：

#### 问题1: OpenTracing与OTel冲突

**现象：**
- 代码中使用了OpenTracing SDK（Jaeger client 0.32.0）
- 每个服务启动时调用 `GlobalTracer.register(Tracing.init(...))`
- 同时你又加了OpenTelemetry的Java agent自动注入

**冲突原因：**
- OpenTracing和OpenTelemetry都试图管理分布式追踪
- 两套系统会互相干扰，导致trace数据混乱或重复
- 手动注册的GlobalTracer会与OTel agent的自动追踪冲突

#### 问题2: Logback MDC键名不匹配

**现象：**
你在logback.xml中配置的pattern使用了：
```xml
TraceID: %X{traceId} SpanID: %X{spanId}
```

**问题：**
- OTel Java agent自动注入到MDC的键名是 `trace_id`、`span_id`、`trace_flags`
- 而不是 `traceId`、`spanId`
- 键名不匹配导致日志中无法显示trace ID

#### 问题3: 关键服务的logback配置

**你的观察是对的！**
- 仓库中所有7个服务都有logback.xml文件
- 但**所有服务的MDC键名都是错的**（都用的是traceId而不是trace_id）
- 所以所有服务都无法正确显示OTel的trace ID

## 解决方案

### 修改1: 更新所有Logback配置

修改了7个logback.xml文件：
- `services/webui/src/main/resources/logback.xml`
- `services/auth/src/main/resources/logback.xml`
- `services/image/src/main/resources/logback.xml`
- `services/persistence/src/main/resources/logback.xml`
- `services/recommender/src/main/resources/logback.xml`
- `services/registry/src/main/resources/logback.xml`
- `interfaces/entities/src/main/resources/logback.xml`

**修改内容：**
```xml
<!-- 之前 -->
<pattern>%d{HH:mm:ss.SSS} %-5level %logger{15}#%line TraceID: %X{traceId} SpanID: %X{spanId} %msg%n</pattern>

<!-- 修改后 -->
<pattern>%d{HH:mm:ss.SSS} %-5level %logger{15}#%line trace_id=%X{trace_id} span_id=%X{span_id} trace_flags=%X{trace_flags} %msg%n</pattern>
```

**效果：**
现在日志会显示：
```
04:30:15.123 INFO  WebuiStartup#56 trace_id=1234567890abcdef span_id=fedcba0987654321 trace_flags=01 Service started
```

### 修改2: 禁用OpenTracing手动初始化

在5个服务的启动类中注释掉了OpenTracing代码：
1. `WebuiStartup.java`
2. `AuthStartup.java`
3. `ImageProviderStartup.java`
4. `InitialDataGenerationDaemon.java` (persistence)
5. `RecommenderStartup.java`

**修改内容：**
```java
// 注释掉了import
// import io.opentracing.util.GlobalTracer;
// import tools.descartes.teastore.registryclient.tracing.Tracing;

// 注释掉了初始化代码
// GlobalTracer.register(Tracing.init(Service.WEBUI.getServiceName()));
```

### 修改3: 禁用OpenTracing上下文传播

在registryclient工具库中注释掉了手动传播代码：
- `HttpWrapper.java` - 注释掉 `Tracing.inject(builder)`
- `TrackingFilter.java` - 注释掉 `Tracing.extractCurrentSpan()`

**原因：**
OTel Java agent会自动处理HTTP头的trace上下文传播，不需要手动inject/extract。

### 保留但不使用的代码

**重要说明：**
- OpenTracing的依赖（jaeger-client）仍然保留在pom.xml中
- `Tracing.java`工具类代码仍然存在
- 只是不再调用这些代码

**为什么保留：**
1. 避免大规模重构
2. 保持向后兼容性
3. 不影响Kieker监控功能
4. 依赖虽然存在但不会干扰OTel

## 如何使用

### 启动服务时附加OTel Java Agent

```bash
# 下载OTel Java agent
wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# 设置环境变量
export OTEL_SERVICE_NAME=teastore-webui
export OTEL_EXPORTER_OTLP_ENDPOINT=http://your-collector:4317
export OTEL_TRACES_EXPORTER=otlp
export OTEL_LOGS_EXPORTER=otlp

# 启动应用
java -javaagent:opentelemetry-javaagent.jar -jar webui.war
```

### Docker方式

在Dockerfile中添加：
```dockerfile
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /opt/otel-agent.jar
ENV JAVA_OPTS="-javaagent:/opt/otel-agent.jar"
```

### Kubernetes方式

使用OpenTelemetry Operator自动注入：
```yaml
apiVersion: v1
kind: Pod
metadata:
  annotations:
    instrumentation.opentelemetry.io/inject-java: "true"
spec:
  containers:
  - name: webui
    image: teastore-webui:latest
```

## 验证修改

### 1. 检查编译
```bash
mvn clean package
# 应该显示 BUILD SUCCESS
```

### 2. 检查日志包含trace_id
```bash
# 启动服务后
docker logs teastore-webui | grep trace_id
# 应该看到类似：trace_id=abc123... span_id=def456...
```

### 3. 检查trace数据
- 在你的collector日志中查看是否收到trace数据
- 在Jaeger/Tempo等后端查看是否有完整的trace链路

## 关键配置建议

```bash
# 必需的环境变量
OTEL_SERVICE_NAME=teastore-webui          # 服务名称
OTEL_EXPORTER_OTLP_ENDPOINT=http://collector:4317  # Collector地址
OTEL_TRACES_EXPORTER=otlp                 # 使用OTLP导出器

# 推荐的环境变量
OTEL_PROPAGATORS=tracecontext,baggage,b3  # 支持多种传播格式
OTEL_RESOURCE_ATTRIBUTES=service.namespace=teastore,deployment.environment=prod

# 可选：日志自动关联
OTEL_LOGS_EXPORTER=otlp                   # 开启日志导出
```

## 常见问题

### Q1: 日志中trace_id仍然是空的？
**A:** 检查：
- OTel agent是否正确附加（-javaagent参数）
- OTEL_TRACES_EXPORTER是否设置
- 是否有请求进来（trace上下文只在处理请求时存在）

### Q2: Collector没收到trace数据？
**A:** 检查：
- OTEL_EXPORTER_OTLP_ENDPOINT配置是否正确
- Collector是否运行且可访问
- 查看Collector日志是否有错误

### Q3: 会影响Kieker监控吗？
**A:** 不会。Kieker监控是独立的系统，与OTel不冲突。

### Q4: 需要删除Jaeger依赖吗？
**A:** 不必要。虽然不再使用，但保留依赖不会造成问题。未来可以清理。

## 修改后的架构

```
请求流程：
用户 → WebUI → OTel Agent (自动创建span)
              ↓
         Auth Service → OTel Agent (自动传播trace context)
              ↓
         Persistence → OTel Agent (自动传播trace context)
              ↓
         所有trace数据 → OTel Collector → 你的后端(Jaeger/Tempo等)
```

**自动化的功能：**
- ✅ Trace创建和管理
- ✅ HTTP头的context传播（W3C Trace Context或B3）
- ✅ 日志中的trace ID注入（通过MDC）
- ✅ 跨服务的分布式追踪
- ✅ 与Collector的通信

**你不需要做的：**
- ❌ 手动创建tracer
- ❌ 手动inject/extract context
- ❌ 手动管理span
- ❌ 配置propagation格式

## 总结

### 解决的问题
1. ✅ 修复了OpenTracing与OTel的冲突
2. ✅ 修正了所有服务的logback MDC键名
3. ✅ 所有服务现在都能正确显示trace ID

### 残留代码说明
- OpenTracing代码保留但已禁用
- 不会干扰OTel的正常工作
- 可以在未来清理，但不是必需的

### 使用建议
- 使用OTel Java agent启动所有服务
- 配置正确的collector endpoint
- 验证日志中显示trace_id
- 在你的observability后端查看完整trace链路

详细的英文文档请参考 `OPENTELEMETRY_MIGRATION.md`。
