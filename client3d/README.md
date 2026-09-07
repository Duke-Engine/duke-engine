# `client3d` — 3D klient (jMonkeyEngine)

**Rol: PREZENTATSIYA.** Simulyatsiyani 3D da chizadi va kirishni buyruqqa
aylantiradi. Mantiqqa hech qachon tegmaydi — faqat `WorldSnapshot` ni o'qiydi
va `DukeGame.postCommand()` orqali buyruq qaytaradi.

| | |
|---|---|
| Bog'liqligi | `api project(":game")` + **jMonkeyEngine 3.7.0-stable** (jme3-core / desktop / lwjgl3 / plugins / jogg) |
| Kim bunga bog'lanadi | `studio`, `sandbox3d` |
| Hajmi | **4 fayl, ~1 350 qator** |
| Testlar | **0 ta** |

Bu — loyihadagi yagona og'ir tashqi bog'liqlik. `core`, `rts`, `game` sof Java;
jME faqat shu yerdan boshlanadi.

---

## Nima bor

| Fayl | Qator | Vazifa |
|---|---|---|
| `DukeRtsApp` | **1 024** | butun klient: snapshot→sahna sinxronizatsiyasi, glTF/Ogre model yuklash, `AnimComposer` va eski `AnimControl` animatsiyasi, pozitsion ovoz, RTS kamera, ray-pick tanlash, sog'liq chiziqlari, HUD, build menyusi, minimap, formatsiya harakati, ekran holat mashinasi (MENU → PLAYING ⇄ PAUSED + SETTINGS) |
| `MenuOverlay` | 140 | GUI kutubxonasiz menyu: xiralashgan quad + `BitmapText` tugmalar, hover/klik hit-testing |
| `Visuals` | 122 | Unity-uslub asset bog'lash: `.unit("Tank", u -> u.model("Models/tank.glb").walk("Drive").fireSound("..."))`. Modeli yo'q birliklar primitiv bilan chiziladi |
| `Duke3D` | 64 | `launch(game, visuals)` — oyna ochadi, yopilguncha bloklaydi |

### Boshqaruv

LMB tanlash (Shift — qo'shish) · RMB buyruq (dushmanga = hujum, yerga = yurish,
zavod tanlangan bo'lsa = rally) · WASD/o'qlar kamera · g'ildirak zoom ·
`H` to'xtatish · `P` pauza · `Esc` tanlovni bekor / pauza · `1`–`9` build
menyusidan navbatga qo'yish · minimapga LMB = kamera sakrashi.

### Sozlamalar

Fullscreen, Resolution (1280×720 / 1600×900 / 1920×1080), Volume — `Preferences`
da saqlanadi (`duke-engine/game` tugunida: `resIndex`, `fullscreen`, `volume`).

---

## O'chirish tahlili

**Bu modul o'chirilsa nima bo'ladi:** `studio` ning Play 3D tugmasi va butun
export quvuri (chiqarilgan o'yin `client3d` ni ishlatadi) yiqiladi, `sandbox3d`
ham ketadi. Ya'ni `studio` ni saqlab, `client3d` ni o'chirib bo'lmaydi.

**Modul ichida o'chiriladigan narsa yo'q** — 4 ta fayl ham ishlatiladi.

**Lekin e'tibor bering:**

1. **`DukeRtsApp` = 1 024 qator, bitta fayl, 0 test.** Loyihadagi eng zich va eng
   tekshirilmagan bo'lak. Agar "engine tugatish" maqsad bo'lsa, bu yerni
   bo'lish (sahna sinxronizatsiyasi / kirish / HUD / menyu) va hech bo'lmasa
   snapshot→sahna qismini testlash eng katta foyda beradi.
2. **Prezentatsiya ikki marta yozilgan:** bu modul va `game/swing` bir xil ishni
   ikki xil qiladi. Birini tanlash kerak — 3D to'liqroq (menyular, minimap,
   build menyusi, model/ovoz), 2D esa jME'siz va tez.
3. **jME versiyasi qulflangan** (3.7.0-stable) — `sandbox3d` ning
   `jme3-testdata` bog'liqligi tarmoq sekin bo'lsa `./gradlew build` ni aynan
   shu yerda yiqitadi (kod muammosi emas).

## Koordinatalar

Sim (x, y) → jME (x, 0, z). Yo'nalish: `fromAngles(0, -θ, 0)`, primitivlarning
oldi = +X. Minimapda ekran y dunyo y ga **teskari**.
