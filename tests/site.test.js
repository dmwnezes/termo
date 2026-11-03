// Testes do site. Uso: node --test tests/site.test.js (precisa de "playwright" instalado).
const test = require("node:test"), assert = require("node:assert"), fs = require("fs"), path = require("path"), http = require("http");
const { chromium } = require("playwright");
const fakeNtfy = require("./fake-ntfy");
const ROOT = path.join(__dirname, "..");
const TYPES = { ".html": "text/html", ".js": "text/javascript", ".css": "text/css", ".txt": "text/plain", ".png": "image/png", ".jpg": "image/jpeg", ".webmanifest": "application/manifest+json" };
const site = http.createServer((req, res) => {
  const p = path.join(ROOT, decodeURIComponent(req.url.split("?")[0].replace(/\/$/, "/index.html")));
  if (!p.startsWith(ROOT) || !fs.existsSync(p)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { "Content-Type": TYPES[path.extname(p)] || "application/octet-stream" }); fs.createReadStream(p).pipe(res);
});
const ntfyA = fakeNtfy(), ntfyB = fakeNtfy();
let browser, base;
const listen = (s) => new Promise((r) => s.listen(0, "127.0.0.1", () => r(`http://127.0.0.1:${s.address().port}/`)));
let urlA, urlB;

test.before(async () => { base = await listen(site); urlA = await listen(ntfyA.server); urlB = await listen(ntfyB.server); browser = await chromium.launch(); });
test.after(async () => { await browser.close(); site.closeAllConnections(); site.close(); [ntfyA, ntfyB].forEach((n) => { n.server.closeAllConnections(); n.server.close(); }); });

/** Abre o site sem abertura e sem "Como jogar"; falha em qualquer erro de JavaScript. */
async function open(hash = "", storage = {}, ctxOpts = {}) {
  const ctx = await browser.newContext(Object.assign({ serviceWorkers: "block", viewport: { width: 390, height: 844 } }, ctxOpts));
  await ctx.addInitScript((s) => { localStorage.setItem("pv-seen-help", "1"); sessionStorage.setItem("pv-splash", "1"); Object.entries(s).forEach(([k, v]) => localStorage.setItem(k, v)); }, storage);
  const page = await ctx.newPage(), errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  await page.goto(base + hash); await page.waitForFunction(() => window.P && P.games);
  page.errors = errors;
  return page;
}

test("service worker guarda todos os arquivos do site", () => {
  const walk = (d) => fs.readdirSync(path.join(ROOT, d), { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(path.join(d, e.name)) : [path.join(d, e.name)]));
  const disk = ["js", "shared", "img"].flatMap(walk).concat("index.html", "style.css", "manifest.webmanifest").sort();
  const sw = fs.readFileSync(path.join(ROOT, "sw.js"), "utf8"), listed = [...sw.slice(sw.indexOf("FILES"), sw.indexOf("];")).matchAll(/"([^"]+)"/g)].map((m) => m[1]).filter((f) => f !== "./").sort();
  assert.deepStrictEqual(listed, disk);
});

test("index.html carrega todo js/ e nenhum arquivo tem versão manual", () => {
  const html = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
  assert.ok(!/\?v=/.test(html));
  fs.readdirSync(path.join(ROOT, "js")).filter((f) => f.endsWith(".js")).forEach((f) => assert.ok(html.includes(`js/${f}`), f));
});

test("palavras: tentativas limpas, respostas continuam aceitas", async () => {
  const p = await open();
  const r = await p.evaluate(() => ({ en: ["ABOUT", "AFTER", "AARON", "ABDUL", "AEGON", "WORLD"].filter((w) => P.words.ok(w)), pt: ["CANTA", "FALEI", "DUNAS", "ACOES", "ROSAS"].filter((w) => !P.words.ok(w)), ans: P.words.answers.every((w) => P.words.ok(w)) }));
  assert.deepStrictEqual(r, { en: [], pt: [], ans: true });
  await p.context().close();
});

