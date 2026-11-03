// Empilha (site): solte o bloco em cima da pilha; o que sobra para fora cai. Mesmo motor do app (game/Empilha.kt).
(function () {
  "use strict";
  const HELP = "Toque para soltar o bloco que se move. Só a parte que ficar sobre a pilha continua, então os blocos vão diminuindo. Blocos quase alinhados encaixam certinho. A velocidade aumenta. A partida acaba quando você erra a pilha.";
  const COLORS = ["#5FB873", "#E6C14F", "#6FA8E8", "#E5737A", "#9B8CFF", "#FF9F5A", "#5FD0C5"];
  P.empilha = {
    WIDTH: 100, START: 60, SNAP: 1.5,
    create: () => ({ tower: [{ x: 20, w: 60 }], x: 0, w: 60, dir: 1, over: false, perfect: false }),
    score: (g) => g.tower.length - 1,
    speed: (g) => Math.min(120, 45 + P.empilha.score(g) * 2),
    tick(g, dt) {
      if (g.over) return;
      g.x += g.dir * P.empilha.speed(g) * dt;
      const max = P.empilha.WIDTH - g.w;
      if (g.x <= 0) { g.x = -g.x; g.dir = 1; }
      if (g.x >= max) { g.x = 2 * max - g.x; g.dir = -1; }
      if (g.x < 0) g.x = 0;
    },
    drop(g) {
      if (g.over) return;
      const top = g.tower[g.tower.length - 1];
      g.perfect = Math.abs(g.x - top.x) <= P.empilha.SNAP;
      if (g.perfect) g.x = top.x;
      const left = Math.max(g.x, top.x), right = Math.min(g.x + g.w, top.x + top.w), w = right - left;
      if (w < 1) { g.over = true; return; } // sobra invisível também acaba a partida
      g.tower.push({ x: left, w });
      g.w = w;
      // O próximo sai do lado oposto ao último.
      g.dir = -g.dir;
      g.x = g.dir > 0 ? 0 : P.empilha.WIDTH - w;
    },
  };
  P.games.empilha = function (root) {
    root.innerHTML = P.topbar("Empilha", { help: true }) + `<div class="game emp"><div class="score-row"><span><small>Altura</small><b data-score>0</b></span><span><small>Recorde</small><b data-best>${P.store.get("emp-best", 0) || 0}</b></span></div>
      <canvas class="emp-board" data-board width="400" height="560" aria-label="Pilha de blocos: toque para soltar"></canvas><div class="row-btns" data-actions></div></div>`;
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Empilha</h2><p>${HELP}</p>`);
    const cv = P.$("[data-board]", root), ctx = cv.getContext("2d");
    let g = P.empilha.create(), last = 0, ended = false;
    const H = 34, UNIT = cv.width / P.empilha.WIDTH;
    function draw() {
      ctx.clearRect(0, 0, cv.width, cv.height);
      const n = g.tower.length, base = Math.max(0, n - 12);
      const y = (i) => cv.height - (i - base + 1) * H;
      for (let i = base; i < n; i++) { const b = g.tower[i]; ctx.fillStyle = COLORS[i % COLORS.length]; ctx.beginPath(); ctx.roundRect(b.x * UNIT, y(i), b.w * UNIT, H - 3, 8); ctx.fill(); }
      if (!g.over) { ctx.fillStyle = COLORS[n % COLORS.length]; ctx.beginPath(); ctx.roundRect(g.x * UNIT, y(n), g.w * UNIT, H - 3, 8); ctx.fill(); }
      P.$("[data-score]", root).textContent = P.empilha.score(g);
    }
    function actions() {
      const A = P.$("[data-actions]", root);
      A.innerHTML = g.over ? `<button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>Jogar de novo</button>` : `<button class="pill wide" data-drop>Soltar</button>`;
      const d = P.$("[data-drop]", A); if (d) d.onclick = drop;
      const s = P.$("[data-share]", A); if (s) s.onclick = () => P.share(`Empilha 🧱 altura ${P.empilha.score(g)}\nhttps://dmwnezes.com/termo/`);
      const a = P.$("[data-again]", A); if (a) a.onclick = () => { g = P.empilha.create(); ended = false; actions(); };
    }
    function drop() {
      if (g.over) return;
      P.empilha.drop(g);
      if (g.over) {
        if (!ended) {
          ended = true;
          P.store.add("emp-played"); P.logActivity();
          const b = P.store.get("emp-best", 0) || 0, sc = P.empilha.score(g);
          if (sc > b) { P.store.set("emp-best", sc); P.$("[data-best]", root).textContent = sc; if (sc >= 10) P.confetti(); }
          P.fx.lose();
        }
        actions();
      } else if (g.perfect) P.fx.reveal(0, "c"); else P.fx.type();
    }
    function loop(t) {
      if (!document.body.contains(cv)) return;
      if (last) P.empilha.tick(g, Math.min(0.05, (t - last) / 1000));
      last = t; draw();
      requestAnimationFrame(loop);
    }
    cv.onpointerdown = (e) => { e.preventDefault(); drop(); };
    const key = (e) => { if (!document.body.contains(cv)) return document.removeEventListener("keydown", key); if (e.code === "Space" && !document.querySelector(".overlay")) { e.preventDefault(); drop(); } };
    document.addEventListener("keydown", key);
    actions();
    P.empilhaState = () => g;
    requestAnimationFrame(loop);
  };
})();
