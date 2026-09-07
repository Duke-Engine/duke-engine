# `core` — janrsiz engine yadrosi

**Rol: ENGINE.** Loyihaning asl qiymati shu yerda. Deterministik simulyatsiya
yadrosi — hech qanday o'yin mazmuni, hech qanday janr bilimi yo'q: bu yerda na
qurol bor, na pul, na `MoveTo` buyrug'i.

| | |
|---|---|
| Bog'liqligi | **yo'q** — sof Java, tashqi kutubxonasiz |
| Kim bunga bog'lanadi | `rts` (→ `game` → `client3d` → `studio`) |
| Hajmi | 58 fayl, ~4 020 qator |
| Testlar | 118 ta |

**Asosiy invariant:** `core` hech qachon `rts` ni import qilmaydi. Bu Gradle
darajasida ta'minlangan (`core/build.gradle.kts` da bironta bog'liqlik yo'q),
ya'ni qoidani buzish = kompilyatsiya xatosi.

RTS bo'lmagan o'yin yozmoqchi bo'lsangiz faqat shu modulga bog'lanasiz va
beshta chok orqali o'zingiznikini qo'yasiz:

| Chok | `core` beradi | O'yin beradi |
|---|---|---|
| Buyruqlar | `Command` markeri + `MessageStream` | o'z **sealed** buyruq ierarxiyasi |
| Sim formati | `SocketTransport` dagi `PacketCodec` plagi | o'z kodeki |
| Xulq modullari | `ModuleFactory.withDefaults()` = tana + harakat | o'z modul to'plami |
| O'yinchilar | `Player` (identity + diplomatiya) + `PlayerList(PlayerFactory)` | o'z `Player` subtipi |
| Tasniflash | `Kind` — nom bo'yicha interned | o'z lug'ati |
| Hodisalar | `WorldEvent` + post/drain kanali | o'z hodisalari |

---

## Nima bor (paket bo'yicha)

### `uz.duke.core` — sikl va hayot sikli

| Fayl | Qator | Vazifa | Ishlatiladimi |
|---|---|---|---|
| `GameLogic` | 355 | **yadroning yadrosi** — deterministik dunyo: obyektlar, kadr tartibi, buyruq drenaji, o'lganlarni yig'ish, tuman (`canSee`), `checksum()` | ha (16 main / 23 test) |
| `GameEngine` | 168 | asosiy sikl: 30 Hz mantiq / ≤`maxFps` render, akkumulyator + catch-up cheklovi | ha (`RtsGameEngine`) |
| `SubsystemList` | 66 | subsystemlarni egallaydi; ro'yxat tartibi = hayot sikli tartibi | ha |
| `SubsystemInterface` | 57 | har bir subsystem shartnomasi (init / postProcessLoad / reset / shutdown) | ha (12 joy) |
| `GameClient` | 38 | prezentatsiya qatlamining bazasi (mantiqni faqat o'qiydi) | ha (`RtsClient`) |
| `GameConstants` | 32 | SAGE dan ko'chirilgan vaqt konstantalari (`LOGICFRAMES_PER_SECOND = 30`) | ha |
| `NameKeyGenerator` | 77 | satr→int internlash (SAGE `NameKeyGenerator`) | **YO'Q — o'lik** |
| `NameKeyType` | 24 | internlangan satr uchun handle | **YO'Q — o'lik** |

> `NameKeyGenerator` va `NameKeyType` faqat **bir-birini** ishlatadi; engine'da
> ularga bironta murojaat yo'q. `Kind.of(name)` internlashi ularning o'rnini
> bosgan.

### `uz.duke.core.thing` — obyekt modeli

| Fayl | Qator | Vazifa |
|---|---|---|
| `GameObject` | 168 | jonli nusxa: id, pozitsiya, orientatsiya, egasi, modullar, status bayroqlari |
| `ThingTemplate` | 137 | INI'dan o'qilgan o'zgarmas "chizma": nom, `Kind` bayroqlari, modul ro'yxati, `VisionRange` + **`BuildCost`/`BuildTime`** |
| `ThingTemplateLoader` | 87 | `Object … End` INI bloklarini template'ga aylantiradi |
| `ThingFactory` | 69 | template registri + `newObject` (modullarni `ModuleFactory` orqali quradi) |
| `World` | 63 | modullar simulyatsiyani so'roq qiladigan interfeys (SAGE global `TheGameLogic` o'rniga) |
| `Kind` | 52 | tasniflash bayrog'i, **nom bo'yicha interned**, identity bo'yicha solishtiriladi |
| `Geometry` | 100 | **fizik shakl** (SAGE `GeometryInfo`): sealed `Sphere` / `Cylinder` / `Box`. `POINT` = o'lchamsiz, hech narsa bilan to'qnashmaydi |
| `Footprint` | 110 | shakl + joy + yo'nalish = "yer ustidagi iz". `overlaps` / `separation` (yuzadan yuzaga) / `contains` |
| `ObjectId` | 18 | yaratilish tartibidagi barqaror identity |
| `ObjectStatus` | 14 | DISABLED / SLOWED vaqtinchalik bayroqlari |