test("palavra do dia: passado não muda e o futuro não repete em 1000 dias", async () => {
  const p = await open();
  const r = await p.evaluate(() => {
    const at = (i) => P.daily(new Date(2026, 0, 1 + i));
    const seen = {}; let reps = 0;
    for (let i = 0; i < 1600; i++) { const w = at(i); if (i >= 306 && seen[w] !== undefined && i - seen[w] < 1000) reps++; seen[w] = i; }
    return { d0: at(0), d190: at(190), d305: at(305), d284: at(284), d306: at(306), d1000: at(1000), reps };
  });
  assert.strictEqual(r.reps, 0);
  assert.strictEqual(r.d190, "GASTO"); assert.strictEqual(r.d284, "GASTO"); // já foram ao ar
  console.log("referência para o app:", JSON.stringify(r));
  await p.context().close();
});

test("conteúdo diário: passado não muda e o novo entra de 03/11 em diante", async () => {
  const p = await open();
  const r = await p.evaluate(async () => {
    const pod = P.podioParse(await P.text("podio.txt"));
    const conn = (await P.text("conexoes.txt")).split(/\n\s*\n/).map((x) => x.split("\n").filter((l) => l && !l.startsWith("#"))).filter((x) => x.length);
    if (conn.length !== 98 || conn.some((x) => x.length !== 4)) throw new Error("Conexões: " + conn.length);
    return { nPod: pod.length, old: P.podioDaily(pod, 305).idx, oldExpected: (305 * 7919 + 104729) % 155, cz: P.cruzadaParse(await P.text("cruzada.txt")).length, cnt: [P.dailyCount(305, 98, 46), P.dailyCount(306, 98, 46)] };
  });
  assert.strictEqual(r.old, r.oldExpected); assert.deepStrictEqual(r.cnt, [46, 98]); assert.strictEqual(r.cz, 366);
  await p.context().close();
});

test("todos os jogos da tela inicial abrem sem erro", async () => {
  const p = await open();
  const ks = await p.evaluate(() => [...document.querySelectorAll(".hm-card")].map((b) => b.dataset.k));
  assert.ok(ks.includes("cruzada") && ks.length >= 19, ks.join());
  for (const k of ks.concat("perfil", "arquivo")) {
    await p.evaluate((h) => { location.hash = "#/" + h; }, k);
    await p.waitForTimeout(250);
  }
  assert.deepStrictEqual(p.errors, []);
  await p.context().close();
});

test("Palavra Cruzada: conferir, travar, vencer e contar no perfil", async () => {
  const p = await open("#/cruzada");
  await p.waitForSelector(".cz-grid");
  const sol = await p.evaluate(() => P.cruzadaState().sol);
  assert.strictEqual(sol.flat().filter(Boolean).length, 21);
  // Letra errada na 1ª casa, depois tudo certo pelas pistas horizontais e verticais.
  await p.keyboard.press("Q".toLowerCase() === sol[0][0].toLowerCase() ? "z" : "q");
  await p.keyboard.press("Enter");
  assert.ok(await p.locator(".cz-c.bad").count() === 1);
  for (let i = 0; i < 6; i++) {
    await p.locator(`[data-clue="${i}"]`).click();
    const st = await p.evaluate((n) => { const s = P.cruzadaState(); return n < 3 ? s.sol[n * 2].join("") : s.sol.map((l) => l[(n - 3) * 2]).join(""); }, i);
    for (let k = 0; k < 5; k++) {
      const [r, c] = i < 3 ? [i * 2, k] : [k, (i - 3) * 2];
      await p.locator(`[data-c="${r},${c}"]`).evaluate((b) => b.click());
      const across = await p.evaluate(() => P.cruzadaState().across);
      if (across !== i < 3) await p.locator(`[data-c="${r},${c}"]`).evaluate((b) => b.click());
      await p.keyboard.press(st[k].toLowerCase());
    }
  }
  await p.keyboard.press("Enter");
  assert.ok(await p.evaluate(() => P.cruzadaState().won));
  assert.ok((await p.locator(".cz-c.ok").count()) === 21);
  assert.strictEqual(await p.evaluate(() => P.store.get("cruz-won")), 1);
  assert.strictEqual(await p.evaluate(() => P.store.get("daily-done-cruzada")), await p.evaluate(() => P.dayKey()));
  assert.deepStrictEqual(p.errors, []);
  await p.context().close();
});

