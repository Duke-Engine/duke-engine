# Reja — lug'at rekordlarini joyiga qo'yish va skriptlarni modul qilish

**Sana:** 2026-09-20 · **Holat:** 0, 1, 2a, 3, 5, 6, 7 BAJARILDI

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
- `client3d` da `OrderMark`, `PanelLook`, `HitNumbers`, `MenuStyle` bor — to'rtta
  blok so'zi. (`Effect` va `Layer` ham o'sha yerda edi, 0-bosqichda `core` ga ketdi.)

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

1. `game/src/main/java/uz/dukeengine/game/script/UnitScript.java` — foydalanuvchi
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

### 0 — `Effect` + `Layer` → `core` ✅ BAJARILDI (2026-09-20)

**Nega birinchi:** `Skill`, `Monster`, `Projectile`, `Moment` va
`Theme.ThemeMonster` da `@Link(Effect.class)` turibdi. Ular `rts` ga ko'chadi,
`rts` esa `client3d` ni **ko'ra olmaydi**. Shunisiz butun reja to'xtaydi.

**Xavf: yo'q.** Ikkala rekord ham faqat `java.util` ni import qiladi — jME
ularda umuman yo'q.

**Fayllar:** `client3d/.../Effect.java`, `client3d/.../Layer.java` →
`core/src/main/java/uz/dukeengine/core/effect/`. Import'lar: `client3d` ichida
effektni chizadigan joylar, `dungeon/content/DungeonSettings.java`, `kit`
data'siga tegilmaydi (blok so'zi o'zgarmaydi).

