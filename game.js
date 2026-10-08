// Motor do jogo Termo. Modos: Diário (uma palavra por dia) e Infinito (quantas quiser).
// A estrutura já está preparada para Dueto e Quarteto (vários tabuleiros).
(function () {
  "use strict";

  const WORD_LENGTH = 5;
  const MAX_TRIES = 6;
  const ROWS = ["QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"];
  const $ = (id) => document.getElementById(id);

  // ---------- palavras ----------
  function normalize(text) {
    return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase();
  }

  // Respostas possíveis (sem acento) e a forma com acento para exibir.
  const ACCENTED = {};
  const ANSWER_LIST = [];
  ANSWERS.forEach((w) => {
    const n = normalize(w);
    if (n.length === WORD_LENGTH && !ACCENTED[n]) {
      ACCENTED[n] = w.toUpperCase();
      ANSWER_LIST.push(n);
    }
  });
  const ACCEPTED = new Set([...ANSWER_LIST, ...VALID.map(normalize)]);

  const show = (w) => ACCENTED[w] || w;

  // ---------- datas ----------
  function dayIndex(date = new Date()) {
    const start = Date.UTC(2026, 0, 1);
    const today = Date.UTC(date.getFullYear(), date.getMonth(), date.getDate());
    return Math.floor((today - start) / 86400000);
  }

  function todayKey() {
    const d = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  }

  // Embaralhamento fixo, para a ordem das palavras do dia não seguir a lista.
  function dailyWord() {
    const i = dayIndex();
    const idx = (i * 7919 + 104729) % ANSWER_LIST.length;
    return ANSWER_LIST[(idx + ANSWER_LIST.length) % ANSWER_LIST.length];
  }

  function randomWord(avoid) {
    let w;
    do {
      w = ANSWER_LIST[Math.floor(Math.random() * ANSWER_LIST.length)];
    } while (w === avoid && ANSWER_LIST.length > 1);
    return w;
  }

  // ---------- regras ----------
  function evaluate(guess, answer) {
    const result = Array(WORD_LENGTH).fill("absent");
    const counts = {};
    for (let i = 0; i < WORD_LENGTH; i++) {
      if (guess[i] === answer[i]) result[i] = "correct";
      else counts[answer[i]] = (counts[answer[i]] || 0) + 1;
    }
    for (let i = 0; i < WORD_LENGTH; i++) {
      if (result[i] === "correct") continue;
      if (counts[guess[i]] > 0) {
        result[i] = "present";
        counts[guess[i]]--;
      }
    }
    return result;
  }

  // ---------- armazenamento ----------
  const store = {
    get(key, fallback) {
      try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch (_) { return fallback; }
    },
    set(key, value) {
      try { localStorage.setItem(key, JSON.stringify(value)); } catch (_) { /* indisponível */ }
    },
  };

  const MODES = {
    diario: { stateKey: "termo-diario-state-v2", statsKey: "termo-diario-stats-v2", label: "Diário" },
    infinito: { stateKey: "termo-infinito-state-v2", statsKey: "termo-infinito-stats-v2", label: "Infinito" },
  };

  function defaultStats() {
    return { played: 0, won: 0, streak: 0, maxStreak: 0, lastWinDay: null, dist: [0, 0, 0, 0, 0, 0] };
  }

  // ---------- estado ----------
  const game = {
    mode: store.get("termo-mode", "diario"),
    answer: "",
    key: "",
    guesses: [], // { word, display, result }
    current: "",
    over: false,
    won: false,
    busy: false,
  };
  if (!MODES[game.mode]) game.mode = "diario";

  function startMode(mode) {
    game.mode = mode;
    store.set("termo-mode", mode);
    const saved = store.get(MODES[mode].stateKey, null);

    if (mode === "diario") {
      game.key = todayKey();
      game.answer = dailyWord();
      const valid = saved && saved.key === game.key;
      restore(valid ? saved : null);
    } else {
      const valid = saved && ANSWER_LIST.includes(saved.answer);
      game.key = "infinito";
      game.answer = valid ? saved.answer : randomWord();
      restore(valid ? saved : null);
    }

    document.querySelectorAll(".mode[data-mode]").forEach((b) => {
      const on = b.dataset.mode === mode;
      b.classList.toggle("active", on);
      b.setAttribute("aria-pressed", on);
    });
    $("btn-new").hidden = !(mode === "infinito" && game.over);
    buildBoard();
    render();
    if (game.over && mode === "diario") setTimeout(openStats, 300);
  }

  function restore(saved) {
    game.guesses = saved ? saved.guesses : [];
    game.over = saved ? saved.over : false;
    game.won = saved ? saved.won : false;
    game.current = "";
    game.busy = false;
  }

  function saveState() {
    store.set(MODES[game.mode].stateKey, {
      key: game.key, answer: game.answer, guesses: game.guesses, over: game.over, won: game.won,
    });
  }

  function newInfiniteWord() {
    game.answer = randomWord(game.answer);
    restore(null);
    saveState();
    $("btn-new").hidden = true;
    closeModals();
    buildBoard();
    render();
  }

  function recordResult(won, tries) {
    const key = MODES[game.mode].statsKey;
    const stats = store.get(key, defaultStats());
    stats.played++;
    if (won) {
      stats.won++;
      stats.dist[tries - 1]++;
      if (game.mode === "diario") {
        const yesterday = dayIndex(new Date(Date.now() - 86400000));
        stats.streak = stats.lastWinDay === yesterday ? stats.streak + 1 : 1;
        stats.lastWinDay = dayIndex();
      } else {
        stats.streak++; // no Infinito, a sequência conta vitórias seguidas
      }
      stats.maxStreak = Math.max(stats.maxStreak, stats.streak);
    } else {
      stats.streak = 0;
    }
    store.set(key, stats);
  }

  // ---------- interface ----------
  function buildBoard() {
    const board = $("board");
    board.innerHTML = "";
    for (let r = 0; r < MAX_TRIES; r++) {
      const row = document.createElement("div");
      row.className = "row";
      for (let c = 0; c < WORD_LENGTH; c++) {
        const tile = document.createElement("div");
        tile.className = "tile";
        row.appendChild(tile);
      }
      board.appendChild(row);
    }
  }

  function buildKeyboard() {
    const kb = $("keyboard");
    kb.innerHTML = "";
    ROWS.forEach((letters, idx) => {
      const row = document.createElement("div");
      row.className = "key-row";
      if (idx === 2) row.appendChild(makeKey("ENTER", "wide"));
      letters.split("").forEach((l) => row.appendChild(makeKey(l)));
      if (idx === 2) row.appendChild(makeKey("⌫", "wide"));
      kb.appendChild(row);
    });
  }

  function makeKey(label, extra) {
    const btn = document.createElement("button");
    btn.className = "key" + (extra ? " " + extra : "");
    btn.dataset.key = label === "⌫" ? "Backspace" : label === "ENTER" ? "Enter" : label;
    btn.textContent = label;
    return btn;
  }

  function render() {
    const rows = $("board").children;
    game.guesses.forEach((g, r) => {
      const letters = [...(g.display || g.word)];
      [...rows[r].children].forEach((tile, c) => {
        tile.textContent = letters[c];
        tile.className = "tile " + g.result[c];
      });
    });
    const currentRow = game.guesses.length;
    if (!game.over && currentRow < MAX_TRIES) {
      [...rows[currentRow].children].forEach((tile, c) => {
        tile.textContent = game.current[c] || "";
        tile.className = "tile" + (game.current[c] ? " filled" : "");
      });
    }
    paintKeyboard();
  }

  function paintKeyboard() {
    const rank = { absent: 1, present: 2, correct: 3 };
    const best = {};
    game.guesses.forEach((g) => {
      g.word.split("").forEach((l, i) => {
        const r = g.result[i];
        if (!best[l] || rank[r] > rank[best[l]]) best[l] = r;
      });
    });
    document.querySelectorAll(".key").forEach((btn) => {
      btn.className = "key" + (btn.classList.contains("wide") ? " wide" : "");
      const state = best[btn.dataset.key];
      if (state) btn.classList.add(state);
    });
  }

  function toast(msg, ms = 1500) {
    const el = $("toast");
    el.textContent = msg;
    el.classList.add("show");
    clearTimeout(toast._t);
    toast._t = setTimeout(() => el.classList.remove("show"), ms);
  }

  function shakeRow() {
    const row = $("board").children[game.guesses.length];
    if (!row) return;
    row.classList.add("shake");
    setTimeout(() => row.classList.remove("shake"), 400);
  }

  // ---------- ações ----------
  function typeLetter(letter) {
    if (game.over || game.busy || game.current.length >= WORD_LENGTH) return;
    game.current += letter;
    render();
  }

  function deleteLetter() {
    if (game.over || game.busy) return;
    game.current = game.current.slice(0, -1);
    render();
  }

  function submit() {
    if (game.over || game.busy) return;
    if (game.current.length < WORD_LENGTH) {
      toast("Palavra incompleta");
      shakeRow();
      return;
    }
    const guess = game.current;
    if (!ACCEPTED.has(guess)) {
      toast("Palavra não aceita");
      shakeRow();
      return;
    }

    game.busy = true;
    const result = evaluate(guess, game.answer);
    game.guesses.push({ word: guess, display: show(guess), result });
    game.current = "";
    render();

    const row = $("board").children[game.guesses.length - 1];
    [...row.children].forEach((tile, i) => {
      setTimeout(() => tile.classList.add("flip"), i * 250);
    });

    setTimeout(() => {
      game.busy = false;
      const won = result.every((r) => r === "correct");
      if (won) {
        finish(true);
        toast(["Genial!", "Muito bem!", "Boa!", "Uau!", "Acertou!", "Por pouco!"][game.guesses.length - 1]);
      } else if (game.guesses.length === MAX_TRIES) {
        finish(false);
        toast("A palavra era " + show(game.answer), 4000);
      }
      saveState();
    }, WORD_LENGTH * 250 + 200);
  }

  function finish(won) {
    game.over = true;
    game.won = won;
    recordResult(won, game.guesses.length);
    if (game.mode === "infinito") $("btn-new").hidden = false;
    setTimeout(openStats, 1400);
  }

  // ---------- estatísticas ----------
  function renderStats() {
    const s = store.get(MODES[game.mode].statsKey, defaultStats());
    $("stats-title").textContent = "Estatísticas · " + MODES[game.mode].label;
    $("s-played").textContent = s.played;
    $("s-winpct").textContent = s.played ? Math.round((s.won / s.played) * 100) : 0;
    $("s-streak").textContent = s.streak;
    $("s-max").textContent = s.maxStreak;

    const max = Math.max(1, ...s.dist);
    const dist = $("dist");
    dist.innerHTML = "";
    s.dist.forEach((count, i) => {
      const row = document.createElement("div");
      row.className = "dist-row";
      const hit = game.over && game.won && game.guesses.length === i + 1;
      row.innerHTML = `<span class="n">${i + 1}</span>
        <span class="bar${hit ? " hit" : ""}" style="width:${Math.max(8, (count / max) * 100)}%">${count}</span>`;
      dist.appendChild(row);
    });

    const isInf = game.mode === "infinito";
    $("stats-new").hidden = !(isInf && game.over);
    $("stats-next").hidden = !(game.mode === "diario" && game.over);
  }

  function openStats() {
    renderStats();
    $("modal-stats").hidden = false;
  }

  function closeModals() {
    document.querySelectorAll(".modal").forEach((m) => (m.hidden = true));
  }

  // ---------- eventos ----------
  function onKey(key) {
    if (/^[a-zA-ZçÇ]$/.test(key)) return typeLetter(normalize(key));
    if (key === "Enter") return submit();
    if (key === "Backspace") return deleteLetter();
  }

  document.addEventListener("keydown", (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    const modalOpen = [...document.querySelectorAll(".modal")].some((m) => !m.hidden);
    if (modalOpen) {
      if (e.key === "Escape") closeModals();
      return;
    }
    onKey(e.key);
  });

  $("keyboard").addEventListener("click", (e) => {
    const btn = e.target.closest(".key");
    if (btn) { onKey(btn.dataset.key); btn.blur(); }
  });

  document.querySelectorAll(".mode[data-mode]").forEach((b) =>
    b.addEventListener("click", () => {
      if (game.busy || b.dataset.mode === game.mode) return;
      b.blur();
      startMode(b.dataset.mode);
    })
  );

  $("btn-new").addEventListener("click", newInfiniteWord);
  $("stats-new").addEventListener("click", newInfiniteWord);
  $("btn-stats").addEventListener("click", openStats);
  $("btn-help").addEventListener("click", () => ($("modal-help").hidden = false));
  document.querySelectorAll("[data-close]").forEach((b) => b.addEventListener("click", closeModals));
  document.querySelectorAll(".modal").forEach((m) =>
    m.addEventListener("click", (e) => { if (e.target === m) closeModals(); })
  );

  // ---------- início ----------
  buildKeyboard();
  startMode(game.mode);
})();
