// Light/dark tema. NotificationBell'e verilen `theme` prop'una göre
// ("light"|"dark"|"auto") bir ThemeContext kurulur; alt bileşenler
// renklerini bu context'ten okur (tek tek theme prop'u geçirmek gerekmez).

import { createContext, useContext, useEffect, useState } from 'react';

export type ThemeName = 'light' | 'dark' | 'auto';
export type ResolvedTheme = 'light' | 'dark';

export interface ThemeTokens {
  /** Panelin (bildirim listesinin açtığı kutu) arka plan rengi. */
  panelBg: string;
  /** İnce ayırıcı çizgiler. */
  border: string;
  /** Boş liste / "yükleniyor..." gibi soluk yardımcı metinler. */
  mutedText: string;
  /** Bildirim gövdesi (mesaj) metni. */
  secondaryText: string;
  /** Zaman damgası, silme ikonu gibi en soluk detaylar. */
  faintText: string;
  /** Okunmamış bildirim satırının arka planı. */
  unreadBg: string;
  /** Uyarı metni (ör. "bağlantı koptu" satırı). */
  warningText: string;
  /** Uyarı arka planı (ör. "bağlantı koptu" satırı). */
  warningBg: string;
}

const LIGHT_TOKENS: ThemeTokens = {
  panelBg: '#ffffff',
  border: '#f0f0f0',
  // WCAG AA icin en az 4.5:1 kontrast (beyaz panelBg uzerinde). Eski
  // #8c8c8c/#bfbfbf degerleri sirasiyla ~3.36:1 ve ~1.84:1 idi - ikisi
  // de yetersizdi, ozellikle faintText (zaman damgasi gibi gercek metin
  // icin kullaniliyor) neredeyse okunmaz seviyedeydi.
  mutedText: '#666666',   // ~5.74:1
  secondaryText: '#595959',
  faintText: '#767676',   // ~4.50:1
  unreadBg: '#fafafa',
  warningText: '#ad4e00',   // amber, beyaz uzerinde ~4.9:1
  warningBg: '#fff7e6',
};

const DARK_TOKENS: ThemeTokens = {
  panelBg: '#1f1f1f',
  border: '#303030',
  mutedText: '#a6a6a6',
  secondaryText: '#d9d9d9',
  // Eski #737373 karsi panelBg'ye (#1f1f1f) ~3.48:1 idi, 4.5 esiginin
  // altinda kaliyordu.
  faintText: '#8a8a8a',   // ~4.78:1
  unreadBg: '#262626',
  warningText: '#e8a33d',   // amber, koyu panelBg (#1f1f1f) uzerinde ~7:1
  warningBg: '#2b2111',     // koyu amber tonu
};

/**
 * theme="auto" iken, tarayıcının/işletim sisteminin dark mode ayarını
 * (prefers-color-scheme) okur ve değişikliği canlı izler. theme "light"
 * veya "dark" olarak açıkça verilmişse, sistem ne olursa olsun o kullanılır.
 */
export function useResolvedTheme(theme: ThemeName): ResolvedTheme {
  const [systemPrefersDark, setSystemPrefersDark] = useState(() =>
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(prefers-color-scheme: dark)').matches
      : false
  );

  useEffect(() => {
    if (theme !== 'auto') return;
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    function handleChange(event: MediaQueryListEvent) {
      setSystemPrefersDark(event.matches);
    }
    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [theme]);

  if (theme === 'light' || theme === 'dark') return theme;
  return systemPrefersDark ? 'dark' : 'light';
}

/** Alt bileşenlerin okuduğu, çözümlenmiş ('light'/'dark') temayı taşıyan context. Varsayılan 'light'. */
export const ThemeContext = createContext<ResolvedTheme>('light');

/** Şu anki çözümlenmiş temayı döner. */
export function useTheme(): ResolvedTheme {
  return useContext(ThemeContext);
}

/** Verilen (çözümlenmiş) temaya ait renk tokenlarını döner - Context'e ihtiyaç duymaz. */
export function getThemeTokens(theme: ResolvedTheme): ThemeTokens {
  return theme === 'dark' ? DARK_TOKENS : LIGHT_TOKENS;
}

/** Şu anki temaya göre renk tokenlarını döner (Context içinden okur). */
export function useThemeTokens(): ThemeTokens {
  const theme = useContext(ThemeContext);
  return getThemeTokens(theme);
}