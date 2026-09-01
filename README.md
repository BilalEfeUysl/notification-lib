# Notification Library

**English** · [Türkçe](./README.tr.md)

A real-time notification system you can add to a Java Spring Boot application with a
single dependency, paired with a React UI package. Your application code publishes a
notification (`notificationService.publish(...)`); the library persists it to
PostgreSQL, pushes it to every connected browser over WebSocket, and exposes a REST
API for history, read/hide state, saving and search.

```
Application code
      │  notificationService.publish(...)
      ▼
Notification library
      ├──► PostgreSQL         (durable record)
      └──► WebSocket          (live fan-out)
                │
                ▼
      Connected browsers      (pop-up + bell icon + list)
```

By default every connected browser sees every notification (no per-user identity, no
authentication). An optional **targeting** mode delivers notifications to a specific
user or role — see [Targeting](#targeting). In both modes notifications are created
from application code only; the library never accepts a notification over HTTP.

---

## Contents

- [Requirements & compatibility](#requirements--compatibility)
- [Repository layout](#repository-layout)
- [Modules](#modules)
- [Quick start](#quick-start-60-seconds)
- [Adding the library to your project](#adding-the-library-to-your-project)
- [Publishing a notification](#publishing-a-notification)
- [Localized content (Turkish / English)](#localized-content-turkish--english)
- [Configuration](#configuration)
- [Security & CORS](#security--cors)
- [REST API](#rest-api)
- [WebSocket API](#websocket-api)
- [Targeting](#targeting)
- [Extension points](#extension-points)
- [React UI package](#react-ui-package)
- [Known limitations](#known-limitations)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [License](#license)

---

## Requirements & compatibility

| Component | Version | Notes |
|---|---|---|
| Java | 21+ | The backend targets Java 21 (`maven.compiler.release=21`). |
| Spring Boot | 3.3.x | Built and tested against 3.3.5. The starter uses `spring-boot-autoconfigure` 3.x APIs (`@AutoConfiguration`). |
| Build tool | Maven 3.9+ | The reactor is a multi-module Maven build. |
| Database | PostgreSQL 12+ | PostgreSQL only — the schema and queries use `JSONB`, partial indexes and row-value keyset pagination. Developed against PostgreSQL 16. |
| Node.js | 18+ | Only needed to build/use the React package. |
| React | 18.2+ | `react` / `react-dom` are **peer dependencies** of `notification-react`. |
| Ant Design | 4.24+ (v4) | `antd` v4 is a **peer dependency**. v5 is not supported. |

**Backend ↔ frontend version pairing.** The React package talks to the backend over
the documented [REST](#rest-api) and [WebSocket](#websocket-api) contracts only. Any
`notification-react` `0.x` works with any backend `0.x` that exposes the same
contract. When the contract changes, both sides move to the next minor version
together; the release notes will state the matching pair.

---

## Repository layout

```
notification/
├── LICENSE                  Apache License 2.0
├── docker-compose.yml       Local PostgreSQL for development
├── README.md                This file
├── CONTRIBUTING.md          Development setup, build & test commands
├── SECURITY.md              How to report a vulnerability
├── CODE_OF_CONDUCT.md       Contributor Covenant
├── .github/                 Issue & pull-request templates
├── notification-parent/     Java backend (Maven multi-module)
└── notification-react/      React npm package (TypeScript + Vite)
```

---

## Modules

| Module | Responsibility |
|---|---|
| `notification-core` | Model, service, JDBC repository, Flyway schema setup |
| `notification-websocket` | Live fan-out (WebSocket handler, session registry, broadcaster) |
| `notification-rest-api` | REST controller, DTOs, identity resolver |
| `notification-spring-boot-starter` | Auto-configuration that wires the three modules together — **this is the only dependency a consuming application adds** |
| `notification-example` | A minimal runnable application that uses the starter |

**Java package root:** `io.github.bilalefeuysl.notification`

---

## Quick start (60 seconds)

```bash
# 1. Start PostgreSQL
docker compose up -d

# 2. Build the backend and install it into your local Maven repository
cd notification-parent
mvn clean install

# 3. Run the example application
cd notification-example
java -jar target/notification-example-0.1.0.jar
```

The application starts on `http://localhost:8080`. Try it:

```bash
# publish a notification (example app's own test endpoint)
curl -X POST "http://localhost:8080/example/publish?classification=Sensor+Alarm&message=Tank+3+threshold+exceeded&type=WARNING"

# targeting is ON in the example config, so list requests need an identity header
curl -H "X-User-Id: user1" http://localhost:8080/api/notifications
```

> Building the backend requires **Docker Desktop running** — `notification-core` and
> `notification-spring-boot-starter` tests spin up a throwaway PostgreSQL container
> via Testcontainers. See [Development](#development).

---

## Adding the library to your project

```xml
<dependency>
  <groupId>io.github.bilalefeuysl.notification</groupId>
  <artifactId>notification-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

If your application already has a `DataSource` bean pointing at PostgreSQL, you do not
need to configure anything else — the library creates its own tables, registers the
WebSocket endpoint and the REST endpoints, and runs its own Flyway migrations. To use
a **separate** database connection for notifications, set `notification.datasource.*`
(see [Configuration](#configuration)).

**No Flyway configuration is required.** The library runs its migrations with its own
history table (`notification_schema_history`), separate from any Flyway history your
application keeps.

- **If your app does not use Flyway:** the library registers a `Flyway` bean so Spring
  Boot's own Flyway auto-configuration backs off (`@ConditionalOnMissingBean(Flyway.class)`)
  and does not crash with *"Found non-empty schema(s) but no schema history table"*
  after the library has created its tables.
- **If your app uses Flyway the standard way** (migrations under `classpath:db/migration`,
  or `spring.flyway.locations` set), the library detects this, does **not** register that
  suppressor bean, and lets Spring Boot run your migrations normally. It also makes sure
  Spring Boot's Flyway runs *before* the library fills the schema, so the non-empty-schema
  check still passes. The library's own schema is prepared independently, with its own
  history table. (Log line: *"uygulamanın kendi Flyway migration'ları bulundu … 'bastırıcı'
  Flyway bean'ini kaydetmiyor"*.)

You only need a manual `@Bean Flyway` if you manage migrations from a non-default location
without the `spring.flyway.locations` property — Spring Boot's standard "multiple Flyway
instances" pattern:

```java
@Bean
public Flyway appFlyway(DataSource dataSource) {
    Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/my-migrations")
            .load();
    flyway.migrate();
    return flyway;
}
```

---

## Publishing a notification

**Simple** (one line, one import):

```java
notificationService.publish("Sensor Alarm", "Tank 3 temperature threshold exceeded");

// with a type (drives the colour: INFO / SUCCESS / WARNING / ERROR)
notificationService.publish("Sensor Alarm", "Tank 3 threshold exceeded", NotificationType.WARNING);
```

**Advanced** (extra fields — source device id, free-form metadata, audience):

```java
notificationService.publish(
    NotificationCommand.builder()
        .classification("Sensor Alarm")            // title (required, max 128 chars)
        .message("Tank 3 temperature threshold exceeded: 94°C")  // body (required)
        .type(NotificationType.WARNING)            // or a free-text string, max 32 chars
        .priority(NotificationPriority.HIGH)       // LOW / NORMAL (default) / HIGH
        .sourceDeviceId("PLC-42")                  // optional, max 128 chars
        .metadataEntry("temperature", 94)          // optional free-form JSON data
        .audience(new NotificationAudience.SpecificUser("user-42")) // see Targeting
        .build());
```

Every successful `publish()` fires a `NotificationPublishedEvent`; a consuming
application can add behaviour (email, metrics, …) with an `@EventListener` without
touching the library.

---

## Localized content (Turkish / English)

`classification` / `message` are the **default text** — write them in whatever
language you want. A notification can additionally carry an **English** variant; the
React UI shows it to users whose language is `en` and falls back to the default for
everyone else.

```java
notificationService.publish(
    NotificationCommand.builder()
        .classification("Bakım başladı")            // default text (required)
        .message("Sistem 02:00'de kapanacak")
        .classificationEn("Maintenance started")    // optional English variant
        .messageEn("System goes down at 02:00")
        .build());
```

Rules:

- The English variant is **all-or-nothing** — pass both `classificationEn` and
  `messageEn`, or neither. A half-filled variant throws at `build()` time, and the
  database enforces the same with a `CHECK` constraint.
- Only have English content? Put it in `classification` / `message` and skip the
  `*En` methods — everyone then sees English.
- Resolution happens **client-side** (the WebSocket sends one message to every
  browser), so both variants travel to the browser in `NotificationDto` /
  the WebSocket payload as `classificationEn` / `messageEn` (`null` when absent).
- Free-text search (`?q=`) matches the English text too.
- Migration `V7` renames the DB columns `classification` / `message` to
  `classification_tr` / `message_tr` (symmetric with the new nullable
  `classification_en` / `message_en`). The REST / WebSocket JSON field names are
  unchanged — `classification`, `message`, `classificationEn`, `messageEn`.
- Turkish / English only — matching the React UI, which supports those two languages.

---

## Configuration

All keys live under `notification.*` in `application.yml` or `application.properties`.
Invalid values (blank required strings, non-positive limits) are rejected at
**application start** with a clear message, not at runtime.

| Key | Default | Description |
|---|---|---|
| `notification.enabled` | `true` | Master switch. `false` disables every bean and auto-configuration. |
| `notification.table-name` | `notifications` | Name of the notifications table. |
| `notification.schema` | `public` | Schema that holds the library's tables. |
| `notification.initialize-schema` | `true` | Whether the library creates/updates its own tables via Flyway on startup. Set `false` when you supply your own `NotificationRepository` (e.g. in-memory) or manage the schema yourself — the library then touches no `DataSource` and registers no `Flyway` bean. See [Extension points](#extension-points). |
| `notification.datasource.url` | *(empty)* | If set, the library opens its **own** connection pool. If empty, the application's existing `DataSource` is used. |
| `notification.datasource.username` | *(empty)* | Username for the separate connection. |
| `notification.datasource.password` | *(empty)* | Password for the separate connection. |
| `notification.websocket.enabled` | `true` | WebSocket layer. `false` → notifications are readable via REST only. |
| `notification.websocket.path` | `/ws/notifications` | WebSocket handshake path. |
| `notification.rest.enabled` | `true` | REST layer. `false` → the controller is not registered. |
| `notification.rest.base-path` | `/api/notifications` | Root path for all REST endpoints. |
| `notification.rest.default-limit` | `25` | Page size when the client sends no `limit`. |
| `notification.rest.max-limit` | `100` | Largest `limit` the client may request; higher values are clamped. |
| `notification.targeting.enabled` | `false` | Enables per-user/role targeting — see [Targeting](#targeting). |
| `notification.cors.allowed-origins` | *(empty)* | Cross-origin browser origins allowed to reach the REST endpoints **and** the WebSocket. Empty → cross-origin access is off (only a frontend served from the backend's own origin connects). See [Security & CORS](#security--cors). |

If you use a separate `notification.datasource.url`, the library needs a connection
pool on the classpath. HikariCP is used automatically when present
(`spring-boot-starter-jdbc` / `-data-jpa` pull it in). If the URL is set but no
HikariCP is available, the application fails to start with a clear message rather than
silently falling back to the wrong database.

---

## Security & CORS

A browser blocks a page's JavaScript from reading a response from a **different
origin** (scheme + host + port) unless the target server explicitly allows it (CORS).
This library reads the allowed origins from `notification.cors.allowed-origins`:

```yaml
notification:
  cors:
    allowed-origins:
      - https://app.example.com
      - http://localhost:5173   # local dev (Vite, etc.)
```

- **Frontend served from the backend's own origin** (Spring serves the static files,
  or both sit behind one domain / reverse proxy) → you do **not** need this setting;
  same-origin requests are not subject to CORS.
- **Frontend on a different origin** → list its origins as above. The same list
  applies to both the REST endpoints and the WebSocket handshake.
- The wildcard (`*`) is **deliberately not supported**: leaving every origin open lets
  a malicious site read a logged-in user's notification stream through their browser
  (Cross-Site WebSocket Hijacking).

CORS only constrains browsers; non-browser clients (`curl`, scripts) bypass it. For
identity-based access control see the [security warning](#security-warning) in the
Targeting section.

---

## REST API

Base path is configurable (`notification.rest.base-path`, default `/api/notifications`).
All responses are JSON. When [targeting](#targeting) is enabled every request must
carry an `X-User-Id` header (optionally `X-User-Roles: ADMIN,EDITOR`); a missing
`X-User-Id` returns `400 INVALID_REQUEST`.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `{base}` | Paginated list (newest first). Query params below. |
| `GET` | `{base}/unread-count` | `{ "count": <n> }` — total unread, independent of pagination. |
| `DELETE` | `{base}/{id}` | Hide one notification (soft delete). `204`. |
| `DELETE` | `{base}` | Hide all currently visible notifications. `204`. |
| `PATCH` | `{base}/read` | Mark ids read. Body: JSON array of id strings. `204`. |
| `PATCH` | `{base}/{id}/saved` | Save / unsave. Body: `{ "saved": true }` or `{ "saved": false }`. `204`. |

### `GET {base}` query parameters

| Param | Type | Description |
|---|---|---|
| `before` | ISO-8601 instant | Time cursor for the next page. Omit for the first page. |
| `limit` | integer | Page size (clamped to `max-limit`). Must be `> 0`. |
| `priority` | `LOW` / `NORMAL` / `HIGH` | Return only this priority. |
| `saved` | `true` | Return only saved notifications ("saved" view). |
| `q` | string | Free-text search over title, message, English title/message, type, source device id and the formatted date (`DD.MM.YYYY HH:MM`). Case-insensitive; `%` / `_` are matched literally. |
| `sort` | `priority` | Opt-in priority ordering (HIGH → NORMAL → LOW, then newest first). Cannot be combined with `q` / `saved` / `priority` (returns `400`). |
| `priorityCursor` | opaque string | Page cursor when `sort=priority`. Pass back the previous response's `nextPriorityCursor` verbatim. |

### Response shape

```json
{
  "items": [
    {
      "id": "…",
      "classification": "Sensor Alarm",
      "message": "Tank 3 threshold exceeded",
      "classificationEn": null,
      "messageEn": null,
      "type": "WARNING",
      "priority": "HIGH",
      "read": false,
      "saved": false,
      "createdAt": "2026-08-28T10:15:30Z",
      "metadata": { "temperature": 94 },
      "sourceDeviceId": "PLC-42"
    }
  ],
  "hasMore": true,
  "nextBefore": "2026-08-28T09:00:00Z",
  "nextPriorityCursor": null
}
```

`nextBefore` is `null` on the last page. `nextPriorityCursor` is populated **only**
when `sort=priority` was requested (and `null` otherwise).

---

## WebSocket API

Connect to `ws://host:port{websocket.path}` (default `/ws/notifications`). When
[targeting](#targeting) is enabled, pass the identity as query parameters — the
browser's built-in WebSocket API cannot add custom headers during the handshake:

```
ws://host:port/ws/notifications?userId=user-42&roles=ADMIN,EDITOR
```

A missing identity is rejected with `401 Unauthorized` and the connection is never
established.

### Messages from the server

| Event | Payload | Meaning |
|---|---|---|
| `NOTIFICATION_CREATED` | a full notification object (same shape as a REST `items[]` entry) | A new notification was published for this connection. |
| `NOTIFICATION_HIDDEN` | `{ "ids": ["…"] }` | These notifications were hidden (possibly in another tab). |
| `NOTIFICATION_READ` | `{ "ids": ["…"] }` | These notifications were marked read. |
| `NOTIFICATION_ALL_HIDDEN` | *(none)* | Every notification was hidden — the client should clear its list. |
| `PONG` | *(none)* | Reply to the client's `PING` keep-alive. |

```json
{
  "event": "NOTIFICATION_CREATED",
  "payload": {
    "id": "…", "classification": "…", "message": "…",
    "classificationEn": null, "messageEn": null, "type": "WARNING",
    "priority": "HIGH", "read": false, "saved": false,
    "createdAt": "…", "metadata": {}, "sourceDeviceId": null
  }
}
```

The `HIDDEN` / `READ` / `ALL_HIDDEN` events keep multiple browser tabs of the same
user in sync.

---

## Targeting

By default the library runs **without targeting**: every published notification goes
to every connected browser, regardless of who they are. To deliver notifications to a
specific user or role, turn it on:

```yaml
notification:
  targeting:
    enabled: true
```

<a name="security-warning"></a>
> ⚠️ **Do not use this in production without authentication.**
> The library's default identity resolver (`HeaderNotificationIdentityResolver`) reads
> the identity straight from the `X-User-Id` / `X-User-Roles` headers (WebSocket:
> `?userId=…&roles=…` query params) and **does not validate them**. Anyone making a
> request can impersonate any identity:
>
> ```bash
> curl http://localhost:8080/api/notifications -H "X-User-Id: boss"
> ```
>
> This is **by design** — the library leaves real authentication to the application.
> The default resolver is only safe on a trusted network (e.g. behind an API gateway
> that has already authenticated the user and set these headers) or in local
> development. For a public deployment you **must**:
> 1. Put an authenticating layer in front of the requests (Spring Security, gateway, mTLS…),
> 2. Define your own `NotificationIdentityResolver` bean that reads the identity from
>    the **verified** `SecurityContext`, not the raw header (full example below).
>
> The [CORS setting](#security--cors) limits browser-based attacks but `curl` / scripts
> bypass it entirely — so CORS is **not** a substitute for authentication.

When enabled:

- Each notification can carry an **audience** (default `Everyone` — goes to everyone,
  identical to the non-targeting behaviour):

  ```java
  notificationService.publish(
      NotificationCommand.builder()
          .classification("Awaiting approval")
          .message("Your form was submitted for review")
          .audience(new NotificationAudience.SpecificUser("user-42"))  // only this user
          // .audience(new NotificationAudience.Role("ADMIN"))         // or everyone with this role
          .build());
  ```

- **REST requests** read the identity from `X-User-Id` (optional
  `X-User-Roles: ADMIN,EDITOR`, comma-separated). A missing `X-User-Id` returns
  `400 Bad Request` (`INVALID_REQUEST`).
- **The WebSocket connection** expects the same information as query parameters
  (`?userId=user-42&roles=ADMIN,EDITOR`). A missing identity fails the handshake with
  `401 Unauthorized`.
- Read / hidden / **saved** state is now **per-user** (stored in a separate
  `notification_user_state` table); with targeting on, the library runs one extra
  Flyway migration for it.
- Identity resolution is pluggable. Implement `NotificationIdentityResolver` and
  register it as a `@Bean`. **There are two separate interfaces with the same name** —
  `...rest.identity.NotificationIdentityResolver` (used by the REST controller) and
  `...websocket.identity.NotificationIdentityResolver` (used by the WebSocket layer).
  Each module expects its own bean; one does not substitute for the other.

### Production example — Spring Security + identity-bound resolver

The three pieces below make targeting safe: (1) a `SecurityFilterChain` that closes
the notification endpoints to unauthenticated callers, (2) a REST resolver that reads
from `SecurityContext`, (3) the same for WebSocket. Once these beans exist, the
library's default `HeaderNotificationIdentityResolver` backs off automatically
(`@ConditionalOnMissingBean`).

**1. Security chain protecting the notification endpoints** (JWT resource server here;
form login / session / your own filter also work):

```java
@Configuration
@EnableWebSecurity
public class NotificationSecurityConfig {

    @Bean
    SecurityFilterChain notificationSecurity(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/notifications/**", "/ws/notifications")
            .authorizeHttpRequests(reg -> reg.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
```

> The browser's built-in WebSocket API cannot send an `Authorization` header during
> the handshake. With JWT you must carry the token as a query parameter
> (`ws://.../ws/notifications?access_token=…`) and read it with a `BearerTokenResolver`.
> Session (cookie) based auth does not have this problem — the cookie is sent
> automatically.

**2. REST resolver — reads the identity from `SecurityContext`:**

```java
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import io.github.bilalefeuysl.notification.rest.identity.NotificationIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SecurityContextRestIdentityResolver implements NotificationIdentityResolver {

    @Override
    public NotificationIdentity resolve(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // The security chain already returns 401; this is only a safety net.
            throw new IllegalStateException("Notification request arrived unauthenticated");
        }
        return new NotificationIdentity(auth.getName(), rolesOf(auth));
    }

    private Set<String> rolesOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .collect(Collectors.toSet());
    }
}
```

**3. WebSocket resolver — reads the verified `Principal` from the handshake:**

```java
import io.github.bilalefeuysl.notification.core.model.NotificationIdentity;
import io.github.bilalefeuysl.notification.websocket.identity.NotificationIdentityResolver;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SecurityContextWsIdentityResolver implements NotificationIdentityResolver {

    @Override
    public NotificationIdentity resolve(ServerHttpRequest request) {
        Principal principal = request.getPrincipal();
        if (!(principal instanceof Authentication auth) || !auth.isAuthenticated()) {
            throw new IllegalStateException("WebSocket handshake arrived unauthenticated");
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .collect(Collectors.toSet());
        return new NotificationIdentity(auth.getName(), roles);
    }
}
```

With this setup `X-User-Id` / `?userId=` are never read — the identity comes entirely
from the verified token / session and cannot be spoofed.

---

## Extension points

Several core components are plain interfaces registered with
`@ConditionalOnMissingBean`, so defining your own `@Bean` of the same type replaces
the library's default:

| Interface | Default | Replace it to… |
|---|---|---|
| `NotificationRepository` | `JdbcNotificationRepository` (PostgreSQL) | store notifications somewhere else (another DB, in-memory for tests). |
| `NotificationService` | `DefaultNotificationService` | change publish/query behaviour. |
| `NotificationBroadcaster` | `LocalBroadcaster` (single JVM) | fan out across instances via Redis Pub/Sub, Kafka, … (see [Known limitations](#known-limitations)). |
| `NotificationIdentityResolver` (REST and WebSocket — two interfaces) | `HeaderNotificationIdentityResolver` | bind the identity to your auth layer (see [Targeting](#targeting)). |

### Writing your own `NotificationRepository`

`NotificationRepository` is a plain interface — a custom `@Bean` of that type replaces
`JdbcNotificationRepository` entirely. Two things to know:

1. **The abstract methods are all non-targeting.** The `…ForIdentity` methods are
   `default` and throw `UnsupportedOperationException`. Override them only if you also
   run with `notification.targeting.enabled=true`.
2. **Turn off the library's schema management** with `notification.initialize-schema=false`,
   otherwise it still tries to run Flyway against a `DataSource` on startup. With a
   non-database repository and no other need for a `DataSource`, also exclude Spring
   Boot's own data-source auto-configuration:

   ```java
   @SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
   ```

```java
@Configuration
class NotificationRepositoryConfig {
    @Bean
    NotificationRepository notificationRepository() {
        return new InMemoryNotificationRepository();
    }
}
```

```java
import io.github.bilalefeuysl.notification.core.model.Notification;
import io.github.bilalefeuysl.notification.core.model.NotificationPriority;
import io.github.bilalefeuysl.notification.core.repository.NotificationRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Keeps notifications in memory only — state is lost on restart. Useful for tests,
 * demos, or a single node that does not need persistence. Targeting
 * ({@code notification.targeting.enabled=true}) is NOT supported.
 */
public class InMemoryNotificationRepository implements NotificationRepository {

    private final Map<UUID, Notification> store = new ConcurrentHashMap<>();

    @Override
    public Notification save(Notification notification) {
        store.put(notification.id(), notification);
        return notification;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    // newest-first; `before` is a keyset cursor on createdAt (null = from the top)
    private Stream<Notification> visibleNewestFirst(Instant before) {
        return store.values().stream()
                .filter(Notification::visible)
                .filter(n -> before == null || n.createdAt().isBefore(before))
                .sorted(Comparator.comparing(Notification::createdAt).reversed());
    }

    @Override
    public List<Notification> findVisibleBefore(Instant before, int limit) {
        return visibleNewestFirst(before).limit(limit).toList();
    }

    @Override
    public List<Notification> findVisibleBefore(Instant before, int limit, NotificationPriority priority) {
        return visibleNewestFirst(before)
                .filter(n -> priority == null || n.priority() == priority)
                .limit(limit)
                .toList();
    }

    @Override
    public List<Notification> findSavedBefore(Instant before, int limit) {
        return visibleNewestFirst(before).filter(Notification::saved).limit(limit).toList();
    }

    @Override
    public List<Notification> searchVisibleBefore(String query, Instant before, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return visibleNewestFirst(before)
                .filter(n -> contains(n.classification(), q) || contains(n.message(), q)
                        || contains(n.classificationEn(), q) || contains(n.messageEn(), q)
                        || contains(n.type(), q) || contains(n.sourceDeviceId(), q))
                .limit(limit)
                .toList();
    }

    private static boolean contains(String value, String lowerCaseQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerCaseQuery);
    }

    // opt-in ?sort=priority : HIGH → NORMAL → LOW, then newest, then id (3-part keyset)
    @Override
    public List<Notification> findVisibleSortedByPriority(NotificationPriority cursorPriority,
            Instant cursorCreatedAt, UUID cursorId, int limit) {
        Comparator<Notification> order = Comparator
                .comparingInt((Notification n) -> n.priority().ordinal()).reversed()
                .thenComparing(Notification::createdAt, Comparator.reverseOrder())
                .thenComparing(Notification::id, Comparator.reverseOrder());

        return store.values().stream()
                .filter(Notification::visible)
                .sorted(order)
                .filter(n -> cursorPriority == null
                        || afterCursor(n, cursorPriority, cursorCreatedAt, cursorId))
                .limit(limit)
                .toList();
    }

    private static boolean afterCursor(Notification n, NotificationPriority cp, Instant cc, UUID ci) {
        int byPriority = Integer.compare(cp.ordinal(), n.priority().ordinal());
        if (byPriority != 0) return byPriority > 0;
        int byTime = cc.compareTo(n.createdAt());
        if (byTime != 0) return byTime > 0;
        return ci.compareTo(n.id()) > 0;
    }

    @Override
    public boolean hide(UUID id) {
        Notification n = store.get(id);
        if (n == null || !n.visible()) return false;
        store.put(id, copy(n, false, n.read(), n.saved()));
        return true;
    }

    @Override
    public int hideAll() {
        int count = 0;
        for (Notification n : store.values()) {
            if (n.visible()) {
                store.put(n.id(), copy(n, false, n.read(), n.saved()));
                count++;
            }
        }
        return count;
    }

    @Override
    public int markAsRead(List<UUID> ids) {
        int count = 0;
        for (UUID id : ids) {
            Notification n = store.get(id);
            if (n != null && !n.read()) {
                store.put(id, copy(n, n.visible(), true, n.saved()));
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean setSaved(UUID id, boolean saved) {
        Notification n = store.get(id);
        if (n == null) return false;
        store.put(id, copy(n, n.visible(), n.read(), saved));
        return true;
    }

    // countUnread() has a default implementation; override it for efficiency if needed.

    private static Notification copy(Notification n, boolean visible, boolean read, boolean saved) {
        return new Notification(n.id(), n.classification(), n.message(),
                n.classificationEn(), n.messageEn(), n.type(), n.priority(), n.sourceDeviceId(),
                n.createdAt(), visible, read, saved, n.metadata(), n.audience());
    }
}
```

> This exact class is compiled and exercised by `NotificationCustomRepositoryTest`
> in the starter module.

---

## React UI package

The browser side lives in [`notification-react/`](./notification-react/) and is
published to npm separately. It provides `NotificationProvider` (data + WebSocket +
app-wide theme/language), `NotificationBell` (bell icon + panel), `PopupStack` (the
corner pop-ups, rendered independently of the bell) and a `useNotifications` hook.
It connects to this backend over WebSocket and renders live notifications.

See [`notification-react/README.md`](./notification-react/README.md) for the full
component and prop reference.

---

## Known limitations

**Designed for a single instance.** The default `NotificationBroadcaster` is
`LocalBroadcaster`: a published notification only reaches browsers connected to the
**same JVM process**. If you run several instances / pods behind a load balancer, a
browser connected to one instance will not receive notifications published on another.
The `NotificationBroadcaster` interface is deliberately abstract for this reason —
provide an implementation backed by a message broker (Redis Pub/Sub, Kafka, …) and
register it as a `@Bean` to replace `LocalBroadcaster`. The library does not ship such
an implementation yet.

**Other limits.** PostgreSQL only (JSONB / partial indexes / row-value pagination). No
automatic retention or archiving of old notifications. No built-in flood / dedupe
protection. Free-text search (`q=`) is a plain `ILIKE` and is not backed by a
full-text index.

---

## Troubleshooting

**`mvn clean install` fails with "Could not find a valid Docker environment".**
`notification-core` and `notification-spring-boot-starter` tests use Testcontainers,
which needs a running Docker daemon. Start Docker Desktop and retry.

**`mvn deploy` / plugin download fails with `PKIX path building failed` /
`certificate_unknown`.** Your network (corporate proxy / SSL inspection) is
intercepting TLS to Maven Central. Configure `~/.m2/settings.xml` with your proxy's CA,
or build on an unrestricted network. The Apache license-header check plugin is
deliberately kept in an opt-in `license` profile for exactly this reason — the default
build never downloads it.

**Consuming app crashes at start with "Found more than one migration with version 1"
or "Found non-empty schema(s) but no schema history table".** An older layout put the
library's migrations under `classpath:db/migration`, where Spring Boot's own Flyway
would scan them. They now live under `classpath:db/notification-migration/{core,targeting}`
with their own history tables (`notification_schema_history`,
`notification_targeting_schema_history`) and the library registers its own `Flyway`
bean so Spring Boot's Flyway backs off. If you see this, make sure you are on a current
version and have not copied the old migration folder.

**After adding the library, my own Flyway migrations (`classpath:db/migration`) stopped
running.** Fixed. The library used to *always* register a `Flyway` bean to suppress
Spring Boot's Flyway auto-configuration, which — because Boot's `FlywayConfiguration` is
`@ConditionalOnMissingBean(Flyway.class)` at class level — also disabled the
`FlywayMigrationInitializer` that runs your `db/migration` scripts, silently. The library
now detects your migrations (`classpath:db/migration/**/*.sql`, or `spring.flyway.locations`
set) and steps aside, and orders Spring Boot's Flyway before its own schema setup. If your
migrations live somewhere else and you don't set `spring.flyway.locations`, define an
explicit `@Bean Flyway` (see the Flyway section above). Make sure you are on a current
version.

**A `SPECIFIC_USER` notification is visible to everyone.** Targeting is not actually
active. Check the startup log for `targeting: true` and that the
`notification_targeting_schema_history` / `notification_user_state` migrations ran.
With a stale Flyway "baseline" row, the targeting `V1` migration can be skipped; the
library now sets `baselineVersion("0")` to prevent this — again, make sure you are on
a current version.

**Targeting is on but requests fail at start with "NotificationIdentityResolver not
provided".** With `notification.targeting.enabled=true` the library needs a
`NotificationIdentityResolver` bean. The default (`HeaderNotificationIdentityResolver`)
is registered automatically; if you defined your own, make sure its type is the right
one for the module (REST vs WebSocket — they are different interfaces with the same
name).

**Missing `X-User-Id` returns `400`, not `500`.** That is intentional — a request
without an identity while targeting is on is a client error.

**After adding the library, my app rejects unknown JSON fields / date formats and
`@JsonInclude` settings changed.** The library used to register its internal
`ObjectMapper` before Spring Boot's `JacksonAutoConfiguration`, which made Boot back
off (`@ConditionalOnMissingBean(ObjectMapper.class)`) so the whole app used the
library's bare mapper. Fixed: `NotificationAutoConfiguration` is now
`@AutoConfigureAfter(JacksonAutoConfiguration.class)`, so Boot's `@Primary`
`ObjectMapper` wins and the library's `notificationObjectMapper` stays a secondary,
internal-only bean. Make sure you are on a current version; no consumer-side
workaround (a `@Primary ObjectMapper` bean) is needed anymore.

**`java -jar` instead of `mvn spring-boot:run`.** On some environments (e.g. a Windows
username with non-ASCII characters) `mvn spring-boot:run` misbehaves; run the built jar
directly:

```bash
cd notification-example
java -jar target/notification-example-0.1.0.jar
```

---

## Development

See [CONTRIBUTING.md](./CONTRIBUTING.md) for the full setup, build and test workflow.
In short:

- **Docker Desktop must be running** for the backend tests (Testcontainers).
- Backend: `cd notification-parent && mvn clean install`
- Frontend: `cd notification-react && npm install && npm test && npm run build`
- Local database: `docker compose up -d` (PostgreSQL on `localhost:5432`,
  db/user/password all `notification`).

---

## License

Apache License 2.0 — see [LICENSE](./LICENSE).
