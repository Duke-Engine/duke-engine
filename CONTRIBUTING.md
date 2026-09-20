# Contributing to duke-engine

Thank you for looking. Issues and pull requests are both welcome, and a question counts as a
contribution — if something took you an hour to work out, that is a hole in the documentation and
worth an issue on its own.

## Getting it running

Nothing needs installing beyond the wrapper; Gradle provisions the Java 25 toolchain itself.

```
git clone https://github.com/abdurasul29052002/duke-engine.git
cd duke-engine
./gradlew build          # compile + every test, about a minute
./gradlew :dungeon:run   # see that it actually plays
```

The IntelliJ plugin is a **separate Gradle build** in `duke-plugin/`:

```
cd duke-plugin
./gradlew test     # 54 tests
./gradlew runIde   # a sandbox IDE with the plugin in it
```

Open the sandbox IDE on this repository — the plugin reads the games' `.duke` files against the
engine's own records, so the repository is its own best test project.

The plugin's tests read two checkouts off the disk: the engine and a game. Both are looked for in
the usual places and can be pointed anywhere with `DUKE_ENGINE` and `DUKE_SAMPLE` — see
[duke-plugin/README.md](duke-plugin/README.md).

> If `runIde` fails to start, close any sandbox IDE that is already open: the plugin jar is locked
> while one is running.

## What to work on

- **`docs/plan/`** holds the current plan, phase by phase, with what is done and what is not.
- The **Known gaps** list in [README.md](README.md) is honest and not a wish-list — anything there
  is genuinely open.

If you are about to spend more than an afternoon on something, open an issue first. Not for
permission — so that two people do not write the same thing twice.

## The rules that are not negotiable

[CLAUDE.md](CLAUDE.md) is the full set and is worth reading before a first pull request. It is
addressed to an AI assistant because one wrote a good deal of this code, but every rule in it
applies to everybody. The four that a review will always check:

### 1. Determinism is sacred

The simulation must be reproducible from a frame number plus a command stream. Inside `GameLogic`
and anything it ticks: **no wall-clock reads, no unordered iteration, no `Math.random()`, no
threads.** Two machines that diverge by one bit desync a multiplayer game and break every replay.

Use `StrictMath` for trigonometry in the logic path — `Math.sin`/`cos`/`atan2`/`pow`/`hypot` are
specified only to within 1 ulp and may use platform intrinsics, so two peers can differ in the last
bit. `Math.sqrt` and `Math.abs` are exact and fine. Presentation code may use `Math` freely.

### 2. Module boundaries

```
dungeon, skirmish  →  client3d  →  game  →  rts  →  core
```

Never the other way. `core` never imports `rts`.

- **`core` is genre-neutral.** If a name only makes sense for an RTS — harvesting, build cost, rally
  points — it does not belong there.
- **`rts` knows no particular game.** The question to ask is: *would BFME need this, and Warcraft
  III, and Generals?* If only one of them would, it is that game's rule, not a mechanism.

The test is not whether something is *useful* but whether it is a **decision**. A mechanism carries
a question; a rule answers it. `rts` asks, games answer.

### 3. A rule belongs in data, not in Java

Numbers, names, prices, what a unit looks like, which effect it wears: all of it is a `.duke` block
read into a record. A pull request that hard-codes a number a game might want to change will be
asked to move it into a block.

Likewise **every asset path lives in a data file, never in Java** — a path in a `.java` file is a
path that needs a rebuild to move.

### 4. Tests, and what they are for

Every module's tests run in `./gradlew build`, and the build must be green before a pull request is
ready. Beyond that:

- A bug fix comes with the test that would have caught it.
- A test asserts behaviour, not implementation. `StagePlayTest` checks that a shipped map can be
  played; it does not check which class laid it out.
- Tests never open a window. The engine runs headless (`game.runHeadless(frames)`) and so do the
  plugin's.

## Before you open a pull request

```
./gradlew build                          # green
cd duke-plugin && ./gradlew test         # green, if you touched the plugin
```

- No new `-Xlint:all` warnings.
- `core` still compiles with no reference to `rts`, and nothing genre-specific leaked the other way.
- If you added an asset, add a row to [CREDITS.md](CREDITS.md) — what it is, who made it, its
  licence and where it sits — and keep the pack's own `License.txt` beside the files. Where the
  terms are not plain, say so on that page rather than deciding quietly.

## Style

Idiomatic Java 25: pattern-matching `switch` over `instanceof` chains, records for immutable data,
sealed interfaces for closed hierarchies, `var` only where the right-hand side makes the type
obvious, text blocks for multi-line strings.

**Comments explain why, not what.** A well-named method needs no comment; a determinism constraint,
a SAGE quirk being preserved deliberately, or a performance trade-off does. If a comment restates
the line under it, delete the comment.

Commit messages: a short summary line saying what changed and, where it is not obvious, a body
saying why.

## Licence of contributions

By opening a pull request you agree that your contribution is released under the
[MIT License](LICENSE), the same as the rest of the code.

Assets are different: anything that is not code keeps its own terms, and a contribution that brings
one must say what those terms are. Art with unclear licensing will not be merged — it is cheap to
find out now and expensive to find out after a release.

## Reporting a bug

Say what you did, what you expected and what happened. For anything in the simulation, the two
things that make a report actionable are **the map or data file** and **the seed**: the engine is
deterministic, so with both of those the bug reproduces exactly on any machine.

## Security

If you find something that should not be reported in public, use the repository's **Security** tab to
open a private advisory rather than filing an issue.
