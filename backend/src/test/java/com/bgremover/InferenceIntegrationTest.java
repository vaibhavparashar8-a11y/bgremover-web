package com.bgremover;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** Full-stack test against a WireMock stub of the inference service. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InferenceIntegrationTest {

  private static final WireMockServer wiremock =
      new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @Autowired private TestRestTemplate rest;

  @BeforeAll
  static void startStub() {
    wiremock.start();
  }

  @AfterAll
  static void stopStub() {
    wiremock.stop();
  }

  @DynamicPropertySource
  static void inferenceUrl(DynamicPropertyRegistry registry) {
    wiremock.start();
    registry.add("bgremover.inference.base-url", wiremock::baseUrl);
  }

  private HttpEntity<MultiValueMap<String, Object>> multipart() {
    var file =
        new ByteArrayResource(new byte[] {1, 2, 3}) {
          @Override
          public String getFilename() {
            return "cat.jpg";
          }
        };
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("file", new HttpEntity<>(file, imageHeaders()));
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);
    return new HttpEntity<>(form, headers);
  }

  private HttpHeaders imageHeaders() {
    HttpHeaders partHeaders = new HttpHeaders();
    partHeaders.setContentType(MediaType.IMAGE_JPEG);
    return partHeaders;
  }

  @Test
  void removeProxiesToInferenceAndReturnsPng() {
    byte[] fakePng = {(byte) 0x89, 'P', 'N', 'G'};
    wiremock.stubFor(
        post(urlEqualTo("/remove"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "image/png")
                    .withBody(fakePng)));

    ResponseEntity<byte[]> response = rest.postForEntity("/api/remove", multipart(), byte[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
    assertThat(response.getBody()).isEqualTo(fakePng);
  }

  @Test
  void inferenceErrorBodyIsPassedThrough() {
    wiremock.stubFor(
        post(urlEqualTo("/remove"))
            .willReturn(
                aResponse()
                    .withStatus(422)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"detail\":\"Unknown model 'x'\"}")));

    ResponseEntity<String> response = rest.postForEntity("/api/remove", multipart(), String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).contains("Unknown model");
  }

  @Test
  void modelsEndpointProxiesRegistry() {
    wiremock.stubFor(
        get(urlEqualTo("/models"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"active\":\"u2net\",\"models\":[]}")));

    ResponseEntity<String> response = rest.getForEntity("/api/models", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("u2net");
  }
}
