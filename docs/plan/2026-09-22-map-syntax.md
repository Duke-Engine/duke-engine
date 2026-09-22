# `[ … ]` sintaksisi: map va vergul

**0.3.0 uchun. Ikkita format o'zgarishi, bir vaqtda.**

Ikkalasi ham bitta narsaga tegadi — `[ … ]` nima ekaniga — shuning uchun birga chiqadi. Alohida
chiqarilsa, ma'lumotni ikki marta ko'chirish kerak bo'ladi.

---

# 1. Map: blokdan `[ … ]` ga

## Nega

`CLAUDE.md` dagi qoida:

> `Binder` makes each block the record its word names, **every line in it one of its components by name**

Map hozir ichki blok sifatida yoziladi va bu qoidani buzadi — `CRUSH` hech qaysi rekordning komponenti emas:

```
Armor
  Name = HumanArmor
  Multipliers        ← komponent
    CRUSH = 2.0      ← komponent emas
  End
End
```

## Nimaga o'zgaradi

Yagona shakl, `Modules = [ … ]` bilan bir xil naqsh, faqat har bir yozuv oldidan kalit:

```
Multipliers = [ CRUSH = 2.0, ARMOR_PIERCING = 0.1 ]

Multipliers = [
  CRUSH = 2.0
  ARMOR_PIERCING = 0.1
]

Pieces = [
  Minimap = Piece
    Texture = ui/panel/minimap.png
    Inset = 16
  End
]
```

Qiymat skalyar bo'lsa `k = qiymat`, uchinchi maydon kerak bo'lsa u rekordga aylanadi va `k = Word … End`
bo'ladi. Ikkala holatda ham blok darajasida faqat `Komponent = …` turadi, ya'ni qoida tiklanadi.

## Narxi: nol

80 ta chiqarilgan `.duke` faylning **birortasi ham** map blokini ishlatmaydi. Tekshirildi. Ya'ni bu
printsipial jihatdan buzuvchi o'zgarish, amalda esa hech narsani buzmaydi.

Map komponenti bor uchta rekord:

| Rekord | Turi | Holati |
|---|---|---|
| `ActiveBody.Data.armor` | `Map<DamageType, Float>` | skalyar — yangi shaklga to'g'ridan-to'g'ri tushadi |
| `Upgrade.effects` | `Map<String, Float>` | skalyar — bir xil |
| `PanelSkin.pieces` | `Map<String, Piece>` | rekord qiymatli — **hozir umuman o'qib bo'lmaydi** |

Oxirgisi muhim: `Binder.map()` ichki bloklarni ochiqdan-ochiq rad etadi
(`'…' holds entries, not blocks`), ya'ni rekord qiymatli map hech qachon ma'lumotdan o'qilmagan.
`PanelSkin` Java'da quriladi. Demak bu ko'chirish emas, **yangi imkoniyat**.

## Engine'da nima o'zgaradi

**`Binder`**

1. `bind()` ichidagi ichki blok yo'li o'chadi — 100–110-qatorlar, `block.blocks()` bo'ylab yurib
   `Map` komponentini qidiradigan joy.
2. `map(Block, Type)` o'chadi. Uning o'rniga map `value()` ichida, `[ … ]` dan o'qiladi.
3. `value()` map turini bilishi kerak: `[k = v]` yozuvlarini kalit va qiymatga ajratib, ikkalasini
   komponentning generic argumentlari bo'yicha o'qiydi. Rekord qiymat uchun yozuv bloki bo'ladi.
4. Xato matnlari yangilanadi: `misplaced()` (133–157) eski shaklni tavsiflaydi, `map()` ning
   `holds entries, not blocks` xabari umuman kerak bo'lmay qoladi.

**`DukeText`**

`[` ni allaqachon ikki xil o'qiydi (148-qator): `itemsAreBlocks()` birinchi yozuvga qarab hal qiladi —
"tanasi bor so'z yoki `End`". Rekord qiymatli map yozuvi esa `kalit = So'z` keyin tana keyin `End`, ya'ni
**uchinchi shakl**. Aniqlagich shuni ham tanishi kerak.

