// Reverso, Qual é a Palavra? e Sinônimos (site).
(function () {
  "use strict";
  const ORD = ["c", "p", "a"];

  // ---------- Reverso: o site tenta adivinhar a sua palavra ----------
  P.games.reverso = function (root) {
    let common, rare, history, guess, marks, over, appWon, contradiction, recorded;
    const answersSet = new Set(P.words.answers);
    const pick = () => {
      const pool = common.length ? common : rare;
      if (!pool.length) return "";
      if (pool.length <= 2) return pool[0];
      const f = {}; pool.forEach((w) => new Set(w).forEach((c) => (f[c] = (f[c] || 0) + 1)));
      let best = pool[0], bs = -1;
      pool.forEach((w) => { const s = [...new Set(w)].reduce((a, c) => a + f[c], 0); if (s > bs) { bs = s; best = w; } });
      return best;
    };
    const rebuild = () => {
      common = P.words.answers.slice(); rare = [...P.words.all].filter((w) => !answersSet.has(w));
      history.forEach((h) => {
        const ok = (w) => w !== h.word && P.evaluate(h.word, w).join("") === h.marks.join("");
        common = common.filter(ok); rare = rare.filter(ok);
      });
      over = false; appWon = false; contradiction = false; guess = pick(); marks = ["a", "a", "a", "a", "a"];
    };
    const reset = () => { history = []; recorded = false; rebuild(); };
    reset();
    let started = false;

    root.innerHTML = P.topbar("Reverso") + `<div class="game" data-body style="padding-bottom:16px"></div>`;
    const body = P.$("[data-body]", root);
    function draw() {
      if (!started) {
        body.innerHTML = `<div class="center" style="margin:auto 0">
          <div class="mark bounce" style="justify-content:center"><span></span><span></span><span></span><span></span><span></span></div>
          <h2 style="font-size:1.6rem">Pense numa palavra de 5 letras</h2>
          <p class="muted">Agora é o Palavreiro que tenta adivinhar. A cada chute, toque nos quadrados para marcar as cores: verde se a letra está no lugar certo, amarelo se está na palavra em outro lugar, cinza se não está.</p>
          <p class="muted">O site tem 6 chances.</p><button class="pill wide" data-go>Já pensei!</button></div>`;
        P.$("[data-go]", body).onclick = () => { started = true; draw(); };
        return;
      }
      const hist = history.map((h) => `<div class="ex" style="justify-content:center">${[...P.words.display(h.word)].map((c, i) => `<div class="tile ${h.marks[i]}" style="--tile:46px">${c}</div>`).join("")}</div>`).join("");
      let main;
      if (!over) {
        main = `<p class="center">Meu chute é…</p><div class="mark-row">${[...P.words.display(guess)].map((c, i) => `<div class="tile ${marks[i]}" data-i="${i}">${c}</div>`).join("")}</div>
          <p class="center muted" style="font-size:.85rem">Toque nas letras para trocar a cor · ${common.length + rare.length} palavras ainda possíveis</p>`;
      } else {
        const [t, s] = appWon ? ["Acertei!", `Adivinhei em ${history.length} ${history.length === 1 ? "chute" : "chutes"}.`]
          : contradiction ? ["Hmm, não conheço essa…", "Nenhuma palavra que eu conheço combina com essas cores. Confere se marcou tudo certo? Você pode desfazer a última."]
          : ["Você venceu!", "Não consegui adivinhar em 6 chutes. Boa escolha de palavra!"];
        main = `<h2 class="center">${t}</h2><p class="center muted">${s}</p>`;
      }
      body.innerHTML = `<p class="center muted">Chute ${Math.min(history.length + 1, 6)} de 6</p>${hist}${main}
        ${P.statsHTML([[P.store.get("rev-app", 0), "O site acertou"], [P.store.get("rev-user", 0), "Você venceu"]], "two")}
        <div class="row-btns" style="margin-top:auto">${!over ? `<button class="pill ghost" data-undo ${history.length ? "" : "disabled"}>Desfazer</button><button class="pill" data-send>Enviar cores</button>`
          : `${contradiction ? `<button class="pill ghost" data-undo>Desfazer</button>` : ""}<button class="pill" data-again>Pensar em outra</button>`}</div>`;
      body.querySelectorAll(".mark-row .tile").forEach((t) => (t.onclick = () => { const i = +t.dataset.i; marks[i] = ORD[(ORD.indexOf(marks[i]) + 2) % 3]; P.fx.type(); draw(); }));
      const u = P.$("[data-undo]", body); if (u) u.onclick = () => { history.pop(); rebuild(); draw(); };
      const a = P.$("[data-again]", body); if (a) a.onclick = () => { reset(); draw(); };
      const s = P.$("[data-send]", body); if (s) s.onclick = send;
    }
    function send() {
      history.push({ word: guess, marks: marks.slice() });
      if (marks.every((m) => m === "c")) { over = true; appWon = true; }
      else {
        const ok = (w) => w !== guess && P.evaluate(guess, w).join("") === marks.join("");
        common = common.filter(ok); rare = rare.filter(ok);
        if (history.length >= 6) { over = true; appWon = false; }
        else if (!common.length && !rare.length) { over = true; contradiction = true; }
        else { const prev = guess; guess = pick(); marks = [0, 1, 2, 3, 4].map((i) => (history[history.length - 1].marks[i] === "c" && guess[i] === prev[i] ? "c" : "a")); }
      }
      if (over && !contradiction && !recorded) {
        recorded = true; P.logActivity();
        if (appWon) { P.store.add("rev-app"); P.fx.win(); } else { P.store.add("rev-user"); P.fx.lose(); P.confetti(); }
      }
      draw();
    }
    draw();
  };

  // ---------- Qual é a Palavra? ----------
  P.games.definicao = async function (root) {
    const defs = (await P.text("definicoes.txt")).split("\n").filter((l) => l.includes("|") && !l.startsWith("#"))
      .map((l) => ({ word: l.split("|")[0].trim(), text: l.split("|")[1].trim() }));
    let deck = P.shuffle(defs), idx = 0, item, target, slots, revealed, typed, misses, state, streak = 0, points = 0;
    const load = () => {
      item = deck[idx]; target = P.norm(item.word); slots = [...target].map((c, i) => (/[A-Z]/.test(c) ? i : -1)).filter((i) => i >= 0);
      revealed = []; typed = []; misses = 0; state = "playing";
      if (slots.length >= 9) revealed.push(slots[Math.floor(Math.random() * slots.length)]);
    };
    load();
    const open = () => slots.filter((i) => !revealed.includes(i));

    root.innerHTML = P.topbar("Qual é a Palavra?") + `<div class="game" data-body></div>`;
    const body = P.$("[data-body]", root);
    const kbHost = P.h(`<div></div>`); root.appendChild(kbHost);
    P.keyboard(kbHost, {
      enterLabel: "ENVIAR",
      onLetter: (c) => { if (state === "playing" && typed.length < open().length) { typed.push(c); P.fx.type(); draw(); } },
      onDelete: () => { typed.pop(); draw(); },
      onEnter: submit,
    });
    function letterAt(i) {
      if (!/[A-Z]/.test(target[i])) return target[i];
      if (state !== "playing") return item.word.toUpperCase()[i] || target[i];
      if (revealed.includes(i)) return target[i];
      return typed[open().indexOf(i)] || "";
    }
    function draw() {
      const best = P.store.get("def-best", 0);
      body.innerHTML = `${P.statsHTML([[streak, "Sequência"], [points, "Pontos"], [best, "Recorde"]], "three")}
        <div class="big-q">${P.esc(item.text)}</div>
        <div class="slots" style="--n:${target.length}">${[...target].map((c, i) => {
          if (!/[A-Z]/.test(c)) return `<span class="muted" style="align-self:center;font-size:1.4rem">${c}</span>`;
          const cls = state === "right" ? "c" : state === "wrong" ? "wrongslot" : revealed.includes(i) ? "p" : "input";
          return `<div class="tile ${cls}" style="${cls === "wrongslot" ? "background:var(--red)" : ""}">${letterAt(i)}</div>`;
        }).join("")}</div>
        <p class="center muted">${slots.length} letras · ${3 - misses} ${3 - misses === 1 ? "chance" : "chances"}</p>
        <div class="center">${state === "playing" ? `<button class="textlink" data-give>Desistir</button>` : `<button class="pill" data-next>${state === "right" ? "Próxima palavra" : "Começar de novo"}</button>`}</div>`;
      const gv = P.$("[data-give]", body); if (gv) gv.onclick = () => { state = "wrong"; streak = 0; P.toast(`Era ${item.word}`); draw(); };
      const nx = P.$("[data-next]", body); if (nx) nx.onclick = () => { idx++; if (idx >= deck.length) { deck = P.shuffle(defs); idx = 0; } load(); draw(); };
    }
    function submit() {
      if (state !== "playing") return;
      if (typed.length < open().length) return P.toast("Complete a palavra");
      const guess = [...target]; open().forEach((pos, k) => (guess[pos] = typed[k]));
      if (guess.join("") === target) {
        state = "right"; streak++; points += 3 - misses; P.store.add("def-right"); P.store.max("def-best", streak); P.logActivity();
        P.fx.win(); P.toast(["Isso!", "Acertou!", "Boa!", "Mandou bem!"][Math.floor(Math.random() * 4)]);
      } else {
        misses++; typed = []; P.fx.invalid();
        if (misses >= 3 || open().length <= 1) { state = "wrong"; streak = 0; P.toast(`Era ${item.word}`); }
        else { const o = open(); revealed.push(o[Math.floor(Math.random() * o.length)]); P.toast("Não é essa. Ganhou uma letra!"); }
      }
      draw();
    }
    draw();
  };

  // ---------- Sinônimos em cadeia ----------
  P.games.sinonimos = async function (root) {
    const pairs = [], fams = [];
    (await P.text("sinonimos.txt")).split("\n").forEach((raw) => {
      const l = raw.trim();
      if (!l || l.startsWith("#")) return;
      if (l.startsWith("=")) fams.push(new Set(l.slice(1).split(",").map((s) => s.trim())));
      else if (l.includes("|")) pairs.push({ word: l.split("|")[0].trim(), syn: l.split("|")[1].trim() });
    });
    const famOf = (w) => { const s = new Set([w]); fams.forEach((f) => f.has(w) && f.forEach((x) => s.add(x))); return s; };
    const SECS = 10;
    let deck, idx, chain, over, picked, options, started = false, raf, t0;

    const build = () => {
      const q = deck[idx], blocked = new Set([...famOf(q.word), ...famOf(q.syn)]);
      const pool = [...new Set(pairs.map((p) => p.syn))].filter((s) => !blocked.has(s) && ![...famOf(s)].some((f) => blocked.has(f)));
      options = P.shuffle(P.shuffle(pool).slice(0, 3).concat(q.syn)); picked = null;
    };
    const reset = () => { deck = P.shuffle(pairs); idx = 0; chain = 0; over = false; build(); };
    reset();

    root.innerHTML = P.topbar("Sinônimos") + `<div class="game" data-body style="padding-bottom:16px"></div>`;
    const body = P.$("[data-body]", root);
    function tick() {
      if (!document.body.contains(body) || over || picked) return;
      const left = 1 - (performance.now() - t0) / (SECS * 1000);
      const bar = P.$(".timer i", body);
      if (bar) { bar.style.width = Math.max(0, left * 100) + "%"; bar.style.background = left < 0.3 ? "var(--red)" : "var(--accent)"; }
      if (left <= 0) { over = true; P.fx.lose(); end(); draw(); return; }
      raf = requestAnimationFrame(tick);
    }
    const end = () => { P.store.max("syn-best", chain); P.logActivity(); };
    function draw() {
      const best = P.store.get("syn-best", 0), q = deck[idx];
      if (!started) {
        body.innerHTML = `${P.statsHTML([[0, "Cadeia"], [best, "Recorde"]], "two")}<div class="center" style="margin:auto 0">
          <h2>Escolha o sinônimo certo</h2><p class="muted">Aparece uma palavra e quatro opções. Toque na que tem o mesmo sentido. Você tem ${SECS} segundos por palavra, e a cadeia continua até o primeiro erro.</p>
          <button class="pill wide" data-go>Começar</button></div>`;
        P.$("[data-go]", body).onclick = () => { started = true; t0 = performance.now(); draw(); raf = requestAnimationFrame(tick); };
        return;
      }
      body.innerHTML = `${P.statsHTML([[chain, "Cadeia"], [Math.max(best, chain), "Recorde"]], "two")}
        <div class="timer"><i style="width:100%"></i></div>
        <p class="center muted" style="margin-top:24px">Qual é o sinônimo de</p><div class="word-big">${P.esc(q.word)}</div>
        ${options.map((o) => { const cls = picked == null && !over ? "" : o === q.syn ? "right" : o === picked ? "wrong" : "dim"; return `<button class="opt ${cls}" data-o="${P.esc(o)}">${P.esc(o)}</button>`; }).join("")}
        ${over ? `<h2 class="center">${picked == null ? "O tempo acabou!" : "Fim da cadeia!"}</h2><p class="center muted">${P.esc(q.word)} = ${P.esc(q.syn)}</p><button class="pill wide" data-again>Jogar de novo</button>` : ""}`;
      body.querySelectorAll("[data-o]").forEach((b) => (b.onclick = () => {
        if (over || picked) return;
        picked = b.dataset.o; cancelAnimationFrame(raf);
        if (picked === q.syn) {
          chain++; P.fx.reveal(chain % 5, "c"); draw();
          setTimeout(() => { idx = (idx + 1) % deck.length; build(); t0 = performance.now(); draw(); raf = requestAnimationFrame(tick); }, 550);
        } else { over = true; P.fx.invalid(); end(); draw(); }
      }));
      const ag = P.$("[data-again]", body); if (ag) ag.onclick = () => { reset(); t0 = performance.now(); draw(); raf = requestAnimationFrame(tick); };
    }
    draw();
  };
})();
