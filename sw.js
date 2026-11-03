// Site offline: guarda os arquivos na primeira visita. Com internet, sempre busca a versão nova (por isso os arquivos não têm ?v=).
const CACHE = "palavreiro-1";
const FILES = [
  "./", "index.html", "style.css", "manifest.webmanifest",
  "img/criador.jpg", "img/icon-192.png", "img/icon-512.png", "img/og.png",
  "js/ampulheta.js", "js/empilha.js", "js/placas.js", "js/respostas.js", "js/caminhos.js", "js/fatomito.js", "js/minado.js", "js/maismenos.js", "js/silhueta.js", "js/app.js", "js/arquivo.js", "js/caca.js", "js/conexoes.js", "js/core.js", "js/cruzada.js", "js/damas.js", "js/extras.js", "js/fun.js", "js/intruso.js", "js/matiz.js",
  "js/memoria.js", "js/mestre.js", "js/multi-adedonha.js", "js/multi-forca.js", "js/multi-games.js", "js/multi-naval.js", "js/multi-trivia.js", "js/multi-xadrez.js", "js/multi.js", "js/naval.js",
  "js/ortografia.js", "js/perfil.js", "js/podio.js", "js/quizzes.js", "js/sudoku.js", "js/termo.js", "js/trivia.js", "js/vendor/qrcode.js", "js/xadrez-solo.js", "js/xadrez.js",
  "shared/adedonha.txt", "shared/ampulheta.txt", "shared/antonimos.txt", "shared/caca-extra.txt", "shared/caca.txt", "shared/conexoes-familias.txt", "shared/conexoes.txt", "shared/cruzada.txt",
  "shared/definicoes.txt", "shared/ortografia.txt", "shared/caminhos.txt", "shared/fatomito.txt", "shared/maismenos.txt", "shared/pesquisa.txt", "shared/placas.txt", "shared/top10.txt", "shared/podio.txt", "shared/silhueta.txt", "shared/significados.txt", "shared/sinonimos.txt", "shared/sudoku.txt", "shared/trivia.txt", "shared/words.js",
];
const FONTS = ["https://fonts.googleapis.com", "https://fonts.gstatic.com"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(FILES.map((f) => new Request(f, { cache: "reload" })))).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((l) => Promise.all(l.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", (e) => {
  const req = e.request, url = new URL(req.url), same = url.origin === location.origin;
  if (req.method !== "GET" || (!same && !FONTS.includes(url.origin))) return;
  // Link com parâmetros (?mp=, ?sync=) abre a mesma página guardada.
  const key = same ? url.origin + url.pathname : req.url;
  e.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const net = fetch(same ? new Request(req.url, { cache: "no-cache" }) : req).then((r) => { if (r.ok || r.type === "opaque") cache.put(key, r.clone()); return r; });
    const old = await cache.match(key);
    if (!old) return net.catch(() => (req.mode === "navigate" ? cache.match("./") : Response.error()));
    // Internet lenta: não deixa a tela esperando mais que 4 s.
    return Promise.race([net, new Promise((res) => setTimeout(() => res(old), 4000))]).catch(() => old);
  })());
});
