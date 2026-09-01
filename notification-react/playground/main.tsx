import ReactDOM from 'react-dom/client';
import 'antd/dist/antd.css';
import { App } from './App';

// StrictMode BILEREK kaldirildi - sadece bu playground/test araci icin.
// Kutuphanenin kendisi StrictMode ile tam uyumlu (dinleyici sizintisi hatasi
// daha once bulunup duzeltildi), ama StrictMode'un efektleri iki kere
// calistirmasi, WebSocket'i acar-acmaz-kapatip-tekrar-acmasina ve bunun
// tarayici tarafindan (susturulamayan) bir "baglanti kurulmadan kapatildi"
// hatasi olarak loglanmasina yol aciyor - test sirasinda gereksiz gurultu.
ReactDOM.createRoot(document.getElementById('root')!).render(<App />);