test("Batalha Naval entra no perfil, nas conquistas e no código de sincronização", async () => {
  const p = await open("", { "pv-naval-won-dificil": "2" });
  const r = await p.evaluate(() => {
    const code = P.syncExport(), d = JSON.parse(atob(code.slice(4).replace(/-/g, "+").replace(/_/g, "/")));
    return { n: d.n.naval_won_dificil, ach: P.achievements().find((a) => a.title === "Almirante").ok };
  });
  assert.deepStrictEqual(r, { n: 2, ach: true });
  await p.evaluate(() => { location.hash = "#/perfil"; });
  await p.waitForSelector(".recc");
  assert.ok((await p.locator(".recc", { hasText: "Batalha Naval" }).count()) === 1);
  await p.context().close();
});

test("sincronização com código curto leva o progresso em partes", async () => {
  const relay = { "pv-ntfy": urlA, "pv-ntfy2": urlB };
  const a = await open("", relay), b = await open("", relay);
  await a.evaluate(() => { P.store.set("mp-won", 7); const act = {}; for (let i = 0; i < 700; i++) act[P.dayKey(new Date(2024, 0, 1 + i))] = 1; P.store.set("activity", act); });
  const code = await a.evaluate(() => P.syncSend());
  assert.match(code, /^[2-9A-Z]{6}$/);
  assert.ok(ntfyA.topics["palavreiro-sync-" + code.toLowerCase()].length > 1, "não foi em partes");
  assert.strictEqual(await b.evaluate((c) => P.syncReceive(c), code), true);
  assert.strictEqual(await b.evaluate(() => P.store.get("mp-won")), 7);
  assert.strictEqual(await b.evaluate((c) => P.syncReceive(c), "ZZZZZZ"), false);
  assert.strictEqual(await b.evaluate((c) => P.syncShort("https://dmwnezes.com/termo/?sync=" + c), code), code);
  await a.context().close(); await b.context().close();
});

test("retrospectiva soma só o ano e abre a janela", async () => {
  const p = await open();
  const r = await p.evaluate(() => {
    P.store.set("activity", { "2026-03-01": 2, "2026-03-02": 1, "2026-03-03": 4, "2026-07-10": 1, "2025-03-04": 9 });
    P.store.set("termo-days", { "2026-03-01": "w", "2026-03-02": "l", "2025-01-01": "w" });
    P.store.set("results", { "termo|2026-03-01": "w", "termo|2026-03-02": "l", "conn|2026-03-01": "w" });
    P.store.set("first-guesses", [{ w: "AUDIO", n: 3, d: "2026-03-01" }, { w: "AUDIO", n: 2, d: "2026-03-02" }, { w: "PEDRA", n: 2, d: "2025-03-02" }]);
    P.retroSheet();
    return P.retro(2026);
  });
  assert.deepStrictEqual(r, { year: 2026, days: 4, games: 8, bestRun: 3, month: "março", termoPlayed: 2, termoWon: 1, topGame: "Termo", topGuess: "AUDIO" });
  assert.ok(await p.locator(".retro-row").count() >= 5);
  await p.context().close();
});

test("partida com amigo continua quando o servidor principal cai", async () => {
  const relay = { "pv-ntfy": urlA, "pv-ntfy2": urlB };
  const a = await open("", relay), b = await open("", relay);
  // Transporte puro: mesma lista de servidores e mensagens repetidas ignoradas.
  const topic = "palavreiro-mp-teste" + Date.now();
  const pub = (m) => a.evaluate(([t, m]) => P.relay.post(t, JSON.stringify(m)), [topic, m]);
  assert.strictEqual(await pub({ t: "x", _u: "um" }), true);
  ntfyA.setDown(true);
  assert.strictEqual(await pub({ t: "x", _u: "dois" }), true);
  assert.deepStrictEqual((await b.evaluate((t) => P.relay.history(t), topic)).map((m) => JSON.parse(m)._u), ["um", "dois"]);
  ntfyA.setDown(false);
  await a.context().close(); await b.context().close();
});

