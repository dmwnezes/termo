// Mais ou Menos (site): o segundo item tem mais ou menos que o primeiro? Temas gerais em shared/maismenos.txt.
// Do dia: 10 rodadas sorteadas pelo MINSTD (igual ao app). Infinito: até errar.
(function () {
  "use strict";
  const ROUNDS = 10;
  const HELP = "Você vê o valor do primeiro item. Toque em Mais se o segundo tiver um valor maior, ou em Menos se tiver um valor menor. " +
    "Em temas de ano, Mais quer dizer um ano mais recente. Os valores são aproximados. Do dia: 10 rodadas, iguais para todo mundo. Infinito: até errar.";

  P.mmParse = (t) => {
    const out = []; let c = null;
    t.split("\n").forEach((l) => {
      l = l.trim();
      if (l.startsWith("## ")) { const f = l.slice(3).split("|"); c = { title: f[0], unit: f[1] || "", year: f[2] === "ano", items: [] }; out.push(c); }
      else if (l && !l.startsWith("#") && c) { const f = l.split("|"); c.items.push({ name: f[0], v: +f[1] }); }
    });
    return out;
  };
  /** Dois valores só valem como rodada com diferença clara: valores são aproximados. */
  const fair = (c, a, b) => (c.year ? Math.abs(a.v - b.v) >= 3 : Math.abs(a.v - b.v) >= 0.1 * Math.max(Math.abs(a.v), Math.abs(b.v)));
  /** Próxima rodada usando next(): tema sem repetir os [recent], e dois itens com diferença clara. Mesmo cálculo no app. */
  P.mmRound = (cats, next, recent) => {
    for (let k = 0; k < 100000; k++) {
      const ci = next() % cats.length;
      if (recent.includes(ci)) continue;
      const c = cats[ci], a = next() % c.items.length, b = next() % c.items.length;
      if (a !== b && fair(c, c.items[a], c.items[b])) return { c: ci, a, b };
    }
    throw new Error("sem rodada");
  };
  P.mmDaily = (cats, day) => {
    const next = P.minstd(day), out = [];
    while (out.length < ROUNDS) out.push(P.mmRound(cats, next, out.map((r) => r.c)));
    return out;
  };
  const fmt = (c, v) => (c.year ? String(v) : v.toLocaleString("pt-BR"));
  const valueText = (c, v) => (c.year || !c.unit ? fmt(c, v) : `${fmt(c, v)} ${c.unit}`);

  P.games.maismenos = async function (root, inf = !!P.store.get("mm-inf", false)) {
    P.store.set("mm-inf", inf);
    root.innerHTML = P.topbar("Mais ou Menos", { help: true }) + P.modeSwitch(inf) + `<div class="game mm" data-stage></div>`;
    const stage = P.$("[data-stage]", root);
    const cats = P.mmParse(await P.text("maismenos.txt").catch(() => ""));
    if (!document.body.contains(stage)) return;
    P.bindModeSwitch(root, (v) => P.games.maismenos(root, v));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar o Mais ou Menos</h2><p>${HELP}</p>`);
    if (!cats.length) { stage.innerHTML = `<p class="center muted">Não foi possível carregar os temas.</p>`; return; }

    const today = P.dayKey();
    const saved = inf ? null : P.store.get("mm-state", null);
    let marks = saved && saved.day === today && Array.isArray(saved.marks) ? saved.marks.slice(0, ROUNDS) : [];
    const rounds = inf ? [] : P.mmDaily(cats, P.dayIndex());
    const rnd = () => Math.floor(Math.random() * 2147483646) + 1;
    const recent = () => rounds.slice(-Math.min(8, cats.length - 1)).map((r) => r.c);
    if (inf) rounds.push(P.mmRound(cats, rnd, []));
    let answer = null;
    const hits = () => marks.filter(Boolean).length;
    const finished = () => (inf ? marks.includes(false) : marks.length >= ROUNDS);

    function pick(more) {
      if (answer !== null || finished()) return;
      const r = rounds[marks.length], c = cats[r.c], right = c.items[r.b].v > c.items[r.a].v === more;
      answer = right;
      marks.push(right);
      if (right) P.fx.win(); else P.fx.invalid();
      if (inf) P.store.set("mm-best", Math.max(P.store.get("mm-best", 0) || 0, hits()));
      else P.store.set("mm-state", { day: today, marks });
      if (finished()) {
        P.store.add("mm-played"); P.logActivity();
        if (!inf) {
          P.store.set("mm-day-best", Math.max(P.store.get("mm-day-best", 0) || 0, hits()));
          P.store.set("daily-done-maismenos", today); P.setResult("maismenos", today, hits() >= 5);
          if (hits() === ROUNDS) P.confetti();
        }
      }
      draw();
    }
    function next() {
      answer = null;
      if (inf && !finished()) rounds.push(P.mmRound(cats, rnd, recent()));
      draw();
    }
    function draw() {
      const showEnd = finished() && answer === null;
      if (showEnd) {
        stage.innerHTML = `<div class="mm-end"><div class="mm-big">${hits()}${inf ? "" : "/" + ROUNDS}</div><p>${inf ? `${hits() === 1 ? "acerto seguido" : "acertos seguidos"} · recorde ${P.store.get("mm-best", 0) || 0}` : "acertos no desafio de hoje"}</p>
          ${inf ? "" : `<div class="mm-dots">${marks.map((m) => `<i class="${m ? "ok" : "bad"}"></i>`).join("")}</div>`}
          <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>${inf ? "Jogar de novo" : "Jogar o Infinito"}</button></div></div>`;
        P.$("[data-share]", stage).onclick = () => P.share(`Mais ou Menos ${inf ? `🔥 ${hits()} seguidos` : `${P.shortDay(today)} ${hits()}/${ROUNDS}\n${marks.map((m) => (m ? "🟩" : "🟥")).join("")}`}\nhttps://dmwnezes.com/termo/`);
        P.$("[data-again]", stage).onclick = () => P.games.maismenos(root, true);
        return;
      }
      const at = answer === null ? marks.length : marks.length - 1, r = rounds[at], c = cats[r.c], a = c.items[r.a], b = c.items[r.b];
      stage.innerHTML = `<p class="hint">${inf ? `Acertos seguidos: <b>${hits()}</b>` : `Rodada ${at + 1} de ${ROUNDS} · <b>${hits()}</b> ${hits() === 1 ? "acerto" : "acertos"}`}</p>
        <div class="mm-theme">${P.esc(c.title)}</div>
        <div class="mm-card"><span>${P.esc(a.name)}</span><b>${P.esc(valueText(c, a.v))}</b></div>
        <div class="mm-card second${answer === null ? "" : answer ? " ok" : " bad"}"><span>${P.esc(b.name)}</span><b>${answer === null ? "?" : P.esc(valueText(c, b.v))}</b></div>
        ${answer === null ? `<p class="hint mm-q">${c.year ? "É de um ano mais recente ou mais antigo?" : "Tem mais ou menos?"}</p><div class="row-btns"><button class="pill mm-less" data-less>${c.year ? "Mais antigo" : "Menos"}</button><button class="pill" data-more>${c.year ? "Mais recente" : "Mais"}</button></div>`
          : `<p class="hint mm-q">${answer ? "Acertou! ✓" : "Errou."}</p><button class="pill wide" data-next>${finished() ? "Ver resultado" : "Próxima"}</button>`}`;
      const m = P.$("[data-more]", stage); if (m) { m.onclick = () => pick(true); P.$("[data-less]", stage).onclick = () => pick(false); }
      const n = P.$("[data-next]", stage); if (n) n.onclick = next;
    }
    P.mmState = () => ({ inf, marks: marks.slice(), rounds, answer, finished: finished() });
    draw();
  };
})();
