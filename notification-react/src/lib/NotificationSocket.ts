// WebSocket baglantisini acan, mesajlari ayristiran ve baglanti koparsa ustel
// geri cekilmeyle (exponential backoff + jitter) otomatik yeniden baglanan sinif.
// Olaylari native EventTarget uzerinden, tip-guvenli on/off ile yayinlar.

import type { Notification, ServerMessage } from '../types';

/** on()/off()'un tip-guvenli olmasi icin: olay adi -> CustomEvent.detail tipi. */
interface NotificationSocketEventMap {
  notification: Notification;
  hidden: string[];       // gizlenen bildirimlerin id listesi
  allHidden: undefined;   // hideAll oldu, id yok
  read: string[];         // okundu isaretlenen bildirimlerin id listesi
  connected: undefined;
  disconnected: undefined;
}

type EventName = keyof NotificationSocketEventMap;
type Listener<K extends EventName> = (detail: NotificationSocketEventMap[K]) => void;

const INITIAL_RECONNECT_DELAY_MS = 1000;
export const MAX_RECONNECT_DELAY_MS = 30000;
// Otomatik yeniden baglanma bu kadar denemeden sonra durur. Sonrasinda
// baglanti ancak ag geri geldiginde ('online') veya sekme one alindiginda
// ('visibilitychange') tekrar denenir - boylece pil/uyku sonrasi da kurtarilir
// ama arka planda sonsuza dek denemeye devam edilmez.
export const MAX_RECONNECT_ATTEMPTS = 20;
const PING_INTERVAL_MS = 25000;

export class NotificationSocket {
  private readonly url: string;
  private readonly target = new EventTarget();
  // Kullanicinin verdigi listener -> addEventListener'a verilen sarmalayici.
  // off()'ta native removeEventListener ayni sarmalayici referansini ister.
  //
  // Anahtar OLAY ADI ILE BIRLIKTE tutuluyor (once sadece listener'di): ayni
  // fonksiyon referansi iki farkli olaya abone edilirse (orn. tek bir
  // handler'i hem 'connected' hem 'disconnected' icin kullanmak - tamamen
  // makul bir kullanim), tek anahtarli map ikinci kaydin sarmalayicisiyla
  // birincinin uzerine yazardi; off('connected', h) yanlis sarmalayiciyi
  // kaldirir, digeri de sonsuza dek dinlemeye devam ederdi (sizinti).
  private readonly wrappedListeners = new Map<string, Map<Listener<never>, EventListener>>();

  private ws: WebSocket | null = null;
  private reconnectDelay = INITIAL_RECONNECT_DELAY_MS;
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;
  private closedByUser = false;
  // MAX_RECONNECT_ATTEMPTS asildiginda kurulan, ag/sekme geri gelince
  // yeniden denemeyi tetikleyen dinleyici. Bir kez kurulur, tetiklenince kaldirilir.
  private resumeHandler: (() => void) | null = null;

  constructor(url: string) {
    this.url = url;
  }

  /** Bir olaya dinleyici ekler. `detail` tipi olay adindan cikarilir. */
  on<K extends EventName>(event: K, listener: Listener<K>): void {
    let forEvent = this.wrappedListeners.get(event);
    if (!forEvent) {
      forEvent = new Map();
      this.wrappedListeners.set(event, forEvent);
    }
    // Ayni listener ayni olaya iki kez eklenirse tek kayit kalsin - aksi halde
    // ilk sarmalayicinin referansi kaybolur ve off() ile bir daha kaldirilamaz.
    if (forEvent.has(listener as Listener<never>)) return;

    const wrapped: EventListener = (e) => {
      listener((e as CustomEvent<NotificationSocketEventMap[K]>).detail);
    };
    forEvent.set(listener as Listener<never>, wrapped);
    this.target.addEventListener(event, wrapped);
  }

  /** Bir dinleyiciyi kaldirir. on() ile verdigin AYNI fonksiyon referansi verilmeli. */
  off<K extends EventName>(event: K, listener: Listener<K>): void {
    const forEvent = this.wrappedListeners.get(event);
    const wrapped = forEvent?.get(listener as Listener<never>);
    if (!forEvent || !wrapped) return;

    this.target.removeEventListener(event, wrapped);
    forEvent.delete(listener as Listener<never>);
    if (forEvent.size === 0) {
      this.wrappedListeners.delete(event);
    }
  }

  private emit<K extends EventName>(event: K, detail: NotificationSocketEventMap[K]): void {
    this.target.dispatchEvent(new CustomEvent(event, { detail }));
  }

