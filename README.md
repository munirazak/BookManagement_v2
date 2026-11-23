# BookManagement

Spring Boot service that owns the book catalog (`book_detail`, `book_copy`).

**Maven:** `com.kopibru:BookManagement`  
**Port:** `8081`  
**Database:** `book_catalogue`

IndexShelf calls these APIs over HTTP for list / borrow / return (it no longer accesses `book_copy` directly).

## Run

### Option A — Local (Maven)

```bash
# Dev profile (default) — requires local PostgreSQL
mvn spring-boot:run

# Prod profile
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### Option B — Docker Compose (app → Neon)

```bash
docker compose up --build
```

This starts BookManagement on port `8081` against Neon PostgreSQL.
Default profile is **`dev`**. Neon host/password are **not** in properties files — set them via `.env` or Secret Manager.

```bash
cp .env.example .env   # then edit NEON_HOST / DB_PASSWORD
docker compose up --build

# Production Neon branch
SPRING_PROFILES_ACTIVE=prod docker compose up --build
```

On Windows PowerShell:

```powershell
Copy-Item .env.example .env
$env:SPRING_PROFILES_ACTIVE="prod"; docker compose up --build
```

Stop with `docker compose down`.

Base URL: `http://localhost:8081`

### Port and profile (local vs prod)

| Setting | Local default | Production |
|---------|---------------|------------|
| Port | `8081` (`PORT` unset) | Set env `PORT` (Cloud Run sets this, often `8080`) |
| Profile | `dev` | Set env `SPRING_PROFILES_ACTIVE=prod` |
| Database | Neon (via `NEON_HOST`) | Neon (via `NEON_HOST`) |

`application.properties` uses:

```properties
server.port=${PORT:8081}
spring.profiles.active=${SPRING_PROFILES_ACTIVE:dev}
```

Datasource env vars (dev + prod) — never commit real values:

| Env | Purpose | Example |
|-----|---------|---------|
| `NEON_HOST` | Neon pooler hostname only | `ep-....neon.tech` |
| `DB_NAME` | Database name | `book_catalogue` (default) |
| `DB_USERNAME` | DB user | `neondb_owner` (default) |
| `DB_PASSWORD` | DB password | from Secret Manager / `.env` |
| `DB_URL` | Optional full JDBC URL override | if set, replaces the URL built from `NEON_HOST` |

On Cloud Run, configure:

- `SPRING_PROFILES_ACTIVE=prod`
- `NEON_HOST` / `DB_USERNAME` / `DB_PASSWORD` (from Secret Manager)

Do **not** put Neon host or password in committed properties files.

### Neon PostgreSQL

JDBC URL is built from env (when `DB_URL` is unset):

```text
jdbc:postgresql://${NEON_HOST}/${DB_NAME}?sslmode=require&channelBinding=require
```

Create the `book_catalogue` database in the Neon console before the first deploy.
`SchemaBootstrap` skips `CREATE DATABASE` for Neon hosts and only applies `schema.sql`.

Docker Compose runs the app only (no local Postgres container). Copy `.env.example` → `.env` and set `NEON_HOST` / `DB_PASSWORD`.

### CI/CD (GitHub Actions)

Declarative pipeline in `.github/workflows/ci.yml`:

1. On push/PR to `main`: run `mvn verify` (tests + coverage gate)
2. On push to `main`: build and publish the Docker image to GitHub Container Registry (`ghcr.io`)

Pull the published image (after a successful push to `main`):

```bash
docker pull ghcr.io/<your-github-username>/bookmanagement:latest
```

## APIs

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/books` | Register book copy |
| `POST` | `/api/books/from-file` | Import from `.txt` / `.dat` |
| `GET` | `/api/books?status=all\|available\|borrowed` | List books |
| `POST` | `/api/books/{bookId}/borrow` | Borrow (`{"libraryId":"..."}`) |
| `POST` | `/api/books/{bookId}/return` | Return (`{"libraryId":"..."}`) |

### Register book
```bash
curl -X POST http://localhost:8081/api/books \
  -H "Content-Type: application/json" \
  -d '{"id":"BOOK-001","isbn":"978-0134685991","title":"Effective Java","author":"Joshua Bloch"}'
```

### Import from file
```bash
curl -X POST http://localhost:8081/api/books/from-file \
  -F "file=@src/main/resources/sample-books.txt"
```

Duplicates are skipped; other books still register. Response shape:

```json
{
  "registered": [ { "id": "BOOK-002", "...": "..." } ],
  "errors": [ "Book copy with id 'BOOK-001' already exists" ]
}
```

### Borrow / return (used by IndexShelf)
```bash
curl -X POST http://localhost:8081/api/books/BOOK-001/borrow \
  -H "Content-Type: application/json" \
  -d '{"libraryId":"LIB-001"}'
```
