// Node tools for the checks: PNG in and out, and the engine bundle with the app's paper scans loaded.
const fs = require("fs"), zlib = require("zlib"), path = require("path");
// ---------- PNG ----------
const CRC = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c; } return t; })();
const crc32 = b => { let c = -1; for (let i = 0; i < b.length; i++) c = CRC[(c ^ b[i]) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
function chunk(type, data) { const out = Buffer.alloc(12 + data.length); out.writeUInt32BE(data.length, 0); out.write(type, 4, "ascii"); data.copy(out, 8); out.writeUInt32BE(crc32(out.subarray(4, 8 + data.length)), 8 + data.length); return out; }
// rgb: Uint8Array w*h*3, or grey w*h (channels 1).
function writePNG(file, w, h, px, channels = 3) {
  const raw = Buffer.alloc((w * channels + 1) * h);
  for (let y = 0; y < h; y++) { raw[y * (w * channels + 1)] = 0; Buffer.from(px.buffer, px.byteOffset + y * w * channels, w * channels).copy(raw, y * (w * channels + 1) + 1); }
  const ihdr = Buffer.alloc(13); ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4); ihdr[8] = 8; ihdr[9] = channels === 3 ? 2 : 0;
  fs.writeFileSync(file, Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", zlib.deflateSync(raw, { level: 6 })), chunk("IEND", Buffer.alloc(0))]));
}
// Reads 8-bit grey, RGB or RGBA PNGs (no interlace), as grey 0..1.
function readPNGGray(file) {
  const b = fs.readFileSync(file); let o = 8, w, h, ct, idat = [];
  while (o < b.length) { const len = b.readUInt32BE(o), type = b.toString("ascii", o + 4, o + 8), d = b.subarray(o + 8, o + 8 + len); if (type === "IHDR") { w = d.readUInt32BE(0); h = d.readUInt32BE(4); ct = d[9]; } if (type === "IDAT") idat.push(d); o += 12 + len; }
  const ch = { 0: 1, 2: 3, 4: 2, 6: 4 }[ct], raw = zlib.inflateSync(Buffer.concat(idat)), stride = w * ch, cur = Buffer.alloc(stride), prev = Buffer.alloc(stride), g = new Float32Array(w * h);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)], line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= ch ? cur[x - ch] : 0, up = prev[x], c = x >= ch ? prev[x - ch] : 0;
      let v = line[x];
      if (f === 1) v += a; else if (f === 2) v += up; else if (f === 3) v += (a + up) >> 1; else if (f === 4) { const p = a + up - c, pa = Math.abs(p - a), pb = Math.abs(p - up), pc = Math.abs(p - c); v += pa <= pb && pa <= pc ? a : pb <= pc ? up : c; }
      cur[x] = v & 255;
    }
    for (let x = 0; x < w; x++) g[y * w + x] = ch >= 3 ? (cur[x * ch] * .299 + cur[x * ch + 1] * .587 + cur[x * ch + 2] * .114) / 255 : cur[x * ch] / 255;
    cur.copy(prev);
  }
  return { w, h, g };
}
const clamp = (v, a = 0, b = 1) => v < a ? a : v > b ? b : v;
// Loads the engine bundle and the app's paper scans.
function engine(bundle) {
  const E = require(bundle);
  const A = path.join(__dirname, "..", "..", "..", "app", "src", "main", "assets", "textures");
  for (const [k, f] of [["cold press", "cold-press"], ["laid", "laid"], ["felt", "felt"], ["crumpled", "crumpled"], ["canvas", "canvas"]]) E.TEXTURES[k] = Float32Array.from(fs.readFileSync(path.join(A, f + ".bin")), v => v / 255);
  return E;
}
module.exports = { writePNG, readPNGGray, clamp, engine };
