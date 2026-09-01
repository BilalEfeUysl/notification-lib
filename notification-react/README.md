# notification-react

**English** · [Türkçe](./README.tr.md)

React UI for the [notification library](../README.md): a `NotificationProvider` that
connects to the backend, a `NotificationBell` (bell icon + panel + pop-ups), and a
`useNotifications` hook. It shows live notifications as corner pop-ups and a scrollable
list, and mirrors read / hide / save state across tabs.

```bash
npm install @bilalefeuysl/notification-react
```

`react`, `react-dom` and `antd` **v4** are **peer dependencies** — they must already
be installed in the host application. Ant Design v5 is not supported.

---

## Contents

- [Styles](#styles)
- [Quick start](#quick-start)
- [`NotificationProvider`](#notificationprovider)
- [`NotificationBell`](#notificationbell)
  - [`badge`](#badge-options)
  - [`panel`](#panel-options)
  - [Imperative handle & controlled panel](#imperative-handle--controlled-panel)
  - [Render props](#render-props)
- [`useNotifications`](#usenotifications)
- [Standalone components](#standalone-components)
  - [`PopupStack`](#popupstack)
- [Theming](#theming)
- [Type colours](#type-colours)
- [Internationalization](#internationalization)
- [Targeting (per-user notifications)](#targeting-per-user-notifications)
- [Priority ordering](#priority-ordering)
- [Browser storage](#browser-storage)
- [Server-side rendering (Next.js)](#server-side-rendering-nextjs)
- [Accessibility](#accessibility)
- [Troubleshooting](#troubleshooting)

---

## Styles

The package ships its stylesheet as a separate CSS file. Import it **once** at your
application's entry point, next to Ant Design's own CSS:

```js
import 'antd/dist/antd.css';
import '@bilalefeuysl/notification-react/styles.css';
```

Without this line the components render unstyled.

---

## Quick start

```tsx
import { NotificationProvider, NotificationBell, PopupStack } from '@bilalefeuysl/notification-react';
import '@bilalefeuysl/notification-react/styles.css';

function App() {
  return (
    <NotificationProvider
      basePath="https://api.example.com/api/notifications"
      websocketUrl="wss://api.example.com/ws/notifications"
      theme="dark"
      language="en"
    >
      <MyHeader>
        <NotificationBell />
      </MyHeader>

      {/* The corner pop-up stack. Render it ONCE, anywhere inside the provider. */}
      <PopupStack />
    </NotificationProvider>
  );
}
```

`NotificationProvider` fetches history over REST, subscribes to the WebSocket, and
publishes the app-wide `theme` / `language` defaults. `NotificationBell` renders the
bell with an unread badge and opens the panel on click. `PopupStack` renders the
corner pop-ups for new notifications. Any component inside the provider can also call
[`useNotifications()`](#usenotifications) directly.

> **The bell and the pop-up stack are independent.** The bell does **not** render
> pop-ups — you place `<PopupStack />` yourself. This means you can hide the bell
> (or render several of them) without affecting pop-ups, put pop-ups anywhere in the
> tree, and use pop-ups with no bell at all. Render `PopupStack` **once**: each
> instance keeps its own queue, so two of them show every notification twice.

---

## `NotificationProvider`

Wrap your app (or the subtree that needs notifications) once. It owns the data, the
WebSocket connection, and the sound / pop-up preferences.

| Prop | Type | Default | Description |
|---|---|---|---|
| `basePath` | `string` | — (required) | Absolute URL of the REST base path, e.g. `https://api.example.com/api/notifications`. |
| `websocketUrl` | `string` | — (required) | Absolute WebSocket URL, e.g. `wss://api.example.com/ws/notifications`. |
| `initialLimit` | `number` | `25` | Page size for the first load and each "load more". |
| `onError` | `(error: Error) => void` | — | Called when a REST request or the socket fails. The UI also surfaces a message; this is for logging. |
| `identity` | `{ userId: string; roles?: string[] }` | — | **Required when the backend has targeting enabled.** Added to REST requests as `X-User-Id` / `X-User-Roles` headers and to the WebSocket URL as `?userId=…&roles=…`. Changing it reconnects the socket. |
| `sortByPriority` | `boolean` | `false` | When `true`, the list is ordered by priority (HIGH → NORMAL → LOW) then by date, using the backend's opt-in `sort=priority` path. When omitted, the backend does no extra work. See [Priority ordering](#priority-ordering). |
| `theme` | `'light' \| 'dark' \| 'auto'` | `'auto'` | App-wide theme. `NotificationBell` and `PopupStack` inherit it unless they set their own `theme`. `'auto'` follows the OS / browser colour scheme. |
| `language` | `'tr' \| 'en' \| 'auto'` | `'tr'` | App-wide UI language. `NotificationBell` and `PopupStack` inherit it unless they set their own `language`. `'auto'` follows `navigator.language`. |
| `credentials` | `RequestCredentials` | — | `fetch` credentials mode for every REST request. **Set `'include'` if you use cookie/session auth and serve the frontend from a different origin than the backend** — otherwise the browser never sends the cookie and requests silently fail. |
| `children` | `ReactNode` | — | Your app. |

---

## `NotificationBell`

The bell you drop into your header: an unread badge, and the notification panel on
click. It does **not** render pop-ups — see [`PopupStack`](#popupstack) for those.

| Prop | Type | Default | Description |
|---|---|---|---|
| `language` | `'tr' \| 'en' \| 'auto'` | inherits provider | UI language for chrome text, date formatting **and which notification-content variant is shown**. Falls back to `NotificationProvider`'s `language`. Set this only to make *this bell* differ from the rest of the app. |
| `theme` | `'light' \| 'dark' \| 'auto'` | inherits provider | Falls back to `NotificationProvider`'s `theme`. Set this only to make *this bell* differ from the rest of the app. |
| `icon` | `ReactNode` | status-aware bell | Custom trigger icon. If omitted, the library's default bell is used (see `showStatusIcon`); passing your own icon disables `showStatusIcon`. |
| `showStatusIcon` | `boolean` | `true` | The default bell reflects sound/popup state on the icon: **sound on** → vibration arcs beside the bell; **popups off** → a diagonal slash across the bell. Toggled from the bell's right-click menu ("Mute sound" / "Turn off notifications"). `false` → plain `<BellOutlined/>`. No effect if you pass `icon`. |
| `className` | `string` | — | Class on the trigger wrapper. |
| `style` | `CSSProperties` | — | Inline style on the trigger wrapper. |
| `badge` | `NotificationBadgeOptions` | — | Unread badge options — [see below](#badge-options). |
| `panel` | `NotificationPanelOptions` | — | Panel options — [see below](#panel-options). |
| `typeStyles` | `Record<string, Partial<TypeStyle>>` | — | Override the colour palette per notification `type`. See [Type colours](#type-colours). |
| `showUnreadIndicator` | `boolean` | `true` | Show a small dot on unread rows in the list. |
| `timeFormat` | `TimeFormat` | `'full'` | `'short'` \| `'full'` \| `'relative'` \| `'time-only'` \| `(iso, lang) => string`. |
| `showTypeIcons` | `boolean` | `false` | Show a small icon next to the title for `success` / `error` / `warning` / `info`. |
| `readTrigger` | `'onOpen' \| 'onClick' \| 'manual'` | `'onOpen'` | When a notification is marked read. `'onOpen'`: everything visible when the panel closes. `'onClick'`: only the clicked one. `'manual'`: never automatically — you call `markAsRead` yourself. |
| `open` | `boolean` | — | Controlled mode: when set, you own the panel's open/closed state and update it from `onOpenChange`. |
| `onOpenChange` | `(open: boolean) => void` | — | Fires whenever the panel wants to open or close (bell click, outside click, `Esc`…). |
| `enableServerSearch` | `boolean` | `false` | `false`: search filters the currently loaded notifications instantly. `true`: search queries the backend across all history (debounced). |
| `onNotificationClick` | `(notification: Notification) => void` | — | A list row or pop-up card was clicked. |
| `renderTrigger` | `(props: { unreadCount: number; onClick: () => void }) => ReactNode` | — | Replace the whole bell trigger. |
| `renderItem` | `(notification, actions: { hide: () => void }) => ReactNode` | — | Replace the list row body. |
| `errorFallback` | `ReactNode` | — | Shown if the library throws during render. If omitted, the notification area disappears silently — the rest of your app keeps working. |
| `onRenderError` | `(error: Error) => void` | — | Reports a caught render error (for logging). |

### `badge` options

| Field | Type | Default | Description |
|---|---|---|---|
| `showCount` | `boolean` | `true` | Show the number vs. a plain dot. |
| `color` | `string` | antd default | Badge colour. |
| `size` | `'small' \| 'default'` | `'small'` | `'default'` is antd's larger badge. |

### `panel` options

| Field | Type | Default | Description |
|---|---|---|---|
| `placement` | `PopupPlacement` | `'bottomRight'` | `'bottom' \| 'bottomLeft' \| 'bottomRight' \| 'top' \| 'topLeft' \| 'topRight'`. |
| `width` | `number` | `440` | Panel width (px). |
| `height` | `number` | `420` | Height (px) of the scrollable list area inside the panel. |
| `offsetX` / `offsetY` | `number` | `0` | Nudge the panel **body** in pixels. The arrow is unaffected and keeps pointing at the bell. |
| `arrowOffsetX` / `arrowOffsetY` | `number` | `0` | Nudge only the arrow, independently of the body. Rarely needed. |
| `background` | `string` | theme (white / `#1f1f1f`) | Panel body background. The arrow follows this unless `arrowBackground` is set. |
| `arrowBackground` | `string` | follows `background` | Arrow background, if you want it different from the body. |
| `zIndex` | `number` | antd default | Panel z-index (antd Popover). |
| `getPopupContainer` | `(trigger: HTMLElement) => HTMLElement` | `document.body` | Where the panel (and the bell's right-click menu) is rendered in the DOM. **The library defaults to `document.body`, not antd's "trigger's parent"** — an ancestor with `transform`, `filter`, `backdrop-filter` or `overflow: hidden` (a blurred/sticky navbar is the classic case) would otherwise mis-position or clip the panel. Override this if the panel must scroll together with a specific container. |

### Imperative handle & controlled panel

`NotificationBell` forwards a ref exposing `{ open(), close(), toggle() }`:

```tsx
import { useRef } from 'react';
import { NotificationBell, type NotificationBellHandle } from '@bilalefeuysl/notification-react';

const bellRef = useRef<NotificationBellHandle>(null);
// ...
<NotificationBell ref={bellRef} />
<button onClick={() => bellRef.current?.open()}>Open notifications</button>
```

For full control, pass `open` and handle `onOpenChange` yourself (the component then
ignores its internal state).

### Render props

`renderTrigger`, `renderPopupCard` and `renderItem` each hand you the notification
data (and a `close` / `hide` callback) and let you render whatever you want. When you
supply `renderPopupCard`, the library's automatic date line is not added — the card is
entirely yours.

---

## `useNotifications`

Call it in any component inside `NotificationProvider` to read the data and drive it:

```tsx
const {
  notifications,      // Notification[] — the loaded list, newest first
  hasMore,            // boolean — more pages available
  loading,            // boolean
  error,              // string | null
  loadMore,           // () => Promise<void>
  hide,               // (id) => Promise<void>   — hide one
  hideAll,            // () => Promise<void>      — hide all
  markAsRead,         // (ids: string[]) => Promise<void>
  unreadCount,        // number — true total, independent of pagination (use for the badge)
  soundEnabled,       // boolean
  toggleSound,        // () => void  (persisted in localStorage)
  popupsEnabled,      // boolean
  togglePopups,       // () => void  (persisted in localStorage)
  toggleSaved,        // (id) => Promise<void>  — save / unsave (reversible)
  fetchSaved,         // (before?, query?) => Promise<NotificationPage>  — "saved" view, separate query
  searchNotificationsRemote, // (query, before?) => Promise<NotificationPage>
  connectionStatus,   // 'connected' | 'disconnected'  (reconnect keeps running while 'disconnected')
} = useNotifications();
```

---

## Standalone components

If you don't want the all-in-one `NotificationBell`, compose the pieces yourself:

- **`NotificationPanel`** — the panel body (list + header actions + search + saved
  view). Props: `language`, `onClearAll` (required), `width`, `height`, `typeStyles`,
  `showUnreadIndicator`, `timeFormat`, `showTypeIcons`, `onNotificationClick`,
  `renderItem`, `enableServerSearch`.
- **`NotificationList`** — just the scrollable list with infinite scroll. Props:
  `language`, `height`, `typeStyles`, `showUnreadIndicator`, `timeFormat`,
  `showTypeIcons`, `onNotificationClick`, `renderItem`, `overrideNotifications`,
  `emptyMessage`, `selectionMode` / `selectedIds` / `onToggleSelect`,
  `onAfterToggleSave`, `onAfterDelete`.
### `PopupStack`

The corner pop-up (toast) stack. Render it **once**, anywhere inside
`NotificationProvider` — it is independent of `NotificationBell`.

By default it renders into a **portal on `document.body`**. This is deliberate: the
stack is `position: fixed`, and a fixed element is positioned relative to the nearest
ancestor that has `transform`, `filter`, `backdrop-filter` or `will-change` — not the
viewport. A blurred/sticky navbar trivially triggers that, which used to trap the
pop-ups inside the navbar.

| Prop | Type | Default | Description |
|---|---|---|---|
| `width` | `number` | `340` | Width (px) of each pop-up card. |
| `groupThreshold` | `number` | `3` | Above this many pop-ups, they collapse into a peek stack. |
| `autoDismissMs` | `number \| null` | `6000` | Auto-dismiss delay in ms; `null` disables auto-dismiss. |
| `maxVisible` | `number` | all | Hard cap on cards rendered while expanded (the rest scroll). |
| `placement` | `'top-right' \| 'top-left' \| 'bottom-right' \| 'bottom-left'` | `'top-right'` | Which corner the stack anchors to. Bottom placements grow **upward**, so the collapsed peek stack always leans into the screen. |
| `offsetY` | `number` | `24` | Distance (px) from the chosen **vertical** edge (top for `top-*`, bottom for `bottom-*`) — set to your fixed navbar height. Also caps how tall the stack can grow. |
| `offsetX` | `number` | `24` | Distance (px) from the chosen **horizontal** edge (right for `*-right`, left for `*-left`). |
| `topOffset` | `number` | `24` | **Deprecated** — use `offsetY`. Kept for backwards compatibility; `offsetY` wins if both are given. |
| `zIndex` | `number` | `1000` | Pop-up stack z-index. |
| `theme` | `'light' \| 'dark' \| 'auto'` | inherits provider | Falls back to `NotificationProvider`'s `theme`. |
| `language` | `'tr' \| 'en' \| 'auto'` | inherits provider | Falls back to `NotificationProvider`'s `language`. |
| `timeFormat` | `TimeFormat` | `'full'` | Timestamp format on the card. |
| `typeStyles` | `Record<string, Partial<TypeStyle>>` | — | Override the colour palette per notification `type`. |
| `container` | `HTMLElement \| null` | `document.body` | Portal target. Pass `null` to disable the portal and render inline (SSR / custom render target). |
| `onNotificationClick` | `(notification: Notification) => void` | — | A pop-up card was clicked. |
| `onPopupDismiss` | `(notification, reason: PopupDismissReason) => void` | — | A pop-up was dismissed (`reason` is `'user'` or `'timeout'`). |
| `renderPopupCard` | `(notification, close: () => void) => ReactNode` | — | Replace the pop-up card body. |

All of these must be rendered inside `NotificationProvider`.

---

## Theming

Set `theme` **once on `NotificationProvider`** — it is published through context and
inherited by `NotificationBell` and `PopupStack`:

```tsx
<NotificationProvider theme="dark" ...>
  <NotificationBell />   {/* dark */}
  <PopupStack />         {/* dark */}
</NotificationProvider>
```

Either component can still override it locally (`<PopupStack theme="light" />`) if you
genuinely want it to differ from the rest of the app.

`theme` is `'light' | 'dark' | 'auto'` (default `'auto'` — follows the OS). The CSS
respects `prefers-reduced-motion: reduce` and drops all transitions/animations when
set.

Theme hooks are also exported for advanced cases: `useResolvedTheme(name)`,
`useThemeTokens()`, `useTheme()`, plus the `ThemeTokens` / `ResolvedTheme` /
`ThemeName` types.

---

## Type colours

Each notification `type` maps to a small palette (`background`, `borderColor`,
`titleColor`, `textColor`), with separate light/dark values. `success` / `error` /
`warning` / `info` are built in; any other string renders with a neutral style.
Override per type with the `typeStyles` prop (only the fields you pass are replaced):

```tsx
<NotificationBell
  typeStyles={{
    warning: { borderColor: '#e67e22' },
    deployment: { borderColor: '#6c5ce7', titleColor: '#6c5ce7' },
  }}
/>
```

`getTypeStyle(type, theme)` and the `TypeStyle` type are also exported.

---

## Internationalization

Set `language` **once on `NotificationProvider`** — like `theme`, it is inherited by
`NotificationBell` and `PopupStack`, and either can override it locally.

The `language` prop is `'tr' | 'en' | 'auto'` (default `'tr'`; `'auto'` reads
`navigator.language`). It controls:

1. **Component chrome** — button labels, empty states, date formatting.
2. **Notification content** — if `language` resolves to `'en'` and the notification
   carries an English variant (`classificationEn` / `messageEn`, filled by the
   backend), that variant is shown; otherwise the default `classification` /
   `message` is used. See [Localized content](../README.md#localized-content-turkish--english)
   in the backend README for how to publish both.

Exports for reuse elsewhere: `getMessages(language)`,
`formatRelativeTime(iso, language)`, `resolveLanguage(setting)` (turns `'auto'` into a
concrete `'tr'`/`'en'`), and `resolveNotificationText(notification, language)` (picks
the title/message to display).

---

## Targeting (per-user notifications)

When the backend runs with targeting enabled, pass `identity` to the provider:

```tsx
<NotificationProvider
  basePath="…"
  websocketUrl="…"
  identity={{ userId: currentUser.id, roles: currentUser.roles }}
>
```

The provider adds `X-User-Id` / `X-User-Roles` to REST calls and `?userId=…&roles=…`
to the WebSocket URL. Read / hide / save state is then per-user. Changing `identity`
transparently reconnects the socket and reloads the list.

> ⚠️ The default backend resolver trusts these values without verification — see the
> [security warning](../README.md#security-warning) in the backend README. In
> production the backend must bind identity to a verified auth token.

---

## Priority ordering

Set `sortByPriority` on the provider to get HIGH → NORMAL → LOW (then newest first).
Live notifications arriving over the WebSocket are always prepended to the top
regardless of sort mode. There is no built-in UI toggle for this — it's a provider
prop; add your own control if you want users to switch.

---

## Browser storage

Two preferences are stored in `localStorage`, both default **on**, both toggled from
the panel header:

| Key | Meaning |
|---|---|
| `notification-react:sound-enabled` | Play a sound on a new notification. |
| `notification-react:popups-enabled` | Show corner pop-ups for new notifications (the list and badge still update when off). |

---

## Server-side rendering (Next.js)

The package is SSR-safe: every `window` / `document` / `localStorage` / `WebSocket` /
`Audio` access is either guarded by a `typeof window` check or lives inside an effect /
event handler that never runs on the server. The sound / pop-up toggles fall back to
their default (`on`) on the server and sync to the stored value during hydration; if
that ever causes a hydration warning for the icon's first paint, render
`NotificationBell` client-side only.

---

## Accessibility

The list is a `role="list"` of `role="listitem"` rows; empty states use
`role="status"`. The bell trigger exposes `aria-expanded` / `aria-haspopup="dialog"`.
The panel closes on `Esc`, interactive elements are keyboard-reachable, and the list
supports arrow-key navigation. Screen-reader testing (NVDA / Narrator) is still
pending.

---

## Troubleshooting

**Components render unstyled.** You didn't import the stylesheet — see [Styles](#styles).

**`useNotifications() must be called inside <NotificationProvider>`.** The component
calling the hook is outside the provider subtree.

**Nothing loads / `401` on the WebSocket.** The backend has targeting enabled but you
didn't pass `identity` to the provider.

**`ERR_PACKAGE_PATH_NOT_EXPORTED` for the CSS.** Use the exact specifier
`@bilalefeuysl/notification-react/styles.css`.

**A "connection lost" banner appears.** The socket dropped; the library keeps retrying
with backoff + jitter and recovers on `online` / tab-focus. `connectionStatus` from
`useNotifications()` reflects the current state.
