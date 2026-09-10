# Not referenced by anything

These five files ship with the game and nothing asks for them — no INI block
names them, no Java path mentions them, and no test loads them. They are kept
here rather than deleted so that the decision is a person's rather than a
refactor's; git would remember them either way.

| File | Size | Why it is here |
|---|---|---|
| `T_Puglin_BaseColor_3.png` | 1.3 MB | The bestiary ships three skins per creature; `dungeon.ini` dresses the Puglin in `_1` and `_2`. The third was never used. |
| `aimwalk.glb` | 55 KB | A Mixamo clip. The hero walks with `run.glb` and shoots standing still, so a walk-while-aiming was downloaded and never wired up. |
| `punch.glb` | 52 KB | The same. He carries a bow. |
| `template-floor-detail.glb` | 22 KB | Kenney floor tiles with a pattern on them. No tone names them — the dungeon lays one plain floor. |
| `template-floor-detail-a.glb` | 22 KB | The same, second variant. |

Nothing here is loaded at run time. Deleting the folder changes nothing that
happens on screen.
