// Testler icin ortak yardimcilar. Gercek Provider/Context/WebSocket kurmadan,
// useNotifications() ciktisini elle sekillendirmek icin.

import { vi } from 'vitest'
import type { Notification } from '../types'
import type { NotificationContextValue } from '../lib/NotificationContext'

/** Eksik alanlari makul varsayilanlarla dolduran sahte bildirim. */
export function makeNotification(overrides: Partial<Notification> = {}): Notification {
  return {
    id: 'id',
    classification: 'Baslik',
    message: 'test mesaji',
    classificationEn: null,
    messageEn: null,
    type: 'info',
    priority: 'NORMAL',
    read: false,
    saved: false,
    createdAt: new Date('2026-08-27T10:15:00Z').toISOString(),
    metadata: {},
    sourceDeviceId: null,
    ...overrides,
  }
}

/**
 * useNotifications() mock'u. mockReturnValue TAM NotificationContextValue
 * sekli bekledigi icin geri kalan alanlari makul varsayilanlarla dolduruyoruz
 * (any'e kacmadan). Sadece ilgilendigin alanlari override et.
 */
export function makeNotificationsMock(
  overrides: Partial<NotificationContextValue> = {},
): NotificationContextValue {
  return {
    notifications: [],
    hasMore: false,
    loading: false,
    error: null,
    loadMore: vi.fn(),
    hide: vi.fn().mockResolvedValue(undefined),
    hideAll: vi.fn().mockResolvedValue(undefined),
    markAsRead: vi.fn(),
    unreadCount: 0,
    soundEnabled: true,
    toggleSound: vi.fn(),
    popupsEnabled: true,
    togglePopups: vi.fn(),
    toggleSaved: vi.fn().mockResolvedValue(undefined),
    fetchSaved: vi.fn().mockResolvedValue({ items: [], hasMore: false, nextBefore: null }),
    searchNotificationsRemote: vi
      .fn()
      .mockResolvedValue({ items: [], hasMore: false, nextBefore: null }),
    subscribe: vi.fn(() => () => {}),
    connectionStatus: 'connected',
    language: 'tr',
    ...overrides,
  }
}
