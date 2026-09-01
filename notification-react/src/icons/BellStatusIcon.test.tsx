import { render } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { BellStatusIcon } from './BellStatusIcon'

// Can her zaman 2 path (govde + tokmak). Yaylar 4 path daha ekler,
// capraz cizgi bir <line>.
describe('BellStatusIcon', () => {
  it('ses acikken canin yaninda titresim yaylarini cizer', () => {
    const { container } = render(<BellStatusIcon sound popups />)
    expect(container.querySelectorAll('path')).toHaveLength(6)
    expect(container.querySelector('line')).toBeNull()
  })

  it('sessiz modda yaylari cizmez', () => {
    const { container } = render(<BellStatusIcon sound={false} popups />)
    expect(container.querySelectorAll('path')).toHaveLength(2)
    expect(container.querySelector('line')).toBeNull()
  })

  it('bildirimler kapaliyken can uzerine capraz cizgi ekler', () => {
    const { container } = render(<BellStatusIcon sound popups={false} />)
    expect(container.querySelector('line')).not.toBeNull()
  })

  it('ikisi de kapaliyken cizgi var, yay yok', () => {
    const { container } = render(<BellStatusIcon sound={false} popups={false} />)
    expect(container.querySelectorAll('path')).toHaveLength(2)
    expect(container.querySelector('line')).not.toBeNull()
  })
})
