import '@testing-library/jest-dom/vitest'

// React'e "act(...) kurallarini destekleyen bir test ortamindayiz" diye
// haber veriyoruz - yoksa React state guncellemelerinde gereksiz
// "not configured to support act" uyarilari basiyor.
;(globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true