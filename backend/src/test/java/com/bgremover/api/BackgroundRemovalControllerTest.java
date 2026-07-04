package com.bgremover.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bgremover.client.InferenceUnavailableException;
import com.bgremover.service.RemovalService;
import com.bgremover.service.UnsupportedImageTypeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BackgroundRemovalController.class)
class BackgroundRemovalControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private RemovalService removalService;

  private final MockMultipartFile file =
      new MockMultipartFile("file", "cat.jpg", "image/jpeg", new byte[] {1, 2, 3});

  @Test
  void returnsPngOnSuccess() throws Exception {
    when(removalService.removeBackground(any(), any())).thenReturn(new byte[] {9});

    mockMvc
        .perform(multipart("/api/remove").file(file))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG));
  }

  @Test
  void mapsUnsupportedTypeTo415() throws Exception {
    when(removalService.removeBackground(any(), any()))
        .thenThrow(new UnsupportedImageTypeException("application/pdf"));

    mockMvc
        .perform(multipart("/api/remove").file(file))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void mapsInferenceDownTo503() throws Exception {
    when(removalService.removeBackground(any(), any()))
        .thenThrow(new InferenceUnavailableException(new RuntimeException("refused")));

    mockMvc.perform(multipart("/api/remove").file(file)).andExpect(status().isServiceUnavailable());
  }

  @Test
  void missingFilePartIs400() throws Exception {
    mockMvc.perform(multipart("/api/remove")).andExpect(status().isBadRequest());
  }
}
