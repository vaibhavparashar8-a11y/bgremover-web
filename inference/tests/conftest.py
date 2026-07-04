"""Shared fixtures: a TestClient and small real sample images."""

import io

import pytest
from fastapi.testclient import TestClient
from PIL import Image, ImageDraw

from app.main import app


@pytest.fixture(scope="session")
def client() -> TestClient:
    return TestClient(app)


def make_image(width: int = 96, height: int = 96) -> bytes:
    """A small real image: yellow disc on blue background."""
    img = Image.new("RGB", (width, height), (30, 144, 255))
    draw = ImageDraw.Draw(img)
    draw.ellipse([width // 4, height // 4, 3 * width // 4, 3 * height // 4], fill=(255, 200, 60))
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


@pytest.fixture(scope="session")
def sample_png() -> bytes:
    return make_image()
