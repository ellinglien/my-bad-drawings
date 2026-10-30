// ===================================================================================================================
// Brush Lab Dials: one tile's pictures. No DOM: it runs in a worker (the engine, Brush Studio's render.js and this) or
// on the page when workers can't start. A job is plain data (tiles.js); the answer is frames at the Manta's 16 greys,
// one byte a pixel (0 to 15). The stroke is Studio's (studioWave, studioSampleSize, studioBand, studioCross); what moves
// after lift ticks as the lab's test area ticks it: Slow's water (stroke.js Water.step, ten a second), Float's bath
// (float.js Bath.tick), Mold's halos (newmods.js Halo.step) and Fading's and Lemon Juice's clock (newmods.js fadeAll).
// ===================================================================================================================
const DEMO_W = 480, DEMO_H = 240, DEMO_TICK = 1000 / TPS;
// Fading's clock is the pen's time here (the checks set their own the same way).
const DEMO_CLOCK = { t: 0 };
if (typeof FADE_CLOCK !== "undefined") FADE_CLOCK.now = () => DEMO_CLOCK.t;
const DEMO_TILT = { x: 0, y: .8 };   // a page tilted toward the foot, for Float's Drift Downhill

function demoLevels(s) {
  const n = s.w * s.h, out = new Uint8Array(n);
  if (s.resist && typeof pageGrays === "function") { const g = pageGrays(s, true); for (let i = 0; i < n; i++) out[i] = Math.max(0, Math.min(15, Math.round(g[i] * 15))); }
  else for (let i = 0; i < n; i++) { const v = Math.round(s.page[i] / 17); out[i] = v < 0 ? 0 : v > 15 ? 15 : v; }
  return out;
}
// What keeps changing after lift, as the lab's test area hosts it.
// What keeps changing after lift, hosted as the lab's test area hosts it (ui.js HOST): its water and bath, and the
// surface's halos and fading marks kept to tick.
function demoHost(s, B) {
  if (!["dry", "float", "halo", "fade", "lemon"].includes(B.life.k)) return null;
  return { water: null, getWater() { return this.water ??= new Water(s, 11); }, bath: null, getBath() { return this.bath ??= new Bath(s); }, halos: true, fades: true };
}
const demoWet = (s, host) => !!host && !!((host.water && host.water.wet) || (host.bath && host.bath.wet) || s.halos.length || s.fades.length);
// One tick, 100 ms of pen time ending at t.
function demoTick(s, host, t, tilt, drift) {
  DEMO_CLOCK.t = t;
  if (host.water && host.water.wet) host.water.step();
  if (host.bath && host.bath.wet) host.bath.tick(t, tilt, drift);
  if (s.halos.length) { for (const h of s.halos) h.step(); s.halos = s.halos.filter(h => h.tick < h.N); }
  if (s.fades.length) fadeAll(s, t);
}
// Everything still moving, finished: for a picture of the mark once it has settled.
function demoSettle(s, host) {
  if (!host) return;
  if (host.water) host.water.dryAll();
  if (host.bath && host.bath.wet) host.bath.print();
  haloDryAll(s);
  if (s.fades.length) { DEMO_CLOCK.t += 1e7; fadeAll(s, DEMO_CLOCK.t); }
}

