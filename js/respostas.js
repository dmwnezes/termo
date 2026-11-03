// Pesquisa e Top 10 (site): digite as respostas, com 3 erros permitidos. Dados em shared/pesquisa.txt e shared/top10.txt. Do dia: linha = dia mod N, igual no app.
(function () {
  "use strict";
  const ERRORS = 3;
  /** Texto digitado em forma comparável: sem acento, pontuação nem artigo no começo. */
  const clean = (s) => P.norm(s).replace(/[^A-Z0-9 ]+/g, " ").replace(/\s+/g, " ").trim().replace(/^(O|A|OS|AS|UM|UMA) /, "");
  P.respClean = clean;
  /** Índice da resposta que combina com o texto, ou -1. */
  P.respMatch = (items, text) => { const v = clean(text); return v ? items.findIndex((it) => it.names.some((n) => clean(n) === v)) : -1; };
  P.pesquisaParse = (t) => t.split("\n").filter((l) => l.trim() && !l.startsWith("#")).map((l) => {
    const f = l.split("|");
    return { title: f[0], items: f.slice(1).map((x) => { const i = x.lastIndexOf(":"); return { names: x.slice(0, i).split("~"), pts: +x.slice(i + 1) }; }) };
  });
  P.top10Parse = (t) => t.split("\n").filter((l) => l.trim() && !l.startsWith("#")).map((l) => {
    const f = l.split("|");
    return { title: f[0], items: f.slice(1).map((x) => ({ names: x.split("~"), pts: 1 })) };
  });

  function game(root, inf, cfg) {
    P.store.set(cfg.key + "-inf", inf);
    root.innerHTML = P.topbar(cfg.name, { help: true }) + P.modeSwitch(inf) + `<div class="game resp" data-stage></div>`;
    const stage = P.$("[data-stage]", root);
    P.bindModeSwitch(root, (v) => game(root, v, cfg));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar ${cfg.name}</h2><p>${cfg.help}</p>`);
    P.text(cfg.file).then(cfg.parse).catch(() => []).then((list) => {
      if (!document.body.contains(stage)) return;
      if (!list.length) { stage.innerHTML = `<p class="center muted">Não foi possível carregar o desafio.</p>`; return; }
      const today = P.dayKey(), key = cfg.key + (inf ? "-inf-state" : "-state");
      let saved = P.store.get(key, null);
      if (saved && ((!inf && saved.day !== today) || !(saved.idx < list.length) || !Array.isArray(saved.got))) saved = null;
      const n = list.length, day = P.dayIndex(), idx = saved ? saved.idx : inf ? Math.floor(Math.random() * n) : ((day % n) + n) % n;
      const q = list[idx];
      let got = saved ? saved.got : [], errors = saved ? saved.errors || [] : [];
      const points = () => got.reduce((a, i) => a + q.items[i].pts, 0);
      const finished = () => got.length === q.items.length || errors.length >= ERRORS;
      const save = () => P.store.set(key, { day: today, idx, got, errors });
      function finish() {
        P.store.add(cfg.key + "-played"); P.logActivity();
        const k = cfg.key + "-best"; P.store.set(k, Math.max(P.store.get(k, 0) || 0, points()));
        if (got.length === q.items.length) { P.store.add(cfg.key + "-full"); P.confetti(); P.fx.win(); } else P.fx.lose();
        if (!inf) { P.store.set("daily-done-" + cfg.game, today); P.setResult(cfg.game, today, got.length === q.items.length); }
      }
      function send(text) {
        if (finished() || !clean(text)) return;
        const i = P.respMatch(q.items, text);
        if (got.includes(i)) { P.toast("Essa já foi!"); return; }
        if (errors.map(clean).includes(clean(text))) { P.toast("Você já tentou essa"); return; }
        if (i >= 0) { got.push(i); P.fx.type(); } else { errors.push(text.trim().slice(0, 40)); P.fx.invalid(); }
        if (finished()) finish();
        save(); draw();
      }
      function draw() {
        const fin = finished(), left = ERRORS - errors.length;
        const row = (it, i) => {
          const has = got.includes(i);
          return `<div class="resp-row${has ? " ok" : fin ? " miss" : ""}"><b>${has || fin ? P.esc(it.names[0]) : cfg.numbered ? i + 1 : "•••"}</b>${cfg.points ? `<span>${has || fin ? it.pts : ""}</span>` : ""}</div>`;
        };
        stage.innerHTML = `<p class="hint">${fin ? (got.length === q.items.length ? "Completou tudo! 🎉" : "Fim de jogo.") : `${left} ${left === 1 ? "erro restante" : "erros restantes"}`} · ${cfg.points ? `<b>${points()}</b> pontos` : `<b>${got.length}</b> de 10`}</p>
          <div class="mm-theme">${P.esc(q.title)}</div>
          ${fin ? `<div class="row-btns resp-end"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-next>${inf ? "Outro desafio" : "Jogar o Infinito"}</button></div>`
            : `<form class="resp-form" data-form><input class="mp-input" data-input autocomplete="off" placeholder="Digite sua resposta" aria-label="Sua resposta" /><button class="pill">OK</button></form>`}
          <div class="resp-list">${q.items.map(row).join("")}</div>
          ${errors.length ? `<p class="resp-errors">Erros: ${errors.map(P.esc).join(", ")}</p>` : ""}${cfg.note ? `<p class="resp-note">${cfg.note}</p>` : ""}`;
        const form = P.$("[data-form]", stage);
        if (form) {
          const input = P.$("[data-input]", form);
          form.onsubmit = (e) => { e.preventDefault(); send(input.value); };
          input.focus();
        }
        const sh = P.$("[data-share]", stage);
        if (sh) sh.onclick = () => P.share(`${cfg.name} ${inf ? "" : P.shortDay(today) + " "}${cfg.points ? points() + " pontos" : got.length + "/10"}\nhttps://dmwnezes.com/termo/`);
        const nx = P.$("[data-next]", stage);
        if (nx) nx.onclick = () => { P.store.set(cfg.key + "-inf-state", null); game(root, true, cfg); };
      }
      cfg.state(() => ({ q, got, errors, points: points(), finished: finished() }));
      draw();
    });
  }

  P.games.pesquisa = (root, inf = !!P.store.get("pq-inf", false)) => game(root, inf, {
    key: "pq", game: "pesquisa", name: "Pesquisa", file: "pesquisa.txt", parse: P.pesquisaParse, points: true, numbered: false, state: (f) => (P.pesquisaState = f),
    help: "Adivinhe as respostas mais prováveis para a pergunta. Cada resposta vale pontos, e as mais óbvias valem mais. Você pode errar 3 vezes. As respostas foram escolhidas para o jogo, não vêm de uma pesquisa real.",
    note: "Respostas escolhidas para o jogo, sem pesquisa real.",
  });
  P.games.top10 = (root, inf = !!P.store.get("t10-inf", false)) => game(root, inf, {
    key: "t10", game: "top10", name: "Top 10", file: "top10.txt", parse: P.top10Parse, points: false, numbered: true, state: (f) => (P.top10State = f),
    help: "Escreva os 10 itens da lista, em qualquer ordem. Você pode errar 3 vezes. Acentos e maiúsculas não importam.",
  });
})();
