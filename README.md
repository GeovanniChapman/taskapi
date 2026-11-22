# Task API

A RESTful backend for managing personal tasks. Users register, authenticate with a JWT, and manage their own tasks. Each task is scoped to its owner, and updates use optimistic locking to prevent lost writes.

## Features

- User registration and login with BCrypt-hashed passwords
- Stateless JWT authentication (HS256) with a 15-minute token lifetime
- Full CRUD for tasks, scoped to the authenticated user
- Paginated task listing
- Optimistic locking on updates (`version` field)
- Input validation with structured error responses
- Database schema versioning with Liquibase
- Local PostgreSQL environment via Docker Compose

## Tech Stack

| Area            | Technology                                              |
|-----------------|---------------------------------------------------------|
| Language        | Java 21                                                 |
| Framework       | Spring Boot 4.2.0                                       |
| Web             | Spring Web MVC (`spring-boot-starter-webmvc`)           |
| Persistence     | Spring Data JPA, Hibernate                              |
| Database        | PostgreSQL 16                                           |
| Migrations      | Liquibase                                               |
| Security        | Spring Security, OAuth2 Resource Server, JWT (Nimbus)   |
| Validation      | Jakarta Bean Validation                                 |
| Build           | Maven (with the Maven Wrapper)                          |
| Infrastructure  | Docker Compose                                          |

## Prerequisites

