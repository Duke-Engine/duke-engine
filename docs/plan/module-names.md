# Module names: a proposal

Nothing is renamed yet. This page proposes names for the modules of the 3D side and lists what a rename touches. The
user picks the names; the rename is its own change, done in one commit across the engine, the plugin and the games. Kept in `docs/plan/` with `render.md`, which describes what the modules draw.

## Why

- The `render-*` names are jMonkeyEngine's (`jme3-plugins`, `jme3-jogg`, `jme3-lwjgl3`) with the prefix swapped. They
  say where the code came from, not what it does: `render-plugins` holds the model loaders, `render-jogg` an Ogg audio
  decoder, `render-effects` the whole physically based picture (`PictureSettings`, `Quality`, every filter).
- The game clients are named two ways: `client3d-action`, `client3d-touch`, but `rts-client-3d`, `rpg-client-3d`.

## Proposal

| Now | From jME | Proposed | What it is |
|---|---|---|---|
| `render-core` | `jme3-core` | `render-core` (kept) | scene graph, renderer, materials, shaders |
| `render-effects` | `jme3-effects` | `render-picture` | post filters and the picture they make (`PictureSettings`, `Quality`) |
| `render-plugins` | `jme3-plugins` | `render-loaders` | glTF and Ogre model loaders, J3M exporters (OBJ is in `render-core`) |
| `render-plugins-json` | `jme3-plugins-json` | `render-loaders-json` | the JSON interface the glTF loader reads through |
| `render-plugins-json-gson` | `jme3-plugins-json-gson` | `render-loaders-gson` | that interface on Gson |
| `render-jogg` | `jme3-jogg` | `render-ogg` | Ogg Vorbis decoding |
| `render-desktop` | `jme3-desktop` | `render-desktop` (kept) | AWT input, cursors, desktop settings |
| `render-lwjgl3` | `jme3-lwjgl3` | `render-glfw` | the window, GL context and OpenAL through LWJGL 3 |
| `render-android` | `jme3-android` | `render-android` (kept) | the Android backend |
| `render-bench` | — | `render-bench` (kept) | the measuring tool |
| `rts-client-3d` | — | `client3d-rts` | the RTS's 3D client parts |
| `rpg-client-3d` | — | `client3d-rpg` | the RPG's 3D client parts |

Other choices worth a look: `render-post` for `render-effects` (says "post-processing", but materials and probes live
there too); `render-lwjgl` for `render-lwjgl3` (the smallest change: the version drops out of the name).

## Kept on purpose

- **Java packages.** The fork's `com.jme3.*` stays: renaming 600+ files buys nothing a reader sees, and it would make
  every upstream jME fix a hand merge. New code is already `uz.dukeengine.render.*`. The BSD licence asks for the
  notice, not the package names.
- **Gradle group** `uz.duke-engine` stays; only the artifact names change.

## What a rename touches

- Engine: `settings.gradle.kts`, `bom`, the root `build.gradle.kts`, every module's `build.gradle.kts` that names
  another, `.github/workflows/ci.yml`, `README.md`, `CLAUDE.md`, `CHANGELOG.md`, `docs/plan/*.md`, and tests that
  name modules (`NeutralModulesTest`, `action`'s `BoundaryTest`).
- duke-plugin: `GameTemplate` (the new-game template writes the dependency names), its test, `Repos.kt`, README.
- Games that build against the engine by name: duke-dungeon, duke-generals (and its Android module), duke-romchi
  (which still takes jME's own `jme3-desktop`, `jme3-effects`, `jme3-lwjgl3` from Maven; its move is outside this plan).
- Worktrees on other branches (`duke-engine-sim`, `third-person-action`) see the rename as a conflict in every file
  above; rename after they merge, not before.

## Order when it is done

1. Merge the open branches (`render-module`, `sim-gaps`, `third-person-action`).
2. Rename in the engine with `git mv`, one commit; `./gradlew build` and `-Pandroid` build.
3. The plugin's template and the games, each its own commit, each built.