**Bajarildi:** ikkala rekord `core/src/main/java/uz/dukeengine/core/content/` da (1-bosqichda `core.effect` dan shu yerga yig'ildi). `client3d`
umuman tegilmadi — u bu rekordlarni ishlatmas ekan, chizish uchun o'zining
`EffectLayer`/`EffectRecipe` tiplari bor. 9 ta dungeon faylida import tuzatildi,
plagin testidagi bitta tasdiq yangilandi. Build: `deep.map` dan boshqa yangi
nosozlik yo'q; plagin testlari yashil.

### 1 — Janrsiz rekordlar → `core` ✅ BAJARILDI (2026-09-20)

`Sound`, `AnimationSet`, `Game`, `World`, `Hundredths`. (`Hundredths` — aniq
fixed-point qiymat tipi, determinizm uchun; u `kit` ga emas, `core` ga.)

**Xavf: past.** Bularda dungeon so'zi ham, hosil qiluvchi metod ham yo'q.

**Bajarildi:** `Sound`, `AnimationSet`, `Game`, `Effect`, `Layer` →
`uz.dukeengine.core.content` (bitta paket, to'rtta emas). `Hundredths` →
`uz.dukeengine.core.math`, chunki u determinizm uchun fixed-point qiymat tipi.

**`World` ko'chmadi — va ko'chmasligi kerak.** Ikki sabab: `core` da allaqachon
`uz.dukeengine.core.thing.World` bor (simulyatsiyaning dunyosi), va CLAUDE.md ning o'z
seam jadvali `World` rekordini **o'yinning o'ziniki** deb belgilaydi — `core`
`WorldTemplate` va `Layered` interfeyslarini beradi, xolos.

**Yo'l-yo'lakay topilgan xato:** plagindagi 3D ko'rinish qavat balandligini
"birinchi uchragan `Layered` blok" deb olardi. `StaticMap` ham `Layered` bo'lgach
(bugun qo'shildi), xarita loyihadagi boshqa xaritaning balandligini olib qolardi —
fayl tartibiga qarab goh to'g'ri, goh noto'g'ri. Endi `MapScene.levelHeight`
engine qoidasini takrorlaydi: xaritaning o'zi aytgani, aytmasa — dunyoniki, va
"dunyo" deb `` i yo'q `Layered` blok tushuniladi.

### 2a — Ko'rinish rekordlari → `client3d` ✅ BAJARILDI (2026-09-20)

`Sun`, `Fog`, `Tiles`, `Theme`, `Held`, `PortraitArt`→`Portrait`, `UnitBar`,
`SkillRing`, `HitFeel`, `Cursor`, `Skin`, `Moment`, `EffectBudget`, `Camera`.

**Xavf: past, lekin ko'p fayl.** `client3d` da allaqachon `PanelLook`,
`MenuStyle`, `HitNumbers`, `OrderMark` bor — naqsh tayyor.

**Ko'chdi (9 ta, hech qanday o'zgarishsiz):** `Sun`, `Camera`, `HitFeel`,
`EffectBudget`, `Cursor`, `Skin`, `Moment`, `SkillRing`, `Held`. Dungeon ularni
aynan shu holida ishlatadi, shuning uchun bu sof ko'chirish.

### 2b — Bo'linishi kerak bo'lganlar ⏸ SENING QARORINGNI KUTADI

`Fog`, `Tiles`, `Theme`, `UnitBar`, `PortraitArt`, `Hud` — bularni ko'chirish
**sof ko'chirish emas**, chunki har birida dungeon qoidasi bor, va sen
"dungeon'ga xos maydon qolmasin" deding.

Muammo shundaki, generic nusxani `client3d` ga qo'ysak, dungeon o'z to'liq
versiyasini ishlatishda davom etadi — ya'ni `client3d` **hech kim ishlatmaydigan
rekord** olib yuradi. CLAUDE.md buni taqiqlaydi ("No speculative flexibility …
Delete dead code").

Shuning uchun ular ikkinchi o'yin paydo bo'lguncha dungeon'da qoladi — yoki sen
"baribir bo'lsin" desang, o'sha paytda bo'linadi. Ayni shu savol 3- va
4-bosqichlarda ham turibdi.

**Ko'chmaganlarda nima dungeon'niki:**
- `Theme`: `wallFillsRock`, `rockFace`, `stairs`
- `Tiles`: `stairs`, `corner`
- `UnitBar`: `bossRim`, `bossNameSize`
- `PortraitArt`: qotirilgan 5 kayfiyat (`calm/fight/hurt/dead/levelUp`)
- `Hud`: 51 komponentning yarmi (`endlessBlurb`, `chooseStageHint`, `depthWord`…)
- `Fog`: `client3d` da allaqachon shu nomli klass bor — nom to'qnashuvi

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

### 5 — Skriptlar modul bo'ladi ✅ BAJARILDI (2026-09-20)

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

**Bajarildi.** `ScriptModule` dagi nomlar reestri o'chdi; o'rniga tipli bitta
satr: `ScriptModule.registerScript(factory, HeroBrain.Data.class, …)`. Ikkala
brain ham `@ModuleGroup(SCRIPT)` va `record Data()` oldi, 15 ta data fayli
`HeroBrain` / `MonsterBrain` deb yozildi, `brainTag()` o'chdi.

**Bitta muhim tafsilot:** brain o'z `MonsterKind` ini template'dan emas,
`settings.monster(template.name())` dan oladi. Sababi — `Dungeon.world(map,
settings, creaturesIni)` ataylab qo'yilgan seam: chaqiruvchi bir xil mavjudot
fayli ustidan qayta sozlangan settings bera oladi, va 8 ta test aynan shunga
tayanadi. Template'dan o'qisak, o'sha seam yo'qolar edi.

**Natija:** plagin endi `MonsterBrain` so'zini to'ldiradi, Ctrl+Click
`MonsterBrain.java` ni ochadi, noto'g'ri yozilsa muharrirda qizil chiziq —
`DukeEngineSourcesTest` shuni tekshiradi.

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

---

## 6-bosqich — ikkinchi o'yin: `skirmish` ✅ BIRINCHI QADAM BAJARILDI (2026-09-20)

Yangi modul: **`skirmish`** — kichik RTS. Dungeon'dan nusxa olinmadi, noldan
yozildi, va **atayin butunlay boshqa o'yin**: qahramon yo'q, qavat yo'q, skill,
mana, loot, daraja yo'q. O'rniga pul, narx, ishlab chiqarish vaqti, ochiq maydon
va guruh jangi.

Hajmi: **385 qator Java + 141 qator data**. Ikkala testi yashil.

### Topilgan dalillar

**1. `RtsTemplate` da birorta ham ko'rinish maydoni yo'q.**
Uning 8 komponenti: `name`, `displayName`, `kindOf`, `visionRange`, `geometry`,
`modules`, `buildCost`, `buildTime`. Model, tekstura, animatsiya — hech biri yo'q.
Shuning uchun **ikkala o'yin ham mustaqil ravishda o'z unit rekordini o'ylab
topdi**. Ikki o'yin bir xil narsani ixtiro qilgan bo'lsa, bu yetishmayotgan
seam'ning ta'rifi.

**2. Ikki rekordning kesishmasi — `Unit` interfeysi uchun aniq ro'yxat.**
`skirmish.Unit` 17 komponent, `dungeon.Monster` 34. Ikkalasida ham bor **14 ta**:

`name` · `displayName` · `kindOf` · `visionRange` · `geometry` · `modules` ·
`model` · `modelScale` · `tint` · `facing` · `animations` · `idle` · `walk` ·
`attack`

Faqat skirmish'da: `buildCost`, `buildTime` (dungeon'da mavjudot sotib olinmaydi).
Faqat dungeon'da: sezish/quvish radiuslari, portret, qo'ldagi qurol, skill'lar,
`minDepth`, `maxPerRoom`, `weight`, `colour`, `hurt` klipi.

**Xulosa:** `Unit` interfeysi shu 14 tani e'lon qilsin. `buildCost`/`buildTime`
allaqachon `Buildable` da — ya'ni RTS uniti `Unit` + `Buildable`.

**3. Template loader o'yinni blok ajratishga majbur qiladi.**
Loader matn oladi va o'zi bilmagan blokni rad etadi, shuning uchun o'yin unit
bloklarini qolganidan ajratishi shart. Dungeon buni **so'zlar ro'yxati** bilan,
skirmish **papka** bilan qildi. Ikkalasi ham o'yinning o'z kashfiyoti — demak
bu ham seam bo'lishi kerak (yoki `Game` manifesti qaysi fayllar template
ekanini aytsin, yoki loader tanimagan so'zni o'tkazib yuborsin).

**4. `rts` da "nishonga yaqinlashish" yo'q.**
`AttackObject` buyrug'i faqat `weapon.attack(target)` qiladi. `WeaponUpdate`
masofadagi dushmanni **o'zi topib otadi**, lekin masofadan tashqaridagiga
**yurmaydi**. Ya'ni "hujum qil" deb buyurilgan unit joyida turaveradi.

Har bir RTS'da bu bor (Generals'da ham, WC3'da ham). Dungeon buni `MonsterBrain`
scripti bilan qoplaydi — ya'ni **mexanizm o'yin tomonida yashiringan**.
CLAUDE.md ning o'z mezoni bo'yicha bu `rts` ga tegishli.

**5. Yassi o'yin `Layered` ga umuman tegmaydi.**
`skirmish.Field` da faqat `name` bor. Qavat balandligi tushunchasi **nolga
teng emas, umuman yo'q**. Seam haqiqatan ixtiyoriy ekan — bu yaxshi xabar.

**6. 2a-bosqich to'g'ri tanlagan.**
`Sun` va `Camera` ni ikkinchi o'yin **hech qanday o'zgarishsiz** ishlatdi. Bu
birinchi mustaqil tasdiq.

**7. Data rekordi → runtime tipi konversiyasi har bir o'yinning bo'ynida.**
`Sun` → `Sunlight`, `Effect` → `EffectRecipe`, `Unit` → `Visuals.UnitVisual`.
Uchalasi ham bir xil naqsh, va uchalasini ham har bir o'yin qo'lda yozadi.
`Main.look()` — skirmish'dagi shu boilerplate, va u `Unit` seam'i paydo
bo'lgach butunlay yo'qolishi kerak.

### Keyingi qadam

Hozirgi skirmish'da **iqtisod va ishlab chiqarish hali ulanmagan** —
`Barracks` da `ProductionUpdate` bor, lekin uni haydaydigan buyruq yo'q. Uni
ulash 3- va 4-bosqichlarga yana dalil beradi (pul, navbat, rally nuqtasi —
hammasi `rts` da bor).

---

## 3-bosqich — birinchi seam: `Drawn` ✅ BAJARILDI (2026-09-20)

Ikkinchi o'yin dalil berdi, shundan keyin qaror aniq bo'ldi. Va u men kutgan
"katta `Unit` rekordi" emas ekan.

### Nega `Unit` emas

Kesishmadagi 15 komponentning **6 tasi allaqachon qoplangan**:

| Komponent | Qaysi interfeys |
|---|---|
| `name`, `modules` | `ThingTemplate` |
| `displayName` | `Titled` |
| `kindOf` | `Classified` |
| `visionRange` | `Sighted` |
| `geometry` | `Solid` |

Ya'ni **haqiqatan yetishmayotgani 9 ta ko'rinish maydoni**: `model`,
`modelScale`, `tint`, `facing`, `animations`, `idle`, `walk`, `attack`, `death`.

Demak yechim — semiz `Unit` rekordi emas, **yana bitta "template'da shu narsa
bormi" interfeysi**, aynan `Solid`/`Sighted`/`Classified`/`Titled` naqshida.

### `core.thing.Drawn`

```java
public interface Drawn extends ThingTemplate {
    String model();                                   // yagona majburiy
    default float modelScale() { return 1f; }
    default int tint() { return 0xFFFFFF; }
    default float facing() { return 0f; }
    default String animations() { return null; }
    default String idle() / walk() / attack() / death() { return null; }
    default boolean hasModel() { ... }
}
```

**Modeldan boshqa hammasi `default`.** Shuning uchun rekord uni **qanday
komponentlari bor bo'lsa, shunday** implement qiladi: `Tint` i yo'q blok
bo'yalmasdan chiziladi, `Walk` i yo'q blok yurganda qimirlamaydi. Rekord har bir
metodni **o'z komponenti bilan** qoplaydi — bitta ham qo'lda yozilgan metod yo'q.

### Tasdiq

`Monster`, `Hero` va `skirmish.Unit` ga faqat `implements … Drawn` qo'shildi —
**uchtasining ham birorta komponenti o'zgarmadi**. Seam ikkala o'yinga ham
ularni o'zgartirmasdan tushdi. Kuchliroq tasdiq bo'lishi qiyin.

### Ikkinchi yarmi: `Visuals.draw(Drawn, AnimationSet)`

`client3d` da tayyor bog'lovchi: model, o'lcham, tint, facing, klip'lar va
bog'langan to'plamning kutubxona fayllari — hammasi bir chaqiruvda. Klipni blok
aytmasa, bog'langan to'plamnikidan olinadi.

`skirmish/Main.java` dan **21 qator o'chdi** va bitta satrga aylandi:

```java
case Unit unit -> visuals.draw(unit, sets.get(unit.animations()));
```

### Uchinchi yarmi: `RtsTemplate` endi `Drawn`

`Object` bloki ham ko'rinish maydonlarini oldi. Ya'ni **yangi o'yin o'z rekordini
yozmasdan** `Object` bloki bilan modelli unit ola oladi:

```
Object
  Name = Tank
  Model = models/units/tank.glb
  BuildCost = 300
End
```

Bu aynan "developer project yaratgandan keyin nima qilsam ekan deb qolmasin"
degan maqsadga ishlaydi.

### Qoldi (keyingi seam'lar, dalili bor)

1. **Nishonga yaqinlashish** — `rts` da yo'q; har bir RTS'da bor. Eng katta
   bo'shliq.
2. **Yig'ish sikli namunaviy** — `HarvestUpdate` eng yaqin uyumni butun xarita
   bo'ylab topadi, unga **yurmaydi**, ombor ham yo'q. Javadoc'ining o'zi buni
   "keyingi yaxshilash" deb ataydi. `SkirmishTest` buni qulflab qo'ydi.
3. **Template loader blok ajratishni o'yinga yuklaydi.**

---

## Ikkinchi va uchinchi seam: `PursueUpdate` va yuradigan `HarvestUpdate` ✅ BAJARILDI (2026-09-20)

Skirmish topgan qolgan ikki bo'shliq yopildi. Ikkalasi ham **mexanizm** —
qaroriga o'yin javob beradi.

### `rts.module.PursueUpdate` — nishonga yaqinlashish

`WeaponUpdate` ning o'z izohi "harakat yaqinlashtirguncha kutadi" derdi, lekin
RTS kutubxonasida kutiladigan harakat **yo'q edi**: masofadan tashqaridagi
narsaga yuborilgan unit joyida turardi.

**Ixtiyoriy modul, chunki quvish — qaror.** Minora, bunker, yig'ilishi kerak
bo'lgan qamal quroli hech narsani ta'qib qilmaydi. Template moduli bor-yo'qligi
bilan javob beradi; qanchalik uzoqqa borishini esa blokdagi raqam aytadi:

```
PursueUpdate
  RepathFrames = 10
  GiveUpBeyond = 200     ; 0 = qancha kerak bo'lsa
End
```

Nishon masofaga kirganda **to'xtaydi** — yurishda ota olmaydigan unit aks holda
umuman otmasdi.

`WeaponUpdate` ga `isInRange(victim)` qo'shildi: masofa qurolniki, uni steer
qiladigan kod ikkinchi nusxasini saqlamasligi kerak.

### Yuradigan `HarvestUpdate` va `SupplyDepot`

Avval: butun xarita bo'ylab eng yaqin uyumni topib, **qimirlamasdan** pul
yozardi. Endi sikl: uyumga bor → yukla → o'z tomonining eng yaqin omboriga
qaytar → bankka qo'y.

```
HarvestUpdate
  LoadPerTrip = 25
  FramesPerTrip = 30
  SearchRange = 400      ; 0 = butun xarita
End
```

`SupplyDepot` — bitta belgi moduli: qaysi binosi ombor ekanini o'yin aytadi.
**Omborsiz o'yin turgan joyida bankka qo'yadi** va oyoqsiz yig'uvchi ham
avvalgidek ishlaydi — `rts` ning eski `HarvestTest` i **bir harf o'zgarmasdan**
o'tdi (faza o'tishlari bir kadr ichida zanjirlanadi, shuning uchun
`FramesPerTrip` hamon faqat yuklash vaqti).

