// Palavreiro (site): aviso de conquista desbloqueada e o easter egg dos créditos (segurar ~1,5 s).
(function () {
  "use strict";
  const reduced = () => window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;

  // ---------- conquista desbloqueada ----------
  // As conquistas já vistas ficam em localStorage "pv-ach-seen" (títulos). Na primeira vez, só guarda as atuais.
  const SEEN = "ach-seen";
  const unlocked = () => { try { return P.achievements().filter((a) => a.ok); } catch (_) { return []; } };
  const queue = [];
  let showing = false, checkTimer = 0;

  function showNext() {
    if (showing || !queue.length) return;
    showing = true;
    const a = queue.shift();
    const el = P.h(`<div class="ach-pill" role="status" aria-live="polite">
      <span class="ap-k">🏆 Conquista desbloqueada</span><span class="ap-sep" aria-hidden="true">·</span><b><span aria-hidden="true">${a.emoji}</span> ${a.title}</b></div>`);
    document.body.appendChild(el);
    P.vibrate([20, 40, 20]);
    requestAnimationFrame(() => requestAnimationFrame(() => el.classList.add("in")));
    setTimeout(() => {
      el.classList.remove("in");
      setTimeout(() => { el.remove(); showing = false; showNext(); }, 380);
    }, 3000);
  }

  P.checkAchievements = () => {
    const ok = unlocked(), titles = ok.map((a) => a.title);
    const seen = P.store.get(SEEN);
    if (!Array.isArray(seen)) { P.store.set(SEEN, titles); return; } // primeira vez: semeia em silêncio
    const fresh = ok.filter((a) => !seen.includes(a.title));
    if (!fresh.length) return;
    P.store.set(SEEN, seen.concat(fresh.map((a) => a.title)));
    fresh.forEach((a) => queue.push(a));
    showNext();
  };
  const schedule = () => { clearTimeout(checkTimer); checkTimer = setTimeout(P.checkAchievements, 900); };

  // Toda gravação no P.store (add/max/min passam por set) agenda uma verificação.
  const rawSet = P.store.set;
  P.store.set = function (k, v) { rawSet.call(this, k, v); if (k !== SEEN) schedule(); };
  P.checkAchievements();
  setInterval(P.checkAchievements, 8000);

  // ---------- easter egg dos créditos ----------
  const HOLD = 1500;
  let press = null, suppressClick = false, running = false, fired = null, unTimer = 0;
  const unsuppress = (ms) => { clearTimeout(unTimer); unTimer = setTimeout(() => (suppressClick = false), ms); };

  function cancel() {
    if (!press) return;
    press.timers.forEach(clearTimeout);
    press.el.classList.remove("pressing");
    press = null;
    try { if (navigator.vibrate && P.settings().vibration) navigator.vibrate(0); } catch (_) {}
  }

  document.addEventListener("pointerdown", (e) => {
    const el = e.target.closest && e.target.closest(".credit");
    if (!el || (e.pointerType === "mouse" && e.button !== 0) || running) return;
    cancel();
    // Pulsos cada vez mais fortes enquanto segura.
    const pulses = [[0, 12], [300, 22], [600, 36], [900, 55], [1200, 80]];
    press = { el, t: performance.now(), x: e.clientX, y: e.clientY, id: e.pointerId, timers: pulses.map(([t, ms]) => setTimeout(() => P.vibrate(ms), t)) };
    press.timers.push(setTimeout(() => {
      const origin = press.el; fired = press.id; cancel();
      suppressClick = true; clearTimeout(unTimer); // só libera depois de soltar o dedo / botão
      explode(origin);
    }, HOLD));
    el.classList.add("pressing");
  });
  document.addEventListener("pointermove", (e) => {
    if (press && e.pointerId === press.id && Math.hypot(e.clientX - press.x, e.clientY - press.y) > 14) cancel();
  });
  ["pointerup", "pointercancel"].forEach((t) => document.addEventListener(t, (e) => {
    if (fired !== null && e.pointerId === fired) { fired = null; unsuppress(700); }
    if (!press || e.pointerId !== press.id) return;
    // Soltou antes da hora: cancela. Se já estava segurando um bom tempo, também não abre o link.
    if (performance.now() - press.t > 600) { suppressClick = true; unsuppress(700); }
    cancel();
  }));
  window.addEventListener("blur", cancel);
  // Segurar não abre o Instagram (nem o menu do link / arrastar).
  document.addEventListener("click", (e) => {
    if (suppressClick && e.target.closest && e.target.closest(".credit")) { e.preventDefault(); e.stopPropagation(); suppressClick = false; }
  }, true);
  document.addEventListener("contextmenu", (e) => { if (e.target.closest && e.target.closest(".credit")) e.preventDefault(); });
  document.addEventListener("dragstart", (e) => { if (e.target.closest && e.target.closest(".credit")) e.preventDefault(); });

  function thanks() {
    const msg = P.h(`<div class="egg-msg" role="status"><span>Obrigado por jogar! 💚</span></div>`);
    document.body.appendChild(msg);
    return msg;
  }

  function explode(origin) {
    running = true;
    P.vibrate([120, 60, 220]);
    const r = origin.getBoundingClientRect(), ox = r.left + r.width / 2, oy = r.top + r.height / 2;
    if (reduced()) {
      const msg = thanks(); msg.classList.add("calm");
      setTimeout(() => msg.classList.add("out"), 3200);
      setTimeout(() => { msg.remove(); running = false; }, 3800);
      return;
    }
    const c = document.createElement("canvas"); c.className = "egg-canvas"; document.body.appendChild(c);
    const W = innerWidth, H = innerHeight, dpr = window.devicePixelRatio || 1;
    c.width = W * dpr; c.height = H * dpr;
    const g = c.getContext("2d"); g.scale(dpr, dpr);
    const COLS = [["#5FB873", "#FFFFFF"], ["#E6C14F", "#1A1438"], ["#4A72CF", "#FFFFFF"]];
    const n = Math.round(Math.min(70, Math.max(40, (W * H) / 9000)));
    const tiles = Array.from({ length: n }, (_, i) => {
      const ang = -Math.PI / 2 + (Math.random() - 0.5) * Math.PI * 1.5, sp = 520 + Math.random() * 900;
      const col = COLS[i % 3];
      return { x: ox, y: oy, vx: Math.cos(ang) * sp, vy: Math.sin(ang) * sp - 200, a: Math.random() * 6, va: (Math.random() - 0.5) * 14,
        s: 26 + Math.random() * 20, ch: String.fromCharCode(65 + Math.floor(Math.random() * 26)), bg: col[0], fg: col[1], d: Math.random() * 0.18 };
    });
    const GRAV = 1500, END = 4.6, t0 = performance.now();
    let last = t0, msg = null;
    const frame = (now) => {
      const t = (now - t0) / 1000, dt = Math.min(0.033, (now - last) / 1000); last = now;
      if (!msg && t > 0.45) msg = thanks();
      g.clearRect(0, 0, W, H);
      const fade = Math.min(1, Math.max(0, (END - t) / 0.8));
      g.globalAlpha = fade;
      tiles.forEach((p) => {
        if (t < p.d) return;
        p.vy += GRAV * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.a += p.va * dt;
        const h = p.s / 2;
        if (p.y > H - h) { p.y = H - h; p.vy *= -0.55; p.vx *= 0.8; p.va *= 0.7; }
        if (p.x < h) { p.x = h; p.vx = Math.abs(p.vx) * 0.7; }
        if (p.x > W - h) { p.x = W - h; p.vx = -Math.abs(p.vx) * 0.7; }
        const grow = Math.min(1, (t - p.d) / 0.15);
        g.save(); g.translate(p.x, p.y); g.rotate(p.a); g.scale(grow, grow);
        g.fillStyle = p.bg; g.beginPath(); g.roundRect(-h, -h, p.s, p.s, p.s * 0.28); g.fill();
        g.fillStyle = p.fg; g.font = `800 ${Math.round(p.s * 0.58)}px Outfit, system-ui, sans-serif`;
        g.textAlign = "center"; g.textBaseline = "middle"; g.fillText(p.ch, 0, p.s * 0.04);
        g.restore();
      });
      if (msg && t > END - 0.9 && !msg.classList.contains("out")) msg.classList.add("out");
      if (t < END) requestAnimationFrame(frame);
      else { c.remove(); setTimeout(() => { msg && msg.remove(); running = false; }, 500); }
    };
    requestAnimationFrame(frame);
  }
})();
