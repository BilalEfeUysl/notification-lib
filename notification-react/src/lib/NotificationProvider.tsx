// Bildirim verisini tum React agacina dagitan kaynak bilesen: REST'ten gecmisi
// ceker, WebSocket'ten anligi dinler, ikisini birlestirip Context'le sunar.

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import type { Notification } from '../types';
import {
  fetchNotifications,
  fetchUnreadCount,
  hideAllNotifications,
  hideNotification,
  markNotificationsAsRead,
  setNotificationSaved,
} from './restClient';
import { NotificationContext, type NotificationContextValue } from './NotificationContext';
import { ThemeContext, useResolvedTheme, type ThemeName } from '../context/theme';
import type { LanguageSetting } from '../locales/i18n';
import { NotificationSocket } from './NotificationSocket';
import { playNotificationSound } from './sound';

const DEFAULT_INITIAL_LIMIT = 25;
const SOUND_STORAGE_KEY = 'notification-react:sound-enabled';
const POPUPS_STORAGE_KEY = 'notification-react:popups-enabled';

export interface NotificationProviderProps {
  basePath: string;
  websocketUrl: string;
  initialLimit?: number;
  onError?: (error: Error) => void;
  /**
   * targeting.enabled=true olan bir backend'e baglanirken ZORUNLU.
   * REST isteklerine X-User-Id/X-User-Roles header'i olarak eklenir;
   * WebSocket'e ise tarayici ozel header EKLEYEMEDIGI icin ?userId=...&roles=...
   * query parametresi olarak eklenir (backend'deki HeaderNotificationIdentityResolver
   * bunu fallback olarak okur).
   */
  identity?: { userId: string; roles?: string[] };
  /**
   * true verilirse liste, tarih yerine ONCE onceliğe (HIGH -> NORMAL -> LOW)
   * sonra tarihe gore sirali gelir (backend B11, opt-in) - bu prop
   * verilmedigi surece backend'de fazladan HICBIR hesaplama yapilmaz,
   * eski (tarih sirali) sorgu yolu aynen calisir. Varsayilan false.
   */
  sortByPriority?: boolean;
  /**
   * fetch'in credentials modu, TUM REST isteklerine uygulanir.
   * <p>
   * Cerez/oturum tabanli kimlik dogrulamasi kullanan ve frontend'i backend'den
   * FARKLI bir origin'de sunan uygulamalarda 'include' VERILMELI: fetch'in
   * varsayilani 'same-origin' oldugu icin, capraz-origin'de tarayici oturum
   * cerezini hic gondermez ve butun bildirim istekleri sessizce 401/403 doner -
   * hata mesaji da "yetkisiz" demez, sadece liste bos gelir. Backend tarafinda
   * notification.cors.allowed-origins zaten ayarli olmali (kutuphane o origin'ler
   * icin allowCredentials(true) uygular).
   */
  credentials?: RequestCredentials;
  /**
   * Uygulama genelinde tema: "light" | "dark" | "auto" (varsayilan "auto",
   * yani sistem tercihi izlenir).
   * <p>
   * Provider bunu bir ThemeContext olarak yayar; NotificationBell ve
   * PopupStack kendi `theme` prop'lari VERILMEDIGI surece bunu miras alir.
   * Ikisi ayri ayri render edildigi icin temayi tek bir yerde soylemeyi
   * saglar - aksi halde her bilesene ayri ayri gecirmek gerekirdi.
   */
  theme?: ThemeName;
  /**
   * Uygulama genelinde dil: 'tr' | 'en' | 'auto' (varsayilan 'tr';
   * 'auto' = tarayici dili).
   * <p>
   * theme ile ayni mantik: NotificationBell ve PopupStack kendi `language`
   * prop'lari VERILMEDIGI surece bunu miras alir - dili tek bir yerde
   * soylemeyi saglar.
   */
  language?: LanguageSetting;
  children: ReactNode;
}

