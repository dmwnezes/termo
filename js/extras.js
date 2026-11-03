// Palavreiro (site): painel "Hoje", conquistas, gráfico de evolução e sincronização com o app.
(function () {
  "use strict";
  const n = (k) => P.store.get(k, 0) || 0;

  // ---------- painel Hoje ----------
  const TODAY_GAMES = [
    ["termo", "Termo", () => P.store.get("daily-done-termo")],
    ["dueto", "Dueto", () => P.store.get("daily-done-dueto")],
    ["quarteto", "Quarteto", () => P.store.get("daily-done-quarteto")],
    ["conexoes", "Conexões", () => P.store.get("conn-done")],
    ["caca", "Caça", () => P.store.get("ws-done")],
  ];
  P.todayStatus = () => { const t = P.dayKey(); return TODAY_GAMES.map(([k, name, f]) => ({ k, name, done: f() === t })); };

  // Compacto: "Hoje" + 5 bolinhas (verde = feito; toque abre o jogo) + "3/5" + botão do Arquivo, numa linha só.
  const TODAY_MARK = { termo: "T", dueto: "D", quarteto: "Q", conexoes: "C", caca: "CP" };
  P.todayPanel = () => {
    const st = P.todayStatus(), done = st.filter((x) => x.done).length, all = done === st.length;
    return `<div class="today${all ? " all" : ""}">
      <b class="today-title">Hoje</b>
      <div class="today-dots">${st.map((x) => `<button class="tdot${x.done ? " done" : ""}" data-go="${x.k}" title="${x.name}${x.done ? " (feito)" : ""}" aria-label="${x.name}: ${x.done ? "feito" : "a fazer"}">${TODAY_MARK[x.k]}</button>`).join("")}</div>
      <span class="today-count" role="progressbar" aria-label="Desafios do dia" aria-valuemin="0" aria-valuemax="${st.length}" aria-valuenow="${done}">${all ? "Tudo feito hoje 🎉" : `${done}/${st.length}`}</span>
      <button class="today-arch" data-go="arquivo" aria-label="Arquivo de desafios">🗂️<span> Arquivo</span></button>
    </div>`;
  };
  /** Confete e contador uma vez por dia quando os 5 desafios estão feitos. */
  P.checkAllDone = () => {
    const t = P.dayKey();
    if (P.todayStatus().every((x) => x.done) && P.store.get("alldone-day") !== t) {
      P.store.set("alldone-day", t); P.store.add("alldone-days"); P.confetti(); P.fx.win();
    }
  };

  // ---------- conquistas (mesma lista e ordem do app) ----------
  P.achievements = () => {
    const st = Object.fromEntries(["termo", "infinito", "dueto", "quarteto"].map((k) => [k, P.stats(k)]));
    const d = st.termo, inf = st.infinito, wins = d.won + inf.won, first = d.firstTry + inf.firstTry;
    const fast = (b) => b >= 1 && b <= 119;
    return [
      ["🌱", "Primeira palavra", "Acerte sua primeira palavra", wins, 1],
      ["🎯", "De primeira", "Acerte na primeira tentativa", first, 1],
      ["⚡", "Rapidinho", "Acerte em duas tentativas", d.dist[1] + inf.dist[1] + first, 1],
      ["🔥", "Três dias seguidos", "Sequência de 3 no Termo", d.maxStreak, 3],
      ["📅", "Uma semana", "Sequência de 7 no Termo", d.maxStreak, 7],
      ["🏆", "Um mês", "Sequência de 30 no Termo", d.maxStreak, 30],
      ["♾️", "Maratona", "Acerte 25 palavras no Infinito", inf.won, 25],
      ["📚", "Palavreiro", "Acerte 100 palavras no total", wins, 100],
      ["👯", "Dupla certa", "Vença um Dueto", st.dueto.won, 1],
      ["🍀", "Quatro de uma vez", "Vença um Quarteto", st.quarteto.won, 1],
      ["🧩", "Conectado", "Resolva um Conexões", n("conn-won"), 1],
      ["💎", "Sem errar", "Resolva um Conexões sem erros", n("conn-perfect"), 1],
      ["♻️", "Conexão sem fim", "Resolva 10 Conexões no Infinito", n("conn-inf-won"), 10],
      ["🔎", "Olho de águia", "Termine um Caça-Palavras em menos de 2 minutos", fast(n("ws-best")) || fast(n("ws-inf-best")) ? 1 : 0, 1],
      ["🗺️", "Caçador incansável", "Termine 10 grades no Caça-Palavras Infinito", n("ws-inf-played"), 10],
      ["📖", "Dicionário ambulante", "Acerte 10 seguidas no Qual é a Palavra?", n("def-best"), 10],
      ["⛓️", "Corrente forte", "Faça uma cadeia de 20 no Sinônimo ou Antônimo", Math.max(n("syn-best"), n("sa-best")), 20],
      ["🔄", "Do avesso", "Acerte 20 pegadinhas no Sinônimo ou Antônimo", Math.max(n("ant-best"), n("sa-tricks")), 20],
      ["🕵️", "Detetive", "Faça 10 pontos no Intruso", n("intr-best"), 10],
      ["✍️", "Escrita impecável", "Faça 20 pontos no Certo ou Errado", n("ort-best"), 20],
      ["👑", "Obediente", "Faça 15 pontos no Mestre Mandou", n("mestre-best"), 15],
      ["🧠", "Sabichão", "Ganhe 1 partida de Trivia (as 6 medalhas)", n("trivia-won"), 1],
      ["📚", "Enciclopédia", "Acerte 100 perguntas na Trivia", n("trivia-right"), 100],
      ["🃏", "Memória de elefante", "Termine a Memória no Difícil", n("mem-best-dificil") > 0 ? 1 : 0, 1],
      ["🎨", "Olho clínico", "Faça 95 pontos numa cor no Matiz", n("matiz-top"), 95],
      ["🏆", "Pódio perfeito", "Acerte um Pódio de primeira", n("podio-first"), 1],
      ["⏳", "Historiador", "Acerte as 10 cartas do dia na Ampulheta", n("amp-day-best"), 10],
      ["🔢", "Mestre do Quadrante", "Termine um Quadrante Difícil sem errar", n("sudoku-perfect"), 1],
      ["♟️", "Xeque-mate", "Vença o computador no Xeque (Difícil)", n("xadrez-won-dificil"), 1],
      ["💣", "Desarmador", "Limpe um Campo Minado no Difícil", n("mina-best-dificil") > 0 ? 1 : 0, 1],
      ["🔗", "Tudo ligado", "Complete um Caminhos no Difícil", n("cam-best-dificil") > 0 ? 1 : 0, 1],
      ["🧐", "Caça-mitos", "Acerte as 10 do dia no Fato ou Mito", n("fm-day-best"), 10],
      ["🗺️", "Cartógrafo", "Acerte 10 Silhuetas", n("sil-won"), 10],
      ["⚖️", "Bom de conta", "Acerte as 10 rodadas do dia no Mais ou Menos", n("mm-day-best"), 10],
      ["✏️", "Cruzadista", "Resolva 10 Palavras Cruzadas", n("cruz-won"), 10],
      ["🚢", "Almirante", "Vença o computador na Batalha Naval (Difícil)", n("naval-won-dificil"), 1],
      ["⚔️", "Duelista", "Vença 5 partidas com amigos", n("mp-won"), 5],
      ["🗂️", "Viajante do tempo", "Termine 5 desafios do Arquivo", n("arch-played"), 5],
      ["🌟", "Dia completo", "Faça os 5 desafios do dia", n("alldone-days"), 1],
    ].map(([emoji, title, desc, v, goal]) => ({ emoji, title, desc, goal, progress: Math.min(v, goal), ok: v >= goal }));
  };

  // ---------- gráfico de evolução ----------
  const pad = (x) => String(x).padStart(2, "0");
  P.evolutionHTML = (kind) => {
    const hist = P.store.get("history", {});
    const now = new Date(), monday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - ((now.getDay() + 6) % 7));
    const weeks = [];
    for (let w = 7; w >= 0; w--) {
      const start = new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() - 7 * w), vals = [];
      for (let k = 0; k < 7; k++) {
        const h = hist[P.dayKey(new Date(start.getFullYear(), start.getMonth(), start.getDate() + k))];
        if (h && typeof h[kind] === "number") vals.push(h[kind]);
      }
      weeks.push({ label: `${pad(start.getDate())}/${pad(start.getMonth() + 1)}`, v: vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : null });
    }
    if (weeks.every((w) => w.v == null)) return `<p class="muted evo-empty">Jogue os desafios do dia para ver sua evolução.</p>`;
    const top = kind === "termo" ? 7 : kind === "conn" ? 4 : Math.max(60, ...weeks.map((w) => w.v || 0));
    const fmt = (v) => (kind === "caca" ? P.fmtTime(Math.round(v)) : v.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 }));
    return `<div class="evo" role="img" aria-label="Média por semana nas últimas 8 semanas">${weeks.map((w) => `<div class="evo-col">
        <div class="evo-track">${w.v == null ? `<i class="evo-bar empty"></i>` : `<span class="evo-val">${fmt(w.v)}</span><i class="evo-bar" style="height:${Math.max(4, (w.v / top) * 100)}%"></i>`}</div>
        <small>${w.label}</small></div>`).join("")}</div>
      <p class="muted evo-legend">Quanto menor, melhor</p>`;
  };

  // ---------- sincronizar site e app ----------
  // Nome canônico (app) → chave do site. As chaves rev_* (Reverso, removido) ficam só por compatibilidade.
  const COUNTERS = ["conn_won", "conn_perfect", "conn_played", "conn_inf_played", "conn_inf_won", "ws_played", "ws_best", "ws_inf_played", "ws_inf_best",
    "rev_appwins", "rev_userwins", "rev_played", "def_best", "def_right", "syn_best", "intr_played", "intr_best", "intr_right",
    "ort_played", "ort_best", "ort_right", "mestre_played", "mestre_best", "mestre_right", "ant_best", "arch_played", "arch_won", "alldone_days",
    "mp_played", "mp_won", "sa_best", "sa_tricks",
    "trivia_won", "trivia_best", "trivia_right",
    "mem_best_facil", "mem_best_medio", "mem_best_dificil", "mem_moves_facil", "mem_moves_medio", "mem_moves_dificil",
    "matiz_best", "matiz_played", "matiz_top", "podio_won", "podio_played", "podio_streak", "podio_first", "amp_best", "amp_day_best", "amp_played",
    "sudoku_best_facil", "sudoku_best_medio", "sudoku_best_dificil", "sudoku_won", "sudoku_perfect",
    "xadrez_won_facil", "xadrez_won_medio", "xadrez_won_dificil",
    "naval_won_facil", "naval_won_medio", "naval_won_dificil", "cruz_won", "cruz_perfect",
    "sil_won", "sil_played", "sil_first", "mm_best", "mm_day_best", "mm_played",
    "mina_won", "mina_played", "mina_best_facil", "mina_best_medio", "mina_best_dificil",
    "cam_won", "cam_best_facil", "cam_best_medio", "cam_best_dificil", "fm_best", "fm_day_best", "fm_played"];
  // Contadores em que menor é melhor (tempo e jogadas): 0 = ainda não jogou.
  const LOWER = ["ws_best", "ws_inf_best", "mem_best_facil", "mem_best_medio", "mem_best_dificil", "mem_moves_facil", "mem_moves_medio", "mem_moves_dificil",
    "sudoku_best_facil", "sudoku_best_medio", "sudoku_best_dificil",
    "mina_best_facil", "mina_best_medio", "mina_best_dificil", "cam_best_facil", "cam_best_medio", "cam_best_dificil"];
  const siteKey = (c) => (c === "rev_appwins" ? "rev-app" : c === "rev_userwins" ? "rev-user" : c.replace(/_/g, "-"));
  const MODES = ["termo", "infinito", "dueto", "quarteto"];
  const toB64 = (str) => { let bin = ""; new TextEncoder().encode(str).forEach((b) => (bin += String.fromCharCode(b))); return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, ""); };
  const fromB64 = (s) => { const b = s.replace(/-/g, "+").replace(/_/g, "/"); const bin = atob(b + "=".repeat((4 - (b.length % 4)) % 4)); return new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(bin, (c) => c.charCodeAt(0))); };
  const int = (x) => (typeof x === "number" && isFinite(x) ? Math.trunc(x) : 0);
  const cleanStats = (s) => {
    const d = P.defaultStats(), o = Object.assign({}, d, s || {});
    const lw = int(o.lastWinDay);
    return { played: int(o.played), won: int(o.won), streak: int(o.streak), maxStreak: int(o.maxStreak), lastWinDay: o.lastWinDay == null || lw < -1000 ? -999999 : lw,
      firstTry: int(o.firstTry), dist: Array.from({ length: 9 }, (_, i) => int((o.dist || [])[i])) };
  };

  /** 1º chutes válidos: {w: 5 letras A-Z, n: 1..7, d: AAAA-MM-DD}. */
  const cleanGuesses = (l) => (Array.isArray(l) ? l : []).filter((x) => x && typeof x.w === "string" && /^[A-Z]{5}$/.test(x.w) && int(x.n) >= 1 && int(x.n) <= 7 && /^\d{4}-\d{2}-\d{2}$/.test(String(x.d)))
    .map((x) => ({ w: x.w, n: int(x.n), d: String(x.d) }));

  P.syncExport = () => {
    const data = {
      v: 1,
      stats: Object.fromEntries(MODES.map((m) => [m, cleanStats(P.stats(m))])),
      n: Object.fromEntries(COUNTERS.map((c) => [c, int(P.store.get(siteKey(c), 0))])),
      activity: P.store.get("activity", {}), termoDays: P.store.get("termo-days", {}),
      results: P.store.get("results", {}), history: P.store.get("history", {}),
    };
    const fg = cleanGuesses(P.store.get("first-guesses", []));
    if (fg.length) data.firstGuesses = fg;
    return "PV1-" + toB64(JSON.stringify(data));
  };

  // ---------- sincronizar com código curto ----------
  // O código longo vai em partes por um tópico temporário do ntfy (limite de 4 KB por mensagem; expira em poucas horas).
  const SYNC_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ", SYNC_CHUNK = 3000;
  const syncTopic = (code) => "palavreiro-sync-" + code.toLowerCase();
  /** Só as letras e números do código curto digitado, ou null se não for um. */
  P.syncShort = (t) => { const c = String(t || "").trim().toUpperCase().replace(/^.*[?&]SYNC=/, "").replace(/[^0-9A-Z]/g, ""); return c.length === 6 && [...c].every((x) => SYNC_ALPHABET.includes(x)) ? c : null; };
  P.syncLink = (code) => "https://dmwnezes.com/termo/?sync=" + code;
  /** Publica o progresso. Devolve o código curto, ou null se nenhum servidor aceitou. */
  P.syncSend = async () => {
    const full = P.syncExport(), r = new Uint32Array(6); crypto.getRandomValues(r);
    const code = Array.from(r, (x) => SYNC_ALPHABET[x % SYNC_ALPHABET.length]).join("");
    const n = Math.ceil(full.length / SYNC_CHUNK);
    for (let i = 0; i < n; i++) {
      if (!(await P.relay.post(syncTopic(code), JSON.stringify({ i, n, d: full.slice(i * SYNC_CHUNK, (i + 1) * SYNC_CHUNK) })))) return null;
    }
    return code;
  };
  /** Baixa e mescla o progresso do código curto. Devolve true se deu certo. */
  P.syncReceive = async (code) => {
    const parts = {}; let n = 0;
    (await P.relay.history(syncTopic(code))).forEach((m) => { try { const o = JSON.parse(m); if (Number.isInteger(o.i) && Number.isInteger(o.n) && typeof o.d === "string") { parts[o.i] = o.d; n = o.n; } } catch (_) {} });
    if (!n || Object.keys(parts).length < n) return false;
    return P.syncImport(Array.from({ length: n }, (_, i) => parts[i]).join(""));
  };

  // ---------- retrospectiva do ano ----------
  const RETRO_NAMES = { termo: "Termo", dueto: "Dueto", quarteto: "Quarteto", conn: "Conexões", conexoes: "Conexões", caca: "Caça-Palavras", ws: "Caça-Palavras", cruzada: "Palavra Cruzada", silhueta: "Silhueta", maismenos: "Mais ou Menos", minado: "Campo Minado", caminhos: "Caminhos", fatomito: "Fato ou Mito" };
  const MONTHS = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"];
  /** Números do ano a partir do progresso salvo. Mesmo cálculo no app (game/Retro.kt). */
  P.retro = (year = new Date().getFullYear()) => {
    const pre = year + "-", act = Object.entries(P.store.get("activity", {})).filter(([d, v]) => d.startsWith(pre) && v > 0).sort();
    const months = Array(12).fill(0); let run = 0, best = 0, prev = null;
    act.forEach(([d, v]) => {
      months[+d.slice(5, 7) - 1] += v;
      const t = Date.UTC(+d.slice(0, 4), +d.slice(5, 7) - 1, +d.slice(8, 10)) / 86400000;
      run = prev !== null && t === prev + 1 ? run + 1 : 1; best = Math.max(best, run); prev = t;
    });
    const td = Object.entries(P.store.get("termo-days", {})).filter(([d]) => d.startsWith(pre));
    const wins = td.filter(([, v]) => v === "w").length;
    const games = {}; Object.keys(P.store.get("results", {})).forEach((k) => { const [g, d] = k.split("|"); if (d && d.startsWith(pre) && RETRO_NAMES[g]) games[RETRO_NAMES[g]] = (games[RETRO_NAMES[g]] || 0) + 1; });
    const top = (o) => Object.entries(o).sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0];
    const guesses = {}; (P.store.get("first-guesses", []) || []).forEach((x) => { if (x && typeof x.w === "string" && String(x.d).startsWith(pre)) guesses[x.w] = (guesses[x.w] || 0) + 1; });
    const m = months.indexOf(Math.max(...months)), g = top(games), w = top(guesses);
    return { year, days: act.length, games: act.reduce((a, [, v]) => a + v, 0), bestRun: best, month: act.length ? MONTHS[m] : null, termoPlayed: td.length, termoWon: wins, topGame: g ? g[0] : null, topGuess: w ? w[0] : null };
  };

  /** Mescla o código no progresso local. Devolve true se deu certo. */
  P.syncImport = (code) => {
    let d;
    try {
      const s = String(code || "").replace(/\s+/g, "");
      if (!s.startsWith("PV1-")) return false;
      d = JSON.parse(fromB64(s.slice(4)));
    } catch (_) { return false; }
    if (!d || typeof d !== "object" || d.v !== 1) return false;
    const obj = (x) => (x && typeof x === "object" && !Array.isArray(x) ? x : {});
    Object.entries(obj(d.stats)).forEach(([m, s]) => {
      if (!MODES.includes(m)) return;
      const inc = cleanStats(s);
      if (inc.played > P.stats(m).played) P.store.set("stats-" + m, inc);
    });
    Object.entries(obj(d.n)).forEach(([c, v]) => {
      if (!COUNTERS.includes(c)) return;
      const k = siteKey(c), cur = int(P.store.get(k, 0)), inc = int(v);
      if (LOWER.includes(c)) { if (inc > 0 && (cur <= 0 || inc < cur)) P.store.set(k, inc); }
      else if (inc > cur) P.store.set(k, inc);
    });
    const act = P.store.get("activity", {});
    Object.entries(obj(d.activity)).forEach(([day, v]) => { if (int(v) > (act[day] || 0)) act[day] = int(v); });
    P.store.set("activity", act);
    const union = (key, inc, deep) => {
      const cur = P.store.get(key, {});
      Object.entries(obj(inc)).forEach(([k, v]) => {
        if (deep) cur[k] = Object.assign({}, obj(v), obj(cur[k]));
        else if (!(k in cur) && (v === "w" || v === "l")) cur[k] = v;
      });
      P.store.set(key, cur);
    };
    if (Array.isArray(d.firstGuesses)) {
      // União por (d, w, n), em ordem de data, até 500 (ficam os mais recentes).
      const cur = cleanGuesses(P.store.get("first-guesses", [])), seen = new Set(cur.map((x) => x.d + "|" + x.w + "|" + x.n));
      cleanGuesses(d.firstGuesses).forEach((x) => { const k = x.d + "|" + x.w + "|" + x.n; if (!seen.has(k)) { seen.add(k); cur.push(x); } });
      const sorted = cur.map((x, i) => [x, i]).sort((a, b) => (a[0].d < b[0].d ? -1 : a[0].d > b[0].d ? 1 : a[1] - b[1])).map((p) => p[0]);
      P.store.set("first-guesses", sorted.slice(-500));
    }
    union("termo-days", d.termoDays); union("results", d.results); union("history", d.history, true);
    return true;
  };
})();