// ---------- the stroke ----------
const DEMO_SHAPES = { hand: [.6, { ramp: true, tilt: true, waves: 1 }], carry: [.4, { waves: .75 }], paper: [.35, { waves: .75 }] };
function demoSurface(m, B) {
  const s = studioSurf(DEMO_W, DEMO_H, B);
  // blankPage leaves the last stamp's ink and a dirty brush's paint on the surface (engine.js, newmods.js): every tile starts clean.
  s.halos = []; s.fades = []; s.stampInk = null; s.heldPaint = null;
  const role = m.role;
  if (role === "medium" || role === "carry" || role === "life") {
    studioBand(s, .42, .58, role === "carry" ? 60 : 150);
    if (role === "carry") studioBand(s, .2, .24, 40);
  }
  if (m.box) {
    // Ink lines round most of the tile, for Stay in Lines to keep inside.
    const w = s.w, h = s.h, x0 = w * .03, x1 = w * .64, y0 = h * .08, y1 = h * .92, line = [];
    for (const [a, b, c, d] of [[x0, y0, x1, y0], [x1, y0, x1, y1], [x1, y1, x0, y1], [x0, y1, x0, y0]])
      for (let i = 0; i <= 30; i++) line.push({ x: a + (c - a) * i / 30, y: b + (d - b) * i / 30, p: .7, t: line.length * 6, tx: NaN, ty: NaN });
    layStroke(s, studioCross(), line, Math.max(2, h / 60), 1, null);
  }
  return s;
}
// The wave a part's tile draws (Studio's: render.js studioSample), sized by the tile's own brush before its dial moves
// (B0), so every frame draws the same stroke.
function demoPoints(m, B0) {
  const w = DEMO_W, h = DEMO_H, ref = Math.min(h, w * .62), size = studioSampleSize(B0, ref, m.role), lineish = size < ref / 7;
  const pad = Math.max(size * (lineish ? 2 : .7) + 6, w * .06), amp = Math.max(3, Math.min(ref * .3, h / 2 - size * .6 - 6));
  const [k, opts] = DEMO_SHAPES[m.role] || [1, {}];
  let pts = studioWave(w, h, pad, amp * k, opts);
  // fast: an even, quick pace to the lift (Flick); stop: quick, then still with the pen down (Fling's hard stop).
  if (m.pace) {
    // Ended where the wave runs level (Fling at a third, its drops flying furthest), so what flies on lands in the tile.
    pts = pts.slice(0, Math.round(pts.length * (m.pace === "stop" ? .32 : .75))).map((p, i) => Object.assign({}, p, { t: i * .7 }));   // about 3.5 px a ms: past Fling's 3 (newmods.js FF)
    if (m.pace === "stop") { const q = pts[pts.length - 1]; for (let j = 1; j <= 5; j++) pts.push(Object.assign({}, q, { t: q.t + 15 * j })); }
  }
  return { pts, size, ref };
}
// Fed as a pen feeds it, three samples an event; rest: ms the pen stays still at the middle (Build-Up).
function demoStroke(s, B, pts, size, shade, host, rest, upTo) {
  const st = new Stroke(s, B, size, shade, host), stop = upTo ?? pts.length, mid = rest ? pts.length >> 1 : -1;
  let dt = 0;
  for (let i = 0; i < stop; i += 3) {
    const grp = pts.slice(i, Math.min(stop, i + 3));
    st.add(grp.map(p => [p.x, p.y, p.p, p.t + dt, p.tx, p.ty]));
    if (mid >= i && mid < i + 3) { const q = grp[grp.length - 1]; for (let t = 20; t <= rest; t += 20) { st.add([[q.x, q.y, q.p, q.t + t, q.tx, q.ty]]); st.holdTick(q.t + t); } dt = rest; }
  }
  return st;
}
// The tile's marks, up to the lift. Returns the pen's time at the last lift, or the level frame at the pen's stop
// (at: a share of the stroke drawn, the pen still down) as { pen }.
function demoMarks(m, B, B0, s, host, onPen) {
  const shade = m.shade ?? 1, { pts, size, ref } = demoPoints(m, B0);
  if (B.life.k === "float" && (m.drops || isClearWater(B))) return studioFloat(s, B, pts, size, shade, ref, host);
  if (m.at !== undefined) {
    const st = demoStroke(s, B, pts, size, shade, host, 0, Math.round(pts.length * m.at));
    const out = demoLevels(s); st.end(); return { pen: out };
  }
  let lift = 0;
  if (m.strokes > 1) {
    // Separate strokes along the wave, a short gap between them; with Grey Shift each takes its grey at the touch.
    const n = m.strokes, per = Math.ceil(pts.length / n), turn = shiftTurn(), r = rng(DEMO_SEED_SHIFT);
    turn.random = r;
    for (let j = 0; j < n; j++) {
      const part = pts.slice(j * per, Math.min(pts.length, (j + 1) * per - 2));
      if (part.length < 2) continue;
      const k = shiftPick(B, turn, null), sh = k < 0 ? shade : shiftDark(B, k, null);
      const t0 = part[0].t - 400 * j;
      demoStroke(s, B, part.map(p => Object.assign({}, p, { t: p.t - t0 })), size, sh, host).end();
      lift = part[part.length - 1].t - t0;
    }
  } else {
    const st = demoStroke(s, B, pts, size, shade, host, m.rest);
    DEMO_CLOCK.t = pts[pts.length - 1].t + (m.rest || 0);
    st.end();
    lift = DEMO_CLOCK.t;
  }
  if (m.cross && host && host.water && host.water.wet) {
    // A plain pen line drawn into the still-wet wash (Takes Ink), as Studio's and the lab's samples cross it.
    const line = [], w = s.w, h = s.h;
    for (let i = 0; i <= 40; i++) { const u = i / 40; line.push({ x: w * (.38 + .24 * u), y: h / 2 + ref * (-.42 + .84 * u), p: .7, t: i * 10, tx: NaN, ty: NaN }); }
    layStroke(s, studioCross(), line, Math.max(2, ref / 45), 1, host);
    host.water.step();
  }
  if (m.rub) {
    // The eraser across the middle, a broad stroke from top to foot (Erasable).
    const er = new Eraser(s, 120);
    for (let k = 0; k <= 40; k++) er.add([[s.w * .5, -20 + (s.h + 40) * k / 40, 1, k * 6, NaN, NaN]]);
  }
  return lift;
}
const DEMO_SEED_SHIFT = 0x5f1;

