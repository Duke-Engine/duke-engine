# `game` — Unity-uslub API qatlami

**Rol: API + 2D klient.** Engine yadrosi emas — engine ustidagi "batteries
included" qulaylik qatlami. Maqsadi: bir necha qatorda o'ynasa bo'ladigan RTS.

```java
var game = DukeGame.create("My RTS").loadUnits(DukeGame.STARTER_UNITS).map(70, 45);
var you = game.addPlayer("USA", Color.CYAN);
var foe = game.addPlayer("China", Color.RED);
game.enemies(you, foe).money(you, 1500);
game.spawn("Barracks", you, 100, 360);
game.start();   // oyna ochiladi, yopilguncha bloklaydi
```

| | |
|---|---|
| Bog'liqligi | `api project(":rts")`. Tashqi kutubxona **yo'q** (Swing = JDK) |
| Kim bunga bog'lanadi | `client3d` (→ `studio`), `sandbox` |
| Hajmi | 12 fayl, ~2 010 qator |
| Testlar | 13 ta |

---

## Nima bor

### Fasad va mantiq

| Fayl | Qator | Vazifa |
|---|---|---|
| `DukeGame` | 762 | **Butun ommaviy API.** Sozlash (`loadUnits`, `map`, `addPlayer`, `enemies`, `money`, `spawn`), callback'lar (`onStart`, `onTick`, `everySeconds`, `onPlayerDefeated`), skirmish (`skirmish`, `selectSkirmish`, `getMapChoices`), multiplayer (`hostMultiplayer`, `joinMultiplayer`), ishga tushirish (`start`, `startEngineOnly`, `runHeadless`), runtime (`getSnapshot`, `getBuildOptions`, `postCommand`, `togglePause`, `setBanner`) |
| `RtsLogic` | 206 | tayyor RTS mantiqi: buyruq routingi (`MoveTo`→`MoveUpdate`, `AttackObject`→`WeaponUpdate`, `QueueProduction`→`ProductionUpdate`, …), **egalik tekshiruvi**, **build menyusi = shartnoma** qoidasi, annihilation bo'yicha mag'lubiyat |
| `RtsGameEngine` | 42 | konkret engine build'i (SAGE'ning `CreateGameEngine()` roli) |
| `GamePlayer` | 47 | o'yin kodi ko'radigan o'yinchi handle'i + ko'rsatish rangi |

### Thread seami

| Fayl | Qator | Vazifa |
|---|---|---|
| `RtsClient` | 85 | har klient kadrida ko'rinadigan holatni immutable `WorldSnapshot` ga nusxalaydi — **simulyatsiya holati thread chegarasidan faqat shu yerda o'tadi** |
| `view/WorldSnapshot` | 32 | volatile immutable kadr; tuman snapshot qurilishida qo'llanadi |
| `view/UnitView` | 35 | bitta birlikning chizish uchun kerakli maydonlari |

### Multiplayer

| Fayl | Qator | Vazifa |
|---|---|---|
| `MultiplayerSession` | 163 | 2 o'yinchili TCP lock-step: xom soketda `DUKE-JOIN`/`DUKE-WELCOME` qo'l berishuvi, `FRAME_DELAY = 3`, `beforeStep()` darvozasi (prime → pump → submit → ready bo'lmasa **to'xtaydi**) |

**Jonli tekshirilgan:** bir mashinada ikkita oyna, Host → Join 127.0.0.1, ikkala
tomon sinxron o'ynadi. `MultiplayerSyncTest` haqiqiy localhost TCP orqali 300 ta
o'zaro qadamda checksum'larni bit-aniqlikda solishtiradi.

### Custom kod

| Fayl | Qator | Vazifa |
|---|---|---|
| `script/UnitScript` | 162 | foydalanuvchi kodi uchun barqaror API (Unity `MonoBehaviour` naqshi): `onStart`/`onUpdate` @30 Hz + `findNearestEnemy`, `moveTo`, `attack`, `trainUnit`, `money`… |
| `script/ScriptModule` | 71 | skriptni engine moduli sifatida yurgizadi; **xatoni izolyatsiya qiladi** — exception tashlagan skript o'chiriladi, sim davom etadi |

### 2D klient

| Fayl | Qator | Vazifa |
|---|---|---|
| `swing/GamePanel` | 374 | ichki 2D ko'rinish + kirish: LMB tanlash/ramka, RMB yurish/hujum, WASD pan, g'ildirak zoom, HUD |
| `swing/GameWindow` | 30 | `GamePanel` ni ushlab turuvchi oyna |

---

## O'chirish tahlili

Bu modulda **o'lik kod yo'q** — hamma fayl ishlatiladi.

Savol boshqacha: **`game` moduli umuman kerakmi?** Ikki qarash bor.

**Kerak, chunki:** `client3d` va `studio` butunlay shunga tayanadi (`DukeGame`
13 joyda ishlatiladi). `RtsLogic` dagi buyruq routingi va egalik tekshiruvi
haqiqiy mantiq, uni yo'qotsangiz har bir o'yin qaytadan yozadi.

**Qisqartirish mumkin bo'lgan joy:**

| Nomzod | Sabab | Narxi |
|---|---|---|
| `swing/GamePanel` + `swing/GameWindow` (~404 qator) | ikkinchi, kambag'alroq klient. `client3d` to'liq (menyular, minimap, build menyusi, model/ovoz) — 2D panelda banner ham, build menyusi ham yo'q | `sandbox` va Studio'ning "Play 2D" tugmasi yo'qoladi; jME'siz tez sinov usuli qoladi |
| `MultiplayerSession` | agar multiplayer maqsad bo'lmasa. Hozir 2 o'yinchiga qattiq kodlangan | LAN o'yin yo'qoladi; `core` dagi lock-step primitivlari qoladi |

> `DukeGame` ning 762 qatori — modulning eng katta bo'lagi va u ham fasad, ham
> setup skripti, ham skirmish yig'uvchisi. Bo'lish mantiqiy bo'lardi, lekin bu
> refaktoring, o'chirish emas.

## Bilib qo'yish kerak

- **`setUp()` tartibi qat'iy:** `engine.init()` subsystemlarni **reset qiladi**,
  shuning uchun custom modullar → INI → relyef → o'yinchilar → scenario →
  skirmish assembler aynan shu tartibda bo'lishi shart.
- **Skript paketi:** generatsiya qilingan `Main` da lokal o'zgaruvchi `dukeGame`
  deb ataladi, chunki `game` nomi `game.scripts` paketi bilan to'qnashadi.
- **MP qo'l berishuvi** `SocketTransport.wrap()` dan **oldin** bo'lishi shart.
- **MP test tuzog'i:** ikkita simni o'zaro qadamlatganda ular bir kadrga siljiydi
  — checksum'ni har **yarim** qadamdan keyin solishtiring.
