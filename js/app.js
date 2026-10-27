// Palavreiro (site): abertura, tela inicial e navegação entre os jogos.
(function () {
  "use strict";
  const app = P.$("#app");

  const mini = (cell, seed) => {
    const cols = ["var(--absent)", "var(--present)", "var(--correct)"];
    let s = `<div class="mini" style="grid-template-columns:repeat(3,${cell}px)">`;
    for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) {
      const v = (r * 3 + c + seed) % 5, col = r === 2 ? cols[2] : v === 0 ? cols[1] : v === 3 ? cols[2] : cols[0];
      s += `<i style="width:${cell}px;height:${cell}px;background:${col}"></i>`;
    }
    return s + "</div>";
  };
  const ICONS = {
    termo: mini(12, 0),
    dueto: `<div style="display:flex;gap:4px">${mini(7, 0)}${mini(7, 2)}</div>`,
    quarteto: `<div style="display:grid;grid-template-columns:auto auto;gap:4px">${mini(7, 0)}${mini(7, 1)}${mini(7, 2)}${mini(7, 3)}</div>`,
    conexoes: `<div class="mini" style="grid-template-columns:repeat(4,10px);gap:3px">${["var(--present)", "var(--correct)", "var(--blue)", "var(--purple)"].map((c) => `<i style="width:10px;height:10px;background:${c}"></i>`.repeat(4)).join("")}</div>`,
    caca: `<div style="font-weight:600;font-size:12px;line-height:1.5;letter-spacing:6px;position:relative">CAS<br>OLE<br>PAJ<span style="position:absolute;left:-2px;top:6px;width:58px;height:10px;border-radius:9px;background:rgba(230,193,79,.6);transform:rotate(38deg);transform-origin:left"></span></div>`,
    definicao: `<div style="display:grid;gap:5px;justify-items:center"><i style="display:block;width:46px;height:5px;border-radius:9px;background:var(--muted);opacity:.6"></i><i style="display:block;width:34px;height:5px;border-radius:9px;background:var(--muted);opacity:.6"></i><div class="mini" style="grid-template-columns:repeat(4,11px)">${["--surface-high", "--present", "--surface-high", "--surface-high"].map((c) => `<i style="width:11px;height:11px;background:var(${c})"></i>`).join("")}</div></div>`,
    // Sinônimo ou Antônimo: as duas mini pílulas antigas empilhadas.
    sinonimos: `<div class="sa-ico"><span style="background:var(--correct)">BELO = LINDO</span><span style="background:var(--red)">ALTO ≠ BAIXO</span></div>`,
    intruso: `<div class="mini" style="grid-template-columns:repeat(5,9px);gap:3px">${["--absent", "--absent", "--red", "--absent", "--absent"].map((c) => `<i style="width:9px;height:9px;background:var(${c})"></i>`).join("")}</div>`,
    ortografia: `<div style="display:flex;gap:5px;font-weight:800;font-size:15px"><span style="display:grid;place-items:center;width:24px;height:24px;border-radius:8px;background:var(--correct);color:var(--dark-text)">✓</span><span style="display:grid;place-items:center;width:24px;height:24px;border-radius:8px;background:var(--red);color:var(--dark-text)">✗</span></div>`,
    mestre: `<div style="display:grid;gap:2px;justify-items:center"><span style="font-size:17px;line-height:1">👑</span><span class="pill-badge" style="font-size:9px;padding:3px 9px;background:var(--accent);color:var(--dark-text)">TOQUE</span></div>`,
    amigo: `<div style="display:flex;gap:5px">${(() => {
      const g = (cells) => `<div class="mini" style="grid-template-columns:repeat(3,8px)">${cells.map((c) => `<i style="width:8px;height:8px;background:var(${c})"></i>`).join("")}</div>`;
      return g(["--absent", "--present", "--absent", "--absent", "--correct", "--absent", "--correct", "--correct", "--correct"]) + g(["--absent", "--absent", "--present", "--present", "--absent", "--absent", "--surface-high", "--surface-high", "--surface-high"]);
    })()}</div>`,
    // Trivia: a roleta das 6 categorias.
    trivia: `<div class="tv-ico" aria-hidden="true"><i></i><b>?</b></div>`,
    // Memória: duas cartas viradas e um par verde.
    memoria: `<div class="mem-ico" aria-hidden="true"><i></i><i class="ok">AB</i><i class="ok">AB</i><i></i></div>`,
    // Matiz: três bolhas de cor sobrepostas; Pódio: degraus 2-1-3; Ampulheta: ampulheta em CSS.
    matiz: `<div class="mz-ico" aria-hidden="true"><i></i><i></i><i></i></div>`,
    podio: `<div class="pd-ico" aria-hidden="true"><i>2</i><i>1</i><i>3</i></div>`,
    ampulheta: `<div class="amp-ico" aria-hidden="true"><i></i><i></i></div>`,
    // Quadrante (Sudoku): pedaço de grade com números; Xeque (Xadrez): o cavalo num tabuleiro.
    quadrante: `<div class="sdk-ico" aria-hidden="true">${[5, 3, "", "", 7, "", 6, "", 9].map((d, i) => `<i class="${i === 4 ? "on" : ""}">${d}</i>`).join("")}</div>`,
    xeque: `<div class="xz-ico" aria-hidden="true"><i></i><i></i><i></i><i></i><b>♞&#xFE0E;</b></div>`,
    desafiar: `<div style="text-align:center"><div style="font-size:22px;color:var(--present)">✉</div><div class="mini" style="grid-template-columns:repeat(5,8px)">${'<i style="width:8px;height:8px;background:var(--accent)"></i>'.repeat(5)}</div></div>`,
  };

  function home() {
    const today = P.dayKey();
    const badge = (done) => (done ? `<span class="pill-badge">Feito hoje ✓</span>` : `<span class="pill-badge new">Novo</span>`);
    const dd = (k) => P.store.get("daily-done-" + k) === today;
    const guess = [
      ["termo", "Termo", "Uma palavra nova por dia", badge(dd("termo"))],
      ["dueto", "Dueto", "Duas palavras, 7 tentativas", badge(dd("dueto"))],
      ["quarteto", "Quarteto", "Quatro palavras, 9 tentativas", badge(dd("quarteto"))],
    ];
    const more = [
      ["conexoes", "Conexões", "Separe 16 palavras em 4 grupos", badge(P.store.get("conn-done") === today)],
      ["caca", "Caça-Palavras", "Ache as palavras do tema do dia", badge(P.store.get("ws-done") === today)],
      ["intruso", "Intruso", "Ache a palavra que não pertence ao grupo", ""],
      ["ortografia", "Certo ou Errado", "A palavra está escrita certo?", ""],
      ["mestre", "Mestre Mandou", "Obedeça só quando o mestre mandar", ""],
      ["definicao", "Qual é a Palavra?", "Descubra a palavra pela definição", ""],
      ["sinonimos", "Sinônimo ou Antônimo", "Mesmo sentido ou o contrário? Leia a pergunta!", ""],
      ["trivia", "Trivia", "Gire a roleta e junte as 6 medalhas", ""],
      ["memoria", "Jogo da Memória", "Ache os pares de sinônimos", ""],
      ["matiz", "Matiz", "Memorize a cor e recrie-a", badge(dd("matiz"))],
      ["podio", "Pódio", "Monte o top 5 do ranking", badge(dd("podio"))],
      ["ampulheta", "Ampulheta", "O que veio antes? Monte a linha do tempo", badge(dd("ampulheta"))],
      ["quadrante", "Quadrante", "Sudoku: um por nível todo dia, ou sem fim", ""],
      ["xeque", "Xeque", "Xadrez contra o computador", ""],
      ["amigo", "Jogar com amigo", "Partida ao vivo: quem acertar primeiro ganha", ""],
    ];
    // Celular: abas por categoria (layout B). Computador: grade de ícones + prévia ao lado (layout E).
    const CATS = [
      ["p", "Palavras", "Jogos de Palavras", ["termo", "dueto", "quarteto", "conexoes", "caca", "intruso", "ortografia", "definicao", "sinonimos", "memoria"]],
      ["l", "Lógica", "Jogos de Lógica", ["mestre", "matiz", "quadrante", "xeque"]],
      ["c", "Conhecimento", "Jogos de Conhecimento", ["trivia", "podio", "ampulheta"]],
    ];
    const info = {};
    guess.concat(more).forEach(([k, t, s, b]) => (info[k] = { t, s, b }));
    const catOf = {};
    CATS.forEach(([id, , , ks]) => ks.forEach((k) => (catOf[k] = id)));
    let cat = P.store.get("home-cat", "p");
    if (!CATS.some((c) => c[0] === cat)) cat = "p";
    let sel = P.store.get("home-sel", "termo");
    if (!catOf[sel]) sel = "termo";
    const wide = () => window.matchMedia("(min-width: 900px)").matches;
    const tile = (k) => `<button class="hm-card" data-k="${k}"><span class="ico">${ICONS[k]}</span><span class="hm-name">${info[k].t}</span><span class="hm-sub">${info[k].s}</span>${info[k].b ? `<span class="hm-badge">${info[k].b}</span>` : ""}</button>`;
    const ICO_JOGOS = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="8" height="8" rx="2.5"/><rect x="13" y="3" width="8" height="8" rx="2.5"/><rect x="3" y="13" width="8" height="8" rx="2.5"/><rect x="13" y="13" width="8" height="8" rx="2.5"/></svg>`;
    const ICO_AMIGOS = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c0-3.5 3-5.5 6.5-5.5s6.5 2 6.5 5.5"/><circle cx="17" cy="9" r="2.5"/><path d="M17 14.5c2.5 0 4.5 1.5 4.5 4"/></svg>`;
    const ICO_PERFIL = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 4-6 8-6s8 2 8 6"/></svg>`;
    const navLinks = `<button class="on" data-nav="" aria-current="page">${ICO_JOGOS}<span>Jogos</span></button><button data-nav="amigo">${ICO_AMIGOS}<span>Amigos</span></button><button data-nav="perfil">${ICO_PERFIL}<span>Perfil</span></button>`;
    app.classList.add("home-wide");
    app.innerHTML = `<div class="home-head"><div class="title"><h1>Palavreiro</h1><div class="mark small"><span></span><span></span><span></span><span></span><span></span></div></div>
        <nav class="hm-top" aria-label="Navegação principal">${navLinks}</nav></div>
      ${P.todayPanel()}
      <div class="hm-tabs" role="tablist" aria-label="Categorias de jogos">${CATS.map(([id, short, , ks]) => `<button role="tab" class="hm-tab c-${id}" data-cat="${id}" aria-selected="${id === cat}">${short}<small>${ks.length} jogos</small></button>`).join("")}</div>
      <div class="hm-body"><div class="hm-main">${CATS.map(([id, , name, ks]) => `<section class="hm-cat c-${id}${id === cat ? " on" : ""}" data-cat="${id}"><h2 class="hm-lbl">${name}</h2><div class="hm-grid">${ks.map(tile).join("")}</div></section>`).join("")}</div>
        <aside class="hm-prev" aria-live="polite"></aside></div>
      <div class="footer">${P.creditHTML()}</div>
      <nav class="hm-nav" aria-label="Navegação principal">${navLinks}</nav>`;
    const prev = app.querySelector(".hm-prev");
    const showPrev = () => {
      const c = CATS.find((x) => x[0] === catOf[sel]);
      prev.innerHTML = `<div class="hm-prev-in"><span class="hm-prev-ico ico">${ICONS[sel]}</span><span class="hm-pill c-${c[0]}">${c[2]}</span>
        <h2>${info[sel].t}</h2><p>${info[sel].s}</p>${info[sel].b}
        <button class="pill hm-play" data-go="${sel}">▶ Jogar</button><button class="pill hm-friend" data-go="amigo">Jogar com amigo</button></div>`;
      prev.querySelectorAll("[data-go]").forEach((b) => (b.onclick = () => P.go(b.dataset.go)));
      app.querySelectorAll(".hm-card").forEach((b) => b.setAttribute("aria-pressed", b.dataset.k === sel));
    };
    showPrev();
    app.querySelectorAll(".hm-card").forEach((b) => (b.onclick = () => {
      if (!wide()) return P.go(b.dataset.k);
      sel = b.dataset.k; P.store.set("home-sel", sel); showPrev(); // computador: mostra a prévia, o botão Jogar abre
    }));
    app.querySelectorAll(".hm-tab").forEach((b) => (b.onclick = () => {
      cat = b.dataset.cat; P.store.set("home-cat", cat);
      app.querySelectorAll(".hm-tab").forEach((t) => t.setAttribute("aria-selected", t.dataset.cat === cat));
      app.querySelectorAll(".hm-cat").forEach((s) => s.classList.toggle("on", s.dataset.cat === cat));
    }));
    app.querySelectorAll("[data-nav]").forEach((b) => (b.onclick = () => b.dataset.nav && P.go(b.dataset.nav)));
    P.checkAllDone();
  }

  // Botão voltar: de um desafio do arquivo volta para o arquivo; do resto, para o início.
  const backTo = () => (/^#\/arquivo\/./.test(location.hash) ? "arquivo" : "");
  const wireBack = () => app.querySelectorAll("[data-back]").forEach((b) => (b.onclick = () => P.go(backTo())));

  function route() {
    const parts = (location.hash.replace(/^#\/?/, "") || "").split("/"), r = parts[0];
    if (P.mp) P.mp.leave(); // sai da partida com amigo (manda "bye")
    app.innerHTML = "";
    app.classList.remove("home-wide");
    document.querySelectorAll(".overlay").forEach((o) => o.remove());
    window.scrollTo(0, 0);
    const screens = {
      termo: () => P.games.termoScreen(app, "termo"),
      infinito: () => P.games.termoScreen(app, "infinito"), // aba Infinito do Termo
      dueto: () => P.games.termoScreen(app, "dueto"),
      quarteto: () => P.games.termoScreen(app, "quarteto"),
      conexoes: () => P.games.conexoes(app),
      mestre: () => P.games.mestre(app),
      caca: () => P.games.caca(app),
      definicao: () => P.games.definicao(app),
      sinonimos: () => P.games.sinonimos(app),
      antonimos: () => P.games.antonimos(app),
      desafiar: () => P.games.challengeCreate(app),
      amigo: () => (parts[1] === "entrar" ? P.games.mpJoin(app) : (parts[1] === "sala" && P.games.mpResume(app)) || P.games.mpCreate(app)),
      perfil: () => P.games.perfil(app),
      intruso: () => P.games.intruso(app),
      ortografia: () => P.games.ortografia(app),
      trivia: () => P.games.trivia(app),
      memoria: () => P.games.memoria(app),
      matiz: () => P.games.matiz(app),
      podio: () => P.games.podio(app),
      ampulheta: () => P.games.ampulheta(app),
      quadrante: () => P.games.sudoku(app),
      sudoku: () => P.games.sudoku(app),
      xeque: () => P.games.xadrez(app),
      xadrez: () => P.games.xadrez(app),
      arquivo: () => (parts[1] && P.games.arquivoGame(app, parts[1], parts[2])) || P.games.arquivo(app),
    };
    if (r === "desafio") {
      const w = P.challenge.decode(sessionStorage.getItem("pv-challenge") || "");
      if (w) return P.games.termoScreen(app, "desafio", w);
    }
    (screens[r] || home)();
    wireBack();
  }

  // Link de desafio (?d=...): abre direto o Termo com a palavra do amigo.
  const code = new URLSearchParams(location.search).get("d");
  if (code && P.challenge.decode(code)) {
    try { sessionStorage.setItem("pv-challenge", code); } catch (_) {}
    history.replaceState(null, "", location.pathname + "#/desafio");
  }

  // Link de partida com amigo (?mp=SALA-MODO-SEED): abre a tela de entrar.
  const mpCode = new URLSearchParams(location.search).get("mp");
  if (mpCode && (P.mp.parse(mpCode) || P.mp.gone(mpCode))) { // link antigo da Bomba: a tela de entrar avisa que o jogo saiu
    try { sessionStorage.setItem("pv-mp-join", mpCode.trim().toLowerCase()); } catch (_) {}
    history.replaceState(null, "", location.pathname + "#/amigo/entrar");
  }

  // Sala com amigo guardada (vale 10 minutos): ao abrir o site no início, em #/amigo ou no link da MESMA sala,
  // volta direto para ela (o anfitrião costuma sair para mandar o link e o navegador pode descartar a aba).
  const mpSaved = P.mp.saved();
  if (mpSaved) {
    const h = location.hash.replace(/^#\/?/, "");
    let back = h === "" || h === "amigo" || h === "amigo/sala";
    if (h === "amigo/entrar") {
      let j = null, raw = null; try { raw = sessionStorage.getItem("pv-mp-join"); j = P.mp.parse(raw); } catch (_) {}
      back = j ? j.room === mpSaved.room : !P.mp.gone(raw);
    }
    if (back) history.replaceState(null, "", location.pathname + "#/amigo/sala");
  }

  // Botão voltar em telas criadas depois (os jogos assíncronos montam o topo depois).
  new MutationObserver(wireBack).observe(app, { childList: true, subtree: true });

  window.addEventListener("hashchange", route);
  route();

  // Abertura: a bandeira do Brasil em quadradinhos tremula e vira a marca (mesma animação do app).
  const sp = P.$("#splash");
  (function flag() {
    const cv = P.$("#flag", sp); if (!cv) return;
    const dpr = devicePixelRatio || 1, W = cv.clientWidth, H = cv.clientHeight;
    cv.width = W * dpr; cv.height = H * dpr;
    const g = cv.getContext("2d"); g.scale(dpr, dpr);
    const G = "#5FB873", Y = "#E6C14F", B = "#4A72CF";
    const diamond = new Set(["0,3", "1,2", "1,3", "1,4", "2,1", "2,2", "2,4", "2,5", "3,2", "3,3", "3,4", "4,3"]);
    const keepers = ["2,0", "2,2", "2,3", "2,4", "2,6"];
    const color = (r, c) => (r === 2 && c === 3 ? B : diamond.has(r + "," + c) ? Y : G);
    const ease = (x) => { const t = Math.min(1, Math.max(0, x)); return t * t * (3 - 2 * t); };
    const s = 30, gap = 7, fs = s * 0.82, fg = fs * 0.2, cx = W / 2, cy = H / 2;
    const flagW = 7 * fs + 6 * fg, flagH = 5 * fs + 4 * fg, rowW = 5 * s + 4 * gap;
    const rr = (x, y, z, col, a = 1) => { g.globalAlpha = a; g.fillStyle = col; g.beginPath(); g.roundRect(x, y, z, z, z * 0.3); g.fill(); };
    const t0 = performance.now();
    const title = P.$("#splash-title"), credit = P.$("#splash-credit");
    const frame = (now) => {
      if (!document.body.contains(cv)) return;
      const t = (now - t0) / 1000, m = ease(t - 1), wave = 1 - m;
      g.clearRect(0, 0, W, H);
      for (let r = 0; r < 5; r++) for (let c = 0; c < 7; c++) {
        const k = keepers.indexOf(r + "," + c);
        const fx = cx - flagW / 2 + c * (fs + fg), fy = cy - flagH / 2 + r * (fs + fg) + Math.sin(t * 5 - c * 0.7) * fs * 0.2 * wave;
        if (k >= 0) {
          const hop = m >= 1 ? Math.max(0, Math.sin(((t - 2) / 1.6 - k * 0.12) * 2 * Math.PI)) * s * 0.35 : 0;
          const tx = cx - rowW / 2 + k * (s + gap), ty = cy - s / 2 - hop, z = fs + (s - fs) * m;
          rr(fx + (tx - fx) * m, fy + (ty - fy) * m, z, color(r, c));
        } else if (m < 1) {
          const z = fs * (1 - m);
          rr(fx + (cx - fx) * m * 0.35 + (fs - z) / 2, fy + (cy - fy) * m * 0.35 + (fs - z) / 2, z, color(r, c), 1 - m);
        }
      }
      g.globalAlpha = 1;
      const a = Math.min(1, Math.max(0, (t - 1.7) / 0.5));
      title.style.opacity = a; title.style.transform = `translateY(${(1 - a) * 20}px)`; credit.style.opacity = a;
      requestAnimationFrame(frame);
    };
    requestAnimationFrame(frame);
  })();
  const close = () => { sp.classList.add("hide"); setTimeout(() => sp.remove(), 400); if (!P.store.get("seen-help") && !location.hash.includes("desafio") && !location.hash.includes("amigo/entrar")) { P.store.set("seen-help", true); P.games.help(); } };
  const t = setTimeout(close, 3600);
  sp.addEventListener("click", (e) => { if (!e.target.closest(".credit")) { clearTimeout(t); close(); } });
})();
