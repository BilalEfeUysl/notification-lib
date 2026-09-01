// Kutuphanenin TUM ozellestirilebilir prop'larini canli deneyebilecegin
// kontrol paneli. Bildirim GONDERMEK icin notification-example'daki
// index.html'i (http://localhost:8080) kullan - bu sayfa sadece
// NotificationBell'in gorsel/davranissal ayarlarini test etmek icin.

import { useState, type ReactNode } from 'react';
import {
  BellOutlined,
  BellFilled,
  NotificationOutlined,
  SoundOutlined,
} from '@ant-design/icons';
import {
  NotificationProvider,
  NotificationBell,
  PopupStack,
  type Notification,
  type PopupDismissReason,
  type PopupPlacement,
  type RenderTriggerProps,
  type TypeStyle,
} from '../src';


const BASE_PATH = 'http://localhost:8080/api/notifications';
const WEBSOCKET_URL = 'ws://localhost:8080/ws/notifications';

const ICON_OPTIONS: Record<string, ReactNode> = {
  'Varsayılan (durum göstergeli)': undefined,
  BellOutlined: <BellOutlined style={{ fontSize: 20 }} />,
  BellFilled: <BellFilled style={{ fontSize: 20 }} />,
  NotificationOutlined: <NotificationOutlined style={{ fontSize: 20 }} />,
  SoundOutlined: <SoundOutlined style={{ fontSize: 20 }} />,
};

const NOTIFICATION_TYPES = ['success', 'error', 'warning', 'info'] as const;

const TABS = ['Rozet', 'Panel', 'Popup', 'Dil / Okundu', 'Görünüm', 'Renkler'] as const;
type Tab = (typeof TABS)[number];