- [JDK 21](https://adoptium.net/)
- [Docker](https://www.docker.com/) and Docker Compose
- `openssl` (used by `start.sh` to generate the JWT secret)

The Maven Wrapper (`./mvnw`) is included, so a local Maven installation is not required.

## Getting Started

1. **Start the database**

   ```bash
   docker compose up -d --wait
   ```

   This starts PostgreSQL 16 on `127.0.0.1:5432` with the database `taskdb`.

2. **Run the application**

   ```bash
   ./start.sh
   ```

   The script generates a random `JWT_SECRET` and starts the application with `./mvnw spring-boot:run`. Liquibase applies the database migrations automatically on startup.

   The API is available at `http://localhost:8080`.

   > **Note:** Because `start.sh` generates a new secret on every run, tokens issued by a previous run become invalid after a restart. To keep tokens valid across restarts, export a fixed `JWT_SECRET` yourself (a Base64-encoded value of at least 32 bytes) and run `./mvnw spring-boot:run` directly.

   On Windows, set `JWT_SECRET` in your environment and run `mvnw.cmd spring-boot:run`.

3. **Run the tests**

   ```bash
   ./mvnw test
   ```

   The test suite includes a Spring context test, so the PostgreSQL container must be running. The test supplies its own `app.jwt.secret`, so `JWT_SECRET` does not need to be set.

## Configuration

Configuration is read from `src/main/resources/application.yml`. Every value can be overridden with an environment variable.

| Variable       | Default                                      | Description                                                                 |
|----------------|----------------------------------------------|-----------------------------------------------------------------------------|
| `DB_URL`       | `jdbc:postgresql://localhost:5432/taskdb`    | JDBC connection URL                                                         |
| `DB_USER`      | `taskapp`                                    | Database user                                                               |
| `DB_PASSWORD`  | `local-dev-password`                         | Database password                                                           |
| `JWT_SECRET`   | *None (required)*                            | Base64-encoded HMAC key, at least 32 bytes after decoding. The app will not start without it. |

Other settings:

- **JWT issuer:** `task-api`
- **JWT audience:** `task-api-clients`
- **CORS origins:** `http://localhost:3000`, `http://localhost:5173`
- **Error details:** Error messages and stack traces are not exposed in responses (`include-message: never`).

To generate a secret manually:

```bash
openssl rand -base64 32
```

## API Reference

All endpoints return JSON. Errors follow the [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details format.

### Authentication

| Method | Endpoint             | Auth | Description                     |
|--------|----------------------|------|---------------------------------|
| `POST` | `/api/auth/register` | No   | Create a new user account       |
| `POST` | `/api/auth/login`    | No   | Authenticate and receive a JWT  |

**Register**

```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "jane@example.com",
  "password": "a-secure-passphrase"
}
```

- `email`: required, valid email, max 254 characters. Stored in lowercase.
- `password`: required, 12 to 72 characters (72 UTF-8 bytes maximum).

Response: `201 Created`

```json
{
  "id": "3f6c1b2e-8a4d-4f0e-9c1a-2b7d5e8f9a10",
  "email": "jane@example.com"
}
```

**Login**

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "jane@example.com",
  "password": "a-secure-passphrase"
}
```

Response: `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Invalid credentials return `401 Unauthorized`.

### Tasks

All task endpoints require a valid bearer token:

```http
Authorization: Bearer <accessToken>
```

Users can only access tasks they own. Requests for another user's task return `404 Not Found`.

| Method   | Endpoint           | Description                          | Success |
|----------|--------------------|--------------------------------------|---------|
| `POST`   | `/api/tasks`       | Create a task                        | `201`   |
| `GET`    | `/api/tasks`       | List your tasks (paginated)          | `200`   |
| `GET`    | `/api/tasks/{id}`  | Get a task by ID                     | `200`   |
| `PUT`    | `/api/tasks/{id}`  | Update a task (requires `version`)   | `200`   |
| `DELETE` | `/api/tasks/{id}`  | Delete a task                        | `204`   |

**Create a task**

```json
{
  "title": "Write project documentation",
  "completed": false
}
```

- `title`: required, max 200 characters
- `completed`: required

The response includes a `Location` header pointing to the new resource.

```json
{
  "id": "9b1e2c3d-4f5a-4b6c-8d7e-0f1a2b3c4d5e",
  "title": "Write project documentation",
  "completed": false,
  "version": 0
}
```

**List tasks**

```http
GET /api/tasks?page=0&size=20
```

- `page`: zero-based page index, default `0`
- `size`: page size, default `20`, range `1` to `100`

Results are sorted by `id` ascending.

```json
{
  "content": [
    {
      "id": "9b1e2c3d-4f5a-4b6c-8d7e-0f1a2b3c4d5e",
      "title": "Write project documentation",
      "completed": false,
      "version": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

**Update a task**

Updates use optimistic locking. Send the `version` you last read. If the task has changed since then, the request returns `409 Conflict` and you must reload the task before retrying.

```json
{
  "title": "Write project documentation",
  "completed": true,
  "version": 0
}
```

## Error Responses

| Status | When                                                              |
|--------|-------------------------------------------------------------------|
| `400`  | Request validation failed. The `errors` property maps each field to its message. |
| `401`  | Missing, invalid, or expired token, or invalid login credentials  |
| `404`  | Task not found, or not owned by the authenticated user            |
| `409`  | Version mismatch on update, or a conflicting data change          |

Example validation error:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
        "detail": "Request validation failed",
  "errors": {
    "email": "must be a well-formed email address"
  }
}
```

## Quick Start with cURL

```bash
# 1. Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"a-secure-passphrase"}'

# 2. Log in and capture the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jane@example.com","password":"a-secure-passphrase"}' \
  | jq -r '.accessToken')

# 3. Create a task
curl -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Write project documentation","completed":false}'

# 4. List tasks
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/tasks?page=0&size=20"
```

## Project Structure

```text
src/main/java/com/example/taskapi/
├── TaskApiApplication.java   # Application entry point
├── auth/                     # Security configuration, JWT encoder/decoder, CORS
├── controller/               # REST controllers (auth and tasks)
├── dto/                      # Request and response records
├── entity/                   # JPA entities (AppUser, Task)
├── exception/                # Global exception handling
├── repository/               # Spring Data JPA repositories
└── service/                  # Business logic (AuthService, TaskService)

src/main/resources/
├── application.yml           # Application configuration
└── db/changelog/             # Liquibase changelogs and SQL migrations
```

## Database Schema

Managed by Liquibase. Migrations run automatically at startup, and Hibernate runs with `ddl-auto: validate`.

- **`app_users`**: `id` (UUID, PK), `email` (unique), `password_hash`
- **`tasks`**: `id` (UUID, PK), `owner_id` (FK to `app_users.id`), `title`, `completed`, `version`
  - Index on `(owner_id, id)`

## Security Notes

- Passwords are hashed with a delegating password encoder (BCrypt by default).
- The API is stateless (no server-side sessions). CSRF protection is disabled because it does not apply to token-based clients.
- Tokens are signed with HMAC-SHA256 and validated for issuer, audience, and subject.
- `JWT_SECRET` has no default and must be supplied at runtime. Keep it and `DB_PASSWORD` out of source control. The `DB_PASSWORD` default is for local development only, so override it before deploying.
