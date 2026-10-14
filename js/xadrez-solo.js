// Xeque — Xadrez (site): tabuleiro (P.xboard, usado também na partida com amigo) e a partida contra o computador.
(function () {
  "use strict";
  const X = () => P.xadrez;
  const GLYPH = { k: "♚", q: "♛", r: "♜", b: "♝", n: "♞", p: "♟" };
  const pieceHTML = (p, cls = "") => `<i class="xz-p ${X().white(p) ? "w" : "b"}${cls}">${GLYPH[p.toLowerCase()]}&#xFE0E;</i>`;
  const WORTH = { p: 1, n: 3, b: 3, r: 5, q: 9 };
  const INIT = { p: 8, n: 2, b: 2, r: 2, q: 1 };
  const WHY = {
    mate: "Xeque-mate.", afogamento: "Afogamento: sem lances e sem xeque.", material: "Material insuficiente para dar mate.",
    50: "Regra dos 50 lances sem captura nem lance de peão.", repeticao: "A mesma posição se repetiu 3 vezes.",
  };

  // ---------- tabuleiro ----------
  /** Casa mostrada na posição i (0 = canto de cima à esquerda). */
  const sqAt = (i, flip) => { const s = (7 - (i >> 3)) * 8 + (i & 7); return flip ? 63 - s : s; };
  const html = (flip) => `<div class="xz-board" data-xboard>${Array.from({ length: 64 }, (_, i) => {
    const sq = sqAt(i, flip), dark = ((sq >> 3) + (sq & 7)) % 2 === 0;
    return `<div class="xz-c ${dark ? "dark" : "light"}" data-sq="${sq}"><span class="xz-pc"></span>${(i & 7) === 0 ? `<small class="xz-r">${(sq >> 3) + 1}</small>` : ""}${i >> 3 === 7 ? `<small class="xz-f">${"abcdefgh"[sq & 7]}</small>` : ""}</div>`;
  }).join("")}</div>`;
  /** Pinta a posição da partida g. o = {sel, can, side: "w"|"b" (quem toca)}. */
  function paint(el, g, o = {}) {
    const st = g.st, last = g.list[g.list.length - 1];
    const check = X().inCheck(st) ? st.b.indexOf(st.w ? "K" : "k") : -1;
    const dests = new Map();
    if (o.can && o.sel != null) g.legal.filter((m) => m.f === o.sel).forEach((m) => dests.set(m.t, !!st.b[m.t] || m.ep));
    el.classList.toggle("off", !o.can);
    el.querySelectorAll("[data-sq]").forEach((c) => {
      const sq = +c.dataset.sq, dark = ((sq >> 3) + (sq & 7)) % 2 === 0, p = st.b[sq];
      c.className = `xz-c ${dark ? "dark" : "light"}${last && (last.f === sq || last.t === sq) ? " last" : ""}${sq === check ? " check" : ""}${sq === o.sel ? " sel" : ""}${dests.has(sq) ? (dests.get(sq) ? " dest cap" : " dest") : ""}`;
      const h = p ? pieceHTML(p) : "", box = c.firstElementChild;
      if (box.innerHTML !== h) box.innerHTML = h;
    });
  }
  /** Peças capturadas por cada lado e a vantagem de material (em peões) das brancas. */
  function material(b) {
    const cnt = {}; b.forEach((p) => { if (p) cnt[p] = (cnt[p] || 0) + 1; });
    const lost = (w) => "qrbnp".split("").flatMap((k) => Array(Math.max(0, INIT[k] - (cnt[w ? k.toUpperCase() : k] || 0))).fill(w ? k.toUpperCase() : k));
    const sum = (w) => "qrbnp".split("").reduce((a, k) => a + WORTH[k] * (cnt[w ? k.toUpperCase() : k] || 0), 0);
    return { byW: lost(false), byB: lost(true), adv: sum(true) - sum(false) };
  }
  /** Linha de um jogador: nome, peças que ele capturou e "+3" se estiver na frente. */
  function sideRow(g, side, label, turn) {
    const m = material(g.st.b), caps = side === "w" ? m.byW : m.byB, adv = side === "w" ? m.adv : -m.adv;
    return `<span class="xz-name${turn ? " turn" : ""}"><i class="xz-dot ${side}"></i>${label}</span>
      <span class="xz-caps">${caps.map((p) => pieceHTML(p, " mini")).join("")}${adv > 0 ? `<em>+${adv}</em>` : ""}</span>`;
  }
  /** Escolha da peça da promoção: Promise com "q" | "r" | "b" | "n" ou null. */
  const promo = (side) => new Promise((done) => {
    let v = null;
    const ui = P.sheet(`<h2>Promover para</h2><div class="xz-promo">${["q", "r", "b", "n"].map((k) => `<button class="xz-pick" data-pr="${k}" aria-label="${{ q: "Dama", r: "Torre", b: "Bispo", n: "Cavalo" }[k]}">${pieceHTML(side === "w" ? k.toUpperCase() : k)}</button>`).join("")}</div>`, () => done(v));
    ui.el.querySelectorAll("[data-pr]").forEach((b) => (b.onclick = () => { v = b.dataset.pr; ui.close(); }));
  });
  /** Toque numa casa. ctx = {g, side, sel, can(), play(lance), redraw()}. */
  async function tap(ctx, sq) {
    if (!ctx.can()) return;
    const g = ctx.g, p = g.st.b[sq], mine = p && X().white(p) === (ctx.side === "w");
    if (ctx.sel != null) {
      const ms = g.legal.filter((m) => m.f === ctx.sel && m.t === sq);
      if (ms.length) {
        let m = ms[0];
        if (ms.length > 1) {
          const k = await promo(ctx.side);
          if (!k || !ctx.can() || ctx.g !== g) return;
          m = ms.find((x) => x.p === k);
        }
        ctx.sel = null;
        return ctx.play(m);
      }
    }
    if (mine && g.legal.some((m) => m.f === sq) && ctx.sel !== sq) { ctx.sel = sq; P.fx.type(); }
    else ctx.sel = null;
    ctx.redraw();
  }
  /** Som do lance: captura mais forte, xeque com dois toques. */
  const moveFx = (g, m, mine) => {
    if (X().inCheck(g.st)) { P.tone([[659, 60], [880, 90]], 0.09); P.vibrate([25, 40, 25]); }
    else if (m.cap) { P.tone([[392, 60], [523, 90]], 0.09); P.vibrate(40); }
    else P.tone([[mine ? 440 : 523, 45]], 0.06);
  };
  /** Joga e anota se foi captura (para o som). */
  const push = (g, m) => { const cap = !!g.st.b[m.t] || m.ep, r = X().push(g, m); if (r) r.cap = cap; return r; };
  P.xboard = { html, paint, material, sideRow, promo, tap, moveFx, push, pieceHTML, WHY };

  // ---------- computador num Web Worker (cai para a thread principal se não der) ----------
  let worker = null, wid = 0;
  const pending = {};
  function ask(st, level) {
    const id = ++wid;
    return new Promise((done) => {
      try {
        if (!worker) {
          const src = [...document.scripts].map((s) => s.src).find((s) => /js\/xadrez\.js/.test(s)) || "js/xadrez.js";
          worker = new Worker(src);
          worker.onmessage = (e) => { const f = pending[e.data.id]; delete pending[e.data.id]; if (f) f(e.data.u); };
          worker.onerror = () => { worker = null; Object.keys(pending).forEach((k) => { const f = pending[k]; delete pending[k]; f(null); }); };
        }
        pending[id] = (u) => done(u || X().best(st, level));
        worker.postMessage({ id, fen: X().toFEN(st), level });
      } catch (_) { setTimeout(() => done(X().best(st, level)), 20); }
    });
  }

  // ---------- partida contra o computador ----------
  const LEVELS = [["facil", "Fácil"], ["medio", "Médio"], ["dificil", "Difícil"]];
  const HELP = "Xeque é o Xadrez do Palavreiro, contra o computador. Mova as peças tocando nelas e depois na casa de destino (os pontos mostram os lances possíveis). " +
    "Valem todas as regras: roque, en passant, promoção (você escolhe a peça), xeque-mate e empates por afogamento, material insuficiente, " +
    "50 lances sem captura e tripla repetição. Fácil, Médio e Difícil: o computador pensa 1, 2 ou 3 lances à frente. " +
    "“Desfazer” volta o seu último lance e a resposta do computador.";
  const SAVE = "xz-game";

  P.games.xadrez = function (root) {
    root.innerHTML = P.topbar("Xeque", { help: true }) + `<div class="game xz" data-body></div>`;
    const body = P.$("[data-body]", root);
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Xeque</h2><p>${HELP}</p>`);
    let lv = Math.max(0, LEVELS.findIndex(([k]) => k === P.store.get("xz-level", "facil")));
    let pickSide = P.store.get("xz-side", "w");
    let ctx = null;

    function setup() {
      ctx = null;
      const won = LEVELS.map(([k, n]) => `${n} ${P.store.get("xadrez-won-" + k, 0) || 0}`).join(" · ");
      body.innerHTML = `<div class="section xz-setup">
          <h3>Nível do computador</h3>
          <div class="seg" data-lvs>${LEVELS.map(([, n], i) => `<button data-lv="${i}" class="${i === lv ? "on" : ""}">${n}</button>`).join("")}</div>
          <h3>Você joga de</h3>
          <div class="seg" data-sides>${[["w", "Brancas"], ["b", "Pretas"], ["r", "Sorteio"]].map(([k, n]) => `<button data-side="${k}" class="${k === pickSide ? "on" : ""}">${n}</button>`).join("")}</div>
          <button class="pill wide" data-start style="margin-top:16px">Começar partida</button>
          <p class="muted center xz-wins">Vitórias: ${won}</p></div>`;
      body.querySelectorAll("[data-lv]").forEach((b) => (b.onclick = () => { lv = +b.dataset.lv; P.store.set("xz-level", LEVELS[lv][0]); P.fx.type(); setup(); }));
      body.querySelectorAll("[data-side]").forEach((b) => (b.onclick = () => { pickSide = b.dataset.side; P.store.set("xz-side", pickSide); P.fx.type(); setup(); }));
      P.$("[data-start]", body).onclick = () => {
        const me = pickSide === "r" ? (Math.random() < 0.5 ? "w" : "b") : pickSide;
        start({ lv: LEVELS[lv][0], me, u: [] });
      };
    }

    function save() { if (ctx && !ctx.g.end && !ctx.over) P.store.set(SAVE, { lv: ctx.lv, me: ctx.side, u: ctx.g.list.map(X().uci) }); }
    const forget = () => { try { localStorage.removeItem("pv-" + SAVE); } catch (_) {} };

    function start(sv) {
      const g = X().game(sv.u);
      const lvi = Math.max(0, LEVELS.findIndex(([k]) => k === sv.lv));
      ctx = { g, side: sv.me, lv: LEVELS[lvi][0], lvi, sel: null, thinking: false, over: null, token: {} };
      ctx.can = () => !!ctx && !ctx.over && !ctx.g.end && !ctx.thinking && (ctx.g.st.w ? "w" : "b") === ctx.side && document.body.contains(body);
      ctx.play = (m) => { const r = push(ctx.g, m); if (!r) return; moveFx(ctx.g, r, true); after(); };
      ctx.redraw = draw;
      const flip = sv.me === "b";
      body.innerHTML = `<div class="xz-side" data-top></div>${html(flip)}<div class="xz-side" data-bot></div>
        <p class="xz-status" data-status></p>
        <div class="row-btns xz-btns"><button class="pill ghost" data-undo>Desfazer</button><button class="pill ghost" data-resign>Desistir</button></div>`;
      P.$("[data-xboard]", body).addEventListener("click", (e) => { const c = e.target.closest("[data-sq]"); if (c && ctx) tap(ctx, +c.dataset.sq); });
      P.$("[data-undo]", body).onclick = undo;
      P.$("[data-resign]", body).onclick = () => {
        if (!ctx || ctx.over) return;
        if (ctx.g.end) return setup();
        const ui = P.sheet(`<h2>Desistir da partida?</h2><p class="subtitle">O computador ganha esta.</p>
          <div class="row-btns"><button class="pill ghost" data-no>Continuar</button><button class="pill danger" data-yes>Desistir</button></div>`);
        P.$("[data-no]", ui.el).onclick = ui.close;
        P.$("[data-yes]", ui.el).onclick = () => { ui.close(); finish({ res: ctx.side === "w" ? "b" : "w", why: "desistiu" }); };
      };
      if (g.end) return finish(g.end);
      save(); draw(); think();
    }

    function draw() {
      if (!ctx || !document.body.contains(body)) return;
      const g = ctx.g, me = ctx.side, them = me === "w" ? "b" : "w", turn = g.st.w ? "w" : "b", end = ctx.over || g.end;
      paint(P.$("[data-xboard]", body), g, { sel: ctx.sel, can: ctx.can() });
      P.$("[data-top]", body).innerHTML = sideRow(g, them, `🤖 Computador · ${LEVELS[ctx.lvi][1]}`, !end && turn === them);
      P.$("[data-bot]", body).innerHTML = sideRow(g, me, "Você", !end && turn === me);
      const st = P.$("[data-status]", body), chk = X().inCheck(g.st);
      st.className = "xz-status" + (!end && turn === me ? " mine" : "") + (!end && chk ? " chk" : "");
      st.textContent = end ? (end.res === "draw" ? "Empate" : end.res === me ? "Você venceu! ♟️" : "O computador venceu")
        : turn === me ? (chk ? "Xeque! Sua vez" : "Sua vez") : (chk ? "Xeque! " : "") + "Computador pensando…";
      const u = P.$("[data-undo]", body), r = P.$("[data-resign]", body);
      u.disabled = !!end || ctx.thinking || g.list.length < (me === "b" ? 3 : 2);
      r.textContent = end ? "Nova partida" : "Desistir";
      r.classList.toggle("ghost", !end);
    }

    function after() {
      save(); draw();
      if (ctx.g.end) return finish(ctx.g.end);
      think();
    }

    async function think() {
      if (!ctx || ctx.g.end || ctx.over || (ctx.g.st.w ? "w" : "b") === ctx.side) return;
      const c = ctx, token = (c.token = {}), t0 = Date.now();
      c.thinking = true; draw();
      const u = await ask(c.g.st, c.lvi);
      await P.sleep(Math.max(0, 450 - (Date.now() - t0)));
      if (ctx !== c || c.token !== token || !document.body.contains(body)) return;
      c.thinking = false;
      const m = u && push(c.g, u);
      if (m) moveFx(c.g, m, false);
      after();
    }

    function undo() {
      if (!ctx || ctx.thinking || ctx.over || ctx.g.end) return;
      const n = ctx.g.list.length - 2;
      if (n < (ctx.side === "b" ? 1 : 0)) return;
      const keep = ctx.g.list.slice(0, n).map(X().uci);
      ctx.g = X().game(keep); ctx.sel = null;
      P.fx.type(); save(); draw();
    }

    function finish(end) {
      if (!ctx || ctx.over) return;
      ctx.over = end; ctx.sel = null; forget();
      const win = end.res === ctx.side;
      if (win) P.store.add("xadrez-won-" + ctx.lv);
      P.logActivity();
      draw();
      if (win) { P.confetti(); P.fx.win(); } else if (end.res !== "draw") P.fx.lose();
      const lvName = LEVELS[ctx.lvi][1], c = ctx;
      setTimeout(() => {
        if (ctx !== c || !document.body.contains(body) || document.querySelector(".overlay")) return;
        const why = end.why === "desistiu" ? "Você desistiu." : WHY[end.why];
        const ui = P.sheet(`<div class="mp-sheet-ico" aria-hidden="true">${win ? "🏆" : end.res === "draw" ? "🤝" : "♟️"}</div>
          <h2>${win ? "Você venceu!" : end.res === "draw" ? "Empate" : "O computador venceu"}</h2>
          <p class="subtitle">${why} Nível ${lvName} · ${Math.ceil(c.g.list.length / 2)} ${Math.ceil(c.g.list.length / 2) === 1 ? "lance" : "lances"}.</p>
          ${P.statsHTML(LEVELS.map(([k, n]) => [P.store.get("xadrez-won-" + k, 0) || 0, n]), "three")}
          <div class="row-btns"><button class="pill ghost" data-see>Ver tabuleiro</button><button class="pill" data-new>Nova partida</button></div>`);
        P.$("[data-see]", ui.el).onclick = ui.close;
        P.$("[data-new]", ui.el).onclick = () => { ui.close(); setup(); };
      }, win || end.why === "desistiu" ? 600 : 1200);
    }

    const sv = P.store.get(SAVE, null);
    if (sv && Array.isArray(sv.u) && (sv.me === "w" || sv.me === "b")) start(sv); else setup();
    // Testes: estado da partida
    P.xzState = () => ctx && { fen: X().toFEN(ctx.g.st), u: ctx.g.list.map(X().uci), side: ctx.side, lv: ctx.lv, thinking: ctx.thinking, end: ctx.over || ctx.g.end };
  };
})();
