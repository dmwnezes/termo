// Gera shared/silhueta.txt: nome|tipo (p = país, e = estado)|lat|lon|contorno SVG em caixa 100×100.
// A ordem das linhas define o desafio de cada dia: para ampliar, acrescente no fim.
// Fontes: world-atlas (Natural Earth, domínio público) e os limites de estados do IBGE (click_that_hood/brazil-states.geojson).
// Uso: npm i world-atlas@2 topojson-client i18n-iso-countries; node scripts/gerar-silhueta.js <pasta com node_modules e br.geojson>
const fs = require("fs"), path = require("path");
const DIR = path.resolve(process.argv[2] || ".");
const req = (m) => require(path.join(DIR, "node_modules", m));
const { feature } = req("topojson-client");
const iso = req("i18n-iso-countries");
iso.registerLocale(JSON.parse(fs.readFileSync(path.join(DIR, "node_modules/i18n-iso-countries/langs/pt.json"))));

// Nomes como se fala no Brasil.
const FIX = { 840: "Estados Unidos", 643: "Rússia", 180: "República Democrática do Congo", 178: "República do Congo", 410: "Coreia do Sul", 408: "Coreia do Norte", 826: "Reino Unido", 528: "Países Baixos", 203: "Tchéquia", 703: "Eslováquia", 76: "Brasil", 364: "Irã", 760: "Síria", 704: "Vietnã", 418: "Laos", 498: "Moldávia", 807: "Macedônia do Norte", 616: "Polônia", 642: "Romênia", 112: "Belarus", 804: "Ucrânia", 834: "Tanzânia", 262: "Djibuti", 270: "Gâmbia", 288: "Gana", 404: "Quênia", 800: "Uganda", 646: "Ruanda", 108: "Burundi", 516: "Namíbia", 454: "Malaui", 894: "Zâmbia", 716: "Zimbábue", 72: "Botsuana", 426: "Lesoto", 748: "Essuatíni", 450: "Madagascar", 104: "Mianmar", 50: "Bangladesh", 702: "Singapura", 96: "Brunei", 626: "Timor-Leste", 598: "Papua-Nova Guiné", 780: "Trinidad e Tobago", 591: "Panamá", 340: "Honduras", 222: "El Salvador", 558: "Nicarágua", 188: "Costa Rica", 214: "República Dominicana", 332: "Haiti", 388: "Jamaica", 192: "Cuba", 44: "Bahamas", 862: "Venezuela", 68: "Bolívia", 600: "Paraguai", 858: "Uruguai", 604: "Peru", 218: "Equador", 170: "Colômbia", 328: "Guiana", 740: "Suriname", 152: "Chile", 32: "Argentina", 384: "Costa do Marfim", 120: "Camarões", 148: "Chade", 140: "República Centro-Africana", 566: "Nigéria", 562: "Níger", 466: "Mali", 854: "Burkina Faso", 686: "Senegal", 478: "Mauritânia", 324: "Guiné", 624: "Guiné-Bissau", 226: "Guiné Equatorial", 694: "Serra Leoa", 430: "Libéria", 768: "Togo", 204: "Benim", 24: "Angola", 508: "Moçambique", 710: "África do Sul", 728: "Sudão do Sul", 729: "Sudão", 232: "Eritreia", 231: "Etiópia", 706: "Somália", 818: "Egito", 434: "Líbia", 788: "Tunísia", 12: "Argélia", 504: "Marrocos", 266: "Gabão", 887: "Iêmen", 512: "Omã", 784: "Emirados Árabes Unidos", 634: "Catar", 414: "Kuwait", 682: "Arábia Saudita", 368: "Iraque", 400: "Jordânia", 422: "Líbano", 376: "Israel", 792: "Turquia", 268: "Geórgia", 51: "Armênia", 31: "Azerbaijão", 398: "Cazaquistão", 860: "Uzbequistão", 795: "Turcomenistão", 762: "Tajiquistão", 417: "Quirguistão", 4: "Afeganistão", 586: "Paquistão", 356: "Índia", 144: "Sri Lanka", 524: "Nepal", 64: "Butão", 156: "China", 496: "Mongólia", 392: "Japão", 764: "Tailândia", 116: "Camboja", 458: "Malásia", 360: "Indonésia", 608: "Filipinas", 36: "Austrália", 554: "Nova Zelândia", 242: "Fiji", 124: "Canadá", 484: "México", 320: "Guatemala", 84: "Belize", 352: "Islândia", 372: "Irlanda", 620: "Portugal", 724: "Espanha", 250: "França", 276: "Alemanha", 380: "Itália", 756: "Suíça", 40: "Áustria", 56: "Bélgica", 442: "Luxemburgo", 208: "Dinamarca", 578: "Noruega", 752: "Suécia", 246: "Finlândia", 233: "Estônia", 428: "Letônia", 440: "Lituânia", 348: "Hungria", 100: "Bulgária", 300: "Grécia", 8: "Albânia", 191: "Croácia", 705: "Eslovênia", 70: "Bósnia e Herzegovina", 688: "Sérvia", 499: "Montenegro", 196: "Chipre" };
// Territórios e micro-Estados que viram um ponto ou não são países independentes reconhecidos pelo Brasil.
const SKIP = new Set([10, 304, 238, 260, 732, 275, 158, 548, 90]);
const EARTH = 6371;
const rad = (d) => (d * Math.PI) / 180;
const dist = (a, b) => { const s = Math.sin(rad(b[0] - a[0]) / 2) ** 2 + Math.cos(rad(a[0])) * Math.cos(rad(b[0])) * Math.sin(rad(b[1] - a[1]) / 2) ** 2; return 2 * EARTH * Math.asin(Math.sqrt(s)); };
/** Longitudes contínuas: o Pacífico não parte o contorno (Rússia, Fiji). */
const unwrap = (ring) => { const o = [ring[0].slice()]; for (let i = 1; i < ring.length; i++) { let x = ring[i][0]; const p = o[i - 1][0]; while (x - p > 180) x -= 360; while (x - p < -180) x += 360; o.push([x, ring[i][1]]); } return o; };
const area = (r) => { let a = 0; const k = Math.cos(rad(r[0][1])); for (let i = 0; i < r.length - 1; i++) a += r[i][0] * r[i + 1][1] - r[i + 1][0] * r[i][1]; return Math.abs(a / 2) * Math.abs(k || 1); };
const centroid = (r) => { let x = 0, y = 0, a = 0; for (let i = 0; i < r.length - 1; i++) { const c = r[i][0] * r[i + 1][1] - r[i + 1][0] * r[i][1]; a += c; x += (r[i][0] + r[i + 1][0]) * c; y += (r[i][1] + r[i + 1][1]) * c; } return a ? [x / (3 * a), y / (3 * a)] : r[0]; };
const rdp = (p, eps) => {
  if (p.length < 3) return p;
  let m = 0, at = 0; const [a, b] = [p[0], p[p.length - 1]], dx = b[0] - a[0], dy = b[1] - a[1], l = Math.hypot(dx, dy) || 1;
  for (let i = 1; i < p.length - 1; i++) { const d = l > 1e-9 && (dx || dy) ? Math.abs(dy * p[i][0] - dx * p[i][1] + b[0] * a[1] - b[1] * a[0]) / l : Math.hypot(p[i][0] - a[0], p[i][1] - a[1]); if (d > m) { m = d; at = i; } }
  return m > eps ? rdp(p.slice(0, at + 1), eps).slice(0, -1).concat(rdp(p.slice(at), eps)) : [a, b];
};
const lonOf = (x, c) => { let v = x; while (v - c > 180) v -= 360; while (v - c < -180) v += 360; return v; };

