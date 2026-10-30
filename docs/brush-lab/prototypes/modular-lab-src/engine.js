// ===================================================================================================================
// Modular Brush Lab: the stroke engine. One stroke, two coverage buffers (L: the line, W: the body of paint), one page.
// Built on port.js (the faithful web port of the app's WildInkStroke and wild-paint settle, unchanged). The stampers
// below are copies of the prototypes' (brush-experiments: stampInkX, stampPaintX, the Hake ribbon, Fast-wet; pen-paint-
// blend: partsA, stampPlaced, the mixing settle; marble-and-bristle: the bristle swarm; ink-and-water: Water), each with
// the hand's extra inputs added as factors that are exactly 1 when off, so old brushes draw as before.
// ===================================================================================================================
const DEG = Math.PI / 180;
const smooth = t => { t = clamp(t); return t * t * (3 - 2 * t); };
const smoothstep = (e0, e1, x) => smooth((x - e0) / (e1 - e0));
// lerp that lands exactly on its ends, so Wet 0 and Wet 1 are the old pen and paint to the bit.
const mix = (a, b, t) => t <= 0 ? a : t >= 1 ? b : a + (b - a) * t;
const LINE_WET_K = 1.4;
const PAPER_TONE = 251;
const union = (a, b) => [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])];
const emptyRect = r => !r || r[2] < r[0] || r[3] < r[1];
function grow(r, s) { if (!s || emptyRect(s)) return r; if (s[0] < r[0]) r[0] = s[0]; if (s[1] < r[1]) r[1] = s[1]; if (s[2] > r[2]) r[2] = s[2]; if (s[3] > r[3]) r[3] = s[3]; return r; }
const now = () => performance.now();

// ---------- a drawing surface: the paper's noise (as port.js's surface, same seed order), the page and the stroke buffers ----------
function modSurface(w, h, seed) {
  const r = rng(seed), n = w * h;
  const s = { w, h, fine: valueNoise(w, h, 1.7, r), mid: valueNoise(w, h, 6, r), low: fbm(w, h, 44, r), blob: valueNoise(w, h, 16, r),
    L: new Float32Array(n), W: new Float32Array(n), D: new Float32Array(n), page: new Float32Array(n).fill(PAPER_TONE), base: new Float32Array(n).fill(PAPER_TONE) };
  s.scratch = s.L;
  return s;
}
// Buffers only some brushes need, made the first time they're asked for.
function lazyBuf(s, name, fill) { if (!s[name]) { s[name] = new Float32Array(s.w * s.h); if (fill) s[name].fill(fill); } return s[name]; }
function clearBuf(s, buf, r, v = 0, pad = 0) {
  if (!buf || emptyRect(r)) return;
  const x0 = Math.max(0, r[0] - pad), x1 = Math.min(s.w - 1, r[2] + pad);
  for (let y = Math.max(0, r[1] - pad); y <= Math.min(s.h - 1, r[3] + pad); y++) buf.fill(v, y * s.w + x0, y * s.w + x1 + 1);
}
function withScratch(surf, buf, fn) { const keep = surf.scratch; surf.scratch = buf; try { return fn(); } finally { surf.scratch = keep; } }
// kept (made by the first stroke that isn't wholly erasable): per pixel, the tone the eraser leaves; keptBase, as the
// stroke found it. Without one, the eraser leaves paper everywhere, as before.
function blankPage(s) { s.page.fill(PAPER_TONE); s.base.fill(PAPER_TONE); if (s.fill) s.fill.fill(0); s.kept = null; s.keptBase = null; }

// ---------- tilt, as BrushDirection.java (copied from brush-experiments) ----------
const MIN_LEAN = Math.tan(5 * DEG);
function tiltOf(tx, ty) {
  if (!Number.isFinite(tx) || !Number.isFinite(ty) || Math.abs(tx) > 90 || Math.abs(ty) > 90) return null;
  const x = Math.tan(clamp(tx, -89.9, 89.9) * DEG), y = Math.tan(clamp(ty, -89.9, 89.9) * DEG), m = Math.hypot(x, y);
  const incl = Math.atan(m) / DEG;
  return { incl, lean: clamp((incl - 5) / 60), dir: m < MIN_LEAN ? null : Math.atan2(y, x) };
}
// Tilt → size's factor and Tilt → angle's flattening at a lean (the app's ResolvedBrush.Pen tiltWidth, tiltFlat). The
// hand's Tilt Range Wide (b.tiltWide) is the Fude pen's: FINE of the width held at STEEP degrees or steeper, all of it at
// the NATURAL 45, BROAD times laid at LOW or lower; its nib round to 45, then flattening (eased in) to FLATTEST at 60.
const TILT_RANGE = { STEEP: 30, NATURAL: 45, LOW: 60, FINE: .12, BROAD: 40 * .12, FLATTEST: .2 };
function tiltWidth(b, lean) {
  if (!b.tiltWide) return 1 + 1.2 * (b.tiltSize || 0) * lean;
  const R = TILT_RANGE, deg = 5 + 60 * lean;
  const share = deg <= R.NATURAL ? R.FINE + (1 - R.FINE) * clamp((deg - R.STEEP) / (R.NATURAL - R.STEEP)) : 1 + (R.BROAD - 1) * clamp((deg - R.NATURAL) / (R.LOW - R.NATURAL));
  return 1 + (b.tiltSize || 0) * (share - 1);
}
function tiltFlat(b, lean) {
  if (!b.tiltWide) return 1 - 0.45 * (b.tiltAngle || 0) * lean;
  const past = clamp((5 + 60 * lean - TILT_RANGE.NATURAL) / (TILT_RANGE.LOW - TILT_RANGE.NATURAL));
  return 1 - (1 - TILT_RANGE.FLATTEST) * (b.tiltAngle || 0) * past * past;
}
// Tilt Shading (the app's ResolvedBrush.Pen.shade): none through 35 degrees, so the natural hold stays a crisp line,
// easing in to all of the hand's Tilt Shading at 65 and lower; laid there SIDE.WIDTH times as wide again, a little lighter
// and more diffused: SIDE.LIGHT of its darkness lifted, its edge soft over SIDE.SOFT of the nib's radius (a stick's fuzzy
// edge SIDE.FEATHER wider again), the paper's valleys skipped (sideTooth) and faint streaks along it (sideStreak), a dab
// laid every SIDE.STEP of its width. About two thirds of the upright mark's darkness at the same pressure.
const SIDE = { WIDTH: 5, LIGHT: .05, SOFT: .8, FEATHER: .5, SKIP: .35, BAND: .05, STREAK: .1, STEP: .2 };
const sideShade = (b, lean) => b.side > 0 && lean > .5 ? b.side * smoothstep(.5, 1, lean) : 0;
// The side rides the paper's peaks and skips its valleys (fine grain 0 to 1) below SIDE.SKIP of the way up, the lower it lies the more.
const sideTooth = (fine, shade) => smoothstep(SIDE.SKIP * shade - SIDE.BAND, SIDE.SKIP * shade + SIDE.BAND, fine);
// Streaks along the stroke (across: -1 to 1, edge to edge), from the brush's own draws, 9 across, at most SIDE.STREAK lighter.
const sideStreak = (draws, shade, across) => { const f = clamp((across + 1) / 2, 0, 0.9999) * 9, i = f | 0; return 1 - SIDE.STREAK * shade * lerp(draws[i], draws[i + 1], f - i); };
function turnToward(a, target, k) {
  let d = (target - a) % Math.PI; if (d > Math.PI / 2) d -= Math.PI; if (d < -Math.PI / 2) d += Math.PI;
  return a + d * k;
}
// The port's addPoint, plus tilt, a smoothed travel direction and the running peak pressure.
function addPt(pts, x, y, p, t, tx, ty) {
  if (!addPoint(pts, x, y, p, t)) return false;
  const q = pts[pts.length - 1], o = pts[pts.length - 2];
  q.tx = tx; q.ty = ty;
  q.pk = Math.max(o ? o.pk : 0, p);
  if (o) {
    const seg = q.d - o.d, ux = (q.x - o.x) / seg, uy = (q.y - o.y) / seg;
    if (o.ux === undefined) { o.ux = ux; o.uy = uy; q.ux = ux; q.uy = uy; }
    else {
      const k = 1 - Math.exp(-seg / 12);
      let sx = o.ux + (ux - o.ux) * k, sy = o.uy + (uy - o.uy) * k; const m = Math.hypot(sx, sy) || 1;
      q.ux = sx / m; q.uy = sy / m;
    }
  }
  return true;
}
// Flick (newmods.js; the app's StrokePoints.flickWidth and flickDry): past pts.flick.from the stroke carries on past its
// lift over pts.flick.len px, narrowing as (1 − u)^.75 and running dry from 30% of the way, .8 of its bristles at its end.
const flickU = (fl, d) => d <= fl.from ? 0 : Math.min(1, (d - fl.from) / fl.len);
const flickWidth = (fl, d) => { const u = flickU(fl, d); return u <= 0 ? 1 : Math.pow(1 - u, .75); };
const flickDry = (fl, d) => smoothstep(.3, 1, flickU(fl, d)) * .8;
// Size scaling past the sizes today's engine was tuned for (pens 16 px, paints 220 px); 1 at or under them.
const inkBig = (b, size) => b.scaleAware ? Math.max(1, size / 16) : 1;
const paintBig = (b, size) => b.scaleAware ? Math.max(1, Math.sqrt(size / 220)) : 1;
function taperLens(b, size) { const k = Math.sqrt(inkBig(b, size)); return [b.taperIn * 70 * k, b.taperOut * 90 * k]; }

