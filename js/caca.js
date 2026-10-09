// Caça-Palavras do Dia (site): mesmo tema do app; arraste o dedo ou o mouse sobre as letras.
(function () {
  "use strict";
  const N = 10;
  const COLORS = ["#5FB873", "#E6C14F", "#6FA8E8", "#B48CF0", "#E5737A", "#7FD1C4", "#F2A65A", "#9B8CFF"];

  function build(words, rnd) {
    const list = words.map((d) => ({ d, w: P.norm(d).replace(/[^A-Z]/g, "") })).sort((a, b) => b.w.length - a.w.length);
    for (let attempt = 0; attempt < 400; attempt++) {
      const g = Array.from({ length: N }, () => Array(N).fill(" ")), placed = [];
      const fwd = [[0, 1], [1, 0], [1, 1], [-1, 1]], back = [[0, -1], [-1, 0], [-1, -1], [1, -1]];
      let ok = true;
      for (const { d, w } of list) {
        let done = false;
        for (let k = 0; k < 300 && !done; k++) {
          const dirs = attempt < 40 && rnd() < 0.25 ? back : fwd;
          const [dr, dc] = dirs[Math.floor(rnd() * dirs.length)];
          const r = Math.floor(rnd() * N), c = Math.floor(rnd() * N);
          const er = r + dr * (w.length - 1), ec = c + dc * (w.length - 1);
          if (er < 0 || er >= N || ec < 0 || ec >= N) continue;
          if (![...w].every((ch, i) => { const x = g[r + dr * i][c + dc * i]; return x === " " || x === ch; })) continue;
          [...w].forEach((ch, i) => (g[r + dr * i][c + dc * i] = ch));
          placed.push({ w, d, cells: [...w].map((_, i) => [r + dr * i, c + dc * i]) });
          done = true;
        }
        if (!done) { ok = false; break; }
      }
      if (!ok) continue;
      const fill = "AAAAEEEEIIOOOUURRSSTTNNMMLLCCDDPPBGVFHQJZX";
      g.forEach((row) => row.forEach((x, c) => { if (x === " ") row[c] = fill[Math.floor(rnd() * fill.length)]; }));
      return { g, placed };
    }
    return null;
  }

  const fmt = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
  P.fmtTime = fmt;

  const parseThemes = (t) => t.split("\n").map((l) => l.trim()).filter((l) => l && !l.startsWith("#"))
    .map((l) => { const i = l.indexOf(":"); return { name: l.slice(0, i).trim(), words: l.slice(i + 1).split(",").map((s) => s.trim()).filter(Boolean) }; });
  // Infinito: tema sorteado (dos do dia + extras) e 8 palavras dele; mesma semente = mesma grade.
  const infinite = (pool, seed) => {
    const rnd = P.rng(seed);
    for (let k = 0; k < 30; k++) {
      const theme = pool[Math.floor(rnd() * pool.length)];
      const words = P.shuffle([...new Set(theme.words)], rnd).slice(0, 8);
      const b = build(words, rnd);
      if (b) return { theme: { name: theme.name, words }, ...b };
    }
    return null;
  };
  const newSeed = () => Math.floor(Math.random() * 2147483647);

  P.games.caca = async function (root, inf = false) {
    const [themes, extra] = await Promise.all([P.text("caca.txt").then(parseThemes), P.text("caca-extra.txt").then(parseThemes)]);
    const pool = themes.concat(extra);
    const day = P.dayKey(), i = P.dayIndex();
    const st = P.store.get(inf ? "ws-inf-state" : "ws-state", null);
    const seed = inf ? (st && st.seed != null ? st.seed : newSeed()) : null;
    let theme, g, placed;
    if (inf) ({ theme, g, placed } = infinite(pool, seed));
    else { theme = themes[((i * 11 + 3) % themes.length + themes.length) % themes.length]; ({ g, placed } = build(theme.words, P.rng(i * 31 + 17))); }
    const mine = st && (inf ? st.seed === seed : st.day === day);
    let found = mine ? st.found : [];
    let seconds = mine ? st.seconds || 0 : 0;
    const done = () => found.length === placed.length;
    // Próxima grade infinita, sempre com tema diferente da atual.
    const next = () => {
      let s; do { s = newSeed(); } while (infinite(pool, s).theme.name === theme.name);
      P.store.set("ws-inf-state", { seed: s, found: [], seconds: 0 });
      document.querySelectorAll(".overlay").forEach((o) => o.remove());
      P.games.caca(root, true);
    };
    if (inf && done()) return next();

    root.innerHTML = P.topbar("Caça-Palavras", { extra: `<b class="muted" data-time style="padding-right:10px">${fmt(seconds)}</b>` }) + P.modeSwitch(inf) +
      `<div class="game"><div class="ws-theme"><small>${inf ? "Tema sorteado" : "Tema de hoje"}</small><h2>${P.esc(theme.name)}</h2></div>
      <div class="ws-wrap"><canvas></canvas><div class="ws-grid" style="grid-template-columns:repeat(${N},1fr)"></div></div>
      <div class="chips" data-chips></div><p class="center muted" data-count></p><div class="center" data-after style="padding-bottom:16px"></div></div>`;
    const wrap = P.$(".ws-wrap", root), canvas = P.$("canvas", wrap), gridEl = P.$(".ws-grid", wrap);
    g.forEach((row) => row.forEach((ch) => gridEl.appendChild(P.h(`<span>${ch}</span>`))));

    let start = null, end = null;
    const timer = setInterval(() => {
      if (!document.body.contains(wrap)) return clearInterval(timer);
      if (done()) return;
      seconds++; P.$("[data-time]", root).textContent = fmt(seconds); save();
    }, 1000);
    function save() { if (document.body.contains(wrap)) P.store.set(inf ? "ws-inf-state" : "ws-state", inf ? { seed, found, seconds } : { day, found, seconds }); }
    P.bindModeSwitch(root, (v) => { clearInterval(timer); P.games.caca(root, v); });

    function paint() {
      const dpr = devicePixelRatio || 1, size = wrap.clientWidth;
      canvas.width = size * dpr; canvas.height = size * dpr;
      const c = canvas.getContext("2d"); c.scale(dpr, dpr); c.lineCap = "round";
      const cell = size / N, ctr = ([r, col]) => [col * cell + cell / 2, r * cell + cell / 2];
      const line = (a, b, color) => { c.strokeStyle = color; c.lineWidth = cell * 0.78; c.beginPath(); c.moveTo(...ctr(a)); c.lineTo(...ctr(b)); c.stroke(); };
      placed.forEach((p, k) => { if (found.includes(p.w)) { c.globalAlpha = 0.55; line(p.cells[0], p.cells[p.cells.length - 1], COLORS[k % COLORS.length]); } });
      if (start && end) { c.globalAlpha = 0.6; line(start, end, "#9B8CFF"); }
      P.$("[data-chips]", root).innerHTML = placed.map((p) => `<span class="${found.includes(p.w) ? "found" : ""}">${P.esc(p.d)}</span>`).join("");
      P.$("[data-count]", root).textContent = `${found.length} de ${placed.length} palavras`;
      const after = P.$("[data-after]", root);
      if (done() && !after.innerHTML) {
        const b = P.h(`<button class="pill${inf ? " ghost" : ""}">Ver resultado</button>`); b.onclick = result; after.appendChild(b);
        if (inf) { const n = P.h(`<button class="pill" style="margin-left:8px">Próxima grade</button>`); n.onclick = next; after.appendChild(n); }
      }
    }
    const cellAt = (e) => {
      const r = wrap.getBoundingClientRect(), cell = r.width / N;
      return [Math.min(N - 1, Math.max(0, Math.floor((e.clientY - r.top) / cell))), Math.min(N - 1, Math.max(0, Math.floor((e.clientX - r.left) / cell)))];
    };
    const snap = ([r1, c1], [r2, c2]) => {
      const dr = r2 - r1, dc = c2 - c1;
      if (!dr || !dc) return [r2, c2];
      const a = Math.abs(dr), b = Math.abs(dc);
      if (a > 2 * b) return [r2, c1]; if (b > 2 * a) return [r1, c2];
      const n = Math.min(a, b); return [r1 + Math.sign(dr) * n, c1 + Math.sign(dc) * n];
    };
    wrap.addEventListener("pointerdown", (e) => { if (done()) return; wrap.setPointerCapture(e.pointerId); start = cellAt(e); end = start; paint(); });
    wrap.addEventListener("pointermove", (e) => { if (!start) return; end = snap(start, cellAt(e)); paint(); });
    const up = () => {
      if (!start) return;
      const [r1, c1] = start, [r2, c2] = end, n = Math.max(Math.abs(r2 - r1), Math.abs(c2 - c1));
      const cells = Array.from({ length: n + 1 }, (_, k) => [r1 + Math.sign(r2 - r1) * k, c1 + Math.sign(c2 - c1) * k]);
      const key = JSON.stringify(cells), rev = JSON.stringify(cells.slice().reverse());
      const hit = placed.find((p) => !found.includes(p.w) && (JSON.stringify(p.cells) === key || JSON.stringify(p.cells) === rev));
      start = end = null;
      if (hit) {
        found.push(hit.w); save(); P.fx.reveal(2, "c"); P.toast(hit.d);
        if (done()) {
          P.fx.win(); P.confetti();
          if (inf) { P.store.add("ws-inf-played"); P.store.min("ws-inf-best", seconds); P.logActivity(); }
          else if (P.store.get("ws-done") !== day) { P.store.set("ws-done", day); P.store.add("ws-played"); P.store.min("ws-best", seconds); P.logActivity(); }
          setTimeout(result, 1600);
        }
      }
      paint();
    };
    wrap.addEventListener("pointerup", up); wrap.addEventListener("pointercancel", () => { start = end = null; paint(); });

    function result() {
      const best = P.store.get(inf ? "ws-inf-best" : "ws-best", 0), played = P.store.get(inf ? "ws-inf-played" : "ws-played", 0);
      const ui = P.sheet(`<h2>Você achou todas!</h2><p class="subtitle">Tema: ${P.esc(theme.name)}</p>
        ${P.statsHTML([[fmt(seconds), "Tempo"], [best ? fmt(best) : "—", "Melhor tempo"], [played, "Grades"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill accent" data-story>Stories</button></div>
        ${inf ? `<button class="pill" data-next style="width:100%;margin-top:10px">Próxima grade</button>` : `<p class="note">Uma grade nova aparece amanhã.</p>`}`);
      if (inf) P.$("[data-next]", ui.el).onclick = next;
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Caça-Palavras\nTema: ${theme.name}\nAchei as ${placed.length} palavras em ${fmt(seconds)} 🔎`);
      P.$("[data-story]", ui.el).onclick = () => P.story({
        game: inf ? "Caça-Palavras Infinito" : "Caça-Palavras do dia", headline: `Achei tudo em ${fmt(seconds)}`, detail: `Tema: ${theme.name}`,
        grids: [placed.map((_, k) => COLORS[k % COLORS.length]).reduce((a, c, k) => { (a[Math.floor(k / 4)] = a[Math.floor(k / 4)] || []).push(c); return a; }, [])],
        stats: [[best ? fmt(best) : "—", "Melhor tempo"], [String(played), "Grades"]],
      });
    }
    window.addEventListener("resize", () => document.body.contains(wrap) && paint());
    paint();
    if (done()) setTimeout(result, 400);
  };
})();
