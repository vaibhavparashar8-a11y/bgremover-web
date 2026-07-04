package com.bgremover.client;

/**
 * Options forwarded to the inference service.
 *
 * @param model registry model name; null uses the service's active model
 * @param alphaMatting edge refinement for hair/fur
 * @param points SAM prompt JSON (points and/or boxes); only valid for interactive models
 * @param invert keep what the mask excludes ("remove what I selected")
 */
public record RemovalOptions(String model, boolean alphaMatting, String points, boolean invert) {}
