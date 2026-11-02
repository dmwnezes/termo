// Palavra Cruzada (site): grade 5×5 com 3 palavras na horizontal e 3 na vertical. Dados em shared/cruzada.txt.
// Do dia: linha = (dia × 7919 + 104729) mod N, igual no app. Conferir pinta as letras certas; sem limite de tentativas.
(function () {
  "use strict";
  const HELP = "Toque numa casa e digite. Toque de novo na mesma casa para trocar entre horizontal e vertical, ou toque numa pista. " +
    "Conferir deixa verdes e travadas as letras certas e marca as erradas. Revelar mostra a letra da casa escolhida. Termine com a grade toda verde.";
  const SIZE = 5, ROWS = [0, 2, 4];
  const open = (r, c) => r % 2 === 0 || c % 2 === 0;
  const NUM = { "0,0": 1, "0,2": 2, "0,4": 3, "2,0": 4, "4,0": 5 };

  P.cruzadaParse = (t) => t.split("\n").filter((l) => l.trim() && !l.startsWith("#")).map((l) => {
    const f = l.split("|"), w = f[0].split(",");
    return { across: w.slice(0, 3), down: w.slice(3, 6), clues: f.slice(1, 7) };
  });
  P.cruzadaIdx = (day, n) => ((day * 7919 + 104729) % n + n) % n;
  /** A letra certa de cada casa ("" nas pretas). */
  P.cruzadaSolution = (q) => Array.from({ length: SIZE }, (_, r) => Array.from({ length: SIZE }, (_, c) => (r % 2 === 0 ? q.across[r / 2][c] : c % 2 === 0 ? q.down[c / 2][r] : "")));

  P.games.cruzada = async function (root, inf = !!P.store.get("cruz-inf", false)) {
    P.store.set("cruz-inf", inf);
    root.innerHTML = P.topbar("Palavra Cruzada", { help: true }) + P.modeSwitch(inf) + `<div class="game cz" data-stage></div>`;
    const stage = P.$("[data-stage]", root);
    const list = P.cruzadaParse(await P.text("cruzada.txt").catch(() => ""));
    if (!document.body.contains(stage)) return;
    P.bindModeSwitch(root, (v) => P.games.cruzada(root, v));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar a Palavra Cruzada</h2><p>${HELP}</p>`);
    if (!list.length) { stage.innerHTML = `<p class="center muted">Não foi possível carregar as grades.</p>`; return; }

    const today = P.dayKey(), key = inf ? "cruz-inf-state" : "cruz-state";
    let saved = P.store.get(key, null);
    if (saved && !inf && saved.day !== today) saved = null;
    if (saved && (!(saved.idx < list.length) || !Array.isArray(saved.g))) saved = null;
    const idx = saved ? saved.idx : inf ? Math.floor(Math.random() * list.length) : P.cruzadaIdx(P.dayIndex(), list.length);
    const q = list[idx], sol = P.cruzadaSolution(q);
    const blank = () => Array.from({ length: SIZE }, () => Array(SIZE).fill(""));
    const g = saved ? saved.g : blank(), lock = saved ? saved.lock : blank(), bad = blank();
    let checks = saved ? saved.checks || 0 : 0, won = saved ? !!saved.won : false, r = 0, c = 0, across = true;
    const save = () => P.store.set(key, { day: today, idx, g, lock, checks, won });
    const wordCells = (rr, cc, ac) => (ac ? [0, 1, 2, 3, 4].map((k) => [rr, k]) : [0, 1, 2, 3, 4].map((k) => [k, cc]));
    const canAcross = (rr) => rr % 2 === 0, canDown = (cc) => cc % 2 === 0;
    /** Índice da pista da palavra atual (0–2 horizontais, 3–5 verticais). */
    const clueOf = () => (across ? r / 2 : 3 + c / 2);

    function draw() {
      const cells = new Set(wordCells(r, c, across).map((x) => x.join()));
      let grid = "";
      for (let y = 0; y < SIZE; y++) for (let x = 0; x < SIZE; x++) {
        if (!open(y, x)) { grid += `<div class="cz-b"></div>`; continue; }
        const cls = [lock[y][x] ? "ok" : bad[y][x] ? "bad" : "", cells.has(`${y},${x}`) && !won ? "in" : "", y === r && x === c && !won ? "at" : ""].join(" ");
        const n = NUM[`${y},${x}`];
        grid += `<button class="cz-c ${cls}" data-c="${y},${x}" aria-label="Linha ${y + 1}, coluna ${x + 1}${g[y][x] ? ", " + g[y][x] : ", vazia"}">${n ? `<small>${n}</small>` : ""}${g[y][x]}</button>`;
      }
      const clue = (i) => `<button class="cz-clue${!won && clueOf() === i ? " on" : ""}" data-clue="${i}"><b>${i < 3 ? [1, 4, 5][i] : i - 2}</b><span>${P.esc(q.clues[i])}</span></button>`;
      stage.querySelector(".cz-top")?.remove();
      const top = P.h(`<div class="cz-top">
        <p class="hint">${won ? `Resolvida com ${checks} ${checks === 1 ? "conferência" : "conferências"}! ✏️` : `${across ? "Horizontal" : "Vertical"} · ${P.esc(q.clues[clueOf()])}`}</p>
        <div class="cz-grid">${grid}</div>
        ${won ? `<div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-next>${inf ? "Outra grade" : "Jogar o Infinito"}</button></div>`
          : `<div class="row-btns"><button class="pill ghost" data-reveal>Revelar letra</button><button class="pill" data-check>Conferir</button></div>`}
        <div class="cz-clues"><div><h3>Horizontais</h3>${[0, 1, 2].map(clue).join("")}</div><div><h3>Verticais</h3>${[3, 4, 5].map(clue).join("")}</div></div></div>`);
      stage.prepend(top);
      top.querySelectorAll("[data-c]").forEach((b) => (b.onclick = () => {
        const [y, x] = b.dataset.c.split(",").map(Number);
        if (y === r && x === c && canAcross(y) && canDown(x)) across = !across;
        else { r = y; c = x; across = canAcross(y) && (across || !canDown(x)); }
        P.fx.type(); draw();
      }));
      top.querySelectorAll("[data-clue]").forEach((b) => (b.onclick = () => {
        const i = +b.dataset.clue;
        across = i < 3;
        if (across) { r = i * 2; c = 0; } else { c = (i - 3) * 2; r = 0; }
        firstFree(); P.fx.type(); draw();
      }));
      const rv = P.$("[data-reveal]", top); if (rv) rv.onclick = reveal;
      const ck = P.$("[data-check]", top); if (ck) ck.onclick = () => kb.onEnter();
      const sh = P.$("[data-share]", top); if (sh) sh.onclick = () => P.share(`Palavra Cruzada ${inf ? "" : P.shortDay(today) + " "}✏️ resolvida com ${checks} ${checks === 1 ? "conferência" : "conferências"}\nhttps://dmwnezes.com/termo/`);
      const nx = P.$("[data-next]", top); if (nx) nx.onclick = () => { P.store.set("cruz-inf-state", null); P.games.cruzada(root, true); };
      const kb = P.$(".keyboard", stage); if (kb) kb.style.display = won ? "none" : "";
    }
    /** Vai para a primeira casa livre da palavra atual. */
    function firstFree() {
      const f = wordCells(r, c, across).find(([y, x]) => !lock[y][x] && !g[y][x]);
      if (f) [r, c] = f;
    }
    function step(d) {
      const cells = wordCells(r, c, across);
      for (let i = cells.findIndex(([y, x]) => y === r && x === c) + d; i >= 0 && i < SIZE; i += d) {
        const [y, x] = cells[i];
        if (!lock[y][x]) { r = y; c = x; return true; }
      }
      return false;
    }
    function finish() {
      won = true;
      P.store.add("cruz-won"); if (checks <= 1) P.store.add("cruz-perfect"); P.logActivity();
      if (!inf) { P.store.set("daily-done-cruzada", today); P.setResult("cruzada", today, true); }
      P.confetti(); P.fx.win();
    }
    const allOk = () => sol.every((line, y) => line.every((v, x) => !v || lock[y][x]));
    function reveal() {
      if (won || lock[r][c]) return;
      g[r][c] = sol[r][c]; lock[r][c] = sol[r][c]; bad[r][c] = "";
      if (allOk()) finish(); else P.fx.type();
      save(); draw();
    }
    const kb = { onLetter(l) {
      if (won) return;
      if (!lock[r][c]) { g[r][c] = l; bad[r][c] = ""; P.fx.type(); step(1); save(); draw(); }
      else if (step(1)) draw();
    }, onDelete() {
      if (won) return;
      if (!lock[r][c] && g[r][c]) g[r][c] = "";
      else if (step(-1)) g[r][c] = "";
      P.fx.type(); save(); draw();
    }, onEnter() {
      if (won) return;
      checks++;
      let wrong = 0;
      sol.forEach((line, y) => line.forEach((v, x) => { if (!v || lock[y][x] || !g[y][x]) return; if (g[y][x] === v) lock[y][x] = v; else { bad[y][x] = "1"; wrong++; } }));
      if (allOk()) finish(); else if (wrong) P.fx.invalid(); else P.fx.type();
      save(); draw();
    }, enterLabel: "OK" };
    P.keyboard(stage, kb);
    P.cruzadaState = () => ({ inf, idx, g, lock, sol, checks, won, r, c, across });
    firstFree(); draw();
  };
})();
