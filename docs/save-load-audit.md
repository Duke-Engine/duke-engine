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
| `MoveUpdate` (+ `Route`, `Progress`, `LinkCrossing`, `GivingWay`, `GaitDrive`, `RouteWalk`, `Steering`) | template | the walk: destination, waypoints, path and index, the given locomotor, speed set, steering, band, give-way timers, gait speed, link crossing plan | see §6 |
| `FlyUpdate` | template | speed and turn rate as set, velocity, goal, followed thing, circle, arrived, going wide, facing | Saveable |
| `ScriptedMove` | added | from, to, frames, arc, frame, done; the story's `onLanded` callback | Saveable + reviver; refused while it still has a callback to call |

### combat (`CombatModules`)

| Module | How | State | 5.1 |
|---|---|---|---|
| `WeaponUpdate` (+ `Loadout`, `Clip`, `Engagement`, `Firing`, `TargetScan`) | template | target, forced, ground target, order source, point attack, shots left, leeching; each clip's rounds, wait, reloading; locked slot and its lock, set in use; wind-up; last fired at; when it looks next | see §6 |
| `PursueUpdate` | template | where it took its target from, where it planned for, going there, plans | Saveable |
| `ExperienceModule` | template | experience, rung | Saveable |
| `AutoHealUpdate` | template | nothing | @Stateless |
| `StatusUpdate` | template | each timed status and its frames left | Saveable |
| `AuraUpdate` | template | whom it holds, when it looks next | Saveable |

### rts (`RtsModules`)

| Module | How | State | 5.1 |
|---|---|---|---|
| `ProductionUpdate` (+ `Job`, door states) | template | the queue (unit or research, progress, paid, reservation id), next job id, units left in a burst, rally point, exit wait, each door's state and frames | see §6 |
| `PowerModule` | template | its bonus | Saveable |
| `CapacityGate` | template | nothing | @Stateless |
| `SpecialPowerModule` | template | cooldown | Saveable |
| `ContainModule` | template | passengers in order, shared network, whether passengers fire | see §6 |
| `SupplyModule` (+ `Docking`) | template | what is left; its dock: who is in, free from, places, who has come, closed | Saveable |
| `SupplyDepot` (+ `Docking`) | template | its dock | Saveable |
| `HarvestUpdate` | template | what it is doing, its place, pile, depot, load, waits and timers, search range | see §6 |
| `CrushUpdate` | template | where it stood last frame | Saveable |
| `Crushable` | template | nothing | @Stateless |
| `ToppleUpdate` | template | its fall: state, side, speed, how far, sunk, ground, who pushed it | Saveable (the lean is drawn again from the fall) |
| `AttackMoveOrder` | added | destination, target, where it took it on, chase, engaged at, next look, over | see §6 |
| `GuardOrder` | added | place, charge, mode, last place, target, chase, engaged at, next look, returning | see §6 |
| `BuildOrder` | added | what, where, facing, cost, side, its walk, its site, at work | see §6 |
| `ConstructionSite` | added | builder, cost, frames, work done | see §6 |
| `Selling.Coming` | added | when sold, the share left | see §6 |

### game

| Module | How | State | 5.1 |
|---|---|---|---|
| `ScriptModule` | template | started, broken, and its `UnitScript`'s fields | Saveable; its script must be Saveable or @Stateless itself, or the save is refused by the script's name |

### rpg

`ArrowUpdate`, `Bow`, `EyesOnly`, `FallingUpdate`, `Swing`, `AttackBonus`,
`AttackSpeed`, `CriticalStrike`, `FountainUpdate`, `GrowableBody`, `LevelBonus`,
`Recovery`, `GroundItem`, `ItemErrand`, `KeyDrop`, `LootDrop`, `GateUpdate`,
`Mission.Counted`, `MendingUpdate`, `SkillBook`, `Summoned`, `SummoningUpdate`.
No RPG world is saved (`GameSnapshot` saves an `RtsSimulation`, and duke-generals
builds without `rpg`), so 5.1 leaves them as they are: **not saveable, refused
by name** if a save ever meets one.

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
| where movers were sent beside a thing | `StandingPlaces.sentBeside` | no | no | see §6 |
| movers' own blocks of cells | `PathGrid.movers` | no | no | see §6 |
| path budget used this frame / last | `Routes.cellsThisFrame/LastFrame` | no | no | see §6 |
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

Filled in as each family lands; see the end of the 5.1 work.

## 7. Drawing-only state, kept out

`GameObject` pitch, roll, lift, yaw, corners, keeps-own-height, drawn-as,
wears-colours-of, opacity, fade, scale, flash, caption, without-world, ambient,
mount, busy; `Spectacle` beams, riding effects and streams. A module whose state
the picture follows draws it again from that state on a load (`ToppleUpdate`).
A game module that set any of these must set it again in its own `load`.
