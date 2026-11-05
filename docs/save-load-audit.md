# Save/load audit

What a saved world must hold so that, loaded, it goes on frame for frame as the
world it was saved from would have (PLAN-generals 5.1). Written before the
module save layer, from the code at `9dc7d47`/`6af61c2e`; the last column says
what 5.1 does with each.

## 1. What a snapshot held (`rts/save/GameSnapshot`, before 5.1)

| Line | Holds |
|---|---|
| `FRAME`, `NEXTID`, `RANDOM` | the frame, the next id, where the random numbers stand |
| `REVEALED`, `SEEN` | the players the map is revealed to / marked seen for |
| `DECK` | each deck: floor, corners, open |
| `PLAYER` | index, name, money, weapon-damage bonus, upgrades, granted words, computer, earned, spent |
| `SIGHT` | each side's seen cells and what is in sight |
| `OBJECT` | id, template, owner, place, facing, health, statuses, condition words, most health, own sight, targetable-from, floor, span, producer, own fog range, relation overrides |

Every module was made afresh from its template on a load. The class comment
said so: "per-module in-flight state … is not yet serialized".

## 2. What the checksum covers (`core/Checksum`, `GameLogic.checksum`)

Each thing in creation order: id, owner, place, facing, health; and where set:
the body's damage scale, own sight, own fog range, targetable-from, producer,
floor, relation overrides, statuses. Then the players the map is revealed to and
marked seen for, the scenery pieces taken away, the closed decks, each player's
own sum (`RtsPlayer`: money, upgrades, granted words, bonuses), the random
numbers where they have been drawn, and the game's own parts (`checksumAlso`).

Two of these the old snapshot did not save: **the body's damage scale** and
**the scenery taken away**. A load of a world where either was set summed
differently at once.

The checksum holds no module state. A module's state shows in the sum only once
it moves, hurts or makes something — so the save tests below play both worlds on
and compare the whole save text, not only the sum.

## 3. Module types

Every `Module` subclass in the engine. "Template" = built from a `Data` block;
"added" = put on a thing while the match runs (needs a reviver to be made again).

### core (`ModuleFactory.withDefaults`)

| Module | How | State outside the template | 5.1 |
|---|---|---|---|
| `ActiveBody` (+ `BodyModule`) | template | health, most health, the killing blow, damage scale, least health, sole healer and until when | Saveable |
| `MoveUpdate` (+ `Route`, `Progress`, `LinkCrossing`, `GivingWay`, `GaitDrive`, `RouteWalk`, `Steering`) | template | the walk: destination, waypoints, path and index, the given locomotor, speed set, steering, band, give-way timers, gait speed, link crossing plan | Saveable; refused while a `Steering` walks it, for a band round a thing that left without dying, and for a link the world no longer has |
| `FlyUpdate` | template | speed and turn rate as set, velocity, goal, followed thing, circle, arrived, going wide, facing | Saveable |
| `ScriptedMove` | added | from, to, frames, arc, frame, done; the story's `onLanded` callback | Saveable + reviver; refused while it still has a callback to call |

### combat (`CombatModules`)

| Module | How | State | 5.1 |
|---|---|---|---|
| `WeaponUpdate` (+ `Loadout`, `Clip`, `Engagement`, `Firing`, `TargetScan`) | template | target, forced, ground target, order source, point attack, shots left, leeching; each clip's rounds, wait, reloading; locked slot and its lock, set in use; wind-up; last fired at; when it looks next | Saveable; refused while a game `PointAttack` is in hand |
| `PursueUpdate` | template | where it took its target from, where it planned for, going there, plans | Saveable |
| `ExperienceModule` | template | experience, rung | Saveable |
| `AutoHealUpdate` | template, or added (an upgrade) | its rate | Saveable + reviver (5.2) |
| `StatusUpdate` | template | each timed status and its frames left | Saveable |
| `AuraUpdate` | template | whom it holds, when it looks next | Saveable |

### rts (`RtsModules`)

| Module | How | State | 5.1 |
|---|---|---|---|
| `ProductionUpdate` (+ `Job`, door states) | template | the queue (unit or research, progress, paid, reservation id), next job id, units left in a burst, rally point, exit wait, each door's state and frames | Saveable; each job's reservation token written by its `ProductionReservation` (`saveToken`/`loadToken`; the defaults refuse) |
| `PowerModule` | template, or added (a power cut, an upgrade) | its figures, its bonus | Saveable + reviver (5.2) |
| `CapacityGate` | template | nothing | @Stateless |
| `SpecialPowerModule` | template | cooldown | Saveable |
| `ContainModule` | template | passengers in order, shared network, whether passengers fire | Saveable; a shared network's passengers are the RTS world's |
| `SupplyModule` (+ `Docking`) | template | what is left; its dock: who is in, free from, places, who has come, closed | Saveable |
| `SupplyDepot` (+ `Docking`) | template | its dock | Saveable |
| `HarvestUpdate` | template | what it is doing, its place, pile, depot, load, waits and timers, search range | Saveable |
| `CrushUpdate` | template | where it stood last frame | Saveable |
| `Crushable` | template | nothing | @Stateless |
| `ToppleUpdate` | template | its fall: state, side, speed, how far, sunk, ground, who pushed it | Saveable (the lean is drawn again from the fall) |
| `AttackMoveOrder` | added | destination, target, where it took it on, chase, engaged at, next look, over | Saveable, made again by its reviver |
| `GuardOrder` | added | place, charge, mode, last place, target, chase, engaged at, next look, returning | Saveable, made again by its reviver |
| `BuildOrder` | added | what, where, facing, cost, side, its walk, its site, at work | Saveable, made again by its reviver |
| `ConstructionSite` | added | builder, cost, work done (frames come from the template) | Saveable, made again by its reviver |
| `Selling.Coming` | added | when sold, the share left | Saveable, made again by its reviver |

