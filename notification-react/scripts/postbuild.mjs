// Build sonrasi adimi.
//
// vite-plugin-dts sadece index.d.ts (ESM tipleri) uretiyor. Ama paket hem
// ESM hem CJS yayinliyor; package.json exports'ta "require" kosulu ayri bir
// .d.cts tip dosyasi bekliyor (yoksa CJS ile require eden bir TS projesi
// tipleri yanlis - ESM - cozer; publint bunu hata sayiyor).
// Icerik ikisinde de birebir ayni oldugu icin index.d.ts'i kopyalamak yeterli.
import { copyFileSync, existsSync } from 'node:fs';

const src = 'dist/index.d.ts';
const dest = 'dist/index.d.cts';

if (!existsSync(src)) {
  console.error(`postbuild: ${src} bulunamadi - once "vite build" calismali.`);
  process.exit(1);
}
copyFileSync(src, dest);
console.log(`postbuild: ${dest} olusturuldu (${src} kopyasi).`);