test("offline: depois da primeira visita o site abre e os jogos solo carregam", async () => {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 } });
  await ctx.addInitScript(() => { localStorage.setItem("pv-seen-help", "1"); });
  const p = await ctx.newPage();
  await p.goto(base);
  await p.evaluate(() => navigator.serviceWorker.ready);
  await p.waitForFunction(() => navigator.serviceWorker.controller, null, { timeout: 15000 }).catch(() => p.reload());
  await p.waitForFunction(() => navigator.serviceWorker.controller);
  await ctx.setOffline(true);
  await p.goto(base + "#/cruzada");
  await p.reload();
  await p.waitForSelector(".cz-grid", { timeout: 8000 });
  assert.ok((await p.locator(".cz-c").count()) === 21);
  await ctx.close();
});

test("na partida real, o convidado passa a ouvir o servidor reserva quando o principal cai", async () => {
  const relay = { "pv-ntfy": urlA, "pv-ntfy2": urlB };
  const h = await open("#/amigo", relay);
  await h.fill("[data-name]", "Ana");
  await h.click("[data-create]");
  await h.waitForSelector("[data-link]");
  const link = (await h.textContent("[data-link]")).trim();
  const g = await open(link.slice(link.indexOf("?")), relay);
  await g.waitForSelector("[data-name]");
  await g.fill("[data-name]", "Bia");
  await g.locator("button.pill", { hasText: /Entrar/ }).first().click();
  await h.waitForFunction(() => P.mp.current() && P.mp.current().started, null, { timeout: 15000 });
  await g.waitForFunction(() => P.mp.current() && P.mp.current().started, null, { timeout: 15000 });
  ntfyA.setDown(true);
  await g.waitForFunction(() => P.mp.current().srv === 1, null, { timeout: 15000 });
  await h.evaluate(() => P.mp._.publish({ t: "react", id: P.mp.current().me.id, e: "👍", _u: "depois-da-queda" }));
  await g.waitForFunction(() => P.mp.current().seen.has("depois-da-queda"), null, { timeout: 15000 });
  ntfyA.setDown(false);
  assert.deepStrictEqual(h.errors.concat(g.errors), []);
  await h.context().close(); await g.context().close();
});

test("Silhueta: dados válidos, dica correta, derrota e vitória", async () => {
  const p = await open("#/silhueta");
  await p.waitForSelector(".sil-map");
  const data = await p.evaluate(async () => {
    const l = P.silhuetaParse(await P.text("silhueta.txt")), by = (n) => l.find((x) => x.name === n);
    return { n: l.length, names: new Set(l.map((x) => x.name)).size, ok: l.every((x) => /^M[\d.]/.test(x.d) && x.d.endsWith("Z") && Math.abs(x.lat) <= 90 && Math.abs(x.lon) <= 180), e: l.filter((x) => x.kind === "e").length,
      hint: P.silhuetaHint(by("Ceará"), by("São Paulo")), far: P.silhuetaHint(by("Brasil"), by("Japão")), norte: P.silhuetaHint(by("Paraná"), by("Ceará")) };
  });
  assert.strictEqual(data.n, data.names); assert.strictEqual(data.e, 27); assert.ok(data.ok);
  assert.ok(data.hint.km > 1800 && data.hint.km < 2600, "Ceará-SP " + data.hint.km);
  assert.ok(data.far.km > 15000, "Brasil-Japão " + data.far.km);
  assert.ok([0, 1].includes(data.norte.dir), "Ceará fica a nordeste do Paraná: " + data.norte.dir);
  const st = await p.evaluate(() => P.silhuetaState());
  await p.fill("[data-input]", st.name.slice(0, 4));
  await p.locator(".sil-opt", { hasText: st.name }).first().click();
  assert.ok((await p.evaluate(() => P.silhuetaState())).won);
  assert.strictEqual(await p.evaluate(() => P.store.get("sil-first")), 1);
  assert.deepStrictEqual(p.errors, []);
  await p.context().close();
});

