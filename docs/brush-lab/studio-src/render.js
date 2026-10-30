// ===================================================================================================================
// Brush Studio: drawing one picture with the lab's engine. No DOM: it runs in a worker (the engine plus this file) or
// on the page when workers can't start. A job is plain data; the answer is the page at the Manta's 16 greys, one byte
// a pixel (0, 17, … 255).
//   { k: "sample", B, role, w, h, shade, tilt, drops }: a tile, as the lab draws its samples (ui.js renderSample). The
//     loop ("loop") is a whole brush: the app's sample loop, upright, or with the pen tilting along it (tilt), or with
//     Float's drops (drops). Every other role is a wave, one part or setting (docs/DESIGN.md "Loop and wave"): a grey
//     band behind for medium, smudge and drying, a ramp of pressure and tilt for control.
//   { k: "path", B, w, h, shade, path, size, under }: a stroke along path (points in a unit box, fitted to the tile),
//     at size px, or at a sample's size when size is 0; under: a grey band and an ink line behind it (bare: only those).
// ===================================================================================================================
const STUDIO_SURFS = new Map();
// A white brush (newmods.js Blend White) draws on a dark ground, as the lab's samples: on paper it would show nothing.
const studioWhite = B => !!B && B.medium.k !== "resist" && (B.medium.d.blend | 0) === 1;
function studioSurf(w, h, B) {
  const key = w + "x" + h;
  let s = STUDIO_SURFS.get(key);
  if (!s) { if (STUDIO_SURFS.size > 16) STUDIO_SURFS.clear(); s = modSurface(w, h, 7); STUDIO_SURFS.set(key, s); }
  blankPage(s);
  if (studioWhite(B)) { s.page.fill(68); s.base.set(s.page); }
  return s;
}
// The lab's sample geometry (ui.js wavePath, loopPath, sampleSize), so a Studio tile reads as the lab's and the app's.
function studioWave(w, h, pad, amp, opts = {}) {
  const pts = [], x0 = pad, x1 = w - pad, n = Math.max(30, Math.ceil((x1 - x0) / 2.5));
  let t = 0;
  for (let i = 0; i <= n; i++) {
    const u = i / n, env = Math.sin(Math.PI * u), x = lerp(x0, x1, u), y = h / 2 + amp * Math.sin(2 * Math.PI * (opts.waves || 1.25) * u);
    const p = opts.ramp ? .08 + .92 * Math.pow(u, 1.2) : .15 + .78 * Math.pow(env, .7);
    t += opts.ramp ? 2 + 5 * (1 - u) : 2 + 4 * (1 - env);
    const tilt = opts.tilt ? [Math.atan(Math.tan(u * 55 * DEG) * Math.cos((200 + u * 120) * DEG)) / DEG, Math.atan(Math.tan(u * 55 * DEG) * Math.sin((200 + u * 120) * DEG)) / DEG] : [NaN, NaN];
    pts.push({ x, y, p, t, tx: tilt[0], ty: tilt[1] });
  }
  return pts;
}
function studioSampleSize(B, h, role) {
  const mk = B.medium.k, tk = B.tip.k;
  let s;
  if (mk === "ink") s = h / (13 + 14 * B.medium.d.wet);
  else if (mk === "charcoal" || mk === "crayon") s = h / 9;
  else if (mk === "graphite") s = h / 16;
  else s = tk === "hake" ? h * .24 : h * .17;
  if (tk === "bristles" && (mk === "ink" || mk === "graphite")) s = h / 8;
  if (role === "paper") s *= 1.7;
  if (role === "hand" && s < h / 7) s *= 1.8;
  return Math.max(2, s);
}
let STUDIO_CROSS = null;   // the plain pen line a Slow wash that takes ink is crossed with (ui.js CROSS_LINE)
function studioCross() {
  if (!STUDIO_CROSS) { const x = fromOld(BUILT[0]); x.life = freshPart("life", "none"); x.hand.d.ps = .3; STUDIO_CROSS = normBrush(x); }
  return STUDIO_CROSS;
}
function studioHost(s, B) {
  if (B.life.k === "dry") return { water: null, getWater() { return this.water ??= new Water(s, 11); } };
  return null;
}
// Whatever still moves is finished for a picture: a Slow wash dried, a Halo grown (newmods.js, when the lab has it).
function studioFinish(host, s) {
  if (host && host.water) host.water.dryAll();
  if (s && s.halos && s.halos.length && typeof haloDryAll === "function") haloDryAll(s);
}
// Float's tile, as the lab's: its stroke, then drops of it and of clear water in turn at the middle, then printed. With
// a host of its own (Brush Lab Dials: dial-demo-src/render.js) it is left floating there to tick; returns the pen's
// time at the last lift.
function studioFloat(s, B, pts, size, shade, ref, keep) {
  const host = keep || { bath: null, getBath() { return this.bath ??= new Bath(s); } }, cw = cloneBrush(B);
  cw.carry = switchChoice("carry", B.carry, "smudge"); cw.carry.d.load = 0; normBrush(cw);
  const own = isClearWater(B) ? (() => { const c = cloneBrush(B); c.carry = freshPart("carry", "off"); return normBrush(c); })() : B;
  const small = Math.max(2, Math.min(size, ref / 9));
  if (!isClearWater(B)) layStroke(s, B, pts, size, shade, host);
  const cx = s.w / 2, cy = s.h / 2;
  let last = 0;
  for (let i = 0; i < 5; i++) for (const [b, hold] of [[own, 260 + 90 * i], [cw, 160]]) {
    last = 1000 * i + hold;
    const st = new Stroke(s, b, small, i === 2 ? shade * .5 : shade, host);
    st.add([[cx, cy, .8, 1000 * i, NaN, NaN]]); for (let t = 50; t <= hold; t += 50) st.holdTick(1000 * i + t); st.end();
  }
  if (!keep && host.bath && host.bath.wet) host.bath.print();
  return last;
}
function studioBand(s, x0f, x1f, tone) {
  const x0 = Math.round(s.w * x0f), x1 = Math.round(s.w * x1f);
  for (let y = 0; y < s.h; y++) for (let x = x0; x < x1; x++) s.page[y * s.w + x] = tone;
}
function studioSample(m) {
  const { B, role, w, h } = m, shade = m.shade ?? 1, s = studioSurf(w, h, B);
  const looped = role === "loop";
  const ref = looped ? Math.min(w, h * .73) : Math.min(h, w * .62), size = studioSampleSize(B, ref, role), lineish = size < ref / 7;
  if (role === "medium" || role === "carry" || role === "life") {
    studioBand(s, .42, .58, role === "carry" ? 60 : 150);
    if (role === "carry") studioBand(s, .2, .24, 40);
  }
  const pad = Math.max(size * (lineish ? 2 : .7) + 6, w * .06), amp = Math.max(3, Math.min(ref * .3, h / 2 - size * .6 - 6));
  let pts;
  if (looped) pts = loopPoints(w, h, Math.max(4, lineish ? size * 2 + 6 : size * .55 + 4)).map((p, i, a) => {
    // tilt: as the lab's control wave, from upright to 55° along the stroke, its direction turning.
    const u = i / Math.max(1, a.length - 1), tx = m.tilt ? Math.atan(Math.tan(u * 55 * DEG) * Math.cos((200 + u * 120) * DEG)) / DEG : NaN, ty = m.tilt ? Math.atan(Math.tan(u * 55 * DEG) * Math.sin((200 + u * 120) * DEG)) / DEG : NaN;
    return { x: p.x, y: p.y, p: p.p, t: p.t, tx, ty };
  });
  else if (role === "hand") pts = studioWave(w, h, pad, amp * .6, { ramp: true, tilt: true, waves: 1 });
  else if (role === "carry") pts = studioWave(w, h, pad, amp * .4, { waves: .75 });
  else if (role === "paper") pts = studioWave(w, h, pad, amp * .35, { waves: .75 });
  else pts = studioWave(w, h, pad, amp);
  if (B.life.k === "float" && (role === "life" || m.drops || isClearWater(B))) { studioFloat(s, B, pts, size, shade, ref); return s; }
  const host = (role === "life" || role === "medium" || looped) ? studioHost(s, B) : null;
  layStroke(s, B, pts, size, shade, host);
  if (host && host.water && (B.life.d.takes | 0) && role === "life") {
    const n = 40, line = [];
    for (let i = 0; i <= n; i++) { const u = i / n; line.push({ x: w * (.38 + .24 * u), y: h / 2 + ref * (-.42 + .84 * u), p: .7, t: i * 10, tx: NaN, ty: NaN }); }
    layStroke(s, studioCross(), line, Math.max(2, ref / 45), 1, host);
  }
  studioFinish(host, s);
  return s;
}
// A stroke along a path given in a unit box (x, y from 0 to 1 over the box's aspect), fitted into the tile.
function studioPath(m) {
  const { B, w, h, path } = m, shade = m.shade ?? 1, s = studioSurf(w, h, B);
  const ref = Math.min(h, w * .62), size = m.size > 0 ? m.size : studioSampleSize(B, ref, "path");
  if (m.under || m.bare) {
    studioBand(s, .42, .58, 150);
    const zig = [], n = 60;
    for (let i = 0; i <= n; i++) { const u = i / n; zig.push({ x: w * (.2 + .06 * u), y: h * (.1 + .8 * u), p: .7, t: i * 6, tx: NaN, ty: NaN }); }
    layStroke(s, studioCross(), zig, Math.max(2, h / 60), 1, null);
  }
  if (m.bare) return s;
  const pts = studioPathPts(path, studioFit(path, w, h, size));
  if (B.life.k === "float" && isClearWater(B)) { studioFloat(s, B, pts, size, shade, ref); return s; }
  const host = studioHost(s, B);
  layStroke(s, B, pts, size, shade, host);
  studioFinish(host, s);
  return s;
}
// The path's box fitted into a w × h tile, with room for the mark: { sc, ox, oy }.
function studioFit(path, w, h, size) {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const p of path) { x0 = Math.min(x0, p.x); x1 = Math.max(x1, p.x); y0 = Math.min(y0, p.y); y1 = Math.max(y1, p.y); }
  const pad = Math.min(w, h) * .06 + Math.min(size * .7, Math.min(w, h) * .3), bw = Math.max(1e-6, x1 - x0), bh = Math.max(1e-6, y1 - y0);
  const sc = Math.min((w - 2 * pad) / bw, (h - 2 * pad) / bh);
  return { sc, ox: (w - bw * sc) / 2 - x0 * sc, oy: (h - bh * sc) / 2 - y0 * sc };
}
// A path's points on the tile. Each point carries pressure p, tilt (degrees from upright) with az (its direction) and
// speed v in mm/s; the times come from the speed over the fitted distance (300 px an inch, as the Manta).
function studioPathPts(path, f) {
  let t = 0, px = 0, py = 0;
  return path.map((p, i) => {
    const x = f.ox + p.x * f.sc, y = f.oy + p.y * f.sc;
    if (i) t += Math.hypot(x - px, y - py) * 25.4 / 300 / Math.max(1, p.v || 100) * 1000;
    px = x; py = y;
    const k = Math.tan(clamp(p.tilt || 0, 0, 80) * DEG), up = !(p.tilt >= 1);
    return { x, y, p: clamp(p.p, 0, 1), t, tx: up ? NaN : Math.atan(k * Math.cos(p.az || 0)) / DEG, ty: up ? NaN : Math.atan(k * Math.sin(p.az || 0)) / DEG };
  });
}
function studioJob(m) {
  const t0 = performance.now();
  const s = m.k === "path" ? studioPath(m) : studioSample(m), n = s.w * s.h, gray = new Uint8Array(n), page = s.page;
  // The page as the screen shows it: with a resist hatched over it when the lab has one (newmods.js pageGrays).
  if (s.resist && typeof pageGrays === "function") { const g = pageGrays(s, true); for (let i = 0; i < n; i++) gray[i] = Math.round(g[i] * 255); }
  else for (let i = 0; i < n; i++) { const v = Math.round(page[i] / 17) * 17; gray[i] = v < 0 ? 0 : v > 255 ? 255 : v; }
  return { id: m.id, w: s.w, h: s.h, gray, ms: performance.now() - t0 };
}
