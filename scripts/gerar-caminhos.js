// Gera shared/caminhos.txt: "lado|a b,c d,…" (pares de casas). Toda grade tem solução que preenche o tabuleiro inteiro.
// Método: caminho que passa por todas as casas (embaralhado com "backbite") cortado em trechos; as pontas dos trechos viram os pares.
// A ordem das linhas define o desafio de cada dia: para ampliar, acrescente no fim. Uso: node scripts/gerar-caminhos.js
const fs = require("fs");
const LEVELS = [[5, 4], [7, 6], [9, 8]], PER_LEVEL = 150;
let seed = 20261105;
const rnd = (n) => { seed = (seed * 48271) % 2147483647; return seed % n; };
function hamiltonian(n) {
  let p = [];
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) p.push(y * n + (y % 2 ? n - 1 - x : x));
  const near = (c) => { const y = Math.floor(c / n), x = c % n, o = []; if (y) o.push(c - n); if (y < n - 1) o.push(c + n); if (x) o.push(c - 1); if (x < n - 1) o.push(c + 1); return o; };
  for (let k = 0; k < n * n * 40; k++) {
    if (rnd(2)) p.reverse();
    const end = p[p.length - 1], opts = near(end), to = opts[rnd(opts.length)], i = p.indexOf(to);
    if (i === p.length - 2) continue;
    p = p.slice(0, i + 1).concat(p.slice(i + 1).reverse());
  }
  return p;
}
const adjacent = (a, b, n) => Math.abs(a - b) === n || (Math.abs(a - b) === 1 && Math.floor(a / n) === Math.floor(b / n));
function puzzle(n, pairs) {
  for (;;) {
    const p = hamiltonian(n), cuts = new Set();
    while (cuts.size < pairs - 1) cuts.add(3 + rnd(p.length - 5));
    const at = [0, ...[...cuts].sort((a, b) => a - b), p.length], parts = [];
    for (let i = 0; i < pairs; i++) parts.push(p.slice(at[i], at[i + 1]));
    // Trechos curtos ou com as pontas vizinhas não têm graça.
    if (parts.every((s) => s.length >= 3 && !adjacent(s[0], s[s.length - 1], n))) return { n, parts };
  }
}
const lines = [];
for (const [n, pairs] of LEVELS) {
  const seen = new Set();
  while (seen.size < PER_LEVEL) {
    const q = puzzle(n, pairs), l = `${n}|${q.parts.map((s) => s[0] + " " + s[s.length - 1]).join(",")}`;
    if (!seen.has(l)) { seen.add(l); lines.push(l); }
  }
}
fs.writeFileSync("shared/caminhos.txt", "# Caminhos: lado da grade|pares de casas (casa = linha × lado + coluna). A ordem define o desafio de cada dia.\n" + lines.join("\n") + "\n");
console.log(lines.length, "grades,", fs.statSync("shared/caminhos.txt").size, "bytes");