function shape(geom, tolerance = 0.4) {
  const polys = (geom.type === "Polygon" ? [geom.coordinates] : geom.coordinates).map((p) => unwrap(p[0]));
  polys.sort((a, b) => area(b) - area(a));
  const big = centroid(polys[0]), bigArea = area(polys[0]);
  // Só o corpo principal: sem territórios distantes (Alasca, Guiana Francesa) nem ilhotas.
  const keep = polys.filter((r) => { const c = centroid(r); return r === polys[0] || (area(r) >= bigArea * 0.03 && dist([big[1], big[0]], [c[1], lonOf(c[0], big[0])]) < 3000); })
    .map((r) => { const c = centroid(r), shift = lonOf(c[0], big[0]) - c[0]; return r.map(([x, y]) => [x + shift, y]); });
  const ys = keep.flat().map((p) => p[1]), lat0 = (Math.min(...ys) + Math.max(...ys)) / 2, k = Math.cos(rad(lat0));
  const proj = keep.map((r) => r.map(([x, y]) => [x * k, -y]));
  const xs = proj.flat().map((p) => p[0]), yy = proj.flat().map((p) => p[1]);
  const [x0, x1, y0, y1] = [Math.min(...xs), Math.max(...xs), Math.min(...yy), Math.max(...yy)];
  const s = 96 / Math.max(x1 - x0, y1 - y0), ox = (100 - (x1 - x0) * s) / 2, oy = (100 - (y1 - y0) * s) / 2;
  const d = proj.map((r) => {
    const pts = rdp(r.map(([x, y]) => [ox + (x - x0) * s, oy + (y - y0) * s]), tolerance);
    return pts.length < 4 ? "" : "M" + pts.slice(0, -1).map(([x, y]) => `${x.toFixed(1)} ${y.toFixed(1)}`).join("L") + "Z";
  }).join("");
  // Centro para distância e direção: média ponderada das partes mantidas.
  let w = 0, cx = 0, cy = 0; keep.forEach((r) => { const a = area(r), c = centroid(r); w += a; cx += c[0] * a; cy += c[1] * a; });
  let lon = cx / w; while (lon > 180) lon -= 360; while (lon < -180) lon += 360;
  return { d, lat: +(cy / w).toFixed(2), lon: +lon.toFixed(2) };
}