// ---------- the hand: how pressure, tilt and speed change darkness (size and shape are inside each stamper) ----------
// Each factor is exactly 1 when its cell is off.
function handDark(b, pr, v, lean) {
  let k = 1;
  if (b.pDark) k *= 1 - 0.8 * b.pDark * (1 - pr);
  if (b.sDark) k *= 1 - 0.55 * b.sDark * clamp(v / 2.5);
  if (b.tDark && lean) k *= 1 - 0.45 * b.tDark * lean;
  return k;
}

// ---------- charcoal's own grain: a seeded, tileable 512 px value noise (copied from brush-experiments) ----------
function tileNoise(N, cell, r) {
  const G = Math.max(2, Math.round(N / cell)), g = new Float32Array(G * G), out = new Float32Array(N * N);
  for (let i = 0; i < g.length; i++) g[i] = r();
  for (let y = 0; y < N; y++) {
    const fy = y / N * G, iy = fy | 0, ty = fy - iy, sy = ty * ty * (3 - 2 * ty), y0 = iy % G, y1 = (iy + 1) % G;
    for (let x = 0; x < N; x++) {
      const fx = x / N * G, ix = fx | 0, tx = fx - ix, sx = tx * tx * (3 - 2 * tx), x0 = ix % G, x1 = (ix + 1) % G;
      out[y * N + x] = lerp(lerp(g[y0 * G + x0], g[y0 * G + x1], sx), lerp(g[y1 * G + x0], g[y1 * G + x1], sx), sy);
    }
  }
  return out;
}
const GRAINS = new Map();
function stickGrain(seed) {
  let s = GRAINS.get(seed);
  if (!s) {
    const r = rng((seed ^ 0xc4a2c0a1) >>> 0);
    s = { fine: tileNoise(512, 1.6, r), mid: tileNoise(512, 7, r), low: tileNoise(512, 36, r) };
    GRAINS.set(seed, s);
    if (GRAINS.size > 6) GRAINS.delete(GRAINS.keys().next().value);
  }
  return s;
}
const grainAt = (px, py) => ((py & 511) << 9) | (px & 511);

