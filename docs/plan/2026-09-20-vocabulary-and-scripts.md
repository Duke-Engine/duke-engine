# Reja — lug'at rekordlarini joyiga qo'yish va skriptlarni modul qilish

**Sana:** 2026-09-20 · **Holat:** tasdiqlangan, 0-bosqich boshlanmagan

Bu hujjat ikki ishni bog'laydi:

1. `.duke` lug'at rekordlarini (bugun hammasi `dungeon` da) ularni **o'qiydigan
   modulga** ko'chirish, ya'ni engine'dan o'yin yasash mumkin bo'lishi uchun.
2. Unit xatti-harakat **skriptlarini modul qilish** — bugungi sehrli satr
   o'rniga, modul tanlagandek tanlanadigan qilib.

Maqsad — [product-vision]: IntelliJ + plagin = kod yozmasdan RTS/RPG yasash
muharriri. Bu reja shu maqsadning poydevori: bugun o'yin yasamoqchi bo'lgan odam
`dungeon` modulidan nusxa olishdan boshqa yo'l topmaydi.

---

## Qabul qilingan qoida

> **Har bir rekord uni o'qiydigan modulga boradi.**

Bu yangi qoida emas — loyihada allaqachon shunday:

- `rts` da `RtsTemplate` bor — `Object` blokiga bog'langan rekord.
- `client3d` da `Effect`, `Layer`, `OrderMark`, `PanelLook`, `HitNumbers`,
  `MenuStyle` bor — beshta blok so'zi.

Shuning uchun **yangi kod moduli ochilmaydi**. `kit` bor holicha qoladi: faqat
data (27 effekt bloki + 98 zarracha rasmi), bitta ham `.java` yo'q.

### Bo'linish

| Rekord turi | Qayerga | Nega |
|---|---|---|
| Simulyatsiya lug'ati — `Unit`, `Monster`, `Hero`, `Prop`, `Projectile`, `Skill`, `Attribute`, `Progression`, `Combat`, `LootItem` | `rts` | Har bir RTS/RPG shu mexanizmlarga muhtoj |
| Ko'rinish lug'ati — `Sun`, `Fog`, `Tiles`, `Theme`, `Held`, `Portrait`, `UnitBar`, `SkillRing`, `HitFeel`, `Cursor`, `Skin`, `Moment`, `EffectBudget`, `Camera`, `Hud` | `client3d` | Ularni klient chizadi; `rts` ning ekrani yo'q |
| Janrsiz data — `Effect`, `Layer`, `Hundredths`, `Sound`, `AnimationSet`, `Game`, `World`, `Map` | `core` | Platformerda ham ovoz, animatsiya, manifest, dunyo, xarita bor |
| Kontent — effekt bloklari va zarracha rasmlari | `kit` | Hozirgidek, kodsiz |

Bir o'yin ko'proq narsa xohlasa, **o'sha blok so'zi bilan o'z rekordini yozadi
va u g'olib chiqadi** — blok so'zi → rekord moslikni o'yinning o'zi belgilaydi
(`Content.TYPES`). Shuning uchun `minDepth`, `maxPerRoom`, `rooms`/`links`/`boss`
kabi dungeon qoidalarini hech qayerdan olib tashlash shart emas: ular
dungeon'ning o'z rekordlarida qoladi.

### Inventar (2026-09-20 holati)

| | Rekord | Komponent |
|---|---|---|
| Blok so'ziga bog'langan | 32 | 346 |
| Ular ichidagi nested | 14 | 72 |
| Komponent tipi bo'lgan boshqalar | 4 | 58 |
| **Jami** | **50** | **476** |

Ustiga 3 enum: `LootKind`, `SkillEffect` (11 afsun), `Themes.WhenExhausted`.

Eng katta ikkitasi: `Hud` — **51 komponent**, `Skill` — **35 komponent** va 12 ta
hosil qiluvchi metod.

---

## Skriptlar bugun qanday ishlaydi (va nimasi noto'g'ri)

### Mexanizm

1. `game/src/main/java/uz/duke/game/script/UnitScript.java` — foydalanuvchi
   yozadigan base class. `onStart()` / `onUpdate()`, va himoyalangan
   yordamchilar (`moveTo`, `findNearestEnemy`, `health()`…). Har mantiqiy
   kadrda (30/sek) chaqiriladi.
