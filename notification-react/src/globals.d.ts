/**
 * Build sırasında Vite `define` ile paketin gerçek sürümüyle (package.json
 * `version`) değiştirilir. Test/dev ortamında (define yok) tanımsız kalır;
 * index.ts bu durumda `'0.0.0-dev'`'e düşer.
 */
declare const __LIB_VERSION__: string | undefined;
