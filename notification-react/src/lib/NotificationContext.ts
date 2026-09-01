// Context ve tipi AYRI dosyada: react-refresh bir dosyanin sadece bilesen
// export etmesini ister, Context (bir deger) NotificationProvider.tsx'te
// durunca Fast Refresh bozuluyordu (react-refresh/only-export-components).

import { createContext } from 'react';
import type { Notification, NotificationPage } from '../types';
import type { LanguageSetting } from '../locales/i18n';

/**
 * Context'in disariya sundugu her sey. useNotifications hook'u
 * bu sekli okuyup bilesenlere verecek.
 */
export interface NotificationContextValue {
  notifications: Notification[];
  hasMore: boolean;
  loading: boolean;
  error: string | null;
  loadMore: () => Promise<void>;
  hide: (id: string) => Promise<void>;
  hideAll: () => Promise<void>;
  /** Verilen id'leri hem backend'de hem yerel state'te okundu isaretler. */
  markAsRead: (ids: string[]) => Promise<void>;
  /**
   * Sayfalamadan bagimsiz, GERCEK toplam okunmamis bildirim sayisi.
   * Rozette (showCount) bunu kullan - yuklu listeden saymak, sadece o an
   * tarayicida bulunan (orn. ilk 25) kayittan sayar, bu da yanlis/tutarsiz
   * gorunur.
   */
  unreadCount: number;
  /** Yeni bildirim geldiginde ses calinip calinmayacagi (varsayilan: acik). */
  soundEnabled: boolean;
  /** Ses acik/kapali durumunu tersine cevirir - tercih tarayicida (localStorage) saklanir. */
  toggleSound: () => void;
  /**
   * Yeni bildirimler geldiginde popup (toast) gosterilsin mi (varsayilan:
   * acik). Kapaliyken yeni bildirimler listeye/rozete normal sekilde
   * eklenmeye devam eder, sadece ekranin kosesinde popup olarak CIKMAZLAR.
   */
  popupsEnabled: boolean;
  /** Popup gosterimini acar/kapatir - tercih tarayicida (localStorage) saklanir. */
  togglePopups: () => void;
  /**
   * Bir bildirimin kaydedildi durumunu tersine cevirir (kaydet/kaydi
   * kaldir) - read/hidden'in aksine GERI ALINABILIR.
   */
  toggleSaved: (id: string) => Promise<void>;
  /**
   * "Kayitlilar" gorunumu icin ayri bir sorgu - ana listeden (notifications)
   * BAGIMSIZ, kendi sayfalamasini doner. Ana listeyi degistirmez.
   */
  fetchSaved: (before?: string, query?: string) => Promise<NotificationPage>;
  /**
   * Sunucu tarafli serbest metin arama - backend'de TUM gecmiste arar.
   * SADECE NotificationBell'e enableServerSearch=true verildiyse kullanilir.
   */
  searchNotificationsRemote: (query: string, before?: string) => Promise<NotificationPage>;
  /**
   * Yeni bir bildirim WebSocket'ten geldigi ANDA haberdar olmak icin (usePopupQueue
   * bunu kullaniyor). Donen fonksiyon dinlemeyi durdurur.
   */
  subscribe: (listener: (notification: Notification) => void) => () => void;
  /**
   * WebSocket baglantisinin su anki durumu. 'disconnected' iken kutuphane
   * arka planda otomatik yeniden baglanmayi DENEMEYE DEVAM EDER (bkz.
   * NotificationSocket) - bu alan sadece kullaniciya gorsel bir gosterge
   * sunmak icin.
   */
  connectionStatus: 'connected' | 'disconnected';
  /**
   * Uygulama genelinde dil tercihi (NotificationProvider'a verilen deger,
   * COZULMEMIS hali - 'auto' ise oyle kalir). NotificationBell / PopupStack
   * kendi `language` prop'lari verilmediginde bunu kullanir.
   */
  language: LanguageSetting;
}

export const NotificationContext = createContext<NotificationContextValue | null>(null);
