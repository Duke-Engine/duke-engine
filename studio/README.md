# `studio` — Duke Studio IDE

**Rol: TOOLING.** Engine emas — engine ustidagi muharrir. Swing + Gson.
`.duke` JSON loyihasi = **yagona haqiqat manbai**; engine INI'si esa **build
artefakti**.

| | |
|---|---|
| Bog'liqligi | `implementation project(":client3d")` + Gson 2.11.0 |
| Kim bunga bog'lanadi | hech kim (eng yuqori qatlam) |
| Hajmi | 17 fayl, ~3 680 qator — **`core` bilan deyarli teng** |
| Testlar | 8 ta |
| Ishga tushirish | `./gradlew :studio:run` (yoki `--args="../examples/RohanVsMordor.duke"`) |

---

## Nima bor

### Model (loyiha hujjati)

| Fayl | Qator | Vazifa |
|---|---|---|
| `model/StudioProject` | 473 | `.duke` hujjat modeli: `FactionDef` (+ `startingUnits`), `MapDef` (o'lcham, to'siqlar, **start pozitsiyalari**, neytrallar — **map'da armiya yo'q**), `UnitDef`, `PlayerDef`, `ScriptDef`. `ensureIntegrity()` osilib qolgan havolalarni tuzatadi va eski formatni migratsiya qiladi |
| `model/GameFactory` | 254 | loyiha → INI + `Visuals` + sozlangan `DukeGame`. Play ham, Export ham shundan foydalanadi ("nimani o'ynasang, shuni chiqarasan") |
| `model/CapabilityType` | 64 | qobiliyat katalogi: MOVE / ATTACK / PRODUCE / POWER / EXPERIENCE / AUTO_HEAL / SUPPLY / HARVEST → engine moduli + parametr sxemasi |
| `model/ScriptCompiler` | 129 | `javax.tools` bilan xotirada kompilyatsiya; Play xatoda ishga tushirishni **rad etadi** |

### UI

| Fayl | Qator | Vazifa |
|---|---|---|
| `ui/StudioWindow` | 737 | IDE qobig'i: menyular, toolbar, faction→unit daraxti, undo/redo (butun loyiha JSON snapshot'lari, limit 100), Ctrl+D duplicate |
| `ui/InspectorPanel` | 336 | birlik inspektori: qobiliyat checkbox'lari → parametr formasi, model/ovoz uchun "…" browse |
| `ui/MapPanel` | 310 | map muharriri: relyef cho'tkasi (1–3 katak), start-pozitsiya asbobi, neytral obyektlar, MMB-pan, g'ildirak zoom 1×–8× |
| `ui/FactionPanel` | 201 | faction inspektori + **boshlang'ich baza jadvali** (unit / dx / dy) |
| `ui/ScriptsPanel` | 176 | kod muharriri + Compile |
| `ui/PlayersDialog` | 103 | o'yinchilar: nom, rang, pul, jamoa (bir jamoa = ittifoqchi) |

### IO va export

| Fayl | Qator | Vazifa |
|---|---|---|
| `export/GameExporter` | 342 | mustaqil Gradle loyihasi: generatsiya qilingan `Main.java`, `libs/` da engine jar'lari, wrapper, `fatJar` va `packageApp` (jpackage) tasklari |
| `io/MapImporter` | 108 | `.txt`/`.map` → `MapLoader`; **rasm** (`.png`/`.jpg`) → 1 piksel = 1 katak, yorqinlik < 0.4 = to'siq |
| `io/AssetImporter` | 73 | assetni `<nom>_assets/` ga ko'chiradi, engine yo'lini qaytaradi; `.gltf`/`.mesh.xml` uchun yondosh fayllarni ham oladi |
| `io/ProjectIO` | 44 | `.duke` JSON saqlash/yuklash |
| `StudioMain` | 37 | kirish nuqtasi |

### O'YIN MAZMUNI (engine emas)

| Fayl | Qator | Vazifa |
|---|---|---|
| `examples/RohanVsMordor` | 274 | **to'liq o'yin**: Rohan vs Mordor rosterlari, tog'li map, skriptli Mordor AI. `:studio:writeExamples` → `examples/RohanVsMordor.duke` |
| `examples/ExportExample` | 19 | o'sha o'yinni `dist/RohanVsMordor/` ga chiqaradi (`:studio:exportExample`) |

---

## O'chirish tahlili

**Eng katta savol shu modulda.** "Engine tugatish, o'yin emas" maqsadiga
qaraganda, `studio` — 3 680 qator, `core` bilan deyarli teng hajm — engine'ning
o'zi emas, ustidagi mahsulot.

| Nomzod | Sabab | Narxi |
|---|---|---|
| `examples/RohanVsMordor` + `examples/ExportExample` (293 qator) + `examples/*.duke` + `dist/` | **sof o'yin mazmuni**, engine emas. Dogfooding uchun yozilgan, o'z vazifasini bajardi | `RohanVsMordorTest` (2 700 kadrlik headless urush — hozirgi eng kuchli integratsiya testi) yo'qoladi. O'chirsangiz, o'sha qamrovni boshqa joyda qoplash kerak |
| **butun `studio` moduli** | agar maqsad faqat engine bo'lsa. `core`+`rts`+`game`+`client3d` = 8 100 qator ishlaydigan engine, hech narsa yo'qotmaydi (hech kim `studio` ga bog'lanmaydi) | Map import, asset import, capability UI, export/jpackage quvuri, undo/redo — hammasi ketadi. Product vision ("istalgan odam Studio'da o'yin yasasin") bekor bo'ladi |

> Bu qaror texnik emas, **mahsulot qarori**: duke-engine kutubxonami
> (dasturchi kod yozadi) yoki mahsulotmi (odam UI'da yasaydi)?

**Modul ichida o'lik kod yo'q** — hamma UI paneli `StudioWindow` dan
ishlatiladi.

## Ma'lum kamchiliklar

1. **Chiqarilgan o'yinda map/faction tanlash yo'q** — `GameExporter` birinchi
   map'ni kodga pishirib qo'yadi, `.skirmish(…)` katalogi generatsiya qilinmaydi.
   Sabab: model va `GameFactory` `game` + `client3d` dan yuqorida turadi.
2. **Studio'da ochilmagan engine imkoniyatlari:** `StatusUpdate`,
   `SpecialPowerModule`, `ContainModule`, `Armor`, `Upgrade`, core `Trigger`.
3. 3D preview embed yo'q · INI'ni orqaga import qilish yo'q · per-inshoot rally
   nuqtasi yo'q.

## Tuzoq

**jpackage:** oddiy `commandLine("jpackage", …)` PATH'dagi eski JDK'ni oladi →
Java 21 runtime + Java 25 klasslari = `UnsupportedClassVersionError`, oynali exe
jimgina exit 1 bilan o'ladi. Yechim allaqachon qo'llangan: jpackage Gradle
toolchain'idan olinadi.
