import { render, screen, waitFor, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createRef } from 'react'
import { NotificationProvider } from '../lib/NotificationProvider'
import { NotificationBell, type NotificationBellHandle } from './NotificationBell'
import { fetchNotifications, fetchUnreadCount } from '../lib/restClient'
import { makeNotification } from '../test-utils/notificationMocks'

// Gercek network ve WebSocket olmadan calissin diye ikisini de sahteliyoruz -
// NotificationProvider.test.tsx ile ayni desen.
vi.mock('../lib/restClient', () => ({
  fetchNotifications: vi.fn(),
  fetchUnreadCount: vi.fn(),
  hideAllNotifications: vi.fn(),
  hideNotification: vi.fn(),
  markNotificationsAsRead: vi.fn(),
  setNotificationSaved: vi.fn(),
}))

vi.mock('../lib/NotificationSocket', () => ({
  NotificationSocket: vi.fn().mockImplementation(() => ({
    on: vi.fn(),
    off: vi.fn(),
    connect: vi.fn(),
    disconnect: vi.fn(),
  })),
}))

function renderBell(props: Partial<React.ComponentProps<typeof NotificationBell>> = {}) {
  return render(
    <NotificationProvider basePath="/api" websocketUrl="ws://test">
      <NotificationBell {...props} />
    </NotificationProvider>,
  )
}

// Zil tetikleyicisi. aria-expanded, panelin acik olup olmadigini yansitir
// (antd Popover kapaninca icerigi DOM'dan silmedigi icin metne bakmak
// guvenilir degil - aria-expanded dogrudan panelOpen'a bagli).
const bell = () => screen.getByRole('button', { name: /okunmamış/ })

describe('NotificationBell - ref ve kontrollu panel (O4)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(fetchNotifications).mockResolvedValue({ items: [], hasMore: false, nextBefore: null })
    vi.mocked(fetchUnreadCount).mockResolvedValue(0)
  })

  it('ref.current.open() / close() paneli acip kapatir', async () => {
    const ref = createRef<NotificationBellHandle>()
    renderBell({ ref })
    await waitFor(() => expect(fetchNotifications).toHaveBeenCalled())

    expect(bell()).toHaveAttribute('aria-expanded', 'false')

    act(() => ref.current!.open())
    await waitFor(() => expect(bell()).toHaveAttribute('aria-expanded', 'true'))

    act(() => ref.current!.close())
    await waitFor(() => expect(bell()).toHaveAttribute('aria-expanded', 'false'))
  })

  it('ref.current.toggle() her cagrida durumu tersine cevirir', async () => {
    const ref = createRef<NotificationBellHandle>()
    renderBell({ ref })
    await waitFor(() => expect(fetchNotifications).toHaveBeenCalled())

    act(() => ref.current!.toggle())
    await waitFor(() => expect(bell()).toHaveAttribute('aria-expanded', 'true'))

    act(() => ref.current!.toggle())
    await waitFor(() => expect(bell()).toHaveAttribute('aria-expanded', 'false'))
  })

  it('kontrollu mod: panelin durumunu open prop u belirler', async () => {
    const onOpenChange = vi.fn()
    const { rerender } = render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test">
        <NotificationBell open={false} onOpenChange={onOpenChange} />
      </NotificationProvider>,
    )
    await waitFor(() => expect(fetchNotifications).toHaveBeenCalled())

    expect(bell()).toHaveAttribute('aria-expanded', 'false')

    rerender(
      <NotificationProvider basePath="/api" websocketUrl="ws://test">
        <NotificationBell open onOpenChange={onOpenChange} />
      </NotificationProvider>,
    )
    await waitFor(() => expect(bell()).toHaveAttribute('aria-expanded', 'true'))
  })

  it('kontrollu modda ref.open() paneli kendiliginden acmaz, sadece onOpenChange tetikler', async () => {
    const onOpenChange = vi.fn()
    const ref = createRef<NotificationBellHandle>()
    render(
      <NotificationProvider basePath="/api" websocketUrl="ws://test">
        <NotificationBell ref={ref} open={false} onOpenChange={onOpenChange} />
      </NotificationProvider>,
    )
    await waitFor(() => expect(fetchNotifications).toHaveBeenCalled())

    act(() => ref.current!.open())

    expect(onOpenChange).toHaveBeenCalledWith(true)
    // parent open i guncellemedigi icin panel KAPALI kalir.
    expect(bell()).toHaveAttribute('aria-expanded', 'false')
  })

  // popup yiginini agacin baska bir yerinde kendin konumlandirabilmek icin
  // zil'in KENDI PopupStack'ini kapatabilmek gerekiyor - kapatilamazsa,
  // ayrica render edilen bir <PopupStack /> ile birlikte ekranda IKI kuyruk
  // olusur ve her bildirim CIFT gorunur.
  // Kuyrugun DOLU olmasi sart: bos kuyrukla yapilan "render etmedi" iddiasi
  // hicbir sey kanitlamaz (PopupStack bos kuyrukta zaten null doner).
  // Acilis toplu gosterimi SADECE okunmamis bildirimleri popup yapar.
  const unread = {
    items: [makeNotification({ id: 'n1', classification: 'Uyari', message: 'Disk doldu', read: false })],
    hasMore: false,
    nextBefore: null,
  }

  // Zil ARTIK kendi popup yiginini render ETMIYOR - popup'lar ayri bir
  // <PopupStack /> ile konumlandiriliyor. Boylece zil'i iki kere render
  // etmek ya da kosullu gizlemek popup'lari bozmuyor.
  it('zil kendi basina popup yigini render etmez', async () => {
    vi.mocked(fetchNotifications).mockResolvedValue(unread)
    renderBell()

    await waitFor(() => expect(fetchNotifications).toHaveBeenCalled())
    await new Promise((r) => setTimeout(r, 50))

    expect(document.body.querySelector('.notif-popup-hover-zone')).toBeNull()
  })

  // Regresyon: panel ve sag-tik menusu antd'nin varsayilanina (tetikleyicinin
  // ebeveyni) birakilirsa, transform'lu / overflow:hidden bir navbar icinde
  // yanlis konumlanip kirpiliyordu. Varsayilan artik document.body.
  it('panel varsayilan olarak document.body altina render edilir', async () => {
    renderBell()
    await waitFor(() => expect(fetchNotifications).toHaveBeenCalled())

    act(() => {
      bell().click()
    })

    await waitFor(() => {
      const panel = document.body.querySelector('.ant-popover')
      expect(panel).not.toBeNull()
      // Panel, RTL'in render container'inin ICINDE olmamali - body'nin
      // dogrudan altinda bir portal'da olmali.
      expect(panel!.closest('.notif-bell-context-wrapper')).toBeNull()
    })
  })

  it('panel.getPopupContainer ile hedef degistirilebilir', async () => {
    const custom = document.createElement('div')
    custom.id = 'ozel-hedef'
    document.body.appendChild(custom)

    renderBell({ panel: { getPopupContainer: () => custom } })
    await waitFor(() => expect(fetchNotifications).toHaveBeenCalled())

    act(() => {
      bell().click()
    })

    await waitFor(() => expect(custom.querySelector('.ant-popover')).not.toBeNull())
    custom.remove()
  })
})
