package com.bgremover.client;

/**
 * Boundary to the Python inference microservice. The only place in the Java codebase that talks to
 * it; everything else depends on this interface so the service can be mocked in tests.
 */
public interface InferenceClient {

  /** Sends an image for background removal and returns the resulting PNG bytes. */
  byte[] removeBackground(byte[] image, String filename, RemovalOptions options);

  /** Returns the raw JSON of the inference service's model registry. */
  String listModels();

  /** Switches the inference service's active model; returns the updated registry JSON. */
  String setActiveModel(String name);

  /** Returns the raw JSON of the inference service's health endpoint. */
  String health();
}
