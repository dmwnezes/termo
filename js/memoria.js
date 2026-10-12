// Jogo da Memória (site): cartas viradas para baixo; cada par é uma palavra e o seu sinônimo (shared/sinonimos.txt).
// Os pares sorteados não têm família de sentido em comum, então cada carta tem um único par certo.
(function () {
  "use strict";
  const LEVELS = [
    { k: "facil", name: "Fácil", pairs: 6, cols: 3 },
    { k: "medio", name: "Médio", pairs: 8, cols: 4 },
    { k: "dificil", name: "Difícil", pairs: 12, cols: 4 },
  ];
  const MAX_LEN = 11; // palavras compridas não cabem na carta
  const HELP = "Vire duas cartas por vez. Cada palavra tem um par: o seu sinônimo (ALEGRE e CONTENTE, por exemplo). " +
    "Se as duas combinarem, ficam verdes; se não, viram de novo. Ache todos os pares com o menor número de jogadas e no menor tempo. " +
    "Fácil: 6 pares · Médio: 8 pares · Difícil: 12 pares.";

  /** {pairs: [[a, b]], fams: Map(palavra normalizada → Set de famílias)} */
  const parse = (t) => {
    const fams = new Map(), pairs = [];
    let fi = 0;
    t.split("\n").forEach((raw) => {
      const l = raw.trim();
      if (!l || l.startsWith("#")) return;
      if (l.startsWith("=")) {
        const id = "f" + fi++;
        l.slice(1).split(",").map((w) => P.norm(w.trim())).filter(Boolean).forEach((w) => { if (!fams.has(w)) fams.set(w, new Set()); fams.get(w).add(id); });
        return;
      }
      const f = l.split("|").map((s) => s.trim());
      if (f.length >= 2 && f[0] && f[1] && P.norm(f[0]) !== P.norm(f[1])) pairs.push([f[0].toUpperCase(), f[1].toUpperCase()]);
    });
    return { pairs, fams };
  };

  /** Sorteia n pares sem palavras repetidas e sem famílias em comum entre pares diferentes. */
  const pickPairs = ({ pairs, fams }, n) => {
    const tags = (p) => {
      const out = new Set();
      p.forEach((w) => { const k = P.norm(w); out.add("w:" + k); (fams.get(k) || []).forEach((f) => out.add(f)); });
      return out;
    };
    for (let tries = 0; tries < 20; tries++) {
      const chosen = [], taken = new Set();
      for (const p of P.shuffle(pairs.filter((p) => p.every((w) => w.length <= MAX_LEN)))) {
        const t = tags(p);
        if ([...t].some((x) => taken.has(x))) continue;
        chosen.push(p); t.forEach((x) => taken.add(x));
        if (chosen.length === n) return chosen;
      }
    }
    return null;
  };
  P.memPickPairs = (t, n) => pickPairs(parse(t), n);

  P.games.memoria = async function (root) {
    let lv = Math.max(0, LEVELS.findIndex((l) => l.k === P.store.get("mem-level", "facil")));
    root.innerHTML = P.topbar("Jogo da Memória", { help: true }) + `<div class="game mem">
      <div class="seg mem-levels" data-levels>${LEVELS.map((l, i) => `<button data-lv="${i}">${l.name}</button>`).join("")}</div>
      <div class="score-row mem-hud"><span><small>Jogadas</small><b data-moves>0</b></span><span><small>Pares</small><b data-pairs>0/0</b></span><span><small>Tempo</small><b data-time>0:00</b></span></div>
      <div class="mem-grid" data-grid></div>
      <p class="center muted mem-best" data-best></p></div>`;
    const grid = P.$("[data-grid]", root);
    const data = parse(await P.text("sinonimos.txt").catch(() => ""));
    if (!document.body.contains(grid)) return;

    let cards = [], open = [], found = 0, moves = 0, start = 0, secs = 0, clock = 0, lock = false, over = false;
    P.memState = () => ({ level: LEVELS[lv].k, cards: cards.map((c) => ({ pair: c.pair, word: c.word, up: c.up, ok: c.ok })), moves, found, secs, over });

    const fmt = P.fmtTime || ((s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`);
    function hud() {
      P.$("[data-moves]", root).textContent = moves;
      P.$("[data-pairs]", root).textContent = `${found}/${LEVELS[lv].pairs}`;
      P.$("[data-time]", root).textContent = fmt(secs);
      const L = LEVELS[lv], bt = P.store.get("mem-best-" + L.k, 0), bm = P.store.get("mem-moves-" + L.k, 0);
      P.$("[data-best]", root).textContent = bt ? `Recorde no ${L.name}: ${fmt(bt)} · ${bm} ${bm === 1 ? "jogada" : "jogadas"}` : `Sem recorde no ${L.name} ainda`;
      root.querySelectorAll("[data-lv]").forEach((b) => b.classList.toggle("on", +b.dataset.lv === lv));
    }
    const stopClock = () => { clearInterval(clock); clock = 0; };
    function startClock() {
      if (clock) return;
      start = Date.now() - secs * 1000;
      clock = setInterval(() => {
        if (!document.body.contains(grid)) return stopClock();
        secs = Math.floor((Date.now() - start) / 1000); P.$("[data-time]", root).textContent = fmt(secs);
      }, 250);
    }

    function deal() {
      stopClock();
      document.querySelectorAll(".overlay").forEach((o) => o.remove());
      const L = LEVELS[lv], pairs = pickPairs(data, L.pairs);
      open = []; found = 0; moves = 0; secs = 0; lock = false; over = false;
      if (!pairs) { cards = []; grid.innerHTML = `<p class="center muted">Não foi possível carregar as palavras.</p>`; return hud(); }
      cards = P.shuffle(pairs.flatMap((p, i) => [{ pair: i, word: p[0] }, { pair: i, word: p[1] }])).map((c, i) => Object.assign(c, { i, up: false, ok: false }));
      grid.className = `mem-grid lv-${L.k}`;
      grid.style.gridTemplateColumns = `repeat(${L.cols}, 1fr)`;
      grid.innerHTML = cards.map((c) => `<button class="mem-card" data-c="${c.i}" aria-label="Carta virada"><span class="mem-inner">
          <span class="mem-face mem-back" aria-hidden="true"><i></i></span>
          <span class="mem-face mem-front"><span class="mem-word${c.word.length > 8 ? " long" : ""}">${P.esc(c.word)}</span></span></span></button>`).join("");
      grid.querySelectorAll("[data-c]").forEach((b) => (b.onclick = () => flip(+b.dataset.c)));
      hud();
    }

    const el = (i) => grid.querySelector(`[data-c="${i}"]`);
    function flip(i) {
      const c = cards[i];
      if (!c || lock || over || c.up || c.ok) return;
      startClock();
      c.up = true; el(i).classList.add("up"); el(i).setAttribute("aria-label", c.word);
      P.tone([[520 + open.length * 80, 40]], 0.06); P.vibrate(8);
      open.push(c);
      if (open.length < 2) return;
      moves++;
      const [a, b] = open; open = [];
      if (a.pair === b.pair) {
        a.ok = b.ok = true; found++;
        setTimeout(() => {
          [a, b].forEach((x) => el(x.i) && el(x.i).classList.add("ok"));
          P.fx.reveal(2, "c");
          if (found === LEVELS[lv].pairs) win();
        }, 280);
      } else {
        lock = true;
        setTimeout(() => {
          if (!document.body.contains(grid)) return;
          [a, b].forEach((x) => { x.up = false; const e = el(x.i); if (e) { e.classList.remove("up"); e.classList.add("miss"); e.setAttribute("aria-label", "Carta virada"); setTimeout(() => e.classList.remove("miss"), 400); } });
          lock = false;
        }, 900);
        P.tone([[300, 60]], 0.05);
      }
      hud();
    }

    function win() {
      over = true; stopClock();
      secs = Math.max(1, Math.floor((Date.now() - start) / 1000));
      const L = LEVELS[lv], prevT = P.store.get("mem-best-" + L.k, 0), prevM = P.store.get("mem-moves-" + L.k, 0);
      P.store.min("mem-best-" + L.k, secs); P.store.min("mem-moves-" + L.k, moves); P.logActivity();
      const recT = !prevT || secs < prevT, recM = !prevM || moves < prevM;
      hud(); P.confetti(); P.fx.win();
      const next = lv < LEVELS.length - 1 ? LEVELS[lv + 1] : null;
      const ui = P.sheet(`<h2>Achou todos os pares! 🃏</h2><p class="subtitle">${L.name} · ${L.pairs} pares${recT || recM ? " · novo recorde!" : ""}</p>
        ${P.statsHTML([[fmt(secs), "Tempo"], [moves, "Jogadas"], [fmt(P.store.get("mem-best-" + L.k, secs)), "Melhor tempo"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>Jogar de novo</button></div>
        ${next ? `<button class="pill accent wide mem-next" data-next>Tentar o ${next.name} ›</button>` : ""}`);
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Jogo da Memória (${L.name})\nAchei os ${L.pairs} pares em ${fmt(secs)} com ${moves} jogadas 🃏`);
      P.$("[data-again]", ui.el).onclick = () => { ui.close(); deal(); };
      if (next) P.$("[data-next]", ui.el).onclick = () => { ui.close(); setLevel(lv + 1); };
    }

    function setLevel(i) { lv = i; P.store.set("mem-level", LEVELS[i].k); deal(); }
    root.querySelectorAll("[data-lv]").forEach((b) => (b.onclick = () => { if (+b.dataset.lv !== lv || over) { P.fx.type(); setLevel(+b.dataset.lv); } }));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar a Memória</h2><p>${HELP}</p>`);
    deal();
  };
})();
