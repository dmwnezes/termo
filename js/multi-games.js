// Partida com amigo (site): Caça-Palavras (c), Jogo da Velha (v) e Damas (x).
// Usa as peças internas de js/multi.js (P.mp._) e registra cada jogo em P.mp.kinds. Mensagens iguais às do app:
// grid, found, timeup (Caça); move (Velha e Damas); resign (Damas).
(function () {
  "use strict";
  const X = () => P.mp._;
  const live = (s, r) => X().S === s && s.round === r;
  const meId = (s) => s.me.id;

  // ================= Caça-Palavras =================
  const N = 10;
  const MINE = "#5FB873", THEIRS = "#9B8CFF";
  /** Forma de comparação: maiúsculas, sem acento, só letras ("Bem-te-vi" → "BEMTEVI"). */
  const strip = (w) => P.norm(String(w || "")).replace(/[^A-Z]/g, "");
  const DIR8 = [[0, 1], [1, 0], [1, 1], [-1, 1], [0, -1], [-1, 0], [-1, -1], [1, -1]];
  /** Onde a palavra está na grade (para pintar a do amigo). */
  function locate(rows, w) {
    for (let r = 0; r < N; r++) for (let c = 0; c < N; c++) for (const [dr, dc] of DIR8) {
      const er = r + dr * (w.length - 1), ec = c + dc * (w.length - 1);
      if (er < 0 || er >= N || ec < 0 || ec >= N) continue;
      let ok = true;
      for (let i = 0; i < w.length && ok; i++) ok = rows[r + dr * i][c + dc * i] === w[i];
      if (ok) return Array.from({ length: w.length }, (_, i) => [r + dr * i, c + dc * i]);
    }
    return null;
  }
  let themesP = null;
  const themes = () => themesP || (themesP = Promise.all([P.text("caca.txt"), P.text("caca-extra.txt")])
    .then(([a, b]) => P.caca.parseThemes(a).concat(P.caca.parseThemes(b))));

  /** Anfitrião: sorteia o tema (sem repetir na sala), monta a grade e manda o "grid" da rodada. */
  async function makeGrid(s, n) {
    s.gridSent = s.gridSent || {};
    if (s.gridSent[n]) return;
    s.gridSent[n] = true;
    let pool;
    try { pool = await themes(); } catch (_) { s.gridSent[n] = false; P.toast("Não foi possível carregar os temas"); return; }
    if (X().S !== s) return;
    let avail = pool.filter((t) => !s.themes.includes(t.name));
    if (!avail.length) avail = pool;
    for (let tries = 0; tries < 60; tries++) {
      const t = avail[Math.floor(Math.random() * avail.length)];
      const rnd = P.rng(Math.floor(Math.random() * 2147483647));
      const words = P.shuffle([...new Set(t.words)], rnd).filter((w) => strip(w).length >= 3 && strip(w).length <= N).slice(0, 8);
      if (words.length < 4) continue;
      const b = P.caca.build(words, rnd);
      if (!b) continue;
      X().publish({ t: "grid", n, th: t.name, g: b.g.map((row) => row.join("")).join("|"), w: b.placed.map((p) => p.d) });
      return;
    }
  }

  function czRound(s, startAt) {
    const { h, g } = X().ids(s);
    const r = (s.round = { kind: "c", n: s.rn, startAt, host: h, guest: g, grid: null, theme: "", words: [], keys: [], cells: [],
      owner: {}, mineCells: {}, pend: {}, result: null, ms: X().debug().cacaMs || 300000, sentTimeup: false, t0: 0 });
    r.ready = () => !!r.grid || !!r.result;
    r.waitTxt = "Preparando a grade…";
    s.view = "game";
    X().gameShell(s, "cz", `
        <div class="cz-head"><span class="cz-theme" data-theme>Preparando a grade…</span><b class="cz-clock" data-clock>${X().clockFmt(r.ms)}</b></div>
        <div class="timer cz-timer"><i data-bar style="width:100%"></i></div>
        <div class="ws-wrap cz-wrap"><canvas></canvas><div class="ws-grid" style="grid-template-columns:repeat(${N},1fr)"></div></div>
        <div class="chips cz-chips" data-chips></div>
        <div class="center" data-after></div>`);
    r.api = { redraw: () => czDraw(s, r) };
    czDrag(s, r);
    if (s.grids[r.n]) czApply(s, r);
    else if (s.me.host) makeGrid(s, r.n);
    X().drawScore(s);
    czDraw(s, r);
    X().countdown(s, r);
    s.gameTimer = setInterval(() => czTick(s, r), 250);
  }
  function czApply(s, r) {
    const G = s.grids[r.n];
    r.grid = G.rows; r.theme = G.th; r.words = G.words; r.keys = G.words.map(strip);
    r.cells = r.keys.map((k) => locate(G.rows, k));
    r.t0 = Math.max(r.startAt, Date.now());
    const gridEl = P.$(".ws-grid", s.root);
    if (gridEl) gridEl.innerHTML = r.grid.map((row) => [...row].map((ch) => `<span>${ch}</span>`).join("")).join("");
    czDraw(s, r);
  }
  function czMsg(s, m) {
    if (m.t === "grid") {
      const n = Number(m.n);
      if (!Number.isInteger(n) || n < 0 || s.grids[n]) return;
      const rows = String(m.g || "").split("|");
      if (rows.length !== N || !rows.every((x) => /^[A-Z]{10}$/.test(x)) || !Array.isArray(m.w)) return;
      const words = m.w.map(String).filter((w) => strip(w).length >= 2).slice(0, 20);
      if (!words.length) return;
      s.grids[n] = { th: String(m.th || "").slice(0, 40), rows, words };
      if (m.th) s.themes.push(String(m.th));
      const r = s.round;
      if (r && r.kind === "c" && r.n === n && !r.grid) czApply(s, r);
      return;
    }
    const r = s.round;
    if (!r || r.kind !== "c" || r.result || Number(m.n) !== r.n) return;
    if (m.t === "timeup") return czFinish(s, r);
    if (m.t !== "found" || (m.id !== r.host && m.id !== r.guest) || !r.grid) return;
    const key = strip(m.w), i = r.keys.indexOf(key);
    if (i < 0 || r.owner[key]) return;
    r.owner[key] = m.id; // dono = o PRIMEIRO "found" na ordem do tópico
    const word = r.words[i], on = X().oppName(s);
    if (m.id === meId(s)) { P.fx.reveal(2, "c"); P.toast(word); }
    else { P.toast(r.pend[key] ? `${on} pegou ${word} primeiro` : `${on} achou ${word}`); P.tone([[494, 70]], 0.08); P.vibrate(30); }
    delete r.pend[key];
    czDraw(s, r);
    if (r.keys.every((k) => r.owner[k])) czFinish(s, r);
  }
  const czCount = (s, r) => {
    let me = 0, op = 0;
    r.keys.forEach((k) => { if (r.owner[k] === meId(s)) me++; else if (r.owner[k]) op++; });
    return { me, op };
  };
  function czFinish(s, r) {
    if (r.result) return;
    const { me, op } = czCount(s, r);
    r.final = { me, op };
    X().settle(s, r, me > op ? "me" : me < op ? "opp" : "draw", () => czSheet(s, r));
    czDraw(s, r);
  }
  function czTick(s, r) {
    if (!live(s, r)) return X().stopGame(s);
    if (!r.grid || r.result) return;
    const el = Math.max(0, Date.now() - r.t0), left = Math.max(0, r.ms - el);
    const clock = P.$("[data-clock]", s.root), bar = P.$("[data-bar]", s.root);
    if (clock) { clock.textContent = X().clockFmt(left); clock.classList.toggle("low", left < 30000); }
    if (bar) bar.style.width = `${(left / r.ms) * 100}%`;
    // Fim do tempo: o anfitrião manda o "timeup"; o convidado só se o anfitrião saiu ou passaram 10 s a mais.
    if (!r.sentTimeup && ((s.me.host && el > r.ms) || s.oppLeft || el > r.ms + 10000)) {
      r.sentTimeup = true;
      X().publish({ t: "timeup", n: r.n });
    }
  }
  function czDrag(s, r) {
    const wrap = P.$(".cz-wrap", s.root);
    let start = null, end = null;
    const can = () => live(s, r) && r.grid && !r.result && Date.now() >= r.t0;
    wrap.addEventListener("pointerdown", (e) => { if (!can()) return; wrap.setPointerCapture(e.pointerId); start = P.caca.cellIn(wrap, e); end = start; czPaint(s, r, start, end); });
    wrap.addEventListener("pointermove", (e) => { if (!start) return; end = P.caca.snap(start, P.caca.cellIn(wrap, e)); czPaint(s, r, start, end); });
    wrap.addEventListener("pointerup", () => {
      if (!start) return;
      const cells = P.caca.lineCells(start, end);
      start = end = null;
      if (cells.length >= 2 && can()) {
        const str = cells.map(([a, b]) => r.grid[a][b]).join(""), rev = [...str].reverse().join("");
        const key = r.keys.find((k) => !r.owner[k] && !r.pend[k] && (k === str || k === rev));
        if (key) {
          r.pend[key] = true; r.mineCells[key] = cells;
          P.fx.type();
          X().publish({ t: "found", id: meId(s), n: r.n, w: key });
        }
      }
      czDraw(s, r);
    });
    wrap.addEventListener("pointercancel", () => { start = end = null; czPaint(s, r); });
  }
  function czPaint(s, r, a, b) {
    const wrap = P.$(".cz-wrap", s.root); if (!wrap) return;
    const canvas = P.$("canvas", wrap), dpr = devicePixelRatio || 1, size = wrap.clientWidth;
    canvas.width = size * dpr; canvas.height = size * dpr;
    const c = canvas.getContext("2d"); c.scale(dpr, dpr); c.lineCap = "round";
    const cell = size / N, ctr = ([y, x]) => [x * cell + cell / 2, y * cell + cell / 2];
    const line = (p, q, color, alpha) => { c.globalAlpha = alpha; c.strokeStyle = color; c.lineWidth = cell * 0.78; c.beginPath(); c.moveTo(...ctr(p)); c.lineTo(...ctr(q)); c.stroke(); };
    r.keys.forEach((k, i) => {
      const own = r.owner[k];
      if (!own && !r.pend[k]) return;
      const cells = (own ? own === meId(s) : true) ? r.mineCells[k] || r.cells[i] : r.cells[i];
      if (!cells) return;
      line(cells[0], cells[cells.length - 1], own && own !== meId(s) ? THEIRS : MINE, own ? 0.6 : 0.28);
    });
    if (a && b) line(a, b, "#F4F1FF", 0.3);
  }
  function czDraw(s, r) {
    if (!live(s, r) || !P.$(".cz", s.root)) return;
    const { me, op } = czCount(s, r), on = P.esc(X().oppName(s));
    const title = P.$("[data-title]", s.root);
    if (title) title.innerHTML = `Você <b class="cz-n me">${me}</b> × <b class="cz-n">${op}</b> ${on}`;
    const th = P.$("[data-theme]", s.root);
    if (th) th.innerHTML = r.grid ? `Tema: <b>${P.esc(r.theme || "—")}</b>` : "Preparando a grade…";
    P.$("[data-chips]", s.root).innerHTML = r.words.map((w, i) => {
      const k = r.keys[i], own = r.owner[k];
      return `<span class="${own ? (own === meId(s) ? "me" : "opp") : r.pend[k] ? "pend" : ""}">${P.esc(w)}</span>`;
    }).join("");
    czPaint(s, r);
    X().afterBtn(P.$("[data-after]", s.root), r, () => czSheet(s, r));
  }
  function czSheet(s, r) {
    const { me, op } = r.final || czCount(s, r), en = P.esc(X().oppName(s));
    const list = r.words.map((w, i) => {
      const own = r.owner[r.keys[i]];
      return `<div class="ana-row${own === meId(s) ? " me" : own ? " opp" : ""}"><b>${P.esc(w)}</b><span>${own === meId(s) ? "✓ você" : own ? en : "—"}</span></div>`;
    }).join("");
    X().seriesSheet(s, r, { icon: "🔎", detail: `Palavras: ${me} × ${op}${r.theme ? ` · Tema: ${P.esc(r.theme)}` : ""}`, extra: `<div class="ana-rows">${list}</div>`, share: `Palavras: ${me} × ${op} 🔎` });
  }
  window.addEventListener("resize", () => { const s = X() && X().S; if (s && s.round && s.round.kind === "c") czPaint(s, s.round); });

  P.mp.kinds.c = {
    round: czRound, msg: czMsg,
    bye: (s, r) => { if (!r.result && !r.sentTimeup) { r.sentTimeup = true; X().publish({ t: "timeup", n: r.n }); } czDraw(s, r); },
    story: (s, r) => { const { me, op } = r.final || czCount(s, r); return `Palavras: ${me} × ${op}`; },
  };

  // ================= Jogo da Velha =================
  const LINES = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]];
  const SYM = {
    X: `<svg viewBox="0 0 40 40" aria-hidden="true"><path d="M10 10L30 30M30 10L10 30" stroke="currentColor" stroke-width="6" stroke-linecap="round"/></svg>`,
    O: `<svg viewBox="0 0 40 40" aria-hidden="true"><circle cx="20" cy="20" r="11" fill="none" stroke="currentColor" stroke-width="6"/></svg>`,
  };
  const vvTurnSym = (r) => (r.moves % 2 === 0 ? "X" : "O");
  const vvIdOf = (r, sym) => (sym === "X" ? r.xId : r.oId);
  const vvMySym = (s, r) => (r.xId === meId(s) ? "X" : "O");
  const vvCan = (s, r) => live(s, r) && !r.result && r.pend == null && Date.now() >= r.startAt && vvIdOf(r, vvTurnSym(r)) === meId(s);

  function vvRound(s, startAt) {
    const { h, g } = X().ids(s);
    // Rodada 0 da sala: anfitrião é X; a cada rodada os símbolos trocam. X sempre começa.
    const hostX = s.rn % 2 === 0;
    const r = (s.round = { kind: "v", n: s.rn, startAt, host: h, guest: g, xId: hostX ? h : g, oId: hostX ? g : h,
      cells: Array(9).fill(null), moves: 0, result: null, line: null, pend: null, last: -1, why: "" });
    s.view = "game";
    X().gameShell(s, "vv", `
        <p class="vv-turn" data-turn></p>
        <div class="vv-board" data-board>${Array.from({ length: 9 }, (_, i) => `<button class="vv-c" data-c="${i}" aria-label="Casa ${i + 1}"></button>`).join("")}</div>
        <p class="vv-sym" data-sym></p>
        <div class="center" data-after></div>`);
    P.$("[data-board]", s.root).addEventListener("click", (e) => {
      const b = e.target.closest("[data-c]"); if (!b) return;
      const c = +b.dataset.c;
      if (!vvCan(s, r) || r.cells[c]) return;
      r.pend = c; P.fx.type();
      X().publish({ t: "move", id: meId(s), n: r.moves, c });
      vvDraw(s, r);
    });
    r.api = { redraw: () => vvDraw(s, r) };
    X().drawScore(s);
    vvDraw(s, r);
    X().countdown(s, r);
  }
  function vvMsg(s, m) {
    const r = s.round;
    if (!r || r.kind !== "v" || r.result || m.t !== "move") return;
    const c = Number(m.c), sym = vvTurnSym(r);
    const ok = m.id === vvIdOf(r, sym) && Number(m.n) === r.moves && Number.isInteger(c) && c >= 0 && c < 9 && !r.cells[c];
    if (!ok) { if (m.id === meId(s) && r.pend != null) { r.pend = null; vvDraw(s, r); } return; }
    r.cells[c] = sym; r.moves++; r.last = c;
    if (m.id === meId(s)) r.pend = null;
    else { P.tone([[523, 50]], 0.07); P.vibrate(20); }
    const line = LINES.find((l) => l.every((i) => r.cells[i] === sym));
    if (line) { r.line = line; X().settle(s, r, m.id === meId(s) ? "me" : "opp", () => vvSheet(s, r), 1500); }
    else if (r.moves === 9) { r.why = "velha"; X().settle(s, r, "draw", () => vvSheet(s, r), 1500); }
    vvDraw(s, r);
  }
  function vvDraw(s, r) {
    if (!live(s, r) || !P.$(".vv", s.root)) return;
    const on = P.esc(X().oppName(s)), mine = vvMySym(s, r), theirs = mine === "X" ? "O" : "X";
    const turn = P.$("[data-turn]", s.root), started = Date.now() >= r.startAt;
    const ts = vvTurnSym(r), myTurn = vvIdOf(r, ts) === meId(s);
    turn.className = "vv-turn" + (!r.result && started && myTurn ? " mine" : "");
    turn.innerHTML = r.result
      ? r.result === "draw" ? (r.why === "velha" ? "Deu velha!" : "Empate") : r.result === "me" ? (r.why === "saiu" ? `${on} saiu` : "Você venceu!") : `${on} venceu`
      : !started ? "Prepare-se…" : myTurn ? `Sua vez (${ts})` : `Vez de ${on} (${ts})`;
    P.$("[data-board]", s.root).classList.toggle("off", !vvCan(s, r));
    P.$("[data-board]", s.root).querySelectorAll("[data-c]").forEach((b) => {
      const i = +b.dataset.c, v = r.cells[i] || (r.pend === i ? mine : null);
      b.className = "vv-c" + (v ? " " + v.toLowerCase() : "") + (r.pend === i ? " pend" : "") + (r.line && r.line.includes(i) ? " win" : "") + (i === r.last && r.cells[i] && r.cells[i] !== mine ? " last" : "");
      b.innerHTML = v ? SYM[v] : "";
    });
    P.$("[data-sym]", s.root).innerHTML = `Você joga de <b class="${mine.toLowerCase()}">${mine}</b> · ${on} de <b class="${theirs.toLowerCase()}">${theirs}</b>`;
    X().afterBtn(P.$("[data-after]", s.root), r, () => vvSheet(s, r));
  }
  function vvDetail(s, r) {
    if (r.result === "draw") return "Deu velha! Não conta na série.";
    if (r.why === "saiu") return `${P.esc(X().oppName(s))} saiu da partida.`;
    const sym = r.result === "me" ? vvMySym(s, r) : vvMySym(s, r) === "X" ? "O" : "X";
    return `${r.result === "me" ? "Você fez" : `${P.esc(X().oppName(s))} fez`} a linha com ${sym}.`;
  }
  const vvSheet = (s, r) => X().seriesSheet(s, r, { icon: r.result === "draw" ? "🤝" : "⭕", detail: vvDetail(s, r) });
  P.mp.kinds.v = {
    round: vvRound, msg: vvMsg,
    bye: (s, r) => { if (!r.result) { r.why = "saiu"; X().settle(s, r, "me", () => vvSheet(s, r), 1200); } vvDraw(s, r); },
    story: (s, r) => (r.result === "draw" ? "Deu velha!" : r.why === "saiu" ? "O amigo saiu da partida" : `Vitória com ${r.result === "me" ? vvMySym(s, r) : vvMySym(s, r) === "X" ? "O" : "X"}`),
  };

  // ================= Damas =================
  const D = () => P.damas;
  const SIDE = { w: "brancas", b: "pretas" };
  const dmMySide = (s, r) => (r.whiteId === meId(s) ? "w" : "b");
  const dmIdOf = (r, side) => (side === "w" ? r.whiteId : r.blackId);
  const dmCan = (s, r) => live(s, r) && !r.result && !r.pend && Date.now() >= r.startAt && dmIdOf(r, r.turn) === meId(s);
  const dmCount = (b, side) => b.filter((p) => D().sideOf(p) === side).length;
  const pathKey = (p) => p.join(",");

  function dmRound(s, startAt) {
    const { h, g } = X().ids(s);
    // Rodada 0 da sala: anfitrião joga de brancas; a cada rodada as cores trocam. Brancas começam.
    const hostW = s.rn % 2 === 0;
    const board = D().initial();
    const r = (s.round = { kind: "x", n: s.rn, startAt, host: h, guest: g, whiteId: hostW ? h : g, blackId: hostW ? g : h,
      board, turn: "w", plies: 0, kingRun: 0, legal: D().moves(board, "w"), result: null, why: "", loser: null, last: null, sel: null, path: [], pend: false });
    s.view = "game";
    const flip = dmMySide(s, r) === "b"; // cada um vê as SUAS peças embaixo
    X().gameShell(s, "dm", `
        <div class="dm-status"><p class="dm-turn" data-turn></p><span class="dm-count" data-count></span></div>
        <div class="dm-board${flip ? " flip" : ""}" data-board>${Array.from({ length: 64 }, (_, i) => {
          const sq = flip ? 63 - i : i, dark = ((sq >> 3) + (sq & 7)) % 2 === 1;
          return `<div class="dm-c ${dark ? "dark" : "light"}" data-sq="${sq}"></div>`;
        }).join("")}</div>
        <div class="dm-foot"><button class="pill ghost dm-resign" data-resign>Desistir</button></div>
        <div class="center" data-after></div>`);
    P.$("[data-board]", s.root).addEventListener("click", (e) => { const c = e.target.closest("[data-sq]"); if (c) dmTap(s, r, +c.dataset.sq); });
    P.$("[data-resign]", s.root).onclick = () => {
      if (!live(s, r) || r.result) return;
      const ui = P.sheet(`<h2>Desistir da rodada?</h2><p class="subtitle">${P.esc(X().oppName(s))} ganha esta rodada.</p>
        <div class="row-btns"><button class="pill ghost" data-no>Continuar</button><button class="pill danger" data-yes>Desistir</button></div>`);
      P.$("[data-no]", ui.el).onclick = ui.close;
      P.$("[data-yes]", ui.el).onclick = () => { ui.close(); if (live(s, r) && !r.result) X().publish({ t: "resign", id: meId(s) }); };
    };
    r.api = { redraw: () => dmDraw(s, r) };
    X().drawScore(s);
    dmDraw(s, r);
    X().countdown(s, r);
  }
  /** Toque numa casa: escolhe a peça, depois as casas do caminho até a jogada ficar única (ou o destino final). */
  function dmTap(s, r, sq) {
    if (!dmCan(s, r)) return;
    const mine = D().sideOf(r.board[sq]) === dmMySide(s, r);
    if (r.sel != null) {
      const cand = r.legal.filter((m) => r.path.every((q, i) => m.p[i] === q));
      const step = cand.filter((m) => m.p.length > r.path.length && m.p[r.path.length] === sq);
      if (step.length) {
        r.path = r.path.concat(sq);
        if (step.length === 1) return dmPlay(s, r, step[0]);
        P.fx.type();
        return dmDraw(s, r);
      }
      const fin = cand.filter((m) => m.p[m.p.length - 1] === sq);
      if (fin.length === 1) return dmPlay(s, r, fin[0]);
    }
    if (mine && r.legal.some((m) => m.p[0] === sq)) { r.sel = sq; r.path = [sq]; P.fx.type(); }
    else {
      if (mine) P.toast(r.legal.length && r.legal[0].cap.length ? "A captura é obrigatória" : "Essa peça não pode andar");
      r.sel = null; r.path = [];
    }
    dmDraw(s, r);
  }
  function dmPlay(s, r, m) {
    r.pend = true; r.sel = m.p[0]; r.path = m.p.slice();
    X().publish({ t: "move", id: meId(s), n: r.plies, p: m.p });
    dmDraw(s, r);
  }
  function dmMsg(s, m) {
    const r = s.round;
    if (!r || r.kind !== "x" || r.result) return;
    if (m.t === "resign") {
      if (m.id !== r.host && m.id !== r.guest) return;
      r.why = "desistiu"; r.loser = m.id;
      X().settle(s, r, m.id === meId(s) ? "opp" : "me", () => dmSheet(s, r), 1200);
      return dmDraw(s, r);
    }
    if (m.t !== "move") return;
    const p = Array.isArray(m.p) ? m.p.map(Number) : [];
    const mv = m.id === dmIdOf(r, r.turn) && Number(m.n) === r.plies && r.legal.find((x) => pathKey(x.p) === pathKey(p));
    if (!mv) { if (m.id === meId(s) && r.pend) { r.pend = false; r.sel = null; r.path = []; dmDraw(s, r); } return; }
    const piece = r.board[mv.p[0]];
    r.board = D().apply(r.board, mv);
    r.kingRun = D().isKing(piece) && !mv.cap.length ? r.kingRun + 1 : 0;
    r.plies++;
    r.last = { p: mv.p, cap: mv.cap, by: m.id };
    r.turn = r.turn === "w" ? "b" : "w";
    r.legal = D().moves(r.board, r.turn);
    r.sel = null; r.path = []; r.pend = false;
    if (mv.cap.length) { P.tone([[392, 60], [523, 90]], 0.09); P.vibrate(40); } else P.tone([[m.id === meId(s) ? 440 : 523, 45]], 0.06);
    if (!r.legal.length) {
      r.why = dmCount(r.board, r.turn) ? "sem jogadas" : "sem peças"; r.loser = dmIdOf(r, r.turn);
      X().settle(s, r, m.id === meId(s) ? "me" : "opp", () => dmSheet(s, r));
    } else if (r.kingRun >= 20) {
      r.why = "damas";
      X().settle(s, r, "draw", () => dmSheet(s, r));
    }
    dmDraw(s, r);
  }
  function dmDraw(s, r) {
    if (!live(s, r) || !P.$(".dm", s.root)) return;
    const me = dmMySide(s, r), them = me === "w" ? "b" : "w", on = P.esc(X().oppName(s)), started = Date.now() >= r.startAt;
    const myTurn = dmIdOf(r, r.turn) === meId(s);
    const turn = P.$("[data-turn]", s.root);
    turn.className = "dm-turn" + (!r.result && started && myTurn ? " mine" : "");
    turn.innerHTML = r.result
      ? r.result === "draw" ? "Empate" : r.result === "me" ? "Você venceu!" : `${on} venceu`
      : !started ? `Você joga de ${SIDE[me]}` : myTurn ? `Sua vez (${SIDE[me]})` : `Vez de ${on} (${SIDE[them]})`;
    P.$("[data-count]", s.root).innerHTML = `<span><i class="dm-dot ${me}"></i>${dmCount(r.board, me)}</span><span><i class="dm-dot ${them}"></i>${dmCount(r.board, them)}</span>`;
    const can = dmCan(s, r), mustCap = r.legal.length && r.legal[0].cap.length;
    const cand = r.sel != null ? r.legal.filter((m) => r.path.every((q, i) => m.p[i] === q)) : [];
    const steps = new Set(cand.filter((m) => m.p.length > r.path.length).map((m) => m.p[r.path.length]));
    const finals = new Set(cand.filter((m) => m.p.length > r.path.length + 1).map((m) => m.p[m.p.length - 1]));
    const hit = new Set(cand.length ? cand[0].cap.slice(0, Math.max(0, r.path.length - 1)) : []);
    const must = new Set(can && mustCap && r.sel == null ? r.legal.map((m) => m.p[0]) : []);
    const lastOpp = r.last && r.last.by !== meId(s) ? new Set(r.last.p) : new Set();
    P.$("[data-board]", s.root).classList.toggle("off", !can);
    P.$("[data-board]", s.root).querySelectorAll("[data-sq]").forEach((el) => {
      const sq = +el.dataset.sq, dark = ((sq >> 3) + (sq & 7)) % 2 === 1, pc = r.board[sq];
      let cls = "dm-c " + (dark ? "dark" : "light");
      if (lastOpp.has(sq)) cls += " last";
      if (can && steps.has(sq)) cls += " dest";
      else if (can && finals.has(sq)) cls += " dest fin";
      if (r.path.includes(sq) && r.path[0] !== sq) cls += " path";
      el.className = cls;
      const pcls = pc ? `dm-p ${D().sideOf(pc)}${D().isKing(pc) ? " k" : ""}${r.sel === sq ? " sel" : ""}${must.has(sq) ? " must" : ""}${hit.has(sq) ? " hit" : ""}` : "";
      const html = pc ? `<i class="${pcls}">${D().isKing(pc) ? "♛" : ""}</i>` : "";
      if (el.innerHTML !== html) el.innerHTML = html;
    });
    const rs = P.$("[data-resign]", s.root); if (rs) rs.disabled = !!r.result || !started;
    X().afterBtn(P.$("[data-after]", s.root), r, () => dmSheet(s, r));
  }
  function dmDetail(s, r) {
    const on = P.esc(X().oppName(s)), me = dmMySide(s, r), them = me === "w" ? "b" : "w";
    const pcs = `Peças: ${dmCount(r.board, me)} × ${dmCount(r.board, them)}`;
    const loserName = r.loser === meId(s) ? "Você" : on;
    const why = r.why === "damas" ? "Empate: 20 jogadas seguidas só de damas, sem captura."
      : r.why === "desistiu" ? `${loserName} desistiu.`
      : r.why === "saiu" ? `${on} saiu da partida.`
      : r.why === "sem peças" ? `${r.loser === meId(s) ? "Você ficou" : `${on} ficou`} sem peças.`
      : r.why === "sem jogadas" ? `${r.loser === meId(s) ? "Você ficou" : `${on} ficou`} sem jogadas.` : "";
    return `${why} ${pcs}`.trim();
  }
  const dmSheet = (s, r) => X().seriesSheet(s, r, { icon: "♛", detail: dmDetail(s, r) });
  P.mp.kinds.x = {
    round: dmRound, msg: dmMsg,
    bye: (s, r) => { if (!r.result) { r.why = "saiu"; X().settle(s, r, "me", () => dmSheet(s, r), 1200); } dmDraw(s, r); },
    story: (s, r) => { const me = dmMySide(s, r); return `Peças: ${dmCount(r.board, me)} × ${dmCount(r.board, me === "w" ? "b" : "w")}`; },
    tap: (sq) => { const s = X().S; if (s && s.round && s.round.kind === "x") dmTap(s, s.round, sq); }, // para testes
  };
})();
