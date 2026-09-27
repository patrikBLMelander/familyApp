# KidQuest – i18n glossary & contract

Languages: **sv** (Swedish, original), **en** (English, fallback for any unsupported language), **de** (German), **es** (Spanish).

Tone: playful, warm, short. Children read most of this. Address the user informally: *du* (sv), *you* (en), **du** (de – never *Sie*), **tú** (es – never *usted*). Keep emoji exactly as in the Swedish source.
Spanish: neutral/international Spanish (no *vosotros*-only forms in UI; prefer constructions that work for Spain and Latin America). German: gender-neutral where cheap ("Kind", "Elternteil").

## Core terms (use these exact words everywhere)

| Swedish | English | German | Spanish |
|---|---|---|---|
| syssla / sysslor | chore / chores | Aufgabe / Aufgaben | tarea / tareas |
| dagens sysslor | today's chores | Aufgaben für heute | tareas de hoy |
| obligatorisk / extra (syssla) | required / extra | Pflicht / Extra | obligatoria / extra |
| XP | XP | XP | XP |
| bonus-XP | bonus XP | Bonus-XP | XP extra |
| nivå | level | Level | nivel |
| djur / husdjur | pet | Tier | mascota |
| ägg | egg | Ei | huevo |
| kläcka / kläcks | hatch / hatches | schlüpfen | eclosionar (UI: "nacer" ok for kids) |
| mata | feed | füttern | alimentar / dar de comer |
| mat (djurmat) | food | Futter | comida |
| omatad mat | unfed food | übriges Futter | comida sin dar |
| månadens djur | pet of the month | Tier des Monats | mascota del mes |
| samling | collection | Sammlung | colección |
| vanlig / sällsynt / legendarisk | common / rare / legendary | gewöhnlich / selten / legendär | común / rara / legendaria |
| äventyr | adventure | Abenteuer | aventura |
| (äventyrs)biljett | (adventure) ticket | (Abenteuer-)Ticket | ticket (de aventura) |
| stjärna | star | Stern | estrella |
| ram (på scenen) | frame | Rahmen | marco |
| dekoration | decoration | Deko | decoración |
| plånbok | wallet | Geldbörse | monedero |
| saldo | balance | Kontostand | saldo |
| veckopeng | weekly allowance | Taschengeld (wöchentlich) | paga semanal |
| månadspeng | monthly allowance | Taschengeld (monatlich) | paga mensual |
| månadspeng efter nivå | allowance by level | Taschengeld nach Level | paga según el nivel |
| automatisk utbetalning | automatic allowance | automatisches Taschengeld | paga automática |
| sparmål | savings goal | Sparziel | meta de ahorro |
| köp / utgift | purchase / expense | Einkauf / Ausgabe | compra / gasto |
| familj | family | Familie | familia |
| förälder / föräldrar | parent / parents | Elternteil / Eltern | padre o madre / padres |
| barn | child / kid | Kind | niño/a (UI: "hijo/a" in parent context) |
| assistent | helper | Helfer:in | ayudante |
| kalender | calendar | Kalender | calendario |
| att göra-lista | to-do list | To-do-Liste | lista de tareas pendientes |
| händelse | event | Termin | evento |
| upprepas | repeats | wiederholt sich | se repite |
| bjud in (QR) | invite | einladen | invitar |
| koppla enhet | link device | Gerät verbinden | vincular dispositivo |
| prenumeration | subscription | Abo | suscripción |
| provperiod | free trial | Testphase | prueba gratuita |
| värvningskod | referral code | Empfehlungscode | código de invitación |
| mens/cykel (spårning) | cycle tracking | Zyklus-Tracking | seguimiento del ciclo |
| inställningar | settings | Einstellungen | ajustes |
| språk | language | Sprache | idioma |
| valuta | currency | Währung | moneda |

## Pets (petType → name)

| petType | sv | en | de | es |
|---|---|---|---|---|
| cat | Katt | Cat | Katze | Gato |
| dog | Hund | Dog | Hund | Perro |
| rabbit | Kanin | Rabbit | Hase | Conejo |
| bird | Fågel | Bird | Vogel | Pájaro |
| bear | Björn | Bear | Bär | Oso |
| panda | Panda | Panda | Panda | Panda |
| slot (sloth) | Sengångare | Sloth | Faultier | Perezoso |
| kapybara | Kapybara | Capybara | Wasserschwein | Capibara |
| snake | Orm | Snake | Schlange | Serpiente |
| koala | Koala | Koala | Koala | Koala |
| meerkat | Surikat | Meerkat | Erdmännchen | Suricata |
| penguin | Pingvin | Penguin | Pinguin | Pingüino |
| spider | Spindel | Spider | Spinne | Araña |
| kangaroo | Känguru | Kangaroo | Känguru | Canguro |
| dragon | Drake | Dragon | Drache | Dragón |
| hydra | Hydra | Hydra | Hydra | Hidra |
| unicorn | Enhörning | Unicorn | Einhorn | Unicornio |
| shark | Haj | Shark | Hai | Tiburón |
| lion | Lejon | Lion | Löwe | León |
| scorpion | Skorpion | Scorpion | Skorpion | Escorpión |
| octopus | Bläckfisk | Octopus | Oktopus | Pulpo |
| snowleopard | Snöleopard | Snow leopard | Schneeleopard | Leopardo de las nieves |
| tiger | Tiger | Tiger | Tiger | Tigre |
| polarbear | Isbjörn | Polar bear | Eisbär | Oso polar |
| giraffe | Giraff | Giraffe | Giraffe | Jirafa |
| elephant | Elefant | Elephant | Elefant | Elefante |
| crocodile | Krokodil | Crocodile | Krokodil | Cocodrilo |
| panther | Panter | Panther | Panther | Pantera |
| wolf | Varg | Wolf | Wolf | Lobo |

Egg names follow the pattern "<color> egg" / "<Farbe>s Ei" / "huevo <color>"; translate the color word.

Months: use the platform's locale-aware formatter (never hand-written month tables) where possible; lowercase in sv/es, capitalised in en/de.

## Currency

Per family: `SEK` (default), `EUR`, `USD`, `GBP`, `NOK`, `DKK`, `CHF`. Amounts stay **whole units** (int). Always format with the platform's currency formatter using the **app language's locale** and **0 fraction digits** (e.g. sv `120 kr`, de `120 €`, en `€120`, es `120 €`). Never hard-code "kr".

## API contract (backend ↔ apps)

- Apps send `Accept-Language: <sv|en|de|es>` on **every** request = the effective app language.
- Backend resolves the locale for user-facing text: member's saved `language` → `Accept-Language` → `en`.
- `FamilyMemberResponse` (both the one in FamilyMemberController and in FamilyController) gains `language: string | null` (`null` = follow device).
- `PATCH /api/v1/family-members/{memberId}/language` body `{ "language": "de" | null }` → `FamilyMemberResponse`. Allowed for the member themselves, or a parent of that member's family. Invalid code → 400.
- `FamilyResponse` gains `currency: string` (ISO 4217).
- `PATCH /api/v1/families/{familyId}/currency` body `{ "currency": "EUR" }` → `FamilyResponse`. Parents only. Unsupported code → 400.
- `WalletBalanceResponse` gains `currency: string` (the family's), so a child's wallet can format without fetching the family.
- Error bodies stay `{ "error": "<message>" }`, message now localized.
- Wallet transaction descriptions and default expense-category names produced by the system are translated **when read** (old Swedish rows included); user-typed text is never translated.
