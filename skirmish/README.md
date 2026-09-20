# `skirmish` — Duke Skirmish

**Rol: O'YIN.** Engine ustidagi **ikkinchi** o'yin, va ataylab birinchisiga
umuman o'xshamaydi.

| | |
|---|---|
| Bog'liqligi | `client3d` + `kit` |
| Kim bunga bog'lanadi | hech kim |
| Hajmi | 385 qator Java + 141 qator data |
| Ishga tushirish | `./gradlew :skirmish:run` |

## Nega bor

Dungeon'dan olingan shablonlar dungeon'ga mos tushishi hech narsani isbotlamaydi
— ular o'shandan kelib chiqqan. Shuning uchun **noldan yozilgan, boshqacha**
o'yin kerak edi: engine haqiqatan istalgan RTS/RPG ni ko'tara oladimi, va
universal qatlamga nimalar yetishmayapti degan savolga javob beradi.

| Dungeon | Skirmish |
|---|---|
| bitta qahramon | ko'p unit, tomonlar |
| qavatlar, zinalar, relyef | bitta yassi ochiq maydon |
| maxluqlar xonalarda paydo bo'ladi | unit **sotib olinadi** |
| loot, daraja, skill, mana | pul, narx, qurilish vaqti |
| pastga tushasan | dushman qarorgohini yiqitasan |

## Nima topdi va nima chiqdi

Topilgan bo'shliqlardan **birinchisi yopildi**: `core.thing.Drawn` + tayyor
`Visuals.draw(...)` bog'lovchisi, va `RtsTemplate` ham ko'rinish maydonlarini
oldi. Natijada bu o'yinning `Main.java` sidan 21 qator o'chdi.


To'liq ro'yxat: `docs/plan/2026-09-20-vocabulary-and-scripts.md`, «6-bosqich».
Qisqasi:

1. **`RtsTemplate` da ko'rinish maydonlari yo'q** — ikkala o'yin ham o'z unit
   rekordini mustaqil ixtiro qildi. Yetishmayotgan seam shu.
2. Ikki rekordning kesishmasi **14 komponent** — `Unit` interfeysi uchun aniq
   ro'yxat.
3. **`rts` da nishonga yaqinlashish yo'q**: hujum buyrug'i qurolga nishon
   beradi, lekin unit masofadan tashqarida bo'lsa joyidan qimirlamaydi.
4. Yassi o'yin `Layered` ga umuman tegmaydi — seam haqiqatan ixtiyoriy.
5. `Sun` va `Camera` hech o'zgarishsiz ishlatildi — 2a-bosqich to'g'ri tanlagan.

## Nima yo'q

Xarita paketi yo'q — maydon hozircha `DukeGame.map(w, h)` bilan quriladi. Ikki
tomon ham qo'lda joylashtiriladi; AI yo'q.
