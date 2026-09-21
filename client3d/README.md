# `client3d` — 3D klient (jMonkeyEngine)

**Rol: PREZENTATSIYA.** Simulyatsiyani 3D da chizadi va kirishni buyruqqa
aylantiradi. Mantiqqa hech qachon tegmaydi — faqat `WorldSnapshot` ni o'qiydi
va `DukeGame.postCommand()` orqali buyruq qaytaradi.

| | |
|---|---|
| Bog'liqligi | `api project(":game")` + **jMonkeyEngine 3.7.0-stable** (jme3-core / desktop / lwjgl3 / plugins / jogg) |
| Kim bunga bog'lanadi | `skirmish` |
| Hajmi | 81 fayl, ~25 194 qator |
| Testlar | 554 ta, 52 klassda — jME'siz ishlaydigan qismlar (layout, o'lchov, holat) |

Bu — loyihadagi yagona og'ir tashqi bog'liqlik. `core`, `rts`, `game` sof Java;
jME faqat shu yerdan boshlanadi.

---

## Nima bor

81 fayl. Ro'yxat emas, guruhlar — chunki bittalab sanash 81 qatorlik jadval
bo'ladi va hech kimga hech narsa aytmaydi:

| Guruh | Kattaroqlari | Vazifa |
|---|---|---|
| **Kirish nuqtasi** | `Duke3D` · `DukeRtsApp` (**5 020**) | `launch(game, visuals[, shell])` oyna ochadi va yopilguncha bloklaydi; `DukeRtsApp` — snapshot→sahna sinxronizatsiyasi, kamera, ray-pick tanlash, ekran holat mashinasi (MENU → PLAYING ⇄ PAUSED + SETTINGS) |
| **Assetlarni bog'lash** | `Visuals` (1 313) · `AnimationLibrary` · `Preload` | shablonni modelga, klipga, ovozga bog'laydi. `draw(Drawn, AnimationSet)` — shablonning o'zi aytadi, klient fayl nomini bilmaydi |
| **Yer va nur** | `TerrainScene` (896) · `Tileset` · `TileLayout` · `Sun` · `Sunlight` · `Fog` · `FogMap` | kataklardan sahna, tumanlik va yorug'lik |
| **HUD** | `HeroPanel` (**4 232**) · `HeroPortrait` · `UnitBars` · `PanelSkin` · `NineSlice` · `SkillRing` · `StoneMenu` (936) | panellar, portretlar, sog'liq chiziqlari, minimap — hammasi ma'lumot faylidan o'lchanadi |
| **Effektlar** | `LayeredEffects` (951) · `EffectLayer` · `ParticleLayer` · `SkillEffects` · `ProjectileEffects` · `EffectBudget` | `core.content.Effect` ni chizilgan narsaga aylantiradi |
| **Buyruq va sezgi** | `Hotkeys` · `Formation` · `OrderMarkers` · `HitFeel` · `HitFlash` · `FloatingNumbers` · `Chevrons` | kirish → buyruq, va buyruq bajarilgani ko'rinadigan qilib |
| **Menyu** | `Shell` · `MenuStyle` · `GameSettings` | **bosh menyuni o'yin belgilaydi**: qaysi bandlar, qanday nomlanadi, yoki umuman menyusiz |
| **Ovoz** | `Sounds` · `SoundBank` · `AudioSink` · `GameSounds` | pozitsion ovoz va uning manbalari |

### Menyu kimniki

Ilgari klient bosh menyuni o'zi qurardi va buni yomon qilardi: ikkita o'yinchi
slot bo'lsa "Host LAN Game" taklif qilardi — bir kishilik zindon o'yinida
([duke-dungeon](https://github.com/Duke-Engine/duke-dungeon)) esa ikkinchi slot
bu skeletlar egasi. Bu — engine strukturaviy fakt asosida
mahsulot qarorini chiqarishi, va bu uning qarori emas.

Endi chegara aniq: **mexanizm klientniki** (`StoneMenu` — xiralashgan qatlam
va bosiladigan matn), **mazmun o'yinniki** (`Shell`). Klient faqat bandning
ma'nosi bor-yo'qligini tekshiradi (bir kishilik o'yinga LAN bandi ko'rsatilmaydi).

**Tizim masalalari klientda qoladi** — Settings (o'lcham, ovoz, fullscreen),
pauza menyusi, chiqish: bular mashina haqida, o'yin haqida emas.

```java
Duke3D.launch(game, visuals, Shell.create()
        .entry(Shell.Entry.PLAY, "Start skirmish")
        .entry(Shell.Entry.SETTINGS)
        .entry(Shell.Entry.QUIT));
```

`Shell.standard()` — avvalgi xulq (standart, ya'ni mavjud demolar va
chiqarilgan o'yinlar o'zgarmaydi) · `Shell.none()` — menyusiz, oyna
ochilishi bilan o'yin boshlanadi.

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

**Bu modul o'chirilsa nima bo'ladi:** `skirmish` butunlay yiqiladi — o'yin shu klient
ustida turadi — va chiqarilgan o'yin ham (u `client3d` ni ishlatadi).

**Modul ichida o'chiriladigan narsa yo'q** — 81 faylning hammasi ishlatiladi.

**Lekin e'tibor bering:**

1. **`DukeRtsApp` = 5 020 qator va `HeroPanel` = 4 232 qator.** Loyihadagi eng
   zich ikki fayl: ikkisi modulning uchdan biri. Ular atrofidagi kichik
   sinflar (`PanelPlace`, `Formation`, `MinimapProjection`…) jME'siz
   testlangani uchun 554 test shu yerdan keladi — testlanmagani aynan shu ikki
   faylning ichi. Agar "engine tugatish" maqsad bo'lsa, ularni bo'lish eng
   katta foyda beradi.
2. **Prezentatsiya ikki marta yozilgan:** bu modul va `game/swing` bir xil ishni
   ikki xil qiladi. Birini tanlash kerak — 3D to'liqroq (menyular, minimap,
   build menyusi, model/ovoz), 2D esa jME'siz va tez.
3. **jME versiyasi qulflangan** (3.7.0-stable) — jME bog'liqliklari tarmoq sekin
   bo'lsa `./gradlew build` ni aynan shu yerda yiqitadi (kod muammosi emas).

## Koordinatalar

Sim (x, y) → jME (x, 0, z). Yo'nalish: `fromAngles(0, -θ, 0)`, primitivlarning
oldi = +X. Minimapda ekran y dunyo y ga **teskari**.
