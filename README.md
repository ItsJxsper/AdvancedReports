# AdvancedReports

> A modular, multi-server Minecraft report system — PaperMC Plugin, Velocity Proxy, Spring Boot Backend and a Discord
> Bot, tied together via REST and RabbitMQ.

```
Plugin (Paper)  ──REST──▶  Spring Boot Backend  ──RabbitMQ──▶  Discord Bot
      ▲                          │      │                          │
      │                          │      └──▶ Redis (rate limits)   │
      │                          └──▶ PostgreSQL   │  AWS S3        │
      └──────────────────────────┴───────────────────────────────────┘
                       RabbitMQ Update Notifications (+ DLQ)
```

> **Status:** This project is under active development on the `develop` branch. The `common` and `backend` modules
> already contain real code; `plugin`, `proxy`, `api` and `discord-bot` are currently module skeletons (build files
> only,
> no sources yet) and are being built out next.

---

## What is this?

**AdvancedReports** is a monorepo for a Minecraft player-reporting system built around a clean **REST vs. RabbitMQ**
split:

- **REST API** → send and receive *objects* (create a report, fetch data, update status, upload a screenshot)
- **RabbitMQ** → fire lightweight *notifications* after a database write completes (new report ping, status change
  alert, screenshot ready)

The system is designed for **multi-server networks**: every server registers itself against the backend and gets its own
UUID, which is used both for identifying where a report came from and for per-server rate limiting.

---

## Monorepo layout

The project is a single Gradle build with 6 modules:

| Module        | Purpose                                                                  | Status                                |
|---------------|--------------------------------------------------------------------------|---------------------------------------|
| `common`      | Shared DTOs, enums and exceptions used by every other module             | ✅ Implemented                        |
| `backend`     | Spring Boot REST API, persistence, rate limiting, RabbitMQ publishing    | ✅ Implemented                        |
| `api`         | Shared client-facing API library (depends on OkHttp, Jackson, Paper API) | 🚧 Skeleton (build file only)         |
| `plugin`      | PaperMC plugin (in-game commands, admin GUI)                             | 🚧 Skeleton (build file only)         |
| `proxy`       | Velocity proxy plugin, for network-wide functionality                    | 🚧 Skeleton (resources/template only) |
| `discord-bot` | Discord bot module                                                       | 🚧 Skeleton (no dependencies yet)     |

All published modules are versioned and published to GitHub Packages under the `de.itsjxsper` group.

---

## Tech Stack

