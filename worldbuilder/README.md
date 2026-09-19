# `worldbuilder` — Duke World Builder (stage muharriri)

**Rol: ASBOB.** Engine emas, o'yin ham emas — **Duke Dungeon uchun qotirilgan
xarita yasaydigan** Swing muharriri.

| | |
|---|---|
| Bog'liqligi | `implementation project(":dungeon")` — o'yinning o'zi |
| Ishga tushirish | `./gradlew :worldbuilder:run` |
| Fayl ochib | `./gradlew :worldbuilder:run --args="dungeon/src/main/resources/data/maps/first.duke"` |
| Namuna stage'ni qayta yozish | `./gradlew :worldbuilder:writeExampleStage` |
| Testlar | 9 ta, hammasi headless (`StageDraft`, Swing'siz) |

> **Duke Studio'ga tegilmagan.** U engine'ning RTS muharriri va boshqa savolga
> javob beradi. Bu — bitta o'yinning o'z asbobi, shuning uchun `dungeon` ga
> bog'lanadi: u yasaydigan stage o'sha o'yinning generatori bilan chiziladi,
> o'sha o'yinning qoidalari bilan tekshiriladi va o'sha o'yinning `.duke`
> fayllaridagi maxluqlar bilan to'ldiriladi.

## Mehnat taqsimoti (butun asbob shunga tayanadi)

**Generator joyni chizadi, muallif uni to'ldiradi.**

Xona, koridor, qavat va zinapoyalar seed'dan keladi va bu yerda **hech qachon
tahrirlanmaydi** — ularda ulanish kafolati bor (koridorlar qamrovchi daraxt), va
sichqoncha tutgan odam uni bir kunda buzadi. Tahrirlanadigani — ularning
ichida nima turishi: qaysi burchakda qaysi maxluq, boss qayerda kutadi,
o'yinchi qayerdan kiradi.

Shuning uchun bu yerda **pol/devor chizish asbobi yo'q**, xona qo'shish yo'q,
xarita o'lchamini o'zgartirish yo'q. Yoqmasa — yangi seed.

## Avval so'raydi, keyin chizadi

Ishga tushganda (va `New stage…` bosilganda) **oldin savol**:

```
┌─ New stage ──────────────────────────────────┐
│  Width       [ 100]  cells across            │
│  Height      [  76]  cells down              │
│  Rooms       [  28]                          │
│              about 38 fit a map 100 x 76     │
│  Difficulty  [   8]                          │
│              monsters at 275% health and     │
│              240% of their number, led by a  │
│              Champion — deeper than the      │
│              descent itself goes (4)         │
└──────────────────────────────────────────────┘
```

**Nega oldin?** Ikkalasini ham keyin qo'llab bo'lmaydi:

- **O'lcham** — kattaroq xarita "o'sha xonalar uzoqroqda" emas, **boshqa qavat**.
- **Qiyinchilik** — u qavat **qaysi chuqurlikda generatsiya qilinishi**, ya'ni qaysi
  turdagi maxluqlar umuman paydo bo'lgan (`MinDepth`) va oxirida qaysi boss turgani.

Keyin so'ralsa — qayta chizish kerak bo'lardi, muallif esa o'zgarishi kutilayotgan
qavatga allaqachon narsalarni terib qo'ygan bo'lardi.

**Qiyinchilik = chuqurlik.** Yangi shkala emas: `Stage.difficulty` — tushishning
nechanchi qavatiga teng ekani. "Qiyinchilik 7" degani "tushishning 7-qavati
qanday bo'lsa, shunday" — va buni borib tekshirsa bo'ladi. Tushish o'zi 4-qavatda
tugaydi, ya'ni **undan chuqurroq bosqich yasash** — bosqich yasashning asosiy
sabablaridan biri. Pastdagi izoh o'yinning o'z raqamlaridan o'qiladi, shuning
uchun u arifmetikadan ajralib qolmaydi.

**Xona soni — taxmin, qoida emas.** Xonalar tashlab-rad qilib joylashtiriladi,
ya'ni sig'maganini sig'dirib bo'lmaydi. 40 so'rab 22 tushsa, muharrir buni
**aytadi** — aytmasa muallif o'zi yozgan raqamga ishonaverardi.

## Ekran

```
[New stage…] Seed: [20260911] [Generate] [New seed] | [Open…] [Save] [Save as…] | [Undo] [Redo]
Name: [...]  About: [...]  Difficulty: [8]  Players: [1] | Place: [Monster ▾][Skeleton ▾]
─────────────────────────────────────────────────────────────────────────────
        tepadan 2D ko'rinish — tosh, pol (qavat bo'yicha soya), zinapoya,
        xona konturlari, kirish · boss (halqa bilan) · maxluqlar · prop'lar
─────────────────────────────────────────────────────────────────────────────
 What is wrong with it:
   ⚠ room 4 at 31,12 cannot be walked to from the entrance
   ⚠ the Skeleton at 19,6 is standing on the Pillar
 Ready to play · first.duke — unsaved
```

- **LMB** qo'yadi · **RMB** olib tashlaydi · **g'ildirak** zoom (kursor ostidagi
  katak joyida qoladi) · **MMB** sudrab pan.
- **Ctrl+Z / Ctrl+Y / Ctrl+S / Ctrl+O**.
- Sudrash **chizmaydi**: bitta klik = bitta jonzot. Sudralgan skeletlar qatori —
  hech kim xohlamagan xona.

**Maxluq va prop ro'yxati `DungeonSettings` dan o'qiladi** (`data/units/` va `data/props/` dagi
`Monster` / `Prop` bloklari) — ya'ni generator qaysi ro'yxatdan
tortsa, muharrir ham o'shandan. Muharrir hech qachon o'yin spawn qila olmaydigan
narsani taklif qilmaydi. Nuqtaning rangi ham o'sha fayldan, shuning uchun bu
yerdagi xarita minimapga o'xshaydi.

## Pastdagi ro'yxat — asbobning butun sifati

Bu ro'yxat — **o'yin yuklashda ishlatadigan aynan o'sha tekshiruv**
(`StageCheck`), va aynan shu muhim: muallifga bu yerda bir narsa, Play bosganda
boshqa narsa aytilmaydi. Yurish engine'ning o'z qoidasi bilan tekshiriladi
(`PathGrid.canStep`), nusxasi bilan emas.

Muharrir **buzuq stage'ni ham saqlaydi** — ataylab. Muallif ishni yarmida
to'xtaydi, va tugatmagan ishni saqlashdan bosh tortadigan asbob uni bir o'tirishda
tugatishga yoki yo'qotishga o'rgatadi. **O'yin** esa buzuq stage'ni o'ynashdan
bosh tortadi — noto'g'rilik haqiqatan qimmatga tushadigan joy o'sha.

## Fayllar

| Fayl | Nima |
|---|---|
| `StageDraft.java` | tahrirlash qoidalari — **Swing'siz**, testlar shuni sinaydi |
| `ui/BuilderWindow.java` | oyna: tugmalar, metama'lumot, undo, xatolar ro'yxati |
| `ui/StageCanvas.java` | tepadan ko'rinish + sichqoncha |
| `ui/Palette.java` | nima qo'yiladi (ikkita dropdown) + rang lug'ati |
| `ExampleStage.java` | `dungeon/.../data/maps/first.duke` va `deep.duke` ni qayta yozadigan task |

Format va tekshiruvning o'zi bu yerda emas — ular `dungeon` modulida
(`uz.duke.dungeon.stage`), chunki **o'yin ularni o'qiydi**. Muharrir ularning
mijozi, egasi emas.

## Bilib qo'yish kerak

- **`;` — izoh boshlanishi**, butun formatda. Name/About maydonlariga yozilgan
  `;` yozilgan zahoti vergulga aylanadi — keyinroq saqlashda tashlanmasin uchun.
- **Boss asbobi ikkita ishni bitta bosishda qiladi:** bossni ko'chiradi va
  "boss xonasi" ni o'sha katak turgan xonaga qo'yadi. Ular bitta qaror.
- **Yangi seed hamma narsani o'chiradi** — shuning uchun so'raydi.
- **Yuqoridagi Difficulty spinner'i faqat raqamlarni qayta o'lchaydi.** Qavat
  qayta chizilmaydi: qaysi **turdagi** maxluqlar bu yerda va qaysi boss kutayotgani
  qavat chizilganda hal bo'lgan. Ularni o'zgartirish uchun — `New stage…`.
- **Chizish cheklovi (menikida emas, klientda):** `TerrainScene` har tosh katak
  uchun **bitta `Geometry`** yasaydi va batch qilmaydi. Shipping qavatda ~995 ta,
  100×76 da ~4700, 200×150 da ~22 000. Generatsiya, tekshiruv va simulyatsiya
  200×150/70 xonada ham bemalol ishlaydi (56 ms), lekin **3D'da chizish sinalmagan**
  — juda katta bosqich yasasangiz avval o'yinda ochib ko'ring.
