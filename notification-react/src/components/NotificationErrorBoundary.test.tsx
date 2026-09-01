import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { NotificationErrorBoundary } from './NotificationErrorBoundary'

// Testin icinden "hata firlat" ya da "firlatma" diye kontrol edebilecegimiz
// kucuk bir sahte component.
function Bomb({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) {
    throw new Error('kasitli test hatasi')
  }
  return <div>calisiyor</div>
}

describe('NotificationErrorBoundary', () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    // React, yakalanan hatalari KENDI mekanizmasindan BAGIMSIZ olarak
    // ayrica console.error'a da yazar - test cikisini kirletmesin diye
    // bu sure icin sessize aliyoruz.
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleErrorSpy.mockRestore()
  })

  it('hata YOKSA cocugu oldugu gibi gosterir', () => {
    render(
      <NotificationErrorBoundary>
        <Bomb shouldThrow={false} />
      </NotificationErrorBoundary>
    )

    expect(screen.getByText('calisiyor')).toBeInTheDocument()
  })

  it('cocuk hata firlatirsa fallback\'i gosterir, cocugu GOSTERMEZ', () => {
    render(
      <NotificationErrorBoundary fallback={<div>bir sorun oldu</div>}>
        <Bomb shouldThrow={true} />
      </NotificationErrorBoundary>
    )

    expect(screen.getByText('bir sorun oldu')).toBeInTheDocument()
    expect(screen.queryByText('calisiyor')).not.toBeInTheDocument()
  })

  it('hata yakalandiginda onError, firlatilan hatayla cagirilir', () => {
    const onError = vi.fn()

    render(
      <NotificationErrorBoundary onError={onError}>
        <Bomb shouldThrow={true} />
      </NotificationErrorBoundary>
    )

    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError.mock.calls[0][0]).toBeInstanceOf(Error)
    expect(onError.mock.calls[0][0].message).toBe('kasitli test hatasi')
  })

  it('fallback verilmezse, hata aninda hicbir sey render ETMEZ (cokmez)', () => {
    const { container } = render(
      <NotificationErrorBoundary>
        <Bomb shouldThrow={true} />
      </NotificationErrorBoundary>
    )

    expect(container).toBeEmptyDOMElement()
  })
})