// tr.ts/en.ts sozluklerini okur, yer tutuculari ({count}, {n}) doldurur ve
// goreli zaman formatlama mantigini icerir. Metinler tr.ts / en.ts'te.

import tr from './tr';
import en from './en';

export type Language = 'tr' | 'en';

/**
 * Bilesenlere verilen `language` prop'unun tipi: sabit bir dil ya da 'auto'.
 * 'auto' iken tarayicinin diline (navigator.language) bakilir - 'en' ile
 * basliyorsa 'en', aksi halde 'tr'.
 */
export type LanguageSetting = Language | 'auto';

/** 'auto'yu tarayici diline gore somut bir Language'e cevirir; SSR'da 'tr'ye duser. */
export function resolveLanguage(setting: LanguageSetting = 'tr'): Language {
  if (setting !== 'auto') return setting;
  if (typeof navigator === 'undefined') return 'tr';
  return navigator.language?.toLowerCase().startsWith('en') ? 'en' : 'tr';
}

/**
 * Bir bildirimin, aktif dile gore gosterilecek baslik + mesajini secer.
 * Dil 'en' VE Ingilizce alanlar doluysa onlari, aksi halde varsayilan
 * (classification/message) metni doner. Backend'deki Notification.resolved*
 * ile ayni mantik.
 */
export function resolveNotificationText(
  notification: {
    classification: string;
    message: string;
    classificationEn?: string | null;
    messageEn?: string | null;
  },
  language: Language,
): { classification: string; message: string } {
  const useEn =
    language === 'en' &&
    notification.classificationEn != null &&
    notification.messageEn != null;
  return useEn
    ? { classification: notification.classificationEn!, message: notification.messageEn! }
    : { classification: notification.classification, message: notification.message };
}

/**
 * Bildirim zaman damgasının nasıl gösterileceği:
 * - 'short'    (varsayılan): "21 Ağu 14:32" - gün + ay adı + saat, yıl yok
 * - 'full'                 : "21.08.2026 14:32" - tam tarih + saat
 * - 'relative'             : "5 dakika önce" - göreli
 * - 'time-only'            : "14:32" - sadece saat
 * - kendi fonksiyonun      : (isoDate, language) => string - tamamen kendi formatın
 */
export type TimeFormat = 'short' | 'full' | 'relative' | 'time-only' | ((isoDate: string, language: Language) => string);

const DICTIONARIES = { tr, en };

export type Messages = typeof tr;

/** Verilen dile ait metin sozlugunu doner. */
export function getMessages(language: Language): Messages {
  return DICTIONARIES[language];
}

/** {isim} seklindeki yer tutuculari verilen degerlerle degistirir. */
function interpolate(template: string, vars: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => String(vars[key] ?? `{${key}}`));
}

/** n=1 icin 'one', digerleri icin 'other' formunu secip {n} yer tutucusunu doldurur. */
function pluralize(forms: { one: string; other: string }, n: number): string {
  const template = n === 1 ? forms.one : forms.other;
  return interpolate(template, { n });
}

/** "{count} bildirim daha" gibi tek yer tutuculu metinleri doldurmak icin. */
export function formatMoreNotifications(language: Language, count: number): string {
  return interpolate(getMessages(language).moreNotifications, { count });
}

/** "{count} seçili" - toplu secim modundaki basliktaki sayaci doldurur. */
export function formatSelectionCount(language: Language, count: number): string {
  return interpolate(getMessages(language).selectionCount, { count });
}

/** "{count} bildirimi silmek istediginize emin misiniz?" - toplu silme onay metni. */
export function formatConfirmDeleteSelected(language: Language, count: number): string {
  return interpolate(getMessages(language).confirmDeleteSelected, { count });
}

/**
 * ISO-8601 tarih metnini (backend'den gelen createdAt), "5 dakika once" gibi
 * insan-okunur goreli bir metne cevirir.
 */
export function formatRelativeTime(isoDate: string, language: Language): string {
  const messages = getMessages(language);
  const then = new Date(isoDate).getTime();
  const now = Date.now();
  const diffSeconds = Math.max(0, Math.floor((now - then) / 1000));

  if (diffSeconds < 60) return messages.justNow;

  const diffMinutes = Math.floor(diffSeconds / 60);
  if (diffMinutes < 60) return pluralize(messages.minutesAgo, diffMinutes);

  const diffHours = Math.floor(diffMinutes / 60);
  if (diffHours < 24) return pluralize(messages.hoursAgo, diffHours);

  const diffDays = Math.floor(diffHours / 24);
  return pluralize(messages.daysAgo, diffDays);
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

/** "21 Ağu 14:32" - gün + kısaltılmış ay adı + saat, yıl yok. */
export function formatShortTime(isoDate: string, language: Language): string {
  const d = new Date(isoDate);
  const month = getMessages(language).monthsShort[d.getMonth()];
  return `${d.getDate()} ${month} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

/** "21.08.2026 14:32" - tam tarih + saat. */
export function formatFullTime(isoDate: string): string {
  const d = new Date(isoDate);
  return `${pad2(d.getDate())}.${pad2(d.getMonth() + 1)}.${d.getFullYear()} ${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

/** "14:32" - sadece saat. */
export function formatTimeOnly(isoDate: string): string {
  const d = new Date(isoDate);
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`;
}

/**
 * timeFormat'a göre doğru formatlayıcıyı seçip çalıştırır - NotificationItem
 * bu TEK fonksiyonu çağırır, hangi format seçilmişse onu döner.
 */
export function formatNotificationTime(isoDate: string, language: Language, timeFormat: TimeFormat = 'short'): string {
  if (typeof timeFormat === 'function') return timeFormat(isoDate, language);
  switch (timeFormat) {
    case 'full':
      return formatFullTime(isoDate);
    case 'relative':
      return formatRelativeTime(isoDate, language);
    case 'time-only':
      return formatTimeOnly(isoDate);
    case 'short':
    default:
      return formatShortTime(isoDate, language);
  }
}