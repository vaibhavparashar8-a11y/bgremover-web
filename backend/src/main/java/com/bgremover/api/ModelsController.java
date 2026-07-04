package com.bgremover.api;

import com.bgremover.api.dto.ActiveModelRequest;
import com.bgremover.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Model registry + health proxy; all logic lives in {@link ModelService}. */
@RestController
@RequestMapping("/api")
public class ModelsController {

  private final ModelService modelService;

  public ModelsController(ModelService modelService) {
    this.modelService = modelService;
  }

  @Operation(summary = "List registered models and the active default")
  @GetMapping("/models")
  public ResponseEntity<String> models() {
    return json(modelService.listModels());
  }

  @Operation(summary = "Switch the active model at runtime (no rebuild)")
  @PutMapping("/models/active")
  public ResponseEntity<String> setActive(@Valid @RequestBody ActiveModelRequest request) {
    return json(modelService.setActiveModel(request.name()));
  }

  @Operation(summary = "Health of the backend + inference service")
  @GetMapping("/health")
  public ResponseEntity<String> health() {
    return json(modelService.health());
  }

  private ResponseEntity<String> json(String body) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
  }
}
