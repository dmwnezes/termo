// Campo Minado (site): mesma grade do dia do app (Minstd). A partida começa com uma área aberta, então não há primeiro toque perdido.
(function () {
  "use strict";
  const LEVELS = [["facil", "Fácil", 8, 8, 10], ["medio", "Médio", 9, 12, 18], ["dificil", "Difícil", 10, 15, 30]];
  const HELP = "Abra todas as casas sem mina. O número mostra quantas minas há nas 8 casas vizinhas. Use Bandeira para marcar uma casa suspeita. " +
    "Tocar num número com todas as bandeiras ao redor abre as vizinhas restantes. A partida já começa com uma área aberta. O desafio do dia é o mesmo para todo mundo em cada nível.";

  /** Motor puro, igual ao game/Minado.kt. */
  P.minado = {
    LEVELS,
    create(level, seed) {
      const [, , w, h, m] = LEVELS[level], total = w * h, next = P.minstd(seed), cells = Array.from({ length: total }, (_, i) => i);
      for (let i = total - 1; i > 0; i--) { const j = next() % (i + 1); [cells[i], cells[j]] = [cells[j], cells[i]]; }
      const mine = Array(total).fill(false); cells.slice(0, m).forEach((c) => (mine[c] = true));
      const near = (c) => { const y = Math.floor(c / w), x = c % w, o = []; for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) { if (!a && !b) continue; const yy = y + a, xx = x + b; if (yy >= 0 && yy < h && xx >= 0 && xx < w) o.push(yy * w + xx); } return o; };
      const count = Array.from({ length: total }, (_, c) => near(c).filter((n) => mine[n]).length);
      const dist = (c) => (2 * (Math.floor(c / w)) - (h - 1)) ** 2 + (2 * (c % w) - (w - 1)) ** 2;
      // Começo: a casa sem mina com menos minas ao redor, a mais perto do centro.
      const start = Array.from({ length: total }, (_, c) => c).filter((c) => !mine[c]).sort((a, b) => count[a] - count[b] || dist(a) - dist(b) || a - b)[0];
      const g = { w, h, mines: m, mine, count, near, open: Array(total).fill(false), flag: Array(total).fill(false), lost: false, won: false, start };
      P.minado.reveal(g, start);
      return g;
    },
    reveal(g, c) {
      if (g.won || g.lost || g.flag[c] || g.open[c]) return;
      if (g.mine[c]) { g.lost = true; g.boom = c; return; }
      const stack = [c];
      while (stack.length) {
        const k = stack.pop();
        if (g.open[k] || g.flag[k]) continue;
        g.open[k] = true;
        if (!g.count[k]) g.near(k).forEach((n) => { if (!g.open[n]) stack.push(n); });
      }
      g.won = g.open.filter(Boolean).length === g.w * g.h - g.mines;
    },
    /** Toque: abre (ou abre as vizinhas de um número com as bandeiras certas na conta). */
    tap(g, c) {
      if (g.won || g.lost) return;
      if (!g.open[c]) return P.minado.reveal(g, c);
      const around = g.near(c);
      if (g.count[c] && around.filter((n) => g.flag[n]).length === g.count[c]) around.forEach((n) => P.minado.reveal(g, n));
    },
    toggleFlag(g, c) { if (!g.won && !g.lost && !g.open[c]) g.flag[c] = !g.flag[c]; },
  };

  P.games.minado = function (root, inf = !!P.store.get("mina-inf", false), level = P.store.get("mina-level", 0) || 0) {
    P.store.set("mina-inf", inf); P.store.set("mina-level", level);
    const [lk, , w] = LEVELS[level], today = P.dayKey(), key = inf ? `mina-inf-state-${lk}` : `mina-state-${lk}`;
    root.innerHTML = P.topbar("Campo Minado", { help: true, extra: `<b class="muted" data-time style="padding-right:10px">0:00</b>` }) + P.modeSwitch(inf) + `<div class="game mina">
      <div class="seg mina-lv">${LEVELS.map(([, n], i) => `<button class="${i === level ? "on" : ""}" data-lv="${i}">${n}</button>`).join("")}</div>
      <p class="hint" data-status></p><div class="mina-grid" data-grid style="grid-template-columns:repeat(${w},1fr)"></div>
      <div class="row-btns" data-actions></div></div>`;
    P.bindModeSwitch(root, (v) => P.games.minado(root, v, level));
    root.querySelectorAll("[data-lv]").forEach((b) => (b.onclick = () => P.games.minado(root, inf, +b.dataset.lv)));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar o Campo Minado</h2><p>${HELP}</p>`);

    let saved = P.store.get(key, null);
    if (saved && (!inf && saved.day !== today || !Array.isArray(saved.open))) saved = null;
    const seed = saved ? saved.seed : inf ? Math.floor(Math.random() * 1e9) : P.dayIndex() * 3 + level;
    const g = P.minado.create(level, seed);
    if (saved) { g.open = saved.open; g.flag = saved.flag; g.lost = !!saved.lost; g.won = !!saved.won; g.boom = saved.boom; }
    let seconds = saved ? saved.seconds || 0 : 0, flagMode = false, finished = g.won || g.lost;
    const save = () => P.store.set(key, { day: today, seed, open: g.open, flag: g.flag, lost: g.lost, won: g.won, boom: g.boom, seconds });
    const timeEl = P.$("[data-time]", root);
    const timer = setInterval(() => {
      if (!document.body.contains(timeEl)) return clearInterval(timer);
      if (finished) return;
      seconds++; timeEl.textContent = P.fmtTime(seconds);
      if (seconds % 5 === 0) save();
    }, 1000);
    timeEl.textContent = P.fmtTime(seconds);

    function finish() {
      finished = true;
      P.store.add("mina-played"); P.logActivity();
      if (g.won) {
        P.store.add("mina-won");
        const k = "mina-best-" + lk, b = P.store.get(k, 0) || 0;
        if (!b || seconds < b) P.store.set(k, seconds);
        P.confetti(); P.fx.win();
      } else P.fx.lose();
      if (!inf) { P.store.set("daily-done-minado", today); P.setResult("minado", today, g.won); }
    }
    function act(c) {
      if (finished) return;
      if (flagMode && !g.open[c]) { P.minado.toggleFlag(g, c); P.fx.type(); }
      else { P.minado.tap(g, c); if (g.won || g.lost) finish(); else P.fx.type(); }
      save(); draw();
    }
    function draw() {
      const flags = g.flag.filter(Boolean).length, cols = ["", "#6FA8E8", "#5FB873", "#E5737A", "#9B8CFF", "#E6C14F", "#5FD0C5", "#F4F1FF", "#A9A2D0"];
      P.$("[data-status]", root).textContent = g.won ? "Campo limpo! 🎉" : g.lost ? "Boom! Pisou numa mina." : `${g.mines - flags} ${g.mines - flags === 1 ? "mina restante" : "minas restantes"}`;
      const grid = P.$("[data-grid]", root);
      grid.innerHTML = g.open.map((o, c) => {
        const show = o || (finished && g.mine[c]);
        const txt = !show ? (g.flag[c] ? "🚩" : "") : g.mine[c] ? "💣" : g.count[c] || "";
        const label = o ? (g.count[c] ? `${g.count[c]} minas ao redor` : "aberta") : g.flag[c] ? "com bandeira" : "fechada";
        return `<button class="mina-c${o ? " on" : ""}${c === g.boom ? " boom" : ""}" data-c="${c}" aria-label="Linha ${Math.floor(c / g.w) + 1}, coluna ${c % g.w + 1}, ${label}" style="${o && g.count[c] ? `color:${cols[g.count[c]]}` : ""}">${txt}</button>`;
      }).join("");
      grid.querySelectorAll("[data-c]").forEach((b) => {
        const c = +b.dataset.c;
        b.onclick = () => act(c);
        b.oncontextmenu = (e) => { e.preventDefault(); if (!finished) { P.minado.toggleFlag(g, c); P.fx.type(); save(); draw(); } };
      });
      const A = P.$("[data-actions]", root);
      A.innerHTML = finished ? `<button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-new>${inf ? "Nova grade" : "Jogar o Infinito"}</button>`
        : `<button class="pill${flagMode ? "" : " ghost"}" data-flag aria-pressed="${flagMode}">🚩 Bandeira${flagMode ? " ligada" : ""}</button>`;
      const f = P.$("[data-flag]", A); if (f) f.onclick = () => { flagMode = !flagMode; draw(); };
      const s = P.$("[data-share]", A); if (s) s.onclick = () => P.share(`Campo Minado ${LEVELS[level][1]} ${inf ? "" : P.shortDay(today) + " "}${g.won ? "💣 limpo em " + P.fmtTime(seconds) : "💥"}\nhttps://dmwnezes.com/termo/`);
      const n = P.$("[data-new]", A); if (n) n.onclick = () => { P.store.set(`mina-inf-state-${lk}`, null); P.games.minado(root, true, level); };
    }
    P.minadoState = () => g;
    save(); draw();
  };
})();
