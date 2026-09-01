import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { NotificationPanel } from './NotificationPanel'
import { useNotifications } from '../hooks/useNotifications'
import { makeNotification, makeNotificationsMock } from '../test-utils/notificationMocks'

vi.mock('../hooks/useNotifications', () => ({ useNotifications: vi.fn() }))

const noop = () => {}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock())
})

describe('NotificationPanel', () => {
  it('baslik ve context listesindeki bildirimleri gosterir', () => {
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({
        notifications: [makeNotification({ id: 'a', classification: 'Bakim' })],
      }),
    )
    render(<NotificationPanel onClearAll={noop} />)

    expect(screen.getByText('Bildirimler')).toBeInTheDocument()
    expect(screen.getByText('Bakim')).toBeInTheDocument()
  })

  it('bağlantı koptuğunda uyarı satırı gösterilir', () => {
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({ connectionStatus: 'disconnected' }),
    )
    render(<NotificationPanel onClearAll={noop} />)

    expect(screen.getByText(/Bağlantı koptu/)).toBeInTheDocument()
  })

  it('arama ikonuna basınca arama kutusu açılır ve yerel olarak filtreler', () => {
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({
        notifications: [
          makeNotification({ id: 'a', classification: 'Bakim penceresi' }),
          makeNotification({ id: 'b', classification: 'Yeni surum' }),
        ],
      }),
    )
    render(<NotificationPanel onClearAll={noop} />)

    fireEvent.click(screen.getByLabelText('Bildirimlerde ara'))
    const input = screen.getByPlaceholderText('Ara...')
    fireEvent.change(input, { target: { value: 'bakim' } })

    expect(screen.getByText('Bakim penceresi')).toBeInTheDocument()
    expect(screen.queryByText('Yeni surum')).not.toBeInTheDocument()
  })

  it('"Kaydedilenleri göster" fetchSaved çağırır ve kayıtlı görünüme geçer', async () => {
    const fetchSaved = vi
      .fn()
      .mockResolvedValue({ items: [makeNotification({ id: 's', classification: 'Kayitli olan' })], hasMore: false, nextBefore: null })
    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({ fetchSaved }))
    render(<NotificationPanel onClearAll={noop} />)

    fireEvent.click(screen.getByLabelText('Kaydedilenleri göster'))

    expect(fetchSaved).toHaveBeenCalledTimes(1)
    expect(await screen.findByText('Kayitli olan')).toBeInTheDocument()
    expect(screen.getByText('Kaydedilenler')).toBeInTheDocument()
  })

  it('ses ikonuna basınca toggleSound çağrılır', () => {
    const toggleSound = vi.fn()
    vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock({ toggleSound }))
    render(<NotificationPanel onClearAll={noop} />)

    fireEvent.click(screen.getByLabelText('Sesi kapat'))
    expect(toggleSound).toHaveBeenCalledTimes(1)
  })

  it('"Tümünü temizle" seçim moduna geçirir (checkbox\'lar görünür)', async () => {
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({
        notifications: [makeNotification({ id: 'a' }), makeNotification({ id: 'b' })],
      }),
    )
    render(<NotificationPanel onClearAll={noop} />)

    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
    fireEvent.click(screen.getByLabelText('Tümünü temizle'))

    await waitFor(() => expect(screen.getAllByRole('checkbox')).toHaveLength(2))
  })
})
