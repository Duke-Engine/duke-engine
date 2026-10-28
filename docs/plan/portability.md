# Portability of the logic modules

`core`, `combat`, `rts` and `rpg` are meant to compile for more than a desktop JVM (Android first, a browser last).
This note records what stands in the way and what has been done about it.

## Where the boundary is today

- None of the four logic modules imports `java.awt`, `javax.swing` or `javax.imageio`. Swing lives in `game`
  (5 files) and `client3d` (16 files).
- Sockets (`java.net.Socket`, `ServerSocket`) and `java.nio.channels` are used only in `core/network`.
  `URI`, `URLEncoder` and `URLDecoder` are used in `core/map`, `core/network` and `combat/network`: plain text work.
- Files (`java.io.File`, `java.nio.file`) are used only in `core/map` (`MapPackage`, `MapPackages`).
- Reflection: `core/data/Binder` (record components, accessors, constructors, generic types), `core/map/MapTerrain`
  (records), and `ServiceLoader` in `core/Flavour` and `client3d/HudPart`.

`PortabilityTest` in `core` keeps the first three from growing.

## Network

- `Transport` (`send`, `subscribe`, `pump`, `onLinkLost`, `drop`) is already an interface; `LoopbackTransport`,
  `SocketTransport` and `HostTransport` implement it, and `LockstepGate` and the scheduler only see `Transport`.
- Below it, `LineChannel` (`send(String)`, `receive()`, `close()`) is the second seam: `SocketTransport.over(LineChannel,
  codec, peerIndex)` and `HostTransport.addGuest(playerIndex, LineChannel)` take any line channel, and its Javadoc
  already names a WebSocket as the intended second one. A WebSocket transport is a new `LineChannel`, no new `Transport`.
- `java.net.Socket` / `ServerSocket` appear in four files: `SocketTransport` (`connect`, `accept`, `wrap`),
  `HostTransport` (`addGuest(int, Socket)`), `LineChannel` (`over(Socket)`) and `LineChannels` (the `Sockets` class).
  `PacketCodec`, `NetFraming` and the rest use no socket (`NetFraming` uses `URLEncoder`/`URLDecoder`, plain text).
- The Socket-taking entry points are convenience overloads next to the `LineChannel` ones; nothing else in the package
  depends on them. `SocketTransport` is named for sockets but its field and logic are a `LineChannel`.
- No change was needed.

## Map source

- `MapPackage` read its files straight through `java.nio.file.Files`. It now reads through `MapSource`
  (`byte[] read(String name)`), whose only implementation is `MapSource.in(Path folder)`: same bytes, same errors.
