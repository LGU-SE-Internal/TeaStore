package tools.descartes.teastore.registryclient.tracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.HttpHeaders;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

/**
 * Utility functions for OpenTelemetry integration.
 * Migrated from OpenTracing/Jaeger to OpenTelemetry SDK.
 *
 * <p>This implementation is designed to work with OpenTelemetry auto-instrumentation
 * (Java agent or Kubernetes operator), which configures the SDK via environment variables.
 * When running without auto-instrumentation, the SDK should be configured programmatically
 * or using the autoconfigure module.
 *
 * <p>Log correlation with traces:
 * The OpenTelemetry Logback appender automatically correlates logs with the current trace
 * when a span is active (via {@code span.makeCurrent()}). For this to work:
 * <ul>
 *   <li>With auto-instrumentation: The agent handles appender installation automatically</li>
 *   <li>Without auto-instrumentation: Call {@code OpenTelemetryAppender.install(openTelemetry)}
 *       after SDK initialization, which this class does automatically</li>
 * </ul>
 *
 * @author Long Bui
 */
public final class Tracing {

  /**
   * Instrumentation library version for OpenTelemetry tracer.
   */
  private static final String INSTRUMENTATION_VERSION = "1.0.0";

  private static volatile Tracer tracer;
  private static volatile boolean initialized = false;

  private Tracing() {
  }

