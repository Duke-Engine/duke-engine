# Changelog

Versions are `major.minor.patch`. While the major is 0, a minor may break what came before — and
when it does, this page says exactly what to change and how.

## 0.5.0

**A map that says what its ground is painted with is now drawn with it.** `Painted` and `@Paint`
shipped in 0.4.1 and nothing answered them: the 3D client drew one flat coloured `Quad` whatever the
map said. It now lays one mesh per palette entry over the map's relief.

| | |
|---|---|
| `Painted.coverage()` | **new**, defaulting to `Map.of()`: how many cells one copy of a picture spans, by palette key. A key it does not name covers one cell. |
| `client3d` `GroundPaint` | reads the `@Paint` rows and the palette, and reports what is wrong with them |
| `client3d` `Surfaces` | how a ground material is made, from a colour and a picture |

**A palette value is used exactly as written.** No folder is put in front of it, no suffix taken off:
where a game keeps its art is that game's arrangement, and a path invented in the engine is a path
that game cannot move. The one thing read into it is whether it begins with `#`, which makes it a
colour — `#3A5F2B`, or `#FC0` — so a game that ships no textures gets painted ground too.

**How much ground one picture covers comes from the map.** Texture coordinates run across the world
divided by that, not 0..1 inside each cell — which is the difference between ground and a
chequerboard, since a picture laid per cell shows the whole of itself in every one and its own edges
draw the grid. Per palette entry, because one game's road repeats every two cells and its grass every
ten.

**A kit beats paint.** A map with both is drawn from its `Tileset` floor models; the dungeon's look is
unchanged. Paint is for the other kind of map — an outdoor field with no floor models at all.

**Nothing of it reaches the simulation.** Paint is look: no pathfinding, no cover, no speed.

**Not in it:** blending where two pictures meet. Seams are hard edges today.

Also fixed: `MapTerrain.rows` now calls `setAccessible`, as the `Binder` already did, so a map record
that is not `public` is read rather than throwing.

## 0.4.1

**Fixed: a quoted value in a `Map` kept its quotes.** `Properties = [uniqueID = "Crusader 1701"]` bound
`"Crusader 1701"` — quotes and all — where every other quoted value in the format loses them.

The reader hands a map's entry over whole, because to it `uniqueID = "…"` is one value with a quote in
the middle; the entry becomes two halves only once the `Binder` splits it at the `=`, and the `Binder`
was not finishing the job. It does now, for the key as well as the value, using the same `unquote` the
reader uses — which moved to `DukeText` so the rule is written once.

A value that opens with a quote and does not close on one is trailing text, which was already an error
inside a list and is now one here: `'X': nothing may follow a quoted value, '"a" b'`.

Anything that has a `Map<String, String>` read from data is affected — Generals' `Properties`,
`Defaults` and `Palette` among them. No file changes; re-read them and the quotes are gone.

## 0.4.0

**Core can ask four more things of a map.** All four are additions: a map record that compiled against
0.3.0 compiles against this unchanged, and answers the new questions by implementing what it has.

| Question | Core supplies | A game supplies |
|---|---|---|
| What is a cell painted with? | `@Paint` on the rows, `Painted.palette()` | rows one character a cell, and what each character names |
| Where is the water? | `MapArea`, `Zoned.areas()` | its own `Area` record, implementing `MapArea` |
| Who are the sides? | `MapSide`, `Sided.sides()` | its own `Side` record |
| What stands there before the first frame? | `MapThing`, `Furnished.things()` | its own `Thing` record |

The three that answer with more than a number answer with **interfaces**, not records core hands out, so
a block keeps the word it was written with — a game's `record Area(…) implements MapArea` needs no change
to its data files. It is the bargain `ThingTemplate` already strikes with `Solid` and `Sighted`.

`MapTerrain.rows(map, Paint.class, "paint")` reads the paint rows; it already read any mark of that kind,
so `@Paint` needed no new engine code.

Across the ground, cells; up it, steps. `MapThing.x()`/`y()` are cells, `z()` is steps, `facing()` is
degrees, and world units appear only in `Scaled.cellSize()`.

**Nothing in core consumes any of it.** Water in the pathfinder and paint in the renderer are their own
jobs; this release is about being able to ask.

**Breaking, for the example game only:** `skirmish`'s `Battlefield.Placed` is now
`(String template, float x, float y, float facing)` and implements `MapThing` — it was
`(String kind, int x, int y)`. `skirmish` is not published, so nothing on Maven Central changes shape. A
map file is unaffected: `OreNode 9 7` reads as it did.

## 0.3.0

**Two breaking changes to the `.duke` syntax, both the same correction.** The rule the format is
built on is that a block *is* its record and every line in it names one of that record's components.
Two things stood outside that rule, and now neither does.

### A map is a list of its entries, not a block of them

A map used to be written as a block named after its component, each line an entry:

```
ActiveBody
  MaxHealth = 480
  Armor                 ; before
    FLAME = 0.5
    SNIPER = 2.0
  End
End
```

`FLAME` is not a component of anything, so that block broke the rule. A map is a field now, written
the way every other list is:

```
ActiveBody
  MaxHealth = 480
  Armor = [FLAME = 0.5, SNIPER = 2.0]     ; after
End
```

Where an entry's value holds more than a line does, it is a block opened by the key it belongs to —
which is how a `Map<String, Piece>` became writable at all:

```
Pieces = [
  Minimap = Piece
    Texture = ui/panel/minimap.png
    Inset = 16
  End,
  Portrait = Piece
    Texture = ui/panel/portrait.png
  End
]
```

The reader tells the two apart by shape alone: a bare word with a body under it is no list of
values, because that one carries the comma that holds its entries apart.

**To migrate:** a map block becomes one line. The old form is now an error that names the component
and prints the line to write in its place, so `./gradlew build` finds every one of them for you.
`Binder.map(Block, Type)` is gone from the API.

### A list of blocks separates its entries with a comma

A list of values was written `[a, b]` and a list of blocks was written with nothing between them, so
one `[ … ]` had two rules. `End` followed by a word was two entries and `End` followed by `]` was
one, and nothing on the page said which — the reader had to count.

```
Modules = [
  ActiveBody
    MaxHealth = 480
  End,                  ; new: required between entries
  MoveUpdate
    Speed = 10
  End                   ; optional after the last, as in [a, b,]
]
```

**To migrate:** run [docs/plan/2026-09-22-commas.js](docs/plan/2026-09-22-commas.js) over your data
files. It is textual on purpose — the new parser rejects the old form, so it cannot be used to read
what is being migrated — and it needs only the indentation these files already keep:

```
node docs/plan/2026-09-22-commas.js $(find src/main/resources -name '*.duke')
```

It prints what it changed and touches nothing it did not have to. A missing comma is an error that
names the block it belongs before, so the build finds whatever the script missed.

## 0.2.0

The first published release: `core`, `rts`, `game`, `client3d`, `kit` and the `bom`, on Maven
Central under `uz.duke-engine`.
