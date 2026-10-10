// Termo, Infinito, Dueto, Quarteto e Desafio (site). Mesmas regras do app.
// Termo, Dueto e Quarteto têm o seletor "Do dia | Infinito": a aba Do dia sempre mostra o desafio de hoje;
// a aba Infinito usa um save separado (Termo: "game-infinito"; Dueto/Quarteto: "game-livre-<modo>").
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

  /** Estado de uma partida. free = partida livre (aba Infinito) do Dueto/Quarteto. */
  function createGame(modeKey, challenge, archDay, free) {
    const mode = MODES[modeKey];
    free = !!free && mode.free && mode.daily && !archDay;
    const g = { modeKey, mode, boards: mode.boards, maxTries: 5 + mode.boards, rows: [], current: [null, null, null, null, null], cursor: 0, over: false, won: false, hints: [], hard: false, isDaily: mode.daily && !free, key: "", slot: "game-" + modeKey, arch: archDay || null, free: modeKey === "infinito" || free };
    const today = P.dayKey();
    if (archDay) g.slot = "game-arquivo-" + modeKey;
    else if (free) g.slot = "game-livre-" + modeKey;
    // Versões antigas guardavam a partida livre no lugar do desafio do dia: move para o save do Infinito.
    if (mode.daily && mode.free && !archDay) {
      const old = P.store.get("game-" + modeKey, null);
      if (old && String(old.key).startsWith("livre")) {
        if (!P.store.get("game-livre-" + modeKey, null)) P.store.set("game-livre-" + modeKey, old);
        const won = P.store.get("results", {})[modeKey + "|" + today] === "w";
        if (P.store.get("daily-done-" + modeKey) === today) P.store.set("game-" + modeKey, { key: today, answers: P.dailySet(new Date(), mode.boards, mode.salt || 0), rows: [], over: true, won, hints: [], hard: false });
        else P.store.set("game-" + modeKey, null);
      }
    }
    const saved = P.store.get(g.slot, null);
    const restore = (answers, s) => {
      g.answers = answers; g.rows = s ? s.rows : []; g.over = s ? s.over : false; g.won = s ? s.won : false;
      g.hints = s && s.hints ? s.hints : []; g.hard = s && s.rows.length ? !!s.hard : (g.boards === 1 && P.settings().hard);
    };
    if (archDay) {
      // Arquivo: mesmas palavras do dia escolhido, em slot próprio, sem mexer em estatísticas.
      g.key = archDay; g.isDaily = false;
      const answers = P.dailySet(P.parseDay(archDay), g.boards, mode.salt || 0);
      restore(answers, saved && saved.key === archDay && JSON.stringify(saved.answers) === JSON.stringify(answers) ? saved : null);
    } else if (modeKey === "desafio") {
      g.key = "desafio-" + challenge; g.isDaily = false;
      restore([challenge], saved && saved.key === g.key ? saved : null);
    } else if (mode.daily && !free) {
      const daily = P.dailySet(new Date(), g.boards, mode.salt || 0);
      g.key = today;
      restore(daily, saved && saved.key === today && JSON.stringify(saved.answers) === JSON.stringify(daily) ? saved : null);
    } else {
      const ok = saved && saved.answers && saved.answers.length === g.boards && saved.answers.every((a) => P.words.answers.includes(a));
      g.key = ok && saved.key ? saved.key : "livre"; g.isDaily = false;
      const fresh = []; while (fresh.length < g.boards) fresh.push(P.randomWord(fresh));
      restore(ok ? saved.answers : fresh, ok ? saved : null);
    }
    return g;
  }

  /** Partida com amigo: palavras fixas, sem save, sem dicas, sem modo difícil e sem estatísticas. */
  function mpGame(mp) {
    const mode = MODES[mp.mode];
    return { modeKey: mp.mode, mode, boards: mode.boards, maxTries: 5 + mode.boards, rows: [], current: [null, null, null, null, null], cursor: 0, over: false, won: false, hints: [], hard: false, isDaily: false, key: "mp", slot: null, arch: null, free: false, mp: true, answers: mp.answers.slice() };
  }

  const save = (g) => g.slot && P.store.set(g.slot, { key: g.key, answers: g.answers, rows: g.rows, over: g.over, won: g.won, hints: g.hints, hard: g.hard });
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
    if (g.mp) return;
    // Estatística das letras: 1º chute das partidas de 1 tabuleiro (Termo, Infinito, Arquivo do Termo, desafio).
    if (g.boards === 1 && g.rows.length) P.addFirstGuess(g.rows[0], g.won ? g.rows.length : 7);
    if (g.arch) return P.archiveDone(g.modeKey, g.arch, g.won);
    if (g.isDaily && g.mode.daily) {
      P.setResult(g.modeKey, P.dayKey(), g.won);
      if (g.modeKey === "termo") P.setHistory(P.dayKey(), "termo", g.won ? g.rows.length : 7);
    }
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
  P.games.termoScreen = function (root, modeKey, challenge, archDay, opts = {}) {
    const mp = opts.mp || null;
    let g = mp ? mpGame(mp) : createGame(modeKey, challenge, archDay, opts.free);
    let busy = false;
    let replaying = null, replayed = false;
    const locked = () => g.over || busy || replaying || (mp && mp.locked());
    let meanings = {};
    P.meanings().then((m) => (meanings = m));

    // Seletor "Do dia | Infinito": Termo ↔ Infinito; Dueto/Quarteto ↔ partidas livres.
    const base = modeKey === "infinito" ? "termo" : modeKey;
    const hasSwitch = !mp && !archDay && MODES[base].daily;
    const showTab = (inf) => P.games.termoScreen(root, inf && base === "termo" ? "infinito" : base, null, null, { free: inf && base !== "termo", quiet: true });
    const gameName = () => MODES[base].title + (g.free && base !== "termo" ? " Infinito" : "");
    const title = () => mp ? mp.title : g.arch ? `${g.mode.title} · ${P.shortDay(g.arch)}` : MODES[base].title;
    root.innerHTML = P.topbar(`<span data-title>${title()}</span>`, {
      help: true,
      extra: (mp && mp.extra ? mp.extra : "") + `<span class="badge hidden" data-hard>DIFÍCIL</span><button class="icon-btn" data-hint aria-label="Dica">💡</button>`,
    }) + (hasSwitch ? P.modeSwitch(g.free) : "") + `<div class="game">${mp ? `<div class="opp" data-opp></div>` : ""}<div class="boards"><div class="boards-grid"></div></div><div class="center" data-after></div></div>`;
    if (hasSwitch) P.bindModeSwitch(root, showTab);
    const grid = P.$(".boards-grid", root);
    // Durante o replay, um toque no tabuleiro pula para o fim.
    P.$(".boards", root).addEventListener("click", () => { if (replaying) replaying.skip = true; });
    const after = P.$("[data-after]", root);
    if (mp && mp.mount) mp.mount(P.$("[data-opp]", root));

    const kb = P.keyboard(root, {
      onLetter: (c) => type(c), onEnter: () => submit(), onDelete: () => del(),
      marksFor: (ch) => g.answers.map((a, b) => {
        if (g.boards > 1 && solvedAt(g, b) >= 0) return null;
        const rows = boardRows(g, b).slice(0, busy ? g.rows.length - 1 : g.rows.length);
        return P.keyMarks(rows, a)[ch] || null;
      }),
    });
    if (root._arrow) root.removeEventListener("arrow", root._arrow);
    root._arrow = (e) => { if (document.body.contains(grid)) { g.cursor = Math.max(0, Math.min(4, g.cursor + e.detail)); draw(); } };
    root.addEventListener("arrow", root._arrow);

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
      P.$("[data-hint]", root).classList.toggle("hidden", g.over || !!mp || !!replaying);
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
              t.addEventListener("click", () => { if (!locked()) { g.cursor = c; draw(); } });
            } else if (at >= 0 && r > at) t.classList.add("fade");
            row.appendChild(t);
          }
          board.appendChild(row);
        }
        grid.appendChild(board);
      }
      kb.paint();
      after.innerHTML = "";
      if (g.over && mp) mp.after(after);
      else if (g.over && g.arch) {
        const r = P.h(`<button class="pill ghost">Ver resultado</button>`); r.onclick = () => result();
        const b = P.h(`<button class="pill" style="margin-left:8px">Voltar ao arquivo</button>`); b.onclick = () => P.go("arquivo");
        after.append(r, b);
      } else if (g.over && g.free) {
        if (replayed) { const r = P.h(`<button class="pill ghost" style="margin-right:8px">Ver resultado</button>`); r.onclick = () => result(); after.appendChild(r); }
        const b = P.h(`<button class="pill">${g.boards === 1 ? "Nova palavra" : "Jogar de novo"}</button>`);
        b.onclick = () => newWord(); after.appendChild(b);
      } else if (g.over) {
        const b = P.h(`<button class="pill ghost">Ver resultado</button>`); b.onclick = () => result(); after.appendChild(b);
        if (hasSwitch) { const i = P.h(`<button class="pill" style="margin-left:8px">Jogar Infinito</button>`); i.onclick = () => showTab(true); after.appendChild(i); }
      }
    }

    function nextEmpty(from) { for (let i = from; i < 5; i++) if (!g.current[i]) return i; for (let i = 0; i < Math.min(from, 5); i++) if (!g.current[i]) return i; return -1; }
    function type(c) {
      if (locked()) return;
      g.current[g.cursor] = c; const n = nextEmpty(g.cursor + 1);
      g.cursor = n === -1 ? Math.min(g.cursor + 1, 4) : n; P.fx.type(); draw();
    }
    function del() {
      if (locked()) return;
      if (g.current[g.cursor]) g.current[g.cursor] = null; else if (g.cursor > 0) { g.cursor--; g.current[g.cursor] = null; }
      draw();
    }
    function shake(msg) {
      P.fx.invalid(); P.toast(msg);
      grid.querySelectorAll(`.brow[data-r="${g.rows.length}"]`).forEach((r) => { r.classList.remove("shake"); void r.offsetWidth; r.classList.add("shake"); });
    }
    async function submit() {
      if (locked()) return;
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
      if (g.over) { draw(); return; } // partida com amigo encerrada pelo adversário durante a revelação
      // Partida com amigo: manda só as cores (tabuleiro resolvido antes desta linha vai vazio).
      if (mp) mp.onRow(row, g.answers.map((a, b) => (solvedAt(g, b, row) >= 0 ? "" : P.evaluate(word, a).join(""))).join("|"));
      g.answers.forEach((a, b) => { if (g.boards > 1 && solvedAt(g, b) === row) { P.fx.win(); P.toast(`Palavra ${b + 1} certa!`); } });
      if (g.answers.every((_, b) => solvedAt(g, b) >= 0)) end(true);
      else if (g.rows.length >= g.maxTries) end(false);
      save(g); draw();
    }
    function end(win) {
      if (mp) {
        g.over = true; g.won = win;
        if (win) { P.fx.win(); P.confetti(); } else { P.fx.lose(); P.toast("Suas tentativas acabaram", 2200); }
        mp.onEnd(win, g.rows.length);
        return;
      }
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
      if (locked() || mp) return;
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
    /** Replay: limpa o tabuleiro e refaz a partida letra por letra, sem som e sem vibração. */
    async function replay() {
      if (replaying || busy || !g.over) return;
      const full = { rows: g.rows.slice(), won: g.won }, st = (replaying = { skip: false });
      const alive = () => document.body.contains(grid) && !st.skip;
      const blank = () => [null, null, null, null, null];
      const wait = async (ms) => { const until = Date.now() + ms; while (Date.now() < until && alive()) await P.sleep(40); };
      P.fx.muted = true;
      g.rows = []; g.over = false; g.current = blank(); g.cursor = 0;
      draw(); await wait(350);
      for (const word of full.rows) {
        for (let i = 0; i < 5 && alive(); i++) { g.current[i] = word[i]; g.cursor = Math.min(i + 1, 4); draw(); await wait(110); }
        if (!alive()) break;
        g.rows.push(word); g.current = blank(); busy = true; draw(g.rows.length - 1);
        await wait(160 + 250 * 4 + 340); busy = false;
        if (!alive()) break;
        draw(); await wait(400);
      }
      g.rows = full.rows; g.over = true; g.won = full.won; g.current = blank(); g.cursor = 0;
      busy = false; replaying = null; replayed = true; P.fx.muted = false;
      if (document.body.contains(grid)) draw();
    }

    function result() {
      if (mp) return mp.showResult();
      const s = P.stats(g.modeKey);
      const defs = g.answers.map((a) => meanings[a] ? `<b>${P.words.display(a)}</b>${P.esc(meanings[a])}` : "").filter(Boolean);
      const plural = g.boards > 1;
      const ui = P.sheet(`<h2>${g.won ? "Você acertou!" : "Não foi dessa vez"}</h2>
        <p class="subtitle">${plural ? "As palavras eram" : "A palavra era"} ${g.answers.map(P.words.display).join(", ")}</p>
        ${defs.length ? `<div class="meaning"><small>O que significa</small>${defs.join("")}</div>` : ""}
        ${g.modeKey === "desafio" || g.arch ? "" : P.statsHTML([[s.played, "Jogos"], [Math.round((s.won / Math.max(1, s.played)) * 100) + "%", "Vitórias"], [s.streak, "Sequência"], [s.maxStreak, "Melhor"]]) + P.distHTML(s.dist, g.won ? g.rows.length : -1, g.maxTries)}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-next>${g.arch ? "Voltar ao arquivo" : g.free ? (g.boards === 1 ? "Nova palavra" : "Jogar de novo") : "Jogar Infinito"}</button></div>
        <div class="row-btns wrap" style="margin-top:10px"><button class="pill ghost" data-replay>▶ Replay</button><button class="pill accent" data-story>Cartão para Stories</button></div>
        <p class="note">${g.arch ? `Desafio de ${P.shortDay(g.arch)} do arquivo.` : g.mode.daily && g.isDaily ? (g.boards === 1 ? "Uma palavra nova aparece amanhã." : "Palavras novas aparecem amanhã.") : ""}</p>`);
      P.$("[data-share]", ui.el).onclick = () => {
        const score = (g.won ? `${g.rows.length}/${g.maxTries}` : `X/${g.maxTries}`) + (g.hard ? "*" : "") + (g.hints.length ? ` 💡${g.hints.length}` : "");
        const em = { c: "🟩", p: "🟨", a: "⬛" };
        P.share(`Palavreiro · ${g.arch ? title() : gameName()} ${score}\n\n${grids().map((b) => b.map((r) => r.map((m) => em[m]).join("")).join("\n")).join("\n\n")}\n\n${location.origin}${location.pathname}`);
      };
      P.$("[data-replay]", ui.el).onclick = () => { ui.close(); replay(); };
      P.$("[data-next]", ui.el).onclick = () => { ui.close(); if (g.arch) P.go("arquivo"); else if (g.free) newWord(); else if (hasSwitch) showTab(true); else P.go("infinito"); };
      P.$("[data-story]", ui.el).onclick = () => P.story({
        game: g.arch ? title() : gameName() + (g.isDaily && g.mode.daily ? " do dia" : ""),
        headline: g.won ? `Acertei em ${g.rows.length}/${g.maxTries}` : `Quase! X/${g.maxTries}`,
        detail: [g.hard ? "modo difícil" : "", g.hints.length ? `${g.hints.length} dica(s)` : ""].filter(Boolean).join(" · ") || "Você consegue mais rápido?",
        grids: grids(),
        stats: g.modeKey === "desafio" || g.arch ? [] : [[String(s.streak), "Sequência"], [Math.round((s.won / Math.max(1, s.played)) * 100) + "%", "Vitórias"], [String(s.maxStreak), "Melhor"]],
      });
    }

    sizeBoards(); draw();
    window.addEventListener("resize", () => { if (document.body.contains(grid)) { sizeBoards(); } });
    if (g.over && !opts.quiet) setTimeout(result, 400);
    return {
      get game() { return g; },
      replay,
      /** Partida com amigo: o adversário já venceu, encerra sem mexer em nada. */
      stop() { if (!g.over) { g.over = true; g.won = false; if (!busy) draw(); } },
      redraw: () => { if (!replaying && !busy && document.body.contains(grid)) draw(); },
      resize: () => sizeBoards(),
    };
  };

  /** Janela de ajuda do Termo. */
  P.games.help = () => {
    const ex = (w, i, m, t) => `<div class="ex">${[...w].map((c, k) => `<div class="tile ${k === i ? m : "input"}">${c}</div>`).join("")}</div><p class="muted" style="margin:4px 0 10px">${t}</p>`;
    P.sheet(`<h2>Como jogar</h2>
      <p>Descubra a palavra de 5 letras em até 6 tentativas. Os acentos aparecem sozinhos.</p>
      ${ex("PEDRA", 0, "c", "O P está no lugar certo.")}${ex("CAMPO", 2, "p", "O M está na palavra, mas em outro lugar.")}${ex("TERMO", 4, "a", "O O não está na palavra.")}
      <p class="muted">Toque num quadrado da linha para escolher onde a próxima letra entra. No Dueto (7 tentativas) e no Quarteto (9) você descobre 2 ou 4 palavras ao mesmo tempo.</p>
      <p class="muted">Com a vibração ligada, o verde dá um toque e o amarelo dá dois.</p>
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
