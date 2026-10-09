// Termo, Infinito, Dueto, Quarteto e Desafio (site). Mesmas regras do app.
(function () {
  "use strict";
  const MODES = {
    termo: { title: "Termo", boards: 1, daily: true, free: false },
    infinito: { title: "Infinito", boards: 1, daily: false, free: true },
    dueto: { title: "Dueto", boards: 2, daily: true, free: true, salt: 1 },
    quarteto: { title: "Quarteto", boards: 4, daily: true, free: true, salt: 2 },
    desafio: { title: "Desafio", boards: 1, daily: false, free: false },
  };
  P.MODES = MODES;
  const ORD = ["1ª", "2ª", "3ª", "4ª", "5ª"];

  /** Estado de uma partida. */
  function createGame(modeKey, challenge) {
    const mode = MODES[modeKey];
    const g = { modeKey, mode, boards: mode.boards, maxTries: 5 + mode.boards, rows: [], current: [null, null, null, null, null], cursor: 0, over: false, won: false, hints: [], hard: false, isDaily: mode.daily, key: "" };
    const today = P.dayKey();
    const saved = P.store.get("game-" + modeKey, null);
    const restore = (answers, s) => {
      g.answers = answers; g.rows = s ? s.rows : []; g.over = s ? s.over : false; g.won = s ? s.won : false;
      g.hints = s && s.hints ? s.hints : []; g.hard = s && s.rows.length ? !!s.hard : (g.boards === 1 && P.settings().hard);
    };
    if (modeKey === "desafio") {
      g.key = "desafio-" + challenge; g.isDaily = false;
      restore([challenge], saved && saved.key === g.key ? saved : null);
    } else if (mode.daily) {
      const daily = P.dailySet(new Date(), g.boards, mode.salt || 0);
      if (saved && saved.key === today && JSON.stringify(saved.answers) === JSON.stringify(daily)) { g.key = today; restore(daily, saved); }
      else if (saved && String(saved.key).startsWith("livre") && P.store.get("daily-done-" + modeKey) === today) { g.key = saved.key; g.isDaily = false; restore(saved.answers, saved); }
      else { g.key = today; restore(daily, null); }
    } else {
      const ok = saved && saved.answers && saved.answers.every((a) => P.words.answers.includes(a));
      g.key = "livre"; g.isDaily = false;
      restore(ok ? saved.answers : [P.randomWord()], ok ? saved : null);
    }
    return g;
  }

  const save = (g) => P.store.set("game-" + g.modeKey, { key: g.key, answers: g.answers, rows: g.rows, over: g.over, won: g.won, hints: g.hints, hard: g.hard });
  const solvedAt = (g, b, upTo = g.rows.length) => { const i = g.rows.indexOf(g.answers[b]); return i >= 0 && i < upTo ? i : -1; };
  const boardRows = (g, b) => { const at = solvedAt(g, b); return at >= 0 ? g.rows.slice(0, at + 1) : g.rows.slice(); };

  function hardProblem(g, word) {
    for (const w of g.rows) {
      const m = P.evaluate(w, g.answers[0]);
      for (let i = 0; i < 5; i++) if (m[i] === "c" && word[i] !== w[i]) return `A ${ORD[i]} letra precisa ser ${w[i]}`;
      const need = {};
      for (let i = 0; i < 5; i++) if (m[i] !== "a") need[w[i]] = (need[w[i]] || 0) + 1;
      for (const c in need) if ([...word].filter((x) => x === c).length < need[c]) return `A palavra precisa ter ${c}`;
    }
    return null;
  }

  function record(g) {
    const s = P.stats(g.modeKey), today = P.dayIndex(), tries = g.rows.length;
    s.played++;
    if (g.won) {
      s.won++; s.dist[tries - 1]++; if (tries === 1) s.firstTry++;
      if (g.modeKey === "infinito") s.streak++;
      else if (g.isDaily) s.streak = s.lastWinDay === today - 1 ? s.streak + 1 : 1;
      if (g.isDaily || g.modeKey === "infinito") s.lastWinDay = today;
      s.maxStreak = Math.max(s.maxStreak, s.streak);
    } else if (g.isDaily || g.modeKey === "infinito") s.streak = 0;
    P.store.set("stats-" + g.modeKey, s);
    P.logActivity();
    if (g.isDaily && g.mode.daily) P.store.set("daily-done-" + g.modeKey, P.dayKey());
    if (g.isDaily && g.modeKey === "termo") { const t = P.store.get("termo-days", {}); t[P.dayKey()] = g.won ? "w" : "l"; P.store.set("termo-days", t); }
  }

  /** Desenha a tela do jogo. */
  P.games.termoScreen = function (root, modeKey, challenge) {
    let g = createGame(modeKey, challenge);
    let busy = false;
    let meanings = {};
    P.meanings().then((m) => (meanings = m));

    const title = () => g.mode.title + (g.mode.daily && g.mode.free && !g.isDaily ? " · livre" : "");
    root.innerHTML = P.topbar(`<span data-title>${title()}</span>`, {
      help: true,
      extra: `<span class="badge hidden" data-hard>DIFÍCIL</span><button class="icon-btn" data-hint aria-label="Dica">💡</button>`,
    }) + `<div class="game"><div class="boards"><div class="boards-grid"></div></div><div class="center" data-after></div></div>`;
    const grid = P.$(".boards-grid", root);
    const after = P.$("[data-after]", root);

    const kb = P.keyboard(root, {
      onLetter: (c) => type(c), onEnter: () => submit(), onDelete: () => del(),
      marksFor: (ch) => g.answers.map((a, b) => {
        if (g.boards > 1 && solvedAt(g, b) >= 0) return null;
        const rows = boardRows(g, b).slice(0, busy ? g.rows.length - 1 : g.rows.length);
        return P.keyMarks(rows, a)[ch] || null;
      }),
    });
    root.addEventListener("arrow", (e) => { g.cursor = Math.max(0, Math.min(4, g.cursor + e.detail)); draw(); });

    function sizeBoards() {
      const cols = g.boards === 1 ? 1 : 2, gridRows = g.boards === 4 ? 2 : 1, gap = g.boards === 1 ? 6 : 3;
      const box = P.$(".boards", root).getBoundingClientRect();
      const w = Math.min(root.clientWidth, 540) - 8, h = Math.max(260, box.height - 10);
      const byW = (w - 12 * (cols - 1) - gap * 4 * cols) / (5 * cols);
      const byH = (h - 12 * (gridRows - 1) - gap * (g.maxTries - 1) * gridRows) / (g.maxTries * gridRows);
      const tile = Math.floor(Math.min(byW, byH, 62));
      grid.style.gridTemplateColumns = `repeat(${cols}, auto)`;
      grid.style.setProperty("--tile", tile + "px"); grid.style.setProperty("--gap", gap + "px");
    }

    function draw(revealRow = -1) {
      P.$("[data-title]", root).textContent = title();
      P.$("[data-hard]", root).classList.toggle("hidden", !g.hard);
      P.$("[data-hint]", root).classList.toggle("hidden", g.over);
      grid.innerHTML = "";
      for (let b = 0; b < g.boards; b++) {
        const rows = boardRows(g, b), at = solvedAt(g, b);
        const board = P.h(`<div class="board${g.boards > 1 && at >= 0 && !g.over && revealRow !== at ? " solved" : ""}"></div>`);
        for (let r = 0; r < g.maxTries; r++) {
          const row = P.h(`<div class="brow" data-r="${r}"></div>`);
          const isCur = r === g.rows.length && !g.over && at < 0;
          for (let c = 0; c < 5; c++) {
            const t = P.h(`<div class="tile"></div>`);
            if (r < rows.length) {
              const w = rows[r], m = P.evaluate(w, g.answers[b]);
              t.textContent = P.words.display(w)[c] || w[c];
              if (r === revealRow) { t.classList.add("pending"); setTimeout(() => { t.classList.remove("pending"); t.classList.add(m[c], "flip"); }, c * 250 + 160); }
              else t.classList.add(m[c]);
            } else if (isCur) {
              t.classList.add("input"); if (c === g.cursor && !busy) t.classList.add("sel");
              t.textContent = g.current[c] || "";
              t.addEventListener("click", () => { if (!g.over && !busy) { g.cursor = c; draw(); } });
            } else if (at >= 0 && r > at) t.classList.add("fade");
            row.appendChild(t);
          }
          board.appendChild(row);
        }
        grid.appendChild(board);
      }
      kb.paint();
      after.innerHTML = "";
      if (g.over && g.mode.free) {
        const b = P.h(`<button class="pill">${g.boards === 1 ? "Nova palavra" : "Jogar de novo"}</button>`);
        b.onclick = () => newWord(); after.appendChild(b);
      } else if (g.over) {
        const b = P.h(`<button class="pill ghost">Ver resultado</button>`); b.onclick = () => result(); after.appendChild(b);
      }
    }

    function nextEmpty(from) { for (let i = from; i < 5; i++) if (!g.current[i]) return i; for (let i = 0; i < Math.min(from, 5); i++) if (!g.current[i]) return i; return -1; }
    function type(c) {
      if (g.over || busy) return;
      g.current[g.cursor] = c; const n = nextEmpty(g.cursor + 1);
      g.cursor = n === -1 ? Math.min(g.cursor + 1, 4) : n; P.fx.type(); draw();
    }
    function del() {
      if (g.over || busy) return;
      if (g.current[g.cursor]) g.current[g.cursor] = null; else if (g.cursor > 0) { g.cursor--; g.current[g.cursor] = null; }
      draw();
    }
    function shake(msg) {
      P.fx.invalid(); P.toast(msg);
      grid.querySelectorAll(`.brow[data-r="${g.rows.length}"]`).forEach((r) => { r.classList.remove("shake"); void r.offsetWidth; r.classList.add("shake"); });
    }
    async function submit() {
      if (g.over || busy) return;
      if (g.current.some((x) => !x)) return shake("Palavra incompleta");
      const word = g.current.join("");
      if (!P.words.ok(word) && word !== g.answers[0]) return shake("Palavra não aceita");
      if (g.hard) { const p = hardProblem(g, word); if (p) return shake(p); }
      busy = true;
      g.rows.push(word); g.current = [null, null, null, null, null]; g.cursor = 0;
      const row = g.rows.length - 1;
      draw(row);
      const first = g.answers.findIndex((_, b) => solvedAt(g, b, row) < 0);
      const marks = P.evaluate(word, g.answers[Math.max(0, first)]);
      for (let i = 0; i < 5; i++) { await P.sleep(i ? 250 : 160); P.fx.reveal(i, marks[i]); }
      await P.sleep(280);
      busy = false;
      g.answers.forEach((a, b) => { if (g.boards > 1 && solvedAt(g, b) === row) { P.fx.win(); P.toast(`Palavra ${b + 1} certa!`); } });
      if (g.answers.every((_, b) => solvedAt(g, b) >= 0)) end(true);
      else if (g.rows.length >= g.maxTries) end(false);
      save(g); draw();
    }
    function end(win) {
      g.over = true; g.won = win; record(g);
      if (win) {
        const spare = g.maxTries - g.rows.length;
        P.fx.win(); P.confetti();
        P.toast(g.rows.length === 1 ? "Genial!" : spare >= 4 ? "Magnífico!" : spare >= 2 ? "Muito bem!" : spare === 1 ? "Acertou!" : "Ufa, por pouco!");
        setTimeout(result, 1800);
      } else {
        P.fx.lose();
        const miss = g.answers.filter((_, b) => solvedAt(g, b) < 0).map(P.words.display);
        P.toast(miss.length === 1 ? `A palavra era ${miss[0]}` : `Faltou: ${miss.join(", ")}`, 3500);
        setTimeout(result, 2200);
      }
    }
    function newWord() {
      const avoid = g.answers.slice();
      const answers = []; while (answers.length < g.boards) { const w = P.randomWord(avoid.concat(answers)); answers.push(w); }
      g.answers = answers; g.rows = []; g.over = false; g.won = false; g.hints = []; g.isDaily = false;
      g.key = "livre-" + Date.now(); g.hard = g.boards === 1 && P.settings().hard; g.current = [null, null, null, null, null]; g.cursor = 0;
      save(g); draw();
    }
    function hint() {
      if (g.over || busy) return;
      if (g.hints.length >= 2) return P.toast("Sem dicas nesta partida");
      const b = g.answers.findIndex((_, i) => solvedAt(g, i) < 0); if (b < 0) return;
      const ans = g.answers[b];
      const known = new Set(); boardRows(g, b).forEach((w) => P.evaluate(w, ans).forEach((m, i) => m === "c" && known.add(i)));
      const opts = [0, 1, 2, 3, 4].filter((i) => !known.has(i) && !g.hints.includes(i) && g.current[i] !== ans[i]);
      if (!opts.length) return P.toast("Nada para revelar");
      const pos = opts[Math.floor(Math.random() * opts.length)];
      g.hints.push(pos); g.current[pos] = ans[pos]; const n = nextEmpty(0); if (n >= 0) g.cursor = n;
      P.fx.reveal(pos, "c"); P.toast(`Dica: a ${ORD[pos]} letra é ${ans[pos]}`); save(g); draw();
    }
    P.$("[data-hint]", root).onclick = hint;
    P.$("[data-help]", root).onclick = () => P.games.help();

    function grids() { return g.answers.map((a, b) => boardRows(g, b).map((w) => P.evaluate(w, a))); }
    function result() {
      const s = P.stats(g.modeKey);
      const defs = g.answers.map((a) => meanings[a] ? `<b>${P.words.display(a)}</b>${P.esc(meanings[a])}` : "").filter(Boolean);
      const plural = g.boards > 1;
      const ui = P.sheet(`<h2>${g.won ? "Você acertou!" : "Não foi dessa vez"}</h2>
        <p class="subtitle">${plural ? "As palavras eram" : "A palavra era"} ${g.answers.map(P.words.display).join(", ")}</p>
        ${defs.length ? `<div class="meaning"><small>O que significa</small>${defs.join("")}</div>` : ""}
        ${g.modeKey === "desafio" ? "" : P.statsHTML([[s.played, "Jogos"], [Math.round((s.won / Math.max(1, s.played)) * 100) + "%", "Vitórias"], [s.streak, "Sequência"], [s.maxStreak, "Melhor"]]) + P.distHTML(s.dist, g.won ? g.rows.length : -1, g.maxTries)}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-next>${g.mode.free ? (g.boards === 1 ? "Nova palavra" : "Jogar de novo") : "Jogar Infinito"}</button></div>
        <button class="pill accent wide" style="margin-top:10px" data-story>Cartão para Stories</button>
        <p class="note">${g.mode.daily && g.isDaily ? (g.mode.free ? "Desafio do dia concluído. Novas partidas são livres." : "Uma palavra nova aparece amanhã.") : ""}</p>`);
      P.$("[data-share]", ui.el).onclick = () => {
        const score = (g.won ? `${g.rows.length}/${g.maxTries}` : `X/${g.maxTries}`) + (g.hard ? "*" : "") + (g.hints.length ? ` 💡${g.hints.length}` : "");
        const em = { c: "🟩", p: "🟨", a: "⬛" };
        P.share(`Palavreiro · ${g.mode.title} ${score}\n\n${grids().map((b) => b.map((r) => r.map((m) => em[m]).join("")).join("\n")).join("\n\n")}\n\n${location.origin}${location.pathname}`);
      };
      P.$("[data-next]", ui.el).onclick = () => { ui.close(); if (g.mode.free) newWord(); else P.go("infinito"); };
      P.$("[data-story]", ui.el).onclick = () => P.story({
        game: g.mode.title + (g.isDaily && g.mode.daily ? " do dia" : ""),
        headline: g.won ? `Acertei em ${g.rows.length}/${g.maxTries}` : `Quase! X/${g.maxTries}`,
        detail: [g.hard ? "modo difícil" : "", g.hints.length ? `${g.hints.length} dica(s)` : ""].filter(Boolean).join(" · ") || "Você consegue mais rápido?",
        grids: grids(),
        stats: g.modeKey === "desafio" ? [] : [[String(s.streak), "Sequência"], [Math.round((s.won / Math.max(1, s.played)) * 100) + "%", "Vitórias"], [String(s.maxStreak), "Melhor"]],
      });
    }

    sizeBoards(); draw();
    window.addEventListener("resize", () => { if (document.body.contains(grid)) { sizeBoards(); } });
    if (g.over) setTimeout(result, 400);
  };

  /** Janela de ajuda do Termo. */
  P.games.help = () => {
    const ex = (w, i, m, t) => `<div class="ex">${[...w].map((c, k) => `<div class="tile ${k === i ? m : "input"}">${c}</div>`).join("")}</div><p class="muted" style="margin:4px 0 10px">${t}</p>`;
    P.sheet(`<h2>Como jogar</h2>
      <p>Descubra a palavra de 5 letras em até 6 tentativas. Os acentos aparecem sozinhos.</p>
      ${ex("PEDRA", 0, "c", "O P está no lugar certo.")}${ex("CAMPO", 2, "p", "O M está na palavra, mas em outro lugar.")}${ex("TERMO", 4, "a", "O O não está na palavra.")}
      <p class="muted">Toque num quadrado da linha para escolher onde a próxima letra entra. No Dueto (7 tentativas) e no Quarteto (9) você descobre 2 ou 4 palavras ao mesmo tempo.</p>
      <p class="muted">💡 A lâmpada revela uma letra (até 2 por partida). No modo difícil, ligado no Perfil, as letras verdes precisam ficar no lugar e as amarelas precisam ser usadas.</p>
      <div class="footer">${P.creditHTML()}</div>`);
  };

  /** Criar desafio para um amigo. */
  P.games.challengeCreate = function (root) {
    let letters = [];
    root.innerHTML = P.topbar("Desafiar um amigo") + `<div class="game" style="justify-content:center;text-align:center">
      <h2 style="margin:0">Escolha uma palavra de 5 letras</h2>
      <p class="muted">O site cria um link. Quem abrir joga um Termo com a sua palavra, no navegador ou no app.</p>
      <div class="ex" style="justify-content:center" data-boxes></div><div data-out style="margin-top:16px"></div></div>`;
    const boxes = P.$("[data-boxes]", root), out = P.$("[data-out]", root);
    const draw = () => { boxes.innerHTML = [0, 1, 2, 3, 4].map((i) => `<div class="tile input" style="--tile:54px">${letters[i] || ""}</div>`).join(""); };
    P.keyboard(root, {
      enterLabel: "CRIAR",
      onLetter: (c) => { if (letters.length < 5) { letters.push(c); P.fx.type(); draw(); } },
      onDelete: () => { letters.pop(); draw(); },
      onEnter: () => {
        const w = letters.join("");
        if (w.length < 5) return P.toast("Complete as 5 letras");
        if (!P.words.ok(w)) { P.fx.invalid(); return P.toast("Palavra não aceita"); }
        const link = P.challenge.link(w);
        P.fx.win();
        out.innerHTML = `<p class="muted" style="word-break:break-all">${link}</p><button class="pill wide" data-send>Enviar desafio</button>`;
        P.$("[data-send]", out).onclick = () => P.share(`Te desafio no Palavreiro! Descubra a minha palavra de 5 letras: ${link}`);
      },
    });
    draw();
  };
})();
