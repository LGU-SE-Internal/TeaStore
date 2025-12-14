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
    
    // Check if we have an active span - if so, the agent is likely handling instrumentation
    Span currentSpan = Span.current();
    boolean agentActive = currentSpan != null && currentSpan.getSpanContext().isValid();
    Scope customScope = null;
    boolean mdcSetByUs = false;
    
    try {
      if (!agentActive) {
        // No active span from agent - manually extract context and create span
        // This is the fallback for when agent is not used
        customScope = Tracing.extractCurrentSpan((HttpServletRequest) request);
        currentSpan = Span.current();
        
        // Set MDC for log correlation
        if (currentSpan != null && currentSpan.getSpanContext().isValid()) {
          MDC.put(MDC_TRACE_ID, currentSpan.getSpanContext().getTraceId());
          MDC.put(MDC_SPAN_ID, currentSpan.getSpanContext().getSpanId());
          mdcSetByUs = true;
          
          if (LOG.isDebugEnabled()) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            LOG.debug("Manual tracing - Processing request: {} {} trace_id={} span_id={}",
                httpRequest.getMethod(),
                httpRequest.getRequestURI(),
                currentSpan.getSpanContext().getTraceId(),
                currentSpan.getSpanContext().getSpanId());
          }
        }
      } else {
        // Agent is active - MDC should already be populated by agent's logback-mdc instrumentation
        // Just verify and log for debugging
        if (LOG.isDebugEnabled()) {
          String traceId = MDC.get(MDC_TRACE_ID);
          String spanId = MDC.get(MDC_SPAN_ID);
          HttpServletRequest httpRequest = (HttpServletRequest) request;
          
          // Use MDC values if available, otherwise get from current span
          if (traceId == null && currentSpan != null) {
            traceId = currentSpan.getSpanContext().getTraceId();
          }
          if (spanId == null && currentSpan != null) {
            spanId = currentSpan.getSpanContext().getSpanId();
          }
          
          LOG.debug("Agent tracing - Processing request: {} {} trace_id={} span_id={}",
              httpRequest.getMethod(),
              httpRequest.getRequestURI(),
              traceId,
              spanId);
        }
      }

      chain.doFilter(request, response);
      
    } finally {
      // Only clean up MDC if we set it ourselves
      if (mdcSetByUs) {
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
