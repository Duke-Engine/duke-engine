# Credits

Art and audio the engine and its games are built from, and what each of them asks
for in return. Where a licence requires attribution it is given here; where it
only invites it, it is given anyway.

The licence text as it shipped is kept beside the files themselves, in a
`License.txt` in each folder — this page is the index, not the terms.

The code itself is under the MIT License — see `LICENSE`. None of the assets below
are: each keeps the terms given here.

**Adding an asset means adding a row here.** See the rule in `CLAUDE.md`.

## Duke Dungeon

Everything lives under `dungeon/src/main/resources/`, so the paths below are
written from there.

| What | Who | Licence | Where |
|---|---|---|---|
| Skill, command and figure icons — all 22 | **Generated with Microsoft Copilot** (AI), cut and resized by `:dungeon:cutIcons` | see the note below | `icons/{skills,commands,stats}/` |
| Attribute icons — strength, agility, intelligence | **AI-generated**, supplied by the owner as one sheet; cut and resized by `:dungeon:cutIcons` | see the note below | `icons/stats/stat_{strength,agility,intelligence}.png` |
| Dungeon Pack — floors, walls, stairs | **KayKit** / Kay Lousberg ([kaylousberg.com](https://kaylousberg.com)) | CC0 | `models/tiles/dungeon/` |
| …and its pillars, barrel and chest | KayKit | CC0 | `models/props/dungeon/` |
| …and the dirt floors the forest is laid on | KayKit | CC0 | `models/tiles/forest/` |
| …and the wall that retains its terraces | KayKit | CC0 | `models/props/forest/wall.gltf` |
| Forest Nature Pack — the trees that stand where a wall would | KayKit | CC0 | `models/tiles/forest/` |
| …and its rocks and bare trees | KayKit | CC0 | `models/props/forest/` |
| Skeletons — everything that walks the floors, and its blades, axes and staves | KayKit | CC0 | `models/monsters/` |
| …and its atlas repainted with a green robe and a purple one, for the Skeleton Healer and the Skeleton Summoner | repainted from the original KayKit atlas, supplied by the owner | CC0 (a repaint of a CC0 atlas) | `models/monsters/skeleton_texture_{green,purple}.png` |
| Adventurers — all three heroes, and everything they carry | KayKit | CC0 | `models/heroes/` |
| Character Animations — everything anything down here does | KayKit | CC0 | `animations/characters/` |
| Standing 2H Magic Area Attack — the mage's meteor | **Mixamo** / Adobe | **not CC0, see below** | `animations/characters/magic.glb` |
| RPG Audio — cloth, coins, a door, a knife drawn | Kenney | CC0 | `audio/sfx/` |
| Impact Sounds — what an arrow and a fist land like, and footsteps | Kenney | CC0 | `audio/sfx/` |
| Interface Sounds — clicks and a gong | Kenney | CC0 | `audio/ui/` |
| A climb of notes — the level-up | from the owner's own asset folder, maker not named | **unconfirmed, see below** | `audio/ui/level_up.ogg` |
| Fantasy UI Borders — the carved frames the hero panel's edges are painted with | Kenney | CC0 | `ui/borders/` |
| Particle Pack — every flame, smoke, spark, slash and ring the skills are drawn with | Kenney | CC0 | `effects/particles/` |
| Two columns of light — the gradients a level and an arrival are drawn with | made for this game, by `dungeon/art/effects/pillar_textures.py` | CC0 | `effects/particles/pillar_{core,halo}.png` |
| Cursor Pack — the mouse pointers | Kenney | CC0 | `ui/cursors/` |
| Voiceover Pack — what Erika says | Kenney | CC0 | `audio/voice/` |
| Three pieces of music | freesound.org | **unconfirmed, see below** | `audio/music/` |
| Cinzel — the lettering the menus are set in | Natanael Gama / the Cinzel Project Authors | [SIL OFL 1.1](https://openfontlicense.org) | `fonts/` |

### The two that needed a decision, and no longer do

Both are settled, and both the same way. This section is kept rather than deleted
because the reasoning is worth having next time a free asset arrives with terms
attached.

**The hero** came from Mixamo, which is Adobe's and is neither CC0 nor any of the
Creative Commons licences, and no licence text shipped with the download to keep
beside her. The fix was the straight swap this page predicted — *"a CC0 rigged
character of the same shape, of which KayKit's Ranger is one"* — and it turned
out to be exactly that, plus a bow, an arrow and three libraries of movement.

**The monsters** were Quaternius's Bestiary, under the QAL: *"You may not
extract, repackage, sublicense, sell, or otherwise redistribute the Assets… as a
standalone asset, asset pack, stock file, template, or similar product."*
Building and releasing the game was plainly allowed; whether a public git tree
counts as handing the files out was a judgement call rather than a rule, and the
kind of question that does not get easier by being left. They are gone too.

Every model in the tree is CC0, out of one maker's packs, and every clip but one.
**That one is a Mixamo animation and it went in with its eyes open** — the mage's
two-handed cast, added later and knowingly, which puts the question this section
settled back on the page. It is set out in full below rather than folded away
here, because a decision reversed deserves as plain a statement as the decision
did.

Two other things are open and are of a different kind: three pieces of music have
licences nobody has looked up, and the icons are a generator's output whose terms
are not the same sort of thing as a licence. Both are at the end of this page.

### The icons that came before

Four icons by **Lorc**, from [game-icons.net](https://game-icons.net), under
[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) — the archer's whole
set, and the only skill art the game had until the generated sheets arrived:

- [Arrowhead](https://game-icons.net/1x1/lorc/arrowhead.html) — Q, the aimed shot
- [Arrow cluster](https://game-icons.net/1x1/lorc/arrow-cluster.html) — W, the volley around him
- [Sprint](https://game-icons.net/1x1/lorc/sprint.html) — E, the dash
- [Hood](https://game-icons.net/1x1/lorc/hood.html) — R, the ultimate

Changed from the originals: the opaque black backing square was removed and the
shape was written out at 128×128 on transparency, so the panel could tint it. The
drawings themselves are untouched.

They are at `_unused/icons/` now and **nothing draws them**, which is why they
are no longer in the table above — CC BY asks for a line of credit for art that
is *used*. They are kept with their `License.txt` because they are the fallback:
see the note at the end of this page for why a generated set wants one.

### The three heroes, in full

All three come out of KayKit Adventurers 2.0 (CC0), which ships five characters
— Barbarian, Knight, Mage, Ranger and Rogue — on one shared rig. Three of them
are in this repository, along with the weapons each of them carries:

- **Rogue** (`rogue.glb`) — the archer. He wore the ranger's mesh while he was
  the only hero and there was nobody to be told apart from. Carries `bow.gltf`
  and `quiver.gltf`.
- **Knight** (`knight.glb`) — `sword_1handed.gltf` and `shield_round.gltf`.
- **Mage** (`mage.glb`) — `staff.gltf` and `spellbook_closed.gltf`. He briefly
  wore the ranger's mesh and the *skeletons'* staff, which was a placeholder and
  is not one any more.

`ranger.glb` is still here and nothing uses it. It is the obvious fourth hero,
along with `sword_2handed.gltf`, which the knight put down when he picked up a
shield.

**All twelve skills have icons now**, out of the generated sheets — which is what
the eight empty slots on this page were waiting for.

### The audio, in full

Four packs by **Kenney** ([kenney.nl](https://kenney.nl)), all CC0 — no
attribution required, given anyway. Roughly sixty of their four hundred files are
shipped: the ones a dungeon has a use for, renamed from `impactSoft_medium_000`
to what they are actually for, and sorted into `sfx/`, `ui/` and `voice/`.

The voiceover pack is a game-show and military set rather than a fantasy one.
The lines that carry over are used — *go*, *target engaged*, *fire in the hole*,
*medic*, *level up*, *game over* — and the numbers, the quiz answers and the
weapon calls with no weapon behind them were left where they were.

### The one animation that is not KayKit's

`magic.glb` is **Mixamo's**, which is Adobe's, and it is the only file in the
tree that is not CC0. It is the mage's ultimate: the kit has no two-handed cast
in it, and a man calling a meteor down by waving a staff one-handed is not the
gesture.

**This is the same question the hero and the monsters were removed over**, and it
is worth saying plainly rather than leaving for somebody to find. Mixamo's terms
are royalty-free and generous about *using* the animation in a game; what they do
not clearly cover is redistributing the animation **as a file**, which is what a
public git tree does. That is the reasoning that retired the Mixamo hero and the
Quaternius bestiary from this repository, and it applies here unchanged.

It is here anyway, knowingly, because this is a hobby project and the owner said
so. Two things make it cheap to undo if that ever changes:

- The source `.fbx` is at `dungeon/art/anim/`, outside the resources, so removing
  the animation is deleting two files rather than unpicking a folder.
- Nothing else depends on it. `CastAnim` in the mage's `R` skill
  (`data/units/mage.duke`) names the clip; delete the two lines and the mage casts
  his meteor standing still, exactly as he did before this arrived.

The bones are KayKit's own, which is luck rather than planning: it was animated
on the mage himself, so the 23 joints are named as the kit names them and the
client's retargeting matches them one for one. A clip straight off Mixamo's own
rig arrives as `mixamorig:Hips` and friends and would animate nothing at all —
`dungeon/art/anim/fbx_to_glb.py` fails loudly rather than exporting one.

### The music — licences to confirm

Three tracks from [freesound.org](https://freesound.org), converted from WAV to
Ogg Vorbis (12 MB of PCM to 200 KB, which is the whole reason):

| Track | Freesound | Author |
|---|---|---|
| Industrial | [862920](https://freesound.org/s/862920/) | looplicator |
| Uncertainty | [774883](https://freesound.org/s/774883/) | destructo20 |
| The Sentinel | [868449](https://freesound.org/s/868449/) | logicmoon |

Freesound hosts CC0, CC BY and CC BY-NC side by side, and which of them applies
is per upload rather than per site. Before this is shipped anywhere, each of the
three pages above needs looking at: CC0 needs nothing, CC BY needs a line here
naming the author, and CC BY-NC would mean the track cannot ship in anything
sold. They are named here so that the question is on the page rather than
forgotten.

### The level-up sound — licence to confirm

`audio/ui/level_up.ogg` is `SFX_1up07.ogg` out of a folder of 110 short effects
(`Sound FX_(ogg)`: 1up, Blip, Boom, Coin, Error, Hit, Hurt, Jump, Lose, Shoot and
Slash, ten of each) that the owner keeps with his other game assets. It was chosen
for being a climb of notes as long as the light it plays with. No licence file and
no maker's name came with the folder, so where it came from and on what terms is
the open question: it is named here rather than decided quietly. Kenney's own
`level_up` from the Interface Sounds pack is what it replaced, and is the CC0 way
back if the answer is the wrong one.

### The lettering

The menus are set in **Cinzel** — the Cinzel Project Authors, Natanael Gama —
under the [SIL Open Font License 1.1](https://openfontlicense.org). The licence
ships with it at `fonts/License-Cinzel.txt`, as the OFL requires.

jME cannot read a TrueType font, so what ships is a bitmap of it: a PNG of every
glyph and an AngelCode `.fnt` saying where each one sits, in three sizes. Those
were baked here rather than downloaded — `client3d`'s `BitmapFontBaker` turns a
`.ttf` into the pair — so the OFL's terms are met by the original, and anyone can
re-bake them from it.


### The frames, and why they keep Kenney's numbering

The hero panel's edges are painted from **Fantasy UI Borders** (Kenney, CC0),
which ships at `ui/borders/` with its own `License.txt` beside it. Two families —
`default/` at 48×48 and `double/` at 96×96 — each in six sets: `border/` (frame
only, transparent middle), `panel/` (frame plus an opaque middle),
`transparent_border/`, `transparent_center/`, `divider/` and `divider_fade/`.

`CLAUDE.md` says a pack's own file names are changed on the way in, and these
were not. The rule exists so that `character_medieval_2.glb` becomes a name that
says what the thing is; here the number **is** what the thing is. There are
thirty-two interchangeable frames with nothing to tell them apart but their
carving, `data/world/hud.duke` picks between them by name, and inventing
`frame_studded.png` for `panel-border-013.png` would replace a number anyone can
look up in the pack with an adjective only this repository knows. The folders
were renamed — lower case, underscores for the spaces — and the files were not.

All 280 were copied rather than the eight in use, at 388 KB the lot, so that
choosing a different frame is an edit to `data/world/hud.duke` rather than a trip back to
the pack.

### The particles, and why they are half the size they shipped at

**Particle Pack** (Kenney, CC0, version 1.1) ships at `effects/particles/` with its
own `License.txt` beside it — all 96 of the transparent textures, `rotated/`
included, so choosing a different smoke for a skill is an edit to its effect in `data/effects/`
rather than a trip back to the pack.

They shipped at 512×512 and are kept at **256×256**. A particle is a soft shape
seen for a fraction of a second, usually smaller on screen than the texture is
wide, and a 512 texture with its mipmaps is about 1.3 MB of video memory — thirty
of them is forty megabytes on the kind of laptop GPU that was already running hot.
The quarter-size set is 2.7 MB on disk and indistinguishable in motion.

They were shrunk in **premultiplied alpha**, by `art/effects/shrink_particles.py`.
Every one of them is a white shape on transparency, and resampling colour and
alpha separately averages the white of the shape with the black of the empty
pixels beside it — the edge comes out darker than the middle, which under ordinary
blending is a grey fringe round every puff of smoke. The script says how to run it
again against a newer pack.

The names are Kenney's, for the reason the frames' are: `smoke_07` cannot be
improved on when there are ten interchangeable smokes and the number is the only
thing that tells them apart. The folders were lower-cased on the way in.

### The pointers

The mouse pointers are Kenney's **Cursor Pack 1.1** (CC0), at `ui/cursors/`,
with its `License.txt` beside them. The *Outline* family in both sizes — 182
drawings at 32 pixels in `default/` and the same 182 at 64 in `double/` — so
swapping size is one word in `data/world/hud.duke`. Five are used.

Only *Outline* was copied, and that is a decision rather than a preference. The
pack's other family, *Basic*, is pure white with no keyline: measured, one colour
and one colour only. A white pointer is invisible over a lit floor, and a
dungeon has both a lit floor and a dark one. The outlined drawings are white
**inside a black line**, which is what lets the client tint them — see
the `Cursor` blocks of `data/world/hud.duke` — and what keeps them readable whatever they are over.

### The pointers that came before

Wenrexa's *Assets: Magic Cursors Pack* was shipped for one commit and removed in
the next. It was not liked, and no licence text shipped with it: the download
contained the pictures, a Discord link, a Twitter link and a thank-you card, with
the terms on the pack's page rather than in the zip. Either reason would have
been enough on its own. The note is kept because the rule it followed is worth
keeping: **an asset's licence goes in the table above before it is committed,
not after.**
### The icons, and what is not known about them

All twenty-two pictures the panel draws — ten skills, four order buttons and
eight figures — were **generated with Microsoft Copilot** and cut out of three
sheets by `./gradlew :dungeon:cutIcons`. The sheets themselves are kept at
`dungeon/art/icons/`, outside the resources, so the cut can be run again.

The three attribute pictures — an arm for strength, a runner for agility, a head
with a brain for intelligence — are **AI-generated** as well, in the same flat
gold, and came as one sheet from the owner's asset folder. It is kept beside the
others as `dungeon/art/icons/attributes_sheet.png` and cut the same way:
`./gradlew :dungeon:cutIcons --args=attributes`. Which generator drew it is not
recorded, so everything below applies to them exactly as it does to the rest.

The terms are recorded here as what they are, which is **not fully settled**.
Microsoft's service terms give the user broad rights to what it produces, and
under current US and UK practice an image with no human author has no copyright
of its own to license — which is closer to "nobody owns it" than to CC0, and is
not the same thing as a licence somebody granted. Two things follow, and both
are worth knowing before this ships anywhere that matters:

- **No attribution is owed** and none is claimed. The line in the table is a
  record of where the pictures came from, not a condition on using them.
- **No warranty of originality.** A generator can reproduce what it was trained
  on. Nothing here was checked against anything, and nobody looked.

The line-drawn skill icons these replaced are at
`dungeon/src/main/resources/_unused/icons/` with their own `License.txt`. They
are game-icons.net's, CC BY 3.0, and **do** require attribution — which is why
they were moved rather than deleted: if the generated set ever has to go, the
art that replaces it is already there and its terms are already known.
