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
