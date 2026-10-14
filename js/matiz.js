// Matiz (site), inspirado no Colorle: uma cor aparece por 3 s e some; recrie-a com as barras R, G e B.
// Nota da rodada = round(max(0, 100 − ΔE76 × 1.5)) no espaço Lab (sRGB D65). Do dia: 5 cores pelo MINSTD (igual ao app).
(function () {
  "use strict";
  const ROUNDS = 5, SHOW_S = 3;
  const HELP = "Uma cor aparece por 3 segundos e some. Recrie a cor com as barras R (vermelho), G (verde) e B (azul) e toque em Pronto. " +
    "Quanto mais perto da original, maior a nota (0 a 100). São 5 cores por partida, total de até 500 pontos. " +
    "Do dia: as mesmas 5 cores para todo mundo. Infinito: cores sorteadas, quantas partidas quiser.";

  const hex = (c) => "#" + c.map((v) => v.toString(16).padStart(2, "0")).join("").toUpperCase();
  const lab = ([r, g, b]) => {
    const lin = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
    const [R, G, B] = [lin(r), lin(g), lin(b)];
    const f = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
    const x = f((0.4124564 * R + 0.3575761 * G + 0.1804375 * B) / 0.95047), y = f(0.2126729 * R + 0.7151522 * G + 0.072175 * B),
      z = f((0.0193339 * R + 0.119192 * G + 0.9503041 * B) / 1.08883);
    return [116 * y - 16, 500 * (x - y), 200 * (y - z)];
  };
  const score = (a, b) => { const p = lab(a), q = lab(b); return Math.round(Math.max(0, 100 - Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]) * 1.5)); };
  /** As 5 cores do dia: 3 números seguidos do MINSTD por cor (R, G, B), cada um mod 256. */
  const dailyColors = (day) => { const next = P.minstd(day); return Array.from({ length: ROUNDS }, () => [next() % 256, next() % 256, next() % 256]); };
  const randColors = () => Array.from({ length: ROUNDS }, () => [0, 0, 0].map(() => Math.floor(Math.random() * 256)));
  const square = (n) => (n >= 90 ? "🟩" : n >= 70 ? "🟨" : "⬛");
  P.matizDaily = dailyColors; P.matizScore = score; P.matizHex = hex;

  P.games.matiz = function (root, inf = !!P.store.get("matiz-inf", false)) {
    P.store.set("matiz-inf", inf);
    const today = P.dayKey(), di = P.dayIndex();
    const saved = P.store.get("matiz-state", null);
    // Do dia: retoma as notas já feitas hoje (a cor da rodada em andamento recomeça).
    let colors = inf ? randColors() : dailyColors(di), scores = [], picks = [];
    if (!inf && saved && saved.day === today) { scores = saved.scores || []; picks = saved.picks || []; }
    let phase = "show", pick = [128, 128, 128], timers = [];

    root.innerHTML = P.topbar("Matiz", { help: true }) + P.modeSwitch(inf) + `<div class="game mz">
      <div class="score-row"><span><small>Cor</small><b data-round></b></span><span><small>Total</small><b data-total></b></span></div>
      <div class="mz-stage" data-stage></div></div>`;
    const stage = P.$("[data-stage]", root);
    const later = (fn, ms) => timers.push(setTimeout(() => { if (document.body.contains(stage)) fn(); }, ms));
    P.matizState = () => ({ inf, phase, round: scores.length, colors: colors.map(hex), scores: scores.slice() });

    const total = () => scores.reduce((a, b) => a + b, 0);
    function hud() {
      P.$("[data-round]", root).textContent = `${Math.min(scores.length + (phase === "result" ? 0 : 1), ROUNDS)}/${ROUNDS}`;
      P.$("[data-total]", root).textContent = total();
    }

    function show() {
      phase = "show"; hud();
      const c = colors[scores.length];
      stage.innerHTML = `<p class="hint">Memorize esta cor</p><div class="mz-swatch big" style="background:${hex(c)}"><b data-count>${SHOW_S}</b></div>`;
      for (let s = 1; s < SHOW_S; s++) later(() => { P.$("[data-count]", stage).textContent = SHOW_S - s; P.fx.type(); }, s * 1000);
      later(choose, SHOW_S * 1000);
    }

    function choose() {
      phase = "pick"; pick = [128, 128, 128]; hud();
      stage.innerHTML = `<p class="hint">Recrie a cor</p><div class="mz-swatch" data-prev></div>
        <div class="mz-sliders">${["R", "G", "B"].map((k, i) => `<label class="mz-row mz-${k}"><b>${k}</b>
          <input type="range" min="0" max="255" value="128" data-ch="${i}" aria-label="${["Vermelho", "Verde", "Azul"][i]}"><span data-v="${i}">128</span></label>`).join("")}</div>
        <button class="pill wide" data-done>Pronto</button>`;
      const prev = P.$("[data-prev]", stage);
      const paint = () => { prev.style.background = hex(pick); stage.querySelectorAll("[data-v]").forEach((s) => (s.textContent = pick[+s.dataset.v])); };
      stage.querySelectorAll("[data-ch]").forEach((r) => (r.oninput = () => { pick[+r.dataset.ch] = +r.value; paint(); }));
      P.$("[data-done]", stage).onclick = done;
      paint();
    }

    function done() {
      const c = colors[scores.length], n = score(c, pick);
      scores.push(n); picks.push(pick.slice()); phase = "result";
      P.store.max("matiz-top", n);
      if (!inf) P.store.set("matiz-state", { day: today, scores, picks });
      n >= 70 ? P.fx.win() : P.tone([[300, 60]], 0.05);
      hud();
      const last = scores.length === ROUNDS;
      stage.innerHTML = `<div class="mz-pair">
          <div><div class="mz-swatch half" style="background:${hex(c)}"></div><small>Original</small><code>${hex(c)}</code></div>
          <div><div class="mz-swatch half" style="background:${hex(pick)}"></div><small>Sua cor</small><code>${hex(pick)}</code></div></div>
        <div class="mz-score"><b>${n}</b><small>de 100</small></div>
        <button class="pill wide" data-next>${last ? "Ver resultado" : "Próxima cor ›"}</button>`;
      P.$("[data-next]", stage).onclick = () => { if (last) { finish(); P.confetti(); } else show(); };
      if (last) record();
    }

    function record() {
      P.store.add("matiz-played"); P.logActivity();
      if (!inf) { P.store.max("matiz-best", total()); P.store.set("daily-done-matiz", today); }
    }

    const shareText = () => `Matiz ${total()}/${ROUNDS * 100} 🎨\n${scores.map(square).join("")}`;
    function finish() {
      phase = "over"; hud();
      const pairs = colors.map((c, i) => `<span class="mz-mini"><i style="background:${hex(c)}"></i><i style="background:${hex(picks[i] || c)}"></i><small>${scores[i]}</small></span>`).join("");
      stage.innerHTML = `<div class="mz-sum"><div class="mz-score"><b>${total()}</b><small>de ${ROUNDS * 100}</small></div><div class="mz-minis">${pairs}</div>
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button>${inf ? `<button class="pill" data-again>Jogar de novo</button>` : `<button class="pill" data-inf>Jogar o Infinito</button>`}</div>
        ${inf ? "" : `<p class="center muted">Novas cores amanhã · recorde ${P.store.get("matiz-best", 0)}</p>`}</div>`;
      P.$("[data-share]", stage).onclick = () => P.share(shareText());
      if (inf) P.$("[data-again]", stage).onclick = () => P.games.matiz(root, true);
      else P.$("[data-inf]", stage).onclick = () => P.games.matiz(root, true);
    }

    P.bindModeSwitch(root, (v) => { timers.forEach(clearTimeout); P.games.matiz(root, v); });
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar o Matiz</h2><p>${HELP}</p>`);
    scores.length >= ROUNDS ? finish() : show();
  };
})();
