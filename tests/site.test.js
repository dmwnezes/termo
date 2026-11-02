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
