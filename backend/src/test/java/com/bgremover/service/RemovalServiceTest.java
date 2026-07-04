package com.bgremover.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bgremover.client.InferenceClient;
import com.bgremover.client.RemovalOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class RemovalServiceTest {

  @Mock private InferenceClient inferenceClient;
  @InjectMocks private RemovalService removalService;

  private final RemovalOptions options = new RemovalOptions(null, false, null, false);

  @Test
  void forwardsValidUploadAndReturnsPng() {
    var file = new MockMultipartFile("file", "cat.jpg", "image/jpeg", new byte[] {1, 2, 3});
    when(inferenceClient.removeBackground(any(), eq("cat.jpg"), eq(options)))
        .thenReturn(new byte[] {9});

    byte[] result = removalService.removeBackground(file, options);

    assertThat(result).containsExactly(9);
    verify(inferenceClient).removeBackground(new byte[] {1, 2, 3}, "cat.jpg", options);
  }

  @Test
  void rejectsEmptyUpload() {
    var file = new MockMultipartFile("file", "cat.jpg", "image/jpeg", new byte[0]);

    assertThatThrownBy(() -> removalService.removeBackground(file, options))
        .isInstanceOf(EmptyUploadException.class);
    verifyNoInteractions(inferenceClient);
  }

  @Test
  void rejectsUnsupportedContentType() {
    var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] {1});

    assertThatThrownBy(() -> removalService.removeBackground(file, options))
        .isInstanceOf(UnsupportedImageTypeException.class)
        .hasMessageContaining("application/pdf");
    verifyNoInteractions(inferenceClient);
  }
}