### Yo'l-yo'lakay: `World.standingNextTo` va `World.cellSize`

Ikkala modul ham bir xil tuzoqqa tushdi va u **engine darajasidagi kamchilik**
edi: qimirlamaydigan narsa to'siq qatlamiga kiritiladi, shuning uchun uning
o'z koordinatasiga **yo'l topilmaydi** — yuborilgan unit bitta ham qadam
tashlamaydi. Uyumga yuborilgan ishchi shunday qotib qolgan edi.

`World` ga ikkita default qo'shildi:

- `cellSize()` — `GameLogic` uni path grid'dan qaytaradi;
- `standingNextTo(who, what)` — **butun bitta katak** berib turadigan nuqta;
  undan kam bo'lsa yuboriladigan katak hamon narsaning o'z izidan chiqmaydi;
- `isBeside(who, what)` — "ishlagani yetarlicha yaqinmi": bir katak ichida.

Bu ikkinchi o'yin bo'lmasa **hech qachon topilmas edi**: dungeon'da nishon
har doim harakatlanuvchi maxluq, ya'ni hech qachon to'siq qatlamida emas.

---

## 7-bosqich — plaginda New Project ✅ BAJARILDI (2026-09-20)

**File → New → Project → Duke Game.** Sehrgar ikkita narsa so'raydi: o'yin nomi
va duke-engine checkout'ining papkasi.

