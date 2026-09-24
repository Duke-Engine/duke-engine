# Changelog

Versions are `major.minor.patch`. While the major is 0, a minor may break what came before — and
when it does, this page says exactly what to change and how.

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
- Nothing else breaks. Every record that grew keeps its old constructors — `WeaponUpdate.Data`,
  `HarvestUpdate.Data`, `WeaponFired`, `Weapon` (and its own `Bonuses`), `WorldSnapshot` (and `revealed`,
  `contextOrder`), `SoundBank.Cue`, `Sound`, `UnitView` (and `passengers`, `conditions`, `built`), `CommandButton`,
  `Upgrade`, `ProductionUpdate.Data` (and its `Exit`, `Door` and `Words`), `PlacementRules` (and its `SiteWords`),
  `ContainModule.Data`, `OrderMark`, `RtsTemplate`, `Shot`, `ActiveBody.Data`, `ExperienceModule.Data` (and
  `LevelHealthBonus`) — and every new field left out means what the old record did. A template that names no
  prerequisite, word or cap is buildable as before; a save from before granted words and computer sides loads.
  `Canvas.drawPicture(Picture, …)` is a default that refuses, so a game's own canvas compiles as it did.
  `ProjectileLauncher.launch(shooter, victim, damage, type)` and `DieModule.onDie()` are still called, through the
  forms that now say more. The static `Duke3D.launch` methods are shorthand for `Duke3D.of(game, visuals)...launch()`.

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
`UnitView.built`, the simulation's own progress, so every machine shows the same height.

### A factory's working words, and a thing drawn by several models

`ProductionUpdate.Data` may name `Words`: `busy` while anything is in the queue — the reference's
`ACTIVELY_CONSTRUCTING` — and `made` for `madeFrames` from a unit finished (`CONSTRUCTION_COMPLETE`), not started
again while it holds. `UnitVisual.layer(name)` draws another model with a thing — a factory's door, its crane, its
scaffold, the reference's second draw modules — a look of its own with the same means as the thing's: models by
words (`model(words, null)` hides it), pieces, clips by words, rising as built, size and facing. Each layer is chosen
by the thing's words on its own and painted in its owner's colour; picking and rings stay with the thing's own look.

### An order answered with the game's own model

`OrderMark.model(path, clip, frames)` answers a move, and an attack-move, with a model laid on the ground where the
order goes — the reference's `MoveHintName` — its clip played once from its first frame, gone after `frames` of the
game's frames. A new order from the same selection moves its mark rather than laying another. Those orders alone, as
there: with a model named, a rally point, a power's place and an order the game names get no mark, and a lone
building told to move gets none. `attackRing(false)` answers an attack on a thing with nothing, the pointer having
said it already, as the reference does.

### Text floated up from the world

`DukeGame.floatText(text, x, y, z, argb)` floats a short text up from a point — money earned there, a bounty — as the
reference's floating text does. It is an event (`TextFloated`, which the simulation's own code may post too), seen
only by a player whose view of the point is clear. The client draws it rising and fading as
`Visuals.floatingText(rise, hold, fade)` says, the reference's 1, 10 and 0.1 where the game says nothing.

### Harvesters told where to work

`HarvestUpdate.workAt(place)` sends a harvester to a pile to fetch from, or a depot of its side to deliver to — the
reference's preferred dock — and it goes back there after every delivery, however far, until told another or the
place is gone. A load is banked only beside the depot (see what to change); a trip cut short keeps its load, and the
harvester sets off again a second later.

### A hold shared by a side

`ContainModule`'s `SharedBy` names a network: every thing of a side whose hold names it holds one list of passengers
with one capacity — the reference's tunnel network. What gets in at any of them may get out at any of them; the
passengers live through the loss of any but the last, and die with that one. `RtsSimulation.sharedHold` reads the
list.

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
- `Scroll-N` to `Scroll-NW` show while the view is scrolled at the window's edge.
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
by default. The camera keeps its vertical angle and takes the region's shape. Picking, the drag box, placing,
edge scroll and the bars over units all work inside that part. Outside it the world takes no pointer and draws
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
