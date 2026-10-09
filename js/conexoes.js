// Conexões (site): mesmo desafio do dia que o app, mais o modo Infinito (grupos sorteados).
(function () {
  "use strict";
  const parse = (t) => {
    const out = []; let cur = [];
    t.split("\n").forEach((raw) => {
      const l = raw.trim();
      if (l.startsWith("#")) return;
      if (!l) { if (cur.length) out.push(cur); cur = []; return; }
      const i = l.lastIndexOf(":");
      cur.push({ name: l.slice(0, i).trim(), words: l.slice(i + 1).split(",").map((s) => s.trim()).filter(Boolean), level: cur.length });
    });
    if (cur.length) out.push(cur);
    return out;
  };

  const families = (t) => {
    const m = {};
    t.split("\n").forEach((raw) => { const l = raw.trim(); if (!l || l.startsWith("#") || !l.includes(":")) return; m[l.slice(0, l.indexOf(":")).trim()] = l.slice(l.indexOf(":") + 1).split(",").map((x) => x.trim()).filter(Boolean); });
    return m;
  };
  // Infinito: 4 grupos de desafios diferentes, um de cada cor, sem assunto em comum nem palavra repetida.
  const remix = (puzzles, fam, seed) => {
    const rnd = P.rng(seed);
    const byLevel = [0, 1, 2, 3].map((lv) => puzzles.map((p) => p[lv]).filter((g) => g && fam[g.name] && !fam[g.name].includes("fora") && g.words.length === 4));
    for (let k = 0; k < 5000; k++) {
      const pick = [], usedF = new Set(), usedW = new Set();
      for (let lv = 0; lv < 4; lv++) {
        const g = byLevel[lv][Math.floor(rnd() * byLevel[lv].length)], f = fam[g.name], w = g.words.map(P.norm);
        if (f.some((x) => usedF.has(x)) || w.some((x) => usedW.has(x))) break;
        f.forEach((x) => usedF.add(x)); w.forEach((x) => usedW.add(x)); pick.push(g);
      }
      if (pick.length === 4) return pick;
    }
    return puzzles[Math.floor(rnd() * puzzles.length)];
  };
  const newSeed = () => Math.floor(Math.random() * 2147483647);

  P.games.conexoes = async function (root, inf = false, arch = null) {
    const [puzzles, fam] = await Promise.all([P.text("conexoes.txt").then(parse), P.text("conexoes-familias.txt").then(families)]);
    if (arch) inf = false;
    const day = arch || P.dayKey(), i = P.dayIndex(arch ? P.parseDay(arch) : new Date());
    const stateKey = arch ? "conn-arch-state" : inf ? "conn-inf-state" : "conn-state";
    const saved = P.store.get(stateKey, null);
    const seed = inf ? (saved && saved.seed != null ? saved.seed : newSeed()) : null;
    const groups = inf ? remix(puzzles, fam, seed) : puzzles[((i * 13 + 7) % puzzles.length + puzzles.length) % puzzles.length];
    const rnd = P.rng(inf ? seed : i * 97 + 5);
    let tiles = P.shuffle(groups.flatMap((g) => g.words), rnd);
    let sel = [], solved = [], tries = [], mistakes = 0, over = false, won = false;

    const st = saved;
    const submitSel = () => {
      const set = new Set(sel);
      if (tries.some((t) => t.length === 4 && t.every((w) => set.has(w)))) return "repeat";
      tries.push(sel.slice());
      const g = groups.find((gr) => gr.words.every((w) => set.has(w)));
      if (g) { solved.push(g); tiles = tiles.filter((w) => !g.words.includes(w)); sel = []; if (solved.length === 4) { over = true; won = true; } return g; }
      mistakes++;
      const best = Math.max(...groups.map((gr) => gr.words.filter((w) => set.has(w)).length));
      if (mistakes >= 4) { over = true; won = false; sel = []; groups.forEach((gr) => { if (!solved.includes(gr)) solved.push(gr); }); tiles = []; }
      return best === 3 ? "one" : "wrong";
    };
    if (st && (inf ? st.seed === seed : st.day === day)) st.tries.forEach((t) => { sel = t.slice(); submitSel(); });
    sel = [];
    // Um desafio infinito já terminado não volta: abre logo um novo.
    const next = () => { P.store.set("conn-inf-state", { seed: newSeed(), tries: [] }); document.querySelectorAll(".overlay").forEach((o) => o.remove()); P.games.conexoes(root, true); };
    if (inf && over) return next();
    const persist = () => P.store.set(stateKey, inf ? { seed, tries } : { day, tries });
    const finish = () => {
      if (inf) { P.store.add("conn-inf-played"); P.logActivity(); if (won) P.store.add("conn-inf-won"); return; }
      if (arch) return P.archiveDone("conexoes", arch, won);
      if (P.store.get("conn-done") === day) return;
      P.store.set("conn-done", day); P.store.add("conn-played"); P.logActivity();
      P.setResult("conexoes", day, won); P.setHistory(day, "conn", won ? mistakes : 4);
      if (won) { P.store.add("conn-won"); if (mistakes === 0) P.store.add("conn-perfect"); }
    };

    root.innerHTML = P.topbar(arch ? `Conexões · ${P.shortDay(arch)}` : "Conexões", { help: true }) + (arch ? "" : P.modeSwitch(inf)) + `<div class="game conn"><p class="hint">${arch ? "Desafio do arquivo" : inf ? "Grupos sorteados, sem limite de jogos" : "Crie 4 grupos de 4 palavras"}</p>
      <div data-solved></div><div class="conn-grid" data-grid></div><div class="dots" data-dots></div>
      <div class="row-btns" data-btns style="margin-top:auto;padding-bottom:16px"></div></div>`;
    const fs = (w) => (w.includes(" ") ? 11 : w.length >= 13 ? 8.5 : w.length >= 11 ? 9.5 : w.length >= 9 ? 10.5 : w.length >= 7 ? 12 : 14);
    const grid = () => tries.map((t) => t.map((w) => ["#E6C14F", "#5FB873", "#6FA8E8", "#B48CF0"][groups.find((g) => g.words.includes(w)).level]));

    function draw() {
      P.$("[data-solved]", root).innerHTML = solved.map((g) => `<div class="solved lv${g.level}"><b>${P.esc(g.name)}</b>${g.words.join(", ")}</div>`).join("");
      const gEl = P.$("[data-grid]", root); gEl.innerHTML = "";
      tiles.forEach((w) => {
        const b = P.h(`<button class="wtile${sel.includes(w) ? " sel" : ""}" style="font-size:${fs(w)}px">${P.esc(w)}</button>`);
        b.onclick = () => { if (over) return; if (sel.includes(w)) sel = sel.filter((x) => x !== w); else if (sel.length < 4) sel.push(w); P.fx.type(); draw(); };
        gEl.appendChild(b);
      });
      P.$("[data-dots]", root).innerHTML = `Erros restantes: ${[0, 1, 2, 3].map((k) => `<i class="${k < 4 - mistakes ? "" : "off"}"></i>`).join("")}`;
      const btns = P.$("[data-btns]", root);
      if (!over) {
        btns.innerHTML = `<button class="pill ghost" data-mix>Misturar</button><button class="pill ghost" data-clear ${sel.length ? "" : "disabled"}>Limpar</button><button class="pill" data-send ${sel.length === 4 ? "" : "disabled"}>Enviar</button>`;
        P.$("[data-mix]", btns).onclick = () => { tiles = P.shuffle(tiles); draw(); };
        P.$("[data-clear]", btns).onclick = () => { sel = []; draw(); };
        P.$("[data-send]", btns).onclick = () => {
          const r = submitSel();
          if (r === "repeat") P.toast("Você já tentou essa");
          else if (r === "one") { P.fx.invalid(); P.toast("Falta só uma!"); }
          else if (r === "wrong") { P.fx.invalid(); P.toast("Não é um grupo"); }
          else { P.fx.reveal(r.level, "c"); P.toast(r.name); }
          persist();
          if (over) { finish(); if (won) { P.fx.win(); P.confetti(); } else P.fx.lose(); setTimeout(result, 1600); }
          draw();
        };
      } else if (inf) {
        btns.innerHTML = `<button class="pill ghost" data-res>Resultado</button><button class="pill" data-next>Próximo</button>`;
        P.$("[data-res]", btns).onclick = result;
        P.$("[data-next]", btns).onclick = next;
      } else if (arch) {
        btns.innerHTML = `<button class="pill ghost" data-res>Resultado</button><button class="pill" data-back-arch>Voltar ao arquivo</button>`;
        P.$("[data-res]", btns).onclick = result;
        P.$("[data-back-arch]", btns).onclick = () => P.go("arquivo");
      } else {
        btns.innerHTML = `<button class="pill" data-res>Ver resultado</button>`;
        P.$("[data-res]", btns).onclick = result;
      }
    }
    function result() {
      const em = ["🟨", "🟩", "🟦", "🟪"];
      const share = tries.map((t) => t.map((w) => em[groups.find((g) => g.words.includes(w)).level]).join("")).join("\n");
      const ui = P.sheet(`<h2>${won ? (mistakes === 0 ? "Perfeito!" : "Você conseguiu!") : "Não foi dessa vez"}</h2>
        <p class="subtitle">${won ? `Erros: ${mistakes}` : inf || arch ? "As respostas estão na tela" : "Volte amanhã para um desafio novo"}</p>
        <p class="center" style="font-size:22px;line-height:1.2;white-space:pre">${share}</p>
        ${arch ? "" : inf ? P.statsHTML([[P.store.get("conn-inf-played", 0), "Jogos"], [P.store.get("conn-inf-won", 0), "Vitórias"]])
              : P.statsHTML([[P.store.get("conn-played", 0), "Jogos"], [P.store.get("conn-won", 0), "Vitórias"], [P.store.get("conn-perfect", 0), "Perfeitos"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill accent" data-story>Stories</button></div>
        ${arch ? `<button class="pill" data-arch style="width:100%;margin-top:10px">Voltar ao arquivo</button>` : inf ? `<button class="pill" data-next style="width:100%;margin-top:10px">Próximo desafio</button>` : `<p class="note">Um desafio novo aparece amanhã.</p>`}`);
      if (inf) P.$("[data-next]", ui.el).onclick = next;
      if (arch) P.$("[data-arch]", ui.el).onclick = () => P.go("arquivo");
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Conexões${inf ? " Infinito" : arch ? " · " + P.shortDay(arch) : ""}\n\n${share}`);
      P.$("[data-story]", ui.el).onclick = () => P.story({
        game: inf ? "Conexões Infinito" : arch ? "Conexões · " + P.shortDay(arch) : "Conexões do dia", headline: won ? (mistakes === 0 ? "Perfeito!" : "Resolvi!") : "Quase lá!",
        detail: won ? `${mistakes} ${mistakes === 1 ? "erro" : "erros"}` : "Faltou pouco", grids: [grid()],
        stats: inf ? [[String(P.store.get("conn-inf-won", 0)), "Resolvidos"], [String(P.store.get("conn-inf-played", 0)), "Jogos"]]
          : [[String(P.store.get("conn-won", 0)), "Resolvidos"], [String(P.store.get("conn-perfect", 0)), "Perfeitos"]],
      });
    }
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Conexões</h2>
      <p>Encontre 4 grupos de 4 palavras que têm algo em comum. Toque em 4 palavras e em Enviar.</p>
      <p>Os grupos vão do mais fácil (amarelo) ao mais difícil (roxo). Cuidado com as pegadinhas: algumas palavras parecem caber em mais de um grupo.</p>
      <p>Você pode errar até 4 vezes. Um desafio novo por dia.</p>
      <p>No modo Infinito, cada desafio junta grupos sorteados e você joga quantos quiser.</p>`);
    P.bindModeSwitch(root, (v) => P.games.conexoes(root, v));
    draw();
    if (over && !inf && !arch) setTimeout(result, 400);
  };
})();
