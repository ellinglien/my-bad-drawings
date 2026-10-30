// Halo's golden pages from the web lab's engine (docs/brush-lab/prototypes/modular-lab-src with newmods.js, in Node),
// for HaloChecks. Each case lays Halo strokes on a page of marks and grows them to the end, as the lab's test area does
// (a host that keeps halos, ten ticks a second, every halo a tick at a time together). For each stroke it writes the page
// as the halo found it before the stroke and after it, then the page once grown (and, for two strokes, the page when the
// second was laid), as 8-bit grey PNGs of the lab's tones rounded, and halo.json: each stroke's rectangle, seed, Reach,
// ticks, darkness and Growth, its band and scouts.
//
// Two things are the app's, applied to the lab here: its limits (the owner's: Reach at most three tip widths, at most
// 20 s), and its whole-number page (the halo reads the stroke's darkness and the marks from the page rounded, as the
// app's 8-bit layers hold them). Paper 255, as the app's (newmods.js reads its paper tone as 255 here).
// Run from the repo: node scripts/halo_goldens.cjs
const fs = require("fs"), path = require("path"), zlib = require("zlib");
const ROOT = path.join(__dirname, ".."), SRC = path.join(ROOT, "docs/brush-lab/prototypes/modular-lab-src");
const patch = t => {
  const a = "const g = (y + y0) * W + x + x0, b = s.base[g];", b = "own[y * w + x] = clamp((1 - s.page[g] / Math.max(1, b))";
  if (!t.includes(a) || !t.includes(b)) throw new Error("newmods.js Halo has changed: update the patch");
  return t.replace(/PAPER_TONE/g, "255").replace(a, "const g = (y + y0) * W + x + x0, b = Math.round(s.base[g]);")
    .replace(b, "own[y * w + x] = clamp((1 - Math.round(s.page[g]) / Math.max(1, b))");
};
const code = ["port.js", "oldmodel.js", "engine.js", "stroke.js", "float.js", "parts.js", "checks.js", "newmods.js"].map(f => {
  const t = fs.readFileSync(path.join(SRC, f), "utf8");
  return f === "newmods.js" ? patch(t) : t;
}).join("\n;\n");
// The app's limits on the lab's resolve: Reach within three tip widths, at most 200 ticks.
const limits = `
const resolveLab = resolve;
resolve = function (B, size, shade) {
  const R = resolveLab(B, size, shade);
  if (R.halo) { const w = R.line ? R.inkSize : R.bodySize; R.halo.reach = Math.min(R.halo.reach, Math.max(1, Math.round(3 * w))); R.halo.ticks = Math.min(200, R.halo.ticks); }
  return R;
};`;
const M = new Function(code + limits + "\nreturn { readAnyCode, encodeM, Stroke, modSurface, haloDryAll, hashPts, rollStyle, STYLE_NAMES, switchChoice, normBrush, choiceWords, captionItems, plainWords, lifeNote, rng, freshPart, cloneBrush };")();
const OUT = path.join(ROOT, "app/src/test/resources/halo");
fs.mkdirSync(OUT, { recursive: true });
const W = 480, H = 360;
// The web lab's Halo ink preset (newmods.js newModPresets: Reach .55, Growth .6), at Steady and as it is (Press dark);
// a Halo wash (Wash behind's wash, its own way, Halo at its defaults).
const INK = "halo-steady.M70i077at0000at_000zz000000000000000_0_000_4jsjsll_0000000000000000000nz0.wx",
  PRESET = "halo-ink.M70i077at0000at_000zz000000000000000_0_000_4jsjsll_25ess00000000000000nz0.wx",
  WASH = "halo-wash.M60i000000000at_1ulat00770000ee_0_000_4jsi0i0_0000000000000000000nz0.11kx";
