import { describe, it, expect, vi, afterEach } from 'vitest';
import { resolveLanguage, resolveNotificationText } from './i18n';

const base = { classification: 'Bakim', message: 'Sistem kapanacak' };

describe('resolveNotificationText', () => {
  it('varsayilan metni doner - dil tr', () => {
    expect(resolveNotificationText(base, 'tr')).toEqual(base);
  });

  it('Ingilizce alan yoksa dil en olsa bile varsayilana duser', () => {
    expect(resolveNotificationText(base, 'en')).toEqual(base);
  });

  it('dil en VE Ingilizce alanlar doluysa onlari doner', () => {
    const n = { ...base, classificationEn: 'Maintenance', messageEn: 'System goes down' };
    expect(resolveNotificationText(n, 'en')).toEqual({
      classification: 'Maintenance',
      message: 'System goes down',
    });
  });

  it('dil tr ise Ingilizce alanlar dolu olsa bile varsayilani doner', () => {
    const n = { ...base, classificationEn: 'Maintenance', messageEn: 'System goes down' };
    expect(resolveNotificationText(n, 'tr')).toEqual(base);
  });

  it('yarim Ingilizce (sadece baslik) varsayilana duser', () => {
    const n = { ...base, classificationEn: 'Maintenance', messageEn: null };
    expect(resolveNotificationText(n, 'en')).toEqual(base);
  });
});

describe('resolveLanguage', () => {
  const original = navigator.language;
  afterEach(() => {
    Object.defineProperty(navigator, 'language', { value: original, configurable: true });
    vi.restoreAllMocks();
  });

  it('somut dili oldugu gibi doner', () => {
    expect(resolveLanguage('tr')).toBe('tr');
    expect(resolveLanguage('en')).toBe('en');
  });

  it('auto + tarayici dili en-* → en', () => {
    Object.defineProperty(navigator, 'language', { value: 'en-US', configurable: true });
    expect(resolveLanguage('auto')).toBe('en');
  });

  it('auto + tarayici dili tr-* → tr', () => {
    Object.defineProperty(navigator, 'language', { value: 'tr-TR', configurable: true });
    expect(resolveLanguage('auto')).toBe('tr');
  });

  it('auto + bilinmeyen dil → tr', () => {
    Object.defineProperty(navigator, 'language', { value: 'de-DE', configurable: true });
    expect(resolveLanguage('auto')).toBe('tr');
  });
});
