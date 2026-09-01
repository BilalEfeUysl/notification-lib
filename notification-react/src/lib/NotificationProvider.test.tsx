import { render, screen, waitFor, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useContext } from 'react'
import { NotificationProvider } from './NotificationProvider'
import { useTheme } from '../context/theme'
import { NotificationContext, type NotificationContextValue } from './NotificationContext'
import { fetchNotifications, fetchUnreadCount } from './restClient'

// restClient'in TAMAMINI sahte fonksiyonlarla degistiriyoruz - gercek network
// istegi atmasin diye. Her fonksiyon simdilik "bos" bir vi.fn(), asagida
// testin icinde ne donderecegini ayrica belirleyecegiz.
vi.mock('./restClient', () => ({
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  hideAllNotifications: vi.fn(),
  hideNotification: vi.fn(),
  markNotificationsAsRead: vi.fn(),
}))

// NotificationSocket'i de sahteleştiriyoruz - gercek bir WebSocket baglantisi
// kurmaya calismasin diye (jsdom'da bu zaten calismaz).
// on()'a kaydedilen dinleyicileri GERCEKTEN saklayan bir sahte socket.
// Boylece testin icinden "olay X tetiklendi" diye elle cagirabiliyoruz -
// tipki gercek NotificationSocket'in davranisini taklit eder gibi.
const socketListeners = new Map<string, Set<(detail: unknown) => void>>()

function emitToSocketListeners(event: string, detail: unknown) {
  socketListeners.get(event)?.forEach((listener) => listener(detail))
}

vi.mock('./NotificationSocket', () => ({
  NotificationSocket: vi.fn().mockImplementation(() => ({
    on: vi.fn((event: string, listener: (detail: unknown) => void) => {
      if (!socketListeners.has(event)) socketListeners.set(event, new Set())
      socketListeners.get(event)!.add(listener)
    }),
    off: vi.fn((event: string, listener: (detail: unknown) => void) => {
      socketListeners.get(event)?.delete(listener)
    }),
    connect: vi.fn(),
    disconnect: vi.fn(),
  })),
}))

describe('NotificationProvider - loadMore yaris durumu korumasi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    socketListeners.clear()
    vi.mocked(fetchUnreadCount).mockResolvedValue(0)
  })

  it('loadMore ust uste iki kez cagrilirsa backend e SADECE bir istek gider', async () => {
    // Acilis yuklemesi (Provider ilk render oldugunda otomatik cagirilan
    // fetchNotifications) icin sahte cevap.
    vi.mocked(fetchNotifications).mockResolvedValue({
      items: [],
      hasMore: true,
      nextBefore: 'cursor-1',
    })

    // Context degerine disaridan erisebilmek icin kucuk bir "casus" bilesen.
    const ctx: { current: NotificationContextValue | null } = { current: null }
    function Spy() {
      ctx.current = useContext(NotificationContext)
      return null
    }

    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test">
        <Spy />
      </NotificationProvider>
    )

    // Acilis yuklemesinin bitmesini bekle.
    await waitFor(() => expect(ctx.current?.loading).toBe(false))

    // Acilista zaten 1 cagri oldu - onu sayimdan cikar, temiz baslayalim.
    vi.mocked(fetchNotifications).mockClear()

    // ASIL TEST: loadMore'u art arda, ayni anda iki kez cagir
    // (hizli scroll sirasinda gercekte olan budur). loadMore() zaten
    // async oldugu icin donen promise'leri DOGRUDAN await ediyoruz -
    // boylece act() sadece "cagrildi mi" degil, ic taraftaki TUM
    // setState guncellemeleri (then/finally dahil) bitene kadar bekliyor.
    await act(async () => {
      await Promise.all([ctx.current?.loadMore(), ctx.current?.loadMore()])
    })

    // Koruma dogru calisiyorsa: iki cagriya ragmen backend e SADECE 1 istek gitmis olmali.
    expect(fetchNotifications).toHaveBeenCalledTimes(1)
  })
})


