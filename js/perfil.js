// Perfil (site): calendário, estatísticas, recordes, conquistas e configurações.
(function () {
  "use strict";
  const MONTHS = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"];

  P.games.perfil = function (root) {
    const modes = [["termo", "Termo", 6], ["infinito", "Infinito", 6], ["dueto", "Dueto", 7], ["quarteto", "Quarteto", 9]];
    let tab = 0;
    const now = new Date();
    let ym = [now.getFullYear(), now.getMonth()];
    root.innerHTML = P.topbar("Perfil") + `<div data-body></div><div class="footer">${P.creditHTML()}</div>`;
    const body = P.$("[data-body]", root);

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
      return `<div class="cal-head"><button class="icon-btn filled" data-prev>‹</button><b>${name} de ${y}</b><button class="icon-btn filled" data-next>›</button></div>
        <div class="cal">${cells}</div><p class="muted" style="font-size:.85rem">${played} ${played === 1 ? "dia jogado" : "dias jogados"} · ${games} ${games === 1 ? "jogo" : "jogos"} no mês</p>`;
    }

    function achievements(st) {
      const g = (k) => P.store.get(k, 0);
      const d = st.termo, inf = st.infinito, wins = d.won + inf.won;
      const list = [
        ["🌱", "Primeira palavra", "Acerte sua primeira palavra", wins >= 1],
        ["🎯", "De primeira", "Acerte na primeira tentativa", d.firstTry + inf.firstTry >= 1],
        ["⚡", "Rapidinho", "Acerte em duas tentativas", d.dist[1] + inf.dist[1] + d.firstTry + inf.firstTry >= 1],
        ["🔥", "Três dias seguidos", "Sequência de 3 no Termo", d.maxStreak >= 3],
        ["📅", "Uma semana", "Sequência de 7 no Termo", d.maxStreak >= 7],
        ["🏆", "Um mês", "Sequência de 30 no Termo", d.maxStreak >= 30],
        ["♾️", "Maratona", "Acerte 25 palavras no Infinito", inf.won >= 25],
        ["📚", "Palavreiro", "Acerte 100 palavras no total", wins >= 100],
        ["👯", "Dupla certa", "Vença um Dueto", st.dueto.won >= 1],
        ["🍀", "Quatro de uma vez", "Vença um Quarteto", st.quarteto.won >= 1],
        ["🧩", "Conectado", "Resolva um Conexões", g("conn-won") >= 1],
        ["💎", "Sem errar", "Resolva um Conexões sem erros", g("conn-perfect") >= 1],
        ["🔎", "Olho de águia", "Termine um Caça-Palavras em menos de 2 minutos", g("ws-best") > 0 && g("ws-best") < 120],
        ["🤖", "Mais esperto que o site", "Vença o Reverso", g("rev-user") >= 1],
        ["📖", "Dicionário ambulante", "Acerte 10 seguidas no Qual é a Palavra?", g("def-best") >= 10],
        ["⛓️", "Corrente forte", "Faça uma cadeia de 20 sinônimos", g("syn-best") >= 20],
      ];
      return `<p class="muted" style="margin-top:0">${list.filter((a) => a[3]).length} de ${list.length}</p><div class="ach">${list.map(([e, t, s, ok]) =>
        `<div class="${ok ? "" : "off"}"><div style="font-size:1.5rem">${ok ? e : "🔒"}</div><b>${t}</b><small>${s}</small></div>`).join("")}</div>`;
    }

    function draw() {
      const st = Object.fromEntries(modes.map(([k]) => [k, P.stats(k)]));
      const s = st[modes[tab][0]], set = P.settings(), g = (k) => P.store.get(k, 0);
      const best = g("ws-best");
      body.innerHTML = `
        <div class="section"><h3>Calendário</h3>${calendar()}</div>
        <div class="section"><h3>Estatísticas</h3>
          <div class="seg">${modes.map(([, n], i) => `<button class="${i === tab ? "on" : ""}" data-tab="${i}">${n}</button>`).join("")}</div>
          ${P.statsHTML([[s.played, "Jogos"], [Math.round((s.won / Math.max(1, s.played)) * 100) + "%", "Vitórias"], [s.streak, "Sequência"], [s.maxStreak, "Melhor"]])}
          <p class="muted" style="font-size:.85rem">Distribuição de tentativas</p>${P.distHTML(s.dist, -1, modes[tab][2])}</div>
        <div class="section"><h3>Recordes</h3>
          <div class="rec"><span>Conexões</span><span>${g("conn-won")} resolvidos · ${g("conn-perfect")} sem erro</span></div>
          <div class="rec"><span>Caça-Palavras</span><span>${g("ws-played")} grades · melhor ${best ? P.fmtTime(best) : "—"}</span></div>
          <div class="rec"><span>Reverso</span><span>site ${g("rev-app")} × ${g("rev-user")} você</span></div>
          <div class="rec"><span>Qual é a Palavra?</span><span>recorde ${g("def-best")} seguidas · ${g("def-right")} acertos</span></div>
          <div class="rec"><span>Sinônimos</span><span>maior cadeia: ${g("syn-best")}</span></div></div>
        <div class="section"><h3>Conquistas</h3>${achievements(st)}</div>
        <div class="section"><h3>Configurações</h3>
          <div class="toggle"><span>Sons</span><button class="switch ${set.sound ? "on" : ""}" data-set="sound" aria-label="Sons"></button></div>
          <div class="toggle"><span>Vibração</span><button class="switch ${set.vibration ? "on" : ""}" data-set="vibration" aria-label="Vibração"></button></div>
          <div class="toggle"><span>Modo difícil</span><button class="switch ${set.hard ? "on" : ""}" data-set="hard" aria-label="Modo difícil"></button></div>
          <p class="muted" style="font-size:.8rem;margin:0">Vale a partir da próxima partida do Termo e do Infinito.</p>
          <div class="toggle" style="margin-top:8px"><span>Como jogar</span><button class="icon-btn" data-howto>›</button></div>
          <div class="toggle"><span>App para Android</span><a class="pill ghost" style="text-decoration:none;padding:9px 14px" href="https://github.com/dmwnezes/termo/releases/latest/download/Palavreiro.apk">Baixar</a></div>
        </div>`;
      body.querySelectorAll("[data-tab]").forEach((b) => (b.onclick = () => { tab = +b.dataset.tab; draw(); }));
      body.querySelectorAll("[data-set]").forEach((b) => (b.onclick = () => { const s2 = P.settings(); s2[b.dataset.set] = !s2[b.dataset.set]; P.saveSettings(s2); draw(); }));
      P.$("[data-prev]", body).onclick = () => { ym = ym[1] === 0 ? [ym[0] - 1, 11] : [ym[0], ym[1] - 1]; draw(); };
      P.$("[data-next]", body).onclick = () => { const n = ym[1] === 11 ? [ym[0] + 1, 0] : [ym[0], ym[1] + 1]; if (n[0] < now.getFullYear() || (n[0] === now.getFullYear() && n[1] <= now.getMonth())) { ym = n; draw(); } };
      P.$("[data-howto]", body).onclick = () => P.games.help();
    }
    draw();
  };
})();
