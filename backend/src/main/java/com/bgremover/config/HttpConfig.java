package com.bgremover.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** HTTP wiring: the inference RestClient and dev-time CORS. */
@Configuration
@EnableConfigurationProperties(InferenceProperties.class)
public class HttpConfig {

  @Bean
  public RestClient inferenceRestClient(InferenceProperties props) {
    // Long read timeout on purpose: a model's first use downloads its weights.
    var settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(Duration.ofSeconds(props.connectTimeoutSeconds()))
            .withReadTimeout(Duration.ofSeconds(props.readTimeoutSeconds()));
    return RestClient.builder()
        .baseUrl(props.baseUrl())
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .build();
  }

  @Bean
  public WebMvcConfigurer corsConfigurer() {
    // Vite dev server during frontend development
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE");
      }
    };
  }
}
