"""Environment setup and configuration loading.

Import this module before anything that imports rembg/huggingface: the model
cache env vars must point at the E: drive before those libraries read them.
"""

import os
from pathlib import Path
from typing import Any

os.environ.setdefault("U2NET_HOME", r"E:\AIModels\u2net")
os.environ.setdefault("HF_HOME", r"E:\AIModels\huggingface")
os.environ.setdefault("TORCH_HOME", r"E:\AIModels\torch")

for _path in (os.environ["U2NET_HOME"], os.environ["HF_HOME"], os.environ["TORCH_HOME"]):
    os.makedirs(_path, exist_ok=True)

import yaml  # noqa: E402  (env vars must be set before third-party imports)

DEFAULT_CONFIG_PATH = Path(__file__).resolve().parent.parent / "config.yaml"


def load_config(path: Path | None = None) -> dict[str, Any]:
    """Load the model-registry configuration.

    Args:
        path: Optional explicit config path; defaults to ``$BGR_CONFIG`` or the
            checked-in ``config.yaml`` next to the ``app`` package.

    Returns:
        Parsed configuration dictionary.
    """
    resolved = path or Path(os.environ.get("BGR_CONFIG", DEFAULT_CONFIG_PATH))
    with open(resolved, encoding="utf-8") as fh:
        return yaml.safe_load(fh)
