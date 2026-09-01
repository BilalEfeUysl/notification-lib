import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  NotificationSocket,
  MAX_RECONNECT_ATTEMPTS,
  MAX_RECONNECT_DELAY_MS,
} from './NotificationSocket'

// Gercek tarayici WebSocket'inin yerine gececek sahte sinif. Testten
// disaridan "sunucu su an baglandi/koptu/mesaj gonderdi" diye elle
// tetikleyebilmemiz icin trigger* metodlari ekliyoruz.
class MockWebSocket {
  static instances: MockWebSocket[] = []
  static readonly CONNECTING = 0
  static readonly OPEN = 1

  url: string
  onopen: (() => void) | null = null
  onmessage: ((e: { data: string }) => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  readyState = MockWebSocket.CONNECTING
  private openListeners: Array<() => void> = []

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }

  send() {}

  // Gercek WebSocket'in addEventListener'ini taklit ediyoruz - simdilik
  // sadece 'open' olayini destekliyoruz, kodun ihtiyaci olan tek olay bu.
  addEventListener(event: string, listener: () => void) {
    if (event === 'open') this.openListeners.push(listener)
  }
  removeEventListener(event: string, listener: () => void) {
    if (event === 'open') {
      this.openListeners = this.openListeners.filter((l) => l !== listener)
    }
  }

  close() {
    this.onclose?.()
  }

  triggerOpen() {
    this.readyState = MockWebSocket.OPEN
    this.onopen?.()
    this.openListeners.forEach((l) => l())
  }
  triggerMessage(data: string) {
    this.onmessage?.({ data })
  }
  triggerClose() {
    this.onclose?.()
  }
}