### Nima yoziladi

```
settings.gradle.kts      includeBuild(<engine>)  ← nashr qilinmagani uchun
build.gradle.kts         uz.duke:client3d + uz.duke:kit, application, JUnit
README.md                "birinchi nimani o'zgartirish kerak" ro'yxati bilan
src/main/java/…/Main.java      3D klientni ochadi (~50 qator)
src/main/java/…/Content.java   fayllarni o'qiydi (~80 qator)
src/main/resources/data/game.duke
src/main/resources/data/units/{scout,turret}.duke
src/main/resources/data/world/look.duke
src/test/java/…/GameLoadsTest.java   headless yuklash testi
```

### Uchta qaror

**1. Modelsiz.** Yangi loyihada san'at yo'q, va engine `Model` i yo'q
template'ni o'z `Geometry` si bilan chizadi. Shuning uchun birinchi ko'radigan
narsang — **o'zingning o'yining**, boshqa birovning san'ati emas. Model qo'shish
bitta satr bo'lib qoladi. Plagin jariga binar asset ham tushmaydi.

**2. `Object` bloki, o'z rekordisiz.** `RtsTemplate` endi `Drawn` bo'lgani
uchun yangi o'yin birorta Java rekordi yozmasdan modelli unit ola oladi.
`Content.TYPES` da faqat engine so'zlari turibdi.

