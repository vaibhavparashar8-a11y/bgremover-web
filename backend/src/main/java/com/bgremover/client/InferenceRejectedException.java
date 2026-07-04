package com.bgremover.client;

/** The inference service answered with an error status; its JSON body is preserved. */
public class InferenceRejectedException extends RuntimeException {

  private final int status;
  private final String body;

  public InferenceRejectedException(int status, String body) {
    super("Inference service rejected the request with status " + status);
    this.status = status;
    this.body = body;
  }

  public int status() {
    return status;
  }

  public String body() {
    return body;
  }
}
