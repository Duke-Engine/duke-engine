# ClientPlatform: splitting client3d into neutral and desktop

`client3d` is neutral (see CLAUDE.md, "Module boundaries"): it must name no desktop API so it runs on Android too.
The desktop side goes to a new platform module `client3d-desktop` (jme3-desktop, jme3-lwjgl3, AWT, Swing, GLFW);
`client3d-android` comes later. The one door between them is `ClientPlatform`, found with `ServiceLoader`.

## Seams

Package `uz.dukeengine.client3d.platform`, public, neutral types only.

- `ClientPlatform` — one aggregate, `static ClientPlatform get()`: loads lazily and caches
  `ServiceLoader.load(ClientPlatform.class, ClientPlatform.class.getClassLoader())` as `Flavour` does, exactly one
  provider; none is an error naming `client3d-desktop`. Hands out `glyphs()`, `images()`, `settings()`, `prompt()`,
  `window(Supplier<JmeContext>)`, `held(Supplier<JmeContext>)`,
  `ScreenshotAppState screenshots(String folder, String pattern, Consumer<String> saved)` (null folder = jME's default).
  One aggregate: one lookup, one clear failure, and a platform supplies all of it or none.
  Desktop: `DesktopPlatform` plus its `META-INF/services` line.
- `GlyphRasterizer` (desktop `AwtGlyphs`): `Typeface load(byte[] fontFile)` (null if not a font),
  `Typeface system(String family)` (null family = the machine's own face).
  - `Typeface`: `Face at(float size, boolean bold, float scaleX, boolean fractionalMetrics)`.
  - `Face`: `ascent()`, `descent()`, `leading()`, `float width(String text)` (advances and kerning),
    `GlyphImage draw(int codePoint)`; `record GlyphImage(int left, int top, int width, int height, float advance, int[] argb)`,
    white on clear.
- `ImageDecoder` (desktop `ImageIoDecoder`): `Picture decode(InputStream in) throws IOException`; null if not a
  picture; never closes `in`.
- `SettingsStore` (desktop `FileSettingsStore`, `~/.duke-engine/settings.properties`, reads the old registry
  (`java.util.prefs`) once when there is no file): `byte[] read()` (null for none), `void write(byte[]) throws IOException`.
- `TextPrompt` (desktop `SwingTextPrompt`): `String ask(String prompt, String initial)`, blocks, null on cancel, never
  on the render thread.
- `Window` (desktop `GlfwWindow`): `boolean open()`, `boolean fullscreen()`, `boolean fillTheScreen(boolean)`,
  `boolean resize(int w, int h, boolean fullscreen)`, `List<int[]> modes()`, `int[] desktopSize()`.
- `HeldInput` (desktop `GlfwHeldInput`): `boolean pointerInside()`, `boolean focused()`,
  `boolean buttonHeld(int button)` (`MouseInput.BUTTON_*`, same numbers as GLFW), `boolean keyHeld(char key)`.
  With no window the desktop ones answer as today: button held true, focused false, pointer inside true,
  fullscreen from `AppSettings`.
- The cursor needs no seam: `Cursors` uses only `JmeCursor` and `inputManager.setMouseCursor` (jme3-core).

## File by file

| File | Platform part | Outcome |
|---|---|---|
| BitmapFontBaker | AWT baking, `ImageIO.write`, `main` writing files | splits: packing, fnt text, kerning stay on `Face` with an `int[]` atlas; `main` → desktop `BakeFont` |
| CanvasText | `Page.image` `BufferedImage`, `Face` drawing and metrics, hints | splits: pages `int[]`, layout stays; fonts → `AwtGlyphs` |
| CanvasDrawing | `DataBufferInt` | stays, reads `Page.argb` |
| Lettering | AWT `Font`, `ImageIO.read`, `AWTLoader` (jme3-desktop) | stays on `Typeface` + neutral baker; drop the unused `Lettering(AssetManager, Font)` |
| MovieFrames | `ImageIO.read` of zip entries | stays, through `ImageDecoder` (not `loadAssetFromStream`: AWTLoader closes the stream) |
| DisplayModes | AWT desktop size | stays, `Window.desktopSize()` |
| WindowDisplay | GLFW/`LwjglWindow`, AWT modes | splits: `Size`, size filter, `indexOfSize`, preference logic stay; GLFW → `GlfwWindow`/`GlfwHeldInput` |
| CameraRig | GLFW button polling, cursor mode, cursor position | stays: `HeldInput.buttonHeld`; drop the redundant `glfwSetInputMode`; motion from a `RawInputListener` summing `getDX/getDY` (dy negated) |
| DukeRtsApp | `PREFS`, `Path` screenshot folder, GLFW left button, `LwjglWindow` framebuffer cast | stays: prefs → desktop store, folder a `String`, `HeldInput`, `getContext().getFramebufferWidth/Height()` (jme3-core 3.7) |
| Screens | `JOptionPane` in `askText` | stays, `TextPrompt.ask` |
| Screenshots | `Path`/`Files`, ImageIO re-encode, `File` | moves whole to desktop; the String-only `checked` stays neutral |
| Duke3D | `GameSettings`, registry migration, AWT desktop size, `screenshots(Path, …)` | stays; `screenshots(String folder, String pattern)` (public API break, duke-generals `FrontEnd.java:153`) |
| GameSettings | `Path`/`Files`, `inheritFrom(Preferences)` | stays on `SettingsStore`; `GameSettings(Path)` and the migration → `FileSettingsStore` |

## Start

`Duke3D.launch()` today reads settings, builds `DukeRtsApp`, builds `AppSettings`, `client.start()`, `awaitStop()`,
then stops the game and joins the sim thread. Split:
- public `LegacyApplication Duke3D.application()`: everything up to and including `setSettings`, not `start`;
- `launch()` = `application().start()` + the wait: the desktop entry point, still neutral;
- "stop the game, join the sim thread" moves into `DukeRtsApp.destroy()`, so it runs under any host.
- Android: the activity extends `AndroidHarness` and sets `app = duke.application()` before `super.onCreate`
  (3.7 only builds `appClass` when `app == null`, then `setSettings(its own)` and `start()`: samples and vsync go
  through the harness's fields). `JmeSurfaceView.setLegacyApplication` is the alternative.

## Tests and dependencies

- client3d tests never start a context but need jme3-desktop at runtime (`DesktopAssetManager`, `AWTLoader` for PNG):
  `testImplementation(project(":client3d-desktop"))`; GameSettings tests use `new GameSettings(new FileSettingsStore(path))`.
- rts-client-3d, rpg-client-3d, client3d-action tests: `testRuntimeOnly(project(":client3d-desktop"))`.
- Desktop launchers: `runtimeOnly(project(":client3d-desktop"))` (skirmish; games through the BOM).
- `NeutralModulesTest` scans `src/main` only. Its regex misses `com.jme3.texture.plugins.AWTLoader` and
  `com.jme3.system.JmeDesktopSystem`: widen it when jme3-desktop leaves client3d (move 9).

## Risks

- Text pixels: `CanvasText` measures with `LineMetrics`, fractional metrics off; the baker with `FontMetrics`,
  fractional on. `AwtGlyphs` must give both exactly what they measure today (CanvasTest, Lettering tests).
- Mouse capture: jME scales deltas by the content scale; check turning speed on a 150% display and the sign of dy.
- With no platform, `ClientPlatform.get()` fails at the first `new GameSettings()` (a field initializer in
  `DukeRtsApp`): the message must name the module.
- Android: R8 must keep the services entry and the provider (not verified). `TextPrompt` posts to the UI thread.

## Order of moves (each commit builds and shrinks the test's lists)

1. Empty `client3d-desktop` (settings, publish map, BOM), `ClientPlatform`, `DesktopPlatform`, services file; wire
   client3d tests, other client tests, skirmish. client3d keeps its desktop deps for now.
2. `Window` and `HeldInput`: WindowDisplay's GLFW half moves; DisplayModes, CameraRig, DukeRtsApp use the seams;
   mouse capture and `getFramebufferWidth`.
3. `SettingsStore`: GameSettings, Duke3D, Shell; `PREFS` → desktop store.
4. `TextPrompt`: Screens.
5. Screenshots: `Duke3D.screenshots(String, String)`, the class moves.
6. `ImageDecoder`: MovieFrames.
7. `GlyphRasterizer`: CanvasText, CanvasDrawing.
8. BitmapFontBaker and Lettering on `Face`; `BakeFont` tool; `AWTLoader` gone.
9. Drop jme3-desktop and jme3-lwjgl3 from client3d; empty the client3d entries in both lists; widen the regex.
