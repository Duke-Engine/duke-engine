# Credits

Art and audio the engine and its games are built from, and what each of them asks
for in return. Where a licence requires attribution it is given here; where it
only invites it, it is given anyway.

The licence text as it shipped is kept beside the files themselves, in a
`License.txt` in each folder — this page is the index, not the terms.

**Adding an asset means adding a row here.** See the rule in `CLAUDE.md`.

## Duke Dungeon

Everything lives under `dungeon/src/main/resources/`, so the paths below are
written from there.

| What | Who | Licence | Where |
|---|---|---|---|
| Skill icons — arrowhead, arrow cluster, sprint, hood | **Lorc** ([game-icons.net](https://game-icons.net)) | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/) — **attribution required** | `icons/skills/` |
| Dungeon Pack — floors, walls, stairs | **KayKit** / Kay Lousberg ([kaylousberg.com](https://kaylousberg.com)) | CC0 | `models/tiles/dungeon/` |
| …and its pillars, barrel and chest | KayKit | CC0 | `models/props/dungeon/` |
| …and the dirt floors the forest is laid on | KayKit | CC0 | `models/tiles/forest/` |
| …and the wall that retains its terraces | KayKit | CC0 | `models/props/forest/wall.gltf` |
| Forest Nature Pack — the trees that stand where a wall would | KayKit | CC0 | `models/tiles/forest/` |
| …and its rocks and bare trees | KayKit | CC0 | `models/props/forest/` |
| Skeletons — everything that walks the floors, and its blades, axes and staves | KayKit | CC0 | `models/monsters/` |
| Adventurers — all three heroes, and everything they carry | KayKit | CC0 | `models/heroes/` |
| Character Animations — everything anything down here does | KayKit | CC0 | `animations/characters/` |
| RPG Audio — cloth, coins, a door, a knife drawn | Kenney | CC0 | `audio/sfx/` |
| Impact Sounds — what an arrow and a fist land like, and footsteps | Kenney | CC0 | `audio/sfx/` |
| Interface Sounds — clicks, a level, a gong | Kenney | CC0 | `audio/ui/` |
| Fantasy UI Borders — the carved frames the hero panel's edges are painted with | Kenney | CC0 | `ui/borders/` |
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

Every model and every clip in the tree is now CC0, out of one maker's packs.
Nothing left in the art turns on how a repository is read. What is still open is
smaller and of a different kind: the four icons ask for a line of credit, which
they have below, and three pieces of music have licences nobody has looked up —
see the end of this page.

### The icons, in full

Four icons by **Lorc**, from [game-icons.net](https://game-icons.net), under
[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/):

- [Arrowhead](https://game-icons.net/1x1/lorc/arrowhead.html) — Q, the aimed shot
- [Arrow cluster](https://game-icons.net/1x1/lorc/arrow-cluster.html) — W, the volley around him
- [Sprint](https://game-icons.net/1x1/lorc/sprint.html) — E, the dash
- [Hood](https://game-icons.net/1x1/lorc/hood.html) — R, the ultimate

Changed from the originals: the opaque black backing square was removed and the
shape was written out at 128×128 on transparency, so the panel can tint it. The
drawings themselves are untouched. See the folder's `License.txt`.

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

**The skills have no icons.** Nothing in this build can reach
[game-icons.net](https://game-icons.net), so eight of the twelve slots draw the
letter of their key — which is what every slot in the game did until there were
four pictures. When more arrive they belong on the icons row above *and* in the
list under *The icons, in full*: Lorc's CC BY 3.0 asks for the line, so adding a
file without adding the line is the one mistake here that costs something.

### The audio, in full

Four packs by **Kenney** ([kenney.nl](https://kenney.nl)), all CC0 — no
attribution required, given anyway. Roughly sixty of their four hundred files are
shipped: the ones a dungeon has a use for, renamed from `impactSoft_medium_000`
to what they are actually for, and sorted into `sfx/`, `ui/` and `voice/`.

The voiceover pack is a game-show and military set rather than a fantasy one.
The lines that carry over are used — *go*, *target engaged*, *fire in the hole*,
*medic*, *level up*, *game over* — and the numbers, the quiz answers and the
weapon calls with no weapon behind them were left where they were.

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
carving, `dungeon.ini` picks between them by name, and inventing
`frame_studded.png` for `panel-border-013.png` would replace a number anyone can
look up in the pack with an adjective only this repository knows. The folders
were renamed — lower case, underscores for the spaces — and the files were not.

All 280 were copied rather than the eight in use, at 388 KB the lot, so that
choosing a different frame is an edit to `dungeon.ini` rather than a trip back to
the pack.

### The pointers

The mouse pointers are Kenney's **Cursor Pack 1.1** (CC0), at `ui/cursors/`,
with its `License.txt` beside them. The *Outline* family in both sizes — 182
drawings at 32 pixels in `default/` and the same 182 at 64 in `double/` — so
swapping size is one word in `dungeon.ini`. Five are used.

Only *Outline* was copied, and that is a decision rather than a preference. The
pack's other family, *Basic*, is pure white with no keyline: measured, one colour
and one colour only. A white pointer is invisible over a lit floor, and a
dungeon has both a lit floor and a dark one. The outlined drawings are white
**inside a black line**, which is what lets the client tint them — see
`DungeonCursor` — and what keeps them readable whatever they are over.

### The pointers that came before

Wenrexa's *Assets: Magic Cursors Pack* was shipped for one commit and removed in
the next. It was not liked, and no licence text shipped with it: the download
contained the pictures, a Discord link, a Twitter link and a thank-you card, with
the terms on the pack's page rather than in the zip. Either reason would have
been enough on its own. The note is kept because the rule it followed is worth
keeping: **an asset's licence goes in the table above before it is committed,
not after.**