describe('NotificationSocket - yeniden baglanma', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    MockWebSocket.instances = []
    // NotificationSocket icindeki `new WebSocket(...)` cagrisi artik
    // bizim sahte sinifimizi kullansin diye global'i degistiriyoruz.
    vi.stubGlobal('WebSocket', MockWebSocket)
    // Jitter icin Math.random varsayilan olarak 1 (tam gecikme) - asagidaki
    // zamanlama testleri deterministik kalsin. Jitter'i ayrica test eden
    // testler bunu kendileri degistiriyor.
    vi.spyOn(Math, 'random').mockReturnValue(1)
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('beklenmedik kopma sonrasi 1000ms sonra otomatik yeniden baglanmayi dener', () => {
    const socket = new NotificationSocket('ws://test')
    socket.connect()
    expect(MockWebSocket.instances).toHaveLength(1)

    MockWebSocket.instances[0].triggerClose()

    // Zaman henuz ilerlemedi - yeni baglanti ACILMAMIS olmali.
    expect(MockWebSocket.instances).toHaveLength(1)

    vi.advanceTimersByTime(1000)

    expect(MockWebSocket.instances).toHaveLength(2)
  })

  it('ust uste basarisiz denemelerde gecikme ikiye katlanir', () => {
    const socket = new NotificationSocket('ws://test')
    socket.connect()

    MockWebSocket.instances[0].triggerClose()
    vi.advanceTimersByTime(1000) // ilk deneme: 1000ms
    expect(MockWebSocket.instances).toHaveLength(2)

    MockWebSocket.instances[1].triggerClose()
    vi.advanceTimersByTime(1999)
    expect(MockWebSocket.instances).toHaveLength(2) // henuz 2000ms dolmadi
    vi.advanceTimersByTime(1)
    expect(MockWebSocket.instances).toHaveLength(3) // simdi 2000ms doldu
  })

  it('basarili baglanti (onopen) gecikmeyi baslangic degerine sifirlar', () => {
    const socket = new NotificationSocket('ws://test')
    socket.connect()

    // Ilk iki deneme basarisiz olsun, gecikme 1000 -> 2000 -> 4000'e ciksin.
    MockWebSocket.instances[0].triggerClose()
    vi.advanceTimersByTime(1000)
    MockWebSocket.instances[1].triggerClose()
    vi.advanceTimersByTime(2000)
    expect(MockWebSocket.instances).toHaveLength(3)

    // 3. baglanti bu sefer BASARILI olsun, sonra yine kopsun.
    MockWebSocket.instances[2].triggerOpen()
    MockWebSocket.instances[2].triggerClose()

    // Basarili baglanti gecikmeyi sifirladiysa, sonraki deneme yine
    // BASLANGIC gecikmesiyle (1000ms) olmali - 4000ms degil.
    vi.advanceTimersByTime(999)
    expect(MockWebSocket.instances).toHaveLength(3) // henuz 1000ms dolmadi
    vi.advanceTimersByTime(1)
    expect(MockWebSocket.instances).toHaveLength(4) // 1000ms'de geldi
  })

  it('disconnect() sonrasi hic yeniden baglanma denenmez', () => {
    const socket = new NotificationSocket('ws://test')
    socket.connect()
    expect(MockWebSocket.instances).toHaveLength(1)

    socket.disconnect()

    vi.advanceTimersByTime(60000) // zaman ne kadar ilerlerse ilerlesin
    expect(MockWebSocket.instances).toHaveLength(1)
  })

  it('NOTIFICATION_CREATED mesaji gelince notification olayi payload ile yayinlanir', () => {
    const socket = new NotificationSocket('ws://test')
    const handler = vi.fn()
    socket.on('notification', handler)
    socket.connect()

    const payload = { id: 'n1', message: 'test' }
    MockWebSocket.instances[0].triggerMessage(
      JSON.stringify({ event: 'NOTIFICATION_CREATED', payload })
    )

    expect(handler).toHaveBeenCalledWith(payload)
  })

  it('NOTIFICATION_HIDDEN mesaji gelince hidden olayi id listesiyle yayinlanir', () => {
    const socket = new NotificationSocket('ws://test')
    const handler = vi.fn()
    socket.on('hidden', handler)
    socket.connect()

    MockWebSocket.instances[0].triggerMessage(
      JSON.stringify({ event: 'NOTIFICATION_HIDDEN', payload: { ids: ['a1', 'a2'] } })
    )

    expect(handler).toHaveBeenCalledWith(['a1', 'a2'])
  })

  it('NOTIFICATION_ALL_HIDDEN mesaji gelince allHidden olayi yayinlanir', () => {
    const socket = new NotificationSocket('ws://test')
    const handler = vi.fn()
    socket.on('allHidden', handler)
    socket.connect()

    MockWebSocket.instances[0].triggerMessage(JSON.stringify({ event: 'NOTIFICATION_ALL_HIDDEN' }))

    // CustomEvent'in detail'i tarayicida (jsdom dahil) undefined verilse bile
    // null'a normallesir - NotificationProvider'daki handleAllHidden zaten bu
    // degeri hic okumadigi icin bu zararsiz, ama testte gercek deger budur.
    expect(handler).toHaveBeenCalledWith(null)
  })

  it('NOTIFICATION_READ mesaji gelince read olayi id listesiyle yayinlanir', () => {
    const socket = new NotificationSocket('ws://test')
    const handler = vi.fn()
    socket.on('read', handler)
    socket.connect()

    MockWebSocket.instances[0].triggerMessage(
      JSON.stringify({ event: 'NOTIFICATION_READ', payload: { ids: ['b1'] } })
    )

    expect(handler).toHaveBeenCalledWith(['b1'])
  })

  it('eski (degistirilmis) socket tekrar olay tetiklerse gormezden gelinir', () => {
    const socket = new NotificationSocket('ws://test')
    socket.connect()
    const ws1 = MockWebSocket.instances[0]

    ws1.triggerClose() // beklenmedik kopma, yeniden baglanma planlanir
    vi.advanceTimersByTime(1000)
    expect(MockWebSocket.instances).toHaveLength(2) // ws2 acildi

    // ws1 artik ESKI. Onun close'u yanlislikla TEKRAR tetiklenirse
    // (gercekte olabilir - ag katmani gec bir olay yollayabilir),
    // fazladan bir yeniden-baglanma DENEMESI PLANLANMAMALI.
    ws1.triggerClose()
    vi.advanceTimersByTime(1000)

    expect(MockWebSocket.instances).toHaveLength(2)
  })

  it('jitter: Math.random=0 iken gecikme tam degerin yarisina iner', () => {
    vi.spyOn(Math, 'random').mockReturnValue(0)

    const socket = new NotificationSocket('ws://test')
    socket.connect()
    MockWebSocket.instances[0].triggerClose()

    // 1000ms taban * (0.5 + 0*0.5) = 500ms
    vi.advanceTimersByTime(499)
    expect(MockWebSocket.instances).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(MockWebSocket.instances).toHaveLength(2)
  })

  it('MAX_RECONNECT_ATTEMPTS asilinca otomatik denemeler durur, "online" ile tekrar baslar', () => {
    const socket = new NotificationSocket('ws://test')
    socket.connect()

    // Her kopmada zamani sonuna kadar ilerlet: her seferinde bir yeni deneme.
    for (let i = 0; i < MAX_RECONNECT_ATTEMPTS; i++) {
      MockWebSocket.instances[MockWebSocket.instances.length - 1].triggerClose()
      vi.advanceTimersByTime(MAX_RECONNECT_DELAY_MS)
    }
    const afterLimit = MAX_RECONNECT_ATTEMPTS + 1 // 1 ilk baglanti + N deneme
    expect(MockWebSocket.instances).toHaveLength(afterLimit)

    // Sinir asildi: artik yeni deneme PLANLANMAMALI.
    MockWebSocket.instances[afterLimit - 1].triggerClose()
    vi.advanceTimersByTime(MAX_RECONNECT_DELAY_MS * 5)
    expect(MockWebSocket.instances).toHaveLength(afterLimit)

    // Ag geri gelince (online olayi) tek seferlik yeniden baglanma tetiklenir.
    window.dispatchEvent(new Event('online'))
    expect(MockWebSocket.instances).toHaveLength(afterLimit + 1)
  })

  it('disconnect() sonrasi "online" olayi yeniden baglanmayi tetiklemez', () => {
    const socket = new NotificationSocket('ws://test')
    socket.connect()
    for (let i = 0; i < MAX_RECONNECT_ATTEMPTS; i++) {
      MockWebSocket.instances[MockWebSocket.instances.length - 1].triggerClose()
      vi.advanceTimersByTime(MAX_RECONNECT_DELAY_MS)
    }
    socket.disconnect()

    window.dispatchEvent(new Event('online'))
    vi.advanceTimersByTime(60000)
    expect(MockWebSocket.instances).toHaveLength(MAX_RECONNECT_ATTEMPTS + 1)
  })
})