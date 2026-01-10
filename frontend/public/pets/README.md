# Pet Images

Placera dina pet-bilder här i följande struktur:

```
pets/
├── dragon/
│   ├── dragon-egg-stage1.png      (Visas vid val av ägg)
│   ├── dragon-egg-stage2.png      (Hatching animation)
│   ├── dragon-egg-stage3.png      (Hatching animation)
│   ├── dragon-egg-stage4.png      (Hatching animation)
│   ├── dragon-egg-stage5.png      (Hatching animation)
│   ├── dragon-stage1.png          (Pet efter kläckning)
│   ├── dragon-stage2.png          (Pet evolution)
│   ├── dragon-stage3.png          (Pet evolution)
│   ├── dragon-stage4.png          (Pet evolution)
│   ├── dragon-stage5.png          (Pet evolution)
│   └── dragon-background.png      (Bakgrund för pet-kortet)
├── cat/
│   ├── cat-egg-stage1.png
│   ├── cat-egg-stage2.png
│   ├── cat-egg-stage3.png
│   ├── cat-egg-stage4.png
│   ├── cat-egg-stage5.png
│   ├── cat-stage1.png
│   ├── cat-stage2.png
│   ├── cat-stage3.png
│   ├── cat-stage4.png
│   ├── cat-stage5.png
│   └── cat-background.png
├── dog/
│   ├── dog-egg-stage1.png
│   ├── dog-egg-stage2.png
│   ├── dog-egg-stage3.png
│   ├── dog-egg-stage4.png
│   ├── dog-egg-stage5.png
│   ├── dog-stage1.png
│   ├── dog-stage2.png
│   ├── dog-stage3.png
│   ├── dog-stage4.png
│   ├── dog-stage5.png
│   └── dog-background.png
├── bird/
│   ├── bird-egg-stage1.png
│   ├── bird-egg-stage2.png
│   ├── bird-egg-stage3.png
│   ├── bird-egg-stage4.png
│   ├── bird-egg-stage5.png
│   ├── bird-stage1.png
│   ├── bird-stage2.png
│   ├── bird-stage3.png
│   ├── bird-stage4.png
│   ├── bird-stage5.png
│   └── bird-background.png
├── rabbit/
│   ├── rabbit-egg-stage1.png
│   ├── rabbit-egg-stage2.png
│   ├── rabbit-egg-stage3.png
│   ├── rabbit-egg-stage4.png
│   ├── rabbit-egg-stage5.png
│   ├── rabbit-stage1.png
│   ├── rabbit-stage2.png
│   ├── rabbit-stage3.png
│   ├── rabbit-stage4.png
│   ├── rabbit-stage5.png
│   └── rabbit-background.png
└── bear/
    ├── bear-egg-stage1.png
    ├── bear-egg-stage2.png
    ├── bear-egg-stage3.png
    ├── bear-egg-stage4.png
    ├── bear-egg-stage5.png
    ├── bear-stage1.png
    ├── bear-stage2.png
    ├── bear-stage3.png
    ├── bear-stage4.png
    ├── bear-stage5.png
    └── bear-background.png
```

## Rekommendationer för bilder:

- **Format**: PNG (med transparent bakgrund)
- **Storlek**: 400x400px eller större (kvadratiska bilder fungerar bäst)
- **Bakgrund**: Transparent
- **Stil**: Söt, barnvänlig, chibi/cartoon-stil
- **Färger**: Ljus och färgglad

## Egg Stages (för hatching-animation):

- **Egg Stage 1**: Visas när användaren väljer ägg (helt ägg)
- **Egg Stage 2-5**: Progressivt kläckande (fler sprickor/mindre skal)
- Dessa används under hatching-animationen för att visa att ägget kläcks

## Pet Stages (för pet-evolution):

- **Stage 1**: Bebis/nyfödd - liten och söt (visas direkt efter kläckning)
- **Stage 2**: Ung - lite större, mer detaljer
- **Stage 3**: Tonåring - medelstor, mer utvecklad
- **Stage 4**: Vuxen - större, fullt utvecklad
- **Stage 5**: Majestätisk - störst, mest imponerande

## Background Images:

- **Background**: Bakgrundsbild för pet-kortet på dashboarden
- **Namnmönster**: `{petType}-background.png` (t.ex. `dragon-background.png`)
- **Placering**: Samma mapp som övriga bilder för respektive djur
- **Användning**: Visas som bakgrund på kortet där petet visas
- **Format**: PNG eller JPG (med eller utan transparent bakgrund - bilden täcker hela kortet)
- **Storlek**: Rekommenderat 800x600px eller större (kortet använder `background-size: cover` så storleken justeras automatiskt)

