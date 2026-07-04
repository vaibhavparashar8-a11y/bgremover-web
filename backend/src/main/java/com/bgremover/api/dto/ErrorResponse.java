package com.bgremover.api.dto;

/** Uniform error body: mirrors the inference service's {@code {"detail": ...}} shape. */
public record ErrorResponse(String detail) {}
