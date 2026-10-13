// Partida com amigo (site): Forca (f). Série (1 partida, melhor de 3, melhor de 5).
// Rodada da sala par: o anfitrião escolhe e o convidado adivinha; ímpar: ao contrário.
// Mensagens (iguais no app), RN = rodada da sala:
//   {"t":"secret","id","n":RN,"th":"Comida","m":"... .. ......"}  máscara: letra → ".", espaço e hífen ficam
//   {"t":"guess","id","n":RN,"l":"A"} → quem escolheu responde {"t":"reveal","id","n":RN,"l":"A","p":[0,3]} (p vazio = erro)
//   {"t":"try","id","n":RN,"w":"PAO DE QUEIJO"} → {"t":"tryres","n":RN,"ok":true|false} (chute errado = 1 erro)
//   {"t":"word","n":RN,"w":"Pão de queijo"}  a palavra original, no fim
(function () {
  "use strict";
  const X = () => P.mp._;
  const live = (s, r) => X().S === s && s.round === r;
  const meId = (s) => s.me.id;
  const MAX_ERR = 6, WAIT_MS = 90000;
  const ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");
  /** Letra base (sem acento): Á, Ã, Â, À → A; Ç → C. */
  const base = (ch) => P.norm(ch || "");
  /** Comparação do chute: sem acento, só letras (hífen e espaços não contam). */
  const cmp = (w) => P.norm(String(w || "")).replace(/[^A-Z]/g, "");
  const clean = (w) => String(w || "").normalize("NFC").trim().replace(/\s+/g, " ").replace(/\s*-\s*/g, "-");
  const LET = "A-Za-zÀ-ÖØ-öø-ÿ";
  const WORD_RE = new RegExp(`^[${LET}]+(?:[ -][${LET}]+)*$`);
  /** Palavra válida: 2 a 20 caracteres, só letras (com acento), espaços e hífen. */
  const validWord = (w) => WORD_RE.test(w) && w.length <= 20 && w.replace(/[^A-Za-zÀ-ÿ]/g, "").length >= 2;
  const maskOf = (w) => [...w].map((ch) => (ch === " " || ch === "-" ? ch : ".")).join("");
  const positions = (w, l) => [...w].map((ch, i) => (base(ch) === l ? i : -1)).filter((i) => i >= 0);
  P.mp.forca = { maskOf, positions, cmp, validWord, clean };

  function fcRound(s, startAt) {
    const { h, g } = X().ids(s);
    const hostChooses = s.rn % 2 === 0;
    const r = (s.round = { kind: "f", n: s.rn, startAt, host: h, guest: g, chooser: hostChooses ? h : g, guesser: hostChooses ? g : h,
      secret: null, word: null, finalWord: null, guessed: {}, order: [], tries: [], tryPend: false, pend: {}, answered: {}, errors: 0,
      result: null, why: "", winner: null, view: "" });
    s.view = "game";
    X().gameShell(s, "fc", `<div class="fc-stage" data-stage></div><div class="center" data-after></div>`);
    const st = P.$("[data-stage]", s.root);
    st.addEventListener("click", (e) => {
      const k = e.target.closest("[data-l]"); if (k) return fcGuess(s, r, k.dataset.l);
      if (e.target.closest("[data-ready]")) return fcReady(s, r);
      if (e.target.closest("[data-try-open]")) { const b = P.$("[data-tryrow]", st); b.classList.toggle("hidden"); if (!b.classList.contains("hidden")) P.$("[data-tryw]", st).focus(); return; }
      if (e.target.closest("[data-try]")) return fcTry(s, r);
      if (e.target.closest("[data-quit]")) { X().S && P.mp.leave(); P.go(""); }
    });
    st.addEventListener("keydown", (e) => {
      if (e.key !== "Enter") return;
      if (e.target.matches("[data-tryw]")) { e.preventDefault(); fcTry(s, r); }
      else if (e.target.matches("[data-fword], [data-ftheme]")) { e.preventDefault(); if (e.target.matches("[data-fword]")) P.$("[data-ftheme]", st).focus(); else fcReady(s, r); }
    });
    // Teclado físico de quem adivinha.
    const onKey = (e) => {
      if (!live(s, r)) return document.removeEventListener("keydown", onKey);
      if (e.ctrlKey || e.metaKey || e.altKey || document.querySelector(".overlay") || /^(INPUT|TEXTAREA)$/.test((e.target && e.target.tagName) || "")) return;
      if (/^[A-Za-zÇç]$/.test(e.key)) { e.preventDefault(); fcGuess(s, r, base(e.key)); }
    };
    document.addEventListener("keydown", onKey);
    r.api = { redraw: () => fcDraw(s, r, true) };
    X().drawScore(s);
    fcDraw(s, r, true);
    X().countdown(s, r);
    s.gameTimer = setInterval(() => fcTick(s, r), 500);
  }
  const iChoose = (s, r) => r.chooser === meId(s);
  const started = (r) => Date.now() >= r.startAt;

  function fcReady(s, r) {
    if (!live(s, r) || !iChoose(s, r) || r.secret || r.sentSecret || !started(r)) return;
    const wEl = P.$("[data-fword]", s.root), tEl = P.$("[data-ftheme]", s.root);
    const w = clean(wEl.value), th = tEl.value.trim().replace(/\s+/g, " ");
    if (!validWord(w)) return X().shakeEl(wEl.closest(".fc-field"), "A palavra precisa ter de 2 a 20 letras (pode ter acento, espaço e hífen)");
    if (!th || th.length > 24) return X().shakeEl(tEl.closest(".fc-field"), "Escreva o tema (até 24 letras)");
    r.word = w; r.theme = th; r.sentSecret = true;
    P.fx.type();
    X().publish({ t: "secret", id: meId(s), n: r.n, th, m: maskOf(w) });
    fcDraw(s, r, true);
  }
  function fcGuess(s, r, l) {
    if (!live(s, r) || iChoose(s, r) || !r.secret || r.result || r.guessed[l] || r.pend[l] || !ABC.includes(l)) return;
    r.pend[l] = true;
    P.fx.type();
    X().publish({ t: "guess", id: meId(s), n: r.n, l });
    fcDraw(s, r, true);
  }
  function fcTry(s, r) {
    if (!live(s, r) || iChoose(s, r) || !r.secret || r.result || r.tryPend) return;
    const el = P.$("[data-tryw]", s.root), w = clean(el.value);
    if (cmp(w).length < 1) return X().shakeEl(el, "Escreva a palavra");
    r.tryPend = true;
    X().publish({ t: "try", id: meId(s), n: r.n, w: w.toUpperCase() });
    fcDraw(s, r, true);
  }
  /** Posições que ainda faltam (letras da máscara que ninguém revelou). */
  const missing = (r) => {
    const got = new Set(Object.values(r.guessed).flat());
    return [...r.secret.m].map((c, i) => (c === "." && !got.has(i) ? i : -1)).filter((i) => i >= 0);
  };
  function fcMsg(s, m) {
    const r = s.round;
    if (!r || r.kind !== "f" || Number(m.n) !== r.n) return;
    if (m.t === "word") { r.finalWord = String(m.w || "").slice(0, 40); return fcDraw(s, r, true); }
    if (r.result) return;
    if (m.t === "secret") {
      if (m.id !== r.chooser || r.secret || typeof m.m !== "string" || !/^[. -]{1,40}$/.test(m.m) || !m.m.includes(".")) return;
      r.secret = { th: String(m.th || "").slice(0, 24), m: m.m };
      if (!iChoose(s, r)) { P.tone([[523, 60], [659, 80]], 0.07); P.vibrate(30); }
      return fcDraw(s, r, true);
    }
    if (!r.secret) return;
    if (m.t === "guess") {
      // Quem escolheu responde sozinho com as posições da letra.
      const l = String(m.l || "");
      if (!iChoose(s, r) || m.id !== r.guesser || !ABC.includes(l) || r.answered[l] || !r.word) return;
      r.answered[l] = true;
      X().publish({ t: "reveal", id: meId(s), n: r.n, l, p: positions(r.word, l) });
      return;
    }
    if (m.t === "reveal") {
      const l = String(m.l || "");
      if (m.id !== r.chooser || !ABC.includes(l) || r.guessed[l]) return;
      const p = (Array.isArray(m.p) ? m.p : []).map(Number).filter((i) => Number.isInteger(i) && r.secret.m[i] === ".");
      r.guessed[l] = p; r.order.push(l); delete r.pend[l];
      if (!p.length) { r.errors++; if (!iChoose(s, r)) { P.tone([[330, 140]], 0.08); P.vibrate(50); } }
      else if (!iChoose(s, r)) P.fx.reveal ? P.fx.reveal(p.length, "c") : P.fx.type();
      fcEnd(s, r);
      return fcDraw(s, r, true);
    }
    if (m.t === "try") {
      if (m.id !== r.guesser) return;
      const w = String(m.w || "").slice(0, 40);
      r.tries.push({ w, ok: null });
      if (iChoose(s, r) && r.word) X().publish({ t: "tryres", n: r.n, ok: cmp(w) === cmp(r.word) });
      return fcDraw(s, r, true);
    }
    if (m.t === "tryres") {
      const t = r.tries.find((x) => x.ok == null);
      if (t) t.ok = m.ok === true;
      r.tryPend = false;
      if (m.ok === true) { r.why = "chute"; r.winner = r.guesser; }
      else { r.errors++; if (!iChoose(s, r)) P.toast("Não é essa!"); }
      fcEnd(s, r);
      return fcDraw(s, r, true);
    }
  }
  function fcEnd(s, r) {
    if (r.result) return;
    if (!r.winner && !missing(r).length) { r.winner = r.guesser; r.why = "letras"; }
    if (!r.winner && r.errors >= MAX_ERR) { r.winner = r.chooser; r.why = "forca"; }
    if (!r.winner) return;
    if (iChoose(s, r) && r.word) { r.finalWord = r.word; X().publish({ t: "word", n: r.n, w: r.word }); }
    X().settle(s, r, r.winner === meId(s) ? "me" : "opp", () => fcSheet(s, r), 2200);
  }
  function fcTick(s, r) {
    if (!live(s, r)) return X().stopGame(s);
    // Quem adivinha esperando há 90 s: mostra o aviso com a opção de sair.
    if (!r.secret && !r.result && !iChoose(s, r) && Date.now() - r.startAt > WAIT_MS && r.view !== "late") fcDraw(s, r, true);
    if (started(r) && r.view === "pre") fcDraw(s, r, true);
  }

  // ---------- desenho ----------
  /** Boneco da forca (arredondado): cada erro mostra uma parte. */
  function gallows(err, lost) {
    const parts = [
      `<circle cx="80" cy="40" r="11"/>`, `<path d="M80 51 V80"/>`, `<path d="M80 59 L67 71"/>`,
      `<path d="M80 59 L93 71"/>`, `<path d="M80 80 L69 98"/>`, `<path d="M80 80 L91 98"/>`,
    ];
    return `<svg class="fc-gallows${lost ? " lost" : ""}" viewBox="0 0 120 120" aria-label="${err} de ${MAX_ERR} erros">
      <path class="fc-post" d="M18 110 H70 M34 110 V12 H80 V27 M34 32 L54 12"/>
      <g class="fc-man">${parts.slice(0, err).map((p, i) => p.replace(/^<(\w+)/, `<$1 class="${i === err - 1 ? "new" : ""}"`)).join("")}</g></svg>`;
  }
  /** Máscara: cada palavra num grupo; letras reveladas (ou a palavra original no fim). */
  function maskHTML(r, mine) {
    const m = r.secret.m, shown = {}, word = mine ? r.word : r.finalWord;
    Object.entries(r.guessed).forEach(([l, ps]) => ps.forEach((i) => (shown[i] = l)));
    const full = word && (mine || r.result) && word.length === m.length ? [...word] : null;
    let html = "", group = "";
    [...m].forEach((c, i) => {
      if (c === " ") { html += `<span class="fc-wd">${group}</span>`; group = ""; return; }
      if (c === "-") { group += `<span class="fc-hy">-</span>`; return; }
      const got = shown[i] != null, ch = full ? full[i].toUpperCase() : got ? shown[i] : "";
      group += `<span class="fc-sl${got ? " got" : ""}${full && !got ? (mine && !r.result ? " hid" : " miss") : ""}">${P.esc(ch)}</span>`;
    });
    html += `<span class="fc-wd">${group}</span>`;
    return `<div class="fc-mask" style="--n:${Math.max(...m.split(" ").map((x) => x.length), 6)}">${html}</div>`;
  }
  function fcDraw(s, r, full) {
    if (!live(s, r) || !P.$(".fc", s.root)) return;
    X().drawScore(s);
    const st = P.$("[data-stage]", s.root), on = P.esc(X().oppName(s)), me = iChoose(s, r);
    const title = P.$("[data-title]", s.root);
    if (title) title.innerHTML = `Você × ${on}`;
    if (!started(r)) { r.view = "pre"; st.innerHTML = `<p class="fc-role">${me ? "Você escolhe a palavra" : `${on} escolhe a palavra`}</p>`; return; }
    if (!r.secret) {
      if (me) {
        if (r.view === "form" && !r.sentSecret) return;
        r.view = "form";
        st.innerHTML = `<div class="fc-form">
            <div class="fc-form-ico">${P.mp.icon("f")}</div>
            <h2>Escolha a palavra</h2>
            <p class="muted">${on} vai tentar adivinhar letra por letra.</p>
            <label class="mp-field fc-field"><span>Palavra</span><input class="mp-input" data-fword maxlength="20" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="Ex.: guarda-chuva" value="${P.esc(r.word || "")}" ${r.sentSecret ? "disabled" : ""}></label>
            <label class="mp-field fc-field"><span>Tema</span><input class="mp-input" data-ftheme maxlength="24" autocomplete="off" placeholder="Ex.: Comida" value="${P.esc(r.theme || "")}" ${r.sentSecret ? "disabled" : ""}></label>
            <p class="fc-help">De 2 a 20 letras. Pode ter acento, espaço e hífen: “pão de queijo”.</p>
            <button class="pill wide" data-ready ${r.sentSecret ? "disabled" : ""}>${r.sentSecret ? "Enviando…" : "Pronto"}</button></div>`;
        if (!r.sentSecret) setTimeout(() => P.$("[data-fword]", st)?.focus(), 50);
        return;
      }
      const late = Date.now() - r.startAt > WAIT_MS;
      r.view = late ? "late" : "wait";
      st.innerHTML = `<div class="fc-wait"><div class="mark small bounce"><span></span><span></span><span></span><span></span><span></span></div>
          <p>${late ? `Esperando <b>${on}</b> escolher…` : `<b>${on}</b> está escolhendo a palavra…`}</p>
          ${late ? `<button class="pill ghost" data-quit>Sair da partida</button>` : ""}</div>`;
      return;
    }
    r.view = "game";
    const lost = r.result && r.winner === r.chooser;
    const misses = r.order.filter((l) => !r.guessed[l].length);
    let status;
    if (r.result) {
      status = r.winner === r.guesser
        ? (me ? `${on} adivinhou!` : "Você adivinhou! 🎉")
        : (me ? "A forca venceu! Você ganhou a rodada" : "Enforcado! 😵");
    } else if (me) status = r.tryPend || r.tries.some((t) => t.ok == null) ? `${on} chutou a palavra…` : `${on} está tentando…`;
    else status = r.tryPend ? "Conferindo o chute…" : "Escolha uma letra";
    const keys = me ? "" : `<div class="fc-keys${r.result ? " off" : ""}">${ABC.map((l) => {
      const g = r.guessed[l], cls = g ? (g.length ? " hit" : " miss") : r.pend[l] ? " pend" : "";
      return `<button class="fc-key${cls}" data-l="${l}" ${g || r.pend[l] || r.result ? "disabled" : ""}>${l}</button>`;
    }).join("")}</div>
      <div class="fc-tryline"><button class="pill ghost fc-tryopen" data-try-open ${r.result || r.tryPend ? "disabled" : ""}>Chutar a palavra</button></div>
      <div class="fc-tryrow hidden" data-tryrow><input class="mp-input" data-tryw maxlength="40" autocomplete="off" autocapitalize="characters" spellcheck="false" placeholder="A palavra inteira"><button class="pill" data-try>Chutar</button></div>`;
    const tries = r.tries.length ? `<p class="fc-tries">Chutes: ${r.tries.map((t) => `<span class="${t.ok ? "ok" : t.ok === false ? "no" : ""}">${P.esc(t.w)}</span>`).join(" ")}</p>` : "";
    const keep = !me && P.$("[data-tryrow]", st) && !P.$("[data-tryrow]", st).classList.contains("hidden") && !r.result && !r.tryPend ? P.$("[data-tryw]", st).value : null;
    st.innerHTML = `<div class="fc-head"><span class="fc-theme">Tema: <b>${P.esc(r.secret.th)}</b></span><span class="fc-err${r.errors >= 4 ? " hi" : ""}">Erros <b>${r.errors}</b>/${MAX_ERR}</span></div>
      <div class="fc-board">${gallows(Math.min(r.errors, MAX_ERR), lost)}
        <div class="fc-side">${me ? `<p class="fc-role mine">Sua palavra</p>` : `<p class="fc-role">Palavra de ${on}</p>`}
          <p class="fc-misses">${misses.length ? misses.map((l) => `<span>${l}</span>`).join("") : `<em>Nenhuma letra errada</em>`}</p></div></div>
      ${maskHTML(r, me)}
      <p class="fc-status${r.result ? (r.winner === meId(s) ? " win" : " lose") : ""}">${status}</p>
      ${r.result && !me ? `<p class="fc-final">${r.finalWord ? `A palavra era <b>${P.esc(r.finalWord.toUpperCase())}</b>` : "…"}</p>` : ""}
      ${tries}${keys}`;
    if (keep != null) { P.$("[data-tryrow]", st).classList.remove("hidden"); const i = P.$("[data-tryw]", st); i.value = keep; }
    X().afterBtn(P.$("[data-after]", s.root), r, () => fcSheet(s, r));
  }
  function fcDetail(s, r) {
    const on = P.esc(X().oppName(s)), me = iChoose(s, r);
    if (r.why === "saiu") return `${on} saiu da partida.`;
    if (r.winner === r.guesser) return `${me ? `${on} adivinhou` : "Você adivinhou"}${r.why === "chute" ? " no chute" : ""} com ${X().plural(r.errors, "erro", "erros")}.`;
    return `${me ? `${on} não adivinhou` : "Você não adivinhou"}: ${MAX_ERR} erros.`;
  }
  function fcSheet(s, r) {
    const w = r.finalWord || r.word;
    X().seriesSheet(s, r, { icon: "🪢", detail: fcDetail(s, r),
      extra: `<div class="fc-sheetword"><small>${r.secret ? `Tema: ${P.esc(r.secret.th)}` : "A palavra"}</small><b data-fsword>${w ? P.esc(w.toUpperCase()) : "…"}</b>${X().seriesOver(s) ? "" : `<span class="note">Na próxima rodada os papéis trocam.</span>`}</div>`,
      share: w ? `Palavra: ${w.toUpperCase()} 🪢` : "" });
  }
  P.mp.kinds.f = {
    round: fcRound, msg: (s, m, lv) => { fcMsg(s, m, lv); const el = document.querySelector("[data-fsword]"), r = s.round; if (el && r && r.finalWord) el.textContent = r.finalWord.toUpperCase(); },
    bye: (s, r) => { if (!r.result) { r.why = "saiu"; r.winner = meId(s); X().settle(s, r, "me", () => fcSheet(s, r), 1200); } fcDraw(s, r, true); },
    story: (s, r) => { const w = r.finalWord || r.word; return w ? `Palavra: ${w.toUpperCase()}` : "Forca"; },
  };
})();
