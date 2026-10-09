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
    termo: mini(12, 0), infinito: mini(12, 0),
    dueto: `<div style="display:flex;gap:4px">${mini(7, 0)}${mini(7, 2)}</div>`,
    quarteto: `<div style="display:grid;grid-template-columns:auto auto;gap:4px">${mini(7, 0)}${mini(7, 1)}${mini(7, 2)}${mini(7, 3)}</div>`,
    conexoes: `<div class="mini" style="grid-template-columns:repeat(4,10px);gap:3px">${["var(--present)", "var(--correct)", "var(--blue)", "var(--purple)"].map((c) => `<i style="width:10px;height:10px;background:${c}"></i>`.repeat(4)).join("")}</div>`,
    caca: `<div style="font-weight:600;font-size:12px;line-height:1.5;letter-spacing:6px;position:relative">CAS<br>OLE<br>PAJ<span style="position:absolute;left:-2px;top:6px;width:58px;height:10px;border-radius:9px;background:rgba(230,193,79,.6);transform:rotate(38deg);transform-origin:left"></span></div>`,
    reverso: `<div style="text-align:center"><b style="color:var(--accent);font-size:18px">?</b><div class="mini" style="grid-template-columns:repeat(5,9px)">${["--correct", "--absent", "--present", "--absent", "--correct"].map((c) => `<i style="width:9px;height:9px;background:var(${c})"></i>`).join("")}</div></div>`,
    definicao: `<div style="display:grid;gap:5px;justify-items:center"><i style="display:block;width:46px;height:5px;border-radius:9px;background:var(--muted);opacity:.6"></i><i style="display:block;width:34px;height:5px;border-radius:9px;background:var(--muted);opacity:.6"></i><div class="mini" style="grid-template-columns:repeat(4,11px)">${["--surface-high", "--present", "--surface-high", "--surface-high"].map((c) => `<i style="width:11px;height:11px;background:var(${c})"></i>`).join("")}</div></div>`,
    sinonimos: `<div style="display:grid;gap:3px;justify-items:center;font-size:9px;font-weight:600"><span class="pill-badge" style="font-size:9px;padding:2px 8px">BELO</span>=<span class="pill-badge new" style="font-size:9px;padding:2px 8px;background:var(--correct)">LINDO</span></div>`,
    desafiar: `<div style="text-align:center"><div style="font-size:22px;color:var(--present)">✉</div><div class="mini" style="grid-template-columns:repeat(5,8px)">${'<i style="width:8px;height:8px;background:var(--accent)"></i>'.repeat(5)}</div></div>`,
  };

  function home() {
    const today = P.dayKey();
    const badge = (done) => (done ? `<span class="pill-badge">Feito hoje ✓</span>` : `<span class="pill-badge new">Novo</span>`);
    const dd = (k) => P.store.get("daily-done-" + k) === today;
    const guess = [
      ["termo", "Termo", "Uma palavra nova por dia", badge(dd("termo"))],
      ["infinito", "Infinito", "Quantas palavras quiser", ""],
      ["dueto", "Dueto", "Duas palavras, 7 tentativas", badge(dd("dueto"))],
      ["quarteto", "Quarteto", "Quatro palavras, 9 tentativas", badge(dd("quarteto"))],
    ];
    const more = [
      ["conexoes", "Conexões", "Separe 16 palavras em 4 grupos", badge(P.store.get("conn-done") === today)],
      ["caca", "Caça-Palavras", "Ache as palavras do tema do dia", badge(P.store.get("ws-done") === today)],
      ["reverso", "Reverso", "O site tenta adivinhar a sua palavra", ""],
      ["definicao", "Qual é a Palavra?", "Descubra a palavra pela definição", ""],
      ["sinonimos", "Sinônimos", "Corrente de sinônimos contra o tempo", ""],
      ["desafiar", "Desafiar um amigo", "Escolha uma palavra e mande o link", ""],
    ];
    const card = ([k, t, s, b]) => `<button class="card" data-go="${k}"><span class="ico">${ICONS[k]}</span><span class="txt"><span class="name">${t} ${b}</span><span class="sub">${s}</span></span></button>`;
    app.innerHTML = `<div class="home-head"><div class="title"><h1>Palavreiro</h1><div class="mark small"><span></span><span></span><span></span><span></span><span></span></div></div>
        <button class="icon-btn filled" data-go="perfil" aria-label="Perfil"><svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-6 8-6s8 2 8 6z"/></svg></button></div>
      <div class="section-label">Adivinhe a palavra</div><div class="cards">${guess.map(card).join("")}</div>
      <div class="section-label">Mais jogos</div><div class="cards">${more.map(card).join("")}</div>
      <div class="footer">${P.creditHTML()}</div>`;
    app.querySelectorAll("[data-go]").forEach((b) => (b.onclick = () => P.go(b.dataset.go)));
  }

  function route() {
    const r = (location.hash.replace(/^#\/?/, "") || "").split("/")[0];
    app.innerHTML = "";
    document.querySelectorAll(".overlay").forEach((o) => o.remove());
    window.scrollTo(0, 0);
    const screens = {
      termo: () => P.games.termoScreen(app, "termo"),
      infinito: () => P.games.termoScreen(app, "infinito"),
      dueto: () => P.games.termoScreen(app, "dueto"),
      quarteto: () => P.games.termoScreen(app, "quarteto"),
      conexoes: () => P.games.conexoes(app),
      caca: () => P.games.caca(app),
      reverso: () => P.games.reverso(app),
      definicao: () => P.games.definicao(app),
      sinonimos: () => P.games.sinonimos(app),
      desafiar: () => P.games.challengeCreate(app),
      perfil: () => P.games.perfil(app),
    };
    if (r === "desafio") {
      const w = P.challenge.decode(sessionStorage.getItem("pv-challenge") || "");
      if (w) return P.games.termoScreen(app, "desafio", w);
    }
    (screens[r] || home)();
    app.querySelectorAll("[data-back]").forEach((b) => (b.onclick = () => P.go("")));
  }

  // Link de desafio (?d=...): abre direto o Termo com a palavra do amigo.
  const code = new URLSearchParams(location.search).get("d");
  if (code && P.challenge.decode(code)) {
    try { sessionStorage.setItem("pv-challenge", code); } catch (_) {}
    history.replaceState(null, "", location.pathname + "#/desafio");
  }

  // Botão voltar em telas criadas depois (os jogos assíncronos montam o topo depois).
  new MutationObserver(() => app.querySelectorAll("[data-back]").forEach((b) => (b.onclick = () => P.go("")))).observe(app, { childList: true, subtree: true });

  window.addEventListener("hashchange", route);
  route();

  // Abertura com créditos: some sozinha em ~2,8 s ou ao tocar fora do link.
  const sp = P.$("#splash");
  const close = () => { sp.classList.add("hide"); setTimeout(() => sp.remove(), 400); if (!P.store.get("seen-help") && !location.hash.includes("desafio")) { P.store.set("seen-help", true); P.games.help(); } };
  const t = setTimeout(close, 2800);
  sp.addEventListener("click", (e) => { if (!e.target.closest(".credit")) { clearTimeout(t); close(); } });
})();
