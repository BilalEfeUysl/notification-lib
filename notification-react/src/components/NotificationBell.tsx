// Navbar'a konan zil: okunmamis sayisini rozette gosterir, tiklaninca
// bildirim panelini acar.
//
// Popup (toast) yigini BILEREK burada DEGIL - ayri bir <PopupStack /> olarak
// render edilir. Eskiden zil kendi PopupStack'ini icine gomuyordu; bu,
// (1) zil'i her render edende tam ekran position:fixed bir katman
// olusturuyor, (2) iki zil render edildiginde ya da zil kosullu gizlendiginde
// popup'lari cift/hic gosteriyor, (3) popup'i sayfanin baska bir yerine
// koymayi imkansiz kiliyordu.

import {
  forwardRef,
  useContext,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
} from 'react';
import { Badge, Dropdown, Popover } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { BellStatusIcon } from '../icons/BellStatusIcon';
import { useNotifications } from '../hooks/useNotifications';
import { NotificationPanel } from './NotificationPanel';
import { NotificationErrorBoundary } from './NotificationErrorBoundary';
import { ThemeContext, getThemeTokens, useResolvedTheme, type ThemeName } from '../context/theme';
import type { TypeStyle } from '../styles/typeStyles';
import { getMessages, resolveLanguage, type LanguageSetting, type TimeFormat } from '../locales/i18n';
import type { Notification } from '../types';
import '../context/theme.css';
import './NotificationBell.css';

export type PopupPlacement = 'bottomRight' | 'bottomLeft' | 'bottom' | 'topRight' | 'topLeft' | 'top';

/**
 * antd'nin Popover'i, placement 'Right' ile bitince panelin SAĞ kenarını,
 * 'Left' ile bitince panelin SOL kenarını zile hizalıyor - ok (arrow)
 * merkeze gelsin diye (arrowPointAtCenter) her ikisinde de aynı miktarda
 * yatay bir telafi gerekiyor. Ölçülerek bulundu (26px). Ortalanmış
 * placement'larda ('bottom'/'top') hiçbir telafi gerekmiyor.
 *
 * ÖNEMLİ: Bu telafi `align.offset`'e verilir - antd bu değeri okla
 * govdeyi (ant-popover-arrow + ant-popover-inner) TEK BİR BLOK olarak
 * kaydırmak icin kullanıyor, yani ok GOVDEYLE BİRLİKTE hareket eder.
 * Bu yuzden panel.offsetX/offsetY (kullanicinin ELLE verdigi, SADECE
 * govdeyi kaydırması gereken deger) BURAYA hic karistirilmiyor - eger
 * karisirsa (eskiden oyleydi) kullanici deger verince bu telafi silinip
 * ok artik zilin merkezini gostermez olurdu. Bu yuzden asagidaki iki
 * fonksiyon HER ZAMAN sadece placement'a gore hesaplanir, kullanicinin
 * panel.offsetX/offsetY'sinden ETKİLENMEZ - o deger ayrı olarak
 * overlayInnerStyle ile SADECE govdeye (ok'a degil) uygulanıyor (asagida,
 * Popover JSX'inde).
 */
const AUTO_OFFSET_X_PX = 26;

function getDefaultPanelOffsetX(placement: PopupPlacement): number {
  if (placement.endsWith('Right')) return AUTO_OFFSET_X_PX;
  if (placement.endsWith('Left')) return -AUTO_OFFSET_X_PX;
  return 0;
}

/**
 * Dikeyde de aynı mantık geçerli: antd'nin koordinat hesabında ayni offsetY
 * degeri, panel ASAGIDA iken zilden UZAKLASTIRIR, panel YUKARIDA iken ise
 * zile YAKLASTIRIR - yon placement'a gore ters isliyor. Varsayilan taban 4px
 * uzerinden, aralarindaki bosluk 3px kisilecek sekilde her iki yonde de
 * telafi ediliyor.
 */
/**
 * antd overlay'lerinin (panel + sag-tik menusu) nereye render edilecegi.
 * Kullanici bir sey vermezse document.body - bkz. NotificationPanelOptions.getPopupContainer.
 * SSR'da document olmadigi icin antd'nin kendi varsayilanina birakiyoruz.
 */