- Still desktop-only, on purpose: `MapPackages` (finding maps by listing folders and opening a jar's file system) and the
  `Path` accessors `MapPackage.file()`, `extras()`, `preview()`, which games outside this repo call.
- To load maps on a platform with no file system, the next step is a way to list the map names and build a
  `MapPackage` from a `MapSource` instead of a folder; that is not done here.

## game module

Read-only audit, by grep and reading; nothing was compiled or run.

Files in `game/src/main` that import `java.awt`, `javax.swing` or `javax.imageio` (no `javax.imageio` import was found):

- `swing/GamePanel.java`: the built-in 2D renderer and input (AWT `Graphics2D`, key/mouse listeners, `javax.swing.Timer`). Swing only.
- `swing/GameWindow.java`: a `JFrame` hosting `GamePanel`; closing it quits. Swing only.
- `DukeGame.java`: the facade. Imports `java.awt.Color` (`:3`), `javax.swing.SwingUtilities` (`:20`), `swing.GameWindow` (`:39`).
- `GamePlayer.java` and `Roster.java`: import only `java.awt.Color`, for a player's colour (`GamePlayer.java:16,28`, `Roster.java:17,49-51`).

Is the rest of `game` free of them? No. Only `swing/GamePanel` and `swing/GameWindow` are purely Swing. The other three leak:

- `java.awt.Color` is in the public API: `DukeGame.addPlayer(String, Color)` (`DukeGame.java:218`), `DukeGame.getColor(int)` (`:1169`), `GamePlayer.getColor()`. Not Swing, but `java.desktop` (absent on Android and in a browser).
- Swing is used by `DukeGame.start()` only: `SwingUtilities.invokeLater` and `new GameWindow(...)` (`:914-927`).
- No other file in `game` (scripts under `script/`, `MatchEngine`, `LogicDriver`, `Netplay`, ...) imports AWT or Swing.
- Nothing in `game` outside `GameWindow` constructs `GamePanel` or `GameWindow`.

Can a `DukeGame` start and tick with no Swing class loaded? Yes, for the paths that do not call `start()`:

- `runHeadless(int frames)` (`DukeGame.java:937`) calls `setUp`, `netplay.talk()`, `logic.update()`, `link.client().update()`. No window, no Swing. The `game` tests use it (`AlliedViewTest`, `AttackableTest`, `BeamTest`, ...).
- `boot()` (`:816`) and `startEngineOnly()` (`:795`, a `duke-sim` thread running `engine.execute()`) build and run the world with no window. `client3d` uses these.
- `start()` is the only entry that needs Swing. It is not reachable from the others.
- Even headless, `java.awt.Color` must be loadable, so the JVM needs the `java.desktop` module. Not a Swing class, but not Android-safe.

Not verified: whether the JVM resolves `GameWindow`/`SwingUtilities` lazily (they sit in method bodies, so it likely does; I did not run it with a class-load trace). Whether `engine.execute()` or other `core`/`combat` code touches AWT (the earlier `core` audit covers `core`). No `main` method exists in `game`.

Smallest follow-up, not done: replace `Color` in the API with an int RGB or a small `PlayerColour` record, and move `start()` and the `swing` package into a separate module or a launcher.

## client3d

Read-only audit of all 233 `.java` files in `client3d/src/main` (flat package `uz.dukeengine.client3d`). Found by grep for `java.awt`, `javax.imageio`, `javax.swing`, `java.nio.file`, `org.lwjgl`, `AppSettings`, `LwjglWindow`. Nothing compiled or run. `build.gradle.kts` has `jme3-core` as `api`; `jme3-desktop`, `jme3-lwjgl3`, `jme3-plugins`, `jme3-plugins-json`, `jme3-jogg` as `implementation`.

(a) Platform-neutral: about 215 files, using `jme3-core` classes and plain Java only (scene, effects, HUD, menus, camera maths, fog, particles, sounds, `Canvas`/`CanvasFrame`/`CanvasInputs`, `Cursors` and the rest). `Cursors` uses `com.jme3.cursors.plugins.JmeCursor` (in core). Not checked file by file for jme3 classes that are desktop-only inside core.

(b) Desktop-only, 18 files (12 with real desktop APIs, 6 that only name `java.awt.Color`), with the reason:

- `BitmapFontBaker.java`: AWT `Font`/`BufferedImage`/`Graphics2D`, `ImageIO.write`, `java.nio.file.Files` (tool that writes a font to disk).
- `CanvasText.java`: AWT `Font`, `Graphics2D`, `BufferedImage` to rasterise glyph pages.
- `CanvasDrawing.java`: reads the AWT `BufferedImage` raster (`DataBufferInt`, `:326`) to upload glyph pages.
- `Lettering.java`: AWT `Font`/`FontFormatException`; `ImageIO.read` of the baked PNG (`:143`).
- `MovieFrames.java`: `ImageIO.read` of zipped frames, `BufferedImage` (`:12,63`).
- `DisplayModes.java`: `java.awt.GraphicsEnvironment` to list screen modes (`:37`).
- `WindowDisplay.java`: GLFW calls (`org.lwjgl.glfw.GLFW`), `LwjglWindow` cast, AWT `GraphicsEnvironment`/`DisplayMode`, `AppSettings`.
- `CameraRig.java`: GLFW cursor mode and cursor position (`:185,272-284`).
- `DukeRtsApp.java`: GLFW mouse button (`:836`), `LwjglWindow` cast (`:1639`), `java.awt.Color` (`:968`).
- `Duke3D.java`: `AppSettings` (window title, size, fullscreen, vsync, `:189`), `java.awt.Color` in `shadowColour` (`:563`). This is the desktop launcher.
- `Screens.java`: `javax.swing.JOptionPane` input dialog (`:386`), `AppSettings` supplier.
- `GameSettings.java`: settings file read and written with `java.nio.file` (`:45-149`). Plain Java, not AWT; works on desktop and not on a sandbox with no file path.
- `Materials.java:37`, `MinimapView.java:439`, `SceneLight.java:127`, `PanelSkin.java`, `Skin.java:22`, `Visuals.java` (many lines, `:118,1151,1497,2038,...`): `java.awt.Color` only, in signatures and fields. Easy to swap; these are the neutral code that AWT leaks into.

`Visuals.java` is the biggest case: it is neutral except for `java.awt.Color` in its builder API, which also comes from `game`.

Not verified: that these 18 are the only files that fail on Android (my count is 18 by grep, the request said 16; the difference is probably how `java.nio.file` and the Color-only files are counted). Whether `jme3-plugins`/`jme3-jogg` loaders work on Android (they should, but untested). Tests in `src/test` were not classified.

Split proposal (not implemented):

- `client3d-core`: the 215 neutral files plus `jme3-core`, with `java.awt.Color` replaced by an int or `ColorRGBA`.
- `client3d-desktop`: `Duke3D`, `WindowDisplay`, `DisplayModes`, `CameraRig`'s GLFW part, the `Screens` dialog, `GameSettings` file store, `jme3-desktop`, `jme3-lwjgl3`.
- Put the AWT text/image work (`CanvasText`, `Lettering`, `BitmapFontBaker`, `MovieFrames`) behind small interfaces (glyph rasteriser, image decoder, settings store) that the desktop module implements.
- An Android client would need what is not there: a launcher (`jme3-android` activity and `AppSettings` equivalent), a touch input layer (no touch/gesture code exists, the camera and selection assume a mouse and keys), a glyph and image source with no AWT (Android bitmap or pre-baked fonts), asset loading from the APK instead of folders, a settings store using app storage, a text-input dialog replacing `JOptionPane`, and a replacement for the GLFW cursor and window calls.

## Release 17 / 21

Measured on a scratch copy (main sources of `core`, `combat`, `rts`, `rpg`, 412 files), with the installed JDK 25: one `javac --release N -Xmaxerrs 10000` over all four modules (no Gradle). Nothing in the repo was changed.

- **`--release 17`: does not compile, none of the four modules is clean.** javac reports 21 errors, but that is a floor: it stops after the parse stage and reports each language feature once per file. They are all language features, no missing API was reported (see the last point for the API side).
- **`--release 21`: only 9 errors, all in `core`, all one cause: unnamed variables `_`** (preview in 21, final in Java 22). `combat`, `rts` and `rpg` compile clean at 21. With the `_` sites renamed in the scratch copy, all four modules compile with 0 errors at 21.
- **Pattern matching for switch** (`case Type name ->`, guards `when`, `case null, default`; final in Java 21): 12 files fail at 17: core 6, combat 1, rts 4, rpg 1. Counting the real sites by grep: 86 case labels (core 41, combat 4, rts 38, rpg 3). Examples: `core/.../data/Binder.java:301`, `rts/.../RtsLogic.java:27`, `rts/.../network/CommandCodec.java:50`, `combat/.../network/CombatOrderCodec.java:29`.
- **Unnamed variables `_`** (final in Java 22): 9 files fail, all in `core` (about 20 sites by grep; catch parameters and `case X _`). Fails at both 17 and 21. Examples: `core/.../math/Hundredths.java:19`, `core/.../Obstacles.java:164`, `core/.../replay/Replay.java:71`, `core/.../network/LineChannels.java:63`.
- **Hidden at 17 by the parse errors, measured from the Java 21 bytecode instead:** `Math.clamp` (Java 21): 36 call sites (core 29, rpg 4, rts 3); SequencedCollection `getFirst/getLast/removeFirst/removeLast` on `List`/`ArrayList` (Java 21): 38 call sites (core 17, rts 12, rpg 5, combat 4). Examples: `core/.../module/FlyUpdate.java:243`, `core/.../pathfind/HeightMap.java:94`, `rts/.../GroupMove.java:195`. These would be about 74 more errors once the syntax is fixed at 17.
- Fine at 17 (no errors): sealed types, records, text blocks, `instanceof` patterns, switch expressions, `Stream.toList`, `String.formatted`. No record patterns, no string templates and no `String.indexOf` overloads from 21 were found. `javac` also emits the Java 21 class `MatchException` for exhaustive pattern switches (compiler-generated, not in the sources).
- Not verified: the 17 API count rests on a read of the distinct JDK methods the classes call (458 members) plus bytecode counts, not on javac at release 17 (javac cannot get past the syntax errors); no D8/R8 or TeaVM run; the `_` count by grep may be off by a few; resources and `build.gradle.kts` untouched.

## TeaVM (Wasm GC)

Measured on a scratch Gradle project (JDK 25, Gradle 9.3.1, TeaVM and Gradle plugin `org.teavm` **0.16.0**, the newest on Maven Central; target `teavm { wasmGC { mainClass = ... } }`, task `generateWasmGC`). Nothing in the repo was changed except this section. teavm.org/docs was behind a bot check, so plugin usage was read from the plugin jar and the TeaVM sources jars instead.

- **Result: `core` builds to Wasm GC (929 KB `.wasm`) and the output runs under Node 22**, in 3 TeaVM attempts. Printed output (parsed `.duke` text, `Binder` into a record, a `GameEngine` and `GameLogic` init, a spawn, a path search, `checksum()`) is identical to the same code on the JVM, checksum included.
- **Sources copied:** all of `core/src/main/java`, minus `network/{SocketTransport, HostTransport, LineChannel, LineChannels}` (java.net) and `map/{MapPackage, MapPackages}` (java.nio.file). The rest of `network` and `map` compile unchanged; `replay` and `Flavour` need the other `network` classes.
- **Attempt 1 failed:** `Class.isRecord()` missing (called from `Binder`). TeaVM stops at the first reachable missing method per site, so Binder's other reflection calls were not yet reported.
- **Attempt 2 failed, 7 sites, 4 methods:** `Integer.parseUnsignedInt(String,int)` (Binder:434), `Long.parseUnsignedLong(String,int)` (Binder:437), `Collections.unmodifiableSortedSet` (4 sites: `Sight.revealedTo`, `Sight.markedSeen`, `Scenery.removed`, `GameObject.getConditions`), `Collections.unmodifiableSortedMap` (`GameObject.getRelationOverrides`). Nothing else was reported for the code reachable from `GameEngine`/`GameLogic`/`Binder`: no thread, file, ServiceLoader or language-feature failures (records, sealed, pattern switch, `_`, text blocks all compiled).
- **Attempt 3 built.** Fixes, scratch copy only: a `Compat` class replacing those 4 methods (the sorted views are not read-only there), and a `Rec` helper in place of `Class.isRecord/getRecordComponents/isSealed/getPermittedSubclasses` and `RecordComponent`.
- **Binder under TeaVM:** TeaVM's classlib has none of `Class.isRecord`, `getRecordComponents`, `RecordComponent`, `isSealed`, `getPermittedSubclasses`. It does have `ClassValue`, `Field.getGenericType`, `ParameterizedType`, `getDeclaredFields`, `getDeclaredMethod`, `getDeclaredConstructor`, `Constructor.newInstance`, `Method.invoke`. Members are only reachable if a `ReflectionPolicy` (service `org.teavm.extension.spi.reflection.ReflectionPolicy`, base class `SimpleReflectionPolicy`, jar `teavm-extension-spi`) makes them reflectable; the old `ReflectionSupplier` is deprecated. With `Rec` building components from the non-static declared fields in order, each accessor by name, and a policy exposing the records and `Kind` (for its static `of(String)`), `Binder` bound a record holding a `String`, `float`, `Set<Kind>`, a `List` of records and a positional record (`At = [x, y, z]`), generic types included.
- **Not verified:** `getPermittedSubclasses` for a real sealed type (`Rec.PERMITS` is a hand-filled map, left empty; core's `Geometry` and `@Link`/`@Grid` annotations were not exercised); `Binder.vocabulary`, enums and `Map` components; annotations reading under TeaVM; only the code reachable from the probe `main` was compiled, not all of `core` (the network, replay, map and pathfinding-by-sector code is mostly untouched); a browser (only Node 22 with a hand-written `require` loader, which kept the process alive after `main` returned); size or speed; `StrictMath` bit-identity against the JVM beyond the one checksum; the probe's `logic frame` stayed 0 on both (no `execute` loop was used).
- **What a real port needs:** a generated or hand-kept record-shape table in place of the missing `Class` methods (or a TeaVM-side `Rec`), a reflection policy covering every record and `@Link`/`@Grid` target, `MapSource` bytes from the page instead of `Path`, a `Transport` that is not `java.net`, and the four JDK-method replacements above.
## Summary by platform

| Target | State | What stands in the way | Next step |
|---|---|---|---|
| Desktop JVM | works | nothing | jpackage installer; GraalVM Native Image only if startup or size matters |
| Android | not tried | logic modules need Java 21 sources at least (9 `_` sites in `core` at 21; 17 means 86 pattern-switch sites and ~74 newer API calls); `client3d` has 18 desktop-only files, no Android client exists | check what the Android toolchain accepts at 21, then write a client |
| Browser | `core` compiles and runs under Node (TeaVM 0.16.0, Wasm GC), for the code a probe reaches | TeaVM lacks `Class.isRecord`, record components, sealed reflection and four JDK methods; `Binder` needs a replacement and a reflection policy for every record it reads | decide the `Binder` question (see the decisions in PLAN.md), then widen the probe |
| GraalVM native | not tried (`native-image` not installed) | reflection metadata for `Binder` (tracing agent can produce it) | install GraalVM CE, run a probe with the agent |
| iOS | out of scope | no maintained Java AOT toolchain for Java 25 found | none |

Open: the generated-shape work for `Binder` was stopped after its design step; it is mostly a browser concern.
