# Bildirim Kütüphanesi

[English](./README.md) · **Türkçe**

Java Spring Boot uygulamalarına tek bir bağımlılıkla eklenebilen gerçek zamanlı bir
bildirim sistemi ve buna eşlik eden bir React arayüz paketi. Uygulama kodu bir
bildirim yayınlar (`notificationService.publish(...)`); kütüphane bunu PostgreSQL'e
kaydeder, açık tüm tarayıcılara WebSocket üzerinden anında iletir ve geçmiş,
okundu/gizli durumu, kaydetme ve arama için bir REST arayüzü sunar.

```
Uygulama kodu
      │  notificationService.publish(...)
      ▼
Bildirim kütüphanesi
      ├──► PostgreSQL         (kalıcı kayıt)
      └──► WebSocket          (canlı yayın)
                │
                ▼
      Bağlı tarayıcılar       (pop-up + zil ikonu + liste)
```

Varsayılan modda bağlı her tarayıcı her bildirimi görür (kullanıcı ayrımı, kimlik
doğrulama yok). Opsiyonel **hedefleme (targeting)** modu, bildirimleri belirli bir
kullanıcıya veya role iletir — bkz. [Hedefli bildirim](#hedefli-bildirim). Her iki
modda da bildirimler yalnızca uygulama kodundan verilir; kütüphane HTTP üzerinden
bildirim kabul etmez.

---

## İçindekiler

- [Gereksinimler ve uyumluluk](#gereksinimler-ve-uyumluluk)
- [Repo yapısı](#repo-yapısı)
- [Modüller](#modüller)
- [Hızlı başlangıç](#hızlı-başlangıç-60-saniye)
- [Kütüphaneyi projenize ekleme](#kütüphaneyi-projenize-ekleme)
- [Bildirim yayınlama](#bildirim-yayınlama)
- [İki dilli içerik (Türkçe / İngilizce)](#i̇ki-dilli-içerik-türkçe--i̇ngilizce)
- [Konfigürasyon](#konfigürasyon)
- [Güvenlik ve CORS](#güvenlik-ve-cors)
- [REST API](#rest-api)
- [WebSocket API](#websocket-api)
- [Hedefli bildirim](#hedefli-bildirim)
- [Genişletme noktaları](#genişletme-noktaları)
- [React arayüz paketi](#react-arayüz-paketi)
- [Bilinen kısıtlamalar](#bilinen-kısıtlamalar)
- [Sorun giderme](#sorun-giderme)
- [Geliştirme](#geliştirme)
- [Lisans](#lisans)

---

## Gereksinimler ve uyumluluk

| Bileşen | Sürüm | Not |
|---|---|---|
| Java | 21+ | Backend Java 21'i hedefler (`maven.compiler.release=21`). |
| Spring Boot | 3.3.x | 3.3.5 ile derlenip test edildi. Starter, 3.x auto-configure API'lerini kullanır (`@AutoConfiguration`). |
| Derleme aracı | Maven 3.9+ | Reaktör çok modüllü bir Maven build'idir. |
| Veritabanı | PostgreSQL 12+ | Yalnızca PostgreSQL — şema ve sorgular `JSONB`, partial index ve satır-değeri (row-value) keyset sayfalama kullanır. PostgreSQL 16 ile geliştirildi. |
| Node.js | 18+ | Yalnızca React paketini derlemek/kullanmak için gerekli. |
| React | 18.2+ | `react` / `react-dom`, `notification-react`'in **peer dependency**'sidir. |
| Ant Design | 4.24+ (v4) | `antd` v4 bir **peer dependency**'dir. v5 desteklenmez. |

**Backend ↔ frontend sürüm eşleşmesi.** React paketi backend ile yalnızca belgeli
[REST](#rest-api) ve [WebSocket](#websocket-api) sözleşmeleri üzerinden konuşur. Aynı
sözleşmeyi sunan herhangi bir backend `0.x` ile herhangi bir `notification-react`
`0.x` çalışır. Sözleşme değiştiğinde iki taraf birlikte bir sonraki minor sürüme
geçer; sürüm notları eşleşen çifti belirtir.

---

## Repo yapısı

```
notification/
├── LICENSE                  Apache License 2.0
├── docker-compose.yml       Geliştirme için yerel PostgreSQL
├── README.md                İngilizce (birincil)
├── README.tr.md             Bu dosya
├── CONTRIBUTING.md          Geliştirme kurulumu, build & test komutları
├── SECURITY.md              Güvenlik açığı bildirimi
├── CODE_OF_CONDUCT.md       Contributor Covenant
├── .github/                 Issue ve pull-request şablonları
├── notification-parent/     Java backend (Maven çok modüllü)
└── notification-react/      React npm paketi (TypeScript + Vite)
```

---

## Modüller

| Modül | Sorumluluğu |
|---|---|
| `notification-core` | Model, servis, JDBC repository, Flyway şema kurulumu |
| `notification-websocket` | Canlı yayın (WebSocket handler, oturum kaydı, broadcaster) |
| `notification-rest-api` | REST controller, DTO'lar, kimlik çözücü |
| `notification-spring-boot-starter` | Üç modülü birbirine bağlayan otomatik yapılandırma — **kullanan uygulamanın eklediği tek bağımlılık budur** |
| `notification-example` | Starter'ı kullanan çalışan minimal uygulama |

**Java paket kökü:** `io.github.bilalefeuysl.notification`

---

## Hızlı başlangıç (60 saniye)

```bash
# 1. PostgreSQL'i başlat
docker compose up -d

# 2. Backend'i derle ve yerel Maven deposuna kur
cd notification-parent
mvn clean install

# 3. Örnek uygulamayı çalıştır
cd notification-example
java -jar target/notification-example-0.1.0-SNAPSHOT.jar
```

Uygulama `http://localhost:8080` üzerinde açılır. Deneyin:

```bash
# bir bildirim yayınla (örnek uygulamanın kendi test ucu)
curl -X POST "http://localhost:8080/example/publish?classification=Sensor+Alarmi&message=Tank+3+esik+asildi&type=WARNING"

# örnek konfigürasyonda hedefleme AÇIK, bu yüzden liste istekleri kimlik başlığı ister
curl -H "X-User-Id: user1" http://localhost:8080/api/notifications
```

> Backend'i derlemek için **Docker Desktop çalışıyor olmalı** — `notification-core`
> ve `notification-spring-boot-starter` testleri Testcontainers ile geçici bir
> PostgreSQL konteyneri ayağa kaldırır. Bkz. [Geliştirme](#geliştirme).

---

## Kütüphaneyi projenize ekleme

```xml
<dependency>
  <groupId>io.github.bilalefeuysl.notification</groupId>
  <artifactId>notification-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Projenizde PostgreSQL'e bağlı bir `DataSource` bean'i zaten varsa başka hiçbir şey
yapmanıza gerek yok — kütüphane tablolarını kendisi oluşturur, WebSocket ve REST
uçlarını kendisi kaydeder, Flyway migration'larını kendisi çalıştırır. Bildirimler
için **ayrı** bir veritabanı bağlantısı kullanmak isterseniz `notification.datasource.*`
ayarlarını doldurun (bkz. [Konfigürasyon](#konfigürasyon)).

**Hiçbir Flyway ayarı gerekmez.** Kütüphane migration'larını kendi geçmiş tablosuyla
(`notification_schema_history`) çalıştırır — uygulamanızın tuttuğu herhangi bir Flyway
geçmişinden ayrı.

- **Uygulamanız Flyway kullanmıyorsa:** kütüphane bir `Flyway` bean'i kaydeder, böylece
  Spring Boot'un kendi Flyway yapılandırması geri çekilir
  (`@ConditionalOnMissingBean(Flyway.class)`) ve kütüphane tabloları oluşturduktan sonra
  *"Found non-empty schema(s) but no schema history table"* hatasıyla çökmez.
- **Uygulamanız Flyway'i standart şekilde kullanıyorsa** (`classpath:db/migration` altında
  migration'lar veya `spring.flyway.locations` ayarlı), kütüphane bunu algılar, o
  "bastırıcı" bean'i **kaydetmez** ve Spring Boot'un sizin migration'larınızı normal
  çalıştırmasına izin verir. Ayrıca Spring Boot'un Flyway'inin, kütüphane şemayı
  doldurmadan *önce* çalışmasını sağlar (böylece boş-olmayan-şema kontrolü yine geçer).
  Kütüphanenin kendi şeması, kendi geçmiş tablosuyla bağımsız hazırlanır. (Log satırı:
  *"uygulamanın kendi Flyway migration'ları bulundu … 'bastırıcı' Flyway bean'ini
  kaydetmiyor"*.)

Yalnızca migration'larınız varsayılan olmayan bir konumdaysa ve `spring.flyway.locations`
ayarını kullanmıyorsanız elle bir `@Bean Flyway` gerekir — Spring Boot'un standart "çoklu
Flyway" deseni:

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

## Bildirim yayınlama

**Basit** (tek satır, tek import):

```java
notificationService.publish("Sensör Alarmı", "Tank 3 sıcaklık eşiği aşıldı");

// tip belirterek (rengi belirler: INFO / SUCCESS / WARNING / ERROR)
notificationService.publish("Sensör Alarmı", "Tank 3 eşiği aşıldı", NotificationType.WARNING);
```

**Gelişmiş** (ekstra alanlar — kaynak cihaz kimliği, serbest metadata, audience):

```java
notificationService.publish(
    NotificationCommand.builder()
        .classification("Sensör Alarmı")           // başlık (zorunlu, en fazla 128 karakter)
        .message("Tank 3 sıcaklık eşiği aşıldı: 94°C")  // içerik (zorunlu)
        .type(NotificationType.WARNING)            // ya da serbest metin, en fazla 32 karakter
        .priority(NotificationPriority.HIGH)       // LOW / NORMAL (varsayılan) / HIGH
        .sourceDeviceId("PLC-42")                  // opsiyonel, en fazla 128 karakter
        .metadataEntry("sicaklik", 94)             // opsiyonel serbest JSON verisi
        .audience(new NotificationAudience.SpecificUser("user-42")) // bkz. Hedefli bildirim
        .build());
```

Her başarılı `publish()` bir `NotificationPublishedEvent` yayınlar; kullanan uygulama
`@EventListener` ile kütüphaneye dokunmadan ek davranış (e-posta, metrik…) ekleyebilir.

---

## İki dilli içerik (Türkçe / İngilizce)

`classification` / `message` **varsayılan metindir** — hangi dilde isterseniz yazın.
Bir bildirim ek olarak bir **İngilizce** karşılık taşıyabilir; React arayüzü bunu
dili `en` olan kullanıcılara gösterir, geri kalan herkese varsayılana düşer.

```java
notificationService.publish(
    NotificationCommand.builder()
        .classification("Bakım başladı")            // varsayılan metin (zorunlu)
        .message("Sistem 02:00'de kapanacak")
        .classificationEn("Maintenance started")    // opsiyonel İngilizce karşılık
        .messageEn("System goes down at 02:00")
        .build());
```

Kurallar:

- İngilizce karşılık **ya tam ya hiç** — `classificationEn` ve `messageEn` ikisi
  birlikte verilir ya da hiçbiri. Yarım verilirse `build()` hata fırlatır; veritabanı
  da aynısını bir `CHECK` constraint'i ile zorlar.
- Sadece İngilizce içeriğiniz mi var? `classification` / `message`'a yazıp `*En`
  metotlarını atlayın — o zaman herkes İngilizce görür.
- Çözümleme **istemci tarafında** olur (WebSocket tek mesajı tüm tarayıcılara
  gönderir), bu yüzden iki karşılık da `NotificationDto` / WebSocket payload'ında
  `classificationEn` / `messageEn` olarak tarayıcıya gider (yoksa `null`).
- Serbest metin arama (`?q=`) İngilizce metinde de eşleşir.
- Migration `V7`, veritabanı sütunları `classification` / `message`'ı
  `classification_tr` / `message_tr` olarak yeniden adlandırır (yeni nullable
  `classification_en` / `message_en` ile simetrik). REST / WebSocket JSON alan
  adları değişmez — `classification`, `message`, `classificationEn`, `messageEn`.
- React arayüzüne paralel olarak yalnızca Türkçe/İngilizce'dir.

---

## Konfigürasyon

Tüm anahtarlar `application.yml` veya `application.properties` içinde `notification.*`
altındadır. Bozuk değerler (boş zorunlu metinler, pozitif olmayan limitler)
**uygulama açılışında** net bir mesajla reddedilir, çalışma anında değil.

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `notification.enabled` | `true` | Ana anahtar. `false` tüm bean'leri ve otomatik yapılandırmayı kapatır. |
| `notification.table-name` | `notifications` | Bildirim tablosunun adı. |
| `notification.schema` | `public` | Kütüphanenin tablolarını tutacak şema. |
| `notification.initialize-schema` | `true` | Kütüphane açılışta kendi tablolarını Flyway ile oluşturup güncellesin mi. Kendi `NotificationRepository`'nizi (örn. bellek-içi) verdiğinizde ya da şemayı kendiniz yönettiğinizde `false` yapın — kütüphane o zaman hiçbir `DataSource`'a dokunmaz ve `Flyway` bean'i kaydetmez. Bkz. [Genişletme noktaları](#genişletme-noktaları). |
| `notification.datasource.url` | *(boş)* | Doldurulursa kütüphane **kendi** bağlantı havuzunu açar. Boşsa uygulamanın mevcut `DataSource`'u kullanılır. |
| `notification.datasource.username` | *(boş)* | Ayrı bağlantı için kullanıcı adı. |
| `notification.datasource.password` | *(boş)* | Ayrı bağlantı için parola. |
| `notification.websocket.enabled` | `true` | WebSocket katmanı. `false` → bildirimler yalnızca REST ile okunur. |
| `notification.websocket.path` | `/ws/notifications` | WebSocket el sıkışma (handshake) yolu. |
| `notification.rest.enabled` | `true` | REST katmanı. `false` → controller kaydedilmez. |
| `notification.rest.base-path` | `/api/notifications` | Tüm REST uçlarının kök yolu. |
| `notification.rest.default-limit` | `25` | İstemci `limit` vermezse dönülecek sayfa boyutu. |
| `notification.rest.max-limit` | `100` | İstemcinin isteyebileceği en yüksek `limit`; üstü bu değere kırpılır. |
| `notification.targeting.enabled` | `false` | Kişiye/role özel hedeflemeyi açar — bkz. [Hedefli bildirim](#hedefli-bildirim). |
| `notification.cors.allowed-origins` | *(boş)* | REST uçlarına **ve** WebSocket'e erişebilecek çapraz-origin adresleri. Boş → çapraz-origin erişim kapalı (yalnızca backend ile aynı origin'den sunulan frontend bağlanır). Bkz. [Güvenlik ve CORS](#güvenlik-ve-cors). |

Ayrı bir `notification.datasource.url` kullanırsanız kütüphanenin classpath'te bir
bağlantı havuzuna ihtiyacı olur. HikariCP varsa otomatik kullanılır
(`spring-boot-starter-jdbc` / `-data-jpa` onu getirir). URL ayarlı ama HikariCP yoksa
uygulama, sessizce yanlış veritabanına düşmek yerine net bir mesajla açılmaz.

---

## Güvenlik ve CORS

Tarayıcı, bir sayfanın JavaScript'inin **farklı bir origin**'e (protokol + host +
port) istek atıp cevabını okumasını, hedef sunucu açıkça izin vermedikçe engeller
(CORS). Bu kütüphane izinli origin'leri `notification.cors.allowed-origins`'den okur:

```yaml
notification:
  cors:
    allowed-origins:
      - https://uygulamam.example.com
      - http://localhost:5173   # yerel geliştirme (Vite vb.)
```

- **Frontend backend ile aynı origin'den sunuluyorsa** (Spring statik dosyaları serve
  ediyor ya da ikisi tek alan adı / ters vekil arkasında) → bu ayara **gerek yok**;
  aynı-origin istekleri CORS'a girmez.
- **Frontend farklı bir origin'deyse** → origin'lerini yukarıdaki gibi ekleyin. Aynı
  liste hem REST uçlarına hem WebSocket el sıkışmasına uygulanır.
- Joker (`*`) **kasıtlı olarak** desteklenmez: her origin'e açık bırakmak, giriş
  yapmış bir kullanıcının tarayıcısı üzerinden bildirim akışının kötü niyetli bir
  siteye sızmasına yol açar (Cross-Site WebSocket Hijacking).

CORS yalnızca tarayıcıları bağlar; tarayıcı-dışı istemciler (`curl`, script) onu
atlar. Kimliğe dayalı erişim kontrolü için Hedefli bildirim bölümündeki
[güvenlik uyarısına](#güvenlik-uyarısı) bakın.

---

## REST API

Taban yol yapılandırılabilir (`notification.rest.base-path`, varsayılan
`/api/notifications`). Tüm cevaplar JSON'dur. [Hedefleme](#hedefli-bildirim) açıkken
her istek bir `X-User-Id` başlığı taşımalıdır (opsiyonel `X-User-Roles: ADMIN,EDITOR`);
eksik `X-User-Id` `400 INVALID_REQUEST` döner.

| Metot | Yol | Amaç |
|---|---|---|
| `GET` | `{base}` | Sayfalanmış liste (en yeni önce). Sorgu parametreleri aşağıda. |
| `GET` | `{base}/unread-count` | `{ "count": <n> }` — toplam okunmamış, sayfalamadan bağımsız. |
| `DELETE` | `{base}/{id}` | Tek bildirimi gizle (soft delete). `204`. |
| `DELETE` | `{base}` | Görünür tüm bildirimleri gizle. `204`. |
| `PATCH` | `{base}/read` | Id'leri okundu işaretle. Gövde: id metinlerinden JSON dizisi. `204`. |
| `PATCH` | `{base}/{id}/saved` | Kaydet / kaldır. Gövde: `{ "saved": true }` veya `{ "saved": false }`. `204`. |

### `GET {base}` sorgu parametreleri

| Parametre | Tip | Açıklama |
|---|---|---|
| `before` | ISO-8601 an | Sonraki sayfa için zaman imleci. İlk sayfada verilmez. |
| `limit` | tam sayı | Sayfa boyutu (`max-limit`'e kırpılır). `> 0` olmalı. |
| `priority` | `LOW` / `NORMAL` / `HIGH` | Yalnızca bu önceliği döndürür. |
| `saved` | `true` | Yalnızca kaydedilmiş bildirimleri döndürür ("kaydedilenler" görünümü). |
| `q` | metin | Başlık, içerik, İngilizce başlık/içerik, tip, kaynak cihaz kimliği ve biçimlenmiş tarih (`GG.AA.YYYY SS:DD`) üzerinde serbest metin arama. Büyük/küçük harf duyarsız; `%` / `_` harfi harfine eşleşir. |
| `sort` | `priority` | Opt-in öncelik sıralaması (HIGH → NORMAL → LOW, sonra en yeni önce). `q` / `saved` / `priority` ile birlikte kullanılamaz (`400` döner). |
| `priorityCursor` | opak metin | `sort=priority` iken sayfa imleci. Önceki cevabın `nextPriorityCursor`'unu olduğu gibi geri gönderin. |

### Cevap şekli

```json
{
  "items": [
    {
      "id": "…",
      "classification": "Sensör Alarmı",
      "message": "Tank 3 eşiği aşıldı",
      "classificationEn": null,
      "messageEn": null,
      "type": "WARNING",
      "priority": "HIGH",
      "read": false,
      "saved": false,
      "createdAt": "2026-08-28T10:15:30Z",
      "metadata": { "sicaklik": 94 },
      "sourceDeviceId": "PLC-42"
    }
  ],
  "hasMore": true,
  "nextBefore": "2026-08-28T09:00:00Z",
  "nextPriorityCursor": null
}
```

`nextBefore` son sayfada `null`'dır. `nextPriorityCursor` **yalnızca** `sort=priority`
istendiğinde doludur (aksi halde `null`).

---

## WebSocket API

`ws://host:port{websocket.path}` adresine bağlanın (varsayılan `/ws/notifications`).
[Hedefleme](#hedefli-bildirim) açıkken kimliği query parametresi olarak geçirin —
tarayıcının yerleşik WebSocket API'si el sıkışma sırasında özel başlık ekleyemez:

```
ws://host:port/ws/notifications?userId=user-42&roles=ADMIN,EDITOR
```

Eksik kimlik `401 Unauthorized` ile reddedilir, bağlantı hiç kurulmaz.

### Sunucudan gelen mesajlar

| Olay | Gövde | Anlamı |
|---|---|---|
| `NOTIFICATION_CREATED` | tam bir bildirim nesnesi (REST `items[]` öğesiyle aynı şekil) | Bu bağlantı için yeni bir bildirim yayınlandı. |
| `NOTIFICATION_HIDDEN` | `{ "ids": ["…"] }` | Bu bildirimler gizlendi (başka bir sekmede olabilir). |
| `NOTIFICATION_READ` | `{ "ids": ["…"] }` | Bu bildirimler okundu işaretlendi. |
| `NOTIFICATION_ALL_HIDDEN` | *(yok)* | Tüm bildirimler gizlendi — istemci listesini temizlemeli. |
| `PONG` | *(yok)* | İstemcinin `PING` canlı-tutma mesajına cevap. |

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

`HIDDEN` / `READ` / `ALL_HIDDEN` olayları aynı kullanıcının birden fazla tarayıcı
sekmesini senkron tutar.

---

## Hedefli bildirim

Varsayılan olarak kütüphane **hedeflemesiz** çalışır: yayınlanan her bildirim, kim
olduğu önemli olmadan bağlı tüm tarayıcılara gider. Bildirimleri belirli bir
kullanıcıya veya role iletmek için açın:

```yaml
notification:
  targeting:
    enabled: true
```

<a name="güvenlik-uyarısı"></a>
> ⚠️ **Bunu kimlik doğrulaması olmadan üretimde kullanmayın.**
> Kütüphanenin varsayılan kimlik çözücüsü (`HeaderNotificationIdentityResolver`)
> kimliği doğrudan `X-User-Id` / `X-User-Roles` başlıklarından (WebSocket'te
> `?userId=…&roles=…` query parametrelerinden) okur ve **bu değerleri doğrulamaz**.
> İsteği yapan herkes istediği kimliğe bürünebilir:
>
> ```bash
> curl http://localhost:8080/api/notifications -H "X-User-Id: patron"
> ```
>
> Bu **bilerek** böyledir — kütüphane gerçek kimlik doğrulamayı uygulamaya bırakır.
> Varsayılan çözücü yalnızca güvenilir bir ağda (örn. kimliği zaten doğrulayıp bu
> başlıkları ekleyen bir API gateway'in arkasında) veya yerel geliştirmede güvenlidir.
> Herkese açık bir dağıtımda **mutlaka**:
> 1. İsteğin önüne kimlik doğrulayan bir katman koyun (Spring Security, gateway, mTLS…),
> 2. Kimliği ham başlıktan değil, **doğrulanmış** `SecurityContext`'ten okuyan kendi
>    `NotificationIdentityResolver` bean'inizi tanımlayın (aşağıda tam örnek).
>
> [CORS ayarı](#güvenlik-ve-cors) tarayıcı kaynaklı saldırıları sınırlar ama
> `curl` / script bunu tamamen atlar — yani CORS, kimlik doğrulamanın yerini **tutmaz**.

Açıldığında:

- Her bildirimin bir **audience**'ı olabilir (varsayılan `Everyone` — herkese gider,
  hedeflemesiz davranışla birebir aynı):

  ```java
  notificationService.publish(
      NotificationCommand.builder()
          .classification("Onay bekliyor")
          .message("Formunuz onaya gönderildi")
          .audience(new NotificationAudience.SpecificUser("user-42"))  // sadece bu kullanıcıya
          // .audience(new NotificationAudience.Role("ADMIN"))         // ya da bu role sahip herkese
          .build());
  ```

- **REST istekleri** kimliği `X-User-Id`'den okur (opsiyonel `X-User-Roles: ADMIN,EDITOR`,
  virgülle ayrılmış). Eksik `X-User-Id` `400 Bad Request` (`INVALID_REQUEST`) döner.
- **WebSocket bağlantısı** aynı bilgiyi query parametresi olarak bekler
  (`?userId=user-42&roles=ADMIN,EDITOR`). Eksik kimlik el sıkışmayı `401 Unauthorized`
  ile başarısız kılar.
- Okundu / gizli / **kaydedildi** durumu artık **kişiye özeldir** (ayrı bir
  `notification_user_state` tablosunda tutulur); hedefleme açıkken kütüphane bunun
  için bir ek Flyway migration'ı daha çalıştırır.
- Kimlik çözme takılabilir. `NotificationIdentityResolver` implemente edip bir `@Bean`
  olarak kaydedin. **Aynı isimde iki ayrı arayüz vardır** —
  `...rest.identity.NotificationIdentityResolver` (REST controller kullanır) ve
  `...websocket.identity.NotificationIdentityResolver` (WebSocket katmanı kullanır).
  Her modül kendi bean'ini bekler; biri diğerinin yerine geçmez.

### Üretim için tam örnek — Spring Security + kimliğe bağlı çözücü

Aşağıdaki üç parça hedeflemeyi güvenli kılar: (1) bildirim uçlarını kimlik
doğrulamasına kapatan bir `SecurityFilterChain`, (2) `SecurityContext`'ten okuyan bir
REST çözücüsü, (3) WebSocket için aynısı. Bu bean'ler tanımlandığında kütüphanenin
varsayılan `HeaderNotificationIdentityResolver`'ı `@ConditionalOnMissingBean`
sayesinde otomatik devre dışı kalır.

**1. Bildirim uçlarını koruyan güvenlik zinciri** (burada JWT resource-server; form
login / session / kendi filtreniz de olur):

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

> Tarayıcının yerleşik WebSocket API'si el sıkışmada `Authorization` başlığı
> gönderemez. JWT kullanıyorsanız token'ı query parametresiyle taşıyıp
> (`ws://.../ws/notifications?access_token=…`) bir `BearerTokenResolver` ile okutmanız
> gerekir. Session (çerez) tabanlı auth'ta bu sorun yoktur — çerez otomatik gider.

**2. REST çözücüsü — kimliği `SecurityContext`'ten alır:**

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
            // Güvenlik zinciri zaten 401 döndürür; bu yalnızca bir emniyet ağı.
            throw new IllegalStateException("Bildirim isteği kimlik doğrulanmadan geldi");
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

**3. WebSocket çözücüsü — el sıkışmadaki doğrulanmış `Principal`'dan alır:**

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
            throw new IllegalStateException("WebSocket el sıkışması kimlik doğrulanmadan geldi");
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .collect(Collectors.toSet());
        return new NotificationIdentity(auth.getName(), roles);
    }
}
```

Bu kurulumda `X-User-Id` / `?userId=` hiç okunmaz — kimlik tamamen doğrulanmış
token/session'dan gelir, taklit edilemez.

---

## Genişletme noktaları

Birkaç çekirdek bileşen `@ConditionalOnMissingBean` ile kaydedilmiş düz
arayüzlerdir; aynı tipte kendi `@Bean`'inizi tanımlamak kütüphanenin varsayılanını
devre dışı bırakır:

| Arayüz | Varsayılan | Değiştirirseniz… |
|---|---|---|
| `NotificationRepository` | `JdbcNotificationRepository` (PostgreSQL) | bildirimleri başka bir yerde saklarsınız (başka DB, testlerde bellek-içi). |
| `NotificationService` | `DefaultNotificationService` | yayınlama/sorgulama davranışını değiştirirsiniz. |
| `NotificationBroadcaster` | `LocalBroadcaster` (tek JVM) | Redis Pub/Sub, Kafka vb. ile instance'lar arası yayın yaparsınız (bkz. [Bilinen kısıtlamalar](#bilinen-kısıtlamalar)). |
| `NotificationIdentityResolver` (REST ve WebSocket — iki arayüz) | `HeaderNotificationIdentityResolver` | kimliği auth katmanınıza bağlarsınız (bkz. [Hedefli bildirim](#hedefli-bildirim)). |

### Kendi `NotificationRepository`'nizi yazmak

`NotificationRepository` düz bir arayüzdür — aynı tipte bir `@Bean` tanımlamak
`JdbcNotificationRepository`'yi tamamen devre dışı bırakır. İki nokta:

1. **Soyut (abstract) metotların hepsi hedeflemesizdir.** `…ForIdentity` metotları
   `default`'tur ve `UnsupportedOperationException` fırlatır. Bunları yalnızca
   `notification.targeting.enabled=true` ile de çalışıyorsanız override edin.
2. **Kütüphanenin şema yönetimini kapatın:** `notification.initialize-schema=false`.
   Yoksa açılışta yine bir `DataSource`'a karşı Flyway çalıştırmaya çalışır.
   Veritabanı kullanmayan bir repository'de ve başka bir `DataSource` ihtiyacınız
   yoksa Spring Boot'un kendi veri-kaynağı otomatik yapılandırmasını da hariç tutun:

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
 * Bildirimleri yalnızca bellekte tutar — yeniden başlatınca kaybolur. Testler,
 * demolar ya da kalıcılığa ihtiyacı olmayan tek düğüm için. Hedefli bildirim
 * ({@code notification.targeting.enabled=true}) DESTEKLENMEZ.
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

    // en yeni önce; `before` createdAt üzerinde bir keyset imleci (null = en baştan)
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

    // opt-in ?sort=priority : HIGH → NORMAL → LOW, sonra en yeni, sonra id (3 parçalı keyset)
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

    // countUnread() varsayılan implementasyonu iş görür; gerekirse verim için override edin.

    private static Notification copy(Notification n, boolean visible, boolean read, boolean saved) {
        return new Notification(n.id(), n.classification(), n.message(),
                n.classificationEn(), n.messageEn(), n.type(), n.priority(), n.sourceDeviceId(),
                n.createdAt(), visible, read, saved, n.metadata(), n.audience());
    }
}
```

> Bu sınıfın tam aynısı starter modülündeki `NotificationCustomRepositoryTest`
> tarafından derlenip çalıştırılıyor.

---

## React arayüz paketi

Tarayıcı tarafı [`notification-react/`](./notification-react/) içindedir ve npm'e ayrı
yayınlanır. `NotificationProvider` (veri + WebSocket + uygulama geneli tema/dil),
`NotificationBell` (zil ikonu + panel), `PopupStack` (zilden bağımsız render edilen
köşe pop-up'ları) ve bir `useNotifications` hook'u sunar; bu backend'e WebSocket ile
bağlanır ve canlı bildirimleri gösterir.

Tam bileşen ve prop referansı için bkz.
[`notification-react/README.tr.md`](./notification-react/README.tr.md).

---

## Bilinen kısıtlamalar

**Tek instance için tasarlanmıştır.** Varsayılan `NotificationBroadcaster`,
`LocalBroadcaster`'dır: yayınlanan bir bildirim yalnızca **aynı JVM sürecine** bağlı
tarayıcılara ulaşır. Bir yük dengeleyici arkasında birden fazla instance / pod
çalıştırıyorsanız, bir instance'a bağlı bir tarayıcı başka bir instance'da yayınlanan
bildirimi almaz. `NotificationBroadcaster` arayüzü bu yüzden kasıtlı olarak soyuttur —
bir mesaj aracısıyla (Redis Pub/Sub, Kafka…) desteklenen bir implementasyon yazıp
`@Bean` olarak kaydederek `LocalBroadcaster`'ın yerine geçirin. Kütüphane böyle bir
implementasyonu henüz sunmuyor.

**Diğer kısıtlar.** Yalnızca PostgreSQL (JSONB / partial index / satır-değeri
sayfalama). Eski bildirimler için otomatik saklama/arşivleme yok. Yerleşik
flood / dedupe koruması yok. Serbest metin arama (`q=`) düz bir `ILIKE`'tır ve tam
metin indeksiyle desteklenmez.

---

## Sorun giderme

**`mvn clean install` "Could not find a valid Docker environment" ile başarısız.**
`notification-core` ve `notification-spring-boot-starter` testleri çalışan bir Docker
daemon gerektiren Testcontainers kullanır. Docker Desktop'ı başlatıp tekrar deneyin.

**`mvn deploy` / plugin indirme `PKIX path building failed` / `certificate_unknown`
ile başarısız.** Ağınız (kurumsal proxy / SSL inceleme) Maven Central'a giden TLS'i
kesiyor. `~/.m2/settings.xml`'i proxy'nizin CA'sıyla yapılandırın veya kısıtsız bir
ağda derleyin. Apache lisans-başlığı kontrol plugin'i tam bu yüzden opt-in `license`
profilinde tutuluyor — varsayılan build onu hiç indirmez.

**Kullanan uygulama açılışta "Found more than one migration with version 1" veya
"Found non-empty schema(s) but no schema history table" ile çöküyor.** Eski bir düzen
kütüphanenin migration'larını `classpath:db/migration` altına koyuyordu; Spring
Boot'un kendi Flyway'i orayı tarıyordu. Artık `classpath:db/notification-migration/{core,targeting}`
altındalar, kendi geçmiş tablolarıyla (`notification_schema_history`,
`notification_targeting_schema_history`) ve kütüphane kendi `Flyway` bean'ini
kaydediyor, böylece Spring Boot'un Flyway'i geri çekiliyor. Bunu görüyorsanız güncel
bir sürümde olduğunuzdan ve eski migration klasörünü kopyalamadığınızdan emin olun.

**Kütüphaneyi ekledikten sonra kendi Flyway migration'larım (`classpath:db/migration`)
çalışmaz oldu.** Düzeltildi. Kütüphane eskiden Spring Boot'un Flyway yapılandırmasını
bastırmak için *her zaman* bir `Flyway` bean'i kaydediyordu; Boot'un `FlywayConfiguration`
sınıfı class seviyesinde `@ConditionalOnMissingBean(Flyway.class)` olduğu için bu, sizin
`db/migration` script'lerinizi çalıştıran `FlywayMigrationInitializer`'ı da sessizce devre
dışı bırakıyordu. Kütüphane artık migration'larınızı algılıyor
(`classpath:db/migration/**/*.sql` veya `spring.flyway.locations` ayarlı) ve geri
çekiliyor; ayrıca Spring Boot'un Flyway'ini kendi şema kurulumundan önce sıralıyor.
Migration'larınız başka bir yerdeyse ve `spring.flyway.locations` ayarlamıyorsanız, açıkça
bir `@Bean Flyway` tanımlayın (yukarıdaki Flyway bölümüne bakın). Güncel bir sürümde
olduğunuzdan emin olun.

**Bir `SPECIFIC_USER` bildirimi herkese görünüyor.** Hedefleme aslında aktif değil.
Açılış logunda `hedefleme: true` (`targeting: true`) olduğunu ve
`notification_targeting_schema_history` / `notification_user_state` migration'larının
çalıştığını kontrol edin. Eski bir Flyway "baseline" kaydıyla hedefleme `V1`'i
atlanabilir; kütüphane bunu önlemek için artık `baselineVersion("0")` ayarlıyor —
yine, güncel bir sürümde olduğunuzdan emin olun.

**Hedefleme açık ama istekler açılışta "NotificationIdentityResolver verilmedi" ile
başarısız.** `notification.targeting.enabled=true` iken kütüphanenin bir
`NotificationIdentityResolver` bean'ine ihtiyacı var. Varsayılan
(`HeaderNotificationIdentityResolver`) otomatik kaydedilir; kendi bean'inizi
tanımladıysanız tipinin modüle doğru olanı (REST vs WebSocket — aynı isimde farklı
arayüzler) olduğundan emin olun.

**Eksik `X-User-Id` `400` dönüyor, `500` değil.** Bu kasıtlıdır — hedefleme açıkken
kimliksiz bir istek bir istemci hatasıdır.

**Kütüphaneyi ekledikten sonra uygulamam bilinmeyen JSON alanlarını reddediyor /
tarih formatları ve `@JsonInclude` ayarları değişti.** Kütüphane eskiden kendi iç
`ObjectMapper`'ını Spring Boot'un `JacksonAutoConfiguration`'ından önce kaydediyordu;
bu, Boot'u geri çektiriyor (`@ConditionalOnMissingBean(ObjectMapper.class)`) ve tüm
uygulama kütüphanenin çıplak mapper'ını kullanıyordu. Düzeltildi:
`NotificationAutoConfiguration` artık `@AutoConfigureAfter(JacksonAutoConfiguration.class)`
— Boot'un `@Primary` `ObjectMapper`'ı kazanıyor, kütüphanenin `notificationObjectMapper`'ı
ikincil ve yalnızca iç kullanıma yönelik bir bean olarak kalıyor. Güncel sürümde
olduğunuzdan emin olun; artık tüketici tarafında bir çözüm (`@Primary ObjectMapper`
bean'i) gerekmiyor.

**`mvn spring-boot:run` yerine `java -jar`.** Bazı ortamlarda (örn. ASCII olmayan
karakterli bir Windows kullanıcı adı) `mvn spring-boot:run` sorun çıkarır; derlenmiş
jar'ı doğrudan çalıştırın:

```bash
cd notification-example
java -jar target/notification-example-0.1.0-SNAPSHOT.jar
```

---

## Geliştirme

Tam kurulum, build ve test akışı için bkz. [CONTRIBUTING.md](./CONTRIBUTING.md).
Kısaca:

- Backend testleri için **Docker Desktop çalışıyor olmalı** (Testcontainers).
- Backend: `cd notification-parent && mvn clean install`
- Frontend: `cd notification-react && npm install && npm test && npm run build`
- Yerel veritabanı: `docker compose up -d` (PostgreSQL `localhost:5432`,
  db/kullanıcı/parola hepsi `notification`).

---

## Lisans

Apache License 2.0 — bkz. [LICENSE](./LICENSE).
