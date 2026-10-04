# Servicedesk

A full-stack IT support ticketing platform — part of the TaskForge project. Requesters log tickets, agents triage and resolve them against SLA deadlines, and managers/admins get reporting and configuration tools, all behind role-based access control.

## Features

- **Ticketing** — create, assign, and track tickets through a status lifecycle (New → Assigned → In Progress → Pending/Resolved → Closed, with a 14-day reopen window), scoped to queues and categories.
- **SLA tracking** — first-response and resolution deadlines computed per priority, with a scheduled sweep job that flags warnings (80% of budget consumed) and breaches.
- **Comments & attachments** — threaded ticket comments (public or internal-only), file attachments with content-type validation and size limits, backed by S3-compatible object storage.
- **Notifications** — queued email notifications (ticket created/assigned/resolved, new comments) sent by a background job with retry/backoff.
- **Role-based access** — Requester, Agent, Manager, and Admin roles enforced on both API and UI, with JWT access/refresh tokens and login rate limiting.
- **Reporting dashboard** — ticket volume by status/priority, average first-response and resolution time, SLA compliance, and agent workload, for managers and admins.
- **Admin console** — manage queues, categories, SLA policies, and user accounts/roles without touching the database.
- **Audit trail** — every status change, assignment, and comment is recorded against the ticket.

## Tech stack

- **Backend**: Java 21, Spring Boot 3.3, Spring Security (JWT), Spring Data JPA, PostgreSQL, Flyway, ShedLock, AWS S3 SDK (MinIO-compatible), Maven, Spotless, JaCoCo
- **Frontend**: Angular 21 (standalone components, SSR via Express), Chart.js, Bootstrap, Vitest
- **Infrastructure**: Docker / Docker Compose, PostgreSQL, MinIO, MailHog (local dev mail catcher)
- **CI**: GitHub Actions — lint (Spotless), build & test with coverage gate, Snyk dependency scanning, Docker image build & push to GHCR

## Structure

```
backend/    Spring Boot API (com.tcc.servicedesk)
frontend/   Angular SPA (SSR-enabled)
docs/       Project documentation
```

## Getting started

### Prerequisites

- Java 21, Node.js 22, Docker

### Option A — Docker Compose (everything at once)

```bash
cp .env.example .env
# edit .env: set a real JWT_SECRET (32+ random bytes, base64-encoded)
docker compose up --build
```

This starts PostgreSQL, MinIO, MailHog, the backend API (`:8080`), and the frontend (`:4200`).

### Option B — run backend and frontend separately

1. Start the supporting services only:
   ```bash
   docker compose up postgres mailhog minio minio-init
   ```
2. Backend:
   ```bash
   cd backend
   ./mvnw spring-boot:run
   ```
   Runs with the `local` Spring profile against `localhost` service URLs (see `application-local.yml`).
3. Frontend:
   ```bash
   cd frontend
   npm install
   npm start
   ```
   Serves on `http://localhost:4200` and proxies API calls to `http://localhost:8080`.

### Useful local ports

| Service      | Port       |
|--------------|------------|
| Frontend     | 4200       |
| Backend API  | 8080       |
| PostgreSQL   | 5432       |
| MinIO API / Console | 9000 / 9001 |
| MailHog UI / SMTP   | 8025 / 1025 |

## Testing

```bash
# Backend: unit + integration tests, coverage gate, formatting check
cd backend && ./mvnw verify

# Frontend: unit tests
cd frontend && npm test
```

## License

No license has been set for this project yet — all rights reserved by default until one is added.
