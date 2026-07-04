package com.bgremover.service;

/** The request contained no file or an empty file. */
public class EmptyUploadException extends RuntimeException {

  public EmptyUploadException() {
    super("No file uploaded.");
  }
}
