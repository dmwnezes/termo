// Intruso (site): palavras de um grupo do Conexões e uma intrusa. Ache a que não pertence, antes do tempo acabar.
(function () {
  "use strict";
  const BAD = ["lacuna", "palavra", "fora"];

  const parseGroups = (t) => {
    const out = [];
    t.split("\n").forEach((raw) => {
      const l = raw.trim();
      if (!l || l.startsWith("#") || !l.includes(":")) return;
      const i = l.lastIndexOf(":");
      out.push({ name: l.slice(0, i).trim(), words: l.slice(i + 1).split(",").map((s) => s.trim()).filter(Boolean) });
    });
    return out;
  };
  const parseFamilies = (t) => {
    const m = {};
    t.split("\n").forEach((raw) => { const l = raw.trim(); if (!l || l.startsWith("#") || !l.includes(":")) return; m[l.slice(0, l.indexOf(":")).trim()] = l.slice(l.indexOf(":") + 1).split(",").map((x) => x.trim()).filter(Boolean); });
    return m;
  };

  // Também usados pelo Mestre Mandou.
  P.parseGroups = parseGroups; P.parseFamilies = parseFamilies; P.BAD_FAMILIES = BAD;

  // Regras de dificuldade (as mesmas do app, game/Party.kt):
  // até 4 pontos, 3 palavras do grupo + a intrusa; a partir de 5, 4 + a intrusa;
  // a partir de 3 pontos a intrusa vem de um assunto vizinho (a 1ª família de cada grupo forma um par de NEAR);
  // tempo da rodada = max(4, 12 − 0,3 × pontos) segundos; deixar acabar custa uma vida.
  const NEAR_FROM = 3, FIVE_FROM = 5;
  // Assuntos vizinhos (vale nos dois sentidos); a mesma lista está no app (IntruderGame.NEAR).
  const NEAR = new Set("animal-natureza comida-natureza comida-casa comida-festa casa-escola casa-tecnologia roupa-corpo roupa-esporte roupa-artesanato corpo-saude esporte-jogo esporte-praia jogo-festa musica-festa musica-arte arte-artesanato ceu-natureza ceu-tempo ceu-mito tempo-festa tempo-medida lugar-transporte lugar-praia transporte-tecnologia escola-trabalho escola-jogo trabalho-saude trabalho-dinheiro mineral-natureza mineral-cor cor-arte forma-escola forma-medida sentimento-familia mito-signo".split(" ").flatMap((p) => [p, p.split("-").reverse().join("-")]));
  const optionsFor = (pts) => (pts >= FIVE_FROM ? 5 : 4);
  const secondsFor = (pts) => Math.max(4, 12 - 0.3 * pts);
  const HELP = "Todas as palavras menos uma têm algo em comum. Toque na intrusa antes de o tempo acabar. " +
    "A partir de 3 pontos a intrusa vem de um assunto parecido; a partir de 5 pontos são 5 palavras em vez de 4; " +
    "e o tempo encurta a cada acerto, de 12 até 4 segundos. Errar ou deixar o tempo acabar custa uma das 3 vidas.";

  P.games.intruso = async function (root) {
    const [groupsAll, fam] = await Promise.all([P.text("conexoes.txt").then(parseGroups), P.text("conexoes-familias.txt").then(parseFamilies)]);
    // Um grupo por nome, só os que têm famílias conhecidas e nenhuma família proibida.
    const byName = {};
    groupsAll.forEach((g) => { if (!byName[g.name] && g.words.length === 4 && fam[g.name] && fam[g.name].length && !fam[g.name].some((f) => BAD.includes(f))) byName[g.name] = g; });
    const pool = Object.values(byName);
    // Vizinho: sem família em comum, e as famílias principais (1ª da lista) formam um par de NEAR.
    const isNear = (bf, of) => !bf.some((f) => of.includes(f)) && NEAR.has(bf[0] + "-" + of[0]);

    let points = 0, lives = 3, used = new Set(), round = null, locked = false, over = false;
    let secs = 12, t0 = 0, elapsed = 0, raf = 0, paused = false;

    root.innerHTML = P.topbar("Intruso", { help: true }) + `<div class="game intr">
      <div class="score-row"><span><small>Pontos</small><b data-pts>0</b></span><span class="hearts" data-lives aria-label="Vidas"></span></div>
      <div class="timer" style="margin:4px 0 14px"><i data-bar style="width:100%"></i></div>
      <p class="center muted intr-q">Qual palavra não pertence ao grupo?</p>
      <div class="intr-list" data-list></div>
      <p class="center intr-msg" data-msg aria-live="polite"></p></div>`;
    const list = P.$("[data-list]", root), msg = P.$("[data-msg]", root), bar = P.$("[data-bar]", root);

    function pick() {
      let cands = pool.filter((g) => !used.has(g.name));
      if (!cands.length) { used = new Set(); cands = pool; }
      for (let k = 0; k < 100; k++) {
        const base = cands[Math.floor(Math.random() * cands.length)];
        const bf = fam[base.name];
        const far = pool.filter((g) => g !== base && !fam[g.name].some((f) => bf.includes(f)));
        // Palavras que também aparecem num grupo de assunto em comum (inclusive o próprio) nunca são intrusas.
        const bw = new Set(pool.filter((g) => !far.includes(g)).flatMap((g) => g.words.map(P.norm)));
        const words = (gs) => gs.flatMap((g) => g.words).filter((w) => !bw.has(P.norm(w)));
        const close = points >= NEAR_FROM ? words(far.filter((g) => isNear(bf, fam[g.name]))) : [];
        const opts = close.length ? close : words(far);
        if (!opts.length) continue;
        const intruder = opts[Math.floor(Math.random() * opts.length)];
        used.add(base.name);
        const shown = P.shuffle(base.words.slice()).slice(0, optionsFor(points) - 1);
        return { base, intruder, words: P.shuffle(shown.concat(intruder)) };
      }
      return null;
    }

    function hud() {
      P.$("[data-pts]", root).textContent = points;
      P.$("[data-lives]", root).innerHTML = [0, 1, 2].map((i) => `<i class="${i < lives ? "" : "lost"}">❤️</i>`).join("");
    }

    function paint(left) {
      bar.style.width = Math.max(0, left * 100) + "%";
      bar.style.background = left < 0.3 ? "var(--red)" : "var(--accent)";
    }
    function tick() {
      if (!document.body.contains(list) || locked || over || paused) return;
      const left = 1 - (elapsed + performance.now() - t0) / (secs * 1000);
      paint(left);
      if (left <= 0) return choose(null, null);
      raf = requestAnimationFrame(tick);
    }
    function pause() { if (paused) return; paused = true; elapsed += performance.now() - t0; cancelAnimationFrame(raf); }
    function resume() { if (!paused) return; paused = false; t0 = performance.now(); if (!locked && !over) raf = requestAnimationFrame(tick); }

    function next() {
      round = pick(); locked = false; msg.textContent = ""; msg.className = "center intr-msg";
      list.innerHTML = "";
      round.words.forEach((w) => {
        const b = P.h(`<button class="opt intr-opt">${P.esc(w)}</button>`);
        b.onclick = () => choose(b, w);
        list.appendChild(b);
      });
      hud();
      secs = secondsFor(points); elapsed = 0; t0 = performance.now(); paint(1);
      cancelAnimationFrame(raf); if (!paused) raf = requestAnimationFrame(tick);
    }

    // btn/w nulos = o tempo acabou.
    function choose(btn, w) {
      if (locked || over) return;
      locked = true; cancelAnimationFrame(raf);
      const right = w === round.intruder;
      list.querySelectorAll("button").forEach((b) => {
        if (b.textContent === round.intruder) b.classList.add("right");
        else if (b === btn) b.classList.add("wrong");
        else b.classList.add("dim");
      });
      msg.textContent = (w === null ? "O tempo acabou! " : "") + `Eram todos: ${round.base.name}`;
      if (right) { points++; P.store.add("intr-right"); P.fx.reveal(2, "c"); msg.classList.add("good"); }
      else { lives--; P.fx.invalid(); msg.classList.add("bad"); }
      hud();
      setTimeout(() => { if (!document.body.contains(list)) return; if (lives <= 0) end(); else next(); }, 1300);
    }

    function end() {
      over = true; cancelAnimationFrame(raf);
      P.store.add("intr-played"); P.store.max("intr-best", points); P.logActivity();
      if (points > 0 && points >= P.store.get("intr-best", 0)) P.fx.win(); else P.fx.lose();
      const ui = P.sheet(`<h2>Fim de jogo</h2><p class="subtitle">Você fez ${points} ${points === 1 ? "ponto" : "pontos"}</p>
        ${P.statsHTML([[points, "Pontos"], [P.store.get("intr-best", 0), "Recorde"], [P.store.get("intr-right", 0), "Acertos totais"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>Jogar de novo</button></div>`);
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Intruso\nFiz ${points} ${points === 1 ? "ponto" : "pontos"} 🕵️`);
      P.$("[data-again]", ui.el).onclick = () => { ui.close(); points = 0; lives = 3; used = new Set(); over = false; next(); };
    }

    // A ajuda pausa o tempo da rodada.
    P.$("[data-help]", root).onclick = () => { pause(); P.sheet(`<h2>Como jogar Intruso</h2><p>${HELP}</p>`, resume); };
    if (!pool.length) { msg.textContent = "Não foi possível carregar as palavras."; return; }
    next();
  };
})();