const world = JSON.parse(fs.readFileSync(path.join(DIR, "node_modules/world-atlas/countries-50m.json")));
const countries = [];
for (const f of feature(world, world.objects.countries).features) {
  const n = +f.id;
  if (!n || SKIP.has(n)) continue;
  const name = FIX[n]; // só países com nome revisado: evita dependências, territórios e ilhotas
  if (!name) continue;
  const s = shape(f.geometry);
  if (s.d.length > 40) countries.push({ name, t: "p", ...s });
}
const UF = JSON.parse(fs.readFileSync(path.join(DIR, "br.geojson")));
const states = UF.features.map((f) => ({ name: f.properties.name, t: "e", ...shape(f.geometry, 0.4) }));

// Ordem fixa e misturada; a cada 5 desafios um é estado.
let seed = 20261104;
const next = (n) => { seed = (seed * 48271) % 2147483647; return seed % n; };
const shuffle = (a) => { for (let i = a.length - 1; i > 0; i--) { const j = next(i + 1); [a[i], a[j]] = [a[j], a[i]]; } return a; };
shuffle(countries); shuffle(states);
const out = []; let c = 0, e = 0;
while (c < countries.length || e < states.length) {
  for (let k = 0; k < 4 && c < countries.length; k++) out.push(countries[c++]);
  if (e < states.length) out.push(states[e++]);
}
fs.writeFileSync("shared/silhueta.txt", "# Silhueta: nome|p = país, e = estado|latitude|longitude|contorno SVG (caixa 100×100). A ordem define o desafio de cada dia.\n" + out.map((x) => `${x.name}|${x.t}|${x.lat}|${x.lon}|${x.d}`).join("\n") + "\n");
console.log(countries.length, "países,", states.length, "estados,", fs.statSync("shared/silhueta.txt").size, "bytes");
console.log(states.map((s) => s.name).join(", "));
console.log("sem nome:", feature(world, world.objects.countries).features.filter((f) => !FIX[+f.id]).map((f) => f.properties.name).join(", "));
