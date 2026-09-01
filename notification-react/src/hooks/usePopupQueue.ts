// Popup kuyrugu: acilistaki toplu gosterim + canli WebSocket bildirimleri.
// Her ogenin otomatik kaybolma zamanlayicisi burada tutulur.

import { useCallback, useEffect, useRef, useState } from 'react';
import type { Notification } from '../types';
import { useNotifications } from './useNotifications';

const DEFAULT_AUTO_DISMISS_MS = 6000;

/** Bir popup nasil kapandi: kullanici mi kapatti, yoksa suresi mi doldu. */
export type PopupDismissReason = 'timeout' | 'user';

export interface PopupQueueItem {
  key: string;
  notification: Notification;
}

export interface UsePopupQueueOptions {
  /**
   * Bir popup kac ms sonra otomatik kapansin. null veya 0 verilirse HIC
   * otomatik kapanmaz, sadece kullanici (x) ile kapatabilir.
   * Verilmezse varsayilan 6000ms kullanilir.
   */
  autoDismissMs?: number | null;
  /** Bir popup kapandiginda (otomatik veya kullanici tarafindan) cagirilir. */
  onDismiss?: (notification: Notification, reason: PopupDismissReason) => void;
}

export function usePopupQueue(options?: UsePopupQueueOptions) {
  const { notifications, loading, subscribe, popupsEnabled } = useNotifications();
  const [items, setItems] = useState<PopupQueueItem[]>([]);
  const openingBatchShownRef = useRef(false);
  const timersRef = useRef(new Map<string, ReturnType<typeof setTimeout>>());
  const itemsRef = useRef<PopupQueueItem[]>([]);

  // items her degistiginde ref'i de guncel tut - dismiss() icinde
  // useCallback'i "items"e bagimli yapmadan en guncel listeye erisebilmek icin.
  useEffect(() => {
    itemsRef.current = items;
  }, [items]);

  // Callback ve ayarlari ref'te tutuyoruz ki her render'da yeniden olusan
  // fonksiyonlar (kullanici inline `() => ...` verdiyse) dismiss/scheduleDismiss'i
  // gereksiz yere yeniden olusturmasin.
  const onDismissRef = useRef(options?.onDismiss);
  onDismissRef.current = options?.onDismiss;
  const autoDismissMsRef = useRef(options?.autoDismissMs);
  autoDismissMsRef.current = options?.autoDismissMs;

  const dismiss = useCallback((key: string, reason: PopupDismissReason = 'user') => {
    const item = itemsRef.current.find((i) => i.key === key);
    setItems((prev) => prev.filter((i) => i.key !== key));
    const timer = timersRef.current.get(key);
    if (timer !== undefined) {
      clearTimeout(timer);
      timersRef.current.delete(key);
    }
    if (item) {
      onDismissRef.current?.(item.notification, reason);
    }
  }, []);

  const scheduleDismiss = useCallback(
    (key: string) => {
      const configured = autoDismissMsRef.current;
      const ms = configured === undefined ? DEFAULT_AUTO_DISMISS_MS : configured;
      if (!ms) return; // null veya 0 = otomatik kapanma kapali
      const timer = setTimeout(() => dismiss(key, 'timeout'), ms);
      timersRef.current.set(key, timer);
    },
    [dismiss]
  );

  const enqueue = useCallback(
    (notification: Notification) => {
      const key = notification.id;
      setItems((prev) => [{ key, notification }, ...prev]);
      scheduleDismiss(key);
    },
    [scheduleDismiss]
  );

  // Acilis toplu gosterimi: SADECE BIR KERE calisir. SADECE OKUNMAMIS
  // bildirimler popup olarak gosterilir - kullanici daha once gorup
  // okudugu eski bildirimler, sayfa her acildiginda/yenilendiginde tekrar
  // popup olarak karsisina cikmamali (bu, "kacirdiklarim" hissini bozar).
  useEffect(() => {
    if (openingBatchShownRef.current) return;
    if (loading) return;
    openingBatchShownRef.current = true;
    // Popup gosterimi kapaliysa acilis toplu gosterimi de HIC yapilmiyor -
    // ref yine de true'ya cekiliyor ki kullanici sonradan popup'lari
    // TEKRAR acarsa, o an biriken TUM okunmamis gecmis birden popup
    // olarak "patlamasin" (bu, sadece ilk yuklemeye ozel bir davranis).
    if (!popupsEnabled) return;
    // notifications en-yeniden-eskiye sirali; enqueue basa ekledigi icin
    // ters sirayla ekleyip sonucun yine en-yeni-basta olmasini sagliyoruz.
    [...notifications]
      .filter((n) => !n.read)
      .reverse()
      .forEach(enqueue);
  }, [loading, notifications, enqueue, popupsEnabled]);

  // Canli WebSocket bildirimleri.
  useEffect(() => {
    const unsubscribe = subscribe((notification) => {
      if (!openingBatchShownRef.current) return;
      if (!popupsEnabled) return;
      enqueue(notification);
    });
    return unsubscribe;
  }, [subscribe, enqueue, popupsEnabled]);

  // Popup kapatildiginda SADECE yenilerini engellemek yetmiyor - o an
  // ekranda acik duran popup'lar da kapanmali. dismiss() her biri icin
  // ayri ayri cagriliyor ki onDismiss callback'i ('user' sebebiyle) ve
  // zamanlayici temizligi normal akisiyla aynen calissin.
  useEffect(() => {
    if (popupsEnabled) return;
    itemsRef.current.forEach((item) => dismiss(item.key, 'user'));
  }, [popupsEnabled, dismiss]);

  // Bilesen kaldirildiginda tum zamanlayicilari temizle.
  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach(clearTimeout);
      timers.clear();
    };
  }, []);

  const pauseAutoDismiss = useCallback(() => {
    timersRef.current.forEach((timer) => clearTimeout(timer));
    timersRef.current.clear();
  }, []);

  // itemsRef kullaniyoruz (items degil) - boylece bu fonksiyonun kimligi
  // SABIT kalir, her yeni bildirimde yeniden olusturulmaz. Ayrica
  // zaten zamanlayicisi OLAN bir ogeye tekrar zamanlayici KURMUYORUZ -
  // yogun trafikte (ornegin animasyonlu yigin genisligi degisirken
  // istemeden art arda tetiklenen mouseenter/mouseleave) ayni ogeler
  // icin gereksiz yere yuzlerce zamanlayicinin ust uste kurulmasini
  // (ve bunun React'in "nested update" uyarisini tetiklemesini) engelliyor.
  const resumeAutoDismiss = useCallback(() => {
    itemsRef.current.forEach((item) => {
      if (!timersRef.current.has(item.key)) {
        scheduleDismiss(item.key);
      }
    });
  }, [scheduleDismiss]);

  return { items, dismiss, pauseAutoDismiss, resumeAutoDismiss };
}