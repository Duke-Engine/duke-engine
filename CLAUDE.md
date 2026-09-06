# duke-engine — coding rules

A reimplementation of the **SAGE engine** (C&C Generals Zero Hour) in Java.
This is the engine itself, not a game. Reference C++ source:
`../CnC_Generals_Zero_Hour` (GeneralsMD = Zero Hour, Generals = base game).

Toolchain: **Java 25** (Gradle toolchain), **Gradle 9**. No runtime deps yet —
core is pure Java; a render/audio backend comes later.

## Porting philosophy

- **Faithful, not transliterated.** Preserve SAGE's architecture and behaviour
  (subsystem lifecycle, fixed-rate deterministic logic, logic/client split),
  but write idiomatic Java 25 — do not copy C++ idioms (manual memory, raw
  pointers, `Bool`/`Int` typedefs, singletons-as-globals).
- **Determinism is sacred.** The logic simulation must be reproducible from a
  frame number + command stream. No wall-clock reads, no unordered iteration, no
  floating-point nondeterminism inside `GameLogic`. If in doubt, keep it out of
  the logic path.
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
