# Duke Engine — hozirgi holat va ishlash tamoyili

**Holat sanasi:** 2026-09-10 · **Testlar:** 605 ta, hammasi yashil (0 failure / 0 error)

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

## 5. `client3d` — 3D klient (5 fayl, ~1470 qator)

jMonkeyEngine 3.7.0-stable ustida. `Duke3D.launch(game, visuals[, shell])` oynani ochadi va
yopilguncha bloklaydi.

**Ekran holat mashinasi:** `MENU → PLAYING ⇄ PAUSED`, plus `SETTINGS`. Simulyatsiya "Play"
bosilganda (yoki `Shell.none()` bo'lsa — darhol) boshlanadi.

- **Bosh menyu — o'yinniki, klientniki emas.** Ilgari klient uni o'zi qurardi va
  ikkita o'yinchi slot bo'lsa "Host LAN Game" taklif qilardi; bir kishilik
  dungeon'da esa ikkinchi slot skeletlar egasi bo'lib chiqdi. Bu engine
  strukturaviy fakt asosida mahsulot qarorini chiqarishi edi. Endi **mexanizm
  klientniki** (`MenuOverlay` — xiralashgan quad + `BitmapText` tugmalar, GUI
  kutubxonasiz), **mazmun o'yinniki** (`Shell`): qaysi bandlar, qanday nomlanadi,
  yoki umuman menyusiz. Klient faqat bandning ma'nosi borligini tekshiradi.
  `Shell.standard()` — avvalgi xulq, ya'ni mavjud demolar o'zgarmaydi.
  **Tizim masalalari klientda qoladi:** Settings, pauza menyusi, chiqish.
- **Skirmish menyusi** — map'ni aylantirish + har o'yinchi uchun faction tanlash → `selectSkirmish()`.
  Faqat `getMapChoices()` bo'sh bo'lmasa va MP bo'lmasa ko'rinadi.
- **Settings** — Fullscreen, Resolution (1280×720 / 1600×900 / 1920×1080), Volume (100…0 %).
  `Preferences` da saqlanadi (`duke-engine/game` tuguni: `resIndex`, `fullscreen`, `volume`).
  O'zgarish `restart()` bilan qo'llanadi; `reshape()` menyularni qayta quradi va HUD'ni joylashtiradi.
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
  zavod tanlangan bo'lsa = rally nuqtasi), WASD / o'q tugmalar kamera, g'ildirak zoom, `H` to'xtatish,
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

- **605 test yashil** (core 133, rts 110, generals 5, game 28, client3d 110, studio 8, dungeon 211) — 0 failure / 0 error.
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
    Test manba klipda siljish **borligini** ham tekshiradi — aks holda u hech
    nimani isbotlamasdi.
  - **Jon chizig'i model ichida qolardi** — balandligi qat'iy 4.5 edi, kapsulaga
    mos, qahramon esa ~12, boss ~22. Endi tananing haqiqiy chegarasidan
    o'lchanadi va `depthTest` o'chirilgan holda **doim ustidan** chiziladi: jon
    chizig'i dunyodagi narsa emas, ko'rsatkich.
  - **Boshqa skeletda, va bu muammo emas.** Qahramon `mixamorig:` riginda,
    monstrlar UE mannequin'da — bitta ham umumiy suyak nomi yo'q. Har jonzot
    **o'z rigiga** qurilgan kutubxonadan animatsiya oladi, ya'ni ikki to'plam
    yonma-yon yashaydi. Test buni ataylab qulflaydi: monstrlar kutubxonasi
    qahramonga **0 ta** klip beradi, va bu kutilgan natija.
  - **Animatsiya har harakat uchun alohida fayl.** Saytlar ishni shunday beradi,
    va har faylning ichidagi klip **bir xil** nom bilan keladi (`mixamo.com`) —
    ya'ni qaysi biri yugurish ekanini **fayl** aytadi, nom emas. Shuning uchun
    `DungeonHero` blokida `IdleFrom`/`WalkFrom`/`AttackFrom` — yo'l, nom emas
    (`DungeonAnimations` da esa aksincha: bitta kutubxona, nomlar ajratadi).
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
    `TerrainScene` har katakka "qopqoq" beradi, minimap esa har katakni holat
    bo'yicha bo'yaydi.
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
  - Tumanni **so'ramagan o'yin hech narsa to'lamaydi**: qopqoq qurilmaydi, sikl
    ishlamaydi. `studio` va oddiy RTS xulqi o'zgarmagan.
- **Qahramonda 4 ta skill (Q W E R)** — `STRIKE` (eng yaqin dushmanga zarba),
  `AREA_DAMAGE` (atrofdagilarga), `DASH` (yuzi tomon otilish), `EMPOWER`
  (ultimate: vaqtincha zarar oshishi, 5-darajadan ochiladi).
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
- **Skilllar buyruq quvuridan o'tadi** — `CastSkill` bu **o'yinning o'z
  buyrug'i**. Klavish bosilishi klientda faqat `postCommand` qiladi; nishon
  tanlash, kuluar, daraja tekshiruvi — hammasi simulyatsiyada, kadr chegarasida.
  Buning uchun engine chokining o'zi ochildi (quyida).
- **Skeletlar qahramonni quvadi** — `SkeletonBrain` ikki radius bilan: sezish
  (~bitta xona, aggro xonama-xona tarqaladi) va quvish (kengroq, lekin cheklangan).
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
  - `TileSource.shade` endi `boolean` emas, `float` oladi; `KitTiles` 12 pog'onali
    material narvonini bir marta quradi va har bir bo'lak o'z pog'onasiga ishora
    qiladi.
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

- **Daraja oshganda kuch tanlanadi** — har darajada uchta karta, bittasi olinadi va
  run oxirigacha qoladi. Ikki run bir xil oltinchi darajaga yetsa ham ikki xil
  qahramon bo'ladi, chunki tanlov o'yinchiniki.
  - **Kod effekt turini beradi, INI qolganini.** `PowerEffect` beshta shakl:
    `SKILL_DAMAGE`, `COOLDOWN`, `MOVE_SPEED`, `LIFESTEAL`, `EXTRA_CHARGE`. Yangi
    kuch = `dungeon.ini` da bitta `DungeonPower` bloki, **noldan Java**; yangi
    *tur* — enum'da bitta konstanta va o'qiladigan joyda bitta shox.
  - **Uchtasi seed'dan chiqadi** — `PowerDraft` sof: seed + daraja kirsa, ro'yxat
    chiqadi. `Math.random` yo'q, ya'ni bitta seed butun run'ni belgilaydi,
    kartalar ham. Chegarasiga yetgan (`MaxStacks`) kuch taklif qilinmaydi;
    `MinLevel` esa kartani run boshida ushlab turadi.
  - **Stack qo'shiladi, ko'paymaydi.** Ikkita "+25%" = +50%, 1.25² emas: ikkinchi
    kartani o'qigan o'yinchi uni birinchisicha qadrli deb kutadi, va ko'paytirish
    olingan tartibga qarab sirg'anadi.
  - **Buyruq quvuridan o'tadi** — `ChoosePower` bu o'yinning **ikkinchi** buyrug'i
    (`CastSkill` yonida). Klik faqat `postCommand` qiladi; qaysi karta ekani va u
    nima berishi kadr chegarasida, simulyatsiyada hal bo'ladi.
  - **Taklif *raqami* daraja emas, ketma-ket nomer.** O'lim darajani birga
    qaytaradi, ya'ni har run'ning "2-daraja" taklifi bo'ladi — darajani nom qilib
    olsak, klient birinchisini javoblaganini eslab ikkinchisini **umuman
    ko'rsatmasdi**. Test shuni qulflaydi.
  - **Ekran o'yinni to'xtatib turadi** — pauzani **klient** qo'yadi va oladi,
    to'g'ridan-to'g'ri (pastdagi tuzoqqa qarang). Tanlangach buyruq jo'natiladi va
    dunyo yana yuradi; javob berilgan taklif qaytib chiqmaydi.
  - **Zaryadlar tuzog'i (tuzatilgan):** "E ikki marta" kartasi cheksiz bo'lib
    qolgan edi — ikkinchi zaryad sarflangach uya kuluarsiz qolar, keyingi kadr esa
    uni to'ldirar edi. Endi **shift** (`chargeCap`) eslab qolinadi: to'ldirish
    faqat kuluar tugaganda, va karta olinganda faqat **farq** beriladi.
- **Warcraft uslubidagi pastki panel** — `HeroPanel` bitta tosh plitaga aylandi:
  chapda minimap uyasi, o'rtada portret va tirik ko'rsatkichlar, o'ngda skill
  uyalari va ular ostida olingan kuchlar qatori, chekkada chuqurlik. Bo'limlar
  o'yma chiziq bilan ajratiladi — alohida quticha emas.
  - **Maket piksellarida chiziladi, keyin oynaga masshtablanadi** (`DESIGN_WIDTH`
    1180). Shuning uchun nisbatlar har ekranda saqlanadi va `reshape` bitta
    `layOut()` chaqiradi.
  - **Minimap panelga ko'chdi.** Panel uyaning to'rtburchagini oyna piksellarida
    beradi (`minimapRect`), klient minimapni o'shanga moslaydi — hech biri
    ikkinchisining arifmetikasini bilmaydi. Panel yo'q o'yinda minimap avvalgidek
    burchakda qoladi.
  - **Portret 3D render emas, siluet.** Ikkinchi kamera bilan jonli qahramonni
    teksturaga chizish — haqiqiy narsa, lekin ramka aslida "ekranning qaysi
    burchagi meniki" deyish uchun; siluet buni qiladi.
  - Panelga bitta ham inglizcha so'z qo'shilmadi: `Kuchlar`, `Zarba`, `Zirh`,
    `Tezlik` va tanlov ekranining sarlavhasi `DungeonHud` blokidan keladi.
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
  - **Tuman rangi bor.** Plitka materiallari narvonining quyi uchi qora emas,
    `Tint` — ochilgan xona "yoritilmagan tosh" emas, "qorong'idan ko'ringan tosh"
    bo'ladi. O'sha rang oynaning fon rangi ham (ikkalasi bitta qorong'ilik).
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
  Oyna o'lchami o'zgarganda panel, undagi minimap, tanlov ekrani va menyular
  `reshape` da qayta joylashadi.

---

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

5. **Dunyo tekis.** `Coord3D.z` faqat xeshlash va serializatsiyada o'qiladi —
   balandlik, qiyalik, relyef turi (harakat narxi), tepalik ortidan ko'rinmaslik
   yo'q. `PathGrid` faqat "o'tsa bo'ladi / bo'lmaydi" biladi.
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

- **Ovoz yo'q** — musiqa ham, effekt ham.
- **Inventar yo'q** — loot to'g'ridan-to'g'ri doimiy bonus beradi, almashtiriladigan
  narsa emas. Ataylab: qaror "borib olamanmi", va oxirida ikkinchi qaror
  rasmiyatchilik bo'lardi.
- **Sandiqning modeli yo'q** — Kenney to'plamida sandiq yo'q, shuning uchun u
  mash'al rangli quti. Model topilsa — `Main.looks()` da bitta qator.
- **Relyef yo'q, dunyo tekis** — xonalar (X, Z) tekisligida; `Coord3D.z` ishlatilmaydi.
- **Yo'nalishga bog'liq ko'rish yo'q** — orqadan kelgan narsa ham xuddi shunday
  ko'rinadi; tuman faqat devorni biladi.
- Qo'lda chizilgan xona (`Dungeon.create()`) hali turibdi — engine o'ynasa bo'ladiganini
  ko'rsatadigan ma'lum javobli dunyo. Haqiqiy o'yin `create(long seed)`.

### Infratuzilma

- `:sandbox3d:startScripts` `jme3-testdata` jar'ini talab qiladi; tarmoq sekin bo'lsa
  `./gradlew build` aynan shu yerda yiqiladi (kod muammosi emas).

---

## 10. Tuzoqlar (gotchas)

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
  `DungeonPower` / `DungeonLootItem` bloklari shipping fayldan qo'shiladi, lekin
  `MapWidth` yoki `DepthWord` kabi **alohida maydonlar** Java'dagi standart
  qiymatiga tushadi. Test uchun shuni kutish kerak: qisman fayl bilan qurilgan
  o'yinda panel so'zlari inglizcha chiqadi.
- **Geometriya qo'shsangiz joylashuvni tekshiring:** template'ga `Geometry`
  bergan zahoti u yer egallaydi. Bir-biriga juda yaqin qo'yilgan eski
  spawn koordinatalari endi kesishishi mumkin — birliklar chiqib ketguncha
  bir-birining ichidan o'tadi.

---

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
| `client3d/…/client3d/Discovery.java` | kashfiyot tumani — uzluksiz yorug'lik, fazoviy+vaqt silliqlash (faqat klient) |
| `dungeon/…/dungeon/combat/Swing.java` | zarba qachon tushganini aytadi — hech narsa uchirmaydi |
| `client3d/…/client3d/TileLayout.java` | qaysi plitka qayerda — sof arifmetika, jME'siz |
| `client3d/…/client3d/{Tileset,TileSource}.java` | to'plam ta'rifi + bo'lak yuklovchisi choki |
| `client3d/…/client3d/AnimationLibrary.java` | klipni boshqa skeletga nom bo'yicha qayta ulash |
| `dungeon/…/dungeon/content/MonsterLook.java` | model/teri/bo'y/rang — turdan alohida, simulyatsiya o'qimaydi |
| `dungeon/src/main/resources/Models/dungeon/` | Kenney to'plami (CC0) + `colormap.png` atlasi |
| `dungeon/src/main/resources/Models/monsters/` | Quaternius Bestiary + Universal Animation Library |
| `dungeon/src/main/resources/Models/hero/` | Mixamo qahramoni + har harakat uchun bitta fayl |
| `dungeon/…/dungeon/content/HeroLook.java` | qahramon ko'rinishi — animatsiyasi fayl bo'yicha, nom bo'yicha emas |
| `dungeon/…/dungeon/Dungeon.java` | o'yinni yig'ish (fixture xona va haqiqiy o'yin) |
| `dungeon/…/dungeon/content/DungeonSettings.java` | `dungeon.ini` — generatsiya va xulq sozlamalari |
| `dungeon/…/dungeon/gen/DungeonGenerator.java` | seed'dan xonalar + koridorlar (ulanish kafolati) |
| `dungeon/…/dungeon/ai/{HeroBrain,SkeletonBrain}.java` | klik-ataka va skelet AI'si |
| `dungeon/…/dungeon/run/DungeonRun.java` | run loop: o'lim → yangi seed → yangi dungeon |
| `dungeon/…/dungeon/skill/{Skill,SkillEffect}.java` | skill ma'lumoti + daraja arifmetikasi (sof) |
| `dungeon/…/dungeon/skill/SkillBook.java` | qahramon moduli: kuluar, effektlar, `DamageModifier` |
| `dungeon/…/dungeon/skill/{CastSkill,Skills}.java` | o'yinning o'z buyrug'i (nishoni bilan) + status qatori |
| `dungeon/…/dungeon/run/HeroStatus.java` | panel o'qiydigan qator — so'zlar shu yerda tugaydi |
| `client3d/…/client3d/HeroPanel.java` | qahramon paneli — tosh uyalar, barlar, chuqurlik |
| `client3d/…/client3d/Hotkeys.java` | o'yin da'vo qilgan klavishlar → `postCommand`; klavish to'qnashuvini hal qiladi |
| `dungeon/…/dungeon/level/Levelling.java` | daraja qoidalari — sof, INI qiymatlaridan |
| `dungeon/…/dungeon/level/HeroBody.java` | o'sadigan tana (engine'niki final) + `Armor` |
| `dungeon/…/dungeon/level/HeroProgress.java` | XP → daraja → atributlar, run'da nolga qaytish |
| `dungeon/…/dungeon/power/{Power,PowerEffect}.java` | daraja kuchi: ma'lumot + effekt turlari (kod faqat shu yerda) |
| `dungeon/…/dungeon/power/PowerDraft.java` | uchta kartani seed'dan tanlash — sof, dunyosiz |
| `dungeon/…/dungeon/power/{PowerBook,PowerChoice}.java` | olingan kuchlar va ular nimaga teng + taklif holati |
| `dungeon/…/dungeon/power/ChoosePower.java` | o'yinning ikkinchi buyrug'i — "o'shani olaman" |
| `dungeon/…/dungeon/loot/{Loot,LootKind,LootTable}.java` | tushadigan narsa: ma'lumot, turlar, deterministik qur'a |
| `dungeon/…/dungeon/loot/{LootBag,LootDrop,LootUpdate}.java` | topilganlar + `DieModule` cho'ntagi + poldagi sandiq |
| `client3d/…/client3d/LevelUpOverlay.java` | daraja tanlash ekrani — mexanizm klientniki, so'zlar o'yinniki |
| `client3d/…/client3d/Fog.java` | tuman sozlamasi (LOS, xotira yorqinligi, yumshoqlik, rang) |
| `client3d/…/client3d/EdgeScroll.java` | kursor bilan kamerani surish sozlamasi |
| `dungeon/src/main/resources/uz/duke/dungeon/*.ini` | o'yin ma'lumoti — kompilyatsiyasiz sozlanadi |
