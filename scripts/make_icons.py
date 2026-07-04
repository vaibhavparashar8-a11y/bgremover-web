"""Generates the desktop-shortcut icons (run once; outputs scripts/icons/*.ico).

Usage: inference\\.venv\\Scripts\\python.exe scripts\\make_icons.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).parent / "icons"
SIZES = [(16, 16), (32, 32), (48, 48), (64, 64), (256, 256)]


def base(color: tuple[int, int, int]) -> tuple[Image.Image, ImageDraw.ImageDraw]:
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.ellipse([8, 8, 248, 248], fill=color)
    return img, draw


def make_start() -> Image.Image:
    img, draw = base((0, 184, 148))  # green
    draw.polygon([(100, 72), (100, 184), (192, 128)], fill=(255, 255, 255))
    return img


def make_stop() -> Image.Image:
    img, draw = base((214, 48, 49))  # red
    draw.rounded_rectangle([88, 88, 168, 168], radius=12, fill=(255, 255, 255))
    return img


def main() -> None:
    OUT.mkdir(exist_ok=True)
    make_start().save(OUT / "start.ico", sizes=SIZES)
    make_stop().save(OUT / "stop.ico", sizes=SIZES)
    print(f"icons written to {OUT}")


if __name__ == "__main__":
    main()
