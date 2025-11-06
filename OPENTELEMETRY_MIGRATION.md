# OpenTelemetry Migration Guide

## Overview

This document describes the changes made to support OpenTelemetry (OTel) auto-instrumentation and resolve conflicts with the legacy OpenTracing SDK.

## Problem Statement

The TeaStore application previously used:
1. **OpenTracing SDK with Jaeger client** for distributed tracing
2. **Manual instrumentation** via `GlobalTracer.register()` in service startup classes
3. **Logback MDC placeholders** with incorrect key names for OTel compatibility

When OpenTelemetry Java agent auto-instrumentation was added, conflicts arose:
- Both OpenTracing and OTel tried to manage trace propagation
- Logback MDC keys (`traceId`, `spanId`) didn't match OTel's keys (`trace_id`, `span_id`, `trace_flags`)
- Manual OpenTracing instrumentation interfered with OTel's automatic instrumentation

## Changes Made

### 1. Updated Logback Configuration (All Services)

**Files Modified:**
- `services/tools.descartes.teastore.webui/src/main/resources/logback.xml`
- `services/tools.descartes.teastore.auth/src/main/resources/logback.xml`
- `services/tools.descartes.teastore.image/src/main/resources/logback.xml`
- `services/tools.descartes.teastore.persistence/src/main/resources/logback.xml`
- `services/tools.descartes.teastore.recommender/src/main/resources/logback.xml`
- `services/tools.descartes.teastore.registry/src/main/resources/logback.xml`
- `interfaces/tools.descartes.teastore.entities/src/main/resources/logback.xml`

**Change:** Updated MDC pattern from:
```xml
<pattern>%d{HH:mm:ss.SSS} %-5level %logger{15}#%line TraceID: %X{traceId} SpanID: %X{spanId} %msg%n</pattern>
```

To:
```xml
<pattern>%d{HH:mm:ss.SSS} %-5level %logger{15}#%line trace_id=%X{trace_id} span_id=%X{span_id} trace_flags=%X{trace_flags} %msg%n</pattern>
```

**Rationale:** OTel Java agent automatically injects trace context into SLF4J MDC using keys `trace_id`, `span_id`, and `trace_flags`. The pattern must match these keys for automatic trace correlation in logs.

### 2. Disabled OpenTracing Manual Instrumentation

**Files Modified:**
- `services/tools.descartes.teastore.webui/src/main/java/tools/descartes/teastore/webui/startup/WebuiStartup.java`
- `services/tools.descartes.teastore.auth/src/main/java/tools/descartes/teastore/auth/startup/AuthStartup.java`
- `services/tools.descartes.teastore.image/src/main/java/tools/descartes/teastore/image/setup/ImageProviderStartup.java`
- `services/tools.descartes.teastore.persistence/src/main/java/tools/descartes/teastore/persistence/daemons/InitialDataGenerationDaemon.java`
- `services/tools.descartes.teastore.recommender/src/main/java/tools/descartes/teastore/recommender/servlet/RecommenderStartup.java`

**Change:** Commented out OpenTracing imports and `GlobalTracer.register()` calls:
```java
// OpenTracing imports commented out for OpenTelemetry compatibility
// import io.opentracing.util.GlobalTracer;
// import tools.descartes.teastore.registryclient.tracing.Tracing;

// In contextInitialized():
// GlobalTracer.register(Tracing.init(Service.XXX.getServiceName()));
```

**Rationale:** The OTel Java agent automatically handles tracer creation and registration. Manual OpenTracing tracer registration conflicts with this and can cause duplicate spans or missing trace context.

### 3. Disabled OpenTracing Context Propagation

**Files Modified:**
- `utilities/tools.descartes.teastore.registryclient/src/main/java/tools/descartes/teastore/registryclient/rest/HttpWrapper.java`
- `utilities/tools.descartes.teastore.registryclient/src/main/java/tools/descartes/teastore/registryclient/rest/TrackingFilter.java`

**Change:** Commented out OpenTracing span injection and extraction:
```java
// In HttpWrapper.wrap():
// Tracing.inject(builder);

// In TrackingFilter.doFilter():
// try (Scope scope = Tracing.extractCurrentSpan((HttpServletRequest) request)) {
```

**Rationale:** OTel Java agent automatically propagates trace context via HTTP headers using W3C Trace Context or B3 propagation. Manual OpenTracing propagation is redundant and can interfere.

### 4. Removed Kieker Monitoring Agent

**Files Modified:**
- `utilities/tools.descartes.teastore.dockerbase/Dockerfile`
- `utilities/tools.descartes.teastore.dockerbase/start.sh`

**Change:** Removed Kieker monitoring agent configuration from the base Docker image:
- Removed Kieker agent JAR, AOP configuration, and properties files from Docker image
- Removed `RABBITMQ_HOST` and `LOG_TO_FILE` environment variables
- Removed Kieker agent initialization from `start.sh`

**Rationale:** With OTel auto-instrumentation, Kieker monitoring becomes redundant. The OTel Java agent provides comprehensive observability including traces, metrics, and logs without requiring AspectJ weaving or additional agents. Removing Kieker reduces container size and eliminates potential agent conflicts.

## OpenTracing Dependencies Retained