describe('NotificationProvider - baglanti durumu', () => {
  beforeEach(() => {
    vi.mocked(fetchNotifications).mockResolvedValue({
      items: [],
      hasMore: false,
      nextBefore: null,
    })
  })

  it('socket disconnected olayi yayinlaninca connectionStatus disconnected olur', async () => {
    const ctx: { current: NotificationContextValue | null } = { current: null }
    function Spy() {
      ctx.current = useContext(NotificationContext)
      return null
    }

    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test">
        <Spy />
      </NotificationProvider>
    )

    await waitFor(() => expect(ctx.current?.loading).toBe(false))
    // Baslangicta 'connected' varsayiliyor (bkz. NotificationProvider.tsx).
    expect(ctx.current?.connectionStatus).toBe('connected')

    act(() => {
      emitToSocketListeners('disconnected', undefined)
    })

    expect(ctx.current?.connectionStatus).toBe('disconnected')
  })

  it('disconnected sonrasi connected olayi gelince connectionStatus tekrar connected olur', async () => {
    const ctx: { current: NotificationContextValue | null } = { current: null }
    function Spy() {
      ctx.current = useContext(NotificationContext)
      return null
    }

    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test">
        <Spy />
      </NotificationProvider>
    )

    await waitFor(() => expect(ctx.current?.loading).toBe(false))

    act(() => {
      emitToSocketListeners('disconnected', undefined)
    })
    expect(ctx.current?.connectionStatus).toBe('disconnected')

    act(() => {
      emitToSocketListeners('connected', undefined)
    })
    expect(ctx.current?.connectionStatus).toBe('connected')
  })
})

describe('NotificationProvider - coklu sekme senkronu (hidden/read WebSocket olaylari)', () => {
  const makeItem = (id: string, overrides: Partial<NotificationContextValue['notifications'][number]> = {}) => ({
    id,
    classification: 'Baslik',
    message: 'Mesaj',
    classificationEn: null,
    messageEn: null,
    type: 'info',
    priority: 'NORMAL' as const,
    read: false,
    saved: false,
    createdAt: '2026-08-27T10:00:00Z',
    metadata: {},
    sourceDeviceId: null,
    ...overrides,
  })

  beforeEach(() => {
    vi.mocked(fetchNotifications).mockResolvedValue({
      items: [makeItem('n1'), makeItem('n2')],
      hasMore: false,
      nextBefore: null,
    })
    vi.mocked(fetchUnreadCount).mockResolvedValue(2)
  })

  async function renderProviderAndWait() {
    const ctx: { current: NotificationContextValue | null } = { current: null }
    function Spy() {
      ctx.current = useContext(NotificationContext)
      return null
    }
    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test">
        <Spy />
      </NotificationProvider>
    )
    await waitFor(() => expect(ctx.current?.loading).toBe(false))
    return ctx
  }

  it('baska sekmede silinen bildirim icin NOTIFICATION_HIDDEN gelince listeden cikar', async () => {
    const ctx = await renderProviderAndWait()
    expect(ctx.current?.notifications).toHaveLength(2)

    // handleHidden, liste guncellemesinin ardindan rozet sayisini yeniden
    // cekmek icin ayrica async fetchUnreadCount() cagirir - o promise'in
    // act() disinda cozulmesini (ve "not wrapped in act" uyarisini) onlemek
    // icin act bloguna await ile bir microtask ekliyoruz.
    await act(async () => {
      emitToSocketListeners('hidden', ['n1'])
      await Promise.resolve()
    })

    expect(ctx.current?.notifications.map((n) => n.id)).toEqual(['n2'])
  })

  it('baska sekmede tumu silinince NOTIFICATION_ALL_HIDDEN gelince liste ve rozet sifirlanir', async () => {
    const ctx = await renderProviderAndWait()

    act(() => {
      emitToSocketListeners('allHidden', undefined)
    })

    expect(ctx.current?.notifications).toEqual([])
    expect(ctx.current?.unreadCount).toBe(0)
  })

  it('baska sekmede okundu isaretlenen bildirim icin NOTIFICATION_READ gelince read=true olur', async () => {
    const ctx = await renderProviderAndWait()

    await act(async () => {
      emitToSocketListeners('read', ['n1'])
      await Promise.resolve()
    })

    const n1 = ctx.current?.notifications.find((n) => n.id === 'n1')
    expect(n1?.read).toBe(true)
    const n2 = ctx.current?.notifications.find((n) => n.id === 'n2')
    expect(n2?.read).toBe(false)
  })
})

