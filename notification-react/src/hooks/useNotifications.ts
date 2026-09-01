// Bilesenlerin NotificationProvider'daki veriye eristigi tek nokta -
// NotificationContext'i dogrudan kullanmaya gerek yok.

import { useContext } from 'react';
import { NotificationContext, type NotificationContextValue } from '../lib/NotificationContext';
/**
 * Bildirim verisine (liste, sayfalama, silme, WebSocket'e abone olma) erisim saglar.
 * NotificationProvider'in ICINDE bir yerde cagirilmalidir, yoksa hata firlatir.
 */
export function useNotifications(): NotificationContextValue {
  const context = useContext(NotificationContext);

  if (context === null) {
    throw new Error(
      'useNotifications() yalnizca <NotificationProvider> icindeki bir bilesende cagirilabilir.'
    );
  }

  return context;
}