**Important:** The Jaeger client dependency (`io.jaegertracing:jaeger-client:0.32.0`) in `utilities/tools.descartes.teastore.registryclient/pom.xml` has been **retained** but is no longer actively used for tracing.

**Why retained:**
1. The `Tracing` utility class still references OpenTracing types
2. Removing the dependency would require more extensive refactoring
3. Kieker monitoring components may have indirect dependencies
4. The dependency is benign when not actively used

**Note:** The Jaeger client dependency can be removed in a future cleanup phase if needed.

## Using OpenTelemetry Java Agent

To run the TeaStore with OpenTelemetry auto-instrumentation:

### Option 1: Docker with OTel Agent

```bash
# Download the OTel Java agent
wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# Set environment variables for OTel
export OTEL_SERVICE_NAME=teastore-webui
export OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
export OTEL_TRACES_EXPORTER=otlp
export OTEL_METRICS_EXPORTER=otlp
export OTEL_LOGS_EXPORTER=otlp

# Run with Java agent
java -javaagent:opentelemetry-javaagent.jar -jar app.war
```

### Option 2: Update Dockerfile

Add to your Dockerfile:
```dockerfile
# Download OTel Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /opt/opentelemetry-javaagent.jar
ENV JAVA_OPTS="-javaagent:/opt/opentelemetry-javaagent.jar"
```

### Option 3: Kubernetes Operator

Use the [OpenTelemetry Operator](https://github.com/open-telemetry/opentelemetry-operator) to automatically inject the Java agent into pods.

## Configuration

### Recommended OTel Environment Variables

```bash
# Service identification
OTEL_SERVICE_NAME=<service-name>
OTEL_RESOURCE_ATTRIBUTES=service.namespace=teastore,deployment.environment=production

# Exporter configuration
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317
OTEL_EXPORTER_OTLP_PROTOCOL=grpc

# Trace configuration
OTEL_TRACES_EXPORTER=otlp
OTEL_TRACES_SAMPLER=parentbased_always_on

# Metrics configuration (optional)
OTEL_METRICS_EXPORTER=otlp

# Logs configuration (optional)
OTEL_LOGS_EXPORTER=otlp

# Propagation format (use B3 for compatibility with Istio/Zipkin)
OTEL_PROPAGATORS=tracecontext,baggage,b3
```

## Log Output Format

With these changes, logs will now show:
```
04:30:15.123 INFO  WebuiStartup#56 trace_id=1234567890abcdef span_id=fedcba0987654321 trace_flags=01 Service started successfully
```

The trace context is automatically populated by the OTel Java agent and correlates logs with distributed traces.

## Verification

To verify the migration:

1. **Check logs contain trace IDs:**
   ```bash
   docker logs teastore-webui | grep trace_id
   ```

2. **Verify traces in collector:**
   - Check your OTel collector logs/metrics
   - Verify traces appear in your observability backend (Jaeger, Tempo, etc.)

3. **Test distributed tracing:**
   - Make a request to WebUI
   - Verify the trace spans multiple services
   - Check that trace context is propagated correctly

## Troubleshooting

### Logs don't show trace_id

**Problem:** `trace_id=` is empty in logs
**Solution:** 
- Verify OTel Java agent is attached (`-javaagent` flag)
- Check `OTEL_TRACES_EXPORTER` is set
- Ensure service is receiving requests (trace context only exists during requests)

### Traces not appearing in backend

**Problem:** No traces in Jaeger/Tempo
**Solution:**
- Check `OTEL_EXPORTER_OTLP_ENDPOINT` is correct
- Verify OTel collector is running and reachable
- Check collector logs for errors
- Verify sampler is not filtering out traces

### Conflicts with Kieker monitoring

**Problem:** Kieker monitoring stops working
**Solution:**
- The Kieker instrumentation is independent of OTel
- Check `CTRLINST.isMonitoringEnabled()` returns true
- Both Kieker and OTel can coexist

## Migration Checklist

- [x] Update all logback.xml files with correct MDC keys
- [x] Disable OpenTracing manual instrumentation in startup classes
- [x] Comment out OpenTracing context propagation in filters
- [x] Verify build succeeds with `mvn clean package`
- [ ] Test with OTel Java agent attached
- [ ] Verify traces appear in observability backend
- [ ] Verify logs contain trace_id/span_id
- [ ] Test distributed tracing across services
- [ ] Update Docker images with OTel agent
- [ ] Update Kubernetes manifests if applicable

## Future Improvements

1. **Remove Jaeger client dependency:** Once confirmed OTel works correctly, remove the unused dependency
2. **Refactor Tracing.java:** Convert to OTel-native APIs or remove entirely
3. **Custom spans:** Add manual OTel spans for business-critical operations
4. **Metrics:** Enable OTel metrics collection for deeper observability
5. **Baggage:** Use OTel baggage for propagating business context

## References

- [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)
- [OTel Java Agent Configuration](https://opentelemetry.io/docs/instrumentation/java/automatic/agent-config/)
- [OTel Logback MDC Instrumentation](https://opentelemetry.io/docs/instrumentation/java/automatic/logback/)
- [W3C Trace Context](https://www.w3.org/TR/trace-context/)