**3. Loyiha o'z testi bilan keladi.** Birinchi o'rgangan narsasi — bu o'yin
testlanadi, va o'sha test oyna ochmaydi.

### Tekshiruv

Shablon — sof matn generatori, shuning uchun **testlanadi**: manifest aynan
yozilgan fayllarni sanaydimi, `Main` aynan e'lon qilingan unitlarni
spawn qiladimi, `includeBuild` faqat checkout berilganda chiqadimi.

Undan ham muhimi: generatsiya qilingan loyiha vaqtinchalik papkaga yozilib,
haqiqiy Gradle bilan qurildi — **`compileJava` ham, o'z `test` i ham yashil**.
Ya'ni sehrgar yozgan misol kod ishlaydi, faqat ko'rinmaydi emas.

### Qoldi

Engine nashr qilinmaguncha sehrgar checkout so'rashda davom etadi. Nashr
qilingach, `includeBuild` satri o'chadi va boshqa hech narsa o'zgarmaydi —
`build.gradle.kts` dagi bog'liqliklar allaqachon oddiy koordinatalar.

---

## `kit` qayerda turishi kerak — qaror (2026-09-20)

Savol: kit'dagi effekt fayllarini `core` ga yoki `client3d` ga ko'chirsakmi,
chunki effektlar har bir o'yinga kerak?

