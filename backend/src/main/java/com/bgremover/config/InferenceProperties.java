package com.bgremover.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings for the Python inference service. */
@ConfigurationProperties(prefix = "bgremover.inference")
public record InferenceProperties(
    String baseUrl, int readTimeoutSeconds, int connectTimeoutSeconds) {}
