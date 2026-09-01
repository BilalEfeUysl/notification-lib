# Changelog

All notable changes to `@bilalefeuysl/notification-react` are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2026-09-01

### Fixed
- `NOTIFICATION_LIB_VERSION` reported `0.1.2` while the package was `0.1.0`. The
  constant is now injected from `package.json` at build time, so it can no longer
  drift from the real package version.

## [0.1.0] - 2026-09-01

Initial public release. React UI for the notification library:
`NotificationProvider`, `NotificationBell`, `PopupStack`, `useNotifications`, live
WebSocket updates, cross-tab read/hide/save sync, targeting, priority ordering,
light/dark/auto theme, Turkish/English content.

[0.1.1]: https://github.com/BilalEfeUysl/notification-lib/releases/tag/react-v0.1.1
[0.1.0]: https://github.com/BilalEfeUysl/notification-lib/releases/tag/react-v0.1.0