test("Mais ou Menos: rodadas justas, dia igual e fim com 10", async () => {
  const p = await open("#/maismenos");
  await p.waitForSelector(".mm-card");
  const r = await p.evaluate(async () => {
    const cats = P.mmParse(await P.text("maismenos.txt")), bad = [];
    for (let d = 0; d < 800; d++) P.mmDaily(cats, d).forEach((x) => {
      const c = cats[x.c], a = c.items[x.a].v, b = c.items[x.b].v;
      if (a === b || (c.year ? Math.abs(a - b) < 3 : Math.abs(a - b) < 0.1 * Math.max(Math.abs(a), Math.abs(b)))) bad.push(c.title);
    });
    const d306 = P.mmDaily(cats, 306);
    return { cats: cats.length, bad, same: JSON.stringify(d306) === JSON.stringify(P.mmDaily(cats, 306)), d306: d306.map((x) => `${cats[x.c].items[x.a].name}/${cats[x.c].items[x.b].name}`), distinct: new Set(d306.map((x) => x.c)).size };
  });
  assert.deepStrictEqual(r.bad, []); assert.ok(r.same); assert.strictEqual(r.distinct, 10); assert.ok(r.cats >= 20);
  console.log("referência para o app:", JSON.stringify(r.d306));
  for (let i = 0; i < 10; i++) {
    await p.locator("[data-more]").click();
    await p.locator("[data-next]").click();
  }
  await p.waitForSelector(".mm-end");
  assert.strictEqual(await p.evaluate(() => P.store.get("mm-played")), 1);
  assert.strictEqual(await p.evaluate(() => P.store.get("daily-done-maismenos")), await p.evaluate(() => P.dayKey()));
  assert.deepStrictEqual(p.errors, []);
  await p.context().close();
});

test("Campo Minado: mesma grade do dia, começa aberto, vence e perde", async () => {
  const p = await open("#/minado");
  await p.waitForSelector(".mina-c");
  const r = await p.evaluate(() => {
    const out = [];
    [0, 1, 2].forEach((lv) => { for (let s = 0; s < 300; s++) { const g = P.minado.create(lv, s); if (g.mine.filter(Boolean).length !== P.minado.LEVELS[lv][4] || !g.open[g.start] || g.mine[g.start] || g.lost) out.push([lv, s]); } });
    const a = P.minado.create(0, 921), ref = a.mine.map((m, i) => (m ? i : -1)).filter((i) => i >= 0);
    const w = P.minado.create(0, 7); w.mine.forEach((m, c) => { if (!m) P.minado.reveal(w, c); });
    const l = P.minado.create(0, 7); P.minado.reveal(l, l.mine.indexOf(true));
    const f = P.minado.create(0, 7), c = f.mine.indexOf(true); P.minado.toggleFlag(f, c); P.minado.reveal(f, c);
    return { bad: out, ref, start: a.start, won: w.won, lost: l.lost, flagSafe: !f.lost };
  });
  assert.deepStrictEqual(r.bad, []); assert.ok(r.won && r.lost && r.flagSafe);
  console.log("referência minado 921:", JSON.stringify(r.ref), r.start);
  // Pela tela: abre tudo o que não é mina.
  const cells = await p.evaluate(() => { const g = P.minadoState(); return g.mine.map((m, c) => (!m && !g.open[c] ? c : -1)).filter((c) => c >= 0); });
  for (const c of cells) if (await p.evaluate((x) => !P.minadoState().open[x], c)) await p.locator(`[data-c="${c}"]`).click();
  assert.ok(await p.evaluate(() => P.minadoState().won));
  assert.strictEqual(await p.evaluate(() => P.store.get("mina-won")), 1);
  assert.ok((await p.evaluate(() => P.store.get("mina-best-facil"))) >= 0);
  assert.deepStrictEqual(p.errors, []);
  await p.context().close();
});

