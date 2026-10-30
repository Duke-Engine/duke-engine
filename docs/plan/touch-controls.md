# Touch controls: is `ControlScheme` enough?

Step 3.1 of the Android plan. Verdict: **almost; one hook and a few calls were missing, now added in `client3d`.**
Touch lives in a neutral module `client3d-touch` (jME's `TouchInput` is in jme3-core; on desktop the mouse is emulated
as touch, so it is tested on desktop). It plugs in as a `ControlScheme`, as `client3d-action`'s `ActionControls` does.

## What already worked

- `attach(Context)`, `frame(tpf)`, `steer(panned, tpf)`: a scheme is called every frame and may move the camera
  (`lookAt`, `turnBy`, `tilt`, `lookingAt`, `yaw`).
- `left(pressed)` runs after the command bar, armed buttons and armed skills have had the press, so HUD taps stay the
  client's, and returning true stops `orders.beginDrag()`: a one-finger drag does not start a box by itself.
- `aiming()`/`aimPoint()` already feed `WorldPointer.pickGround()`, which the build ghost and every order read. A finger
  can aim the ghost with no change to the pointer code.
- Where the platform simulates the mouse from touch, the cursor, `pointedUnit()` and `pointedGround()` follow the finger.

## What was missing, and what was added

1. Several fingers: the client reads one mouse. `Context.input()` returns the `InputManager`; the scheme registers its
   own `RawInputListener` and gets every `TouchEvent` with its pointer id.
2. Ground under any finger: `Context.groundAt(x, y)` (as `pointedGround`, for any screen point). Panning that keeps the
   ground under the finger, and the pinch centre, need it.
3. Zoom: only wheel and keys existed. `Context.zoomBy(factor)` (the camera's own clamp).
4. Select and command were reachable only through mouse buttons. Added `beginBox()`, `endBox(add)`,
   `selectPointed(add)`, `orderPointed()`: the same `Orders` calls the buttons make.
5. Build placement put the place down on release (`buttons.releaseArmedButton()`), with no way to defer it. Added
   `ControlScheme.holdsPlacement()` (default false: nothing changes) and `Context.putDownArmed()`. `DukeRtsApp` skips
   the release when the scheme holds it.
6. A skill button of the scheme's had no way to press a skill. Added `Context.pressSkill(key)` (a click on its slot).

All new `Context` methods are `default` no-ops, so existing schemes and `TestWindow` compile untouched. No platform
check anywhere; `client3d` gets one more `InputManager` import (jme3-core).

## How 3.2 to 3.5 plug in

- **3.2 RTS touch.** A `RawInputListener` tracks fingers. `left(true)` returns true while it decides. Tap: `selectPointed`
  (or `orderPointed` on ground with a selection). One-finger drag: `lookAt(start - groundAt(now))` in `frame`.
  Long press: `beginBox()` then `endBox(false)` on lift. Pinch: `zoomBy(previous / now)`. Two-finger twist: `turnBy`.
- **3.3 Placement.** `holdsPlacement()` true while a build is armed; the finger sets `aimPoint()` with `aiming()` true;
  the scheme's confirm button (in `gui()`) calls `putDownArmed()`; a cancel button uses the existing disarm.
- **3.4 Joystick and skills.** A separate scheme in `client3d-touch`, independent of `client3d-action`. The stick makes a
  direction and tells the game as `ActionControls.walk` does (a callback, not keys); skill buttons call `pressSkill`
  and aim with `armed()`/`aimPoint()`/`castArmed()`.
- **3.5 HUD scale by DPI.** Not a `ControlScheme` matter: the platform's `Window`/`ClientPlatform` reports density and
  `client3d` scales `guiNode`. Not designed here.

## Open

- Edge scrolling reads `heldInput.pointerInside()`; on Android that must be false or a finger left near an edge scrolls.
- The desktop mouse-as-touch emulation must emit `TouchEvent`s and also keep the mouse events, or the cursor-based calls
  above see nothing.
- `holdsPlacement()` leaves `placing()` true until confirm; a second press re-anchors, which is wanted, not checked.

## 3.2 RTS touch: done (`client3d-touch`, `TouchRts`)

`TouchGestures` (pure: finger id, x, y, time in; tap/pan/box/pinch/twist out) and `TouchRts` (the `ControlScheme`; a
`RawInputListener` feeds the recognizer, `frame` ticks the long press). No new `Context` calls were needed.

| Gesture | Meaning | Calls |
|---|---|---|
| Tap on a unit, or with nothing selected | select | `selectPointed(false)` |
| Tap on ground with a selection | order there | `orderPointed()` |
| One finger dragging (past `SLOP` = 16 px) | pan, the ground kept under the finger | `groundAt`, `lookAt` |
| Finger held `LONG_PRESS` = 0.4 s, then dragged | box select, chosen on lift | `beginBox()`, `endBox(false)` |
| Two fingers, distance changes | zoom, spreading comes nearer | `zoomBy(1 / factor)` |
| Two fingers, line turns | turn the camera, the ground following the fingers | `turnBy(-radians)` |

Chosen over "double-tap orders": one tap that decides by what is under the finger needs no timing window. There is no
add-to-selection gesture yet. A press that never reached the world (a HUD button) does nothing: `left(true)` marks a
touch as the world's. Two fingers, a second finger during a box, and a third finger never tap or pan.

Not done / to check on a device: `SLOP` is in raw pixels (3.5 scales by density); the sign of `turnBy` follows the key
turn (`TURN_LEFT` is positive) and is untested on screen; select, order and box act at the cursor, so they need the
platform's mouse-from-touch simulation (jME Android has it; no mouse-to-touch emulation was added on desktop, where the
mouse gives no `TouchEvent`s, so the scheme can only be tried there with the synthetic events of the tests).
It is installed like any scheme: `Duke3D.controls(new TouchRts())`.

