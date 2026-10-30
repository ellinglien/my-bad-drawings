"use strict";
let QUANT = true;
// ---------- randomness ----------
function rng(seed) {
  let s = seed >>> 0;
  return function () {
    s = (s + 0x6D2B79F5) >>> 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const clamp = (v, a = 0, b = 1) => v < a ? a : v > b ? b : v;
const lerp = (a, b, t) => a + (b - a) * t;

// Smooth value noise in 0..1, cell pixels per lattice step.
function valueNoise(w, h, cell, r) {
  const gw = Math.ceil(w / cell) + 2, gh = Math.ceil(h / cell) + 2, g = new Float32Array(gw * gh);
  for (let i = 0; i < g.length; i++) g[i] = r();
  const out = new Float32Array(w * h);
  for (let y = 0; y < h; y++) {
    const fy = y / cell, iy = fy | 0, ty = fy - iy, sy = ty * ty * (3 - 2 * ty);
    for (let x = 0; x < w; x++) {
      const fx = x / cell, ix = fx | 0, tx = fx - ix, sx = tx * tx * (3 - 2 * tx);
      const a = g[iy * gw + ix], b = g[iy * gw + ix + 1], c = g[(iy + 1) * gw + ix], d = g[(iy + 1) * gw + ix + 1];
      out[y * w + x] = lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
    }
  }
  return out;
}
function fbm(w, h, cell, r) {
  const a = valueNoise(w, h, cell, r), b = valueNoise(w, h, cell / 2, r), c = valueNoise(w, h, cell / 4, r);
  for (let i = 0; i < a.length; i++) a[i] = (a[i] * 4 + b[i] * 2 + c[i]) / 7;
  return a;
}

// ---------- a surface: wash under ink, plus the paper's grain ----------
function surface(w, h, seed) {
  const r = rng(seed);
  return { w, h, ink: new Float32Array(w * h), wash: new Float32Array(w * h), scratch: new Float32Array(w * h),
    fine: valueNoise(w, h, 1.7, r), mid: valueNoise(w, h, 6, r), low: fbm(w, h, 44, r), blob: valueNoise(w, h, 16, r) };
}

// ---------- brushes ----------
const ADJ = ["bold","brave","brisk","bashful","chipper","cosmic","dapper","dramatic","feral","fierce","frantic","gallant","humble","jumbo","mellow","nimble","peculiar","plump","pompous","regal","rogue","rustic","sleek","smug","snug","stout","stubborn","suave","swift","upbeat","vivid","wild","zealous"];
const NOUN = ["plum","pear","fig","lime","leek","kale","yam","bean","pea","beet","corn","date","grape","peach","quince","chard","squash","gourd","cress","radish","turnip","parsnip","carrot","lemon","melon","mango","apple","cherry","olive","onion","garlic","pepper","okra","fennel","lychee","guava","shallot","caper","currant"];
const INK_SIZE = [3, 8, 16], PAINT_SIZE = [24, 120, 220];
const INK_DARK = [1, 0.53, 0.31], PAINT_DARK = [0.34, 0.2, 0.11];

// A roll: a seed decides every dial. Each roughness dial is off about half the time, so plain brushes still turn up.
function roll(kind, seed) {
  const r = rng(seed), maybe = p => r() < p ? r() : 0;
  const name = ADJ[(r() * ADJ.length) | 0] + "-" + NOUN[(r() * NOUN.length) | 0];
  if (kind === "ink") {
    const nib = ["round", "chisel", "flat"][(r() * 3) | 0];
    const b = { kind, seed, name, nib,
      aspect: nib === "round" ? 1 : nib === "chisel" ? lerp(0.3, 0.55, r()) : lerp(0.14, 0.28, r()),
      angle: lerp(20, 70, r()),
      taperIn: maybe(0.7), taperOut: maybe(0.75), pressure: r(), speed: lerp(-1, 1, r()),
      dry: maybe(0.45), tooth: maybe(0.5), wobble: maybe(0.45) };
    // Keep a roll tasteful: the three roughness dials share a budget.
    const rough = b.dry + b.tooth + b.wobble;
    if (rough > 1.3) { const k = 1.3 / rough; b.dry *= k; b.tooth *= k; b.wobble *= k; }
    // Drawn last, so seeds rolled before these dials existed keep the rest of their character.
    b.pool = maybe(0.45); b.splatter = maybe(0.3);
    return b;
  }
  const wet = r() < 0.65;
  const b = { kind, seed, name, mode: wet ? "wet" : "flat", pressure: r(), soft: r(),
    gran: maybe(0.55), feather: maybe(0.5), hollow: wet ? maybe(0.7) : 0, bloom: wet ? maybe(0.6) : 0 };
  b.streaks = maybe(0.45); const backrun = maybe(0.4); b.backrun = wet ? backrun : 0;
  // Drawn last, so seeds rolled before drying time existed keep the rest of their character. On the Manta,
  // 0.75 s + 4.25 s * drying^2 of wet time (WashLook.wetMs); not simulated here.
  b.drying = r();
  return b;
}
const INK_DIALS = [["taperIn", "Taper in"], ["taperOut", "Taper out"], ["pressure", "Pressure"], ["speed", "Speed", true], ["dry", "Dry brush"], ["tooth", "Paper tooth"], ["wobble", "Wobble"], ["pool", "Pooling"], ["splatter", "Splatter"]];

// ---------- ink ----------
// Stamps the stroke into surf.scratch (coverage 0..1), returns the touched rectangle.
function stampInk(surf, b, pts, size, final, from) {
  const { w, h, scratch: buf, fine, mid } = surf;
  const total = pts.length ? pts[pts.length - 1].d : 0;
  const rnd = rng(b.seed ^ 0x9e37);
  const bristles = []; for (let i = 0; i < 48; i++) bristles.push(rnd());
  const cosA = Math.cos(b.angle * Math.PI / 180), sinA = Math.sin(b.angle * Math.PI / 180);
  const inLen = b.taperIn * 70, outLen = b.taperOut * 90;
  let rect = [w, h, 0, 0];
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    const dirx = seg > 0 ? (p1.x - p0.x) / seg : 1, diry = seg > 0 ? (p1.y - p0.y) / seg : 0;
    const steps = Math.max(1, Math.ceil(seg / Math.max(0.4, size * 0.1)));
    for (let s = (k === 1 ? 0 : 1); s <= steps; s++) {
      const t = s / steps, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), d = lerp(p0.d, p1.d, t);
      const pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t);
      let wd = size * lerp(1 - 0.75 * b.pressure, 1 + 0.6 * b.pressure, pr);
      wd *= clamp(1 + b.speed * 0.55 * (clamp(v / 2.5) - 0.3), 0.35, 1.6);
      if (inLen > 0) wd *= Math.pow(clamp(d / inLen), 0.6) * 0.92 + 0.08;
      if (final && outLen > 0) wd *= Math.pow(clamp((total - d) / outLen), 0.6) * 0.92 + 0.08;
      const xi = clamp(x | 0, 0, w - 1), yi = clamp(y | 0, 0, h - 1);
      if (b.wobble > 0) wd *= 1 + b.wobble * 0.7 * (mid[yi * w + xi] - 0.5);
      // Ink pools where the pen slows or rests.
      if (b.pool > 0) wd *= 1 + b.pool * 0.9 * (1 - clamp(v / 0.25)) ** 2;
      const a = Math.max(0.6, wd / 2), bb = Math.max(0.6, Math.min(a, Math.max(1, a * b.aspect)));
      // How much ink is left in the brush: dry brushes run out with distance and speed, pressing pushes ink out.
      const load = 1 - b.dry * (0.35 + 0.8 * clamp(d / 600) + 0.7 * clamp(v / 2.5) - 0.6 * pr);
      const nBr = Math.min(48, 6 + Math.round(a));
      const R = Math.ceil(a + 1.5);
      const x0 = Math.max(0, Math.floor(x - R)), x1 = Math.min(w - 1, Math.ceil(x + R));
      const y0 = Math.max(0, Math.floor(y - R)), y1 = Math.min(h - 1, Math.ceil(y + R));
      for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
        // Already fully covered: nothing here can change it (the same picture, sooner).
        if (buf[py * w + px] >= 1) continue;
        const dx = px + 0.5 - x, dy = py + 0.5 - y;
        const u = dx * cosA + dy * sinA, q = -dx * sinA + dy * cosA;
        const dist = Math.sqrt((u / a) * (u / a) + (q / bb) * (q / bb));
        // Antialias by the distance in pixels to the ellipse's edge, so thin nibs stay crisp along their length too.
        const g = Math.hypot(u / (a * a), q / (bb * bb)) / Math.max(dist, 1e-6);
        let cov = clamp(0.5 - (dist - 1) / Math.max(g, 1e-6));
        if (cov <= 0) continue;
        if (b.dry > 0) {
          const across = clamp(((-dx * diry + dy * dirx) / a + 1) / 2, 0, 0.9999);
          const bristle = bristles[(across * nBr) | 0];
          if (bristle > load + 0.25 * (fine[py * w + px] - 0.5)) cov *= 0.0;
        }
        const i = py * w + px;
        if (cov > buf[i]) buf[i] = cov;
      }
      if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
    }
  }
  if (final && b.splatter > 0) splatter(surf, b, pts, size, rect);
  return rect;
}
// Fast flicks throw drops ahead of the pen and a little to the side.
function splatter(surf, b, pts, size, rect) {
  const { w, h, scratch: buf } = surf, total = pts.length ? pts[pts.length - 1].d : 0;
  const rnd = rng((b.seed ^ Math.imul(pts.length, 2654435761) ^ Math.round(total * 16)) >>> 0);
  for (let k = 1; k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    if (p1.v < 0.8 || seg <= 0 || rnd() > b.splatter * 0.025 * (p1.v - 0.8) * seg) continue;
    const dirx = (p1.x - p0.x) / seg, diry = (p1.y - p0.y) / seg;
    for (let n = 1 + ((rnd() * 3) | 0); n > 0; n--) {
      const ahead = 6 + rnd() * 34 * p1.v, side = (rnd() - 0.5) * 18;
      const x = p1.x + dirx * ahead - diry * side, y = p1.y + diry * ahead + dirx * side;
      const r = Math.max(0.7, (0.3 + rnd() * rnd() * 1.4) * size / 5), R = Math.ceil(r + 1);
      const x0 = Math.max(0, Math.floor(x - R)), x1 = Math.min(w - 1, Math.ceil(x + R));
      const y0 = Math.max(0, Math.floor(y - R)), y1 = Math.min(h - 1, Math.ceil(y + R));
      for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
        const cov = clamp(r - Math.hypot(px + 0.5 - x, py + 0.5 - y) + 0.5), i = py * w + px;
        if (cov > buf[i]) buf[i] = cov;
      }
      if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
    }
  }
}
// Ragged paper tooth, then onto the ink layer.
function settleInk(surf, b, rect, dark) {
  const { w, scratch: buf, ink, fine, mid } = surf;
  for (let y = rect[1]; y <= rect[3]; y++) for (let x = rect[0]; x <= rect[2]; x++) {
    const i = y * w + x; let a = buf[i]; if (a <= 0) continue;
    if (b.tooth > 0) {
      const n = fine[i] * 0.65 + mid[i] * 0.35;
      a = clamp((a - (0.5 + b.tooth * 0.9 * (n - 0.5))) * 4 + 0.5);
    }
    const v = a * dark; if (v > ink[i]) ink[i] = v;
    buf[i] = 0;
  }
}

// ---------- paint ----------
function paintWidth(b, size, p) {
  const tip = lerp(0.6, 0.06, b.pressure);
  return size * (tip + (1 - tip) * Math.pow(clamp(p), 1.2));
}
function stampPaint(surf, b, pts, size, from) {
  const { w, h, scratch: buf } = surf;
  let rect = [w, h, 0, 0];
  const hairs = []; if (b.streaks > 0) { const rnd = rng(b.seed ^ 0x51ed); for (let i = 0; i < 130; i++) hairs.push(rnd()); }
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    const dirx = seg > 0 ? (p1.x - p0.x) / seg : 1, diry = seg > 0 ? (p1.y - p0.y) / seg : 0;
    const steps = Math.max(1, Math.ceil(seg / Math.max(0.8, paintWidth(b, size, p1.p) * 0.12)));
    for (let s = (k === 1 ? 0 : 1); s <= steps; s++) {
      const t = s / steps, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t);
      const r = Math.max(0.8, paintWidth(b, size, pr) / 2), edge = Math.max(1, r * lerp(0.08, 0.6, b.soft));
      const dens = b.mode === "wet" ? lerp(0.7, 1, pr) : 1;
      const R = Math.ceil(r + 1);
      const x0 = Math.max(0, Math.floor(x - R)), x1 = Math.min(w - 1, Math.ceil(x + R));
      const y0 = Math.max(0, Math.floor(y - R)), y1 = Math.min(h - 1, Math.ceil(y + R));
      for (let py = y0; py <= y1; py++) {
        // Only the pixels inside the disc, and not those already as dark as this stamp can make them (the same picture, sooner).
        const dy = py + 0.5 - y, span = r * r - dy * dy;
        if (span <= 0) continue;
        const sx = Math.sqrt(span), xa = Math.max(x0, Math.floor(x - 0.5 - sx)), xb = Math.min(x1, Math.ceil(x - 0.5 + sx));
        for (let px = xa; px <= xb; px++) {
        const i = py * w + px;
        if (buf[i] >= dens) continue;
        const dx = px + 0.5 - x, dist = Math.sqrt(dx * dx + dy * dy);
        let cov = clamp((r - dist) / edge) * dens;
        if (b.streaks > 0 && cov > 0) {
          // Hair lines along the stroke: each hair across the brush carries its own amount of paint.
          const f = clamp(((-dx * diry + dy * dirx) / r + 1) / 2) * Math.min(120, 6 + r / 2.5), h0 = f | 0, t = f - h0;
          cov *= 1 - b.streaks * 0.75 * lerp(hairs[h0], hairs[h0 + 1], t * t * (3 - 2 * t));
        }
        if (cov > buf[i]) buf[i] = cov;
        }
      }
      if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
    }
  }
  return rect;
}
function blur(src, rw, rh, r) {
  const tmp = new Float32Array(rw * rh), out = new Float32Array(rw * rh), n = 2 * r + 1;
  for (let y = 0; y < rh; y++) {
    let acc = 0; const row = y * rw;
    for (let x = -r; x <= r; x++) acc += src[row + clamp(x, 0, rw - 1)];
    for (let x = 0; x < rw; x++) {
      tmp[row + x] = acc / n;
      acc += src[row + Math.min(rw - 1, x + r + 1)] - src[row + Math.max(0, x - r)];
    }
  }
  for (let x = 0; x < rw; x++) {
    let acc = 0;
    for (let y = -r; y <= r; y++) acc += tmp[clamp(y, 0, rh - 1) * rw + x];
    for (let y = 0; y < rh; y++) {
      out[y * rw + x] = acc / n;
      acc += tmp[Math.min(rh - 1, y + r + 1) * rw + x] - tmp[Math.max(0, y - r) * rw + x];
    }
  }
  return out;
}
// The wash settles: bleeds feather out, pigment pools at the rim, blooms and granulates, then glazes over what was there.
function settlePaint(surf, b, rect, dark) {
  const { w, h, scratch: buf, wash, fine, mid, low, blob } = surf;
  const fr = Math.round(2 + b.feather * 12), pad = fr + 8;
  const X0 = Math.max(0, rect[0] - pad), Y0 = Math.max(0, rect[1] - pad), X1 = Math.min(w - 1, rect[2] + pad), Y1 = Math.min(h - 1, rect[3] + pad);
  const rw = X1 - X0 + 1, rh = Y1 - Y0 + 1;
  if (rw <= 0 || rh <= 0) return;
  let P = new Float32Array(rw * rh);
  for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) P[y * rw + x] = buf[(y + Y0) * w + x + X0];
  if (b.feather > 0) {
    const B = blur(blur(P, rw, rh, fr), rw, rh, Math.max(1, fr >> 1));
    for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
      const j = y * rw + x, i = (y + Y0) * w + x + X0;
      // Creeping fingers: where the blurred paint reaches past a noisy threshold, paint follows it.
      const reach = clamp((B[j] - (0.42 - 0.3 * b.feather) - 0.45 * (low[i] - 0.5) - 0.25 * (mid[i] - 0.5)) * 6);
      const f = reach * B[j] * 1.4 * b.feather;
      if (f > P[j]) P[j] = clamp(f);
    }
  }
  let rim = null;
  if (b.hollow > 0) {
    const B2 = blur(blur(P, rw, rh, 3 + Math.round(b.hollow * 5)), rw, rh, 2);
    rim = new Float32Array(rw * rh);
    for (let j = 0; j < rim.length; j++) rim[j] = Math.max(0, P[j] - B2[j]);
  }
  for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
    const j = y * rw + x, i = (y + Y0) * w + x + X0; let d = P[j];
    if (d <= 0) continue;
    if (rim) d = d * (1 - 0.6 * b.hollow) + rim[j] * b.hollow * 5;
    if (b.bloom > 0) d *= clamp(1 + b.bloom * 2.6 * (low[i] - 0.5) + b.bloom * 0.5 * (mid[i] - 0.5), 0.25, 2);
    if (b.backrun > 0) {
      // Cauliflowers: water creeping back leaves pale blossoms with a dark rim.
      const th = 0.7 - 0.05 * b.backrun, n = 0.55 * blob[i] + 0.45 * low[i];
      const inside = clamp((n - th) * 40), ring = Math.exp(-(((n - th) / 0.02) ** 2));
      d = d * (1 - 0.4 * b.backrun * inside) + d * b.backrun * 0.9 * ring;
    }
    if (b.gran > 0) d *= 1 + b.gran * 1.5 * (fine[i] - 0.5) + b.gran * 0.6 * (mid[i] - 0.5);
    d = clamp(d, 0, 1.6) * dark;
    wash[i] = 1 - (1 - wash[i]) * (1 - clamp(d));
  }
  for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) buf[(y + Y0) * w + x + X0] = 0;
}

// ---------- points: distance and smoothed speed (px per ms) ----------
function addPoint(pts, x, y, p, t) {
  const q = pts[pts.length - 1];
  if (!q) { pts.push({ x, y, p, t, d: 0, v: 0 }); return true; }
  const seg = Math.hypot(x - q.x, y - q.y);
  if (seg < 0.6) return false;
  const v = q.v * 0.75 + (seg / Math.max(1, t - q.t)) * 0.25;
  pts.push({ x, y, p, t, d: q.d + seg, v });
  return true;
}

// ---------- drawing a whole stroke on any surface ----------
function drawStroke(surf, b, pts, sizeIx, shadeIx) {
  if (b.kind === "ink") {
    const rect = stampInk(surf, b, pts, INK_SIZE[sizeIx], true, 1);
    settleInk(surf, b, rect, INK_DARK[shadeIx]);
  } else {
    const rect = stampPaint(surf, b, pts, PAINT_SIZE[sizeIx], 1);
    settlePaint(surf, b, rect, PAINT_DARK[shadeIx]);
  }
}

// ---------- compositing ----------
const PAPER = 251;
function compose(ctx, surf, rect, live, quant) {
  const { w, ink, wash, scratch } = surf;
  const x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(surf.w - 1, rect[2]), y1 = Math.min(surf.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const rw = x1 - x0 + 1, rh = y1 - y0 + 1, img = ctx.createImageData(rw, rh), data = img.data;
  for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
    const i = (y + y0) * w + x + x0;
    let wd = wash[i], id = ink[i];
    if (live && scratch[i] > 0) {
      if (live.kind === "paint") wd = 1 - (1 - wd) * (1 - scratch[i] * live.dark);
      else id = Math.max(id, scratch[i] * live.dark);
    }
    let tone = PAPER * (1 - wd) * (1 - id);
    if (quant) tone = Math.round(tone / 17) * 17;
    const o = (y * rw + x) * 4;
    data[o] = data[o + 1] = data[o + 2] = tone; data[o + 3] = 255;
  }
  ctx.putImageData(img, x0, y0);
}

const TEXTURES = {};   // name -> Float32Array(256 * 256), 0..1
function loadTextures() {
  return Promise.all(Object.entries(TEXTURE_SRC).map(([name, src]) => new Promise(done => {
    const img = new Image();
    img.onload = () => {
      const c = document.createElement("canvas"); c.width = c.height = 256;
      const x = c.getContext("2d"); x.drawImage(img, 0, 0);
      const d = x.getImageData(0, 0, 256, 256).data, f = new Float32Array(256 * 256);
      for (let i = 0; i < f.length; i++) f[i] = d[i * 4] / 255;
      TEXTURES[name] = f; done();
    };
    img.onerror = () => done();
    img.src = src;
  })));
}
// A surface's fine and mid noise drive paper tooth, dry-brush bristles, wobble and granulation. A brush with a
// texture swaps in the scan: fine at full scale, mid at three times the scale, tiled across the surface.
function useTexture(surf, b) {
  surf.fine0 ??= surf.fine; surf.mid0 ??= surf.mid; surf.tex ??= {};
  const t = b.texture && TEXTURES[b.texture];
  if (!t) { surf.fine = surf.fine0; surf.mid = surf.mid0; return; }
  if (!surf.tex[b.texture]) {
    const fine = new Float32Array(surf.w * surf.h), mid = new Float32Array(surf.w * surf.h);
    for (let y = 0; y < surf.h; y++) for (let x = 0; x < surf.w; x++) {
      fine[y * surf.w + x] = t[(y & 255) * 256 + (x & 255)];
      const fx = x / 3, fy = y / 3, ix = fx | 0, iy = fy | 0, tx = fx - ix, ty = fy - iy;
      const at = (u, v) => t[(v & 255) * 256 + (u & 255)];
      mid[y * surf.w + x] = lerp(lerp(at(ix, iy), at(ix + 1, iy), tx), lerp(at(ix, iy + 1), at(ix + 1, iy + 1), tx), ty);
    }
    surf.tex[b.texture] = { fine, mid };
  }
  surf.fine = surf.tex[b.texture].fine; surf.mid = surf.tex[b.texture].mid;
}

// ---------- the sample stroke: the app icon's loop (Phosphor scribble-loop), its centre line in the icon's 256 units ----------
const LOOP = [[28,93],[50,72],[78,55],[108,48],[138,53],[160,68],[173,88],[180,110],[180,135],[170,160],[150,183],[124,200],[100,207],[78,201],[63,186],[60,167],[68,147],[86,131],[110,120],[136,112],[160,114],[184,122],[210,137],[236,155],[246,161]];
// Catmull-Rom through the loop, every couple of pixels, as the app draws it (WildSample.loop): turned a quarter clockwise,
// then flipped left to right (x and y swapped), so it no longer reads as a letter a, and its tail after it crosses itself
// half as long (LOOP_TAIL); it stands 186 high and 159 wide from (48, 28). Light at the start, firm from .6 of the way
// (LOOP_FIRM) to the end, slower where it's firm.
const LOOP_TAIL = .5, LOOP_FIRM = .6;
function loopPoints(w, h, pad, x0 = 0, y0 = 0) {
  const sc = Math.min((w - 2 * pad) / 159, (h - 2 * pad) / 186), ox = x0 + (w - 159 * sc) / 2 - 48 * sc, oy = y0 + (h - 186 * sc) / 2 - 28 * sc;
  const P = LOOP.map(([y, x]) => [ox + x * sc, oy + y * sc]);
  let dense = [];
  for (let i = 0; i < P.length - 1; i++) {
    const p0 = P[Math.max(0, i - 1)], p1 = P[i], p2 = P[i + 1], p3 = P[Math.min(P.length - 1, i + 2)];
    const n = Math.max(2, Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1]) / 2));
    for (let s = 0; s < n; s++) {
      const t = s / n, t2 = t * t, t3 = t2 * t;
      dense.push([0, 1].map(k => .5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3)));
    }
  }
  dense.push(P[P.length - 1]);
  dense = cutTail(dense, LOOP_TAIL);
  const pts = []; let time = 0;
  dense.forEach(([x, y], i) => { const u = i / (dense.length - 1), p = .15 + .75 * Math.pow(Math.sin(Math.PI / 2 * Math.min(1, u / LOOP_FIRM)), .7); time += 2 + 3 * p; addPoint(pts, x, y, p, time); });
  return pts;
}
// The line with only `keep` of its length after it first crosses itself (WildSample.cut); whole if it never does.
function cutTail(line, keep) {
  const n = line.length, arc = [0], side = (c, d, a) => (d[0] - c[0]) * (a[1] - c[1]) - (d[1] - c[1]) * (a[0] - c[0]);
  for (let i = 1; i < n; i++) arc.push(arc[i - 1] + Math.hypot(line[i][0] - line[i - 1][0], line[i][1] - line[i - 1][1]));
  for (let k = 2; k < n; k++) {
    const a = line[k - 1], b = line[k];
    for (let j = 0; j + 1 < k - 1; j++) {
      const c = line[j], d = line[j + 1], d1 = side(c, d, a), d2 = side(c, d, b), d3 = side(a, b, c), d4 = side(a, b, d);
      if ((d1 > 0) === (d2 > 0) || (d3 > 0) === (d4 > 0)) continue;
      const crossing = arc[k - 1] + (arc[k] - arc[k - 1]) * d1 / (d1 - d2), end = crossing + keep * (arc[n - 1] - crossing);
      let e = k;
      while (e < n - 1 && arc[e] < end) e++;
      const f = (end - arc[e - 1]) / (arc[e] - arc[e - 1]), p = line[e - 1], q = line[e];
      return [...line.slice(0, e), [p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f]];
    }
  }
  return line;
}
// Lays a whole stroke and settles it, in the brush's texture.
function lay(surf, b, pts, size, dark) {
  useTexture(surf, b);
  if (b.kind === "ink") { const rect = stampInk(surf, b, pts, size, true, 1); settleInk(surf, b, rect, dark ?? INK_DARK[0]); }
  else { const rect = stampPaint(surf, b, pts, size, 1); settlePaint(surf, b, rect, dark ?? PAINT_DARK[0]); }
}
const surfaces = new Map();
function drawLoop(canvas, b) {
  const w = canvas.width, h = canvas.height, key = w + "x" + h;
  if (!surfaces.has(key)) surfaces.set(key, surface(w, h, 7));
  const s = surfaces.get(key); s.ink.fill(0); s.wash.fill(0); s.scratch.fill(0);
  if (b.kind === "ink") { const size = Math.max(2.5, h / 30); lay(s, b, loopPoints(w, h, size * 2 + 6), size); }
  else { const size = h * .13; lay(s, b, loopPoints(w, h, size * .7 + 4), size); }
  compose(canvas.getContext("2d"), s, [0, 0, w - 1, h - 1], null, QUANT);
}
function loopCanvas(b, w, h) { const c = document.createElement("canvas"); c.width = w; c.height = h; drawLoop(c, b); return c; }

;
// ---------- the brush model, as the app's: BrushSetting, BrushStyle, BrushCode, BrushNames, BrushVariations, BrushCaptions ----------
// A brush: { kind: "ink" | "paint", seed, name, style, nib, aspect, angle, taperIn, taperOut, pressure, speed, thicker,
//   dry, tooth, wobble, pool, splatter, mode: "wet" | "flat", soft, gran, feather, hollow, bloom, streaks, backrun, drying,
//   texture, builtin? }. Every dial of both kinds is always there (0 when unused), as the app's WildBrush.
const TEXTURE_NAMES = ["plain", "cold press", "laid", "felt", "crumpled", "canvas"];
// Never reorder: share codes are written in this order (BrushSetting.java).
const SETTINGS = {
  nib: { label: "Nib", choices: ["round", "chisel", "flat"] },
  mode: { label: "Paint", choices: ["wet", "flat"] },
  texture: { label: "Texture", choices: TEXTURE_NAMES },
  aspect: { label: "Nib width", min: .14, max: .55 },
  angle: { label: "Nib angle", min: 20, max: 70 },
  taperIn: { label: "Taper in", min: 0, max: 1 },
  taperOut: { label: "Taper out", min: 0, max: 1 },
  pressure: { label: "Pressure", min: 0, max: 1 },
  speed: { label: "Speed", min: -1, max: 1 },
  dry: { label: "Dry brush", min: 0, max: 1 },
  tooth: { label: "Paper tooth", short: "tooth", min: 0, max: 1 },
  wobble: { label: "Wobble", min: 0, max: 1 },
  pool: { label: "Pooling", min: 0, max: 1 },
  splatter: { label: "Splatter", min: 0, max: 1 },
  soft: { label: "Soft edge", short: "softness", min: 0, max: 1 },
  gran: { label: "Granulation", short: "grain", min: 0, max: 1 },
  feather: { label: "Feathered bleed", short: "bleed", min: 0, max: 1 },
  hollow: { label: "Hollow edge", min: 0, max: 1 },
  bloom: { label: "Bloom", min: 0, max: 1 },
  streaks: { label: "Brush streaks", short: "streaks", min: 0, max: 1 },
  backrun: { label: "Backruns", min: 0, max: 1 },
  drying: { label: "Drying time", short: "drying", min: 0, max: 1 }
};
for (const [k, s] of Object.entries(SETTINGS)) { s.k = k; s.short ??= s.label.toLowerCase(); }
const ORDER = {
  ink: ["nib", "texture", "aspect", "angle", "taperIn", "taperOut", "pressure", "speed", "dry", "tooth", "wobble", "pool", "splatter"],
  paint: ["mode", "texture", "pressure", "soft", "gran", "feather", "hollow", "bloom", "streaks", "backrun", "drying"]
};
const ALL_KEYS = Object.keys(SETTINGS);
const isChoice = k => !!SETTINGS[k].choices;
const STRENGTH = { ink: "tooth", paint: "gran" };
const nibOnly = k => k === "aspect" || k === "angle";
const wetOnly = k => k === "hollow" || k === "bloom" || k === "backrun";
const activeSetting = (b, k) => !(nibOnly(k) && b.nib === "round") && !(wetOnly(k) && b.mode !== "wet");
// A choice's option index; a custom texture (not in the app yet) counts as plain.
function getIndex(b, k) {
  if (k === "mode") return b.mode === "wet" ? 0 : 1;
  const i = SETTINGS[k].choices.indexOf(b[k]);
  return i < 0 ? 0 : i;
}
function setIndex(b, k, i) { b[k] = SETTINGS[k].choices[i]; }
const wetMs = v => Math.round(750 + 4250 * v * v);
function shown(b, k) {
  const s = SETTINGS[k];
  if (s.choices) return k === "texture" ? b.texture : SETTINGS[k].choices[getIndex(b, k)];
  const v = b[k];
  if (k === "speed") return v === 0 ? "off" : Math.abs(v).toFixed(2);
  if (k === "drying") return (wetMs(v) / 1000).toFixed(1) + " s";
  if (k === "angle") return Math.round(v) + "°";
  if (s.min === 0 && v === 0) return "off";
  return v.toFixed(2);
}

// A blank brush, every dial off, round, flat, plain, Wild: as new WildBrush(kind, seed, name).
function blank(kind, seed, name) {
  const b = { kind, seed: seed >>> 0, name, style: WILD, nib: "round", aspect: 1, angle: 0, thicker: false, mode: "flat", texture: "plain" };
  for (const k of ALL_KEYS) if (!isChoice(k) && k !== "aspect" && k !== "angle") b[k] = 0;
  return b;
}
const nibWidths = nib => nib === "chisel" ? [.3, .55] : [.14, .28];
function normalize(b) {
  if (b.kind === "ink") {
    if (b.nib === "round") b.aspect = 1;
    else { const [lo, hi] = nibWidths(b.nib); b.aspect = b.aspect === 1 ? (lo + hi) / 2 : clamp(b.aspect, lo, hi); }
  } else if (b.mode !== "wet") { b.hollow = 0; b.bloom = 0; b.backrun = 0; }
  return b;
}
function copyBrush(b, seed) {
  const c = { ...b }; delete c.builtin; delete c.blurb; if (seed !== undefined) c.seed = seed >>> 0;
  return c;
}
const speedDir = b => b.speed < 0 ? -1 : b.speed > 0 ? 1 : b.thicker ? 1 : -1;
// A texture needs some strength to show: picking one raises a faint strength to .5, unless that dial is locked.
function pickTexture(b, t, locks) {
  b.texture = t;
  const s = STRENGTH[b.kind];
  if (t !== "plain" && !locks.has(s) && b[s] < .35) b[s] = .5;
  return b;
}

// ---------- styles, as BrushStyle.java: a range per setting; [lo, hi] always, {p, lo, hi} on that often, a list of options ----------
const WILD = "Wild";
const STYLES = {
  ink: {
    "Fineliner": { nib: ["round"], texture: ["plain", "plain", "cold press"], pressure: [0, .25], taperIn: { p: .4, lo: 0, hi: .15 }, taperOut: { p: .4, lo: 0, hi: .2 }, speed: [-.2, .2], tooth: { p: .3, lo: 0, hi: .15 }, wobble: { p: .2, lo: 0, hi: .1 }, pool: { p: .4, lo: 0, hi: .3 } },
    "Brush pen": { nib: ["round", "chisel"], texture: ["plain", "plain", "cold press", "felt"], pressure: [.7, 1], taperIn: [.3, .8], taperOut: [.4, 1], speed: [0, .7], dry: { p: .3, lo: 0, hi: .3 }, tooth: { p: .3, lo: 0, hi: .2 }, wobble: { p: .3, lo: 0, hi: .15 }, pool: { p: .4, lo: 0, hi: .4 }, splatter: { p: .15, lo: 0, hi: .3 } },
    "Dip pen": { nib: ["flat", "chisel"], texture: ["plain", "laid", "cold press"], angle: [30, 60], pressure: [.4, .85], taperIn: { p: .5, lo: 0, hi: .3 }, taperOut: { p: .5, lo: 0, hi: .4 }, speed: [-.3, .3], dry: { p: .25, lo: 0, hi: .25 }, pool: { p: .6, lo: .1, hi: .6 }, splatter: { p: .3, lo: 0, hi: .35 } },
    "Marker": { nib: ["chisel", "round"], texture: ["plain", "felt"], pressure: [0, .2], speed: [-.3, 0], pool: [.3, .8], tooth: { p: .3, lo: 0, hi: .2 } },
    "Pencil": { nib: ["round"], texture: ["cold press", "felt", "laid"], pressure: [.2, .5], taperIn: { p: .4, lo: 0, hi: .2 }, taperOut: { p: .4, lo: 0, hi: .25 }, dry: [.1, .3], tooth: [.5, .9], wobble: { p: .3, lo: 0, hi: .12 } },
    "Charcoal": { nib: ["chisel", "round"], texture: ["crumpled", "cold press", "laid"], pressure: [.5, .9], taperIn: { p: .4, lo: 0, hi: .3 }, taperOut: { p: .4, lo: 0, hi: .3 }, dry: [.3, .6], tooth: [.6, 1], wobble: { p: .6, lo: 0, hi: .4 } }
  },
  paint: {
    "Watercolor": { mode: ["wet"], texture: ["plain", "cold press"], pressure: [.3, .8], soft: [.2, .6], gran: { p: .5, lo: 0, hi: .5 }, feather: { p: .3, lo: 0, hi: .3 }, hollow: { p: .6, lo: .2, hi: .7 }, bloom: { p: .6, lo: .2, hi: .6 }, backrun: { p: .3, lo: 0, hi: .5 }, drying: [.3, .7] },
    "Ink wash": { mode: ["wet"], texture: ["plain", "felt"], pressure: [.4, .9], soft: [.5, 1], feather: [.3, .8], hollow: { p: .3, lo: 0, hi: .3 }, bloom: { p: .3, lo: 0, hi: .3 }, drying: [.5, .9] },
    "Gouache": { mode: ["flat"], texture: ["plain", "plain", "felt"], pressure: [0, .3], soft: [0, .2], gran: { p: .3, lo: 0, hi: .2 }, streaks: { p: .3, lo: 0, hi: .3 }, drying: [0, .3] },
    "Oils": { mode: ["flat"], texture: ["canvas"], pressure: [.3, .7], soft: [0, .15], gran: { p: .4, lo: 0, hi: .3 }, streaks: [.5, .95], drying: [.8, 1] },
    "Pastels": { mode: ["flat"], texture: ["cold press", "felt", "crumpled", "laid"], pressure: [.2, .6], soft: [.1, .4], gran: [.6, 1], streaks: { p: .4, lo: 0, hi: .4 }, drying: [0, .1] },
    "Dry brush": { mode: ["flat", "flat", "wet"], texture: ["cold press", "laid", "crumpled"], pressure: [.3, .7], soft: [0, .3], gran: [.3, .8], streaks: [.5, 1], drying: [0, .3] }
  }
};
const styleNames = kind => [...Object.keys(STYLES[kind]), WILD];
const recipe = b => STYLES[b.kind][b.style] || null;
// A style's range for a dial, as BrushStyle.band: null in Wild and for choices; a dial the style leaves out is its off value.
function band(b, k) {
  const rec = recipe(b), s = SETTINGS[k];
  if (!rec || s.choices) return null;
  if (k === "aspect") return nibWidths(b.nib);
  const spec = rec[k];
  if (!spec) { if (k === "angle") return [20, 70]; const off = Math.max(0, s.min); return [off, off]; }
  return Array.isArray(spec) ? spec : [spec.p < 1 ? 0 : spec.lo, spec.hi];
}
function amountBand(b) {
  const bd = band(b, "speed");
  if (!bd) return null;
  if (bd[0] <= 0 && bd[1] >= 0) return [0, Math.max(-bd[0], bd[1])];
  const a = Math.abs(bd[0]), c = Math.abs(bd[1]);
  return [Math.min(a, c), Math.max(a, c)];
}
const optionsOf = (b, k) => { const rec = recipe(b); return rec && rec[k] ? rec[k].map(o => SETTINGS[k].choices.indexOf(o)) : null; };

// ---------- names: adjective-produce (app/src/main/assets/names) ----------
const NAMES_ADJ = "bold\nbrave\nbrisk\nbashful\nchipper\ncosmic\ndapper\ndramatic\nferal\nfierce\nfrantic\ngallant\nhumble\njumbo\nmellow\nnimble\npeculiar\nplump\npompous\nregal\nrogue\nrustic\nsleek\nsmug\nsnug\nstout\nstubborn\nsuave\nswift\nupbeat\nvivid\nwild\nzealous\n".split("\n").map(s => s.trim()).filter(Boolean);
const NAMES_PRODUCE = "plum\npear\nfig\nlime\nleek\nkale\nyam\nbean\npea\nbeet\ncorn\ndate\ngrape\npeach\nquince\nchard\nsquash\ngourd\ncress\nradish\nturnip\nparsnip\ncarrot\nlemon\nmelon\nmango\napple\ncherry\nolive\nonion\ngarlic\npepper\nokra\nfennel\nlychee\nguava\nshallot\ncaper\ncurrant\n".split("\n").map(s => s.trim()).filter(Boolean);
const pick = list => list[(Math.random() * list.length) | 0];
const freshName = () => pick(NAMES_ADJ) + "-" + pick(NAMES_PRODUCE);
function kinName(name) {
  const space = name.indexOf(" ");
  const family = (space > 0 ? name.slice(space + 1) : name.slice(name.lastIndexOf("-") + 1)).trim();
  if (!family) return freshName();
  return pick(NAMES_ADJ) + "-" + family.toLowerCase();
}
const newSeed = () => (Math.random() * 4294967296) >>> 0;

// ---------- rolls, as BrushStyle.roll and rollSetting ----------
// Wild: a pen or a paint at random, the roll tester's roll of a new seed, a random texture half the time.
function rollIn(kind, style) {
  if (style === WILD) {
    const k = Math.random() < .5 ? "ink" : "paint";
    const b = Object.assign(blank(k, 0, ""), roll(k, newSeed()));
    b.style = WILD; b.name = freshName();
    b.mode = b.kind === "paint" ? b.mode : "flat";
    pickTexture(b, Math.random() < .5 ? "plain" : TEXTURE_NAMES[1 + ((Math.random() * (TEXTURE_NAMES.length - 1)) | 0)], new Set());
    return normalize(b);
  }
  const rec = STYLES[kind][style], b = blank(kind, newSeed(), freshName());
  b.style = style;
  for (const k of ORDER[kind]) {
    const spec = rec[k], s = SETTINGS[k];
    if (s.choices) { const opts = spec || [s.choices[0]]; b[k] = opts[(Math.random() * opts.length) | 0]; }
    else if (k === "aspect") b.aspect = 1;
    else if (!spec) b[k] = k === "angle" ? lerp(20, 70, Math.random()) : Math.max(0, s.min);
    else if (Array.isArray(spec)) b[k] = lerp(spec[0], spec[1], Math.random());
    else b[k] = Math.random() < spec.p ? lerp(spec.lo, spec.hi, Math.random()) : 0;
  }
  normalize(b);
  if (kind === "ink" && b.nib !== "round") { const [lo, hi] = nibWidths(b.nib); b.aspect = lerp(lo, hi, Math.random()); }
  return b;
}
function rollSetting(b, k) {
  const s = SETTINGS[k];
  if (s.choices) { const o = optionsOf(b, k); setIndex(b, k, o ? o[(Math.random() * o.length) | 0] : (Math.random() * s.choices.length) | 0); }
  else {
    const bd = band(b, k);
    if (!bd && s.min === 0 && Math.random() < .3) b[k] = 0;
    else b[k] = lerp(bd ? bd[0] : s.min, bd ? bd[1] : s.max, Math.random());
  }
  return normalize(b);
}

// ---------- share codes, byte for byte as BrushCode.java: name.K S DIALS.SEED ----------
const STEPS = 1295, THINNER_AT_ZERO = 647, THICKER_AT_ZERO = 648;
const slug = name => name.toLowerCase().replace(/[^a-z]+/g, "-").replace(/^-|-$/g, "") || "brush";
function dials(b) {
  let out = "";
  for (const k of ORDER[b.kind]) {
    const s = SETTINGS[k];
    if (s.choices) { out += getIndex(b, k); continue; }
    let step;
    if (k === "speed" && b.speed === 0) step = b.thicker ? THICKER_AT_ZERO : THINNER_AT_ZERO;
    else { const v = Math.max(s.min, Math.min(s.max, b[k])); step = Math.round((v - s.min) / (s.max - s.min) * STEPS); }
    const d = step.toString(36);
    out += (d.length < 2 ? "0" : "") + d;
  }
  return out;
}
const styleIndex = b => Math.max(0, styleNames(b.kind).indexOf(b.style));
function encode(b) { return slug(b.name) + "." + (b.kind === "ink" ? "I" : "P") + styleIndex(b) + dials(b) + "." + (b.seed >>> 0).toString(36); }
// Java's String.trim: only characters up to space.
const javaTrim = s => s.replace(/^[\u0000- ]+|[\u0000- ]+$/g, "");
function decode(code) {
  if (code == null) return null;
  let c = javaTrim(String(code)).toLowerCase();
  const dot = c.indexOf(".") + 1;
  if (dot > 0 && dot < c.length) c = c.slice(0, dot) + c[dot].toUpperCase() + c.slice(dot + 1);
  const m = /^([a-z]+(?:-[a-z]+)*)\.([IP])(\d)([0-9a-z]+)\.([0-9a-z]{1,7})$/.exec(c);
  if (!m) return null;
  const kind = m[2] === "I" ? "ink" : "paint", styles = styleNames(kind), style = m[3].charCodeAt(0) - 48;
  const seed = parseInt(m[5], 36);
  if (style >= styles.length || seed > 0xffffffff) return null;
  const b = blank(kind, seed, m[1]);
  b.style = styles[style];
  const digits = m[4];
  let at = 0;
  for (const k of ORDER[kind]) {
    const s = SETTINGS[k];
    if (s.choices) {
      if (at >= digits.length) return null;
      const o = digits.charCodeAt(at++) - 48;
      if (o < 0 || o >= s.choices.length) return null;
      setIndex(b, k, o); continue;
    }
    if (at + 2 > digits.length) return null;
    const step = parseInt(digits.slice(at, at + 2), 36);
    at += 2;
    if (k === "speed" && (step === THINNER_AT_ZERO || step === THICKER_AT_ZERO)) { b.speed = 0; b.thicker = step === THICKER_AT_ZERO; continue; }
    b[k] = s.min + step / STEPS * (s.max - s.min);
  }
  return at === digits.length ? normalize(b) : null;
}
// What makes two brushes draw alike: the code without name or style.
const settingsKey = b => (b.kind === "ink" ? "I" : "P") + dials(b) + "." + (b.seed >>> 0).toString(36) + (TEXTURE_NAMES.includes(b.texture) ? "" : "+" + b.texture);
const NAME_MAX = 32;
function cleanName(name) { const c = name.replace(/\s+/g, " ").trim(); return c.length > NAME_MAX ? c.slice(0, NAME_MAX).trim() : c; }

// ---------- captions, as BrushCaptions.java ----------
const LINE = 17;
function twoLines(items) {
  const lines = [];
  for (const it of items) {
    if (it.length > LINE) continue;
    const last = lines.length - 1;
    if (last >= 0 && (lines[last] + ", " + it).length <= LINE) lines[last] += ", " + it;
    else if (lines.length < 2) lines.push(it);
  }
  return lines;
}
function describeTool(b) {
  if (b.builtin) return twoLines([b.style, b.blurb]);
  const t = [b.style], add = (on, s) => { if (on) t.push(s); };
  if (b.kind === "ink") {
    add(b.nib !== "round", b.nib + " nib");
    add(b.splatter > .3, "splatters"); add(b.dry > .3, "dry brush"); add(b.tooth > .4, "toothy");
    add(b.wobble > .2, "wobbly"); add(b.pool > .4, "pools ink"); add(b.taperIn + b.taperOut > 1, "long tapers");
    add(b.pressure > .75, "pressure-hungry"); add(Math.abs(b.speed) > .4, b.speed > 0 ? "thicker when fast" : "thinner when fast");
  } else {
    const before = t.length;
    add(b.bloom > .35, "blooms"); add(b.hollow > .4, "hollow edge"); add(b.feather > .4, "feathered");
    add(b.streaks > .4, "streaky"); add(b.gran > .5, "grainy"); add(b.backrun > .3, "backruns");
    add(b.soft > .6, "soft edge"); add(b.drying > .75, "slow drying"); add(b.drying < .15, "dries fast");
    add(t.length === before, b.mode === "wet" ? "wet" : "flat");
  }
  add(b.texture !== "plain", b.texture);
  return twoLines(t);
}

// ---------- variations, as BrushVariations.java ----------
function holdsLocked(b, k, locks) {
  if (k === "nib") return locks.has("aspect");
  if (k !== "mode") return false;
  for (const l of locks) if (wetOnly(l) && b[l] !== 0) return true;
  return false;
}
function varies(b, k) {
  const rec = recipe(b);
  if (!rec) return true;
  if (isChoice(k)) { const o = optionsOf(b, k); return !!o && o.length > 1; }
  if (k === "aspect") return true;
  const bd = band(b, k); return bd[1] > bd[0];
}
function direction(b, locks) {
  const pool = ORDER[b.kind].filter(k => !locks.has(k) && !holdsLocked(b, k, locks) && (isChoice(k) || activeSetting(b, k)) && varies(b, k));
  const pushes = [], n = 2 + (Math.random() < .4 ? 1 : 0);
  while (pushes.length < n && pool.length) {
    const k = pool.splice((Math.random() * pool.length) | 0, 1)[0], s = SETTINGS[k];
    if (s.choices) {
      if (Math.random() < .5) continue;
      const cur = getIndex(b, k), opts = optionsOf(b, k) || s.choices.map((_, i) => i), others = opts.filter(o => o !== cur);
      if (!others.length) continue;
      pushes.push({ k, to: others[(Math.random() * others.length) | 0] }); continue;
    }
    const bd = band(b, k), v = b[k], lo = Math.min(bd ? bd[0] : s.min, v), hi = Math.max(bd ? bd[1] : s.max, v), span = hi - lo;
    const rel = (v - lo) / (span === 0 ? 1 : span);
    let sign = Math.random() < .5 ? -1 : 1;
    if (rel < .08) sign = 1; else if (rel > .92) sign = -1;
    pushes.push({ k, d: sign * lerp(.35, .8, Math.random()) * span, lo, hi });
  }
  return pushes;
}
function applyPushes(b, pushes, locks, seed) {
  const c = copyBrush(b, seed);
  for (const p of pushes) {
    if (p.k === "texture") { pickTexture(c, TEXTURE_NAMES[p.to], locks); continue; }
    if (isChoice(p.k)) { setIndex(c, p.k, p.to); continue; }
    let v = Math.max(p.lo, Math.min(p.hi, b[p.k] + p.d));
    if (SETTINGS[p.k].min === 0 && v < .04) v = 0;
    c[p.k] = v;
  }
  return normalize(c);
}
function describePushes(pushes) {
  return twoLines(pushes.map(p => {
    if (!isChoice(p.k)) return (p.d > 0 ? "more " : "less ") + SETTINGS[p.k].short;
    const o = SETTINGS[p.k].choices[p.to];
    return p.k === "nib" ? o + " nib" : p.k === "texture" ? o : o + " paint";
  }));
}
function makeVariations(b, locks) {
  const out = [];
  for (let i = 0; i < 6; i++) {
    const pushes = direction(b, locks), v = applyPushes(b, pushes, locks, newSeed());
    v.name = b.builtin ? pick(NAMES_ADJ) + "-" + b.name.toLowerCase() : kinName(b.name);
    out.push({ b: v, caption: describePushes(pushes) });
  }
  return out;
}

// ---------- built-ins: the app's four tools, as their Wild-engine approximations (BuiltinBrush.java) ----------
function builtin(kind, name, style, blurb, seed, dials) {
  const b = Object.assign(blank(kind, seed, name), dials);
  b.style = style; b.builtin = true; b.blurb = blurb;
  return normalize(b);
}
const BUILT = [
  builtin("ink", "Ink", "Fineliner", "steady round line", 11, { nib: "round", angle: 45, pressure: .55, taperOut: .12 }),
  builtin("ink", "Fude", "Brush pen", "swells and tapers", 12, { nib: "chisel", aspect: .34, angle: 40, pressure: 1, taperIn: .35, taperOut: .6, speed: .3 }),
  builtin("paint", "Wash", "Gouache", "flat, even tone", 13, { mode: "flat", pressure: .1, soft: .25 }),
  builtin("paint", "Watercolor", "Watercolor", "soft blooms", 14, { mode: "wet", pressure: .6, soft: .45, gran: .2, bloom: .35, drying: .55 })
];

;
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

;
// ===================================================================================================================
// Line and wash meeting at lift (pen-paint-blend's settleA): the line loosens in wet wash, the wash pulls feathers out
// of it, the rim pools where they cross. Only in Drying, and only where a line and a body meet; Wet 0 and Wet 1 with
// no line never come here, so old brushes are untouched. Writes L in place and the body's darkness into D.
// ===================================================================================================================
function settleBlend(s, R, rect) {
  const P = R.parts, { w, low, mid } = s, L = s.L, W = s.W;
  const mixing = P.stampLine && P.bodyAmt > 0;
  let R0;
  if (mixing) {
    const g = 6, X0 = Math.max(0, rect[0] - g), Y0 = Math.max(0, rect[1] - g), X1 = Math.min(s.w - 1, rect[2] + g), Y1 = Math.min(s.h - 1, rect[3] + g);
    const rw = X1 - X0 + 1;
    R0 = [X0, Y0, rw, Y1 - Y0 + 1];
    const LS = lazyBuf(s, "lineSave"), BS = lazyBuf(s, "bodySave");
    for (let y = Y0; y <= Y1; y++) { const a0 = y * w + X0, a1 = a0 + rw; LS.set(L.subarray(a0, a1), a0); BS.set(W.subarray(a0, a1), a0); }
    const k = smooth(P.w * 1.5), reachF = 1 + Math.round(P.w);
    for (let y = Y0; y <= Y1; y++) for (let i = y * w + X0, end = i + rw; i < end; i++) {
      const l = LS[i];
      if (l <= 0 || BS[i] <= 0) continue;
      const soft = clamp(BS[i] * 1.5) * k * .9 * clamp((low[i] - .38) * 3.2 + .15 * (mid[i] - .5));
      if (soft <= 0) continue;
      const nl = l * (1 - .72 * soft);
      L[i] = l >= .5 ? nl : Math.max(nl, Math.min(L[i], l));
      W[i] += (l - nl) * 1.2;
      const halo = l * soft * .5, x = i - y * w;
      for (let dy = -reachF; dy <= reachF; dy++) for (let dx = -reachF; dx <= reachF; dx++) {
        const xx = x + dx, yy = y + dy;
        if (xx < X0 || yy < Y0 || xx > X1 || yy > Y1 || (dx === 0 && dy === 0)) continue;
        const v = halo / (1 + Math.abs(dx) + Math.abs(dy)), ii = yy * w + xx;
        if (LS[ii] < .5 && v > L[ii]) L[ii] = v;
      }
    }
  }
  if (P.lineWet > 0) {
    const k = P.lineWet * LINE_WET_K, along = .3 * P.bodyAmt;
    for (let y = rect[1]; y <= rect[3]; y++) for (let x = rect[0]; x <= rect[2]; x++) { const i = y * w + x, v = L[i] * k; if (v > 0) W[i] = Math.max(W[i], v) + along * L[i]; }
  }
  if (!(P.bodyAmt > 0 || P.lineWet > 0)) return rect;
  const diffuse = P.diffuse > 0 ? (Q, X0, Y0, rw, rh) => {
    const B = blur(blur(Q, rw, rh, P.diffR), rw, rh, Math.max(1, P.diffR >> 1)), m = P.diffuse;
    for (let j = 0; j < Q.length; j++) Q[j] = Q[j] * (1 - .45 * m) + B[j] * m * 1.1;
  } : null;
  const rT = Math.round(3 + 9 * P.w);
  const prep = mixing ? (Q, X0, Y0, rw, rh) => {
    if (diffuse) diffuse(Q, X0, Y0, rw, rh);
    const LS = s.lineSave, BS = s.bodySave, [mx0, my0, mw, mh] = R0, H = s.h;
    const hw = (rw + 1) >> 1, hh = (rh + 1) >> 1, down = new Float32Array(hw * hh);
    for (let y = Math.max(Y0, my0); y < Math.min(Y0 + rh, my0 + mh); y++) {
      const row = ((y - Y0) >> 1) * hw;
      for (let x = Math.max(X0, mx0); x < Math.min(X0 + rw, mx0 + mw); x++) { const l = LS[y * w + x]; if (l > 0) down[row + ((x - X0) >> 1)] += l * .25; }
    }
    const BL = blur(down, hw, hh, Math.max(1, rT >> 1)), tk = smooth(P.w * 1.4);
    for (let y = 0; y < rh; y++) {
      const fy = Math.max(0, y / 2 - .25), hy0 = Math.min(hh - 1, fy | 0), hy1 = Math.min(hh - 1, hy0 + 1), ty = fy - hy0, Y = y + Y0;
      for (let x = 0; x < rw; x++) {
        if (BL[hy0 * hw + (x >> 1)] < .006 && BL[hy1 * hw + (x >> 1)] < .006) continue;
        const fx = Math.max(0, x / 2 - .25), hx0 = Math.min(hw - 1, fx | 0), hx1 = Math.min(hw - 1, hx0 + 1), tx = fx - hx0;
        const bl = lerp(lerp(BL[hy0 * hw + hx0], BL[hy0 * hw + hx1], tx), lerp(BL[hy1 * hw + hx0], BL[hy1 * hw + hx1], tx), ty);
        if (bl < .012) continue;
        const j = y * rw + x, X = x + X0, i = Y * w + X, br = BS[i], inWash = clamp(br * 2.5);
        const reach = clamp((bl * 5 - (.45 - .25 * tk) - .45 * (low[i] - .5) - 1.1 * (mid[i] - .5)) * 4);
        let add = reach * clamp(bl * 3) * .9 * tk * (.35 + .65 * inWash);
        if (br > 0 && X >= 3 && Y >= 3 && X < w - 3 && Y < H - 3) {
          const rim = Math.max(0, br - (BS[i - 3] + BS[i + 3] + BS[i - 3 * w] + BS[i + 3 * w]) * .25);
          add += rim * clamp(bl * 7) * 3.2 * tk;
        }
        Q[j] += add;
      }
    }
    for (let y = my0; y < my0 + mh; y++) { LS.fill(0, y * w + mx0, y * w + mx0 + mw); BS.fill(0, y * w + mx0, y * w + mx0 + mw); }
  } : diffuse;
  const out = settleBody(s, W, s.D, R.look, rect, R.bodySize, prep, prep ? Math.max(P.diffR * 2, mixing ? rT * 2 : 0) : 0);
  return union(rect, out);
}

// ===================================================================================================================
// Water (ink-and-water): after a Drying wash lifts, its pixels stay wet for the drying time. Suspended pigment settles
// into the paper's valleys, and a wash that "takes ink" lets a pen line drawn into it plume. Changes only darken the page.
// Adapted from the wash layer to the page's tones: every stain multiplies the page (and the live stroke's base).
// ===================================================================================================================
const TPS = 10;
const wetMsOf = d => Math.round(750 + (20000 - 750) * d * d);
const wetTicksOf = d => Math.max(3, Math.round(wetMsOf(d) * TPS / 1000));
const W_TILE = 32, PARCEL_CAP = 1200, W_FT = 256;
function hashPts(pts) { let h = 0x811c9dc5; for (const p of pts) { h = Math.imul(h ^ Math.round(p.x * 4), 16777619); h = Math.imul(h ^ Math.round(p.y * 4), 16777619); } return h >>> 0; }
function waterTile(cell, r) {
  const g = W_FT / cell, lat = new Float32Array(g * g), out = new Float32Array(W_FT * W_FT);
  for (let i = 0; i < lat.length; i++) lat[i] = r();
  for (let y = 0; y < W_FT; y++) {
    const fy = y / cell, iy = fy | 0, sy = smooth(fy - iy);
    for (let x = 0; x < W_FT; x++) {
      const fx = x / cell, ix = fx | 0, sx = smooth(fx - ix);
      const a = lat[iy % g * g + ix % g], b = lat[iy % g * g + (ix + 1) % g], c = lat[(iy + 1) % g * g + ix % g], d = lat[(iy + 1) % g * g + (ix + 1) % g];
      out[y * W_FT + x] = lerp(lerp(a, b, sx), lerp(c, d, sx), sy);
    }
  }
  return out;
}
function sampleTile(t, x, y) {
  const ix = Math.floor(x), iy = Math.floor(y), tx = x - ix, ty = y - iy;
  const x0 = ix & 255, x1 = (ix + 1) & 255, y0 = (iy & 255) << 8, y1 = ((iy + 1) & 255) << 8;
  return lerp(lerp(t[y0 + x0], t[y0 + x1], tx), lerp(t[y1 + x0], t[y1 + x1], tx), ty);
}
const angDiff = (a, b) => { let d = a - b; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI; return d; };
const W_KERNELS = new Map();
function waterKernel(r) {
  const key = Math.max(4, Math.min(24, Math.round(r * 4)));
  let k = W_KERNELS.get(key);
  if (!k) {
    const rr = key / 4, R = Math.ceil(rr * 1.3), n = 2 * R + 1, wts = new Float32Array(n * n);
    for (let y = -R; y <= R; y++) for (let x = -R; x <= R; x++) wts[(y + R) * n + x + R] = Math.max(0, Math.exp(-(x * x + y * y) / (rr * rr)) - .05);
    let total = 0; for (const v of wts) total += v;
    k = { R, n, wts, total }; W_KERNELS.set(key, k);
  }
  return k;
}
class Water {
  constructor(surf, seed) {
    const { w, h } = surf, n = w * h;
    this.s = surf; this.w = w; this.h = h; this.seed = seed >>> 0; this.tick = 0; this.base = null;
    this.wetEnd = new Float32Array(n); this.wetLife = new Float32Array(n).fill(1);
    this.keep = new Float32Array(n).fill(1); this.seen = new Float32Array(n);
    this.plumeAt = new Float32Array(n); this.veinAt = new Uint8Array(n);
    this.trail = null; this.trail2 = null;
    this.tw = Math.ceil(w / W_TILE); this.th = Math.ceil(h / W_TILE); this.trailTiles = new Set();
    this.patches = []; this.parcels = []; this.sources = [];
    this.r = rng(this.seed ^ 0x1a7e5);
    const fr = rng(this.seed ^ 0xf10e);
    this.psiA = waterTile(64, fr); this.psiB = waterTile(64, fr); this.psiC = waterTile(32, fr);
    this.height = new Float32Array(n); this.depth = new Float32Array(n);
    this.dirty = null; this.lastEnd = 0; this.cost = { ms: 0, ticks: 0, peak: 0 };
    const raw = new Float32Array(n);
    for (let i = 0; i < n; i++) raw[i] = .65 * surf.fine[i] + .35 * surf.mid[i];
    const b = blur(raw, w, h, 1);
    for (let i = 0; i < n; i++) this.height[i] = .5 * raw[i] + .5 * b[i];
  }
  // e: how erasable the paint is (the Erasable dial): what isn't erasable goes into the kept tones too.
  mul(i, f, e = 1) {
    this.s.page[i] *= f; if (this.base) this.base[i] *= f;
    const K = this.s.kept;
    if (K && e < 1) { const g = 1 - (1 - e) * (1 - f); K[i] = Math.min(PAPER_TONE, K[i] * g); if (this.base && this.s.keptBase) this.s.keptBase[i] = Math.min(PAPER_TONE, this.s.keptBase[i] * g); }
  }
  seep(i) { const b = this.s.blob[i] * .7 + this.s.low[i] * .3; return smooth((b - .42) * 3); }
  wetI(i) { const e = this.wetEnd[i] - this.tick; return e <= 0 ? 0 : Math.min(1, e / this.wetLife[i]); }
  wetAt(x, y) { if (x < 0 || y < 0 || x >= this.w || y >= this.h) return 0; return this.wetI((y | 0) * this.w + (x | 0)); }
  get wet() { return this.lastEnd > this.tick || this.parcels.length > 0 || this.patches.length > 0 || this.sources.length > 0; }
  mark(x0, y0, x1, y1) {
    x0 = Math.max(0, x0 | 0); y0 = Math.max(0, y0 | 0); x1 = Math.min(this.w - 1, Math.ceil(x1)); y1 = Math.min(this.h - 1, Math.ceil(y1));
    const d = this.dirty;
    this.dirty = d ? [Math.min(d[0], x0), Math.min(d[1], y0), Math.max(d[2], x1), Math.max(d[3], y1)] : [x0, y0, x1, y1];
  }
  takeDirty() { const d = this.dirty; this.dirty = null; return d; }
  curl(x, y) {
    const ph = this.tick * .045, c = Math.cos(ph), s = Math.sin(ph), A = this.psiA, B = this.psiB, C = this.psiC, dep = this.depth, w = this.w, h = this.h;
    const psi = (u, v) => { const xi = u < 0 ? 0 : u >= w ? w - 1 : u | 0, yi = v < 0 ? 0 : v >= h ? h - 1 : v | 0;
      return (sampleTile(A, u, v) * c + sampleTile(B, u, v) * s + .6 * sampleTile(C, u, v) - .8) * dep[yi * w + xi]; };
    return [(psi(x, y + 2) - psi(x, y - 2)) * 30, -(psi(x + 2, y) - psi(x - 2, y)) * 30];
  }
  // A Drying wash lands: water where it reached for its drying time; a share f of its pigment is still suspended
  // (the caller has laid only 1 - f of it) and settles into the valleys as it dries. plume: does it take ink.
  addWet(X0, Y0, rw, rh, D, drying, settle, strokeSeed, plume, vein, e = 1) {
    const { w } = this, N = wetTicksOf(drying), born = this.tick, end = born + N, f = .45 * settle;
    const shown = new Float32Array(rw * rh);
    for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
      const j = y * rw + x, d = D[j];
      if (d <= 0) continue;
      const i = (y + Y0) * w + x + X0;
      if (d > .004 && end > this.wetEnd[i]) { this.wetEnd[i] = end; this.wetLife[i] = N; this.plumeAt[i] = plume; this.veinAt[i] = vein ? 1 : 0; }
      shown[j] = d * (1 - f);
    }
    this.lastEnd = Math.max(this.lastEnd, end);
    {
      const m = new Float32Array(rw * rh);
      for (let j = 0; j < m.length; j++) m[j] = D[j] > .004 ? 1 : 0;
      const b = blur(blur(m, rw, rh, 4), rw, rh, 3);
      for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
        const j = y * rw + x, v = m[j] ? clamp((b[j] - .5) * 2.4) : 0, i = (y + Y0) * w + x + X0;
        if (v > this.depth[i]) this.depth[i] = v;
      }
    }
    this.mark(X0, Y0, X0 + rw - 1, Y0 + rh - 1);
    if (f <= 0) return;
    const cw = (rw + 1) >> 1, ch = (rh + 1) >> 1, S = new Float32Array(cw * ch), H = new Float32Array(cw * ch);
    const Smax = new Float32Array(cw * ch), M = new Float32Array(cw * ch), vfn = new Float32Array(rw * rh), fx = new Float32Array(cw * ch), fy = new Float32Array(cw * ch);
    // Each cell's darkest pixel wet: settling takes a pixel to .95 at most, or to that if darker (the app's DryingWater.darkest).
    const Dk = new Float32Array(cw * ch);
    const rr = rng((strokeSeed ^ 0x5e771e) >>> 0), G = 2.4 * settle, cells = [];
    for (let cy = 0; cy < ch; cy++) for (let cx = 0; cx < cw; cx++) {
      const c = cy * cw + cx;
      let sum = 0, n = 0, hs = 0, vs = 0, most = 0;
      for (let v = 0; v < 2; v++) for (let u = 0; u < 2; u++) {
        const x = 2 * cx + u, y = 2 * cy + v; if (x >= rw || y >= rh) continue;
        const j = y * rw + x, hh = this.height[(y + Y0) * w + x + X0];
        sum += D[j]; n++; hs += hh; vfn[j] = Math.exp(G * (.5 - hh)); vs += vfn[j]; most = Math.max(most, D[j]);
      }
      Dk[c] = most;
      H[c] = hs / n;
      const noise = rr();
      if (sum / n <= .003) continue;
      for (let v = 0; v < 2; v++) for (let u = 0; u < 2; u++) { const x = 2 * cx + u, y = 2 * cy + v; if (x < rw && y < rh) vfn[y * rw + x] *= n / vs; }
      S[c] = f * sum / n * (1 + .8 * settle * (noise - .5));
      Smax[c] = f * sum / n * 2.4;
      M[c] = 1; cells.push(c);
      const [ux, uy] = this.curl(X0 + 2 * cx + 1, Y0 + 2 * cy + 1); fx[c] = ux; fy[c] = uy;
    }
    const Wb = blur(blur(M, cw, ch, 3), cw, ch, 2);
    this.patches.push({ X0, Y0, rw, rh, cw, ch, S, Smax, Sn: new Float32Array(cw * ch), H, M, Wb, vfn, fx, fy, Dk, shown, cells: Int32Array.from(cells), born, N, st: settle, lam: 2.1 / N, e });
  }
  stepPatch(P) {
    const age = this.tick - P.born, { cw, ch, rw, rh, S, Sn, Smax, H, M, Wb, fx, fy, cells } = P;
    const last = age >= P.N - 1, wet = 1 - age / P.N, v = 2 * Math.pow(Math.max(0, wet), 1.4), st = P.st;
    Sn.set(S);
    if (!last) {
      const B = blur(blur(S, cw, ch, 1), cw, ch, 1);
      const kf = .5 * st * v, kv = 1.5 * st * v, ke = 2.2 * st * v * (1 - wet), kd = .1 * st * v, dif = .06 + .04 * v;
      const flow = (c, n, u) => {
        const flux = u > 0 ? S[c] * u * Math.max(0, 1 - S[n] / Smax[n]) : S[n] * u * Math.max(0, 1 - S[c] / Smax[c]);
        const d = (S[c] - S[n]) * dif;
        Sn[c] -= flux + d; Sn[n] += flux + d;
      };
      for (let k = 0; k < cells.length; k++) {
        const c = cells[k], x = c % cw;
        if (x + 1 < cw && M[c + 1]) { const n = c + 1; let u = kf * (B[n] - B[c]) / (B[n] + B[c] + 1e-4) + kv * (H[c] - H[n]) + ke * (Wb[c] - Wb[n]) + kd * (fx[c] + fx[n]) * .5; u = u < -.3 ? -.3 : u > .3 ? .3 : u; flow(c, n, u); }
        if (c + cw < cw * ch && M[c + cw]) { const n = c + cw; let u = kf * (B[n] - B[c]) / (B[n] + B[c] + 1e-4) + kv * (H[c] - H[n]) + ke * (Wb[c] - Wb[n]) + kd * (fy[c] + fy[n]) * .5; u = u < -.3 ? -.3 : u > .3 ? .3 : u; flow(c, n, u); }
      }
    }
    const q = last ? 1 : 1 - Math.exp(-2 * P.lam * (.4 + 1.2 * age / P.N)), { vfn, shown, X0, Y0, Dk } = P, w = this.w;
    for (let k = 0; k < cells.length; k++) {
      const c = cells[k];
      if (Sn[c] < 0) Sn[c] = 0;
      const dq = Sn[c] * q;
      if (dq <= 0) continue;
      Sn[c] -= dq;
      const cx = (c % cw) * 2, cy = ((c / cw) | 0) * 2, cap = Math.max(.95, Dk[c]);
      for (let v2 = 0; v2 < 2; v2++) for (let u = 0; u < 2; u++) {
        const x = cx + u, y = cy + v2; if (x >= rw || y >= rh) continue;
        const j = y * rw + x, old = shown[j], nd = Math.min(cap, old + dq * vfn[j]);
        if (nd <= old) continue;
        shown[j] = nd;
        this.mul((y + Y0) * w + x + X0, (1 - nd) / (1 - old), P.e);
      }
    }
    P.S = Sn; P.Sn = S;
    this.mark(X0, Y0, X0 + rw - 1, Y0 + rh - 1);
    return last;
  }
  stain(x, y, amt, r, e = 1) {
    if (amt <= 0) return;
    const { w, h } = this, K = waterKernel(r), cx = Math.floor(x), cy = Math.floor(y);
    let sum = 0;
    for (let v = -K.R; v <= K.R; v++) { const py = cy + v; if (py < 0 || py >= h) continue;
      for (let u = -K.R; u <= K.R; u++) { const px = cx + u; if (px < 0 || px >= w) continue;
        if (this.wetEnd[py * w + px] >= this.tick - 1 && this.wetEnd[py * w + px] > 0) sum += K.wts[(v + K.R) * K.n + u + K.R]; } }
    if (sum <= 0) { if (cx >= 0 && cy >= 0 && cx < w && cy < h) { this.mul(cy * w + cx, 1 - Math.min(.6, amt), e); this.mark(cx, cy, cx, cy); } return; }
    const k = amt / sum;
    for (let v = -K.R; v <= K.R; v++) { const py = cy + v; if (py < 0 || py >= h) continue;
      for (let u = -K.R; u <= K.R; u++) { const px = cx + u; if (px < 0 || px >= w) continue;
        const i = py * w + px; if (this.wetEnd[i] < this.tick - 1 || this.wetEnd[i] <= 0) continue;
        const a = Math.min(.5, K.wts[(v + K.R) * K.n + u + K.R] * k);
        if (a > 0) this.mul(i, 1 - a, e); } }
    this.mark(cx - K.R, cy - K.R, cx + K.R, cy + K.R);
  }
  spawn(x, y, load, vx, vy, opt) {
    const p = { x, y, vx, vy, h: Math.atan2(vy, vx), load, load0: load, owe: 0, ticks: 0, min: Math.max(.02, load * .12), age: 0, ox: x, oy: y, pl: opt.plume, style: opt.style, e: opt.e ?? 1 };
    this.stain(x, y, load * .05, 2.2, p.e); p.load *= .95;
    this.parcels.push(p);
  }
  addSource(x, y, mass, nx, ny, opt, Q) {
    const n = Math.hypot(nx, ny) || 1;
    this.sources.push({ x, y, mass, nx: nx / n, ny: ny / n, opt, Q, q: Math.max(Q * .4, mass * .06) });
  }
  stepSources() {
    const keep = [];
    for (const src of this.sources) {
      const wet = this.wetAt(src.x, src.y);
      if (wet <= 0) { this.stain(src.x, src.y, src.mass, Math.min(8, 3 + 2 * Math.sqrt(src.mass)), src.opt.e ?? 1); continue; }
      const i = (src.y | 0) * this.w + (src.x | 0), left = Math.max(1, this.wetEnd[i] - this.tick);
      let e = Math.min(src.mass, Math.max(src.q * (.6 + .6 * wet), src.mass / (left * .5)));
      for (let k = 0; k < 4 && e > .02 && this.parcels.length < PARCEL_CAP * 2; k++) {
        const carry = Math.min(e, src.Q * 2);
        e -= carry; src.mass -= carry;
        const side = this.r() < .5 ? -1 : 1, push = (.6 + .5 * this.r()) * (.6 + 1.6 * src.opt.plume) * wet;
        const a = (this.r() - .5) * 1.1, ca = Math.cos(a), sa = Math.sin(a), vx = side * (src.nx * ca - src.ny * sa) * push, vy = side * (src.nx * sa + src.ny * ca) * push;
        this.spawn(src.x + vx, src.y + vy, carry, vx, vy, src.opt);
      }
      if (src.mass > .02) keep.push(src);
    }
    this.sources = keep;
  }
  // After a live line stamp: line coverage new on wet pixels of a wash that takes ink gives up its loose share.
  touchLine(rect, st) {
    if (emptyRect(rect)) return;
    const { w } = this, sc = this.s.L, x0 = Math.max(0, rect[0]), x1 = Math.min(w - 1, rect[2]), y0 = Math.max(0, rect[1]), y1 = Math.min(this.h - 1, rect[3]);
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const i = y * w + x, c = sc[i];
      if (c <= this.seen[i]) continue;
      const d = c - this.seen[i]; this.seen[i] = c;
      const pl = this.plumeAt[i];
      if (pl <= 0) continue;
      const wet = this.wetI(i);
      if (wet <= 0) continue;
      if (this.keep[i] === 1) this.keep[i] = 1 - clamp(pl * Math.pow(wet, 1.2) * .85, 0, .8) * (.25 + .75 * this.seep(i));
      st.acc += d * (1 - this.keep[i]) * st.loadK * (1 + 11 * pl) * (.25 + .75 * wet);
      if (st.acc >= st.Qs) { st.acc -= st.Qs; if (this.parcels.length > PARCEL_CAP * .7 && st.Q < st.Q0 * 4) { st.Q *= 1.25; st.Qs *= 1.25; } this.addSource(x + .5, y + .5, st.Qs, -st.dy, st.dx, { plume: pl, style: this.veinAt[i] ? "vein" : "drift", e: st.e }, st.Q); }
      st.ax = x; st.ay = y; st.pl = pl; st.vein = this.veinAt[i];
    }
  }
  // The line is laid: flush what it still owes and forget what it let go.
  lineDone(rect, st) {
    if (st.acc > .05 && st.pl) this.addSource(st.ax + .5, st.ay + .5, st.acc, -st.dy, st.dx, { plume: st.pl, style: st.vein ? "vein" : "drift", e: st.e }, st.Q);
    const g = 40, w = this.w;
    for (let y = Math.max(0, rect[1] - g); y <= Math.min(this.h - 1, rect[3] + g); y++) {
      const a = y * w + Math.max(0, rect[0] - g), b = y * w + Math.min(w - 1, rect[2] + g) + 1;
      this.keep.fill(1, a, b); this.seen.fill(0, a, b);
    }
  }
  sense(p, x, y) {
    if (this.wetAt(x, y) <= 0) return -1;
    const i = (y | 0) * this.w + (x | 0);
    return this.trail[i] + (this.s.page[i] < PAPER_TONE * .5 && Math.hypot(x - p.ox, y - p.oy) > 30 ? 1.5 : 0);
  }
  stepParcels() {
    const P = this.parcels, n0 = P.length, out = [], w = this.w, crowded = n0 > PARCEL_CAP;
    for (let k = 0; k < n0; k++) {
      const p = P[k], wet = this.wetAt(p.x, p.y), vein = p.style === "vein";
      const heft = clamp(Math.sqrt(p.load0 / 3), .8, 2), rad = vein ? Math.min(4, (1.5 + p.age * .03 + p.ticks * .03) * heft) : Math.min(5, (2.6 + p.age * .05 + p.ticks * .07) * heft);
      p.ticks++;
      if (wet <= 0 || p.load < (crowded ? 2.5 * p.min : p.min)) { this.stain(p.x, p.y, p.load + p.owe, rad + 1.5, p.e); continue; }
      const sp = Math.pow(wet, .8), [fx, fy] = this.curl(p.x, p.y), flow = 1.2 + 3.6 * p.pl;
      let vx, vy;
      if (!this.trail) { this.trail = new Float32Array(w * this.h); this.trail2 = new Float32Array(w * this.h); }
      if (!vein) {
        const T = this.trail, xi = clamp(p.x | 0, 4, w - 5), yi = clamp(p.y | 0, 4, this.h - 5), i = yi * w + xi;
        const gx = T[i + 4] - T[i - 4], gy = T[i + 4 * w] - T[i - 4 * w], tot = T[i + 4] + T[i - 4] + T[i + 4 * w] + T[i - 4 * w] + 2;
        const kp = 1.4 * (.5 + p.pl);
        vx = (fx * flow + p.vx - kp * gx / tot) * sp + (this.r() - .5) * .35 * sp; vy = (fy * flow + p.vy - kp * gy / tot) * sp + (this.r() - .5) * .35 * sp;
      } else {
        const SD = 9 + 5 * p.pl, sa = .5, ra = .12, c = Math.cos, s = Math.sin;
        const F = this.sense(p, p.x + c(p.h) * SD, p.y + s(p.h) * SD), L = this.sense(p, p.x + c(p.h - sa) * SD, p.y + s(p.h - sa) * SD), R = this.sense(p, p.x + c(p.h + sa) * SD, p.y + s(p.h + sa) * SD);
        if (p.age < 10 || (F >= L && F >= R)) { } else if (L > R) p.h -= ra; else if (R > L) p.h += ra; else p.h += this.r() < .5 ? -ra : ra;
        if (fx || fy) p.h += .12 * angDiff(Math.atan2(fy, fx), p.h);
        p.h += (this.r() - .5) * .1;
        const v0 = (.8 + 1.4 * p.pl) * sp;
        vx = Math.cos(p.h) * v0 + p.vx * sp; vy = Math.sin(p.h) * v0 + p.vy * sp;
      }
      p.vx *= .94; p.vy *= .94;
      const dist = Math.hypot(vx, vy), n = Math.max(1, Math.ceil(dist / Math.max(.8, rad * .9)));
      const life = this.wetLife[(p.y | 0) * w + (p.x | 0)], reach = 20 + 50 * p.pl;
      const kdep = (1 - Math.exp(-dist / n / reach)) + 1.6 / life / n;
      for (let sub = 0; sub < n; sub++) {
        const nx = p.x + vx / n, ny = p.y + vy / n;
        if (this.wetAt(nx, ny) <= .01) { p.vx = -p.vx * .5; p.vy = -p.vy * .5; p.h += Math.PI * (.7 + .6 * this.r()); break; }
        p.x = nx; p.y = ny; p.age += dist / n;
        const amt = p.load * kdep; p.load -= amt; p.owe += amt;
        if (p.owe > .05 * p.load0) { this.stain(p.x, p.y, p.owe, rad, p.e); p.owe = 0; }
        if (!vein || p.age > 10) { const i = (p.y | 0) * w + (p.x | 0); this.trail[i] += 1; this.trailTiles.add(((p.y / W_TILE) | 0) * this.tw + ((p.x / W_TILE) | 0)); }
      }
      if (this.parcels.length + out.length - k < PARCEL_CAP && p.load > .6 && this.r() < (.02 + .05 * p.pl) * sp) {
        const a = (this.r() < .5 ? -1 : 1) * (.35 + .45 * this.r()), ca = Math.cos(a), sa2 = Math.sin(a);
        const q = { ...p, owe: 0, load: p.load * .45, load0: p.load0 * .45, min: p.min * .45, vx: p.vx * ca - p.vy * sa2, vy: p.vx * sa2 + p.vy * ca, h: p.h + a };
        if (!vein && Math.hypot(q.vx, q.vy) < .3) { q.vx += Math.cos(p.h + a) * .4; q.vy += Math.sin(p.h + a) * .4; }
        p.load *= .55; out.push(q);
      }
      out.push(p);
    }
    for (let k = n0; k < P.length; k++) out.push(P[k]);
    this.parcels = out;
    if (this.trailTiles.size && this.tick % 2 === 0) this.stepTrail();
  }
  stepTrail() {
    const { w, h, trail: T, trail2: T2 } = this, K = 3, pers = .93;
    for (const t of [...this.trailTiles]) {
      const tx = (t % this.tw) * W_TILE, ty = ((t / this.tw) | 0) * W_TILE;
      let any = false;
      for (let y = Math.max(1, ty); y < Math.min(h - 1, ty + W_TILE); y++) for (let x = Math.max(1, tx); x < Math.min(w - 1, tx + W_TILE); x++) {
        const i = y * w + x;
        if (this.wetEnd[i] <= this.tick) { T2[i] = 0; continue; }
        const v = pers * (T[i] * K + T[i - 1] + T[i + 1] + T[i - w] + T[i + w]) / (4 + K);
        T2[i] = v > .002 ? v : 0; if (T2[i]) any = true;
      }
      if (!any) this.trailTiles.delete(t);
    }
    for (const t of this.trailTiles) {
      const tx = (t % this.tw) * W_TILE, ty = ((t / this.tw) | 0) * W_TILE;
      for (let y = Math.max(1, ty); y < Math.min(h - 1, ty + W_TILE); y++) { const a = y * w + Math.max(1, tx), b = y * w + Math.min(w - 1, tx + W_TILE); T.set(T2.subarray(a, b), a); }
    }
  }
  step() {
    const t0 = now();
    if (this.sources.length) this.stepSources();
    if (this.parcels.length) this.stepParcels();
    this.patches = this.patches.filter(P => (this.tick - P.born) % 2 === 1 && (this.tick - P.born) < P.N - 1 ? true : !this.stepPatch(P));
    this.tick++;
    const ms = now() - t0; this.cost.ms += ms; this.cost.ticks++; this.cost.peak = Math.max(this.cost.peak, ms);
  }
  dryAll() { let n = 0; while (this.wet && n++ < 100000) this.step(); }
}

// ===================================================================================================================
// Compositing a stroke onto the page: page = f(base, line coverage L, body darkness). Transparent paint and ink multiply
// (darken only); opaque paint mixes toward its own grey; impasto adds its relief. Old brushes reproduce the port's
// compose exactly (the same float32 rounding of the layer values).
// ===================================================================================================================
// A Drying body's light preview grey over under while the pen is down, by shade (R.previewShade; the app's
// ModularCompose.previewWash): past 1 (a wash darker than the old black) it darkens by the same steps over white, in
// proportion over darker paint.
function previewWash(under, shade) {
  return shade <= 1 ? under - (under - Math.min(200, under * 0.82)) * shade : Math.min(200, under * 0.82) * (1 - 55 / 200 * (shade - 1));
}
// The density a body of density d lays at lift with share f of its darkness held back to settle (the app's
// ModularStroke.held): d less that share; past the knee, the density that lays that share less darkness.
function heldDensity(d, washK, f) {
  const x = d * washK;
  return x <= KNEE ? d * (1 - f) : strength((1 - f) * laid(x)) / washK;
}
function composeStroke(st, rect, phase) {
  const { surf, R } = st, { w, fine, mid } = surf, L = surf.L, page = surf.page, base = surf.base;
  const x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(surf.w - 1, rect[2]), y1 = Math.min(surf.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const preview = phase === "preview", Bsrc = preview || R.life === "none" ? surf.W : surf.D;
  const cover = R.cover, paint = R.gray, washK = R.washK, inkK = R.inkK, body = lerp(1, 0.4, clamp(R.look.impasto || 0));
  // Opaque Lift: the dark it covers tints its paint, by twice Pick up (Smudge spec F3; MonoPaint's opaque wet pen).
  const tint = R.carry && R.carry.kind === "lift" && cover > 0 ? Math.min(1, 2 * R.carry.pick) : 0;
  const relief = !preview && R.look.impasto > 0 ? surf.relief : null, keep = st.water && st.wt ? st.water.keep : null;
  const rule = R.rule, tooth = R.lineTooth, G = rule === "charcoal" ? stickGrain(R.pen.seed) : null, fill = rule === "graphite" ? surf.fill : null;
  const hold = R.hold, spread = R.spread, grade = R.grade;
  // Erasable: the stroke's paint over the kept tones too, as much as it won't erase (all of it for ink, none for pencil).
  const kept = surf.kept && st.e < 1 ? surf.kept : null, kb = kept ? surf.keptBase : null, ke = 1 - st.e;
  // The stroke's tone at pixel i of row y over under (its line coverage c, its body d).
  const toneOf = (under, i, y, c, d) => {
    let tone = under, gray = tint && under < paint ? paint + (under - paint) * tint : paint;
    if (d > 0) {
      if (preview) {
        const t = previewWash(under, R.previewShade);
        tone = cover > 0 ? lerp(t, lerp(under, gray, Math.min(1, d)), cover) : t;
      } else {
        const dd = Math.min(1.6, d), glaze = under * (1 - Math.fround(laid(dd * washK)));
        tone = cover > 0 ? lerp(glaze, lerp(under, gray, Math.min(1, dd / body)), cover) : glaze;
        if (relief && relief[i]) tone = clamp(tone + relief[i], 0, 255);
      }
    }
    if (c > 0) {
      let a = c;
      if (rule === "ink") {
        if (tooth > 0) { const n = fine[i] * 0.65 + mid[i] * 0.35; a = clamp((a - (0.5 + tooth * 0.9 * (n - 0.5))) * 4 + 0.5); }
      } else if (rule === "charcoal") {
        // The contact rule (MEDIA-RESEARCH 5): the stick touches the high points first; pressure, a firmer binder
        // (Hold) and charcoal already there reach into the valleys. The stick's own grain, the paper's by its tooth.
        const x = i - y * w, gi = grainAt(x, y), stick = G.fine[gi] * .65 + G.mid[gi] * .35, paper = fine[i] * .65 + mid[i] * .35;
        const hgt = lerp(stick, paper, .3 + .4 * tooth), ex = 1 - under / PAPER_TONE;
        const depth = clamp(c * lerp(.62, .97, hold) + .5 * ex * (.4 + .6 * hold)), soft = .07 + .22 * spread;
        a = Math.max(c * smoothstep(1 - depth - soft, 1 - depth + soft, hgt), c * .5 * spread);
      } else if (rule === "graphite") {
        // Fill: a tooth already holding graphite takes less, and its valleys rise (burnishing, H blocking B).
        const fl = fill ? fill[i] : 0, paper = .5 + (fine[i] * .65 + mid[i] * .35 - .5) * (.35 + .65 * tooth), hp = paper + fl * (1 - paper);
        const depth = clamp(c * (.9 - .35 * grade) + .6 * fl), soft = lerp(.12, .04, grade);
        a = c * smoothstep(1 - depth - soft, 1 - depth + soft, hp) * (1 - .5 * fl * fl) + c * .15 * spread;
      }
      if (keep) a *= keep[i];
      if (a > 0) tone = tone * (1 - Math.fround(a * inkK));
    }
    return tone;
  };
  for (let y = y0; y <= y1; y++) for (let i = y * w + x0, e = y * w + x1; i <= e; i++) {
    const c = L[i], d = Bsrc[i], under = base[i];
    if (c <= 0 && d <= 0) { page[i] = under; if (kept) kept[i] = kb[i]; continue; }
    page[i] = toneOf(under, i, y, c, d);
    if (kept) { const k0 = kb[i]; kept[i] = k0 + (toneOf(k0, i, y, c, d) - k0) * ke; }
  }
}
const quantTone = v => Math.round(v / 17) * 17;
// The page on a canvas: 16 greys, as the Manta shows them.
function showPage(ctx, surf, rect) {
  const x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(surf.w - 1, rect[2]), y1 = Math.min(surf.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const rw = x1 - x0 + 1, rh = y1 - y0 + 1, img = ctx.createImageData(rw, rh), data = img.data, page = surf.page;
  for (let y = 0; y < rh; y++) for (let x = 0, i = (y + y0) * surf.w + x0, o = y * rw * 4; x < rw; x++, i++, o += 4) {
    const v = Math.round(page[i] / 17) * 17;
    data[o] = data[o + 1] = data[o + 2] = v; data[o + 3] = 255;
  }
  ctx.putImageData(img, x0, y0);
}

// ===================================================================================================================
// A stroke from pen-down to lift. Life decides when the mark is final:
//   none (Fast): final under the nib; wet (Fast-wet, default): the last few mm behind the nib keep developing while the
//   pen is down, nothing at lift; dry (Drying): the old engine's lift (taper re-laid, wash settled, line and wash mixed),
//   then the wash stays wet for its drying time (settling pigment, plumes if it takes ink).
// ===================================================================================================================
const WET_FRAME_MS = 330;
const FLOAT_STILL = 3, FLOAT_HOLD = 150;   // Float: within 3 px of pen-down, a tap or a hold (a drop); past it, a drag
const firstAt = (pts, d) => { let k = 1; while (k < pts.length - 1 && pts[k].d < d) k++; return k; };
class Stroke {
  constructor(surf, B, size, shade, host) {
    this.surf = surf; this.R = resolve(B, size, shade); this.host = host || null; this.e = this.R.erase;
    useTexture(surf, { texture: this.R.paper });
    surf.base.set(surf.page);
    // The first paint that isn't wholly erasable gives the page its kept tones (paper: until now everything erased).
    if (this.e < 1 && !surf.kept) surf.kept = new Float32Array(surf.w * surf.h).fill(PAPER_TONE);
    if (surf.kept) (surf.keptBase ??= new Float32Array(surf.w * surf.h)).set(surf.kept);
    this.water = host && host.water && host.water.wet ? host.water : null;
    if (this.water) this.water.base = surf.base;
    this.wt = this.water && this.R.lineInk && this.R.line ? { e: this.e, acc: 0, Q: Math.max(2.4, 1.8 * Math.pow(this.R.inkSize, .8)), Qs: 0, Q0: 0, loadK: this.R.inkK, dx: 1, dy: 0, ax: 0, ay: 0, pl: 0, vein: 0 } : null;
    if (this.wt) { this.wt.Qs = this.wt.Q * 10; this.wt.Q0 = this.wt.Q; }
    this.pts = []; this.done = 1; this.rect = [surf.w, surf.h, -1, -1];
    this.mL = { widest: 0, lastD: -Infinity }; this.mW = { widest: 0, lastD: -Infinity };
    this.frozenD = 0; this.swL = null; this.swW = null; this.cbuf = null; this.cd = 0;
    this.samples = 0; this.ms = 0; this.max = 0; this.times = [];
    // Paint Load (the oil paint spec): the stroke's paint, its Starting Load's share (the web has no rub on a grey), spent sample by sample.
    this.oil = this.R.loaded ? { share: this.R.loaded.start, spent: 0 } : null;
    if (this.R.float) {
      // Float joins the bath already floating (the host's), or starts one; a sample with no host floats alone and prints at lift.
      const own = !(host && host.getBath), bath = own ? new Bath(surf) : host.getBath();
      bath.e = Math.min(bath.e, this.e);
      if (bath.e < 1 && !bath.keptDry) bath.keptDry = Float32Array.from(surf.kept);
      this.fl = { bath, own, dropped: false, dragging: false, r2: 0, grown: 1, cd: 0, prev: null };
      bath.begin();
    }
  }
  // ---------- Float: the pen on the water ----------
  // A tap, or the pen held still, is a drop: the brush's own grey (clear water's is 0), pushing everything floating
  // outward. A stroke that moves off at once is a drag, with no drop.
  floatDown() {
    const F = this.R.float, fl = this.fl, p = this.pts[0], rho = dropRadius(F.filmW, F.spread, p.p);
    fl.dropped = true; fl.r2 = rho * rho; fl.prev = { x: p.x, y: p.y, d: 0 };
    fl.bath.apply({ k: "drop", x: p.x, y: p.y, r2: fl.r2, dark: F.load < .02 ? 0 : F.dropDark });
  }
  // Held still for 150 ms, the pen drops; held longer the drop keeps spreading: a tenth more area each 100 ms, up to
  // four times (marble-and-bristle).
  holdTick(t) {
    const fl = this.fl; if (!fl || fl.dragging || !this.pts.length) return null;
    const p0 = this.pts[0], last = this.pts[this.pts.length - 1];
    if (Math.hypot(last.x - p0.x, last.y - p0.y) > FLOAT_STILL) return null;
    if (!fl.dropped) { if (t - p0.t < FLOAT_HOLD) return null; this.floatDown(); }
    const want = Math.min(4, 1 + .1 * Math.max(0, t - p0.t - FLOAT_HOLD) / 100);
    if (want - fl.grown < .05) return null;
    fl.bath.apply({ k: "push", x: p0.x, y: p0.y, r2: (want - fl.grown) * fl.r2 }); fl.grown = want;
    return this.floatShow("quick");
  }
  // The pen moving: older paint is pushed aside by the paint it lays (a drop every few px, as much area as the band it
  // lays), and, with Drag or Smudge, dragged along by its tines.
  floatMove(from) {
    const F = this.R.float, fl = this.fl, pts = this.pts, sp = Math.max(2, F.filmW * .6);
    const area = F.load >= .02 ? 2 * F.filmW * sp * F.load / Math.PI : 0;
    if (!fl.dragging) {
      const p0 = pts[0], q = pts[pts.length - 1];
      if (Math.hypot(q.x - p0.x, q.y - p0.y) <= FLOAT_STILL) return this.floatShow("quick");
      fl.dragging = true; fl.prev = { x: p0.x, y: p0.y, d: 0 };
    }
    fl.bath.batching = true;
    let pushes = []; const near = 8 * F.filmW + 40;
    for (let k = Math.max(1, from); k < pts.length; k++) {
      const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
      while (fl.cd + sp <= p1.d) {
        fl.cd += sp;
        const t = seg > 0 ? clamp((fl.cd - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t);
        const pull = floatPull(F.pick, pr);
        if (pull > 0) { if (pushes.length) { fl.bath.apply({ k: "pushes", list: pushes, near }); pushes = []; } fl.bath.apply({ k: "rake", ax: fl.prev.x, ay: fl.prev.y, bx: x, by: y, sig: F.sig, tines: F.tines, slip: floatSlip(pull) }); }
        if (area > 0) pushes.push(x, y, area);
        fl.prev = { x, y, d: fl.cd };
      }
    }
    if (pushes.length) fl.bath.apply({ k: "pushes", list: pushes, near });
    fl.bath.batching = false; fl.bath.flush();
    return this.floatShow("quick");
  }
  // The bath, then this stroke's own paint over it as it will float (one grey per film), until lift traces it.
  floatShow(quality) {
    const F = this.R.float, s = this.surf, L = s.L, W = s.W, w = s.w, page = s.page;
    const r = this.fl.bath.render(quality, false, emptyRect(this.rect) ? null : this.rect);
    if (F.load >= .02 && !emptyRect(this.rect)) {
      const R0 = this.rect;
      for (let y = R0[1]; y <= R0[3]; y++) for (let i = y * w + R0[0], e = y * w + R0[2]; i <= e; i++) {
        let k = 1;
        if (W[i] >= .5) k *= 1 - F.bodyDark;
        if (L[i] >= .5) k *= 1 - F.lineDark;
        if (k < 1) page[i] *= k;
      }
    }
    return r ? (emptyRect(this.rect) ? r : union(r, this.rect)) : this.rect.slice();
  }
  // Lift: the stroke's paint becomes floating rings (its body, then its line over it), each one flat grey.
  floatLift() {
    const F = this.R.float, fl = this.fl, s = this.surf, R0 = this.rect, films = [];
    if (!fl.dragging && !fl.dropped) this.floatDown();
    if (F.load >= .02 && fl.dragging && !emptyRect(R0)) {
      const rw = R0[2] - R0[0] + 1, rh = R0[3] - R0[1] + 1;
      for (const [buf, K, cap] of [[s.W, this.R.washK, 1.6], [s.L, this.R.inkK, 1]]) {
        if (!(K > 0)) continue;
        const f = new Float32Array(rw * rh); let sum = 0, n = 0;
        for (let y = 0; y < rh; y++) for (let x = 0, i = (y + R0[1]) * s.w + R0[0]; x < rw; x++, i++) { const v = buf[i]; f[y * rw + x] = v; if (v >= .5) { sum += Math.min(cap, v); n++; } }
        if (n < 3) continue;
        const cs = traceFilm(f, rw, rh, R0[0], R0[1]);
        if (cs.length) films.push({ cs, dark: buf === s.W ? laid(sum / n * K) : clamp(sum / n * K) });
      }
    }
    const t0 = now();
    if (films.length) fl.bath.apply({ k: "film", films });
    fl.bath.cost.film += now() - t0;
    fl.bath.lastTouch = this.pts[this.pts.length - 1].t; fl.bath.dryMs = F.dryMs; fl.bath.drift = F.drift;
    for (const b of [s.L, s.W]) clearBuf(s, b, R0);
    const r = fl.own ? fl.bath.print() : fl.bath.render("full", false, R0);
    return r ? union(r, R0) : R0.slice();
  }
  // ---------- the stampers, by tip ----------
  stampL(buf, final, from, clip, win) {
    const R = this.R, s = this.surf, pts = this.pts;
    return withScratch(s, buf, () => {
      if (R.lineTip === "hake") return stampHake(s, R.lineHake, pts, R.inkSize, this.mL, R.lineHake.softK);
      if (R.lineTip === "bristles") { this.swL ??= swarmStart(s, R.lineSwarm, R.inkSize / 2, pts[0]); return swarmFeed(this.swL, pts, buf); }
      if (R.linePlaced) { s.wetBuf = R.body ? s.W : null; const r = stampPlaced(s, R.pen, R.placeBody, pts, R.inkSize, R.placeSize, final, from, win); if (this.mL.widest < R.lineReach) this.mL.widest = R.lineReach; return r; }
      return stampLine(s, R.pen, pts, R.inkSize, final, from, clip, this.mL, win);
    });
  }
  stampW(buf, final, from) {
    const R = this.R, s = this.surf, pts = this.pts;
    return withScratch(s, buf, () => {
      if (R.bodyTip === "bristles") { this.swW ??= swarmStart(s, R.bodySwarm, R.bodySize / 2, pts[0]); return swarmFeed(this.swW, pts, buf); }
      if (R.bodyTip === "placed") { s.wetBuf = null; return stampPlaced(s, R.bodyPen, R.bodyGeom, pts, R.bodyLineSize, R.bodyPlaceSize, final, from, null); }
      return stampBody(s, R.body, pts, R.bodySize, from, this.mW, final);
    });
  }
  // The line's loose share joins the wash as it is laid (Fast and Fast-wet; Drying does this at lift with the mixing).
  mergeLineWet(r, wetD, d) {
    const R = this.R, k = R.parts.lineWet * LINE_WET_K;
    if (!(k > 0) || emptyRect(r)) return;
    const s = this.surf, L = s.L, W = R.life === "wet" ? s.rawW : s.W, w = s.w, along = .3 * R.parts.bodyAmt;
    for (let y = Math.max(0, r[1]); y <= Math.min(s.h - 1, r[3]); y++) for (let i = y * w + Math.max(0, r[0]), e = y * w + Math.min(w - 1, r[2]); i <= e; i++) {
      const l = L[i]; if (l <= 0) continue;
      const v = Math.max(W[i], l * k) + along * l * .25;
      if (v > W[i]) { W[i] = v; if (wetD) wetD[i] = d; }
    }
  }
  // ---------- Fast-wet: the line's window (copied from wetInk) ----------
  wetLine() {
    const { surf, R, pts } = this, w = surf.w, total = pts[pts.length - 1].d, prev = this.frozenD;
    // The line's window only re-tapers the tail: dabs further back than the taper are already final. No taper out, no
    // window: each dab is laid once, as in Fast.
    if (R.lineStateful || !(R.pen.taperOut > 0)) {
      const r = this.stampL(surf.L, false, this.done);
      if (R.pen.splatter > 0) withScratch(surf, surf.L, () => splatterLive(surf, R.pen, pts, R.inkSize, this.done, r));
      return r;
    }
    const L = Math.min(R.windowLen, taperLens(R.pen, R.inkSize)[1] + 4), dW = Math.max(0, total - L);
    // The tail is re-tapered at most every 50 ms of pen time (the e-ink shows about three frames a second); in between,
    // new dabs are laid as they come and the next re-taper redoes them.
    const tNow = pts[pts.length - 1].t;
    if (this.lineT !== undefined && tNow - this.lineT < 50) return this.stampL(surf.L, false, this.done, null, { dMin: this.frozenD, dMax: Infinity });
    this.lineT = tNow;
    const rawL = lazyBuf(surf, "rawL");
    if (dW > prev) { this.stampL(rawL, false, firstAt(pts, prev), null, { dMin: prev, dMax: dW }); this.frozenD = dW; }
    if (R.pen.splatter > 0) withScratch(surf, rawL, () => splatterLive(surf, R.pen, pts, R.inkSize, this.done, [surf.w, surf.h, 0, 0]));
    const reach = Math.max(this.mL.widest, Math.ceil(R.inkSize * 0.9) + 2, R.lineReach || 0) + 2;
    const C = this.boxOf(Math.max(0, firstAt(pts, prev) - 1), reach + (R.pen.splatter > 0 ? 50 : 0));
    for (let y = C[1]; y <= C[3]; y++) surf.L.set(rawL.subarray(y * w + C[0], y * w + C[2] + 1), y * w + C[0]);
    const tip = pts[pts.length - 1], pk = tip.pk || tip.p;
    const fall = pk > 0 ? clamp((0.75 * pk - tip.p) / (0.45 * pk)) : 0, still = 1 - clamp(tip.v / 0.3), flick = clamp((tip.v - 1.2) / 2);
    const amt = R.pen.taperOut * Math.max(fall, still, flick), len = Math.min(Math.max(1, taperLens(R.pen, R.inkSize)[1]), R.windowLen * 0.85);
    this.stampL(surf.L, false, firstAt(pts, dW), C, { dMin: dW, dMax: Infinity, tail: amt > 0 ? { len, amt } : null });
    return C;
  }
  boxOf(k0, pad) {
    const { pts, surf } = this;
    let l = Infinity, t = Infinity, r = -Infinity, bt = -Infinity;
    for (let k = k0; k < pts.length; k++) { const p = pts[k]; if (p.x < l) l = p.x; if (p.x > r) r = p.x; if (p.y < t) t = p.y; if (p.y > bt) bt = p.y; }
    return [Math.max(0, Math.floor(l - pad)), Math.max(0, Math.floor(t - pad)), Math.min(surf.w - 1, Math.ceil(r + pad)), Math.min(surf.h - 1, Math.ceil(bt + pad))];
  }
  // ---------- Fast-wet: the body's window (copied from wetPaint): settled once per e-ink frame ----------
  wetBody(lineRect) {
    const { surf, R, pts } = this, { w, fine, mid, low, blob } = surf, total = pts[pts.length - 1].d, L = R.windowLen, dW = Math.max(0, total - L);
    const raw = lazyBuf(surf, "rawW"), wetD = lazyBuf(surf, "wetD", -1), D = surf.D;
    WET = wetD;
    let r; try { r = this.stampW(raw, false, this.done); } finally { WET = null; }
    if (R.line && lineRect) { this.mergeLineWet(lineRect, wetD, total); r = union(r, lineRect); }
    const tNow = pts[pts.length - 1].t;
    if (this.lastSettle !== undefined && tNow - this.lastSettle < WET_FRAME_MS && total - this.lastSettleD < 0.3 * L) {
      if (emptyRect(r)) return null;
      for (let y = Math.max(0, r[1]); y <= Math.min(surf.h - 1, r[3]); y++) for (let i = y * w + Math.max(0, r[0]), e = y * w + Math.min(w - 1, r[2]); i <= e; i++) if (wetD[i] >= dW && raw[i] > D[i]) D[i] = raw[i];
      return r;
    }
    this.lastSettle = tNow; this.lastSettleD = total;
    const look = R.look, cw = 1 - clamp(look.cover || 0);
    const hol = look.hollow * cw, blo = look.bloom * cw, gr = look.gran, fe = look.feather * cw, br = look.backrun * cw, imp = look.impasto || 0;
    const fr = Math.round(2 + 12 * fe), Rr = this.boxOf(Math.max(0, firstAt(pts, dW) - 1), R.bodySize * 0.55 + fr + 12);
    if (!emptyRect(r)) grow(Rr, r);
    const rw = Rr[2] - Rr[0] + 1, rh = Rr[3] - Rr[1] + 1;
    if (rw <= 0 || rh <= 0) return null;
    const P = new Float32Array(rw * rh), Wt = new Float32Array(rw * rh);
    for (let y = 0; y < rh; y++) for (let x = 0, i = (y + Rr[1]) * w + Rr[0], j = y * rw; x < rw; x++, i++, j++) { P[j] = raw[i]; if (wetD[i] >= dW) Wt[j] = raw[i]; }
    const Bh = hol > 0 ? blur(blur(P, rw, rh, 3 + Math.round(4 * hol)), rw, rh, 2) : null;
    const Bf = fe > 0 ? blur(blur(Wt, rw, rh, fr), rw, rh, Math.max(1, fr >> 1)) : null;
    const Bp = imp > 0 ? blur(P, rw, rh, Math.max(2, Math.round(3 * paintBig(look, R.bodySize)))) : null;
    const rel = imp > 0 ? lazyBuf(surf, "relief") : null;
    for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
      const j = y * rw + x, i = (y + Rr[1]) * w + x + Rr[0], p = P[j];
      let wd = wetD[i], bleedOnly = false;
      if (wd < dW) {
        if (!(p <= 0 && wd < 0 && Bf && Bf[j] > 0.02)) continue;
        bleedOnly = true; wd = wetD[i] = total;
      }
      const age = clamp((total - wd) / (0.7 * L));
      let d = p;
      if (Bh && p > 0) d = d * (1 - 0.6 * hol * age) + Math.max(0, p - Bh[j]) * hol * 5 * age;
      if (Bf && (bleedOnly || Bf[j] > p)) {
        const reach = clamp((Bf[j] - (0.42 - 0.3 * fe) - 0.45 * (low[i] - 0.5) - 0.25 * (mid[i] - 0.5)) * 6), f = reach * Bf[j] * 1.4 * fe * age;
        if (f > d) d = f;
      }
      if (d <= 0) { D[i] = 0; continue; }
      if (blo > 0) d *= clamp(1 + blo * 2.6 * age * (low[i] - 0.5) + blo * 0.5 * age * (mid[i] - 0.5), 0.25, 2);
      if (br > 0) {
        const th = 0.7 - 0.05 * br, nn = 0.55 * blob[i] + 0.45 * low[i], inside = clamp((nn - th) * 40), ring = Math.exp(-(((nn - th) / 0.02) ** 2));
        d = d * (1 - 0.4 * br * age * inside) + d * br * 0.9 * age * ring;
      }
      if (gr > 0) d *= 1 + gr * age * (1.5 * (fine[i] - 0.5) + 0.6 * (mid[i] - 0.5));
      D[i] = clamp(d, 0, 1.6);
      if (Bp && x > 0 && y > 0 && x < rw - 1 && y < rh - 1) {
        const h0 = Math.min(1, P[j]), ridge = Math.min(1, P[j + rw + 1]) - Math.min(1, P[j - rw - 1]);
        rel[i] = imp * (100 * ridge - 90 * Math.max(0, h0 - Bp[j]));
      }
    }
    return Rr;
  }
  // ---------- Carry: a dab-sized buffer that travels with the tool, picking up the page and laying it back ----------
  carryPass(from) {
    const C = this.R.carry, { pts, surf } = this;
    let rect = [surf.w, surf.h, -1, -1];
    for (let k = Math.max(1, from); k < pts.length; k++) {
      const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d, sp = C.spacing;
      while (this.cd <= p1.d) {
        const t = seg > 0 ? clamp((this.cd - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t);
        if (C.soften < 1) grow(rect, this.carryDab(x, y, pr));
        if (C.soften > 0) {
          const len = Math.hypot(p1.x - p0.x, p1.y - p0.y), sr = this.stumpDab(x, y, pr, len > 0 ? (p1.x - p0.x) / len : 0, len > 0 ? (p1.y - p0.y) / len : 0);
          if (sr) grow(rect, sr);
        }
        this.cd += sp;
      }
    }
    return rect;
  }
  // A dab of the reservoir (Smudge spec F2, F3, as tuned): each cell trades paint with the page under it. Where the
  // page is darker the brush takes Pick up of the difference, where the brush is darker it gives Lay down of it, each
  // over a radius of travel (about r / spacing dabs); what it takes is gone from the page and what it gives is gone
  // from the brush, so a line it crosses is carried on along the stroke and fades as it is laid down: more Pick up,
  // darker and longer streaks; more Lay down, stronger right behind the line and a shorter tail. Lift takes twice as
  // readily and soaks up 1 − Lay down of what it holds (a damp brush), so the track lightens. Opaque
  // Lift has no pass (its cover hides the page): what it covers tints its paint (composeStroke). Pressure scales it.
  carryDab(x, y, pr) {
    const C = this.R.carry, s = this.surf, base = s.base, w = s.w, r = C.r, S = C.stride, N = C.n, side = 2 * N + 1, K0 = s.keptBase;
    this.cbuf ??= new Float32Array(side * side).fill(NaN);
    // Keeps Paint: the paint the surface's brush kept from its last stroke, as this reservoir holds it; Clear Water: the
    // page as the stroke found it, and how wet each pixel is (the wet smudge spec).
    if (this.held === undefined) this.held = C.keep > 0 && s.heldPaint ? heldSized(s.heldPaint, N) : null;
    if (C.kind === "water" && !this.found) { this.found = Float32Array.from(base); this.wet = new Float32Array(w * s.h); }
    const press = .45 + .55 * pr, buf = this.cbuf, edge = Math.max(1, r * .3), pass = 1 / C.hits, kept = this.held, wet = this.wet;
    const lift = C.kind === "lift" || C.kind === "water";
    let take = 1 - Math.pow(1 - Math.min(.95, (lift ? 2 : 1) * C.pick * press), pass);
    // Lift soaks up 1 − Lay down of what it holds over a radius of travel: it stays damp, so it keeps lifting.
    let give = 1 - Math.pow(1 - C.lay * press, pass), soak = lift ? 1 - Math.pow(C.lay * press, pass) : 0;
    // Part stump (Smear's Soften): the reservoir trades the rest.
    if (C.soften > 0) { take *= 1 - C.soften; give *= 1 - C.soften; }
    const x0 = Math.max(0, Math.floor(x - r)), x1 = Math.min(w - 1, Math.ceil(x + r)), y0 = Math.max(0, Math.floor(y - r)), y1 = Math.min(s.h - 1, Math.ceil(y + r));
    for (let py = y0; py <= y1; py++) {
      const dy = py + .5 - y, jy = (Math.round(dy / S) + N) * side;
      for (let px = x0; px <= x1; px++) {
        const dx = px + .5 - x, dd = Math.sqrt(dx * dx + dy * dy);
        if (dd >= r) continue;
        const c = clamp((r - dd) / edge), j = jy + Math.round(dx / S) + N, i = py * w + px;
        // Only what the eraser could take moves: over kept tone k the loose paint is the page's tone over k's.
        const k = K0 ? K0[i] : PAPER_TONE, held = k < PAPER_TONE && k >= base[i], under = held ? (k > .5 ? PAPER_TONE * base[i] / k : PAPER_TONE) : base[i];
        // A cell's first touch reads the page, so a stroke never smears at its very start; Lift's starts clean, so it lifts at once.
        let bj = buf[j];
        if (bj !== bj) { bj = lift ? C.paper : under; if (kept && kept[j] === kept[j]) bj += (kept[j] - bj) * C.keep; }
        if (wet && c > wet[i]) wet[i] = c;
        // Tones: lower is darker. m > 0: the page is darker and the brush takes; m < 0: the brush gives.
        const d = bj - under, m = d * (d > 0 ? take : give) * c;
        base[i] = held ? (under + m) * k / PAPER_TONE : under + m;
        buf[j] = soak ? C.paper - (C.paper - (bj - m)) * (1 - soak * c) : bj - m;
      }
    }
    return [x0, y0, x1, y1];
  }
  // Smear's Soften (the Stump spec): a dab of MonoPaint's blending stump (ToneDabs.soften; the app's Carry.soften). Every
  // pixel within the radius moves toward the mean of the page (its loose paint) around the pixel a quarter radius behind,
  // along the stroke (80%), and around itself (20%), by .7 × strength × (1 − q)², all read before any is written; Pick up
  // is the strength, pressure scales it, spread over this stroke's dabs as MonoPaint's are over its own (.24 r apart). A
  // Rake or Split nib combs: a little stump at each tine, a pixel taken by its nearest, dragging only what it meets
  // behind it, by .7 × strength × (1 − q), so its streaks run on. Returns the rectangle it changed, or null.
  stumpDab(x, y, pr, ux, uy) {
    const C = this.R.carry, s = this.surf, base = s.base, w = s.w, h = s.h, K0 = s.keptBase, tines = C.tines;
    if (tines && ux === 0 && uy === 0) return null;
    const R = tines ? C.tineReach : C.r, press = .45 + .55 * pr;
    const wt = clamp(.7 * C.soften * C.pick * press), f0 = 1 - Math.pow(1 - wt, C.spacing / Math.max(.5, .24 * R));
    if (!(f0 > 0)) return null;
    const blur = C.blur, pX = Math.round(ux * C.r * .25), pY = Math.round(uy * C.r * .25), own = tines ? 0 : .2, comb = !!tines;
    // Only what the eraser could take moves (as carryDab): over kept tone k the loose paint is the page's tone over k's.
    const loose = i => { const k = K0 ? K0[i] : PAPER_TONE; return k < PAPER_TONE && k >= base[i] ? (k > .5 ? PAPER_TONE * base[i] / k : PAPER_TONE) : base[i]; };
    const spots = (tines || [0]).map(at => {
      const cx = x - at * uy, cy = y + at * ux;
      const x0 = Math.max(0, Math.floor(cx - R)), x1 = Math.min(w, Math.ceil(cx + R)), y0 = Math.max(0, Math.floor(cy - R)), y1 = Math.min(h, Math.ceil(cy + R));
      const sl = Math.max(0, x0 - blur - Math.abs(pX)), st = Math.max(0, y0 - blur - Math.abs(pY));
      const sr = Math.min(w, x1 + blur + Math.abs(pX)), sb = Math.min(h, y1 + blur + Math.abs(pY)), sw = sr - sl + 1;
      return { cx, cy, x0, x1, y0, y1, sl, st, sr, sb, sw, sum: null };
    });
    // Every spot's summed-area table before any pixel is written.
    for (const p of spots) {
      if (p.x1 <= p.x0 || p.y1 <= p.y0) continue;
      const sum = p.sum = new Float64Array(p.sw * (p.sb - p.st + 1));
      for (let py = p.st; py < p.sb; py++) {
        let row = 0; const o = (py - p.st + 1) * p.sw, above = o - p.sw;
        for (let px = p.sl; px < p.sr; px++) { row += loose(py * w + px); const j = px - p.sl + 1; sum[o + j] = sum[above + j] + row; }
      }
    }
    const mean = (p, px, py) => {
      const x0 = Math.max(p.sl, px - blur) - p.sl, y0 = Math.max(p.st, py - blur) - p.st, x1 = Math.min(p.sr, px + blur + 1) - p.sl, y1 = Math.min(p.sb, py + blur + 1) - p.st;
      return (p.sum[y1 * p.sw + x1] - p.sum[y0 * p.sw + x1] - p.sum[y1 * p.sw + x0] + p.sum[y0 * p.sw + x0]) / ((x1 - x0) * (y1 - y0));
    };
    let rect = null;
    spots.forEach((p, k) => {
      if (!p.sum) return;
      for (let py = p.y0; py < p.y1; py++) {
        const dy = py + .5 - p.cy;
        for (let px = p.x0; px < p.x1; px++) {
          const dx = px + .5 - p.cx, d2 = dx * dx + dy * dy, q = d2 / (R * R);
          if (q >= 1) continue;
          // A pixel is its nearest tine's.
          if (comb && spots.some((o, j) => { if (j === k) return false; const ex = px + .5 - o.cx, ey = py + .5 - o.cy, e = ex * ex + ey * ey; return e < d2 || e === d2 && j < k; })) continue;
          let m = mean(p, px, py);
          if (pX || pY) m = own * m + (1 - own) * mean(p, Math.max(0, Math.min(w - 1, px - pX)), Math.max(0, Math.min(h - 1, py - pY)));
          const i = py * w + px, k0 = K0 ? K0[i] : PAPER_TONE, held = k0 < PAPER_TONE && k0 >= base[i], u = loose(i);
          const f = f0 * (1 - q) * (comb ? 1 : 1 - q), nu = u + (m - u) * f;
          base[i] = held ? nu * k0 / PAPER_TONE : nu;
        }
      }
      const r = [p.x0, p.y0, p.x1 - 1, p.y1 - 1];
      rect = rect ? union(rect, r) : r;
    });
    return rect;
  }
  // ---------- one pointer event's samples [x, y, p, t, tx, ty]; returns the rectangle to show, or null ----------
  add(samples) {
    const t0 = now();
    let added = 0;
    for (const s of samples) if (addPt(this.pts, ...s)) { added++; if (this.oil) this.oilSpend(); }
    if (!added || this.pts.length < 2) return null;
    const R = this.R, s = this.surf, from = this.done, pts = this.pts;
    let r = [s.w, s.h, -1, -1], rl = null;
    if (R.life === "wet") {
      if (R.line) { rl = this.wetLine(); r = union(r, rl); }
      if (R.body) { const rb = this.wetBody(rl); if (rb) r = union(r, rb); }
    } else {
      if (R.line) { rl = this.stampL(s.L, false, from); r = union(r, rl); }
      if (R.body) r = union(r, this.stampW(s.W, false, from));
      if (R.life === "none" && R.line && R.body) this.mergeLineWet(rl, null, 0);
    }
    if (R.carry && R.carry.moves && !this.fl) r = union(r, this.carryPass(from));
    if (this.wt && rl && !emptyRect(rl)) {
      const a = pts[pts.length - 2], b = pts[pts.length - 1], d = Math.hypot(b.x - a.x, b.y - a.y) || 1;
      this.wt.dx = (b.x - a.x) / d; this.wt.dy = (b.y - a.y) / d;
      this.water.touchLine(rl, this.wt);
    }
    this.done = pts.length;
    if (this.fl) { if (!emptyRect(r)) grow(this.rect, r); r = this.floatMove(from); }
    else if (emptyRect(r)) r = null;
    else { composeStroke(this, r, R.life === "dry" ? "preview" : "final"); grow(this.rect, r); }
    const ms = now() - t0;
    this.samples += added; this.ms += ms; if (ms > this.max) this.max = ms; this.times.push(ms / added);
    return r;
  }
  // Paint Load (the app's LoadedPaint.Run): the newest sample spends a dab's width of paint; its own, how much of its own
  // paint the brush still lays there (1 loaded to 0 empty: holding, then falling smoothly), which every stamper reads.
  oilSpend() {
    const L = this.R.loaded, pts = this.pts, k = pts.length - 1, q = pts[k];
    if (k > 0) this.oil.spent += (q.d - pts[k - 1].d) * oilWidth(this.R, q.p, q.v);
    const len = this.oil.share * L.full, u = len > 0 ? Math.min(1, this.oil.spent / len) : 1;
    q.own = len > 0 ? 1 - u * u * (3 - 2 * u) : 0;
  }
  // ---------- lift ----------
  end() {
    const s = this.surf, R = this.R;
    if (this.pts.length === 1) { const p = this.pts[0]; this.add([[p.x + .8, p.y, p.p, p.t + 16, p.tx, p.ty]]); }
    if (this.water) this.water.base = null;
    if (this.fl) { if (!this.pts.length) { this.fl.bath.strokes.pop(); this.lift = 0; return null; } const t0 = now(), r = this.floatLift(); this.lift = now() - t0; return r; }
    if (emptyRect(this.rect)) { this.lift = 0; return null; }
    const t0 = now(), out = this.rect.slice();
    let wetPatch = null;
    if (R.life === "dry") {
      if (R.line && !R.lineStateful) { clearBuf(s, s.L, this.rect); grow(out, this.stampL(s.L, true, 1)); }
      if (R.body && !R.bodyStateful) { clearBuf(s, s.W, this.rect); this.mW.lastD = -Infinity; grow(out, this.stampW(s.W, true, 1)); } // laid afresh: spacing starts again
      if (R.line && this.wt) this.water.touchLine(out, this.wt);
      if (R.body) {
        grow(out, R.line ? settleBlend(s, R, out) : settleBody(s, s.W, s.D, R.look, out, R.bodySize));
        // What stays wet: the body's darkness; a share of it settles later (only for transparent paint).
        const f = R.cover > 0 ? 0 : .45 * R.settle, rw = out[2] - out[0] + 1, rh = out[3] - out[1] + 1, Dd = new Float32Array(rw * rh);
        for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) {
          const i = (y + out[1]) * s.w + x + out[0], d = s.D[i];
          if (d <= 0) continue;
          Dd[y * rw + x] = laid(d * R.washK);
          if (f > 0) s.D[i] = heldDensity(d, R.washK, f);
        }
        wetPatch = { X0: out[0], Y0: out[1], rw, rh, D: Dd };
      }
      composeStroke(this, out, "final");
    }
    // Clear Water: what it lifted runs out through its wet (a Slow one's share settles after lift, as a wash's).
    if (R.carry && R.carry.kind === "water" && R.carry.moves && this.found) {
      const ro = this.runOut(R.life === "dry" ? .45 * R.settle : 0, R.life === "dry");
      if (ro) { grow(out, ro.r); composeStroke(this, ro.r, "final"); if (ro.patch) wetPatch = ro.patch; }
    }
    // Keeps Paint: the surface's brush keeps what it ends with for its next stroke.
    if (R.carry && R.carry.keep > 0 && this.cbuf) {
      const kept = this.cbuf.slice();
      if (this.held) for (let j = 0; j < kept.length; j++) if (kept[j] !== kept[j]) kept[j] = this.held[j];
      s.heldPaint = { n: R.carry.n, buf: kept };
    }
    if (R.rule === "graphite") this.commitFill(out);
    if (this.wt) this.water.lineDone(out, this.wt);
    if (wetPatch && this.host) {
      const W = this.host.getWater();
      W.addWet(wetPatch.X0, wetPatch.Y0, wetPatch.rw, wetPatch.rh, wetPatch.D, R.drying, R.cover > 0 ? 0 : R.settle, (R.pen.seed ^ hashPts(this.pts)) >>> 0, R.takes ? R.plume : 0, R.takes === "vein", this.e);
    }
    for (const b of [s.L, s.W, s.D, s.rawL, s.rawW, s.relief]) clearBuf(s, b, out);
    clearBuf(s, s.wetD, out, -1);
    this.lift = now() - t0;
    return out;
  }
  // Clear Water's lift (the app's ClearWater.lift): Lay down of what it lifted (the page lighter than found) spreads
  // through the wet, softly, gathering a little at its edge, laid back as a glaze, held back by held for later; returns
  // the rectangle and, staying wet, the patch to settle; null when nothing ran out.
  runOut(held, stays) {
    const s = this.surf, C = this.R.carry, w = s.w, h = s.h, base = s.base, f0 = this.found, wet = this.wet, r = this.rect;
    const sig = Math.max(3, C.r * .8), q = Math.max(1, Math.min(6, Math.round(sig / 4))), m = Math.ceil(2 * sig) + 2 * q;
    const x0 = Math.max(0, r[0] - m), y0 = Math.max(0, r[1] - m), x1 = Math.min(w - 1, r[2] + m), y1 = Math.min(h - 1, r[3] + m);
    const cw = Math.floor((x1 - x0) / q) + 1, ch = Math.floor((y1 - y0) / q) + 1, lifted = new Float32Array(cw * ch), wc = new Float32Array(cw * ch);
    let total = 0;
    for (let y = Math.max(0, r[1]); y <= Math.min(h - 1, r[3]); y++) for (let x = Math.max(0, r[0]); x <= Math.min(w - 1, r[2]); x++) {
      const i = y * w + x; if (!(wet[i] > 0)) continue;
      const k = Math.floor((y - y0) / q) * cw + Math.floor((x - x0) / q), d = (base[i] - f0[i]) / PAPER_TONE;
      wc[k] += wet[i]; if (d > 0) { lifted[k] += d * C.lay; total += d * C.lay; }
    }
    if (!(total > 1e-3)) return null;
    const reach = Math.max(1, Math.round(.7 * sig / q)), spread = blur(blur(lifted, cw, ch, reach), cw, ch, reach), water = blur(wc, cw, ch, Math.max(1, reach >> 1));
    let kept = 0;
    for (let k = 0; k < spread.length; k++) { const mm = clamp(1.5 * water[k] / (q * q)); spread[k] *= mm * (1 + .8 * 4 * mm * (1 - mm)); kept += spread[k]; }
    if (!(kept > 0)) return null;
    const scale = Math.min(4, total / kept) / (q * q), out = [w, h, -1, -1], K0 = s.keptBase;
    const D = new Float32Array((x1 - x0 + 1) * (y1 - y0 + 1));
    for (let y = y0; y <= y1; y++) {
      const v = (y - y0 + .5) / q - .5, cy = Math.floor(v), fy = v - cy, ra = clamp(cy, 0, ch - 1) * cw, rb = clamp(cy + 1, 0, ch - 1) * cw;
      for (let x = x0; x <= x1; x++) {
        const u = (x - x0 + .5) / q - .5, cx = Math.floor(u), fx = u - cx, ca = clamp(cx, 0, cw - 1), cb = clamp(cx + 1, 0, cw - 1);
        let g = Math.min(.9, scale * ((spread[ra + ca] * (1 - fx) + spread[ra + cb] * fx) * (1 - fy) + (spread[rb + ca] * (1 - fx) + spread[rb + cb] * fx) * fy));
        // Masking fluid keeps the water off.
        if (s.resist) g *= 1 - (s.resist[y * w + x] || 0);
        if (!(g > .001)) continue;
        const i = y * w + x;
        base[i] *= 1 - g * (1 - held);
        D[(y - y0) * (x1 - x0 + 1) + x - x0] = g;
        grow(out, [x, y, x, y]);
      }
    }
    if (emptyRect(out)) return null;
    return { r: out, patch: stays ? { X0: x0, Y0: y0, rw: x1 - x0 + 1, rh: y1 - y0 + 1, D } : null };
  }
  // Pencil: the tooth holds what it took (Fill), so later strokes find it fuller.
  commitFill(r) {
    const s = this.surf, fill = lazyBuf(s, "fill"), L = s.L, w = s.w, rate = lerp(.35, .10, this.R.grade);
    for (let y = Math.max(0, r[1]); y <= Math.min(s.h - 1, r[3]); y++) for (let i = y * w + Math.max(0, r[0]), e = y * w + Math.min(w - 1, r[2]); i <= e; i++) {
      const c = L[i]; if (c <= 0) continue;
      const room = 1 - fill[i]; fill[i] = Math.min(1, fill[i] + c * Math.pow(room, .7) * rate);
    }
  }
}
// ===================================================================================================================
// The eraser (the app's EraseBothStroke): each pixel it passes goes, once a stroke, to what can't be erased: the kept
// tones, paper wherever everything was erasable. However hard it is pressed, it never takes permanent paint (the owner,
// 2026-10-16). Its width follows pressure as the app's does (InkWashSizes: a 2 px tip to the size's broadest, 24, 120 or 220 px).
// ===================================================================================================================
const ERASER_MAX = [24, 120, 220], ERASER_TIP = 2;
class Eraser {
  constructor(surf, max) { this.s = surf; this.max = max; this.seen = new Uint8Array(surf.w * surf.h); this.last = null; }
  width(p, tx, ty) { const t = tiltOf(tx, ty), lean = t ? t.lean : 0; return ERASER_TIP + (this.max - ERASER_TIP) * Math.min(1, p * p * (1 + 2 * lean)); }
  // One pointer event's samples [x, y, p, t, tx, ty]; returns the rectangle changed, or null.
  add(samples) {
    let r = null;
    for (const [x, y, p, , tx, ty] of samples) {
      const d = this.width(p, tx, ty), a = this.last || { x, y, d };
      const n = Math.max(1, Math.ceil(Math.hypot(x - a.x, y - a.y) / Math.max(1, Math.min(a.d, d) / 4)));
      for (let k = 1; k <= n; k++) { const u = k / n; r = grow(r || [this.s.w, this.s.h, -1, -1], this.disc(lerp(a.x, x, u), lerp(a.y, y, u), lerp(a.d, d, u) / 2)); }
      this.last = { x, y, d };
    }
    return r && !emptyRect(r) ? r : null;
  }
  disc(cx, cy, rad) {
    const s = this.s, w = s.w, K = s.kept, fill = s.fill, x0 = Math.max(0, Math.floor(cx - rad)), x1 = Math.min(w - 1, Math.ceil(cx + rad)), y0 = Math.max(0, Math.floor(cy - rad)), y1 = Math.min(s.h - 1, Math.ceil(cy + rad));
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const i = y * w + x;
      if (this.seen[i] || (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 > rad * rad) continue;
      this.seen[i] = 1;
      const k = K ? K[i] : PAPER_TONE;
      s.page[i] = s.base[i] = k;
      if (fill && k >= PAPER_TONE - .5) fill[i] = 0;
    }
    return [x0, y0, x1, y1];
  }
}
// Keeps Paint: a brush's kept reservoir {n, buf} as one of half-side m holds it, each cell the nearest at the same place
// across the dab (the app's HeldPaint.Reservoir.sized).
function heldSized(h, m) {
  if (h.n === m) return h.buf;
  const side = 2 * m + 1, from = 2 * h.n + 1, out = new Float32Array(side * side), at = k => clamp(Math.round((k - m) * h.n / Math.max(1, m)) + h.n, 0, from - 1);
  for (let j = 0; j < side; j++) for (let i = 0; i < side; i++) out[j * side + i] = h.buf[at(j) * from + at(i)];
  return out;
}
// A whole stroke, fed as a pen would feed it (three samples an event), then lifted. Returns the touched rectangle.
function layStroke(surf, B, pts, size, shade, host) {
  const st = new Stroke(surf, B, size, shade, host);
  for (let i = 0; i < pts.length; i += 3) st.add(pts.slice(i, i + 3).map(p => [p.x, p.y, p.p, p.t, p.tx, p.ty]));
  const r = st.end();
  return { st, r };
}

;
// ===================================================================================================================
// Float (suminagashi): a Drying choice. The stroke's paint floats on a water surface instead of soaking in.
//
// Floating paint is a stack of rings: closed outlines in page pixels, each one flat grey (clear water is grey 0), oldest
// first. Everything that moves paint on the water moves the rings' points, never pixels, so edges stay crisp however
// often the bath is stirred. Painting the rings oldest to newest is exact, because a new drop sits in the hole it made.
//   drop (Jaffer and Lu):  P' = C + (P - C) * sqrt(1 + r^2 / |P - C|^2)       area kept exactly
//   drag (marble-and-bristle's stylus): a stream function psi = (U x r) exp(-|r|^2 / 2 s^2), divergence-free, with slip
//   drift (page tilt): a shear along the downhill direction, faster mid-bath; divergence-free
// The film a stroke lays (its own line and body, as the stampers drew them) is traced at 0.5 coverage into rings at lift.
// The bath is shown, and printed, by a scanline sweep that finds the topmost ring along each row (no overdraw).
// ===================================================================================================================
const F_MAXSEG = 1.6, F_MINSEG = .45, F_VCAP = 240000;
const floatMs = d => wetMsOf(d);                                  // floating time: Drying's span, 0.75-20 s
// How big a tap's drop is: grows with the tip and with Spread; capped so one tap can't flood the page.
const dropRadius = (w, spread, p) => clamp((8 + 3 * w) * (.4 + 1.6 * spread), 4, 240) * Math.sqrt(.4 + .6 * clamp(p));
// How far the pen drags floating paint (MonoPaint's brush-pen pull: strength x the pressure curve), and the slip that
// turns it into a bounded carry (about 1.6 sigma at pull 0, 38 at pull 1).
const floatPull = (pick, p) => pick * (.15 + .85 * clamp(p));
const floatSlip = pull => .45 + .5 * pull;

function fRing(xs, ys, dark) {
  const c = { x: Float64Array.from(xs), y: Float64Array.from(ys), n: xs.length };
  const g = { cs: [c], dark, b: [0, 0, 0, 0] };
  fBox(g); return g;
}
function fCircle(cx, cy, R, dark) {
  const n = Math.max(24, Math.ceil(2 * Math.PI * R / 1.2)), xs = [], ys = [];
  for (let i = 0; i < n; i++) { const a = i / n * 2 * Math.PI; xs.push(cx + R * Math.cos(a)); ys.push(cy + R * Math.sin(a)); }
  return fRing(xs, ys, dark);
}
function fBox(g) {
  let l = Infinity, t = Infinity, r = -Infinity, b = -Infinity;
  for (const c of g.cs) for (let i = 0; i < c.n; i++) { const x = c.x[i], y = c.y[i]; if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y; }
  g.b[0] = l; g.b[1] = t; g.b[2] = r; g.b[3] = b;
}

class Bath {
  constructor(surf) {
    this.s = surf; this.rings = []; this.dry = Float32Array.from(surf.page);
    // Erasable: the least erasable of the brushes floating here, and the kept tones as the bath began (once one isn't all).
    this.e = 1; this.keptDry = surf.kept ? Float32Array.from(surf.kept) : null;
    this.strokes = []; this.ops = null;            // the log, one list of ops per stroke: replaying it gives the same bath
    this.shown = null; this.verts = 0; this.maxSeg = F_MAXSEG; this.wet = true;
    this.batching = false; this.dirty = new Set(); this.dirtyAll = false;
    this.lastTouch = 0; this.dryMs = floatMs(.6); this.age = 0;
    this.cost = { drop: 0, nDrop: 0, push: 0, nPush: 0, rake: 0, rakePx: 0, refine: 0, render: 0, nRender: 0, renderPx: 0, quick: 0, nQuick: 0, quickPx: 0,
      film: 0, drift: 0, nDrift: 0, print: 0 };
  }
  // ---------- ops: each is logged on the stroke that made it ----------
  begin() { this.ops = []; this.strokes.push(this.ops); }
  apply(op, log = true) {
    const t0 = now(), C = this.cost;
    if (op.k === "drop" || op.k === "push") {
      this.opDrop(op.x, op.y, op.r2);
      if (op.k === "drop") { this.rings.push(fCircle(op.x, op.y, Math.sqrt(op.r2), op.dark)); C.drop += now() - t0; C.nDrop++; }
      else { C.push += now() - t0; C.nPush++; }
    } else if (op.k === "pushes") {
      this.opPushes(op.list, op.near);
      C.push += now() - t0; C.nPush += op.list.length / 3;
    } else if (op.k === "rake") {
      this.opRake(op);
      C.rake += now() - t0; C.rakePx += Math.hypot(op.bx - op.ax, op.by - op.ay);
    } else if (op.k === "film") {
      for (const f of op.films) { const g = { cs: f.cs.map(c => ({ x: Float64Array.from(c.x), y: Float64Array.from(c.y), n: c.x.length })), dark: f.dark, b: [0, 0, 0, 0] }; fBox(g); this.rings.push(g); }
    } else if (op.k === "drift") {
      this.opDrift(op); C.drift += now() - t0; C.nDrift++;
    }
    // Outlines are refined once a batch (a pen event's ops), and after a drag only the rings it reached.
    if (op.k === "rake" || op.k === "pushes") { for (const g of this._near || []) this.dirty.add(g); } else this.dirtyAll = true;
    if (!this.batching) this.flush();
    if (log && this.ops) this.ops.push(op);
  }
  opDrop(cx, cy, R2) {
    for (const g of this.rings) {
      for (const c of g.cs) {
        const X = c.x, Y = c.y;
        for (let i = 0; i < c.n; i++) {
          let dx = X[i] - cx, dy = Y[i] - cy, d2 = dx * dx + dy * dy;
          if (d2 < 1e-12) { dx = 1e-6; dy = 0; d2 = 1e-12; }
          const s = Math.sqrt(1 + R2 / d2);
          X[i] = cx + dx * s; Y[i] = cy + dy * s;
        }
      }
    }
  }
  // A pen event's band pushes (x, y, r2 triples), in order. Points near them take each in turn; points farther than
  // `near` from their box take one drop of the summed area at their centre (the far field differs by under ~0.1 px).
  opPushes(list, near) {
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity, A = 0, cx = 0, cy = 0;
    for (let k = 0; k < list.length; k += 3) { const x = list[k], y = list[k + 1], a = list[k + 2]; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); A += a; cx += x * a; cy += y * a; }
    if (!(A > 0)) return;
    cx /= A; cy /= A; x0 -= near; y0 -= near; x1 += near; y1 += near;
    this._near = [];
    for (const g of this.rings) for (const c of g.cs) {
      const X = c.x, Y = c.y, inBox = !(g.b[2] < x0 || g.b[0] > x1 || g.b[3] < y0 || g.b[1] > y1);
      if (inBox && c === g.cs[0]) this._near.push(g);
      for (let i = 0; i < c.n; i++) {
        let px = X[i], py = Y[i];
        if (inBox && px >= x0 && px <= x1 && py >= y0 && py <= y1) {
          for (let k = 0; k < list.length; k += 3) {
            let dx = px - list[k], dy = py - list[k + 1], d2 = dx * dx + dy * dy;
            if (d2 < 1e-12) { dx = 1e-6; dy = 0; d2 = 1e-12; }
            const f = Math.sqrt(1 + list[k + 2] / d2); px = list[k] + dx * f; py = list[k + 1] + dy * f;
          }
        } else {
          let dx = px - cx, dy = py - cy, d2 = dx * dx + dy * dy;
          if (d2 < 1e-12) { dx = 1e-6; dy = 0; d2 = 1e-12; }
          const f = Math.sqrt(1 + A / d2); px = cx + dx * f; py = cy + dy * f;
        }
        X[i] = px; Y[i] = py;
      }
    }
  }
  // The pen moved a -> b. Tines sit across the motion at offsets op.tines (one tine for a round tip, a broad one for a
  // hake, two to five for a split nib or a multi-line). Substeps keep each step under 0.3 sigma. Only points inside the
  // segment's reach are gathered, once per call, so the cost follows what is near the pen, not the bath.
  opRake(op) {
    const { ax, ay, bx, by, sig, tines, slip } = op, len = Math.hypot(bx - ax, by - ay);
    if (len < 1e-9) return;
    const reach = 4 * sig + Math.max(0, ...tines.map(Math.abs));
    const lo0 = Math.min(ax, bx) - reach, lo1 = Math.min(ay, by) - reach, hi0 = Math.max(ax, bx) + reach, hi1 = Math.max(ay, by) + reach;
    const near = []; this._near = [];
    for (const g of this.rings) {
      const b = g.b; if (b[2] < lo0 || b[0] > hi0 || b[3] < lo1 || b[1] > hi1) continue;
      this._near.push(g);
      for (const c of g.cs) {
        const idx = []; for (let i = 0; i < c.n; i++) { const x = c.x[i], y = c.y[i]; if (x >= lo0 && x <= hi0 && y >= lo1 && y <= hi1) idx.push(i); }
        if (idx.length) near.push([c, idx]);
      }
    }
    if (!near.length) return;
    const n = Math.min(600, Math.max(1, Math.ceil(len / (sig * .3)))), Ux = (bx - ax) / n, Uy = (by - ay) / n;
    const is2 = 1 / (sig * sig), cut = 16 * sig * sig, ml = Math.hypot(Ux, Uy), nx = -Uy / ml, ny = Ux / ml, T = tines.length;
    for (let s = 0; s < n; s++) {
      const cx = ax + Ux * (s + .5), cy = ay + Uy * (s + .5);
      for (const [c, idx] of near) {
        const X = c.x, Y = c.y;
        for (const i of idx) {
          const px = X[i], py = Y[i];
          let vx = 0, vy = 0;
          for (let j = 0; j < T; j++) {
            const qx = px - cx - nx * tines[j], qy = py - cy - ny * tines[j], r2 = qx * qx + qy * qy;
            if (r2 > cut) continue;
            const G = Math.exp(-.5 * r2 * is2), cr = Ux * qy - Uy * qx;
            vx += G * (Ux - cr * qy * is2); vy += G * (Uy + cr * qx * is2);
          }
          X[i] = px + vx * slip; Y[i] = py + vy * slip;
        }
      }
    }
  }
  // Page tilt: everything floats downhill, the middle of the bath (across the slope) faster than its sides, so rings
  // shear into the long chevrons of a tray tipped while the ink floats. u = g * A * f(n), n across the slope: no divergence.
  opDrift(op) {
    const { gx, gy, A, cx, cy, hw } = op, px = -gy, py = gx;
    for (const g of this.rings) for (const c of g.cs) {
      const X = c.x, Y = c.y;
      for (let i = 0; i < c.n; i++) {
        const u = clamp(((X[i] - cx) * px + (Y[i] - cy) * py) / hw, -1, 1), f = .35 + .65 * Math.cos(u * Math.PI / 2) ** 2;
        X[i] += gx * A * f; Y[i] += gy * A * f;
      }
    }
  }
  flush() {
    const t1 = now();
    if (this.dirtyAll) this.refine(this.rings); else if (this.dirty.size) this.refine([...this.dirty]);
    this.dirtyAll = false; this.dirty.clear();
    this.cost.refine += now() - t1;
  }
  // Keep outlines crisp: split long edges, merge points closer than F_MINSEG; a cap so a long stir can't run away.
  refine(list) {
    for (const g of list) {
      let pts = 0;
      for (const c of g.cs) {
        const X = c.x, Y = c.y, n = c.n, ox = [], oy = [];
        let lx = X[0], ly = Y[0]; ox.push(lx); oy.push(ly);
        for (let i = 1; i <= n; i++) {
          const j = i % n, x = X[j], y = Y[j], d = Math.hypot(x - lx, y - ly);
          if (d < F_MINSEG && i < n) continue;
          if (d > this.maxSeg) { const m = Math.ceil(d / this.maxSeg); for (let k = 1; k < m; k++) { ox.push(lx + (x - lx) * k / m); oy.push(ly + (y - ly) * k / m); } }
          if (i < n) { ox.push(x); oy.push(y); }
          lx = x; ly = y;
        }
        c.x = Float64Array.from(ox); c.y = Float64Array.from(oy); c.n = ox.length;
        pts += c.n;
      }
      this.verts += pts - (g.pts || 0); g.pts = pts;
      fBox(g);
    }
    const total = this.verts;
    if (total > F_VCAP && this.maxSeg < 8) this.maxSeg *= 1.5;
  }
  // ---------- show: the rings over the page as it was when the bath began ----------
  box() {
    const s = this.s; let r = [Infinity, Infinity, -Infinity, -Infinity];
    for (const g of this.rings) { const b = g.b; if (b[0] < r[0]) r[0] = b[0]; if (b[1] < r[1]) r[1] = b[1]; if (b[2] > r[2]) r[2] = b[2]; if (b[3] > r[3]) r[3] = b[3]; }
    if (!(r[0] <= r[2])) return null;
    const out = [Math.max(0, Math.floor(r[0]) - 1), Math.max(0, Math.floor(r[1]) - 1), Math.min(s.w - 1, Math.ceil(r[2]) + 1), Math.min(s.h - 1, Math.ceil(r[3]) + 1)];
    return emptyRect(out) ? null : out;
  }
  // quick: one sample a row (the pen is down); full: four (at lift, and each tick). tooth: the paper takes a touch less
  // ink in its valleys (at print). Returns the rectangle written.
  render(quality, tooth, extra) {
    const t0 = now(), s = this.s, w = s.w, page = s.page, dry = this.dry, fine = s.fine, KD = this.e < 1 ? this.keptDry : null, KP = s.kept, ke = 1 - this.e;
    let R = this.box();
    if (this.shown) R = R ? union(R, this.shown) : this.shown;
    if (extra && !emptyRect(extra)) R = R ? union(R, extra) : extra.slice();
    if (!R) return null;
    this.shown = this.box();
    const SS = quality === "quick" ? 1 : 4, x0 = R[0], y0 = R[1], rw = R[2] - R[0] + 1, rh = R[3] - R[1] + 1;
    // Edges, bucketed by the row they start in.
    let ne = 0; for (const g of this.rings) for (const c of g.cs) ne += c.n;
    const ex = new Float64Array(ne), ey0 = new Float64Array(ne), ey1 = new Float64Array(ne), sl = new Float64Array(ne), er = new Uint16Array(ne);
    const start = new Int32Array(rh + 1), nextE = new Int32Array(ne).fill(-1); start.fill(-1);
    let k = 0;
    this.rings.forEach((g, ri) => {
      const b = g.b; if (b[3] < y0 || b[1] > R[3] + 1) return;
      for (const c of g.cs) for (let i = 0, j = c.n - 1; i < c.n; j = i++) {
        let xa = c.x[j], ya = c.y[j], xb = c.x[i], yb = c.y[i];
        if (ya === yb) continue;
        if (ya > yb) { let t = xa; xa = xb; xb = t; t = ya; ya = yb; yb = t; }
        if (yb <= y0 || ya >= R[3] + 1) continue;
        ex[k] = xa; ey0[k] = ya; ey1[k] = yb; sl[k] = (xb - xa) / (yb - ya); er[k] = ri;
        const row = clamp(Math.floor(ya) - y0, 0, rh - 1);
        nextE[k] = start[row]; start[row] = k; k++;
      }
    });
    const nr = this.rings.length, par = new Uint8Array(nr), dark = new Float64Array(nr);
    this.rings.forEach((g, i) => { dark[i] = g.dark; });
    let active = [], keys = new Float64Array(64);
    const acc = new Float64Array(rw + 1);
    for (let y = 0; y < rh; y++) {
      for (let e = start[y]; e >= 0; e = nextE[e]) active.push(e);
      const top = y0 + y + 1;
      acc.fill(0);
      for (let sub = 0; sub < SS; sub++) {
        const ys = y0 + y + (sub + .5) / SS;
        let nk = 0;
        for (const e of active) {
          if (ey0[e] > ys || ey1[e] <= ys) continue;
          const x = ex[e] + (ys - ey0[e]) * sl[e];
          if (nk >= keys.length) { const kk = new Float64Array(keys.length * 2); kk.set(keys); keys = kk; }
          keys[nk++] = Math.round((clamp(x, x0 - 2, x0 + rw + 2) - x0 + 4) * 256) * 65536 + er[e];
        }
        if (!nk) continue;
        const ks = keys.subarray(0, nk).sort();
        let topR = -1, prevX = 0;
        for (let q = 0; q < nk; q++) {
          const key = ks[q], ri = key % 65536, x = Math.floor(key / 65536) / 256 - 4;
          if (topR >= 0 && dark[topR] > 0) {
            const a = Math.max(0, prevX), b = Math.min(rw, x), v = dark[topR] / SS;
            if (b > a) {
              let ia = Math.floor(a), ib = Math.floor(b);
              if (ia === ib) acc[ia] += (b - a) * v;
              else { acc[ia] += (ia + 1 - a) * v; for (let p = ia + 1; p < ib; p++) acc[p] += v; if (ib < rw) acc[ib] += (b - ib) * v; }
            }
          }
          par[ri] ^= 1;
          if (par[ri]) { if (ri > topR) topR = ri; }
          else if (ri === topR) { topR = -1; for (let r2 = ri - 1; r2 >= 0; r2--) if (par[r2]) { topR = r2; break; } }
          prevX = x;
        }
        par.fill(0);
      }
      for (let x = 0, i = (y0 + y) * w + x0; x < rw; x++, i++) {
        const a = acc[x];
        page[i] = a > 0 ? dry[i] * (1 - Math.min(1, a) * (tooth ? .9 + .1 * fine[i] : 1)) : dry[i];
        if (KD) KP[i] = a > 0 ? KD[i] * (1 - ke * Math.min(1, a) * (tooth ? .9 + .1 * fine[i] : 1)) : KD[i];
      }
      active = active.filter(e => ey1[e] > top);
    }
    const ms = now() - t0, C = this.cost;
    if (SS === 1) { C.quick += ms; C.nQuick++; C.quickPx += rw * rh; } else { C.render += ms; C.nRender++; C.renderPx += rw * rh; }
    return R;
  }
  // ---------- life ----------
  // A tick (ten a second while it floats and the pen is up): drift if the page is tilted, print when its time is up.
  tick(t, tilt, drift) {
    if (!this.wet) return null;
    if (t - this.lastTouch >= this.dryMs) return this.print();
    const g = Math.hypot(tilt.x, tilt.y);
    if (g <= .08 || !(drift > 0) || !this.rings.length) return null;
    const b = this.box(); if (!b) return null;
    const gx = tilt.x / g, gy = tilt.y / g, cx = (b[0] + b[2]) / 2, cy = (b[1] + b[3]) / 2;
    const hw = Math.max(20, Math.abs((b[2] - b[0]) * gy) / 2 + Math.abs((b[3] - b[1]) * gx) / 2);
    // Up to 40 px a second on an upright page at Drift 1, from nothing at the dead zone.
    const A = 40 / TPS * drift * clamp((g - .08) / .92);
    this.apply({ k: "drift", gx, gy, A, cx, cy, hw });
    return this.render("full", false);
  }
  // Prints onto the paper and stops: the page keeps the rings' greys, the paper's tooth taking a little less in its valleys.
  print() {
    const t0 = now(), r = this.render("full", true);
    this.wet = false; this.rings = []; this.cost.print = now() - t0;
    this.s.base.set(this.s.page);
    return r;
  }
  // Undo while it floats: replay every stroke but the last on a fresh bath (the log is the bath).
  undoLast() {
    if (!this.strokes.length) return null;
    this.strokes.pop();
    const keep = this.strokes; this.rings = []; this.verts = 0; this.maxSeg = F_MAXSEG; this.strokes = []; this.ops = null;
    for (const ops of keep) { this.begin(); for (const op of ops) this.apply(op); }
    return this.render("full", false);
  }
}

// ---------- the film a stroke lays: its line and body traced at half coverage (marching squares) ----------
function traceFilm(field, rw, rh, ox, oy, th = .5) {
  // Pad by one cell of nothing so every outline closes. Edge points: horizontal edge (x, y) -> (x, y)-(x+1, y);
  // vertical edge (x, y) -> (x, y)-(x, y+1). Each point joins exactly two segments, so loops are walked by adjacency.
  const W = rw + 2, H = rh + 2, v = (x, y) => (x <= 0 || y <= 0 || x > rw || y > rh) ? 0 : field[(y - 1) * rw + x - 1];
  const nbr = new Map(), pos = new Map();
  const hId = (x, y) => (y * W + x) * 2, vId = (x, y) => (y * W + x) * 2 + 1;
  const pt = (id) => {
    if (pos.has(id)) return id;
    const cell = id >> 1, x = cell % W, y = (cell / W) | 0;
    if (id & 1) { const a = v(x, y), b = v(x, y + 1), t = (th - a) / (b - a); pos.set(id, [x, y + t]); }
    else { const a = v(x, y), b = v(x + 1, y), t = (th - a) / (b - a); pos.set(id, [x + t, y]); }
    return id;
  };
  const link = (a, b) => { pt(a); pt(b); (nbr.get(a) || nbr.set(a, []).get(a)).push(b); (nbr.get(b) || nbr.set(b, []).get(b)).push(a); };
  for (let y = 0; y < H - 1; y++) for (let x = 0; x < W - 1; x++) {
    const a = v(x, y) >= th, b = v(x + 1, y) >= th, c = v(x + 1, y + 1) >= th, d = v(x, y + 1) >= th;
    const idx = (a ? 8 : 0) | (b ? 4 : 0) | (c ? 2 : 0) | (d ? 1 : 0);
    if (idx === 0 || idx === 15) continue;
    const T = hId(x, y), B = hId(x, y + 1), L = vId(x, y), R = vId(x + 1, y);
    switch (idx) {
      case 1: case 14: link(L, B); break;
      case 2: case 13: link(B, R); break;
      case 3: case 12: link(L, R); break;
      case 4: case 11: link(T, R); break;
      case 6: case 9: link(T, B); break;
      case 7: case 8: link(L, T); break;
      case 5: case 10: {
        const mid = (v(x, y) + v(x + 1, y) + v(x + 1, y + 1) + v(x, y + 1)) / 4 >= th;
        if ((idx === 5) === mid) { link(L, T); link(B, R); } else { link(L, B); link(T, R); }
        break;
      }
    }
  }
  const seen = new Set(), loops = [];
  for (const id0 of nbr.keys()) {
    if (seen.has(id0)) continue;
    const xs = [], ys = [];
    let prev = -1, cur = id0;
    while (!seen.has(cur)) {
      seen.add(cur); const p = pos.get(cur); xs.push(p[0] - 1 + ox + .5); ys.push(p[1] - 1 + oy + .5);
      const nb = nbr.get(cur), nxt = nb[0] !== prev ? nb[0] : nb[1];
      prev = cur; cur = nxt;
      if (cur === undefined) break;
    }
    if (xs.length >= 3) loops.push({ x: xs, y: ys });
  }
  return loops;
}

;
// ===================================================================================================================
// The part model. A brush is six parts, each holding one choice and that choice's dials:
//   Tip (the contact's shape) → Medium (what it lays) → Carry (what it picks up) → Paper → Life (when it is final) → Hand
// Every part always holds something; "Off" and "None" are real choices, so any combination draws.
// ===================================================================================================================
const PARTS = ["tip", "medium", "carry", "paper", "life", "hand"];
// The part descriptions, one set for the lab and Brush Studio (its card for Erasable, the medium's last dial, says ERASE_ROLE's).
const ROLE = {
  tip: { label: "Tip", blurb: "The shape that touches the paper." },
  medium: { label: "Medium", blurb: "What the mark is made of." },
  carry: { label: "Smudge", blurb: "How the brush moves paint already there." },
  paper: { label: "Paper", blurb: "The surface and its texture." },
  life: { label: "Drying", blurb: "How long the mark stays wet." },
  hand: { label: "Control", blurb: "How pressure, tilt and speed change the mark." }
};
const ERASE_ROLE = { label: "Erasable", blurb: "How much the eraser can take." };
const GRADES = ["6H", "5H", "4H", "3H", "2H", "H", "F", "HB", "B", "2B", "3B", "4B", "5B", "6B", "7B", "8B"];
const gradeOf = g => GRADES[Math.round(g < .4 ? g / .4 * 7 : 7 + (g - .4) / .6 * 8)];
const pct = v => Math.round(v * 100) + "";
// Every dial: range, default, and how it's shown. A choice dial holds an option index.
const DIALS = {
  tip: {
    weight: { label: "Size", min: .5, max: 1.5, def: 1, fmt: v => "×" + v.toFixed(2) },
    aspect: { label: "Nib Width", min: .14, max: .55, def: .4 },
    angle: { label: "Nib Angle", min: 20, max: 70, def: 40, fmt: v => Math.round(v) + "°" },
    taperIn: { label: "Taper In", min: 0, max: 1, def: 0 },
    taperOut: { label: "Taper Out", min: 0, max: 1, def: 0 },
    dry: { label: "Dry Brush", min: 0, max: 1, def: 0 },
    wobble: { label: "Wobble", min: 0, max: 1, def: 0 },
    soft: { label: "Soft Edge", min: 0, max: 1, def: .3 },
    headTurn: { label: "Head Follows", choices: ["Stroke", "Page"], def: 0 },
    gap: { label: "Tine Gap", min: 0, max: 1, def: .55 },
    count: { label: "Lines", min: 2, max: 5, def: 3, int: true, fmt: v => String(Math.round(v)) },
    reach: { label: "Spacing", min: 0, max: 1, def: .6 },
    hairs: { label: "Hairs", min: 6, max: 32, def: 26, int: true, fmt: v => String(Math.round(v)) },
    cohesion: { label: "Clump", min: 0, max: 1, def: .55 },
    splay: { label: "Splay", min: 0, max: 1, def: .45 },
    dryness: { label: "Dry Brush", min: 0, max: 1, def: .15 }
  },
  medium: {
    wet: { label: "Wet", min: 0, max: 1, def: 0 },
    line: { label: "Pen Line", min: 0, max: 1, def: 1 },
    pool: { label: "Pooling", min: 0, max: 1, def: 0 },
    splatter: { label: "Spatter", min: 0, max: 1, def: 0 },
    bleed: { label: "Bleed", min: 0, max: 1, def: 0 },
    bloom: { label: "Bloom", min: 0, max: 1, def: 0 },
    backrun: { label: "Backruns", min: 0, max: 1, def: 0 },
    lines: { label: "Line Style", choices: ["Centre", "Outline", "Squiggle"], def: 0 },
    offset: { label: "Line Shift", min: 0, max: 1, def: 0 },
    chaos: { label: "Breaks", min: 0, max: 1, def: 0 },
    cover: { label: "Opacity", min: 0, max: 1, def: 1 },
    impasto: { label: "Thick Paint", min: 0, max: 1, def: 0 },
    spread: { label: "Messy Edge", min: 0, max: 1, def: .3 },
    hold: { label: "Density", min: 0, max: 1, def: .4 },
    grade: { label: "Grade", min: 0, max: 1, def: .4, fmt: gradeOf },
    // Not the prototype's: the app's charcoal dust (its gravity), kept so codes line up with the app's; the lab draws none.
    dust: { label: "Dust When Tilted", min: 0, max: 1, def: 0, hide: true },
    // How much of a mark the eraser takes (spec 2026-10-16-erasable-design.md). A code without it reads 1, today's: all.
    erase: { label: "Erasable", min: 0, max: 1, def: 1 }
  },
  carry: {
    pick: { label: "Pick Up Paint", min: 0, max: 1, def: .4 },
    lay: { label: "Lay Down Paint", min: 0, max: 1, def: .5 },
    load: { label: "Own Paint", min: 0, max: 1, def: 1 },
    // Smear's Soften (the Stump spec, 2026-10-18-stump-design.md): how much of the smear is MonoPaint's blending stump.
    soften: { label: "Soften", min: 0, max: 1, def: 0 },
    // Drag's and Smear's Keeps Paint (the wet smudge spec, 2026-10-19-wet-smudge-design.md): how much of what the brush
    // picked up it keeps for the next stroke, a dirty brush. Off: every stroke starts clean, as before. Never rolled.
    keep: { label: "Keeps Paint", min: 0, max: 1, def: 0 },
    // Drag's and Smear's Paint Load and Starting Load (the oil paint spec, 2026-10-19-oil-paint-design.md; MonoPaint's oil
    // paint mode): how much paint a full brush holds (off: it never runs out, as before), and the share of it every
    // stroke starts with. Written only when on; a part's roll leaves them off (Oils rolls Paint Load from the seed).
    pload: { label: "Paint Load", min: 0, max: 1, def: 0 },
    pmin: { label: "Starting Load", min: 0, max: 1, def: .25 }
  },
  paper: { tooth: { label: "Texture", min: 0, max: 1, def: .5 } },
  life: {
    window: { label: "Wet Edge", min: 1, max: 10, def: 6, fmt: v => v.toFixed(1) + " mm" },
    drying: { label: "Drying Time", min: 0, max: 1, def: .35, fmt: v => (wetMsOf(v) / 1000).toFixed(1) + " s" },
    settle: { label: "Flow While Wet", min: 0, max: 1, def: .35 },
    takes: { label: "Takes Ink", choices: ["Off", "Drift", "Vein"], def: 0 },
    plume: { label: "Plume", min: 0, max: 1, def: .55 },
    spread: { label: "Ring Size", min: 0, max: 1, def: .5 },
    drift: { label: "Drift Downhill", min: 0, max: 1, def: .5 }
  },
  hand: {
    ps: { label: "Pressure Size", min: 0, max: 1, def: 0 }, pd: { label: "Pressure Dark", min: 0, max: 1, def: 0 }, pw: { label: "Pressure Nib", min: 0, max: 1, def: 0 },
    ts: { label: "Tilt Size", min: 0, max: 1, def: 0 }, td: { label: "Tilt Dark", min: 0, max: 1, def: 0 }, tw: { label: "Tilt Nib", min: 0, max: 1, def: 0 },
    ss: { label: "Speed Size", min: 0, max: 1, def: 0 }, sd: { label: "Speed Dark", min: 0, max: 1, def: 0 }, sw: { label: "Speed Nib", min: 0, max: 1, def: 0 },
    dir: { label: "Fast Strokes", choices: ["Thinner", "Thicker"], def: 0 },
    gain: { label: "Response", min: 0, max: 1.5, def: 1, fmt: v => "×" + v.toFixed(2) }
  }
};
const HAND_CELLS = ["ps", "pd", "pw", "ts", "td", "tw", "ss", "sd", "sw"];
const HAND_DIALS = [...HAND_CELLS, "gain", "dir"];
// Never reorder a part's choices or a choice's dials: share codes are written in this order. Append only.
const CHOICES = {
  tip: [
    { k: "round", label: "Round", dials: ["weight", "taperIn", "taperOut", "dry", "wobble", "soft"], macro: "weight", words: ["fine", "broad"], blurb: "A round point or a round brush." },
    { k: "chisel", label: "Chisel", dials: ["aspect", "angle", "weight", "taperIn", "taperOut", "dry", "wobble", "soft"], macro: "aspect", words: ["fine", "broad"], range: { aspect: [.3, .55] }, def: { aspect: .42 }, blurb: "A broad edge held at an angle: thick and thin." },
    { k: "flat", label: "Flat Nib", dials: ["aspect", "angle", "weight", "taperIn", "taperOut", "dry", "wobble", "soft"], macro: "aspect", words: ["fine", "broad"], range: { aspect: [.14, .28] }, def: { aspect: .2 }, blurb: "A thin italic edge: hairlines and full strokes." },
    { k: "hake", label: "Hake", dials: ["weight", "headTurn", "angle", "dry", "soft"], macro: "weight", words: ["fine", "broad"], def: { angle: 75, dry: .35, soft: .2 }, blurb: "A wide flat head swept as a ribbon; it pivots on tight turns." },
    { k: "split", label: "Split Nib", dials: ["gap", "aspect", "angle", "weight", "taperIn", "taperOut", "dry"], macro: "gap", words: ["closed", "open"], range: { aspect: [.3, .55] }, def: { aspect: .42 }, blurb: "Two tines that part under pressure." },
    { k: "multi", label: "Rake", dials: ["count", "reach", "weight", "taperIn", "taperOut", "dry", "wobble"], macro: "reach", words: ["close", "wide"], blurb: "Two to five lines that breathe apart and together with pressure." },
    { k: "bristles", label: "Bristle", dials: ["hairs", "splay", "cohesion", "dryness", "weight"], macro: "splay", words: ["tight", "splayed"], blurb: "Hairs that follow each other's trails, split and rejoin." }
  ],
  medium: [
    { k: "ink", label: "Ink", dials: ["wet", "line", "pool", "splatter", "bleed", "bloom", "lines", "offset", "chaos", "erase"], macro: "wet", words: ["dry", "wet"], blurb: "A line; Wet lets it loosen into a wash around it." },
    { k: "wash", label: "Wash", dials: ["wet", "pool", "bleed", "bloom", "backrun", "line", "erase"], macro: "wet", words: ["flat", "wet"], def: { wet: .85, line: 0, pool: .3, bloom: .2 }, labels: { pool: "Dark Edge" }, blurb: "Transparent paint: only darkens what's under it." },
    { k: "cover", label: "Opaque", dials: ["cover", "impasto", "erase"], macro: "cover", words: ["thin", "opaque"], blurb: "Opaque body colour: light covers dark. Thick paint is lit from the upper left." },
    { k: "charcoal", label: "Charcoal", dials: ["spread", "hold", "dust", "erase"], macro: "spread", words: ["crisp", "messy"], labels: { hold: "Density" }, blurb: "Dry dust: catches the high points first; fuzz and specks from the stick, not the paper." },
    { k: "graphite", label: "Graphite", dials: ["grade", "spread", "erase"], macro: "grade", words: ["hard", "soft"], def: { spread: 0 }, blurb: "Pencil, 6H to 8B: fills the texture with each pass." }
  ],
  carry: [
    { k: "off", label: "Off", dials: [], blurb: "Lays its own paint only." },
    { k: "lift", label: "Lift", dials: ["pick", "lay"], macro: "pick", words: ["gentle", "strong"], def: { pick: .2, lay: .3 }, blurb: "Takes paint off as it goes, like a damp brush or a tissue." },
    { k: "drag", label: "Drag", dials: ["pick", "lay", "load", "keep", "pload", "pmin"], macro: "pick", words: ["clean", "smeary"], def: { pick: .6, lay: .7, load: .85 }, blurb: "Drags the paint beneath along: wet into wet." },
    { k: "smudge", label: "Smear", dials: ["pick", "lay", "load", "soften", "keep", "pload", "pmin"], macro: "pick", words: ["clean", "smeary"], def: { pick: .7, lay: .55, load: .3 }, blurb: "Mostly smears what's there, like a finger or a stump." },
    // Clear Water (the wet smudge spec): Smudge's fifth choice, so older codes never hold it. Lays only water.
    { k: "water", label: "Clear Water", dials: ["pick", "lay"], macro: "pick", words: ["gentle", "strong"], def: { pick: .5, lay: .6 }, blurb: "Lays only water: lifts what it touches and lets it run through the wet." }
  ],
  paper: ["plain", "cold press", "laid", "felt", "crumpled", "canvas"].map(k => ({ k, label: { plain: "Smooth", "cold press": "Cold Press", laid: "Ribbed" }[k] || k[0].toUpperCase() + k.slice(1), dials: ["tooth"], macro: "tooth", words: ["smooth", "rough"], def: { tooth: k === "plain" ? 0 : .5 } })),
  life: [
    { k: "none", label: "Instant", dials: [], blurb: "Final the moment the nib passes. Fast." },
    { k: "wet", label: "Quick", dials: ["window"], macro: "window", words: ["short", "long"], blurb: "Only the last few mm behind the nib change while the pen is down; nothing after lift." },
    { k: "dry", label: "Slow", dials: ["drying", "settle", "takes", "plume"], macro: "drying", words: ["short", "long"], blurb: "Keeps changing for its drying time after lift." },
    { k: "float", label: "Float", dials: ["drying", "spread", "drift"], macro: "spread", words: ["small", "large"], def: { drying: .6 }, labels: { drying: "Floating Time" },
      blurb: "Suminagashi: the paint floats on water. A tap drops it in rings that push older paint outward; with Smudge it combs what floats. It prints onto the paper when its time is up." }
  ],
  hand: [
    { k: "steady", label: "Steady", cells: {} },
    { k: "wider", label: "Press Wide", cells: { ps: .8 } },
    { k: "darker", label: "Press Dark", cells: { pd: .8, ps: .15 } },
    { k: "both", label: "Press Both", cells: { ps: .7, pd: .6 } },
    { k: "brush", label: "Springy", cells: { ps: .9, pd: .3, pw: .45, ss: .4 } },
    { k: "side", label: "Tilt Shade", cells: { ps: .4, ts: .8, td: .5 } },
    { k: "nib", label: "Tilt Turn", cells: { ps: .4, tw: .9, pw: .4 } },
    { k: "quick", label: "Flick", cells: { ps: .3, ss: .7, sd: .5, sw: .4 } },
    { k: "custom", label: "Custom", cells: null }
  ]
};
CHOICES.hand.forEach(c => { c.dials = HAND_DIALS; c.macro = "gain"; c.words = ["steady", "responsive"]; });
const choiceOf = (part, k) => CHOICES[part].find(c => c.k === k) || CHOICES[part][0];
const choiceIndex = (part, k) => Math.max(0, CHOICES[part].findIndex(c => c.k === k));
const dialSpec = (part, key) => DIALS[part][key];
// A Title Case label in a sentence or a card's words (ModularWords.sentence): "Flat Nib" as "Flat nib".
const sentenceCase = t => t.charAt(0) + t.slice(1).toLowerCase();
const dialLabel = (part, k, key) => (choiceOf(part, k).labels || {})[key] || DIALS[part][key].label;
function rangeOf(part, k, key) { const c = choiceOf(part, k), s = DIALS[part][key]; return (c.range && c.range[key]) || [s.min, s.max]; }
function dialDefault(part, k, key) { const c = choiceOf(part, k); return c.def && c.def[key] !== undefined ? c.def[key] : DIALS[part][key].def; }
function fmtDial(part, key, v) {
  const s = DIALS[part][key];
  if (s.choices) return s.choices[v | 0];
  if (s.fmt) return s.fmt(v);
  if (s.min === 0 && v < .005) return "off";
  return v.toFixed(2);
}
// A part holding choice k, every dial at its default.
function freshPart(part, k) {
  const c = choiceOf(part, k), d = {};
  for (const key of c.dials) d[key] = dialDefault(part, k, key);
  if (part === "hand" && c.cells) for (const key of HAND_CELLS) d[key] = c.cells[key] || 0;
  return { k: c.k, d };
}
// Dials added after the prototype's, last in their choices' lists (the app's ModularCode.ADDED): written only when one is
// off its default, so a brush that doesn't use them keeps its code; never rolled, so every roll draws as before.
const ADDED = new Set(["medium.dust", "medium.erase", "carry.soften", "carry.keep", "carry.pload", "carry.pmin"]);
// Erasable (the Erasable spec): every medium starts at 1, erases clean (the owner, 2026-10-22: "per-tool things should be
// erasable unless the user specifies"; until then the real medium's: ink permanent, a wash .4, gouache .3, Oils' paint off).
// A code that doesn't say reads 1. Resist's dial is wax or fluid (newmods.js).
const REAL_ERASE = { ink: 1, wash: 1, cover: 1, charcoal: 1, graphite: 1 };
const realErase = (style, k) => REAL_ERASE[k] ?? 1;
// No roll changes Erasable (the app's ModularStyle.keepErasable; the owner: "the randomizer leaves that var alone unless the
// user moves it"): B's medium takes old's value, or 1 with no old brush or Resist on either side. Draws nothing.
function keepErase(B, old) {
  const M = B.medium, O = old && old.medium;
  if (M.k === "resist" || !choiceOf("medium", M.k).dials.includes("erase")) return;
  M.d.erase = O && O.k !== "resist" && O.d.erase !== undefined ? O.d.erase : 1;
}
// Swap a part's choice: the new choice's defaults, keeping dials that mean the same thing in both (a tip's tapers, say).
const KEEPS = { tip: ["weight", "taperIn", "taperOut", "dry", "wobble", "soft", "angle"], medium: ["pool", "bleed", "bloom", "splatter"], carry: ["pload", "pmin"], paper: ["tooth"], life: ["drying"], hand: ["gain", "dir"] };
function switchChoice(part, P, k) {
  const out = freshPart(part, k);
  if (P) for (const key of KEEPS[part]) if (key in out.d && key in P.d && !(part === "paper" && P.k === "plain")) out.d[key] = P.d[key];
  if (part === "tip" && P && (k === "chisel" || k === "flat" || k === "split")) { const [lo, hi] = rangeOf("tip", k, "aspect"); if ("aspect" in P.d && P.d.aspect >= lo && P.d.aspect <= hi) out.d.aspect = P.d.aspect; }
  if (part === "hand" && P && k === "custom") Object.assign(out.d, P.d);
  // A medium chosen keeps its Erasable (the app's ModularBrush.switchTo), 1 from or to Resist (wax or fluid).
  if (part === "medium") out.d.erase = P && P.k !== "resist" && k !== "resist" && P.d.erase !== undefined ? P.d.erase : REAL_ERASE[k];
  return out;
}
function normPart(part, P) {
  const c = choiceOf(part, P.k), d = {};
  P.k = c.k;
  for (const key of c.dials) {
    const s = DIALS[part][key], [lo, hi] = rangeOf(part, c.k, key);
    let v = P.d[key];
    if (v === undefined || !Number.isFinite(v)) v = dialDefault(part, c.k, key);
    v = s.choices ? clamp(Math.round(v), 0, s.choices.length - 1) : clamp(v, lo, hi);
    if (s.int) v = Math.round(v);
    d[key] = v;
  }
  P.d = d;
  return P;
}
function normBrush(B) { for (const p of PARTS) { B[p] ??= freshPart(p, CHOICES[p][0].k); normPart(p, B[p]); } B.seed = B.seed >>> 0; return B; }
const cloneBrush = B => JSON.parse(JSON.stringify(B));
const val = (B, part, key) => B[part].d[key] !== undefined ? B[part].d[key] : 0;
// The hand's cell in use: amount times the overall response, at most 1.
const handCell = (B, key) => Math.min(1, (B.hand.d[key] || 0) * (B.hand.d.gain ?? 1));

// ===================================================================================================================
// resolve: parts → what the engine draws. The medium sets the blend of a line (the pen engine) and a body of paint
// (the paint engine) as pen-paint-blend's partsA; the tip picks the stampers; the hand feeds every stamper.
// ===================================================================================================================
// How dark a body lays (the app's ModularSizes): a body glazes at GLAZE of the swatch's darkness (ink's wet body, opaque
// paint's, a wash's until 2026-10-19); a wash, since washes reach black, at WASH_BLACK of it where its body is full. A
// body of strength x (its density times washK) lays x up to KNEE, as every body always laid, then eases toward black.
const GLAZE = .34, WASH_BLACK = .97, KNEE = .55;
function laid(x) { return x <= KNEE ? x : 1 - (1 - KNEE) * Math.exp(-(x - KNEE) / (1 - KNEE)); }
function strength(y) { return y <= KNEE ? y : KNEE - (1 - KNEE) * Math.log((1 - Math.min(y, .999)) / (1 - KNEE)); }
// A wash's strength at swatch darkness d: its full body lays WASH_BLACK of d.
function washStrength(d) { return strength(WASH_BLACK * clamp(d)); }
function resolve(B, size, shade) {
  const T = B.tip, M = B.medium, C = B.carry, P = B.paper, L = B.life, tk = T.k, mk = M.k;
  const t = key => T.d[key] ?? 0, m = key => M.d[key] ?? 0, h = key => handCell(B, key);
  const lineMedium = mk === "ink" || mk === "charcoal" || mk === "graphite";
  let w = 0, line = 1, mode = "wet", wetMul = 1, cover = 0, impasto = 0, rule = "ink", lineDark = 1;
  if (mk === "ink") { w = m("wet"); line = m("line"); }
  else if (mk === "wash") { w = 1; line = m("line"); mode = m("wet") >= .5 ? "wet" : "flat"; wetMul = mode === "wet" ? lerp(.4, 1, (m("wet") - .5) * 2) : 1; }
  else if (mk === "cover") { w = 1; line = 0; mode = "flat"; cover = m("cover"); impasto = m("impasto"); }
  else { rule = mk; }
  if (mk === "charcoal") lineDark = lerp(.72, 1, m("hold"));
  if (mk === "graphite") lineDark = 1 - lerp(185, 40, m("grade")) / 255;
  const weight = tk === "round" || T.d.weight !== undefined ? (T.d.weight ?? 1) : 1;
  let inkSize, washSize;
  if (lineMedium) { inkSize = size * weight; washSize = Math.min(360, inkSize * 8); }
  else { washSize = size * weight; inkSize = Math.max(1.5, washSize / 10); }
  // Charcoal is the costliest stamp (its fuzz never fills): capped at 240 px.
  if (mk === "charcoal") inkSize = Math.min(inkSize, 240);
  // A Filbert (the oil paint spec) is an oval nib at its width, as a chisel's; it follows the pen's tilt (pen.filbert).
  const nib = tk === "chisel" || tk === "split" || tk === "filbert" ? "chisel" : tk === "flat" ? "flat" : "round";
  const speedAmt = h("ss"), thicker = (B.hand.d.dir | 0) === 1;
  const bb = { seed: B.seed >>> 0, wet: w, line, nib, aspect: nib === "round" ? 1 : t("aspect"), angle: T.d.angle ?? 45,
    taperIn: t("taperIn"), taperOut: t("taperOut"), pressure: h("ps"), speed: speedAmt === 0 ? 0 : (thicker ? 1 : -1) * speedAmt, thicker,
    grain: P.d.tooth ?? 0, bristle: tk === "bristles" ? 0 : t("dry"), wobble: t("wobble"), pool: m("pool"), splatter: m("splatter"), mode,
    soft: T.d.soft ?? .3, bleed: m("bleed") * wetMul, bloom: m("bloom") * wetMul, backrun: m("backrun") * wetMul, drying: L.d.drying ?? .35,
    lines: "centre", count: Math.round(T.d.count ?? 3), spread: .6, breathe: 0, offset: m("offset"), chaos: m("chaos") };
  if (mk === "ink") bb.lines = ["centre", "outline", "squiggle"][M.d.lines | 0];
  if (tk === "split") { bb.lines = "split"; bb.spread = t("gap"); bb.breathe = Math.max(.25, h("pw")); }
  if (tk === "multi") { bb.lines = "spread"; bb.spread = t("reach"); bb.breathe = h("pw"); }
  const Pa = partsA(bb, inkSize, washSize);
  const life = L.k, fast = life === "none";
  const hand = { scaleAware: true, fast, tiltWide: (B.hand.d.trange | 0) === 1, tiltSize: h("ts"), tiltAngle: h("tw"), tDark: h("td"), pDark: h("pd"), sDark: h("sd"), pWidth: h("pw"), sAngle: h("sw") };
  const pen = Object.assign(Pa.pen, hand, { fuzz: rule === "ink" ? 0 : m("spread"), thicker });
  // The hand's Tilt Shading (newmods.js): a nib laid low lays its side.
  pen.side = B.hand.d.tshade ?? 0;
  if (tk === "split" || tk === "multi") pen.pWidth = 0;   // pressure → width is their breathing, inside the placement
  const body = Object.assign(Pa.body, hand, { cover, impasto, gran: Pa.settle.gran, head: tk === "hake" ? "flat" : "round", headTurn: ["follow", "fixed"][T.d.headTurn | 0],
    tilt: Math.max(h("ts"), h("tw")), speed: Pa.body.speed ?? 0 });
  // Paint's own speed and nib: a body follows the hand's speed too (0 for every old paint), and a shaped tip's ellipse.
  body.speed = lineMedium ? 0 : bb.speed;
  if (!lineMedium && (tk === "chisel" || tk === "flat")) { body.aspect = clamp(t("aspect") * 1.6, .25, 1); body.angle = bb.angle; }
  // A Filbert's paint is its own oval, at its width, following tilt (engine.js stampBody).
  if (tk === "filbert") { pen.filbert = true; body.filbert = true; if (!lineMedium) { body.aspect = t("aspect"); body.angle = bb.angle; } }
  const look = Object.assign({}, Pa.settle, { cover, impasto, scaleAware: true });
  const R = { parts: Pa, pen, body, look, rule, paper: P.k, life, inkSize, washSize, bodySize: lineMedium ? Pa.bodySize : washSize,
    line: Pa.stampLine, body: Pa.bodyAmt > 0, lineTooth: Pa.pen.tooth, cover, gray: PAPER_TONE * (1 - shade), shade,
    // Drying's light preview grey: the swatch's shade, a wash's as dark as its glaze is to the old 34% one.
    previewShade: mk === "wash" ? WASH_BLACK * shade / GLAZE : shade,
    hold: m("hold"), spread: m("spread"), grade: m("grade"), lineInk: mk === "ink",
    windowLen: (L.d.window ?? 6) * 12 + 0.5 * (lineMedium && w < .5 ? inkSize : washSize), drying: L.d.drying ?? .35, settle: life === "dry" ? (L.d.settle ?? 0) : 0,
    takes: life === "dry" ? ["", "drift", "vein"][L.d.takes | 0] : "", plume: L.d.plume ?? .55 };
  R.body = Pa.bodyAmt > 0 ? body : null;
  R.line = Pa.stampLine;
  // Which stamper draws each part.
  R.lineTip = tk === "hake" ? "hake" : tk === "bristles" ? "bristles" : "nib";
  R.linePlaced = R.lineTip === "nib" && Pa.placed;
  R.lineStateful = R.lineTip !== "nib";
  R.placeBody = Pa.body;
  // A multi-line tip spreads its lines over a band wider than one line: the line plus 1.4 lines' room per extra line.
  R.placeSize = tk === "multi" ? Math.max(Pa.bodySize, inkSize * (2 + 1.4 * bb.count)) : Pa.bodySize;
  R.lineReach = R.linePlaced ? Math.ceil(Math.max(Pa.bodySize, tk === "multi" ? inkSize * (2 + 1.4 * bb.count) : 0) * .9 + inkSize * 3 + (bb.offset > 0 ? Pa.bodySize * 1.4 + inkSize * 2.5 : 0) + (bb.chaos > 0 ? Pa.bodySize * .6 + inkSize : 0)) : 0;
  if (R.lineTip === "hake") R.lineHake = { seed: bb.seed, mode: "flat", pressure: h("ps"), soft: T.d.soft ?? .2, streaks: t("dry"), gran: 0, angle: bb.angle, headTurn: body.headTurn,
    tilt: body.tilt, fast: false, head: "flat", cover: 0, pDark: h("pd"), pWidth: h("pw"), softK: lerp(.012, .6, T.d.soft ?? .2) };
  if (R.lineTip === "bristles") R.lineSwarm = { seed: bb.seed, hairs: t("hairs"), cohesion: t("cohesion"), spreadH: t("splay"), dryness: t("dryness"), tiltSize: h("ts"), tDark: h("td"), pDark: h("pd"), sDark: h("sd") };
  R.bodyTip = tk === "bristles" ? "bristles" : (tk === "split" || tk === "multi") && !lineMedium ? "placed" : "body";
  R.bodyStateful = tk === "bristles" || tk === "hake";
  if (R.bodyTip === "bristles") R.bodySwarm = Object.assign({}, R.lineSwarm || { seed: bb.seed, hairs: t("hairs"), cohesion: t("cohesion"), spreadH: t("splay"), dryness: t("dryness"), tiltSize: h("ts"), tDark: h("td"), pDark: h("pd"), sDark: h("sd") }, { amount: Pa.bodyAmt });
  if (R.bodyTip === "placed") {
    R.bodyLineSize = washSize * (tk === "split" ? .55 : .22);
    R.bodyPen = Object.assign({}, pen, { lines: tk === "split" ? "split" : "spread", wetGap: 0, dry: t("dry"), aspect: tk === "split" ? t("aspect") : 1, skipStart: 0, skipEnd: 0, pool: 0, splatter: 0 });
    R.bodyGeom = Object.assign({}, Pa.body, { aspect: 1 });
    R.bodyPlaceSize = tk === "multi" ? washSize * 1.8 : washSize;
  }
  // How dark: the swatch's grey, the share of the line that stays crisp, charcoal's and graphite's darkest, and Carry's
  // own-paint share, opaque paint's cover too (Smudge spec F1). A body glazes at a third of the swatch, as the app's
  // paint shades (PAINT_DARK); a wash to near black (washStrength: its full body lays .97 of the swatch's grey).
  // Clear Water lays no paint of its own (the app's ModularBrush.ownPaint).
  const load = C.k === "off" ? 1 : C.k === "water" ? 0 : (C.d.load ?? 1);
  R.inkK = shade * Pa.inkShare * lineDark * load;
  R.washK = (mk === "wash" ? washStrength(shade) : GLAZE * shade) * load;
  R.cover = cover * load;
  R.erase = M.d.erase ?? 1;
  if (life === "float") R.float = floatParts(B, R, load);
  if (C.k !== "off") {
    // As wide as the mark, to 180 px, half the largest brush (F4); at most 63 × 63 cells.
    const r = Math.max(3, Math.min(180, (lineMedium && w < .4 ? inkSize : washSize) / 2));
    // Dabs 1 + .08 r apart (r / 4 below 15 px): closer than the lab's first r / 4, so a smear streaks smoothly, without combing.
    const spacing = Math.max(1.5, Math.min(r * .25, 1 + r * .08));
    R.carry = { kind: C.k, moves: !(C.k === "lift" && R.cover > 0), pick: C.d.pick ?? .4, lay: C.d.lay ?? .5, paper: PAPER_TONE, r, spacing, hits: r / spacing, stride: r > 30 ? r / 30 : 1, n: Math.ceil(r / (r > 30 ? r / 30 : 1)) + 1,
      keep: C.k === "drag" || C.k === "smudge" ? (C.d.keep ?? 0) : 0 };
    // Smear's Soften: part or all of it MonoPaint's blending stump (stroke.js stumpDab); a Rake or a Split nib combs, along
    // its tines (as floatParts places them, a Rake's over its lines' band).
    const soften = C.k === "smudge" ? (C.d.soften ?? 0) : 0;
    if (soften > 0) {
      const tn = tinesOf(B.tip, R.inkSize, R.placeSize / 2);
      Object.assign(R.carry, { soften, tines: tn ? tn.tines : null, tineW: tn ? tn.sig : 0 });
      R.carry.tineReach = 1.6 * R.carry.tineW;
      R.carry.blur = tn ? Math.max(1, Math.round(tn.sig)) : Math.max(2, Math.min(8, Math.round(r * .2)));
    } else R.carry.soften = 0;
  }
  // Paint Load (the oil paint spec; the app's LoadedPaint): a Drag's or Smear's own paint runs out along the stroke, only
  // one that lays some and never on water. A full load lays 3000 px × Paint Load², for a 60 px brush (the root of its
  // width: half to twice); every stroke starts with its Starting Load's share (stroke.js oilSpend). A Filbert's dabs are
  // crescents then.
  if ((C.k === "drag" || C.k === "smudge") && (C.d.pload ?? 0) > 0 && life !== "float" && load > 0 && (R.line || R.body)) {
    const pl = C.d.pload, sz = R.body ? R.bodySize : R.inkSize;
    R.loaded = { full: OIL_FULL_PX * pl * pl * clamp(Math.sqrt(Math.max(1, sz) / OIL_REF_PX), .5, 2), start: clamp(C.d.pmin ?? .25) };
    if (R.body && R.body.filbert) R.body.crescent = true;
  }
  return R;
}
const OIL_FULL_PX = 3000, OIL_REF_PX = 60;
// How much wider or narrower than its full width a stroke lays at pressure pr and speed v (the app's LoadedPaint.widthShare):
// its body's dab, else its line's, 1 at a full press. A wider dab spends paint faster.
function oilWidth(R, pr, v) {
  if (R.body) return paintDiameter(R.body, 1, pr, null);
  const P = R.pen.pressure, wd = (p, q) => lerp(1 - .75 * P, 1 + .6 * P, p) * clamp(1 + R.pen.speed * .55 * (clamp(q / 2.5) - .3), .35, 1.6);
  return wd(pr, v) / wd(1, 0);
}
// A Rake's or a Split nib's tines: each one's offset across the stroke (a Rake's spread over hw either side) and their
// half-width; null for a tip that drags with one point (the app's ResolvedBrush.tinesOf).
function tinesOf(T, inkSize, hw) {
  const t = key => T.d[key] ?? 0;
  if (T.k === "multi") {
    const n = Math.round(t("count") || 3), half = hw * lerp(.3, .95, t("reach")), gap = 2 * half / Math.max(1, n - 1), tines = [];
    for (let j = 0; j < n; j++) tines.push(-half + gap * j);
    return { tines, sig: Math.max(2.5, Math.min(gap / 3, inkSize * .4)) };
  }
  if (T.k === "split") { const g = Math.max(2, inkSize * .6 * (t("gap") + .3)); return { tines: [-g, g], sig: Math.max(2.5, inkSize * .4) }; }
  return null;
}
// Float: what the stroke does on the water. Own paint (Smudge's) is how much paint it puts on the water, 0 = clear water;
// Drag and Smudge drag what floats (Pick up, with pressure, as MonoPaint's brush pen's Strength); Off and Lift don't drag.
// The pen's tines: one stylus as wide as the tip (a hake's is broad), or one per line of a split nib or multi-line.
function floatParts(B, R, load) {
  const T = B.tip, C = B.carry, L = B.life, tk = T.k, t = key => T.d[key] ?? 0;
  const drags = C.k === "drag" || C.k === "smudge" || C.k === "water";
  const lineW = R.inkSize / 2 * (tk === "multi" ? Math.round(t("count") || 3) : tk === "split" ? 2 : 1);
  const filmW = Math.max(R.body ? R.bodySize / 2 : 0, R.line ? lineW : 0, 1);
  const hw = R.body ? R.bodySize / 2 : tk === "multi" ? R.placeSize / 2 : R.inkSize / 2;
  let tines = [0], sig = Math.max(3, hw * (tk === "hake" ? .7 : .8));
  const tn = tinesOf(T, R.inkSize, hw);
  if (tn) ({ tines, sig } = tn);
  return { spread: L.d.spread ?? .5, drift: L.d.drift ?? .5, dryMs: wetMsOf(L.d.drying ?? .6), load, pick: drags ? (C.d.pick ?? .4) : 0,
    filmW, tines, sig, lineDark: clamp(R.inkK), bodyDark: laid(R.washK * 1.2), dropDark: R.line ? clamp(R.inkK) : laid(R.washK * 1.2) };
}
// ---------- pen-paint-blend's A: two parts, a line and a wash, fed the same samples (copied) ----------
function partsA(bb, inkSize, washSize) {
  const w = bb.wet, s = smooth(w), mid = 4 * w * (1 - w), wetPaint = bb.mode === "wet";
  const pen = { seed: bb.seed, nib: bb.nib, aspect: bb.aspect, angle: bb.angle, taperIn: bb.taperIn, taperOut: bb.taperOut,
    pressure: bb.pressure, speed: bb.speed, dry: bb.bristle * (1 - w), tooth: bb.grain * (1 - w), wobble: bb.wobble,
    pool: bb.pool * (1 - w), splatter: bb.splatter,
    lines: bb.lines || "centre", count: bb.count, spread: bb.spread, breathe: bb.breathe, wetGap: clamp(w * 1.8),
    offset: bb.offset || 0, chaos: bb.chaos || 0, skipStart: 0, skipEnd: 0 };
  const inkShare = mix(1, bb.line, s);
  const lineWet = mid * 1.2 + w * bb.line;
  const bodyAmt = smooth((w - .08) / .75);
  const body = { seed: bb.seed, mode: bb.mode, pressure: bb.pressure, soft: mix(1, bb.soft, s), streaks: bb.bristle,
    aspect: mix(bb.aspect, 1, s), angle: bb.angle, taperIn: bb.taperIn * (1 - w), taperOut: bb.taperOut * (1 - w), amount: bodyAmt, skipStart: 0, skipEnd: 0 };
  const bodySize = mix(inkSize * 2.2, washSize, Math.pow(w, 1.4));
  if (pen.offset > 0) {
    const r = rng(bb.seed ^ 0x1a6), len = () => pen.offset * (14 + bodySize * .55) * (.4 + .6 * r());
    const a = len(), b = len();
    if (r() < .5) pen.skipStart = a; else body.skipStart = a;
    if (r() < .5) pen.skipEnd = b; else body.skipEnd = b;
  }
  const settle = { feather: clamp(bb.bleed * s + .3 * mid), hollow: wetPaint ? bb.pool * s : 0, bloom: wetPaint ? bb.bloom * s : 0,
    backrun: wetPaint ? bb.backrun * s : 0, gran: bb.grain * s, streaks: 0 };
  return { w, pen, inkShare, lineWet, body, bodyAmt, settle, lineSize: inkSize, bodySize, diffuse: mid, diffR: Math.round(1 + mid * (1.5 + inkSize * .5)),
    stampLine: inkShare > 0 || lineWet > 0, placed: pen.lines !== "centre" || pen.offset > 0 || pen.chaos > 0 };
}

// ===================================================================================================================
// Styles: a preset of parts plus ranges. A range is [lo, hi] always, {p, lo, hi} on that often, or bands with weights.
// A dial a style leaves out rolls at its choice's default.
// ===================================================================================================================
const WILDCARD = "Anything goes";
const MSTYLES = {
  "Fineliner": { size: 4, parts: { tip: { round: 1 }, medium: { ink: 1 }, carry: { off: 1 }, paper: { plain: 3, "cold press": 1 }, life: { wet: 1 } },
    d: { "tip.taperIn": { p: .4, lo: 0, hi: .15 }, "tip.taperOut": { p: .4, lo: 0, hi: .2 }, "tip.wobble": { p: .2, lo: 0, hi: .1 }, "tip.weight": [.9, 1.1],
      "medium.wet": [0, 0], "medium.line": [1, 1], "medium.pool": { p: .4, lo: 0, hi: .3 }, "paper.tooth": { p: .3, lo: 0, hi: .15 }, "hand.ps": [0, .25], "hand.ss": { p: .4, lo: 0, hi: .2 } } },
  "Brush pen": { size: 8, parts: { tip: { round: 2, chisel: 1 }, medium: { ink: 1 }, carry: { off: 1 }, paper: { plain: 2, "cold press": 1, felt: 1 }, life: { wet: 1 } },
    d: { "tip.taperIn": [.3, .8], "tip.taperOut": [.4, 1], "tip.dry": { p: .3, lo: 0, hi: .3 }, "tip.wobble": { p: .3, lo: 0, hi: .15 }, "tip.aspect": [.3, .55],
      "medium.wet": { p: .2, lo: .1, hi: .35 }, "medium.line": [.75, 1], "medium.pool": { p: .4, lo: 0, hi: .4 }, "medium.splatter": { p: .15, lo: 0, hi: .3 },
      "paper.tooth": { p: .3, lo: 0, hi: .2 }, "hand.ps": [.7, 1], "hand.pd": { p: .5, lo: .2, hi: .5 }, "hand.ss": [0, .7], "hand.ts": { p: .4, lo: .3, hi: .8 } } },
  "Dip pen": { size: 6, parts: { tip: { flat: 2, chisel: 1, split: 2 }, medium: { ink: 1 }, carry: { off: 1 }, paper: { plain: 1, laid: 1, "cold press": 1 }, life: { wet: 1 } },
    d: { "tip.angle": [30, 60], "tip.gap": [.35, .8], "tip.taperIn": { p: .5, lo: 0, hi: .3 }, "tip.taperOut": { p: .5, lo: 0, hi: .4 }, "tip.dry": { p: .25, lo: 0, hi: .25 },
      "medium.wet": { p: .15, lo: .1, hi: .3 }, "medium.pool": { p: .6, lo: .1, hi: .6 }, "medium.splatter": { p: .3, lo: 0, hi: .35 }, "paper.tooth": { p: .3, lo: 0, hi: .2 },
      "hand.ps": [.4, .85], "hand.pw": [.5, 1], "hand.ss": { p: .5, lo: 0, hi: .3 }, "hand.tw": { p: .5, lo: .4, hi: .9 } } },
  "Marker": { size: 12, parts: { tip: { chisel: 2, round: 1 }, medium: { ink: 1 }, carry: { off: 1 }, paper: { plain: 1, felt: 1 }, life: { wet: 1 } },
    d: { "tip.aspect": [.35, .55], "medium.wet": { p: .3, lo: .05, hi: .25 }, "medium.pool": [.3, .8], "medium.bleed": { p: .3, lo: 0, hi: .3 }, "paper.tooth": { p: .3, lo: 0, hi: .2 },
      "hand.ps": [0, .2], "hand.ss": [0, .3], "hand.tw": { p: .3, lo: .3, hi: .7 } } },
  "Pencil": { size: 4, parts: { tip: { round: 1 }, medium: { graphite: 1 }, carry: { off: 6, smudge: 1 }, paper: { "cold press": 1, felt: 1, laid: 1 }, life: { wet: 1 } },
    d: { "medium.grade": [.2, .8], "medium.spread": { p: .3, lo: 0, hi: .15 }, "tip.taperIn": { p: .4, lo: 0, hi: .2 }, "tip.taperOut": { p: .4, lo: 0, hi: .25 }, "tip.wobble": { p: .3, lo: 0, hi: .12 },
      "paper.tooth": [.5, .9], "carry.pick": [.3, .6], "hand.ps": [.2, .5], "hand.pd": [.4, .8], "hand.ts": [.3, .7], "hand.td": [.3, .6] } },
  "Charcoal": { size: 30, parts: { tip: { chisel: 2, round: 1 }, medium: { charcoal: 1 }, carry: { off: 3, smudge: 1 }, paper: { "cold press": 2, laid: 1, plain: 1 }, life: { wet: 1 } },
    d: { "medium.spread": { bands: [[.7, .15, .45], [.15, 0, .1], [.15, .6, .9]] }, "medium.hold": [.1, .8], "tip.taperIn": { p: .4, lo: 0, hi: .3 }, "tip.taperOut": { p: .4, lo: 0, hi: .3 },
      "tip.dry": { p: .5, lo: .1, hi: .4 }, "tip.wobble": { p: .6, lo: 0, hi: .4 }, "tip.aspect": [.3, .55], "paper.tooth": [.6, 1],
      "hand.ps": [.5, .9], "hand.pd": [.3, .7], "hand.ts": [.6, 1], "hand.td": [.4, .7] } },
  "Watercolor": { size: 120, parts: { tip: { round: 1 }, medium: { wash: 1 }, carry: { off: 1 }, paper: { plain: 1, "cold press": 2 }, life: { wet: 4, dry: 1 } },
    d: { "tip.soft": [.2, .6], "medium.wet": [.7, 1], "medium.pool": { p: .6, lo: .2, hi: .7 }, "medium.bleed": { p: .3, lo: 0, hi: .3 }, "medium.bloom": { p: .6, lo: .2, hi: .6 },
      "medium.backrun": { p: .3, lo: 0, hi: .5 }, "paper.tooth": { p: .5, lo: 0, hi: .5 }, "life.drying": [.3, .7], "hand.ps": [.3, .8] } },
  "Ink wash": { size: { ink: 8, wash: 90 }, parts: { tip: { round: 1 }, medium: { ink: 3, wash: 2 }, carry: { off: 1 }, paper: { plain: 1, felt: 1 }, life: { wet: 3, dry: 1 } },
    d: { "tip.soft": [.5, 1], "tip.taperIn": { p: .5, lo: .1, hi: .5 }, "tip.taperOut": { p: .5, lo: .2, hi: .7 }, "medium.wet": [.45, .9], "medium.line": [.5, 1],
      "medium.bleed": [.3, .8], "medium.bloom": { p: .3, lo: 0, hi: .3 }, "medium.pool": { p: .3, lo: 0, hi: .3 }, "life.drying": [.5, .9], "hand.ps": [.4, .9] } },
  "Gouache": { size: 60, parts: { tip: { round: 2, flat: 1, hake: 1 }, medium: { cover: 1 }, carry: { off: 1, lift: 1 }, paper: { plain: 2, felt: 1, "cold press": 1 }, life: { wet: 1 } },
    d: { "medium.cover": [1, 1], "medium.impasto": [0, 0], "tip.soft": [0, .2], "tip.dry": { p: .3, lo: 0, hi: .3 }, "tip.weight": [.8, 1.1], "paper.tooth": { p: .3, lo: 0, hi: .2 }, "hand.ps": [0, .3] } },
  "Oils": { size: 60, parts: { tip: { round: 2, flat: 1, hake: 1 }, medium: { cover: 1 }, carry: { drag: 7, off: 3 }, paper: { canvas: 1 }, life: { wet: 1 } },
    d: { "medium.cover": [1, 1], "medium.impasto": [.4, .9], "tip.dry": [.5, .95], "tip.soft": [0, .15], "paper.tooth": { p: .4, lo: 0, hi: .3 }, "carry.pick": [.4, .8],
      "hand.ps": [.3, .7], "hand.ts": { p: .4, lo: .4, hi: .9 } } },
  "Pastels": { size: 24, parts: { tip: { chisel: 1, round: 1 }, medium: { charcoal: 1 }, carry: { off: 2, smudge: 1 }, paper: { "cold press": 1, felt: 1, crumpled: 1, laid: 1 }, life: { wet: 1 } },
    d: { "medium.spread": [.1, .35], "medium.hold": [.1, .5], "tip.dry": [.1, .3], "tip.aspect": [.35, .55], "paper.tooth": [.6, 1], "hand.ps": [.2, .6], "hand.ts": [.5, 1], "hand.td": [.3, .6] } },
  "Dry brush": { size: 60, parts: { tip: { round: 2, bristles: 2, flat: 1 }, medium: { wash: 7, cover: 3 }, carry: { off: 1 }, paper: { "cold press": 1, laid: 1, crumpled: 1 }, life: { wet: 1 } },
    d: { "medium.wet": [0, .4], "medium.cover": [.5, .9], "tip.dry": [.5, 1], "tip.soft": [0, .3], "tip.dryness": [.4, .8], "tip.splay": [.4, .9], "tip.hairs": [16, 32], "paper.tooth": [.3, .8], "hand.ps": [.3, .7] } },
  "Hake": { size: 180, parts: { tip: { hake: 1 }, medium: { wash: 7, cover: 3 }, carry: { off: 1 }, paper: { plain: 1, "cold press": 1 }, life: { wet: 1 } },
    d: { "tip.headTurn": { opts: [0, 0, 0, 1] }, "tip.angle": [70, 80], "tip.dry": [.2, .6], "tip.soft": [.1, .4], "medium.wet": [.3, 1], "medium.cover": [.5, .9], "hand.ps": [.1, .4] } },
  [WILDCARD]: { size: 16, wild: true },
  // Print (the app's own, after Anything goes as codes carry the index): every roll prints through a Print Shading, or
  // one in five is Screentone (newmods.js rollScreen). Pens a little broader than a Fineliner's; charcoal, pencil and wash broader still.
  "Print": { size: { ink: 12, charcoal: 30, graphite: 14, wash: 40 }, parts: { tip: { round: 2, chisel: 1 }, medium: { ink: 3, wash: 1, charcoal: 1, graphite: 1 }, carry: { off: 1 }, paper: { plain: 2, "cold press": 1 }, life: { wet: 1 } },
    d: { "tip.soft": [.3, .7], "tip.aspect": [.35, .55], "tip.taperIn": { p: .3, lo: 0, hi: .2 }, "tip.taperOut": { p: .3, lo: 0, hi: .3 }, "medium.grade": [.4, .8], "medium.spread": [.1, .4],
      "medium.hold": [.3, .7], "medium.wet": [0, 0], "medium.line": [1, 1], "paper.tooth": { p: .3, lo: 0, hi: .3 }, "hand.ps": [.1, .4], "hand.pd": [.4, .8] } }
};
const STYLE_NAMES = Object.keys(MSTYLES);
// The size a style draws at best, for the size slot nearest to it.
function styleSize(name, B) { const s = (MSTYLES[name] || MSTYLES[WILDCARD]).size; return typeof s === "number" ? s : s[B && B.medium.k] || Object.values(s)[0]; }
const rnd = () => Math.random();
function pickWeighted(map) { const e = Object.entries(map); let t = 0; for (const [, v] of e) t += v; let r = rnd() * t; for (const [k, v] of e) { r -= v; if (r <= 0) return k; } return e[e.length - 1][0]; }
function rollSpec(spec, lo, hi) {
  if (Array.isArray(spec)) return lerp(spec[0], spec[1], rnd());
  if (spec.bands) { const r = rnd(); let a = 0; for (const [p, l, h] of spec.bands) { a += p; if (r <= a) return lerp(l, h, rnd()); } const b = spec.bands[0]; return lerp(b[1], b[2], rnd()); }
  if (spec.opts) return spec.opts[(rnd() * spec.opts.length) | 0];
  return rnd() < spec.p ? lerp(spec.lo, spec.hi, rnd()) : 0;
}
// The range a style keeps a dial in (for the hatched band and for variations), or the dial's own range.
function styleBand(style, part, k, key) {
  const st = MSTYLES[style], spec = st && st.d && st.d[part + "." + key], [lo, hi] = rangeOf(part, k, key);
  if (!spec || DIALS[part][key].choices) return null;
  if (Array.isArray(spec)) return [clamp(spec[0], lo, hi), clamp(spec[1], lo, hi)];
  if (spec.bands) return [Math.min(...spec.bands.map(b => b[1])), Math.max(...spec.bands.map(b => b[2]))];
  if (spec.opts) return null;
  return [spec.p < 1 ? 0 : spec.lo, spec.hi];
}
// Wildcard: any choice of every part; a rough dial is off about half the time, so plain brushes still turn up.
const WILD_PARTS = { tip: { round: 3, chisel: 2, flat: 2, hake: 1, split: 1.5, multi: 1.5, bristles: 1 }, medium: { ink: 3, wash: 3, cover: 2, charcoal: 1.5, graphite: 1 },
  carry: { off: 6, lift: 1, drag: 1, smudge: 1 }, paper: { plain: 3, "cold press": 2, laid: 1, felt: 1, crumpled: 1, canvas: 1 }, life: { wet: 7, dry: 2, none: 1 } };
const ROUGH = { "tip.dry": 1, "tip.wobble": 1, "paper.tooth": 1 };
function rollPart(style, part, old, locked) {
  const st = MSTYLES[style] || MSTYLES[WILDCARD];
  if (part === "hand") return rollHand(st);
  const weights = st.wild ? WILD_PARTS[part] : st.parts[part];
  const k = pickWeighted(weights);
  const P = freshPart(part, k);
  for (const key of choiceOf(part, k).dials) {
    if (locked && locked.has(part + "." + key) && old && old.k === k) { P.d[key] = old.d[key]; continue; }
    if (ADDED.has(part + "." + key)) { if (key === "erase") P.d.erase = realErase(style, k); continue; }
    P.d[key] = rollDial(style, part, k, key);
  }
  if (st.wild) {
    let rough = 0; for (const key of Object.keys(P.d)) if (ROUGH[part + "." + key]) rough += P.d[key];
    if (rough > 1.1) for (const key of Object.keys(P.d)) if (ROUGH[part + "." + key]) P.d[key] *= 1.1 / rough;
  }
  return normPart(part, P);
}
function rollDial(style, part, k, key) {
  const st = MSTYLES[style] || MSTYLES[WILDCARD], s = DIALS[part][key], spec = st.d && st.d[part + "." + key], [lo, hi] = rangeOf(part, k, key);
  if (s.choices) { if (spec && spec.opts) return spec.opts[(rnd() * spec.opts.length) | 0]; if (st.wild || !spec) return st.wild ? (rnd() * s.choices.length) | 0 : dialDefault(part, k, key); }
  if (spec) return clamp(rollSpec(spec), lo, hi);
  if (!st.wild) return dialDefault(part, k, key);
  if (part === "life" && key === "window") return lerp(3, 9, rnd());
  if (key === "weight") return lerp(.8, 1.2, rnd());
  if (key === "line" && part === "medium") return k === "ink" ? lerp(.6, 1, rnd()) : rnd() < .7 ? 0 : lerp(.3, .8, rnd());
  if (key === "cover") return lerp(.6, 1, rnd());
  if (key === "offset" || key === "chaos") return rnd() < .25 ? lerp(0, .7, rnd()) : 0;
  if (s.min === 0 && !["wet", "soft", "angle", "aspect", "gap", "reach", "splay", "cohesion", "spread", "hold", "grade", "pick", "lay", "load", "drying", "settle", "plume", "dryness"].includes(key)) return rnd() < .5 ? lerp(lo, Math.min(hi, lo + (hi - lo) * .8), rnd()) : 0;
  return lerp(lo, hi, rnd());
}
function rollHand(st) {
  const P = freshPart("hand", "custom");
  if (st.wild) {
    P.d.ps = rnd() < .75 ? lerp(.3, 1, rnd()) : 0; P.d.pd = rnd() < .35 ? lerp(.2, .8, rnd()) : 0; P.d.pw = rnd() < .3 ? lerp(.3, 1, rnd()) : 0;
    P.d.ts = rnd() < .3 ? lerp(.3, 1, rnd()) : 0; P.d.td = rnd() < .2 ? lerp(.2, .7, rnd()) : 0; P.d.tw = rnd() < .25 ? lerp(.3, 1, rnd()) : 0;
    P.d.ss = rnd() < .35 ? lerp(.2, .8, rnd()) : 0; P.d.sd = rnd() < .2 ? lerp(.2, .7, rnd()) : 0; P.d.sw = rnd() < .15 ? lerp(.2, .8, rnd()) : 0;
    P.d.dir = rnd() < .5 ? 1 : 0;
  } else for (const key of HAND_CELLS) { const spec = st.d && st.d["hand." + key]; P.d[key] = spec ? rollSpec(spec) : 0; }
  P.k = matchHand(P);
  return normPart("hand", P);
}
// Float is rare: decided after every other part, from the brush's seed rather than another random draw, so every other
// roll (and the lab's seeded starting library) comes out exactly as before. Anything goes 1 in 30, Ink wash 1 in 25,
// Watercolor 1 in 40; no other style.
const FLOAT_ODDS = { [WILDCARD]: 30, "Ink wash": 25, "Watercolor": 40 };
function rollFloat(style, B) {
  const n = FLOAT_ODDS[style]; if (!n) return;
  const h = Math.imul((B.seed >>> 0) ^ 0x5f10a7, 2654435761) >>> 0;
  if (h % n) return;
  const P = switchChoice("life", B.life, "float");
  P.d.spread = .3 + .5 * ((h >>> 8) % 1000) / 1000; P.d.drift = .5;
  B.life = P;
}
// A hand that is exactly one of the presets is named for it; anything else is Custom.
function matchHand(P) {
  for (const c of CHOICES.hand) if (c.cells && HAND_CELLS.every(k => Math.abs((P.d[k] || 0) - (c.cells[k] || 0)) < .005)) return c.k;
  return "custom";
}
// A new brush in a style. locks: { parts: Set of part names, dials: Set of "part.key" }, empty in Simple.
function rollStyle(style, old, locks) {
  const lk = locks || { parts: new Set(), dials: new Set() };
  const B = { name: freshName(), seed: newSeed(), style };
  for (const part of PARTS) {
    if (old && lk.parts.has(part)) { B[part] = cloneBrush(old[part]); continue; }
    B[part] = rollPart(style, part, old && old[part], lk.dials);
    if (old && old[part].k === B[part].k) for (const key of Object.keys(B[part].d)) if (lk.dials.has(part + "." + key)) B[part].d[key] = old[part].d[key];
  }
  keepErase(B, old);
  if (!(old && lk.parts.has("life"))) rollFloat(style, B);
  normBrush(B);
  // Last: what stands out takes the adjective's place ("drip-plum"), from the finished brush and its seed.
  B.name = describedName(B, B.name);
  return B;
}
// One part rolled again (a card's dice), in the brush's style, keeping locked dials.
function rollOnePart(B, part, locks) {
  const c = cloneBrush(B), old = B[part];
  c[part] = rollPart(B.style, part, old, locks.dials);
  if (old.k === c[part].k) for (const key of Object.keys(c[part].d)) if (locks.dials.has(part + "." + key)) c[part].d[key] = old.d[key];
  if (part === "medium") keepErase(c, B);
  return normBrush(c);
}
function rollOneDial(B, part, key) {
  const c = cloneBrush(B), P = c[part];
  // Erasable has no die (only Resist's, wax or fluid): it moves only by hand.
  if (part === "medium" && key === "erase" && P.k !== "resist") return c;
  if (part === "hand" && HAND_CELLS.includes(key)) { P.d[key] = rnd() < .3 ? 0 : lerp(.25, 1, rnd()); P.k = matchHand(P); }
  else { const v = rollDial(B.style, part, P.k, key), bd = styleBand(B.style, part, P.k, key), [lo, hi] = rangeOf(part, P.k, key);
    P.d[key] = bd || DIALS[part][key].choices ? v : lerp(lo, hi, rnd()); }
  return normBrush(c);
}

// ===================================================================================================================
// Variations (Advanced only): six "your brush, but…" cards. Each nudges two or three unlocked dials; at most one of the
// six also swaps one part's choice, and says so ("Split nib", "Instant drying").
// ===================================================================================================================
function makeVars(B, locks) {
  const out = [], swapAt = rnd() < .85 ? (rnd() * 6) | 0 : -1;
  const pool = [];
  for (const part of PARTS) {
    if (locks.parts.has(part)) continue;
    for (const key of choiceOf(part, B[part].k).dials) {
      if (locks.dials.has(part + "." + key) || DIALS[part][key].choices || key === "gain" || ADDED.has(part + "." + key)) continue;
      pool.push([part, key]);
    }
  }
  for (let i = 0; i < 6; i++) {
    const c = cloneBrush(B), caps = []; let swap = null;
    c.seed = newSeed(); c.name = kinOf(B.name, c.seed);
    if (i === swapAt) {
      const parts = PARTS.filter(p => !locks.parts.has(p) && p !== "hand");
      const part = parts[(rnd() * parts.length) | 0];
      if (part) {
        const others = CHOICES[part].filter(ch => ch.k !== B[part].k && !(part === "hand" && ch.k === "custom"));
        const ch = others[(rnd() * others.length) | 0];
        c[part] = switchChoice(part, B[part], ch.k);
        caps.push(part === "life" && ch.k !== "float" ? sentenceCase(ch.label) + " drying" : sentenceCase(ch.label)); swap = caps[0];
      }
    }
    const pl = pool.slice(), n = 2 + (rnd() < .4 ? 1 : 0);
    for (let j = 0; j < n && pl.length; j++) {
      const [part, key] = pl.splice((rnd() * pl.length) | 0, 1)[0];
      if (!(key in c[part].d)) continue;
      const [lo0, hi0] = rangeOf(part, c[part].k, key), bd = styleBand(B.style, part, c[part].k, key) || [lo0, hi0];
      const v = c[part].d[key], lo = Math.min(bd[0], v), hi = Math.max(bd[1], v), span = hi - lo || (hi0 - lo0) * .3;
      const rel = (v - lo) / (span || 1);
      let sign = rnd() < .5 ? -1 : 1; if (rel < .08) sign = 1; else if (rel > .92) sign = -1;
      let nv = clamp(v + sign * lerp(.35, .8, rnd()) * span, lo0, hi0);
      if (DIALS[part][key].min === 0 && nv < .04) nv = 0;
      c[part].d[key] = nv;
      if (part === "hand") c.hand.k = matchHand(c.hand);
      caps.push((nv > v ? "more " : "less ") + dialLabel(part, c[part].k, key).toLowerCase().replace(/ \(.*\)/, ""));
    }
    out.push({ b: normBrush(c), caption: caps, swap });
  }
  return out;
}

// ===================================================================================================================
// Words: a card's choice in words, the hand's summary, the library caption and the plain "This brush" description.
// ===================================================================================================================
// The house style (docs/TEXT-STYLE.md), as the app's ModularWords: named, not explained; only what is unusual.
function handWords(B, n = 2) {
  const d = B.hand.d, g = d.gain ?? 1, items = [
    ["ps", "pressure", "widens"], ["pd", "pressure", "darkens"], ["pw", "pressure", B.tip.k === "split" ? "opens tines" : B.tip.k === "multi" ? "spreads lines" : "widens nib"],
    ["ts", "tilt", (d.trange | 0) ? "broadens" : "widens"], ["td", "tilt", "lightens"], ["tw", "tilt", "turns nib"],
    ["ss", "speed", (d.dir | 0) ? "thickens" : "thins"], ["sd", "speed", "lightens"], ["sw", "speed", "turns nib"]];
  const on = items.map(([k, c, e]) => [Math.min(1, (d[k] || 0) * g), c, e]).filter(x => x[0] > .15).sort((a, b) => b[0] - a[0]);
  if (!on.length) return "steady";
  const [, c1, e1] = on[0];
  if (n < 2 || on.length < 2) return c1 + " " + e1;
  const [, c2, e2] = on[1], verb = e => e.split(" ")[0], plural = e => verb(e).slice(0, -1) + e.slice(verb(e).length);
  if (c1 === c2) return verb(e1) === verb(e2) ? c1 + " " + e1 : c1 + " " + e1 + " and " + e2;
  if (e1 === e2) return c1 + " and " + c2 + " " + plural(e1);
  return c1 + " " + e1 + ", " + c2 + " " + e2;
}
// A Float brush with no paint of its own: clear water (Smudge's Own paint 0).
// A Smear that is mostly MonoPaint's blending stump (Soften from .5): "Softens.", tagged "stump"; with tines, "Combs.", "comb".
const isStump = B => B.carry.k === "smudge" && (B.carry.d.soften ?? 0) >= .5;
const combs = B => B.tip.k === "multi" || B.tip.k === "split";
const isClearWater = B => B.life.k === "float" && (B.carry.k === "water" || (B.carry.k === "drag" || B.carry.k === "smudge") && (B.carry.d.load ?? 1) < .02);
// Smudge's Clear Water off the water, and Keeps Paint on (a dirty brush; not all stump): the wet smudge spec.
const isWater = B => B.carry.k === "water" && B.life.k !== "float";
const keepsPaint = B => (B.carry.k === "drag" || B.carry.k === "smudge") && (B.carry.d.keep ?? 0) > 0 && (B.carry.d.soften ?? 0) < 1;
// Paint Load on (the oil paint spec; ModularWords.runsOut): its own paint runs out along the stroke. Not on water, nor
// Resist or Screentone, laid as a crisp ink line with no smudge.
const runsOut = B => (B.carry.k === "drag" || B.carry.k === "smudge") && (B.carry.d.pload ?? 0) > 0 && B.life.k !== "float" && (B.carry.d.load ?? 1) > 0
  && B.medium.k !== "resist" && B.medium.k !== "tone";
const dryingSec = B => (wetMsOf(B.life.d.drying) / 1000).toFixed(1);
const floatSec = B => { const sec = wetMsOf(B.life.d.drying) / 1000; return sec.toFixed(sec >= 9.95 ? 0 : 1); };
function choiceWords(B, part) {
  const P = B[part], c = choiceOf(part, P.k), d = P.d;
  if (part === "hand") return handWords(B);
  if (part === "medium") {
    if (P.k === "ink") return d.wet < .05 ? "Ink" : d.wet < .6 ? "Wet ink" : "Ink wash";
    if (P.k === "wash") return d.wet < .5 ? "Flat wash" : "Wet wash";
    if (P.k === "cover") return d.impasto > .3 ? "Thick paint" : "Opaque";
    if (P.k === "charcoal") return d.spread > .5 ? "Messy charcoal" : d.spread < .12 ? "Crisp charcoal" : "Charcoal";
    if (P.k === "graphite") return "Graphite " + gradeOf(d.grade);
  }
  if (part === "life") {
    if (P.k === "dry") return "Slow " + dryingSec(B) + " s";
    if (P.k === "float") return "Float " + floatSec(B) + " s";
    if (P.k === "wet") return "Quick";
    return "Instant";
  }
  if (part === "tip" && P.k === "multi") return d.count + " lines";
  // Clear Water's module has room for one word, as Smudge's others.
  if (part === "carry" && P.k === "water") return "Water";
  return c.label;
}
// The note under a swap tile (ModularWords.swapNote): a drying's time or how it dries; nothing for Quick.
const lifeNote = b => b.life.k === "halo" ? "mold " + dryingSec(b) + " s" : b.life.k === "fade" ? "fades " + fmtDial("life", "fadeIn", b.life.d.fadeIn) : b.life.k === "lemon" ? "darkens " + fmtDial("life", "dryIn", b.life.d.dryIn) : b.life.k === "float" ? (isClearWater(b) ? "clear water" : "floats " + floatSec(b) + " s") : b.life.k === "dry" ? "wet " + dryingSec(b) + " s" : b.life.k === "wet" ? "" : "dries at once";
// The choice a style weighs most for a part (the first of equals), as ModularWords.usual.
function usual(style, part) {
  const w = (MSTYLES[style] || {}).parts?.[part] || {};
  let best = null, most = 0;
  for (const ch of CHOICES[part]) if ((w[ch.k] || 0) > most) { most = w[ch.k]; best = ch.k; }
  return best;
}
const STYLE_LABEL = s => s.replace("Watercolor", "Watercolour");
// The caption's items (ModularWords.caption): the style, then what is unusual for it, in display words.
function captionItems(B) {
  const style = B.style || WILDCARD, lc = t => t.toLowerCase(), items = [STYLE_LABEL(style)], M = B.medium, md = M.d;
  if (B.life.k === "float") items.push(isClearWater(B) ? "clear water" : "float");
  const tip = choiceOf("tip", B.tip.k).label;
  if (B.tip.k !== usual(style, "tip") && lc(tip) !== lc(style)) items.push(lc(tip));
  const tag = M.k === "ink" ? (md.wet < .05 ? "ink" : md.wet < .6 ? "wet ink" : "ink wash") : M.k === "wash" ? (md.wet < .5 ? "flat wash" : "wet wash")
    : M.k === "cover" ? (md.impasto > .3 ? "thick paint" : "opaque") : M.k === "charcoal" ? (md.spread > .5 ? "messy" : md.spread < .12 ? "crisp" : "charcoal")
    : gradeOf(md.grade) + (style === "Pencil" ? "" : " graphite");
  const plain = { ink: "ink", wash: "wet wash", cover: "opaque", charcoal: "charcoal" }[M.k];
  if (tag !== lc(style) && !(M.k === usual(style, "medium") && tag === plain)) items.push(tag);
  // Paint Load in the smudge's place: "Oils, filbert, runs out".
  if (B.carry.k !== "off" && !(B.carry.k === "water" && B.life.k === "float")) items.push(runsOut(B) ? "runs out" : isStump(B) ? (combs(B) ? "comb" : "stump") : lc(choiceOf("carry", B.carry.k).label));
  if (keepsPaint(B)) items.push("keeps paint");
  const er = eraseLabel(B); if (er) items.push(lc(er));
  if (B.paper.k !== "plain" && B.paper.k !== usual(style, "paper")) items.push(lc(choiceOf("paper", B.paper.k).label));
  if (B.life.k === "dry") items.push("slow"); else if (B.life.k === "none") items.push("instant");
  return items;
}
// Erasable, said only when it isn't the medium's own (ModularWords.erase): an ink that erases, a pencil that doesn't.
function eraseLabel(B) {
  const e = B.medium.d.erase ?? 1;
  if (Math.abs(e - realErase(B.style, B.medium.k)) < .05) return "";
  return e >= .95 ? "Erasable" : e <= .05 ? "Permanent" : "Part erasable";
}
// Two caption lines of 17 characters, comma-joined while they fit (BrushCaptions.twoLines).
function captionLines(B) {
  const lines = [];
  for (const item of captionItems(B)) {
    if (item.length > 17) continue;
    if (lines.length && (lines[lines.length - 1] + ", " + item).length <= 17) lines[lines.length - 1] += ", " + item;
    else if (lines.length < 2) lines.push(item);
  }
  return lines;
}
const PAPER_WORDS = { "cold press": "cold-press paper", laid: "ribbed paper", felt: "felt paper", crumpled: "crumpled paper", canvas: "canvas", plain: "smooth paper" };
const cap1 = t => t.charAt(0).toUpperCase() + t.slice(1);
// Up to five short sentences, in the order a painter notices (ModularWords.plainWords): what it is and what it's on;
// its two strongest marks; its smudge; its drying when not Quick; how it answers pressure, tilt and speed.
function plainWords(B) {
  const T = B.tip, M = B.medium, out = [], md = M.d, td = T.d, style = B.style || WILDCARD;
  if (isClearWater(B)) return "Clear water. Taps drop clear rings, dragging combs them" + (B.carry.d.pick > .05 ? "" : " a little") + ".";
  const tipNoun = { round: "round tip", chisel: "chisel tip", flat: "flat nib", hake: "hake", split: "split nib", multi: (td.count || 3) + "-line rake", bristles: "bristle brush", filbert: "filbert" }[T.k];
  let noun;
  if (M.k === "ink") noun = md.wet < .05 ? "Ink line" : md.wet < .45 ? "Line and wash" : "Ink wash";
  else if (M.k === "wash") noun = md.wet < .5 ? "Flat wash" : "Wet wash";
  else if (M.k === "cover") noun = md.impasto > .3 ? "Thick paint" : md.cover > .8 ? (style === "Oils" ? "Oil paint" : "Gouache") : "Semi-opaque paint";
  else if (M.k === "charcoal") noun = style === "Pastels" ? (md.hold > .6 ? "Hard pastel" : "Soft pastel") : md.hold > .6 ? "Compressed charcoal" : "Vine charcoal";
  else noun = gradeOf(md.grade) + " graphite";
  const floats = B.life.k === "float", tip = T.k === "round" ? "" : ", " + tipNoun, on = floats ? " on water" : B.paper.k === "plain" ? "" : " on " + PAPER_WORDS[B.paper.k];
  out.push((floats && M.k === "ink" ? "Sumi ink" : isWater(B) ? "Clear water" : noun) + tip + (tip && on ? "," : "") + on + ".");
  const marks = [];
  const add = (v, th, words) => { if (v > th) marks.push([v - th, words]); };
  add(td.taperIn + td.taperOut, .9, "tapers to a point"); add(td.dry || 0, .3, T.k === "hake" ? "dry-brush streaks" : "dry-brush texture");
  add(td.wobble || 0, .25, "slight wobble"); add(md.pool || 0, .35, M.k === "wash" ? "dark edges" : "pools when slow");
  add(md.splatter || 0, .3, "spatters on flicks"); add(md.bloom || 0, .3, "blooms"); add(md.backrun || 0, .3, "backruns");
  add(md.bleed || 0, .35, "bleeds"); add(md.impasto || 0, .3, "raised ridges");
  if (M.k === "charcoal") add(md.spread + .3, .35, md.spread > .5 ? "dusty broken edge" : "soft speckled edge");
  add(md.offset || 0, .25, "line shifted"); add(md.chaos || 0, .25, "broken line");
  if (T.k === "split") marks.push([.5, "opens into two lines"]);
  if (T.k === "bristles") marks.push([.4, "splits into strands"]);
  if (B.paper.d.tooth > .55) marks.push([B.paper.d.tooth - .5, "picks up the texture"]);
  const strong = marks.sort((a, b) => b[0] - a[0]).slice(0, 2).map(m => m[1]);
  if (strong.length) out.push(cap1(strong.join(", ")) + ".");
  // Smudge Off says nothing (not "undefined").
  const smudge = floats ? null : { lift: "Lifts paint.", drag: "Drags wet paint.", smudge: isStump(B) ? (combs(B) ? "Combs." : "Softens.") : "Smears.", water: "Lifts paint, lets it run." }[B.carry.k];
  // Paint Load says plainly what it does, and how to load it, in the smudge's place (the oil paint spec).
  const carry = smudge ? (runsOut(B) ? "Runs out of paint, then only smudges. Rub a grey to load." : smudge) + (keepsPaint(B) ? " Keeps paint for the next stroke." : "") : null;
  if (carry) out.push(carry);
  const er = eraseLabel(B); if (er) out.push(er + ".");
  if (B.life.k === "none") out.push("Dries at once.");
  else if (floats) out.push("Floats " + floatSec(B) + " s. Taps drop rings" + (B.carry.k === "drag" || B.carry.k === "smudge" || B.carry.k === "water" ? ", dragging combs them" : "") + ".");
  else if (B.life.k === "dry") out.push("Wet for " + dryingSec(B) + " s" + ((B.life.d.takes | 0) ? ", pen lines plume in it" : "") + ".");
  const hw = handWords(B, 2);
  if (hw !== "steady") out.push(cap1(hw) + ".");
  // At most 260 characters (the app's budget): sentences past the second that don't fit are left out.
  let text = out[0];
  for (let i = 1; i < out.length; i++) { if (i >= 2 && text.length + 1 + out[i].length > 260) break; text += " " + out[i]; }
  return text;
}

// ===================================================================================================================
// Names: adjective-produce from the app's lists (oldmodel.js's freshName); a new brush is then named for what it is, a
// fun word, a describer and its part, "sloppy-inky-drag" (the app's BrushNames.described), by the app's
// names/traits.txt: the copy below, kept the same by BrushNamesChecks. From the brush and its seed, no draw from
// Random, so every roll is as before.
// ===================================================================================================================
const NAMES_TRAITS = "# A new brush's name, said by what it is, deadpan and in Title Case (the owner, 2026-10-29: \"mostly deadpan\"; \"have the\n# brush names hint at what the brush does for sure\"). Every name has its part, the word for what makes it, and most its\n# describer too. About one in five is its three words: a fun word, a describer and the part (\"Sloppy Inky Drag\"). The\n# rest are one of eight forms from [deadpan] (\"Polite Smear\", \"Regrettable Inky Hake\", \"Dusty Aunt Charcoal\", \"Inky\n# Pen Junior\", \"Smear of Mild Regret\", \"Wash for Beginners\", \"Inky Pen Situation\", \"Budget Dusty Charcoal\"). About one\n# in six has a made-up word: the describer's stem or the part with an ending from [deadpan]'s nonsense line\n# (\"Wobblefuzz\", \"Smudgekins\"). A name that won't wrap to two lines of a card (12 letters a line) takes the next form,\n# then its words, then just the describer and part.\n# Each section below is a list of lines: something a brush can have (as brush codes name it), a colon, then its words,\n# short and lowercase, one word each (-y and -ish welcome). A brush takes the first line it has, top to bottom, and one\n# of its words picked by its seed, so two alike brushes still differ. Drying and smudges count when they aren't the\n# style's usual, a paper only with a strong grain. \"any\" is every brush. A line starting with # is a note; a word in\n# square brackets starts a section.\n\n[part]\n# The part that makes it, most telling first: special tips, then smudges, then tips, then the medium.\ntip.stamp: stamp\ntip.spiro: spiro loop\ntip.handhatch: hatch\ntip.hatch: hatch\nmedium.scratch: scraper blade\nmedium.crayon: crayon\ntip.stipple: dots stipple\nmedium.wax: wax candle\nmedium.resist: mask\nmedium.tone: tone\nmedium.screen: print\nlife.float: float\nlife.halo: mold\ncarry.water: water\ncarry.stayin: filler\ncarry.comb: comb\ncarry.stump: stump\ncarry.lift: lift\ncarry.drag: drag\ncarry.smudge: smear smudge\ntip.bristles: bristle\ntip.split: split\ntip.multi: rake\ntip.hake: hake\ntip.chisel: chisel\ntip.flat: nib\ntip.filbert: filbert\nmedium.ink: pen liner\nmedium.wash: wash\nmedium.cover: paint\nmedium.charcoal: charcoal\nmedium.graphite: pencil\nany: brush\n\n[describer]\n# What it does, most telling first: its mischief, its drying and marks, then its paper, then its medium.\nblend.invert: flipped topsy\nblend.behind: tucked shy\nblend.white: chalky pale ghostly\nlife.fade: fading ghostly\nlife.lemon: secret sour\ncarry.pload: oily loaded\ntip.flick: flicky whippy\nmedium.fling: flingy splatty\nmedium.dust: dusty sooty\nmedium.bloom: bloomy blossomy\nmedium.backrun: rippled frilly\nmedium.bleed: bleedy feathery\nlife.drips: drippy dribbly\nlife.runs: runny trickly\nlife.pool: puddly pooling\nlife.sink: silty sandy\nlife.blend: rocky swirly\nlife.wetpaint: wettish dewy\nlife.dry: damp dampish soggy\nmedium.splatter: spattery splotchy\nmedium.impasto: lumpy ridged thick\nmedium.pool: puddly blotchy\ntip.dry: scratchy streaky\npaper.cold press: grainy toothy\npaper.laid: ribbed ribby\npaper.felt: felty fuzzy\npaper.crumpled: crinkly creased\npaper.canvas: woven weavy\npaper.watercolour: cottony\npaper.rough: bumpy lumpy\npaper.unryu: wispy fibrous\npaper.library: bookish\npaper.kraft: brownish papery\npaper.old buff: oldish faded\npaper.blue fibre: fibrous flecked\npaper.fine speck: flecky specked\ntip.wobble: wobbly shaky\ntip.taper: pointy tapered\nmedium.crayon: waxy crayony\nmedium.wax: waxy\nmedium.resist: masky rubbery\nmedium.ink: inky inkish\nmedium.wash: washy watery\nmedium.cover: painty creamy\nmedium.charcoal: sooty powdery chalky\nmedium.graphite: greyish leady\nmedium.tone: dotty\nany: plain simple\n\n[fun]\n# A word for its mood, mostly so two names don't clash: playful, wet, rough, thick, soft, then neat.\nlife.fade: sly impish wistful shy fleeting coy\nlife.lemon: sly sneaky secret coy wily cagey\ntip.flick: giddy cheeky perky zippy frisky jolly\nmedium.fling: giddy zany rowdy wild madcap frisky\ntip.spiro: giddy loopy dizzy whirly jolly zany\ntip.stamp: cheeky perky chirpy bouncy jolly plucky\nlife.halo: creepy eerie spooky weird furtive odd\nblend.invert: sly topsy contrary rogue odd wily\nmedium.wax: sly secret sneaky hidden coy cagey\nmedium.resist: careful patient polite tidy shy prim\nlife.float: dreamy drifty floaty calm serene lazy\nlife.wetpaint: sloppy loose juicy lush gooey slick\nlife.dry: loose dreamy lazy patient slow mellow\nmedium.bloom: dreamy loose airy lush tender hazy\nmedium.wash: loose mellow breezy easy calm fluid gentle wistful lazy carefree\nlife.drips: sloppy messy leaky soggy clumsy gooey\nlife.runs: sloppy loose leaky restless runaway wayward\nmedium.dust: gruff rugged grubby rumpled crusty grimy\ntip.dry: scratchy gruff wiry gritty raspy rough thirsty parched crusty grainy\ntip.bristles: shaggy scruffy unkempt tousled wiry wild\nmedium.charcoal: gruff smoky grubby moody brooding rugged grumpy stormy dusky gloomy\nmedium.scratch: gruff edgy feisty prickly spiky sharp\nmedium.crayon: chunky sunny merry cheery bold plucky\nmedium.impasto: hearty burly chunky stout beefy robust\nmedium.cover: hearty bold stout sturdy brash plush jolly sunny merry earnest\ncarry.drag: sloppy bold brash pushy burly hasty\ncarry.pload: hearty plump greasy rich lavish slick\ncarry.smudge: hazy gentle dreamy soft fuzzy woolly\ncarry.stump: hazy gentle velvet soft mellow tender\ncarry.lift: gentle airy light polite careful mild\ntip.hatch: tidy prim patient orderly fussy steady\ntip.handhatch: tidy patient fussy diligent busy steady\ntip.stipple: patient fussy dotty finicky busy steady\ntip.flat: prim dapper formal stately elegant poised\ntip.chisel: patient tidy crisp sharp stately keen bold brisk upright proud\nmedium.graphite: tidy humble quiet modest sober plain shy pensive bookish studious\nmedium.ink: tidy prim neat crisp keen proper dainty earnest snappy perky\nany: brisk spry jaunty nimble bright swift\n\n[deadpan]\n# Not keyed by what a brush has: the deadpan forms' words, a line each. manner: \"Polite Smear\"; relative: \"Dusty Aunt\n# Charcoal\"; edition: \"Inky Pen Junior\"; mild and concern: \"Smear of Mild Regret\"; nonsense: a made-up word's ending\n# (\"wobblefuzz\", \"smudgekins\").\nmanner: polite nervous regrettable apologetic reluctant sensible earnest suspicious hopeful modest tired puzzled sincere decent humble cautious sheepish dutiful unbothered optimistic sleepy worried confident tentative bashful\nrelative: uncle aunt cousin nephew niece neighbour grandpa grandma landlord roommate\nedition: deluxe classic junior lite original\nmild: mild some minor modest slight faint passing\nconcern: regret concern doubt worry interest suspicion unease bother\nnonsense: fuzz monger whistle wump kins wick doodle ery\n\n# ---- A new notebook's title ----\n# Not a brush's: [title] names new notebooks, deadpan, in fourteen forms: \"Several Opinions About Soup\", \"A Brief Inventory\n# of Lint\", \"Notes Toward a Better Spoon\", \"The Pamphlet of Mild Concerns\", \"Doodles for a Goose\", \"Mistakes Volume\n# Three\", \"Probably Fine\", \"The Lukewarm Pamphlet\", \"Spoons I Have Known\", \"In Defence of Fog\", \"Advice from a Puffin\",\n# \"Minutes of the Pigeon Committee\", \"Field Notes on Scones\" and \"Letters to a Walrus\". About one in six has a made-up word instead, a lear word with a nonsense ending:\n# \"The Flimwhistle Papers\", \"Doodles for a Pobblewick\", \"Wobblefuzz and Other Concerns\". A title too long for a shelf\n# tile takes a shorter form. Only new notebooks: a name already given never changes.\n\n[title]\n# Its words, a line each. things: plural, or a heap of; thing and creature follow \"a\".\nthings: lint spoons crumbs socks puddles pigeons biscuits receipts buttons noodles pebbles mittens teaspoons potatoes soup toast gravel fog weather drizzle doorknobs envelopes pickles turnips sandwiches gulls kettles hats lunches parsnips napkins cushions clouds raisins sweaters crackers onions marbles lampshades staplers shoelaces paperclips thimbles scones hedges icebergs mussels rhubarb porridge\nthing: spoon kettle sandwich chair bench sock hat potato toaster mailbox teapot biscuit puddle tuesday doorknob lunchbox cardigan mitten bucket ladder pencil sofa parsnip thimble teacup napkin scone spatula cushion doormat saucepan stapler shoelace\ncreature: goose pigeon moth badger hedgehog walrus heron sheep duck newt snail puffin moose crab gull capelin lobster beetle pony hen squid caribou toad ferret llama pelican plover vole wombat mole lemming seal trout cod raven robin\nadj: mild minor modest lukewarm damp polite vague reasonable unofficial ordinary sensible tepid small quiet humble careful tentative soggy beige decent brief tidy lumpy plain overdue borrowed ongoing sincere\nbook: pamphlet ledger logbook binder handbook almanac folder report bulletin register leaflet dossier\nverdict: fine okay alright harmless edible waterproof sturdy accurate legal adequate enough upright dry fireproof finished correct square\nnumber: two three four five six seven eight nine eleven twelve\nlear: flim wobble quib splotch bumble pobble jumble quangle fizzle plonk gribble dimble wimble crumple flonk\n";
// Its sections (BrushNames.traits): "[fun]" starts one (lines before any go in part); every other line is a key, a
// colon and its words, in the file's order.
function traitSections(text) {
  const out = new Map(); let sec = "part";
  for (const raw of text.replace(/\uFEFF/g, "").split("\n")) {
    const line = raw.replace(/\t/g, " ").trim();
    if (!line || line.startsWith("#")) continue;
    if (/^\[[a-z]+\]$/.test(line)) { sec = line.slice(1, -1); continue; }
    const cut = line.indexOf(":");
    if (cut <= 0) continue;
    const key = line.slice(0, cut).trim().replace(/\s+/g, " "), words = [];
    for (const w of line.slice(cut + 1).trim().split(/\s+/)) if (/^[a-z]+$/.test(w) && !words.includes(w)) words.push(w);
    if (!out.has(sec)) out.set(sec, new Map());
    const m = out.get(sec);
    if (key && words.length && !m.has(key)) m.set(key, words);
  }
  for (const [k, m] of [...out]) if (!m.size) out.delete(k);
  return out;
}
const TRAITS = traitSections(NAMES_TRAITS), TRAIT_WORDS = new Set([...TRAITS.values()].flatMap(m => [...m.values()].flat()));
// TRAIT_NAMES.on false: names stay adjective-produce (the checks, to see every roll is as before).
const TRAIT_NAMES = { on: true }, TRAIT_SALT = 0x7a175, KIN_SALT = 0x4b1e5, STRONG_TOOTH = .6;
const nameFamily = name => name.slice(Math.max(name.lastIndexOf("-"), name.lastIndexOf(" ")) + 1).trim().toLowerCase();
// The choice a style rolls most (ModularWords.usual, Anything goes' too).
function nameUsual(style, part) {
  const st = MSTYLES[style] || MSTYLES[WILDCARD];
  if (!st.wild) return usual(style, part);
  let best = null, most = 0;
  for (const [k, w] of Object.entries(WILD_PARTS[part])) if (w > most) { most = w; best = k; }
  return best;
}
// The marks a name can say and from what strength, "This brush"'s (BrushNames.MARKS).
const TRAIT_MARKS = [["medium", "bloom", .3], ["medium", "backrun", .3], ["medium", "bleed", .35], ["medium", "splatter", .3], ["medium", "impasto", .3],
  ["medium", "pool", .35], ["medium", "fling", .05], ["tip", "flick", .05], ["tip", "dry", .3], ["tip", "wobble", .25]];
// What a name can say about B, as traits.txt keys it (BrushNames.traitKeys): "any"; its medium and any tip but Round;
// a smudge or drying only when it isn't the style's usual, a paper only with a strong grain; a stump or a comb; Resist's
// wax, Crayon's scratch; Blend; gravity's dials and dust past .15; a taper; the marks.
function traitKeys(B) {
  const style = B.style || WILDCARD, keys = new Set(["any"]), T = B.tip, M = B.medium, P = B.paper, L = B.life;
  if (T.k !== "round") keys.add("tip." + T.k);
  if ((T.d.taperIn ?? 0) + (T.d.taperOut ?? 0) >= .9) keys.add("tip.taper");
  keys.add("medium." + M.k);
  if (M.k === "resist" && (M.d.erase ?? 1) < .5) keys.add("medium.wax");
  if (M.k === "crayon" && (M.d.scratch | 0) === 1) keys.add("medium.scratch");
  if (M.k !== "resist") { const bl = M.d.blend | 0; if (bl > 0) keys.add("blend." + ["darken", "white", "behind", "invert"][bl]); }
  if ((M.d.dust ?? 0) > .15) keys.add("medium.dust");
  if ((M.d.screen | 0) > 0) keys.add("medium.screen");
  for (const [part, d, at] of TRAIT_MARKS) if ((B[part].d[d] ?? 0) >= at) keys.add(part + "." + d);
  if (isStump(B)) keys.add(combs(B) ? "carry.comb" : "carry.stump");
  else if (B.carry.k !== "off" && B.carry.k !== nameUsual(style, "carry")) keys.add("carry." + B.carry.k);
  if (runsOut(B)) keys.add("carry.pload");
  if (P.k !== "plain" && P.k !== nameUsual(style, "paper") && (P.d.tooth ?? 0) >= STRONG_TOOTH) keys.add("paper." + P.k);
  if (L.k !== nameUsual(style, "life")) keys.add("life." + L.k);
  for (const k of ["runs", "drips", "pool", "sink", "blend"]) if ((L.d[k] ?? 0) > .15) keys.add("life." + k);
  return keys;
}
// Its name's words (BrushNames.words): from each section the first line B has, a word picked by the seed; the part
// first, then the describer, then the fun word, no word twice; said fun, describer, part; maybe one made up.
function nameWords(B) { const w = plainNameWords(B); return w.length === 3 ? nonsenseName(w, B.seed >>> 0) : w; }
function plainNameWords(B) {
  const has = traitKeys(B), part = namePick("part", has, B.seed, 0), describer = namePick("describer", has, B.seed, 1, part), fun = namePick("fun", has, B.seed, 2, part, describer);
  return [fun, describer, part].filter(w => w !== null);
}
// From a section, the first line B has, one of its words by the seed (hash n), the next where it echoes one of not (BrushNames.pick).
// Two words echo when they start alike, their first four letters or all of the shorter (BrushNames.echoes).
const nameEchoes = (a, b) => a !== null && b !== null && a.slice(0, Math.min(4, a.length, b.length)) === b.slice(0, Math.min(4, a.length, b.length));
function namePick(sec, has, seed, n, ...not) { const all = namePicks(sec, has, seed, n, ...not); return all.length ? all[0] : null; }
// namePick's word and the rest of its line after it, in the seed's order (BrushNames.picks).
function namePicks(sec, has, seed, n, ...not) {
  const m = TRAITS.get(sec), out = [];
  if (!m) return out;
  for (const [k, line] of m) {
    if (!has.has(k)) continue;
    const at = (hash01(((seed >>> 0) ^ TRAIT_SALT) >>> 0, n) * line.length) | 0;
    for (let i = 0; i < line.length; i++) { const w = line[(at + i) % line.length]; if (!not.some(t => nameEchoes(w, t))) out.push(w); }
    if (out.length) return out;
  }
  return out;
}
// B's name (BrushNames.name), deadpan, Title Case: about one in five its words, the rest a deadpan form (the next where
// one won't wrap to two lines of a card), then its words, its plain words, its describer and part; null with none.
const PLAIN = .2, capWord = w => w[0].toUpperCase() + w.slice(1);
function brushNameOf(B) {
  const w = nameWords(B), plain = plainNameWords(B);
  if (!w.length) return null;
  const s = ((B.seed >>> 0) ^ TRAIT_SALT) >>> 0, tries = w.length === 3 && hash01(s, 7) >= PLAIN ? deadpanForms(w, plain, s) : [];
  tries.push(nameTitled(w), nameTitled(plain));
  for (const name of tries) if (name.length <= NAME_MAX && nameTwoLines(name)) return name;
  return nameTitled(plain.length < 3 ? plain : plain.slice(1));
}
// Words with spaces, each capitalized: names are Title Case (BrushNames.titled).
const nameTitled = ws => ws.map(capWord).join(" ");
// The deadpan forms (BrushNames.deadpan), from [deadpan], the seed's first, then the next ones round: "Polite Smear",
// "Regrettable Inky Hake", "Dusty Aunt Charcoal", "Inky Pen Junior", "Smear of Mild Regret", "Wash for Beginners", "Inky
// Pen Situation", "Budget Dusty Charcoal"; each says the part; a made-up describer leaves out the forms without it; the
// two short forms only when picked.
const nameWord = (line, s, n) => line ? line[(hash01(s, n) * line.length) | 0] : null;
const nameJoin = (...ws) => ws.some(w => w === null) ? null : ws.map(w => w.startsWith(" ") ? w : capWord(w)).join("");
function deadpanForms(w, plain, s) {
  const d = TRAITS.get("deadpan"), out = [];
  if (!d) return out;
  const describer = w[1], part = w[2], manner = nameWord(d.get("manner"), s, 13), madeUp = describer !== plain[1];
  const forms = [madeUp ? null : nameJoin(manner, " ", part), nameJoin(manner, " ", describer, " ", part),
    nameJoin(describer, " ", nameWord(d.get("relative"), s, 14), " ", part), nameJoin(describer, " ", part, " ", nameWord(d.get("edition"), s, 17)),
    madeUp ? null : nameJoin(part, " of ", nameWord(d.get("mild"), s, 15), " ", nameWord(d.get("concern"), s, 16)),
    madeUp ? null : nameJoin(part, " for Beginners"), nameJoin(describer, " ", part, " Situation"), nameJoin("Budget ", describer, " ", part)];
  const at = (hash01(s, 12) * forms.length) | 0;
  for (let i = 0; i < forms.length; i++) { const k = (at + i) % forms.length; if (forms[k] !== null && (i === 0 || k !== 0 && k !== 5)) out.push(forms[k]); }
  return out;
}
// Whether a name wraps to two lines of LINE_LETTERS as a card wraps it (BrushNames.twoLines).
const LINE_LETTERS = 12;
function nameTwoLines(name) {
  const pieces = [];
  for (let word of name.trim().split(/ +/)) {
    while (word.length > LINE_LETTERS) {
      let cut = -1;
      for (let i = word.indexOf("-", 1); i > 0 && i < word.length - 1; i = word.indexOf("-", i + 1)) if (cut < 0 || i + 1 <= LINE_LETTERS) cut = i;
      if (cut < 0) break;
      pieces.push(word.slice(0, cut + 1)); word = word.slice(cut + 1);
    }
    pieces.push(word);
  }
  let lines = 0, line = -1;
  for (const p of pieces) {
    if (p.length > LINE_LETTERS) return false;
    if (line >= 0 && line + 1 + p.length <= LINE_LETTERS) { line += 1 + p.length; continue; }
    lines++; line = p.length;
  }
  return lines <= 2;
}
// Made-up words (BrushNames.nonsense), about one name in six: a -y or -ish describer's stem, else the part, with an
// ending from [deadpan]'s nonsense line ("wobblefuzz", "smudgekins"); none past 12 letters, never the word twice.
const NONSENSE = 1 / 6, NONSENSE_MOST = 12, isVowel = c => "aeiou".includes(c);
function nameStem(w) {
  const s = w.endsWith("ish") ? w.slice(0, -3) : w.endsWith("y") ? w.slice(0, -1) : null;
  if (s === null || s.length < 3) return null;
  const n = s.length, last = s[n - 1], before = s[n - 2];
  if (last === before && "bdgmnpt".includes(last)) return s.slice(0, -1);
  if (last === "l" && !isVowel(before) && before !== "l") return s.endsWith("stl") ? s.slice(0, -1) : s + "e";
  if (last === "v" || last === "k" && isVowel(before) && n >= 3 && !isVowel(s[n - 3])) return s + "e";
  return s;
}
function nameSuffixed(w, suffix) {
  const soft = isVowel(suffix[0]) || suffix[0] === "y", n = w.length;
  if (soft && w.endsWith("e")) return w.slice(0, -1) + suffix;
  if (soft && n <= 4 && n >= 3 && !isVowel(w[n - 1]) && !"wxy".includes(w[n - 1]) && isVowel(w[n - 2]) && !isVowel(w[n - 3])) return w + w[n - 1] + suffix;
  return w + suffix;
}
function nonsenseName(words, seed) {
  const s = ((seed | 0) ^ TRAIT_SALT) >>> 0, d = TRAITS.get("deadpan"), ends = d ? d.get("nonsense") : null;
  if (!ends || hash01(s, 3) >= NONSENSE) return words;
  const [fun, describer, part] = words, stem = nameStem(describer), base = stem !== null ? stem : part, at = (hash01(s, 6) * ends.length) | 0;
  for (let i = 0; i < ends.length; i++) {
    const end = ends[(at + i) % ends.length], w = nameSuffixed(base, end);
    if (w.length > NONSENSE_MOST || nameEchoes(base, end)) continue;
    return stem !== null ? [fun, w, part] : [fun, describer, w];
  }
  return words;
}
// A new brush's name said by what it is; as it is with no family part or no words.
function describedName(B, name) {
  if (!TRAIT_NAMES.on || !nameFamily(name)) return name;
  const n = brushNameOf(B);
  return n === null ? name : n;
}
// A variation's name (BrushNames.kin): kinName draws first, so every variation rolls as before; a capitalized name led by
// a word of [deadpan], [fun], [describer] or [part] keeps the rest under another word of that line; a fun word's name keeps the
// rest (its describer and part) under another word of the fun line, both from the variation's seed; an older
// trait-produce name ("drip-plum") keeps its first word under another produce; any other as kinName says.
function kinOther(list, not, seed) {
  let i = (hash01(((seed >>> 0) ^ KIN_SALT) >>> 0, 0) * list.length) | 0;
  if (list[i] === not) i = (i + 1) % list.length;
  return list[i];
}
function kinOf(name, seed) {
  const kin = kinName(name), f = nameFamily(name);
  if (!f) return kin;
  const words = name.trim().toLowerCase().split(/[- ]+/), t = name.trim();
  // A deadpan name keeps all but its first word, under another of the same line ("Polite Smear", "Nervous Smear").
  if (words.length >= 2 && t[0] !== t[0].toLowerCase())
    for (const sec of ["deadpan", "fun", "describer", "part"]) if (TRAITS.has(sec))
      for (const line of TRAITS.get(sec).values()) if (line.includes(words[0])) return capWord(kinOther(line, words[0], seed)) + t.slice(words[0].length);
  if (words.length >= 2 && TRAITS.has("fun") && !NAMES_PRODUCE.includes(f))
    for (const line of TRAITS.get("fun").values()) if (line.includes(words[0])) return kinOther(line, words[0], seed) + name.trim().toLowerCase().slice(words[0].length);
  if (words.length === 2 && words[0] && !NAMES_ADJ.includes(words[0]) && NAMES_PRODUCE.includes(f)) return words[0] + "-" + kinOther(NAMES_PRODUCE, f, seed);
  return kin;
}

// ===================================================================================================================
// Share codes. New: name.M<style><tip>_<medium>_<carry>_<paper>_<life>_<hand>.<seed>: each part its choice as one
// base-36 digit, then its choice's dials in order (a choice dial one digit, a dial two base-36 digits over its range).
// Dials added later go on the end; a code that stops early reads them as defaults. Old app codes (K = I or P) and the
// prototypes' I/P tails, ".x" pen suffix and B codes still load and draw as before.
// ===================================================================================================================
const M_STEPS = 1295;
const two36 = n => (n < 36 ? "0" : "") + n.toString(36);
function writePart(part, P) {
  let s = choiceIndex(part, P.k).toString(36), keys = choiceOf(part, P.k).dials, end = keys.length;
  while (end > 0 && ADDED.has(part + "." + keys[end - 1]) && P.d[keys[end - 1]] === dialDefault(part, P.k, keys[end - 1])) end--;
  for (const key of keys.slice(0, end)) {
    const sp = DIALS[part][key], v = P.d[key];
    if (sp.choices) { s += (v | 0).toString(36); continue; }
    const [lo, hi] = [sp.min, sp.max];
    s += two36(Math.round((clamp(v, lo, hi) - lo) / (hi - lo) * M_STEPS));
  }
  return s;
}
function readPart(part, seg) {
  if (!seg) return null;
  const ci = parseInt(seg[0], 36);
  if (!(ci >= 0 && ci < CHOICES[part].length)) return null;
  const c = CHOICES[part][ci], P = freshPart(part, c.k);
  let at = 1;
  for (const key of c.dials) {
    const sp = DIALS[part][key];
    if (sp.choices) { if (at >= seg.length) break; const o = parseInt(seg[at++], 36); if (!(o >= 0 && o < sp.choices.length)) return null; P.d[key] = o; continue; }
    if (at + 2 > seg.length) break;
    const step = parseInt(seg.slice(at, at + 2), 36); at += 2;
    if (!(step >= 0 && step <= M_STEPS)) return null;
    P.d[key] = sp.min + step / M_STEPS * (sp.max - sp.min);
  }
  return P;
}
const codeSlug = n => String(n).toLowerCase().replace(/[^a-z]+/g, "-").replace(/^-|-$/g, "") || "brush";
// A Stamp's nib (newmods.js; the app's ModularCode): its id in base 36 after the six, only for a Stamp with one, so
// every other code is as before.
const nibSeg = B => B.tip && B.tip.k === "stamp" && B.nib ? "_" + (B.nib >>> 0).toString(36) : "";
function encodeM(B) {
  return codeSlug(B.name) + ".M" + Math.max(0, STYLE_NAMES.indexOf(B.style)).toString(36) + PARTS.map(p => writePart(p, B[p])).join("_") + nibSeg(B) + "." + (B.seed >>> 0).toString(36);
}
// What makes two brushes draw alike: the parts, the seed and a Stamp's nib, not the name or style.
const brushKey = B => PARTS.map(p => writePart(p, B[p])).join("_") + nibSeg(B) + "." + (B.seed >>> 0).toString(36);
// Any code: M, B, I/P (with the prototypes' tails and ".x"). Returns { b, from } or null.
function readAnyCode(text) {
  if (text == null) return null;
  let c = javaTrim(String(text)).toLowerCase();
  const dot = c.indexOf(".") + 1;
  if (dot > 0 && dot < c.length) c = c.slice(0, dot) + c[dot].toUpperCase() + c.slice(dot + 1);
  let m = /^([a-z]+(?:-[a-z]+)*)\.M([0-9a-z])([0-9a-z_]+)\.([0-9a-z]{1,7})$/.exec(c);
  if (m) {
    const style = parseInt(m[2], 36), seed = parseInt(m[4], 36), segs = m[3].split("_");
    if (style >= STYLE_NAMES.length || seed > 0xffffffff || segs.length < PARTS.length) return null;
    const B = { name: m[1], seed, style: STYLE_NAMES[style] };
    for (let i = 0; i < PARTS.length; i++) { const P = readPart(PARTS[i], segs[i]); if (!P) return null; B[PARTS[i]] = P; }
    // A Stamp's nib: a seventh segment (one that doesn't read is no nib).
    if (B.tip.k === "stamp" && segs.length > PARTS.length && /^[0-9a-z]{1,7}$/.test(segs[PARTS.length])) { const id = parseInt(segs[PARTS.length], 36); if (id <= 0xffffffff) B.nib = id; }
    return { b: normBrush(B), from: "M" };
  }
  m = /^([a-z]+(?:-[a-z]+)*)\.B([0-9a-z])([0-9a-z]+)\.([0-9a-z]{1,7})$/.exec(c);
  if (m) { const b = readB(m); return b ? { b, from: "B" } : null; }
  // ".x": the lab v2 mockup's pen-input suffix.
  const x = /^(.*)\.x([0-2])([01])([0-9a-z]{2})([0-9a-z]{2})$/.exec(c);
  if (x) { const r = readAnyCode(x[1]); if (!r || r.from === "M" || r.from === "B") return null; applyPenSuffix(r.b, r.old, x); r.from += ".x"; return r; }
  m = /^([a-z]+(?:-[a-z]+)*)\.([IP])(\d)([0-9a-z]+)\.([0-9a-z]{1,7})$/.exec(c);
  if (!m) return null;
  const old = decode(c);
  if (old) return { b: fromOld(old), old, from: m[2] };
  return readTailed(m);
}
// I/P codes with the prototypes' appended dials (brush-experiments: ink SPREAD TILT FAST, paint COVER IMPASTO HEAD TILT
// FAST, then LIFE and WET WINDOW; marble-and-bristle: ink SWARM BRISTLES COHESION SPREAD; living-ink: 9 digits, read as still).
function readTailed(m) {
  const kind = m[2] === "I" ? "ink" : "paint", baseLen = kind === "ink" ? 24 : 20, digits = m[4];
  if (digits.length <= baseLen) return null;
  let style = +m[3];
  const hake = kind === "paint" && style === 7;
  const old = decode(m[1] + "." + m[2] + (hake ? 6 : style) + digits.slice(0, baseLen) + "." + m[5]);
  if (!old) return null;
  const tail = digits.slice(baseLen), d2 = i => parseInt(tail.slice(i, i + 2), 36) / 1295;
  const B = fromOld(old);
  if (hake) { B.style = "Hake"; }
  if (kind === "ink") {
    if (tail.length === 5 || tail.length === 8) {
      const spread = d2(0), tilt = d2(2), fast = tail[4] === "1";
      if (spread > 0) { B.medium = freshPart("medium", "charcoal"); B.medium.d.spread = spread; B.medium.d.hold = .5; }
      if (tilt > 0) { B.hand.d.ts = tilt * .6; B.hand.d.tw = tilt; B.hand.k = "custom"; }
      if (fast) B.life = freshPart("life", "none");
      if (tail.length === 8) { const L = ["none", "wet", "dry"][+tail[5]] || "wet"; B.life = freshPart("life", L); if (L === "wet") B.life.d.window = 10 * d2(6); }
    } else if (tail.length === 7) {
      if (tail[0] === "1") { const d = B.tip.d; B.tip = freshPart("tip", "bristles"); Object.assign(B.tip.d, { hairs: 6 + 58 * d2(1), cohesion: d2(3), splay: d2(5), dryness: old.dry }); }
    } else if (tail.length !== 9) return null;
  } else {
    if (tail.length !== 8 && tail.length !== 11) return null;
    const cover = d2(0), impasto = d2(2), head = tail[4] === "1", tilt = d2(5), fast = tail[7] === "1";
    if (cover > 0) { const keep = B.medium.d; B.medium = freshPart("medium", "cover"); B.medium.d.cover = cover; B.medium.d.impasto = impasto; }
    if (head || hake) { const d = B.tip.d; B.tip = switchChoice("tip", B.tip, "hake"); B.tip.d.dry = d.dry; }
    if (tilt > 0) { B.hand.d.ts = tilt; B.hand.k = "custom"; }
    if (fast) B.life = freshPart("life", "none");
    if (tail.length === 11) { const L = ["none", "wet", "dry"][+tail[8]] || "wet"; B.life = freshPart("life", L); if (L === "wet") B.life.d.window = 10 * d2(9); }
  }
  return { b: normBrush(B), old, from: m[2] + "+" };
}
function applyPenSuffix(B, old, x) {
  const to = ["size", "dark", "both"][+x[2]], tilt = parseInt(x[4], 36) / 1295, sp = parseInt(x[5], 36) / 1295 * 2 - 1, p = old ? old.pressure : B.hand.d.ps;
  B.hand.d.ps = to === "dark" ? 0 : p; B.hand.d.pd = to === "size" ? 0 : p;
  if (tilt > 0) { if (x[3] === "1") B.hand.d.tw = tilt; else B.hand.d.ts = tilt; }
  if (old && old.kind === "paint" && Math.abs(sp) > .001) { B.hand.d.ss = Math.abs(sp); B.hand.d.dir = sp > 0 ? 1 : 0; }
  B.hand.k = matchHand(B.hand); normBrush(B);
}
// An app brush (I or P, as oldmodel.js's decode gives it) as parts. Life is Drying: the app re-tapers and settles at lift.
const OLD_STYLE = { "Wild": WILDCARD, "Wildcard": WILDCARD };
function fromOld(o) {
  const B = { name: o.name, seed: o.seed >>> 0, style: OLD_STYLE[o.style] || (MSTYLES[o.style] ? o.style : WILDCARD) };
  if (o.kind === "ink") {
    B.tip = { k: o.nib, d: { aspect: o.aspect, angle: o.angle, weight: 1, taperIn: o.taperIn, taperOut: o.taperOut, dry: o.dry, wobble: o.wobble, soft: .3 } };
    B.medium = { k: "ink", d: { wet: 0, line: 1, pool: o.pool, splatter: o.splatter, bleed: 0, bloom: 0, lines: 0, offset: 0, chaos: 0 } };
    B.paper = { k: o.texture, d: { tooth: o.tooth } };
  } else {
    B.tip = { k: "round", d: { weight: 1, taperIn: 0, taperOut: 0, dry: o.streaks, wobble: 0, soft: o.soft } };
    B.medium = { k: "wash", d: { wet: o.mode === "wet" ? 1 : 0, pool: o.hollow, bleed: o.feather, bloom: o.bloom, backrun: o.backrun, line: 0 } };
    B.paper = { k: o.texture, d: { tooth: o.gran } };
  }
  B.carry = freshPart("carry", "off");
  B.life = { k: "dry", d: { drying: o.drying || 0, settle: 0, takes: 0, plume: .55 } };
  B.hand = freshPart("hand", "custom");
  B.hand.d.ps = o.pressure; B.hand.d.ss = Math.abs(o.speed || 0); B.hand.d.dir = (o.speed > 0 || (o.speed === 0 && o.thicker)) ? 1 : 0;
  B.hand.k = matchHand(B.hand);
  return normBrush(B);
}
// pen-paint-blend's B codes (and ink-and-water's 5-digit plume tail after them).
const B_TEX = ["plain", "cold press", "laid", "felt", "crumpled", "canvas"], R01 = [0, 1];
const BLEND_ORDER = [["nib", ["round", "chisel", "flat"]], ["mode", ["wet", "flat"]], ["texture", B_TEX], ["wet", R01], ["line", R01],
  ["aspect", [.14, .55]], ["angle", [20, 70]], ["taperIn", R01], ["taperOut", R01], ["pressure", R01], ["speed", [-1, 1]],
  ["grain", R01], ["bristle", R01], ["wobble", R01], ["pool", R01], ["splatter", R01], ["soft", R01], ["bleed", R01],
  ["bloom", R01], ["backrun", R01], ["drying", R01]];
const BLEND_TAIL = [["lines", PLACEMENTS], ["count", [1, 5]], ["spread", R01], ["breathe", R01], ["offset", R01], ["chaos", R01]];
const PLUME_TAIL = [["plume", R01], ["pstyle", ["drift", "vein"]], ["settle", R01]];
const BLEND_STYLES = ["Fineliner", "Brush pen", "Dip pen", "Marker", "Pencil", "Charcoal", "Pen and wash", "Wet line", "Ink wash", "Watercolor", "Gouache", "Oils", "Pastels", "Dry brush", "Wild"];
function readSpec(digits, order, into) {
  let at = 0;
  for (const [k, spec] of order) {
    if (typeof spec[0] === "string") { if (at >= digits.length) return false; const o = digits.charCodeAt(at++) - 48; if (o < 0 || o >= spec.length) return false; into[k] = spec[o]; continue; }
    if (at + 2 > digits.length) return false;
    const step = parseInt(digits.slice(at, at + 2), 36); at += 2;
    if (k === "speed" && (step === 647 || step === 648)) { into.speed = 0; into.thicker = step === 648; continue; }
    into[k] = spec[0] + step / 1295 * (spec[1] - spec[0]);
  }
  return at === digits.length;
}
function readB(m) {
  const seed = parseInt(m[4], 36), style = parseInt(m[2], 36), digits = m[3];
  if (style >= BLEND_STYLES.length || seed > 0xffffffff) return null;
  const o = { lines: "centre", count: 3, spread: .6, breathe: .8, offset: 0, chaos: 0, plume: 0, pstyle: "drift", settle: 0 };
  if (digits.length < 39 || !readSpec(digits.slice(0, 39), BLEND_ORDER, o)) return null;
  const tail = digits.slice(39);
  const blendLen = { 0: 0, 5: 0, 7: 7, 11: 11, 12: 7, 16: 11 }[tail.length];
  if (blendLen === undefined) return null;
  if (blendLen && !readSpec(tail.slice(0, blendLen), BLEND_TAIL.slice(0, blendLen === 7 ? 4 : 6), o)) return null;
  if (tail.length - blendLen === 5 && !readSpec(tail.slice(blendLen), PLUME_TAIL, o)) return null;
  const sty = BLEND_STYLES[style], B = { name: m[1], seed, style: sty === "Wild" ? WILDCARD : sty === "Pen and wash" || sty === "Wet line" ? "Ink wash" : sty };
  const tipD = { aspect: o.aspect, angle: o.angle, weight: 1, taperIn: o.taperIn, taperOut: o.taperOut, dry: o.bristle, wobble: o.wobble, soft: o.soft };
  if (o.lines === "split") B.tip = { k: "split", d: Object.assign(tipD, { gap: o.spread }) };
  else if (o.lines === "spread") B.tip = { k: "multi", d: Object.assign(tipD, { count: o.count, reach: o.spread }) };
  else B.tip = { k: o.nib, d: tipD };
  const paint = o.wet >= .999 && o.line <= .001 && o.lines === "centre" && !o.offset && !o.chaos;
  if (paint) { B.tip.k = "round"; B.medium = { k: "wash", d: { wet: o.mode === "wet" ? 1 : 0, pool: o.pool, bleed: o.bleed, bloom: o.bloom, backrun: o.backrun, line: 0 } }; }
  else B.medium = { k: "ink", d: { wet: o.wet, line: o.line, pool: o.pool, splatter: o.splatter, bleed: o.bleed, bloom: o.bloom, lines: Math.max(0, ["centre", "outline", "squiggle"].indexOf(o.lines)), offset: o.offset, chaos: o.chaos } };
  B.paper = { k: o.texture, d: { tooth: o.grain } };
  B.carry = freshPart("carry", "off");
  B.life = { k: "dry", d: { drying: o.drying, settle: o.settle, takes: o.plume > 0 ? (o.pstyle === "vein" ? 2 : 1) : 0, plume: o.plume || .55 } };
  B.hand = freshPart("hand", "custom");
  Object.assign(B.hand.d, { ps: o.pressure, ss: Math.abs(o.speed), dir: (o.speed > 0 || (o.speed === 0 && o.thicker)) ? 1 : 0, pw: o.lines === "split" || o.lines === "spread" ? o.breathe : 0 });
  B.hand.k = matchHand(B.hand);
  return normBrush(B);
}

// ---------- the built-ins: the app's four tools, as old codes, so they draw exactly as in the app today ----------
const BUILTIN_CODES = [
  ["Ink", "Fineliner", "steady round line"], ["Fude", "Brush pen", "swells and tapers"], ["Wash", "Gouache", "flat, even tone"], ["Watercolor", "Watercolor", "soft blooms"]];
function builtins() {
  return BUILT.map((o, i) => { const b = fromOld(o); b.name = o.name; b.builtin = true; b.blurb = BUILTIN_CODES[i][2]; return b; });
}

;
// ===================================================================================================================
// Checks, in the page (#selftest) and in Node: old codes load and draw as before; codes round-trip; every part's every
// choice draws something.
// ===================================================================================================================
// The app's own test codes (BrushCodeChecks.java) and one per paint style.
const LEGACY_CODES = ["dapper-fig.I610gavij3f7q16o0000006m00.e1", "bobson-dugnutt.I610zkyum2fbzsge00ej5k00e7.1", "x.I613zkyum2fbzsge00ej5k00e7.1",
  "mellow-plum.P600xjf400c4jzxh00aqhu.hd", "bouncy-moose.P610hh46000000000000wp.25"];
// The old engine (port.js, unchanged) and the new one, the same stroke on the same paper; differing pixels at 16 greys.
function legacyDiff(code, W = 420, H = 300) {
  const r = readAnyCode(code), old = decode(code);
  if (!r || !old) return { code, ok: false, why: "did not read" };
  const ink = old.kind === "ink", size = ink ? 8 : 120, pad = ink ? 22 : 70;
  const ps = surface(W, H, 7), pts = loopPoints(W, H, pad);
  lay(ps, old, pts, size, ink ? INK_DARK[0] : PAINT_DARK[0]);
  const ms = modSurface(W, H, 7);
  // An old paint (a wash) at the darkness that lays the old black's glaze since washes reach black (the app's ModularSizes.sinceBlack).
  layStroke(ms, r.b, pts, size, r.b.medium.k === "wash" ? GLAZE / WASH_BLACK : 1, null);
  let diff = 0, marked = 0;
  for (let i = 0; i < W * H; i++) {
    const a = Math.round(PAPER * (1 - ps.wash[i]) * (1 - ps.ink[i]) / 17) * 17, b = Math.round(ms.page[i] / 17) * 17;
    if (a !== b) diff++; if (a !== 238 + 17 * 0 && a < 238) marked++;
  }
  return { code, ok: diff === 0 && marked > 200, diff, marked, from: r.from };
}
function roundTrips(n = 300) {
  let bad = [];
  for (let i = 0; i < n; i++) {
    const style = STYLE_NAMES[i % STYLE_NAMES.length], B = rollStyle(style);
    const code = encodeM(B), back = readAnyCode(code);
    if (!back || encodeM(back.b) !== code) { bad.push(code); continue; }
    for (const p of PARTS) for (const [k, v] of Object.entries(B[p].d)) {
      const s = DIALS[p][k], tol = s.choices ? 0 : (s.max - s.min) / M_STEPS / 2 + 1e-9;
      if (Math.abs(back.b[p].d[k] - v) > tol && !(s.int)) bad.push(code + " " + p + "." + k);
    }
  }
  return bad;
}
// Codes from the prototypes: they load, into the parts they describe. B codes are written here as pen-paint-blend wrote them.
function writeSpec(o, order) {
  let out = "";
  for (const [k, spec] of order) {
    if (typeof spec[0] === "string") { out += Math.max(0, spec.indexOf(o[k])); continue; }
    out += two36(Math.round((clamp(o[k], spec[0], spec[1]) - spec[0]) / (spec[1] - spec[0]) * 1295));
  }
  return out;
}
function protoChecks() {
  const out = [];
  const base = "dapper-fig.I610gavij3f7q16o0000006m00";
  const t = (code, test, what) => { const r = readAnyCode(code); out.push({ code, what, ok: !!r && (!test || test(r.b)) }); };
  t(base + ".e1.x10zz00", b => b.hand.d.pd > .3 && b.hand.d.ps === 0 && b.hand.d.ts > .99, "lab v2 .x pen suffix");
  t(base + "8v001.e1", b => b.medium.k === "charcoal" && b.life.k === "none", "brush-experiments ink tail (spread, tilt, fast)");
  t(base + "1c7jsg7.e1", b => b.tip.k === "bristles", "marble-and-bristle swarm tail");
  t("mellow-plum.P600xjf400c4jzxh00aqhuzz001000.hd", b => b.medium.k === "cover" && b.tip.k === "hake", "brush-experiments paint tail (cover, head)");
  t("mellow-plum.P700xjf400c4jzxh00aqhu00001000.hd", b => b.style === "Hake" && b.tip.k === "hake", "brush-experiments Hake style");
  const bb = { nib: "chisel", mode: "wet", texture: "cold press", wet: .55, line: .8, aspect: .4, angle: 40, taperIn: .2, taperOut: .5, pressure: .7, speed: -.3,
    grain: .2, bristle: .1, wobble: 0, pool: .3, splatter: 0, soft: .5, bleed: .4, bloom: .2, backrun: 0, drying: .5,
    lines: "split", count: 3, spread: .6, breathe: .8, offset: 0, chaos: 0, plume: .7, pstyle: "vein", settle: .4 };
  const head = "brisk-quince.B6" + writeSpec(bb, BLEND_ORDER);
  t(head + ".7", b => b.medium.k === "ink" && Math.abs(b.medium.d.wet - .55) < .01 && b.paper.k === "cold press", "pen-paint-blend B code");
  t(head + writeSpec(bb, BLEND_TAIL) + ".7", b => b.tip.k === "split" && Math.abs(b.tip.d.gap - .6) < .01, "B code with a split-nib tail");
  t(head + writeSpec(bb, BLEND_TAIL.slice(0, 4)) + ".7", b => b.tip.k === "split", "B code from round 2 (tail before Offset and Chaos)");
  t(head + writeSpec(bb, PLUME_TAIL) + ".7", b => b.life.k === "dry" && b.life.d.takes === 2 && Math.abs(b.life.d.settle - .4) < .01, "B code with ink-and-water's plume tail");
  for (const c of ["hello", "x.M0_0_0.1", "dapper-fig.I610gavij3f7q16o0000006m0.e1", "a.M9z0000.1"]) out.push({ code: c, what: "a bad code reads as nothing", ok: readAnyCode(c) === null });
  return out;
}
// Float (suminagashi): drops keep area, rings print crisp, clear water lays nothing, the log replays, Undo while it
// floats, drift only when tilted, prints when its time is up, codes round-trip.
function floatChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok });
  const mk = (load, tip = "round") => {
    const B = { name: "f", seed: 7, style: "Ink wash", tip: freshPart("tip", tip), medium: freshPart("medium", "ink"), carry: freshPart("carry", load === undefined ? "off" : "smudge"),
      paper: freshPart("paper", "plain"), life: freshPart("life", "float"), hand: freshPart("hand", "steady") };
    if (load !== undefined) { B.carry.d.load = load; B.carry.d.pick = .9; }
    return normBrush(B);
  };
  const W = 360, H = 260, ink = mk(), cw = mk(0);
  const area = g => { let a = 0; for (const c of g.cs) { let s = 0; for (let i = 0, j = c.n - 1; i < c.n; j = i++) s += c.x[j] * c.y[i] - c.x[i] * c.y[j]; a += Math.abs(s / 2); } return a; };
  const hashRings = b => { let h = 0x811c9dc5; for (const g of b.rings) for (const c of g.cs) for (let i = 0; i < c.n; i++) { h = Math.imul(h ^ Math.round(c.x[i] * 64), 16777619); h = Math.imul(h ^ Math.round(c.y[i] * 64), 16777619); } return h >>> 0; };
  let T = 0;
  const tap = (s, host, B, x, y, hold, shade = 1) => { const st = new Stroke(s, B, 8, shade, host); st.add([[x, y, .7, T, NaN, NaN]]); for (let k = 50; k <= hold; k += 50) st.holdTick(T + k); T += hold + 100; st.end(); return st; };
  const drag = (s, host, B, x0, y0, x1, y1, size = 8) => { const st = new Stroke(s, B, size, 1, host), n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 2.5);
    for (let i = 0; i <= n; i += 3) { const sm = []; for (let k = i; k < Math.min(n + 1, i + 3); k++) sm.push([x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n, .7, (T += 4), NaN, NaN]); st.add(sm); }
    T += 100; st.end(); return st; };
  const bathOf = s => ({ bath: null, getBath() { if (!this.bath || !this.bath.wet) this.bath = new Bath(s); return this.bath; } });
  // 1. Drops keep every ring's area.
  { const s = modSurface(W, H, 7), host = bathOf(s); tap(s, host, ink, 90, 130, 200); const b = host.bath, a0 = area(b.rings[0]);
    tap(s, host, cw, 230, 128, 400); tap(s, host, ink, 236, 135, 300);
    t("a drop pushes older paint out without changing its area (" + ((area(b.rings[0]) / a0 - 1) * 100).toFixed(2) + "%)", Math.abs(area(b.rings[0]) / a0 - 1) < .005); }
  // 2. Ink and clear water in turn print as crisp rings: many dark/light changes along a ray, each edge at most ~2 px of grey.
  { const s = modSurface(W, H, 7), host = bathOf(s);
    for (let i = 0; i < 6; i++) { tap(s, host, ink, 180, 130, 200 + 60 * i); tap(s, host, cw, 180, 130, 150); }
    host.bath.print();
    let changes = 0, grey = 0, last = -1;
    for (let x = 180; x < W; x++) { const v = Math.round(s.page[130 * W + x] / 17) * 17, k = v < 100 ? 0 : v > 200 ? 1 : -1; if (k < 0) grey++; else { if (last >= 0 && k !== last) changes++; last = k; } }
    t("ink and clear water in turn print as rings: " + changes + " edges along a ray, " + (grey / Math.max(1, changes)).toFixed(1) + " grey px an edge", changes >= 10 && grey / changes <= 2.2); }
  // 3. Clear water lays nothing of its own: on a blank page, a drag and a tap leave it blank.
  { const s = modSurface(W, H, 7), host = bathOf(s); drag(s, host, cw, 40, 60, 320, 200); tap(s, host, cw, 100, 200, 300); host.bath.print();
    let marked = 0; for (let i = 0; i < W * H; i++) if (Math.round(s.page[i] / 17) * 17 < 238) marked++;
    t("clear water on blank paper lays nothing (" + marked + " px)", marked === 0); }
  // 4. The log is the bath: replaying it gives the same rings, bit for bit; 5. Undo while it floats takes back one stroke.
  { const s = modSurface(W, H, 7), host = bathOf(s); tap(s, host, ink, 150, 120, 300); tap(s, host, cw, 152, 121, 200);
    const b = host.bath, h2 = hashRings(b);
    drag(s, host, mk(0, "multi"), 40, 130, 320, 130, 12);
    const h3 = hashRings(b), keep = b.strokes.map(o => o.slice()), b2 = new Bath(s); for (const ops of keep) { b2.begin(); for (const op of ops) b2.apply(op); }
    t("replaying the log gives the same bath", hashRings(b2) === h3 && h3 !== h2);
    b.undoLast(); t("Undo while floating takes back the last stroke exactly", hashRings(b) === h2); }
  // 6. Drift: none on a level page or in the dead zone; downhill when tilted, area kept; 7. prints when its time is up.
  { const s = modSurface(W, H, 7), host = bathOf(s); tap(s, host, ink, 150, 90, 300); tap(s, host, cw, 150, 90, 200);
    const b = host.bath, h0 = hashRings(b), cy = g => (g.b[1] + g.b[3]) / 2, y0 = cy(b.rings[0]), a0 = area(b.rings[0]);
    b.tick(b.lastTouch + 100, { x: 0, y: 0 }, 1); b.tick(b.lastTouch + 200, { x: .05, y: .05 }, 1);
    t("a level page (and the dead zone) doesn't drift", hashRings(b) === h0);
    for (let k = 3; k < 23; k++) b.tick(b.lastTouch + 100 * k, { x: 0, y: .9 }, 1);
    t("a tilted page drifts it downhill (" + (cy(b.rings[0]) - y0).toFixed(1) + " px in 2 s), area kept (" + ((area(b.rings[0]) / a0 - 1) * 100).toFixed(2) + "%)", cy(b.rings[0]) - y0 > 20 && Math.abs(area(b.rings[0]) / a0 - 1) < .01);
    b.tick(b.lastTouch + b.dryMs + 1, { x: 0, y: 0 }, 1); t("it prints when its floating time is up", !b.wet); }
  // 8. Codes: Float is the fourth Drying choice; its code round-trips.
  { const code = encodeM(cw), back = readAnyCode(code);
    t("a Float code round-trips (" + code + ")", back && encodeM(back.b) === code && back.b.life.k === "float" && code.split("_")[4][0] === "3"); }
  return out;
}
// Erasable (spec docs/archive/superpowers/specs/2026-10-16-erasable-design.md): pencil, ink over it, the eraser, and the ink is
// as it was; a wash erases in part; paint that erases over paint that doesn't gives it back; a smear moves only what
// erases; pressed hard, the eraser still leaves permanent ink; old codes, and every roll, as before.
function eraseChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 360, H = 240, q = v => Math.round(v / 17) * 17;
  const mk = (style, medium, md, carry) => {
    const B = { name: "e", seed: 11, style, tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium), carry: freshPart("carry", carry || "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", "wet"), hand: freshPart("hand", "steady") };
    Object.assign(B.medium.d, md || {});
    return normBrush(B);
  };
  const line = (x0, y0, x1, y1, n = 80) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p: .8, t: k * 6, tx: NaN, ty: NaN }));
  const rub = (s, x0, y0, x1, y1, max, p) => { const er = new Eraser(s, max); for (let k = 0; k <= 60; k++) er.add([[x0 + (x1 - x0) * k / 60, y0 + (y1 - y0) * k / 60, p, k * 6, NaN, NaN]]); };
  const eraseAll = s => { for (let y = 0; y <= s.h + 60; y += 60) rub(s, -40, y, s.w + 40, y, 220, 1); };
  const differ = (a, b) => { let n = 0; for (let i = 0; i < a.length; i++) if (q(a[i]) !== q(b[i])) n++; return n; };
  const dark = (s, x0, y0, x1, y1) => { let d = 0; for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) d += PAPER_TONE - s.page[y * s.w + x]; return d; };
  const pencil = mk("Pencil", "graphite", { grade: .55 }), ink = mk("Fineliner", "ink", { erase: 0 }); // permanent by hand: every medium starts at 1
  // 1. The sketch, inked, erased: the page is the ink alone.
  { const s = modSurface(W, H, 7), alone = modSurface(W, H, 7);
    layStroke(s, pencil, line(30, 40, 330, 200), 6, 1, null); layStroke(s, pencil, line(30, 130, 330, 110), 6, 1, null);
    const sketched = differ(s.page, alone.page);
    layStroke(s, ink, line(40, 200, 320, 50), 4, 1, null); layStroke(alone, ink, line(40, 200, 320, 50), 4, 1, null);
    eraseAll(s);
    t("pencil, ink over it, erased: " + sketched + " px of pencil gone, " + differ(s.page, alone.page) + " px differ from the ink alone", sketched > 1500 && differ(s.page, alone.page) === 0); }
  // 2. A brush from an old code erases wholly, as before.
  { const s = modSurface(W, H, 7), old = readAnyCode("steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud").b;
    layStroke(s, old, line(40, 120, 320, 120), 6, 1, null); eraseAll(s);
    t("an old ink code reads all erasable (" + old.medium.d.erase + ") and erases to paper", old.medium.d.erase === 1 && dark(s, 0, 0, W, H) === 0); }
  // 3. A wash (Erasable .4): the eraser takes about 40% of its darkness.
  { const s = modSurface(W, H, 7), wash = mk("Watercolor", "wash", { erase: .4 }); layStroke(s, wash, line(40, 120, 320, 120), 90, 1, null);
    const d0 = dark(s, 0, 0, W, H); eraseAll(s); const kept = dark(s, 0, 0, W, H) / d0;
    t("a wash at Erasable .4 keeps " + (kept * 100).toFixed(1) + "% of its darkness", Math.abs(kept - .6) < .03); }
  // 4. Erasable opaque paint over permanent ink: erased, the ink is back as it was.
  { const s = modSurface(W, H, 7), alone = modSurface(W, H, 7), white = mk("Gouache", "cover", { erase: 1 });
    layStroke(s, ink, line(40, 120, 320, 120), 6, 1, null); layStroke(alone, ink, line(40, 120, 320, 120), 6, 1, null);
    layStroke(s, white, line(40, 120, 320, 120), 60, .05, null);
    const hidden = differ(s.page, alone.page); eraseAll(s);
    t("erasable gouache over ink (" + hidden + " px), erased: " + differ(s.page, alone.page) + " px differ from the ink", hidden > 1000 && differ(s.page, alone.page) === 0); }
  // 5. A finger (Smear, Own paint 0) moves pencil, never permanent ink.
  { const finger = mk("Charcoal", "charcoal", { erase: 1 }, "smudge"); finger.carry.d.load = 0; finger.carry.d.pick = .9;
    const moved = B => { const s = modSurface(W, H, 7); layStroke(s, B, line(180, 20, 180, 220), 8, 1, null); const was = Float32Array.from(s.page);
      layStroke(s, finger, line(60, 120, 300, 125), 30, 1, null); let m = 0; for (let i = 0; i < was.length; i++) m += Math.abs(s.page[i] - was[i]); return m; };
    const mi = moved(ink), mp = moved(pencil);
    t("a finger smears pencil (" + mp.toFixed(0) + " tone) and leaves ink (" + mi.toFixed(1) + ")", mp > 2000 && mi < 1); }
  // 6. However hard it is pressed, the eraser leaves permanent ink as it was.
  { const s = modSurface(W, H, 7); layStroke(s, ink, line(40, 120, 320, 120), 12, 1, null); const d0 = dark(s, 0, 0, W, H);
    rub(s, 20, 120, 340, 120, 120, .6); const d1 = dark(s, 0, 0, W, H); rub(s, 20, 120, 340, 120, 120, 1); rub(s, 20, 120, 340, 120, 120, 1); const d2 = dark(s, 0, 0, W, H);
    t("pressed lightly or hard, twice, the eraser leaves the ink (" + (d1 / d0 * 100).toFixed(1) + "%, " + (d2 / d0 * 100).toFixed(1) + "%)", d1 === d0 && d2 === d0); }
  // 7. Codes: old ones byte for byte; the dial written only when not 1, after charcoal's dust; round trips.
  { const olds = ["steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud", "messy-stick.M51eueei000005bd4at_3nbcw_0_2tz_1jz_8syjs00y8j900000000nz0.t4mf73",
      "soft-wash.M60i0000000007q_1wva200000000_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_0000000000000000000nz0.7"];
    t("old codes re-encode byte for byte", olds.every(c => encodeM(readAnyCode(c).b) === c));
    const c0 = encodeM(ink), cc = encodeM(mk("Charcoal", "charcoal", { erase: .9 })), back = readAnyCode(c0).b;
    t("an ink at Erasable off writes it last (" + c0.split("_")[1] + "); a charcoal at .9 after its dust (" + cc.split("_")[1] + ")",
      back.medium.d.erase === 0 && encodeM(back) === c0 && /00$/.test(c0.split("_")[1]) && cc.split("_")[1].slice(-4, -2) === "00" && Math.abs(readAnyCode(cc).b.medium.d.erase - .9) < .001); }
  // 8. No roll changes Erasable (the owner, 2026-10-22): a new brush starts at 1, a roll over a brush keeps its value
  //    (Randomize, a part's dice), the dial's own die leaves it, and every other part rolls exactly as before (300 rolls).
  //    Names keep their adjective here (nameChecks: only the name changes).
  { const keep = Math.random; Math.random = rng(4242); TRAIT_NAMES.on = false; let h = 0x811c9dc5, fresh = true, kept = true;
    const none = { parts: new Set(), dials: new Set() };
    // The hash was taken over the fourteen styles before Print.
    const OLD14 = STYLE_NAMES.filter(n => n !== "Print");
    try { const all = [];
      for (let i = 0; i < 300; i++) { const st = OLD14[i % OLD14.length]; let B = rollStyle(st); all.push(B);
        if (B.medium.k !== "resist" && B.medium.d.erase !== 1) fresh = false;
        B = cloneBrush(B); B.medium.d.erase = 1; for (const ch of encodeM(B)) h = Math.imul(h ^ ch.charCodeAt(0), 16777619); }
      for (const B of all) if (B.medium.k !== "resist") { const p = cloneBrush(B); p.medium.d.erase = .25;
        const r = rollStyle(B.style, p, none), m = rollOnePart(p, "medium", none), d = rollOneDial(p, "medium", "erase");
        for (const x of [r, m, d]) if (x.medium.k !== "resist" && x.medium.d.erase !== .25) kept = false; } } finally { Math.random = keep; TRAIT_NAMES.on = true; }
    t("rolls start at Erasable 1 and keep a brush's own through Randomize, a part's dice and the dial's; with it at 1 they hash as before (" + (h >>> 0).toString(16) + ")",
      fresh && kept && (h >>> 0) === 0x3cfc3ac9); }
  return out;
}
// Names (parts.js describedName and kinOf, the app's BrushNamesChecks): a new brush is named for what it is, a fun
// word, a describer and its part, deadpan, from the brush and its seed; the same words, forms, made-up words and
// variations as the app's.
function nameChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), newMods = CHOICES.tip.some(c => c.k === "stipple");
  // A Fineliner (all its usual), then [part, choice, dials] changes.
  const mk = (style, seed, ...ch) => {
    const B = { name: "dapper-plum", seed, style, tip: freshPart("tip", "round"), medium: switchChoice("medium", null, "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", "wet"), hand: freshPart("hand", "steady") };
    for (const [part, k, d] of ch) { if (k) B[part] = switchChoice(part, B[part], k); Object.assign(B[part].d, d || {}); }
    return normBrush(B);
  };
  // [describer|either part|either] ("-" any describer), as BrushNamesChecks.traits has them.
  const F = "Fineliner", cases = [
    ["- pen|liner", mk(F, 1)], ["- nib", mk(F, 1, ["tip", "flat"], ["life", "none"])], ["- float", mk(F, 1, ["life", "float"])],
    ["- smear|smudge", mk(F, 3, ["carry", "smudge"])], ["- stump", mk(F, 1, ["carry", "smudge", { soften: .6 }])],
    ["- comb", mk(F, 1, ["tip", "multi"], ["carry", "smudge", { soften: .6 }])], ["- lift", mk(F, 1, ["carry", "lift"])], ["inky|inkish drag", mk(F, 2, ["carry", "drag"])],
    ["dusty|sooty charcoal", mk(F, 1, ["medium", "charcoal", { dust: .4 }])], ["dusty|sooty chisel", mk(F, 1, ["tip", "chisel"], ["medium", "charcoal", { dust: .4 }])],
    ["- rake", mk(F, 1, ["tip", "multi"])], ["- split", mk(F, 1, ["tip", "split"])], ["- bristle", mk(F, 1, ["tip", "bristles"])],
    ["- hake", mk(F, 1, ["tip", "hake"])], ["- chisel", mk(F, 1, ["tip", "chisel"])], ["- float", mk(F, 1, ["tip", "multi"], ["life", "float"])],
    ["ribbed|ribby pen|liner", mk(F, 1, ["paper", "laid", { tooth: .8 }])], ["- pen|liner", mk(F, 1, ["paper", "laid", { tooth: .5 }])],
    ["- pencil", mk(F, 1, ["medium", "graphite"])], ["- wash", mk(F, 1, ["medium", "wash"])], ["- paint", mk(F, 1, ["medium", "cover"])],
    ["- hake", mk("Hake", 1, ["tip", "hake"], ["medium", "wash"])], ["- pencil", mk("Pencil", 1, ["medium", "graphite"])]];
  if (newMods) cases.push(["- mask", mk(F, 1, ["medium", "resist", { erase: 1 }])], ["waxy wax|candle", mk(F, 1, ["medium", "resist", { erase: 0 }])],
    ["- dots|stipple", mk(F, 1, ["tip", "stipple"])], ["- hatch", mk(F, 1, ["tip", "hatch"])], ["chalky|pale|ghostly pen|liner", mk(F, 1, ["medium", null, { blend: 1 }])],
    ["- mold", mk(F, 1, ["life", "halo"])], ["bloomy|blossomy hake", mk(F, 1, ["tip", "hake"], ["medium", "wash", { bloom: .6 }])]);
  const wrong = cases.filter(([want, B]) => { const w = plainNameWords(B), [d, p] = want.split(" ");
    return !(w.length === 3 && p.split("|").includes(w[2]) && (d === "-" || d.split("|").includes(w[1])) && describedName(B, B.name) === brushNameOf(B)); })
    .map(([want, B]) => want + " not " + plainNameWords(B).join(" "));
  t("a name says what the brush is, most telling first (" + cases.length + " brushes" + (wrong.length ? ": " + wrong.join(", ") : "") + ")", !wrong.length);
  { const keep = TRAIT_NAMES.on; TRAIT_NAMES.on = false; const off = describedName(mk(F, 1, ["life", "float"]), "dapper-plum"); TRAIT_NAMES.on = keep;
    t("off, the name as it was; no family part, no new name", off === "dapper-plum" && describedName(mk(F, 1, ["life", "float"]), "") === ""); }
  t("traits.txt's five sections (" + [...TRAITS.keys()] + "), short words", TRAITS.size === 5 && ["part", "describer", "fun"].every(k => TRAITS.get(k).has("any"))
    && ["part", "describer", "fun"].every(k => [...TRAITS.get(k).values()].flat().every(w => /^[a-z]{3,8}$/.test(w))));
  t("an edited list reads what it can", JSON.stringify([...traitSections("# a\n#\ntip.multi:\trake raked\npaper.cold  press: grainy\ntip.multi: comb\nnonsense\ntip.split: Split 2 forky\ntip.hake: hake-y\n[fun]\nany: brisk\n[describer]\n# nothing\n")].map(([k, m]) => [k, [...m]])) ===
    JSON.stringify([["part", [["tip.multi", ["rake", "raked"]], ["paper.cold press", ["grainy"]], ["tip.split", ["forky"]]]], ["fun", [["any", ["brisk"]]]]]));
  // Made-up words: the app's BrushNamesChecks.nonsense.
  { let bad = 0, ok = true;
    for (const plain of [["giddy", "dusty", "chisel"], ["giddy", "shy", "chisel"]]) { const seen = new Set(); let m = 0;
      for (let seed = 0; seed < 1000; seed++) { const w = nonsenseName(plain, seed); if (w === plain) continue; m++; seen.add(w.join(" "));
        if (w[0] !== "giddy" || !(plain[1] === "dusty" ? w[1].startsWith("dust") && w[2] === "chisel" : w[1] === "shy" && w[2].startsWith("chisel"))) bad++; }
      ok = ok && m > 120 && m < 220 && (seen.has("giddy dustwhistle chisel") || seen.has("giddy shy chiselkins")); }
    t("about one name in six has a made-up word, still the brush's", !bad && ok
      && nameStem("wobbly") === "wobble" && nameStem("shaky") === "shake" && nameStem("ghostly") === "ghost" && nameStem("pale") === null
      && nameSuffixed("drag", "y") === "draggy" && nameSuffixed("smudge", "ery") === "smudgery" && nameSuffixed("drip", "ery") === "drippery" && nameSuffixed("pen", "kins") === "penkins"); }
  // Deadpan forms, every name saying its part (BrushNamesChecks.deadpan): Title Case, "of" and "for" between (BrushNamesChecks.FORM).
  const NAME_FORM = /^[A-Z][a-z]+( ([A-Z][a-z]+|of|for)){1,3}$/;
  { let n = 0, bad = 0; const seen = new Set();
    for (let seed = 0; seed < 500; seed++) { const B = mk(F, seed, ["tip", "hake"], ["medium", "wash", { bloom: .6 }]), w = brushNameOf(B); seen.add(w);
      if (!w.toLowerCase().includes("hake") || w.length > NAME_MAX || !nameTwoLines(w)) bad++;
      if ([nameWords(B), plainNameWords(B)].some(ws => ws.join(" ") === w.toLowerCase())) continue; n++;
      const kin = kinOf(w, seed);
      if (!NAME_FORM.test(w) || !kin.endsWith(w.slice(w.indexOf(" "))) && TRAIT_WORDS.has(w.slice(0, w.indexOf(" ")).toLowerCase())) bad++; }
    t("most names are deadpan forms, every one saying hake (" + n + ": " + [...seen].slice(0, 3).join(", ") + ")", !bad && n > 330 && n < 470 && [...seen].some(x => x.includes(" of ")) && [...seen].some(x => x.endsWith(" Situation"))
      && kinOf("Dusty Aunt Charcoal", 3) === "Sooty Aunt Charcoal" && kinOf("Smear of Mild Regret", 3) === "Smudge of Mild Regret"); }
  // Alike brushes differ, as in the app (BrushNamesChecks.SEEDS).
  { let picks = ""; const names = new Set();
    for (let seed = 0; seed < 64; seed++) { const n = describedName(mk(F, seed), "dapper-plum"); names.add(n); for (const w of n.split("-")) picks += w[0]; }
    t("alike brushes mostly differ (" + names.size + " of 64), as in the app", names.size >= 30 && picks === "UTIILPPSPOIBLSBIICIBILHDWIIIPBKIPPNSRISIBPLIIIPPNIDLCBIIPEIIPINI"); }
  // A variation keeps the rest under another fun word of its mood, an older drip-plum its drip (BrushNamesChecks.KIN, FUN_KIN), drawing as kinName does.
  { let kin = "", fun = ""; for (let seed = 0; seed < 8; seed++) { kin += kinOf("drip-plum", seed * 7919) + " "; fun += kinOf("sloppy-inky-drag", seed * 7919) + " "; }
    const keep = Math.random; let n = 0, m = 0;
    try { Math.random = () => (n++, .5); kinOf("drip-plum", 3); kinOf("sloppy-inky-drag", 3); kinOf("-", 3); Math.random = () => (m++, .5); kinName("drip-plum"); kinName("sloppy-inky-drag"); kinName("-"); } finally { Math.random = keep; }
    t("a variation keeps the describer and part, as in the app (" + fun.trim() + "), drawing as before (" + n + " " + m + ")",
      kin === "drip-yam drip-garlic drip-cherry drip-pear drip-garlic drip-peach drip-carrot drip-date " && fun === "loose-inky-drag gooey-inky-drag gooey-inky-drag loose-inky-drag gooey-inky-drag juicy-inky-drag lush-inky-drag loose-inky-drag "
      && n === m && /^[a-z]+-plum$/.test(kinOf("dapper-plum", 3))); }
  // 300 rolls with trait names on and off, the same Random: the same brush and seed; the name its words or a deadpan form, saying its part.
  { const roll = on => { const keep = Math.random, k0 = TRAIT_NAMES.on; Math.random = rng(5151); TRAIT_NAMES.on = on;
      try { return Array.from({ length: 300 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; TRAIT_NAMES.on = k0; } };
    const a = roll(true), b = roll(false); let bad = 0; const names = new Set();
    for (let i = 0; i < a.length; i++) {
      names.add(a[i].name);
      if (encodeM(Object.assign(cloneBrush(a[i]), { name: b[i].name })) !== encodeM(b[i]) || !NAMES_ADJ.includes(b[i].name.split("-")[0]) || a[i].name !== brushNameOf(a[i]) || a[i].name.length > 32
        || !nameTwoLines(a[i].name) || !a[i].name.toLowerCase().includes(plainNameWords(a[i])[2].slice(0, 4))
        || !NAME_FORM.test(a[i].name)) bad++;
    }
    t("300 rolls are as before but for the name (" + names.size + " names: " + a.slice(0, 6).map(B => B.name).join(", ") + ")", !bad && names.size > 120); }
  // App codes the app names the same (BrushNamesChecks.LAB_NAMED).
  { const codes = [
      "wild-plum.M00j40p000000at_000zz6400000000000_0_044_1jz_82500000000005w0000nz0.1o028zx", "Inkish Pen Situation",
      "jumbo-okra.M10i0hpxm3d00at_000ve0000000000000_0_000_1jz_8zj0000000000i60000nz0.10hs76", "Regrettable Pen",
      "split-mango.M24fvokcxi06l9s00_0apzz0000000000000_0_100_1jz_8u700i8000000990000nz0.zwz2n7", "Inkish Split Situation",
      "smear-plum.M40i000000000at_4ht3b_3hmjsat_3sj_1jz_8egh500myeb00000000nz0.1154jqa", "Fuzzmonger Smear Junior",
      "float-quince.M60i0000000008t_1zued00000000ee_0_0h0_3lldii0_8p90000000000000000nz0.d21ddj", "Lazy Blotchy Float",
      "bristle-radish.Mb6vuj8jsqli0_2v400at_0_2qe_1jz_8dz0000000000000000nz0.171wrh6", "Ribbed Bristle",
      "lift-apple.M80fy000000000v_2zz00at_177at_100_1jz_8720000000000000000nz0.xgo4h0", "Painty Niece Lift",
      "lead-okra.Md22h84hw00rqs500ot_4ziua_0_000_1ul_8xb00dr00000000bz00nz1.qbqfn0", "Polite Scratchy Nib",
      "dust-bean.M51sgeei03s3fck00at_383lkdnwe_0_0xu_1jz_8uvlj00ufg700000000nz0.1eetlzr", "Chisel of Some Bother",
      "crease-parsnip.Mb0i00000yo007v_14hat00770000ee_0_4nj_1jz_8na0000000000000000nz0.ss1dt7", "Wash of Faint Worry",
      "hatch-bean.Md887dj671zzm3_2u900at_0_04u_1qe_80000ep008g00gy8z00nz0.4oy7gg", "Budget Creamy Hatch",
      "paint-beet.Md2a8qjeb006vik0066_2wp00at_0_26b_1re_8sx0000kf0000000000nz0.12l96hy", "Budget Scratchy Nib",
      "under-lychee.M70i000on0000uq_1om00f70000phee2_0_000_1jz_8o20000000000000000nz0.169plr8", "Wash of Minor Worry",
      "laid-shallot.Mb25aeei00000u100ak_19qat00770000ee_0_2ln_1jz_8fy0000000000000000nz0.gdoqm5", "Scratchy Cousin Nib",
      "rake-date.Md5c08gdu00j0df75_2v3dmat_0_100_1va_8pn000000000000d500nz0.mig41m", "Budget Ridged Rake",
      "white-quince.M80f6000000003k_2zz00at1_177at_04r_1jz_87c0000000000000000nz0.y41duu", "Chalky Lift Situation",
      "invert-kale.Md0bmh92cfc00aj_1htdc00008700ee3_0_500_1et_8eyoy00000000q60000nz0.yo9dw8", "Odd Flipped Wash",
      "mask-lychee.M60i000000000en_5_0_100_1jz_8em0000000000000000nz0.1qim1tm", "Prim Rubbery Mask",
      "grain-date.Md0ayfd000082yf_0g8ny0000000000000_0_1n6_1t2_8000000ky00pt000000nz0.rdle15", "Pen for Beginners"];
    const off = [];
    for (let i = 0; i < codes.length; i += 2) { const r = readAnyCode(codes[i]), w = r ? brushNameOf(r.b) : "?"; if (w !== codes[i + 1]) off.push(codes[i + 1] + " not " + w); }
    if (newMods) t("19 app codes name as in the app" + (off.length ? ": " + off.join(", ") : ""), !off.length); }
  return out;
}
function selfTest() {
  const legacy = LEGACY_CODES.map(c => legacyDiff(c));
  const trips = roundTrips();
  const proto = protoChecks();
  const float = floatChecks();
  const erase = eraseChecks();
  const names = nameChecks();
  const ok = legacy.every(l => l.ok) && !trips.length && proto.every(p => p.ok) && float.every(f => f.ok) && erase.every(f => f.ok) && names.every(f => f.ok);
  return { ok, legacy, trips, proto, float, erase, names };
}

;
// ===================================================================================================================
// New modules (spec docs/archive/superpowers/specs/2026-10-16-new-modules-design.md), kept in this one file:
//   Blend   a Medium dial: how a mark meets what's there (the medium's own way, Lighten, Behind, Invert);
//   Stipple and Hatch, Tip choices: dots scattered along the path, parallel lines that cross with pressure;
//   Resist  a Medium choice: masking fluid or wax, shown hatched, which nothing takes on until it is rubbed off;
//   Halo    a Drying choice, shown as Mold (the owner's name, 2026-10-18; its key stays halo): veins that reach from the
//           wet stroke to nearby marks and bridge them, then set.
// Everything is appended (choices, dials, codes), so every old code reads, draws and writes exactly as before. Built
// after checks.js: it extends parts.js's tables and wraps resolve, the compose and a few of the stroke's hooks.
// ===================================================================================================================
const NM = { rolls: true, coreRolls: true, last: null };

// ---------- the tables, appended ----------
Object.assign(DIALS.tip, {
  dot: { label: "Dot Size", min: 0, max: 1, def: .35 },
  scatter: { label: "Scatter", min: 0, max: 1, def: .4 },
  jitter: { label: "Uneven Dots", min: 0, max: 1, def: .5 },
  pitch: { label: "Spacing", min: 0, max: 1, def: .4 },
  lineW: { label: "Line Weight", min: 0, max: 1, def: .3 },
  hangle: { label: "Line Angle", min: 0, max: 180, def: 45, fmt: v => Math.round(v) + "°" },
  layers: { label: "Layers", min: 1, max: 3, def: 3, int: true, fmt: v => String(Math.round(v)) },
  // Stipple's Build-up (the owner: "can it randomize position on hold?"): dots keep landing in the tip while the pen rests
  // or lingers, as a spray does. Last of Stipple's dials, written only when on.
  hold: { label: "Build-Up", choices: ["Off", "Slow", "Fast"], def: 0 }
});
// Blend's first option is the medium's own way: darken for every medium but Opaque, which covers (its Opacity). The
// second (key lighten) is shown as White: what it lays.
DIALS.medium.blend = { label: "Blend Mode", choices: ["Darken", "White", "Behind", "Invert"], def: 0 };
Object.assign(DIALS.life, { reach: { label: "Mold Length", min: 0, max: 1, def: .5 }, growth: { label: "Mold Growth", min: 0, max: 1, def: .5 } });
CHOICES.tip.push(
  { k: "stipple", label: "Stipple", dials: ["dot", "scatter", "jitter", "weight", "hold"], macro: "dot", words: ["fine", "coarse"],
    blurb: "Dots scattered along the stroke. Pressure packs them closer." },
  { k: "hatch", label: "Hatch", dials: ["pitch", "lineW", "hangle", "headTurn", "layers", "weight"], macro: "pitch", words: ["tight", "open"],
    labels: { headTurn: "Lines Follow" }, def: { headTurn: 1 }, blurb: "Parallel lines instead of a solid mark. Pressure crosses them a second and a third way." });
for (const c of CHOICES.medium) c.dials.push("blend");
choiceOf("medium", "cover").opts = { blend: ["Cover", "White", "Behind", "Invert"] };
CHOICES.medium.push({ k: "resist", label: "Resist", dials: ["erase"], macro: "erase", words: ["wax", "fluid"],
  blurb: "Masking fluid or wax. Nothing takes on it until it is rubbed off. Shown hatched." });
CHOICES.life.push({ k: "halo", label: "Mold", dials: ["drying", "reach", "growth"], macro: "reach", words: ["short", "long"], def: { drying: .55 },
  blurb: "Veins reach from the wet stroke to nearby marks and bridge them, then set as it dries." });
ADDED.add("medium.blend"); ADDED.add("tip.hold");
REAL_ERASE.resist = 1;   // masking fluid rubs off; wax (Erasable off) stays
const BLENDS = ["own", "lighten", "behind", "invert"];
// A choice dial's option as shown: Opaque's own way is "cover".
function optLabel(part, k, key, i) { const o = (choiceOf(part, k).opts || {})[key]; return (o || DIALS[part][key].choices)[i]; }
const PATTERN_TIPS = new Set(["stipple", "hatch"]);
const isNew = B => PATTERN_TIPS.has(B.tip.k) || B.medium.k === "resist" || B.life.k === "halo" || B.carry.k === "water" || ((B.medium.d.blend | 0) > 0 && B.medium.k !== "resist")
  || B.medium.k === "tone" || (B.medium.d.screen | 0) > 0;

// ---------- resolve: the new choices as the engine's own, plus what only they need ----------
const resolve0 = resolve;
resolve = function (B, size, shade) {
  const T = B.tip, tk = T.k, mk = B.medium.k, pattern = PATTERN_TIPS.has(tk), resist = mk === "resist", halo = B.life.k === "halo";
  const blend = resist ? 0 : (B.medium.d.blend | 0);
  if (!pattern && !resist && !halo && !blend) return resolve0(B, size, shade);
  let A = B;
  if (pattern || resist || halo) {
    A = cloneBrush(B);
    // A pattern tip lays its dots or lines where a round tip of its weight would reach; the stampers are its own.
    if (pattern) A.tip = { k: "round", d: { weight: T.d.weight ?? 1, taperIn: 0, taperOut: 0, dry: 0, wobble: 0, soft: .3 } };
    // Resist is laid as a crisp ink line, into its own plane instead of the page.
    if (resist) A.medium = { k: "ink", d: { wet: 0, line: 1, pool: 0, splatter: 0, bleed: 0, bloom: 0, lines: 0, offset: 0, chaos: 0, erase: B.medium.d.erase ?? 1, blend: 0 } };
    // Resist goes on paper, never on water: on Float it lays as Quick. It smears nothing (the app's: Smudge does nothing on it).
    if (resist && A.life.k === "float") A.life = freshPart("life", "wet");
    if (resist) A.carry = freshPart("carry", "off");
    // Halo is Slow's lift (no settling of its own), then the veins grow for its drying time.
    if (halo) A.life = { k: "dry", d: { drying: B.life.d.drying ?? .55, settle: 0, takes: 0, plume: .55 } };
  }
  const R = resolve0(A, size, shade);
  R.blend = BLENDS[blend];
  if (blend === 1 || blend === 3) R.settle = 0;   // settling pigment only darkens
  if (resist) { R.resist = true; R.blend = "own"; }
  if (pattern) {
    const t = key => T.d[key] ?? DIALS.tip[key].def;
    R.lineTip = "nm"; R.linePlaced = false; R.lineStateful = true;
    if (R.body) { R.bodyTip = "nm"; R.bodyStateful = true; }
    R.pat = { kind: tk, seed: B.seed >>> 0, dot: t("dot"), scatter: t("scatter"), jitter: t("jitter"), hold: tk === "stipple" ? T.d.hold | 0 : 0, pitch: t("pitch"), lineW: t("lineW"),
      angle: T.d.hangle ?? 45, fixed: ((T.d.headTurn ?? 1) | 0) === 1, layers: Math.round(T.d.layers ?? 3), ps: handCell(B, "ps"),
      dark: { pDark: handCell(B, "pd"), sDark: handCell(B, "sd"), tDark: 0 } };
  }
  if (halo) {
    const L = B.life.d, big = clamp(Math.sqrt((R.line ? R.inkSize : R.bodySize) / 6), 1, 3);
    R.halo = { reach: Math.round((12 + 68 * (L.reach ?? .5)) * big), growth: L.growth ?? .5, ticks: wetTicksOf(L.drying ?? .55),
      dark: R.line ? clamp(R.inkK) : laid(R.washK * 1.5) };
  }
  return R;
};

// ---------- Stipple and Hatch: the stampers ----------
function nmDot(s, buf, cx, cy, r, val, d, rect) {
  const w = s.w, x0 = Math.max(0, Math.floor(cx - r - 1)), x1 = Math.min(w - 1, Math.ceil(cx + r + 1)), y0 = Math.max(0, Math.floor(cy - r - 1)), y1 = Math.min(s.h - 1, Math.ceil(cy + r + 1));
  if (x1 < x0 || y1 < y0) return;
  for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
    const dx = px + .5 - cx, dy = py + .5 - cy, cov = clamp(r + .5 - Math.sqrt(dx * dx + dy * dy)) * val;
    if (cov <= 0) continue;
    const i = py * w + px;
    if (cov > buf[i]) buf[i] = cov;
    if (WET) WET[i] = d;
  }
  grow(rect, [x0, y0, x1, y1]);
}
// Stipple: every cell of path (a dot and a bit long) holds a fixed set of candidate dots, from the stroke's seed and the
// cell's number; pressure decides how many of them land. Jitter 0 sets them on a staggered lattice across the band
// (an even stipple); 1 scatters them and varies their size (a sponge). Each pass of the pen adds dots, so tone builds.
function stampStipple(st, buf, size, from, M) {
  const P = st.R.pat, s = st.surf, pts = st.pts, rect = [s.w, s.h, -1, -1];
  M.seed ??= hashInts(P.seed ^ 0x57e1, Math.round(pts[0].x * 4), Math.round(pts[0].y * 4));
  // Dots up to two fifths of the tip across (a pen's dots, a sponge's pores); clumps of up to four as Jitter nears 1.
  const dotD = clamp(size * lerp(.05, .4, P.dot * P.dot), 1.6, 60), band = Math.max(dotD * .5, size / 2 * (1 + 1.6 * P.scatter));
  const clump = P.jitter > .5 ? (P.jitter - .5) * 2 : 0, step = Math.max(1.2, dotD * .9);
  const per = Math.max(1, Math.round(2 * band * step / (dotD * dotD) * 1.1 / (1 + 1.5 * clump)));
  M.next ??= 0; M.cell ??= 0;
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d, ux = seg > 0 ? (p1.x - p0.x) / seg : 1, uy = seg > 0 ? (p1.y - p0.y) / seg : 0;
    while (M.next <= p1.d) {
      const t = seg > 0 ? clamp((M.next - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t);
      const dens = lerp(.12, 1, smooth(pr)), dk = handDark(P.dark, pr, v, 0) * (p1.own === undefined ? 1 : lerp(p0.own, p1.own, t)), bw = band * (P.ps ? lerp(1, .35 + .65 * pr, P.ps) : 1), c = M.cell;
      for (let j = 0; j < per; j++) {
        const hs = hashInts(M.seed, c, j);
        if (hash01(hs, 0) >= dens) continue;
        const lat = ((j + .5 + (c & 1) * .5) / per) % 1 * 2 - 1, rnd = hash01(hs, 1) + hash01(hs, 2) - 1;
        const across = lerp(lat, rnd, P.jitter) * bw, along = (hash01(hs, 3) - .5) * step * (.3 + .7 * P.jitter);
        nmDotClump(s, buf, P, hs, x - uy * across + ux * along, y + ux * across + uy * along, dotD, clump, dk, M.next, rect);
      }
      M.next += step; M.cell++;
    }
  }
  // Build-up: each tick's dots at random in the band round the pen, as many as cover it once in its fill time, by pressure.
  const H = pts.holds;
  if (H && P.hold > 0) {
    M.holdsLaid ??= 0; M.holdAcc ??= 0;
    const fill = HOLD_FILL_MS[Math.min(2, P.hold)];
    for (; M.holdsLaid < H.ev.length; M.holdsLaid++) {
      const e = H.ev[M.holdsLaid], pr = e.p, bw = band * (P.ps ? lerp(1, .35 + .65 * pr, P.ps) : 1), dens = lerp(.12, 1, smooth(pr));
      M.holdAcc += Math.min(HOLD_MOST_DOTS, 4 * bw * bw / (dotD * dotD) * dens * HOLD_TICK / fill);
      const n = Math.floor(M.holdAcc); M.holdAcc -= n;
      const dk = handDark(P.dark, pr, 0, 0) * (pts[pts.length - 1].own ?? 1);
      for (let j = 0; j < n; j++) {
        const hs = hashInts(M.seed ^ 0x401d, e.tick, j), a = 2 * Math.PI * hash01(hs, 1), rad = bw * Math.sqrt(hash01(hs, 2));
        nmDotClump(s, buf, P, hs, e.x + rad * Math.cos(a), e.y + rad * Math.sin(a), dotD, clump, dk, e.d, rect);
      }
    }
  }
  return rect;
}
// A candidate's dot, its size jittered, and its clump of up to three more as Jitter nears 1.
function nmDotClump(s, buf, P, hs, cx, cy, dotD, clump, dk, d, rect) {
  const r = Math.max(.8, dotD / 2 * (1 + P.jitter * .9 * (hash01(hs, 4) - .5)));
  nmDot(s, buf, cx, cy, r, dk, d, rect);
  for (let n = Math.floor(clump * 3.99 * hash01(hs, 5)), m = 0; m < n; m++)
    nmDot(s, buf, cx + (hash01(hs, 6 + m) - .5) * dotD * 1.8, cy + (hash01(hs, 9 + m) - .5) * dotD * 1.8, r * lerp(.45, .9, hash01(hs, 12 + m)), dk, d, rect);
}
// Build-up's ticks: every HOLD_TICK ms of pen time since pen-down, the pen's place, pressure and distance at the sample
// that reached it. Every sample feeds it, kept or not (a resting pen's are too close to keep), and the test area ticks a
// resting pen as the app's pen-down ticker does (the last sample again, at the time).
const HOLD_TICK = 20, HOLD_FILL_MS = [0, 4000, 1200], HOLD_MOST_TICKS = 250, HOLD_MOST_DOTS = 400;
class Holds {
  constructor() { this.t0 = NaN; this.ticks = 0; this.ev = []; }
  feed(x, y, p, t, d) {
    if (!Number.isFinite(t)) return false;
    if (this.t0 !== this.t0) { this.t0 = t; return false; }
    const now = Math.max(this.ticks, Math.min(this.ticks + HOLD_MOST_TICKS, Math.floor((t - this.t0) / HOLD_TICK)));
    if (now === this.ticks) return false;
    for (let n = this.ticks + 1; n <= now; n++) this.ev.push({ x, y, p, d, tick: n });
    this.ticks = now; return true;
  }
}
const addPt0 = addPt;
addPt = function (pts, x, y, p, t, tx, ty) {
  const kept = addPt0(pts, x, y, p, t, tx, ty);
  if (pts.holds && pts.length) { pts.lastRaw = [x, y, p, t, tx, ty]; if (pts.holds.feed(x, y, p, t, pts[pts.length - 1].d)) pts.holdsFresh = true; }
  return kept;
};
// Hatch: inside the round band, each pixel takes the lines of its nearest dab: one set of parallel lines, a second set
// at 90° where the pressure passes .45 and a third at 45° past .72 (up to Layers). Fixed: the angle is on the page, so
// strokes of one brush line up and an overlap adds nothing; Follows: the angle is to the stroke, the lines bend with it.
const HATCH_AT = [0, .45, .72], HATCH_TURN = [0, 90, 45];
function stampHatch(st, buf, size, from, M, which) {
  const P = st.R.pat, s = st.surf, pts = st.pts, w = s.w, rect = [s.w, s.h, -1, -1];
  const best = M.best ??= lazyBuf(s, "nmBest" + which, Infinity);
  const k = Math.max(1, Math.sqrt(size / 30)), pitch = lerp(3, 18, P.pitch) * k, lw = Math.min(pitch * .75, lerp(.9, 4, P.lineW) * k);
  const sn = HATCH_TURN.map(a => Math.sin((P.angle + a) * DEG)), cs = HATCH_TURN.map(a => Math.cos((P.angle + a) * DEG));
  const r0 = size / 2, spacing = Math.max(1, r0 * .15);
  M.next ??= 0;
  for (let q = Math.max(1, from); q < pts.length; q++) {
    const p0 = pts[q - 1], p1 = pts[q], seg = p1.d - p0.d;
    let ux = p1.ux ?? (seg > 0 ? (p1.x - p0.x) / seg : 1), uy = p1.uy ?? (seg > 0 ? (p1.y - p0.y) / seg : 0);
    while (M.next <= p1.d) {
      const t = seg > 0 ? clamp((M.next - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t), d = M.next;
      const r = r0 * (P.ps ? lerp(1, .35 + .65 * pr, P.ps) : 1), dk = handDark(P.dark, pr, v, 0) * (p1.own === undefined ? 1 : lerp(p0.own, p1.own, t));
      let layers = 1; while (layers < P.layers && pr >= HATCH_AT[layers]) layers++;
      const x0 = Math.max(0, Math.floor(x - r - 1)), x1 = Math.min(w - 1, Math.ceil(x + r + 1)), y0 = Math.max(0, Math.floor(y - r - 1)), y1 = Math.min(s.h - 1, Math.ceil(y + r + 1));
      const R2 = (r + .5) * (r + .5);
      for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
        const dx = px + .5 - x, dy = py + .5 - y, d2 = dx * dx + dy * dy, i = py * w + px;
        if (d2 > R2 || d2 >= best[i]) continue;
        best[i] = d2;
        const X = P.fixed ? px + .5 : d + dx * ux + dy * uy, Y = P.fixed ? py + .5 : -dx * uy + dy * ux;
        let cov = 0;
        for (let L = 0; L < layers; L++) {
          const m = (P.fixed ? X * sn[L] + Y * cs[L] : -X * sn[L] + Y * cs[L]) / pitch + L * .37, f = Math.abs(m - Math.round(m)) * pitch, c = clamp(lw / 2 + .5 - f);
          if (c > cov) cov = c;
        }
        buf[i] = cov * clamp(r + .5 - Math.sqrt(d2)) * dk;
        if (WET && buf[i] > 0) WET[i] = d;
      }
      grow(rect, [x0, y0, x1, y1]);
      M.next += spacing;
    }
  }
  return rect;
}
function stampPattern(st, buf, size, from, which) {
  const M = st["nm" + which] ??= {};
  return st.R.pat.kind === "stipple" ? stampStipple(st, buf, size, from, M) : stampHatch(st, buf, size, from, M, which);
}

// ---------- the compose: Blend and Resist over the medium's own compose ----------
// Behind: how blank the page is, 1 on paper to 0 from mid grey down; a mark goes only where it is still blank.
const blankOf = u => smoothstep(.55, .96, u / PAPER_TONE), WAX_SHEEN = .07;
const composeOwn = composeStroke;
composeStroke = function (st, rect, phase) {
  const R = st.R, s = st.surf, blend = R.blend || "own", rp = s.resist;
  if (!R.resist && blend === "own" && !rp) return composeOwn(st, rect, phase);
  const w = s.w, x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(s.w - 1, rect[2]), y1 = Math.min(s.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const page = s.page, base = s.base, P = PAPER_TONE, kept = s.kept && st.e < 1 ? s.kept : null, kb = kept ? s.keptBase : null, ke = 1 - st.e;
  if (!R.resist && (blend === "own" || blend === "behind")) {
    composeOwn(st, rect, phase);
    for (let y = y0; y <= y1; y++) for (let i = y * w + x0, e = y * w + x1; i <= e; i++) {
      const f = (blend === "behind" ? blankOf(base[i]) : 1) * (rp ? 1 - rp[i] : 1);
      if (f === 1) continue;
      page[i] = base[i] + (page[i] - base[i]) * f;
      if (kept) kept[i] = kb[i] + (kept[i] - kb[i]) * f;
    }
    return;
  }
  // Lighten, Invert and Resist need the mark itself: the compose over blank paper gives its darkness a at each pixel.
  const paper = s.nmPaper ??= new Float32Array(s.w * s.h).fill(P), keepK = s.kept, keepKB = s.keptBase;
  s.base = paper; s.kept = null; s.keptBase = null;
  try { composeOwn(st, rect, phase); } finally { s.base = base; s.kept = keepK; s.keptBase = keepKB; }
  const rb = s.resistBase, rk = s.resistKept, rkb = s.resistKeptBase;
  const to = blend === "lighten" ? (u, a) => u + (P - u) * a : (u, a) => u + (P - 2 * u) * a;
  const tooth = R.resist ? R.lineTooth || 0 : 0, t0 = .25 + .5 * tooth;
  for (let y = y0; y <= y1; y++) for (let i = y * w + x0, e = y * w + x1; i <= e; i++) {
    let a = clamp(1 - page[i] / P); const u = base[i];
    if (R.resist) {
      // Wax (the part that can't lift) catches only the paper's high points; masking fluid, a liquid, fills the grain.
      if (tooth > 0 && ke > 0) a *= 1 - ke + ke * smoothstep(t0 - .1, t0 + .1, s.fine[i] * .65 + s.mid[i] * .35);
      // Into the resist planes: how strongly it shields, and the part the eraser can't lift. Masking fluid leaves the page
      // as it was (it shows hatched); wax lays a faint sheen of its own, one grey step, that stays in the drawing.
      // The part that can't lift adds to what was kept as the app's write rule does (ToneDocument: kept k + a·(1 − k)), at
      // most the whole shield.
      if (a > 0) { rp[i] = Math.max(rb[i], a); rk[i] = Math.min(rp[i], rkb[i] + a * ke * (1 - rkb[i])); }
      page[i] = u * (1 - WAX_SHEEN * a * ke);
      if (kept) kept[i] = kb[i] * (1 - WAX_SHEEN * a * ke);
      continue;
    }
    const f = rp ? 1 - rp[i] : 1;
    page[i] = u + (to(u, a) - u) * f;
    if (kept) { const k0 = kb[i]; kept[i] = k0 + (to(k0, a) - k0) * ke * f; }
  }
};
function resistBegin(s) {
  const n = s.w * s.h;
  s.resist ??= new Float32Array(n); s.resistKept ??= new Float32Array(n);
  (s.resistBase ??= new Float32Array(n)).set(s.resist); (s.resistKeptBase ??= new Float32Array(n)).set(s.resistKept);
}
// What else writes the page takes the shield too: Slow's settling, Float's print, a smudge's lay-down, the eraser.
const waterMul0 = Water.prototype.mul;
Water.prototype.mul = function (i, f, e) { const r = this.s.resist; if (r && r[i] > 0) f = 1 - (1 - f) * (1 - r[i]); return waterMul0.call(this, i, f, e); };
let NM_ENDING = null;
const addWet0 = Water.prototype.addWet;
Water.prototype.addWet = function (X0, Y0, rw, rh, D, ...rest) {
  const st = NM_ENDING;
  if (st && st.R.blend === "behind") {
    // A wash behind marks settles only where it landed: its suspended pigment scaled by how blank the page was.
    const base = st.surf.base, w = st.surf.w; D = Float32Array.from(D);
    for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) D[y * rw + x] *= blankOf(base[(y + Y0) * w + x + X0]);
  }
  return addWet0.call(this, X0, Y0, rw, rh, D, ...rest);
};
const bathPrint0 = Bath.prototype.print;
Bath.prototype.print = function () {
  const s = this.s, rp = s.resist;
  if (!rp) return bathPrint0.call(this);
  const was = Float32Array.from(s.page), r = bathPrint0.call(this);
  if (r) for (let y = Math.max(0, r[1]); y <= Math.min(s.h - 1, r[3]); y++) for (let i = y * s.w + Math.max(0, r[0]), e = y * s.w + Math.min(s.w - 1, r[2]); i <= e; i++)
    if (rp[i] > 0) s.page[i] = was[i] + (s.page[i] - was[i]) * (1 - rp[i]);
  s.base.set(s.page);
  return r;
};
const blankPage0 = blankPage;
// A clear page peels every resist and stops every halo.
blankPage = function (s) { blankPage0(s); s.resist = null; s.resistKept = null; if (s.halos) s.halos = []; };
const resistMarked = s => { let n = 0; if (s.resist) for (const v of s.resist) if (v >= .5) n++; return n; };

// ---------- the stroke: the new stampers, the shield on a smudge, Halo at lift ----------
const Stroke0 = Stroke;
Stroke = class extends Stroke0 {
  constructor(surf, B, size, shade, host) {
    // Any stroke that isn't Halo sets the halos still growing, where they are (as a new stroke lays a Slow wash).
    if (surf.halos && surf.halos.length && B.life.k !== "halo") haloSet(surf);
    super(surf, B, size, shade, host);
    if (this.R.resist) resistBegin(surf);
    // Stipple's Build-up: the ticks of pen time while the pen rests or lingers (the test area's ticker feeds a resting pen).
    if (this.R.pat && this.R.pat.hold > 0) this.holds = this.pts.holds = new Holds();
  }
  // A sample that only adds Build-up's ticks (a resting pen's, too close to keep) lays their dots, as the app's add does.
  add(samples) {
    if (this.holds) this.pts.holdsFresh = false;
    const r = super.add(samples);
    if (r || !this.holds || !this.pts.holdsFresh || !this.pts.length) return r;
    return this.restamp();
  }
  // Stamps what is new since the last samples, with no new sample of its own (Build-up's ticks, a Spiro's lift), as the
  // app's ModularStroke.stampNew.
  restamp() {
    const R = this.R, s = this.surf;
    let out = [s.w, s.h, -1, -1], rl = null;
    if (R.life === "wet") {
      if (R.line) { rl = this.wetLine(); out = union(out, rl); }
      if (R.body) { const rb = this.wetBody(rl); if (rb) out = union(out, rb); }
    } else {
      if (R.line) { rl = this.stampL(s.L, false, this.done); out = union(out, rl); }
      if (R.body) out = union(out, this.stampW(s.W, false, this.done));
      if (R.life === "none" && R.line && R.body) this.mergeLineWet(rl, null, 0);
    }
    if (emptyRect(out)) return null;
    grow(this.rect, out);
    if (this.fl) return null;
    composeStroke(this, out, R.life === "dry" ? "preview" : "final");
    return out;
  }
  // The test area's ticker: a resting pen's last sample again, at time t (Float's hold otherwise).
  holdTick(t) {
    if (!this.holds) return super.holdTick(t);
    const q = this.pts.lastRaw;
    return q ? this.add([[q[0], q[1], q[2], t, q[4], q[5]]]) : null;
  }
  stampL(buf, final, from, clip, win) { return this.R.lineTip === "nm" ? stampPattern(this, buf, this.R.inkSize, from, "L") : super.stampL(buf, final, from, clip, win); }
  stampW(buf, final, from) { return this.R.bodyTip === "nm" ? stampPattern(this, buf, this.R.bodySize, from, "W") : super.stampW(buf, final, from); }
  // A smudge's dab and a stump's (Smear's Soften) over masking fluid or wax: what it shields isn't there for them. Within
  // reach of the dab each shielded pixel shows them only its loose part (the page over paper by 1 − rp: the app's
  // CarryPlane keeps the shielded share in place, as it keeps a permanent one), so nothing is picked up or blended from
  // under the fluid, and what they change there lands by 1 − rp (the app's shield on the write).
  shieldDab(x, y, reach, dab) {
    const s = this.surf, rp = s.resist;
    if (!rp) return dab();
    const w = s.w, P = PAPER_TONE, held = [];
    for (let py = Math.max(0, Math.floor(y - reach)); py <= Math.min(s.h - 1, Math.ceil(y + reach)); py++)
      for (let i = py * w + Math.max(0, Math.floor(x - reach)), e = py * w + Math.min(w - 1, Math.ceil(x + reach)); i <= e; i++)
        if (rp[i] > 0) { const o = s.base[i], t = P - (P - o) * (1 - rp[i]); held.push(i, o, t); s.base[i] = t; }
    const out = dab();
    for (let k = 0; k < held.length; k += 3) { const i = held[k]; s.base[i] = held[k + 1] + (s.base[i] - held[k + 2]) * (1 - rp[i]); }
    return out;
  }
  carryDab(x, y, pr) { return this.shieldDab(x, y, this.R.carry.r + 1, () => super.carryDab(x, y, pr)); }
  stumpDab(x, y, pr, ux, uy) {
    const C = this.R.carry, tines = C.tines ? Math.max(...C.tines.map(Math.abs)) : 0;
    // The stump reads the page a blur and a quarter radius past where it writes, at each tine.
    return this.shieldDab(x, y, (C.tines ? C.tineReach + tines : C.r) + (C.blur || 0) + .25 * C.r + 2, () => super.stumpDab(x, y, pr, ux, uy));
  }
  end() {
    const R = this.R, s = this.surf;
    NM_ENDING = this;
    let r; try { r = super.end(); } finally { NM_ENDING = null; }
    if (R.pat) for (const n of ["nmBestL", "nmBestW"]) if (s[n]) clearBuf(s, s[n], this.rect, Infinity, 2);
    if (R.halo && r) { const h = haloLift(this, r); if (h) r = union(r, h); }
    return r;
  }
};
// The eraser lifts resist first: where it passes over masking fluid it takes the fluid and leaves the paint under it
// (the next pass erases as usual); the part that can't lift (wax) shields the paint from it, as it does from paint.
const Eraser0 = Eraser;
Eraser = class extends Eraser0 {
  constructor(surf, ...rest) { if (surf.halos && surf.halos.length) haloSet(surf); super(surf, ...rest); }
  disc(cx, cy, rad, ...rest) {
    const s = this.s, rp = s.resist;
    if (!rp) return super.disc(cx, cy, rad, ...rest);
    const rk = s.resistKept, w = s.w, held = [];
    for (let y = Math.max(0, Math.floor(cy - rad)); y <= Math.min(s.h - 1, Math.ceil(cy + rad)); y++) for (let x = Math.max(0, Math.floor(cx - rad)); x <= Math.min(w - 1, Math.ceil(cx + rad)); x++) {
      const i = y * w + x;
      if (this.seen[i] || (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 > rad * rad) continue;
      if (rp[i] > rk[i] + .004) { rp[i] = rk[i]; this.seen[i] = 1; }
      else if (rk[i] > 0) held.push(i, s.page[i]);
    }
    const out = super.disc(cx, cy, rad, ...rest);
    for (let k = 0; k < held.length; k += 2) { const i = held[k], o = held[k + 1]; s.page[i] = s.base[i] = o + (s.page[i] - o) * (1 - rk[i]); }
    return out;
  }
};

// ---------- the page on a canvas: the resist hatched over it, as MonoPaint shows masking fluid ----------
// MonoPaint's own (ToneDocument.maskHatch): diagonal lines 4 px wide every 12 px where the fluid shields at least 32 of
// 255, black over light paint and white over dark. On screen only: never part of the drawing.
const HATCH = 12, HATCH_LINE = 4, DASH = 24, DASH_ON = 16, DASH_SHIFT = 12; // dashed, unlike a Lines screentone (2026-10-22)
// Only what can still be lifted is hatched: wax shows by its sheen.
const hatchAt = (s, x, y, i) => s.resist && (x + y) % HATCH < HATCH_LINE && ((x - y + DASH_SHIFT) % DASH + DASH) % DASH < DASH_ON && s.resist[i] - s.resistKept[i] >= 32 / 255 ? (s.page[i] < 128 ? 255 : 0) : -1;
const showPage0 = showPage;
showPage = function (ctx, surf, rect) {
  if (!surf.resist) return showPage0(ctx, surf, rect);
  const x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(surf.w - 1, rect[2]), y1 = Math.min(surf.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const rw = x1 - x0 + 1, rh = y1 - y0 + 1, img = ctx.createImageData(rw, rh), data = img.data;
  for (let y = 0; y < rh; y++) for (let x = 0, i = (y + y0) * surf.w + x0, o = y * rw * 4; x < rw; x++, i++, o += 4) {
    const hv = hatchAt(surf, x + x0, y + y0, i), v = hv >= 0 ? hv : quantTone(surf.page[i]);
    data[o] = data[o + 1] = data[o + 2] = v; data[o + 3] = 255;
  }
  ctx.putImageData(img, x0, y0);
};
// The page's 16 greys as 0..1 (for pictures), the resist hatched when asked.
function pageGrays(s, hatch) {
  const g = new Float32Array(s.w * s.h);
  for (let y = 0; y < s.h; y++) for (let x = 0, i = y * s.w; x < s.w; x++, i++) { const hv = hatch ? hatchAt(s, x, y, i) : -1; g[i] = (hv >= 0 ? hv : quantTone(s.page[i])) / 255; }
  return g;
}

// ---------- Halo: Living Ink's Mould (physarum), grown from a Halo stroke's edge while it is wet ----------
// Scouts leave the stroke's edge, sense left, ahead and right, steer up the trail and toward other marks within Reach,
// and lay trail; the trail diffuses and fades over the band only. Where traffic runs well above the band's mean a vein
// darkens, so the veins that last are the ones between marks. It only darkens, and sets when its time is up.
function nmChamfer(own, w, h) {
  const d = new Float32Array(w * h);
  for (let i = 0; i < d.length; i++) d[i] = own[i] >= .5 ? 0 : 1e9;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = y * w + x; let v = d[i];
    if (x > 0) v = Math.min(v, d[i - 1] + 1);
    if (y > 0) { v = Math.min(v, d[i - w] + 1); if (x > 0) v = Math.min(v, d[i - w - 1] + 1.414); if (x < w - 1) v = Math.min(v, d[i - w + 1] + 1.414); }
    d[i] = v;
  }
  for (let y = h - 1; y >= 0; y--) for (let x = w - 1; x >= 0; x--) {
    const i = y * w + x; let v = d[i];
    if (x < w - 1) v = Math.min(v, d[i + 1] + 1);
    if (y < h - 1) { v = Math.min(v, d[i + w] + 1); if (x < w - 1) v = Math.min(v, d[i + w + 1] + 1.414); if (x > 0) v = Math.min(v, d[i + w - 1] + 1.414); }
    d[i] = v;
  }
  return d;
}
// Living Ink's Mould, tuned for thin pen lines: sensors 8 px ahead at ±43°, a little wander, food worth five trail; a vein
// where traffic passes 3 times the band's mean. Scouts that reach another mark carry back to their own stroke (shuttle) and
// lay a bridge trail as well: bridges darken to .7, the rest of the halo only to .2.
const HALO_AGENTS = 2400, HALO_TUNE = { sd: 8, sa: 43, wander: .18, food: 5, vein: .7, faint: .2, shuttle: 1, thr: 3 };
class Halo {
  constructor(s, st, rect) {
    const H = st.R.halo, W = s.w, R = H.reach;
    const x0 = Math.max(0, rect[0] - R - 3), y0 = Math.max(0, rect[1] - R - 3), x1 = Math.min(s.w - 1, rect[2] + R + 3), y1 = Math.min(s.h - 1, rect[3] + R + 3);
    const w = x1 - x0 + 1, h = y1 - y0 + 1, n = w * h;
    Object.assign(this, { s, x0, y0, w, h, R, dark: H.dark, e: st.e, behind: st.R.blend === "behind", N: H.ticks, tick: 0, rect: [x0, y0, x1, y1] });
    // The stroke's own mark (its darkness over what it found, for its own darkness), and other marks as found.
    const own = this.own = new Float32Array(n), env = this.env = new Float32Array(n);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const g = (y + y0) * W + x + x0, b = s.base[g];
      own[y * w + x] = clamp((1 - s.page[g] / Math.max(1, b)) / Math.max(.05, H.dark));
      env[y * w + x] = 1 - b / PAPER_TONE;
    }
    const dist = this.dist = nmChamfer(own, w, h), band = [];
    for (let x = 0; x < w; x++) { dist[x] = 1e9; dist[(h - 1) * w + x] = 1e9; }
    for (let y = 0; y < h; y++) { dist[y * w] = 1e9; dist[y * w + w - 1] = 1e9; }
    for (let i = 0; i < n; i++) if (dist[i] <= R) band.push(i);
    this.band = Int32Array.from(band);
    this.T = new Float32Array(n); this.T2 = new Float32Array(n); this.F = new Float32Array(n); this.F2 = new Float32Array(n); this.acc = new Float32Array(n); this.lev = new Float32Array(n); this.app = new Float32Array(n);
    this.r = rng((st.R.pen.seed ^ hashPts(st.pts) ^ 0x51a3e01d) >>> 0);
    const edges = [];
    for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
      const i = y * w + x;
      if (own[i] >= .5 && (own[i - 1] < .5 || own[i + 1] < .5 || own[i - w] < .5 || own[i + w] < .5)) edges.push(i);
    }
    const K = Math.min(HALO_AGENTS, Math.round(band.length * (.01 + .05 * H.growth)));
    this.rate = .04 * (.6 + .8 * H.growth); this.ag = [];
    for (let k = 0; k < K && edges.length; k++) {
      const i = edges[(this.r() * edges.length) | 0], x = i % w + .5, y = (i / w | 0) + .5, [nx, ny] = this.outward(x, y), out = 1.5 + this.r() * this.r() * R * .8;
      if (!this.inBand(x + nx * out, y + ny * out)) continue;
      this.ag.push({ x: x + nx * out, y: y + ny * out, h: Math.atan2(ny, nx) + (this.r() - .5) * 2.4, wait: Math.floor(this.r() * this.N * .15) });
    }
    this.ok = this.ag.length > 0;
  }
  inBand(x, y) { return x >= 0 && y >= 0 && x < this.w && y < this.h && this.dist[(y | 0) * this.w + (x | 0)] <= this.R; }
  outward(x, y) {
    const { w, h, dist: d } = this, xi = clamp(x | 0, 1, w - 2), yi = clamp(y | 0, 1, h - 2), i = yi * w + xi, gx = d[i + 1] - d[i - 1], gy = d[i + w] - d[i - w], m = Math.hypot(gx, gy);
    if (m < 1e-6 || m > 1e6) { const a = this.r() * 6.2832; return [Math.cos(a), Math.sin(a)]; }
    return [gx / m, gy / m];
  }
  // Trail, and other marks as food: the scouts find them and the veins between thicken. Its own stroke is home, not food.
  // A scout seeking smells other marks; one carrying back smells its own stroke (home), so the traffic shuttles between them.
  sense(x, y, back) {
    if (!this.inBand(x, y)) return -1;
    const i = (y | 0) * this.w + (x | 0), food = back ? this.dist[i] <= 2 : this.env[i] > .5 && this.dist[i] > 2;
    return this.T[i] + (food ? HALO_TUNE.food : 0);
  }
  // Another Halo stroke lifted while this one grows: its mark is food here too.
  feed(o) {
    for (let y = Math.max(this.y0, o.y0); y < Math.min(this.y0 + this.h, o.y0 + o.h); y++) for (let x = Math.max(this.x0, o.x0); x < Math.min(this.x0 + this.w, o.x0 + o.w); x++) {
      const i = (y - this.y0) * this.w + x - this.x0, j = (y - o.y0) * o.w + x - o.x0;
      if (o.own[j] > this.env[i]) this.env[i] = o.own[j];
    }
  }
  // One tick (ten a second): vigour falls as it dries, so growth slows to nothing. Returns the page rectangle changed.
  step() {
    if (this.tick >= this.N) return null;
    const v = Math.pow(1 - this.tick / this.N, 1.4), { T, w } = this, SD = HALO_TUNE.sd, SA = HALO_TUNE.sa * DEG;
    for (let sub = 0; sub < 2; sub++) for (const a of this.ag) {
      if (a.wait > 0) { if (sub === 0) a.wait--; continue; }
      const bk = HALO_TUNE.shuttle && a.back;
      const F = this.sense(a.x + Math.cos(a.h) * SD, a.y + Math.sin(a.h) * SD, bk);
      const Lv = this.sense(a.x + Math.cos(a.h - SA) * SD, a.y + Math.sin(a.h - SA) * SD, bk);
      const Rv = this.sense(a.x + Math.cos(a.h + SA) * SD, a.y + Math.sin(a.h + SA) * SD, bk);
      if (F >= Lv && F >= Rv) { } else if (Lv > Rv) a.h -= SA * .5; else if (Rv > Lv) a.h += SA * .5; else a.h += this.r() < .5 ? -SA * .5 : SA * .5;
      a.h += (this.r() - .5) * HALO_TUNE.wander;
      const sp = .3 + .8 * v, nx = a.x + Math.cos(a.h) * sp, ny = a.y + Math.sin(a.h) * sp;
      if (!this.inBand(nx, ny)) { a.h += Math.PI * (.6 + .8 * this.r()); continue; }
      a.x = nx; a.y = ny;
      const j = (ny | 0) * w + (nx | 0); T[j] += 1;
      // A scout that has found another mark lays the bridge trail too: only bridges darken in full.
      if (a.fed) this.F[j] += 1;
      if (HALO_TUNE.shuttle) { if (!a.back && this.env[j] > .5 && this.dist[j] > 2) { a.back = a.fed = true; a.h += Math.PI; } else if (a.back && this.dist[j] <= 2.5) { a.back = false; a.h += Math.PI; } }
    }
    const T2 = this.T2, F = this.F, F2 = this.F2, band = this.band, acc = this.acc, lev = this.lev, dist = this.dist;
    let sum = 0;
    for (let k = 0; k < band.length; k++) { const i = band[k]; sum += T2[i] = .88 * (T[i] * 3 + T[i - 1] + T[i + 1] + T[i - w] + T[i + w]) / 7; F2[i] = .88 * (F[i] * 3 + F[i - 1] + F[i + 1] + F[i - w] + F[i + w]) / 7; }
    const thr = .6 + HALO_TUNE.thr * sum / Math.max(1, band.length), rate = this.rate * v, changed = [];
    for (let k = 0; k < band.length; k++) {
      const i = band[k], t = T2[i]; T[i] = t; F[i] = F2[i];
      if (t > thr && dist[i] > 2) {
        acc[i] = Math.min(1, acc[i] + (t - thr) / thr * rate * smooth((dist[i] - 2) / 7));
        const a = smooth(acc[i]) * lerp(HALO_TUNE.faint, HALO_TUNE.vein, clamp(2 * F2[i] / t));
        if (a > lev[i] + .004) { lev[i] = a; changed.push(i); }
      }
    }
    this.tick++;
    return this.write(changed);
  }
  // A vein's new darkness onto the page: multiplied in (so it commutes with every glaze), shielded, kept by Erasable.
  write(changed) {
    if (!changed.length) return null;
    const s = this.s, W = s.w, P = PAPER_TONE, rp = s.resist, K = s.kept, KB = s.keptBase, e = this.e;
    let r = null;
    for (const i of changed) {
      const x = i % this.w + this.x0, y = (i / this.w | 0) + this.y0, g = y * W + x;
      let f = (1 - this.lev[i] * this.dark) / (1 - this.app[i] * this.dark);
      this.app[i] = this.lev[i];
      if (this.behind) f = 1 - (1 - f) * blankOf(s.base[g]);
      if (rp && rp[g] > 0) f = 1 - (1 - f) * (1 - rp[g]);
      s.page[g] *= f; s.base[g] *= f;
      if (K && e < 1) { const k = 1 - (1 - e) * (1 - f); K[g] = Math.min(P, K[g] * k); if (KB) KB[g] = Math.min(P, KB[g] * k); }
      r = r ? grow(r, [x, y, x, y]) : [x, y, x, y];
    }
    return r;
  }
  run() { let r = null; while (this.tick < this.N) { const d = this.step(); if (d) r = r ? union(r, d) : d; } return r; }
}
// Sets every halo still growing, where it is.
function haloSet(s) { if (s.halos) for (const h of s.halos) h.tick = h.N; s.halos = []; }
// Every growing halo, a tick at a time together, to the end (pictures and checks); returns the rectangle changed.
function haloDryAll(s) {
  let r = null;
  while (s.halos && s.halos.length) { for (const h of s.halos) { const d = h.step(); if (d) r = r ? union(r, d) : d; } s.halos = s.halos.filter(h => h.tick < h.N); }
  return r;
}
// At lift: a halo from the stroke's edge. With a host that keeps halos (the test area), it grows ten ticks a second,
// beside any other halos still growing; otherwise (a sample tile) it grows to the end at once.
function haloLift(st, r) {
  const R = st.R, s = st.surf;
  if (R.blend === "lighten" || R.blend === "invert") return null;   // veins only darken
  const h = new Halo(s, st, r);
  if (!h.ok) return null;
  for (const o of s.halos || []) o.feed(h);
  const host = st.host, live = typeof HOST !== "undefined" && host === HOST;
  if (live || (host && host.halos === true)) { (s.halos ??= []).push(h); if (live) haloTicker(s); return null; }
  return h.run();
}
function haloTicker(s) {
  if (s.nmTicking) return;
  s.nmTicking = true;
  const go = () => {
    if (TEST.surf !== s || (!(s.halos && s.halos.length) && !s.nmDirty)) { s.nmTicking = false; s.nmDirty = null; return; }
    let r = s.nmDirty || null;
    for (const h of s.halos || []) { const d = h.step(); if (d) r = r ? union(r, d) : d; }
    if (s.halos) s.halos = s.halos.filter(h => h.tick < h.N);
    if (r) { if (TEST.live) s.nmDirty = r; else { s.nmDirty = null; showPage(TEST.ctx, s, r); } }
    setTimeout(go, 1000 / TPS);
  };
  setTimeout(go, 1000 / TPS);
}

// ---------- rolls: rare, from the seed, after everything else ----------
// As Float's: each decided from the brush's seed (its own salt) rather than another draw from Random, so every other
// roll comes out exactly as before; a brush either keeps all its parts or changes one of them to the new choice.
const NM_ODDS = {
  stipple: { [WILDCARD]: 30, Fineliner: 40, Watercolor: 45 },
  hatch: { [WILDCARD]: 30, Fineliner: 35, "Dip pen": 30, Pencil: 35 },
  lighten: { [WILDCARD]: 40, Pastels: 30, Charcoal: 40, Gouache: 40 },
  behind: { Watercolor: 30, "Ink wash": 30, Gouache: 35 },
  invert: { [WILDCARD]: 80 },
  resist: { Watercolor: 60 },
  halo: { [WILDCARD]: 30, "Ink wash": 30, Fineliner: 50 }
};
const NM_SALT = { stipple: 0x5717e, hatch: 0x4a7c4, lighten: 0x119a7, behind: 0xbe41d, invert: 0x14e47, resist: 0x7e515, halo: 0x4a10, core: 0x3c0e, rake: 0x4a4e };
function rollNew(style, B, lk, old) {
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i);
  const hit = key => { const n = NM_ODDS[key][style]; return !!n && u(key, 0) < 1 / n; };
  const free = part => !(old && lk.parts.has(part));
  if (free("tip")) {
    if (hit("stipple")) {
      B.tip = switchChoice("tip", B.tip, "stipple"); const sponge = B.medium.k === "wash";
      Object.assign(B.tip.d, { dot: sponge ? lerp(.35, .55, u("stipple", 1)) : lerp(.35, .65, u("stipple", 1)), scatter: lerp(.2, .8, u("stipple", 2)), jitter: sponge ? lerp(.7, 1, u("stipple", 3)) : u("stipple", 3) });
    } else if (hit("hatch")) {
      B.tip = switchChoice("tip", B.tip, "hatch");
      Object.assign(B.tip.d, { pitch: lerp(.2, .6, u("hatch", 1)), lineW: lerp(.15, .45, u("hatch", 2)), hangle: lerp(30, 60, u("hatch", 3)), headTurn: u("hatch", 4) < .75 ? 1 : 0 });
    }
  }
  if (free("medium")) {
    if (hit("resist")) { B.medium = switchChoice("medium", B.medium, "resist"); B.medium.d.erase = u("resist", 1) < .7 ? 1 : 0; }
    else if (hit("lighten")) B.medium.d.blend = 1;
    else if (hit("behind")) B.medium.d.blend = 2;
    else if (hit("invert")) B.medium.d.blend = 3;
  }
  if (free("life") && B.life.k !== "float" && hit("halo")) {
    B.life = switchChoice("life", B.life, "halo"); Object.assign(B.life.d, { reach: lerp(.3, .8, u("halo", 1)), growth: lerp(.3, .8, u("halo", 2)) });
  }
  // The owner's "internal line, weighted less often" (ModularStyle.rollCore): a mark with a pen line through its heart keeps
  // it one roll in four (Ink wash two); without it a wash loses its line and wet ink goes .9–1 wet (up to its style's top).
  const md = B.medium.d;
  if (NM.coreRolls && free("medium") && !lk.dials.has("medium.line") && !(u("core", 0) < 1 / (style === "Ink wash" ? 2 : 4))) {
    if (B.medium.k === "wash" && md.line > 0) md.line = 0;
    else if (B.medium.k === "ink" && md.wet >= .35 && md.line > 0) {
      const st = MSTYLES[style] || MSTYLES[WILDCARD], spec = st.d && st.d["medium.wet"], hi = !spec ? 1 : Array.isArray(spec) ? spec[1] : spec.hi;
      md.line = 0; md.wet = Math.max(md.wet, lerp(Math.min(.9, hi), hi, u("core", 1)));
    }
  }
  // And multiline nibs (ModularStyle.rollRake): a rolled Rake stays one in three, else a Round tip.
  if (NM.coreRolls && free("tip") && B.tip.k === "multi" && !(u("rake", 0) < 1 / 3)) B.tip = switchChoice("tip", B.tip, "round");
}
const rollStyle0 = rollStyle;
rollStyle = function (style, old, locks) {
  const B = rollStyle0(style, old, locks);
  // The name said again with the new choices in: one that adds a Stipple or a Resist says so (parts.js describedName).
  if (NM.rolls) { rollNew(style, B, locks || { parts: new Set(), dials: new Set() }, old); normBrush(B); B.name = describedName(B, B.name); }
  return B;
};
// Variations never swap to a new choice (as they never pick Float's odds): the new choices are left out of the swap.
const makeVars0 = makeVars;
makeVars = function (B, locks) {
  const out = {}, cut = { tip: ["stipple", "hatch"], medium: ["resist"], life: ["halo"], carry: ["water"] };
  for (const [part, ks] of Object.entries(cut)) { const keep = CHOICES[part].slice(); out[part] = keep; CHOICES[part] = keep.filter(c => !ks.includes(c.k) || c.k === B[part].k); }
  try { return makeVars0(B, locks); } finally { for (const part of Object.keys(out)) CHOICES[part] = out[part]; }
};

// ---------- words (docs/TEXT-STYLE.md): only what is unusual ----------
const lc1 = t => t.charAt(0).toLowerCase() + t.slice(1);
const WHITE_WORDS = { ink: "White ink", wash: "White wash", cover: "White paint", charcoal: "Chalk", graphite: "White pencil" };
// Each medium's plain noun: what a stipple or hatching lays, and Behind's and Invert's card words, short enough for the
// app's module to keep one line ("Wash behind", "Invert ink").
const NM_BASE = { ink: "Ink", wash: "Wash", cover: "Paint", charcoal: "Charcoal", graphite: "Pencil" };
const choiceWords0 = choiceWords;
choiceWords = function (B, part) {
  if (part === "medium") {
    if (B.medium.k === "resist") return (B.medium.d.erase ?? 1) < .5 ? "Wax resist" : "Masking fluid";
    const bl = B.medium.d.blend | 0, w0 = choiceWords0(B, part);
    return bl === 1 ? WHITE_WORDS[B.medium.k] : bl === 2 ? NM_BASE[B.medium.k] + " behind" : bl === 3 ? "Invert " + NM_BASE[B.medium.k].toLowerCase() : w0;
  }
  if (part === "life" && B.life.k === "halo") return "Mold " + dryingSec(B) + " s";
  return choiceWords0(B, part);
};
// The brush as the old words know it: a round tip for a pattern, a quick line of ink for resist, Quick for Halo.
function nmPlain(B) {
  const A = cloneBrush(B);
  if (PATTERN_TIPS.has(B.tip.k)) A.tip = Object.assign(freshPart("tip", "round"), { d: Object.assign(freshPart("tip", "round").d, { weight: B.tip.d.weight ?? 1 }) });
  if (B.medium.k === "resist") { A.medium = freshPart("medium", "ink"); A.medium.d.erase = 0; }
  if (B.life.k === "halo") A.life = freshPart("life", "wet");
  A.medium.d.blend = 0;
  return normBrush(A);
}
const captionItems0 = captionItems;
captionItems = function (B) {
  if (!isNew(B)) return captionItems0(B);
  let items = captionItems0(nmPlain(B));
  // Resist is read as ink to word the rest: keep only the items an ink and a wash would both give (no medium tag).
  if (B.medium.k === "resist") { const A = nmPlain(B); A.medium = freshPart("medium", "wash"); const other = captionItems0(normBrush(A)); items = items.filter(t => other.includes(t)); }
  const bl = B.medium.k === "resist" ? 0 : B.medium.d.blend | 0, add = [];
  if (B.medium.k === "resist") add.push((B.medium.d.erase ?? 1) < .5 ? "wax" : "resist");
  if (bl) add.push(["", "white", "behind", "invert"][bl]);
  if (PATTERN_TIPS.has(B.tip.k)) add.push(B.tip.k);
  if (B.life.k === "halo") add.push("mold");
  items.splice(1, 0, ...add);
  return items;
};
const plainWords0 = plainWords;
plainWords = function (B) {
  if (!isNew(B)) return plainWords0(B);
  const A = nmPlain(B), mk = B.medium.k, tk = B.tip.k, bl = mk === "resist" ? 0 : B.medium.d.blend | 0;
  let text = plainWords0(A);
  const cut = text.indexOf(". "), first = cut < 0 ? text : text.slice(0, cut + 1), rest = cut < 0 ? [] : text.slice(cut + 2).split(/(?<=\.)\s+/).filter(Boolean);   // "Wet for 6.7 s." stays whole
  // The noun: what it lays, before " on …" or ", …".
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(first), noun0 = m ? m[1] : first.slice(0, -1), tail = m && m[2] ? m[2] : "";
  const base = NM_BASE[mk];
  let noun = noun0;
  if (mk === "resist") noun = (B.medium.d.erase ?? 1) < .5 ? "Wax resist" : "Masking fluid";
  else if (tk === "stipple") noun = mk === "wash" || mk === "cover" ? "Sponged " + lc1(base) : base + " stipple";
  else if (tk === "hatch") noun = base + " hatching";
  // A grade isn't a noun to whiten ("HB graphite"): a white pencil.
  if (bl === 1) noun = tk !== "stipple" && tk !== "hatch" && (mk === "charcoal" || mk === "graphite") ? (mk === "charcoal" ? "White chalk" : "White pencil") : "White " + lc1(noun);
  const out = [noun + tail + "."];
  if (tk === "stipple" && (B.tip.d.hold | 0) > 0) out.push("Builds up where it lingers.");
  if (mk === "resist") out.push((B.medium.d.erase ?? 1) < .5 ? "Nothing takes on it." : "Nothing takes on it until rubbed off.");
  if (bl === 2) out.push("Goes behind marks.");
  if (bl === 3) out.push("Inverts what's under it.");
  const hw = handWords(B, 2), control = hw !== "steady" ? cap1(hw) + "." : null;
  for (const t of rest) if (t !== control) out.push(t);
  if (B.life.k === "halo") out.push("Veins bridge nearby marks for " + dryingSec(B) + " s.");
  if (control) out.push(control);
  let t2 = out[0];
  for (let i = 1; i < out.length; i++) { if (i >= 2 && t2.length + 1 + out[i].length > 260) break; t2 += " " + out[i]; }
  return t2;
};

// ---------- presets to try them with, added once at the front of the lab's library ----------
function newModPresets() {
  const make = (name, style, seed, tip, td, medium, md, life, ld, hand, paper) => {
    const B = { name, seed, style, tip: freshPart("tip", tip), medium: switchChoice("medium", null, medium), carry: freshPart("carry", "off"),
      paper: freshPart("paper", paper || "plain"), life: freshPart("life", life), hand: freshPart("hand", hand || "steady") };
    Object.assign(B.tip.d, td); Object.assign(B.medium.d, md); Object.assign(B.life.d, ld);
    return normBrush(B);
  };
  return [
    make("White gel pen", "Fineliner", 0x9e1, "round", {}, "ink", { blend: 1 }, "wet", {}),
    make("Wash behind", "Watercolor", 0xbe41, "round", {}, "wash", { blend: 2 }, "wet", {}),
    make("Stipple sponge", "Watercolor", 0x5701, "stipple", { dot: .45, scatter: .4, jitter: .9 }, "wash", { wet: .35, pool: 0 }, "wet", {}),
    make("Stipple pen", "Fineliner", 0x5702, "stipple", { dot: .5, scatter: .35, jitter: .35 }, "ink", {}, "wet", {}),
    make("Hatch pen", "Fineliner", 0x4a7, "hatch", { pitch: .3, lineW: .22, hangle: 45, headTurn: 1 }, "ink", {}, "wet", {}),
    make("Halo ink", "Ink wash", 0x4a1, "round", { taperIn: .2, taperOut: .3 }, "ink", {}, "halo", { drying: .55, reach: .55, growth: .6 }, "darker"),
    make("Masking fluid", "Watercolor", 0x3a5, "round", { weight: 1 }, "resist", { erase: 1 }, "wet", {}),
    make("Wax resist", "Watercolor", 0x3a6, "round", { dry: .35 }, "resist", { erase: 0 }, "wet", {}, "steady", "cold press")
  ];
}
if (typeof addErasePresets === "function") {
  const addErase0 = addErasePresets;
  // Behind Erasable's three at the front.
  addErasePresets = function () { const add = newModPresets().filter(b => !S.library.some(x => x.name === b.name)); S.library.unshift(...add); addErase0(); };
}

// A white brush's samples (tiles, cards, the corner) draw on a dark ground: on paper they would show nothing. A resist's
// show by what refuses to take: no band under it, and a wash of the band's grey over the whole sample once it is laid,
// which takes only where it can (the app's WildSample.washedOver, on the wash layer under wax's sheen).
const RESIST_GROUND = 150;
function washOver(s, tone) {
  const rp = s.resist, k = 1 - tone / PAPER_TONE;
  for (let i = 0; i < s.page.length; i++) { s.page[i] *= 1 - k * (rp ? 1 - rp[i] : 1); s.base[i] = s.page[i]; }
}
if (typeof renderSample === "function" && typeof sampleSurf === "function") {
  const renderSample0 = renderSample, sampleSurf0 = sampleSurf, layStroke0 = layStroke; let darkGround = false, resistSample = false;
  renderSample = function (c, B, role) {
    darkGround = B.medium.k !== "resist" && (B.medium.d.blend | 0) === 1; resistSample = B.medium.k === "resist";
    try { return renderSample0(c, B, role); } finally { darkGround = false; resistSample = false; }
  };
  sampleSurf = function (w, h) { const s = sampleSurf0(w, h); if (darkGround) { s.page.fill(68); s.base.set(s.page); } return s; };
  layStroke = function (surf, B, pts, size, shade, host) {
    if (!resistSample || B.medium.k !== "resist") return layStroke0(surf, B, pts, size, shade, host);
    blankPage(surf);
    const out = layStroke0(surf, B, pts, size, shade, host);
    washOver(surf, RESIST_GROUND);
    return out;
  };
}

// Blend does nothing on water (every medium floats as a darkening film): greyed on a Float brush, as the lab greys any
// dial that does nothing.
if (typeof dialActive === "function") { const dialActive0 = dialActive; dialActive = (B, part, key) => !(part === "medium" && key === "blend" && B.life.k === "float") && dialActive0(B, part, key); }
// A choice dial whose options don't fit their row (four on the Nomad) becomes a menu button: nothing is clipped.
function fitSegs(root) {
  if (!root) return;
  for (const seg of root.querySelectorAll(".pseg")) {
    const bs = [...seg.querySelectorAll("button")];
    if (!bs.length || !bs.some(b => b.scrollWidth > b.clientWidth + 1)) continue;
    const { part, key } = bs[0].dataset, sel = document.createElement("select");
    sel.className = "psel"; sel.disabled = bs[0].disabled; sel.setAttribute("aria-label", dialLabel(part, cur()[part].k, key));
    bs.forEach((b, i) => { const o = document.createElement("option"); o.value = String(i); o.textContent = b.textContent; o.selected = b.getAttribute("aria-pressed") === "true"; sel.appendChild(o); });
    sel.addEventListener("change", () => ACT.dialchoice({ dataset: { part, key, i: sel.value } }));
    seg.replaceChildren(sel);
  }
}
if (typeof renderCol === "function" && typeof document !== "undefined") {
  const renderCol0 = renderCol;
  renderCol = function () { renderCol0(); fitSegs(document.getElementById("col")); };
  document.head.insertAdjacentHTML("beforeend", "<style>.psel { width: 100%; height: 24px; border: 2px solid var(--ink); border-radius: 0; background: var(--screen); color: var(--ink); font: inherit; font-size: 11px; font-weight: 700; text-transform: uppercase; padding: 0 4px; }</style>");
}

// ---------- checks (in RUN CHECKS and Node) ----------
function newModChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 360, H = 240, q = quantTone, P = PAPER_TONE;
  const mk = (tip, medium, md, life, td, hand) => {
    const B = { name: "n", seed: 17, style: "Fineliner", tip: freshPart("tip", tip || "round"), medium: switchChoice("medium", null, medium || "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", life || "wet"), hand: freshPart("hand", hand || "steady") };
    Object.assign(B.medium.d, md || {}); Object.assign(B.tip.d, td || {});
    return normBrush(B);
  };
  const line = (x0, y0, x1, y1, p = .8, n = 90) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p: typeof p === "function" ? p(k / n) : p, t: k * 6, tx: NaN, ty: NaN }));
  const band = (s, tone) => { for (let y = 80; y < 160; y++) for (let x = 0; x < W; x++) s.page[y * W + x] = tone; s.base.set(s.page); };
  const hashPage = s => { let h = 0x811c9dc5; for (let i = 0; i < s.page.length; i++) h = Math.imul(h ^ q(s.page[i]), 16777619); return h >>> 0; };
  const count = (s, f, x0 = 0, y0 = 0, x1 = W, y1 = H) => { let n = 0; for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) if (f(q(s.page[y * W + x]), y * W + x)) n++; return n; };
  // 1. Codes: every old one byte for byte; Blend written only when not the medium's own; the new choices round-trip.
  { const olds = [...LEGACY_CODES.map(c => encodeM(readAnyCode(c).b)), "steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud",
      "messy-stick.M51eueei000005bd4at_3nbcw00we_0_2tz_1jz_8syjs00y8j900000000nz0.t4mf73", "f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_0000000000000000000nz0.7"];
    t("old codes re-encode byte for byte", olds.every(c => encodeM(readAnyCode(c).b) === c));
    const ink = mk(), white = mk("round", "ink", { blend: 1 }), seg = c => c.split("_")[1];
    t("Blend is written only when not the medium's own (" + seg(encodeM(ink)) + " → " + seg(encodeM(white)) + ")", encodeM(ink) === encodeM(readAnyCode(encodeM(ink)).b) && seg(encodeM(white)).endsWith("01") && seg(encodeM(white)).length === seg(encodeM(ink)).length + 1);
    const news = [mk("stipple"), mk("hatch"), mk("round", "resist", { erase: 0 }), mk("round", "ink", {}, "halo"), mk("round", "cover", { blend: 2 }), white];
    t("the new choices round-trip (" + news.map((b, i) => encodeM(b).split("_")[[0, 0, 1, 4, 1, 1][i]].replace(/^.*\.M./, "")).join(" ") + ")", news.every(b => { const c = encodeM(b), r = readAnyCode(c); return r && encodeM(r.b) === c; })); }
  // 2. Rolls: with the new rolls off, every roll is as before; on, a brush either is the same or changes one part to a new choice.
  // The core line and the rake (not a new choice, but rolled here too) are off for both, and Flick and Fling (dials, below).
  { const roll = on => { const keep = Math.random, k0 = NM.rolls, k1 = NM.coreRolls, k2 = NM.flickRolls, k3 = NM.flingRolls, k4 = NM.oilRolls, k5 = NM.fadeRolls, k6 = NM.lemonRolls; Math.random = rng(9191); NM.rolls = on; NM.coreRolls = NM.flickRolls = NM.flingRolls = NM.oilRolls = NM.fadeRolls = NM.lemonRolls = false;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.rolls = k0; NM.coreRolls = k1; NM.flickRolls = k2; NM.flingRolls = k3; NM.oilRolls = k4; NM.fadeRolls = k5; NM.lemonRolls = k6; } };
    const a = roll(false), b = roll(true); let same = 0, hits = 0, odd = 0;
    for (let i = 0; i < a.length; i++) { if (encodeM(a[i]) === encodeM(b[i])) { same++; continue; } hits++; if (!isNew(b[i])) odd++; }
    t("rolls: " + same + " of " + a.length + " as before, " + hits + " with one new choice, none else changed", odd === 0 && hits > 10 && hits < a.length * .15); }
  // 3. Lighten: white over a dark band, nothing on paper; Darken (the default) is today's compose.
  { const s = modSurface(W, H, 7); band(s, 51); layStroke(s, mk("round", "ink", { blend: 1 }), line(20, 60, 340, 180), 8, 1, null);
    const lit = count(s, (v, i) => v >= 204 && Math.floor(i / W) >= 80 && Math.floor(i / W) < 160), onPaper = count(s, (v, i) => v < 238 && (Math.floor(i / W) < 76 || Math.floor(i / W) > 163));
    t("a white gel line over a dark wash (" + lit + " px near white), nothing on blank paper (" + onPaper + " px)", lit > 300 && onPaper === 0); }
  // 4. Behind: a wash behind a light ink line leaves the line's grey; the same wash glazed over it darkens it.
  { const draw = bl => { const s = modSurface(W, H, 7); layStroke(s, mk(), line(30, 120, 330, 120), 6, .53, null); layStroke(s, mk("round", "wash", { blend: bl }), line(20, 120, 340, 120), 90, 1, null); return s; };
    const own = draw(0), behind = draw(2), lineTone = s => q(s.page[120 * W + 180]), paper = s => q(s.page[95 * W + 60]);
    t("a wash behind keeps a light line's grey (" + lineTone(behind) + " vs " + lineTone(own) + " glazed), the wash as dark on paper (" + paper(behind) + " = " + paper(own) + ")", lineTone(behind) > lineTone(own) + 20 && paper(behind) === paper(own)); }
  // 5. Invert: paper turns dark, a dark band light.
  { const s = modSurface(W, H, 7); band(s, 34); layStroke(s, mk("round", "cover", { blend: 3 }), line(20, 40, 340, 200), 30, 1, null);
    t("invert: paper turns black (" + q(s.page[50 * W + 40]) + "), black turns white (" + q(s.page[120 * W + 180]) + ")", q(s.page[50 * W + 40]) <= 34 && q(s.page[120 * W + 180]) >= 204); }
  // 6. Resist: a wash leaves masking fluid's pixels as they were; one eraser pass lifts the fluid and leaves the paint; a
  //    second wash then takes there. Wax (Erasable off) stays, and shields what's under it from the eraser.
  { const s = modSurface(W, H, 7), fluid = mk("round", "resist", { erase: 1 }), wax = mk("round", "resist", { erase: 0 }), wash = mk("round", "wash", { wet: .3 });
    layStroke(s, fluid, line(40, 70, 320, 70), 24, 1, null); layStroke(s, wax, line(40, 170, 320, 170), 24, 1, null);
    const before = Float32Array.from(s.page); layStroke(s, wash, line(10, 120, 350, 120), 300, 1, null);
    const under = i => Math.abs(s.page[i] - before[i]) < .5, fl = 70 * W + 180, wx = 170 * W + 180, open = 120 * W + 180;
    t("a wash doesn't take on masking fluid or wax (" + (under(fl) && under(wx)) + "), it does beside them (" + q(s.page[open]) + ")", under(fl) && under(wx) && q(s.page[open]) < 230);
    const er = new Eraser(s, 60, false); for (let k = 0; k <= 60; k++) er.add([[20 + 5 * k, 70, 1, k * 6, NaN, NaN]]); for (let k = 0; k <= 60; k++) er.add([[20 + 5 * k, 170, 1, 400 + k * 6, NaN, NaN]]);
    t("one eraser pass lifts the fluid (" + s.resist[fl].toFixed(2) + ") and keeps the wax (" + s.resist[wx].toFixed(2) + ")", s.resist[fl] < .01 && s.resist[wx] > .99);
    layStroke(s, wash, line(10, 70, 350, 70), 80, 1, null);
    t("a wash after the lift takes where the fluid was (" + q(s.page[fl]) + ")", q(s.page[fl]) < 230); }
  // 6b. A smudge and a stump (Smear's Soften: Blend stump, Wash comb) over masking fluid move none of what it shields and
  //     draw nothing out from under it: a dark band under a strip of fluid stays, and the paper past the strip stays paper.
  { const codes = ["blend-stump.M50i00000000yat_3eqlj00we_3ssll00zz_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "wash-comb.M65zzlli000000000_1ulat00770000ee_3zzll00zz_000_1jz_0000000000000000000nz0.7"];
    const fluid = mk("round", "resist", { erase: 1 }), res = [];
    for (const c of codes) for (const masked of [false, true]) {
      const s = modSurface(W, H, 7), B = readAnyCode(c).b;
      for (let y = 0; y < H; y++) for (let x = 150; x < 190; x++) s.page[y * W + x] = s.base[y * W + x] = 40;
      if (masked) layStroke(s, fluid, line(170, 10, 170, 230), 70, 1, null);
      const before = Float32Array.from(s.page);
      layStroke(s, B, line(60, 120, 330, 120), 40, 1, null);
      let moved = 0, past = 0;
      for (let y = 105; y < 136; y++) for (let x = 0; x < W; x++) {
        const i = y * W + x;
        if (s.resist && s.resist[i] > .999 && Math.abs(s.page[i] - before[i]) > .01) moved++;
        if (x >= 215 && x < 255) past += before[i] - s.page[i];
      }
      res.push({ masked, moved, past });
    }
    const ok = res.every(r => r.masked ? r.moved === 0 && r.past < 1 : r.past > 100);
    t("a stump and a comb over masking fluid leave what it shields and draw nothing out from under it (" + res.map(r => (r.masked ? "fluid " : "bare ") + Math.round(r.past)).join(", ") + ")", ok); }
  // 7. Stipple: dots, more with pressure; the same samples batched differently lay the same dots.
  { const st = mk("stipple", "ink", {}, "wet", { dot: .3, scatter: .4, jitter: .5 }), dark = p => { const s = modSurface(W, H, 7); layStroke(s, st, line(20, 120, 340, 120, p), 16, 1, null); return count(s, v => v < 128); };
    const lo = dark(.2), hi = dark(.95);
    const once = (n) => { const s = modSurface(W, H, 7), S = new Stroke(s, st, 16, 1, null), pts = line(20, 120, 340, 120, u => .3 + .6 * u); for (let i = 0; i < pts.length; i += n) S.add(pts.slice(i, i + n).map(p => [p.x, p.y, p.p, p.t, p.tx, p.ty])); S.end(); return hashPage(s); };
    t("stipple: " + lo + " dark px at light pressure, " + hi + " pressed; batched 1 or 7 a time, the same dots", hi > lo * 2.5 && lo > 40 && once(1) === once(7)); }
  // 7b. Build-up: a pen resting a second lays more dots the faster it builds, none when off; the test area's tick (the
  //     last sample again) lays what the samples would.
  { const held = (hold, tick) => { const s = modSurface(W, H, 7), S = new Stroke(s, mk("stipple", "ink", {}, "wet", { dot: .5, scatter: .35, jitter: .35, hold }), 24, 1, null);
      S.add([[180, 120, .6, 1000, NaN, NaN]]); for (let t = 10; t <= 1000; t += 10) { if (tick) S.holdTick(1000 + t); else S.add([[180, 120, .6, 1000 + t, NaN, NaN]]); } S.end(); return count(s, v => v < 128); };
    const off = held(0, false), slow = held(1, false), fast = held(2, false);
    t("build-up: resting a second, " + off + " dark px off, " + slow + " slow, " + fast + " fast; ticked, the same", off > 0 && slow > 1.5 * off && fast > 1.5 * slow && held(2, true) === fast && held(0, true) === off); }
  // 8. Hatch: crisp lines (few in-between greys), pressure crosses them, a fixed angle lines strokes up.
  { const ht = mk("hatch", "ink", {}, "wet", { pitch: .35, lineW: .25, hangle: 45, headTurn: 1 });
    const lay1 = (p, ys) => { const s = modSurface(W, H, 7); for (const y of ys) layStroke(s, ht, line(20, y, 340, y, p), 60, 1, null); return s; };
    const a = lay1(.3, [120]), b = lay1(.95, [120]), da = count(a, v => v < 128), db = count(b, v => v < 128), grey = count(b, v => v > 34 && v < 221);
    const rows = (v, i) => v < 128 && Math.floor(i / W) > 100 && Math.floor(i / W) < 135, one = count(lay1(.3, [110]), rows), two = count(lay1(.3, [110, 125]), rows);
    const batched = n => { const s = modSurface(W, H, 7), S = new Stroke(s, mk("hatch", "ink", {}, "wet", { headTurn: 0 }), 40, 1, null), pts = line(20, 100, 340, 160, u => .2 + .75 * u);
      for (let i = 0; i < pts.length; i += n) S.add(pts.slice(i, i + n).map(p => [p.x, p.y, p.p, p.t, p.tx, p.ty])); S.end(); return hashPage(s); };
    t("hatch: " + da + " dark px light, " + db + " pressed (crossed), " + Math.round(grey / Math.max(1, db) * 100) + "% in-between greys; overlapping strokes line up (" + one + " → " + two + " dark px); batched 1 or 7 a time, the same lines",
      db > da * 1.6 && grey < db * .9 && two < one * 1.35 && batched(1) === batched(7)); }
  // 9. Halo: veins reach a mark within Reach and bridge to it; nothing past Reach; a stroke that isn't Halo sets it.
  { const halo = mk("round", "ink", {}, "halo", {}, "steady"); halo.life.d.reach = .6; halo.life.d.growth = .7;
    const make = gap => { const s = modSurface(W, H, 7); layStroke(s, mk(), line(20, 120 + gap, 340, 120 + gap), 6, 1, null); const before = Float32Array.from(s.page);
      layStroke(s, halo, line(20, 120, 340, 120), 6, 1, null); let between = 0, far = 0;
      for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = y * W + x; if (q(s.page[i]) < q(before[i]) - 16) { if (y > 124 && y < 120 + gap - 4) between++; if (y < 40) far++; } } return { between, far }; };
    const near = make(28);
    t("halo: veins bridge a line 28 px off (" + near.between + " px between) and stay within reach (" + near.far + " px far off)", near.between > 60 && near.far === 0);
    const s = modSurface(W, H, 7), host = { halos: true }; layStroke(s, halo, line(20, 120, 340, 120), 6, 1, host); const h = s.halos[0];
    for (let k = 0; k < 5; k++) h.step(); layStroke(s, mk(), line(20, 200, 340, 200), 6, 1, host);
    t("a stroke that isn't Halo sets the halo where it is (" + h.tick + " of " + h.N + " ticks)", !s.halos.length && h.tick === h.N); }
  return out;
}
const selfTest0 = selfTest;
selfTest = function () { const r = selfTest0(); r.newmods = newModChecks(); r.ok = r.ok && r.newmods.every(c => c.ok); NM.last = r.newmods; return r; };
// Erasable's check hashes rolls from before the new choices: it runs with their rolls off.
const eraseChecks0 = eraseChecks;
eraseChecks = function () { const k = NM.rolls; NM.rolls = false; try { return eraseChecks0(); } finally { NM.rolls = k; } };
if (typeof runChecks === "function") {
  const runChecks0 = runChecks;
  runChecks = function () {
    runChecks0();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.last) return;
      out.textContent += "\n\nNew modules (Blend, Stipple, Hatch, Resist, Mold):\n" + NM.last.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 30);
  };
}

// ===================================================================================================================
// Screen and Tone (the mischief modules spec, Stage 1; mischief-lab.html's buildScreen and bit1; the app's PrintScreen):
//   Screen  a Medium dial on every medium with Blend: none, halftone, line screen, dither or grain, with Cell, Angle and
//           the newsprint dials Misregister, Ink gain and Uneven. The mark's own darkness passes a threshold fixed to the
//           page: pure black (White's white, Invert's opposite) or the page as found (opaque paint lays white there).
//   Tone    a Medium choice, a screentone: the brush lays a fill, and what shows is dots, lines, sand or a gradient at
//           its Tone over it, in black; the eraser scrapes it in streaks. Laid as a crisp ink line, always erasable.
// Appended (dials after Blend, Tone the medium's seventh choice), written only when not default: old codes as before.
// ===================================================================================================================
// The Screen dial's name wherever the lab shows it (the app's PrintScreen.LABEL; its key stays "screen").
const SCREEN_LABEL = "Print Shading";
Object.assign(DIALS.medium, {
  screen: { label: SCREEN_LABEL, choices: ["None", "Halftone", "Engraving", "Dither", "Grain", "Rosette", "Ben-Day", "Crosshatch", "Wavy Lines", "Mezzotint", "Diamond Dots", "Stipple", "Woodcut", "Scribble", "Hatching"], def: 0 },
  cell: { label: "Pattern Size", min: 3, max: 16, def: 6, int: true, fmt: v => Math.round(v) + " px" },
  sangle: { label: "Angle", min: 0, max: 90, def: 45, fmt: v => Math.round(v) + "°" },
  mis: { label: "Double Print", min: 0, max: 8, def: 0, int: true, fmt: v => v < .5 ? "off" : Math.round(v) + " px" },
  gain: { label: "Dot Swell", min: 0, max: 1, def: 0 },
  uneven: { label: "Patchy Ink", min: 0, max: 1, def: 0 },
  pattern: { label: "Pattern", choices: ["Dots", "Lines", "Sand", "Gradient", "Crosshatch", "Wavy Lines", "Mezzotint", "Stipple", "Woodcut", "Scribble", "Hatching"], def: 0 },
  ttone: { label: "Darkness", min: .05, max: 1, def: .45 }
});
const SCREEN_DIALS = ["screen", "cell", "sangle", "mis", "gain", "uneven"], UNDER_SCREEN = new Set(SCREEN_DIALS.slice(1));
for (const c of CHOICES.medium) if (c.dials.includes("blend")) c.dials.push(...SCREEN_DIALS);
CHOICES.medium.push({ k: "tone", label: "Screentone", dials: ["pattern", "ttone", "cell", "sangle"], macro: "ttone", words: ["light", "dark"],
  blurb: "A screentone: dots, lines, sand or a gradient laid over the fill. Scrapes off in streaks." });
for (const k of [...SCREEN_DIALS, "pattern", "ttone"]) ADDED.add("medium." + k);
REAL_ERASE.tone = 1;
// Appended after the first four (the app's PrintScreen.Kind): rosette, Ben-Day, crosshatch, wavy lines, mezzotint, diamond
// dots, then stipple, woodcut, scribble and hatching; Screentone's last seven are the twins of crosshatch to hatching.
const SCREEN_KINDS = ["halftone", "lines", "dither", "grain", "rosette", "benday", "crosshatch", "wavy", "mezzotint", "diamond", "stipple", "woodcut", "scribble", "hatching"],
  TONE_KINDS = ["dots", "tlines", "sand", "gradient", "crosshatch", "wavy", "mezzotint", "stipple", "woodcut", "scribble", "hatching"], SCREEN_GHOST = .45;
const ROSETTE_OFF = [-30, 30, -45, 0];
// The screen a medium lays through, or null (the app's PrintScreen.of).
function screenOf(M) {
  const d = M.d, cell = d.cell ?? 6, angle = d.sangle ?? 45, tone = M.k === "tone", sc = tone ? 0 : d.screen | 0;
  if (!tone && !sc) return null;
  const a = angle * DEG, mis = tone ? 0 : d.mis || 0;
  return { kind: tone ? TONE_KINDS[d.pattern | 0] : SCREEN_KINDS[sc - 1], tone, cell, ca: Math.cos(a), sa: Math.sin(a), inv: 1 / Math.max(1, cell),
    misX: Math.round(mis * .8), misY: Math.round(mis * .6), gain: tone ? 0 : d.gain || 0, uneven: tone ? 0 : d.uneven || 0, level: tone ? d.ttone ?? .45 : 1,
    grain: Math.max(1, Math.round(cell / 6)),
    rot: ROSETTE_OFF.map(o => [Math.cos((angle + o) * DEG), Math.sin((angle + o) * DEG)]) };
}
// mischief-lab.html's hash: page pixel x, y and salt s, 0 to 1.
function screenHash(x, y, s) {
  let h = Math.imul(x | 0, 0x27d4eb2d) ^ Math.imul(y | 0, 0x165667b1) ^ Math.imul(s | 0, 0x9e3779b1);
  h = Math.imul(h ^ (h >>> 15), 0x85ebca6b); h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}
const BAYER8 = (() => { let m = [[0]]; while (m.length < 8) { const k = m.length, q = [[0, 2], [3, 1]], o = []; for (let y = 0; y < 2 * k; y++) { o.push([]); for (let x = 0; x < 2 * k; x++) o[y].push(4 * m[y % k][x % k] + q[(y / k) | 0][(x / k) | 0]); } m = o; } return m.flat(); })();
// Ben-Day (the app's PrintScreen): a round dot at every site of an offset (hexagonal) lattice BENDAY_PITCH cells apart, all
// one size, growing smoothly with the tone: a pixel inks once the tone passes the share of the page its site's dot covers
// when it reaches it (BENDAY_SHARE, made on first use), solid just short of full darkness.
// Java's Math.rint: halves to the even neighbour.
const rint = x => { const f = Math.floor(x), d = x - f; return d < .5 ? f : d > .5 ? f + 1 : f % 2 === 0 ? f : f + 1; };
const BENDAY_PITCH = 1.5, BENDAY_SOLID = .997, BENDAY_REACH = 1 / Math.sqrt(3) + 1e-9;
function benDayDistance(x, y) {
  const h = Math.sqrt(3) / 2, j0 = Math.floor(y / h); let best = 9;
  for (let j = j0; j <= j0 + 1; j++) { const shift = (j & 1) * .5, sx = rint(x - shift) + shift, dx = x - sx, dy = y - j * h; best = Math.min(best, dx * dx + dy * dy); }
  return Math.sqrt(best);
}
let BENDAY_SHARE = null;
function benDayShare() {
  if (BENDAY_SHARE) return BENDAY_SHARE;
  const S = new Float64Array(1024), within = new Int32Array(S.length), n = 256, hh = Math.sqrt(3) / 2;
  for (let a = 0; a < n; a++) for (let b = 0; b < n; b++) { const d = benDayDistance((a + .5) / n, (b + .5) / n * 2 * hh); within[Math.min(S.length - 1, (d * (S.length - 1) / BENDAY_REACH) | 0)]++; }
  let sum = 0; for (let i = 0; i < S.length; i++) { sum += within[i]; S[i] = BENDAY_SOLID * sum / (n * n); }
  return BENDAY_SHARE = S;
}
const screenTri = t => Math.abs(t - Math.floor(t) - .5) * 2;
// Mezzotint: dashes lying along the angle in rows a third of a cell wide, each row its own dash length and shift, each dash its own rank.
function screenMezzo(S, x, y) {
  const w = Math.max(1, S.cell / 3), px = x * S.ca + y * S.sa, py = -x * S.sa + y * S.ca, j = Math.floor(py / w), len = S.cell * (.6 + 1.2 * screenHash(j, 0, 51));
  return screenHash(Math.floor((px + screenHash(j, 0, 52) * len) / len), j, 53);
}
// Stipple, woodcut, scribble and hatching (the app's PrintScreen.raw and even), whose look changes as they darken: a raw
// field (lower inks first) at u, v cells in the angle's frame, evened by its own table so the share inked is the darkness.
const EVEN_FAMILY = { stipple: 0, woodcut: 1, scribble: 2, hatching: 3 }, EVEN_RAW_MOST = 2.5, EVEN_BINS = 1024, EVEN_TABLES = [];
// cos(2πt) from a 4096-step table, as the app's cos1, so every screen lands on the app's pixels.
const COS1 = Float64Array.from({ length: 4096 }, (_, i) => Math.cos(2 * Math.PI * i / 4096));
const cos1 = t => COS1[((t - Math.floor(t)) * 4096 | 0) & 4095], sin1 = t => cos1(t - .25);
const evMix = (a, b, c) => { let h = Math.imul(a, 0x27d4eb2d) ^ Math.imul(b, 0x165667b1) ^ Math.imul(c, 0x9e3779b1 | 0); h = Math.imul(h ^ (h >>> 15), 0x85ebca6b | 0); h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35 | 0); return h ^ (h >>> 16); };
const evUnit = h => { let g = Math.imul(h ^ (h >>> 16), 0x45d9f3b); g ^= g >>> 16; return (g >>> 0) / 4294967296; };
// Hatching's three directions off the angle (the app's HatchStamper.TURN); where each scribble and hatching set starts.
const EV_TURN = HATCH_TURN.map(t => [Math.cos(t * DEG), Math.sin(t * DEG)]), SHADE_SCRIBBLE_AT = [0, .45, .8], SHADE_HATCH_AT = [0, .3, .55, .8, .95, 1.1], SHADE_SCRIBBLE_K = .55;
function evenRaw(k, u, v, cell) {
  const TAU = 2 * Math.PI;
  if (k === 0) {
    // Stipple: dots on a jittered lattice appear one by one, small, then grow and merge.
    const r0 = Math.max(.75, cell * .2) / cell, i0 = Math.floor(u - .5), j0 = Math.floor(v - .5); let best = 9;
    for (let i = i0; i <= i0 + 1; i++) for (let j = j0; j <= j0 + 1; j++) {
      const h = evMix(i, j, 61), sx = i + .5 + (evUnit(h) - .5) * .6, sy = j + .5 + (evUnit(Math.imul(h, 0x9e3779b1 | 0)) - .5) * .6;
      best = Math.min(best, .9 * evUnit(Math.imul(h, 0x85ebca6b | 0)) + 1.6 * Math.max(0, Math.sqrt((u - sx) * (u - sx) + (v - sy) * (v - sy)) - r0));
    }
    return best;
  }
  if (k === 1) {
    // Woodcut: a line a cell, wavering, broken at first, thickening until only broken white lines are left.
    let j = Math.floor(v), wav = 0;
    for (let pass = 0; pass < 2; pass++) {
      const h = evMix(j, 0, 71);
      wav = .2 * sin1(u * .05 + evUnit(h)) + .07 * sin1(u * .17 + evUnit(Math.imul(h, 0x85ebca6b | 0)));
      const jj = Math.floor(v + wav); if (jj === j) break; j = jj;
    }
    const h = evMix(j, 0, 73), b = .5 + .3 * sin1(u * .06 + evUnit(h)) + .2 * sin1(u * .19 + evUnit(Math.imul(h, 0x9e3779b1 | 0)));
    return screenTri(v + wav) + .55 * (b - .5) + .3;
  }
  if (k === 2) {
    // Scribble: three looping scribbles, each laid over the last, as far off a smooth field's levels as its slope says.
    let best = 9;
    for (let l = 0; l < 3; l++) {
      const uu = l === 1 ? v : u, vv = l === 1 ? -u : v, ph = l * .37, a = .07 + .015 * l;
      const p1 = a * 1.3 * vv + ph, A = a * uu + .45 * sin1(p1), p2 = a * .9 * uu + 2 * ph, B = a * 1.1 * vv + .45 * sin1(p2), C = a * .7 * (uu + vv) + ph * 3;
      const cA = TAU * cos1(A), cB = TAU * cos1(B), cC = .6 * TAU * cos1(C) * a * .7, F = sin1(A) + sin1(B) + .6 * sin1(C);
      const fu = cA * a + cB * .45 * TAU * cos1(p2) * a * .9 + cC, fv = cA * .45 * TAU * cos1(p1) * a * 1.3 + cB * a * 1.1 + cC;
      const kk = F * SHADE_SCRIBBLE_K, off = Math.abs(kk - rint(kk)) / Math.max(.12, SHADE_SCRIBBLE_K * Math.sqrt(fu * fu + fv * fv));
      best = Math.min(best, SHADE_SCRIBBLE_AT[l] + 1.8 * off);
    }
    return best;
  }
  // Hatching: short strokes, a little askew, one direction, then a second and a third, then each again between its rows.
  let best = 9;
  for (let s = 0; s < 6 && SHADE_HATCH_AT[s] < best; s++) {
    const [c, sn] = EV_TURN[s % 3], odd = (s / 3) | 0, hu = u * c + v * sn, hv = -u * sn + v * c, k2 = Math.floor((hv - odd) / 2 + .5);
    const row = evMix(k2, s, 83), seg = 6, shift = evUnit(row) * seg, m = Math.floor((hu + shift) / seg), h = evMix(row, m, 89);
    const mid = (m + .5) * seg - shift + (evUnit(h) - .5) * 1.2, x = hu - mid, half = seg * (.36 + .1 * evUnit(Math.imul(h, 0x27d4eb2d)));
    const centre = 2 * k2 + odd + (evUnit(Math.imul(h, 0x9e3779b1 | 0)) - .5) * .5 + (evUnit(Math.imul(h, 0x85ebca6b | 0)) - .5) * .16 * x;
    const e = Math.abs(x) / half, d = Math.abs(hv - centre) * (1 + 2 * Math.max(0, e - .55)) + 2 * Math.max(0, Math.abs(x) - half);
    best = Math.min(best, SHADE_HATCH_AT[s] + 1.4 * d);
  }
  return best;
}
// Field k's even table, from a page of the default Pattern Size at 45 degrees, made on first use.
function evenTable(k) {
  if (EVEN_TABLES[k]) return EVEN_TABLES[k];
  const n = 384, cell = DIALS.medium.cell.def, inv = 1 / cell, ca = Math.cos(Math.PI / 4), sa = ca, count = new Int32Array(EVEN_BINS), e = new Float64Array(EVEN_BINS);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) count[clamp(Math.round(evenRaw(k, (x * ca + y * sa) * inv, (-x * sa + y * ca) * inv, cell) * (EVEN_BINS - 1) / EVEN_RAW_MOST), 0, EVEN_BINS - 1)]++;
  let sum = 0; for (let i = 0; i < EVEN_BINS; i++) { sum += count[i]; e[i] = .998 * (sum - count[i] / 2) / (n * n) + 1e-6 * i; }
  return EVEN_TABLES[k] = e;
}
function screenEvened(k, u, v, cell) {
  const e = evenTable(k), f = evenRaw(k, u, v, cell) * (EVEN_BINS - 1) / EVEN_RAW_MOST, i = clamp(Math.floor(f), 0, EVEN_BINS - 2);
  return Math.fround(e[i] + (e[i + 1] - e[i]) * clamp(f - i));
}
// The threshold at page pixel x, y (buildScreen): ink where the darkness is above it.
function screenT(S, x, y) {
  if (S.kind === "mezzotint") return screenMezzo(S, x, y);
  if (EVEN_FAMILY[S.kind] !== undefined) return screenEvened(EVEN_FAMILY[S.kind], (x * S.ca + y * S.sa) * S.inv, (-x * S.sa + y * S.ca) * S.inv, S.cell);
  if (S.kind === "dither") return (BAYER8[(y & 7) * 8 + (x & 7)] + .5) / 64;
  if (S.kind === "grain") return screenHash(Math.floor(x / S.grain), Math.floor(y / S.grain), 3);
  if (S.kind === "sand") return screenHash(x, y, 5);
  const u = (x * S.ca + y * S.sa) * S.inv, v = (-x * S.sa + y * S.ca) * S.inv;
  if (S.kind === "lines") { const vv = v + .22 * sin1(u / 9); return Math.abs(vv - Math.floor(vv) - .5) * 2; }
  if (S.kind === "tlines") return Math.abs(v - Math.floor(v) - .5) * 2;
  // Banknote waves; etching (one set to half grey, the crossing set from there to black); square dots in the screen's frame.
  if (S.kind === "wavy") return screenTri(v + .7 * sin1(u / 8));
  if (S.kind === "crosshatch") { const t1 = screenTri(v + .22 * sin1(u / 9)), t2 = screenTri(u + .22 * sin1(v / 9)); return Math.min(t1 < .5 ? t1 : 2, (1 + t2) / 2); }
  if (S.kind === "diamond") { const d = 2 * Math.max(Math.abs(u - Math.floor(u) - .5), Math.abs(v - Math.floor(v) - .5)); return d * d; }
  // Four halftones at the process-colour angles, all black: a pixel inks where any of them would.
  if (S.kind === "rosette") {
    let m = 1;
    for (const [c, s] of S.rot) m = Math.min(m, .5 - (cos1((x * c + y * s) * S.inv) + cos1((-x * s + y * c) * S.inv)) / 4);
    const q = (1 - m) * (1 - m); return 1 - q * q;
  }
  if (S.kind === "benday") { const sh = benDayShare(), d = benDayDistance(u / BENDAY_PITCH, v / BENDAY_PITCH); return sh[Math.min(sh.length - 1, Math.max(0, (d * (sh.length - 1) / BENDAY_REACH) | 0))]; }
  return .5 - (cos1(u) + cos1(v)) / 4;
}
// Whether darkness a (0 to 1) inks page pixel x, y of a page h tall, blot its soft noise (bit1 and the render's second pass).
function screenInk(S, a, x, y, h, blot) {
  if (!(a > 0)) return false;
  if (S.tone) return a * (S.kind === "gradient" ? clamp(S.level * 2 * (y + .5) / h) : S.level) > screenT(S, x, y);
  const b0 = S.uneven > 0 ? blot : 0, t = a * (1 - S.uneven * .55 * b0);
  let b = t > screenT(S, x, y);
  if (!b && S.gain > 0) {
    const n = (t > screenT(S, x - 1, y)) + (t > screenT(S, x + 1, y)) + (t > screenT(S, x, y - 1)) + (t > screenT(S, x, y + 1));
    if (n && screenHash(x, y, 17) < S.gain * (.22 + .18 * n)) b = true;
  }
  if (b && S.uneven > 0 && screenHash(x, y, 23) < S.uneven * (.04 + .14 * b0)) b = false;
  return b;
}
// Resolve: Tone as a crisp ink line (never on water, smearing nothing); a screen lays no grey after it.
const resolveNM = resolve;
resolve = function (B, size, shade) {
  const tone = B.medium.k === "tone";
  if (!tone && !(B.medium.d.screen | 0)) return resolveNM(B, size, shade);
  let A = B;
  if (tone) {
    A = cloneBrush(B);
    A.medium = { k: "ink", d: { wet: 0, line: 1, pool: 0, splatter: 0, bleed: 0, bloom: 0, lines: 0, offset: 0, chaos: 0, erase: 1, blend: 0 } };
    if (A.life.k === "float") A.life = freshPart("life", "wet");
    A.carry = freshPart("carry", "off");
  }
  const R = resolveNM(A, size, shade);
  if (!tone && B.life.k === "float") return R;
  R.screen = screenOf(B.medium); R.settle = 0; R.halo = null;
  return R;
};
// The compose: the mark's own darkness over blank paper (and its misregistered pass), through the screen.
const composeNoScreen = composeStroke;
composeStroke = function (st, rect, phase) {
  const R = st.R, S = R.screen, s = st.surf;
  if (!S) {
    composeNoScreen(st, rect, phase);
    // Paint over Tone's dots: they are paint now, which the eraser takes.
    if (s.dots) for (let y = Math.max(0, rect[1]); y <= Math.min(s.h - 1, rect[3]); y++) for (let i = y * s.w + Math.max(0, rect[0]), e = y * s.w + Math.min(s.w - 1, rect[2]); i <= e; i++) if (s.dots[i] && s.page[i] !== s.base[i]) s.dots[i] = 0;
    return;
  }
  const w = s.w, P = PAPER_TONE, dx = S.misX, dy = S.misY;
  const X0 = Math.max(0, rect[0] - dx), Y0 = Math.max(0, rect[1] - dy), X1 = Math.min(s.w - 1, rect[2] + dx), Y1 = Math.min(s.h - 1, rect[3] + dy);
  if (X1 < X0 || Y1 < Y0) return;
  const gw = X1 - X0 + 1, gh = Y1 - Y0 + 1, page = s.page, base = s.base, saved = new Float32Array(gw * gh), A = new Float32Array(gw * gh);
  for (let y = 0; y < gh; y++) for (let x = 0; x < gw; x++) saved[y * gw + x] = page[(y + Y0) * w + x + X0];
  const paper = s.nmPaper ??= new Float32Array(s.w * s.h).fill(P), keepK = s.kept, keepKB = s.keptBase;
  s.base = paper; s.kept = null; s.keptBase = null;
  try { composeOwn(st, [X0, Y0, X1, Y1], phase); } finally { s.base = base; s.kept = keepK; s.keptBase = keepKB; }
  for (let y = 0; y < gh; y++) for (let x = 0; x < gw; x++) { const i = (y + Y0) * w + x + X0; A[y * gw + x] = clamp(1 - page[i] / P); page[i] = saved[y * gw + x]; }
  const kept = s.kept && st.e < 1 ? s.kept : null, kb = kept ? s.keptBase : null, ke = 1 - st.e, rp = s.resist, blend = R.blend || "own";
  const lays = blend === "lighten" || blend === "invert", to = (u, bit) => bit ? (blend === "lighten" ? P : blend === "invert" ? P - u : 0) : u;
  if (S.tone) s.dots ??= new Uint8Array(s.w * s.h);
  // The print tone (the app's ToneDocument.Layer.print): what the screened marks would show unscreened, kept on the
  // page, made by the first screened stroke; each builds on it as it found it (st.printBase), so a second prints darker.
  const prints = !S.tone && !lays, pr = prints ? (s.print ??= new Float32Array(s.w * s.h).fill(P)) : null, pb = prints ? (st.printBase ??= Float32Array.from(pr)) : null;
  const ex = Math.min(s.w - 1, rect[2] + dx), ey = Math.min(s.h - 1, rect[3] + dy);
  for (let y = Math.max(0, rect[1]); y <= ey; y++) for (let x = Math.max(0, rect[0]); x <= ex; x++) {
    const i = y * w + x, gy = y - Y0, gx = x - X0, own = A[gy * gw + gx];
    let a = own;
    if ((dx || dy) && gx - dx >= 0 && gy - dy >= 0) a = Math.max(a, SCREEN_GHOST * A[(gy - dy) * gw + gx - dx]);
    if (blend === "behind") a *= blankOf(base[i]);
    if (prints) {
      const f0 = rp ? 1 - rp[i] : 1, p = printedTone(pb[i], a, R.cover > 0 && own > 0 ? R.cover : 0);
      pr[i] = pb[i] + (p - pb[i]) * f0;
      if (pb[i] < P) a = 1 - p / P;
    }
    const bit = screenInk(S, a, x, y, s.h, s.low[i]), u = base[i], f = rp ? 1 - rp[i] : 1;
    const target = bit ? to(u, true) : R.cover > 0 && !lays && own > 0 ? u + (P - u) * R.cover : u;
    page[i] = u + (target - u) * f;
    if (kept) { const k0 = kb[i], t0 = bit ? to(k0, true) : k0; kept[i] = k0 + (t0 - k0) * ke * f; }
    if (S.tone) s.dots[i] = bit ? 1 : 0;
  }
};
// The print tone a screened mark of darkness a over white leaves over print tone v (the app's PrintScreen.printed): the
// darker of the two, so strokes over strokes at one tone print the same dots; opaque paint, covering it by cover, moves it toward its own.
function printedTone(v, a, cover) { const t = Math.min(v, PAPER_TONE * (1 - a)); return cover > 0 ? t + (PAPER_TONE * (1 - a) - t) * cover : t; }
// The eraser scrapes Tone's dots in streaks along its stroke (the scrape branch of mischief-lab.html's dab1, a pass of
// the blade taken at once): a light streak takes about one in five, a heavy one nine in ten. Anything else erases.
const EraserNM = Eraser;
Eraser = class extends EraserNM {
  disc(cx, cy, rad, ...rest) {
    const s = this.s, dots = s.dots;
    // The print tone goes where the eraser leaves paper (once a stroke, as the eraser takes each pixel).
    if (s.print) for (let y = Math.max(0, Math.floor(cy - rad)); y <= Math.min(s.h - 1, Math.ceil(cy + rad)); y++) for (let x = Math.max(0, Math.floor(cx - rad)); x <= Math.min(s.w - 1, Math.ceil(cx + rad)); x++) {
      const i = y * s.w + x;
      if (!this.seen[i] && (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 <= rad * rad && (s.kept ? s.kept[i] : PAPER_TONE) >= PAPER_TONE - .5) s.print[i] = PAPER_TONE;
    }
    if (!dots) return super.disc(cx, cy, rad, ...rest);
    if (this.nmSalt === undefined) { this.nmSalt = (s.nmScrapes = (s.nmScrapes || 0) + 1) * 0x9e3779b1; this.nmHead = [1, 0]; }
    if (this.nmAt) { const ddx = cx - this.nmAt[0], ddy = cy - this.nmAt[1], d = Math.hypot(ddx, ddy); if (d > .5) this.nmHead = [ddx / d, ddy / d]; }
    this.nmAt = [cx, cy];
    const [hx, hy] = this.nmHead, w = s.w, held = [];
    for (let y = Math.max(0, Math.floor(cy - rad)); y <= Math.min(s.h - 1, Math.ceil(cy + rad)); y++) for (let x = Math.max(0, Math.floor(cx - rad)); x <= Math.min(w - 1, Math.ceil(cx + rad)); x++) {
      const i = y * w + x;
      if (this.seen[i] || !dots[i] || (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 > rad * rad) continue;
      const n = screenHash(Math.floor((-hy * x + hx * y) / 1.4), 0, 31), take = 1 - Math.pow(1 - (.08 + .5 * n * n), 3);
      if (screenHash(x, y, this.nmSalt) < take) dots[i] = 0; else held.push(i, s.page[i]);
    }
    const out = super.disc(cx, cy, rad, ...rest);
    for (let k = 0; k < held.length; k += 2) s.page[held[k]] = s.base[held[k]] = held[k + 1];
    return out;
  }
};
const blankPageScreen0 = blankPage;
blankPage = function (s) { blankPageScreen0(s); s.dots = null; s.print = null; };
// The lab shows Screen's own dials only with a screen; Cell and Angle greyed where its print has none.
function dialShown(B, part, key) { return !(part === "medium" && UNDER_SCREEN.has(key) && B.medium.k !== "tone" && !(B.medium.d.screen | 0)); }
if (typeof dialActive === "function") {
  const dialActiveNM = dialActive;
  dialActive = (B, part, key) => {
    if (part === "medium" && (key === "screen" || UNDER_SCREEN.has(key)) && B.medium.k !== "tone" && B.life.k === "float") return false;
    const sc = B.medium.d.screen | 0, pt = B.medium.d.pattern | 0, tone = B.medium.k === "tone";
    if (part === "medium" && key === "cell" && (tone ? pt === 2 : sc === 3)) return false;
    if (part === "medium" && key === "sangle" && (tone ? pt === 2 : sc === 3 || sc === 4)) return false;
    return dialActiveNM(B, part, key);
  };
}

// ---------- rolls: rare, from the seed, after every part (the app's ModularStyle.rollTone, rollScreen) ----------
Object.assign(NM_ODDS, { screen: { [WILDCARD]: 25, Marker: 25, "Ink wash": 25, Charcoal: 25, Fineliner: 40, Pencil: 40, Print: 1 }, tone: { [WILDCARD]: 40, Print: 5 } });
Object.assign(NM_SALT, { screen: 0x5c4ee, tone: 0x70e5, screen2: 0x5c4e6 });
NM.screenRolls = true; NM.toneRolls = true;
function rollScreen(style, B, lk, old) {
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i), odds = key => NM_ODDS[key][style] || 0;
  const free = !(old && lk.parts.has("medium"));
  if (free && NM.toneRolls && odds("tone") && B.medium.k !== "resist" && u("tone", 0) < 1 / odds("tone")) {
    B.medium = switchChoice("medium", B.medium, "tone");
    Object.assign(B.medium.d, { pattern: Math.min(3, (u("tone", 1) * 4) | 0), ttone: lerp(.25, .65, u("tone", 2)), cell: Math.round(lerp(4, 10, u("tone", 3))),
      sangle: u("tone", 4) < .5 ? 45 : Math.round(lerp(0, 90, u("tone", 5))) });
  }
  if (free && NM.screenRolls && !lk.dials.has("medium.screen") && odds("screen") && choiceOf("medium", B.medium.k).dials.includes("screen") && u("screen", 0) < 1 / odds("screen"))
    // Print picks any of the first ten alike; elsewhere one roll in six takes one of the six appended prints (the rate of a Screen stays as it was).
    Object.assign(B.medium.d, { screen: style === "Print" ? 1 + Math.min(9, (u("screen2", 1) * 10) | 0) : u("screen2", 0) < 1 / 6 ? 5 + Math.min(5, (u("screen2", 1) * 6) | 0) : 1 + Math.min(3, (u("screen", 1) * 4) | 0), cell: Math.round(lerp(4, 10, u("screen", 2))),
      sangle: u("screen", 3) < .5 ? 45 : Math.round(lerp(0, 90, u("screen", 4))), mis: u("screen", 5) < .25 ? Math.round(lerp(1, 5, u("screen", 6))) : 0,
      gain: u("screen", 7) < 1 / 3 ? lerp(.2, .6, u("screen", 8)) : 0, uneven: u("screen", 9) < 1 / 3 ? lerp(.2, .6, u("screen", 10)) : 0 });
}
const rollNewNM = rollNew;
rollNew = function (style, B, lk, old) { rollNewNM(style, B, lk, old); rollScreen(style, B, lk, old); };
const makeVarsNM = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.medium.slice();
  CHOICES.medium = keep.filter(c => c.k !== "tone" || B.medium.k === "tone");
  try { return makeVarsNM(B, locks); } finally { CHOICES.medium = keep; }
};

// ---------- words (docs/TEXT-STYLE.md): the print, or the tone, says what it lays ----------
const SCREEN_WORDS = ["", "Halftone", "Engraved", "Dithered", "Grained", "Rosette", "Ben-Day", "Crosshatch", "Wavy-line", "Mezzotint", "Diamond-dot", "Stippled", "Woodcut", "Scribbled", "Hatched"],
  SCREEN_TAGS = ["", "halftone", "engraved", "dithered", "grained", "rosette", "ben-day", "crosshatch", "wavy-line", "mezzotint", "diamond-dot", "stippled", "woodcut", "scribbled", "hatched"];
const TONE_WORDS = ["Dot screentone", "Line screentone", "Sand screentone", "Gradient screentone", "Crosshatch screentone", "Wavy-line screentone", "Mezzotint screentone",
  "Stipple screentone", "Woodcut screentone", "Scribble screentone", "Hatched screentone"];
// The app's LabText: a character .6 em, m and w .9; the Medium module's line holds 9 em (LabLayout.choiceEm).
const emsOf = t => [...t].reduce((n, c) => n + ("mwMW%@".includes(c) ? .9 : .6), 0);
const fitsMedium = t => emsOf(t) <= 9 + 1e-4;
const screenWords = (sc, base) => { const both = SCREEN_WORDS[sc] + " " + base.toLowerCase(); return fitsMedium(both) ? both : SCREEN_WORDS[sc]; };
const toneWords = B => TONE_WORDS[B.medium.d.pattern | 0];
// The Medium card: "Screentone" alone where its pattern's name would pass the line ("Gradient screentone").
const toneCard = B => fitsMedium(toneWords(B)) ? toneWords(B) : "Screentone";
// A Medium swap tile's note (ModularWords.swapNote): Screentone's pattern, as the tile says Screentone already.
const mediumNote = B => B.medium.k === "tone" ? DIALS.medium.pattern.choices[B.medium.d.pattern | 0].toLowerCase() : choiceWords(B, "medium");
// Tone worded as the ink line it is laid as (no Erasable of its own, no smudge).
const asToneInk = B => { const A = cloneBrush(B); A.medium = freshPart("medium", "ink"); A.medium.d.erase = 0; A.carry = freshPart("carry", "off"); return normBrush(A); };
// A screened mark lays no grey after it: no Slow settling to say (the app's: it doesn't settle).
const noSettle = t => t !== "slow" && !/^Wet for /.test(t);
const choiceWordsScreen = choiceWords;
choiceWords = function (B, part) {
  if (part === "medium" && B.medium.k === "tone") return toneCard(B);
  if (part === "medium" && (B.medium.d.screen | 0) && !(B.medium.d.blend | 0)) return screenWords(B.medium.d.screen | 0, NM_BASE[B.medium.k]);
  return choiceWordsScreen(B, part);
};
const captionItemsScreen = captionItems;
captionItems = function (B) {
  if (B.medium.k === "tone") {
    const A = asToneInk(B), W = cloneBrush(A); W.medium = freshPart("medium", "wash");
    const other = captionItemsScreen(normBrush(W)), items = captionItemsScreen(A).filter(t => other.includes(t));
    items.splice(1, 0, toneWords(B).toLowerCase());
    return items.filter(noSettle);
  }
  const sc = B.medium.d.screen | 0, items = sc ? captionItemsScreen(B).filter(noSettle) : captionItemsScreen(B);
  if (sc) items.splice(1 + ((B.medium.d.blend | 0) ? 1 : 0), 0, SCREEN_TAGS[sc]);
  return items;
};
// Control: a mark that reaches the page as dots (Print shading, Screentone; not on water) prints its darkness as dots,
// so the darkness cells say so (the app's ModularWords.handWords and darkHead): "pressure grows dots", the grid's Dots.
const printsDots = B => B.medium.k === "tone" || (B.medium.d.screen | 0) > 0 && B.life.k !== "float";
const darkHead = B => printsDots(B) ? "Dots" : "Dark";
const handWordsScreen = handWords;
handWords = function (B, n = 2) {
  const t = handWordsScreen(B, n);
  return printsDots(B) ? t.replace(/\bdarkens\b/g, "grows dots").replace(/\blightens\b/g, "shrinks dots").replace(/\bdarken\b/g, "grow dots").replace(/\blighten\b/g, "shrink dots") : t;
};
// Misregister, Ink gain and Uneven: the two strongest, one sentence.
function newsprintWords(M) {
  const on = [[(M.d.mis || 0) / 8 - .1, "doubled"], [(M.d.gain || 0) - .25, "dots swell"], [(M.d.uneven || 0) - .25, "patchy ink"]].filter(m => m[0] > 0).sort((a, b) => b[0] - a[0]);
  return on.length ? cap1(on[0][1]) + (on.length > 1 ? ", " + on[1][1] : "") + "." : null;
}
const plainWordsScreen = plainWords;
plainWords = function (B) {
  const tone = B.medium.k === "tone", sc = tone ? 0 : B.medium.d.screen | 0;
  if (!tone && !sc) return plainWordsScreen(B);
  const text = plainWordsScreen(tone ? asToneInk(B) : B), all = text.split(/(?<=\.)\s+/).filter(Boolean).filter(noSettle);
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(all[0]), noun = m ? m[1] : all[0].slice(0, -1), tail = m && m[2] ? m[2] : "";
  const out = [(tone ? toneWords(B) : SCREEN_WORDS[sc] + " " + lc1(noun)) + tail + "."];
  let k = 1;
  const SAID = ["Builds up where it lingers.", "Goes behind marks.", "Inverts what's under it."];
  if (all[k] === SAID[0]) out.push(all[k++]);
  if (tone) out.push("Scrapes off in streaks.");
  while (k < all.length && SAID.includes(all[k])) out.push(all[k++]);
  const news = sc ? newsprintWords(B.medium) : null;
  if (news) out.push(news);
  out.push(...all.slice(k));
  let t2 = out[0];
  for (let i = 1; i < out.length; i++) { if (i >= 2 && t2.length + 1 + out[i].length > 260) break; t2 += " " + out[i]; }
  return t2;
};

// ---------- checks ----------
function screenChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 360, H = 240, q = quantTone, P = PAPER_TONE;
  const mk = (medium, md, life) => {
    const B = { name: "s", seed: 23, style: "Fineliner", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium || "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", life || "wet"), hand: freshPart("hand", "steady") };
    Object.assign(B.medium.d, md || {});
    return normBrush(B);
  };
  const line = (x0, y0, x1, y1, p = .8, n = 90) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p, t: k * 6, tx: NaN, ty: NaN }));
  const seg = c => c.split("_")[1];
  // Codes: none leaves the code as it was; each screen one digit after Blend's; Tone is _6_ at its defaults.
  { const ink = mk(), codes = [1, 2, 3, 4].map(sc => encodeM(mk("ink", { screen: sc }))), tone = mk("tone");
    t("Screen is written only when not none (" + seg(codes[0]) + "), Tone is _" + seg(encodeM(tone)) + "_", encodeM(mk("ink", { screen: 0 })) === encodeM(ink)
      && codes.every((c, i) => seg(c).startsWith(seg(encodeM(ink))) && seg(c).endsWith("0" + (i + 1)) && encodeM(readAnyCode(c).b) === c) && seg(encodeM(tone)) === "6"); }
  // Pure black and white, overlapping strokes on the same dots.
  { const res = [];
    for (let sc = 1; sc <= 4; sc++) {
      const B = mk("wash", { screen: sc, wet: .3 }), a = modSurface(W, H, 7), c = modSurface(W, H, 7), both = modSurface(W, H, 7);
      layStroke(a, B, line(20, 100, 340, 100), 80, 1, null); layStroke(c, B, line(40, 140, 320, 140), 80, 1, null);
      layStroke(both, B, line(20, 100, 340, 100), 80, 1, null); layStroke(both, B, line(40, 140, 320, 140), 80, 1, null);
      // Exactly both strokes' dots, where one reached and where both did: the same tone prints the same dots (the app's
      // print tone is the darker of the two), so strokes over strokes fill evenly.
      let grey = 0, wrong = 0, inked = 0, more = 0;
      for (let i = 0; i < W * H; i++) {
        const v = both.page[i], either = a.page[i] < .5 || c.page[i] < .5, over = a.print[i] < P && c.print[i] < P;
        if (v > .5 && v < P - .5) grey++; if (v < .5) inked++;
        if (either && v >= .5 || !over && !either && v < .5) wrong++; else if (over && !either && v < .5) more++;
      }
      res.push(grey + "/" + wrong + "/" + inked + "/" + more);
      if (grey || wrong || inked < 300 || more) { t("screen " + sc + ": pure black and white, overlaps lay both's dots and no more (" + res.join(" ") + ")", false); break; }
    }
    if (res.length === 4) t("every screen lays pure black and white; two overlapping strokes lay both's dots and no more (grey/wrong/black/more " + res.join(" ") + ")", true); }
  // The dots follow the tone the brush lays unscreened: a grey swatch about half; a second stroke the same (the darker of the two).
  { const share = (shade, n, sc) => { const s = modSurface(W, H, 7), plain = modSurface(W, H, 7);
      for (let k = 0; k < n; k++) { layStroke(s, mk("ink", { screen: sc }), line(20, 120, 340, 120), 20, shade, null); layStroke(plain, mk(), line(20, 120, 340, 120), 20, shade, null); }
      let foot = 0, bl = 0, dark = 0; for (let i = 0; i < W * H; i++) if (plain.page[i] < P - 5) { foot++; dark += 1 - plain.page[i] / P; if (s.page[i] < .5) bl++; }
      return [bl / foot, dark / foot]; };
    const one = share(.5, 1, 1), two = share(.5, 2, 1), f = v => Math.round(100 * v) + "%";
    t("a grey halftone ink lays " + f(one[0]) + " dots for " + f(one[1]) + " grey, twice " + f(two[0]) + " for " + f(two[1]),
      Math.abs(one[0] - one[1]) < .12 && Math.abs(two[0] - one[0]) < .02); }
  // Tone: black dots over the fill, the pen line kept; the eraser scrapes some in streaks.
  { const s = modSurface(W, H, 7); layStroke(s, mk(), line(20, 120, 340, 120), 4, 1, null);
    const pen = Float32Array.from(s.page);
    for (let y = 60; y <= 180; y += 20) layStroke(s, mk("tone", { ttone: .6 }), line(20, y, 340, y), 40, 1, null);
    let dots = 0, kept = 0, lines = 0;
    for (let i = 0; i < W * H; i++) { if (s.dots[i]) dots++; if (pen[i] < 60) { lines++; if (s.page[i] <= pen[i] + .5) kept++; } }
    const er = new Eraser(s, 40, false); for (let k = 0; k <= 60; k++) er.add([[20 + 5 * k, 120, 1, k * 6, NaN, NaN]]);
    let after = 0; for (let i = 0; i < W * H; i++) if (s.dots[i]) after++;
    t("Tone lays " + dots + " black dots and keeps the pen line (" + kept + " of " + lines + "); the eraser scrapes some (" + after + " left)", dots > 3000 && kept === lines && after < dots && after > dots * .6); }
  // Rolls: with them off every roll is as before; on, a screen or Tone at the odds, nothing else changed.
  { const roll = on => { const keep = Math.random, k0 = NM.screenRolls, k1 = NM.toneRolls; Math.random = rng(4242); NM.screenRolls = NM.toneRolls = on;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.screenRolls = k0; NM.toneRolls = k1; } };
    const a = roll(false), b = roll(true); let screens = 0, tones = 0, odd = 0;
    // Codes without the name, which says what the brush is (a screen or Tone renames it).
    const code = B => encodeM(Object.assign(cloneBrush(B), { name: "n" }));
    for (let i = 0; i < a.length; i++) {
      if (code(a[i]) === code(b[i])) continue;
      if (b[i].medium.k === "tone") { tones++; if (b[i].style !== WILDCARD && b[i].style !== "Print") odd++; continue; }
      const c = cloneBrush(b[i]); for (const k of SCREEN_DIALS) c.medium.d[k] = DIALS.medium[k].def;
      if ((b[i].medium.d.screen | 0) && code(normBrush(c)) === code(a[i]) && b[i].style !== "Watercolor") screens++; else odd++;
    }
    // Print screens every roll (one in five Tone): about 1400 / 15 more.
    t("rolls: " + screens + " gain a Screen, " + tones + " become Tone, none else changed", odd === 0 && screens > 60 && screens < 260 && tones >= 1); }
  // Words.
  { const h = mk("ink", { screen: 1 }), tn = mk("tone");
    t("words: \"" + choiceWords(h, "medium") + "\", \"" + plainWords(h) + "\", \"" + choiceWords(tn, "medium") + "\", \"" + plainWords(tn) + "\"",
      choiceWords(h, "medium") === "Halftone ink" && plainWords(h).startsWith("Halftone ink line.") && captionItems(h).includes("halftone") && choiceWords(tn, "medium") === "Dot screentone"
      && plainWords(tn).startsWith("Dot screentone. Scrapes off in streaks.") && captionItems(tn)[1] === "dot screentone"); }
  // The ten appended prints (the app's ScreenChecks.newPrints): each inks about as much as the grey asks and more with more grey,
  // reads back from its code as the same choice, and says its name; the old four stay where they were.
  { const names = ["Rosette", "Ben-Day", "Crosshatch", "Wavy Lines", "Mezzotint", "Diamond Dots", "Stipple", "Woodcut", "Scribble", "Hatching"], pick = DIALS.medium.screen.choices;
    let ok = pick.length === 15 && pick.slice(0, 5).join() === "None,Halftone,Engraving,Dither,Grain" && pick.slice(5).join() === names.join(), worst = 0;
    for (let s = 5; s <= 14; s++) {
      const S = screenOf(mk("ink", { screen: s }).medium); let last = -1;
      for (const g of [.1, .25, .5, .75, .9]) {
        let n = 0; for (let y = 0; y < 200; y++) for (let x = 0; x < 200; x++) if (screenInk(S, g, x, y, 360, 0)) n++;
        const f = n / 40000; worst = Math.max(worst, Math.abs(f - g) - (s === 5 ? .06 : 0)); if (!(f > last)) ok = false; last = f;
      }
      const B = mk("ink", { screen: s }), back = readAnyCode(encodeM(B)).b;
      ok = ok && (back.medium.d.screen | 0) === s && encodeM(normBrush(back)) === encodeM(B) && choiceWords(B, "medium").length > 5;
    }
    t("the ten appended prints: coverage within " + Math.round(100 * worst) + "% of the grey, growing with it, codes and names", ok && worst < .06); }
  return out;
}
const selfTestScreen = selfTest;
selfTest = function () { const r = selfTestScreen(); r.screen = screenChecks(); r.ok = r.ok && r.screen.every(c => c.ok); NM.lastScreen = r.screen; return r; };
// Erasable's check hashes rolls from before the new choices: it runs with their rolls off.
const eraseChecksScreen = eraseChecks;
eraseChecks = function () { const k0 = NM.screenRolls, k1 = NM.toneRolls, k2 = NM.waterRolls; NM.screenRolls = NM.toneRolls = NM.waterRolls = false; try { return eraseChecksScreen(); } finally { NM.screenRolls = k0; NM.toneRolls = k1; NM.waterRolls = k2; } };
if (typeof runChecks === "function") {
  const runChecksScreen = runChecks;
  runChecks = function () {
    runChecksScreen();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastScreen) return;
      out.textContent += "\n\nScreen and Tone:\n" + NM.lastScreen.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 40);
  };
}

// ===================================================================================================================
// The wet smudges (spec docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md; the app's HeldPaint and ClearWater):
// Smudge's Keeps Paint (Drag's and Smear's, a dirty brush that keeps what it picks up for the next stroke) and Smudge's
// fifth choice, Clear Water (lays only water: lifts, and lets what it lifted run out through its wet). Their tables and
// words are parts.js's, their drawing stroke.js's; here Clear Water's rare roll from the seed and the checks.
// ===================================================================================================================
Object.assign(NM_ODDS, { water: { [WILDCARD]: 40, Watercolor: 50, "Ink wash": 50 } });
Object.assign(NM_SALT, { water: 0xc1ea4 });
NM.waterRolls = true;
// Rare (the app's ModularStyle.rollClearWater): after every part, from the seed with its salt, never on Float; Pick up
// .35–.75, Lay down .4–.8. Keeps Paint is never rolled.
function rollWater(style, B, lk, old) {
  const u = i => hash01((B.seed ^ NM_SALT.water) >>> 0, i), n = NM_ODDS.water[style] || 0;
  if (!NM.waterRolls || !n || (old && lk.parts.has("carry")) || B.life.k === "float" || !(u(0) < 1 / n)) return;
  B.carry = { k: "water", d: { pick: lerp(.35, .75, u(1)), lay: lerp(.4, .8, u(2)) } };
}
const rollNewScreen = rollNew;
rollNew = function (style, B, lk, old) { rollNewScreen(style, B, lk, old); rollWater(style, B, lk, old); };
function wetChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 360, H = 240, P = PAPER_TONE;
  const mk = (carry, cd, medium, life) => {
    const B = { name: "w", seed: 23, style: "Watercolor", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium || "wash"), carry: freshPart("carry", carry),
      paper: freshPart("paper", "plain"), life: freshPart("life", life || "wet"), hand: freshPart("hand", "steady") };
    Object.assign(B.carry.d, cd || {});
    return normBrush(B);
  };
  const line = (x0, y0, x1, y1, p = .8, n = 90) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p, t: k * 6, tx: NaN, ty: NaN }));
  const mean = (s, x0, x1, y0, y1) => { let a = 0, n = 0; for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { a += s.page[y * W + x]; n++; } return a / n; };
  const seg = c => c.split("_")[2];
  // Codes: Keeps Paint written only when on (after Soften), Clear Water is _4.
  { const smear = mk("smudge", { load: 0 }), dirty = mk("smudge", { load: 0, keep: .9 }), water = mk("water");
    t("Keeps Paint is written only when on (" + seg(encodeM(smear)) + " → " + seg(encodeM(dirty)) + "), Clear Water is _" + seg(encodeM(water)) + "_",
      encodeM(mk("smudge", { load: 0, keep: 0 })) === encodeM(smear) && seg(encodeM(dirty)).length === seg(encodeM(smear)).length + 4 && seg(encodeM(water)).startsWith("4")
      && [dirty, water].every(b => { const c = encodeM(b); return encodeM(readAnyCode(c).b) === c; })); }
  // Rolls: with them off every roll is as before; on, Clear Water now and then, nothing else changed.
  { const roll = on => { const keep = Math.random, k0 = NM.waterRolls; Math.random = rng(5151); NM.waterRolls = on;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.waterRolls = k0; } };
    const a = roll(false), b = roll(true); let water = 0, odd = 0, kept = 0;
    for (let i = 0; i < a.length; i++) {
      if ((b[i].carry.d.keep ?? 0) > 0) kept++;
      if (encodeM(a[i]) === encodeM(b[i])) continue;
      const c = cloneBrush(b[i]); c.carry = cloneBrush(a[i]).carry;
      if (b[i].carry.k === "water" && encodeM(normBrush(c)).split(".M")[1] === encodeM(a[i]).split(".M")[1]) water++; else odd++;
    }
    t("rolls: " + water + " become Clear Water, none else changed, Keeps Paint never rolled", odd === 0 && water >= 3 && water < 80 && kept === 0); }
  // Keeps Paint: a dirty brush lays what it picked up on the next stroke; off, nothing.
  { const draw = keep => { const s = modSurface(W, H, 7); for (let y = 40; y < 140; y++) for (let x = 60; x < 120; x++) s.page[y * W + x] = 60;
      const B = mk("smudge", { load: 0, pick: .75, lay: .5, keep });
      layStroke(s, B, line(30, 90, 150, 90), 60, .5, null); layStroke(s, B, line(180, 200, 340, 200), 60, .5, null); return mean(s, 185, 215, 185, 215); };
    const dirty = draw(.9), clean = draw(0);
    t("a dirty brush lays what it picked up on the next stroke (" + dirty.toFixed(1) + "; clean " + clean.toFixed(1) + ")", dirty < P - 8 && clean > P - .5); }
  // Clear Water: it lightens a wash where it goes and runs some of it out beside, onto the paper; on paper it lays nothing.
  { const s = modSurface(W, H, 7); for (let y = 0; y < H; y++) for (let x = 0; x < 180; x++) s.page[y * W + x] = 130;
    layStroke(s, mk("water"), line(180, 20, 180, 220), 60, .5, null);
    const inside = mean(s, 150, 172, 60, 180), beside = mean(s, 186, 205, 60, 180), blank = modSurface(W, H, 7);
    layStroke(blank, mk("water"), line(60, 120, 300, 120), 60, .5, null);
    t("Clear Water lifts a wash (" + inside.toFixed(1) + " from 130) and runs it out beside (" + beside.toFixed(1) + "), nothing on paper", inside > 135 && beside < P - .5 && mean(blank, 0, W - 1, 0, H - 1) > P - .01); }
  // Words.
  { const d = mk("smudge", { load: 0, keep: .9 }), w = mk("water");
    t("words: \"" + plainWords(d) + "\", \"" + plainWords(w) + "\", \"" + choiceWords(w, "carry") + "\"",
      plainWords(d).includes("Smears. Keeps paint for the next stroke.") && captionItems(d).includes("keeps paint") && plainWords(w).startsWith("Clear water. Lifts paint, lets it run.")
      && choiceWords(w, "carry") === "Water" && captionItems(w).includes("clear water") && isClearWater(mk("water", {}, "ink", "float"))); }
  return out;
}
const selfTestWet = selfTest;
selfTest = function () { const r = selfTestWet(); r.wet = wetChecks(); r.ok = r.ok && r.wet.every(c => c.ok); NM.lastWet = r.wet; return r; };
if (typeof runChecks === "function") {
  const runChecksWet = runChecks;
  runChecks = function () {
    runChecksWet();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastWet) return;
      out.textContent += "\n\nWet smudges:\n" + NM.lastWet.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 60);
  };
}

// ===================================================================================================================
// Flick and Fling (the mischief modules spec, Stage 2; mischief-lab.html's flick, fling, land2 and watch2; the app's
// FlickFling): ink that keeps going after the pen.
//   Flick  a Tip dial, last of every tip's: a stroke lifted while still moving carries on along its last heading and
//          turn, eased out, for about Flick × the speed: its own samples past the lift, narrowing and running dry.
//   Fling  a Medium dial, last of every medium with Blend: a hard stop (fast in the last 140 ms, then still 45 ms with
//          the pen down, or a lift right after) pools a little ink and throws drops ahead, nearest landing first.
// Both read only the samples and the seed, so a redraw lands the same. Appended, written only when on: old codes as before.
// Bristle's Paint Load (the app's, not drawn here) goes before Flick so the codes line up.
// ===================================================================================================================
Object.assign(DIALS.tip, { load: { label: "Paint Load", min: 0, max: 1, def: .5, hide: true }, flick: { label: "Flick", min: 0, max: 1, def: 0 } });
DIALS.medium.fling = { label: "Fling", min: 0, max: 1, def: 0 };
choiceOf("tip", "bristles").dials.push("load");
for (const c of CHOICES.tip) c.dials.push("flick");
for (const c of CHOICES.medium) if (c.dials.includes("blend")) c.dials.push("fling");
for (const k of ["tip.load", "tip.flick", "medium.fling"]) ADDED.add(k);
KEEPS.tip.push("flick"); KEEPS.medium.push("fling");
// The prototype's pad is CSS px (1/96 in), the Manta's page px 1/300 in: its lengths and speeds taken PROTO times.
const FF = { PROTO: 3, LIFT_MS: 40, FLICK_MS: 30, PEAK_MS: 140, STILL_MS: 45, AIM_MS: 40, STILL_PX: 4, FASTEST: 5, STEP: 1.5, REACH: 85, SALT: 0xf1196 };
Object.assign(FF, { FLICK_SPEED: .35 * FF.PROTO, MOST: 260 * FF.PROTO, LEAST: 3 * FF.PROTO, TURN: .012 / FF.PROTO, FLING_SPEED: FF.PROTO, AIM_PX: 3 * FF.PROTO });
const resolveFF = resolve;
resolve = function (B, size, shade) {
  const R = resolveFF(B, size, shade);
  R.flick = B.tip.d.flick || 0; R.fling = B.medium.k === "tone" || B.medium.k === "resist" ? 0 : B.medium.d.fling || 0;
  return R;
};
// velocity2: over the samples within win ms before sample k; the heading at k itself (its last AIM_PX), and the turn.
function ffMotion(pts, k, win) {
  const L = pts[k];
  let j = k; while (j > 0 && pts[j - 1].t >= L.t - win) j--;
  if (j === k && j > 0) j--;
  const P = pts[j], dt = Math.max(1, L.t - P.t), vx = (L.x - P.x) / dt, vy = (L.y - P.y) / dt, m = { vx, vy, speed: Math.hypot(vx, vy), dir: Math.atan2(vy, vx), turn: 0 };
  let e = k; while (e > 0 && L.d - pts[e].d < FF.AIM_PX) e--;
  m.end = e < k ? Math.atan2(L.y - pts[e].y, L.x - pts[e].x) : m.dir;
  let q = j; while (q > 0 && pts[q - 1].t >= P.t - win) q--;
  const len = Math.hypot(L.x - P.x, L.y - P.y);
  if (q < j && len > 2 * FF.PROTO) {
    let da = m.dir - Math.atan2(P.y - pts[q].y, P.x - pts[q].x);
    while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
    m.turn = clamp(da / len, -FF.TURN, FF.TURN);
  }
  return m;
}
// peak2: the fastest the pen went (over about 20 ms) in the win ms up to sample k.
function ffPeak(pts, k, win) {
  let most = 0; const lt = pts[k].t;
  for (let i = k, j = k; i > 0 && pts[i].t >= lt - win; i--) {
    if (j > i) j = i;
    while (j > 0 && pts[i].t - pts[j].t < 20) j--;
    const dt = pts[i].t - pts[j].t;
    if (dt > 0) most = Math.max(most, (pts[i].d - pts[j].d) / dt);
  }
  return most;
}
class FlickFling {
  constructor(R) { this.R = R; this.anchorT = null; this.lastT = null; this.checkedAt = null; this.anchorK = 0; this.flung = false; this.lifted = false; this.drops = []; this.landed = 0; }
  // Every sample, kept or not: where the pen rests and since when; a hard stop held STILL_MS throws the fling.
  sampled(pts, x, y, t) {
    if (t === undefined || this.lifted || !pts.length) return;
    this.lastT = t;
    if (this.anchorT === null || Math.hypot(x - this.ax, y - this.ay) > FF.STILL_PX) { this.ax = x; this.ay = y; this.anchorT = t; this.anchorK = pts.length - 1; return; }
    if (this.flung || !(this.R.fling > 0) || t - this.anchorT < FF.STILL_MS || this.checkedAt === this.anchorT) return;
    this.checkedAt = this.anchorT;
    const peak = ffPeak(pts, this.anchorK, FF.PEAK_MS);
    if (peak > FF.FLING_SPEED) this.fling(pts, peak, t);
  }
  // The lift: still moving, the flick's samples past it ([x, y, p, t, tx, ty] each), marking pts; just after a stop, the fling.
  lift(pts) {
    if (this.lifted) return null;
    this.lifted = true;
    if (this.flung || this.lastT === null || pts.length < 3) return null;
    const last = pts.length - 1, m = ffMotion(pts, last, FF.FLICK_MS);
    if (this.lastT - this.anchorT < FF.LIFT_MS && m.speed > FF.FLICK_SPEED) return this.R.flick > 0 ? this.carry(pts, m) : null;
    if (this.R.fling > 0) { const peak = ffPeak(pts, this.anchorK, FF.PEAK_MS); if (peak > FF.FLING_SPEED) this.fling(pts, peak, this.lastT); }
    return null;
  }
  carry(pts, m) {
    const len = Math.min(FF.MOST, this.R.flick * m.speed * FF.REACH);
    if (len < FF.LEAST) return null;
    const L = pts[pts.length - 1], ms = 150 + 150 * Math.min(1, len / FF.PROTO / 220), n = Math.ceil(len / FF.STEP), ds = len / n, out = [];
    let th = m.end, x = L.x, y = L.y;
    for (let i = 0; i < n; i++) {
      th += m.turn * ds * .85; x += Math.cos(th) * ds; y += Math.sin(th) * ds;
      const u = (i + 1) / n, e = 1 - Math.pow(1 - u, 1 / 2.2);
      out.push([x, y, L.p * (1 - u), L.t + Math.round(e * ms), L.tx, L.ty]);
    }
    pts.flick = { from: L.d, len };
    return out;
  }
  fling(pts, peak, now) {
    this.flung = true;
    const R = this.R, k = this.anchorK, S = pts[k], a = R.fling, v = Math.min(FF.FASTEST, peak / FF.PROTO), dir = ffMotion(pts, k, FF.AIM_MS).dir;
    const r0 = R.line ? Math.max(.6, R.inkSize * lerp(1 - .75 * R.pen.pressure, 1 + .6 * R.pen.pressure, S.p) / 2) : R.bodySize / 2;
    const grow = r0 <= 6.4 ? r0 / 3.2 : 2 * Math.sqrt(r0 / 6.4), pool = R.line ? 1.3 * r0 : 1.05 * r0, ahead = R.line ? .5 * r0 : .3 * r0;
    const n = Math.round(a * (1.5 + v * 2.2)), rnd = rng(((R.pen.seed | 0) ^ FF.SALT ^ Math.imul(k + 1, 2654435761) ^ Math.round(S.d * 16)) >>> 0);
    const out = [];
    for (let i = 0; i < n; i++) {
      const ang = dir + (rnd() - .5) * .45 + (rnd() - .5) * (rnd() - .5) * .6;
      const dist = (10 + v * 34) * (.35 + rnd() * .95) * (.6 + a * .8), rad = clamp((.8 + v * .8) * (.4 + rnd() * .9), .7, 6) * grow, at = dist * FF.PROTO + ahead;
      out.push({ x: S.x + Math.cos(ang) * at, y: S.y + Math.sin(ang) * at, along: rad * 1.5, across: rad, angle: ang, at: now + 30 + Math.round(dist / v * 1.6), speck: rnd() < .4 });
    }
    // Ink piles up a little where the nib stops dead; then the drops, nearest first.
    out.sort((p, q) => p.at - q.at);
    this.drops = [{ x: S.x + Math.cos(dir) * ahead, y: S.y + Math.sin(dir) * ahead, along: pool, across: pool, angle: dir, at: now, speck: false }, ...out]; this.landed = 0;
  }
  landing(now) { return this.landed < this.drops.length && this.drops[this.landed].at <= now; }
  land(now) { const out = []; while (this.landing(now)) out.push(this.drops[this.landed++]); return out; }
}
// A drop into buffer buf (the strongest kept), an ellipse along its flight and its speck beyond; wetD takes dist where it lays.
function ffDrop(s, buf, wetD, D, k, dist) {
  const ell = (cx, cy, ra, rb, ang) => {
    const R = Math.ceil(Math.max(ra, rb) + 1), x0 = Math.max(0, Math.floor(cx - R)), x1 = Math.min(s.w - 1, Math.ceil(cx + R)), y0 = Math.max(0, Math.floor(cy - R)), y1 = Math.min(s.h - 1, Math.ceil(cy + R));
    if (x1 < x0 || y1 < y0) return null;
    const ca = Math.cos(ang), sa = Math.sin(ang);
    for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
      const dx = px + .5 - cx, dy = py + .5 - cy, al = (dx * ca + dy * sa) / ra, ac = (-dx * sa + dy * ca) / rb, c = clamp((1 - Math.sqrt(al * al + ac * ac)) * rb + .5) * k, i = py * s.w + px;
      if (c > buf[i]) { buf[i] = c; if (wetD) wetD[i] = dist; }
    }
    return [x0, y0, x1, y1];
  };
  let r = ell(D.x, D.y, D.along, D.across, D.angle);
  if (D.speck) { const q = Math.max(.5, D.across * .35), sp = ell(D.x + Math.cos(D.angle) * D.across * 3, D.y + Math.sin(D.angle) * D.across * 3, q, q, 0); if (sp) r = r ? union(r, sp) : sp; }
  return r;
}
const StrokeFF = Stroke;
Stroke = class extends StrokeFF {
  constructor(surf, B, size, shade, host) {
    super(surf, B, size, shade, host);
    this.ff = this.R.flick > 0 || this.R.fling > 0 ? new FlickFling(this.R) : null;
  }
  // Sample by sample, so the watcher sees each one (kept or not); drops land as the samples' clock reaches them.
  add(samples) {
    if (!this.ff || this.ff.lifted) return super.add(samples);
    let out = null;
    for (const q of samples) {
      const r = super.add([q]);
      if (r) out = out ? union(out, r) : r;
      this.ff.sampled(this.pts, q[0], q[1], q[3]);
      if (this.ff.landing(q[3])) { const d = this.ffLand(q[3]); if (d) out = out ? union(out, d) : d; }
    }
    return out;
  }
  // The drops landing by now, where the stroke's marks are: its line, else its body (Fast-wet's as laid and as shown).
  ffLand(now) {
    let r = null;
    for (const D of this.ff.land(now)) { const b = this.ffLay(D, true); if (b) r = r ? union(r, b) : b; }
    if (!r) return null;
    grow(this.rect, r);
    if (!this.fl) composeStroke(this, r, this.R.life === "dry" ? "preview" : "final");
    return r;
  }
  ffLay(D, live) {
    const R = this.R, s = this.surf;
    if (R.line) { const r = ffDrop(s, s.L, null, D, 1, 0); if (live && R.life === "wet" && s.rawL) ffDrop(s, s.rawL, null, D, 1, 0); return r; }
    if (!R.body) return null;
    const amt = R.body.amount ?? 1;
    if (R.life !== "wet") return ffDrop(s, s.W, null, D, amt, 0);
    ffDrop(s, lazyBuf(s, "rawW"), lazyBuf(s, "wetD", -1), D, amt, this.pts[this.pts.length - 1].d);
    return ffDrop(s, s.D, null, D, amt, 0);
  }
  // A Slow lift lays the line and body again: the drops that landed go back in.
  stampL(buf, final, from, clip, win) {
    let r = super.stampL(buf, final, from, clip, win);
    if (final && this.ff && this.R.line && buf === this.surf.L) for (const D of this.ff.drops.slice(0, this.ff.landed)) { const b = this.ffLay(D, false); if (b) r = union(r, b); }
    return r;
  }
  stampW(buf, final, from) {
    let r = super.stampW(buf, final, from);
    if (final && this.ff && !this.R.line && buf === this.surf.W) for (const D of this.ff.drops.slice(0, this.ff.landed)) { const b = this.ffLay(D, false); if (b) r = union(r, b); }
    return r;
  }
  end() {
    if (this.ff && !this.ff.lifted) {
      const c = this.ff.lift(this.pts);
      if (c) for (let i = 0; i < c.length; i += 3) super.add(c.slice(i, i + 3));
      this.ffLand(Infinity);
    }
    return super.end();
  }
};

// ---------- rolls: rare, from the seed, after every part (the app's ModularStyle.rollFlick, rollFling) ----------
Object.assign(NM_ODDS, { flick: { [WILDCARD]: 12, "Brush pen": 12, "Ink wash": 12, Watercolor: 20 }, fling: { [WILDCARD]: 15, "Dip pen": 15, "Brush pen": 15 } });
Object.assign(NM_SALT, { flick: 0xf11c4, fling: 0xf1196 });
NM.flickRolls = true; NM.flingRolls = true;
function rollFlickFling(style, B, lk, old) {
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i), odds = key => NM_ODDS[key][style] || 0;
  if (NM.flickRolls && !(old && lk.parts.has("tip")) && !lk.dials.has("tip.flick") && odds("flick") && "flick" in B.tip.d && u("flick", 0) < 1 / odds("flick")) B.tip.d.flick = lerp(.35, .85, u("flick", 1));
  if (NM.flingRolls && !(old && lk.parts.has("medium")) && !lk.dials.has("medium.fling") && odds("fling") && "fling" in B.medium.d && u("fling", 0) < 1 / odds("fling")) B.medium.d.fling = lerp(.3, .8, u("fling", 1));
}
const rollNewWet = rollNew;
rollNew = function (style, B, lk, old) { rollNewWet(style, B, lk, old); rollFlickFling(style, B, lk, old); };

// ---------- words (docs/TEXT-STYLE.md): said plainly ----------
const mischiefWords = B => {
  const flick = (B.tip.d.flick || 0) >= .05, fling = (B.medium.d.fling || 0) >= .05;
  return flick && fling ? "Flicks on a quick lift, flings drops on a hard stop." : flick ? "Flicks on a quick lift." : fling ? "Flings drops on a hard stop." : null;
};
const choiceWordsFF = choiceWords;
choiceWords = function (B, part) {
  const w = choiceWordsFF(B, part);
  // Fling after the medium, as Slow's gravity ("Ink, fling"), where the module's line has room; the Tip's has none for Flick.
  if (part !== "medium" || !((B.medium.d.fling || 0) >= .05)) return w;
  const both = w + ", fling";
  return fitsMedium(both) ? both : w;
};
const plainWordsFF = plainWords;
plainWords = function (B) {
  const said = mischiefWords(B), text = plainWordsFF(B);
  if (!said) return text;
  const all = text.split(/(?<=\.)\s+/).filter(Boolean);
  all.splice(all[1] === "Builds up where it lingers." ? 2 : 1, 0, said);
  let t2 = all[0];
  for (let i = 1; i < all.length; i++) { if (i >= 2 && t2.length + 1 + all[i].length > 260) break; t2 += " " + all[i]; }
  return t2;
};

// ---------- checks ----------
function flickChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 900, H = 600, P = PAPER_TONE;
  const mk = (flick, fling, life) => {
    const B = { name: "f", seed: 23, style: "Brush pen", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", life || "none"), hand: freshPart("hand", "steady") };
    B.tip.d.flick = flick; B.medium.d.fling = fling;
    return normBrush(B);
  };
  const line = (x0, x1, y, v) => { const n = Math.ceil((x1 - x0) / (v * 2)); return Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y, p: .7, t: 1000 + 2 * k, tx: NaN, ty: NaN })); };
  const dash = (x0, x1, y, hold) => {
    const o = [];
    for (let i = 0; i <= 26; i++) o.push({ x: x0 + (x1 - x0) * Math.pow(i / 26, 1.7), y, p: .45 + .4 * i / 26, t: 1000 + 4 * i, tx: NaN, ty: NaN });
    for (let k = 1; k * 4 <= hold; k++) o.push({ x: x1 + .3 * Math.sin(k), y: y + .3 * Math.cos(k * 1.3), p: .85, t: 1104 + 4 * k, tx: NaN, ty: NaN });
    return o;
  };
  const draw = (B, pts) => { const s = modSurface(W, H, 7); layStroke(s, B, pts, 12, 1, null); return s; };
  const reach = (s, from) => { let m = from; for (let y = 0; y < H; y++) for (let x = from; x < W; x++) if (s.page[y * W + x] < P - 5) m = Math.max(m, x); return m; };
  const ink = (s, x0, x1) => { let n = 0; for (let y = 0; y < H; y++) for (let x = x0; x <= x1; x++) if (s.page[y * W + x] < P - 5) n++; return n; };
  const same = (a, b) => a.page.every((v, i) => v === b.page[i]);
  // Codes: off, as before; on, appended and read back.
  { const off = mk(0, 0), on = mk(.6, .4), c = encodeM(on), back = readAnyCode(c).b;
    t("Flick and Fling are written only when on and read back (" + c + ")", encodeM(off) !== c && Math.abs(back.tip.d.flick - .6) < 1e-3 && Math.abs(back.medium.d.fling - .4) < 1e-3 && encodeM(back) === c
      && CHOICES.tip.every(ch => ch.dials[ch.dials.length - 1] === "flick") && CHOICES.medium.filter(ch => ch.dials.includes("blend")).every(ch => ch.dials[ch.dials.length - 1] === "fling")); }
  // Flick: a quick lift carries on, a slow one doesn't.
  for (const life of ["none", "wet", "dry"]) {
    const a = draw(mk(.7, 0, life), line(100, 400, 300, 3)), b = draw(mk(0, 0, life), line(100, 400, 300, 3)), far = reach(a, 400);
    t(life + ": a quick lift flicks on to x " + far + " (without, " + reach(b, 400) + ")", far > 520 && far < 620 && reach(b, 400) < 430);
  }
  { const slow = Array.from({ length: 121 }, (_, i) => ({ x: 200 + i, y: 300, p: .7, t: 1000 + 4 * i, tx: NaN, ty: NaN }));
    t("a slow lift doesn't flick", same(draw(mk(1, 0), slow), draw(mk(0, 0), slow))); }
  // Fling: a dead stop throws drops ahead, the same again; a smooth stroke doesn't.
  { const a = draw(mk(0, .8), dash(100, 420, 300, 120)), b = draw(mk(0, 0), dash(100, 420, 300, 120)), c = draw(mk(0, .8), dash(100, 420, 300, 120));
    t("a dead stop flings drops ahead (" + ink(a, 432, W - 1) + " px), the same again", ink(a, 432, W - 1) > 0 && ink(b, 432, W - 1) === 0 && same(a, c));
    t("a smooth quick stroke throws none", same(draw(mk(0, 1), line(100, 400, 300, 3)), draw(mk(0, 0), line(100, 400, 300, 3)))); }
  // Rolls: with them off every roll is as before; on, Flick or Fling at the odds, nothing else changed.
  { const roll = on => { const keep = Math.random, k0 = NM.flickRolls, k1 = NM.flingRolls; Math.random = rng(6161); NM.flickRolls = NM.flingRolls = on;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.flickRolls = k0; NM.flingRolls = k1; } };
    const a = roll(false), b = roll(true); let flicks = 0, flings = 0, odd = 0;
    for (let i = 0; i < a.length; i++) {
      if (encodeM(a[i]) === encodeM(b[i])) continue;
      const c = cloneBrush(b[i]); if ("flick" in c.tip.d) c.tip.d.flick = 0; if ("fling" in c.medium.d) c.medium.d.fling = 0;
      if ((b[i].tip.d.flick || 0) > 0) flicks++; if ((b[i].medium.d.fling || 0) > 0) flings++;
      if (encodeM(normBrush(c)).split(".M")[1] !== encodeM(a[i]).split(".M")[1]) odd++;
    }
    t("rolls: " + flicks + " flick, " + flings + " fling, none else changed", odd === 0 && flicks > 10 && flicks < 90 && flings > 5 && flings < 70); }
  // Words.
  { const B = mk(.6, .6);
    t("words: \"" + plainWords(B) + "\", \"" + choiceWords(B, "medium") + "\"", plainWords(B).includes("Flicks on a quick lift, flings drops on a hard stop.") && choiceWords(B, "medium") === "Ink, fling"
      && !plainWords(mk(0, 0)).includes("Fling")); }
  return out;
}
const selfTestFF = selfTest;
selfTest = function () { const r = selfTestFF(); r.flick = flickChecks(); r.ok = r.ok && r.flick.every(c => c.ok); NM.lastFlick = r.flick; return r; };
// Erasable's check hashes rolls from before Flick and Fling: it runs with their rolls off.
const eraseChecksFF = eraseChecks;
eraseChecks = function () { const k0 = NM.flickRolls, k1 = NM.flingRolls; NM.flickRolls = NM.flingRolls = false; try { return eraseChecksFF(); } finally { NM.flickRolls = k0; NM.flingRolls = k1; } };
if (typeof runChecks === "function") {
  const runChecksFF = runChecks;
  runChecks = function () {
    runChecksFF();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastFlick) return;
      out.textContent += "\n\nFlick and Fling:\n" + NM.lastFlick.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 80);
  };
}

// ===================================================================================================================
// Oil paint (the oil paint spec, docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md; MonoPaint's oil paint mode; the
// app's LoadedPaint): Smudge's Paint Load and Starting Load, Drag's and Smear's last dials (parts.js), whose own paint is
// laid solidly at first and runs out along the stroke, sooner the wider, until the brush only smudges (stroke.js
// oilSpend, every stamper's darkness); and the Filbert, Tip choice 9: a rounded oval that follows the pen's tilt, with
// Paint Load a crescent hollowed the way it moves, so a first touch is a sliver (engine.js stampBody). Every web stroke
// starts at its Starting Load: the app's rub on a grey has no twin here. Here the Filbert, its rare rolls and the checks.
// ===================================================================================================================
CHOICES.tip.push({ k: "filbert", label: "Filbert", dials: ["aspect", "angle", "weight", "taperIn", "taperOut", "dry", "wobble", "soft", "flick"], macro: "aspect", words: ["slim", "round"],
  range: { aspect: [.3, .55] }, def: { aspect: .5, soft: .15 }, blurb: "A rounded oval that follows the pen's tilt. With Paint Load a first touch is a crescent." });
// A variation never swaps to the Filbert (the app's ModularFeatures.seedOnly): only a roll's seed picks it.
const makeVarsOil = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.tip.slice();
  CHOICES.tip = keep.filter(c => c.k !== "filbert" || c.k === B.tip.k);
  try { return makeVarsOil(B, locks); } finally { CHOICES.tip = keep; }
};
// ---------- rolls: rare, from the seed, after every part (the app's ModularStyle.rollFilbert, rollPaintLoad) ----------
Object.assign(NM_ODDS, { filbert: { [WILDCARD]: 30, Oils: 3 }, pload: { Oils: 3 } });
Object.assign(NM_SALT, { filbert: 0xf11be, pload: 0x910ad });
NM.oilRolls = true;
function rollOil(style, B, lk, old) {
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i), odds = key => NM_ODDS[key][style] || 0;
  // An Oils (or Anything goes) round or flat nib becomes a Filbert, .35–.55 wide; an Oils Drag or Smear runs out, Paint Load .35–.75.
  if (NM.oilRolls && !(old && lk.parts.has("tip")) && odds("filbert") && (B.tip.k === "round" || B.tip.k === "flat") && u("filbert", 0) < 1 / odds("filbert")) {
    B.tip = switchChoice("tip", B.tip, "filbert"); B.tip.d.aspect = lerp(.35, .55, u("filbert", 1));
  }
  if (NM.oilRolls && !(old && lk.parts.has("carry")) && !lk.dials.has("carry.pload") && odds("pload") && "pload" in B.carry.d && u("pload", 0) < 1 / odds("pload")) B.carry.d.pload = lerp(.35, .75, u("pload", 1));
}
const rollNewFF = rollNew;
rollNew = function (style, B, lk, old) { rollNewFF(style, B, lk, old); rollOil(style, B, lk, old); };

// ---------- checks ----------
const OIL_PAINT = "oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l";
function oilChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 640, H = 240, P = PAPER_TONE;
  const oil = () => readAnyCode(OIL_PAINT).b;
  const line = (x0, y0, x1, y1, p = .8, tx = NaN, ty = NaN) => { const n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 2.5); return Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p, t: k * 4, tx, ty })); };
  const mean = (s, x0, x1, y0, y1) => { let a = 0, n = 0; for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { a += s.page[y * W + x]; n++; } return a / n; };
  const reach = (s, y) => { let m = 0; for (let x = 0; x < W; x++) if (s.page[y * W + x] < P - 20) m = x; return m; };
  // Codes: Oil paint as the app writes it; old Drag and Smear codes as before; the Filbert is Tip 9, nothing past it.
  { const olds = ["oils-drag.M90i00000000042_2zz00_2llp600_000_0_0000000000000000000nz0.1vtkv3y", "stump.M50i00000000yat_3eqlj00we_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud",
      "dirty-brush.M60i000000000at_1ulat00770000ee_3qzi00000we_000_1jz_0000000000000000000nz0.20j"];
    const b = oil(), off = cloneBrush(b); off.carry.d.pload = 0;
    t("Oil paint reads and writes as the app's (" + encodeM(b) + "); older Drag and Smear codes as before, Paint Load off",
      encodeM(b) === OIL_PAINT && b.tip.k === "filbert" && Math.abs(b.carry.d.pload - .55) < 1e-3 && b.carry.d.pmin === .25
      && olds.every(c => { const r = readAnyCode(c).b; return encodeM(r) === c && (r.carry.d.pload ?? 0) === 0 && !resolve(r, 60, 1).loaded; })
      && encodeM(normBrush(off)).split("_")[2].length === OIL_PAINT.split("_")[2].length - 4 && CHOICES.tip.findIndex(c => c.k === "filbert") === 9 && !readAnyCode(OIL_PAINT.replace(".M99", ".M9b"))); }
  // Rolls: with them off every roll is as before; on, a Filbert or Paint Load now and then (Oils, Anything goes), nothing else.
  { const roll = on => { const keep = Math.random, k0 = NM.oilRolls; Math.random = rng(7171); NM.oilRolls = on;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.oilRolls = k0; } };
    const a = roll(false), b = roll(true); let filberts = 0, loads = 0, odd = 0;
    for (let i = 0; i < a.length; i++) {
      if (encodeM(a[i]) === encodeM(b[i])) continue;
      const c = cloneBrush(b[i]), st = b[i].style;
      if (b[i].tip.k === "filbert") { filberts++; c.tip = cloneBrush(a[i]).tip; }
      if ((b[i].carry.d.pload ?? 0) > 0) { loads++; c.carry.d.pload = 0; }
      if (st !== "Oils" && st !== WILDCARD || (b[i].carry.d.pload ?? 0) > 0 && st !== "Oils" || encodeM(normBrush(c)).split(".M")[1] !== encodeM(a[i]).split(".M")[1]) odd++;
    }
    t("rolls: " + filberts + " Filberts, " + loads + " run out, none else changed", odd === 0 && filberts > 10 && loads > 10 && filberts < 140 && loads < 140); }
  // A loaded stroke runs out along its length, sooner pressed wider; past it, on paper, nothing.
  { const draw = (p, B = oil()) => { const s = modSurface(W, H, 7); layStroke(s, B, line(20, 120, 620, 120, p), 40, 1, null); return s; };
    const s = draw(.8), firm = draw(1), light = draw(.35), end = reach(s, 120);
    t("Oil paint lays solidly at first (" + mean(s, 25, 50, 115, 125).toFixed(0) + "), runs out by x " + end + " (firm " + reach(firm, 120) + ", light " + reach(light, 120) + "), nothing past it",
      mean(s, 25, 50, 115, 125) < 80 && end > 60 && end < 400 && mean(s, 560, 620, 100, 140) > P - .5 && reach(firm, 120) < reach(light, 120)); }
  // Empty (Starting Load 0), over paper it lays nothing.
  { const B = oil(); B.carry.d.pmin = 0; const s = modSurface(W, H, 7); layStroke(s, B, line(20, 120, 620, 120), 60, 1, null);
    t("an empty brush lays nothing on paper", mean(s, 0, W - 1, 0, H - 1) > P - .01); }
  // The Filbert: a tap leaning right is a crescent with Paint Load, its oval without.
  { const tap = B => { const s = modSurface(W, H, 7); layStroke(s, B, [{ x: 120, y: 120, p: .8, t: 0, tx: 40, ty: 0 }], 80, 1, null); let n = 0; for (const v of s.page) if (v < 128) n++; return n; };
    const on = oil(); on.carry.d.pmin = 1; const off = oil(); off.carry.d.pload = 0;
    const a = tap(on), b = tap(off);
    t("a Filbert's first touch: a crescent with Paint Load (" + a + " px), its oval without (" + b + " px)", a > 0 && a < .6 * b && resolve(on, 80, 1).body.crescent && !resolve(off, 80, 1).body.crescent); }
  // Words.
  { const B = oil();
    t("words: \"" + plainWords(B) + "\", \"" + captionItems(B).join(", ") + "\"", plainWords(B) === "Oil paint, filbert, on canvas. Runs out of paint, then only smudges. Rub a grey to load. Pressure widens."
      && captionItems(B).join(", ") === "Oils, filbert, runs out" && plainNameWords(B)[2] === "filbert" && ["oily", "loaded"].includes(plainNameWords(B)[1])); }
  return out;
}
const selfTestOil = selfTest;
selfTest = function () { const r = selfTestOil(); r.oil = oilChecks(); r.ok = r.ok && r.oil.every(c => c.ok); NM.lastOil = r.oil; return r; };
if (typeof runChecks === "function") {
  const runChecksOil = runChecks;
  runChecks = function () {
    runChecksOil();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastOil) return;
      out.textContent += "\n\nOil paint:\n" + NM.lastOil.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 100);
  };
}

// ===================================================================================================================
// Fading and Lemon Juice (the mischief modules spec, Stage 3; mischief-lab.html's dab3, shade3 and tick3; the app's
// FadingInk): Drying choices 5 and 6, laid as Quick lays them, then the mark changes by the clock. Fading fades away
// unevenly, thin parts and the paper's damp patches first, to nothing; Lemon Juice goes on as a faint trace and darkens
// in unevenly over its time, then is an ordinary dried mark. A mark is the page as the stroke found it and as it laid
// it, with when; each pixel shows a share of that change by its age (the app's FadingInk.share), stepped as coarsely as
// e-ink looks fine (Lemon Juice once a second, Fading a tenth of its time, 2 to 5 s). The test area keeps its marks and
// steps them on a timer; a sample tile, and Brush Studio's pages, show the stroke as laid. Here the choices, rolls,
// words, the test area's timer and the checks.
// ===================================================================================================================
Object.assign(DIALS.life, {
  fadeIn: { label: "Fades In", min: 10, max: 120, def: 20, fmt: v => Math.round(v) + " s" },
  dryIn: { label: "Dries In", min: 10, max: 600, def: 300, fmt: v => { const s = Math.round(v); return s < 60 ? s + " s" : Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"); } }
});
CHOICES.life.push(
  { k: "fade", label: "Fading", dials: ["fadeIn"], macro: "fadeIn", words: ["fast", "slow"], blurb: "Fades away unevenly, thin parts first, until it's gone." },
  { k: "lemon", label: "Lemon Juice", dials: ["dryIn"], macro: "dryIn", words: ["fast", "slow"], blurb: "Goes on as a faint trace and darkens in unevenly as it dries." });
const FADES = new Set(["fade", "lemon"]);
// The clock (wall time, ms): the checks set their own.
const FADE_CLOCK = { now: () => Date.now() };
const FADE_TRACE = 26, FADE_MOST = 1.25, LEMON_MOST = 1.3;
const fadeTime = B => fmtDial("life", B.life.k === "lemon" ? "dryIn" : "fadeIn", B.life.d[B.life.k === "lemon" ? "dryIn" : "fadeIn"]);

// ---------- resolve: Quick, and what the clock needs ----------
const resolveFade = resolve;
resolve = function (B, size, shade) {
  const k = B.life.k;
  if (!FADES.has(k)) return resolveFade(B, size, shade);
  const A = cloneBrush(B);
  A.life = freshPart("life", "wet");
  const R = resolveFade(A, size, shade);
  R.fade = { lemon: k === "lemon", ms: Math.max(1, Math.round(1000 * (k === "lemon" ? B.life.d.dryIn ?? 300 : B.life.d.fadeIn ?? 20))) };
  return R;
};

// ---------- the clock's shares (FadingInk.share, stepMs, mottle) ----------
function fadeShare(lemon, ms, dark, m, age) {
  if (lemon) { const u = clamp(age / (ms * (.7 + .6 * m))); return u >= 1 ? 1 : (FADE_TRACE + (255 - FADE_TRACE) * u * u * (3 - 2 * u)) / 255; }
  const left = 1 - age / (ms * (.35 + .65 * dark) * (.75 + .5 * m));
  return left <= 0 ? 0 : Math.min(1, left * 2.2);
}
const fadeStepMs = (lemon, ms) => lemon ? Math.max(1000, Math.min(15000, ms / 20)) : Math.max(2000, Math.min(5000, ms / 10));
const fadeEnd = M => M.born + Math.ceil(M.ms * (M.lemon ? LEMON_MOST : FADE_MOST));
const fadeNext = M => M.shown < 0 ? M.born : Math.min(fadeEnd(M), M.shown + fadeStepMs(M.lemon, M.ms));
// The mottle fixed to the page: its soft 16 px blobs and a pixel of grit (the prototype's mott: .7 and .3).
const fadeMottle = (s, x, y) => .7 * s.blob[y * s.w + x] + .3 * hash01(hashInts(0x2b, x, y), 0);
// What pixel i of mark M shows at clock time t (-1: as laid).
function fadeValue(s, M, j, t) {
  const b = M.before[j], a = M.after[j];
  if (t < 0 || b === a) return a;
  const x = M.x0 + j % M.rw, y = M.y0 + (j / M.rw | 0);
  return b + (a - b) * fadeShare(M.lemon, M.ms, Math.abs(a - b) / PAPER_TONE, fadeMottle(s, x, y), t - M.born);
}
// Mark M steps to clock time t: each pixel still showing what it last showed shows its share now. Returns the rectangle changed, or null.
function fadeStep(s, M, t) {
  let r = null;
  for (let j = 0; j < M.before.length; j++) {
    if (M.before[j] === M.after[j]) continue;
    const x = M.x0 + j % M.rw, y = M.y0 + (j / M.rw | 0), i = y * s.w + x, was = fadeValue(s, M, j, M.shown), next = fadeValue(s, M, j, t);
    if (was === next || Math.abs(s.page[i] - was) > 1e-3) continue;
    s.page[i] = next;
    r = r ? union(r, [x, y, x, y]) : [x, y, x, y];
  }
  M.shown = t;
  return r;
}
// At lift: the stroke's rectangle as found and as laid becomes a mark (fresh ink over a mark of its kind still changing
// changes from what lay under that one); with the test area's host it is kept and ticks, else the stroke stays as laid.
function fadeLift(st, r, found) {
  const s = st.surf, F = st.R.fade, rw = r[2] - r[0] + 1, rh = r[3] - r[1] + 1, before = new Float32Array(rw * rh), after = new Float32Array(rw * rh);
  for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) { const i = (y + r[1]) * s.w + x + r[0]; before[y * rw + x] = found[i]; after[y * rw + x] = s.page[i]; }
  const M = { lemon: F.lemon, ms: F.ms, born: FADE_CLOCK.now(), shown: -1, x0: r[0], y0: r[1], rw, rh, before, after };
  for (const O of s.fades || []) {
    if (O.lemon !== M.lemon) continue;
    for (let j = 0; j < before.length; j++) {
      const x = r[0] + j % rw, y = r[1] + (j / rw | 0), ox = x - O.x0, oy = y - O.y0;
      if (ox < 0 || oy < 0 || ox >= O.rw || oy >= O.rh) continue;
      const k = oy * O.rw + ox;
      if (O.before[k] !== O.after[k] && Math.abs(fadeValue(s, O, k, O.shown) - before[j]) < 1e-3) before[j] = O.before[k];
    }
  }
  (s.fades ??= []).push(M);
  return fadeStep(s, M, M.born);
}
// Every mark due by t steps there; those done leave the list. Returns the rectangle changed, or null.
function fadeAll(s, t) {
  let r = null;
  for (const M of s.fades || []) if (fadeNext(M) <= t) { const d = fadeStep(s, M, Math.min(t, fadeEnd(M))); if (d) r = r ? union(r, d) : d; }
  if (s.fades) s.fades = s.fades.filter(M => M.shown < fadeEnd(M));
  return r;
}
// The test area's timer: the next mark due, never while the pen is down; nothing runs without marks.
function fadeTicker(s) {
  if (s.fadeTicking) return;
  s.fadeTicking = true;
  const go = () => {
    if (TEST.surf !== s || !(s.fades && s.fades.length)) { s.fadeTicking = false; return; }
    if (!TEST.live) { const r = fadeAll(s, FADE_CLOCK.now()); if (r) showPage(TEST.ctx, s, r); }
    const due = s.fades.length ? Math.min(...s.fades.map(fadeNext)) : 0;
    setTimeout(go, Math.max(50, Math.min(1000, due - FADE_CLOCK.now())));
  };
  setTimeout(go, 50);
}
const StrokeFade = Stroke;
Stroke = class extends StrokeFade {
  end() {
    const s = this.surf, F = this.R.fade, live = F && typeof HOST !== "undefined" && this.host === HOST, keeps = F && (live || (this.host && this.host.fades === true));
    const found = keeps ? Float32Array.from(s.base) : null, r = super.end();
    if (!keeps || !r) return r;
    const d = fadeLift(this, r, found);
    if (live) fadeTicker(s);
    return d ? union(r, d) : r;
  }
};
// A clear page drops every mark.
const blankPageFade = blankPage;
blankPage = function (s) { blankPageFade(s); s.fades = []; };
// Variations never swap to them (the app's ModularFeatures.seedOnly).
const makeVarsFade = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.life.slice();
  CHOICES.life = keep.filter(c => !FADES.has(c.k) || c.k === B.life.k);
  try { return makeVarsFade(B, locks); } finally { CHOICES.life = keep; }
};

// ---------- rolls: rare, from the seed, after every part (the app's ModularStyle.rollFade) ----------
Object.assign(NM_ODDS, { fade: { [WILDCARD]: 40 }, lemon: { [WILDCARD]: 40, "Ink wash": 60 } });
Object.assign(NM_SALT, { fade: 0xfade5, lemon: 0x1e307 });
NM.fadeRolls = true; NM.lemonRolls = true;
function rollFade(style, B, lk, old) {
  if (old && lk.parts.has("life") || B.life.k === "float" || B.life.k === "halo") return;
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i), odds = key => NM_ODDS[key][style] || 0;
  if (NM.fadeRolls && odds("fade") && u("fade", 0) < 1 / odds("fade")) B.life = { k: "fade", d: { fadeIn: Math.round(lerp(15, 60, u("fade", 1))) } };
  else if (NM.lemonRolls && odds("lemon") && u("lemon", 0) < 1 / odds("lemon")) B.life = { k: "lemon", d: { dryIn: 30 * Math.round(lerp(6, 14, u("lemon", 1))) } };
}
const rollNewOil = rollNew;
rollNew = function (style, B, lk, old) { rollNewOil(style, B, lk, old); rollFade(style, B, lk, old); };

// ---------- words (docs/TEXT-STYLE.md): said plainly ----------
const choiceWordsFade = choiceWords;
choiceWords = function (B, part) {
  // Fading says how soon it goes ("Fading 20 s"); Lemon Juice's time would pass the module's line.
  if (part === "life" && B.life.k === "fade") return "Fading " + fadeTime(B);
  if (part === "life" && B.life.k === "lemon") return "Lemon juice";
  return choiceWordsFade(B, part);
};
const captionItemsFade = captionItems;
captionItems = function (B) {
  const items = captionItemsFade(B);
  if (!FADES.has(B.life.k)) return items;
  // After the style and the new choices' tags (resist, Blend, Print Shading, a pattern tip), as Mold's: "Anything goes, fading".
  const mk = B.medium.k, bl = mk === "resist" || mk === "tone" ? 0 : B.medium.d.blend | 0, sc = mk === "tone" ? 0 : B.medium.d.screen | 0;
  items.splice(1 + (mk === "resist" || mk === "tone" ? 1 : 0) + (bl ? 1 : 0) + (sc ? 1 : 0) + (PATTERN_TIPS.has(B.tip.k) ? 1 : 0), 0, B.life.k === "fade" ? "fading" : "lemon juice");
  return items;
};
const plainWordsFade = plainWords;
plainWords = function (B) {
  const text = plainWordsFade(B);
  if (!FADES.has(B.life.k)) return text;
  // In the drying's place, before how it answers the hand.
  const said = B.life.k === "fade" ? "Fades away in " + fadeTime(B) + "." : "Darkens in as it dries.", hw = handWords(B, 2), control = hw !== "steady" ? cap1(hw) + "." : null;
  const all = text.split(/(?<=\.)\s+/).filter(Boolean), at = control && all[all.length - 1] === control ? all.length - 1 : all.length;
  all.splice(at, 0, said);
  let t2 = all[0];
  for (let i = 1; i < all.length; i++) { if (i >= 2 && t2.length + 1 + all[i].length > 260) break; t2 += " " + all[i]; }
  return t2;
};

// ---------- checks ----------
function fadeChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 400, H = 240, P = PAPER_TONE, keep = FADE_CLOCK.now;
  const mk = (life, d) => {
    const B = { name: "f", seed: 23, style: "Brush pen", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", life), hand: freshPart("hand", "darker") };
    Object.assign(B.life.d, d || {});
    return normBrush(B);
  };
  const line = () => Array.from({ length: 61 }, (_, i) => ({ x: 40 + 320 * i / 60, y: 120 + 30 * Math.sin(i / 9), p: .3 + .6 * i / 60, t: 1000 + 8 * i, tx: NaN, ty: NaN }));
  const dark = s => { let n = 0; for (const v of s.page) n += P - v; return n; };
  let clock = 1e6; FADE_CLOCK.now = () => clock;
  try {
    // Codes: Drying 5 and 6, written and read back; every older code as before.
    { const f = mk("fade", { fadeIn: 45 }), l = mk("lemon", { dryIn: 150 }), cf = encodeM(f), cl = encodeM(l), bf = readAnyCode(cf).b, bl = readAnyCode(cl).b;
      t("Fading and Lemon Juice are Drying 5 and 6, read back (" + cf.split("_")[4] + ", " + cl.split("_")[4] + ")", cf.split("_")[4][0] === "5" && cl.split("_")[4][0] === "6"
        && encodeM(bf) === cf && encodeM(bl) === cl && Math.abs(bf.life.d.fadeIn - 45) < .1 && Math.abs(bl.life.d.dryIn - 150) < .5
        && CHOICES.life.findIndex(c => c.k === "fade") === 5 && CHOICES.life.findIndex(c => c.k === "lemon") === 6); }
    // Rolls: off, every roll as before; on, Fading or Lemon Juice now and then, nothing else changed.
    { const roll = on => { const k = Math.random, k0 = NM.fadeRolls, k1 = NM.lemonRolls; Math.random = rng(8181); NM.fadeRolls = NM.lemonRolls = on;
        try { return Array.from({ length: 2800 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = k; NM.fadeRolls = k0; NM.lemonRolls = k1; } };
      const a = roll(false), b = roll(true); let fades = 0, lemons = 0, odd = 0;
      for (let i = 0; i < a.length; i++) {
        if (encodeM(a[i]) === encodeM(b[i])) continue;
        if (b[i].life.k === "fade") fades++; else if (b[i].life.k === "lemon") lemons++; else { odd++; continue; }
        const c = cloneBrush(b[i]); c.life = cloneBrush(a[i]).life;
        if (brushKey(normBrush(c)) !== brushKey(a[i]) || (b[i].life.k === "fade" && b[i].style !== WILDCARD) || (b[i].life.k === "lemon" && b[i].style !== WILDCARD && b[i].style !== "Ink wash")) odd++;
      }
      t("rolls: " + fades + " fading, " + lemons + " lemon juice, none else changed", odd === 0 && fades > 0 && lemons > 0 && fades + lemons < 40); }
    // Words.
    { const f = mk("fade"), l = mk("lemon");
      t("words: \"" + plainWords(f) + "\", \"" + plainWords(l) + "\", " + captionItems(f).join(", ") + "; " + choiceWords(f, "life") + ", " + choiceWords(l, "life"),
        plainWords(f).includes("Fades away in 20 s.") && plainWords(l).includes("Darkens in as it dries.") && captionItems(f).includes("fading") && captionItems(l).includes("lemon juice")
        && choiceWords(f, "life") === "Fading 20 s" && choiceWords(l, "life") === "Lemon juice" && ["fading", "ghostly"].includes(plainNameWords(f)[1]) && ["secret", "sour"].includes(plainNameWords(l)[1])); }
    // The clock: Fading thins out to nothing, thin parts first; Lemon Juice starts faint and ends as Quick lays it.
    { const host = { fades: true }, s = modSurface(W, H, 7), q = modSurface(W, H, 7);
      layStroke(s, mk("fade"), line(), 14, 1, host); layStroke(q, mk("wet"), line(), 14, 1, null);
      const start = dark(s), laid = Float32Array.from(s.page);
      fadeAll(s, clock + 12000);
      let faint = 0, faintGone = 0, core = 0, coreGone = 0;
      for (let i = 0; i < laid.length; i++) { if (laid[i] >= P - 1) continue; const gone = s.page[i] >= P - 1; if (laid[i] > P * .65) { faint++; if (gone) faintGone++; } else if (laid[i] < P * .25) { core++; if (gone) coreGone++; } }
      fadeAll(s, clock + 26000);
      t("Fading lays as Quick (" + start.toFixed(0) + "), thin parts go first (at 12 s " + faintGone + " of " + faint + " faint px, " + coreGone + " of " + core + " dark), and reaches nothing",
        Math.abs(start - dark(q)) < 1e-6 && faint > 20 && core > 20 && faintGone / faint > coreGone / core + .2 && dark(s) < 1e-6 && !s.fades.length);
      const l = modSurface(W, H, 7); layStroke(l, mk("lemon"), line(), 14, 1, host);
      const trace = dark(l);
      fadeAll(l, clock + 2000); const mid = dark(l);
      fadeAll(l, clock + 6000);
      t("Lemon Juice goes on faint (" + (trace / dark(q) * 100).toFixed(0) + "%), darkens in (" + (mid / dark(q) * 100).toFixed(0) + "% at 2 s) and ends as Quick lays it",
        trace > dark(q) * .06 && trace < dark(q) * .16 && mid > trace && mid < dark(q) && l.page.every((v, i) => Math.abs(v - q.page[i]) < 1e-4) && !l.fades.length);
      const sample = modSurface(W, H, 7); layStroke(sample, mk("lemon"), line(), 14, 1, null);
      t("a sample tile shows it as laid, nothing ticking", !(sample.fades && sample.fades.length) && sample.page.every((v, i) => Math.abs(v - q.page[i]) < 1e-4)); }
  } finally { FADE_CLOCK.now = keep; }
  return out;
}
const selfTestFade = selfTest;
selfTest = function () { const r = selfTestFade(); r.fade = fadeChecks(); r.ok = r.ok && r.fade.every(c => c.ok); NM.lastFade = r.fade; return r; };
if (typeof runChecks === "function") {
  const runChecksFade = runChecks;
  runChecks = function () {
    runChecksFade();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastFade) return;
      out.textContent += "\n\nFading and Lemon Juice:\n" + NM.lastFade.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 120);
  };
}

// ===================================================================================================================
// Stamp (mischief-lab-2.html's second pad; the owner: "have it function like a regular brush … just with a potato stamp
// nib … so then the stamp and nib are the same thing"; the app's StampNib and StampStamper): Tip choice 10, a nib cut
// from the page by lasso, printed every Spacing along the line (close, a nib trail; apart, separate prints; a tap one
// print), turned with the line or not (Turns), potato-stamp inked: blotchy, lighter each print by Ink Fade until the pen
// rests about a second, darker with pressure. The medium lays it as any mark. Its nib is kept beside the code (NM.nibs
// by its id, the app's nib store; here localStorage too), the code carrying the id after the six (parts.js nibSeg). A
// Stamp whose nib isn't here prints this lab's default star (the app's prints round). Never rolled: it needs a cut.
// ===================================================================================================================
Object.assign(DIALS.tip, {
  spacing: { label: "Spacing", min: .05, max: 1.5, def: .12, fmt: v => Math.round(v * 100) + "%" },
  inkFade: { label: "Ink Fade", min: 0, max: .3, def: .22, fmt: v => v < .005 ? "None" : v > .295 ? "Strong" : Math.round(v * 100) + "%" }
});
CHOICES.tip.push({ k: "stamp", label: "Stamp", dials: ["weight", "spacing", "headTurn", "inkFade", "flick"], macro: "spacing", words: ["trail", "prints"],
  labels: { headTurn: "Turns" }, opts: { headTurn: ["On", "Off"] }, def: { headTurn: 0 }, blurb: "Stamps a shape cut from the page." });
PATTERN_TIPS.add("stamp");
const STAMP = { SIZE: 132, REST_MS: 1000, FLOOR: .25, TURN: .35, JIT: .04, MOST: 256, INK: .05 };
// A Stamp's Medium is its own in every style (the app's ModularSizes.STAMP).
const styleSizeSt = styleSize;
styleSize = function (name, B) { return B && B.tip && B.tip.k === "stamp" ? STAMP.SIZE : styleSizeSt(name, B); };
// A variation never swaps to the Stamp (the app's ModularFeatures.seedOnly), and no roll picks it.
const makeVarsSt = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.tip.slice();
  CHOICES.tip = keep.filter(c => c.k !== "stamp" || c.k === B.tip.k);
  try { return makeVarsSt(B, locks); } finally { CHOICES.tip = keep; }
};

// ---------- nibs: { w, h, a (coverage 0–255), id }, the id a hash of its size and pixels (the app's StampNib) ----------
NM.nibs = new Map();
function makeNib(w, h, a) {
  let id = 0x811c9dc5;
  id = Math.imul(id ^ w, 16777619); id = Math.imul(id ^ h, 16777619);
  for (let i = 0; i < a.length; i++) id = Math.imul(id ^ a[i], 16777619);
  return { w, h, a, id: (id >>> 0) || 1 };
}
function inLasso(x, y, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) { const [xi, yi] = poly[i], [xj, yj] = poly[j]; if ((yi > y) !== (yj > y) && x < xj + (y - yj) / (yi - yj) * (xi - xj)) inside = !inside; }
  return inside;
}
// The default star: a solid five-pointed star 48 px across (mischief-lab-2.html's first stamp), each pixel 4 × 4 samples.
const STAR_NIB = (() => {
  const d = 48, a = new Uint8Array(d * d), poly = [];
  for (let k = 0; k < 10; k++) { const t = -Math.PI / 2 + k * Math.PI / 5, r = (k % 2 ? .45 : 1) * (d / 2 - .5); poly.push([d / 2 + r * Math.cos(t), d / 2 + r * Math.sin(t)]); }
  for (let y = 0; y < d; y++) for (let x = 0; x < d; x++) { let n = 0; for (let j = 0; j < 4; j++) for (let i = 0; i < 4; i++) if (inLasso(x + (i + .5) / 4, y + (j + .5) / 4, poly)) n++; a[y * d + x] = Math.round(255 * n / 16); }
  return makeNib(d, d, a);
})();
const NIB_STORE = "inkwash.modlab.nib.";
function keepNib(nib) {
  NM.nibs.set(nib.id, nib);
  try { localStorage.setItem(NIB_STORE + nib.id.toString(36), JSON.stringify({ w: nib.w, h: nib.h, a: Array.from(nib.a) })); } catch (e) { /* storage off: kept until the page closes */ }
  return nib.id;
}
// Nib id's pixels, or null: kept here, or in localStorage from an earlier visit.
function nibOf(id) {
  if (!id) return null;
  let n = NM.nibs.get(id >>> 0);
  if (!n) try { const o = JSON.parse(localStorage.getItem(NIB_STORE + (id >>> 0).toString(36)) || "null"); if (o) { n = makeNib(o.w, o.h, Uint8Array.from(o.a)); if (n.id !== id >>> 0) n = null; else NM.nibs.set(n.id, n); } } catch (e) { n = null; }
  return n || null;
}
// cut2 (the app's StampNib.cut): the ink inside the lasso pts ([x, y] page px, closed back to its start; even-odd) on
// surface s, trimmed to the pixels with ink, scaled down to 256 on its longer side if bigger; null for nothing to cut.
function cutNib(s, pts) {
  if (pts.length < 3) return null;
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), P = PAPER_TONE;
  const x0 = Math.max(0, Math.floor(Math.min(...xs))), x1 = Math.min(s.w - 1, Math.ceil(Math.max(...xs))), y0 = Math.max(0, Math.floor(Math.min(...ys))), y1 = Math.min(s.h - 1, Math.ceil(Math.max(...ys)));
  if (x1 < x0 || y1 < y0) return null;
  const bw = x1 - x0 + 1, ink = new Float32Array(bw * (y1 - y0 + 1));
  let tx0 = Infinity, ty0 = Infinity, tx1 = -1, ty1 = -1;
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    if (!inLasso(x + .5, y + .5, pts)) continue;
    const v = clamp((P - s.page[y * s.w + x]) / P);
    if (!(v > STAMP.INK)) continue;
    ink[(y - y0) * bw + x - x0] = v;
    tx0 = Math.min(tx0, x); tx1 = Math.max(tx1, x); ty0 = Math.min(ty0, y); ty1 = Math.max(ty1, y);
  }
  if (tx1 < 0) return null;
  const w = tx1 - tx0 + 1, h = ty1 - ty0 + 1, k = Math.max(1, Math.max(w, h) / STAMP.MOST), sw = Math.max(1, Math.round(w / k)), sh = Math.max(1, Math.round(h / k)), a = new Uint8Array(sw * sh);
  for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) {
    const fx0 = Math.floor(x * k), fx1 = Math.max(fx0 + 1, Math.min(w, Math.floor((x + 1) * k))), fy0 = Math.floor(y * k), fy1 = Math.max(fy0 + 1, Math.min(h, Math.floor((y + 1) * k)));
    let sum = 0;
    for (let yy = fy0; yy < fy1; yy++) for (let xx = fx0; xx < fx1; xx++) sum += ink[(ty0 - y0 + yy) * bw + tx0 - x0 + xx];
    a[y * sw + x] = Math.round(255 * sum / ((fx1 - fx0) * (fy1 - fy0)));
  }
  return makeNib(sw, sh, a);
}
const nibAt = (n, x, y) => x < 0 || y < 0 || x >= n.w || y >= n.h ? 0 : n.a[y * n.w + x] / 255;
function nibSample(n, u, v) {
  const x = Math.floor(u), y = Math.floor(v), fx = u - x, fy = v - y;
  return (nibAt(n, x, y) * (1 - fx) + nibAt(n, x + 1, y) * fx) * (1 - fy) + (nibAt(n, x, y + 1) * (1 - fx) + nibAt(n, x + 1, y + 1) * fx) * fy;
}
function stampNoise(x, y, seed) {
  const ix = Math.floor(x), iy = Math.floor(y), fx = smoothstep(0, 1, x - ix), fy = smoothstep(0, 1, y - iy), c = (i, j) => hash01(hashInts(seed, i, j), 0);
  const top = c(ix, iy) + (c(ix + 1, iy) - c(ix, iy)) * fx;
  return top + (c(ix, iy + 1) + (c(ix + 1, iy + 1) - c(ix, iy + 1)) * fx - top) * fy;
}

// ---------- resolve: the pattern's stamp fields ----------
const resolveSt = resolve;
resolve = function (B, size, shade) {
  const R = resolveSt(B, size, shade);
  if (B.tip.k === "stamp" && R.pat) {
    const mk = B.medium.k, lineMedium = mk === "ink" || mk === "charcoal" || mk === "graphite" || mk === "resist" || mk === "tone";
    Object.assign(R.pat, { size: lineMedium ? R.inkSize : R.washSize, spacing: B.tip.d.spacing ?? .12, fade: B.tip.d.inkFade ?? .22, turns: ((B.tip.d.headTurn ?? 0) | 0) === 0, nib: nibOf(B.nib) || STAR_NIB });
  }
  return R;
};

// ---------- the stamper (print2 and place2; the app's StampStamper) ----------
function stampPrint(s, buf, nib, cx, cy, size, ang, amount, dk, seed, grit, d, rect) {
  if (!(size > 0) || !(dk > 0)) return;
  const w = s.w, k = size / Math.max(nib.w, nib.h), ca = Math.cos(ang), sa = Math.sin(ang), R = Math.hypot(nib.w, nib.h) * k / 2 + 2;
  const x0 = Math.max(0, Math.floor(cx - R)), x1 = Math.min(w - 1, Math.ceil(cx + R)), y0 = Math.max(0, Math.floor(cy - R)), y1 = Math.min(s.h - 1, Math.ceil(cy + R));
  if (x1 < x0 || y1 < y0) return;
  const cell = Math.max(2, Math.max(nib.w, nib.h) / 12), hw = nib.w / 2 - .5, hh = nib.h / 2 - .5;
  for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
    const dx = px + .5 - cx, dy = py + .5 - cy, u = (dx * ca + dy * sa) / k + hw, q = (-dx * sa + dy * ca) / k + hh;
    if (u < -1 || q < -1 || u > nib.w || q > nib.h) continue;
    const cov = nibSample(nib, u, q);
    if (!(cov > .01)) continue;
    const n = .65 * stampNoise(u / cell, q / cell, seed) + .35 * hash01(hashInts(grit, Math.floor(u), Math.floor(q)), 0);
    const c = cov * clamp((amount * 1.1 - (1 - n)) * 3 + .1) * dk, i = py * w + px;
    if (c > buf[i]) { buf[i] = c; if (WET) WET[i] = d; }
  }
  grow(rect, [x0, y0, x1, y1]);
}
function stampStamp(st, buf, from, M) {
  const P = st.R.pat, s = st.surf, pts = st.pts, rect = [s.w, s.h, -1, -1], size = P.size;
  if (!pts.length) return rect;
  if (M.load === undefined) {
    M.seed = hashInts(P.seed ^ 0x57a3b, Math.round(pts[0].x * 4), Math.round(pts[0].y * 4));
    M.jit = (hash01(M.seed, 1) * 2 - 1) * STAMP.JIT; M.next = 0; M.prints = 0; M.dir = NaN;
    // The surface's last stamp left its ink; a rest of over a second since its last print inks it afresh.
    const left = s.stampInk, t0 = pts[0].t;
    M.load = !left || !(t0 - left.last <= STAMP.REST_MS) ? 1 : Math.max(STAMP.FLOOR, left.load);
  }
  const gap = Math.max(1, P.spacing * size);
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d, ux = seg > 0 ? (p1.x - p0.x) / seg : 1, uy = seg > 0 ? (p1.y - p0.y) / seg : 0;
    while (M.next <= p1.d) {
      const t = seg > 0 ? clamp((M.next - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t);
      const press = M.prints === 0;
      if (!press && seg > 0) {
        const head = Math.atan2(uy, ux);
        if (Number.isNaN(M.dir)) M.dir = head;
        let dd = head - M.dir; while (dd > Math.PI) dd -= 2 * Math.PI; while (dd < -Math.PI) dd += 2 * Math.PI;
        M.dir += dd * STAMP.TURN;
      }
      const ang = P.turns && !Number.isNaN(M.dir) ? M.dir : M.jit;
      const sz = size * (P.ps ? lerp(1, .35 + .65 * pr, P.ps) : 1) * (pts.flick ? flickWidth(pts.flick, M.next) : 1);
      const amount = M.load * (.55 + .6 * pr) * (p1.own === undefined ? 1 : lerp(p0.own, p1.own, t)), dk = handDark(P.dark, pr, v, 0);
      const seed = P.spacing >= 1 ? hashInts(M.seed, M.prints, 7) : M.seed, grit = hashInts(M.seed ^ 0x9e37, M.prints, 3);
      stampPrint(s, buf, P.nib, x, y, sz, ang, amount, dk, seed, grit, M.next, rect);
      M.load = Math.max(STAMP.FLOOR, M.load * Math.pow(1 - P.fade, press ? 1 : Math.min(1, P.spacing * P.spacing)));
      M.last = lerp(p0.t, p1.t, t); M.prints++;
      M.next += gap;
    }
  }
  // What the stamp has left goes with the surface, for its next stroke.
  if (M.prints) s.stampInk = { load: M.load, last: M.last };
  return rect;
}
const stampPatternSt = stampPattern;
stampPattern = function (st, buf, size, from, which) {
  return st.R.pat.kind === "stamp" ? stampStamp(st, buf, from, st["nm" + which] ??= {}) : stampPatternSt(st, buf, size, from, which);
};

// ---------- words: "Ink stamp. Stamps a shape cut from the page. Fades as it prints." ----------
const plainWordsSt = plainWords;
plainWords = function (B) {
  if (B.tip.k !== "stamp") return plainWordsSt(B);
  const sent = plainWordsSt(B).split(/(?<=\.)\s+/).filter(Boolean), first = sent.shift() || "";
  // The first sentence names it by what it lays (a stipple's way): "Ink stamp.", "Wash stamp on felt paper."
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(first), tail = m && m[2] ? m[2] : "";
  const out = [NM_BASE[B.medium.k] ? NM_BASE[B.medium.k] + " stamp" + tail + "." : first, "Stamps a shape cut from the page."];
  if ((B.tip.d.inkFade ?? .22) >= .005) out.push("Fades as it prints.");
  let t = out.join(" ");
  for (const x of sent) { if (t.length + 1 + x.length > 260) break; t += " " + x; }
  return t;
};
const captionItemsSt = captionItems;
captionItems = function (B) {
  if (B.tip.k !== "stamp") return captionItemsSt(B);
  const items = captionItemsSt(B).filter(t => t !== "stamp");
  items.splice(1, 0, "stamp");
  return items;
};

// ---------- checks ----------
function stampChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 640, H = 240, P = PAPER_TONE;
  const mk = (d, medium) => normBrush({ name: "potato", seed: 17, style: "Fineliner", tip: Object.assign(freshPart("tip", "stamp"), { d: Object.assign(freshPart("tip", "stamp").d, d || {}) }),
    medium: freshPart("medium", medium || "ink"), carry: freshPart("carry", "off"), paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: freshPart("hand", "steady") });
  const line = (x0, x1, y, t0) => { const n = Math.ceil((x1 - x0) / 3); return Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y, p: .7, t: t0 + 2 * k })); };
  const draw = (B, pts) => { const s = modSurface(W, H, 7); layStroke(s, B, pts, 40, 1, null); return s; };
  const inkAt = (s, x0, x1) => { let a = 0; for (let y = 60; y < 180; y++) for (let x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) a += P - s.page[y * W + x]; return a; };
  // Codes: Tip choice 10, its nib after the six, every other code as before; a Stamp with no nib writes six.
  { const B = mk({ spacing: .5 }); B.nib = keepNib(STAR_NIB);
    const c = encodeM(B), back = readAnyCode(c).b, bare = encodeM(mk());
    t("a Stamp's code carries its nib (" + c + ") and reads back", c.split(".")[1].split("_").length === 7 && back.nib === STAR_NIB.id && encodeM(back) === c
      && bare.split(".")[1].split("_").length === 6 && CHOICES.tip.findIndex(ch => ch.k === "stamp") === 10 && brushKey(B) !== brushKey(mk({ spacing: .5 }))); }
  // The lasso cut keeps the ink inside it, trimmed; nothing inside, nothing.
  { const s = modSurface(W, H, 7); for (let y = 30; y <= 43; y++) for (let x = 50; x <= 69; x++) s.page[y * W + x] = 0; for (let y = 30; y <= 50; y++) for (let x = 200; x <= 220; x++) s.page[y * W + x] = 0;
    const n = cutNib(s, [[40, 20], [90, 20], [90, 60], [40, 60]]);
    t("the lasso cut is the ink inside it, trimmed (" + (n ? n.w + " × " + n.h : "none") + "); an empty lasso cuts nothing", n && n.w === 20 && n.h === 14 && n.a[0] === 255 && !cutNib(s, [[300, 100], [340, 100], [340, 140]])); }
  // Prints at Spacing: apart, paper between; Ink Fade None keeps every print as dark; on, they fade.
  { const gap = 48, none = draw(mk({ spacing: 1.2, inkFade: 0 }), line(30, 630, 120, 1000)), on = draw(mk({ spacing: 1.2, inkFade: .22 }), line(30, 630, 120, 1000));
    const at = (s, k) => inkAt(s, 30 + k * gap - 23, 30 + k * gap + 23), between = inkAt(none, 30 + gap / 2 - 1, 30 + gap / 2 + 1);
    const n = [0, 3, 6, 9].map(k => at(none, k)), f = [0, 3, 6].map(k => at(on, k));
    t("prints every Spacing, paper between (" + between.toFixed(0) + "); Ink Fade None as dark each print (" + n.map(v => v.toFixed(0)).join(", ") + "), on fading (" + f.map(v => v.toFixed(0)).join(", ") + ")",
      between < 1 && Math.min(...n) > .9 * Math.max(...n) && f[0] > 1.5 * f[1] && f[1] >= f[2] * .99); }
  // A rest inks it afresh: two taps close together, the second lighter; a third after a rest as dark as the first.
  { const s = modSurface(W, H, 7), tap = (x, t0) => { layStroke(s, mk({ spacing: 1.2 }), [{ x, y: 120, p: .7, t: t0 }], 40, 1, null); return inkAt(s, x - 30, x + 30); };
    const a = tap(100, 1000), b = tap(300, 1400), c = tap(500, 3400);
    t("taps: soon after, lighter (" + b.toFixed(0) + " of " + a.toFixed(0) + "); after a rest, afresh (" + c.toFixed(0) + ")", b < .9 * a && c > .9 * a); }
  // Without its nib: this lab's star.
  { const B = mk(); B.nib = 0x5eed; t("a Stamp without its nib prints the default star", resolve(B, 60, 1).pat.nib === STAR_NIB); }
  // Words and rolls.
  { const B = mk(), none = mk({ inkFade: 0 }), roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).tip.k === "stamp") return false; return true; };
    t("words: \"" + plainWords(B) + "\"; never rolled", plainWords(B).startsWith("Ink stamp. Stamps a shape cut from the page. Fades as it prints.") && !plainWords(none).includes("Fades")
      && captionItems(B).includes("stamp") && roll()); }
  return out;
}
const selfTestSt = selfTest;
selfTest = function () { const r = selfTestSt(); r.stamp = stampChecks(); r.ok = r.ok && r.stamp.every(c => c.ok); NM.lastStamp = r.stamp; return r; };
if (typeof runChecks === "function") {
  const runChecksSt = runChecks;
  runChecks = function () {
    runChecksSt();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastStamp) return;
      out.textContent += "\n\nStamp:\n" + NM.lastStamp.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 120);
  };
}

// ===================================================================================================================
// Crayon (mischief-lab-2.html's fourth pad; spec its NOTES "## 4. Crayon"; the app's PartChoice.CRAYON): Medium choice
// 7, Wax and Scratch. Wax on toothy paper: a light touch catches only the grain's peaks, a hard one goes solid; wax
// already laid lifts the surface, so layers build up; the edge is ragged, from noise fixed to the page, so a stroke
// drawn again matches. Its wax goes into the resist planes (Resist's, above) all kept, so washes and the rest slide off
// it and the eraser doesn't lift it; a later crayon takes on it (only masking fluid shields a crayon). Scratch On: a thin
// blade that scrapes back to white. Drawn as a charcoal line (its code and dials its own), its compose rule "crayon".
// Never rolled, never a variation's swap; its Medium size 30 px in every style (the app's ModularSizes.CRAYON).
// ===================================================================================================================
Object.assign(DIALS.medium, {
  wax: { label: "Wax", min: 0, max: 1, def: .6 },
  scratch: { label: "Scratch", choices: ["Off", "On"], def: 0 }
});
CHOICES.medium.push({ k: "crayon", label: "Crayon", dials: ["wax", "scratch"], macro: "wax", words: ["chalky", "waxy"], blurb: "Wax on the paper's tooth. Washes slide off it." });
REAL_ERASE.crayon = 1;
const CRAYON = { SIZE: 30, SCRATCH: .3, REACH: 1.35, WAX: .6, MOST_MAPS: 4 };
const crayonScratch = B => (B.medium.d.scratch | 0) === 1;
// Its Medium size in every style; a Stamp's own first, as the app's ModularSizes.size.
const styleSizeCr = styleSize;
styleSize = function (name, B) { return B && B.medium && B.medium.k === "crayon" && !(B.tip && B.tip.k === "stamp") ? CRAYON.SIZE : styleSizeCr(name, B); };
// A variation never swaps to the Crayon, and no roll picks it (the app's ModularFeatures.seedOnly).
const makeVarsCr = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.medium.slice();
  CHOICES.medium = keep.filter(c => c.k !== "crayon" || c.k === B.medium.k);
  try { return makeVarsCr(B, locks); } finally { CHOICES.medium = keep; }
};

// ---------- the page's own noise, the same for every crayon: tooth, the ragged edge, grit (kept by page size) ----------
const CRAYON_MAPS = new Map();
function crayonMaps(w, h) {
  const key = w + "x" + h;
  let M = CRAYON_MAPS.get(key);
  if (M) return M;
  const n = w * h, tooth = new Float32Array(n), rag = new Float32Array(n), grit = new Float32Array(n);
  for (let y = 0, i = 0; y < h; y++) for (let x = 0; x < w; x++, i++) {
    // Tooth: soft bumps 3 px across with a pixel of grit. The edge: notches ~7 px apart, a waver ~2.5 px, a pixel of grit.
    tooth[i] = .55 * stampNoise(x / 3, y / 3, 83) + .45 * hash01(hashInts(89, x, y), 0);
    rag[i] = .55 * stampNoise(x / 7, y / 7, 91) + .3 * stampNoise(x / 2.5, y / 2.5, 97) + .15 * hash01(hashInts(103, x, y), 0);
    grit[i] = hash01(hashInts(107, x, y), 0);
  }
  CRAYON_MAPS.set(key, M = { tooth, rag, grit });
  if (CRAYON_MAPS.size > CRAYON.MOST_MAPS) CRAYON_MAPS.delete(CRAYON_MAPS.keys().next().value);
  return M;
}
// The width's wobble, ±10 % along the stroke: noise ~22 px across, read at each dab's centre.
const crayonWobble = (x, y) => .9 + .2 * stampNoise(x / 22, y / 22, 101);

// ---------- resolve: a charcoal line, crisp and as dark as the swatch, with the crayon's rule ----------
// The brush the engine draws a crayon as: on paper (Float laid as Quick), smearing nothing (as Resist's).
function crayonAs(B) {
  const A = cloneBrush(B);
  A.medium = freshPart("medium", "charcoal"); Object.assign(A.medium.d, { spread: 0, hold: 1, dust: 0, erase: 1 });
  if (A.life.k === "float") A.life = freshPart("life", "wet");
  A.carry = freshPart("carry", "off");
  return A;
}
const resolveCr = resolve;
resolve = function (B, size, shade) {
  if (B.medium.k !== "crayon") return resolveCr(B, size, shade);
  const R = resolveCr(crayonAs(B), size, shade), scratch = crayonScratch(B);
  R.rule = "crayon"; R.settle = 0;
  R.crayon = { wax: B.medium.d.wax ?? CRAYON.WAX, scratch, tooth: B.paper.d.tooth ?? 0 };
  // Scratch: a blade a third as wide, laying white by its darkness, with a plain crisp nib.
  if (scratch) { R.inkSize *= CRAYON.SCRATCH; R.blend = "lighten"; } else R.pen.crayon = true;
  return R;
};

// ---------- the line: each dab's rim wobbles; past .6 of it only the peaks take wax; the edge ragged, with flecks ----------
// V, what the line buffer holds: the pressure (less up to .22 toward the edge) times the hand's darkness, the strongest
// per pixel. Dabs on a fixed distance grid, a tenth of the size apart, so the Quick window's re-lay lands the same dabs.
function crayonLine(surf, b, pts, size, final, from, clip, meta, win) {
  const { w, h, scratch: buf } = surf, M = crayonMaps(w, h), rect = [w, h, 0, 0];
  if (!pts.length) return rect;
  const total = pts[pts.length - 1].d, [inLen, outLen] = taperLens(b, size), fl = pts.flick, sp = Math.max(.4, size * .1);
  const cosA = Math.cos((b.angle ?? 45) * DEG), sinA = Math.sin((b.angle ?? 45) * DEG), asp = b.aspect ?? 1, tiltOn = (b.tiltSize || 0) + (b.tDark || 0) > 0;
  const cl = clip ? clip[0] : 0, ct = clip ? clip[1] : 0, cr = clip ? clip[2] : w - 1, cb = clip ? clip[3] : h - 1;
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    for (let j = k === 1 ? 0 : Math.floor(p0.d / sp) + 1, d = j * sp; d <= p1.d; j++, d = j * sp) {
      if (win && (d < win.dMin || d >= win.dMax)) continue;
      const t = seg > 0 ? clamp((d - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t);
      let wd = size * lerp(1 - .75 * b.pressure, 1 + .6 * b.pressure, pr) * clamp(1 + b.speed * .55 * (clamp(v / 2.5) - .3), .35, 1.6), lean = 0;
      if (inLen > 0) wd *= Math.pow(clamp(d / inLen), .6) * .92 + .08;
      if (fl) wd *= flickWidth(fl, d);
      if (b.fast) { if (b.taperOut > 0) wd *= lerp(1, Math.pow(p0.pk > 0 ? clamp(pr / (p0.pk * .7)) : 1, .8) * .92 + .08, b.taperOut); }
      else if (final && outLen > 0) wd *= Math.pow(clamp((total - d) / outLen), .6) * .92 + .08;
      if (win && win.tail) wd *= lerp(1, Math.pow(clamp((total - d) / win.tail.len), .6) * .92 + .08, win.tail.amt);
      if (tiltOn) { const T = tiltOf(lerp(p0.tx, p1.tx, t), lerp(p0.ty, p1.ty, t)); if (T) { lean = T.lean; wd *= tiltWidth(b, lean); } }
      const dk = handDark(b, pr, v, lean) * (p1.own === undefined ? 1 : lerp(p0.own, p1.own, t));
      const a = Math.max(.6, wd / 2) * crayonWobble(x, y), bb = Math.max(.6, Math.min(a, Math.max(1, a * asp))), rimPx = Math.sqrt(a * bb);
      const R = Math.ceil(a * CRAYON.REACH + 1);
      if (meta && R > (meta.widest || 0)) meta.widest = R;
      const x0 = Math.max(cl, Math.floor(x - R)), x1 = Math.min(cr, Math.ceil(x + R)), y0 = Math.max(ct, Math.floor(y - R)), y1 = Math.min(cb, Math.ceil(y + R));
      if (x1 < x0 || y1 < y0) continue;
      for (let py = y0; py <= y1; py++) for (let px = x0, i = py * w + x0; px <= x1; px++, i++) {
        if (buf[i] >= 1) continue;
        const dx = px + .5 - x, dy = py + .5 - y, u = (dx * cosA + dy * sinA) / a, q0 = (-dx * sinA + dy * cosA) / bb, q = Math.sqrt(u * u + q0 * q0);
        if (q >= CRAYON.REACH) continue;
        const edge = .8 + .5 * M.rag[i];
        let V;
        // Past the edge a lone high grain catches a fleck; inside it, the pressure, less toward the edge, a pixel's antialias.
        if (q > edge) { if (!(M.tooth[i] > .62 && M.grit[i] > .9)) continue; V = .8 * pr - .22; }
        else V = clamp((edge - q) * rimPx + .5) * (pr - .22 * smoothstep(.6, 1, q / edge));
        V = clamp(V * dk);
        if (V > buf[i]) buf[i] = V;
      }
      if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
    }
  }
  return rect;
}
const stampLineCr = stampLine;
stampLine = function (surf, b, pts, size, final, from, clip, meta, win) { return b.crayon ? crayonLine(surf, b, pts, size, final, from, clip, meta, win) : stampLineCr(surf, b, pts, size, final, from, clip, meta, win); };

// ---------- the compose: wax on the tooth, building on what's there; Scratch scrapes to white ----------
// The crayon's darkness at tooth t, line V and darkness ex found there (wax laid lifts the surface by k × ex).
function crayonDark(t, k, ex, V) {
  let a = clamp((t + k * ex - 1.08 + V) * 3.5);
  if (V > .75) a = Math.max(a, (V - .75) * 2.4 * clamp(t * 5));
  return 1 - Math.pow(1 - .4 * a, 8);
}
function crayonCompose(st, rect) {
  const R = st.R, C = R.crayon, s = st.surf, w = s.w, P = PAPER_TONE, K = R.inkK;
  const x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(s.w - 1, rect[2]), y1 = Math.min(s.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const M = crayonMaps(w, s.h), L = s.L, page = s.page, base = s.base, fine = s.fine, mid = s.mid, ph = .3 * C.tooth, kx = .35 * C.wax / CRAYON.WAX;
  const kept = s.kept && st.e < 1 ? s.kept : null, kb = kept ? s.keptBase : null, ke = 1 - st.e, dots = s.dots;
  // The shield as the stroke found it: masking fluid's share (what can lift) shields a crayon; wax takes wax.
  const rp = s.resist, rk = s.resistKept, rb = rp ? s.resistBase : null, rkb = rp ? s.resistKeptBase : null;
  const lays = !C.scratch && C.wax > 0 && rp, wk = 1.6 * C.wax / CRAYON.WAX;
  for (let y = y0; y <= y1; y++) for (let i = y * w + x0, e = y * w + x1; i <= e; i++) {
    const V = L[i], u = base[i];
    if (!(V > 0)) { page[i] = u; if (kept) kept[i] = kb[i]; if (lays) { rp[i] = rb[i]; rk[i] = rkb[i]; } continue; }
    const f = rb ? 1 - Math.max(0, rb[i] - rkb[i]) : 1;
    if (C.scratch) {
      const a = clamp(V * (M.grit[i] < .15 ? .5 : 1) * K) * f;
      page[i] = u + (P - u) * a;
      if (kept) kept[i] = kb[i] + (P - kb[i]) * a * ke;
    } else {
      const t = lerp(M.tooth[i], fine[i] * .65 + mid[i] * .35, ph);
      page[i] = u * (1 - crayonDark(t, kx, 1 - u / P, V) * K * f);
      if (kept) kept[i] = kb[i] * (1 - crayonDark(t, kx, 1 - kb[i] / P, V) * K * f * ke);
      // Its wax into the resist planes, all of it kept: washes slide off it, the eraser doesn't lift it, it isn't hatched.
      if (lays) { const m = clamp(wk * crayonDark(t, kx, 0, V) * K); rp[i] = Math.max(rb[i], m); rk[i] = Math.max(rkb[i], m); }
    }
    // Over Tone's dots: they are paint now (as any mark, the Screen compose's).
    if (dots && page[i] !== u) dots[i] = 0;
  }
}
const composeCr = composeStroke;
composeStroke = function (st, rect, phase) { return st.R.crayon ? crayonCompose(st, rect) : composeCr(st, rect, phase); };
const StrokeCr = Stroke;
Stroke = class extends StrokeCr {
  constructor(surf, B, size, shade, host) {
    super(surf, B, size, shade, host);
    // The planes as the stroke found them: the shield, and what its wax adds to.
    const C = this.R.crayon;
    if (C && (surf.resist || (!C.scratch && C.wax > 0))) resistBegin(surf);
  }
};

// ---------- words: "Wax crayon. Builds up, resists washes." ----------
const crayonNoun = B => crayonScratch(B) ? "Crayon scratch" : "Wax crayon";
// The brush as the old words know it: a line of ink that won't erase (as Resist's), the crayon's own said over it; in
// "This brush" and the caption without its smudge, which it never lays (the app's ModularWords). A card keeps its own.
function crayonPlain(B, card) { const A = cloneBrush(B); A.medium = freshPart("medium", "ink"); A.medium.d.erase = 0; if (!card) A.carry = freshPart("carry", "off"); return normBrush(A); }
const choiceWordsCr = choiceWords;
choiceWords = function (B, part) {
  if (B.medium.k !== "crayon") return choiceWordsCr(B, part);
  return part === "medium" ? (crayonScratch(B) ? "Scratch" : "Crayon") : choiceWordsCr(crayonPlain(B, true), part);
};
const captionItemsCr = captionItems;
captionItems = function (B) {
  if (B.medium.k !== "crayon") return captionItemsCr(B);
  // Only the items an ink and a wash would both give (no medium tag), its own in the medium's place.
  const A = crayonPlain(B), W = cloneBrush(A); W.medium = freshPart("medium", "wash");
  const other = captionItemsCr(normBrush(W)), items = captionItemsCr(A).filter(t => other.includes(t));
  items.splice(1, 0, crayonScratch(B) ? "scratch" : "crayon");
  return items;
};
const plainWordsCr = plainWords;
plainWords = function (B) {
  if (B.medium.k !== "crayon") return plainWordsCr(B);
  const sent = plainWordsCr(crayonPlain(B)).split(/(?<=\.)\s+/).filter(Boolean), first = sent.shift() || "";
  // The first sentence names it for itself, whatever its tip, keeping its tail (" on cold press").
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(first), tail = m && m[2] ? m[2] : "";
  const out = [crayonNoun(B) + tail + ".", crayonScratch(B) ? "Scrapes back to white." : (B.medium.d.wax ?? CRAYON.WAX) >= .05 ? "Builds up, resists washes." : "Builds up."];
  let t = out.join(" ");
  for (const x of sent) { if (t.length + 1 + x.length > 260) break; t += " " + x; }
  return t;
};

// ---------- checks ----------
function crayonChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 640, H = 240, q = quantTone, P = PAPER_TONE;
  const mk = (md, medium) => { const B = { name: "wax", seed: 29, style: "Pastels", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium || "crayon"), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "wet"), hand: freshPart("hand", "steady") }; Object.assign(B.medium.d, md || {}); return normBrush(B); };
  const line = (x0, x1, y, p, n = 120) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y, p, t: k * 6, tx: NaN, ty: NaN }));
  const draw = (B, ys, p, s) => { s = s || modSurface(W, H, 7); for (const y of ys) layStroke(s, B, line(40, 600, y, p), 30, 1, null); return s; };
  const count = (s, f, y0 = 0, y1 = H) => { let n = 0; for (let y = y0; y < y1; y++) for (let x = 60; x < 580; x++) if (f(q(s.page[y * W + x]), y * W + x)) n++; return n; };
  const darkSum = (s, y0, y1) => { let a = 0; for (let y = y0; y < y1; y++) for (let x = 60; x < 580; x++) a += P - s.page[y * W + x]; return a; };
  const hashPage = s => { let hh = 0x811c9dc5; for (let i = 0; i < s.page.length; i++) hh = Math.imul(hh ^ Math.round(s.page[i] * 64), 16777619); return hh >>> 0; };
  const seg = c => c.split("_")[1];
  // Codes: Medium choice 7, Wax two digits, Scratch one; they read back; every other code as before.
  { const c = encodeM(mk()), c2 = encodeM(mk({ wax: .25, scratch: 1 })), back = readAnyCode(c2).b;
    const olds = [...LEGACY_CODES.map(x => encodeM(readAnyCode(x).b)), OIL_PAINT, "f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_0000000000000000000nz0.7"];
    const tone = encodeM(mk({}, "tone")), resist = encodeM(mk({ erase: 0 }, "resist"));
    t("a Crayon's code is _" + seg(c) + "_ (Scratch On, Wax .25: _" + seg(c2) + "_), Medium choice " + CHOICES.medium.findIndex(ch => ch.k === "crayon") + "; it reads back; older codes as before",
      seg(c) === "7" + two36(Math.round(.6 * M_STEPS)) + "0" && seg(c2).length === 4 && seg(c2).endsWith("1") && encodeM(readAnyCode(c).b) === c && encodeM(back) === c2 && back.medium.k === "crayon"
      && Math.abs(back.medium.d.wax - .25) < 1e-3 && back.medium.d.scratch === 1 && CHOICES.medium.findIndex(ch => ch.k === "crayon") === 7
      && olds.every(x => encodeM(readAnyCode(x).b) === x) && readAnyCode(tone).b.medium.k === "tone" && readAnyCode(resist).b.medium.k === "resist" && encodeM(readAnyCode(resist).b) === resist); }
  // Light pressure catches the peaks only; hard goes near solid in the middle, the edge still broken.
  { const light = draw(mk(), [120], .25), hard = draw(mk(), [120], 1), lit = count(light, v => v < 238), hd = count(hard, v => v < 238);
    // The middle: rows within 4 px of the line; its outer band: 11 to 15 px off it, both sides.
    const mid = count(hard, v => v <= 51, 116, 125) / (9 * 520), outer = f => count(hard, f, 105, 109) + count(hard, f, 132, 136);
    const paperOut = outer(v => v >= 238), inkOut = outer(v => v < 238);
    t("light pressure inks " + lit + " px, hard " + hd + "; hard is " + Math.round(mid * 100) + "% near black in the middle, its edge broken (" + paperOut + " paper, " + inkOut + " inked px in its outer band)",
      hd > 3 * lit && mid > .85 && paperOut > 400 && inkOut > 400); }
  // Layers build up: a second stroke over the first darkens it (wax takes wax); masking fluid still shields a crayon.
  { const once = draw(mk(), [120], .55), twice = draw(mk(), [120, 120], .55), a = darkSum(once, 100, 140), b = darkSum(twice, 100, 140);
    const s = modSurface(W, H, 7); layStroke(s, mk({ erase: 1 }, "resist"), line(40, 600, 120, .8), 30, 1, null); const before = Float32Array.from(s.page), held = s.resist.map((r, i) => r > .999 && s.resistKept[i] < .001 ? 1 : 0); draw(mk(), [120], 1, s);
    let fluid = 0, moved = 0; for (let i = 0; i < W * H; i++) if (held[i]) { fluid++; if (Math.abs(s.page[i] - before[i]) > .5) moved++; }
    t("a second stroke darkens the first (" + Math.round(a / P) + " → " + Math.round(b / P) + "); masking fluid shields a crayon (" + moved + " of " + fluid + " px changed)", b > 1.15 * a && fluid > 1000 && moved === 0); }
  // A wash slides off the wax: it lays far less on the crayon than beside it.
  { const s = draw(mk(), [80], 1), before = Float32Array.from(s.page);
    layStroke(s, mk({ wet: .3 }, "wash"), line(10, 630, 120, .8), 300, 1, null);
    let on = 0, nOn = 0, off = 0, nOff = 0;
    for (let y = 60; y < 200; y++) for (let x = 100; x < 540; x++) { const i = y * W + x, k = 1 - s.page[i] / Math.max(1, before[i]); if (before[i] < 60) { on += k; nOn++; } else if (y > 140 && before[i] > P - .5) { off += k; nOff++; } }
    on /= Math.max(1, nOn); off /= Math.max(1, nOff);
    t("a wash lays " + Math.round(on * 100) + "% on the crayon, " + Math.round(off * 100) + "% beside it; the wax isn't hatched", nOn > 1000 && off > .2 && on < .25 * off && !s.resistKept.some((k, i) => k > .5 && hatchAt(s, i % W, (i / W) | 0, i) >= 0)); }
  // Scratch through a black crayon patch leaves near white.
  { const s = draw(mk(), [70, 85, 100, 115, 130, 145, 160, 175], 1); draw(mk(), [70, 85, 100, 115, 130, 145, 160, 175], 1, s);
    const black = count(s, v => v <= 34, 100, 140) / (40 * 520);
    layStroke(s, mk({ scratch: 1 }), line(40, 600, 120, .9), 30, 1, null);
    const white = count(s, v => v >= 204, 118, 123);
    t("Scratch through a black patch (" + Math.round(black * 100) + "% black) leaves " + white + " near-white px", black > .85 && white > 1200); }
  // The same stroke drawn twice on fresh pages matches exactly; batched differently, the same.
  { const one = hashPage(draw(mk(), [100, 140], .7)), two = hashPage(draw(mk(), [100, 140], .7));
    const batched = n => { const s = modSurface(W, H, 7), S = new Stroke(s, mk(), 30, 1, null), pts = line(40, 600, 120, .8); for (let i = 0; i < pts.length; i += n) S.add(pts.slice(i, i + n).map(p => [p.x, p.y, p.p, p.t, p.tx, p.ty])); S.end(); return hashPage(s); };
    t("the same strokes drawn twice match exactly; batched 1 or 7 a time, the same", one === two && batched(1) === batched(7)); }
  // Words, size, rolls and variations.
  { const B = mk(), lo = mk({ wax: .02 }), sc = mk({ scratch: 1 }), paper = mk(), smeared = mk(); paper.paper = freshPart("paper", "cold press"); smeared.carry = freshPart("carry", "smudge");
    const roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).medium.k === "crayon") return false; return true; };
    const vars = () => { const ink = mk({}, "ink"), lk = { parts: new Set(), dials: new Set() }; for (let i = 0; i < 200; i++) if (makeVars(ink, lk).some(v => v.b.medium.k === "crayon")) return false; return true; };
    t("words: \"" + plainWords(B) + "\", \"" + plainWords(lo) + "\", \"" + plainWords(sc) + "\", \"" + plainWords(paper) + "\"; " + choiceWords(B, "medium") + ", " + choiceWords(sc, "medium") + "; 30 px in every style; never rolled or swapped to",
      plainWords(B) === "Wax crayon. Builds up, resists washes." && plainWords(lo) === "Wax crayon. Builds up." && plainWords(sc) === "Crayon scratch. Scrapes back to white."
      && /^Wax crayon on [a-z -]+\. Builds up/.test(plainWords(paper)) && !/eras/i.test(plainWords(B) + plainWords(sc)) && choiceWords(B, "medium") === "Crayon" && choiceWords(sc, "medium") === "Scratch"
      && captionItems(B)[1] === "crayon" && captionItems(sc)[1] === "scratch" && plainWords(smeared) === plainWords(B) && captionItems(smeared).join() === captionItems(B).join() && STYLE_NAMES.every(n => styleSize(n, B) === 30) && roll() && vars()); }
  return out;
}
const selfTestCr = selfTest;
selfTest = function () { const r = selfTestCr(); r.crayon = crayonChecks(); r.ok = r.ok && r.crayon.every(c => c.ok); NM.lastCrayon = r.crayon; return r; };
if (typeof runChecks === "function") {
  const runChecksCr = runChecks;
  runChecks = function () {
    runChecksCr();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastCrayon) return;
      out.textContent += "\n\nCrayon:\n" + NM.lastCrayon.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 140);
  };
}

// ===================================================================================================================
// Spiro (mischief-lab-2.html's third pad; the owner: "more like the real toy", loops round the stroke and a guilloche
// within it, its variables in Control; the app's SpiroStamper): Tip choice 11, a fine line from two phases, th (the
// wheel's trip round the ring) and ps (its spin, q times as fast, summed step by step, so a setting changed mid-stroke
// bends the line). Curves: the toy's (a wheel inside a 96- or 105-tooth ring, the pen in a hole), Epi (the wheel
// outside), Rose, Lissajous, Harmonograph (dying away over Fade Turns), Coil (its far side lighter). Line: the curve
// rides the stroke, Loops per cm. Ring: the stroke's first point pins the ring; a second with no pressure PIN or more out
// is its rim (the app's ring setup: pen down pins the centre, the drag out sizes it), else the ring is the brush's own
// size; the pen's eased angle round it turns the wheel, only forward past a tremble; at the lift it catches up, and a tap
// draws the whole figure. Oval and Bar: the toy's oval and bar tracks, set up corner to corner, the wheel rolling round
// inside (spiroTrack), the other curves stretched to the box. Control's
// Pressure Sets, Speed Sets and Tilt Sets (the hand's last dials, after the app's Tilt Range and page tilt, kept here so
// codes line up) scale Size, Loops, Wheel, Hole or Petals by .4 to 1.3. Never rolled.
// ===================================================================================================================
const SPIRO = { SIZE: 132, PROTO: 3, PX_CM: 300 / 2.54, WHEELS: [24, 30, 32, 36, 40, 42, 45, 48, 50, 52, 56, 60, 63, 64, 72, 75, 80, 84], TEETH: [96, 105],
  RATIOS: [[1, 2], [2, 3], [3, 4], [3, 5]], RHO: .2, SIDE: Math.sin(50 * Math.PI / 180), RING: 80 / 22, PIN: 18, REV: 18, OVAL: 2, BAR: 3, ASPECT: .6, EASE: 25, EASE_HAND: 80,
  UNTIMED: 8, SPEED_FULL: 3.6, NEAR: .92, FAR: .47, STEP: 1.5, LINE_R: 2.4 };
// Curves and what a reading drives, in their options' order.
const SP_C = { spiro: 0, epi: 1, rose: 2, liss: 3, harmo: 4, coil: 5 }, SP_D = { size: 0, loops: 1, wheel: 2, hole: 3, petals: 4, none: 5 };
const DRIVE_OPTS = ["Size", "Loops", "Wheel", "Hole", "Petals", "Nothing"];
Object.assign(DIALS.tip, {
  mode: { label: "Mode", choices: ["Line", "Ring", "Oval", "Bar"], def: 0 },
  curve: { label: "Curve", choices: ["Spirograph", "Epi", "Rose", "Lissajous", "Harmonograph", "Coil"], def: 0 },
  loops: { label: "Loops per cm", min: .3, max: 4, def: 2, fmt: v => v.toFixed(1) },
  teeth: { label: "Ring Teeth", choices: ["96", "105"], def: 0 },
  wheel: { label: "Wheel", min: 0, max: 17, def: 4, int: true, fmt: v => String(SPIRO.WHEELS[clamp(Math.round(v), 0, 17)]) },
  hole: { label: "Hole", min: .2, max: .9, def: .8, fmt: v => Math.round(v * 100) + "%" },
  petals: { label: "Petals", min: 3, max: 12, def: 5, int: true, fmt: v => String(Math.round(v)) },
  ratio: { label: "Ratio", choices: ["1:2", "2:3", "3:4", "3:5"], def: 1 },
  phase: { label: "Phase", min: 0, max: 1, def: .5, fmt: v => Math.round(v * 90) + "°" },
  fades: { label: "Fade Turns", min: 3, max: 40, def: 12, int: true, fmt: v => String(Math.round(v)) }
});
CHOICES.tip.push({ k: "spiro", label: "Spiro", dials: ["weight", "mode", "curve", "loops", "teeth", "wheel", "hole", "petals", "ratio", "phase", "fades", "flick"],
  macro: "weight", words: ["small", "big"], blurb: "The toy's wheel and ring: loops along the line, or round a ring, an oval or a bar set up first." });
PATTERN_TIPS.add("spiro");
// The hand's last dials, as the app's: its Tilt Range (Wide: the Fude pen's, engine.js tiltWidth) and page tilt (which this lab
// doesn't draw: hidden, kept so codes line up), then a Spiro's drives, shown only for a Spiro (dialShown).
Object.assign(DIALS.hand, {
  trange: { label: "Tilt Range", choices: ["Normal", "Wide"], def: 0 },
  gs: { label: "Page Tilt Size", min: 0, max: 1, def: 0, hide: true }, gd: { label: "Page Tilt Dark", min: 0, max: 1, def: 0, hide: true },
  gw: { label: "Page Tilt Nib", min: 0, max: 1, def: 0, hide: true },
  pdrive: { label: "Pressure Sets", choices: DRIVE_OPTS, def: 0 }, sdrive: { label: "Speed Sets", choices: DRIVE_OPTS, def: 5 }, tdrive: { label: "Tilt Sets", choices: DRIVE_OPTS, def: 3 }
});
const DRIVES = ["pdrive", "sdrive", "tdrive"];
HAND_DIALS.push("trange", "gs", "gd", "gw", ...DRIVES);
for (const k of ["trange", "gs", "gd", "gw", ...DRIVES]) ADDED.add("hand." + k);
KEEPS.hand.push(...DRIVES);
// A Spiro's Medium is its own in every style (the app's ModularSizes.SPIRO).
const styleSizeSp = styleSize;
styleSize = function (name, B) { return B && B.tip && B.tip.k === "spiro" ? SPIRO.SIZE : styleSizeSp(name, B); };
// A variation never swaps to the Spiro, and no roll picks it.
const makeVarsSp = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.tip.slice();
  CHOICES.tip = keep.filter(c => c.k !== "spiro" || c.k === B.tip.k);
  try { return makeVarsSp(B, locks); } finally { CHOICES.tip = keep; }
};
// Whether driving `what` changes the tip's curve (the app's SpiroStamper.acts), and the dials a Spiro's curve shows (shows).
function spiroActs(T, what) {
  // Wheel drives the toy's wheel, or the swings' ratio.
  return what === SP_D.size ? true : what === SP_D.loops ? spiroShows(T, "loops") : what === SP_D.wheel ? spiroShows(T, "wheel") || spiroShows(T, "ratio")
    : what === SP_D.hole ? spiroShows(T, "hole") : what === SP_D.petals ? spiroShows(T, "petals") : false;
}
function spiroShows(T, key) {
  if (T.k !== "spiro") return true;
  const c = T.d.curve | 0, wheels = c === SP_C.spiro || c === SP_C.epi, swings = c === SP_C.liss || c === SP_C.harmo;
  switch (key) {
    case "loops": return (T.d.mode | 0) === 0 || c === SP_C.coil;
    case "teeth": case "wheel": case "hole": return wheels;
    case "petals": return c === SP_C.rose;
    case "ratio": case "phase": return swings;
    case "fades": return c === SP_C.harmo;
    default: return true;
  }
}
const dialShownSp = dialShown;
dialShown = function (B, part, key) {
  if (part === "hand" && DRIVES.includes(key)) return B.tip.k === "spiro";
  return dialShownSp(B, part, key) && (part !== "tip" || spiroShows(B.tip, key));
};

// ---------- resolve: the pattern's Spiro fields ----------
const resolveSp = resolve;
resolve = function (B, size, shade) {
  const R = resolveSp(B, size, shade);
  if (B.tip.k === "spiro" && R.pat) {
    const mk = B.medium.k, lineMedium = mk === "ink" || mk === "charcoal" || mk === "graphite" || mk === "resist" || mk === "tone";
    const T = B.tip.d, H = B.hand.d, t = k => T[k] ?? DIALS.tip[k].def, h = k => H[k] ?? DIALS.hand[k].def;
    R.pat.size = lineMedium ? R.inkSize : R.washSize;
    R.pat.spiro = { mode: t("mode") | 0, ring: (t("mode") | 0) !== 0, curve: t("curve") | 0, teeth: SPIRO.TEETH[t("teeth") | 0], wheel: clamp(Math.round(t("wheel")), 0, 17), petals: Math.round(t("petals")),
      ratio: t("ratio") | 0, perCm: t("loops"), hole: t("hole"), phase: t("phase"), fades: t("fades"), drives: DRIVES.map(k => h(k) | 0) };
  }
  return R;
};

// ---------- the stamper (the app's SpiroStamper) ----------
const spiroRadius = (S, size) => size / 2 * (S.ring ? SPIRO.RING : 1);
const spiroLineR = size => Math.max(1, SPIRO.LINE_R * Math.sqrt(size / SPIRO.SIZE));
const spGcd = (a, b) => b ? spGcd(b, a % b) : a;
// The curve's shape with what the pen drives now (f: Size, Loops, Wheel, Hole, Petals); A the ring's radius.
function spiroGear(S, f, ring, A) {
  const g = { c: S.curve, q: 1, a: 0, d: 0, ph: 0, lam: 0, th0: 0 };
  if (S.curve === SP_C.spiro || S.curve === SP_C.epi) {
    const R = S.teeth, r = clamp(SPIRO.WHEELS[S.wheel] * f[2], 12, S.curve === SP_C.spiro ? R - 8 : 120), d = clamp(S.hole * f[3], .05, .95) * r;
    if (S.curve === SP_C.spiro) { g.q = (R - r) / r; g.a = (R - r) / R; g.d = d / R; }
    else { const E = R + 2 * r; g.q = (R + r) / r; g.a = (R + r) / E; g.d = d / E; }
  } else if (S.curve === SP_C.rose) {
    const P = S.petals, n = Math.floor(P / 2);
    g.q = (P % 2 === 1 ? P : n % 2 === 1 ? n / 2 : n) * f[4];
  } else if (S.curve === SP_C.liss || S.curve === SP_C.harmo) {
    const [a, b] = SPIRO.RATIOS[S.ratio];
    g.q = a / b * f[2] + (S.curve === SP_C.harmo ? .008 : 0); g.ph = S.phase * Math.PI / 2; g.lam = Math.log(20) / (S.fades * 2 * Math.PI);
  } else g.q = ring ? Math.max(3, Math.round(S.perCm * f[1] * 2 * Math.PI * (1 - SPIRO.RHO) * A / SPIRO.PX_CM)) : 1;
  return g;
}
// The whole figure's turn, for a tap: until it closes (the harmonograph: until it is down to 3%).
function spiroSpan(S, g) {
  switch (S.curve) {
    case SP_C.spiro: case SP_C.epi: { const r = SPIRO.WHEELS[S.wheel]; return 2 * Math.PI * r / spGcd(g && g.tr ? g.tr.teeth : S.teeth, r); }
    case SP_C.rose: { const P = S.petals; return P % 2 === 1 ? Math.PI : Math.floor(P / 2) % 2 === 1 ? 4 * Math.PI : 2 * Math.PI; }
    case SP_C.liss: return 2 * Math.PI * SPIRO.RATIOS[S.ratio][1];
    case SP_C.harmo: return Math.log(1 / .03) / g.lam;
    default: return 2 * Math.PI;
  }
}
// The pen's hole at (th, ps) of a curve A across centred at (cx, cy), into out: x, y, tone. (nx, ny): the line's normal.
// On a track the wheel's centre keeps its radius a in from the line (the epitrochoid's out), the hole d from it; the other
// curves stretched to the track's box.
function spiroAt(g, th, ps, A, cx, cy, nx, ny, ring, out) {
  const tr = ring ? g.tr : null;
  if (!tr) return spiroCurve(g, th, ps, A, cx, cy, nx, ny, ring, out);
  if (spiroRolls(g)) {
    const p = tr.at(th), side = g.c === SP_C.epi ? 1 : -1, wx = p[0] + side * g.a * Math.cos(p[3]), wy = p[1] + side * g.a * Math.sin(p[3]);
    out[0] = cx + A * (wx - side * g.d * Math.cos(ps)); out[1] = cy + A * (wy - g.d * Math.sin(ps)); out[2] = SPIRO.NEAR;
    return;
  }
  spiroCurve(g, th, ps, 1, 0, 0, nx, ny, true, out);
  out[0] = cx + A * tr.ha * out[0]; out[1] = cy + A * tr.hb * out[1];
}
function spiroCurve(g, th, ps, A, cx, cy, nx, ny, ring, out) {
  let tone = SPIRO.NEAR, x, y;
  switch (g.c) {
    case SP_C.spiro: x = cx + A * (g.a * Math.cos(th) + g.d * Math.cos(ps)); y = cy + A * (g.a * Math.sin(th) - g.d * Math.sin(ps)); break;
    case SP_C.epi: x = cx + A * (g.a * Math.cos(th) - g.d * Math.cos(ps)); y = cy + A * (g.a * Math.sin(th) - g.d * Math.sin(ps)); break;
    case SP_C.rose: { const r = Math.cos(ps); x = cx + A * r * Math.cos(th); y = cy + A * r * Math.sin(th); break; }
    case SP_C.liss: x = cx + A * Math.sin(ps + g.ph); y = cy + A * Math.sin(th); break;
    case SP_C.harmo: { const e = A * Math.exp(-g.lam * Math.max(0, th - g.th0)); x = cx + e * Math.sin(ps + g.ph); y = cy + e * Math.sin(th); break; }
    default: {
      let rho = A, tx = ny, ty = -nx;
      if (ring) { rho = A * SPIRO.RHO; nx = Math.cos(th); ny = Math.sin(th); tx = -ny; ty = nx; cx += (A - rho) * nx; cy += (A - rho) * ny; }
      const c = Math.cos(ps), s = Math.sin(ps);
      x = cx + rho * (nx * c + tx * s * SPIRO.SIDE); y = cy + rho * (ny * c + ty * s * SPIRO.SIDE);
      if (s < 0) tone = SPIRO.FAR;
    }
  }
  out[0] = x; out[1] = y; out[2] = tone;
}
// An antialiased line r px each side, round ends, of darkness tone, each pixel keeping its strongest.
function spiroSeg(s, buf, x0, y0, x1, y1, r, tone, d, rect) {
  if (!(tone > 0)) return;
  const w = s.w, R = r + 1, bx0 = Math.max(0, Math.floor(Math.min(x0, x1) - R)), bx1 = Math.min(w - 1, Math.ceil(Math.max(x0, x1) + R));
  const by0 = Math.max(0, Math.floor(Math.min(y0, y1) - R)), by1 = Math.min(s.h - 1, Math.ceil(Math.max(y0, y1) + R));
  if (bx1 < bx0 || by1 < by0) return;
  const ex = x1 - x0, ey = y1 - y0, len2 = ex * ex + ey * ey;
  for (let py = by0; py <= by1; py++) {
    const qy = py + .5 - y0;
    for (let px = bx0; px <= bx1; px++) {
      const qx = px + .5 - x0, t = len2 > 0 ? clamp((qx * ex + qy * ey) / len2) : 0, dx = qx - t * ex, dy = qy - t * ey;
      const c = clamp(r + .5 - Math.sqrt(dx * dx + dy * dy)) * tone, i = py * w + px;
      if (c > buf[i]) { buf[i] = c; if (WET) WET[i] = d; }
    }
  }
  grow(rect, [bx0, by0, bx1, by1]);
}
// The readings at sample q eased into what the pen drives, dt ms since the last (NaN: at once). A tilt the pen doesn't give leaves its setting as set.
function spiroDrive(M, S, q, dt) {
  const want = [1, 1, 1, 1, 1], tl = tiltOf(q.tx, q.ty);
  const scale = (what, v) => { if (what >= 0 && what < 5) want[what] *= .4 + .9 * v; };
  scale(S.drives[0], q.p); scale(S.drives[1], clamp((q.v || 0) / SPIRO.SPEED_FULL)); if (tl) scale(S.drives[2], tl.lean);
  const e = Number.isNaN(dt) ? 1 : 1 - Math.exp(-dt / SPIRO.EASE_HAND);
  for (let i = 0; i < 5; i++) M.f[i] += (want[i] - M.f[i]) * e;
}
function spiroSince(M, q) {
  if (!Number.isFinite(q.t) || !Number.isFinite(M.t)) { M.t = q.t; return SPIRO.UNTIMED; }
  const dt = Math.max(1, q.t - M.t); M.t = q.t; return dt;
}
// The toy's ring as a share of the figure's radius: 1, but the epitrochoid's, inside its wheel (the app's ringShare).
const spiroShare = (S, f) => S.curve === SP_C.epi && S.mode < SPIRO.OVAL ? 2 * spiroGear(S, f, true, 1).a - 1 : 1;
// An Oval's or Bar's track (the app's SpiroStamper.Track), its longer half 1: an ellipse, or a rectangle whose corners are
// rounded to a quarter of its shorter side. Its wheels are sized to its tightest bend, so they roll round it, corners too.
// Sampled along its line from its right end: the pen's angle round its centre finds the point there, how far along it is
// and which way it faces.
const spUnwrap = (a, near) => a + 2 * Math.PI * Math.round((near - a) / (2 * Math.PI));
const spCorner = (ha, hb) => Math.min(ha, hb) / 2;
const spBarLength = (ha, hb) => { const c = spCorner(ha, hb); return 4 * (ha + hb - 2 * c) + 2 * Math.PI * c; };
// The point u of the way round a track of halves ha, hb from its right end, and its outward normal's angle, into out.
function spOutline(bar, ha, hb, u, out) {
  if (!bar) { const t = 2 * Math.PI * u; out[0] = ha * Math.cos(t); out[1] = hb * Math.sin(t); out[2] = Math.atan2(ha * Math.sin(t), hb * Math.cos(t)); return; }
  const c = spCorner(ha, hb), sx = ha - c, sy = hb - c;
  let s = u * spBarLength(ha, hb) + sy;
  // Side q (0 right, 1 bottom, 2 left, 3 top, then the right again), from its start, then the corner after it.
  for (let k = 0; ; k++) {
    const q = k & 3, nx = q === 0 ? 1 : q === 2 ? -1 : 0, ny = q === 1 ? 1 : q === 3 ? -1 : 0, half = q % 2 === 0 ? sy : sx, far = q % 2 === 0 ? ha : hb;
    if (s <= 2 * half || k === 4) { const o = Math.min(s, 2 * half) - half; out[0] = nx * far - ny * o; out[1] = ny * far + nx * o; out[2] = k * Math.PI / 2; return; }
    s -= 2 * half;
    if (s <= c * Math.PI / 2) { const a = k * Math.PI / 2 + s / c, along = q % 2 === 0 ? sx : sy; out[0] = nx * along - ny * half + c * Math.cos(a); out[1] = ny * along + nx * half + c * Math.sin(a); out[2] = a; return; }
    s -= c * Math.PI / 2;
  }
}
function spiroTrack(bar, ha, hb, ringTeeth) {
  const N = 2048, TAU = 2 * Math.PI, x = new Float64Array(N + 1), y = new Float64Array(N + 1), s = new Float64Array(N + 1), phi = new Float64Array(N + 1), n = new Float64Array(N + 1), o = [0, 0, 0];
  for (let i = 0; i <= N; i++) {
    spOutline(bar, ha, hb, i / N, o);
    x[i] = o[0]; y[i] = o[1];
    n[i] = i === 0 ? o[2] : spUnwrap(o[2], n[i - 1]);
    phi[i] = i === 0 ? 0 : spUnwrap(Math.atan2(o[1], o[0]), phi[i - 1]);
    s[i] = i === 0 ? 0 : s[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
  }
  phi[N] = n[N] = TAU;
  const length = s[N], bend = bar ? spCorner(ha, hb) : Math.min(ha, hb) * Math.min(ha, hb) / Math.max(ha, hb), p = [0, 0, 0, 0];
  // Its teeth: the ring's pitch round it, rounded up to a whole 24, as a kit's are.
  const teeth = 24 * Math.ceil(length * ringTeeth / (TAU * bend * 24) - 1e-9);
  // The line at the pen's angle th round the centre, any turn: x, y, how far along (laps counted), its normal's angle.
  const at = th => {
    const lap = Math.floor(th / TAU), f = th - lap * TAU;
    let lo = 0, hi = N;
    while (hi - lo > 1) { const m = (lo + hi) >>> 1; if (phi[m] <= f) lo = m; else hi = m; }
    const u = clamp((f - phi[lo]) / (phi[hi] - phi[lo]));
    p[0] = lerp(x[lo], x[hi], u); p[1] = lerp(y[lo], y[hi], u); p[2] = lap * length + lerp(s[lo], s[hi], u); p[3] = lap * TAU + lerp(n[lo], n[hi], u);
    return p;
  };
  return { ha, hb, length, teeth, at };
}
// The ring's radius a rim at (x, y) sets round the pin (cx, cy): 0 within PIN (the brush's own size).
const spiroRim = (cx, cy, x, y) => { const d = Math.hypot(x - cx, y - cy); return d >= SPIRO.PIN ? d : 0; };
// A track's centre and halves [cx, cy, ha, hb] set up from the pin to the pen at (x, y): a ring round the pin out to the
// pen, an Oval or Bar corner to corner (each half at least PIN); within the pin the brush's own radius, an Oval or Bar ASPECT as tall.
function spiroBox(mode, px, py, x, y, own) {
  const rim = spiroRim(px, py, x, y);
  if (mode < SPIRO.OVAL) { const r = rim > 0 ? rim : own; return [px, py, r, r]; }
  if (rim <= 0) return [px, py, own, own * SPIRO.ASPECT];
  return [(px + x) / 2, (py + y) / 2, Math.max(SPIRO.PIN, Math.abs(x - px) / 2), Math.max(SPIRO.PIN, Math.abs(y - py) / 2)];
}
// The curve on a track: the toy's wheel rolls round inside it (the epitrochoid's outside), its teeth's share of the track's.
function spiroOnTrack(g, S, f, tr) {
  g.tr = tr;
  if (S.curve !== SP_C.spiro && S.curve !== SP_C.epi) return;
  const w = clamp(SPIRO.WHEELS[S.wheel] * f[2], 12, S.curve === SP_C.spiro ? S.teeth - 8 : 120);
  g.a = tr.length * w / (2 * Math.PI * tr.teeth); g.d = clamp(S.hole * f[3], .05, .95) * g.a; g.q = tr.teeth / w + (S.curve === SP_C.epi ? 1 : -1);
}
const spiroRolls = g => !!g.tr && (g.c === SP_C.spiro || g.c === SP_C.epi);
// How far the wheel's spin turns as the pen goes on step from th0: q times as far; on a track as far as it rolls along it,
// less its line's own turning (the epitrochoid's, more).
function spiroSpin(g, th0, step) {
  if (!spiroRolls(g)) return g.q * step;
  let p = g.tr.at(th0); const s0 = p[2], n0 = p[3];
  p = g.tr.at(th0 + step);
  return (p[2] - s0) / g.a + (g.c === SP_C.epi ? 1 : -1) * (p[3] - n0);
}
function spiroRingGear(M, S) { M.A = M.base * M.f[0]; M.g = spiroGear(S, M.f, true, M.A); M.g.th0 = M.th0; if (M.tr) spiroOnTrack(M.g, S, M.f, M.tr); }
// The ring as its rim set it, at what the pen drives now; else the brush's own.
function spiroRingSet(M, S) { if (M.rim > 0) M.base = M.rim / (M.f[0] * spiroShare(S, M.f)); spiroRingGear(M, S); }
function spiroPlace(M) { const p = [0, 0, 0]; spiroAt(M.g, M.th, M.ps, M.A, M.cx, M.cy, 0, 0, true, p); M.hx = p[0]; M.hy = p[1]; M.htone = p[2]; M.was = M.g; M.wasA = M.A; M.fresh = true; }
function spiroStart(M, a) { M.on = true; M.th = a; M.th0 = a; M.g.th0 = a; M.ps = M.g.q * a; M.lastA = a; spiroPlace(M); }
// Turns the wheel on to th1 in steps of at most STEP of the pen's hole, a setting changed since the last point inked blended in.
function spiroTurn(s, buf, P, M, th1, q, r, rect) {
  const g = M.g, was = M.was, A = Math.max(M.wasA, M.A);
  let reach = Math.abs(th1 - M.th) * (A * (1 + Math.abs(g.q)) + 1);
  if (spiroRolls(g)) {
    // On a track: as far as the line goes and turns, the wheel's centre and its hole with it.
    let p = g.tr.at(M.th); const s0 = p[2], n0 = p[3];
    p = g.tr.at(th1);
    const a = Math.max(g.a, was.a), dd = Math.max(g.d, was.d);
    reach = A * (Math.abs(p[2] - s0) * (1 + dd / a) + (a + dd) * Math.abs(p[3] - n0)) + Math.abs(th1 - M.th);
  }
  const n = Math.ceil(reach / SPIRO.STEP);
  if (!(n > 0)) return;
  const dk = handDark(P.dark, q.p, q.v, 0) * (q.own === undefined ? 1 : q.own), d = q.d;
  if (M.fresh) { spiroSeg(s, buf, M.hx, M.hy, M.hx, M.hy, r, M.htone * dk, d, rect); M.fresh = false; }
  const step = (th1 - M.th) / n, p0 = [0, 0, 0], p1 = [0, 0, 0];
  for (let i = 1; i <= n; i++) {
    M.ps += spiroSpin(g, M.th, step); M.th += step;
    const u = i / n;
    spiroAt(was, M.th, M.ps, M.wasA, M.cx, M.cy, 0, 0, true, p0); spiroAt(g, M.th, M.ps, M.A, M.cx, M.cy, 0, 0, true, p1);
    const x = lerp(p0[0], p1[0], u), y = lerp(p0[1], p1[1], u);
    spiroSeg(s, buf, M.hx, M.hy, x, y, r, p1[2] * dk, d, rect);
    M.hx = x; M.hy = y; M.htone = p1[2];
  }
  M.was = g; M.wasA = M.A;
}
function spiroRing(s, buf, P, S, pts, M, k, r, rect) {
  const q = pts[k];
  // The setup's rim: no pressure, out from the pin (an Oval's or Bar's far corner, the pin its first). Nothing inked.
  if (k === 1 && q.p === 0 && (M.rim = spiroRim(M.cx, M.cy, q.x, q.y)) > 0) {
    if (M.tr) { const b = spiroBox(S.mode, M.cx, M.cy, q.x, q.y, 0); M.cx = b[0]; M.cy = b[1]; M.rim = Math.max(b[2], b[3]); M.tr = spiroTrack(S.mode === SPIRO.BAR, b[2] / M.rim, b[3] / M.rim, S.teeth); }
    return;
  }
  const dx = q.x - M.cx, dy = q.y - M.cy, dist = Math.hypot(dx, dy);
  M.far = Math.max(M.far, dist);
  if (dist < SPIRO.PIN) return;
  const ang = Math.atan2(dy, dx), dt = spiroSince(M, q);
  spiroDrive(M, S, q, dt);
  // The wheel starts where the pen first is round the pin.
  if (!M.on) { spiroRingSet(M, S); spiroStart(M, ang); M.raw = M.sm = ang; return; }
  spiroRingGear(M, S);
  let da = ang - M.lastA;
  while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
  M.lastA = ang; M.raw += da; M.sm += (M.raw - M.sm) * (1 - Math.exp(-dt / SPIRO.EASE));
  const lead = M.sm - M.th;
  if (!M.dir) { spiroPlace(M); if (Math.abs(lead) * dist < 1) return; M.dir = lead > 0 ? 1 : -1; }
  if (lead * M.dir < 0) { if (-lead * M.dir * dist < SPIRO.REV) return; M.dir = -M.dir; }
  spiroTurn(s, buf, P, M, M.sm, q, r, rect);
}
function spiroLine(s, buf, P, S, pts, M, k, r, rect) {
  const a = pts[k - 1], b = pts[k], seg = b.d - a.d, dt = spiroSince(M, b);
  spiroDrive(M, S, b, dt);
  const g = spiroGear(S, M.f, false, 0), A1 = M.base * M.f[0], rate = 2 * Math.PI * S.perCm * M.f[1] / SPIRO.PX_CM;
  let ux = b.ux, uy = b.uy;
  if (ux === undefined || Number.isNaN(ux)) { ux = seg > 0 ? (b.x - a.x) / seg : 1; uy = seg > 0 ? (b.y - a.y) / seg : 0; }
  const nx1 = -uy, ny1 = ux, p0 = [0, 0, 0], p1 = [0, 0, 0], fw = dd => pts.flick ? flickWidth(pts.flick, dd) : 1;
  if (!M.started) {
    M.started = true; M.nx = nx1; M.ny = ny1; M.was = g; M.wasA = A1;
    spiroAt(g, M.th, M.ps, A1 * fw(a.d), a.x, a.y, nx1, ny1, false, p0); M.hx = p0[0]; M.hy = p0[1]; M.htone = p0[2]; M.fresh = true;
  }
  const dth = rate * seg, A = Math.max(A1, M.wasA), n = Math.ceil((dth * A * (1 + Math.abs(g.q)) + seg) / SPIRO.STEP);
  const dk0 = handDark(P.dark, a.p, a.v, 0), dk1 = handDark(P.dark, b.p, b.v, 0), own = t => a.own === undefined ? 1 : lerp(a.own, b.own, t);
  if (M.fresh) { spiroSeg(s, buf, M.hx, M.hy, M.hx, M.hy, r, M.htone * dk0 * own(0), a.d, rect); M.fresh = false; }
  for (let i = 1; i <= n; i++) {
    const u = i / n;
    M.th += dth / n; M.ps += g.q * dth / n;
    const cx = lerp(a.x, b.x, u), cy = lerp(a.y, b.y, u), d = lerp(a.d, b.d, u);
    let nx = lerp(M.nx, nx1, u), ny = lerp(M.ny, ny1, u); const nl = Math.hypot(nx, ny);
    if (nl > 0) { nx /= nl; ny /= nl; } else { nx = nx1; ny = ny1; }
    spiroAt(M.was, M.th, M.ps, M.wasA * fw(d), cx, cy, nx, ny, false, p0); spiroAt(g, M.th, M.ps, A1 * fw(d), cx, cy, nx, ny, false, p1);
    const x = lerp(p0[0], p1[0], u), y = lerp(p0[1], p1[1], u);
    spiroSeg(s, buf, M.hx, M.hy, x, y, r, p1[2] * lerp(dk0, dk1, u) * own(u), d, rect);
    M.hx = x; M.hy = y; M.htone = p1[2];
  }
  M.nx = nx1; M.ny = ny1; M.was = g; M.wasA = A1;
}
// The curve from the samples not yet read; lifting: the ring catches up, or, never left, draws its whole figure.
function stampSpiro(st, buf, M, lifting) {
  const P = st.R.pat, S = P.spiro, s = st.surf, pts = st.pts, rect = [s.w, s.h, -1, -1];
  if (!pts.length) return rect;
  const r = spiroLineR(P.size), base = spiroRadius(S, P.size);
  if (M.k === undefined) {
    Object.assign(M, { k: 1, lifted: false, f: [1, 1, 1, 1, 1], t: pts[0].t, cx: pts[0].x, cy: pts[0].y, far: 0, th: 0, ps: 0, th0: 0, on: false, dir: 0, started: false,
      base, rim: 0, tr: S.mode >= SPIRO.OVAL ? spiroTrack(S.mode === SPIRO.BAR, 1, SPIRO.ASPECT, S.teeth) : null });
    spiroDrive(M, S, pts[0], NaN); spiroRingGear(M, S);
  }
  for (; M.k < pts.length; M.k++) (S.ring ? spiroRing : spiroLine)(s, buf, P, S, pts, M, M.k, r, rect);
  if (lifting && !M.lifted && S.ring) {
    M.lifted = true;
    const q = pts[pts.length - 1];
    if (M.on && M.dir && (M.raw - M.th) * M.dir > 0) spiroTurn(s, buf, P, M, M.raw, q, r, rect);
    if (M.far < SPIRO.PIN) { spiroRingSet(M, S); spiroStart(M, -Math.PI / 2); spiroTurn(s, buf, P, M, M.th + spiroSpan(S, M.g), q, r, rect); }
  }
  return rect;
}
const stampPatternSp = stampPattern;
stampPattern = function (st, buf, size, from, which) {
  return st.R.pat.kind === "spiro" ? stampSpiro(st, buf, st["nm" + which] ??= {}, !!st.spiroLift) : stampPatternSp(st, buf, size, from, which);
};
// The lift: a Spiro's ring catches up with the pen, or draws its whole figure (the app's ModularStroke.lastSamples).
const StrokeSp = Stroke;
Stroke = class extends StrokeSp {
  end() {
    const P = this.R.pat;
    if (P && P.kind === "spiro" && !this.fl && this.pts.length) {
      if (this.pts.length === 1) { const p = this.pts[0]; this.add([[p.x + .8, p.y, p.p, p.t + 16, p.tx, p.ty]]); }
      if (this.pts.length >= 2) { this.spiroLift = true; this.restamp(); }
    }
    return super.end();
  }
};

// The lift: a hake whose start still waits for its direction lays it now (the app's ModularStroke.lastSamples, hakeLift).
const StrokeHk = Stroke;
Stroke = class extends StrokeHk {
  end() {
    if (!this.fl && this.pts.length >= 2) {
      const waits = m => m && m.hake && m.hake.th === null;
      const line = this.R.lineTip === "hake" && waits(this.mL), body = this.R.body && this.R.body.head === "flat" && waits(this.mW);
      if (line || body) { if (line) this.mL.hake.lift = true; if (body) this.mW.hake.lift = true; this.restamp(); }
    }
    return super.end();
  }
};

// A Ring's guide (the app's SpiroStamper.guide): the pinned ring's radius at the touch-down q's pressure and tilt, as
// the stamper starts it (the epitrochoid's ring inside its wheel); 0 along the line. The test area draws it (ui.js).
function spiroGuide(R, q) {
  const P = R.pat;
  if (!P || P.kind !== "spiro" || !P.spiro.ring) return 0;
  const M = { f: [1, 1, 1, 1, 1] };
  spiroDrive(M, P.spiro, { p: q.p, v: 0, tx: q.tx, ty: q.ty }, NaN);
  return spiroRadius(P.spiro, P.size) * M.f[0] * spiroShare(P.spiro, M.f);
}

// ---------- words: "Ink spiro. Loops along the line. Pressure sets size, tilt sets hole." ----------
const SPIRO_WORDS = { line: "Loops along the line.", ring: "Loops round a ring pulled out from the start.", oval: "Loops round an oval drawn first.", bar: "Loops round a bar drawn first." };
const spiroModeWords = T => [SPIRO_WORDS.line, SPIRO_WORDS.ring, SPIRO_WORDS.oval, SPIRO_WORDS.bar][T.d.mode | 0];
// Control for a Spiro: what pressure, speed and tilt set of its shape (the app's ModularWords.driveWords).
function driveWords(B, n = 2) {
  const on = [];
  ["pressure", "speed", "tilt"].forEach((cause, i) => { const what = B.hand.d[DRIVES[i]] ?? DIALS.hand[DRIVES[i]].def; if (spiroActs(B.tip, what | 0)) on.push([cause, DRIVE_OPTS[what | 0].toLowerCase()]); });
  if (!on.length) return "steady";
  const [c1, e1] = on[0];
  if (n < 2 || on.length < 2) return c1 + " sets " + e1;
  const [c2, e2] = on[1];
  return e1 === e2 ? c1 + " and " + c2 + " set " + e1 : c1 + " sets " + e1 + ", " + c2 + " sets " + e2;
}
const handWordsSp = handWords;
handWords = function (B, n = 2) { return B.tip.k === "spiro" ? driveWords(B, n) : handWordsSp(B, n); };
const choiceWordsSp = choiceWords;
choiceWords = function (B, part) {
  if (part === "hand" && B.tip.k === "spiro") return driveWords(B);
  if (part !== "tip" || B.tip.k !== "spiro") return choiceWordsSp(B, part);
  return DIALS.tip.curve.choices[B.tip.d.curve | 0] + ((B.tip.d.mode | 0) ? " " + DIALS.tip.mode.choices[B.tip.d.mode | 0].toLowerCase() : "");
};
const plainWordsSp = plainWords;
plainWords = function (B) {
  if (B.tip.k !== "spiro") return plainWordsSp(B);
  const sent = plainWordsSp(B).split(/(?<=\.)\s+/).filter(Boolean), first = sent.shift() || "";
  // Named by what it lays, as a stamp is: "Ink spiro.", "Wash spiro on felt paper."; then where its loops go.
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(first), tail = m && m[2] ? m[2] : "";
  const out = [NM_BASE[B.medium.k] ? NM_BASE[B.medium.k] + " spiro" + tail + "." : first, spiroModeWords(B.tip)];
  // Control's sentence is the drives' (handWords), last as ever.
  const hand = handWords(B, 2), said = cap1(hand) + ".", rest = sent.filter(x => x !== cap1(handWordsSp(B, 2)) + "." && x !== said);
  if (hand !== "steady") rest.push(said);
  let t = out.join(" ");
  for (const x of rest) { if (t.length + 1 + x.length > 260) break; t += " " + x; }
  return t;
};
const captionItemsSp = captionItems;
captionItems = function (B) {
  if (B.tip.k !== "spiro") return captionItemsSp(B);
  const items = captionItemsSp(B).filter(t => t !== "spiro");
  items.splice(1, 0, "spiro");
  return items;
};

// ---------- checks ----------
function spiroChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 900, H = 640, P = PAPER_TONE;
  const mk = (d, hand) => normBrush({ name: "rosette", seed: 17, style: "Fineliner", tip: Object.assign(freshPart("tip", "spiro"), { d: Object.assign(freshPart("tip", "spiro").d, d || {}) }),
    medium: freshPart("medium", "ink"), carry: freshPart("carry", "off"), paper: freshPart("paper", "plain"), life: freshPart("life", "none"),
    hand: Object.assign(freshPart("hand", "steady"), { d: Object.assign(freshPart("hand", "steady").d, hand || {}) }) });
  const line = (x0, x1, y, p) => { const n = Math.ceil((x1 - x0) / 3); return Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y, p: p ?? .65, t: 1000 + 2 * k })); };
  const circle = (cx, cy, R, turns) => { const o = [{ x: cx, y: cy, p: .7, t: 1000 }]; let tm = 1000; for (let i = 1; i <= 12; i++) o.push({ x: cx + R * i / 12, y: cy, p: .7, t: tm += 12 });
    for (let i = 1; i <= turns * 120; i++) { const a = i / 120 * 2 * Math.PI; o.push({ x: cx + R * Math.cos(a), y: cy + R * Math.sin(a), p: .7, t: tm += 8 }); } return o; };
  const draw = (B, pts) => { const s = modSurface(W, H, 7); layStroke(s, B, pts, SPIRO.SIZE, 1, null); return s; };
  const box = s => { let x0 = W, y0 = H, x1 = -1, y1 = -1, n = 0; for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (s.page[y * W + x] < P - 10) { n++; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); } return [x0, y0, x1, y1, n]; };
  // Codes: Tip choice 11, the drives last of the hand after the app's Tilt Range and page tilt; at their defaults, nothing.
  { const B = mk({ mode: 1, curve: 2, petals: 7 }, { sdrive: 4 }), c = encodeM(B), back = readAnyCode(c).b, hand = encodeM(mk()).split(".")[1].split("_")[5];
    t("a Spiro's code (" + c + ") is Tip choice 11 and reads back, drives and all; at their defaults the hand writes as before (" + hand + ")",
      CHOICES.tip.findIndex(ch => ch.k === "spiro") === 11 && encodeM(back) === c && back.tip.d.petals === 7 && back.hand.d.sdrive === 4 && hand.length === 22
      && HAND_DIALS.slice(11, 18).join(" ") === "trange gs gd gw pdrive sdrive tdrive"); }
  // Along the line: within its width, Loops per cm setting how often it turns; pressure sets size.
  { const s = draw(mk(), line(100, 800, 320)), b = box(s), A = SPIRO.SIZE / 2, light = box(draw(mk(), line(100, 800, 320, .15))), firm = box(draw(mk(), line(100, 800, 320, 1)));
    t("along the line within its width (rows " + b[1] + " to " + b[3] + "); pressure sets size (light " + (light[3] - light[1]) + " px across, firm " + (firm[3] - firm[1]) + ")",
      b[4] > 3000 && b[1] >= 320 - 1.3 * A - 4 && b[3] <= 320 + 1.3 * A + 4 && firm[3] - firm[1] > 1.6 * (light[3] - light[1])); }
  // Ring: the rosette round the pin, within the ring; a tap draws the whole figure.
  { const R = SPIRO.SIZE / 2 * SPIRO.RING, s = draw(mk({ mode: 1 }), circle(450, 320, 150, 5)), b = box(s), tap = box(draw(mk({ mode: 1 }), [{ x: 450, y: 320, p: .7, t: 1000 }]));
    t("a ring's rosette round the pin (" + b.slice(0, 4).join(", ") + "); a tap draws the whole figure (" + tap[4] + " px, " + (tap[2] - tap[0] + 1) + " across)",
      b[4] > 5000 && b[0] >= 450 - 1.3 * R - 4 && b[2] <= 450 + 1.3 * R + 4 && tap[4] > 8000 && tap[2] - tap[0] > 1.2 * R && Math.abs((tap[0] + tap[2]) / 2 - 450) < 8); }
  // The setup's rim sizes the ring (a second sample with no pressure, 120 px out), and sets an Oval's or Bar's box corner
  // to corner: the rose reaches the ring, the toy's wheel rolls round inside the track.
  const setup = (cx, cy, rx, ry, R, turns) => { const o = circle(cx, cy, R, turns).slice(13); return [{ x: cx, y: cy, p: .7, t: 990 }, { x: rx, y: ry, p: 0, t: 995 }, ...o]; };
  { const b = box(draw(mk({ mode: 1, curve: 2 }), setup(450, 320, 570, 320, 120, 1.5)));
    const tracks = [2, 3].map(mode => { const pts = setup(300, 200, 600, 400, 150, 3).map((q, i) => i ? Object.assign(q, { x: q.x + (i > 1 ? 150 : 0), y: q.y + (i > 1 ? 100 : 0) }) : q); return box(draw(mk({ mode }), pts)); });
    const inside = b => b[4] > 2000 && b[0] >= 300 - 3 && b[2] <= 600 + 3 && b[1] >= 200 - 3 && b[3] <= 400 + 3 && b[2] - b[0] > 200;
    t("the rim sizes the ring: a rose set to 120 px reaches " + (b[2] + 1 - 450) + " px; an Oval and a Bar set corner to corner roll inside their boxes (" + tracks.map(b => b.slice(0, 4).join(",")).join("; ") + ")",
      Math.abs(b[2] + 1 - 450 - 120) < 5 && tracks.every(inside)); }
  // Every curve draws in every mode.
  { const bad = []; for (let c = 0; c < 6; c++) for (let mode = 0; mode < 4; mode++) if (box(draw(mk({ mode, curve: c }), mode ? circle(450, 320, 140, 3) : line(100, 800, 320)))[4] < 1500) bad.push(c + "/" + mode);
    t("every curve draws in every mode" + (bad.length ? ": not " + bad.join(", ") : ""), !bad.length); }
  // Words, the dials each curve shows, never rolled.
  { const B = mk(), rose = mk({ mode: 1, curve: 2 }), roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).tip.k === "spiro") return false; return true; };
    t("words: \"" + plainWords(B) + "\", \"" + plainWords(rose) + "\"; shows the toy's dials; never rolled",
      plainWords(B).startsWith("Ink spiro. " + SPIRO_WORDS.line) && plainWords(B).endsWith("Pressure sets size, tilt sets hole.") && plainWords(rose).endsWith("Pressure sets size.")
      && captionItems(B).includes("spiro") && choiceWords(rose, "tip") === "Rose ring" && dialShown(B, "tip", "wheel") && !dialShown(B, "tip", "petals") && dialShown(rose, "tip", "petals")
      && !dialShown(rose, "tip", "loops") && dialShown(B, "hand", "pdrive") && !dialShown(normBrush(Object.assign(cloneBrush(B), { tip: freshPart("tip", "round") })), "hand", "pdrive") && roll()); }
  return out;
}
const selfTestSp = selfTest;
selfTest = function () { const r = selfTestSp(); r.spiro = spiroChecks(); r.ok = r.ok && r.spiro.every(c => c.ok); NM.lastSpiro = r.spiro; return r; };
if (typeof runChecks === "function") {
  const runChecksSp = runChecks;
  runChecks = function () {
    runChecksSp();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastSpiro) return;
      out.textContent += "\n\nSpiro:\n" + NM.lastSpiro.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 160);
  };
}

// ===================================================================================================================
// Grey Shift (mischief-lab-2.html's Shifting swatches, section 5; the owner's "unpredictable swatch selection on pen
// touch"; the app's SwatchPicker): a brush with it on draws each touch in a grey picked at the touch from a set, by its
// rule: Random (repeats allowed), From a Set (a shuffled deck, each grey once before any again, never one twice running
// across a reshuffle) or Pattern, In Order or Ping-Pong. Greys: Toolbar (the test area's shade swatches here, the
// app's four grey swatches elsewhere) or Any (the panel's 16, paper white to black). Each palette keeps its own set as
// the greys left out, a bit each: sbar, and Any's sany (its first eight, paper white out at first) and sany2 (hidden,
// as the app shows them as swatches). The hand's last dials, after a Spiro's drives; written only when on. Never rolled.
// ===================================================================================================================
const SHIFT = { OFF: 0, RANDOM: 1, DECK: 2, PATTERN: 3, PING_PONG: 1, ANY: 1, LEVELS: 16,
  // The app's grey swatches at first (ToolSwatches.GREYS): black, then InkWashShades' 60, 120 and 175.
  GREYS: [1, 1 - 60 / 255, 1 - 120 / 255, 1 - 175 / 255],
  WORDS: { random: "Greys shift each stroke at random.", deck: "Greys shift each stroke, shuffled.", order: "Greys shift each stroke, in order.", pingpong: "Greys shift each stroke, there and back." } };
Object.assign(DIALS.hand, {
  shift: { label: "Grey Shift", choices: ["Off", "Random", "From a Set", "Pattern"], def: 0 },
  sorder: { label: "Order", choices: ["In Order", "Ping-Pong"], def: 0 },
  sgreys: { label: "Greys", choices: ["Toolbar", "Any"], def: 0 },
  sbar: { label: "Toolbar Greys", min: 0, max: 15, def: 0, int: true, hide: true },
  sany: { label: "Any Greys", min: 0, max: 255, def: 1, int: true, hide: true },
  sany2: { label: "Any Greys", min: 0, max: 255, def: 0, int: true, hide: true }
});
const SHIFTS = ["shift", "sorder", "sgreys", "sbar", "sany", "sany2"];
HAND_DIALS.push(...SHIFTS);
for (const k of SHIFTS) ADDED.add("hand." + k);
KEEPS.hand.push(...SHIFTS);
const shiftH = (B, k) => B.hand.d[k] ?? DIALS.hand[k].def;
const shiftRule = B => shiftH(B, "shift") | 0;
const shiftPalette = B => shiftH(B, "sgreys") | 0;
// How many greys B's palette offers: the toolbar's (`bar`, the app's four without it), or the panel's 16.
const shiftSize = (B, bar) => shiftPalette(B) === SHIFT.ANY ? SHIFT.LEVELS : (bar || SHIFT.GREYS).length;
// The greys of B's palette left out of its set, a bit each.
const shiftOut = B => shiftPalette(B) === SHIFT.ANY ? (Math.round(shiftH(B, "sany")) & 255) | (Math.round(shiftH(B, "sany2")) & 255) << 8 : Math.round(shiftH(B, "sbar")) & 15;
// Whether grey k is in B's set; with every grey left out, all are.
function shiftIn(B, k, bar) { const all = (1 << shiftSize(B, bar)) - 1, out = shiftOut(B); return (out & all) === all || !(out >> k & 1); }
const shiftSet = (B, bar) => Array.from({ length: shiftSize(B, bar) }, (_, k) => k).filter(k => shiftIn(B, k, bar));
// Grey k taken into B's set, or out of it (B changed); false when that would empty the set.
function shiftToggle(B, k, bar) {
  const all = (1 << shiftSize(B, bar)) - 1;
  let out = shiftOut(B);
  if ((out & all) === all) out = 0;
  const next = out ^ 1 << k;
  if ((next & all) === all) return false;
  if (shiftPalette(B) === SHIFT.ANY) { B.hand.d.sany = next & 255; B.hand.d.sany2 = next >> 8 & 255; } else B.hand.d.sbar = next;
  return true;
}
// A turn: the last grey picked, Pattern's step, what is left of the deck; begun again when the rule, order or set changes.
const shiftTurn = () => ({ last: -1, step: -1, deck: [], shape: null, random: () => Math.random() });
// The grey B picks at this touch, an index into its palette, the turn moving on; -1 when it is off (the app's SwatchPicker.pick).
function shiftPick(B, st, bar) {
  const rule = shiftRule(B);
  if (rule === SHIFT.OFF) return -1;
  const order = shiftH(B, "sorder") | 0, on = shiftSet(B, bar), n = on.length, shape = [rule, order, shiftPalette(B), shiftOut(B), n].join();
  if (shape !== st.shape) { st.shape = shape; st.step = -1; st.deck = []; }
  let k;
  if (rule === SHIFT.RANDOM) k = on[(st.random() * n) | 0];
  else if (rule === SHIFT.DECK) {
    if (!st.deck.length) {
      const d = st.deck = on.slice();
      for (let i = n - 1; i > 0; i--) { const j = (st.random() * (i + 1)) | 0; [d[i], d[j]] = [d[j], d[i]]; }
      // Never the same grey twice across a reshuffle: the deck is dealt from its end.
      if (n > 1 && d[n - 1] === st.last) [d[0], d[n - 1]] = [d[n - 1], d[0]];
    }
    k = st.deck.pop();
  } else {
    const period = order === SHIFT.PING_PONG ? Math.max(1, 2 * n - 2) : n;
    st.step = (st.step + 1) % period;
    k = on[st.step < n ? st.step : period - st.step];
  }
  st.last = k;
  return k;
}
// The darkness grey k of B's palette stands for: the toolbar's swatch, or Any's level k / 15.
const shiftDark = (B, k, bar) => shiftPalette(B) === SHIFT.ANY ? k / (SHIFT.LEVELS - 1) : (bar || SHIFT.GREYS)[k];
// The lab's test area: the shade a touch of B draws in ({ shade, k }: the grey picked, -1 for none), `shade` when it doesn't shift.
NM.shiftTurn = shiftTurn();
function shiftShade(B, shade, bar) { const k = shiftPick(B, NM.shiftTurn, bar); return { shade: k < 0 ? shade : shiftDark(B, k, bar), k }; }
// The Control card's Grey Shift rows (the app's BrushLab.handRows): its rule, a Pattern's Order, its Greys and its set.
const shiftRows = B => ["shift", ...(shiftRule(B) === SHIFT.PATTERN ? ["sorder"] : []), ...(shiftRule(B) ? ["sgreys", "set"] : [])];
// The set's swatches (the app's GreySet): in the set a full square with an ink edge, else small with a grey edge; a tap toggles.
function greySetHtml(B, bar, locked) {
  const n = shiftSize(B, bar);
  return `<div class="drow" style="gap:4px">` + Array.from({ length: n }, (_, k) => {
    const g = Math.round(255 * (1 - shiftDark(B, k, bar))), on = shiftIn(B, k, bar);
    return `<button data-act="greyset" data-i="${k}" aria-pressed="${on}" aria-label="${Math.round(100 * shiftDark(B, k, bar))}% grey${on ? "" : ", left out"}" ${locked ? "disabled" : ""}
      style="flex:${n > 4 ? "1 1 0" : "0 0 44px"};min-width:0;height:30px;padding:${on ? 2 : 8}px;border:2px solid ${on ? "#000" : "#888"};background:#fff"><span style="display:block;width:100%;height:100%;background:rgb(${g},${g},${g})"></span></button>`;
  }).join("") + `</div>`;
}
const dialShownGs = dialShown;
dialShown = function (B, part, key) {
  if (part === "hand" && key === "sorder") return shiftRule(B) === SHIFT.PATTERN;
  if (part === "hand" && key === "sgreys") return shiftRule(B) !== SHIFT.OFF;
  return dialShownGs(B, part, key);
};

// ---------- words: "Greys shift each stroke at random." after what the brush is (the app's SwatchPicker.words) ----------
function shiftWords(B) {
  const rule = shiftRule(B);
  return rule === SHIFT.RANDOM ? SHIFT.WORDS.random : rule === SHIFT.DECK ? SHIFT.WORDS.deck
    : rule === SHIFT.PATTERN ? ((shiftH(B, "sorder") | 0) === SHIFT.PING_PONG ? SHIFT.WORDS.pingpong : SHIFT.WORDS.order) : null;
}
// What comes before it, as the app's ModularWords: the noun, then Build-up's, a Stamp's, a Crayon's and a Spiro's own.
const SHIFT_AFTER = new Set(["Builds up where it lingers.", "Stamps a shape cut from the page.", "Fades as it prints.", "Builds up, resists washes.", "Builds up.",
  "Scrapes back to white.", SPIRO_WORDS.line, SPIRO_WORDS.ring]);
const plainWordsGs = plainWords;
plainWords = function (B) {
  const said = shiftWords(B), text = plainWordsGs(B);
  if (!said) return text;
  const all = text.split(/(?<=\.)\s+/).filter(Boolean);
  let i = 1;
  while (i < all.length && SHIFT_AFTER.has(all[i])) i++;
  all.splice(i, 0, said);
  let t2 = all[0];
  for (let j = 1; j < all.length; j++) { if (j >= 2 && t2.length + 1 + all[j].length > 260) break; t2 += " " + all[j]; }
  return t2;
};

// ---------- checks ----------
function shiftChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok });
  const mk = hand => normBrush({ name: "shifty", seed: 17, style: "Fineliner", tip: freshPart("tip", "round"), medium: freshPart("medium", "ink"), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: Object.assign(freshPart("hand", "steady"), { d: Object.assign(freshPart("hand", "steady").d, hand || {}) }) });
  const picks = (B, n, bar) => { const st = shiftTurn(); let s = 7; st.random = () => ((s = Math.imul(s ^ (s >>> 15), 0x2c1b3c6d) + 0x6d2b79f5 >>> 0) / 4294967296); return Array.from({ length: n }, () => shiftPick(B, st, bar)); };
  // Codes: the hand's last dials, after a Spiro's drives; off, nothing.
  { const B = mk({ shift: 3, sorder: 1, sgreys: 1, sany: 0x81, sany2: 0xc0 }), c = encodeM(B), back = readAnyCode(c).b, hand = encodeM(mk()).split(".")[1].split("_")[5];
    t("a code with Grey Shift (" + c + ") reads back, its set too; off, the hand writes as before (" + hand + ")",
      encodeM(back) === c && shiftSet(back).join() === shiftSet(B).join() && hand.length === 22 && HAND_DIALS.slice(HAND_DIALS.indexOf("shift"), HAND_DIALS.indexOf("shift") + 6).join(" ") === "shift sorder sgreys sbar sany sany2"
      && encodeM(mk({ shift: 1 })).split(".")[1].split("_")[5].length === 22 + 11); }
  // The rules.
  { const r = picks(mk({ shift: 1, sbar: 5 }), 200), deck = picks(mk({ shift: 2, sgreys: 1 }), 15 * 20), set = shiftSet(mk({ shift: 2, sgreys: 1 }));
    let dealt = true, twice = false;
    for (let d = 0; d < 20; d++) dealt = dealt && new Set(deck.slice(d * 15, d * 15 + 15)).size === 15;
    for (let i = 1; i < deck.length; i++) twice = twice || deck[i] === deck[i - 1];
    t("Random picks from the set only; From a Set deals each of Any's 15 (no paper white) once before any again, never one twice running",
      r.every(k => k === 1 || k === 3) && r.includes(1) && r.includes(3) && set.length === 15 && set[0] === 1 && dealt && !twice); }
  t("Pattern: In Order 0 1 2 3 0, Ping-Pong 0 1 2 3 2 1 0, over the set only",
    picks(mk({ shift: 3 }), 5).join() === "0,1,2,3,0" && picks(mk({ shift: 3, sorder: 1 }), 7).join() === "0,1,2,3,2,1,0" && picks(mk({ shift: 3, sbar: 2 }), 4).join() === "0,2,3,0");
  { const B = mk({ shift: 1 }), set = B => shiftSet(B).length;
    t("each palette its own set, the last grey stays; off picks nothing; Toolbar's greys the bar's",
      shiftToggle(B, 0) && shiftToggle(B, 1) && shiftToggle(B, 2) && !shiftToggle(B, 3) && set(B) === 1 && B.hand.d.sany === 1 && picks(mk(), 1)[0] === -1
      && shiftDark(B, 2, [.2, .5, .9]) === .9 && shiftSize(B, [.2, .5, .9]) === 3); }
  // Words, never rolled.
  { const roll = () => { for (let i = 0; i < 1400; i++) if (shiftRule(rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]))) return false; return true; };
    t("words: \"" + plainWords(mk({ shift: 1 })) + "\"; never rolled",
      plainWords(mk({ shift: 1 })).includes(SHIFT.WORDS.random) && plainWords(mk({ shift: 3, sorder: 1 })).includes(SHIFT.WORDS.pingpong) && !plainWords(mk()).includes("Greys")
      && dialShown(mk({ shift: 3 }), "hand", "sorder") && !dialShown(mk({ shift: 1 }), "hand", "sorder") && !dialShown(mk(), "hand", "sgreys") && roll()); }
  return out;
}
const selfTestGs = selfTest;
selfTest = function () { const r = selfTestGs(); r.shift = shiftChecks(); r.ok = r.ok && r.shift.every(c => c.ok); NM.lastShift = r.shift; return r; };
if (typeof runChecks === "function") {
  const runChecksGs = runChecks;
  runChecks = function () {
    runChecksGs();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastShift) return;
      out.textContent += "\n\nGrey Shift:\n" + NM.lastShift.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 180);
  };
}

// ===================================================================================================================
// Stay in Lines (mischief-lab-2.html's first pad, "Stay in the lines"; spec its NOTES "## 1. Stay in the lines"; the
// app's PartChoice.STAY_IN and StayIn): Smudge choice 5, Flood. Wet paint treats the crisp ink lines as walls, like a
// colouring book: the stroke keeps to the shape it starts in and floods on through it past the brush, 220 × flood² px
// (0 the brush alone, 1 the whole shape, "Fills"), about 3 px a frame by pen time. Only the ink layer's marks make walls:
// here, what strokes of an ink-layer medium laid (STAY.inks: ink, charcoal, graphite, resist, screentone, crayon), kept
// in the surface's walls plane; a wash or any soft mark never holds it. Its walls, margin, line under the edge and
// pockets are the prototype's walls1 and room1; open paper (a shape reaching the page's edge) is no shape, and the
// stroke a plain wash. Never rolled, never a variation's swap.
// ===================================================================================================================
DIALS.carry.flood = { label: "Flood", min: 0, max: 1, def: .35, fmt: v => v < .005 ? "Brush" : v > .995 ? "Fills" : Math.max(1, Math.round(220 * v * v * 1.875 * 25.4 / 300)) + " mm" };
CHOICES.carry.push({ k: "stayin", label: "Stay in Lines", dials: ["flood"], macro: "flood", words: ["brush", "fills"], blurb: "Keeps inside crisp ink lines, like a colouring book, and floods through them." });
// Filled Shape (the app's PartChoice.SHAPE and FilledShape, 2026-10-23): Smudge choice 6, no dials. In the app the
// outline closes at the lift and the area inside fills with the same brush (its FillTool, which has no twin here), so
// the lab and Studio say it and carry its code, and their pictures draw the outline alone. Never rolled or swapped to.
CHOICES.carry.push({ k: "shape", label: "Filled Shape", dials: [], blurb: "Closes its outline at the lift and fills the shape with the same paint." });
const STAY = { inks: new Set(["ink", "charcoal", "graphite", "resist", "tone", "crayon"]), LINE: .4, GEO_K: 14, GEO_MIN: 20, BOX: 6, RATE: 9 / (1000 / 60), UNTIMED: 9,
  BUDGET: 2000, ENDLESS: 1e9, MEET: 12, RIM: 7, RIM_FROM: 12, FADE_ALL: 1800 };
STAY.reach = flood => flood > .995 ? STAY.ENDLESS : Math.round(3 * 220 * flood * flood);
// A variation never swaps to it, and no roll picks it (the app's ModularFeatures.seedOnly).
const makeVarsLines = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.carry.slice();
  CHOICES.carry = keep.filter(c => (c.k !== "stayin" && c.k !== "shape") || c.k === B.carry.k);
  try { return makeVarsLines(B, locks); } finally { CHOICES.carry = keep; }
};
// Resolve: no smudge (it moves no paint along); its Flood, none on water. Filled Shape smudges nothing either.
const resolveLines = resolve;
resolve = function (B, size, shade) {
  if (B.carry.k !== "stayin" && B.carry.k !== "shape") return resolveLines(B, size, shade);
  const A = cloneBrush(B); A.carry = freshPart("carry", "off");
  const R = resolveLines(A, size, shade);
  if (B.carry.k === "shape") return R;
  if (R.life !== "float" && (R.line || R.body)) R.stayIn = B.carry.d.flood ?? .35;
  return R;
};

// ---------- the walls plane: what ink-layer strokes laid, the eraser takes back, a clear page clears ----------
const blankPageLines = blankPage;
blankPage = function (s) { blankPageLines(s); if (s.walls) s.walls.fill(0); };
const EraserLines = Eraser;
Eraser = class extends EraserLines {
  disc(cx, cy, rad, ...rest) {
    const r = super.disc(cx, cy, rad, ...rest), s = this.s, wl = s.walls;
    if (wl) {
      const R = Math.ceil(rad) + 2, x0 = Math.max(0, Math.floor(cx - R)), x1 = Math.min(s.w - 1, Math.ceil(cx + R)), y0 = Math.max(0, Math.floor(cy - R)), y1 = Math.min(s.h - 1, Math.ceil(cy + R));
      for (let y = y0; y <= y1; y++) for (let i = y * s.w + x0, e = y * s.w + x1; i <= e; i++) if (wl[i] > 0) wl[i] = Math.min(wl[i], clamp(1 - s.page[i] / PAPER_TONE));
    }
    return r;
  }
};

// ---------- the shape: the prototype's edge1, walls1 and room1 on the walls plane ----------
// Each pixel's distance to the nearest line pixel out to 6 px (Infinity beyond), and that pixel.
function stayEdges(w, h, ink) {
  const n = w * h, B = STAY.BOX, BW = 2 * B + 1, e = new Float32Array(n).fill(Infinity), near = new Int32Array(n).fill(-1);
  for (let i = 0; i < n; i++) {
    if (ink[i] <= STAY.LINE) continue;
    e[i] = 0; near[i] = i;
    const x = i % w, y = (i - x) / w;
    if (x > 0 && x < w - 1 && y > 0 && y < h - 1 && ink[i - 1] > STAY.LINE && ink[i + 1] > STAY.LINE && ink[i - w] > STAY.LINE && ink[i + w] > STAY.LINE) continue;
    for (let yy = Math.max(0, y - B); yy <= Math.min(h - 1, y + B); yy++) for (let xx = Math.max(0, x - B); xx <= Math.min(w - 1, x + B); xx++) {
      const j = yy * w + xx, d = Math.hypot(xx - x, yy - y);
      if (d < e[j]) { e[j] = d; near[j] = i; }
    }
  }
  return { e, near };
}
function stayDilate(m, w, h) {
  const t = new Uint8Array(w * h), o = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * w + x; t[i] = m[i] | (x > 0 ? m[i - 1] : 0) | (x < w - 1 ? m[i + 1] : 0); }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * w + x; o[i] = t[i] | (y > 0 ? t[i - w] : 0) | (y < h - 1 ? t[i + w] : 0); }
  return o;
}
// The walls: line pixels, thickened 2 px only where the ink near by is two pieces or one that meets only a long way round.
function stayWalls(w, h, ink, near) {
  const n = w * h, B = STAY.BOX, BW = 2 * B + 1, core = new Uint8Array(n);
  for (let i = 0; i < n; i++) core[i] = ink[i] > STAY.LINE ? 1 : 0;
  const piece = new Int32Array(n), q = new Int32Array(n);
  for (let s = 0, np = 0; s < n; s++) {
    if (!core[s] || piece[s]) continue;
    piece[s] = ++np; let head = 0, tail = 0; q[tail++] = s;
    while (head < tail) {
      const i = q[head++], x = i % w, y = (i - x) / w;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
        const j = yy * w + xx;
        if (core[j] && !piece[j]) { piece[j] = np; q[tail++] = j; }
      }
    }
  }
  const band = stayDilate(stayDilate(core, w, h), w, h), wall = core.slice();
  const loc = new Int32Array(BW * BW), wq = new Int32Array(BW * BW), seen = new Int32Array(n), lev = new Int32Array(n);
  const repI = new Int32Array(BW * BW), repD = new Float32Array(BW * BW), want = new Int32Array(BW * BW);
  let gen = 0;
  for (let p = 0; p < n; p++) {
    if (!band[p] || core[p]) continue;
    const px = p % w, py = (p - px) / w, c = near[p], nx = c % w - px, ny = (c - c % w) / w - py, nl = Math.hypot(nx, ny);
    const x0 = Math.max(0, px - B), x1 = Math.min(w - 1, px + B), y0 = Math.max(0, py - B), y1 = Math.min(h - 1, py + B);
    let first = 0, two = false, far = false;
    for (let y = y0; y <= y1 && !two; y++) for (let x = x0; x <= x1; x++) {
      const k = piece[y * w + x];
      if (!k) continue;
      if (k !== first) { if (!first) first = k; else { two = true; break; } }
      const dx = x - px, dy = y - py;
      if (!far && dx * nx + dy * ny < -.2 * nl * Math.hypot(dx, dy)) far = true;
    }
    if (two) { wall[p] = 1; continue; }
    if (!far) continue;
    loc.fill(0); let parts = 0;
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const c0 = (y - y0) * BW + (x - x0);
      if (loc[c0] || !core[y * w + x]) continue;
      const id = ++parts; let head = 0, tail = 0, best = -1, bd = Infinity;
      loc[c0] = id; wq[tail++] = c0;
      while (head < tail) {
        const cc = wq[head++], cx = cc % BW, cy = (cc - cx) / BW, gx = x0 + cx, gy = y0 + cy, d = Math.hypot(gx - px, gy - py);
        if (d < bd) { bd = d; best = gy * w + gx; }
        for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
          const ax = gx + dx, ay = gy + dy;
          if (ax < x0 || ay < y0 || ax > x1 || ay > y1) continue;
          const c2 = (ay - y0) * BW + (ax - x0);
          if (!loc[c2] && core[ay * w + ax]) { loc[c2] = id; wq[tail++] = c2; }
        }
      }
      repI[id - 1] = best; repD[id - 1] = bd;
    }
    if (parts < 2) continue;
    let src = 0;
    for (let k = 1; k < parts; k++) if (repD[k] < repD[src]) src = k;
    let lim = 0, left = parts - 1;
    for (let k = 0; k < parts; k++) { want[k] = k === src ? -1 : Math.max(STAY.GEO_MIN, Math.ceil(STAY.GEO_K * (repD[src] + repD[k]))); lim = Math.max(lim, want[k]); }
    gen++; let head = 0, tail = 0; q[tail++] = repI[src]; seen[repI[src]] = gen; lev[repI[src]] = 0;
    while (head < tail && left) {
      const i = q[head++], li = lev[i];
      for (let k = 0; k < parts; k++) if (want[k] >= 0 && repI[k] === i) { want[k] = -1; left--; }
      if (li >= lim) continue;
      const x = i % w, y = (i - x) / w;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
        const j = yy * w + xx;
        if (core[j] && seen[j] !== gen) { seen[j] = gen; lev[j] = li + 1; q[tail++] = j; }
      }
    }
    if (left) wall[p] = 1;
  }
  return wall;
}
// The shape round x, y on a surface w × h whose walls are ink (0 to 1): { room, edge, pockets }, each pixel's kind 1
// open, 2 margin, 3 the line under the edge, 4 behind it, 5 a pocket, 0 elsewhere; null with no open pixel within 8 px,
// or open paper reaching the edge.
function stayShape(w, h, ink, x, y) {
  const n = w * h, { e: edge, near } = stayEdges(w, h, ink), wall = stayWalls(w, h, ink, near), L = STAY.LINE;
  let s = -1;
  for (let r = 0; r <= 8 && s < 0; r++) for (let dy = -r; dy <= r && s < 0; dy++) for (let dx = -r; dx <= r; dx++) {
    if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
    const px = Math.floor(x) + dx, py = Math.floor(y) + dy;
    if (px >= 0 && py >= 0 && px < w && py < h && !wall[py * w + px]) { s = py * w + px; break; }
  }
  if (s < 0) return null;
  const room = new Uint8Array(n), q = new Int32Array(n); let head = 0, tail = 0;
  room[s] = 1; q[tail++] = s;
  const go = j => { if (!wall[j] && !room[j]) { room[j] = 1; q[tail++] = j; } };
  while (head < tail) {
    const i = q[head++], px = i % w;
    if (px === 0 || px === w - 1 || i < w || i >= n - w) return null;
    go(i - 1); go(i + 1); go(i - w); go(i + w);
  }
  head = 0; tail = 0;
  for (let i = 0; i < n; i++) if (room[i] && edge[i] < 5) q[tail++] = i;
  const down = (i, j) => { if (!room[j] && wall[j] && ink[j] <= L && edge[j] <= edge[i]) { room[j] = 2; q[tail++] = j; } };
  while (head < tail) {
    const i = q[head++], px = i % w;
    if (px > 0) down(i, i - 1); if (px < w - 1) down(i, i + 1); if (i >= w) down(i, i - w); if (i < n - w) down(i, i + w);
  }
  for (let layer = 3; layer <= 4; layer++) {
    const add = [];
    for (let i = 0; i < n; i++) {
      if (room[i] || (ink[i] <= L && (layer === 4 || edge[i] > 1))) continue;
      const px = i % w, ok = v => layer === 3 ? v === 1 || v === 2 : v === 3;
      if ((px > 0 && ok(room[i - 1])) || (px < w - 1 && ok(room[i + 1])) || (i >= w && ok(room[i - w])) || (i < n - w && ok(room[i + w]))) add.push(i);
    }
    for (const i of add) room[i] = layer;
  }
  const pk = new Uint8Array(n), seen = new Int32Array(n), pockets = new Map(); let gen = 0;
  for (let s0 = 0; s0 < n; s0++) {
    if (pk[s0] || room[s0] || ink[s0] > L) continue;
    pk[s0] = 1; head = 0; tail = 0; q[tail++] = s0;
    let open = false, thick = 0;
    while (head < tail) {
      const i = q[head++], px = i % w;
      if (edge[i] > thick) thick = edge[i];
      const nb = j => { if (!room[j] && !pk[j] && ink[j] <= L) { pk[j] = 1; q[tail++] = j; } };
      if (px > 0) nb(i - 1); else open = true;
      if (px < w - 1) nb(i + 1); else open = true;
      if (i >= w) nb(i - w); else open = true;
      if (i < n - w) nb(i + w); else open = true;
    }
    if (open || !(tail <= 24 || (tail <= 1000 && thick <= 3.2))) continue;
    let anchor = -1; gen++;
    let ring = [];
    for (let k = 0; k < tail; k++) { ring.push(q[k]); seen[q[k]] = gen; }
    for (let step = 0; step <= 12 && anchor < 0 && ring.length; step++) {
      const next = [];
      for (const i of ring) {
        const px = i % w;
        for (const j of [px > 0 ? i - 1 : -1, px < w - 1 ? i + 1 : -1, i - w, i + w]) {
          if (j < 0 || j >= n || seen[j] === gen) continue;
          seen[j] = gen;
          const r = room[j];
          if (r >= 1 && r <= 4) { anchor = j; break; }
          if (ink[j] > L) next.push(j);
        }
        if (anchor >= 0) break;
      }
      ring = next;
    }
    if (anchor < 0) continue;
    let list = pockets.get(anchor); if (!list) pockets.set(anchor, list = []);
    for (let k = 0; k < tail; k++) { room[q[k]] = 5; list.push(q[k]); }
  }
  // The meeting the line rule's distance, in the flood's thirds of a pixel, capped as the app's.
  const e3 = new Float32Array(n);
  for (let i = 0; i < n; i++) e3[i] = Math.min(18, 3 * edge[i]);
  return { room, edge: e3, pockets };
}

// ---------- the flood: the prototype's bucket queue, by pen time ----------
class StayFlood {
  constructor(surf, shape, flood) {
    const n = surf.w * surf.h;
    Object.assign(this, { s: surf, room: shape.room, edge: shape.edge, pockets: shape.pockets, reach: STAY.reach(flood), dist: new Float64Array(n).fill(Infinity),
      at: new Int32Array(n).fill(-1), val: new Float32Array(n), queue: new Map(), queued: 0, key: 0, t0: null, tint: 0, walked: -1, next: 0, changed: [surf.w, surf.h, -1, -1] });
  }
  push(i, d, key) {
    if (d >= this.dist[i]) return;
    this.dist[i] = d; this.at[i] = key;
    let b = this.queue.get(key); if (!b) this.queue.set(key, b = []);
    b.push(i); this.queued++;
  }
  rim(x, y, r) {
    const { w, h } = this.s, put = (px, py) => { if (px < 0 || py < 0 || px >= w || py >= h) return; const i = py * w + px, k = this.room[i]; if (k && k !== 5) this.push(i, 0, this.key); };
    put(Math.floor(x), Math.floor(y));
    const m = Math.max(8, Math.ceil(2 * Math.PI * r * 1.5));
    for (let k = 0; k < m; k++) { const a = 2 * Math.PI * k / m; put(Math.floor(x + r * Math.cos(a)), Math.floor(y + r * Math.sin(a))); }
  }
  // The samples not yet walked wet their rims every .3 of radius; the flood runs on to pen time t, painting buf.
  sample(pts, t, radius, tint, buf) {
    this.tint = tint;
    const r = Math.max(1, radius), gap = Math.max(1, r * .3);
    for (let k = this.walked + 1; k < pts.length; k++, this.walked++) {
      if (k === 0) { this.rim(pts[0].x, pts[0].y, r); this.next = gap; continue; }
      const a = pts[k - 1], b = pts[k], seg = b.d - a.d;
      for (; this.next <= b.d; this.next += gap) { const u = seg > 0 ? (this.next - a.d) / seg : 1; this.rim(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u, r); }
    }
    if (this.t0 === null && Number.isFinite(t)) this.t0 = t;
    const target = Number.isFinite(t) && this.t0 !== null ? (t - this.t0) * STAY.RATE : this.key + STAY.UNTIMED;
    this.run(target, STAY.BUDGET, buf);
    return this.took();
  }
  finish(buf) { this.run(Infinity, Infinity, buf); return this.took(); }
  run(target, most, buf) {
    let work = 0;
    while (this.queued && this.key < target && work < most) {
      const b = this.queue.get(this.key);
      if (b) { this.queue.delete(this.key); this.queued -= b.length; for (const i of b) { this.settle(i, this.key, buf); work++; } }
      this.key++;
    }
    if (!this.queued && target !== Infinity && this.key < target) this.key = Math.floor(target);
  }
  took() { const c = this.changed; this.changed = [this.s.w, this.s.h, -1, -1]; return c[2] < c[0] ? null : c; }
  wet(i, d, me) {
    const R = this.reach, u = R >= STAY.ENDLESS ? Math.min(1, d / STAY.FADE_ALL) : R ? Math.min(1, d / R) : 0;
    let v = this.tint * (1 - .4 * u * u) * (.88 + .24 * this.s.blob[i]);
    if (me >= 2) v *= 1.3;
    else if (R < STAY.ENDLESS && R > STAY.RIM_FROM && R - d < STAY.RIM) v *= 1.22;
    return Math.min(1, v);
  }
  settle(i, key, buf) {
    if (this.at[i] !== key) return;
    const { w, h } = this.s, room = this.room, edge = this.edge, d = this.dist[i], x = i % w, y = (i - x) / w, me = room[i], v = this.wet(i, d, me);
    if (v > this.val[i]) { this.val[i] = v; if (v > buf[i]) { buf[i] = v; grow(this.changed, [x, y, x, y]); } }
    const pocket = this.pockets.get(i);
    if (pocket) for (const j of pocket) this.push(j, d + 3, key + 3);
    if (me >= 4) return;
    const go = (j, step) => {
      const to = room[j];
      if (!to || to === 5 || (me === 3 ? to !== 4 : to === 4)) return;
      if (d + step <= this.reach || to >= 3 || edge[j] < edge[i] || (to === 2 && edge[j] <= edge[i] && d + step <= this.reach + STAY.MEET)) this.push(j, d + step, key + step);
    };
    if (x > 0) go(i - 1, 3); if (x < w - 1) go(i + 1, 3); if (y > 0) go(i - w, 3); if (y < h - 1) go(i + w, 3);
    if (me === 3 || d >= this.reach) return;
    const open = j => room[j] === 1 || room[j] === 2;
    const l = x > 0 && open(i - 1), r = x < w - 1 && open(i + 1), u = y > 0 && open(i - w), dn = y < h - 1 && open(i + w);
    if (l && u && open(i - w - 1)) go(i - w - 1, 4); if (r && u && open(i - w + 1)) go(i - w + 1, 4);
    if (l && dn && open(i + w - 1)) go(i + w - 1, 4); if (r && dn && open(i + w + 1)) go(i + w + 1, 4);
  }
  // Buffers kept to the shape over rect; the flood laid again where stronger.
  keep(buf, rect) {
    if (!buf) return;
    const s = this.s, x0 = Math.max(0, rect[0]), x1 = Math.min(s.w - 1, rect[2]);
    for (let y = Math.max(0, rect[1]); y <= Math.min(s.h - 1, rect[3]); y++) for (let i = y * s.w + x0, e = y * s.w + x1; i <= e; i++) if (!this.room[i] && buf[i]) buf[i] = 0;
  }
  relay(buf, rect) {
    const s = this.s, x0 = Math.max(0, rect[0]), x1 = Math.min(s.w - 1, rect[2]);
    for (let y = Math.max(0, rect[1]); y <= Math.min(s.h - 1, rect[3]); y++) for (let i = y * s.w + x0, e = y * s.w + x1; i <= e; i++) if (this.val[i] > buf[i]) buf[i] = this.val[i];
  }
}

// ---------- the stroke: its shape at the first sample, kept to it, flooding; an ink-layer stroke's walls ----------
let stayLifting = null;
const composeLines = composeStroke;
composeStroke = function (st, rect, phase) {
  const f = st.stay;
  if (f) { const s = st.surf; f.keep(s.L, rect); f.keep(s.W, rect); f.keep(s.D, rect); f.keep(s.relief, rect); if (st.R.life === "wet" && st.R.body) f.relay(s.D, rect); }
  return composeLines(st, rect, phase);
};
// A Slow lift settles the body laid afresh with the flood: kept in, so its settling after lift stays in too.
for (const name of ["settleBody", "settleBlend"]) {
  const prev = globalThis[name] || (name === "settleBody" ? settleBody : settleBlend);
  const wrapped = function (...a) { const r = prev(...a), st = stayLifting; if (st && st.stay && r) { st.stay.keep(st.surf.D, r); st.stay.keep(st.surf.relief, r); } return r; };
  if (name === "settleBody") settleBody = wrapped; else settleBlend = wrapped;
}
const StrokeLines = Stroke;
Stroke = class extends StrokeLines {
  constructor(surf, B, size, shade, host) {
    super(surf, B, size, shade, host);
    this.inks = STAY.inks.has(B.medium.k);
  }
  stayBuf() { return this.R.life === "wet" ? this.surf.D : this.surf.W; }
  stayTint() {
    const b = this.R.body, q = this.pts[this.pts.length - 1];
    return b && q ? (b.mode === "wet" ? lerp(.7, 1, q.p) : 1) * (b.amount ?? 1) * handDark(b, q.p, q.v || 0, 0) : 0;
  }
  stayFlood(t) {
    const f = this.stay;
    if (!f || !this.R.body) return null;
    const r = t === Infinity ? (f.sample(this.pts, NaN, .45 * this.R.bodySize, this.stayTint(), this.stayBuf()), f.finish(this.stayBuf()))
      : f.sample(this.pts, t, .45 * this.R.bodySize, this.stayTint(), this.stayBuf());
    if (r) { composeStroke(this, r, this.R.life === "dry" ? "preview" : "final"); grow(this.rect, r); }
    return r;
  }
  add(samples) {
    if (this.R.stayIn !== undefined && this.stayLooked === undefined && samples.length) {
      this.stayLooked = true;
      const s = this.surf, sh = s.walls ? stayShape(s.w, s.h, s.walls, samples[0][0], samples[0][1]) : null;
      if (sh) this.stay = new StayFlood(s, sh, this.R.stayIn);
    }
    const r = super.add(samples);
    if (this.stay && samples.length) { const f = this.stayFlood(samples[samples.length - 1][3]); if (f) return r ? union(r, f) : f; }
    return r;
  }
  stampW(buf, final, from) {
    const r = super.stampW(buf, final, from);
    // A Slow lift lays the body afresh: the flood with it.
    if (final && this.stay) this.stay.relay(buf, [0, 0, this.surf.w - 1, this.surf.h - 1]);
    return r;
  }
  end() {
    if (this.stay) this.stayFlood(Infinity);
    stayLifting = this;
    let r;
    try { r = super.end(); } finally { stayLifting = null; }
    // An ink-layer stroke's marks are walls from now on: how much it darkened the page as it found it.
    if (this.inks && r) {
      const s = this.surf, wl = s.walls || (s.walls = new Float32Array(s.w * s.h)), x0 = Math.max(0, r[0]), x1 = Math.min(s.w - 1, r[2]);
      for (let y = Math.max(0, r[1]); y <= Math.min(s.h - 1, r[3]); y++) for (let i = y * s.w + x0, e = y * s.w + x1; i <= e; i++) {
        const k = s.base[i] > 0 ? clamp(1 - s.page[i] / s.base[i]) : 0;
        if (k > wl[i]) wl[i] = k;
      }
    }
    return r;
  }
};

// ---------- words: "Floods inside ink lines." in the smudge's place ----------
const stayWords = B => { const f = B.carry.d.flood ?? .35; return f < .005 ? "Stays inside ink lines." : f > .995 ? "Fills the shape it starts in." : "Floods inside ink lines."; };
const choiceWordsLines = choiceWords;
choiceWords = function (B, part) { return part === "carry" && B.carry.k === "stayin" ? "Stay in" : part === "carry" && B.carry.k === "shape" ? "Shape" : choiceWordsLines(B, part); };
const plainWordsLines = plainWords;
plainWords = function (B) {
  if (B.carry.k !== "stayin" && B.carry.k !== "shape") return plainWordsLines(B);
  // As Lift says its smudge, in the same place: "Lifts paint." becomes its own.
  const A = cloneBrush(B); A.carry = freshPart("carry", "lift");
  const t = plainWordsLines(normBrush(A));
  return t.includes("Lifts paint.") ? t.replace("Lifts paint.", B.carry.k === "shape" ? "Fills the shape it outlines." : stayWords(B)) : t;
};

// ---------- checks ----------
function stayChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok });
  const mk = (flood, life) => { const B = { name: "lines", seed: 7, style: "Watercolor", tip: freshPart("tip", "round"), medium: freshPart("medium", "wash"), carry: freshPart("carry", "stayin"),
    paper: freshPart("paper", "plain"), life: freshPart("life", life || "none"), hand: freshPart("hand", "steady") }; B.carry.d.flood = flood; return normBrush(B); };
  const seg = c => c.split("_")[2];
  // Codes: Smudge choice 5, Flood two digits; they read back; older codes as before.
  { const c = encodeM(mk(.6)), back = readAnyCode(c).b;
    const olds = [...LEGACY_CODES.map(x => encodeM(readAnyCode(x).b)), OIL_PAINT];
    t("Stay in Lines' code is _" + seg(c) + "_, Smudge choice " + CHOICES.carry.findIndex(ch => ch.k === "stayin") + "; it reads back; older codes as before",
      seg(c) === "5" + two36(Math.round(.6 * M_STEPS)) && encodeM(back) === c && back.carry.k === "stayin" && CHOICES.carry.findIndex(ch => ch.k === "stayin") === 5 && olds.every(x => encodeM(readAnyCode(x).b) === x)); }
  // The prototype's ring: radius 120, a 4 px gap, drawn in ink; a tap inside at Fills fills it to the line and no farther.
  const W = 360, H = 360, ink = freshPart("medium", "ink");
  const pen = normBrush({ name: "pen", seed: 3, style: "Fineliner", tip: freshPart("tip", "round"), medium: Object.assign(ink, { d: Object.assign(ink.d, { wet: 0 }) }), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: freshPart("hand", "steady") });
  const arc = (cx, cy, r, a0, a1) => Array.from({ length: Math.ceil(Math.abs(a1 - a0) * r / 2) + 1 }, (_, k, n) => { const a = a0 + (a1 - a0) * k / (Math.ceil(Math.abs(a1 - a0) * r / 2)); return { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a), p: .8, t: k * 4, tx: NaN, ty: NaN }; });
  const tap = (s, B, x, y) => layStroke(s, B, [{ x, y, p: .8, t: 0, tx: NaN, ty: NaN }], 30, .6, null);
  { const s = modSurface(W, H, 7); layStroke(s, pen, arc(180, 180, 120, -Math.PI / 2 + 4 / 120, 1.5 * Math.PI), 3, 1, null);
    const before = Float32Array.from(s.page), { st } = tap(s, mk(1), 180, 180);
    let outside = 0, dry = 0;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = y * W + x, r = Math.hypot(x + .5 - 180, y + .5 - 180); if (r > 125 && s.page[i] !== before[i]) outside++; if (r < 115 && s.page[i] >= before[i]) dry++; }
    t("a ring with a 4 px gap holds the flood (" + outside + " px outside), Fills fills it (" + dry + " dry)", st.stay && outside === 0 && dry === 0); }
  // A 10° wedge, closed: a tap in it at Fills reaches its point.
  { const s = modSurface(W, H, 7), a = 5 * DEG, L = 300, ax = 20, ay = 180;
    const line = (x0, y0, x1, y1) => Array.from({ length: 151 }, (_, k) => ({ x: x0 + (x1 - x0) * k / 150, y: y0 + (y1 - y0) * k / 150, p: .8, t: k * 4, tx: NaN, ty: NaN }));
    layStroke(s, pen, line(ax, ay, ax + L * Math.cos(a), ay - L * Math.sin(a)), 3, 1, null); layStroke(s, pen, line(ax, ay, ax + L * Math.cos(a), ay + L * Math.sin(a)), 3, 1, null);
    layStroke(s, pen, line(ax + L * Math.cos(a) - 6, ay - L * Math.sin(a) - 6, ax + L * Math.cos(a) - 6, ay + L * Math.sin(a) + 6), 3, 1, null);
    const before = Float32Array.from(s.page), { st } = tap(s, mk(1), ax + L * .6, ay);
    let tip = Infinity, paper = Infinity;
    for (let y = ay - 20; y < ay + 20; y++) for (let x = ax; x < ax + L; x++) {
      if (Math.abs(y + .5 - ay) > (x + .5 - ax) * Math.tan(a)) continue;
      const i = y * W + x;
      if (s.page[i] < before[i]) tip = Math.min(tip, x);
      if (before[i] > PAPER_TONE * .8) paper = Math.min(paper, x);
    }
    t("a 10° wedge fills to its point: wet from x = " + tip + ", paper from x = " + paper, st.stay && tip <= paper); }
  // Flood 0 is the brush alone; on open paper a plain wash; a wash line is no wall.
  { const s = modSurface(W, H, 7); layStroke(s, pen, arc(180, 180, 120, 0, 2 * Math.PI + .05), 3, 1, null);
    const before = Float32Array.from(s.page); tap(s, mk(0), 180, 180);
    let far = 0; for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (s.page[y * W + x] < before[y * W + x]) far = Math.max(far, Math.hypot(x - 180, y - 180));
    const a = modSurface(W, H, 7), b = modSurface(W, H, 7), plain = mk(1); plain.carry = freshPart("carry", "off");
    tap(a, plain, 180, 180); const { st } = tap(b, mk(1), 180, 180);
    t("Flood 0 is the brush alone (wet to " + Math.round(far) + " px from a 30 px brush's centre); on open paper a plain wash", far < 30 && !st.stay && a.page.every((v, i) => v === b.page[i])); }
  // Words, rolls and swaps.
  { const B = mk(.5), roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).carry.k === "stayin") return false; return true; };
    const vars = () => { const lk = { parts: new Set(), dials: new Set() }, w = mk(.5); w.carry = freshPart("carry", "off"); for (let i = 0; i < 200; i++) if (makeVars(normBrush(w), lk).some(v => v.b.carry.k === "stayin")) return false; return true; };
    t("words: \"" + plainWords(B) + "\"; " + choiceWords(B, "carry") + "; " + captionItems(B).join(", ") + "; Flood " + fmtDial("carry", "flood", .35) + "; never rolled or swapped to",
      plainWords(B).includes("Floods inside ink lines.") && plainWords(mk(0)).includes("Stays inside ink lines.") && plainWords(mk(1)).includes("Fills the shape it starts in.")
      && choiceWords(B, "carry") === "Stay in" && captionItems(B).includes("stay in lines") && fmtDial("carry", "flood", 0) === "Brush" && fmtDial("carry", "flood", 1) === "Fills"
      && fmtDial("carry", "flood", .35) === "4 mm" && roll() && vars()); }
  // Filled Shape: Smudge choice 6, no dials, its code read back; its words; never rolled or swapped to.
  { const B = mk(.5); B.carry = freshPart("carry", "shape"); const S = normBrush(B), c = encodeM(S), back = readAnyCode(c).b;
    const roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).carry.k === "shape") return false; return true; };
    const vars = () => { const lk = { parts: new Set(), dials: new Set() }, w = mk(.5); w.carry = freshPart("carry", "off"); for (let i = 0; i < 200; i++) if (makeVars(normBrush(w), lk).some(v => v.b.carry.k === "shape")) return false; return true; };
    t("Filled Shape's code is _" + seg(c) + "_, Smudge choice " + CHOICES.carry.findIndex(ch => ch.k === "shape") + "; it reads back; \"" + plainWords(S) + "\"; " + choiceWords(S, "carry") + "; never rolled or swapped to",
      seg(c) === "6" && CHOICES.carry.findIndex(ch => ch.k === "shape") === 6 && encodeM(back) === c && back.carry.k === "shape" && !resolve(S, 30, 1).carry
      && plainWords(S).includes("Fills the shape it outlines.") && choiceWords(S, "carry") === "Shape" && captionItems(S).includes("filled shape") && roll() && vars()); }
  return out;
}
const selfTestLines = selfTest;
selfTest = function () { const r = selfTestLines(); r.stayin = stayChecks(); r.ok = r.ok && r.stayin.every(c => c.ok); NM.lastStay = r.stayin; return r; };
if (typeof runChecks === "function") {
  const runChecksLines = runChecks;
  runChecks = function () {
    runChecksLines();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastStay) return;
      out.textContent += "\n\nStay in Lines:\n" + NM.lastStay.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 200);
  };
}

// ===================================================================================================================
// Gradient (the owner's "fading color (or gradient shift)"; the app's GreyGradient): the grey changes along each stroke,
// by the distance drawn (cm at the page's 300 dpi, the Manta's and the Nomad's alike), where Grey Shift picks one between
// strokes. Fade lightens the stroke's grey to paper over its Length; To Grey takes it to its End Grey (one of the
// panel's 16: 0 paper white to 15 black); Cycle steps through Grey Shift's set (its Greys, Toolbar or Any) from the
// stroke's grey on, round and round, eased from one to the next over each Length, a rainbow pencil's way. Each stroke
// starts again at its own grey. The stroke is laid at the darkest grey it reaches, each point's share of that in its
// own (times Paint Load's), which every stamper scales its darkness by: a wash's share the ratio of its strengths, any
// other mark's the ratio of greys. Ink, Wash, Charcoal, Graphite and Crayon take it, not on water. The hand's last three
// dials, after Grey Shift's; written only when on. Never rolled.
// ===================================================================================================================
const GRAD = { OFF: 0, FADE: 1, TO_GREY: 2, CYCLE: 3, LEVELS: 16, PX_CM: 300 / 2.54, TAKES: new Set(["ink", "wash", "charcoal", "graphite", "crayon"]) };
Object.assign(DIALS.hand, {
  grad: { label: "Gradient", choices: ["Off", "Fade", "To Grey", "Cycle"], def: 0 },
  gto: { label: "End Grey", min: 0, max: 15, def: 5, int: true, fmt: v => gradGreyShown(Math.round(v)) },
  glen: { label: "Length", min: 1, max: 36, def: 10, int: true, fmt: v => Math.round(v) + " cm" }
});
const GRADIENTS = ["grad", "gto", "glen"];
HAND_DIALS.push(...GRADIENTS);
for (const k of GRADIENTS) ADDED.add("hand." + k);
KEEPS.hand.push(...GRADIENTS);
const gradRule = B => (B.hand.d.grad ?? 0) | 0;
// Whether B's medium takes a grey per dab: not Opaque (it covers at its own grey), Resist, Screentone or Float.
const gradTakes = B => GRAD.TAKES.has(B.medium.k) && B.life.k !== "float";
const gradOn = B => gradRule(B) !== GRAD.OFF && gradTakes(B);
const gradEnd = B => Math.round(B.hand.d.gto ?? DIALS.hand.gto.def);
const gradCm = B => Math.round(B.hand.d.glen ?? DIALS.hand.glen.def);
function gradGreyShown(k) { return k <= 0 ? "White" : k >= GRAD.LEVELS - 1 ? "Black" : Math.round(100 * k / (GRAD.LEVELS - 1)) + "%"; }
// A stroke of B from `start` darkness (bar: the toolbar's greys for a Cycle on Toolbar); null when its grey stays.
function gradientOf(B, start, bar) {
  if (!gradOn(B)) return null;
  start = clamp(start);
  const rule = gradRule(B);
  let stops;
  if (rule === GRAD.FADE) stops = [start, 0];
  else if (rule === GRAD.TO_GREY) stops = [start, gradEnd(B) / (GRAD.LEVELS - 1)];
  else {
    // Cycle: from the stroke's grey to the set's next, round and round; from the nearest when it isn't in the set.
    const greys = shiftSet(B, bar).map(k => shiftDark(B, k, bar)), n = greys.length;
    let near = 0;
    for (let i = 0; i < n; i++) if (Math.abs(greys[i] - start) < Math.abs(greys[near] - start)) near = i;
    const first = Math.abs(greys[near] - start) < 1e-6 ? near + 1 : near;
    stops = [start, ...greys.map((_, i) => greys[(first + i) % n])];
  }
  const cycles = rule === GRAD.CYCLE, length = gradCm(B) * GRAD.PX_CM, ref = Math.max(...stops), washes = B.medium.k === "wash";
  const strength = d => washes ? washStrength(d) : d, refK = strength(ref);
  const stop = m => m === 0 ? stops[0] : stops[1 + (m - 1) % (stops.length - 1)];
  const darkness = d => {
    const u = Math.max(0, d) / length;
    if (!cycles) return lerp(stops[0], stops[1], Math.min(1, u));
    const m = Math.floor(u);
    return lerp(stop(m), stop(m + 1), smooth(u - m));
  };
  const share = dk => refK > 0 ? strength(dk) / refK : 0;
  return { ref, length, darkness, share, shareAt: d => share(darkness(d)) };
}
// The stroke: laid at the darkest grey it reaches, each new point's share in its own (the app's GreyGradient.apply), over
// Paint Load's; its oilSpend is the app's per-sample hook, so a gradient alone gives it one.
const StrokeGd = Stroke;
Stroke = class extends StrokeGd {
  constructor(surf, B, size, shade, host) {
    const g = gradientOf(B, shade, host && host.swatches);
    super(surf, B, size, g ? g.ref : shade, host);
    this.grad = g;
    if (g && !this.oil) this.oil = { grad: true };
  }
  oilSpend() {
    if (this.R.loaded) super.oilSpend();
    if (!this.grad) return;
    const q = this.pts[this.pts.length - 1], s = this.grad.shareAt(q.d);
    q.own = this.R.loaded ? q.own * s : s;
  }
};
// The Control card's Gradient rows (the app's BrushLab.handRows): its rule, a To Grey's End Grey, its Length, and a
// Cycle's Greys and set when Grey Shift doesn't show them; none for a medium that doesn't take it.
const gradRows = B => !gradTakes(B) ? [] : ["grad", ...(gradRule(B) === GRAD.TO_GREY ? ["gto"] : []), ...(gradRule(B) ? ["glen"] : []),
  ...(gradRule(B) === GRAD.CYCLE && !shiftRule(B) ? ["sgreys", "set"] : [])];
const dialShownGd = dialShown;
dialShown = function (B, part, key) {
  if (part === "hand" && key === "grad") return gradTakes(B);
  if (part === "hand" && key === "gto") return gradTakes(B) && gradRule(B) === GRAD.TO_GREY;
  if (part === "hand" && key === "glen") return gradOn(B);
  if (part === "hand" && key === "sgreys" && gradOn(B) && gradRule(B) === GRAD.CYCLE) return true;
  return dialShownGd(B, part, key);
};

// ---------- words: "Fades to paper over 10 cm." after Grey Shift's (the app's GreyGradient.words) ----------
function gradWords(B) {
  if (!gradOn(B)) return null;
  const over = gradCm(B) + " cm.", rule = gradRule(B), k = gradEnd(B);
  if (rule === GRAD.FADE || rule === GRAD.TO_GREY && k <= 0) return "Fades to paper over " + over;
  if (rule === GRAD.TO_GREY) return "Shifts to " + (k >= GRAD.LEVELS - 1 ? "black" : gradGreyShown(k) + " grey") + " over " + over;
  return "Cycles through its greys every " + over;
}
const plainWordsGd = plainWords;
plainWords = function (B) {
  const said = gradWords(B), text = plainWordsGd(B);
  if (!said) return text;
  const all = text.split(/(?<=\.)\s+/).filter(Boolean), shifts = shiftWords(B);
  let i = shifts ? all.indexOf(shifts) + 1 : 0;
  if (i <= 0) { i = 1; while (i < all.length && SHIFT_AFTER.has(all[i])) i++; }
  all.splice(i, 0, said);
  let t2 = all[0];
  for (let j = 1; j < all.length; j++) { if (j >= 2 && t2.length + 1 + all[j].length > 260) break; t2 += " " + all[j]; }
  return t2;
};

// ---------- checks ----------
function gradChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 700, H = 100, Y = 50, X0 = 20, L = 2 * GRAD.PX_CM, P = PAPER_TONE;
  const mk = (hand, medium) => normBrush({ name: "graded", seed: 17, style: "Fineliner", tip: freshPart("tip", "round"), medium: freshPart("medium", medium || "ink"), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: Object.assign(freshPart("hand", "steady"), { d: Object.assign(freshPart("hand", "steady").d, { glen: 2 }, hand || {}) }) });
  const line = () => { const n = Math.ceil((640 - X0) / 3); return Array.from({ length: n + 1 }, (_, k) => ({ x: X0 + 3 * k, y: Y, p: .65, t: 1000 + 2 * k })); };
  const draw = (B, shade) => { const s = modSurface(W, H, 7); layStroke(s, B, line(), 12, shade, null); return s; };
  const tone = (s, at) => { const x = Math.round(X0 + at); let sum = 0; for (let dx = -4; dx <= 4; dx++) for (let dy = -2; dy <= 2; dy++) sum += s.page[(Y + dy) * W + x + dx]; return sum / 45; };
  // Codes: the hand's last dials, after Grey Shift's; off, nothing.
  { const B = mk({ grad: 2, gto: 9, glen: 7 }), c = encodeM(B), back = readAnyCode(c).b, hand = encodeM(mk({ glen: 10 })).split(".")[1].split("_")[5];
    t("a code with a Gradient (" + c + ") reads back, its End Grey and Length too; off, the hand writes as before (" + hand + ")",
      encodeM(back) === c && gradRule(back) === 2 && gradEnd(back) === 9 && gradCm(back) === 7 && hand.length === 22 && HAND_DIALS.slice(HAND_DIALS.indexOf("grad"), HAND_DIALS.indexOf("grad") + 3).join(" ") === "grad gto glen"
      && encodeM(mk({ grad: 3, glen: 10 })).split(".")[1].split("_")[5].length === 22 + 7 + 3 + 9 + 1); }
  // The greys along the stroke.
  { const f = gradientOf(mk({ grad: 1 }), .8), g = gradientOf(mk({ grad: 2, gto: 15 }), .2), c = gradientOf(mk({ grad: 3 }), SHIFT.GREYS[1]);
    t("Fade reaches paper at its Length; To Grey ends at its End Grey, laid at the darker; Cycle visits the toolbar's greys in order, round and round",
      f.darkness(0) === .8 && Math.abs(f.darkness(L / 2) - .4) < 1e-9 && f.darkness(L) === 0 && f.darkness(3 * L) === 0 && g.ref === 1 && g.darkness(2 * L) === 1
      && [2, 3, 0, 1, 2].every((k, m) => Math.abs(c.darkness((m + 1) * L) - SHIFT.GREYS[k]) < 1e-9) && gradientOf(mk(), 1) === null); }
  // Drawn: Fade lightens to paper; To Grey ends at a plain stroke of its grey; each stroke starts afresh.
  { const s = draw(mk({ grad: 1 }), 1), black = draw(mk(), 1), d = draw(mk({ grad: 2, gto: 5 }), 1), third = draw(mk(), 1 / 3), again = draw(mk({ grad: 1 }), 1);
    t("drawn: Fade from black (" + tone(s, 6).toFixed(0) + ") to paper past its Length (" + tone(s, L + 20).toFixed(0) + "); To Grey ends at its grey ("
      + tone(d, L + 100).toFixed(0) + " against " + tone(third, L + 100).toFixed(0) + "); a second stroke starts black again",
      Math.abs(tone(s, 6) - tone(black, 6)) < 12 && tone(s, L / 2) > tone(s, 6) + 40 && tone(s, L + 20) >= P - .5 && Math.abs(tone(d, L + 100) - tone(third, L + 100)) < 4
      && tone(again, 6) === tone(s, 6)); }
  // Media: ink, wash, charcoal, graphite and crayon take it; Opaque, Resist and Screentone don't.
  { const takes = ["ink", "wash", "charcoal", "graphite", "crayon"].every(m => { const s = draw(mk({ grad: 1 }, m), 1); return gradTakes(mk({}, m)) && tone(s, L + 30) >= P - 1 && tone(s, 8) < tone(s, L * .6) - 10; });
    t("ink, wash, charcoal, graphite and crayon fade along the stroke; opaque, resist and screentone take none (no rows, no words)",
      takes && ["cover", "resist", "tone"].every(m => !gradTakes(mk({ grad: 1 }, m)) && !gradientOf(mk({ grad: 1 }, m), 1) && !gradRows(mk({ grad: 1 }, m)).length && !gradWords(mk({ grad: 1 }, m)))); }
  // Words, rows, never rolled.
  { const roll = () => { for (let i = 0; i < 1400; i++) if (gradRule(rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]))) return false; return true; };
    t("words: \"" + gradWords(mk({ grad: 2 })) + "\", after Grey Shift's; its rows; never rolled",
      gradWords(mk({ grad: 1 })) === "Fades to paper over 2 cm." && gradWords(mk({ grad: 2 })) === "Shifts to 33% grey over 2 cm." && gradWords(mk({ grad: 2, gto: 15 })) === "Shifts to black over 2 cm."
      && gradWords(mk({ grad: 3 })) === "Cycles through its greys every 2 cm." && plainWords(mk({ grad: 1, shift: 1 })).includes(SHIFT.WORDS.random + " Fades to paper over 2 cm.")
      && gradRows(mk({ grad: 3 })).join() === "grad,glen,sgreys,set" && gradRows(mk({ grad: 3, shift: 1 })).join() === "grad,glen" && gradRows(mk()).join() === "grad"
      && dialShown(mk({ grad: 2 }), "hand", "gto") && !dialShown(mk({ grad: 1 }), "hand", "gto") && dialShown(mk({ grad: 3 }), "hand", "sgreys") && roll()); }
  return out;
}
const selfTestGd = selfTest;
selfTest = function () { const r = selfTestGd(); r.grad = gradChecks(); r.ok = r.ok && r.grad.every(c => c.ok); NM.lastGrad = r.grad; return r; };
if (typeof runChecks === "function") {
  const runChecksGd = runChecks;
  runChecks = function () {
    runChecksGd();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastGrad) return;
      out.textContent += "\n\nGradient:\n" + NM.lastGrad.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 220);
  };
}

// ===================================================================================================================
// The owner's eight photo papers (2026-10-22): Paper choices 6 to 13 (the app's PartChoice COTTON to FINE_SPECK), so
// older codes never hold them; their scans are TEXTURE_SRC's, by key. No roll picks one and a variation never swaps to
// one (the app's ModularFeatures.seedOnly): chosen in the lab, kept by codes.
// ===================================================================================================================
const PHOTO_PAPERS = [["watercolour", "Cotton", "cotton paper"], ["rough", "Bumpy", "bumpy paper"], ["unryu", "Unryu", "unryu paper"],
  ["library", "Library", "library paper"], ["kraft", "Kraft", "kraft paper"], ["old buff", "Vintage", "vintage paper"],
  ["blue fibre", "Craft", "craft paper"], ["fine speck", "Fine", "fine paper"]];
for (const [k, label, words] of PHOTO_PAPERS) {
  CHOICES.paper.push({ k, label, dials: ["tooth"], macro: "tooth", words: ["smooth", "rough"], def: { tooth: .5 } });
  PAPER_WORDS[k] = words;
}
const makeVarsPp = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.paper.slice(), photo = new Set(PHOTO_PAPERS.map(p => p[0]));
  CHOICES.paper = keep.filter(c => !photo.has(c.k) || c.k === B.paper.k);
  try { return makeVarsPp(B, locks); } finally { CHOICES.paper = keep; }
};

// ===================================================================================================================
// Tilt Shading (the owner, 2026-10-22, of the pencils: "let's use the tilt so when the pencil is tilted it shades like
// an angled pencil"; the app's PartDial.SIDE_SHADE): how far a pen laid low lays the side of its nib, a wider, lighter,
// softer-edged line (graphite's catching only the paper's tooth), held upright a crisp line as ever (engine.js
// sideShade, stampLine). The hand's last dial, after Gradient's; written only when on. Shown for a nib's line of an
// ink-layer medium (Round, Chisel, Flat, Filbert). Never rolled.
// ===================================================================================================================
Object.assign(DIALS.hand, { tshade: { label: "Tilt Shading", min: 0, max: 1, def: 0 } });
HAND_DIALS.push("tshade");
ADDED.add("hand.tshade");
KEEPS.hand.push("tshade");
const tiltShades = B => STAY.inks.has(B.medium.k) && ["round", "chisel", "flat", "filbert"].includes(B.tip.k);
const dialShownTs = dialShown;
dialShown = function (B, part, key) { if (part === "hand" && key === "tshade") return tiltShades(B); return dialShownTs(B, part, key); };
function tiltShadeChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 400, H = 120, Y = 60;
  const mk = side => normBrush({ name: "shaded", seed: 17, style: "Pencil", tip: freshPart("tip", "round"), medium: freshPart("medium", "graphite"), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: Object.assign(freshPart("hand", "steady"), { d: Object.assign(freshPart("hand", "steady").d, side ? { tshade: side } : {}) }) });
  const draw = (B, deg) => { const s = modSurface(W, H, 7), n = 110; layStroke(s, B, Array.from({ length: n + 1 }, (_, k) => ({ x: 40 + 3 * k, y: Y, p: .6, tx: 0, ty: deg, t: 1000 + 2 * k })), 4, 1, null); return s; };
  const across = s => { let rows = 0; for (let y = 0; y < H; y++) { let any = false; for (let x = 190; x < 210; x++) if (s.page[y * W + x] < PAPER_TONE - 8) any = true; if (any) rows++; } return rows; };
  const ink = s => { let sum = 0, n = 0; for (let y = 0; y < H; y++) for (let x = 150; x < 250; x++) { const v = PAPER_TONE - s.page[y * W + x]; if (v > 8) { sum += v; n++; } } return n ? sum / n : 0; };
  // Codes: the hand's last dial; off, the hand writes as before.
  { const c = encodeM(mk(1)), back = readAnyCode(c).b, hand = encodeM(mk(0)).split(".")[1].split("_")[5];
    t("a code with Tilt Shading (" + c + ") reads back; off, the hand writes as before (" + hand + ")",
      encodeM(back) === c && back.hand.d.tshade === 1 && hand.length === 22 && HAND_DIALS[HAND_DIALS.length - 1] === "tshade"); }
  // Drawn: upright and at 30 degrees exactly as without it; at 70, wider and lighter.
  { const off = draw(mk(0), 70), up = draw(mk(1), 0), up0 = draw(mk(0), 0), at30 = draw(mk(1), 30), low = draw(mk(1), 70);
    t("drawn: upright and at 30 degrees as without it (" + across(up) + " rows); laid at 70, " + across(low) + " rows wide and lighter (" + ink(low).toFixed(0) + " against " + ink(up).toFixed(0) + ")",
      up.page.every((v, i) => v === up0.page[i]) && at30.page.every((v, i) => v === up0.page[i]) && off.page.every((v, i) => v === up0.page[i])
      && across(low) >= 3 * across(up) && ink(low) < ink(up)); }
  t("shown for a nib's line of graphite, ink or charcoal; not a wash's or a hatch", tiltShades(mk(1)) && dialShown(mk(1), "hand", "tshade")
    && !tiltShades(Object.assign(mk(1), { medium: freshPart("medium", "wash") })) && !tiltShades(Object.assign(mk(1), { tip: freshPart("tip", "hatch") })));
  return out;
}
const selfTestTs = selfTest;
selfTest = function () { const r = selfTestTs(); r.tshade = tiltShadeChecks(); r.ok = r.ok && r.tshade.every(c => c.ok); NM.lastTshade = r.tshade; return r; };
if (typeof runChecks === "function") {
  const runChecksTs = runChecks;
  runChecks = function () {
    runChecksTs();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastTshade) return;
      out.textContent += "\n\nTilt Shading:\n" + NM.lastTshade.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 240);
  };
}

// ===================================================================================================================
// The app's catch-up (2026-10-27): what the app's ModularCode, PartChoice and PartDial have that this port didn't, so
// every share code the app writes reads here and writes back the same.
//   Gravity     Quick's Runs Downhill, Pools Downhill and Grainy; Slow's Runs, Drips, Pools, Grainy and Rock to Blend
//               (the Gravity Lab's). They move wet paint on a tilted page, which this page has none of: hidden, as the
//               hand's page tilt is, kept so codes line up. Written only when on; never rolled here.
//   Wet         Drying's eighth choice (the wet paint spec): Stays Wet and Runs Downhill. The app lays it into its wet
//               canvas, which mixes, evens out and stays wet across strokes; here it lays as Instant, as the app does
//               wherever it has no wet canvas (its samples, Copy Strokes' copies). Never rolled or swapped to.
//   Hand Hatch  cut from the app (2026-10-24): Tip choice 12 still reads, as Hatch with the dials the two share.
// ===================================================================================================================
Object.assign(DIALS.life, {
  runs: { label: "Runs Downhill", min: 0, max: 1, def: 0, hide: true }, drips: { label: "Drips", min: 0, max: 1, def: 0, hide: true },
  pool: { label: "Pools Downhill", min: 0, max: 1, def: 0, hide: true }, sink: { label: "Grainy", min: 0, max: 1, def: 0, hide: true },
  blend: { label: "Rock to Blend", min: 0, max: 1, def: 0, hide: true },
  stays: { label: "Stays Wet", min: 0, max: 1, def: .5 }
});
choiceOf("life", "wet").dials.push("runs", "pool", "sink");
choiceOf("life", "dry").dials.push("runs", "drips", "pool", "sink", "blend");
CHOICES.life.push({ k: "wetpaint", label: "Wet", dials: ["stays", "runs"], macro: "stays", words: ["short", "long"],
  blurb: "Stays wet across strokes and mixes into the wet paint under it. In the app; drawn here as Instant." });
for (const k of ["runs", "drips", "pool", "sink", "blend", "stays"]) ADDED.add("life." + k);
KEEPS.life.push("runs", "pool", "sink");
const resolveWetPaint = resolve;
resolve = function (B, size, shade) {
  if (B.life.k !== "wetpaint") return resolveWetPaint(B, size, shade);
  const A = cloneBrush(B);
  A.life = freshPart("life", "none");
  return resolveWetPaint(A, size, shade);
};
const makeVarsWet = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.life.slice();
  CHOICES.life = keep.filter(c => c.k !== "wetpaint" || c.k === B.life.k);
  try { return makeVarsWet(B, locks); } finally { CHOICES.life = keep; }
};
// Hand Hatch: read with its own dials (the app's PartChoice.HAND_HATCH), then Hatch keeps the ones the two share
// (ModularCode.asHatch). Never in CHOICES, so nothing offers it.
Object.assign(DIALS.tip, {
  hlen: { label: "Line Length", min: 0, max: 1, def: .5, hide: true }, hwob: { label: "Wobble", min: 0, max: 1, def: .5, hide: true },
  hends: { label: "Taper Out", min: 0, max: 1, def: .7, hide: true }
});
const HAND_HATCH = { k: "handhatch", label: "Hand Hatch", dials: ["pitch", "lineW", "hangle", "headTurn", "hlen", "hwob", "hends", "weight", "flick"], def: { headTurn: 1 } }, HAND_HATCH_AT = 12;
const readPartHH = readPart;
readPart = function (part, seg) {
  if (part !== "tip" || !seg || parseInt(seg[0], 36) !== HAND_HATCH_AT || CHOICES.tip.length !== HAND_HATCH_AT) return readPartHH(part, seg);
  CHOICES.tip.push(HAND_HATCH);
  let P;
  try { P = readPartHH(part, seg); } finally { CHOICES.tip.length = HAND_HATCH_AT; }
  if (!P) return null;
  const H = freshPart("tip", "hatch");
  for (const key of choiceOf("tip", "hatch").dials) if (HAND_HATCH.dials.includes(key)) H.d[key] = P.d[key];
  return H;
};

;
// ===================================================================================================================
// A brush in friendly words (the app's BrushVoice, the owner's 2026-10-22 brief: "explain what makes the brush unique,
// what to expect", "complete sentences", "human sounding", "don't use semicolons"). plainWords keeps the facts, short and
// checked; brushWords says them as a person would, in complete sentences, what matters most first, at most four. The
// rows of VOICE_RULES are BrushVoice.RULES', row for row (VoiceChecks.java checks they stay the same). This brush and
// Brush Studio show brushWords.
// ===================================================================================================================
// Rank (lower first), kind (S a whole sentence, P what "It" does, two joined), the fact as a pattern, the words, a tag.
const VOICE_RULES = [
  ["1", "S", "Builds up where it lingers", "Dots pile up where you linger.", ""],
  ["1", "S", "Stamps a shape cut from the page", "It stamps a shape you cut from the page.", ""],
  ["1", "S", "Fades as it prints", "Each print comes out a little fainter.", ""],
  ["1", "S", "Builds up, resists washes", "The wax builds up in layers and pushes washes away.", ""],
  ["1", "S", "Builds up", "The wax builds up in layers.", ""],
  ["1", "S", "Scrapes back to white", "It scrapes back through to white paper.", ""],
  ["1", "S", "Loops along the line", "It draws toy loops along your line.", ""],
  ["1", "S", "Loops round a ring pulled out from the start", "It loops around a ring you pull out from where you start.", ""],
  ["1", "S", "Crosses where gone over or held", "Go back over a spot, or hold still, and the hatching crosses.", ""],
  ["1", "S", "Greys shift each stroke at random", "Each stroke picks a new grey at random.", ""],
  ["1", "S", "Greys shift each stroke, shuffled", "Each stroke takes the next grey from a shuffled set.", ""],
  ["1", "S", "Greys shift each stroke, there and back", "Each stroke steps to the next grey, then back again.", ""],
  ["1", "S", "Greys shift each stroke, in order", "Each stroke steps to the next grey in order.", ""],
  ["1", "S", "Fades to paper over (.+) cm", "It fades out to bare paper over $1 cm.", ""],
  ["1", "S", "Shifts to (.+) over (.+) cm", "It shifts to $1 over $2 cm.", ""],
  ["1", "S", "Cycles through its greys every (.+) cm", "It cycles through its greys every $1 cm.", ""],
  ["1", "S", "Flicks on a quick lift, flings drops on a hard stop", "Lift quickly and it flicks. Stop hard and it flings drops.", ""],
  ["1", "S", "Flicks on a quick lift", "Lift quickly and it flicks.", ""],
  ["1", "S", "Flings drops on a hard stop", "Stop hard and it flings drops.", ""],
  ["1", "S", "Nothing takes on it", "Nothing painted on top will stick to it.", ""],
  ["1", "S", "Nothing takes on it until rubbed off", "Nothing sticks to it until you rub it off with the eraser.", ""],
  ["1", "S", "Scrapes off in streaks", "The eraser scrapes it off in streaks.", ""],
  ["1", "S", "Goes behind marks", "It slips behind the marks already there.", ""],
  ["1", "S", "Inverts what's under it", "It flips the greys under it, dark to light.", ""],
  ["1", "S", "Taps drop clear rings, dragging combs them", "Tap to drop clear rings, then drag to comb them.", ""],
  ["1", "S", "Taps drop clear rings, dragging combs them a little", "Tap to drop clear rings, then drag to comb them a little.", ""],
  ["1", "S", "Floats (.+) s", "It floats on the water for $s.", ""],
  ["1", "S", "Taps drop rings, dragging combs them", "Tap to drop rings of ink, then drag to comb them.", ""],
  ["1", "S", "Taps drop rings", "Tap to drop rings of ink.", ""],
  ["1", "S", "Veins bridge nearby marks for (.+) s", "Mold veins grow out to bridge nearby marks for $s.", ""],
  ["1", "S", "Fades away in (.+) s", "It fades away after $s.", ""],
  ["1", "S", "Darkens in as it dries", "It goes on faint and darkens as it dries.", ""],
  ["1", "S", "Holds ink between strokes", "It holds its ink between strokes.", ""],
  ["1", "S", "Tap a grey to dip", "Tap a grey to dip it in fresh ink.", ""],
  ["1", "S", "Runs out of paint, then only smudges", "It runs out of paint as you go, then only smudges.", ""],
  ["1", "S", "Rub a grey to load", "Rub a grey to load it up again.", ""],
  ["1", "S", "Stays inside ink lines", "It stays inside your ink lines.", ""],
  ["1", "S", "Floods inside ink lines", "It floods up to your ink lines.", ""],
  ["1", "S", "Fills the shape it starts in", "It fills the whole shape it starts in.", ""],
  ["1", "S", "Fills the shape it outlines", "When you lift, it closes the outline and fills it in.", ""],
  ["2", "S", "Stays wet until dried", "It stays wet until you tap the drop to dry it.", ""],
  ["2", "S", "Merges while wet, blooms when damp", "Wet strokes mix together and bloom as they dry.", "bloom"],
  ["2", "S", "Merges while wet", "Wet strokes mix together.", ""],
  ["2", "S", "Wet for (.+) s", "It stays wet for $s.", ""],
  ["2", "S", "(runs|drips|pools|granulates|blends when rocked)( and (runs|drips|pools|granulates|blends when rocked))? when tilted", "@tilt", "tilt"],
  ["2", "S", "Lifts paint, lets it run", "It lifts paint and lets it run.", ""],
  ["2", "S", "Lifts paint", "It lifts paint off the page.", ""],
  ["2", "S", "Drags wet paint", "It drags wet paint along with it.", ""],
  ["2", "S", "Smears", "It smears whatever it passes over.", ""],
  ["2", "S", "Softens", "It softens marks without adding any paint.", ""],
  ["2", "S", "Combs", "It combs through wet paint.", ""],
  ["2", "S", "Keeps paint for the next stroke", "It keeps some paint for the next stroke.", ""],
  ["4", "P", "tapers to a point", "tapers to a fine point", ""],
  ["4", "P", "dry-brush texture", "has a scratchy, dry-brush texture", ""],
  ["4", "P", "dry-brush streaks", "leaves streaky brush marks", ""],
  ["4", "P", "slight wobble", "wobbles a little", ""],
  ["4", "P", "dark edges", "dries with dark edges", ""],
  ["4", "P", "pools when slow", "pools where you slow down", ""],
  ["4", "P", "spatters on flicks", "spatters when you flick", ""],
  ["4", "P", "blooms", "blooms as it dries", "bloom"],
  ["4", "P", "backruns", "leaves pale backruns", ""],
  ["4", "P", "bleeds", "bleeds softly into the paper", ""],
  ["4", "P", "raised ridges", "builds up raised ridges", ""],
  ["4", "P", "dusty broken edge", "has a dusty, broken edge", ""],
  ["4", "P", "soft speckled edge", "has a soft, speckled edge", ""],
  ["4", "P", "line shifted", "lands a little off the line", ""],
  ["4", "P", "broken line", "breaks up here and there", ""],
  ["4", "P", "opens into two lines", "opens into two lines", ""],
  ["4", "P", "splits into strands", "splits into strands", ""],
  ["4", "P", "runs dry quickly", "runs dry quickly", ""],
  ["4", "P", "runs dry on long strokes", "runs dry on long strokes", ""],
  ["4", "P", "picks up the texture", "catches the paper's grain", ""],
  ["4", "S", "sheds dust when tilted", "Tilt the page and dust falls off it.", ""],
  ["4", "P", "doubled", "prints a little doubled", ""],
  ["4", "P", "dots swell", "prints fat, swollen dots", ""],
  ["4", "P", "patchy ink", "prints with patchy ink", ""],
  ["5", "S", "Dries at once", "It dries as soon as you lift the pen.", ""],
  ["5", "S", "Permanent", "The eraser can't touch it.", ""],
  ["5", "S", "Part erasable", "The eraser only lightens it.", ""],
  ["5", "S", "Erasable", "", ""],
];
const VOICE_PATTERNS = VOICE_RULES.map(r => new RegExp("^" + r[2] + "$"));
const VOICE_MOST_SENTENCES = 4, VOICE_MOST = 230, VOICE_HAND = 3, VOICE_OPENER_MOST = 70, VOICE_TWO_SENTENCES = 150, VOICE_ABOUT_MOST = 260, VOICE_STEADY = "It stays the same however hard you press or how fast you go.";
const voiceLower1 = s => s ? s.charAt(0).toLowerCase() + s.slice(1) : s, voiceCap1 = s => s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
const voiceSentences = t => t.trim().split(/(?<=\.)\s+/).filter(Boolean);
const voiceStrip = s => s.endsWith(".") ? s.slice(0, -1) : s;
// "about 6 seconds", "1 second": a time the facts give in seconds, to the nearest whole one.
function voiceSeconds(s) { const n = Math.max(1, Math.round(parseFloat(s))); return (n < 10 ? "about " : "") + n + (n === 1 ? " second" : " seconds"); }
const VOICE_TILT = { runs: "runs", drips: "drips", pools: "pools", granulates: "settles into grain", "blends when rocked": "blends as you rock it" };
function voiceTilt(first, second) {
  const last = second || first, downhill = last === "runs" || last === "drips" || last === "pools";
  return "Tilt the page and it " + VOICE_TILT[first] + (second ? " and " + VOICE_TILT[second] : "") + (downhill ? " downhill" : "") + ".";
}
function voiceRule(fact) {
  const f = voiceLower1(fact);
  for (let i = 0; i < VOICE_RULES.length; i++) {
    const m = VOICE_PATTERNS[i].exec(f) || VOICE_PATTERNS[i].exec(fact);
    if (!m) continue;
    let words = VOICE_RULES[i][3];
    if (words === "@tilt") words = voiceTilt(m[1], m[3]);
    else if (words.includes("$s")) words = words.replace("$s", voiceSeconds(m[1]));
    else for (let g = m.length - 1; g >= 1; g--) words = words.split("$" + g).join(m[g] == null ? "" : m[g]);
    return [VOICE_RULES[i][0], words, VOICE_RULES[i][1], VOICE_RULES[i][4]];
  }
  return null;
}
const VOICE_MISSED = new Set();
const VOICE_NOUN = /^(.+?)(?:, (chisel tip|flat nib|hake|split nib|\d+-line rake|bristle brush|filbert|round tip))?,?(?: on (.+))?$/;
// What the facts' noun says it is, friendlier, with "a" or "an" where it takes one (BrushVoice.nounPhrase).
function voiceNounPhrase(noun) {
  let n = noun.replace(/ stipple$/, " stippling").replace(/ spiro$/, " spirograph").replace(/^(Vine|Compressed) charcoal$/, "stick of $1 charcoal");
  const grade = /^(.*?)(\S+) graphite$/.exec(n);
  if (grade) n = grade[1] + (/^\d*[bhfBHF]+$/.test(grade[2]) ? grade[2].toUpperCase() : grade[2]) + " pencil";
  const first = n.split(" ")[0];
  if (!(first === "Ben-Day" || first === first.toUpperCase())) n = voiceLower1(n);
  const counted = /(line|wash|stamp|spirograph|scratch|pencil|pastel|crayon)$|^stick of/.test(n) && !n.endsWith(" and wash");
  const an = "aeiou8".includes(n.charAt(0)) || /^[FHLMNRSX][A-Z0-9 ]/.test(n);
  return (!counted ? "" : an ? "an " : "a ") + n.replace("stick of Vine", "stick of vine").replace("stick of Compressed", "stick of compressed");
}
// The facts' first sentence as a sentence: "It's an ink line with a chisel tip, on felt paper."
function voiceNoun(fact) {
  const m = VOICE_NOUN.exec(fact);
  if (!m) { VOICE_MISSED.add(fact); return "It's " + voiceLower1(fact) + "."; }
  const tip = m[2] ? " with a " + m[2] : "", on = m[3] ? (tip ? "," : "") + " on " + m[3] : "";
  return "It's " + voiceNounPhrase(m[1]) + tip + on + ".";
}
// What stands out, for the opener, most telling first (BrushVoice.OPENERS, row for row): the fact, after "that", after a comma.
const VOICE_OPENERS = [
  ["Stamps a shape cut from the page", "stamps a shape you cut from the page", "stamping a shape you cut from the page"],
  ["Loops along the line", "spins out toy loops as you draw", "spinning out toy loops as you draw"],
  ["Loops round a ring pulled out from the start", "spins loops around a ring you pull out", "spinning loops around a ring you pull out"],
  ["Crosses where gone over or held", "crosshatches wherever you go back", "crosshatching wherever you go back"],
  ["Builds up, resists washes", "lays down waxy layers", "laying down waxy layers"],
  ["Builds up", "lays down waxy layers", "laying down waxy layers"],
  ["Scrapes back to white", "scratches back to white", "scratching back to white"],
  ["Builds up where it lingers", "piles up dots where you linger", "piling up dots where you linger"],
  ["Nothing takes on it(?: until rubbed off)?", "keeps washes off the paper", "keeping washes off the paper"],
  ["Goes behind marks", "slides under your lines", "sliding under your lines"],
  ["Inverts what's under it", "flips the greys under it", "flipping the greys under it"],
  ["Taps drop clear rings, dragging combs them(?: a little)?", "pushes floating ink into rings", "pushing floating ink into rings"],
  ["Floats (.+) s", "floats in rings", "floating in rings"],
  ["Veins bridge nearby marks for (.+) s", "grows little mold veins", "growing little mold veins"],
  ["Fades away in (.+) s", "slowly fades away", "slowly fading away"],
  ["Darkens in as it dries", "shows up as it dries", "showing up as it dries"],
  ["Holds ink between strokes", "keeps its ink from stroke to stroke", "keeping its ink from stroke to stroke"],
  ["Runs out of paint, then only smudges", "runs out of paint as you go", "running out of paint as you go"],
  ["Stays inside ink lines", "stays inside your lines", "staying inside your lines"],
  ["Floods inside ink lines", "floods up to your lines", "flooding up to your lines"],
  ["Fills the shape it starts in", "fills whole shapes", "filling whole shapes"],
  ["Fills the shape it outlines", "fills in the shapes you draw", "filling in the shapes you draw"],
  ["Flicks on a quick lift, flings drops on a hard stop", "flicks and flings when you're quick", "flicking and flinging when you're quick"],
  ["Flicks on a quick lift", "flicks when you lift fast", "flicking when you lift fast"],
  ["Flings drops on a hard stop", "flings drops when you stop hard", "flinging drops when you stop hard"],
  ["Greys shift each stroke.*", "changes grey every stroke", "changing grey every stroke"],
  ["(?:Fades to paper|Shifts to .+|Cycles through its greys) (?:over|every) .+ cm", "changes grey along the stroke", "changing grey along the stroke"],
  ["Stays wet until dried", "stays wet until you dry it", "staying wet until you dry it"],
  ["Wet for (.+) s", "stays wet for a while", "staying wet for a while"],
  ["sheds dust when tilted", "leaves dust in its wake", "leaving dust in its wake"],
  ["blooms", "blooms as it dries", "blooming as it dries"],
  ["backruns", "leaves pale backruns", "leaving pale backruns"],
  ["bleeds", "bleeds into the paper", "bleeding into the paper"],
  ["splits into strands", "splits into hairy strands", "splitting into hairy strands"],
  ["runs dry (?:quickly|on long strokes)", "runs dry into streaks", "running dry into streaks"],
  ["opens into two lines", "opens into two lines", "opening into two lines"],
  ["(?:runs|drips|pools)(?: and \\w+)? when tilted", "runs when you tilt the page", "running when you tilt the page"],
  ["(?:granulates|blends when rocked)(?: and .+)? when tilted", "moves when you tilt the page", "moving when you tilt the page"],
  ["Merges while wet(?:, blooms when damp)?", "mixes while it's wet", "mixing while it's wet"],
  ["Lifts paint, lets it run", "lifts paint and lets it run", "lifting paint and letting it run"],
  ["Lifts paint", "lifts paint off the page", "lifting paint off the page"],
  ["Drags wet paint", "drags wet paint along", "dragging wet paint along"],
  ["Softens", "softens what's there", "softening what's there"],
  ["Combs", "combs through wet paint", "combing through wet paint"],
  ["Smears", "smears what's there", "smearing what's there"],
  ["Keeps paint for the next stroke", "never quite comes clean", "never quite coming clean"],
  ["dots swell|doubled|patchy ink", "prints like old newsprint", "printing like old newsprint"],
  ["spatters on flicks", "spatters when flicked", "spattering when flicked"],
  ["pools when slow", "pools when you slow down", "pooling when you slow down"],
  ["raised ridges", "builds up raised ridges", "building up raised ridges"],
  ["dusty broken edge", "has a dusty, broken edge", "showing a dusty, broken edge"],
  ["tapers to a point", "tapers to a fine point", "tapering to a fine point"],
  ["dry-brush streaks", "leaves streaky marks", "leaving streaky marks"],
  ["dry-brush texture", "skips and scratches", "skipping and scratching"],
  ["picks up the texture", "catches the paper's grain", "catching the paper's grain"],
  ["soft speckled edge", "has a soft, speckled edge", "showing a soft, speckled edge"],
  ["dark edges", "dries with dark edges", "drying with dark edges"],
  ["slight wobble", "wobbles a little", "wobbling a little"],
  ["line shifted|broken line", "breaks up here and there", "breaking up here and there"],
];
const VOICE_OPENER_PATTERNS = VOICE_OPENERS.map(r => new RegExp("^(?:" + r[0] + ")$"));
const VOICE_HAND_START = /^(Pressure|Tilt|Speed|Downhill|Uphill)\b/;
// FNV-1a over the facts' chars: a brush's own pick of the opener's shape (BrushVoice.hash).
function voiceHash(s) { let h = 0x811c9dc5; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
const voiceOpens = u => VOICE_OPENER_PATTERNS.findIndex(p => p.test(voiceLower1(u)) || p.test(u));
function voiceUnits(sentences) {
  const out = [];
  for (let i = 1; i < sentences.length; i++) {
    const s = voiceStrip(sentences[i]);
    if (VOICE_HAND_START.test(s)) continue;
    if (s.includes(", ") && voiceOpens(s) >= 0) { out.push(s); continue; }
    out.push(...s.split(", "));
  }
  return out;
}
function voiceShaped(shape, what, rows) {
  if (!rows.length) return shape === 0 ? voiceCap1(what) + ", plain and simple." : shape === 1 ? "Here's " + what + ", nothing fancy."
    : shape === 2 ? "This is " + what + ", no surprises." : "Meet " + what + ".";
  const rel = VOICE_OPENERS[rows[0]][1] + (rows.length > 1 ? " and " + VOICE_OPENERS[rows[1]][1] : "");
  const ing = VOICE_OPENERS[rows[0]][2] + (rows.length > 1 ? " and " + VOICE_OPENERS[rows[1]][2] : "");
  return shape === 0 ? voiceCap1(what) + " that " + rel + "." : shape === 1 ? "Here's " + what + " that " + rel + "."
    : shape === 2 ? "This is " + what + ", " + ing + "." : "Meet " + what + ", " + ing + ".";
}
// The opener: what it is and the one or two things that stand out most, in one of four shapes (BrushVoice.opener).
function voiceOpener(facts, used, most = VOICE_OPENER_MOST) {
  const sentences = voiceSentences(facts);
  if (!sentences.length) return "";
  const m = VOICE_NOUN.exec(voiceStrip(sentences[0]));
  const noun = m ? voiceNounPhrase(m[1]) : voiceLower1(voiceStrip(sentences[0]));
  const tip = m && m[2] ? " with a " + m[2] : "", on = m && m[3] ? " on " + m[3] : "";
  const units = voiceUnits(sentences), picked = [], rows = [];
  for (let i = 0; i < VOICE_OPENERS.length && rows.length < 2; i++)
    for (const u of units)
      if (!picked.includes(u) && (VOICE_OPENER_PATTERNS[i].test(voiceLower1(u)) || VOICE_OPENER_PATTERNS[i].test(u))) { picked.push(u); rows.push(i); break; }
  const shape = voiceHash(facts) % 4;
  const tries = [["2", on, tip], ["1", on, tip], ["1", "", tip], ["1", "", ""], ["0", on, tip], ["0", "", tip], ["0", "", ""]];
  for (let lead = 0; lead < 2; lead++)
    for (const t of tries) {
      const n = Math.min(+t[0], rows.length);
      if (n === 0 && rows.length && t[0] === "0" && lead === 0) continue;
      const s = voiceShaped(lead === 0 ? shape : 0, noun + t[2] + t[1], rows.slice(0, n));
      if (s.length <= most) { used.push(...picked.slice(0, n)); return s; }
    }
  return voiceCap1(noun) + ".";
}
// The facts without what the opener said (BrushVoice.without).
function voiceWithout(facts, used) {
  for (const u of used.slice()) if (voiceLower1(u) === "blooms") used.push("blooms when damp");
  const sentences = voiceSentences(facts); let out = "";
  for (let i = 0; i < sentences.length; i++) {
    let s = voiceStrip(sentences[i]);
    if (i > 0 && !VOICE_HAND_START.test(s)) {
      if (used.includes(s)) continue;
      const keep = s.split(", ").filter(p => !used.includes(p));
      if (!keep.length) continue;
      s = voiceCap1(keep.join(", "));
    }
    out += (out ? " " : "") + s + ".";
  }
  return out;
}
const VOICE_CAUSE = { pressure: "Pressing harder", tilt: "Tilting the pen", speed: "Moving faster", downhill: "Tilting the page", uphill: "Tilting the page" };
const VOICE_MAKES = {}, VOICE_SETS = { size: "the loops' size", loops: "how many loops there are", wheel: "the wheel", hole: "the hole in the middle", petals: "the petals", nothing: "nothing" };
for (const [one, two, made] of [["widens", "widen", "it wider"], ["darkens", "darken", "it darker"], ["lightens", "lighten", "it lighter"],
  ["thins", "thin", "it thinner"], ["thickens", "thicken", "it thicker"], ["broadens", "broaden", "it broader"],
  ["grows dots", "grow dots", "the dots bigger"], ["shrinks dots", "shrink dots", "the dots smaller"],
  ["widens nib", "widen nib", "the nib wider"], ["opens nib", "open nib", "the nib open"],
  ["opens tines", "open tines", "the tines spread"], ["spreads lines", "spread lines", "the lines spread"], ["turns nib", "turn nib", "the nib turn"]]) { VOICE_MAKES[one] = made; VOICE_MAKES[two] = made; }
function voiceMakes(effect, cause) {
  let made = VOICE_MAKES[effect];
  if (made == null) { VOICE_MISSED.add(effect); made = "it " + effect; }
  return made + (cause === "downhill" ? " downhill" : cause === "uphill" ? " uphill" : "");
}
function voiceSets(what) { let s = VOICE_SETS[what]; if (s == null) { VOICE_MISSED.add(what); s = "the " + what; } return s; }
function voiceClause(f) {
  const two = /^(\w+) and (\w+) (.+)$/.exec(f);
  if (two && VOICE_CAUSE[two[1]] && VOICE_CAUSE[two[2]]) {
    let both = voiceLower1(VOICE_CAUSE[two[1]]) + " and " + voiceLower1(VOICE_CAUSE[two[2]]);
    if (two[1] === "downhill" || two[2] === "downhill" || VOICE_CAUSE[two[1]] === VOICE_CAUSE[two[2]]) both = voiceLower1(VOICE_CAUSE[two[1]]);
    const what = two[3];
    if (what.startsWith("set ")) return both + " both change " + voiceSets(what.slice(4));
    return both + " both make " + voiceMakes(what, two[1]);
  }
  const space = f.indexOf(" "), cause = f.slice(0, space), what = f.slice(space + 1);
  const who = VOICE_CAUSE[cause] ? voiceLower1(VOICE_CAUSE[cause]) : cause;
  if (what.startsWith("sets ")) return who + " changes " + voiceSets(what.slice(5));
  const effects = what.split(" and ");
  let made = voiceMakes(effects[0], cause);
  if (effects.length > 1) {
    const second = voiceMakes(effects[1], cause);
    made = made.startsWith("it ") && second.startsWith("it ") ? made + " and " + second.slice(3)
      : made.replace(/^it /, "the line ") + " and " + second.replace(/^it /, "the line ");
  }
  return who + " makes " + made;
}
// Control's facts ("Pressure widens, speed thins") as a sentence: "Pressing harder makes it wider, and moving faster makes it thinner."
function voiceHand(fact) {
  const f = voiceLower1(fact), pair = f.split(", ");
  if (pair.length === 2) return voiceCap1(voiceClause(pair[0])) + ", and " + voiceClause(pair[1]) + ".";
  return voiceCap1(voiceClause(f)) + ".";
}
// The facts (plainWords) in friendly sentences, what matters most first; noun: start with what it is.
function sayWords(facts, noun = true) {
  const sentences = voiceSentences(facts), all = [];
  if (noun && sentences.length) all.push({ rank: 0, order: 0, words: voiceNoun(voiceStrip(sentences[0])), tag: "" });
  for (let i = 1; i < sentences.length; i++) {
    const s = voiceStrip(sentences[i]);
    if (VOICE_HAND_START.test(s)) { all.push({ rank: VOICE_HAND, order: all.length, words: voiceHand(s), tag: "" }); continue; }
    const whole = s.includes(", ") ? voiceRule(s) : null;
    if (whole) { if (whole[1]) all.push({ rank: +whole[0], order: all.length, words: whole[1], tag: whole[3] }); continue; }
    const does = [], doTags = []; let rank = 9;
    for (const part of s.split(", ")) {
      let r = voiceRule(part);
      if (!r) { VOICE_MISSED.add(part); r = ["4", "It " + voiceLower1(part) + ".", "S", ""]; }
      if (!r[1]) continue;
      if (r[2] === "P") { does.push(r[1]); doTags.push(r[3]); rank = Math.min(rank, +r[0]); }
      else all.push({ rank: +r[0], order: all.length, words: r[1], tag: r[3] });
    }
    if (does.length) all.push({ rank, order: all.length, words: null, tag: "", does, doTags });
  }
  // Nothing past what it is: Control is steady, the drying Quick, nothing unusual. Say so.
  if (all.length === (noun && sentences.length ? 1 : 0)) all.push({ rank: VOICE_HAND, order: all.length, words: VOICE_STEADY, tag: "" });
  const order = all.slice().sort((a, b) => a.rank !== b.rank ? a.rank - b.rank : a.order - b.order), tags = new Set();
  let out = "", count = 0;
  for (const s of order) {
    if (s.tag && tags.has(s.tag)) continue;
    if (s.tag) tags.add(s.tag);
    let words = s.words;
    if (s.does) {
      const left = s.does.filter((d, k) => { const g = s.doTags[k]; if (!g) return true; if (tags.has(g)) return false; tags.add(g); return true; });
      if (!left.length) continue;
      words = "It " + left.join(" and ") + ".";
    }
    const n = voiceSentences(words).length;
    if (count > 0 && (count + n > VOICE_MOST_SENTENCES || out.length + 1 + words.length > VOICE_MOST)) continue;
    out += (out ? " " : "") + words; count += n;
  }
  return out;
}
// The brush in words (BrushVoice.about): mine (a special tool's own sentences) or the opener, then how it behaves,
// leaving out what the opener said, while it fits.
function aboutWords(facts, mine = "") {
  const used = [];
  let lead = mine || voiceOpener(facts, used), behaves = sayWords(voiceWithout(facts, used), false);
  // The opener and the first of the rest within Brush Lab's two first sentences (150): a shorter opener where they aren't.
  const first = voiceSentences(behaves);
  if (!mine && first.length && lead.length + 1 + first[0].length > VOICE_TWO_SENTENCES) {
    used.length = 0;
    lead = voiceOpener(facts, used, VOICE_TWO_SENTENCES - 1 - first[0].length);
    behaves = sayWords(voiceWithout(facts, used), false);
  }
  let out = lead;
  for (const s of voiceSentences(behaves)) { if (out.length + 1 + s.length > VOICE_ABOUT_MOST) break; out += " " + s; }
  return out;
}
// This brush, as the app's Brush Lab and Tools page say it.
function brushWords(B) { return aboutWords(plainWords(B)); }

;
const DRAWN_TOOLS = [["Steady line","steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud"],["Ballpoint","ballpoint.M00at00005e003m_000zz0000000000077_0_177_0_85ei000000000000000nz0.2ez"],["Fountain pen","fountain-pen.M00i000at00003m_03mzzwe00000000000_0_000_0_8qz0000000000000000nz0.2f1"],["Split nib","split-nib.M24m0okjmi0000y00_000zz0000000000000_0_000_1jz_8lw00wk0000fq000000nz0.51j8ud"],["Fude pen","broad-nib.M20ss007700003m_000zzat00000000000_0_000_0_8at0000i000ll000000nz01.1s13pn4"],["Stipple pen","stipple-pen.M07i0clcli0_000zz0000000000000_0_000_1jz_0000000000000000000nz0.h6q"],["Engraving pen","engraving-pen.Me0wdatee0000ll_0p6000000000000000zz028bc0_0_000_0_3p6ll00000000000000nz0.2er"],["Felt marker","felt-marker.M31j2eei000000000at_000zzbq00920000000_0_300_1jz_84u0000000000400000nz0.jjds6x"],["Chisel marker","chisel-marker.M31j2eei000000000at_000zzdj005z0000000_0_000_1jz_82o00000000005u0000nz0.t4mf73"],["Highlighter","highlighter.M31e1zzp60000000000_1007700000000zz2_0_000_0_0000000000000000000nz0.2f2"],["Springy brush","springy-brush.M10ssati000003m_000zz0000000000000_0_000_0_4weatg7000000ee0000nz0.2fg"],["Swell brush","swell-brush.M10i0q6f40000at_000t7008h000000000_0_000_1jz_8wgft00dr0000ey0000nz0.1s13pn4"],["Flat hake","flat-hake.Mc3i01zz9a7e_1dxat00770000_0_1i0_1jz_88w0000000000000000nz0.1ifv2mx"],["Water brush","water-brush.M60i000000000at_1ulat00770000_4i0ll_000_2g7ll0jsee00at_0000000000000000000nz0.20k"],["Soft wash","soft-wash.M60i0000000007q_1wva200000000_0_0fk_7_8la0000000000000000nz0.t4mf73"],["Wet blooms","wet-blooms.M60i000000000ji_1z5gk00f6e400_0_000_7js_8mx0000000000000000nz0.1s13pn4"],["Pooling wash","pooling-wash.M60i000000000g7_1x3at00770000_0_190_7qzll_8g70000000000000000nz0.373k3"],["Masking fluid","masking-fluid.M60i000000000at_5_0_000_1jz_0000000000000000000nz0.px"],["Wax resist","wax-resist.M60i00000cl00at_500_0_1i0_1jz_0000000000000000000nz0.py"],["HB pencil","hb-pencil.M40i000000000at_4e551_0_1v5_1jz_8arsr00000000000000nz000000000530000005000c099zz.pg5zxt"],["Soft charcoal","soft-charcoal.M50i00000000yat_3eqlj_0_1nl_1jz_8pthh00000000000000nz000000000530000005000c099zz.51j8ud"],["Soft pastel","soft-pastel.Ma0i000007s00at_3b98o_0_3nj_1jz_8hg0000000000000000nz000000000530000005000c099zz.51j8ud"],["Wax crayon","wax-crayon.Ma0p600000000at_7ll0_0_1i0_0_0000000000000000000nz0.2f9"],["Loaded oils","loaded-oils.M99vleei0000000005e_2zz00_2llp6zz00js_000_7ne_1ss0000000000000000nz0.2fu"],["Spirograph","spirograph.M0bi010gj08huu801i08r_000zz0000000000000_0_000_0_0000000000000000000nz0.2fb"],["Stamp","stamp.M0agx1r0qe_000zz0000000000000_0_000_0_8cl0000000000ee0000nz0.2gd"],["Fill in","fill-in.M30wd00000000at_1atat00000000_5zz_000_0_0000000000000000000nz0.2fh"],["Filled shape","filled-shape.M30wd00000000at_1atat00000000_6_000_0_0000000000000000000nz0.2fi"],["Dot tone","dot-tone.Me0i00000000000_60e0_0_000_0_0000000000000000000nz0.2e5"],["Ben-Day brush","ben-day-brush.Me1zzeezz00000000ll_0i0000000000000000zz065ji000at_0_000_0_25ess00000000000000nz0.2ef"],["Lemon juice","lemon-juice.M20i000at0000at_000zz0000000000000_0_000_6hp_1ss0000000000000000nz0.2ff"],["Vanishing ink","vanishing-ink.M00i000000000at_000zz0000000000000_0_000_53a_0000000000000000000nz0.2fe"]];
;
// ---------- image tools for Match and texture import: plain arrays, no canvas ----------
function grayOf(img) {
  const { width: w, height: h, data } = img, g = new Float32Array(w * h);
  for (let i = 0; i < g.length; i++) g[i] = (data[4 * i] * .299 + data[4 * i + 1] * .587 + data[4 * i + 2] * .114) / 255;
  return { w, h, g };
}
function bilinear(a, w, h, x, y) {
  x = x < 0 ? 0 : x > w - 1 ? w - 1 : x; y = y < 0 ? 0 : y > h - 1 ? h - 1 : y;
  const x0 = x | 0, y0 = y | 0, x1 = x0 + 1 < w ? x0 + 1 : x0, y1 = y0 + 1 < h ? y0 + 1 : y0, tx = x - x0, ty = y - y0;
  return (a[y0 * w + x0] * (1 - tx) + a[y0 * w + x1] * tx) * (1 - ty) + (a[y1 * w + x0] * (1 - tx) + a[y1 * w + x1] * tx) * ty;
}
// Resamples to W x H: averages first when shrinking, so nothing aliases.
function resample(src, w, h, W, H) {
  const sx = w / W, sy = h / H, r = Math.floor(Math.max(sx, sy) / 2);
  const a = r >= 1 ? blur(src, w, h, r) : src, out = new Float32Array(W * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) out[y * W + x] = bilinear(a, w, h, (x + .5) * sx - .5, (y + .5) * sy - .5);
  return out;
}
function cropOf(src, w, h, x0, y0, cw, ch) {
  const out = new Float32Array(cw * ch);
  for (let y = 0; y < ch; y++) out.set(src.subarray((y + y0) * w + x0, (y + y0) * w + x0 + cw), y * cw);
  return out;
}
// Running max or min over a (2r+1) window, rows then columns.
function slideExt(src, w, h, r, max) {
  const tmp = new Float32Array(w * h), out = new Float32Array(w * h), q = new Int32Array(Math.max(w, h) + 1);
  const line = (get, set, n) => {
    let head = 0, tail = 0;
    for (let j = 0; j < n + r; j++) {
      if (j < n) { const v = get(j); while (tail > head && (max ? get(q[tail - 1]) <= v : get(q[tail - 1]) >= v)) tail--; q[tail++] = j; }
      const i = j - r;
      if (i >= 0) { while (q[head] < i - r) head++; set(i, get(q[head])); }
    }
  };
  for (let y = 0; y < h; y++) line(j => src[y * w + j], (i, v) => { tmp[y * w + i] = v; }, w);
  for (let x = 0; x < w; x++) line(j => tmp[j * w + x], (i, v) => { out[i * w + x] = v; }, h);
  return out;
}
// Darkness with the lighting taken out. The paper's brightness is a smooth surface (a cubic in x and y) fitted to
// the image with the dark pixels (the strokes) left out, round by round, then nudged by the paper's own local
// average where the light isn't so smooth. Dividing by it turns a shadow or a gradient across the page into paper.
function flatten(g, w, h, radius) {
  const f = Math.max(1, Math.round(Math.min(radius, Math.max(w, h) / 24) / 4)), lw = Math.ceil(w / f), lh = Math.ceil(h / f);
  const low = new Float32Array(lw * lh);
  for (let y = 0; y < lh; y++) for (let x = 0; x < lw; x++) {
    let s = 0, n = 0;
    for (let yy = y * f; yy < Math.min(h, y * f + f); yy++) for (let xx = x * f; xx < Math.min(w, x * f + f); xx++) { s += g[yy * w + xx]; n++; }
    low[y * lw + x] = s / n;
  }
  // Block centres, in -1..1.
  const cx = x => (Math.min(w, x * f + f) + x * f) / w - 1, cy = y => (Math.min(h, y * f + f) + y * f) / h - 1;
  const basis = (x, y) => [1, x, y, x * x, x * y, y * y, x * x * x, x * x * y, x * y * y, y * y * y];
  const B = [];
  for (let y = 0; y < lh; y++) for (let x = 0; x < lw; x++) B.push(basis(cx(x), cy(y)));
  const wt = new Float32Array(low.length).fill(1), fit = new Float32Array(low.length);
  let coef = null;
  for (let round = 0; round < 8; round++) {
    const A = Array.from({ length: 10 }, () => new Float64Array(11));
    for (let i = 0; i < low.length; i++) {
      if (!wt[i]) continue;
      const b = B[i];
      for (let r = 0; r < 10; r++) { for (let c = 0; c < 10; c++) A[r][c] += b[r] * b[c]; A[r][10] += b[r] * low[i]; }
    }
    for (let r = 0; r < 10; r++) A[r][r] += 1e-6;
    coef = solveLinear(A) || coef;
    if (!coef) break;
    const res = [];
    for (let i = 0; i < low.length; i++) { let v = 0; for (let k = 0; k < 10; k++) v += coef[k] * B[i][k]; fit[i] = v; if (wt[i]) res.push(low[i] - v); }
    const med = median(res), mad = 1.4826 * median(res.map(e => Math.abs(e - med))) + 1e-4;
    let changed = 0;
    for (let i = 0; i < low.length; i++) { const e = low[i] - fit[i], keep = e > med - 2.5 * mad && e < med + 4 * mad ? 1 : 0; if (keep !== wt[i]) changed++; wt[i] = keep; }
    if (!changed) break;
  }
  // The paper's own departures from the surface, averaged nearby where there's paper to average.
  const R2 = Math.max(1, Math.round(Math.max(radius, 8) / f / 3)), num = new Float32Array(low.length);
  for (let i = 0; i < low.length; i++) num[i] = wt[i] * (low[i] - fit[i]);
  const ns = blur(blur(num, lw, lh, R2), lw, lh, R2), ds = blur(blur(wt, lw, lh, R2), lw, lh, R2), bgl = new Float32Array(low.length);
  for (let i = 0; i < low.length; i++) bgl[i] = Math.max(.02, fit[i] + (ds[i] > .05 ? ns[i] / ds[i] * clamp(ds[i] * 2) : 0));
  const d = new Float32Array(w * h), bg = new Float32Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const b = Math.max(.03, bilinear(bgl, lw, lh, (x + .5) / f - .5, (y + .5) / f - .5)), i = y * w + x;
    bg[i] = b; d[i] = clamp(1 - g[i] / b, -.5, 1);
  }
  return { d, bg };
}
// Gaussian elimination with partial pivoting on an augmented n x (n+1) matrix; null if singular.
function solveLinear(A) {
  const n = A.length;
  for (let c = 0; c < n; c++) {
    let p = c; for (let r = c + 1; r < n; r++) if (Math.abs(A[r][c]) > Math.abs(A[p][c])) p = r;
    if (Math.abs(A[p][c]) < 1e-12) return null;
    [A[c], A[p]] = [A[p], A[c]];
    for (let r = c + 1; r < n; r++) { const k = A[r][c] / A[c][c]; for (let j = c; j <= n; j++) A[r][j] -= k * A[c][j]; }
  }
  const x = new Float64Array(n);
  for (let r = n - 1; r >= 0; r--) { let s = A[r][n]; for (let j = r + 1; j < n; j++) s -= A[r][j] * x[j]; x[r] = s / A[r][r]; }
  return x;
}
function otsu(a, idx) {
  const hist = new Float64Array(256); let n = 0;
  const add = v => { hist[Math.min(255, Math.max(0, (v * 255) | 0))]++; n++; };
  if (idx) for (const i of idx) add(a[i]); else for (let i = 0; i < a.length; i++) add(a[i]);
  let sum = 0; for (let i = 0; i < 256; i++) sum += i * hist[i];
  let sb = 0, wb = 0, best = 0, t = 0;
  for (let i = 0; i < 256; i++) {
    wb += hist[i]; if (!wb) continue;
    const wf = n - wb; if (!wf) break;
    sb += i * hist[i];
    const mb = sb / wb, mf = (sum - sb) / wf, v = wb * wf * (mb - mf) * (mb - mf);
    if (v > best) { best = v; t = i; }
  }
  return (t + .5) / 255;
}
// 8-connected components of a mask: labels (0 none) and each one's area and box.
function components(mask, w, h) {
  const labels = new Int32Array(w * h), stack = new Int32Array(w * h), comps = [null];
  for (let s = 0; s < w * h; s++) {
    if (!mask[s] || labels[s]) continue;
    const id = comps.length, c = { id, area: 0, x0: w, y0: h, x1: 0, y1: 0 };
    let top = 0; stack[top++] = s; labels[s] = id;
    while (top) {
      const p = stack[--top], x = p % w, y = (p / w) | 0;
      c.area++; if (x < c.x0) c.x0 = x; if (x > c.x1) c.x1 = x; if (y < c.y0) c.y0 = y; if (y > c.y1) c.y1 = y;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const X = x + dx, Y = y + dy;
        if (X < 0 || Y < 0 || X >= w || Y >= h) continue;
        const q = Y * w + X;
        if (mask[q] && !labels[q]) { labels[q] = id; stack[top++] = q; }
      }
    }
    comps.push(c);
  }
  return { labels, comps };
}
// Euclidean distance from each set pixel to the nearest unset one (Felzenszwalb and Huttenlocher); unset pixels are 0.
function edt(mask, w, h) {
  const INF = 1e12, f = new Float64Array(Math.max(w, h)), d = new Float64Array(Math.max(w, h)), v = new Int32Array(Math.max(w, h)), z = new Float64Array(Math.max(w, h) + 1);
  const out = new Float64Array(w * h);
  for (let i = 0; i < w * h; i++) out[i] = mask[i] ? INF : 0;
  const pass = (n, get, set) => {
    for (let q = 0; q < n; q++) f[q] = get(q);
    let k = 0; v[0] = 0; z[0] = -INF; z[1] = INF;
    for (let q = 1; q < n; q++) {
      let s;
      while (true) { const p = v[k]; s = ((f[q] + q * q) - (f[p] + p * p)) / (2 * q - 2 * p); if (s <= z[k] && k > 0) k--; else break; }
      if (s <= z[k]) { v[0] = q; z[0] = -INF; z[1] = INF; k = 0; continue; }
      k++; v[k] = q; z[k] = s; z[k + 1] = INF;
    }
    k = 0;
    for (let q = 0; q < n; q++) { while (z[k + 1] < q) k++; const p = v[k]; d[q] = (q - p) * (q - p) + f[p]; }
    for (let q = 0; q < n; q++) set(q, d[q]);
  };
  for (let x = 0; x < w; x++) pass(h, q => out[q * w + x], (q, val) => { out[q * w + x] = val; });
  for (let y = 0; y < h; y++) pass(w, q => out[y * w + q], (q, val) => { out[y * w + q] = val; });
  const res = new Float32Array(w * h);
  for (let i = 0; i < w * h; i++) res[i] = Math.sqrt(Math.min(out[i], 1e10));
  return res;
}
const notMask = m => { const o = new Uint8Array(m.length); for (let i = 0; i < m.length; i++) o[i] = m[i] ? 0 : 1; return o; };
function dilate(mask, w, h, r) { const dt = edt(notMask(mask), w, h), o = new Uint8Array(w * h); for (let i = 0; i < o.length; i++) o[i] = dt[i] <= r ? 1 : 0; return o; }
function erode(mask, w, h, r) { const dt = edt(mask, w, h), o = new Uint8Array(w * h); for (let i = 0; i < o.length; i++) o[i] = dt[i] > r ? 1 : 0; return o; }
const closing = (m, w, h, r) => erode(dilate(m, w, h, r), w, h, r);
// Zhang-Suen thinning to a one-pixel skeleton.
function thin(mask, w, h) {
  const m = Uint8Array.from(mask), del = [];
  let x0 = w, y0 = h, x1 = 0, y1 = 0;
  for (let i = 0; i < m.length; i++) if (m[i]) { const x = i % w, y = (i / w) | 0; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
  x0 = Math.max(1, x0); y0 = Math.max(1, y0); x1 = Math.min(w - 2, x1); y1 = Math.min(h - 2, y1);
  for (let changed = true; changed;) {
    changed = false;
    for (let sub = 0; sub < 2; sub++) {
      del.length = 0;
      for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
        const i = y * w + x; if (!m[i]) continue;
        const p2 = m[i - w], p3 = m[i - w + 1], p4 = m[i + 1], p5 = m[i + w + 1], p6 = m[i + w], p7 = m[i + w - 1], p8 = m[i - 1], p9 = m[i - w - 1];
        const B = p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9;
        if (B < 2 || B > 6) continue;
        const A = (!p2 && p3) + (!p3 && p4) + (!p4 && p5) + (!p5 && p6) + (!p6 && p7) + (!p7 && p8) + (!p8 && p9) + (!p9 && p2);
        if (A !== 1) continue;
        if (sub === 0 ? (p2 && p4 && p6) || (p4 && p6 && p8) : (p2 && p4 && p8) || (p2 && p6 && p8)) continue;
        del.push(i);
      }
      if (del.length) { changed = true; for (const i of del) m[i] = 0; }
    }
  }
  return m;
}
// The longest shortest path through a skeleton: two breadth-first searches.
function longestPath(skel, w, h) {
  let start = -1, count = 0;
  for (let i = 0; i < skel.length; i++) if (skel[i]) { if (start < 0) start = i; count++; }
  if (start < 0) return [];
  const dist = new Int32Array(w * h), par = new Int32Array(w * h), queue = new Int32Array(count + 1);
  const bfs = s => {
    dist.fill(-1); let head = 0, tail = 0, far = s; queue[tail++] = s; dist[s] = 0; par[s] = -1;
    while (head < tail) {
      const p = queue[head++], x = p % w, y = (p / w) | 0;
      if (dist[p] > dist[far]) far = p;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const X = x + dx, Y = y + dy; if (X < 0 || Y < 0 || X >= w || Y >= h) continue;
        const q = Y * w + X; if (skel[q] && dist[q] < 0) { dist[q] = dist[p] + 1; par[q] = p; queue[tail++] = q; }
      }
    }
    return far;
  };
  // Start in the skeleton's largest piece.
  const { labels, comps } = components(skel, w, h);
  let big = 1; for (let i = 2; i < comps.length; i++) if (comps[i].area > comps[big].area) big = i;
  for (let i = 0; i < skel.length; i++) if (labels[i] === big) { start = i; break; }
  const a = bfs(start), b = bfs(a), path = [];
  for (let p = b; p >= 0; p = par[p]) path.push([p % w, (p / w) | 0]);
  return path;
}
const median = arr => { const a = Float64Array.from(arr).sort(); return a.length ? a[a.length >> 1] : 0; };
function quantile(arr, q) { const a = Float64Array.from(arr).sort(); return a.length ? a[Math.min(a.length - 1, Math.max(0, Math.round(q * (a.length - 1))))] : 0; }
const mean = a => { let s = 0; for (const v of a) s += v; return a.length ? s / a.length : 0; };
function std(a) { const m = mean(a); let s = 0; for (const v of a) s += (v - m) * (v - m); return a.length > 1 ? Math.sqrt(s / (a.length - 1)) : 0; }
// A moving average over +-k samples, shrinking at the ends.
function smooth1(a, k) {
  const n = a.length, out = new Float64Array(n), c = new Float64Array(n + 1);
  for (let i = 0; i < n; i++) c[i + 1] = c[i] + a[i];
  for (let i = 0; i < n; i++) { const lo = Math.max(0, i - k), hi = Math.min(n - 1, i + k); out[i] = (c[hi + 1] - c[lo]) / (hi - lo + 1); }
  return out;
}
// A Gaussian, roughly: three box blurs.
function gauss(src, w, h, sigma) {
  if (sigma < .3) return src;
  if (sigma < 1.2) {
    // Small blurs: a three-tap kernel, once or twice.
    const passes = sigma < .8 ? 1 : 2, a = sigma * sigma / (2 * passes);
    let cur = src;
    for (let n = 0; n < passes; n++) {
      const tmp = new Float32Array(w * h), out = new Float32Array(w * h);
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * w + x; tmp[i] = cur[i] * (1 - 2 * a) + a * (cur[x > 0 ? i - 1 : i] + cur[x < w - 1 ? i + 1 : i]); }
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * w + x; out[i] = tmp[i] * (1 - 2 * a) + a * (tmp[y > 0 ? i - w : i] + tmp[y < h - 1 ? i + w : i]); }
      cur = out;
    }
    return cur;
  }
  const r = Math.max(1, Math.round(Math.sqrt(12 * sigma * sigma / 3 + 1) / 2 - .5));
  return blur(blur(blur(src, w, h, r), w, h, r), w, h, r);
}

;
// ===================================================================================================================
// Match a stroke, part 1: reading the photo. Classical image analysis only (no learning): the lighting is divided out,
// the mark is thresholded (Otsu) and segmented (connected components), thinned to its centre line (Zhang–Suen), and
// measured along that line: width, edges, tone, gaps, halo, grain, ends and how many lines run side by side.
// Adapted from the first web Brush Lab's Match (weblab/src/match.js), now for the six-part engine.
// ===================================================================================================================
const MATCH = { margin: 40, lenTarget: 440, wMax: 110, wMin: 3.2, maxSide: 760 };
// Photo darkness of black ink after the lighting is divided out is about .9, not 1: read it as black.
const INK_BLACK = .9;
// A real stroke's small faults, which say nothing about the brush (the owner, 2026-10-27: "a bit too sensitive to edge
// roughness, wobble"), in work-frame px. The traced edges smoothed over edge px before roughness is read (photo and
// candidates alike: smoothing the photo alone softened its edge, which the camera's blur then copied); the centre
// line's direction taken over thetaK widths each way (a half before; the line itself smoothed more lost an italic nib's
// hairlines); the widths the nib is fitted to smoothed over nibK widths each way. Edge roughness under floor px (or
// floorW of the width, the larger) and width wobble under wobFloor (of the width) read as smooth.
const STEADY = { edge: 1.5, thetaK: 1, nibK: .5, floor: .3, floorW: .02, wobFloor: .05 };
const roughFloor = F => Math.max(STEADY.floor, STEADY.floorW * F.Wmed);
// Roughness past what any real pen shows: 0 for a ragged pen line, more for charcoal's or crayon's broken edge.
const roughExcess = F => Math.max(0, F.rough - roughFloor(F));

// ---------- 1. a photo: grey, at most 1200 px, the lighting flattened, the stroke's box found ----------
function preparePhoto(gray) {
  let { w, h, g } = gray;
  const long = Math.max(w, h);
  if (long > 1200) { const k = 1200 / long, W = Math.round(w * k), H = Math.round(h * k); g = resample(g, w, h, W, H); w = W; h = H; }
  let radius = Math.max(12, Math.max(w, h) / 16), flat = flatten(g, w, h, radius);
  let crop = autoCrop(flat.d, w, h);
  // A stroke wider than the lighting's radius would be taken for paper: widen it and look again.
  if (crop && crop.W0 * 1.4 > radius) { radius = crop.W0 * 1.4 + 6; flat = flatten(g, w, h, radius); crop = autoCrop(flat.d, w, h) || crop; }
  return { w, h, g, d: flat.d, crop: crop || { x: 0, y: 0, w, h, W0: 8 } };
}
// The stroke's width from a mask: the distance transform's 90th percentile is most of the half-width.
function widthOf(mask, w, h) {
  const dt = edt(mask, w, h), v = [];
  for (let i = 0; i < mask.length; i++) if (mask[i]) v.push(dt[i]);
  return { dt, W: Math.max(1.5, 2.1 * quantile(v, .9)) };
}
function biggest(comps) { let m = null; for (let i = 1; i < comps.length; i++) if (!m || comps[i].area > m.area) m = comps[i]; return m; }
// The box round the darkest large shape, its drops included, with some paper round it.
function autoCrop(d, w, h) {
  const t = Math.max(.06, otsu(d)), m0 = new Uint8Array(w * h);
  for (let i = 0; i < d.length; i++) m0[i] = d[i] > t ? 1 : 0;
  // Closed a little first: a pencil or charcoal line photographs as crumbs.
  const mask = closing(m0, w, h, Math.max(3, Math.round(Math.max(w, h) / 250)));
  const { labels, comps } = components(mask, w, h), main = biggest(comps);
  if (!main || main.area < 12) return null;
  const only = new Uint8Array(w * h);
  for (let i = 0; i < only.length; i++) only[i] = labels[i] === main.id ? 1 : 0;
  const W0 = widthOf(only, w, h).W, reach = 3 * W0 + 10;
  let x0 = main.x0, y0 = main.y0, x1 = main.x1, y1 = main.y1;
  for (let i = 1; i < comps.length; i++) {
    const c = comps[i];
    if (c === main || c.area < 3 || c.x1 < main.x0 - reach || c.x0 > main.x1 + reach || c.y1 < main.y0 - reach || c.y0 > main.y1 + reach) continue;
    x0 = Math.min(x0, c.x0); y0 = Math.min(y0, c.y0); x1 = Math.max(x1, c.x1); y1 = Math.max(y1, c.y1);
  }
  const pad = Math.round(Math.max(12, 1.5 * W0));
  x0 = Math.max(0, x0 - pad); y0 = Math.max(0, y0 - pad); x1 = Math.min(w - 1, x1 + pad); y1 = Math.min(h - 1, y1 + pad);
  return { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1, W0 };
}
// The work frame: the crop at the engine's scale, with paper round it.
function buildFrame(dc, cw, ch, s) {
  const W = Math.max(8, Math.round(cw * s)), H = Math.max(8, Math.round(ch * s)), M = MATCH.margin;
  const D0 = resample(dc, cw, ch, W, H), w = W + 2 * M, h = H + 2 * M, D = new Float32Array(w * h);
  for (let y = 0; y < H; y++) D.set(D0.subarray(y * W, y * W + W), (y + M) * w + M);
  return { w, h, D, s, M };
}

// ---------- 2. the mark at the engine's scale ----------
// The scale: a stroke about 440 px long (a hand's stroke on the Manta), never wider than 110 px or thinner than 3.2 px.
function analyzeStroke(photo, crop, opts = {}) {
  const raw = cropOf(photo.d, photo.w, photo.h, crop.x, crop.y, crop.w, crop.h), cw = crop.w, ch = crop.h;
  for (let i = 0; i < raw.length; i++) raw[i] = Math.min(1.2, raw[i] / INK_BLACK);
  // The paper first: its own level and texture, so the stroke is read against them (see paperModel).
  const pm = paperModel(raw, cw, ch, crop.W0 || 6), dc = pm.clean;
  const tau = Math.max(.06, otsu(dc)), m0 = new Uint8Array(dc.length);
  for (let i = 0; i < dc.length; i++) m0[i] = dc[i] > tau ? 1 : 0;
  // The stroke's extent: the mask closed a little (pencil and charcoal break into crumbs), its largest piece.
  const bridge = Math.max(3, Math.round((crop.W0 || 6) * .8)), mc = closing(m0, cw, ch, bridge);
  const { labels, comps } = components(mc, cw, ch), main = biggest(comps);
  if (!main || main.area < 12) throw new Error("No stroke found. Box it more tightly.");
  for (let i = 0; i < mc.length; i++) mc[i] = labels[i] === main.id ? 1 : 0;
  // Fine structure (parallel lines whose gaps photograph grey) shows at a stricter threshold: halfway to the stroke's
  // dark tone.
  const inVals = []; for (let i = 0; i < dc.length; i += 3) if (mc[i]) inVals.push(dc[i]);
  const tau2 = (tau + quantile(inVals, .9)) / 2, m2 = new Uint8Array(dc.length);
  for (let i = 0; i < dc.length; i++) m2[i] = dc[i] > tau2 ? 1 : 0;
  const W0 = widthOf(mc, cw, ch).W, L = strokeLength(mc, cw, ch, W0), Wl = Math.min(lineWidth(m0, mc, cw, ch), lineWidth(m2, mc, cw, ch) * 1.3);
  // The scale: about 440 px long, at most 110 px wide; but a fine line inside it (a split nib's tine, a pencil's
  // crumbs) keeps 3 px, and the whole frame stays under 760 px.
  let s = Math.min(MATCH.lenTarget / Math.max(W0, L), MATCH.wMax / W0);
  s = Math.max(s, MATCH.wMin / W0, 3 / Wl);
  s = Math.min(s, MATCH.maxSide / Math.max(cw, ch), 4);
  const job = measureMark(buildFrame(dc, cw, ch, s), tau, Object.assign({ bridge: bridge * s }, opts));
  job.crop = crop; job.scale = s; job.lineW = Wl;
  // The camera's speckle on candidates: the paper's own spread, in the stretched units (the floor then takes it out
  // again, as it took the paper's out of the photo).
  delete pm.clean; job.paper = pm;
  job.noiseSigma = clamp(pm.s / Math.max(.2, pm.b - pm.m), 0, .05);
  const nr = rng(99); job.noise = new Float32Array(job.w * job.h);
  if (job.noiseSigma > .002) for (let i = 0; i < job.noise.length; i++) job.noise[i] = (nr() + nr() + nr() - 1.5) * 2 * job.noiseSigma;
  return job;
}
// The halo control: a band from 2.5 px past the ink's half-tone edge (the camera's blur and the edge's own softness
// lie inside it) out to half a width, against bare paper: more of it over the paper's floor (three times as much, plus
// a quarter) and darker by three spreads.
function haloTest(d, w, h, mark, away, far, t0, Lc, m, sp, W0) {
  const n = d.length, half = new Uint8Array(n), hl = (t0 + Lc) / 2; for (let i = 0; i < n; i++) half[i] = mark[i] && d[i] > hl ? 1 : 0;
  const away2 = edt(notMask(half), w, h);
  let bandN = 0, bandOver = 0, papN = 0, papOver = 0, bandSum = 0;
  for (let i = 0; i < n; i++) {
    const a = away2[i];
    if (a > 2.5 && a <= 2.5 + .5 * W0) { bandN++; bandSum += d[i]; if (d[i] > t0) bandOver++; }
    else if (away[i] > far) { papN++; if (d[i] > t0) papOver++; }
  }
  const bandFrac = bandOver / Math.max(1, bandN), papFrac = papOver / Math.max(1, papN);
  return { real: bandFrac > 3 * papFrac + .25 && bandSum / Math.max(1, bandN) - m > 3 * sp, bandFrac, papFrac };
}
// Crisp ink's tone curve: 0 to 1 (paper to the mark's core) pressed to near black and white, a 30 % step at the middle.
const inkCurve = t => clamp((t - .5) / .3 + .5);
// The paper in the photo: its level and spread from pixels well away from the mark (after the lighting is divided
// out), then the photo restated against it: the paper's level plus 2.5 spreads is white, and a mark that is plainly
// ink (dark, even) but photographed grey is stretched so its dark core is black. Faint flecks away from the mark go.
// A halo counts only if the band just outside the edge is clearly darker than bare paper is (the same measure, the
// share of pixels over the paper's floor, on the band and on the paper itself).
function paperModel(d, w, h, W0) {
  const n = d.length, t1 = Math.max(.04, otsu(d)), m1 = new Uint8Array(n);
  for (let i = 0; i < n; i++) m1[i] = d[i] > t1 ? 1 : 0;
  const mc = closing(m1, w, h, Math.max(3, Math.round(W0 * .8))), { labels, comps } = components(mc, w, h), main = biggest(comps);
  const mark = new Uint8Array(n); if (main) for (let i = 0; i < n; i++) mark[i] = labels[i] === main.id ? 1 : 0;
  const away = edt(notMask(mark), w, h), inside = edt(mark, w, h), far = Math.max(10, 1.5 * W0);
  const pv = []; for (let i = 0; i < n; i += 2) if (away[i] > far) pv.push(d[i]);
  if (pv.length < 300) for (let i = 0; i < n; i += 2) if (!mark[i] && d[i] < t1) pv.push(d[i]);
  const m = median(pv), sp = Math.max(.004, robustStd(pv)), t0 = m + 2.5 * sp;
  const core = []; for (let i = 0; i < n; i++) if (inside[i] >= Math.max(1, .3 * W0)) core.push(d[i]);
  const Lc = core.length ? quantile(core, .9) : 1, cv = core.length ? std(core) / Math.max(.05, mean(core)) : 1;
  const b = Lc > .4 && cv < .35 ? clamp(Lc, .45, 1) : 1;
  // Ink unless it is plainly a wash: a mark with a dark, even core is read as crisp ink, near black and white (the
  // paper's floor to white, the core to black, a narrow step between: a best guess for a photo that isn't ideal).
  // Only a pale mark, or one with an uneven, soft inside, keeps its greys.
  const clean = new Float32Array(n);
  // (A halo, tested below, keeps the greys too: it is part of the look.)
  const haloReal = haloTest(d, w, h, mark, away, far, t0, Lc, m, sp, W0);
  const inkLike = Lc >= .45 && cv < .35 && !haloReal.real;
  if (inkLike) for (let i = 0; i < n; i++) clean[i] = inkCurve((d[i] - t0) / Math.max(.1, Lc - t0));
  else for (let i = 0; i < n; i++) clean[i] = clamp((d[i] - t0) / Math.max(.1, b - t0), 0, 1.2);
  // Flecks: faint pieces not touching the mark.
  const near = Math.max(2, .3 * W0), fm = new Uint8Array(n); for (let i = 0; i < n; i++) fm[i] = clean[i] > 0 ? 1 : 0;
  const fc = components(fm, w, h); let flecks = 0;
  const peak = new Float32Array(fc.comps.length), touch = new Uint8Array(fc.comps.length);
  for (let i = 0; i < n; i++) { const l = fc.labels[i]; if (!l) continue; if (clean[i] > peak[l]) peak[l] = clean[i]; if (away[i] <= near) touch[l] = 1; }
  for (let i = 0; i < n; i++) { const l = fc.labels[i]; if (l && !touch[l] && peak[l] < .5) { clean[i] = 0; flecks++; } }
  return { clean, m, s: sp, t0, b, Lc, cv, inkLike, stretch: 1 / Math.max(.1, b - t0), haloReal: haloReal.real, bandFrac: haloReal.bandFrac, papFrac: haloReal.papFrac, flecks: flecks / Math.max(1, n),
    texture: sp / Math.max(.1, Lc - m) };
}
// The length of a mask's centre line, roughly: thinned where the mask is about 10 px wide.
function strokeLength(mask, w, h, W) {
  const gs = Math.min(1, 10 / W), gw = Math.max(4, Math.round(w * gs)), gh = Math.max(4, Math.round(h * gs));
  const f = new Float32Array(w * h); for (let i = 0; i < f.length; i++) f[i] = mask[i];
  const small = resample(f, w, h, gw, gh), m = new Uint8Array(gw * gh);
  for (let i = 0; i < m.length; i++) m[i] = small[i] > .5 ? 1 : 0;
  const p = longestPath(thin(m, gw, gh), gw, gh);
  let len = 0; for (let i = 1; i < p.length; i++) len += Math.hypot(p[i][0] - p[i - 1][0], p[i][1] - p[i - 1][1]);
  return len / gs;
}
// The width of the finest lines in the stroke: twice the distance transform along its ridges (median), within the stroke.
function lineWidth(m0, inside, w, h) {
  const dt = edt(m0, w, h), v = [];
  for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
    const i = y * w + x, d = dt[i];
    if (!inside[i] || d < .9) continue;
    if (d >= dt[i - 1] && d >= dt[i + 1] && d >= dt[i - w] && d >= dt[i + w]) v.push(d);
  }
  return v.length ? Math.max(1, 2 * median(v)) : 4;
}

// Everything measured in the work frame: masks, the centre line, widths, directions, and the strips features read.
function measureMark(frame, tau, opts = {}) {
  const { w, h, D } = frame;
  let mask = new Uint8Array(w * h);
  for (let i = 0; i < D.length; i++) mask[i] = D[i] > tau ? 1 : 0;
  // Crumbs (pencil, charcoal) bridged as they were when the stroke was found, unless that would merge parallel lines.
  if (opts.bridge >= 1.5) { const mc = closing(mask, w, h, Math.round(opts.bridge)), pieces = components(mask, w, h).comps.length;
    if (pieces > 12) mask = mc; }
  const { labels, comps } = components(mask, w, h), main = biggest(comps);
  if (!main) throw new Error("No stroke found. Box it more tightly.");
  const mainMask = new Uint8Array(w * h);
  for (let i = 0; i < mask.length; i++) mainMask[i] = labels[i] === main.id ? 1 : 0;
  // Other pieces close by belong to the stroke (a dry brush's broken body, the second line of a split nib): joined.
  const Wm0 = widthOf(mainMask, w, h).W, near0 = edt(notMask(mainMask), w, h);
  let joinGap = 0;
  for (let i = 1; i < comps.length; i++) {
    const c = comps[i]; if (c === main || c.area < Math.max(6, Wm0 * Wm0 * .15)) continue;
    let nd = Infinity;
    for (let y = c.y0; y <= c.y1; y++) for (let x = c.x0; x <= c.x1; x++) { const j = y * w + x; if (labels[j] === c.id) nd = Math.min(nd, near0[j]); }
    if (nd <= Math.max(3, Wm0 * .8) || (c.area > main.area * .2 && nd <= 3 * Wm0 + 6)) {
      for (let y = c.y0; y <= c.y1; y++) for (let x = c.x0; x <= c.x1; x++) { const j = y * w + x; if (labels[j] === c.id) mainMask[j] = 1; }
      // A parallel line: the closing must bridge the widest part of the gap (its far edge's distance, mostly).
      if (c.area > main.area * .2) { const ds0 = []; for (let y = c.y0; y <= c.y1; y++) for (let x = c.x0; x <= c.x1; x++) { const j = y * w + x; if (labels[j] === c.id) ds0.push(near0[j]); } joinGap = Math.max(joinGap, quantile(ds0, .9)); }
    }
  }
  const Wm = widthOf(mainMask, w, h).W;
  // Bridge dry-brush gaps and the gaps between parallel lines; what's left nearby is splatter, what's far is dust.
  const closed = closing(mainMask, w, h, Math.max(1, Math.round(Math.max(Wm / 3, Wm0 * .9, joinGap / 2 + 1))));
  for (let i = 0; i < closed.length; i++) if (mainMask[i]) closed[i] = 1;
  const dtIn = edt(closed, w, h), dtOut = edt(notMask(closed), w, h);
  const Wc = widthOf(closed, w, h).W;
  const reach = Math.max(4 * Wc, 40), Dc = Float32Array.from(D);
  for (let i = 1; i < comps.length; i++) {
    const c = comps[i]; if (c === main) continue;
    let near = Infinity;
    for (let y = c.y0; y <= c.y1; y++) for (let x = c.x0; x <= c.x1; x++) { const j = y * w + x; if (labels[j] === c.id) near = Math.min(near, dtOut[j]); }
    if (near > reach) for (let y = Math.max(0, c.y0 - 1); y <= Math.min(h - 1, c.y1 + 1); y++) for (let x = Math.max(0, c.x0 - 1); x <= Math.min(w - 1, c.x1 + 1); x++) Dc[y * w + x] = Math.min(Dc[y * w + x], tau * .5);
  }
  // The centre line: thin the closed stroke at a scale where it's about 12 px wide, keep the longest path.
  const gs = Math.min(1, 12 / Wc), gw = Math.max(4, Math.round(w * gs)), gh = Math.max(4, Math.round(h * gs));
  const cf = new Float32Array(w * h); for (let i = 0; i < cf.length; i++) cf[i] = closed[i];
  const small = resample(cf, w, h, gw, gh), gm = new Uint8Array(gw * gh);
  for (let i = 0; i < gm.length; i++) gm[i] = small[i] > .5 ? 1 : 0;
  let raw = longestPath(thin(gm, gw, gh), gw, gh).map(([x, y]) => [(x + .5) * w / gw - .5, (y + .5) * h / gh - .5]);
  if (raw.length < 2) raw = [[main.x0, (main.y0 + main.y1) / 2], [main.x1, (main.y0 + main.y1) / 2]];
  const ds = Wc > 14 ? 2 : 1;
  let path = smoothPath(raw, Math.max(2, Math.round(Wc * gs / 2.5)), ds);
  path = extendEnds(path, dtIn, w, h, ds);
  path = smoothPath(path, Math.max(1, Math.round(Wc / 6 / ds)), ds);
  let widths = path.map(([x, y]) => Math.max(.6, 2 * bilinear(dtIn, w, h, x, y) - 1));
  // The photo's sensor noise, from the paper well away from the stroke: candidates get the same, so speckle compares.
  const M = frame.M, hp = [];
  for (let y = M + 1; y < h - M - 1; y += 2) for (let x = M + 1; x < w - M - 1; x += 2) {
    const i = y * w + x;
    if (dtOut[i] < reach + 2 || D[i] > tau * .5) continue;
    hp.push(D[i] - (D[i - 1] + D[i + 1] + D[i - w] + D[i + w]) / 4);
  }
  const sigma = hp.length > 200 ? Math.sqrt(mean(hp.map(v => v * v))) / 1.118 : 0;
  const nr = rng(99), noise = new Float32Array(w * h);
  if (sigma > .002) for (let i = 0; i < noise.length; i++) noise[i] = (nr() + nr() + nr() - 1.5) * 2 * sigma;
  const job = { frame, w, h, D: Dc, tau, mainMask, closed, dtIn, dtOut, Wm, Wc, reach, ds, noise, noiseSigma: sigma };
  const auto = startsAtFirst(job, path, widths);
  let forward = auto.forward;
  if (opts.flip) forward = !forward;
  if (!forward) { path = path.slice().reverse(); widths = widths.slice().reverse(); }
  job.autoForward = auto.forward; job.flipped = !!opts.flip;
  setPath(job, path, widths);
  return job;
}
// Smooths a polyline (+-k points) and resamples it every ds px.
function smoothPath(pts, k, ds) {
  const xs = smooth1(pts.map(p => p[0]), k), ys = smooth1(pts.map(p => p[1]), k);
  xs[0] = pts[0][0]; ys[0] = pts[0][1]; xs[xs.length - 1] = pts[pts.length - 1][0]; ys[ys.length - 1] = pts[pts.length - 1][1];
  const out = [[xs[0], ys[0]]];
  let carry = 0;
  for (let i = 1; i < xs.length; i++) {
    const sx = xs[i - 1], sy = ys[i - 1], dx = xs[i] - sx, dy = ys[i] - sy, seg = Math.hypot(dx, dy);
    let at = ds - carry;
    while (at <= seg) { out.push([sx + dx * at / seg, sy + dy * at / seg]); at += ds; }
    carry = seg - (at - ds);
  }
  const last = [xs[xs.length - 1], ys[ys.length - 1]], tail = out[out.length - 1];
  if (Math.hypot(last[0] - tail[0], last[1] - tail[1]) > ds * .3) out.push(last);
  return out;
}
// Thinning stops short of the ends: walk on while the stroke narrows slowly (a taper); stop where it ends at once.
function extendEnds(path, dt, w, h, ds) {
  const n = path.length, back = Math.min(n - 1, Math.max(2, Math.round(6 / ds)));
  const grow = (end, prev) => {
    const out = [];
    let [x, y] = end, dx = end[0] - prev[0], dy = end[1] - prev[1];
    const len = Math.hypot(dx, dy) || 1; dx /= len; dy /= len;
    let d = bilinear(dt, w, h, x, y);
    for (let i = 0; i < 400; i++) {
      const nx = x + dx * ds, ny = y + dy * ds, nd = bilinear(dt, w, h, nx, ny);
      if (nd < .75 || nd < d - .8 * ds) break;
      out.push([nx, ny]); x = nx; y = ny; d = nd;
    }
    return out;
  };
  const head = n > 1 ? grow(path[0], path[back]) : [], tail = n > 1 ? grow(path[n - 1], path[n - 1 - back]) : [];
  return [...head.reverse(), ...path, ...tail];
}
// The start: the blunter, wider and darker end, the end with the shorter taper; dry gaps grow toward the end.
function startsAtFirst(job, path, widths) {
  const n = path.length, Wb = median(widths.slice(Math.floor(n * .25), Math.ceil(n * .75))) || median(widths);
  const sm = smooth1(widths, Math.max(1, Math.round(Wb / 4 / job.ds)));
  const l80 = from => { for (let i = 0; i < n; i++) { const j = from ? i : n - 1 - i; if (sm[j] >= .8 * Wb) return i * job.ds; } return n * job.ds / 2; };
  const near = Math.max(2, Math.round(1.2 * Wb / job.ds));
  const endStat = first => {
    let dark = 0, holes = 0, cnt = 0;
    for (let k = 0; k < Math.min(n, near * 3); k++) {
      const [x, y] = path[first ? k : n - 1 - k], v = bilinear(job.D, job.w, job.h, x, y);
      dark += v; cnt++; if (v < job.tau) holes++;
    }
    return { dark: dark / cnt, holes: holes / cnt, bulge: Math.max(...sm.slice(first ? 0 : n - near, first ? near : n)) / Wb };
  };
  const a = endStat(true), b = endStat(false);
  const score = (l80(false) - l80(true)) / Wb + 2.5 * (b.holes - a.holes) + 1.5 * (a.bulge - b.bulge) + 2 * (a.dark - b.dark);
  return { forward: score >= 0, score };
}
// Fixes the centre line and everything that hangs off it: directions, the body, the pressure envelope, the strips.
function setPath(job, path, widths) {
  const n = path.length, ds = job.ds;
  job.path = path; job.widths = widths; job.length = (n - 1) * ds;
  const k = Math.max(1, Math.round(job.Wc * STEADY.thetaK / ds));
  job.theta = path.map((_, i) => { const a = path[Math.max(0, i - k)], b = path[Math.min(n - 1, i + k)]; return Math.atan2(b[1] - a[1], b[0] - a[0]); });
  const Wb = median(widths.slice(Math.floor(n * .25), Math.ceil(n * .75))) || median(widths);
  job.Wb = Wb;
  const sm = smooth1(widths, Math.max(1, Math.round(Wb / 4 / ds)));
  const l80 = from => { for (let i = 0; i < n; i++) { const j = from ? i : n - 1 - i; if (sm[j] >= .8 * Wb) return i * ds; } return n * ds / 2; };
  job.l80In = l80(true); job.l80Out = l80(false);
  // The body: past the tapers' reach.
  let b0 = Math.round(Math.max(.6 * Wb, 1.6 * job.l80In) / ds), b1 = n - 1 - Math.round(Math.max(.6 * Wb, 1.6 * job.l80Out) / ds);
  if (b1 - b0 < n * .2) { b0 = Math.floor(n * .3); b1 = Math.ceil(n * .7); }
  job.body = [b0, b1];
  // The pressure envelope: the body's widths smoothed over a few widths, carried on its own trend over the tapers.
  const env = smooth1(widths, Math.max(2, Math.round(1.5 * Wb / ds)));
  const trend = (from, to) => {
    const lo = Math.min(from, to), hi = Math.max(from, to), xs = [], ys = [];
    for (let i = lo; i <= hi; i++) { xs.push(i); ys.push(env[i]); }
    const mx = mean(xs), my = mean(ys); let sxy = 0, sxx = 0;
    for (let i = 0; i < xs.length; i++) { sxy += (xs[i] - mx) * (ys[i] - my); sxx += (xs[i] - mx) ** 2; }
    const slope = sxx ? sxy / sxx : 0, base = env[from];
    return i => clamp(base + slope * (i - from), .6 * base, 1.4 * base);
  };
  const reach = Math.round(Math.max(2 * Wb, 40) / ds);
  const head = trend(b0, Math.min(b1, b0 + reach)), tail = trend(b1, Math.max(b0, b1 - reach));
  job.envelope = Array.from(env, (v, i) => i < b0 ? head(i) : i > b1 ? tail(i) : v);
  const half = smooth1(widths, Math.max(1, Math.round(Wb / ds))).map(v => Math.max(.8, v / 2));
  job.half = half;
  const T = Math.ceil(Math.max(...half) + Math.max(8, Wb * .5));
  const U = []; for (let u = -1.6; u <= 1.6001; u += .05) U.push(u);
  job.ts = strip(job, T);
  job.us = strip(job, 0, U);
  job.U = U;
  // Where the loss looks: round the photo's stroke.
  const band = dilate(job.closed, job.w, job.h, job.Wb * .6 + 4), idx = [];
  for (let i = 0; i < band.length; i++) if (band[i]) idx.push(i);
  job.band = Int32Array.from(idx);
  job.satZone = dilate(job.closed, job.w, job.h, job.reach);
  job.mainZone = dilate(job.closed, job.w, job.h, 2);
  let area = 0; for (let i = 0; i < job.closed.length; i++) area += job.closed[i];
  job.mainArea = area;
  const core = [];
  for (let i = 0; i < job.dtIn.length; i++) if (job.dtIn[i] >= Math.max(1.5, job.Wb * .2)) core.push(i);
  job.core = Int32Array.from(core);
  // A box round the band, for the per-candidate filters.
  let x0 = job.w, y0 = job.h, x1 = 0, y1 = 0;
  for (const i of job.band) { const x = i % job.w, y = (i / job.w) | 0; if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; }
  job.box = [x0, y0, x1, y1];
  job.blurR = Math.max(2, Math.round(Wb / 6));
  job.Db = blur(job.D, job.w, job.h, job.blurR);
  let s = 0; for (const i of job.band) s += job.Db[i];
  job.Dmean = s / job.band.length;
  job.commonBlur = .7;
  job.Df = gauss(job.D, job.w, job.h, job.commonBlur);
  job.rho = 1;
  const f0 = features(job, job.Df);
  job.rho = clamp(job.tau / Math.max(.05, f0.Lb), .2, .8);
  job.F = features(job, job.Df);
  const sat = satellites(job, job.D, job.F.thr); job.F.satN = sat.n; job.F.satA = sat.a;
  job.F.spec = spectrum(job, job.D);
  job.ends = [endShape(job, job.Df, job.F.thr, true), endShape(job, job.Df, job.F.thr, false)];
  pressureEnvelope(job);
  fitNib(job);
  job.swing = widthSwing(job);
}
// The pressure envelope, second pass: the widths as the photo shows them row by row, smoothed only over a third of a
// width, so a wobble or a thin stretch comes through. Only the taper zones are held: from each tip until the width
// reaches 80 % of what lies just ahead of it (a plateau, even a thin one, ends the zone; a point keeps rising).
function pressureEnvelope(job) {
  const n = job.path.length, ds = job.ds, Wb = job.Wb, F = job.F;
  const wd = Array.from(F.widths, (v, i) => v > 0 ? v : job.widths[i]);
  const env = smooth1(wd, Math.max(1, Math.round(.3 * Wb / ds))), win = Math.max(4, Math.round(1.2 * Wb / ds));
  const zone = first => {
    for (let k = 0; k < n - win; k++) {
      const i = first ? k : n - 1 - k, ahead = first ? env.slice(i, i + win) : env.slice(i - win + 1, i + 1);
      if (env[i] >= .8 * median(ahead) && env[i] > .15 * Wb) return k;
    }
    return Math.floor(n / 3);
  };
  let zIn = zone(true), zOut = zone(false);
  if (zIn + zOut > n * .8) { zIn = Math.floor(n * .3); zOut = Math.floor(n * .3); }
  job.zoneIn = zIn * ds; job.zoneOut = zOut * ds;
  const hi = env[zIn], ho = env[n - 1 - zOut];
  job.envelope = Array.from(env, (v, i) => i < zIn ? hi : i > n - 1 - zOut ? ho : v);
  // How far the width swings outside the tapers, against the body's median (the pressure range the hand must cover).
  const mid = job.envelope.slice(zIn, n - zOut), med = median(job.envelope.slice(job.body[0], job.body[1] + 1));
  job.widthRange = [quantile(mid, .04) / med, quantile(mid, .96) / med];
}
// Bilinear sample positions along the centre line: per row, columns at t px across (or u half-widths across).
function strip(job, T, U) {
  const n = job.path.length, cols = U ? U.length : 2 * T + 1, idx = new Int32Array(n * cols), fx = new Float32Array(n * cols), fy = new Float32Array(n * cols);
  const { w, h } = job;
  for (let i = 0; i < n; i++) {
    const [x, y] = job.path[i], th = job.theta[i], nx = -Math.sin(th), ny = Math.cos(th);
    for (let j = 0; j < cols; j++) {
      const t = U ? U[j] * job.half[i] : j - T;
      const X = clamp(x + nx * t, 0, w - 1.001), Y = clamp(y + ny * t, 0, h - 1.001);
      const x0 = X | 0, y0 = Y | 0, o = i * cols + j;
      idx[o] = y0 * w + x0; fx[o] = X - x0; fy[o] = Y - y0;
    }
  }
  return { idx, fx, fy, rows: n, cols, T };
}
function sampleStrip(st, img, w) {
  const out = new Float32Array(st.idx.length);
  for (let o = 0; o < out.length; o++) {
    const i = st.idx[o], tx = st.fx[o], ty = st.fy[o];
    out[o] = (img[i] * (1 - tx) + img[i + 1] * tx) * (1 - ty) + (img[i + w] * (1 - tx) + img[i + w + 1] * tx) * ty;
  }
  return out;
}

// ---------- 3. features: measured the same way on the photo and on every candidate ----------
function features(job, img) {
  const { ts, us, ds } = job, n = ts.rows, [b0, b1] = job.body, w = job.w;
  const S = sampleStrip(ts, img, w), Us = sampleStrip(us, img, w), T = ts.T, C = ts.cols, UC = us.cols;
  // Body tone: the dark part of the stroke over the body (its 85th percentile: split lines leave paper between them).
  const coreVals = [];
  for (let i = b0; i <= b1; i++) for (let j = 0; j < UC; j++) if (Math.abs(job.U[j]) <= .9) coreVals.push(Us[i * UC + j]);
  const Lb = Math.max(.02, quantile(coreVals, .85)), thr = job.rho * Lb;
  // Widths: from the centre out to the last pixel over the threshold, stepping over gaps (dry brush, split lines).
  const wl = new Float64Array(n), wr = new Float64Array(n), wd = new Float64Array(n), gapMax = Math.max(2, Math.round(.45 * job.Wb));
  const edgeFrom = (row, dir) => {
    let last = -1, gap = 0;
    for (let k = 0; k <= T; k++) {
      const j = T + dir * k;
      if (S[row + j] >= thr) { last = j; gap = 0; } else if (last >= 0 && ++gap > gapMax) break; else if (last < 0 && k > gapMax + .5 * job.Wb) break;
    }
    if (last < 0) return 0;
    const out = last + dir;
    if (out < 0 || out >= C) return last - T;
    const a = S[row + out], b = S[row + last];
    return last - T + dir * (b > a ? clamp((b - thr) / (b - a)) : 0);
  };
  for (let i = 0; i < n; i++) { const L = edgeFrom(i * C, -1), R = edgeFrom(i * C, 1); wl[i] = L; wr[i] = R; wd[i] = Math.max(0, R - L); }
  const bodyW = Array.from(wd.slice(b0, b1 + 1)), Wmed = Math.max(.5, median(bodyW));
  const sm = smooth1(wd, Math.max(1, Math.round(Wmed / 4 / ds)));
  const l80 = from => { for (let i = 0; i < n; i++) { const j = from ? i : n - 1 - i; if (sm[j] >= .8 * Wmed) return i * ds; } return n * ds / 2; };
  const near = Math.max(2, Math.round(Wmed / ds));
  let poolS = 0, poolE = 0;
  for (let i = 0; i < Math.min(n, near); i++) { poolS = Math.max(poolS, sm[i]); poolE = Math.max(poolE, sm[n - 1 - i]); }
  // Wobble: the body's width wiggling over a few px to a few widths.
  const fine = smooth1(wd, Math.max(1, Math.round(1.5 / ds))), slow = smooth1(wd, Math.max(2, Math.round(2.5 * Wmed / ds)));
  const bp = []; for (let i = b0; i <= b1; i++) bp.push(fine[i] - slow[i]);
  // Edge roughness: the edges' fine jitter, in px.
  const jit = [];
  for (const e of [wl, wr]) { const ef = smooth1(e, Math.max(1, Math.round(STEADY.edge / ds))), es = smooth1(e, Math.max(2, Math.round(3 * STEADY.edge / ds))); for (let i = b0; i <= b1; i++) jit.push(ef[i] - es[i]); }
  // Dry brush: gaps in the core, first and second half; speckle: fine variation in the core. Runs: how many separate
  // lines cross each row of the body (a split nib's two, a multi-line's three to five, a dry brush's changing count).
  let dryA = 0, dryB = 0, nA = 0, nB = 0;
  const hp = [], runs = [];
  const mid = (b0 + b1) / 2;
  for (let i = Math.max(1, b0); i <= Math.min(n - 2, b1); i++) {
    const lim = .7 * job.half[i];
    for (let j = 1; j < C - 1; j++) {
      const t = j - T; if (Math.abs(t) > lim) continue;
      const v = S[i * C + j], gap = v < .5 * Lb ? 1 : 0;
      if (i < mid) { dryA += gap; nA++; } else { dryB += gap; nB++; }
      hp.push(v - (S[i * C + j - 1] + S[i * C + j + 1] + S[(i - 1) * C + j] + S[(i + 1) * C + j]) / 4);
    }
    if (wd[i] > 0) {
      // Peaks across the row: a new line starts when the tone has dipped by a quarter of the body tone and risen again
      // (a gap needn't reach the paper: between close lines it photographs grey).
      const prom = .25 * Lb;
      let cnt = 0, up = true, peak = -1, dip = 1e9;
      for (let j = Math.max(0, Math.floor(T + wl[i]) - 1); j <= Math.min(C - 1, Math.ceil(T + wr[i]) + 1); j++) {
        const v = S[i * C + j];
        // A gap between lines falls well below the lines (to 65 % of the peak, and by a quarter tone): a wash's darker
        // rim round a paler middle doesn't.
        if (up) { if (v > peak) peak = v; if (peak >= thr && v < peak - prom && v < .65 * peak) { cnt++; up = false; dip = v; } }
        else { if (v < dip) dip = v; if (v > dip + prom && v > dip / .65) { up = true; peak = v; } }
      }
      if (up && peak >= thr) cnt++;
      runs.push(Math.max(1, cnt));
    }
  }
  dryA /= Math.max(1, nA); dryB /= Math.max(1, nB);
  const hist = [0, 0, 0, 0, 0, 0, 0]; for (const r of runs) hist[Math.min(6, r)]++;
  let mode = 1; for (let r = 1; r <= 6; r++) if (hist[r] > hist[mode]) mode = r;
  const runsFrac = hist[mode] / Math.max(1, runs.length), runsMean = mean(runs);
  // Across the stroke in half-widths: the mean profile (soft edge, hollow edge) and hair lines (streaks).
  const prof = new Float64Array(UC), rowMean = [];
  for (let i = b0; i <= b1; i++) {
    let rm = 0, rc = 0;
    for (let j = 0; j < UC; j++) { const v = Us[i * UC + j]; prof[j] += v; if (Math.abs(job.U[j]) <= .75) { rm += v; rc++; } }
    rowMean.push(rm / rc);
  }
  for (let j = 0; j < UC; j++) prof[j] /= (b1 - b0 + 1) * Lb;
  let inner = 0, ni = 0, rim = 0, nr = 0;
  job.U.forEach((u, j) => { const a = Math.abs(u); if (a < .35) { inner += prof[j]; ni++; } else if (a >= .7 && a <= .9) { rim += prof[j]; nr++; } });
  inner /= ni; rim /= nr;
  const crossing = (level, side) => { for (let k = 0; k < UC; k++) { const j = side < 0 ? k : UC - 1 - k; if (prof[j] >= level) return Math.abs(job.U[j]); } return 0; };
  const ramp = Math.max(0, ((crossing(.2, -1) - crossing(.8, -1)) + (crossing(.2, 1) - crossing(.8, 1))) / 2);
  const cu = [], cv = [];
  job.U.forEach((u, j) => { if (Math.abs(u) <= .75) { let s = 0; for (let i = b0; i <= b1; i++) s += Us[i * UC + j]; cu.push(u); cv.push(s / (b1 - b0 + 1)); } });
  const streak = std(residualQuadratic(cu, cv)) / Lb;
  // Bloom: the tone wandering along the stroke, a few tens of px at a time.
  const rmS = smooth1(rowMean, Math.max(1, Math.round(4 / ds))), rmL = smooth1(rowMean, Math.max(3, Math.round(120 / ds)));
  const bloom = std(rmS.map((v, i) => v - rmL[i])) / Lb;
  // Halo (bleed): what lies 2 to 8 px outside the edges, as a share of the body tone; the outer halo 8 to 20 px out.
  let fr = 0, fn = 0, fo = 0, fon = 0;
  for (let i = b0; i <= b1; i++) {
    if (!wd[i]) continue;
    for (const [e, sgn] of [[wl[i], -1], [wr[i], 1]]) {
      for (let t = 2; t <= 8; t++) { const j = Math.round(e + sgn * t) + T; if (j >= 0 && j < C) { fr += S[i * C + j]; fn++; } }
      for (let t = 9; t <= 20; t++) { const j = Math.round(e + sgn * t) + T; if (j >= 0 && j < C) { fo += S[i * C + j]; fon++; } }
    }
  }
  // The halo band: the share of samples 2 to 6 px outside each row's own edges that are visibly grey (a pixel or two
  // of anti-aliasing and the camera's blur sit inside 2 px, so they don't count).
  let hb = 0, hbn = 0; const hThr = clamp(.35 * Lb, .08, .12);
  for (let i = 0; i < n; i++) {
    if (!wd[i]) continue;
    for (const [e, sgn] of [[wl[i], -1], [wr[i], 1]]) for (let t = 2; t <= 6; t++) { const j = Math.round(e + sgn * t) + T; if (j >= 0 && j < C) { hbn++; if (S[i * C + j] > hThr) hb++; } }
  }
  // Tone histogram over the stroke: 24 bins of darkness up to 1.2.
  const th = new Float64Array(24); let tn = 0;
  for (let i = b0; i <= b1; i++) for (let j = 0; j < UC; j++) if (Math.abs(job.U[j]) <= .9) { th[Math.min(23, Math.max(0, (Us[i * UC + j] / 1.2 * 24) | 0))]++; tn++; }
  for (let k = 0; k < 24; k++) th[k] /= Math.max(1, tn);
  return { Lb, thr, Wmed, l80In: l80(true), l80Out: l80(false), poolS: poolS / Wmed, poolE: poolE / Wmed, wob: robustStd(bp) / Wmed, rough: robustStd(jit),
    dry: (dryA + dryB) / 2, dryGrow: dryB - dryA, speck: std(hp) / Lb, hollow: rim / Math.max(.05, inner) - 1, ramp, streak, bloom,
    halo: fr / Math.max(1, fn) / Lb, haloOut: fo / Math.max(1, fon) / Lb, haloFrac: hb / Math.max(1, hbn), runsMode: mode, runsFrac, runsMean, toneHist: th, widths: wd, wl, wr };
}
function robustStd(a) { if (!a.length) return 0; const m = median(a); return 1.4826 * median(a.map(v => Math.abs(v - m))); }
function residualQuadratic(x, y) {
  const n = x.length; let s0 = n, s1 = 0, s2 = 0, s3 = 0, s4 = 0, t0 = 0, t1 = 0, t2 = 0;
  for (let i = 0; i < n; i++) { const a = x[i], a2 = a * a; s1 += a; s2 += a2; s3 += a2 * a; s4 += a2 * a2; t0 += y[i]; t1 += a * y[i]; t2 += a2 * y[i]; }
  const M = [[s0, s1, s2, t0], [s1, s2, s3, t1], [s2, s3, s4, t2]];
  for (let c = 0; c < 3; c++) { let p = c; for (let r = c + 1; r < 3; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r; [M[c], M[p]] = [M[p], M[c]]; if (Math.abs(M[c][c]) < 1e-12) return y.slice(); for (let r = 0; r < 3; r++) if (r !== c) { const f = M[r][c] / M[c][c]; for (let k = c; k < 4; k++) M[r][k] -= f * M[c][k]; } }
  const a = M[0][3] / M[0][0], b = M[1][3] / M[1][1], c = M[2][3] / M[2][2];
  return y.map((v, i) => v - (a + b * x[i] + c * x[i] * x[i]));
}
// The share of samples 2 to 6 px outside each row's edges (as features found them) that are visibly grey.
function haloFracOf(job, img, F) {
  const st = job.ts, C = st.cols, T = st.T, S = sampleStrip(st, img, job.w), n = st.rows;
  let hb = 0, hbn = 0;
  for (let i = 0; i < n; i++) {
    if (!F.widths[i]) continue;
    for (const [e, sgn] of [[F.wl[i], -1], [F.wr[i], 1]]) for (let t = 2; t <= 6; t++) { const j = Math.round(e + sgn * t) + T; if (j >= 0 && j < C) { hbn++; if (S[i * C + j] > .06) hb++; } }
  }
  return hb / Math.max(1, hbn);
}
// Satellites: drops near the stroke, outside it (splatter).
function satellites(job, img, thr) {
  const { w, h } = job, m = new Uint8Array(w * h);
  for (let i = 0; i < m.length; i++) m[i] = img[i] > thr && job.satZone[i] && !job.mainZone[i] ? 1 : 0;
  const { comps } = components(m, w, h);
  let count = 0, area = 0;
  for (let i = 1; i < comps.length; i++) { if (comps[i].area < 2) continue; count++; area += comps[i].area; }
  return { n: count * 100 / Math.max(20, job.length), a: area / Math.max(1, job.mainArea) };
}
// The grain's spectrum inside the stroke: the energy in four octave bands (differences of Gaussians), per unit of tone.
// Paper tooth, dry-brush speckle and charcoal dust each fill different bands.
const SPEC_SIGMAS = [.6, 1.2, 2.4, 4.8, 9.6];
function spectrum(job, img) {
  const [x0, y0, x1, y1] = job.box, bw = x1 - x0 + 1, bh = y1 - y0 + 1, sub = new Float32Array(bw * bh);
  for (let y = 0; y < bh; y++) sub.set(img.subarray((y + y0) * job.w + x0, (y + y0) * job.w + x0 + bw), y * bw);
  const G = SPEC_SIGMAS.map(s => gauss(sub, bw, bh, s)), out = [];
  let lb = 0; for (const i of job.core) lb += img[i];
  lb = Math.max(.05, lb / Math.max(1, job.core.length));
  for (let k = 0; k < SPEC_SIGMAS.length - 1; k++) {
    let e = 0, n = 0;
    for (const i of job.core) { const x = i % job.w - x0, y = ((i / job.w) | 0) - y0, j = y * bw + x; const v = G[k][j] - G[k + 1][j]; e += v * v; n++; }
    out.push(Math.sqrt(e / Math.max(1, n)) / lb);
  }
  return out;
}

// ---------- the ends: square (a flat brush lifted), round (a round tip's cap) or tapered ----------
// Walks past the centre line's end along its direction, measuring the width across at every half pixel; the tip is
// where the width falls under 15 % of the body's. Square: the width 12 % of a body width back from the tip, against
// the body's (a square end keeps its width to the edge, a round cap is down to 65 % there, a taper far less). The end
// edge's slant: a line fitted to where each strip across the stroke stops.
function endShape(job, img, thr, first, turn = 0) {
  const P = job.path, n = P.length, W = Math.max(2, job.Wb), w = job.w, h = job.h;
  // The stroke's direction from a little way back (the last width of the centre line bends toward a slanted cut's
  // long corner).
  const k = Math.min(n - 1, Math.max(3, Math.round(W / job.ds))), k3 = Math.min(n - 1, 3 * k), e = first ? P[0] : P[n - 1];
  const a = first ? P[k3] : P[n - 1 - k3], b = first ? P[k] : P[n - 1 - k];
  let dx = b[0] - a[0], dy = b[1] - a[1]; const L = Math.hypot(dx, dy) || 1; dx /= L; dy /= L;
  if (turn) { const c = Math.cos(turn * Math.PI / 180), sn = Math.sin(turn * Math.PI / 180); [dx, dy] = [dx * c - dy * sn, dx * sn + dy * c]; }
  const nx = -dy, ny = dx, half = W * .8, ts = [], ws = [];
  const at = (t, u) => { const x = e[0] + dx * t + nx * u, y = e[1] + dy * t + ny * u; return x < 0 || y < 0 || x > w - 1.001 || y > h - 1.001 ? 0 : bilinear(img, w, h, x, y); };
  for (let t = -W; t <= 1.5 * W + 6; t += .5) {
    let c = 0; for (let u = -half; u <= half; u += .5) if (at(t, u) >= thr) c += .5;
    ts.push(t); ws.push(c);
  }
  let tip = -W; for (let i = 0; i < ws.length; i++) if (ws[i] > .15 * W) tip = ts[i];
  // How full the last half width is, against a rectangle at the width just before it: a square end is nearly 1 (its
  // edge blurs a pixel or two), a round cap about 0.8, a taper well under. Under 6 px wide the photo can't tell.
  const before = [], last = []; for (let i = 0; i < ts.length; i++) { const d = tip - ts[i]; if (d >= .6 * W && d <= W) before.push(ws[i]); else if (d >= 0 && d < .5 * W) last.push(ws[i]); }
  const Wref = Math.max(.5, median(before) || W), fill = mean(last) / Wref;
  // Fuller than the width before it is a blob (ink pooled where the pen stopped), not a square end.
  const square = W < 6 ? .3 : clamp((fill - .8) / .15) * clamp(1 - (fill - 1.12) / .1);
  // Where each strip across stops, for the middle 70 % of the width.
  const us = [], te = [];
  for (let u = -.35 * W; u <= .35 * W; u += 1) { let last = null; for (let t = -W; t <= tip + 4; t += .5) if (at(t, u) >= thr) last = t; if (last !== null) { us.push(u); te.push(last); } }
  let slant = 0, straight = 1;
  if (us.length >= 4) {
    const mu = mean(us), mt = mean(te); let suu = 0, sut = 0; for (let i = 0; i < us.length; i++) { suu += (us[i] - mu) ** 2; sut += (us[i] - mu) * (te[i] - mt); }
    const b = suu ? sut / suu : 0; slant = Math.atan(b) * 180 / Math.PI;
    let r = 0; for (let i = 0; i < us.length; i++) r += (te[i] - (mt + b * (us[i] - mu))) ** 2;
    straight = Math.sqrt(r / us.length) / W;
  }
  // The cut's curve: where the strips across the middle 60 % stop, fitted with a parabola. A round cap bows out by
  // about a tenth of the width there, a taper far more; a square cut, slanted or not, hardly at all.
  let curveSq = 0;
  const um = [], tm = []; for (let i = 0; i < us.length; i++) if (Math.abs(us[i]) <= .3 * W) { um.push(us[i]); tm.push(te[i]); }
  // Only an end that keeps most of its width to within half a width of the tip can be a cut (a taper's last strips,
  // few and close together, would read as a short straight edge).
  const nearTip = []; for (let i = 0; i < ts.length; i++) { const dd = tip - ts[i]; if (dd >= .35 * W && dd <= .65 * W) nearTip.push(ws[i]); }
  const keeps = (median(nearTip) || 0) / Wref;
  if (W >= 6 && keeps > .7 && um.length >= Math.max(4, .8 * (.6 * W + 1))) {
    const r2 = residualQuadratic(um, tm), q = um.map((u, i) => tm[i] - r2[i]);
    const u0 = Math.min(...um), u1 = Math.max(...um), mid = (u0 + u1) / 2, iMid = um.reduce((b, u, i) => Math.abs(u - mid) < Math.abs(um[b] - mid) ? i : b, 0);
    // Depth of the bow: the fitted curve's middle against the chord between its ends.
    const i0 = um.indexOf(u0), i1 = um.indexOf(u1), chord = q[i0] + (q[i1] - q[i0]) * (um[iMid] - u0) / Math.max(1e-6, u1 - u0);
    const depth = Math.abs(q[iMid] - chord) / W;
    curveSq = clamp(1 - (depth - .015) / .05);
  }
  const out = { tip, square: W < 6 ? .3 : Math.max(square, curveSq), slant, straight, dir: [dx, dy], at: [e[0] + dx * tip, e[1] + dy * tip], fill, curveSq };
  // A cut slanted to the line across (a chisel, or a brush lifted at an angle): measured again turned to it.
  if (!turn && Math.abs(slant) > 4 && Math.abs(slant) < 45) {
    for (const t of [slant, -slant]) { const o = endShape(job, img, thr, first, t); if (o.fill > out.fill) { o.slant = slant; return o; } }
  }
  return out;
}
const endWord = E => E.square > .65 ? "square" : E.square > .2 ? "round" : "tapered";

// ---------- 4. the nib and the swing of pressure, from the widths ----------
const nibFactor = (theta, phi, A) => { const s = Math.sin(theta - phi), c = Math.cos(theta - phi); return Math.sqrt(s * s + A * A * c * c); };
// Fits width(theta) for an elliptic nib, the pressure free as a slow swell along the stroke. Shaped only when the
// stroke turns enough to tell, and the shaped fit is clearly better than a round one.
function fitNib(job) {
  // The whole stroke but its very ends: a shaped nib's thin strokes would otherwise read as tapers and be left out.
  const n = job.path.length, b0 = Math.max(1, Math.round(n * .06)), b1 = Math.min(n - 2, Math.round(n * .94)), ys = [], th = [], ss = [];
  // The widths smoothed a little first (STEADY.nibK widths each way): a ragged edge's notches aren't the nib.
  const wd = smooth1(job.widths, Math.max(1, Math.round(STEADY.nibK * job.Wb / job.ds)));
  for (let i = b0; i <= b1; i++) { ys.push(Math.log(Math.max(.5, wd[i]))); th.push(job.theta[i]); ss.push((i - b0) / Math.max(1, b1 - b0)); }
  const sse = (phi, A) => std(residualQuadratic(ss, ys.map((y, i) => y - Math.log(nibFactor(th[i], phi, A))))) ** 2;
  const round = sse(0, 1); let best = { e: Infinity };
  for (let A = .14; A <= .9; A *= 1.12) for (let deg = 0; deg < 180; deg += 3) {
    const e = sse(deg * Math.PI / 180, A);
    if (e < best.e) best = { e, A, deg };
  }
  let c2 = 0, s2 = 0; for (const t of th) { c2 += Math.cos(2 * t); s2 += Math.sin(2 * t); }
  const variety = 1 - Math.hypot(c2, s2) / Math.max(1, th.length);
  const shaped = variety > .15 && best.A < .62 && round > best.e * 2 && job.F.runsMode === 1;
  // The engine's nib angle (image coordinates, y down: a stroke along it is thinnest) runs 20–70°: the best in that range.
  let eng = { e: Infinity };
  for (let A = .14; A <= .9; A *= 1.12) for (let deg = 20; deg <= 70; deg += 2) { const e = sse(deg * Math.PI / 180, A); if (e < eng.e) eng = { e, A, deg }; }
  job.nib = shaped && (eng.e < best.e * 1.5 || best.A < .3) ? { shaped: true, A: best.A, angle: eng.deg, engA: eng.A, fitAngle: best.deg, inRange: eng.e < best.e * 1.5 } : { shaped: false, A: 1, angle: 45 };
  job.nibFit = { round, best, variety };
}
// How much the width swings in the body, beyond the nib's own turn: 10th over 90th percentile of the smoothed widths.
function widthSwing(job) {
  const [b0, b1] = job.body, v = [];
  for (let i = b0; i <= b1; i++) v.push(job.envelope[i] / (job.nib.shaped ? nibFactor(job.theta[i], job.nib.fitAngle * Math.PI / 180, job.nib.A) : 1));
  const sm = smooth1(v.length > 3 ? v : job.widths, Math.max(2, Math.round(1.5 * job.Wb / job.ds)));
  return clamp(quantile(sm, .1) / Math.max(.1, quantile(sm, .9)), .05, 1);
}

;
// ===================================================================================================================
// Match a stroke, part 2: the fit. Inverse rendering with the real engine: a candidate brush is drawn along the photo's
// own centre line (pressure solved from the photo's widths, a steady hand), softened and speckled like the camera, and
// scored against the photo. Coarse candidates (Tip × Medium × Life, from the analysis) each get a short look; the best
// two get a Nelder–Mead search over their dials; then the paper. No learning anywhere.
// ===================================================================================================================
const FIT = { pMid: .6, speed: 1, budgetMs: 5000 };
const LINE_MEDIA = { ink: 1, charcoal: 1, graphite: 1, crayon: 1 };
// Media that sit on the paper's tooth: cold press to start with (the paper step tries the rest).
const TOOTHY = { charcoal: 1, graphite: 1, crayon: 1 };

// ---------- a candidate: a structure (the choices) plus a vector of dial values ----------
// struct: { tip, medium, life, paper, count?, hairs? }   x: { key: value } over FREE dials, plus size and shade.
// Keys are "part.dial", "sizeK" (log of the size against the analysis's guess), "shade" and "hand.ps".
function brushOf(struct, x, seed) {
  const B = { name: "matched", seed: seed >>> 0, style: "Anything goes" };
  B.tip = freshPart("tip", struct.tip); B.medium = freshPart("medium", struct.medium); B.carry = freshPart("carry", "off");
  B.paper = freshPart("paper", struct.paper || "plain"); B.life = freshPart("life", struct.life || "wet"); B.hand = freshPart("hand", "custom");
  if (struct.count) B.tip.d.count = struct.count;
  if (struct.hairs) B.tip.d.hairs = struct.hairs;
  for (const [k, v] of Object.entries(struct.fixed || {})) { const [p, d] = k.split("."); B[p].d[d] = v; }
  for (const [k, v] of Object.entries(x)) {
    if (k === "sizeK" || k === "shade") continue;
    const [p, d] = k.split(".");
    if (B[p] && d in B[p].d) B[p].d[d] = v;
  }
  for (const key of ["ps", "pd", "pw", "ts", "td", "tw", "ss", "sd", "sw"]) B.hand.d[key] = key === "ps" ? (x["hand.ps"] ?? 0) : 0;
  B.hand.d.gain = 1; B.hand.d.dir = 0;
  return normBrush(B);
}
// What draws the visible width: the line (ink, charcoal, graphite while they stay mostly dry) or the body of paint.
function widthLaw(B) {
  const mk = B.medium.k, line = LINE_MEDIA[mk] && !(mk === "ink" && B.medium.d.wet > .55);
  const P = Math.min(1, B.hand.d.ps * B.hand.d.gain);
  if (B.tip.k === "hake") return { line: false, f: p => .8 + .2 * Math.min(1, Math.pow(clamp(p), 1.2)) };
  if (line) return { line: true, f: p => lerp(1 - .75 * P, 1 + .6 * P, p) };
  const tip = lerp(.6, .06, P);
  return { line: false, f: p => tip + (1 - tip) * Math.pow(clamp(p), 1.2) };
}
// The size the analysis expects for this brush, before the fit's own factor: the body's width at a usual pressure.
function baseSize(job, B) {
  const law = widthLaw(B), env = median(job.envelope.slice(job.body[0], job.body[1] + 1));
  let w = env / law.f(FIT.pMid);
  const tk = B.tip.k;
  if (law.line) {
    if (tk === "chisel" || tk === "flat") w /= Math.max(.35, median(job.theta.slice(job.body[0], job.body[1] + 1).map(t => nibFactor(t, (B.tip.d.angle) * Math.PI / 180, B.tip.d.aspect))));
    if (tk === "split") w /= 1.1 + .5 * B.tip.d.gap;
    if (tk === "multi") w /= (2 + 1.4 * B.tip.d.count) * (.25 + .45 * B.tip.d.reach);
    if (tk === "bristles") w /= 1.15;
  } else {
    w /= 1 - .25 * (B.tip.d.soft ?? .3);
  }
  return w;
}
// The pen's samples along the photo's centre line: pressure from the widths through the brush's own width law, about
// 1 px a millisecond, easing off over the last two samples, then held a moment (so the Fast-wet tail re-tapers).
function pointsFor(job, B) {
  const law = widthLaw(B), n = job.path.length, P = Math.min(1, B.hand.d.ps * B.hand.d.gain);
  const [b0, b1] = job.body, envMed = median(job.envelope.slice(b0, b1 + 1)), fMid = law.f(FIT.pMid);
  const every = job.Wb > 40 ? Math.max(1, Math.round(.05 * job.Wb / job.ds)) : 1, at = [];
  for (let i = 0; i < n - 1; i += every) at.push(i);
  at.push(n - 1);
  const pts = [];
  let d = 0, last = null, t = 0;
  at.forEach((i, k) => {
    const [x, y] = job.path[i];
    if (last) { const seg = Math.hypot(x - last[0], y - last[1]); d += seg; t += seg / FIT.speed; }
    last = job.path[i];
    let p = FIT.pMid;
    if (P > .02) {
      const want = fMid * job.envelope[i] / envMed;
      let lo = 0, hi = 1;
      for (let it = 0; it < 22; it++) { const m = (lo + hi) / 2; if (law.f(m) < want) lo = m; else hi = m; }
      p = (lo + hi) / 2;
    }
    // A hand eases off into a tapered or round end; a square end is lifted at full pressure.
    if (k >= at.length - 2 && !squareEnd(job, false)) p *= k === at.length - 1 ? .15 : .45;
    pts.push({ x, y, p: clamp(p, .02, 1), t, tx: NaN, ty: NaN });
  });
  // A flat head's ribbon stops where its last sample is: it is drawn out to each square end's edge (a round tip's
  // cap reaches past its last sample by itself).
  if (B.tip.k === "hake" && job.ends && pts.length > 1) {
    const reach = (E, from) => { const out = []; for (let t = 1; t < E.tip - .5; t += 1) out.push([from.x + E.dir[0] * t, from.y + E.dir[1] * t]); return out; };
    const head = reach(job.ends[0], pts[0]).reverse(), tail = reach(job.ends[1], pts[pts.length - 1]);
    const p0 = pts[0], pl = pts[pts.length - 1];
    pts.unshift(...head.map(([x, y]) => ({ x, y, p: p0.p, t: 0, tx: NaN, ty: NaN })));
    pts.push(...tail.map(([x, y]) => ({ x, y, p: pl.p, t: 0, tx: NaN, ty: NaN })));
    let tt = 0; pts.forEach((q, i) => { if (i) tt += Math.hypot(q.x - pts[i - 1].x, q.y - pts[i - 1].y) / FIT.speed; q.t = tt; });
  }
  if (pts.length > 1) { const L = pts[pts.length - 1]; L.t += 60; }
  return pts;
}
const squareEnd = (job, first) => !!(job.ends && job.ends[first ? 0 : 1].square > .65);
// The candidate drawn: darkness 0 (paper) to 1 (black), before the camera.
function renderFit(job, B, size, shade) {
  if (!job.surf) job.surf = modSurface(job.w, job.h, 2026);
  const s = job.surf;
  blankPage(s);
  layStroke(s, B, pointsFor(job, B), size, shade, null);
  const out = new Float32Array(job.w * job.h);
  for (let i = 0; i < out.length; i++) out[i] = 1 - s.page[i] / PAPER_TONE;
  return out;
}
// The camera's blur, from a first candidate: a crisp line is softened until its edges are as soft as the photo's.
// (Paint's soft edge is the brush's own, so paint is left as drawn.)
function cameraBlur(job, B, size, shade) {
  job.blurSigma = 0;
  if (!widthLaw(B).line) return 0;
  const img = camera(job, renderFit(job, B, size, shade), true), sp = edgeSigma(job, job.D), sr = edgeSigma(job, img);
  return job.blurSigma = clamp(Math.sqrt(Math.max(0, sp * sp - sr * sr)), 0, Math.min(2.5, .2 * job.Wb));
}
// The camera: the photo's blur and its sensor noise.
// Then the paper's floor, as the photo had it (paperModel): speckle at the paper's spread, 2.5 spreads taken off.
// For crisp ink the same near black-and-white curve as the photo had.
function camera(job, img, noNoise) {
  let out = job.blurSigma > .3 ? gauss(img, job.w, job.h, job.blurSigma) : Float32Array.from(img);
  const sg = job.noiseSigma || 0, f = 2.5 * sg, k = 1 / (1 - f);
  if (job.paper && job.paper.inkLike) { for (let i = 0; i < out.length; i++) out[i] = inkCurve(out[i] + (noNoise || sg <= .002 ? 0 : job.noise[i])); return out; }
  if (sg > .002) for (let i = 0; i < out.length; i++) out[i] = clamp((out[i] + (noNoise ? 0 : job.noise[i]) - f) * k, 0, 1.2);
  return out;
}

// ---------- the score: how unlike the photo a candidate is (0 is the photo itself) ----------
// Each term is in units of the difference that just shows, so they add up. Measured on the photo and on candidates the
// same way, through the same slight blur.
const huber = x => { const a = Math.abs(x); return a <= 1 ? a * a : 2 * a - 1; };
// Roughness and grain weigh less since 2026-10-27 (rough .35 and spec .35 before): a real pen's ragged edge and the
// camera's noise pushed fits toward textured brushes.
const SCORE_UNITS = { iou: .06, l1: .2, width: .08, tone: .06, rough: .6, spec: .5, dry: .05, halo: .05, haloOut: .04, hollow: .12, ramp: .1, streak: .02,
  sat: .35, runs: .35, bloom: .04, speck: .06, pool: .15, ends: .12, haloBand: .05 };
function scoreFit(job, img, detail) {
  const R = camera(job, img), Rf = gauss(R, job.w, job.h, job.commonBlur), F = features(job, Rf), P = job.F;
  // The halo band on the brush as drawn (before the camera's curve, which would hide a pale band), against the
  // photo's only where it has a halo.
  const hbRaw = haloFracOf(job, job.blurSigma > .3 ? gauss(img, job.w, job.h, job.blurSigma) : img, F);
  // Silhouette: the photo's threshold on both, over the whole frame.
  const thr = P.thr; let inter = 0, uni = 0;
  for (let i = 0; i < Rf.length; i++) { const a = job.Df[i] > thr, b = Rf[i] > thr; if (a && b) inter++; if (a || b) uni++; }
  const iou = uni ? inter / uni : 1;
  const Rb = blur(R, job.w, job.h, job.blurR);
  let l1 = 0; for (const i of job.band) l1 += Math.abs(Rb[i] - job.Db[i]);
  l1 = l1 / job.band.length / Math.max(.02, job.Dmean);
  let we = 0; for (let i = 0; i < P.widths.length; i++) we += Math.abs(F.widths[i] - P.widths[i]);
  const width = we / P.widths.length / Math.max(1, P.Wmed);
  let cp = 0, cc = 0, tone = 0; for (let k = 0; k < 24; k++) { cp += P.toneHist[k]; cc += F.toneHist[k]; tone += Math.abs(cp - cc) * .05; }
  const specC = spectrum(job, R); let spec = 0;
  for (let k = 0; k < specC.length; k++) spec += Math.abs(Math.log((specC[k] + .02) / (P.spec[k] + .02)));
  spec /= specC.length;
  const sat = satellites(job, R, thr), ends = job.Wb >= 6 ? [endShape(job, Rf, thr, true), endShape(job, Rf, thr, false)] : job.ends;
  const d = {
    iou: 1 - iou, l1, width, tone, rough: (roughExcess(F) - roughExcess(P)) / Math.max(1, P.Wmed * .1), spec,
    dry: F.dry - P.dry, halo: F.halo - P.halo, haloBand: hbRaw - (job.paper && job.paper.haloReal ? P.haloFrac : 0), haloOut: F.haloOut - P.haloOut, hollow: (F.hollow - P.hollow) / (1 + Math.abs(P.hollow)), ramp: F.ramp - P.ramp,
    streak: F.streak - P.streak, sat: (sat.n - P.satN) / Math.max(1, Math.sqrt(P.satN + 1)), runs: F.runsMean - P.runsMean, bloom: F.bloom - P.bloom,
    speck: F.speck - P.speck, pool: Math.max(F.poolS - P.poolS, P.poolS - F.poolS, 0) * .5 + Math.abs(F.poolE - P.poolE) * .5,
    // Ends count where either side is square: round against tapered is the width profile's business.
    ends: job.Wb >= 6 ? [0, 1].reduce((a, i) => a + (Math.max(ends[i].square, job.ends[i].square) > .65 ? Math.abs(ends[i].square - job.ends[i].square) : 0), 0) / 2 : 0
  };
  let total = 0; const parts = {};
  // A thin line's silhouette can't overlap better than its edge pixels allow: the unit grows as the line thins.
  const units = Object.assign({}, SCORE_UNITS, { iou: Math.max(.05, 1.2 / Math.max(1, P.Wmed / Math.max(1, P.runsMean))) });
  for (const k in units) { const e = huber(d[k] / units[k]) * (k === "iou" || k === "width" || k === "l1" || k === "ends" || k === "haloBand" ? 2 : 1); parts[k] = e; total += e; }
  if (!detail) return total;
  return { total, parts, raw: d, iou, l1, width, tone, spec, F, specC, sat, R, ends };
}

// ---------- Nelder–Mead in the unit cube (values clamped), adaptive coefficients for higher dimensions ----------
function* nelderMead(f, x0, step, maxEvals) {
  const n = x0.length, a = 1, g = 1 + 2 / n, r = .75 - 1 / (2 * n), s = 1 - 1 / n;
  const cl = v => v.map(t => clamp(t));
  let S = [cl(x0)];
  for (let i = 0; i < n; i++) { const v = x0.slice(); v[i] = v[i] + step[i] > 1 ? v[i] - step[i] : v[i] + step[i]; S.push(cl(v)); }
  let F = [];
  for (const v of S) { F.push(f(v)); yield; }
  let evals = S.length;
  while (evals < maxEvals) {
    const ord = F.map((v, i) => i).sort((i, j) => F[i] - F[j]);
    S = ord.map(i => S[i]); F = ord.map(i => F[i]);
    if (F[n] - F[0] < 1e-3 && Math.max(...S.map(v => Math.max(...v.map((t, k) => Math.abs(t - S[0][k]))))) < .01) break;
    const c = new Array(n).fill(0);
    for (let i = 0; i < n; i++) for (let k = 0; k < n; k++) c[k] += S[i][k] / n;
    const at = t => cl(c.map((ck, k) => ck + t * (S[n][k] - ck)));
    const xr = at(-a), fr = f(xr); evals++; yield;
    if (fr < F[0]) { const xe = at(-a * g), fe = f(xe); evals++; yield; if (fe < fr) { S[n] = xe; F[n] = fe; } else { S[n] = xr; F[n] = fr; } }
    else if (fr < F[n - 1]) { S[n] = xr; F[n] = fr; }
    else {
      const out = fr < F[n], xc = at(out ? -r : r), fc = f(xc); evals++; yield;
      if (fc < (out ? fr : F[n])) { S[n] = xc; F[n] = fc; }
      else { for (let i = 1; i <= n; i++) { S[i] = cl(S[i].map((t, k) => S[0][k] + s * (t - S[0][k]))); F[i] = f(S[i]); evals++; yield; } }
    }
  }
  let b = 0; for (let i = 1; i < F.length; i++) if (F[i] < F[b]) b = i;
  return { x: S[b], f: F[b] };
}

// ---------- a tip and a category chosen before the scan (the owner, 2026-10-27: "it should allow you to select a nib
// first to start, and a starting point for what category it's in") ----------
// Tip: the engine's Tip choices the fit can draw; a hard limit. Category: the tool picker's groups, in DrawLayout.PICKER's
// order (the app shows no headers), each with its drawn tools and the tips and media they draw with: those are seeded
// and preferred by CATEGORY_PRIOR (a unit of loss each), and the drawn tools compared are the group's. Either one makes
// the search shorter (CHOSEN_BUDGET of the time). Oils' Loaded oils (wet paint) doesn't read in this engine: Opaque
// paint stands in. Odd Ones (a spirograph, screentone, fading inks) can't be told from one stroke: ink and wash.
const FIT_TIPS = ["round", "chisel", "flat", "split", "multi", "bristles", "hake"];
const CATEGORIES = [
  { label: "Pens", tools: ["Steady line", "Ballpoint", "Fountain pen", "Split nib", "Fude pen", "Stipple pen", "Engraving pen"], tips: ["round", "flat", "split"], media: ["ink"] },
  { label: "Markers", tools: ["Felt marker", "Chisel marker", "Highlighter"], tips: ["chisel", "round"], media: ["ink", "wash"] },
  { label: "Brushes", tools: ["Springy brush", "Swell brush", "Sumi", "Comb", "Flat hake"], tips: ["round", "bristles", "multi", "hake"], media: ["ink", "wash"] },
  { label: "Washes", tools: ["Water brush", "Soft wash", "Wet blooms", "Pooling wash", "Masking fluid", "Wax resist"], tips: ["round", "hake", "bristles"], media: ["wash"] },
  { label: "Dry Media", tools: ["HB pencil", "Soft charcoal", "Soft pastel", "Wax crayon"], tips: ["round", "chisel"], media: ["graphite", "charcoal", "crayon"] },
  { label: "Oils", tools: ["Loaded oils"], tips: ["bristles", "round"], media: ["cover"] },
  { label: "Odd Ones", tools: ["Spirograph", "Fill in", "Filled shape", "Dot tone", "Ben-Day brush", "Lemon juice", "Vanishing ink"], tips: ["round"], media: ["ink", "wash"] }
];
const CATEGORY_PRIOR = 3, CHOSEN_BUDGET = .6;
const categoryOf = label => CATEGORIES.find(c => c.label === label) || null;

// ---------- the candidates: choices from the analysis, dials started where the measurements point ----------
// opts.tip and opts.category: the person's choices (see CATEGORIES); left out, the analysis chooses alone.
function candidates(job, opts = {}) {
  const F = job.F, out = [], cat = categoryOf(opts.category), tipOK = t => !opts.tip || t === opts.tip;
  const lines = F.runsMode >= 2 && F.runsFrac > .45;
  const soft = F.ramp > .28 || F.halo > .12, grey = F.Lb < .62, wide = job.Wb > 30, inkLike = !!(job.paper && job.paper.inkLike);
  // Tips, each with a prior (higher: tried first).
  const tips = [];
  if (lines) { if (F.runsMode === 2) tips.push([3, "split"], [2.5, "multi", { count: 2 }]); else tips.push([3, "multi", { count: Math.min(5, F.runsMode) }]); }
  if (job.nib.shaped) { if (job.nib.engA < .33) tips.push([2.8, "flat"]); if (job.nib.engA > .25) tips.push([2.6, "chisel"]); }
  // A square end: a flat head (Hake follows the stroke, so its ends are cut across it), or a chisel.
  const square = !lines && job.Wb >= 6 && Math.max(job.ends[0].square, job.ends[1].square) > .65;
  // Hake has no taper: with one end square and the other pointed, a flat nib turned across the stroke does it.
  const mixed = square && Math.min(job.ends[0].square, job.ends[1].square) < .3;
  if (square) tips.push([mixed ? 2.6 : 3.4, "hake"], [mixed ? 3.4 : 2.8, "flat", { squareCut: true }], [2.4, "chisel", { squareCut: true }]);
  tips.push([lines ? 1 : square ? 2 : 2.7, "round"]);
  if (F.streak > .05 || (F.dry > .08 && F.runsMean > 1.3) || (lines && F.runsFrac < .7)) tips.push([2.2, "bristles"]);
  if (!square && wide && (F.streak > .04 || F.dry > .05) && F.l80In < job.Wb) tips.push([1.5, "hake"]);
  // The category's tips and the tip chosen, where the analysis didn't offer them.
  for (const t of [...(cat ? cat.tips : []), opts.tip].filter(Boolean)) if (!tips.some(x => x[1] === t)) tips.push([2, t, t === "multi" ? { count: clamp(F.runsMode, 2, 5) } : undefined]);
  // Media, each with a prior.
  const media = [];
  const rough = roughExcess(F) > .1 || F.spec[0] > .08;
  // A wide, pale mark is a wash: grey ink would draw like it here but not on the page.
  if (!(F.Lb < .3 && job.Wb > 20)) media.push([grey && (soft || wide) ? 1 : grey ? 1.8 : rough ? 2.2 : 3, "ink"]);
  // Wet ink's wash round the line only where the photo has a halo darker than bare paper (paperModel's control).
  const halo = job.paper ? job.paper.haloReal : F.halo > .06;
  // Crisp ink (paperModel's best guess) gets no wash and no wet ink: their soft greys would be pressed out of the
  // score's black and white but show on the page.
  if (halo && !inkLike) media.push([grey ? 1.5 : 3.2, "ink", { wetInk: true }]);
  if (!inkLike) media.push([grey || soft || wide ? 2.8 : 1, "wash"]);
  const paleWide = F.Lb < .3 && job.Wb > 20;
  // Crisp ink first: charcoal only a fallback there, pencil not at all (the curve has pressed out its greys).
  if (!lines && !paleWide) { media.push([inkLike ? .9 : (roughExcess(F) > .05 || F.spec[0] > .06 || F.satN > .3) && F.Lb > .35 ? 2.4 : .8, "charcoal"]);
    // Wax crayon (the lab's new modules): a rough, toothy mark that isn't crisp ink.
    if (!inkLike) media.push([rough ? 2 : .6, "crayon"]);
    if (job.Wb < 30 && !inkLike) media.push([grey ? 2.4 : .9, "graphite"]); }
  if (cat) for (const m of cat.media) if (!media.some(x => x[1] === m && !x[2])) media.push([2, m]);
  for (const [pt, tip, te] of tips) for (const [pm, medium, me] of media) {
    if (!tipOK(tip)) continue;
    if (tip === "hake" && medium !== "wash" && medium !== "ink") continue;
    if ((tip === "split" || tip === "multi") && medium !== "ink") continue;
    if (medium === "crayon" && tip !== "round" && tip !== "chisel") continue;
    // Charcoal and pencil always sit on some paper's tooth: cold press to start with (the paper step tries the rest).
    const inCat = cat && cat.tips.includes(tip) && cat.media.includes(medium);
    const st = Object.assign({ tip, medium, life: "wet", paper: TOOTHY[medium] ? "cold press" : "plain", prior: pt + pm + (inCat ? CATEGORY_PRIOR : 0) }, te, me);
    out.push(st);
    if (medium === "wash" || st.wetInk) out.push(Object.assign({}, st, { life: "dry", prior: st.prior - 1.2 }));
  }
  // A tip chosen that draws with none of the media (a split nib in charcoal): in ink.
  if (!out.length) out.push({ tip: opts.tip || "round", medium: "ink", life: "wet", paper: "plain", prior: 3 });
  out.sort((a, b) => b.prior - a.prior);
  return out.slice(0, opts.tip || cat ? 6 : 10);
}
// The dials that make a structure what it is: searched already in the coarse look.
function keyDials(st) {
  const k = [];
  if (st.wetInk) k.push("medium.wet");
  if (st.medium === "wash") k.push("medium.wet", "tip.soft");
  if (st.medium === "charcoal") k.push("medium.spread", "medium.hold");
  if (st.medium === "graphite") k.push("medium.grade", "paper.tooth");
  if (st.medium === "crayon") k.push("medium.wax", "paper.tooth");
  if (st.tip === "split") k.push("tip.gap");
  if (st.tip === "multi") k.push("tip.reach");
  if (st.tip === "bristles") k.push("tip.splay", "tip.dryness");
  if (st.tip === "hake") k.push("tip.dry");
  if ((st.tip === "flat" || st.tip === "chisel") && st.squareCut) k.push("tip.angle");
  return k.slice(0, 3);
}
// The dials a structure frees for the search, each with its starting value.
function freeDials(job, st) {
  const F = job.F, x = {}, k = Math.sqrt(Math.max(1, (job.Wb || 8) / 16));
  x.sizeK = 0; x.shade = clamp(st.medium === "wash" ? F.Lb / .45 : F.Lb, .12, 1);
  // Pressure → size wide enough for the photo's widths: the least that covers its range (thin stretch to widest)
  // at a usual pressure of 0.6, under the brush's own width law.
  if (st.tip !== "hake" && job.widthRange) x["hand.ps"] = pressureFor(job.widthRange, LINE_MEDIA[st.medium] && !st.wetInk);
  const tip = st.tip, taperIn = clamp((job.zoneIn ?? F.l80In) / (.665 * 70 * k)), taperOut = clamp((job.zoneOut ?? F.l80Out) / (.665 * 90 * k));
  // A square end has no taper (the width runs right to the edge).
  if (tip !== "hake" && tip !== "bristles") { x["tip.taperIn"] = taperIn < .06 || squareEnd(job, true) ? 0 : taperIn; x["tip.taperOut"] = taperOut < .06 || squareEnd(job, false) ? 0 : taperOut; }
  // Dry brush from the gaps in the body (between parallel lines the gaps are the paper, not dryness).
  if (tip === "round" || tip === "chisel" || tip === "flat" || tip === "multi" || tip === "split") x["tip.dry"] = F.runsMode >= 2 ? 0 : clamp(F.dry * 2.2);
  if (tip === "round" || tip === "chisel" || tip === "flat" || tip === "multi") x["tip.wobble"] = clamp((F.wob - STEADY.wobFloor) * 6);
  // A square end cut by a flat nib: the nib held along the cut (the engine's angle runs 20–70°, the nearest of it).
  const sqE = job.ends ? (job.ends[1].square >= job.ends[0].square ? job.ends[1] : job.ends[0]) : null;
  if ((tip === "chisel" || tip === "flat") && !job.nib.shaped && sqE && sqE.square > .65) {
    let phi = (Math.atan2(sqE.dir[1], sqE.dir[0]) * 180 / Math.PI + 90 + sqE.slant) % 180; if (phi < 0) phi += 180;
    const dist = v => v < 20 ? 20 - v : v > 70 ? v - 70 : 0, opts = [phi, phi - 180, phi + 180].sort((a, b) => dist(a) - dist(b));
    x["tip.angle"] = clamp(opts[0], 20, 70); x["tip.aspect"] = rangeOf("tip", tip, "aspect")[0];
  }
  if ((tip === "chisel" || tip === "flat") && job.nib.shaped) { x["tip.aspect"] = clamp(job.nib.engA, ...rangeOf("tip", tip, "aspect")); x["tip.angle"] = job.nib.angle; }
  if (tip === "split") { x["tip.gap"] = .55; x["tip.aspect"] = .42; x["tip.angle"] = job.nib.shaped ? job.nib.angle : 40; }
  if (tip === "multi") x["tip.reach"] = .6;
  if (tip === "bristles") { x["tip.splay"] = .45; x["tip.cohesion"] = .55; x["tip.dryness"] = clamp(.1 + F.dry * 2); }
  if (tip === "hake") { x["tip.dry"] = clamp(F.streak * 8 + F.dry * 2); x["tip.soft"] = .2; x["tip.angle"] = 75; }
  const mk = st.medium;
  if (mk === "ink") {
    x["medium.pool"] = squareEnd(job, true) ? 0 : clamp((F.poolS - 1.08) / .9);
    if (F.satN > .3) x["medium.splatter"] = clamp(F.satN / 3);
    if (st.wetInk) { x["medium.wet"] = clamp(.2 + F.halo * 2, .1, .8); x["medium.line"] = .8; x["medium.bleed"] = clamp(F.halo * 4); x["tip.soft"] = .5; }
  }
  if (mk === "wash") { x["medium.wet"] = F.ramp > .2 ? .85 : .6; x["medium.pool"] = clamp(F.hollow * 2); x["medium.bleed"] = clamp(F.halo * 3); x["medium.bloom"] = clamp(F.bloom * 6); x["tip.soft"] = clamp(F.ramp * 1.5); }
  if (mk === "charcoal") { x["medium.spread"] = .3; x["medium.hold"] = clamp(F.Lb); }
  // Graphite's darkest is (70 + 145 grade) / 255; the paper's tooth lets about 60 % of it show.
  if (mk === "graphite") { x["medium.grade"] = clamp((F.Lb / .6 * 255 - 70) / 145, .1, 1); x["medium.spread"] = .12; }
  if (mk === "crayon") x["medium.wax"] = clamp(1 - F.dry * 3, .2, 1);
  if (st.paper !== "plain") x["paper.tooth"] = mk === "graphite" ? .4 : .5;
  if (st.life === "wet" && x["tip.taperOut"] > 0) x["life.window"] = clamp(F.l80Out / 12 + 2, 1, 10);
  // Only dials the chosen choices have.
  const kOf = { tip: st.tip, medium: st.medium, life: st.life, paper: st.paper || "plain" };
  for (const key of Object.keys(x)) { const [p, d] = key.split("."); if (kOf[p] && !CHOICES[p].find(c => c.k === kOf[p]).dials.includes(d)) delete x[key]; }
  return x;
}
function pressureFor([rmin, rmax], line) {
  const f = (P, p) => line ? lerp(1 - .75 * P, 1 + .6 * P, p) : (lerp(.6, .06, P) + (1 - lerp(.6, .06, P)) * Math.pow(p, 1.2));
  for (let P = 0; P <= 1.0001; P += .05) if (f(P, 0) / f(P, FIT.pMid) <= rmin * 1.02 && f(P, 1) / f(P, FIT.pMid) >= rmax * .98) return P;
  return 1;
}
// Packs a dial map into the unit cube and back.
function packer(st, x0) {
  const keys = Object.keys(x0), span = keys.map(key => {
    if (key === "sizeK") return [-.7, .7]; if (key === "shade") return [.12, 1]; if (key === "hand.ps") return [0, 1];
    const [p, d] = key.split("."); return rangeOf(p, p === "tip" ? st.tip : p === "medium" ? st.medium : p === "life" ? st.life : st.paper || "plain", d);
  });
  return {
    keys,
    toU: x => keys.map((k, i) => clamp((x[k] - span[i][0]) / (span[i][1] - span[i][0]))),
    fromU: u => Object.fromEntries(keys.map((k, i) => [k, lerp(span[i][0], span[i][1], clamp(u[i]))]))
  };
}

// ---------- the search ----------
// A generator: each yield is a progress report; the caller (the worker, or the page in slices) runs it to the end.
function* fitStroke(job, opts = {}) {
  const seed = opts.seed ?? 0x5eed;
  const budget = (opts.budgetMs ?? FIT.budgetMs) * (opts.tip || opts.category ? CHOSEN_BUDGET : 1), t0 = Date.now(), elapsed = () => Date.now() - t0;
  let evals = 0, best = null;
  const byStruct = new Map();
  const evalX = (st, x) => {
    evals++;
    const B = brushOf(st, x, seed), size = clamp(baseSize(job, B) * Math.exp(x.sizeK || 0), 1, 360);
    // What the measurements expected counts a little: a unit per step of prior below the likeliest.
    if (job.paper && job.paper.inkLike) x.shade = 1;
    const l = scoreFit(job, renderFit(job, B, size, x.shade)) + simplicity(st, x) + Math.max(0, topPrior - (st.prior ?? topPrior));
    if (!best || l < best.l) best = { l, st, x: Object.assign({}, x), B, size, shade: x.shade };
    const key = structWords(st), b = byStruct.get(key);
    if (!b || l < b.l) byStruct.set(key, { l, st, x: Object.assign({}, x) });
    return l;
  };
  const cands = candidates(job, opts).filter(opts.filter || (() => true)), topPrior = Math.max(...cands.map(c => c.prior));
  { const st = cands[0], x = freeDials(job, st), B = brushOf(st, x, seed); cameraBlur(job, B, baseSize(job, B), x.shade); }
  // 1. Coarse: every structure, a short search over size, shade and the one or two dials that make it what it is.
  const tried = [];
  for (const st of cands) {
    const x0 = freeDials(job, st), sub = { sizeK: x0.sizeK, shade: x0.shade };
    for (const k of keyDials(st)) if (k in x0) sub[k] = x0[k];
    const n = Object.keys(sub).length, pk = packer(st, sub), f = u => evalX(st, Object.assign({}, x0, pk.fromU(u)));
    const g = nelderMead(f, pk.toU(sub), new Array(n).fill(.15), 3 + 2 * n); let r;
    while (!(r = g.next()).done) yield { phase: "Trying " + structWords(st), evals, loss: best.l, best };
    tried.push({ st, f: r.value.f, x: Object.assign({}, x0, pk.fromU(r.value.x)) });
    if (elapsed() > budget * .3 && tried.length >= 2) break;
  }
  tried.sort((a, b) => a.f - b.f);
  // 2. Closer: the best four (within twice the best), every free dial, a short search each.
  const closer = tried.filter((t, i) => i < 4 && t.f < tried[0].f * 2);
  for (const t of closer) {
    const pk = packer(t.st, t.x), n = pk.keys.length, f = u => evalX(t.st, pk.fromU(u)), tEnd = Math.max(elapsed() + 250, budget * .55);
    const g = nelderMead(f, pk.toU(t.x), new Array(n).fill(.12), 10 + 3 * n); let r;
    while (!(r = g.next()).done) { yield { phase: "Closer: " + structWords(t.st), evals, loss: best.l, best }; if (elapsed() > tEnd) break; }
  }
  // 3. Fine: the best two now (one if the second is far behind), every free dial, the rest of the time.
  const ranked = closer.map(t => byStruct.get(structWords(t.st))).sort((a, b) => a.l - b.l);
  const finalists = ranked.filter((t, i) => i === 0 || (i === 1 && t.l < ranked[0].l * 1.35));
  const share = (budget * .85 - elapsed()) / finalists.length;
  for (const t of finalists) {
    const pk = packer(t.st, t.x), n = pk.keys.length, f = u => evalX(t.st, pk.fromU(u));
    const tEnd = elapsed() + Math.max(300, share);
    const g = nelderMead(f, pk.toU(t.x), new Array(n).fill(.08), 40 + 14 * n); let r;
    while (!(r = g.next()).done) { yield { phase: "Refining " + structWords(t.st), evals, loss: best.l, best }; if (elapsed() > tEnd) break; }
  }
  // 4. Paper: where grain shows, each scan with its tooth searched.
  const F = job.F, grainy = F.spec[0] > .08 || F.dry > .04 || TOOTHY[best.st.medium];
  if (grainy && elapsed() < budget) {
    const base = best;
    for (const paper of ["cold press", "felt", "laid", "canvas", "crumpled"]) {
      const st = Object.assign({}, base.st, { paper }), x = Object.assign({}, base.x, { "paper.tooth": .5 });
      for (const tooth of [.3, .7]) { x["paper.tooth"] = tooth; evalX(st, x); yield { phase: "Paper: " + paper, evals, loss: best.l, best }; }
      if (elapsed() > budget * 1.1) break;
    }
    if (best.st.paper !== "plain") {
      const pk = packer(best.st, best.x), keys = ["paper.tooth", "sizeK", "shade"].filter(k => k in best.x);
      const sub = Object.fromEntries(keys.map(k => [k, best.x[k]])), pk2 = packer(best.st, sub), st = best.st, xb = best.x;
      const g = nelderMead(u => evalX(st, Object.assign({}, xb, pk2.fromU(u))), pk2.toU(sub), keys.map(() => .1), 14); let r;
      while (!(r = g.next()).done) yield { phase: "Paper tooth", evals, loss: best.l, best };
      void pk;
    }
  }
  // A near tie (within 8 %) goes to what the measurements expected.
  const near = [...byStruct.values()].filter(t => t.l <= best.l * 1.08).sort((a, b) => (b.st.prior ?? 0) - (a.st.prior ?? 0))[0];
  if (near && (near.st.prior ?? 0) > (best.st.prior ?? 0)) {
    const B = brushOf(near.st, near.x, seed);
    best = { l: near.l, st: near.st, x: near.x, B, size: clamp(baseSize(job, B) * Math.exp(near.x.sizeK || 0), 1, 360), shade: near.x.shade };
  }
  const res = finish(job, best, evals, elapsed());
  res.tried = [...byStruct.values()].sort((a, b) => a.l - b.l).map(t => ({ words: structWords(t.st), loss: t.l }));
  res.drawn = yield* nearestDrawn(job, res.shade, opts);
  return res;
}
// ---------- the app's drawn tools (DrawLayout.PICKER's Basic tools, DRAWN_TOOLS from the build) against the photo ----------
// Each as it is, only its size free (at the fit's grey: the app draws every brush in the one grey): three sizes each,
// then the closest five to a finer size. Lowest score first; tools whose codes this engine can't read are left out.
// A category chosen keeps to its tools, a tip to the tools with that tip (all of them if none is left).
function* nearestDrawn(job, shade, opts = {}) {
  let tools = [];
  for (const [name, code] of typeof DRAWN_TOOLS === "undefined" ? [] : DRAWN_TOOLS) {
    const r = readAnyCode(code); if (!r || !r.b) continue;
    tools.push({ name, B: r.b, base: baseSize(job, r.b), l: Infinity, k: 0 });
  }
  const cat = categoryOf(opts.category), kept = tools.filter(t => (!cat || cat.tools.includes(t.name)) && (!opts.tip || t.B.tip.k === opts.tip));
  if (kept.length) tools = kept;
  const tryK = (t, k) => { const l = scoreFit(job, renderFit(job, t.B, clamp(t.base * Math.exp(k), 1, 360), shade)); if (l < t.l) { t.l = l; t.k = k; } };
  for (const t of tools) { for (const k of [-.35, 0, .35]) tryK(t, k); yield { phase: "Drawn tools: " + t.name, loss: t.l }; }
  tools.sort((a, b) => a.l - b.l);
  for (const t of tools.slice(0, 5)) { const k0 = t.k; for (const k of [k0 - .18, k0 + .18, k0 - .09, k0 + .09]) tryK(t, k); yield { phase: "Drawn tools: " + t.name, loss: t.l }; }
  return tools.sort((a, b) => a.l - b.l).map(t => ({ name: t.name, loss: t.l }));
}
// A small cost for every effect that's on, so a dial the photo can't show stays off rather than wandering.
const SIMPLE = { "tip.dry": .8, "tip.wobble": 1.2, "medium.pool": .5, "medium.splatter": .8, "medium.bleed": .6, "medium.bloom": .6, "medium.backrun": .8,
  "medium.offset": 1, "medium.chaos": 1, "paper.tooth": .6, "tip.taperIn": .2, "tip.taperOut": .2, "hand.ps": .05 };
function simplicity(st, x) {
  let s = (st.paper && st.paper !== "plain" && !TOOTHY[st.medium] ? .6 : 0) + (st.life === "dry" ? .4 : 0) + (st.wetInk ? .6 : 0);
  for (const k in SIMPLE) if (x[k] > 0) s += SIMPLE[k] * x[k];
  // A pale ink line or a pale charcoal mark is more likely a wash or a pencil.
  if (st.medium === "ink" && x.shade < .55) s += (.55 - x.shade) * 10;
  if (st.medium === "charcoal" && x.shade < .5) s += (.5 - x.shade) * 10;
  return s;
}
// Faint dials off, a style that fits, a name; the size and shade to draw it with; the scores against the photo.
function finish(job, best, evals, ms) {
  const B = cloneBrush(best.B);
  for (const p of ["tip", "medium"]) for (const [k, v] of Object.entries(B[p].d)) { const s = DIALS[p][k]; if (s && !s.choices && s.min === 0 && v < .04 && !(p === "medium" && (k === "line" || k === "wet"))) B[p].d[k] = 0; }
  B.style = styleFor(B);
  normBrush(B);
  // Named for what stands out in it ("rake-fig"; parts.js describedName), else adjective-produce.
  B.name = describedName(B, freshName());
  const img = renderFit(job, B, best.size, best.shade), sc = scoreFit(job, img, true);
  return { B, code: encodeM(B), size: Math.round(best.size * 10) / 10, shade: best.shade, struct: best.st, x: best.x, loss: sc.total, score: sc, img, evals, ms };
}
function styleFor(B) {
  const t = B.tip.k, m = B.medium.k;
  if (m === "charcoal") return "Charcoal";
  if (m === "graphite") return "Pencil";
  if (m === "crayon") return "Pastels";
  if (m === "cover") return t === "bristles" ? "Oils" : "Gouache";
  if (t === "hake") return "Hake";
  if (t === "bristles") return "Dry brush";
  if (m === "wash") return "Watercolor";
  if (m === "ink" && B.medium.d.wet > .4) return "Ink wash";
  if (t === "split" || t === "flat") return "Dip pen";
  if (t === "chisel") return "Marker";
  if (t === "multi") return "Anything goes";
  return B.tip.d.taperIn + B.tip.d.taperOut > .4 ? "Brush pen" : "Fineliner";
}
const TIP_WORDS = { round: "round", chisel: "chisel", flat: "flat nib", hake: "hake", split: "split nib", bristles: "bristle brush" };
const MEDIUM_WORDS = { ink: "ink", wash: "wash", cover: "gouache", charcoal: "charcoal", graphite: "graphite", crayon: "crayon" };
const structWords = st => [st.tip === "multi" ? (st.count || 3) + "-line rake" : TIP_WORDS[st.tip], st.wetInk ? "wet ink" : MEDIUM_WORDS[st.medium],
  st.life === "dry" ? "slow drying" : "", st.paper && st.paper !== "plain" ? st.paper : ""].filter(Boolean).join(", ");
// How blurred the edges are, as a Gaussian's sigma in px: from the steepest slope across each edge, row by row.
function edgeSigma(job, img) {
  const F = features(job, img), S = sampleStrip(job.ts, img, job.w), C = job.ts.cols, T = job.ts.T, [b0, b1] = job.body, wd = F.widths, slopes = [];
  for (let i = b0; i <= b1; i++) {
    if (wd[i] < Math.max(2.5, .8 * F.Wmed)) continue;
    for (const [e, sgn] of [[F.wl[i], -1], [F.wr[i], 1]]) {
      let best = 0;
      for (let t = Math.floor(Math.abs(e) - 3); t <= Math.ceil(Math.abs(e) + 3); t++) {
        const j = T + sgn * t;
        if (j < 1 || j >= C - 1) continue;
        best = Math.max(best, Math.abs(S[i * C + j + 1] - S[i * C + j - 1]) / 2);
      }
      if (best > 0) slopes.push(best);
    }
  }
  const slope = median(slopes);
  return slope > 0 ? F.Lb / (slope * Math.sqrt(2 * Math.PI)) : 0;
}
// Runs a fit to the end at once (the checks, and Node).
function fitNow(job, opts) { const g = fitStroke(job, opts); let r; while (!(r = g.next()).done); return r.value; }
// A brush and size from a finished fit, to draw with elsewhere.
function brushFromFit(r) { return { B: r.B, size: r.size, shade: r.shade }; }

;
// ===================================================================================================================
// Import a paper: a photo of a sheet becomes a grain the engine can use, in the app's own format (BrushTexture.java,
// PaperGrain.scan: 256 × 256, 8-bit, rows top to bottom, wrapping at the edges; normalised to mean 128, spread 52).
//   lighting divided out (the photo over a heavy blur of itself) → the grain's size matched to the app's papers (by
//   the distance its texture stays alike) → high-passed (no blotches when it repeats) → made seamless (each axis
//   cross-faded near its edges with itself shifted half a tile, whose wrap is seamless, keeping the spread) → 8 bits.
// ===================================================================================================================
const PAPER_SIDE = 256;
// How far the grain stays alike: the lag (px) where the high-passed texture's autocorrelation falls to a half,
// across and down, averaged.
function grainLength(t, w, h) {
  const lo = gauss(t, w, h, 8), hp = new Float32Array(w * h);
  for (let i = 0; i < hp.length; i++) hp[i] = t[i] - lo[i];
  const m = 8, ac = lag => {
    let s = 0, v = 0;
    for (let y = m; y < h - m - lag; y += 2) for (let x = m; x < w - m - lag; x += 2) { const i = y * w + x, a = hp[i]; v += a * a; s += a * (hp[i + lag] + hp[i + lag * w]) / 2; }
    return v > 0 ? s / v : 0;
  };
  let prev = 1;
  for (let lag = 1; lag <= 24; lag++) { const a = ac(lag); if (a <= .5) return lag - 1 + (prev - .5) / Math.max(1e-6, prev - a); prev = a; }
  return 24;
}
// The app's papers' grain length (the scans as the page has them): what an imported paper is matched to.
function appGrain() {
  const out = {};
  for (const [k, t] of Object.entries(TEXTURES)) if (t && t.length === PAPER_SIDE * PAPER_SIDE) out[k] = grainLength(t, PAPER_SIDE, PAPER_SIDE);
  return out;
}
// gray: { w, h, g } (0..1). opts: { grain: × on the matched size (1), box: {x, y, s} square in the photo }.
function makePaper(gray, opts = {}) {
  const { w, h, g } = gray, N = PAPER_SIDE;
  const side = opts.box ? Math.round(opts.box.s) : Math.round(Math.min(w, h) * .9);
  const x0 = opts.box ? clamp(Math.round(opts.box.x), 0, w - side) : Math.round((w - side) / 2), y0 = opts.box ? clamp(Math.round(opts.box.y), 0, h - side) : Math.round((h - side) / 2);
  let sq = cropOf(g, w, h, x0, y0, side, side);
  // 1. Lighting: the photo over a heavy blur of itself (a lamp's falloff, a shadow across the sheet).
  const light = gauss(sq, side, side, Math.max(4, side / 10));
  for (let i = 0; i < sq.length; i++) sq[i] = sq[i] / Math.max(.02, light[i]);
  // 2. The grain's size: the photo's grain length against the app's papers' (median), times the user's factor.
  const ref = Object.values(appGrain()), refL = ref.length ? median(ref) : 3;
  const sample = Math.min(side, 512), probe = sample < side ? resample(sq, side, side, sample, sample) : sq;
  const photoL = grainLength(probe, sample, sample) * side / sample;
  let k = refL / Math.max(.5, photoL) * (opts.grain || 1);        // texture px per photo px
  let region = Math.round(N / k);
  if (region > side) { region = side; k = N / side; }
  if (region < 32) { region = 32; k = N / 32; }
  const rx = Math.round((side - region) / 2), ry = rx;
  let t = resample(cropOf(sq, side, side, rx, ry, region, region), region, region, N, N);
  // 3. High-pass: nothing broader than about a tenth of the tile, so it repeats without blotches.
  const low = gauss(t, N, N, 14);
  for (let i = 0; i < t.length; i++) t[i] -= low[i];
  // 4. Seamless: per axis, cross-faded near the edges with itself shifted half a tile.
  const fade = 56;
  const axis = (src, horizontal) => {
    const out = new Float32Array(N * N);
    for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
      const p = horizontal ? x : y, wgt = clamp(Math.min(p + .5, N - .5 - p) / fade);
      const sx = horizontal ? (x + N / 2) % N : x, sy = horizontal ? y : (y + N / 2) % N;
      out[y * N + x] = (wgt * src[y * N + x] + (1 - wgt) * src[sy * N + sx]) / Math.sqrt(wgt * wgt + (1 - wgt) * (1 - wgt));
    }
    return out;
  };
  t = axis(axis(t, true), false);
  // 5. As the app's scans: mean 128, spread 52, 0 to 255.
  const m = mean(t), s = Math.max(1e-6, std(t)), bytes = new Uint8Array(N * N), floats = new Float32Array(N * N);
  for (let i = 0; i < t.length; i++) { bytes[i] = Math.round(clamp(128 + 52 * (t[i] - m) / s, 0, 255)); floats[i] = bytes[i] / 255; }
  return { bytes, floats, box: { x: x0, y: y0, s: side }, region: { x: x0 + rx, y: y0 + ry, s: region }, k, photoL, refL, stats: paperStats(bytes) };
}
// Mean, spread, the grain length, and the seam: the step across the tile's wrap against the steps inside it (1 is seamless).
function paperStats(bytes) {
  const N = PAPER_SIDE, f = Float32Array.from(bytes, v => v / 255);
  let s = 0; for (const v of bytes) s += v;
  const m = s / bytes.length; let v2 = 0; for (const v of bytes) v2 += (v - m) * (v - m);
  let inner = 0, ni = 0, wrap = 0, nw = 0;
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const a = bytes[y * N + x], r = bytes[y * N + (x + 1) % N], d = bytes[((y + 1) % N) * N + x];
    if (x === N - 1) { wrap += Math.abs(a - r); nw++; } else { inner += Math.abs(a - r); ni++; }
    if (y === N - 1) { wrap += Math.abs(a - d); nw++; } else { inner += Math.abs(a - d); ni++; }
  }
  return { mean: m, spread: Math.sqrt(v2 / bytes.length), grain: grainLength(f, N, N), seam: (wrap / nw) / Math.max(1e-6, inner / ni) };
}

;
// ===================================================================================================================
// Sample photos, made here (the page's "Try a sample" and the checks use the same ones):
//  - engine strokes: drawn by the engine with a known brush, then "photographed": rescaled, blurred, a warm uneven
//    light, paper grain and sensor noise, so a fit can be checked against the truth;
//  - drawn marks: made without the engine (a sumi stroke with hair streaks, a fountain pen that feathers, pencil on
//    cold press, an italic nib, two parallel lines, a watercolour stroke with a darker rim).
// ===================================================================================================================
// An S curve across a w × h box, every 1.5 px or more, light at the ends and firm in the middle, about 1 px a ms.
function samplePath(w, h, o = {}) {
  const raw = [], n = 400, x0 = o.x0 ?? w * .12, x1 = o.x1 ?? w * .88, amp = o.amp ?? h * .22;
  let d = 0, last = null;
  for (let i = 0; i <= n; i++) {
    const u = i / n, x = lerp(x0, x1, u), y = h / 2 + amp * Math.sin(u * Math.PI * (o.waves ?? 1.3) + (o.phase ?? .3));
    if (last) { const seg = Math.hypot(x - last[0], y - last[1]); if (seg < 1.5 && i < n) continue; d += seg; }
    last = [x, y]; raw.push({ x, y, u, d });
  }
  return raw.map(p => ({ x: p.x, y: p.y, p: o.press ? o.press(p.u) : .35 + .45 * Math.pow(Math.sin(Math.PI * p.u), .6), t: p.d, tx: NaN, ty: NaN, s: p.d / d }));
}
// A long, near-vertical stroke down a w × h box, gently bowed.
function verticalPath(w, h, o = {}) {
  const pts = []; let d = 0, last = null;
  for (let i = 0; i <= 300; i++) {
    const u = i / 300, x = w / 2 + w * .12 * Math.sin(u * Math.PI * .9 + .4) - w * .05, y = lerp(h * .08, h * .92, u);
    if (last) d += Math.hypot(x - last[0], y - last[1]);
    last = [x, y]; pts.push({ x, y, u, d });
  }
  return pts.map(p => ({ x: p.x, y: p.y, p: o.press ? o.press(p.u) : .6, t: p.d, tx: NaN, ty: NaN, s: p.d / d }));
}
// A soft ellipse into a coverage buffer: rx along the angle, ry across it.
function stampEllipse(buf, w, h, x, y, rx, ry, ang, amt) {
  const R = Math.ceil(Math.max(rx, ry) + 2), c = Math.cos(ang), s = Math.sin(ang);
  for (let py = Math.max(0, Math.floor(y - R)); py <= Math.min(h - 1, Math.ceil(y + R)); py++) for (let px = Math.max(0, Math.floor(x - R)); px <= Math.min(w - 1, Math.ceil(x + R)); px++) {
    const dx = px + .5 - x, dy = py + .5 - y, u = (dx * c + dy * s) / Math.max(.5, rx), v = (-dx * s + dy * c) / Math.max(.5, ry), r = Math.sqrt(u * u + v * v);
    const a = clamp((1 - r) * Math.max(.5, Math.min(rx, ry)) + .5) * amt, i = py * w + px;
    if (a > buf[i]) buf[i] = a;
  }
}
// A bar across the stroke (a flat brush's edge): half-length L across the travel, a pixel thick, anti-aliased.
function stampBar(buf, w, h, x, y, L, ang, amt) {
  const R = Math.ceil(L + 2), c = Math.cos(ang), s = Math.sin(ang);
  for (let py = Math.max(0, Math.floor(y - R)); py <= Math.min(h - 1, Math.ceil(y + R)); py++) for (let px = Math.max(0, Math.floor(x - R)); px <= Math.min(w - 1, Math.ceil(x + R)); px++) {
    const dx = px + .5 - x, dy = py + .5 - y, along = dx * c + dy * s, across = -dx * s + dy * c;
    const a = clamp(L - Math.abs(across) + .5) * clamp(1 - Math.abs(along) + .5) * amt, i = py * w + px;
    if (a > buf[i]) buf[i] = a;
  }
}
function alongPath(pts, every, fn) {
  for (let k = 1; k < pts.length; k++) {
    const a = pts[k - 1], b = pts[k], seg = Math.hypot(b.x - a.x, b.y - a.y), n = Math.max(1, Math.ceil(seg / every)), ang = Math.atan2(b.y - a.y, b.x - a.x);
    for (let j = 0; j < n; j++) { const t = j / n; fn(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.s, b.s, t), ang); }
  }
}
// The camera: darkness (0 paper .. 1 black) to a warm RGB photo under uneven light, with paper grain and noise.
function photographMark(dark, w, h, o = {}) {
  const r = rng(o.seed || 5), k = o.scale || 1, W = Math.round(w * k), H = Math.round(h * k);
  let d = resample(dark, w, h, W, H);
  // A real pen's ragged edge and a hand's wobble (the noise check): the mark pushed about by o.ragged px over a few px,
  // and by o.wobble px over about 60 px.
  if (o.ragged || o.wobble) {
    const f = [3, 3, 60, 60].map(c => valueNoise(W, H, c * k, r)), src = d; d = new Float32Array(W * H);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = y * W + x, a = 2 * k * (o.ragged || 0), b = 2 * k * (o.wobble || 0);
      d[i] = bilinear(src, W, H, clamp(x + a * (f[0][i] - .5) + b * (f[2][i] - .5), 0, W - 1.001), clamp(y + a * (f[1][i] - .5) + b * (f[3][i] - .5), 0, H - 1.001)); }
  }
  d = gauss(d, W, H, o.blur ?? 1.1);
  const grain = valueNoise(W, H, 2.2, r), lumps = valueNoise(W, H, 30, r), px = new Uint8Array(W * H * 3), ink = o.ink ?? .9, nz = o.noise ?? .018;
  // Textured paper: a coarser tooth, and faint grey speckle (fibres, flecks) all over the sheet.
  const tex = o.texture ? valueNoise(W, H, 5, r) : null, fleck = o.speckle ? new Float32Array(W * H) : null;
  if (fleck) for (let k = 0; k < W * H * o.speckle; k++) { const x = r() * W, y = r() * H, rr = .6 + r() * 1.4, a = .05 + r() * .12; for (let yy = Math.floor(y - 2); yy <= y + 2; yy++) for (let xx = Math.floor(x - 2); xx <= x + 2; xx++) { if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue; const dd = Math.hypot(xx + .5 - x, yy + .5 - y); if (dd < rr) fleck[yy * W + xx] = Math.max(fleck[yy * W + xx], a * clamp(rr - dd)); } }
  const n = () => (r() + r() + r() - 1.5) * nz;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x, u = x / W - .5, v = y / H - .5;
    const light = (.92 - .22 * (u * .8 + v * .6) - .35 * (u * u + v * v)) * (1 + .03 * (lumps[i] - .5));
    const paper = (1 + (o.grain ?? .05) * (grain[i] - .5) + (tex ? o.texture * (tex[i] - .5) : 0)) * (1 - (fleck ? fleck[i] : 0));
    const t = paper * (1 - ink * clamp(d[i], 0, 1.2));
    px[i * 3] = clamp(light * t + n()) * 255; px[i * 3 + 1] = clamp(light * t * .96 + n()) * 255; px[i * 3 + 2] = clamp(light * t * .88 + n()) * 255;
  }
  return { w: W, h: H, px };
}
// A brush for a sample: parts over defaults, the hand's pressure → size.
function sampleBrush(parts, ps) {
  const B = { name: "truth", seed: 4242, style: "Anything goes" };
  for (const p of PARTS) B[p] = freshPart(p, CHOICES[p][0].k);
  B.life = freshPart("life", "wet");
  for (const [p, v] of Object.entries(parts)) { B[p] = freshPart(p, v.k); Object.assign(B[p].d, v.d || {}); }
  B.hand = freshPart("hand", "custom"); B.hand.d.ps = ps ?? 0;
  return normBrush(B);
}
const SAMPLE_ENGINE = {
  "e1-fineliner": { label: "Fineliner", parts: { tip: { k: "round", d: { taperIn: .05, taperOut: .1 } }, medium: { k: "ink" } }, ps: .2, size: 5, shade: 1, box: [640, 300], photo: { scale: 1.8 } },
  "e2-brush-pen": { label: "Brush pen", parts: { tip: { k: "round", d: { taperIn: .45, taperOut: .8, dry: .15, wobble: .05 } }, medium: { k: "ink" } }, ps: .9, size: 14, shade: 1, box: [640, 320], photo: { scale: 1.4 } },
  "e3-italic-nib": { label: "Italic nib", parts: { tip: { k: "flat", d: { aspect: .2, angle: 40, taperIn: 0, taperOut: .1 } }, medium: { k: "ink", d: { pool: .3 } } }, ps: .3, size: 16, shade: 1, box: [600, 340], photo: { scale: 1.2, waves: 1.6 } },
  "e4-split-nib": { label: "Split nib", parts: { tip: { k: "split", d: { gap: .7, aspect: .42, angle: 40 } }, medium: { k: "ink" } }, ps: .7, size: 9, shade: 1, box: [620, 300], photo: { scale: 2 } },
  "e5-multi-line": { label: "Three lines", parts: { tip: { k: "multi", d: { count: 3, reach: .55 } }, medium: { k: "ink" } }, ps: .3, size: 4, shade: 1, box: [620, 300], photo: { scale: 2.2 } },
  "e6-watercolour": { label: "Watercolour", parts: { tip: { k: "round", d: { soft: .4 } }, medium: { k: "wash", d: { wet: .85, pool: .45, bloom: .3 } } }, ps: .5, size: 70, shade: .85, box: [700, 380], photo: { scale: .9, amp: .18 } },
  "e7-charcoal": { label: "Charcoal", parts: { tip: { k: "round", d: { taperIn: .1, taperOut: .2 } }, medium: { k: "charcoal", d: { spread: .3, hold: .55 } }, paper: { k: "cold press", d: { tooth: .75 } } }, ps: .6, size: 26, shade: 1, box: [640, 340], photo: { scale: 1.3 } },
  "e9-flat-end": { label: "Flat end", parts: { tip: { k: "hake", d: { dry: 0, soft: .1 } }, medium: { k: "ink" } }, ps: 0, size: 30, shade: 1, box: [360, 700], photo: { scale: 1.2, ink: .55, texture: .08, speckle: .002, vertical: true } },
  "e8-dry-brush": { label: "Dry brush", parts: { tip: { k: "round", d: { dry: .7, soft: .2 } }, medium: { k: "wash", d: { wet: .3 } }, paper: { k: "cold press", d: { tooth: .5 } } }, ps: .4, size: 46, shade: 1, box: [660, 340], photo: { scale: 1.1 } }
};
const SAMPLE_DRAWN = {
  // A sumi stroke: 40 hairs, each its own line, running dry toward the end; a pressed, tapered body.
  "d1-sumi-stroke": { label: "Sumi brush", about: "sumi brush: hair streaks, runs dry toward the end", photo: { scale: 1.1, blur: 1 }, make() {
    const w = 760, h = 360, buf = new Float32Array(w * h), r = rng(11), pts = samplePath(w, h, { amp: 70, waves: 1.1 });
    const hairs = Array.from({ length: 40 }, () => ({ off: r() * 2 - 1, load: .7 + .3 * r(), dry: .55 + .45 * r(), wig: r() * 6 })), field = valueNoise(w, h, 5, r);
    alongPath(pts, .5, (x, y, s, a) => {
      const W = 46 * Math.pow(Math.sin(Math.PI * Math.min(1, s * 1.05 + .02)), .5) * (.75 + .25 * Math.sin(s * 3)), nx = -Math.sin(a), ny = Math.cos(a);
      for (const hr of hairs) {
        const o = hr.off * W / 2 * (1 + .15 * Math.sin(s * 9 + hr.wig)), X = x + nx * o, Y = y + ny * o, dryness = clamp((s - hr.dry * .7) / .3), i = (Y | 0) * w + (X | 0);
        if (i < 0 || i >= w * h || field[i] < dryness * .9) continue;
        stampEllipse(buf, w, h, X, Y, 2.2, 2.2, 0, hr.load * (1 - .5 * dryness));
      }
    });
    return { w, h, dark: buf };
  } },
  // A fountain pen that feathers on soft paper: a crisp line, a halo along the fibres, a few hairline spikes.
  "d2-feathered-pen": { label: "Feathered pen", about: "fountain pen on soft paper: feathered halo", photo: { scale: 1.3, blur: 1 }, make() {
    const w = 700, h = 300, buf = new Float32Array(w * h), r = rng(12), pts = samplePath(w, h, { amp: 60, press: () => .6 });
    alongPath(pts, .4, (x, y) => stampEllipse(buf, w, h, x, y, 2.6, 2.6, 0, 1));
    const fib = gauss(valueNoise(w, h, 1.3, r), w, h, .8), halo = gauss(buf, w, h, 3.5), out = new Float32Array(w * h);
    for (let i = 0; i < out.length; i++) out[i] = Math.max(buf[i], clamp(halo[i] * 1.2) * .55 * clamp((fib[i] - .3) * 2.2));
    for (let k = 0; k < 40; k++) { const p = pts[(r() * pts.length) | 0], a = r() * Math.PI * 2, L = 3 + r() * 7; for (let t = 0; t < L; t += .5) stampEllipse(out, w, h, p.x + Math.cos(a) * (3 + t), p.y + Math.sin(a) * (3 + t), .6, .6, 0, .5 * (1 - t / L)); }
    return { w, h, dark: out };
  } },
  // Pencil on cold press: the lead touches the high points; a grey, grainy line.
  "d3-pencil": { label: "Pencil", about: "pencil on cold-press paper", photo: { scale: 1.5, blur: .9 }, make() {
    const w = 680, h = 300, buf = new Float32Array(w * h), r = rng(13), pts = samplePath(w, h, { amp: 55 });
    alongPath(pts, .4, (x, y) => stampEllipse(buf, w, h, x, y, 3.2, 3.2, 0, .9));
    const paper = valueNoise(w, h, 1.8, r), mid = valueNoise(w, h, 6, r), out = new Float32Array(w * h);
    for (let i = 0; i < out.length; i++) out[i] = buf[i] * .62 * clamp((paper[i] * .6 + mid[i] * .4 - .32) * 3.2);
    return { w, h, dark: out };
  } },
  // An italic nib held at 140° (a left hand's angle): hairlines and full strokes.
  "d4-italic": { label: "Italic, left hand", about: "italic nib at 140° (outside the engine's 20–70°)", photo: { scale: 1.6, blur: 1.2 }, make() {
    const w = 620, h = 360, buf = new Float32Array(w * h), pts = samplePath(w, h, { amp: 90, waves: 1.8 });
    alongPath(pts, .3, (x, y) => stampEllipse(buf, w, h, x, y, 9, 1.4, -40 * Math.PI / 180, 1));
    return { w, h, dark: buf };
  } },
  // Two parallel lines, as a ruling pen or a split nib held open.
  "d5-two-lines": { label: "Two lines", about: "two parallel lines", photo: { scale: 1.8, blur: 1 }, make() {
    const w = 680, h = 300, buf = new Float32Array(w * h), pts = samplePath(w, h, { amp: 60 });
    alongPath(pts, .4, (x, y, s, a) => { const nx = -Math.sin(a), ny = Math.cos(a), g = 4.5 + 1.5 * Math.sin(Math.PI * s); for (const sg of [-1, 1]) stampEllipse(buf, w, h, x + nx * g * sg, y + ny * g * sg, 1.5, 1.5, 0, 1); });
    return { w, h, dark: buf };
  } },
  // A long brush line in black ink, near vertical, the start pressed in from a point and the end cut square: shot
  // dim on off-white textured paper with grey flecks (low contrast).
  "d7-low-contrast": { label: "Dim photo", about: "black brush line, square end, low contrast on textured, flecked paper", photo: { scale: 1, blur: 1.1, ink: .5, texture: .1, speckle: .003, grain: .08 }, make() {
    const w = 360, h = 900, buf = new Float32Array(w * h), pts = verticalPath(w, h);
    alongPath(pts, .4, (x, y, s, a) => { const W = 22 * Math.min(1, s / .12) ** .7 * (1 + .08 * Math.sin(s * 9)); stampBar(buf, w, h, x, y, Math.max(.6, W / 2), a, 1); });
    return { w, h, dark: buf };
  } },
  // A narrower black brush line, gently S-curved down the page, pressed in from a point and lifted with a short
  // square cut slanted 35° to the stroke; clean paper.
  "d9-slant-cut": { label: "Slant cut", about: "narrow black line, square end cut at 35°, clean paper", photo: { scale: 1, blur: 1 }, make() {
    const w = 300, h = 900, buf = new Float32Array(w * h), pts = [];
    let d = 0, last = null;
    for (let i = 0; i <= 320; i++) { const u = i / 300, x = w / 2 + 26 * Math.sin(u * Math.PI * 1.6), y = lerp(70, 830, u); if (last) d += Math.hypot(x - last[0], y - last[1]); last = [x, y]; pts.push({ x, y, d }); }
    const L = pts[300].d, end = pts[300], prev = pts[290], ex = end.x - prev.x, ey = end.y - prev.y, el = Math.hypot(ex, ey), ux = ex / el, uy = ey / el;
    const cut = 35 * Math.PI / 180, cx = ux * Math.cos(cut) - uy * Math.sin(cut), cy = ux * Math.sin(cut) + uy * Math.cos(cut);
    alongPath(pts.map(p => Object.assign(p, { s: p.d / L })), .4, (x, y, s, a) => stampBar(buf, w, h, x, y, 11 * Math.min(1, s / .1) ** .7, a, 1));
    // The cut: everything past a line through the end, slanted 35° from square across the stroke.
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const t = (x + .5 - end.x) * cx + (y + .5 - end.y) * cy; if (t > -.5) buf[y * w + x] *= clamp(.5 - t); }
    return { w, h, dark: buf };
  } },
  // A long brush line pressed lightly for its first third (a third of its later width), then pressed down, widening
  // through the middle, lifted with a square cut; mid-contrast, textured paper.
  "d10-thin-top": { label: "Thin top", about: "thin top third, three times wider below, square end, textured paper", photo: { scale: 1, blur: 1.1, ink: .7, texture: .07, speckle: .002 }, make() {
    const w = 320, h = 960, buf = new Float32Array(w * h), pts = verticalPath(w, h);
    alongPath(pts, .4, (x, y, s, a) => { const W = 26 * (s < .03 ? s / .03 * .33 : s < .33 ? .33 : .33 + .67 * smooth((s - .33) / .3)); stampBar(buf, w, h, x, y, Math.max(.6, W / 2), a, 1); });
    return { w, h, dark: buf };
  } },
  // A thinner brush line whose top third wavers in width as the hand settles, then widens and ends square;
  // mid-contrast, textured paper.
  "d11-wobble-top": { label: "Wobbly top", about: "width wobbles in the top third, then widens; square end", photo: { scale: 1, blur: 1.1, ink: .65, texture: .08, speckle: .002 }, make() {
    const w = 300, h = 900, buf = new Float32Array(w * h), pts = verticalPath(w, h);
    alongPath(pts, .4, (x, y, s, a) => { const base = 7 + 9 * smooth((s - .25) / .4), wob = s < .38 ? 1 + .28 * Math.sin(s * Math.PI * 2 * 9) : 1, W = base * wob * Math.min(1, s / .05) ** .7; stampBar(buf, w, h, x, y, Math.max(.6, W / 2), a, 1); });
    return { w, h, dark: buf };
  } },
  // A flat brush pulled in one go: an even band, both ends cut square across it.
  "d8-flat-brush": { label: "Flat brush", about: "flat brush: even width, both ends cut square", photo: { scale: 1.1, blur: 1 }, make() {
    const w = 760, h = 340, buf = new Float32Array(w * h), pts = samplePath(w, h, { amp: 60, waves: 1.1 });
    alongPath(pts, .4, (x, y, s, a) => stampBar(buf, w, h, x, y, 17, a, .97));
    return { w, h, dark: buf };
  } },
  // A watercolour stroke: grey, soft edged, darker at its rim, with blooms.
  "d6-wash": { label: "Wash", about: "watercolour: soft edge, darker rim, blooms", photo: { scale: 1, blur: 1.2, grain: .07 }, make() {
    const w = 760, h = 400, buf = new Float32Array(w * h), r = rng(16), pts = samplePath(w, h, { amp: 80, waves: 1 });
    alongPath(pts, 1, (x, y, s) => stampEllipse(buf, w, h, x, y, 34 + 6 * Math.sin(s * 5), 34 + 6 * Math.sin(s * 5), 0, 1));
    const soft = gauss(buf, w, h, 2.5), inner = gauss(buf, w, h, 7), bloom = valueNoise(w, h, 40, r), out = new Float32Array(w * h);
    for (let i = 0; i < out.length; i++) out[i] = soft[i] * (.3 + .12 * (bloom[i] - .5)) + Math.max(0, soft[i] - inner[i]) * 2.2 * .22;
    return { w, h, dark: out };
  } }
};
// One sample as an RGB photo, with what made it; extra: more for the camera (ragged, wobble, noise).
function makeSample(name, extra) {
  const e = SAMPLE_ENGINE[name];
  if (e) {
    const B = sampleBrush(e.parts, e.ps), [w, h] = e.box, s = modSurface(w, h, 77);
    const pts = e.photo.vertical ? verticalPath(w, h) : samplePath(w, h, { waves: e.photo.waves, amp: e.photo.amp ? h * e.photo.amp : undefined });
    // A hand eases off at the end of a stroke: the last samples lighter, then held.
    pts[pts.length - 2].p *= .45; pts[pts.length - 1].p *= .15; pts[pts.length - 1].t += 60;
    layStroke(s, B, pts, e.size, e.shade, null);
    const dark = Float32Array.from(s.page, v => 1 - v / PAPER_TONE);
    return Object.assign(photographMark(dark, w, h, Object.assign({ seed: name.length * 31 }, e.photo, extra)), { truth: { kind: "engine", code: encodeM(B), struct: { tip: B.tip.k, medium: B.medium.k, paper: B.paper.k, life: B.life.k }, size: e.size, shade: e.shade, photoScale: e.photo.scale } });
  }
  const d = SAMPLE_DRAWN[name], m = d.make();
  return Object.assign(photographMark(m.dark, m.w, m.h, Object.assign({ seed: name.length * 17 }, d.photo, extra)), { truth: { kind: "drawn", about: d.about } });
}
const SAMPLE_NAMES = [...Object.keys(SAMPLE_ENGINE), ...Object.keys(SAMPLE_DRAWN)];

module.exports = { rng, clamp, lerp, modSurface, blankPage, layStroke, Stroke, showPage, resolve, encodeM, readAnyCode, normBrush, freshPart, cloneBrush, CHOICES, DIALS, PARTS, TEXTURES, PAPER_TONE, Water, plainWords, choiceWords, freshName, newSeed, MSTYLES, STYLE_NAMES, rangeOf, dialDefault, switchChoice, brushWords, choiceWords, DRAWN_TOOLS, grayOf, flatten, otsu, components, edt, gauss, resample, blur, median, quantile, mean, std, preparePhoto, analyzeStroke, measureMark, endShape, STEADY, roughExcess, fitStroke, fitNow, renderFit, scoreFit, brushFromFit, cameraBlur, candidates, freeDials, FIT_TIPS, CATEGORIES, makePaper, paperStats, makeSample, sampleBrush, SAMPLE_NAMES, SAMPLE_ENGINE, SAMPLE_DRAWN };
