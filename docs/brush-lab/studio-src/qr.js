// ===================================================================================================================
// A small QR encoder (byte mode, error correction M, versions 1-10: up to 213 bytes, far more than a brush code needs),
// after Project Nayuki's reference QR Code generator (MIT). qrMatrix(text) → { size, dark: Uint8Array(size * size) }.
// ===================================================================================================================
const QR_ECC_M = [0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26];   // error-correction codewords per block, by version
const QR_BLOCKS_M = [0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5];          // blocks, by version
function qrRawModules(ver) {
  let r = (16 * ver + 128) * ver + 64;
  if (ver >= 2) { const n = Math.floor(ver / 7) + 2; r -= (25 * n - 10) * n - 55; if (ver >= 7) r -= 36; }
  return r;
}
const qrDataCodewords = ver => Math.floor(qrRawModules(ver) / 8) - QR_ECC_M[ver] * QR_BLOCKS_M[ver];
function qrMul(x, y) { let z = 0; for (let i = 7; i >= 0; i--) { z = (z << 1) ^ ((z >>> 7) * 0x11d); z ^= ((y >>> i) & 1) * x; } return z & 255; }
function qrDivisor(n) {
  const r = new Array(n).fill(0); r[n - 1] = 1; let root = 1;
  for (let i = 0; i < n; i++) { for (let j = 0; j < n; j++) { r[j] = qrMul(r[j], root); if (j + 1 < n) r[j] ^= r[j + 1]; } root = qrMul(root, 2); }
  return r;
}
function qrRemainder(data, div) {
  const r = div.map(() => 0);
  for (const b of data) { const f = b ^ r.shift(); r.push(0); div.forEach((c, i) => { r[i] ^= qrMul(c, f); }); }
  return r;
}
function qrAlign(ver, size) {
  if (ver === 1) return [];
  const n = Math.floor(ver / 7) + 2, step = Math.ceil((ver * 4 + 4) / (n * 2 - 2)) * 2, out = [6];
  for (let p = size - 7; out.length < n; p -= step) out.splice(1, 0, p);
  return out;
}
function qrMatrix(text) {
  const bytes = Array.from(new TextEncoder().encode(String(text)));
  let ver = 1;
  const cap = v => (qrDataCodewords(v) * 8 - 4 - (v < 10 ? 8 : 16)) >> 3;
  while (ver <= 10 && bytes.length > cap(ver)) ver++;
  if (ver > 10) return null;
  // The bit stream: byte mode, the length, the bytes, a terminator, padding.
  const bits = [], put = (v, n) => { for (let i = n - 1; i >= 0; i--) bits.push((v >>> i) & 1); };
  put(4, 4); put(bytes.length, ver < 10 ? 8 : 16); for (const b of bytes) put(b, 8);
  const room = qrDataCodewords(ver) * 8;
  put(0, Math.min(4, room - bits.length)); put(0, (8 - bits.length % 8) % 8);
  for (let pad = 0xec; bits.length < room; pad ^= 0xec ^ 0x11) put(pad, 8);
  const data = []; for (let i = 0; i < bits.length; i += 8) { let v = 0; for (let k = 0; k < 8; k++) v = (v << 1) | bits[i + k]; data.push(v); }
  // Blocks and their error correction, interleaved.
  const nb = QR_BLOCKS_M[ver], ecl = QR_ECC_M[ver], raw = Math.floor(qrRawModules(ver) / 8), nShort = nb - raw % nb, shortLen = Math.floor(raw / nb), div = qrDivisor(ecl);
  const blocks = [];
  for (let i = 0, k = 0; i < nb; i++) {
    const dat = data.slice(k, k += shortLen - ecl + (i < nShort ? 0 : 1)), ecc = qrRemainder(dat, div);
    if (i < nShort) dat.push(0);
    blocks.push(dat.concat(ecc));
  }
  const words = [];
  for (let i = 0; i < blocks[0].length; i++) blocks.forEach((b, j) => { if (i !== shortLen - ecl || j >= nShort) words.push(b[i]); });
  // The function patterns.
  const size = ver * 4 + 17, dark = new Uint8Array(size * size), fn = new Uint8Array(size * size);
  const set = (x, y, d) => { dark[y * size + x] = d ? 1 : 0; fn[y * size + x] = 1; };
  for (let i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0); }
  for (const [cx, cy] of [[3, 3], [size - 4, 3], [3, size - 4]])
    for (let dy = -4; dy <= 4; dy++) for (let dx = -4; dx <= 4; dx++) {
      const x = cx + dx, y = cy + dy, d = Math.max(Math.abs(dx), Math.abs(dy));
      if (x >= 0 && x < size && y >= 0 && y < size) set(x, y, d !== 2 && d !== 4);
    }
  const al = qrAlign(ver, size);
  al.forEach((ax, i) => al.forEach((ay, j) => {
    if ((i === 0 && j === 0) || (i === 0 && j === al.length - 1) || (i === al.length - 1 && j === 0)) return;
    for (let dy = -2; dy <= 2; dy++) for (let dx = -2; dx <= 2; dx++) set(ax + dx, ay + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
  }));
  const formatBits = mask => {
    const d = (0 << 3) | mask; let r = d;   // M is 00
    for (let i = 0; i < 10; i++) r = (r << 1) ^ ((r >>> 9) * 0x537);
    const b = ((d << 10) | (r & 0x3ff)) ^ 0x5412, bit = i => (b >>> i) & 1;
    for (let i = 0; i <= 5; i++) set(8, i, bit(i));
    set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8));
    for (let i = 9; i < 15; i++) set(14 - i, 8, bit(i));
    for (let i = 0; i < 8; i++) set(size - 1 - i, 8, bit(i));
    for (let i = 8; i < 15; i++) set(8, size - 15 + i, bit(i));
    set(8, size - 8, 1);
  };
  formatBits(0);
  if (ver >= 7) {
    let r = ver; for (let i = 0; i < 12; i++) r = (r << 1) ^ ((r >>> 11) * 0x1f25);
    const b = (ver << 12) | (r & 0xfff);
    for (let i = 0; i < 18; i++) { const d = (b >>> i) & 1, a = size - 11 + i % 3, c = Math.floor(i / 3); set(a, c, d); set(c, a, d); }
  }
  // The codewords, in the zigzag.
  let i = 0;
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5;
    for (let v = 0; v < size; v++) for (let j = 0; j < 2; j++) {
      const x = right - j, up = ((right + 1) & 2) === 0, y = up ? size - 1 - v : v;
      if (!fn[y * size + x] && i < words.length * 8) { dark[y * size + x] = (words[i >>> 3] >>> (7 - (i & 7))) & 1; i++; }
    }
  }
  // The mask with the lowest penalty (runs, 2 × 2 blocks and balance).
  const MASKS = [(x, y) => (x + y) % 2 === 0, (x, y) => y % 2 === 0, x => x % 3 === 0, (x, y) => (x + y) % 3 === 0,
    (x, y) => (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0, (x, y) => x * y % 2 + x * y % 3 === 0,
    (x, y) => (x * y % 2 + x * y % 3) % 2 === 0, (x, y) => ((x + y) % 2 + x * y % 3) % 2 === 0];
  const apply = m => { for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) if (!fn[y * size + x] && MASKS[m](x, y)) dark[y * size + x] ^= 1; };
  const penalty = () => {
    let p = 0, n = 0;
    for (let a = 0; a < size; a++) for (const row of [true, false]) {
      let run = 0, prev = -1;
      for (let b = 0; b < size; b++) { const v = row ? dark[a * size + b] : dark[b * size + a]; if (v === prev) { run++; if (run === 5) p += 3; else if (run > 5) p++; } else { run = 1; prev = v; } }
    }
    for (let y = 0; y < size - 1; y++) for (let x = 0; x < size - 1; x++) { const v = dark[y * size + x]; if (v === dark[y * size + x + 1] && v === dark[(y + 1) * size + x] && v === dark[(y + 1) * size + x + 1]) p += 3; }
    for (const v of dark) n += v;
    return p + Math.floor(Math.abs(n * 20 - size * size * 10) / (size * size)) * 10;
  };
  let best = 0, bestP = Infinity;
  for (let m = 0; m < 8; m++) { apply(m); formatBits(m); const p = penalty(); if (p < bestP) { bestP = p; best = m; } apply(m); }
  apply(best); formatBits(best);
  return { size, dark, version: ver };
}
