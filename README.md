# duke-engine

[![CI](https://github.com/abdurasul29052002/duke-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/abdurasul29052002/duke-engine/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/)

**A deterministic game engine in Java, and an IntelliJ plugin that makes games with it without
writing code.**

A game here is text files. What a unit is, what it looks like, what it can do, where it stands on a
map — all of it is `.duke` blocks the engine reads into your own Java records, and the plugin turns
those same records into a form, a 3D map editor and a preview. Changing how much a unit costs is
changing a line in a file, not a recompile.

The engine is a from-scratch reimplementation of the **SAGE engine** (behind *Command & Conquer:
Generals — Zero Hour*): a subsystem framework driving a fixed-rate, lock-step simulation cleanly
separated from presentation. What is kept from SAGE is the architecture; what is not is Generals'
game design — every rule that engine baked in is a knob here.

> **0.2.0 is the first published release.** Until it lands on Maven Central, build it locally with
> `./gradlew publishToMavenLocal`, or let the plugin's **New Project** wire it up for you.

## Making a game

### With the plugin

Install **Duke Engine** in IntelliJ, then **File → New → Project → Duke Game**. You get a project
that already runs: two units, a light and a camera as `.duke` blocks, a couple of effects from the
kit, a `Main` that opens the 3D client, and a test that loads the whole thing headless. Nothing is
drawn from a model file, because a new project has no art yet and a template with no `Model` is
drawn as its `Geometry` — so the first thing you see is your own game.

Then open a `.duke` file and the plugin shows it as a form; open a map and it opens in 3D.

See [duke-plugin/README.md](duke-plugin/README.md).

### By hand

```kotlin
dependencies {
    // The version, once. Everything below comes from it.
    implementation(platform("uz.duke-engine:bom:0.2.0"))

    implementation("uz.duke-engine:client3d") // brings core, rts and game with it
    implementation("uz.duke-engine:kit")      // effects to start from, data only
}
```

Each module is its own artifact — depend on `core` alone and you get the genre-neutral engine and
nothing else. The BOM only says which versions go together, so a client from one release can never
meet a core from another.

```java
var game = DukeGame.create("My RTS")
        .loadUnits(DukeGame.STARTER_UNITS)
        .map(70, 45);

var you = game.addPlayer("USA", Color.CYAN);
var foe = game.addPlayer("China", Color.RED);
game.enemies(you, foe).money(you, 1500);

game.spawn("Barracks", you, 100, 360);
game.spawn("Tank", foe, 550, 100);

game.start(); // opens a window: select with LMB, order with RMB
```

## Two games, on purpose

Templates taken from one game fit that game by construction and prove nothing. So there are two,
written independently and deliberately unalike — and what the second one could **not** do is where
several of the engine's seams came from.

| | **Duke Dungeon** | **Duke Skirmish** |
|---|---|---|
| | a 3D roguelike | a small RTS |
| you are | one hero | two sides |
| the world | floors, storeys, stairs | one open field |
| creatures | spawn in rooms | are **bought** |
| you keep | loot, levels, skills, mana | money, build cost, build time |
| you win by | descending | destroying their base |

```
./gradlew :dungeon:run
./gradlew :skirmish:run
```

## Modules

```
dungeon, skirmish  →  client3d  →  game  →  rts  →  core
       └───────────→  kit
```

- **core** — the genre-neutral engine. Subsystems and the fixed-timestep loop,
  objects/templates/modules, the `.duke` data layer, spatial queries, pathfinding, lock-step
  networking, fog of war, scripting triggers. It knows nothing about any genre: no commands, no
  weapons, no economy.
- **rts** — the RTS on top of it: the command set, combat, production, economy, veterancy, power,
  transports, superweapons, the RTS vocabulary and the save format.
- **game** — the Unity-style API: the `DukeGame` facade, a built-in Swing 2D renderer, camera, unit
  selection, right-click orders, HUD, two-player lock-step multiplayer over TCP.
- **client3d** — the 3D client on jMonkeyEngine: glTF/Ogre model loading, skeletal animation,
  positional sound, an RTS camera, ray-picked selection, effects, menus and a minimap.
- **kit** — the starter set a game begins from: an effects library, 27 effects in eight groups with
  the particle art they are drawn with. Data only, no code. A game links one and replaces it by
  writing its own of the same name.
- **bom** — every module at one version, so a game writes the version once.
- **dungeon**, **skirmish** — the two games above. Not published: they are played, not depended on.

`core` never imports `rts`. Building a game that is not an RTS means depending on `core` alone and
supplying your own commands, modules and vocabulary — see **Extending** below.

## Architecture

SAGE's core split is preserved:

| SAGE concept            | duke-engine                             |
|-------------------------|-----------------------------------------|
| `SubsystemInterface`    | `uz.dukeengine.core.SubsystemInterface` |
| `SubsystemInterfaceList`| `uz.dukeengine.core.SubsystemList`      |
| `GameEngine` (main loop)| `uz.dukeengine.core.GameEngine`         |
| `GameLogic` (simulation)| `uz.dukeengine.core.GameLogic`          |
| `GameClient` (present)  | `uz.dukeengine.core.GameClient`         |

### The loop

The simulation advances in fixed **30 Hz logic frames** — each frame is a fixed slice of game time,
which is what makes the simulation deterministic and replayable, and is the unit lock-step
networking synchronises on.

`GameEngine.execute()` uses a fixed-timestep accumulator: the logic steps at exactly 30 Hz (draining
banked time) while the client renders once per loop, up to `maxFps` (default 45). Game time
therefore tracks wall time regardless of render rate. This implements the decoupling SAGE's own
`update()` flagged as a `@todo` but never shipped.

## Extending — what a game supplies

The engine deliberately ships no game content. These seams let a game fill in its own, and the `rts`
module is the worked example of each:

| Seam | core | rts |
|---|---|---|
| Commands | `Command` marker + `MessageStream` | sealed `GameMessage` (Move/Attack/Stop/Queue/Rally) |
| Wire format | `PacketCodec` plug on `SocketTransport` | `CommandCodec` |
| Behaviour modules | `ModuleFactory.withDefaults()` — `ActiveBody`, `MoveUpdate` | `RtsModules` — weapons, production, economy, … |
| Players | `Player` (identity, diplomacy) + `PlayerList(PlayerFactory)` | `RtsPlayer` (money, upgrades) |
| Classification | `Kind`, interned by name | `RtsKinds` (`STRUCTURE`, `INFANTRY`, …) |
| Templates | one interface per thing a template may have — `Solid`, `Sighted`, `Classified`, `Titled`, `Drawn` | `RtsTemplate`: an `Object` block with a price and a look |

Command hierarchies are sealed on purpose, so a new command is a compile error at every dispatch
site until it is handled — and sealed types cannot cross a module boundary, which is exactly why the
engine holds only the marker.

The full table of seams, and the rule for which module a thing belongs in, is in
[CLAUDE.md](CLAUDE.md).

## Build

Requires nothing pre-installed beyond the wrapper — Gradle provisions the **Java 25** toolchain.

```
./gradlew build                  # compile + test
./gradlew publishToMavenLocal    # the engine, for a game on this machine
./gradlew :dungeon:run           # Duke Dungeon
./gradlew :skirmish:run          # Duke Skirmish
./gradlew :dungeon:newMap --args="crypt 42"   # a new map from a seed; fill it on the Map tab
```

The IntelliJ plugin is a build of its own:

```
cd duke-plugin && ./gradlew test runIde
```

## Status

**1,582 tests** across the engine and **54** in the plugin, all green. `core`, `rts`, `game`,
`client3d` and `kit` publish to Maven Central under `uz.duke-engine`; the two games do not.

Known gaps: multiplayer is two players only and does not compare checksums live; save/load is not
wired into any UI; per-module in-flight state (move goals, reload counters, build queues) is not yet
serialised; maps are edited in the plugin and nowhere else.

## Contributing

Issues and pull requests are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md) — it covers how
to build, how the modules are allowed to depend on each other, and the handful of rules that are not
negotiable (determinism above all).

## License

The code is released under the [MIT License](LICENSE).

The models, animations, sounds, fonts and pictures the games ship are **not** covered by it: each
keeps its own terms, listed in [CREDITS.md](CREDITS.md) and kept in a `License.txt` beside the
files.

*Command & Conquer* and *Generals* are trademarks of Electronic Arts. This project is not affiliated
with EA; the original C++ source (EA's GPL release) is used purely as a reference for behaviour and
structure.