> **Ochiq qaror:** `ThingTemplate` dagi `BuildCost` / `BuildTime` — sof
> strategiya tushunchasi, `core` da turishi noto'g'ri. Chiqarish uchun
> template'ga kengaytma-ma'lumot mexanizmi va `ThingTemplateLoader` ga
> maydon-registratsiyasi kerak (o'yin o'z INI maydonlarini qo'sha olsin).

### `uz.duke.core.module` — janrsiz xulq modullari

| Fayl | Qator | Vazifa |
|---|---|---|
| `MoveUpdate` | 202 | waypoint harakati, tezlik, burilish tezligi + **to'qnashuv**: har qadamdan oldin `World.findBlocker`, band bo'lsa ±45°/±90° chetlab o'tish, ilgarilamasa 2 soniyada voz kechish |
| `Locomotor` | 19 | marker interfeys: "bu modul obyektni o'z kuchi bilan harakatlantira oladi". Engine shu orqali janrsiz so'raydi — bu obyekt relyefning bir qismimi? |
| `ActiveBody` / `BodyModule` | 99 / 42 | sog'liq, zarar, davolash |
| `ModuleFactory` | 93 | INI tag → modul builder. `withDefaults()` faqat 2 ta: `ActiveBody`, `MoveUpdate` |
| `Armor` / `DamageType` | 47 / 18 | zarar turi ↔ zirh ko'paytirgichi |
| `Module` / `UpdateModule` / `ModuleData` | 26 / 20 / 17 | kompozitsiya asosi: obyekt xulqi = modullari yig'indisi |

### `uz.duke.core.ini` — ma'lumot qatlami

`Ini` (322) SAGE tokenizatorining sodiq porti · `FieldParseTable` /
`FieldParser` — C++ dagi offset+userData hiylasi o'rniga lambda-setter ·
`IniException`. Engine'ning data-driven bo'lishi shu 4 faylga tayanadi
(15 joyda ishlatiladi).

### `uz.duke.core.message` — buyruq quvuri

`Command` (marker, bitta metod: `playerIndex()`) · `MessageStream` (FIFO, kadr
boshida drenaj) · `CommandHandler`. Buyruqlarning **o'zi** bu yerda emas —
sealed ierarxiya modul chegarasidan o'ta olmaydi, shuning uchun buyruq to'plami
o'yinniki.

### `uz.duke.core.event` — lahzalar kanali

`WorldEvent` (marker: `frame()`, ixtiyoriy `where()`) · `ObjectDied` (nima edi,
kimniki, qayerda — chunki o'qilganda obyekt dunyoda yo'q).

Snapshot nima **bor**ligini aytadi, nima **bo'lgan**ini emas. Kanalsiz klient
taxmin qilishga majbur: "yo'qoldi va yarador edi = o'ldi". `GameLogic.post()`
e'lon qiladi, klient `drainEvents()` bilan oladi (tozalash emas — engine bitta
klient kadriga bir nechta mantiq kadrini yugurtirishi mumkin). Navbat
chegaralangan: hech kim drenaj qilmasa eng eskisi tashlanadi.