// ===================================================================================================================
// The line: the port's stampInk (via brush-experiments' stampInkX) plus tilt, charcoal spread, size scaling, the Fast
// taper, a Fast-wet window, and the hand's darkness, nib-width and speed-angle inputs. Writes surf.scratch.
// clip: [l, t, r, b]; meta.widest records the widest reach; win: lay only dabs with dMin <= d < dMax, win.tail tapers them.
// ===================================================================================================================
function stampLine(surf, b, pts, size, final, from, clip, meta, win) {
  const { w, h, scratch: buf, fine, mid } = surf;
  const total = pts.length ? pts[pts.length - 1].d : 0;
  const big = inkBig(b, size), bigS = Math.sqrt(big);
  const rnd = rng(b.seed ^ 0x9e37);
  const nBrMax = b.scaleAware ? 160 : 48;
  const bristles = []; for (let i = 0; i < nBrMax; i++) bristles.push(rnd());
  const [inLen, outLen] = taperLens(b, size), spread = b.fuzz || 0, dryOn0 = b.dry > 0, fl = pts.flick;
  const tiltOn = (b.tiltSize || 0) + (b.tiltAngle || 0) + (b.tDark || 0) + (b.side || 0) > 0 || !!b.filbert;
  const G = spread > 0 ? stickGrain(b.seed) : null;
  let rect = [w, h, 0, 0];
  // Big nibs lay a dab only where the stroke crosses a fixed distance grid: a twelfth of a nib, or closer so the
  // scallops between dabs dip under a sixteenth of the edge at the nib's sharpest bend (a crisp nib's pixel, a stick's
  // fuzz), as the app's LineStamper (2026-10-29: a big line's edge smooth, not a repeat). The grid depends on distance
  // alone, so the Fast-wet window's re-lay lands the same dabs. At 16 px and under every step lays a dab, as the port does.
  const rim = Math.max(1, size / 2 * 1.5 * spread), bend = Math.max(0.8, size / 2 * Math.min(1, b.aspect) * Math.min(1, b.aspect));
  const gridSp = big > 1 || b.sizes ? Math.min(size * 0.08, Math.max(0.8, Math.min(size * 0.12, Math.sqrt(8 / 16 * bend * rim)))) : 0;
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    const dirx = seg > 0 ? (p1.x - p0.x) / seg : 1, diry = seg > 0 ? (p1.y - p0.y) / seg : 0;
    // Tilt Shading: a nib laid on its side is stepped as its width (its soft edge hides the steps).
    const T1 = b.side > 0 ? tiltOf(p1.tx, p1.ty) : null, shade1 = T1 ? sideShade(b, T1.lean) : 0;
    // A crisp nib steps a grid at most, so every grid line gets its dab.
    let stepLen = Math.max(0.4, (shade1 > 0 ? size * (1 + SIDE.WIDTH * shade1) : size) * 0.1);
    if (gridSp > 0 && shade1 === 0 && spread === 0) stepLen = Math.min(stepLen, gridSp);
    const steps = Math.max(1, Math.ceil(seg / stepLen));
    for (let s = (k === 1 ? 0 : 1); s <= steps; s++) {
      const t = s / steps, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), d = lerp(p0.d, p1.d, t);
      if (win && (d < win.dMin || d >= win.dMax)) continue;
      const pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t);
      let wd = size * lerp(1 - 0.75 * b.pressure, 1 + 0.6 * b.pressure, pr);
      wd *= clamp(1 + b.speed * 0.55 * (clamp(v / 2.5) - 0.3), 0.35, 1.6);
      if (inLen > 0) wd *= Math.pow(clamp(d / inLen), 0.6) * 0.92 + 0.08;
      // Flick: past the lift the stroke narrows to nothing and runs dry (newmods.js, the app's FlickFling).
      const fdry = fl ? flickDry(fl, d) : 0, dryOn = dryOn0 || fdry > 0;
      if (fl) wd *= flickWidth(fl, d);
      if (b.fast) {
        if (b.taperOut > 0) {
          const fall = p0.pk > 0 ? clamp(pr / (p0.pk * 0.7)) : 1, flick = clamp((v - 1.2) / 2);
          wd *= lerp(1, Math.pow(fall, 0.8) * 0.92 + 0.08, b.taperOut) * (1 - 0.8 * b.taperOut * flick);
        }
      } else if (final && outLen > 0) wd *= Math.pow(clamp((total - d) / outLen), 0.6) * 0.92 + 0.08;
      if (win && win.tail) wd *= lerp(1, Math.pow(clamp((total - d) / win.tail.len), 0.6) * 0.92 + 0.08, win.tail.amt);
      const xi = clamp((x / big) | 0, 0, w - 1), yi = clamp((y / big) | 0, 0, h - 1);
      if (b.wobble > 0) wd *= 1 + b.wobble * 0.7 * (mid[yi * w + xi] - 0.5);
      if (b.pool > 0) {
        const grw = b.pool * 0.9 * (1 - clamp(v / 0.25)) ** 2;
        wd = big > 1 ? wd + Math.min(wd * grw, 16 * grw * bigS) : wd * (1 + grw);
      }
      let ang = b.angle * DEG, flat = 1, lean = 0, shade = 0;
      if (tiltOn) {
        const T = tiltOf(lerp(p0.tx, p1.tx, t), lerp(p0.ty, p1.ty, t));
        if (T) {
          lean = T.lean;
          wd *= tiltWidth(b, lean);
          // Tilt Shading: laid low, the side of the nib (a pencil's lead).
          shade = sideShade(b, lean);
          if (shade > 0) wd *= 1 + SIDE.WIDTH * shade;
          if (b.tiltAngle > 0 && T.dir !== null) ang = turnToward(ang, T.dir + Math.PI / 2, b.tiltAngle);
          // A Filbert's broad side lies across the pen's lean.
          if (b.filbert && T.dir !== null) ang = T.dir + Math.PI / 2;
          flat = tiltFlat(b, lean);
        }
      }
      // Speed turns the nib to follow a quick stroke, broad side across it.
      if (b.sAngle > 0) ang = turnToward(ang, Math.atan2(diry, dirx) + Math.PI / 2, b.sAngle * clamp(v / 1.5));
      const cosA = Math.cos(ang), sinA = Math.sin(ang);
      const a = Math.max(0.6, wd / 2);
      let bb = Math.max(0.6, Math.min(a, Math.max(1, a * b.aspect * flat)));
      // Pressure to nib width: a round tip squashes into an oval, a shaped nib opens wider.
      if (b.pWidth > 0) bb = b.aspect >= 1 ? Math.max(0.6, bb * (1 - 0.45 * b.pWidth * pr)) : Math.max(0.6, Math.min(a, bb * (1 + 1.2 * b.pWidth * (pr - 0.4))));
      let dk = handDark(b, pr, v, lean);
      if (shade > 0) dk *= 1 - SIDE.LIGHT * shade;
      // Paint Load: what of its own paint the brush still lays here (stroke.js oilSpend).
      if (p1.own !== undefined) dk *= lerp(p0.own, p1.own, t);
      // Tilt Shading: a nib on its side lays a dab every SIDE.STEP of its width (its soft edge and grain hide the scallops).
      const grid = shade > 0 ? Math.max(gridSp, 2 * a * SIDE.STEP) : gridSp;
      if (grid && !(k === 1 && s === 0) && !(final && k === pts.length - 1 && s === steps) && Math.floor(d / grid) === Math.floor((d - seg / steps) / grid)) continue;
      let sw = spread > 0 ? Math.max(1, spread * (bb * 1.4 + a * 0.12)) : 0;
      // Tilt Shading: a stick laid on its side feathers its fuzzy edge further.
      if (sw && shade > 0) sw *= 1 + SIDE.FEATHER * shade;
      const load = 1 - b.dry * (0.35 + 0.8 * clamp(d / 600) + 0.7 * clamp(v / 2.5) - 0.6 * pr) - fdry;
      const press = spread > 0 ? lerp(1, 0.3 + 0.7 * pr * pr, spread) : 1;
      const nBr = Math.min(nBrMax, 6 + Math.round(a));
      // Tilt Shading: its edge soft over this many px.
      const soft = shade > 0 ? 1 + SIDE.SOFT * shade * a : 0;
      const R = Math.ceil(a + 0.7 * sw + 1.5 + 0.5 * soft);
      if (meta && R > (meta.widest || 0)) meta.widest = R;
      const cl = clip ? clip[0] : 0, ct = clip ? clip[1] : 0, cr = clip ? clip[2] : w - 1, cb = clip ? clip[3] : h - 1;
      const x0 = Math.max(cl, Math.floor(x - R)), x1 = Math.min(cr, Math.ceil(x + R));
      const y0 = Math.max(ct, Math.floor(y - R)), y1 = Math.min(cb, Math.ceil(y + R));
      if (x1 >= x0 && y1 >= y0) {
        for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
          const i = py * w + px;
          if (buf[i] >= 1) continue;
          const dx = px + 0.5 - x, dy = py + 0.5 - y;
          const u = dx * cosA + dy * sinA, q = -dx * sinA + dy * cosA;
          const dist = Math.sqrt((u / a) * (u / a) + (q / bb) * (q / bb));
          const gu = u / (a * a), gq = q / (bb * bb), g = Math.sqrt(gu * gu + gq * gq) / Math.max(dist, 1e-6);
          let cov;
          if (sw) {
            const gi = grainAt(px, py), gm = G.mid[gi], gl = G.low[gi];
            const e = (1 - dist) / Math.max(g, 1e-6) + sw * spread * (1.1 * (gm - 0.5) + 1.8 * (gl - 0.5));
            const f = clamp((e + 0.5 * sw) / sw);
            cov = f * f * (3 - 2 * f) * press * clamp(1 + 1.2 * spread * (gl - 0.5) + 0.5 * spread * (gm - 0.5), 0.2, 1.2);
          } else if (soft > 0) cov = clamp(0.5 - (dist - 1) / Math.max(g, 1e-6) / soft);
          else cov = clamp(0.5 - (dist - 1) / Math.max(g, 1e-6));
          // Tilt Shading: the side skips the paper's valleys and lays in streaks.
          if (shade > 0) cov *= sideTooth(fine[i], shade) * sideStreak(bristles, shade, (-dx * diry + dy * dirx) / a);
          if (cov <= 0) continue;
          if (dryOn) {
            const across = clamp(((-dx * diry + dy * dirx) / a + 1) / 2, 0, 0.9999);
            if (bristles[(across * nBr) | 0] > load + 0.25 * ((G ? G.fine[grainAt(px, py)] : fine[i]) - 0.5)) cov = 0;
          }
          if (dk !== 1) cov *= dk;
          if (cov > buf[i]) buf[i] = cov;
        }
        if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
      }
      // Dust: specks knocked off around the stick, seeded by where the dab is, so a re-lay lays the same ones.
      if (spread > 0) {
        const dr = rng((b.seed ^ Math.imul(Math.round(d * 4), 2654435761)) >>> 0), n = Math.min(24, Math.round(spread * (1.5 + a * 0.08)));
        for (let m = 0; m < n; m++) {
          const th = dr() * 2 * Math.PI, rr = 1.02 + dr() * dr() * 1.3, su = Math.cos(th) * (a + 0.5 * sw) * rr, sq = Math.sin(th) * (bb + 0.5 * sw) * rr;
          const sx = x + su * cosA - sq * sinA, sy = y + su * sinA + sq * cosA, sr = 0.5 + dr() * dr() * 1.6, sc = (0.6 + 0.4 * dr()) * dk;
          const X0 = Math.max(cl, Math.floor(sx - sr - 1)), X1 = Math.min(cr, Math.ceil(sx + sr + 1)), Y0 = Math.max(ct, Math.floor(sy - sr - 1)), Y1 = Math.min(cb, Math.ceil(sy + sr + 1));
          if (X1 < X0 || Y1 < Y0) continue;
          for (let py = Y0; py <= Y1; py++) for (let px = X0; px <= X1; px++) {
            const ex = px + 0.5 - sx, ey = py + 0.5 - sy, c = clamp(sr - Math.sqrt(ex * ex + ey * ey) + 0.5) * sc, i = py * w + px;
            if (c > buf[i]) buf[i] = c;
          }
          if (X0 < rect[0]) rect[0] = X0; if (Y0 < rect[1]) rect[1] = Y0; if (X1 > rect[2]) rect[2] = X1; if (Y1 > rect[3]) rect[3] = Y1;
        }
      }
    }
  }
  if (b.fast && b.splatter > 0) splatterLive(surf, b, pts, size, from, rect);
  else if (final && b.splatter > 0) {
    if (b.scaleAware && big > 1) { const rr = rng((b.seed ^ Math.imul(pts.length, 2654435761) ^ Math.round(total * 16)) >>> 0); for (let k = 1; k < pts.length; k++) splatterSeg(surf, b, pts, size, k, rr, rect); }
    else splatter(surf, b, pts, size, rect);
  }
  return rect;
}
function splatterLive(surf, b, pts, size, from, rect) {
  for (let k = Math.max(1, from); k < pts.length; k++) splatterSeg(surf, b, pts, size, k, rng((b.seed ^ Math.imul(k, 2654435761)) >>> 0), rect);
}
function splatterSeg(surf, b, pts, size, k, rnd, rect) {
  const { w, h, scratch: buf } = surf, big = inkBig(b, size), bigS = Math.sqrt(big);
  const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
  if (p1.v < 0.8 || seg <= 0 || rnd() > b.splatter * 0.025 * (p1.v - 0.8) * seg) return;
  const dirx = (p1.x - p0.x) / seg, diry = (p1.y - p0.y) / seg;
  const reach = big > 1 ? bigS : 1, dropSize = big > 1 ? Math.min(size, 24) * bigS : size;
  for (let n = 1 + ((rnd() * 3) | 0); n > 0; n--) {
    const ahead = (6 + rnd() * 34 * p1.v) * reach + (big > 1 ? size * 0.5 : 0), side = (rnd() - 0.5) * 18 * reach;
    const x = p1.x + dirx * ahead - diry * side, y = p1.y + diry * ahead + dirx * side;
    const r = Math.max(0.7, (0.3 + rnd() * rnd() * 1.4) * dropSize / 5), R = Math.ceil(r + 1);
    const x0 = Math.max(0, Math.floor(x - R)), x1 = Math.min(w - 1, Math.ceil(x + R));
    const y0 = Math.max(0, Math.floor(y - R)), y1 = Math.min(h - 1, Math.ceil(y + R));
    if (x1 < x0 || y1 < y0) continue;
    for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
      const ex = px + 0.5 - x, ey = py + 0.5 - y, cov = clamp(r - Math.sqrt(ex * ex + ey * ey) + 0.5), i = py * w + px;
      if (cov > buf[i]) buf[i] = cov;
    }
    if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
  }
}