2. `game/.../script/ScriptModule.java` — skriptni engine moduli sifatida
   yurgizadigan adapter. Uning ichida **nomlar reestri** bor:
   `registerScript(factory, "<nom>", supplier)`.
3. Data faylida:
   ```
   ScriptModule
     Name = BruteBrain
   End
   ```
4. O'yin ishga tushganda `dungeon/.../Dungeon.java:146` reestrni to'ldiradi:
   `"HeroBrain"` → `new HeroBrain(settings, orders)`, va **har bir maxluq turi
   uchun sikl ichida** `kind.brainTag()` → `new MonsterBrain(kind, settings)`.
   `brainTag()` = `name + "Brain"` (`content/MonsterKind.java:66`).

### Nimasi noto'g'ri

15 ta unit skript nomlaydi: 3 qahramon `HeroBrain` deydi, 12 maxluq esa
`BruteBrain`, `ChampionBrain`, `ReaperBrain`… deydi — va **o'n ikkitasi ham
bitta `MonsterBrain` klassiga boradi**. `BruteBrain.java` degan fayl yo'q.

Oqibatlari:

- Data faylidagi satrning birorta klass bilan bog'liqligi **yo'q**. Plagin uni
  to'ldira olmaydi, Ctrl+Click ocholmaydi, xato yozilsa aytolmaydi.
- Xato faqat o'yin ishga tushganda chiqadi:
  `no script is registered as 'BrutBrain'`.
- Qolgan **hamma** modul `.duke` da o'z klassi nomi bilan ataladi
  (`MoveUpdate`, `ExperienceModule`). `ScriptModule` — yagona istisno:
  bloki bitta, ichidagi `Name` esa sehrli satr.
- `<nom>Brain` kelishuvi hech qayerda majburlanmagan — u faqat `brainTag()`
  ichidagi qator.

### Qanday bo'lishi kerak

Skript ham **o'z klassi nomi bilan atalgan modul** bo'lsin — `MoveUpdate` kabi:

```
Modules = [
  HeroBrain
  End
]
```

va maxluqlar uchun:

```
Modules = [
  MonsterBrain
  End
]
```

`MonsterBrain` o'z turini (`MonsterKind`) **o'zi turgan unitning template'idan**
o'qiydi (`GameObject.getTemplate()`), shuning uchun `brainTag()` ham, 12 ta
turli satr ham, ro'yxatga oluvchi sikl ham butunlay yo'qoladi.

`UnitScript` base class bo'lib qoladi — u foydalanuvchiga qulay API
(`moveTo`, `findNearestEnemy`) va u o'zgarmaydi. `ScriptModule` esa reestrsiz,
sof adapter bo'ladi.

Yutuq: plagin skript nomini boshqa modullar qatori to'ldiradi, Ctrl+Click
`HeroBrain.java` ni ochadi, noto'g'ri nom **muharrirda** qizil chiziq bo'ladi.

---

## Bosqichlar

Har bir bosqich mustaqil tugaydi: `./gradlew build` va
`cd duke-plugin && ./gradlew test` yashil bo'lishi kerak.

### 0 — `Effect` + `Layer` → `core`

**Nega birinchi:** `Skill`, `Monster`, `Projectile`, `Moment` va
`Theme.ThemeMonster` da `@Link(Effect.class)` turibdi. Ular `rts` ga ko'chadi,
`rts` esa `client3d` ni **ko'ra olmaydi**. Shunisiz butun reja to'xtaydi.

**Xavf: yo'q.** Ikkala rekord ham faqat `java.util` ni import qiladi — jME
ularda umuman yo'q.

**Fayllar:** `client3d/.../Effect.java`, `client3d/.../Layer.java` →
`core/src/main/java/uz/duke/core/effect/`. Import'lar: `client3d` ichida
effektni chizadigan joylar, `dungeon/content/DungeonSettings.java`, `kit`
data'siga tegilmaydi (blok so'zi o'zgarmaydi).

**Tugagani:** `grep -rn "client3d.Effect" ` bo'sh; kit effektlari avvalgidek
o'qiladi; `DungeonEffectLayerTest` yashil.

### 1 — Janrsiz rekordlar → `core`

`Sound`, `AnimationSet`, `Game`, `World`, `Hundredths`. (`Hundredths` — aniq
fixed-point qiymat tipi, determinizm uchun; u `kit` ga emas, `core` ga.)

**Xavf: past.** Bularda dungeon so'zi ham, hosil qiluvchi metod ham yo'q.
Inventar bo'yicha faqat `content/Content.java` va `content/DungeonSettings.java`
tuzatiladi.

