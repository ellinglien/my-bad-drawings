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