// ===================================================================================================================
// The body of paint: the port's stampPaint (via stampPaintX) plus tilt, a nib ellipse and tapers (pen-paint-blend's
// stampWash), opaque edges, Fast's live granulation, the wet window's distance map, and the hand's darkness.
// ===================================================================================================================
function paintDiameter(b, size, pr, T) {
  const press = Math.pow(clamp(pr), 1.2), broad = Math.min(1, press * (1 + 2 * (T ? T.lean : 0)));
  if (b.head === "flat") return size * (0.8 + 0.2 * broad);
  const tip = lerp(0.6, 0.06, b.pressure);
  return size * (tip + (1 - tip) * broad);
}
// How far apart, at most, a Filbert of diameter D lays its dabs (the app's BodyStamper.filbertSpacing): a round dab's
// scallop rule for its oval's sharpest bend (r × aspect²); a crescent's a fifth of its short half-axis too.
function filbertSpacing(b, D, softK) {
  const r = Math.max(0.8, D / 2), edge = Math.max(1, r * softK), bend = Math.max(0.8, r * b.aspect * b.aspect), most = Math.max(0.8, Math.sqrt(2 * bend * edge));
  return b.crescent ? Math.min(most, Math.max(0.8, .2 * Math.max(0.8, r * b.aspect))) : most;
}
let WET = null, WET_D = 0;   // while a Fast-wet body stamps: each pixel's distance along the stroke of its latest dab
function stampBody(surf, b, pts, size, from, meta, final) {
  const { w, h, scratch: buf, fine, mid } = surf;
  let rect = [w, h, 0, 0];
  const flatHead = b.head === "flat", tilt = b.tilt || 0, softK = lerp(lerp(0.08, 0.012, clamp(b.cover || 0)), 0.6, b.soft);
  if (flatHead) return stampHake(surf, b, pts, size, meta, softK);
  const hairs = []; if (b.streaks > 0) { const rnd = rng(b.seed ^ 0x51ed); for (let i = 0; i < 130; i++) hairs.push(rnd()); }
  const liveGran = b.fast && b.gran > 0, gran = b.gran, amount = b.amount ?? 1, ellipse = b.aspect !== undefined && b.aspect < 1;
  const total = pts.length ? pts[pts.length - 1].d : 0, grw = Math.max(1, size / 24);
  const inLen = (b.taperIn || 0) * 70 * grw, outLen = (b.taperOut || 0) * 90 * grw;
  const gap = b.sizes || paintBig(b, size) > 1 ? (meta || { lastD: -Infinity }) : null;
  if (gap && gap.lastD === undefined) gap.lastD = -Infinity;
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    const dirx = seg > 0 ? (p1.x - p0.x) / seg : 1, diry = seg > 0 ? (p1.y - p0.y) / seg : 0;
    const T1 = tilt > 0 ? tiltOf(p1.tx, p1.ty) : null;
    const D1 = paintDiameter(b, size, p1.p, T1);
    let spacing = Math.max(0.8, D1 * 0.12);
    if (b.filbert) spacing = Math.min(spacing, filbertSpacing(b, D1, softK));
    const steps = Math.max(1, Math.ceil(seg / spacing));
    for (let s = (k === 1 ? 0 : 1); s <= steps; s++) {
      const t = s / steps, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), d = lerp(p0.d, p1.d, t);
      if ((b.skipStart || b.skipEnd) && (d < b.skipStart || (final && d > total - b.skipEnd))) continue;
      const T = tilt > 0 || b.tDark || b.filbert ? tiltOf(lerp(p0.tx, p1.tx, t), lerp(p0.ty, p1.ty, t)) : null;
      const D = paintDiameter(b, size, pr, tilt > 0 ? T : null);
      if (gap && d - gap.lastD < (b.filbert ? Math.min(Math.max(0.8, D * 0.12), filbertSpacing(b, D, softK)) : Math.max(0.8, D * 0.12))) continue;
      if (gap) gap.lastD = d;
      let tf = 1;
      if (inLen > 0) tf *= Math.pow(clamp(d / inLen), 0.6) * 0.92 + 0.08;
      if (final && outLen > 0) tf *= Math.pow(clamp((total - d) / outLen), 0.6) * 0.92 + 0.08;
      if (pts.flick) tf *= flickWidth(pts.flick, d);
      const v = lerp(p0.v, p1.v, t);
      let dens = b.mode === "wet" ? lerp(0.7, 1, pr) : 1;
      if (amount !== 1) dens *= amount;
      const dk = handDark(b, pr, v, T ? T.lean : 0);
      if (dk !== 1) dens *= dk;
      // Paint Load: what of its own paint the brush still lays here; none left, no dab.
      if (p1.own !== undefined) { dens *= lerp(p0.own, p1.own, t); if (!(dens > 0)) continue; }
      let r = Math.max(0.8, D / 2);
      if (tf !== 1) r = Math.max(0.8, r * tf);
      if (b.speed) r *= clamp(1 + b.speed * 0.55 * (clamp(v / 2.5) - 0.3), 0.35, 1.6);
      const edge = Math.max(1, r * softK);
      let A = r, B = r, th = 0;
      if (b.filbert) {
        // A Filbert: its oval's broad side across the pen's lean, tilt's lean stretching it too; its Nib Angle without a reading.
        const reads = T && T.dir !== null;
        if (reads && tilt > 0 && T.lean > 0) A = r * (1 + 0.6 * tilt * T.lean);
        B = Math.max(0.8, r * b.aspect); th = reads ? T.dir + Math.PI / 2 : b.angle * DEG;
      } else if (tilt > 0 && T && T.dir !== null && T.lean > 0) { A = r * (1 + 0.6 * tilt * T.lean); B = r * (1 - 0.35 * tilt * T.lean); th = T.dir + Math.PI / 2; }
      else if (ellipse) { B = Math.max(0.8, r * b.aspect); th = b.angle * DEG; }
      const R = Math.ceil(A + 1), c = Math.cos(th), sn = Math.sin(th);
      // A Filbert's crescent (Paint Load; MonoPaint's BrushStamp.crescent): its oval less a slightly shorter one (.85 as long, as
      // thick) set half the oval's reach toward the way it moves, so the hollow faces it and a first touch is a sliver.
      let biteA = 0, biteB = 0, biteU = 0, biteQ = 0, biteK = 0;
      if (b.crescent && A !== B) {
        const tu = dirx * c + diry * sn, tq = -dirx * sn + diry * c, reach = Math.sqrt(A * tu * A * tu + B * tq * B * tq);
        biteU = .5 * reach * tu; biteQ = .5 * reach * tq; biteA = .85 * A; biteB = B; biteK = Math.sqrt(biteA * biteB) / edge;
      }
      const x0 = Math.max(0, Math.floor(x - R)), x1 = Math.min(w - 1, Math.ceil(x + R));
      const y0 = Math.max(0, Math.floor(y - R)), y1 = Math.min(h - 1, Math.ceil(y + R));
      const nH = Math.min(120, 6 + A / 2.5);
      for (let py = y0; py <= y1; py++) {
        const dy = py + 0.5 - y;
        let xa = x0, xb = x1;
        if (A === B) { const span = r * r - dy * dy; if (span <= 0) continue; const sx = Math.sqrt(span); xa = Math.max(x0, Math.floor(x - 0.5 - sx)); xb = Math.min(x1, Math.ceil(x - 0.5 + sx)); }
        for (let px = xa; px <= xb; px++) {
          const i = py * w + px;
          if (buf[i] >= dens && !liveGran) continue;
          const dx = px + 0.5 - x;
          let cov;
          if (A === B) cov = clamp((r - Math.sqrt(dx * dx + dy * dy)) / edge) * dens;
          else {
            const u = dx * c + dy * sn, q = -dx * sn + dy * c, nd = Math.sqrt((u / A) * (u / A) + (q / B) * (q / B)); cov = clamp((1 - nd) * Math.sqrt(A * B) / edge) * dens;
            if (biteA > 0 && cov > 0) { const bu = (u - biteU) / biteA, bq = (q - biteQ) / biteB; cov *= 1 - clamp((1 - Math.sqrt(bu * bu + bq * bq)) * biteK); }
          }
          if (cov <= 0) continue;
          if (b.streaks > 0) {
            const f = clamp(((-dx * diry + dy * dirx) / A + 1) / 2) * nH, h0 = f | 0, tt = f - h0;
            cov *= 1 - b.streaks * 0.75 * lerp(hairs[h0], hairs[h0 + 1], tt * tt * (3 - 2 * tt));
          }
          if (liveGran) cov *= clamp(1 + gran * 1.5 * (fine[i] - 0.5) + gran * 0.6 * (mid[i] - 0.5), 0, 1.6);
          if (cov > buf[i]) buf[i] = cov;
          if (WET && cov > 0) WET[i] = d;
        }
      }
      if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
    }
  }
  return rect;
}

