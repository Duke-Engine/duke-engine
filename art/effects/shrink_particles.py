"""Bring Kenney's Particle Pack into the game at a size a laptop can afford.

The pack ships every texture at 512 x 512. A particle is a soft shape seen for a
fraction of a second, usually smaller on screen than the texture is wide, and a
512 x 512 RGBA texture with its mipmaps is about 1.3 MB of video memory -- thirty
of them is forty megabytes on an integrated GPU that is already running hot.
256 x 256 is a quarter of that and indistinguishable in motion.

Resized in PREMULTIPLIED alpha. Every texture here is a white shape on
transparency, and resampling colour and alpha separately averages the white of
the shape with the black of the empty pixels beside it: the edge comes out
darker than the middle, which under ordinary alpha blending is a grey fringe
round every puff of smoke. Multiplying the colour by the alpha first, resizing,
and dividing it back out is what keeps the edge the colour of the middle.

The pack's own names are kept. `smoke_07` is not a description that can be
improved on -- there are ten interchangeable smokes and the number is the only
thing that tells them apart, which is the same reasoning CREDITS.md gives for
the frames.

    py art/effects/shrink_particles.py "<pack>/PNG (Transparent)" \
        dungeon/src/main/resources/effects/particles
"""

import argparse
import shutil
from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 256


def shrink(source: Path, target: Path) -> int:
    rgba = np.asarray(Image.open(source).convert("RGBA"), dtype=np.float64) / 255.0
    alpha = rgba[..., 3:4]
    premultiplied = np.concatenate([rgba[..., :3] * alpha, alpha], axis=-1)

    # One channel at a time through PIL's own filter, since it resamples float
    # images only as single-channel "F" mode.
    channels = []
    for index in range(4):
        plane = Image.fromarray(premultiplied[..., index].astype(np.float32), mode="F")
        channels.append(np.asarray(plane.resize((SIZE, SIZE), Image.Resampling.LANCZOS)))
    small = np.clip(np.stack(channels, axis=-1), 0.0, 1.0)

    out_alpha = small[..., 3:4]
    colour = np.where(out_alpha > 1e-6, small[..., :3] / np.maximum(out_alpha, 1e-6), 1.0)
    result = np.concatenate([np.clip(colour, 0.0, 1.0), out_alpha], axis=-1)

    target.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray((result * 255.0 + 0.5).astype(np.uint8), mode="RGBA").save(
        target, optimize=True)
    return target.stat().st_size


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("source", type=Path, help="the pack's 'PNG (Transparent)' folder")
    parser.add_argument("target", type=Path, help="where the game reads them from")
    arguments = parser.parse_args()

    written = 0
    count = 0
    for source in sorted(arguments.source.rglob("*.png")):
        relative = source.relative_to(arguments.source)
        # Lower case and no spaces on the way in, as every other folder is.
        folder = Path(*[part.lower().replace(" ", "_") for part in relative.parent.parts])
        written += shrink(source, arguments.target / folder / source.name)
        count += 1

    licence = arguments.source.parent / "License.txt"
    if licence.exists():
        shutil.copyfile(licence, arguments.target / "License.txt")

    print(f"{count} textures, {written / 1024:.0f} KB at {SIZE}x{SIZE}")


if __name__ == "__main__":
    main()
