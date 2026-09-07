# `dungeon` — Duke Dungeon (engine'dagi ilk o'yin)

**Rol: O'YIN.** Engine emas, engine'ning **mijozi**. Bu modul faqat `game`
ning ommaviy API'siga bog'lanadi va engine'ga bironta narsa qo'shmaydi —
aynan shu uning maqsadi: agar o'yin yashash uchun yangi engine imkoniyatini
talab qilsa, demak engine tugallanmagan.

| | |
|---|---|
| Bog'liqligi | `implementation project(":game")` — jME yo'q, sof Java |
| Hajmi | 4 fayl, ~450 qator |
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

## Nega o'z oynasi bor

`DukeGame.start()` beradigan tayyor panel — RTS ko'rinishi: birlikni tanla,
keyin o'ng tugma bilan buyruq ber. Dungeon boshqacha o'qiladi: **bitta klik**,
va qahramon bosilgan narsa bilan shug'ullanadi. Farq faqat kiritishda,
shuning uchun u engine o'zgarishiga emas, kichik o'z oynasiga arziydi
(`DungeonView`). U simulyatsiyaga tegmaydi — `WorldSnapshot` o'qiydi va
`postCommand` yuboradi, ya'ni engine beradigan yagona thread choki.

## Keyingi qadamlar (hozir YO'Q)

Procedural generatsiya · ko'p xona, boss, leveling · o'lim/qaytadan sikli ·
model, tekstura, ovoz, musiqa.