// ---------- the Hake: a wide flat head swept as a ribbon (copied from brush-experiments v5) ----------
// The hake's start waits until the stroke's direction settles, then lies along it (the app's HakeRibbon.settle; the
// owner, 2026-10-22: "the hake brush struggles to find its direction and often leaves a mark that doesn't match at the
// beginning"): a third of the size, kept between 24 and 40 px.
const hakeSettle = size => Math.max(24, Math.min(40, size * 0.3));
function hakePointAt(pts, at) {
  const n = pts.length; if (n < 2) return pts[0];
  let k = 1; while (k < n - 1 && pts[k].d < at) k++;
  const seg = pts[k].d - pts[k - 1].d, t = seg > 0 ? clamp((at - pts[k - 1].d) / seg) : 1;
  return { x: lerp(pts[k - 1].x, pts[k].x, t), y: lerp(pts[k - 1].y, pts[k].y, t) };
}
// The direction the first upTo px settle on: from a quarter of the way along to the end (the landing's wobble and hook left out); NaN if it hasn't moved.
function hakeStartTravel(pts, upTo) {
  const a = hakePointAt(pts, upTo * .25), z = hakePointAt(pts, upTo);
  let dx = z.x - a.x, dy = z.y - a.y;
  if (Math.hypot(dx, dy) < 1e-6) { dx = z.x - pts[0].x; dy = z.y - pts[0].y; }
  return Math.hypot(dx, dy) < 1e-6 ? NaN : Math.atan2(dy, dx);
}
// The travel a following head turns by at d: the settled start over the first settle px, eased into the measured one over as far again.
function hakeTravelAt(start, settle, d, measured) {
  if (Number.isNaN(start) || d >= 2 * settle) return measured;
  if (d <= settle) return start;
  let dt = measured - start; dt = Math.atan2(Math.sin(dt), Math.cos(dt));
  return start + dt * (d - settle) / settle;
}
function stampHake(surf, b, pts, size, meta, softK) {
  const { w, h } = surf, n = pts.length;
  const M = meta || {}, H = M.hake || (M.hake = { next: 0, prev: null, th: null, k: 1, start: NaN, settle: 0 });
  const rect = [w, h, 0, 0];
  if (n < 2) return rect;
  const total = pts[n - 1].d, tilt = b.tilt || 0, follow = b.headTurn !== "fixed";
  const hairs = []; { const rnd = rng(b.seed ^ 0x51ed); for (let i = 0; i < 130; i++) hairs.push(rnd()); }
  const liveGran = b.fast && b.gran > 0, gran = b.gran;
  if (H.th === null) {
    const need = Math.max(6, size * 0.05);
    if (total < need) return rect;
    // A following head waits until the way the stroke goes has settled (H.lift: the pen lifted first), then lays its
    // start along it from the first sample, its first angle the one it turns to there, the pen's lean included.
    if (follow) {
      const settle = hakeSettle(size);
      if (total < settle && !H.lift) return rect;
      H.settle = settle; H.start = hakeStartTravel(pts, Math.min(settle, total));
      if (Number.isNaN(H.start)) H.start = 0;
    }
    let th0 = follow ? H.start + Math.PI / 2 : b.angle * DEG;
    const T0 = tiltOf(pts[0].tx, pts[0].ty);
    if (tilt > 0 && T0 && T0.dir !== null) th0 = turnToward(th0, T0.dir + Math.PI / 2, tilt);
    H.th = th0;
  }
  while (H.next <= total) {
    const at = H.next;
    while (H.k < n - 1 && pts[H.k].d < at) H.k++;
    const p0 = pts[H.k - 1], p1 = pts[H.k], seg = p1.d - p0.d, t = seg > 0 ? clamp((at - p0.d) / seg) : 1;
    const x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t);
    const T = tiltOf(lerp(p0.tx, p1.tx, t), lerp(p0.ty, p1.ty, t));
    const D = paintDiameter(b, size, pr, T), W = Math.max(1, D / 2), Q = Math.max(1.5, size * (0.03 + 0.05 * Math.pow(clamp(pr), 1.2)) * (1 + 0.8 * (b.pWidth || 0) * (pr - 0.4)));
    const step = H.prev ? at - H.prev.d : 0;
    const travel = hakeTravelAt(H.start ?? NaN, H.settle || 0, at, Math.atan2(lerp(p0.uy ?? 0, p1.uy ?? 0, t), lerp(p0.ux ?? 1, p1.ux ?? 1, t)));
    let target = follow ? travel + Math.PI / 2 : b.angle * DEG;
    if (tilt > 0 && T && T.dir !== null) target = turnToward(target, T.dir + Math.PI / 2, tilt);
    if (step > 0) {
      if (follow && H.travel !== undefined) {
        let dt = travel - H.travel; dt = Math.atan2(Math.sin(dt), Math.cos(dt));
        const radius = Math.abs(dt) > 1e-6 ? step / Math.abs(dt) : Infinity;
        H.th += dt * clamp((1.5 * W - radius) / (0.75 * W));
      }
      const lag = Math.max(40, W * 1.2), want = turnToward(H.th, target, 1 - Math.exp(-step / lag)) - H.th;
      H.th += clamp(want, -DEG * step, DEG * step);
    }
    H.travel = travel;
    const head = { x, y, th: H.th, W, Q, d: at, pr, c: Math.cos(H.th), s: Math.sin(H.th), own: p1.own === undefined ? 1 : lerp(p0.own, p1.own, t) };
    if (H.prev) {
      const A = H.prev, mx = x - A.x, my = y - A.y, m = Math.hypot(mx, my);
      if (m > 1e-6) {
        const dL = ((x + head.c * W) - (A.x + A.c * A.W)) * mx + ((y + head.s * W) - (A.y + A.s * A.W)) * my;
        const dR = ((x - head.c * W) - (A.x - A.c * A.W)) * mx + ((y - head.s * W) - (A.y - A.s * A.W)) * my;
        if (dL < 0 && dR >= 0) { head.x = A.x + A.c * A.W - head.c * W; head.y = A.y + A.s * A.W - head.s * W; }
        else if (dR < 0 && dL >= 0) { head.x = A.x - A.c * A.W + head.c * W; head.y = A.y - A.s * A.W + head.s * W; }
      }
    }
    let from = H.prev;
    if (from) {
      const end = Math.max(Math.hypot((head.x + head.c * W) - (from.x + from.c * from.W), (head.y + head.s * W) - (from.y + from.s * from.W)),
        Math.hypot((head.x - head.c * W) - (from.x - from.c * from.W), (head.y - head.s * W) - (from.y - from.s * from.W)));
      const parts = Math.min(24, Math.ceil(end / 6));
      for (let j = 1; j < parts; j++) {
        const f = j / parts, th = turnToward(from.th, head.th, f), Wj = lerp(from.W, W, f);
        const md = { x: lerp(from.x, head.x, f), y: lerp(from.y, head.y, f), th, W: Wj, Q: lerp(from.Q, Q, f), d: lerp(from.d, at, f), pr: lerp(from.pr, pr, f), c: Math.cos(th), s: Math.sin(th), own: lerp(from.own ?? 1, head.own, f) };
        hakeRibbon(surf, b, from, md, hairs, softK, liveGran, gran, rect);
        from = md;
      }
    }
    hakeHead(surf, b, head, hairs, softK, liveGran, gran, rect);
    if (from) hakeRibbon(surf, b, from, head, hairs, softK, liveGran, gran, rect);
    H.prev = head;
    H.next += Math.max(1.5, Math.min(Q, 2 + W * 0.03));
  }
  return rect;
}
function laneShare(hairs, sLane, W, st) {
  if (st <= 0) return 1;
  const f = clamp(sLane) * Math.min(120, 6 + W / 2.5), h0 = f | 0, tt = f - h0;
  return 1 - st * 0.75 * lerp(hairs[h0], hairs[h0 + 1], tt * tt * (3 - 2 * tt));
}
const runOut = (b, d) => b.streaks + (1 - b.streaks) * 0.7 * clamp(d / 2400);
const hakeDens = (b, pr) => (b.mode === "wet" ? lerp(0.7, 1, pr) : 1) * (b.amount ?? 1) * (b.pDark ? 1 - 0.8 * b.pDark * (1 - pr) : 1);
function hakePut(surf, i, cov, liveGran, gran) {
  if (liveGran) cov *= clamp(1 + gran * 1.5 * (surf.fine[i] - 0.5) + gran * 0.6 * (surf.mid[i] - 0.5), 0, 1.6);
  if (cov > surf.scratch[i]) surf.scratch[i] = cov;
  if (WET) WET[i] = WET_D;
}
function hakeHead(surf, b, hd, hairs, softK, liveGran, gran, rect) {
  const { w, h } = surf, { x, y, W, Q, c, s: sn } = hd, edgeU = Math.max(1, W * softK * 0.5);
  const dens = hakeDens(b, hd.pr) * (hd.own ?? 1), st = runOut(b, hd.d);
  WET_D = hd.d;
  const ex = Math.abs(c) * W + Math.abs(sn) * Q + 1.5, ey = Math.abs(sn) * W + Math.abs(c) * Q + 1.5, U = W + 0.5, QQ = Q + 0.5;
  const x0 = Math.max(0, Math.floor(x - ex)), x1 = Math.min(w - 1, Math.ceil(x + ex)), y0 = Math.max(0, Math.floor(y - ey)), y1 = Math.min(h - 1, Math.ceil(y + ey));
  for (let py = y0; py <= y1; py++) {
    const dy = py + 0.5 - y;
    let lo = -ex, hi = ex;
    if (Math.abs(c) > 1e-6) { const a1 = (-U - dy * sn) / c, a2 = (U - dy * sn) / c; lo = Math.max(lo, Math.min(a1, a2)); hi = Math.min(hi, Math.max(a1, a2)); } else if (Math.abs(dy * sn) > U) continue;
    if (Math.abs(sn) > 1e-6) { const b1 = (dy * c - QQ) / sn, b2 = (dy * c + QQ) / sn; lo = Math.max(lo, Math.min(b1, b2)); hi = Math.min(hi, Math.max(b1, b2)); } else if (Math.abs(dy * c) > QQ) continue;
    if (hi < lo) continue;
    for (let px = Math.max(x0, Math.floor(x - 0.5 + lo)), xb = Math.min(x1, Math.ceil(x - 0.5 + hi)); px <= xb; px++) {
      const dx = px + 0.5 - x, u = dx * c + dy * sn, q = -dx * sn + dy * c;
      const au = Math.abs(u) / W, cov = clamp((W - Math.abs(u)) / edgeU) * clamp(Q * (1 - au ** 8) - Math.abs(q) + 0.5) * dens;
      if (cov > 0) hakePut(surf, py * w + px, cov * laneShare(hairs, (1 - u / W) / 2, W, st), liveGran, gran);
    }
  }
  if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
}
function hakeRibbon(surf, br, A, B, hairs, softK, liveGran, gran, rect) {
  const { w, h } = surf;
  WET_D = B.d;
  const ax = A.x + A.c * A.W, ay = A.y + A.s * A.W, bx = A.x - A.c * A.W, by = A.y - A.s * A.W;
  const cx = B.x - B.c * B.W, cy = B.y - B.s * B.W, dx = B.x + B.c * B.W, dy = B.y + B.s * B.W;
  const ex = bx - ax, ey = by - ay, fx = dx - ax, fy = dy - ay, gx = ax - bx + cx - dx, gy = ay - by + cy - dy;
  const k2 = gx * fy - gy * fx;
  const X = [ax, bx, cx, dx], Y = [ay, by, cy, dy];
  const y0 = Math.max(0, Math.floor(Math.min(ay, by, cy, dy)) - 1), y1 = Math.min(h - 1, Math.ceil(Math.max(ay, by, cy, dy)) + 1);
  let rx0 = w, rx1 = -1;
  for (let py = y0; py <= y1; py++) {
    const yc = py + 0.5;
    let lo = Infinity, hi = -Infinity;
    for (let e = 0; e < 4; e++) {
      const x1e = X[e], y1e = Y[e], x2e = X[(e + 1) & 3], y2e = Y[(e + 1) & 3];
      if ((yc < Math.min(y1e, y2e) - 1) || (yc > Math.max(y1e, y2e) + 1)) continue;
      const xe = Math.abs(y2e - y1e) < 1e-6 ? null : x1e + (x2e - x1e) * clamp((yc - y1e) / (y2e - y1e));
      if (xe === null) { lo = Math.min(lo, x1e, x2e); hi = Math.max(hi, x1e, x2e); } else { lo = Math.min(lo, xe); hi = Math.max(hi, xe); }
    }
    if (hi < lo) continue;
    const xa = Math.max(0, Math.floor(lo - 1.5)), xb = Math.min(w - 1, Math.ceil(hi + 1.5));
    for (let px = xa; px <= xb; px++) {
      const hx = px + 0.5 - ax, hy = yc - ay;
      const k1 = ex * fy - ey * fx + hx * gy - hy * gx, k0 = hx * ey - hy * ex;
      let s = -1, t = -1;
      const solve = v => Math.abs(ex + gx * v) > Math.abs(ey + gy * v) ? (hx - fx * v) / (ex + gx * v) : (hy - fy * v) / (ey + gy * v);
      if (Math.abs(k2) < 1e-9) { if (Math.abs(k1) < 1e-12) continue; t = -k0 / k1; s = solve(t); }
      else {
        let disc = k1 * k1 - 4 * k0 * k2; if (disc < 0) continue; disc = Math.sqrt(disc);
        t = (-k1 - disc) / (2 * k2); s = solve(t);
        if (!(t >= -1e-3 && t <= 1.001 && s > -0.05 && s < 1.05)) { t = (-k1 + disc) / (2 * k2); s = solve(t); }
      }
      if (!(t >= -1e-3 && t <= 1.001) || !(s > -0.05 && s < 1.05)) continue;
      const tt = clamp(t), W = lerp(A.W, B.W, tt), edgeU = Math.max(1, W * softK * 0.5);
      const cov = clamp((W - Math.abs(1 - 2 * s) * W) / edgeU) * hakeDens(br, lerp(A.pr, B.pr, tt)) * lerp(A.own ?? 1, B.own ?? 1, tt);
      if (cov <= 0) continue;
      hakePut(surf, py * w + px, cov * laneShare(hairs, s, W, runOut(br, lerp(A.d, B.d, tt))), liveGran, gran);
      if (px < rx0) rx0 = px; if (px > rx1) rx1 = px;
    }
  }
  if (rx1 >= rx0) { if (rx0 < rect[0]) rect[0] = rx0; if (y0 < rect[1]) rect[1] = y0; if (rx1 > rect[2]) rect[2] = rx1; if (y1 > rect[3]) rect[3] = y1; }
}