### game

| Module | How | State | 5.1 |
|---|---|---|---|
| `ScriptModule` | template | started, broken, and its `UnitScript`'s fields | Saveable; its script must be Saveable or @Stateless itself, or the save is refused by the script's name |

### rpg (the library's own; a game registers them as it registers its others)

| Module | How | State outside the template | 7.1 |
|---|---|---|---|
| `ArrowUpdate` | template | whom it chases, its shooter, the figures it carries (damage and its kind, step, blast, stun, height, width), frames flown, travel left, where it was loosed from, whom a wave has hurt | Saveable |
| `FallingUpdate`, `MendingUpdate` | template | caller or patient, the figure, frames left, where it was called from | Saveable |
| `SummoningUpdate` | template | caller, creature, how long it lasts, experience share, level, frames to open | Saveable |
| `Swing` | template | the frame it last struck | Saveable |
| `Bow`, `EyesOnly`, `FountainUpdate` | template | nothing | @Stateless |
| `GrowableBody` | template | its body line, ceiling, health, armour | Saveable |
| `Recovery` | template | its rate and what is carried toward the next point | Saveable |
| `GateUpdate` | template | whether it is opened | Saveable |
| `GroundItem` | template | what it holds, whole: every field of the item, the skills it lends and the affixes it rolled (`Loot.save`, `Skill.save`), so a save needs no catalogue | Saveable |
| `SkillBook` (+ `ManaPool`, `Buffs`, `Passives`, `Mount`, `CastMarks`, `Blows`, `Shots`, `Blasts`, `Moves`, `Charge`, `Allies`) | template | cooldowns, last cast, mana and its carry, each buff lent and the boost and guard held, ranks and souls, the scale, floor and flinch it holds, the mount, the marks, a shot being drawn, a whirlwind, a charge with whom it caught, whom it summoned. Skills are kept by their place in the book; a book loaded where its template lists others is refused | Saveable |
| `AttackBonus`, `AttackSpeed`, `CriticalStrike`, `LevelBonus`, `Summoned` | added | their figures | Saveable + reviver (`RpgModules.registerRevivers`) |
| `LootDrop`, `KeyDrop`, `ItemErrand`, `Mission.Counted` | added | `LootDrop`: chest, tier, boss; `KeyDrop`: chest, floor owner; `ItemErrand`: what it was sent to do, where, to what, the slot, how near it has got; `Counted`: nothing | Saveable (`Counted` @Stateless) + reviver the game registers with what it is made again with: the loot table, the bag of a hero, the errand rules, the floor's `Mission` |

What the revivers are given is the game's: a `LootTable`, a `LootBag` found by hero, `ItemErrand.Rules`, a `Mission`
(`Mission.restored` makes one again without telling the counted; `Mission.save`/`load` hold the dead counted and
whether the key is laid). `SummoningUpdate`, `Bow`, `FountainUpdate`, `ArrowUpdate`, `FallingUpdate`, `Swing`
and `SkillBook` are built by the game's own builders, with the seal, rules and skills it gives them, before the load.

### Game-defined modules

A game's own modules (duke-generals' ~hundreds under `content/object/`) are
registered on the same `ModuleFactory`. The engine cannot list them; the
contract lets the game declare each (§5), and `ModuleFactory.unsaved()` lists
every registered module that is neither — what a game's test asserts empty.

## 4. World state outside modules

