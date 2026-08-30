# FamilyApp – Claude Instructions

## Project Purpose
Family app to motivate children to do daily tasks. Kids earn XP from completing tasks, which feeds a monthly pet animal that levels up as it grows. Parents manage tasks, family members, and children's digital wallet.

## Platforms
- **Web app** (`frontend/` + `backend/`) – most feature-complete, the reference implementation
- **Android app** (`android/KidQuest/`) – Kotlin + Jetpack Compose, shares same backend
- **iOS app** (`ios/`) – in early stages, goal is feature parity with Android

## Tech Stack

### Backend (`backend/`)
- Java 21, Spring Boot
- MySQL, Flyway migrations (ALL schema changes via Flyway – no manual DB changes)
- REST API versioned at `/api/v1/...`
- Clean architecture layers: `domain` / `application`/`service` / `infrastructure` / `api`/`web`
- Deployed on Railway

### Frontend (`frontend/`)
- React + TypeScript + Vite
- Feature-based folder structure: `src/features/`, `src/shared/`
- Functional components + hooks only, no class components
- No `any` in TypeScript
- Mobile-first design (320–430px primary targets)

### Android (`android/KidQuest/`)
- Kotlin, Jetpack Compose, MVVM
- Retrofit + OkHttp for API calls
- Device token stored in DataStore, encrypted first with an Android Keystore key (`session/SessionCrypto.kt`) and excluded from auto-backup
- Same backend as web, auth via `X-Device-Token` header

### iOS (`ios/`)
- Goal: copy design and functionality from Android app

## Architecture Rules
- Flyway for ALL DB schema changes – never modify DB manually
- No hardcoded secrets – use environment variables
- Passwords must be hashed (BCrypt), never logged
- API: structured error responses, input validation on both frontend and backend
- Commits: descriptive messages (what and why), feature branches for larger changes

## Key Features (Web)
- Todo lists with drag-and-drop
- Calendar with recurring events; tasks are calendar events with XP
- XP system + pet/animal system for children (monthly animal, 5 growth stages)
- QR code family invites
- Children's digital wallet (allowance, savings goals, expenses)
- Menstrual cycle tracking (parents only, private)
- Password-based auth for adults, device token for children

## Auth Model
- Adults: email + password → device token stored in browser/app
- Children: device token only (generated via QR code by parent)
- API auth: `X-Device-Token` header

## Backend API Base URLs
- Production: `https://backend-production-5c57.up.railway.app/api/v1`
- Local (emulator): `http://10.0.2.2:8080/api/v1`
- Local (web): `http://localhost:8080`

## Local Development
```bash
docker compose up --build   # starts backend + frontend + MySQL
```
Frontend dev (no Docker): `cd frontend && npm install && npm run dev` → http://localhost:5173

## Important Patterns
- All DB migrations in `backend/src/main/resources/db/migration/`
- Frontend API clients in `frontend/src/shared/api/`
- Keep domain logic in service layer, not in controllers or UI
- Shared logic in hooks (`useSomething`), not duplicated across components
- Mobile-first: test at 320px and 390px widths

## Current Focus
- iOS app development – copying design and functionality from Android app
