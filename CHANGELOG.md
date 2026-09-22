# Changelog

Versions are `major.minor.patch`. While the major is 0, a minor may break what came before — and
when it does, this page says exactly what to change and how.

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
