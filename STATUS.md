# Duke Engine — hozirgi holat va ishlash tamoyili

**Holat sanasi:** 2026-09-07 · **Testlar:** 225 ta, hammasi yashil (0 failure / 0 error)

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
| `rts` | `api core` | RTS: buyruq to'plami, jang, ishlab chiqarish, iqtisod, veteranlik, quvvat, lug'at, save formati |
| `game` | `api rts` | Unity-uslub `DukeGame` fasadi, 2D Swing klient, multiplayer sessiyasi |
| `client3d` | `api game` + jMonkeyEngine 3.7.0-stable | To'liq 3D klient: model/animatsiya/ovoz, menyular, minimap, HUD |
| `studio` | `client3d` + Gson 2.11.0 | Duke Studio — Swing IDE (`uz.duke.studio.StudioMain`) |
| `sandbox` | `game` | 2D skirmish demo (~70 qator) |
| `sandbox3d` | `client3d` + jme3-testdata | 3D skirmish demo (~74 qator) |
| `dungeon` | `client3d` | **Duke Dungeon** — engine ustidagi ilk o'yin (3D roguelike, primitiv shakllar) |

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
   +----------------------------------------------+
   | rts — RTS: buyruqlar, jang, iqtisod, lug'at  |
   +----------+-----------------------------------+
              v
   +----------------------------------------------+
   | core — janrsiz engine (rendering YO'Q,       |
   |        buyruq YO'Q, gameplay YO'Q)           |
   +----------------------------------------------+
```

Qoida: pastki qatlam yuqoridagini bilmaydi. `core` da rendering yo'q va hech qanday
o'yin mazmuni yo'q; `rts` da jME yo'q; `client3d` faqat snapshot o'qiydi,
simulyatsiyaga tegmaydi.

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
  `PowerModule` + `PowerGrid` (quvvat yetmasa ishlab chiqarish to'xtaydi),
  `ExperienceModule` + `VeterancyLevel` (ko'tarilishda to'liq davolanadi),
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
- **Boshqaruv:** LMB tanlash (Shift — qo'shish), RMB buyruq (dushmanga = hujum, yerga = yurish,
  zavod tanlangan bo'lsa = rally nuqtasi), WASD / o'q tugmalar kamera, g'ildirak zoom, `H` to'xtatish,
  `P` pauza, `Esc` tanlovni bekor / pauza menyusi, `1`–`9` build menyusidan navbatga qo'yish.
- **Minimap** — o'ng pastda: statik fon (map chegarasi + to'siq kataklari) + jonli nuqtalar
  (o'yinchi rangi bo'yicha, inshootlar kattaroq). Minimapga LMB = kamera sakrashi (birlik tanlashdan
  oldin tekshiriladi).
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
| EXPERIENCE | `Behavior = ExperienceModule` | ExperienceValue, ExperienceRequired |
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

- **225 test yashil** (core 118, rts 71, game 18, client3d 5, studio 8, dungeon 5) — 0 failure / 0 error.
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
- **Engine ustida o'yin yozilmoqda** — `dungeon` moduli (Duke Dungeon): bitta
  xona, qahramon, uchta skelet. Muhimi: **engine'ga bironta narsa qo'shilmadi**
  — o'yin faqat mavjud API'ni ishlatadi. `DungeonTest` headless tekshiradi:
  qahramon buyurilgan joyga boradi, skeletni o'ldiradi, skeletlar javob qaytaradi
  va devorlar qattiq (400 kadr davomida hech qachon tosh ustida turmaydi).
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

### `core` da qolgan RTS izlari

`ThingTemplate` hali ham `BuildCost` / `BuildTime` / `VisionRange` maydonlarini saqlaydi —
birinchi ikkitasi sof RTS/strategiya tushunchasi. Ularni chiqarish uchun template'ga
kengaytma-ma'lumot mexanizmi va `ThingTemplateLoader` ga maydon-registratsiyasi kerak
(o'yin o'z INI maydonlarini qo'sha olsin). **Hali qilinmagan — ochiq qaror.**

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
| `rts/…/rts/module/*.java` | 10 ta RTS moduli + `RtsModules`, `PowerGrid` |
| `rts/…/rts/player/{RtsPlayer,Upgrade}.java` | pul, upgrade'lar |
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
