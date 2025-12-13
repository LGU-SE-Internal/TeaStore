package tools.descartes.teastore.registryclient.rest;

import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.descartes.teastore.registryclient.tracing.Tracing;

import jakarta.ws.rs.client.WebTarget;

/**
 * Wrapper for http calls.
 * Injects OpenTelemetry trace context into outgoing requests.
 *
 * @author Simon
 *
 */
public final class HttpWrapper {
  private static final Logger LOG = LoggerFactory.getLogger(HttpWrapper.class);

  /**
   * Hide default constructor.
   */
  private HttpWrapper() {

  }

  /**
   * Wrap webtarget with OpenTelemetry trace context injection.
   *
   * @param target webtarget to wrap
   * @return wrapped webtarget with trace context headers
   */
  public static Builder wrap(WebTarget target) {
    Builder builder = target.request(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON);
    Tracing.inject(builder);
    return builder;
  }
}
