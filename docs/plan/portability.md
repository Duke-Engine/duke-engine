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
