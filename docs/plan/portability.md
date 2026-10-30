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
  (`byte[] read(String name)`), with two implementations: `MapSource.in(Path folder)` (same bytes, same errors) and
  `MapSource.onClasspath(ClassLoader, folder)`, which reads a folder of the classpath by `getResourceAsStream`.
- Step 4.2, maps from inside the APK: no jME `AssetManager` is needed, because Java resources of the jars are packed into
  an APK and a class loader reads them the same on both platforms. A platform with no folder to scan finds maps by a list:
  `maps/maps.list`, one line a file (`clearing/clearing.map`, `clearing/preview.png`). `MapPackages.shipped(root)` reads
  that list and builds each `MapPackage` over `onClasspath`, so no file system is touched. The root `build.gradle.kts`
  writes the list for every module that has `src/{main,test}/resources/maps` (`generateMainMapList`,
  `generateTestMapList`), so a new map needs no edit. Desktop uses it too; there is one code path for shipped maps.
- Kept: the directory scan, as the fallback for a game outside this repo whose build writes no list, and for the player's
  own folder (`MapPackages.under`, `all`) which is desktop-only by nature. The list is read from the first resource root
  that has one (not merged across roots).
- Still desktop-only, on purpose: the `Path` accessors `MapPackage.file()`, `extras()`, `preview()` (relative names for a
  listed map), and the scan with its jar file system.
- Not verified: reading on a device or emulator; the class-loader path is covered by a test with an in-memory loader.

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
## Generated shapes (Binder without reflection)

