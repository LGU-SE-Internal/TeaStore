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
   * <p>When using the OpenTelemetry Java agent (auto-instrumentation), the agent
   * automatically instruments HTTP requests and populates MDC with trace_id and span_id.
   * This filter ensures MDC is set even when the agent isn't used, or provides
   * additional context extraction if needed.
   *
   * @param request  request
   * @param response response
   * @param chain    filter chain
   * @throws IOException      ioException
   * @throws ServletException servletException
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    
    // Check if MDC is already populated by the Java agent's logback-mdc instrumentation
    boolean mdcAlreadySet = MDC.get(MDC_TRACE_ID) != null;
    Scope customScope = null;
    
    try {
      // If MDC is not already set, we're not using the agent's auto-instrumentation
      // In this case, manually extract context and set MDC
      if (!mdcAlreadySet) {
        customScope = Tracing.extractCurrentSpan((HttpServletRequest) request);
        
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
      } else if (LOG.isDebugEnabled()) {
        // MDC already set by agent - just log for debugging
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        LOG.debug("Processing request with agent instrumentation: {} {} trace_id={} span_id={}",
            httpRequest.getMethod(),
            httpRequest.getRequestURI(),
            MDC.get(MDC_TRACE_ID),
            MDC.get(MDC_SPAN_ID));
      }

      chain.doFilter(request, response);
      
    } finally {
      // Only clean up MDC if we set it ourselves (not set by agent)
      if (!mdcAlreadySet) {
        MDC.remove(MDC_TRACE_ID);
        MDC.remove(MDC_SPAN_ID);
      }
      
      // Close custom scope if we created one
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
