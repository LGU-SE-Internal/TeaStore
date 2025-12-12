package tools.descartes.teastore.registryclient.rest;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import kieker.common.record.controlflow.OperationExecutionRecord;
import kieker.monitoring.core.controller.IMonitoringController;
import kieker.monitoring.core.controller.MonitoringController;
import kieker.monitoring.core.registry.ControlFlowRegistry;
import kieker.monitoring.core.registry.SessionRegistry;
import tools.descartes.teastore.registryclient.tracing.Tracing;

/**
 * Servlet filter for request tracking.
 * Supports both OpenTelemetry tracing and Kieker monitoring.
 *
 * @author Simon
 *
 */
public class TrackingFilter implements Filter {

  private static final Logger LOG = LoggerFactory.getLogger(TrackingFilter.class);

  private static final IMonitoringController CTRLINST = MonitoringController.getInstance();
  private static final String SESSION_ID_ASYNC_TRACE = "NOSESSION-ASYNCIN";
  private static final ControlFlowRegistry CF_REGISTRY = ControlFlowRegistry.INSTANCE;
  private static final SessionRegistry SESSION_REGISTRY = SessionRegistry.INSTANCE;
  private static final String HEADER_FIELD = "KiekerTracingInfo";

  // MDC keys for log correlation
  private static final String MDC_TRACE_ID = "trace_id";
  private static final String MDC_SPAN_ID = "span_id";

  /**
   * empty initialization method.
   *
   * @param filterConfig configuration of filter
   * @throws ServletException servletException
   */
  public void init(FilterConfig filterConfig) throws ServletException {

  }

  /**
   * Filter method that appends tracking id and sets up OpenTelemetry context.
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
      }

      try {
        if (!CTRLINST.isMonitoringEnabled()) {
          chain.doFilter(request, response);
          return;
        }
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
          String url = ((HttpServletRequest) request).getRequestURL().toString();
          if (url.contains("webui")) {
            chain.doFilter(request, response);
            return;
          }
          HttpServletRequest req = (HttpServletRequest) request;
          String sessionId = SESSION_REGISTRY.recallThreadLocalSessionId();
          long traceId = -1L;
          int eoi;
          int ess;

          final String operationExecutionHeader = req.getHeader(HEADER_FIELD);

          if ((operationExecutionHeader == null) || (operationExecutionHeader.equals(""))) {
            LOG.debug("No monitoring data found in the incoming request header");
            traceId = CF_REGISTRY.getAndStoreUniqueThreadLocalTraceId();
            CF_REGISTRY.storeThreadLocalEOI(0);
            // ESS is set to 1 because the next operation will be ess + 1
            CF_REGISTRY.storeThreadLocalESS(1);
            eoi = 0;
            ess = 0;
          } else {
            if (LOG.isDebugEnabled()) {
              LOG.debug("Received request: " + req.getMethod() + "with header = " + operationExecutionHeader);
            }
            final String[] headerArray = operationExecutionHeader.split(",");

            sessionId = headerArray[1];
            if ("null".equals(sessionId)) {
              sessionId = OperationExecutionRecord.NO_SESSION_ID;
            }

            final String eoiStr = headerArray[2];
            eoi = -1;
            try {
              eoi = Integer.parseInt(eoiStr);
            } catch (final NumberFormatException exc) {
              LOG.warn("Invalid eoi", exc);
            }

            final String essStr = headerArray[3];
            ess = -1;
            try {
              ess = Integer.parseInt(essStr);
            } catch (final NumberFormatException exc) {
              LOG.warn("Invalid ess", exc);
            }

            final String traceIdStr = headerArray[0];
            if (traceIdStr != null) {
              try {
                traceId = Long.parseLong(traceIdStr);
              } catch (final NumberFormatException exc) {
                LOG.warn("Invalid trace id", exc);
              }
            } else {
              traceId = CF_REGISTRY.getUniqueTraceId();
              sessionId = SESSION_ID_ASYNC_TRACE;
              eoi = 0;
              ess = 0;
            }

            CF_REGISTRY.storeThreadLocalTraceId(traceId);
            CF_REGISTRY.storeThreadLocalEOI(eoi);
            CF_REGISTRY.storeThreadLocalESS(ess);
            SESSION_REGISTRY.storeThreadLocalSessionId(sessionId);
          }

        } else {
          LOG.error("Something went wrong");
        }
        CharResponseWrapper wrappedResponse = new CharResponseWrapper((HttpServletResponse) response);
        PrintWriter out = response.getWriter();

        chain.doFilter(request, wrappedResponse);

        String sessionId = SESSION_REGISTRY.recallThreadLocalSessionId();
        long traceId = CF_REGISTRY.recallThreadLocalTraceId();
        int eoi = CF_REGISTRY.recallThreadLocalEOI();
        wrappedResponse.addHeader(HEADER_FIELD,
            traceId + "," + sessionId + "," + (eoi) + "," + Integer.toString(CF_REGISTRY.recallThreadLocalESS()));
        out.write(wrappedResponse.toString());
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
    CF_REGISTRY.unsetThreadLocalTraceId();
    CF_REGISTRY.unsetThreadLocalEOI();
    CF_REGISTRY.unsetThreadLocalESS();
  }
}
