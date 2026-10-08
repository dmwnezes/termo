// Motor do jogo Termo. Estrutura preparada para outros modos (Dueto, Quarteto).
(function () {
  "use strict";

  const WORD_LENGTH = 5;
  const MAX_TRIES = 6;
  const STATS_KEY = "termo-stats-v1";
  const STATE_KEY = "termo-state-v1";
  const ROWS = ["QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"];

  const $ = (id) => document.getElementById(id);

  // ---------- utilidades ----------
  function normalize(text) {
    return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase();
  }

  // Número de dias desde 01/01/2026, usado para escolher a palavra do dia.
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

  // Lista limpa: só palavras de 5 letras, sem repetição.
  const LIST = [...new Set(WORDS.map(normalize))].filter(
    (w) => w.length === WORD_LENGTH && /^[A-Z]+$/.test(w)
  );

  function wordOfTheDay() {
    return LIST[dayIndex() % LIST.length];
  }

  // Avalia a tentativa em duas passadas, tratando letras repetidas corretamente.
  function evaluate(guess, answer) {
    const result = Array(WORD_LENGTH).fill("absent");
    const counts = {};

    for (let i = 0; i < WORD_LENGTH; i++) {
      if (guess[i] === answer[i]) {
        result[i] = "correct";
      } else {
        counts[answer[i]] = (counts[answer[i]] || 0) + 1;
      }
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

  // ---------- estado ----------
  const game = {
    answer: wordOfTheDay(),
    key: todayKey(),
    guesses: [],     // { word, result }
    current: "",
    over: false,
    won: false,
    busy: false,
  };

  function loadState() {
    try {
      const saved = JSON.parse(localStorage.getItem(STATE_KEY) || "null");
      if (saved && saved.key === game.key) {
        Object.assign(game, { guesses: saved.guesses, over: saved.over, won: saved.won });
      }
    } catch (_) { /* ignora dados corrompidos */ }
  }

  function saveState() {
    try {
      localStorage.setItem(STATE_KEY, JSON.stringify({
        key: game.key, guesses: game.guesses, over: game.over, won: game.won,
      }));
    } catch (_) { /* armazenamento indisponível */ }
  }

  function loadStats() {
    try {
      return JSON.parse(localStorage.getItem(STATS_KEY)) || defaultStats();
    } catch (_) {
      return defaultStats();
    }
  }

  function defaultStats() {
    return { played: 0, won: 0, streak: 0, maxStreak: 0, lastWinDay: null, dist: [0, 0, 0, 0, 0, 0] };
  }

  function recordResult(won, tries) {
    const stats = loadStats();
    stats.played++;
    if (won) {
      stats.won++;
      stats.dist[tries - 1]++;
      const yesterday = dayIndex(new Date(Date.now() - 86400000));
      stats.streak = stats.lastWinDay === yesterday ? stats.streak + 1 : 1;
      stats.lastWinDay = dayIndex();
      stats.maxStreak = Math.max(stats.maxStreak, stats.streak);
    } else {
      stats.streak = 0;
    }
    try { localStorage.setItem(STATS_KEY, JSON.stringify(stats)); } catch (_) {}
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
      [...rows[r].children].forEach((tile, c) => {
        tile.textContent = g.word[c];
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
    if (!LIST.includes(guess)) {
      toast("Palavra não está na lista");
      shakeRow();
      return;
    }

    game.busy = true;
    const result = evaluate(guess, game.answer);
    game.guesses.push({ word: guess, result });
    game.current = "";
    render();

    // Espera a animação de revelação antes de encerrar a rodada.
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
        toast(game.answer, 4000);
      }
      saveState();
    }, WORD_LENGTH * 250 + 200);
  }

  function finish(won) {
    game.over = true;
    game.won = won;
    recordResult(won, game.guesses.length);
    renderStats();
    setTimeout(openStats, 1200);
  }

  // ---------- estatísticas ----------
  function renderStats() {
    const s = loadStats();
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
      const hit = game.won && game.guesses.length === i + 1;
      row.innerHTML = `<span class="n">${i + 1}</span>
        <span class="bar${hit ? " hit" : ""}" style="width:${Math.max(8, (count / max) * 100)}%">${count}</span>`;
      dist.appendChild(row);
    });
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
    if (/^[a-zA-Z]$/.test(key)) return typeLetter(normalize(key));
    if (key === "Enter") return submit();
    if (key === "Backspace") return deleteLetter();
  }

  document.addEventListener("keydown", (e) => {
    if (!$("modal-stats").hidden || !$("modal-help").hidden) {
      if (e.key === "Escape") closeModals();
      return;
    }
    onKey(e.key);
  });

  $("keyboard").addEventListener("click", (e) => {
    const btn = e.target.closest(".key");
    if (btn) onKey(btn.dataset.key);
  });

  $("btn-stats").addEventListener("click", openStats);
  $("btn-help").addEventListener("click", () => ($("modal-help").hidden = false));
  document.querySelectorAll("[data-close]").forEach((b) => b.addEventListener("click", closeModals));
  document.querySelectorAll(".modal").forEach((m) =>
    m.addEventListener("click", (e) => { if (e.target === m) closeModals(); })
  );

  // ---------- início ----------
  buildBoard();
  buildKeyboard();
  loadState();
  render();
  if (game.over) setTimeout(openStats, 400);
})();
