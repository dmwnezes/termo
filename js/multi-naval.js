// Partida com amigo (site): Batalha Naval (modo n). Mensagens iguais às do app:
// {"t":"ready","id":ID} frota posicionada · {"t":"shot","id":ID,"n":K,"c":CASA} tiro número K da rodada (a partir de 0)
// {"t":"res","id":ID,"n":K,"c":CASA,"h":0|1,"k":NAVIO,"s":[casas do navio afundado],"w":1 se acabou a frota} resposta de quem levou o tiro
// {"t":"fleet","id":ID,"f":[[casas]...]} frota mostrada no fim · {"t":"resign","id":ID}.
// Cada um guarda a própria frota (o outro nunca recebe as casas antes do fim). Atira primeiro o anfitrião nas rodadas pares; um tiro de cada vez.
(function () {
  "use strict";
  const X = () => P.mp._;
  const NV = () => P.naval;
  const B = () => P.nboard;
  const live = (s, r) => X().S === s && s.round === r;
  const meId = (s) => s.me.id;
  const other = (r, id) => (id === r.host ? r.guest : r.host);
  const bothReady = (r) => !!(r.rdy[r.host] && r.rdy[r.guest]);
  /** Quem dá o tiro número k. */
  const shooterOf = (r, k) => (k % 2 === 0 ? r.firstId : other(r, r.firstId));
  const resolved = (r) => r.shots.every((x) => x.res);
  const myTurn = (s, r) => live(s, r) && !r.result && bothReady(r) && resolved(r) && !r.pend && Date.now() >= r.startAt && shooterOf(r, r.shots.length) === meId(s);

  // A frota da rodada fica guardada (recarregar a página não perde os navios).
  const keyOf = (s, r) => `${s.room}-${r.n}-${meId(s)}`;
  function savedFleet(s, r) {
    const v = P.store.get("nv-mp", null);
    return v && v.k === keyOf(s, r) && NV().valid(v.f) ? v.f : null;
  }
  const keepFleet = (s, r) => P.store.set("nv-mp", { k: keyOf(s, r), f: r.fleet });

  function round(s, startAt) {
    const { h, g } = X().ids(s);
    const r = (s.round = { kind: "n", n: s.rn, startAt, host: h, guest: g, firstId: s.rn % 2 === 0 ? h : g,
      fleet: null, rdy: {}, shots: [], oppFleet: null, sentFleet: false, result: null, why: "", loser: null, pend: false, aim: null });
    r.fleet = savedFleet(s, r) || NV().place();
    keepFleet(s, r);
    s.view = "game";
    X().gameShell(s, "nv nvm", B().layoutHTML(`de ${P.esc(X().oppName(s))}`) + `<div class="nv-foot" data-foot></div><div class="center" data-after></div>`);
    s.root.classList.add("nv-wide");
    P.$("[data-nb=foe]", s.root).addEventListener("click", (e) => { const c = e.target.closest("[data-c]"); if (c) tap(s, r, +c.dataset.c); });
    r.api = { redraw: () => draw(s, r) };
    X().drawScore(s);
    draw(s, r);
    X().countdown(s, r);
  }

  function tap(s, r, c) {
    if (!myTurn(s, r)) return;
    if (r.shots.some((x) => x.by === meId(s) && x.c === c)) { P.toast("Você já atirou aí"); return; }
    const touch = window.matchMedia("(hover: none)").matches;
    if (touch && r.aim !== c) { r.aim = c; P.fx.type(); return draw(s, r); }
    r.aim = null; r.pend = true;
    X().publish({ t: "shot", id: meId(s), n: r.shots.length, c });
    draw(s, r);
  }

  /** Responde um tiro que eu levei (depois de ler todo o histórico, para não responder duas vezes). */
  function answer(s, r, k) {
    setTimeout(() => {
      const x = r.shots[k];
      if (!live(s, r) || !x || x.res || x.answered) return;
      x.answered = true;
      const before = r.shots.slice(0, k).filter((y) => y.by !== meId(s)).map((y) => y.c);
      const f = NV().fire(r.fleet, before, x.c);
      X().publish({ t: "res", id: meId(s), n: k, c: x.c, h: f.hit ? 1 : 0, k: f.ship, s: f.sunk ? r.fleet[f.ship] : [], w: f.all ? 1 : 0 });
    }, 0);
  }

  function revealFleet(s, r) {
    if (r.sentFleet) return;
    r.sentFleet = true;
    X().publish({ t: "fleet", id: meId(s), f: r.fleet });
  }

  function msg(s, m, isLive) {
    const r = s.round;
    if (!r || r.kind !== "n") return;
    const inRoom = m.id === r.host || m.id === r.guest;
    if (!inRoom) return;
    if (m.t === "fleet") {
      if (m.id !== meId(s) && NV().valid(m.f)) { r.oppFleet = m.f; draw(s, r); }
      return;
    }
    if (r.result) return;
    if (m.t === "resign") {
      r.why = "desistiu"; r.loser = m.id;
      X().settle(s, r, m.id === meId(s) ? "opp" : "me", () => sheet(s, r), 1200);
      revealFleet(s, r);
      return draw(s, r);
    }
    if (m.t === "ready") {
      r.rdy[m.id] = true;
      if (bothReady(r) && isLive) { P.tone([[523, 60], [659, 80]], 0.07); P.vibrate(25); }
      return draw(s, r);
    }
    if (m.t === "shot") {
      const k = Number(m.n), c = Number(m.c);
      const ok = bothReady(r) && k === r.shots.length && resolved(r) && m.id === shooterOf(r, k) && Number.isInteger(c) && c >= 0 && c < 100
        && !r.shots.some((x) => x.by === m.id && x.c === c);
      if (!ok) { if (m.id === meId(s)) { r.pend = false; draw(s, r); } return; }
      r.shots.push({ by: m.id, c, res: null });
      if (m.id !== meId(s)) answer(s, r, k);
      return draw(s, r);
    }
    if (m.t === "res") {
      const k = Number(m.n), x = r.shots[k];
      if (!x || x.res || m.id !== other(r, x.by) || Number(m.c) !== x.c) return;
      const sunk = Array.isArray(m.s) ? m.s.map(Number).filter((v) => v >= 0 && v < 100) : [];
      x.res = { h: m.h === 1, k: Number.isInteger(m.k) ? m.k : -1, s: sunk, w: m.w === 1 };
      if (x.by === meId(s)) r.pend = false;
      const mine = x.by === meId(s);
      if (isLive) {
        B().shotFx({ hit: x.res.h, sunk: sunk.length > 0 }, mine);
        if (sunk.length && x.res.k >= 0 && NV().SHIPS[x.res.k]) {
          const nm = NV().SHIPS[x.res.k][0].toLowerCase();
          P.toast(mine ? `Você afundou o ${nm}!` : `${X().oppName(s)} afundou seu ${nm}`, 1800);
        }
      }
      if (x.res.w) {
        r.why = "frota"; r.loser = other(r, x.by);
        X().settle(s, r, mine ? "me" : "opp", () => sheet(s, r));
        revealFleet(s, r);
      }
      return draw(s, r);
    }
  }

  /** Os dois mares e as frotas, do meu ponto de vista. */
  function seas(s, r) {
    const me = meId(s);
    const foe = { shots: new Map(), sunk: new Set(), ships: [], sunkIdx: new Set() };
    const mine = { shots: new Map(), sunk: new Set(), set: new Set() };
    r.shots.forEach((x) => {
      if (!x.res) return;
      if (x.by === me) {
        foe.shots.set(x.c, x.res.h ? "h" : "m");
        if (x.res.s.length) { x.res.s.forEach((y) => foe.sunk.add(y)); foe.ships.push(x.res.s); if (x.res.k >= 0) foe.sunkIdx.add(x.res.k); }
      } else { mine.shots.set(x.c, x.res.h ? "h" : "m"); mine.set.add(x.c); }
    });
    r.fleet.forEach((sh) => { if (sh.every((y) => mine.set.has(y))) sh.forEach((y) => mine.sunk.add(y)); });
    return { foe, mine };
  }

  function draw(s, r) {
    if (!live(s, r) || !P.$(".nvm", s.root)) return;
    const on = P.esc(X().oppName(s)), started = Date.now() >= r.startAt, { foe, mine } = seas(s, r);
    const placing = !r.rdy[meId(s)], waitOpp = !placing && !bothReady(r);
    B().paint(P.$("[data-nb=foe]", s.root), { ships: foe.ships.concat(r.oppFleet || []), shots: foe.shots, sunk: foe.sunk, aim: r.aim, can: myTurn(s, r) });
    const last = [...r.shots].reverse().find((x) => x.by !== meId(s) && x.res);
    B().paint(P.$("[data-nb=me]", s.root), { ships: r.fleet, shots: mine.shots, sunk: mine.sunk, mine: true, last: last && last.c });
    P.$("[data-fleets]", s.root).innerHTML = B().fleetHTML(on, B().fleetState(null, null, foe.sunkIdx), false) + B().fleetHTML("Você", B().fleetState(r.fleet, mine.set), true);
    P.$("[data-count]", s.root).innerHTML = `<span>${on} <b>${5 - foe.sunkIdx.size}</b></span><span>Você <b>${5 - r.fleet.filter((sh) => sh.every((y) => mine.set.has(y))).length}</b></span>`;
    const turn = P.$("[data-turn]", s.root), mt = myTurn(s, r);
    turn.className = "nv-turn" + (mt ? " mine" : "");
    turn.textContent = r.result ? (r.result === "me" ? "Você venceu! ⚓" : `${X().oppName(s)} venceu`)
      : !started ? (r.firstId === meId(s) ? "Você atira primeiro" : `${X().oppName(s)} atira primeiro`)
      : placing ? "Posicione sua frota" : waitOpp ? `Esperando ${X().oppName(s)} posicionar…`
      : mt ? (r.aim != null ? `Toque de novo em ${NV().name(r.aim)} para atirar` : "Sua vez · escolha uma casa")
      : r.pend ? "Atirando…" : `Vez de ${X().oppName(s)}`;
    const foot = P.$("[data-foot]", s.root);
    const want = r.result ? "" : placing ? "place" : "play";
    if (foot.dataset.v !== want) {
      foot.dataset.v = want;
      foot.innerHTML = want === "place"
        ? `<div class="row-btns nv-btns"><button class="pill ghost" data-shuffle>Sortear de novo</button><button class="pill" data-ready>Pronto</button></div>`
        : want === "play" ? `<button class="pill ghost dm-resign" data-resign>Desistir</button>` : "";
      const sh = P.$("[data-shuffle]", foot), rd = P.$("[data-ready]", foot), rs = P.$("[data-resign]", foot);
      if (sh) sh.onclick = () => { if (!live(s, r) || r.rdy[meId(s)]) return; r.fleet = NV().place(); keepFleet(s, r); P.fx.type(); draw(s, r); };
      if (rd) rd.onclick = () => { if (!live(s, r) || r.rdy[meId(s)] || Date.now() < r.startAt) return; rd.disabled = true; keepFleet(s, r); X().publish({ t: "ready", id: meId(s) }); };
      if (rs) rs.onclick = () => {
        if (!live(s, r) || r.result) return;
        const ui = P.sheet(`<h2>Desistir da rodada?</h2><p class="subtitle">${on} ganha esta rodada.</p>
          <div class="row-btns"><button class="pill ghost" data-no>Continuar</button><button class="pill danger" data-yes>Desistir</button></div>`);
        P.$("[data-no]", ui.el).onclick = ui.close;
        P.$("[data-yes]", ui.el).onclick = () => { ui.close(); if (live(s, r) && !r.result) X().publish({ t: "resign", id: meId(s) }); };
      };
    }
    const rd = P.$("[data-ready]", foot); if (rd) rd.disabled = !started;
    X().afterBtn(P.$("[data-after]", s.root), r, () => sheet(s, r));
  }

  function stats(s, r) {
    const mine = r.shots.filter((x) => x.by === meId(s) && x.res);
    return { n: mine.length, hits: mine.filter((x) => x.res.h).length };
  }
  function detail(s, r) {
    const on = P.esc(X().oppName(s)), st = stats(s, r);
    const why = r.why === "frota" ? (r.loser === meId(s) ? `${on} afundou sua frota.` : "Você afundou a frota toda.")
      : r.why === "desistiu" ? `${r.loser === meId(s) ? "Você" : on} desistiu.`
      : r.why === "saiu" ? `${on} saiu da partida.` : "";
    return `${why} ${st.n} tiros, ${st.hits} acertos.`.trim();
  }
  const sheet = (s, r) => X().seriesSheet(s, r, { icon: "⚓", detail: detail(s, r) });
  P.mp.kinds.n = {
    round, msg,
    bye: (s, r) => { if (!r.result) { r.why = "saiu"; X().settle(s, r, "me", () => sheet(s, r), 1200); } draw(s, r); },
    story: (s, r) => { const st = stats(s, r); return `${st.hits} acertos em ${st.n} tiros`; },
    // Para testes: atira na casa como se fosse tocada.
    shoot: (c) => { const s = X().S, r = s && s.round; if (!r || r.kind !== "n" || !myTurn(s, r)) return false; r.aim = c; tap(s, r, c); return true; },
    ready: () => { const s = X().S, r = s && s.round; if (!r || r.kind !== "n" || r.rdy[meId(s)]) return false; X().publish({ t: "ready", id: meId(s) }); return true; },
    state: () => { const s = X().S, r = s && s.round; return r && r.kind === "n" ? { fleet: r.fleet, shots: r.shots, ready: r.rdy, result: r.result, first: r.firstId } : null; },
  };
})();
