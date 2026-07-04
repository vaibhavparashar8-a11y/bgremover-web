package com.bgremover.api;

import com.bgremover.api.dto.ErrorResponse;
import com.bgremover.client.InferenceRejectedException;
import com.bgremover.client.InferenceUnavailableException;
import com.bgremover.service.EmptyUploadException;
import com.bgremover.service.UnsupportedImageTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Maps domain and client exceptions to proper HTTP statuses with a uniform error body. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(EmptyUploadException.class)
  public ResponseEntity<ErrorResponse> emptyUpload(EmptyUploadException e) {
    return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(MissingServletRequestPartException.class)
  public ResponseEntity<ErrorResponse> missingPart(MissingServletRequestPartException e) {
    return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(UnsupportedImageTypeException.class)
  public ResponseEntity<ErrorResponse> unsupportedType(UnsupportedImageTypeException e) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(new ErrorResponse("File exceeds the upload size limit."));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + " " + f.getDefaultMessage())
            .findFirst()
            .orElse("Invalid request body.");
    return ResponseEntity.badRequest().body(new ErrorResponse(detail));
  }

  @ExceptionHandler(InferenceUnavailableException.class)
  public ResponseEntity<ErrorResponse> inferenceDown(InferenceUnavailableException e) {
    log.error("Inference service unreachable", e);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse(e.getMessage()));
  }

  @ExceptionHandler(InferenceRejectedException.class)
  public ResponseEntity<String> inferenceRejected(InferenceRejectedException e) {
    log.warn("Inference service rejected request: {} {}", e.status(), e.body());
    return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).body(e.body());
  }
}
