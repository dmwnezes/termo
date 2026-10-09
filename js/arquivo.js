// Arquivo de desafios (site): jogue os desafios do dia de qualquer data desde 01/01/2026 até ontem.
(function () {
  "use strict";
  const MONTHS = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"];
  const GAMES = [["termo", "Termo"], ["dueto", "Dueto"], ["quarteto", "Quarteto"], ["conexoes", "Conexões"], ["caca", "Caça"]];
  const ss = {
    get(k, f) { try { const v = sessionStorage.getItem("pv-arch-" + k); return v == null ? f : JSON.parse(v); } catch (_) { return f; } },
    set(k, v) { try { sessionStorage.setItem("pv-arch-" + k, JSON.stringify(v)); } catch (_) {} },
  };

  P.games.arquivo = function (root) {
    const now = new Date(), yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1);
    const lastYm = [yesterday.getFullYear(), yesterday.getMonth()];
    let game = ss.get("tab", "termo");
    if (!GAMES.some(([k]) => k === game)) game = "termo";
    let ym = ss.get("ym", lastYm);
    const cmp = (a, b) => a[0] * 12 + a[1] - (b[0] * 12 + b[1]);
    if (cmp(ym, [2026, 0]) < 0 || cmp(ym, lastYm) > 0) ym = lastYm;

    root.innerHTML = P.topbar("Arquivo") + `<div data-body></div>`;
    const body = P.$("[data-body]", root);

    function draw() {
      ss.set("tab", game); ss.set("ym", ym);
      const res = P.store.get("results", {}), last = P.dayKey(yesterday);
      const [y, m] = ym, first = new Date(y, m, 1), days = new Date(y, m + 1, 0).getDate(), off = first.getDay();
      let cells = ["D", "S", "T", "Q", "Q", "S", "S"].map((d) => `<div class="dow">${d}</div>`).join("");
      for (let k = 0; k < off; k++) cells += "<div></div>";
      let w = 0, l = 0;
      for (let d = 1; d <= days; d++) {
        const key = P.dayKey(new Date(y, m, d)), r = res[game + "|" + key], ok = key <= last && key >= "2026-01-01";
        if (r === "w") w++; else if (r === "l") l++;
        const mark = r === "w" ? `<em class="ok" aria-label="vitória">✓</em>` : r === "l" ? `<em class="no" aria-label="derrota">✗</em>` : "";
        cells += ok ? `<button class="day arch on${r ? " " + r : ""}" data-day="${key}" aria-label="${d} de ${MONTHS[m]}">${d}${mark}</button>`
          : `<div class="day off" aria-disabled="true">${d}</div>`;
      }
      const name = MONTHS[m][0].toUpperCase() + MONTHS[m].slice(1);
      const canPrev = cmp(ym, [2026, 0]) > 0, canNext = cmp(ym, lastYm) < 0;
      body.innerHTML = `<p class="muted arch-intro">Jogue os desafios dos dias que passaram. Não muda suas sequências nem as estatísticas do dia.</p>
        <div class="seg arch-seg">${GAMES.map(([k, t]) => `<button class="${k === game ? "on" : ""}" data-g="${k}">${t}</button>`).join("")}</div>
        <div class="section" style="margin-top:14px">
          <div class="cal-head"><button class="icon-btn filled" data-prev ${canPrev ? "" : "disabled"} aria-label="Mês anterior">‹</button><b>${name} de ${y}</b><button class="icon-btn filled" data-next ${canNext ? "" : "disabled"} aria-label="Próximo mês">›</button></div>
          <div class="cal">${cells}</div>
          <p class="muted" style="font-size:.85rem;margin-bottom:0">${w} ${w === 1 ? "vitória" : "vitórias"} · ${l} ${l === 1 ? "derrota" : "derrotas"} neste mês</p>
        </div>`;
      body.querySelectorAll("[data-g]").forEach((b) => (b.onclick = () => { game = b.dataset.g; P.fx.type(); draw(); }));
      body.querySelectorAll("[data-day]").forEach((b) => (b.onclick = () => P.go(`arquivo/${game}/${b.dataset.day}`)));
      P.$("[data-prev]", body).onclick = () => { if (canPrev) { ym = ym[1] === 0 ? [ym[0] - 1, 11] : [ym[0], ym[1] - 1]; draw(); } };
      P.$("[data-next]", body).onclick = () => { if (canNext) { ym = ym[1] === 11 ? [ym[0] + 1, 0] : [ym[0], ym[1] + 1]; draw(); } };
    }
    draw();
  };

  /** Abre um desafio do arquivo. Devolve false se a data ou o jogo forem inválidos. */
  P.games.arquivoGame = function (root, game, day) {
    const now = new Date(), last = P.dayKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1));
    if (!/^\d{4}-\d{2}-\d{2}$/.test(day || "") || day < "2026-01-01" || day > last || P.dayKey(P.parseDay(day)) !== day) return false;
    if (game === "termo" || game === "dueto" || game === "quarteto") P.games.termoScreen(root, game, null, day);
    else if (game === "conexoes") P.games.conexoes(root, false, day);
    else if (game === "caca") P.games.caca(root, false, day);
    else return false;
    ss.set("tab", game);
    return true;
  };
})();