**Bir tomonlama:** simulyatsiya ularni qaytib o'qimaydi, `checksum()` ga
kirmaydi. Simulyatsiyani o'zgartiradigan narsa — hodisa emas, **modul**:
`DieModule` vayronani qoldiradi, `ObjectDied` esa renderga portlashni aytadi.

### `uz.duke.core.replay` — o'yinni yozib olish

`FrameLog` (yozib olish choki) · `ReplayRecorder` · `Replay` · `ReplayMismatch`.

Deterministik sim = boshlang'ich shart + buyruqlar funksiyasi, demak yozib
olinadigan narsa faqat shu ikkitasi — dunyoni saqlash shart emas, qaytadan
hisoblanadi. Format ham yangi emas: yozuv `NetFraming` bilan yoziladi, ya'ni
**replay — saqlab qolingan tarmoq oqimi**.

Har 30 kadrda dunyo xeshi yoziladi. Qayta o'ynatishda mos kelmasa —
`ReplayMismatch`, ya'ni determinizm nosozligi **build ichida**, kadr raqami
bilan ushlanadi. Aynan shu narsa replay'ni demo'dan ko'ra qimmatliroq qiladi.

**Yoziladigan narsa** — sim *iste'mol qilgan* buyruqlar, kimdir bosgan tugma
emas; shu tufayli bitta recorder single-player va multiplayer uchun birdek
ishlaydi. `Replay.beforeStep` avval `discardPendingCommands()` qiladi, aks holda
simning o'zi yaratgan buyruq (skript, taymer) qayta o'ynatishda ikki marta
qo'llanardi.

### `uz.duke.core.network` — lock-step

