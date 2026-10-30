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
