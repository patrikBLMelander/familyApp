# Deployment Guide för Railway

## Steg 1: Förberedelser

### 1.1 Skapa GitHub Repository

1. Gå till [GitHub](https://github.com) och skapa ett nytt repository
2. Namn: `familyApp` (eller valfritt namn)
3. Välj **Private** om du vill hålla det privat
4. **Inte** initialisera med README, .gitignore eller license (vi har redan dessa)

### 1.2 Pusha till GitHub

```bash
# Om du inte redan har git initierat
git init
git add .
git commit -m "Initial commit"

# Lägg till ditt GitHub repository som remote
git remote add origin https://github.com/DITT-ANVÄNDARNAMN/familyApp.git

# Pusha till GitHub
git branch -M main
git push -u origin main
```

## Steg 2: Railway Setup

### 2.1 Skapa Railway-konto

1. Gå till [Railway](https://railway.app)
2. Logga in med GitHub
3. Klicka på "New Project"
4. Välj "Deploy from GitHub repo"
5. Välj ditt `familyApp` repository

### 2.2 Skapa MySQL Database

1. I Railway-projektet, klicka på "+ New"
2. Välj "Database" → "Add MySQL"
3. Railway skapar automatiskt en MySQL-databas
4. **Spara connection string** (du behöver den senare)

### 2.3 Konfigurera Backend Service

1. I Railway-projektet, klicka på "+ New"
2. Välj "GitHub Repo" → Välj ditt repository
3. Railway kommer automatiskt upptäcka `backend/Dockerfile`
4. Sätt **Root Directory** till `backend` (under Settings → Root Directory)
5. Konfigurera följande miljövariabler (under Variables):

```
SPRING_DATASOURCE_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
SPRING_DATASOURCE_USERNAME=${{MySQL.MYSQLUSER}}
SPRING_DATASOURCE_PASSWORD=${{MySQL.MYSQLPASSWORD}}
SERVER_PORT=8080
CORS_ALLOWED_ORIGINS=https://<ditt-frontend-service>.railway.app
```

**Viktigt**: 
- Railway använder `${{ServiceName.VARIABLE}}` för att referera till andra services variabler
- Om din MySQL-service heter något annat än "MySQL", ersätt "MySQL" med rätt service-namn
- Ersätt `<ditt-frontend-service>` med ditt faktiska frontend-service namn (du får detta efter att frontend är deployad)
- Du hittar frontend-URL:en i Railway under frontend-service:ns "Settings" → "Domains"

### 2.4 Konfigurera Frontend Service

1. I Railway-projektet, klicka på "+ New"
2. Välj "GitHub Repo" → Välj samma repository
3. Railway kommer automatiskt upptäcka `frontend/Dockerfile`
4. Sätt **Root Directory** till `frontend` (under Settings → Root Directory)
5. Konfigurera följande miljövariabler (under Variables):

```
VITE_API_BASE_URL=https://<ditt-backend-service>.railway.app/api/v1
```

**Viktigt**: 
- Ersätt `<ditt-backend-service>` med ditt faktiska backend-service namn
- Du hittar backend-URL:en i Railway under backend-service:ns "Settings" → "Domains"
- Backend-URL:en ser ut som: `https://backend-production-xxxx.up.railway.app`

### 2.5 Konfigurera Root Directory (för båda services)

Railway behöver veta var Dockerfile:erna finns:

**Backend Service:**
- Gå till backend-service:ns "Settings"
- Under "Root Directory", sätt: `backend`

**Frontend Service:**
- Gå till frontend-service:ns "Settings"
- Under "Root Directory", sätt: `frontend`

## Steg 3: Deployment

### 3.1 Automatisk Deployment

Railway deployar automatiskt när du pushar till `main` branch.

### 3.2 Manuell Deployment

1. Gå till ditt service i Railway
2. Klicka på "Deploy" → "Redeploy"

## Steg 4: Verifiera Deployment

1. Öppna frontend-URL:en från Railway
2. Testa att registrera en ny familj
3. Testa att logga in
4. Verifiera att allt fungerar

## Troubleshooting

### Backend startar inte

- Kontrollera att MySQL-connection string är korrekt
- Kontrollera att miljövariablerna är satta korrekt
- Kolla logs i Railway dashboard
- Verifiera att Root Directory är satt till `backend`

### Frontend kan inte nå backend

- Kontrollera att `VITE_API_BASE_URL` är korrekt
- Kontrollera CORS-inställningar i backend (`CORS_ALLOWED_ORIGINS`)
- Verifiera att backend är igång
- Verifiera att Root Directory är satt till `frontend`

### Database connection errors

- Kontrollera att MySQL-service är igång
- Verifiera att connection string använder Railway's variabler korrekt
- Kontrollera att användarnamn och lösenord är korrekta

### Build errors

- Kontrollera att Dockerfile:erna är korrekta
- Verifiera att Root Directory är satt korrekt för varje service
- Kolla build logs i Railway dashboard
