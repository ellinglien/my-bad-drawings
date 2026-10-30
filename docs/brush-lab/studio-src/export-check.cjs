// Brush export's checks, in Node with no Procreate (the brush export spec, "Checks without Procreate"): makes a
// .brushset from example brushes with export.js as the page runs it, then reads it back with readers written here
// (zip, PNG, binary plist) and, when python3 is there, with Python's plistlib: the file list, the set's plist, the PNGs'
// sizes, greys and polarity, and every archive key and type against the template's.
//   node export-check.cjs                     after build.cjs; writes out/check.brushset; exit 1 on any failure
//   node export-check.cjs --template <Brush.archive> <source>   writes procreate-template.json from a real brush
"use strict";
const fs = require("fs"), path = require("path"), zlib = require("zlib"), { execFileSync } = require("child_process");

// ---------- readers ----------
function readBplist(buf) {
  const b = Buffer.from(buf);
  if (b.toString("latin1", 0, 8) !== "bplist00") throw new Error("not a bplist00");
  const t = b.length - 32, offSize = b[t + 6], refSize = b[t + 7], n = Number(b.readBigUInt64BE(t + 8)), top = Number(b.readBigUInt64BE(t + 16)), table = Number(b.readBigUInt64BE(t + 24));
  const uint = (at, size) => { let v = 0; for (let k = 0; k < size; k++) v = v * 256 + b[at + k]; return v; };
  const offs = []; for (let i = 0; i < n; i++) offs.push(uint(table + i * offSize, offSize));
  const read = i => {
    let at = offs[i]; const m = b[at], type = m >> 4; let len = m & 15;
    const count = () => { if (len === 15) { const im = b[at + 1], s = 1 << (im & 15); len = uint(at + 2, s); return at + 2 + s; } return at + 1; };
    switch (type) {
      case 0: if (m === 8) return false; if (m === 9) return true; throw new Error("bplist: marker " + m);
      case 1: { const s = 1 << len; return { $int: s === 8 ? Number(b.readBigInt64BE(at + 1)) : uint(at + 1, s) }; }
      case 2: return len === 2 ? b.readFloatBE(at + 1) : b.readDoubleBE(at + 1);
      case 3: return { $date: b.readDoubleBE(at + 1) };
      case 4: { const s = count(); return { $data: b.subarray(s, s + len).toString("base64") }; }
      case 5: { const s = count(); return b.toString("latin1", s, s + len); }
      case 6: { const s = count(); let o = ""; for (let k = 0; k < len; k++) o += String.fromCharCode(b.readUInt16BE(s + 2 * k)); return o; }
      case 8: return { $uid: uint(at + 1, len + 1) };
      case 10: { const s = count(), out = []; for (let k = 0; k < len; k++) out.push(read(uint(s + k * refSize, refSize))); return out; }
      case 13: { const s = count(), out = {}; for (let k = 0; k < len; k++) out[read(uint(s + k * refSize, refSize))] = read(uint(s + (len + k) * refSize, refSize)); return out; }
      default: throw new Error("bplist: type " + type);
    }
  };
  return read(top);
}
function readZip(buf) {
  const b = Buffer.from(buf), files = new Map();
  let e = b.length - 22; while (e >= 0 && b.readUInt32LE(e) !== 0x06054b50) e--;
  if (e < 0) throw new Error("no end of central directory");
  const count = b.readUInt16LE(e + 10); let at = b.readUInt32LE(e + 16);
  for (let i = 0; i < count; i++) {
    if (b.readUInt32LE(at) !== 0x02014b50) throw new Error("bad central entry");
    const method = b.readUInt16LE(at + 10), crc = b.readUInt32LE(at + 16), size = b.readUInt32LE(at + 20), nl = b.readUInt16LE(at + 28), xl = b.readUInt16LE(at + 30), cl = b.readUInt16LE(at + 32), off = b.readUInt32LE(at + 42);
    const name = b.toString("utf8", at + 46, at + 46 + nl);
    if (b.readUInt32LE(off) !== 0x04034b50) throw new Error("bad local header for " + name);
    const start = off + 30 + b.readUInt16LE(off + 26) + b.readUInt16LE(off + 28), raw = b.subarray(start, start + b.readUInt32LE(off + 18));
    const data = method === 0 ? raw : zlib.inflateRawSync(raw);
    files.set(name, { data, crc, size, method, crcOk: (zlib.crc32 ? zlib.crc32(data) : crc) >>> 0 === crc });
    at += 46 + nl + xl + cl;
  }
  return files;
}
function readPng(buf) {
  const b = Buffer.from(buf);
  if (b.readUInt32BE(0) !== 0x89504e47) throw new Error("not a PNG");
  let at = 8, w = 0, h = 0, depth = 0, ctype = 0; const idat = [];
  while (at < b.length) {
    const len = b.readUInt32BE(at), type = b.toString("latin1", at + 4, at + 8), data = b.subarray(at + 8, at + 8 + len);
    if (type === "IHDR") { w = data.readUInt32BE(0); h = data.readUInt32BE(4); depth = data[8]; ctype = data[9]; }
    if (type === "IDAT") idat.push(data);
    at += 12 + len;
  }
  const ch = { 0: 1, 2: 3, 4: 2, 6: 4 }[ctype], raw = zlib.inflateSync(Buffer.concat(idat)), row = w * ch, px = new Uint8Array(row * h);
  if (depth !== 8) throw new Error("PNG depth " + depth);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (row + 1)], s = y * (row + 1) + 1;
    for (let i = 0; i < row; i++) {
      const a = i >= ch ? px[y * row + i - ch] : 0, up = y ? px[(y - 1) * row + i] : 0, c = y && i >= ch ? px[(y - 1) * row + i - ch] : 0, x = raw[s + i];
      const p = a + up - c, pa = Math.abs(p - a), pb = Math.abs(p - up), pc = Math.abs(p - c);
      px[y * row + i] = (f === 0 ? x : f === 1 ? x + a : f === 2 ? x + up : f === 3 ? x + ((a + up) >> 1) : x + (pa <= pb && pa <= pc ? a : pb <= pc ? up : c)) & 255;
    }
  }
  return { w, h, ctype, ch, px };
}
const most = a => { let m = -Infinity; for (const x of a) if (x > m) m = x; return m; }, least = a => { let m = Infinity; for (const x of a) if (x < m) m = x; return m; };
const typeOf = v => v === null ? "null" : Array.isArray(v) ? "array" : typeof v === "object" ? (Object.keys(v).length === 1 && /^\$(int|uid|data|date)$/.test(Object.keys(v)[0]) ? Object.keys(v)[0] : "dict") : typeof v;

