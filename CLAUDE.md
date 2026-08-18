# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

AdvancedReports is a modular Minecraft player-reporting system: a PaperMC plugin, a Spring Boot backend, and a Discord
bot, connected via REST (commands/queries) and RabbitMQ (fire-and-forget notifications after a DB write). See
`README.md` for the full feature list, REST endpoint table, RabbitMQ event table, and permission nodes.

**Implementation status** (don't assume otherwise): `backend` and `common` are the real, implemented modules. `api`,
`proxy`, and `discord-bot` are Gradle scaffolding only — build files and dependencies exist but there is no source yet
(`api`/`discord-bot` have empty `src/`). `plugin` has only resource files (translations) — no Java source, even though
`plugin/build.gradle.kts` already references main/bootstrapper/loader classes for the shadow/paper-yaml plugins that
don't exist yet.

Note the README describes MongoDB in the features table, but the backend and `docker-compose.yaml` actually use
**PostgreSQL** + **Redis** (for rate limiting) + **RabbitMQ** — trust the code/config over that one README line.

## Monorepo layout (Gradle multi-project)

- `common` — shared DTOs (Java records), enums (`ReportStatus`, `UploadStatus`, `ApiErrorCode`), and `ApiException`/
  `ApiErrorResponse`. Published as a Maven artifact (`de.itsjxsper:common`) to GitHub Packages and consumed by `backend`
  as `project(":common")` — it's the contract shared across the whole system (backend responses, plugin/bot API client).
  Java 25 toolchain like everything else via the root `subprojects {}` block.
- `backend` — the Spring Boot 4 application. All real business logic lives here.
- `api` — intended future REST client library (OkHttp + Jackson + jakarta-validation) for the plugin/Discord bot to talk
  to the backend; currently dependency scaffolding only.
- `plugin` — PaperMC plugin (Paper API, Java 25, packaged with Shadow). Config'd for `runServer` (via
  `xyz.jpenilla.run-paper`) targeting Minecraft version from `libs.versions.toml` (`minecraft`).
- `proxy` — Velocity proxy plugin (Java 21 toolchain — the one module not on 25), config'd for `runVelocity`.
- `discord-bot` — planned JDA-based Discord bot; currently empty.

Module versions are set per-module via each module's `gradle.properties` (`moduleVersion=...`), read by the root
`build.gradle.kts` and applied to that module's `version`. Dependency versions/plugins are centralized in
`gradle/libs.versions.toml` (version catalog, referenced as `libs.xxx`); `backend` mixes in plain string-coordinate
dependencies not yet in the catalog.

GitHub Packages (`maven.pkg.github.com/ItsJxsper/advancedreports`) is used both as a dependency source (for `common`)
and a publish target; credentials come from `gpr.user`/`gpr.token` Gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN`
/env fallbacks (inconsistent naming across files — check the specific `build.gradle.kts`/`settings.gradle.kts` before
assuming which env var applies).

## Common commands

Run from the repo root; the wrapper is `gradlew`/`gradlew.bat` (Gradle 9.6.1, Java 25 toolchain — Gradle will
auto-provision it).

```bash
# Build everything
./gradlew build

# Build/test a single module
./gradlew :backend:build
./gradlew :backend:test

# Run a single test class or method (backend uses JUnit 5 / useJUnitPlatform)
./gradlew :backend:test --tests "de.itsjxsper.advancedreports.backend.category.service.CategoryServiceTest"
./gradlew :backend:test --tests "*.CategoryServiceTest.shouldCreateCategoryWhenNameDoesNotExist"

# Run the backend locally (Spring Boot dev profile is default: spring.profiles.active=dev)
./gradlew :backend:bootRun

# Bring up backend infra (Postgres, Redis, RabbitMQ) — also auto-started by
# spring-boot-docker-compose (developmentOnly dep) when running bootRun/tests
docker compose -f backend/docker-compose.yaml up -d

