# Testlista: Bonus Mat-funktionalitet

## Översikt
Denna lista täcker alla aspekter av bonus mat-funktionaliteten där vuxna kan ge mat till barn, och barnen sedan kan mata sina djur.

---

## 1. Ge Bonus Mat (Vuxen vy)

### 1.1 Grundfunktionalitet
- [ ] Logga in som vuxen (PARENT eller ASSISTANT)
- [ ] Gå till "Mina barns djur"-vyn
- [ ] Klicka på "+ Ge mat" för ett barn
- [ ] Verifiera att dialogrutan öppnas
- [ ] Ange ett värde mellan 1-100 (t.ex. 10)
- [ ] Klicka på "Ge mat"
- [ ] Verifiera att dialogrutan stängs utan fel

### 1.2 Validering
- [ ] Försök ange 0 mat → ska visa felmeddelande
- [ ] Försök ange negativt värde → ska visa felmeddelande
- [ ] Försök ange värde över 100 → ska visa felmeddelande
- [ ] Försök ange text istället för siffror → ska visa felmeddelande

### 1.3 Edge Cases
- [ ] Ge mat till barn som inte har något djur ännu
- [ ] Ge mat till flera barn i rad
- [ ] Ge olika mängder mat (1, 10, 50, 100)

---

## 2. Visa Mat på Barnets Dashboard

### 2.1 Mat visas korrekt
- [ ] Logga in som barnet som fick bonus mat
- [ ] Gå till barnets dashboard
- [ ] Verifiera att maten visas i "Insamlad mat"-sektionen
- [ ] Verifiera att antalet stämmer med vad vuxen gav
- [ ] Verifiera att rätt mat-emoji visas (baserat på djurets typ)

### 2.2 Mat persisterar
- [ ] Ladda om sidan (F5)
- [ ] Verifiera att maten fortfarande visas
- [ ] Logga ut och logga in igen
- [ ] Verifiera att maten fortfarande finns kvar

### 2.3 Kombinera med task-mat
- [ ] Ge bonus mat (t.ex. 5)
- [ ] Klara en task som ger mat (t.ex. 3)
- [ ] Verifiera att totalen är korrekt (5 + 3 = 8)
- [ ] Verifiera att både bonus mat och task-mat visas tillsammans

---

## 3. Mata Djuret

### 3.1 Mata 1 mat
- [ ] Klicka på "Mata 1"-knappen
- [ ] Verifiera att antalet mat minskar med 1
- [ ] Verifiera att XP ökar med 1
- [ ] Verifiera att progress bar uppdateras
- [ ] Verifiera att inga felmeddelanden visas

### 3.2 Mata allt
- [ ] Samla mat (bonus + tasks)
- [ ] Klicka på "Mata allt"-knappen
- [ ] Verifiera att all mat försvinner från "Insamlad mat"
- [ ] Verifiera att XP ökar med rätt mängd
- [ ] Verifiera att progress bar uppdateras korrekt

### 3.3 Level up
- [ ] Samla tillräckligt med mat för att nå nästa level
- [ ] Mata djuret
- [ ] Verifiera att confetti-animationen visas
- [ ] Verifiera att djurets bild pulserar med gradient
- [ ] Verifiera att level ökar
- [ ] Verifiera att progress bar återställs för nästa level

### 3.4 Edge Cases
- [ ] Försök mata när det inte finns någon mat → knappen ska vara disabled
- [ ] Mata när maten är under matning → knappen ska vara disabled
- [ ] Mata precis så att level ökar

---

## 4. Task Uncompletion med Bonus Mat

### 4.1 Uncompleta task med bonus mat
- [ ] Ge bonus mat (t.ex. 10)
- [ ] Klara en task (t.ex. 5 XP)
- [ ] Uncompleta samma task
- [ ] Verifiera att task blir uncompleted
- [ ] Verifiera att bonus mat används för att "betala tillbaka" XP
- [ ] Verifiera att rätt mängd mat finns kvar (10 - 5 = 5)

### 4.2 Uncompleta task utan tillräckligt med mat
- [ ] Ge lite bonus mat (t.ex. 2)
- [ ] Klara en task med mer XP (t.ex. 5)
- [ ] Försök uncompleta tasken
- [ ] Verifiera att felmeddelande visas
- [ ] Verifiera att tasken förblir completed

### 4.3 Uncompleta task med redan matad mat
- [ ] Ge bonus mat (t.ex. 10)
- [ ] Klara en task (t.ex. 5)
- [ ] Mata allt
- [ ] Försök uncompleta tasken
- [ ] Verifiera att felmeddelande visas om att maten redan är matad
- [ ] Verifiera att tasken förblir completed

---

## 5. Visuell Feedback

### 5.1 Floating XP Numbers
- [ ] Mata djuret
- [ ] Verifiera att flytande XP-siffror visas
- [ ] Verifiera att de animeras korrekt
- [ ] Verifiera att de försvinner efter animationen