## 3.3 Placement by touch: done (`TouchRts`, two small `client3d` additions)

While a building is armed and its place pressed (`Context.placing()`, new, default false), `holdsPlacement()` is true, so
lifting puts nothing down. A finger drag sets `aimPoint()` (`aiming()` true) and the camera does not pan; on lift the
ghost stays and **Confirm** / **Cancel** (`BitmapText` in `gui()`, green / red, under the ghost) come up. A tap on Confirm
calls `putDownArmed()`; on Cancel the new `Context.cancelArmed()` (default no-op; `DukeRtsApp` -> `buttons.disarmButton()`,
what Esc does). A tap elsewhere moves the ghost there. `CommandButtons.followTheAim` re-presses the place each frame while
a scheme aims it, so the drag moves the ghost instead of turning it (the mouse's drag-to-turn is untouched).

Not done: turning the building by touch (the facing stays the button's), button backgrounds (text only, hit area 160 x 64
px), a hold-then-drag does not move the ghost until the lift (box mode reports no moves), a tap on the command bar while
placing also moves the ghost (the bar then disarms it anyway), a refused (red) confirm drops the press and the ghost
follows the cursor again until the next touch. Not tried on a device.

## 3.4 Hero by thumbs: done (`client3d-touch`, `TouchHero` + pure `VirtualStick`, one small `client3d` addition)

A separate `ControlScheme` beside `TouchRts`, the touch twin of `client3d-action`'s `ActionControls` and with no
dependency on it. Fingers are followed by pointer id, so the stick, a button and the camera work at once.

| Finger | Meaning | Calls |
|---|---|---|
| Left half, anywhere | floating stick: touch = centre, drag = direction (dead zone 20 %, radius 120 px), lift = stop | `walk` callback, 256ths of a turn, `-1` for none, as `ActionControls.walk` |
| Right half, drag | turn the camera (`TURN_PER_PIXEL`, right drags as the mouse does) | `turnBy` |
| Hit button (bottom right), held | strike along the camera's forward; told again as the camera turns, `held=false` on lift | `strike` callback `(game, forward radians, held)` |
| Skill buttons (row left of Hit, `skills('Q', ...)`) | press the skill; one that is aimed is aimed from the hero along the forward (all of a lane's reach, else two thirds) and cast when the finger lifts | `pressSkill(key)`, `armed`/`aimPoint`/`castArmed` |

The game's part (`ActionOrders.along` / `toward`) comes in through the two callbacks, because `action` is not a
dependency here: `duke.controls(new TouchHero().walk(...).strike(...).skills('Q', 'W', 'E'))`. The direction is the
camera-turned one the keys give, so the same game callback serves both. Drawn in `gui()` as `BitmapText` (a ring and knob
under the stick, a label per button, yellow while held). New `Context.screenSize()` (default null; `DukeRtsApp` ->
camera width and height) to tell the halves and place the buttons.

Not done / to check on a device: a touch on the HUD (skill panel, command bar) on the left half also starts the stick
(`left(true)` is not used to gate it, since a button of the scheme's own must work regardless); button hit areas are fixed
96 px (3.5 scales them by density); a skill held to be cast on release (`castOnRelease` ranges, which never call `armed`)
is not cast on lift; the Hit label is text only; no strike hold-lock on an enemy (the game's callback decides what a
strike is); the stick centre is not clamped inside the screen. Not tried on a device, only with synthetic events.

## 3.5 HUD scale by density: done (`ClientPlatform.uiScale`, `HudScale` in `client3d`, touch constants)

One number, `ClientPlatform.uiScale()` (new, `default` 1f; desktop keeps 1, so its look and every test are unchanged;
Android answers `displayMetrics.density`; see client-platform.md). `DukeRtsApp` makes a `HudScale` of it and
`ControlScheme.Context.uiScale()` (new, `default` 1f) hands it to the schemes.

- **HUD.** Laid out in "HUD pixels" = window pixels / scale and drawn `scale` times larger: the roots of the HUD hang on
  `hudNode` (scaled), which is also `HudPart.Window.gui()`, so parts, the command bar, menus, banner, minimap, loading
  overlay and the status text need no change of their own; the canvas node scales itself (it is shown alone when the
  interface is hidden). Every width or height the HUD is laid out for, and the `open`/`resized` sizes a part is told, are
  `hudScale.of(...)`; the canvas's `width()/height()`, `screenOf`, `barOf` likewise.
- **Hits.** What tests a point against the HUD is given `hudScale.of(cursor)`: the command bar, the menu click, hover
  and drag, a part's `covers/raiseAt/keyAt`, the minimap click and cover, the cursor in `HudPart.Frame`, and the pointer
  the game's canvas hears (`CanvasInputs`). Picking the world, the drag box, unit bars, floating numbers and the scheme's
  own `gui()` (the touch buttons) stay in window pixels, on `guiNode` itself.
- **Touch.** `TouchGestures` slop 16, `VirtualStick` radius 120, `TouchHero` buttons 96 and gap 24, `TouchRts`
  Confirm/Cancel 160 x 64 and gap 24 are all times `context.uiScale()`.
- **Tests (desktop emulation, scale 2).** `HudScaleTest`: lengths, points and a root halved and doubled; the command
  bar laid out for a 1000 px window equals the 500 px one at 1, a touch at the doubled place hits the same button and
  the unhalved point does not; the canvas hears (100, 250) for a touch at (200, 100) of a 600 tall window. Touch tests:
  a 25 px drag is a pan at 1 and a tap at 2, Confirm hits at (800, 250) only at 2, the strike button and the stick's
  radius double.

Not done / to check on a device: at a scale other than 1 nothing was seen on a screen, only asserted; the hint text,
the status line and BitmapText sizes grow with the node but a font is not re-baked at the larger size (blurry, not
wrong); `TURN_PER_PIXEL` (a finger turning the camera) is still per raw pixel, so on a dense screen the same swipe in
centimetres turns further; the loading overlay is built once at the first size (as before); the movie, the scene veil,
the world tint and the first-person crosshair stay in window pixels; the shadows of floating numbers, unit bars and
the box are at window size by design, so their size does not follow the density.
