"""The two gradients a column of light is drawn from: a white-hot core and a soft halo.

The particle pack has streaks, sparks and rings and nothing that is a column, so these
are made rather than found. White on transparency, like every particle texture -- the
layer gives the colour. Row 0 of the image is the TOP of the column, since the client
flips a texture as it loads it.

    py dungeon/art/effects/pillar_textures.py
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main',
                   'resources', 'effects', 'particles')
WIDE, TALL = 128, 512


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


across = (np.arange(WIDE) + 0.5) / WIDE * 2.0 - 1.0
up = 1.0 - (np.arange(TALL) + 0.5) / TALL
X, UP = np.meshgrid(across, up)
edge = 1.0 - smoothstep(0.72, 1.0, np.abs(X))

# Soft at BOTH ends. A column is drawn over however much of it has risen or come
# down so far, so either end of the picture may be the end that is moving -- and a
# moving end that stops in bright light is a hard straight line across the screen.

# The core: a hot narrow middle with soft shoulders, strongest low down and gone at
# the top.
core = ((np.exp(-(X / 0.12) ** 2) * 0.72 + np.exp(-(X / 0.36) ** 2) * 0.28) * edge
        * smoothstep(0.0, 0.12, UP) * (1.0 - smoothstep(0.4, 1.0, UP)) ** 1.3)

# The halo: wide and soft all the way across, and gone sooner than the core.
halo = (np.exp(-(X / 0.48) ** 2) * edge
        * smoothstep(0.0, 0.16, UP) * (1.0 - smoothstep(0.2, 1.0, UP)) ** 1.6)


def save(name, strength):
    rgba = np.zeros((TALL, WIDE, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(np.clip(strength / strength.max(), 0.0, 1.0) * 255.0)
    Image.fromarray(rgba, 'RGBA').save(os.path.join(OUT, name), optimize=True)
    print('wrote', os.path.normpath(os.path.join(OUT, name)))


save('pillar_core.png', core)
save('pillar_halo.png', halo)
