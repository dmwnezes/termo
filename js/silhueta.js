// Silhueta (site): adivinhe o país ou estado pelo contorno. Dados em shared/silhueta.txt. Cada erro mostra distância, direção e proximidade.
// Do dia: linha = dia mod N, igual no app.
(function () {
  "use strict";
  const TRIES = 6;
  const HELP = "Adivinhe o país ou o estado brasileiro pelo contorno, em até 6 tentativas. Digite e escolha um nome da lista. " +
    "Cada erro mostra a distância até a resposta, a direção em que ela fica e o quanto você chegou perto. A silhueta do dia é a mesma para todo mundo.";
  const ARROWS = ["↑", "↗", "→", "↘", "↓", "↙", "←", "↖"];
  const rad = (d) => (d * Math.PI) / 180;

  P.silhuetaParse = (t) => t.split("\n").filter((l) => l.trim() && !l.startsWith("#")).map((l) => {
    const f = l.split("|");
    return { name: f[0], kind: f[1], lat: +f[2], lon: +f[3], d: f[4] };
  });
  P.silhuetaIdx = (day, n) => ((day % n) + n) % n;
  /** Distância em km, direção (índice de 0 a 7, de quem chutou até a resposta) e proximidade em %. */
  P.silhuetaHint = (from, to) => {
    const dLat = rad(to.lat - from.lat), dLon = rad(to.lon - from.lon), a = rad(from.lat), b = rad(to.lat);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(a) * Math.cos(b) * Math.sin(dLon / 2) ** 2;
    const km = Math.round(2 * 6371 * Math.asin(Math.sqrt(h)));
    const deg = (Math.atan2(Math.sin(dLon) * Math.cos(b), Math.cos(a) * Math.sin(b) - Math.sin(a) * Math.cos(b) * Math.cos(dLon)) * 180) / Math.PI;
    return { km, dir: Math.round(((deg + 360) % 360) / 45) % 8, pct: Math.max(0, Math.round(100 - (km / 20015) * 100)) };
  };

  P.games.silhueta = async function (root, inf = !!P.store.get("sil-inf", false)) {
    P.store.set("sil-inf", inf);
    root.innerHTML = P.topbar("Silhueta", { help: true }) + P.modeSwitch(inf) + `<div class="game sil" data-stage></div>`;
    const stage = P.$("[data-stage]", root);
    const list = P.silhuetaParse(await P.text("silhueta.txt").catch(() => ""));
    if (!document.body.contains(stage)) return;
    P.bindModeSwitch(root, (v) => P.games.silhueta(root, v));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar a Silhueta</h2><p>${HELP}</p>`);
    if (!list.length) { stage.innerHTML = `<p class="center muted">Não foi possível carregar os mapas.</p>`; return; }

    const today = P.dayKey(), key = inf ? "sil-inf-state" : "sil-state";
    let saved = P.store.get(key, null);
    if (saved && (!inf && saved.day !== today || !(saved.idx < list.length) || !Array.isArray(saved.g))) saved = null;
    const idx = saved ? saved.idx : inf ? Math.floor(Math.random() * list.length) : P.silhuetaIdx(P.dayIndex(), list.length);
    const q = list[idx], options = list.filter((x) => x.kind === q.kind);
    let guesses = saved ? saved.g.filter((n) => options.some((o) => o.name === n)) : [];
    const won = () => guesses.includes(q.name), over = () => won() || guesses.length >= TRIES;
    const save = () => P.store.set(key, { day: today, idx, g: guesses });
    const norm = (s) => P.norm(s).replace(/[^A-Z ]/g, "");

    function matches(text) {
      const v = norm(text);
      if (!v) return [];
      return options.filter((o) => !guesses.includes(o.name) && norm(o.name).includes(v)).sort((a, b) => norm(a.name).indexOf(v) - norm(b.name).indexOf(v) || a.name.localeCompare(b.name)).slice(0, 6);
    }
    function guess(name) {
      if (over() || guesses.includes(name)) return;
      guesses.push(name);
      if (over()) {
        P.store.add("sil-played"); P.logActivity();
        if (won()) { P.store.add("sil-won"); if (guesses.length === 1) P.store.add("sil-first"); P.confetti(); P.fx.win(); } else P.fx.lose();
        if (!inf) { P.store.set("daily-done-silhueta", today); P.setResult("silhueta", today, won()); }
      } else P.fx.invalid();
      save(); draw();
    }
    function draw() {
      const fin = over();
      const rows = guesses.map((n) => {
        if (n === q.name) return `<div class="sil-row ok"><b>${P.esc(n)}</b><span>Acertou! 🎉</span></div>`;
        const h = P.silhuetaHint(options.find((o) => o.name === n), q);
        return `<div class="sil-row"><b>${P.esc(n)}</b><span>${h.km.toLocaleString("pt-BR")} km</span><span class="sil-dir" aria-label="Direção da resposta">${ARROWS[h.dir]}</span><span>${h.pct}%</span></div>`;
      }).join("");
      stage.innerHTML = `<p class="hint">${fin ? (won() ? `Acertou em ${guesses.length} ${guesses.length === 1 ? "tentativa" : "tentativas"}!` : `Era ${P.esc(q.name)}.`) : `${q.kind === "e" ? "Estado do Brasil" : "País"} · tentativa ${guesses.length + 1} de ${TRIES}`}</p>
        <svg class="sil-map" viewBox="0 0 100 100" role="img" aria-label="Contorno ${q.kind === "e" ? "de um estado" : "de um país"}"><path d="${q.d}"/></svg>
        ${fin ? `<div class="sil-answer">${P.esc(q.name)}</div><div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-next>${inf ? "Outra silhueta" : "Jogar o Infinito"}</button></div>`
          : `<div class="sil-search"><input class="mp-input" data-input autocomplete="off" autocapitalize="words" placeholder="${q.kind === "e" ? "Digite o estado" : "Digite o país"}" aria-label="Sua resposta" /><div class="sil-opts" data-opts></div></div>`}
        <div class="sil-rows">${rows}</div>`;
      const input = P.$("[data-input]", stage);
      if (input) {
        const opts = P.$("[data-opts]", stage);
        const fill = () => {
          const m = matches(input.value);
          opts.innerHTML = m.map((o) => `<button class="sil-opt" data-name="${P.esc(o.name)}">${P.esc(o.name)}</button>`).join("");
          opts.querySelectorAll("[data-name]").forEach((b) => (b.onclick = () => guess(b.dataset.name)));
        };
        input.oninput = fill;
        input.onkeydown = (e) => { if (e.key === "Enter") { const m = matches(input.value); if (m[0]) guess(m[0].name); } };
        input.focus();
      }
      const sh = P.$("[data-share]", stage);
      if (sh) sh.onclick = () => P.share(`Silhueta ${inf ? "" : P.shortDay(today) + " "}${won() ? guesses.length : "X"}/${TRIES} 🗺️\nhttps://dmwnezes.com/termo/`);
      const nx = P.$("[data-next]", stage);
      if (nx) nx.onclick = () => { P.store.set("sil-inf-state", null); P.games.silhueta(root, true); };
    }
    P.silhuetaState = () => ({ inf, idx, name: q.name, kind: q.kind, guesses: guesses.slice(), won: won(), over: over() });
    draw();
  };
})();
