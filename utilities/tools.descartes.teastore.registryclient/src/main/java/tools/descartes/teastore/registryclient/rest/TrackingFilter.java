package tools.descartes.teastore.registryclient.rest;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.descartes.teastore.registryclient.tracing.Tracing;

/**
 * Servlet filter that emits one request-path log per request so the OpenTelemetry
 * Java agent stamps it with the active span's TraceId. When no agent span is
 * present on the thread it falls back to extracting/creating a span itself.
 *
 * @author Simon
 *
 */
public class TrackingFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(TrackingFilter.class);

  /**
   * Empty initialization method.
   *
   * @param filterConfig configuration of filter
   * @throws ServletException servletException
   */
  public void init(FilterConfig filterConfig) throws ServletException {
    // No initialization needed
  }

  /**
   * Filter method that sets up OpenTelemetry context and MDC for log correlation.
   * 
   * <p>When using the OpenTelemetry Java agent (auto-instrumentation), the agent
   * automatically instruments HTTP requests and populates MDC with trace_id and span_id.
   * In this case, the filter only ensures MDC is accessible.
   * 
   * <p>When NOT using the agent, this filter manually extracts trace context and
   * creates spans for distributed tracing.
   *
   * @param request  request
   * @param response response
   * @param chain    filter chain
   * @throws IOException      ioException
   * @throws ServletException servletException
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    // Detect the agent by whether it has already made a valid server span current.
    // (System.getProperty("otel.javaagent.enabled") is NOT set by the agent at
    // runtime — it returns null even when the agent is attached — so the old
    // property-based check always fell into the manual branch.)
    Scope customScope = null;
    if (!Span.current().getSpanContext().isValid()) {
      // No agent span on this thread: extract/create one ourselves so the request
      // still carries trace context.
      customScope = Tracing.extractCurrentSpan((HttpServletRequest) request);
    }

    try {
      // One INFO log per request, on the request thread inside the active server
      // span, so the agent's logback instrumentation stamps it with the live
      // TraceId and ships it via OTLP. auth/image/persistence/recommender/webui
      // have little or no request-path logging of their own; this is the log that
      // carries correlation for the abnormal-window logs parquet.
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      LOG.info("{} {}", httpRequest.getMethod(), httpRequest.getRequestURI());

      chain.doFilter(request, response);

    } finally {
      if (customScope != null) {
        customScope.close();
      }
    }
  }

  /**
   * Teardown method.
   */
  public void destroy() {
    // No cleanup needed
  }
}
