# `dungeon` — Duke Dungeon (engine'dagi ilk o'yin)

**Rol: O'YIN.** Engine emas, engine'ning **mijozi**. Bu modul faqat `game`
ning ommaviy API'siga bog'lanadi va engine'ga bironta narsa qo'shmaydi —
aynan shu uning maqsadi: agar o'yin yashash uchun yangi engine imkoniyatini
talab qilsa, demak engine tugallanmagan.

| | |
|---|---|
| Bog'liqligi | `implementation project(":client3d")` — engine'ning 3D klienti |
| Hajmi | 3 fayl, ~270 qator |
| Testlar | 5 ta (headless) |
| Ishga tushirish | `./gradlew :dungeon:run` |

## Hozirgi qadam

Eng kichik **o'ynaladigan** holat, ataylab yalang'och: bitta xona, bitta
qahramon, uchta skelet. Model yo'q, tekstura yo'q, ovoz yo'q — hamma narsa
rangli shakl. Maqsad — engine'ni *o'ynasa bo'ladimi* degan savolga javob
olish, buning eng tez yo'li esa javobni yashira oladigan hamma narsani
olib tashlash.

- **Xona** — qo'lda chizilgan ASCII (`Dungeon.ROOM`), `#` = tosh. Ikkita
  ustun ataylab qo'yilgan: bitta xonada ham A* o'z o'rnini oqlashi kerak.
- **Yurish** — yerni bosasiz → `GameMessage.MoveTo`. Yo'lni engine topadi.
- **Hujum** — skeletni bosasiz → `MoveTo` **so'ng** `AttackObject`.
- **O'lim** — HP tugaydi, obyekt dunyodan chiqadi; klient buni `ObjectDied`
  hodisasidan biladi (yo'qolib qolganidan taxmin qilmaydi).

## Ikkita nozik joy

**Skeletlarda `Speed = 0` bo'lsa ham `MoveUpdate` bor.** Engine uchun
*shakli bor, lekin `Locomotor` moduli yo'q* obyekt — relyef, va u navigatsiya
gridiga bosiladi. Modulsiz skeletlar **devorga aylanardi** va qahramon
ularga umuman yeta olmasdi. Bu modul ularni mebel emas, jonzot qiladi.

**Hujumda buyruq tartibi muhim.** `MoveTo` joriy nishonni bekor qiladi
(engine uchun aniq yurish buyrug'i = "qilayotganingni unut"), shuning uchun
`AttackObject` **keyin** kelishi shart, aks holda qahramon yetib borgunicha
u yo'qolib ketardi.

## Klient — engine'ning 3D klienti

`Duke3D.launch(game, Visuals.create(), shell)`, tamom. Hech qanday `Visuals`
bog'lanmagan, shuning uchun har bir jonzot **rangli primitiv** bo'lib
chiziladi — bu bosqichda shakllar o'yinning o'zi, san'atning o'rinbosari emas.

Kamera yerdan ~55° burchakda turadi (`(0, d×0.82, d×0.57)`), ya'ni tepadan
vertikal emas. Boshqaruv — engine'niki: qahramonni **LMB** bilan tanlaysiz,
**RMB** bilan yerga (yurish) yoki skeletga (hujum) buyruq berasiz.

> Bosh menyu ham shu o'yinniki: "Enter the dungeon / Settings / Quit" — LAN
> bandisiz.
>
> Boshida bu modul o'z 2D Swing oynasi bilan yozilgan edi (bitta klik = bitta
> buyruq). Ekranda ko'rilgach ma'lum bo'ldiki, u o'yinga emas, o'yin
> diagrammasiga o'xshaydi — shuning uchun engine'ning 3D klientiga
> o'tkazildi. Kiritish endi RTS uslubida, chunki klient shunday.

**Ma'lum vizual cheklov:** devor bloklari past (balandligi 6 birlik, katak
eni 10) — bu `client3d` ning relyef chizishida qattiq yozilgan, o'yin uni
o'zgartira olmaydi. Baland devorlar kerak bo'lsa, bu engine ishi bo'ladi.

## Keyingi qadamlar (hozir YO'Q)

Procedural generatsiya · ko'p xona, boss, leveling · o'lim/qaytadan sikli ·
model, tekstura, ovoz, musiqa.

## Stage rejimi — o'zgarmaydigan xarita

O'yinning ikkinchi turi. Roguelike tushishi har run'da yangi qavat chizadi va
savol "qanchaga tushdim?" bo'ladi. **Stage** — qotirilgan qavat: o'sha xonalar,
o'sha burchaklarda o'sha maxluqlar, har safar. Savol "shuni yengaman-mi?" ga
aylanadi — Warcraft custom map uslubi.

```
./gradlew :dungeon:run --args="--map=first"
```

yoki `data/game.duke` da:

```
Game
  StartMap = first
  Files = [ … ]
End
```

