// Blend's golden pages from the web lab's engine (docs/brush-lab/prototypes/modular-lab-src with newmods.js, in Node),
// for BlendChecks: White, Behind and Invert along the golden wave over a page of marks, with ink and opaque paint (whose
// own marks the app draws as the lab does; a wash's and a stick's it draws its own way, so BlendChecks checks the
// lab's rule on those against the app's own mark instead), the paper 255 as the app's (newmods.js
// reads its paper tone as 255 here). The lab draws on one page; the app lays the marks on the layer BlendChecks.GOLDENS
// names and the stroke on its own, and their composite is compared. Each page is written as an 8-bit grey PNG of the
// lab's tones, rounded. Run from the repo: node scripts/blend_goldens.cjs
const fs = require("fs"), path = require("path"), zlib = require("zlib");
const ROOT = path.join(__dirname, ".."), SRC = path.join(ROOT, "docs/brush-lab/prototypes/modular-lab-src");
const code = ["port.js", "oldmodel.js", "engine.js", "stroke.js", "float.js", "parts.js", "checks.js", "newmods.js"].map(f => {
  const t = fs.readFileSync(path.join(SRC, f), "utf8");
  return f === "newmods.js" ? t.replace(/PAPER_TONE/g, "255") : t;
}).join("\n;\n");
const M = new Function(code + "\nreturn { readAnyCode, Stroke, modSurface };")();
const OUT = path.join(ROOT, "app/src/test/resources/blend");
fs.mkdirSync(OUT, { recursive: true });
// name, code, size px, darkness, marks (BlendChecks.GOLDENS lists the same).
const CASES = [
  ["white-gel", "white-gel-pen.M00i000000000at_000zz0000000000000001_0_000_1jz_0000000000000000000nz0.1y9", 8, 1, "ground"],
  ["white-gel-light", "white-gel-pen.M00i000000000at_000zz0000000000000001_0_000_1jz_0000000000000000000nz0.1y9", 14, .5, "ground"],
  ["invert-ink", "invert-ink.M00i000000000at_000zz0000000000000003_0_000_1jz_0000000000000000000nz0.1y9", 30, 1, "lines"],
  ["paint-behind", "paint-behind.M80i000000000at_2zz00at2_0_000_1jz_0000000000000000000nz0.13ztx3e", 60, .6, "lines"],
  ["white-paint", "white-paint.M80i000000000at_2zz00at1_0_000_1jz_0000000000000000000nz0.13ztx3e", 40, .7, "lines"],
];
const W = 480, H = 360;
// The marks (BlendChecks.mark): two black lines, a grey band, hatching, 255 paper; or a dark ground with a lighter band.
function mark(kind, x, y) {
  if (kind === "ground") return x >= 60 && x < 420 ? (x >= 200 && x < 260 ? 150 : 40) : 255;
  if ((x >= 100 && x < 106) || (x >= 340 && x < 346)) return 0;
  if (x >= 200 && x < 280) return 150;
  if (x >= 380 && x < 460 && ((x + Math.floor(y / 2)) % 10 + 10) % 10 < 2) return 90;
  return 255;
}
// The golden wave (BlendChecks.wave): x, y, pressure travel, ms.
const wave = [];
for (let i = 0; i <= 120; i++) { const t = i / 120; wave.push([40 + 400 * t, 180 + 60 * Math.sin(2 * Math.PI * t), .2 + .7 * Math.sin(Math.PI * t), i * 5, NaN, NaN]); }
function crc32(buf) { let c, crc = 0xffffffff; for (let n = 0; n < buf.length; n++) { c = (crc ^ buf[n]) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crc = (crc >>> 8) ^ c; } return (crc ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, c]); }
for (const [name, c, size, shade, kind] of CASES) {
  const r = M.readAnyCode(c);
  if (!r) throw new Error("no code " + name);
  const s = M.modSurface(W, H, 7);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) s.page[y * W + x] = s.base[y * W + x] = mark(kind, x, y);
  const st = new M.Stroke(s, r.b, size, shade, null);
  for (let i = 0; i < wave.length; i += 3) st.add(wave.slice(i, i + 3));
  st.end();
  const raw = Buffer.alloc((W + 1) * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) raw[y * (W + 1) + 1 + x] = Math.max(0, Math.min(255, Math.round(s.page[y * W + x])));
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8;
  fs.writeFileSync(path.join(OUT, name + ".png"), Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]));
  console.log(name, fs.statSync(path.join(OUT, name + ".png")).size, "bytes");
}
