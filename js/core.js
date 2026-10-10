// Palavreiro (site) — utilidades compartilhadas por todos os jogos.
// As regras de "palavra do dia" são as mesmas do app Android, então os dois mostram a mesma palavra.
(function () {
  "use strict";
  const P = (window.P = { games: {} });
  const V = "18"; // versão dos arquivos de conteúdo

  P.$ = (sel, root = document) => root.querySelector(sel);
  P.h = (html) => { const t = document.createElement("template"); t.innerHTML = html.trim(); return t.content.firstElementChild; };
  P.esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  P.norm = (t) => t.normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase();
  P.sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  // ---------- palavras ----------
  P.words = (() => {
    const acc = {};
    const list = [];
    const add = (w) => {
      const n = P.norm(w);
      if (n.length === 5 && /^[A-Z]+$/.test(n) && !acc[n]) { acc[n] = w.toUpperCase(); list.push(n); }
    };
    ANSWERS.forEach(add);
    const base = list.length; // palavras do sorteio antes de ANSWERS2_FROM
    (typeof ANSWERS2 !== "undefined" ? ANSWERS2 : []).forEach(add);
    const accepted = new Set([...list, ...VALID.map(P.norm)]);
    const from = typeof ANSWERS2_FROM !== "undefined" ? ANSWERS2_FROM : 0;
    return { answers: list, base, from, display: (w) => acc[w] || w, ok: (w) => accepted.has(w), all: accepted };
  })();

  // ---------- datas ----------
  P.dayIndex = (d = new Date()) => Math.floor((Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) - Date.UTC(2026, 0, 1)) / 86400000);
  P.dayKey = (d = new Date()) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  const mod = (a, n) => ((a % n) + n) % n;
  /** Quantas palavras entram no sorteio do dia nessa data (as novas só a partir de ANSWERS2_FROM). */
  const poolSize = (i) => (i >= P.words.from ? P.words.answers.length : P.words.base);
  P.daily = (d = new Date()) => { const i = P.dayIndex(d); return P.words.answers[mod(i * 7919 + 104729, poolSize(i))]; };
  /** Palavras do dia para Dueto (salt 1) e Quarteto (salt 2). */
  P.dailySet = (d, count, salt) => {
    if (count === 1 && salt === 0) return [P.daily(d)];
    const i = P.dayIndex(d), n = poolSize(i), out = [];
    for (let k = 0; out.length < count; k++) {
      const w = P.words.answers[mod(i * 7919 + 104729 + salt * 3331 + k * 577, n)];
      if (!out.includes(w)) out.push(w);
    }
    return out;
  };

  /** Palavras de uma partida com amigo (mesma conta do app): lista completa, sem repetir. */
  P.mpWords = (seed, count) => {
    const list = P.words.answers, n = list.length, out = [];
    for (let k = 0; out.length < count && k < n * 4; k++) {
      const w = list[((seed % n) * 7919 % n + 104729 + k * 577) % n];
      if (!out.includes(w)) out.push(w);
    }
    return out;
  };

  // Gerador de números com semente (mesma grade para todos no mesmo dia).
  P.rng = (seed) => {
    let a = seed >>> 0;
    return () => { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  };
  P.shuffle = (arr, rnd = Math.random) => { const a = arr.slice(); for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; } return a; };
  P.randomWord = (avoid) => { let w; do { w = P.words.answers[Math.floor(Math.random() * P.words.answers.length)]; } while (avoid && avoid.includes(w)); return w; };

  // ---------- regras do Termo ----------
  /** "c" = lugar certo, "p" = em outro lugar, "a" = não está. */
  P.evaluate = (guess, answer) => {
    const r = Array(guess.length).fill("a"), left = {};
    for (let i = 0; i < guess.length; i++) { if (guess[i] === answer[i]) r[i] = "c"; else left[answer[i]] = (left[answer[i]] || 0) + 1; }
    for (let i = 0; i < guess.length; i++) { if (r[i] !== "c" && left[guess[i]] > 0) { r[i] = "p"; left[guess[i]]--; } }
    return r;
  };
  P.keyMarks = (rows, answer) => {
    const rank = { a: 1, p: 2, c: 3 }, best = {};
    rows.forEach((w) => P.evaluate(w, answer).forEach((m, i) => { if (!best[w[i]] || rank[m] > rank[best[w[i]]]) best[w[i]] = m; }));
    return best;
  };

  // ---------- desafio por link (mesmo cálculo do app) ----------
  P.challenge = {
    encode: (word) => [...P.norm(word)].map((c, i) => String.fromCharCode(97 + mod(c.charCodeAt(0) - 65 + 7 * i + 3, 26))).join("").split("").reverse().join(""),
    decode: (code) => {
      const s = String(code || "").trim().toLowerCase().split("").reverse();
      if (s.length !== 5 || s.some((c) => c < "a" || c > "z")) return null;
      return s.map((c, i) => String.fromCharCode(65 + mod(c.charCodeAt(0) - 97 - 7 * i - 3, 26))).join("");
    },
    link: (word) => location.origin + location.pathname.replace(/index\.html$/, "") + "?d=" + P.challenge.encode(word),
  };

  // ---------- dados guardados no navegador ----------
  P.store = {
    get(k, f) { try { const v = localStorage.getItem("pv-" + k); return v == null ? f : JSON.parse(v); } catch (_) { return f; } },
    set(k, v) { try { localStorage.setItem("pv-" + k, JSON.stringify(v)); } catch (_) {} },
    add(k, d = 1) { this.set(k, (this.get(k, 0) || 0) + d); },
    max(k, v) { if (v > (this.get(k, 0) || 0)) this.set(k, v); },
    min(k, v) { const c = this.get(k, 0) || 0; if (!c || v < c) this.set(k, v); },
  };
  P.defaultStats = () => ({ played: 0, won: 0, streak: 0, maxStreak: 0, lastWinDay: -999999, firstTry: 0, dist: [0, 0, 0, 0, 0, 0, 0, 0, 0] });
  P.stats = (mode) => Object.assign(P.defaultStats(), P.store.get("stats-" + mode, {}));
  P.logActivity = (day = P.dayKey()) => { const a = P.store.get("activity", {}); a[day] = (a[day] || 0) + 1; P.store.set("activity", a); };
  /** Resultado de um desafio do dia ou do arquivo: results["jogo|AAAA-MM-DD"] = "w" | "l". */
  P.setResult = (game, day, won) => { const r = P.store.get("results", {}); r[game + "|" + day] = won ? "w" : "l"; P.store.set("results", r); };
  /** Histórico só dos desafios do dia: history[dia][campo] = n (termo, conn). */
  P.setHistory = (day, field, n) => { const h = P.store.get("history", {}); h[day] = Object.assign(h[day] || {}, { [field]: n }); P.store.set("history", h); };
  /** Partida do arquivo terminada. */
  P.archiveDone = (game, day, won) => { P.setResult(game, day, won); P.store.add("arch-played"); if (won) P.store.add("arch-won"); P.logActivity(); };
  /** 1º chute de cada partida de 1 tabuleiro: [{w, n (7 = errou), d}], no máximo 500 (sai o mais antigo). */
  P.addFirstGuess = (w, n) => {
    const l = P.store.get("first-guesses", []);
    const list = Array.isArray(l) ? l : [];
    list.push({ w, n, d: P.dayKey() });
    P.store.set("first-guesses", list.slice(-500));
  };
  /** "2026-03-12" → Date local; "12/03". */
  P.parseDay = (k) => { const [y, m, d] = String(k).split("-").map(Number); return new Date(y, m - 1, d); };
  P.shortDay = (k) => k.slice(8, 10) + "/" + k.slice(5, 7);

  // ---------- conteúdo (arquivos .txt compartilhados com o app) ----------
  const cache = {};
  P.text = (name) => cache[name] || (cache[name] = fetch(`shared/${name}?v=${V}`).then((r) => r.text()));
  P.meanings = () => P.text("significados.txt").then((t) => {
    const m = {};
    t.split("\n").forEach((l) => { const i = l.indexOf("|"); if (i > 0) m[P.norm(l.slice(0, i).trim())] = l.slice(i + 1).trim(); });
    return m;
  });

  // ---------- sons e vibração ----------
  let ctx = null;
  P.settings = () => Object.assign({ sound: true, vibration: true, hard: false }, P.store.get("settings", {}));
  P.saveSettings = (s) => P.store.set("settings", s);
  P.tone = (notes, vol = 0.12) => {
    if (P.fx.muted || !P.settings().sound) return;
    try {
      ctx = ctx || new (window.AudioContext || window.webkitAudioContext)();
      let t = ctx.currentTime;
      notes.forEach(([f, ms]) => {
        const o = ctx.createOscillator(), g = ctx.createGain();
        o.frequency.value = f; o.type = "sine";
        g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(vol, t + 0.005); g.gain.exponentialRampToValueAtTime(0.0001, t + ms / 1000);
        o.connect(g).connect(ctx.destination); o.start(t); o.stop(t + ms / 1000 + 0.02); t += ms / 1000;
      });
    } catch (_) {}
  };
  P.vibrate = (p) => { if (!P.fx.muted && P.settings().vibration && navigator.vibrate) try { navigator.vibrate(p); } catch (_) {} };
  P.fx = {
    muted: false, // ligado durante o replay: sem som e sem vibração
    type: () => { P.tone([[660, 28]], 0.08); P.vibrate(8); },
    // Verde = 1 toque curto; amarelo = 2 toques curtos; cinza = nada.
    reveal: (i, m) => { P.tone([[({ c: 587, p: 494, a: 330 }[m] || 400) * (1 + i * 0.06), 70]]); if (m === "c") P.vibrate(35); else if (m === "p") P.vibrate([25, 70, 25]); },
    invalid: () => { P.tone([[196, 90], [165, 120]]); P.vibrate([40, 60, 40]); },
    win: () => { P.tone([[523.25, 110], [659.25, 110], [783.99, 110], [1046.5, 260]], 0.14); P.vibrate([30, 70, 30, 70, 90]); },
    lose: () => { P.tone([[392, 160], [311.1, 160], [261.6, 320]]); P.vibrate(180); },
  };

  // ---------- mensagem curta ----------
  let toastTimer;
  P.toast = (msg, ms = 1600) => {
    const el = P.$("#toast");
    el.textContent = msg; el.classList.add("show");
    clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove("show"), ms);
  };

  // ---------- janela que sobe de baixo ----------
  P.sheet = (html, onClose) => {
    const ov = P.h(`<div class="overlay"><div class="sheet"><div class="grab"></div>${html}</div></div>`);
    const close = () => { ov.remove(); document.removeEventListener("keydown", esc); onClose && onClose(); };
    const esc = (e) => { if (e.key === "Escape") close(); };
    ov.addEventListener("click", (e) => { if (e.target === ov) close(); });
    document.addEventListener("keydown", esc);
    document.body.appendChild(ov);
    return { el: ov.firstElementChild, close };
  };

  P.creditHTML = () => `<a class="credit" href="https://www.instagram.com/dmwnezes/" target="_blank" rel="noopener">
      <img src="img/criador.jpg?v=7" alt="Foto de @dmwnezes" width="34" height="34" /><span>criado por: <b>@dmwnezes</b></span></a>`;

  P.topbar = (title, { help, extra = "" } = {}) => `<div class="topbar">
      <button class="icon-btn" data-back aria-label="Voltar">←</button>
      <h1>${title}</h1>${extra}${help ? `<button class="icon-btn" data-help aria-label="Como jogar">?</button>` : ""}</div>`;

  // Seletor "Do dia | Infinito" (Termo, Dueto, Quarteto e Conexões).
  P.modeSwitch = (inf) => `<div class="mode-switch"><button data-mode="0" class="${inf ? "" : "on"}">Do dia</button><button data-mode="1" class="${inf ? "on" : ""}">Infinito</button></div>`;
  P.bindModeSwitch = (root, go) => root.querySelectorAll("[data-mode]").forEach((b) => (b.onclick = () => { if (!b.classList.contains("on")) { P.fx.type(); go(b.dataset.mode === "1"); } }));

  P.statsHTML = (items, cls = "") => `<div class="stats ${cls}">${items.map(([v, l]) => `<div><b>${v}</b><small>${l}</small></div>`).join("")}</div>`;
  P.distHTML = (dist, highlight, rows) => {
    const shown = dist.slice(0, rows), max = Math.max(1, ...shown);
    return `<div class="dist">${shown.map((v, i) => `<div><span class="n">${i + 1}</span><span class="bar${i + 1 === highlight ? " hit" : ""}" style="width:${Math.max(8, (v / max) * 100)}%">${v}</span></div>`).join("")}</div>`;
  };

  // ---------- teclado ----------
  /**
   * Monta o teclado QWERTY. marksFor(letra) devolve as cores por tabuleiro (array de "c"/"p"/"a"/null).
   * Teclado físico também funciona enquanto o teclado está na tela.
   */
  P.keyboard = (root, { onLetter, onEnter, onDelete, enterLabel = "ENTER", marksFor }) => {
    const rows = ["QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"];
    const kb = P.h(`<div class="keyboard"></div>`);
    rows.forEach((letters, i) => {
      const r = P.h(`<div class="krow"></div>`);
      if (i === 1) r.appendChild(P.h(`<span class="spacer-half"></span>`));
      if (i === 2) r.appendChild(P.h(`<button class="key wide" data-k="Enter">${enterLabel}</button>`));
      [...letters].forEach((l) => r.appendChild(P.h(`<button class="key" data-k="${l}"><span>${l}</span></button>`)));
      if (i === 2) r.appendChild(P.h(`<button class="key wide" data-k="Backspace" aria-label="Apagar">⌫</button>`));
      if (i === 1) r.appendChild(P.h(`<span class="spacer-half"></span>`));
      kb.appendChild(r);
    });
    const press = (k) => {
      if (k === "Enter") onEnter(); else if (k === "Backspace" || k === "Delete") onDelete();
      else if (/^[A-Za-zÇç]$/.test(k)) onLetter(P.norm(k));
    };
    kb.addEventListener("click", (e) => { const b = e.target.closest(".key"); if (b) { press(b.dataset.k); b.blur(); } });
    const onKey = (e) => {
      if (!document.body.contains(kb)) return document.removeEventListener("keydown", onKey);
      if (e.ctrlKey || e.metaKey || e.altKey || document.querySelector(".overlay")) return;
      if (e.key === "Enter" || e.key === "Backspace" || e.key === "Delete" || /^[A-Za-zÇç]$/.test(e.key)) { e.preventDefault(); press(e.key); }
      else if (e.key === "ArrowLeft" || e.key === "ArrowRight") root.dispatchEvent(new CustomEvent("arrow", { detail: e.key === "ArrowLeft" ? -1 : 1 }));
    };
    document.addEventListener("keydown", onKey);
    root.appendChild(kb);
    const colors = { c: "var(--correct)", p: "var(--present)", a: "var(--key-absent)" };
    return {
      paint() {
        if (!marksFor) return;
        kb.querySelectorAll(".key[data-k]").forEach((b) => {
          const k = b.dataset.k; if (k.length !== 1) return;
          const m = marksFor(k);
          b.className = "key"; b.querySelector(".kseg")?.remove();
          if (m.length <= 1 || m.every((x) => !x)) { if (m.length === 1 && m[0]) b.classList.add(m[0]); return; }
          // Mesma cor em todos os tabuleiros: pinta a tecla inteira (ex.: letra que não está em nenhuma palavra).
          if (m.every((x) => x && x === m[0])) { b.classList.add(m[0]); return; }
          const seg = P.h(`<div class="kseg" style="grid-template-columns:repeat(2,1fr);grid-auto-rows:1fr"></div>`);
          m.forEach((x) => seg.appendChild(P.h(`<i style="background:${x ? colors[x] : "var(--key)"}"></i>`)));
          b.prepend(seg);
        });
      },
    };
  };

  // ---------- confete ----------
  P.confetti = () => {
    const c = document.createElement("canvas"); c.id = "confetti"; document.body.appendChild(c);
    const dpr = window.devicePixelRatio || 1; c.width = innerWidth * dpr; c.height = innerHeight * dpr;
    const g = c.getContext("2d"); g.scale(dpr, dpr);
    const cols = ["#5FB873", "#E6C14F", "#9B8CFF", "#F4F1FF"];
    const ps = Array.from({ length: 140 }, () => ({ x: Math.random() * innerWidth, d: Math.random() * 0.6, v: 0.35 + Math.random() * 0.45, dr: Math.random() * 2 - 1, s: 6 + Math.random() * 8, sp: Math.random() * 720 - 360, c: cols[Math.floor(Math.random() * 4)], r: Math.random() < 0.5 }));
    const t0 = performance.now();
    const frame = (now) => {
      const t = (now - t0) / 1000; g.clearRect(0, 0, innerWidth, innerHeight);
      const fade = Math.min(1, Math.max(0, 1 - (t - 2.4) / 0.8));
      ps.forEach((p) => {
        const life = t - p.d; if (life < 0) return;
        const y = -20 + life * p.v * innerHeight, x = p.x + Math.sin(life * 3 + p.dr * 5) * 30 * p.dr;
        g.save(); g.globalAlpha = fade; g.translate(x, y); g.rotate((p.sp * life * Math.PI) / 180); g.fillStyle = p.c;
        if (p.r) { g.beginPath(); g.arc(0, 0, p.s / 2, 0, 7); g.fill(); } else g.fillRect(-p.s / 2, -p.s / 4, p.s, p.s / 2);
        g.restore();
      });
      if (t < 3.2) requestAnimationFrame(frame); else c.remove();
    };
    requestAnimationFrame(frame);
  };

  // ---------- compartilhar ----------
  P.share = async (text) => {
    try { if (navigator.share) { await navigator.share({ text }); return; } } catch (_) { return; }
    try { await navigator.clipboard.writeText(text); P.toast("Copiado! Cole onde quiser."); } catch (_) { P.toast("Não deu para copiar"); }
  };

  /** Base dos cartões de Stories (1080×1920): fundo "Noite suave", bolhas de luz e a marca no topo. */
  const storyBase = async () => {
    await document.fonts.ready.catch(() => {});
    const W = 1080, H = 1920, c = document.createElement("canvas"); c.width = W; c.height = H;
    const g = c.getContext("2d");
    const bg = g.createLinearGradient(0, 0, 0, H); bg.addColorStop(0, "#2A2058"); bg.addColorStop(1, "#120E2B");
    g.fillStyle = bg; g.fillRect(0, 0, W, H);
    g.fillStyle = "rgba(155,140,255,.10)"; g.beginPath(); g.arc(W * 0.85, H * 0.12, 360, 0, 7); g.fill();
    g.fillStyle = "rgba(95,184,115,.07)"; g.beginPath(); g.arc(W * 0.1, H * 0.85, 420, 0, 7); g.fill();
    const rr = (x, y, w, h, r, col) => { g.fillStyle = col; g.beginPath(); g.roundRect(x, y, w, h, r); g.fill(); };
    const font = (size, weight) => `${weight} ${size}px Outfit, "Noto Color Emoji", sans-serif`;
    /** Texto centralizado que diminui até caber em maxW. Devolve o tamanho usado. */
    const txt = (s, y, size, weight, col, spacing, maxW = W - 120, x = W / 2, align = "center") => {
      g.font = font(size, weight); g.fillStyle = col; g.textAlign = align;
      if (spacing) g.letterSpacing = spacing; while (g.measureText(s).width > maxW && size > 20) { size -= 2; g.font = font(size, weight); }
      g.fillText(s, x, y, maxW); g.letterSpacing = "0px";
      return size;
    };
    let x = (W - (5 * 46 + 4 * 12)) / 2;
    ["#5FB873", "#E6C14F", "#4A72CF", "#E6C14F", "#5FB873"].forEach((col) => { rr(x, 170, 46, 46, 13, col); x += 58; });
    txt("Palavreiro", 330, 92, 800, "#F4F1FF");
    return { c, g, W, H, rr, txt, font };
  };
  /** Compartilha a imagem (navigator.share com arquivo) ou baixa como alternativa. */
  const storyOut = async (c) => {
    const blob = await new Promise((r) => c.toBlob(r, "image/png"));
    const file = new File([blob], "palavreiro-story.png", { type: "image/png" });
    try { if (navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file] }); return; } } catch (_) { return; }
    const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = "palavreiro-story.png"; a.click();
    P.toast("Imagem salva: poste nos Stories!");
  };

  /** Cartão 1080×1920 para Stories, igual ao do app. */
  P.story = async ({ game, headline, detail, grids, stats = [] }) => {
    const { c, g, W, H, rr, txt } = await storyBase();
    txt(game.toUpperCase(), 420, 40, 600, "#A9A2D0", "7px");
    txt(headline, 560, 76, 800, "#F4F1FF");
    txt(detail, 640, 40, 400, "#A9A2D0");
    const colors = { c: "#5FB873", p: "#E6C14F", a: "#3A3363" };
    if (grids.length) {
      const top = 720, bottom = H - 520, cols = grids.length === 1 ? 1 : 2, rowsB = Math.ceil(grids.length / cols);
      const maxR = Math.max(...grids.map((b) => b.length), 1), maxC = Math.max(...grids.map((b) => Math.max(...b.map((r) => r.length), 1)), 1);
      const bg2 = 50, cg = 12;
      const cell = Math.min(((W - 160 - bg2 * (cols - 1)) / cols - cg * (maxC - 1)) / maxC, ((bottom - top - bg2 * (rowsB - 1)) / rowsB - cg * (maxR - 1)) / maxR, 120);
      const bw = maxC * cell + (maxC - 1) * cg, bh = maxR * cell + (maxR - 1) * cg;
      const sx = (W - (cols * bw + (cols - 1) * bg2)) / 2, sy = top + (bottom - top - (rowsB * bh + (rowsB - 1) * bg2)) / 2;
      grids.forEach((block, bi) => {
        const bx = sx + (bi % cols) * (bw + bg2), by = sy + Math.floor(bi / cols) * (bh + bg2);
        block.forEach((line, r) => {
          const lx = bx + (bw - (line.length * cell + (line.length - 1) * cg)) / 2;
          line.forEach((m, k) => rr(lx + k * (cell + cg), by + r * (cell + cg), cell, cell, cell * 0.24, colors[m] || m));
        });
      });
    }
    stats.forEach(([v, l], i) => {
      const cx = (W / (stats.length + 1)) * (i + 1);
      g.textAlign = "center"; g.font = "800 72px Outfit, sans-serif"; g.fillStyle = "#F4F1FF"; g.fillText(v, cx, H - 400);
      g.font = "400 34px Outfit, sans-serif"; g.fillStyle = "#A9A2D0"; g.fillText(l, cx, H - 346);
    });
    const d = new Date();
    txt(`${String(d.getDate()).padStart(2, "0")}/${String(d.getMonth() + 1).padStart(2, "0")}/${d.getFullYear()}`, H - 210, 36, 400, "#A9A2D0");
    txt("criado por @dmwnezes", H - 150, 38, 600, "rgba(244,241,255,.85)");
    await storyOut(c);
  };

  /**
   * Cartão de Stories da partida com amigo: "EU  2 × 1  AMIGO", frase do resultado e detalhe do modo.
   * {pill, me, opp, score, gold, result, detail: string | [linha1, linha2], mine: "win"|"lose"|"draw"}
   */
  P.storyVs = async ({ pill, me, opp, score, gold, result, detail, mine }) => {
    const { c, g, W, H, rr, txt, font } = await storyBase();
    // pílula do modo
    g.font = font(44, 700);
    const pw = g.measureText(pill).width + 84;
    rr((W - pw) / 2, 410, pw, 88, 44, "rgba(155,140,255,.20)");
    g.strokeStyle = "rgba(155,140,255,.55)"; g.lineWidth = 3; g.beginPath(); g.roundRect((W - pw) / 2, 410, pw, 88, 44); g.stroke();
    txt(pill, 470, 44, 700, "#F4F1FF");
    // Nomes dos lados do placar; se um nome não cabe grande na coluna, empilha: NOME / placar / NOME.
    g.font = font(190, 800);
    const sw = g.measureText(score).width, colW = (W - 120 - 80 - sw - 2 * 36) / 2;
    const fits = (s) => { g.font = font(56, 800); return g.measureText(s).width <= colW; };
    const side = fits(me) && fits(opp);
    const top = side ? 680 : 640, ch = side ? 640 : 720;
    rr(60, top, W - 120, ch, 64, "rgba(35,28,72,.82)");
    g.strokeStyle = gold ? "rgba(245,200,76,.55)" : "rgba(155,140,255,.30)"; g.lineWidth = 4; g.beginPath(); g.roundRect(60, top, W - 120, ch, 64); g.stroke();
    const meCol = mine === "win" ? "#5FB873" : "#F4F1FF", oppCol = mine === "lose" ? "#9B8CFF" : "#F4F1FF", scoreCol = gold ? "#F5C84C" : "#F4F1FF";
    let lineY;
    if (side) {
      const cy = top + 250;
      g.font = font(190, 800); g.textAlign = "center"; g.fillStyle = scoreCol; g.fillText(score, W / 2, cy + 66);
      const name = (s, cx, col) => {
        let size = 84; g.font = font(size, 800);
        while (g.measureText(s).width > colW && size > 56) { size -= 2; g.font = font(size, 800); }
        g.fillStyle = col; g.textAlign = "center"; g.fillText(s, cx, cy + 30, colW);
      };
      name(me, 60 + 40 + colW / 2, meCol);
      name(opp, W - 60 - 40 - colW / 2, oppCol);
      lineY = top + 400;
    } else {
      txt(me, top + 150, 88, 800, meCol, null, W - 220);
      g.font = font(170, 800); g.textAlign = "center"; g.fillStyle = scoreCol; g.fillText(score, W / 2, top + 345);
      txt(opp, top + 470, 88, 800, oppCol, null, W - 220);
      lineY = top + 520;
    }
    // linha divisória e frase do resultado
    rr(W / 2 - 60, lineY, 120, 6, 3, gold ? "rgba(245,200,76,.7)" : "rgba(155,140,255,.5)");
    txt(result, lineY + 100, 64, 800, gold ? "#F5C84C" : mine === "win" ? "#5FB873" : "#F4F1FF", null, W - 200);
    // detalhe do modo
    const lines = Array.isArray(detail) ? detail : [detail];
    g.font = font(40, 500);
    if (lines.length === 2 && g.measureText(lines.join("  |  ")).width <= W - 160) txt(lines.join("  |  "), top + ch + 110, 40, 500, "#A9A2D0", null, W - 160);
    else lines.forEach((l, i) => txt(l, top + ch + 110 + i * 60, 40, 500, "#A9A2D0", null, W - 160));
    // rodapé
    rr(120, H - 330, W - 240, 120, 60, "rgba(95,184,115,.16)");
    txt("Jogue comigo: dmwnezes.github.io/termo", H - 255, 42, 700, "#F4F1FF", null, W - 300);
    txt("criado por: @dmwnezes", H - 140, 38, 600, "rgba(244,241,255,.75)");
    await storyOut(c);
  };

  // ---------- navegação ----------
  P.go = (route) => { location.hash = "#/" + route; };
})();
