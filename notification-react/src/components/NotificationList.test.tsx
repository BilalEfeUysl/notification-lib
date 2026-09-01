import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { NotificationList } from './NotificationList'
import { useNotifications } from '../hooks/useNotifications'
import { makeNotification, makeNotificationsMock } from '../test-utils/notificationMocks'

vi.mock('../hooks/useNotifications', () => ({ useNotifications: vi.fn() }))

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock())
})

describe('NotificationList', () => {
  it('bildirimleri role="list" / role="listitem" ile listeler (O6)', () => {
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({
        notifications: [
          makeNotification({ id: 'a', classification: 'Birinci' }),
          makeNotification({ id: 'b', classification: 'Ikinci' }),
        ],
      }),
    )
    render(<NotificationList />)

    expect(screen.getByRole('list')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText('Birinci')).toBeInTheDocument()
    expect(screen.getByText('Ikinci')).toBeInTheDocument()
  })

  it('bildirim yokken bos durum metnini role="status" ile gosterir', () => {
    render(<NotificationList />)
    expect(screen.getByRole('status')).toHaveTextContent('Hiç bildirim yok')
  })

  it('language="en" iken Ingilizce baslik/mesaj gosterir, yoksa varsayilana duser', () => {
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({
        notifications: [
          makeNotification({
            id: 'a',
            classification: 'Bakim',
            message: 'Sistem kapanacak',
            classificationEn: 'Maintenance',
            messageEn: 'System goes down',
          }),
          makeNotification({ id: 'b', classification: 'Sadece TR', message: 'ingilizcesi yok' }),
        ],
      }),
    )
    render(<NotificationList language="en" />)

    expect(screen.getByText('Maintenance')).toBeInTheDocument()
    expect(screen.getByText('System goes down')).toBeInTheDocument()
    expect(screen.queryByText('Bakim')).not.toBeInTheDocument()
    // Ingilizcesi olmayan bildirim varsayilan (TR) metniyle gorunur
    expect(screen.getByText('Sadece TR')).toBeInTheDocument()
  })

  it('overrideNotifications verildiginde context listesi yerine onu gosterir', () => {
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({
        notifications: [makeNotification({ id: 'ctx', classification: 'ContextItem' })],
      }),
    )
    render(
      <NotificationList
        overrideNotifications={[makeNotification({ id: 'ov', classification: 'OverrideItem' })]}
      />,
    )

    expect(screen.getByText('OverrideItem')).toBeInTheDocument()
    expect(screen.queryByText('ContextItem')).not.toBeInTheDocument()
  })

  it('bir bildirime tiklayinca onNotificationClick cagrilir', () => {
    const onNotificationClick = vi.fn()
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({ notifications: [makeNotification({ id: 'a', classification: 'Tikla' })] }),
    )
    render(<NotificationList onNotificationClick={onNotificationClick} />)

    fireEvent.click(screen.getByText('Tikla'))
    expect(onNotificationClick).toHaveBeenCalledTimes(1)
    expect(onNotificationClick.mock.calls[0][0]).toMatchObject({ id: 'a' })
  })

  it('secim modunda her ogede bir checkbox gosterir', () => {
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({
        notifications: [makeNotification({ id: 'a' }), makeNotification({ id: 'b' })],
      }),
    )
    render(<NotificationList selectionMode selectedIds={new Set(['a'])} onToggleSelect={vi.fn()} />)

    const checkboxes = screen.getAllByRole('checkbox')
    expect(checkboxes).toHaveLength(2)
    expect(checkboxes[0]).toHaveAttribute('aria-checked', 'true')
    expect(checkboxes[1]).toHaveAttribute('aria-checked', 'false')
  })

  it('silme onayindan sonra context.hide ve onAfterDelete cagrilir', async () => {
    const hide = vi.fn().mockResolvedValue(undefined)
    const onAfterDelete = vi.fn()
    vi.mocked(useNotifications).mockReturnValue(
      makeNotificationsMock({ notifications: [makeNotification({ id: 'a', classification: 'Sil' })], hide }),
    )
    render(<NotificationList onAfterDelete={onAfterDelete} />)

    fireEvent.click(screen.getByLabelText('Bildirimi sil'))
    fireEvent.click(await screen.findByText('Evet'))

    await waitFor(() => expect(hide).toHaveBeenCalledWith('a'))
    await waitFor(() => expect(onAfterDelete).toHaveBeenCalledWith('a'))
  })
})
