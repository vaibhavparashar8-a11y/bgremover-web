package com.bgremover.client;

/** The inference service could not be reached (down, timeout, connection refused). */
public class InferenceUnavailableException extends RuntimeException {

  public InferenceUnavailableException(Throwable cause) {
    super("Inference service is not reachable. Is it running on port 8000?", cause);
  }
}
