// Kutuphanenin VARSAYILAN zil ikonu. Duz bir cana ek olarak iki durumu
// ikonun uzerinde gosterir:
//   - ses ACIK        -> canin iki yaninda "titresim yaylari" (cift kavis)
//   - bildirim KAPALI  -> canin uzerine capraz cizgi
// Ikisi de currentColor kullanir; renk/boyut ebeveynden gelir.
//
// NotificationBell showStatusIcon={false} verilirse bunun yerine sade
// <BellOutlined/> render eder; kullanici kendi `icon` prop'unu verdiyse
// yine bu bilesen kullanilmaz.

import './BellStatusIcon.css';

export interface BellStatusIconProps {
  /** true iken canin yanlarinda titresim yaylari cizilir (ses acik). */
  sound: boolean;
  /** false iken canin uzerine capraz cizgi cizilir (bildirim kapali). */
  popups: boolean;
  className?: string;
}

// Lucide "bell" (24'luk kutu). 32x26 kutuya +4/+1.5 kaydirildi: can kendi
// boyutunda kalir, yaylar SOLDA/SAGDA acilan ek boslugu kullanir (tam
// boyutta can kutunun tamamini kaplayip yaylara yer birakmiyordu).
const BELL = 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9';
const CLAPPER = 'M10.3 21a1.9 1.9 0 0 0 3.4 0';

export function BellStatusIcon({ sound, popups, className }: BellStatusIconProps) {
  return (
    <svg
      className={['notif-bell-status-icon', className].filter(Boolean).join(' ')}
      viewBox="0 0 32 26"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.9}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <g transform="translate(4 1.5)">
        <path d={BELL} />
        <path d={CLAPPER} />
      </g>

      {sound && (
        <g strokeWidth={1.6}>
          <path d="M4 9 Q1.2 13 4 17" />
          <path d="M6.1 10.6 Q4.3 13 6.1 15.4" />
          <path d="M28 9 Q30.8 13 28 17" />
          <path d="M25.9 10.6 Q27.7 13 25.9 15.4" />
        </g>
      )}

      {/* capraz cizgi - panel icindeki .notif-icon-strike ile ayni dil
          (halo yok, dogrudan currentColor cizgi) */}
      {!popups && <line x1="9" y1="19.5" x2="24" y2="4.5" strokeWidth={1.9} />}
    </svg>
  );
}
