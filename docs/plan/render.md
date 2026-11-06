# The renderer: what it has, what it costs, what it lacks

The engine draws with its own fork of jMonkeyEngine 3.7.0 (`render-*`). A game asks for the physically based picture with
`Visuals.physicallyBased(true)` and says how it is finished with `Visuals.picture(PictureSettings)`, or in its `.duke`
files with a `Picture` block (`client3d.PictureLook`):

```
Picture
  Quality = High        ; Low, Medium, High, Epic; left out, every effect (PictureSettings.FULL)
  Exposure = 0.7
  Atmosphere = Yes      ; the sky is the air under the sun, and the sun reddens as it sets
  FogDensity = 0.02
  FogFalloff = 0.06
  Water = No
End
```

Costs below are milliseconds per frame at 1080p on one NVIDIA desktop GPU, measured with `render-bench`
(`render-bench <scene> <words> <outdir>`; it needs `-Dbench.models=<models folder>` or every prop is a cube).

## What it has

| Part | Class | Cost |
|---|---|---|
| Physically based materials, clear coat, sheen, skin, parallax | `PBRLighting.j3md` | coat +0.03, cloth +0.02, skin +0.01, parallax +0.12 per full-screen layer |
| Sky probe from the game's sun and ambient, or the atmosphere | `SkyProbe`, `AtmosphereSky` | baked off the frame |
| Box probes for rooms | `LocalProbes` | +0.05..0.11 |
| Sun shadows, cascaded, soft, contact | `SunShadowFilter`, `SunCascades` | |
| Shadows of local lights, the most important ones each frame | `LocalShadowFilter` | grows with the budget: night 0→8 shadows 22→40 ms |
| Clustered forward lights, one pass | `ClusteredLights` | 256 lights +0..1.3, 1024 +2.5..3.6 |
| Ground-truth ambient occlusion | `GtaoFilter` | |
| Screen-space reflections | `SsrFilter` | |
| Screen-space GI, one bounce | `SsgiFilter` | +0.4..0.6 |
| Height fog and the sun's shafts | `HeightFogFilter` | |
| Water with refraction and absorption | `Water`, `WaterFilter` | +0.3 (35% of screen), +0.9 (70%) |
| Mip bloom, ACES, auto exposure, LUT, vignette, depth of field, motion blur | `PictureChain` | |
| TAA with sharpening, or FXAA | `TaaFilter` | |
| Auto instancing of shared meshes | `AutoInstancing` | forest-1000 67.7 → 28.9 ms |
| Auto LOD, levels baked off the frame | `AutoLod` | forest-5000 13.5M → 6.3M triangles |
| Occlusion culling by hardware queries | `OcclusionCulling` | town-5000 88 → 53 ms; open forest +2..10% |

## Quality steps

| Scene | Low | Medium | High | Epic |
|---|---|---|---|---|
| outdoor | 21.0 | 21.8 | 22.0 | 21.8 |
| interior | 1.60 | 2.69 | 3.51 | 4.26 |
| night (local lights) | 22.3 | 30.0 | 36.0 | 40.5 |
| town + forest-1000 | 8.0 | 8.3 | 8.3 | 8.2 |

Low has no screen-space effects and FXAA, Medium adds AO, contact shadows and 2 local shadows, High is everything but
screen-space GI with 4 local shadows, Epic is everything with 8. On Low a light behind a wall shines through it.

## Seeing it

`<game> --shot=<file.png>` starts the match with no menu, writes one picture once it has stood 12 seconds, and quits
(`Duke3D.launch(String...)`).

## What it lacks

- Shadow filters multiply the whole picture, ambient too: hard terminators, dark skin shade, the local shadow fill colour.
- Instancing rewrites every matrix each frame: big still forests are bound by the CPU submitting them.
- Occlusion answers a frame late; a thing hidden still casts its shadow.
- Only one bounce of what the screen shows; no ray tracing, no virtual geometry, no virtual shadow maps.
- Water cannot be seen from under it; the sun's glint on it is not shadowed by the filter.
- Tested on NVIDIA desktop only; Android and MSAA are not checked.