### 2 — Ko'rinish rekordlari → `client3d`

`Sun`, `Fog`, `Tiles`, `Theme`, `Held`, `PortraitArt`→`Portrait`, `UnitBar`,
`SkillRing`, `HitFeel`, `Cursor`, `Skin`, `Moment`, `EffectBudget`, `Camera`.

**Xavf: past, lekin ko'p fayl.** `client3d` da allaqachon `PanelLook`,
`MenuStyle`, `HitNumbers`, `OrderMark` bor — naqsh tayyor.

**Bu bosqichda qayta nomlanadi:**
- `Tiles`: `stairs`, `corner` — xona-koridor to'riga tayanadi → dungeon'da qoladi
- `UnitBar`: `bossRim`, `bossNameSize` → dungeon'da qoladi
- `PortraitArt`: 5 ta qotirilgan kayfiyat (`calm/fight/hurt/dead/levelUp`) →
  moment→klip xaritasi
- `Theme`: `wallFillsRock`, `rockFace`, `stairs` → dungeon'da qoladi

`Hud` — **alohida ish**. 51 komponentning yarmi bitta o'yinning menyu oqimi
(`endlessBlurb`, `chooseStageHint`, `depthWord`). `client3d` ga faqat har bir
o'yinga kerak bo'ladigan minimum ketadi (buyruq so'zlari, stat so'zlari,
ikonkalar); qolgani dungeon'ning o'z `Hud` rekordida qoladi.

### 3 — `interface Unit` + `Monster`/`Hero` → `rts`

Java'da rekord `extends` qila olmaydi, shuning uchun "base class" =
**interfeys**, aynan `Solid`/`Sighted`/`Classified`/`Titled` kabi:

```java
public interface Unit extends Solid, Sighted, Classified, Titled {
    float closeDistance();
    String model();
    // … har bir unitda bo'lishi kerak bo'lgan ~20 ta
}
```

`Monster` (34 komponent) va `Hero` (27) da **~20 komponent aynan bir xil** —
interfeys shularni e'lon qiladi. `rts` da ikkita rekord: `Monster` va `Hero`,
har biri `Unit` ni implement qiladi va boshqa o'yinlarga shablon bo'ladi.

**Dungeon'da qoladi:** `minDepth`, `maxPerRoom` (Monster), `primary`,
`attributes` (Hero) — chuqurlik va atribut qoidalari.

**Xavf: loyihalash.** `Monster.kind()`, `Monster.look()`, `Hero.look()` hosil
qiluvchi metodlar `MonsterKind`, `MonsterLook`, `HeroLook` ga boradi — ular
`dungeon` ning runtime tiplari va `.duke` lug'atiga kirmaydi. Ko'chirishdan
oldin shu uchta tipning taqdirini hal qilish kerak.

### 4 — `Skill`, `Attribute`, `Progression` → `rts`

**Eng katta bitta ish.** `Skill` — 35 komponent, 11 ta nomlangan afsunning
hammasi bitta rekordga yassilangan (`heal`, `summonCount`, `boostPercent`…), va
12 ta hosil qiluvchi metod (`damageAt(level)`, `cooldownAt(level)`…).

**Tavsiya:** yassi 35 maydonni effekt bo'yicha bo'lish — umumiy qism (nom, kalit,
ikonka, mana, cooldown, rank) + effektga xos blok. Ya'ni `SkillEffect` enum'i
o'rniga har bir effekt o'z rekordi.

`Attribute` da `healthPerPoint`/`speedPerPoint`/`manaPerPoint` bitta ochko
nimani sotib olishini **qotirib** yozadi — bu qoida, mexanizm emas. `rts` ga
nomlangan bonuslar jadvali sifatida ketadi, nimani sotib olishini o'yin aytadi.

### 5 — Skriptlar modul bo'ladi

Yuqoridagi "Qanday bo'lishi kerak" bo'limi.

**Ishlar:**
1. `ScriptModule` dan nomlar reestrini (`registerScript`, `Scripts`,
   `ScriptModule.Data`) olib tashlash; u sof adapter bo'lib qoladi.
2. `HeroBrain` va `MonsterBrain` ga `Data` rekordi berish va ularni
   `ModuleFactory` ga o'z klassi bilan ro'yxatdan o'tkazish — `MoveUpdate` kabi.
