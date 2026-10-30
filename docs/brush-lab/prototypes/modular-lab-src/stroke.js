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
