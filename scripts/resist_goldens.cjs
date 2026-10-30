// Resist's golden pages from the web lab's engine (docs/brush-lab/prototypes/modular-lab-src with newmods.js, in Node),
// for ResistChecks: masking fluid along the golden wave over a page of marks, shown hatched as the screen shows it; the
// fluid, then a broad ink line across it (the masked pixels keep the paper); the same with Light fluid (it keeps out
// part of the ink); wax, shown (no hatch: only its faint sheen) and under the same ink. Ink, as the app draws ink as the lab does; the paper
// 255 as the app's (newmods.js reads its paper tone as 255 here). Each page is written as an 8-bit grey PNG of the lab's
// tones (rounded; the hatched one as shown). Run from the repo: node scripts/resist_goldens.cjs
const fs = require("fs"), path = require("path"), zlib = require("zlib");
const ROOT = path.join(__dirname, ".."), SRC = path.join(ROOT, "docs/brush-lab/prototypes/modular-lab-src");
const code = ["port.js", "oldmodel.js", "engine.js", "stroke.js", "float.js", "parts.js", "checks.js", "newmods.js"].map(f => {
  const t = fs.readFileSync(path.join(SRC, f), "utf8");
  return f === "newmods.js" ? t.replace(/PAPER_TONE/g, "255") : t;
}).join("\n;\n");
const M = new Function(code + "\nreturn { readAnyCode, Stroke, modSurface, pageGrays };")();
const OUT = path.join(ROOT, "app/src/test/resources/resist");
fs.mkdirSync(OUT, { recursive: true });
const FLUID = "masking-fluid.M60i000000000at_5_0_000_1jz_0000000000000000000nz0.px",
  WAX = "wax-smooth.M60i000000000at_500_0_000_1jz_0000000000000000000nz0.py",
  INK = "plain-ink.M00i000000000at_000zz000000000000000_0_000_1jz_0000000000000000000nz0.1y9";
// name, resist code, its size px and darkness, marks, then the ink across (size px, or 0 for none), shown hatched.
// Wax on smooth paper with no dry brush: the app's paper grain and dry brush are its own (ResistChecks checks wax's
// tooth by the lab's rule on the app's own paper), so here wax shields in full and shows only its sheen.
const LIGHT = 1 - 120 / 255;
const CASES = [
  ["fluid-hatch", FLUID, 30, 1, "lines", 0, true],
  ["fluid-ink", FLUID, 30, 1, "blank", 90, false],
  ["fluid-light", FLUID, 40, LIGHT, "blank", 90, false],
  ["wax-hatch", WAX, 30, 1, "lines", 0, true],
  ["wax-ink", WAX, 40, 1, "blank", 90, false],
];
const W = 480, H = 360;
// The marks (ResistChecks.mark, BlendChecks.mark's lines): two black lines, a grey band, hatching, 255 paper.
function mark(kind, x, y) {
  if (kind === "blank") return 255;
  if ((x >= 100 && x < 106) || (x >= 340 && x < 346)) return 0;
  if (x >= 200 && x < 280) return 150;
  if (x >= 380 && x < 460 && ((x + Math.floor(y / 2)) % 10 + 10) % 10 < 2) return 90;
  return 255;
}
// The golden wave (ResistChecks.wave): x, y, pressure travel, ms; the ink across at y 180, a sample every 2.5 px.
const wave = [], across = [];
for (let i = 0; i <= 120; i++) { const t = i / 120; wave.push([40 + 400 * t, 180 + 60 * Math.sin(2 * Math.PI * t), .2 + .7 * Math.sin(Math.PI * t), i * 5, NaN, NaN]); }
for (let i = 0, n = Math.ceil(400 / 2.5); i <= n; i++) across.push([40 + 400 * i / n, 180, .8, i * 4, NaN, NaN]);
function crc32(buf) { let c, crc = 0xffffffff; for (let n = 0; n < buf.length; n++) { c = (crc ^ buf[n]) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crc = (crc >>> 8) ^ c; } return (crc ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, c]); }
function stroke(s, c, size, shade, pts) {
  const r = M.readAnyCode(c);
  if (!r) throw new Error("no code " + c);
  const st = new M.Stroke(s, r.b, size, shade, null);
  for (let i = 0; i < pts.length; i += 3) st.add(pts.slice(i, i + 3));
  st.end();
}
for (const [name, c, size, shade, kind, inkSize, hatched] of CASES) {
  const s = M.modSurface(W, H, 7);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) s.page[y * W + x] = s.base[y * W + x] = mark(kind, x, y);
  stroke(s, c, size, shade, wave);
  if (inkSize) stroke(s, INK, inkSize, 1, across);
  const shown = hatched ? M.pageGrays(s, true) : null;
  const raw = Buffer.alloc((W + 1) * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) raw[y * (W + 1) + 1 + x] = hatched ? Math.round(shown[y * W + x] * 255) : Math.max(0, Math.min(255, Math.round(s.page[y * W + x])));
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8;
  fs.writeFileSync(path.join(OUT, name + ".png"), Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]));
  console.log(name, fs.statSync(path.join(OUT, name + ".png")).size, "bytes");
}