Map uning `Name`i bilan yoki map faylining yo'li bilan so'raladi. **Bo'sh bo'lsa —
roguelike**, aynan avvalgidek. Argument fayldan ustun turadi. Yo'l avval diskdan,
topilmasa classpath'dan qidiriladi (shipping map installer ichida yuradi).

**Stage'da:** o'lsang — o'sha stage boshidan (yangi dungeon EMAS). Bossni
o'ldirsang — g'alaba, chunki ostida qavat yo'q.

### Format — `dungeon/src/main/resources/data/maps/first.duke`

Matn: bitta `StaticMap` bloki, boshqa `.duke` fayllar kabi o'qiladi. Binar format —
hech kim ocholmaydigan daraja: g'alati ishlaydigan stage kimdir ochib, o'qib, xatoni
ko'ra oladigan fayl bo'lishi kerak. Koordinatalar **katakda** (dunyo birligida emas),
chunki ular bir xil fakt va faqat birini `Cells` rasmi bo'yicha ko'z bilan sanash mumkin.

Fayl ichida: metama'lumot + seed, bitta `Cells` xaritasi (`#` tosh, raqam qavat,
`/` zina), xonalar, koridor ulanishlari, kirish, boss, maxluqlar, prop'lar. Map
`data/game.duke` ro'yxatida bo'lsa — menyuda chiqadi. Endless tushishning sozlamalari
esa `data/maps/endless.duke` dagi `ProceduralMap`.

**Stage fayli — muzlatilgan `GeneratedDungeon`.** Ya'ni `Spawner` qotirilgan
qavatni bir daqiqa oldin chizilganidan ajrata olmaydi — "stage o'zi kesilgan
dungeon bilan aynan bir xil o'ynaladi" degani shundan kelib chiqadi, ehtiyotkorlikdan
emas. `StagePlayTest` buni checksum bilan qulflaydi.

**Yuklashda qayta tekshiriladi** (`StageCheck`): qo'lda tahrirlangan fayl
yetib bo'lmaydigan xona, toshdagi maxluq yoki belgilanmagan boss bilan kelsa,
o'yin **hammasini ro'yxat qilib to'xtaydi** — jimgina roguelike'ga qaytmaydi.
Yurish engine'ning `PathGrid.canStep` i bilan tekshiriladi, nusxasi bilan emas.

Stage yasash — `./gradlew :dungeon:newMap --args="nom seed [chuqurlik [eni bo'yi xonalar]]"` qavatni seed'dan chizadi; nima qayerda turishini IDE'dagi **Map** tabida qo'lda qo'yasiz.

### Qiyinchilik = chuqurlik

`Stage.difficulty` — **o'sha bosqich qaysi chuqurlikda o'ynalishi**, yorliq emas.
Yangi mexanizm yasalmadi: qavatni xavfli qiladigan hamma narsa allaqachon
`depth` bo'yicha yozilgan va sozlangan — `monsterHealthAt`, `monsterDamageAt`,
`monsterCountAt`, qaysi turlar umuman paydo bo'lgani (`MinDepth`), va
`bossKindAt`. Shuning uchun "qiyinchilik 7" ning ma'nosi bor: tushishning
7-qavati qanday bo'lsa, shunday — borib tekshirsa bo'ladi.

Tushish `Bosses` ro'yxati tugaganda tugaydi (hozir 4 qavat). Bosqich esa
**undan chuqurroq** qurilishi mumkin — bu bosqich yasashning asosiy
sabablaridan biri.

O'lmoq → **o'sha chuqurlikda** qayta boshlanadi (1 ga tushmaydi, aks holda
qiyin bosqichning ikkinchi urinishi oson bo'lib qolardi). Bossni o'ldirmoq →
g'alaba, qaysi chuqurlikda qurilgan bo'lsa ham.

Chuqurlik qayerdan boshlanishini `Floors.firstDepth()` aytadi: `GeneratedFloors`
1 qaytaradi (roguelike o'zgarmadi), `StageFloors` — bosqichning qiyinchiligini.

### O'lcham

Generatsiya o'lchami endi `Layout` record'i orqali override qilinadi
(`gen/Layout.java`). `Layout.of(settings)` — bugungi dungeon, **bit-baravar**;
`Layout.sized(settings, w, h, rooms)` — muallif so'ragani.

Shipping bosqichlar: `first.stage` (50×36, 9 xona, chuqurlik 1) va
`deep.stage` (100×76, 28 xona, **chuqurlik 8** — tushishdan chuqurroq).
Ikkalasini `./gradlew :dungeon:writeExampleMaps` qayta yozadi.

**Ulanish kafolati kattalikda ham tekshirilgan:** `LayoutTest` 180×140 / 60 xona
o'lchamda 12 ta seed'ni `StageCheck` bilan yurib chiqadi.
