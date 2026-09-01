import { render, screen, fireEvent, cleanup } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { PopupStack } from './PopupStack'
import { usePopupQueue, type PopupQueueItem } from '../hooks/usePopupQueue'
import { makeNotification, makeNotificationsMock } from '../test-utils/notificationMocks'
import { useNotifications } from '../hooks/useNotifications'

// usePopupQueue'yu DOGRUDAN sahtelestiriyoruz - PopupStack'e istedigimiz
// kuyrugu elle veriyoruz (kendi testi de bu deseni kullaniyor).
vi.mock('../hooks/usePopupQueue', () => ({ usePopupQueue: vi.fn() }))
// PopupStack, dil tercihini Provider'dan (useNotifications) miras aliyor -
// bu testlerde gercek Provider olmadigi icin onu da sahteliyoruz.
vi.mock('../hooks/useNotifications', () => ({ useNotifications: vi.fn() }))

const dismiss = vi.fn()

function mockQueue(items: PopupQueueItem[]) {
  vi.mocked(usePopupQueue).mockReturnValue({
    items,
    dismiss,
    pauseAutoDismiss: vi.fn(),
    resumeAutoDismiss: vi.fn(),
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(useNotifications).mockReturnValue(makeNotificationsMock())
})

describe('PopupStack', () => {
  it('kuyruk bosken hicbir sey render etmez', () => {
    mockQueue([])
    const { container } = render(<PopupStack />)
    expect(container).toBeEmptyDOMElement()
  })

  it('kuyruktaki her bildirim icin bir kart gosterir (baslik + mesaj)', () => {
    mockQueue([
      { key: 'a', notification: makeNotification({ id: 'a', classification: 'Uyari', message: 'Disk doldu' }) },
    ])
    render(<PopupStack />)

    expect(screen.getByText('Uyari')).toBeInTheDocument()
    expect(screen.getByText('Disk doldu')).toBeInTheDocument()
  })

  it('sourceDeviceId doluysa kart tarihinin yaninda ismi dogrudan (etiketsiz) gosterir', () => {
    mockQueue([
      { key: 'a', notification: makeNotification({ id: 'a', sourceDeviceId: 'PLC-42' }) },
      { key: 'b', notification: makeNotification({ id: 'b', sourceDeviceId: null }) },
    ])
    render(<PopupStack />)

    // document.body uzerinden sorguluyoruz, render'in container'i uzerinden
    // DEGIL: PopupStack artik varsayilan olarak document.body'ye portal
    // ediyor (position:fixed'in, transform'lu bir atanin icine hapsolmasini
    // onlemek icin - bkz. PopupStackProps.container).
    const sources = document.body.querySelectorAll('.notif-popup-card-time-source')
    expect(sources).toHaveLength(1)
    expect(sources[0].textContent).toContain('PLC-42')
    expect(sources[0].textContent).not.toMatch(/Kaynak|Source/)
  })

  it('kapat dugmesi dismiss(key, "user") cagirir', () => {
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a' }) }])
    render(<PopupStack />)

    fireEvent.click(screen.getByLabelText('Bildirimi kapat'))
    expect(dismiss).toHaveBeenCalledWith('a', 'user')
  })

  it('karta tiklayinca onNotificationClick cagrilir', () => {
    const onNotificationClick = vi.fn()
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a', message: 'Tikla bana' }) }])
    render(<PopupStack onNotificationClick={onNotificationClick} />)

    fireEvent.click(screen.getByText('Tikla bana'))
    expect(onNotificationClick).toHaveBeenCalledTimes(1)
    expect(onNotificationClick.mock.calls[0][0]).toMatchObject({ id: 'a' })
  })

  // Regresyon: PopupStack'in theme prop'u YOKKEN, tek basina (NotificationBell
  // disinda) render edildiginde ustunde hicbir ThemeContext.Provider olmadigi
  // icin context varsayilanina ('light') dusuyordu - sayfa koyu tema olsa bile
  // popup HER ZAMAN acik cikiyordu.
  it('tek basina render edilirken theme="dark" koyu paleti uygular', () => {
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a', type: 'info' }) }])
    render(<PopupStack theme="dark" />)

    const card = document.body.querySelector('.notif-popup-card') as HTMLElement
    expect(card).not.toBeNull()
    const darkBg = card.style.getPropertyValue('--notif-card-bg')
    cleanup()

    // Ayni bildirim, acik temada FARKLI bir arka plan almali.
    render(<PopupStack theme="light" />)
    const lightCard = document.body.querySelector('.notif-popup-card') as HTMLElement
    const lightBg = lightCard.style.getPropertyValue('--notif-card-bg')

    expect(darkBg).not.toBe('')
    expect(darkBg).not.toBe(lightBg)
  })

  it('container={null} verilirse portal kullanmaz, bulundugu yere render eder', () => {
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a', classification: 'Yerinde' }) }])
    const { container } = render(<PopupStack container={null} />)

    expect(container.querySelector('.notif-popup-card')).not.toBeNull()
  })

  it('renderPopupCard verilirse kartin gorunumu tamamen ona birakilir', () => {
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a', classification: 'X' }) }])
    render(
      <PopupStack
        renderPopupCard={(n, close) => (
          <div>
            <span>ozel-{n.classification}</span>
            <button onClick={close}>kapat-ozel</button>
          </div>
        )}
      />,
    )

    expect(screen.getByText('ozel-X')).toBeInTheDocument()
    fireEvent.click(screen.getByText('kapat-ozel'))
    expect(dismiss).toHaveBeenCalledWith('a', 'user')
  })

  // Konumlandirma: varsayilan sag-ust (eski davranis), digerleri opt-in.
  const zone = () => document.body.querySelector('.notif-popup-hover-zone') as HTMLElement

  it('varsayilan olarak sag-uste yaslanir', () => {
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a' }) }])
    render(<PopupStack />)

    const st = zone().style
    expect(st.getPropertyValue('--notif-popup-top')).toBe('24px')
    expect(st.getPropertyValue('--notif-popup-right')).toBe('24px')
    // Kullanilmayan kenarlar HIC set edilmemeli - CSS'te auto'ya dussunler.
    expect(st.getPropertyValue('--notif-popup-bottom')).toBe('')
    expect(st.getPropertyValue('--notif-popup-left')).toBe('')
  })

  it('placement="bottom-left" sol-alta yaslar', () => {
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a' }) }])
    render(<PopupStack placement="bottom-left" />)

    const st = zone().style
    expect(st.getPropertyValue('--notif-popup-bottom')).toBe('24px')
    expect(st.getPropertyValue('--notif-popup-left')).toBe('24px')
    expect(st.getPropertyValue('--notif-popup-top')).toBe('')
    expect(st.getPropertyValue('--notif-popup-right')).toBe('')
  })

  it('offsetX / offsetY kenar uzakliklarini ayarlar', () => {
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a' }) }])
    render(<PopupStack placement="top-left" offsetX={8} offsetY={72} />)

    const st = zone().style
    expect(st.getPropertyValue('--notif-popup-top')).toBe('72px')
    expect(st.getPropertyValue('--notif-popup-left')).toBe('8px')
  })

  it('eski topOffset hala calisir, offsetY verilirse o kazanir', () => {
    mockQueue([{ key: 'a', notification: makeNotification({ id: 'a' }) }])
    const { unmount } = render(<PopupStack topOffset={64} />)
    expect(zone().style.getPropertyValue('--notif-popup-top')).toBe('64px')
    unmount()

    render(<PopupStack topOffset={64} offsetY={100} />)
    expect(zone().style.getPropertyValue('--notif-popup-top')).toBe('100px')
  })
})
