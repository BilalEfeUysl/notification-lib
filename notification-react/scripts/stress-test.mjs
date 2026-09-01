// stress-test.mjs
// Node ile calistir: node stress-test.mjs
// CORS kisitlamasi olmadigi icin dogrudan backend'e istek atabiliyoruz.

const BURST_SIZE = 300;
const DELAY_MS = 15;
const types = ['info', 'success', 'warning', 'error'];

let sent = 0;
let failed = 0;

console.log(`Stres testi basliyor: ${BURST_SIZE} bildirim, aralarinda ${DELAY_MS}ms...`);

for (let i = 0; i < BURST_SIZE; i++) {
  const body = new URLSearchParams({
    classification: `Stres Testi ${i + 1}`,
    message: `Bu ${i + 1}. otomatik test bildirimidir`,
    type: types[i % types.length],
    priority: 'NORMAL',
    audienceType: 'EVERYONE',
  });

  try {
    const res = await fetch('http://localhost:8080/example/publish', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
    if (res.ok) sent++; else failed++;
  } catch (e) {
    failed++;
  }

  if (DELAY_MS > 0) await new Promise((r) => setTimeout(r, DELAY_MS));
}

console.log(`Bitti. Basarili: ${sent}, Basarisiz: ${failed}`);