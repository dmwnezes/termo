// Partida com amigo (site): Adedonha (s), inspirada no StopotS. Jogo de pontos (3/5/7 rodadas).
// Mensagens (iguais no app), RN = rodada da sala, R = rodada da partida:
//   {"t":"sround","n":RN,"k":R,"l":"M","c":[categorias]}  anfitrião sorteia letra e (na partida) 6 categorias
//   {"t":"stop","id":ID|"tempo","n":RN,"k":R}              o PRIMEIRO da rodada trava os campos
//   {"t":"ans","id":ID,"n":RN,"k":R,"a":[respostas]}       cada um manda as suas
//   {"t":"vote","id":ID,"n":RN,"k":R,"v":[1,0,…]}          votos sobre as respostas do AMIGO
// Pontos: válida (começa com a letra E o voto do amigo é 1) → 10, ou 5 se igual à resposta válida do outro; inválida → 0.
(function () {
  "use strict";
  const X = () => P.mp._;
  const live = (s, r) => X().S === s && s.round === r;
  const meId = (s) => s.me.id;
  const LETTERS = "ABCDEFGHIJLMNOPRSTUV".split("");
  const FILL = () => X().debug().stopMs || 90000;
  const VOTE = () => X().debug().voteMs || 40000;
  const NEXT = () => X().debug().nextMs || 6000;
  const SPIN = 1800, NCATS = 6;

  let catsP = null;
  const cats = () => catsP || (catsP = P.text("adedonha.txt")
    .then((t) => t.split("\n").map((l) => l.trim()).filter((l) => l && !l.startsWith("#")))
    .catch((e) => { catsP = null; throw e; }));
  /** Comparação de respostas: sem acento, minúsculas, sem espaços extras. */
  const key = (a) => P.norm(String(a || "")).toLowerCase().trim().replace(/\s+/g, " ");
  /** Validação automática: não vazia e começa com a letra (sem acento/maiúsculas). */
  const auto = (a, l) => { const t = key(a); return t.length > 0 && t[0] === l.toLowerCase(); };
  /** Pontos da rodada: mine/theirs = respostas; vOnMine = votos do amigo sobre as minhas; vOnTheirs = os meus sobre as dele. */
  function scoreRound(l, mine, theirs, vOnMine, vOnTheirs, nc) {
    const me = [], opp = [];
    for (let i = 0; i < nc; i++) {
      const okMe = auto(mine[i], l) && Number((vOnMine || [])[i]) !== 0;
      const okOpp = auto(theirs[i], l) && Number((vOnTheirs || [])[i]) !== 0;
      const same = okMe && okOpp && key(mine[i]) === key(theirs[i]);
      me.push(okMe ? (same ? 5 : 10) : 0);
      opp.push(okOpp ? (same ? 5 : 10) : 0);
    }
    return { me, opp, tm: me.reduce((a, b) => a + b, 0), to: opp.reduce((a, b) => a + b, 0) };
  }
  P.mp.adedonha = { key, auto, scoreRound, LETTERS };

  function adRound(s, startAt) {
    const { h, g } = X().ids(s);
    const r = (s.round = { kind: "s", n: s.rounds, rn: s.rn, startAt, host: h, guest: g, k: -1, R: [], cats: null,
      pts: { me: 0, opp: 0 }, sentR: {}, result: null, phase: "" });
    s.view = "game";
    X().gameShell(s, "ad", `
        <div class="ad-head"><span class="ad-round" data-round></span><b class="ad-clock" data-clock></b></div>
        <div class="timer ad-timer"><i data-bar style="width:100%"></i></div>
        <div class="ad-stage" data-stage></div>
        <div class="center" data-after></div>`);
    const st = P.$("[data-stage]", s.root);
    st.addEventListener("input", (e) => { const f = e.target.closest("[data-f]"); if (f) adInput(s, r, +f.dataset.f, f.value); });
    st.addEventListener("keydown", (e) => {
      const f = e.target.closest("[data-f]"); if (!f || e.key !== "Enter") return;
      e.preventDefault();
      const nx = P.$(`[data-f="${+f.dataset.f + 1}"]`, st);
      if (nx) nx.focus(); else P.$("[data-stop]", st)?.click();
    });
    st.addEventListener("click", (e) => {
      const t = e.target.closest("[data-v]");
      if (t) return adToggle(s, r, +t.dataset.v);
      if (e.target.closest("[data-stop]")) return adStop(s, r);
      if (e.target.closest("[data-confirm]")) return adSendVote(s, r);
    });
    r.api = { redraw: () => adDraw(s, r, true) };
    adDraw(s, r, true);
    X().countdown(s, r);
    s.gameTimer = setInterval(() => adTick(s, r), 90);
  }
  const cur = (r) => r.R[r.k] || null;
  /** Fase da rodada atual: wait | spin | fill | locked | vote | waitvote | result. */
  function phase(s, r) {
    const c = cur(r);
    if (!c) return "wait";
    if (c.sc) return "result";
    if (c.voteAt) return c.sent.vote ? "waitvote" : "vote";
    if (c.stop) return "locked";
    return Date.now() < c.at ? "spin" : "fill";
  }

  /** Anfitrião: sorteia a letra da rodada R (sem repetir na partida) e, na 1ª, as 6 categorias da partida. */
  function hostSend(s, r, k) {
    if (r.sentR[k]) return;
    r.sentR[k] = true;
    cats().then((list) => {
      if (!live(s, r)) return;
      if (!r.cats) r.cats = P.shuffle(list).slice(0, NCATS);
      const used = r.R.map((x) => x && x.l), pool = LETTERS.filter((l) => !used.includes(l));
      const l = (pool.length ? pool : LETTERS)[Math.floor(Math.random() * (pool.length || LETTERS.length))];
      X().publish({ t: "sround", n: r.rn, k, l, c: r.cats });
    }).catch(() => { r.sentR[k] = false; P.toast("Não foi possível carregar as categorias"); });
  }

  function adMsg(s, m) {
    const r = s.round;
    if (!r || r.kind !== "s" || r.result || Number(m.n) !== r.rn) return;
    const k = Number(m.k), c = cur(r);
    if (m.t === "sround") {
      if (k !== r.k + 1 || k >= r.n || typeof m.l !== "string" || !/^[A-Z]$/.test(m.l)) return;
      const cs = Array.isArray(m.c) && m.c.length ? m.c.map((x) => String(x).slice(0, 40)).slice(0, 10) : r.cats;
      if (!cs || !cs.length) return;
      r.cats = cs;
      r.R[k] = { l: m.l, c: cs, at: Math.max(Date.now(), r.startAt) + SPIN, stop: null, ans: {}, votes: {}, voteAt: 0, vote: [], sent: {}, inputs: cs.map(() => ""), sc: null, doneAt: 0 };
      r.k = k;
      P.tone([[440, 50], [554, 50], [659, 70]], 0.06);
      return adDraw(s, r, true);
    }
    if (!c || k !== r.k) return;
    const who = m.id === r.host || m.id === r.guest;
    if (m.t === "stop") {
      if (c.stop || (!who && m.id !== "tempo")) return;
      c.stop = m.id;
      if (m.id === meId(s)) P.fx.win(); else { P.tone([[392, 90], [330, 130]], 0.1); P.vibrate([40, 40, 40]); }
      adSendAns(s, r);
      return adDraw(s, r, true);
    }
    if (!who) return;
    if (m.t === "ans") {
      if (c.ans[m.id]) return;
      const a = Array.isArray(m.a) ? m.a : [];
      c.ans[m.id] = c.c.map((_, i) => String(a[i] == null ? "" : a[i]).slice(0, 40));
      return adCheck(s, r);
    }
    if (m.t === "vote") {
      if (c.votes[m.id]) return;
      const v = Array.isArray(m.v) ? m.v : [];
      c.votes[m.id] = c.c.map((_, i) => (Number(v[i]) === 0 ? 0 : 1));
      return adCheck(s, r);
    }
  }
  /** Avança: respostas dos dois → votação; votos dos dois → pontos. O amigo que saiu conta como vazio / tudo ✓. */
  function adCheck(s, r) {
    const c = cur(r); if (!c) return;
    const o = X().opp(s), oid = o && o.id, me = meId(s);
    const theirAns = c.ans[oid] || (s.oppLeft && c.ans[me] ? c.c.map(() => "") : null);
    if (!c.voteAt && c.ans[me] && theirAns) {
      if (!c.ans[oid]) c.ans[oid || "_"] = theirAns;
      c.voteAt = Date.now();
      c.vote = theirAns.map((a) => (auto(a, c.l) ? 1 : 0));
    }
    const theirVote = c.votes[oid] || (s.oppLeft && c.votes[me] ? c.c.map(() => 1) : null);
    if (c.voteAt && !c.sc && c.votes[me] && theirVote) {
      const mine = c.ans[me], theirs = c.ans[oid] || c.ans._ || c.c.map(() => "");
      c.sc = scoreRound(c.l, mine, theirs, theirVote, c.votes[me], c.c.length);
      r.pts.me += c.sc.tm; r.pts.opp += c.sc.to;
      c.doneAt = Date.now();
      if (c.sc.tm > c.sc.to) P.fx.win(); else P.tone([[523, 60], [659, 80]], 0.07);
      if (r.k >= r.n - 1 || s.oppLeft) { // última rodada (ou o amigo saiu): fim da partida
        r.result = r.pts.me > r.pts.opp ? "me" : r.pts.me < r.pts.opp ? "opp" : "draw";
        X().ptsDone(s, r, () => adSheet(s, r), 4500);
      }
    }
    adDraw(s, r, true);
  }
  function adSendAns(s, r) {
    const c = cur(r); if (!c || c.sent.ans) return;
    c.sent.ans = true;
    X().publish({ t: "ans", id: meId(s), n: r.rn, k: r.k, a: c.inputs.map((x) => String(x || "").trim().replace(/\s+/g, " ").slice(0, 40)) });
  }
  function adSendVote(s, r) {
    const c = cur(r); if (!live(s, r) || !c || !c.voteAt || c.sent.vote) return;
    c.sent.vote = true;
    P.fx.type();
    X().publish({ t: "vote", id: meId(s), n: r.rn, k: r.k, v: c.vote.slice() });
    adDraw(s, r, true);
  }
  function adToggle(s, r, i) {
    const c = cur(r); if (!c || phase(s, r) !== "vote") return;
    const o = X().opp(s), a = (c.ans[o && o.id] || c.ans._ || [])[i];
    if (!auto(a, c.l)) return; // ✗ automático, travado
    c.vote[i] = c.vote[i] ? 0 : 1;
    P.fx.type();
    const b = P.$(`[data-v="${i}"]`, s.root);
    if (b) { b.classList.toggle("no", !c.vote[i]); b.textContent = c.vote[i] ? "✓" : "✗"; b.closest(".ad-vrow").classList.toggle("no", !c.vote[i]); }
  }
  const allOk = (c) => c.inputs.every((x) => auto(x, c.l));
  function adInput(s, r, i, v) {
    const c = cur(r); if (!c || phase(s, r) !== "fill") return;
    c.inputs[i] = v;
    const f = P.$(`[data-f="${i}"]`, s.root);
    if (f) { const t = v.trim(); f.closest(".ad-field").classList.toggle("ok", auto(t, c.l)); f.closest(".ad-field").classList.toggle("bad", !!t && !auto(t, c.l)); }
    const b = P.$("[data-stop]", s.root); if (b) b.disabled = !allOk(c) || !!c.sent.stop;
  }
  function adStop(s, r) {
    const c = cur(r);
    if (!live(s, r) || !c || phase(s, r) !== "fill" || c.sent.stop || !allOk(c)) return;
    c.sent.stop = true;
    X().publish({ t: "stop", id: meId(s), n: r.rn, k: r.k });
    const b = P.$("[data-stop]", s.root); if (b) { b.disabled = true; b.textContent = "STOP!…"; }
  }
  function adTick(s, r) {
    if (!live(s, r)) return X().stopGame(s);
    const now = Date.now(), c = cur(r), ph = phase(s, r);
    // Rodada 0 depois da contagem; as outras 6 s depois do resultado (só o anfitrião sorteia).
    if (s.me.host && !r.result) {
      if (r.k < 0 && now >= r.startAt) hostSend(s, r, 0);
      else if (c && c.sc && now >= c.doneAt + NEXT() && r.k + 1 < r.n) hostSend(s, r, r.k + 1);
    }
    const bar = P.$("[data-bar]", s.root), clock = P.$("[data-clock]", s.root);
    let left = null, total = 1;
    if (ph === "fill") {
      const el = now - c.at; left = FILL() - el; total = FILL();
      // Tempo esgotado: o anfitrião manda o "stop" do tempo (o convidado só se o anfitrião saiu ou passaram 10 s a mais).
      if (left <= 0 && !c.sent.tempo && (s.me.host || s.oppLeft || el > FILL() + 10000)) { c.sent.tempo = true; X().publish({ t: "stop", id: "tempo", n: r.rn, k: r.k }); }
    } else if (ph === "vote") {
      left = VOTE() - (now - c.voteAt); total = VOTE();
      if (left <= 0) adSendVote(s, r); // quem não votou a tempo: vale o que está marcado (tudo ✓ menos os automáticos)
    }
    if (bar) { bar.style.width = left == null ? (ph === "spin" || ph === "wait" ? "100%" : "0%") : `${Math.max(0, (left / total) * 100)}%`; bar.classList.toggle("low", left != null && left < 10000); }
    if (clock) { clock.textContent = left == null ? "" : X().clockFmt(Math.max(0, left)); clock.classList.toggle("low", left != null && left < 10000); }
    if (ph === "spin" || ph === "wait") {
      const t = P.$("[data-spin]", s.root);
      if (t && (!t.dataset.at || now - +t.dataset.at > (ph === "spin" && c.at - now < 600 ? 160 : 70))) { t.dataset.at = now; t.textContent = LETTERS[Math.floor(Math.random() * LETTERS.length)]; }
    }
    if (ph === "result" && c) {
      const nx = P.$("[data-next]", s.root);
      if (nx && r.k + 1 < r.n) nx.textContent = `Próxima rodada em ${Math.max(0, Math.ceil((c.doneAt + NEXT() - now) / 1000))} s`;
    }
    if (`${r.k}|${ph}` !== r.phase) adDraw(s, r, true);
  }

  function adDraw(s, r, full) {
    if (!live(s, r) || !P.$(".ad", s.root)) return;
    const c = cur(r), ph = phase(s, r), on = P.esc(X().oppName(s)), shown = Math.max(0, r.k);
    const prev = r.phase;
    r.phase = `${r.k}|${ph}`;
    X().ptsScore(s, r, `rodada ${Math.min(shown + 1, r.n)}/${r.n}`);
    P.$("[data-round]", s.root).innerHTML = c ? `Rodada ${r.k + 1} de ${r.n}` : `Adedonha · ${r.n} rodadas`;
    if (!full || (prev === r.phase && (ph === "fill" || ph === "vote"))) return; // não apaga o que está sendo digitado/votado
    const st = P.$("[data-stage]", s.root);
    if (ph === "wait" || ph === "spin") {
      st.innerHTML = `<div class="ad-spin"><div class="ad-letter spin" data-spin>?</div><p>Sorteando a letra…</p></div>`;
    } else if (ph === "fill" || ph === "locked") {
      const lock = ph === "locked";
      const stopTxt = !lock ? "" : c.stop === "tempo" ? "Tempo!" : c.stop === meId(s) ? "Você deu STOP!" : `${on} deu STOP!`;
      st.innerHTML = `<div class="ad-top"><div class="ad-letter land">${c.l}</div><p>Palavras com <b>${c.l}</b>. Preencha tudo e dê STOP!</p></div>
        ${lock ? `<div class="ad-banner">${stopTxt}<small>Conferindo as respostas…</small></div>` : ""}
        <div class="ad-fields">${c.c.map((cat, i) => {
          const v = c.inputs[i] || "", t = v.trim();
          return `<label class="ad-field${auto(t, c.l) ? " ok" : t ? " bad" : ""}"><span>${P.esc(cat)}</span><span class="ad-in"><i>${c.l}</i>
            <input data-f="${i}" placeholder="${c.l}…" value="${P.esc(v)}" maxlength="40" autocomplete="off" autocapitalize="sentences" spellcheck="false" ${lock ? "disabled" : ""} enterkeyhint="${i < c.c.length - 1 ? "next" : "done"}"></span></label>`;
        }).join("")}</div>
        <button class="ad-stop" data-stop ${lock || !allOk(c) || c.sent.stop ? "disabled" : ""}>STOP!</button>`;
    } else if (ph === "vote" || ph === "waitvote") {
      const o = X().opp(s), theirs = c.ans[o && o.id] || c.ans._ || [];
      const done = ph === "waitvote";
      st.innerHTML = `<div class="ad-top small"><div class="ad-letter land">${c.l}</div><p>${done ? `Esperando ${on} votar…` : `Confira as respostas de <b>${on}</b>. Toque para marcar ✗.`}</p></div>
        <div class="ad-votes">${c.c.map((cat, i) => {
          const a = theirs[i] || "", ok = auto(a, c.l), v = ok && c.vote[i];
          const why = !a.trim() ? "em branco" : !ok ? `não começa com ${c.l}` : "";
          return `<div class="ad-vrow${v ? "" : " no"}${ok ? "" : " auto"}"><span class="ad-vcat">${P.esc(cat)}</span><span class="ad-vans">${a.trim() ? P.esc(a) : "—"}${why ? `<small>${why}</small>` : ""}</span>
            <button class="ad-vbtn${v ? "" : " no"}" data-v="${i}" ${ok && !done ? "" : "disabled"} aria-label="${v ? "Válida" : "Inválida"}">${v ? "✓" : "✗"}</button></div>`;
        }).join("")}</div>
        <button class="pill wide" data-confirm ${done ? "disabled" : ""}>${done ? "Votos enviados" : "Confirmar"}</button>`;
    } else if (ph === "result") {
      const o = X().opp(s), mine = c.ans[meId(s)] || [], theirs = c.ans[o && o.id] || c.ans._ || [];
      const cell = (a, p) => `<td class="p${p}"><span>${a.trim() ? P.esc(a) : "—"}</span><b>${p}</b></td>`;
      st.innerHTML = `<div class="ad-top small"><div class="ad-letter land">${c.l}</div><p>Rodada ${r.k + 1}: <b>+${c.sc.tm}</b> × <b>+${c.sc.to}</b></p></div>
        <table class="ad-table"><thead><tr><th></th><th>Você</th><th>${on}</th></tr></thead><tbody>
        ${c.c.map((cat, i) => `<tr><th>${P.esc(cat)}</th>${cell(mine[i] || "", c.sc.me[i])}${cell(theirs[i] || "", c.sc.opp[i])}</tr>`).join("")}
        </tbody><tfoot><tr><th>Placar</th><td><b>${r.pts.me}</b></td><td><b>${r.pts.opp}</b></td></tr></tfoot></table>
        <p class="ad-next" data-next>${r.k + 1 < r.n ? "" : "Fim da partida!"}</p>`;
    }
    X().afterBtn(P.$("[data-after]", s.root), r.result && ph === "result" ? r : {}, () => adSheet(s, r));
  }
  function adSheet(s, r) {
    const rows = r.R.filter(Boolean).map((c, i) => `<div class="ana-row${c.sc && c.sc.tm > c.sc.to ? " me" : c.sc && c.sc.tm < c.sc.to ? " opp" : ""}"><b>Rodada ${i + 1} · ${c.l}</b><span>${c.sc ? `${c.sc.tm} × ${c.sc.to}` : "—"}</span></div>`).join("");
    X().ptsSheet(s, r, { icon: "✋", title: `Pontos: Você ${r.pts.me} × ${r.pts.opp} ${P.esc(X().oppName(s))}`, extra: `<div class="ana-rows">${rows}</div>` });
  }
  P.mp.kinds.s = {
    round: adRound, msg: adMsg,
    bye: (s, r) => {
      if (r.result) return;
      const c = cur(r);
      // Entre rodadas (ou antes da 1ª letra): encerra com o placar atual. No meio da rodada: segue até o STOP/tempo.
      if (!c || c.sc) {
        r.result = r.pts.me > r.pts.opp ? "me" : r.pts.me < r.pts.opp ? "opp" : "draw";
        X().ptsDone(s, r, () => adSheet(s, r), 800);
      } else adCheck(s, r);
      adDraw(s, r, true);
    },
    story: (s, r) => `Pontos ${r.pts.me} × ${r.pts.opp}`,
  };
})();
