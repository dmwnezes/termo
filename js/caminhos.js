// Caminhos (site): ligue os pares de cores sem cruzar e preencha a grade inteira. Dados em shared/caminhos.txt, um desafio por nível a cada dia.
(function () {
  "use strict";
  const LEVELS = [["facil", "Fácil", 5], ["medio", "Médio", 7], ["dificil", "Difícil", 9]];
  const COLORS = ["#E5737A", "#6FA8E8", "#5FB873", "#E6C14F", "#C58CFF", "#FF9F5A", "#5FD0C5", "#F49AC1"];
  const HELP = "Arraste a partir de um ponto colorido até o outro ponto da mesma cor. Os caminhos não podem se cruzar: passar por cima de outro caminho corta esse caminho. " +
    "Para vencer, ligue todos os pares e preencha todas as casas. Há um desafio por nível a cada dia.";

  P.caminhosParse = (t) => t.split("\n").filter((l) => l.trim() && !l.startsWith("#")).map((l) => {
    const [n, p] = l.split("|");
    return { n: +n, pairs: p.split(",").map((x) => x.split(" ").map(Number)) };
  });
  P.caminhosDaily = (list, level, day) => { const of = list.filter((q) => q.n === LEVELS[level][2]); return of[((day % of.length) + of.length) % of.length]; };

  /** Motor puro, igual ao game/Caminhos.kt. */
  P.caminhos = {
    create: (q) => ({ q, paths: q.pairs.map(() => []), active: -1 }),
    adjacent: (n, a, b) => Math.abs(a - b) === n || (Math.abs(a - b) === 1 && Math.floor(a / n) === Math.floor(b / n)),
    endpointOf: (g, c) => g.q.pairs.findIndex((p) => p.includes(c)),
    connected(g, k) { const p = g.paths[k], e = g.q.pairs[k]; return p.length > 1 && e.includes(p[0]) && e.includes(p[p.length - 1]) && p[0] !== p[p.length - 1]; },
    won(g) { return g.q.pairs.every((_, k) => P.caminhos.connected(g, k)) && g.paths.reduce((a, p) => a + p.length, 0) === g.q.n * g.q.n; },
    /** Começa num ponto (recomeça a cor) ou numa casa de um caminho (corta a partir dali). */
    start(g, c) {
      const k = P.caminhos.endpointOf(g, c);
      if (k >= 0) { g.paths[k] = [c]; g.active = k; return; }
      const o = g.paths.findIndex((p) => p.includes(c));
      if (o >= 0) { g.paths[o] = g.paths[o].slice(0, g.paths[o].indexOf(c) + 1); g.active = o; } else g.active = -1;
    },
    /** Avança uma casa. */
    step(g, c) {
      const k = g.active; if (k < 0) return false;
      const p = g.paths[k], last = p[p.length - 1];
      if (c === last || !P.caminhos.adjacent(g.q.n, last, c)) return false;
      const i = p.indexOf(c);
      if (i >= 0) { g.paths[k] = p.slice(0, i + 1); return true; }
      if (P.caminhos.connected(g, k)) return false; // ligado: só pode voltar
      const end = P.caminhos.endpointOf(g, c);
      if (end >= 0 && end !== k) return false;
      g.paths.forEach((o, x) => { if (x !== k) { const at = o.indexOf(c); if (at >= 0) g.paths[x] = o.slice(0, at); } });
      p.push(c);
      return true;
    },
    /** Arrasto até uma casa: anda em linha reta casa por casa, mesmo se o dedo pular. */
    dragTo(g, c) {
      const n = g.q.n;
      for (let guard = 0; guard < n * 2 && g.active >= 0; guard++) {
        const p = g.paths[g.active], last = p[p.length - 1];
        if (last === c) return;
        const y = Math.floor(last / n), x = last % n, ty = Math.floor(c / n), tx = c % n;
        const next = Math.abs(tx - x) >= Math.abs(ty - y) ? y * n + x + Math.sign(tx - x) : (y + Math.sign(ty - y)) * n + x;
        if (!P.caminhos.step(g, next)) return;
      }
    },
  };

  P.games.caminhos = async function (root, inf = !!P.store.get("cam-inf", false), level = P.store.get("cam-level", 0) || 0) {
    P.store.set("cam-inf", inf); P.store.set("cam-level", level);
    const [lk] = LEVELS[level], today = P.dayKey(), key = inf ? `cam-inf-state-${lk}` : `cam-state-${lk}`;
    root.innerHTML = P.topbar("Caminhos", { help: true, extra: `<b class="muted" data-time style="padding-right:10px">0:00</b>` }) + P.modeSwitch(inf) + `<div class="game cam">
      <div class="seg mina-lv">${LEVELS.map(([, n], i) => `<button class="${i === level ? "on" : ""}" data-lv="${i}">${n}</button>`).join("")}</div>
      <p class="hint" data-status></p><svg class="cam-board" data-board role="application" aria-label="Grade do Caminhos"></svg><div class="row-btns" data-actions></div></div>`;
    P.bindModeSwitch(root, (v) => P.games.caminhos(root, v, level));
    root.querySelectorAll("[data-lv]").forEach((b) => (b.onclick = () => P.games.caminhos(root, inf, +b.dataset.lv)));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Caminhos</h2><p>${HELP}</p>`);
    const list = P.caminhosParse(await P.text("caminhos.txt").catch(() => ""));
    const board = P.$("[data-board]", root);
    if (!document.body.contains(board)) return;
    if (!list.length) { P.$("[data-status]", root).textContent = "Não foi possível carregar as grades."; return; }

    let saved = P.store.get(key, null);
    if (saved && (!inf && saved.day !== today || !Array.isArray(saved.paths))) saved = null;
    const pool = list.filter((q) => q.n === LEVELS[level][2]);
    const qi = saved ? saved.qi : inf ? Math.floor(Math.random() * pool.length) : pool.indexOf(P.caminhosDaily(list, level, P.dayIndex()));
    const q = pool[qi], n = q.n, g = P.caminhos.create(q);
    if (saved && saved.paths.length === q.pairs.length) g.paths = saved.paths;
    let seconds = saved ? saved.seconds || 0 : 0, finished = P.caminhos.won(g);
    const save = () => P.store.set(key, { day: today, qi, paths: g.paths, seconds });
    const timeEl = P.$("[data-time]", root);
    timeEl.textContent = P.fmtTime(seconds);
    const timer = setInterval(() => {
      if (!document.body.contains(timeEl)) return clearInterval(timer);
      if (finished) return;
      seconds++; timeEl.textContent = P.fmtTime(seconds);
      if (seconds % 5 === 0) save();
    }, 1000);
    board.setAttribute("viewBox", `0 0 ${n * 10} ${n * 10}`);
    const xy = (c) => [(c % n) * 10 + 5, Math.floor(c / n) * 10 + 5];
    const cellAt = (e) => {
      const r = board.getBoundingClientRect(), x = Math.floor(((e.clientX - r.left) / r.width) * n), y = Math.floor(((e.clientY - r.top) / r.height) * n);
      return x >= 0 && x < n && y >= 0 && y < n ? y * n + x : -1;
    };
    function finish() {
      finished = true;
      P.store.add("cam-won"); P.logActivity();
      const k = "cam-best-" + lk, b = P.store.get(k, 0) || 0;
      if (!b || seconds < b) P.store.set(k, seconds);
      if (!inf) { P.store.set("daily-done-caminhos", today); P.setResult("caminhos", today, true); }
      P.confetti(); P.fx.win();
    }
    function draw() {
      const filled = g.paths.reduce((a, p) => a + p.length, 0), joined = q.pairs.filter((_, k) => P.caminhos.connected(g, k)).length;
      P.$("[data-status]", root).textContent = finished ? `Grade completa em ${P.fmtTime(seconds)}! 🎉` : `${joined} de ${q.pairs.length} pares · ${Math.round((filled / (n * n)) * 100)}% da grade`;
      let s = "";
      for (let c = 0; c < n * n; c++) { const [x, y] = xy(c); s += `<rect x="${x - 4.6}" y="${y - 4.6}" width="9.2" height="9.2" rx="2.4" class="cam-cell"/>`; }
      g.paths.forEach((p, k) => {
        if (p.length) { const col = COLORS[k % COLORS.length]; p.forEach((c) => { const [x, y] = xy(c); s += `<rect x="${x - 4.6}" y="${y - 4.6}" width="9.2" height="9.2" rx="2.4" fill="${col}" opacity=".22"/>`; }); }
        if (p.length > 1) s += `<polyline points="${p.map((c) => xy(c).join(",")).join(" ")}" fill="none" stroke="${COLORS[k % COLORS.length]}" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round"/>`;
      });
      q.pairs.forEach((p, k) => p.forEach((c) => { const [x, y] = xy(c); s += `<circle cx="${x}" cy="${y}" r="3.3" fill="${COLORS[k % COLORS.length]}"/>`; }));
      board.innerHTML = s;
      const A = P.$("[data-actions]", root);
      A.innerHTML = finished ? `<button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-new>${inf ? "Nova grade" : "Jogar o Infinito"}</button>` : `<button class="pill ghost" data-clear>Limpar</button>`;
      const cl = P.$("[data-clear]", A); if (cl) cl.onclick = () => { g.paths = q.pairs.map(() => []); g.active = -1; P.fx.type(); save(); draw(); };
      const sh = P.$("[data-share]", A); if (sh) sh.onclick = () => P.share(`Caminhos ${LEVELS[level][1]} ${inf ? "" : P.shortDay(today) + " "}🔗 em ${P.fmtTime(seconds)}\nhttps://dmwnezes.com/termo/`);
      const nw = P.$("[data-new]", A); if (nw) nw.onclick = () => { P.store.set(`cam-inf-state-${lk}`, null); P.games.caminhos(root, true, level); };
    }
    board.onpointerdown = (e) => {
      if (finished) return;
      const c = cellAt(e); if (c < 0) return;
      P.caminhos.start(g, c);
      if (g.active >= 0) { board.setPointerCapture(e.pointerId); P.fx.type(); draw(); }
      e.preventDefault();
    };
    board.onpointermove = (e) => {
      if (finished || g.active < 0 || !board.hasPointerCapture(e.pointerId)) return;
      const c = cellAt(e); if (c < 0) return;
      const before = g.paths[g.active].length;
      P.caminhos.dragTo(g, c);
      if (g.paths[g.active].length !== before) { draw(); }
    };
    board.onpointerup = board.onpointercancel = () => {
      g.active = -1; save();
      if (!finished && P.caminhos.won(g)) { finish(); save(); }
      draw();
    };
    P.caminhosState = () => g;
    draw();
  };
})();
