package com.bgremover.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code PUT /api/models/active}. */
public record ActiveModelRequest(@NotBlank String name) {}
