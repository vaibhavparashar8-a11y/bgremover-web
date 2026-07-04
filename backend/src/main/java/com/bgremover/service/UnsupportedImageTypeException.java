package com.bgremover.service;

/** The uploaded file's content type is not an accepted image format. */
public class UnsupportedImageTypeException extends RuntimeException {

  public UnsupportedImageTypeException(String contentType) {
    super("Unsupported file type '" + contentType + "'. Allowed: PNG, JPEG, WebP, BMP, TIFF.");
  }
}
