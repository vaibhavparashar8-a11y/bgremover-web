package com.bgremover.service;

import com.bgremover.client.InferenceClient;
import org.springframework.stereotype.Service;

/** Exposes the inference service's model registry to the API layer. */
@Service
public class ModelService {

  private final InferenceClient inferenceClient;

  public ModelService(InferenceClient inferenceClient) {
    this.inferenceClient = inferenceClient;
  }

  public String listModels() {
    return inferenceClient.listModels();
  }

  public String setActiveModel(String name) {
    return inferenceClient.setActiveModel(name);
  }

  public String health() {
    return inferenceClient.health();
  }
}
