# Play-butikssidan

Klart och osäkert, per fält. Uppdatera i takt med att rutorna fylls i.

## Text — klart, väntar på granskning
| Fält | Fil | Läge |
|---|---|---|
| Appnamn (max 30) | `KidQuest` | 8 tecken |
| Kort beskrivning (max 80) | `kort-beskrivning.txt` | 78 tecken |
| Fullständig beskrivning (max 4000) | `fullstandig-beskrivning.txt` | 1981 tecken |

Priset i texten (29 kr/mån, en månad gratis) är hämtat ur
`FamilySubscription.TRIAL_MONTHS = 1` och betalväggens egen text, inte gissat.
Ändras priset måste beskrivningen ändras samtidigt.

## Skärmbilder — tagna, inte valda
`skarmbilder/`, alla 1080x1920 (9:16). Play kräver minst två, tar upp till åtta.
Tagna på emulator med demoläge i statusraden (09:41, fullt batteri) och med
`--es kq_name Ella`, så inget riktigt barns namn syns.

| Fil | Visar | Duger till butiken? |
|---|---|---|
| `0-valkommen.png` | Startskärmen med de tre löftena | ja |
| `1-mata.png` | Djuret, matremsan, dagens sysslor | ja — huvudbilden |
| `2-klart.png` | "Allt klart idag!", full remsa | ja |
| `4-foralder.png` | Förälderns vy av barnets skärm | ja |

Äggväljaren och samlingen saknas: bilderna jag tog visade de gamla äggnamnen,
som nu är borttagna, så de kastades. `referens-aggvaljaren-ios.png` visar hur
väljaren ser ut efter ändringen — den är från iOS-simulatorn och har fel
bildförhållande för Play, så den är en referens och inte en butiksbild. Android
måste tas om när emulatorn mår bra igen.

Saknas: föräldraöversikten, plånboken och sparmålen. De ligger bakom en riktig
inloggning och emulatorn har ingen session kvar. Antingen loggar du in på
emulatorn, eller så bygger jag en fixtur för vuxenvyn på samma sätt som
barnvyns.

## Grafik — inte gjord
- [ ] Appikon 512x512 PNG
- [ ] Utvald bild 1024x500 PNG
- [ ] Riktig launcher-ikon i appen (`ic_launcher_foreground.xml` är fortfarande
      Android Studios mall)

## Deklarationer — inte gjorda
- [ ] Kategori och taggar
- [ ] Kontaktuppgifter (support@kidquest.se, kidquest.se)
- [ ] Integritetspolicy: https://www.kidquest.se/privacy
- [ ] **Målgrupp och innehåll** — se varningen nedan
- [ ] Innehållsklassificering (IARC-formuläret)
- [ ] Datasäkerhet, inklusive raderingslänk
      https://www.kidquest.se/radera-konto — kontrollera att sidan finns
- [ ] Appen innehåller köp i appen: ja (prenumeration 29 kr/mån)
- [ ] Annonser: nej

## Varning: målgruppsfrågan är inte en formalitet

Play frågar vilka åldrar appen riktar sig till. Svarar man att barn ingår
hamnar appen i **Familjeprogrammet**, med egna krav på annonser, datainsamling
och köpflöden, och en extra granskning som tar tid.

KidQuest är byggd för att barn ska använda den, men köps och styrs av en
förälder. Vilket svar som är rätt avgör hur mycket arbete som återstår, och det
bör redas ut innan produktionsansökan skickas in den 13 september — inte efter.
