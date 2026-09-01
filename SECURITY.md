# Security Policy

_[English](#english) · [Türkçe](#türkçe)_

---

## English

### Supported versions

The library is pre-1.0. Security fixes land on the latest `0.x` release only.

| Version | Supported |
|---|---|
| latest `0.x` | ✅ |
| older | ❌ |

### Reporting a vulnerability

**Do not open a public GitHub issue for a security vulnerability.**

Email **bilalefeuysl@gmail.com** with:

- a description of the issue and its impact,
- steps to reproduce (proof-of-concept if possible),
- affected version(s) and environment.

You will get an acknowledgement within a few days. Once a fix is available, a patched
release is published and the report is credited (unless you prefer to stay anonymous).

### Known, by-design behaviour — not a vulnerability

- **The default identity resolver does not validate `X-User-Id` / `?userId=`.** When
  `notification.targeting.enabled=true`, the built-in `HeaderNotificationIdentityResolver`
  trusts the identity headers / query params as-is. This is documented and intentional
  — the library delegates authentication to the application. A public deployment must
  put an auth layer in front and supply an identity-bound `NotificationIdentityResolver`.
  See the [security section of the README](./README.md#security-warning).
- **`notification.cors.allowed-origins` is empty by default**, which blocks
  cross-origin browser access. The wildcard `*` is deliberately unsupported.

If you believe one of the above can be exploited *beyond* what the documentation
describes, please do report it.

---

## Türkçe

### Desteklenen sürümler

Kütüphane 1.0 öncesidir. Güvenlik düzeltmeleri yalnızca en son `0.x` sürümüne gelir.

| Sürüm | Destekleniyor |
|---|---|
| en son `0.x` | ✅ |
| daha eski | ❌ |

### Güvenlik açığı bildirimi

**Bir güvenlik açığı için herkese açık GitHub issue açmayın.**

**bilalefeuysl@gmail.com** adresine şunları içeren bir e-posta gönderin:

- sorunun ve etkisinin açıklaması,
- tekrar üretme adımları (mümkünse kavram kanıtı),
- etkilenen sürüm(ler) ve ortam.

Birkaç gün içinde bir yanıt alırsınız. Düzeltme hazır olduğunda yamalı bir sürüm
yayınlanır ve bildirim size atfedilir (anonim kalmayı tercih etmezseniz).

### Bilinen, tasarım gereği davranış — güvenlik açığı değildir

- **Varsayılan kimlik çözücüsü `X-User-Id` / `?userId=` değerlerini doğrulamaz.**
  `notification.targeting.enabled=true` iken yerleşik `HeaderNotificationIdentityResolver`
  kimlik başlıklarına / query parametrelerine olduğu gibi güvenir. Bu belgelidir ve
  kasıtlıdır — kütüphane kimlik doğrulamayı uygulamaya bırakır. Herkese açık bir
  dağıtım, önüne bir auth katmanı koymalı ve kimliğe bağlı bir
  `NotificationIdentityResolver` sağlamalıdır. Bkz.
  [README güvenlik bölümü](./README.tr.md#güvenlik-uyarısı).
- **`notification.cors.allowed-origins` varsayılan olarak boştur**, bu da çapraz-origin
  tarayıcı erişimini engeller. Joker `*` kasıtlı olarak desteklenmez.

Yukarıdakilerden birinin, dokümantasyonun anlattığının *ötesinde* istismar
edilebileceğini düşünüyorsanız lütfen bildirin.
