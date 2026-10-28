// Batalha Naval: motor (P.naval), tabuleiro (P.nboard, usado também na partida com amigo) e a partida contra o computador.
// Casas 0..99 = linha*10 + coluna, linha 0 em cima ("A1" = 0, "J10" = 99; letra = coluna, número = linha).
// Frota = lista de navios na ordem de SHIPS; cada navio = lista de casas. Navios retos e sem encostar (nem na diagonal).
// O app Android tem o mesmo motor (game/Naval.kt).
(function (root) {
  "use strict";
  const N = 10;
  const SHIPS = [["Porta-aviões", 5], ["Encouraçado", 4], ["Cruzador", 3], ["Submarino", 3], ["Destróier", 2]];
  const LENS = SHIPS.map((x) => x[1]);
  const name = (i) => "ABCDEFGHIJ"[i % N] + (Math.floor(i / N) + 1);
  /** As 8 vizinhas da casa (dentro do mar). */
  const around = (i) => {
    const r = Math.floor(i / N), c = i % N, out = [];
    for (let dr = -1; dr <= 1; dr++) for (let dc = -1; dc <= 1; dc++) {
      if (!dr && !dc) continue;
      const rr = r + dr, cc = c + dc;
      if (rr >= 0 && rr < N && cc >= 0 && cc < N) out.push(rr * N + cc);
    }
    return out;
  };
  /** As 4 vizinhas (cima, baixo, esquerda, direita). */
  const cross = (i) => {
    const r = Math.floor(i / N), c = i % N, out = [];
    if (r > 0) out.push(i - N); if (r < N - 1) out.push(i + N); if (c > 0) out.push(i - 1); if (c < N - 1) out.push(i + 1);
    return out;
  };
  /** Casas do navio de tamanho len começando em i (horizontal ou vertical) ou null se sai do mar. */
  function span(i, len, vert) {
    const r = Math.floor(i / N), c = i % N;
    if (vert ? r + len > N : c + len > N) return null;
    return Array.from({ length: len }, (_, k) => (vert ? i + k * N : i + k));
  }

  /** Frota sorteada (rnd = Math.random ou P.rng). */
  function place(rnd = Math.random) {
    for (;;) {
      const used = new Set(), fleet = [];
      let ok = true;
      for (const len of LENS) {
        let put = null;
        for (let t = 0; t < 200 && !put; t++) {
          const cells = span(Math.floor(rnd() * N * N), len, rnd() < 0.5);
          if (cells && cells.every((x) => !used.has(x) && around(x).every((y) => !used.has(y) || cells.includes(y)))) put = cells;
        }
        if (!put) { ok = false; break; }
        put.forEach((x) => used.add(x));
        fleet.push(put);
      }
      if (ok) return fleet;
    }
  }
  /** Frota válida: 5 navios dos tamanhos certos, retos, dentro do mar, sem sobrepor nem encostar. */
  function valid(fleet) {
    if (!Array.isArray(fleet) || fleet.length !== LENS.length) return false;
    const owner = new Map();
    for (let k = 0; k < fleet.length; k++) {
      const s = fleet[k];
      if (!Array.isArray(s) || s.length !== LENS[k] || !s.every((x) => Number.isInteger(x) && x >= 0 && x < N * N)) return false;
      const sorted = s.slice().sort((a, b) => a - b), vert = sorted.length > 1 && sorted[1] - sorted[0] === N;
      const want = span(sorted[0], s.length, vert);
      if (!want || want.join() !== sorted.join()) return false;
      for (const x of s) { if (owner.has(x)) return false; owner.set(x, k); }
    }
    for (const [x, k] of owner) for (const y of around(x)) if (owner.has(y) && owner.get(y) !== k) return false;
    return true;
  }
  const vertOf = (s) => s.length > 1 && s[1] - s[0] === N;
  /**
   * Põe o navio k começando na casa start (vertical ou não), encaixando na borda se passar do mar.
   * Devolve a frota nova, ou null se encostar/sobrepor outro navio.
   */
  function move(fleet, k, start, vert) {
    const len = fleet[k].length;
    let r = Math.floor(start / N), c = start % N;
    if (vert) r = Math.min(r, N - len); else c = Math.min(c, N - len);
    const nf = fleet.map((s, j) => (j === k ? span(r * N + c, len, vert) : s));
    return valid(nf) ? nf : null;
  }
  /** Tiro na casa c contra a frota, sabendo os tiros anteriores nesse mar. {hit, ship (índice ou -1), sunk, all}. */
  function fire(fleet, shots, c) {
    const done = new Set(shots); done.add(c);
    const ship = fleet.findIndex((s) => s.includes(c));
    if (ship < 0) return { hit: false, ship: -1, sunk: false, all: false };
    const sunk = fleet[ship].every((x) => done.has(x));
    const all = fleet.every((s) => s.every((x) => done.has(x)));
    return { hit: true, ship, sunk, all };
  }

  /**
   * Tiro do computador. know = {shots: Map casa→"h"|"m", sunk: [[casas]...], left: [tamanhos que ainda boiam]}.
   * level: 0 Fácil (quase ao acaso), 1 Médio (caça e mira em volta do acerto), 2 Difícil (mapa de probabilidade).
   */
  function aim(know, level, rnd = Math.random) {
    const shot = (i) => know.shots.has(i);
    const blocked = new Set();
    know.sunk.forEach((s) => s.forEach((x) => { blocked.add(x); if (level > 0) around(x).forEach((y) => blocked.add(y)); }));
    const sunkCells = new Set(know.sunk.flat());
    const open = [...know.shots].filter(([i, v]) => v === "h" && !sunkCells.has(i)).map(([i]) => i);
    const free = (i) => !shot(i) && !blocked.has(i);
    const all = Array.from({ length: N * N }, (_, i) => i).filter(free);
    const pick = (list) => list[Math.floor(rnd() * list.length)];
    if (!all.length) return Array.from({ length: N * N }, (_, i) => i).find((i) => !shot(i));
    if (level === 0) {
      const near = open.flatMap(cross).filter(free);
      return near.length && rnd() < 0.5 ? pick(near) : pick(all);
    }
    if (level === 1) {
      if (open.length) {
        // Dois acertos em linha: continua a linha; senão, uma das 4 vizinhas.
        const line = [];
        for (const a of open) for (const b of open) {
          if (b - a === 1 && Math.floor(a / N) === Math.floor(b / N)) { if (a % N > 0) line.push(a - 1); if (b % N < N - 1) line.push(b + 1); }
          if (b - a === N) { if (a >= N) line.push(a - N); if (b + N < N * N) line.push(b + N); }
        }
        const ext = line.filter((x) => x >= 0 && free(x));
        if (ext.length) return pick(ext);
        const near = open.flatMap(cross).filter(free);
        if (near.length) return pick(near);
      }
      const min = Math.min(...know.left);
      const par = all.filter((i) => (Math.floor(i / N) + (i % N)) % min === 0);
      return pick(par.length ? par : all);
    }
    // Difícil: conta em quantas posições possíveis dos navios que sobram cada casa aparece.
    const heat = new Array(N * N).fill(0);
    const ok = (i) => !blocked.has(i) && know.shots.get(i) !== "m";
    for (const len of know.left) for (let i = 0; i < N * N; i++) for (const vert of [false, true]) {
      const cells = span(i, len, vert);
      if (!cells || !cells.every(ok)) continue;
      const hits = cells.filter((x) => know.shots.get(x) === "h" && !sunkCells.has(x)).length;
      if (open.length && !hits) continue;
      const w = open.length ? Math.pow(20, hits) : 1;
      cells.forEach((x) => { if (!shot(x)) heat[x] += w; });
    }
    let best = -1, top = [];
    all.forEach((i) => { if (heat[i] > best) { best = heat[i]; top = [i]; } else if (heat[i] === best) top.push(i); });
    return pick(top);
  }

  const api = { N, SHIPS, LENS, name, around, cross, span, place, valid, move, vertOf, fire, aim };
  root.Naval = api;
  if (root.P) root.P.naval = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(typeof self !== "undefined" ? self : globalThis);

// ---------- tela (site) ----------
(function () {
  "use strict";
  if (typeof document === "undefined" || !window.P) return;
  const NV = () => P.naval;
  const N = 10;

  /** Mar 10×10 com letras em cima e números ao lado. id distingue os dois mares; enemy = casas tocáveis. */
  const boardHTML = (id, enemy, label) => {
    let h = `<div class="nv-board${enemy ? " enemy" : ""}" data-nb="${id}" role="grid" aria-label="${label}"><i class="nv-l"></i>`;
    for (let c = 0; c < N; c++) h += `<i class="nv-l">${"ABCDEFGHIJ"[c]}</i>`;
    for (let r = 0; r < N; r++) {
      h += `<i class="nv-l">${r + 1}</i>`;
      for (let c = 0; c < N; c++) {
        const i = r * N + c;
        h += enemy ? `<button class="nv-c" data-c="${i}" aria-label="${NV().name(i)}"></button>` : `<span class="nv-c" data-c="${i}"></span>`;
      }
    }
    return h + "</div>";
  };
  /**
   * Pinta um mar. o = {ships: [[casas]] a mostrar (os meus ou os afundados do outro), shots: Map casa→"h"|"m",
   * sunk: Set de casas afundadas, aim: casa mirada, last: último tiro, mine: true = mar meu (acerto em vermelho), can: tocável}.
   */
  function paint(el, o) {
    const ship = new Map();
    (o.ships || []).forEach((s, k) => s.forEach((x) => ship.set(x, k)));
    el.classList.toggle("off", !o.can);
    el.querySelectorAll("[data-c]").forEach((c) => {
      const i = +c.dataset.c, shot = o.shots.get(i), isShip = ship.has(i), sunk = o.sunk && o.sunk.has(i);
      let cls = "nv-c";
      if (sunk) cls += " sunk";
      else if (shot === "h") cls += o.mine ? " hit me" : " hit";
      else if (shot === "m") cls += " miss";
      else if (isShip) cls += " ship";
      if (isShip || sunk) cls += " body";
      if (o.sel && o.sel.has(i)) cls += " sel";
      if (i === o.aim) cls += " aim";
      if (i === o.last) cls += " last";
      c.className = cls;
      // Cápsula: cantos redondos só nas pontas do navio.
      if (isShip) {
        const k = ship.get(i), has = (x) => ship.get(x) === k, r = Math.floor(i / N), col = i % N;
        const up = r > 0 && has(i - N), dn = r < N - 1 && has(i + N), lf = col > 0 && has(i - 1), rt = col < N - 1 && has(i + 1);
        const R = "999px", q = "4px";
        c.style.borderRadius = [!up && !lf ? R : q, !up && !rt ? R : q, !dn && !rt ? R : q, !dn && !lf ? R : q].join(" ");
      } else c.style.borderRadius = "";
      const t = shot === "h" || sunk ? "✕" : shot === "m" ? "•" : "";
      if (c.textContent !== t) c.textContent = t;
      if (c.tagName === "BUTTON") c.setAttribute("aria-label", `${NV().name(i)}${shot === "h" ? ", acerto" : shot === "m" ? ", água" : sunk ? ", afundado" : ""}`);
    });
  }
  /**
   * Lista de uma frota com as cápsulas (design D). st[k] = {hits (casas atingidas, -1 = não sei), sunk}.
   * full = mostra o nome de cada navio.
   */
  function fleetHTML(title, st, mine) {
    return `<div class="nv-fleet"><h3>${title}</h3>${NV().SHIPS.map(([nm, len], k) => {
      const x = st[k] || { hits: 0, sunk: false };
      const seg = Array.from({ length: len }, (_, j) => `<i class="${x.sunk ? "sunk" : mine && j < x.hits ? "hit" : ""}"></i>`).join("");
      return `<div class="nv-ship${x.sunk ? " gone" : ""}"><span class="nv-nm">${nm}</span><span class="nv-cap${mine ? " mine" : ""}" aria-label="${nm}: ${x.sunk ? "afundado" : mine && x.hits ? `${x.hits} de ${len} atingidas` : "no mar"}">${seg}</span></div>`;
    }).join("")}</div>`;
  }
  /** Estado das frotas para fleetHTML: mine = minha frota + tiros que recebi; ou a do outro: só sei os afundados. */
  function fleetState(fleet, shotSet, sunkIdx) {
    if (fleet) return fleet.map((s) => { const hits = s.filter((x) => shotSet.has(x)).length; return { hits, sunk: hits === s.length }; });
    return NV().LENS.map((_, k) => ({ hits: 0, sunk: sunkIdx.has(k) }));
  }
  /** Moldura do jogo (layout A + frotas ao lado): mar do outro, meu mar e as duas frotas. */
  const layoutHTML = (oppLabel) => `<div class="nv-wrap" data-play><div class="nv-status"><p class="nv-turn" data-turn></p><span class="nv-count" data-count></span><span class="nv-act" data-act></span></div>
    <div class="nv-layout">
      <section class="nv-card nv-foe"><h2>Mar ${oppLabel}</h2><div class="nv-fit">${boardHTML("foe", true, "Mar do adversário")}</div></section>
      <section class="nv-card nv-mine"><h2>Sua frota</h2><div class="nv-fit">${boardHTML("me", false, "Seu mar")}</div></section>
      <aside class="nv-card nv-fleets" data-fleets></aside>
    </div></div>`;

  /**
   * Posicionar a frota: toque num navio para escolher, toque numa casa para levar o navio até lá, "Girar" deita ou levanta.
   * o = {get() frota atual, set(frota), extra: botões a mais (HTML)}. Devolve {draw, el}.
   */
  function placer(el, o) {
    let sel = -1;
    el.innerHTML = `<div class="nv-status"><p class="nv-turn mine" data-hint></p></div>
      <div class="nv-card nv-place"><h2>Sua frota</h2><div class="nv-fit">${boardHTML("pl", true, "Posicionar a frota")}</div></div>
      <div class="row-btns nv-pbtns"><button class="pill ghost" data-rot>Girar</button><button class="pill ghost" data-shuffle>Sortear</button>${o.extra || ""}</div>`;
    const board = P.$("[data-nb=pl]", el);
    const draw = () => {
      const f = o.get();
      paint(board, { ships: f, shots: new Map(), mine: true, can: true, sel: sel >= 0 ? new Set(f[sel]) : null });
      P.$("[data-hint]", el).textContent = sel < 0 ? "Toque num navio para mudar de lugar" : `${NV().SHIPS[sel][0]}: toque onde colocar`;
      P.$("[data-rot]", el).disabled = sel < 0;
    };
    const bad = (t) => { P.fx.invalid(); P.toast(t, 1800); };
    board.addEventListener("click", (e) => {
      const c = e.target.closest("[data-c]"); if (!c) return;
      const i = +c.dataset.c, f = o.get(), k = f.findIndex((x) => x.includes(i));
      if (k >= 0) { sel = k === sel ? -1 : k; P.fx.type(); return draw(); }
      if (sel < 0) return draw();
      const nf = NV().move(f, sel, i, NV().vertOf(f[sel]));
      if (!nf) return bad("Não cabe aí: os navios não podem encostar");
      o.set(nf); P.fx.type(); draw();
    });
    P.$("[data-rot]", el).onclick = () => {
      const f = o.get(); if (sel < 0) return;
      const nf = NV().move(f, sel, f[sel][0], !NV().vertOf(f[sel]));
      if (!nf) return bad("Não dá para girar aqui");
      o.set(nf); P.fx.type(); draw();
    };
    P.$("[data-shuffle]", el).onclick = () => { sel = -1; o.set(NV().place()); P.fx.type(); draw(); };
    draw();
    return { draw, el };
  }
  /** Som do tiro: água, acerto, afundou. */
  const shotFx = (res, mine) => {
    if (res.sunk) { P.tone([[392, 70], [523, 70], [659, 120]], 0.1); P.vibrate([30, 50, 60]); }
    else if (res.hit) { P.tone([[523, 50], [659, 90]], 0.09); P.vibrate(mine ? 30 : 60); }
    else P.tone([[mine ? 330 : 294, 70]], 0.06);
  };
  P.nboard = { boardHTML, paint, fleetHTML, fleetState, layoutHTML, placer, shotFx };

  // ---------- partida contra o computador ----------
  const LEVELS = [["facil", "Fácil"], ["medio", "Médio"], ["dificil", "Difícil"]];
  const HELP = "Afunde os 5 navios do computador antes que ele afunde os seus. Toque numa casa do mar dele para atirar: " +
    "verde é acerto, • é água e lilás é navio afundado. Vocês atiram um de cada vez. Os navios são retos e nunca encostam um no outro, nem na diagonal. " +
    "Fácil atira quase ao acaso, Médio mira em volta dos acertos e Difícil calcula onde é mais provável haver navio.";
  const SAVE = "nv-game";

  P.games.naval = function (root) {
    root.classList.add("nv-wide");
    root.innerHTML = P.topbar("Batalha Naval", { help: true }) + `<div class="game nv" data-body></div>`;
    const body = P.$("[data-body]", root);
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Batalha Naval</h2><p>${HELP}</p>`);
    let lv = Math.max(0, LEVELS.findIndex(([k]) => k === P.store.get("nv-level", "medio")));
    let g = null;

    // Antes da partida: nível e a frota sorteada (dá para sortear de novo).
    // Antes da partida: nível e a frota (o jogador põe cada navio onde quiser, ou sorteia).
    function setup() {
      g = null;
      let fleet = NV().place();
      const won = LEVELS.map(([k, n]) => `${n} ${P.store.get("naval-won-" + k, 0) || 0}`).join(" · ");
      body.innerHTML = `<div class="nv-wrap nv-setup">
          <div class="seg" data-lvs aria-label="Nível do computador">${LEVELS.map(([, n], i) => `<button data-lv="${i}" class="${i === lv ? "on" : ""}">${n}</button>`).join("")}</div>
          <div class="nv-wrap" data-place></div>
          <p class="muted center nv-wins">Vitórias: ${won}</p></div>`;
      placer(P.$("[data-place]", body), { get: () => fleet, set: (f) => (fleet = f), extra: `<button class="pill" data-start>Começar</button>` });
      body.querySelectorAll("[data-lv]").forEach((b) => (b.onclick = () => {
        lv = +b.dataset.lv; P.store.set("nv-level", LEVELS[lv][0]); P.fx.type();
        body.querySelectorAll("[data-lv]").forEach((x) => x.classList.toggle("on", +x.dataset.lv === lv));
      }));
      P.$("[data-start]", body).onclick = () => start({ lv: LEVELS[lv][0], me: fleet, ai: NV().place(), ps: [], as: [] });
    }

    const save = () => { if (g && !g.over) P.store.set(SAVE, { lv: g.lv, me: g.me, ai: g.ai, ps: g.ps, as: g.as }); };
    const forget = () => { try { localStorage.removeItem("pv-" + SAVE); } catch (_) {} };
    /** Tiros num mar → {shots Map, sunk Set de casas, sunkIdx Set de navios}. */
    function seaOf(fleet, list) {
      const shots = new Map(), set = new Set(list), sunk = new Set(), sunkIdx = new Set();
      list.forEach((c) => shots.set(c, fleet.some((s) => s.includes(c)) ? "h" : "m"));
      fleet.forEach((s, k) => { if (s.every((x) => set.has(x))) { sunkIdx.add(k); s.forEach((x) => sunk.add(x)); } });
      return { shots, sunk, sunkIdx, set, all: sunkIdx.size === fleet.length };
    }

    function start(sv) {
      const lvi = Math.max(0, LEVELS.findIndex(([k]) => k === sv.lv));
      g = { lv: LEVELS[lvi][0], lvi, me: sv.me, ai: sv.ai, ps: sv.ps.slice(), as: sv.as.slice(), thinking: false, over: null, token: {}, aim: null };
      body.innerHTML = layoutHTML("do computador");
      P.$("[data-act]", body).innerHTML = `<button class="pill ghost nv-mini" data-resign>Desistir</button>`;
      P.$("[data-nb=foe]", body).addEventListener("click", (e) => { const c = e.target.closest("[data-c]"); if (c) tap(+c.dataset.c); });
      P.$("[data-resign]", body).onclick = () => {
        if (!g) return;
        if (g.over) return setup();
        const ui = P.sheet(`<h2>Desistir da partida?</h2><p class="subtitle">O computador ganha esta.</p>
          <div class="row-btns"><button class="pill ghost" data-no>Continuar</button><button class="pill danger" data-yes>Desistir</button></div>`);
        P.$("[data-no]", ui.el).onclick = ui.close;
        P.$("[data-yes]", ui.el).onclick = () => { ui.close(); finish("ai", "desistiu"); };
      };
      save(); draw();
      // Vez de quem: o jogador começa; se o número de tiros dos dois é igual, é a vez do jogador.
      if (g.as.length < g.ps.length) think();
    }
    const myTurn = () => g && !g.over && !g.thinking && g.ps.length === g.as.length;

    function tap(c) {
      if (!myTurn() || !document.body.contains(body)) return;
      if (g.ps.includes(c)) { P.toast("Você já atirou aí"); return; }
      // 1º toque mira, 2º toque na mesma casa atira (no computador, o clique atira direto).
      const touch = window.matchMedia("(hover: none)").matches;
      if (touch && g.aim !== c) { g.aim = c; P.fx.type(); return draw(); }
      g.aim = null;
      const res = NV().fire(g.ai, g.ps, c);
      g.ps.push(c);
      shotFx(res, true);
      if (res.sunk) P.toast(`Você afundou o ${NV().SHIPS[res.ship][0].toLowerCase()}!`, 1800);
      save(); draw();
      if (res.all) return finish("me");
      think();
    }

    async function think() {
      if (!g || g.over) return;
      const c = g, token = (c.token = {});
      c.thinking = true; draw();
      await P.sleep(650);
      if (g !== c || c.token !== token || !document.body.contains(body)) return;
      const sea = seaOf(c.me, c.as);
      const know = { shots: sea.shots, sunk: [...sea.sunkIdx].map((k) => c.me[k]), left: NV().LENS.filter((_, k) => !sea.sunkIdx.has(k)) };
      const cell = NV().aim(know, c.lvi);
      const res = NV().fire(c.me, c.as, cell);
      c.as.push(cell);
      c.thinking = false;
      shotFx(res, false);
      if (res.sunk) P.toast(`O computador afundou seu ${NV().SHIPS[res.ship][0].toLowerCase()}`, 1800);
      save(); draw();
      if (res.all) finish("ai");
    }

    function draw() {
      if (!g || !document.body.contains(body)) return;
      const foe = seaOf(g.ai, g.ps), mine = seaOf(g.me, g.as);
      paint(P.$("[data-nb=foe]", body), { ships: [...foe.sunkIdx].map((k) => g.ai[k]).concat(g.over ? g.ai : []), shots: foe.shots, sunk: foe.sunk, aim: g.aim, last: g.ps[g.ps.length - 1], can: myTurn() });
      paint(P.$("[data-nb=me]", body), { ships: g.me, shots: mine.shots, sunk: mine.sunk, mine: true, last: g.as[g.as.length - 1] });
      P.$("[data-fleets]", body).innerHTML = fleetHTML("Computador", fleetState(null, null, foe.sunkIdx), false)
        + fleetHTML("Você", fleetState(g.me, mine.set), true);
      const t = P.$("[data-turn]", body);
      t.className = "nv-turn" + (myTurn() ? " mine" : "");
      t.textContent = g.over ? (g.over === "me" ? "Você venceu! ⚓" : "O computador venceu")
        : myTurn() ? (g.aim != null ? `Toque de novo em ${NV().name(g.aim)}` : "Sua vez · escolha uma casa") : "Computador mirando…";
      P.$("[data-count]", body).innerHTML = `<span>Computador <b>${5 - foe.sunkIdx.size}</b></span><span>Você <b>${5 - mine.sunkIdx.size}</b></span>`;
      const r = P.$("[data-resign]", body);
      r.textContent = g.over ? "Nova partida" : "Desistir";
      r.classList.toggle("ghost", !g.over);
      r.classList.toggle("accent", !!g.over);
    }

    function finish(who, why) {
      if (!g || g.over) return;
      g.over = who; g.thinking = false; forget();
      const win = who === "me";
      if (win) P.store.add("naval-won-" + g.lv);
      P.logActivity();
      draw();
      if (win) { P.confetti(); P.fx.win(); } else P.fx.lose();
      const c = g, hits = c.ps.filter((x) => c.ai.some((s) => s.includes(x))).length;
      setTimeout(() => {
        if (g !== c || !document.body.contains(body) || document.querySelector(".overlay")) return;
        const ui = P.sheet(`<div class="mp-sheet-ico" aria-hidden="true">${win ? "🏆" : "⚓"}</div>
          <h2>${win ? "Você venceu!" : "O computador venceu"}</h2>
          <p class="subtitle">${why === "desistiu" ? "Você desistiu. " : ""}Nível ${LEVELS[c.lvi][1]} · ${c.ps.length} tiros, ${hits} acertos (${Math.round((hits / Math.max(1, c.ps.length)) * 100)}%).</p>
          ${P.statsHTML(LEVELS.map(([k, n]) => [P.store.get("naval-won-" + k, 0) || 0, n]), "three")}
          <div class="row-btns"><button class="pill ghost" data-see>Ver mares</button><button class="pill" data-new>Nova partida</button></div>`);
        P.$("[data-see]", ui.el).onclick = ui.close;
        P.$("[data-new]", ui.el).onclick = () => { ui.close(); setup(); };
      }, win || why ? 700 : 1200);
    }

    const sv = P.store.get(SAVE, null);
    if (sv && NV().valid(sv.me) && NV().valid(sv.ai) && Array.isArray(sv.ps) && Array.isArray(sv.as)) start(sv); else setup();
    // Testes: estado e tiro direto.
    P.nvState = () => g && { lv: g.lv, me: g.me, ai: g.ai, ps: g.ps.slice(), as: g.as.slice(), over: g.over, thinking: g.thinking };
    P.nvShoot = (c) => { if (!myTurn()) return false; g.aim = c; tap(c); return true; };
  };
})();
