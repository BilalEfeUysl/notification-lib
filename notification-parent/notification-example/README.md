# notification-example

`notification-spring-boot-starter`'ı kullanan minimal, çalışan örnek uygulama.

## Çalıştırma

1. PostgreSQL'i ayağa kaldır (proje kökünden):

```
docker compose up -d
```

2. Uygulamayı başlat (bu modülün içinden):

```
mvn spring-boot:run
```

Uygulama `http://localhost:8080` üzerinde ayağa kalkar.

## Test etme

Bildirim yayınlama (WebSocket'e canlı düşer, DB'ye kalıcı yazılır):

```
curl -X POST "http://localhost:8080/example/publish?classification=Sensor+Alarmi&message=Test+mesaji&type=WARNING"
```

Bildirimleri listeleme:

```
curl http://localhost:8080/api/notifications
```

Tek bir bildirimi gizleme:

```
curl -X DELETE http://localhost:8080/api/notifications/{id}
```

WebSocket adresi (tarayıcı konsolu veya `wscat` ile):

```
ws://localhost:8080/ws/notifications
```

## Konfigürasyon

| Anahtar | Varsayılan | Açıklama |
|---|---|---|
| `notification.enabled` | `true` | Kütüphaneyi tamamen kapatır/açar |
| `notification.websocket.enabled` | `true` | WebSocket katmanı |
| `notification.rest.enabled` | `true` | REST katmanı |
| `notification.rest.base-path` | `/api/notifications` | REST uçlarının kök yolu |
| `notification.websocket.path` | `/ws/notifications` | WebSocket yolu |
| `notification.rest.default-limit` | `25` | Sayfa başına varsayılan kayıt sayısı |
| `notification.rest.max-limit` | `100` | İzin verilen en büyük sayfa boyutu |