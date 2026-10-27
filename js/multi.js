// Jogar com amigo (site): partida ao vivo pelo ntfy.sh, compatível com o app Android.
// Link: ?mp=SALA-MODO-SEED · tópico "palavreiro-mp-" + SALA · mensagens JSON (hello, start, row, end, again, bye;
// Anagrama e Qual é a Palavra?: solve, skip; Caça-Palavras: grid, found, timeup; Velha e Damas: move, resign; reações: react).
// Caça-Palavras, Jogo da Velha e Damas ficam em js/multi-games.js; Xeque/Xadrez (k) em js/multi-xadrez.js (registrados em P.mp.kinds).
(function () {
  "use strict";
  // Testes: localStorage "pv-ntfy" (ex.: "http://127.0.0.1:8799/") troca o servidor do ntfy.sh.
  const NTFY = (() => { try { const v = localStorage.getItem("pv-ntfy"); if (v && /^https?:\/\//.test(v)) return v.endsWith("/") ? v : v + "/"; } catch (_) {} return "https://ntfy.sh/"; })();
  // pts = jogo de pontos (rodadas 5/10/15); os outros são séries (1 partida, melhor de 3, melhor de 5).
  const MODES = {
    t: { key: "termo", name: "Termo", pill: "Termo", inv: "um Termo", boards: 1 },
    d: { key: "dueto", name: "Dueto", pill: "Dueto", inv: "um Dueto", boards: 2 },
    q: { key: "quarteto", name: "Quarteto", pill: "Quarteto", inv: "um Quarteto", boards: 4 },
    a: { key: "anagrama", name: "Anagrama", pill: "Anagrama", inv: "um Anagrama", boards: 0, pts: true },
    c: { key: "caca", name: "Caça-Palavras", pill: "Caça", inv: "um Caça-Palavras", boards: 0 },
    p: { key: "palavra", name: "Qual é a Palavra?", pill: "Palavra?", inv: "um Qual é a Palavra?", boards: 0, pts: true },
    v: { key: "velha", name: "Jogo da Velha", pill: "Velha", inv: "um Jogo da Velha", boards: 0 },
    x: { key: "damas", name: "Damas", pill: "Damas", inv: "uma partida de Damas", boards: 0 },
    k: { key: "xadrez", name: "Xeque", pill: "Xeque", inv: "uma partida de Xeque", boards: 0, rd: 1 },
    f: { key: "forca", name: "Forca", pill: "Forca", inv: "uma Forca", boards: 0 },
    s: { key: "adedonha", name: "Adedonha", pill: "Adedonha", inv: "uma Adedonha", boards: 0, pts: true, rc: [3, 5, 7], rd: 5 },
    g: { key: "trivia", name: "Trivia", pill: "Trivia", inv: "uma Trivia", boards: 0, pts: true },
    n: { key: "naval", name: "Batalha Naval", pill: "Naval", inv: "uma Batalha Naval", boards: 0 },
  };
  /** Seções da tela de criar partida (e a ordem da grade do "Próximo jogo"). */
  const SECTIONS = [["Palavras", ["t", "d", "q", "a", "c", "p", "f", "s"]], ["Conhecimento", ["g"]], ["Tabuleiro", ["v", "x", "k", "n"]]];
  const ORDER = SECTIONS.flatMap((x) => x[1]);
  const DESC = {
    t: "Uma palavra, quem acertar primeiro", d: "Duas palavras ao mesmo tempo", q: "Quatro palavras, 9 tentativas",
    a: "Desembaralhe as letras antes", c: "A mesma grade para os dois", p: "Descubra a palavra pela definição",
    f: "Um escolhe a palavra, o outro adivinha", s: "Uma letra, várias categorias", g: "Perguntas de 6 temas",
    v: "X contra O, o clássico", x: "Regras brasileiras", k: "Xadrez com regras completas", n: "Afunde a frota do amigo",
  };
  // ---------- rodadas: série (1/3/5) ou pontos (5/10/15; Adedonha 3/5/7) ----------
  const RCHOICES = (m) => MODES[m].rc || (MODES[m].pts ? [5, 10, 15] : [1, 3, 5]);
  const RDEF = (m) => MODES[m].rd || (MODES[m].pts ? 10 : 3);
  const validR = (m, r) => (RCHOICES(m).includes(Number(r)) ? Number(r) : null);
  const roundsPref = (m) => validR(m, P.store.get("mp-rounds-" + m, null)) || RDEF(m);
  const rLabel = (m, n) => (MODES[m].pts ? `${n} rodadas` : n === 1 ? "1 partida" : `Melhor de ${n}`);
  /** Sufixo do convite: "melhor de 3", "1 partida", "15 rodadas". */
  const rSuffix = (m, n) => (MODES[m].pts ? `${n} rodadas` : n === 1 ? "1 partida" : `melhor de ${n}`);
  const seriesTxt = (n) => (n === 1 ? "Uma partida só: quem vencer leva o troféu 🏆" : `Melhor de ${n}: quem vencer ${Math.ceil(n / 2)} rodadas leva o troféu 🏆`);
  /** Texto de cada modo na tela de criar/entrar. */
  function leadOf(m, host, n) {
    n = n || RDEF(m);
    if (m === "a") return `${n} rodadas com as mesmas letras embaralhadas. Quem achar a palavra primeiro leva o ponto.`;
    if (m === "p") return `${n} definições iguais para os dois. Quem descobrir a palavra primeiro leva o ponto. Dicas aos 20 e aos 40 segundos.`;
    if (m === "c") return `A mesma grade para os dois, 5 minutos por rodada. Cada palavra é de quem achar primeiro. ${seriesTxt(n)}`;
    if (m === "v") return `X começa e os símbolos trocam a cada rodada. Deu velha? Não conta. ${seriesTxt(n)}`;
    if (m === "x") return `Regras brasileiras: captura obrigatória, lei da maioria e dama voadora. ${seriesTxt(n)}`;
    if (m === "k") return `Regras completas: roque, en passant e promoção. Brancas começam e as cores trocam a cada rodada. Empate não conta. ${seriesTxt(n)}`;
    if (m === "n") return `Cada um sorteia a própria frota e vocês atiram um de cada vez. Quem afundar os 5 navios do outro primeiro vence. ${seriesTxt(n)}`;
    if (m === "s") return `${n} rodadas: uma letra sorteada e 6 categorias. Preencha tudo e dê STOP! Resposta só sua vale 10, igual à do amigo vale 5.`;
    if (m === "g") return `${n} perguntas de 6 temas, as mesmas para os dois, 15 segundos cada. Quem acertar mais vence.`;
    if (m === "f") return `Um escolhe a palavra e o tema, o outro adivinha letra por letra. 6 erros e a forca vence. Os papéis trocam a cada rodada. ${seriesTxt(n)}`;
    return `${host ? "Vocês recebem as mesmas palavras. Quem acertar primeiro ganha." : "Partida ao vivo: quem acertar primeiro ganha."} ${seriesTxt(n)}`;
  }
  const ABC = "abcdefghijklmnopqrstuvwxyz0123456789";
  const randStr = (n) => { const b = new Uint32Array(n); crypto.getRandomValues(b); return Array.from(b, (x) => ABC[x % 36]).join(""); };
  const newSeed = () => { const b = new Uint32Array(1); crypto.getRandomValues(b); return b[0] % 2147483647; };
  const clockFmt = (ms) => { const s = Math.max(0, Math.round((ms || 0) / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; };
  const triesTxt = (n) => `${n} ${n === 1 ? "tentativa" : "tentativas"}`;
  const GONE_TXT = "Esse jogo saiu do Palavreiro. Crie uma partida nova!";

  P.mp = {
    MODES, ORDER, SECTIONS,
    kinds: {}, // jogos registrados por js/multi-games.js: {round, msg, bye, sheet, story}
    link: (room, m, seed) => location.origin + location.pathname.replace(/index\.html$/, "") + `?mp=${room}-${m}-${seed.toString(36)}`,
    /** "sala-modo-seed" → {room, m, seed} ou null. */
    parse(code) {
      const x = /^([a-z0-9]{12})-([tdqacpvxsgfkn])-([0-9a-z]{1,7})$/.exec(String(code || "").trim().toLowerCase());
      if (!x) return null;
      const seed = parseInt(x[3], 36);
      return seed >= 0 && seed <= 2147483646 ? { room: x[1], m: x[2], seed } : null;
    },
    /** Link de um jogo que saiu (Bomba-Relógio, "b"). */
    gone: (code) => /^[a-z0-9]{12}-b-[0-9a-z]{1,7}$/.test(String(code || "").trim().toLowerCase()),
    GONE_TXT,
  };

  let S = null; // sessão atual

  // ---------- sala guardada por 10 minutos ----------
  // localStorage "pv-mp-session" = {code, host, id, name, at}: o anfitrião sai para mandar o link (WhatsApp)
  // e o navegador pode descartar a aba; ao voltar, o site reabre a mesma sala com o mesmo id de jogador.
  const KEEP_MS = 600000;
  const codeOf = (s) => `${s.room}-${s.m}-${s.seed.toString(36)}`;
  function remember(s) {
    s.at = s.at || Date.now();
    P.store.set("mp-session", { code: codeOf(s), host: !!s.me.host, id: s.me.id, name: s.me.name, at: s.at, r: s.rounds });
  }
  function forget() { try { localStorage.removeItem("pv-mp-session"); } catch (_) {} }
  /** Sessão guardada ainda válida: {room, m, seed, host, id, name, at} ou null (a vencida é apagada). */
  function saved() {
    const v = P.store.get("mp-session", null);
    const info = v && typeof v === "object" ? P.mp.parse(v.code) : null;
    const ok = info && typeof v.id === "string" && /^[a-z0-9]{8}$/.test(v.id) && typeof v.name === "string" && v.name.length >= 2
      && typeof v.at === "number" && Date.now() < v.at + KEEP_MS && v.at <= Date.now() + 60000;
    if (!ok) { if (v != null) forget(); return null; }
    return Object.assign(info, { host: v.host === true, id: v.id, name: v.name.slice(0, 16), at: v.at, r: validR(info.m, v.r) || RDEF(info.m) });
  }
  P.mp.saved = saved;

  /** Encerra a sessão. Sair de propósito (padrão) também apaga a sala guardada; keep = true mantém. */
  function leave(sendBye = true, keep = false) {
    if (!S) return;
    if (!keep) forget();
    const s = S; S = null;
    s.closed = true;
    clearTimeout(s.retry); clearInterval(s.countTimer); clearTimeout(s.sheetTimer); clearInterval(s.ttlTimer); stopGame(s);
    closePicker();
    if (s.es) s.es.close();
    if (sendBye && s.joined) { try { fetch(NTFY + s.topic, { method: "POST", body: JSON.stringify({ t: "bye", id: s.me.id }), keepalive: true }).catch(() => {}); } catch (_) {} }
    P.$("#mp-net")?.remove();
    P.fx.muted = false;
  }
  P.mp.leave = leave;
  P.mp.current = () => S;
  const waitingView = (s) => s.view === "wait" || s.view === "joinwait";
  // Trocar de aba/app enquanto espera não é sair da sala: sem "bye". Na partida, sai como antes.
  window.addEventListener("pagehide", () => { if (S && !waitingView(S)) leave(true, true); });
  /** Voltou para a aba: relê o histórico e reabre a conexão ao vivo se ela caiu. */
  async function refresh() {
    const s = S;
    if (!s || s.closed || !s.loaded || document.visibilityState !== "visible") return;
    if (!s.es || s.es.readyState === 2) { clearTimeout(s.retry); listen(s); }
    try {
      const r = await fetch(`${NTFY}${s.topic}/json?poll=1&since=all`);
      if (!r.ok || s.closed) return;
      (await r.text()).split("\n").forEach((l) => { if (!l.trim()) return; try { ingest(s, JSON.parse(l), false); } catch (_) {} });
      afterHistory(s);
    } catch (_) {}
  }
  document.addEventListener("visibilitychange", refresh);
  window.addEventListener("pageshow", (e) => { if (e.persisted) refresh(); });

  // ---------- transporte ----------
  function publish(msg) {
    const s = S; if (!s) return Promise.resolve();
    const body = JSON.stringify(msg);
    // Fila: as mensagens chegam no tópico na mesma ordem em que foram enviadas.
    s.queue = s.queue.then(async () => {
      for (let i = 0; i < 4; i++) {
        try { const r = await fetch(NTFY + s.topic, { method: "POST", body }); if (r.ok) return; } catch (_) {}
        if (s.closed) return;
        net(true); await P.sleep(1500);
      }
    });
    return s.queue;
  }

  function net(down) {
    let el = P.$("#mp-net");
    if (down && !el && S) document.body.appendChild(P.h(`<div id="mp-net" class="mp-net" role="status">Reconectando…</div>`));
    else if (!down && el) el.remove();
  }

  function ingest(s, ev, live) {
    if (!ev || ev.event !== "message" || !ev.id || s.seen.has(ev.id)) return;
    s.seen.add(ev.id); s.lastId = ev.id;
    let m; try { m = JSON.parse(ev.message); } catch (_) { return; }
    if (!m || typeof m !== "object" || !m.t) return;
    handle(s, m, live);
  }

  /** Lê o histórico do tópico e depois fica ouvindo ao vivo (SSE). */
  async function connect(s) {
    try {
      const r = await fetch(`${NTFY}${s.topic}/json?poll=1&since=${s.lastId || "all"}`);
      if (!r.ok) throw new Error("poll");
      const text = await r.text();
      if (s.closed) return;
      text.split("\n").forEach((l) => { if (!l.trim()) return; try { ingest(s, JSON.parse(l), false); } catch (_) {} });
      s.loaded = true; afterHistory(s); s.onLoaded && s.onLoaded();
      net(false);
    } catch (_) {
      if (s.closed) return;
      net(true); s.retry = setTimeout(() => connect(s), 2000); return;
    }
    listen(s);
  }
  /** Depois de ler o histórico inteiro: manda o que ficou pendente (hello próprio que não chegou, start do anfitrião). */
  function afterHistory(s) {
    if (s.closed || !s.joined || s.full) return;
    if (s.resumed && !s.sawMyHello && !s.helloResent) {
      s.helloResent = true;
      publish(helloMsg(s));
    }
    if (s.me.host && s.pendingStart && s.guestHello && !s.started && !s.startSent) sendStart(s);
  }
  function sendStart(s) {
    s.startSent = true; s.pendingStart = false;
    waitingJoined(s);
    publish({ t: "start", at: Date.now(), g: s.guestHello.id });
  }

  function listen(s) {
    if (s.closed) return;
    if (s.es) s.es.close();
    const es = (s.es = new EventSource(`${NTFY}${s.topic}/sse?since=${s.lastId || "all"}`));
    // O ntfy.sh leva ~1 s para guardar cada mensagem no histórico: relê o histórico logo depois de conectar
    // para não perder nada que chegou entre a leitura inicial e a conexão ao vivo (repetidas são ignoradas).
    es.onopen = () => {
      net(false);
      setTimeout(async () => {
        if (s.closed || s.es !== es) return;
        try {
          const r = await fetch(`${NTFY}${s.topic}/json?poll=1&since=all`);
          if (!r.ok || s.closed) return;
          (await r.text()).split("\n").forEach((l) => { if (!l.trim()) return; try { ingest(s, JSON.parse(l), true); } catch (_) {} });
        } catch (_) {}
      }, 2500);
    };
    es.onmessage = (e) => { try { ingest(s, JSON.parse(e.data), true); } catch (_) {} };
    es.onerror = () => {
      es.close(); if (s.closed || s.es !== es) return;
      net(true); s.retry = setTimeout(() => listen(s), 2000);
    };
  }

  function session({ room, m, seed, host, name, root, rounds }) {
    leave(false, true);
    S = { room, m, seed, mode: MODES[m], topic: "palavreiro-mp-" + room, me: { id: randStr(8), name, host }, root,
      hostHello: null, guestHello: null, started: false, startSent: false, joined: false, full: false, oppLeft: false,
      seen: new Set(), lastId: null, es: null, closed: false, queue: Promise.resolve(), round: null, view: null,
      series: { me: 0, opp: 0 }, rounds: validR(m, rounds) || RDEF(m),
      rn: -1, // rodada da sala: 0 no start, +1 a cada again (Caça, Velha e Damas usam)
      grids: {}, themes: [] };
    return S;
  }
  /** hello: o anfitrião leva o número de rodadas ("r"). */
  const helloMsg = (s) => (s.me.host ? { t: "hello", id: s.me.id, n: s.me.name, host: true, r: s.rounds } : { t: "hello", id: s.me.id, n: s.me.name, host: false });
  const opp = (s) => (s.me.host ? s.guestHello : s.hostHello);

  // ---------- mensagens ----------
  /** Mensagens dos jogos novos → modo que as trata. */
  const KIND_OF = { sround: "s", stop: "s", ans: "s", vote: "s", pick: "g", secret: "f", guess: "f", reveal: "f", try: "f", tryres: "f", word: "f", ready: "n", shot: "n", res: "n", fleet: "n" };
  function handle(s, m, live) {
    if (m.t === "hello" && typeof m.id === "string") {
      const who = { id: m.id, name: String(m.n || "Amigo").slice(0, 16) };
      if (m.id === s.me.id) s.sawMyHello = true;
      if (m.host === true) {
        if (!s.hostHello) { s.hostHello = who; if (!s.me.host) s.rounds = validR(s.m, m.r) || RDEF(s.m); }
      }
      else if (!s.guestHello) s.guestHello = who;
      if (s.me.host && s.guestHello && s.guestHello.id === m.id && !s.started && !s.startSent) {
        // Lendo o histórico (sala retomada): espera o fim, porque o start pode já estar lá.
        if (!s.loaded && !live) s.pendingStart = true;
        else sendStart(s);
      }
      if (!s.me.host && s.joined && s.guestHello && s.guestHello.id !== s.me.id) markFull(s);
      if (s.view === "join") joinText(s);
      if (s.view === "joinwait" && m.host === true) {
        const t = P.$("[data-waittxt]", s.root); if (t) t.innerHTML = `Esperando <b>${P.esc(s.hostHello.name)}</b> começar…`;
        const bd = P.$(".mp-mode-badge", s.root); if (bd) bd.textContent = `${s.mode.name} · ${rSuffix(s.m, s.rounds)}`;
      }
    } else if (m.t === "start") {
      s.started = true;
      if (!s.me.host && (!s.joined || m.g !== s.me.id)) { if (s.joined) markFull(s); else if (s.view === "join") joinText(s); return; }
      s.series = { me: 0, opp: 0 };
      forget(); clearInterval(s.ttlTimer); // a partida começou: a sala guardada não serve mais
      beginRound(s, +m.at || Date.now(), live);
    } else if (m.t === "again") {
      if (!s.round) return;
      const seed = parseInt(String(m.seed || ""), 36);
      if (!(seed >= 0 && seed <= 2147483646)) return;
      s.seed = seed;
      // "m" = modo da próxima partida (sem "m", continua o mesmo). Trocar de modo zera a série.
      const nm = typeof m.m === "string" && MODES[m.m] ? m.m : s.m, switched = nm !== s.m;
      if (switched) { s.m = nm; s.mode = MODES[nm]; }
      // "r" = rodadas do próximo jogo; sem "r": padrão do modo novo ou o mesmo de antes.
      const nr = validR(nm, m.r) || (switched ? RDEF(nm) : s.rounds), rChanged = nr !== s.rounds;
      s.rounds = nr;
      // "s":1 = série nova (o placar volta a 0 × 0). Série já decidida também recomeça.
      if (switched || rChanged || m.s === 1 || seriesOver(s)) s.series = { me: 0, opp: 0 };
      beginRound(s, +m.at || Date.now(), live);
      if (switched && !s.me.host) P.toast(`Novo jogo: ${s.mode.name}`, 2400);
    } else if (m.t === "solve" || m.t === "skip") {
      if (s.round && (s.round.kind === "a" || s.round.kind === "p")) anaMsg(s, m);
    } else if (m.t === "grid" || m.t === "found" || m.t === "timeup" || m.t === "move" || m.t === "resign") {
      const K = P.mp.kinds[m.t === "grid" || m.t === "found" || m.t === "timeup" ? "c" : s.round && s.round.kind];
      if (K && K.msg) K.msg(s, m, live);
    } else if (KIND_OF[m.t]) {
      // Adedonha, Trivia e Forca (js/multi-*.js): só valem na rodada do mesmo jogo.
      const k = KIND_OF[m.t], K = P.mp.kinds[k];
      if (s.round && s.round.kind === k && K && K.msg) K.msg(s, m, live);
    } else if (m.t === "suggest") {
      // Convidado sugeriu outro jogo: aviso no topo da folha de fim do anfitrião.
      const o = opp(s);
      if (!s.me.host || !o || m.id !== o.id || typeof m.m !== "string" || !MODES[m.m] || !s.round) return;
      s.suggest = { m: m.m, rn: s.rn };
      suggBanner(s);
      if (live) { P.vibrate(30); P.tone([[660, 60], [880, 80]], 0.06); }
    } else if (m.t === "row") {
      const r = s.round, o = opp(s);
      if (!r || r.kind || !o || m.id !== o.id || typeof m.m !== "string") return;
      const idx = +m.r;
      if (!(idx >= 0 && idx < 9) || r.opp.rows[idx]) return;
      r.opp.rows[idx] = m.m.split("|"); r.opp.fresh = idx;
      drawStrip(s);
    } else if (m.t === "end") {
      const r = s.round, o = opp(s);
      if (!r || r.kind || r.ends.some((e) => e.id === m.id) || (m.id !== s.me.id && (!o || m.id !== o.id))) return;
      r.ends.push({ id: m.id, won: m.won === true, tries: +m.tries || 0, ms: +m.ms || 0 });
      decide(s); drawStrip(s);
    } else if (m.t === "react") {
      // Só reações frescas do adversário (o histórico do tópico traz as velhas); a minha já apareceu na hora.
      const o = opp(s);
      if (!s.round || s.view !== "game" || !o || m.id === s.me.id || m.id !== o.id || !EMOJIS.includes(m.e)) return;
      if (!(Math.abs(Date.now() - Number(m.at)) < REACT_FRESH)) return;
      bubble(s, m.e, o.name, false);
      P.vibrate(40);
    } else if (m.t === "bye") {
      const o = opp(s);
      if (!o || m.id !== o.id) return;
      s.oppLeft = true;
      const K = s.round && P.mp.kinds[s.round.kind];
      if (K && K.bye) K.bye(s, s.round);
      else if (s.round && (s.round.kind === "a" || s.round.kind === "p")) anaDraw(s, s.round);
      else if (s.round) { decide(s); drawStrip(s); }
      else if (s.view === "joinwait") P.toast(`${o.name} saiu da partida`, 2500);
    }
  }

  function markFull(s) {
    if (s.full) return;
    s.full = true; s.joined = false;
    forget();
    const body = P.$("[data-mpbody]", s.root);
    if (body) body.innerHTML = `<div class="mp-card center"><div class="mp-big">🚪</div><h2>A partida já está cheia</h2><p class="muted">Peça para seu amigo criar uma nova partida.</p><button class="pill wide" data-home>Voltar ao início</button></div>`;
    const b = P.$("[data-home]", s.root); if (b) b.onclick = () => P.go("");
    s.view = "full";
  }

  // ---------- telas ----------
  const shell = (root, title) => {
    root.innerHTML = P.topbar(title) + `<div class="mp" data-mpbody></div>`;
    return P.$("[data-mpbody]", root);
  };
  const nameField = () => `<label class="mp-field"><span>Seu nome</span><input class="mp-input" data-name maxlength="16" autocomplete="nickname" placeholder="Como seu amigo vai te ver" value="${P.esc(P.store.get("mp-name", "") || "")}" /></label>`;
  const readName = (root) => {
    const v = P.$("[data-name]", root).value.trim().replace(/\s+/g, " ");
    if (v.length < 2 || v.length > 16) { P.fx.invalid(); P.toast("Seu nome precisa ter de 2 a 16 letras"); return null; }
    P.store.set("mp-name", v); return v;
  };
  const duoIcon = `<div class="mp-duo" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>`;

  // ---------- mini ícones dos jogos (no estilo dos ícones da tela inicial) ----------
  const fl = (c) => `style="fill:var(--${c})"`;
  const sk = (c, w) => `style="stroke:var(--${c});stroke-width:${w};stroke-linecap:round;stroke-linejoin:round;fill:none"`;
  const grid3 = (x0, y0, cell, gap, cols) => cols.map((c, i) => `<rect x="${x0 + (i % 3) * (cell + gap)}" y="${y0 + Math.floor(i / 3) * (cell + gap)}" width="${cell}" height="${cell}" rx="${cell * 0.24}" ${fl(c)}/>`).join("");
  const T1 = ["absent", "present", "absent", "absent", "correct", "present", "correct", "correct", "correct"];
  const T2 = ["present", "absent", "absent", "absent", "absent", "correct", "absent", "correct", "absent"];
  const T3 = ["absent", "absent", "present", "correct", "absent", "absent", "surface-high", "surface-high", "surface-high"];
  const T4 = ["correct", "absent", "absent", "absent", "present", "absent", "correct", "correct", "correct"];
  const tx = (x, y, t, size, c, w = 800) => `<text x="${x}" y="${y}" text-anchor="middle" dominant-baseline="central" style="fill:var(--${c});font:${w} ${size}px Outfit,sans-serif">${t}</text>`;
  const wedge = (i, cx, cy, rad) => {
    const a0 = ((i * 60 - 90) * Math.PI) / 180, a1 = (((i + 1) * 60 - 90) * Math.PI) / 180, f = (n) => n.toFixed(2);
    return `M${cx} ${cy}L${f(cx + rad * Math.cos(a0))} ${f(cy + rad * Math.sin(a0))}A${rad} ${rad} 0 0 1 ${f(cx + rad * Math.cos(a1))} ${f(cy + rad * Math.sin(a1))}Z`;
  };
  const TRIVIA_COLS = ["#5FB873", "#E6C14F", "#6FA8E8", "#F0954A", "#EE7FB5", "#B48CF0"];
  const ICON_SVG = {
    t: grid3(5, 5, 10.5, 2.3, T1),
    d: grid3(1, 12, 6, 1.4, T1) + grid3(23.2, 12, 6, 1.4, T2),
    q: grid3(3, 3, 5, 1.3, T1) + grid3(24.4, 3, 5, 1.3, T2) + grid3(3, 24.4, 5, 1.3, T3) + grid3(24.4, 24.4, 5, 1.3, T4),
    a: `<g transform="rotate(-9 11 23)"><rect x="2" y="14" width="16" height="17" rx="4.5" ${fl("surface-high")}/>${tx(10, 22.8, "M", 11, "text")}</g>
        <rect x="15" y="9" width="16" height="17" rx="4.5" ${fl("present")}/>${tx(23, 17.8, "A", 11, "dark-text")}
        <g transform="rotate(10 36 25)"><rect x="27" y="16" width="16" height="17" rx="4.5" ${fl("surface-high")}/>${tx(35, 24.8, "R", 11, "text")}</g>
        <path d="M10 37 Q22 44 34 37" ${sk("accent", 2.4)}/><path d="M30 34.5 L34.5 37 L31.5 41" ${sk("accent", 2.4)}/>`,
    c: `<rect x="3" y="15" width="40" height="11" rx="5.5" transform="rotate(38 8 20)" style="fill:var(--present);opacity:.55"/>
        ${["CAS", "OLE", "PAJ"].map((r, y) => [...r].map((ch, x) => tx(9 + x * 13, 9 + y * 13, ch, 10.5, "text", 600)).join("")).join("")}`,
    p: `<rect x="7" y="7" width="30" height="4.5" rx="2.25" style="fill:var(--muted);opacity:.6"/><rect x="11" y="15" width="22" height="4.5" rx="2.25" style="fill:var(--muted);opacity:.6"/>
        ${["surface-high", "present", "surface-high", "surface-high"].map((c, i) => `<rect x="${3.5 + i * 9.6}" y="26" width="8" height="10" rx="2.4" ${fl(c)}/>`).join("")}`,
    f: `<path d="M8 40 H26 M14 40 V5 H31 V10 M14 13 L21 5" ${sk("muted", 3)}/><circle cx="31" cy="15.5" r="5" ${fl("accent")}/>
        <path d="M31 21 V31 M31 24 L26.5 28.5 M31 24 L35.5 28.5 M31 31 L27.5 37 M31 31 L34.5 37" ${sk("accent", 2.6)}/>`,
    s: `<rect x="3" y="7" width="20" height="22" rx="6" ${fl("present")}/>${tx(13, 18.6, "A", 15, "dark-text")}
        ${[0, 1, 2].map((i) => `<rect x="27" y="${8 + i * 8}" width="${[14, 10, 12][i]}" height="4.5" rx="2.25" style="fill:var(--muted);opacity:.6"/>`).join("")}
        <rect x="7" y="33" width="32" height="8" rx="4" ${fl("red")}/>${tx(23, 37.2, "STOP", 6.5, "dark-text")}`,
    g: TRIVIA_COLS.map((c, i) => `<path d="${wedge(i, 22, 22, 19)}" style="fill:${c}"/>`).join("") + `<circle cx="22" cy="22" r="8.5" ${fl("surface")}/>${tx(22, 22.6, "?", 12, "text")}`,
    v: `<path d="M16 5 V39 M28 5 V39 M5 16 H39 M5 28 H39" ${sk("outline", 2.6)}/>
        <path d="M7.5 7.5 L13.5 13.5 M13.5 7.5 L7.5 13.5 M19 19 L25 25 M25 19 L19 25" ${sk("correct", 2.8)}/>
        <circle cx="34" cy="10.5" r="3.6" ${sk("present", 2.6)}/><circle cx="10.5" cy="34" r="3.6" ${sk("present", 2.6)}/>`,
    x: `<rect x="3" y="3" width="19" height="19" rx="4" ${fl("surface-high")}/><rect x="22" y="22" width="19" height="19" rx="4" ${fl("surface-high")}/>
        <rect x="22" y="3" width="19" height="19" rx="4" style="fill:var(--bg-bottom)"/><rect x="3" y="22" width="19" height="19" rx="4" style="fill:var(--bg-bottom)"/>
        <circle cx="31.5" cy="12.5" r="7" style="fill:#F4F1FF"/><circle cx="31.5" cy="12.5" r="4.3" style="fill:none;stroke:#C9C2EA;stroke-width:1.4"/>
        <circle cx="12.5" cy="31.5" r="7" style="fill:#14102C;stroke:var(--outline);stroke-width:1.4"/>${tx(12.5, 31.8, "♛", 8, "present", 600)}`,
    k: `<rect x="3" y="3" width="38" height="38" rx="9" ${fl("surface-high")}/><rect x="3" y="3" width="19" height="19" rx="9" style="fill:var(--bg-bottom)"/><rect x="22" y="22" width="19" height="19" rx="9" style="fill:var(--bg-bottom)"/>
        <text x="22" y="24" text-anchor="middle" dominant-baseline="central" style="fill:#F4F1FF;stroke:#14102C;stroke-width:1.2;paint-order:stroke;font:400 30px 'DejaVu Sans',sans-serif">♞&#xFE0E;</text>`,
    n: `${[0, 1, 2].map((r) => [0, 1, 2].map((c) => `<rect x="${3 + c * 13.5}" y="${3 + r * 13.5}" width="11" height="11" rx="3.2" ${fl(r === 1 ? "flag-blue" : r === 0 && c === 1 ? "correct" : "surface-high")}/>`).join("")).join("")}
        <rect x="3" y="16.5" width="38" height="11" rx="5.5" ${fl("flag-blue")}/>`,
  };
  /** Mini ícone do jogo (SVG 44×44). */
  const gameIcon = (m) => `<svg viewBox="0 0 44 44" aria-hidden="true">${ICON_SVG[m] || ""}</svg>`;
  P.mp.icon = gameIcon;
  /** Grade de cartões dos jogos. compact = só ícone + nome (folhas de fim). */
  function gameGrid(sel, attr, compact) {
    const card = (k) => `<button class="mpc-card${k === sel ? " on" : ""}" data-${attr}="${k}" aria-pressed="${k === sel}" aria-label="${MODES[k].name}">
        <span class="mpc-ico">${gameIcon(k)}</span>${compact ? `<b>${MODES[k].pill}</b>` : `<b>${MODES[k].name}</b><small>${DESC[k]}</small>`}<i class="mpc-check" aria-hidden="true">✓</i></button>`;
    if (compact) return `<div class="mpc-grid compact">${ORDER.map(card).join("")}</div>`;
    return SECTIONS.map(([t, ks]) => `<div class="mpc-sec">${t}</div><div class="mpc-grid">${ks.map(card).join("")}</div>`).join("");
  }
  const markGrid = (box, attr, k) => box.querySelectorAll(`[data-${attr}]`).forEach((x) => { const on = x.dataset[attr] === k; x.classList.toggle("on", on); x.setAttribute("aria-pressed", on); });

  /** 1. Criar partida: escolha visual do jogo, nome, rodadas e o botão. */
  P.games.mpCreate = function (root) {
    leave();
    let m = P.store.get("mp-mode", "d");
    if (!MODES[m]) m = "d";
    let rounds = roundsPref(m);
    const body = shell(root, "Jogar com amigo");
    body.classList.add("mp-create");
    body.innerHTML = `<div class="mpc-hero">${duoIcon}<h2>Partida ao vivo</h2><p class="muted">Escolha o jogo e chame um amigo.</p></div>
        <div class="mpc-games">${gameGrid(m, "m", false)}</div>
        <div class="mp-card mpc-setup">
          ${nameField()}
          <div class="mp-field"><span>Rodadas · <em data-gname>${MODES[m].name}</em></span><div class="seg mp-rounds" data-rounds></div></div>
          <p class="mpc-rules" data-lead>${leadOf(m, true, rounds)}</p>
          <button class="pill wide mpc-go" data-create>Criar partida</button>
        </div>
        <div class="mp-or"><span>ou entre numa partida</span></div>
        <div class="mp-card">
          <label class="mp-field"><span>Link da partida</span>
            <input class="mp-input" data-paste placeholder="Cole aqui o link que seu amigo mandou" autocomplete="off" autocapitalize="off" spellcheck="false"></label>
          <p class="mp-err hidden" data-paste-err>Esse link não é de uma partida do Palavreiro.</p>
          <div class="row-btns"><button class="pill ghost" data-paste-btn>Colar</button><button class="pill accent" data-enter>Entrar na partida</button></div>
          <p class="mpc-qrnote">No app do Palavreiro dá para entrar lendo o QR code.</p>
        </div>`;
    const drawRounds = () => {
      const box = P.$("[data-rounds]", body);
      box.innerHTML = RCHOICES(m).map((n) => `<button data-r="${n}" class="${n === rounds ? "on" : ""}">${rLabel(m, n)}</button>`).join("");
      box.querySelectorAll("[data-r]").forEach((b) => (b.onclick = () => { rounds = +b.dataset.r; P.store.set("mp-rounds-" + m, rounds); P.fx.type(); drawRounds(); }));
      P.$("[data-lead]", body).textContent = leadOf(m, true, rounds);
      P.$("[data-gname]", body).textContent = MODES[m].name;
      P.$("[data-create]", body).textContent = `Criar partida de ${MODES[m].name}`;
    };
    drawRounds();
    body.querySelectorAll("[data-m]").forEach((b) => (b.onclick = () => { m = b.dataset.m; rounds = roundsPref(m); P.store.set("mp-mode", m); P.fx.type(); markGrid(body, "m", m); drawRounds(); }));
    P.$("[data-name]", body).addEventListener("keydown", (e) => { if (e.key === "Enter") P.$("[data-create]", body).click(); });
    // Entrar numa partida colando o link (inteiro, só o código, ou no meio de uma mensagem).
    const fromPasted = (t) => {
      t = String(t || "").trim();
      const a = /[?&]mp=([A-Za-z0-9-]+)/.exec(t); if (a) return P.mp.parse(a[1]) ? a[1].toLowerCase() : null;
      const b = /\b([a-z0-9]{12}-[tdqacpvxsgfkn]-[0-9a-z]{1,7})\b/.exec(t.toLowerCase()); return b && P.mp.parse(b[1]) ? b[1] : null;
    };
    const isGone = (t) => { t = String(t || "").trim().toLowerCase(); const a = /[?&]mp=([a-z0-9-]+)/.exec(t); return P.mp.gone(a ? a[1] : (/\b([a-z0-9]{12}-b-[0-9a-z]{1,7})\b/.exec(t) || [])[1]); };
    const err = P.$("[data-paste-err]", body), input = P.$("[data-paste]", body);
    const enter = (t) => {
      const code = fromPasted(t);
      err.textContent = !code && isGone(t) ? GONE_TXT : "Esse link não é de uma partida do Palavreiro.";
      err.classList.toggle("hidden", !!code || !String(t || "").trim());
      if (!code) return;
      try { sessionStorage.setItem("pv-mp-join", code); } catch (_) {}
      P.go("amigo/entrar");
    };
    input.addEventListener("input", () => err.classList.add("hidden"));
    input.addEventListener("keydown", (e) => { if (e.key === "Enter") enter(input.value); });
    P.$("[data-enter]", body).onclick = () => enter(input.value);
    P.$("[data-paste-btn]", body).onclick = async () => {
      try { const t = await navigator.clipboard.readText(); input.value = t; enter(t); }
      catch (_) { input.focus(); P.toast("Cole o link no campo"); }
    };
    P.mp.fromPasted = fromPasted;
    P.$("[data-create]", body).onclick = () => {
      const name = readName(root); if (!name) return;
      const s = session({ room: randStr(12), m, seed: newSeed(), host: true, name, root, rounds });
      s.joined = true;
      remember(s);
      waiting(s);
      connect(s);
      publish(helloMsg(s));
    };
  };

  /** 2. Esperando o convidado (anfitrião). */
  function waiting(s) {
    s.view = "wait";
    const link = P.mp.link(s.room, s.m, s.seed);
    const body = shell(s.root, "Jogar com amigo");
    body.innerHTML = `<div class="mp-card">
        <span class="pill-badge mp-mode-badge">${s.mode.name} · ${rSuffix(s.m, s.rounds)}</span>
        <h2>Mande o link para seu amigo</h2>
        <div class="mp-link" data-link>${P.esc(link)}</div>
        <div class="row-btns"><button class="pill" data-share>Compartilhar</button><button class="pill ghost" data-copy>Copiar link</button></div>
        ${qrBlock(link)}
        <div data-ttlbox><p class="mp-ttl" data-ttl></p></div>
        <div class="mp-wait" data-wait><div class="mark small bounce"><span></span><span></span><span></span><span></span><span></span></div><p>Esperando seu amigo entrar…</p></div>
      </div>`;
    P.$("[data-share]", body).onclick = () => P.share(`Bora jogar ${s.mode.inv} comigo no Palavreiro (${rSuffix(s.m, s.rounds)})? ${link}`);
    P.$("[data-copy]", body).onclick = async () => {
      try { await navigator.clipboard.writeText(link); P.toast("Link copiado!"); }
      catch (_) { const r = document.createRange(); r.selectNodeContents(P.$("[data-link]", body)); getSelection().removeAllRanges(); getSelection().addRange(r); P.toast("Selecione e copie o link"); }
    };
    if (s.startSent && s.guestHello) waitingJoined(s);
    ttl(s);
  }
  /** Contagem da sala aberta (10 min desde a criação). No fim, a sala expira. */
  function ttl(s) {
    clearInterval(s.ttlTimer);
    const tick = () => {
      const box = P.$("[data-ttlbox]", s.root);
      if (S !== s || s.view !== "wait" || !box || s.startSent || s.started) { clearInterval(s.ttlTimer); if (box && (s.startSent || s.started)) box.remove(); return; }
      const left = Math.ceil((s.at + KEEP_MS - Date.now()) / 1000);
      if (left > 0) {
        P.$("[data-ttl]", box).textContent = `A sala fica aberta por mais ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}, mesmo se você sair para mandar o link.`;
        return;
      }
      clearInterval(s.ttlTimer);
      box.innerHTML = `<div class="mp-expired"><p>A sala expirou.</p><button class="pill wide" data-again>Criar outra sala</button></div>`;
      P.$("[data-wait]", s.root)?.remove();
      const root = s.root;
      leave(false); // apaga a sala guardada e para de ouvir
      P.$("[data-again]", box).onclick = () => P.games.mpCreate(root);
    };
    tick(); s.ttlTimer = setInterval(tick, 1000);
  }
  /** QR da sala: o amigo do lado entra só apontando a câmera. */
  function qrSvg(text) {
    if (typeof qrcode !== "function") return "";
    const q = qrcode(0, "M"); q.addData(text); q.make();
    const n = q.getModuleCount(); let d = "";
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (q.isDark(y, x)) d += `M${x} ${y}h1v1h-1z`;
    return `<svg viewBox="-3 -3 ${n + 6} ${n + 6}" shape-rendering="crispEdges" role="img" aria-label="QR code do link da partida"><rect x="-3" y="-3" width="${n + 6}" height="${n + 6}" fill="#fff"/><path d="${d}" fill="#14102C"/></svg>`;
  }
  const qrBlock = (link) => { const svg = qrSvg(link); return svg ? `<div class="mp-qr" data-qr>${svg}<p>Amigo do lado? Ele aponta a câmera aqui e entra.</p></div>` : ""; };

  function waitingJoined(s) {
    const w = s.view === "wait" && P.$("[data-wait]", s.root);
    if (w) { w.classList.add("joined"); w.innerHTML = `<div class="mp-big">🎉</div><p><b>${P.esc(s.guestHello.name)}</b> entrou!</p>`; P.fx.win(); }
  }

  /** 3. Entrar (convidado, ao abrir o link). */
  P.games.mpJoin = function (root) {
    let info = null, raw = null;
    try { raw = sessionStorage.getItem("pv-mp-join"); info = P.mp.parse(raw); } catch (_) {}
    if (!info && P.mp.gone(raw)) {
      // Link antigo da Bomba-Relógio: o jogo saiu.
      leave();
      const body = shell(root, "Jogar com amigo");
      body.innerHTML = `<div class="mp-card center mp-gone"><div class="mp-big">🔗</div><h2>Link inválido</h2><p class="muted">${GONE_TXT}</p><button class="pill wide" data-new>Criar partida</button></div>`;
      P.$("[data-new]", body).onclick = () => { try { sessionStorage.removeItem("pv-mp-join"); } catch (_) {} P.games.mpCreate(root); };
      return;
    }
    if (!info) { P.toast("Link de partida inválido"); return P.games.mpCreate(root); }
    if (S && S.room === info.room && S.root === root && S.view) return; // já está nesta sala
    const s = session({ room: info.room, m: info.m, seed: info.seed, host: false, name: "", root });
    s.view = "join";
    const body = shell(root, "Jogar com amigo");
    body.innerHTML = `<div class="mp-card">
        <span class="mpc-ico big" aria-hidden="true">${gameIcon(s.m)}</span>
        <h2 data-invite>Você foi chamado para ${s.mode.inv}</h2>
        <p class="muted mp-lead" data-lead>${leadOf(s.m, false)}</p>
        ${nameField()}
        <button class="pill wide" data-join>Entrar</button>
      </div>`;
    P.$("[data-name]", body).addEventListener("keydown", (e) => { if (e.key === "Enter") P.$("[data-join]", body).click(); });
    P.$("[data-join]", body).onclick = () => {
      if (s.closed || s.joined) return;
      if (s.guestHello || s.started) return markFull(s);
      const name = readName(root); if (!name) return;
      s.me.name = name; s.joined = true;
      remember(s);
      joinWait(s);
      publish(helloMsg(s));
    };
    s.onLoaded = () => joinText(s);
    connect(s);
  };
  /** Volta para a sala guardada (mesmo id de jogador). Devolve false se não há sala válida. */
  P.games.mpResume = function (root) {
    const sv = saved();
    if (!sv) return false;
    const s = session({ room: sv.room, m: sv.m, seed: sv.seed, host: sv.host, name: sv.name, root, rounds: sv.r });
    s.me.id = sv.id; s.at = sv.at; s.joined = true; s.resumed = true;
    if (!sv.host) { try { sessionStorage.setItem("pv-mp-join", codeOf(s)); } catch (_) {} }
    shell(root, "Jogar com amigo");
    if (sv.host) waiting(s); else joinWait(s);
    connect(s);
    return true;
  };

  function joinText(s) {
    if (s.view !== "join") return;
    if (s.guestHello || s.started) return markFull(s);
    const h = P.$("[data-invite]", s.root);
    if (h) h.innerHTML = s.hostHello ? `<b>${P.esc(s.hostHello.name)}</b> te chamou para ${s.mode.inv} · ${rSuffix(s.m, s.rounds)}` : `Você foi chamado para ${s.mode.inv}`;
    const ld = P.$("[data-lead]", s.root); if (ld) ld.textContent = leadOf(s.m, false, s.rounds);
  }
  function joinWait(s) {
    s.view = "joinwait";
    const body = P.$("[data-mpbody]", s.root);
    body.innerHTML = `<div class="mp-card center"><span class="pill-badge mp-mode-badge">${s.mode.name}${s.hostHello ? ` · ${rSuffix(s.m, s.rounds)}` : ""}</span>
        <div class="mp-wait"><div class="mark small bounce"><span></span><span></span><span></span><span></span><span></span></div>
        <p data-waittxt>Esperando ${s.hostHello ? `<b>${P.esc(s.hostHello.name)}</b>` : "o anfitrião"} começar…</p></div></div>`;
  }

  // ---------- partida ----------
  function beginRound(s, at, live) {
    clearInterval(s.countTimer); clearTimeout(s.sheetTimer);
    document.querySelectorAll(".overlay").forEach((o) => o.remove());
    closePicker();
    const now = Date.now();
    // Contagem 3-2-1: usa o "at" do anfitrião quando o relógio bate; se não, conta a partir da chegada.
    let delay = at + 3000 - now;
    if (live && Math.abs(now - at) > 2500) delay = 3000;
    delay = Math.max(0, Math.min(3000, delay));
    stopGame(s);
    s.rn++;
    if (s.m === "a" || s.m === "p") return anaRound(s, now + delay, s.m);
    if (P.mp.kinds[s.m]) return P.mp.kinds[s.m].round(s, now + delay, live);
    const o = opp(s);
    s.round = { seed: s.seed, startAt: now + delay, opp: { rows: [], fresh: -1 }, ends: [], result: null, sentEnd: false, answers: P.mpWords(s.seed, s.mode.boards) };
    s.view = "game";
    const r = s.round;
    r.api = P.games.termoScreen(s.root, s.mode.key, null, null, {
      mp: {
        mode: s.mode.key, answers: r.answers,
        title: `Você × ${o ? o.name : "Amigo"}`,
        extra: reactBtn(s) + `<span class="mp-score" data-score></span>`,
        locked: () => Date.now() < r.startAt || !!r.result,
        mount: (el) => { r.strip = el; drawStrip(s); },
        onRow: (row, marks) => { if (S === s && s.round === r) publish({ t: "row", id: s.me.id, r: row, m: marks }); },
        onEnd: (won, tries) => {
          if (S !== s || s.round !== r || r.sentEnd) return;
          r.sentEnd = true; r.myMs = Date.now() - r.startAt;
          publish({ t: "end", id: s.me.id, won, tries, ms: r.myMs });
          r.api && r.api.redraw();
        },
        after: (el) => afterArea(s, r, el),
        showResult: () => resultSheet(s, r),
      },
    });
    drawScore(s);
    countdown(s, r);
  }

  // ---------- série: 1 partida, melhor de 3 ou melhor de 5 ----------
  const winsOf = (s) => Math.ceil(s.rounds / 2);
  const seriesOver = (s) => s.series.me >= winsOf(s) || s.series.opp >= winsOf(s);
  const seriesWinner = (s) => (s.series.me >= winsOf(s) ? "me" : s.series.opp >= winsOf(s) ? "opp" : null);
  const score = (s) => `${s.series.me} × ${s.series.opp}`;
  /** Placar da série no topo, ao lado do título ("melhor de N"; nada quando é 1 partida). */
  function drawScore(s) {
    const el = s.root && P.$("[data-score]", s.root); if (!el) return;
    el.classList.toggle("over", seriesOver(s));
    el.innerHTML = `<b>${seriesOver(s) ? "🏆 " : ""}${score(s)}</b>${s.rounds > 1 ? `<small>melhor de ${s.rounds}</small>` : ""}`;
  }

  /** Contagem 3-2-1. Com r.ready (ex.: Caça esperando a grade), só termina quando ficar pronto. */
  function countdown(s, r) {
    const el = P.h(`<div class="mp-count" aria-live="assertive"><b></b><p></p></div>`);
    const ready = () => !r.ready || r.ready();
    const tick = () => {
      if (S !== s || s.round !== r) { clearInterval(s.countTimer); el.remove(); return; }
      const left = r.startAt - Date.now();
      const b = el.querySelector("b");
      if (left <= 0 && !ready()) {
        if (b.textContent !== "…") { b.textContent = "…"; el.querySelector("p").textContent = r.waitTxt || "Preparando…"; }
        return;
      }
      if (left <= 0) {
        clearInterval(s.countTimer);
        b.textContent = "Já!"; el.classList.add("go"); el.querySelector("p").textContent = "";
        setTimeout(() => el.remove(), 450);
        r.api && r.api.redraw(); return;
      }
      const n = String(Math.ceil(left / 1000));
      if (b.textContent !== n) { b.textContent = n; b.classList.remove("beat"); void b.offsetWidth; b.classList.add("beat"); P.fx.type(); }
    };
    if (r.startAt - Date.now() <= 0 && ready()) return;
    P.$(".game", s.root).appendChild(el);
    tick(); s.countTimer = setInterval(tick, 100);
  }

  function oppStatus(s, r) {
    const o = opp(s), e = r.ends.find((x) => o && x.id === o.id), max = 5 + s.mode.boards;
    if (e) return e.won ? { txt: `acertou em ${e.tries}!`, cls: "win" } : { txt: "errou", cls: "lose" };
    if (s.oppLeft) return { txt: "saiu", cls: "gone" };
    return { txt: `jogando · ${r.opp.rows.filter(Boolean).length}/${max}`, cls: "" };
  }

  /** Faixa do adversário: só cores, uma linha por tentativa. */
  function drawStrip(s) {
    const r = s.round; if (!r || !r.strip || !document.body.contains(r.strip)) return;
    const o = opp(s), max = 5 + s.mode.boards, st = oppStatus(s, r), nb = s.mode.boards;
    let boards = "";
    for (let b = 0; b < nb; b++) {
      let rows = "", solved = false;
      for (let i = 0; i < max; i++) {
        const row = r.opp.rows[i], mk = row ? row[b] || "" : null;
        const cells = mk && mk.length === 5 ? [...mk].map((c) => `<i class="${c === "c" || c === "p" ? c : "a"}"></i>`).join("") : `<i></i><i></i><i></i><i></i><i></i>`;
        const cls = mk && mk.length === 5 ? (i === r.opp.fresh ? " in" : "") : row || solved ? " done" : "";
        rows += `<div class="ob-row${cls}">${cells}</div>`;
        if (mk === "ccccc") solved = true;
      }
      boards += `<div class="ob">${rows}</div>`;
    }
    r.strip.className = `opp n${nb}`;
    r.strip.innerHTML = `<div class="opp-who"><b>${P.esc(o ? o.name : "Amigo")}</b><small class="${st.cls}">${st.txt}</small></div><div class="opp-boards">${boards}</div>`;
    r.opp.fresh = -1;
  }

  function afterArea(s, r, el) {
    const o = opp(s);
    if (!r.result) { el.innerHTML = `<p class="muted mp-after">Esperando ${P.esc(o ? o.name : "seu amigo")} terminar…</p>`; return; }
    const b = P.h(`<button class="pill ghost">Ver resultado</button>`); b.onclick = () => resultSheet(s, r);
    el.appendChild(b);
  }

  /** Decide o vencedor pela ordem de chegada no tópico. */
  function decide(s) {
    const r = s.round; if (!r || r.result) return;
    const o = opp(s), first = r.ends.find((e) => e.won);
    let res = null;
    if (first) res = first.id === s.me.id ? "me" : "opp";
    else {
      const meDone = r.ends.some((e) => e.id === s.me.id), oppDone = (o && r.ends.some((e) => e.id === o.id)) || s.oppLeft;
      if (meDone && oppDone) res = "draw";
    }
    if (!res) return;
    r.result = res;
    if (!seriesOver(s)) { if (res === "me") s.series.me++; else if (res === "opp") s.series.opp++; }
    drawScore(s);
    P.store.add("mp-played"); if (res === "me") P.store.add("mp-won");
    P.logActivity();
    const g = r.api && r.api.game;
    if (g && !g.over) { r.api.stop(); P.toast(res === "opp" ? `${o ? o.name : "Seu amigo"} acertou primeiro` : "Fim da partida", 2200); }
    r.api && r.api.redraw();
    s.sheetTimer = setTimeout(() => { if (S === s && s.round === r && !document.querySelector(".overlay")) resultSheet(s, r); }, res === "me" ? 1800 : 1100);
  }

  /** 5. Fim. */
  function resultSheet(s, r) {
    if (!r.result) return;
    document.querySelectorAll(".overlay").forEach((x) => x.remove());
    const o = opp(s), oname = o ? o.name : "Amigo";
    const mine = r.ends.find((e) => e.id === s.me.id), theirs = o && r.ends.find((e) => e.id === o.id);
    const champ = seriesWinner(s);
    const words = `${r.answers.length > 1 ? "As palavras eram" : "A palavra era"} ${r.answers.map(P.words.display).join(", ")}`;
    const head = seriesHead(s, r, words);
    const line = (who, e, left) => {
      const res = e ? (e.won ? triesTxt(e.tries) : "errou") : left ? "saiu" : "não terminou";
      return `<div class="mp-res${e && e.won ? " ok" : ""}"><b>${who}</b><span>${res}</span><small>${e ? clockFmt(e.ms) : "–"}</small></div>`;
    };
    const ui = P.sheet(`${head}
      <div class="mp-results">${line("Você", mine, false)}${line(P.esc(oname), theirs, s.oppLeft)}</div>
      <div class="row-btns mp-share-row"><button class="pill ghost" data-share>Compartilhar</button><button class="pill accent" data-story>📸 Stories</button><button class="pill ghost" data-replay>▶ Replay</button></div>
      ${nextBlock(s)}
      <div class="row-btns" style="margin-top:10px"><button class="pill" data-again ${s.me.host && !s.oppLeft ? "" : "disabled"}>${champ ? "Nova série" : "Próxima rodada"}</button><button class="pill ghost" data-exit>Sair</button></div>
      ${s.me.host ? (s.oppLeft ? `<p class="note">${P.esc(oname)} saiu da partida.</p>` : "") : `<p class="note">Esperando o anfitrião</p>`}`);
    P.$("[data-share]", ui.el).onclick = () => {
      const t = champ === "me" ? `Levei a série por ${score(s)} 🏆`
        : champ === "opp" ? `Perdi a série por ${s.series.opp} × ${s.series.me} ⚔️`
        : (r.result === "me" ? `Venci em ${triesTxt(mine ? mine.tries : 0)} ⚔️` : r.result === "opp" ? "Perdi ⚔️" : "Empate ⚔️") + ` (série ${score(s)})`;
      P.share(`Palavreiro · Partida com ${oname}\n${t}`);
    };
    P.$("[data-replay]", ui.el).onclick = () => { ui.close(); r.api && r.api.replay(); };
    P.$("[data-story]", ui.el).onclick = () => storyCard(s, r);
    P.$("[data-exit]", ui.el).onclick = () => { ui.close(); leave(); P.go(""); };
    bindAgain(s, ui, champ ? "Nova série" : "Próxima rodada", !!champ);
  }

  // ---------- revanche trocando de modo (o anfitrião escolhe; o convidado sugere) ----------
  const nextBlock = (s) => (s.me.host
    ? `<div class="mp-next"><span>Próximo jogo</span>${gameGrid(s.m, "next", true)}<div class="seg mp-rounds small" data-nextr></div></div>`
    : `<div class="mp-next guest">
        <button class="pill ghost wide mp-sugg-open" data-sugg-open>Sugerir outro jogo</button>
        <div class="mp-sugg-grid hidden" data-sugg-grid><span>Qual jogo você quer?</span>${gameGrid(null, "sugg", true)}</div>
        <p class="mp-sugg-note hidden" data-sugg-note></p></div>`);
  /** Aviso "Ana quer jogar Damas" no topo da folha de fim aberta (anfitrião). */
  function suggBanner(s) {
    const sheet = document.querySelector(".overlay .sheet");
    const old = sheet && P.$(".mp-sugg", sheet);
    const sg = s.suggest;
    if (!sheet || !P.$("[data-again]", sheet) || !sg || sg.rn !== s.rn || !s.me.host) { if (old && (!sg || sg.rn !== s.rn)) old.remove(); return; }
    const el = P.h(`<div class="mp-sugg" role="status"><span class="mpc-ico">${gameIcon(sg.m)}</span>
        <p><b>${P.esc(oppName(s))}</b> quer jogar <b>${MODES[sg.m].name}</b></p>
        <button class="pill" data-sugg-yes>Aceitar</button><button class="icon-btn mp-sugg-x" data-sugg-no aria-label="Dispensar">✕</button></div>`);
    if (old) old.replaceWith(el); else P.$(".grab", sheet).after(el);
    P.$("[data-sugg-no]", el).onclick = () => { s.suggest = null; el.remove(); };
    P.$("[data-sugg-yes]", el).onclick = () => { if (s.pickNext) { s.suggest = null; s.pickNext(sg.m, true); } };
  }
  /** Convidado: "Sugerir outro jogo" abre a grade; tocar manda {"t":"suggest","id","m"}. */
  function bindSuggest(s, ui) {
    const open = P.$("[data-sugg-open]", ui.el); if (!open) return;
    const grid = P.$("[data-sugg-grid]", ui.el), note = P.$("[data-sugg-note]", ui.el);
    const sent = () => s.suggSent && s.suggSent.rn === s.rn;
    const showNote = () => { if (!sent()) return; note.classList.remove("hidden"); note.innerHTML = `Sugestão enviada: <b>${MODES[s.suggSent.m].name}</b>`; markGrid(grid, "sugg", s.suggSent.m); };
    showNote();
    open.onclick = () => { grid.classList.toggle("hidden"); P.fx.type(); };
    grid.querySelectorAll("[data-sugg]").forEach((b) => (b.onclick = () => {
      if (S !== s || s.oppLeft) return;
      const k = b.dataset.sugg;
      s.suggSent = { m: k, rn: s.rn };
      publish({ t: "suggest", id: s.me.id, m: k });
      P.fx.type(); P.toast("Sugestão enviada");
      grid.classList.add("hidden");
      showNote();
    }));
  }
  /** Botão principal da folha de fim: mesmo modo → texto de sempre; outro modo → "Jogar MODO". */
  function bindAgain(s, ui, sameText, newSeries) {
    bindSuggest(s, ui);
    const btn = P.$("[data-again]", ui.el); if (!btn) return;
    let next = s.m, nextR = s.rounds;
    const label = () => { btn.textContent = next !== s.m ? `Jogar ${MODES[next].name}` : nextR !== s.rounds ? (MODES[next].pts ? "Revanche" : "Nova série") : sameText; };
    const rounds = () => {
      const box = P.$("[data-nextr]", ui.el); if (!box) return;
      box.innerHTML = RCHOICES(next).map((n) => `<button data-nr="${n}" class="${n === nextR ? "on" : ""}">${rLabel(next, n)}</button>`).join("");
      box.querySelectorAll("[data-nr]").forEach((b) => (b.onclick = () => { nextR = +b.dataset.nr; P.store.set("mp-rounds-" + next, nextR); P.fx.type(); rounds(); label(); }));
    };
    const pick = (k) => {
      next = k; nextR = next === s.m ? s.rounds : roundsPref(next);
      markGrid(ui.el, "next", k);
      rounds(); label();
    };
    ui.el.querySelectorAll("[data-next]").forEach((b) => (b.onclick = () => { P.fx.type(); pick(b.dataset.next); }));
    rounds(); label();
    btn.onclick = () => {
      if (!s.me.host || s.oppLeft || S !== s || btn.disabled) return;
      btn.disabled = true;
      const msg = { t: "again", seed: newSeed().toString(36), at: Date.now(), m: next, r: nextR };
      if (newSeries || next !== s.m || nextR !== s.rounds) msg.s = 1;
      publish(msg);
    };
    // Aceitar a sugestão do convidado = escolher esse jogo e começar.
    s.pickNext = (k, go) => { if (!document.body.contains(btn)) return; pick(k); if (go) btn.click(); };
    suggBanner(s);
  }

  // ---------- cartão para Stories (todos os modos) ----------
  function storyCard(s, r) {
    const meName = s.me.name || "Você", oName = oppName(s);
    const nameOf = (w) => (w === "me" ? meName : oName);
    const mine = (w) => (w === "me" ? "win" : w === "opp" ? "lose" : "draw");
    let sc, result, detail, gold = false, res;
    const K = P.mp.kinds[r.kind];
    if (MODES[r.kind] && MODES[r.kind].pts) {
      sc = `${r.pts.me} × ${r.pts.opp}`; res = mine(r.result); gold = r.result !== "draw";
      result = r.result === "draw" ? "Empate" : `🏆 ${nameOf(r.result)} venceu!`;
      detail = K && K.story ? K.story(s, r) : `${r.n} rodadas · ${sc}`;
    } else {
      const champ = seriesWinner(s);
      sc = score(s);
      if (champ) { result = `🏆 ${nameOf(champ)} levou a série!`; gold = true; res = mine(champ); }
      else { result = r.result === "draw" ? "Empate" : `${nameOf(r.result)} venceu a rodada`; res = mine(r.result); }
      if (K && K.story) detail = K.story(s, r);
      else {
        const o = opp(s), line = (n, e, left) => `${n}: ${e ? (e.won ? `acertou em ${e.tries} · ${clockFmt(e.ms)}` : "errou") : left ? "saiu" : "não terminou"}`;
        detail = [line(meName, r.ends.find((e) => e.id === s.me.id), false), line(oName, o && r.ends.find((e) => e.id === o.id), s.oppLeft)];
      }
    }
    const pill = `${s.mode.name} ${({ a: "🔤", p: "❓", c: "🔎", v: "⭕", x: "♛", k: "♞", s: "✋", g: "🧠", f: "🪢" })[r.kind] || "⚔️"}`;
    return P.storyVs({ pill, me: meName, opp: oName, score: sc, gold, result, detail, mine: res });
  }
  P.mp.storyCard = () => S && S.round && S.round.result ? storyCard(S, S.round) : null;

  // ---------- reações rápidas ----------
  const EMOJIS = ["😂", "😱", "🔥", "👏", "😈"];
  const REACT_FRESH = 10000, REACT_GAP = 1500;
  const cooling = (s) => !!s.reactAt && Date.now() - s.reactAt < REACT_GAP;
  const reactBtn = (s) => `<button class="icon-btn mp-react${cooling(s) ? " cool" : ""}" data-react aria-label="Reagir" title="Reagir">😊</button>`;
  function closePicker() { document.querySelectorAll(".mp-react-catch, .mp-react-pick").forEach((x) => x.remove()); }
  function openPicker(s) {
    if (document.querySelector(".mp-react-pick")) return closePicker();
    if (cooling(s)) return;
    const bar = P.$(".topbar", s.root), top = bar ? bar.getBoundingClientRect().bottom + 4 : 60;
    const catcher = P.h(`<div class="mp-react-catch"></div>`);
    const pick = P.h(`<div class="mp-react-pick" role="menu" aria-label="Reagir" style="top:${Math.round(top)}px">${EMOJIS.map((e) => `<button role="menuitem" data-e="${e}" aria-label="${e}">${e}</button>`).join("")}</div>`);
    catcher.addEventListener("click", (e) => { e.stopPropagation(); closePicker(); });
    pick.querySelectorAll("[data-e]").forEach((b) => (b.onclick = (e) => { e.stopPropagation(); closePicker(); sendReact(s, b.dataset.e); }));
    document.body.append(catcher, pick);
  }
  function sendReact(s, e) {
    if (S !== s || !s.round || cooling(s) || !EMOJIS.includes(e)) return;
    s.reactAt = Date.now();
    publish({ t: "react", id: s.me.id, e, at: s.reactAt });
    bubble(s, e, "Você", true);
    document.querySelectorAll("[data-react]").forEach((b) => b.classList.add("cool"));
    clearTimeout(s.reactTimer);
    s.reactTimer = setTimeout(() => document.querySelectorAll("[data-react]").forEach((b) => b.classList.remove("cool")), REACT_GAP);
  }
  /** Balão que sobe e some: abaixo da faixa do adversário quando houver, senão logo abaixo do topo. */
  let bubbleN = 0;
  function bubble(s, e, who, mine) {
    const anchor = P.$(".opp", s.root) || P.$(".ana-opp", s.root) || P.$(".topbar", s.root);
    const y = anchor ? anchor.getBoundingClientRect().bottom + 8 : 70;
    const live = document.querySelectorAll(".mp-bubble").length;
    const dx = [0, 56, -56, 112, -112][(live ? bubbleN : 0) % 5];
    bubbleN = live ? bubbleN + 1 : 1;
    const el = P.h(`<div class="mp-bubble${mine ? " mine" : ""}" aria-live="polite" style="top:${Math.round(y)}px;left:calc(50% + ${dx}px)"><span>${e}</span><small>${P.esc(who)}</small></div>`);
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 2600);
  }
  document.addEventListener("click", (e) => {
    const b = e.target.closest && e.target.closest("[data-react]");
    if (b && S && S.round && S.view === "game") { e.stopPropagation(); openPicker(S); }
  });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && document.querySelector(".mp-react-pick")) closePicker(); }, true);

  // ================= jogos fora do Termo (mesma sala, mesmas mensagens do app) =================
  function stopGame(s) {
    clearInterval(s.gameTimer); clearTimeout(s.boomTimer);
    if (s.raf) cancelAnimationFrame(s.raf);
    s.gameTimer = s.boomTimer = s.raf = null;
  }
  /** Ids do anfitrião (h) e do convidado (g) na sala. */
  const ids = (s) => ({
    h: s.me.host ? s.me.id : s.hostHello && s.hostHello.id,
    g: s.me.host ? s.guestHello && s.guestHello.id : s.me.id,
  });
  const oppName = (s) => { const o = opp(s); return o ? o.name : "Amigo"; };
  const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;
  const tiles5 = (letters, cls = "") => [0, 1, 2, 3, 4].map((i) => `<div class="tile input${cls}">${letters[i] || ""}</div>`).join("");
  /** Só para testes (P.mp.debug ou window.PV_DEBUG = {roundMs, hintMs: [1ª, última], cacaMs}); sem isso valem os tempos da regra. */
  const debug = () => { const d = P.mp.debug || window.PV_DEBUG; return d && typeof d === "object" ? d : {}; };
  /** Tela base: topo "Você × NOME" + placar, área do jogo e (se houver keys) o teclado do Termo. */
  function gameShell(s, cls, inner, keys) {
    s.root.classList.remove("nv-wide"); // só a Batalha Naval usa a tela larga
    s.root.innerHTML = P.topbar(`<span data-title>Você × ${P.esc(oppName(s))}</span>`, { extra: reactBtn(s) + `<span class="mp-score" data-score></span>` })
      + `<div class="game mpg ${cls}">${inner}</div>`;
    const game = P.$(".game", s.root);
    if (!keys) return { el: null, game };
    const kb = P.keyboard(game, keys);
    kb.el = P.$(".keyboard", game); kb.game = game;
    return kb;
  }
  function shakeEl(el, msg) {
    P.fx.invalid(); P.toast(msg);
    if (el) { el.classList.remove("shake"); void el.offsetWidth; el.classList.add("shake"); }
  }

  // ---------- séries (Caça, Velha, Damas): fim da rodada e folha ----------
  /** Fecha a rodada com "me" | "opp" | "draw" (empate não conta na série) e abre a folha depois. */
  function settle(s, r, res, sheet, wait = 1700) {
    if (r.result) return;
    r.result = res;
    if (!seriesOver(s)) { if (res === "me") s.series.me++; else if (res === "opp") s.series.opp++; }
    drawScore(s);
    P.store.add("mp-played"); if (res === "me") P.store.add("mp-won");
    P.logActivity();
    if (res === "me") P.fx.win(); else if (res === "opp") P.fx.lose();
    if (res === "me" && seriesOver(s)) P.confetti();
    clearTimeout(s.sheetTimer);
    s.sheetTimer = setTimeout(() => { if (S === s && s.round === r && !document.querySelector(".overlay")) sheet(); }, wait);
  }
  function seriesHead(s, r, detail) {
    const oname = P.esc(oppName(s)), champ = seriesWinner(s);
    return champ
      ? `<div class="mp-trophy${champ === "me" ? "" : " theirs"}" aria-hidden="true">🏆</div><h2>${champ === "me" ? (s.rounds === 1 ? "Você venceu!" : "Você levou a série!") : s.rounds === 1 ? `${oname} venceu` : `${oname} levou a série`}</h2>
         <p class="mp-series gold">${rLabel(s.m, s.rounds)} · ${score(s)}</p>
         <p class="subtitle">${s.rounds > 1 ? (r.result === "me" ? "Você venceu a última rodada. " : r.result === "opp" ? `${oname} venceu a última rodada. ` : "") : ""}${detail}</p>`
      : `<h2>${r.result === "me" ? "Você venceu a rodada!" : r.result === "opp" ? `${oname} venceu a rodada` : "Empate"}</h2>
         <p class="subtitle">${detail}</p>
         <p class="mp-series">Série: Você ${score(s)} ${oname}${s.rounds > 1 ? ` · melhor de ${s.rounds}` : ""}</p>`;
  }
  /** Folha de fim das séries: cabeçalho, Compartilhar/Stories, Próximo jogo, Próxima rodada/Nova série, Sair. */
  function seriesSheet(s, r, { icon, detail, extra = "", share = "" }) {
    if (!r.result) return;
    document.querySelectorAll(".overlay").forEach((x) => x.remove());
    const oname = oppName(s), champ = seriesWinner(s);
    const ui = P.sheet(`<div class="mp-sheet-ico" aria-hidden="true">${icon}</div>${seriesHead(s, r, detail)}${extra}
      <div class="row-btns mp-share-row" style="margin-top:14px"><button class="pill ghost" data-share>Compartilhar</button><button class="pill accent" data-story>📸 Stories</button></div>
      ${nextBlock(s)}
      <div class="row-btns" style="margin-top:10px"><button class="pill" data-again ${s.me.host && !s.oppLeft ? "" : "disabled"}>${champ ? "Nova série" : "Próxima rodada"}</button><button class="pill ghost" data-exit>Sair</button></div>
      ${s.me.host ? (s.oppLeft ? `<p class="note">${P.esc(oname)} saiu da partida.</p>` : "") : `<p class="note">Esperando o anfitrião</p>`}`);
    P.$("[data-share]", ui.el).onclick = () => {
      const t = champ === "me" ? `${s.rounds === 1 ? "Venci" : `Levei a série por ${score(s)}`} 🏆`
        : champ === "opp" ? (s.rounds === 1 ? "Perdi" : `Perdi a série por ${s.series.opp} × ${s.series.me}`)
        : `${r.result === "me" ? "Venci a rodada" : r.result === "opp" ? "Perdi a rodada" : "Empate"} (série ${score(s)})`;
      P.share(`Palavreiro · ${s.mode.name} com ${oname}\n${t}${share ? "\n" + share : ""}`);
    };
    P.$("[data-story]", ui.el).onclick = () => storyCard(s, r);
    P.$("[data-exit]", ui.el).onclick = () => { ui.close(); leave(); P.go(""); };
    bindAgain(s, ui, champ ? "Nova série" : "Próxima rodada", !!champ);
    return ui;
  }
  /** "Ver resultado" no fim da tela de jogo. */
  function afterBtn(el, r, open) {
    if (!el) return;
    el.innerHTML = "";
    if (r.result) { const b = P.h(`<button class="pill ghost">Ver resultado</button>`); b.onclick = open; el.appendChild(b); }
  }

  // ---------- jogos de pontos (Anagrama, Qual é a Palavra?, Adedonha, Trivia): fim e folha ----------
  /** Fecha a partida de pontos (r.result já definido): contadores, confete e a folha depois de "wait". */
  function ptsDone(s, r, sheet, wait) {
    P.store.add("mp-played"); if (r.result === "me") P.store.add("mp-won");
    P.logActivity();
    clearTimeout(s.sheetTimer);
    s.sheetTimer = setTimeout(() => {
      if (S !== s || s.round !== r) return;
      if (r.result === "me") P.confetti();
      r.api && r.api.redraw();
      if (!document.querySelector(".overlay")) sheet();
    }, wait);
  }
  /** Placar de pontos no topo: "3 × 2" e a rodada. */
  function ptsScore(s, r, small) {
    const sc = P.$("[data-score]", s.root); if (!sc) return;
    sc.classList.toggle("over", !!r.result);
    sc.innerHTML = `<b>${r.result === "me" ? "🏆 " : ""}${r.pts.me} × ${r.pts.opp}</b><small>${small}</small>`;
  }
  /** Folha de fim dos jogos de pontos. */
  function ptsSheet(s, r, { icon, extra = "", share = "", title = "" }) {
    if (!r.result) return;
    document.querySelectorAll(".overlay").forEach((x) => x.remove());
    const oname = oppName(s), en = P.esc(oname), sc = `${r.pts.me} × ${r.pts.opp}`;
    const ui = P.sheet(`<div class="mp-sheet-ico" aria-hidden="true">${icon}</div>
      <h2>${r.result === "me" ? "Você venceu! 🏆" : r.result === "opp" ? `${en} venceu` : "Empate"}</h2>
      <p class="mp-series gold ana-final">${sc}</p>${title ? `<p class="subtitle">${title}</p>` : ""}
      ${extra}
      <div class="row-btns mp-share-row" style="margin-top:14px"><button class="pill ghost" data-share>Compartilhar</button><button class="pill accent" data-story>📸 Stories</button></div>
      ${nextBlock(s)}
      <div class="row-btns" style="margin-top:10px">${s.me.host ? `<button class="pill" data-again ${s.oppLeft ? "disabled" : ""}>Revanche</button>` : ""}<button class="pill ghost" data-exit>Sair</button></div>
      ${s.me.host ? (s.oppLeft ? `<p class="note">${en} saiu da partida.</p>` : "") : `<p class="note">Esperando o anfitrião</p>`}`);
    P.$("[data-share]", ui.el).onclick = () => {
      const t = r.result === "me" ? `Venci por ${sc}` : r.result === "opp" ? `Perdi por ${sc}` : `Empate ${sc}`;
      P.share(`Palavreiro · ${s.mode.name} com ${oname}\n${t} ${share || icon}`);
    };
    P.$("[data-story]", ui.el).onclick = () => storyCard(s, r);
    P.$("[data-exit]", ui.el).onclick = () => { ui.close(); leave(); P.go(""); };
    bindAgain(s, ui, "Revanche", false);
    return ui;
  }

  // Peças internas para js/multi-games.js e js/multi-{adedonha,trivia,forca}.js.
  P.mp._ = {
    get S() { return S; }, publish, opp, ids, oppName, plural, debug, gameShell, shakeEl, countdown, drawScore,
    seriesOver, seriesWinner, score, settle, seriesSheet, afterBtn, stopGame, clockFmt, ptsDone, ptsScore, ptsSheet, gameIcon,
  };

  // ---------- Anagrama e Qual é a Palavra? (rodadas de pontos, mensagens solve/skip) ----------
  const ANA_GAP = 2500;
  /** Embaralhamento da palavra k, igual nos dois lados. */
  function anaShuffle(word, k, seed) {
    const a = [...word];
    let x = ((seed + k * 7919) % 2147483646) + 1;
    for (let i = 4; i >= 1; i--) { x = (x * 48271) % 2147483647; const j = x % (i + 1); const t = a[i]; a[i] = a[j]; a[j] = t; }
    let out = a.join("");
    for (let n = 0; n < 4 && out === word; n++) out = out.slice(1) + out[0];
    return out;
  }
  P.mp.anaShuffle = anaShuffle;
  P.mp.anaWords = (seed, n = 10) => P.mpWords(seed, n);
  const sorted = (w) => [...w].sort().join("");

  /** Qual é a Palavra?: linhas válidas "PALAVRA|definição" do definicoes.txt, na ordem do arquivo. */
  const defList = () => P.text("definicoes.txt").then((t) => t.split("\n").map((l) => l.trim())
    .filter((l) => l && !l.startsWith("#") && l.includes("|"))
    .map((l) => { const f = l.split("|"); return { word: f[0].trim(), text: (f[1] || "").trim() }; })
    .filter((x) => x.word && x.text));
  /** As N palavras da partida (mesma conta do app): idx = ((SEED mod M)·7919 mod M + 104729 + k·577) mod M, sem repetir. */
  async function defWords(seed, n) {
    const list = await defList(), M = list.length, out = [], used = new Set();
    for (let k = 0; out.length < n && k < M * 4; k++) {
      const i = (((seed % M) * 7919) % M + 104729 + k * 577) % M;
      if (!used.has(i)) { used.add(i); out.push(list[i]); }
    }
    return out;
  }
  P.mp.defWords = defWords;
  const HINTS = () => { const h = debug().hintMs; return Array.isArray(h) && h.length === 2 ? h : [20000, 40000]; };
  const palTarget = (r, k) => P.norm(r.words[k]);
  const palSlots = (t) => [...t].map((c, i) => (/[A-Z]/.test(c) ? i : -1)).filter((i) => i >= 0);
  /** Dicas automáticas (iguais nos dois): aos 20 s a 1ª letra, aos 40 s também a última. */
  function palRevealed(r, k) {
    const el = Date.now() - r.start[k], [h1, h2] = HINTS(), sl = palSlots(palTarget(r, k)), out = [];
    if (el >= h1) out.push(sl[0]);
    if (el >= h2 && sl.length > 1) out.push(sl[sl.length - 1]);
    return out;
  }
  const palOpen = (r, k) => { const rev = palRevealed(r, k); return palSlots(palTarget(r, k)).filter((i) => !rev.includes(i)); };
  const inputMax = (r) => (r.kind === "p" ? palOpen(r, r.done).length : 5);

  function anaRound(s, startAt, kind) {
    const { h, g } = ids(s), n = s.rounds, pal = kind === "p";
    const r = (s.round = { kind, seed: s.seed, startAt, host: h, guest: g, n, words: null, items: null, shuf: null,
      roundMs: debug().roundMs || (pal ? 60000 : 45000), start: [startAt], res: [], done: 0, pts: { me: 0, opp: 0 }, input: [],
      order: null, orderFor: -1, sent: {}, result: null, shown: -1 });
    if (pal) {
      r.ready = () => !!r.words; r.waitTxt = "Carregando…";
      defWords(s.seed, n).then((items) => {
        if (S !== s || s.round !== r) return;
        r.items = items; r.words = items.map((x) => x.word.toUpperCase());
        if (Date.now() > r.start[0]) r.start[0] = r.startAt = Date.now();
        anaDraw(s, r);
      }).catch(() => P.toast("Não foi possível carregar as definições"));
    } else {
      r.words = P.mpWords(s.seed, n);
      r.shuf = r.words.map((w, k) => anaShuffle(w, k, s.seed));
    }
    s.view = "game";
    const stage = pal ? `
        <div class="ana-opp" data-opp></div>
        <div class="pal-stage">
          <p class="pal-round" data-pround></p>
          <div class="pal-def" data-def></div>
          <div class="pal-slots" data-input></div>
          <p class="pal-hint" data-hint></p>
          <p class="ana-msg" data-msg></p>
        </div>
        <div class="timer ana-timer"><i data-bar></i></div>
        <div class="center" data-after></div>` : `
        <div class="ana-opp" data-opp></div>
        <div class="ana-stage">
          <div class="ana-letters" data-letters></div>
          <button class="ana-mix" data-mix>🔀 Embaralhar</button>
          <p class="ana-msg" data-msg></p>
        </div>
        <div class="timer ana-timer"><i data-bar></i></div>
        <div class="mpg-input" data-input></div>
        <div class="center" data-after></div>`;
    r.kb = gameShell(s, pal ? "ana pal" : "ana", stage, {
      enterLabel: pal ? "ENVIAR" : "ENTER",
      onLetter: (c) => { if (anaCanType(s, r) && r.input.length < inputMax(r)) { r.input.push(c); P.fx.type(); anaInput(s, r); } },
      onDelete: () => { if (anaCanType(s, r) && r.input.length) { r.input.pop(); anaInput(s, r); } },
      onEnter: () => anaSubmit(s, r),
    });
    const mix = P.$("[data-mix]", s.root);
    if (mix) mix.onclick = () => {
      const k = anaActive(r); if (k < 0 || !r.order) return;
      let nn; do { nn = P.shuffle(r.order); } while (nn.join("") === r.order.join("") && new Set(r.order).size > 1);
      r.order = nn; P.fx.type(); anaLetters(s, r, true);
    };
    r.api = { redraw: () => anaDraw(s, r) };
    anaDraw(s, r);
    countdown(s, r);
    s.gameTimer = setInterval(() => anaTick(s, r), 100);
  }
  /** Rodada em jogo agora (-1 = contagem, intervalo entre rodadas ou fim). */
  const anaActive = (r) => (!r.result && r.words && r.done < r.n && Date.now() >= r.start[r.done] ? r.done : -1);
  const anaCanType = (s, r) => S === s && s.round === r && anaActive(r) >= 0 && !r.sent[r.done];

  function anaSubmit(s, r) {
    if (!anaCanType(s, r)) return;
    const k = r.done, row = P.$("[data-input]", s.root);
    if (r.kind === "p") {
      const t = palTarget(r, k), open = palOpen(r, k);
      if (r.input.length < open.length) return shakeEl(row, "Complete a palavra");
      const guess = [...t]; open.forEach((pos, j) => (guess[pos] = r.input[j]));
      if (guess.join("") !== t) { r.input = []; anaInput(s, r); return shakeEl(row, "Não é essa"); }
    } else {
      const ans = r.words[k];
      if (r.input.length < 5) return shakeEl(row, "Palavra incompleta");
      const w = r.input.join("");
      if (sorted(w) !== sorted(ans) || !(P.words.ok(w) || w === ans)) { r.input = []; anaInput(s, r); return shakeEl(row, "Não é essa"); }
    }
    r.sent[k] = true;
    publish({ t: "solve", id: s.me.id, r: k, ms: Date.now() - r.start[k] });
    anaDraw(s, r);
  }
  function anaMsg(s, m) {
    const r = s.round;
    if (!r || r.result || Number(m.r) !== r.done || r.done >= r.n) return;
    if (m.t === "solve" && m.id !== r.host && m.id !== r.guest) return;
    const k = r.done, by = m.t === "solve" ? m.id : null;
    r.res[k] = by;
    if (by === s.me.id) r.pts.me++; else if (by) r.pts.opp++;
    r.done = k + 1;
    r.start[k + 1] = Date.now() + ANA_GAP;
    r.input = [];
    if (by === s.me.id) P.fx.win(); else if (by) { P.tone([[392, 120], [330, 160]]); P.vibrate(60); } else P.tone([[330, 160]]);
    if (r.done >= r.n) {
      r.result = r.pts.me > r.pts.opp ? "me" : r.pts.me < r.pts.opp ? "opp" : "draw";
      P.store.add("mp-played"); if (r.result === "me") P.store.add("mp-won");
      P.logActivity();
      clearTimeout(s.sheetTimer);
      s.sheetTimer = setTimeout(() => {
        if (S !== s || s.round !== r) return;
        if (r.result === "me") P.confetti();
        anaDraw(s, r);
        if (!document.querySelector(".overlay")) anaSheet(s, r);
      }, ANA_GAP);
    }
    anaDraw(s, r);
  }
  function anaTick(s, r) {
    if (S !== s || s.round !== r) return stopGame(s);
    const k = anaActive(r);
    let hints = "";
    if (k >= 0) {
      const el = Date.now() - r.start[k];
      // Tempo esgotado: o anfitrião manda o "skip" (o convidado só se o anfitrião saiu ou passaram 5 s a mais).
      if (el > r.roundMs && !r.sent["skip" + k] && (s.me.host || s.oppLeft || el > r.roundMs + 5000)) {
        r.sent["skip" + k] = true;
        publish({ t: "skip", r: k });
      }
      const bar = P.$("[data-bar]", s.root);
      if (bar) bar.style.width = `${Math.max(0, 100 - (el / r.roundMs) * 100)}%`;
      if (r.kind === "p") { hints = palRevealed(r, k).length; palHint(s, r, k); }
    }
    // Mudou de fase (contagem → rodada, dica nova, rodada → intervalo, intervalo → próxima): redesenha.
    const phase = `${r.done}|${k}|${hints}`;
    if (phase !== r.phase) {
      r.phase = phase;
      if (k >= 0 && r.kind === "p") {
        // Dica nova: a letra revelada sai do que foi digitado; as outras continuam nas mesmas casas.
        const now = palOpen(r, k), at = {};
        if (r.openFor === k) (r.openWas || []).forEach((pos, j) => { if (j < r.input.length) at[pos] = r.input[j]; });
        const keep = [];
        for (const pos of now) { if (!at[pos]) break; keep.push(at[pos]); }
        r.input = r.openFor === k ? keep : [];
        r.openFor = k; r.openWas = now;
      } else if (k >= 0 && r.input.length > inputMax(r)) r.input = r.input.slice(0, inputMax(r));
      anaDraw(s, r);
    }
  }

  function anaLetters(s, r, pop) {
    const el = P.$("[data-letters]", s.root); if (!el) return;
    const k = anaActive(r);
    let letters;
    if (k >= 0) {
      if (r.orderFor !== k) { r.orderFor = k; r.order = [...r.shuf[k]]; }
      letters = r.order;
    } else if (r.done > 0 && (r.done >= r.n || Date.now() < r.start[r.done])) letters = [...P.words.display(r.words[r.done - 1])];
    else letters = ["?", "?", "?", "?", "?"];
    el.classList.toggle("reveal", k < 0 && r.done > 0);
    el.innerHTML = letters.map((c, i) => `<div class="ana-l l${i}${pop ? " pop" : ""}">${P.esc(c)}</div>`).join("");
  }
  /** Qual é a Palavra?: texto da próxima dica. */
  function palHint(s, r, k) {
    const el = P.$("[data-hint]", s.root); if (!el) return;
    const t = Date.now() - r.start[k], [h1, h2] = HINTS();
    const txt = k < 0 ? "" : t < h1 ? `Dica: 1ª letra em ${Math.ceil((h1 - t) / 1000)} s` : t < h2 ? `Dica: última letra em ${Math.ceil((h2 - t) / 1000)} s` : "Dicas: 1ª e última letra";
    if (el.textContent !== txt) el.textContent = txt;
  }
  /** Qual é a Palavra?: quadradinhos da resposta (hífen/espaço fixos, dicas em amarelo). */
  function palDraw(s, r) {
    const k = anaActive(r), box = P.$("[data-input]", s.root), def = P.$("[data-def]", s.root), rd = P.$("[data-pround]", s.root);
    let kk = k, reveal = false;
    if (k < 0 && r.done > 0 && (r.done >= r.n || Date.now() < r.start[r.done])) { kk = r.done - 1; reveal = true; }
    if (kk < 0 || !r.words) {
      box.innerHTML = ""; def.textContent = r.words ? "Prepare-se…" : "Carregando…"; rd.textContent = `Rodada 1/${r.n}`;
      box.classList.add("off"); return;
    }
    rd.textContent = `Rodada ${kk + 1}/${r.n}`;
    def.textContent = r.items[kk].text;
    const t = palTarget(r, kk), disp = r.words[kk];
    box.style.setProperty("--n", t.length);
    const on = anaCanType(s, r);
    box.classList.toggle("off", !reveal && !on);
    if (reveal) {
      const by = r.res[kk], cls = by === s.me.id ? "c" : by ? "o" : "a";
      box.innerHTML = [...t].map((c, i) => (/[A-Z]/.test(c) ? `<div class="tile ${cls}">${P.esc(disp[i] || c)}</div>` : `<span class="pal-fix">${P.esc(c === " " ? " " : c)}</span>`)).join("");
      return;
    }
    const rev = palRevealed(r, kk), open = palOpen(r, kk);
    box.innerHTML = [...t].map((c, i) => {
      if (!/[A-Z]/.test(c)) return `<span class="pal-fix">${P.esc(c === " " ? " " : c)}</span>`;
      if (rev.includes(i)) return `<div class="tile p">${P.esc(disp[i] || c)}</div>`;
      const j = open.indexOf(i);
      return `<div class="tile input${on && j === r.input.length ? " sel" : ""}">${r.input[j] || ""}</div>`;
    }).join("");
  }
  function anaInput(s, r) {
    if (r.kind === "p") return palDraw(s, r);
    const el = P.$("[data-input]", s.root); if (!el) return;
    const on = anaCanType(s, r);
    el.classList.toggle("off", !on);
    el.innerHTML = tiles5(r.input);
    if (on && r.input.length < 5) el.querySelectorAll(".tile")[r.input.length].classList.add("sel");
  }
  const ptsWord = (r, w) => (r.kind === "p" ? w : P.words.display(w));
  function anaDraw(s, r) {
    if (S !== s || s.round !== r || !P.$(".ana", s.root)) return;
    const oname = P.esc(oppName(s)), k = anaActive(r), shownRound = k >= 0 ? k : Math.max(0, Math.min(r.n, r.done) - (r.done > 0 ? 1 : 0));
    const sc = P.$("[data-score]", s.root);
    if (sc) { sc.classList.toggle("over", !!r.result); sc.innerHTML = `<b>${r.result === "me" ? "🏆 " : ""}${r.pts.me} × ${r.pts.opp}</b><small>rodada ${shownRound + 1}/${r.n}</small>`; }
    if (r.kind === "a") anaLetters(s, r, k >= 0 && r.shown !== k);
    if (k >= 0) r.shown = k;
    const last = r.done - 1, inGap = k < 0 && r.done > 0;
    const msg = P.$("[data-msg]", s.root);
    if (inGap) {
      const by = r.res[last], word = P.esc(ptsWord(r, r.words[last]));
      msg.className = "ana-msg " + (by === s.me.id ? "win" : by ? "lose" : "none");
      msg.innerHTML = by === s.me.id ? `Você acertou! <b>${word}</b>` : by ? `${oname} acertou: <b>${word}</b>` : `Ninguém acertou: <b>${word}</b>`;
    } else if (k >= 0 && r.sent[k]) { msg.className = "ana-msg"; msg.textContent = "Conferindo…"; }
    else { msg.className = "ana-msg"; msg.textContent = ""; }
    const mix = P.$("[data-mix]", s.root); if (mix) mix.disabled = k < 0;
    if (r.kind === "p") palHint(s, r, k);
    const o = P.$("[data-opp]", s.root);
    const oppGot = inGap && r.res[last] && r.res[last] !== s.me.id && Date.now() < r.start[r.done];
    o.className = "ana-opp" + (oppGot ? " got" : "") + (s.oppLeft ? " gone" : "");
    o.innerHTML = s.oppLeft ? `<b>${oname}</b> saiu` : oppGot ? `<b>${oname}</b> acertou!` : `<b>${oname}</b>: ${plural(r.pts.opp, "ponto", "pontos")}`;
    const bar = P.$("[data-bar]", s.root);
    if (k < 0) bar.style.width = r.done > 0 ? "0%" : "100%";
    anaInput(s, r);
    r.kb.el.classList.toggle("off", !anaCanType(s, r));
    const after = P.$("[data-after]", s.root);
    after.innerHTML = "";
    if (r.result && Date.now() >= r.start[r.n]) { const b = P.h(`<button class="pill ghost">Ver resultado</button>`); b.onclick = () => anaSheet(s, r); after.appendChild(b); }
  }
  function anaSheet(s, r) {
    const en = P.esc(oppName(s));
    const rows = r.words.map((w, i) => {
      const by = r.res[i];
      return `<div class="ana-row${by === s.me.id ? " me" : by ? " opp" : ""}"><b>${P.esc(ptsWord(r, w))}</b><span>${by === s.me.id ? "✓ você" : by ? en : "—"}</span></div>`;
    }).join("");
    ptsSheet(s, r, { icon: r.kind === "p" ? "❓" : "🔤", extra: `<div class="ana-rows${r.kind === "p" ? " one" : ""}">${rows}</div>` });
  }
})();
