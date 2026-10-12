// Trivia (site), no estilo Perguntados: a roleta sorteia a categoria, você responde em 20 s.
// 3 acertos seguidos = coroa: escolha uma categoria sem medalha e responda para ganhar a medalha.
// 6 medalhas = vitória; 3 erros = fim de jogo. Perguntas em shared/trivia.txt (CATEGORIA|pergunta|certa|e1|e2|e3).
(function () {
  "use strict";
  // Cores e ícones fixos (os mesmos do app).
  const CATS = [
    { name: "Ciência", emoji: "🧪", color: "#5FB873" },
    { name: "História", emoji: "📜", color: "#E6C14F" },
    { name: "Geografia", emoji: "🌎", color: "#4A8FE0" },
    { name: "Esportes", emoji: "⚽", color: "#F0964A" },
    { name: "Arte", emoji: "🎨", color: "#F07BB4" },
    { name: "Entretenimento", emoji: "🎬", color: "#B48CF0" },
  ];
  const SECS = 20, SPIN_MS = 1500, LIVES = 3, CROWN_AT = 3;
  const HELP = "Gire a roleta: ela para numa categoria e você tem 20 segundos para escolher a resposta certa entre 4. " +
    "Acertando 3 seguidas você ganha a 👑 coroa: escolha uma categoria cuja medalha ainda não tem e responda a pergunta da coroa para ganhar a medalha. " +
    "Junte as 6 medalhas para vencer. Errar ou deixar o tempo acabar custa uma das 3 vidas (e zera a sequência).";

  const parse = (t) => {
    const out = [];
    t.split("\n").forEach((raw) => {
      const l = raw.trim();
      if (!l || l.startsWith("#")) return;
      const f = l.split("|").map((s) => s.trim());
      if (f.length < 6 || f.slice(1, 6).some((s) => !s)) return;
      const cat = CATS.findIndex((c) => c.name === f[0]);
      if (cat < 0) return;
      out.push({ id: out.length, cat, q: f[1], right: f[2], wrong: f.slice(3, 6) });
    });
    return out;
  };
  P.triviaCats = CATS;

  P.games.trivia = async function (root) {
    root.innerHTML = P.topbar("Trivia", { help: true }) + `<div class="game tv">
      <div class="tv-hud">
        <div class="tv-medals" data-medals aria-label="Medalhas"></div>
        <div class="tv-hud-row"><span class="hearts" data-lives aria-label="Vidas"></span>
          <span class="tv-streak" data-streak aria-label="Sequência"></span></div>
      </div>
      <div class="tv-stage" data-stage></div></div>`;
    const stage = P.$("[data-stage]", root);
    const all = parse(await P.text("trivia.txt").catch(() => ""));
    if (!document.body.contains(stage)) return;

    let s; // estado da partida
    const fresh = () => ({ medals: new Set(), lives: LIVES, streak: 0, right: 0, used: new Set(), phase: "spin", cur: null, rot: 0, over: false, won: false });
    s = fresh();
    let raf = 0, t0 = 0, elapsed = 0, paused = false, locked = false, timers = [];
    const later = (fn, ms) => { const id = setTimeout(() => { if (document.body.contains(stage)) fn(); }, ms); timers.push(id); };
    const clearTimers = () => { timers.forEach(clearTimeout); timers = []; cancelAnimationFrame(raf); };
    // Para os testes (e para depuração): estado atual, sem expor nada na tela.
    P.triviaState = () => ({ phase: s.phase, medals: [...s.medals].map((i) => CATS[i].name), lives: s.lives, streak: s.streak, right: s.right,
      used: s.used.size, cat: s.cur ? CATS[s.cur.cat].name : null, crown: !!(s.cur && s.cur.crown), answer: s.cur ? s.cur.q.right : null, over: s.over, won: s.won });

    function hud() {
      P.$("[data-medals]", root).innerHTML = CATS.map((c, i) => `<span class="tv-medal${s.medals.has(i) ? " on" : ""}" style="--c:${c.color}" title="${c.name}" aria-label="${c.name}${s.medals.has(i) ? ": medalha ganha" : ""}">${c.emoji}</span>`).join("");
      P.$("[data-lives]", root).innerHTML = [0, 1, 2].map((i) => `<i class="${i < s.lives ? "" : "lost"}">❤️</i>`).join("");
      P.$("[data-streak]", root).innerHTML = `<span class="tv-pips">${[0, 1, 2].map((i) => `<i class="${i < s.streak ? "on" : ""}"></i>`).join("")}</span><b>${s.streak}/${CROWN_AT}</b> 👑`;
    }

    const unused = (cat) => all.filter((q) => q.cat === cat && !s.used.has(q.id));
    /** Pergunta ainda não usada na categoria (se acabarem, libera as da categoria de novo). */
    function takeQuestion(cat) {
      let list = unused(cat);
      if (!list.length) { all.forEach((q) => { if (q.cat === cat) s.used.delete(q.id); }); list = unused(cat); }
      const q = list[Math.floor(Math.random() * list.length)];
      s.used.add(q.id);
      return q;
    }

    // ---------- roleta ----------
    function wheelHTML() {
      const stops = CATS.map((c, i) => `${c.color} ${i * 60}deg ${(i + 1) * 60}deg`).join(",");
      return `<div class="tv-wheel-wrap"><div class="tv-pointer" aria-hidden="true"></div>
        <div class="tv-wheel" data-wheel style="background:conic-gradient(${stops});transform:rotate(${s.rot}deg)">
          ${CATS.map((c, i) => `<span class="tv-wslot" style="--a:${i * 60 + 30}deg"><span>${c.emoji}</span></span>`).join("")}
          <span class="tv-hub">?</span></div></div>
        <p class="center tv-wlabel" data-wlabel aria-live="polite">Girando…</p>`;
    }
    function spin() {
      if (s.over) return;
      s.phase = "spin"; s.cur = null; locked = true;
      // Só categorias com perguntas carregadas.
      const ok = CATS.map((_, i) => i).filter((i) => all.some((q) => q.cat === i));
      const fresh2 = ok.filter((i) => unused(i).length);
      const pool = fresh2.length ? fresh2 : ok;
      const cat = pool[Math.floor(Math.random() * pool.length)];
      stage.innerHTML = wheelHTML();
      hud();
      const wheel = P.$("[data-wheel]", stage);
      // O centro da fatia `cat` para embaixo do ponteiro (no alto): ângulo final ≡ −(cat·60 + 30).
      const target = (((-(cat * 60 + 30)) % 360) + 360) % 360;
      const base = s.rot - (((s.rot % 360) + 360) % 360);
      s.rot = base + 360 * 4 + target + (Math.random() * 30 - 15);
      requestAnimationFrame(() => requestAnimationFrame(() => {
        wheel.style.transition = `transform ${SPIN_MS}ms cubic-bezier(.17,.67,.21,1)`;
        wheel.style.transform = `rotate(${s.rot}deg)`;
      }));
      P.tone([[440, 40], [494, 40], [523, 40], [587, 40]], 0.06);
      later(() => {
        const c = CATS[cat], lab = P.$("[data-wlabel]", stage);
        lab.innerHTML = `<span class="tv-cat" style="--c:${c.color}">${c.emoji} ${c.name}</span>`;
        P.fx.reveal(2, "c");
        later(() => ask(cat, false), 650);
      }, SPIN_MS + 80);
    }

    // ---------- pergunta ----------
    function ask(cat, crown) {
      const q = takeQuestion(cat), c = CATS[cat];
      s.phase = "question"; s.cur = { cat, crown, q }; locked = false;
      const opts = P.shuffle([q.right, ...q.wrong]);
      stage.innerHTML = `<div class="tv-q" style="--c:${c.color}">
          <div class="tv-qhead"><span class="tv-cat" style="--c:${c.color}">${c.emoji} ${c.name}</span>${crown ? `<span class="tv-crown-tag">👑 Pergunta da coroa</span>` : ""}
            <span class="tv-secs" data-secs>${SECS}</span></div>
          <div class="timer"><i data-bar style="width:100%"></i></div>
          <p class="tv-text">${P.esc(q.q)}</p></div>
        <div class="tv-opts" data-opts>${opts.map((o, i) => `<button class="opt tv-opt" data-i="${i}">${P.esc(o)}</button>`).join("")}</div>
        <p class="center tv-msg" data-msg aria-live="polite"></p>`;
      stage.querySelectorAll("[data-i]").forEach((b) => (b.onclick = () => choose(b, opts[+b.dataset.i])));
      hud();
      elapsed = 0; t0 = performance.now(); cancelAnimationFrame(raf);
      if (!paused) raf = requestAnimationFrame(tick);
    }
    function tick() {
      if (!document.body.contains(stage) || locked || s.over || paused || s.phase !== "question") return;
      const ms = elapsed + performance.now() - t0, left = 1 - ms / (SECS * 1000);
      const bar = P.$("[data-bar]", stage), secs = P.$("[data-secs]", stage);
      if (bar) { bar.style.width = Math.max(0, left * 100) + "%"; bar.style.background = left < 0.25 ? "var(--red)" : "var(--c, var(--accent))"; }
      if (secs) secs.textContent = Math.max(0, Math.ceil(SECS - ms / 1000));
      if (left <= 0) return choose(null, null);
      raf = requestAnimationFrame(tick);
    }
    function pause() { if (paused) return; paused = true; elapsed += performance.now() - t0; cancelAnimationFrame(raf); }
    function resume() { if (!paused) return; paused = false; t0 = performance.now(); if (!locked && !s.over && s.phase === "question") raf = requestAnimationFrame(tick); }

    // btn/val nulos = o tempo acabou.
    function choose(btn, val) {
      if (locked || s.over || s.phase !== "question") return;
      locked = true; cancelAnimationFrame(raf);
      const { q, crown, cat } = s.cur, right = val === q.right, c = CATS[cat];
      stage.querySelectorAll("[data-i]").forEach((b) => {
        if (b.textContent === q.right) b.classList.add("right");
        else if (b === btn) b.classList.add("wrong");
        else b.classList.add("dim");
      });
      const msg = P.$("[data-msg]", stage);
      let delay = 1300;
      if (right) {
        s.right++; P.store.add("trivia-right");
        if (crown) {
          s.medals.add(cat); s.streak = 0;
          msg.innerHTML = `Medalha de <b>${c.name}</b> conquistada! ${c.emoji}`; msg.className = "center tv-msg good";
          P.fx.win(); delay = 1700;
        } else {
          s.streak = Math.min(CROWN_AT, s.streak + 1);
          msg.textContent = s.streak >= CROWN_AT ? "Certa! 👑 Você ganhou a coroa!" : "Certa!"; msg.className = "center tv-msg good";
          P.fx.reveal(2, "c");
        }
      } else {
        s.lives--; s.streak = 0;
        msg.innerHTML = (val === null ? "O tempo acabou! " : "Errou! ") + `A resposta era <b>${P.esc(q.right)}</b>.`; msg.className = "center tv-msg bad";
        P.fx.invalid(); delay = 1800;
      }
      hud();
      later(() => {
        if (s.medals.size === CATS.length) return end(true);
        if (s.lives <= 0) return end(false);
        if (!crown && right && s.streak >= CROWN_AT) return crownPick();
        spin();
      }, delay);
    }

    // ---------- coroa ----------
    function crownPick() {
      s.phase = "crown"; s.cur = null;
      const left = CATS.map((c, i) => [c, i]).filter(([, i]) => !s.medals.has(i) && all.some((q) => q.cat === i));
      if (!left.length) { s.streak = 0; return spin(); }
      stage.innerHTML = `<div class="tv-crown"><div class="tv-crown-ico" aria-hidden="true">👑</div>
          <h2>Coroa!</h2><p class="muted">Escolha uma categoria e acerte a pergunta para ganhar a medalha.</p>
          <div class="tv-crown-list">${left.map(([c, i]) => `<button class="tv-crown-btn" data-cat="${i}" style="--c:${c.color}"><span>${c.emoji}</span>${c.name}</button>`).join("")}</div></div>`;
      P.fx.win();
      stage.querySelectorAll("[data-cat]").forEach((b) => (b.onclick = () => { s.streak = 0; P.fx.type(); ask(+b.dataset.cat, true); }));
      hud();
    }

    // ---------- fim ----------
    function end(won) {
      s.over = true; s.won = won; s.phase = "over"; clearTimers();
      P.store.max("trivia-best", s.right); P.logActivity();
      if (won) { P.store.add("trivia-won"); P.confetti(); P.fx.win(); } else P.fx.lose();
      const medals = CATS.map((c, i) => (s.medals.has(i) ? c.emoji : "⚪")).join("");
      stage.innerHTML = `<div class="tv-crown"><div class="tv-crown-ico" aria-hidden="true">${won ? "🏆" : "💔"}</div>
        <h2>${won ? "Você venceu!" : "Fim de jogo"}</h2><p class="muted">${s.medals.size} de 6 medalhas · ${s.right} ${s.right === 1 ? "acerto" : "acertos"}</p>
        <button class="pill" data-again2>Jogar de novo</button></div>`;
      P.$("[data-again2]", stage).onclick = again;
      const ui = P.sheet(`<h2>${won ? "Você venceu! 🏆" : "Fim de jogo"}</h2>
        <p class="subtitle">${won ? "As 6 medalhas são suas" : `Você juntou ${s.medals.size} de 6 medalhas`}</p>
        <p class="center tv-sheet-medals">${CATS.map((c, i) => `<span class="tv-medal${s.medals.has(i) ? " on" : ""}" style="--c:${c.color}">${c.emoji}</span>`).join("")}</p>
        ${P.statsHTML([[s.right, "Acertos"], [P.store.get("trivia-best", 0), "Recorde"], [P.store.get("trivia-won", 0), "Vitórias"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>Jogar de novo</button></div>`);
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Trivia\n${won ? "Ganhei as 6 medalhas! 🏆" : `${s.medals.size} de 6 medalhas`}\n${medals}\n${s.right} ${s.right === 1 ? "acerto" : "acertos"} 🧠`);
      P.$("[data-again]", ui.el).onclick = () => { ui.close(); again(); };
    }
    function again() {
      document.querySelectorAll(".overlay").forEach((o) => o.remove());
      clearTimers(); const rot = s.rot; s = fresh(); s.rot = rot; spin();
    }

    // A ajuda pausa o tempo da pergunta.
    P.$("[data-help]", root).onclick = () => { pause(); P.sheet(`<h2>Como jogar Trivia</h2><p>${HELP}</p>`, resume); };
    if (!all.length) { stage.innerHTML = `<p class="center muted">Não foi possível carregar as perguntas.</p>`; return; }
    hud();
    spin();
  };
})();