- `core/data`: `Shape` says what the `Binder` reads of a type (a record's names, generic types as `TypeRef`, erased types, runtime annotations, build, read, `DEFAULTS`; a sealed type's permitted types; an `of/valueOf(String)` factory; an enum's constants). `Shapes.of(type)` takes the generated shape first and falls back to reflection; `-Dduke.shapes=reflection` uses reflection only.
- `processor`: an annotation processor, applied to every module from the root build, writes one `DukeShapes` class a package (`DukeShapesTest` for test sources) and lists it under `META-INF/services/uz.dukeengine.core.data.ShapeSource`. No annotation is needed. A type it cannot name from its package (private, or holding a private type, `DEFAULTS` or permitted type, or an annotation inside an annotation) is left to reflection. Counted on this branch: core 105 of 116 records, combat 25/25, rts 40/43, rpg 55/56; the rest are private records.
- `MapTerrain` reads `@Grid`/`@Relief`/marked components through the same shapes; generated annotations are anonymous implementations of the annotation interface.
- Checked: `ShapesTest` compares every generated shape in `core` (main and test) with reflection, and `reflectionTest` runs the `core`, `combat`, `rts` and `rpg` tests a second time on reflection alone. Both pass.
- A game outside this repository (duke-dungeon) adds `annotationProcessor("uz.duke-engine:processor")` to get shapes for its own records; the processor is published and in the BOM.
- Still needed for the browser: (1) `Shapes` falls back to reflection for every type without a generated shape, which includes JDK types the `Binder` asks about (`String`, `Integer`, `List`, `Map`, primitives); under TeaVM that fallback calls `Class.isRecord` and will not compile, so it needs built-in empty shapes for those JDK types and no reflective fallback on that platform. (2) The 16 private records (and any private map record of a game) need to be made package-private. (3) The four JDK methods from the TeaVM section. Not verified under TeaVM itself.

## ServiceLoader (`Flavour`, `HudPart`, `ShapeSource`)

- Untouched by Phase 2 apart from `ShapeSource`, which uses the same mechanism.
- GraalVM Native Image: providers listed in `META-INF/services` on the build class path are registered automatically; nothing extra is needed. Not verified (no `native-image` here).
- TeaVM: its classlib implements `ServiceLoader` from `META-INF/services` read at compile time, so providers must be on the TeaVM class path when it compiles. Not verified in the probe (the probe reached no `ServiceLoader`). `client3d/HudPart` is moot until there is a browser client.

## Android

Measured on a scratch app (AGP 9.2.1, Gradle 9.4.1, compileSdk 36, targetSdk 35, jME 3.7.0-stable with `jme3-android` and `jme3-android-native`) on a Vivo V2247 phone, Android 15 (API 35), arm64-v8a. Nothing in the repo was changed except this section.

- **jME runs:** `AndroidHarness` with a `SimpleApplication` opened and drew a lit spinning box (OpenAL renderer started).
- **Java 25 bytecode is accepted as is:** `assembleDebug` (D8) and `assembleRelease` (R8 minify) both pass with the `core`, `combat`, `rts`, `game` 0.8.0 jars (class version 69), no warnings, no recompile at a lower release. Two catches in the app module: the Gradle JVM must be JDK 25 (with JDK 21 it fails "invalid source release: 25"); the DSL `compileOptions` cannot be 25 (AGP 9's built-in Kotlin: "Unknown Kotlin JVM target: 25"), so it stays 21 and the `JavaCompile` tasks get source/target 25 in `afterEvaluate`, else javac rejects the jars ("wrong version 69.0, should be 65.0").
- **minSdk 35 works with no source changes.** On the phone `List.getFirst/getLast` and `Math.clamp` ran correctly.
- **Lower than 35 needs engine changes.** `Math.clamp` (API 35) is backported by D8 at minSdk 26. `List/ArrayList.getFirst/getLast/removeFirst/removeLast`, `LinkedHashSet/SortedSet.getFirst` (API 35) are not: D8, R8 and `desugar_jdk_libs` 2.1.5 all leave them, lint NewApi flags them at minSdk 26, and they would throw `NoSuchMethodError` before 35. Also flagged: `ServiceLoader.stream`, `CharSequence.isEmpty`, `InputStream.readAllBytes`, `URLEncoder.encode(String, Charset)`/`URLDecoder.decode`, `Thread.onSpinWait`, `String.formatted`, `Path.of` (API 33 to 34), `Class.isRecord`/`getRecordComponents`/`RecordComponent` in `Shapes`/`Binder` (API 34), `Files.readString` in `DukeGame.loadUnitsFile` (API 36 jar only).
- **Headless game fails on `java.awt.Color`, as expected:** `DukeGame.create(..).loadUnits(..).map(80, 80)` works; `addPlayer(name, Color)` throws `NoClassDefFoundError: java/awt/Color`. Without a player `runHeadless` refuses to start, so no logic frame ran yet. An Android caller cannot even compile against `addPlayer` without a stub `java.awt.Color`. By `jdeps`, `java.awt`/`javax.swing` are only in `game` (`DukeGame`, `GamePlayer`, `Roster`, `swing/*`); `java.nio.file` is in `core/map` and `DukeGame`.
- Not verified: any device below API 35 (older APIs are from lint and the dex only); the release APK was built but not run.
- **In the repo (step 4.1, `client3d-android`):** AGP 9.2.1 refuses Gradle 9.3.1 ("Minimum supported Gradle version is
  9.4.1"), so the wrapper is 9.4.1. The Gradle JVM need not be 25: with `java { toolchain { 25 } }` in the Android
  module, javac and the unit tests get a JDK 25 while Gradle runs on 21 (setting `javaLauncher` on the test task
  instead fails: AGP has made it final). Sources compile against `android.jar`, whose `ByteBuffer.flip()` still
  answers a `Buffer`.

## Summary by platform

| Target | State | What stands in the way | Next step |
|---|---|---|---|
| Desktop JVM | works | nothing | jpackage installer; GraalVM Native Image only if startup or size matters |
| Android | jME runs on a phone; engine jars at Java 25 dex fine, minSdk 35 | `java.awt.Color` in the `game` API; below API 35 the SequencedCollection calls and a few API 33/34 calls; `client3d` has 18 desktop-only files, no Android client exists | take AWT out of `game`, split `client3d`, write the Android client |
| Browser | `core` compiles and runs under Node (TeaVM 0.16.0, Wasm GC), for the code a probe reaches | TeaVM lacks `Class.isRecord`, record components, sealed reflection and four JDK methods; `Binder` reads generated shapes now, but `Shapes` still falls back to reflection for JDK types, and 16 private records stay reflective | built-in shapes for JDK types and no reflective fallback under TeaVM, then widen the probe to `Binder` with real data |
| GraalVM native | not tried (`native-image` not installed) | reflection metadata only for the private records left to reflection (tracing agent can produce it) | install GraalVM CE, run a probe with the agent |
| iOS | out of scope | no maintained Java AOT toolchain for Java 25 found | none |

Open: see "Generated shapes" for what the browser still needs from `Binder`.