// ---------- the template, once, from a real brush's archive ----------
if (process.argv[2] === "--template") {
  const [file, source] = process.argv.slice(3);
  const archive = readBplist(fs.readFileSync(file));
  const out = { source: source || "", license: process.env.TEMPLATE_LICENSE ? fs.readFileSync(process.env.TEMPLATE_LICENSE, "utf8") : "", archive };
  fs.writeFileSync(path.join(__dirname, "procreate-template.json"), JSON.stringify(out, null, 1) + "\n");
  console.log("wrote procreate-template.json: " + archive.$objects.length + " objects, " + Object.keys(archive.$objects[1]).length + " keys");
  process.exit(0);
}

// ---------- the checks ----------
let n = 0, fail = 0;
const ok = (cond, what) => { n++; if (!cond) fail++; console.log((cond ? "ok   " : "FAIL ") + what); };
(async () => {
  const E = require("./out/node-engine.cjs");
  // The paper scans, as the page loads them (port.js loadTextures), from the lab page's PNGs.
  const lab = fs.readFileSync(path.join(__dirname, "..", "prototypes", "modular-lab.html"), "utf8");
  const SRC = JSON.parse(/const TEXTURE_SRC = (\{[^\n]*\});/.exec(lab)[1]);
  for (const [k, uri] of Object.entries(SRC)) { const p = readPng(Buffer.from(uri.split(",")[1], "base64")), f = new Float32Array(256 * 256); for (let i = 0; i < f.length; i++) f[i] = p.px[i * p.ch] / 255; E.TEXTURES[k] = f; }
  const T = E.PROCREATE_TEMPLATE.archive;

  // The writer: the template written and read back is the template.
  ok(JSON.stringify(readBplist(E.writeBplist(T))) === JSON.stringify(T), "bplist: the template written and read back is unchanged (" + T.$objects.length + " objects)");

  // Example brushes: the Basic tools, then one of each tip the MVP covers and two that only stand in (Hake, Spiro).
  const base = E.builtins()[0], mk = (name, tip, medium, paper, extra) => {
    const B = E.cloneBrush(base); B.name = name;
    B.tip = E.switchChoice("tip", B.tip, tip); B.medium = E.switchChoice("medium", B.medium, medium); B.paper = E.switchChoice("paper", B.paper, paper);
    if (extra) extra(B); return E.normBrush(B);
  };
  const brushes = [...E.builtins(),
    mk("round-ink", "round", "ink", "plain", B => { B.tip.d.taperIn = .4; B.tip.d.taperOut = .6; B.hand = E.switchChoice("hand", B.hand, "both"); }),
    mk("chisel-charcoal", "chisel", "charcoal", "cold press"), mk("flat-wash", "flat", "wash", "felt", B => { B.medium.d.pool = .6; }),
    mk("filbert-paint", "filbert", "cover", "canvas"), mk("stipple-ink", "stipple", "ink", "plain"), mk("stamp-ink", "stamp", "ink", "plain"),
    mk("hake-wash", "hake", "wash", "cold press"), mk("spiro-ink", "spiro", "ink", "plain"), mk("white-ink", "round", "ink", "plain", B => { B.medium.d.blend = 1; }),
    mk("Café crème", "round", "graphite", "laid")];
  const t0 = Date.now();
  const bytes = await E.procreateSet(brushes, "Scribulor");
  fs.mkdirSync(path.join(__dirname, "out"), { recursive: true });
  fs.writeFileSync(path.join(__dirname, "out", "check.brushset"), bytes);
  ok(bytes.length > 1000, "made a set of " + brushes.length + " brushes: " + (bytes.length / 1024 / 1024).toFixed(2) + " MB in " + ((Date.now() - t0) / 1000).toFixed(1) + " s");

  const files = readZip(bytes), names = [...files.keys()];
  ok([...files.values()].every(f => f.method === 0 && f.crcOk), "zip: every entry stored, its CRC-32 right");
  try { execFileSync("unzip", ["-tq", path.join(__dirname, "out", "check.brushset")], { stdio: "pipe" }); ok(true, "zip: unzip -t finds no errors"); }
  catch (e) { ok(e.code === "ENOENT", e.code === "ENOENT" ? "unzip not found: its test skipped" : "zip: unzip -t: " + String(e.stdout || e.message).trim()); }
  const plist = files.get("brushset.plist").data.toString("utf8");
  const ids = [...plist.matchAll(/<string>([^<]*)<\/string>/g)].map(m => m[1]), setName = ids.pop();
  ok(setName === "Scribulor" && /<key>name<\/key>/.test(plist) && /<key>brushes<\/key>/.test(plist), "brushset.plist: the set's name and its brushes");
  ok(ids.length === brushes.length && new Set(ids).size === ids.length && ids.every(u => /^[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}$/.test(u)), "one upper-case UUID a brush, all different");
  const tops = new Set(names.filter(x => x !== "brushset.plist").map(x => x.split("/")[0]));
  ok(tops.size === ids.length && ids.every(u => tops.has(u)), "a folder for each listed brush, and nothing else");

  // Python's plistlib, a reader written by others, when there is a python3.
  let py = null;
  try { execFileSync("python3", ["-c", "import plistlib"], { stdio: "ignore" }); py = "python3"; } catch (e) { /* no python */ }
  const pyRead = buf => JSON.parse(execFileSync(py, ["-I", "-c",
    "import plistlib,sys,json\np=plistlib.loads(sys.stdin.buffer.read())\no=p['$objects']\nr=o[p['$top']['root'].data]\n" +
    "print(json.dumps({'archiver':p['$archiver'],'version':p['$version'],'cls':o[r['$class'].data]['$classname'],'keys':len(r),'name':o[r['name'].data],'size':r['paintSize'],'blend':r['blendMode']}))"], { input: buf }).toString());

  const tRoot = T.$objects[1], tKeys = Object.keys(tRoot);
  brushes.forEach((B, i) => {
    const id = ids[i], f = k => files.get(id + "/" + k), tag = B.name + ": ";
    const each = [], chk = (c, w) => { if (!c) each.push(w.replace(tag, "")); };
    const want = ["Brush.archive", "Shape.png", "QuickLook/Thumbnail.png"].concat(E.exportGrain(B) ? ["Grain.png"] : []);
    chk(want.every(k => f(k)) && names.filter(x => x.startsWith(id + "/") && !x.endsWith("/")).length === want.length, tag + "files " + want.join(", "));
    const A = readBplist(f("Brush.archive").data), o = A.$objects, root = o[A.$top.root.$uid];
    chk(A.$archiver === "NSKeyedArchiver" && A.$version.$int === 100000 && o[root.$class.$uid].$classname === "SilicaBrush", tag + "archive: NSKeyedArchiver, a SilicaBrush");
    const lost = tKeys.filter(k => !(k in root)), changed = tKeys.filter(k => k in root && typeOf(root[k]) !== typeOf(tRoot[k]));
    chk(!lost.length && !changed.length && Object.keys(root).length === tKeys.length && o.length === T.$objects.length, tag + "every template key kept, no type changed" + (lost.length ? "; lost " + lost : "") + (changed.length ? "; changed " + changed : ""));
    chk(o[root.name.$uid] === B.name && o[root.creationDate.$uid]["NS.time"] > 7e8, tag + "named, dated now");
    const v = E.procreateValues(B), bad = Object.entries(v).filter(([k, x]) => k in root && typeof x === "number" && (root[k].$int ?? root[k]) !== (typeOf(root[k]) === "$int" ? Math.round(x) : x));
    chk(!bad.length && Object.values(v).every(x => typeof x === "boolean" || (x >= 0 && x <= 1)), tag + "the mapped values written, each 0 to 1" + (bad.length ? ": " + bad.map(b => b[0]) : ""));
    chk(typeOf(root.bundledGrainPath) === "$uid" && (want.includes("Grain.png") ? root.bundledGrainPath.$uid === 0 : o[root.bundledGrainPath.$uid] === "Brush-Preset-Blank.png"), tag + (want.includes("Grain.png") ? "its own Grain.png" : "the blank bundled grain"));
    const s = readPng(f("Shape.png").data);
    let inked = 0; for (const x of s.px) if (x > 128) inked++;
    chk(s.w === E.SHAPE_PX && s.h === E.SHAPE_PX && s.ctype === 0 && inked > 200 && most(s.px) === 255, tag + "Shape.png " + s.w + " × " + s.h + " greyscale, white paints (" + (inked * 100 / s.px.length).toFixed(1) + "%)");
    if (want.includes("Grain.png")) { const g = readPng(f("Grain.png").data); chk(g.w === E.GRAIN_PX && g.h === E.GRAIN_PX && g.ctype === 0 && least(g.px) === 0 && most(g.px) === 255, tag + "Grain.png " + g.w + " × " + g.h + " greyscale, full range"); }
    const q = readPng(f("QuickLook/Thumbnail.png").data);
    let seen = 0; for (let k = 3; k < q.px.length; k += 4) if (q.px[k] > 60) seen++;
    chk(q.w === 1060 && q.h === 324 && q.ctype === 6 && seen > 500, tag + "thumbnail 1060 × 324 RGBA, a stroke on clear");
    if (py) {
      const r = pyRead(f("Brush.archive").data);
      chk(r.archiver === "NSKeyedArchiver" && r.version === 100000 && r.cls === "SilicaBrush" && r.keys === tKeys.length && r.name === B.name && Math.abs(r.size - v.paintSize) < 1e-9 && r.blend === v.blendMode, tag + "plistlib reads it the same");
    }
    ok(!each.length, tag + "files, archive (every template key and type), names, values, PNGs" + (py ? ", plistlib" : "") + (each.length ? ". Failed: " + each.join("; ") : ""));
  });
  if (!py) ok(true, "python3 not found: plistlib's read skipped");

  // Polarity, the spec's: a Round's dab is white in the centre, black in the corners.
  { const s = readPng(files.get(ids[brushes.findIndex(b => b.name === "round-ink")] + "/Shape.png").data), at = (x, y) => s.px[y * s.w + x], c = s.w >> 1;
    ok(at(c, c) > 200 && at(2, 2) < 10 && at(s.w - 3, s.h - 3) < 10, "round-ink: Shape.png white in the centre (" + at(c, c) + "), black in the corners"); }
  // The mapping, the spec's table: wash multiplies, pressure, tapers, a set grain depth.
  { const v = E.procreateValues(brushes.find(b => b.name === "flat-wash")), r = E.procreateValues(brushes.find(b => b.name === "round-ink")), c = E.procreateValues(brushes.find(b => b.name === "chisel-charcoal"));
    ok(v.blendMode === 1 && v.burntEdgesAmount > .5 && r.blendMode === 0, "Wash multiplies with its dark edge, Ink is Normal");
    ok(r.dynamicsPressureSize > .3 && r.dynamicsPressureOpacity > .3 && r.pencilTaperStartLength > 0 && r.pencilTaperEndLength > r.pencilTaperStartLength && r.pencilTaperSize > 0, "Press Both, Taper In and Out carried");
    ok(c.grainDepth > .5 && v.grainDepth > 0 && r.grainDepth === 0, "grain depth from the paper's Texture, deeper for charcoal"); }
  console.log(`${n - fail} of ${n} passed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
