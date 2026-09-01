// Kod ile uretilen kisa bir "ding" sesi (Web Audio API) - ses dosyasi
// gerekmez. Tarayicinin otomatik-oynatma kisiti yuzunden, sayfa hic
// tiklanmadan gelen ilk bildirim sessiz kalabilir (tarayici davranisi).

let audioContext: AudioContext | null = null;

function getAudioContext(): AudioContext | null {
  if (typeof window === 'undefined') return null;
  const AudioContextClass = window.AudioContext ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioContextClass) return null;
  if (!audioContext) {
    audioContext = new AudioContextClass();
  }
  return audioContext;
}

export function playNotificationSound(): void {
  const ctx = getAudioContext();
  if (!ctx) return;
  if (ctx.state === 'suspended') {
    ctx.resume().catch(() => {});
  }

  const oscillator = ctx.createOscillator();
  const gain = ctx.createGain();

  oscillator.type = 'sine';
  oscillator.frequency.setValueAtTime(880, ctx.currentTime);

  gain.gain.setValueAtTime(0, ctx.currentTime);
  gain.gain.linearRampToValueAtTime(0.25, ctx.currentTime + 0.01);
  gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35);

  oscillator.connect(gain);
  gain.connect(ctx.destination);

  oscillator.start(ctx.currentTime);
  oscillator.stop(ctx.currentTime + 0.35);
}