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
