# Railway Setup - Snabbguide

## Steg 1: Skapa MySQL Database

1. I Railway-projektet, klicka på "+ New"
2. Välj "Database" → "Add MySQL"
3. Vänta tills databasen är klar

## Steg 2: Skapa Backend Service

1. Klicka på "+ New" → "GitHub Repo" → Välj `familyApp`
2. Gå till **Settings** → **Root Directory** → Sätt till: `backend`
3. Gå till **Variables** och lägg till:

```
SPRING_DATASOURCE_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=${{MySQL.MYSQLUSER}}
SPRING_DATASOURCE_PASSWORD=${{MySQL.MYSQLPASSWORD}}
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=https://<frontend-url>.railway.app
```

**OBS**: 
- Ersätt `MySQL` med ditt faktiska MySQL-service namn om det är annorlunda
- Lämna `CORS_ALLOWED_ORIGINS` tomt först, uppdatera efter att frontend är deployad

4. Vänta tills backend är deployad
5. Kopiera backend-URL:en från **Settings** → **Domains** (t.ex. `https://backend-production-xxxx.up.railway.app`)

## Steg 3: Skapa Frontend Service

1. Klicka på "+ New" → "GitHub Repo" → Välj `familyApp`
2. Gå till **Settings** → **Root Directory** → Sätt till: `frontend`
3. Gå till **Variables** och lägg till:

```
VITE_API_BASE_URL=https://<backend-url>/api/v1
```

**OBS**: Ersätt `<backend-url>` med backend-URL:en från steg 2 (utan `/api/v1` i slutet)

4. Vänta tills frontend är deployad
5. Kopiera frontend-URL:en från **Settings** → **Domains** (t.ex. `https://frontend-production-xxxx.up.railway.app`)

## Steg 4: Uppdatera CORS

1. Gå tillbaka till **Backend Service** → **Variables**
2. Uppdatera `CORS_ALLOWED_ORIGINS` med frontend-URL:en från steg 3:
   ```
   CORS_ALLOWED_ORIGINS=https://frontend-production-xxxx.up.railway.app
   ```
3. Backend kommer automatiskt redeploya

## Steg 5: Testa

1. Öppna frontend-URL:en i webbläsaren
2. Testa att registrera en ny familj
3. Testa att logga in
4. Verifiera att allt fungerar!

## Troubleshooting

### Backend kan inte ansluta till MySQL

- Kontrollera att MySQL-service:ns namn matchar i `${{MySQL.MYSQLHOST}}` etc.
- Om MySQL heter något annat (t.ex. "mysql"), ändra till `${{mysql.MYSQLHOST}}`
- Kolla backend logs i Railway dashboard

### Frontend kan inte nå backend

- Verifiera att `VITE_API_BASE_URL` är korrekt (ska sluta med `/api/v1`)
- Kontrollera CORS-inställningar i backend
- Kolla browser console för felmeddelanden

### Build errors

- Verifiera att Root Directory är satt korrekt (`backend` resp. `frontend`)
- Kolla build logs i Railway dashboard