function brush(c) { const r = M.readAnyCode(c); if (!r) throw new Error("no code " + c); return r.b; }
// The marks (HaloChecks.mark): black bars, a grey band, hatching; paper 255.
function mark(kind, x, y) {
  if (kind === "bars") { if ((y >= 148 && y < 152) || (y >= 236 && y < 240 && x > 60 && x < 420)) return 0; return 255; }
  if (kind === "page") {
    if ((x >= 100 && x < 104) || (x >= 340 && x < 344)) return 0;
    if (x >= 200 && x < 280 && y >= 40 && y < 320) return 150;
    if (x >= 380 && x < 460 && ((x + Math.floor(y / 2)) % 10 + 10) % 10 < 2) return 90;
    return 255;
  }
  return 255;
}
const line = (x0, y0, x1, y1, n = 120, p = .8) => Array.from({ length: n + 1 }, (_, k) => [x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n, p, k * 5, NaN, NaN]);
const wave = Array.from({ length: 121 }, (_, i) => { const t = i / 120; return [40 + 400 * t, 180 + 60 * Math.sin(2 * Math.PI * t), .2 + .7 * Math.sin(Math.PI * t), i * 5, NaN, NaN]; });
function crc32(buf) { let c, crc = 0xffffffff; for (let n = 0; n < buf.length; n++) { c = (crc ^ buf[n]) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crc = (crc >>> 8) ^ c; } return (crc ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, c]); }
function png(name, tones) {
  const raw = Buffer.alloc((W + 1) * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) raw[y * (W + 1) + 1 + x] = Math.max(0, Math.min(255, Math.round(tones[y * W + x])));
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8;
  fs.writeFileSync(path.join(OUT, name + ".png"), Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]));
}
// A host that keeps halos growing (the test area's), with a Slow wash's water that settles nothing.
const host = () => ({ halos: true, getWater: () => ({ addWet() { } }) });
function lay(s, c, size, shade, pts, h) {
  const st = new M.Stroke(s, brush(c), size, shade, h);
  for (let i = 0; i < pts.length; i += 3) st.add(pts.slice(i, i + 3));
  return st;
}
// name, marks, then each stroke: code, size px, darkness, samples, ticks the halos grow before the next.
const CASES = [
  ["bridge", "bars", [[INK, 12, 1, line(30, 120, 450, 120), 0]]],
  ["thin", "bars", [[INK, 6, 1, line(30, 138, 450, 138), 0]]],
  ["wave", "page", [[PRESET, 10, 1, wave, 0]]],
  ["wash", "bars", [[WASH, 40, 1, line(30, 100, 450, 100), 0]]],
  ["pair", "blank", [[INK, 10, 1, line(30, 150, 450, 150), 20], [INK, 10, 1, line(30, 190, 450, 190), 0]]],
];
const info = {};
for (const [name, kind, strokes] of CASES) {
  const s = M.modSurface(W, H, 7);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) s.page[y * W + x] = s.base[y * W + x] = mark(kind, x, y);
  const hs = host(), out = [];
  strokes.forEach(([c, size, shade, pts, grow], k) => {
    const st = lay(s, c, size, shade, pts, hs);
    const before = Float32Array.from(s.page);
    // end(): the stroke laid, then its halo built from the page (s.base is the page before it).
    const r = st.end(), h = s.halos[s.halos.length - 1];
    png(name + "-" + k + "-before", s.base); png(name + "-" + k + "-after", s.page);
    out.push({ code: c, size, shade, rect: h.rect.length ? [r[0], r[1], r[2], r[3]] : null, seed: (st.R.pen.seed ^ M.hashPts(st.pts) ^ 0x51a3e01d) >>> 0,
      reach: h.R, ticks: h.N, dark: h.dark, growth: st.R.halo.growth, band: h.band.length, scouts: h.ag.length, box: h.rect, hash: M.hashPts(st.pts) >>> 0 });
    for (let t = 0; t < grow; t++) { for (const o of s.halos) o.step(); s.halos = s.halos.filter(o => o.tick < o.N); }
    if (grow) png(name + "-" + k + "-grown", s.page);
  });
  M.haloDryAll(s);
  png(name + "-lab", s.page);
  info[name] = out;
  console.log(name, out.map(o => `reach ${o.reach} ticks ${o.ticks} band ${o.band} scouts ${o.scouts}`).join("; "));
}
fs.writeFileSync(path.join(OUT, "halo.json"), JSON.stringify(info, null, 1) + "\n");

// The words: every style's rolls, seeded, made Halo with a drying time, Reach and Growth from the same seed.
{
  const keep = Math.random, r = M.rng(20261018), rows = [];
  Math.random = r;
  try {
    for (let i = 0; i < 120; i++) {
      let B = M.rollStyle(M.STYLE_NAMES[i % M.STYLE_NAMES.length]);
      if (B.life.k === "float") continue;
      B.life = M.switchChoice("life", B.life, "halo");
      Object.assign(B.life.d, { drying: r(), reach: r(), growth: r() });
      M.normBrush(B);
      // Its words as its code reads (the dials to the code's steps).
      const c = M.encodeM(B), A = M.readAnyCode(c).b;
      B = A;
      const Q = M.cloneBrush(B); Q.life = M.freshPart("life", "wet"); M.normBrush(Q);
      rows.push([c, M.choiceWords(B, "life"), M.captionItems(B).join("|"), M.plainWords(B), M.lifeNote(B), M.captionItems(Q).join("|"), M.plainWords(Q)].join("\t"));
    }
  } finally { Math.random = keep; }
  fs.writeFileSync(path.join(OUT, "words.tsv"), rows.join("\n") + "\n");
  console.log("words", rows.length);
}
