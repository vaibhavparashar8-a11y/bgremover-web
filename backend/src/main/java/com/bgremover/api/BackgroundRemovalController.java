package com.bgremover.api;

import com.bgremover.client.RemovalOptions;
import com.bgremover.service.RemovalService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Background-removal endpoint; all logic lives in {@link RemovalService}. */
@RestController
@RequestMapping("/api")
public class BackgroundRemovalController {

  private final RemovalService removalService;

  public BackgroundRemovalController(RemovalService removalService) {
    this.removalService = removalService;
  }

  @Operation(
      summary = "Remove the background of an uploaded image",
      description =
          "Returns a transparent PNG at the original resolution. For the interactive 'sam' model,"
              + " pass 'points' as a JSON array of {x,y,label} points and/or {x1,y1,x2,y2} boxes."
              + " Set 'invert' to remove the selection instead of keeping it.")
  @PostMapping(value = "/remove", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> remove(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "model", required = false) String model,
      @RequestParam(value = "alphaMatting", defaultValue = "false") boolean alphaMatting,
      @RequestParam(value = "points", required = false) String points,
      @RequestParam(value = "invert", defaultValue = "false") boolean invert) {
    byte[] png =
        removalService.removeBackground(
            file, new RemovalOptions(model, alphaMatting, points, invert));
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header("Content-Disposition", "inline; filename=\"result.png\"")
        .body(png);
  }
}
