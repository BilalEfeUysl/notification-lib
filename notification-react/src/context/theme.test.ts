import { renderHook, act } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useResolvedTheme } from './theme'

// jsdom matchMedia'yi desteklemiyor - "sahte" bir MediaQueryList yaziyoruz.
// changeListeners: testin icinden "sistem temasi degisti" olayini elle
// tetikleyebilmemiz icin dinleyicileri saklıyoruz.
function createMockMediaQueryList(initialMatches: boolean) {
  let matches = initialMatches
  const changeListeners: Array<(e: { matches: boolean }) => void> = []
  return {
    get matches() {
      return matches
    },
    addEventListener: vi.fn((_event: string, listener: (e: { matches: boolean }) => void) => {
      changeListeners.push(listener)
    }),
    removeEventListener: vi.fn((_event: string, listener: (e: { matches: boolean }) => void) => {
      const i = changeListeners.indexOf(listener)
      if (i !== -1) changeListeners.splice(i, 1)
    }),
    // Test yardimcisi - gercek matchMedia API'sinin parcasi degil.
    simulateChange(newMatches: boolean) {
      matches = newMatches
      changeListeners.forEach((l) => l({ matches: newMatches }))
    },
  }
}

describe('useResolvedTheme', () => {
  let mockMql: ReturnType<typeof createMockMediaQueryList>

  beforeEach(() => {
    mockMql = createMockMediaQueryList(false) // varsayilan: sistem light
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mockMql))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('theme="light" verilirse sistem ne olursa olsun HER ZAMAN light doner', () => {
    mockMql = createMockMediaQueryList(true) // sistem dark olsa bile
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mockMql))

    const { result } = renderHook(() => useResolvedTheme('light'))
    expect(result.current).toBe('light')
  })

  it('theme="dark" verilirse sistem ne olursa olsun HER ZAMAN dark doner', () => {
    // sistem light olsa bile
    const { result } = renderHook(() => useResolvedTheme('dark'))
    expect(result.current).toBe('dark')
  })

  it('theme="auto" iken baslangicta sistemin GUNCEL tercihini okur', () => {
    mockMql = createMockMediaQueryList(true) // sistem dark
    vi.stubGlobal('matchMedia', vi.fn().mockReturnValue(mockMql))

    const { result } = renderHook(() => useResolvedTheme('auto'))
    expect(result.current).toBe('dark')
  })

  it('theme="auto" iken sistem teması SONRADAN degisirse CANLI guncellenir', () => {
    // baslangicta sistem light
    const { result } = renderHook(() => useResolvedTheme('auto'))
    expect(result.current).toBe('light')

    // kullanici isletim sisteminde temayi dark'a cevirdi
    act(() => {
      mockMql.simulateChange(true)
    })

    expect(result.current).toBe('dark')
  })

  it('theme="light"/"dark" iken sistem degisikligi dinlenmez (addEventListener cagrilmaz)', () => {
    renderHook(() => useResolvedTheme('light'))
    expect(mockMql.addEventListener).not.toHaveBeenCalled()
  })
})