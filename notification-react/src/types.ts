// Backend'in REST/WebSocket uzerinden gonderdigi verinin TypeScript tipleri.

/** Backend'deki NotificationPriority enum'unun karsiligi (JSON'da duz metin). */
export type NotificationPriority = 'LOW' | 'NORMAL' | 'HIGH';

/**
 * Tek bir bildirim. Backend'deki NotificationDto (rest-api) ve
 * NotificationCreatedEvent.Payload (websocket) ile birebir ayni JSON sekli.
 */
export interface Notification {
  id: string;
  /** Bildirimin basligi - VARSAYILAN metin (yayinci hangi dilde yazdiysa o). */
  classification: string;
  /** Bildirimin icerigi - VARSAYILAN metin. */
  message: string;
  /**
   * Opsiyonel Ingilizce baslik; yoksa null. Arayuz dili 'en' VE bu doluysa
   * classification yerine bu gosterilir (bkz. resolveNotificationText).
   */
  classificationEn: string | null;
  /** Opsiyonel Ingilizce icerik; yoksa null. */
  messageEn: string | null;
  /** Serbest metin - "success"/"error"/"warning"/"info" hazir oneriler ama sabit degil. */
  type: string;
  priority: NotificationPriority;
  read: boolean;
  /** read/hidden'in aksine GERI ALINABILIR. */
  saved: boolean;
  /** ISO-8601 (orn. "2026-08-19T10:15:30Z"). */
  createdAt: string;
  /** Kullanan uygulamanin koydugu serbest ek veri; yoksa {}. */
  metadata: Record<string, unknown>;
  /** Bildirimi yayinlayan kaynak/cihaz; verilmediyse null. */
  sourceDeviceId: string | null;
}

export interface NotificationPage {
  items: Notification[];
  hasMore: boolean;
  nextBefore: string | null; // sonraki sayfayi istemek icin "before" parametresi; son sayfadaysa null
  /**
   * SADECE sort: 'priority' ile yapilan isteklerde doludur (B11, opt-in
   * oncelik sirali liste). nextBefore'un bu moddaki karsiligi - tek bir
   * tarih yetmedigi icin (ayni tarih farkli oncelikte olabilir) backend'in
   * urettigi OPAK bir imlec metnidir, icerigi hic yorumlanmaz, oldugu gibi
   * bir sonraki istege priorityCursor olarak geri gonderilir.
   */
  nextPriorityCursor?: string | null;
}

/**
 * WebSocket uzerinden sunucudan gelen mesajlarin sekli.
 * NOTIFICATION_HIDDEN / NOTIFICATION_READ: birden fazla id'yi tek mesajda tasiyabilir.
 * NOTIFICATION_ALL_HIDDEN: hicbir id tasimaz - istemci tum listesini bosaltmali.
 */
export type ServerMessage =
  | { event: 'NOTIFICATION_CREATED'; payload: Notification }
  | { event: 'NOTIFICATION_HIDDEN'; payload: { ids: string[] } }
  | { event: 'NOTIFICATION_ALL_HIDDEN' }
  | { event: 'NOTIFICATION_READ'; payload: { ids: string[] } }
  | { event: 'PONG' };