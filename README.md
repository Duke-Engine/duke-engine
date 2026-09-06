# duke-engine

A from-scratch reimplementation of the **SAGE engine** (the engine behind
*Command & Conquer: Generals — Zero Hour*) in modern Java — now with a
**Unity-style API for building RTS games in a few lines**:

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
separated from presentation. The `game` module layers the easy API on top.

The original C++ source (EA's GPL release) lives at
`../CnC_Generals_Zero_Hour` and is used purely as a reference for behaviour and
structure.

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

## Modules

- **core** — the engine framework and all simulation systems (no rendering).
- **game** — the Unity-style API: `DukeGame` facade, built-in Swing 2D
  renderer, camera, unit selection, right-click orders, HUD. Zero external
  dependencies.
- **client3d** — the full 3D client on jMonkeyEngine: glTF/Ogre **model
  loading**, skeletal **animation** (idle/walk/attack per unit), positional
  **sound** (fire/death), RTS camera, ray-picked selection and orders, health
  bars. Assets bind Unity-style via `Visuals`; units without art get clean
  primitives:

  ```java
  var visuals = Visuals.create()
          .unit("Tank", u -> u.model("Models/tank.gltf").scale(1.5f)
                  .idle("Idle").walk("Drive").attack("Fire")
                  .fireSound("Sounds/cannon.ogg").dieSound("Sounds/boom.ogg"));
  Duke3D.launch(game, visuals);
  ```
- **sandbox** — the 2D skirmish (~40 lines). `./gradlew :sandbox:run`
- **sandbox3d** — the 3D skirmish with an animated demo model (~60 lines).
  `./gradlew :sandbox3d:run`

## Build

Requires nothing pre-installed beyond the wrapper — Gradle provisions the
**Java 25** toolchain.

```
./gradlew build          # compile + test (87 tests)
./gradlew :sandbox:run   # run the integrated demo scenario
```

## Implemented

- **Subsystem framework + main loop** — `SubsystemInterface`, `SubsystemList`,
  `GameEngine` (fixed-timestep 30Hz logic / capped render).
- **`NameKeyGenerator`** — string→key interning.
- **INI data layer** (`uz.duke.core.ini`) — faithful tokeniser, field parse
  tables, scan helpers.
- **Math** (`uz.duke.core.math`) — `Coord3D` / `Coord2D` / `ICoord3D`.
- **Thing/Object/Module system** — `ThingTemplate`, `ThingFactory`,
  `GameObject`, composable `Module`s (`ActiveBody`, `AIUpdate`, …),
  `ModuleFactory`. `GameLogic` owns objects, ticks them, reaps the dead.
- **Data-driven objects** — `ThingTemplateLoader` loads `Object` INI blocks,
  including nested module sub-blocks, into templates.
- **Command pipeline** — sealed `GameMessage` hierarchy + `MessageStream`;
  `GameLogic.issueCommand` queues commands, drained deterministically each frame
  to `onCommand`.
- **Movement** — `AIUpdate` steers an object toward a goal at its configured
  speed, on the fixed logic clock.
- **Players** — `Player` / `PlayerList` with `Relationship` diplomacy and money.
- **Combat** — `WeaponUpdate` deals damage on a reload cycle, range-gated, never
  hits allies, and auto-acquires the nearest enemy when idle.
- **Spatial queries** — `PartitionManager` + composable `PartitionFilter`
  (objects-in-range, closest-matching).
- **Lock-step core** — `LockstepScheduler` gates each frame until every player's
  commands have arrived; backs `GameEngine.isLogicFrameReady()`.
- **Pathfinding** — deterministic A* (`Pathfinder`/`PathGrid`/`Path`); `AIUpdate`
  follows the resulting waypoints, routing around obstacles. `MapLoader` builds a
  grid from ASCII text.
- **Economy & production** — `ThingTemplate` build cost/time; `ProductionUpdate`
  queues units, charges the owner, builds over time, spawns them.
- **Veterancy** — `ExperienceModule` + `VeterancyLevel`; units earn XP from kills,
  rank up, and deal more damage. `AutoHealUpdate` regenerates health over time.
- **Desync detection** — `GameLogic.checksum()` hashes the whole world each frame
  (SAGE's `VERIFY_CRC`), the lock-step desync check.
- **Multiplayer core** — `LockstepDriver` + `CommandPacket`: per-peer scheduling,
  frame-delay lookahead, transport-agnostic. Two peers exchanging only commands
  stay bit-identical (proven by test).
- **Upgrades** — `Upgrade` + `GameLogic.purchaseUpgrade`; player-wide combat
  bonuses.
- **Status effects** — `ObjectStatus` (DISABLED/SLOWED) + timed `StatusUpdate`;
  honoured by movement and combat.
- **Power grid** — `PowerModule`; production stalls when a base is under-powered.
- **Save / load** — `GameSnapshot` serializes the world to text and restores it
  to a checksum-identical state.
- **Networking** — `CommandCodec` (wire format) + `Transport`
  (`LoopbackTransport` in-process, `SocketTransport` real TCP). Lock-step over
  actual sockets, no external dependency.
- **Fog of war** — vision ranges; `GameLogic.canSee` / `getVisibleObjects`,
  allies share sight.
- **Scripting** — `Trigger` + `ScriptEngine` for victory/defeat and map events.
- **Special powers** — `SpecialPowerModule`: rechargeable area-damage superweapon.
- **Damage types & armor** — `DamageType` + `Armor`; weapons deal typed damage,
  bodies resist/are weak to it per type.
- **Garrison / transport** — `ContainModule`; loaded units go idle and untargetable
  until unloaded.
- **Harvesting economy** — `SupplyModule` resource piles + `HarvestUpdate` gather
  loop that turns resources into money.
- **Rendering** — `Renderer` seam + `AsciiRenderer` (top-down text minimap,
  fog-of-war aware) + `RenderingGameClient`. A 3D backend (jME) is a drop-in
  alternative `Renderer`.

The `sandbox` demo runs an integrated scenario end to end: a barracks spends
money to build soldiers who auto-engage and destroy an advancing enemy — and
prints the world as a text minimap.

## Status

Every major SAGE category is implemented (133 tests): the deterministic
simulation, lock-step multiplayer over real TCP, save/load, and a (text)
rendering backend. Remaining work is polish: a real 3D `Renderer` (jME — needs
a dependency and a display), richer binary map formats, and per-module
in-flight save/load fidelity.