| State | Where | In checksum | In old save | 5.1 |
|---|---|---|---|---|
| players' relationships | `Player.relationships` | no (decides targeting) | no | saved |
| scenery taken away | `Scenery.removed` | yes | no | saved |
| body damage scale | `BodyModule` | yes | no | saved with the body |
| a dead thing kept in the world (`died`) | `GameObject` | no | no | saved |
| inside a hold, seeing out | `GameObject.contained`, `seesOut` | no | no | saved |
| commands queued for the next frame | `MessageStream` | no | no | saved through the game's `PacketCodec` |
| who is awake, where sleep is on | `Sleepers.awake` | no | no | saved |
| waypoints still to walk, units just sent | `RtsSimulation.waypoints`, `sentNow`, `seenMoving` | no | no | saved |
| shared holds' passengers | `RtsSimulation.sharedHolds` | no | no | saved |
| caps set at run time | `RtsSimulation.caps` | no | no | saved |
| a side's named bonuses, price changes | `RtsPlayer.bonuses`, `priceChanges` | bonuses yes | weapon damage only | saved |
| where movers were sent beside a thing | `StandingPlaces.sentBeside` | no | no | saved (`GameLogic.saveMovers`) |
| movers' own blocks of cells | `PathGrid.movers` | no | no | saved (`MoverCells`) |
| path budget used this frame / last | `Routes.cellsThisFrame/LastFrame` | no | no | saved; the obstacle version written as the next laying leaves it |
| triggers | `ScriptEngine` | no | no | the game's (5.2): its triggers are code |
| the game's own state | `checksumAlso` parts | yes | no | the game's (5.2) |

Derived, not saved: the obstacle layer and its sectors and zones (laid again
from the things), the roster's indices and partition, the sight cells' chunks.

## 5. The contract

- `core.save.Saveable` — `save(SaveOut)`, `load(SaveIn)`; `Saveable.Reviver`
  makes again a module added while the match ran.
- `core.save.Stateless` — a module class declared to hold nothing a save needs.
  Not inherited.
- `ModuleStates.save(thing, factory)` refuses (`UnsavedModuleException`, naming
  the class and the thing) a module that is neither, an added module with no
  reviver, and a subclass of a Saveable that keeps fields its inherited save
  does not write. `ModuleStates.load` refuses a module its template no longer
  makes where the save says, and any value read differently from how it was
  written.
- `ModuleFactory.revives(type, reviver)`, `ModuleFactory.unsaved()`.

## 6. Status

Every engine module of core, combat, rts, game and rpg is Saveable or @Stateless
(`EngineModulesAreAllSavedTest`: `RtsModules.withDefaults().unsaved()` is
empty, and `RpgModulesAreAllSavedTest` for the RPG library), and every module rts or core adds while a match runs has a reviver.
`GameSnapshot.VERSION` is **3** (2 before the game's parts); a text of any other version (version 1 is the
unmarked one before) is refused before the world is touched.

Each family is played on after a load by `rts/src/test/.../save/*ResumeTest`:
the two worlds sum the same every frame and save the same text at the end, and
(`resumesOnlyWithItsModules`) a load with the modules made afresh parts from the
played world — so each family's state is shown to matter.

Still open, and loud:

- **rpg modules** (7.1): no longer refused. Every module of the library is Saveable or @Stateless
  (`RpgModulesAreAllSavedTest`) and `rpg/src/test/.../save` plays each family on after a load, in a plain RPG world
  (`RpgSaveCheck`: things and modules as `ModuleStates` writes them, the world's own lines), since `GameSnapshot` is
  the RTS's. What stays the game's, as a save part (`GameLogic.saveAlso`): the hero's progress and bag (`HeroProgress`,
  `LootBag`, `QuestLog`), the wiring a load expects set before it (`Passives.lendFrom(bag)`), and the rules and tables
  the revivers close over. Refusals that belong to modules outside the library stay and name them: a hero walked by a
  `Steering` (core's `MoveUpdate`, `action.Stride`), a `WeaponUpdate` holding a game `PointAttack` (combat), and a push
  still in the air, whose `ScriptedMove` has a landing the book hears.
- **Refusals that stand**: a `ScriptedMove` whose landing the story hears; a
  mover walked by a `Steering` (`action.Stride` too); a band round a thing that
  left without dying; a `WeaponUpdate` holding a game `PointAttack`; a factory
  job whose `ProductionReservation` does not write its token; a `BuildOrder`,
  site or sale made with placement or sell rules other than the world's.
- **The game's own** (5.2): what it keeps outside the engine it gives as save parts (`GameLogic.saveAlso`, `DukeGame.saveAlso`; `GAME` lines, version 3; the engine game's own two, `duke.driver` — who has had things and who is beaten — and `duke.camera`, are given by `DukeGame`); its modules (`ModuleFactory.unsaved()` in its
  test), its `UnitScript`s, its `ProductionReservation` tokens, its triggers
  (`GameLogic.reset` clears them on a load), its `checksumAlso` parts, and the
  rules a load expects set the same before it (templates, armoury, placement,
  sell and guard rules, obstacle rules, path budget, traversal links, pile rule,
  sleep rule, terrain changed at run time, caps set by script).
- **Not checksummed**: module state is not in `GameLogic.checksum`; the sum is
  unchanged, so recordings and lock-step stay as they were. A desync of module
  state shows once it moves something.

## 7. Drawing-only state, kept out

`GameObject` pitch, roll, lift, yaw, corners, keeps-own-height, drawn-as,
wears-colours-of, opacity, fade, scale, flash, caption, without-world, ambient,
mount, busy; `Spectacle` beams, riding effects and streams. A module whose state
the picture follows draws it again from that state on a load (`ToppleUpdate`).
A game module that set any of these must set it again in its own `load`.
