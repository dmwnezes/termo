// Mestre Mandou (site): obedeça a ordem só quando ela começar com "O mestre mandou". Mesmas regras do app.
(function () {
  "use strict";
  const RARE = [..."BCDFGHJLMNPQRSTVXZ"];
  const HELP = "Faça o que a ordem pede, mas só quando começar com 'O mestre mandou'. Se o mestre não mandou, não toque em nada e espere o tempo acabar. Cuidado com o NÃO e com o TODAS. Você tem 3 vidas.";
  const up = (s) => s.toLocaleUpperCase("pt-BR");
  const rand = (a) => a[Math.floor(Math.random() * a.length)];
  // Acento = qualquer marca sobre a letra (agudo, circunflexo, til, crase…). Cedilha não conta.
  const hasAccent = (w) => /[̀-ͯ]/.test(w.normalize("NFD").replace(/̧/g, ""));
  const letters = (n) => n.replace(/[^A-Z]/g, "");

  /** Lê os arquivos e monta os bancos de palavras (uma vez por visita). */
  let dataP = null;
  const loadData = () => dataP || (dataP = Promise.all(["conexoes.txt", "conexoes-familias.txt", "sinonimos.txt", "antonimos.txt"].map(P.text)).then(([conn, connFam, syn, ant]) => {
    const groupsAll = P.parseGroups(conn), fam = P.parseFamilies(connFam);
    const synPairs = [], synFams = [], antPairs = [], antFams = [];
    P.parseChain(syn, synPairs, synFams);
    P.parseChain(ant, antPairs, antFams);
    // Banco de letras: palavras simples (sem espaço, hífen ou apóstrofo) de 4 a 10 letras, sem repetição.
    const bank = [], seen = new Set();
    const addWord = (raw) => {
      const d = up(raw.trim());
      if (!d || /[\s\-'’]/.test(d)) return;
      const n = P.norm(d);
      if (!/^[A-Z]+$/.test(n) || n.length < 4 || n.length > 10 || seen.has(n)) return;
      seen.add(n); bank.push({ d, n, acc: hasAccent(d) });
    };
    groupsAll.forEach((g) => g.words.forEach(addWord));
    synPairs.forEach((p) => { addWord(p.word); addWord(p.syn); });
    antPairs.forEach((p) => { addWord(p.word); addWord(p.syn); });
    // Grupos de categoria: um por nome, com famílias conhecidas e nenhuma proibida (regra do Intruso).
    const byName = {};
    groupsAll.forEach((g) => { if (!byName[g.name] && g.words.length === 4 && fam[g.name] && !fam[g.name].some((f) => P.BAD_FAMILIES.includes(f))) byName[g.name] = g; });
    // Contrários nos dois sentidos; as famílias dos sinônimos também valem (regra do Antônimos).
    const antAll = antPairs.concat(antPairs.map((p) => ({ word: p.syn, syn: p.word })));
    return { bank, groups: Object.values(byName), fam, synPairs, synFams, antAll, antFams: antFams.concat(synFams) };
  }));

  // ---------- geração da rodada ----------
  const famOf = (fams, w) => { const s = new Set([w]); fams.forEach((f) => f.has(w) && f.forEach((x) => s.add(x))); return s; };
  /** Até `k` itens distintos (pela forma normalizada) de `pool`, sem repetir os de `avoid`. */
  const pickDistinct = (pool, k, avoid) => {
    const used = new Set(avoid.map((x) => P.norm(x))), out = [];
    for (const w of P.shuffle(pool)) { const n = P.norm(w); if (!used.has(n)) { used.add(n); out.push(w); if (out.length === k) break; } }
    return out.length === k ? out : null;
  };

  /** Critério de letra. all = TODAS (2 cumprem + 2 não). Devolve { sing, plur, words: [{d, ok}] } ou null. */
  function letterCrit(D, all) {
    const kinds = ["start", "end", "has", "accent", "len"].concat(all ? [] : ["longest"]);
    for (let c = 0; c < 12; c++) {
      const kind = rand(kinds);
      for (let k = 0; k < 50; k++) {
        const t = rand(kind === "accent" ? D.bank.filter((w) => w.acc) : D.bank);
        let X, test, sing, plur;
        if (kind === "start") { X = t.n[0]; test = (w) => w.n[0] === X; sing = `na palavra que começa com ${X}`; plur = `as palavras que começam com ${X}`; }
        else if (kind === "end") { X = t.n[t.n.length - 1]; test = (w) => w.n.endsWith(X); sing = `na palavra que termina com ${X}`; plur = `as palavras que terminam com ${X}`; }
        else if (kind === "has") {
          const opts = RARE.filter((x) => t.n.includes(x)); if (!opts.length) continue;
          X = rand(opts); test = (w) => w.n.includes(X); sing = `na palavra que tem a letra ${X}`; plur = `as palavras que têm a letra ${X}`;
        } else if (kind === "accent") { test = (w) => w.acc; sing = "na palavra com acento"; plur = "as palavras com acento"; }
        else if (kind === "len") { const N = letters(t.n).length; test = (w) => letters(w.n).length === N; sing = `na palavra de ${N} letras`; plur = `as palavras de ${N} letras`; }
        else { const N = t.n.length; if (N < 6) continue; test = (w) => w.n.length >= N; sing = "na palavra mais comprida"; }
        // Com a letra C, palavras com Ç ficam de fora (evita a dúvida C × Ç).
        const fair = (w) => X !== "C" || !w.d.includes("Ç");
        if (!fair(t)) continue;
        const yes = D.bank.filter((w) => w !== t && fair(w) && test(w)), no = D.bank.filter((w) => fair(w) && !test(w));
        let picks;
        if (all) {
          if (!yes.length) continue;
          const t2 = rand(yes), others = pickDistinct(no.map((w) => w.d), 2, [t.d, t2.d]);
          if (!others || P.norm(t2.d) === t.n) continue;
          picks = [{ d: t.d, ok: true }, { d: t2.d, ok: true }].concat(others.map((d) => ({ d, ok: false })));
        } else {
          const others = pickDistinct(no.map((w) => w.d), 3, [t.d]);
          if (!others) continue;
          picks = [{ d: t.d, ok: true }].concat(others.map((d) => ({ d, ok: false })));
        }
        return { sing, plur, words: P.shuffle(picks) };
      }
    }
    return null;
  }

  /** Critério de sentido: sinônimo, contrário ou categoria (só a categoria vale para TODAS). */
  function senseCrit(D, all) {
    const kind = all ? "cat" : rand(["syn", "ant", "cat"]);
    for (let k = 0; k < 50; k++) {
      if (kind === "cat") {
        const base = rand(D.groups), bf = D.fam[base.name];
        const pool = D.groups.filter((g) => g !== base && !D.fam[g.name].some((f) => bf.includes(f))).flatMap((g) => g.words);
        const right = P.shuffle(base.words).slice(0, all ? 2 : 1);
        const others = pickDistinct(pool, all ? 2 : 3, base.words);
        if (!others) continue;
        const name = up(base.name);
        return { sing: `na palavra do grupo ${name}`, plur: `as palavras do grupo ${name}`,
          words: P.shuffle(right.map((d) => ({ d: up(d), ok: true })).concat(others.map((d) => ({ d: up(d), ok: false })))) };
      }
      const [pairs, fams] = kind === "syn" ? [D.synPairs, D.synFams] : [D.antAll, D.antFams];
      const q = rand(pairs), blocked = new Set([...famOf(fams, q.word), ...famOf(fams, q.syn)]);
      const pool = [...new Set(pairs.map((p) => p.syn))].filter((s) => !blocked.has(s) && ![...famOf(fams, s)].some((f) => blocked.has(f)));
      const others = pickDistinct(pool, 3, [q.syn, q.word]);
      if (!others) continue;
      return { sing: `${kind === "syn" ? "no sinônimo" : "no contrário"} de ${up(q.word)}`,
        words: P.shuffle([{ d: up(q.syn), ok: true }].concat(others.map((d) => ({ d: up(d), ok: false })))) };
    }
    return null;
  }

  /** Sorteia o tipo de ordem e o critério. */
  function makeRound(D) {
    const r = Math.random();
    const type = r < 0.55 ? "normal" : r < 0.67 ? "nao" : r < 0.80 ? "todas" : r < 0.92 ? "sem" : "naomandou";
    const all = type === "todas";
    let crit = null;
    while (!crit) crit = Math.random() < 0.5 ? letterCrit(D, all) : senseCrit(D, all);
    const esc = P.esc, b = (s) => `<b>${s}</b>`;
    const text = {
      normal: `O mestre mandou: toque ${esc(crit.sing)}`,
      nao: `O mestre mandou: ${b("NÃO")} toque ${esc(crit.sing)}`,
      todas: `O mestre mandou: toque em ${b("TODAS")} ${esc(crit.plur || "")}`,
      sem: `Toque ${esc(crit.sing)}!`,
      naomandou: `O mestre ${b("NÃO")} mandou: toque ${esc(crit.sing)}`,
    }[type];
    return { type, text, words: crit.words, tapped: [] };
  }

  // ---------- tela ----------
  P.games.mestre = async function (root) {
    root.innerHTML = P.topbar("Mestre Mandou", { help: true }) + `<div class="game mm" data-body></div>`;
    const body = P.$("[data-body]", root);
    P.$("[data-help]", root).onclick = () => P.sheet(`<h2>Como jogar Mestre Mandou</h2><p>${HELP}</p>`);
    let D;
    try { D = await loadData(); } catch (_) { body.innerHTML = `<p class="center muted">Não foi possível carregar as palavras.</p>`; return; }
    if (!document.body.contains(body)) return;

    let points = 0, lives = 3, round = null, state = "start", t0 = 0, secs = 6, raf = 0, timer = 0;

    function start() {
      body.innerHTML = `${P.statsHTML([[P.store.get("mestre-best", 0), "Recorde"], [P.store.get("mestre-right", 0), "Acertos totais"]], "two")}
        <div class="center" style="margin:auto 0"><div class="mm-crown" aria-hidden="true">👑</div><h2>Mestre Mandou</h2>
        <p class="muted">${HELP}</p><button class="pill wide" data-go>Começar</button></div>`;
      P.$("[data-go]", body).onclick = () => { points = 0; lives = 3; layout(); next(); };
    }
    function layout() {
      body.innerHTML = `<div class="score-row"><span><small>Pontos</small><b data-pts>0</b></span><span class="hearts" data-lives aria-label="Vidas"></span></div>
        <div class="timer mm-timer"><i style="width:100%"></i></div>
        <div class="mm-order" data-order aria-live="polite"></div>
        <div class="mm-grid" data-grid></div>
        <p class="center intr-msg" data-msg aria-live="polite"></p>`;
    }
    function hud() {
      P.$("[data-pts]", body).textContent = points;
      P.$("[data-lives]", body).innerHTML = [0, 1, 2].map((i) => `<i class="${i < lives ? "" : "lost"}">❤️</i>`).join("");
    }
    const fs = (w) => (w.length > 14 ? "1rem" : w.length > 10 ? "1.15rem" : w.length > 8 ? "1.3rem" : "");
    function next() {
      round = makeRound(D); state = "play";
      secs = Math.max(2.5, 6.0 - 0.15 * points) * (round.type === "sem" || round.type === "naomandou" ? 0.7 : 1);
      hud();
      P.$("[data-order]", body).innerHTML = `<span>${round.text}</span>`;
      const msg = P.$("[data-msg]", body); msg.textContent = ""; msg.className = "center intr-msg";
      const grid = P.$("[data-grid]", body); grid.innerHTML = "";
      round.words.forEach((w, i) => {
        const b = P.h(`<button class="mm-word" data-i="${i}"${fs(w.d) ? ` style="font-size:${fs(w.d)}"` : ""}>${P.esc(w.d)}</button>`);
        b.onclick = () => tap(i);
        grid.appendChild(b);
      });
      t0 = performance.now(); cancelAnimationFrame(raf); raf = requestAnimationFrame(tick);
    }
    function tick() {
      if (!document.body.contains(body) || state !== "play") return;
      const left = 1 - (performance.now() - t0) / (secs * 1000);
      const bar = P.$(".timer i", body);
      if (bar) { bar.style.width = Math.max(0, left * 100) + "%"; bar.style.background = left < 0.3 ? "var(--red)" : "var(--accent)"; }
      if (left <= 0) return timeUp();
      raf = requestAnimationFrame(tick);
    }
    function tap(i) {
      if (state !== "play") return;
      const w = round.words[i], t = round.type;
      if (t === "sem" || t === "naomandou") return finish(false, "Pegadinha! O mestre não mandou.", i);
      if (t === "normal") return w.ok ? finish(true, "Isso!", i) : finish(false, "Não era essa.", i);
      if (t === "nao") return w.ok ? finish(false, "O mestre mandou NÃO tocar nessa!", i) : finish(true, "Isso!", i);
      // TODAS: cada toque certo fica verde; precisa das duas.
      if (round.tapped.includes(i)) return;
      if (!w.ok) return finish(false, "Essa não cumpre a ordem.", i);
      round.tapped.push(i); P.fx.type();
      P.$(`[data-i="${i}"]`, body).classList.add("right");
      if (round.tapped.length === round.words.filter((x) => x.ok).length) finish(true, "Isso! Todas certas.", i);
    }
    function timeUp() {
      if (round.type === "sem" || round.type === "naomandou") finish(true, "Boa! O mestre não mandou.", -1);
      else finish(false, "O tempo acabou!", -1);
    }
    function finish(ok, text, i) {
      state = "result"; cancelAnimationFrame(raf);
      const t = round.type, trap = t === "sem" || t === "naomandou";
      body.querySelectorAll(".mm-word").forEach((b, k) => {
        const w = round.words[k];
        if (k === i) b.classList.add(ok ? "right" : "wrong");
        else if (!trap && t !== "nao" && w.ok) b.classList.add("right");
        else if (!b.classList.contains("right")) b.classList.add("dim");
      });
      const msg = P.$("[data-msg]", body); msg.textContent = text; msg.classList.add(ok ? "good" : "bad");
      if (ok) { points++; P.store.add("mestre-right"); P.fx.reveal(points % 5, "c"); }
      else { lives--; P.fx.invalid(); }
      hud();
      clearTimeout(timer);
      timer = setTimeout(() => { if (!document.body.contains(body)) return; if (lives <= 0) end(); else next(); }, 1100);
    }
    function end() {
      state = "over";
      P.store.add("mestre-played"); P.store.max("mestre-best", points); P.logActivity();
      if (points > 0 && points >= P.store.get("mestre-best", 0)) P.fx.win(); else P.fx.lose();
      const pts = `${points} ${points === 1 ? "ponto" : "pontos"}`;
      const ui = P.sheet(`<h2>Fim de jogo</h2><p class="subtitle">Você fez ${pts}</p>
        ${P.statsHTML([[points, "Pontos"], [P.store.get("mestre-best", 0), "Recorde"], [P.store.get("mestre-right", 0), "Acertos totais"]], "three")}
        <div class="row-btns"><button class="pill ghost" data-share>Compartilhar</button><button class="pill" data-again>Jogar de novo</button></div>`, () => { if (state === "over") start(); });
      P.$("[data-share]", ui.el).onclick = () => P.share(`Palavreiro · Mestre Mandou\nFiz ${pts} 👑`);
      P.$("[data-again]", ui.el).onclick = () => { state = "again"; ui.close(); points = 0; lives = 3; layout(); next(); };
    }
    // Para testes automatizados: estado da rodada atual.
    P.games.mestre.debug = { round: () => round, state: () => state, points: () => points, lives: () => lives, make: () => makeRound(D), tap: (i) => tap(i), secs: () => secs };
    start();
  };
})();