**Ikkita plan bitta ulanishda:** `NetMessage` sealed — `CommandPacket`
(*ma'lumot* plani, o'yinniki, o'yinning `PacketCodec` i kodlaydi) hamda
`PeerLeft`, `FrameChecksum`, `SessionHalted` (*boshqaruv* plani, engine'niki).
Konvert — `NetFraming` (`C …` / `L …` / `K …` / `H …`). Shu ajratish tufayli
engine o'zi bilmaydigan o'yin uchun ham a'zolikni boshqaradi, determinizmni
tekshiradi va kerak bo'lsa o'yinni to'xtatadi.

`SessionState`: `RUNNING` → `DESYNCED` yoki `DISCONNECTED`. Tashqaridan har
qanday to'xtash bir xil ko'rinadi (sim qimirlamaydi), shuning uchun sekin
o'yinchini kutayotgan peer bilan o'yini tugagan peer aynan shu bilan farqlanadi.

| Fayl | Vazifa |
|---|---|
| `LockstepGate` | engine har kadr so'raydigan darvoza: prime → `pump` → lokal buyruqni `frame+delay` ga jo'natish → tayyor bo'lmasa **to'xtash**. Uzilishni **faqat host** qarorga aylantiradi |
| `LockstepScheduler` | kadr darvozasining hisobi; a'zolik **kadrga bog'liq** (`retirePlayer(player, fromFrame)`) |
| `HostTransport` | host tomoni: har mehmonga bitta ulanish, kelgan xabarni qolganlarga **uzatadi** |
| `SocketTransport` | bitta ulanish (mehmon tomoni); reader thread navbatga qo'yadi, o'yin thread'i `pump()` qiladi |
| `LoopbackTransport` | in-process fan-out (hotseat, replay, test) |
| `PacketCodec` | sim format plagi — o'yin o'z buyruqlarini kodlaydi |

**Uzilish = qaror, muzlash emas.** Har bir peer o'zicha sezsa, ular turli
kadrlarda kutishdan to'xtaydi va dunyolar ajraladi. Shuning uchun host ketgan
o'yinchining jimligini to'ldiradi va `PeerLeft(player, fromFrame)` e'lon qiladi;
hamma **aynan o'sha kadrda** to'xtaydi. Mehmon host'ni yo'qotsa hech narsa hal
qilmaydi — `isConnectionLost()` bo'ladi.

**Desync aniqlash va to'xtatish.** Lock-step — va'da, mexanizm emas: buyruqlar
bir xil dunyo beradi deb *ishoniladi*, buni esa hech narsa tekshirmasdi. Har
`CHECKSUM_INTERVAL = 30` kadrda peer'lar dunyosini xeshlab e'lon qiladi
(`FrameChecksum`). Farq chiqsa — holat `DESYNCED`, darvoza boshqa ochilmaydi, va
host `SessionHalted` bilan hammaga aytadi. Xesh kadr *boshlanishidan oldin*
olinadi: bu peer'lar post-step hook'siz kelishishi mumkin bo'lgan yagona nuqta.

**Nega host yetarli:** hamma hammaning xeshini oladi va tenglik tranzitiv, demak
har qanday nomuvofiqlikda host albatta ishtirok etadi — ikkita mehmon
bir-biridan farq qilib, ikkalasi ham host bilan mos kelishi mumkin emas.
Shuning uchun "qaysi peer haq" degan arbitratsiya yo'q: hamma to'xtaydi.
Tiklash (resync/reconnect) engine ishi emas.

Tashqi kutubxona yo'q — hammasi `java.net`.

### `uz.duke.core.pathfind` — yo'l topish

`Pathfinder` (146 — deterministik A*: butun sonli narxlar 10/14, octile
evristika, tenglikda katak indeksi bo'yicha uziladi, qat'iy qo'shni tartibi) ·
`PathGrid` (katak = 10 dunyo birligi) · `Path` · `MapLoader` (ASCII → grid).

`PathGrid` **ikki qatlamli**: *relyef* (map yozadi, hech qachon o'zgarmaydi) va
*to'siqlar* (simulyatsiya yozadi — binolar va boshqa harakatlanmaydigan jismlar).
`isBlocked` = ikkalasining OR'i. Ajratilgani sababli, qoyaga tiralib qurilgan
bino buzilganda qoyada teshik qolmaydi.

Harakatlanmaydigan geometrik obyekt to'siq qatlamiga bosiladi, ya'ni **A* ni
binolar aylanib o'tadi** — lokal chetlab o'tishga tayanib qolmaydi.
"Harakatlanmaydigan" janrsiz aniqlanadi: obyektda `Locomotor` moduli bormi?
Qatlam butunlay qayta quriladi (sanoq yuritilmaydi — u dunyodan ajralib qolishi
mumkin), `staticObstaclesDirty` bayrog'i bilan faqat kerak bo'lganda.

Qatlamning **versiyasi** bor (`getObstacleVersion` / `World.getNavigationVersion`).
Har bir `MoveUpdate` marshrutini qaysi versiyada tuzganini eslab qoladi va farq
sezsa yo'lni qayta tuzadi — yo'l qolmagan bo'lsa to'xtaydi. Versiya **natijaga**
qo'yiladi: `commitObstacles()` yangi qatlamni eskisi bilan solishtiradi va faqat
haqiqatan farq bo'lsa oshiradi, aks holda har bir tayyor bo'lgan piyoda butun
armiyani qayta yo'l tuzishga majbur qilardi.

### `uz.duke.core.player` — o'yinchilar

`PlayerList` (88 — `PlayerFactory` orqali o'yin o'z subtipini qo'yadi) ·
`Player` (63 — faqat identity + diplomatiya) · `Relationship`.

### `uz.duke.core.partition` — fazoviy so'rovlar

`PartitionManager` (140 — "radiusdagi obyektlar", "eng yaqin dushman",
"bu yer bo'shmi", "shu joydan nimaga yetaman"; hozircha halol brute-force skan)
+ `PartitionFilter` (kompozitsion predikatlar). `GameLogic` ishlatadi.

**Fizik dunyo API'si** (`World` orqali modullarga ochiq):

| Metod | Nima uchun |
|---|---|
| `findBlocker(mover, position)` | "shu yerga qadam tashlasam, kim xalaqit beradi?" — lokomotor har kadr shuni so'raydi |
| `findClearPosition(shape, near, radius)` | "shu shakl shu yerga sig'adimi, sig'masa qayerga?" — yangi birlik, tushirilgan yo'lovchi |
| `findClosestInReach(from, reach, filter)` | "shu yerdan nimaga yetaman?" — masofa markazdan emas, **yuzadan yuzaga** |
| `World.reachBetween(a, b)` | ikki obyekt orasidagi devordan devorgacha masofa |

### `uz.duke.core.script` — trigger'lar

`ScriptEngine` (65 — har kadr baholaydi) + `Trigger` (shart→harakat).
`GameLogic` ishlatadi, lekin yuqori qatlamlar (`game` / `studio`) undan
foydalanmaydi — u yerdagi `UnitScript` butunlay boshqa mexanizm.

### `uz.duke.core.math` — matematika

`Coord3D` (61) hamma joyda ishlatiladi. `Coord2D` (44) va `ICoord3D` (23) —
**bironta joyda, hatto testda ham ishlatilmaydi.**

### `uz.duke.core.client` — rendering choki

`Renderer` interfeysi (21) + `RenderingGameClient` (38).
**Haqiqiy klientlarning hech biri buni ishlatmaydi:** `game` ning Swing paneli
ham, `client3d` ham `WorldSnapshot` ni o'qiydi, `Renderer` ni emas.
`RenderingGameClient` ga main'da bironta murojaat yo'q (faqat 1 ta test).

---

## O'chirish tahlili

**O'chirsa bo'ladi — hech narsa buzilmaydi:**

| Nomzod | Sabab | Narxi |
|---|---|---|
| `math/Coord2D`, `math/ICoord3D` | 0 murojaat (main ham, test ham) | yo'q |
| `NameKeyGenerator` + `NameKeyType` (+ testi) | faqat bir-birini ishlatadi; `Kind` internlash o'rnini bosgan | yo'q |
| `client/Renderer` + `client/RenderingGameClient` | hech bir haqiqiy klient ishlatmaydi; `rts/client/AsciiRenderer` bilan birga o'chadi | ASCII minimap va uning testi yo'qoladi |
| `network/LoopbackTransport` | faqat testda; hotseat/replay senariysi hali yo'q | replay kerak bo'lganda qayta yozish kerak |

**O'chirib bo'lmaydi (yadro):** `GameEngine`, `GameLogic`, `SubsystemInterface`,
`SubsystemList`, `GameConstants`, butun `thing` / `module` / `ini` / `message`
paketlari, `pathfind`, `player`, `network` (Loopback'dan tashqari).

**Chegaradagilar:**
`partition` — faqat `GameLogic` ishlatadi, lekin uning `objectsInRange` /
`findClosest` API'si `rts` modullariga kerak, ya'ni bilvosita ishlaydi.
`script` — engine imkoniyati sifatida to'g'ri, lekin hozircha faqat `core`
ichida qolgan: g'alaba/mag'lubiyat mantig'i `game` da alohida yozilgan.

## Determinizm haqida bitta qattiq qoida

Mantiq yo'lida trigonometriya **`StrictMath`** orqali ketadi, `Math` orqali emas.
`Math.sin`/`cos`/`atan2` faqat 1 ulp aniqlikda kafolatlanadi va platforma
intrinsic'laridan foydalanishi mumkin — ya'ni ikki peer oxirgi bitda farq
qilishi mumkin, bu esa desync. `StrictMath` hamma joyda bit-aniq bir xil.
`Math.sqrt` va `Math.abs` aniq, ularni ishlatish mumkin.

## Ochiq ishlar

1. `ThingTemplate` dagi `BuildCost` / `BuildTime` ni chiqarish (template
   kengaytma-ma'lumoti + maydon registratsiyasi kerak) — **qaror kutilmoqda**.
2. `PartitionManager` hali brute-force; endi u har kadr to'qnashuv uchun ham
   so'raladi, ya'ni SAGE'ning katak-gridiga o'tish avvalgidan muhimroq.
3. `MapLoader` faqat ASCII bilan cheklangan — haqiqiy relyef formati yo'q;
   dunyo hali ham tekis (`Coord3D.z` faqat xeshda o'qiladi).
4. Ikkita `Box` bir-biriga qarshi tekshirilganda o'rab turuvchi doira
   ishlatiladi (burchaklarda ortiqcha teginish). Birlik ↔ har qanday shakl aniq.
