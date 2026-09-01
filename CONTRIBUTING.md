# Contributing

_[English](#english) · [Türkçe](#türkçe)_

---

## English

Thanks for your interest in the notification library. This repository holds two
packages that are built and released independently:

- **`notification-parent/`** — the Java backend (Maven multi-module).
- **`notification-react/`** — the React UI (npm, TypeScript + Vite).

### Prerequisites

| Tool | Version | For |
|---|---|---|
| JDK | 21+ | Backend |
| Maven | 3.9+ | Backend |
| Docker Desktop | any recent | **Required** — backend tests use Testcontainers (a throwaway PostgreSQL container). Tests fail with "Could not find a valid Docker environment" if it is not running. |
| Node.js | 18+ | Frontend |
| npm | 9+ | Frontend |

### Backend

```bash
cd notification-parent
mvn clean install          # compile + run all tests (needs Docker running)
```

Run the example application:

```bash
cd notification-example
java -jar target/notification-example-0.1.0.jar
```

> `java -jar` is preferred over `mvn spring-boot:run`: on some machines (e.g. a
> Windows username with non-ASCII characters) the plugin misbehaves.

Local database (used by the example app, not by the tests):

```bash
docker compose up -d       # PostgreSQL on localhost:5432, db/user/pass all "notification"
docker compose down -v     # stop and wipe the data volume
```

Inspect it:

```bash
docker exec -it notification-postgres psql -U notification -d notification
```

**License headers.** Every `.java` file carries the Apache-2.0 header. The check plugin
is in an opt-in profile (the default build never downloads it, because it is
unreachable behind some corporate proxies):

```bash
mvn -Plicense validate         # fails if a header is missing
mvn -Plicense license:format   # add missing headers
```

### Frontend

```bash
cd notification-react
npm install
npm test                   # vitest
npm run lint               # eslint — must be clean (0 errors, 0 warnings)
npm run typecheck          # tsc --noEmit
npm run build              # produces dist/ (also runs tsc)
npm run preview:ui         # playground on localhost:5173 — manual visual check
npm run check:package      # publint + are-the-types-wrong
npm run size               # size-limit budget check
```

Before opening a PR that touches `notification-react`, all of `lint`, `typecheck`,
`test` and `build` must pass. The playground (`preview:ui`) needs a backend running on
`localhost:8080` for live data.

### Migrations

The library manages its own schema with two Flyway instances and two history tables.
Migrations live at:

```
notification-core/src/main/resources/db/notification-migration/core/       V1..V6
notification-core/src/main/resources/db/notification-migration/targeting/  V1..V3
```

They are **not** under `db/migration` on purpose — Spring Boot's own Flyway would scan
that path and collide with a consuming app's migrations. Add new migrations to the end
of the relevant sequence and update the location constants in
`NotificationSchemaInitializer` only if the folders move.

### Pull requests

- Branch from `main`; keep the change focused.
- Match the surrounding code style (comment density, naming, idioms).
- Add or update tests for behaviour changes.
- Backend: `mvn clean install` green. Frontend: `lint` / `typecheck` / `test` /
  `build` green.
- Describe **what** changed and **why** in the PR body.

### Reporting bugs

Open an issue with the bug template. Include the version, environment (OS, JDK / Node,
PostgreSQL), reproduction steps, and the actual vs. expected behaviour.

### Security

Do **not** open a public issue for a security vulnerability — see
[SECURITY.md](./SECURITY.md).

---

## Türkçe

Bildirim kütüphanesine ilgin için teşekkürler. Bu repo, bağımsız derlenip yayınlanan
iki paket içerir:

- **`notification-parent/`** — Java backend (Maven çok modüllü).
- **`notification-react/`** — React arayüzü (npm, TypeScript + Vite).

### Ön gereksinimler

| Araç | Sürüm | Ne için |
|---|---|---|
| JDK | 21+ | Backend |
| Maven | 3.9+ | Backend |
| Docker Desktop | güncel bir sürüm | **Zorunlu** — backend testleri Testcontainers kullanır (geçici bir PostgreSQL konteyneri). Çalışmıyorsa testler "Could not find a valid Docker environment" ile başarısız olur. |
| Node.js | 18+ | Frontend |
| npm | 9+ | Frontend |

### Backend

```bash
cd notification-parent
mvn clean install          # derleme + tüm testler (Docker çalışıyor olmalı)
```

Örnek uygulamayı çalıştır:

```bash
cd notification-example
java -jar target/notification-example-0.1.0.jar
```

> `java -jar`, `mvn spring-boot:run`'a tercih edilir: bazı makinelerde (örn. ASCII
> olmayan karakterli bir Windows kullanıcı adı) plugin sorun çıkarır.

Yerel veritabanı (örnek uygulama kullanır, testler kullanmaz):

```bash
docker compose up -d       # PostgreSQL localhost:5432, db/kullanıcı/parola hepsi "notification"
docker compose down -v     # durdur ve veri volume'unu sil
```

İncele:

```bash
docker exec -it notification-postgres psql -U notification -d notification
```

**Lisans başlıkları.** Her `.java` dosyası Apache-2.0 başlığı taşır. Kontrol plugin'i
opt-in bir profildedir (varsayılan build onu hiç indirmez, çünkü bazı kurumsal proxy'ler
arkasında erişilemiyor):

```bash
mvn -Plicense validate         # başlık eksikse başarısız olur
mvn -Plicense license:format   # eksik başlıkları ekler
```

### Frontend

```bash
cd notification-react
npm install
npm test                   # vitest
npm run lint               # eslint — temiz olmalı (0 hata, 0 uyarı)
npm run typecheck          # tsc --noEmit
npm run build              # dist/ üretir (tsc de çalışır)
npm run preview:ui         # playground localhost:5173 — elle görsel kontrol
npm run check:package      # publint + are-the-types-wrong
npm run size               # size-limit bütçe kontrolü
```

`notification-react`'e dokunan bir PR açmadan önce `lint`, `typecheck`, `test` ve
`build` hepsi geçmeli. Playground (`preview:ui`) canlı veri için `localhost:8080`'de
çalışan bir backend ister.

### Migration'lar

Kütüphane kendi şemasını iki Flyway örneği ve iki geçmiş tablosuyla yönetir.
Migration'lar şurada:

```
notification-core/src/main/resources/db/notification-migration/core/       V1..V6
notification-core/src/main/resources/db/notification-migration/targeting/  V1..V3
```

Bunlar **kasıtlı olarak** `db/migration` altında değil — Spring Boot'un kendi
Flyway'i o yolu tarayıp kullanan uygulamanın migration'larıyla çakışırdı. Yeni
migration'ları ilgili dizinin sonuna ekleyin; klasörler taşınmadıkça
`NotificationSchemaInitializer`'daki konum sabitlerine dokunmayın.

### Pull request'ler

- `main`'den dallanın; değişikliği odaklı tutun.
- Çevredeki kod stiline uyun (yorum yoğunluğu, isimlendirme, deyimler).
- Davranış değişiklikleri için test ekleyin/güncelleyin.
- Backend: `mvn clean install` yeşil. Frontend: `lint` / `typecheck` / `test` /
  `build` yeşil.
- PR gövdesinde **ne** değiştiğini ve **neden** değiştiğini anlatın.

### Hata bildirimi

Bug şablonuyla bir issue açın. Sürümü, ortamı (işletim sistemi, JDK / Node,
PostgreSQL), tekrar üretme adımlarını, gerçek ve beklenen davranışı ekleyin.

### Güvenlik

Bir güvenlik açığı için **herkese açık issue açmayın** — bkz.
[SECURITY.md](./SECURITY.md).
