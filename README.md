# AdvancedReports

> A full-stack Minecraft report system — powered by PaperMC, Spring Boot, a Discord Bot, and RabbitMQ.

```
Plugin  ──REST──▶  Spring Boot  ──RabbitMQ──▶  Discord Bot
  ▲                    │                            │
  └────────────────────┴────────────────────────────┘
              RabbitMQ Update Notifications
```

---

## What is this?

**AdvancedReports** is a modular Minecraft player reporting system for PaperMC servers. Players report others in-game with categories, reasons, and screenshot evidence. Admins review reports through an In-Game GUI. Everything syncs to a Spring Boot backend and a Discord Bot in real-time.

**Rule of thumb:**
- **REST API** → send and receive *objects* (create report, fetch data, update status)
- **RabbitMQ** → fire *notifications* after a database operation completes (new report ping, status change alert)

---

## Features

| Feature                                      | Description                                                   |
|----------------------------------------------|---------------------------------------------------------------|
| `/report <player> <category> [reason] [url]` | In-game report command with tab completion                    |
| **Category System**                          | Configurable categories with individual cooldowns             |
| **Screenshot Evidence**                      | Players submit a URL → backend downloads & uploads to AWS S3  |
| **Cooldown System**                          | Per-category cooldowns, persisted across restarts             |
| **In-Game Admin GUI**                        | Chest inventory GUI — browse, inspect, and close reports      |
| **Permission Nodes**                         | Vault/LuckPerms integration with granular permissions         |
| **PostgreSQL**                               | PostgreSQL for production                                     |
| **Discord Integration**                      | New reports as embeds with Accept / Reject / Teleport buttons |
| **RabbitMQ Events**                          | Lightweight notifications after every DB write                |

---

## Architecture

```
┌─────────────────────────────────────────┐
│            Minecraft Server             │
│                                         │
│  /report CMD  →  REST POST /reports     │
│  Admin GUI    →  REST GET  /reports     │
│                  REST PATCH /reports/id │
│                                         │
│  RabbitMQ Consumer (read-only):         │
│   ← report.created  → ping admins       │
│   ← report.updated  → notify reporter   │
└───────────────────┬─────────────────────┘
                    │ HTTP
                    ▼
┌─────────────────────────────────────────┐
│           Spring Boot Backend           │
│                                         │
│  REST API  →  Service  →  MySQL/JPA     │
│                 │                       │
│                 └──after DB write──▶    │
│              RabbitMQ Fanout Publish    │
│              report.created / .updated  │
│              screenshot.ready           │
└─────────────┬───────────────────────────┘
              │ AMQP
              ▼
┌─────────────────────────────────────────┐
│              RabbitMQ Broker            │
│                                         │
│  Exchange: reports.notify (Fanout)      │
│   → Queue: notify.plugin                │
│   → Queue: notify.discord               │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
 Plugin Consumer   Discord Bot
 (admin ping,      (post embed,
  chat notify)      buttons → REST PATCH)
```

---

## Tech Stack

| Layer            | Technology                            |
|------------------|---------------------------------------|
| Minecraft Plugin | PaperMC API 1.21+, Java 21            |
| Backend          | Spring Boot 3.x, Spring AMQP, Spring Data JPA |
| Discord Bot      | JDA 5, Spring Boot module             |
| Message Broker   | RabbitMQ 3.12+                        |
| Database         | PostgreSQL                            |
| File Storage     | AWS S3                                |
| Build            | Gradle                                |
| Deployment       | Docker Compose                        |

---

## REST API

Base URL: `http://localhost:8080/api/v1`
Auth: `X-API-Key: <your-key>` header on every request

| Method  | Endpoint                   | Description                 |
|---------|----------------------------|-----------------------------|
| `POST`  | `/reports`                 | Submit a new report         |
| `GET`   | `/reports`                 | List reports (paginated)    |
| `GET`   | `/reports/{id}`            | Get report detail           |
| `PATCH` | `/reports/{id}/status`     | Update report status        |
| `POST`  | `/reports/{id}/screenshot` | Attach screenshot URL       |
| `GET`   | `/players/{uuid}/stats`    | Player report statistics    |
| `GET`   | `/categories`              | Available report categories |

Interactive docs available at `/docs` (Swagger UI) when the backend is running.

---

## RabbitMQ Events

Spring Boot publishes a lightweight event to the `reports.notify` Fanout Exchange **after every successful database write**. No objects are sent over RabbitMQ — only a notification with the ID and event type. Consumers fetch details via REST if needed.

