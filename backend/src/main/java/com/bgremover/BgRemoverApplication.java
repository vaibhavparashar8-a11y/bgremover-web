package com.bgremover;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BgRemoverApplication {

  public static void main(String[] args) {
    ensureTempDir();
    SpringApplication.run(BgRemoverApplication.class, args);
  }

  /** The multipart temp dir (E:-drive policy) must exist before Tomcat accepts uploads. */
  private static void ensureTempDir() {
    String dir = System.getenv().getOrDefault("BGR_TMP_DIR", "E:/Projects/BGRemover/data/tmp");
    try {
      Files.createDirectories(Path.of(dir));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create multipart temp dir " + dir, e);
    }
  }
}
