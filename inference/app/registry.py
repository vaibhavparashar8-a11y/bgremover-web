"""Model registry: the only place that knows about concrete model backends.

Everything outside this module works with the :class:`BackgroundRemover`
interface and the :class:`ModelRegistry`; nothing else may import rembg
sessions directly. New model families are added here (or in a sibling module)
and registered via ``config.yaml`` — never referenced ad hoc elsewhere.
"""

import logging
import threading
from abc import ABC, abstractmethod
from collections import OrderedDict
from typing import Any

from PIL import ImageOps
from PIL.Image import Image as PILImage
from rembg import new_session, remove

from app.schemas import ModelInfo

log = logging.getLogger("inference.registry")


class UnknownModelError(KeyError):
    """Raised when a model name is not present in the registry."""


class BackgroundRemover(ABC):
    """Interface every model backend implements.

    Both methods are stateless with respect to the request: they accept one
    image and return one image, so a future video pipeline can stream frames
    through the same objects.
    """

    @abstractmethod
    def remove(
        self,
        image: PILImage,
        *,
        alpha_matting: bool = False,
        prompt: list[dict[str, Any]] | None = None,
    ) -> PILImage:
        """Return the RGBA cutout of ``image`` (foreground kept)."""

    @abstractmethod
    def mask(
        self,
        image: PILImage,
        *,
        prompt: list[dict[str, Any]] | None = None,
    ) -> PILImage:
        """Return the L-mode foreground mask of ``image``."""


class RembgRemover(BackgroundRemover):
    """A rembg-backed model; ``session_name`` is any valid rembg session."""

    def __init__(self, session_name: str) -> None:
        self.session_name = session_name
        self._session = new_session(session_name)

    def _kwargs(self, prompt: list[dict[str, Any]] | None) -> dict[str, Any]:
        return {"sam_prompt": prompt} if prompt else {}

    def remove(
        self,
        image: PILImage,
        *,
        alpha_matting: bool = False,
        prompt: list[dict[str, Any]] | None = None,
    ) -> PILImage:
        result = remove(
            image,
            session=self._session,
            alpha_matting=alpha_matting,
            alpha_matting_foreground_threshold=240,
            alpha_matting_background_threshold=10,
            alpha_matting_erode_size=10,
            **self._kwargs(prompt),
        )
        return result.convert("RGBA")

    def mask(
        self,
        image: PILImage,
        *,
        prompt: list[dict[str, Any]] | None = None,
    ) -> PILImage:
        result = remove(image, session=self._session, only_mask=True, **self._kwargs(prompt))
        return result.convert("L")


def invert_cutout(image: PILImage, mask: PILImage) -> PILImage:
    """Compose the *background* cutout: keep everything the mask excludes."""
    result = image.convert("RGBA")
    result.putalpha(ImageOps.invert(mask))
    return result


class ModelRegistry:
    """Thread-safe registry of configured models with LRU-loaded backends."""

    def __init__(self, config: dict[str, Any]) -> None:
        self._infos: dict[str, ModelInfo] = {m["name"]: ModelInfo(**m) for m in config["models"]}
        self._active: str = config["active_model"]
        if self._active not in self._infos:
            raise UnknownModelError(self._active)
        self._max_loaded: int = int(config.get("max_loaded_models", 2))
        self._loaded: OrderedDict[str, BackgroundRemover] = OrderedDict()
        self._lock = threading.Lock()

    @property
    def active(self) -> str:
        """Name of the model used when a request does not specify one."""
        return self._active

    @property
    def loaded(self) -> list[str]:
        """Names of models currently held in memory."""
        with self._lock:
            return list(self._loaded.keys())

    def infos(self) -> list[ModelInfo]:
        """Metadata for every registered model."""
        return list(self._infos.values())

    def info(self, name: str) -> ModelInfo:
        """Metadata for one model, raising :class:`UnknownModelError` if absent."""
        try:
            return self._infos[name]
        except KeyError as exc:
            raise UnknownModelError(name) from exc

    def set_active(self, name: str) -> None:
        """Switch the runtime default model (no rebuild, no restart)."""
        self.info(name)
        self._active = name
        log.info("Active model switched to '%s'", name)

    def get(self, name: str | None = None) -> BackgroundRemover:
        """Return a loaded backend for ``name`` (or the active model), lazily
        loading it and evicting the least-recently-used one beyond the cap."""
        name = name or self._active
        self.info(name)
        with self._lock:
            if name in self._loaded:
                self._loaded.move_to_end(name)
                return self._loaded[name]
        log.info("Loading model '%s' (first use may download weights)", name)
        remover = RembgRemover(name)
        with self._lock:
            self._loaded[name] = remover
            self._loaded.move_to_end(name)
            while len(self._loaded) > self._max_loaded:
                evicted, _ = self._loaded.popitem(last=False)
                log.info("Evicted model '%s' from memory", evicted)
        return remover
