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