| Layer            | Technology                                                                       |
|------------------|----------------------------------------------------------------------------------|
| Minecraft Plugin | PaperMC API (targeting Minecraft 26.2), Java 25 toolchain                        |
| Proxy            | Velocity API 3.5.0-SNAPSHOT                                                      |
| Backend          | Spring Boot 4.1, Spring Web MVC, Spring Data JPA, Spring AMQP, Spring Data Redis |
| API docs         | springdoc-openapi (Swagger UI)                                                   |
| Rate limiting    | Bucket4j + Redis (Lettuce-based distributed buckets)                             |
| Message Broker   | RabbitMQ (Fanout exchange + Dead Letter Queue)                                   |
| Database         | PostgreSQL (via Spring Data JPA)                                                 |
| File Storage     | AWS S3 (optional, active only under the `s3` Spring profile)                     |
| Object Mapping   | MapStruct                                                                        |
| Build            | Gradle (Kotlin DSL), version catalog (`gradle/libs.versions.toml`)               |
| Local dev stack  | Docker Compose (via Spring Boot's `spring-boot-docker-compose`)                  |

---

## Architecture

```
┌───────────────────────────────────────────────────────────┐
│                     Minecraft Network                     │
│                                                             │
│   Paper Plugin(s)  ──REST──▶  Backend                       │
│   Velocity Proxy   ──REST──▶  Backend                       │
│   (per-server identification via X-Server-UUID header)     │
└──────────────────────────┬──────────────────────────────────┘
                            │ HTTP (REST, /api/v1/**)
                            ▼
┌───────────────────────────────────────────────────────────┐
│                    Spring Boot Backend                     │
│                                                             │
│  Controllers → Services → Repositories → PostgreSQL         │
│      │                        │                             │
│      │                        └──▶ MapStruct DTO mapping    │
│      │                                                      │
│      ├──▶ Rate Limit Aspect (@RateLimited) → Redis/Bucket4j │
│      ├──▶ Screenshot Service → AWS S3 (profile: s3)         │
│      └──▶ after successful DB write ──▶ RabbitMQ publish    │
└──────────────────────────┬──────────────────────────────────┘
                            │ AMQP
                            ▼
┌───────────────────────────────────────────────────────────┐
│                      RabbitMQ Broker                       │
│                                                              │
│  Exchange: reports.notify (Fanout, durable)                 │
│   ├── Queue: notify.plugin                                   │
│   └── Queue: notify.discord ──(on failure)──▶ reports.dlx    │
│                                     └──▶ notify.discord.dlq   │
└──────────────┬──────────────────────────────────────────────┘
               │
       ┌───────┴────────┐
       ▼                 ▼
 Plugin Consumer     Discord Bot
 (admin ping,        (post embed,
  chat notify)        buttons → REST PATCH)
```

Key points reflected in the current code:

- The `notify.discord` queue is bound with a **dead-letter exchange** (`reports.dlx` → `notify.discord.dlq`), so failed
  Discord notifications aren't silently lost.
- RabbitMQ messages use Jackson-based JSON conversion (`JacksonJsonMessageConverter`) and the `RabbitTemplate` is
  configured as **mandatory**, so unroutable messages are surfaced rather than dropped.
- Screenshot storage via S3 is **opt-in**: `S3Config` only activates under the `s3` Spring profile and only creates the
  `S3Client` bean if `aws.s3.bucket` is configured.

---

## REST API

Base URL: `http://localhost:8080/api/v1`
Interactive Swagger UI is available once the backend is running (via `springdoc-openapi`).

### Authentication & rate limiting

Instead of a single API key header, endpoints are protected with **per-caller rate limiting** using request headers.
Each controller method is annotated with `@RateLimited`, which independently checks whichever headers are relevant:

| Header          | Used for                       |
|-----------------|--------------------------------|
| `X-Server-UUID` | Per-server rate limiting       |
| `X-Player-UUID` | Per-player rate limiting       |
| `X-Discord-ID`  | Per-Discord-user rate limiting |

If a required header is missing, the backend returns `400` with error code `MISSING_HEADER`. If the caller exceeds their
bucket, it returns `429 Too Many Requests` with error code `RATE_LIMIT_EXCEEDED`. Default limits (configurable via
`rate-limit.*` properties):

| Caller type  | Default requests/second |
|--------------|-------------------------|
| Server       | 100                     |
| Player       | 5                       |
| Discord user | 5                       |

Every rate-limited response also includes a remaining-tokens header, e.g. `X-RateLimit-Player-Remaining`.

### Reports — `/api/v1/reports`

| Method   | Endpoint         | Description                             |
|----------|------------------|-----------------------------------------|
| `GET`    | `/reports`       | List reports (paginated, `page`/`size`) |
| `POST`   | `/reports`       | Create a new report                     |
| `GET`    | `/reports/{id}`  | Get a report by id                      |
| `PATCH`  | `/reports/{id}`  | Update a report                         |
| `DELETE` | `/reports/{id}`  | Delete a report                         |
| `GET`    | `/reports/count` | Total number of reports                 |

A report (`ReportDto`) carries: reporter UUID, reported UUID, category id, reason, server UUID, in-world location,
status (`PENDING` / `APPROVED` / `REJECTED`), who handled it, a handler note, and a linked screenshot id.

### Categories — `/api/v1/categories`

| Method   | Endpoint                     | Description                                   |
|----------|------------------------------|-----------------------------------------------|
| `GET`    | `/categories`                | List categories (paginated)                   |
| `POST`   | `/categories`                | Create a category                             |
| `PATCH`  | `/categories/`               | Update a category                             |
| `GET`    | `/categories/{id}`           | Get a category by id                          |
| `DELETE` | `/categories/{id}`           | Delete a category                             |
| `GET`    | `/categories/{id}/reports`   | Get a category including its reports          |
| `GET`    | `/categories/count`          | Total number of categories                    |
| `GET`    | `/categories/reports/count`  | Report count grouped by category              |
| `GET`    | `/categories/reports/active` | Categories that currently have active reports |

Categories now carry their cooldown (`cooldownSec`) and an `active` flag directly in the database, rather than in a
static plugin config file.

### Players — `/api/v1/player`

| Method   | Endpoint               | Description              |
|----------|------------------------|--------------------------|
| `GET`    | `/player`              | List players (paginated) |
| `POST`   | `/player`              | Create a player          |
| `PATCH`  | `/player`              | Update a player          |
| `GET`    | `/player/{playerUuid}` | Get a player by UUID     |
| `DELETE` | `/player/{playerUuid}` | Delete a player by UUID  |
| `GET`    | `/player/count`        | Total number of players  |

### Discord players — `/api/v1/discord-players`

Links a Minecraft player UUID to a Discord user id.

| Method   | Endpoint                               | Description                     |
|----------|----------------------------------------|---------------------------------|
| `POST`   | `/discord-players`                     | Link a player to a Discord user |
| `GET`    | `/discord-players/{id}`                | Get a link by its id            |
| `GET`    | `/discord-players/player/{playerUUID}` | Get a link by player UUID       |
| `PUT`    | `/discord-players/{id}`                | Update a link                   |
| `DELETE` | `/discord-players/{id}`                | Delete a link by id             |
| `DELETE` | `/discord-players/player/{playerUUID}` | Delete a link by player UUID    |

### Screenshots — `/api/v1/screenshots`

| Method   | Endpoint                     | Description                                         |
|----------|------------------------------|-----------------------------------------------------|
| `GET`    | `/screenshots`               | List screenshots (paginated)                        |
| `POST`   | `/screenshots`               | Create screenshot metadata                          |
| `POST`   | `/screenshots/upload`        | Upload a screenshot file (multipart) directly to S3 |
| `GET`    | `/screenshots/{id}`          | Get screenshot metadata by id                       |
| `GET`    | `/screenshots/{id}/download` | Download the stored screenshot                      |
| `PATCH`  | `/screenshots/{id}`          | Update screenshot metadata                          |
| `DELETE` | `/screenshots/{id}`          | Delete a screenshot                                 |
| `GET`    | `/screenshots/count`         | Total number of screenshots                         |

Upload status is tracked as `PENDING` / `SUCCESS` / `FAILED`.

### Servers — `/api/v1/servers`

Supports registering each Minecraft server in the network so reports and rate limits can be tied to a specific server.

| Method   | Endpoint                              | Description                        |
|----------|---------------------------------------|------------------------------------|
| `POST`   | `/servers`                            | Register a new server              |
| `GET`    | `/servers/{serverUUID}`               | Get a server by UUID               |
| `GET`    | `/servers`                            | List servers (paginated)           |
| `PATCH`  | `/servers`                            | Update a server                    |
| `DELETE` | `/servers/{serverUUID}`               | Remove a server                    |
| `GET`    | `/servers/count`                      | Total number of registered servers |
| `GET`    | `/servers/{serverUUID}/reports/count` | Report count for a specific server |

---

## Error handling

All exceptions are funneled through a single `GlobalExceptionHandler` (`@RestControllerAdvice`), returning a consistent
`ApiErrorResponse` body with an HTTP status, a structured `ApiErrorCode` enum value, and a human-readable message.
Current error codes include:

```
METHOD_NOT_ALLOWED, METHOD_ARGUMENT_TYPE_MISMATCH, ILLEGAL_ARGUMENT, UNSUPPORTED_OPERATION,
MISSING_REQUEST_PARAMETER, PLAYER_ALREADY_EXISTS, PLAYER_NOT_FOUND, DISCORD_USER_NOT_FOUND,
CATEGORY_ALREADY_EXISTS, CATEGORY_NOT_FOUND, SERVER_NOT_FOUND, SCREENSHOT_NOT_FOUND,
SCREENSHOT_STORAGE_ERROR, REPORT_NOT_FOUND, RATE_LIMIT_EXCEEDED, MISSING_HEADER,
INTERNAL_SERVER_ERROR
```

---

## RabbitMQ Events

Spring Boot publishes a lightweight event to the `reports.notify` fanout exchange **after every successful database
write**. No full objects are sent over RabbitMQ — consumers fetch full details via REST if they need them.

| Event              | Trigger                             | Consumers           |
|--------------------|-------------------------------------|---------------------|
| `report.created`   | After a report is created           | Plugin, Discord Bot |
| `report.updated`   | After a report's status changes     | Plugin, Discord Bot |
| `screenshot.ready` | After a screenshot upload completes | Discord Bot         |

The `notify.discord` queue is dead-lettered to `reports.dlx` / `notify.discord.dlq` on delivery failure, so a Discord
outage doesn't lose events.

---

## Local development setup

The backend ships its own Docker Compose file (`backend/docker-compose.yaml`) with Postgres, Redis and RabbitMQ, and
Spring Boot's `spring-boot-docker-compose` dev dependency will start/stop it automatically when you run the backend
locally.

```yaml
services:
  postgres:
    image: postgres:latest
    environment:
      POSTGRES_DB: advanced-reports
      POSTGRES_USER: advanced-reports
      POSTGRES_PASSWORD: advanced-reports
    ports:
      - "5432:5432"
  redis:
    image: redis:latest
    ports:
      - "6379:6379"
  rabbitmq:
    image: rabbitmq:latest
    ports:
      - "5672:5672"
      - "15672:15672"
```

### Prerequisites

- Java 25 (Gradle toolchains will provision it automatically if not present)
- Docker & Docker Compose
- A PaperMC server on Minecraft 26.2 (for the plugin, once implemented)
- An AWS account with an S3 bucket, only if you enable the `s3` profile

### 1. Clone the repository

```bash
git clone https://github.com/ItsJxsper/AdvancedReports.git
cd AdvancedReports
```

### 2. Run the backend

```bash
./gradlew :backend:bootRun
```

On startup, Spring Boot detects `backend/docker-compose.yaml` and brings up Postgres, Redis and RabbitMQ for you. The
active Spring profile defaults to `dev` (see `application-dev.properties`).

### 3. Build a specific module

```bash
./gradlew :backend:build
./gradlew :common:build
```

### 4. Explore the API

Once running, the backend exposes Swagger UI (via springdoc) for interactive exploration of every endpoint described
above.

---

## Roadmap

Based on the current state of the `develop` branch:

- [x] `common` module — shared DTOs, enums, exceptions
- [x] `backend` module — REST API, PostgreSQL persistence, Redis-backed rate limiting, RabbitMQ publishing with DLQ,
  optional S3 screenshot storage
- [ ] `plugin` module — PaperMC in-game commands, cooldowns, admin GUI, RabbitMQ consumer
- [ ] `proxy` module — Velocity network-wide integration
- [ ] `api` module — shared client library for plugin/proxy REST calls
- [ ] `discord-bot` module — Discord embeds, buttons, RabbitMQ consumer

---

## License

MIT License — see [LICENSE](LICENSE) for details.

---

*AdvancedReports · PaperMC + Velocity + Spring Boot + Discord Bot, via REST and RabbitMQ*