describe('NotificationProvider - sortByPriority (B11, opt-in oncelik siralamasi)', () => {
  beforeEach(() => {
    vi.mocked(fetchUnreadCount).mockResolvedValue(0)
  })

  it('sortByPriority verilmezse istek sort parametresi TASIMAZ (varsayilan davranis degismez)', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue({ items: [], hasMore: false, nextBefore: null })

    const ctx: { current: NotificationContextValue | null } = { current: null }
    function Spy() {
      ctx.current = useContext(NotificationContext)
      return null
    }
    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test">
        <Spy />
      </NotificationProvider>
    )
    await waitFor(() => expect(ctx.current?.loading).toBe(false))

    expect(fetchNotifications).toHaveBeenCalledWith(
      '/api',
      expect.objectContaining({ sort: undefined })
    )
  })

  it('sortByPriority=true iken ilk yukleme sort=priority ile istek atar', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue({
      items: [],
      hasMore: false,
      nextBefore: null,
      nextPriorityCursor: null,
    })

    const ctx: { current: NotificationContextValue | null } = { current: null }
    function Spy() {
      ctx.current = useContext(NotificationContext)
      return null
    }
    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test" sortByPriority>
        <Spy />
      </NotificationProvider>
    )
    await waitFor(() => expect(ctx.current?.loading).toBe(false))

    expect(fetchNotifications).toHaveBeenCalledWith(
      '/api',
      expect.objectContaining({ sort: 'priority' })
    )
  })

  it('sortByPriority=true iken loadMore, before DEGIL bir onceki sayfanin nextPriorityCursor degerini gonderir', async () => {
    vi.mocked(fetchNotifications).mockResolvedValueOnce({
      items: [],
      hasMore: true,
      nextBefore: null,
      nextPriorityCursor: 'cursor-abc',
    })

    const ctx: { current: NotificationContextValue | null } = { current: null }
    function Spy() {
      ctx.current = useContext(NotificationContext)
      return null
    }
    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test" sortByPriority>
        <Spy />
      </NotificationProvider>
    )
    await waitFor(() => expect(ctx.current?.loading).toBe(false))

    vi.mocked(fetchNotifications).mockResolvedValueOnce({
      items: [],
      hasMore: false,
      nextBefore: null,
      nextPriorityCursor: null,
    })

    await act(async () => {
      await ctx.current?.loadMore()
    })

    expect(fetchNotifications).toHaveBeenLastCalledWith(
      '/api',
      expect.objectContaining({ priorityCursor: 'cursor-abc', sort: 'priority' })
    )
  })

  // Provider tema yayar: NotificationBell ve PopupStack ayri ayri render
  // edildigi icin, temayi tek bir yerde soyleyebilmek gerekiyor. Bu olmadan
  // her bilesene ayri ayri theme gecirmek zorunda kalinirdi (ve unutulan
  // biri sessizce acik temada cizilirdi).
  it('theme prop u ThemeContext olarak alt agaca yayar', async () => {
    function ThemeProbe() {
      return <span data-testid="tema">{useTheme()}</span>
    }
    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test" theme="dark">
        <ThemeProbe />
      </NotificationProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('tema')).toHaveTextContent('dark'))
  })
})