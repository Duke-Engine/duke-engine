# `sandbox` — 2D demo skirmish

**Rol: DEMO.** Engine ham emas, o'yin ham emas — API'ning namoyishi.
"40 qatordan kam kodda o'ynasa bo'ladigan RTS" da'vosining isboti.

| | |
|---|---|
| Bog'liqligi | `implementation project(":game")` |
| Kim bunga bog'lanadi | hech kim |
| Hajmi | **1 fayl, 69 qator** |
| Testlar | 0 |
| Ishga tushirish | `./gradlew :sandbox:run` |

## Nima qiladi

`STARTER_UNITS` INI'sini yuklaydi, 70×45 map quradi, ikkita o'yinchi qo'yadi
(USA ko'k / China qizil), ikkala bazani spawn qiladi va dushmanga oddiy AI
beradi: `everySeconds(6, …)` bilan kazarma navbati 2 tadan kam bo'lsa yangi
Rifleman qo'shadi, rally nuqtasi — sizning bazangiz. `game.start()` Swing
oynasini ochadi.

## O'chirish tahlili

**O'chirsa bo'ladi.** Hech kim bunga bog'lanmaydi, test yo'q, mazmun yo'q.

Ikki narsani hisobga oling:

1. **`sandbox3d` bilan ~90% bir xil** — bir xil bazalar, bir xil `foeBarracks`
   yordamchisi, bir xil AI. Bittasini saqlash yetadi.
2. `CLAUDE.md` dagi "done" mezoni `./gradlew :sandbox:run` hali ham siklni
   yurgizishini talab qiladi — o'chirsangiz o'sha mezonni ham yangilang.
3. Bu `game/swing` (2D klient) ning yagona real iste'molchisi. `game/swing` ni
   o'chirish qaroriga bog'liq: ikkalasi birga ketadi.
