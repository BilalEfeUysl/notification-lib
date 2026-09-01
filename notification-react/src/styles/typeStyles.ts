// Bildirim 'type' degerine gore renk paleti (light/dark iki set). PopupStack
// ve NotificationList ortak kullanir. `typeStyles` prop'u verilirse sadece
// belirtilen alanlar/tipler varsayilanin uzerine yazilir.

import { getThemeTokens, type ResolvedTheme } from '../context/theme';

/**
 * Bilinen 4 hazır tip icin ikon adi. Serbest metin (bilinmeyen) tipler icin
 * undefined doner - o zaman hic ikon gosterilmez.
 */
export type KnownIconType = 'success' | 'error' | 'warning' | 'info';

export function getKnownIconType(type: string): KnownIconType | undefined {
  if (type === 'success' || type === 'error' || type === 'warning' || type === 'info') return type;
  return undefined;
}

export interface TypeStyle {
  background: string;
  borderColor: string;
  titleColor: string;
  textColor: string;
}

type BaseTypeStyle = Omit<TypeStyle, 'background'>;

function hexToRgb(hex: string): [number, number, number] {
  const value = hex.replace('#', '');
  return [parseInt(value.slice(0, 2), 16), parseInt(value.slice(2, 4), 16), parseInt(value.slice(4, 6), 16)];
}

// background, ayni tipin borderColor'indan panelBg uzerine belli bir
// alfa ile "bindirilmis" GIBI GORUNEN ama aslinda tamamen OPAK bir renk
// olarak hesaplaniyor (rgba degil, rgb). Sebep: PopupStack'te kartlar
// collapsed (yigin) durumda ust uste bindigi icin GERCEKTEN saydam bir
// arka plan, alttaki kartin yazisinin ustten gorunup yazilarin ust uste
// binmesine yol aciyordu - opak bir renk bu sorunu tamamen ortadan
// kaldiriyor, gorunus (panelBg uzerinde) BIREBIR AYNI kaliyor.
// Karanlik panelde ayni alfa daha soluk okundugu icin dark ~2 kat daha
// yuksek alfa kullaniyor.
const LIGHT_TINT_ALPHA = 0.08;
const DARK_TINT_ALPHA = 0.16;

function blendWithPanel(hex: string, alpha: number, theme: ResolvedTheme): string {
  const [r1, g1, b1] = hexToRgb(hex);
  const [r2, g2, b2] = hexToRgb(getThemeTokens(theme).panelBg);
  const r = Math.round(alpha * r1 + (1 - alpha) * r2);
  const g = Math.round(alpha * g1 + (1 - alpha) * g2);
  const b = Math.round(alpha * b1 + (1 - alpha) * b2);
  return `rgb(${r}, ${g}, ${b})`;
}

const LIGHT_BASE_STYLES: Record<string, BaseTypeStyle> = {
  success: { borderColor: '#3fa14f', titleColor: '#2f7a3d', textColor: '#4a6b4e' },
  error: { borderColor: '#d1554c', titleColor: '#b8453c', textColor: '#7a5450' },
  warning: { borderColor: '#dc9c2e', titleColor: '#b87d1f', textColor: '#86653f' },
  info: { borderColor: '#3f80b3', titleColor: '#2f5f85', textColor: '#4c6478' },
};

const DARK_BASE_STYLES: Record<string, BaseTypeStyle> = {
  success: { borderColor: '#4fbf63', titleColor: '#7fd48e', textColor: '#a3d1aa' },
  error: { borderColor: '#e0736a', titleColor: '#e89189', textColor: '#dba9a4' },
  warning: { borderColor: '#e0a94a', titleColor: '#eec27a', textColor: '#e6cb9c' },
  info: { borderColor: '#4f9bcf', titleColor: '#8fbde0', textColor: '#a8c8de' },
};

function withTintBackground(base: Record<string, BaseTypeStyle>, alpha: number, theme: ResolvedTheme): Record<string, TypeStyle> {
  return Object.fromEntries(
    Object.entries(base).map(([type, style]) => [type, { ...style, background: blendWithPanel(style.borderColor, alpha, theme) }])
  );
}

const LIGHT_TYPE_STYLES: Record<string, TypeStyle> = withTintBackground(LIGHT_BASE_STYLES, LIGHT_TINT_ALPHA, 'light');
const DARK_TYPE_STYLES: Record<string, TypeStyle> = withTintBackground(DARK_BASE_STYLES, DARK_TINT_ALPHA, 'dark');

const DEFAULT_TYPE_STYLE_LIGHT: TypeStyle = {
  background: '#fafafa',
  borderColor: '#d9d9d9',
  titleColor: '#262626',
  textColor: '#595959',
};

const DEFAULT_TYPE_STYLE_DARK: TypeStyle = {
  background: '#262626',
  borderColor: '#434343',
  titleColor: '#e8e8e8',
  textColor: '#bfbfbf',
};

/**
 * Bir tip için nihai renk paletini döner. theme, hangi (light/dark) renk
 * setinin kullanılacağını belirler. overrides verilirse, o tip için SADECE
 * belirtilen alanlar varsayılanın üzerine yazılır (Partial) - belirtilmeyen
 * alanlar seçilen temanın varsayılanında kalmaya devam eder.
 */
export function getTypeStyle(
  type: string,
  theme: ResolvedTheme,
  overrides?: Record<string, Partial<TypeStyle>>
): TypeStyle {
  const styles = theme === 'dark' ? DARK_TYPE_STYLES : LIGHT_TYPE_STYLES;
  const defaultStyle = theme === 'dark' ? DEFAULT_TYPE_STYLE_DARK : DEFAULT_TYPE_STYLE_LIGHT;
  const base = styles[type] ?? defaultStyle;
  const override = overrides?.[type];
  return override ? { ...base, ...override } : base;
}