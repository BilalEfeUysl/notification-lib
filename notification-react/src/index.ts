/**
 * Kutuphanenin tek giris noktasi. Kullanan uygulama SADECE buradan import eder.
 */

export { NotificationProvider } from './lib/NotificationProvider';
export type { NotificationProviderProps } from './lib/NotificationProvider';
export type { NotificationContextValue } from './lib/NotificationContext';

export { useNotifications } from './hooks/useNotifications';

export { NotificationBell } from './components/NotificationBell';
export type {
  NotificationBellProps,
  NotificationBellHandle,
  NotificationBadgeOptions,
  NotificationPanelOptions,
  PopupPlacement,
  RenderTriggerProps,
  ReadTrigger,
} from './components/NotificationBell';

export { NotificationPanel } from './components/NotificationPanel';
export type { NotificationPanelProps } from './components/NotificationPanel';

export { NotificationList } from './components/NotificationList';
export type { NotificationListProps } from './components/NotificationList';

export { PopupStack } from './components/PopupStack';
export type { PopupStackProps, PopupStackPlacement } from './components/PopupStack';

export type { PopupDismissReason } from './hooks/usePopupQueue';

export { getMessages, formatRelativeTime, resolveLanguage, resolveNotificationText } from './locales/i18n';
export type { Language, LanguageSetting } from './locales/i18n';

export { getTypeStyle } from './styles/typeStyles';
export type { TypeStyle } from './styles/typeStyles';

export type {
  Notification,
  NotificationPage,
  NotificationPriority,
  ServerMessage,
} from './types';

export const NOTIFICATION_LIB_VERSION = '0.1.2';

export { useResolvedTheme, useTheme, useThemeTokens } from './context/theme';
export type { ThemeName, ResolvedTheme, ThemeTokens } from './context/theme';
export type { TimeFormat } from './locales/i18n';