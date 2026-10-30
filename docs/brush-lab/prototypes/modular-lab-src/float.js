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