3. `MonsterBrain` o'z `MonsterKind` ini `getOwner().getTemplate()` dan olsin.
   **Tekshirish kerak:** template `Monster` rekordimi yoki `RtsTemplate` mi —
   `Dungeon.java:205` dagi ro'yxatga olishga qarang.
4. `MonsterKind.brainTag()` va `Dungeon.java:146-153` dagi sikl o'chadi.
5. 15 ta data faylida `ScriptModule / Name = XBrain / End` →
   `HeroBrain / End` yoki `MonsterBrain / End`.
6. `Content.java:90` dagi modul ro'yxatidan `ScriptModule.Data` chiqib,
   `HeroBrain.Data`, `MonsterBrain.Data` kiradi.

**Tugagani:** `grep -rn "Brain" dungeon/src/main/resources/data` faqat
`HeroBrain` va `MonsterBrain` ni ko'rsatadi; plaginda skript nomi
to'ldiriladi va Ctrl+Click klassni ochadi; `grep -rn "brainTag" ` bo'sh.

**Xavf: past.** Mexanizm allaqachon bor — faqat bitta indirection olib
tashlanadi.

### 6 — Dungeon'ni shablonlar ustida qayta qurish

Sen so'raganing. Lekin **halol ogohlantirish:** shablonlar dungeon'dan olingan,
shuning uchun dungeon ularga mos tushishi hech narsani isbotlamaydi.

**Shuning uchun juftlab qilinsin:** `rts` da allaqachon iqtisod, ishlab
chiqarish va jang bor — 50-100 qatorlik data bilan kichkina skirmish yasash
("unit, zavod, resurs"). Kit/`rts` shablonlari qayerda yorilishini dungeon'ning
muvaffaqiyatli qayta qurilishidan ko'ra ko'proq aytadi.

### 7 — Plaginda New Project

Engine'dan foydalanmoqchi bo'lgan odam **File → New → Duke Game** bilan tavsiya
etilgan strukturani oladi: `data/` (units, world, maps ro'yxati bilan
`game.duke`), `maps/starter/`, `build.gradle.kts`, bitta `Main`. Ko'p engine va
framework'larda bor narsa.

**0-5 tugamaguncha ma'nosiz:** sehrgar shablonlardan nusxa oladi, shablonlar
esa hali dungeon ichida.

### 8 — Export Game

`dungeon/build.gradle.kts` da `application` plagini va `installDist` bor.
Plaginda tugma yo'q — "o'yinni odamlarga berish" hozir Gradle bilimini talab
qiladi.

---

## Ochiq savollar va qarorlar

| Savol | Holat |
|---|---|
| `Effect`/`Layer` → `core` | ✅ tasdiqlangan |
| `Unit` = interfeys, `Monster`/`Hero` rekordlari `rts` da | ✅ tasdiqlangan |
| `Skill`, `Attribute` → `rts` | ✅ tasdiqlangan |
| `Hud` → `client3d` (`rts` emas) | ✅ tasdiqlangan |
| `kit` kodsiz qoladi | ✅ tasdiqlangan |
| `MonsterKind`/`MonsterLook`/`HeroLook` taqdiri | ❌ 3-bosqichdan oldin hal qilinsin |
| `Skill` ni effekt bo'yicha bo'lish shakli | ❌ 4-bosqichdan oldin |
| `generals` moduli — CLAUDE.md da bor, `settings.gradle.kts` da yo'q | ❌ reja yoki eskirgan matn? |

## To'sqinlik

`dungeon/src/main/resources/maps/deep/deep.map` muharrirda tahrirlangan va
buzilgan: (67,28), (68,28), (69,28) kataklariga tosh bo'yalgan — bu room 0 ning
markazi, shuning uchun unga yetib borib bo'lmaydi. **6 ta test yiqilyapti**
(`StageChoiceTest` ×3, `MapWriterTest`, `StageDifficultyTest`, `StagePlayTest`).
Shu uchta katak pol qilinsa yashil bo'ladi. Bu muallifning o'z tahriri, shuning
uchun avtomatik qaytarilmaydi.

## Tugagan ishlar

- `studio` moduli o'chirildi (20 fayl, 3906 qator) — uning ishini plagin
  bajaradi. Hujjatlardan ham tozalandi.
- CLAUDE.md: «Before claiming done» dagi `:sandbox:run` → plagin testi.
- STATUS.md 2026-09-14 holati ekani boshida ogohlantirish bilan belgilandi.
