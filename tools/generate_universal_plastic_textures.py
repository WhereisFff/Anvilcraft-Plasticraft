from __future__ import annotations

import json
import math
import struct
import zlib
from pathlib import Path


SIZE = 16
FRAME_COUNT = 4
BRIGHTNESS_LEVELS = (0.18, 0.28, 0.40, 0.54, 0.68, 0.80, 0.90, 1.00)
GRAY_LEVELS = tuple(round(255 * brightness) for brightness in BRIGHTNESS_LEVELS)
MC_COLORS = (
    (249, 255, 254),
    (249, 128, 29),
    (199, 78, 189),
    (58, 179, 218),
    (254, 216, 61),
    (128, 199, 31),
    (243, 139, 170),
    (71, 79, 82),
    (157, 157, 151),
    (22, 156, 156),
    (137, 50, 184),
    (60, 68, 170),
    (131, 84, 50),
    (94, 124, 22),
    (176, 46, 38),
    (29, 29, 33),
)


def png_chunk(chunk_type: bytes, data: bytes) -> bytes:
    checksum = zlib.crc32(chunk_type)
    checksum = zlib.crc32(data, checksum)
    return struct.pack(">I", len(data)) + chunk_type + data + struct.pack(">I", checksum)


def write_rgba_png(path: Path, width: int, height: int, pixels: list[tuple[int, int, int, int]]) -> None:
    if len(pixels) != width * height:
        raise ValueError(f"Expected {width * height} pixels, got {len(pixels)}")

    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for pixel in pixels[y * width : (y + 1) * width]:
            rows.extend(pixel)

    signature = b"\x89PNG\r\n\x1a\n"
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    image_data = zlib.compress(bytes(rows), level=9)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        signature
        + png_chunk(b"IHDR", header)
        + png_chunk(b"IDAT", image_data)
        + png_chunk(b"IEND", b"")
    )


def gray(level: int) -> tuple[int, int, int, int]:
    value = GRAY_LEVELS[max(0, min(7, level))]
    return value, value, value, 255


def melt_level(x: int, y: int, frame: int) -> int:
    phase = frame * math.pi / 2.0
    broad_flow = math.sin((x + frame * 1.5) * 0.55 + math.sin(y * 0.45) * 0.8)
    cross_flow = math.sin(y * 0.72 - phase + math.sin(x * 0.38) * 0.7)
    slow_fold = math.cos((x + y) * 0.31 + phase * 0.5)
    field = 5.15 + broad_flow * 0.9 + cross_flow * 0.65 + slow_fold * 0.4

    highlight_y = (frame * 3 + x // 3 + (x % 5 == 0)) % SIZE
    shadow_y = (highlight_y + 6 + x % 3) % SIZE
    if min((y - highlight_y) % SIZE, (highlight_y - y) % SIZE) == 0:
        field += 1.15
    if min((y - shadow_y) % SIZE, (shadow_y - y) % SIZE) == 0:
        field -= 0.85

    return max(0, min(7, round(field)))


def make_melt_pixels() -> list[tuple[int, int, int, int]]:
    pixels = []
    for frame in range(FRAME_COUNT):
        for y in range(SIZE):
            for x in range(SIZE):
                pixels.append(gray(melt_level(x, y, frame)))
    return pixels


GRANULE_PATTERN = (
    "................",
    "......34........",
    ".....4564..23...",
    ".....5665.3453..",
    "......44..454...",
    "..23......33....",
    ".3453..4564.....",
    ".454..456765....",
    "..33..567765....",
    ".....456654..23.",
    "..34..3443..3453",
    ".4564.......454.",
    ".5665..23....33.",
    "..44..3453......",
    "......454.......",
    ".......33.......",
)


def make_granule_pixels() -> list[tuple[int, int, int, int]]:
    pixels = []
    for row in GRANULE_PATTERN:
        if len(row) != SIZE:
            raise ValueError(f"Granule row must be {SIZE} pixels wide: {row!r}")
        for symbol in row:
            pixels.append((0, 0, 0, 0) if symbol == "." else gray(int(symbol)))
    return pixels


def make_palette_pixels() -> list[tuple[int, int, int, int]]:
    pixels = []
    for red, green, blue in MC_COLORS:
        for brightness in BRIGHTNESS_LEVELS:
            color = (
                round(red * brightness),
                round(green * brightness),
                round(blue * brightness),
                255,
            )
            pixels.extend((color, color))
    return pixels


def main() -> None:
    project_root = Path(__file__).resolve().parents[1]
    texture_root = project_root / "src/main/resources/assets/anvilcraftplasticraft/textures"
    melt_path = texture_root / "block/universal_plastic_melt.png"
    granule_path = texture_root / "item/universal_plastic_granule.png"
    palette_path = texture_root / "palette/universal_plastic_palette.png"

    write_rgba_png(melt_path, SIZE, SIZE * FRAME_COUNT, make_melt_pixels())
    write_rgba_png(granule_path, SIZE, SIZE, make_granule_pixels())
    write_rgba_png(palette_path, SIZE, SIZE, make_palette_pixels())

    metadata = {"animation": {"frametime": 6, "interpolate": True}}
    melt_path.with_suffix(melt_path.suffix + ".mcmeta").write_text(
        json.dumps(metadata, indent=2) + "\n",
        encoding="utf-8",
    )

    print(melt_path.relative_to(project_root))
    print(granule_path.relative_to(project_root))
    print(palette_path.relative_to(project_root))


if __name__ == "__main__":
    main()
