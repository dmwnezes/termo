// Gera img/icon-192.png e img/icon-512.png (cinco quadradinhos sobre o roxo do app). Uso: node tests/gerar-icones.js
const { chromium } = require("playwright");
(async () => {
  const b = await chromium.launch(), p = await b.newPage();
  for (const n of [192, 512]) {
    await p.setViewportSize({ width: n, height: n });
    const q = n * 0.11, gap = n * 0.03, cols = ["#5FB873", "#5FB873", "#E6C14F", "#5FB873", "#5FB873"];
    await p.setContent(`<body style="margin:0;height:${n}px;display:flex;align-items:center;justify-content:center;gap:${gap}px;background:linear-gradient(#2A2058,#120E2B)">${cols.map((c) => `<i style="width:${q}px;height:${q}px;border-radius:${q * 0.28}px;background:${c}"></i>`).join("")}</body>`);
    await p.screenshot({ path: `img/icon-${n}.png` });
  }
  await b.close();
})();