### 5.2 Pet Mood
- [ ] Ge mat till barnet
- [ ] Verifiera att djurets humör är "glad"
- [ ] Verifiera att rätt meddelande visas
- [ ] Vänta en dag utan att ge mat
- [ ] Verifiera att djurets humör blir "hungrig"
- [ ] Verifiera att rätt meddelande visas

### 5.3 Progress Bar
- [ ] Verifiera att progress bar visar korrekt progress
- [ ] Verifiera att progress bar uppdateras när mat matas
- [ ] Verifiera att progress bar är 360 grader och synlig
- [ ] Verifiera att djurets namn och level visas i cirkeln
- [ ] Testa på mobil → verifiera att cirkeln är mindre

---

## 6. Vuxen vy - Visuella Uppdateringar

### 6.1 "Mina barns djur"-vyn
- [ ] Verifiera att texten säger "Mat" istället för "XP"
- [ ] Verifiera att mat-emoji visas för varje barn
- [ ] Verifiera att "+ Ge mat"-knappen fungerar
- [ ] Verifiera att dialogrutan säger "mat" istället för "XP"

### 6.2 Kalendervyn (RollingView)
- [ ] Gå till kalendervyn
- [ ] Verifiera att barnens tasks visar mat-emoji (t.ex. "🐟")
- [ ] Verifiera att mat-emoji visas på höger sida av task-raden
- [ ] Verifiera att matnamnet INTE visas (bara emoji)
- [ ] Verifiera att vuxnas tasks INTE visar XP/mat

### 6.3 Personlig XP-vy (XpDashboard)
- [ ] Logga in som vuxen
- [ ] Gå till personlig XP-vy
- [ ] Verifiera att texten säger "Mat & Level" istället för "XP & Level"
- [ ] Verifiera att mat-emoji och namn visas

---

## 7. Backend & Databas

### 7.1 Databas
- [ ] Verifiera att `collected_food`-tabellen har nullable `event_id`
- [ ] Verifiera att bonus mat sparas med `event_id = NULL`
- [ ] Verifiera att task-mat sparas med `event_id` satt till event-ID

### 7.2 API Endpoints
- [ ] Testa `POST /api/v1/xp/members/{memberId}/bonus` → ska skapa food items
- [ ] Testa `GET /api/v1/pets/collected-food` → ska returnera bonus mat
- [ ] Testa `POST /api/v1/pets/feed` → ska ge XP och markera mat som matad

### 7.3 Felhantering
- [ ] Testa att ge mat utan device token → ska ge fel
- [ ] Testa att ge mat till vuxen → ska ge fel
- [ ] Testa att ge mat till barn i annan familj → ska ge fel

---

## 8. Performance & Edge Cases

### 8.1 Stora mängder
- [ ] Ge 100 mat i en gång
- [ ] Verifiera att allt sparas korrekt
- [ ] Verifiera att UI hanterar stora antal korrekt

### 8.2 Samtidiga operationer
- [ ] Ge mat från vuxen vy
- [ ] Samtidigt, mata djuret från barn vy
- [ ] Verifiera att inga race conditions uppstår

### 8.3 Nätverksfel
- [ ] Simulera nätverksfel när man ger mat
- [ ] Verifiera att felmeddelande visas
- [ ] Verifiera att ingen mat skapas om request misslyckas

---

## 9. Regression Testing

### 9.1 Befintlig funktionalitet
- [ ] Verifiera att task completion fortfarande fungerar
- [ ] Verifiera att task uncompletion fortfarande fungerar
- [ ] Verifiera att pet growth stages fortfarande fungerar
- [ ] Verifiera att XP-systemet fortfarande fungerar

### 9.2 Cross-browser
- [ ] Testa i Chrome
- [ ] Testa i Safari
- [ ] Testa i Firefox
- [ ] Testa på mobil (iOS)
- [ ] Testa på mobil (Android)

---

## 10. Sammanfattning

### Kritiska testfall (måste fungera)
1. ✅ Ge bonus mat från vuxen vy
2. ✅ Visa mat på barnets dashboard
3. ✅ Mata djuret med bonus mat
4. ✅ Mat persisterar efter reload
5. ✅ Level up fungerar med bonus mat

### Viktiga testfall (bör fungera)
1. ✅ Kombinera bonus mat med task-mat
2. ✅ Uncompleta tasks med bonus mat
3. ✅ Visuell feedback (confetti, floating numbers)
4. ✅ Pet mood uppdateras korrekt

### Nice-to-have testfall
1. ✅ Edge cases och felhantering
2. ✅ Performance med stora mängder
3. ✅ Cross-browser kompatibilitet

---

## Noteringar

- **Datum för test:** ___________
- **Testat av:** ___________
- **Resultat:** ___________
- **Kända problem:** ___________
