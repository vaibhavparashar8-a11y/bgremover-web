package com.bgremover.client;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** {@link InferenceClient} backed by Spring's {@link RestClient} over localhost HTTP. */
@Component
public class RestInferenceClient implements InferenceClient {

  private final RestClient restClient;

  public RestInferenceClient(RestClient inferenceRestClient) {
    this.restClient = inferenceRestClient;
  }

  @Override
  public byte[] removeBackground(byte[] image, String filename, RemovalOptions options) {
    var fileResource =
        new ByteArrayResource(image) {
          @Override
          public String getFilename() {
            return (filename == null || filename.isBlank()) ? "upload" : filename;
          }
        };

    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("file", fileResource);
    if (options.model() != null && !options.model().isBlank()) {
      form.add("model", options.model());
    }
    form.add("alpha_matting", String.valueOf(options.alphaMatting()));
    form.add("invert", String.valueOf(options.invert()));
    if (options.points() != null && !options.points().isBlank()) {
      form.add("points", options.points());
    }

    return execute(
        () ->
            restClient
                .post()
                .uri("/remove")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .body(byte[].class));
  }

  @Override
  public String listModels() {
    return execute(() -> restClient.get().uri("/models").retrieve().body(String.class));
  }

  @Override
  public String setActiveModel(String name) {
    return execute(
        () ->
            restClient
                .put()
                .uri("/models/active")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"" + name.replace("\"", "") + "\"}")
                .retrieve()
                .body(String.class));
  }

  @Override
  public String health() {
    return execute(() -> restClient.get().uri("/health").retrieve().body(String.class));
  }

  private <T> T execute(java.util.function.Supplier<T> call) {
    try {
      return call.get();
    } catch (RestClientResponseException e) {
      throw new InferenceRejectedException(e.getStatusCode().value(), e.getResponseBodyAsString());
    } catch (ResourceAccessException e) {
      throw new InferenceUnavailableException(e);
    }
  }
}