// ===================================================================================================================
// Lines placed inside the wash body (pen-paint-blend round 2/3): split nib, multi-line, outline, squiggle, plus Offset
// and Chaos. Adds a Fast-wet window, the Fast taper and the hand's darkness. The split's wet gap goes to surf.wetBuf.
// ===================================================================================================================
const PLACEMENTS = ["centre", "spread", "outline", "squiggle", "split"];
const breath = (b, pr) => mix(1, smooth((pr - .2) / .65), b.breathe);
function hash01(seed, i) {
  let t = (seed ^ Math.imul(i | 0, 0x27d4eb2d)) >>> 0;
  t = Math.imul(t ^ (t >>> 15), 0x2c1b3c6d); t = Math.imul(t ^ (t >>> 12), 0x297a2d39); t ^= t >>> 15;
  return (t >>> 0) / 4294967296;
}
function noise1(seed, x) {
  const i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f);
  const a = lerp(hash01(seed, i), hash01(seed, i + 1), u), b = lerp(hash01(seed ^ 0x9e3779b9, Math.floor(x * 2.3)), hash01(seed ^ 0x9e3779b9, Math.floor(x * 2.3) + 1), (x * 2.3) % 1);
  return a * .75 + b * .25;
}
function stampPlaced(surf, b, body, pts, size, bodySize, final, from, win) {
  const { w, h, scratch: buf, fine, mid, low } = surf, wet = surf.wetBuf || null;
  const total = pts.length ? pts[pts.length - 1].d : 0;
  const rnd = rng(b.seed ^ 0x9e37);
  const bristles = []; for (let i = 0; i < 48; i++) bristles.push(rnd());
  const lr = rng(b.seed ^ 0x11e5), loads = [], widths = [], phase = [];
  for (let j = 0; j < 5; j++) { loads.push(.8 + .2 * lr()); widths.push(.85 + .3 * lr()); phase.push([lr() * 997, lr() * 991]); }
  const sq0 = lr() * 6.283, sq1 = lr() * 6.283;
  const cosA = Math.cos(b.angle * Math.PI / 180), sinA = Math.sin(b.angle * Math.PI / 180);
  const inLen = b.taperIn * 70, outLen = b.taperOut * 90, kind = b.lines;
  const grw = Math.max(1, bodySize / 24), bIn = (body.taperIn || 0) * 70 * grw, bOut = (body.taperOut || 0) * 90 * grw;
  const n = kind === "spread" ? b.count : kind === "outline" ? 2 : 1, chaos = b.chaos, sd = b.seed >>> 0;
  const cn = (j, c, x) => noise1((sd + j * 1013 + c * 7919) >>> 0, x);
  const sub = kind === "spread" ? .7 : kind === "outline" ? .6 : kind === "squiggle" ? .85 : 1;
  const reach = lerp(.3, .95, b.spread), bodyRef = paintWidth(body, bodySize, .7) / 2;
  const lambda = Math.max(30, bodyRef * 3);
  const swing = (kind === "squiggle" ? 1 + 6.283 * reach * bodyRef / lambda * (1 + 2 * chaos) : 1) * (1 + chaos);
  let rect = [w, h, 0, 0];
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    const dirx = seg > 0 ? (p1.x - p0.x) / seg : 1, diry = seg > 0 ? (p1.y - p0.y) / seg : 0;
    const steps = Math.max(1, Math.ceil(seg * swing / Math.max(0.4, size * sub * 0.1)));
    for (let s = (k === 1 ? 0 : 1); s <= steps; s++) {
      const t = s / steps, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), d = lerp(p0.d, p1.d, t);
      if (win && (d < win.dMin || d >= win.dMax)) continue;
      if (d < b.skipStart || (final && d > total - b.skipEnd)) continue;
      const pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t), open = breath(b, pr);
      let wd = size * lerp(1 - 0.75 * b.pressure, 1 + 0.6 * b.pressure, pr);
      wd *= clamp(1 + b.speed * 0.55 * (clamp(v / 2.5) - 0.3), 0.35, 1.6);
      if (inLen > 0) wd *= Math.pow(clamp(d / inLen), 0.6) * 0.92 + 0.08;
      if (b.fast) { if (b.taperOut > 0) { const fall = p0.pk > 0 ? clamp(pr / (p0.pk * 0.7)) : 1; wd *= lerp(1, Math.pow(fall, 0.8) * 0.92 + 0.08, b.taperOut); } }
      else if (final && outLen > 0) wd *= Math.pow(clamp((total - d) / outLen), 0.6) * 0.92 + 0.08;
      if (win && win.tail) wd *= lerp(1, Math.pow(clamp((total - d) / win.tail.len), 0.6) * 0.92 + 0.08, win.tail.amt);
      // Flick: past the lift the lines and their body narrow to nothing and run dry (newmods.js, the app's FlickFling).
      const flk = pts.flick ? flickWidth(pts.flick, d) : 1, fdry = pts.flick ? flickDry(pts.flick, d) : 0;
      wd *= flk;
      if (b.pool > 0) wd *= 1 + b.pool * 0.9 * (1 - clamp(v / 0.25)) ** 2;
      let tf = 1;
      if (bIn > 0) tf *= Math.pow(clamp(d / bIn), 0.6) * 0.92 + 0.08;
      if (final && bOut > 0) tf *= Math.pow(clamp((total - d) / bOut), 0.6) * 0.92 + 0.08;
      tf *= flk;
      const rB = Math.max(0.8, mix(bodyRef, paintWidth(body, bodySize, pr) / 2, b.breathe) * tf);
      const nx = -diry, ny = dirx, an = nx * cosA + ny * sinA, pn = -nx * sinA + ny * cosA;
      const edge = Math.sqrt((rB * an) ** 2 + (rB * body.aspect * pn) ** 2);
      const g = kind === "split" ? b.spread * open * (chaos > 0 ? 1 + chaos * (2 * cn(9, 1, d / 60) - 1) * .7 : 1) : 0;
      let drift = 0;
      if (b.offset > 0) drift = b.offset * (edge * 1.6 + size * 2.5) * clamp((2 * noise1(sd ^ 0x0ff5e7, d / (60 + bodyRef * 2)) - 1) * 1.8, -1, 1);
      if (g > 0) wd *= 1 + .9 * g;
      const load = 1 - b.dry * (0.35 + 0.8 * clamp(d / 600) + 0.7 * clamp(v / 2.5) - 0.6 * pr) - fdry, dry = b.dry > 0 || fdry > 0;
      let dk = handDark(b, pr, v, 0);
      if (p1.own !== undefined) dk *= lerp(p0.own, p1.own, t);
      for (let j = 0; j < n; j++) {
        let cx = x + nx * drift, cy = y + ny * drift, wj = wd * sub * (n > 1 ? widths[j] : 1), ink = (n > 1 ? loads[j] : 1) * dk;
        if (chaos > 0) {
          if (cn(j, 5, d / (14 + size * 2)) < chaos * .34) continue;
          wj *= Math.max(.25, 1 + chaos * .9 * (2 * cn(j, 3, d / 30) - 1));
          ink *= 1 - chaos * .45 * cn(j, 4, d / 45);
          const jit = chaos * (edge * .55 + size) * (2 * cn(j, 2, d / 38) - 1);
          cx += nx * jit; cy += ny * jit;
        }
        const lw = wj / 2;
        if (kind === "spread") {
          let u = (j - (n - 1) / 2) / Math.max(1, (n - 1) / 2) * Math.max(0, rB * reach - lw);
          if (chaos > 0) u *= 1 + chaos * .8 * (2 * cn(j, 1, d / 50) - 1);
          cx += cosA * u; cy += sinA * u;
        } else if (kind === "outline") {
          const off = Math.max(0, edge * .97 - lw * .8) * (j ? 1 : -1);
          cx += nx * off; cy += ny * off;
          const thin = clamp((edge - wd * .9) / (wd * 1.5)), xi = clamp(cx | 0, 0, w - 1), yi = clamp(cy | 0, 0, h - 1);
          ink *= 1 - (1 - thin) * b.wetGap * (.2 + .6 * clamp((.5 - low[yi * w + xi]) * 7));
          wj *= 1 - .3 * (1 - thin) * b.wetGap;
          if (ink <= .02) continue;
        } else if (kind === "squiggle") {
          const ph = 6.283 * (d / lambda + chaos * 1.6 * noise1(sd ^ 0x5a1, d / (lambda * 1.4))) + sq0 + .9 * (1 - chaos) * Math.sin(d / lambda * 1.7 + sq1);
          const off = Math.max(0, edge * reach - lw) * Math.sin(ph) * (1 - chaos * .6 * cn(0, 6, d / (lambda * .9)));
          cx += nx * off; cy += ny * off;
        }
        if (n > 1 || kind === "squiggle" || chaos > 0) {
          const px0 = clamp((cx + phase[j][0]) % w | 0, 0, w - 1), py0 = clamp((cy + phase[j][1]) % h | 0, 0, h - 1);
          const jit = (.2 + b.wobble) * (mid[py0 * w + px0] - .5) * size * .5;
          cx += nx * jit; cy += ny * jit;
          wj *= 1 + b.wobble * 0.7 * (mid[py0 * w + px0] - 0.5);
        } else if (b.wobble > 0) { const xi = clamp(x | 0, 0, w - 1), yi = clamp(y | 0, 0, h - 1); wj *= 1 + b.wobble * 0.7 * (mid[yi * w + xi] - 0.5); }
        const a = Math.max(0.6, wj / 2), bb = Math.max(0.6, Math.min(a, Math.max(1, a * b.aspect)));
        const nBr = Math.min(48, (g > 0 ? 4 : 6) + Math.round(a));
        const R = Math.ceil(a + 1.5);
        const x0 = Math.max(0, Math.floor(cx - R)), x1 = Math.min(w - 1, Math.ceil(cx + R));
        const y0 = Math.max(0, Math.floor(cy - R)), y1 = Math.min(h - 1, Math.ceil(cy + R));
        for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
          const i = py * w + px;
          if (buf[i] >= ink) continue;
          const dx = px + 0.5 - cx, dy = py + 0.5 - cy;
          const u = dx * cosA + dy * sinA, q = -dx * sinA + dy * cosA;
          const dist = Math.sqrt((u / a) * (u / a) + (q / bb) * (q / bb));
          const gr = Math.hypot(u / (a * a), q / (bb * bb)) / Math.max(dist, 1e-6);
          let cov = clamp(0.5 - (dist - 1) / Math.max(gr, 1e-6));
          if (cov <= 0) continue;
          const across = (-dx * diry + dy * dirx) / a;
          if (g > 0) {
            const gp = clamp((g * .72 - Math.abs(across)) / .14);
            if (gp > 0 && wet && b.wetGap > 0) { const v2 = cov * gp * b.wetGap * .8; if (v2 > wet[i]) wet[i] = v2; }
            cov *= 1 - gp;
            if (cov <= 0) continue;
          }
          if (dry && bristles[(clamp((across + 1) / 2, 0, .9999) * nBr) | 0] > load + 0.25 * (fine[i] - 0.5)) continue;
          cov *= ink;
          if (cov > buf[i]) buf[i] = cov;
          if (WET && cov > 0) WET[i] = d;
        }
        if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
      }
    }
  }
  if (b.fast && b.splatter > 0) splatterLive(surf, b, pts, size, from, rect);
  else if (final && b.splatter > 0) splatter(surf, b, pts, size, rect);
  return rect;
}

