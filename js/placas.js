// Placas (site): placas e regras de trânsito do Brasil, com símbolos simplificados. Dados em shared/placas.txt. Do dia: 10 perguntas pelo Minstd (igual ao app). Infinito: até errar 3 vezes.
(function () {
  "use strict";
  const ROUNDS = 10, LIVES = 3;
  const HELP = "Escolha o significado da placa ou a resposta certa sobre as regras. Depois, veja a explicação. Os desenhos são simplificados, e o jogo não substitui o material oficial. Do dia: 10 perguntas. Infinito: até errar 3 vezes.";
  P.placasParse = (t) => t.split("\n").filter((l) => l.trim() && !l.startsWith("#")).map((l) => {
    const f = l.split("|");
    return { shape: f[0], symbol: f[1], text: f[2], options: f.slice(3, 7), why: f[7] };
  });
  P.placasDaily = (n, day) => { const next = P.minstd(day), out = []; while (out.length < Math.min(ROUNDS, n)) { const i = next() % n; if (!out.includes(i)) out.push(i); } return out; };
  /** Ordem fixa das 4 opções de cada pergunta (a certa é a de índice 0 no arquivo). */
  P.placasOrder = (i) => { const next = P.minstd(i), a = [0, 1, 2, 3]; for (let k = 3; k > 0; k--) { const j = next() % (k + 1); [a[k], a[j]] = [a[j], a[k]]; } return a; };
  const sign = (s, sym) => {
    if (s === "regra") return "";
    const t = (size) => (sym ? `<text x="50" y="52" text-anchor="middle" dominant-baseline="central" font-family="Outfit,sans-serif" font-weight="800" font-size="${size}" fill="${s === "pare" ? "#fff" : "#17132F"}">${P.esc(sym)}</text>` : "");
    const size = sym.length > 2 ? 26 : 38;
    const shapes = {
      pare: `<polygon points="30,4 70,4 96,30 96,70 70,96 30,96 4,70 4,30" fill="#C62828" stroke="#fff" stroke-width="3"/>${t(30)}`,
      triangulo: `<polygon points="6,10 94,10 50,90" fill="#fff" stroke="#C62828" stroke-width="10" stroke-linejoin="round"/>`,
      contramao: `<circle cx="50" cy="50" r="45" fill="#C62828"/><rect x="22" y="40" width="56" height="20" fill="#fff"/>`,
      circulo: `<circle cx="50" cy="50" r="42" fill="#fff" stroke="#C62828" stroke-width="9"/>${t(size)}`,
      proibido: `<circle cx="50" cy="50" r="42" fill="#fff" stroke="#C62828" stroke-width="9"/>${t(size)}<line x1="20" y1="20" x2="80" y2="80" stroke="#C62828" stroke-width="8"/>`,
      losango: `<polygon points="50,4 96,50 50,96 4,50" fill="#F2C230" stroke="#17132F" stroke-width="3" stroke-linejoin="round"/>${t(size)}`,
    };
    return `<svg class="pl-sign" viewBox="0 0 100 100" role="img" aria-label="Placa de trânsito">${shapes[s] || ""}</svg>`;
  };

  P.games.placas = async function (root, inf = !!P.store.get("pl-inf", false)) {
    P.store.set("pl-inf", inf);
    root.innerHTML = P.topbar("Placas", { help: true }) + P.modeSwitch(inf) + `<div class="game mm" data-stage></div>`;
    const stage = P.$("[data-stage]", root);
    const list = P.placasParse(await P.text("placas.txt").catch(() => ""));
    if (!document.body.contains(stage)) return;
    P.bindModeSwitch(root, (v) => P.games.placas(root, v));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Placas</h2><p>${HELP}</p>`);
    if (!list.length) { stage.innerHTML = `<p class="center muted">Não foi possível carregar as placas.</p>`; return; }
    const today = P.dayKey(), deck = inf ? P.shuffle(list.map((_, i) => i)) : P.placasDaily(list.length, P.dayIndex());
    const saved = inf ? null : P.store.get("pl-state", null);
    let marks = saved && saved.day === today && Array.isArray(saved.marks) ? saved.marks.slice(0, deck.length) : [];
    let shown = false;
    const hits = () => marks.filter(Boolean).length, misses = () => marks.length - hits();
    const finished = () => misses() >= (inf ? LIVES : 99) || marks.length >= deck.length;
    function pick(i) {
      if (shown || finished()) return;
      const right = i === 0;
      marks.push(right); shown = true;
      if (right) P.fx.win(); else P.fx.invalid();
      if (inf) P.store.set("pl-best", Math.max(P.store.get("pl-best", 0) || 0, hits()));
      else P.store.set("pl-state", { day: today, marks });
      if (finished()) {
        P.store.add("pl-played"); P.logActivity();
        if (!inf) {
          P.store.set("pl-day-best", Math.max(P.store.get("pl-day-best", 0) || 0, hits()));
          P.store.set("daily-done-placas", today); P.setResult("placas", today, hits() >= 5);
          if (hits() === ROUNDS) P.confetti();
        }
      }
      draw();
    }
    function draw() {
      if (finished() && !shown) {
        stage.innerHTML = `<div class="mm-end"><div class="mm-big">${hits()}${inf ? "" : "/" + ROUNDS}</div><p>${inf ? `${hits() === 1 ? "acerto" : "acertos"} · recorde ${P.store.get("pl-best", 0) || 0}` : "acertos no desafio de hoje"}</p>
          ${inf ? "" : `<div class="mm-dots">${marks.map((m) => `<i class="${m ? "ok" : "bad"}"></i>`).join("")}</div>`}
          <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>${inf ? "Jogar de novo" : "Jogar o Infinito"}</button></div></div>`;
        P.$("[data-share]", stage).onclick = () => P.share(`Placas ${inf ? `🚦 ${hits()} acertos` : `${P.shortDay(today)} ${hits()}/${ROUNDS}\n${marks.map((m) => (m ? "🟩" : "🟥")).join("")}`}\nhttps://dmwnezes.com/termo/`);
        P.$("[data-again]", stage).onclick = () => P.games.placas(root, true);
        return;
      }
      const at = shown ? marks.length - 1 : marks.length, qi = deck[at], q = list[qi], right = marks[at];
      stage.innerHTML = `<p class="hint">${inf ? `Acertos: <b>${hits()}</b> · ${"❤️".repeat(Math.max(0, LIVES - misses()))}` : `Pergunta ${at + 1} de ${ROUNDS} · <b>${hits()}</b> ${hits() === 1 ? "acerto" : "acertos"}`}</p>
        ${sign(q.shape, q.symbol)}<div class="mm-theme pl-q">${P.esc(q.text)}</div>
        ${shown ? `<div class="mm-card fm-card pl-ans ${right ? "ok" : "bad"}"><b>${P.esc(q.options[0])}</b></div><p class="fm-verdict">${right ? "Acertou!" : "Errou."}</p><p class="fm-why">${P.esc(q.why)}</p><button class="pill wide" data-next>${finished() ? "Ver resultado" : "Próxima"}</button>`
          : `<div class="pl-opts">${P.placasOrder(qi).map((o) => `<button class="pl-opt" data-o="${o}">${P.esc(q.options[o])}</button>`).join("")}</div>`}`;
      stage.querySelectorAll("[data-o]").forEach((b) => (b.onclick = () => pick(+b.dataset.o)));
      const n = P.$("[data-next]", stage); if (n) n.onclick = () => { shown = false; draw(); };
    }
    P.placasState = () => ({ inf, deck, marks: marks.slice(), shown, finished: finished() });
    draw();
  };
})();
