// Ampulheta (site), inspirado no WasThatBefore: encaixe cada acontecimento no lugar certo da linha do tempo.
// Dados em shared/ampulheta.txt (ANO|acontecimento; ano negativo = a.C.). Do dia: 11 acontecimentos pelo MINSTD (igual ao app).
(function () {
  "use strict";
  const DAY_CARDS = 10, LIVES = 3;
  const HELP = "A linha do tempo começa com um acontecimento e o seu ano. Aparece uma carta nova, sem o ano: toque no espaço da linha onde ela se encaixa " +
    "(antes do primeiro, entre dois ou depois do último). Acertou: a carta entra com o ano revelado. Errou: a carta entra no lugar certo mesmo assim. " +
    "Do dia: 10 cartas, a pontuação é o número de acertos. Infinito: cartas sem fim até errar 3 vezes.";

  const parse = (t) => t.split("\n").map((l) => l.trim()).filter((l) => l && !l.startsWith("#")).map((l) => {
    const i = l.indexOf("|"), y = parseInt(l.slice(0, i), 10);
    return i > 0 && Number.isFinite(y) && l.slice(i + 1).trim() ? { y, t: l.slice(i + 1).trim() } : null;
  }).filter(Boolean);
  /** Índices do dia: próximo índice = MINSTD mod N, pulando índices e anos repetidos (11 ou menos se a lista for curta). */
  const dailyIdx = (list, day) => {
    const next = P.minstd(day), out = [], years = new Set();
    for (let k = 0; k < 100000 && out.length < DAY_CARDS + 1; k++) {
      const i = next() % list.length;
      if (out.includes(i) || years.has(list[i].y)) continue;
      out.push(i); years.add(list[i].y);
    }
    return out;
  };
  const yr = (y) => (y < 0 ? `${-y} a.C.` : String(y));
  P.ampDaily = dailyIdx; P.ampParse = parse;

  P.games.ampulheta = async function (root, inf = !!P.store.get("amp-inf", false)) {
    P.store.set("amp-inf", inf);
    root.innerHTML = P.topbar("Ampulheta", { help: true }) + P.modeSwitch(inf) + `<div class="game amp">
      <div class="score-row"><span data-left></span><span><small>Acertos</small><b data-hits>0</b></span></div><div data-stage></div></div>`;
    const stage = P.$("[data-stage]", root);
    const list = parse(await P.text("ampulheta.txt").catch(() => ""));
    if (!document.body.contains(stage)) return;
    P.bindModeSwitch(root, (v) => P.games.ampulheta(root, v));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar a Ampulheta</h2><p>${HELP}</p>`);
    if (list.length < 2) { stage.innerHTML = `<p class="center muted">Não foi possível carregar os acontecimentos.</p>`; return; }

    const today = P.dayKey(), di = P.dayIndex();
    // deck: índices na ordem em que aparecem (o 1º é o inicial). No Infinito, sorteados sem repetir ano.
    let deck, results = [];
    if (inf) {
      const years = new Set();
      deck = P.shuffle(list.map((_, i) => i)).filter((i) => !years.has(list[i].y) && years.add(list[i].y));
    } else {
      deck = dailyIdx(list, di);
      const saved = P.store.get("amp-state", null);
      if (saved && saved.day === today && Array.isArray(saved.results)) results = saved.results.slice(0, deck.length - 1);
    }
    const total = inf ? deck.length - 1 : Math.min(DAY_CARDS, deck.length - 1);
    let last = -1; // índice (na lista) da última carta colocada, para destacar
    const hits = () => results.filter(Boolean).length, lives = () => LIVES - results.filter((r) => !r).length;
    const over = () => results.length >= total || (inf && lives() <= 0);
    const line = () => deck.slice(0, results.length + 1).sort((a, b) => list[a].y - list[b].y);
    P.ampState = () => ({ inf, deck: deck.slice(0, total + 1), years: deck.slice(0, total + 1).map((i) => list[i].y), placed: results.length, hits: hits(), lives: lives(), over: over() });

    function draw() {
      const L = line(), fin = over(), cur = fin ? null : list[deck[results.length + 1]];
      P.$("[data-left]", root).innerHTML = inf ? `<span class="hearts">${[0, 1, 2].map((i) => `<i class="${i < lives() ? "" : "lost"}">❤️</i>`).join("")}</span>`
        : `<small>Carta</small><b>${Math.min(results.length + (fin ? 0 : 1), total)}/${total}</b>`;
      P.$("[data-hits]", root).textContent = hits();
      const gap = (k) => (fin ? "" : `<button class="amp-gap" data-gap="${k}" aria-label="Colocar aqui">＋</button>`);
      const card = (i) => `<div class="amp-card${i === last ? (results[results.length - 1] ? " ok" : " bad") : ""}"><b>${yr(list[i].y)}</b><span>${P.esc(list[i].t)}</span></div>`;
      stage.innerHTML = (fin ? endHTML() : `<div class="amp-card new"><b>?</b><span>${P.esc(cur.t)}</span></div><p class="hint">Toque onde ela se encaixa</p>`) +
        `<div class="amp-line">${gap(0)}${L.map((i, k) => card(i) + gap(k + 1)).join("")}</div>`;
      stage.querySelectorAll("[data-gap]").forEach((b) => (b.onclick = () => place(+b.dataset.gap)));
      if (fin) {
        P.$("[data-share]", stage).onclick = () => P.share(`Ampulheta ${hits()}/${inf ? results.length : total} ⏳\n${results.map((r) => (r ? "🟩" : "🟥")).join("")}`);
        P.$("[data-again]", stage).onclick = () => P.games.ampulheta(root, true);
      }
    }
    const endHTML = () => `<div class="amp-end"><div class="mz-score"><b>${hits()}</b><small>${inf ? (hits() === 1 ? "acerto" : "acertos") : `de ${total}`}</small></div>
      <p class="center muted">${inf ? `Recorde no Infinito: ${P.store.get("amp-best", 0)}` : `Recorde do dia: ${P.store.get("amp-day-best", 0)} · novas cartas amanhã`}</p>
      <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>${inf ? "Jogar de novo" : "Jogar o Infinito"}</button></div></div>`;

    function place(k) {
      if (over()) return;
      const L = line(), i = deck[results.length + 1], y = list[i].y;
      const ok = (k === 0 || list[L[k - 1]].y < y) && (k === L.length || y < list[L[k]].y);
      results.push(ok); last = i;
      if (!inf) P.store.set("amp-state", { day: today, results });
      if (ok) { P.fx.reveal(1, "c"); P.toast("Certo!"); } else { P.fx.invalid(); P.toast(`Era ${yr(y)}`); }
      if (over()) finish();
      draw();
    }

    function finish() {
      P.store.add("amp-played"); P.logActivity();
      if (inf) P.store.max("amp-best", hits());
      else { P.store.max("amp-day-best", hits()); P.store.set("daily-done-ampulheta", today); }
      if (!inf && hits() === total) { P.confetti(); P.fx.win(); }
    }
    draw();
  };
})();
