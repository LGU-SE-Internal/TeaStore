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
import org.slf4j.MDC;
import tools.descartes.teastore.registryclient.tracing.Tracing;

/**
 * Servlet filter for request tracking using OpenTelemetry.
 * Extracts trace context from incoming requests and sets up MDC for log correlation.
 *
 * @author Simon
 *
 */
public class TrackingFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(TrackingFilter.class);

  // MDC keys for log correlation
  private static final String MDC_TRACE_ID = "trace_id";
  private static final String MDC_SPAN_ID = "span_id";

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
   * @param request  request
   * @param response response
   * @param chain    filter chain
   * @throws IOException      ioException
   * @throws ServletException servletException
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try (Scope scope = Tracing.extractCurrentSpan((HttpServletRequest) request)) {
      // Set MDC for log correlation with OpenTelemetry trace context
      Span currentSpan = Span.current();
      if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
        MDC.put(MDC_TRACE_ID, currentSpan.getSpanContext().getTraceId());
        MDC.put(MDC_SPAN_ID, currentSpan.getSpanContext().getSpanId());
        
        if (LOG.isDebugEnabled()) {
          HttpServletRequest httpRequest = (HttpServletRequest) request;
          LOG.debug("Processing request: {} {} trace_id={} span_id={}",
              httpRequest.getMethod(),
              httpRequest.getRequestURI(),
              currentSpan.getSpanContext().getTraceId(),
              currentSpan.getSpanContext().getSpanId());
        }
      }

      try {
        chain.doFilter(request, response);
      } finally {
        // Clean up MDC
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
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
