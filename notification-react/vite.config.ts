import { defineConfig } from 'vite';
import dts from 'vite-plugin-dts';

// Library mode: uygulama degil, baskalarinin import edecegi bir paket uretiyoruz.
export default defineConfig({
  plugins: [dts({ include: ['src'], rollupTypes: true })],
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