function resolvePopupContainer(
  custom?: (triggerNode: HTMLElement) => HTMLElement
): ((triggerNode: HTMLElement) => HTMLElement) | undefined {
  if (custom) return custom;
  if (typeof document === 'undefined') return undefined;
  return () => document.body;
}

const BASE_OFFSET_Y_PX = 4;
const VERTICAL_GAP_REDUCTION_PX = 3;

function getDefaultPanelOffsetY(placement: PopupPlacement): number {
  return placement.startsWith('top')
    ? BASE_OFFSET_Y_PX + VERTICAL_GAP_REDUCTION_PX
    : BASE_OFFSET_Y_PX - VERTICAL_GAP_REDUCTION_PX;
}

/**
 * Bir bildirim NE ZAMAN okundu isaretlensin:
 * - 'onOpen'  (varsayilan): panel kapanirken, o an listede GORUNEN her sey okundu olur.
 * - 'onClick': SADECE tiklanan bildirim okundu olur; panelin acilip kapanmasinin etkisi yok.
 * - 'manual' : kutuphane hicbir otomatik isaretleme yapmaz; markAsRead'i (useNotifications()
 *              uzerinden) istedigin an kendin cagirirsin.
 */
export type ReadTrigger = 'onOpen' | 'onClick' | 'manual';

export interface NotificationBadgeOptions {
  /**
   * Rozette okunmamış bildirimlerin SAYISINI mı yoksa yalnızca küçük bir
   * NOKTAYI mı göster. Varsayılan: true (sayı). `false` verirsen sayı yerine
   * "okunmamış var / yok" bilgisini taşıyan düz bir nokta gösterilir.
   */
  showCount?: boolean;
  color?: string;
  /**
   * Rozetin boyutu. antd'nin varsayılanı ("default") özellikle 2 haneli
   * sayılarda zile göre orantısız/büyük görünebiliyor - bu yüzden
   * kütüphanenin kendi varsayılanı "small". İstersen "default" ile
   * eski (daha büyük) haline dönebilirsin.
   */
  size?: 'small' | 'default';
}

export interface NotificationPanelOptions {
  placement?: PopupPlacement;
  /** Panelin genişliği (px). Varsayılan: 440. */
  width?: number;
  /** Panelin içindeki kaydırılabilir liste alanının yüksekliği (px). Varsayılan: 420. */
  height?: number;
  /**
   * Panelin GÖVDESİNİ (kutunun kendisi - kenarlık, köşe yuvarlama, gölge)
   * piksel bazında ELLE kaydırır - ok (arrow) bundan ETKİLENMEZ, her zaman
   * zilin tam ortasını göstermeye devam eder (kütüphanenin kendi iç
   * hizalama düzeltmesi placement'a göre ayrıca ve her zaman uygulanır).
   * Panel farklı bir yere (ör. başka bir konumdaki bir zile) daha iyi
   * otursun diye ince ayar yapmak istediğinde kullan. Vermezsen panel
   * hiç kaydırılmaz (0, 0).
   */
  offsetX?: number;
  offsetY?: number;
  /**
   * Oku (arrow), GÖVDEDEN BAĞIMSIZ olarak piksel bazında kaydırır. Normalde
   * buna gerek yoktur - ok zaten her zaman zilin merkezini gösterir. Nadir
   * bir özel durum için (örn. tasarım gereği okun kasıtlı olarak merkezden
   * kaydırılmış durması istenirse) buraya elle bir değer verilebilir.
   * Vermezsen ok hiç kaydırılmaz.
   */
  arrowOffsetX?: number;
  arrowOffsetY?: number;
  /**
   * Panelin GÖVDESİNİN arka plan rengi. Vermezsen mevcut temaya göre
   * otomatik seçilir (theme="light" için beyaz, theme="dark" için #1f1f1f).
   * Ok (arrow) da varsayılan olarak bu rengi alır - ayrı bir renk istersen
   * `arrowBackground` ver.
   */
  background?: string;
  /**
   * Okun (arrow) arka plan rengi. Vermezsen `background`'i (o da yoksa
   * temayı) takip eder - yani normalde gövdeyle aynı renk olur. SADECE
   * oku gövdeden farklı bir renkte istediğinde ver.
   */
  arrowBackground?: string;
  /** Panelin z-index'i (antd Popover). Kullanan uygulamanın overlay'leriyle çakışırsa ayarla. */
  zIndex?: number;
  /**
   * Panelin (ve zil sag-tik menusunun) DOM'da hangi elemanin altina
   * render edilecegi. Varsayilan: {@code document.body}.
   * <p>
   * Neden varsayilan body: antd'nin kendi varsayilani "tetikleyicinin
   * ebeveyni"dir. Panel mutlak konumlandirildigi icin, atalarindan birinde
   * {@code transform}, {@code filter}, {@code backdrop-filter} veya
   * {@code overflow: hidden} varsa panel ya yanlis yere konumlanir ya da
   * kirpilir - bulanik/sabit bir navbar'in icine konan zil bu tuzagi cok
   * kolay kuruyor. body'ye render etmek bunu kokten cozer (PopupStack'in
   * portal'i ile ayni mantik).
   * <p>
   * Panelin kaydirilabilir bir kapsayiciyla BIRLIKTE hareket etmesi
   * gerekiyorsa burayi o kapsayici olarak degistir.
   */
  getPopupContainer?: (triggerNode: HTMLElement) => HTMLElement;
}

