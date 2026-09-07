# `rts` — RTS janr moduli

**Rol: ENGINE (janr qatlami).** `core` ustidagi RTS: buyruq to'plami, jang,
ishlab chiqarish, iqtisod, veteranlik, quvvat, lug'at va save formati.

Bu modul ikki vazifani bajaradi:
1. **Ishlaydigan RTS kutubxonasi** — `game` shunga tayanadi.
2. **Beshta kengaytirish chokining ishlangan namunasi** — boshqa janr yozmoqchi
   bo'lgan odam `core` ga bog'lanib, aynan shu naqshni takrorlaydi.

| | |
|---|---|
| Bog'liqligi | `api project(":core")` |
| Kim bunga bog'lanadi | `game` (→ `client3d` → `studio`) |
| Hajmi | 21 fayl, ~1 750 qator |
| Testlar | 69 ta |

---

## Nima bor

### Chok implementatsiyalari (modulning skeleti)

| Fayl | Qator | Vazifa |
|---|---|---|
| `RtsSimulation` | 73 | RTS mantiqining bazasi: `Command` → `GameMessage` toraytirish (bir marta, shu yerda), `RtsModules` o'rnatish, `RtsPlayer` rosteri, `purchaseUpgrade` |
| `message/GameMessage` | 61 | **sealed** RTS buyruq to'plami: `MoveTo`, `AttackObject`, `StopMoving`, `QueueProduction`, `SetRallyPoint` |
| `network/CommandCodec` | 113 | sim formati: paket ↔ bitta qator. Exhaustive switch, `Float.toString` (bit-aniq round-trip) |
| `module/RtsModules` | 60 | INI tag → RTS modullari registratsiyasi (`ModuleFactory.withDefaults()` ning RTS varianti) |
| `player/RtsPlayer` | 75 | `Player` + pul, upgrade'lar, qurol bonusi |
| `thing/RtsKinds` | 36 | RTS lug'ati: `SELECTABLE`, `CAN_ATTACK`, `STRUCTURE`, `INFANTRY`, `VEHICLE`, `POWERED` |

### Gameplay modullari

| Fayl | Qator | Vazifa | Yuqori qatlamda ochilganmi |
|---|---|---|---|
| `WeaponUpdate` | 216 | reload sikli, splash, ittifoqchini urmaydi, XP beradi; masofa **yuzadan yuzaga** (devorga tiralgan birlik binoni ura oladi) | ha (Studio: ATTACK) |
| `ProductionUpdate` | 175 | navbat, pulni oldindan yechish, rally nuqtasi; quvvat yetmasa to'xtaydi; tayyor birlik zavod devoridan **tashqarida, bo'sh yerda** paydo bo'ladi | ha (Studio: PRODUCE) |
| `ExperienceModule` | 114 | XP yig'ish, `VeterancyLevel` bo'yicha ko'tarilish (ko'tarilganda to'liq davolanadi) | ha (EXPERIENCE) |
| `SpecialPowerModule` | 98 | qayta zaryadlanuvchi radiusli superqurol | **yo'q** |
| `ContainModule` | 97 | garnizon / transport (ichidagilar jim, nishonga olinmaydi) | **yo'q** |
| `HarvestUpdate` | 84 | yig'ish→topshirish sikli (eng yaqin `SupplyModule` uyumidan) | ha (HARVEST) |
| `StatusUpdate` | 69 | muddatli DISABLED / SLOWED effektlari | **yo'q** |
| `PowerModule` | 59 | quvvat ishlab chiqarish / iste'mol | ha (POWER) |
| `AutoHealUpdate` | 58 | regeneratsiya | ha (AUTO_HEAL) |
| `SupplyModule` | 57 | cheklangan resurs uyumi | ha (SUPPLY) |
| `PowerGrid` | 37 | o'yinchi quvvat balansi — **hosila holat**, hech qayerda saqlanmaydi (desync manbai yo'q) | bilvosita |
| `VeterancyLevel` | 25 | REGULAR → HEROIC, zarar ko'paytirgichlari | bilvosita |
| `player/Upgrade` | 13 | sotib olinadigan texnologiya (o'yinchi bo'ylab qurol bonusi) | **yo'q** |

