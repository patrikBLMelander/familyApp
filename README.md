# FamilyApp

En familjeapplikation för att hantera to-do listor, dagliga sysslor och scheman för hela familjen.

## Teknisk stack

- **Backend**: Java Spring Boot
- **Frontend**: React + TypeScript + Vite
- **Database**: MySQL
- **Migrations**: Flyway
- **Containerization**: Docker & Docker Compose
- **Deployment**: Railway

## Funktioner

- ✅ To-do listor med drag-and-drop
- ✅ Kalender med events och återkommande händelser
- ✅ Dagliga sysslor (hanteras via kalendern med XP-system för barn)
- ✅ QR-kod inbjudningar för familjemedlemmar
- ✅ Privata listor
- ✅ Färgteman för listor
- ✅ XP-system och djur för barn (motivation för att göra sysslor)

## Lokal utveckling

### Förutsättningar

- Docker & Docker Compose
- Node.js 22+ (för frontend-utveckling utan Docker)

### Starta med Docker

```bash
docker compose up --build
```

Backend: http://localhost:8080
Frontend: http://localhost:3000
Database: localhost:3306

### Frontend-utveckling (utan Docker)

```bash
cd frontend
npm install
npm run dev
```

Frontend kommer köra på http://localhost:5173

## Deployment på Railway

Se [DEPLOYMENT.md](./DEPLOYMENT.md) för detaljerade instruktioner.

## Projektstruktur

```
familyApp/
├── backend/          # Spring Boot backend
├── frontend/         # React frontend
├── docker-compose.yml
└── README.md
```

