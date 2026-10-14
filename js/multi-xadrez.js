// Partida com amigo (site): Xeque — Xadrez (modo k). Mensagens iguais às do app:
// {"t":"move","id":ID,"n":JOGADA,"u":"e2e4"} (n = número da jogada na rodada, a partir de 0) e {"t":"resign","id":ID}.
// Brancas = anfitrião quando a rodada da sala (s.rn) é par; brancas começam. Empates automáticos não contam na série.
(function () {
  "use strict";
  const X = () => P.mp._;
  const C = () => P.xadrez;
  const B = () => P.xboard;
  const live = (s, r) => X().S === s && s.round === r;
  const meId = (s) => s.me.id;
  const SIDE = { w: "brancas", b: "pretas" };
  const mySide = (s, r) => (r.whiteId === meId(s) ? "w" : "b");
  const idOf = (r, side) => (side === "w" ? r.whiteId : r.blackId);
  const turnOf = (r) => (r.g.st.w ? "w" : "b");
  const can = (s, r) => live(s, r) && !r.result && !r.pend && Date.now() >= r.startAt && idOf(r, turnOf(r)) === meId(s);

  function round(s, startAt) {
    const { h, g } = X().ids(s);
    const hostW = s.rn % 2 === 0;
    const r = (s.round = { kind: "k", n: s.rn, startAt, host: h, guest: g, whiteId: hostW ? h : g, blackId: hostW ? g : h,
      g: C().game([]), result: null, why: "", loser: null, pend: false, ctx: null });
    s.view = "game";
    X().gameShell(s, "xz xzm", `
        <div class="dm-status"><p class="dm-turn" data-turn></p></div>
        <div class="xz-side" data-top></div>${B().html(mySide(s, r) === "b")}<div class="xz-side" data-bot></div>
        <div class="dm-foot"><button class="pill ghost dm-resign" data-resign>Desistir</button></div>
        <div class="center" data-after></div>`);
    r.ctx = {
      get g() { return r.g; }, side: mySide(s, r), sel: null,
      can: () => can(s, r),
      redraw: () => draw(s, r),
      play: (m) => { r.pend = true; r.pendMove = m; X().publish({ t: "move", id: meId(s), n: r.g.list.length, u: C().uci(m) }); draw(s, r); },
    };
    P.$("[data-xboard]", s.root).addEventListener("click", (e) => { const c = e.target.closest("[data-sq]"); if (c) B().tap(r.ctx, +c.dataset.sq); });
    P.$("[data-resign]", s.root).onclick = () => {
      if (!live(s, r) || r.result) return;
      const ui = P.sheet(`<h2>Desistir da rodada?</h2><p class="subtitle">${P.esc(X().oppName(s))} ganha esta rodada.</p>
        <div class="row-btns"><button class="pill ghost" data-no>Continuar</button><button class="pill danger" data-yes>Desistir</button></div>`);
      P.$("[data-no]", ui.el).onclick = ui.close;
      P.$("[data-yes]", ui.el).onclick = () => { ui.close(); if (live(s, r) && !r.result) X().publish({ t: "resign", id: meId(s) }); };
    };
    r.api = { redraw: () => draw(s, r) };
    X().drawScore(s);
    draw(s, r);
    X().countdown(s, r);
  }

  function msg(s, m) {
    const r = s.round;
    if (!r || r.kind !== "k" || r.result) return;
    if (m.t === "resign") {
      if (m.id !== r.host && m.id !== r.guest) return;
      r.why = "desistiu"; r.loser = m.id;
      X().settle(s, r, m.id === meId(s) ? "opp" : "me", () => sheet(s, r), 1200);
      return draw(s, r);
    }
    if (m.t !== "move") return;
    const mine = m.id === meId(s);
    const ok = m.id === idOf(r, turnOf(r)) && Number(m.n) === r.g.list.length && typeof m.u === "string" ? B().push(r.g, m.u) : null;
    if (mine && r.pend) { r.pend = false; r.pendMove = null; }
    if (!ok) return draw(s, r);
    r.ctx.sel = null;
    B().moveFx(r.g, ok, mine);
    if (!mine) P.vibrate(20);
    const end = r.g.end;
    if (end) {
      r.why = end.why;
      if (end.res === "draw") X().settle(s, r, "draw", () => sheet(s, r));
      else { r.loser = idOf(r, end.res === "w" ? "b" : "w"); X().settle(s, r, idOf(r, end.res) === meId(s) ? "me" : "opp", () => sheet(s, r)); }
    }
    draw(s, r);
  }

  function draw(s, r) {
    if (!live(s, r) || !P.$(".xzm", s.root)) return;
    const me = mySide(s, r), them = me === "w" ? "b" : "w", on = P.esc(X().oppName(s)), started = Date.now() >= r.startAt;
    const t = turnOf(r), myTurn = idOf(r, t) === meId(s), chk = !r.result && C().inCheck(r.g.st);
    const turn = P.$("[data-turn]", s.root);
    turn.className = "dm-turn" + (!r.result && started && myTurn ? " mine" : "") + (chk ? " chk" : "");
    turn.innerHTML = r.result
      ? r.result === "draw" ? "Empate" : r.result === "me" ? "Você venceu!" : `${on} venceu`
      : !started ? `Você joga de ${SIDE[me]}` : (chk ? "Xeque! " : "") + (myTurn ? `Sua vez (${SIDE[me]})` : `Vez de ${on} (${SIDE[them]})`);
    const view = r.pend && r.pendMove ? Object.assign({}, r.g, { st: C().apply(r.g.st, r.pendMove), list: r.g.list.concat(r.pendMove) }) : r.g;
    B().paint(P.$("[data-xboard]", s.root), view, { sel: r.ctx.sel, can: can(s, r) });
    P.$("[data-top]", s.root).innerHTML = B().sideRow(r.g, them, on, !r.result && t === them);
    P.$("[data-bot]", s.root).innerHTML = B().sideRow(r.g, me, "Você", !r.result && t === me);
    const rs = P.$("[data-resign]", s.root); if (rs) rs.disabled = !!r.result || !started;
    X().afterBtn(P.$("[data-after]", s.root), r, () => sheet(s, r));
  }

  function detail(s, r) {
    const on = P.esc(X().oppName(s)), who = r.loser === meId(s) ? "Você" : on;
    const why = r.why === "mate" ? `${r.loser === meId(s) ? `${on} deu` : "Você deu"} xeque-mate.`
      : r.why === "desistiu" ? `${who} desistiu.`
      : r.why === "saiu" ? `${on} saiu da partida.`
      : `${B().WHY[r.why] || ""} Empate não conta na série.`;
    const n = Math.ceil(r.g.list.length / 2);
    return `${why} ${n} ${n === 1 ? "lance" : "lances"}.`;
  }
  const sheet = (s, r) => X().seriesSheet(s, r, { icon: r.result === "draw" ? "🤝" : "♞", detail: detail(s, r) });
  P.mp.kinds.k = {
    round, msg,
    bye: (s, r) => { if (!r.result) { r.why = "saiu"; X().settle(s, r, "me", () => sheet(s, r), 1200); } draw(s, r); },
    story: (s, r) => (r.result === "draw" ? "Empate" : r.why === "mate" ? "Xeque-mate!" : r.why === "saiu" ? "O amigo saiu da partida" : r.why === "desistiu" ? (r.loser === meId(s) ? "Desisti" : "O amigo desistiu") : X().plural(Math.ceil(r.g.list.length / 2), "lance", "lances")),
    // Para testes: joga o lance UCI como se fosse tocado.
    play: (u) => { const s = X().S, r = s && s.round; if (!r || r.kind !== "k" || !can(s, r)) return false; const m = C().parse(r.g.st, u); if (!m) return false; r.ctx.play(m); return true; },
  };
})();
