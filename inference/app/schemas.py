"""Pydantic schemas for the inference-service API contract."""

from pydantic import BaseModel, Field


class ModelInfo(BaseModel):
    """Metadata for one registered model."""

    name: str
    label: str
    quality: str
    speed: str
    size_mb: int
    interactive: bool = False


class ModelsResponse(BaseModel):
    """Registry listing returned by ``GET /models``."""

    active: str
    models: list[ModelInfo]


class ActiveModelRequest(BaseModel):
    """Body of ``PUT /models/active``."""

    name: str = Field(min_length=1)


class HealthResponse(BaseModel):
    """Response of ``GET /health``."""

    status: str
    active_model: str
    loaded_models: list[str]


class PromptPoint(BaseModel):
    """A single include/exclude click, in natural-image pixels."""

    x: int
    y: int
    label: int = Field(default=1, ge=0, le=1)


class PromptBox(BaseModel):
    """A drag-selected rectangle, in natural-image pixels."""

    x1: int
    y1: int
    x2: int
    y2: int