export interface RenderTriggerProps {
  unreadCount: number;
  onClick: () => void;
}

/**
 * `ref` ile disariya acilan komutlar. Kullanim:
 *   const bellRef = useRef<NotificationBellHandle>(null);
 *   <NotificationBell ref={bellRef} />
 *   bellRef.current?.open();
 */
export interface NotificationBellHandle {
  /** Bildirim panelini acar. */
  open: () => void;
  /** Bildirim panelini kapatir. */
  close: () => void;
  /** Panel acikken kapatir, kapaliyken acar. */
  toggle: () => void;
}

export interface NotificationBellProps {
  /**
   * 'tr' | 'en' | 'auto'. VERILMEZSE NotificationProvider'a verilen dil
   * miras alinir (onun da varsayilani 'tr'). 'auto' → tarayici dili.
   * Sadece bu zil'i uygulamanin genelinden FARKLI bir dilde istiyorsan ver.
   */
  language?: LanguageSetting;
  /**
   * "light" | "dark" | "auto". VERILMEZSE NotificationProvider'a verilen
   * tema miras alinir (onun da varsayilani "auto" = sistem tercihi).
   * Sadece bu zil'i uygulamanin genelinden FARKLI bir temada istiyorsan ver.
   */
  theme?: ThemeName;
  /**
   * Zil tetikleyicisinde gosterilecek ikon. VERMEZSEN kutuphanenin
   * varsayilan durum-gostergeli zili kullanilir (bkz. `showStatusIcon`).
   * Kendi ikonunu verirsen oldugu gibi gosterilir, `showStatusIcon`
   * ayarinin etkisi olmaz.
   */
  icon?: ReactNode;
  /**
   * Varsayilan zil ikonu, ses ve bildirim durumunu ikonun uzerinde
   * gosterir: ses acikken canin yaninda titresim yaylari, bildirimler
   * (popup'lar) kapaliyken can uzerinde capraz cizgi. Bu durumlar sag-tik
   * menusunden ("Sesi kapat" / "Bildirimleri kapat") degistirilir.
   * `false` verirsen sade bir zil (<BellOutlined/>) kullanilir.
   * `icon` prop'unu verdiysen bu ayarin etkisi yoktur. Varsayilan: true.
   */
  showStatusIcon?: boolean;
  className?: string;
  style?: CSSProperties;
  badge?: NotificationBadgeOptions;
  panel?: NotificationPanelOptions;
  typeStyles?: Record<string, Partial<TypeStyle>>;
  /** Okunmamış bildirimlerde listede köşede küçük bir nokta gösterilsin mi. Varsayılan: true. */
  showUnreadIndicator?: boolean;
  /** Bildirim zaman damgası formatı. Varsayılan: 'full' ("26.08.2026 14:32"), liste öğesinin altında gösterilir. */
  timeFormat?: TimeFormat;
  /** success/error/warning/info tipleri için başlığın yanında küçük bir ikon gösterilsin mi. Varsayılan: false. */
  showTypeIcons?: boolean;
  /** Bildirim ne zaman okundu isaretlensin. Varsayilan: 'onOpen'. */
  readTrigger?: ReadTrigger;
  /**
   * Panelin acik olup olmadigini DISARIDAN kontrol etmek istersen ver
   * (kontrollu mod). Verildiginde bilesen kendi ic state'ini KULLANMAZ -
   * acilip kapanmayi tamamen sen yonetirsin, `onOpenChange` ile haberdar
   * olursun. Vermezsen bilesen kendi ic state'iyle calisir (varsayilan).
   */
  open?: boolean;
  /**
   * Panel her acilmak/kapanmak istediginde cagrilir (kullanici zile
   * tikladi, disari tikladi, Esc'e basti...). Kontrollu modda (`open`
   * verildiginde) yeni durumu buradan alip `open`'i guncellemen gerekir.
   */
  onOpenChange?: (open: boolean) => void;
  /**
   * Arama SADECE o an yuklu olan bildirimlerde (yerel, aninda) mi yapilsin,
   * yoksa backend'de TUM gecmiste mi (sunucu tarafli)? Varsayilan: false (yerel).
   */
  enableServerSearch?: boolean;
  onNotificationClick?: (notification: Notification) => void;
  renderTrigger?: (props: RenderTriggerProps) => ReactNode;
  renderItem?: (notification: Notification, actions: { hide: () => void }) => ReactNode;
  /**
   * Kütüphane içinde beklenmedik bir render hatası olursa gösterilecek
   * yedek görünüm. Verilmezse, hata anında bildirim alanı sessizce
   * kaybolur (hiçbir şey render edilmez) - ama KULLANAN UYGULAMANIN
   * geri kalanı çökmez.
   */
  errorFallback?: ReactNode;
  /** Yakalanan render hatasını dışarıya (loglama vb. için) bildirir. */
  onRenderError?: (error: Error) => void;
}

