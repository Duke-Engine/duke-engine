# Changelog

Versions are `major.minor.patch`. While the major is 0, a minor may break what came before — and
when it does, this page says exactly what to change and how.

## 0.6.0

### The binder works out what a record is once

`Binder` asked reflection the same questions for every block — a record's components, their generic types,
the constructor — and looked up every scalar type's `of(String)` by throwing twice when it had none. Both are
now kept per class in a `ClassValue`. Measured on 2400 blocks of real templates, parse and bind together:
**~580 ms → ~60 ms** warm, 1180 → 210 cold. Every record, every error message and every order are what they
were.

### House colour

`Visuals.houseColour(prefix)` names the meshes that take their owner's colour — `HOUSECOLOR01` and the like,
which RTS art already marks by name. The client multiplies the owner's colour (`game.getColor`) over the
mesh's own colour and picture, ambient included, so the shading painted into it survives. Painted when a
unit arrives and again when its model is swapped for a condition. The engine names no prefix; a game that
names none is drawn as before.

### The sun belongs to the world

`Visuals.sunlight(...)` may now be said after launch, when a map is chosen, and it lights the next world
built. It used to be read once when the application was made, so every match was lit by one sun. A game
that sets it once before launch sees no difference.

### A match reads what it can draw

`DukeGame.boot()` assembles the world without stepping a frame, and `templatesThisMatchCanDraw()` reads off
it what stands there and everything those can produce, through `ProductionUpdate`. The art plan is that set
when a match was chosen — measured: 876 models (70 MB) down to the 63 (3 MB) the match can ever draw — and
everything the game registered when none was, as before. Sound cues made for a template (`died.Rifleman`)
follow it. Warming is a time budget of half a frame rather than one model a frame. A look's models for its
conditions are now planned and warmed too.

### A command bar

There was no way to hand the client a set of orders. A dungeon's `HeroPanel` reads its skills out of a
line of text, which works because the thing selected never changes; an RTS selects a barracks and then a
tank and wants a different set each time — so nothing could be built, trained or ordered except from the
keyboard.

| | |
|---|---|
| `CommandButton` | a picture, a word, a key, and whether it may be pressed — plus the id sent back |
| `WorldSnapshot.commands` | **new component**: the buttons for the current selection |
| `DukeGame.commandBar(fn)` | asked once a frame, **on the simulation thread**, so it may read live state |
| `DukeGame.setSelection(ids)` | the window reports what it has; one-way, thread-safe |
| `DukeGame.onCommandPressed(fn)` | the button's id and what it was about |
| `client3d` `CommandBar` | draws the grid and answers a click |

**Worked out where the state lives.** The selection goes out now and the buttons come back in the *next*
snapshot, computed on the simulation thread — so asking "can he afford this, is the power on, what does
this building train" reads live objects safely, and only the answer crosses.

**The engine knows what none of the buttons mean.** Training a rifleman, casting a spell and calling an
airstrike are one thing from here; a press is the game's own word, handed back.

**The key on a button is a label.** What a key *does* is claimed through `Hotkeys`, which a game has been
able to do for years — this is not a second way of pressing one.

A button that cannot be pressed keeps its place and is drawn dim: a bar whose buttons come and go as
money does is a bar nobody can learn. `skirmish` answers it — select a barracks and it offers what it
trains, priced — which is what proves it is a seam rather than one game's shape.

### A game names its own kinds of damage

`DamageType` was an enum of five whose own javadoc called it "a representative subset" — and a combat
table cannot be a subset. It is now a word, interned, exactly as `Kind` already was.

```
Armor = [SMALL_ARMS = 1.0, ARMOR_PIERCING = 0.1, POISON = 0.0]
DamageType = SMALL_ARMS
```

Nothing is registered first: the `Binder` makes one out of the word in the block, because that is how it
reads any type with a static `of(String)`. A file naming a type no armour lists loads — which is what
"unlisted is 1.0" already meant. The five constants read exactly as they did and **not one caller
needed editing**.

Identity equality, so a lookup on every landed shot costs what the enum cost. `Armor` and
`ActiveBody.Data` now keep their multipliers in the order the block wrote them rather than a hash's,
because the key hashes by identity and that is settled per run of the JVM.

### A map is chosen by its picture

`MapPackage.previewResource()` names the picture beside a map's own file the way a resource loader wants
it — `maps/crypt/preview.png` — rather than as a `Path`, which is no use for a map shipped inside a jar.
`MapPackages.shipped` is what tells it the folder, so neither spells `maps/` in code, and a map read off
the machine names none. `DukeGame.mapPictures(...)` carries them by map name, and the skirmish menu draws
one beside the lit row — which `StoneMenu` has been able to do all along.

### A template may name a model for the state its thing is in `Drawn` named one, so a thing looked
the same however hurt it was and whatever world it stood in.

```
Object
  Name  = Barracks
  Model = models/barracks.glb
  Models = [DAMAGED = models/barracks_d.glb, "DAMAGED SNOW" = models/barracks_ds.glb]
  WhenHurt = [DAMAGED = 0.5, RUBBLE = 0.1]
End
```

| | |
|---|---|
| `Drawn.models()` | **new**, default `Map.of()`: a model per set of conditions, keyed by the words that must all hold, separated by spaces |
| `Drawn.whenHurt()` | **new**, default `Map.of()`: below what share of its health each condition holds |
| `Visuals.world(String...)` | what is true of the whole world — weather, the hour, the season — said once when the map is loaded |
| `Visuals.UnitVisual.modelFor(health, world)` | the matching, as a pure function |
| `RtsTemplate` | carries both, so an `Object` block uses this without a record of its own |

**A condition is a word and nothing else.** The engine defines none of them and knows what none of them
mean: `SNOW`, `TORCHLIT`, `HARVESTED` are all alike to it.

**The rule.** A candidate fits when every word of it holds; among those that fit, the one with the most
words wins, so `DAMAGED SNOW` beats `SNOW` beats the plain model. A tie goes to the sorted words, never
to iteration order — two machines with one snapshot must draw one building.

**How deep a threshold is counts for nothing.** `RUBBLE` alone does not beat `DAMAGED`; a game that
wants the wreck to win writes `"DAMAGED RUBBLE"`, which is the same rule the world's conditions use
rather than a second one about health.

**Nothing of it reaches the simulation.** The choosing happens in the client, downstream of the
snapshot, so a machine drawing snow and one drawing summer are still playing the same game.

**A template that names no second model pays nothing** — not a lookup, not a set — and is drawn exactly
as it was.

The 3D client swaps the model where the thing stands: place and facing live on its node, so a child is
exchanged and neither moves. The bar height and the animation controls are read off the new body, and
the clip that was playing carries over by name where the new model has one.

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