// ---------- jobs ----------
// { k: "frame", B, B0, role, … }: the mark once it has settled (or, with at, the pen still down).
function demoFrame(m) {
  const { B, B0 } = m, s = demoSurface(m, B);
  const host = B.life.k === "dry" || B.life.k === "float" ? demoHost(s, B) : null;
  const r = demoMarks(m, B, B0, s, host);
  if (r && r.pen) return [r.pen];
  demoSettle(s, host);
  return [demoLevels(s)];
}
// { k: "after", B, B0, role, times }: frames at each time after the last lift (ms), the last once it has settled.
// Ticks ten a second, as the lab's test area, rocking nothing; Float's page tilted for its drift.
function demoAfter(m) {
  const { B, B0 } = m, s = demoSurface(m, B), host = demoHost(s, B), frames = [], times = [];
  const lift = demoMarks(m, B, B0, s, host), tilt = m.tilt ? DEMO_TILT : { x: 0, y: 0 }, drift = B.life.d.drift ?? .5;
  let t = lift;
  for (const at of m.times) {
    while (t - lift < at && demoWet(s, host)) { t += DEMO_TICK; demoTick(s, host, t, tilt, drift); }
    frames.push(demoLevels(s)); times.push(at);
  }
  let n = 0;
  while (demoWet(s, host) && n++ < 400) { t += DEMO_TICK; demoTick(s, host, t, tilt, drift); }
  demoSettle(s, host);
  frames.push(demoLevels(s)); times.push(Math.max(t - lift, m.times[m.times.length - 1]));
  return { frames, times };
}
// { k: "time", B, B0, role }: the stroke as it happens, about ten frames a second of pen time, then after lift until
// nothing moves (at most 40 s), fewer frames the longer it goes. { frames, times, lift, dry } in ms of pen time.
function demoTimeline(m) {
  const { B, B0 } = m, s = demoSurface(m, B), host = demoHost(s, B), shade = m.shade ?? 1, { pts, size, ref } = demoPoints(m, B0);
  const frames = [demoLevels(s)], times = [0], tilt = m.tilt ? DEMO_TILT : { x: 0, y: 0 }, drift = B.life.d.drift ?? .5;
  let lift;
  if (B.life.k === "float") { lift = studioFloat(s, B, pts, size, shade, ref, host); frames.push(demoLevels(s)); times.push(lift); }
  else {
    const st = new Stroke(s, B, size, shade, host);
    let last = 0;
    for (let i = 0; i < pts.length; i += 3) {
      const grp = pts.slice(i, i + 3), t = grp[grp.length - 1].t;
      st.add(grp.map(p => [p.x, p.y, p.p, p.t, p.tx, p.ty]));
      if (t - last >= 100) { frames.push(demoLevels(s)); times.push(t); last = t; }
    }
    lift = pts[pts.length - 1].t + 20; DEMO_CLOCK.t = lift;
    st.end();
    frames.push(demoLevels(s)); times.push(lift);
  }
  let t = lift, n = 0;
  while (demoWet(s, host) && n < 400) {
    t += DEMO_TICK; n++; demoTick(s, host, t, tilt, drift);
    if (n <= 20 || n % (n <= 100 ? 5 : 20) === 0) { frames.push(demoLevels(s)); times.push(t); }
  }
  if (demoWet(s, host)) { demoSettle(s, host); t += DEMO_TICK; }
  if (times[times.length - 1] !== t) { frames.push(demoLevels(s)); times.push(t); }
  return { frames, times, lift, dry: t };
}
function demoJob(m) {
  const t0 = performance.now();
  if (m.k === "time") return Object.assign({ id: m.id, i: 0, ms: performance.now() - t0 }, demoTimeline(m));
  if (m.k === "after") return Object.assign({ id: m.id, i: m.i }, demoAfter(m), { ms: performance.now() - t0 });
  return { id: m.id, i: m.i, frames: demoFrame(m), ms: performance.now() - t0 };
}