# Run the Paper plugin in a local test server (once plugin has source)
./gradlew :plugin:runServer

# Run the Velocity proxy locally
./gradlew :proxy:runVelocity

# Publish a module to GitHub Packages
./gradlew :common:publish
```

Backend integration tests use Testcontainers (`org.testcontainers:postgresql`, `testcontainers-rabbitmq`,
`spring-boot-testcontainers`) — Docker must be running locally for anything beyond pure Mockito unit tests.
`TestcontainersConfiguration` (test-only `@TestConfiguration`) spins up Postgres + RabbitMQ containers via
`@ServiceConnection`, auto-wired into Spring's datasource/AMQP config — no manual connection-string wiring needed in
tests.

## Backend architecture (`backend/src/main/java/.../backend/`)

Feature-package-by-domain, not layer-by-package: each domain (`category`, `player`, `reports`, `screenshot`, `server`,
`discord`) is its own top-level package containing its own `controller/`, `service/`, `data/entity/`,
`data/repository/`, `mapper/`, `exceptions/`. `messaging/events/` holds the RabbitMQ event payload records shared across
domains. `ratelimit/` and `config/` and top-level `exceptions/` are cross-cutting.

Per-domain conventions to follow when adding a new domain or endpoint:

- **Controller → Service → Repository**, with MapStruct (`@Mapper(componentModel = SPRING)`) doing Entity ↔ DTO
  conversion. DTOs live in `common` as records; entities live in `backend`. `partialUpdate(dto, @MappingTarget entity)`
  with `NullValuePropertyMappingStrategy.IGNORE` is the pattern for PATCH-style updates.
- **Exceptions are domain-scoped** (e.g. `category/exceptions/CategoryNotFoundException`) and all funnel into the single
  `@RestControllerAdvice` `GlobalExceptionHandler`, which maps each exception type 1:1 to an `ApiErrorCode` +
  `HttpStatus` and wraps it in `ApiErrorResponse`. Adding a new domain exception means adding both the exception class
  and a handler method here.
- **`common.exceptions.ApiException`** is the client-side counterpart: any module consuming the backend API (future
  `plugin`/`discord-bot` via `api`) parses an `ApiErrorResponse` body into a typed `ApiException` via
  `ApiException.fromHttpResponse(...)`, with `isNotFound()`/`isRateLimited()` helpers keyed off `ApiErrorCode`. Keep
  `ApiErrorCode` values in sync between what `GlobalExceptionHandler` emits and what `ApiException` checks for.
- **Rate limiting** is aspect-based: annotate a controller method with
  `@RateLimited(serverUuid=/playerUuid=/discordUserId=)`, and `RateLimitAspect` (an `@Around` advice) enforces
  per-identity Bucket4j buckets backed by Redis (`RateLimiterService`), reading `X-Server-UUID`/`X-Player-UUID`/
  `X-Discord-ID` headers and setting `X-RateLimit-*-Remaining` response headers. Missing a required header throws
  `MissingHeaderException`; exceeding the bucket throws `RateLimitExceededException` (both handled centrally, see
  above).
- **RabbitMQ** is a single fanout exchange (`RabbitMQConfiguration.EXCHANGE = "reports.notify"`) with per-consumer
  queues (`notify.plugin`, `notify.discord`) bound to it; the Discord queue has a dead-letter exchange/queue
  (`reports.dlx` / `notify.discord.dlq`). Only lightweight event records (`ReportCreatedEvent`, `ReportUpdatedEvent`,
  `ScreenshotReadyEvent` in `messaging/events/`) are published — consumers re-fetch full objects over REST, never over
  AMQP.
- Tests mirror the domain package structure under `backend/src/test/java/.../backend/<domain>/...` — see
  `CategoryServiceTest` for the expected style: `@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks` on the
  mapper+repository+service, `@Nested` classes grouping tests per service method, AssertJ (`assertThat`/
  `assertThatThrownBy`) for assertions, German `@DisplayName`s.
