package com.bgremover.service;

import com.bgremover.client.InferenceClient;
import com.bgremover.client.RemovalOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Validates uploads and orchestrates background removal via the inference service. */
@Service
public class RemovalService {

  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "image/bmp", "image/tiff");

  private final InferenceClient inferenceClient;

  public RemovalService(InferenceClient inferenceClient) {
    this.inferenceClient = inferenceClient;
  }

  /** Returns the transparent-PNG cutout for the uploaded image. */
  public byte[] removeBackground(MultipartFile file, RemovalOptions options) {
    if (file == null || file.isEmpty()) {
      throw new EmptyUploadException();
    }
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
      throw new UnsupportedImageTypeException(contentType);
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the uploaded file", e);
    }
    return inferenceClient.removeBackground(bytes, file.getOriginalFilename(), options);
  }
}
