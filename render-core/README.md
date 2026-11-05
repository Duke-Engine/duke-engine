# `render-*` — the renderer

**Role: DRAWING.** A hard fork of jMonkeyEngine 3.7.0, owned here: it does not follow upstream, and its API may
change with `client3d` in one commit. The first commit (`feat(render): import jMonkeyEngine 3.7.0 sources unchanged`)
is the exact upstream snapshot, so `git diff` against it shows everything changed since. Licence: see
[NOTICE.md](../NOTICE.md).

| Module | Was | |
|---|---|---|
| `render-core` | `jme3-core` | scene graph, materials, shaders, lights, shadows, animation, assets |
| `render-effects` | `jme3-effects` | full-screen filters |
| `render-plugins`, `render-plugins-json`, `render-plugins-json-gson` | `jme3-plugins*` | model loaders |
| `render-jogg` | `jme3-jogg` | Ogg sound |
| `render-desktop`, `render-lwjgl3` | `jme3-desktop`, `jme3-lwjgl3` | desktop, OpenGL through LWJGL |
| `render-android` | `jme3-android` | Android, only with `-Pandroid` |

Packages stay `com.jme3.*` for now: the released `jme3-android-native` binds its JNI functions to those names.
Left out as unused: jme3-examples, -terrain, -jbullet, -niftygui, -networking, -lwjgl, -vr, -ios. The one Groovy test
(`render-core/src/test/groovy`) is not compiled: Groovy has no release for Java 25 in upstream's version.