**Javob: yo'q — lekin savol ortidagi muammo haqiqiy edi va u tuzatildi.**

### Nega ko'chirilmadi

Ikki xil narsani ajratish kerak:

| Narsa | Qayerda | Nega |
|---|---|---|
| `Effect` / `Layer` **rekordlari** (lug'at) | `core.content` ✅ allaqachon | Har qanday o'yin effekt nomlay oladi |
| **27 effekt bloki + 98 zarracha rasmi** (kontent) | `kit` | Bu birovning san'ati va birovning raqamlari |

CLAUDE.md ning o'z qoidasi: *"Models, animations, textures… belong to the game,
not to the engine: `core`, `rts` and `game` ship none, and `client3d` ships only
the shader it draws terrain with."*

Amaliy sabablar:

- **3.1 MB.** `core` ga qo'ysak, 2D o'yin ham, headless server ham, test
  harness ham hech qachon chizmaydigan zarracha rasmlarini ko'tarib yuradi.
- **`client3d`** bugun bitta asset tashiydi — relyef shaderi. Unga san'at
  paketini solsak, klientni almashtirganda Kenney'ning zarralari ham ergashadi.
- **Litsenziya.** Kit o'z `License.txt` i va `CREDITS.md` dagi o'z bo'limi bilan
  bitta joyda turibdi.

### Haqiqiy muammo va uning yechimi

Savolning asosi to'g'ri edi: **yangi o'yin effektsiz qolardi.** Ikki sabab:

1. Sehrgar `uz.duke:kit` ga bog'lanardi, lekin `game.duke` da kit'ning birorta
   fayli sanalmasdi.
2. Undan ham yomoni — **`RtsTemplate` ga effekt biriktirib bo'lmasdi.**
   `Drawn` da `effect()` yo'q edi, ya'ni o'z rekordini yozmagan o'yin effekt
   kiya olmasdi.

Ikkalasi tuzatildi:

- `Drawn.effect()` qo'shildi (default null) va `RtsTemplate` ga
  `@Link(Effect.class) String effect` komponenti; `Visuals.draw` uni bog'laydi.
  **Dungeon'ning `Monster` i allaqachon `effect` komponentiga ega edi** — ya'ni
  u yangi seam'ni bir harf o'zgarmasdan qanoatlantirdi.
- Sehrgar `game.duke` ga ikkita kit effektini yozadi va `Scout` ga
  `Effect = Focus` beradi.

Tekshirildi: generatsiya qilingan loyiha yana vaqtinchalik papkaga yozilib,
haqiqiy Gradle bilan qurildi — kompilyatsiya ham, o'z testi ham yashil, ya'ni
kit fayllari jardan o'qiladi va `Effect = Focus` bog'lanadi.

---

## 0.2.0 ga tayyorgarlik (2026-09-20)

### Nashr

`groupId = uz.duke-engine` — `duke-engine.uz` domeniga mos, central.sonatype.com
da tasdiqlangan namespace. Versiya **0.2.0**.

Nashr qilinadi: `core`, `rts`, `game`, `client3d`, `kit`.
**Nashr qilinmaydi:** `dungeon`, `skirmish` — ular o'yin, kutubxona emas, va
ularga tasodifan bog'lanib qolish mumkin bo'lmasligi kerak.

Har bir artefakt: `jar` + `sources` + `javadoc` + Central talab qiladigan to'liq
POM (nom, tavsif, url, MIT, developer, scm). Tekshirildi: `publishToMavenLocal`.

Imzo **faqat kalit bo'lganda** qo'llanadi, shuning uchun kalitsiz ham lokal
nashr ishlaydi. Kalit ham, token ham repoda emas — `~/.gradle/gradle.properties`
da (`mavenCentralUsername`, `mavenCentralPassword`, `signingInMemoryKey`,
`signingInMemoryKeyPassword`).

### Paketlar: `uz.duke.*` → `uz.dukeengine.*`

Maven groupId'da tire mumkin, Java paketda — yo'q. Lekin paketni ham egalik
qilinadigan domendan olish kerak: `duke-engine.uz` bizniki, `duke.uz` — yo'q.

**565 fayl, 5058 joy**, plus 14 ta paket papkasi `git mv` bilan ko'chirildi.
Ikki xil yozuv bor edi va ikkalasi ham tuzatildi: nuqtali (`uz.duke.core`) va
slashli (`"uz/duke/"` — modul skanerida yo'l satri sifatida).

Plaginning `<id>` si ham `uz.dukeengine.plugin` bo'ldi va Gradle guruhi
`uz.sonic` → `uz.duke-engine`. Ikkalasi ham hali nashr qilinmagani uchun bepul.

**Nega hozir:** Central'ga chiqqandan keyin paket nomini o'zgartirish har bir
foydalanuvchi uchun buzuvchi o'zgarish bo'lardi. Bu oxirgi arzon lahza edi.

### Tekshirildi

- `./gradlew build` — yashil (1630 test)
- plagin testlari — yashil (54 test)
- `publishToMavenLocal` — beshta modul `uz/duke-engine/` ostida
- sehrgar yozgan loyiha **haqiqiy Gradle bilan** qurildi va o'z testi o'tdi,
  endi `uz.dukeengine.*` importlari va `uz.duke-engine:client3d:0.2.0`
  koordinatasi bilan

### Plagin relizi

Versiya **0.2.0** (engine bilan bir xil). `duke-plugin/build.gradle.kts` ga
reliz bloki qo'shildi:

- `sinceBuild = 253` (2025.3), **untilBuild yo'q**. Yuqori chegara yozish —
  hech kim sinab ko'rmasdan turib plaginni yangi IDE'ga o'rnatishdan man qilish
  degani; buni `verifyPlugin` aytadi, raqam emas.
- `changeNotes` — `CHANGELOG.md` dan o'qiladi, ikki joyda yozilmaydi.
  `patchChangelog` ishga tushirildi: `Unreleased` bo'limi `## 0.2.0` ostiga
  ko'chdi.
- **Kanal:** `0.x` versiya **beta** kanaliga ketadi — uni faqat o'sha kanalni
  ataylab qo'shgan odam oladi. Birinchi `0.` siz versiya default kanalga.
- Imzo va token — `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
  `PRIVATE_KEY_PASSWORD` muhit o'zgaruvchilari. Repoda hech narsa yo'q.

`<vendor url>` `duke-engine.uz` ga o'zgartirildi, `<id>` esa paket
o'zgarishi bilan `uz.dukeengine.plugin` bo'ldi. Ikkalasi ham hali nashr
qilinmagani uchun bepul.

Tekshirildi: `buildPlugin` (1.0 MB zip), `verifyPluginProjectConfiguration`,
`verifyPluginStructure` — hammasi toza. Paketlangan `plugin.xml` da
`since-build="253"`, versiya, change-notes va New Project tavsifi bor.

**Qolgani:** `verifyPlugin` (IDE'larni yuklab oladi, ~GB) va haqiqiy
`publishPlugin` — ikkalasi ham kalit va tokenni talab qiladi, ya'ni seniki.
