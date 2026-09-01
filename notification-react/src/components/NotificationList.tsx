// Zile tiklayinca acilan tam liste. Sonsuz kaydirma: ilk sayfa Provider'da
// hazir, asagi kaydirdikca loadMore() ile eskiler eklenir.
//
// jsx-a11y: bu bilesen tasarim geregi "tiklanabilir satirlardan olusan, ok
// tuslariyla gezilen bir liste" - role="list"/"listitem" + kapsayicida
// onKeyDown (roving focus) + her satirda tabIndex={0}. Asagidaki iki kural
// bu yuzden dosya bazinda kapatildi (satirlar Enter/Space + ok tuslariyla
// tam erisilebilir).
/* eslint-disable jsx-a11y/no-noninteractive-element-interactions, jsx-a11y/no-noninteractive-tabindex */
import { useContext, useRef, useState, type CSSProperties, type KeyboardEvent, type ReactElement, type ReactNode } from 'react';
import InfiniteScroll from 'react-infinite-scroll-component';
import { Spin, Popconfirm } from 'antd';
import {
  DeleteOutlined,
  CheckOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { RibbonOutlined, RibbonFilled } from '../icons/RibbonIcon';
import { useNotifications } from '../hooks/useNotifications';
import { getTypeStyle, getKnownIconType, type TypeStyle } from '../styles/typeStyles';
import {
  formatNotificationTime,
  getMessages,
  resolveLanguage,
  resolveNotificationText,
  type Language,
  type LanguageSetting,
  type TimeFormat,
} from '../locales/i18n';
import { ThemeContext, useThemeTokens } from '../context/theme';
import type { Notification } from '../types';
import '../context/theme.css';
import './NotificationList.css';


const SCROLL_CONTAINER_ID = 'notification-list-scroll-container';
const DEFAULT_LIST_HEIGHT_PX = 420;

export interface NotificationListProps {
  /** 'tr' | 'en' | 'auto' (varsayilan 'tr'). 'auto' → tarayici dili. */
  language?: LanguageSetting;
  height?: number;
  typeStyles?: Record<string, Partial<TypeStyle>>;
  /** Okunmamış bildirimlerde köşede küçük bir nokta gösterilsin mi. Varsayılan: true. */
  showUnreadIndicator?: boolean;
  /** Zaman damgası formatı. Varsayılan: 'full' ("26.08.2026 14:32"), öğenin altında gösterilir. */
  timeFormat?: TimeFormat;
  /** success/error/warning/info tipleri için başlığın yanında küçük bir ikon gösterilsin mi. Varsayılan: false. */
  showTypeIcons?: boolean;
  onNotificationClick?: (notification: Notification) => void;
  /** Verilirse, liste satirinin GORUNUMU tamamen bu fonksiyona birakilir. */
  renderItem?: (notification: Notification, actions: { hide: () => void }) => ReactNode;
  /** Toplu silme secim modu acik mi - NotificationPanel tarafindan yonetilir. */
  selectionMode?: boolean;
  selectedIds?: Set<string>;
  onToggleSelect?: (id: string) => void;
  /**
   * Verilirse, context'ten gelen normal (sayfalanan) liste yerine BU liste
   * gosterilir - sonsuz kaydirma devre disi kalir. "Kayitlilar" gorunumu
   * gibi ozel gorunumler icin (bkz. NotificationPanel).
   */
  overrideNotifications?: Notification[];
  /** overrideNotifications ile birlikte kullanilir - bos oldugunda gosterilecek metin. Verilmezse varsayilan "hic bildirim yok" metni kullanilir. */
  emptyMessage?: string;
  /** overrideNotifications ile birlikte kullanilir - bir kaydetme/kaldirma islemi basariyla bittikten sonra cagirilir (orn. "kayitlilar" gorunumunde kaldirilan ogeyi yerel listeden de silmek icin). */
  onAfterToggleSave?: (id: string) => void;
  /**
   * overrideNotifications ile birlikte kullanilir - bir silme islemi
   * basariyla bittikten sonra cagirilir. hide() context'teki ANA listeyi
   * guncelliyor ama overrideNotifications (orn. "kayitlilar"/arama sonucu)
   * AYRI bir yerel state oldugu icin kendiliginden guncellenmiyor - silinen
   * id'yi o yerel state'ten de cikarmak icin gerekli.
   */
  onAfterDelete?: (id: string) => void;
}

export function NotificationList({
  language: languageSetting = 'tr',
  height = DEFAULT_LIST_HEIGHT_PX,
  typeStyles,
  showUnreadIndicator = true,
  timeFormat = 'full',
  showTypeIcons = false,
  onNotificationClick,
  renderItem,
  selectionMode = false,
  selectedIds,
  onToggleSelect,
  overrideNotifications,
  emptyMessage,
  onAfterToggleSave,
  onAfterDelete,
}: NotificationListProps) {
  const language = resolveLanguage(languageSetting);
  const { notifications: contextNotifications, hasMore: contextHasMore, loadMore, hide, toggleSaved } = useNotifications();
  const notifications = overrideNotifications ?? contextNotifications;
  const hasMore = overrideNotifications ? false : contextHasMore;
  const messages = getMessages(language);
  const tokens = useThemeTokens();

  // HIGH oncelikliler listenin EN USTUNDE gorunsun istendi. sort() JS'de
  // "kararli" (stable) calisir - yani ayni oncelige sahip bildirimlerin
  // kendi aralarindaki sirasi (en yeni en ustte) BOZULMUYOR, sadece HIGH
  // olanlar bir grup halinde one aliniyor.
  const sortedNotifications = [...notifications].sort((a, b) => {
    const aWeight = a.priority === 'HIGH' ? 0 : 1;
    const bWeight = b.priority === 'HIGH' ? 0 : 1;
    return aWeight - bWeight;
  });

  // Her bildirim satirinin gercek DOM elementini burada tutuyoruz -
  // ok tuslarina basildiginda "su an odakli olan hangisi, bir sonraki/
  // onceki hangisi" diye bulup focus() cagirabilmek icin.
  const itemRefs = useRef<(HTMLDivElement | null)[]>([]);

  function handleListKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return;
    const items = itemRefs.current.filter((el): el is HTMLDivElement => el !== null);
    if (items.length === 0) return;
    e.preventDefault();
    const currentIndex = items.findIndex((el) => el === document.activeElement);
    if (e.key === 'ArrowDown') {
      const next = items[currentIndex + 1] ?? items[0];
      next.focus();
    } else {
      const prev = currentIndex <= 0 ? items[items.length - 1] : items[currentIndex - 1];
      prev.focus();
    }
  }

  if (notifications.length === 0) {
    return (
      <div
        role="status"
        className="notif-list-empty"
        style={{ '--notif-muted-text': tokens.mutedText } as CSSProperties}
      >
        {emptyMessage ?? messages.emptyList}
      </div>
    );
  }

  return (
    <div
      id={SCROLL_CONTAINER_ID}
      role="list"
      onKeyDown={handleListKeyDown}
      className="notif-list-scroll"
      style={{ '--notif-list-height': `${height}px`, '--notif-faint-text': tokens.faintText } as React.CSSProperties}
    >
      <InfiniteScroll
        dataLength={notifications.length}
        next={loadMore}
        hasMore={hasMore}
        loader={
          <div className="notif-list-loader">
            <Spin size="small" />{' '}
            <span className="notif-list-loader-text" style={{ '--notif-muted-text': tokens.mutedText } as React.CSSProperties}>
              {messages.loadingMore}
            </span>
          </div>
        }
        scrollableTarget={SCROLL_CONTAINER_ID}
      >
        {sortedNotifications.map((notification, index) =>
          renderItem ? (
            <div key={notification.id} role="listitem">
              {renderItem(notification, { hide: () => hide(notification.id) })}
            </div>
          ) : (
            <NotificationItem
              key={notification.id}
              notification={notification}
              language={language}
              typeStyles={typeStyles}
              showUnreadIndicator={showUnreadIndicator}
              timeFormat={timeFormat}
              showTypeIcons={showTypeIcons}
              onDelete={() => hide(notification.id).then(() => onAfterDelete?.(notification.id))}
              onToggleSave={() => toggleSaved(notification.id).then(() => onAfterToggleSave?.(notification.id))}
              onClick={onNotificationClick ? () => onNotificationClick(notification) : undefined}
              itemRef={(el) => {
                itemRefs.current[index] = el;
              }}
              selectionMode={selectionMode}
              selected={selectedIds?.has(notification.id) ?? false}
              onToggleSelect={() => onToggleSelect?.(notification.id)}
            />
          )
        )}
      </InfiniteScroll>
    </div>
  );
}

