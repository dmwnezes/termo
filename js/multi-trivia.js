// Partida com amigo (site): Trivia (g), inspirada no Perguntados. Jogo de pontos (5/10/15 rodadas).
// Perguntas de shared/trivia.txt ("CATEGORIA|pergunta|certa|e1|e2|e3"), escolhidas pela mesma conta do app:
//   Q = perguntas válidas na ordem do arquivo; idx = ((SEED mod Q)·7919 mod Q + 104729 + k·577) mod Q, sem repetir.
//   Opções da pergunta K (rodada K = 0, 1, 2…): [certa, e1, e2, e3] giradas para a esquerda ((SEED + K·7) mod 4) vezes.
// Mensagem: {"t":"pick","id":ID,"r":K,"o":0..3 ou -1,"ms":TEMPO}. A rodada termina quando os dois picks chegaram.
(function () {
  "use strict";
  const X = () => P.mp._;
  const live = (s, r) => X().S === s && s.round === r;
  const meId = (s) => s.me.id;
  /** Categorias: cor e ícone fixos (iguais no app). */
  const CATS = {
    "Ciência": { c: "#5FB873", e: "🧪" }, "História": { c: "#E6C14F", e: "📜" }, "Geografia": { c: "#6FA8E8", e: "🌎" },
    "Esportes": { c: "#F0954A", e: "⚽" }, "Arte": { c: "#EE7FB5", e: "🎨" }, "Entretenimento": { c: "#B48CF0", e: "🎬" },
  };
  const CAT_ORDER = Object.keys(CATS);
  const Q_MS = () => X().debug().triviaMs || 15000;
  const SPIN = 1500, GAP = 3000;

  /** Linhas válidas: 6 campos não vazios e categoria conhecida, na ordem do arquivo. */
  function parse(text) {
    return String(text || "").split("\n").map((l) => l.trim()).filter((l) => l && !l.startsWith("#"))
      .map((l) => l.split("|").map((x) => x.trim()))
      .filter((f) => f.length === 6 && f.every(Boolean) && CATS[f[0]])
      .map((f) => ({ cat: f[0], q: f[1], a: f.slice(2) }));
  }
  /** Índices das N perguntas da partida. */
  function select(Q, seed, n) {
    const out = [], used = new Set();
    for (let k = 0; out.length < n && out.length < Q && k < Q * 4; k++) {
      const i = (((seed % Q) * 7919) % Q + 104729 + k * 577) % Q;
      if (!used.has(i)) { used.add(i); out.push(i); }
    }
    return out;
  }
  /** Opções da pergunta K já giradas e a posição da certa. */
  function options(a, seed, K) {
    const t = (seed + K * 7) % 4;
    return { opts: a.slice(t).concat(a.slice(0, t)), right: (4 - t) % 4 };
  }
  let listP = null;
  const list = () => listP || (listP = P.text("trivia.txt").then(parse).catch((e) => { listP = null; throw e; }));
  P.mp.trivia = { parse, select, options, list, CATS };

  function tvRound(s, startAt) {
    const { h, g } = X().ids(s);
    const r = (s.round = { kind: "g", seed: s.seed, n: s.rounds, startAt, host: h, guest: g, qs: null, cur: 0, qStart: [startAt],
      picks: [], res: [], pts: { me: 0, opp: 0 }, ms: { me: 0, opp: 0 }, sent: {}, result: null, phase: "" });
    r.ready = () => !!r.qs || !!r.result;
    r.waitTxt = "Carregando as perguntas…";
    list().then((items) => {
      if (!live(s, r)) return;
      const idx = select(items.length, s.seed, s.rounds);
      r.qs = idx.map((i, K) => Object.assign({ i }, items[i], options(items[i].a, s.seed, K)));
      r.n = r.qs.length;
      if (Date.now() > r.qStart[0]) r.qStart[0] = r.startAt = Date.now();
      check(s, r);
      tvDraw(s, r, true);
    }).catch(() => P.toast("Não foi possível carregar as perguntas"));
    s.view = "game";
    X().gameShell(s, "tv", `
        <div class="tv-top"><span class="tv-round" data-round></span><span class="tv-opp" data-opp></span></div>
        <div class="tv-stage" data-stage></div>
        <div class="timer tv-timer"><i data-bar></i></div>
        <div class="center" data-after></div>`);
    P.$("[data-stage]", s.root).addEventListener("click", (e) => { const b = e.target.closest("[data-o]"); if (b) tvPick(s, r, +b.dataset.o); });
    r.api = { redraw: () => tvDraw(s, r, true) };
    tvDraw(s, r, true);
    X().countdown(s, r);
    s.gameTimer = setInterval(() => tvTick(s, r), 100);
  }
  /** Pergunta em jogo: {k, ph} com ph = wait | spin | ask | reveal | end. */
  function phase(r) {
    const now = Date.now(), k = r.cur;
    if (!r.qs) return { k: 0, ph: "wait" };
    if (k >= r.n) return { k: r.n - 1, ph: r.result && now >= r.qStart[r.n] ? "end" : "reveal" };
    if (now < r.qStart[k]) return k > 0 ? { k: k - 1, ph: "reveal" } : { k, ph: "wait" };
    if (now < r.qStart[k] + SPIN) return { k, ph: "spin" };
    return { k, ph: "ask" };
  }
  const qAt = (r, k) => r.qStart[k] + SPIN;
  function tvPick(s, r, o) {
    const { k, ph } = phase(r);
    if (!live(s, r) || ph !== "ask" || r.sent[k] || r.result) return;
    r.sent[k] = { o };
    P.fx.type();
    X().publish({ t: "pick", id: meId(s), r: k, o, ms: Math.max(0, Math.min(Q_MS(), Date.now() - qAt(r, k))) });
    tvDraw(s, r, true);
  }
  function tvMsg(s, m) {
    const r = s.round;
    if (!r || r.kind !== "g" || m.t !== "pick" || (m.id !== r.host && m.id !== r.guest)) return;
    const K = Number(m.r);
    if (!Number.isInteger(K) || K < r.cur || K >= r.n) return;
    const p = (r.picks[K] = r.picks[K] || {});
    if (p[m.id]) return;
    const o = Number(m.o);
    p[m.id] = { o: Number.isInteger(o) && o >= 0 && o < 4 ? o : -1, ms: Math.max(0, Math.min(Q_MS(), Number(m.ms) || 0)) };
    if (m.id !== meId(s)) { P.tone([[523, 40]], 0.05); }
    check(s, r);
    tvDraw(s, r, true);
  }
  /** Fecha as perguntas que já têm os dois picks (o amigo que saiu conta como -1). */
  function check(s, r) {
    if (!r.qs || r.result) return;
    const o = X().opp(s), oid = o && o.id;
    while (r.cur < r.n) {
      const k = r.cur, p = r.picks[k] || {}, mine = p[meId(s)];
      const theirs = p[oid] || (s.oppLeft && mine ? { o: -1, ms: 0 } : null);
      if (!mine || !theirs) break;
      const right = r.qs[k].right, okMe = mine.o === right, okOpp = theirs.o === right;
      if (okMe) { r.pts.me++; r.ms.me += mine.ms; }
      if (okOpp) { r.pts.opp++; r.ms.opp += theirs.ms; }
      r.res[k] = { me: mine.o, opp: theirs.o, okMe, okOpp };
      r.cur = k + 1;
      r.qStart[k + 1] = Date.now() + GAP;
      if (okMe) P.fx.win(); else if (okOpp) P.tone([[392, 120], [330, 160]]); else P.tone([[330, 160]]);
    }
    if (r.cur >= r.n && !r.result) {
      const { me, opp } = r.pts;
      r.result = me > opp ? "me" : me < opp ? "opp" : r.ms.me < r.ms.opp ? "me" : r.ms.me > r.ms.opp ? "opp" : "draw";
      X().ptsDone(s, r, () => tvSheet(s, r), GAP);
    }
  }
  function tvTick(s, r) {
    if (!live(s, r)) return X().stopGame(s);
    const { k, ph } = phase(r);
    const bar = P.$("[data-bar]", s.root);
    if (ph === "ask") {
      const el = Date.now() - qAt(r, k), left = Q_MS() - el;
      if (bar) { bar.style.width = `${Math.max(0, (left / Q_MS()) * 100)}%`; bar.classList.toggle("low", left < 5000); }
      // Fim dos 15 s sem resposta: manda o próprio pick com -1.
      if (left <= 0 && !r.sent[k]) { r.sent[k] = { o: -1 }; X().publish({ t: "pick", id: meId(s), r: k, o: -1, ms: Q_MS() }); }
    } else if (bar) { bar.style.width = ph === "wait" || ph === "spin" ? "100%" : "0%"; bar.classList.remove("low"); }
    const key = `${k}|${ph}`;
    if (key !== r.phase) tvDraw(s, r, true);
  }
  const optCls = (r, k, i) => {
    const res = r.res[k], right = r.qs[k].right;
    if (!res) return "";
    if (i === right) return " right";
    return res.me === i || res.opp === i ? " wrong" : " dim";
  };
  function tvDraw(s, r, full) {
    if (!live(s, r) || !P.$(".tv", s.root)) return;
    const { k, ph } = phase(r), on = P.esc(X().oppName(s)), n = r.n, prev = r.phase;
    r.phase = `${k}|${ph}`;
    if (ph === "spin" && prev === r.phase) full = false; // não reinicia a roleta
    X().ptsScore(s, r, `pergunta ${Math.min(k + 1, n)}/${n}`);
    P.$("[data-round]", s.root).textContent = r.qs ? `Pergunta ${k + 1} de ${n}` : "Trivia";
    const op = P.$("[data-opp]", s.root), p = r.picks[k] || {}, o = X().opp(s);
    op.className = "tv-opp" + (s.oppLeft ? " gone" : ph === "ask" && o && p[o.id] ? " done" : "");
    op.innerHTML = s.oppLeft ? `<b>${on}</b> saiu` : ph === "ask" ? (o && p[o.id] ? `<b>${on}</b> respondeu ✓` : `<b>${on}</b> pensando…`) : `<b>${on}</b>: ${X().plural(r.pts.opp, "acerto", "acertos")}`;
    if (!full) return;
    const st = P.$("[data-stage]", s.root);
    if (!r.qs || ph === "wait") { st.innerHTML = `<div class="tv-wheel-box">${wheel()}</div><p class="tv-msg">${r.qs ? "Prepare-se…" : "Carregando…"}</p>`; }
    else if (ph === "spin") {
      const q = r.qs[k], i = CAT_ORDER.indexOf(q.cat);
      st.innerHTML = `<div class="tv-wheel-box spinning">${wheel()}</div><p class="tv-msg">Girando…</p>`;
      const w = P.$(".tv-wheel", st), fin = 360 * 4 - (i * 60 + 30) - (k * 37 % 20);
      requestAnimationFrame(() => requestAnimationFrame(() => { w.style.transform = `rotate(${fin}deg)`; }));
      setTimeout(() => { if (!live(s, r) || r.phase !== `${k}|spin`) return; const m = P.$(".tv-msg", st); if (m) { m.innerHTML = chip(q.cat); m.classList.add("land"); } }, SPIN - 200);
    } else {
      const q = r.qs[k], mine = r.sent[k], res = r.res[k];
      const tags = (i) => (res ? (res.me === i ? `<em class="tv-tag me">Você</em>` : "") + (res.opp === i ? `<em class="tv-tag opp">${on}</em>` : "") : "");
      let msg = "";
      if (res) msg = res.okMe && res.okOpp ? "Os dois acertaram!" : res.okMe ? "Você acertou!" : res.okOpp ? `${on} acertou` : res.me === -1 ? "Tempo esgotado" : "Ninguém acertou";
      else if (mine) msg = mine.o < 0 ? "Tempo esgotado" : `Esperando ${on}…`;
      st.innerHTML = `${chip(q.cat)}<div class="tv-q" style="--cc:${CATS[q.cat].c}">${P.esc(q.q)}</div>
        <div class="tv-opts">${q.opts.map((t, i) => `<button class="opt tv-opt${optCls(r, k, i)}${!res && mine && mine.o === i ? " sel" : ""}" data-o="${i}" ${res || mine ? "disabled" : ""}><span>${P.esc(t)}</span>${tags(i)}</button>`).join("")}</div>
        <p class="tv-msg ${res ? (res.okMe ? "win" : res.okOpp ? "lose" : "none") : ""}">${msg}</p>`;
    }
    X().afterBtn(P.$("[data-after]", s.root), ph === "end" ? r : {}, () => tvSheet(s, r));
  }
  const chip = (cat) => `<span class="tv-chip" style="--cc:${CATS[cat].c}">${CATS[cat].e} ${P.esc(cat)}</span>`;
  const wheel = () => `<div class="tv-pointer"></div><div class="tv-wheel" style="background:conic-gradient(${CAT_ORDER.map((c, i) => `${CATS[c].c} ${i * 60}deg ${(i + 1) * 60}deg`).join(",")})">
      ${CAT_ORDER.map((c, i) => `<span style="transform:rotate(${i * 60 + 30}deg) translateY(-70px) rotate(${-(i * 60 + 30)}deg)">${CATS[c].e}</span>`).join("")}<i>?</i></div>`;
  const secs = (ms) => `${(ms / 1000).toFixed(1).replace(".", ",")} s`;
  function tvSheet(s, r) {
    const en = P.esc(X().oppName(s));
    const rows = (r.qs || []).slice(0, r.n).map((q, k) => {
      const res = r.res[k] || {};
      return `<div class="tv-row"><i style="--cc:${CATS[q.cat].c}">${CATS[q.cat].e}</i><span>${P.esc(q.q)}<small>${P.esc(q.opts[q.right])}</small></span>
        <b class="${res.okMe ? "ok" : ""}">${res.okMe ? "✓" : "✗"}</b><b class="${res.okOpp ? "ok them" : ""}">${res.okOpp ? "✓" : "✗"}</b></div>`;
    }).join("");
    const tie = r.pts.me === r.pts.opp && r.pts.me > 0 ? `Empate nos acertos: desempate pelo tempo · Você ${secs(r.ms.me)} × ${en} ${secs(r.ms.opp)}` : `Tempo nos acertos: Você ${secs(r.ms.me)} · ${en} ${secs(r.ms.opp)}`;
    X().ptsSheet(s, r, { icon: "🧠", title: tie, extra: `<div class="tv-rows"><div class="tv-row head"><i></i><span>Pergunta</span><b>Você</b><b>${en}</b></div>${rows}</div>` });
  }
  P.mp.kinds.g = {
    round: tvRound, msg: tvMsg,
    bye: (s, r) => { check(s, r); tvDraw(s, r, true); },
    story: (s, r) => `${r.pts.me} × ${r.pts.opp} acertos`,
  };
})();
