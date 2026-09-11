# Not referenced by anything

This file ships with the game and nothing asks for it — no INI block names it,
no Java path mentions it, and no test loads it. It is kept here rather than
deleted so that the decision is a person's rather than a refactor's; git would
remember it either way.

| File | Size | Why it is here |
|---|---|---|
| `T_Puglin_BaseColor_3.png` | 1.3 MB | The bestiary ships three skins per creature; `dungeon.ini` dresses the Puglin in `_1` and `_2`. The third was never used. |

Two Mixamo clips used to sit here too — a walk-while-aiming and a punch, for
moves the hero did not have. They went out with the hero they were rigged to
when he became KayKit's Ranger, whose own libraries carry both anyway.

Nothing here is loaded at run time. Deleting the folder changes nothing that
happens on screen.
