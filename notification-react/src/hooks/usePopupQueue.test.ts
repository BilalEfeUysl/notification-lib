import { renderHook, waitFor, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { usePopupQueue } from './usePopupQueue'
import { useNotifications } from './useNotifications'
import type { Notification } from '../types'
import { makeNotification, makeNotificationsMock } from '../test-utils/notificationMocks'

// useNotifications'i DOGRUDAN sahtelestiriyoruz - gercek Provider/Context
// kurmadan, usePopupQueue'ya istedigimiz senaryoyu elle veriyoruz.
vi.mock('./useNotifications', () => ({
  useNotifications: vi.fn(),
}))

describe('usePopupQueue - acilis toplu gosterimi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sadece OKUNMAMIS bildirimleri, EN-YENIDEN-ESKIYE sirayla kuyruga ekler', async () => {
    // notifications listesi en-yeniden-eskiye sirali geliyor (koddaki varsayim).
    // n3 en yeni, n1 en eski. n2 OKUNMUS - kuyrukta HIC GORUNMEMELI.
    const n3 = makeNotification({ id: 'n3', read: false })
    const n2 = makeNotification({ id: 'n2', read: true })
    const n1 = makeNotification({ id: 'n1', read: false })

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [n3, n2, n1],
      loading: false,
      subscribe: vi.fn(() => () => {}),
    }))

    const { result } = renderHook(() => usePopupQueue())

    await waitFor(() => expect(result.current.items).toHaveLength(2))

    // Beklenen sira: en yeni okunmamis (n3) basta, en eski okunmamis (n1) sonda.
    // n2 (okunmus) listede HIC olmamali.
    expect(result.current.items.map((i) => i.notification.id)).toEqual(['n3', 'n1'])
  })

  it('loading true iken acilis toplu gosterimi TETIKLENMEZ', () => {
    const n1 = makeNotification({ id: 'n1', read: false })

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [n1],
      loading: true, // hala yukleniyor
      subscribe: vi.fn(() => () => {}),
    }))

    const { result } = renderHook(() => usePopupQueue())

    // loading true oldugu surece kuyruk BOS kalmali.
    expect(result.current.items).toHaveLength(0)
  })
})

describe('usePopupQueue - canli WebSocket bildirimleri', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('acilis toplu gosterimi BITMEDEN gelen canli bildirim GORMEZDEN GELINIR', () => {
    // subscribe'a verilen dinleyiciyi disaridan tetikleyebilmek icin yakaliyoruz.
    let liveListener: ((n: Notification) => void) | undefined
    const mockSubscribe = vi.fn((listener: (n: Notification) => void) => {
      liveListener = listener
      return () => {}
    })

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [],
      loading: true, // hala yukleniyor - acilis toplu gosterimi efekti HENUZ calismadi
      subscribe: mockSubscribe,
    }))

    const { result } = renderHook(() => usePopupQueue())

    // WebSocket'ten "canli" bir bildirim geldigini simule ediyoruz.
    const liveNotification = makeNotification({ id: 'live-1', read: false })
    act(() => {
      liveListener?.(liveNotification)
    })

    // openingBatchShownRef hala false oldugu icin bu bildirim YOK SAYILMALI.
    expect(result.current.items).toHaveLength(0)
  })

  it('acilis toplu gosterimi BITTIKTEN SONRA gelen canli bildirim kuyruga EKLENIR', async () => {
    let liveListener: ((n: Notification) => void) | undefined
    const mockSubscribe = vi.fn((listener: (n: Notification) => void) => {
      liveListener = listener
      return () => {}
    })

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [], // acilista gosterilecek gecmis bildirim yok
      loading: false, // yukleme BITTI - acilis toplu gosterimi efekti calisip openingBatchShownRef'i true yapacak
      subscribe: mockSubscribe,
    }))

    const { result } = renderHook(() => usePopupQueue())

    // Acilis efektinin calisip openingBatchShownRef'i true yapmasini bekle.
    await waitFor(() => expect(mockSubscribe).toHaveBeenCalled())

    const liveNotification = makeNotification({ id: 'live-1', read: false })
    act(() => {
      liveListener?.(liveNotification)
    })

    // Artik acilis toplu gosterimi bitti, bu bildirim kuyruga EKLENMELI.
    expect(result.current.items).toHaveLength(1)
    expect(result.current.items[0].notification.id).toBe('live-1')
  })
})