export const NotificationBell = forwardRef<NotificationBellHandle, NotificationBellProps>(
  function NotificationBell({
  language: languageSetting,
  theme,
  icon,
  showStatusIcon = true,
  className,
  style,
  badge,
  panel,
  typeStyles,
  showUnreadIndicator = true,
  timeFormat = 'full',
  showTypeIcons = false,
  readTrigger = 'onOpen',
  enableServerSearch = false,
  open: controlledOpen,
  onOpenChange,
  onNotificationClick,
  renderTrigger,
  renderItem,
  errorFallback,
  onRenderError,
}: NotificationBellProps, ref) {
  const {
    notifications,
    hideAll,
    markAsRead,
    soundEnabled,
    toggleSound,
    popupsEnabled,
    togglePopups,
    unreadCount: totalUnreadCount,
    language: providerLanguage,
  } = useNotifications();
  // theme VERILMEZSE NotificationProvider'in yaydigi ThemeContext'i miras
  // aliriz; verilirse bu zil (ve alt agaci) icin onu ezer. Iki hook da
  // KOSULSUZ cagriliyor - hook kurallari geregi.
  const inheritedTheme = useContext(ThemeContext);
  const ownTheme = useResolvedTheme(theme ?? 'auto');
  const resolvedTheme = theme === undefined ? inheritedTheme : ownTheme;
  const popupContainer = resolvePopupContainer(panel?.getPopupContainer);
  const tokens = getThemeTokens(resolvedTheme);
  // language prop'u verilmemisse Provider'in dilini miras al (theme ile ayni desen).
  const language = resolveLanguage(languageSetting ?? providerLanguage);
  const messages = getMessages(language);
  // Kontrollu/kontrolsuz: `open` prop'u verilmisse onu kullan, degilse kendi
  // ic state'imizi. `panelOpen` her iki durumda da "su anki gercek durum".
  const [uncontrolledOpen, setUncontrolledOpen] = useState(false);
  const isControlled = controlledOpen !== undefined;
  const panelOpen = isControlled ? controlledOpen : uncontrolledOpen;
  const [bellContextMenuOpen, setBellContextMenuOpen] = useState(false);

  function handleBellContextMenu(e: React.MouseEvent) {
    e.preventDefault();
    setBellContextMenuOpen(true);
  }

  // trigger={[]} verdigimiz icin antd'nin kendi "disariya tiklayinca kapat"
  // mekanizmasi da devre disi kaliyor (ayni mekanizmanin parcasi) - bu yuzden
  // kendi listener'imizi ekliyoruz. Bir SECENEGE tiklamak da bir "click"
  // oldugu icin, bu ayni listener menude bir seye tiklaninca da otomatik
  // kapatiyor - ayrica kod yazmaya gerek yok.
  useEffect(() => {
    if (!bellContextMenuOpen) return;
    function handleDocumentClick() {
      setBellContextMenuOpen(false);
    }
    document.addEventListener('click', handleDocumentClick);
    return () => document.removeEventListener('click', handleDocumentClick);
  }, [bellContextMenuOpen]);

  const bellContextMenuItems = [
    {
      key: 'toggle-sound',
      label: soundEnabled ? messages.muteSound : messages.unmuteSound,
      onClick: toggleSound,
    },
    {
      key: 'toggle-popups',
      label: popupsEnabled ? messages.hidePopups : messages.showPopups,
      onClick: togglePopups,
    },
  ];
  const triggerWrapperRef = useRef<HTMLSpanElement>(null);

  // Kullanici kendi ikonunu verdiyse aynen kullan; vermediyse varsayilan
  // durum-gostergeli zil (showStatusIcon=false ise sade zil).
  const resolvedIcon =
    icon ??
    (showStatusIcon ? (
      <BellStatusIcon sound={soundEnabled} popups={popupsEnabled} />
    ) : (
      <BellOutlined className="notif-bell-default-icon" />
    ));

  const showCount = badge?.showCount ?? true;
  // Rozet artik yuklu listeden SAYMIYOR (sayfalama yuzunden bu yanlis
  // sonuc verirdi - sadece ilk 25/50 kayit arasindan sayardi). Bunun
  // yerine NotificationProvider'in ayri bir uctan cektigi GERCEK toplam
  // sayiyi (totalUnreadCount) kullaniyor.
  const hasNotifications = totalUnreadCount > 0;

  function handleOpenChange(visible: boolean) {
    // Kontrolsuz modda kendi state'imizi guncelleriz; kontrollu modda
    // guncelleme cagirana kalir, biz sadece haber veririz.
    if (!isControlled) setUncontrolledOpen(visible);
    onOpenChange?.(visible);
    if (!visible && readTrigger === 'onOpen') {
      const unreadIds = notifications.filter((n) => !n.read).map((n) => n.id);
      if (unreadIds.length > 0) markAsRead(unreadIds);
    }
  }

  // `ref` ile disariya acilan komutlar. panelOpen degistikce toggle'in
  // guncel degeri gormesi icin bagimliliklara ekli.
  useImperativeHandle(
    ref,
    () => ({
      open: () => handleOpenChange(true),
      close: () => handleOpenChange(false),
      toggle: () => handleOpenChange(!panelOpen),
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [panelOpen, isControlled, readTrigger],
  );

  // Tek bir bildirime tiklandiginda cagrilir - hem panelde hem popup'ta
  // AYNI fonksiyon kullanilir. Kullanicinin kendi onNotificationClick'ini
  // her zaman cagirir; readTrigger 'onClick' ise ayrica o bildirimi
  // okundu isaretler.
  function handleNotificationClick(notification: Notification) {
    onNotificationClick?.(notification);
    if (readTrigger === 'onClick' && !notification.read) {
      markAsRead([notification.id]);
    }
  }

  // Esc ile paneli kapat, kapaninca odagi zile geri dondur.
  useEffect(() => {
    if (!panelOpen) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        handleOpenChange(false);
        triggerWrapperRef.current?.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [panelOpen, readTrigger]);

  const panelContent = (
    <NotificationPanel
      language={language}
      onClearAll={hideAll}
      width={panel?.width}
      height={panel?.height}
      typeStyles={typeStyles}
      showUnreadIndicator={showUnreadIndicator}
      timeFormat={timeFormat}
      showTypeIcons={showTypeIcons}
      onNotificationClick={handleNotificationClick}
      renderItem={renderItem}
      enableServerSearch={enableServerSearch}
    />
  );

  return (
    <NotificationErrorBoundary fallback={errorFallback} onError={onRenderError}>
    <ThemeContext.Provider value={resolvedTheme}>
      <Dropdown
        menu={{ items: bellContextMenuItems }}
        open={bellContextMenuOpen}
        onOpenChange={setBellContextMenuOpen}
        trigger={[]}
        overlayClassName={resolvedTheme === 'dark' ? 'notif-theme-dark' : 'notif-theme-light'}
        getPopupContainer={popupContainer}
      >
      <Popover
        trigger="click"
        open={panelOpen}
        onOpenChange={handleOpenChange}
        placement={panel?.placement ?? 'bottomRight'}
        zIndex={panel?.zIndex}
        getPopupContainer={popupContainer}
        autoAdjustOverflow
        arrowPointAtCenter
        overlayClassName={resolvedTheme === 'dark' ? 'notif-theme-dark' : 'notif-theme-light'}
        align={{
          offset: [
            getDefaultPanelOffsetX(panel?.placement ?? 'bottomRight'),
            getDefaultPanelOffsetY(panel?.placement ?? 'bottomRight'),
          ],
        }}
        overlayInnerStyle={
          panel?.offsetX || panel?.offsetY
            ? { transform: `translate(${panel?.offsetX ?? 0}px, ${panel?.offsetY ?? 0}px)` }
            : undefined
        }
        overlayStyle={
          panel?.background || panel?.arrowBackground || panel?.arrowOffsetX || panel?.arrowOffsetY
            ? ({
                ...(panel?.background ? { '--notif-panel-bg': panel.background } : {}),
                ...(panel?.arrowBackground ? { '--notif-arrow-bg': panel.arrowBackground } : {}),
                ...(panel?.arrowOffsetX ? { '--notif-arrow-offset-x': `${panel.arrowOffsetX}px` } : {}),
                ...(panel?.arrowOffsetY ? { '--notif-arrow-offset-y': `${panel.arrowOffsetY}px` } : {}),
              } as React.CSSProperties)
            : undefined
        }
        content={panelContent}
      >
        {/* onContextMenu KASITLI OLARAK burada, Popover'in ICINDE - Popover'in
            content'i (panel) bir portal ile DOM'un baska bir yerine render
            ediliyor, ama React'in olay sistemi sag-tik gibi olaylari DOM
            agacina degil REACT bilesen agacina gore yukari tasiyor. Eger bu
            span Popover'in DISINDA olsaydi, panelin (portalin) icinde
            HERHANGI bir yere sag tiklamak da bu dinleyiciye ulasirdi -
            tam yasanan hataydi. Burada, SADECE tetikleyici ikonu sarmaladigi
            icin panelin icindeki sag tiklamalar buraya hic ulasmiyor. */}
        <span onContextMenu={handleBellContextMenu} className="notif-bell-context-wrapper">
        {renderTrigger ? (
          <span ref={triggerWrapperRef} tabIndex={-1} className="notif-bell-trigger-wrapper">
          {renderTrigger({ unreadCount: totalUnreadCount, onClick: () => handleOpenChange(!panelOpen) })}
          </span>
        ) : (
          <Badge
            count={showCount ? totalUnreadCount : undefined}
            dot={!showCount && hasNotifications}
            color={badge?.color}
            size={badge?.size ?? 'small'}
          >
            <span
              ref={triggerWrapperRef}
              className={['notif-bell-trigger', className].filter(Boolean).join(' ')}
              role="button"
              tabIndex={0}
              aria-label={`Bildirimler, ${totalUnreadCount} okunmamış`}
              aria-haspopup="dialog"
              aria-expanded={panelOpen}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  handleOpenChange(!panelOpen);
                }
              }}
              style={{ '--notif-secondary-text': tokens.secondaryText, ...style } as React.CSSProperties}
            >
              {resolvedIcon}
            </span>
          </Badge>
        )}
        </span>
      </Popover>
      </Dropdown>

    </ThemeContext.Provider>
    </NotificationErrorBoundary>
  );
});