Skalyar map (`[k = v, k = v]`) uchun o'zgarish kerak emas — u oddiy ro'yxat kabi o'qiladi va ajratish
`Binder` da bo'ladi. Bu yaxshi: parser turni bilmaydi va bilishi ham shart emas.

## Engine'dan tashqarida

| Kim | Nima |
|---|---|
| **duke-plugin** | completion, Inspector'ning map tahriri, highlighting — alohida sessiyada |
| **duke-generals** | `ArmorConverter` eski shaklni yozadi, bir qator o'zgaradi |
| **duke-dungeon** | hech narsa — map ishlatmaydi |

---

# 2. Vergul majburiy bo'ladi

Hozir `[ … ]` ichida ikki xil qoida ishlaydi: skalyar ro'yxat vergul bilan (`[a, b]`), bloklar ro'yxati
esa vergulsiz — `End` dan keyin to'g'ridan-to'g'ri keyingi blok boshlanadi. Bitta qavs, ikki qoida.

Endi vergul **yozuvlar orasida majburiy**, oxirgisidan keyin ixtiyoriy:

```
Modules = [
  ActiveBody
    MaxHealth = 480
  End,                  ← majburiy
  PhysicsBehavior
    Mass = 200
  End                   ← oxirgisi, ixtiyoriy
]
```

Bu birinchi o'zgarish bilan bog'liq: rekord qiymatli map yozuvlari `kalit = So'z … End` ko'rinishida
bo'ladi, va aynan o'sha yerda `End` dan keyin nima kelishini ko'z bilan ajratish qiyin. Vergul shuni hal
qiladi. Xato xabari ham aniqroq bo'ladi: tushib qolgan vergul — shikoyat, sukut bilan qabul qilinadigan
narsa emas.

## Narxi: nol emas

| | |
|---|---|
| Blokli ro'yxat ishlatadigan fayllar | **69 / 80** |
| Ulardagi `End` qatorlari | ~582 |

Bu birinchi o'zgarishdan tubdan farq qiladi: u hech narsaga tegmagan edi, bu esa deyarli hamma faylga
tegadi — dungeon, skirmish, kit effektlari.

Lekin ko'chirish **mexanik**, va uni parserning o'zi bajaradi: u tuzilmani allaqachon biladi, ya'ni
qaysi `End` dan keyin yozuv davom etishini va qaysinisi oxirgisi ekanini ayta oladi. Qo'lda tahrirlash
kerak emas.

Ko'chirish to'g'ri bo'lganini **mavjud testlar** isbotlaydi: engine'da 883 ta, dungeon'da o'ziniki. Agar
ko'chirish biror faylni buzsa, ular qizil bo'ladi.

## Tashqaridagilar uchun

Bu haqiqiy buzuvchi o'zgarish. Hozir engine'ga bog'langan odam deyarli yo'q (0.2.0 bir kun oldin
chiqdi), shuning uchun narxi eng past payt — lekin `CHANGELOG` da ochiq yozilishi va ko'chirish
buyrug'i ko'rsatilishi kerak.

---

# Tartib

1. **`Binder` + `DukeText`**, testlar bilan — uchala map turi (skalyar, rekord qiymatli, bo'sh) va
   vergul qoidasi
2. **Ko'chirish vositasi** — parserdan foydalanib 69 faylga vergul qo'yadi
3. **Testlar** — engine 883, dungeon o'ziniki, hammasi yashil bo'lishi shart
4. `CLAUDE.md` dagi "Data" chokini yangilash — format tavsifi o'sha yerda
5. 0.3.0 chiqarilganda `duke-generals` konvertorini o'tkazish

Plugin engine bilan bir vaqtda emas, keyin — u mavjud 0.2.0 ga qarshi ishlayveradi, faqat yangi
sintaksisni bilmaydi.
