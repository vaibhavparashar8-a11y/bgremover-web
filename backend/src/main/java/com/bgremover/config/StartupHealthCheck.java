package com.bgremover.config;

import com.bgremover.client.InferenceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Polls the inference service once at startup so a misconfigured stack is visible immediately. The
 * backend still starts when inference is down (requests then return 503 with a clear message).
 */
@Component
public class StartupHealthCheck implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(StartupHealthCheck.class);

  private final InferenceClient inferenceClient;

  public StartupHealthCheck(InferenceClient inferenceClient) {
    this.inferenceClient = inferenceClient;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      log.info("Inference service is up: {}", inferenceClient.health());
    } catch (RuntimeException e) {
      log.warn(
          "Inference service is NOT reachable at startup ({}). The UI will show a clear error"
              + " until it is started (start-inference.cmd).",
          e.getMessage());
    }
  }
}