### Boshqa

| Fayl | Qator | Vazifa | Holati |
|---|---|---|---|
| `save/GameSnapshot` | 138 | dunyo holatini matnga saqlash/tiklash; tiklangan dunyo checksum'i bir xil | **hech qayerda ishlatilmaydi** (faqat 1 test) |
| `client/AsciiRenderer` | 83 | tumanni hisobga oluvchi matnli minimap | **hech qayerda ishlatilmaydi** (faqat 1 test) |

---

## O'chirish tahlili

**O'chirsa bo'ladi:**

| Nomzod | Sabab | Narxi |
|---|---|---|
| `client/AsciiRenderer` | `game` ham, `client3d` ham buni ishlatmaydi — ikkalasi `WorldSnapshot` o'qiydi. `core/client/Renderer` seami bilan birga o'chadi | vizual regressiya testining bir turi yo'qoladi |
| `save/GameSnapshot` | yuqori qatlamlarga umuman ulanmagan; modul ichidagi "in-flight" holat (ishlab chiqarish taymerlari) baribir saqlanmaydi, ya'ni hozirgi holida yarim ishlaydi | o'yin ichida save/load qilmoqchi bo'lsangiz noldan yozish kerak |
| `module/SpecialPowerModule`, `module/ContainModule`, `module/StatusUpdate`, `player/Upgrade` | faqat `RtsModules` da ro'yxatdan o'tadi — Studio'da ham, biror o'yinda ham ishlatilmaydi. Qo'lda INI yozsa ishlaydi | RTS to'liqligi kamayadi; keyin kerak bo'lsa qayta yozish |

> Diqqat: bu 4 ta modulni o'chirsangiz `RtsModules` dan registratsiyasini,
> tegishli testlarni (`SpecialPowerTest`, `ContainTest`, `StatusUpdateTest`,
> `UpgradeTest`) va `RtsSimulation.purchaseUpgrade` ni ham olib tashlash kerak.
> `ObjectStatus` (`core`) faqat `StatusUpdate` uchun ma'noli — u ham bo'shab qoladi.

**O'chirib bo'lmaydi:** `RtsSimulation`, `GameMessage`, `CommandCodec`,
`RtsModules`, `RtsPlayer`, `RtsKinds`, `WeaponUpdate`, `ProductionUpdate`,
`PowerModule`+`PowerGrid`, `SupplyModule`+`HarvestUpdate`, `ExperienceModule`+
`VeterancyLevel`, `AutoHealUpdate` — bularning hammasi Studio capability'lari
yoki `game` mantig'i orqali ishlatiladi.

## Bilib qo'yish kerak

- **Sealed modul chegarasidan o'tmaydi** — aynan shuning uchun buyruq to'plami
  `core` da emas, shu yerda. Yangi janr = o'z modulingizda o'z sealed
  ierarxiyangiz.
- **Template nomlari** vergul, `|`, `;`, `:` belgilarini o'z ichiga olmasin —
  `CommandCodec` sim formati buziladi.
- **Quvvat tuzog'i:** o'yinchi quvvatsiz bo'lsa ishlab chiqarish to'xtaydi
  (Generals mexanikasi) — iste'molchi spawn qiladigan testlarga PowerPlant kerak.
- **Fizik o'lcham `core` dan keladi:** `ThingTemplate.Geometry`. RTS tomonda bu
  ikki joyda seziladi — ishlab chiqarish chiqish nuqtasi (zavod devoridan
  tashqarida bo'sh yer izlaydi) va qurol masofasi (`World.reachBetween`,
  markazdan emas). Geometriyasi yo'q (`POINT`) kontent avvalgidek ishlaydi.