interface NotificationItemProps {
  notification: Notification;
  language: Language;
  typeStyles?: Record<string, Partial<TypeStyle>>;
  showUnreadIndicator: boolean;
  timeFormat: TimeFormat;
  showTypeIcons: boolean;
  onDelete: () => void;
  onToggleSave: () => void;
  onClick?: () => void;
  /** Satırın gerçek DOM elementini üst bileşene (NotificationList) bildirir - ok tuşu gezinmesi için. */
  itemRef: (el: HTMLDivElement | null) => void;
  /** Toplu secim modu acikken checkbox gosterilir, tikla/Enter secimi degistirir. */
  selectionMode: boolean;
  selected: boolean;
  onToggleSelect: () => void;
}

function NotificationItem({
  notification,
  language,
  typeStyles,
  showUnreadIndicator,
  timeFormat,
  showTypeIcons,
  onDelete,
  onToggleSave,
  onClick,
  itemRef,
  selectionMode,
  selected,
  onToggleSelect,
}: NotificationItemProps) {
  const theme = useContext(ThemeContext);
  const tokens = useThemeTokens();
  const messages = getMessages(language);
  const { classification, message } = resolveNotificationText(notification, language);
  const palette = getTypeStyle(notification.type, theme, typeStyles);
  // Kutunun ici artik tipin rengiyle hafifce boyaniyor (palette.background).
  // Hover'da ayrica bir renge gecmek yerine mevcut tonu filter ile
  // koyulastirip/acarak (temaya gore) geri bildirim veriyoruz - boylece
  // hover'da renk KAYBOLMUYOR.
  const hoverFilter = theme === 'dark' ? 'brightness(1.18)' : 'brightness(0.97)';

  // Nokta bir kez uzerine gelinince KALICI olarak "gorulmus" sayilir - CSS
  // :hover'dan farkli olarak, mouse cekilse bile geri gelmez. Bu SADECE
  // gorsel bir durum, backend'e hicbir istek gitmiyor (okundu isaretlemesi
  // hala readTrigger prop'una gore ayrica calisiyor).
  const [dotDismissed, setDotDismissed] = useState(false);

  function handleRowClick() {
    if (selectionMode) {
      onToggleSelect();
      return;
    }
    onClick?.();
  }

  function handleKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (e.key !== 'Enter' && e.key !== ' ') return;
    e.preventDefault();
    if (selectionMode) {
      onToggleSelect();
    } else if (onClick) {
      onClick();
    }
  }

  return (
    <div
      ref={itemRef}
      role="listitem"
      tabIndex={0}
      onClick={handleRowClick}
      onKeyDown={handleKeyDown}
      onMouseEnter={() => setDotDismissed(true)}
      className={`notif-item ${onClick || selectionMode ? 'notif-item--clickable' : ''}`}
      style={
        {
          borderLeft: `5px solid ${palette.borderColor}`,
          '--notif-item-bg': palette.background,
          '--notif-item-hover-filter': hoverFilter,
        } as CSSProperties
      }
    >
      {selectionMode ? (
        <span
          role="checkbox"
          aria-checked={selected}
          tabIndex={0}
          onClick={(e) => {
            e.stopPropagation();
            onToggleSelect();
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              e.stopPropagation();
              onToggleSelect();
            }
          }}
          className={`notif-item-checkbox ${selected ? 'notif-item-checkbox--checked' : ''}`}
          style={
            {
              '--notif-checkbox-border': tokens.faintText,
              '--notif-checkbox-checked-bg': tokens.secondaryText,
              '--notif-checkbox-checked-icon': tokens.panelBg,
            } as CSSProperties
          }
        >
          {selected && <CheckOutlined />}
        </span>
      ) : (
        showUnreadIndicator && !notification.read && (
          <span
            aria-hidden="true"
            className={`notif-item-unread-dot ${dotDismissed ? 'notif-item-unread-dot--dismissed' : ''}`}
            style={{ '--notif-dot-color': palette.borderColor } as CSSProperties}
          />
        )
      )}
      <div className="notif-item-content">
        <div className="notif-item-header">
          {showTypeIcons && <TypeIcon type={notification.type} color={palette.titleColor} />}
          <strong
            className={`notif-item-title ${notification.read ? 'notif-item-title--read' : 'notif-item-title--unread'}`}
            style={{ '--notif-title-color': palette.titleColor } as CSSProperties}
          >
            {classification}
          </strong>
        </div>
        <div
          className="notif-item-message"
          style={{ '--notif-secondary-text': tokens.secondaryText } as CSSProperties}
        >
          {message}
        </div>
        <div
          className="notif-item-time"
          style={{ '--notif-faint-text': tokens.faintText } as CSSProperties}
        >
          {formatNotificationTime(notification.createdAt, language, timeFormat)}
          {notification.sourceDeviceId && (
            <span className="notif-item-time-source"> · {notification.sourceDeviceId}</span>
          )}
        </div>
      </div>
      {!selectionMode && (
        <div className="notif-item-actions">
          <span
            role="button"
            aria-label={notification.saved ? messages.unsaveNotification : messages.saveNotification}
            aria-pressed={notification.saved}
            tabIndex={0}
            onClick={(e) => {
              e.stopPropagation();
              onToggleSave();
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                e.stopPropagation();
                onToggleSave();
              }
            }}
            className="notif-item-save"
            style={{ '--notif-faint-text': tokens.faintText } as CSSProperties}
          >
            {notification.saved ? <RibbonFilled /> : <RibbonOutlined />}
          </span>
          <Popconfirm
            title={messages.confirmDeleteTitle}
            okText={messages.confirmYes}
            cancelText={messages.confirmNo}
            icon={null}
            overlayClassName={theme === 'dark' ? 'notif-confirm-dark' : 'notif-confirm-light'}
            onConfirm={(e) => {
              e?.stopPropagation();
              onDelete();
            }}
            onCancel={(e) => e?.stopPropagation()}
          >
            <span
              role="button"
              aria-label="Bildirimi sil"
              tabIndex={0}
              onClick={(e) => e.stopPropagation()}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') e.stopPropagation();
              }}
              className="notif-item-delete"
              style={{ '--notif-faint-text': tokens.faintText } as CSSProperties}
            >
              <DeleteOutlined />
            </span>
          </Popconfirm>
        </div>
      )}
    </div>
  );
}


const TYPE_ICONS: Record<'success' | 'error' | 'warning' | 'info', ReactElement> = {
  success: <CheckCircleOutlined />,
  error: <CloseCircleOutlined />,
  warning: <ExclamationCircleOutlined />,
  info: <InfoCircleOutlined />,
};

function TypeIcon({ type, color }: { type: string; color: string }) {
  const known = getKnownIconType(type);
  if (!known) return null;
  return (
    <span className="notif-item-type-icon" style={{ '--notif-icon-color': color } as CSSProperties}>
      {TYPE_ICONS[known]}
    </span>
  );
}