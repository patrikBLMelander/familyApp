# Butiksbilder – en / de / es

Tagna 2026-09-27 mot appens fixturer (ingen backend, inget riktigt konto). Statusrad 09:41, fullt batteri.
Valuta: **en = USD, de/es = EUR**. Svenska originalen ligger kvar i `~/kidquest-shots/` (iOS) och `../play/skarmbilder/` (Android).

## iOS (`ios/<språk>/`)
| Mapp | Storlek | App Store Connect |
|---|---|---|
| `iphone-6.5/` | 1284×2778 | iPhone 6.5" (tagna på iPhone 17 Pro Max, skalade) |
| `ipad-13/` | 2064×2752 | iPad 13" (iPad Pro 13" M5) |

| Fil | Fixtur (`KQ_SCREEN`) |
|---|---|
| 01-pet-and-chores | `child` |
| 02-todays-chores | `childtasks` |
| 03-wallet | `wallet` |
| 04-parent-overview | `dashboard` |
| 05-allowance | `allowance` |
| 06-all-done | `child-done` |

Ta om: `SIMCTL_CHILD_KQ_SCREEN=<fixtur> xcrun simctl launch <udid> se.kidquest.app -AppleLanguages "(de)" -AppleLocale de_DE -kq.familyCurrency EUR`

## Android (`android/<språk>/`), 1080×1920
| Fil | Fixtur (`--es kq_screen`) |
|---|---|
| 0-welcome | `welcome` |
| 1-feed | `child` |
| 2-all-done | `child-done` |
| 4-parent | `child-asparent` |

Ta om (emulatorn **KidQuest_Shots**, 12 GB data – den gamla hade slut på plats):
`adb shell cmd locale set-app-locales se.kidquest.app --locales de-DE` och
`adb shell am start -n se.kidquest.app/.MainActivity --es kq_screen child --es kq_currency EUR --es kq_name Ella`.
Kör varje språk två gånger efter en ny installation – första starten nollställer appspråket.

## Namn i bilderna
Påhittade namn på båda plattformarna: barnen **Ella** och **Leo**, föräldrarna **Jonas** och **Anna**, familjen **Berg**
(iOS-fixturerna bytta 2026-09-27; Android via `--es kq_name Ella`). Inga riktiga familjemedlemmar syns.

## Saknas
- Android: föräldraöversikt, plånbok, veckopeng (ingen fixtur – kräver inloggning, samma som i den svenska uppsättningen).
- Äggväljaren/samlingen på båda plattformarna.
