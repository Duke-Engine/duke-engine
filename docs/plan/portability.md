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
