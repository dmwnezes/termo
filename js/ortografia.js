// Certo ou Errado (site): a palavra está escrita do jeito certo?
(function () {
  "use strict";
  const parse = (t) => t.split("\n").map((l) => l.trim()).filter((l) => l && !l.startsWith("#") && l.includes("|"))
    .map((l) => { const [right, wrong] = l.split("|").map((s) => s.trim()); return { right, wrong }; }).filter((x) => x.right && x.wrong);
  const up = (s) => s.toLocaleUpperCase("pt-BR");

  P.games.ortografia = async function (root) {
    const all = await P.text("ortografia.txt").then(parse);
    let deck = [], points = 0, lives = 3, cur = null, locked = false, over = false;

    root.innerHTML = P.topbar("Certo ou Errado", { help: true }) + `<div class="game ort">
      <div class="score-row"><span><small>Pontos</small><b data-pts>0</b></span><span class="hearts" data-lives aria-label="Vidas"></span></div>
      <div class="ort-card" data-card><small>Está escrita certo?</small><div class="ort-word" data-word></div><p class="ort-fix" data-fix aria-live="polite"></p></div>
      <div class="ort-btns"><button class="ort-btn no" data-ans="0"><span aria-hidden="true">✗</span> Errado</button><button class="ort-btn yes" data-ans="1"><span aria-hidden="true">✓</span> Certo</button></div></div>`;
    const card = P.$("[data-card]", root), word = P.$("[data-word]", root), fix = P.$("[data-fix]", root);

    function hud() {
      P.$("[data-pts]", root).textContent = points;
      P.$("[data-lives]", root).innerHTML = [0, 1, 2].map((i) => `<i class="${i < lives ? "" : "lost"}">❤️</i>`).join("");
    }
    function next() {
      if (!deck.length) deck = P.shuffle(all);
      const item = deck.pop();
      cur = { item, showRight: Math.random() < 0.5 };
      const w = up(cur.showRight ? item.right : item.wrong);
      word.textContent = w; word.style.fontSize = w.length > 13 ? "1.6rem" : w.length > 10 ? "2rem" : "";
      fix.textContent = ""; card.className = "ort-card"; locked = false; hud();
    }
    function answer(saysRight) {
      if (locked || over) return;
      locked = true;
      if (saysRight === cur.showRight) {
        points++; P.store.add("ort-right"); P.fx.reveal(2, "c"); P.toast("Isso!", 1000); card.classList.add("good");
        if (!cur.showRight) fix.textContent = `Certo: ${up(cur.item.right)}`;
      } else {
        lives--; P.fx.invalid(); card.classList.add("bad");
        fix.textContent = `Certo: ${up(cur.item.right)}`;
      }
      hud();
      setTimeout(() => { if (!document.body.contains(card)) return; if (lives <= 0) end(); else next(); }, 1200);
    }
    function end() {
      over = true;
      P.store.add("ort-played"); P.store.max("ort-best", points); P.logActivity();
      if (points > 0 && points >= P.store.get("ort-best", 0)) P.fx.win(); else P.fx.lose();
      const ui = P.sheet(`<h2>Fim de jogo</h2><p class="subtitle">Você fez ${points} ${points === 1 ? "ponto" : "pontos"}</p>
        ${P.statsHTML([[points, "Pontos"], [P.store.get("ort-best", 0), "Recorde"], [P.store.get("ort-right", 0), "Acertos totais"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>Jogar de novo</button></div>`);
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Certo ou Errado\nFiz ${points} ${points === 1 ? "ponto" : "pontos"} ✍️`);
      P.$("[data-again]", ui.el).onclick = () => { ui.close(); points = 0; lives = 3; over = false; deck = []; next(); };
    }
    root.querySelectorAll("[data-ans]").forEach((b) => (b.onclick = () => answer(b.dataset.ans === "1")));
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Certo ou Errado</h2>
      <p>Veja a palavra e diga se ela está escrita do jeito certo. Você tem 3 vidas.</p>`);
    next();
  };
})();
