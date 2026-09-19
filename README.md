# duke-engine

A from-scratch reimplementation of the **SAGE engine** (the engine behind
*Command & Conquer: Generals — Zero Hour*) in modern Java — split into a
genre-neutral engine and an RTS built on top of it, with a **Unity-style API
for building a game in a few lines**:

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

The core is a faithful but Java-idiomatic port of SAGE's architecture: a
subsystem framework driving a deterministic, lock-step simulation cleanly
separated from presentation. The `rts` module adds the RTS; the `game` module
layers the easy API on top.

The original C++ source (EA's GPL release) lives at
`../CnC_Generals_Zero_Hour` and is used purely as a reference for behaviour and
structure.

## Modules

```
studio  →  client3d  →  game  →  rts  →  core
```

- **core** — the genre-neutral engine. Subsystems and the fixed-timestep loop,
  objects/templates/modules, the .duke data layer, spatial queries, pathfinding,
  lock-step networking, fog of war, scripting triggers. It knows nothing about
  any particular game: no commands, no weapons, no economy.
- **rts** — the RTS on top of it: the command set, combat, production, economy,
  veterancy, power, transports, superweapons, the RTS classification vocabulary
  and the save format.
- **game** — the Unity-style API: the `DukeGame` facade, a built-in Swing 2D
  renderer, camera, unit selection, right-click orders, HUD, two-player
  lock-step multiplayer over TCP. Zero external dependencies.
- **client3d** — the full 3D client on jMonkeyEngine: glTF/Ogre **model
  loading**, skeletal **animation**, positional **sound**, RTS camera,
  ray-picked selection, health bars, menus and a minimap. Assets bind
  Unity-style via `Visuals`; units without art get clean primitives.
- **studio** — Duke Studio, the Swing editor: author factions, units, maps and
  scripts, press Play, export a standalone cross-platform game.
- **kit** — the starter set a game begins from: the effects library, 27 effects
  in eight groups with the particle art they are drawn with. A game links them,
  and replaces one by writing its own of the same name.
- **dungeon** — Duke Dungeon, the first game written on the engine: a 3D
  roguelike whose floors are drawn from a seed, or a **stage** — one floor
  frozen into a text file and played the same way every time.
- **sandbox** / **sandbox3d** — the 2D and 3D demo skirmishes, ~70 lines each.

`core` never imports `rts`. Building a game that is not an RTS means depending
on `core` alone and supplying your own commands, modules and vocabulary — see
**Extending** below.

## Architecture

SAGE's core split is preserved:

| SAGE concept            | duke-engine                         |
|-------------------------|-------------------------------------|
| `SubsystemInterface`    | `uz.duke.core.SubsystemInterface`   |
| `SubsystemInterfaceList`| `uz.duke.core.SubsystemList`        |
| `GameEngine` (main loop)| `uz.duke.core.GameEngine`           |
| `GameLogic` (simulation)| `uz.duke.core.GameLogic`            |
| `GameClient` (present)  | `uz.duke.core.GameClient`           |

### The loop

The simulation advances in fixed **30 Hz logic frames** — each frame is a fixed
slice of game time, which is what makes the simulation deterministic and
replayable, and is the unit lock-step networking synchronises on.

`GameEngine.execute()` uses a fixed-timestep accumulator: the logic steps at
exactly 30 Hz (draining banked time) while the client renders once per loop, up
to `maxFps` (default 45). Game time therefore tracks wall time regardless of
render rate. This implements the decoupling SAGE's own `update()` flagged as a
`@todo` but never shipped.

## Extending — what a game supplies

The engine deliberately ships no game content. Five seams let a game fill in
its own, and the `rts` module is the worked example of each:

| Seam | core | rts |
|---|---|---|
| Commands | `Command` marker + `MessageStream` | sealed `GameMessage` (Move/Attack/Stop/Queue/Rally) |
| Wire format | `PacketCodec` plug on `SocketTransport` | `CommandCodec` |
| Behaviour modules | `ModuleFactory.withDefaults()` — `ActiveBody`, `MoveUpdate` | `RtsModules` — weapons, production, economy, … |
| Players | `Player` (identity, diplomacy) + `PlayerList(PlayerFactory)` | `RtsPlayer` (money, upgrades) |
| Classification | `Kind`, interned by name | `RtsKinds` (`STRUCTURE`, `INFANTRY`, …) |

Command hierarchies are sealed on purpose, so a new command is a compile error
at every dispatch site until it is handled — and sealed types cannot cross a
module boundary, which is exactly why the engine holds only the marker.

## Build

Requires nothing pre-installed beyond the wrapper — Gradle provisions the
**Java 25** toolchain.

```
./gradlew build            # compile + test (165 tests)
./gradlew :studio:run      # the Duke Studio editor
./gradlew :sandbox:run     # the 2D demo skirmish
./gradlew :sandbox3d:run   # the 3D demo skirmish
./gradlew :dungeon:run     # Duke Dungeon
./gradlew :dungeon:newMap --args="crypt 42"  # a new stage from a seed; fill it on the Map tab in the IDE
```

## Implemented

**core** — genre-neutral:

- **Subsystem framework + main loop** — `SubsystemInterface`, `SubsystemList`,
  `GameEngine` (fixed-timestep 30Hz logic / capped render).
- **`NameKeyGenerator`** — string→key interning.
- **`.duke` data layer** (`uz.duke.core.data`) — `DukeText` reads the syntax
  (one word opens a block, `Key = value`, `[a, b]` lists, `Geometry = Cylinder`
  with its fields under it, `Modules = [` a block for each `]`); `Binder` makes
  each block the record its word names, every line of it a component by name.
- **Math** (`uz.duke.core.math`) — `Coord3D` / `Coord2D` / `ICoord3D`.
- **Thing/Object/Module system** — `ThingTemplate`, `ThingFactory`,
  `GameObject`, composable `Module`s, `ModuleFactory`, `Kind` classification.
  `GameLogic` owns objects, ticks them, reaps the dead.
- **Data-driven objects** — `ThingTemplateLoader` loads `Object` blocks from
  `.duke` text into templates, each module a block named by its class in the
  `Modules = [ … ]` list; a game adds block types of its own
  (`loader.type(Monster.class)`).
- **Command pipeline** — `Command` + `MessageStream`; commands are queued and
  drained deterministically at the start of each frame.
- **Movement** — `MoveUpdate` steers an object toward a goal at its configured
  speed, with an optional turn rate, on the fixed logic clock.
- **Health & damage** — `BodyModule`/`ActiveBody`, `DamageType`, `Armor`.
- **Players** — `Player` / `PlayerList` with `Relationship` diplomacy.
- **Spatial queries** — `PartitionManager` + composable `PartitionFilter`.
- **Pathfinding** — deterministic A* (`Pathfinder`/`PathGrid`/`Path`);
  `MapLoader` builds a grid from ASCII text.
- **Lock-step networking** — `LockstepScheduler`/`LockstepDriver` gate each
  frame until every player's commands have arrived; `Transport` +
  `LoopbackTransport`/`SocketTransport` (real TCP, no external dependency).
- **Desync detection** — `GameLogic.checksum()` hashes the whole world each
  frame (SAGE's `VERIFY_CRC`).
- **Fog of war** — vision ranges; `canSee` / `getVisibleObjects`, allies share
  sight.
- **Scripting** — `Trigger` + `ScriptEngine` for victory/defeat and map events.
- **Rendering seam** — `Renderer` + `RenderingGameClient`.

**rts** — the RTS on top:

- **Combat** — `WeaponUpdate`: reload cycle, range gating, splash, never fires
  on allies, typed damage against armor.
- **Economy & production** — build cost/time, `ProductionUpdate` (queue, charge,
  rally point), `SupplyModule` piles + `HarvestUpdate` gather loop.
- **Veterancy** — `ExperienceModule` + `VeterancyLevel`; kills earn XP, ranks
  raise damage and heal to full.
- **Power grid** — `PowerModule` + `PowerGrid`; production stalls when a base is
  under-powered.
- **Upgrades** — `Upgrade` + `RtsSimulation.purchaseUpgrade`, player-wide bonuses.
- **Status effects** — `StatusUpdate` applying DISABLED/SLOWED for a duration.
- **Garrison / transport** — `ContainModule`.
- **Special powers** — `SpecialPowerModule`, a rechargeable area-damage superweapon.
- **Commands & wire format** — sealed `GameMessage` + `CommandCodec`.
- **Save / load** — `GameSnapshot` serializes the world to text and restores it
  to a checksum-identical state.
- **Text rendering** — `AsciiRenderer`, a fog-aware top-down minimap.

## Status

The engine is split and green at 165 tests: a genre-neutral core, an RTS on top
of it, lock-step multiplayer over real TCP, save/load, a 3D client and an
editor that exports standalone games.

Known gaps: exported games bake in one map and faction set instead of offering
the skirmish menu; multiplayer is two players only and does not yet compare
checksums live; save/load is not wired into any UI; per-module in-flight state
(move goals, reload counters, build queues) is not yet serialized.

## License

The code is released under the [MIT License](LICENSE).

The models, animations, sounds, fonts and pictures the games ship are not
covered by it: each keeps its own terms, listed in [CREDITS.md](CREDITS.md) and
kept in a `License.txt` beside the files.
