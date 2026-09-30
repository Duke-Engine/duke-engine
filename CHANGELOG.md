# Changelog

Versions are `major.minor.patch`. While the major is 0, a minor may break what came before — and
when it does, this page says exactly what to change and how.

## 0.8.0

Unreleased. The release after 0.6.0: 0.7.0 is never published, and what its section below lists ships in this one, so a
game coming from 0.6.0 makes the changes both lists of what to change name.

The engine is being split so that an RTS and an RPG are two libraries on one layer they share: `combat`, what both
fight with, under `rts` and, in a later step, `rpg`. The split lands in steps, and this section grows with them.

### What to change

- **A new module, `combat`** (`uz.duke-engine:combat`), between `core` and `rts`. `rts` names it as part of its API, so
  a game that depends on `rts`, `game` or `client3d` has it without naming it, and the bom lists it. Twenty-five classes
  moved into it, each keeping its name — an import to change, and nothing else:

  | Was | Is |
  |---|---|
  | `uz.dukeengine.rts.module.` `Weapon`, `WeaponUpdate`, `WeaponSet`, `WeaponSlot`, `WeaponStatus`, `WeaponAim`, `WeaponHold`, `Clip`, `AimOffset`, `TargetRule`, `WeaponBonus`, `DamageModifier`, `RateOfFireModifier`, `Shot`, `ProjectileLauncher`, `StatusUpdate`, `AutoHealUpdate`, `ExperienceModule`, `PursueUpdate`, `Engaging`, `GuardRules`, `Errand` | `uz.dukeengine.combat.module.` the same |
  | `uz.dukeengine.rts.event.` `WeaponFired`, `ShotLanded` | `uz.dukeengine.combat.event.` the same |
  | `uz.dukeengine.rts.message.OrderSource` | `uz.dukeengine.combat.message.OrderSource` |

  A data file names a module by its class's simple name, so no `.duke` file changes, and a save reads as before.
- **Move, attack and stop are the orders every side gives**: `GameMessage.MoveTo`, `AttackObject` and `StopMoving` are
  `uz.dukeengine.combat.message.CombatOrder.MoveTo`, `AttackObject` and `StopMoving` — the same components and
  constructors, and the same lines on the wire (`MOVE,…`, `ATTACK,…`, `STOP,…`), so a recording reads as ever. A
  `switch` over `GameMessage` loses the three cases. An `RtsSimulation` of the game's own is told them by
  `onCombatOrder(CombatOrder)`, no longer by `onRtsCommand`; `CommandCodec.decodeCommand` answers a `Command`, a
  `CombatOrder` or a `GameMessage`, and `encodeCommand` takes either. `DukeGame.postCommand` takes them as it takes
  any command.
- `OrderListener.onOrder` is told a `Command` — a `CombatOrder` or a `GameMessage` — where it was told a `GameMessage`.
- Whether a computer plays a side is core's: `Player.isComputer()` and `setComputer(boolean)`, which `RtsPlayer` has by
  inheriting them, so a call reads as before.
- `RtsModules.MODULES` begins with `CombatModules.MODULES`: the words an RTS's files may use are what they were, and
  the aura's.
- **The views are core's**: the records a frame is shown by moved from `uz.dukeengine.game.view` to
  `uz.dukeengine.core.view`, each keeping its name — `UnitView`, `WorldSnapshot`, `CommandButton`, `CommandPress`,
  `RallyView`, `Turrets`, `MomentWords`, `AimAnswer`, `AimMark`, `BeamView`, `StreamView`, `EffectView`, `CameraView`,
  `FallingWeather` and `ViewRays`. An import to change.
- **A word order is an order every side gives**: `GameMessage.GameOrder` is `uz.dukeengine.combat.message.GameOrder`,
  with the same components and the same `ORDER,…` line on the wire. A `switch` over `GameMessage` loses the case.
  `RtsSimulation.onOrder` and `ordered` are gone: `DukeGame.onOrder` hears word orders as before, and a world run
  without the runtime hands them, with every command of no set it knows, to `GameLogic.onOtherCommands`. An
  `RtsSimulation`'s `onOtherCommand` is core's `GameLogic.onOtherCommand`.
- **The runtime is of no kind**: `game` depends on `combat`, not on `rts`, and runs whatever kind of game it is given.
  `DukeGame.create(title)` runs on the one kind on the classpath — the RTS library's, for a game with `rts` or
  `client3d` among its dependencies, so a game's `create` reads as before; one that depended on `game` alone adds
  `rts` — and `DukeGame.create(title, flavour)` on the one it names. The RTS's own API moved off `DukeGame` onto `uz.dukeengine.rts.RtsFlavour`, which the game makes and
  names or asks for (`game.flavour(RtsFlavour.class)`):

  | Was, on `DukeGame` | Is, on `RtsFlavour` |
  |---|---|
  | `onProduced`, `onConstructed`, `onPlaced`, `onResearched`, `onSold`, `addUpgrades` | the same, chaining on the flavour |
  | `money(player, amount)` | `money(player::getIndex, amount)` |
  | `getBuildOptions(factory)`, `BuildOption` | `getBuildOptions(factory, player)`, `RtsFlavour.BuildOption` |
  | `STARTER_UNITS` | `RtsFlavour.STARTER_UNITS` |
  | `getLogic()`, an `RtsSimulation` | `logic()`; `DukeGame.getLogic()` answers a `GameLogic` |
  | `postCommand(GameMessage)` | gone: `DukeGame.postCommand(Command)` takes it |

  A side of the RTS is `RtsPlayer.of(game.getLogic(), index)`, and the world's armoury `Armoury.of(game.getLogic())`.
  `UnitScript` keeps what any unit does: its `money()`, `productionQueue()`, `trainUnit()` and `setRallyPoint()` are
  gone, and a script on a factory asks its `ProductionUpdate` and its side's `RtsPlayer` itself. The sessions and the
  relay speak the classpath kind's wire, and their factories read as before.
- **A view of a side's sight cells shares their chunks**: `SightCells.View`'s components are `chunks` — each chunk of
  `SightCells.CHUNK` cells a side, null for one nothing has seen — and `everywhere`, where they were `states`. `at(x,
  y)` reads as before, `new View(cellSize, width, height, states)` still builds one from cells given row by row, and
  `states()` gives them back, a copy the size of the map.
- `PathGrid.setLevel` refuses a storey below -32768 or above 32767: a cell's storey is kept in two bytes.

### One seamless world

A world a thousand cells a side and more: what a frame costs follows what is awake and near, not the size of the
world; a route across it costs what its length does; what each side has seen is kept as small as what it walked, by the
simulation, and saved with it; and its ground is built as far as the camera sees. Each part is the game's to take; a
game that takes none plays, routes and draws every frame as before, which every test in the repository holds it to.

- **Things far from every waker sleep** (`Sleep`, `GameLogic.setSleep`, `DukeGame.sleep`): every so many frames, from
  where things stand and in the order they came in, the things farther than the rule's reach from every waker — the
  game's heroes — are put to sleep. A sleeper's modules do not run and it looks at nothing, and all of it stays as it
  was; a blow landing on it wakes it until the rule next decides, a thing marked destroyed leaves the same frame, and one
  that dies of no blow on the rule's next frame. The same frames on every machine.
- **A frame costs what is awake**: the partition's buckets hash apart (a diagonal of them hashed alike, and a busy frame
  went on collisions) and answer in the order things came in, which each thing now carries (`GameObject.getEntered`);
  the world keeps each player's things (`getObjectsOf`, `getOwners`) and what a player is shown is asked of his own and
  his allies' things and of what stands where their reach falls; a side's defeat, its power and an aura look at its own
  or at what is near (`World.thingsNear`); a thing marked destroyed is reaped from the list of those marked; and the
  ground under the still things is laid again only when one of them changed what it lays, cell for cell as a ground
  laid whole. Five thousand monsters asleep on a 900-cell world cost 0.06 ms a frame where awake they cost 5.
- **Routes by sectors** (`Sectored`, `GameLogic.setRouteSectors`): a map that says so is kept in sectors of so many of
  its cells a side, each sector's open cells in the pieces a step joins inside it (`Sectors`), joined to their
  neighbours'. What reaches what is answered by them — the same cells together as the zones worked out whole
  (`Zones.over`), kept sector by sector as the ground changes, the grid now recording where it changed
  (`PathGrid.changesSince`). A route is found through the pieces its way crosses and then cell by cell a stretch of two
  sectors at a time, by the search's own steps, costs and turns: corner to corner of a 1024-cell world examines some
  nine thousand cells in about 15 ms, where the whole grid's search examined nearly half a million in 800; of a
  4096-cell one, about a hundred thousand in 90 ms. A search's arrays on a map past two million cells hold only the
  cells it touched, with the same answers.
- **What each side has seen, kept small and by the simulation**: sight cells in chunks made as a side's lookers first
  cover them, the cells in sight kept with the frame each was last covered — every answer the one the cells kept whole
  gave. A view a client is shown shares the chunks, so the client's discovery takes again only the chunks that changed.
  Where the game says so (`GameLogic.setSightHiddenByStone`) stone stops a line of sight and a floor above a looker's is
  seen with nothing on it in sight — a crawler's dark kept by the simulation, the same on every machine. What a side
  has seen is remembered and recalled (`getSightMemory`, `setSightMemory`) and saved with a game (`GameSnapshot`'s
  `SIGHT` lines).
- **The fog's and the minimap's pictures** send only the tiles of 64 texels that changed to the card
  (`Renderer.modifyTexture`), the whole picture where most of it did.
- **The ground streamed** (`Visuals.streamGround`): a kit's floor built a chunk of 16 cells at a time within so many
  cells of where the camera looks, the nearest first and two a frame, and chunks further off let go — each chunk corner
  for corner what the whole build makes, its geometry kept about its own corner. A painted map is built whole, as
  before.
- A unit some forty-one thousand units from the origin walks as one beside it, step for step: a float there is a 256th
  of a unit.

Not measured here: frames a second on a graphics card, which this repository's machine has none of.

### A runtime of no kind

The runtime — `DukeGame`, its sessions, replays, snapshots and scripts — runs a kind of game it does not know, which
brings what only it knows through core's `Flavour`: its world (`newWorld`), the records its blocks are read into
(`templates`), its wire and what the wire carries (`codec`, `carries`), its own setup once its players are in
(`began`), and its parts of the picture (`ViewParts`: whether a thing is a structure and may be picked, what it has
queued and carries, how far it is built, its turrets and rally point, a side's money and spare power). A library offers
its kind as a service, and `Flavour.found` is the one on the classpath. What the runtime does each frame on the
simulation thread — work and orders posted from other threads, the game's per-frame code, the annihilation rule — runs
where the world's `simulate` is, just before it (`GameLogic.eachFrame`), in the same order as before; commands of no
set the world knows reach the runtime by `GameLogic.onOtherCommands`. A match's art is planned from the modules whose
data names what their thing may bring into the world (`Brings`), a factory's build list among them.

### The RTS, a flavour

`RtsFlavour` is the RTS as a kind of game: `RtsLogic` — the RTS world with the standard orders applied, moved into
`rts` — `RtsTemplate`, `CommandCodec`, the RTS's view parts, and the RTS's own API beside the runtime's: what a factory
makes and a builder finishes, what research and selling set off, a side's upgrades and starting money, a factory's
build menu, the starter units, and the RTS world it runs (`logic()`). A callback given it after the match has begun
reaches the world at once.

### Combat, a layer of its own

`combat` is what fights, whatever the genre — weapons and their bonuses, statuses, experience, healing over time,
shots, closing on a target, the auras below and the orders every side gives — on `core` alone, so a world that is no
RTS's fights with it: a `GameLogic` that is an `ArmedWorld`, its `armoury()` the world's arms settings — the weapons by
name, the target rules, the weapon bonus table, how often weapons look, what is shown when hidden, the guard rules —
with `CombatModules.withDefaults()` for its modules. `RtsSimulation` is one, and its `addWeapons`, `findWeapon`,
`setTargetRules`, `setWeaponBonuses`, `setTargetScanFrames`, `setGuardRules` and the rest are its armoury's, doing
what they did. A side's own damage bonus is asked of a player that is an `ArmedSide`, as `RtsPlayer` is, and a unit's
of its `DamageModifier` modules as ever; whether a contained thing fires from its hold, of every `Hold` in the world,
as `ContainModule` is. `CombatModuleGroups.PROGRESSION` is the editor's group for experience, the word
`RtsModuleGroups.PROGRESSION` names.

### A weapon's shots told to its own thing

A module of a shooter that is a `ShotListener` is told each shot as it is fired (`onFired`), and each blow of it as it
lands — a direct hit, a carried shot coming down, each thing a blast catches — with what the blow took after armour and
the body's scale (`onDealt`): the frame a swing strikes, and what a lifesteal takes its share of. `BodyModule.worthOf`
is that number, asked before the blow is taken.

### Auras

`AuraUpdate` is the reference's `PropagandaTowerBehavior` as a mechanism, which a leader's bonus and a fountain are as
well. Every `PulseFrames` it looks who is within its `Radius` along the ground — its side and its allies, or whom its
`Affects` names in a blast's words (`ALLIES`, `ENEMIES`, `NEUTRALS`, `SELF` for its own thing, `NOT_SIMILAR`,
`NOT_AIRBORNE`), of its `Kinds` and of none of its `ExceptKinds`. Each thing found holds its `Words` for as long as it
stays — what a `WeaponBonus` line, an armour set or a weapon set reads — and is given back its `HealShareEachSecond` of
its most health, a little every frame, from one aura at a time: `BodyModule.healFromOne`, the reference's
`attemptHealingFromSoleBenefactor`, a heal taken from one healer until that one has stopped for its frames. A thing no
longer found gives the words back at the next look, but for a word another aura still holds it in, so where two
leaders' reaches meet, leaving one takes nothing the other gives. Its `PulseEffect` plays riding its thing at each look,
and its thing's `AuraListener`s are told who was found — what a fountain's mana or a pulse shown over each head needs.
It gives nothing while its thing is dead, disabled, sold, hidden or in a hold it does not fire out of, nor while it is
being built, and takes everything back then, when its thing dies, and when it is taken off its thing — a skill given
up.

