"""FastAPI inference microservice: model loading + inference only.

Internal-only (bind to localhost). Stateless per request: image in, cutout or
mask out. No business logic, no persistent file management — this keeps every
endpoint reusable for a future per-frame video pipeline.
"""

import io
import json
import logging

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.responses import Response
from PIL import Image
from PIL.Image import Image as PILImage

from app import config  # noqa: F401  (must be first: sets model-cache env vars)
from app.registry import ModelRegistry, UnknownModelError, invert_cutout
from app.schemas import ActiveModelRequest, HealthResponse, ModelsResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("inference")

app = FastAPI(
    title="BGRemover Inference Service",
    description="Internal model-inference API. Not exposed beyond localhost.",
    version="1.0.0",
)
registry = ModelRegistry(config.load_config())


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    """Liveness + which models are active/loaded."""
    return HealthResponse(status="ok", active_model=registry.active, loaded_models=registry.loaded)


@app.get("/models", response_model=ModelsResponse)
def models() -> ModelsResponse:
    """List the model registry and the current runtime default."""
    return ModelsResponse(active=registry.active, models=registry.infos())


@app.put("/models/active", response_model=ModelsResponse)
def set_active_model(body: ActiveModelRequest) -> ModelsResponse:
    """Switch the runtime default model without a rebuild or restart."""
    try:
        registry.set_active(body.name)
    except UnknownModelError:
        raise HTTPException(status_code=404, detail=f"Unknown model '{body.name}'.") from None
    return ModelsResponse(active=registry.active, models=registry.infos())


def _decode_image(data: bytes) -> PILImage:
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
        return img
    except Exception:
        raise HTTPException(status_code=422, detail="File is not a decodable image.") from None


def _parse_prompt(points: str, width: int, height: int) -> list[dict]:
    """Parse a JSON list of clicks/boxes into a rembg sam_prompt.

    Entries are either ``{x, y, label}`` points (label 1 = include,
    0 = exclude) or ``{x1, y1, x2, y2}`` rectangles, in natural-image pixels.
    """
    try:
        parsed = json.loads(points)
        if not isinstance(parsed, list):
            raise ValueError
    except (json.JSONDecodeError, ValueError):
        raise HTTPException(
            status_code=422,
            detail="'points' must be a JSON array of {x, y, label} or {x1, y1, x2, y2}.",
        ) from None
    prompt: list[dict] = []
    for p in parsed:
        if isinstance(p, dict) and "x1" in p:
            try:
                x1, y1, x2, y2 = int(p["x1"]), int(p["y1"]), int(p["x2"]), int(p["y2"])
            except (KeyError, TypeError, ValueError):
                raise HTTPException(status_code=422, detail=f"Bad rectangle entry: {p!r}") from None
            x1, x2 = sorted((max(0, x1), min(width - 1, x2)))
            y1, y2 = sorted((max(0, y1), min(height - 1, y2)))
            prompt.append({"type": "rectangle", "data": [x1, y1, x2, y2]})
            continue
        try:
            x, y, label = int(p["x"]), int(p["y"]), int(p.get("label", 1))
        except (KeyError, TypeError, ValueError):
            raise HTTPException(status_code=422, detail=f"Bad point entry: {p!r}") from None
        if not (0 <= x < width and 0 <= y < height):
            raise HTTPException(
                status_code=422, detail=f"Point ({x}, {y}) outside image {width}x{height}."
            )
        prompt.append({"type": "point", "data": [x, y], "label": label})
    return prompt


@app.post("/remove")
async def remove_background(
    file: UploadFile = File(...),
    model: str | None = Form(default=None),
    alpha_matting: bool = Form(default=False),
    points: str | None = Form(default=None),
    invert: bool = Form(default=False),
) -> Response:
    """Cut the foreground (or, with ``invert``, the background) out of an image.

    Args:
        file: The source image (PNG/JPEG/WebP/BMP/TIFF).
        model: Registry model name; defaults to the active model.
        alpha_matting: Edge refinement for hair/fur (ignored when ``invert``).
        points: SAM prompt JSON — points ``{x, y, label}`` and/or boxes
            ``{x1, y1, x2, y2}``. Only valid with an interactive model.
        invert: Keep what the mask excludes ("remove what I selected").

    Returns:
        A transparent PNG at the original resolution.
    """
    name = model or registry.active
    try:
        info = registry.info(name)
    except UnknownModelError:
        raise HTTPException(
            status_code=422, detail=f"Unknown model '{name}'. See GET /models."
        ) from None

    src = _decode_image(await file.read())

    prompt = None
    if points:
        if not info.interactive:
            raise HTTPException(
                status_code=422, detail="'points' is only supported with model 'sam'."
            )
        prompt = _parse_prompt(points, src.width, src.height) or None

    try:
        remover = registry.get(name)
    except Exception as exc:
        log.exception("Failed to load model '%s'", name)
        raise HTTPException(
            status_code=503, detail=f"Model '{name}' failed to load: {exc}"
        ) from exc

    try:
        if invert:
            result = invert_cutout(src, remover.mask(src, prompt=prompt))
        else:
            result = remover.remove(src, alpha_matting=alpha_matting, prompt=prompt)
    except Exception as exc:
        log.exception("Inference failed (model=%s)", name)
        raise HTTPException(status_code=500, detail=f"Inference failed: {exc}") from exc

    buf = io.BytesIO()
    result.save(buf, format="PNG")
    return Response(
        content=buf.getvalue(),
        media_type="image/png",
        headers={"X-Model-Used": name},
    )