// ===================================================================================================================
// Bristle swarm (marble-and-bristle): hairs that read each other's fading trails. Hairs capped at 32. Stateful: fed the
// samples not yet walked, one step per px of travel; final at lift. Stamps into the buffer it's given.
// ===================================================================================================================
const SW_CELL = 2, SW_PT = (() => { const a = new Float32Array(400); for (let k = 0; k < a.length; k++) a[k] = Math.pow(.93, k); return a; })();
function swHash(i, s) { let h = Math.imul(i, 0x1f123bb5) ^ s; h ^= h >>> 16; h = Math.imul(h, 0x45d9f3b); h ^= h >>> 16; return (h & 65535) / 65536; }
function swNoise(x, s) { const i = Math.floor(x), t = smooth(x - i); return lerp(swHash(i, s), swHash(i + 1, s), t); }
function hashInts(h, ...v) { for (const x of v) h = Math.imul(h ^ (x | 0), 16777619); return h >>> 0; }
function swarmStart(surf, b, R, p0) {
  // A big brush is the small swarm scaled up: the hairs move in units of a 10 px brush, k page px each, so a 120 px
  // bristle brush looks like a 20 px one enlarged, not like ropes. At 10 px and under, k = 1: the prototype exactly.
  R = Math.max(2, R);
  const k = Math.max(1, R / 10), Ri = R / k;
  const N = Math.max(6, Math.min(32, Math.round(b.hairs || 24)));
  const seed = hashInts(b.seed, Math.round(p0.x * 4), Math.round(p0.y * 4));
  const r = rng(seed), hairs = [];
  for (let i = 0; i < N; i++) {
    const u0 = ((i + .5) / N * 2 - 1) + (r() - .5) * (2 / N);
    hairs.push({ u0, u: u0 * Ri * .35, w: 0, a: (.25 - Math.pow(r(), 1.3)) * Ri, load: 1, thirst: .6 + .8 * r(),
      rb: Math.max(.55, Ri * .1) * (.75 + .5 * r()), ns: (r() * 4294967296) >>> 0, x: p0.x, y: p0.y, F: 0, n: 0 });
  }
  const cell = SW_CELL * k, gw = Math.ceil(surf.w / cell), gh = Math.ceil(surf.h / cell), coh = b.cohesion, spr = b.spreadH;
  if (!surf.swField || surf.swField.length < gw * gh) { surf.swField = new Float32Array(Math.ceil(surf.w / SW_CELL) * Math.ceil(surf.h / SW_CELL)); surf.swLast = new Int32Array(surf.swField.length); }
  surf.swField.fill(0, 0, gw * gh); surf.swLast.fill(0, 0, gw * gh);
  const th = lerp(.55, 1.15, spr);
  return { surf, b, R: Ri, k, cell, N, hairs, gw, gh, field: surf.swField, last: surf.swLast, step: 0, dirx: 0, diry: 0, done: 1,
    cs: Math.cos(th), sn: Math.sin(th), sd: Ri * .5 + 2.5, gain: lerp(-.9, 1.0, coh) * .6 * Math.sqrt(Ri / 8), sat: lerp(1.2, 4.2, coh),
    drain: .0009 * b.dryness ** 3 };
}
function swField(S, x, y) {
  const gx = (x / S.cell) | 0, gy = (y / S.cell) | 0;
  if (gx < 0 || gy < 0 || gx >= S.gw || gy >= S.gh) return 0;
  const c = gy * S.gw + gx, age = S.step - S.last[c];
  return age >= SW_PT.length ? 0 : S.field[c] * SW_PT[age];
}
function swDeposit(S, x, y, amt) {
  const gx0 = (x / S.cell) | 0, gy0 = (y / S.cell) | 0;
  for (let gy = gy0 - 1; gy <= gy0 + 1; gy++) for (let gx = gx0 - 1; gx <= gx0 + 1; gx++) {
    if (gx < 0 || gy < 0 || gx >= S.gw || gy >= S.gh) continue;
    const c = gy * S.gw + gx, age = S.step - S.last[c], kk = (gx === gx0 && gy === gy0) ? amt : amt * .4;
    S.field[c] = (age >= SW_PT.length ? 0 : S.field[c] * SW_PT[age]) + kk; S.last[c] = S.step;
  }
}
function swHair(S, buf, x, y, rb, dark, thr, rect) {
  const { w, h, fine, mid } = S.surf, R = Math.ceil(rb + 1);
  const x0 = Math.max(0, Math.floor(x - R)), x1 = Math.min(w - 1, Math.ceil(x + R)), y0 = Math.max(0, Math.floor(y - R)), y1 = Math.min(h - 1, Math.ceil(y + R));
  for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
    const ex = px + .5 - x, ey = py + .5 - y;
    let cov = rb + .5 - Math.sqrt(ex * ex + ey * ey);
    if (cov <= 0) continue;
    if (cov > 1) cov = 1;
    const i = py * w + px;
    // A dry hair skips the paper's high points: it inks only where the tooth is below what it still holds.
    if (thr < 1.2) { const s = (thr - (fine[i] * .62 + mid[i] * .38)) * 8 + .5; if (s <= 0) continue; if (s < 1) cov *= s * s * (3 - 2 * s); }
    const v = cov * dark;
    if (v > buf[i]) buf[i] = v;
    if (WET) WET[i] = WET_D;
  }
  if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
}
function swStep(S, buf, px, py, v, p, d, lean, rect, own = 1) {
  const b = S.b, R = S.R, k = S.k, hairs = S.hairs, N = S.N, spr = b.spreadH, dry = b.dryness;
  const dx = S.dirx, dy = S.diry, nx = -dy, ny = dx, cs = S.cs, sn = S.sn, sd = S.sd * k, gain = S.gain, sat = S.sat;
  const sv = clamp(v / 2.2), pp = clamp((p - .3) / .7), ramp = smooth(d / k / (R * 1.5));
  const Rs = R * (.42 + .38 * ramp + spr * (.55 * pp + .5 * sv));
  const wander = .025 * R * spr * (.4 + sv), wl = R * 2.5;
  const f = s => { const q = s / sat; return s / (1 + q * q); };
  for (const hr of hairs) {
    const x = px + (nx * hr.u + dx * hr.a) * k, y = py + (ny * hr.u + dy * hr.a) * k;
    const sL = swField(S, x + (dx * cs - nx * sn) * sd, y + (dy * cs - ny * sn) * sd);
    const sR = swField(S, x + (dx * cs + nx * sn) * sd, y + (dy * cs + ny * sn) * sd);
    const sC = swField(S, x + dx * sd, y + dy * sd);
    let F = gain * (f(sR) - f(sL)) / Math.sqrt(sat);
    if (sC > sat * 1.8) F += (sR >= sL ? -1 : 1) * Math.abs(gain) * .4;
    F += (swNoise(d / k / wl, hr.ns) - .5) * 2 * wander;
    F -= .009 * (hr.u - hr.u0 * Rs);
    const over = Math.abs(hr.u) - Rs; if (over > 0) F -= .3 * over * Math.sign(hr.u);
    hr.F = F;
  }
  for (const hr of hairs) { hr.w = hr.w * .8 + hr.F; hr.u += hr.w; hr.x = px + (nx * hr.u + dx * hr.a) * k; hr.y = py + (ny * hr.u + dy * hr.a) * k; }
  for (let i = 0; i < N; i++) { const hi = hairs[i]; let n = 0; for (let j = 0; j < N; j++) { const hj = hairs[j]; if (Math.abs(hj.u - hi.u) < (hi.rb + hj.rb) * .8) n++; } hi.n = n - 1; }
  const dk = handDark(b, p, v, lean) * own;
  WET_D = d;
  for (const hr of hairs) {
    hr.load = Math.max(0, hr.load - S.drain * k * hr.thirst * (1 + .6 * sv));
    const held = hr.load + dry * .3 * (pp - .4) - dry * dry * .2 * sv;
    const thr = dry <= 0 ? 9 : held + .1;
    if (thr <= -.05) continue;
    const dark = lerp(.42, 1, clamp(hr.n / 3)) * lerp(.72, 1, clamp(hr.load * 1.4)) * dk * (b.amount ?? 1);
    swHair(S, buf, hr.x, hr.y, hr.rb * k * (.7 + .7 * pp) * (1 + 1.2 * (b.tiltSize || 0) * lean), dark, thr, rect);
  }
  for (const hr of hairs) swDeposit(S, hr.x, hr.y, 1);
  S.step++;
}
function swarmFeed(S, pts, buf) {
  const rect = [S.surf.w, S.surf.h, 0, 0];
  for (let k = Math.max(1, S.done); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    if (seg <= 0) continue;
    const ux = (p1.x - p0.x) / seg, uy = (p1.y - p0.y) / seg;
    if (S.dirx === 0 && S.diry === 0) { S.dirx = ux; S.diry = uy; }
    const T = (S.b.tiltSize || S.b.tDark) ? tiltOf(p1.tx, p1.ty) : null, lean = T ? T.lean : 0;
    const n = Math.max(1, Math.ceil(seg / S.k));
    for (let s = 1; s <= n; s++) {
      const t = s / n;
      const hx = S.dirx + (ux - S.dirx) * .2, hy = S.diry + (uy - S.diry) * .2, m = Math.hypot(hx, hy) || 1;
      S.dirx = hx / m; S.diry = hy / m;
      swStep(S, buf, lerp(p0.x, p1.x, t), lerp(p0.y, p1.y, t), lerp(p0.v, p1.v, t), lerp(p0.p, p1.p, t), lerp(p0.d, p1.d, t), lean, rect, p1.own === undefined ? 1 : lerp(p0.own, p1.own, t));
    }
  }
  S.done = Math.max(S.done, pts.length);
  return rect;
}