```
AuraUpdate
  Radius = 150
  PulseFrames = 60
  Words = [ENTHUSIASTIC]
  HealShareEachSecond = 0.01
  ExceptKinds = [STRUCTURE]
  PulseEffect = PropagandaTowerPulse
End
```

## 0.7.0

Never published: what it lists ships in 0.8.0.

### What to change

- A game that names no window size (`Duke3D.window`) opens a new player's window at the monitor's own mode, filling
  the screen, where it opened a 1280 × 720 window; the choice is written into his settings as if he had made it. A
  game that names a size, and a player who chose one, open as before.
- `Tileset.wall` and `corner` take several paths (`String...`): a call with one reads as it did, but a game built
  against 0.6.0 is built again.

### Large maps

A route search keeps its arrays on its thread from one search to the next, made fresh for each by a stamp, where it
made five the size of the map every time — 380 KB a search on a 160 × 120 floor, many searches a frame. Routes are the
same to the bit.

The fog's light is worked out again only where what is open changed, a cell eases only until it has arrived, and the
fog's picture redraws only the texels near cells whose light moved: on a 160 × 120 floor, 2 ms a frame whether the hero
walked or stood became 0.07 ms walking and 0.01 standing. `Fog.texelsPerCell` draws the picture at that many texels a
cell, made anew to the size of each map, so a large floor is as sharp as a small one; 0, the default, keeps the fixed
`textureSize`.

The minimap's ground is one picture, a texel a cell, on one quad, repainted only where the player's knowledge changed,
where it was a square a cell — nineteen thousand things drawn one at a time on a 160 × 120 floor, each given its
material again every frame. The colours are the squares' own.

A kit's floor is gathered into chunks of 16 by 16 cells, one geometry for each material in each, its vertices where
the pieces stood, where it was a geometry a piece under a node a cell: a 160 × 120 floor drawn as 240 things rather than
41,000, and a scene of 320 spatials rather than 102,000 for the frame to walk. The relief bends the gathered meshes in
place, where it copied every piece's mesh to bend it. The fog leaves a chunk out when all of it and a cell round it is
dark, asked of the chunks near where the light moved.

### Cells of different looks

A map whose record is `Looked` names a look for each cell — a theme the game registered, `lookAt(cx, cy)`, null for
the map's own — and each cell of a kit's floor is drawn from its own look's kit: its floor, its lid, what stands on its
rock and the faces of it, each by that kit's own numbers, so looks modelled at different sizes lie side by side. A piece
between two cells is drawn by the one it belongs to: a wall against rock, and the post in a notch, by the rock. The
fog's colour turns to the look under the point the camera looks at, over about a second. A still thing — a pillar, a
chest, a fountain — is drawn as the look of the cell it stands on draws it (`Visuals.Theme.unit`), its clips, sounds
and death with it, or as the game drew it outside any look where that look names none: a wood's pillar stays a bare
tree though the floor's look is a cave's. What moves is drawn as the floor's look draws it, wherever it walks. A map
that names no looks is laid as it always was.

### Scenery

A map whose record is `Dressed` lists scenery (`MapScenery`): a model, where it stands in cells, its facing, scale and
tint, and a footprint. Each piece is laid with the ground under it — gathered into its chunk, on its floor and the
relief, left out by the fog with its ground — in the materials its model came with, and it is no thing of the
simulation's: nothing a frame, on no minimap, in no snapshot. A piece with a footprint stands in the way: a route keeps
a body clear of it by its true distance, in the cells it walks and the line it is pulled straight along
(`PathGrid.clearOfCircles`), and a ground mover's step goes no deeper into it than it already stands
(`GameLogic.setSceneryFootprints`, which `DukeGame.applyMapTerrain` feeds from the map, and
`World.sceneryInTheWay`). It closes no cell, so a body goes between two trunks wherever it fits between them.

### Maps walked finer than drawn

A map whose record is `Subdivided` is walked on `navigationCellsPerCell()` cells a side for each of its own: at 2, a
map of 10-unit cells is searched, blocked and stood on in cells of 5, so a knight goes between two trees whose trunks
leave it room, while the map is drawn a tile to a cell as before. Every rule counted in cells — beside, near enough, a
group's shared route — is still counted in the map's (`World.cellSize`), and so are the search budget, the searches
for a band, a way out of stone and a place to stand, and the most a mover's cells reach: a frame searches as much
ground as it did. How high the ground stands, the relief and its cliffs are answered at the map's own cells
(`PathGrid.subdivided`), so slopes and stairs stand where they stood. Its round still things — a cylinder's or a
sphere's footprint, the fountain and the pillar — are kept off by their true distance as its scenery is and close no
cell (`PathGrid.setObstacleCircle`), so a hero goes between two where he fits and comes up to one as near as his own
outline; a box still closes the cells it covers, and a step into any of them is refused by its shape as before. `GameLogic.setPathGrid(grid, k)` is the same
from code; the logic's `getPathGrid()` is the grid walked, `DukeGame.getTerrain()` the one drawn. A map that is not
`Subdivided` is walked on its own cells, to the bit as before.

### A kit's walls of several models

`Tileset.wall(String...)` names several models for a kit's wall — rocks, trees, bushes — and each placement, and each
member of a clump, takes one by the settled number of where it stands, so the same map draws the same ones in the same
places every time; `corner(String...)` the same for its posts. They share the wall's numbers, so they are modelled
alike. One is the wall there always was.

### The hero bar's lettering, sharp at any scale

The hero bar's words may be drawn in a face the game names: `Lettering` and `TitleLettering` in its `PanelLook`, each a
font file (TrueType) by its whole path. Each line is baked from it at the pixel size it lands at on this window — its
design size times the bar's scale — and drawn one pixel of it to one of the screen, where a bitmap font drawn through
the bar's scale is resampled and soft. A face is loaded once, its kerning measured once and scaled to every size it is
baked at, so a size costs a few milliseconds and a bar rebuilt at a resize bakes nothing it already has. A bar naming
none draws in the bitmap fonts as before, and `BitmapFontBaker`'s files are the same to the byte. The card that opens over a slot is lettered the same
way, its name in `TitleLettering` and the rest in `Lettering`.

### An aura follows its status

An aura layer that `renews` (`EffectLayer.Builder.renews`) is carried to a new cast's end when it is cast again on somebody
it still burns on — a stun given again wears its stars to the second stun's end — where one that does not is dropped,
as a skill's look cast on every blow it lands is. A continuous layer on somebody stops being made at its span, as one
on a spot does, where it went on for another of its lives.

### A creature's own level

A creature may carry a level of its own on its bar: the game names the start of a word in its `UnitBarLook`
(`withLevelWord("level:")`) and sets `level:7` on the creature (`GameObject.setCondition`), and its medallion shows 7
where it showed the floor's depth. The hero keeps his own level, a creature holding no such word the depth, and a game
naming none sees its bars as they were.

### Clicks and bars on a hill

A click on the ground — an order, an aimed skill, what the pointer is over — lands where the pointer is on a relief of
hills. The ray under it is walked from where it comes down to the map's highest ground, half a cell at a time, to the
first ground it meets, and halved down onto it. It was met with a level plane and settled onto the ground from there,
which on hills landed past the cursor: the plane at zero lies behind a rise the ray has already struck, and on a steep
face the settling never settled.

Every drawing on the ground lies over its rise and fall: the rings — a skill's reach and blast, the ring round what an
order was given on, the flash of an attack — and their washes, a shot's lane, an aim's picture and a ground picture,
an order's arrowheads and the disc under a selected thing. Each point of one stands on the ground under it, no more
than two units from the next, where they were laid flat at the height under their middle — or, for a picture, on eight
cells however large it was — and a slope climbed through the half of them uphill. A disc under a thing in the air lies
on the ground under it.

A thing's bar floats over its head wherever its feet stand, up a hill or down one. The bars drawn in the game's own
look floated at their model's height over zero, sinking under a hero walking uphill; the plain bar always stood on the
feet.

### The window's size, at once

The settings screen's Size is taken by the window as it is chosen — the move `Duke3D.display` makes — and put back by
Cancel, where it waited for the next launch: a player whose saved size was far smaller than his monitor no longer
plays a blurred picture until he restarts. An Anti-aliasing row (off, 2 or 4 samples a pixel) is kept for the next
launch, when the window is made with it; under the sun's shadows the world is drawn through a pass that takes none.

The minimap shows in the hero bar's socket on a wide window. Past about 1440 pixels the design's bar is drawn larger
than 1.1, and the minimap, laid at a depth of its own, was drawn under the hole cut for it: the bar's depths grow with
its scale. It is laid at the depth the bar hands out beside the socket's place (`HeroPanel.minimapRect`).

## 0.6.0

### What to change

- `DukeGame.pressCommand(id, place, target)` is `pressCommand(id, place, facing, target)`, `setAim(id, place)`
  is `setAim(id, place, facing)`, and an `aimFits` lambda takes the facing too: `(button, place, facing) ->`.
  `CommandPress` carries the `facing` between `place` and `target`.
- `World` has `random()`. Only `GameLogic` implements it in this repository; anything else that does must too.
- `ObjectStatus` has `AIRBORNE`, `HELD` and `SOLD`: a `switch` over it with no `default` needs the cases.
- `GameLogic.checksum()` mixes in a thing's statuses when it has any. A world where nothing has a status sums
  as it did, so old goldens and replays hold; one where something is `HELD`, `AIRBORNE` or under construction
  sums differently than it did in 0.5.0.
- `GameMessage` has `Sell`, `AttackMove`, `Guard`, `Evacuate`, `ExitContainer` and `GameOrder`, each with its
  line in `CommandCodec`: a `switch` over it with no `default` needs the cases.
- `DamageType` is no longer an enum (see below), so `DamageType.values()` is gone: the set is open, and a game
  that walked every type — duke-dungeon's `GrowableBody` does, to armour a hero against all of them — names
  the types it means.
