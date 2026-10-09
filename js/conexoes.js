// Conexões (site): mesmo desafio do dia que o app.
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

  P.games.conexoes = async function (root) {
    const puzzles = parse(await P.text("conexoes.txt"));
    const day = P.dayKey(), i = P.dayIndex();
    const groups = puzzles[((i * 13 + 7) % puzzles.length + puzzles.length) % puzzles.length];
    const rnd = P.rng(i * 97 + 5);
    let tiles = P.shuffle(groups.flatMap((g) => g.words), rnd);
    let sel = [], solved = [], tries = [], mistakes = 0, over = false, won = false;

    const st = P.store.get("conn-state", null);
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
    if (st && st.day === day) st.tries.forEach((t) => { sel = t.slice(); submitSel(); });
    sel = [];
    const persist = () => P.store.set("conn-state", { day, tries });
    const finish = () => {
      if (P.store.get("conn-done") === day) return;
      P.store.set("conn-done", day); P.store.add("conn-played"); P.logActivity();
      if (won) { P.store.add("conn-won"); if (mistakes === 0) P.store.add("conn-perfect"); }
    };

    root.innerHTML = P.topbar("Conexões", { help: true }) + `<div class="game conn"><p class="hint">Crie 4 grupos de 4 palavras</p>
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
      } else {
        btns.innerHTML = `<button class="pill" data-res>Ver resultado</button>`;
        P.$("[data-res]", btns).onclick = result;
      }
    }
    function result() {
      const em = ["🟨", "🟩", "🟦", "🟪"];
      const share = tries.map((t) => t.map((w) => em[groups.find((g) => g.words.includes(w)).level]).join("")).join("\n");
      const ui = P.sheet(`<h2>${won ? (mistakes === 0 ? "Perfeito!" : "Você conseguiu!") : "Não foi dessa vez"}</h2>
        <p class="subtitle">${won ? `Erros: ${mistakes}` : "Volte amanhã para um desafio novo"}</p>
        <p class="center" style="font-size:22px;line-height:1.2;white-space:pre">${share}</p>
        ${P.statsHTML([[P.store.get("conn-played", 0), "Jogos"], [P.store.get("conn-won", 0), "Vitórias"], [P.store.get("conn-perfect", 0), "Perfeitos"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill accent" data-story>Stories</button></div>
        <p class="note">Um desafio novo aparece amanhã.</p>`);
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Conexões\n\n${share}`);
      P.$("[data-story]", ui.el).onclick = () => P.story({
        game: "Conexões do dia", headline: won ? (mistakes === 0 ? "Perfeito!" : "Resolvi!") : "Quase lá!",
        detail: won ? `${mistakes} ${mistakes === 1 ? "erro" : "erros"}` : "Faltou pouco", grids: [grid()],
        stats: [[String(P.store.get("conn-won", 0)), "Resolvidos"], [String(P.store.get("conn-perfect", 0)), "Perfeitos"]],
      });
    }
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Conexões</h2>
      <p>Encontre 4 grupos de 4 palavras que têm algo em comum. Toque em 4 palavras e em Enviar.</p>
      <p>Os grupos vão do mais fácil (amarelo) ao mais difícil (roxo). Cuidado com as pegadinhas: algumas palavras parecem caber em mais de um grupo.</p>
      <p>Você pode errar até 4 vezes. Um desafio novo por dia.</p>`);
    draw();
    if (over) setTimeout(result, 400);
  };
})();
