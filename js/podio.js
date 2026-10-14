// Pódio (site), inspirado no Factle: monte o top 5 de um ranking com 10 opções em até 5 tentativas.
// Dados em shared/podio.txt (TÍTULO|1º..5º|5 erradas). Do dia: idx = (dia × 7919 + 104729) mod N; opções embaralhadas com o MINSTD do dia.
(function () {
  "use strict";
  const TRIES = 5;
  const HELP = "Coloque as 5 respostas certas na ordem do ranking, do 1º ao 5º. Toque numa opção para pôr na próxima vaga livre; toque numa vaga para devolver a opção. " +
    "Com as 5 vagas cheias, toque em Enviar. Verde: posição certa. Amarelo: está no top 5, mas em outra posição. Cinza: não está no top 5 (fica riscada nas opções). " +
    "Você tem 5 tentativas.";
  const MARK = { c: "🟩", p: "🟨", a: "⬛" };

  const parse = (t) => t.split("\n").map((l) => l.trim()).filter((l) => l && !l.startsWith("#"))
    .map((l) => l.split("|").map((s) => s.trim())).filter((f) => f.length >= 11 && f.slice(0, 11).every(Boolean))
    .map((f) => ({ title: f[0], top: f.slice(1, 6), wrong: f.slice(6, 11) }));
  const dailyIdx = (day, n) => (((day * 7919 + 104729) % n) + n) % n;
  /** Ordem do dia: Fisher–Yates (i de 9 a 1, j = próximo MINSTD mod (i+1)) sobre 1º..5º seguidos das 5 erradas. */
  const dailyOrder = (q, day) => { const next = P.minstd(day), a = q.top.concat(q.wrong); for (let i = a.length - 1; i > 0; i--) { const j = next() % (i + 1); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  const judge = (q, row) => row.map((w, i) => (w === q.top[i] ? "c" : q.top.includes(w) ? "p" : "a"));
  P.podioDaily = (list, day) => { const i = dailyIdx(day, list.length); return { idx: i, order: dailyOrder(list[i], day) }; };
  P.podioParse = parse;

  P.games.podio = async function (root, inf = !!P.store.get("podio-inf", false)) {
    P.store.set("podio-inf", inf);
    root.innerHTML = P.topbar("Pódio", { help: true }) + P.modeSwitch(inf) + `<div class="game pd" data-stage></div>`;
    const stage = P.$("[data-stage]", root);
    const list = parse(await P.text("podio.txt").catch(() => ""));
    if (!document.body.contains(stage)) return;
    P.bindModeSwitch(root, (v) => P.games.podio(root, v));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar o Pódio</h2><p>${HELP}</p>`);
    if (!list.length) { stage.innerHTML = `<p class="center muted">Não foi possível carregar os rankings.</p>`; return; }

    const today = P.dayKey(), di = P.dayIndex();
    let idx, opts, tries = [];
    if (inf) {
      // Sem repetir até passar por todos.
      let seen = (P.store.get("podio-seen", []) || []).filter((i) => i < list.length);
      if (seen.length >= list.length) seen = [];
      const free = list.map((_, i) => i).filter((i) => !seen.includes(i));
      idx = free[Math.floor(Math.random() * free.length)];
      P.store.set("podio-seen", seen.concat(idx));
      opts = P.shuffle(list[idx].top.concat(list[idx].wrong));
    } else {
      ({ idx, order: opts } = P.podioDaily(list, di));
      const saved = P.store.get("podio-state", null);
      if (saved && saved.day === today && Array.isArray(saved.tries)) tries = saved.tries;
    }
    const q = list[idx];
    let slots = [null, null, null, null, null];
    const won = () => tries.length > 0 && judge(q, tries[tries.length - 1]).every((m) => m === "c");
    const over = () => won() || tries.length >= TRIES;
    P.podioState = () => ({ inf, idx, title: q.title, top: q.top.slice(), opts: opts.slice(), slots: slots.slice(), tries: tries.length, won: won(), over: over() });

    function draw() {
      const out = new Set(tries.flatMap((r) => r.filter((w) => !q.top.includes(w))));
      const fin = over();
      const hist = tries.map((r) => `<div class="pd-try">${judge(q, r).map((m, i) => `<span class="pd-chip ${m}">${P.esc(r[i])}</span>`).join("")}</div>`).join("");
      const shown = fin ? q.top : slots;
      stage.innerHTML = `<div class="big-q pd-title">${P.esc(q.title)}</div>
        <p class="hint">${fin ? (won() ? `Acertou em ${tries.length} ${tries.length === 1 ? "tentativa" : "tentativas"}! 🏆` : "Era assim:") : `Tentativa ${tries.length + 1} de ${TRIES}`}</p>
        <div class="pd-slots">${shown.map((w, i) => `<button class="pd-slot${w ? " full" : ""}${fin ? " c" : ""}" data-slot="${i}" ${fin ? "disabled" : ""}><b>${i + 1}º</b><span>${w ? P.esc(w) : ""}</span></button>`).join("")}</div>
        ${fin ? `<div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>${inf ? "Outro Pódio" : "Jogar o Infinito"}</button></div>`
          : `<div class="pd-opts">${opts.map((w) => `<button class="pd-opt${slots.includes(w) ? " used" : ""}${out.has(w) ? " out" : ""}" data-opt="${P.esc(w)}" ${slots.includes(w) ? "disabled" : ""}>${P.esc(w)}</button>`).join("")}</div>
          <button class="pill wide" data-send ${slots.every(Boolean) ? "" : "disabled"}>Enviar</button>`}
        ${hist ? `<div class="pd-hist"><small class="muted">Tentativas</small>${hist}</div>` : ""}`;
      stage.querySelectorAll("[data-slot]").forEach((b) => (b.onclick = () => { if (slots[+b.dataset.slot]) { slots[+b.dataset.slot] = null; P.fx.type(); draw(); } }));
      stage.querySelectorAll("[data-opt]").forEach((b) => (b.onclick = () => { const i = slots.indexOf(null); if (i >= 0) { slots[i] = opts.find((w) => w === b.dataset.opt); P.fx.type(); draw(); } }));
      const send = P.$("[data-send]", stage); if (send) send.onclick = submit;
      const sh = P.$("[data-share]", stage); if (sh) sh.onclick = () => P.share(`Pódio ${won() ? tries.length : "X"}/${TRIES} 🏆\n${tries.map((r) => judge(q, r).map((m) => MARK[m]).join("")).join("\n")}`);
      const ag = P.$("[data-again]", stage); if (ag) ag.onclick = () => P.games.podio(root, true);
    }

    function submit() {
      if (!slots.every(Boolean) || over()) return;
      tries.push(slots.slice());
      const m = judge(q, slots);
      // Mantém nas vagas só as que ficaram verdes.
      slots = slots.map((w, i) => (m[i] === "c" ? w : null));
      if (!inf) P.store.set("podio-state", { day: today, tries });
      if (over()) finish(); else { P.tone([[420, 50]], 0.05); P.vibrate(8); }
      draw();
    }

    function finish() {
      const w = won();
      P.store.add("podio-played"); P.logActivity();
      if (w) { P.store.add("podio-won"); if (tries.length === 1) P.store.add("podio-first"); P.confetti(); P.fx.win(); } else P.fx.invalid();
      if (!inf) {
        P.store.set("daily-done-podio", today);
        P.store.set("podio-streak", w ? (P.store.get("podio-last", -9) === di - 1 ? P.store.get("podio-streak", 0) + 1 : 1) : 0);
        if (w) P.store.set("podio-last", di);
      }
    }
    draw();
  };
})();