  /**
   * TextMapGetter for extracting context from HTTP headers.
   */
  private static final TextMapGetter<Map<String, String>> TEXT_MAP_GETTER =
      new TextMapGetter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          if (carrier == null) {
            return null;
          }
          // Headers are case-insensitive, try both cases
          String value = carrier.get(key);
          if (value == null) {
            value = carrier.get(key.toLowerCase());
          }
          return value;
        }
      };

  /**
   * TextMapSetter for injecting context into request headers.
   */
  private static final TextMapSetter<Invocation.Builder> TEXT_MAP_SETTER =
      (carrier, key, value) -> {
        if (carrier != null) {
          carrier.header(key, value);
        }
      };

  /**
   * Initialize the OpenTelemetry tracer for the service.
   * When using OpenTelemetry auto-instrumentation (Java agent or Kubernetes operator),
   * this method uses GlobalOpenTelemetry to get the tracer.
   *
   * <p>For auto-instrumentation (Java agent or Kubernetes operator), the SDK is configured
   * automatically and the Logback appender is installed by the agent. For manual SDK usage,
   * configure the SDK using AutoConfiguredOpenTelemetrySdk or programmatic configuration
   * before calling this method.
   *
   * <p>Note: When using the Java agent, do NOT call OpenTelemetryAppender.install() manually
   * as it will conflict with the agent's automatic appender installation. The agent handles
   * both trace instrumentation and log correlation automatically.
   *
   * @param service is usually the name of the service
   */
  public static void init(String service) {
    if (!initialized) {
      synchronized (Tracing.class) {
        if (!initialized) {
          OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
          tracer = openTelemetry.getTracer(service, INSTRUMENTATION_VERSION);
          
          // Check if running with Java agent by checking if the agent has already initialized
          // the GlobalOpenTelemetry. If the agent is running, it handles appender installation.
          // Only install manually if not using the agent (for standalone SDK usage).
          String agentPresent = System.getProperty("otel.javaagent.enabled");
          if (agentPresent == null || !"true".equalsIgnoreCase(agentPresent)) {
            // No agent detected - install appender manually for standalone SDK usage
            // See: https://github.com/open-telemetry/opentelemetry-java-examples/tree/main/log-appender
            try {
              OpenTelemetryAppender.install(openTelemetry);
            } catch (IllegalStateException e) {
              // Appender already installed (possibly by agent) - this is fine
            }
          }
          // When using Java agent, the logback-mdc instrumentation automatically populates MDC
          // with trace_id and span_id, so no manual MDC management is needed.
          
          initialized = true;
        }
      }
    }
  }

  /**
   * Get the current tracer instance.
   *
   * @return the OpenTelemetry Tracer
   */
  public static Tracer getTracer() {
    if (!initialized) {
      // Fallback initialization if not already done
      init("teastore");
    }
    return tracer;
  }

  /**
   * This function is used to inject the current span context into the request to
   * be made.
   *
   * @param requestBuilder The requestBuilder object that gets injected
   */
  public static void inject(Invocation.Builder requestBuilder) {
    Span currentSpan = Span.current();
    if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
      GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
          .inject(Context.current(), requestBuilder, TEXT_MAP_SETTER);
    }
  }

  /**
   * Overloaded function used to extract span information out of an
   * HttpServletRequest instance.
   *
   * @param request is the HttpServletRequest instance with the potential span
   *                information
   * @return Scope containing the extracted span marked as active. Can be used
   *         with try-with-resource construct. The span is automatically ended when closed.
   */
  public static Scope extractCurrentSpan(HttpServletRequest request) {
    Map<String, String> headers = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      headers.put(headerName.toLowerCase(), request.getHeader(headerName));
    }
    return buildSpanFromHeaders(headers, request.getRequestURI());
  }

  /**
   * Overloaded function used to extract span information out of an HttpHeaders
   * instance.
   *
   * @param httpHeaders is the HttpHeaders instance with the potential span
   *                    information
   * @return Scope containing the extracted span marked as active. Can be used
   *         with try-with-resource construct. The span is automatically ended when closed.
   */
  public static Scope extractCurrentSpan(HttpHeaders httpHeaders) {
    Map<String, String> headers = new HashMap<>();
    for (String headerName : httpHeaders.getRequestHeaders().keySet()) {
      headers.put(headerName.toLowerCase(), httpHeaders.getRequestHeader(headerName).get(0));
    }
    return buildSpanFromHeaders(headers, "op");
  }

  /**
   * Helper method to extract and build the active span out of Map containing the
   * processed headers.
   *
   * @param headers is the Map of the processed headers
   * @param operationName is the operation name of the span (can be either URL or URI)
   * @return Scope containing the extracted span marked as active. Can be used
   *         with try-with-resource construct. The span is automatically ended when closed.
   */
  private static Scope buildSpanFromHeaders(Map<String, String> headers, String operationName) {
    // Extract context from incoming headers
    Context extractedContext = GlobalOpenTelemetry.getPropagators().getTextMapPropagator()
        .extract(Context.current(), headers, TEXT_MAP_GETTER);

    // Start a new span as child of extracted context
    Span span = getTracer().spanBuilder(operationName)
        .setParent(extractedContext)
        .setSpanKind(SpanKind.SERVER)
        .startSpan();

    // Make the span active and return a wrapper scope that ends the span when closed
    Scope scope = span.makeCurrent();
    return new SpanEndingScope(scope, span);
  }

  /**
   * Get the current trace ID from the active span.
   *
   * @return the trace ID or null if no active span
   */
  public static String getCurrentTraceId() {
    Span span = Span.current();
    if (span != null && span.getSpanContext().isValid()) {
      return span.getSpanContext().getTraceId();
    }
    return null;
  }

  /**
   * Get the current span ID from the active span.
   *
   * @return the span ID or null if no active span
   */
  public static String getCurrentSpanId() {
    Span span = Span.current();
    if (span != null && span.getSpanContext().isValid()) {
      return span.getSpanContext().getSpanId();
    }
    return null;
  }

  /**
   * A wrapper Scope that ends the span when closed.
   * This ensures spans are properly ended when used with try-with-resources.
   *
   * <p>This class is designed to be used in a single-threaded context within request processing.
   * The scope and span should only be closed once, and only by the thread that created them.
   *
   * <p>Usage example:
   * <pre>{@code
   * try (Scope scope = Tracing.extractCurrentSpan(request)) {
   *     // Process request - span is active here
   * }
   * // Scope is closed, span is ended
   * }</pre>
   */
  private static class SpanEndingScope implements Scope {
    private final Scope delegate;
    private final Span span;

    /**
     * Creates a new SpanEndingScope.
     *
     * @param delegate the underlying scope to delegate close() to (must not be null)
     * @param span the span to end when this scope is closed (must not be null)
     * @throws NullPointerException if delegate or span is null
     */
    SpanEndingScope(Scope delegate, Span span) {
      if (delegate == null) {
        throw new NullPointerException("delegate scope must not be null");
      }
      if (span == null) {
        throw new NullPointerException("span must not be null");
      }
      this.delegate = delegate;
      this.span = span;
    }

    @Override
    public void close() {
      try {
        delegate.close();
      } finally {
        span.end();
      }
    }
  }
}