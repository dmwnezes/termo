// Xadrez: motor de regras e computador (alfa-beta). Puro: roda no site (P.xadrez), num Web Worker e no Node (testes).
// Casas 0..63 = fileira*8 + coluna, a1 = 0, h8 = 63. Peças como no FEN: "PNBRQK" brancas, "pnbrqk" pretas, "" vazia.
// Estado: {b: Array(64), w: true se é a vez das brancas, c: roques (1 = K, 2 = Q, 4 = k, 8 = q), ep: casa do en passant ou -1,
// h: lances desde a última captura ou lance de peão, f: número do lance}. Lance: {f, t, p (promoção "q|r|b|n" ou ""), ep, cs}.
// Lances trocados em UCI: "e2e4", promoção "e7e8q". O app Android tem o mesmo motor (perft(3) da posição inicial = 8902).
(function (root) {
  "use strict";
  const FILES = "abcdefgh";
  const name = (s) => FILES[s & 7] + ((s >> 3) + 1);
  const sqOf = (n) => { const f = n.charCodeAt(0) - 97, r = n.charCodeAt(1) - 49; return f >= 0 && f < 8 && r >= 0 && r < 8 ? r * 8 + f : -1; };
  const white = (p) => p !== "" && p < "a";
  const START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

  // Tabelas de destinos: cavalo, rei e raios das peças de longo alcance.
  const step = (s, df, dr) => { const f = (s & 7) + df, r = (s >> 3) + dr; return f >= 0 && f < 8 && r >= 0 && r < 8 ? r * 8 + f : -1; };
  const ND = [[1, 2], [2, 1], [2, -1], [1, -2], [-1, -2], [-2, -1], [-2, 1], [-1, 2]];
  const KD = [[1, 0], [-1, 0], [0, 1], [0, -1], [1, 1], [1, -1], [-1, 1], [-1, -1]];
  const NT = [], KT = [], RAYS = [];
  for (let s = 0; s < 64; s++) {
    NT[s] = ND.map(([a, b]) => step(s, a, b)).filter((x) => x >= 0);
    KT[s] = KD.map(([a, b]) => step(s, a, b)).filter((x) => x >= 0);
    RAYS[s] = KD.map(([a, b]) => { const ray = []; let q = s; while ((q = step(q, a, b)) >= 0) ray.push(q); return ray; });
  }
  // Perda de roque quando algo sai de / chega a essas casas.
  const CR = Array(64).fill(15); CR[4] = 12; CR[0] = 13; CR[7] = 14; CR[60] = 3; CR[56] = 7; CR[63] = 11;

  function fromFEN(fen) {
    const [pl, side, cas, ep, h, f] = String(fen).trim().split(/\s+/);
    const b = Array(64).fill("");
    pl.split("/").forEach((row, i) => { let file = 0; for (const ch of row) { if (/\d/.test(ch)) file += +ch; else b[(7 - i) * 8 + file++] = ch; } });
    let c = 0; if (cas && cas !== "-") for (const ch of cas) c |= { K: 1, Q: 2, k: 4, q: 8 }[ch] || 0;
    return { b, w: side !== "b", c, ep: ep && ep !== "-" ? sqOf(ep) : -1, h: +h || 0, f: +f || 1 };
  }
  function placement(st) {
    let out = "";
    for (let r = 7; r >= 0; r--) {
      let e = 0;
      for (let f = 0; f < 8; f++) { const p = st.b[r * 8 + f]; if (!p) e++; else { if (e) out += e; e = 0; out += p; } }
      if (e) out += e; if (r) out += "/";
    }
    return out;
  }
  const casStr = (c) => (c & 1 ? "K" : "") + (c & 2 ? "Q" : "") + (c & 4 ? "k" : "") + (c & 8 ? "q" : "") || "-";
  const toFEN = (st) => `${placement(st)} ${st.w ? "w" : "b"} ${casStr(st.c)} ${st.ep >= 0 ? name(st.ep) : "-"} ${st.h} ${st.f}`;
  const initial = () => fromFEN(START);

  /** A casa sq é atacada pelo lado "byWhite"? */
  function attacked(b, sq, byWhite) {
    const P_ = byWhite ? "P" : "p", N_ = byWhite ? "N" : "n", K_ = byWhite ? "K" : "k";
    const B_ = byWhite ? "B" : "b", R_ = byWhite ? "R" : "r", Q_ = byWhite ? "Q" : "q";
    const pr = byWhite ? -1 : 1; // peão branco ataca de uma fileira abaixo
    for (const df of [-1, 1]) { const q = step(sq, df, pr); if (q >= 0 && b[q] === P_) return true; }
    for (const q of NT[sq]) if (b[q] === N_) return true;
    for (const q of KT[sq]) if (b[q] === K_) return true;
    const rays = RAYS[sq];
    for (let d = 0; d < 8; d++) {
      const diag = d >= 4;
      for (const q of rays[d]) {
        const p = b[q]; if (!p) continue;
        if (p === Q_ || p === (diag ? B_ : R_)) return true;
        break;
      }
    }
    return false;
  }
  const kingSq = (b, w) => b.indexOf(w ? "K" : "k");
  const inCheck = (st, w = st.w) => attacked(st.b, kingSq(st.b, w), !w);

  /** Lances pseudo-legais (o rei pode ficar em xeque). caps = só capturas e promoções. */
  function pseudo(st, caps) {
    const b = st.b, w = st.w, out = [];
    const enemy = (p) => p !== "" && white(p) !== w;
    const add = (f, t, extra) => out.push(Object.assign({ f, t, p: "", ep: false, cs: false }, extra));
    for (let s = 0; s < 64; s++) {
      const pc = b[s]; if (!pc || white(pc) !== w) continue;
      const k = pc.toLowerCase();
      if (k === "p") {
        const dir = w ? 1 : -1, r = s >> 3, last = w ? 6 : 1, home = w ? 1 : 6;
        const promo = (t) => ["q", "r", "b", "n"].forEach((p) => add(s, t, { p }));
        const one = step(s, 0, dir);
        if (one >= 0 && !b[one]) {
          if (r === last) promo(one);
          else if (!caps) { add(s, one); const two = step(s, 0, 2 * dir); if (r === home && !b[two]) add(s, two); }
        }
        for (const df of [-1, 1]) {
          const t = step(s, df, dir); if (t < 0) continue;
          if (enemy(b[t])) { if (r === last) promo(t); else add(s, t); }
          else if (t === st.ep) add(s, t, { ep: true });
        }
      } else if (k === "n" || k === "k") {
        for (const t of (k === "n" ? NT : KT)[s]) if (!b[t] ? !caps : enemy(b[t])) add(s, t);
        if (k === "k" && !caps && s === (w ? 4 : 60)) {
          const c = st.c, o = w ? 0 : 56, R = w ? "R" : "r";
          if (c & (w ? 1 : 4) && !b[o + 5] && !b[o + 6] && b[o + 7] === R && !attacked(b, o + 4, !w) && !attacked(b, o + 5, !w) && !attacked(b, o + 6, !w)) add(s, o + 6, { cs: true });
          if (c & (w ? 2 : 8) && !b[o + 3] && !b[o + 2] && !b[o + 1] && b[o] === R && !attacked(b, o + 4, !w) && !attacked(b, o + 3, !w) && !attacked(b, o + 2, !w)) add(s, o + 2, { cs: true });
        }
      } else {
        const dirs = k === "b" ? [4, 5, 6, 7] : k === "r" ? [0, 1, 2, 3] : [0, 1, 2, 3, 4, 5, 6, 7];
        for (const d of dirs) for (const t of RAYS[s][d]) {
          if (!b[t]) { if (!caps) add(s, t); continue; }
          if (enemy(b[t])) add(s, t);
          break;
        }
      }
    }
    return out;
  }

  /** Aplica o lance (sem conferir se é legal) e devolve o estado novo. */
  function apply(st, m) {
    const b = st.b.slice(), pc = b[m.f], w = st.w, cap = b[m.t] !== "" || m.ep;
    b[m.f] = "";
    if (m.ep) b[m.t + (w ? -8 : 8)] = "";
    b[m.t] = m.p ? (w ? m.p.toUpperCase() : m.p) : pc;
    if (m.cs) { const o = w ? 0 : 56; if ((m.t & 7) === 6) { b[o + 5] = b[o + 7]; b[o + 7] = ""; } else { b[o + 3] = b[o]; b[o] = ""; } }
    const pawn = pc === "P" || pc === "p";
    return { b, w: !w, c: st.c & CR[m.f] & CR[m.t], ep: pawn && Math.abs(m.t - m.f) === 16 ? (m.f + m.t) / 2 : -1, h: pawn || cap ? 0 : st.h + 1, f: st.f + (w ? 0 : 1) };
  }

  /** Lances legais. */
  function moves(st, caps) {
    return pseudo(st, caps).filter((m) => { const n = apply(st, m); return !attacked(n.b, kingSq(n.b, st.w), !st.w); });
  }
  const uci = (m) => name(m.f) + name(m.t) + (m.p || "");
  /** Texto UCI → lance legal ou null (promoção exige a letra). */
  const parse = (st, u) => { u = String(u || "").toLowerCase(); return /^[a-h][1-8][a-h][1-8][qrbn]?$/.test(u) ? moves(st).find((m) => uci(m) === u) || null : null; };

  /** Chave da posição para a tripla repetição: peças, vez, roques e en passant só se a captura for legal. */
  function key(st, legal) {
    const ep = st.ep >= 0 && (legal || moves(st)).some((m) => m.ep) ? name(st.ep) : "-";
    return `${placement(st)} ${st.w ? "w" : "b"} ${casStr(st.c)} ${ep}`;
  }
  /** Só reis; rei e bispo × rei; rei e cavalo × rei. */
  function insufficient(b) {
    const rest = b.filter((p) => p && p !== "K" && p !== "k");
    return rest.length === 0 || (rest.length === 1 && "BbNn".includes(rest[0]));
  }

  /** Partida a partir de uma lista de lances UCI. Para no primeiro lance ilegal. {st, states, list (lances), keys, legal, end} */
  function game(ucis, fen) {
    let st = fen ? fromFEN(fen) : initial(), legal = moves(st);
    const g = { st, states: [st], list: [], keys: [key(st, legal)], legal, end: null };
    g.end = status(g);
    for (const u of ucis || []) { if (g.end || !push(g, u)) break; }
    return g;
  }
  /** Joga o lance UCI na partida g. Devolve o lance ou null se ilegal. */
  function push(g, u) {
    if (g.end) return null;
    const m = typeof u === "string" ? g.legal.find((x) => uci(x) === String(u).toLowerCase()) : u;
    if (!m) return null;
    g.st = apply(g.st, m); g.legal = moves(g.st);
    g.states.push(g.st); g.list.push(m); g.keys.push(key(g.st, g.legal));
    g.end = status(g);
    return m;
  }
  /** Fim de jogo: {res: "w"|"b"|"draw", why: "mate"|"afogamento"|"material"|"50"|"repeticao"} ou null. */
  function status(g) {
    const st = g.st;
    if (!g.legal.length) return inCheck(st) ? { res: st.w ? "b" : "w", why: "mate" } : { res: "draw", why: "afogamento" };
    if (insufficient(st.b)) return { res: "draw", why: "material" };
    if (st.h >= 100) return { res: "draw", why: "50" };
    const k = g.keys[g.keys.length - 1];
    if (g.keys.filter((x) => x === k).length >= 3) return { res: "draw", why: "repeticao" };
    return null;
  }

  function perft(st, d) {
    if (d === 0) return 1;
    const ms = moves(st);
    if (d === 1) return ms.length;
    let n = 0; for (const m of ms) n += perft(apply(st, m), d - 1);
    return n;
  }

  // ---------- computador ----------
  const VAL = { p: 100, n: 320, b: 330, r: 500, q: 900, k: 0 };
  // Tabelas de posição (do ponto de vista das brancas, fileira 8 primeiro): "Simplified Evaluation Function".
  const PST = {
    p: [0,0,0,0,0,0,0,0, 50,50,50,50,50,50,50,50, 10,10,20,30,30,20,10,10, 5,5,10,25,25,10,5,5, 0,0,0,20,20,0,0,0, 5,-5,-10,0,0,-10,-5,5, 5,10,10,-20,-20,10,10,5, 0,0,0,0,0,0,0,0],
    n: [-50,-40,-30,-30,-30,-30,-40,-50, -40,-20,0,0,0,0,-20,-40, -30,0,10,15,15,10,0,-30, -30,5,15,20,20,15,5,-30, -30,0,15,20,20,15,0,-30, -30,5,10,15,15,10,5,-30, -40,-20,0,5,5,0,-20,-40, -50,-40,-30,-30,-30,-30,-40,-50],
    b: [-20,-10,-10,-10,-10,-10,-10,-20, -10,0,0,0,0,0,0,-10, -10,0,5,10,10,5,0,-10, -10,5,5,10,10,5,5,-10, -10,0,10,10,10,10,0,-10, -10,10,10,10,10,10,10,-10, -10,5,0,0,0,0,5,-10, -20,-10,-10,-10,-10,-10,-10,-20],
    r: [0,0,0,0,0,0,0,0, 5,10,10,10,10,10,10,5, -5,0,0,0,0,0,0,-5, -5,0,0,0,0,0,0,-5, -5,0,0,0,0,0,0,-5, -5,0,0,0,0,0,0,-5, -5,0,0,0,0,0,0,-5, 0,0,0,5,5,0,0,0],
    q: [-20,-10,-10,-5,-5,-10,-10,-20, -10,0,0,0,0,0,0,-10, -10,0,5,5,5,5,0,-10, -5,0,5,5,5,5,0,-5, 0,0,5,5,5,5,0,-5, -10,5,5,5,5,5,0,-10, -10,0,5,0,0,0,0,-10, -20,-10,-10,-5,-5,-10,-10,-20],
    k: [-30,-40,-40,-50,-50,-40,-40,-30, -30,-40,-40,-50,-50,-40,-40,-30, -30,-40,-40,-50,-50,-40,-40,-30, -30,-40,-40,-50,-50,-40,-40,-30, -20,-30,-30,-40,-40,-30,-30,-20, -10,-20,-20,-20,-20,-20,-20,-10, 20,20,0,0,0,0,20,20, 20,30,10,0,0,10,30,20],
  };
  /** Avaliação do ponto de vista de quem joga. */
  function evaluate(st) {
    let s = 0;
    for (let q = 0; q < 64; q++) {
      const p = st.b[q]; if (!p) continue;
      const k = p.toLowerCase(), w = p !== k;
      const idx = w ? (7 - (q >> 3)) * 8 + (q & 7) : q; // tabela vista do lado da peça
      const v = VAL[k] + PST[k][idx];
      s += w ? v : -v;
    }
    return st.w ? s : -s;
  }
  const MATE = 100000;
  const order = (st, ms) => ms.map((m) => [m, (st.b[m.t] ? 10 * VAL[st.b[m.t].toLowerCase()] - VAL[st.b[m.f].toLowerCase()] / 10 + 1000 : 0) + (m.p ? VAL[m.p] : 0)])
    .sort((a, b) => b[1] - a[1]).map((x) => x[0]);

  function quiesce(st, alpha, beta, d) {
    const stand = evaluate(st);
    if (stand >= beta || d === 0) return stand;
    if (stand > alpha) alpha = stand;
    for (const m of order(st, moves(st, true))) {
      const v = -quiesce(apply(st, m), -beta, -alpha, d - 1);
      if (v >= beta) return v;
      if (v > alpha) alpha = v;
    }
    return alpha;
  }
  function negamax(st, d, alpha, beta, ply, q) {
    const ms = moves(st);
    if (!ms.length) return inCheck(st) ? -MATE + ply : 0;
    if (st.h >= 100 || insufficient(st.b)) return 0;
    if (d === 0) return q ? quiesce(st, alpha, beta, 6) : evaluate(st);
    let best = -Infinity;
    for (const m of order(st, ms)) {
      const v = -negamax(apply(st, m), d - 1, -beta, -alpha, ply + 1, q);
      if (v > best) best = v;
      if (v > alpha) alpha = v;
      if (alpha >= beta) break;
    }
    return best;
  }
  /** Lance do computador (UCI). level: 0 Fácil (prof. 1, com sorteio), 1 Médio (prof. 2), 2 Difícil (prof. 3). */
  function best(st, level, rnd = Math.random) {
    const ms = moves(st); if (!ms.length) return null;
    const depth = level + 1, q = level > 0, noise = level === 0 ? 60 : 0;
    let top = null, topV = -Infinity, alpha = -Infinity;
    for (const m of order(st, ms)) {
      let v = -negamax(apply(st, m), depth - 1, -Infinity, noise ? Infinity : -alpha, 1, q);
      if (noise) v += (rnd() * 2 - 1) * noise;
      if (v > topV) { topV = v; top = m; }
      if (v > alpha) alpha = v;
    }
    return uci(top);
  }

  const api = { START, initial, fromFEN, toFEN, name, sqOf, white, moves, apply, parse, uci, inCheck, attacked, key, insufficient, game, push, status, perft, evaluate, best, VAL };
  root.Xadrez = api;
  if (root.P) root.P.xadrez = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  // Web Worker: {id, fen, level} → {id, u}
  if (typeof document === "undefined" && typeof importScripts === "function") {
    root.onmessage = (e) => { const d = e.data || {}; root.postMessage({ id: d.id, u: best(fromFEN(d.fen), d.level) }); };
  }
})(typeof self !== "undefined" ? self : globalThis);
