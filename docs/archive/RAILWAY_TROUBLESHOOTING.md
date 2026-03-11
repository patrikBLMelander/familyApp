# Railway Troubleshooting - VITE_API_BASE_URL

## Problem: Frontend anropar fortfarande localhost:8080

### Lösning 1: Kontrollera Railway Build Logs

1. Gå till frontend-service → **Deployments**
2. Klicka på den senaste deploymenten
3. Kolla build logs - leta efter: `Building with VITE_API_BASE_URL=...`
4. Om du ser `Building with VITE_API_BASE_URL=` (tomt), betyder det att variabeln inte är tillgänglig vid build-time

### Lösning 2: Använd Railway's Build Command (Om variabeln inte är tillgänglig)

Om environment variables inte är tillgängliga vid build-time i Railway, kan vi använda Railway's build settings:

1. Gå till frontend-service → **Settings**
2. Under **Build Command**, sätt:
   ```
   VITE_API_BASE_URL=https://backend-production-d012.up.railway.app/api/v1 npm run build
   ```
3. Men detta fungerar inte med Dockerfile builds...

### Lösning 3: Använd Railway's Nixpacks (Istället för Dockerfile)

Railway kan automatiskt upptäcka Vite-projekt och använda Nixpacks, vilket hanterar environment variables bättre:

1. Ta bort eller döp om `frontend/Dockerfile` till `frontend/Dockerfile.backup`
2. Railway kommer automatiskt använda Nixpacks för Vite-projekt
3. Environment variables kommer då vara tillgängliga vid build-time

### Lösning 4: Använd Runtime Environment Variables (Workaround)

Om build-time variabler inte fungerar, kan vi läsa från window.location i runtime:

Men detta kräver ändringar i koden och är inte idealiskt.

## Rekommenderad lösning

**Använd Nixpacks istället för Dockerfile för frontend:**

1. Döp om `frontend/Dockerfile` till `frontend/Dockerfile.backup`
2. Railway kommer automatiskt upptäcka att det är ett Vite-projekt
3. Railway kommer använda Nixpacks som hanterar environment variables korrekt
4. Redeploy frontend