| Event              | Trigger                            | Consumers           |
|--------------------|------------------------------------|---------------------|
| `report.created`   | After `POST /reports`              | Plugin, Discord Bot |
| `report.updated`   | After `PATCH /reports/{id}/status` | Plugin, Discord Bot |
| `screenshot.ready` | After S3 upload completes          | Discord Bot         |

**Example payload:**
```json
{
  "event":     "report.updated",
  "reportId":  42,
  "newStatus": "CLOSED",
  "handledBy": "AdminUser",
  "timestamp": "2026-02-27T15:00:00Z"
}
```

---

## Permissions

| Node                                | Description                           | Default |
|-------------------------------------|---------------------------------------|---------|
| `advancedreports.report`            | Submit reports                        | `true`  |
| `advancedreports.report.screenshot` | Attach screenshots                    | `true`  |
| `advancedreports.cooldown.bypass`   | Bypass all cooldowns                  | `false` |
| `advancedreports.admin`             | Open admin GUI                        | OP      |
| `advancedreports.admin.close`       | Close reports                         | OP      |
| `advancedreports.admin.reject`      | Reject reports                        | OP      |
| `advancedreports.admin.teleport`    | Teleport to report location           | OP      |
| `advancedreports.notify`            | Receive in-game pings for new reports | OP      |

---

## Configuration

### `plugin/config.yml`
```yaml
database:
  host: localhost
  port: 3306
  name: advancedreports
  user: mc_user
  password: secret

rabbitmq:
  host: localhost
  port: 5672
  username: mc
  password: secret

api:
  base-url: "http://localhost:8080/api/v1"
  api-key: "your-secret-key"

categories:
  - cheating
  - griefing
  - chat-abuse
  - spam

cooldowns:
  default: 120
  per-category:
    cheating: 300
    griefing: 180
    chat-abuse: 60
```

### `backend/application.yml`
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/advancedreports
    username: mc_user
    password: secret
  rabbitmq:
    host: localhost
    port: 5672
    username: mc
    password: secret

app:
  api-key: "your-secret-key"

aws:
  s3:
    bucket: mc-reports-screenshots
    region: eu-central-1
```

### `discord-bot/application.yml`
```yaml
spring:
  rabbitmq:
    host: localhost

discord:
  token: "YOUR_BOT_TOKEN"
  channel:
    reports-id: "123456789012345678"

backend:
  base-url: "http://localhost:8080/api/v1"
  api-key: "your-secret-key"
```

---

## Getting Started

### Prerequisites

- Java 21+
- Docker & Docker Compose
- A PaperMC 1.21+ server
- An AWS account (S3 bucket)
- A Discord Bot token

### 1. Clone the repository

```bash
git clone https://github.com/your-org/advancedreporte.git
cd advancedreporte
```

### 2. Configure environment

Create a `.env` file in the project root:

```env
API_KEY=your-secret-key
DISCORD_TOKEN=your-discord-bot-token
DISCORD_CHANNEL_ID=your-channel-id
AWS_ACCESS_KEY_ID=your-aws-key
AWS_SECRET_ACCESS_KEY=your-aws-secret
```

### 3. Start the backend stack

```bash
docker compose up -d
```

This starts RabbitMQ (`:5672`, management UI `:15672`), MySQL, Spring Boot backend (`:8080`), and the Discord Bot.

### 4. Build and install the plugin

```bash
cd plugin
mvn package
cp target/advancedreports-*.jar /path/to/your/server/plugins/
```

### 5. Configure the plugin

Edit `plugins/AdvancedReports/config.yml` with your backend URL and API key, then restart the server.

---

## Usage

### In-Game

```
# Report a player
/report Griefer99 griefing destroyed my house https://i.imgur.com/proof.png

# Open admin GUI (requires advancedreporte.admin)
/reports
/reports open
/reports history <player>
```

### Discord

Admins can use the buttons on any report embed:
- ✅ **Close** — marks the report as closed
- ❌ **Reject** — marks the report as rejected
- 📍 **Teleport** — sends a teleport command to the in-game admin

All button actions call `PATCH /reports/{id}/status` via REST. Spring Boot then publishes `report.updated` to RabbitMQ, which notifies the Plugin (reporter gets a chat message) and the Bot (embed updates).

--- 

## License

MIT License — see [LICENSE](LICENSE) for details.

---

*AdvancedReports · PaperMC + Spring Boot + Discord Bot via RabbitMQ*