export function App() {
  const [activeTab, setActiveTab] = useState<Tab>('Rozet');
  const [userId, setUserId] = useState('ali');
  const [pageTheme, setPageTheme] = useState<'light' | 'dark'>('light');
  // --- badge ---
  const [iconName, setIconName] = useState<keyof typeof ICON_OPTIONS>('Varsayılan (durum göstergeli)');
  const [showStatusIcon, setShowStatusIcon] = useState(true);
  const [badgeColor, setBadgeColor] = useState('#ff4d4f');
  const [useBadgeColor, setUseBadgeColor] = useState(false);
  const [showCount, setShowCount] = useState(true);
  const [readTrigger, setReadTrigger] = useState<'onOpen' | 'onClick' | 'manual'>('onOpen');

  // --- panel ---
  const [panelPlacement, setPanelPlacement] = useState<PopupPlacement>('bottomRight');
  const [panelWidth, setPanelWidth] = useState(360);
  const [panelHeight, setPanelHeight] = useState(420);
  const [panelOffsetX, setPanelOffsetX] = useState(0);
  const [panelOffsetY, setPanelOffsetY] = useState(0);
  const [arrowOffsetX, setArrowOffsetX] = useState(0);
  const [arrowOffsetY, setArrowOffsetY] = useState(0);
  const [useCustomPanelBg, setUseCustomPanelBg] = useState(false);
  const [panelBg, setPanelBg] = useState('#eef4ff');
  const [useCustomArrowBg, setUseCustomArrowBg] = useState(false);
  const [arrowBg, setArrowBg] = useState('#722ed1');

  // --- popup ---
  const [popupWidth, setPopupWidth] = useState(320);
  const [groupThreshold, setGroupThreshold] = useState(3);
  const [autoDismissMs, setAutoDismissMs] = useState<number | ''>(6000);
  const [maxVisible, setMaxVisible] = useState<number | ''>('');

  // --- dil ---
  const [language, setLanguage] = useState<'tr' | 'en'>('tr');

  // --- Faz 8C: render-prop denemeleri ---
  const [useCustomTrigger, setUseCustomTrigger] = useState(false);
  const [useCustomPopupCard, setUseCustomPopupCard] = useState(false);
  const [useCustomListItem, setUseCustomListItem] = useState(false);

  // --- Faz 8C: typeStyles override denemesi ---
  const [typeOverridesEnabled, setTypeOverridesEnabled] = useState<Record<string, boolean>>({
    success: false,
    error: false,
    warning: false,
    info: false,
  });
  const [typeColors, setTypeColors] = useState<Record<string, string>>({
    success: '#52c41a',
    error: '#f5222d',
    warning: '#faad14',
    info: '#1677ff',
  });

  const typeStylesOverride: Record<string, Partial<TypeStyle>> = {};
  for (const type of NOTIFICATION_TYPES) {
    if (typeOverridesEnabled[type]) {
      typeStylesOverride[type] = { borderColor: typeColors[type] };
    }
  }

  // --- olay günlüğü ---
  const [log, setLog] = useState<string[]>([]);
  function addLog(text: string) {
    const time = new Date().toLocaleTimeString();
    setLog((prev) => [`[${time}] ${text}`, ...prev].slice(0, 50));
  }

  return (
    <NotificationProvider
      basePath={BASE_PATH}
      websocketUrl={WEBSOCKET_URL}
      identity={{ userId }}
      onError={(err) => addLog(`HATA: ${err.message}`)}
    >
      <div
        style={{
          fontFamily: '-apple-system, Segoe UI, Roboto, sans-serif',
          minHeight: '100vh',
          background: pageTheme === 'dark' ? '#141414' : '#f7f8fa',
          color: pageTheme === 'dark' ? '#e8e8e8' : '#1f1f1f',
          transition: 'background-color 0.2s ease, color 0.2s ease',
        }}
      >
        {/* Gercekci demo: zil, gercek bir uygulamanin ust nav bar'inda,
            saga ust kosede duruyor. Sayfanin theme'i degisince (asagidaki
            dugme) zilin theme'i de (theme={pageTheme}) onu takip ediyor. */}
        <nav
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 28px',
            height: 56,
            background: pageTheme === 'dark' ? '#1f1f1f' : '#fff',
            borderBottom: `1px solid ${pageTheme === 'dark' ? '#303030' : '#eaecef'}`,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 28 }}>
            <strong style={{ fontSize: 15, letterSpacing: 0.2 }}>Notification App</strong>
            <div style={{ display: 'flex', gap: 18, fontSize: 13, color: pageTheme === 'dark' ? '#a6a6a6' : '#6e7781' }}>
              <span>Panel</span>
              <span>Projeler</span>
              <span>Raporlar</span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
            <label style={{ fontSize: 12, color: pageTheme === 'dark' ? '#a6a6a6' : '#6e7781' }}>
              Kimliğim (userId):{' '}
              <input
                value={userId}
                onChange={(e) => setUserId(e.target.value)}
                style={{ width: 80, padding: '2px 6px' }}
              />
            </label>

            <button
              onClick={() => setPageTheme((t) => (t === 'dark' ? 'light' : 'dark'))}
              style={{
                border: `1px solid ${pageTheme === 'dark' ? '#434343' : '#d9d9d9'}`,
                background: 'transparent',
                color: pageTheme === 'dark' ? '#e8e8e8' : '#1f1f1f',
                borderRadius: 6,
                padding: '4px 10px',
                fontSize: 12,
                cursor: 'pointer',
              }}
            >
              {pageTheme === 'dark' ? '☀️ Açık tema' : '🌙 Koyu tema'}
            </button>

            <NotificationBell
              theme={pageTheme}
              language={language}
              icon={ICON_OPTIONS[iconName]}
              showStatusIcon={showStatusIcon}
              badge={{ showCount, color: useBadgeColor ? badgeColor : undefined }}
              readTrigger={readTrigger}
              panel={{
                placement: panelPlacement,
                width: panelWidth,
                height: panelHeight,
                offsetX: panelOffsetX,
                offsetY: panelOffsetY,
                arrowOffsetX,
                arrowOffsetY,
                background: useCustomPanelBg ? panelBg : undefined,
                arrowBackground: useCustomArrowBg ? arrowBg : undefined,
              }}
              typeStyles={Object.keys(typeStylesOverride).length > 0 ? typeStylesOverride : undefined}
              onNotificationClick={(n: Notification) =>
                addLog(`Tıklandı: ${n.classification} (metadata: ${JSON.stringify(n.metadata)})`)
              }
              renderTrigger={
                useCustomTrigger
                  ? ({ unreadCount, onClick }: RenderTriggerProps) => (
                      <button
                        onClick={onClick}
                        style={{
                          padding: '9px 18px',
                          borderRadius: 20,
                          border: 'none',
                          background: unreadCount > 0 ? '#722ed1' : '#8c8c8c',
                          color: '#fff',
                          cursor: 'pointer',
                          fontWeight: 600,
                          fontSize: 13,
                          boxShadow: '0 2px 6px rgba(0,0,0,0.15)',
                        }}
                      >
                        🔔 Bildirimler {unreadCount > 0 ? `(${unreadCount})` : ''}
                      </button>
                    )
                  : undefined
              }
              renderItem={
                useCustomListItem
                  ? (notification: Notification, actions: { hide: () => void }) => (
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          alignItems: 'center',
                          padding: '8px 10px',
                          marginBottom: 4,
                          background: notification.read ? '#f5f5f5' : '#e6f4ff',
                          borderRadius: 6,
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 13 }}>📌 {notification.classification}</div>
                          <div style={{ fontSize: 12, color: '#666' }}>{notification.message}</div>
                        </div>
                        <button onClick={actions.hide} style={{ border: 'none', background: 'transparent', cursor: 'pointer' }}>
                          🗑️
                        </button>
                      </div>
                    )
                  : undefined
              }
            />
          </div>
        </nav>

        {/* Sayfanin geri kalani icin kisa bir dolgu - gercekci hissi tamamlar */}
        <div style={{ padding: '32px 28px 0', fontSize: 13, color: pageTheme === 'dark' ? '#a6a6a6' : '#6e7781' }}>
          Bu, zilin gerçek bir uygulamanın navbar'ında nasıl duracağını gösteren bir demo. Sağ üstteki düğmeyle sayfanın (ve panelin) temasını değiştirebilirsin.
        </div>

        <main style={{ display: 'flex', maxWidth: 1100, margin: '24px auto', gap: 20, padding: '0 20px', alignItems: 'flex-start' }}>
          {/* Sol taraf: sekme menusu */}
          <nav style={{ width: 180, flexShrink: 0, background: '#fff', border: '1px solid #eaecef', borderRadius: 10, overflow: 'hidden' }}>
            {TABS.map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                style={{
                  display: 'block',
                  width: '100%',
                  textAlign: 'left',
                  padding: '11px 16px',
                  border: 'none',
                  borderLeft: activeTab === tab ? '3px solid #1677ff' : '3px solid transparent',
                  background: activeTab === tab ? '#eef4ff' : 'transparent',
                  color: activeTab === tab ? '#1677ff' : '#333',
                  fontWeight: activeTab === tab ? 600 : 400,
                  fontSize: 13,
                  cursor: 'pointer',
                }}
              >
                {tab}
              </button>
            ))}
          </nav>

          {/* Sag taraf: secili sekmenin ayarlari */}
          <div style={{ flex: 1, background: '#fff', border: '1px solid #eaecef', borderRadius: 10, padding: 20, minHeight: 220 }}>
            {activeTab === 'Rozet' && (
              <>
                <Field label="İkon">
                  <select value={iconName} onChange={(e) => setIconName(e.target.value as keyof typeof ICON_OPTIONS)}>
                    {Object.keys(ICON_OPTIONS).map((name) => (
                      <option key={name} value={name}>{name}</option>
                    ))}
                  </select>
                </Field>
                <Field label="Rozet rengi">
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <input type="checkbox" checked={useBadgeColor} onChange={(e) => setUseBadgeColor(e.target.checked)} />
                    <input
                      type="color"
                      value={badgeColor}
                      disabled={!useBadgeColor}
                      onChange={(e) => setBadgeColor(e.target.value)}
                      style={{ width: 44, padding: 0, height: 28 }}
                    />
                    <span style={{ fontSize: 12, color: '#6e7781' }}>{useBadgeColor ? badgeColor : 'varsayılan (kırmızı)'}</span>
                  </div>
                </Field>
                <Checkbox checked={showCount} onChange={setShowCount} text="showCount (sayı göster)" />
                <Checkbox
                  checked={showStatusIcon}
                  onChange={setShowStatusIcon}
                  text="showStatusIcon (varsayılan ikonda ses yayları / bildirim çizgisi)"
                />
                <p style={{ fontSize: 12, color: '#6e7781', margin: '4px 0 0' }}>
                  İkon "Varsayılan" iken: sağ tıkla → "Sesi kapat" (yaylar kalkar) / "Bildirimleri kapat" (çapraz çizgi).
                </p>
              </>
            )}

            {activeTab === 'Panel' && (
              <>
                <Field label="Konum (placement)">
                  <select value={panelPlacement} onChange={(e) => setPanelPlacement(e.target.value as PopupPlacement)}>
                    {['bottomRight', 'bottomLeft', 'bottom', 'topRight', 'topLeft', 'top'].map((p) => (
                      <option key={p} value={p}>{p}</option>
                    ))}
                  </select>
                </Field>
                <Field label="Genişlik (px)">
                  <input type="number" value={panelWidth} onChange={(e) => setPanelWidth(Number(e.target.value))} />
                </Field>
                <Field label="Yükseklik (liste alanı, px)">
                  <input type="number" value={panelHeight} onChange={(e) => setPanelHeight(Number(e.target.value))} />
                </Field>
                <Field label="offsetX (gövdeyi kaydır, ok sabit kalmalı)">
                  <input type="number" value={panelOffsetX} onChange={(e) => setPanelOffsetX(Number(e.target.value))} />
                </Field>
                <Field label="offsetY (gövdeyi kaydır, ok sabit kalmalı)">
                  <input type="number" value={panelOffsetY} onChange={(e) => setPanelOffsetY(Number(e.target.value))} />
                </Field>
                <Field label="arrowOffsetX (SADECE oku kaydır)">
                  <input type="number" value={arrowOffsetX} onChange={(e) => setArrowOffsetX(Number(e.target.value))} />
                </Field>
                <Field label="arrowOffsetY (SADECE oku kaydır)">
                  <input type="number" value={arrowOffsetY} onChange={(e) => setArrowOffsetY(Number(e.target.value))} />
                </Field>
                <Field label="Panel arka planı (background)">
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <input type="checkbox" checked={useCustomPanelBg} onChange={(e) => setUseCustomPanelBg(e.target.checked)} />
                    <input
                      type="color"
                      value={panelBg}
                      disabled={!useCustomPanelBg}
                      onChange={(e) => setPanelBg(e.target.value)}
                      style={{ width: 44, padding: 0, height: 28 }}
                    />
                    <span style={{ fontSize: 12, color: '#6e7781' }}>{useCustomPanelBg ? panelBg : 'varsayılan (temaya göre)'}</span>
                  </div>
                </Field>
                <Field label="Ok arka planı (arrowBackground — boşsa panel rengini takip eder)">
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                    <input type="checkbox" checked={useCustomArrowBg} onChange={(e) => setUseCustomArrowBg(e.target.checked)} />
                    <input
                      type="color"
                      value={arrowBg}
                      disabled={!useCustomArrowBg}
                      onChange={(e) => setArrowBg(e.target.value)}
                      style={{ width: 44, padding: 0, height: 28 }}
                    />
                    <span style={{ fontSize: 12, color: '#6e7781' }}>{useCustomArrowBg ? arrowBg : 'panel rengiyle aynı'}</span>
                  </div>
                </Field>
              </>
            )}

            {activeTab === 'Popup' && (
              <>
                <Field label="Kart genişliği (width, px)">
                  <input type="number" value={popupWidth} onChange={(e) => setPopupWidth(Number(e.target.value))} />
                </Field>
                <Field label="Gruplama eşiği">
                  <input type="number" value={groupThreshold} onChange={(e) => setGroupThreshold(Number(e.target.value))} />
                </Field>
                <Field label="Otomatik kapanma (ms, 0 = hiç kapanmaz)">
                  <input
                    type="number"
                    value={autoDismissMs}
                    onChange={(e) => setAutoDismissMs(e.target.value === '' ? '' : Number(e.target.value))}
                  />
                </Field>
                <Field label="Aynı anda gösterilecek en fazla kart (boş = otomatik)">
                  <input
                    type="number"
                    value={maxVisible}
                    onChange={(e) => setMaxVisible(e.target.value === '' ? '' : Number(e.target.value))}
                  />
                </Field>
              </>
            )}

            {activeTab === 'Dil / Okundu' && (
              <>
                <Field label="Dil">
                  <label style={{ marginRight: 16 }}>
                    <input type="radio" checked={language === 'tr'} onChange={() => setLanguage('tr')} /> Türkçe
                  </label>
                  <label>
                    <input type="radio" checked={language === 'en'} onChange={() => setLanguage('en')} /> English
                  </label>
                </Field>
                <Field label="readTrigger — okundu ne zaman işaretlensin">
                  <select value={readTrigger} onChange={(e) => setReadTrigger(e.target.value as typeof readTrigger)}>
                    <option value="onOpen">onOpen (panel kapanınca görünenler)</option>
                    <option value="onClick">onClick (sadece tıklanan)</option>
                    <option value="manual">manual (otomatik yok)</option>
                  </select>
                </Field>
              </>
            )}

            {activeTab === 'Görünüm' && (
              <>
                <p style={{ fontSize: 12, color: '#6e7781', marginTop: 0 }}>
                  Bunlar örnek tasarımlardır — gerçek görünümü App.tsx içindeki ilgili fonksiyonu düzenleyerek değiştirirsin.
                </p>
                <Checkbox checked={useCustomTrigger} onChange={setUseCustomTrigger} text="Özel tetikleyici (renderTrigger)" />
                <Checkbox checked={useCustomPopupCard} onChange={setUseCustomPopupCard} text="Özel popup kartı (renderPopupCard)" />
                <Checkbox checked={useCustomListItem} onChange={setUseCustomListItem} text="Özel liste satırı (renderItem)" />
              </>
            )}

            {activeTab === 'Renkler' && (
              <>
                {NOTIFICATION_TYPES.map((type) => (
                  <Field key={type} label={type}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                      <input
                        type="checkbox"
                        checked={typeOverridesEnabled[type]}
                        onChange={(e) => setTypeOverridesEnabled((prev) => ({ ...prev, [type]: e.target.checked }))}
                      />
                      <input
                        type="color"
                        value={typeColors[type]}
                        disabled={!typeOverridesEnabled[type]}
                        onChange={(e) => setTypeColors((prev) => ({ ...prev, [type]: e.target.value }))}
                        style={{ width: 44, padding: 0, height: 28 }}
                      />
                    </div>
                  </Field>
                ))}
              </>
            )}
          </div>
        </main>

        <div style={{ maxWidth: 1100, margin: '0 auto 32px', padding: '0 20px' }}>
          <div style={{ background: '#fff', border: '1px solid #eaecef', borderRadius: 10, padding: 16 }}>
            <div style={{ fontWeight: 600, marginBottom: 10, fontSize: 13 }}>Olay Günlüğü</div>
            <p style={{ fontSize: 12, color: '#6e7781', marginTop: 0 }}>
              Bildirim göndermek için <code>http://localhost:8080</code> adresindeki test aracını kullan.
            </p>
            <div
              style={{
                height: 160,
                overflowY: 'auto',
                background: '#0d1117',
                color: '#7ee787',
                padding: 10,
                fontFamily: 'SFMono-Regular, Consolas, monospace',
                fontSize: 12,
                borderRadius: 6,
              }}
            >
              {log.length === 0 ? <div>Henüz olay yok.</div> : log.map((line, i) => <div key={i}>{line}</div>)}
            </div>
          </div>
        </div>
      </div>
      {/* Popup yigini artik zil'in ICINDE degil - ayri bir bilesen.
          Playground'daki popup ayarlari dogrudan buraya bagli. */}
      <PopupStack
        theme={pageTheme}
        language={language}
        width={popupWidth}
        groupThreshold={groupThreshold}
        autoDismissMs={autoDismissMs === '' ? undefined : autoDismissMs}
        maxVisible={maxVisible === '' ? undefined : maxVisible}
        topOffset={72}
        typeStyles={Object.keys(typeStylesOverride).length > 0 ? typeStylesOverride : undefined}
        onNotificationClick={(n: Notification) =>
          addLog(`Popup tiklandi: ${n.classification}`)
        }
        onPopupDismiss={(n: Notification, reason: PopupDismissReason) =>
          addLog(`Popup kapandi (${reason}): ${n.classification}`)
        }
        renderPopupCard={
          useCustomPopupCard
            ? (notification: Notification, close: () => void) => (
                <div
                  style={{
                    background: '#1f1f1f',
                    color: '#fff',
                    borderRadius: 8,
                    padding: 12,
                    borderLeft: '4px solid #722ed1',
                  }}
                >
                  <strong>{notification.classification}</strong>
                  <div style={{ fontSize: 13, marginTop: 4 }}>{notification.message}</div>
                  <button onClick={close} style={{ marginTop: 8 }}>Kapat</button>
                </div>
              )
            : undefined
        }
      />
    </NotificationProvider>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div style={{ marginBottom: 14 }}>
      <div style={{ fontSize: 12, color: '#6e7781', marginBottom: 5 }}>{label}</div>
      {children}
    </div>
  );
}

function Checkbox({ checked, onChange, text }: { checked: boolean; onChange: (v: boolean) => void; text: string }) {
  return (
    <label style={{ display: 'block', fontSize: 13, marginTop: 8 }}>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} /> {text}
    </label>
  );
}