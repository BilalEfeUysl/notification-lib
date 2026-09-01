// Ekranin kosesinde beliren popup (toast) yigini.
// - groupThreshold veya azsa hepsi alt alta acik durur.
// - fazlaysa tek yigin halinde toplanir, uzerine gelince acilir.
// - renderPopupCard verilirse kartin gorunumu tamamen cagirana birakilir.

import { useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { motion, AnimatePresence, useReducedMotion } from 'framer-motion';
import { CloseOutlined } from '@ant-design/icons';
import { usePopupQueue, type PopupDismissReason, type PopupQueueItem } from '../hooks/usePopupQueue';
import { useNotifications } from '../hooks/useNotifications';
import { getTypeStyle, type TypeStyle } from '../styles/typeStyles';
import { useThemeTokens } from '../context/theme';
import { ThemeContext, useResolvedTheme, type ThemeName } from '../context/theme';
import {
  formatNotificationTime,
  resolveLanguage,
  resolveNotificationText,
  type Language,
  type LanguageSetting,
  type TimeFormat,
} from '../locales/i18n';
import type { Notification } from '../types';
import '../context/theme.css';
import './PopupStack.css';

const DEFAULT_POPUP_WIDTH_PX = 340;
const DEFAULT_GROUP_THRESHOLD = 3;
const COLLAPSED_PEEK_COUNT = 3;
const COLLAPSED_OFFSET_PX = 10;
const APPROX_CARD_HEIGHT_PX = 96;
const COLLAPSE_DELAY_MS = 200;
const DEFAULT_TOP_OFFSET_PX = 24;
const DEFAULT_SIDE_OFFSET_PX = 24;

/**
 * Yiginin ekranin hangi kosesine yaslanacagi. Varsayilan 'top-right'
 * (eski davranis - degistirilmezse hicbir sey degismez).
 */
export type PopupStackPlacement = 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left';

export interface PopupStackProps {
  /** Her popup kartının genişliği (px). Varsayılan: 340. */
  width?: number;
  groupThreshold?: number;
  autoDismissMs?: number | null;
  /**
   * Acilirken (hover) yigin icinde en fazla kac kart render edilsin.
   * Belirtilmezse TUMU render edilir - ekrana sigmayanlar otomatik olarak
   * tekerlekle kaydirilarak gorulebilir (bkz. topOffset).
   */
  maxVisible?: number;
  /**
   * Yiginin ekranin hangi kosesine yaslanacagi.
   * 'top-right' (varsayilan) | 'top-left' | 'bottom-right' | 'bottom-left'.
   * <p>
   * Alt kosreler secildiginde yigin YUKARI dogru buyur ve toplanmis (peek)
   * gorunumdeki kartlar da yukari dogru dizilir - yani derinlik hissi her
   * zaman ekranin ICINE dogru olur, disina tasmaz.
   */
  placement?: PopupStackPlacement;
  /**
   * Yiginin secilen kosenin DIKEY kenarina uzakligi (px). 'top-*' icin
   * ustten, 'bottom-*' icin alttan olculur. Sabit bir navbar varsa onun
   * yuksekligine gore ayarla. Bu deger ayni zamanda yiginin en fazla ne
   * kadar yer kaplayacagini (viewport'un geri kalanini) belirler - asani
   * tekerlekle kaydirilir. Varsayılan: 24.
   */
  offsetY?: number;
  /**
   * Yiginin secilen kosenin YATAY kenarina uzakligi (px). '*-right' icin
   * sagdan, '*-left' icin soldan olculur. Varsayılan: 24.
   */
  offsetX?: number;
  /**
   * @deprecated `offsetY` kullan - ayni islevi gorur. Geriye donuk uyumluluk
   * icin duruyor; ikisi de verilirse `offsetY` kazanir.
   */
  topOffset?: number;
  /** Tip bazli renk paleti override'i. Belirtilmeyen tipler/alanlar varsayilaninda kalir. */
  typeStyles?: Record<string, Partial<TypeStyle>>;
  /**
   * 'tr' | 'en' | 'auto'. VERILMEZSE NotificationProvider'a verilen dil
   * miras alinir (onun da varsayilani 'tr'). 'auto' → tarayici dili.
   */
  language?: LanguageSetting;
  /** Kart üzerindeki zaman damgası formatı. Varsayılan: 'full' ("26.08.2026 14:32"). */
  timeFormat?: TimeFormat;
  /** Popup yığınının z-index'i. Kullanan uygulamanın kendi overlay'leriyle çakışırsa ayarla. Varsayılan: 1000. */
  zIndex?: number;
  onNotificationClick?: (notification: Notification) => void;
  onPopupDismiss?: (notification: Notification, reason: PopupDismissReason) => void;
  /**
   * Verilirse, popup kartinin GORUNUMU tamamen bu fonksiyona birakilir.
   * close() cagirinca kart kapanir (kullanici tarafindan kapatilmis sayilir).
   */
  renderPopupCard?: (notification: Notification, close: () => void) => ReactNode;
  /**
   * "light" | "dark" | "auto". VERILMEZSE en yakin ThemeContext'ten okunur -
   * yani NotificationBell icinde render edildiginde zil'in temasini aynen
   * miras alir (eski davranis birebir korunur).
   * <p>
   * PopupStack'i zil'den BAGIMSIZ, tek basina render ediyorsan bunu vermen
   * gerekir: o durumda ustte hicbir ThemeContext.Provider olmadigi icin
   * context varsayilani ('light') gecerli olur ve popup, sayfan koyu tema
   * olsa bile HER ZAMAN acik temada cizilir.
   */
  theme?: ThemeName;
  /**
   * Popup yigininin DOM'da hangi elemanin altina baglanacagi.
   * Varsayilan: {@code document.body}.
   * <p>
   * Neden portal: yigin {@code position: fixed}. CSS'te fixed bir eleman,
   * atalarindan birinde {@code transform}, {@code filter}, {@code backdrop-filter}
   * veya {@code will-change} varsa viewport'a DEGIL o ataya gore konumlanir
   * (CSS "containing block" kurali). Sabit/bulanik bir navbar'in icine
   * yerlestirilen zil bu tuzagi cok kolay kuruyordu - popup navbar'in
   * icine hapsolup yanlis yerde ciziliyordu. document.body'ye portal
   * atmak bunu kokten cozer.
   * <p>
   * {@code null} verilirse portal KULLANILMAZ, bilesen bulundugu yerde
   * (inline) render edilir - SSR ya da ozel bir render hedefi gereken
   * durumlar icin kacis kapisi.
   */
  container?: HTMLElement | null;
}

export function PopupStack({
  width = DEFAULT_POPUP_WIDTH_PX,
  groupThreshold = DEFAULT_GROUP_THRESHOLD,
  autoDismissMs,
  maxVisible,
  language: languageSetting,
  timeFormat = 'full',
  placement = 'top-right',
  offsetY,
  offsetX = DEFAULT_SIDE_OFFSET_PX,
  topOffset,
  zIndex,
  typeStyles,
  onNotificationClick,
  onPopupDismiss,
  renderPopupCard,
  theme,
  container,
}: PopupStackProps) {
  // language prop'u verilmemisse Provider'in dilini miras al (theme ile ayni desen).
  const { language: providerLanguage } = useNotifications();
  const language = resolveLanguage(languageSetting ?? providerLanguage);
  const { items, dismiss, pauseAutoDismiss, resumeAutoDismiss } = usePopupQueue({
    autoDismissMs,
    onDismiss: onPopupDismiss,
  });

  // Iki kaynak da KOSULSUZ okunuyor (hook kurallari): theme prop'u verilmisse
  // onu cozup kullaniyoruz, verilmemisse ustteki ThemeContext'i miras aliyoruz.
  const inheritedTheme = useContext(ThemeContext);
  const ownTheme = useResolvedTheme(theme ?? 'auto');
  const effectiveTheme = theme === undefined ? inheritedTheme : ownTheme;

  // container hic verilmediyse varsayilan document.body; null verildiyse
  // portal kullanilmaz. SSR'da document olmadigi icin inline'a duseriz.
  const portalTarget =
    container === null ? null : (container ?? (typeof document !== 'undefined' ? document.body : null));

  // offsetY yeni ad, topOffset eski (deprecated) ad. Ikisi de verilirse
  // yeni olan kazanir; hicbiri yoksa varsayilan.
  const effectiveOffsetY = offsetY ?? topOffset ?? DEFAULT_TOP_OFFSET_PX;

  if (items.length === 0) return null;

  // Yigin artik kaydirilabilir (bkz. PopupStackInner) - ekrana sigan kart
  // sayisini hesaplayip fazlasini gizlemek yerine, ekrana sigmayan kartlar
  // tekerlekle kaydirilarak gorulebiliyor. maxVisible SADECE kullanicinin
  // (bilinerek) bir ust sinir koymak istedigi durumlar icin - vermezse
  // tum bildirimler (kaydirarak erisilebilir sekilde) render edilir.
  const effectiveMax = maxVisible ?? items.length;
  const shouldGroup = items.length > groupThreshold;

  // ThemeContext.Provider SART: PopupCard renklerini useContext(ThemeContext)
  // / useThemeTokens() ile okuyor. Tek basina render edilen bir PopupStack
  // ustunde hicbir Provider olmadigi icin, bu sarmalayici olmadan kartlar
  // context varsayilanina ('light') duserdi.
  const content = (
    <ThemeContext.Provider value={effectiveTheme}>
      <PopupStackInner
        items={items}
        dismiss={dismiss}
        pauseAutoDismiss={pauseAutoDismiss}
        resumeAutoDismiss={resumeAutoDismiss}
        shouldGroup={shouldGroup}
        maxVisible={effectiveMax}
        width={width}
        placement={placement}
        offsetY={effectiveOffsetY}
        offsetX={offsetX}
        zIndex={zIndex}
        typeStyles={typeStyles}
        language={language}
        timeFormat={timeFormat}
        onNotificationClick={onNotificationClick}
        renderPopupCard={renderPopupCard}
      />
    </ThemeContext.Provider>
  );

  return portalTarget ? createPortal(content, portalTarget) : content;
}

interface InnerProps {
  items: PopupQueueItem[];
  dismiss: (key: string, reason?: PopupDismissReason) => void;
  pauseAutoDismiss: () => void;
  resumeAutoDismiss: () => void;
  shouldGroup: boolean;
  maxVisible: number;
  width: number;
  placement: PopupStackPlacement;
  offsetY: number;
  offsetX: number;
  zIndex?: number;
  typeStyles?: Record<string, Partial<TypeStyle>>;
  language: Language;
  timeFormat: TimeFormat;
  onNotificationClick?: (notification: Notification) => void;
  renderPopupCard?: (notification: Notification, close: () => void) => ReactNode;
}

function PopupStackInner({
  items,
  dismiss,
  pauseAutoDismiss,
  resumeAutoDismiss,
  shouldGroup,
  maxVisible,
  width,
  placement,
  offsetY,
  offsetX,
  zIndex,
  typeStyles,
  language,
  timeFormat,
  onNotificationClick,
  renderPopupCard,
}: InnerProps) {
  const [hovering, setHovering] = useState(false);
  const collapseTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const hoverZoneRef = useRef<HTMLDivElement>(null);
  // Ekran okuyucular icin: her yeni popup geldiginde bu metni guncelliyoruz.
  // ONEMLI: bu SABIT, hep DOM'da duran bir bolge - yeni popup kartlarinin
  // kendisi her seferinde yeniden olusturuldugu icin (AnimatePresence),
  // onlarin uzerindeki role="status" bazi ekran okuyucularda GUVENILIR
  // sekilde seslendirilmiyor. Sabit bir bolgenin METNINI degistirmek,
  // ekran okuyucularin "canli bolge" izleme mekanizmasiyla dogru calisiyor.
  const [announcement, setAnnouncement] = useState('');
  const latestKeyRef = useRef<string | null>(null);

  // Bilesen, bekleyen collapse zamanlayicisi varken kaldirilirsa onu temizle -
  // yoksa zamanlayici ates alip kaldirilmis bilesende setState cagirir.
  useEffect(() => {
    return () => {
      if (collapseTimerRef.current) clearTimeout(collapseTimerRef.current);
    };
  }, []);

  useEffect(() => {
    const newest = items[0];
    if (!newest || newest.key === latestKeyRef.current) return;
    latestKeyRef.current = newest.key;
    setAnnouncement(`${newest.notification.classification}: ${newest.notification.message}`);
  }, [items]);

  const expanded = !shouldGroup || hovering;

  // Genisken kullanici asagi kaydirmis olabilir. Mouse cekilip yigin
  // toplaninca (expanded -> false) icerik aniden kucuk "peek" boyutuna
  // donuyor, ama kaydirma konumu (scrollTop) HALA eski (asagidaki) yerde
  // kaliyor - bu da kucuk icerigin gorunmez kalmasina (bos gorunmesine)
  // yol aciyordu, tarayici scrollTop'u kendiliginden duzeltene kadar bir
  // anlik "kayboluyor" hissi veriyordu. Toplanirken kaydirmayi en tepeye
  // (en yeni bildirime) sifirlayarak bunu onluyoruz.
  useEffect(() => {
    if (!expanded) {
      hoverZoneRef.current?.scrollTo({ top: 0 });
    }
  }, [expanded]);

  function handleMouseEnter() {
    if (collapseTimerRef.current) {
      clearTimeout(collapseTimerRef.current);
      collapseTimerRef.current = null;
    }
    setHovering(true);
    pauseAutoDismiss();
  }

  function handleMouseLeave() {
    collapseTimerRef.current = setTimeout(() => {
      setHovering(false);
      resumeAutoDismiss();
    }, COLLAPSE_DELAY_MS);
  }

  const renderCap = expanded ? maxVisible : COLLAPSED_PEEK_COUNT;
  const renderedItems = items.slice(0, renderCap);

  // Bu tahmini yukseklik SADECE collapsed (yiginin ustune gelinmemis,
  // ust uste binen "peek" gorunumu) durumda kullaniliyor - o kartlar
  // position:absolute oldugu icin normal akista yukseklik URETMIYORLAR,
  // yani asagidaki kapsayicinin (dolayisiyla fare algilama alaninin) bir
  // yukseklige ihtiyaci var. Expanded (gercekten acik/gorunen) durumda
  // kartlar normal akista (position:relative) - kapsayiciya hicbir yukseklik
  // vermiyoruz (undefined), gercek toplam yukseklik dogal akistan geliyor.
  // BILEREK duz bir <div> (motion.div/layout DEGIL): framer-motion'in
  // layout gecisleri boyut degisimini gecici olarak transform/kilitli
  // boyutla taklit ediyor, bu da asagidaki .notif-popup-hover-zone'un
  // overflow-y:auto ile gercek icerik boyutunu okuyup kaydirmasini
  // guvenilmez hale getiriyordu (denendi, kaydirma tamamen bozuldu). Duz
  // div ile kapsayicinin yuksekligi HER ZAMAN gercek icerige birebir esit -
  // kaydirma bu sayede sadece standart CSS davranisiyla dogru calisiyor.
  // Kart bazli animasyonlar (asagidaki PopupCard'larin kendi `layout` +
  // animateState'i) zaten ayri calisiyor, gecisi hala canlandiriyor.
  const collapsedHeight = COLLAPSED_OFFSET_PX * (renderedItems.length - 1) + APPROX_CARD_HEIGHT_PX;

  // Fare algilama alaninin ust siniri artik ICERIGE degil, SABIT bir
  // viewport hesabina (topOffset) dayaniyor - yigin kac bildirim
  // tutsa da kutu ASLA boyut degistirmiyor (tarayicinin "fare kutunun
  // disina/icine gecti" seklinde sahte mouseenter/mouseleave uretmesini
  // onlemek icin onemliydi, ayni garanti burada da korunuyor). Bu sinirdan
  // tasan kartlar artik gizlenmek yerine tekerlekle kaydirilarak gorulebiliyor
  // (asagidaki .notif-popup-hover-zone'daki overflow-y: auto).
  const maxHeight = `calc(100vh - ${offsetY * 2}px)`;

  const isBottom = placement === 'bottom-right' || placement === 'bottom-left';
  const isLeft = placement === 'top-left' || placement === 'bottom-left';
  // Alt kosreler: toplanmis (peek) kartlar YUKARI dogru dizilsin ki derinlik
  // ekranin icine dogru olsun, disina tasmasin. Ust kosreler: asagi (eski).
  const stackDirection = isBottom ? -1 : 1;

  // Sadece secilen kosenin iki kenari set edilir; digerleri tanimsiz kalip
  // CSS'te `auto`'ya duser (bkz. PopupStack.css). Once duz bir sozluk olarak
  // kuruluyor: kosullu spread'lerden olusan birlesim tipi dogrudan
  // CSSProperties'e cevrilemiyor.
  const hoverZoneVars: Record<string, string | number> = {
    [isBottom ? '--notif-popup-bottom' : '--notif-popup-top']: `${offsetY}px`,
    [isLeft ? '--notif-popup-left' : '--notif-popup-right']: `${offsetX}px`,
    '--notif-popup-width': `${width}px`,
    '--notif-popup-max-height': maxHeight,
  };
  if (zIndex !== undefined) {
    hoverZoneVars['--notif-popup-z-index'] = zIndex;
  }
  const hoverZoneStyle = hoverZoneVars as React.CSSProperties;

  return (
    <>
      <div aria-live="polite" aria-atomic="true" className="notif-popup-live-region">
        {announcement}
      </div>
      <div
        ref={hoverZoneRef}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        className="notif-popup-hover-zone"
        style={hoverZoneStyle}
      >
        {/* collapsed durumda kartlar position:absolute - akista yukseklik
            uretmedikleri icin kapsayiciya tahmini bir yukseklik veriyoruz
            (fare algilama alani icin). expanded'da 'auto' = hicbir sey vermemekle
            ayni, gercek yukseklik dogal akistan gelir (bkz. bolum 9.3). */}
        <div
          className="notif-popup-stack-flow"
          style={
            {
              '--notif-popup-stack-height': expanded ? 'auto' : `${collapsedHeight}px`,
            } as React.CSSProperties
          }
        >
          <AnimatePresence initial={false}>
            {renderedItems.map((item, index) => (
              <PopupCard
                key={item.key}
                item={item}
                index={index}
                collapsed={!expanded}
                stackDirection={stackDirection}
                typeStyles={typeStyles}
                language={language}
                timeFormat={timeFormat}
                onClose={() => dismiss(item.key, 'user')}
                onClick={onNotificationClick ? () => onNotificationClick(item.notification) : undefined}
                renderPopupCard={renderPopupCard}
              />
            ))}
          </AnimatePresence>
        </div>
      </div>
    </>
  );
}

interface PopupCardProps {
  /** +1 = yigin asagi dogru (ust kosreler), -1 = yukari dogru (alt kosreler). */
  stackDirection: number;
  item: PopupQueueItem;
  index: number;
  collapsed: boolean;
  typeStyles?: Record<string, Partial<TypeStyle>>;
  language: Language;
  timeFormat: TimeFormat;
  onClose: () => void;
  onClick?: () => void;
  renderPopupCard?: (notification: Notification, close: () => void) => ReactNode;
}

function PopupCard({ item, index, collapsed, stackDirection, typeStyles, language, timeFormat, onClose, onClick, renderPopupCard }: PopupCardProps) {
  const theme = useContext(ThemeContext);
  const tokens = useThemeTokens();
  const palette = getTypeStyle(item.notification.type, theme, typeStyles);
  const { classification, message } = resolveNotificationText(item.notification, language);

  // "Hareketi azalt" ayari aciksa framer-motion animasyonlarini da kapat -
  // CSS !important (theme.css) framer-motion'in JS/WAAPI animasyonlarini
  // durdurmadigi icin burada ayrica ele aliniyor.
  const reduceMotion = useReducedMotion();
  const useLayout = !reduceMotion;
  const transition = reduceMotion
    ? { duration: 0 }
    : { duration: 0.28, ease: 'easeInOut' as const };
  const initial = reduceMotion ? false : { opacity: 0, y: -12, scale: 0.95 };
  const exit = reduceMotion ? { opacity: 0 } : { opacity: 0, scale: 0.9 };

  // DIKKAT: collapsed (yigin/peek) durumda opacity DUSURULMUYOR - kart
  // arka plani opak olsa bile opacity < 1 vermek TUM elementi (yazisiyla
  // birlikte) saydamlastirir, bu da arkadaki kartin yazisinin gorunup
  // yazilarin ust uste binmesine yol aciyordu. Derinlik hissi SADECE
  // y-offset (asagi kaydirma) ve scale (kucultme) ile veriliyor - onler
  // kart opak oldugu icin arkadakileri zaten tam kapatiyor, sadece alt
  // kenardan bir "kirinti" (peek) sarkip gorunuyor.
  const animateState = collapsed
    ? { opacity: 1, y: index * COLLAPSED_OFFSET_PX * stackDirection, scale: 1 - index * 0.04 }
    : { opacity: 1, y: 0, scale: 1 };

  const positionClassName = `notif-popup-card-position notif-popup-card-position--${collapsed ? 'collapsed' : 'expanded'}`;
  const positionStyle = { '--notif-popup-z': 100 - index } as React.CSSProperties;

  // renderPopupCard verilmisse GORUNUMU tamamen kullaniciya birakiyoruz -
  // sadece pozisyon/animasyon sarmalayicisini (motion.div) biz koyuyoruz,
  // ICERIGI kullanicinin fonksiyonu belirliyor.
  if (renderPopupCard) {
    return (
      <motion.div
        layout={useLayout}
        initial={initial}
        animate={animateState}
        exit={exit}
        transition={transition}
        role="status"
        className={positionClassName}
        style={positionStyle}
      >
        {renderPopupCard(item.notification, onClose)}
      </motion.div>
    );
  }

  return (
    <motion.div
      layout={useLayout}
      initial={initial}
      animate={animateState}
      exit={exit}
      transition={transition}
      role="status"
      onClick={onClick}
      className={[positionClassName, 'notif-popup-card', onClick ? 'notif-popup-card--clickable' : ''].filter(Boolean).join(' ')}
      style={
        {
          ...positionStyle,
          '--notif-card-bg': palette.background,
          '--notif-border-color': palette.borderColor,
          '--notif-shadow': theme === 'dark' ? '0 4px 12px rgba(0,0,0,0.4)' : '0 4px 12px rgba(0,0,0,0.1)',
        } as React.CSSProperties
      }
    >
      <div className="notif-popup-card-header">
        <strong className="notif-popup-card-title" style={{ '--notif-title-color': palette.titleColor } as React.CSSProperties}>
          {classification}
        </strong>
        <span
          role="button"
          aria-label="Bildirimi kapat"
          tabIndex={0}
          onClick={(e) => {
            e.stopPropagation();
            onClose();
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.stopPropagation();
              onClose();
            }
          }}
          className="notif-popup-card-close"
          style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
        >
          <CloseOutlined />
        </span>
      </div>
      <div
        className="notif-popup-card-message"
        style={{ '--notif-secondary-text': tokens.secondaryText } as React.CSSProperties}
      >
        {message}
      </div>
      <div
        className="notif-popup-card-time"
        style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
      >
        {formatNotificationTime(item.notification.createdAt, language, timeFormat)}
        {item.notification.sourceDeviceId && (
          <span className="notif-popup-card-time-source"> · {item.notification.sourceDeviceId}</span>
        )}
      </div>
    </motion.div>
  );
}