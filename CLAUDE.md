# duke-engine — coding rules

A reimplementation of the **SAGE engine** (C&C Generals Zero Hour) in Java.
This is the engine itself, not a game. Reference C++ source:
`../CnC_Generals_Zero_Hour` (GeneralsMD = Zero Hour, Generals = base game).

Toolchain: **Java 25** (Gradle toolchain), **Gradle 9**. No runtime deps yet —
core is pure Java; a render/audio backend comes later.

## Module boundaries (the most important rule)

- **`core` is genre-neutral.** Any game — RTS, RPG, platformer — is built on it.
  If a name or a rule only makes sense for an RTS (harvesting, build cost,
  rally points, a `MoveTo` command), it does **not** belong in `core`.
- **`rts` is the RTS on top of core.** Its command set, gameplay modules,
  economy, vocabulary and save format live here.
- `game` → `rts` → `core`. Never the other way; `core` never imports `rts`.

The three seams that keep `core` genre-free — extend these rather than
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

- **Faithful, not transliterated.** Preserve SAGE's architecture and behaviour
  (subsystem lifecycle, fixed-rate deterministic logic, logic/client split),
  but write idiomatic Java 25 — do not copy C++ idioms (manual memory, raw
  pointers, `Bool`/`Int` typedefs, singletons-as-globals).
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
