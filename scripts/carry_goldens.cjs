// Smudge's golden pages from the web lab's engine (docs/brush-lab/prototypes/modular-lab-src, in Node), for
// CarrySmudgeChecks: six brushes at Own paint 0 (the wash Lift at no darkness), so the page is the smudge alone, along
// the golden wave over a marks page (two black lines, a grey band, hatching), the paper 255 as the app's. Each page is
// written as an 8-bit grey PNG of the lab's tones, rounded. Run from the repo: node scripts/carry_goldens.cjs
const fs = require("fs"), path = require("path"), zlib = require("zlib");
const ROOT = path.join(__dirname, ".."), SRC = path.join(ROOT, "docs/brush-lab/prototypes/modular-lab-src");
const code = ["port.js", "oldmodel.js", "engine.js", "stroke.js", "parts.js"].map(f => fs.readFileSync(path.join(SRC, f), "utf8")).join("\n;\n");
const M = new Function(code + "\nreturn { readAnyCode, Stroke, modSurface };")();
const OUT = path.join(ROOT, "app/src/test/resources/carry");
// name, code, size px, darkness (CarrySmudgeChecks.GOLDENS lists the same).
const CASES = [
  ["pencil-smear", "pencil-smear.M40i074000000at_4ee00_3p6js00_000_0_0000000000000000000nz0.1wyw208", 6, 1],
  ["charcoal-smear", "charcoal-smear.M50i000000000at_3atee_3p6js00_000_0_0000000000000000000nz0.13ax4gw", 30, 1],
  ["stump", "stump.M50i00000000yat_3eqlj_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", 28, 1],
  ["wash-lift", "wash-lift.M60i000000000bv_1ul0000000000_177at_000_0_0000000000000000000nz0.1dn6o7h", 60, 0],
  ["oils-drag", "oils-drag.M90i00000000042_2zz00_2llp600_000_0_0000000000000000000nz0.1vtkv3y", 60, 1],
  ["watercolor-drag", "watercolor-drag.M60i000000000cg_1ul0000000000_2llp600_000_0_0000000000000000000nz0.1s9xl9o", 120, 1],
  // Smear's Soften (the Stump spec): the Tools page's Blend stump and Wash comb (StumpChecks).
  ["blend-stump", "blend-stump.M50i00000000yat_3eqlj00we_3ssll00zz_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", 28, 1],
  ["wash-comb", "wash-comb.M65zzlli000000000_1ulat00770000ee_3zzll00zz_000_1jz_0000000000000000000nz0.7", 60, 1],
];
const W = 480, H = 360;
// The marks (CarrySmudgeChecks.marks): tone at x, y, 255 for paper.
function mark(x, y) {
  if ((x >= 100 && x < 106) || (x >= 340 && x < 346)) return 0;
  if (x >= 200 && x < 280) return 150;
  if (x >= 380 && x < 460 && ((x + Math.floor(y / 2)) % 10 + 10) % 10 < 2) return 90;
  return 255;
}
// The golden wave (CarrySmudgeChecks.wave): x, y, pressure travel, ms.
const wave = [];
for (let i = 0; i <= 120; i++) { const t = i / 120; wave.push([40 + 400 * t, 180 + 60 * Math.sin(2 * Math.PI * t), .2 + .7 * Math.sin(Math.PI * t), i * 5, NaN, NaN]); }
function crc32(buf) { let c, crc = 0xffffffff; for (let n = 0; n < buf.length; n++) { c = (crc ^ buf[n]) & 0xff; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crc = (crc >>> 8) ^ c; } return (crc ^ 0xffffffff) >>> 0; }
function chunk(type, data) { const len = Buffer.alloc(4); len.writeUInt32BE(data.length); const td = Buffer.concat([Buffer.from(type), data]); const c = Buffer.alloc(4); c.writeUInt32BE(crc32(td)); return Buffer.concat([len, td, c]); }
for (const [name, c, size, shade] of CASES) {
  const r = M.readAnyCode(c);
  if (!r) throw new Error("no code " + name);
  const s = M.modSurface(W, H, 7);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) s.page[y * W + x] = mark(x, y);
  const st = new M.Stroke(s, r.b, size, shade, null);
  if (st.R.carry) st.R.carry.paper = 255;
  for (let i = 0; i < wave.length; i += 3) st.add(wave.slice(i, i + 3));
  st.end();
  const raw = Buffer.alloc((W + 1) * H);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) raw[y * (W + 1) + 1 + x] = Math.max(0, Math.min(255, Math.round(s.page[y * W + x])));
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8;
  fs.writeFileSync(path.join(OUT, name + ".png"), Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 9 })), chunk("IEND", Buffer.alloc(0))]));
  console.log(name, fs.statSync(path.join(OUT, name + ".png")).size, "bytes");
}
