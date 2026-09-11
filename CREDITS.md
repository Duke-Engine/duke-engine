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
| Forest Nature Pack — the trees that stand where a wall would | KayKit | CC0 | `models/tiles/forest/` |
| …and its rocks and bare trees | KayKit | CC0 | `models/props/forest/` |
| Universal Animation Library — what moves the bestiary | Quaternius | CC0 | `animations/monsters/` |
| Bestiary — the Imp and the Puglin, and their skins | Quaternius | **QAL 1.0** — free in any product, no credit needed, **but the files themselves may not be redistributed as assets** | `models/monsters/` |
| Adventurers — the hero, his bow and his arrows | KayKit | CC0 | `models/heroes/` |
| Character Animations — everything he does | KayKit | CC0 | `animations/hero/` |
| RPG Audio — cloth, coins, a door, a knife drawn | Kenney | CC0 | `audio/sfx/` |
| Impact Sounds — what an arrow and a fist land like, and footsteps | Kenney | CC0 | `audio/sfx/` |
| Interface Sounds — clicks, a level, a gong | Kenney | CC0 | `audio/ui/` |
| Voiceover Pack — what Erika says | Kenney | CC0 | `audio/voice/` |
| Three pieces of music | freesound.org | **unconfirmed, see below** | `audio/music/` |
| Cinzel — the lettering the menus are set in | Natanael Gama / the Cinzel Project Authors | [SIL OFL 1.1](https://openfontlicense.org) | `fonts/` |

### The one that needs a decision

There were two. The other was the hero: she came from Mixamo, which is Adobe's
and is neither CC0 nor any of the Creative Commons licences, and no licence text
shipped with the download to keep beside her. The fix was the straight swap this
page predicted — *"a CC0 rigged character of the same shape, of which KayKit's
Ranger is one"* — and it turned out to be exactly that, plus a bow, an arrow and
three libraries of movement, all CC0 and all from the pack the world is already
built from. She is gone from the tree and so is the question.

**Quaternius Bestiary (the monsters).** The QAL is explicit and shipped with the
files: *"You may not extract, repackage, sublicense, sell, or otherwise
redistribute the Assets (in original or modified form) as a standalone asset,
asset pack, stock file, template, or similar product… It does not restrict
distributing a completed Product that merely incorporates the Assets."* Building
and releasing the game is plainly allowed. Whether a public git tree counts as
"distributing them as a standalone asset" is the question, and it is a judgement
call rather than a rule. Their animation library, also here, is CC0 and has no such clause. The world
itself is KayKit now, and CC0 throughout.

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

### Nothing uses these

`_unused/` holds one file nothing references — a spare monster skin. It held two
Mixamo clips as well, and they went out with the hero they belonged to. It is
kept out of the way rather than deleted; the folder's own README says what it is.
