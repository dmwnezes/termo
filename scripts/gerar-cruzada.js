// Gera shared/cruzada.txt: grades 5×5 com 3 palavras na horizontal e 3 na vertical, cruzando nas colunas e linhas 0, 2 e 4.
// Uso: node scripts/gerar-cruzada.js   (só rode de novo com um N maior acrescentando linhas no fim: a ordem delas define o desafio de cada dia).
const fs = require("fs");
const N = 366, MAX_USE = 8;
const src = fs.readFileSync("shared/words.js", "utf8");
const list = (n) => (src.match(new RegExp(`const ${n} = "([^"]*)"`)) || [, ""])[1].split(" ").filter(Boolean);
const norm = (w) => w.normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase();
const clues = {};
for (const l of fs.readFileSync("shared/significados.txt", "utf8").split("\n")) {
  const i = l.indexOf("|"); if (i > 0) clues[norm(l.slice(0, i).trim())] = l.slice(i + 1).trim();
}
// A pista não pode citar a resposta nem ser longa demais para caber na tela.
const usable = (w) => { const c = clues[w]; return c && c.length >= 8 && c.length <= 80 && !c.includes("|") && !norm(c).includes(w.slice(0, 4)); };
const words = [...new Set([...list("ANSWERS"), ...list("ANSWERS2")].map(norm))].filter(usable);
let seed = 20261103;
const rnd = (n) => { seed = (seed * 48271) % 2147483647; return seed % n; };
const byKey = {};
for (const w of words) (byKey[w[0] + w[2] + w[4]] = byKey[w[0] + w[2] + w[4]] || []).push(w);
const use = {}, out = [];
for (let tries = 0; out.length < N && tries < 2e7; tries++) {
  const a = [words[rnd(words.length)], words[rnd(words.length)], words[rnd(words.length)]];
  const d = [0, 2, 4].map((c) => { const o = byKey[a[0][c] + a[1][c] + a[2][c]]; return o ? o[rnd(o.length)] : null; });
  if (d.includes(null)) continue;
  const all = [...a, ...d];
  if (new Set(all).size < 6 || all.some((w) => (use[w] || 0) >= MAX_USE)) continue;
  all.forEach((w) => (use[w] = (use[w] || 0) + 1));
  out.push(all.join(",") + "|" + all.map((w) => clues[w]).join("|"));
}
if (out.length < N) throw new Error("só gerou " + out.length);
fs.writeFileSync("shared/cruzada.txt", "# Palavra Cruzada: 3 horizontais e 3 verticais, depois as 6 pistas na mesma ordem.\n" + out.join("\n") + "\n");
console.log(words.length, "palavras,", out.length, "grades");
