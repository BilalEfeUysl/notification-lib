# notification-react

[English](./README.md) · **Türkçe**

[Bildirim kütüphanesinin](../README.tr.md) React arayüzü: backend'e bağlanan bir
`NotificationProvider`, bir `NotificationBell` (zil ikonu + panel + pop-up'lar) ve bir
`useNotifications` hook'u. Canlı bildirimleri köşe pop-up'ları ve kaydırılabilir bir
liste olarak gösterir; okundu / gizli / kaydedildi durumunu sekmeler arasında
senkron tutar.

```bash
npm install @bilalefeuysl/notification-react
```

`react`, `react-dom` ve `antd` **v4** birer **peer dependency**'dir — kullanan
uygulamada zaten kurulu olmalıdır. Ant Design v5 desteklenmez.

---

## İçindekiler

- [Stiller](#stiller)
- [Hızlı başlangıç](#hızlı-başlangıç)
- [`NotificationProvider`](#notificationprovider)
- [`NotificationBell`](#notificationbell)
  - [`badge`](#badge-seçenekleri)
  - [`panel`](#panel-seçenekleri)
  - [Imperative handle & kontrollü panel](#imperative-handle--kontrollü-panel)
  - [Render prop'lar](#render-proplar)
- [`useNotifications`](#usenotifications)
- [Bağımsız bileşenler](#bağımsız-bileşenler)
  - [`PopupStack`](#popupstack)
- [Tema](#tema)
- [Tip renkleri](#tip-renkleri)
- [Uluslararasılaştırma (i18n)](#uluslararasılaştırma-i18n)
- [Hedefleme (kişiye özel bildirim)](#hedefleme-kişiye-özel-bildirim)
- [Öncelik sıralaması](#öncelik-sıralaması)
- [Tarayıcı deposu](#tarayıcı-deposu)
- [Sunucu tarafında render (Next.js)](#sunucu-tarafında-render-nextjs)
- [Erişilebilirlik](#erişilebilirlik)
- [Sorun giderme](#sorun-giderme)

---

## Stiller

Paket stilini ayrı bir CSS dosyası olarak yayınlar. Uygulamanızın giriş noktasında
**bir kez**, Ant Design'ın kendi CSS'inin yanında import edin:

```js
import 'antd/dist/antd.css';
import '@bilalefeuysl/notification-react/styles.css';
```

Bu satır olmadan bileşenler stilsiz görünür.

---

## Hızlı başlangıç

```tsx
import { NotificationProvider, NotificationBell, PopupStack } from '@bilalefeuysl/notification-react';
import '@bilalefeuysl/notification-react/styles.css';

function App() {
  return (
    <NotificationProvider
      basePath="https://api.example.com/api/notifications"
      websocketUrl="wss://api.example.com/ws/notifications"
      theme="dark"
      language="tr"
    >
      <MyHeader>
        <NotificationBell />
      </MyHeader>

      {/* Köşe pop-up yığını. Provider içinde, istediğiniz yere, BİR KERE koyun. */}
      <PopupStack />
    </NotificationProvider>
  );
}
```

`NotificationProvider` geçmişi REST ile çeker, WebSocket'e abone olur ve uygulama
geneli `theme` / `language` varsayılanlarını yayar. `NotificationBell` zili okunmamış
rozetiyle gösterir, tıklanınca paneli açar. `PopupStack` yeni bildirimleri köşede
pop-up olarak gösterir. Provider içindeki herhangi bir bileşen doğrudan
[`useNotifications()`](#usenotifications) de çağırabilir.

> **Zil ve pop-up yığını birbirinden bağımsızdır.** Zil pop-up render **etmez** —
> `<PopupStack />`'i siz yerleştirirsiniz. Böylece zili gizleyebilir (ya da birden
> fazla zil koyabilir) ve pop-up'ları etkilemezsiniz; pop-up'ları ağacın istediğiniz
> yerine koyabilir, hatta hiç zil olmadan kullanabilirsiniz. `PopupStack`'i **bir
> kere** render edin: her örnek kendi kuyruğunu tuttuğu için iki tanesi her bildirimi
> çift gösterir.

---

## `NotificationProvider`

Uygulamanızı (veya bildirime ihtiyaç duyan alt ağacı) bir kez sarın. Veriyi,
WebSocket bağlantısını ve ses / pop-up tercihlerini bu bileşen tutar.

| Prop | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `basePath` | `string` | — (zorunlu) | REST taban yolunun tam URL'si, örn. `https://api.example.com/api/notifications`. |
| `websocketUrl` | `string` | — (zorunlu) | Tam WebSocket URL'si, örn. `wss://api.example.com/ws/notifications`. |
| `initialLimit` | `number` | `25` | İlk yükleme ve her "daha fazla yükle" için sayfa boyutu. |
| `onError` | `(error: Error) => void` | — | Bir REST isteği veya soket başarısız olduğunda çağrılır. Arayüz zaten bir mesaj gösterir; bu loglama içindir. |
| `identity` | `{ userId: string; roles?: string[] }` | — | **Backend'de hedefleme açıkken zorunlu.** REST isteklerine `X-User-Id` / `X-User-Roles` başlığı, WebSocket URL'sine `?userId=…&roles=…` olarak eklenir. Değişmesi soketi yeniden bağlar. |
| `sortByPriority` | `boolean` | `false` | `true` iken liste önceliğe (HIGH → NORMAL → LOW) sonra tarihe göre sıralanır (backend'in opt-in `sort=priority` yolu). Verilmezse backend fazladan iş yapmaz. Bkz. [Öncelik sıralaması](#öncelik-sıralaması). |
| `theme` | `'light' \| 'dark' \| 'auto'` | `'auto'` | Uygulama geneli tema. `NotificationBell` ve `PopupStack` kendi `theme`'i verilmedikçe bunu miras alır. `'auto'` işletim sistemi / tarayıcı renk şemasını izler. |
| `language` | `'tr' \| 'en' \| 'auto'` | `'tr'` | Uygulama geneli arayüz dili. `NotificationBell` ve `PopupStack` kendi `language`'i verilmedikçe bunu miras alır. `'auto'` → `navigator.language`. |
| `credentials` | `RequestCredentials` | — | Tüm REST istekleri için `fetch` credentials modu. **Çerez/oturum tabanlı kimlik doğrulaması kullanıyor ve frontend'i backend'den farklı bir origin'de sunuyorsanız `'include'` verin** — aksi halde tarayıcı çerezi hiç göndermez ve istekler sessizce başarısız olur. |
| `children` | `ReactNode` | — | Uygulamanız. |

---

## `NotificationBell`

Header'ınıza koyduğunuz zil: okunmamış rozeti ve tıklanınca açılan bildirim paneli.
Pop-up **render etmez** — onun için bkz. [`PopupStack`](#popupstack).

| Prop | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `language` | `'tr' \| 'en' \| 'auto'` | provider'dan miras | Arayüz metinleri, tarih biçimi **ve hangi içerik dilinin gösterileceği**. Verilmezse `NotificationProvider`'ın `language`'i kullanılır. Sadece *bu zili* uygulamanın genelinden farklı bir dilde istiyorsanız verin. |
| `theme` | `'light' \| 'dark' \| 'auto'` | provider'dan miras | Verilmezse `NotificationProvider`'ın `theme`'i kullanılır. Sadece *bu zili* uygulamanın genelinden farklı bir temada istiyorsanız verin. |
| `icon` | `ReactNode` | durum göstergeli zil | Özel tetikleyici ikon. Verilmezse kütüphanenin varsayılan zili kullanılır (bkz. `showStatusIcon`); kendi ikonunuzu verirseniz `showStatusIcon`'ın etkisi kalmaz. |
| `showStatusIcon` | `boolean` | `true` | Varsayılan zil, ses/popup durumunu ikon üzerinde gösterir: **ses açıkken** çanın yanında titreşim yayları, **bildirimler kapalıyken** çanın üzerinde çapraz çizgi. Zil sağ-tık menüsünden değiştirilir ("Sesi kapat" / "Bildirimleri kapat"). `false` → sade `<BellOutlined/>`. `icon` verirseniz etkisizdir. |
| `className` | `string` | — | Tetikleyici sarmalayıcısının class'ı. |
| `style` | `CSSProperties` | — | Tetikleyici sarmalayıcısının inline stili. |
| `badge` | `NotificationBadgeOptions` | — | Okunmamış rozeti seçenekleri — [aşağıda](#badge-seçenekleri). |
| `panel` | `NotificationPanelOptions` | — | Panel seçenekleri — [aşağıda](#panel-seçenekleri). |
| `typeStyles` | `Record<string, Partial<TypeStyle>>` | — | `type` başına renk paletini override eder. Bkz. [Tip renkleri](#tip-renkleri). |
| `showUnreadIndicator` | `boolean` | `true` | Listede okunmamış satırlarda küçük bir nokta göster. |
| `timeFormat` | `TimeFormat` | `'full'` | `'short'` \| `'full'` \| `'relative'` \| `'time-only'` \| `(iso, lang) => string`. |
| `showTypeIcons` | `boolean` | `false` | `success` / `error` / `warning` / `info` için başlığın yanında küçük bir ikon göster. |
| `readTrigger` | `'onOpen' \| 'onClick' \| 'manual'` | `'onOpen'` | Bir bildirim ne zaman okundu sayılır. `'onOpen'`: panel kapanınca görünen her şey. `'onClick'`: sadece tıklanan. `'manual'`: hiçbir zaman otomatik — `markAsRead`'i kendiniz çağırırsınız. |
| `open` | `boolean` | — | Kontrollü mod: verildiğinde panelin açık/kapalı durumunu siz yönetip `onOpenChange`'den güncellersiniz. |
| `onOpenChange` | `(open: boolean) => void` | — | Panel her açılmak/kapanmak istediğinde çağrılır (zil tıklaması, dışarı tıklama, `Esc`…). |
| `enableServerSearch` | `boolean` | `false` | `false`: arama o an yüklü bildirimleri anında filtreler. `true`: arama backend'de tüm geçmişte sorgular (debounce'lu). |
| `onNotificationClick` | `(notification: Notification) => void` | — | Bir liste satırı veya pop-up kartı tıklandı. |
| `renderTrigger` | `(props: { unreadCount: number; onClick: () => void }) => ReactNode` | — | Zil tetikleyicisinin tamamını değiştirir. |
| `renderItem` | `(notification, actions: { hide: () => void }) => ReactNode` | — | Liste satırının gövdesini değiştirir. |
| `errorFallback` | `ReactNode` | — | Kütüphane render sırasında hata fırlatırsa gösterilir. Verilmezse bildirim alanı sessizce kaybolur — uygulamanızın geri kalanı çalışmaya devam eder. |
| `onRenderError` | `(error: Error) => void` | — | Yakalanan render hatasını bildirir (loglama için). |

### `badge` seçenekleri

| Alan | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `showCount` | `boolean` | `true` | Sayıyı mı yoksa düz noktayı mı göster. |
| `color` | `string` | antd varsayılanı | Rozet rengi. |
| `size` | `'small' \| 'default'` | `'small'` | `'default'` antd'nin daha büyük rozeti. |

### `panel` seçenekleri

| Alan | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `placement` | `PopupPlacement` | `'bottomRight'` | `'bottom' \| 'bottomLeft' \| 'bottomRight' \| 'top' \| 'topLeft' \| 'topRight'`. |
| `width` | `number` | `440` | Panel genişliği (px). |
| `height` | `number` | `420` | Panel içindeki kaydırılabilir liste alanının yüksekliği (px). |
| `offsetX` / `offsetY` | `number` | `0` | Panel **gövdesini** piksel bazında kaydırır. Ok etkilenmez, zili göstermeye devam eder. |
| `arrowOffsetX` / `arrowOffsetY` | `number` | `0` | Sadece oku, gövdeden bağımsız kaydırır. Nadiren gerekir. |
| `background` | `string` | tema (beyaz / `#1f1f1f`) | Panel gövdesi arka planı. `arrowBackground` verilmedikçe ok da bunu takip eder. |
| `arrowBackground` | `string` | `background`'i takip eder | Gövdeden farklı istiyorsanız okun arka planı. |
| `zIndex` | `number` | antd varsayılanı | Panel z-index'i (antd Popover). |
| `getPopupContainer` | `(trigger: HTMLElement) => HTMLElement` | `document.body` | Panelin (ve zilin sağ-tık menüsünün) DOM'da hangi elemanın altına render edileceği. **Kütüphane antd'nin "tetikleyicinin ebeveyni" varsayılanını değil `document.body`'yi kullanır** — atalarından birinde `transform`, `filter`, `backdrop-filter` veya `overflow: hidden` varsa (bulanık/sabit navbar klasik örnek) panel yanlış konumlanır ya da kırpılır. Panelin belirli bir kapsayıcıyla birlikte kayması gerekiyorsa burayı değiştirin. |

### Imperative handle & kontrollü panel

`NotificationBell` bir ref sunar: `{ open(), close(), toggle() }`:

```tsx
import { useRef } from 'react';
import { NotificationBell, type NotificationBellHandle } from '@bilalefeuysl/notification-react';

const bellRef = useRef<NotificationBellHandle>(null);
// ...
<NotificationBell ref={bellRef} />
<button onClick={() => bellRef.current?.open()}>Bildirimleri aç</button>
```

Tam kontrol için `open` verip `onOpenChange`'i kendiniz yönetin (bileşen o zaman iç
state'ini kullanmaz).

### Render prop'lar

`renderTrigger`, `renderPopupCard` ve `renderItem` size bildirim verisini (ve bir
`close` / `hide` callback'i) verip ne isterseniz onu render etmenize izin verir.
`renderPopupCard` verdiğinizde kütüphanenin otomatik tarih satırı eklenmez — kart
tamamen sizindir.

---

## `useNotifications`

`NotificationProvider` içindeki herhangi bir bileşende çağırın:

```tsx
const {
  notifications,      // Notification[] — yüklü liste, en yeni önce
  hasMore,            // boolean — daha fazla sayfa var mı
  loading,            // boolean
  error,              // string | null
  loadMore,           // () => Promise<void>
  hide,               // (id) => Promise<void>   — birini gizle
  hideAll,            // () => Promise<void>      — hepsini gizle
  markAsRead,         // (ids: string[]) => Promise<void>
  unreadCount,        // number — gerçek toplam, sayfalamadan bağımsız (rozet için bunu kullan)
  soundEnabled,       // boolean
  toggleSound,        // () => void  (localStorage'da saklanır)
  popupsEnabled,      // boolean
  togglePopups,       // () => void  (localStorage'da saklanır)
  toggleSaved,        // (id) => Promise<void>  — kaydet / kaldır (geri alınabilir)
  fetchSaved,         // (before?, query?) => Promise<NotificationPage>  — "kaydedilenler" görünümü, ayrı sorgu
  searchNotificationsRemote, // (query, before?) => Promise<NotificationPage>
  connectionStatus,   // 'connected' | 'disconnected'  ('disconnected' iken yeniden bağlanma sürer)
} = useNotifications();
```

---

## Bağımsız bileşenler

Hepsi-bir-arada `NotificationBell` yerine parçaları kendiniz birleştirebilirsiniz:

- **`NotificationPanel`** — panel gövdesi (liste + başlık işlemleri + arama +
  kaydedilenler görünümü). Prop'lar: `language`, `onClearAll` (zorunlu), `width`,
  `height`, `typeStyles`, `showUnreadIndicator`, `timeFormat`, `showTypeIcons`,
  `onNotificationClick`, `renderItem`, `enableServerSearch`.
- **`NotificationList`** — sadece sonsuz kaydırmalı liste. Prop'lar: `language`,
  `height`, `typeStyles`, `showUnreadIndicator`, `timeFormat`, `showTypeIcons`,
  `onNotificationClick`, `renderItem`, `overrideNotifications`, `emptyMessage`,
  `selectionMode` / `selectedIds` / `onToggleSelect`, `onAfterToggleSave`,
  `onAfterDelete`.
### `PopupStack`

Köşe pop-up (toast) yığını. `NotificationProvider` içinde, istediğiniz yere, **bir
kere** render edin — `NotificationBell`'den tamamen bağımsızdır.

Varsayılan olarak **`document.body` üzerinde bir portal'a** render edilir. Bu bilinçli:
yığın `position: fixed` ve CSS'te fixed bir eleman, atalarından birinde `transform`,
`filter`, `backdrop-filter` veya `will-change` varsa viewport'a değil **o ataya** göre
konumlanır. Bulanık/sabit bir navbar bu tuzağı çok kolay kuruyor — eskiden pop-up'lar
navbar'ın içine hapsoluyordu.

| Prop | Tip | Varsayılan | Açıklama |
|---|---|---|---|
| `width` | `number` | `340` | Her pop-up kartının genişliği (px). |
| `groupThreshold` | `number` | `3` | Bu sayıyı aşınca pop-up'lar tek bir yığına toplanır. |
| `autoDismissMs` | `number \| null` | `6000` | Otomatik kapanma süresi (ms); `null` = otomatik kapanma yok. |
| `maxVisible` | `number` | hepsi | Açıkken render edilecek kart üst sınırı (fazlası kaydırılır). |
| `placement` | `'top-right' \| 'top-left' \| 'bottom-right' \| 'bottom-left'` | `'top-right'` | Yığının hangi köşeye yaslanacağı. Alt köşelerde yığın **yukarı** doğru büyür; böylece toplanmış kartlar hep ekranın içine doğru dizilir. |
| `offsetY` | `number` | `24` | Seçilen **dikey** kenara uzaklık (px) — `top-*` için üstten, `bottom-*` için alttan. Sabit navbar yüksekliğinize ayarlayın. Yığının en fazla ne kadar uzayabileceğini de belirler. |
| `offsetX` | `number` | `24` | Seçilen **yatay** kenara uzaklık (px) — `*-right` için sağdan, `*-left` için soldan. |
| `topOffset` | `number` | `24` | **Kullanımdan kalktı** — `offsetY` kullanın. Geriye dönük uyumluluk için duruyor; ikisi de verilirse `offsetY` kazanır. |
| `zIndex` | `number` | `1000` | Pop-up yığınının z-index'i. |
| `theme` | `'light' \| 'dark' \| 'auto'` | provider'dan miras | Verilmezse `NotificationProvider`'ın `theme`'i kullanılır. |
| `language` | `'tr' \| 'en' \| 'auto'` | provider'dan miras | Verilmezse `NotificationProvider`'ın `language`'i kullanılır. |
| `timeFormat` | `TimeFormat` | `'full'` | Karttaki zaman damgası biçimi. |
| `typeStyles` | `Record<string, Partial<TypeStyle>>` | — | Bildirim `type`'ına göre renk paletini değiştirir. |
| `container` | `HTMLElement \| null` | `document.body` | Portal hedefi. `null` verirseniz portal kullanılmaz, bulunduğu yere render edilir (SSR / özel hedef). |
| `onNotificationClick` | `(notification: Notification) => void` | — | Bir pop-up kartına tıklandı. |
| `onPopupDismiss` | `(notification, reason: PopupDismissReason) => void` | — | Bir pop-up kapatıldı (`reason` = `'user'` veya `'timeout'`). |
| `renderPopupCard` | `(notification, close: () => void) => ReactNode` | — | Pop-up kartının gövdesini değiştirir. |

Hepsi `NotificationProvider` içinde render edilmelidir.

---

## Tema

`theme`'i **`NotificationProvider`'da bir kere** verin — context ile yayılır ve
`NotificationBell` ile `PopupStack` tarafından miras alınır:

```tsx
<NotificationProvider theme="dark" ...>
  <NotificationBell />   {/* koyu */}
  <PopupStack />         {/* koyu */}
</NotificationProvider>
```

Gerçekten farklı olmasını istiyorsanız her ikisi de yerel olarak ezebilir
(`<PopupStack theme="light" />`).

`theme` değeri `'light' | 'dark' | 'auto'` (varsayılan `'auto'` — işletim sistemini
izler). CSS, `prefers-reduced-motion: reduce`'a saygı duyar ve ayarlıysa tüm
geçiş/animasyonları kaldırır.

İleri seviye için tema hook'ları da dışa aktarılır: `useResolvedTheme(name)`,
`useThemeTokens()`, `useTheme()`, ve `ThemeTokens` / `ResolvedTheme` / `ThemeName`
tipleri.

---

## Tip renkleri

Her bildirim `type`'ı küçük bir palete eşlenir (`background`, `borderColor`,
`titleColor`, `textColor`), ayrı light/dark değerleriyle. `success` / `error` /
`warning` / `info` yerleşiktir; başka herhangi bir metin nötr bir stille gösterilir.
`typeStyles` prop'uyla tip başına override edin (yalnızca verdiğiniz alanlar
değişir):

```tsx
<NotificationBell
  typeStyles={{
    warning: { borderColor: '#e67e22' },
    deployment: { borderColor: '#6c5ce7', titleColor: '#6c5ce7' },
  }}
/>
```

`getTypeStyle(type, theme)` ve `TypeStyle` tipi de dışa aktarılır.

---

## Uluslararasılaştırma (i18n)

`language`'i de **`NotificationProvider`'da bir kere** verin — `theme` gibi
`NotificationBell` ve `PopupStack` tarafından miras alınır, ikisi de yerel olarak
ezebilir.

`language` prop'u `'tr' | 'en' | 'auto'`'dur (varsayılan `'tr'`; `'auto'` →
`navigator.language`). Şunları kontrol eder:

1. **Bileşen arayüzü** — buton metinleri, boş durumlar, tarih biçimi.
2. **Bildirim içeriği** — `language` `'en'`e çözülürse ve bildirim bir İngilizce
   karşılık taşıyorsa (`classificationEn` / `messageEn`, backend tarafından
   doldurulur) o gösterilir; aksi halde varsayılan `classification` / `message`
   kullanılır. İkisini birden nasıl yayınlayacağınız için backend README'sindeki
   [İki dilli içerik](../README.tr.md#i̇ki-dilli-içerik-türkçe--i̇ngilizce) bölümüne bakın.

Başka yerde kullanmak için dışa aktarılanlar: `getMessages(language)`,
`formatRelativeTime(iso, language)`, `resolveLanguage(setting)` (`'auto'`yu somut
`'tr'`/`'en'`e çevirir) ve `resolveNotificationText(notification, language)`
(gösterilecek başlık/mesajı seçer).

---

## Hedefleme (kişiye özel bildirim)

Backend hedefleme açıkken provider'a `identity` verin:

```tsx
<NotificationProvider
  basePath="…"
  websocketUrl="…"
  identity={{ userId: currentUser.id, roles: currentUser.roles }}
>
```

Provider, REST çağrılarına `X-User-Id` / `X-User-Roles`, WebSocket URL'sine
`?userId=…&roles=…` ekler. Okundu / gizli / kaydedildi durumu artık kişiye özeldir.
`identity` değişmesi soketi şeffafça yeniden bağlar ve listeyi yeniden yükler.

> ⚠️ Varsayılan backend çözücüsü bu değerleri doğrulamadan güvenir — backend
> README'sindeki [güvenlik uyarısına](../README.tr.md#güvenlik-uyarısı) bakın.
> Üretimde backend, kimliği doğrulanmış bir auth token'ına bağlamalıdır.

---

## Öncelik sıralaması

Provider'da `sortByPriority` ayarlayınca HIGH → NORMAL → LOW (sonra en yeni önce)
sıralaması gelir. WebSocket'ten gelen canlı bildirimler sıralama modundan bağımsız
her zaman listenin başına eklenir. Bunun için yerleşik bir UI düğmesi yok — bir
provider prop'udur; kullanıcıların değiştirmesini istiyorsanız kendi kontrolünüzü
ekleyin.

---

## Tarayıcı deposu

`localStorage`'da iki tercih saklanır, ikisi de varsayılan **açık**, ikisi de panel
başlığından değiştirilir:

| Anahtar | Anlamı |
|---|---|
| `notification-react:sound-enabled` | Yeni bildirimde ses çal. |
| `notification-react:popups-enabled` | Yeni bildirimler için köşe pop-up'ları göster (kapalıyken liste ve rozet yine güncellenir). |

---

## Sunucu tarafında render (Next.js)

Paket SSR-güvenlidir: her `window` / `document` / `localStorage` / `WebSocket` /
`Audio` erişimi ya `typeof window` kontrolüyle korunur ya da sunucuda hiç çalışmayan
bir efekt / event handler içindedir. Ses / pop-up düğmeleri sunucuda varsayılana
(`açık`) düşer ve hydration sırasında saklanan değere senkronlanır; bu ikon ilk
boyamada bir hydration uyarısına yol açarsa `NotificationBell`'i yalnızca istemcide
render edin.

---

## Erişilebilirlik

Liste, `role="listitem"` satırlarından oluşan bir `role="list"`'tir; boş durumlar
`role="status"` kullanır. Zil tetikleyicisi `aria-expanded` /
`aria-haspopup="dialog"` sunar. Panel `Esc` ile kapanır, interaktif elemanlar klavyeyle
erişilebilir, liste ok tuşuyla gezinmeyi destekler. Ekran okuyucu testi (NVDA /
Narrator) hâlâ bekliyor.

---

## Sorun giderme

**Bileşenler stilsiz görünüyor.** Stil dosyasını import etmediniz — bkz. [Stiller](#stiller).

**`useNotifications() yalnizca <NotificationProvider> icinde çağırılabilir`.** Hook'u
çağıran bileşen provider alt ağacının dışında.

**Hiçbir şey yüklenmiyor / WebSocket'te `401`.** Backend'de hedefleme açık ama
provider'a `identity` vermediniz.

**CSS için `ERR_PACKAGE_PATH_NOT_EXPORTED`.** Tam belirteci kullanın:
`@bilalefeuysl/notification-react/styles.css`.

**"Bağlantı koptu" uyarısı çıkıyor.** Soket düştü; kütüphane backoff + jitter ile
denemeye devam eder ve `online` / sekme-odağı ile kurtarır. `useNotifications()`'tan
gelen `connectionStatus` o anki durumu yansıtır.
