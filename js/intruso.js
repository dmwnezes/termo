// Intruso (site): quatro palavras de um grupo do Conexões e uma intrusa. Ache a que não pertence.
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

  P.games.intruso = async function (root) {
    const [groupsAll, fam] = await Promise.all([P.text("conexoes.txt").then(parseGroups), P.text("conexoes-familias.txt").then(parseFamilies)]);
    // Um grupo por nome, só os que têm famílias conhecidas e nenhuma família proibida.
    const byName = {};
    groupsAll.forEach((g) => { if (!byName[g.name] && g.words.length === 4 && fam[g.name] && !fam[g.name].some((f) => BAD.includes(f))) byName[g.name] = g; });
    const pool = Object.values(byName);

    let points = 0, lives = 3, used = new Set(), round = null, locked = false, over = false;

    root.innerHTML = P.topbar("Intruso", { help: true }) + `<div class="game intr">
      <div class="score-row"><span><small>Pontos</small><b data-pts>0</b></span><span class="hearts" data-lives aria-label="Vidas"></span></div>
      <p class="center muted intr-q">Qual palavra não pertence ao grupo?</p>
      <div class="intr-list" data-list></div>
      <p class="center intr-msg" data-msg aria-live="polite"></p></div>`;
    const list = P.$("[data-list]", root), msg = P.$("[data-msg]", root);

    function pick() {
      let cands = pool.filter((g) => !used.has(g.name));
      if (!cands.length) { used = new Set(); cands = pool; }
      for (let k = 0; k < 200; k++) {
        const base = cands[Math.floor(Math.random() * cands.length)];
        const bf = fam[base.name], bw = new Set(base.words.map(P.norm));
        const others = pool.filter((g) => g !== base && !fam[g.name].some((f) => bf.includes(f)));
        const words = others.flatMap((g) => g.words.map((w) => ({ w, g }))).filter((x) => !bw.has(P.norm(x.w)));
        if (!words.length) continue;
        const intr = words[Math.floor(Math.random() * words.length)];
        used.add(base.name);
        return { base, intruder: intr.w, words: P.shuffle(base.words.concat(intr.w)) };
      }
      return null;
    }

    function hud() {
      P.$("[data-pts]", root).textContent = points;
      P.$("[data-lives]", root).innerHTML = [0, 1, 2].map((i) => `<i class="${i < lives ? "" : "lost"}">❤️</i>`).join("");
    }

    function next() {
      round = pick(); locked = false; msg.textContent = ""; msg.className = "center intr-msg";
      list.innerHTML = "";
      round.words.forEach((w) => {
        const b = P.h(`<button class="opt intr-opt">${P.esc(w)}</button>`);
        b.onclick = () => choose(b, w);
        list.appendChild(b);
      });
      hud();
    }

    function choose(btn, w) {
      if (locked || over) return;
      locked = true;
      const right = w === round.intruder;
      list.querySelectorAll("button").forEach((b) => {
        if (b.textContent === round.intruder) b.classList.add("right");
        else if (b === btn) b.classList.add("wrong");
        else b.classList.add("dim");
      });
      msg.textContent = `Eram todos: ${round.base.name}`;
      if (right) { points++; P.store.add("intr-right"); P.fx.reveal(2, "c"); msg.classList.add("good"); }
      else { lives--; P.fx.invalid(); msg.classList.add("bad"); }
      hud();
      setTimeout(() => { if (!document.body.contains(list)) return; if (lives <= 0) end(); else next(); }, 1300);
    }

    function end() {
      over = true;
      P.store.add("intr-played"); P.store.max("intr-best", points); P.logActivity();
      if (points > 0 && points >= P.store.get("intr-best", 0)) P.fx.win(); else P.fx.lose();
      const ui = P.sheet(`<h2>Fim de jogo</h2><p class="subtitle">Você fez ${points} ${points === 1 ? "ponto" : "pontos"}</p>
        ${P.statsHTML([[points, "Pontos"], [P.store.get("intr-best", 0), "Recorde"], [P.store.get("intr-right", 0), "Acertos totais"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>Jogar de novo</button></div>`);
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Intruso\nFiz ${points} ${points === 1 ? "ponto" : "pontos"} 🕵️`);
      P.$("[data-again]", ui.el).onclick = () => { ui.close(); points = 0; lives = 3; used = new Set(); over = false; next(); };
    }

    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Intruso</h2>
      <p>Quatro palavras têm algo em comum. Toque na que não pertence ao grupo. Você tem 3 vidas.</p>`);
    if (!pool.length) { msg.textContent = "Não foi possível carregar as palavras."; return; }
    next();
  };
})();
