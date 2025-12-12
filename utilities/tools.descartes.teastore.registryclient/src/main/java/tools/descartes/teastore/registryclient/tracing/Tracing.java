package tools.descartes.teastore.registryclient.tracing;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.HttpHeaders;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

/**
 * Utility functions for OpenTelemetry integration.
 * Migrated from OpenTracing/Jaeger to OpenTelemetry SDK.
 *
 * @author Long Bui
 */
public final class Tracing {

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
   * When using OpenTelemetry auto-instrumentation or SDK configuration via environment variables,
   * this method uses GlobalOpenTelemetry to get the tracer.
   *
   * @param service is usually the name of the service
   */
  public static void init(String service) {
    if (!initialized) {
      synchronized (Tracing.class) {
        if (!initialized) {
          tracer = GlobalOpenTelemetry.getTracer(service, "1.0.0");
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
   *         with try-with-resource construct
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
   *         with try-with-resource construct
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
   *         with try-with-resource construct
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

    // Make the span active and return the scope
    return span.makeCurrent();
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
}