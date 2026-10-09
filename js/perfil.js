// Perfil (site): abas Resumo (calendário, estatísticas, evolução, recordes), Conquistas e Ajustes.
(function () {
  "use strict";
  const MONTHS = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"];
  const TABS = ["Resumo", "Conquistas", "Ajustes"];
  const SITE_VERSION = "12";

  P.games.perfil = function (root) {
    const modes = [["termo", "Termo", 6], ["infinito", "Infinito", 6], ["dueto", "Dueto", 7], ["quarteto", "Quarteto", 9]];
    const evoKinds = [["termo", "Termo", "tentativas"], ["conn", "Conexões", "erros"], ["caca", "Caça", "tempo"]];
    let tab = 0, page = 0, evo = "termo";
    try { page = Math.max(0, Math.min(2, +sessionStorage.getItem("pv-profile-tab") || 0)); } catch (_) {}
    const now = new Date();
    let ym = [now.getFullYear(), now.getMonth()];
    root.innerHTML = P.topbar("Perfil") + `<div data-head></div><div class="seg ptabs" role="tablist" data-tabs></div><div data-body></div><div class="footer">${P.creditHTML()}</div>`;
    const body = P.$("[data-body]", root);
    const g = (k) => P.store.get(k, 0);

    function head() {
      const act = P.store.get("activity", {}), days = Object.keys(act).length, games = Object.values(act).reduce((a, b) => a + b, 0);
      const ach = P.achievements(), ok = ach.filter((a) => a.ok).length, t = P.stats("termo");
      P.$("[data-head]", root).innerHTML = `<div class="phead">
        <div class="avatar" aria-hidden="true"><svg viewBox="0 0 24 24" fill="currentColor"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 4-6 8-6s8 2 8 6z"/></svg></div>
        <div><b>Seu progresso</b><small>${days} ${days === 1 ? "dia jogado" : "dias jogados"} · ${games} ${games === 1 ? "jogo" : "jogos"}</small>
        <small>🔥 sequência ${t.streak} · 🏅 ${ok} de ${ach.length} conquistas</small></div></div>`;
      P.$("[data-tabs]", root).innerHTML = TABS.map((n, i) => `<button role="tab" aria-selected="${i === page}" class="${i === page ? "on" : ""}" data-page="${i}">${n}</button>`).join("");
      root.querySelectorAll("[data-page]").forEach((b) => (b.onclick = () => { page = +b.dataset.page; try { sessionStorage.setItem("pv-profile-tab", page); } catch (_) {} draw(); }));
    }

    function calendar() {
      const act = P.store.get("activity", {}), termo = P.store.get("termo-days", {});
      const [y, m] = ym, first = new Date(y, m, 1), days = new Date(y, m + 1, 0).getDate(), off = first.getDay();
      const max = Math.max(1, ...Object.values(act));
      const today = P.dayKey();
      let cells = ["D", "S", "T", "Q", "Q", "S", "S"].map((d) => `<div class="dow">${d}</div>`).join("");
      for (let k = 0; k < off; k++) cells += "<div></div>";
      let played = 0, games = 0;
      for (let d = 1; d <= days; d++) {
        const key = P.dayKey(new Date(y, m, d)), n = act[key] || 0;
        if (n) { played++; games += n; }
        const bg = n ? `background:color-mix(in srgb, var(--accent) ${Math.round((0.3 + 0.7 * (n / max)) * 100)}%, transparent)` : "";
        const dot = termo[key] ? `<i style="background:${termo[key] === "w" ? "var(--correct)" : "var(--red)"}"></i>` : "";
        cells += `<div class="day${n ? " on" : ""}${key === today ? " today" : ""}" style="${bg}">${d}${dot}</div>`;
      }
      const name = MONTHS[m][0].toUpperCase() + MONTHS[m].slice(1);
      return `<div class="cal-head"><button class="icon-btn filled" data-prev aria-label="Mês anterior">‹</button><b>${name} de ${y}</b><button class="icon-btn filled" data-next aria-label="Próximo mês">›</button></div>
        <div class="cal">${cells}</div><p class="muted" style="font-size:.85rem">${played} ${played === 1 ? "dia jogado" : "dias jogados"} · ${games} ${games === 1 ? "jogo" : "jogos"} no mês</p>`;
    }

    function resumo() {
      const s = P.stats(modes[tab][0]), best = g("ws-best");
      body.innerHTML = `
        <div class="section"><h3>Calendário</h3>${calendar()}</div>
        <div class="section"><h3>Estatísticas</h3>
          <div class="seg">${modes.map(([, n], i) => `<button class="${i === tab ? "on" : ""}" data-tab="${i}">${n}</button>`).join("")}</div>
          ${P.statsHTML([[s.played, "Jogos"], [Math.round((s.won / Math.max(1, s.played)) * 100) + "%", "Vitórias"], [s.streak, "Sequência"], [s.maxStreak, "Melhor"]])}
          <p class="muted" style="font-size:.85rem">Distribuição de tentativas</p>${P.distHTML(s.dist, -1, modes[tab][2])}</div>
        <div class="section"><h3>Evolução</h3>
          <div class="seg evo-seg">${evoKinds.map(([k, n, u]) => `<button class="${k === evo ? "on" : ""}" data-evo="${k}">${n}<small>${u}</small></button>`).join("")}</div>
          ${P.evolutionHTML(evo)}</div>
        <div class="section"><h3>Recordes</h3>
          <div class="rec"><span>Conexões</span><span>${g("conn-won")} resolvidos · ${g("conn-perfect")} sem erro</span></div>
          <div class="rec"><span>Caça-Palavras</span><span>${g("ws-played")} grades · melhor ${best ? P.fmtTime(best) : "—"}</span></div>
          <div class="rec"><span>Intruso</span><span>recorde ${g("intr-best")} · ${g("intr-right")} acertos</span></div>
          <div class="rec"><span>Certo ou Errado</span><span>recorde ${g("ort-best")} · ${g("ort-right")} acertos</span></div>
          <div class="rec"><span>Reverso</span><span>site ${g("rev-app")} × ${g("rev-user")} você</span></div>
          <div class="rec"><span>Qual é a Palavra?</span><span>recorde ${g("def-best")} seguidas · ${g("def-right")} acertos</span></div>
          <div class="rec"><span>Sinônimos</span><span>maior cadeia: ${g("syn-best")}</span></div>
          <div class="rec"><span>Antônimos</span><span>maior cadeia: ${g("ant-best")}</span></div>
          <div class="rec"><span>Arquivo</span><span>${g("arch-played")} ${g("arch-played") === 1 ? "desafio" : "desafios"}</span></div></div>`;
      body.querySelectorAll("[data-tab]").forEach((b) => (b.onclick = () => { tab = +b.dataset.tab; draw(); }));
      body.querySelectorAll("[data-evo]").forEach((b) => (b.onclick = () => { evo = b.dataset.evo; draw(); }));
      P.$("[data-prev]", body).onclick = () => { ym = ym[1] === 0 ? [ym[0] - 1, 11] : [ym[0], ym[1] - 1]; draw(); };
      P.$("[data-next]", body).onclick = () => { const n = ym[1] === 11 ? [ym[0] + 1, 0] : [ym[0], ym[1] + 1]; if (n[0] < now.getFullYear() || (n[0] === now.getFullYear() && n[1] <= now.getMonth())) { ym = n; draw(); } };
    }

    function conquistas() {
      const list = P.achievements(), ok = list.filter((a) => a.ok).length;
      const sorted = list.filter((a) => a.ok).concat(list.filter((a) => !a.ok));
      body.innerHTML = `<div class="section"><div class="ach-head"><h3>Conquistas</h3><b>${ok} de ${list.length}</b></div>
        <div class="today-bar"><i style="width:${(ok / list.length) * 100}%"></i></div>
        <div class="ach-list">${sorted.map((a) => `<div class="ach-row${a.ok ? "" : " off"}">
          <span class="ach-emo" aria-hidden="true">${a.ok ? a.emoji : "🔒"}</span>
          <span class="ach-txt"><b>${a.title}</b><small>${a.desc}</small></span>
          <span class="ach-prog${a.ok ? " done" : ""}">${a.ok ? "✓" : `${a.progress}/${a.goal}`}</span></div>`).join("")}</div></div>`;
    }

    function ajustes() {
      const set = P.settings();
      body.innerHTML = `<div class="section"><h3>Ajustes</h3>
          <div class="toggle"><span>Sons</span><button class="switch ${set.sound ? "on" : ""}" data-set="sound" aria-label="Sons" aria-pressed="${set.sound}"></button></div>
          <div class="toggle"><span>Vibração</span><button class="switch ${set.vibration ? "on" : ""}" data-set="vibration" aria-label="Vibração" aria-pressed="${set.vibration}"></button></div>
          <div class="toggle"><span>Modo difícil</span><button class="switch ${set.hard ? "on" : ""}" data-set="hard" aria-label="Modo difícil" aria-pressed="${set.hard}"></button></div>
          <p class="muted" style="font-size:.8rem;margin:0">Vale a partir da próxima partida do Termo e do Infinito.</p></div>
        <div class="section"><h3>Sincronizar site e app</h3>
          <p class="muted" style="font-size:.9rem;margin-top:0">Copie seu código aqui e cole no app (ou o contrário). O progresso dos dois é juntado, sem apagar nada.</p>
          <div class="row-btns"><button class="pill ghost" data-copy>Copiar meu código</button><button class="pill" data-paste>Colar código</button></div></div>
        <div class="section">
          <div class="toggle"><span>Como jogar</span><button class="icon-btn" data-howto aria-label="Como jogar">›</button></div>
          <div class="toggle"><span>App para Android</span><a class="pill ghost" style="text-decoration:none;padding:9px 14px" href="https://github.com/dmwnezes/termo/releases/latest/download/Palavreiro.apk">Baixar</a></div>
          <p class="muted" style="font-size:.8rem;margin:4px 0 0">Versão do site ${SITE_VERSION}</p></div>`;
      body.querySelectorAll("[data-set]").forEach((b) => (b.onclick = () => { const s2 = P.settings(); s2[b.dataset.set] = !s2[b.dataset.set]; P.saveSettings(s2); draw(); }));
      P.$("[data-howto]", body).onclick = () => P.games.help();
      P.$("[data-copy]", body).onclick = async () => {
        const code = P.syncExport();
        try { await navigator.clipboard.writeText(code); P.toast("Código copiado!"); }
        catch (_) {
          const ui = P.sheet(`<h2>Seu código</h2><p class="subtitle">Copie o texto abaixo</p><textarea class="sync-box" readonly>${code}</textarea>`);
          const ta = P.$("textarea", ui.el); ta.focus(); ta.select();
        }
      };
      P.$("[data-paste]", body).onclick = () => {
        const ui = P.sheet(`<h2>Colar código</h2><p class="subtitle">Cole o código copiado no app ou em outro navegador</p>
          <textarea class="sync-box" data-code placeholder="PV1-…" aria-label="Código de sincronização"></textarea>
          <button class="pill wide" data-import style="margin-top:12px">Importar</button>`);
        P.$("[data-import]", ui.el).onclick = () => {
          if (P.syncImport(P.$("[data-code]", ui.el).value)) { ui.close(); P.toast("Progresso sincronizado!"); P.fx.win(); draw(); }
          else { P.fx.invalid(); P.toast("Código inválido"); }
        };
      };
    }

    function draw() { head(); [resumo, conquistas, ajustes][page](); }
    draw();
  };
})();
