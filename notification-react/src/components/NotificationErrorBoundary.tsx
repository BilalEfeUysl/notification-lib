import { Component, type ReactNode } from 'react';

export interface NotificationErrorBoundaryProps {
  children: ReactNode;
  /**
   * Bir render hatasi yakalandiginda gosterilecek yedek gorunum.
   * Verilmezse, hicbir sey render edilmez (kutuphane sessizce kaybolur,
   * ama en azindan KULLANAN UYGULAMAYI cokertmez).
   */
  fallback?: ReactNode;
  /** Yakalanan hatayi kullanan uygulamaya bildirmek icin (loglama vb.). */
  onError?: (error: Error) => void;
}

interface NotificationErrorBoundaryState {
  hasError: boolean;
}

/**
 * notification-react icindeki bir render hatasinin, kutuphaneyi kullanan
 * uygulamanin TAMAMINI cokertmesini engeller. React'in Error Boundary
 * mekanizmasi SADECE class component'lerde calisir (fonksiyon component'te
 * bu iki metod yazilamaz) - bu yuzden istisnai olarak class kullaniyoruz.
 *
 * ONEMLI SINIR: bu SADECE render sirasindaki hatalari yakalar. fetch/WebSocket
 * hatalari zaten reportError/onError ile ayri ele aliniyor, bu ikisi
 * birbirinin yerine gecmez.
 */
export class NotificationErrorBoundary extends Component<
  NotificationErrorBoundaryProps,
  NotificationErrorBoundaryState
> {
  state: NotificationErrorBoundaryState = { hasError: false };

  // React, cocuklardan biri render sirasinda hata firlattiginda bu metodu
  // cagirir - donen deger bir sonraki render'da state olarak kullanilir.
  static getDerivedStateFromError(): NotificationErrorBoundaryState {
    return { hasError: true };
  }

  // Hatanin KENDISINE ve nerede oldugunun detayina (componentStack) burada
  // erisebiliyoruz - loglama/raporlama icin.
  componentDidCatch(error: Error): void {
    this.props.onError?.(error);
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return this.props.fallback ?? null;
    }
    return this.props.children;
  }
}