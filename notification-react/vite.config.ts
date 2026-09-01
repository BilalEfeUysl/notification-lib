import { readFileSync } from 'node:fs';
import { defineConfig } from 'vite';
import dts from 'vite-plugin-dts';

// Paket surumunu tek kaynaktan (package.json) al; NOTIFICATION_LIB_VERSION
// build sirasinda bununla degistirilir (elle yazilan sabit kaymasin diye).
const { version } = JSON.parse(
  readFileSync(new URL('./package.json', import.meta.url), 'utf8'),
) as { version: string };

// Library mode: uygulama degil, baskalarinin import edecegi bir paket uretiyoruz.
export default defineConfig({
  plugins: [dts({ include: ['src'], rollupTypes: true })],
  define: {
    __LIB_VERSION__: JSON.stringify(version),
  },
  build: {
    lib: {
      entry: 'src/index.ts',
      name: 'NotificationReact',
      fileName: (format) => (format === 'es' ? 'index.js' : 'index.cjs'),
      formats: ['es', 'cjs'],
    },
    rollupOptions: {
      // Bunlar pakete GOMULMEZ; kullanan proje kendi kopyasini saglar.
      // Aksi halde projede iki React olur ve hook'lar calismaz.
      external: ['react', 'react-dom', 'react/jsx-runtime', 'antd', '@ant-design/icons'],
      output: {
        globals: {
          react: 'React',
          'react-dom': 'ReactDOM',
          antd: 'antd',
        },
      },
    },
    sourcemap: true,
  },
});