test("Caminhos: toda grade tem solução e o arrasto funciona", async () => {
  const p = await open("#/caminhos");
  await p.waitForSelector(".cam-cell");
  const r = await p.evaluate(async () => {
    const list = P.caminhosParse(await P.text("caminhos.txt")), bad = [];
    list.forEach((q, i) => { const all = q.pairs.flat(); if (new Set(all).size !== all.length || all.some((c) => c < 0 || c >= q.n * q.n)) bad.push(i); });
    const g = P.caminhos.create({ n: 3, pairs: [[0, 8], [1, 5]] });
    P.caminhos.start(g, 0); P.caminhos.dragTo(g, 3); P.caminhos.dragTo(g, 6); P.caminhos.dragTo(g, 8);
    const blockedByOtherEnd = !P.caminhos.step(Object.assign(P.caminhos.create({ n: 3, pairs: [[0, 8], [1, 5]] }), { active: 0, paths: [[0], []] }), 1);
    P.caminhos.start(g, 1); P.caminhos.dragTo(g, 2); P.caminhos.dragTo(g, 5);
    const s = { n: list.length, bad, conn: P.caminhos.connected(g, 0) && P.caminhos.connected(g, 1), won: P.caminhos.won(g), blockedByOtherEnd };
    const cut = P.caminhos.create({ n: 3, pairs: [[0, 2], [6, 8]] });
    P.caminhos.start(cut, 0); P.caminhos.dragTo(cut, 3); P.caminhos.dragTo(cut, 4);
    P.caminhos.start(cut, 6); P.caminhos.dragTo(cut, 7); P.caminhos.dragTo(cut, 4);
    s.cutOther = cut.paths[0].join() === "0,3" && cut.paths[1].join() === "6,7,4";
    s.d306 = P.caminhosDaily(list, 1, 306);
    return s;
  });
  assert.strictEqual(r.n, 450); assert.deepStrictEqual(r.bad, []); assert.ok(r.conn && r.blockedByOtherEnd && r.cutOther);
  assert.strictEqual(r.won, false, "faltam casas: a grade não está cheia");
  console.log("referência caminhos médio 306:", JSON.stringify(r.d306));
  // Arrasto real com o ponteiro, depois da abertura.
  await p.waitForFunction(() => { const s = document.querySelector("#splash"); return !s || getComputedStyle(s).display === "none" || !s.isConnected; }, null, { timeout: 15000 });
  const g = await p.evaluate(() => { const g = P.caminhosState(); return { n: g.q.n, a: g.q.pairs[0][0] }; });
  const box = await p.locator(".cam-board").boundingBox(), at = (c) => [box.x + ((c % g.n) + 0.5) * (box.width / g.n), box.y + (Math.floor(c / g.n) + 0.5) * (box.height / g.n)];
  const nb = g.a % g.n < g.n - 1 ? g.a + 1 : g.a - 1;
  await p.mouse.move(...at(g.a)); await p.mouse.down(); await p.mouse.move(...at(nb), { steps: 3 }); await p.mouse.up();
  assert.ok((await p.evaluate(() => P.caminhosState().paths.reduce((a, x) => a + x.length, 0))) >= 1);
  assert.deepStrictEqual(p.errors, []);
  await p.context().close();
});

test("Fato ou Mito: dados, dia igual e fim com 10", async () => {
  const p = await open("#/fatomito");
  await p.waitForSelector(".fm-card");
  const r = await p.evaluate(async () => { const l = P.fmParse(await P.text("fatomito.txt")); return { n: l.length, f: l.filter((x) => x.fact).length, ok: l.every((x) => x.text && x.why), d: P.fmDaily(l, 306) }; });
  assert.strictEqual(r.n, 100); assert.strictEqual(r.f, 50); assert.ok(r.ok); assert.strictEqual(new Set(r.d).size, 10);
  console.log("referência fato 306:", JSON.stringify(r.d));
  for (let i = 0; i < 10; i++) { await p.locator("[data-fact]").click(); await p.locator("[data-next]").click(); }
  await p.waitForSelector(".mm-end");
  assert.strictEqual(await p.evaluate(() => P.store.get("fm-played")), 1);
  assert.deepStrictEqual(p.errors, []);
  await p.context().close();
});