describe('usePopupQueue - dismiss ve otomatik kapanma', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('dismiss(key, "user") ogeyi kaldirir ve onDismiss "user" nedeniyle cagirilir', async () => {
    const n1 = makeNotification({ id: 'n1', read: false })
    const onDismiss = vi.fn()

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [n1],
      loading: false,
      subscribe: vi.fn(() => () => {}),
    }))

    const { result } = renderHook(() => usePopupQueue({ onDismiss }))

    await vi.waitFor(() => expect(result.current.items).toHaveLength(1))

    act(() => {
      result.current.dismiss('n1', 'user')
    })

    expect(result.current.items).toHaveLength(0)
    expect(onDismiss).toHaveBeenCalledWith(n1, 'user')
  })

  it('autoDismissMs suresi dolunca popup kendiliginden kapanir (reason: timeout)', async () => {
    const n1 = makeNotification({ id: 'n1', read: false })
    const onDismiss = vi.fn()

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [n1],
      loading: false,
      subscribe: vi.fn(() => () => {}),
    }))

    const { result } = renderHook(() => usePopupQueue({ autoDismissMs: 5000, onDismiss }))

    await vi.waitFor(() => expect(result.current.items).toHaveLength(1))

    // Zamani 5000ms ileri sardiriyoruz - gercekte 5 saniye beklemeden.
    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(result.current.items).toHaveLength(0)
    expect(onDismiss).toHaveBeenCalledWith(n1, 'timeout')
  })

  it('autoDismissMs: null verilirse zaman ne kadar ilerlese de popup HIC otomatik kapanmaz', async () => {
    const n1 = makeNotification({ id: 'n1', read: false })

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [n1],
      loading: false,
      subscribe: vi.fn(() => () => {}),
    }))

    const { result } = renderHook(() => usePopupQueue({ autoDismissMs: null }))

    await vi.waitFor(() => expect(result.current.items).toHaveLength(1))

    act(() => {
      vi.advanceTimersByTime(60000) // 1 dakika ileri sardirsak bile
    })

    expect(result.current.items).toHaveLength(1) // hala duruyor olmali
  })

  it('pauseAutoDismiss zamanlayiciyi durdurur, resumeAutoDismiss yeniden baslatir', async () => {
    const n1 = makeNotification({ id: 'n1', read: false })

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [n1],
      loading: false,
      subscribe: vi.fn(() => () => {}),
    }))

    const { result } = renderHook(() => usePopupQueue({ autoDismissMs: 5000 }))

    await vi.waitFor(() => expect(result.current.items).toHaveLength(1))

    act(() => {
      result.current.pauseAutoDismiss()
      vi.advanceTimersByTime(5000) // durdurulmus zamanlayici - kapanmamali
    })
    expect(result.current.items).toHaveLength(1)

    act(() => {
      result.current.resumeAutoDismiss()
      vi.advanceTimersByTime(5000) // yeni zamanlayici basladi, simdi kapanmali
    })
    expect(result.current.items).toHaveLength(0)
  })
})

describe('usePopupQueue - stres testi (yuksek hacim)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('500 canli bildirim hizla art arda gelirse HATASIZ islenir, hicbiri kaybolmaz', async () => {
    let liveListener: ((n: Notification) => void) | undefined
    const mockSubscribe = vi.fn((listener: (n: Notification) => void) => {
      liveListener = listener
      return () => {}
    })

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [],
      loading: false,
      subscribe: mockSubscribe,
    }))

    const { result } = renderHook(() => usePopupQueue({ autoDismissMs: null }))

    await vi.waitFor(() => expect(mockSubscribe).toHaveBeenCalled())

    const BURST_SIZE = 500

    // 500 bildirimi ayni "tick"te, art arda tetikliyoruz - gercek hayatta
    // bir toplu bildirim yayini (ornegin sistem geneli bir duyuru) boyle
    // gorunur.
    act(() => {
      for (let i = 0; i < BURST_SIZE; i++) {
        liveListener?.(makeNotification({ id: `burst-${i}`, read: false }))
      }
    })

    // Hicbir sey kaybolmamis olmali - enqueue basa ekledigi icin en son
    // gelen (burst-499) en basta olmali.
    expect(result.current.items).toHaveLength(BURST_SIZE)
    expect(result.current.items[0].notification.id).toBe('burst-499')
    expect(result.current.items[BURST_SIZE - 1].notification.id).toBe('burst-0')
  })

  it('500 bildirimden sonra unmount olunca TUM zamanlayicilar temizlenir (sizinti yok)', async () => {
    let liveListener: ((n: Notification) => void) | undefined
    const mockSubscribe = vi.fn((listener: (n: Notification) => void) => {
      liveListener = listener
      return () => {}
    })

    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({
      notifications: [],
      loading: false,
      subscribe: mockSubscribe,
    }))

    // autoDismissMs verilmedigi icin VARSAYILAN (6000ms) kullanilacak -
    // yani her bildirim gercekten bir setTimeout kuruyor, tam da test
    // etmek istedigimiz senaryo bu.
    const { result, unmount } = renderHook(() => usePopupQueue())

    await vi.waitFor(() => expect(mockSubscribe).toHaveBeenCalled())

    act(() => {
      for (let i = 0; i < 500; i++) {
        liveListener?.(makeNotification({ id: `burst-${i}`, read: false }))
      }
    })

    expect(result.current.items).toHaveLength(500)
    // Vitest'in sahte zamanlayici sayacinda su an bekleyen ~500 zamanlayici olmali.
    expect(vi.getTimerCount()).toBeGreaterThanOrEqual(500)

    unmount()

    // unmount SONRASI, hicbir bekleyen zamanlayici KALMAMALI - kalirsa bu,
    // component ekrandan kalktiktan sonra bile arka planda calismaya devam
    // eden (ve olası bir "kaldirilmis bilesende state guncelleme" hatasina
    // yol acabilecek) bir sizinti demektir.
    expect(vi.getTimerCount()).toBe(0)
  })
})