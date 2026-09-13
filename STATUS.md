# Duke Engine — hozirgi holat va ishlash tamoyili

**Holat sanasi:** 2026-09-13 · **Testlar:** 1498 ta, hammasi yashil (0 failure / 0 error)

Bu hujjat "nima qurilgan va u qanday ishlaydi" savoliga javob beradi.
Kodlash qoidalari uchun `CLAUDE.md`, umumiy tanishtiruv uchun `README.md`.

> **2026-09-06 — engine ikkiga bo'lindi.** `core` endi janrsiz universal engine;
> RTS'ga xos hamma narsa yangi `rts` moduliga chiqdi. Quyidagi hujjat shu holatni
> aks ettiradi. Tarix: `git log` (4 ta commit, `9d1cc10` baseline).

---

## 1. Bir qarashda

duke-engine — C&C Generals Zero Hour ostidagi **SAGE engine**ining zamonaviy Java'dagi qayta
implementatsiyasi, ustiga **Duke Studio** — RTS yasash uchun IDE qo'shilgan. Maqsad: istalgan
odam Studio'ni ochib, o'z RTS o'yinini boshidan oxirigacha yasay olsin va uni .exe qilib tarqata olsin.

**Toolchain:** Java 25 (Gradle toolchain), Gradle 9.3.1, `group = uz.duke`, `version = 0.1.0-SNAPSHOT`.
Hamma modulda `-Xlint:all`, testlar JUnit 5.11.3.

| Modul | Bog'liqligi | Nima |
|---|---|---|
| `core` | — (sof Java, tashqi kutubxonasiz) | **Janrsiz** engine: deterministik simulyatsiya, INI, obyekt/modul tizimi, pathfinding, lock-step, tuman |
| `rts` | `api core` | **Janrsiz RTS bazasi**: buyruq to'plami, jang, ishlab chiqarish, iqtisod, tajriba narvoni, lug'at, save formati — mexanizmlar, qoidalar emas |
| `generals` | `api rts` | Generals'ning **o'z qoidalari** (4 rank veteranlik va h.k.) — "Generals ham shunchaki bir o'yin" isboti |
| `game` | `api rts` | Unity-uslub `DukeGame` fasadi, 2D Swing klient, multiplayer sessiyasi |
| `client3d` | `api game` + jMonkeyEngine 3.7.0-stable | To'liq 3D klient: model/animatsiya/ovoz, menyular, minimap, HUD |
| `studio` | `client3d` + Gson 2.11.0 | Duke Studio — Swing IDE (`uz.duke.studio.StudioMain`) |
| `sandbox` | `game` | 2D skirmish demo (~70 qator) |
| `sandbox3d` | `client3d` + jme3-testdata | 3D skirmish demo (~74 qator) |
| `dungeon` | `client3d` | **Duke Dungeon** — engine ustidagi ilk o'yin (3D roguelike: seed'li generatsiya + run loop + AI, ma'lumoti INI fayllarda, primitiv shakllar) |
| `worldbuilder` | `dungeon` | **Duke World Builder** — Duke Dungeon uchun qotirilgan xarita (`.stage`) muharriri, Swing (`uz.duke.worldbuilder.WorldBuilderMain`) |

**Asosiy qoida:** `core` hech qachon `rts` ni import qilmaydi. RTS bo'lmagan o'yin
yozmoqchi bo'lsangiz faqat `core` ga bog'lanasiz va o'z buyruqlaringiz, modullaringiz
va lug'atingizni berasiz — pastdagi "Kengaytirish choklari" bo'limiga qarang.

Ishga tushirish:

```
./gradlew build                  # kompilyatsiya + testlar
./gradlew :studio:run            # Duke Studio IDE
./gradlew :studio:run --args="../examples/RohanVsMordor.duke"
./gradlew :sandbox:run           # 2D demo
./gradlew :sandbox3d:run         # 3D demo
./gradlew :dungeon:run           # Duke Dungeon — engine ustidagi ilk o'yin
./gradlew :dungeon:run --args="--stage=stages/first.stage"   # qotirilgan xarita
./gradlew :worldbuilder:run      # Duke World Builder — stage muharriri
./gradlew :worldbuilder:run --args="dungeon/src/main/resources/stages/first.stage"
./gradlew :worldbuilder:writeExampleStage  # shipping stage'ni qayta yozadi
./gradlew :studio:writeExamples  # examples/RohanVsMordor.duke ni qayta yozadi
./gradlew :studio:exportExample  # dist/RohanVsMordor/ mustaqil loyihasini chiqaradi
```

---

## 2. Qatlamlar

```
+------------------------------------------------------------+
| studio — Duke Studio IDE (Swing + Gson)                    |
|   .duke loyihasi = yagona haqiqat manbai                   |
|        | Play                          | Export            |
+--------|------------------------------ |-------------------+
         v                               v
   +----------------------+     mustaqil Gradle loyihasi
   | client3d — jME 3D    |     (generatsiya qilingan Main.java
   | (yoki game'ning 2D   |      + libs/ dagi engine jar'lari)
   |  Swing oynasi)       |
   +----------+-----------+
              v
   +----------------------------------------------+
   | game — DukeGame fasadi, RtsLogic, RtsClient  |
   | (snapshot seami), MultiplayerSession         |
   +----------+-----------------------------------+
              v
   +----------------------------------------------+     +--------------------+
   | rts — RTS BAZASI: buyruqlar, jang, iqtisod,  |<----| generals — Generals|
   |       lug'at. Mexanizmlar, qoidalar emas     |     | o'yinining qoidalari|
   +----------+-----------------------------------+     +--------------------+
              v
   +----------------------------------------------+
   | core — janrsiz engine (rendering YO'Q,       |
   |        buyruq YO'Q, gameplay YO'Q)           |
   +----------------------------------------------+
```

Qoida: pastki qatlam yuqoridagini bilmaydi. `core` da rendering yo'q va hech qanday
o'yin mazmuni yo'q; `rts` da jME yo'q; `client3d` faqat snapshot o'qiydi,
simulyatsiyaga tegmaydi.

### `rts` — mexanizm, qoida emas

`core` janrni bilmaydi; **`rts` esa aniq o'yinni bilmasligi kerak.** U har RTS'ga
umumiy base mexanizmlarni beradi, qoidalarni o'yin yozadi. Har qo'shilma uchun
savol: *"Bu BFME'da ham, Warcraft III'da ham, Generals'da ham kerakmi?"*

| Savol | Javob | Qayerda |
|---|---|---|
| Birlik XP to'playdi | ha, hammasida | **mexanizm** — `rts` |
| 4 rank, har biri 1.1x zarar | yo'q, faqat Generals | **qoida** — o'yin yozadi |
| Bino birlik chiqaradi | ha | **mexanizm** — `rts` |
| Ishlab chiqarish quvvat talab qiladi | yo'q, faqat Generals | **qoida** — ixtiyoriy modul |

`generals` moduli shu printsipning isboti: Generals ham shunchaki bir o'yin, o'z
qoidalarini o'zi yozadi va buning uchun engine'ga tegmaydi.

**Snaryadlar.** `WeaponUpdate` otgan zahoti tegadi — miltiq uchun to'g'ri, lekin
o'q, snaryad, raketa — **hech biri ifodalanmasdi**. Xohlagan o'yin `WeaponUpdate`
dan butunlay voz kechishi kerak edi, u bilan birga nishon tanlash, kuluar, buyruq
yo'naltirish va `WeaponFired` hodisasidan ham. Endi qurol o'sha hammasini
bajaradi va faqat **oxirgi qadamni** beradi: egasida `ProjectileLauncher` bo'lsa,
o'q unga ketadi, zarar qachon (va tegadimi) — o'yinning ishi.

`DamageModifier`ning ko'zgusi: u zarba **qanchalik qattiq** tekkanini o'zgartiradi,
bu esa **tegadimi va qachon** tekkanini. Launcher'i yo'q birlik avvalgidek hitscan.

> Uchta qaror ataylab: berilgan zarar **yakuniy** (barcha modifikatorlar
> qo'llangan); o'q **baribir e'lon qilinadi va kuluarni yeydi**, chunki tepki
> tortilgan; va **rad etish xato emas** — joy topolmagan launcher shunday deydi,
> qurol esa o'qni o'zi tushiradi, birlik jimgina zararsiz bo'lib qolmaydi.
> Va bitta haqiqiy farq test bilan yozib qo'yilgan: **havodagi o'q uchun o'ldirish
> hisobga olinmaydi** — qurol qo'yib yuborganda nishon tirik edi.

**O'yinning o'z buyruqlari.** `core` ning `Command` shartnomasi doim shuni degan:
*"o'yin o'z buyruq to'plamini e'lon qiladi"*. Lekin `rts` uni bloklardi —
`RtsSimulation.onCommand` `final` edi va `GameMessage` bo'lmagan har qanday
buyruqni jimgina tashlab yuborardi, ya'ni `rts` ustidagi **birorta** o'yin o'z
buyrug'iga ega bo'la olmasdi. Endi u `onOtherCommand(Command)` ga uzatadi;
sukut bo'yicha — avvalgidek ogohlantirish, ya'ni bugungi hech narsa
o'zgarmaydi. O'yin uni override qiladi (`DukeGame.onCommand`) va o'z sealed
to'plami bo'yicha dispatch qiladi. Muhimi buyruq **qaysi yo'ldan** borishi:
kirish ipidan navbatga, kadr boshida qo'llanadi, frame log'ga yoziladi. Duke
Dungeon ning `CastSkill` i shu yo'ldan boradi.

> Bitta halol cheklov, jimgina emas — **log bilan**: sim protokoli (`CommandCodec`)
> RTS to'plamini biladi, shuning uchun tarmoq o'yinida o'yin buyrug'i lokal
> qo'llanib desync bermaydi, balki rad etiladi. O'z buyrug'ini simdan o'tkazmoqchi
> bo'lgan o'yin unga codec berishi kerak.

### Kengaytirish choklari

`core` o'z-o'zidan hech qanday o'yin mazmunini bermaydi. Beshta chok orqali o'yin
o'zinikini qo'yadi; `rts` — har birining ishlangan namunasi:

| Chok | `core` beradi | O'yin beradi (`rts` misolida) |
|---|---|---|
| Buyruqlar | `Command` markeri + `MessageStream` | sealed `GameMessage` (Move/Attack/Stop/Queue/Rally) |
| Sim formati | `SocketTransport` dagi `PacketCodec` plagi | `CommandCodec` |
| Xulq modullari | `ModuleFactory.withDefaults()` — `ActiveBody`, `MoveUpdate` | `RtsModules` — qurol, ishlab chiqarish, iqtisod… |
| O'yinchilar | `Player` (identity+diplomatiya) + `PlayerList(PlayerFactory)` | `RtsPlayer` (pul, upgrade) |
| Tasniflash | `Kind` — nom bo'yicha interned | `RtsKinds` (`STRUCTURE`, `INFANTRY`, …) |
| Hodisalar | `WorldEvent` + post/drain kanali | `WeaponFired` (core `ObjectDied` yonida) |

**Nega buyruqlar core'da emas:** sealed ierarxiya modul chegarasidan o'ta olmaydi.
Bu cheklov aslida to'g'ri shakl — engine qaysi o'yin qurilayotganini bilmasligi kerak,
o'yin esa o'z buyruqlari ustidan exhaustive `switch` yozadi.

**`rts` ustidagi choklar** — o'yin RTS qoidalarini shular orqali yozadi, `rts` ga
tegmasdan:

| Chok | `rts` beradi | O'yin beradi |
|---|---|---|
| Zarar modifikatsiyasi | `DamageModifier` — `WeaponUpdate` hammasini ko'paytiradi | daraja bonusi, buff, veteranlik — **birlik bo'yicha** |
| Ishlab chiqarish sharti | `ProductionGate` — zavod hammasidan so'raydi | quvvat/food/nima bo'lsa; hech narsa bo'lmasa to'xtovsiz quriladi |
| Daraja narvoni | `ExperienceModule` — XP + sozlanadigan rung jadvali | nechta rung, har biri necha XP va qancha bonus |
| Tana | `BodyModule` (abstract) | o'sadigan tana, maxsus zirh — `ActiveBody` ni almashtirib |
| HUD ko'rsatkichi | `WorldSnapshot.status` — engine hech qachon o'qimaydi | daraja, to'lqin raqami, taymer |
| Kompozitsiya | `addModule` / `removeModule` / `replaceModule` | birlik ish vaqtida nima ekanini o'zgartirishi |

---

## 3. `core` — janrsiz engine yadrosi

### 3.1 Asosiy sikl

`GameEngine` — o'zi subsystem bo'lib, boshqa subsystemlarni egallaydi. `execute()` = asosiy sikl:

- Mantiq qat'iy **30 Hz** da qadam tashlaydi (`LOGICFRAMES_PER_SECOND = 30`, `STEP_NANOS ≈ 33.33ms`).
- Klient har aylanishda bir marta render qiladi, `maxFps` (standart 45) bilan cheklanadi.
- Vaqt akkumulyatorda yig'iladi; uzoq qotib qolish "catch-up spirali"ga olib kelmasligi uchun
  `MAX_ACCUMULATED_NANOS = 250ms` bilan cheklangan.
- Kadr faqat shu shartda oldinga siljiydi: pauza emas **va** `isLogicFrameReady()` true
  (single-player'da doim true; multiplayerda peer ma'lumotini kutadi).

Bu — SAGE o'zi `@todo` qilib qoldirgan, lekin hech qachon amalga oshirmagan mantiq/render ajratishi.

### 3.2 Bir mantiq kadri nima qiladi

`GameLogic.update()` aniq shu tartibda:

0. `refreshStaticObstacles()` — dunyo o'zgargan bo'lsa navigatsiya gridining
   to'siq qatlami qayta quriladi (shu kadrda beriladigan `MoveTo` dunyoni
   hozirgi holida ko'rishi uchun buyruqlardan **oldin**)
1. `messageStream.propagate(onCommand)` — navbatdagi buyruqlar qo'llanadi
2. `updateObjects()` — har obyektning update-modullari **yaratilish tartibida** tiklanadi
3. `reapDestroyed()` — o'lgan obyektlar dunyodan chiqariladi, so'ng `ObjectDied` e'lon
   qilinadi va `DieModule` lar ishlaydi (jasad allaqachon yo'q dunyoda)
4. `simulate()` — o'yinga xos hook (`game` moduli buni to'ldiradi)
5. `scriptEngine.evaluate()` — trigger'lar (g'alaba/mag'lubiyat, map hodisalari)
6. `frame++`

### 3.3 Obyekt-modul modeli

SAGE'ning `ThingTemplate` / `Object` / `Module` tuzilishi saqlangan:

- **`ThingTemplate`** — INI'dan o'qilgan tur: nom, `Kind` bayroqlari, `BuildCost`/`BuildTime`,
  `VisionRange`, **`Geometry`**, modul ro'yxati. Yuklangandan keyin o'zgarmas.
- **`Geometry`** — obyektning **fizik shakli** (SAGE `GeometryInfo`): sealed
  `Sphere` / `Cylinder` / `Box`. `Box` obyekt bilan birga buriladi. Standart —
  `Geometry.POINT` (o'lchamsiz, hech narsa bilan to'qnashmaydi), shuning uchun
  geometriyadan oldin yozilgan ma'lumot avvalgidek ishlaydi.
- **`Footprint`** — shakl + joy + yo'nalish, ya'ni "yer ustidagi iz":
  `overlaps`, `separation` (**yuzadan yuzaga**, ustma-ust tushsa manfiy),
  `contains`. Birlik ↔ istalgan shakl aniq hisoblanadi; ikkita `Box` bir-biriga
  qarshi o'rab turuvchi doira bilan taqqoslanadi (yagona taqribiylik).
- **`GameObject`** — jonli nusxa: `ObjectId` (yaratilish tartibida monoton), pozitsiya, orientatsiya,
  egasi, modullar. `findModule(Class)` bilan qidiriladi.
- **`Module`** → `UpdateModule` (har kadr `update()`) / `BodyModule` (sog'liq, zarar).
- **`ModuleFactory.withDefaults()`** faqat 2 ta janrsiz tag beradi: `ActiveBody` (sog'liq+zirh)
  va `MoveUpdate` (nuqtaga yurish). Qolgan 10 tasi RTS'niki — `RtsModules` ro'yxatdan o'tkazadi.
- **`Locomotor`** — marker interfeys: "bu modul obyektni o'z kuchi bilan
  harakatlantira oladi". `MoveUpdate` uni implement qiladi. Engine shu orqali
  janrsiz savolga javob beradi: bu obyekt relyefning bir qismimi?
  (`GameObject.isMobile()`)
- **`Kind`** — tasniflash bayrog'i, **nom bo'yicha interned** (`Kind.of("STRUCTURE")`). Eski
  23 qiymatli `KindOf` enum'i o'chirildi: engine "HARVESTER" nima ekanini bilmasligi kerak.
  Solishtirish baribir identity tekshiruvi, ya'ni enum kabi arzon.
- **`World`** interfeysi — modullar simulyatsiyani shu orqali so'roq qiladi (`findObject`,
  `getRelationship`, `getPlayer`, `findTemplate`, `spawn`, `findPath`, `findClosest`,
  `objectsInRange`, `getObjects`). SAGE'dagi global `TheGameLogic` o'rnini bosadi va
  paket sikllarini oldini oladi. **Fizik dunyo qismi:** `findBlocker(mover, pos)`
  ("shu yerga qadam tashlasam kim xalaqit beradi?"), `findClearPosition(shape,
  near, radius)` ("bu shakl qayerga sig'adi?"), `findClosestInReach(from, reach,
  filter)` va `World.reachBetween(a, b)` (masofa markazdan emas, devordan devorga).

### 3.4 INI ma'lumot qatlami

`core.ini.Ini` — SAGE tokenizatorining sodiq porti (ajratkichlar bo'sh joy / tab / `=`, `;` = izoh,
blok/`End` sikli, `X:Y:Z` sub-tokenlari). `FieldParseTable`/`FieldParser` C++ dagi offset+userData
hiylasini lambda-setter bilan almashtiradi. `ThingTemplateLoader` `Object … End` bloklarini,
ichidagi modul sub-bloklari bilan birga, template'ga aylantiradi.

```ini
Object Tank
  DisplayName = Battle Tank
  KindOf = VEHICLE SELECTABLE CAN_ATTACK
  Geometry = BOX
  GeometryMajorRadius = 8
  GeometryMinorRadius = 5
  GeometryHeight = 6
  BuildCost = 700
  BuildTime = 6.0
  VisionRange = 45
  Body = ActiveBody Tag
    MaxHealth = 300
  End
  Update = MoveUpdate Tag
    Speed = 20
    TurnRate = 120
  End
  Update = WeaponUpdate Tag
    Damage = 40
    AttackRange = 30
    ReloadFrames = 45
    SplashRadius = 6
    DamageType = EXPLOSION
  End
End
```

### 3.5 Buyruq quvuri

`core` faqat **`Command`** markerini biladi (bitta metod: `playerIndex()`). Buyruqlarning
o'zi o'yinniki — `rts` da **sealed** `GameMessage` ierarxiyasi, 5 ta record: `MoveTo`,
`AttackObject`, `StopMoving`, `QueueProduction`, `SetRallyPoint`. SAGE'ning Type enum'i
o'rniga sealed + pattern-switch → yangi buyruq qo'shsangiz kompilyator uni qo'llash kerak
bo'lgan hamma joyni ko'rsatadi.

`issueCommand()` → `MessageStream` (FIFO) → **keyingi kadr boshida** `onCommand()` ga drenaj
qilinadi. Buyruq hech qachon "hozir" qo'llanmaydi — aynan shu narsa lock-step va replay'ni mumkin qiladi.

`RtsSimulation` `Command` ni bir marta `GameMessage` ga toraytiradi (`onRtsCommand`), shuning
uchun har bir RTS mantiq sinfi exhaustive `switch` yozadi, cast takrorlanmaydi. RTS bo'lmagan
buyruq jimgina tashlanmaydi — WARNING bilan log qilinadi.

### 3.6 Yo'l topish

`PathGrid` (katak = 10 dunyo birligi) + `Pathfinder` = deterministik A*: butun sonli narxlar
(to'g'ri 10 / diagonal 14), octile evristika, navbat tenglikda katak indeksi bo'yicha uziladi,
qo'shnilar qat'iy tartibda, burchak kesish yo'q. `MapLoader` ASCII matndan grid quradi
(`#`/`X` = to'siq). Grid o'rnatilmagan bo'lsa `findPath` to'g'ri chiziq qaytaradi.

**Qidiruvdan keyin ikkita qadam, va ular harakat qanday ko'rinishiga qidiruvning
o'zidan ko'ra ko'proq ta'sir qiladi:**

- **Yo'l to'g'rilanadi (string pulling).** Grid qidiruvi faqat kataklar bilan
  javob bera oladi, ya'ni waypoint'lar — katak markazlari, va bo'sh maydondan
  yurish zinapoyaga aylanadi: o'ng, diagonal, o'ng, diagonal — to'g'ri chiziq
  butunlay bo'sh bo'lsa ham. Shuning uchun kataklar topilgach, ulardan
  burchaklar tortib olinadi: faqat **oldindan ko'rib bo'lmaydigan** waypoint'lar
  qoladi. Ochiq yerda bu bittagina waypoint qoldiradi va birlik to'g'ri yuradi.
- **Yo'l birlikning kengligini hisobga oladi.** Qidiruv uchun kataklar — nuqta,
  birliklar esa nuqta emas. Burchakka tegib o'tadigan yo'l nuqta uchun to'g'ri,
  **tana** uchun noto'g'ri; tana buni to'qnashib bilib oladi, va bu tashqaridan
  sababsiz yoy chizib yurishga o'xshaydi. `World.findPath(mover, to)` birlikning
  `Geometry.footprintRadius()` ini uzatadi; qidiruv ham, to'g'rilash ham shu
  masofani toshdan saqlaydi. **Yetarli keng yo'l topilmasa**, qidiruv kenglikni
  hisobga olmasdan qayta yuritiladi — siqilib o'tish umuman yurmaslikdan yaxshi.

Determinizm: to'g'rilash chiziqni **chorak katak** qadam bilan namuna oladi,
faqat `sqrt`/`floor`/`ceil` va butun sonli sikllar — trigonometriya yo'q.

`PathGrid` **ikki qatlamli**, va bu ataylab:

| Qatlam | Kim yozadi | Qachon o'zgaradi |
|---|---|---|
| **relyef** (`setBlocked` / `isTerrainBlocked`) | map: qoyalar, suv, devorlar | hech qachon — bir marta muallif yozadi |
| **to'siqlar** (`setObstacle` / `clearObstacles`) | simulyatsiya: binolar va boshqa harakatlanmaydigan jismlar | obyekt paydo bo'lganda/o'lganda |

`isBlocked` = ikkalasining OR'i. Ajratilgani shuning uchunki, qoyaga tiralib
qurilgan bino buzilganda **qoyada teshik qolmasligi** kerak.

### 3.5c Balandlik — diskret qavatlar

Katakning **butun sonli qavati** bor (`level`), va har xil qavatdagi ikki katak
qo'shni **emas**: orasidagi qadam devor kabi rad etiladi. Ularni bog'laydigan
narsa — **ramp** kataki (`isRamp`): "bu katak o'z qavatini qo'shnisiga ulaydi".
Zinapoyami, pandusmi, narvonmi, liftmi — bu **o'yinning** gapi; `core` faqat
"bu katak ulaydi" deb biladi. Shu sababli `core` da "zinapoya" so'zi yo'q.

Yagona qoida — `PathGrid.canStep(from, to)`:

| Shart | Natija |
|---|---|
| ikkala katak ochiq emas | ✗ |
| qavatlar teng | ✓ |
| farq 1 dan katta | ✗ |
| diagonal + qavat farqi | ✗ (burchakdan ko'tarilgan jism qadam davomida devor ichida bo'ladi) |
| farq aynan 1, to'g'ri qadam, uchidan biri ramp | ✓ |

**Nega butun son:** butun son har mashinada bir xil xeshlanadi, yaxlitlashdan
siljimaydi va simulyatsiya balandlikdan so'raydigan **yagona** savolga javob
beradi — u yerga yura olamanmi, biz bir qavatdamizmi. Uzluksiz relyef, qiyalik
va harakat narxi — butunlay boshqa va ancha qimmat narsa; bu yerdagi hech nima
unga qadam emas.

Qoida **uchta** joyda ushlanishi shart, va ikkinchisi ko'zdan yashirin:
`Pathfinder.expand` (qidiruv), `Pathfinder.isClearLine` (**to'g'rilash** — qidiruv
zinapoyadan ko'tarilgandan keyin to'g'rilash o'sha waypointlarni tashlab yuboradi
va to'g'ri chiziq har bir katakda ochiq ko'rinadi), va `MoveUpdate` ning har
qadami.

Yondosh natijalar: `cellCenter` endi `z = level × levelHeight` qaytaradi;
`MoveUpdate` har qadamdan keyin `z` ni **yerdan o'qiydi** (ko'tarib yurmaydi —
aks holda zinapoyaga chiqqan jism tepada ham pastdagi balandligida turadi);
`findBlocker` faqat **bir xil qavatdagi** jismni to'siq deb biladi (yuqoridagi
xona ostidagi koridor — bir xil katak, boshqa joy).

**Tekis dunyo hech narsani sezmaydi.** Hech kim aytmagan grid: hamma qavat 0,
ramp yo'q, `levelHeight = 0` — yuqoridagi har bir qoida avvalgisiga qaytadi va
`Coord3D.z` 0 bo'lib qoladi, ya'ni checksum bit-ma-bit o'zgarmaydi. Buni
`FlatWorldChecksumTest` **golden master** bilan qulflaydi: devor atrofidan yo'l,
to'qnashuv, chetlab o'tish va kelish bor dunyo 300 kadr yuritiladi va butun
dunyoning xeshi balandlik qo'shilishidan **oldingi** engine'dan olingan songa
solishtiriladi. O'sha son testni o'tkazish uchun yangilanmaydi.

Matndan o'qish (ixtiyoriy, ikkinchi qatlam): `MapLoader.levels(grid, text)` —
`0`–`9` qavat, `.` = 0, `/` = ramp (atrofidagi eng past qavatda turadi, ya'ni
zinapoyaning **etagida**), `#`/`X`/bo'shliq = tosh. Tanilmagan belgi — **istisno**,
jimgina 0 emas.

### 3.6b To'qnashuv — obyektlar fizik jism

Obyektning `Geometry` si bo'lsa, u yer egallaydi va boshqasi u yerda tura olmaydi.

- **Har qadamdan oldin tekshiriladi.** `MoveUpdate` qadam tashlashdan avval
  `World.findBlocker(mover, keyingiJoy)` ni so'raydi. Band bo'lsa, qat'iy
  tartibda chetlab o'tishga urinadi: to'g'ri → +45° → −45° → +90° → −90°.
  Tartib qat'iy, chunki ikkala peer to'siqni bir tomondan aylanib o'tishi shart.
- **Qotib qolish o'lchovi = ilgarilash, bloklanish emas.** To'siq atrofida
  aylanayotgan birlik har kadr bo'sh yo'l topadi, ya'ni "bloklangan kadrlarni
  sanash" uni abadiy aylantiradi. Shuning uchun waypoint'gacha bo'lgan eng
  yaqin masofa yodda tutiladi; 2 soniya davomida sezilarli yaqinlashish
  bo'lmasa, yo'ldan voz kechiladi (birlik yeta oladigan eng yaqin joyda to'xtaydi).
- **Nishonning ustiga yuborilgan birlik uni aylanmaydi.** Boshqa birlikning
  tanasi ichidagi nuqtaga hech qachon yetib bo'lmaydi, urinishda davom etgan
  birlik esa joyida turmaydi — chetga qadam tashlaydi, uni bo'sh deb topadi,
  yana chetga tashlaydi va nishon atrofida sekin doira chizadi. Shuning uchun
  yo'lni to'sib turgan narsa **manzilni o'z ichiga olsa**, bu yetib borish deb
  hisoblanadi va birlik to'xtaydi. Tekshiruv ataylab tor: boshqa har qanday
  to'siq — aylanib o'tiladigan narsa, aylanib o'tish esa vaqtincha maqsaddan
  uzoqlashishni talab qiladi.
- **Ichkarida paydo bo'lgan birlik qulflanmaydi.** Agar obyekt hozir allaqachon
  biror narsa bilan kesishib tursa, u chiqib ketguncha to'qnashuv tekshiruvi
  o'tkazib yuboriladi.
- **Geometriyasi yo'q obyekt** (`POINT`) hech narsa bilan to'qnashmaydi — eski
  ma'lumot xatti-harakati bit-aniq saqlanadi.

RTS tomonda bu ikki qoidani o'zgartirdi: ishlab chiqarilgan birlik zavod
devoridan **tashqarida, bo'sh yerda** paydo bo'ladi (`findClearPosition`), va
qurol masofasi **yuzadan yuzaga** o'lchanadi — devorga tiralgan piyoda katta
binoni ura oladi, garchi markazlar orasi masofa qurol radiusidan katta bo'lsa ham.

### 3.6c Harakatlanmaydigan jism = relyef

Lokal chetlab o'tish o'nlab birlik masofasida ishlaydi, baza bo'ylab emas —
A* ning o'zi binolarni bilishi kerak. Shuning uchun **harakatlanmaydigan
geometrik obyekt `PathGrid` ning to'siq qatlamiga bosiladi.**

- **"Harakatlanmaydigan" janrsiz aniqlanadi.** `core` `STRUCTURE` degan RTS
  lug'atini bilmaydi. O'rniga: obyektda **`Locomotor`** (yangi marker interfeys,
  `MoveUpdate` uni implement qiladi) bormi? Yo'q bo'lsa, u harakatlana olmaydi —
  o'yin uni nima deb atashidan qat'i nazar. O'z harakat moduli bor o'yin shu
  interfeysni implement qiladi va boshqa hech narsa ulash kerak emas.
- **Butunlay qayta quriladi, sanoq yuritilmaydi.** To'plam kichik (faqat
  harakatlanmaydigan narsalar), va to'liq qayta qurish dunyodan ajralib qola
  olmaydi — sanoqli hisob esa ajralib qoladi. `staticObstaclesDirty` bayrog'i
  bilan faqat kerak bo'lganda ishlaydi (obyekt yaratilganda, o'lganda, grid
  o'rnatilganda), `update()` boshida va `findPath` da tekshiriladi.
- **Katak band deb hisoblanadi**, agar obyekt konturi katak markazidan yarim
  katak masofagacha yaqin bo'lsa. **Ataylab kam bloklanadi:** noto'g'ri ochiq
  qolgan katakni `MoveUpdate` ning har qadamdagi tekshiruvi ushlaydi, noto'g'ri
  yopilgan katak esa binoning o'z eshigini berkitib qo'yishi mumkin.
- `findClearPosition` endi relyefni ham hurmat qiladi — birlik qoyaning ichida
  paydo bo'lmaydi.

**Yo'lda ketayotgan birlik dunyo o'zgarganini biladi.** Yo'l — dunyoning o'sha
paytdagi holatiga qurilgan reja. Simulyatsiya eskirgan rejani ushlab turganlarni
qidirib yurmaydi; o'rniga `PathGrid` **to'siq qatlami versiyasini** yuritadi va
har bir `MoveUpdate` o'z marshrutini qaysi versiyada tuzganini eslab qoladi.
Raqamlar farq qilsa, birlik turgan joyidan yo'lni qayta tuzadi; yo'l qolmagan
bo'lsa — to'xtaydi.

Versiya **natijaga** qo'yiladi, qayta qurishga emas: to'siq qatlami har bir
obyekt paydo bo'lganda/o'lganda qayta quriladi (RTS'da bu doimiy), lekin
navigatsiya xaritasi ancha kam o'zgaradi. `commitObstacles()` yangi qatlamni
eskisi bilan solishtiradi va faqat haqiqatan farq bo'lsa versiyani oshiradi —
aks holda har bir tayyor bo'lgan piyoda butun armiyani qayta yo'l tuzishga
majbur qilardi.

### 3.6d Hodisalar — holat emas, **lahza**

Snapshot nima **bor**ligini aytadi; nima **bo'lgan**ini ayta olmaydi. "Bu birlik
40 sog'liqda" — holat; "bu birlik hozir portladi" — yo'q, chunki klient qaraganda
obyekt dunyoda yo'q. Kanalsiz klient taxmin qilishga majbur bo'ladi, taxmin esa
chekkalarda xato: o'lgani uchun yo'qolgan birlik tuman ortiga kirgan birlikdan
farq qilmaydi.

- **`WorldEvent`** — marker (`frame()`, ixtiyoriy `where()`), xuddi `Command`
  kabi. `core` o'zining janrsiz hodisasini beradi — **`ObjectDied`** (nima edi,
  kimniki, qayerda — chunki o'qilganda obyekt yo'q); o'yin o'zinikini yoniga
  qo'yadi — `rts` da **`WeaponFired`**.
- **Bir tomonlama.** Simulyatsiya ularni qaytib o'qimaydi, `checksum()` ga
  kirmaydi, hammasini tashlab yuborilsa ham bironta kadr natijasi o'zgarmaydi.
  Aynan shu ularni renderga berishni xavfsiz qiladi.
- **Drenaj qilinadi, tozalanmaydi.** Engine bitta klient kadriga bir nechta
  mantiq kadrini yugurtirishi mumkin (catch-up), shuning uchun "har kadr tozalash"
  oradagi lahzalarni jimgina yo'qotardi. Navbat chegaralangan (1024) — hech kim
  drenaj qilmasa (headless) eng eskisi tashlanadi.
- **Tuman hodisaga ham tegishli.** `RtsClient` ularni `canSee(viewer, where)`
  orqali filtrlaydi — aks holda o'yinchi ko'zi yetmagan hududdagi portlashni
  eshitardi. Shu uchun `GameLogic` da `canSee(viewer, Coord3D)` overloadi bor:
  narsa haqida emas, **joy** haqida savol.
- **`DieModule`** — o'limning gameplay yarmi (vayrona, portlash zarari). Obyekt
  dunyodan chiqqandan **keyin** chaqiriladi, ya'ni u yaratgan vayrona jasad
  turgan dunyoga tushmaydi. Modul simulyatsiyani o'zgartiradi va deterministik
  bo'lishi shart; hodisa esa faqat renderga xabar beradi.

Nima yopildi: 3D klient endi o'limni `hp < 35%` evristikasi bilan **taxmin
qilmaydi** va dul olovini devor-soati bo'yicha miltillatmaydi — o't ochish ovozi
har **o'q** uchun chalinadi, har jang uchun bir marta emas.

### 3.6e Replay — o'yinni yozib olish

Deterministik simulyatsiya — bu **boshlang'ich shart + qo'llangan buyruqlar**ning
funksiyasi. Demak yozib olinadigan narsa aynan shu ikkitasi; dunyoning o'zini
saqlash shart emas, uni qaytadan hisoblab chiqarsa bo'ladi. Bu — lock-step
allaqachon tayanadigan xususiyatning o'zi.

`uz.duke.core.replay`: `FrameLog` (yozib olish choki) · `ReplayRecorder` ·
`Replay` (o'qish + haydash) · `ReplayMismatch`.

- **Format = sim protokolining o'zi.** Yozuv `NetFraming` bilan yoziladi:
  buyruqli kadrlar `C …`, tekshirish nuqtalari `K …`. Replay — bu tashlab
  yuborilmasdan saqlab qolingan tarmoq oqimi, shuning uchun unga alohida format
  o'ylab topilmadi.
- **Nima yoziladi:** simulyatsiya **haqiqatan iste'mol qilgan** buyruqlar, kimdir
  bosgan tugma emas. Tarmoq o'yinida bular farq qiladi (kirish kadrlar oldin
  jo'natiladi va peer'lar kelishgan tartibda keladi) — iste'mol qilinganini
  yozish bitta recorder'ni single-player uchun ham, multiplayer uchun ham
  ishlatadi.
- **Tekshirish nuqtalari — asosiy qiymat.** Har 30 kadrda dunyo xeshi yoziladi.
  Qayta o'ynatishda mos kelmasa — bu determinizm nosozligi **build ichida**, kadr
  raqami bilan ushlangan bo'ladi; jonli o'yinda, birovning mashinasida, hech
  qanday iz qoldirmay emas.
- **Tuzoq (yopilgan):** simulyatsiyaning o'zi buyruq yaratsa (skript, taymerli
  kuchaytirish), qayta o'ynatishda u **ikki marta** qo'llanardi — bir marta o'zi
  yaratgani uchun, bir marta yozuvdan. Shuning uchun `Replay.beforeStep` avval
  `discardPendingCommands()` qiladi: yozuv — kadr kirishi haqidagi yagona haqiqat.
- **O'yinning o'zi faylda yo'q.** Yozuv faqat o'zini yaratgan build va o'yin
  ta'rifiga qarshi ma'noga ega; boshqasiga qarshi birinchi tekshirish nuqtasi
  buni aytadi.

`DukeGame`: `recordReplay()` / `getReplayText()` / `playReplay(text)`.
Qayta o'ynatishda jonli kirish e'tiborsiz qoladi — yozuv nima bo'lganini
allaqachon aytgan.

### 3.7 Tarmoq (lock-step)

Klassik RTS modeli: **dunyo holati simdan o'tmaydi, faqat buyruqlar.**

**Ikkita plan bitta ulanishda.** `NetMessage` — sealed: `CommandPacket` (**ma'lumot**
plani, o'yinniki, o'yinning `PacketCodec` i kodlaydi) va `PeerLeft` (**boshqaruv**
plani, engine'niki, engine o'zi kodlaydi). Tashqi konvert — `NetFraming`
(`C <yuk>` / `L <idx> <frame>`). Ajratilgani sababli engine o'zi hech narsa
bilmaydigan o'yin uchun ham a'zolikni boshqara oladi.

- `LockstepScheduler` — kadr darvozasi: `submit`, `isFrameReady`, `takeCommands`
  (o'yinchi indeksi bo'yicha o'sish tartibida). A'zolik **kadrga bog'liq**:
  `retirePlayer(player, fromFrame)`, `isExpectedAt(frame, player)`.
- `LockstepGate` — engine har kadr so'raydigan darvoza: "shu kadr o'tsinmi, o'tsa
  hamma nima qildi?". Prime, `pump`, lokal buyruqni `frame + frameDelay` ga
  jo'natish, tayyor bo'lmasa **to'xtash**. (Eski `LockstepDriver` o'chirildi — u
  `logic.update()` ni o'zi chaqirardi, ya'ni siklni `GameEngine` boshqaradigan
  jonli yo'lga to'g'ri kelmasdi.)
- `Transport` — `send` / `subscribe` / `pump` / `onLinkLost`. Implementatsiyalar:
  `LoopbackTransport` (in-process), `SocketTransport` (bitta ulanish — mehmon
  tomoni), **`HostTransport`** (host tomoni: har mehmonga bitta ulanish, kelgan
  har xabarni qolganlarga **uzatadi**).
- `PacketCodec` — sim formati **plagi**; RTS implementatsiyasi `rts` dagi
  `CommandCodec` (exhaustive switch, `Float.toString` aniq).
- `GameLogic.checksum()` — butun dunyoning deterministik xeshi (SAGE `VERIFY_CRC`);
  `floatToIntBits` ishlatadi, shuning uchun float holat hamma mashinada bir xil xeshlanadi.

Tashqi kutubxona **yo'q** — hammasi `java.net`.

### 3.7b Topologiya: host = markaz

Mehmonlar bir-biri bilan ulanmagan — faqat host bilan, host esa uzatadi.
Shuning uchun **xabar oqimi tartibga solinadigan yagona nuqta host**: har bir
mehmon host nimani qabul qilgan bo'lsa, aynan o'sha tartibda oladi. Bu butun
dizayn tayanadigan xususiyat.

Narxi: host alohida — uning kechikishi hech kimga muammo emas, lekin o'ziga
afzallik; host chiqsa o'yin tugaydi. To'liq mesh buni har juftlik orasida ulanish
va har mashinada port ochish evaziga almashtiradi.

### 3.7c Uzilish — muzlash emas, qaror

Lock-step'ning kuchi to'xtashda: kimdir yetishmasa, taxmin qilinmaydi. Xavfi ham
shunda — g'oyib bo'lgan o'yinchi hammani abadiy to'xtatib qo'yadi. (Ilgari aynan
shunday edi: mehmon Alt+F4 qilsa, host'ning o'yini jimgina o'lardi.)

Uzilishni har bir peer o'zicha sezishi **mumkin emas** — ikki peer uchinchisini
turli kadrlarda kutishdan to'xtasa, ular turli buyruq to'plamini qo'llaydi va
dunyolar ajraladi. Shuning uchun:

1. Qaror **faqat host**niki. U ketgan o'yinchining jimligini `frame + frameDelay`
   gacha bo'sh paketlar bilan to'ldiradi (hali hech kim o'ynamagan kadr), so'ng
   `PeerLeft(player, fromFrame)` e'lon qiladi.
2. To'ldirish paketlari ham xuddi shu relay orqali ketadi, ya'ni hech bir peer
   "u ketishidan oldin nima qilgan edi" degan savolga boshqacha javob bermaydi.
3. Har bir peer o'sha **aynan bir kadrda** o'yinchini kutishdan to'xtaydi.
4. `takeCommands` allaqachon nafaqaga chiqqan o'yinchining paketini e'tiborsiz
   qoldiradi — ya'ni paket yetib ulgurgani yoki ulgurmagani ahamiyatsiz.

Mehmon host'ni yo'qotsa, o'zi hech narsa hal qila olmaydi: `isConnectionLost()`
true bo'ladi va o'yin buni aytadi ("CONNECTION LOST") — chunki tashqaridan
uzilgan peer bilan sekin o'yinchini kutayotgan peer bir xil ko'rinadi.

### 3.7d Desync aniqlash — va'dani tekshirish

Lock-step — bu **va'da**, mexanizm emas: peer'lar buyruq almashadi va ularni
ishlatish hamma joyda bir xil dunyo beradi deb **ishonadi**. Buni hech narsa
tekshirmasdi. Bitta tartibsiz iteratsiya, bitta devor-soati o'qish, bitta
platformaga bog'liq `Math.sin` — va'da jimgina buziladi, buni esa o'yinchilar
bilib qoladi: har biri boshqalar ko'rmaydigan o'yinni o'ynayotgan bo'ladi.

Endi har **`CHECKSUM_INTERVAL = 30`** kadrda (30 Hz da soniyada bir marta) har
bir peer o'z dunyosini xeshlaydi va raqamni ovoz chiqarib aytadi
(`FrameChecksum`). Relay tufayli hamma hammanikini oladi va solishtiradi; farq
chiqsa — `Desync(frame, localPlayer, localChecksum, otherPlayer, otherChecksum)`.

**Nomuvofiqlik o'yinni tugatadi.** `SessionState` `RUNNING` dan `DESYNCED` ga
o'tadi, darvoza boshqa hech qachon ochilmaydi — keyingi kadr hisoblanmaydi va
boshqa paket ham jo'natilmaydi. Sabab oddiy: peer'lar endi **boshqa-boshqa
dunyo** hisoblamoqda, ya'ni har bir keyingi kadr — o'yinchining faqat o'zi
ko'radigan o'yin haqida qaror qabul qilishi. Buzilgan joyda to'xtash va sababni
aytish — halol yakun.

Qarorlar:

- **Xesh kadr *boshlanishidan oldin* olinadi.** Bu oldingi kadrdan keyingi holat
  — har bir peer o'tadigan aniq nuqta, va post-step hook'siz kelishish mumkin
  bo'lgan yagona nuqta.
- **Bir marta xabar beriladi.** Ajralish kuchayib boradi: birinchi
  nomuvofiqlikdan keyin har bir kadr farq qiladi, takrorlash yangi hech narsa
  aytmaydi. Muhimi — **birinchi** kadr raqami, chunki ajralish bosqichma-bosqich
  emas: bir kadr dunyolar bir xil, keyingisida yo'q.
- **Host e'lon qiladi** (`SessionHalted` — ham "to'xta" buyrug'i, ham sababi:
  kadr, ikkala xesh, peer indeksi). Nima uchun bu yetarli: hamma hammaning
  xeshini oladi va tenglik tranzitiv, demak har qanday nomuvofiqlikda **host
  albatta ishtirok etadi**. Ikkita mehmon bir-biridan farq qilib, ikkalasi ham
  host bilan mos kelishi mumkin emas. Shuning uchun alohida arbitratsiya kerak
  emas — "qaysi peer haq" degan savol qo'yilmaydi, hamma to'xtaydi.
- **Peer'lar bir kadr farq bilan to'xtashi mumkin** — nomuvofiqlikni faqat
  qarshi tomonning xeshi kelgach bilish mumkin, o'shangacha biri yana bir qadam
  tashlab ulgurishi mumkin. Bu zararsiz: o'yin baribir tugadi, muhimi hech biri
  yana qadam tashlamasligi.
- **Tuzatish yo'q va bu ataylab.** Qayta sinxronlash yoki qayta ulanish — aniq
  bir o'yin haqidagi qarorlar, engine haqidagi emas. `LockstepGate` `SEVERE`
  log yozadi (kadr, kutilgan xesh, kelgan xesh, peer id), `DukeGame` esa
  `Synchronization lost - game stopped` bayrog'ini qo'yadi — uni ikkala klient
  ham (2D `GamePanel`, 3D `DukeRtsApp`) markazda chizadi, ya'ni ekran jimgina
  qotib qolmaydi.

### 3.8 Boshqa core tizimlari

O'yinchilar/diplomatiya (`Player` — index, nom, munosabat; `PlayerList` o'yinning o'z
`Player` tipini yaratadigan `PlayerFactory` bilan; `Relationship`) · sog'liq va zarar
(`BodyModule`/`ActiveBody`, `DamageType`, `Armor`) · harakat (`MoveUpdate` — tezlik,
burilish tezligi, waypoint'lar, **to'qnashuv**) · tuman (`canSee`/`getVisibleObjects` —
o'zinikini doim ko'radi, ittifoqchilar ko'rishni bo'lishadi) · fazoviy so'rovlar
(`PartitionManager` + `PartitionFilter`) · skript triggerlari (`Trigger` + `ScriptEngine`) · rendering choki
(`Renderer` + `RenderingGameClient`) · saqlash **mexanizmi** (`clearWorld`, `setFrame`,
`setNextObjectId`, `restoreObject` — format o'yinniki).

---

## 3b. `rts` — RTS moduli

`core` ustidagi RTS qatlami. Ichida:

- **`RtsSimulation`** — RTS mantiqining bazasi: `Command` → `GameMessage` toraytirish,
  `RtsModules` o'rnatish, `RtsPlayer` rosteri, `purchaseUpgrade`, `getRtsPlayer`.
- **`message.GameMessage`** — sealed RTS buyruq to'plami; **`network.CommandCodec`** — sim formati.
- **`module.*`** — `WeaponUpdate` (reload, masofa, splash, ittifoqchini urmaydi),
  `ProductionUpdate` (navbat, pul yechish, rally), `SupplyModule` + `HarvestUpdate`,
  `PowerModule` + `PowerGrid` (tomonning sig'im balansi — Generals'da quvvat,
  WC3'da food, BFME'da sobit cap: bitta mexanizm) va **ixtiyoriy** `CapacityGate`
  (balans manfiy bo'lsa ishlab chiqarish to'xtaydi — bu **qoida**, shuning uchun
  zavod uni o'z ta'rifida so'raydi; `ProductionGate` choki orqali o'yin o'z
  shartini ham yozishi mumkin),
  `ExperienceModule` — **sozlanadigan rank narvoni** (nechta rung, har biri necha XP va
  qancha zarar bonusi — hammasi INI'dan; `HealOnPromotion` ham so'raladi, taxmin qilinmaydi),
  `StatusUpdate` (DISABLED/SLOWED muddat bilan), `ContainModule` (garnizon),
  `SpecialPowerModule` (superqurol), `AutoHealUpdate`. Hammasi `RtsModules` orqali
  INI tagiga bog'lanadi.
- **`player.RtsPlayer`** (pul, upgrade'lar, qurol bonusi) va **`player.Upgrade`**.
- **`thing.RtsKinds`** — RTS lug'ati: `SELECTABLE`, `CAN_ATTACK`, `STRUCTURE`, `INFANTRY`,
  `VEHICLE`, `POWERED`.
- **`save.GameSnapshot`** — matnli serializatsiya, checksum bo'yicha aynan tiklanadi.
- **`client.AsciiRenderer`** — tumanni hisobga oluvchi matnli minimap.

### 3.9 Determinizm invariantlari (buzilmasin)

- `GameLogic` ichida devor-soati o'qish yo'q, `Math.random()` yo'q, tartibsiz iteratsiya yo'q.
- Obyektlar har doim yaratilish tartibida tiklanadi; id'lar monoton.
- Buyruqlar kadr chegarasida qo'llanadi, hech qachon o'rtada emas.
- Float holat `floatToIntBits` orqali xeshlanadi.
- **Trigonometriya `StrictMath` orqali.** `Math.sin`/`cos`/`atan2` faqat 1 ulp
  aniqlikda kafolatlanadi va platforma intrinsic'laridan foydalanishi mumkin —
  ikki peer oxirgi bitda farq qilsa, bu desync. `Math.sqrt`/`abs` aniq, ular mumkin.
- Bir kadrda yaratilgan obyekt o'sha kadrda tiklanmaydi — kadrning obyekt to'plami aniq.

---

## 4. `game` — Unity-uslub qatlam (12 fayl)

### 4.1 DukeGame — asosiy API

```java
var game = DukeGame.create("My RTS")
        .loadUnits(DukeGame.STARTER_UNITS)
        .map(70, 45);

var you = game.addPlayer("USA", Color.CYAN);
var foe = game.addPlayer("China", Color.RED);
game.enemies(you, foe).money(you, 1500);

game.spawn("Barracks", you, 100, 360);
game.spawn("Tank", foe, 550, 100);

game.start();   // oyna ochiladi, yopilguncha bloklaydi
```

Guruhlar bo'yicha:

- **sozlash:** `loadUnits`, `loadUnitsFile`, `map`, `mapFromText`, `addPlayer`, `enemies`, `allies`,
  `localPlayer`, `money`, `spawn`, `spawnNeutral`, `window`, `maxFps`, `subtitle`, `customModules`
- **callback'lar:** `onStart`, `onTick` (har kadr), `everySeconds`, `onPlayerDefeated`
- **skirmish:** `skirmish`, `selectSkirmish`, `getMapChoices`, `getFactionChoices`, `applyMapTerrain`
- **multiplayer:** `hostMultiplayer`, `joinMultiplayer`, `cancelHosting`, `isMultiplayer`, `supportsMultiplayer`
- **ishga tushirish:** `start`, `startEngineOnly`, `runHeadless`, `stop`
- **runtime:** `getSnapshot`, `getBuildOptions`, `postCommand`, `runOnSimThread`, `togglePause`,
  `setBanner`, `getLogic` (hamma narsaga ochqich)

`DukeGame.STARTER_UNITS` — tayyor INI (PowerPlant / Barracks / Rifleman / Tank), shuning uchun
birinchi o'yin uchun hech qanday fayl yozish shart emas.

### 4.2 `setUp()` tartibi — muhim

`engine.init()` subsystemlarni **reset qiladi**, shuning uchun tartib qat'iy:

1. `RtsLogic` / `RtsClient` / `RtsGameEngine` yaratiladi, multiplayer bo'lsa ulanadi
2. `engine.init()` ← **bu yerda reset bo'ladi**
3. custom modullar ro'yxatdan o'tadi (INI ularga murojaat qilishidan oldin)
4. INI yuklanadi → template'lar
5. relyef (`PathGrid`) o'rnatiladi
6. o'yinchilar qo'shiladi, viewer tanlanadi
7. `started = true` → shundan keyin `spawn()` darhol ishlaydi
8. scenario (spawn / pul / diplomatiya) qo'llanadi
9. callback'lar va defeat listener ulanadi
10. **skirmish assembler** tanlangan matchni quradi (relyef + neytrallar + har o'yinchining faction bazasi)
11. `setInGame(true)`, so'ng `onStart` callback'lari

### 4.3 Thread modeli

Yagona kesishma nuqta — `RtsClient` ning `volatile` immutable `WorldSnapshot` i:

```
UI thread (Swing EDT / jME)              Sim thread ("duke-sim")
    |                                            |
    |- postCommand(msg) --> ConcurrentLinkedQueue --| (kadr boshida drenaj)
    |- runOnSimThread(task) ------------------------|
    |                                            |
    \- getSnapshot() <-- volatile WorldSnapshot <---/ (har kadr yangilanadi)
```

`WorldSnapshot` / `UnitView` — immutable recordlar; tuman snapshot qurilishida qo'llanadi.
UI hech qachon `GameLogic` ni to'g'ridan-to'g'ri o'qimaydi.

### 4.4 RtsLogic — tayyor RTS mantiqi

`MoveTo` → `MoveUpdate.moveTo()` + qurolni to'xtatadi · `AttackObject` → `WeaponUpdate.attack()` ·
`StopMoving` → ikkalasini to'xtatadi · `QueueProduction` → `ProductionUpdate.queue()` ·
`SetRallyPoint` → rally nuqtasi.

Ikkita qat'iy qoida:

- **Egalik tekshiruvi** — buyruq faqat uni bergan o'yinchining birligiga ta'sir qiladi.
- **Build menyusi = shartnoma** — `Builds` ro'yxatida bo'lmagan birlikni navbatga qo'yib bo'lmaydi.

Mag'lubiyat qoidasi: birliklari bo'lgan va hammasini yo'qotgan o'yinchi mag'lub (annihilation) →
`onPlayerDefeated` + avtomatik **VICTORY / DEFEAT** bayrog'i.

### 4.5 Multiplayer

`MultiplayerSession` — **N o'yinchili** TCP lock-step. U faqat lobbi qo'l
berishuvi va `DukeGame` ga ulanishni egallaydi; lock-step qoidalarining o'zi
`core` dagi `LockstepGate` da.

**Lobbi protokoli** (xom soketda, lock-step xabarlari boshlanishidan oldin):

```
guest → host   DUKE-JOIN
host  → guest  DUKE-WELCOME <senIndeksing> <o'yinchiSoni> <scenario>
host  → guest  DUKE-START                (hamma yig'ilgach)
```

- Host = o'yinchi 1, mehmonlar kelish tartibida 2, 3, … `hostMultiplayer(port,
  playerCount, onGuestJoined)` har mehmon kelganda xabar beradi, ya'ni lobbi
  "3 dan 2 tasi kirdi" deb ko'rsata oladi.
- **Hamma yig'ilmaguncha hech kim boshlamaydi** (`DUKE-START`) — aks holda
  kelganlar hali ulanmagan o'yinchini kutib to'xtab turardi.
- Mehmonlar hostning map/faction tanlovini qo'llaydi → hamma bir xil matchni yig'adi.
- `FRAME_DELAY = 3` (30 Hz da 100 ms kirish kechikishi). Standart port **7777**.
- Multiplayerda pauza tabiiy ravishda hamma peerni to'xtatadi.

**Tuzoq (tuzatilgan):** qo'l berishuv `BufferedReader` bilan o'qilsa, u o'z
qatoridan **oshirib** o'qib qo'yishi va tashlab yuborilganda birinchi lock-step
xabarlarini olib ketishi mumkin. Endi qo'l berishuv baytma-bayt o'qiladi —
ulanish qo'l berishuvdan uzoq yashaydi, demak qo'l berishuv o'ziga tegishli
bo'lmagan biror baytga tegmasligi kerak.

`onPlayerLeft(...)` — o'yinchi chiqib ketganda (armiyasi yo'q qilinganda emas);
ulanish uzilsa "CONNECTION LOST" bayrog'i chiqadi.

### 4.6 Custom kod — UnitScript

Unity'ning MonoBehaviour naqshi, mavjud modul seami ustida:

```java
package game.scripts;

import uz.duke.game.script.UnitScript;

public class Berserker extends UnitScript {
    @Override
    public void onUpdate() {
        var enemy = findNearestEnemy(100000);
        if (enemy == null) return;
        if (distanceTo(enemy) > 8 && !isMoving()) {
            moveTo(enemy.getPosition().x(), enemy.getPosition().y());
        }
        if (!isAttacking()) attack(enemy);
    }
}
```

API: `onStart()` / `onUpdate()` (30 Hz) + yordamchilar `unit()`, `world()`, `position()`, `health()`,
`frame()`, `isMoving()`, `isAttacking()`, `distanceTo()`, `findNearestEnemy(range)`, `moveTo()`,
`attack()`, `stop()`, `money()`, `productionQueue()`, `trainUnit(name)`, `setRallyPoint()`.

`ScriptModule` adapter qiladi va **xatoni izolyatsiya qiladi**: skript exception tashlasa, u log
qilinadi va o'chiriladi — simulyatsiya davom etadi. INI tag'i: `Update = Script:<Nom> Tag`.

Determinizm shartnomasi skriptga ham tegishli: devor-soati yo'q, `Math.random()` yo'q, thread yo'q, UI yo'q.

---

## 5. `client3d` — 3D klient (49 fayl, ~17 100 qator)

jMonkeyEngine 3.7.0-stable ustida. `Duke3D.launch(game, visuals[, shell])` oynani ochadi va
yopilguncha bloklaydi.

**Ekran holat mashinasi:** `MENU → PLAYING ⇄ PAUSED`, plus `SETTINGS`. Simulyatsiya "Play"
bosilganda (yoki `Shell.none()` bo'lsa — darhol) boshlanadi.

- **Bosh menyu — o'yinniki, klientniki emas.** Ilgari klient uni o'zi qurardi va
  ikkita o'yinchi slot bo'lsa "Host LAN Game" taklif qilardi; bir kishilik
  dungeon'da esa ikkinchi slot skeletlar egasi bo'lib chiqdi. Bu engine
  strukturaviy fakt asosida mahsulot qarorini chiqarishi edi. Endi **mexanizm
  klientniki**, **mazmun o'yinniki** (`Shell`): qaysi bandlar, qanday nomlanadi,
  yoki umuman menyusiz. Klient faqat bandning ma'nosi borligini tekshiradi.
  `Shell.standard()` — avvalgi xulq, ya'ni mavjud demolar o'zgarmaydi.
  **Tizim masalalari klientda qoladi:** Settings, pauza menyusi, chiqish.
- **Menyular toshdan** (`StoneCraft` + `StoneMenu`) — bosh menyu, pauza menyusi va
  sozlamalar **bitta komponent**: ular bir xil narsa, faqat qatorlari boshqa.
  `StoneCraft` — chizish lug'ati (palitra, `slab` = yoritilgan plita + tepa qirra +
  soya, `glow` = mash'al hovuzi, `arrowhead`, `text`); qahramon paneli ham shundan
  chizilgan, shuning uchun menyu panel bilan bir xil toshdan. `StoneMenu.Row` —
  sealed: `Action` (`danger` bayrog'i bilan — qizil), `Choice` (yonma-yon kataklar),
  `Opens` (ochiladigan ro'yxat), `Level` (polzunok), `Words` (faqat o'qish uchun),
  `Buttons` (oyoqdagi juft tugma). Klaviatura ham, mishka ham: har qator o'z hit-box'i
  bilan, polzunokni **bosib sudrash** mumkin (bosilganda ushlaydi, qo'yib yuborilganda
  bo'shatadi — `drag`/`release`), ochiq ro'yxat kursor turgan qatorni tanlaydi.
  Kichik ekranda avval kichrayadi (`LEAST_SCALE 0.62`), undan keyin scroll qiladi.
  Shrift `Visuals.menuStyle(...)` orqali o'yinniki (dungeon — Cinzel).
- **Pauza menyusi** — `Esc` **har doim** menyuni ochadi (o'q nishonga olinayotgan
  bo'lsa avval o'sha bekor qilinadi) va simulyatsiyani to'xtatadi. To'xtatish
  **to'g'ridan-to'g'ri** qo'yiladi, `runOnSimThread` bilan emas: to'xtagan engine
  qadam tashlamaydi, ya'ni navbatga qo'yilgan "davom et" vazifasi hech qachon
  bajarilmasdi va o'yin qotib qolardi. Qatorlari: CHUQURLIK / daraja (o'yinning
  o'z status qatoridan o'qiladi), Resume, Settings, qizil "Abandon the run" →
  tasdiq ekrani. Menyu ochiq bo'lsa HUD (panel, minimap, holat satri) yo'qoladi.
- **Skirmish menyusi** — map'ni aylantirish + har o'yinchi uchun faction tanlash → `selectSkirmish()`.
  Faqat `getMapChoices()` bo'sh bo'lmasa va MP bo'lmasa ko'rinadi.
- **Settings** — Fullscreen, Size (monitor **haqiqiy** rejimlaridan, dropdown),
  Volume / Effects / Voice / Music polzunoklari, Track (musiqa — dropdown), va
  oyoqda **Save / Cancel** tosh uyalarda. Har o'zgarish **darhol** qo'llanadi
  (ovozni eshitmasdan tanlab bo'lmaydi), lekin `Cancel` hammasini ortga qaytaradi:
  `GameSettings` ikki qatlam — `saved` (fayl) + `draft` (hali tasdiqlanmagani).
  Fayl **foydalanuvchi papkasida**: `~/.duke-engine/settings.properties` (ilgari
  `java.util.prefs` = Windows registri edi; eski qiymatlar bir marta ko'chiriladi).
- **Drag-select** — LMB'ni bosib sudrash kvadrat chizadi (kontur, ichi bo'yalmaydi) va ichidagi
  **o'yinchining o'z** birliklarini tanlaydi; dushman hech qachon tanlanmaydi. Tanlov — klient
  holati (`Set<Integer>`), simulyatsiya undan bexabar; simga faqat buyruq ketadi.
  Kichik siljish **klik** deb hisoblanadi va eski bitta-birlik yo'liga tushadi — chegara
  bo'lmasa qo'l hech qachon aynan 0 piksel sudramagani uchun har bir klik kichkina bo'sh
  kvadratga aylanib, jimgina hech narsa tanlamay qo'yardi (`SelectionBox`).
- **Buyruq metkasi** — yerga buyruq berilganda klik nuqtasida halqa chiqadi va ~1.2s da
  so'nadi (harakat = yashil, hujum = qizil). Bir necha birlik tanlansa ham **bitta** metka:
  bu bitta qaror edi. Vaqt `timer.getTimeInSeconds()` dan — sof render tomonda
  (dul olovi bilan bir xil idioma), simulyatsiyaga aloqasiz (`OrderMarkers`).
- **Boshqaruv:** LMB tanlash yoki sudrab kvadrat (Shift — qo'shish), RMB buyruq (dushmanga = hujum, yerga = yurish,
  zavod tanlangan bo'lsa = rally nuqtasi), WASD / o'q tugmalar kamera, `Probel` kamerani
  qahramonga qaytaradi (menyu ochiq bo'lsa — tanlash), g'ildirak zoom, `H` to'xtatish,
  `P` pauza, `Esc` tanlovni bekor / pauza menyusi, `1`–`9` build menyusidan navbatga qo'yish.
- **Minimap** — o'ng pastda: relyef qatlami (map chegarasi + to'siq kataklari) + jonli nuqtalar
  (o'yinchi rangi bo'yicha, inshootlar kattaroq) + **viewport konturi**. Minimapga LMB = kamera
  sakrashi (birlik tanlashdan oldin tekshiriladi).
  Kamera yerga qiya qaraydi, ya'ni ko'radigan hududi to'rtburchak emas **trapetsiya**: ekranning
  4 burchagidan yerga nur tushiriladi va haqiqiy to'rtburchak `LineLoop` bilan chiziladi. Ichi
  bo'yalmaydi — minimapning butun vazifasi qayerda jang ketayotganini ko'rsatish, yarim shaffof
  to'rtburchak esa aynan o'sha joyni xiralashtirardi. Xarita chetidan chiqqan burchak minimap
  chegarasiga qirqiladi. Matematikasi `MinimapProjection` da (jME'siz, testlanadi).
- **Kamera o'yinchiniki** (`CameraFocus`) — o'yin boshlanganda va yangi run boshlanganda
  kamera o'yinchining **o'z birligiga** qo'yiladi (avval xarita markazida turardi va
  o'yinchi qahramonini qidirib topishi kerak edi). Bu **bir martalik so'rov, kuzatuv emas**:
  so'rov qo'yiladi, birinchi o'z birligi ko'ringan snapshot uni bajaradi va o'chiradi —
  shundan keyin kamera erkin. Kuzatib yuradigan kamera pan'ni tortib olardi, turgan
  qahramon atrofiga qarab chiqish esa RTS o'ynashning yarmi. So'rov dunyo hali paydo
  bo'lmaganda ham kutib turadi (o'yin snapshotdan bir-ikki kadr oldin boshlanadi).
  **`Probel` — kamerani qahramon ustiga qaytaradi:** xuddi shu bir martalik so'rov,
  ya'ni qo'ygandan keyin kamera yana o'yinchiniki (kuzatib yurmaydi).
- **Kamera xaritadan chiqib ketmaydi** (`CameraFocus.keepInside`) — qaraydigan nuqta
  minimap chizadigan **aynan o'sha to'rtburchak** ichida ushlanadi. Chegaradan tashqarida
  na yer, na mo'ljal, na minimapda belgi bor: pan tugmasini bir soniya bosib turgan
  o'yinchi adashib qolardi va qaytish yo'lini topolmasdi. Chegara har yangi qavatda
  minimap bilan birga yangilanadi; xarita kichrayса kamera ichkariga tortiladi.
  Xaritasini aytmagan o'yin uchun chegara yo'q (eski erkin kamera).
- **Dunyo almashsa sahna qayta quriladi** — relyef `TerrainScene` ga tegishli o'z tugunida
  yashaydi va **har qayta qurish avval uni bo'shatadi**, ya'ni sahna run'lar bo'ylab o'smaydi.
  Klient dunyo almashganini o'zi sezadi: `applyMapTerrain()` yangi `PathGrid` **instance** qo'yadi,
  klient esa sahna qurilgan grid'ni identity bo'yicha solishtiradi — simulyatsiya klientga hech
  narsa aytmaydi. Almashganda relyef, minimap foni qayta quriladi va kamera o'yinchining yangi
  birligiga o'tadi. (Birliklar avvaldan to'g'ri ishlardi — ular snapshot bilan paydo bo'lib
  yo'qoladi; qotib qolgani faqat bir marta qurilgan narsalar edi.)
- **Formatsiya harakati** — bir nechta birlik uchun MoveTo grid ofsetlariga bo'linadi
  (`cols = ceil(sqrt(n))`, oraliq 5 dunyo birligi) — deterministik, MP-xavfsiz.
- **Vizuallar** — `Visuals` Unity-uslub bog'lash:
  `.unit("Tank", u -> u.model("Models/tank.glb").scale(1.5f).idle("Idle").walk("Drive").attack("Fire"))`.
  glTF va Ogre modellari, `AnimComposer` va eski `AnimControl` animatsiyasi, pozitsion ovoz
  (o't ochish — ko'tarilish qirrasida; o'lim — evristika: yo'qolganda hp < 35 % = o'lim, tuman emas),
  sog'liq chiziqlari (`BillboardControl`), dul olovi. Modeli yo'q birliklar toza primitivlar bilan chiziladi.
- **Koordinatalar:** sim (x, y) → jME (x, 0, z); yo'nalish `fromAngles(0, -θ, 0)`, primitivlarning oldi = +X.

---

## 6. `studio` — Duke Studio IDE (17 fayl)

### 6.1 Loyiha modeli

`.duke` fayli = JSON hujjat (`StudioProject`).
**Loyiha — yagona haqiqat manbai; engine INI'si esa build artefakti.**

- **`FactionDef`** — nom, ko'rinadigan nom, rang, tavsif va **`startingUnits`** (o'yinchi start
  pozitsiyasida qanday baza bilan boshlaydi). Shu sababli faction istalgan map'da o'ynay oladi.
- **`MapDef`** — nom, o'lcham, to'siq kataklari, **start pozitsiyalari** (8 tagacha), neytral
  obyektlar. **Map'da armiya bo'lmaydi.**
- **`UnitDef`** — nom, faction (bo'sh = umumiy), sog'liq, narx, ko'rish masofasi, `capabilities`
  xaritasi, vizual maydonlar (model / animatsiya / ovoz), biriktirilgan skriptlar.
- **`PlayerDef`** — nom, rang, pul, jamoa (bir jamoa = ittifoqchi), faction.
- **`ScriptDef`** — nom + Java manba kodi.

`ensureIntegrity()` har yuklashda va har o'zgarishda chaqiriladi: null'larni tuzatadi, osilib qolgan
havolalarni tozalaydi va **eski formatni migratsiya qiladi** (bitta map + chizilgan armiyalar →
armiya markazlari start pozitsiyalariga, armiyalarning o'zi esa faction'ning boshlang'ich bazasiga).

### 6.2 Capability → engine modul jadvali

Studio'da birlikka "qobiliyat" qo'shish = INI modul bloki generatsiyasi (`GameFactory.toIni`):

| Studio capability | Generatsiya qilinadigan INI | Parametrlar |
|---|---|---|
| MOVE | `Update = MoveUpdate` | Speed, TurnRate |
| ATTACK | `Update = WeaponUpdate` | Damage, AttackRange, ReloadFrames, SplashRadius, DamageType |
| PRODUCE | `Update = ProductionUpdate` | Builds (bo'sh joy bilan ajratilgan nomlar) |
| POWER | `Update = PowerModule` | Produces, Consumes |
| EXPERIENCE | `Behavior = ExperienceModule` | ExperienceValue, ExperienceRequired, LevelDamageBonus, HealOnPromotion |
| AUTO_HEAL | `Update = AutoHealUpdate` | HealPerSecond |
| SUPPLY | `Behavior = SupplyModule` | Amount |
| HARVEST | `Update = HarvestUpdate` | LoadPerTrip, FramesPerTrip |
| (skriptlar) | `Update = Script:<Nom>` | — |

`KindOf` avtomatik hisoblanadi: STRUCTURE yoki INFANTRY + SELECTABLE, ATTACK bo'lsa CAN_ATTACK,
POWER bo'lsa POWERED.

### 6.3 UI

- **Chap:** faction → unit daraxti (+ "(shared units)" tuguni), `+ Faction` / `+ Unit` / `−`.
- **Markaz:** `Map` tab (map tanlagich + `+ Map`; relyef cho'tkasi 1–3 katak, start-pozitsiya asbobi
  slot tanlovi bilan, neytral birliklar; MMB-pan, g'ildirak zoom 1×–8×) · `Scripts` tab
  (ro'yxat + muharrir + Compile) · `Generated INI` tab (faqat o'qish — engine nimani ko'rishini ko'rsatadi).
- **O'ng:** CardLayout — `InspectorPanel` (birlik: faction, sog'liq, narx, qobiliyat checkbox'lari →
  parametr formasi, model/ovoz uchun "…" browse tugmalari, custom skript checkbox'lari) yoki
  `FactionPanel` (id / nom / rang / tavsif + **boshlang'ich baza jadvali**: unit / dx / dy).
- **Menyular:** File (New / Open / Save / Save As / Export game) · Edit (**Ctrl+Z / Ctrl+Y undo-redo**,
  **Ctrl+D duplicate unit**) · Game (Play 3D / Play 2D) · Project (Add faction, Players…, Add map,
  Map size…, **Import map (text/image)…**, Game menu…) · Help.
- **Undo/redo** — butun loyihaning JSON snapshot'lari (limit 100), har o'zgarishda push,
  yangi o'zgarishda redo tozalanadi.

### 6.4 Play yo'li

`GameFactory.toGame(project, compiledScripts)`:

1. `ensureIntegrity()`
2. INI generatsiya → `loadUnits`
3. skriptlarni `customModules` orqali ro'yxatdan o'tkazish
4. o'yinchilar + diplomatiya (jamoa bo'yicha) + pul
5. `skirmish(maps, factions, assembler)` — match play vaqtida tanlanadi

Play tugmasi **avval skriptlarni kompilyatsiya qiladi** (`ScriptCompiler`, `javax.tools`,
classpath = `java.class.path`; `extends UnitScript` va klass nomi tekshiriladi) va xato bo'lsa
ishga tushirishni **rad etadi**. O'yin `duke-play` nomli thread'da, shu jarayon ichida ochiladi.

### 6.5 Map va asset importi

- **Map import** (`MapImporter`): `.txt` / `.map` → core `MapLoader`; **rasm** (`.png`, `.jpg`…) →
  1 piksel = 1 katak, yorqinlik < 0.4 = to'siq; tomoni 200 katakdan katta bo'lsa avtomatik
  kichraytiriladi. Ya'ni map'ni istalgan rasm muharririda chizsa bo'ladi.
- **Asset import** (`AssetImporter`): loyiha asset papkasi = `.duke` fayli yonidagi `<nom>_assets/`
  (saqlanmagan loyiha avval saqlashni so'raydi). Fayl `assets/<kategoriya>/` ga ko'chiriladi va
  engine yo'li (`Models/x.glb`) qaytariladi. `.gltf` / `.mesh.xml` / `.obj` uchun yondosh fayllar ham
  ko'chiriladi (.bin / .material / .skeleton.xml / .mtl / rasmlar). **`.glb` va `.j3o` tavsiya
  etiladi** — ular o'zi-yetarli.

### 6.6 Export yo'li

`GameExporter.export(project, targetDir, engineRoot, assetsRoot)` mustaqil Gradle loyihasini yozadi:

```
<Nom>/
  settings.gradle.kts
  build.gradle.kts                   <- jME Maven'dan, toolchain 25, fatJar + packageApp tasklari
  gradlew, gradlew.bat, gradle/wrapper/
  README.md
  libs/                              <- core, game, client3d jar'lari
  src/main/java/game/Main.java       <- generatsiya qilingan
  src/main/java/game/scripts/*.java  <- custom skriptlar (manba holida, runtime kompilyatsiya YO'Q)
  src/main/resources/                <- assetlar (classpath'da -> locator kerak emas)
```

Chiqarilgan loyihada:

- `gradlew run` — o'ynash
- `gradlew fatJar` → `build/fat/game-all.jar` (bitta yugurtiriladigan jar)
- `gradlew packageApp` → `build/package/<Nom>/<Nom>.exe` (o'z Java runtime'i bilan; maqsad
  mashinada JDK kerak emas). jpackage faqat o'zi ishlayotgan OS uchun quradi — .exe / Linux / macOS
  uchun har OS'da alohida yugurtiring.
- `gradlew distZip` — launch skriptlari bilan portativ zip

---

## 7. Uchidan-uchiga oqim

```
Studio loyihasi (.duke JSON)
   |  GameFactory.toIni()      -> engine INI matni
   |  GameFactory.toVisuals()  -> model / ovoz bog'lashlari
   |  ScriptCompiler           -> kompilyatsiya qilingan UnitScript klasslari
   v
DukeGame (fasad)
   |  setUp(): init -> modullar -> INI -> relyef -> o'yinchilar -> scenario -> skirmish assembler
   v
GameEngine.execute()   -- 30 Hz mantiq / <=45 fps render
   |
   |--> GameLogic (deterministik dunyo) --> RtsClient --> WorldSnapshot (volatile)
   |                                                            |
   \--<-- buyruq navbati <-- postCommand() <---------- UI (jME 3D yoki Swing 2D)
```

Multiplayerda `postCommand` → `MultiplayerSession.issueLocal()` → `frame + 3` ga jo'natiladi →
ikkala peer aynan bir kadrda qo'llaydi.

---

## 8. Nima ishlaydi (tasdiqlangan)

- **657 test yashil** (core 133, rts 110, generals 5, game 28, client3d 130, studio 8, dungeon 243) — 0 failure / 0 error.
- **Obyektlar fizik jism** — `GeometryTest` shakl matematikasini (burilgan box,
  burchaklar, teginish) qulflaydi; `CollisionTest` birlikning binoni aylanib
  o'tishini, birliklarning ustma-ust tushmasligini, ichkarida paydo bo'lgan
  birlikning chiqib keta olishini va geometriyasiz kontentning avvalgidek
  ishlashini tekshiradi; `SolidWorldTest` ishlab chiqarish chiqishini va
  yuzadan-yuzaga qurol masofasini tekshiradi.
- **Lahzalar kanali** — `WorldEventTest`: o'lim obyekt yo'qolgandan keyin ham
  chizishga yetarli ma'lumot bilan e'lon qilinadi, `DieModule` jasadsiz dunyoda
  ishlaydi, o'ldirilmagan (shunchaki olib tashlangan) obyekt o'lim hisoblanmaydi,
  drenaj har lahzani bir marta beradi, navbat chegaralangan. `WeaponFiredTest`:
  60 kadr / 10 kadrlik reload = **6 ta hodisa**, 60 ta emas. `EventVisibilityTest`:
  tuman ortidagi o'lim snapshot'ga tushmaydi.
- **Yo'l qidiruvi binolarni biladi** — `StaticObstacleTest`: bo'sh koridorda
  yo'l to'g'ri, bino qo'yilsa marshrut uni aylanib o'tadi (bironta waypoint band
  katakda emas), **harakatlanuvchi** texnika esa map'ni qayta yozmaydi, bino
  buzilganda yer ochiladi, va buzilish relyefdagi to'siqqa tegmaydi. Yo'lda
  ketayotgan birlik ustiga bino qurilsa — marshrutni qayta tuzib baribir yetib
  boradi; yo'l butunlay yopilsa — to'xtaydi. Oddiy birlik kelib-ketishi
  navigatsiya versiyasini o'zgartirmaydi (qayta yo'l tuzish bo'roni bo'lmaydi).
- **Engine bo'linishi tasdiqlangan** — `core` `rts` ni umuman ko'rmaydi (Gradle bog'liqligi
  bir tomonlama), va bo'linishdan oldingi hamma test hali ham o'tadi.
- **To'liq stack uchidan-uchiga** — sandbox: iqtisod → ishlab chiqarish → jang → g'alaba,
  matnli minimap bilan chiziladi.
- **Multiplayer jonli tekshirilgan** — bitta mashinada ikkita oyna, Host → Join 127.0.0.1,
  ikkala tomon sinxron o'ynadi. `MultiplayerSyncTest`: haqiqiy localhost TCP orqali ikkita DukeGame,
  300 ta o'zaro qadam, checksum'lar bit-aniqlikda bir xil.
- **N o'yinchi va uzilish** — `ThreePlayerSyncTest`: uchta `DukeGame` haqiqiy TCP
  orqali; 2- va 3-mehmon bir-biri bilan **umuman ulanmagan**, lekin ikkalasining
  buyrug'i ham har uchala dunyoga bir xil yetadi. `LockstepGateTest`: uch peer
  bit-aniq bir xil; biri chiqib ketsa qolganlar **davom etadi** (avval abadiy
  qotardi) va uni **aynan bir kadrda** kutishdan to'xtaydi; host'ni yo'qotgan
  mehmon esa qadam tashlamaydi. `NetworkTransportTest`: host relay qiladi, va
  uzilish xabari o'sha peerning oxirgi paketidan **keyin** keladi.
- **Engine ustida o'yin yozilmoqda** — `dungeon` moduli (Duke Dungeon).
  Muhimi: **engine'ga bironta narsa qo'shilmadi** — o'yin faqat mavjud API'ni
  ishlatadi. `DungeonTest` qo'lda chizilgan xonada headless tekshiradi: qahramon
  buyurilgan joyga boradi, skeletni o'ldiradi, skeletlar javob qaytaradi va
  devorlar qattiq (400 kadr davomida hech qachon tosh ustida turmaydi).
- **Dungeon seed'dan generatsiya qilinadi** — `DungeonGenerator`: 5–8 xona,
  koridorlar bilan bog'langan, qahramon boshlang'ich xonada, skeletlar
  qolganlarida. Ulanish **konstruksiya orqali kafolatlangan**: har xona o'zidan
  oldingisiga koridor bilan ulanadi, ya'ni xonalar bitta qamrab oluvchi daraxt —
  izolyatsiya qilingan xona chiqishi mumkin emas, "ulanganmi?" degan tekshiruv
  omad bilan o'tib keta olmaydi. `DungeonGeneratorTest`: bir seed → aynan bir
  dungeon, har xil seed → har xil, va **0..200 seed uchun** qahramon
  katagidan pol bo'ylab flood-fill har bir xona markazi va har bir skeletga
  yetib boradi (engine'ning o'z `findPath` i ham tasdiqlaydi).
- **Run loop — o'lsang boshdan** — `DungeonRun`: `RUNNING` → qahramon HP tugadi
  yoki dunyodan ketdi → `DEAD` ("You died") → ~2 soniyadan keyin yangi seed,
  yangi dungeon, to'liq HP bilan yangi run. Progress saqlanmaydi (save/load yo'q).
  Dunyo o'rnida qayta quriladi — engine'ning save/load choki (`clearWorld` +
  `applyMapTerrain` + respawn); o'yinchilar tegilmaydi, shuning uchun egalik
  indekslari amal qiladi. `DungeonRunTest` holat almashinuvini va yangi run
  to'liq HP bilan boshlanishini qulflaydi.
- **Combat ikki yo'l bilan** — `DungeonCombatTest` (maxsus arenada, lekin o'yinning
  o'z creature va xulqi bilan): uzoqdagi skeletga klik qahramonni yuborib o'ldiradi,
  buyruqsiz yaqinlashganda avto-ataka baribir ishlaydi. Klik-atakani o'yin o'zi
  to'ldiradi (`HeroBrain`) — engine quroli **ataylab** nishonga yurmaydi ("bu
  locomotor ishi"), klient esa RMB'da faqat `AttackObject` yuboradi, ya'ni RTS'da
  to'g'ri bo'lgan qoida dungeon'da qahramonni joyida qotirardi. Harakat buyruq
  quvuridan emas, locomotorga to'g'ridan-to'g'ri beriladi — `MoveTo` **buyrug'i**
  qurol nishonini tozalar va o'zi yuborgan atakani bekor qilardi.
- **Yurish buyrug'i yo'lda uchragan dushmandan ustun** — dushman yonidan olib
  o'tmoqchi bo'lganda qahramon to'xtab jang boshlab yuborardi. `HeroBrain` endi
  nishonni **paydo bo'lgan lahzada bir marta** hukm qiladi: avto-tanlash faqat
  qurol masofasidagi narsani ola oladi, demak undan uzoqdagi nishon faqat
  buyurilgan bo'lishi mumkin. Buyurilganini quvadi; yo'lda uchraganini
  **to'xtamasdan** uradi (qurol baribir otadi), va tik turgan bo'lsagina unga
  buriladi. Ikkita test qulflaydi: yonidan o'tib manzilga yetishi, va yetib
  borgach orqasidan ergashganini o'ldirishi.
  - Ikki tuzoq shu yerda: (1) har kadr qayta hukm qilish — o'tib ketilgan dushman
    bir lahzadan keyin masofadan chiqadi va "buyurilgan"ga aylanadi, qahramon
    orqasiga qaytadi; (2) chegara sifatida `CloseDistance` (4) ni olish — qurol
    masofasi (`AttackRange` 8) undan kattaroq, shuning uchun **har bir**
    avto-tanlangan nishon "buyurilgan" bo'lib o'qilardi. Chegara qahramonning
    o'z `ThingTemplate` idan o'qiladi, o'yinda takrorlanmaydi.
- **O'qlar haqiqiy** — kamondan chiqadi, masofani bosib o'tadi, zarar **yetib
  kelganda** tushadi. 260 tezlikda to'liq masofada ~0.35 sekund havoda turadi.
  - **Nishonni quvadi**, oldini olmaydi: yetaklab otilgan o'q nishon burilishi
    bilan tegmay qolardi va qahramonning masofasi tavsiyaga aylanardi. Quvgani
    uchun **tegmay qolish yo'q** — parvoz vaqt turadi, xolos.
  - O'q **tanasiz va geometriyasiz**: tanasi yo'qligi uchun uni hech kim nishon
    qilib ololmaydi (`acquireTarget` tanasi borlarni izlaydi), geometriyasi
    yo'qligi uchun u dunyodan **o'tib ketadi**, monstrlarni turtmaydi. Ikkalasi
    ham faylda **yo'qlik** bilan ifodalangan, shuning uchun test bilan yozib
    qo'yilgan.
  - **Zarar o'q bilan yuradi**, yetib kelganda qayta hisoblanmaydi: qurol qo'yib
    yuborganida raqam yakuniy edi, va o'sha 0.35 sekundda otuvchi daraja olishi
    ham, o'lishi ham mumkin.
  - **O'ldirsa XP ni o'q beradi** — qurol hech kimni hisobga ololmaydi, chunki u
    qo'yib yuborganda nishon tirik edi. Bu bo'lmasa levelling kamonchilik
    kelgan kuni jimgina to'xtardi (test bor).
  - **O'q kamondan chiqadi, ko'kragidan emas** — `ArrowMuzzleOffset` uni
    qahramon **qaragan tomonga** siljitadi (u otish uchun buriladi, ya'ni yo'nalishi
    otish chizig'i). Nishondan uzoqroqqa siljimaydi: yonginasidagi narsaga otilgan
    o'q uning **orqasida** paydo bo'lib, qaytib kelishi kerak bo'lmasin.
    Balandlik esa `Height` — simulyatsiya tekis, ya'ni bu pozitsiya emas, faqat
    o'q chiziladigan chiziq.
  - **O'q modeli qahramonning o'z faylida bor edi** — kamondagi o'q, bor-yo'g'i
    **80 uchburchak**. `Visuals.modelPart` fayldan bitta nomlangan meshni sug'urib
    oladi (kitlar propni personaj bilan birga beradi), egasining transformini
    tashlab, o'z boshlanishiga qo'yadi — aks holda u ko'rinmas kamonchi qo'lida
    uchib yurardi.
  - **Tuzoq: kit meshlarining nomlari almashib ketgan.** `Eyes` deb atalgani —
    0.75 uzun, 0.04 yo'g'on, ya'ni **o'q**; `Arrow` deb atalgani — kiyim;
    `Eyelashes` — butun tana. Ya'ni INI'da "xato"dek ko'rinadigan nom turibdi va
    uni "tuzatish" — buzish. Test nomga ishonmaydi, **o'lchaydi**: uzun, ingichka
    va arzon bo'lishi shart.
  - `Map.of` 10 juftlikda tugaydi, INI bloklari 11 taga yetdi → `Map.ofEntries`.
  - **`StuckDiagnosisTest` soxta signal berdi**: u hamma obyektni sanardi, o'q
    esa devor ichidan **ataylab** o'tadi. Endi u faqat **yuradigan** birliklarni
    qaraydi — pathfinder yordam berishi kerak bo'lganlarni.
- **Otish va javob** — kamonchi qahramon monstrlarni tinch o'ldirib turmasligi uchun.
  - Qahramon `VisionRange` **70**, `AttackRange` **60**; monstrlarning sezish
    radiusi tushdi (eng kattasi **55**). Ya'ni u ularni **sezilmasdan turib**
    otishi mumkin: jangni u boshlaydi. (Sonlar keyin 0.65 ga qayta o'lchandi —
    pastdagi "Xonalar kattaroq" bandiga qarang; muhimi nisbat, o'sha saqlangan.)
  - **Zarba olgan monster otgan odamga boradi** — masofasiga qaramay
    (`MonsterBrain`). Unga kim otganini aytish shart emas: zindonda bitta
    qahramon bor, demak joni kamaysa — o'sha qilgan. Joni **ko'payishi** zarba
    hisoblanmaydi, chunki Revenant o'zini davolaydi (test bor).
  - Shu bilan otish tekin bo'lmay qoladi: birinchi o'q jangni **boshlaydi**,
    undan qochirmaydi.
  - Ikkita test asosi shundan o'zgardi — endi ular ikki qo'zg'atuvchini
    (sezish va zarba) **ajratib** tekshiradi, aks holda biri ikkinchisini yopardi.
- **O'lim animatsiyasi** — jasad darrov g'oyib bo'lmaydi, klipini o'ynab, keyin
  olib ketiladi. **Snapshotdan chiqib ketish emas, o'lim hodisasi** bilan
  boshqariladi: tuman tufayli burchakni aylangan monster ham snapshotdan chiqadi,
  va har safar jasad tashlab ketish umuman jasadsizdan yomonroq bo'lardi.
  Jon chizig'i va tanlov halqasi darhol olinadi — tirik narsaning belgilari.
- **Qahramon endi kamonchi** — `AttackRange` 8 → **60**, `CloseDistance` 4 → **48**,
  `ReloadFrames` 15 → 24. Monstrlar 8–14 masofada uradi, ya'ni u zindonni ko'p
  barobar ortiqcha masofadan uradi: uzoqdan yutadi, yoniga kelishsa yutqazadi.
  `AttackRange` `VisionRange` (70) ichida qoladi — aks holda tuman ko'rsatmagan
  narsaga o'q otardi, chunki qurol tumandan bexabar.
  - `CloseDistance` endi **faqat qahramonniki** (monstrlarning o'z qiymatlari
    bor). Eski test uni hammaga umumiy deb hisoblardi — toraytirildi.
  - Q "zarba" o'rniga **og'ir o'q** (Range 40 → 130), W esa ochilish emas,
    **panika tugmasi** bo'ldi — yaqinlashib qolganlarga.
  - `HeroProgressTest` yaqin masofadagi jangda qahramonni o'lchardi; endi bu u
    **yutqazadigan** jang, shuning uchun test unga o'lchov tugaguncha yetadigan
    jon beradi — o'lchanayotgan narsa uning **zarari**, yutishi emas.
- **Ikki vizual tuzoq** — ikkalasi ham modellar kelgach paydo bo'ldi:
  - **Root motion.** Yugurish klipi skeletni 3 birlik oldinga olib ketardi
    (masshtabda ~20) — model o'z tugunidan chiqib ketib, yashil halqa va jon
    chizig'i orqada qolardi. Bu animatsiya sozlamasidek **umuman ko'rinmaydi**.
    Simulyatsiya pozitsiyani o'zi boshqaradi, shuning uchun ildiz suyakning
    gorizontal siljishi olib tashlanadi, vertikali (qadam ko'tarilishi) qoladi.
    Hozirgi to'plam klipni joyida chizadi, ya'ni olib tashlaydigan narsa yo'q —
    lekin qoida to'plamdan qat'i nazar bir xil, shuning uchun test qoladi.
  - **Jon chizig'i model ichida qolardi** — balandligi qat'iy 4.5 edi, kapsulaga
    mos, qahramon esa ~12, boss ~22. Endi tananing haqiqiy chegarasidan
    o'lchanadi va `depthTest` o'chirilgan holda **doim ustidan** chiziladi: jon
    chizig'i dunyodagi narsa emas, ko'rsatkich.
  - **Boshqa skeletda, va bu muammo emas.** Qahramon KayKit `Rig_Medium` da,
    monstrlar UE mannequin'da. Har jonzot **o'z rigiga** qurilgan kutubxonadan
    animatsiya oladi, ya'ni ikki to'plam yonma-yon yashaydi. Test buni qulflaydi:
    monstrlar kutubxonasi qahramonning oyog'ini **qimirlatmaydi**. (Ilgari u
    umuman 0 ta klip berardi — eski rigda bitta ham umumiy suyak nomi yo'q edi;
    KayKit rigida bir nechtasi mos keladi, klip ko'chadi, lekin hech nimani
    harakatga keltirmaydi. Aynan shu — "klip bor, personaj qimirlamaydi" — eng
    yashirin nosozlik, shuning uchun test endi harakatni o'lchaydi.)
  - **Animatsiya — kutubxona, fayl-per-harakat emas.** Ilgari aksincha edi:
    animatsiya saytlari ishni har harakat uchun alohida fayl qilib beradi va har
    faylning ichidagi klip bir xil nom bilan keladi (`mixamo.com`), ya'ni qaysi
    biri yugurish ekanini **fayl** aytardi. Personaj **to'plami** teskari: bir
    nechta kutubxona, har birida o'nlab klip, nomlari ma'noli. Shuning uchun
    `DungeonHero` blokida endi `AnimationsFrom` (takrorlanadi) + `Idle`/`Walk`/
    `Attack`/`Hurt`/`Death` klip nomlari — monstrlar bilan bir xil shakl.
  - **Qahramon qurol ushlaydi.** Personaj to'plamlari qurolni tanadan **ayri**
    beradi va rigda uni osish uchun suyak qoldiradi (`handslot.l`/`handslot.r`).
    Bitta ranger + qurol javoni = hamma qurollangan ranger; ranger-kamon-bilan
    esa ulardan bittasi. `Visuals.holds(fayl, suyak, masshtab)` → jME'ning
    `SkinningControl.getAttachmentsNode` i: kamonni **klip harakatlantiradigan
    qo'lning o'zi** olib yuradi, ya'ni har kadr sinxronlab turadigan narsa yo'q.
    Burilish sozlamasi yo'q va bo'lmasligi kerak — suyak aynan shuning uchun bor.
    Materialini qayta qurish esa SHART: qurol ham PBR bilan keladi, ya'ni
    kiyintirilmagan kamon — qora kamon.
  - **Tuzoq — animatsiya faylida `skins` yo'q.** "Without skin" fayl mesh
    olib yurmaydi, ya'ni skin yo'q, ya'ni **skelet ham yo'q** — va o'sha suyaklar
    oddiy `Node` bo'lib keladi. Faqat `Joint` ga qaraydigan qayta ulash bunday
    fayllarda **hech nima topmaydi** va buni aytmaydi ham: klip ko'chadi, ichida
    trek bo'lmaydi, personaj qimirlamaydi. Endi nom `Joint` dan ham, `Spatial`
    dan ham olinadi.
  - **FBX ishlamadi, GLB ishladi.** jME FBX'ni `Bind poses don't match` bilan
    rad etdi; `fbx2gltf` (npm) bilan GLB ga o'girilgach — 1450 ms da yuklandi,
    21k uchburchak, 70 suyak, teksturalar ichida. Blender kerak bo'lmadi.
  - `Facing` endi INI'da (qahramonda ham, monstrlarda ham) — model burilishini
    tuzatish uchun qayta kompilyatsiya kerak emas.
- **Monstrlar model bilan, harakatlanadi** — Quaternius Bestiary (2 model) +
  Universal Animation Library (CC0, 43 klip).
  - **Ikkalasi bir xil skeletda.** Model kiti va animatsiya kutubxonasi alohida
    sotiladi va standart gumanoid rigda uchrashadi: monsterning 55 suyagining
    **hammasi** kutubxonada bor (kutubxonadagi ortiqcha 10 tasi — jimjiloq va
    oyoq uchi — tashlanadi). Shuning uchun bitta kutubxona butun bestiariyni
    harakatga keltiradi va yangi monster uchun animatsiya ishi umuman yo'q.
  - **Klipni shundoq berib bo'lmaydi — bu jimgina ishlamaydigan tuzoq.**
    `AnimClip` treklari maqsad suyakka **to'g'ridan-to'g'ri havola** saqlaydi.
    Kutubxonadan olingan klipni monsterga qo'shsangiz, u monsterni emas,
    **kutubxonaning ko'rinmas skeletini** harakatga keltiradi: klip bor, o'ynaydi,
    monster qimirlamaydi, xato ham chiqmaydi. `AnimationLibrary.copy` har trekni
    nom bo'yicha qayta quradi. Ikkita test qulflaydi — biri qayta ulash ishlashini,
    ikkinchisi **sodda ko'chirish ishlamasligini** (u ishlab ketsa, qayta ulash
    keraksiz bo'lgan bo'lardi).
  - **2 model, 6 tur.** Bepul kitda faqat Imp va Puglin bor (saytdagi 7 tasi
    pullik versiyada). Har modelning **3 ta teri varianti** bor, shuning uchun tur
    = model + teri + bo'y + rang: `noTwoKindsLookAlike` ikki turning bir xil
    ko'rinmasligini qulflaydi.
  - **Uch narsa ko'z bilan tuzatildi.** Model burilishi: klient birlikning local
    +X ini oldinga qaratadi, bu kit esa +Z ga qaragan — chorak burilish xato
    bo'lsa monster yonlamasiga, yarim burilish xato bo'lsa **orqasi bilan sirg'alib**
    keladi, va ikkalasi ham pathfinder buzilgandek ko'rinadi. Animatsiya tanlovi:
    snapshotdagi `attacking` "nishoni bor" degani, "urmoqda" emas — monster
    qahramonni sezgan zahoti true bo'ladi, ya'ni butun quvish davomida urish
    animatsiyasi o'ynardi; endi **yurish ustun**. Va butun bestiariy melee
    bo'ldi: kitda otadigan jonzot yo'q, shuning uchun `Archer` → `Stalker`
    (uzoqdan sezadi, yaqinlashib uradi). Masofadan urish **mexanizmi** saqlanadi
    va testda o'z monsteri bilan qulflanadi.
  - **Material almashtiriladi — monstrlar avval QOP-QORA chiqdi.** jME glTF dan
    **PBR** material yasaydi, PBR esa ambient yorug'likni env-mapdan (light probe)
    oladi; bizda probe yo'q, ya'ni model yoritilmaydi. Ekranda bu "tekstura
    yuklanmadi" bo'lib ko'rinadi, aslida esa yorug'lik masalasi. Ustiga yuklovchi
    `BaseColorMap` ni umuman bog'lamaydi va `UseVertexColor` ni yoqadi. Material
    plitkalardagidek `Lighting` ga o'tkazildi (atlas `DiffuseMap`) — unda
    `NumberOfBones` bor, ya'ni skinning saqlanadi. Test terilarning qora emasligini
    qulflaydi, shunda keyingi safar qora monster **faqat** yorug'lik bo'ladi.
  - `Tint` `Colour` dan alohida: `Colour` — minimap nuqtasi va model topilmasa
    tushadigan shakl rangi; `Tint` esa terining ustidan ko'paytiriladi, ya'ni
    bitta teridan ikki xil monster chiqadi, minimapdagi o'qish esa buzilmaydi.
  - **Tuzoq:** bu fayllar katta (31 MB) va 2048² terilar bilan keladi. Testlarda
    har test o'z asset managerini yasasa, 512 MB heap **yetmaydi va JVM o'ladi** —
    bitta manager baham ko'riladi, o'yindagidek.
- **Zamin modulli to'plamdan quriladi** — Kenney Modular Dungeon Kit (CC0),
  `template-floor` / `template-wall` / `template-wall-corner`.
  - **Devor — katak emas, chegara.** To'plamning devori plitka **chekkasida**
    turadigan bo'lak. Shuning uchun u tosh katakning markaziga emas, ochiq katak
    bilan tosh orasidagi **chiziqqa** qo'yiladi — ya'ni pathfinder qahramonni
    to'xtatadigan aynan o'sha chiziqqa. Ko'rinadigan devor bilan to'qnashuv
    devori bir xil bo'ladi (eski quti usulida ular yarim katak farq qilardi).
  - **O'lcham tasodifan silliq:** plitka 4 model birligi, katak 10 dunyo birligi
    → masshtab **2.5**, hech qanday "taxminan" yo'q. `DungeonTilesTest` buni
    ikki tomondan qulflaydi: modelning haqiqiy o'lchami va INI'dagi raqam.
  - **Ikki burchak bo'lagi devor uchrashgan joydagi kvadrat o'yiqni to'ldiradi** —
    aks holda xona ichidan devor teshigi ko'rinadi.
  - **Tuman tekin mos tushdi:** har ochiq katak o'z `Node`i, ya'ni ko'rilmagan
    katak butunlay chizilmaydi (zamin tekisligi ham yo'q — orqa fon qora),
    ochilgani xira materialda. Kadr uchun katak boshiga bitta chaqiruv.
  - **Chizish soni kamaydi:** haqiqiy qavatlarda eng yomoni 866 bo'lak
    (527 pol + 306 devor + 33 burchak) — eskisi 1800 tuman qopqog'i + toshlar edi.
  - **Tuzoq:** jME'ning glTF yuklovchisi **PBR** material yasaydi, PBR esa ambient
    yorug'likni env-map'dan oladi — klientda `LightProbe` yo'q, natijada plitkalar
    deyarli qop-qora chiqadi. Material `Lighting.j3md` ga qayta qurildi (atlas
    `DiffuseMap` sifatida). Kenney uslubi baribir tekis, ya'ni bu ham to'g'ri,
    ham chizilgan narsaga mos.
  - **To'plam so'ramagan o'yin avvalgidek** — quti va zamin tekisligi. `studio`,
    sandbox va RTS ko'rinishi o'zgarmagan.
  - Yo'llar `dungeon.ini` da (`DungeonTiles`), `MonsterKind` rangi kabi: to'plamni
    almashtirish — tahrir, qayta kompilyatsiya emas. `Floor` nomlanmasa — qutilar.
- **Kashfiyot tumani — xarita qora, qahramon uni ochib boradi.** Uch holat:
  ko'rilmagan (qora — na yer, na devor, na dushman), ochilgan (devorlar ko'rinadi,
  dushmanlar yo'q), hozir ko'rinayotgan (hammasi). Ikki qatlamdan yig'ilgan:
  - **Dushmanlar — engine'niki, tekin.** `GameLogic.canSee` allaqachon jonli
    ko'rish bo'yicha filtrlaydi va **xotirasi yo'q** — ya'ni dushman radiusdan
    chiqsa snapshotdan butunlay yo'qoladi, oxirgi joyida "arvoh" qolmaydi. Kerak
    bo'lgan yagona narsa `creatures.ini` dagi raqam edi: `VisionRange` 600 →
    **80**. 600 — 500x360 xaritaning uzoq burchagiga yetadi, ya'ni tuman butun
    o'yinni o'zidan yashirayotgan edi.
  - **Yer va devorlar — klientniki, yangi.** `core` "ochilgan yer" tushunchasini
    bilmaydi (RTS'da relyef ko'rinadi, faqat birliklar yashirin). `Discovery`
    (client3d) katak bo'yicha ikkita `BitSet` yuritadi: `explored` (hech qachon
    tozalanmaydi = xotira) va `visible` (har kadr qayta yoziladi = ko'zlar).
    `TerrainScene` butunlay qorong'i qolgan bo'laklarni rasmdan chiqaradi,
    qorong'ilikning o'zini relyef materiali `FogMap` teksturasidan o'qiydi,
    minimap esa har katakni holat bo'yicha bo'yaydi.
  - **Radius bitta manbadan.** `Visuals.discoveredBy("Hero")` — masofa emas,
    **template nomi**: radius o'sha template'ning `VisionRange`i, ya'ni aynan
    engine tumani ishlatadigan raqam. Shuning uchun yer ochilishi va dushman
    ko'rinishi ajralib qola olmaydi.
  - **Simulyatsiyaga ta'sir qilmasligi tuzilmaviy.** `canSee` va `getVisionRange`
    butun repoda faqat `RtsClient` (snapshot) va `AsciiRenderer` dan chaqiriladi —
    simulyatsiyada bitta ham chaqiruv yo'q. `Discovery` esa umuman `client3d` da,
    snapshot ustida. `DungeonFogTest` buni raqam bilan qulflaydi: bir xil dunyo,
    `VisionRange` 600 va 80 — checksumlar bit-aniq bir xil.
  - Tuman `refreshWorldIfChanged` da nolga qaytadi, ya'ni yangi run ham, yangi
    chuqurlik ham qop-qora boshlanadi (`applyMapTerrain` grid nusxasini
    almashtiradi — klientга alohida signal kerak emas).
  - Tumanni **so'ramagan o'yin hech narsa to'lamaydi**: tekstura qurilmaydi,
    relyef odatdagi `Lighting.j3md` bilan chiziladi, sikl ishlamaydi. `studio` va
    oddiy RTS xulqi o'zgarmagan.
- **Uchta qahramon, har birida 4 ta skill (Q W E R).** Kamonchi (`Hero`),
  ritsar (`Knight`) va sehrgar (`Mage`) — uchalasi ham **faqat INI**, `Mage`
  qo'shilganda `dungeon` ga bitta ham yangi hero klassi yozilmadi.
  - **Effekt turlari (kodda, `SkillEffect`):** `STRIKE` (bitta dushmanga),
    `AREA_DAMAGE` (o'zi atrofida), `AREA_AT_SPOT` (tanlangan nuqtaga),
    `SKILLSHOT` (yo'nalish bo'ylab uchadi), `DASH` (yurib o'tadi), `EMPOWER`,
    `GUARD`, `BLINK` (teleport), `METEOR` (kechikkan maydon zarari).
  - **Yangi shakl = bitta konstanta + `SkillBook` da bitta tarmoq. Yangi
    SKILL = na u, na bu** — faqat INI bloki. Mana shuning uchun sehrgar
    "Fireball / Frost Nova / Blink / Meteor" ni oldi va ikkitasi (Q, W)
    umuman yangi kod talab qilmadi:
    - **Fireball** = `SKILLSHOT` + `Radius`. Ilgari skillshot uchun `Radius`
      hech nimani anglatmasdi; endi u **qo'ngan joydagi portlash**, ya'ni
      oddiy o'q — bu effekt, raqami yozilmagani.
    - **Frost Nova** = `AREA_DAMAGE` + `SlowFrames`. Sekinlashtirish
      engine'ning **o'z `ObjectStatus.SLOWED`** i: `MoveUpdate` allaqachon uni
      kiygan narsaning qadamini ikkiga bo'ladi, `StatusUpdate` esa taymerini
      o'zi sanaydi va o'zi yechadi. Ya'ni monstr **o'zi eriydi** — sehrgar
      o'lgan bo'lsa ham. Ritsarning whirlwind'i `SlowFrames` haqida hech narsa
      demagani uchun **zarracha ham o'zgarmadi**.
    - **Blink** = yangi `BLINK`. `DASH` dan farqi: hech qachon zarar bermaydi,
      **bosilgan nuqtaga aniq chiziq bo'ylab** boradi (dash o'z yuzi bo'ylab),
      va tushadigan joy topilmasa **rad etadi** (kuluar sarflanmaydi).
    - **Meteor** = yangi `METEOR`. **Kechikish — bu skillning o'zi.** Yerdagi
      ogohlantirish belgisi — ekranda chizilgan ishora emas, **dunyodagi haqiqiy
      obyekt** (`MeteorMark` + `FallingUpdate`), xuddi o'q kabi. Shuning uchun
      klientга bitta ham qator kod kerak bo'lmadi, tuman uni boshqa hamma narsa
      kabi yashiradi, va **doiradan chiqib ketgan monstr ultimate'ni yenggan
      bo'ladi** — bu qila oladigan narsa bo'lishi kerak edi.
  - **Sehrgar eng mo'rt narsa:** 380 jon va 4% armor (ritsarda 980 va 18%), va
    o'z quroli sahifadagi eng kuchsizi — uning butun qiymati kuluarda.
  - ★ **Model — vaqtinchalik:** KayKit Adventurers Mage repozitoriyada yo'q,
    shuning uchun u kamonchining meshini kiyib, skeletlarning tayog'ini ushlab
    turadi. `DungeonHero Mage` blokidagi **bitta `Model` qatori** — rig, kliplar
    va masshtab allaqachon to'g'ri.
  - **Skilllar hero'ga bog'langan, ma'lumot bilan.** `dungeon.ini` da
    `DungeonSkill <Hero> <Key>` bloklari; kod faqat **effekt turlarini** beradi.
    Ikkinchi hero = to'rt blok INI + creature blokida bitta `SkillBook` qatori,
    **noldan Java**. Test buni qulflaydi: faqat INI'da ta'riflangan `Rogue` o'z
    ikkita skilli bilan ishlaydi, hero'ning `Q` si esa unga tegishli emas.
  - **`SpecialPowerModule` ishlatilmadi** — u SAGE superquroli: bitta shakl
    (nuqtaga maydon zarari), qiymatlar konstruktorda muzlatilgan (daraja bilan
    o'smaydi), ochilish darajasi yo'q, va bitta birlikdagi 4 tasi adreslanmaydi
    (`findModule` birinchisini qaytaradi). `GrowableBody`/`HeroProgress` naqshi
    takrorlandi.
  - **`DamageModifier` choki ishlatildi** — `EMPOWER` uchun. `SkillBook` ning
    o'zi uni implement qiladi, buff modulini ulab-uzmaydi: kadr ichida modul
    ulash/uzish engine'ning modul ro'yxati **ataylab** rad etadigan narsa
    (aks holda keyingi modul jimgina o'tkazib yuborilardi), muddati o'tadigan
    bayroq esa xuddi shu narsa, tuzoqsiz.
  - **Determinizm:** kuluar `int[]` kadrlarda sanaladi, soatda emas; `STRIKE`
    nishoni eng yaqini, teng masofada `ObjectId` bo'yicha; `DASH` `StrictMath`
    bilan yuradi va **qadam-qadam** — to'g'ridan-to'g'ri qo'shilsa qahramon
    devor ichida qolardi.
  - **HUD** mavjud `status` kanalida: `Q ready  W 3s  E ready  R lv5`.
  - **Klavish bittasiga tegishli.** `W` skillni ham ishga tushirib, kamerani ham
    surardi: jME'da bitta klavishning har bog'lanishi ishlaydi, keyin qo'shilgani
    avvalgisini **almashtirmaydi**. Shuning uchun klient o'z boshqaruvlarini
    bog'layotganda o'yin da'vo qilgan harfni umuman olmaydi (`Hotkeys.unclaimed`).
    Faqat o'sha harf ketadi — kamera o'q tugmalarida qoladi, ya'ni o'yin WASD ni
    to'liq olsa ham suriladi.
  - **Reset tekin keladi:** har run va har chuqurlikda yangi Hero obyekti
    quriladi, ya'ni yangi `SkillBook`, nol kuluar; ultimate esa darajadan
    kelib chiqadi, daraja o'limda nolga qaytadi.
- **Har bir skillning o'z ko'rinishi bor** (`DungeonSkill ... Look = <blok>`).
  12 ta skill ilgari bir xil sukunatda ishlardi: ular otgan narsa chizilardi,
  **cast'ning o'zi esa yo'q**. Ikkita yangi effekt turi:
  - **`SHOCKWAVE`** — yerda ochilib boradigan halqa. Skill **shundan tanaladi**:
    uchqunlar "bu yerda nimadir bo'ldi" deydi va buni hamma skill bir xil aytadi;
    halqada esa **o'lcham** bor (o'yindagi yagona chizma, skill qay masofaga
    yetganini ko'rsatadigan), **tezlik** bor va qorong'ida ham o'qiladigan rang
    bor. Relyef balandligini kuzatib boradi.
  - **`GROUND_MARK`** — **turadigan** disk. Meteorning ogohlantirish doirasi va
    ritsarning `GUARD` i davomida yerdagi belgi.
  - ★ **`WaveEase` — butun "his" bitta raqamda.** U o'tgan ulushning darajasi:
    `1` da halqa **bir tekis** ochiladi (progress-bar shunday qiladi, dunyoda esa
    hech narsa), `2.4` da esa **otilib chiqib keyin sekinlashadi** — zarba
    shunday qiladi. `SkillEffectsTest` buni to'g'ri chiziq emasligini qulflaydi.
  - **Qatlamli, alternativa emas:** cast avval o'zining `IMPACT_BURST` ini va
    yorug'lik chaqnashini chizadi, keyin halqa **ularning ichidan** ochiladi.
    Uchtasidan istalgani o'z shiftidan tushib qolsa qolgan ikkitasi qoladi.
    **Yorug'lik eng muhimi** — o'yin qorong'i, relyef sheyderi 4 ta o'qiydi.
  - **Kamera silkinishi** faqat **ko'zni** suradi, qaragan nuqtani emas — ekran
    markazidagi narsa joyida qoladi, dunyo esa uning atrofida titraydi. Ikkita
    zarba **yig'indi emas, balandrog'ini** oladi (aks holda nova yonidagi meteor
    o'ynab bo'lmaydigan zilzila bo'lardi).
  - **Pool + shift:** halqalar bir marta quriladi va yashiriladi (`MaxRings`),
    trail'lar bilan bir xil masofadan narisi chizilmaydi. Shiftdan oshsa skill
    o'z olovini, yorug'ligini va zararini **saqlaydi**, faqat bezakni yo'qotadi.
  - **Klient qaysi skill ishlaganini qayerdan biladi:** `status` kanalidan
    (`cast=<blok>,<kadr>,<x>,<y>,<radius>`) — bu o'yinning **o'z klientiga** o'z
    kanali. `WeaponFired` faqat "kim otdi va qayerda" deydi, va u `rts` da —
    skill nima ekanini bilmaydigan o'yinlar bilan baham ko'riladi.
  - **Hammasi INI'da, o'chirish ham:** hamma `ShakeSeconds = 0` — silkinish yo'q;
    hamma `Look` qatori olib tashlansa — skilllar hech nima chizmaydi.
    `SkillLookTest` `Look` qatori **simulyatsiyaga yeta olmasligini** isbotlaydi
    (bir xil seed, checksum-ma-checksum).
  - ★ **Tuzoq:** whirlwind bitta bosishda 8 marta qo'nadi, ya'ni 8 marta
    chizilishi kerak — lekin klient chizadigan kadr va `HeroBrain` eshitadigan
    kadr **ikki xil raqam**. Ularni bitta qilganda ritsar ultimate o'rtasida
    yurish buyrug'ini 8 marta bekor qilib to'xtab qolardi.
- **Skilllar buyruq quvuridan o'tadi** — `CastSkill` bu **o'yinning o'z
  buyrug'i**. Klavish bosilishi klientda faqat `postCommand` qiladi; nishon
  tanlash, kuluar, daraja tekshiruvi — hammasi simulyatsiyada, kadr chegarasida.
  Buning uchun engine chokining o'zi ochildi (quyida).
- **Skeletlar qahramonni quvadi** — `MonsterBrain` uch radius bilan: sezish
  (~bitta xona), chaqirish (`AlertRadius` — qo'shnisi jang boshlasa, ko'rinish
  chizig'i ochiq bo'lsa, bu ham boshlaydi) va quvish (kengroq, lekin cheklangan).
  Skelet qahramondan sekinroq, shuning uchun jangdan chiqib ketish haqiqiy taktika.
  Test radiusda skeletning yaqinlashishini va radiusdan tashqarida **qimirlamasligini**
  qulflaydi; qiymatlar testga yozilmaydi — sozlamalardan o'qiladi.
- **O'yin ma'lumoti kodda emas, faylda** — `creatures.ini` (engine'ning o'z
  `ThingTemplateLoader` i o'qiydi) va `dungeon.ini` (engine'ning `Ini` +
  `FieldParseTable` bloklari). Balansni sozlash uchun qayta kompilyatsiya kerak emas;
  `DungeonSettingsTest` boshqacha INI matni boshqacha o'yin berishini isbotlaydi va
  buzuq faylni **yuklash vaqtida** maydonini nomlab rad etadi. Sozlamalar global
  static emas — kerak bo'lgan joyga uzatiladi, ya'ni simulyatsiyaning kirishlari oshkora.
- **Qahramon kuchayadi (leveling)** — skelet o'ldirilsa XP, yetarli XP'da daraja, har
  darajada ko'proq jon / ko'proq zarar / kamroq zarar olish. Daraja run ichida o'sadi va
  o'lim bilan **nolga qaytadi** (roguelike, meta-progress yo'q).
  **Engine'ning qaysi qismi ishlatildi va nega:** `ExperienceModule` XP'ni hisoblash uchun
  (`WeaponUpdate` allaqachon o'ldiruvchiga qurbonning `ExperienceValue` ini beradi — ya'ni
  "kim kimni o'ldirdi" savolini engine hal qiladi), lekin uning **rank tizimi emas**:
  `VeterancyLevel` — 4 ta qattiq rank, ko'paytirgichlari enum ichida hard-code, HP/DEF
  o'sishi yo'q. Shuning uchun `ExperienceRequired = 0 0 0` bilan ranklar o'chirilgan va u
  toza XP hisoblagichiga aylantirilgan; darajalar qoidasi o'yinniki (`level.Levelling`).
  **HP va DEF uchun o'z tanasi:** `ActiveBody` da `maxHealth` va `armor` **final**, shuning
  uchun o'yin `BodyModule` dan o'z `HeroBody` sini beradi — bu engine'ni o'zgartirish emas,
  aynan "Kengaytirish choklari" dagi modul choki. DEF engine'ning `Armor` i orqali, har
  darajada qayta qurilib, barcha `DamageType` lar uchun.
  **ATK cheklovi:** `WeaponUpdate.damage` final, `removeModule` yo'q, `ExperienceModule`
  esa `final` — ya'ni birlik bo'yicha zararni oshirish yo'li yo'q. Yagona ochiq ilmoq —
  `RtsPlayer.multiplyWeaponDamageBonus`, u **o'yinchi bo'yicha**. Qahramon o'yinchining
  yagona birligi bo'lgani uchun bu hozir aynan uning ATK'si; hamroh unitlar qo'shilsa
  qayta ko'rish kerak. Bonus `clearWorld` dan omon qoladi, shuning uchun yangi run'da
  qo'lda qaytariladi (aks holda daraja nolga tushib, ATK qolib ketardi).
  **Sozlanishi:** hammasi `dungeon.ini` dagi `DungeonLeveling` blokida — maksimal daraja,
  har daraja narxi, HP/ATK/DEF o'sishi, zarar poli; skeletning qiymati `creatures.ini` da.
  Ko'paytirgichlar darajadan **bir qadamda** hisoblanadi, to'plab borilmaydi — 7-darajali
  qahramon unga qanday yetgani bilan farq qilmasin.
  **HUD (hozircha):** daraja oshganda banner ("Level 2!"), va tanlangan birlikning
  `hp joriy/maksimal` i — HP o'sishi shu orqali ko'rinadi. To'liq daraja/XP qatori
  hozircha **yo'q**: `WorldSnapshot` da o'yin belgilaydigan ko'rsatkich uchun maydon yo'q
  va `game` ga tegilmadi. Buning uchun snapshotga bitta "status line" maydoni kerak.
- **Devorga kirib qolish tuzatildi (engine)** — o'yinchi "unitlar yurayotganda devor
  orasiga tiqilib qoladi" deb xabar berdi. Taxmin qilinmadi, **o'lchandi**: 12 ta
  dungeonda qahramon ov qilgandan keyin nechta unit tosh ichida turgani sanaldi —
  **15 ta** edi, endi **0**. Sabab engine'dagi ikki teshik, ular faqat koridorda
  uchrashadi:
  1. Harakatning har qadamdagi tekshiruvi `findBlocker` ni so'raydi, u **kim**
     to'sayotganini aytadi, **nima** emas. Devorlar butunlay pathfinderga qoldirilgan
     edi — "marshrut devordan o'tmaydi" degan mulohaza bilan, va bu unit marshrutiga
     **ergashsagina** to'g'ri. Qo'shnini chetlab o'tish marshrut rejalashtirmagan
     qadam, va tor koridorda yagona yo'l yon tomonga, toshga.
  2. Va nega halokatli: **boshlang'ich katagi bloklangan A\* hech narsa qaytarmaydi**.
     Devorga kirgan unit o'yin oxirigacha reja tuza olmaydi — qisilgan emas, rejasiz.

  Yechim: `World.isGroundBlocked` qo'shildi va `MoveUpdate` yerni ham so'raydi
  (toshda turgan unit istisno, aks holda devor qafasga aylanadi); `Pathfinder`
  toshdan boshlanganda eng yaqin turish mumkin bo'lgan katakka bitta waypoint
  qaytaradi, halqalar bo'yicha qat'iy tartibda.
- **Koridor kengligi endi korrektlik sozlamasi** — `CorridorWidth = 2` (20 birlik).
  Bitta katak 10 birlik edi, boss esa 16 kenglikda: u koridorga **sig'masdi**, va
  chetga qadam tashlashga joyi yo'q unit aynan toshga kirib qolardi. Test eng katta
  radiusni **INI'dan o'qib** solishtiradi, ya'ni koridordan kattaroq monstr qo'shilsa
  jimgina emas, baland ovozda yiqiladi.
- **Zichroq joylashuv** — `MaxRoomSpacing`: yangi xona allaqachon qo'yilganlaridan
  shu masofadan uzoqqa tusha olmaydi. Butun xarita bo'ylab rad etish namunasi
  xonalarni burchaklarga sochib yuborardi, ikki burchakdagi xona esa butun xarita
  bo'ylab koridor bilan ulanardi.
- **Boss oxirida** — u allaqachon koridorlar soni bo'yicha eng uzoq xonada edi; endi
  yo'lda kamida 2 xona, va o'rtacha yarmi bo'lishi test bilan qulflandi. Minimapda
  oq va eng katta — modelsiz o'yinda qavatning oxiri qayerda ekanini aytadigan
  yagona narsa.
- **Dushman xilma-xilligi va chuqurlik** — besh tur (Skeleton, Runner, Brute, Stalker,
  Revenant) va boss. **Bitta miya, turlar bo'yicha parametrlar:** archer boshqa aql
  emas, u shunchaki uzoqroqda to'xtaydigan aql (`CloseDistance` — yaqinlashadigan
  narsa bilan otadigan narsa orasidagi butun farq). Har turga alohida sinf yozilsa,
  beshinchi tur bitta sinf, yigirmanchisi yigirmata bo'lardi.
  **Yangi tur = ikki blok INI, Java yo'q:** `dungeon.ini` da `DungeonMonster` bloki
  (xulq + qaysi chuqurlikdan chiqadi + qanchalik tez-tez + rang/o'lcham) va
  `monsters.ini` da template. O'yin brainlarni shu ro'yxatdan ro'yxatga oladi.
  Revenant `AutoHealUpdate` ni ishlatadi — engine'da allaqachon bor modul.
- **Chuqurlik** — boss eng uzoq xonada (koridorlar soni bo'yicha, metr emas: to'g'ridan
  koridori bor uzoq xona aslida qo'shni). Boss o'ldirilsa keyingi qavat: yangi seed,
  kuchliroq aholi, **lekin o'sha qahramon** — daraja, XP va tanasi bilan. O'lim esa
  hammasini 1-qavatga qaytaradi.
- **Uzoqdan otadiganlar** — ilgari hammasi qo'l bilan urardi, va Stalker blokida
  aynan shunday deb yozilgandi: "kitda otadigan jonzot yo'q". Endi bor.
  - **Stalker — arbalet**, **Revenant — kichik olov shari** (uning ODDIY atakasi;
    fireball-skill keyinroq). Ikkalasi ham qahramonning `Bow` moduli bilan otadi.
  - `Bow` endi **global o'qni emas, o'zinikini** o'qiydi (`Projectile`, `Speed`,
    `MuzzleOffset`). Bo'sh blok avvalgidek qahramonning o'qini oladi.
  - **Ikkalasi ham `EyesOnly` oladi** — aks holda birinchi uzoq otuvchi jonzot
    qahramonni **devor orqali** otardi. Engine quroli devorni bilmaydi.
  - **Qurol ham to'plamning donasiga qarshi yotishi mumkin.** Paketlar deyarli hamma
    qurolni o'z `+Y` i bo'ylab yotqizadi (qilich 1.50, bolta 1.25, tayoq 2.10) va
    suyak aynan shunga mo'ljallangan. **Ikkitasi istisno** -- qahramonning kamoni
    va skeletlarning arbaleti, ikkalasi ham `+Z` bo'ylab. Kamon teskari kelgan,
    arbalet esa ko'ndalang yotib qolgan. Test buni **o'lchaydi**: uzun tomoni +Y
    bo'lmagan qurolda burilish bo'lishi shart, bo'lganida esa bo'lmasligi.
  - **Poza ham muhim ekan.** Klip to'g'ri o'ynardi va hech kim ko'rmasdi: qo'llari
    tushgan skelet, yonida osilgan arbalet, va ular orasida bir soniyalik klip —
    bu otish emas, ko'kragidan o'q chiqqandek ko'rinadi. `Idle` — narsa vaqtining
    to'qqiz ulushida qanday turishi, ya'ni arbaletchi uchun u **mo'ljalga olgan**
    bo'lishi kerak (`Ranged_2H_Aiming`). `MuzzleOffset` ham qurolga chiqarildi.
- **Uchayotgan narsa yonadi** — `ProjectileEffects` (faqat klient).
  - Klient **turlarni** biladi (`FLAME_TRAIL`, `GLOW_ORB`, `IMPACT_BURST`), INI
    esa qaysi snaryad qaysi retseptga ega ekanini va **har bir sonni** aytadi.
    Yangi yonadigan narsa = INI bloki, klass emas.
  - **Simulyatsiyaga tegmaydi.** Uchta lahza klientda allaqachon bor edi: paydo
    bo'lishi (`syncUnits`), uchishi (`updateUnitNode`), tegishi — `ObjectDied`
    hodisasi, uni engine **har qanday** yo'q qilingan obyekt uchun yuboradi, ya'ni
    o'q qo'ngan zahoti, pozitsiyasi bilan. Test: effektlarni INI'dan kesib tashlab
    bir xil seed o'ynaganda checksum bir xil qoladi.
  - **⚠️ Relyef shaderi jME yorug'liklarini o'qimaydi** — u quyoshni va ambientni
    o'zi olib yuradi. Ya'ni `PointLight` jonzotlarni yoritardi, polni esa yo'q.
    `FoggedTerrain` ga **to'rtta ko'chma point light** qo'shildi; bo'sh uya qora
    rang bilan o'tadi (shox emas, bir xil narx).
  - **GLOW_PARTS** — uchmaydigan yagona tur, va eng arzoni: model ichidagi nomlangan
    bo'laklarni ichidan yoritadi (skeletlarning ko'zlari). Bitta material, **nol**
    yorug'lik — xonada yigirmata skelet bo'lishi mumkin, har biriga mash'al
    ikkinchisidayoq byudjetdan chiqardi. Nom **so'z bo'yicha** mos keladi
    (`Skeleton_Warrior_Eyes`, `Skeleton_Mage_Eyes` — bitta `Part = Eyes`).
  - **Pool va byudjet.** Emitterlar, yorug'liklar va portlashlar bir marta
    yasaladi va qaytariladi. Chegaradan oshgani **yorug'liksiz uchadi** — izi
    qoladi, ya'ni yo'qoladigan narsa poldagi yaltirash, otish emas. Uchqun
    teksturasi **kodda generatsiya qilinadi**, fayl emas.
- **Tushishning tubi bor** — va bu o'yinning shaklini o'zgartiradi. Ilgari qavatlar
  cheksiz pastga ketardi: har biri bir oz qiyinroq, va qavat beradigan yagona savol
  "yana qancha?" edi. Endi **har qavatga o'z bossi**, va oxirgisini yenggach —
  **g'alaba** (`DungeonRun.State.WON`).
  - **Ro'yxat = qavatlar soni.** `DungeonDepth Descent / Bosses = Warden Reaper
    Necromancer Champion`. Nechta qavat borligini aytadigan **ikkinchi son yo'q** —
    ikkinchi son bu ro'yxat bilan kelisha olmaydigan son bo'lardi.
  - Hech qanday `Bosses` aytilmasa — eski xulq: bitta `Boss`, har qavatda o'sha,
    cheksiz pastga. Ya'ni tubsiz tushish o'chirilmadi, shunchaki tanlovga aylandi.
  - G'alaba ham, o'lim ham runni tugatadi va hammasini 1-qavatga qaytaradi:
    farqi — so'z va `VictoryFrames` (uzunroq, chunki o'lim uzilish, g'alaba esa
    tugash).
  - To'rtta boss — paketning to'rtta skeletoni, kattalashtirilgan. Kuch pog'onasi
    esa avvalgi `BossHealthPercentPerDepth` ko'paytirgichi: Warden 1-qavatda
    (ko'paytirgich 1.0), Champion 4-da (2.2) — ya'ni Champion aynan ilgarigi
    bossning 4-qavatdagi kuchida qoladi.
  ⚠️ **Nozik joy:** ikkalasi ham qahramon obyektini almashtiradi. "Yangi qahramon
  ko'rindi ⇒ darajani nolla" mantiqi har qavatda darajani o'chirardi — shuning uchun
  run loop `HeroProgress` ga qaysi biri ekanini **aniq aytadi** (`carryOver` yoki
  `reset`), taxmin qilinmaydi.
- **Chuqurlik balansi** — HP o'sadigan tana orqali, zarar `DamageModifier` moduli
  orqali (generalizatsiya refaktorida ochilgan chok aynan shu uchun kerak bo'ldi),
  XP esa `replaceModule` bilan almashtirilgan `ExperienceModule` orqali. Hammasi
  `DungeonDepth` blokidagi foizlardan, chuqurlikdan **bir qadamda** hisoblanadi.
- **Vizual farqlash** — `Visuals.colour()` qo'shildi (`client3d`): o'yinchi rangi
  "bu kimniki?" degan savolga javob beradi, bu esa RTS so'raydigan yagona savol.
  Bir tomonda bir necha xil narsa bo'lgan o'yinda ikkinchi savol bor — "bu nima?" —
  va modelsiz unga javob beradigan boshqa narsa yo'q: hamma dushman bitta ko'k
  kapsula bo'lib qolardi, ekranda ham, minimapda ham.
- **Generatsiya ham determinizm shartnomasida** — `DeterministicRng` (xorshift64,
  faqat butun sonli amallar). Generatsiya yo'lida `Math.random`, devor-soati va
  trigonometriya yo'q; har keyingi run'ning seed'i oldingisidan shu zanjir bilan
  olinadi, ya'ni bitta boshlang'ich seed butun sessiyaning dungeonlar ketma-ketligini
  belgilaydi. Soatga tegadigan yagona joy — `Main` dagi boshlang'ich seed tanlovi,
  u simulyatsiyadan **tashqarida**: qaysi deterministik olamni o'ynashni tanlaydi,
  dungeon qanday qurilishiga aralashmaydi.
- **Determinizm har build'da tekshiriladi** — `ReplayRoundTripTest`: haqiqiy
  skirmish (jang + ishlab chiqarish + skriptli buyruqlar) yozib olinadi va qayta
  o'ynatiladi; tekshirish nuqtalari mos kelishi va yakuniy checksum bir xil
  bo'lishi shart. `ReplayTest`: soxtalashtirilgan tekshirish nuqtasi **o'sha
  kadrda** ushlanadi, va o'zi buyruq yaratadigan dunyo ham aynan qayta o'ynaydi.
- **Desync aniqlanadi va o'yinni to'xtatadi** — `LockstepGateTest`: kelishayotgan
  peer'lar yolg'on signal bermaydi va `RUNNING` bo'lib qoladi; **bitta buyruqni
  tashlab ketgan** peer ushlanadi (birinchi tekshirish kadrida), ikkovi ham
  `DESYNCED` ga o'tadi, kadr raqami muzlaydi va keyingi 100 urinishda ham
  qimirlamaydi; nomuvofiqlikni o'zi ko'rmagan peer host'ning `SessionHalted`
  xabari bilan to'xtaydi. `NetworkTransportTest`: bu xabar sim orqali ham o'tadi.
- **Rohan vs Mordor** — Studio'ning o'z modeli orqali yozilgan to'liq o'yin
  (`examples/RohanVsMordor.duke`); `dist/RohanVsMordor/` mustaqil loyiha sifatida quriladi va
  menyular bilan ishlaydi. `RohanVsMordorTest` 2700 kadrlik headless urushni tekshiradi
  (AI oltin sarflaydi, talofatlar bo'ladi).
- **Native .exe** — `Rohan-vs-Mordor.exe` ishga tushgani tasdiqlangan (~205 MB, o'z runtime'i bilan).
- **Xonalar kattaroq, hamma narsa sekinroq** — karta xonaga to'ldi va masofalar
  bir xil ulushda qisqardi.
  - `MinRooms 6`, `MaxRooms 15`, xona o'lchami 7–13, `PlacementAttempts 3000`:
    o'lchandi — o'rtacha **8.5 xona, 44% pol** (ilgari 6.5 va 21%).
  - `MinRooms` — **so'rov, kafolat emas.** Generator sig'adiganini qo'yadi.
    Jo'natilgan sonlar haqiqatan oltitasini sig'diradi — bu 201 seed ustida
    o'lchangan, bahslashilmagan. Kim ko'tarmoqchi bo'lsa, fayl shuni aytadi.
  - Keyin **har bir tezlik va har bir ko'rish masofasi 0.65 ga ko'paytirildi** —
    qahramon ham, maxluqlar ham. Xonalar kattaroq va ularni kesib o'tish uzoqroq:
    bu bitta o'zgarish, ikki marta aytilgani.
  - Uni kamonchi qiladigan nisbatlar saqlandi: eng uzoq sezuvchi maxluq (**55**)
    uning kamoni (**60**) ichida, u esa uning ko'rishi (**70**) ichida.
  - O'nta test shundan qizardi va hammasi **masofa** sababli edi (nishon endi
    yangi masofadan tashqarida qolgan). Bittasi boshqacha: `roomCountStaysInRange`
    kafolat kutardi — yuqoridagi izoh o'shandan chiqqan.
- **Maxluqqa bosish hitboxi** — ilgari maxluq modeliga bosilsa, orqasidagi yerga
  yurish buyrug'i ketardi. Klient butun tugun bo'yicha uchburchak to'qnashuvini
  qidirardi; pozadagi skinlangan model uchun bu ko'ringan joyida emas. Endi
  modelning dunyo chegarasi bilan tekshiriladi (`pickUnit`).
  - **Test bilan qoplanmagan:** kamera va oyna kerak, headless'da tekshirib
    bo'lmaydi. Faqat ko'z bilan.
- **Qahramon paneli** — `HeroPanel` (client3d): tosh o'ymasi uslubidagi jon/tajriba
  barlari, to'rt skill uyasi (tayyor / kuluar / yopiq), rim raqamli chuqurlik.
  - **Klient mexanizmni saqlaydi, o'yin so'zlarni beradi.** Panel snapshotning
    status kanalidan chiziladi; formatni tanimasa — **yashirinadi** va eski matn
    HUD qoladi. `client3d` ni yana 3 ta o'yin ishlatadi, shuning uchun bu shart.
    Panelda bitta ham inglizcha so'z yo'q: `CHUQURLIK` va `-daraja` `dungeon.ini`
    dagi `DungeonHud` blokidan.
  - Format ikki tomondan qulflangan: `HeroStatusTest` yozuvchi tomonni,
    `HeroPanelTest` o'quvchi tomonni tekshiradi. Biri o'zgarsa — biri qizaradi.
  - Kuluar soyasi **kvadratga qirqilgan** konus (doira emas), aks holda u tosh
    ustidagi tangaga o'xshardi. Belgilar SVG emas, chiziq-mesh — uyaning rangini
    o'zi oladi va rasm fayli kerak emas.
  - Qahramon nomi `DisplayName = Erika` (`creatures.ini`). Template nomi `Hero`
    bo'lib qoladi — uni spawner, skill bloklari va hamma test shunday chaqiradi.
- **Yo yuradi, yo otadi** — `AttackOnTheMove = Yes|No` (`WeaponUpdate`, default
  `Yes`, ya'ni mavjud hamma birlik o'zgarishsiz).
  - **Nega engine'da, skriptda emas:** qurol nishonni **o'zi topadi va o'sha
    chaqiruvda otadi**. Tashqaridan `holdFire()` qilgan skript keyingi kadrda
    quroldan **keyin** ishlaydi — ya'ni qurol allaqachon qayta nishonlab otib
    bo'lgan. Bu haqiqatan yetkazilgan bug edi: kamonchi har kadr o'q otishni
    to'xtatib, baribir otardi. `AttackOnTheMoveTest` ikkalasini ham qulflaydi.
  - Yurayotganda **reload ketaveradi va nishon saqlanadi**, ya'ni to'xtagan zahoti
    o'q uchadi. Aks holda har qadam bir reload turardi.
  - `HeroBrain` bundan **soddalashdi**: qurol nishonni butun yo'l bo'yi saqlagani
    uchun brain uning o'rniga hech narsa eslab turmaydi, va `MoveTo` buyrug'i
    quroldan nishonni olib tashlagani uchun ataka buyrug'ining bekor bo'lishi
    **o'z-o'zidan** ishlaydi.
- **Zarbani oxirigacha yetkazish** — maxluq urishni boshlagach, hero chetga chiqsa
  ham turib zarbani tugatadi, keyin quvadi. `SwingFrames` har bir turnikida
  (`DungeonMonster` bloki): brute 22, runner 8.
  - **Yaqinlashganiga emas, haqiqiy zarbaga bog'langan.** Avval oddiygina
    "masofa ichida bo'lsa" deb yozgan edim — o'shanda maxluq yonidan o'tib
    ketilganda ham 12 kadr yo'qotardi, ya'ni undan tezroq har kim tekinga qochib
    ketardi (o'lchab ko'rdim: skeleton chase radiusidan chiqib qolardi).
  - Zarba qachon tushganini `Swing` moduli aytadi — u `ProjectileLauncher` ni
    implement qiladi va **hech narsa uchirmaydi** (`false` qaytaradi, ya'ni zarar
    odatdagidek joyida tushadi). Bu engine'ning qurol otgan lahzadagi yagona
    ilgagi; engine'ga tegilmadi.
  - Animatsiya o'z-o'zidan to'g'ri chiqadi: nishoni bor va turgan maxluq — bu
    aynan klient "urayapti" deb chizadigan holat.
- **Toshning tomi yopildi** — `TileLayout.Piece.CAP`. Ilgari tosh hujayralar
  umuman chizilmasdi, ya'ni devor ortida **bo'shliq** turardi va kamera devor
  ustidan o'sha teshikka qarardi — tomsiz uy.
  - Qopqoq devor balandligida yotadi (`WallHeight`, `DungeonTiles` blokida), va
    ochiq yerga tegib turgan har bir tosh hujayraga qo'yiladi (diagonal ham).
  - Qopqoqning **egasi** — yonidagi xona, tosh emas: tuman hujayra bo'yicha
    ishlaydi va toshning hujayrasi yo'q. Ya'ni qopqoq xona ochilganda paydo
    bo'ladi va u bilan birga eslab qolinadi. Bitta tosh — bitta ega (birinchi
    ochiq qo'shni), aks holda ikkita plitka bir joyda urishardi.
  - Devori yo'q to'plamga qopqoq qo'yilmaydi — yopadigan narsa yo'q.
- **Fog of war silliqlandi** — uch shakldan uzluksiz yorug'likka.
  - **Fazoviy:** har bir hujayraning yorqinligi qo'shnilari bilan 4-2-1 yadro
    bo'yicha o'rtachalanadi, ya'ni chegara bitta chiziqqa emas, bir necha
    hujayraga yoyiladi. **Tosh o'rtachaga kirmaydi** — u qorong'i yer emas,
    devorning o'zi; busiz ikki hujayra kenglikdagi koridor hech qachon to'liq
    yoritilmasdi.
  - **Vaqt bo'yicha:** hech narsa sakramaydi, sekundiga 7 ulush bilan maqsadiga
    yaqinlashadi — kadrga emas, sekundga, aks holda tezroq mashinada tuman
    tezroq ochilardi.
  - **Ochiladigan narsa o'zgarmadi:** silliqlash ochilgan hujayralar to'plamini
    faqat o'qiydi, hech qachon yozmaydi (`softeningOpensNothing` shuni qulflaydi).
- **Nishonli skillar** — `CastSkill` endi o'yinchi nimani bosganini olib yuradi.
  - `SkillEffect` har bir effekt nimaga qaratilishini biladi: `STRIKE` → maxluq,
    `DASH` → yer, `AREA_DAMAGE`/`EMPOWER` → hech narsa. `Main` shunga qarab
    klavishni bog'laydi; klient bosish-keyin-klik mexanizmini saqlaydi
    (`Hotkeys.onUnit` / `onGround`), o'yin esa nima jo'natishni aytadi.
  - **Yetib bormaydigan narsaga qaratilgan skill rad etadi va kuluarni sarflamaydi.**
    Bosgan maxluqni emas, boshqasini urish — bu nishonlab bo'lmaydigan skill, va
    yo'qotilgan kuluar aynan kerak bo'lgani.
  - Dash bosilgan **joyga** boradi (yo'nalishga emas): yaqin joy bosilsa o'sha
    yerda to'xtaydi, uzoq bo'lsa qanchaga yetsa. Bordi-yu qaragan tomoni boshqa
    bo'lsa — avval buriladi.
  - Kutayotgan uya **nafas oladi** (mash'al rangli lab). Bekor qilish: o'sha
    klavisha yana, o'ng tugma yoki Esc. Tayyor bo'lmagan skill umuman qurollanmaydi.
- **Q endi haqiqiy o'q otadi** — `WindUpFrames` (kamonni tortish) + `Projectile`
  (uchadigan narsa), ikkalasi ham `DungeonSkill` blokida.
  - Bosilganda darrov jon ketmaydi: qahramon nishonga buriladi, quroli unga
    qaraydi (ya'ni otish animatsiyasi o'ynaydi), va **15 kadrdan keyin** o'q
    chiqadi. Chiqqanda `WeaponFired` yuboriladi — klient shundan otish yorug'ligi
    va tovushini oladi, oddiy kamon bilan bir xil yo'ldan.
  - Uchadigan narsa `HeavyArrow`: aynan o'sha model va mesh, **~2 barobar katta**,
    mash'al rangida va **sekinroq** (120 vs 170) — sarflangan o'q ko'z bilan
    kuzatiladigani bo'lsin uchun. `Shot` sinfi endi kamon va skill uchun umumiy.
  - Kuluar **bosilganda** boshlanadi, o'q tekkanda emas: aks holda uzoqdagi otish
    yaqindagisidan arzon bo'lardi.
  - Tortayotganda nishon o'lsa — o'q yo'qoladi va kuluar sarflangan bo'lib qoladi.
    Vaqt talab qiladigan skillning narxi shu, va u shuning uchun qattiqroq uradi.
  - **Tortayotganda kamoni jim turadi** — `WeaponHold` choki orqali. Busiz bitta
    bosishga **ikkita** o'q chiqardi: skill ko'zlash uchun quroliga nishon
    qo'yadi, qurol esa `SkillBook` dan **oldin** yurib o'sha zahoti otib yuborardi.
    Reload ostidan ketaveradi, ya'ni og'ir o'q chiqqach oddiy otish darrov
    tiklanadi — skill ishlatgani uchun qo'shimcha kutish yo'q.
- **R endi Q va W ni ham kuchaytiradi** — `dungeon.ini` da yozilganidek. Ilgari
  boost engine'ning zarar chokida yurardi, uni esa faqat **qurol** o'qiydi;
  `getBody().damage(...)` chaqiradigan skill undan o'tmasdi. Darajadagi +12% esa
  qurolnikiligicha qoladi — skillar o'z `DamagePerLevel` i bilan o'sadi, ikkalasi
  qo'shilsa daraja ikki marta hisoblanardi.

- ~~**Daraja oshganda kuch tanlanadi**~~ — **olib tashlandi** (8.ax): kartalar,
  kuchlar kitobi, tanlash oynasi, kuchlar qatori va `Qon` statistikasi bilan birga.
- **Warcraft uslubidagi pastki panel** — `HeroPanel` bitta tosh plitaga aylandi:
  chapda minimap uyasi, o'rtada portret va tirik ko'rsatkichlar, o'ngda skill
  uyalari, chekkada chuqurlik. Bo'limlar
  o'yma chiziq bilan ajratiladi — alohida quticha emas.
  - **Maket piksellarida chiziladi, keyin oynaga masshtablanadi** (`DESIGN_WIDTH`
    1180). Shuning uchun nisbatlar har ekranda saqlanadi va `reshape` bitta
    `layOut()` chaqiradi.
  - **Minimap panelga ko'chdi.** Panel uyaning to'rtburchagini oyna piksellarida
    beradi (`minimapRect`), klient minimapni o'shanga moslaydi — hech biri
    ikkinchisining arifmetikasini bilmaydi. Panel yo'q o'yinda minimap avvalgidek
    burchakda qoladi.
  - **Portret — tirik 3D sahna** (`HeroPortrait`, 8.ag). Ilgari siluet edi va
    izohda sababi ham yozilgan edi: "ramka aslida ekranning qaysi burchagi meniki
    deyish uchun". Bu to'g'ri edi, lekin to'liq emas — ramka yana o'yinchi ikki
    klik orasida qaraydigan yagona joy, va nafas oladigan, jangga tayyorlanadigan,
    o'lganda yiqiladigan odam siluet ayta olmaydigan savolga javob beradi.
    Siluet qoldi — portreti yo'q maxluq va zaxira yo'l uchun.
  - Panelga bitta ham inglizcha so'z qo'shilmadi: `Zarba`, `Zirh` va `Tezlik`
    `DungeonHud` blokidan keladi.
  - **Uyada endi ikonka bor, va u INI'da tanlanadi.** Uya ilgari klavishning
    harfini chizardi. Endi har `DungeonSkill` bloki `Icon = arrowhead.png` deydi,
    `DungeonHud` esa `IconFolder = icons/skills/` — tayyor yo'l status qatorida
    ketadi (`skill=Q,icons/skills/arrowhead.png,ready`), klient esa berilgan
    yo'ldagi rasmni chizadi. **Kodda birorta ikonka nomi yo'q**: beshinchi skill —
    faylning beshinchi bloki, Java'ga tegilmaydi. Klient uchta boshqa o'yinga
    ham xizmat qiladi va ularning birortasi rasmni qayerda saqlashini bilmasligi
    kerak, shuning uchun **nom emas, yo'l** uzatiladi.
    - **Rasm oq, bo'yash chizishda.** Bitta fayl uch holatga yetadi: tayyor —
      mash'al rangi, kuluarda — sovuq kulrang, yopiq — deyarli o'chgan. `dress()`
      avvalgidek `Color` ni o'rnatadi va qo'lida harfmi yoki tekstura ekanini
      bilmaydi.
    - **Topilmasa — harf.** Rasmni o'yin nomlaydi, klient imlosini tekshira
      olmaydi; yo'q fayl logga bir marta yoziladi (`HeroPanel.iconTexture`) va uya
      rasmlar paydo bo'lishidan oldingi ko'rinishiga qaytadi. Panel yo'qolmaydi.
    - **PNG, va faqat PNG.** jME SVG o'qimaydi, shuning uchun to'rt ikonka
      128×128 shaffof PNG qilib chiqarilgan va faqat shu repoda yotadi. Gradle
      qadami yo'q — ikonka ham san'at, va san'at modellar kabi tayyor holda
      saqlanadi. Asl `.svg`/`.png` fayllar game-icons.net'da, havolalari
      `License.txt` da.
    - **Ikonkalar CC BY 3.0** (Lorc, game-icons.net) — atribut talab qilinadi va
      `CREDITS.md` da hamda `icons/skills/License.txt` da berilgan. Asl fayllarda
      birinchi path — qora fon kvadrati; u olib tashlangan, aks holda tosh uya
      ustida qora plitka chiqardi.
- **Skillni sichqoncha bilan ham ishlatish** — uyaga bosish klavishani bosish
  bilan **bir xil yo'ldan** boradi: klik klientning o'z `pressHotkey` iga tushadi,
  ya'ni nishon talab qiladigan skill xuddi shunday qurollanadi va kutadi,
  talab qilmaydigani darhol ketadi. Ikkinchi nusxa qoida yo'q.
  - Kuluar yoki yopiq uya klikni **yutadi va hech nima qilmaydi** — tosh uyaning
    ustida soya turganda aynan shunday ko'rinishi kerak.
  - Kursor uya ustida bo'lsa uya yorishadi; qurollangan uya esa avvalgidek nafas
    oladi. Ikkalasi ikki xil, chunki ikki xil narsa deydi.
  - **Panelning qolgani ham klikni yutadi**: portretga bosganda qahramon panel
    ortidagi yerga yurib ketardi.
  - **Uya koordinatasi tuzog'i (tuzatilgan):** uya `skillRow` ichida, `skillRow`
    esa markazlashtirilgan blok ichida — faqat blokning siljishini qo'shsak, klik
    chizilgan joydan bir ustun chapga tushardi. Endi uya o'z joyini maket
    piksellarida **eslab qoladi** (`PanelHitTest`).
- **Tuman devorni pisand qiladi (line of sight)** — har katak uchun qahramondan
  to'g'ri chiziq (Bresenham, butun sonlarda) tekshiriladi: yo'lda tosh bo'lsa katak
  yoritilmaydi. **Devorning o'zi ko'rinadi**, ortidagi yo'q — shuning uchun faqat
  **oradagi** kataklar so'raladi. Shader yo'q, trigonometriya yo'q: pathfinder
  allaqachon yuritadigan gridning ustida arifmetika.
  - Busiz yorug'lik nimadan o'tayotgani bilan qiziqmaydigan doira edi: koridorda
    turish ikki tomondagi xonalarni ham yoritardi.
  - **Chekka yumshoqligi sozlanadi** — silliqlash yadrosi endi piramida
    (`SoftenCells` radiusi): 1 da eski 4-2-1, kattaroq radiusda asta so'nadi.
  - **Tuman rangi bor.** Qorong'ilik qora emas, `Tint` — ochilgan xona
    "yoritilmagan tosh" emas, "qorong'idan ko'ringan tosh" bo'ladi. O'sha rang
    oynaning fon rangi ham (ikkalasi bitta qorong'ilik).
  - Hammasi `DungeonFog` blokida; `Fog` yozuvi klientda, so'ramagan o'yin
    avvalgidek qoladi (LOS o'chiq, qora tuman).
- **Loot — o'lgan monster nimadir qoldiradi** — ustiga borilsa olinadi va run
  oxirigacha qoladi: ATK (foiz), HP (yassi) yoki DEF (foiz). Inventar yo'q va
  atayin yo'q: qaror — "borib olamanmi", va oxirida ikkinchi qaror rasmiyatchilik
  bo'lardi.
  - **Engine'ning `DieModule` chokida** — jasad dunyodan chiqqach ishlaydi, ya'ni
    tushgan narsa tana yo'q joyga tushadi. Modul spawn paytida osiladi (depth
    bonusi kabi), creature blokida emas: template narsaning **nima** ekanini
    aytadi, **qayerda uchraganini** emas.
  - **Deterministik va tartibdan mustaqil**: qur'a run seed'i **va monsterning
    o'z id'si** dan chiqadi, aylanayotgan generatordan emas. Shuning uchun monster
    birinchi o'ldirilganda ham, oxirgi o'ldirilganda ham bir xil narsa qoldiradi —
    aks holda replay o'yinchining marshrutiga bog'lanib qolardi.
  - Chuqurlik ikki tomondan ta'sir qiladi: `MinDepth` yaxshi narsalarni yuqori
    qavatlarda ushlab turadi, `ValuePercentPerDepth` esa topilganini qimmatlashtiradi.
  - **Qavat boss o'lgan zahoti yopilmaydi** (`DescendDelayFrames`). Bu bug edi:
    boss ham narsa qoldiradi, dunyo esa o'sha kadrda qayta qurilib uni olib
    ketardi. Yon foydasi — tugagan jangga bir lahza beriladi.
  - Olingan narsa **`HeroProgress` orqali** qo'llanadi, tushgan joyda emas: ATK va
    DEF darajadan **hisoblanadi** (tayinlanadi), shuning uchun sandiq o'zi
    yozganini keyingi daraja o'chirib yuborardi.
  - Modeli yo'q — kitda sandiq yo'q, shuning uchun mash'al rangli quti.
- **Edge scrolling** — kursor ekran chetiga borsa kamera suriladi, WASD va g'ildirak
  bilan **birga**. Tezlik `EdgeSpeedPercent` — klavishlar tezligining ulushi, va u
  masofaga bog'liq, ya'ni har zoomda bir xil miqdorda **ekran** suriladi.
  - **Dunyoning pastki chekkasi — panelning tepasi**, oynaning tepasi emas: panel
    bor ekranda oynaning pastki cheti skill uyasining o'rtasidan o'tadi. Panelning
    o'zi hech nimani surmaydi — uya ustida turish tekin bo'lishi kerak.
  - Menyu yoki tanlov ekrani ochiq bo'lsa surilmaydi.
  - So'ramagan o'yinda o'chiq (`EdgeScroll.NONE`), ya'ni studio va sandbox
    o'zgarmagan.
- **Fullscreen F11 bilan** — sozlamalar menyusidagi o'sha tugmachaning o'zi, ya'ni
  ikkovi kelisha olmaydi va tanlov keyingi ishga tushirishda ham esda qoladi.

- **Ko'rmasa otmaydi ham** — tuman devorni bilardi, o'q esa bilmasdi: qahramon
  devor ortidagi monstrni bemalol o'ldirardi.
  - **`SightLine`** — sof arifmetika (Bresenham, butun sonlarda) `World.isGroundBlocked`
    ustida. Devorning **o'zi** ko'rinadi, ortidagi yo'q, ya'ni faqat **oradagi**
    kataklar so'raladi. Klientdagi tuman bilan bir xil qoida, lekin **alohida**
    yozilgan: o'yinchiga nima ko'rsatilishi jang natijasini hech qachon
    belgilamasligi kerak.
  - **`sees` uchta savol so'raydi, bitta emas** — va ular klient tumani so'raydigan
    o'sha uchtasi. Ajratilgan, lekin **kelishmovchilikka yo'l yo'q**: ko'rsatilmagan
    narsaga otish mumkin emas.
    1. **Yetarlicha yaqinmi** — `VisionRange`, va **katak** bo'yicha, klient qaysi
       katakni yoritsa o'shasi. Ilgari bu tekshirilmasdi: `AttackRange` (60) shunchaki
       `VisionRange` (70) dan kichik qilib qo'yilgandi va ikkita raqam bir-birini
       ushlab turardi. Endi yo'q — **kamon ko'zdan uzunroq bo'lishi mumkin**, otishni
       to'xtatadigan narsa qorong'ilik.
    2. **Orada hech narsa yo'qmi** — tosh, avvalgidek.
    3. **Balandda turgan narsa yo'qmi** — o'z qavatidan yuqoridagi pol o'z chetining
       ortida: pastdagi koridordan uning ustiga ko'rinmaydi, va klient ham shunday
       chizadi. Qavatlar qo'shilganda bu yerda hech narsa o'rganilmagan edi, ya'ni
       qahramon ko'tarilgan xonadagi — hech qayerda chizilmagan — monstrlarni otardi.
    - Zina **pastki qavatga tegishli**: qavat pastga yaxlitlanadi, chunki zina
      katagining o'rtasi yarim qavat balandda turadi va yaqiniga yaxlitlansa zina
      o'zini o'zi yashirardi.
    - `StoreyHeight` `EyesOnly` ga registratsiyadagi closure orqali keladi
      (`Bow` va `SkillBook` kabi), ya'ni INI'da takror raqam yo'q.
  - **`EyesOnly`** — `WeaponHold` choki, qurolning **oldida** ishlaydigan yagona
    ilgak. Ikki narsani to'xtatadi: ko'rinmayotgan nishonga otishni, va **umuman
    nishonsiz turishni** — ikkinchisi engine'ning o'z avto-tanlashini o'chiradi,
    chunki u "eng yaqin dushman" izlaydi va devorni bilmaydi. Tanlash `HeroBrain`
    ga o'tadi, u esa biladi. Narxi bir kadr; foydasi — aylanib o'tib bo'lmaydigan
    qoida.
  - **`HeroBrain` soddalashdi.** Endi har nishon yo shu klass qo'yganidir, yo
    o'yinchi — demak "buyurilganmi yoki yo'lda uchraganmi" **fakt**, taxmin emas.
    Eski masofa-evristikasi va uning "aynan bir marta hukm qilish" tuzog'i
    o'chdi.
  - **Klient ham ko'rsatmaydi** — engine tumani doira, ya'ni devor ortidagi
    monster snapshotda bor edi va qorong'ida turgan holda chizilardi. Endi
    `Discovery.canSee` bo'yicha filtrlanadi; qahramonning o'z narsalari doim
    chiziladi.
- **O'yinchining oxirgi buyrug'i ustun** — "bir vaqtda bitta ish".
  - Yurib ketayotganda ataka buyurilsa — **to'xtaydi va otadi**. Buni bilish
    uchun brain yurayotganda o'zi nishon tanlamaydi: shunda qurolda paydo
    bo'lgan har qanday nishon **faqat o'yinchiniki** bo'ladi, hatto u brain
    tanlagan bo'lardigan o'sha monster bo'lsa ham. Hech narsa yo'qolmaydi —
    kamon yurganda baribir otmaydi (`AttackOnTheMove = No`).
  - Skill ishlatilsa — **to'xtaydi**, va quvish **tugaydi** (pauza emas):
    o'zidan qochib dash qilgan o'yinchi fikridan qaytgan, keyin orqaga yurish
    dash uchun emas edi. Buning uchun quroldan nishon ham olinadi, aks holda
    keyingi kadr uni **yangi** buyruq deb o'qib, qahramonni qaytarib yuborardi.
  - Ikki yo'l `SkillBook` orqali gaplashadi (`lastCastFrame`, `lastAimedAt`) —
    `ScriptModule` o'z skriptini ko'rsatmaydi, va u `game` moduli.
- **Fullscreen ishlaydi — F11 bilan, joyida** (`glfwSetWindowMonitor`).
  - **`restart()` ishlatilmaydi.** U kontekstni buzib qayta quradi, va bu yerda
    `destroy()` chaqiradi — ya'ni sozlamalar menyusidan ekran o'lchamiga tegish
    **o'yinni tugatardi** (yozilganidan beri shunday edi, F11 buni ko'rinadigan
    qildi).
  - **Oyna o'lchamini o'zgartirmaydi**, faqat monitorga ko'chiradi. Sababi
    pastdagi tuzoq: engine'ning GUI'si o'z oynasi o'lchami o'zgarishidan omon
    chiqmaydi. Ekran rejimi o'yin uchun almashadi — bu eski "start'da fullscreen"
    xulqining aynan o'zi.
  - Shu sababli **Resolution — keyingi ishga tushirish sozlamasi** (menyu shunday
    deb yozadi), Fullscreen esa darhol.
- **Tuman — geometriyaning xossasi emas, xaritaning surati** (`client3d/FogMap`).
  Ilgari har hujayra o'z yorqinligini olib, uni butun plitkasiga tekis bo'yardi —
  natijada pol 10 birlikli kvadratlarga bo'linardi. Silliqlash bor edi, lekin
  **noto'g'ri o'lchamda** ishlardi.
  - **Tuman = tekstura.** O'lchami `TextureSize` (INI, standart 256) — plitka
    o'lchamiga hech qanday aloqasi yo'q. Har kadrda har teksel **o'z nuqtasining**
    yorqinligini so'raydi (`Discovery.lightAtPoint`), karta esa tekseller orasini
    o'zi to'ldiradi (Bilinear) — chekka piksel o'lchamida silliq bo'ladi.
  - **Interpolatsiya smoothstep** bilan: to'g'ri chiziqli og'irliklar uzluksiz,
    lekin **qiyaligi** uzluksiz emas, va har hujayra chegarasidagi qiyalik
    o'zgarishi kvadratlar kabi ko'zga tashlanadigan burma beradi.
  - **Proyeksiya, parda emas.** Birinchi urinish xarita ustiga tekis quad yopardi.
    Tekis parda dunyo bilan **faqat bitta balandlikda** mos tushadi: kamera 55°
    burchakda, ya'ni har bir dunyo birligi balandlik uchun qiymat 0.7 birlik
    siljiydi. Polda ossa — tosh ustidagi qopqoqlarning yarmi yoritilgan qolardi;
    devor balandligida ossa — devorning **pastki yarmi** boshqa yorqinlikda
    chiqardi. Endi relyefning **o'z materiali** teksturani dunyo `x`/`z` bo'yicha
    o'qiydi (`MatDefs/duke/FoggedTerrain.frag`) — `y` umuman ishlatilmaydi, ya'ni
    devorning tepasi ham, poyi ham bitta qiymat oladi va devor bo'ylab gradient
    chiqadi. Bu `client3d` dagi **birinchi va yagona shader**; jME'ning
    `GLSLCompat.glsllib` bilan bitta manba GLSL100 dan GLSL310 gacha ishlaydi.
    Yoritish (bitta quyosh + tekis ambient) material parametri sifatida beriladi —
    engine'ning light protokolini gapiradigan shader butun lighting quvurini
    ko'chirish bo'lardi, bitta chiroqli sahna uchun.
  - `TerrainScene` endi **faqat cull qiladi**, hech narsani bo'yamaydi;
    `TileSource` bitta `piece()` metodiga qisqardi (12 pog'onali material narvoni
    va qora "qopqoq" quadlar o'chdi).
  - **Cull har bo'lak uchun, o'z joyi bo'yicha** (`Discovery.hiddenAt`) — bo'lak
    qaysi katakka "yozilgan" bo'yicha emas. Bular boshqa joylar: devor ikki katak
    orasidagi chiziqda turadi, tosh ustidagi qopqoq esa o'zi qurilgan xonaga
    diagonal bo'lishi mumkin. Katak bo'yicha cull yoritilgan xona yonidagi devorni
    o'chirib yuborardi — yorug'lik tugaganda devor xiralashish o'rniga **yo'qolardi**,
    va bu tuman emas, sahnadagi xato bo'lib ko'rinadi. Cull chetlanishi 1 katak
    (`CULL_MARGIN`): tuman katak markazlari orasida chizilgani uchun.
  - **Uch qatlam ravshanligi INI'da**: `UnseenPercent` / `RememberedPercent` /
    `VisiblePercent`. Xotira darajasi endi devorga ham **o'z joyi bo'yicha**
    qo'llanadi: bir marta ko'rilgan devor xira bo'lib turadi, yo'qolmaydi. Fon
    rangi aynan `Tint` (ilgari `Tint × 0.55` edi) — xarita ichidagi ochilmagan joy
    va xaritadan tashqarisi bir xil bo'lsin, aks holda xarita chegarasi
    to'rtburchak bo'lib chizilardi.

---

- **Har chuqurlik boshqacha ko'rinadi** — model to'plami, rangi va tumani almashadi.
  Ikkita to'plam, ikkalasi ham KayKit (CC0): **Dungeon** (ishlangan tosh) va
  **Forest** (tuproq pol, devor o'rnida daraxt qatori).
  - **Ko'rinish, boshqa hech narsa emas.** Xona joylashuvi, dushman turlari, hamma
    son o'zgarmaydi. `DungeonThemeTest` bitta seed'ni ikki xil to'plamda o'ynab
    checksum'larni taqqoslaydi — aynan tuman uchun yozilgan testning shakli.
  - **Tanlov INI'da:** `DungeonThemes Order` chuqurlik→to'plam tartibini beradi,
    `WhenExhausted` esa ro'yxat tugagach `Repeat` (aylanadi) yoki `Last` (oxirgisi
    qoladi) deydi.
  - **Ohanglar** (`DungeonTone`) — bitta to'plam ichidagi kichik farq: boshqa pol
    naqshi, boshqa devor, yoki bir xil toshning sovuqroq quyilishi. Qaysi biri —
    **seed va chuqurlikdan** deterministik. `Math.random` yo'q.
  - **Yangi to'plam = INI + modellar.** Java yozilmaydi.
- **Sim → klient simi** — to'plam nomi status kanalida (`|look=Ruins,Fallen`).
  - Snapshot — sinxronizatsiya nuqtasi: klient yangi kartani va yangi to'plamni
    **bitta kadrda** biladi. Yon kanal (volatile maydon) bir kadr adashishi mumkin
    edi, va bu aynan ko'rinadigan xato bo'lardi — eski kit bilan qurilgan qavat.
  - `Visuals.theme(nom, ...)` — klient har bir ko'rinishni **launch'da** oladi va
    faqat "hozir qaysi biri" deb aytiladi. Klient nomni bo'laklarga ajratmaydi:
    "Ruins,Fallen" — unga bitta narsa; uni to'plam va ohangdan yasash o'yinning
    o'z ishi.
- **Ikkita o'lchov paketlardan chiqdi, taxmin qilinmadi** (Faza 0 da o'lchandi):
  - **`WallTileSize`** — pol 4 birlik, o'rmon "devori" (daraxt) esa **3.2**:
    paketlar bir modulda chizilmagan. Pol raqami bilan masshtablansa daraxt
    o'zidan kattaroq chiqadi.
  - **`WallLift` / `WallShift`** — paketlar devor pivotini har xil qo'yadi.
    Dungeon paketida devor markazda, ya'ni yarmi yer ostida qoladi.
  - **Zinaning ko'tarilishi = uzunligi**, quti balandligi EMAS. Modulli zina
    bitta katakni to'ldiradi va tepadagi qavatga ulanadi — shuning uchun ikkalasi
    bir xil son, va u modelning o'z izidan olinadi. Quti balandligi boshqa narsa:
    zinada panjara bo'ladi, panjara esa maydonchadan baland turadi. KayKit zinasi
    roppa-rosa 4 ga ko'tariladi, lekin qutisi **5.1** — quti bo'yicha
    masshtablanganda zina 4/5 ga siqilib, tepa qavatdan bir enlik pastda
    tugardi, va orada ko'rinadigan bo'shliq qolardi.
  - **Zinaning markazi kataknining markaziga.** Paket pivotni qayerga qo'ygani —
    uning o'z ishi: KayKit'niki pastki zinaning etagida, o'rtasida emas. Pivot
    bo'yicha qo'yilgan model yarim katak siljib, tushish joyi ustida osilib
    qolardi. Markaz `getCenter().x/z` dan olinib, **avval burilib**, keyin
    ayriladi — siljish modelning o'z o'qlarida.
  - `DungeonTilesTest` har bir to'plamning har bir bo'lagini yuklab, o'lchamini
    va devor yerga tegishini tekshiradi — ro'yxat INI'dan olinadi, ya'ni yangi
    to'plam ta'riflangani uchun qoplanadi.
- **`OwnMaterials`** — paket o'z ranglarini olib kelganda ularni saqlash.
  - Teksturasiz paket (MTL'da har bo'lak o'z rangini aytadi) uchun: bitta umumiy
    "skin" ularning hammasini yassi kulrangga aylantirardi.
  - Lekin materiallar **fog'ni o'qishi shart**, aks holda bo'laklar yoritiladi va
    hech qachon qorong'ilashmaydi. Shuning uchun ular saqlanmaydi — **qayta
    quriladi**: har bir material uchun bitta fogli material, rangi o'zinikicha.
  - Atlasli to'plamda umumiy skin **rasm bo'yicha** kalitlanadi, tema bo'yicha
    emas. Tema har doim ham bitta atlas emas: o'rmonning poli zindon atlasidan,
    daraxti o'rmon atlasidan — bitta skin ikkalasini kiyintira olmaydi va qaysi
    bo'lak birinchi yuklangan bo'lsa, o'shaning rasmi hammasiga tushardi. Daraxtlar
    shuning uchun oq poyada kulrang qo'ziqorin bo'lib chiqqan edi.
- **Devor sirtmi yoki narsami** — to'plam aytadigan yagona "o'lchov bo'lmagan" gap.
  - **Sirt** chegarada turadi: har yuzga bitta bo'lak, har qavatga bitta qator.
    Bir katak qalinlikdagi tosh ikki tomondan devorlanadi — tosh uchun to'g'ri,
    chunki toshning ikkita yuzi bor va ikkalasida ham turish mumkin.
  - **Narsa** uchun bularning hammasi noto'g'ri. Daraxtning bitta tanasi bor:
    ikki yuz chizilganda **bitta daraxt ikki marta** chizilardi — biri toshning
    etagida, ikkinchisi uning tomida, orasida qopqoq bilan. Ikki qavat esa yana
    ikki marta, ustma-ust. `WallFillsRock = Yes` — bo'lak **har tosh katagiga
    bitta**, uning o'rtasida, yonidagi eng past poldan eng balandigacha
    o'stirilgan holda chiziladi. Bitta daraxt, tosh qanchalik baland bo'lsa
    shunchalik katta.
  - Har katakda bitta bir xil daraxt — bog', o'rmon emas: ko'z avval qatorni
    ko'radi. `WallClump` / `WallSpread` / `WallVariety` — halqa bo'ylab bir
    nechta, har biri boshqa hajmda va boshqa tomonga qaragan. Toshni to'ldiruvchi
    bo'lak o'z markazi atrofida sochiladi va katagidan chiqmaydi; chegarada
    turgani (tayanch devor) esa **chiziq orqasiga** o'z radiusicha suriladi —
    ochiq yerga shoxi osiladi, tanasi emas.
  - Sochilish **tasodifiy emas**: joy hashidan. Har qayta qurishda o'zgaradigan
    o'rmonni hech kim yodlab ololmaydi (`TerrainScene.steady`).
- **Maxluqlar hamma to'plamda o'zimizniki** — `DungeonThemeMonster` mexanizmi bor
  va ishlaydi (blok o'qiladi, ko'rinish almashadi, simulyatsiya raqami qimirlamaydi),
  lekin hech bir to'plam undan foydalanmaydi. Sci-Fi qavati uchun paketdagi
  animatsiyali robot sinab ko'rildi va **tashlab yuborildi**:
  - FBX→glTF konvertatsiyasi bu rigni ikki xil o'lchamda beradi — ikkita terili
    (skinned) bilak-qo'l bir birlikda, qolgan qattiq bo'laklar yuzdan birida,
    o'rnini uchta tugunga osilgan `scale = 100` bosadi. Spetsifikatsiyaga amal
    qiladigan ko'ruvchida to'g'ri; jME'da esa **qo'llar to'g'ri, tanasi yo'q**:
    bo'g'inga mahkamlangan mesh `attachnode`ga tushadi, `attachnode` esa armature
    o'lchamini meros olmaydi. Ekranda ko'ringani — havoda uchayotgan bir juft qo'l.
  - **O'lchandi, taxmin emas:** jME'da qo'llar `scale (100,100,100)` bilan 1.65
    birlik, qattiq bo'laklar `scale 1` bilan 0.015 birlik chiqadi.
  - Ikkinchi, alohida nuqson: Blender bo'g'in uchlari uchun bo'sh `_end` tugunlar
    beradi, jME esa bir bo'g'inga **faqat bitta** bo'sh bola saqlaydi
    (`GltfLoader.findChildren`, `jw.attachedSpatial = s` — qo'shmaydi, o'rnini
    bosadi). Besh bo'g'inda ikkitadan bo'la bor edi, ya'ni bosh, oyoqlar va
    boldirlar — o'lcham to'g'irlangandan keyin ham yo'qolgan bo'laklar.
  - Bir nechta tuzatish (uniform bake, tugunlarni ko'chirish, uchlarni o'chirish)
    o'lchamni to'g'irladi, lekin modelni yaxlit chiqara olmadi. To'g'ri yechim —
    riggni `.blend` dan Blender orqali qayta eksport qilish; Blender yo'q.
  - Yo'lda bir narsa tuzatildi va **qoldi**: `AnimationsFrom` endi **faqat
    aytilganda** qarz oladi. Ilgari themed maxluq umumiy kutubxonaga tushardi, va
    model o'z klipini o'ziga ko'chirsa treklar noto'g'ri skeletga bog'lanardi.
- **Pol yuzasi y=0 da** — birlik y=0 da turadi, yerga chiziladigan hamma narsa ham
  (tanlov halqasi, buyruq metkasi). Kenney plitkasi tekis tekislik, ya'ni yuzasi
  aynan nolda edi; yangi to'plamlarning plitkasi qalin, va halqa pol ostida
  qolardi. Endi plitka **o'lchanadi** va yuzasi nolga tushiriladi — ya'ni to'plam
  jo'natilgani uchun to'g'ri, INI'da raqam yo'q.


### 8.x San'at oldindan o'qiladi — yuklash ekrani

- **Muammo:** birinchi maxluq maydonga chiqqan kadrda klient 9 MB model
  (`Imp.glb`), 7.6 MB animatsiya kutubxonasi (`UAL1_Standard.glb`) va 1.4 MB
  tekstura o'qirdi — hammasi **chizuvchi oqimda**, o'sha kadrda. FPS 20-30 ga
  tushardi, keyin yana tiklanardi, keyin keyingi tur maxluqda takrorlanardi.
- **Yechim:** `Preload` (`client3d`) — `Visuals`dan o'yin so'raydigan **hamma**
  faylni chiqaradi: modellar, animatsiya kutubxonalari, teksturalar, to'plam
  bo'laklari, tovushlar. Har biri **bir marta** (oltita maxluq bitta kutubxonani
  bo'lishsa, u bir marta o'qiladi). Mavzular ham kiradi — 10-qavatda kiyiladigan
  model 10-qavatda emas, hozir o'qiladi.
- **Ochiq kamchilik: zinapoya ro'yxatda yo'q.** `Preload.plan` to'plamdan
  pol/devor/burchakni oladi, `Tileset.getStairs()` ni esa emas — u qavatlar
  bilan birga qo'shilganda unutilgan. Ya'ni birinchi zinapoya ko'ringanda model
  chizuvchi oqimda o'qiladi. Bitta qatorlik tuzatish, ataylab keyinga
  qoldirilgan: asset ko'chirish refactori bilan aralashmasin.
- **Ikki bosqich, va ikkinchisi asosiy:**
  1. *O'qish* — alohida oqimda (`duke-art`, daemon). jME asset manager buni
     ko'taradi: parsing umumiy keshga tushadi, sahna grafiga tegilmaydi. Shuning
     uchun yuklash ekrani 60 FPS'da chiziladi va polosa siljiydi.
  2. *Kartaga berish* — faqat chizuvchi oqimda bo'ladi: `renderManager
     .preloadScene(...)` bilan mesh/tekstura videoxotiraga, shader kompilyatsiya
     qilinadi. Kadr boshiga bittadan. **Faqat o'qilgan fayl birinchi ko'rinishda
     xuddi ilgarigidek to'xtatadi** — muhim yarmi shu.
- `LoadingOverlay` — `MenuOverlay` bilan bir xil materiallar; polosa bitta quad
  (kadrda yangi obyekt yaratilmaydi), tagida o'qilayotgan fayl nomi.
- `Screen.LOADING` — Play bosilgach shu holatga o'tiladi, simulyatsiya **yuklash
  tugagandan keyin** boshlanadi.
- Yo'lda: `Visuals` xaritalari `LinkedHashMap` (o'yin e'lon qilgan tartib =
  polosa yuradigan tartib), `animationLibraries` `ConcurrentHashMap`.

### 8.y Dash devordan o'tadi, lekin faqat ko'rilgan yerga

- **Simulyatsiyada** (`SkillBook.dashEnd`): endi yo'ldagi hech narsa to'xtatmaydi
  — devor ham, maxluq ham. Yoy **oxiridan orqaga** yuriladi va u nishonga yoki
  undan berigi birinchi bo'sh nuqtaga tushadi. Ya'ni ustidan o'tadi, lekin
  **ichiga tushmaydi** (markazi toshda turgan birlik — bir kunlik bug).
- **Klientda** (`Hotkeys.Aim.OPEN_GROUND`): nishon toshda yoki **hech qachon
  yoritilmagan** (`Discovery.State.UNSEEN`) katakda bo'lsa, klik hech narsa
  yubormaydi. Bir marta yoritilgan, keyin unutilgan joy — mumkin: u yerda nima
  borligini o'yinchi biladi.
- Ikkala shart ham klientniki, chunki ikkalasining javobi ham unda: xaritaning
  shakli bor, **o'yinchi nimani ko'rganini esa faqat u biladi** — bu ekran
  haqidagi fakt, dunyo haqidagi emas, va simulyatsiya uni ushlab turishi xato
  bo'lardi.
- `SkillEffect.Aim.GROUND` → `OPEN_GROUND`.

### 8.z O'q endi yoritmaydi

- **Sabab:** `Discovery.reveal` o'yinchining **hamma** birligi atrofida xaritani
  ochardi. O'q ham birlik, u ham o'yinchiniki — demak har otilgan o'q xonani
  kesib o'tarkan yo'lini yoritib ketardi. Yoy fonarga aylangan edi.
- **Yechim:** `reveal(..., String eyesOf)` — faqat `Visuals.discoveredBy` aytgan
  shablon ko'radi. Bu yangi qoida emas: hujjat boshidan "kimning ko'zi" deb
  yozilgan edi (radius o'sha shablonning `VisionRange`i), kod esa hammaga
  ochardi. `null` = hammasi ko'radi (RTS uchun eski xatti-harakat saqlangan).

### 8.aa Xona birga jang qiladi, tiqilgan narsa to'xtaydi

Bir kadrda ko'ringan to'rt nuqson; hammasi `dungeon` va `client3d` da hal bo'ldi.

- **Uchinchi qavat bossi ham otadi.** `Necromancer` — `Revenant`ning kattasi, va
  endi uning otishi ham kattasi: `Bow { Projectile = GreaterFireball }`,
  `EyesOnly`, `AttackRange = 56`. Shar model kattaligiga yarasha kattaroq, lekin
  kodda emas — `DungeonProjectile GreaterFireball` + `DungeonEffect
  MageFireGreater` (OrbSize 2.6, Particles 54, LightRadius 70). Test
  `everyThrowerActuallyThrowsSomething` shablonlardan o'qiydi: `Bow` ko'targan
  har bir tur haqiqatan ham o'z snaryadini uchiradi va u qahramonga yetadi.

- **Xonadagi maxluqlar bir-birini chaqiradi.** `MonsterKind.alertRadius`
  (`AlertRadius`, standarti 70). Ko'zi hech kimni ko'rmagan maxluq atrofiga
  qaraydi: o'ziniki, tirik, **quroli nishon olgan** va **oradan tosh o'tmagan**
  bittasi jang boshlagan bo'lsa, u ham boshlaydi. "Jang qilyapti" degani
  brainning ichki holati emas, `WeaponUpdate.isAttacking()` — ya'ni hech qanday
  hodisa, bayroq yoki ro'yxat kerak emas, har kim shunchaki qaraydi.
  `SightLine.clear` shuning uchun bor: aks holda bitta xonadagi jang devor
  orqali qo'shni xonani bo'shatib yuborardi, va bu almashtirgan navbatdan
  yomonroq bo'lardi.

- **Tiqilib qolgan narsa joyida to'xtaydi.** Sabab `core` da emas edi: miyalar
  buyruqni ham soat bo'yicha, ham yuruvchi to'xtagan zahoti qayta berardi, har
  bir buyruq esa `MoveUpdate` ning "yaqinlashmayapti" hisoblagichini nolga
  qaytaradi — ya'ni ikki soniyalik voz kechish tekshiruvi hech qachon oxiriga
  yetmasdi. Yangi `Chasing.worthReplanning` qoidasi: quvilayotgan narsa **yarim
  katakdan ko'p siljisagina** qayta yo'l quriladi. O'lchandi: koridorda tanaga
  tiqilgan qahramon avval 150 kadrning **150 tasida** "yuryapti" holatida edi
  (oyoqlari yuradi, o'zi qimirlamaydi), endi **0 tasida**. Narxi hujjatlashtirdi:
  yo'lini to'sgan tana ketib qolsa, u quvlayotgani siljigunicha kutadi —
  jangda bu bir lahza.

- **Bora olmaydigan joyga buyruq endi ishlaydi.** Toshga, zinasiz balkonga yoki
  berk xonaga bosilgan klik jimgina hech narsa qilmasdi: qidiruv yo'l topmaydi va
  qahramon turaveradi. Endi klient `Destination.asCloseAsHeCanGet` bilan
  qahramon turgan katakdan **toshqin (BFS)** yuborib, klikka eng yaqin bora
  oladigan katakni tanlaydi — bora olsa klik **aynan o'zi** qoladi (katak
  markaziga tortilmaydi), aks holda buyruq ham, metka ham shu yaqin joyga
  tushadi. **Faqat tosh** to'siq hisoblanadi, eshikda turgan maxluq emas: tanalar
  ketadi, va klik payti yo'lda kim turganiga qarab buyruqni qisqartirish
  tuzatilayotgan nuqsondan battar bo'lardi.

### 8.ab Panel chizilgan emas, bo'yalgan — 9-slice

Panel butunlay tekis rangdan chizilardi: tepada yorug' chiziq, ostida to'qroq
chiziq, va ko'zdan toshni "o'qib olish" so'ralardi. Natija ozoda, ozoda esa
zindon mebeli bo'lishi kerak bo'lgan narsa emas. Endi qirralari Kenney Fantasy
UI Borders (CC0) o'yma ramkalari bilan bo'yaladi.

- **Qoida: rasm RAMKA, hech qachon maydon emas.** Uyaning ichi avvalgi gradient
  bo'lib qoladi, rasm — shaffof fondagi oq chiziq — ustiga qo'yiladi va
  bo'yaladi. O'yilgan panel aslida shu: soyalangan yuza + bo'yalgan qirra. Xuddi
  shu to'plamda har bir ramkaning to'ldirilgan varianti ham bor; uni ishlatish
  uyani bitta tekis rangga aylantirardi, ya'ni avvalgisidan yomonroq.

- **9-slice `NineSlice` da, jME'da bunday narsa yo'q.** Tekshirildi:
  `com.jme3.ui.Picture` oddiy kvadrat, `Common/MatDefs/Gui` oddiy teksturali
  shader, Nifty/Lemur bog'liqlik emas. Shuning uchun mesh o'zimizniki: 4×4 = 16
  vertex, 9 kvadrat, 18 uchburchak, bitta draw call. `Inset` — rasmning necha
  pikseli burchak; o'sha piksellar hech qachon cho'zilmaydi, faqat qirraning
  o'rtasi takrorlanadi. Shu sababli bitta 48-pikselli fayl 22-pikselli chipni
  ham, 190-pikselli barni ham ramkalaydi.

- **Ikki burchak o'z joyiga sig'masa, kvadratligicha qisiladi.** 22-pikselli chip
  48-pikselli rasmdan 32 piksel burchak so'raydi. Har o'q o'z chegarasiga
  qisqartirilsa burchak 16×5 bo'lib chiqadi — ya'ni cho'zilgan burchak, aynan
  qochilayotgan nuqson. Ikkala o'q bitta koeffitsiyent bilan qisqaradi.

- **Ajratuvchi — istisno.** U qat'iy uzunlikdagi naqsh (gorizontal inseti
  kengligining yarmi), demak to'qqizga bo'linmaydi: butun holicha chiziladi,
  tik qo'yiladi va band o'rtasidan aks ettiriladi. Burilish **geometriyada emas,
  UV'da**: to'g'ri burchak float'da yuz millionchi ulushgacha to'g'ri, va ikki
  pikselli chiziq shuncha siljish bilan namunalansa xiralashadi — ko'rinmaydigan
  xato ko'rinadigan blur beradi. Qaysi burchak rasmning qaysi burchagini
  so'rashini almashtirish aniq va bepul.

- **Hammasi INI'da:** `DungeonSkin <qism>` bloklari (Minimap, Portrait, Slot,
  Gauge, Chip, Divider) — `Texture`, `Inset`, `Scale`, `Tint`; yo'l esa
  `DungeonHud Panel` dagi `SkinFolder` bilan qo'shiladi, `IconFolder` kabi.
  Klientga `Visuals.panelSkin(PanelSkin)` orqali boradi — `MenuStyle`, `Fog`,
  `Tileset`, `SoundBank` yurgan o'sha chok.

- **Zaxira: bo'yoq qatlam, bog'liqlik emas.** Blokni o'chirsangiz o'sha qism
  avvalgidek chiziladi; hammasini o'chirsangiz panel avvalgi panel. Fayl
  topilmasa `iconTexture` naqshi ishlaydi — bir marta logga yoziladi va
  o'yilgan variant chiziladi. Panelni tanimaydigan nom ham logga yoziladi va
  o'tkazib yuboriladi (`Main.panelSkin`).

- **Holat saqlandi:** bo'yalgan uya qulflanganda rimi ham o'chadi. Aks holda
  geroy ura olmaydigan yagona uya butun panelning eng yorqin narsasi bo'lardi.

- **Masshtab bilan ta'siri o'lchandi.** Panel 1180 pikselda chizilib, oyna
  kengligiga qarab 0.55…1.30 masshtablanadi, ya'ni burchaklar ham masshtablanadi
  (WC3 ham shunday). Eng kichikda ekran piksellarida: minimap 10.1, portret 6.6,
  uya 6.05, chip 3.0, bar bezeli 1.65. Hammasi tanib olinarli — `double/` oilasiga
  o'tish kerak bo'lmadi. Ikkala chekkada skrinshot bilan ko'z bilan tasdiqlandi.

### 8.ac Panel maketga to'liq keltirildi

Bo'yoq qo'yilgandan keyin panel maketning **joylashuvi** bilan farq qilardi.
Endi farq yo'q: chapda minimap va yonida to'rtta buyruq tugmasi, portret oltin
romda va ostida daraja nishoni, o'rtada ismi/unvoni/barlari va 2×2 ko'rsatkich
to'ri, keyin narsalar to'ri, mahorat ustuni, chekkada chuqurlik.

- **Buyruq tugmalari (A/S/D/F) — bosiladigan ham, chiroq ham.** Bosiladigani
  ma'lum edi; chiroq bo'lgani muhimroq chiqdi. Tugmalarni bosib o'ynaydigan odam
  yo'q — sichqoncha va klavish har doim tezroq — shuning uchun ularni panelda
  ushlab turadigan narsa **ayta oladigani**: buyruq yetib bordimi, qahramon hali
  yuryaptimi, narigi burchakdagi skelet kelyaptimi.

  Shuning uchun har biri bir **holat**ning chirog'i ham, va to'rttadan aynan
  bittasi yonib turadi: `Yur` / `Hujum` / `To'xta` / `Himoya` — qarang `Doing`.
  Holat miyadan emas, **modullardan** o'qiladi, shuning uchun bir xil to'rt so'z
  qahramonni ham, skeletni ham, keyin qo'shiladigan har qanday narsani ham
  tasvirlaydi. Jang yurishdan ustun: nishonga qarab yurganda ham `Hujum` yonadi,
  chunki buyruq hujum edi — yurish esa uni bajarish usuli.

  **To'rtinchi tugma shu sababdan teskari bo'ldi, va shundan yaxshi chiqdi.**
  U `HoldGround` — "joyingda tur, hech kim bilan urishma" — edi, va bu aslida
  **To'xta**ning ma'nosi: yurishni tashla, nishonni tashla, hech narsa boshlama.
  To'rtinchisi endi **Himoya** — nomi yo'q bo'lgan oddiy bo'sh holat: turadi,
  lekin yaqinlashgan har kimga o'zi tashlanadi. Ikkita tugma holatni **qo'yadi**,
  bittasi uni almashtirmaydi: har biri o'zi qo'yadigan holatning chirog'i, va
  "narigisi" chiroq ko'rsata oladigan holat emas. To'xta o'yinchi boshqa narsa
  xohlashi bilanoq o'chadi — aks holda u kirib chiqib bo'lmaydigan holat bo'lardi.

  **Qator tanlangan unitga tegishli, qahramonga emas.** O'ziniki bo'lmagan jon
  nima qilayotganini ko'rsatadi va buyruq qabul qilmaydi: tugmalar xiralashadi,
  klavish ham, klik ham rad etiladi — ishlamaydigan yonib turgan tugma umuman
  yo'q tugmadan yomon. Lekin xira ≠ bo'sh: skeletning niyatini paneldan o'qish
  o'zinikini o'qish bilan barobar qimmatli, va o'yinchi klik qilishdan oldin
  bilmoqchi bo'ladigan narsa aynan shu.

  **Diqqat:** o'yin A/S/D ni egallagani uchun kamera endi strelkalar bilan
  suriladi; pastdagi yordam qatori buni o'zi bilib yozadi.
- **`Orders`** — buyruq bilan uni bajaradigan miya o'rtasidagi yagona ko'prik.
  Buyruq handler'ida o'yinchi raqami bor, miya esa obyektdagi modul; engine
  skript modulini qaytarib bermaydi, bergan taqdirda ham o'yin engine
  modullarini titkilashi noto'g'ri bo'lardi. Sessiyada bir marta yasaladi va
  `LootBag` kabi ikkala uchiga beriladi.
- **Ushlab turish qanday ishlaydi.** Nishonni "tanlamaslik" yetarli emas edi:
  `WeaponUpdate` nishonni o'zi topadi va **o'sha chaqiruvda otadi**, ya'ni
  tashqaridan faqat kech bo'ladi. Shuning uchun miya har kadrda `holdFire()`
  deydi. Buyurilgan nishon bundan mustasno — "boshlama" degani "eshitma" emas.
- **Narsalar to'ri = `LootBag`.** Yangi mexanika emas: sumka allaqachon bor va
  barlar ostidagi raqamlar aynan undan hisoblanadi. To'r shuni ko'rsatadi.
  Doim oltita uya — o'sadigan to'r har sandiqda o'ng tomondagi hamma narsani
  surib yuborardi, bo'sh uya esa ma'lumot: joy bor va qavatda hali qidiriladigan
  narsa bor. Ishlatib/tashlab bo'lmaydi; narsa topilgan zahoti ta'sir qiladi.
- **Yashil raqam.** `stat=` endi uchinchi maydon olishi mumkin: shu ko'rsatkichda
  **qarzga olingani** (kuchlar + topilgan narsalar). Faqat o'sadigan raqam
  "hozir topgan qiliching arzidimi?" degan savolga javob bermaydi, farq esa
  beradi — bu paneldagi yagona shu savolga javob beradigan son.
- **Testlar ko'z o'rniga.** `PanelLayoutTest` bloklarni sahna grafigidan
  o'lchaydi: ustma-ust tushmasligi, tartibi, to'rt xil oyna kengligida sig'ishi,
  va balandlik bo'yicha chiqib ketmasligi. Bu — maket qurilayotganda yo'l
  qo'yilishi mumkin bo'lgan har bir joylashuv xatosini ushlaydigan yagona
  tekshiruv, va u oyna talab qilmaydi. Ko'rmaydigani — **harflar**: jME
  `BitmapText` ga chizilmaguncha chegara bermaydi.

**Qarz:** `HeroPanel.java` 1727 → **2304 qator**. Kelishilganidek aytyapman:
2000 dan oshdi. Ichida uchta mustaqil narsa bor (buyruq ustuni, sumka,
ko'rsatkich to'ri) va ular alohida sinfga chiqarilishi mumkin — lekin bu vizual
ish emas, shuning uchun qilinmadi.

### 8.ad Panel tanlangan narsani ko'rsatadi, doim geroyni emas

Panel har doim geroyni yozardi. Endi nimani tanlagan bo'lsang, o'shani.

- **Ikki yarim bir-birini ko'rmaydi.** *Qaysi* maxluq — klient bilади: tanlov
  ekran haqidagi fakt, va tanlovi bor simulyatsiya — ichida kamerasi bor
  simulyatsiya. *U nimaga arziydi* — simulyatsiya biladi: skeletning zarari uning
  shabloni × shu qavat ko'paytirgichi, klient esa shablonni umuman ko'rmagan.
  Render oqimidan simulyatsiya obyektlarini o'qish sekin yoki xunuk emas —
  shunchaki noto'g'ri.
- **Shuning uchun bitta buyruq:** `Watching` — hech narsa qilishni so'ramaydigan
  yagona buyruq. Dunyoda hech narsa o'zgarmaydi; u status qatori yoziladigan
  bitta raqamni suradi, status qatori esa snapshotning engine o'qimaydigan
  yagona qismi. Aynan shuning uchun tanlov umuman buyruq bo'la oladi. Har kadrda
  emas, **o'zgarganda** yuboriladi: sekundiga qirq marta "hali ham o'sha"
  hech kim o'qiy olmaydigan buyruq oqimi va hech kim qidira olmaydigan replay.
- **Klient chokidan:** `Hotkeys.onWatch` — `onChoose` bilan bir xil naqsh,
  "klient qaysi birini deydi, o'yin nima ekanini aytadi".
- **Endi dushmanni tanlash mumkin.** Ilgari faqat o'z birligi tanlanardi.
  Boshqasini bosish — uni **ko'zdan kechirish**; buyruq unga hech qachon
  yetmaydi (`selectedIds` faqat o'zinikini beradi), va halqasi yashil emas
  **qizil**: yashil halqa keyingi o'ng-klik bermaydigan buyruqni va'da qilardi.
  Guruhga ham qo'shilmaydi — "nima tanlangan" bir vaqtda ikki xil ma'no berardi.
- **Maxluq kartasi ataylab qisqa.** Skeletda o'yinchi yig'ayotgan tajriba yo'q,
  skill yo'q, sumka yo'q, daraja yo'q. Geroynikidan nusxa olib raqamlarini
  almashtirish — bu ikkinchi geroy yasash bo'lardi. Qolgani: qanchasi bor va
  qanchaga uradi. **Chuqurlik qoladi** — u qavatniki, tanlanganniki emas.
- **Panel bo'sh blokni chizmaydi, umuman chizmaydi.** Bo'sh uyalar va nolga
  tushgan bar "bu maxluqda bunaqasi yo'q" deb emas, "panel buzildi" deb
  o'qiladi. Blok yo'qolganda `layOut` teshikni yopadi.
- **Portretdagi figura ham almashadi** (`face=skull`): skelet kartasida
  kamonchi siluети panelning yolg'oni bo'lardi.
- **Bir tuzoq topildi va tuzatildi.** `GameSounds` darajani `rank` o'zgarishidan
  eshitadi; maxluq kartasida `rank` yo'q, ya'ni har bosishda "daraja oshdi"
  jiringlardi — bosганда bir marta, qo'yib yuborganda yana. Endi darajasiz karta
  uniki emas deb o'qiladi. Skrinshotda ko'rinmaydigan, o'yinda quloqni
  qoqadigan xato — shuning uchun alohida testi bor.

### 8.ae Kursor endi nima ustida turganini aytadi

Kursor operatsion tizimning oq strelkasi edi — u skelet ustida ham, devor
ustida ham bir xil narsani aytadi: hech narsani. Sichqoncha bilan o'ynaladigan
o'yin boshqaruvining yarmi "bu yerda o'ng-klik boshqa ma'noni beradi", va buni
**klikdan oldin** aytadigan yagona joy — kursorning o'zi.

- **Besh holat, klientniki:** `Point` (bo'sh yer, panel, menyu), `Friend`
  (o'ziniki), `Attack` (o'ng-klik urishadigan narsa), `Aim` (skill qurollangan
  va bu yerga tushadi), `Deny` (qurollangan, lekin bu yerga tushmaydi). Kursor
  ostida nima turgani — ekran haqidagi fakt, shuning uchun holatlar klientniki;
  rasmlar esa o'yinniki va `DungeonCursor` bloklaridan keladi.
- **Ustunlik tartibi ham qoida:** menyu hammasidan ustun; keyin qurollangan
  skill (ekrandagi eng baland gap — keyingi klik odatdagidek ishlamasligining
  sababi); keyin panel; keyin dunyo.
- **Ikki konvertatsiya jimgina xato beradi va ikkalasi ham testda qulflangan.**
  jME kursor rasmini **pastdan yuqoriga** saqlaydi — buferning oxirgi qatori
  rasmning birinchi qatori; teskari qilinsa kursor ag'darilgan bo'ladi va bu
  xato emas, "g'alati rasm tanlabdi" bo'lib o'qiladi. Va uning hot-spoti
  **pastdan** o'lchanadi (`height - yHotSpot` uzatiladi); teskari qilinsa har
  klik mo'ljaldan bir kursor bo'yi narida tushadi — bu ham xato emas, "o'yin
  aniq emas" bo'lib o'qiladi. INI'da esa uchi **yuqori-chapdan** yoziladi,
  ya'ni faylga qaragan odam qanday sanasa shunday.
- **Zaxira:** rasm topilmasa, turgan kursor qoladi (oq strelkaga qaytmaydi —
  jonzot ustidan o'tganda oqqa sakrash bittasi ikki holatga xizmat qilishidan
  yomonroq), logga bir marta yoziladi. Hech narsa nomlamagan o'yin tizim
  strelkasida qoladi.

**Bitta xato uchta shikoyat bo'lib ko'rindi.** `loadTexture(String)` rasmni
ALLAQACHON ag'daradi — tekstura OpenGL'da pastdan namunalanadi va bu klient
yuklaydigan qolgan hamma rasm uchun to'g'ri standart. Kursor esa tekstura emas,
u oynaga uzatiladi. Ikki ag'darish bir-birini bekor qilmaydi: kursor boshida
turadi VA ko'rinadigan strelka hot-spotdan ~45 piksel narida chiziladi — ya'ni
dushman ustida kursor almashsa ham, farqi qaralayotgan joydan uzoqda qoladi.
Endi `TextureKey(path, false)` bilan yuklanadi.

**Nega unit testlar ushlamadi:** hammasi xotirada yasalgan rasmdan boshlanardi,
xato esa undan OLDIN, yuklovchida edi. Yangi test fayldan boshlanadi — diskka
PNG yoziladi, `Cursors` uni haqiqatda qanday o'qisa shunday o'qiladi, va burchak
jME oynaga uzatadigan joyda qidiriladi. Eski xatoni qaytarib qo'yib tekshirdim:
test qizaradi.

**"Qaysi kursor, qachon" ham endi sof funksiya** (`Cursors.situationFor`, olti
fakt) va oltita testi bor. "Dushman ustida kursor almashmayapti" — bu shu
funksiya haqidagi gap, va uni sichqoncha surib emas, so'rab bilish kerak.

**Paket almashtirildi:** Kenney Cursor Pack 1.1 (CC0, litsenziyasi ichida).
Faqat *Outline* oilasi ko'chirildi, va bu did emas qaror: paketning ikkinchi
oilasi *Basic* — sof oq, konturisiz (o'lchandi: bitta rang). Oq kursor yorug'
pol ustida ko'rinmaydi, zindonda esa ikkalasi ham bor.

**Rang: bo'yash mumkin va `Tint` INI'da.** Chizmalar qora kontur ichidagi oq
shakl, va rang **ko'paytiriladi**, ustiga bo'yalmaydi: oq → rang, qora ×
nimadir → qora, chekkadagi kulrang → o'sha rangning to'qroq soyasi. Ya'ni
bo'yalgan kursor konturini saqlaydi — aynan shu uni qorong'i pol ustida ham,
mash'al yorug'ida ham o'qiladigan qiladi. Ustiga bo'yash konturni ham bosib
ketardi va chekkasi yo'q shakl qolardi. `0xFFFFFF` = "chizilganidek", va umuman
arifmetika qilinmaydi. Ranglar panelnikidan olingan: suyak / yashil / qon /
mash'al.

**Ikki o'lcham** (`default/` 32px, `double/` 64px) — almashtirish bitta so'z.

### 8.af Hech narsa tanlanmagan bo'lsa panel bo'sh

Panel doim kimnidir yozardi. Endi u tanlanganini yozadi, va hech narsa
tanlanmagan bo'lsa — hech kimni.

**Qoida (Warcraft'dagi kabi): uyalar — MEBEL, ichidagisi — MA'LUMOT.** Panel
shaklini umuman o'zgartirmaydi: portret romi, ikkita bar chuqurchasi, oltita
narsa uyasi, to'rtta mahorat uyasi va to'rtta buyruq tugmasi doim chiziladi.
Yo'qoladigani — ichidagisi: figura, ism, unvon, daraja nishoni, raqamlar,
ikonkalar. Klik qilganda shaklini o'zgartiradigan panel o'yinchini har safar
bosmoqchi bo'lgan narsasini qidirishga majbur qiladi.

Sumkaning oltita uyasi boshidan shunday ishlardi (nechta narsa bo'lishidan
qat'i nazar oltita) — mahorat va buyruq uyalari ham endi shunday.

- **`layOut` ro'yxatga aylandi.** Beshta blok doim bor, groove faqat qo'shni
  juftlar orasiga tushadi — bitta sikl, hech qanday shart.
- **Bo'sh uya boshqaruv emas:** klik qabul qilmaydi va kursor ostida yonmaydi.
- **Bo'sh kartada ham `look=` bor va bu majburiy.** Run boshlanganda hech narsa
  tanlanmagan bo'ladi, ya'ni klient o'qiydigan BIRINCHI qator — shu. Undan
  `look=` tushib qolsa, birinchi qavat noto'g'ri toshdan quriladi.
- **O'lgan narsa ham panelni bo'shatadi** (jasadni yozib turgan panel buzilgan
  panelga o'xshaydi), lekin **o'layotgan qahramon bundan mustasno** — u hali ham
  panel yozayotgan qahramon, va o'limning o'z ekrani bor.

**Diqqat — xulq o'zgardi:** run boshlanganda panel bo'sh, chunki hech narsa
tanlanmagan. Uyalar ko'rinadi, lekin ichi bo'sh; skill klavishlari esa
ishlayveradi, chunki ular tanlovga bog'liq emas. Agar run boshlanishida
qahramon avtomatik tanlansin desangiz — bu bitta qator.

**Kursor endi menyudan boshlab ishlaydi.** `showTheRightPointer()` `simpleUpdate`
ning oxirida turardi, menyu va yuklash ekrani esa undan oldin `return` qiladi —
ya'ni o'yinchi Play tugmasini tizimning oq strelkasi bilan bosardi, va bu uning
o'yindan ko'rgan birinchi narsasi. Chaqiruv yuqoriga ko'chdi.

### 8.ag Portret — rasm emas, kichkina sahna

Warcraft III ning portreti rasm emas: u qahramonning **o'z modeli**, yuziga yaqin
turgan kamera va o'z animatsiyasi. Shuning uchun u tirik ko'rinadi. Endi bizda
ham shunday — jME'ning `ViewPort` + `FrameBuffer` (render-to-texture) imkoniyati
bilan, yangi mexanizmsiz.

**Uch fayl, ikkitasi jME'siz:**

| Fayl | Nima | jME |
|---|---|---|
| `PortraitLook` | o'yin beradigan sozlama: kamera + holat→klip | yo'q |
| `PortraitMood` | portretning **soati va miyasi**: holat, klip, chastota | yo'q |
| `HeroPortrait` | kamera, FrameBuffer, preView viewport, model nusxasi | ha |

- **Alohida nusxa, hech qachon dunyodagi model emas.** Ikki sabab, ikkinchisi
  ko'rinmaydi: birinchisi — dunyodagi qahramon simulyatsiya qo'ygan joyda va
  yurgan tomoniga qarab turadi, bu portret emas. Ikkinchisi — **skinning
  materiali skeletning pozasini ushlab turadi**, ya'ni umumiy material bo'lsa
  ikkalasi har kadr bir-birining pozasini yozib ketardi. Dunyodagi birliklar ham
  aynan shu sababdan material bo'lishmaydi.
- **Tana bitta joyda quriladi** (`DukeRtsApp.buildBody`) — `createUnitNode` dan
  ajratildi. Aks holda portret "biroz boshqa" odamning portreti bo'lardi: boshqa
  tint, kamonsiz kamonchi, yoritilmagan model.
- **Fon shaffof.** Panelning o'z gradiyenti, oltin romi, burchak bezaklari va
  daraja nishoni joyida qoladi — maketdagi `.inner` radial gradiyent aynan fon
  bo'lib ishlaydi. Portret ular orasiga, z = 2.5 ga tushadi.
- **Yorug'lik portretniki.** Dungeon yorug'ligi tuman ostida va yarim qavat
  narida; portret o'z key/fill/ambient rigi bilan yoritiladi.
- **Bitta blok butun bestiariyni qamraydi.** `DungeonPortraits Everyone` —
  **tanlash mumkin bo'lgan har bir** maxluq oladigan portret;
  `DungeonPortrait <Template>` — boshqacha xohlagani uchun override (qahramonda
  ikkita holat monstrlarda yo'q: tayyor kamon va daraja bayrami). Darvoza —
  engine'ning o'z so'zi `selectable`: o'q, sandiq va bochkaning ham modeli bor,
  lekin ularning hech biri panel yozayotgan narsa emas.
- **Nom berilmagan holat maxluqning O'Z klipini kiyadi** — yurish idle'i turish
  uchun, o'z o'limi o'lish uchun. Monstr portreti aynan shu sababdan tekin:
  ikkala klip unda allaqachon bog'langan. Jang va yaralanish esa idle'ga qaytadi,
  hujum klipiga emas — hujum bitta zarba, aylantirilsa monstr soya bilan
  boksga tushadi (dunyo animatsiyasi shu xatoni bir marta to'lagan).
- **Kamera maxluq bo'yining ULUSHIDA o'lchanadi**, dunyo birliklarida emas —
  aynan shu bitta blokni butun bestiariyga yetkazadi. `Yaw = 0` — to'g'ri
  qarshisidan, chunki `Facing` modelning oldini `+X` ga allaqachon burgan.
- **Ramkada NIMA turishini `Show` to'g'ridan-to'g'ri aytadi** (bo'yning ulushi),
  masofa esa undan va linzadan hisoblanadi. Teskarisi — masofa + linza berib,
  kadrlashni ulardan chiqarish — har maxluq uchun ikkala raqamni birga sozlashni
  talab qiladi, va odam birinchi bo'lib qo'l uradigan narsa (yuzni kamroq
  egadigan kengroq linza) jimgina masshtabni kichraytiradi.
- **Balandlik = tepasi yerdan qancha baland**, qutisi qancha baland emas.
  Oyoqda turgan narsada ikkalasi bir xil, turmaganda esa yo'q: Stalker va Reaper
  modellari suzib yurgan poldan pastga cho'ziladi, ya'ni qutisi 3.05, o'zi esa
  2.17. Quti bo'yicha o'lchansa kamera aynan shu ikkitasida uchdan bir tana past
  qaraydi — hech kim tekshirmaydigan ikkitasida.
- **Beshta holat klientniki, kliplar o'yinniki.** Klient holat NIMA ekanini
  biladi (demak klip aylanadimi yo'qmi ham): `Calm`/`Fight`/`Hurt` aylanadi,
  `Dead`/`LevelUp` bir marta ketadi va oxirgi poza qoladi. **Kodda birorta klip
  nomi yo'q** — hammasi INI'da.

**★ Birinchi ko'rinishda topilgan kamchilik (ko'z, arifmetika emas).** Kamera
`Head = 0.86` da edi va ekranda **faqat peshana** ko'rindi. Sababi o'lchab
topildi: bu kit qahramonlarni **bosh bo'yining deyarli yarmi** qilib chizadi —
`head` bo'g'ini modelning 0.55 da, sochning tepasi 1.0 da. Ya'ni 0.86 peshananing
o'rtasi. Endi `Head = 0.74`, `Show = 0.64` — ramka 0.42 dan 1.06 gacha, ko'krakdan
boshning tepasidan bir oz yuqorigacha. **Bu raqamlar taxmin emas, o'lchov**:
modellar headless test bilan o'lchandi, keyin o'sha test o'chirildi va o'lchov
`PortraitCameraTest` ga aylandi.
- **Holat `getSnapshot()` dan.** `attacking()` → Fight, `healthFraction()` →
  Hurt, `ObjectDied` hodisasi → Dead, status qatoridagi `rank` o'zgarishi →
  LevelUp. Simulyatsiyaga bitta ham yozuv yo'q.
- **Kam HP jangdan ustun.** U o'yinning ko'p qismida jangda; o'n foiz jonda esa
  o'yinchi qochish yoki qolishni hal qiladigan bir necha soniyada — ramka aynan
  o'sha lahza uchun.
- **Kitda "charchagan" klip yo'q**, shuning uchun `HurtSpeed = 1.5` — tezlik uni
  san'atsiz aytadi.
- **O'lim ramkani kartadan uzoqroq ushlaydi.** O'lim maxluqni snapshotdan,
  snapshot tanlovdan, tanlov kartadan chiqaradi — hammasi u yiqilgan kadrda.
  Kartaga ergashgan portret o'ziga berilgan o'lim klipining bitta kadrini ham
  chizmasdi. Lekin ramka **faqat hech kimga qarshi** ushlanadi: keyingi bosilgan
  maxluq uni oladi, aks holda o'lik qahramon sessiya oxirigacha ramkada qolardi.
- **Chastota — shift, maqsad emas** (`PortraitFps = 24`). `advance()` vaqtni
  to'playdi va kadr qarzi yig'ilganda sarflaydi; oradagi kadrlarda viewport
  **o'chiriladi**, `RenderManager.renderViewPort` esa birinchi qatorda
  `if (!vp.isEnabled()) return;` qiladi — ya'ni ular tekin. Uzoq to'xtash bir
  qadamda sarflanmaydi (0.25s shift), aks holda model boshqa pozaga sakrardi.
- **Sahnani qo'lda qadamlaymiz.** Faqat viewport'ga ulangan sahna
  `SimpleApplication.update()` ga kirmaydi (u faqat `rootNode`/`guiNode` ni
  yangilaydi) — ya'ni animatsiya o'zi yurmaydi. Bu tuzoq emas, qulaylik bo'lib
  chiqdi: uni faqat chiziladigan kadrda qadamlash aynan chastota cheklovi.
- **FrameBuffer/viewport bir marta**, model esa faqat template o'zgarsa.
  Almashganda avval `detachAllChildren()` — sahna run'lar bo'ylab o'smaydi.
- **Zaxira yo'l hamma joyda.** Render-to-texture yo'q yoki xato bersa — siluet.
  Model yuklanmasa — siluet. Klip modelda bo'lmasa — `Calm` ga qaytadi va logga
  bir marta yoziladi. `Calm` ham bo'lmasa — portret o'chadi. Headless'da
  (`renderManager == null`) hammasi quriladi, ishlaydi, yopiladi va hech nima
  chizmaydi — ovozsiz mashina `SoundSink.SILENT` olgani bilan bir xil bitim.
- **Portret tanlovga ergashadi**, doim qahramonga emas: panelning qolgani
  (ism, HP, raqamlar) tanlangan narsani ko'rsatadi, portret esa boshqa odamni
  ko'rsatsa panel o'zi bilan o'zi ziddiyatga tushardi. Shu sababdan portret
  **har qanday template uchun** sozlanadi — skelet blokini yozsangiz u ham tirik
  portret oladi.

**Panel maketga keltirildi:** rom 112×130 → **126×150**. Siluet o'z 112'lik
kvadratida qoldi va kengroq romda markazlashtirildi — u qo'lda chizilgan
koordinatalar, kattalashtirilsa cho'zilib ketardi.

**Yo'l-yo'lakay tuzatilgan:** panel 640px oynada 2.4 piksel chiqib ketdi.
`MIN_SCALE` ni ko'chirish o'rniga masshtab endi **o'lchangan kenglikdan** ham
oladi (`scaleFor`): `min(o'qilishi mumkin bo'lgan, sig'adigan)`. Ilgari eng
kichik masshtab bir marta qo'lda tekshirilgan raqam edi va panelda biror narsa
kattalashsa jimgina buzilardi.

**Ko'p qahramon — endi haqiqatan INI'da.** `DungeonHero` bloki nomlanadigan va
takrorlanadigan bo'ldi (`DungeonHero Hero`), `HeroLook` ro'yxatga aylandi
(`settings.heroes()`), `Main` ular bo'ylab aylanadi. Ilgari u nomsiz blok edi va
maydonlari to'g'ridan-to'g'ri settings'ga yozilardi — ya'ni ikkinchi blok
birinchisini **jimgina** ustidan yozardi. `DungeonSkill <Hero> <Key>` allaqachon
qahramonini nomi bilan bilar edi, ya'ni ikkinchi qahramon endi: creatures.ini
bloki + `DungeonHero` + `DungeonPortrait` + to'rtta skill bloki, Java'ga
tegilmaydi.

**Ko'z bilan tekshirish kerak** (men ishga tushirmadim): kadrlash endi to'g'rimi,
yorug'lik yetarlimi, monstrlar — ayniqsa suzib yuradigan Stalker va Reaper —
ramkada qanday ko'rinadi, va `LevelUp = Spawn_Ground` g'alaba ko'rinishini
beradimi yoki paydo bo'lishga o'xshaydimi. Hammasi INI'dagi bitta so'z yoki raqam.

---

### 8.ah Yo'l ochiq bo'lsa yuradi, bo'lmasa turadi

Pirpirash ikki xil yo'ldan kelardi va ikkalasi ham `dungeon` da hal bo'ldi.

- **Sabab.** Lokomotor bir oyoqdan ikki soniyadan keyin voz kechadi, lekin **har
  bir yangi buyruq shu hisobni nolga qaytaradi**. Jangda qahramon doim harakatda,
  ya'ni miya har necha qadamda qayta rejalaydi — demak hisob hech qachon
  to'lmaydi va pirpirash chaqirilgan qadar davom etadi.
- **`WayAhead`** o'yinchi ko'rgan savolni beradi: **yonidan o'tadigan joy bormi?**
  Yo'q bo'lsa — turadi. Birinchi variant "oldimda tanami?" deb so'radi va ikkita
  to'g'ri testni buzdi: ochiq xonada lokomotor aylanib o'tadi, va o'tishi ham
  kerak. Farq tanada emas, **yonidagi devorlarda** — shuning uchun savol "chap
  yoki o'ngda bitta tana eni bo'sh joy bormi", o'lchami esa unitning o'z
  shablonidan.
- **Ikki holat.** Maxluq qahramonni quvganda (miya boshqaradi) va qahramon oddiy
  yurish buyrug'ini bajarayotganda. Ikkinchisida qayerga ketayotgani **eslab
  qolinadi**: yo'l ochilsa qayta so'ramasdan davom etadi.
- **O'lchov.** Tuzatishni olib tashlab qayta ishga tushirilganda: 150/150 kadr
  yurish holatida edi → **0/150**. Oddiy yurish buyrug'ida 50/50 → **0/50**.
- **Qotib qolmaydi.** Kutish ikki sababdan tugaydi: tana ketsa, yoki kutilayotgan
  nuqta endi **u bilan maqsad orasida bo'lmasa** — aks holda dushman orqasidan
  aylanib o'tsa, jon eski yo'nalishga qarab abadiy turib qolardi. Taymer emas,
  taqqoslash: taymer har necha vaqtda bir pirpiratadi, ya'ni tuzatilayotgan
  nuqsonning kichik nusxasi bo'lardi.

### 8.ai To'siq ustiga ham yurish buyrug'i beriladi

`Destination` faqat **toshni** o'qirdi. Bochka/ustun grid'ning *obstacle*
qatlamida turadi — klik katagi ochiq ko'rinardi, buyruq o'zgarishsiz ketardi,
keyin unitning o'z qidiruvi (u bochkani biladi) yo'l topolmasdi va qahramon
umuman qimirlamasdi. Endi mebel ham hisobga olinadi va u bochkagacha borib
to'xtaydi.

Bu qatlam **faqat qimirlay olmaydigan** narsalardan yig'iladi
(`GameLogic.refreshStaticObstacles` `isMobile()` ni tashlab ketadi), shuning
uchun eshikdagi skelet baribir buyruqni qisqartirmaydi. Buni tasdiqlaydigan eski
test aslida **engine yarata olmaydigan** holatni tekshirayotgan ekan — u ikkita
to'g'ri testga almashtirildi, biri jonzot hech qachon xaritaga singdirilmasligini
tirik o'yinda o'lchaydi.

Kursor ham shunga moslandi: **ochilmagan yer taqiq emas** — yurish buyrug'i
aimlanmaydi, xohlagan joyga beriladi va qanchalik yaqin borishini simulyatsiya
hal qiladi; ko'rilmagan tosh ustida taqiq ko'rsatish esa xaritani kursor orqali
o'qib berish bo'lardi.

### 8.aj Klikka javob: yurishga o'q uchlari, hujumga halqa

Buyruq belgisi — o'yinda o'yinchiga uning kliki bilan biror narsa sodir bo'lishi
o'rtasidagi lahzada javob qaytaradigan yagona narsa, ya'ni o'lchamidan ko'ra
ko'proq e'tibor talab qiladi.

- **Eski belgining so'nishi hech qachon ishlamagan.** Material'da `BlendMode` yo'q
  edi va u Opaque bucket'da turardi — har kadrda hisoblanayotgan alfa hech qayerga
  bormasdi. Doira 1.2 soniya o'sardi va **birdan o'chardi**.
- **Yurish** — Warcraft III ning uchta o'q uchi: 120° oraliqda, uchlari ichkariga,
  markazga yopiladi. Uchta raqam butun ishni qiladi va uchalasi ham noto'g'ri
  bo'lganda yomon ko'rinadi: **ease-out** (tekis tezlik mexanizmdek o'qiladi),
  **kech so'nish** (birinchi kadrdan so'nsa, o'qilmasidan yarmi ketadi) va
  **kichik burilish** (hech kim ko'rmaydi, yo'qligini hamma sezadi).
- **Hujum** — boshqa savol, boshqa rasm: u **kimgadir** beriladi, shuning uchun
  jon atrofida qizil halqa **ikki marta qattiq yonib-o'chadi**. So'nish emas,
  chunki ko'z **bo'shliqni** ilg'aydi; jang o'rtasida yumshoq so'nishni hech kim
  ko'rmaydi. Ikki marta: bittasi glitch'dek, uchtasi ogohlantirish chirog'idek.
- **Hech narsa o'yin ketayotganda qurilmaydi.** Bitta uchburchak mesh butun
  o'yinga; har belgining node/material'i bir marta yasalib qayta ishlatiladi;
  tugagani uzilmaydi, berkitiladi — sahna eng gavjum lahzada to'xtaydi.

### 8.ak Skill qayergacha yetadi — beshta shakl, bitta til

Cooldown taxminni qimmat qiladi, shuning uchun olib tashlanadigan narsa —
taxmin. Indikator uch savolga javob beradi: **qayergacha yetadi**, **shu yerdan
tegadimi**, va uchadigan narsa uchun **qaysi tomonga ketadi**.

Qaysi rasm chizilishi skill *nima qilishidan* emas, o'yinchidan **nimani
ko'rsatish so'ralishidan** kelib chiqadi — `SkillRange.Shape`:

| Shakl | Rasm |
|---|---|
| `AT_A_CREATURE` | halqa (`Range`); ichidagi jonni bosasan |
| `AT_A_SPOT` | halqa + kursor ostida portlash diski, masofa chetiga qisiladi |
| `DOWN_A_LANE` | yo'lak — o'qning haqiqiy uzunligi va eni, uchida o'q uchi |
| `AROUND_HIM` | bitta disk (`Radius`), o'zining atrofida |
| `ON_HIMSELF` | unga tegib turgan tor halqa: bu faqat unga |

**Raqamlar skillning o'ziniki** (`Range`/`Radius`/`Distance`) — INI'da ikkinchi
nusxa yo'q, va yangi qahramon indikator uchun bir qator Java ham talab qilmaydi.

**Indikator — o'lchagich, effekt emas.** Birinchi variant ochilib chiqardi va
sekin aylanardi (janr shunday qiladi) — ikkalasi ham noto'g'ri edi: o'rnashishini
kutish kerak bo'lgan o'lchagich skill uchun kerak bo'lgan soniya ulushini yeydi,
aylanadigani esa ko'zni masofaga emas o'ziga tortadi. Punktir chiziq ham xonaning
narigi chetida o'qilishni bekorga yo'qotardi. Hozir: **birinchi kadrda to'liq
o'lchamda, qimirlamaydigan, uzluksiz chiziq** — qolgan yagona harakat sekin nafas
olish, uni ham INI'dan o'chirish mumkin.

**Nishonsiz skillar qo'yib yuborilganda ishlaydi** (`W`, `R`): bosib turilganda
radius ko'rinadi. Aks holda ularning masofasini sarflamasdan ko'rishning iloji
yo'q edi. Tez bossangiz baribir ishlaydi, va faqat armed qilgan narsa otadi —
klaviatura bilan armed qilib sichqonchani qo'yib yuborsangiz hech narsa bo'lmaydi.

**Ikkita yangi skill turi** shu paytda qo'shildi, chunki beshta rasmning
ikkitasi ortida skill yo'q edi: `AREA_AT_SPOT` (o'yinchi qo'ygan nuqtaga
portlash — masofadan uzoqroqqa bossa rad etilmaydi, chetiga tortiladi) va
`SKILLSHOT` (yo'nalishga otiladi, yo'ldagiga tegadi, **tegmasligi ham mumkin** —
`STRIKE` ning teskarisi, uning o'qi quvib yetadi va hech qachon xato ketmaydi).
Erkin uchuvchi o'q — o'sha `ArrowUpdate`, faqat nishonsiz; devorga urilib
tugaydi, chunki klient chizgan yo'lak ham devorda tugaydi. Qahramon o'z
to'rttasini saqlab qoldi; bu ikkitasi keyingi qahramonlar uchun.

**Skill tanlangan unitga tegishli.** QWER selection'ni umuman so'ramasdi; endi
so'raydi. Va uning ikkinchi yarmi shart: yangi dunyoda qahramon **bir marta**
avtomatik tanlanadi — har qavat yangi obyekt beradi, ya'ni eski tanlov yaroqsiz
bo'lib, skillar jimgina ishlamay qolardi.

### 8.al Ikkinchi qahramon — Knight, va u'ning aksi

Kamonchining teskarisi: ko'p jon, sekin, uzoqdan hech narsa qila olmaydi. Uni
qo'shish **asosan INI ishi bo'ldi** — mexanizm oldingi ishda tayyorlangan edi —
lekin "asosan" so'zi muhim, va quyidagi beshta joyda Java kerak bo'ldi.

**★ Eng muhimi: qaysi qahramon o'ynalishi Java'da qattiq yozilgan ekan.** `"Hero"`
literal sifatida olti joyda turardi (`Spawner`, `HeroProgress`, `DungeonRun`,
`Main.discoveredBy` va boshqalar). Ya'ni ikkinchi qahramonni **to'liq tasvirlab
ham, u hech qachon dungeon'ga kira olmasdi**. Endi `DefaultHero = Knight` —
`DungeonRun` blokida bitta qator. Tanlash ekrani keyingi ish; bu uni sinash va
balanslash uchun yetarli.

**Klavish skillga emas, UYAGA tegishli.** Ikkala qahramon ham Q/W/E/R da o'ynaydi,
ya'ni faylda Q'da ikkita skill bor. `Main.controls` va indikator halqalari endi
**faqat o'ynalayotgan** qahramonnikini bog'laydi. Bu kichik nozik narsa emas:
kamonchining Q si *jonzotga*, Knight'niki *yerga* mo'ljallanadi — noto'g'risi
bog'lansa klik rad etiladi va skill umuman ketmaydi.

**Ikkita effekt ikki vazifani bajaradi, faylga qarab** — yangi enum qo'shmasdan,
va shu sababdan kamonchining biror skili bir zarra ham o'zgarmadi:

| Effekt | Sozlama yo'q | Sozlama bor |
|---|---|---|
| `AREA_DAMAGE` | bir marta tushadi (kamonchining W si) | `DurationFrames` + `TickFrames` → **aylanma bo'ron**, va u qahramonga **ergashadi** |
| `DASH` | zararsiz sakrash (kamonchining E si) | `Damage` → **otilish**, yo'ldagilarni uradi |

Uchinchisi — `GUARD` — haqiqatan yangi shakl edi. `EMPOWER` **chiquvchi** zararni
oshiradi (o'yinchining qurol bonusi orqali); `GUARD` **kiruvchi** ni kamaytiradi
(tananing zirhi orqali). Ikki xil raqam, ikki xil obyektda — shuning uchun
`EMPOWER` ning manfiysi bo'la olmaydi. Zirh esa `HeroProgress` orqali o'tadi,
chunki **zirhni bitta joy hisoblaydi**: daraja + topilgan narsa + tug'ma zirh +
guard. Aks holda keyingi daraja guard'ni, guard esa darajani bekor qilardi.

**Per-hero ikkita yangi maydon.** `Title` — ilgari `DungeonHud` da bitta qator
edi, ya'ni Knight HUD'da "O'q ustasi" deb turardi. `ArmourPercent` — tug'ma zirh,
jonzot blokida tura olmaydi, chunki qahramonning zirhi har safar darajasidan
**qayta yoziladi**. Ikkalasi ham `DungeonHero` blokida.

**Knight'ning to'rttasi** (barchasi 34 dan uzoqqa yetmaydi — kamonchining eng
uzuni 68):

| | Skill | Effekt | Nega shunday |
|---|---|---|---|
| Q | Cleave | `AREA_AT_SPOT` | Konus shakli o'yinda yo'q; qisqa `Range` + o'rtacha `Radius` o'ynashda aynan cleave bo'ladi |
| W | Charge | `DASH` + `Damage` | Kamonchining E si bilan bir xil effekt, faqat zarari bor |
| E | Guard | `GUARD` | Kamonchida bunday narsa yo'q va bo'lmasligi kerak |
| R | Whirlwind | `AREA_DAMAGE` + davomiylik | 4 soniya, sekundiga 2 marta — ya'ni undan **chiqib ketish mumkin** |

**Surish (push) qilinmadi.** Otilish yo'ldagilarga zarar beradi, lekin ularni
joyidan siljitmaydi: tanani surish navigatsiya gridiga tegishli savol, skillga
emas.

**Modeli keldi** — `knight.glb` + `knight_texture.png` + `sword_2handed`, hammasi
kamonchi chiqqan o'sha KayKit Adventurers 2.0 paketidan (CC0), ya'ni rig bir xil
va sakkizta klipning hammasi hech narsa qilmasdan ko'chdi.

**Ikki qo'llagan qilich — bu bezak emas.** Kitda `Melee_1H_Idle` **yo'q**, ya'ni
bir qo'llagan qilich bilan u `Melee_Unarmed_Idle` da turishi kerak bo'lardi va
qilichini unutgan odamga o'xshardi. Ikki qo'l qolgan tavsifiga ham mos: sekin,
og'ir, ichkariga kiradi.

**★ Raqamlar o'lchandi.** Knight **2.543** balandlikda (kamonchi 2.275), boshi esa
nisbatan **pastroq**: `head` bo'g'ini 0.488 da (kamonchida 0.546). Ya'ni u
**balandroq model, boshi esa nisbatan kichikroq** — shuning uchun:
- `ModelScale = 4.72` (12 / 2.543), kamonchida 5.3. Ikkalasi ekranda bir xil
  bo'yda turadi, chunki ikkalasi ham 12 birlik jonzot.
- Portret kamerasi ham boshqacha: `Head = 0.70`, `Show = 0.68` (kamonchida
  0.74 / 0.64). Kamonchining raqamlari bilan kadr baland tushib, dubulg'asi
  ustida bo'sh joy qolardi.

Aynan shu **per-hero blok nima uchun kerakligining isboti** — ikkinchi qahramon
birinchisining raqamlarini kiyib bo'lmaydi.

**Qilich `+Y` bo'ylab yotibdi**, ya'ni `HeldRoll` kerak emas — buni taxmin
qilmadim, `aWeaponLaidOutAcrossTheKitsGrainIsTurned` testi tekshiradi. Lekin u
test faqat **birinchi** qahramonning qurolini olardi; endi hammasini oladi —
noto'g'ri osilgan qurol ko'z bilan ko'rilmaguncha bilinmaydi, va ikkinchi
qahramonning boshqa turdagi quroli aynan o'sha test o'tkazib yuboradigan holat
edi.

**Ikonkalari hali yo'q** — uyalarida klavish harflari turadi (klientning o'z
zaxira yo'li). Foydalanuvchi o'zi topadi.

**22 ta test**, ichida eng muhimi ikkitasi: `aWholeRunStartsWithTheKnightInIt`
(haqiqiy dungeon generatsiya qilib, uni ichiga qo'yib, 4 soniya yurgizadi) va
`theArcherWasNotRebalanced` (kamonchining beshta raqami yozib qo'yilgan — ikkinchi
qahramonni birinchisini surib balanslash eng klassik xato).


### 8.am Stage — o'zgarmaydigan xarita, va uni yasaydigan asbob

O'yinning ikkinchi turi, va u birinchisining aksi. Roguelike tushishi har run'da
yangi qavat chizadi, ya'ni har qavat — birinchi ko'rish; savol "qanchaga
tushdim?". **Stage** — qotirilgan qavat: o'sha xonalar, o'sha burchaklarda o'sha
maxluqlar, har safar. Savol "shuni yengaman-mi?" ga aylanadi, ya'ni yutqazish
bir narsa o'rgatadi va ikkinchi urinish birinchisidan yaxshiroq bo'ladi.
Warcraft custom map uslubi.

**Stage fayli — muzlatilgan `GeneratedDungeon`.** Ikkinchi xil daraja emas,
generatorning o'z natijasi faylga yozilgani. Shu sababli `Spawner` qotirilgan
qavatni bir daqiqa oldin chizilganidan **ajrata olmaydi**, va "stage o'zi
kesilgan dungeon bilan aynan bir xil o'ynaladi" degani ehtiyotkorlikdan emas,
qurilishdan kelib chiqadi. `StagePlayTest` buni 100 kadrdan keyingi
`checksum()` bilan qulflaydi — ya'ni tarmoq ikki mashina bir xil o'yin
o'ynayotganini isbotlaydigan o'sha dalil, peer o'rniga faylga qaratilgan.

**Format:** matn, engine'ning o'z INI o'quvchisi bilan
(`dungeon/src/main/resources/stages/first.stage`, 189 qator). Binar format —
hech kim ocholmaydigan daraja; g'alati ishlaydigan stage kimdir ochib, o'qib,
xatoni **ko'ra oladigan** fayl bo'lishi kerak. Koordinatalar dunyo birligida
emas, **katakda**: dungeon hamma narsani katak markaziga qo'yadi, ya'ni ikkalasi
bir xil fakt, lekin faqat bittasini tepadagi xarita bo'yicha ko'z bilan sanash
mumkin. Fayl seed'ni ham saqlaydi — loot va temani o'sha
beradi, saqlanmasa "o'sha xonalar, boshqa hamma narsa" chiqardi.

**Yuklashda ulanish qayta tekshiriladi** (`StageCheck`). Generatorga bu kerak
emas: koridorlari qamrovchi daraxt, kafolat qurilishdan keladi. Stage'da esa
qalamni odam ushlab turibdi, va fayl — maxluqni devor ichiga qo'yish mumkin
bo'lgan joy. Shuning uchun dungeon **engine'ning o'z qadam qoidasi**
(`PathGrid.canStep`) bilan yurib chiqiladi — nusxasi bilan emas, aks holda
tekshiruv o'zi bilan kelishib, o'yin bilan kelishmasdi. Javob — **ro'yxat**,
birinchi xato emas: bitta xatoni tuzatib, saqlab, keyingisini eshitadigan
muallifga haqiqat bir gapdan aytilgan bo'lardi.

**`Floors` choki** — `DungeonRun` dagi yagona o'zgarish. Ilgari u seed'ni o'zi
ushlab turardi va `descend()` ichida generatsiya qilardi; endi qavat qayerdan
kelishini chok hal qiladi: `GeneratedFloors` (seed zanjiri, `lastDepth =
Bosses.size()`) yoki `StageFloors` (o'sha qavat, `lastDepth = 1`). Chok
**holatli**, ataylab: roguelike'da o'lim ham `descend()` chaqiradi, ya'ni seed
o'limlardan ham oldinga suriladi — sof `floorAt(depth)` har yangi run'ni bir xil
birinchi qavatdan boshlardi, bu esa boshqa o'yin.

**Rejim tanlash:** `dungeon.ini` da `DungeonStage Play / File = `, yoki
`--stage=stages/first.stage`. Bo'sh — roguelike, aynan avvalgidek. Buzuq
stage **jimgina roguelike'ga qaytmaydi**: stage so'ragan o'yinchi tasodifiy
qavat olsa, nimadir buzilganini bilishning iloji qolmaydi.

**`worldbuilder` moduli** — Swing muharriri, `dungeon` ga bog'langan (Studio
o'zgarmadi; u RTS uchun). Mehnat taqsimoti: **generator joyni chizadi, muallif
uni to'ldiradi**. Xona, koridor, qavat, zinapoya — seed'dan, va bu yerda
tahrirlanmaydi; tahrirlanadigani ichida nima turishi. Shuning uchun pol/devor
chizish asbobi yo'q, 3D preview yo'q, xona qo'shish yo'q: yoqmasa — yangi seed.
Maxluq va prop ro'yxati `DungeonSettings` dan, ya'ni muharrir o'yin spawn qila
olmaydigan narsani taklif qila olmaydi. Pastdagi xatolar ro'yxati —
**yuklashdagi aynan o'sha tekshiruv**, shuning uchun bu yerda bir narsa, Play
bosganda boshqa narsa aytilmaydi. Muharrir buzuq stage'ni saqlaydi (muallif
ishni yarmida to'xtaydi), **o'yin** esa o'ynashdan bosh tortadi — noto'g'rilik
qimmatga tushadigan joy o'sha.

Undo — stage'ning o'z matnidan iborat stek: hujjat bir necha kilobayt, va format
allaqachon to'g'ri o'qilishi shart bo'lgan narsa, ya'ni ikkinchi tasvir yo'q.

**33 ta yangi test.** Eng muhimlari: `aStageComesBackTheDungeonItWasCutFrom`
(100 seed, to'liq record tengligi), `aStagePlaysExactlyLikeTheDungeonItWasCutFrom`
(checksum), `dyingPutsHimBackOnTheSameFloor`, `killingTheBossWinsTheStage` va
`theShippedStageCanBePlayed` (shipping fayl classpath'dan, o'yinchidagi yo'l
bilan). Muharrirning 9 tasi `StageDraft` ustida — Swing'siz, chunki bu build'da
hech qayerda ekran yo'q.

### 8.an Kim bo'lib o'ynash — "Play" endi savol beradi

Ikkita qahramon bor, lekin qaysi biri o'ynalishini `DefaultHero` — INI'dagi bitta
qator — hal qilardi. Endi **o'yinchi tanlaydi, va tanlamaguncha hech narsa
ochilmaydi**.

**★ Qoida: savoldan o'tib ketish yo'li YO'Q.** Bu bezak emas, xususiyatning
o'zi — yo'li bor tanlov bu **sozlama**: o'yinchi Play bosadi, faylda nima
yozilgan bo'lsa shuni oladi va tanlovi borligini umuman bilmaydi. Shuning uchun
`Play` o'yinni emas, **savolni** ochadi; `Back` esa o'yinga emas, bosh menyuga
qaytaradi.

**Mexanizm klientniki, savol o'yinniki** — `Shell` ning qolgan qismidagi bitta
bitim. `Shell.Question` = sarlavha + variantlar + callback. Klient hech qachon
"qahramon" so'zini eshitmagan: u to'rt o'yinga xizmat qiladi va birortasining
tilida gapirmaydi. Ro'yxat `DungeonHero` bloklaridan quriladi, ya'ni **uchinchi
qahramon shu ekranda paydo bo'lish uchun faqat mavjud bo'lishi kifoya**.

**★ Eng nozik joyi: dunyo oyna ochilishidan OLDIN quriladi.** Klient menyuni
mavjud bo'lmagan o'yin ustida ko'rsata olmaydi — `Duke3D.launch` tayyor
`DukeGame` oladi va `DukeRtsApp` da `game` 59 joyda ishlatiladi, `simpleInitApp`
esa undan relyef quradi. Ya'ni savol berilayotgan paytda dungeon'da allaqachon
kimdir turadi (`DefaultHero`). **Uni hech kim ko'rmaydi:** tanlov birinchi
qavatni qaytadan yotqizadi — `DungeonRun.startWith(...)`, ya'ni **o'lim
yuradigan aynan o'sha yo'l**. Sababi ham bir xil: tanlanmagan qahramon
topgan narsaning hech biri tanlanganiniki emas, shuning uchun darajalar va
o'ljalar ketadi.

**`DefaultHero` o'chmadi, ma'nosi torayadi:** *"hech kim so'ralmaganda kim
o'ynaydi"* — headless run va testlar. Shu sababdan 1120 test bir qatorsiz
o'tdi.

**Stage qaysi qahramon uchun ekanini ayta olmaydi.** Bu ataylab: menyu yagona
hal qiluvchi. `Stage` record'iga maydon qo'shilmadi.

**Xavfsizlik chekkasi:** variantlar ma'lumot faylidan keladi, ya'ni fayl birorta
qahramon nomlamasa o'yinchi bo'sh ustun oldida qolib ketardi. `worthAsking()`
buni ushlaydi — variantlar bo'sh bo'lsa savol berilmaydi va `Play` avvalgidek
ishlaydi.

**17 ta yangi test.** `HeroChoiceTest` (12): tanlangan qahramon haqiqatan
dungeon'da turishi va eskisi **yo'qolishi**, o'z skillari va o'z zirhi bilan
kelishi, tanlov run'ni noldan boshlashi (o'lja va daraja ketishi), ikki marta
tanlash ikkitasini qoldirmasligi, va hech kim so'ralmaganda faylning javobi
ishlashi. `ShellTest` (5): bo'sh ro'yxat o'yinchini qamab qo'ymasligi.

---

### 8.ao Bosqich qanchalik katta va qanchalik qiyin — generatsiyadan oldin so'raladi

Bosqich formati bor edi, lekin har bosqich tushishning **birinchi qavati** edi:
shipping `dungeon.ini` dagi o'lchamda va 1-chuqurlikda. Ya'ni "o'rganib, qayta
urinib, oxiri yengiladigan daraja" va'da qilingan narsa amalda birinchi qavatning
muzlatilgan nusxasi bo'lardi. Ikkalasi ham tuzatildi, va **ikkalasi ham
generatsiyadan oldin so'raladi** — chunki keyin qo'llab bo'lmaydi.

**Qiyinchilik — chuqurlik, yangi shkala emas.** `Stage.difficulty` ilgari muallif
yozadigan va panel ko'rsatadigan raqam edi, ya'ni istalgan narsani ayta olardi va
noto'g'ri bo'lishi mumkin edi. Endi u **o'sha bosqich qaysi chuqurlikda
o'ynalishi**. Yangi mexanizm yozilmadi: qavatni xavfli qiladigan hamma narsa
allaqachon `depth` bo'yicha yozilgan va sozlangan — jon, zarar, soni, qaysi turlar
umuman paydo bo'lgani (`MinDepth`) va qaysi boss kutayotgani. Shuning uchun
"qiyinchilik 7" ning ma'nosi bor: tushishning 7-qavati qanday bo'lsa shunday, va
buni borib tekshirsa bo'ladi. Tushish `Bosses` tugaganda tugaydi (4 qavat);
bosqich esa **undan chuqurroq** qurilishi mumkin — bu bosqich yasashning asosiy
sabablaridan biri.

`DungeonRun` da o'zgargani faqat **chuqurlik qayerdan boshlanishi**: `Floors` ga
`firstDepth()` qo'shildi, `GeneratedFloors` 1 qaytaradi (roguelike umuman
o'zgarmadi), `StageFloors` — bosqichning qiyinchiligini. Uchta joyda o'qiladi:
konstruktor, `playing(...)` va `begin()`. Oxirgi ikkitasi muhim va ikkalasi ham
bir xil sabab bilan: **`playing(...)`** — menyu bosqichni o'yin qurilgandan keyin
tanlaydi, ya'ni konstruktordagisi yolg'iz yetmaydi va qiyinchilik faqat
`--stage=` bilan ishlardi; **`begin()`** — o'limdan keyin 1 ga qaytish qiyin
bosqichning ikkinchi urinishini oson qilib qo'yardi, ya'ni qayta urinish uchun
qurilgan daraja uchun mumkin bo'lgan eng yomon nosozlik.

**O'lcham — `Layout` record'i.** Generatsiya raqamlari `DungeonSettings` dan
chiqib, chaqiruvchi almashtira oladigan qiymatga aylandi. `Layout.of(settings)`
— tushishning o'z qavatlari, **bit-baravar** (`LayoutTest` shuni qulflaydi);
`Layout.sized(...)` — muallif so'ragani. Faqat muharrir haqiqatan so'raydigan
uchtasi ochiq: eni, bo'yi, xona soni. Xona o'lchami va koridor eni faylda
qoladi — ular bu o'yinda dungeon **nima ekani**, va o'n to'rt katakli xona
kattaroq dungeon emas, boshqa o'yin.

Urinishlar soni so'ralmaydi va fayldan ham olinmaydi: xonalar tashlab-rad qilib
joylashtiriladi, ya'ni xarita to'lgani sari keyingisini tushirish ko'proq tashlash
talab qiladi. Oltita xonaga sozlangan son qirqtaning uchdan birida taslim bo'lardi
va qavat **jimgina** so'ralganidan kichik chiqardi. Shuning uchun u so'ralgandan
keltirib chiqariladi. Sig'magani baribir bo'ladi — muharrir buni **aytadi**,
chunki aytmasa muallif o'zi yozgan raqamga ishonaverardi.

**Nega oldin so'raladi.** Ikkalasi ham qavat chizilgandan keyin qo'llanmaydi:
kattaroq xarita "o'sha xonalar uzoqroqda" emas, boshqa qavat; qiyinchilik esa
qaysi turlar va qaysi boss chizilishini hal qiladi. Keyin so'ralsa qayta chizish
kerak bo'lardi — muallif esa o'zgarishi kutilayotgan qavatga narsalarni allaqachon
terib qo'ygan bo'lardi. Shuning uchun dialog oynadan **oldin** ochiladi.

**Ikkinchi shipping bosqich:** `deep.stage` — "The Long Dark", 100×76, 28 xona,
221 maxluq, **chuqurlik 8**. `first.stage` (50×36, chuqurlik 1) yonida ataylab:
biri oddiy qavatning o'lchami va xavfi, ikkinchisi esa generatsiya qilingan qavat
hech qachon bo'lmaydigan narsa.

**Ataylab 100×76 da to'xtatildi, 200×150 da emas.** Generator, tekshiruvlar va
simulyatsiya ancha uzoqroqqa boradi — 200×150 / 70 xona / 759 maxluq **56 ms** da
chiziladi va hamma tekshiruvdan o'tadi. Chegara `client3d` da: `TerrainScene` har
tosh katak uchun **bitta `Geometry`** yasaydi va batch qilmaydi — shipping qavatda
~995 ta, 100×76 da ~4700, 200×150 da ~22 000. Slideshow bo'lib chiqadigan bosqich
katta bosqich yasay olishning yomon reklamasi bo'lardi. **Relyefni batch qilish —
ochiq engine ishi**, va katta bosqichlar uni birinchi bo'lib talab qiladi.

**16 ta yangi test.** `LayoutTest` — `Layout.of` bugungi dungeonni aynan
qaytarishi, 180×140 / 60 xonada 12 seed'ning hammasi to'liq yurib bo'ladigan
bo'lishi (ulanish kafolati kattalikda), 12-chuqurlikda ham shunday, va
sig'maydigan xona soni yiqilmasdan kam qaytarishi. `StageDifficultyTest` —
chuqur bosqich haqiqatan qiyinroq, o'limdan keyin **o'sha** qiyinlikda qaytishi,
chuqur bossni o'ldirish g'alaba bo'lishi, panel "VI / VI" deb sanashi, va
**tushish hali ham 1-qavatdan boshlanishi**.

### 8.ap Ritsar nega urmasdi — ikkita raqam, ikkita boshqa fayl

Ikkinchi qahramon o'ynaladigan bo'lgach, ikkita nuqson chiqdi va **ikkalasi ham
bir xil shaklda**: qiymat kamonchiga qarab tanlangan, keyin hamma qahramon uchun
ishlatilgan. Ikkalasi ham Java'da emas, ma'lumotda; ikkalasi ham xato deb
ko'rinmaydi.

**Joyida qotib qolishi.** Maxluqqa hujum buyrug'i berilsa ritsar qimirlamasdi,
maxluq o'zi kelib urgandan keyingina urardi. `CloseDistance` — "nishonga qancha
yaqin borib to'xtaydi" — butun o'yinda bitta edi: **48**, ya'ni kamonchining
raqami (u 60 ga otadi, demak 48 bemalol ichida). Ritsar esa **11** ga yetadi.
Shuning uchun u 48 da to'xtab, to'rt tana uzunlikda turib havoni kesardi. Bu
o'yinchiga "provokatsiya qilinmaguncha urushmaydigan qahramon" bo'lib ko'rinadi.

Tuzatish — raqam **o'ziniki** bo'ldi: `HeroLook.closeDistance`, `DungeonHero`
blokida (`Hero = 48`, `Knight = 8`). Ustiga `HeroBrain` uni **o'z quroli
yetadigan masofaning 4/5 iga qisadi**, faylda nima yozilgan bo'lsa ham — chunki
bu yangi qahramonga beparvo yoziladigan eng ehtimolli raqam, va noto'g'ri bo'lsa
hech narsa ko'rinmaydi: yuradi, to'xtaydi, va hech narsa bo'lmaydi.

**Urish animatsiyasi.** `Melee_2H_Attack_Chop` nomi bo'yicha tanlangan edi, lekin
u **1.633 s** yuradi, zarba esa har `ReloadFrames = 34` = **1.133 s** da tushadi.
Ya'ni har zarba klipni 69% da uzib, boshidan qayta boshlatardi — ritsar bironta
zamahni oxirigacha yetkazmagan. O'rniga `Melee_2H_Attack_Slice` (**1.100 s**)
qo'yildi, ya'ni endi zamah **tugaydi** va keyingisiga 33 ms zaxira qoladi.

**Testlar.** `pointedAtSomethingJustOutOfReachHeStillWalksToIt` — aynan
o'yinchi ko'rgan holat, va u **qahramon qimirladimi** deb so'raydi, "maxluq
shikastlandimi" deb emas: nuqson turganda ham maxluq kelib urishardi va urishga
javob qaytarish "urushyapti" ga o'xshab testni aldardi.
`everyHeroStopsInsideHisOwnReach` ikki raqamni fayl darajasida taqqoslaydi, ya'ni
uchinchi qahramon qo'shilganda jang qurilishidan oldin yiqiladi.
`aSwordsmanFinishesHisSwingBeforeTheNextOneStarts` klip uzunligini
`ReloadFrames` bilan solishtiradi — INI izohidagi "o'zgartirsang tekshir"
ko'rsatmasi endi build'da tekshiriladi.

**Kamonchi ataylab chetda:** o'q klipning *boshida* uchadi, qolgani davomi, shuning
uchun uning 1.333 s lik otishi 0.8 s lik qayta o'qlashiga sig'masligi muammo emas
va hech qachon bo'lmagan ham.

### 8.aq Grafika — havoda osilgan daraxtlar va chuqurligi yo'q pol

Birinchi o'yinchidan feedback keldi va grafika eng katta shikoyat bo'ldi, ikki
marta aytilgan. Ikki aniq nuqson topildi va **ikkalasi ham bitta narsaning ikki
ko'rinishi**: relyef ma'lumoti bor, lekin uni ko'rsatadigan hech narsa yo'q edi.

**★ Avval: `ruins` va `scifi` temalari yo'q.** `dungeon.ini` da ikkita tema bor —
`Dungeon` va `Forest`. Brifda aytilgan boshqalari hali yozilmagan.

#### 1. Yuqori qavatda daraxtlar havoda osilib turardi

O'lchandi: uch qavatli test xaritasida **9 ta daraxt** `y=10` va `y=20` da, tagida
hech narsasiz.

Sabab masonry'da emas edi. `TileLayout` har doim to'g'ri ishlagan: qoyaga qarab
**har qavat uchun bitta kurs** devor qo'yadi, ya'ni ikki qavatli qoya ikki qatlam
bilan o'raladi va orasidagi massa yopiq. Nuqson `TerrainScene.plan()` da — `WallFillsRock`
bo'lganda u qoyaning hamma yuzini **bitta tanaga** yig'adi va uni `min(yuzlar balandligi)`
dan o'stirardi. Yuqori qavatdagi xonani o'rab turgan qoyaga esa **faqat yuqoridan**
tegiladi, ya'ni o'sha min ham yuqorida — daraxt xona balandligida, tagi bo'sh.

Ikkinchi yarmi: ikki qavatli qoya uchun daraxt **2× kattalashtirilardi**. Narsani
balandroq qilishning yagona yo'li uni kengroq qilish, ya'ni yonidagi xonadan ikki
barobar keng daraxt chiqardi — bu ham proporsiya buzilishi edi.

**★ Birinchi yechimim noto'g'ri edi va o'yinchi buni ko'rib aytdi.** Men katak
markaziga qoya (`rock.gltf`) qo'yib, massani yerdan to'ldirgandim. U ishlardi,
lekin ekranda tosh uyumidek ko'rinardi va — muhimrog'i — **keraksiz edi**:
`TileLayout` allaqachon qoyaning yuzalarini hisoblab chiqargan, `plan()` esa
ularni **tashlab yuborardi**. To'g'ri yechim tashlamaslik.

**Yakuniy yechim:**

- `plan()` yuzalarni saqlaydi va ularni kit aytgan model bilan chizadi:
  `RockFace = wall.gltf` — dungeon temasining **o'z devori**, qarzga olingan.
  Shuning uchun u forest'ning `props/` papkasida turadi, tiles ichida emas.
- Daraxt qoyaning **ustida**, doim bitta qavat bo'yi. Eski "2 qavat = 2× katta
  daraxt" qoidasi ham shu bilan tugadi — narsani balandroq qilishning yagona yo'li
  uni kengroq qilish edi.
- **Cho'zish yo'q, va raqam yozilmagan:** devor o'z o'lchovidan masshtablanadi —
  balandligi aynan bitta qavat. Kvadrat slab bo'lgani uchun eni ham aynan bitta
  katak chiqadi. O'lchandi: `scale=2.5`, `en=10.00` (katak 10), `lift=0.00`,
  `back=-1.25` — bu **dungeon temasining qo'lda sozlangan `WallShift = -0.5` ini
  aynan qaytaradi**, ya'ni o'lchov qo'l bilan topilgan raqam bilan mos tushdi.
- **★ Faqat qoya oyoq ostidagidan balandroq turgan joyda chiziladi.** Forest'da
  `WallHeight = 0`, ya'ni qoyaning deyarli hammasi pol balandligida — har daraxt
  ostiga devor qo'yilsa butun o'rmon atrofida tosh bordyur chiqardi.
- **Dungeon temasi `RockFace` yozmaydi → bitta ham bo'lak qo'shilmaydi.**

Bu variant birinchisidan **arzonroq** ham: bo'laklar faqat plato chekkasida,
massaning ichida emas.

#### 2. Pol va devor usti bir xil tekstura

Ildiz sabab bitta qatorda edi: `TerrainScene.assetFor` da `case CAP -> tileset.getFloor()`
— **devor usti tom ma'noda pol plitkasining o'zi**. Shader esa Lambert: ikkala yuzaning
normali `+Y`, demak bitta quyosh ikkovini bir xil soyalaydi. Forest'da bundan ham
yomoni — `WallHeight = 0`, ya'ni qopqoq pol bilan **bir xil balandlikda**.

- **`CapTint`** — qopqoqqa alohida rang. `KitTiles` materiallarni allaqachon tint
  bo'yicha kesh qiladi, shuning uchun bu **qo'shimcha draw call bermaydi**, faqat
  bitta qo'shimcha material. Dungeon `0xA8A8B4` (sovuqroq, ~2/3 yorug'lik).
- **★ Forest'da `CapTint` YO'Q, va bu ham o'yinchi ko'rgandan keyingi tuzatish.**
  Avval unga ham berilgandi (`0x8FA37A`) va o'yinchi "daraxt tagida soya bormi?"
  deb so'radi. Soya emas edi — har katakka bitta **qoramtir kvadrat**, ya'ni
  yuqoridagi `WallClump`/`WallSpread`/`WallVariety` yashirish uchun qurilgan
  **grid**ning o'zi qaytarib berilgani. Zindonda tint hech narsa yo'qotmaydi,
  chunki masonry katak chegaralariga qo'yiladi va grid allaqachon ko'rinib turadi;
  o'rmonda esa u san'atni buzadi. Tashqarida "bu yerdan o'tolmaysan" degan narsa —
  daraxtning o'zi, terrasa chekkasida esa tepadagi devor.
- **`StoreyShadePercent = 108`** — har qavat yuqorisi yorug'roq. ★ **Eng tepadan
  pastga sanaladi**, chunki tint ko'paytiradi va ko'paytirish faqat qoraytira
  oladi: oq plitkani "yorug'roq" qilishni so'rasang o'sha oqni qaytaradi va sozlama
  jimgina ishlamaydi. Birinchi urinishimda aynan shunday bo'ldi — test tutdi.
- **`DungeonSun` bloki** — quyosh burchagi/kuchi endi INI'da. `Sunlight` record
  (`Fog` naqshi bo'yicha); `DukeRtsApp` dagi uchta qotirilgan konstanta o'rniga
  bitta manba, chunki **ikki joy bir xil qiymatni bilishi kerak**: sahna chirog'i
  (maxluqlarni yoritadi) va relyef shaderi (yerni yoritadi).

**★ Pitch nimani ajratadi.** Tik quyoshda pol bilan devor farqi eng katta bo'ladi —
pol 1.0, har qanday tik yuza 0.0. Yo'qoladigani **devorlarning bir-biridan** farqi:
yuzaga tushadigan ulush quyoshning gorizontal qismidan keladi, 90° da esa u nol,
ya'ni shimolga qaragan devor bilan sharqqa qaragani bir xil qorong'i va hech
narsaning shakli yo'q. Shu sababli 57 → **40** ga tushirildi: yoyilish `cos(pitch)`
ga teng, ya'ni 0.54 → 0.77. Yana bir foyda — 57 da pol `0.25 + 0.84 = 1.09` chiqib
**oqqa kesilardi**, 40 da esa 0.92.

**Pitch pol bilan devor ustini ajrata olmaydi** — ikkovining normali bir xil, hech
qanday yorug'lik burchagi buni tuzatmaydi. Uni `CapTint` qiladi.

**(c) — ataylab qilinmadi.** Haqiqiy ambient occlusion yoki soya xaritasi bu
flat-shaded palette renderiga mos emas va Mac qizishi haqidagi shikoyatga
to'g'ridan-to'g'ri zid.

#### Testlar, va ular nega shunday yozilgan

Ikkita test birga turadi va birontasi yolg'iz yetarli emas:

- `NoGapsTest` — **layout** hech qanday pog'onani ochiq qoldirmasligini aytadi.
  U allaqachon bor edi va nuqson davomida **yashil turavergan**, chunki layout
  hech qachon xato bo'lmagan.
- `aKitWhoseWallIsAThingStillDrawsTheSidesOfItsRock` — **renderer** layout aytgan
  narsani chizishini aytadi. Nuqson turganda bu bo'sh ro'yxat qaytarardi.

Yonida `andAKitThatNamesNoRockFaceDrawsNone` va
`andNotWhereTheRockIsLevelWithTheFloorBesideIt` — ikkinchisi bordyur qaytib
kelmasligini qo'riqlaydi va **nechta** emas, **qaysi to'rttasi** ekanini yozadi.

Yozish davomida o'z o'lchovim bir necha marta noto'g'ri savol berdi va har safar
tuzatildi: bo'laklar turgan katagi bo'yicha emas *qurilgan* katagi bo'yicha
guruhlangan edi; bo'lakning `y` i oyog'i emas markazi; tayanch devor yolg'on
ijobiy berardi; `StoreyShade` ni yorug'lashtiruvchi qilib yozgandim, lekin tint
faqat qoraytira oladi; va bu bo'limdagi "8 ta yuza" degan taxminim aslida 4 ta
chiqdi. Hammasi "test yashil bo'lsin" deb emas, *savol* to'g'ri bo'lsin deb
tuzatildi.


### 8.ar Ikonkalar — uch varaqdan 22 ta fayl, va ikkita qotirilgan ro'yxatning oxiri

AI (Microsoft Copilot) bilan uch varaq ikonka tayyorlandi: 10 ta skill (bo'yalgan,
rangli), 4 ta buyruq va 8 ta statistika (tekis oltin). Ish uch qismdan iborat
bo'ldi va faqat birinchisi kutilgandek edi.

#### 1. Kesish — to'r emas, o'lchov

`:dungeon:cutIcons` (`tools/IconSheets.java`). Varaqlarning o'zi
`dungeon/art/icons/` da, resources dan tashqarida — ya'ni o'yin ichida
sayohat qilmaydi, lekin kesishni qayta ishga tushirish mumkin. Bu shart edi:
**qayta ishga tushirilganda bayt-bayt bir xil natija** beradi, tekshirildi.

Uchta to'siq chiqdi va har biri koddagi bitta qoidaga aylandi:

- **★ Birinchi varaqlar `RGB` edi** — alfa kanalisiz, shaffof ko'ringan shaxmat
  naqsh piksellarga chizilgan. Foydalanuvchi RGBA versiyasini berdi va bu muammo
  butunlay yo'qoldi. (Agar yana takrorlansa: chekkadan flood-fill kerak, oddiy
  threshold oq muz yadrosini ham yeb qo'yadi.)
- **AI chiqindisi.** Skill varag'ining yuqori o'ng burchagida 5 ta o'chgan kulrang
  dog' bor. O'lcham bo'yicha ajratib bo'lmaydi — ikkitasi haqiqiy muz parchasidan
  kattaroq. Ajratgich: **qattiq yadro** (`alpha >= 200`). Chiqindining eng yuqori
  alfasi 208, lekin 200 dan yuqori bitta ham piksel yo'q; haqiqiy bo'lak esa
  maydonining yarmida 255 ga chiqadi. Flood butun ko'rinadigan soha bo'ylab
  yuradi (nur kesilmasin uchun), lekin ichida yadro bo'lmagan blob tashlanadi.
- **★ Ikonkalarning yarmi uzuq.** `cmd_move` = strelka + halqa, `cmd_attack` =
  qilich + bolta, `stat_speed` = oyoq + 3 chiziq, `stat_cooldown` = halqa +
  strelka, `stat_range` = nishon + nuqta. Har qatorda **nechta** ikonka borligi
  ma'lum, shuning uchun qator eng katta **N−1 oraliq** bo'yicha bo'linadi. Bu
  skill varag'ining 3-qatori markazda turgani (1-2 ustunda emas) muammosini ham
  o'zi hal qiladi — u haqda hech narsa yozish kerak bo'lmadi.

#### 2. Bo'yalgan ikonka — panel rang bermaydi, yorqinlik beradi

Panel **har doim** ikonkani rangga ko'paytirgan: bitta oq chizma shu bilan tayyor
/ kuluar / yopiq uchta holatga xizmat qilgan. Bo'yalgan rasm buni ko'tarmaydi —
ko'k muz portlashini mash'al rangiga ko'paytirsang oltin muz chiqadi.

`IconLook` qo'shildi, `PaintedSkillIcons = Yes` INI'da. Bo'yalgan ikonkada holat
**yorqinlik** bilan aytiladi (tayyor = aynan chizilganidek, kuluar = ×0.45,
yopiq = ×0.30 va yarim so'ngan). Tanlangan holatda ikonka **umuman
tegilmaydi** — halqa, tosh va bracketlar allaqachon siyanga o'tgan, va olovli
o'qni siyanga yuvish eng muhim uyani eng o'qib bo'lmaydiganiga aylantirardi.

#### 3. Ikkita qotirilgan ro'yxat o'chdi

Bu so'ralgan ish emas edi, lekin "ikonka nomlari INI'da" qoidasi aynan shularga
tegardi:

- `HeroPanel.STAT_GLYPHS = {"blade","shield","bolt","heart"}` — klient o'yinning
  **uchinchi statistikasi chaqmoq** ekaniga qaror qilardi, va to'rtinchisi
  oxirgisining nusxasini olardi.
- `HeroStatus` dagi `{{"F","march"},{"A","blade"},…}` — buyruq qanday
  ko'rinishini o'zgartirish ikki modulni tahrirlashni talab qilardi.

Ikkalasi ham INI'ga chiqdi. Sim formatiga ikonka maydoni qo'shildi
(`stat=so'z,qiymat,bonus,ikonka`) — `Reading.parse` notanish maydonda butun
qatorni rad etadi, shuning uchun klient va o'yin bitta commit'da o'zgardi.

**Yo'l-yo'lakay: `Qon` statistikasi paydo bo'lgan edi** — `PowerBook.lifestealFraction()`
hisoblanardi, lekin chizilmasdi. Kuch tanlash olib tashlanganda u ham ketdi: jon
so'rishning boshqa manbai yo'q edi (8.ax).

#### Qilinmagani, sababi bilan

**Har hero uchun boshqa tusdagi `dash`** (Ranger oltin, Knight po'lat, Mage ko'k)
— brifda "mumkin bo'lmasa bir xil qolsin" deyilgandi, va mumkin emas: yuqoridagi
qoida bo'yalgan ikonkaga rang bermaydi. Uchalasi bir xil.

#### Testlar

`aPaintedIconIsDimmedRatherThanColoured` — bo'yalgan ikonka rangi **kulrang**
bo'lishini talab qiladi (r == g == b). Rang berilsa darrov yiqiladi.
`everyPictureIsSquareAndTheSizeItsSheetWasCutAt` — 22 ta faylning hammasi
kvadrat, kutilgan o'lchamda va alfasi bor. `everySkillIconTheSettingsFileNamesIsThere`
endi **uchala hero**dan so'raydi — hali o'ynalmagan heroning ikonkasi aynan
sezilmay yo'qoladigani edi.

#### Eskilari o'chirilmadi

Lorc'ning 4 ta chiziqli ikonkasi `_unused/icons/` ga ko'chdi, `License.txt` va
sabab yozilgan README bilan. Ular zaxira: generatsiya qilingan to'plam — bitta
generatorning chiqishi, va CREDITS'da yozilganidek uning shartlari litsenziya
bilan bir xil narsa emas.


### 8.as Meteor uchun ikki qo'llik animatsiya, va skill animatsiyasi degan mexanizm

Mage'ning ultimatesi (Meteor) uchun Mixamo'dan ikki qo'llik sehr o'qish
animatsiyasi qo'shildi. Uch topilma taxminlarni o'zgartirdi.

#### 1. ✅ Rig muammosi umuman yo'q edi

Brifda "Mixamo rig, KayKit rig — mos kelmasligi mumkin" deb taxmin qilingandi.
FBX ichida esa **KayKit'ning o'z rigi** (`Rig_Medium`) va suyak nomlari
`mage.glb` bilan **aynan bir xil** (23 ta: `root, hips, spine, chest,
upperarm.l, handslot.r…`). Bitta ham `mixamorig:` yo'q — chunki animatsiya
Mage'ning o'ziga qilingan. O'lchandi: retargetdan keyin **23 ta trekning
hammasi** tushadi.

Bu omad, reja emas. Shuning uchun `fbx_to_glb.py` rigni **tekshiradi** va mos
kelmasa baland ovozda yiqiladi — aks holda chiqish fayli hech narsani
animatsiya qilmaydi va bu ekranda "sehrgar qimirlamayapti" bo'lib ko'rinadi.

#### 2. ⚠️ Skill ishlatilganda animatsiya umuman o'ynatilmasdi

Bu ishning asosiy qismi shu bo'lib chiqdi. Qurol otilganda `WeaponFired` →
`attackAnim` ishlardi; skill cast esa **faqat effekt** chizardi.

Yangi mexanizm: `DungeonSkill` blokida `CastAnim` + `CastSeconds`. Klip
qahramonga **avtomatik yuklanadi** — qahramonning klip ro'yxati o'ziniki **+
skilllari nomlagan har bir `CastAnim`**. Ya'ni nom bitta joyda yoziladi; ikkita
faylga yozilsa, ular ertami-kechmi bir-biriga mos kelmay qoladi.

**★ Sim formatiga 7-maydon qo'shildi.** `cast=` da `whose` bor edi — bu *belgi
kimga tegishli*, Meteor uchun `0` (yer), chunki meteor tushadigan yer bo'lagiga
tegishli. Ya'ni u **kim chaqirganini ayta olmaydi**. Bular ikki boshqa savol va
javobi kastеrdan uzoqqa qaratilgan har bir skill uchun boshqacha.

#### 3. ⚠️ Davomiylik teskari tomonga og'gan

Brifda "qisqa bo'lsa sekinlashtir" deyilgandi. Aslida klip **3.0 s**, Meteor'ning
`WindUpFrames = 45` esa **1.5 s** — ya'ni animatsiya **ikki barobar uzun**.
`CastSeconds = 1.5` qo'yildi, tezlik 2×. Shunda imo-ishora meteor tushgan
paytda tugaydi va uni **chaqirganday** o'qiladi; o'z tezligida qoldirilsa,
tushgandan keyin yana 1.5 s davom etib, chuqurga qarab qo'l silkitganday
ko'rinardi.

#### Tayoq — ikkalasi ham yashirinadi

Mage o'ng qo'lida tayoq, **chap qo'lida kitob** ushlaydi. Faqat tayoqni
yashirsam, ikki qo'llab sehr o'qiyotgan odamning bir qo'lida kitob qolardi.
Ikkalasi `setCullHint` bilan yashirinadi (yangi geometriya yo'q — ular
`getAttachmentsNode(bone)` da osilgan) va klip tugagach qaytadi.
`carryingAgainAt` `actionUntil` dan alohida: imo-ishora modelni egallashi
mumkin, lekin qo'lni bo'shatishi shart emas — o'yindagi boshqa har bir bir
martalik klip aynan shunday.

#### Konvertatsiya

`./gradlew :dungeon:convertAnimations` — Blender fon rejimida. Skript uch ish
qiladi: **meshni tashlaydi** (FBX ichida Mage'ning butun tanasi bor edi;
693 KB → 47 KB), **action nomini o'zgartiradi** (`Rig_Medium|mixamo.com|Layer0`
→ `Magic_Area_Attack`) va **rigni tekshiradi**.

Nomni to'g'rilash muhim: shu bilan yangi fayl **oddiy kutubxona** bo'lib qoladi
va mavjud `AnimationsFrom` + nom bo'yicha nusxalash yo'li o'zgarishsiz ishlaydi.
Aks holda bitta klipli fayllar uchun alohida mexanizm kerak bo'lardi.

Blender build bog'liqligi **emas** — task qo'lda ishga tushiriladi, jo'natiladigan
narsa esa commit qilingan `.glb`.

#### ★ Litsenziya — ataylab qabul qilingan chekinish

Mixamo — Adobe'niki, CC0 emas. **Bu aynan qahramon va maxluqlar olib
tashlangan savolning o'zi**: foydalanishga ruxsat bor, lekin animatsiyani
**alohida fayl sifatida tarqatish** aniq qamralmagan, ochiq git daraxti esa
shundir. Egasi bilib turib rozi bo'ldi va `CREDITS.md` da to'liq yozilgan.

Orqaga qaytarish arzon: manba FBX resources dan tashqarida, va `DungeonSkill
Mage R` dagi ikkita qatorni o'chirsangiz Mage meteorini avvalgidek qimirlamay
chaqiradi.

#### ★ Birinchi urinish ekranda ishlamadi: imo-ishora ustidan yozilardi

O'yinchi aytdi: "ult bosganda shunchaki qo'lini uzatib qo'yyapti". Sabab
`handleEvents` ning tartibida edi:

1. `skillsCastThisFrame` → mening `Magic_Area_Attack` im o'ynaydi.
2. **O'sha kadrning o'zida** hodisalar aylanmasi `WeaponFired` ni ko'radi —
   uni Meteor'ning o'zi jo'natgan (`SkillBook:851`), chunki wind-up'li skill
   ham qurol otadi — va `playOnce(attackAnim)` chaqiriladi.
3. `attackAnim` = `Ranged_Magic_Shoot`, ya'ni bir qo'lni uzatish. U yutadi.

O'lchov buni tasdiqladi: `Magic_Area_Attack` da o'ng qo'l **783°**, chap qo'l
**603°** buriladi (3.0 s, 91 kadr); `Ranged_Magic_Shoot` da esa 132° va 38°
(0.93 s) — aynan "bir qo'lini uzatish". Ya'ni klip to'g'ri qo'yilgan edi,
ustidan yozilgan.

Qoida: **o'yin nom bilan so'ragan imo-ishora umumiy zamahdan ustun**
(`gestureUntil`). Narxi — imo-ishora davomida flinch yutiladi. Bu to'g'ri
tomoni: imo-ishorani o'yin nom bilan so'ragan, flinch'ni esa klient o'zi
qo'shadi, va birinchisini butunlay yo'qotish yomonroq savdo.

**Bu buning testi yo'q, va sababini yozib qo'yaman:** bu klientdagi kadr ichidagi
tartib nuqsoni — uni ushlash uchun ishlayotgan ilova, timer va composer kerak.
`playOnce` dagi bitta qatorli qo'riqchini test qilish tartibni emas, o'sha
qatorni tekshirardi. Klip to'g'riligini esa yuqoridagi o'lchov tasdiqlaydi.

#### ★ Ikkinchi nuqson: sehrgar yotib animatsiya qilardi

O'yinchi ikkinchi marta aytdi. Sabab — o'q (axis) mos kelmasligi, va u
`root` suyagida edi:

| Klip | `root` burilishi | kalitlar |
|---|---|---|
| KayKit'ning barcha kliplari | **0°** | 2, doimiy |
| Import qilingan klip | **x = −90°** | 3, doimiy |

FBX Z-up, glTF esa Y-up. Blender eksporteri bu o'girishni `root` ga yozadi —
o'z faylida to'g'ri, lekin `root` i birlik bo'lgan modelga retarget qilingandan
keyin u **o'girish emas, buyruq** bo'lib qoladi, va buyruq: *yot*.

**Blender tomonidan tuzatib bo'lmadi.** Armature obyektining transformini
tozalash ham, teskari +90° berish ham natijani o'zgartirmadi — eksporter
o'girishni obyekt nima deyishidan qat'i nazar yozadi.

Yechim `AnimationLibrary` da, va u **allaqachon mavjud qoidaning ikkinchi
yarmi**: `heldInPlace` root ning gorizontal siljishini tashlaydi, chunki
*qayerda turishini simulyatsiya hal qiladi*. Endi `facingIsTheGames` root ning
burilishini ham tashlaydi, chunki *qayerga qarab turishini ham simulyatsiya hal
qiladi*. Root bu joylashuv suyagi — tanani burmoqchi bo'lgan har bir klip
hips ni buradi.

Mavjud kutubxonalarga ta'siri **nol**: ularning root i allaqachon birlik.
O'lchov bilan tasdiqlandi — tuzatishdan keyin root 0°, qo'l esa hamon 783°
buriladi, ya'ni boshqa hech narsa tekislanmagan.

**Buning testi bor** (o'tgan nuqsondan farqli): `nothingAHeroBorrowsTurnsHimOver`
har qahramon oladigan har bir klipni retargetdan **keyin** tekshiradi va root da
burilish bo'lsa yiqiladi. Sabotaj bilan tasdiqlandi.

#### Testlar

`everyGestureASkillIsCastWithIsOnTheHeroWhoCastsIt` — eng muhimi. Klip skill
blokida, kutubxonalar esa qahramon blokida nomlanadi va **hech narsa ikkisi mos
kelishini tekshirmaydi**. Test retarget qilingan klipdan so'raydi, kutubxonadagi
klipdan emas: klip mavjud bo'lib turib **hech narsaga tushmasligi** mumkin, va
ekranda bu ikkisi bir xil ko'rinadi. Sabotaj bilan tasdiqlandi (nomdagi bitta
harf).


### 8.at Mana — skilllar endi resurs sarflaydi

Skilllar faqat kuluar bilan cheklangan edi: kuluar tugasa cheksiz. Endi har
qahramonning mana hovuzi bor, har skill narxga ega, va hovuz vaqt bilan tiklanadi.

#### ★ Engine'ning puli ishlatilmadi, va nega

`RtsPlayer` da `money` bor — `deposit`/`withdraw` bilan. **Ikki jihatdan mos
kelmadi:**

1. U **o'yinchiga** tegishli, mana esa **jonzotga**. Bugun har o'yinchida bitta
   qahramon bor, ya'ni ikkisi ustma-ust tushadi — lekin mana sog'liq kabi
   jonzotning xossasi, va maxluqqa skill berilsa u o'yinchi emas.
2. Unda **shift ham, tiklanish ham yo'q** — aynan shu ikkisi manani byudjetdan
   *ritm*ga aylantiradi. Ularni qo'shish `rts` ni o'zgartirishni talab qilardi,
   bunga esa ruxsat yo'q.

Shuning uchun `HeroProgress`/`GrowableBody` naqshi: holat jonzotda
(`SkillBook`), daraja arifmetikasi esa uni biladigan yagona joyda
(`HeroProgress`), xuddi zirh va qurol bonusi kabi.

#### Determinizm — butun son, float emas

Tiklash tezligi **soniyasiga o'ndan bir** da saqlanadi, `manaCarry` esa o'sha
birlikda: har kadr tezlik carry ga qo'shiladi, carry bir soniyalik o'ndan birga
yetganda bitta butun ball tushadi. 70 tenths — aniq 7.0/s, har mashinada,
abadiy. `mana += 7f / 30f` esa yo'q.

**Nega o'ndan bir, butun ball emas:** darajaning eng kichik qadami butun ball
bo'lsa, ritsar 3 dan 4 ga sakraydi — bu uchdan bir, va 15-darajaga borib u
sehrgardan tez tiklanadigan bo'lib qolardi.

`theSameRunCastsTheSameWayTwice` — bir seed, bir xil tugmalar, checksum
solishtiriladi. Birinchi float da yiqilardi.

#### Sig'im va tarkib ajratildi

Bu testdan chiqdi. Daraja bilan **o'sgan** hovuz farqni sovg'a qiladi (daraja —
sovg'a, to'ldirish emas); **yo'qdan yaratilgan** hovuz esa hech narsa sovg'a
qilmaydi, chunki jonzot to'la boshlaydimi degan qaror bu modulniki emas. Yangi
tanani `HeroProgress` to'ldiradi — yangi run va har yangi qavat, ya'ni sog'liq
qanday ishlasa shunday.

#### Balans, va noto'g'ri o'lchov

Boshda **xato metrika** ishlatdim: "eng arzon skillni to'lay olmaydigan kadrlar
ulushi". U Rogue uchun 80%, Mage uchun 0% berardi — go'yo Rogue och qolgan. Aslida
u faqat *Rogue ning arzon skilli tez-tez tayyor bo'lishini* o'lchayotgan edi.

Solishtirsa bo'ladigan raqam — **talab**: har skill qaytishi bilanoq quyilsa,
soniyasiga qancha mana ketadi, soniyasiga qancha qaytadi.

| Qahramon | Hovuz | Tiklash | Talab | Ulush |
|---|---|---|---|---|
| Rogue | 80 | 6.5/s | 13.2/s | 49% |
| Knight | 50 | 4.0/s | 7.7/s | 52% |
| Mage | 120 | 7.0/s | 12.2/s | 57% |

Uchalasi ham ~yarmi. Jumla shu: **mana qila oladiganingni ikkiga bo'ladi, qaysi
yarmini tanlash senda.** Rogue va Knight shu darajaga yetish uchun ko'tarildi
(5.0→6.5 va 3.0→4.0). Bu endi taxmin emas, `nobodyCanSustainEverythingAtOnce`
testi.

Ikki chegara ham test: har qahramonning **eng arzoni o'z kuluarida barqaror**
(tayanadigan narsasi bor), va **to'rttasi birga hovuzga sig'maydi** (tanlov bor).

#### HUD

Ko'k bar (`#3E6FA8`) HP bilan XP orasida — sarflanadigan narsa, shuning uchun
jamg'ariladigani bilan emas, qolgani bilan o'qiladi. Har uyaning burchagida narx.

**Uch xil "ishlamaydi" uch xil ko'rinadi:** *yopiq* — sotib olinmagan; *kuluar* —
qaytyapti va qachonligini aytadi; **och** — tayyor, lekin tepadagi barni kutyapti.
Ochni kuluar kabi chizish "kut" degan bo'lardi, holbuki bu uyani kutish hech narsa
bermaydi — shuning uchun u ko'kimtir yuviladi va **narxi qizarib yorqinlashadi**,
qolgan hamma narsa so'nayotganda.

Tooltip'da mana qatori bitta chaqiruvga tushdi — `row(...)` allaqachon
`hozir → keyingi` naqshini beradi, ya'ni `48 → 62` tekin keldi.

#### Fikr-mulohaza

Rad etilganda bar chaqnaydi va interfeys kanalida past ovoz. Kadr bilan
belgilanadi (cast kabi), aks holda sekundiga o'ttiz marta yangrardi. Panel
*qachon* ekanini biladi (qatorni o'qiydigan o'zi), ilova *nima* ekanini (ovozlar
uniki).

#### Rad etish qurollantirishdan *oldin*

Boshda tekshiruv faqat cast qilinadigan joyda edi — mantiqan to'g'ri, amalda bir
qadam kech. Chunki qurollantirish (arm) alohida qadam: kursor o'zgaradi, skillning
radiusi polga chiziladi, panel uyasi yonadi. Ya'ni 40 mana yetmayotgan ult ham
**nishonga olardi**, keyin bosilgan sichqonchani yutib, hech nima qilmasdi. Bu
"hovuz bo'sh" emas, "tugma buzuq" bo'lib ko'rinadi.

Endi `readyToCast` narxni ham so'raydi — bu panelda, chunki panel nima ko'rsatayotgan
bo'lsa shuni bilishi kerak: uya so'ngan bo'lsa, u nishonga ham olmasin.

**Lekin shunda jimlik paydo bo'ldi:** cast simulyatsiyaga yetib bormaydi, demak
uning rad etishi ham qaytib kelmaydi. Shuning uchun `denyForMana` — o'sha chaqnash
va o'sha ovoz, faqat klient tomonidan ko'tariladi.

Uch xil rad etishdan **faqat bittasi** shunday javob oladi: kuluar o'zi supurilib
sanab turadi, yopiq uya esa qaysi daraja ochishini yozib turadi — ikkalasi ham
u qarab turgan joyda. Bo'sh hovuz esa panelning narigi chekkasidagi barda yozilgan,
shuning uchun uni **bosgan joyida** aytish kerak.

#### Tiklash manbalari

- **Loot** — `MANA` turi, ya'ni **kattaroq hamyon**, flakon emas. Sumkadagi hamma
  narsa doimiy, bu esa sumkaning o'zi. To'ldirish tekin keladi: o'sgan hovuz
  qo'shganini sovg'a qiladi.
- **O'ldirish uchun mana** — bor, lekin **0** da jo'natiladi. Bu qo'rqoq emas,
  qiziqroq sozlama: o'ldirish uchun to'lash manani jang mukofotiga aylantiradi,
  holbuki u tanlov qildirish uchun. Bu tomonda o'ldirish hodisasi yo'q
  (`ExperienceModule` — `rts` niki), shuning uchun **tajriba o'sishi** kuzatiladi;
  bu o'yinda uni boshqa hech narsa oshirmaydi.
- **Qavatga tushganda va yangi run'da** — to'liq, sog'liq kabi, o'zidan kelib
  chiqadi.

#### ★ Dushmanlar uchun knob qo'shilmadi

Brif `UsesMana = no` ni so'ragandi. Tekshirdim: `SkillBook` **faqat uch
qahramonda** bor, ya'ni birorta maxluq skill ishlatmaydi. Mavjud bo'lmagan holat
uchun bayroq — CLAUDE.md aniq taqiqlaydigan narsa ("no flags for cases that don't
exist yet"). Xulq allaqachon `UsesMana = No`: mana faqat `HeroProgress` orqali
beriladi. Chok esa `poolOf` — maxluqqa skill bergan odam unga hovuz berish-bermaslikni
o'zi hal qiladi.

### 8.au HUD v3 — panel qayta tuzildi, unit barlari dunyodan ekranga ko'chdi

Maketga (`duke-dungeon-hud-v3.html`) keltirildi. Ikki qism: pastki panel va
har jonzot boshidagi bar.

#### Panel — nima qayerga ko'chdi

HP va mana **portret ostiga** tushdi, portret ustunining kengligida. Uchovi
bitta blok: kim, qanchalik tirik, qancha manasi bor. Ilgari ular o'rta ustunda
edi — faqat shu sababdanki, barlar keng edi.

Ularning o'rnida **bitta qalin XP bar** (10 → 30 px). U paneldagi yagona
**butun run** haqidagi bar — jon ham, mana ham qaytadi, bu esa hech qachon
orqaga ketmaydi — va ikkala uchida ham harf sig'adigan yagona bar. Aynan shu
portret ostidagi daraja nishonini olib tashlashga imkon berdi: daraja ikki
joyda yozilayotgan edi, endi ko'z allaqachon turgan joyda, chapda; o'ngda esa
darajaga qancha qolgani.

To'ldiruvchida **qiya shtrix**, va u har safar qaytadan kesiladi, cho'zilmaydi:
qiyalikni cho'zsang u burchagini o'zgartiradi, bar to'lgan sari tikroq bo'ladigan
shtrix esa "to'lyapti" emas, "noto'g'ri chizilgan" bo'lib o'qiladi. Faqat
to'lgan eni butun pikselga siljiganda qayta kesiladi.

Ism **Cinzel**da — o'yin menyusi uchun nomlagan shriftning o'zi. Paneldagi
boshqa hamma narsa oddiy shriftda qoladi: qolganlari **o'qiladigan** son va
so'z, bu esa **tanib olinadigan** sarlavha. Unvon katta harfda va harflari
oralatib yoziladi — jME'da tracking yo'q, shuning uchun havo qo'lda qo'yiladi.

Ko'rsatkichlar 2 ustundan **4 ustunga**, har biri bitta qatorda.

> ★ **Ustun kengaydi (292 → 424) va bu o'lchovdan chiqdi.** Maket to'rttasini
> ensizroq joyga sig'diradi, chunki u **Barlow Condensed**da terilgan, bizniki
> esa yo'q. Klientdagi haqiqiy shriftda "Zarba 129" — 45 px, orasida bo'shliq
> ham kerak. Harfni kichraytirish o'rniga kenglik bilan to'landi: panel hech
> kim kattalashtira olmaydigan yagona joy.

#### Unit barlari — billboard emas, ekran fazosi

Eski bar jonzotga ulangan ikkita billboard kvadrat edi va faqat shikastlanganda
ko'rinardi. Endi: medalyon + segmentli bar + mana bar + nom, hammasi **interfeys
tekisligida**. Sabab zarar raqamlaridagi bilan bir xil — uzoqlashgani uchun
kichrayadigan ko'rsatkich ko'rsatkich bo'lishdan to'xtaydi — va billboard bar
ichida harf ushlab turolmaydi: qirq qadamdan nom to'rt piksel bo'lardi.

**Narxi kadr bo'yicha o'ylangan** (qavatda 40 jonzot, har bar o'nlab bo'lak):

- har to'rtburchak **bitta ulashilgan kvadrat**, faqat masshtablanadi
- chiziqchalar **son + en juftligi bo'yicha** mesh — jonzot bo'yicha emas.
  Ikki skelet bitta meshni baham ko'radi, skelet bilan boss esa yo'q
- XP halqasi — aylanishning o'ttiz ikkidan bir qismiga bitta mesh, va halqa
  **faqat qahramonda** bor
- harf faqat **o'zgargandagina** qaytadan beriladi: `BitmapText` har
  `setText` da meshini qayta quradi, "30/30" esa sekundiga o'ttiz marta
  o'zgarmagan meshni qayta qurardi
- pool eng band kadrda to'xtaydi va keyin qayta qurilmaydi
- ekrandan tashqaridagiga bar chizilmaydi; tuman ostidagisi esa umuman
  kelmaydi — snapshot allaqachon ko'rinadigandan quriladi

#### Segment jadvali, va maketning o'zi bilan ziddiyati

Chiziqchaning qiymati **umumiy jadvaldan** olinadi, jonzotdan emas — butun gap
shunda: bir maxluqning barini sanagan o'yinchi keyingisini sanamasdan o'qiydi.

> ★ **Maketning prozasi 8–20 chiziqcha so'raydi, yonidagi jadvali esa 1–10
> beradi** — buni maketning o'z "Segment soni" ustuni yozib turibdi. Brif
> prozani takrorlagani uchun proza bajarildi. 8–20 ni ushlash arifmetikani
> to'liq belgilaydi: LO dan HI gacha V lik bo'laklar uchun HI/V ≤ 20 va
> LO/V ≥ 8 kerak, bu esa faqat HI ≤ 2.5×LO bo'lganda mumkin. Shuning uchun
> pog'onalar ikki yoki ikki yarim baravar ko'tariladi va ularning soni
> yettita emas, **to'qqizta**.

**Uzunlik chiziqchadan hisoblanmaydi**, garchi shunday qilish bitta qoida
bo'lardi. Pog'onali jadval monoton emas: 90 jon o'ntalab — 9 chiziqcha, 170
jon yigirma beshtalab — 6. Kuchsizroq maxluq uzunroq bar kiyib olardi.
Uzunlik ikki langar orasida **logarifmik**: diapazon 30 dan 1320 gacha, ya'ni
qirq olti barobar, va proporsional bar uning to'qqiz ushdan birini birinchi
choragiga tiqadi.

Jadval o'yin **haqiqatan yasaydigan** narsaga qarshi tekshiriladi: har maxluq
uchraydigan har chuqurlikda, har qavatning bossi o'z tikroq egri chizig'ida,
va qahramon o'sishining ikki uchi. 42 o'lcham, 30–1320 jon, 8–19 chiziqcha.

#### O'yin ishga tushirildi, va test ko'rmagan uchta xato chiqdi

Foydalanuvchi so'ragani uchun o'yin ishga tushirildi. Uchala xatoning ham
yonida "hammasi joyida" deb turgan testlar bor edi.

1. **Medalyon umuman chizilmagan.** Bitta alomat, ikkita sabab, shuning uchun
   birinchi tuzatish ishlamagandek ko'rindi. Bar bo'laklari har biriga o'z z'i
   berilib ustma-ust qo'yiladi — lekin z aynan **chuqurlik buferi**
   tekshiradigan narsa, shuning uchun birinchi chizilgan bo'lak o'z chuqurligini
   yozib, ortidagilarni o'chirib tashlardi. Depth-test o'chirilgandan keyin ham
   disk yo'q edi: yassi shaklning **ko'rinadigan tomoni** burchaklar tartibi
   bilan belgilanadi, disk yasaladigan sektorlar to'plami esa qolgan hamma narsa
   yasaladigan kvadratga teskari o'ralgan.
2. **0 tajribali qahramonning bari to'la edi.** Bitta cull-hint'ning ikkita
   egasi bor edi: `fillTo` bo'sh barni yashiradi, `showOnlyWhatTheCardHas` esa
   kartada daraja bo'lsa uni qaytib ko'rsatardi. Yashirilgan to'ldiruvchi
   hech qachon masshtablanmagan, shuning uchun qaytgani **oxirgi eni** — yangi
   qahramonda esa bu to'liq bar. 10 pikselli bezakda yillar davomida
   sezilmagan, 30 pikselda esa panelning o'rtasidagi yagona narsa bo'ldi.
3. **"Tezlik 29" → "Tezli29".** So'z qutisining chapidan, son esa kaltaroq
   qutining o'ngidan chiziladi — ikkovi bitta katakning qarama-qarshi uchidan
   bir-biriga qarab o'sadi. Ikkalasi ham aynan qo'yilgan joyida edi.

Va yana ikkita test **noto'g'ri narsani** o'lchayotgan edi:

- uchinchisi uchun yozilgan test **boshqa barni** tekshirardi: uchala
  to'ldiruvchi ham "fill" deb nomlangan, shuning uchun u jon barini topgan — u
  esa to'la, va to'la bo'lishi kerak. (Bu ikkinchi marta: birinchisi "badge"
  edi.) Endi har bo'lak nomlangan va testlar nom bilan so'raydi
- ustma-ust tushishni **joylashuv** sifatida tekshirib bo'lmaydi: jME
  `BitmapText` ga joyni qaytarib bermaydigan quti orqali beradi va chizilmaguncha
  chegara ham bermaydi. `getWorldTranslation` ni o'qigan test paneldagi har
  qatorning boshlanish nuqtasini olib, qahramon ismi birinchi ko'rsatkichga
  kirib ketyapti deb xabar qildi. O'lchash mumkin bo'lgani — satr **eni**,
  shuning uchun tekshiruv arifmetikaga ko'chirildi

#### Skill uyalari

Uya 58, ultimate 64; rasm uyani chetidan bir pikselgacha to'ldiradi. Ilgari u
uyaning olti ushdan biri edi — 256 da chizilgan san'at o'zi uchun mo'ljallangan
maydonning uchdan birida o'qilardi. Ulush emas, **piksel**: ikki uya har xil
o'lchamda, ulush esa ularga hech kim ayta olmaydigan sabab bilan har xil hoshiya
berardi.

Ultimate **ikki uchidan ham baland**. Ilgari uyalar umumiy tepadan osilardi,
ya'ni ultimate faqat **pastga** cho'zilib kattaroq bo'lardi — bu esa kattaroq
uya emas, **sirg'alib ketgan** uya bo'lib o'qiladi.

### 8.av Skill effektlari — qatlamlangan zarrachalar

Uchala qahramonning to'rttala skilli va ular otadigan narsalar — o't shari,
meteor, olovli o'q — endi **qatlamlardan** chiziladi. Teksturalar Kenney Particle
Pack (CC0), `effects/particles/`, 256² ga kichraytirilgan; nega — `CREDITS.md` da.

#### Effekt — bir nechta qatlam, har biri o'z ishini qiladi

Bitta turdagi zarracha portlash emas, portlashning **diagrammasi** bo'lib
ko'rinadi. Portlash bo'lib o'qiladigani — bir lahzada tushgan beshta narsa:
ko'rilmasdan so'nadigan chaqnash, gurillab o'chadigan olov, sekinroq ko'tarilib
undan uzoq yashaydigan tutun, hammasidan uzoqqa otilgan uchqun va qanchalik
yetganini aytadigan halqa. Shuning uchun effekt `dungeon.ini` da
`DungeonEffectLayer <effekt> <nom>` bloklari to'plami. Yangi effekt — fayldagi
bloklar; yangi **tur** — kod.

| Tur | Nima |
|---|---|
| `BURST` | hammasi bir nuqtadan birdan, va tugaydi |
| `TRAIL` | harakatlanayotgan narsa ortidan chiqadi va chiqqan joyida osilib qoladi |
| `RING` | polda yotadi va tashqariga ochiladi |
| `AURA` | jonzot atrofida, u bilan yuradi, skill qancha davom etsa shuncha |
| `IMPACT` | chaqnash: bir-ikki katta shakl ochilib so'nadi |
| `ARC` | kesik: polda, zarba ketgan tomonga qarab yotadi |
| `BEAM` | yugurishning boshidan oxirigacha chiziq |
| `MARK` | polda yotadi va turadi — ogohlantirish, kuygan dog' |
| `LIGHT` | yorug'lik: chaqnab so'nadi yoki yonayotgan narsa bilan uchadi |

Joy (`At`): `SPOT FROM TO BOTH PATH CASTER CAUGHT`; `CAUGHT` — skill yetgan har
dushmanning ustida. **Qachon** o'ynashini fayl aytmaydi, u turdan kelib chiqadi:
skill ko'rinishining hamma qatlami cast'da; otilgan narsaning `TRAIL`, `AURA` va
`Seconds`siz `LIGHT` qatlamlari u uchayotganda u bilan birga; qolgani u tugagan
lahzada. ★ `ObjectDied` da emas: o'q, o't shari va meteor belgisining tanasi yo'q,
dvijok esa tanasiz narsani "o'ldi" demasdan olib tashlaydi — portlash shu hodisaga
osilgan paytda o'yinda bir marta ham chizilmagan.

**Va tugash hali tegish emas.** Simulyatsiya o't sharining zararini faqat u
jonzotga yetganda, o'sha jonzot atrofida beradi; devorga urilgan yoki masofasi
tugagan o't shari hech kimga zarar bermaydi. Shuning uchun uchgan snaryad faqat
o'sha kadrda narigi tomonga zarba tushgan va zarba u oxirgi chizilgan joydan
`StrikeWithin` ichida bo'lsa portlaydi — va portlash zarba tekkan jonzotning
ustida chiqadi, chunki zarar markazi o'sha. Qo'yilgan joyida yotgan belgi
(meteorniki) esa ostida kim bo'lishidan qat'i nazar tushgan joyida portlaydi
(`Landing`).

#### Zarracha bir marta yoziladi, qolganini shader hisoblaydi

jME'ning `ParticleEmitter`i har zarrachani har kadr protsessorda siljitadi va
rang-o'lchamni ikki qiymat orasida to'g'ri chiziq bo'ylab o'zgartiradi.
Birinchisi noutbukni isitadi (Mac'ning qizishi brifning alohida talabi edi),
ikkinchisi effektni mexanik ko'rsatadi. `ParticleLayer` da zarracha tug'ilgan
kadrda to'rt burchagiga **bir marta** yoziladi — qayerda, qanday tezlikda, qachon,
qancha yashaydi — `Particles.vert` esa har kadr "u hozir necha yoshda" deb so'rab
qolganini hisoblaydi: havo tormozi (yopiq formula), og'irlik, egri chiziq bo'ylab
o'lcham va rang, paydo bo'lish va so'nish, aylanish, nafas. Tug'ilmagan yoki o'lgan
zarrachaning uchburchagi ekran tashqarisidagi bitta nuqtaga yig'iladi — birorta
piksel ham tekshirilmaydi.

#### Qancha davom etishi va qanchalik keng — skillning o'zidan

`Seconds` yozilmagan davomli qatlam vaqtini skilldan oladi (`Main.measureLooks`):
`DurationFrames`, bo'lmasa `WindUpFrames`, bo'lmasa `SlowFrames`. `Measure = Reach`
qatlamning o'lchami va radiusini skillning `Radius`ida sanaydi — `Size = 2` aynan
skill yetgan kenglik. O't shari va meteor portlashi zarar tegadigan maydonga
**aynan teng**: chetini chizadigan har halqaning eng yorug' chizig'i skill
`Radius`ida yotadi (test o'sha chiziqni halqaning o'z teksturasidan topadi), olov,
tutun va uchqunlar esa undan tashqariga uchmaydi (test ularni shader
harakatlantirgandek hisoblaydi). Otilgan narsaning effektiga uni otgan skillning
sonlari beriladi. Natijada meteor ogohlantirishi portlash bilan **aynan** bir xil keng va
tushish bilan aynan bir xil uzoq, muz esa tekkanlarda sekinlashuv qancha tursa
shuncha turadi. Ilgari bular ikkitadan son edi, yonida esa "birini o'zgartirsang,
ikkinchisini ham" degan izoh turardi.

> ★ **Mage W da sekinlashuv bor** (`SlowFrames = 90`, `SkillBook.chill`) — oldingi
> inventarizatsiya "yo'q" degan edi. Klientga kim sekinlashgani aytilmaydi, lekin
> to'lqin ichida kim turgani aytiladi, simulyatsiya esa aynan o'shalarni sekinlashtirgan.

#### Byudjet — maqsad emas, shift

- qatlam pool'dan beriladi va qaytariladi (o'lchami 2 ning darajasi); sahna
  jangda o'smaydi — 50 marta uch qatlamli cast'dan keyin 3 ta qatlam qurilgan
- yorug'lik **bitta** pool'da (`MaxLights = 8`), eski effektlar bilan umumiy. Pol
  shaderi 4 dan 8 ga kengaydi: polni ko'rmaydigan yorug'lik skeletni yoritib,
  uning oyog'i ostidagi toshni qorong'i qoldirardi
- `MaxParticles = 1500` dan oshsa yangi qatlam siyrakroq, keyin umuman chizilmaydi
- tuman ostida, `MaxDistance` dan uzoqda va ekrandan tashqarida boshlanmaydi.
  Tuman tekshiruvi markazni ham, atrofini ham so'raydi: ustun ortida portlagan o't
  shari olovini ustundan o'tkazib otadi
- dunyo qayta qurilganda hammasi birdan qaytariladi

#### Cover — yorug'lik bilan modda o'rtasi

Hamma qatlam bitta **premultiplied** blend bilan chiziladi; `Cover` orqadagining
qanchasini yashirishini aytadi: 0 — yorug'lik (qo'shiladi), 1 — modda (tutun
yopadi). O'yinda ko'rilgandan keyin qo'shildi: sof additiv olov och toshda **och
dog'** bo'lib qolardi — to'q sariqni kulrang polga qo'shsang to'q sariq emas, oqish
chiqadi. Polning bir qismini yashiradigan olov har qanday yerda rangini saqlaydi.

Katta billboard ko'zga tomon o'lchamining yarmicha **tortiladi**: yerga
o'lchamidan yaqin turgan karta polni kesib o'tadi, pol esa uni tekis chiziq bo'ylab
kesib qo'yadi — bu esa tutun bo'lagida bo'lishi mumkin bo'lmagan yagona narsa.

#### Tegish chaqnashi va kamera silkinishi

Narigi tomonning jonzotiga zarba tekkanda u bir lahzaga oqaradi (`HitFlash`):
jonzot tuguniga `Ambient` ustidan `MatParamOverride` qo'yiladi va o'z rangiga
qaytadi — yangi material ham, shader qayta kompilyatsiyasi ham yo'q. Raqam "qancha"
deydi, chaqnash "qaysi biri" deydi. `DungeonEffects Feel`: `HitFlashSeconds`,
`HitFlashStrength`, `HitFlashColour`, va `ShakeScale` (0 — kamera umuman silkinmaydi).

#### Ekranni egallamasdan ko'z bilan tekshirildi

GLSL faqat ishga tushganda kompilyatsiya bo'ladi, chiroyli-xunukni esa test
ko'rmaydi. O'yin **yashirin oynada** (`JmeContext.Type.OffscreenSurface`) ishga
tushirildi va kadrlar o'z framebuffer'imizdan PNG ga yozildi — ish stolida hech
narsa ochilmadi (texnikasi vaqtinchalik, commit qilinmagan). Topilganlarning birini
ham test ko'rmagan edi:

1. o't shari daraxt ortiga tushdi va `LineOfSight` tufayli portlashi umuman
   boshlanmadi → atrofini ham so'raydigan tekshiruv
2. olov och polda oqarib ketdi → `Cover`
3. katta tutun va olov kartalarini pol tekis chiziq bo'ylab kesdi → ko'zga tortish
4. baland `Cover` va to'q oxirgi rang olovni qo'ng'ir changga aylantirdi → yorqinroq uchlar
5. qatlam testi raqamlarni mahkamlagan edi va birinchi sozlashdayoq sindi → endi
   fayldagi blok matnining o'zini o'qiydi
6. meteor tushganda olov bulutining chetlari to'g'ri chiziq bo'lib kesilgandek
   ko'rindi. Taxmin qilinmadi, o'lchandi: pol atrofda tekis, polda yotgan
   qatlamlar alohida chizilganda toza — aybdor billboard'lar. `star_09` va
   `muzzle_*` teksturalarida butun karta bo'ylab 2–3 foizli alfa tumani bor; u
   kichik chizilganda ko'rinmaydi, yuz birlikli chaqnashda esa o'z nuri yoritgan
   pol ustida qirrali kvadrat bo'ladi → shader alfaning eng xira bir necha foizini
   tashlaydi, meteorning katta olov tillari (`muzzle_03`) esa olib tashlandi. Qolgan zinapoya esa olov emas, **pol** edi: to'liq kuchdagi portlash
   nuri toshni oppoq qilib yuborardi, pol shaderi esa tumanni shu yoritilgan rang
   ustiga katak-katak qo'yadi (`mix(lit, dark.rgb, dark.a)`) — oppoq tosh eslab
   qolingan tosh bilan zinapoya bo'lib uchrashardi → nurlar pasaytirildi, tuman
   tegilmadi

> ★ **Lekin demo haqiqiy yo'lni chetlab o'tgan edi.** U tushishni o'zi chaqirardi,
> shuning uchun o't shari va meteor demoda portlardi, o'yinda esa yo'q — buni
> foydalanuvchi o'yinda topdi. Endi portlash snaryad dunyodan yo'qolgan lahzada
> chiziladi, va bu o'yinning o'zi orqali tekshirildi: Mage qahramon, ochko sarflandi,
> Q va R o'z tugmalarining bog'lanishi orqali otildi — simulyatsiyaning o'z o'qi
> uchdi va tushdi. Bo'sh yerga otilgan o't shari portlamadi, skeletga yurib borib
> otilgani esa o'sha skeletning ustida portladi; meteor ostida hech kim yo'q
> bo'lsa ham tushgan joyida portladi. Haqiqiy yo'lda yana ikkita narsa ko'rindi: bir vaqtda boshlangan
> qatlamlar yozilgan tartibda chiziladi, meteorning kuygan dog'i va tutuni esa
> olovdan keyin yozilgan edi — portlash olov ustiga yotqizilgan qora disk bo'lib
> ko'rinardi (endi pol, keyin tutun, oxirida olov va yorug'lik); va modelsiz
> snaryadga beriladigan eski shar meteor ogohlantirishining o'rtasida polda oqish
> disk bo'lib yotardi — qatlamli ko'rinishga endi bo'sh tana beriladi.

#### Brif bilan ziddiyatlar

- **Ranger R** — brifda "teshib o'tuvchi o'qlar, energiya chizig'i"; skill esa
  `EMPOWER`, yo'nalishi yo'q. Nima bo'lsa shunday chizildi: atrofida oltin nur,
  boshi ustida aylanayotgan nishon, oxirgi beshdan birida miltillaydi
- **Ranger W** — brifda "tushayotgan o'qlar"; skill `AREA_DAMAGE`, kamonchining
  **o'z atrofida**. O'q yomg'iri skill yetgan doira ichiga tushadi
- **Mage W** — yuqorida: sekinlashuv haqiqiy

#### Ochiq qolganlar

- qatlamli effektning `DungeonEffect` blokidagi eski `Kind`/`Wave*`/`Mark*`
  qatorlarini hech narsa o'qimaydi (faqat `ShakeSeconds`/`ShakePower`) — fayldan
  olib tashlanmadi
- monstrlarning otishlari (`MageFire`, `MageFireGreater`) va mage'ning oddiy o'qi
  (`ArcaneBolt`) hali eski retseptlarda — va ularning `IMPACT_BURST` i shu
  `ObjectDied` xatosi tufayli hech qachon chizilmagan: eski yo'l hali ham o'sha
  hodisaga osilgan
- effektlar sahnalashtirilgan demoda ko'rildi, haqiqiy jangda emas: tegish
  chaqnashi, muz tekkanlardagi `CAUGHT` qatlami va girdobning har zarbadagi
  halqasi dushmanlar bilan hali ko'z bilan ko'rilmagan (testlari bor)
- demo jabduqlari (yashirin oyna, kadr yozish) commit qilinmadi
- kuchli nuqtaviy yorug'lik tuman chegarasida hali ham katak-katak ko'rinishi
  mumkin: pol shaderi yorug'likni tuman xaritasidan oldin qo'shadi. Tuzatish joyi —
  `FoggedTerrain.frag` (yorug'likni tuman bilan birga so'ndirish), lekin bu tumanning
  mavjud ko'rinishini o'zgartiradi, shuning uchun qilinmadi

### 8.aw Nur ustuni (`PILLAR`) — daraja, boss, yangi qavat

Warcraft III / Dota uslubidagi tik nur ustuni. Qatlamli effekt tizimiga **yangi
tur** sifatida qo'shildi — mavjud turlar va skilllarning ko'rinishi o'zgarmadi.

#### Qanday chiziladi

- `PILLAR` qatlami poldan `Height` gacha turadi va o'z tik o'qi atrofida kameraga
  buriladi (`Particles.vert` dagi `PILLAR` tarmog'i). Ikki yo'nalish — bitta
  mexanizm: `Direction = UP` da yuqori uchi poldan ko'tariladi, `DOWN` da pastki
  uchi tepadan polga tushadi. Harakatlanuvchi uch butun balandlikni umrining `Rise`
  ulushida `RiseEase` egri chizig'i bo'ylab bosib o'tadi (3 — tez boshlanib, sekinlab
  to'xtaydi).
- So'nish ham ease-out: `ColourEase > 1` yorug'likning ko'p qismini erta oladi,
  qolganini cho'zadi. To'g'ri chiziqli so'nish chiroq o'chirilgandek ko'rinadi.
- Bitta ustun — nur tayoqchasi, xolos. Har bir ko'rinish beshta qatlam: yerda
  kengayuvchi halqa (`RING`), keng va xira halo (`PILLAR`), ingichka oq-issiq yadro
  (`PILLAR`), yo'nalishga mos zarralar (`BURST`, `UP`/`DOWN`) va xonani yorituvchi
  chaqnash (`LIGHT`). Poldagisi birinchi yoziladi, har biri oldingisi ustiga chiziladi.
- `Follows = Yes` — qatlam kimning ustida bo'lsa, o'shanga ergashadi: yurib
  ketayotgan qahramon o'z nuridan chiqib qolmaydi. Standart qiymat avvalgidek —
  faqat `AURA` ergashadi.
- Ustun `Count` nechta desa, shuncha chiziladi. Pool to'rtta slot bersa ham,
  qolganlari qorong'i turadi: nur additiv, to'rttasi ustma-ust bo'lsa to'rt barobar
  yorqin chiqadi.
- `cast(..., scale)` — o'lchamlar, reach va ustun balandligi `Scale` ga
  ko'paytiriladi. Boss yengilgandagi ustun — daraja ustunining o'zi, faqat kattaroq.
- Tekstura: Kenney Particle Pack'da ustunga o'xshash narsa yo'q (80 tasi
  o'lchandi — chaqmoq, kalta chiziqlar, halqalar). Ikkita gradient
  `dungeon/art/effects/pillar_textures.py` bilan yasaldi: `pillar_core.png` va
  `pillar_halo.png`, oq rangda, ikkala uchi yumshoq.

#### Qaysi lahzalarga bog'langan

Simulyatsiyada daraja, boss yoki qavat hodisasi yo'q: `WorldSnapshot.events` da
faqat `ObjectDied` va `WeaponFired` bor. Lekin kerakli hamma narsa status qatorida
turibdi. `RunMoments` (klient) har kadr qatorni oldingisi bilan solishtiradi —
simulyatsiyaga tegilmadi.

| Lahza | Nimadan seziladi | Retsept |
|---|---|---|
| `LevelUp` | `\|hero=` dagi daraja oshdi (tanlangan karta emas) | `GoldenPillar` — UP, oltin |
| `BossDown` | `ObjectDied` id si oxirgi `\|boss=` ga teng | `GoldenPillar`, `Scale = 1.5` |
| `Arrived` | run boshlandi, qavat o'zgardi yoki dunyo qayta qurildi | `Descent` — DOWN, mash'al rangi |

- Bog'lash — `DungeonMoment <Nom>` bloki (`Effect`, `Scale`). Yangi joyda ishlatish
  kod emas, INI bloki. Bir kadrda bitta odamga faqat eng katta lahza chiziladi:
  bossni yiqitgan zarba odatda darajani ham oshiradi.
- Ultimate ochilishi alohida lahza emas — u daraja oshganda ochiladi
  (`LevelPerRank = 4`, brifdagi 5 emas), shuning uchun daraja ustuni buni ham bildiradi.
- `HolyLight` (DOWN, oq-oltin) qo'shildi, lekin hech narsaga bog'lanmagan. O'yinda
  hozir davolash yo'q; foydalanuvchi qarori — hozircha ishlatilmaydi. Birinchi
  davolash skilli uni o'z `Look` i sifatida nomlaydi.

#### Ovoz

- `level_up` endi `RunMoments` dan chalinadi. Avval u tanlangan kartadagi `|rank=`
  ga qarardi: skelet tanlangan paytda daraja oshsa, ovoz chiqmasdi, qahramon qayta
  tanlanganda esa kechikib chalinardi.
- Fayl — foydalanuvchining "Game assets" papkasidagi `SFX_1up07`: 0.9 soniyalik
  ko'tariluvchi notalar ketma-ketligi, `Gain = 0.3`. Litsenziyasi noma'lum —
  `CREDITS.md` da ochiq savol sifatida yozilgan.

#### Ko'z bilan tekshirildi (yashirin oynada)

1. Birinchi urinishda tekstura butun balandlikka yotqizilgan va uch ko'tarilgan
   sari ochilib borardi. Harakatlanuvchi uch esa teksturaning yorug' qismini
   kesardi — ekranda to'g'ri gorizontal chiziq paydo bo'lardi (kattalashtirilgan
   boss haloda eng yaqqol). Endi tekstura ustunning hozir bor qismiga cho'ziladi
   va ikkala uchi yumshoq.
2. Run boshidagi `Arrived` umuman ko'rinmadi. Qavat ko'rinishi (`look=`) qatorga
   bir necha kadr kech keladi, dunyo qayta quriladi va yonayotgan hamma narsa
   o'chadi (`buildTerrain` → `layered.clear()`). Endi har qayta qurishda
   `RunMoments.forget()` chaqiriladi va kelish qaytadan chiziladi.
3. Haqiqiy yo'l ham sinaldi (`XpBase = 1` faqat demoda): Mage monsterni o'ldirdi,
   daraja status qatoridan sezildi (`LEVEL 1 -> 9`) va ustun chizildi. Lekin kadr
   o'rtasini daraja oshganda chiqadigan kuch tanlash oynasi yopib turardi.
   Oyna olib tashlangach (8.ax) xuddi shu yo'l qayta sinaldi: daraja avval 9 ga,
   keyin 15 ga chiqdi, har safar oltin ustun qahramondan ko'tarildi va polda halqa
   ochildi. O'yin endi to'xtamaydi — o't sharining portlashi ham, monsterlar ham
   harakatda davom etdi.

#### Testlar

`LayeredEffectsTest` (+7: yo'nalish va balandlik, material, so'ralgancha ustun,
tozalanish, ergashish, masshtab, bir vaqtda 50 ta ustun), `RunMomentsTest` (8),
`DungeonEffectLayerTest` (+6: har bir lahza chiziladi, ustun yo'nalishi, 0.5–0.8
soniya, daraja ko'tariladi va kelish tushadi, boss darajadan katta, blok maydonlari).
`SelectedCardSoundTest` yangi yo'lga moslandi. `ProjectileEffectTest` — qatlamli
effekt endi `Kind`siz bo'lishi mumkin.

### 8.ax Daraja oshganda kuch tanlash olib tashlandi

Foydalanuvchi: "har level upda kuch tanlashni o'chirib tashla, u kerak emas".
Kuchlarning boshqa manbai yo'q edi — karta faqat daraja oshganda chiqardi — shuning
uchun oyna bilan birga butun tizim olib tashlandi. Aks holda hech kim yozolmaydigan
kuchlar kitobi qolardi.

- **Simulyatsiya:** `dungeon/power/` paketi butunlay ketdi (`Power`, `PowerEffect`,
  `PowerDraft`, `PowerBook`, `PowerChoice`, `ChoosePower`). `SkillBook`,
  `ArrowUpdate` va `FallingUpdate` endi `PowerBook` olmaydi. Kartasiz qahramonda
  skill zarari va kuluari 1 ga, zaryad 1 ga ko'paytirilardi, jon so'rish esa 0 edi.
  Ko'paytirishni olib tashlash run'dagi hech bir sonni o'zgartirmaydi. Zaryad
  mexanizmi (`chargesLeft`/`chargeCap`) ham ketdi — ikkinchi zaryadni faqat karta
  berardi. `Session` va `DungeonRun` endi `powers` saqlamaydi.
- **Status qatori:** `|pwWord=`, `|pw=`, `|offer=`, `|opt=` maydonlari va `Qon`
  statistikasi olib tashlandi.
- **Klient:** `LevelUpOverlay` (tanlash oynasi), u o'rnatgan pauza va kiritishni
  ushlab turish, `Hotkeys.onChoose` va panel ostidagi kuchlar qatori olib
  tashlandi. Skill ustuni endi plita ichida o'rtaga keladi.
- **INI:** `DungeonPowers` va 10 ta `DungeonPower` bloki, `PowersWord`,
  `ChooseWord`, `LifestealWord`, `LifestealIcon`, `power_offer` ovozi va uning fayli
  olib tashlandi. `power_taken` qoldi — u skill ochkosi sarflanganda chalinadi.
- **Testlar:** `PowerTest` o'chirildi. `HeroPanelTest`, `HeroStatusTest`,
  `HotkeysTest`, `PanelLayoutTest`, `PanelSkinTest`, `DungeonTilesTest` va
  `SightTest` yangi holatga moslandi.
- Daraja oshganini endi nur ustuni va ovoz bildiradi (8.aw) — o'yin to'xtamaydi.

### 8.ay Skelet-Mage — skill ishlatadigan birinchi monster

Shu paytgacha dushmanlar faqat yaqinlashib urardi yoki qurolidagi o'qni otardi. Endi
monster o'z skillini o'zi qaror qilib ishlatadi. Mexanizm umumiy: yangi skill yoki
yangi kaster — INI ishi.

#### Mexanizm

- **Skill bog'lash — yangi tizim yo'q.** `SkillBook` qahramonga emas, shablon nomiga
  bog'langan edi (`DungeonSkill <Shablon> <Tugma>`), shuning uchun monsterga ham shu
  yo'l bilan beriladi: `monsters.ini` da `Update = SkillBook Tag`, `dungeon.ini` da
  `DungeonSkill SkeletonMage Q`. Mana standart holatda o'chiq, monster faqat kuluar
  kutadi.
- **Qaror `MonsterBrain` da**, `DungeonMonster` dagi yangi kalitlar bo'yicha:
  - `Skill` — qaysi tugma;
  - `SkillDistance` — eng yaqin va eng uzoq otish masofasi;
  - `KeepDistance` — saqlanadigan masofa oralig'i;
  - `MaxPerRoom` — bitta xonada nechtasi bo'lishi mumkin.

  Har kadrda nishon tanlanadi, keyin shartlar tekshiriladi: masofa ichida,
  `SightLine.clear` (devor ortidan otmaydi), kuluar tayyor. Shunda `SkillBook.cast`
  qahramon turgan joyga otadi. Otgandan keyin `SwingFrames` davomida joyida turadi,
  shunda otish harakati ko'rinadi.
- **Nishon:** eng yaqin dushman. `findClosest` teng masofada kichik `ObjectId`ni
  allaqachon tanlar ekan (`PartitionManager`) — test bilan mustahkamlandi.
- **Masofa saqlash (`KeepingDistance`):**
  - qahramon juda yaqin kelsa, monster undan teskari tomonga, oraliqning uzoq
    chetiga chekinadi;
  - to'g'ri orqada tosh bo'lsa, `RetreatTurnDegrees` (30°) qadam bilan chapga,
    keyin o'ngga `RetreatTurns` (3) martagacha sinaydi;
  - hammasi yopiq bo'lsa, joyida jang qiladi;
  - qahramon uzoqlashsa, ortidan boradi; oraliq ichida turib otadi.

  Burchaklar qat'iy tartibda sinaladi va `StrictMath` bilan hisoblanadi.
- **Chuqurlik:** `DepthBonus` `combat` paketiga ko'chdi va skill zarari ham unga
  ko'paytiriladi. Avval skill zarari chuqurlikni hisobga olmasdi. Bu bonus faqat
  monsterlarda bor, qahramon zarari o'zgarmadi.
- **Snaryad tezligi skill blokida:** `ProjectileSpeed`; 0 bo'lsa qahramonning
  `HeavySpeed`i olinadi.
- **Xona chegarasi:** chegaraga yetgan tur o'sha xonada qur'adan chiqariladi. Hech bir
  tur chegaraga yetmaguncha qur'a avvalgidek, ya'ni eski seed'lar o'zgarmaydi. Stage
  fayllariga ta'sir qilmaydi.

#### Skelet-Mage

- **Ko'rinish:** `skeleton_mage.glb` va tayoq, iliq qizil-to'q sariq `Tint`
  (Revenant oqish-ko'k).
- **Statistika:** jon 40, tezlik 13 (eng sekin qahramon — 21), tayoq bilan zaif urish
  (3). 2-chuqurlikdan chiqadi, bitta xonada 2 tadan ko'p emas.
- **Masofalar:** `SkillDistance = 20 60`, `KeepDistance = 35 55`. Oraliqni
  foydalanuvchi tasdiqlagan; brifdagi 200–400 bu o'yin o'lchamida ko'rish
  masofasidan ancha uzoq edi.
- **Olov shari:** `SKILLSHOT`, zarar 24, portlash radiusi 16, tezlik 60 (Mage'niki
  120, shuning uchun ko'rib qochish mumkin), kuluar 4 soniya.
- **Effekt:** `SkullFireball` — Mage Fireball qatlamlarining nusxasi, ranglari
  binafshaga burilgan. Portlashdagi birliklarda o'lchangan qatlamlar 16/26 ga
  kichraytirildi, shuning uchun portlash zarar maydonidan chiqmaydi. Kamerani yarim
  kuch bilan silkitadi.
- **Ovoz:** otishda `skill_dash`, tekkanda `skill_burst` (mavjud to'plamdan).

#### Testlar

`MonsterSkillTest` (11 ta):
- masofa ichidagi qahramonga otadi;
- juda uzoq yoki juda yaqin bo'lsa otmaydi, oraliqqa kirishi bilan otadi;
- devor ortidan otmaydi;
- kuluarni kutadi;
- teng masofadagi ikki qahramondan birinchi yaratilganiga otadi;
- qahramon juda yaqinlashsa chekinadi;
- uzoqlashsa ortidan borib, oraliq ichida to'xtaydi;
- tor tupikda chekinadigan joy topmaydi;
- INI'dagi kengroq oraliq polda ham kengroq bo'ladi;
- chuqurroqdan chiqqan mage kuchliroq uradi;
- bir xil jang ikki marta o'ynalganda checksum bir xil.

`RoomCapTest`: chegara 1 bo'lsa, hech bir xonada bittadan ko'p mage yo'q; chegarasiz
o'sha xonalar ular bilan to'lib ketadi.

Moslangan testlar:
- `SkillLookTest`, `SkillTipTest` — endi faqat o'yinchi skilllarini tekshiradi;
- `DungeonMonsterArtTest` — otish animatsiyasi uchun `Bow` yoki uchadigan skill
  yetarli;
- `HeroStatusTest` — nomini o'zgartiradigan turlar ma'lumotdan sanaladi;
- `Skill` konstruktorini chaqiradigan uchta test.

#### Ko'z bilan tekshiruv (yashirin oynada)

- Bu bosqichda o'yinning o'zida ko'rilmadi. Yashil va binafsha Skelet-Mage qo'shilgach, uchala tur bitta yashirin-oyna tekshiruvida birga ko'riladi — natija o'sha bo'limda.

#### Ochiq qolganlar

- Monster skillining cast belgisi (`cast=`) klientga bormaydi — faqat qahramonniki
  boradi. Shar va portlash o'z effektlari bilan chiziladi, otish animatsiyasi
  `WeaponFired`dan o'ynaydi.
- Chekinish nuqtasi faqat tosh va ko'rish chizig'ini tekshiradi, qavatni emas: boshqa
  qavatdagi nuqta yo'lni uzaytirishi mumkin.
- Revenant va Necromancer o'zgarmadi — ular hali ham oddiy qurol bilan otadi.

### 8.az Skelet-Mage skill orasida oddiy ataka qiladi, olov shari kuchliroq

Foydalanuvchi o'yinda ko'rdi: Skelet-Mage olov sharini otadi-yu, keyingisigacha hech
narsa qilmay turadi. Sababi shuki, oddiy qurol tayoq edi (masofasi 10), mage esa
qahramonni 35–55 oraliqda ushlaydi — qurol hech qachon yetmasdi.

- **Oddiy ataka:**
  - Revenant'ning kichik olovi (`Bow`, `Projectile = Fireball`, tezlik 95);
  - zarar 6, masofa 58 — oraliqning uzoq chetiga yetadi;
  - qayta o'qlash 54 kadr;
  - `EyesOnly` bor, shuning uchun devor ortidan otmaydi.
- **Bir vaqtda bitta otish** (`MonsterBrain`):
  - oddiy o'q hali chiqayotgan bo'lsa (`midBlow`), skill kutadi;
  - skill otilgach, `SwingFrames` davomida qurol ushlab turiladi (`holdFire`).
- **Modul tartibi:** `Swing` endi `Bow` dan oldin turadi. O'q uni birinchi qabul
  qilgan launcherga beriladi; `Swing` faqat kadrni yozadi va o'qni keyingisiga
  uzatadi. Revenant va Stalker'da `Bow` oldin turadi — ular o'zgarmadi.
- **Olov shari:** zarar 24 → 45, kuluar 4 → 6 soniya. 8.ay dagi raqamlar shu bilan
  eskirdi.

**Testlar:**
- `MonsterSkillTest` ga 2 ta test qo'shildi:
  - skill orasida oddiy olov otadi;
  - skill va oddiy olov hech qachon bir-biridan `SwingFrames` dan yaqin chiqmaydi.
- `MonsterKindsTest.everyThrowerActuallyThrowsSomething` o'zgardi: masofa saqlaydigan
  tur uchun qahramon endi uning oralig'iga qo'yiladi. Avval `CloseDistance` (6)
  masofasiga qo'yilardi, mage esa u yerdan otish o'rniga chekinardi.

### 8.ba Yashil Skelet-Mage — o'zinikilarni davolaydi

Dushman skill mexanizmining ikkinchi turi. Qizil mage qahramonga otadi, yashil mage
esa o'z tomonidagi yaralanganni davolaydi.

#### Mexanizm: yangi `HEAL` effekti

Davolash mexanizmi yo'q edi. Eng yaqin mavjud chok meteor belgisi bo'ldi
(`FallingUpdate`), davolash uning teskarisi qilib yozildi:
- brain o'z tomonidan eng og'ir yaralanganni tanlaydi (`Mending.worstHurt`);
- uning oyog'i ostida `MendingLight` obyekti paydo bo'ladi — meteor belgisi kabi
  dunyodagi narsa;
- `WindUpFrames` (20 kadr) o'tgach nur tushadi va `Heal` qadar jon qaytaradi
  (`MendingUpdate`);
- nurni kutayotgan skelet shu orada o'lsa, hech kim davolanmaydi.

#### Kim davolanadi (`Mending`)

Nomzod quyidagi shartlarning hammasiga to'g'ri kelishi kerak:
- o'z tomonidan, tanasi bor va tirik;
- mage'ning o'zi emas;
- joni o'z maksimumining `HealBelowPercent` (60%) idan kam — to'liq yoki sal
  yaralangan skeletga sarflanmaydi;
- `Range` (60, markazdan markazgacha) ichida;
- ko'rish chizig'i ochiq.

Nomzodlar ichidan eng kam jon ulushi qolgani tanlanadi. Ulush o'z maksimumiga nisbatan
hisoblanadi, ball bilan emas: 45% dagi Warden 50% dagi skeletdan oldin davolanadi. Teng
bo'lsa, kichik `ObjectId` tanlanadi.

Brain ham, `SkillBook` ham bir xil `canMend` qoidasini so'raydi. Rad etilgan cast
kuluarni sarflamaydi.

#### Qaror va vaqt

- `MonsterBrain.mend`: HEAL qahramonga otilmaydi, shuning uchun unga `SkillDistance`
  kerak emas. Validatsiya shunga moslandi.
- Qidiruv har `RepathFrames` kadrda bir marta bo'ladi, id bo'yicha surilgan.
- **Nega darhol emas:** klient o'yindan yo'qolgan narsaning "tushish" qatlamlarini
  faqat u joyida kamida 0.5 s yotgan bo'lsa chizadi (`Landing.lay`). Nur 20 kadr
  yotadi, keyin ustun tushadi — aynan jon qaytgan kadrda. Klient kodi o'zgarmadi.
- **O'yin tomoni:** nur tushguncha o'yinchi o'sha skeletni o'ldirib ulgurishi mumkin.
- **Chuqurlik:** davolash miqdori mage'ning `DepthBonus` iga ko'paytiriladi, uning
  zarbalari kabi.

#### Tur: `SkeletonHealer`

- **Ko'rinish:** `skeleton_mage.glb` va yangi `skeleton_texture_green.png`, `Tint`
  yo'q — yashil rang to'nning o'ziniki.
- **Statistika:** jon 36 (uchala mage ichida eng zaifi), tezlik 13, XP 22.
- **Oddiy ataka:** `HolySpark` — zarar 4, masofa 58, qayta o'qlash 60 kadr.
- **Masofa:** `KeepDistance 35 55`.
- **Chiqishi:** 3-chuqurlikdan, `Weight 8`, `MaxPerRoom 1` — ikkitasi bir-birini
  to'xtovsiz davolardi.

**Muqaddas nur:**
- Heal 30 — skeletning yarmi, Warden'ning o'ndan biri;
- HealBelowPercent 60, Range 60;
- WindUp 20 kadr, kuluar 8 soniya.

**Effekt `MendingLight`:** HolyLight shakli, yashil-oltin rangda, biroz kichikroq.
- Yotganda: `AURA` va `LIGHT`.
- Tushganda: `PILLAR DOWN` (halo va core), `RING`, tushayotgan zarrachalar va chaqnash.

**Ovoz:** `spawned.MendingLight` — `skill_empower.ogg`, past balandlikda.

**CREDITS:** yashil tekstura uchun qator qo'shildi.

#### Testlar — `MonsterHealingTest` (11 ta)

- og'ir yaralangan o'zinikini davolaydi (aniq `Heal` qadar);
- to'liq va chegaradan sal yuqoridagini davolamaydi, kuluari sarflanmaydi;
- ikki yaralangandan og'irrog'ini davolaydi;
- ulush maksimumga nisbatan hisoblanadi: ko'proq ball yo'qotgan Warden emas, kam ulush
  qolgani davolanadi;
- teng ulushda birinchi yaratilgani, qayerda turishidan qat'i nazar;
- o'zini davolamaydi;
- devor ortidagini davolamaydi, devorsiz esa davolaydi;
- jon nur tushgan kadrda qaytadi, nur yotganida emas (`WindUpFrames` ± 1);
- ikki davolash orasida kuluarni kutadi;
- INI'dagi `HealBelowPercent 90` 80% dagi skeletni ham davolatadi;
- bir xil jang ikki marta o'ynalganda checksum bir xil.

Mavjud testlarga tegishli o'zgarishlar:
- `Skill` konstruktorini chaqiradigan uchta testga ikkita yangi maydon qo'shildi;
- `DungeonSettingsTest` va `Main.rangeOf` dagi switch'larga `HEAL` qo'shildi.

### 8.bb Binafsha Skelet-Mage — chaqiruvchi

Dushman skill mexanizmining uchinchi turi: qahramonni ko'rsa, atrofida oddiy skeletlarni
chaqiradi.

#### Mexanizm: yangi `SUMMON` effekti

`GameLogic.spawn` bor edi, lekin kim, qayerga, qancha muddatga va nechta chaqirilishini
hal qiladigan narsa yo'q edi. Davolovchi nuri bilan bir xil chok ishlatildi:
- brain qahramon `SkillDistance` ichida va ko'rinib turganida cast qiladi — qizil
  mage'niki bilan bir yo'l;
- `Summoning.spots` joylarni tanlaydi, har bir joyda `SummoningRift` obyekti ochiladi;
- `WindUpFrames` (20 kadr) o'tgach rift "tushadi" va undan `Summons` (oddiy `Skeleton`)
  chiqadi (`SummoningUpdate`). Klient ustunni aynan shu kadrda chizadi (`Landing.lay`);
- chiqqan skeletga `Summoned` moduli qo'shiladi. `DurationFrames` (600 kadr, 20 s)
  o'tgach uning joni 0 ga tushadi. Uni hech kim o'ldirmagan, shuning uchun XP
  berilmaydi, klient esa yiqilish animatsiyasini ko'rsatadi.

#### Joy tanlash (`Summoning`) — deterministik, `Math.random` yo'q

- Joy chaqiruvchidan `Radius` (16) masofada qidiriladi: avval qahramon tomonda, keyin
  `SummonTurnDegrees` (45°) qadam bilan navbatma-navbat ikki tomonga, har tomonga
  `SummonTurns` (4) martagacha. Burchaklar `StrictMath` bilan hisoblanadi.
- Joy quyidagi shartlarning hammasiga to'g'ri kelishi kerak:
  - `isGroundBlocked` emas — tosh ham, polga qo'shilgan props ham (PropsTest qoidasi);
  - yer balandligi chaqiruvchinikiga teng, ya'ni zinapoya yoki boshqa qavat emas;
  - hech kimning tanasi ustida emas (`findBlocker`);
  - ko'rish chizig'i ochiq.
- Ikki joy orasi chaqiriladigan jonzot tanasining ikki radiusidan kam bo'lmaydi.

#### Chegara va kuluar

- `MaxSummoned` (4) — bitta chaqiruvchining tirik chaqirganlari soni. Ochiq riftlar
  ham sanaladi, shuning uchun rift ochiq turganda qilingan cast ham chegaradan oshmaydi.
- `SummonCount` (2) — bitta castda nechta chiqishi; joy yetmasa kamroq chiqadi.
- Hech narsa ochilmasa (chegara to'la yoki joy yo'q), cast rad etiladi va kuluar
  sarflanmaydi.

#### XP va chuqurlik

- **XP:** `SummonExperiencePercent` (0). Chaqirilgan skeletning `ExperienceModule` i shu
  ulushdagi qiymatga almashtiriladi — `Spawner` dagi usul. Qiymat 0 bo'lgani uchun
  u o'ldirilganda mana ham qaytmaydi.
- **Chuqurlik:** `DepthBonus` endi jon multiplikatorini ham saqlaydi. Rift ochilgan
  paytda chaqiruvchining bonusi o'qiladi va chiqqan skeletga beriladi; tanasi
  `GrowableBody` bo'lsa, joni ham o'stiriladi. Oddiy Skeleton `ActiveBody` ga ega,
  shuning uchun xuddi xonaga qo'yilgan skelet kabi faqat zarari o'sadi.

#### Tur: `SkeletonSummoner`

- **Ko'rinish:** `skeleton_mage.glb` va `skeleton_texture_purple.png`, `Tint` yo'q.
- **Statistika:** jon 40, tezlik 13, XP 26.
- **Oddiy ataka:** `ShadowSpark` — zarar 5, masofa 58, qayta o'qlash 60 kadr.
- **Masofalar:** `SkillDistance 20 70`, `KeepDistance 35 55`.
- **Chiqishi:** 3-chuqurlikdan, `Weight 8`, `MaxPerRoom 1`.

**Chaqiruv skilli:**
- 2 ta skelet, bir vaqtda eng ko'pi 4 ta;
- radius 16, umri 20 s, XP 0%;
- wind-up 20 kadr, kuluar 12 soniya.

**Effekt `SummoningRift`:**
- rift yotganda: binafsha girdob (`AURA`) va `LIGHT`;
- tushganda: `PILLAR UP` (halo va core), `RING`, yuqoriga ko'tariladigan zarrachalar va
  chaqnash.

**Ovoz:** `spawned.SummoningRift` — `descend.ogg`, past balandlikda.

**CREDITS:** binafsha tekstura mavjud qatorga qo'shildi.

#### Unumdorlik

Chaqirilganlar birliklar sonini oshiradi, `PartitionManager` esa har so'rovda barcha
obyektlarni ko'rib chiqadi. Shuning uchun son cheklangan:
- xonada bittadan ortiq chaqiruvchi bo'lmaydi, har biriga 4 tadan ortiq skelet bo'lmaydi;
- chaqirilganlar 20 soniyadan keyin yo'qoladi;
- chegara to'la bo'lsa, cast joy qidirishga o'tmasdan rad etiladi — faqat bir nechta
  `findObject` chaqiriladi.

#### Testlar — `MonsterSummoningTest` (10 ta)

- bir castda `SummonCount` ta, `Radius` masofada chiqadi, birinchisi qahramon tomonda;
- INI'da `SummonCount 3` bo'lsa, 3 ta chiqadi;
- tez cast va uzoq umr bilan ham hech bir kadrda 4 tadan oshmaydi, chegarada kuluar
  sarflanmaydi;
- bir hujayrali yo'lakda faqat oldinda va orqada chiqadi — toshda ham, ko'rinmaydigan
  joyda ham chiqmaydi;
- toshga qamalgan joyda bitta ham joy topilmaydi;
- skelet rift tushgan kadrda chiqadi (`WindUpFrames` ± 1);
- `DurationFrames` tugagach yiqiladi;
- XP ulushi: 0% da 0, 50% da yarmi;
- chuqurlik bonusi bonusdan keyin chaqirilganlarga o'tadi, oldingilarga o'tmaydi;
- bir xil o'yin ikki marta o'ynalganda joylar ham, checksum ham bir xil.

Mavjud testlarga tegishli o'zgarishlar:
- `DungeonEffectLayerTest` "portlash" deb faqat zarar beradigan skillni hisoblaydi.
  Chaqiruvning `Radius` i — skeletlar qanchalik uzoqda chiqishi, rifti esa portlash
  emas. Avval u portlash deb sanalib, ring o'lchovi va zarrachalar masofasi
  tekshiruvidan yiqilgandi.
- `Skill` konstruktorini chaqiradigan uchta testga to'rtta yangi maydon qo'shildi;
  `DungeonSettingsTest` va `Main.rangeOf` dagi switch'larga `SUMMON` qo'shildi.

## 9. Nima yo'q / ochiq ishlar

### Katta teshiklar

1. **Chiqarilgan o'yinda map/faction tanlash yo'q.** `GameExporter.generateMain()` birinchi map'ni
   va muallif tayinlagan faction'larni kodga "pishirib" qo'yadi; `.skirmish(…)` katalogi
   generatsiya qilinmaydi. Natijada `DukeRtsApp.showSkirmishMenu()` chiqarilgan o'yinda hech qachon
   ko'rinmaydi (u `getMapChoices()` bo'sh emasligini talab qiladi). Studio ichidagi Play'da menyu bor.
   Sabab: model va `GameFactory` `game` + `client3d` dan yuqori modulda — ularni shipping qilish kerak.
2. **Qayta ulanish yo'q.** Chiqib ketgan o'yinchi qaytib kira olmaydi (bunga
   uning dunyosini tiklash kerak — save/load bilan bir xil mexanizm). Kechikish
   ham moslashuvchan emas: `FRAME_DELAY` qat'iy 3, haqiqiy pingdan qat'i nazar.
3. **Desync'dan keyin tiklanish yo'q.** O'yin toza to'xtaydi va sabab
   ko'rsatiladi, lekin qayta sinxronlash yoki qayta ulanish yo'q — desync = o'yin
   tugadi. Shuningdek sababni topish uchun ma'lumot kam: kadr raqami bor, lekin
   *nima* farq qilgani yo'q (buning uchun obyekt-daraja xeshlari kerak bo'lardi).
4. **Save / load UI'ga ulanmagan.** `GameSnapshot` faqat `core` da; `game` / `client3d` / `studio`
   da umuman ishlatilmaydi — o'yin ichida saqlash/yuklash yo'q. Bundan tashqari modul ichidagi
   "in-flight" holat (masalan ishlab chiqarish taymerlari) serializatsiya qilinmaydi — yuklashda
   modullar yangidan quriladi.

### Studio'da ochilmagan engine imkoniyatlari

Bular `core` da bor va qo'lda INI yozib ishlatsa bo'ladi, lekin Studio UI'sida yo'q va yuqori
qatlamlarda umuman ishlatilmaydi: `StatusUpdate`, `SpecialPowerModule`, `ContainModule`, `Armor`
(zirh turlari), `Upgrade` / `purchaseUpgrade`, core `Trigger` / `ScriptEngine`, `AsciiRenderer`.

### Studio'ning kichikroq kamchiliklari

3D preview embed yo'q · INI'ni orqaga import qilish yo'q · per-inshoot rally nuqtasi yo'q ·
2D `GamePanel` da build menyusi yo'q.

### Fizika va relyef

5. **Uzluksiz relyef yo'q.** Diskret qavatlar **bor** (3.5c va Duke Dungeon
   qismiga qarang): katakning butun sonli qavati, ramp bilan bog'lanish,
   `Coord3D.z` yerdan o'qiladi. Yo'q qolgani — qiyalik, relyef turi (harakat
   narxi) va uzluksiz balandlik maydoni. Ular ataylab qilinmadi: butun son
   deterministik va arzon, uzluksiz relyef esa ancha qimmat va bu o'yinga
   kerak emas.
6. **`PartitionManager` hali brute-force.** Endi u har kadr, har harakatlanuvchi
   birlik uchun to'qnashuv savolini ham oladi, ya'ni SAGE'ning katak-gridiga
   o'tish avvalgidan muhimroq bo'lib qoldi.
7. **Ikkita `Box` bir-biriga qarshi** o'rab turuvchi doira bilan taqqoslanadi
   (burchaklarda ortiqcha teginish). Birlik ↔ istalgan shakl aniq.
8. **Replay bor, lekin ko'rish uchun UI yo'q.** Yozib olish va qayta o'ynatish
   ishlaydi (`DukeGame.recordReplay()` / `playReplay(...)`), ammo klientda
   "Replay ko'rish" tugmasi, tezlashtirish/orqaga qaytarish yoki faylga saqlash
   oqimi yo'q. Determinizm mashinasi sifatida esa allaqachon ishlatilmoqda.

### Engine generalizatsiyasi — bajarilgani va qolgani

Duke Dungeon'ning leveling ishi `rts` ning amalda "Generals qatlami" bo'lib
qolganini ochib berdi. Uch bosqich bajarildi:

1. **Choklar ochildi** — `removeModule`/`replaceModule`, `DamageModifier`
   (birlik bo'yicha zarar), `WorldSnapshot.status` (o'yinning HUD qatori).
   Yo'l-yo'lakay tuzoq topildi: modulni o'z `update()` idan olib tashlash
   **istisno tashlamas ekan** — `ArrayList` ning oxirgi elementi o'chirilsa
   iterator jimgina tugaydi va bo'shliqqa tushgan modul umuman ishlamaydi.
   Endi sikl buni aniq sezadi.
2. **Veteranlik → sozlanadigan narvon** — 4 rank va enum ichidagi 1.1/1.2/1.3
   `generals` ga ko'chdi; `rts` da thresholds + har rungning bonusi (INI'dan)
   qoldi. `HealOnPromotion` ham so'raladi, taxmin qilinmaydi.
3. **Ishlab chiqarish sharti → `ProductionGate`** — quvvat tekshiruvi
   `ProductionUpdate` ichidan chiqdi. Muhim topilma: u **hamma o'yinda** ishlab
   turgan ekan (Rohan/Mordor quvvat ishlatmagani uchun balansi 0 bo'lib,
   ko'rinmagan) — ya'ni "faqat Generals'da" degani noto'g'ri edi.

4. **`Upgrade` nomlangan bonuslarga** — `Upgrade` Generals qoidasi emas ekan
   (WC3 smithy, AoE blacksmith — hamma RTS'da bor), shuning uchun `rts` da qoldi.
   Lekin u **to'liq bo'lmagan mexanizm** edi: bitta `weaponDamageMultiplier`, ya'ni
   sotib olishga arziydigan yagona narsa zarar deb qaror qilingan. Endi `Upgrade`
   nomlangan effektlar to'plamini olib yuradi va `RtsPlayer` nomlangan bonuslarni
   saqlaydi — zirh, tezlik, masofa o'yin tomonidan nomlanadi, engine ularning
   ma'nosini bilmaydi. `setBonus` qo'shildi: qiymat ma'lum bo'lganda uni
   **to'g'ridan-to'g'ri** qo'yish mumkin (ilgari faqat ko'paytirish bor edi, shuning
   uchun aniq qiymat kerak bo'lgan kod eskisini teskari songa bo'lib chiqarardi —
   Duke Dungeon leveling aynan shunday qilardi, endi qilmaydi).

**Determinizm tekshiruvi (natija: bug yo'q edi).** `RtsPlayer.upgrades` `HashSet`
edi va `GameSnapshot` ga yoziladi — desync xavfi deb shubha qilindi. Tekshirildi:
`GameSnapshot` allaqachon `.sorted()` qilar ekan va `checksum()` o'yinchi holatiga
umuman tegmaydi (faqat obyektlar), ya'ni **haqiqiy bug bo'lmagan**. Baribir
kafolat har bir kelajakdagi chaqiruvchining eslashiga bog'liq edi, shuning uchun
to'plamlar manbada tartiblandi (`TreeSet`/`TreeMap`) — kafolat endi tipniki,
chaqiruvchiniki emas.

**Ataylab qilinmagan: `BuildCost`/`BuildTime` ni `core` dan chiqarish.**
`ThingTemplate` ularni hali saqlaydi va bu sof RTS tushunchasi. Lekin **hozir
hech kimga og'riq keltirmayapti**: Duke Dungeon `core` ga tayanadi va bu maydonlar
unga xalaqit qilmadi. Chiqarish uchun template kengaytma-ma'lumoti + loader
maydon-registratsiyasi kerak, u `ProductionUpdate`, `DukeGame.getBuildOptions`,
Studio, `.duke` formati va deyarli hamma `rts` testiga tegadi — ustiga "ro'yxatdan
o'tmagan maydon jimgina yo'qoladi" xavfi bor. **Qaror: kimdir `core` ustida
RTS bo'lmagan o'yin yozib, bu maydon haqiqatan xalaqit qilganda qilinadi** —
o'shanda chok qanday bo'lishi kerakligi taxmin emas, dalil bilan aniq bo'ladi.
`VisionRange` esa **janrsiz** — tuman `core` ning o'z tizimi, RPG'da ham,
roguelike'da ham kerak; u `core` da qoladi.

`CLAUDE.md` ham yangilandi: loyiha endi "SAGE'ning qayta implementatsiyasi" emas,
**SAGE'dan ilhomlangan** engine. `core` — janrdan qat'i nazar o'yin tuzsa bo'ladigan
asosiy engine; `rts` — SAGE'dan ilhomlangan, RTS o'yinlar uchun **kutubxona**;
`generals` — bir o'yinning qoidalari. U yerda `rts` ga qo'shishdan oldin so'raladigan
savol ("BFME'da ham, WC3'da ham, Generals'da ham kerakmi?") va choklar jadvali bor,
hamda porting falsafasiga yangi band: **SAGE'ning arxitekturasini ol, Generals'ning
dizaynini emas** — qoidani sodiq ko'chirish sodiq engine bermaydi, u har bir o'yinni
o'sha o'yinga aylantiradi.

### Duke Dungeon — ataylab qilinmagan narsalar

O'yin hozir "o'ynash mumkinmi?" savolini tekshiryapti, shuning uchun uni yashira
oladigan hamma narsa olib tashlangan. Qilinmagani — kelasi bosqichlar, kamchilik emas:

- **Inventar yo'q** — loot to'g'ridan-to'g'ri doimiy bonus beradi, almashtiriladigan
  narsa emas. Ataylab: qaror "borib olamanmi", va oxirida ikkinchi qaror
  rasmiyatchilik bo'lardi.
- **Sandiqning modeli yo'q** — Kenney to'plamida sandiq yo'q, shuning uchun u
  mash'al rangli quti. Model topilsa — `Main.looks()` da bitta qator.
- **Runner'ning zamahi ham sig'maydi, lekin tegilmadi.** Maxluqlarning hammasi
  bitta bir qo'llik chopni (`Melee_1H_Attack_Chop`, 1.067 s) baham ko'radi, Runner
  esa uni har 0.733 s da tashlaydi — **69%** da uzilib qayta boshlanadi, ya'ni
  ritsarnikini yomon ko'rsatgan aynan o'sha ulush. Farqi: ritsarniki **ikki
  qo'llik** chop edi (1.633 s), zarbasi klipda ancha kech tushadi. Runner shu
  holda ancha vaqtdan beri turibdi va hech kim e'tibor bermagan, demak "necha
  foiz ko'rsatildi" degan qoida yolg'on — to'g'ri savol "zarba lahzasi
  ko'rsatildimi", bu esa faylda yo'q. Shuning uchun
  `aSwordsmanFinishesHisSwingBeforeTheNextOneStarts` faqat qahramonlardan
  so'raydi, va Runner'ning `ReloadFrames` i **ataylab tegilmagan**: testni
  qanoatlantirish uchun balansni o'zgartirish — teskari tartib.
- **Qavatlar** — xonalar har xil balandlikda turadi, koridorlar esa **tekis**:
  hammasi kirish qavatida yotadi va ko'tarilish **xona og'zida** bo'ladi
  (ko'tarilgan xonadan chiqayotib bir zina tushasan). Bu ko'rinish uchun emas —
  koridorlar bir-birini doim kesib o'tadi, va har xil qavatdagi ikki koridorning
  kesishmasi ikkalasini ham kesib qo'yadigan, xaritada ko'rinmaydigan devor
  bo'lardi. Ulanish kafolati endi **tekshiriladi**: generator o'z dungeonini
  engine qoidasi (`canStep`) bilan yurib chiqadi, yetib bo'lmaydigan xona
  topilsa uning ko'tarilishi olib tashlanadi va xarita qayta chiziladi;
  urinishlar soni chegaralangan, oxirida ham ulanmasa **istisno** tashlanadi.
  Sozlamalari `dungeon.ini` da: `MaxStorey`, `StoreyChangePercent`,
  `StoreyHeight`, `StairLength`, `EntranceStorey`, `BossStorey`.
  `MaxStorey = 0` — eski tekis dungeon, aynan avvalgidek.
- **Xonalarda narsalar bor** (`props.ini` + `DungeonProp` bloklari) — ustun,
  haykal, bochka. Ular oddiy template: **`Geometry` bor** (engine ularni
  navigatsiya gridiga bosadi, ya'ni yo'l aylanib o'tadi va jism to'xtaydi) va
  **`Body` yo'q** (ikkala miya ham faqat tanasi bori nishon deb qaraydi, ya'ni
  hech kim ularga o'q otmaydi — sandiq shu tariqa ishlaydi). Qanday chizilishi
  temaniki: `DungeonThemeMonster` istalgan template'ni qayta kiyintiradi.
  Joylashuvi: xona ichida, devordan ikki katak ichkarida (eshik og'zi band
  bo'lmasin), zinapoyada emas, va **hech kimning ustida emas** — ustiga tushgan
  skelet to'siq ichida qoladi va boshqa hech qachon yurolmaydi (bloklangan
  katakdan boshlangan qidiruv yo'l topmaydi). `PropsTest` shu ikkisini qulflaydi.
- **Balandlik ko'rishni to'sadi** — ko'zdan **yuqori** qavatdagi katak ko'rinmaydi
  va nurni devordek to'sadi; pastdagi qavat esa chetidan qaralganda ko'rinadi.
  Ya'ni tepadagi xona unga chiqmaguningizcha qorong'u qoladi, chiqqandan keyin
  esa pastga qarab ko'rasiz — bir tomonlama qoida ataylab, chunki ikkalasini ham
  yashirish ko'tarilishning mukofotini yo'q qiladi. Minimapda har qavat o'z
  ochiqligida (kontur xaritasidek): plan ko'rinish balandlikni boshqacha
  ko'rsatolmaydi. Tuman uch qatlami o'zgarmadi.
- **Yo'nalishga bog'liq ko'rish yo'q** — orqadan kelgan narsa ham xuddi shunday
  ko'rinadi; tuman faqat devorni biladi.
- Qo'lda chizilgan xona (`Dungeon.create()`) hali turibdi — engine o'ynasa bo'ladiganini
  ko'rsatadigan ma'lum javobli dunyo. Haqiqiy o'yin `create(long seed)`.

### 8.s Ovoz — INI'da bog'langan lahzalar

- **Klient lahzalarni nom bilan ko'taradi, faylni bilmaydi.** `client3d` to'rtta
  o'yin chizadi, shuning uchun unda birorta ham fayl nomi yo'q: u
  `died.Boss`, `struck.Arrow`, `walking.Hero` deb so'raydi, javob esa
  `dungeon.ini` da. **Yangi ovoz = INI'da bitta blok**, Java yozilmaydi.
- **Nuqtali nom + fallback:** `died.Boss` topilmasa `died` chalinadi. Shuning
  uchun klientda birorta maxluq nomi yo'q, o'yin esa xohlagancha aniq bo'ladi.
- **Fayllar:**
  | Fayl | Nima |
  |---|---|
  | `SoundBank` | O'yin aytgani: nom → fayllar, kanal, gain, kuluar. jME yo'q, **sof** |
  | `Sounds` | Qoidalar: qaysi fayl, kuluar o'tdimi, to'rt tugmadan keyin qancha baland |
  | `SoundSink` | Ovoz qayerga ketadi — jME yoki jimlik. **Headless'ning kaliti** |
  | `AudioSink` | jME ustidagi amalga oshirish: fayl boshiga bitta tugun, `playInstance` |
  | `GameSounds` | Kadrni lahzaga aylantiradi: hodisalar + oldingi kadr bilan solishtirish |
- **Vaqt parametr sifatida keladi**, soatdan o'qilmaydi — shuning uchun kuluarni
  kutmasdan test qilish mumkin. "Faqat kutib ko'riladigan qoida" — tekshirilmaydigan
  qoida.
- **Engine hech narsa qo'shmadi.** Ikki hodisa (`WeaponFired`, `ObjectDied`) bor;
  qolgani **solishtirishdan** chiqadi: ro'yxatda yo'q bo'lgan o'q — tegdi, kamaygan
  jon — zarba, oldin bo'lmagan birlik — paydo bo'ldi. Status qatori (`HeroStatus`)
  daraja, chuqurlik, loot va skill kuluarini beradi.
- **`struck.` va `gone.`** — o'q nishonni ta'qib qiladi, ya'ni deyarli har doim
  tegadi; lekin nishoni yo'lda o'lsa xona o'rtasida to'xtaydi. Shuning uchun
  "ketishdan oldin **boshqa o'yinchining** birligiga tegib turganmi" tekshiriladi,
  va **oldingi kadr bo'yicha** — o'ldiruvchi zarbada qurbon o'sha nafasda yo'qoladi,
  va eshitilishi kerak bo'lgan aynan o'sha o'q.
- **Voiceover:** kanal bo'yicha kuluar (INI'da 1.6 s) — ikki ovoz bir-birini
  bosmaydi, hatto boshqa gap bo'lsa ham. Faqat **o'z** buyrug'ida chalinadi.
- **Musiqa:** uchta trek, settings'dan almashtiriladi (`Label` INI'da). Oqim
  bilan chalinadi, keshlanmaydi. Preload'ga kirmaydi — u diskdan oqadi.
- **To'rt tugma:** Master / Effects / Voice / Music. UI effektlar bilan yuradi.
  `PREFS` da saqlanadi. Voice `OFF` gacha tushadi.
- **Fayl topilmasa** bir marta logga yoziladi va boshqa so'ralmaydi — o'yin jim
  davom etadi.

### Infratuzilma

- **CI/CD: `.github/workflows/ci.yml`** — bitta fayl, ikkita ish.
  - **Darvoza:** `master`ga har push va har PR → uch platformada (`ubuntu`,
    `windows`, `macos`) `./gradlew build`. Testlar ekran talab qilmaydi, shuning
    uchun headless runner'da to'liq ishlaydi. Yiqilsa test hisobotlari artifact
    bo'lib saqlanadi.
  - **Release:** `v*` tegi push qilinganda (yoki Actions'dan qo'lda, versiya
    kiritib). Har platformada bitta **installer**, o'sha platformaning o'z
    paketlash vositasi bilan, **o'z Java 25 runtime'i ichida**:
    Windows `.msi` (~93 MB), macOS `.dmg`, Linux `.deb`. Keyin `gh release`
    uchtasini bitta release'ga qo'yadi.
  - **Installer nimani talab qiladi:** `msi` uchun **WiX 3** (`candle`/`light`)
    — runner image'ida bo'lsa o'shani oladi, bo'lmasa WiX'ning o'z release'idan
    pinlangan zip'ni PATH'ga qo'yadi; `deb` uchun **fakeroot**. jpackage bularni
    topolmasa faqat shunda aytadi, shuning uchun oldindan qo'yiladi.
  - `--win-per-user-install` — o'yin o'ynash uchun administrator bo'lish shart
    emas. `--linux-package-name duke-dungeon` — dpkg katta harf va probelni
    qabul qilmaydi.
  - **Nega uch runner:** `jpackage` faqat o'zi turgan mashina uchun quradi,
    cross-compile qilmaydi. MSI'ni faqat Windows'da, `.deb`'ni faqat Linux'da.
  - **`--add-modules java.se,jdk.unsupported`** — atayin yozilgan: LWJGL
    `sun.misc.Unsafe` ishlatadi, u `jdk.unsupported`da. Usiz runtime quriladi-yu,
    o'yin birinchi kadrda yiqiladi.
  - Uchinchi tomon action'i yo'q — faqat GitHub'niki va Gradle'niki (`gh` CLI
    runner'da bor).
  - **O'sha installer'ni lokalda ham:** `./gradlew :dungeon:packageInstaller`
    (versiya: `-PinstallerVersion=1.2.0`, standarti `1.0.0`). CI bilan **bir xil
    yo'l va bir xil flaglar** — ikki joyda ikki xil yozilsa ular darrov ajralib
    ketadi. O'zi turgan OS uchun quradi: Windows'da `.msi`, macOS'da `.dmg`,
    Linux'da `.deb`. Natija `dungeon/build/installer/` da.
    - `jpackage` **toolchain'dan** olinadi, PATH'dan emas (10-bo'limdagi tuzoq).
    - Windows'da **WiX** kerak (v3 `candle`/`light` yoki v4/v5 `wix.exe`),
      PATH'da. Bo'lmasa jpackage o'zi aniq aytadi: *"Can not find WiX tools…
      Download WiX 3.0 or later from wixtoolset.org and add it to the PATH."*
    - Loyihaning `0.1.0-SNAPSHOT` versiyasi bu yerga yaramaydi: jpackage bir-uch
      butun sondan boshqasini olmaydi, macOS esa boshidagi nolni ham rad etadi.
- `:sandbox3d:startScripts` `jme3-testdata` jar'ini talab qiladi; tarmoq sekin bo'lsa
  `./gradlew build` aynan shu yerda yiqiladi (kod muammosi emas).

---

## 10. Tuzoqlar (gotchas)

- **macOS `-XstartOnFirstThread` tuzog'i:** GLFW macOS'da asosiy oqimni talab
  qiladi, shuning uchun release to'plamiga bu flag qo'shiladi. Yon ta'siri bor:
  shu flag bilan macOS'da **Swing ishlamaydi**. Hozir zarar yo'q
  (`DukeRtsApp.askText` faqat LAN'ga ulanishda ishlatiladi, dungeon esa LAN
  entry'sini so'ramaydi) — lekin dungeon LAN qo'shsa, macOS'da o'sha dialog
  qotib qoladi.
- **Reset tuzog'i:** `GameEngine.init()` subsystemlarni init qilgandan keyin `resetAll()` chaqiradi —
  `GameLogic.init()` ichida yaratilgan obyektlar o'chib ketadi. Template'lar, modul-builderlar va
  o'yinchilar reset'dan omon qoladi. Obyektlarni reset'dan **keyin** yarating.
- **jpackage tuzog'i:** oddiy `commandLine("jpackage", …)` PATH'dagi JDK 21 jpackage'ini oladi →
  Java 21 runtime + Java 25 klasslari = `UnsupportedClassVersionError` (class 69 vs 65), oynali exe
  jimgina exit 1 bilan o'ladi. Yechim (allaqachon qo'llangan): jpackage Gradle toolchain'idan olinadi.
  Nosozlikni ko'rish uchun `--win-console` varianti yordam beradi.
- **`TurnRate` tuzog'i (tuzatilgan):** `MoveUpdate` da `TurnRate = 0` **maxsus qiymat** —
  "joyida bir zumda burilish"; musbat qiymat esa SAGE'ning texnika xulqi: nishonga tomon
  asta buriladi va o'z yo'nalishi bo'ylab **yoy chizadi**. Dungeon creature'lariga
  `TurnRate = 720` yozilgan edi, natijada odam qahramon teskari yuborilganda mashinadek
  aylana chizardi. Bu engine kamchiligi emas — noto'g'ri kontent qiymati; tuzatish INI'da.
- **Jangda yo'nalish:** engine yo'nalishni **faqat yurayotganda** o'rnatadi (locomotor uni
  keyingi waypointga qaratadi). To'xtab urayotgan narsa oxirgi yurgan tomoniga qarab
  qolaveradi — ya'ni yelkasi orqali uradi. Yo'nalish simulyatsiya hisobiga kirmaydi
  (qurol unga qaramaydi), lekin klient faqat snapshotda borini chizadi, shuning uchun
  uni **o'yin o'zi** o'rnatadi (`ai/Facing`), deterministik qadam ichida, `StrictMath` bilan.
- **Sahna tuzog'i (tuzatilgan):** klientda bir marta quriladigan narsa dunyo bir marta
  quriladi deb **jimgina** taxmin qiladi. Relyef `simpleInitApp` da `rootNode` ga
  ulanardi, shuning uchun yangi run boshlanganda birliklar yangi dungeon'da yurar,
  devorlar esa eskisidan qolardi — minimap nuqtalari to'g'ri ko'rinib, xato faqat 3D'da
  ko'rinardi. Qoida: qayta qurilishi mumkin bo'lgan narsa **o'z tugunida** yashasin va
  qayta qurish har doim `detachAllChildren()` dan boshlansin (aks holda xato bir run
  uchun to'g'ri ko'rinadi, keyin xotira yeydi).
- **O'z masofangda to'xtash tuzog'i (tuzatilgan):** yaqinlashayotgan narsa **o'zining
  eng uzoq masofasida** to'xtasa, u shu masofaning chekkasida parkovka qiladi — va agar
  uning masofasi raqibinikidan uzun bo'lsa, raqibning masofasidan **tashqarida** turadi.
  Skeletlar 10 da to'xtardi, qahramonning avto-tanlashi esa `findClosestInReach(…, 8)`
  edi: qahramon ko'rmaydigan narsa uni urib turardi, ya'ni avto-ataka jimgina o'ldi.
  Yechim — bitta `CloseDistance` (har qanday quroldan qisqa): jang qurollar masofasidan
  qat'i nazar ikki tomonlama bo'ladi. Yon foyda: `AttackRange` endi faqat
  `creatures.ini` da, ikki faylda takrorlanmaydi.
- **MP qo'l berishuvi:** `DUKE-JOIN` / `DUKE-WELCOME` `SocketTransport.wrap()` dan **oldin** bo'lishi
  shart, aks holda transport o'quvchisi qo'l berishuv qatorini buyruq deb talqin qiladi.
- **MP test yozish tuzog'i:** ikkita simni o'zaro qadamlatganda ular bir kadrga fazoviy siljiydi —
  checksum'ni har **yarim** qadamdan keyin solishtiring, aks holda solishtiriladigan kadr qolmaydi.
- **Minimap y o'qi:** ekran y dunyo y ga teskari.
- **Quvvat tuzog'i:** o'yinchi quvvatsiz bo'lsa ishlab chiqarish to'xtaydi (Generals mexanikasi) —
  iste'molchi spawn qiladigan testlarga PowerPlant kerak.
- **Template nomlari** vergul, `|`, `;`, `:` belgilarini o'z ichiga olmasin — `CommandCodec` sim
  formati buziladi.
- **Skript paketi:** generatsiya qilingan `Main` da lokal o'zgaruvchi `dukeGame` deb ataladi, chunki
  `game` nomi `game.scripts` paketi bilan to'qnashadi.
- **Sealed modul chegarasidan o'tmaydi:** shuning uchun buyruq to'plami `core` da tura olmaydi.
  Yangi janr qo'shsangiz — o'z sealed `Command` ierarxiyangizni o'z modulingizda e'lon qiling.
- **`java.lang.Module` to'qnashuvi:** `uz.duke.core.module.Module` ni boshqa paketdan
  ishlatganda importni unutmang, aks holda kompilyator jimgina `java.lang.Module` ni oladi va
  xato "cannot inherit from final Module" bo'lib chiqadi.
- **`Kind` identity bo'yicha solishtiriladi:** `Kind.of(...)` interned, shuning uchun
  `equals`/`hashCode` `Object` dan olinadi — ataylab. `new Kind(...)` yo'q.
- **`Math` vs `StrictMath`:** mantiq yo'lida trigonometriya uchun `Math` ishlatmang
  (yuqoridagi determinizm bandiga qarang). Klient/HUD kodida `Math` mumkin.
- **Pauza qo'yilgan engine hech qachon qadam tashlamaydi — demak navbatdagi
  vazifa bilan uni ocholmaysiz.** `GameEngine.update()` `logic.isGamePaused()`
  bo'lsa `logic.update()` ni **umuman** chaqirmaydi, `runOnSimThread` esa
  `simulate()` ichida drenaj qilinadi. Ya'ni `DukeGame.togglePause()` bilan bir
  marta to'xtatib bo'lgach, ikkinchi bosish hech qachon yetib bormaydi — `P`
  tugmasi shu sababdan **hamma o'yinda** buzuq edi. Klient endi bayroqni
  to'g'ridan-to'g'ri qo'yadi (`getLogic().setGamePaused(...)`): u checksum'ga
  kirmaydi va **qachon** kadr bo'lishini o'zgartiradi, kadr ichida **nima**
  bo'lishini emas. Daraja tanlash ekrani ham shu yo'ldan pauza qiladi.
- **Vertex ranglari sRGB konversiyasidan o'tmaydi.** Klient sRGB frame buffer'ga
  chizadi; material rangi shader'ga ketayotib o'giriladi, mesh'ning rang buferiga
  yozilgani esa **yo'q**. Natijada bitta hex gradient sifatida quti sifatidagidan
  ikki pog'ona ochroq chiqadi — qon-qizil bar pushti bo'lib turardi. `HeroPanel`
  o'sha konversiyani qo'lda qiladi (`linear(...)`), va uni **hamma** rangiga
  qo'llaydi, aks holda ikkisi kelishmaydi.
- **`clearWorld()` `DieModule` larni ishga tushirmaydi** — u shunchaki ro'yxatni
  tozalaydi. Bu yaxshi: aks holda qavat almashganda butun dungeon sandiq yog'dirardi.
  Lekin o'lim orqali bo'ladigan hamma narsa (loot, XP) faqat **haqiqiy** o'limda
  bo'ladi degani.
- **Qisman `dungeon.ini` ro'yxatlarni saqlaydi, skalyarlarni saqlamaydi.**
  `DungeonSettings.parse(...)` da nomlanmagan `DungeonMonster` / `DungeonSkill` /
  `DungeonLootItem` bloklari shipping fayldan qo'shiladi, lekin
  `MapWidth` yoki `DepthWord` kabi **alohida maydonlar** Java'dagi standart
  qiymatiga tushadi. Test uchun shuni kutish kerak: qisman fayl bilan qurilgan
  o'yinda panel so'zlari inglizcha chiqadi.
- **Engine'ning GUI'si oyna o'lchami o'zgarishidan omon chiqmaydi.** Fullscreen'ga
  monitorning o'z rejimida o'tsangiz dunyo to'g'ri chiziladi va **butun HUD
  yo'qoladi** — panel sahnada, to'g'ri joyda, culling'siz turadi, `hud`
  `BitmapText` esa ko'rinadi. Har bo'lagini qaytadan qurish ham yordam bermaydi.
  O'sha o'yin **fullscreen boshlansa** hammasi joyida, chunki hech narsa o'lchamini
  o'zgartirmagan. Shuning uchun F11 oynaning **o'lchamini saqlab** monitorga
  ko'chadi, Resolution esa keyingi ishga tushirishga qoldiriladi.
- **`restart()` `destroy()` chaqiradi** — ya'ni `Duke3D` ning `awaitStop()` i
  qaytadi va o'yin tugaydi. Ekran sozlamasi uchun uni ishlatmang; GLFW'ga
  to'g'ridan-to'g'ri ayting (`LwjglWindow.getWindowHandle()` ochiq).
- **Oyna monitorlar orasida ko'chayotganda engine `reshape(0, 0)` beradi** — bu
  kameralarni nolga keltiradi va 3D kamerasining frustumi boshqa hech narsani
  ko'rmaydigan bo'lib qoladi (HUD ortografik bo'lgani uchun omon qoladi, shuning
  uchun kasallik "dunyo yo'qoldi" bo'lib ko'rinadi). Nol o'lcham — o'lcham emas.
- **Geometriya qo'shsangiz joylashuvni tekshiring:** template'ga `Geometry`
  bergan zahoti u yer egallaydi. Bir-biriga juda yaqin qo'yilgan eski
  spawn koordinatalari endi kesishishi mumkin — birliklar chiqib ketguncha
  bir-birining ichidan o'tadi.

---

- **Additiv yorug'lik och polda yo'qoladi:** to'q sariqni kulrang toshga qo'shish
  oqish dog' beradi, to'q sariq emas. Olov qatlamiga `Cover` bering — u polning bir
  qismini yashiradi va rangini saqlaydi.
- **`MatParamOverride` jimgina o'tkazib yuboriladi:** material'da o'sha nomli
  parametr yo'q bo'lsa yoki turi boshqa bo'lsa — xato yo'q, log yo'q, effekt yo'q.
  `HitFlashTest` Lighting.j3md dagi `Ambient` ni nomi va turi bilan tekshiradi.
- **Katta billboard'ni pol kesadi:** yerdan o'lchamining yarmidan past turgan
  kamera tomon qaragan karta polni kesib o'tadi. `Particles.vert` kartani ko'zga
  tomon tortadi — tekis qirra ko'rinsa, avval shu yerga qarang.
- **Tanasiz narsa `ObjectDied` bermaydi:** `GameLogic.reapDestroyed` hodisani faqat
  `isEffectivelyDead()` — tanasi bor va o'lgan narsa — uchun chiqaradi. O'q, o't
  shari va meteor belgisi `markDestroyed()` bilan ketadi, ularning tushishini shu
  hodisadan kutish hech qachon kelmaydi. Klient ularni dunyodan yo'qolgan lahzada
  ushlaydi (`Landing`): o'yinchining o'zinikini doim, boshqaniki faqat oxirgi
  ko'rilgan joyi ko'z oldida bo'lsa. ★ Lekin yo'qolish tegish emas: devorga
  urilgan o't shari ham yo'qoladi, zarari esa yo'q — tegishning dalili o'sha
  kadrdagi zarba.
- **Tekstura tumani:** Kenney'ning ba'zi flare va muzzle teksturalarida butun
  karta bo'ylab 2–3% alfa bor. Kichik chizilganda ko'rinmaydi, katta chaqnashda esa
  to'g'ri qirrali kvadrat. Shader eng xira alfani tashlaydi — yangi tekstura
  qo'shganda chetlarini tekshiring.
- **Ustunning harakatlanuvchi uchi to'g'ri chiziq bo'lib qoladi,** agar tekstura
  butun balandlikka yotqizilgan bo'lsa: uch teksturaning yorug' joyini kesib
  o'tadi. `PILLAR` teksturasining ikkala uchi yumshoq bo'lishi shart — ustun
  cho'zilganda istalgan uchi harakatlanuvchi uch bo'lishi mumkin.
- **Dunyoni qayta qurish yonayotgan hamma narsani o'chiradi:** `buildTerrain` →
  `layered.clear()`. Qavat ko'rinishi dunyodan bir kadr kech kelsa, o'sha oraliqda
  boshlangan effekt yo'qoladi — shuning uchun `RunMoments` har qayta qurishda
  unutadi.
- **Offscreen + `Shell.none()` da kamera qahramonni topmaydi:** xarita markaziga
  qaraydi, kadr butunlay tuman rangida chiqadi.

## 11. Fayl xaritasi (asosiylari)

| Yo'l | Nima |
|---|---|
| `core/…/core/GameEngine.java` | asosiy sikl, 30 Hz akkumulyator |
| `core/…/core/GameLogic.java` | deterministik dunyo, kadr tartibi, checksum, tuman |
| `core/…/core/thing/{ThingTemplate,GameObject,ThingFactory,World,Kind}.java` | obyekt modeli + tasniflash |
| `core/…/core/thing/{Geometry,Footprint}.java` | fizik shakl va to'qnashuv matematikasi |
| `core/…/core/module/{ActiveBody,Armor,DamageType,MoveUpdate}.java` | janrsiz modullar |
| `core/…/core/message/{Command,MessageStream}.java` | buyruq navbati (buyruqlarning o'zi emas) |
| `core/…/core/ini/Ini.java` | SAGE tokenizatori |
| `core/…/core/pathfind/{PathGrid,Pathfinder,MapLoader}.java` | deterministik A* |
| `core/…/core/network/{LockstepGate,LockstepScheduler}.java` | lock-step darvozasi + a'zolik |
| `core/…/core/network/{HostTransport,SocketTransport,NetMessage,NetFraming}.java` | relay topologiyasi + sim protokoli |
| `rts/…/rts/RtsSimulation.java` | RTS mantiqining bazasi |
| `rts/…/rts/message/GameMessage.java` | sealed RTS buyruq to'plami |
| `rts/…/rts/network/CommandCodec.java` | RTS sim formati |
| `rts/…/rts/module/DamageModifier.java` | birlik bo'yicha zarar choki |
| `rts/…/rts/module/ProjectileLauncher.java` | o'q choki — zarar qachon tushishini o'yin hal qiladi |
| `rts/…/rts/module/WeaponHold.java` | band birlik otmaydi — modul qurolni vaqtincha jim qiladi |
| `dungeon/…/dungeon/combat/{Bow,ArrowUpdate,Shot}.java` | kamon, uchayotgan o'q, va o'qni havoga qo'yish |
| `rts/…/rts/module/{ProductionGate,CapacityGate}.java` | ishlab chiqarish sharti choki + sig'im qoidasi |
| `rts/…/rts/module/ExperienceModule.java` | XP + sozlanadigan rank narvoni |
| `generals/…/generals/GeneralsVeterancy.java` | Generals'ning 4 rankli narvoni — bir o'yinning jadvali |
| `rts/…/rts/player/{RtsPlayer,Upgrade}.java` | pul, nomlangan bonuslar, ko'p effektli upgrade'lar |
| `rts/…/rts/thing/RtsKinds.java` | RTS lug'ati |
| `rts/…/rts/save/GameSnapshot.java` | RTS save formati |
| `game/…/game/DukeGame.java` | asosiy API (762 qator) |
| `game/…/game/RtsLogic.java` | buyruq routingi, mag'lubiyat qoidasi |
| `game/…/game/MultiplayerSession.java` | 2 o'yinchili TCP lock-step |
| `game/…/game/script/UnitScript.java` | custom kod API'si |
| `client3d/…/client3d/DukeRtsApp.java` | 3D klient (1024 qator) |
| `client3d/…/client3d/Visuals.java` | asset bog'lashlari |
| `studio/…/studio/model/StudioProject.java` | `.duke` hujjat modeli |
| `studio/…/studio/model/GameFactory.java` | loyiha → INI / Visuals / DukeGame |
| `studio/…/studio/export/GameExporter.java` | mustaqil o'yin loyihasi generatori |
| `studio/…/studio/ui/StudioWindow.java` | IDE asosiy oynasi |
| `studio/…/studio/examples/RohanVsMordor.java` | namunaviy o'yinning muallifligi |
| `client3d/…/client3d/TerrainScene.java` | relyef sahnasi — qayta qurish almashtiradi, qo'shmaydi |
| `client3d/…/client3d/MinimapProjection.java` | dunyo ↔ minimap matematikasi + viewport konturi |
| `client3d/…/client3d/CameraFocus.java` | kamera nishoni/zoom — boshda o'z birligiga, keyin erkin |
| `client3d/…/client3d/{SelectionBox,Formation,OrderMarkers}.java` | drag-select, guruh joylashuvi, buyruq metkalari |
| `client3d/…/client3d/{SkillRange,RangeLook,RangeRings}.java` | skill qayergacha yetadi — beshta shakl, bitta til; ushlab turiladigan skillar shu yerda hal bo'ladi |
| `client3d/…/client3d/Glow.java` | polda yotadigan yorqin belgilar uchun umumiy material (additive, depth-write yo'q) |
| `client3d/…/client3d/{OrderMark,Chevrons}.java` | yurish klikiga javob: uchta o'q uchi, harakati (ease-out, kech so'nish, burilish) va chizilishi (pool, additive) |
| `client3d/…/client3d/BannerPanel.java` | "o'ldingiz"/"yutdingiz"/keyingi qavat — yalang'och harf emas, ramkali plita; uch lahza uch xil ko'rinadi |
| `client3d/…/client3d/AttackFlash.java` | hujum klikiga javob: jon atrofida qizil halqa, ikki marta qattiq yonib-o'chadi — bo'shliqni ko'z ilg'aydi |
| `client3d/…/client3d/GroundRing.java` | polda yotgan aylana (uzluksiz chiziq + xira to'ldirish); skill halqasi ham, hujum halqasi ham shu — bitta chizma |
| `client3d/…/client3d/Destination.java` | bora olmaydigan joyga bosilgan klikni eng yaqin bora oladigan katakka tortadi (toshqin; tosh + polga mahkamlangan mebel — bochka, ustun; tirik jon EMAS) |
| `client3d/…/client3d/NineSlice.java` | bitta kichik ramka rasmi istalgan o'lchamda — burchaklar cho'zilmaydi; jME'da bunday narsa yo'q |
| `client3d/…/client3d/PanelSkin.java` | panel qirralari nima bilan bo'yalgani — o'yin aytadi, klient chizadi; har qism ixtiyoriy |
| `dungeon/src/main/resources/ui/borders/` | Kenney Fantasy UI Borders (CC0) — ikki oila, olti to'plam; nomerlash Kenney'niki (CREDITS.md da izohlangan) |
| `client3d/…/client3d/{EffectLayer,LayeredEffects}.java` | effekt qatlami (10 tur, 7 joy) va uni cast / uchish / tushish lahzasida o'ynatuvchi — pool, byudjet, tuman-masofa-ekran |
| `client3d/…/client3d/ParticleLayer.java`, `MatDefs/duke/Particles.*` | GPU zarracha qatlami: tug'ilishda bir marta yoziladi, hayotini shader hisoblaydi |
| `client3d/…/client3d/RunMoments.java` | daraja, boss va yangi qavat lahzalari — status qatorini oldingisi bilan solishtiradi; ko'rinishini `DungeonMoment` beradi |
| `dungeon/art/effects/pillar_textures.py` | nur ustunining ikki gradient teksturasi (yadro va halo) |
| `client3d/…/client3d/{LightPool,HitFlash,Landing}.java` | effektlarning umumiy yorug'lik pool'i; tegish chaqnashi; snaryad tushdimi yoki ko'rinmay qoldimi |
| `client3d/…/client3d/ProjectileEffects.java` | uchayotgan narsa qanday yonadi: iz, yoritilgan tana, tegishdagi portlash — pool, yorug'lik byudjeti, kodda yasalgan uchqun teksturasi |
| `client3d/…/client3d/Discovery.java` | kashfiyot tumani — uzluksiz yorug'lik, fazoviy+vaqt silliqlash (faqat klient) |
| `dungeon/…/dungeon/combat/Swing.java` | zarba qachon tushganini aytadi — hech narsa uchirmaydi |
| `client3d/…/client3d/TileLayout.java` | qaysi plitka qayerda — sof arifmetika, jME'siz |
| `client3d/…/client3d/{Tileset,TileSource}.java` | to'plam ta'rifi + bo'lak yuklovchisi choki |
| `client3d/…/client3d/AnimationLibrary.java` | klipni boshqa skeletga nom bo'yicha qayta ulash |
| `dungeon/…/dungeon/content/MonsterLook.java` | model/teri/bo'y/rang — turdan alohida, simulyatsiya o'qimaydi |
| `dungeon/src/main/resources/models/tiles/<tema>/` | pol, devor, zinapoya — tema bo'yicha (KayKit `.gltf` + `.bin` + atlas PNG). Bitta papkada ikkita atlas bo'lishi mumkin: o'rmonda pol zindonnikidan, daraxt o'rmonnikidan |
| `dungeon/src/main/resources/models/props/<tema>/` | ustun, haykal, bochka — tema bo'yicha |
| `dungeon/src/main/resources/models/monsters/` | Quaternius Bestiary + `textures/` |
| `dungeon/src/main/resources/models/heroes/` | KayKit Ranger, uning kamoni va o'qi (CC0) |
| `dungeon/src/main/resources/animations/{hero,monsters}/` | modeldan tashqaridagi klip kutubxonalari — qahramonniki uchta, turi bo'yicha |
| `dungeon/src/main/resources/_unused/` | hech kim murojaat qilmaydigan 3 fayl — README bilan |
| `dungeon/…/dungeon/content/{ThemeArt,Themes}.java` | to'plam ta'rifi + chuqurlik→to'plam tanlovi (sof) |
| `dungeon/…/dungeon/content/HeroLook.java` | qahramon ko'rinishi — animatsiyasi fayl bo'yicha, nom bo'yicha emas |
| `dungeon/…/dungeon/Dungeon.java` | o'yinni yig'ish (fixture xona va haqiqiy o'yin) |
| `dungeon/…/dungeon/content/DungeonSettings.java` | `dungeon.ini` — generatsiya va xulq sozlamalari |
| `dungeon/…/dungeon/gen/DungeonGenerator.java` | seed'dan xonalar + koridorlar (ulanish kafolati) |
| `dungeon/…/dungeon/ai/{HeroBrain,MonsterBrain}.java` | klik-ataka va maxluq AI'si (xonani chaqirish shu yerda) |
| `dungeon/…/dungeon/ai/Chasing.java` | quvishni qachon qayta rejalash kerak — tiqilgan narsa to'xtab qolishining yagona sababi |
| `dungeon/…/dungeon/ai/WayAhead.java` | yonida o'tadigan joy bormi — yo'q bo'lsa turadi, ochilsa yuradi; pirpirashning oxiri |
| `dungeon/…/dungeon/ai/{HoldGround,Orders}.java` | "to'xta" / "qo'riqla" — o'yinning to'rtinchi buyrug'i + uni buyruqdan miyagacha olib boradigan yagona ko'prik |
| `dungeon/…/dungeon/ai/Doing.java` | jon hozir nima qilyapti — to'rt so'z, to'rtta tugmaning chirog'i; modullardan o'qiladi, miyadan emas |
| `dungeon/…/dungeon/run/Watching.java` | "o'shani tanladim" — hech narsa qilishni so'ramaydigan yagona buyruq; panel kimni yozishini hal qiladi |
| `client3d/…/client3d/Cursors.java` | kursor nima ustida turganini aytadi; jME'ning pastdan-yuqoriga rasmi, pastdan hot-spoti va rangga bo'yash shu yerda |
| `dungeon/src/main/resources/ui/cursors/` | Kenney Cursor Pack 1.1 (CC0) — Outline oilasi, 32px va 64px |
| `dungeon/…/dungeon/run/DungeonRun.java` | run loop: o'lim → yangi qavat; qaysi qavat ekanini `Floors` aytadi |
| `dungeon/…/dungeon/run/{Floors,GeneratedFloors,StageFloors}.java` | qavat qayerdan keladi — seed zanjiri yoki muzlatilgan fayl; holatli, ataylab |
| `dungeon/…/dungeon/stage/{Stage,StageFile}.java` | qotirilgan dungeon + metama'lumot, va uning matn formati (engine INI'si, koordinatalar katakda). `difficulty` = o'ynaladigan chuqurlik |
| `dungeon/…/dungeon/gen/Layout.java` | qavat qanchalik katta — `of(settings)` tushishniki (bit-baravar), `sized(...)` muallif so'ragani; urinishlar soni so'ralgandan keltirib chiqariladi |
| `dungeon/src/main/resources/stages/deep.stage` | ikkinchi shipping bosqich: 100×76, 28 xona, chuqurlik 8 — tushishdan chuqurroq |
| `worldbuilder/…/worldbuilder/ui/NewStage.java` | chizishdan oldingi savol: eni, bo'yi, xona soni, qiyinchilik — izohlari o'yinning o'z raqamlaridan o'qiladi |
| `dungeon/…/dungeon/stage/StageCheck.java` | qo'lda tahrirlangan fayl bilan nima noto'g'ri — yurish `PathGrid.canStep` bilan, javob ro'yxat |
| `dungeon/…/dungeon/stage/Stages.java` | qaysi stage o'ynaladi (`--stage=` > INI) va fayl qayerdan topiladi (disk > classpath) |
| `dungeon/src/main/resources/stages/first.stage` | shipping stage — `:worldbuilder:writeExampleStage` qayta yozadi |
| `worldbuilder/…/worldbuilder/StageDraft.java` | tahrirlash qoidalari, Swing'siz — muharrirning testlanadigan yarmi |
| `worldbuilder/…/worldbuilder/ui/{BuilderWindow,StageCanvas,Palette}.java` | oyna, tepadan ko'rinish, nima qo'yiladi |
| `dungeon/…/dungeon/skill/{Skill,SkillEffect}.java` | skill ma'lumoti + daraja arifmetikasi (sof) |
| `dungeon/…/dungeon/skill/SkillBook.java` | qahramon moduli: kuluar, effektlar, `DamageModifier` |
| `dungeon/…/dungeon/skill/{CastSkill,Skills}.java` | o'yinning o'z buyrug'i (nishoni bilan) + status qatori |
| `dungeon/…/dungeon/run/HeroStatus.java` | panel o'qiydigan qator — so'zlar shu yerda tugaydi |
| `client3d/…/client3d/{Preload,LoadingOverlay}.java` | o'yin so'raydigan hamma faylning ro'yxati + yuklash ekrani |
| `client3d/…/client3d/{SoundBank,Sounds,SoundSink,AudioSink,GameSounds}.java` | ovoz: o'yin aytgani, qoidalar, chiqish joyi, jME, kadr→lahza |
| `dungeon/src/main/resources/audio/` | to'rt Kenney to'plami (CC0) + uchta trek; nomlari ma'noli |
| `client3d/…/client3d/HeroPanel.java` | qahramon paneli — tosh uyalar, barlar, chuqurlik; ikonka yo'li INI'dan keladi |
| `dungeon/src/main/resources/icons/skills/` | skill ikonkalari — 128×128 shaffof PNG (Lorc, CC BY 3.0) |
| `CREDITS.md` | san'at mualliflari va litsenziyalari — CC BY talab qiladigan atribut |
| `client3d/…/client3d/Hotkeys.java` | o'yin da'vo qilgan klavishlar → `postCommand`; klavish to'qnashuvini hal qiladi |
| `dungeon/…/dungeon/level/Levelling.java` | daraja qoidalari — sof, INI qiymatlaridan |
| `dungeon/…/dungeon/level/HeroBody.java` | o'sadigan tana (engine'niki final) + `Armor` |
| `dungeon/…/dungeon/level/HeroProgress.java` | XP → daraja → atributlar, run'da nolga qaytish |
| `dungeon/…/dungeon/loot/{Loot,LootKind,LootTable}.java` | tushadigan narsa: ma'lumot, turlar, deterministik qur'a |
| `dungeon/…/dungeon/loot/{LootBag,LootDrop,LootUpdate}.java` | topilganlar + `DieModule` cho'ntagi + poldagi sandiq |
| `dungeon/…/dungeon/ai/KeepingDistance.java` | masofa saqlaydigan monster qayerga chekinadi — qat'iy tartibdagi burchaklar, tosh va ko'rish chizig'i |
| `dungeon/…/dungeon/combat/DepthBonus.java` | chuqurlik bo'yicha zarar bonusi — qurol ham, skill ham o'qiydi |
| `dungeon/…/dungeon/skill/Mending.java` | davolovchi kimni davolaydi: o'z tomonidan eng kam ulush qolgan, yaqin va ko'rinib turgani; teng bo'lsa kichik id |
| `dungeon/…/dungeon/skill/MendingUpdate.java` | yerda yotib, tushganda davolaydigan nur — meteor belgisining teskarisi |
| `dungeon/…/dungeon/skill/Summoning.java` | chaqirilganlar qayerda chiqadi: qahramon tomondan boshlab qat'iy tartibdagi burchaklar, faqat ochiq, bir qavatdagi, ko'rinadigan joy |
| `dungeon/…/dungeon/skill/SummoningUpdate.java` | yerdagi rift: tushganda jonzot chiqaradi, uning muddatini, XP ulushini va chuqurligini beradi |
| `dungeon/…/dungeon/skill/Summoned.java` | chaqirilgan jonzotning muddati — tugaganda hech kim o'ldirmagandek yiqiladi |
| `dungeon/…/dungeon/ai/SightLine.java` | ko'ra oladimi: yetarlicha yaqinmi, orada tosh bormi, balandda turibdimi — sof arifmetika, grid ustida |
| `dungeon/…/dungeon/combat/EyesOnly.java` | ko'rmaganiga otmaydi — qahramon, arbaletchi va mage |
| `client3d/…/client3d/Fog.java` | tuman sozlamasi (LOS, uch qatlam yorqinligi, yumshoqlik, tekstura o'lchami, rang) |
| `client3d/…/client3d/Sunlight.java` | quyosh burchagi/kuchi + ambient; ★ pitch = relyef borligi, 90° da hamma tik yuza bir xil qorong'i |
| `client3d/…/client3d/FogMap.java` | tumanning o'zi — xaritaning qorong'ilik surati (alfa-tekstura) |
| `client3d/src/main/resources/MatDefs/duke/` | relyef materiali: tumanni dunyo x/z bo'yicha o'qiydigan shader + to'rtta ko'chma point light |
| `client3d/…/client3d/EdgeScroll.java` | kursor bilan kamerani surish sozlamasi |
| `dungeon/src/main/resources/ini/*.ini` | o'yin ma'lumoti — kompilyatsiyasiz sozlanadi |
