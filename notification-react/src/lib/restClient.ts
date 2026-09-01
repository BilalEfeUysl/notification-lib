// Backend'in REST uclarina fetch ile istek atan fonksiyonlar. `basePath`
// disaridan gelir - URL hicbir yerde sabit yazilmiyor.
import type { NotificationPage, NotificationPriority } from '../types';

/**
 * GET {basePath} ucuna istek atar, gecmis bildirimleri sayfalanmis sekilde ceker.
 *
 * @param basePath  orn. "http://localhost:8080/api/notifications"
 * @param options   opsiyonel sorgu parametreleri (backend'deki @RequestParam'larla birebir eslesir)
 */
export async function fetchNotifications(
  basePath: string,
  options?: {
    before?: string;
    limit?: number;
    priority?: NotificationPriority;
    /** true verilirse SADECE kaydedilmis bildirimler doner ("kayitlilar" gorunumu). */
    saved?: boolean;
    /** Verilirse serbest metin arama yapilir (baslik/icerik/tip/kaynak/tarih alanlarinda). */
    q?: string;
    /**
     * 'priority' verilirse liste, tarih yerine ONCE onceliğe (HIGH -> NORMAL
     * -> LOW) sonra tarihe gore sirali doner (B11, opt-in) - q/saved/priority
     * ile BIRLIKTE kullanilamaz (backend 400 doner). Bu modda sayfalama
     * `before` ile DEGIL, `priorityCursor` ile yapilir (bkz. nextPriorityCursor).
     */
    sort?: 'priority';
    /** sort: 'priority' iken sayfalama imleci - onceki sayfanin nextPriorityCursor'u, oldugu gibi geri gonderilir. */
    priorityCursor?: string;
    headers?: HeadersInit;
    /**
     * fetch'in credentials modu. Cerez/oturum tabanli kimlik dogrulamasi
     * kullanan ve frontend'i backend'den FARKLI bir origin'de sunan
     * uygulamalar icin 'include' verilmeli - aksi halde tarayici cerezi
     * gondermez ve istekler sessizce 401 doner.
     */
    credentials?: RequestCredentials;
  }
): Promise<NotificationPage> {
  const params = new URLSearchParams();
  if (options?.before) params.set('before', options.before);
  if (options?.limit !== undefined) params.set('limit', String(options.limit));
  if (options?.priority) params.set('priority', options.priority);
  if (options?.saved) params.set('saved', 'true');
  if (options?.q) params.set('q', options.q);
  if (options?.sort) params.set('sort', options.sort);
  if (options?.priorityCursor) params.set('priorityCursor', options.priorityCursor);

  const query = params.toString();
  const url = query ? `${basePath}?${query}` : basePath;

  const response = await fetch(url, { headers: options?.headers, credentials: options?.credentials, cache: 'no-store' });

  if (!response.ok) {
    throw new Error(`Bildirimler alinamadi: ${response.status} ${response.statusText}`);
  }

  return response.json() as Promise<NotificationPage>;
}

/**
 * DELETE {basePath}/{id} ucuna istek atar, tek bir bildirimi gizler (soft delete).
 */
export async function hideNotification(basePath: string, id: string, headers?: HeadersInit, credentials?: RequestCredentials): Promise<void> {
  const response = await fetch(`${basePath}/${id}`, { method: 'DELETE', headers, credentials });

  if (!response.ok) {
    throw new Error(`Bildirim silinemedi: ${response.status} ${response.statusText}`);
  }
  // Backend 204 No Content donuyor (govdesi yok), o yuzden response.json() cagirmiyoruz.
}

/**
 * DELETE {basePath} ucuna istek atar, GORUNUR TUM bildirimleri gizler.
 */
export async function hideAllNotifications(basePath: string, headers?: HeadersInit, credentials?: RequestCredentials): Promise<void> {
  const response = await fetch(basePath, { method: 'DELETE', headers, credentials });

  if (!response.ok) {
    throw new Error(`Bildirimler silinemedi: ${response.status} ${response.statusText}`);
  }
}

/**
 * PATCH {basePath}/read ucuna istek atar, verilen id'leri okundu olarak isaretler.
 * Bos dizi verilirse hicbir istek atilmaz (gereksiz cagriyi onlemek icin).
 */
export async function markNotificationsAsRead(basePath: string, ids: string[], headers?: HeadersInit, credentials?: RequestCredentials): Promise<void> {
  if (ids.length === 0) return;

  const response = await fetch(`${basePath}/read`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...headers },
    credentials,
    body: JSON.stringify(ids),
  });

  if (!response.ok) {
    throw new Error(`Bildirimler okundu olarak isaretlenemedi: ${response.status} ${response.statusText}`);
  }
}


/**
 * PATCH {basePath}/{id}/saved ucuna istek atar, bir bildirimin kaydedildi
 * durumunu ayarlar. read/hidden'in aksine GERI ALINABILIR (saved=false ile
 * kaydi kaldirir).
 */
export async function setNotificationSaved(
  basePath: string,
  id: string,
  saved: boolean,
  headers?: HeadersInit,
  credentials?: RequestCredentials
): Promise<void> {
  const response = await fetch(`${basePath}/${id}/saved`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json', ...headers },
    credentials,
    body: JSON.stringify({ saved }),
  });

  if (!response.ok) {
    throw new Error(`Bildirim kaydedilemedi: ${response.status} ${response.statusText}`);
  }
}

/**
 * GET {basePath}/unread-count ucuna istek atar. Sayfalamadan tamamen
 * bagimsiz, gercek TOPLAM okunmamis bildirim sayisini doner - rozette
 * dogru sayiyi gostermek icin (yuklu listeden saymak yanlis sonuc verir,
 * cunku sayfalama yuzunden listede TUM bildirimler bulunmuyor olabilir).
 */
export async function fetchUnreadCount(basePath: string, headers?: HeadersInit, credentials?: RequestCredentials): Promise<number> {
  const response = await fetch(`${basePath}/unread-count`, { headers, credentials, cache: 'no-store' });

  if (!response.ok) {
    throw new Error(`Okunmamış sayısı alınamadı: ${response.status} ${response.statusText}`);
  }

  const data = (await response.json()) as { count: number };
  return data.count;
}