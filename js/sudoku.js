// Sudoku (site): grades de shared/sudoku.txt (NIVEL|puzzle|solução). Do dia: índice = dia mod 366 na lista do nível.
// Nome no jogo: Quadrante. Infinito ("Outra grade"): sorteado no nível. Erro = número diferente da solução; 3 erros encerram a partida.
(function () {
  "use strict";
  const LEVELS = [["facil", "Fácil"], ["medio", "Médio"], ["dificil", "Difícil"]];
  const MAX_ERR = 3;
  const HELP = "Quadrante é o Sudoku do Palavreiro. Complete a grade 9×9 para que cada linha, cada coluna e cada bloco 3×3 tenha os números de 1 a 9, sem repetir. " +
    "Toque numa casa e depois num número. Com “Anotações” ligado, os números entram pequenos, como lembrete. " +
    "Número errado fica vermelho e conta um erro: com 3 erros a partida acaba. No modo Do dia há uma grade por nível; no Infinito, “Outra grade” sorteia uma nova.";
  const fmt = (s) => (P.fmtTime ? P.fmtTime(s) : `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`);
  const digits = (t) => (/^\d{81}$/.test(t) ? [...t].map(Number) : null);
  let dataP = null;
  /** {facil: [{p, s}], medio: [...], dificil: [...]} */
  const data = () => dataP || (dataP = P.text("sudoku.txt").then((t) => {
    const out = { facil: [], medio: [], dificil: [] };
    t.split("\n").forEach((l) => {
      const [k, p, s] = l.trim().split("|");
      if (out[k] && digits(p) && digits(s)) out[k].push({ p: digits(p), s: digits(s) });
    });
    return out;
  }));
  const peers = (i, j) => i !== j && ((i / 9 | 0) === (j / 9 | 0) || i % 9 === j % 9 || ((i / 27 | 0) === (j / 27 | 0) && ((i % 9) / 3 | 0) === ((j % 9) / 3 | 0)));

  P.games.sudoku = async function (root) {
    const inf = P.store.get("sdk-inf", false) === true;
    let lv = Math.max(0, LEVELS.findIndex(([k]) => k === P.store.get("sdk-level", "facil")));
    root.innerHTML = P.topbar("Quadrante", { help: true }) + P.modeSwitch(inf) + `<div class="game sdk">
      <div class="seg sdk-levels">${LEVELS.map(([, n], i) => `<button data-lv="${i}" class="${i === lv ? "on" : ""}">${n}</button>`).join("")}</div>
      <div class="score-row mem-hud sdk-hud"><span><small>Erros</small><b data-err>0/${MAX_ERR}</b></span><span class="sdk-tag" data-tag></span><span><small>Tempo</small><b data-time>0:00</b></span></div>
      <div class="sdk-grid" data-grid>${Array.from({ length: 81 }, (_, i) => `<button class="sdk-c" data-c="${i}"></button>`).join("")}</div>
      <div class="sdk-tools"><button class="pill ghost" data-erase>⌫ Apagar</button><button class="pill ghost" data-notes aria-pressed="false">✏️ Anotações</button>${inf ? `<button class="pill ghost" data-other>Outra grade</button>` : ""}</div>
      <div class="sdk-pad">${Array.from({ length: 9 }, (_, i) => `<button data-d="${i + 1}">${i + 1}</button>`).join("")}</div>
      <p class="center muted sdk-best" data-best></p></div>`;
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Quadrante</h2><p>${HELP}</p>`);
    P.bindModeSwitch(root, (v) => { P.store.set("sdk-inf", v); P.games.sudoku(root); });
    const grid = P.$("[data-grid]", root);
    let all;
    try { all = await data(); } catch (_) { all = null; }
    if (!document.body.contains(grid)) return;
    if (!all || !all.facil.length) { grid.outerHTML = `<p class="center muted">Não foi possível carregar o Quadrante.</p>`; return; }

    let G = null, sel = -1, notesOn = false, timer = 0;
    const lvKey = () => LEVELS[lv][0];
    const saveKey = () => `sdk-save-${inf ? "inf" : "day"}-${lvKey()}`;
    const day = P.dayIndex(), list = () => all[lvKey()];
    const puzzle = () => list()[G.idx];
    const save = () => P.store.set(saveKey(), G);
    const fresh = (idx) => ({ idx, day, v: list()[idx].p.slice(), notes: Array(81).fill(0), err: 0, secs: 0, over: null });

    function load() {
      const n = list().length, sv = P.store.get(saveKey(), null);
      const okSave = sv && Number.isInteger(sv.idx) && sv.idx >= 0 && sv.idx < n && Array.isArray(sv.v) && sv.v.length === 81 && Array.isArray(sv.notes) && (inf || sv.day === day);
      G = okSave ? sv : fresh(inf ? Math.floor(Math.random() * n) : ((day % n) + n) % n);
      if (G.over === "lost") G = fresh(G.idx); // perdeu e saiu: volta com a grade limpa
      sel = -1; save(); draw();
    }

    function draw() {
      const pz = puzzle(), sv = sel >= 0 ? G.v[sel] : 0;
      grid.querySelectorAll("[data-c]").forEach((b) => {
        const i = +b.dataset.c, v = G.v[i], fixed = pz.p[i] > 0, wrong = v && v !== pz.s[i];
        let cls = "sdk-c" + (fixed ? " fixed" : v ? " mine" : "") + (wrong ? " err" : "");
        if (sel >= 0 && i !== sel && peers(i, sel)) cls += " hl";
        if (sv && v === sv && i !== sel) cls += " same";
        if (i === sel) cls += " sel";
        b.className = cls;
        const h = v ? String(v) : G.notes[i] ? `<span class="sdk-notes">${Array.from({ length: 9 }, (_, d) => `<i>${G.notes[i] & (1 << (d + 1)) ? d + 1 : ""}</i>`).join("")}</span>` : "";
        if (b.innerHTML !== h) b.innerHTML = h;
        b.setAttribute("aria-label", `Linha ${(i / 9 | 0) + 1}, coluna ${(i % 9) + 1}: ${v || "vazia"}`);
      });
      root.querySelectorAll("[data-d]").forEach((b) => { const d = +b.dataset.d; b.disabled = !!G.over || G.v.filter((x, i) => x === d && x === pz.s[i]).length === 9; });
      P.$("[data-err]", root).textContent = `${G.err}/${MAX_ERR}`;
      P.$("[data-err]", root).classList.toggle("bad", G.err > 0);
      P.$("[data-time]", root).textContent = fmt(G.secs);
      P.$("[data-tag]", root).textContent = G.over === "won" ? (inf ? "Resolvido ✓" : "Feito hoje ✓") : "";
      const nb = P.$("[data-notes]", root); nb.classList.toggle("on", notesOn); nb.setAttribute("aria-pressed", notesOn);
      const best = P.store.get("sudoku-best-" + lvKey(), 0);
      P.$("[data-best]", root).textContent = best ? `Melhor tempo no ${LEVELS[lv][1]}: ${fmt(best)}` : `Sem recorde no ${LEVELS[lv][1]} ainda`;
    }

    function put(d) {
      if (G.over || sel < 0) return;
      const pz = puzzle(), i = sel;
      if (pz.p[i] || G.v[i] === pz.s[i]) return; // fixo ou já certo
      if (notesOn) {
        if (G.v[i]) return;
        G.notes[i] ^= 1 << d; P.fx.type();
      } else {
        if (G.v[i] === d) return;
        G.v[i] = d; G.notes[i] = 0;
        if (d === pz.s[i]) {
          for (let j = 0; j < 81; j++) if (peers(i, j)) G.notes[j] &= ~(1 << d);
          P.fx.type();
        } else {
          G.err++; P.fx.invalid();
          const c = grid.querySelector(`[data-c="${i}"]`); c.classList.remove("shake"); void c.offsetWidth; c.classList.add("shake");
        }
      }
      save(); draw();
      if (G.v.every((x, j) => x === pz.s[j])) win();
      else if (G.err >= MAX_ERR) lose();
    }
    function erase() {
      if (G.over || sel < 0) return;
      const pz = puzzle();
      if (pz.p[sel] || G.v[sel] === pz.s[sel] || (!G.v[sel] && !G.notes[sel])) return;
      G.v[sel] = 0; G.notes[sel] = 0; P.fx.type(); save(); draw();
    }

    function win() {
      G.over = "won"; save();
      const k = lvKey(), prev = P.store.get("sudoku-best-" + k, 0), secs = Math.max(1, G.secs);
      P.store.min("sudoku-best-" + k, secs); P.store.add("sudoku-won");
      if (k === "dificil" && G.err === 0) P.store.add("sudoku-perfect");
      P.logActivity();
      sel = -1; draw(); P.confetti(); P.fx.win();
      const rec = !prev || secs < prev, name = LEVELS[lv][1];
      const ui = P.sheet(`<h2>Quadrante resolvido! 🔢</h2><p class="subtitle">${name}${inf ? "" : " · do dia"}${rec ? " · novo recorde!" : ""}</p>
        ${P.statsHTML([[fmt(secs), "Tempo"], [`${G.err}/${MAX_ERR}`, "Erros"], [fmt(P.store.get("sudoku-best-" + k, secs)), "Melhor tempo"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button>${inf ? `<button class="pill" data-next>Outra grade</button>` : `<button class="pill" data-see>Ver grade</button>`}</div>`);
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Quadrante ${name}${inf ? "" : " do dia"}\nResolvi em ${fmt(secs)} com ${G.err} ${G.err === 1 ? "erro" : "erros"} 🔢`);
      if (inf) P.$("[data-next]", ui.el).onclick = () => { ui.close(); other(); };
      else P.$("[data-see]", ui.el).onclick = ui.close;
    }
    function lose() {
      G.over = "lost"; save(); draw(); P.fx.lose();
      const ui = P.sheet(`<h2>Fim de jogo</h2><p class="subtitle">Você cometeu ${MAX_ERR} erros.</p>
        <button class="pill wide" data-retry>Tentar de novo</button>`, () => { if (G.over === "lost") retry(); });
      P.$("[data-retry]", ui.el).onclick = ui.close;
    }
    function retry() { G = fresh(G.idx); sel = -1; save(); draw(); }
    function other() { const n = list().length; let i; do { i = Math.floor(Math.random() * n); } while (n > 1 && i === G.idx); G = fresh(i); sel = -1; save(); draw(); }

    grid.addEventListener("click", (e) => { const b = e.target.closest("[data-c]"); if (!b || G.over) return; sel = +b.dataset.c; draw(); });
    root.querySelectorAll("[data-d]").forEach((b) => (b.onclick = () => put(+b.dataset.d)));
    P.$("[data-erase]", root).onclick = erase;
    P.$("[data-notes]", root).onclick = () => { notesOn = !notesOn; P.fx.type(); draw(); };
    const ob = P.$("[data-other]", root); if (ob) ob.onclick = () => { P.fx.type(); other(); };
    root.querySelectorAll("[data-lv]").forEach((b) => (b.onclick = () => {
      if (+b.dataset.lv === lv) return;
      lv = +b.dataset.lv; P.store.set("sdk-level", lvKey()); P.fx.type();
      root.querySelectorAll("[data-lv]").forEach((x) => x.classList.toggle("on", +x.dataset.lv === lv));
      load();
    }));
    const onKey = (e) => {
      if (!document.body.contains(grid)) return document.removeEventListener("keydown", onKey);
      if (document.querySelector(".overlay") || e.ctrlKey || e.metaKey || e.altKey) return;
      if (/^[1-9]$/.test(e.key)) put(+e.key);
      else if (e.key === "Backspace" || e.key === "Delete" || e.key === "0") erase();
      else if (e.key === "n" || e.key === "N") { notesOn = !notesOn; draw(); }
      else if (e.key.startsWith("Arrow") && !G.over) {
        e.preventDefault();
        const r = sel < 0 ? 0 : sel / 9 | 0, c = sel < 0 ? 0 : sel % 9;
        const [dr, dc] = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] }[e.key] || [0, 0];
        sel = sel < 0 ? 0 : ((r + dr + 9) % 9) * 9 + ((c + dc + 9) % 9); draw();
      }
    };
    document.addEventListener("keydown", onKey);
    timer = setInterval(() => {
      if (!document.body.contains(grid)) return clearInterval(timer);
      if (G.over || document.hidden) return;
      G.secs++; save();
      P.$("[data-time]", root).textContent = fmt(G.secs);
    }, 1000);
    load();
    // Testes: estado da partida
    P.sdkState = () => ({ lv: lvKey(), inf, idx: G.idx, puzzle: puzzle().p.join(""), sol: puzzle().s.join(""), v: G.v.join(""), err: G.err, secs: G.secs, over: G.over });
  };
})();
