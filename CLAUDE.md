# duke-engine — coding rules

A game engine in Java, **inspired by the SAGE engine** (C&C Generals Zero Hour).
This is the engine itself, not a game. Reference C++ source:
`../CnC_Generals_Zero_Hour` (GeneralsMD = Zero Hour, Generals = base game).

Inspired by, not a port of. SAGE is where the architecture and many of the hard
lessons come from — the fixed-rate deterministic logic, the logic/client split,
the object/module composition — and those are kept faithfully. What is *not* kept
is Generals' game design. SAGE was one game's engine; this is meant to be many
games'.

- **`core`** — the base engine. Anything can be built on it: RTS, RPG,
  platformer, roguelike. It knows nothing about any genre.
- **`rts`** — a **library for making RTS games**, drawing on SAGE for how an RTS
  is put together. It is not "the Generals layer": it gives every RTS the
  mechanisms they share and leaves the rules to the game.
- **`generals`** — Generals' own rules, as one game among the games that could be
  built here. It exists to prove that point, and to keep the Generals-specific
  work rather than throw it away.

Toolchain: **Java 25** (Gradle toolchain), **Gradle 9**. No runtime deps yet —
core is pure Java; a render/audio backend comes later.

## Module boundaries (the most important rule)

- **`core` is genre-neutral.** Any game — RTS, RPG, platformer — is built on it.
  If a name or a rule only makes sense for an RTS (harvesting, build cost,
  rally points, a `MoveTo` command), it does **not** belong in `core`.
- **`rts` knows no particular game.** It is the RTS library on top of core — the
  command set, combat, production, economy, vocabulary and save format — but only
  the parts every RTS shares. A rule one game happens to have is the game's.
- `game` → `rts` → `core`, and `generals` → `rts`. Never the other way; `core`
  never imports `rts`.

**The question to ask before adding to `rts`:** *would BFME need this, and
Warcraft III, and Generals?*

| Candidate | Answer | Where |
|---|---|---|
| a unit accumulates experience | all of them | mechanism → `rts` |
| four ranks, each +10% damage | Generals only | rule → the game writes it |
| a building produces units | all of them | mechanism → `rts` |
| production stalls without power | Generals only | rule → an opt-in module |

The test is not whether something is *useful* but whether it is a **decision**.
A mechanism carries a question; a rule answers it. `rts` asks, games answer.

**Seams `rts` offers so a game can answer without editing the engine** — extend
these rather than special-casing:

| Seam | `rts` supplies | The game supplies |
|---|---|---|
| Damage | `DamageModifier`, multiplied by `WeaponUpdate` | per-unit bonuses: levels, buffs, ranks |
| Production | `ProductionGate`, asked by the factory | what stalls the line, or nothing |
| Progression | `ExperienceModule` — XP plus a configurable rung table | how many rungs, what each costs and is worth |
| Bodies | `BodyModule` (abstract) | a body that grows, or armours differently |
| Player bonuses | named bonuses on `RtsPlayer`, multi-effect `Upgrade` | what the names mean |
| HUD | `WorldSnapshot.status`, which the engine never reads | whatever this game counts |

The seams that keep `core` genre-free — extend these rather than
special-casing:

| Seam | Core supplies | A game supplies |
|---|---|---|
| Commands | `Command`, `MessageStream` | its own **sealed** command hierarchy (sealed types cannot cross modules) |
| Wire format | `PacketCodec` plug on `SocketTransport` | a codec for its commands |
| Modules | `ModuleFactory.withDefaults()` (body + locomotor) | its own module set, e.g. `RtsModules` |
| Players | `Player` (identity + diplomacy), `PlayerList(PlayerFactory)` | its `Player` subtype, e.g. `RtsPlayer` |
| Classification | `Kind`, interned by name | its vocabulary, e.g. `RtsKinds` |
| Events | `WorldEvent` + the post/drain channel | its own events, e.g. `WeaponFired` |

Before adding anything to `core`, ask: *would a game that is not an RTS want
this?* If the answer is no, it goes in `rts`.

**State vs moments.** A snapshot says what *is*; it cannot say what *happened*.
If a client would have to infer something ("it vanished while hurt, so it must
have died"), the simulation should say it outright — post a `WorldEvent`. Events
flow one way, take no part in `checksum()`, and dropping them all must not change
a single frame. Anything that does change the simulation is a module, not an
event: `DieModule` leaves the wreck, `ObjectDied` tells the renderer to explode it.

## Porting philosophy

- **Faithful, not transliterated.** Preserve SAGE's architecture (subsystem
  lifecycle, fixed-rate deterministic logic, logic/client split, object/module
  composition), but write idiomatic Java 25 — do not copy C++ idioms (manual
  memory, raw pointers, `Bool`/`Int` typedefs, singletons-as-globals).