  connect(): void {
    this.closedByUser = false;
    // Ayni ornekte connect() iki kez cagirilirsa (orn. StrictMode provasi ya da
    // tuketicinin elle cagirmasi) ikinci cagri, birincinin soketini "sahipsiz"
    // birakirdi: this.ws yeni sokete isaret ettigi icin eskisinin onclose'u
    // erken donup ASLA kapatmazdi - sunucuda sizan bir baglanti kalirdi.
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }
    this.openSocket();
  }

  disconnect(): void {
    this.closedByUser = true;
    this.clearReconnectTimer();
    this.clearPingTimer();
    this.detachResumeHandler();
    const socket = this.ws;
    this.ws = null;
    if (!socket) return;
    // Baglanti HALA kuruluyorsa (CONNECTING), hemen close() cagirmak
    // tarayicinin "WebSocket is closed before the connection is
    // established" uyarisini basmasina sebep oluyor. Once kurulmasini
    // bekleyip, kuruldugu AN kapatiyoruz.
    if (socket.readyState === WebSocket.CONNECTING) {
      socket.addEventListener('open', () => socket.close());
    } else {
      socket.close();
    }
  }

  private openSocket(): void {
    const socket = new WebSocket(this.url);
    this.ws = socket;

    socket.onopen = () => {
      if (this.ws !== socket) return;
      this.reconnectDelay = INITIAL_RECONNECT_DELAY_MS;
      this.reconnectAttempts = 0;
      this.startPing();
      this.emit('connected', undefined);
    };

    socket.onmessage = (event: MessageEvent<string>) => {
      if (this.ws !== socket) return;
      this.handleMessage(event.data);
    };

    socket.onclose = () => {
      if (this.ws !== socket) return;
      this.clearPingTimer();
      this.emit('disconnected', undefined);
      if (!this.closedByUser) {
        this.scheduleReconnect();
      }
    };

    socket.onerror = () => {
      socket.close();
    };
  }

  private handleMessage(raw: string): void {
    let message: ServerMessage;
    try {
      message = JSON.parse(raw) as ServerMessage;
    } catch {
      return;
    }

    switch (message.event) {
      case 'NOTIFICATION_CREATED':
        this.emit('notification', message.payload);
        break;
      case 'NOTIFICATION_HIDDEN':
        this.emit('hidden', message.payload.ids);
        break;
      case 'NOTIFICATION_ALL_HIDDEN':
        this.emit('allHidden', undefined);
        break;
      case 'NOTIFICATION_READ':
        this.emit('read', message.payload.ids);
        break;
      default:
        break; // PONG gibi bizi ilgilendirmeyen mesajlar sessizce yok sayilir
    }
  }

  private scheduleReconnect(): void {
    this.clearReconnectTimer();

    if (this.reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      this.waitForResume();
      return;
    }
    this.reconnectAttempts += 1;

    // Jitter: gecikmenin %50-%100'u arasi rastgele bir sure. Sunucu yeniden
    // basladiginda tum istemcilerin AYNI ANDA baglanmaya calismasini
    // (thundering herd) onler.
    const jittered = this.reconnectDelay * (0.5 + Math.random() * 0.5);

    this.reconnectTimer = setTimeout(() => {
      this.openSocket();
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY_MS);
    }, jittered);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  /**
   * Otomatik deneme siniri asildiginda cagrilir: ag geri geldiginde ya da
   * sekme yeniden gorunur oldugunda tek seferlik yeniden baglanmayi tetikler.
   * SSR/test ortaminda window yoksa sessizce hicbir sey yapmaz.
   */
  private waitForResume(): void {
    if (this.resumeHandler || typeof window === 'undefined') return;

    const resume = () => {
      if (this.closedByUser) return;
      if (typeof document !== 'undefined' && document.visibilityState === 'hidden') return;
      this.detachResumeHandler();
      this.reconnectAttempts = 0;
      this.reconnectDelay = INITIAL_RECONNECT_DELAY_MS;
      this.openSocket();
    };
    this.resumeHandler = resume;
    window.addEventListener('online', resume);
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', resume);
    }
  }

  private detachResumeHandler(): void {
    if (!this.resumeHandler || typeof window === 'undefined') return;
    window.removeEventListener('online', this.resumeHandler);
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this.resumeHandler);
    }
    this.resumeHandler = null;
  }

  private startPing(): void {
    this.clearPingTimer();
    this.pingTimer = setInterval(() => {
      const socket = this.ws;
      // readyState kontrolu SART: soket CONNECTING/CLOSING/CLOSED iken send()
      // cagirmak InvalidStateError firlatir. Sunucu baglantiyi kapattiginda
      // onclose olayi bir sonraki tick'e kadar gelmeyebilir - tam o araliga
      // denk gelen bir ping, setInterval callback'i icinde yakalanmamis bir
      // hataya donusur ve konsolu kirletirdi.
      if (!socket || socket.readyState !== WebSocket.OPEN) return;
      try {
        socket.send(JSON.stringify({ event: 'PING' }));
      } catch {
        // Yarisi kaybettik (soket tam bu anda kapandi) - onclose zaten
        // yeniden baglanmayi tetikleyecek, burada yapacak bir sey yok.
      }
    }, PING_INTERVAL_MS);
  }

  private clearPingTimer(): void {
    if (this.pingTimer !== null) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }
}