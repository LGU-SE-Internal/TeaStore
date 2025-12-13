package tools.descartes.teastore.registryclient.rest;

import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper for http responses.
 * This class is a pass-through now that Kieker monitoring has been removed.
 *
 * @author Simon
 *
 */
public final class ResponseWrapper {

  private static final Logger LOG = LoggerFactory.getLogger(ResponseWrapper.class);

  /**
   * Hide default constructor.
   */
  private ResponseWrapper() {

  }

  /**
   * Pass-through wrapper for responses.
   * Previously used for Kieker monitoring, now just returns the response as-is.
   *
   * @param response response
   * @return response response
   */
  public static Response wrap(Response response) {
    return response;
  }

}