- `ObjectDied` carries the death type, the killer, the facing and the killer's side: `new ObjectDied(frame, id,
  template, player, position, deathType, killer, orientation, killerPlayerIndex)`, the killer `null` and its side
  -1 for a death by no one. Only the engine posts one. A test that makes one adds them; the form without the
  killer's side is kept, and means nobody's. `Death` has `killerPlayerIndex` beside the killer the same way.
- A side's money keeps books. `RtsPlayer.deposit` is earned (`getEarned`) and `withdraw` is spent
  (`getSpent`). Money coming back is `refund`, which is taken off what was spent, and money handed over is
  `give`, which is in neither. `DukeGame.money` now gives. A game that deposits its own starting money or a
  script's gift, and does not want it counted as earned, calls `give`.
- `DukeGame.runHeadless` feeds a replay (`playReplay`) before each step, as the engine's own loop does: its game
  takes the recording's input and not live input, and a checksum that no longer matches is reported. It used to
  step a replay's game on live input.
- The weapon bonus table's lines are summed as the reference sums them, `1 + Σ(multiplier − 1)`, where they were
  multiplied. A thing holding one word deals, reaches and fires as it did; one holding two or more gets less than
  the product (a veteran's 110% with a 125% upgrade is 135%, no longer 137.5%).
- `DukeGame.getLocalPlayerIndex()` is -1 once the machine has taken a watcher's seat mid-match (`watch()`).
  `GameLogic.checksum()` mixes in the players the map was revealed to, when there are any.
- The client tells `DukeGame.getSelection()` in every frame of a match, and in the order the things were chosen,
  whatever draws the HUD. With `Shell.drawnByTheGame()` it used to stay empty, and the order was a hash's.
- A cue with no file plays nothing; it used to throw when played. A save carries each thing's most health, and the
  players the map was revealed to on a `REVEALED` line that an older engine does not read.
- The client reads its own keys from a `KeyMap`, `KeyMap.standard()` unless the game gives one — the keys it
  always had. Two things are new with it: a letter the command bar shows on a button presses that button (after
  the game's own letters and its map), and Enter or Space put the camera on the player's own units at once.
- `World.findPath` may answer `null`: the frame's path searching is spent, and the mover asks again next frame.
  `MoveUpdate` waits its turn walking the route it had; a module of a game's own that asks for a route does the
  same.
- A `PursueUpdate` with `RepathFrames = 0` planned again every frame; it now plans only when it must — no route
  yet, the target more than a cell from where it planned to, the route used up. A number keeps it on a schedule
  besides, staggered by id.
- Every blow that does harm posts `ObjectHurt`, before any `ObjectDied` it causes: code that took the first event
  of a blow for its death filters for `ObjectDied`.
- `NetMessage` has `LoadProgress`, `ChatLine` and `Resent`: a `switch` over it with no `default` needs the cases.
  Machines of a network game run the same engine: the welcome line now carries the host's settings encoded, a
  guest's join line the port it listens on, and the host names every guest's (`DUKE-PEERS`) before it starts.
- A player who leaves a network game is told (`DukeGame.onPlayerLeft`, `LockstepGate.onPlayerLeft`) as the frame
  they left from runs, on the simulation thread and on the same frame on every machine, so the callback may change
  the world — hand the side to an ally with `handOverAll`. It used to be told when the news came in, a frame that
  differed from machine to machine. A guest whose host leaves plays on, the next player relaying, where it used to
  stop with CONNECTION LOST; in a match of two the one left plays on alone.
- A harvester that moves is paid only once it stands beside its depot, where a trip that ended anywhere used to be
  paid. It loads at the pile it walked to, topping up a part load, where it took from whichever was nearest then;
  and one that flies flies to its work (`getLocomotor()`), where it only ever walked.
- A `SoundSink`'s music is a handle it hands back (`Playing music(path, gain)`), which is what lets one track fade
  while the next plays; the interface is the client's own, so only a test's sink changes. `musicOnce` and
  `Playing.ended` are defaults.
- A right click gives up an armed button wherever the pointer is, and so does Escape. A right click off the
  world's part of the window used to do nothing.
- The model a thing is drawn with is chosen by the words it holds as well — `UnitView.conditions`, whatever the
  game set on it with `GameObject.setCondition` — beside the world's and those its health decides. A template
  whose `Models` name a word its things hold now draws that model where it drew the plain one.
- The client keeps a model's glTF `BLEND` and `MASK` when it dresses the model in its own lit material: a
  blended material is drawn in the transparent bucket without writing depth, and a masked one is cut out at its
  cutoff. Every dressed material used to be drawn opaque.
- `Locomotor` has `leave(way, destination)`, the first leg a unit walks out of its maker; a mover of a game's own
  that does not implement it goes straight to the destination, as its `moveTo` does.
- `GameLogic.spawn` is final. Code that ran after a spawn by overriding it overrides `onSpawned(thing)`, called once
  the thing's place, owner and a setup's settings are in (`World.spawn(template, place, player, setup)`).
- `ObjectStatus` has `HIDDEN` and `UNSELECTABLE`, and `EffectList.Entry` has `Debris`: a `switch` over either
  with no `default` needs the case.
- A winged flier arrives on the frame it passes over its goal, going wide and coming back to one inside its turning
  circle, where it used to count itself arrived as soon as the goal was inside the circle; and one given nothing
  circles where it is, where it used to fly straight on.
- `World.standingNextTo` aims at the nearest point of the target's outline, not its middle, so what closes on a box
  from off its corner stands beside it rather than short of it.
- The side's power surplus counts every `PowerModule` of a thing, not only its first, and none of a thing lying
  dead. A builder handed to another side gives up its build order that frame.
- A click, and the pointer, pick the pieces of a thing drawn now, each as the box round its own mesh turned with it,
  the nearest hit along the ray winning — no longer the world-axis box round the whole model and the nearest middle.
  A click on the ground beside a thing, inside that old box, now gives the move.
- A thing with a line (`GameObject.setSpan`) is in every player's snapshot, fog or not, as the ground under it is.
- `GameLogic.checksum()` mixes in a thing's damage scale, own sight, protection and floor when they are set, and a
  closed deck: a world that sets none of them sums as it did.
- A harvester its player gives any order but `workAt` stops working where it is, its load kept, until the next
  `workAt`; it used to set off again by itself. It loads only while it stands beside its pile, and `workAt` keeps
  one place, the last named, pile or depot: told its depot with any load, it delivers it at once.
- Selling a building of a shared hold no longer lets its passengers out: it leaves the network that frame and they
  stay in it, out beside it only where it was the last. A passenger let out comes out on clear ground outside its
  holder's footprint, or at the holder's `ExitBone`, where it used to come out 5 from the holder's middle.
- `EdgeScroll` measures from the window's own edges, the bottom one under a bar of the game's own included, where it
  measured from the world region's, and scrolls nothing while the pointer is outside the window. The pan keys show
  the scroll pointer too.
- A new match starts with the player's camera unturned and at its start distance, where it kept what the last match
  or the backdrop left. The eye stands exactly its distance back along the line of sight, where it stood 0.9986 of
  it, so a `GameCamera` zoom is the eye's distance.
- The pointer over what the game's canvas takes (`CanvasInput.take`), or off the world's part of the window, is
  `Point`, and an armed aim's own pointer shows over the world only. The client's minimap outlines the world
  region's corners, where it outlined the window's.
- A ground mover sent to a place ends on the nearest block of the ground's cells it may have and can walk to, as the
  reference's pathfinder places it, and holds that block as its own. A radius-7 mover sent to (352, 352) stops at
  (350.5, 350.5), and two sent to one point stop 20 apart instead of on one another. A move into something (entering
  it, docking at it, closing on it) goes exactly there with `Locomotor.moveExactlyTo`; the engine's own errands
  already do. A test that expects a mover exactly on the point it was sent to measures from its block's point
  (`Block.of(radius, cellSize, x, y).point(cellSize, z)`). `MoveUpdate.getGoal()` is still the place its order named,
  and `getDestination()` is the block it walks to.
- Ground movers no longer shove or swerve round one another. One is held by the one it drives into and slows to what
  that one allows. It plans a route round after two seconds, and the lower of two stuck on each other steps aside.
  Legs pass legs. Buildings and other things that do not move stay solid as before. A route costs its turns and takes
  a diagonal past one open side of it, so of two routes of one length it takes the one that turns less.
- A mover whose data names a `Gait` moves by it. `MoveUpdate.Data` has acceleration and braking (and their damaged
  values), the share of health below which it counts as damaged, least speed and least turn speed, close enough,
  whether it backs up, its gait, and its path and move priorities. One that names none moves at once and turns as
  things always did.
- A `MoveTo` naming several units places them as a group (`RtsSimulation.getGroupLayout()`, `GroupMove` unless the
  game sets another). It used to send every one of them to the one point. `MoveTo` has `click`, the player's own
  click, written `,click` on the wire; an older line reads as not a click. The client posts one `MoveTo` for its
  selection where it posted one per unit round the click.
- A click on open ground moves a lone selected unit that has a production queue; it used to make the click its rally
  point. In a game with a ground-order rule (`DukeGame.groundOrder`) the client never makes a rally point of its own:
  a click the rule gives no word is a move. With nothing selected that can move and no word, the click is refused
  (`Deny`) and sends nothing.
- A drag box takes the player's units and leaves his buildings out, and takes one thing of another side's alone in
  it. A box with nothing in it to take keeps the selection. It used to take all of his and let an empty box clear
  the selection.
- `World` has `takePlace`, `holdPlace`, `keepsCells`, `letPlaceGo`, `holdsPlace`, `markStanding`, `placeAside`,
  `stillAlliesOn` and `runsOver`, a `findPath` that goes round movers it names, and a `findBlocker` that says
  whether the mover is passing. `Locomotor` has `moveExactlyTo` and `moveThrough`, and `Module` has `keepsBusy`.
  All of them are defaults: only a world or a mover that keeps ground cells implements them.
- A factory's one `Door = Door … End` is written `Doors = [Door … End, …]` in a block; one made in code with a
  single door is made as it was.
- A passenger lends no sight while it rides, unless its hold says `PassengersSeeOut` (a garrison): a transport's
  passengers used to go on seeing from where they got in.
- A blast reaches each thing's bounding sphere, half its height up, not its middle: a thing at the edge of a blast is
  caught where it was missed. `NOT_SIMILAR` spares only the allies of the template of what went off, where it spared
  every thing of the shooter's template, and a blast that does not say `SELF` spares what went off and what made it.
- A weapon whose range less a quarter cell is under a cell closes until it touches, and `WeaponFired`'s contact
  flag asks the weapon by that rule, where it used the reference's older one.
- `World.standingNextTo` measures from the footprint the mover will stand in, turned the way it will face. An
  exact move goes on onto its point once close enough, where it stopped its close-enough short.
- `GameLogic.getObjects()` hands out one copy until the objects next change; a copy handed out stays as it was.
- A still thing turned or moved has its footprint laid again before the next route (`ObstacleRules`, every still
  thing in the way unless the game says otherwise, as before).
- `GameLogic.checksum()` mixes in a thing's producer and its own fog range when it has them, and a save carries
  both; a world where no factory made anything and nothing set a fog range sums as it did, and an older save loads.
- Under `RIGHT_COMMANDS` a game's `RightDrag` scrolls with the right button held, the order given as it is let go
  as a click, where the button ordered the moment it went down. A shift-click on one of the player's own things
  selects it even where the game names a word a click on it gives, and the force-attack key's click sends a forced
  attack (`AttackObject.forced`, `,forced` on the wire; an older line reads as not forced).
- A particle system at a bone its model lacks runs at the thing's own place, where it ran nothing.
- A thing the world keeps dead is drawn by its words and its health — its model, pieces, clip and tints — where it
  was drawn frozen as it fell, and what lay under it fades out over its frames from its death.
- A look's ground picture of no words stays under the picture its words choose, where it gave way to it.
- Every scorch mark is laid over the ground's rise and fall, shaded with the ground's ambient and half its sun and
  shrouded as the ground, where it was a flat unlit square at its middle's height.
- A laser look's tiling scalar is kept as written, where it was raised to 0.01, and a line's picture is tiled no more
  than 50 times.
- `MapArea` has `river()` and `riverStart()`, and `RtsModules` has `ToppleUpdate`, which a moving
  `CrushUpdate` pushes over. Both are defaults a game's own records and modules need not write.
- `GameLogic.checksum()` mixes in every side's money, what it has earned and spent, the words it was granted and its
  upgrades, and the logic's random numbers once any have been drawn, where it summed the things alone: every RTS
  world sums differently than it did, and a replay or a golden checksum recorded before is recorded again. A game
  adds numbers of its own with `checksumAlso`.
- A holder taken out of the world without a death takes its passengers with it, the same frame, where they were
  left in the world contained and held by nothing; the last of a shared network taken away takes its passengers
  away too, where they came out where it stood. `GameLogic` reaps again, the same frame, what the things it reaped
  took with them.
- An `Evacuate` lets no rider off its carrier, and an `ExitContainer` naming a rider does nothing; only the
  carrier's death or the game's own `unload` lets one off.
- With nothing selected that can move and no word of the game's for the click, only one structure of the player's
  selected alone is refused open ground; two buildings show the move pointer, and a click is answered as a move of
  nobody — its mark and the game's move hint — with nothing sent.
- `World.isBeside` also takes a mover sent beside a thing (`standingNextTo`) that has stopped within its
  close-enough distance of the point, the thing still where it stood, whichever way the mover faces.
- A mover counts as damaged at or below its `damagedBelow` share of its most health, where it was below it.
- A unit walking a move is neither stopped nor turned aside by `PursueUpdate` for a target its weapon picked
  itself, and lets it go once out of range; an ordered target is closed on as before.
- A box whose turn waits on another's footprint is held, not stuck: those frames no longer count against its
  progress, and it turns through after its 2 s rather than give its order up.
- A mover on a gait steers along its route (the reference's `Path::computePointOnPath`), passing a waypoint it went
  past or cannot turn onto, where it stepped onto each in turn; wheels within four cells of the end of their way for
  2.5 s brake and slide onto it. Routes that bend are driven along differently than they were. A mover that is
  stepping aside for another is not asked again by it, and one waiting its turn for a route holds nobody up and is
  asked aside by nobody; a search over decks uses the ground's zones.
- `Transport` has `drop`, a default; a guest told the host took it out of the game stops, disconnected, where it
  went looking for another relay.
- `GameMessage` has `ResumeConstruction`, with its `RESUME` line in `CommandCodec`, and `WeaponStatus` has
  `PRE_ATTACK`: a `switch` over either with no `default` needs the case.
- A unit closing on a target plans its route to the band its weapon reaches from (`Locomotor.moveWithin`, a search
  of at most 2500 cells round the target), where it planned to the target's own point; a goal too tight for its
  mover is reached through the nearest cell with room first. A ground mover held behind one standing still plans a
  route round it once.
- A builder that has put a site down keeps to it until it is whole, any order but another build ending its work,
  and a builder on its way to put one down counts against the thing's cap (`MaxSimultaneous`).
- A send to a peer whose link has just closed is a lost link, told as any lost link is, where it threw out of the
  frame.
- With the add-to-selection key held, a click on one of the player's own things already selected takes it out,
  where it kept it; a control group selected by its key, the next or previous unit, every unit and every one of a
  kind are answered with the select voice, where they were silent.
- A sound about a thing is heard from the thing's own height, and one at a place from the place's, where both were
  at height 0.
- A mover's step is held by the still things the `ObstacleRules` put in the way and by other movers, no longer by
  every still thing with a shape: under rules that leave a kind out of the way, movers pass through it. Under the
  default rules every still thing is in the way, as before.
- A ground light's sun (`Visuals.Sun`) stands at any pitch from -90 to 90 and points exactly so, where its pitch was
  held to 0..90 and its direction to 5 degrees up at least; a thing's light named below 5 degrees now points where it
  says.
- An `Evacuate` is told to its container's `OrderListener`s, and an `ExitContainer` to its passenger's, after the
  engine has applied it, where neither was told.
- A target a weapon's outline overlaps is no distance off, never less: inside any least range, and inside none where
  the weapon has none. A weapon with no least range used to find what it overlapped too near, and never fired at it.
- A hold of its own that dies lets its passengers out beside it the frame it dies, its riders dying with it, where
  they stayed in the world held by nothing; each passenger is dealt `DamageToPassengers` of its most health first,
  none by default.
- `Placement.Fit` has `ON_UNSEEN_GROUND`: a `switch` over it with no `default` needs the case. `Placement.check` has
  a form that takes the placing player, which `Construction.order` now uses; the old form, like
  `RtsSimulation.fits(template, place, facing)`, judges a site for no side and asks nothing of what a side has seen.
- `GameLogic.revealMapTo` puts every one of the player's `SightCells` in sight, where his cells had only what his
  lookers covered: `SightCells.sight` and a revealed viewer's `WorldSnapshot.sight` read `IN_SIGHT` everywhere, as
  `canSee` already did. The client drew a revealed player's map open before, and still does.
- A weapon with no target looks on its own clock (`RtsSimulation.setTargetScanFrames`), where it looked on the frames
  its id fell on in the world's round. At the default rate of 1 nothing changes. A game that set a rate above 1 now
  has each thing look first as it falls idle — new, its order done or stopped, its target gone — and then every rate
  frames, its first wait up to half the rate longer, drawn from the world's random numbers: a recording made with a
  rate above 1 plays back differently, and one weapon may be taken sooner or later than it was.
- The client's on-the-screen test for its selecting keys is the world's part of the window (`Duke3D.worldView`),
  where it was the whole window: a unit under a bar of the game's own is off the screen.
- A save carries the players whose map was marked seen on a `SEEN` line that an older engine does not read.
- Nothing else breaks. Every record that grew keeps its old constructors — `WeaponUpdate.Data`,
  `HarvestUpdate.Data` (and `NeedsDepot`, `WaitBy`, `FramesBeforeActs`, `FramesAfterActs`), `SupplyModule.Data` and
  `SupplyDepot.Data` (and `Dock`), `WeaponFired`, `Weapon` (and its own `Bonuses`, its
  `Affects` and second ring), `WorldSnapshot` (and `revealed`, `contextOrder`, `beams`, `rallies`, `effects`),
  `SoundBank.Cue`, `Sound`, `UnitView` (and `passengers`, `conditions`, `built`, `ridesOn`, `allied`, `span`,
  `mobile`, `drawnAs`, `wears`, `opacity`, `hostile`, `speed`), `MoveUpdate.Data` (and its gait and priorities),
  `GameMessage.MoveTo` (and `click`), `UnitBarLook` (and `plain`), `CommandButton`, `Upgrade`,
  `ProductionUpdate.Data` (and its `Exit`, `Door`s and `Words`, and `RefundsPriceNow`), `ProductionUpdate.Queued`,
  `PlacementRules` (and its `SiteWords`), `Visuals.ClipState` (and its picks, `idles` and distance),
  `ContainModule.Data` (and `PassengersFire`, `RiderBone`, `PassengersVanish`, `ExitBone`, its exit path,
  `PassengersSeeOut`, `RiderKinds`, `ShowsPassengers`, `RiderTurret`),
  `OrderMark` (and `ContextColour`), `RtsTemplate` (and `FogRange`, `SeenByAllWithin`, its fence), `Shot`,
  `EffectList.Debris`, `GameMessage.AttackObject`, `ActiveBody.Data`, `ExperienceModule.Data` (and
  `LevelHealthBonus`), `TextFloated`, `HitNumbers`, `WeaponSlot` (and its sources), `MomentWords` (and
  `preAttack`), `WorldSnapshot` (and `sight`, `aimMarks`, `unseenEvents`, `hiddenUnits`, `streams`), `UnitView`
  (and `remembered`, `turrets`, `lift`, `yaw`, `corners`), `CameraFrame` (and `edgeShare`), `SoundBank.Cue` (and
  its reach and voicing), `MoveUpdate.Data` (and its surfaces and class locomotors), `ObstacleRules` (and `laid`),
  `PlacementRules` (and `standsOver`, `seenGround`), `DukeGame.StripPiece` (and its `Mapping`), `ContainModule.Data`
  (and `DamageToPassengers`), `WeaponSet` (and `WeaponLockSharedAcrossSets`), `WeaponUpdate.SlotNow` (and its weapon
  and clip), `WeaponUpdate.Data` (and `TargetScanFrames`) — and every new field left out means what the old record
  did. `SocketTransport` and `HostTransport` run over lines of a `LineChannel`, a socket's
  own wrapped as one, so a game that uses sockets sees no change.
  `PlacementRules` keeps its constructors too, `siteAtOrder` left out as before.
  A template that names no prerequisite, word or cap is buildable as before; a save from before granted words and
  computer sides loads.
  `Canvas.drawPicture(Picture, …)` is a default that refuses, so a game's own canvas compiles as it did.
  `ProjectileLauncher.launch(shooter, victim, damage, type)` and `DieModule.onDie()` are still called, through the
  forms that now say more. The static `Duke3D.launch` methods are shorthand for `Duke3D.of(game, visuals)...launch()`.

### A map seen at the start, sites on seen ground, locks across sets, clips, looks and the screen first

`GameLogic.markMapSeen` (`DukeGame.markMapSeen`) marks every cell of a player's `SightCells` seen and none in sight,
the reference's `revealMapForPlayer` for a match whose own option leaves the shroud out; his lookers open cells to in
sight as ever, and a save and the checksum tell it from `revealMapTo`. `PlacementRules.seenGround` refuses a site
whose middle stands on ground its side has never seen (`Fit.ON_UNSEEN_GROUND`, the reference's `LBC_SHROUD`) — a
person's side's, or every side's — and `RtsSimulation.fits(player, …)` answers as his order would be. A `WeaponSet`
that says `WeaponLockSharedAcrossSets` keeps the lock and its slot when the set in use changes to it.
`WeaponUpdate.SlotNow` tells each slot's clip — its size and the rounds in it — and its weapon's name, for a game's
row of rounds. A thing looks for a target on its own clock, at its template's rate (`TargetScanFrames`) or the
world's, and at once as it falls idle (`RtsSimulation.setIdleTargetScanFrames`). The client's selecting keys try the
screen before the map where the game asks (`KeyMap.screenFirst`), and say which gave what they took.

### Weather for a match, steps, sites, roads and low suns

`DukeGame.weather(FallingWeather)` sets a match's falling weather, or clears it — kept by a game with no window and
nothing in the checksum — drawn as camera-facing squares of its size in world units, one every one-over-density. A
mover's step is held only by what the `ObstacleRules` put in the way. `PlacementRules.standsOver` names the kinds a
site may stand over, and `RtsSimulation.onPlaced` (`DukeGame.onPlaced`) tells the game the frame a site is put down,
for it to clear what the site stands over. A strip piece may lay its picture by one straight mapping of the ground
(`StripPiece.Mapping`), cut rather than stretched. A ground light's suns stand at any pitch, level or below lighting
only the faces turned toward them.

### Holds that hear their orders, channels a game opens, and a relay with no seat

An `Evacuate` and an `ExitContainer` reach the `OrderListener`s of what they name. A network session may run over a
duplex channel of text lines the game supplies — a WebSocket through an HTTPS gateway — for a guest
(`MultiplayerSession.join(channel)`) and for a host, one channel a guest (`host(guests, …)`); `LineChannel.pair` is
two ends in memory. `MultiplayerRelay` relays a game it does not play: every seat a guest's, the host's decisions made
with no simulation of its own, every message heard in order for a replay and the checksums, and the end told once
every seat has left.

### Weapons: a locked slot, aiming, a pitch and a least range, and a wind-up

An order may lock a unit's weapon to one slot of its set in use, until the attack is over or the slot's clip is empty,
or until another lock (`WeaponUpdate.lock`, `GameMessage.AttackObject`'s `slot`); a slot names the sources that may
pick it by themselves (`WeaponSlot.autoChooseSources`: a player's order, the game's, none), and an order says its
source (`OrderSource`) — the reference's `setWeaponLock` and `AutoChooseSources`. A weapon fires once every
`WeaponAim` module of its thing says it is aimed. A `Weapon`'s `MinTargetPitch` and `MaxTargetPitch` keep it off what
stands too high or low; its `MinimumAttackRange` fires at nothing nearer, a unit too near backing away to it and one
that cannot letting it go. `PreAttackFrames` winds up before a shot, a clip or an attack (`PreAttackType`), the thing
holding its moment word meanwhile (`MomentWords.preAttack`), and `LeechRange` keeps an attack's reach once begun.
`ExperienceModule.setExperience` sets a rank down as well as up.

### Routes to a fight, builders, sites and how long they take

A mover closing on a target walks to the band its weapon reaches from, searching a band of cells round the target
rather than the whole map (`Locomotor.moveWithin`, `World.findPathWithin`). A builder keeps to its site until it
is whole; another builder of the side takes up a half-built one (`GameMessage.ResumeConstruction`). How long a
site and a factory's job take is asked every frame of a `BuildLength` module, work done kept as it changes.

### What a player has seen, and still things remembered

`GameLogic.setSightCells` keeps what each player has seen of the map, by cells, lingering a while after, and what
he sees and may target follows it; the snapshot carries it (`WorldSnapshot.sight`) and the client draws the fog by
it (`Visuals.fogBySight`). Still things seen once are drawn as last seen while their ground is fogged
(`DukeGame.remember`, the reference's ghosts), and things out of sight are kept in view a while
(`keepOutOfSight`: 60 frames, 150 dead).

### Ground of classes

A cell may hold a class of ground the game names (`PathGrid.setGroundClass`), and a still thing may lay one over
its footprint by a word or a kind (`ObstacleRules.laid`) — water, a cliff, rubble. A mover enters the classes its
locomotor names (`MoveUpdate.Data.surfaces`) and moves by a locomotor of its own on each (`classLocomotors`): its
routes, zones and steps read the ground as it may walk it.

### Things drawn: clips stood still, turrets turned, lift, yaw and wheels

A look's clips stand still while its thing holds words it names (`UnitVisual.stillWhile`). A game's `Turret` says
how each of two turrets stands turned and pitched, the view carries it, and a look turns the bones its words name
(`UnitVisual.turret`). A thing may be drawn lifted and turned beside its facing (`GameObject.setLift`, `setYaw`), and
its wheels raised at their corners (`setCorners`, `UnitVisual.wheelCorner`) — all of it drawn only.

### The picture: gamma, shadows, weather and streams

`Duke3D.gamma` draws the whole picture through the reference's gamma ramp. A look that casts
(`UnitVisual.castsShadow`) throws the sun's shadow onto the ground and every model, multiplied by the game's colour
once however many overlap (`Duke3D.shadowColour`), cast from no lower a sun than its least height; `Duke3D.shadows`
turns them off and on. `Duke3D.weather` lets flakes fall round the camera as the reference's snow falls. Things may
ride a named stream (`World.rideStream`, `breakStream`), drawn as one ribbon through them, broken at its gaps
(`Visuals.stream`).

### Sounds: from the simulation, where they are heard, and how each play varies

`World.sound` plays a cue at a thing or a place, held going while asked again (`SoundPlayed`). A game's
`SoundBank.Hearing` places the listener between the point looked at and the eye, takes a share off placed sounds
while the eye is far, and names the ranges and the floor a placed sound is heard within; a cue names its own
(`Cue.reaching`) and may be heard through fog (`DukeGame.hearThroughFog`). A cue's `Voicing` varies each play's
rate and loudness, plays attack, sound and decay after a pause — a loop pass after pass — and keeps a thing to one
voice at a time.

### The player's hand: presses, ghosts, selection, the screen and the window

A press the simulation refuses is told to the game (`DukeGame.onPressRefused`). A ghost may be drawn as its
building, lit and in its placer's colours, tinted while refused, and the answer about its place may mark rectangles
of ground (`Visuals.ghost`, `DukeGame.aimAnswer`, `AimMark`). Selection follows the reference: a shift-click
takes a selected thing out, the client's own selecting keys answer with a voice, a thing may be in one control group
at a time (`KeyMap.oneGroupEach`), and the drag box and the ring under selected things are the game's to set
(`Visuals.dragBox`, `selectionRings`). A game's painter may ask where a point of the world and a thing's bar are on
the screen (`Canvas.screenOf`, `barOf`), and hears the control groups (`DukeGame.getControlGroups`). The camera may
be kept back from the map's edges by what its view shows (`CameraFrame.edgeShare`), and a game may set the window's
size and fullscreen while it runs (`Duke3D.display`).

### Holds: riders by kind and on a turret, passengers shown, and a hold taken away

A hold may ride only some kinds at its `RiderBone` (`RiderKinds`), the rest sitting inside, as the reference's Helix
carries soldiers inside and one add-on on top (`ContainModule.rides`); may seat its riders on its turret
(`RiderTurret`), turned as a game's `Turret` module says its turret is; and may show its passengers where the game
stands them (`ShowsPassengers`), clicked as the hold — the reference's fire base. No order lets a rider off. A holder
taken out of the world takes those inside with it.

### Which way a bone points

`Bones.pointingInFrame` and `pointingInWorld` (`Bones.Pointing`): which way a bone's forward axis points, about the
up axis and above the ground, read from the model file as its place is (`ModelBones.forward`) — a jet parked facing
its hangar bone, a missile launched along its silo's launch bone.

### A mark's gap, the ground refused, the latest alert, and a sound by its cue's name

A mark's gap below the plain bar, a share of its own height (`MarkPlace.gapShare`), is pinned at the reference's
numbers. Open ground is refused only to a lone structure. A game may set where the latest-alert control looks
(`DukeGame.setLatestAlert`), the client then moving it no more on its own. An `EffectPlayed` whose name is a sound
cue of the game's plays it at the place, heard as the cue's rules say, and following the thing it rides.

### Research told, the checksum, and harvesters: their range, their stands and their docks

Game code is told when research finishes (`RtsSimulation.onResearched`, `DukeGame.onResearched`). The checksum takes
in the sides, the random numbers and the game's own (`checksumAlso`). A harvester's search range may be set for one
harvester (`HarvestUpdate.setSearchRange`) and the game says which piles it may take (`RtsSimulation.setPileRule`);
it may stand `FramesBeforeActs` before its first act and `FramesAfterActs` after its last. A pile or a depot may name
a `Dock`: one harvester at a time, the others waiting by it in the order of their places, how many may wait, a place
to wait at and a place to act at (`Dock.Place`, a bone or a point from its middle) — the reference's `DockUpdate`.

### A network game that waits, and gives up

While the frame does not move, the session says whom it waits for and how long since each was heard from
(`MultiplayerSession.waitingFor`); the host takes out a player silent past the game's limit (`setSilenceLimit`), or on
the game's word (`takeOut`), as one whose link closed. `join` and `host` take a time limit for the handshake, and a
guest may say the port it listens on, should it have to relay — one a firewall can be told.

### Clips: none, by a thing's moments, several, and paced

A clip state may name no clip, the model standing in its own pose and its roles not standing in. A game may name
words for a thing's moments (`DukeGame.momentWords`, `MomentWords`) — moving, attacking, each weapon slot firing,
between shots and reloading (`WeaponUpdate.slotsNow`), a turret turning — which the snapshot adds to what each holds
while they last. A clip state, an idle and a death may name several clips, each with a weight (`Visuals.Pick`), one
drawn the same on every machine; idles draw another as one ends. A walk or a clip state may name the distance one play
covers, played at the rate that covers it at the thing's speed (`UnitView.speed`, `Locomotor.speedMoved`).

### Movers: steering, stepping aside, a locomotor whole

Movers on a gait steer along their route, and wheels slide onto an end they circle. A mover asked aside plans one
route, not one a frame. A mover's locomotor may be given whole (`MoveUpdate.setLocomotor`), keeping its way. A move
order is not stopped for a target the weapon picked, and a box whose turn waits is held, not stuck.

### A frame at a thousand things

The frame no longer copies the object list for every walk; a footprint's sine and cosine are worked out once as a thing
turns, not in every test; the snapshot sorts the lookers that show a player anything into squares of the ground by what
their reach covers, and asks each thing only of those — the same answers as `canSee`, one by one. The game says how
often a weapon with no target looks for one (`RtsSimulation.setTargetScanFrames`, the reference's
`MoodAttackCheckRate`), each thing on its own frame of the round.

### Harvesters, builders and factories

A harvester sent beside its depot or pile is beside it where its move ends. A `Construct` order the engine takes is
told to the builder's `OrderListener`s, so a worker drops its harvesting. A harvester may need a depot
(`NeedsDepot`): with none standing it keeps its load and waits by a thing of the kinds it names (`WaitBy`). A
factory may have several doors, a job going out by the one its reservation names (`ProductionReservation.door`),
and a module may hold one open (`holdDoorOpen`); it may give back the side's price at the moment of a cancel
(`RefundsPriceNow`). A holder may let passengers out along an exit path (`ExitStart`, `ExitEnd`), through its own
walls to free ground or its rally point.

### What a player sees, and of whom

A thing's fog range stands apart from its sight: a template's `FogRange` and a thing's own at run time
(`setFogRange`) clear the fog, its sight still what it looks for targets by; a site clears only over its footprint,
and a finished thing may be seen by every player within `SeenByAllWithin`. A passenger sees nothing unless its hold
lets it see out. A hidden thing's shot may be shown anyway, by the kind of thing or the weapon
(`setShownWhenHidden`, `ShownWhenHidden`), and its other shots kept from its allies too
(`setHiddenShotsToOwnerOnly`).

### Blasts, what went off, and weapons that touch

A blast reaches a thing's bounding sphere; `NOT_SIMILAR` and `SELF` read off what went off (`Shot.wentOffAs`) and
what made it (`GameObject.getProducer`, set by a factory); a contact weapon closes until it touches.

### What is in the way on the ground

`GameLogic.setObstacleRules`: the kinds of thing in the way (the reference's structures), the kinds never in it,
and how far over the ground a thing may stand and still be; a fence is in the way along its line alone
(`FenceWidth`, `FenceOffset`); a still thing turned or moved is laid again; a thing inside another is in nobody's.

### A thing drawn as another, or as nothing

`GameObject.drawAs(template, player)`: another template's look to every viewer, in that player's colours to those not
on its side (`UnitView.looksAs`, `wears`), faded through the change (`setDrawnOpacity`); the `Disguise` seam,
a thing passed off as none of a side's targets taken only by a forced attack. `UnitVisual.noShape`: a thing drawn as
nothing, and picked by nothing.

### Effects that ride, strips that rise, bones where they are drawn

`World.effect(name, thing, bone, offset)` plays a particle system riding a thing until `endEffect` stops it.
`World.strip(name, where, seconds, rise)` plays a strip of pictures (`Visuals.strip`) at a place, rising and fading.
`Bones.inWorld` reads a bone on any model a thing is drawn with, at a clip's last frame, turned with its turret,
matched without case.

### Sounds held to budgets, the camera's keys, and the mouse

A cue's `Limit` of how many play at once, and its priority within the game's budget of sounds at a place and flat
(`SoundBank.budget`). A `CameraFrame`'s turn-key and zoom-key speeds and the share the eye closes a frame
(`CameraAdjustSpeed`). The right button's scroll under either mouse, its pointer and its `floor`. The ground rule
told whether the player has ever seen the point (`SeenGroundOrder`); the moment that answers a click giving the
game's word (`Visuals.orderAnswer`), and how it is marked — the order mark, the thing flashed, or nothing
(`Visuals.wordMark`); a thing flashing as it is selected (`Visuals.selectionFlash`).

### Looks by words

A transition's keep group, its particle systems, and `waitFor`; a clip's slowest and fastest speed; a system at a
missing bone at the thing's place; a glow its own player alone sees (`Visuals.ownGlow`); a kept-dead thing drawn by
its words and health. A shadow under the picture its words choose, a picture hidden from its owner's enemies, one of
no size as large as its model (`UnitView.hostile`). Several marks by the health bar at once, each placed as the
reference places its own (`Visuals.MarkPlace`), a strip starting on a picture drawn at random. Debris that sounds
where it strikes, trails a system, plays clips, lands with a list, takes its thrower's colour, lies out its life from
rest and slides to a stop (`EffectList.Debris`). A laser's picture tiled between none and 50 times.

### The ground

A map may turn cells to be drawn cut along the other diagonal (`@Flipped`) and lay a cell's picture by its corners
(`@PictureCorners`), for the drawing alone. A game may burn marks into the ground (`DukeGame.markGround`), kept
whatever any player sees, and lay strips of pictures along it — roads (`DukeGame.layStrips`); every scorch lies on
the ground's rise and fall. Water (`Visuals.water`, `MapArea.river`), the ground lit by lights of its own
(`Visuals.groundLight`, `thingSuns`) and pictures multiplied over it (`Visuals.groundShade`,
`Duke3D.groundShades`). Trees that sway (`UnitVisual.sway`), topple under a crushing vehicle and sink away
(`ToppleUpdate`).

### Ground movers keep cells of their own

The grid keeps, per cell, the ground mover standing on it and the one going to it (`PathGrid.movers()`). Each mover
covers the reference's block (`Block`, `Pathfinder::getRadiusAndCenter`): a square of 1 to 5 cells from its bounding
circle, centred on a cell or on a corner. A move to a place takes the nearest block the mover may have and can walk
to, spiralling out over 400 cells as `adjustDestination` does. A block it may have has no stone, cliff or ally's goal
on it, and no still enemy it cannot drive over. It holds that block until it moves again, and it has arrived once the
rest of its route is shorter than its close-enough distance. Two still movers less than half a cell apart move apart
onto blocks of their own. A place out of its reach takes a block round the place on the place's own side, as the
reference's `checkForAdjust` does, and the mover goes as near as it can and says it stopped short. A route costs as the
reference's does: 10 a step, 14 a diagonal, and 4, 8 or 16 more for a
turn of 45, 90 or 135 degrees. A cell an ally stands still on costs 42 more, as does one an ally is passing within 10
cells of the start. A still enemy that cannot be crushed closes its cell.

### Movers give way instead of shoving

As the reference's `AIUpdate` settles it (`blockedBy`, `calculateMaxBlockedSpeed`, `hasHigherPathPriority`):
- Touching another ground mover, a mover is held only when it drives into it: the other within 45 degrees of its
  heading (34 if the other stands still), the two not drawing apart, and itself more than a cell from its goal.
- Held, it goes no faster than the other draws away. The limit falls 5% a frame while it is held and grows back 5%
  a frame after, from a fifth of its speed.
- Held two seconds, or at once behind one standing still while it already faces its way, it plans again round
  them, keeping its body clear of theirs. Planning round the same ones a second time without a step gained, it
  passes through them for two seconds, as the reference lets a blocked-and-stuck unit path through units.
- Of two held by each other, the one of lower path priority steps aside. A mover on wheels or treads held by one on
  legs has the one on legs step aside. Stepping aside is to the nearest block clear of the other's route, for up to
  ten seconds, or through movers where there is none.
- Legs pass legs, and a crusher is never held by what it may crush. A box never turns into another's footprint, and
  a mover brushes past one beside it rather than stopping. A route found through idle allies standing still asks
  them aside (`moveAllies`).

### Movers speed up, slow down and turn as their locomotors do

`MoveUpdate.Data`'s `Gait` is the reference's locomotor appearance.
- `LEGS` turn as they go, aim at their speed less the share of 45 degrees they are off, and ease to their least speed
  near the end.
- `TREADS` go at 0.6 of their speed off their way near a point, brake within `(v / 1.5) × (v / Braking)` of the end
  and slide onto it.
- `WHEELS` turn only while they roll, at a turning speed of a quarter of their speed or `MinTurnSpeed`, whichever
  is more; they slow more than 9 degrees off, and back up or turn in three points where `CanMoveBackwards`.
- Acceleration and braking have damaged values used below `damagedBelow` of the most health.
- `OTHER`, and any mover that names no gait, moves as things always moved.

### A group sent to one point

A `MoveTo` naming several units goes to the game's `GroupLayout` on the simulation thread; the same happens on every
machine. `GroupMove` is the reference's `AIGroup::groupMoveToPosition`:
- A player's click inside the group's bounding rectangle scaled by 0.5 gathers it: nearest first, each onto the
  nearest free block to the point.
- Otherwise each goes to the point plus its offset from the member nearest the point, cut to six times its size. It
  is pulled along the line toward the point onto the free block nearest it, and never onto a block whose walk from
  the point costs more than 1.4 × (|dx| + |dy|), which is one behind a wall.
- A group whose nearest member is 100 or more away walks one shared route, six cells wide. That happens when it is
  over 500 away, spread over 500, larger than 6 infantry or 4 vehicles, or its infantry all have a clear line to its
  middle member. Its infantry walk the route in 3 columns (5 from 16 of them) and end 22 apart across the last leg,
  each 22 behind the one before; a locomotor that moves in the middle or at the back ends 10 or 20 further back.
  Its vehicles do the same only on a route that bends more than six cells from its end: 2 columns walking, ending in
  3 columns 32 apart (2 columns 30 apart when fewer than 5).
Legs are the infantry, wheels and treads the vehicles. A mover walks a column's corners with `moveThrough`, holding
its end place from the start. `GroupMove`'s numbers are the reference's (`GroupMove.REFERENCE`), and a game may give its
own numbers or its own layout (`RtsSimulation.setGroupLayout`).

### A factory's units stand round its rally point

The leg to the rally point takes a block of its own like every move to a place. A unit made next is not stood on the
first, and the first keeps the rally point's own block: allies are asked aside only when a route actually crosses
them, and the line pulled straight goes round a still ally as the route did.

### A click on open ground gives a rally point only as the game says

The client decides a click on open ground in one place. The game's word comes first, where it has one. Otherwise the
click is one move for whatever selected can move. Otherwise, only in a game that names no ground orders, it is the
rally point of a lone building of the player's. Otherwise it is nothing, and the pointer shows `Deny` there.
`UnitView.mobile` says whether a thing can move. `DukeGame.namesGroundOrders()` says whether the game decides.

### A rally point shown while its building is selected

`ProductionUpdate.rallyLine()` is the reference's line (`W3DWaypointBuffer::drawWaypoints`): from the door's create
point, or the building's own position where it has no exit, through its natural rally point to the rally point. Where
the rally point lies behind the door, the line goes round the footprint's corners as the reference's own tests take it.
The snapshot carries these for the viewer's own things (`WorldSnapshot.rallies`). `Visuals.rally(RallyLook)` names the
flag, its clip and facing, and the node model; it also sets the line's width, colour and picture. While exactly one of
the player's buildings is selected, the flag stands on its rally point in his colour. Every selected building of his
with a rally point gets its line, added and drawn over everything, with a node at the natural rally point and each
corner. A game that names nothing gets the reference's 1.5-wide blue line and no models.

### A drag box takes the player's units

As the reference's `SelectionTranslator` has it, a box takes:
- the player's units, leaving his buildings out;
- his one building where it is all of his in the box, and nothing where there are more, the selection let go;
- with nothing of his, one other side's thing alone in the box.
An empty box keeps the selection. With the add key held, units are added to a selection of his units, and replace
any other. How far a press must move to be a box is the game's (`Visuals.dragDistance`, the reference's
`DragTolerance`, 25); it is 5 unless named.

### A health bar drawn as an RTS draws it

`UnitBarLook.plain(Plain)` is the reference's `Drawable::drawHealthBar`: an outline and the fill inside it, 3 and 1 in
the reference. It is shown over every thing, or only over the selected ones and the one under the pointer. Its width
is the thing's two radii added, kept between 20 and 150, times 2, at least 20, times a height the game names (232) over
the eye's height. Its point is the geometry's top plus 10, with 45% of the width to its left. Each bar's colours come
from the game's `BarColours` of the thing as the client sees it (`Visuals.barColours`). The reference's default runs
green to yellow to red, with the outline at half, and blue to cyan while a thing goes up or is disabled. A colour of
null means no bar for that thing. A thing's marks are drawn where its bar would be, shown or not.

### An armed button given up or used by the game

`Duke3D.giveUpAim()` gives up the button the game armed, as Escape does; its armer is told `GIVEN_UP`.
`Duke3D.useAim(place)` presses it there as a click would, `pressCommand(id, place, its facing, -1)`, as the reference's
radar presses the armed command at its point; the armer is told `USED`. Both work from any thread.

### A move the game gives, answered as a click is

`Duke3D.answerMove(place)` answers a move the game gave, such as a radar press, as the player's own click is answered:
the order mark, and where the game named one the move model. A lone structure selected gets no model.

### The pointer over the game's canvas

`Duke3D.canvasPointer(situation)` names the pointer shown while the pointer is over the game's own canvas; null hands
it back to the client. Over the world, the client's own pointer is shown either way. `DukeGame.getPointedAt()` is the
thing the window's pointer is on, or -1, whatever is selected, for a game's own canvas to say what lies under it.

### The player's camera, as the game frames it

`Visuals.cameraFrame(CameraFrame)` sets the player's camera — the reference's `W3DView` and its camera lines: the pitch
it keeps while the player holds it, its field of view across the whole window with the height following the world
region's shape (as `Set_Aspect_Ratio` keeps the horizontal half-width), its nearest, furthest and starting distances
along the line of sight, how far a wheel notch moves it, and the pan speed in world units a second whatever the zoom,
times the player's own `Duke3D.scrollSpeed(share)`. A middle drag turns the view by the frame's angle a pixel and a
middle click — under 5 pixels, within 5 of the reference's frames — puts it back as a reset does. A `GameCamera` pitch
left NaN keeps the player's. `Visuals.rightDrag(RightDrag)`, under `Mouse.LEFT_COMMANDS`, scrolls the view while the
right button is held — by the pointer's offset from where it went down, times the game's factors, the anchor dragged
to within a share of the window, the reference's `SCROLL_RMB` — and lets go of the selection, or gives up an armed
button, only for a click within the game's pixels, milliseconds and camera movement (`DragTolerance`,
`DragToleranceMS`, `DragTolerance3D`). Whenever the view scrolls the pointer is the scroll picture pointing the way.
Unframed, the camera is the client's own, as it was.

### What the view covers, and the view moved by the game

`DukeGame.viewRays()` is what the player's view covers as the client last drew it — the eye and the rays through the
four corners of the world's part of the window, in world units with z up — set every frame, readable from any thread,
for a radar of the game's own to outline as the reference's `W3DRadar::reconstructViewBox` does. `DukeGame.moveView(x,
y)` puts the player's view over a point from any thread, the reference's `TheTacticalView->lookAt`: at the client's
next frame, turned and as far back as it was, the camera staying the player's and his scrolling going on from there.

### A site put down with its order

`PlacementRules.siteAtOrder` puts a site down the moment its order is taken, as the reference's
`DozerAIUpdate::construct` does: at the start share of its health, awaiting its builder, seen, shot at and in the way,
while the builder drives to it. A builder that gives up leaves it standing with the money in it.

### Looks that pass between words

`UnitVisual.transition(fromWords, toWords, model, clip, mode, speed)` plays a clip once between two of a layer's looks —
the reference's `TransitionState`, a fence rising as a site appears and folding back when it is done — and a layer
drawing nothing in the new look is drawn until its way out has played. `particles(words, bone, system)` runs a
particle system at a bone while the words choose that look (`ParticleSysBone` per condition state: a scaffold's
sparks, a factory's steam). `layer(name).hungOn(bone)` draws a layer at a bone of the thing's body or of another
layer, following it (`AttachToBoneInAnotherModule`). A look is chosen once for each set of words a thing holds, where
it was weighed again every frame. `HitNumbers.NONE` throws no number off a thing hurt or healed, the flash and the
alert going on as before.

### Decks laid over the ground

`GameLogic.addDeck(four corners)` lays a floor over the ground at run time and gives its number — the reference's
bridge, classified as `PathfindLayer` classifies a layer's cells, walked at the height of the plane through its corners
with the ground kept under it. A thing is on the ground or on one deck (`GameObject.getFloor`), put on the one whose
height is nearest where it is placed, and gets on and off only at a deck's ends, as its route says; things are in each
other's way only on one floor. The ground under a deck lower than the clearance (`setDeckClearance`, the reference's
10) is closed; higher, units pass under while others drive over. `setDeckOpen` closes one — a bridge destroyed —
handing whatever is on it down to the ground and telling the game who (`onDeckClosed`); every route is planned again.
A click over a deck lands on it. Floors and closed decks are in the checksum and the save.

### A click picks what is drawn

The click and the pointer test each piece drawn now as the box round its own mesh, turned and placed as the piece
is — the reference's `RTS3DScene::castRay` — skipping pieces hidden now and pieces drawn see-through or added (a
muzzle flash, a headlight's beam), unless a thing has nothing else; the nearest hit along the ray wins.

### A click on the ground may carry the game's word

`DukeGame.groundOrder((selection, place) -> word)` is asked, as `contextOrder` is, where the pointer is on no thing —
the reference's `MSG_DO_SPECIAL_POWER_OVERRIDE_DESTINATION`, a beam or a gunship steered by any click. The snapshot
carries the word, the pointer shows it, and the click sends `GameOrder(word, selection, place)` instead of a move.

### Effects, debris and beams the simulation plays

`World.effect(name, place, facing)` and `effect(name, thing)` post an `EffectPlayed` — the reference's `FXList`
played from object creation lists and behaviours — which each client plays from that frame where it sees the point: the
moment of that name, or the effect list, effect or particle system; one on a thing rides it. `DukeGame.effect` does the
same from any thread. An effect list may throw `Debris`, the reference's `CreateDebris`: a model or one piece of it
flung up and out, spinning, falling (the reference's gravity, 1), bouncing with a share of its speed, fading at the end
of its life. `World.beam(look, from, to, width)`, `moveBeam` and `endBeam` make, move and end a beam the simulation
owns, carried in `WorldSnapshot.beams` to whoever sees an end and drawn with the game's `Laser` look — the reference's
`W3DLaserDraw` field for field: nested lines, additive, a scrolling tiled picture, an arc. All of it outside the
checksum.

### Things hidden, kept from some players, unselectable, and kept dead

`ObjectStatus.HIDDEN` keeps a thing from everyone while its modules run; a `Concealment` module keeps it from some
players — not seen, picked, targeted or caught by a blast, its shots left out of their moments, never from its own
side. Its look to the rest: `Visuals.seeThrough(word)` draws it to its own side and allies pulsing from its template's
faintest (`UnitVisual.seeThrough`) to whole, as `StealthUpdate` does, and blinks its blip; `Visuals.glow(word)` draws
it to the others as a glow in the reference's heat-vision colour instead of its model, and to its side as a light over
it (`neverGlows` for a mine). `UnitView.allied` tells the client which is which. `ObjectStatus.UNSELECTABLE` keeps a
thing out of every selection a while. A `KeepsDead` module keeps a dead thing in the world while its death plays out,
its death told at once, the reference's `SlowDeathBehavior`.

### Sight, protection and a blast's reach

`GameObject.setVisionRange` sets a thing's own sight; `GameLogic.setSharedSight` lets a game lend another side's sight
to a player (the reference's CIA Intelligence); `setTargetableFrom` keeps a thing out of every enemy's aim until a
frame. A `Weapon`'s `Affects` names whom its blast hurts — allies, enemies, neutrals, the firer, not its own kind, not
the air, the reference's `RadiusDamageAffects` — and `SecondaryDamage` within `SecondaryRadius` is its second ring.
`WeaponUpdate.refill(share)` refills its clips in part; an `AimOffset` on a target throws direct fire off it.

### A body, and a module, told more

`DamageListener` tells a thing's modules each blow and heal; a body may see the whole blow before it takes it; and its
damage scale multiplies what it takes after armour, but for the damage the game names unresistable. `Module.onCreated`
is called once a thing is made, before its first update. `OrderListener` tells a unit's modules the standard orders it
is given. `Locomotor.setSpeed` sets a mover's speed and turn in place.

### Production, prices and power while the game runs

`RtsPlayer.addPriceChange(kind, percent)` changes what a side pays for a kind of thing, the reference's
`CostModifierUpgrade`; `priceOf` is what is charged. `PowerModule.setBonus` changes a module's output while the game
runs. A `ProductionReservation` module has its say as a unit is queued and hears its job called off; jobs have ids.
`DukeGame.brings(rule)` names what a template leaves behind, for the match's art. A builder handed to another side
stops raising its site.

### Passengers, riders and bones

`ContainModule`'s `PassengersFire` lets passengers fire from inside, and its `RiderBone` stands them on top at a bone of
the carrier's model, firing and dying with it (`UnitView.ridesOn`). `ModelBones` and `Bones` give the simulation
where a model's named bones stand, as the client draws them, the same on every machine.

### How things are drawn by their words

`UnitVisual.groundPicture(words, picture, width, depth, fadeFrames)` lays a picture under a thing — a horde's ring, a
shadow — following it and fading with its words. `mark(words, frames, …)` plays a strip of pictures by its health bar,
the reference's Enthusiastic icon, and `tint(words, red, green, blue, easeFrames)` adds a colour to it, eased in and out.
`alongALine(first, middle, last, across, up)` draws a thing with a line (`GameObject.setSpan`) as its model's pieces
laid from end to end, the reference's bridges, fogged as the ground. `OrderMark.contextRing(colour)` answers an order
the game named on a thing with the attack's ring in that colour. A relief may split its cells along the other diagonal
(`@Relief(diagonal = ANTI)`).

### The host leaving is not the end

A network match goes on when the host's machine goes. Every guest listens from the start, and the host tells each
where the others listen before the first frame, so the order they fall back in is their seats, agreed by all: the
next living player takes over relaying and the others reconnect to it — the reference's packet router fallback.
Nothing is lost or run twice on the way. Every machine resends what it held for the frames still in play and says
`Resent`; only then does the new relay say the old one has left, from a frame past every order of its anybody held.
The match waits while it is found again, as it does for a slow player, and a machine that reaches nobody within five
seconds plays on alone, as in the reference. `LockstepGate` takes a `RelayFallback` for it, and `MultiplayerSession`
gives every gate one.

### A side handed to another player

`RtsSimulation.handOver(things, to)` hands things to another player — the reference's `transferAssetsFromThat`, for a
side that quits with a living ally or anything a game gives away — and `handOverAll(from, to, money)` a whole side,
with its money where asked (`RtsPlayer.handOver`, in neither book). Called on the simulation thread — from `onOrder`,
or `onPlayerLeft`, now told on the same frame everywhere — every machine hands over the same things on the same
frame. What the engine keeps for an owner follows each thing: a factory's queue and rally point, its power, its sight,
its experience. The client paints a thing handed over in its new owner's colour, and lets it go from the old owner's
selection.

### A site that rises, and the words it holds

`PlacementRules` may name `SiteWords`, the words a site holds while it goes up — the reference's
`AWAITING_CONSTRUCTION`, `PARTIALLY_CONSTRUCTED` and `ACTIVELY_BEING_CONSTRUCTED`: the first until a builder first
works on it, the second from then until it is finished, the third on the frames a builder works on it. A building
being sold holds the last two while it comes down. `UnitVisual.risesAsBuilt()` draws a thing rising out of the
ground as it is built and sinking as it is sold — the reference's `ADJUST_HEIGHT_BY_CONSTRUCTION_PERCENT` — from
`UnitView.built`, the simulation's own progress, so every machine shows the same height; `risesAsBuilt(height)`
sinks by the height the game names, the thing's own, as `W3DModelDraw::adjustTransformMtx` does, whatever its model.

### A factory's working words, and a thing drawn by several models

`ProductionUpdate.Data` may name `Words`: `busy` while anything is in the queue — the reference's
`ACTIVELY_CONSTRUCTING` — and `made` for `madeFrames` from a unit finished (`CONSTRUCTION_COMPLETE`), not started
again while it holds. `UnitVisual.layer(name)` draws another model with a thing — a factory's door, its crane, its
scaffold, the reference's second draw modules — a look of its own with the same means as the thing's: models by
words (`model(words, null)` hides it), pieces, clips by words, rising as built, size and facing. Each layer is chosen
by the thing's words on its own and painted in its owner's colour; picking and rings stay with the thing's own look.

### An order answered with the game's own model

`OrderMark.model(path, clip, frames)` answers a move, and an attack-move, with a model laid on the ground where the
order goes — the reference's `MoveHintName` — its clip played once from its first frame, gone after `frames`
thirtieths of a second. A new order from the same selection moves its mark rather than laying another. Those orders
alone, as there: with a model named, a rally point, a power's place and an order the game names get no mark, and a
lone building told to move gets none. `attackRing(false)` answers an attack on a thing with nothing, the pointer
having said it already, as the reference does.

### Text floated up from the world

`DukeGame.floatText(text, x, y, z, argb)` floats a short text up from a point — money earned there, a bounty — as the
reference's floating text does. It is an event (`TextFloated`, which the simulation's own code may post too), seen
only by a player whose view of the point is clear — or, where it says so, by the players it names (`shownTo`), or
by the owner of the thing it is about and whoever may see that thing (`about`, `DukeGame.floatTextAbout`), so a
hidden building's money is not floated to its enemies. The client draws it rising and fading as
`Visuals.floatingText(rise, hold, fade)` says, the reference's 1, 10 and 0.1 where the game says nothing.

### Harvesters told where to work

`HarvestUpdate.workAt(place)` sends a harvester to a pile to fetch from, or a depot of its side to deliver to — the
reference's preferred dock, one place, the last named — and it goes back there after every delivery, however far,
until told another or the place is gone. It loads only beside its pile and is paid only beside its depot (see what to
change). Any other order its player gives it (`OrderListener`) stops its work, its load kept, until the next
`workAt`, as the reference's truck goes idle.

### A hold shared by a side

`ContainModule`'s `SharedBy` names a network: every thing of a side whose hold names it holds one list of passengers
with one capacity — the reference's tunnel network. What gets in at any of them may get out at any of them; the
passengers live through the loss of any but the last, and die with that one — or, with `PassengersVanish`, leave the
world quietly (`GameObject.vanish`: no `ObjectDied`, no die module, no kill), as a collapsed network removes them. A
building of it that is sold leaves the network at once (`ContainModule.sold`). `RtsSimulation.sharedHold` reads the
list. A passenger comes out on the nearest clear ground outside its holder, tried round it from its front, or at the
`ExitBone` its model names.

### A module told it is taken off

`Module.onRemoved()` is called when a module is taken off its thing — an errand given up for a new order, a module
swapped for another — on the simulation thread, before the thing's modules next update, so it can take back what it
put on the thing, such as the words it set for its show.

### A game's own orders

`GameMessage.GameOrder(playerIndex, word, units, place, target, number)` is an order whose meaning is the game's,
such as a special power fired at a place or a science bought. It travels as every order does: posted with
`DukeGame.postCommand` from any thread, sent to every machine, applied on a frame boundary in the order given, and
written into a replay. The game hears it on the simulation thread through `DukeGame.onOrder`
(`RtsSimulation.onOrder`) and does what it means there. The engine never reads the word or the number. On the
wire it is `ORDER`, with the word percent-encoded so it may hold any character.

### Deaths heard, and a side's books

`DukeGame.onDied` (`GameLogic.onDied`) hears every death as it is reaped, on the simulation thread. It gets the
same `ObjectDied` the client's event carries, beside it rather than from it, with `killerPlayerIndex`: the side
whose blow it was, taken when the blow landed, so a kill is credited even when the killer is gone too. A thing
removed without dying, such as a sold building, is not heard. A side's books keep what it earned and what it
spent apart where its balance nets them (see what to change).

### The selection both ways

What the player selects reaches `DukeGame.getSelection()` whatever draws the HUD, so a game that draws its own
command bar hangs it off the selection as the client's bar does. `DukeGame.select(ids)` goes the other way, from
the game's own code on any thread: the client takes the pick up at its next frame, in the game's order, under the
rule a click follows (the player's own things, or one of someone else's alone).

### A factory's exit and door

`ProductionUpdate.Data` may name an `Exit` and a `Door`, the reference's production exits and factory doors. With an
exit, a unit is made at `CreatePoint` and walks first to `RallyPoint`. Both points are in the factory's own frame,
turned and placed with it. The unit is made facing the factory's way and walks straight out through the factory's
own walls (`Locomotor.leave`), then by a route to the rally point the player set, or back to the door's point. After
one leaves, the next waits `Delay` frames, except for the first `Burst`. A finished unit that may not leave yet waits,
complete, at the head of the queue.

A door opens when a unit is finished, and the unit is made the frame it is open. The door stays open for its time after
the last one left, and a unit finishing meanwhile leaves at once. Then it closes, and a unit finishing while it closes
has it open again. The factory holds the three words the game names (`DOOR_1_OPENING`, `DOOR_1_WAITING_OPEN`,
`DOOR_1_CLOSING`) for exactly the frames given. A factory that names neither lets its units out of its side as before.

### Clips chosen by the words a thing holds

`UnitVisual.clip(conditions, clip, mode, start, keepGroup)` plays a clip while the words a thing holds best fit
`conditions`, in place of its idle and walk. `ONCE` stays on its last frame, `ONCE_BACKWARDS` returns to its first,
`LOOP` and `LOOP_BACKWARDS` go round, and `HOLD` stays on one frame. It starts at `FIRST`, `LAST` or `RANDOM`. It
starts again when the choice changes, unless the two states share a keep group, when it carries on from the same
fraction. Its time comes from the game's frames, so every machine shows the same frame. Words given no clip play the
roles as before.

### The pointer says what a click would do

- `Visuals.pointer(situation, strip, frames, hotX, hotY, jiffies)` animates a pointer: pictures side by side, each
  shown for its sixtieths of a second.
- `Move` is open ground where the selection would walk.
- `Scroll-N` to `Scroll-NW` show whenever the view scrolls: the keys, the window's edges, the right button held.
- `DukeGame.contextOrder(rule)` lets the game name the order a click on a thing would give what is selected: `Enter`,
  `Dock`, `Repair`. `WorldSnapshot.contextOrder` carries it, the pointer shows that word's picture, and the click
  sends it as a `GameOrder` for `onOrder`.

A situation the game drew no picture for falls back: Move to Point, a word to Friend or Attack, a scroll to whatever is
under it.

### A power's picture on the ground

`Duke3D.aim(button, radius, pointer, decal, ended)` lays an `AimDecal` on the ground under the pointer while armed,
in place of the ring: a picture 2 × radius across, following the ground, its opacity throbbing between two values as
the reference's radius cursors do, and seen only by the player aiming.

### The reference's mouse

`Duke3D.mouse(Mouse.LEFT_COMMANDS)` is the reference's own mouse: a left click on one of the player's own things
selects it, and anywhere else, with something of the player's selected, is the order the right button gives by
default — a move, an attack, the context order. A right click lets the selection go. A left drag boxes either way,
and `Mouse.RIGHT_COMMANDS`, the default, is the mouse as it was.

### A model's pieces, by the words a thing holds

`UnitView.conditions` carries the words a thing holds to the client, which chooses its look by them with the
world's and its health's. `UnitVisual.pieces(words, hide, show)` hides and shows pieces of the model while the
words best fit, as the reference's `HideSubObject` and `ShowSubObject` per condition state do: sticky, a state
changing only what it names, and kept when the model is swapped for a damaged one. `fireBone`, `muzzleFlash` and
`recoilBone` take a set of words too, so an upgraded turret fires from its own muzzle.

### Model materials drawn as the file marks them

A glTF material with `extras.blend` set to `ADDITIVE` or `MULTIPLY` is drawn added to or multiplied with what is
behind it, as the reference's shaders draw a headlight's cone or a shadow decal. One glTF marks `BLEND` is
blended, and `MASK` is cut out at its `alphaCutoff`. The added, multiplied and blended ones write no depth and
are drawn in the transparent bucket.

### Treads that run, wheels that roll

`UnitVisual.treads(left, right, rate, driveFraction, pivotFraction)` and `UnitVisual.wheels(bones, multiplier,
front, steerDegrees)` are the reference's `W3DTankDraw` and `W3DTruckDraw`, worked out by the client from where a
thing stands in each new frame of the game, so nothing of them reaches the simulation. A tread's picture runs along
u at `rate` lengths a second: every tread with the vehicle when it drives faster than `driveFraction` of its
speed, the two sides opposite ways when it turns slower than `pivotFraction`, and not at all otherwise. The speed
is the fastest the client has seen the thing go. Treads are found by the start of their names, so `TREADSL` is
`TREADSL01`, `TREADSL02` and so on, and a tread both names match is neither side's. A wheel rolls `multiplier`
radians a unit travelled (one over its radius rolls it true) about the vehicle's own side, and the front ones steer
toward a turn.

### What a container holds, a sound's end, the map revealed

`ContainModule.getPassengers()` gives the ids of what rides inside, in the order they got in, and
`UnitView.passengers` carries the same list to the client. `Duke3D.sound(cue, ended)` tells the game on the
window's thread when what it played has played out: at once where nothing played, and at the next line's start
for a cue that cuts itself off. `DukeGame.revealMapTo(player)` reveals the whole map to one player for the rest
of the match, from code on the simulation thread (the reference's `MAP_REVEAL_ALL_PERM`). It is in the checksum
and the save, and `WorldSnapshot.revealed` opens the client's shroud for that view. `DukeGame.watch()` makes the
machine a watcher mid-match: nothing selected or ordered, everything seen, and the machine still in the
lock-step.

### Held in place

`ObjectStatus.HELD` (the reference's `DISABLED_HELD`): nothing moves the thing. A move order is taken and goes
nowhere, and a pursuit does not close in; a flyer hangs where it is. Its weapons still fire at whatever comes
into range. It is part of the checksum.

### What a side may build

An `Object` block may name `Prerequisites` (each entry one requirement, its templates separated by `|`: any one of
them, and every entry), `RequiredWords` (words the side must have been granted: sciences), `Buildability` (`YES`,
`NO`, `ONLY_BY_COMPUTER`, `IGNORING_PREREQUISITES`), and `MaxSimultaneous` with a `MaxSimultaneousLinkKey`
that things sharing a cap share. What counts toward a cap: things alive, sites included; factory queues; and
builders on their way. `RtsSimulation.canBuild(player, template)` answers, and `QueueProduction` and `Construct`
refuse what it refuses, at no cost. `grant(player, word)` gives a side a word, `setCap(linkKey, most)` caps a link
key for the match (a game's superweapon option), and `countsAs(rule)` is the game's own equivalence: a reskin
counting as the thing it reskins. `DukeGame.computer(player)` marks a computer side.

### Selling, attack-move, guard, evacuate

`GameMessage.Sell` puts a building up for sale. It is marked `SOLD` and stands in scaffold for
`SellRules.scaffoldFrames`, then comes down `percentPerFrame` a frame, from just under 100 to -50%, as the
reference runs a building's construction percent backwards. Its health is not touched. Then it is gone: not
destroyed, told through `StructureSold` and `DukeGame.onSold`, with `sellShare` of its cost refunded, or its
`RefundValue` where it names one. A building killed while it comes down pays nothing.

`AttackMove` walks to a place and fights whatever it meets on the way. `Guard` holds a place or keeps to a thing,
in one of three modes: `NORMAL`, `WITHOUT_PURSUIT`, or `FLYING_ONLY`, where only what flies is engaged. The radii,
the chase time and how often it looks are `GuardRules`. `Evacuate` empties a container and `ExitContainer` lets
one passenger out. Every one has its line in `CommandCodec`; the numbers are the game's, the reference's by
default.

### Words in a fight

`ActiveBody` may carry `ArmorSets`, each with the condition words it is for, and the best fit to the thing's words
is its armour. The weapon bonus table (`WeaponBonus`, `DukeGame.addWeaponBonuses`) has one line per word, kind
and multiplier. It raises damage, range, rate of fire (the clip's reload with it) and blast radius for every word
a thing holds, the lines adding up as the reference's `WeaponBonus::appendBonuses` adds them: `1 + Σ(multiplier −
1)`. A `Weapon` block may carry `Bonuses` of its own, which apply to that weapon alone and add up with the
table's. `ExperienceModule`'s `LevelHealthBonus` scales the most health as a rung is reached, by its bonus over
the last one's with the share kept, as `ActiveBody::onVeterancyLevelChanged` does. `ExperienceModule`'s `LevelWords` sets the word of the rung
a unit stands on and lets go of the others, so all three follow its rank. `BodyModule.setMaxHealth(most,
change)` changes the most a body can have: keeping the share, adding the difference, or leaving the health as it
is.

### Players talk during a match

`DukeGame.say(text, to)` sends a line to the players named. Each machine addressed hears it, the sender's
included, in order, through `onChat` and, on the window's thread, `Duke3D.onChat`. A line to a player who has
left goes nowhere. Chat is outside lock-step: no frame waits for it, it is not a command, and it has no part in
a replay or the checksum.

### Pictures the game makes

`Picture`: ARGB rows from the top, drawn by `Canvas.drawPicture` like a file's picture: stretched, modulated,
blended and clipped. A picture that has not changed is not uploaded again: a radar's layers and a minimap drawn
by the game.

### The world drawn in part of the window

`Duke3D.worldView(left, top, width, height)` sets the part of the window the world is drawn in, the whole window
by default. The camera keeps its vertical angle and takes the region's shape — or, with a `CameraFrame`'s field of
view, keeps its width across the window. Picking, the drag box, placing and the bars over units all work inside that
part; edge scroll is the window's own edges. Outside it the world takes no pointer and draws
only black, under the game's canvas. The change shows from the next frame, and the camera does not move.

### A button on the game's canvas aims as the bar's does

`Duke3D.aim(button, ended)` arms the client's aim from game code. For a place, the ghost follows the cursor,
green or red by `aimFits`, turned by a drag. For a thing, the next click on one is the target. The click where it
fits is `pressCommand(id, place, facing, target)`. A right click, Escape, or arming another gives it up, and
`ended` hears `AimOutcome.USED` or `GIVEN_UP`. Another form adds a circle of a radius round the cursor and a
pointer the game names.

### Music in turn

`Duke3D.playlist(tracks)` plays the named cues' tracks once each, in turn, and starts again from the first after
the last: the reference's music list (`AudioManager::nextTrackName`). The same list asked for again changes
nothing, and a cue with no file is passed over. `music(track)` replaces the list, and null or an empty list is
silence.

### A factory says what came out of it, and a site says when it is done

`ProductionListener.onProduced(unit)` on a factory's own modules, `ConstructionListener.onConstructed(builder,
building)` on the building's the frame `UNDER_CONSTRUCTION` clears, and the same two for a game's code:
`DukeGame.onProduced` and `onConstructed` (or `RtsSimulation`'s). On the simulation thread, in the order things
happen. `ProductionUpdate.getEntries()` lists the queue, units and research, in order.

### Research in a factory's queue

An `Upgrade` may take `Frames` to research and be `PLAYER` (side-wide, and every thing the side makes later gets
it) or `OBJECT` (the researcher alone). `ProductionUpdate.Data.researches` names what a building researches;
`GameMessage.QueueResearch` puts one in the queue beside the units, paid for when accepted, and
`CancelProduction(index)` takes any entry out with its money. `UpgradeListener` and `UpgradeCompleted` say when it
is done; `CommandButton.progress` shades a button while its entry is under way.

### A thing hurt, as a moment

`ObjectHurt` — who, how much after armour, the damage type, the blow's point on the victim nearest the attacker,
and who dealt it — posted by `BodyModule` for every blow that does harm. The client plays
`hurt.<template>.<type>.<major|minor>` (`Visuals.hurt`, the line between the two a game's rule), falling back as
every moment's name does.

### A thing drawn at its own height, pitched and rolled

`GameObject` keeps a pitch, a roll and whether it keeps its own height; `UnitView` carries them with its `z`, and
the client draws a thing there — on the ground where it follows the ground, at its own height where it says so.

### Moving through the air

`FlyUpdate` is a locomotor for what flies, hovering or winged: a height it climbs to at its rate and holds, its
speed, acceleration, braking and turn rate, and for a winged one a least speed it circles at rather than stopping —
`ObjectStatus.AIRBORNE` while aloft. `Locomotor` has `moveTo`, `stop`, `isMoving`,
`stoppedShort` and `flies`, and `GameObject.getLocomotor()` finds whichever a thing has, so an order, a factory's
rally point and a pursuit treat a flyer as they treat a walker. Nothing aloft blocks the ground.

### Pathfinding a game can afford

A frame's path searches are held to a budget of cells — the reference's `PATHFIND_CELLS_PER_FRAME`, 5000,
`GameLogic.setPathfindBudget` — a search started only while the frame is under it and run to its end once
started; a mover refused waits its turn. `getCellsExaminedLastFrame()` says what a frame cost. The grid keeps its
connected `Zones`, recomputed when its shape changes: a goal in another zone is known to be out of reach at once,
and the search goes straight for the nearest cell of the mover's own zone; `standingNextTo` asks the zones instead
of searching. Eighty units chasing each other across walls cost 0.7 ms a frame.

### A canvas a game draws on, and the input under it

`Canvas`: pictures (a file or a part of an atlas, held a quarter turn or not, modulated by a colour, blended by
alpha, added, laid solid or in grey), filled and outlined rectangles, triangles, lines with a gradient, a clip
rectangle, and text in a `Canvas.Font` — a system font or a font file, a pixel height, bold, an average width it
is condensed to as Windows condenses a face, and a wide face for code points from 256 up. Clipped exactly, the
picture with its quad; drawn in call order over the world and the client's HUD by the game's `Painter`, every
frame, told the screen's size. `CanvasInput` sees the raw input first — pointer, buttons with double clicks,
wheel, keys with repeats, typed code points — and what it takes goes no further. `Duke3D.of(game,
visuals).canvas(painter).input(input)`.

`Shell.drawnByTheGame()` hides every menu and all of the client's HUD and opens on the game's front end.
`Duke3D.startMatch(match)` plays a match the game built from its own setup — a fresh `DukeGame` each time, a
match being played once — and `frontEnd()` ends it and goes back. A game with its own lobby hosts and joins with
`MultiplayerSession`, whose settings reach the guests whole, and attaches the session with
`DukeGame.multiplayer(session)`; `DukeGame.observe()` is a seat that watches.

### A sound the game plays itself, and how loud each kind is

`Duke3D.sound(cue)` plays a cue now and flat, on its own channel, with the cue's other rules kept; a name the bank
does not have plays nothing and is logged once. `cueVolume(cue, multiplier)` holds until changed, and
`volume(channel, v)` is the game's volume for a channel, multiplied with the player's own, as the reference
multiplies its script and system volumes. From any thread.

### A world behind the front end

`Duke3D.backdrop(recipe)` runs a match behind the front end — made fresh each time the front end is shown,
watched, the canvas over it, torn down when a match starts; `DukeGame.randomSeed(seed)` makes it play the same way
every time. `DukeGame.camera()` is the camera as the game drives it, from its own code on the simulation thread,
stepped once a logic frame: `moveTo(x, y, frames)` in a straight line at an even pace keeping pitch and zoom,
`lookToward(x, y)` to end the move facing a point, `angle`, `pitch`, `zoom`, `current()`, and `release()` to give
it back; while the game has it, `WorldSnapshot.camera` carries it and the player's controls wait.
`Duke3D.music(track)` plays a track by its cue, the old one fading out over two seconds.

`Duke3D.holdBackdrop(true)` keeps the backdrop from being made: its recipe is not asked and nothing of it is read,
so the game's movies play alone, as the reference plays its logo and trailer before loading its shell map. Let go,
it is made as before; asked before launch, the hold is there from the first frame. `onBackdropLoading(percent)`
hears its load as `onLoading` hears a match's, 0 to 100. The 100 comes once a frame of it has run, or when there
is none to be had. `DukeGame.placesEverythingAtSetup()` says a match places at setup everything it will ever draw,
so `templatesThisMatchCanDraw` plans from what stands in it with no skirmish map chosen. A shell map then reads its
own art rather than every look the game registered.

### A movie

`Duke3D.playMovie(movie, ended)`: a `Movie` is a zip of pictures in order, a rate and a sound, stretched over the
window or into a rectangle, under the canvas. Read ahead on a thread of its own; the sound keeps the time; the game
is told when the last picture's time is up; `holdingItsLastFrame()` stays on it until `stopMovie()`.

### Loading, drawn and told

A match is built on a thread of its own while the window draws. `Duke3D.onLoading(percent)` hears 0 to 100, rising,
each figure once, the canvas painted after every one — building the match the first 40, as `DukeGame.boot(progress)`
says its steps, reading the art the rest. `holdMatchStart(true)` keeps a loaded match at 100, not a frame of it
stepped, until `releaseMatchStart()`. In a network game every machine's figure reaches the others:
`DukeGame.onPeerLoadProgress`.

### A spot beside a thing that a mover can actually reach

`World.standingNextTo` worked out a spot on the straight line, and on a cliff the line ends on rock: the
harvester was sent there, found no route, stood with its goal still set, and flickered between "sent" and
"arrived" so its wait at the depot never finished. Now the spot is one the mover can reach — the straight one
where it can, else the reachable cell beside the thing nearest to it — and `Pathfinder.findPathOrNearest`
takes a mover sent somewhere it cannot get to as near as it can: `Path.reachesGoal()` says which,
`MoveUpdate.stoppedShort()` that it has given up short. On a real map's cliff-side depot: nothing banked in
two minutes before, 2400 after.

### A shot in flight lands the way an instant one does

A `ProjectileLauncher` is handed a `Shot` — who fired it and for which side, which `Weapon` from which slot,
and the final damage — so a unit with a gun beside a missile launches only the missile. `WeaponUpdate.land(world,
shot, victim, where, from)` does what an instant hit does from the hand-over on, as the same code: the direct
hit if the victim is still alive, the blast round `where`, the kill experience to the shooter if it is still
there, and `ShotLanded` — new, and posted for instant hits too, at the middle of what was hit. A victim of
`null` is a shell on open ground: the blast only.

### A death knows how it came

`DeathType` is an open vocabulary like `DamageType` (the engine names `NORMAL`); a weapon deals one —
`DeathType = EXPLODED` — and a blow carries it with the killer: `BodyModule.damage(amount, type, Death)`. The
body keeps the blow that killed it (`getDeath()`), `DieModule.onDie(Death)` is told, and `ObjectDied` carries
both. Running a thing over is new: `CrushUpdate` on what crushes and `Crushable` on what is crushed, the
reference's levels and `SquishCollide`'s rule, dealing `CRUSH` and the `CRUSHED` death. The client plays
`died.<template>.<type>` (lower case), which falls back to `died.<template>` for its sound and its look, and
`UnitVisual.die(type, clip)` gives a death its own fall.

### An effect list, and where the world's moments play

`core.content.EffectList` is the reference's `FXList`: entries played together — `ParticleSystem` (count, offset
turned with the thing, a ring's radius, height or the ground's, a delay replacing the system's own, rotations,
orient to, attach to, ricochet, the caller's radius), `Sound`, `LightPulse`, `Shake` (six strengths),
`Scorch`, `Tracer`, and `AtBone` for another list at a model's bones. `Visuals.effectLists(...)` hands them
over, and a list may be named wherever an effect may. `fired.<weapon>` now plays at the fire bone of the slot
that fired (`UnitVisual.fireBone`, numbered per barrel and taken in turn), whose `muzzleFlash` piece shows on
that frame only and whose `recoilBone` kicks back and settles; with no bone, at the thing's middle, or at the
target for a contact weapon. `landed.<weapon>` is new, at the middle of what was hit or where a shell came
down, turned along the shot; `died.<template>` plays at the thing's own height, turned with it. For that,
`WeaponFired` says its `slot`, whether it is a `contact` weapon, and its blast `radius`.

### The client's own controls, on the keys the game says

`Hotkeys.controls(KeyMap)`: every control of the client's on keys the game chooses, with Ctrl, Shift and Alt,
or on none — pan, turn, zoom and reset the camera, stop, scatter, the held force-attack, queue and
add-to-selection keys, select all (leaving out the kinds the game names), all of a kind, the same type on
screen and — twice — everywhere, next and previous, the base, the latest alert, the menu, the command bar,
chat (`Hotkeys.onChat`), a screenshot, pause, fullscreen. Control groups (`groupsOnDigits()`: Ctrl makes,
the digit selects and twice goes there, Shift adds, Alt goes) and camera bookmarks (`bookmarksOnFunctionKeys`)
are the player's own and never the simulation's. The digits build only while nothing else is on them. Force
attack reaches neutral things; a friend and the ground, and queued waypoints, are not yet.

### What a weapon may be fired at

`WeaponUpdate.Data.targets` names the classes a weapon may be fired at — `Targets = [GROUND, AIRBORNE_VEHICLE]`
— and a game gives the world its `TargetRule`s, `RtsSimulation.setTargetRules(...)`: the kinds a thing must
have (any one), whether it must be in the air (`ObjectStatus.AIRBORNE`, which a game sets while its aircraft
are aloft), and the classes it then has, the first line that matches deciding. Acquiring, keeping and taking
an order all ask `canFireAt`; an order to attack what nothing can hit is refused, and the pointer says so —
`DukeGame.setPointedAt(id)` out, `WorldSnapshot.attackable` back. A weapon that names none fires at anything,
as before. In the RTS this was measured in, 47 of 363 weapons can hit aircraft and 27 nothing on the ground.

### A weapon's clip

`ClipSize` (0 = none), `ClipReloadFrames`, `AutoReload` (yes) and `ReloadFramesMax` — `ReloadFrames` stays the
delay between shots, drawn up to the max from the simulation's own random numbers. The weapon says
`getStatus()` (`READY`, `BETWEEN_SHOTS`, `RELOADING`, `OUT`) and `getRounds()`, and `refill()` fills it. Both
waits are divided by the unit's `RateOfFireModifier`s and floored. A clip of three with 0.1 s and 1.0 s fires
at 0, 0.1, 0.2, then 1.2, 1.3, 1.4.

### More than one weapon, and sets of them

`Weapon` blocks, handed over with `addWeapons`, linked by name from `WeaponSlot`s in `WeaponSet`s:
`WeaponUpdate.Data.weaponSets`. The set is the one whose words the unit has — `GameObject.setCondition(word)`
— chosen by the rule conditional models use, now `core.thing.Conditions`. The slot is chosen per target as
the reference's `chooseBestWeaponForTarget` does: preferred-against kinds win outright; else the ready one
that deals most after armour (`BodyModule.estimateDamage`); ties to the lower slot. Each weapon keeps its clip
across a swap. `WeaponFired.weapon` says which fired. A block with no sets is the one weapon it always was.

### The simulation's own random numbers

There were none. `World.random()` is a `LogicRandom` — SplitMix64, a `long` of state — seeded by
`GameLogic.setRandomSeed` (`DEFAULT_RANDOM_SEED` otherwise), reset with the world and saved with it.

### A harvester waits at the depot

`HarvestUpdate.Data` gains `FramesAtDepot` (the money arrives at the end of them), and `FramesPerUnit` with
`UnitOfLoad` — a unit an act, one act more than it has units, a pile that runs out ending the wait early. Four
boxes with 1.0 s at the pile and 0.4 s at the depot stand 5.0 s and 0.4 s. Left out, as before.

### What an RTS makes a sound for

`fired.<weapon>`, `selected.<template>`, `ordered.<order>.<template>`, `moving.<template>`,
`<word>.<template>` for each `WhenHurt` word as health first falls below it, and `ambient.<template>` — a loop
that follows its thing and swaps to `ambient.<template>.<word>` while hurt. A cue may be heard by its
`OWNER` only, and may `interrupt` the last of itself; `Sound` blocks say both. `arrow_fired` is still played
for a nameless weapon where a game names nothing under `fired`.

### Particle systems

`ParticleSystem` blocks — the reference game's model, field for field — run a frame at a time at 30 a second
from the client's own random numbers and are drawn as squares facing the camera or lying flat, or as a streak.
Slave systems, systems riding each particle, and a budget by priority. A game hands them over with
`Visuals.particleSystems(...)`, and names one wherever it would name an effect; `died.<template>` and
`fired.<weapon>` take a look through `Visuals.moment`.

### Placing things turned, and building beside them

A `CommandButton` has a `facing` its ghost is drawn at; pressing on the ground and dragging turns it along the
drag, and the thing stays where the press went down. A builder already beside its own site raises it without
moving; box against box is now measured exactly; and a slow-turning vehicle reaches a point just behind it.

### A second overlay

`@Overlay` and `@Fade` may each mark more than one component; the n-th of each go together, and each layer is
drawn over the ones before it by `OverlayOrder` rather than by the camera's distance. A map with one pair
draws as before.

### Building in the world

A building is placed, not produced — and there was no order that put a thing somewhere.

| | |
|---|---|
| `GameMessage.Construct(player, builder, template, place, facing)` | the builder walks there; a site rises the frame it arrives; whole a build time later |
| `GameMessage.CancelConstruction(player, site)` | the share the game says comes back; the site is gone |
| `ObjectStatus.UNDER_CONSTRUCTION` | SAGE's status: the site is the real building, owned, seen, shootable — and inert but for what builds it |
| `UpdateModule.runsWhileUnderConstruction()` | the one guard, in `GameObject.updateModules`, rather than one in every module |
| `rts.construction.Placement` | off the map, too near the edge, on rock, too steep, in the way — the simulation's answer, only ever shown by a client |
| `rts.construction.PlacementRules` | the game's numbers: how steep, how near the edge, the refund, the health a site rises at |
| `RtsSimulation.fits(...)` | the same question, for a client to show |

The money goes when the order is **accepted** — the frame it is applied, on every machine at once. A place
refused costs nothing. A builder sent elsewhere, stopped, or unable to get there gives its errand up with
the money back in full. A site knocked down gives back nothing and leaves nothing. Health grows by being
added, so a site shot as it goes up is finished hurt. Two runs of the same orders end on the same checksum.

### A command button may ask for a place

`CommandButton.aim` — `NOW`, `GROUND`, `UNIT`. Pressing one that aims arms the cursor; the next click is the
place or the thing; a right click or Escape thinks better of it. The press comes back as a `CommandPress`
(id, selection, place, target) — **`onCommandPressed` now takes one**, where it took an id and a selection.
While a `GROUND` button is armed, its `ghost` — a template, or a model's path — is drawn at the cursor, green
or red by the game's `aimFits`, asked on the simulation thread through `DukeGame.setAim` and carried back in
`WorldSnapshot.aimFits`. A click on red is not sent.

### Ground that blends

`@Overlay` rows lay a second picture from the palette over a cell, and `@Fade` rows name the shape it fades in
by — `.` for none, or one of sixteen hex digits: from a side, a corner's triangle, each reversed. The masks
are the engine's, drawn as vertex alpha over four triangles a cell meeting at its middle, which makes every
shape exact; blended after the opaque ground in any order, because a cell has one overlay at most. A map
with no fade rows builds none of it.

Fixed on the way: 0.5.0 cut the painted ground along the other diagonal from the one `HeightMap` — what is
walked — cuts along, so on a slope the ground drawn and the ground walked differed. They agree now.

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

**The key on a button presses it** — after the game's own letters (`Hotkeys`) and its key map (`KeyMap`),
which come first.

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
"unlisted is 1.0" already meant. The five constants read exactly as they did; what went is `values()`,
since an open set has no list of all of it.

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