- **Take SAGE's architecture, not Generals' design.** Where SAGE hard-codes a
  decision one game made — four veterancy ranks, production stalling without
  power, an upgrade that can only raise damage — port the *mechanism* and let the
  game supply the number, the table or the condition. A faithful port of a rule
  is an unfaithful engine: it makes every game built here that game.
- **Determinism is sacred.** The logic simulation must be reproducible from a
  frame number + command stream. No wall-clock reads, no unordered iteration, no
  floating-point nondeterminism inside `GameLogic`. If in doubt, keep it out of
  the logic path.
- **`StrictMath` for trigonometry in the logic path.** `Math.sin`/`cos`/`atan2`
  /`pow`/`hypot` are only specified to within 1 ulp and may use platform
  intrinsics, so two peers can differ in the last bit — a desync. `StrictMath`
  is bit-identical everywhere. `Math.sqrt` and `Math.abs` are exact and fine.
  Presentation code (renderers, HUD) may use `Math` freely.
- **Cite the source.** When a constant or rule comes from SAGE (e.g.
  `LOGICFRAMES_PER_SECOND = 30`), keep the name and note the origin so it stays
  verifiable against the C++.

## Java 25 idioms (non-negotiable)

- **Pattern-matching `switch`** for type dispatch — not `if (x instanceof A a)`
  chains.
- **Records** for immutable data (snapshots, DTOs, value objects like
  coordinates).
- **Sealed interfaces/classes** for closed hierarchies (message types, module
  kinds) so dispatch is exhaustive and the compiler catches new cases.
- **`var`** only when the RHS makes the type obvious.
- **Text blocks** for multi-line strings.

## Design principles

- **SRP**, **guard clauses / early return** (flat over nested).
- **Immutability by default** — `private final` unless reassignment is needed.
- **Constructor injection** — state flows in through constructors, not setters.
- **No speculative flexibility** — no flags/abstractions for cases that don't
  exist yet. Delete dead code.
- **No silent drops** — an unmatched `switch`/`instanceof` on expected input
  logs at WARNING (sealed types make this a compile error instead).

## Assets

Models, animations, textures, audio, icons and fonts belong to the **game**, not
to the engine: `core`, `rts` and `game` ship none, and `client3d` ships only the
shader it draws terrain with. A game's assets live under its own
`src/main/resources`, sorted by what a thing **is** rather than by which pack it
arrived in:

```
models/     heroes/ · monsters/ · tiles/<theme>/ · props/<theme>/
animations/ clips that are not inside a model, by who they move
audio/      sfx/ · ui/ · voice/ · music/
icons/      skills/ and any other interface art
fonts/      bitmap fonts, baked by BitmapFontBaker
ini/        the data files, read through the game's own Content class
```

**Naming:** lower case, underscores, and what the thing is —
`skeleton_warrior.glb`, `bow_shot.ogg`, `floor_squares.obj`. The pack's own
`character_medieval_2.glb` or `impactMetal_003.ogg` is renamed on the way in.
Variants of one thing are numbered: `footstep_01.ogg`, `imp_1.png`.

**Every path lives in INI, never in Java.** A path in a `.java` file is a path
that needs a rebuild to move. The game hands the client a `Visuals` built from
its own data file; the client has never heard of a file name.

**And every path is whole**, from the resource root, exactly as it is loaded:
`Icon = icons/skills/skill_arrow_shot.png`, never a bare name that code or a
`...Folder` key puts a folder in front of. A game may keep its files in whatever
structure it likes, and the line in the file is the file that is loaded — which is
also what lets the IDE plugin complete and check it.

**Adding an asset means adding a row to `CREDITS.md`** — what it is, who made
it, its licence, and where it sits — and keeping the pack's own `License.txt`
beside the files. Where the terms are not plain, say so on that page rather than
deciding quietly: a licence that turns out to forbid something is cheap to find
now and expensive to find after a release.

**Two traps that have already been paid for:**

- A `.obj` names its `.mtl` inside itself (`mtllib`), so renaming one means
  renaming both and rewriting that line. A `.glb` can name its texture the same
  way — check with `grep -a` before moving the folder it points at, and keep the
  case exactly: Linux is case-sensitive and Windows is not, so a wrong letter
  passes locally and fails in CI.
- Git on Windows runs with `core.ignorecase`, and will merge `models/` into an
  existing `Models/` without a word. Rename through a temporary name
  (`Models` → `Models_tmp` → `models`) and check `git ls-files` afterwards.

## Comment discipline

- Default: no comment; well-named identifiers carry the "what".
- Comment only the non-obvious **why**: a determinism constraint, a SAGE quirk
  being preserved, a performance tradeoff.

## Before claiming "done"

- `./gradlew build` passes.
- `./gradlew :sandbox:run` still drives the loop.
- No new `-Xlint:all` warnings.
- `core` still compiles with no reference to `rts` (it cannot see it — but
  check that nothing genre-specific leaked in the other direction either).
- Nothing new in `rts` answers a question only one game would ask. If it does,
  it belongs behind a seam, with the answer in the game — `generals` is where
  Generals' answers live.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
