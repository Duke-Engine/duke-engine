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
