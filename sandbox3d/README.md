# `sandbox3d` — 3D demo skirmish

**Rol: DEMO.** `sandbox` ning 3D varianti: bir xil skirmish, lekin jME oynasida
va bitta haqiqiy animatsiyalangan model bilan.

| | |
|---|---|
| Bog'liqligi | `implementation project(":client3d")` + `runtimeOnly jme3-testdata` |
| Kim bunga bog'lanadi | hech kim |
| Hajmi | **1 fayl, 74 qator** |
| Testlar | 0 |
| Ishga tushirish | `./gradlew :sandbox3d:run` |

## Nima qiladi

`sandbox` bilan bir xil sahna (bir xil bazalar, bir xil kazarma-AI), ustiga
`Visuals` bog'lashi qo'shilgan:

```java
var visuals = Visuals.create()
        .unit("Rifleman", u -> u.model("Models/Oto/Oto.mesh.xml")
                .scale(0.35f).yOffset(1.8f).facing(90)
                .idle("stand").walk("Walk").attack("push"));
Duke3D.launch(game, visuals);
```

Rifleman jME'ning test-asseti bo'lgan Oto modelini oladi (stand / Walk / push
animatsiyalari), qolganlari primitiv bilan chiziladi — ya'ni "model qo'ysang
ishlaydi, qo'ymasang ham ishlaydi" xatti-harakatining namoyishi.

## O'chirish tahlili

**O'chirsa bo'ladi**, lekin `sandbox` dan ko'ra qimmatliroq:

- `client3d` ning **yagona** tez sinov yo'li (studio'ni ochmasdan 3D klientni
  yurgizish). `client3d` da 0 test bor, ya'ni bu amalda yagona tekshiruv.
- `Visuals` + model yuklash + animatsiya zanjirining yagona namunasi.

**Infratuzilma tuzog'i:** `:sandbox3d:startScripts` `jme3-testdata` jar'ini
talab qiladi — tarmoq sekin bo'lsa `./gradlew build` aynan shu yerda yiqiladi
(kod muammosi emas). Agar build barqarorligi muhim bo'lsa, o'chirish uchun
qo'shimcha sabab.

`sandbox` bilan ~90% bir xil — ikkalasidan bittasini saqlash kifoya. Saqlash
kerak bo'lsa **shuni** saqlang: `sandbox` `game/swing` ni, bu esa butun
`client3d` zanjirini sinaydi.
