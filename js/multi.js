// Jogar com amigo (site): partida ao vivo pelo ntfy.sh, compatível com o app Android.
// Link: ?mp=SALA-MODO-SEED · tópico "palavreiro-mp-" + SALA · mensagens JSON (hello, start, row, end, again, bye).
(function () {
  "use strict";
  const NTFY = "https://ntfy.sh/";
  const MODES = {
    t: { key: "termo", name: "Termo", boards: 1 },
    d: { key: "dueto", name: "Dueto", boards: 2 },
    q: { key: "quarteto", name: "Quarteto", boards: 4 },
  };
  const ABC = "abcdefghijklmnopqrstuvwxyz0123456789";
  const randStr = (n) => { const b = new Uint32Array(n); crypto.getRandomValues(b); return Array.from(b, (x) => ABC[x % 36]).join(""); };
  const newSeed = () => { const b = new Uint32Array(1); crypto.getRandomValues(b); return b[0] % 2147483647; };
  const clockFmt = (ms) => { const s = Math.max(0, Math.round((ms || 0) / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`; };
  const triesTxt = (n) => `${n} ${n === 1 ? "tentativa" : "tentativas"}`;

  P.mp = {
    MODES,
    link: (room, m, seed) => location.origin + location.pathname.replace(/index\.html$/, "") + `?mp=${room}-${m}-${seed.toString(36)}`,
    /** "sala-modo-seed" → {room, m, seed} ou null. */
    parse(code) {
      const x = /^([a-z0-9]{12})-([tdq])-([0-9a-z]{1,7})$/.exec(String(code || "").trim().toLowerCase());
      if (!x) return null;
      const seed = parseInt(x[3], 36);
      return seed >= 0 && seed <= 2147483646 ? { room: x[1], m: x[2], seed } : null;
    },
  };

  let S = null; // sessão atual

  // ---------- sala guardada por 10 minutos ----------
  // localStorage "pv-mp-session" = {code, host, id, name, at}: o anfitrião sai para mandar o link (WhatsApp)
  // e o navegador pode descartar a aba; ao voltar, o site reabre a mesma sala com o mesmo id de jogador.
  const KEEP_MS = 600000;
  const codeOf = (s) => `${s.room}-${s.m}-${s.seed.toString(36)}`;
  function remember(s) {
    s.at = s.at || Date.now();
    P.store.set("mp-session", { code: codeOf(s), host: !!s.me.host, id: s.me.id, name: s.me.name, at: s.at });
  }
  function forget() { try { localStorage.removeItem("pv-mp-session"); } catch (_) {} }
  /** Sessão guardada ainda válida: {room, m, seed, host, id, name, at} ou null (a vencida é apagada). */
  function saved() {
    const v = P.store.get("mp-session", null);
    const info = v && typeof v === "object" ? P.mp.parse(v.code) : null;
    const ok = info && typeof v.id === "string" && /^[a-z0-9]{8}$/.test(v.id) && typeof v.name === "string" && v.name.length >= 2
      && typeof v.at === "number" && Date.now() < v.at + KEEP_MS && v.at <= Date.now() + 60000;
    if (!ok) { if (v != null) forget(); return null; }
    return Object.assign(info, { host: v.host === true, id: v.id, name: v.name.slice(0, 16), at: v.at });
  }
  P.mp.saved = saved;

  /** Encerra a sessão. Sair de propósito (padrão) também apaga a sala guardada; keep = true mantém. */
  function leave(sendBye = true, keep = false) {
    if (!S) return;
    if (!keep) forget();
    const s = S; S = null;
    s.closed = true;
    clearTimeout(s.retry); clearInterval(s.countTimer); clearTimeout(s.sheetTimer); clearInterval(s.ttlTimer);
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
      publish({ t: "hello", id: s.me.id, n: s.me.name, host: !!s.me.host });
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

  function session({ room, m, seed, host, name, root }) {
    leave(false, true);
    S = { room, m, seed, mode: MODES[m], topic: "palavreiro-mp-" + room, me: { id: randStr(8), name, host }, root,
      hostHello: null, guestHello: null, started: false, startSent: false, joined: false, full: false, oppLeft: false,
      seen: new Set(), lastId: null, es: null, closed: false, queue: Promise.resolve(), round: null, view: null,
      series: { me: 0, opp: 0 } };
    return S;
  }
  const opp = (s) => (s.me.host ? s.guestHello : s.hostHello);

  // ---------- mensagens ----------
  function handle(s, m, live) {
    if (m.t === "hello" && typeof m.id === "string") {
      const who = { id: m.id, name: String(m.n || "Amigo").slice(0, 16) };
      if (m.id === s.me.id) s.sawMyHello = true;
      if (m.host === true) { if (!s.hostHello) s.hostHello = who; }
      else if (!s.guestHello) s.guestHello = who;
      if (s.me.host && s.guestHello && s.guestHello.id === m.id && !s.started && !s.startSent) {
        // Lendo o histórico (sala retomada): espera o fim, porque o start pode já estar lá.
        if (!s.loaded && !live) s.pendingStart = true;
        else sendStart(s);
      }
      if (!s.me.host && s.joined && s.guestHello && s.guestHello.id !== s.me.id) markFull(s);
      if (s.view === "join") joinText(s);
      if (s.view === "joinwait" && m.host === true) { const t = P.$("[data-waittxt]", s.root); if (t) t.innerHTML = `Esperando <b>${P.esc(s.hostHello.name)}</b> começar…`; }
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
      // "s":1 = série nova (o placar volta a 0 × 0). Série já decidida também recomeça.
      if (m.s === 1 || seriesOver(s)) s.series = { me: 0, opp: 0 };
      beginRound(s, +m.at || Date.now(), live);
    } else if (m.t === "row") {
      const r = s.round, o = opp(s);
      if (!r || !o || m.id !== o.id || typeof m.m !== "string") return;
      const idx = +m.r;
      if (!(idx >= 0 && idx < 9) || r.opp.rows[idx]) return;
      r.opp.rows[idx] = m.m.split("|"); r.opp.fresh = idx;
      drawStrip(s);
    } else if (m.t === "end") {
      const r = s.round, o = opp(s);
      if (!r || r.ends.some((e) => e.id === m.id) || (m.id !== s.me.id && (!o || m.id !== o.id))) return;
      r.ends.push({ id: m.id, won: m.won === true, tries: +m.tries || 0, ms: +m.ms || 0 });
      decide(s); drawStrip(s);
    } else if (m.t === "bye") {
      const o = opp(s);
      if (!o || m.id !== o.id) return;
      s.oppLeft = true;
      if (s.round) { decide(s); drawStrip(s); }
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

  /** 1. Criar partida. */
  P.games.mpCreate = function (root) {
    leave();
    let m = P.store.get("mp-mode", "d");
    if (!MODES[m]) m = "d";
    const body = shell(root, "Jogar com amigo");
    body.innerHTML = `<div class="mp-card">
        ${duoIcon}
        <h2>Partida ao vivo</h2>
        <p class="muted mp-lead">Vocês recebem as mesmas palavras. Quem acertar primeiro ganha. Melhor de 3: quem vencer 2 rodadas leva o troféu 🏆</p>
        ${nameField()}
        <div class="mp-field"><span>Modo</span><div class="seg mp-modes">${Object.entries(MODES).map(([k, v]) => `<button data-m="${k}" class="${k === m ? "on" : ""}">${v.name}</button>`).join("")}</div></div>
        <button class="pill wide" data-create>Criar partida</button>
        <div class="mp-or"><span>ou entre numa partida</span></div>
        <label class="mp-field"><span>Link da partida</span>
          <input class="mp-input" data-paste placeholder="Cole aqui o link que seu amigo mandou" autocomplete="off" autocapitalize="off" spellcheck="false"></label>
        <p class="mp-err hidden" data-paste-err>Esse link não é de uma partida do Palavreiro.</p>
        <div class="row-btns"><button class="pill ghost" data-paste-btn>Colar</button><button class="pill accent" data-enter>Entrar na partida</button></div>
      </div>`;
    body.querySelectorAll("[data-m]").forEach((b) => (b.onclick = () => { m = b.dataset.m; P.store.set("mp-mode", m); P.fx.type(); body.querySelectorAll("[data-m]").forEach((x) => x.classList.toggle("on", x === b)); }));
    P.$("[data-name]", body).addEventListener("keydown", (e) => { if (e.key === "Enter") P.$("[data-create]", body).click(); });
    // Entrar numa partida colando o link (inteiro, só o código, ou no meio de uma mensagem).
    const fromPasted = (t) => {
      t = String(t || "").trim();
      const a = /[?&]mp=([A-Za-z0-9-]+)/.exec(t); if (a) return P.mp.parse(a[1]) ? a[1].toLowerCase() : null;
      const b = /\b([a-z0-9]{12}-[tdq]-[0-9a-z]{1,7})\b/.exec(t.toLowerCase()); return b && P.mp.parse(b[1]) ? b[1] : null;
    };
    const err = P.$("[data-paste-err]", body), input = P.$("[data-paste]", body);
    const enter = (t) => {
      const code = fromPasted(t);
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
      const s = session({ room: randStr(12), m, seed: newSeed(), host: true, name, root });
      s.joined = true;
      remember(s);
      waiting(s);
      connect(s);
      publish({ t: "hello", id: s.me.id, n: name, host: true });
    };
  };

  /** 2. Esperando o convidado (anfitrião). */
  function waiting(s) {
    s.view = "wait";
    const link = P.mp.link(s.room, s.m, s.seed);
    const body = shell(s.root, "Jogar com amigo");
    body.innerHTML = `<div class="mp-card">
        <span class="pill-badge mp-mode-badge">${s.mode.name}</span>
        <h2>Mande o link para seu amigo</h2>
        <div class="mp-link" data-link>${P.esc(link)}</div>
        <div class="row-btns"><button class="pill" data-share>Compartilhar</button><button class="pill ghost" data-copy>Copiar link</button></div>
        ${qrBlock(link)}
        <div data-ttlbox><p class="mp-ttl" data-ttl></p></div>
        <div class="mp-wait" data-wait><div class="mark small bounce"><span></span><span></span><span></span><span></span><span></span></div><p>Esperando seu amigo entrar…</p></div>
      </div>`;
    P.$("[data-share]", body).onclick = () => P.share(`Bora jogar ${s.mode.name} comigo no Palavreiro? Quem acertar primeiro ganha: ${link}`);
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
    let info = null;
    try { info = P.mp.parse(sessionStorage.getItem("pv-mp-join")); } catch (_) {}
    if (!info) { P.toast("Link de partida inválido"); return P.games.mpCreate(root); }
    if (S && S.room === info.room && S.root === root && S.view) return; // já está nesta sala
    const s = session({ room: info.room, m: info.m, seed: info.seed, host: false, name: "", root });
    s.view = "join";
    const body = shell(root, "Jogar com amigo");
    body.innerHTML = `<div class="mp-card">
        ${duoIcon}
        <h2 data-invite>Você foi chamado para um ${s.mode.name}</h2>
        <p class="muted mp-lead">Partida ao vivo: quem acertar primeiro ganha. Melhor de 3: quem vencer 2 rodadas leva o troféu 🏆</p>
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
      publish({ t: "hello", id: s.me.id, n: name, host: false });
    };
    s.onLoaded = () => joinText(s);
    connect(s);
  };
  /** Volta para a sala guardada (mesmo id de jogador). Devolve false se não há sala válida. */
  P.games.mpResume = function (root) {
    const sv = saved();
    if (!sv) return false;
    const s = session({ room: sv.room, m: sv.m, seed: sv.seed, host: sv.host, name: sv.name, root });
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
    if (h) h.innerHTML = s.hostHello ? `<b>${P.esc(s.hostHello.name)}</b> te chamou para um ${s.mode.name}` : `Você foi chamado para um ${s.mode.name}`;
  }
  function joinWait(s) {
    s.view = "joinwait";
    const body = P.$("[data-mpbody]", s.root);
    body.innerHTML = `<div class="mp-card center"><span class="pill-badge mp-mode-badge">${s.mode.name}</span>
        <div class="mp-wait"><div class="mark small bounce"><span></span><span></span><span></span><span></span><span></span></div>
        <p data-waittxt>Esperando ${s.hostHello ? `<b>${P.esc(s.hostHello.name)}</b>` : "o anfitrião"} começar…</p></div></div>`;
  }

  // ---------- partida ----------
  function beginRound(s, at, live) {
    clearInterval(s.countTimer); clearTimeout(s.sheetTimer);
    document.querySelectorAll(".overlay").forEach((o) => o.remove());
    const now = Date.now();
    // Contagem 3-2-1: usa o "at" do anfitrião quando o relógio bate; se não, conta a partir da chegada.
    let delay = at + 3000 - now;
    if (live && Math.abs(now - at) > 2500) delay = 3000;
    delay = Math.max(0, Math.min(3000, delay));
    const o = opp(s);
    s.round = { seed: s.seed, startAt: now + delay, opp: { rows: [], fresh: -1 }, ends: [], result: null, sentEnd: false, answers: P.mpWords(s.seed, s.mode.boards) };
    s.view = "game";
    const r = s.round;
    r.api = P.games.termoScreen(s.root, s.mode.key, null, null, {
      mp: {
        mode: s.mode.key, answers: r.answers,
        title: `Você × ${o ? o.name : "Amigo"}`,
        extra: `<span class="mp-score" data-score></span>`,
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

  // ---------- melhor de 3 ----------
  const WINS = 2;
  const seriesOver = (s) => s.series.me >= WINS || s.series.opp >= WINS;
  const seriesWinner = (s) => (s.series.me >= WINS ? "me" : s.series.opp >= WINS ? "opp" : null);
  const score = (s) => `${s.series.me} × ${s.series.opp}`;
  /** Placar da série no topo, ao lado do título. */
  function drawScore(s) {
    const el = s.root && P.$("[data-score]", s.root); if (!el) return;
    el.classList.toggle("over", seriesOver(s));
    el.innerHTML = `<b>${seriesOver(s) ? "🏆 " : ""}${score(s)}</b><small>melhor de 3</small>`;
  }

  function countdown(s, r) {
    const el = P.h(`<div class="mp-count" aria-live="assertive"><b></b></div>`);
    const tick = () => {
      if (S !== s || s.round !== r) { clearInterval(s.countTimer); el.remove(); return; }
      const left = r.startAt - Date.now();
      if (left <= 0) {
        clearInterval(s.countTimer);
        el.querySelector("b").textContent = "Já!"; el.classList.add("go");
        setTimeout(() => el.remove(), 450);
        r.api && r.api.redraw(); return;
      }
      const n = String(Math.ceil(left / 1000));
      const b = el.querySelector("b");
      if (b.textContent !== n) { b.textContent = n; b.classList.remove("beat"); void b.offsetWidth; b.classList.add("beat"); P.fx.type(); }
    };
    if (r.startAt - Date.now() <= 0) return;
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
    const head = champ
      ? `<div class="mp-trophy${champ === "me" ? "" : " theirs"}" aria-hidden="true">🏆</div><h2>${champ === "me" ? "Você levou a série!" : `${P.esc(oname)} levou a série`}</h2>
         <p class="mp-series gold">Melhor de 3 · ${score(s)}</p>
         <p class="subtitle">${r.result === "me" ? "Você venceu a última rodada. " : r.result === "opp" ? `${P.esc(oname)} venceu a última rodada. ` : ""}${words}</p>`
      : `<h2>${r.result === "me" ? "Você venceu a rodada!" : r.result === "opp" ? `${P.esc(oname)} venceu a rodada` : "Empate"}</h2>
         <p class="subtitle">${words}</p>
         <p class="mp-series">Série: Você ${score(s)} ${P.esc(oname)} · melhor de 3</p>`;
    const line = (who, e, left) => {
      const res = e ? (e.won ? triesTxt(e.tries) : "errou") : left ? "saiu" : "não terminou";
      return `<div class="mp-res${e && e.won ? " ok" : ""}"><b>${who}</b><span>${res}</span><small>${e ? clockFmt(e.ms) : "–"}</small></div>`;
    };
    const ui = P.sheet(`${head}
      <div class="mp-results">${line("Você", mine, false)}${line(P.esc(oname), theirs, s.oppLeft)}</div>
      <div class="row-btns wrap"><button class="pill ghost" data-share>Compartilhar</button><button class="pill ghost" data-replay>▶ Replay</button></div>
      <div class="row-btns" style="margin-top:10px"><button class="pill" data-again ${s.me.host && !s.oppLeft ? "" : "disabled"}>${champ ? "Nova série" : "Próxima rodada"}</button><button class="pill ghost" data-exit>Sair</button></div>
      ${s.me.host ? (s.oppLeft ? `<p class="note">${P.esc(oname)} saiu da partida.</p>` : "") : `<p class="note">Esperando o anfitrião</p>`}`);
    P.$("[data-share]", ui.el).onclick = () => {
      const t = champ === "me" ? `Levei a série por ${score(s)} 🏆`
        : champ === "opp" ? `Perdi a série por ${s.series.opp} × ${s.series.me} ⚔️`
        : (r.result === "me" ? `Venci em ${triesTxt(mine ? mine.tries : 0)} ⚔️` : r.result === "opp" ? "Perdi ⚔️" : "Empate ⚔️") + ` (série ${score(s)})`;
      P.share(`Palavreiro · Partida com ${oname}\n${t}`);
    };
    P.$("[data-replay]", ui.el).onclick = () => { ui.close(); r.api && r.api.replay(); };
    P.$("[data-exit]", ui.el).onclick = () => { ui.close(); leave(); P.go(""); };
    P.$("[data-again]", ui.el).onclick = () => {
      if (!s.me.host || s.oppLeft) return;
      P.$("[data-again]", ui.el).disabled = true;
      const msg = { t: "again", seed: newSeed().toString(36), at: Date.now() };
      if (champ) msg.s = 1;
      publish(msg);
    };
  }
})();