// ===================================================================================================================
// The settle: the port's settlePaint (via settlePaintD and pen-paint-blend's settleWash) as one function: an optional
// prep step, cover fading the wet effects, size scaling, impasto relief lit from the upper left. Reads src, writes
// dst (darkness 0..1.6, before the shade). Returns the region it covered.
// ===================================================================================================================
function settleBody(surf, src, dst, b, rect, size, prep, extraPad) {
  const { w, h, fine, mid, low, blob } = surf, K = paintBig(b, size || 0), KN = K * K;
  const wet = 1 - clamp(b.cover || 0), feather = b.feather * wet, hollow = b.hollow * wet, bloom = b.bloom * wet, backrun = b.backrun * wet;
  const fr = Math.round((2 + feather * 12) * K), pad = fr + 8 + (extraPad || 0);
  const X0 = Math.max(0, rect[0] - pad), Y0 = Math.max(0, rect[1] - pad), X1 = Math.min(w - 1, rect[2] + pad), Y1 = Math.min(h - 1, rect[3] + pad);
  const rw = X1 - X0 + 1, rh = Y1 - Y0 + 1;
  if (rw <= 0 || rh <= 0) return rect;
  const P = new Float32Array(rw * rh);
  for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) P[y * rw + x] = src[(y + Y0) * w + x + X0];
  if (prep) prep(P, X0, Y0, rw, rh);
  if (feather > 0) {
    const B = blur(blur(P, rw, rh, fr), rw, rh, Math.max(1, fr >> 1));
    for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
      const j = y * rw + x, i = (y + Y0) * w + x + X0;
      const reach = clamp((B[j] - (0.42 - 0.3 * feather) - 0.45 * (low[i] - 0.5) - 0.25 * (mid[i] - 0.5)) * 6);
      const f = reach * B[j] * 1.4 * feather;
      if (f > P[j]) P[j] = clamp(f);
    }
  }
  let rim = null;
  if (hollow > 0) {
    const B2 = blur(blur(P, rw, rh, Math.round((3 + hollow * 5) * K)), rw, rh, Math.round(2 * K));
    rim = new Float32Array(rw * rh);
    for (let j = 0; j < rim.length; j++) rim[j] = Math.max(0, P[j] - B2[j]);
  }
  for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
    const j = y * rw + x, i = (y + Y0) * w + x + X0; let d = P[j];
    if (d <= 0) { dst[i] = 0; continue; }
    const li = K > 1 ? Math.min(h - 1, ((y + Y0) / KN) | 0) * w + Math.min(w - 1, ((x + X0) / KN) | 0) : i;
    if (rim) d = d * (1 - 0.6 * hollow) + rim[j] * hollow * 5;
    if (bloom > 0) d *= clamp(1 + bloom * 2.6 * (low[li] - 0.5) + bloom * 0.5 * (mid[i] - 0.5), 0.25, 2);
    if (backrun > 0) {
      const th = 0.7 - 0.05 * backrun, n = 0.55 * blob[li] + 0.45 * low[li];
      const inside = clamp((n - th) * 40), ring = Math.exp(-(((n - th) / 0.02) ** 2));
      d = d * (1 - 0.4 * backrun * inside) + d * backrun * 0.9 * ring;
    }
    if (b.gran > 0) d *= 1 + b.gran * 1.5 * (fine[i] - 0.5) + b.gran * 0.6 * (mid[i] - 0.5);
    dst[i] = clamp(d, 0, 1.6);
  }
  if ((b.impasto || 0) > 0) {
    const rel = lazyBuf(surf, "relief");
    const Dd = new Float32Array(rw * rh);
    for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) Dd[y * rw + x] = Math.min(1, dst[(y + Y0) * w + x + X0]);
    const pile = blur(Dd, rw, rh, Math.max(2, Math.round(3 * K)));
    for (let y = 1; y < rh - 1; y++) for (let x = 1; x < rw - 1; x++) {
      const j = y * rw + x; if (Dd[j] <= 0) continue;
      // Light fixed at the upper left: a slope rising toward the lower right faces it and reads lighter.
      const ridge = Dd[j + rw + 1] - Dd[j - rw - 1], edge = Math.max(0, Dd[j] - pile[j]);
      rel[(y + Y0) * w + x + X0] = b.impasto * (100 * ridge - 90 * edge);
    }
  }
  return [X0, Y0, X1, Y1];
}
