# Butikstexter – en / de / es

Svenska originalet ligger i `../play/`. Mapparna här har samma innehåll, anpassat och med aktuella fakta.

| Fil | Play Console | App Store Connect | Max |
|---|---|---|---|
| `play-kort-beskrivning.txt` | Kort beskrivning | – | 80 |
| `play-fullstandig-beskrivning.txt` | Fullständig beskrivning | Description (samma text) | 4000 |
| `appstore-undertitel.txt` | – | Subtitle | 30 |
| `appstore-reklamtext.txt` | – | Promotional Text (kan ändras utan ny granskning) | 170 |
| `appstore-nyckelord.txt` | – | Keywords (kommaseparerade, utan mellanslag efter komma) | 100 |

Appnamnet är `KidQuest` på alla språk.

## Ändringar mot den ursprungliga svenska texten (punkt 1–2 och äventyren är nu rättade även i svenskan)
- **Provperiod: 1 månad**, inte tre (`FamilySubscription.TRIAL_MONTHS = 1`).
- **"Nästan 30 djur"** i stället för "fjorton" – det finns 29 djur i dag.
- **Inget pris i siffror.** Butiken visar priset per land; "29 kronor" vore fel i euro. Texten säger "ett lågt månadspris för hela familjen".
- **Nytt stycke om äventyr, sällsynta djur och stjärnor**, och "i din egen valuta" under plånboken. Äventyrsstycket finns nu även i svenskan; valutameningen behövs inte där.
- "En pappa **i Sverige**" – förklarar för en utländsk läsare varför supporten är liten.

## Att göra innan de används
- [ ] Native-granskning av tyska och spanska.
- [ ] Skärmbilder per språk – de nuvarande visar svenska. iOS: `simctl launch … -AppleLanguages "(de)"` med ScreenHarness; Android: byt appspråk i emulatorn.
- [ ] Integritetspolicy på engelska (kidquest.se/privacy är svensk).
- [ ] Support-mejl på engelska/tyska/spanska – svarar du på engelska räcker det, men var beredd.
