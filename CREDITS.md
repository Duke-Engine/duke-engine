# Credits

Art and audio the engine and its games are built from, and what each of them asks
for in return. Where a licence requires attribution it is given here; where it
only invites it, it is given anyway.

The licence text as it shipped is kept beside the files themselves, in a
`License.txt` in each folder — this page is the index, not the terms.

The code itself is under the MIT License — see `LICENSE`. None of the assets below
are: each keeps the terms given here.

**Adding an asset means adding a row here.** See the rule in `CLAUDE.md`.

## The kit

The starter set every game may draw from — the effects, and the art they are drawn with. Everything
lives under `kit/src/main/resources/`, so the paths below are written from there.

| What | Who | Licence | Where |
|---|---|---|---|
| Particle Pack — every flame, smoke, spark, slash and ring the skills are drawn with | Kenney | CC0 | `kit/effects/particles/` |
| Two columns of light — the gradients a level and an arrival are drawn with | made for this project, by a script that went with [duke-dungeon](https://github.com/Duke-Engine/duke-dungeon) (`art/effects/pillar_textures.py` there) | CC0 | `kit/effects/particles/pillar_{core,halo}.png` |

### Why the particles are half the size they shipped at

They shipped at 512x512 and are kept at **256x256**. A particle is a soft shape
seen for a fraction of a second, usually smaller on screen than the texture is
wide, and a 512 texture with its mipmaps is about 1.3 MB of video memory — thirty
of them is forty megabytes on the kind of laptop GPU that was already running hot.
The quarter-size set is 2.7 MB on disk and indistinguishable in motion.

They were shrunk in **premultiplied alpha**, by `art/effects/shrink_particles.py`.
Every one of them is a white shape on transparency, and resampling colour and
alpha separately averages the white of the shape with the black of the empty
pixels beside it — the edge comes out darker than the middle, which under
ordinary blending is a grey fringe round every puff of smoke. The script says how
to run it again against a newer pack.

The names are Kenney's: a renamed `smoke_07` is a file nobody can find in the
pack again, so the line in an effect file is the line in the pack.

## Duke Skirmish

The example game in this repository. Duke Dungeon's assets are credited in its own repository,
[Duke-Engine/duke-dungeon](https://github.com/Duke-Engine/duke-dungeon).

Everything lives under `skirmish/src/main/resources/`, so the paths below are
written from there. Every file here is a copy of one the dungeon already ships,
renamed for what it is in *this* game — a rock is an ore node, a decorated
pillar is a barracks — because a game owns its own assets rather than reaching
into another's. Each `.gltf` was copied with its `.bin` and its texture, and the
`uri` line inside it rewritten to the new name.

| What | Who | Licence | Where |
|---|---|---|---|
| The three units — worker, soldier, archer (the dungeon's rogue, knight and ranger) | **KayKit** / Kay Lousberg ([kaylousberg.com](https://kaylousberg.com)) | CC0 | `models/units/` |
| Ore nodes and a bare tree (its rocks and trees) | KayKit | CC0 | `models/ground/` |
| The field it is all fought on (its dirt floor) | KayKit | CC0 | `models/ground/field.*` |
| A barracks and a depot (its decorated pillar and chest) | KayKit | CC0 | `models/base/` |

Each folder keeps the pack's own `License.txt` beside the files.
