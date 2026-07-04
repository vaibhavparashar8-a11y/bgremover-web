"""API tests. Inference tests use models whose weights are cached on E:."""

import json

from conftest import make_image


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "ok"
    assert body["active_model"]


def test_models_lists_registry(client):
    r = client.get("/models")
    assert r.status_code == 200
    body = r.json()
    names = [m["name"] for m in body["models"]]
    assert "u2net" in names
    assert "isnet-general-use" in names
    assert "sam" in names
    assert body["active"] in names


def test_set_active_model_roundtrip(client):
    original = client.get("/models").json()["active"]
    r = client.put("/models/active", json={"name": "u2netp"})
    assert r.status_code == 200
    assert r.json()["active"] == "u2netp"
    # restore
    assert client.put("/models/active", json={"name": original}).status_code == 200


def test_set_active_model_unknown_404(client):
    assert client.put("/models/active", json={"name": "no-such-model"}).status_code == 404


def test_remove_rejects_non_image(client):
    r = client.post("/remove", files={"file": ("x.png", b"not an image", "image/png")})
    assert r.status_code == 422


def test_remove_rejects_unknown_model(client, sample_png):
    r = client.post(
        "/remove",
        files={"file": ("s.png", sample_png, "image/png")},
        data={"model": "no-such-model"},
    )
    assert r.status_code == 422


def test_remove_rejects_points_on_non_interactive_model(client, sample_png):
    r = client.post(
        "/remove",
        files={"file": ("s.png", sample_png, "image/png")},
        data={"model": "isnet-general-use", "points": json.dumps([{"x": 1, "y": 1}])},
    )
    assert r.status_code == 422


def test_remove_rejects_malformed_points(client, sample_png):
    r = client.post(
        "/remove",
        files={"file": ("s.png", sample_png, "image/png")},
        data={"model": "sam", "points": "{not json"},
    )
    assert r.status_code == 422


def test_remove_isnet_produces_transparent_png(client, sample_png):
    """Real inference on a small image (isnet weights are cached on E:)."""
    r = client.post(
        "/remove",
        files={"file": ("s.png", sample_png, "image/png")},
        data={"model": "isnet-general-use"},
    )
    assert r.status_code == 200
    assert r.headers["content-type"] == "image/png"
    assert r.headers["x-model-used"] == "isnet-general-use"

    import io

    from PIL import Image

    img = Image.open(io.BytesIO(r.content))
    assert img.mode == "RGBA"
    assert img.size == (96, 96)
    lo, hi = img.getchannel("A").getextrema()
    assert lo == 0 and hi == 255  # transparency actually happened


def test_remove_invert_keeps_background(client):
    """Inverted removal keeps the background and cuts the subject out."""
    png = make_image(96, 96)
    normal = client.post(
        "/remove",
        files={"file": ("s.png", png, "image/png")},
        data={"model": "isnet-general-use"},
    )
    inverted = client.post(
        "/remove",
        files={"file": ("s.png", png, "image/png")},
        data={"model": "isnet-general-use", "invert": "true"},
    )
    assert normal.status_code == 200 and inverted.status_code == 200

    import io

    from PIL import Image

    a_normal = Image.open(io.BytesIO(normal.content)).getpixel((48, 48))[3]
    a_invert = Image.open(io.BytesIO(inverted.content)).getpixel((48, 48))[3]
    # the disc center is opaque normally and transparent when inverted
    assert a_normal > 200
    assert a_invert < 55
