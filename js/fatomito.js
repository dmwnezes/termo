// Fato ou Mito (site): a afirmação é verdadeira? Dados em shared/fatomito.txt. Do dia: 10 afirmações pelo Minstd (igual ao app). Infinito: até errar 3 vezes.
(function () {
  "use strict";
  const ROUNDS = 10, LIVES = 3;
  const HELP = "Leia a afirmação e toque em Fato ou em Mito. Depois de responder, veja a explicação. Do dia: 10 afirmações, iguais para todo mundo. Infinito: até errar 3 vezes.";
  P.fmParse = (t) => t.split("\n").filter((l) => l.trim() && !l.startsWith("#")).map((l) => { const f = l.split("|"); return { fact: f[0] === "F", text: f[1], why: f[2] }; });
  /** Índices do dia: próximo índice = Minstd mod N, sem repetir. */
  P.fmDaily = (list, day) => { const next = P.minstd(day), out = []; while (out.length < Math.min(ROUNDS, list.length)) { const i = next() % list.length; if (!out.includes(i)) out.push(i); } return out; };

  P.games.fatomito = async function (root, inf = !!P.store.get("fm-inf", false)) {
    P.store.set("fm-inf", inf);
    root.innerHTML = P.topbar("Fato ou Mito", { help: true }) + P.modeSwitch(inf) + `<div class="game mm" data-stage></div>`;
    const stage = P.$("[data-stage]", root);
    const list = P.fmParse(await P.text("fatomito.txt").catch(() => ""));
    if (!document.body.contains(stage)) return;
    P.bindModeSwitch(root, (v) => P.games.fatomito(root, v));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Fato ou Mito</h2><p>${HELP}</p>`);
    if (!list.length) { stage.innerHTML = `<p class="center muted">Não foi possível carregar as afirmações.</p>`; return; }

    const today = P.dayKey(), deck = inf ? P.shuffle(list.map((_, i) => i)) : P.fmDaily(list, P.dayIndex());
    const saved = inf ? null : P.store.get("fm-state", null);
    let marks = saved && saved.day === today && Array.isArray(saved.marks) ? saved.marks.slice(0, deck.length) : [];
    let shown = false;
    const hits = () => marks.filter(Boolean).length, misses = () => marks.length - hits();
    const finished = () => (inf ? misses() >= LIVES || marks.length >= deck.length : marks.length >= deck.length);

    function pick(fact) {
      if (shown || finished()) return;
      const right = list[deck[marks.length]].fact === fact;
      marks.push(right); shown = true;
      if (right) P.fx.win(); else P.fx.invalid();
      if (inf) P.store.set("fm-best", Math.max(P.store.get("fm-best", 0) || 0, hits()));
      else P.store.set("fm-state", { day: today, marks });
      if (finished()) {
        P.store.add("fm-played"); P.logActivity();
        if (!inf) {
          P.store.set("fm-day-best", Math.max(P.store.get("fm-day-best", 0) || 0, hits()));
          P.store.set("daily-done-fatomito", today); P.setResult("fatomito", today, hits() >= 5);
          if (hits() === ROUNDS) P.confetti();
        }
      }
      draw();
    }
    function draw() {
      if (finished() && !shown) {
        stage.innerHTML = `<div class="mm-end"><div class="mm-big">${hits()}${inf ? "" : "/" + ROUNDS}</div><p>${inf ? `${hits() === 1 ? "acerto" : "acertos"} · recorde ${P.store.get("fm-best", 0) || 0}` : "acertos no desafio de hoje"}</p>
          ${inf ? "" : `<div class="mm-dots">${marks.map((m) => `<i class="${m ? "ok" : "bad"}"></i>`).join("")}</div>`}
          <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>${inf ? "Jogar de novo" : "Jogar o Infinito"}</button></div></div>`;
        P.$("[data-share]", stage).onclick = () => P.share(`Fato ou Mito ${inf ? `🧐 ${hits()} acertos` : `${P.shortDay(today)} ${hits()}/${ROUNDS}\n${marks.map((m) => (m ? "🟩" : "🟥")).join("")}`}\nhttps://dmwnezes.com/termo/`);
        P.$("[data-again]", stage).onclick = () => P.games.fatomito(root, true);
        return;
      }
      const at = shown ? marks.length - 1 : marks.length, q = list[deck[at]], right = marks[at];
      stage.innerHTML = `<p class="hint">${inf ? `Acertos: <b>${hits()}</b> · ${"❤️".repeat(LIVES - misses())}` : `Afirmação ${at + 1} de ${ROUNDS} · <b>${hits()}</b> ${hits() === 1 ? "acerto" : "acertos"}`}</p>
        <div class="mm-card fm-card${shown ? (right ? " ok" : " bad") : ""}"><b>${P.esc(q.text)}</b></div>
        ${shown ? `<p class="fm-verdict">${right ? "Acertou!" : "Errou."} É ${q.fact ? "fato" : "mito"}.</p><p class="fm-why">${P.esc(q.why)}</p><button class="pill wide" data-next>${finished() ? "Ver resultado" : "Próxima"}</button>`
          : `<div class="row-btns"><button class="pill mm-less" data-myth>Mito</button><button class="pill" data-fact>Fato</button></div>`}`;
      const f = P.$("[data-fact]", stage); if (f) { f.onclick = () => pick(true); P.$("[data-myth]", stage).onclick = () => pick(false); }
      const n = P.$("[data-next]", stage); if (n) n.onclick = () => { shown = false; draw(); };
    }
    P.fmState = () => ({ inf, deck, marks: marks.slice(), shown, finished: finished() });
    draw();
  };
})();
