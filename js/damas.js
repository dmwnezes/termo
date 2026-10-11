// Damas (regras brasileiras): motor de regras usado pela partida com amigo. O app Android tem o mesmo motor.
// Casas 0..63 = linha*8+coluna, linha 0 em cima; casas escuras: (linha + coluna) ímpar.
// Peças: "w" pedra branca, "W" dama branca, "b" pedra preta, "B" dama preta. Brancas sobem (linha diminui).
(function () {
  "use strict";
  const DIRS = [[-1, -1], [-1, 1], [1, -1], [1, 1]];
  const inside = (r, c) => r >= 0 && r < 8 && c >= 0 && c < 8;
  const sideOf = (p) => (p ? (p === "w" || p === "W" ? "w" : "b") : null);
  const isKing = (p) => p === "W" || p === "B";

  /** Posição inicial: pretas nas linhas 0–2, brancas nas linhas 5–7. */
  function initial() {
    const b = Array(64).fill(null);
    for (let i = 0; i < 64; i++) {
      const r = i >> 3, c = i & 7;
      if ((r + c) % 2 === 1) { if (r <= 2) b[i] = "b"; else if (r >= 5) b[i] = "w"; }
    }
    return b;
  }

  /** Todas as sequências de captura (completas) da peça em "from". As capturadas só saem no fim. */
  function captures(b, from) {
    const piece = b[from], me = sideOf(piece), king = isKing(piece), out = [];
    const bb = b.slice(); bb[from] = null;
    const dfs = (pos, path, cap) => {
      let more = false;
      const r = pos >> 3, c = pos & 7;
      for (const [dr, dc] of DIRS) {
        if (king) {
          let rr = r + dr, cc = c + dc;
          while (inside(rr, cc) && !bb[rr * 8 + cc]) { rr += dr; cc += dc; }
          if (!inside(rr, cc)) continue;
          const q = rr * 8 + cc;
          if (sideOf(bb[q]) === me || cap.includes(q)) continue;
          let lr = rr + dr, lc = cc + dc;
          while (inside(lr, lc) && !bb[lr * 8 + lc]) {
            const l = lr * 8 + lc;
            more = true; dfs(l, path.concat(l), cap.concat(q));
            lr += dr; lc += dc;
          }
        } else {
          const r2 = r + 2 * dr, c2 = c + 2 * dc;
          if (!inside(r2, c2)) continue;
          const q = (r + dr) * 8 + (c + dc), l = r2 * 8 + c2;
          if (!bb[q] || sideOf(bb[q]) === me || cap.includes(q) || bb[l]) continue;
          more = true; dfs(l, path.concat(l), cap.concat(q));
        }
      }
      if (!more && cap.length) out.push({ p: path, cap });
    };
    dfs(from, [from], []);
    return out;
  }

  /** Jogadas legais do lado "w"/"b": captura obrigatória e lei da maioria. Cada jogada = {p:[origem, ...], cap:[...]}. */
  function moves(b, side) {
    let caps = [];
    for (let i = 0; i < 64; i++) if (sideOf(b[i]) === side) caps = caps.concat(captures(b, i));
    const seen = new Set(), uniq = (list) => list.filter((m) => { const k = m.p.join(","); if (seen.has(k)) return false; seen.add(k); return true; });
    if (caps.length) {
      const max = Math.max(...caps.map((m) => m.cap.length));
      return uniq(caps.filter((m) => m.cap.length === max));
    }
    const out = [], fwd = side === "w" ? -1 : 1;
    for (let i = 0; i < 64; i++) {
      if (sideOf(b[i]) !== side) continue;
      const r = i >> 3, c = i & 7;
      for (const [dr, dc] of DIRS) {
        if (isKing(b[i])) {
          let rr = r + dr, cc = c + dc;
          while (inside(rr, cc) && !b[rr * 8 + cc]) { out.push({ p: [i, rr * 8 + cc], cap: [] }); rr += dr; cc += dc; }
        } else if (dr === fwd && inside(r + dr, c + dc) && !b[(r + dr) * 8 + c + dc]) out.push({ p: [i, (r + dr) * 8 + c + dc], cap: [] });
      }
    }
    return uniq(out);
  }

  /** Aplica a jogada: tira as capturadas e promove a pedra que TERMINA na última linha. */
  function apply(b, m) {
    const nb = b.slice(), from = m.p[0], to = m.p[m.p.length - 1];
    let piece = nb[from];
    nb[from] = null;
    m.cap.forEach((q) => (nb[q] = null));
    if (piece === "w" && to >> 3 === 0) piece = "W";
    if (piece === "b" && to >> 3 === 7) piece = "B";
    nb[to] = piece;
    return nb;
  }

  P.damas = { initial, moves, apply, sideOf, isKing };
})();
