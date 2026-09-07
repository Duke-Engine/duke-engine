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

`Duke3D.launch(game, Visuals.create())`, tamom. Hech qanday `Visuals`
bog'lanmagan, shuning uchun har bir jonzot **rangli primitiv** bo'lib
chiziladi — bu bosqichda shakllar o'yinning o'zi, san'atning o'rinbosari emas.

Kamera yerdan ~55° burchakda turadi (`(0, d×0.82, d×0.57)`), ya'ni tepadan
vertikal emas. Boshqaruv — engine'niki: qahramonni **LMB** bilan tanlaysiz,
**RMB** bilan yerga (yurish) yoki skeletga (hujum) buyruq berasiz.

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