export function NotificationProvider({
  basePath,
  websocketUrl,
  initialLimit = DEFAULT_INITIAL_LIMIT,
  onError,
  identity,
  sortByPriority = false,
  credentials,
  theme = 'auto',
  language = 'tr',
  children,
}: NotificationProviderProps) {
  const resolvedTheme = useResolvedTheme(theme);
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [nextBefore, setNextBefore] = useState<string | null>(null);
  // sortByPriority modunda sayfalama nextBefore ile DEGIL bu imlecle
  // yapilir (bkz. types.ts - NotificationPage.nextPriorityCursor).
  const [nextPriorityCursor, setNextPriorityCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [unreadCount, setUnreadCount] = useState(0);
  // Baslangicta 'connected' varsayiyoruz - socket.connect() henuz cagrilmadan
  // once kullaniciyi yanlislikla "koptu" uyarisiyla karsilamamak icin.
  // Gercek durum, asagidaki useEffect'te socket 'connected'/'disconnected'
  // olaylari geldikce guncellenir.
  const [connectionStatus, setConnectionStatus] = useState<'connected' | 'disconnected'>('connected');

  // Ses acik/kapali tercihi - baslangicta localStorage'dan okunur (hic
  // kayit yoksa varsayilan ACIK). Ref olarak da tutuluyor cunku WebSocket
  // dinleyicisi (asagida) sadece ilk kurulumda olusturuluyor - dinleyici
  // her calistiginda "guncel" degeri REF uzerinden okuyor, boylece ses
  // acilip kapandiginda socket'i yeniden baglamak GEREKMIYOR.
  const [soundEnabled, setSoundEnabledState] = useState<boolean>(() => {
    if (typeof window === 'undefined') return true;
    const stored = window.localStorage.getItem(SOUND_STORAGE_KEY);
    return stored === null ? true : stored === 'true';
  });
  const soundEnabledRef = useRef(soundEnabled);
  useEffect(() => {
    soundEnabledRef.current = soundEnabled;
  }, [soundEnabled]);

  const toggleSound = useCallback(() => {
    setSoundEnabledState((prev) => {
      const next = !prev;
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(SOUND_STORAGE_KEY, String(next));
      }
      return next;
    });
  }, []);

  // Popup (toast) gosterimi acik/kapali tercihi - ses tercihiyle AYNI desen.
  // usePopupQueue bu degeri useNotifications() uzerinden okuyup kapaliyken
  // yeni bildirimleri kuyruga hic eklemiyor - liste/rozet etkilenmiyor.
  const [popupsEnabled, setPopupsEnabledState] = useState<boolean>(() => {
    if (typeof window === 'undefined') return true;
    const stored = window.localStorage.getItem(POPUPS_STORAGE_KEY);
    return stored === null ? true : stored === 'true';
  });

  const togglePopups = useCallback(() => {
    setPopupsEnabledState((prev) => {
      const next = !prev;
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(POPUPS_STORAGE_KEY, String(next));
      }
      return next;
    });
  }, []);

  // identity, App.tsx gibi kullanan kodlarda genelde `identity={{ userId }}`
  // seklinde HER RENDER'DA YENI BIR OBJE olarak veriliyor - React'in obje
  // karsilastirmasi REFERANS bazli oldugu icin, asagidaki useMemo/useEffect
  // BU YUZDEN her render'da GEREKSIZ YERE yeniden calisiyordu (userId hic
  // degismese, hatta ayni deger tekrar yazilsa bile). Sabit bir METIN
  // anahtarina cevirerek bunu onluyoruz - stringler DEGER bazli
  // karsilastirilir, yani icerik gercekten degismedigi surece bu anahtar
  // AYNI kalir.
  const identityKey = identity ? `${identity.userId}|${(identity.roles ?? []).join(',')}` : '';

  // REST istekleri icin header'lar; identity verilmemisse hicbir header eklenmez
  // (targeting kapaliyken bu tamamen zararsizdir, backend zaten aramaz).
  // useMemo: identity degismedigi surece AYNI obje referansini geri verir -
  // asagidaki useCallback'lerin bagimlilik listesine eklenebilmesi icin bu sart,
  // yoksa her render'da yeni bir obje olusur, o da asagidaki fonksiyonlarin
  // gereksiz yere surekli yeniden yaratilmasina sebep olurdu.
  const identityHeaders: HeadersInit | undefined = useMemo(
    () =>
      identity
        ? { 'X-User-Id': identity.userId, 'X-User-Roles': (identity.roles ?? []).join(',') }
        : undefined,
    // identityKey, identity'nin icerigini tam temsil ediyor; obje referansini
    // degil bu anahtari izliyoruz (yukaridaki aciklamaya bak). Asagidaki
    // devre-disi-birakma yorumu, bagimlilik dizisiyle AYNI satira bitisik
    // olmali - araya baska bir yorum satiri girerse bir SONRAKI yorum
    // satirini susturur, asil bagimlilik dizisini degil.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [identityKey]
  );

  // WebSocket ozel header ekleyemedigi icin kimlik buraya query parametresi olarak eklenir.
  const effectiveWebsocketUrl = identity
    ? `${websocketUrl}${websocketUrl.includes('?') ? '&' : '?'}userId=${encodeURIComponent(identity.userId)}&roles=${encodeURIComponent((identity.roles ?? []).join(','))}`
    : websocketUrl;

  // onError, App.tsx gibi kullanan kodlarda genelde HER RENDER'DA yeni bir
  // fonksiyon olarak veriliyor (identity objesiyle ayni sorun). Bunu ref'e
  // alarak reportError'in KENDISINI (ve dolayisiyla asagidaki ana
  // useEffect'i) onError degistikce yeniden olusturmaktan/tetiklemekten
  // kurtariyoruz - tipki soundEnabledRef'in ayni amacla kullanildigi gibi.
  // Baglanti yasam dongusu, kullanicinin onError callback'inin KIMLIGINE
  // degil, sadece gercek bir hata olustugunda GUNCEL halinin cagirilmasina
  // ihtiyac duyar.
  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;

  const reportError = useCallback((err: unknown, fallbackMessage: string): string => {
    const message = err instanceof Error ? err.message : fallbackMessage;
    onErrorRef.current?.(err instanceof Error ? err : new Error(message));
    return message;
  }, []);

  // Socket ornegini "render'lar arasi hafiza"da tutuyoruz - useRef, useState'ten
  // farkli olarak degistiginde bileseni YENIDEN CIZDIRMEZ (re-render tetiklemez).
  // ONEMLI: socket'i asagidaki useEffect'i BEKLEMEDEN, render SIRASINDA
  // olusturuyoruz. Nedeni: React, ic ice bilesenlerde ONCE cocuklarin
  // efektlerini, SONRA ebeveynin efektini calistirir. usePopupQueue gibi
  // ALT bilesenlerin efektleri subscribe() cagirdiginda, eger socket'i
  // sadece bu Provider'in KENDI efekti icinde olustursaydik, o an
  // socketRef.current HALA null olurdu ve dinleyici asla eklenemezdi -
  // tam olarak yasadigimiz hata buydu.
  const socketRef = useRef<NotificationSocket | null>(null);
  // Socket'in EN SON hangi adresle (effectiveWebsocketUrl) olusturuldugunu
  // hatirliyoruz. identity degistiginde effectiveWebsocketUrl de degisir -
  // bu durumda ESKI socket'i atip YENI adresle bir socket kurmamiz lazim,
  // yoksa (eski koddaki hata) kimlik degisikligi WebSocket baglantisini
  // hic etkilemez, kullanici hep ILK acilistaki kimlikle dinlemeye devam eder.
  const socketUrlRef = useRef<string | null>(null);
  // loading state'i React render'i BEKLEYEREK guncellenir - hizli art arda
  // scroll'da loadMore() iki kere ust uste cagrilirsa, ikinci cagri
  // geldiginde state henuz "true" gorunmeyebilir ve AYNI ANDA iki istek
  // gidebilir. Bu ref anINDA guncellendigi icin bu yarisi (race) kesin
  // olarak kapatiyor.
  const loadingRef = useRef(false);
  if (socketRef.current === null || socketUrlRef.current !== effectiveWebsocketUrl) {
    socketRef.current?.disconnect();
    socketRef.current = new NotificationSocket(effectiveWebsocketUrl);
    socketUrlRef.current = effectiveWebsocketUrl;
  }

  // --- Ilk acilista gecmisi cek + WebSocket'e baglan ---
  useEffect(() => {
    let cancelled = false; // bilesen kaldirildiktan sonra state guncellemeyi engellemek icin

    setLoading(true);
    fetchNotifications(basePath, {
      limit: initialLimit,
      headers: identityHeaders,
      credentials,
      sort: sortByPriority ? 'priority' : undefined,
    })
      .then((page) => {
        if (cancelled) return;
        setNotifications(page.items);
        setHasMore(page.hasMore);
        setNextBefore(page.nextBefore);
        setNextPriorityCursor(page.nextPriorityCursor ?? null);
        setError(null);
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setError(reportError(err, 'Bildirimler yuklenemedi'));
      })
      .finally(() => {
        if (cancelled) return;
        setLoading(false);
      });

    // Rozet sayisi ayri bir uctan, sayfalamadan tamamen bagimsiz cekiliyor.
    function refetchUnreadCount() {
      fetchUnreadCount(basePath, identityHeaders, credentials)
        .then((count) => {
          if (!cancelled) setUnreadCount(count);
        })
        .catch(() => {
          // Sessizce gec - rozet sayisi kritik degil, ana listeyi bozmamali.
        });
    }
    refetchUnreadCount();

    // socket zaten render sirasinda olusturuldu (yukarida) - burada SADECE
    // kullaniyoruz, tekrar 'new' ile olusturmuyoruz.
    const socket = socketRef.current!;

    // Dinleyicileri ISIMLI degiskenlere atiyoruz (anonim fonksiyon degil) -
    // cunku temizlik (cleanup) sirasinda socket.off() cagirirken "hangi
    // fonksiyonu kaldiracagim" bilgisine ihtiyacimiz var. Ayni referans
    // olmadan off() dogru dinleyiciyi bulamaz.
    const handleNotification = (notification: Notification) => {
      setNotifications((prev) => [notification, ...prev]);
      if (soundEnabledRef.current) {
        playNotificationSound();
      }
      refetchUnreadCount();
    };
    const handleHidden = (ids: string[]) => {
      const idSet = new Set(ids);
      setNotifications((prev) => prev.filter((n) => !idSet.has(n.id)));
      refetchUnreadCount();
    };
    const handleAllHidden = () => {
      setNotifications([]);
      setHasMore(false);
      setNextBefore(null);
      setNextPriorityCursor(null);
      setUnreadCount(0);
    };
    const handleRead = (ids: string[]) => {
      const idSet = new Set(ids);
      setNotifications((prev) =>
        prev.map((n) => (idSet.has(n.id) ? { ...n, read: true } : n))
      );
      refetchUnreadCount();
    };
    const handleConnected = () => {
      setConnectionStatus('connected');
    };
    const handleDisconnected = () => {
      setConnectionStatus('disconnected');
    };

    socket.on('notification', handleNotification);
    socket.on('hidden', handleHidden);
    socket.on('allHidden', handleAllHidden);
    socket.on('read', handleRead);
    socket.on('connected', handleConnected);
    socket.on('disconnected', handleDisconnected);
    socket.connect();

    // Bu fonksiyon, bilesen ekrandan kaldirildiginda (unmount) VEYA
    // React StrictMode gelistirme modunda efekti "prova" ederken React
    // tarafindan otomatik cagirilir. Eklenen 4 dinleyicinin HEPSI burada
    // kaldirilmali - yoksa efekt tekrar calistiginda (StrictMode'da oldugu
    // gibi) ayni dinleyiciler ust uste birikir ve her bildirim BIRDEN FAZLA
    // kez islenir (duplikasyon hatasi).

    return () => {
      cancelled = true;
      socket.off('notification', handleNotification);
      socket.off('hidden', handleHidden);
      socket.off('allHidden', handleAllHidden);
      socket.off('read', handleRead);
      socket.off('connected', handleConnected);
      socket.off('disconnected', handleDisconnected);
      socket.disconnect();
    };
  }, [basePath, websocketUrl, initialLimit, identityKey, identityHeaders, sortByPriority, credentials, reportError]);

  const loadMore = useCallback(async () => {
    if (loadingRef.current || !hasMore) return;
    if (sortByPriority ? nextPriorityCursor === null : nextBefore === null) return;
    loadingRef.current = true;
    setLoading(true);
    try {
      const page = sortByPriority
        ? await fetchNotifications(basePath, {
            priorityCursor: nextPriorityCursor ?? undefined,
            limit: initialLimit,
            headers: identityHeaders,
            credentials,
            sort: 'priority',
          })
        : await fetchNotifications(basePath, { before: nextBefore ?? undefined, limit: initialLimit, headers: identityHeaders, credentials });
      setNotifications((prev) => [...prev, ...page.items]);
      setHasMore(page.hasMore);
      setNextBefore(page.nextBefore);
      setNextPriorityCursor(page.nextPriorityCursor ?? null);
      setError(null);
    } catch (err) {
      setError(reportError(err, 'Bildirimler yuklenemedi'));
    } finally {
      loadingRef.current = false;
      setLoading(false);
    }
  }, [basePath, initialLimit, hasMore, nextBefore, nextPriorityCursor, sortByPriority, identityHeaders, credentials, reportError]);

  const hide = useCallback(
    async (id: string) => {
      try {
        await hideNotification(basePath, id, identityHeaders, credentials);
        setNotifications((prev) => prev.filter((n) => n.id !== id));
        fetchUnreadCount(basePath, identityHeaders, credentials).then(setUnreadCount).catch(() => {});
      } catch (err) {
        setError(reportError(err, 'Bildirim silinemedi'));
      }
    },
    [basePath, identityHeaders, credentials, reportError]
  );

  const hideAll = useCallback(async () => {
    try {
      await hideAllNotifications(basePath, identityHeaders, credentials);
      setNotifications([]);
      setHasMore(false);
      setNextBefore(null);
      setNextPriorityCursor(null);
      setUnreadCount(0);
    } catch (err) {
      setError(reportError(err, 'Bildirimler silinemedi'));
    }
  }, [basePath, identityHeaders, credentials, reportError]);

  const markAsRead = useCallback(
    async (ids: string[]) => {
      if (ids.length === 0) return;
      try {
        await markNotificationsAsRead(basePath, ids, identityHeaders, credentials);
        const idSet = new Set(ids);
        setNotifications((prev) =>
          prev.map((n) => (idSet.has(n.id) ? { ...n, read: true } : n))
        );
        fetchUnreadCount(basePath, identityHeaders, credentials).then(setUnreadCount).catch(() => {});
      } catch (err) {
        setError(reportError(err, 'Bildirimler okundu isaretlenemedi'));
      }
    },
    [basePath, identityHeaders, credentials, reportError]
  );

  // read/hidden'in aksine GERI ALINABILIR - ayni fonksiyon hem kaydetmek hem
  // kaydi kaldirmak icin kullaniliyor, mevcut degerin tersini yollar.
  const toggleSaved = useCallback(
    async (id: string) => {
      const current = notifications.find((n) => n.id === id);
      const next = !(current?.saved ?? false);
      try {
        await setNotificationSaved(basePath, id, next, identityHeaders, credentials);
        setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, saved: next } : n)));
      } catch (err) {
        setError(reportError(err, 'Bildirim kaydedilemedi'));
      }
    },
    [basePath, identityHeaders, credentials, notifications, reportError]
  );

  // "Kayitlilar" gorunumu (NotificationPanel) icin: ana listeden BAGIMSIZ,
  // kendi sayfalamasini yoneten ayri bir sorgu. Context'in kendi
  // `notifications` state'ini DEGISTIRMIYOR - cagiran taraf sonucu kendi
  // yerel state'inde tutup NotificationList'e ayrica gecirir.
  const fetchSaved = useCallback(
    (before?: string, query?: string) =>
      fetchNotifications(basePath, { before, limit: initialLimit, saved: true, q: query, headers: identityHeaders, credentials }),
    [basePath, initialLimit, identityHeaders, credentials]
  );

  // Sunucu tarafli arama (backend'de TUM gecmiste, tek istekte). Sadece
  // NotificationBell'e enableServerSearch verildiyse kullaniliyor -
  // varsayilanda arama sadece o an yuklu olan (notifications) uzerinde
  // yerel olarak yapiliyor (bkz. NotificationPanel).
  const searchNotificationsRemote = useCallback(
    (query: string, before?: string) =>
      fetchNotifications(basePath, { before, limit: initialLimit, q: query, headers: identityHeaders, credentials }),
    [basePath, initialLimit, identityHeaders, credentials]
  );

  // Bagimlilik listesi ONEMLI: effectiveWebsocketUrl, socket'in NE ZAMAN
  // yenilendigini (kimlik degistiginde) birebir yansitiyor. subscribe'in
  // KENDI kimligini bu deger degistikce degistirmezsek, ona bagimli olan
  // usePopupQueue gibi tuketicilerin dinleme efekti SADECE BIR KERE calisir
  // ve dinleyicisini HEP ILK socket'e ekler - socket sonradan yenilendiginde
  // (kimlik degisince) o eski/artik baglantisiz socket'e bagli kalir, canli
  // bildirimler bir daha HIC ulasmaz. Bu deger degistikce subscribe'in
  // kimligi de degisince, tuketicilerin efekti yeniden calisip GUNCEL
  // socket'e yeniden abone olur.
  const subscribe = useCallback(
    (listener: (notification: Notification) => void) => {
      const socket = socketRef.current;
      if (!socket) {
        return () => {};
      }
      socket.on('notification', listener);
      return () => socket.off('notification', listener);
    },
    // effectiveWebsocketUrl kasitli bir bagimlilik: lint bunu "gereksiz" sanir
    // cunku callback govdesinde dogrudan kullanilmiyor, ama subscribe'in
    // KIMLIGINI bu deger degistikce (kimlik degisip socket yenilendiginde)
    // degistirmesi gerekiyor - yoksa usePopupQueue gibi tuketiciler HEP ILK
    // socket'e bagli kalir (yukaridaki aciklamaya bak).
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [effectiveWebsocketUrl]
  );

  // useMemo SART: bu obje her render'da yeniden olusturulsaydi, Context degeri
  // her seferinde YENI bir referans olurdu ve useNotifications() cagiran TUM
  // bilesenler (kutuphaneninkiler + uygulamanin kendi bilesenleri) Provider
  // her guncellendiginde - bagli olduklari deger hic degismese bile - yeniden
  // cizilirdi. Provider tum uygulama agacini sardigi icin bunun bedeli
  // dogrudan tuketicinin uygulamasina yansiyordu.
  const value: NotificationContextValue = useMemo(
    () => ({
      notifications,
      hasMore,
      loading,
      error,
      loadMore,
      hide,
      hideAll,
      markAsRead,
      unreadCount,
      soundEnabled,
      toggleSound,
      popupsEnabled,
      togglePopups,
      toggleSaved,
      fetchSaved,
      searchNotificationsRemote,
      subscribe,
      connectionStatus,
      language,
    }),
    [
      notifications,
      hasMore,
      loading,
      error,
      loadMore,
      hide,
      hideAll,
      markAsRead,
      unreadCount,
      soundEnabled,
      toggleSound,
      popupsEnabled,
      togglePopups,
      toggleSaved,
      fetchSaved,
      searchNotificationsRemote,
      subscribe,
      connectionStatus,
      language,
    ]
  );

  return (
    <NotificationContext.Provider value={value}>
      <ThemeContext.Provider value={resolvedTheme}>{children}</ThemeContext.Provider>
    </NotificationContext.Provider>
  );
}