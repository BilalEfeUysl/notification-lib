import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Dropdown, Popconfirm, Spin } from 'antd';
import {
  SoundOutlined,
  EyeOutlined,
  EyeInvisibleOutlined,
  DeleteOutlined,
  CloseOutlined,
  SearchOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { RibbonOutlined } from '../icons/RibbonIcon';
import { NotificationList } from './NotificationList';
import { useNotifications } from '../hooks/useNotifications';
import {
  getMessages,
  formatSelectionCount,
  formatConfirmDeleteSelected,
  formatNotificationTime,
  resolveLanguage,
  type LanguageSetting,
  type TimeFormat,
} from '../locales/i18n';
import { useTheme, useThemeTokens } from '../context/theme';
import type { TypeStyle } from '../styles/typeStyles';
import type { Notification } from '../types';
import '../context/theme.css';
import './NotificationPanel.css';

const DEFAULT_PANEL_WIDTH_PX = 440;

export interface NotificationPanelProps {
  /** 'tr' | 'en' | 'auto' (varsayilan 'tr'). 'auto' → tarayici dili. */
  language?: LanguageSetting;
  /**
   * Toplu secim modunda kullanici "Tumunu sec" ile yuklu olan HER SEYI
   * secip onayladiginda cagirilir (tek istekte tumunu silen backend uctan
   * yararlanmak icin) - kismi secimde bunun yerine her secili id icin ayri
   * ayri hide() kullanilir.
   */
  onClearAll: () => void;
  /** Panelin genişliği (px). Varsayılan: 440. */
  width?: number;
  /** İçindeki kaydırılabilir liste alanının yüksekliği (px). Varsayılan: 420. */
  height?: number;
  typeStyles?: Record<string, Partial<TypeStyle>>;
  showUnreadIndicator?: boolean;
  timeFormat?: TimeFormat;
  showTypeIcons?: boolean;
  onNotificationClick?: (notification: Notification) => void;
  renderItem?: (notification: Notification, actions: { hide: () => void }) => ReactNode;
  /**
   * Arama SADECE o an yuklu olan bildirimlerde (yerel, aninda) mi yapilsin,
   * yoksa backend'de TUM gecmiste mi (sunucu tarafli, biraz gecikmeli)?
   * Varsayilan: false (yerel). Acmak backend'e ek istekler gonderir.
   */
  enableServerSearch?: boolean;
}

export function NotificationPanel({
  language: languageSetting = 'tr',
  onClearAll,
  width = DEFAULT_PANEL_WIDTH_PX,
  height,
  typeStyles,
  showUnreadIndicator = true,
  timeFormat = 'full',
  showTypeIcons = false,
  onNotificationClick,
  renderItem,
  enableServerSearch = false,
}: NotificationPanelProps) {
  const language = resolveLanguage(languageSetting);
  const messages = getMessages(language);
  const tokens = useThemeTokens();
  const resolvedTheme = useTheme();
  const {
    soundEnabled,
    toggleSound,
    popupsEnabled,
    togglePopups,
    connectionStatus,
    notifications,
    hide,
    fetchSaved,
    searchNotificationsRemote,
  } = useNotifications();

  // "Kayitlilar" gorunumu: ana (sayfalanan) listeden BAGIMSIZ, kendi
  // sorgusuyla cekilen ayri bir liste. Sadece ilk sayfa gosteriliyor
  // (sonsuz kaydirma yok) - kaydedilenler tipik olarak kucuk bir kume
  // oldugu icin bu simdilik yeterli, ileride genisletilebilir.
  const [savedViewOpen, setSavedViewOpen] = useState(false);
  const [savedItems, setSavedItems] = useState<Notification[]>([]);
  const [savedLoading, setSavedLoading] = useState(false);

  function enterSavedView() {
    setSelectionMode(false);
    setSelectedIds(new Set());
    exitSearch();
    exitSavedSearch();
    setSavedViewOpen(true);
    setSavedLoading(true);
    fetchSaved()
      .then((page) => setSavedItems(page.items))
      .catch(() => setSavedItems([]))
      .finally(() => setSavedLoading(false));
  }

  function exitSavedView() {
    setSavedViewOpen(false);
    setSavedItems([]);
    exitSavedSearch();
  }

  // "Kayitlilar" gorunumunun KENDI ici icin arama - ana aramadan (searchOpen)
  // BAGIMSIZ. Ayni mantik: bos sorguda TUM kayitlilar (savedItems) gorunur,
  // yazildikca (enableServerSearch'e gore yerel/sunucu) daraltilir.
  const [savedSearchOpen, setSavedSearchOpen] = useState(false);
  const [savedSearchQuery, setSavedSearchQuery] = useState('');
  const [savedServerResults, setSavedServerResults] = useState<Notification[]>([]);
  const [savedSearching, setSavedSearching] = useState(false);
  const savedSearchInputRef = useRef<HTMLInputElement>(null);

  function enterSavedSearch() {
    setSavedSearchOpen(true);
    setSavedSearchQuery('');
    setSavedServerResults([]);
  }

  function exitSavedSearch() {
    setSavedSearchOpen(false);
    setSavedSearchQuery('');
    setSavedServerResults([]);
  }

  useEffect(() => {
    if (!savedSearchOpen) return;
    savedSearchInputRef.current?.focus();
  }, [savedSearchOpen]);

  useEffect(() => {
    if (!savedSearchOpen || !enableServerSearch) return;
    const query = savedSearchQuery.trim();
    if (!query) {
      setSavedServerResults([]);
      return;
    }
    setSavedSearching(true);
    // "Nesil koruması": bu efekt yeniden calisinca (sorgu degisince) active=false
    // olur; onceki, yavas gelen istegin cevabi artik state'e YAZILMAZ - yoksa
    // eski sonuc yeni sonucun ustune binebilirdi.
    let active = true;
    const timer = setTimeout(() => {
      fetchSaved(undefined, query)
        .then((page) => {
          if (active) setSavedServerResults(page.items);
        })
        .catch(() => {
          if (active) setSavedServerResults([]);
        })
        .finally(() => {
          if (active) setSavedSearching(false);
        });
    }, 300);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [savedSearchQuery, savedSearchOpen, enableServerSearch, fetchSaved]);

  // Arama: varsayilanda (enableServerSearch=false) SADECE o an yuklu olan
  // "notifications" uzerinde YEREL bir filtre - aninda calisir, ek istek
  // gitmez. enableServerSearch=true ise bunun yerine backend'e TUM
  // gecmiste arayan bir istek atilir (300ms debounce ile, her tus
  // basisinda degil).
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [serverResults, setServerResults] = useState<Notification[]>([]);
  const [searching, setSearching] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);

  function enterSearch() {
    setSelectionMode(false);
    setSelectedIds(new Set());
    exitSavedView();
    setSearchOpen(true);
    setSearchQuery('');
    setServerResults([]);
  }

  function exitSearch() {
    setSearchOpen(false);
    setSearchQuery('');
    setServerResults([]);
  }

  useEffect(() => {
    if (!searchOpen) return;
    searchInputRef.current?.focus();
  }, [searchOpen]);

  useEffect(() => {
    if (!searchOpen || !enableServerSearch) return;
    const query = searchQuery.trim();
    if (!query) {
      setServerResults([]);
      return;
    }
    setSearching(true);
    // "Nesil koruması" - bkz. yukaridaki kayitlilar-ici arama efekti.
    let active = true;
    const timer = setTimeout(() => {
      searchNotificationsRemote(query)
        .then((page) => {
          if (active) setServerResults(page.items);
        })
        .catch(() => {
          if (active) setServerResults([]);
        })
        .finally(() => {
          if (active) setSearching(false);
        });
    }, 300);
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [searchQuery, searchOpen, enableServerSearch, searchNotificationsRemote]);

  function matchesLocally(notification: Notification, query: string): boolean {
    const needle = query.toLowerCase();
    return (
      notification.classification.toLowerCase().includes(needle) ||
      notification.message.toLowerCase().includes(needle) ||
      (notification.classificationEn?.toLowerCase().includes(needle) ?? false) ||
      (notification.messageEn?.toLowerCase().includes(needle) ?? false) ||
      notification.type.toLowerCase().includes(needle) ||
      (notification.sourceDeviceId?.toLowerCase().includes(needle) ?? false) ||
      formatNotificationTime(notification.createdAt, language, 'full').toLowerCase().includes(needle)
    );
  }

  const trimmedQuery = searchQuery.trim();
  const searchResults = !trimmedQuery
    ? []
    : enableServerSearch
      ? serverResults
      : notifications.filter((n) => matchesLocally(n, trimmedQuery));

  const trimmedSavedQuery = savedSearchQuery.trim();
  const savedSearchResults = !trimmedSavedQuery
    ? []
    : enableServerSearch
      ? savedServerResults
      : savedItems.filter((n) => matchesLocally(n, trimmedSavedQuery));

  // "Tumunu temizle" artik DOGRUDAN silmiyor - once secim modunu acar,
  // kullanici hangi bildirimleri silecegini isaretleyip onaylar.
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  function enterSelectionMode() {
    exitSavedView();
    exitSearch();
    setSelectionMode(true);
    setSelectedIds(new Set());
  }

  function exitSelectionMode() {
    setSelectionMode(false);
    setSelectedIds(new Set());
  }

  function toggleSelect(id: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  // "Tumunu sec" o an YUKLU olan bildirimler uzerinden calisir (sayfalama
  // nedeniyle henuz cekilmemis eski bildirimler bu kapsamda degildir).
  const allLoadedSelected = notifications.length > 0 && selectedIds.size === notifications.length;

  function toggleSelectAll() {
    setSelectedIds(allLoadedSelected ? new Set() : new Set(notifications.map((n) => n.id)));
  }

  // Yuklu olan HERSEY secildiyse, backend'de tek istekte tumunu silen
  // onClearAll (hideAll) kullaniliyor; kismi secimde her biri icin ayri
  // ayri hide() cagriliyor.
  async function handleConfirmDeleteSelected() {
    if (allLoadedSelected) {
      await onClearAll();
    } else {
      await Promise.all([...selectedIds].map((id) => hide(id)));
    }
    exitSelectionMode();
  }

  // Sag tik menusunu ELLE kontrol ediyoruz - antd'nin trigger={['contextMenu']}
  // otomatik algilamasi, panel bir Popover'in icinde oldugu icin guvenilir
  // calismiyordu. Kendi onContextMenu'muzde preventDefault() ile tarayicinin
  // varsayilan menusunu engelleyip, Dropdown'i "open" state'i ile aciyoruz.
  // BILEREK sadece ikonlarin oldugu alana (.notif-panel-actions) baglaniyor,
  // TUM panele degil - listenin herhangi bir yerine sag tiklamak artik bu
  // menuyu ACMIYOR, sadece ikonlarin uzerine sag tiklamak aciyor.
  const [contextMenuOpen, setContextMenuOpen] = useState(false);

  function handleContextMenu(e: React.MouseEvent) {
    e.preventDefault();
    setContextMenuOpen(true);
  }

  useEffect(() => {
    if (!contextMenuOpen) return;
    function handleDocumentClick() {
      setContextMenuOpen(false);
    }
    document.addEventListener('click', handleDocumentClick);
    return () => document.removeEventListener('click', handleDocumentClick);
  }, [contextMenuOpen]);

  function handleIconKeyDown(e: React.KeyboardEvent, action: () => void) {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      action();
    }
  }

  // NotificationList'e overrideNotifications ile gecirilen listeler
  // (savedItems/savedServerResults, serverResults) context'in ANA
  // `notifications` state'inden BAGIMSIZ yerel state'ler - context'teki
  // hide()/toggleSaved() bunlari kendiliginden guncellemez. Bu yuzden
  // NotificationList'in onAfterDelete/onAfterToggleSave callback'leriyle
  // bu yerel state'lerden de ilgili id'yi cikarmak gerekiyor, yoksa
  // silinen/kaydi kaldirilan bildirim "kayitlilar"/arama sonucunda
  // gorunmeye devam eder.
  function removeFromSavedLocalState(id: string) {
    setSavedItems((prev) => prev.filter((n) => n.id !== id));
    setSavedServerResults((prev) => prev.filter((n) => n.id !== id));
  }

  function removeFromSearchLocalState(id: string) {
    setServerResults((prev) => prev.filter((n) => n.id !== id));
  }

  const contextMenuItems = [
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

  return (
    <Dropdown
      menu={{ items: contextMenuItems }}
      open={contextMenuOpen}
      onOpenChange={setContextMenuOpen}
      trigger={[]}
      overlayClassName={resolvedTheme === 'dark' ? 'notif-theme-dark' : 'notif-theme-light'}
    >
      <div
        className="notif-panel"
        style={
          {
            '--notif-panel-width': `min(${width}px, calc(100vw - 24px))`,
            '--notif-panel-bg': tokens.panelBg,
          } as React.CSSProperties
        }
      >
        {selectionMode ? (
          <div
            className="notif-panel-header notif-panel-header--selection"
            style={{ '--notif-border': tokens.border } as React.CSSProperties}
          >
            <span
              className="notif-panel-selection-count"
              style={{ '--notif-secondary-text': tokens.secondaryText } as React.CSSProperties}
            >
              {formatSelectionCount(language, selectedIds.size)}
            </span>
            <div className="notif-panel-actions">
              <span
                role="button"
                tabIndex={0}
                onClick={toggleSelectAll}
                onKeyDown={(e) => handleIconKeyDown(e, toggleSelectAll)}
                className="notif-panel-action-link"
                style={{ '--notif-secondary-text': tokens.secondaryText } as React.CSSProperties}
              >
                {allLoadedSelected ? messages.deselectAll : messages.selectAll}
              </span>
              <Popconfirm
                title={formatConfirmDeleteSelected(language, selectedIds.size)}
                okText={messages.confirmYes}
                cancelText={messages.confirmNo}
                icon={null}
                overlayClassName={resolvedTheme === 'dark' ? 'notif-confirm-dark' : 'notif-confirm-light'}
                onConfirm={handleConfirmDeleteSelected}
                disabled={selectedIds.size === 0}
              >
                <span
                  role="button"
                  aria-label={messages.deleteSelected}
                  tabIndex={0}
                  className={`notif-panel-action-icon ${selectedIds.size === 0 ? 'notif-panel-action-icon--disabled' : ''}`}
                  style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
                >
                  <DeleteOutlined />
                </span>
              </Popconfirm>
              <span
                role="button"
                aria-label={messages.cancelSelection}
                tabIndex={0}
                onClick={exitSelectionMode}
                onKeyDown={(e) => handleIconKeyDown(e, exitSelectionMode)}
                className="notif-panel-action-icon"
                style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
              >
                <CloseOutlined />
              </span>
            </div>
          </div>
        ) : savedViewOpen && savedSearchOpen ? (
          <div
            className="notif-panel-header notif-panel-header--search"
            style={
              {
                '--notif-border': tokens.border,
                '--notif-faint-text': tokens.faintText,
              } as React.CSSProperties
            }
          >
            <SearchOutlined className="notif-panel-search-icon" />
            <input
              ref={savedSearchInputRef}
              type="text"
              value={savedSearchQuery}
              onChange={(e) => setSavedSearchQuery(e.target.value)}
              placeholder={messages.searchPlaceholder}
              className="notif-panel-search-input"
              style={
                {
                  '--notif-secondary-text': tokens.secondaryText,
                  '--notif-faint-text': tokens.faintText,
                } as React.CSSProperties
              }
            />
            {savedSearching && <Spin size="small" />}
            <span
              role="button"
              tabIndex={0}
              aria-label={messages.closeSearch}
              onClick={exitSavedSearch}
              onKeyDown={(e) => handleIconKeyDown(e, exitSavedSearch)}
              className="notif-panel-action-icon"
              style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
            >
              <CloseOutlined />
            </span>
          </div>
        ) : savedViewOpen ? (
          <div className="notif-panel-header" style={{ '--notif-border': tokens.border } as React.CSSProperties}>
            <div className="notif-panel-header-left">
              <span
                role="button"
                tabIndex={0}
                aria-label={messages.backToAll}
                onClick={exitSavedView}
                onKeyDown={(e) => handleIconKeyDown(e, exitSavedView)}
                className="notif-panel-action-icon"
                style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
              >
                <ArrowLeftOutlined />
              </span>
              <span className="notif-panel-title" style={{ '--notif-secondary-text': tokens.secondaryText } as React.CSSProperties}>
                {messages.savedPanelTitle}
              </span>
            </div>
            <div className="notif-panel-actions">
              <span
                role="button"
                aria-label={messages.search}
                tabIndex={0}
                onClick={enterSavedSearch}
                onKeyDown={(e) => handleIconKeyDown(e, enterSavedSearch)}
                className="notif-panel-action-icon"
                style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
              >
                <SearchOutlined />
              </span>
            </div>
          </div>
        ) : searchOpen ? (
          <div
            className="notif-panel-header notif-panel-header--search"
            style={
              {
                '--notif-border': tokens.border,
                '--notif-faint-text': tokens.faintText,
              } as React.CSSProperties
            }
          >
            <SearchOutlined className="notif-panel-search-icon" />
            <input
              ref={searchInputRef}
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder={messages.searchPlaceholder}
              className="notif-panel-search-input"
              style={
                {
                  '--notif-secondary-text': tokens.secondaryText,
                  '--notif-faint-text': tokens.faintText,
                } as React.CSSProperties
              }
            />
            {searching && <Spin size="small" />}
            <span
              role="button"
              tabIndex={0}
              aria-label={messages.closeSearch}
              onClick={exitSearch}
              onKeyDown={(e) => handleIconKeyDown(e, exitSearch)}
              className="notif-panel-action-icon"
              style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
            >
              <CloseOutlined />
            </span>
          </div>
        ) : (
          <div className="notif-panel-header" style={{ '--notif-border': tokens.border } as React.CSSProperties}>
            <span className="notif-panel-title" style={{ '--notif-secondary-text': tokens.secondaryText } as React.CSSProperties}>
              {messages.panelTitle}
            </span>
            <div className="notif-panel-actions" onContextMenu={handleContextMenu}>
              <span
                role="button"
                aria-label={messages.search}
                tabIndex={0}
                onClick={enterSearch}
                onKeyDown={(e) => handleIconKeyDown(e, enterSearch)}
                className="notif-panel-action-icon"
                style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
              >
                <SearchOutlined />
              </span>
              <span
                role="button"
                aria-label={messages.showSaved}
                tabIndex={0}
                onClick={enterSavedView}
                onKeyDown={(e) => handleIconKeyDown(e, enterSavedView)}
                className="notif-panel-action-icon"
                style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
              >
                <RibbonOutlined />
              </span>
              <span
                role="button"
                aria-label={soundEnabled ? messages.muteSound : messages.unmuteSound}
                tabIndex={0}
                onClick={toggleSound}
                onKeyDown={(e) => handleIconKeyDown(e, toggleSound)}
                className={`notif-panel-action-icon ${!soundEnabled ? 'notif-icon-strike' : ''}`}
                style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
              >
                <SoundOutlined />
              </span>
              <span
                role="button"
                aria-label={popupsEnabled ? messages.hidePopups : messages.showPopups}
                tabIndex={0}
                onClick={togglePopups}
                onKeyDown={(e) => handleIconKeyDown(e, togglePopups)}
                className="notif-panel-action-icon"
                style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
              >
                {popupsEnabled ? <EyeOutlined /> : <EyeInvisibleOutlined />}
              </span>
              <span
                role="button"
                aria-label={messages.clearAll}
                tabIndex={0}
                onClick={enterSelectionMode}
                onKeyDown={(e) => handleIconKeyDown(e, enterSelectionMode)}
                className="notif-panel-action-icon"
                style={{ '--notif-faint-text': tokens.faintText } as React.CSSProperties}
              >
                <DeleteOutlined />
              </span>
            </div>
          </div>
        )}
        {connectionStatus === 'disconnected' && (
          <div
            role="status"
            className="notif-panel-connection-lost"
            style={
              {
                '--notif-border': tokens.border,
                '--notif-warning-text': tokens.warningText,
                '--notif-warning-bg': tokens.warningBg,
              } as React.CSSProperties
            }
          >
            {messages.connectionLost}
          </div>
        )}
        {savedViewOpen && savedLoading ? (
          <div className="notif-list-loader">
            <Spin size="small" />
          </div>
        ) : (
          <NotificationList
            language={language}
            height={height}
            typeStyles={typeStyles}
            showUnreadIndicator={showUnreadIndicator}
            timeFormat={timeFormat}
            showTypeIcons={showTypeIcons}
            onNotificationClick={onNotificationClick}
            renderItem={renderItem}
            selectionMode={selectionMode}
            selectedIds={selectedIds}
            onToggleSelect={toggleSelect}
            // Arama/kaydedilenler-arama BOS sorguda ozel bir filtre
            // UYGULAMIYOR - "hicbir sey yazilmamissa bile TUM bildirimler
            // gorunsun" istendigi icin, o durumda normal listeye (ana veya
            // kayitlilar) geri dusuluyor.
            overrideNotifications={
              savedViewOpen
                ? savedSearchOpen && trimmedSavedQuery
                  ? savedSearchResults
                  : savedItems
                : searchOpen && trimmedQuery
                  ? searchResults
                  : undefined
            }
            emptyMessage={
              savedViewOpen
                ? savedSearchOpen && trimmedSavedQuery
                  ? messages.emptySearch
                  : messages.emptySaved
                : searchOpen && trimmedQuery
                  ? messages.emptySearch
                  : undefined
            }
            onAfterToggleSave={savedViewOpen ? removeFromSavedLocalState : undefined}
            onAfterDelete={
              savedViewOpen ? removeFromSavedLocalState : searchOpen ? removeFromSearchLocalState : undefined
            }
          />
        )}
      </div>
    </Dropdown>
  );
}