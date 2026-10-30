// ===================================================================================================================
// Brush Studio: brushes for other apps (docs/superpowers/specs/2026-10-20-brush-export-design.md). Today one format:
// a Procreate .brushset, made here in the page with no server: a stored zip, greyscale PNGs, and each brush's
// Brush.archive (an NSKeyedArchiver binary plist) patched from a real brush (PROCREATE_TEMPLATE, set by build.cjs from
// procreate-template.json: CSP2PC's Seed.brush, MIT). The engine draws each brush's Shape.png (one tap of its tip),
// Grain.png (its paper) and thumbnail. No DOM: Node's checks (export-check.cjs) run the same code.
// ===================================================================================================================

// ---------- CRC-32 and a stored (uncompressed) zip: the PNGs inside are already deflated ----------
const CRC_TABLE = (() => { const t = new Uint32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c >>> 0; } return t; })();
function crc32(bytes, crc = 0) {
  crc = ~crc >>> 0;
  for (let i = 0; i < bytes.length; i++) crc = CRC_TABLE[(crc ^ bytes[i]) & 255] ^ (crc >>> 8);
  return ~crc >>> 0;
}
const utf8 = s => new TextEncoder().encode(s);
// files: [{ name, data: Uint8Array }], a name ending "/" a folder. Returns the zip's bytes.
function zipStore(files, when = new Date()) {
  const time = (when.getHours() << 11) | (when.getMinutes() << 5) | (when.getSeconds() >> 1);
  const date = ((Math.max(1980, when.getFullYear()) - 1980) << 9) | ((when.getMonth() + 1) << 5) | when.getDate();
  const parts = [], central = [];
  let offset = 0;
  for (const f of files) {
    const name = utf8(f.name), data = f.data || new Uint8Array(0), crc = crc32(data), dir = f.name.endsWith("/");
    const local = new DataView(new ArrayBuffer(30));
    local.setUint32(0, 0x04034b50, true); local.setUint16(4, 10, true); local.setUint16(6, 0x0800, true); local.setUint16(8, 0, true);
    local.setUint16(10, time, true); local.setUint16(12, date, true); local.setUint32(14, crc, true);
    local.setUint32(18, data.length, true); local.setUint32(22, data.length, true); local.setUint16(26, name.length, true); local.setUint16(28, 0, true);
    const cen = new DataView(new ArrayBuffer(46));
    cen.setUint32(0, 0x02014b50, true); cen.setUint16(4, 0x0314, true); cen.setUint16(6, 10, true); cen.setUint16(8, 0x0800, true); cen.setUint16(10, 0, true);
    cen.setUint16(12, time, true); cen.setUint16(14, date, true); cen.setUint32(16, crc, true); cen.setUint32(20, data.length, true); cen.setUint32(24, data.length, true);
    cen.setUint16(28, name.length, true); cen.setUint32(38, (dir ? 0o40755 : 0o100644) << 16 | (dir ? 0x10 : 0), true); cen.setUint32(42, offset, true);
    parts.push(new Uint8Array(local.buffer), name, data);
    central.push(new Uint8Array(cen.buffer), name);
    offset += 30 + name.length + data.length;
  }
  const cenSize = central.reduce((n, b) => n + b.length, 0), end = new DataView(new ArrayBuffer(22));
  end.setUint32(0, 0x06054b50, true); end.setUint16(8, files.length, true); end.setUint16(10, files.length, true);
  end.setUint32(12, cenSize, true); end.setUint32(16, offset, true);
  return concatBytes([...parts, ...central, new Uint8Array(end.buffer)]);
}
function concatBytes(list) {
  const out = new Uint8Array(list.reduce((n, b) => n + b.length, 0));
  let o = 0; for (const b of list) { out.set(b, o); o += b.length; }
  return out;
}

// ---------- PNG: 8-bit greyscale (1 channel) or RGBA (4), deflated by the browser's CompressionStream ----------
async function deflateZlib(bytes) {
  const stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream("deflate"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}
async function encodePng(w, h, px, channels) {
  const row = w * channels, raw = new Uint8Array((row + 1) * h);
  // Each row filtered Sub (1): the difference from the pixel to its left, which deflates paper grain far better.
  for (let y = 0; y < h; y++) {
    const o = y * (row + 1), s = y * row; raw[o] = 1;
    for (let i = 0; i < row; i++) raw[o + 1 + i] = (px[s + i] - (i >= channels ? px[s + i - channels] : 0)) & 255;
  }
  const chunk = (type, data) => {
    const out = new Uint8Array(12 + data.length), v = new DataView(out.buffer), t = utf8(type);
    v.setUint32(0, data.length); out.set(t, 4); out.set(data, 8); v.setUint32(8 + data.length, crc32(concatBytes([t, data])));
    return out;
  };
  const ihdr = new Uint8Array(13), iv = new DataView(ihdr.buffer);
  iv.setUint32(0, w); iv.setUint32(4, h); ihdr[8] = 8; ihdr[9] = channels === 4 ? 6 : 0;
  return concatBytes([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", await deflateZlib(raw)), chunk("IEND", new Uint8Array(0))]);
}

// ---------- binary plist (bplist00) ----------
// Values as procreate-template.json holds them: a number is a real, { $int } an integer, { $uid } an archiver's
// reference, { $data } base64 bytes; strings, booleans, arrays and objects (dicts) as they are.
const isTag = (v, t) => v && typeof v === "object" && !Array.isArray(v) && Object.keys(v).length === 1 && t in v;
function b64bytes(s) { const b = atob(s), out = new Uint8Array(b.length); for (let i = 0; i < b.length; i++) out[i] = b.charCodeAt(i); return out; }
function writeBplist(top) {
  const objs = [], strings = new Map();
  // Flattened depth first; equal strings share one object, as Apple's writer does.
  const add = v => {
    if (typeof v === "string") { if (strings.has(v)) return strings.get(v); }
    const i = objs.length; objs.push(null);
    if (typeof v === "string") { strings.set(v, i); objs[i] = { v }; }
    else if (Array.isArray(v)) objs[i] = { v, refs: v.map(add) };
    else if (v && typeof v === "object" && !isTag(v, "$int") && !isTag(v, "$uid") && !isTag(v, "$data")) {
      const keys = Object.keys(v), kr = keys.map(add), vr = keys.map(k => add(v[k]));
      objs[i] = { v, refs: [...kr, ...vr], dict: true };
    } else objs[i] = { v };
    return i;
  };
  add(top);
  const refSize = objs.length < 256 ? 1 : objs.length < 65536 ? 2 : 4, out = [], offsets = [];
  let pos = 8;
  const push = b => { out.push(b); pos += b.length; };
  const be = (n, size) => { const b = new Uint8Array(size); for (let k = size - 1; k >= 0; k--) { b[k] = n & 255; n = Math.floor(n / 256); } return b; };
  const marker = (type, n) => n < 15 ? new Uint8Array([type << 4 | n]) : concatBytes([new Uint8Array([type << 4 | 15]), intObj(n)]);
  function intObj(n) {
    if (n >= 0 && n < 256) return new Uint8Array([0x10, n]);
    if (n >= 0 && n < 65536) return concatBytes([new Uint8Array([0x11]), be(n, 2)]);
    if (n >= 0 && n < 4294967296) return concatBytes([new Uint8Array([0x12]), be(n, 4)]);
    const b = new Uint8Array(9), dv = new DataView(b.buffer); b[0] = 0x13; dv.setBigInt64(1, BigInt(Math.round(n))); return b;
  }
  for (const o of objs) {
    offsets.push(pos);
    const v = o.v;
    if (o.refs && !o.dict) push(concatBytes([marker(0xA, o.refs.length), ...o.refs.map(r => be(r, refSize))]));
    else if (o.dict) push(concatBytes([marker(0xD, o.refs.length / 2), ...o.refs.map(r => be(r, refSize))]));
    else if (typeof v === "boolean") push(new Uint8Array([v ? 0x09 : 0x08]));
    else if (typeof v === "number") { const b = new Uint8Array(9), dv = new DataView(b.buffer); b[0] = 0x23; dv.setFloat64(1, v); push(b); }
    else if (typeof v === "string") {
      if (/^[\x00-\x7f]*$/.test(v)) push(concatBytes([marker(0x5, v.length), Uint8Array.from(v, c => c.charCodeAt(0))]));
      else { const u = new Uint8Array(v.length * 2); for (let k = 0; k < v.length; k++) { u[2 * k] = v.charCodeAt(k) >> 8; u[2 * k + 1] = v.charCodeAt(k) & 255; } push(concatBytes([marker(0x6, v.length), u])); }
    }
    else if (isTag(v, "$int")) push(intObj(v.$int));
    else if (isTag(v, "$uid")) { const n = v.$uid, size = n < 256 ? 1 : n < 65536 ? 2 : 4; push(concatBytes([new Uint8Array([0x80 | (size - 1)]), be(n, size)])); }
    else if (isTag(v, "$data")) { const d = b64bytes(v.$data); push(concatBytes([marker(0x4, d.length), d])); }
    else throw new Error("bplist: can't write " + JSON.stringify(v));
  }
  const tableAt = pos, offSize = tableAt < 256 ? 1 : tableAt < 65536 ? 2 : 4;
  for (const o of offsets) push(be(o, offSize));
  const trailer = new Uint8Array(32); trailer[6] = offSize; trailer[7] = refSize;
  trailer.set(be(objs.length, 8), 8); trailer.set(be(0, 8), 16); trailer.set(be(tableAt, 8), 24);
  push(trailer);
  return concatBytes([utf8("bplist00"), ...out]);
}

// ---------- drawing a brush's pictures with the engine ----------
const SHAPE_PX = 1024, GRAIN_PX = 512, THUMB_W = 1060, THUMB_H = 324;
// The brush as one clean dab: smooth paper, Instant, no smudge, no tapers or wobble, a dark mark (White and the rest
// would draw nothing on a clear page). A Spiro's loops don't make a dab: a round one stands in.
function exportDab(B0) {
  const B = cloneBrush(B0);
  B.paper = freshPart("paper", "plain"); B.life = freshPart("life", "none"); B.carry = freshPart("carry", "off");
  if (B.tip.k === "spiro") B.tip = switchChoice("tip", B.tip, "round");
  for (const k of ["taperIn", "taperOut", "wobble", "flick"]) if (k in B.tip.d) B.tip.d[k] = 0;
  if ("weight" in B.tip.d) B.tip.d.weight = 1;
  if (B.medium.k === "resist") B.medium = switchChoice("medium", B.medium, "ink");
  if ("blend" in B.medium.d) B.medium.d.blend = 0;
  return normBrush(B);
}
// Shape.png: one tap of the tip (a short stroke for a tip that only draws moving, as Hake's), white where it paints,
// centred on the tap, cropped to the mark and scaled to SHAPE_PX. Tips that open with pressure tap at half.
function exportShape(B0) {
  const W = 768, c = W / 2, size = 220, s = modSurface(W, W, 7);
  const draw = (B, len) => {
    blankPage(s);
    const p = B.tip.k === "split" || B.tip.k === "multi" ? .5 : 1, st = new Stroke(s, B, size, 1, null);
    st.add([[c - len / 2, c, p, 0, NaN, NaN]]); st.add([[c + len / 2 + .5, c, p, 16, NaN, NaN]]); st.end();
    const cov = new Float32Array(W * W); let most = 0;
    for (let i = 0; i < cov.length; i++) { const v = clamp((PAPER_TONE - s.page[i]) / PAPER_TONE, 0, 1); cov[i] = v; if (v > most) most = v; }
    return most > .05 ? { cov, most } : null;
  };
  const B = exportDab(B0);
  const got = draw(B, 0) || draw(B, size * .12) || draw(exportDab(fromOld(BUILT[0])), 0);
  const { cov, most } = got;
  let reach = 0;
  for (let y = 0; y < W; y++) for (let x = 0; x < W; x++) if (cov[y * W + x] > most * .03) reach = Math.max(reach, Math.abs(x - c), Math.abs(y - c));
  const half = Math.max(8, Math.ceil(reach * 1.06) + 2), out = new Uint8Array(SHAPE_PX * SHAPE_PX);
  const at = (x, y) => x < 0 || y < 0 || x >= W || y >= W ? 0 : cov[y * W + x];
  for (let j = 0; j < SHAPE_PX; j++) for (let i = 0; i < SHAPE_PX; i++) {
    const fx = c - half + (i + .5) / SHAPE_PX * 2 * half - .5, fy = c - half + (j + .5) / SHAPE_PX * 2 * half - .5;
    const x = Math.floor(fx), y = Math.floor(fy), tx = fx - x, ty = fy - y;
    const v = lerp(lerp(at(x, y), at(x + 1, y), tx), lerp(at(x, y + 1), at(x + 1, y + 1), tx), ty) / most;
    out[j * SHAPE_PX + i] = Math.round(clamp(v, 0, 1) * 255);
  }
  return out;
}
// Grain.png: the paper's height map (the lab's scan), white at the high points, tiled to GRAIN_PX; none for smooth paper.
function exportGrain(B) {
  const t = B.paper.k !== "plain" && (B.paper.d.tooth ?? 0) > .02 && typeof TEXTURES !== "undefined" ? TEXTURES[B.paper.k] : null;
  if (!t) return null;
  let lo = 1, hi = 0; for (const v of t) { if (v < lo) lo = v; if (v > hi) hi = v; }
  const out = new Uint8Array(GRAIN_PX * GRAIN_PX), k = 255 / Math.max(1e-6, hi - lo);
  for (let y = 0; y < GRAIN_PX; y++) for (let x = 0; x < GRAIN_PX; x++) out[y * GRAIN_PX + x] = Math.round((t[(y & 255) * 256 + (x & 255)] - lo) * k);
  return out;
}
// QuickLook/Thumbnail.png: a stroke of the brush across, with pressure rising, in its 16 greys as black on clear.
function exportThumb(B0) {
  const B = cloneBrush(B0); if ("blend" in B.medium.d) B.medium.d.blend = 0; normBrush(B);
  const path = [];
  for (let i = 0; i <= 40; i++) { const u = i / 40; path.push({ x: u * 3.3, y: .5 + .32 * Math.sin(u * Math.PI * 2), p: .2 + .75 * Math.sin(Math.PI * u), v: 120 }); }
  const r = studioJob({ k: "path", B, w: THUMB_W, h: THUMB_H, shade: 1, path, size: THUMB_H * .14 }), out = new Uint8Array(THUMB_W * THUMB_H * 4);
  for (let i = 0; i < r.gray.length; i++) out[4 * i + 3] = 255 - r.gray[i];
  return out;
}

// ---------- the dials as Procreate's keys (the spec's table: what maps, what is approximate, what stays) ----------
const PR_FUZZY = new Set(["charcoal", "graphite", "crayon"]);
function procreateValues(B) {
  const t = B.tip, m = B.medium, h = B.hand.d, gain = h.gain ?? 1, amt = v => clamp((v || 0) * gain, 0, 1);
  const mm = (typeof styleSize === "function" ? styleSize(B.style || WILDCARD, B) : 16) * (t.d.weight ?? 1) * 25.4 / 300;
  const taper = Math.max(t.d.taperIn || 0, t.d.taperOut || 0) > 0;
  const v = {
    // Size: the slider's starting place, from the brush's size in mm (a guess at Procreate's scale, for the iPad test).
    paintSize: clamp(mm / 24, .01, 1),
    plotJitter: .05 * (t.d.wobble || 0),
    pencilTaperStartLength: .35 * (t.d.taperIn || 0), pencilTaperEndLength: .35 * (t.d.taperOut || 0), pencilTaperSize: taper ? .9 : 0, pencilTaperOpacity: 0,
    taperStartLength: .35 * (t.d.taperIn || 0), taperEndLength: .35 * (t.d.taperOut || 0), taperSize: taper ? .9 : 0, taperOpacity: 0,
    dynamicsPressureSize: amt(h.ps), dynamicsPressureOpacity: amt(h.pd),
    dynamicsTiltSize: amt(h.ts), dynamicsTiltOpacity: amt(h.td),
    // Speed: only Thinner has a clear twin (Procreate's speed size shrinks a fast stroke).
    dynamicsSpeedSize: (h.dir | 0) === 0 ? amt(h.ss) : 0, dynamicsSpeedOpacity: amt(h.sd),
    maxOpacity: 1, paintOpacity: 1,
    blendMode: 0, extendedBlend: 0, burntEdgesAmount: 0, wetEdgesAmount: 0,
    shapeRotation: 0, shapeRandomise: false, shapeFlipXJitter: false
  };
  if (m.k === "wash") { v.blendMode = v.extendedBlend = 1; v.paintOpacity = .75; v.burntEdgesAmount = clamp(m.d.pool || 0, 0, 1); }
  if (m.k === "cover") v.paintOpacity = clamp(m.d.cover ?? 1, .15, 1);
  if (m.k === "graphite") v.paintOpacity = .45 + .55 * clamp(m.d.grade ?? .4, 0, 1);
  // Spacing: Procreate keeps the square of its slider (5% is .0025, as the template's).
  if (t.k === "stipple") { v.plotSpacing = .3 * .3; v.shapeRandomise = true; v.shapeFlipXJitter = true; }
  if (t.k === "stamp") v.plotSpacing = Math.pow(clamp(t.d.spacing ?? .12, .05, 1), 2);
  // Grain: still under the stroke, as paper; deeper for the dry media that catch the high points.
  const tooth = B.paper.k === "plain" ? 0 : clamp(B.paper.d.tooth ?? 0, 0, 1);
  v.textureMovement = 0;
  v.grainDepth = PR_FUZZY.has(m.k) ? clamp(.3 + tooth, 0, 1) : tooth;
  return v;
}
// The template's archive with this brush's name, date and values; each key keeps the template's type.
function procreateArchive(B, name, hasGrain) {
  const A = JSON.parse(JSON.stringify(PROCREATE_TEMPLATE.archive)), objs = A.$objects, root = objs[A.$top.root.$uid];
  const set = (key, v) => {
    if (!(key in root)) return;
    const was = root[key];
    root[key] = isTag(was, "$int") ? { $int: Math.round(v) } : typeof was === "boolean" ? !!v : +v;
  };
  for (const [k, v] of Object.entries(procreateValues(B))) set(k, v);
  objs[root.name.$uid] = name;
  const date = objs[root.creationDate.$uid]; date["NS.time"] = Date.now() / 1000 - 978307200;
  if (hasGrain) root.bundledGrainPath = { $uid: 0 };
  return A;
}
function uuidUpper() {
  const b = new Uint8Array(16); crypto.getRandomValues(b); b[6] = b[6] & 15 | 64; b[8] = b[8] & 63 | 128;
  const h = [...b].map(x => x.toString(16).padStart(2, "0")).join("").toUpperCase();
  return h.slice(0, 8) + "-" + h.slice(8, 12) + "-" + h.slice(12, 16) + "-" + h.slice(16, 20) + "-" + h.slice(20);
}
const xmlEsc = t => String(t).replace(/[&<>]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));
// The set: one folder a brush (an upper-case UUID: Brush.archive, Shape.png, Grain.png when the paper has one,
// QuickLook/Thumbnail.png) and brushset.plist naming the set and listing the folders. onEach(i) after each brush.
async function procreateSet(brushes, setName, onEach) {
  const files = [], ids = [];
  for (let i = 0; i < brushes.length; i++) {
    const B = brushes[i], id = uuidUpper(), grain = exportGrain(B);
    ids.push(id);
    files.push({ name: id + "/" }, { name: id + "/QuickLook/" });
    files.push({ name: id + "/Brush.archive", data: writeBplist(procreateArchive(B, B.name || "brush", !!grain)) });
    files.push({ name: id + "/Shape.png", data: await encodePng(SHAPE_PX, SHAPE_PX, exportShape(B), 1) });
    if (grain) files.push({ name: id + "/Grain.png", data: await encodePng(GRAIN_PX, GRAIN_PX, grain, 1) });
    files.push({ name: id + "/QuickLook/Thumbnail.png", data: await encodePng(THUMB_W, THUMB_H, exportThumb(B), 4) });
    if (onEach) await onEach(i);
  }
  const plist = '<?xml version="1.0" encoding="UTF-8"?>\n<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">\n' +
    '<plist version="1.0">\n<dict>\n\t<key>brushes</key>\n\t<array>\n' + ids.map(u => "\t\t<string>" + u + "</string>\n").join("") +
    "\t</array>\n\t<key>name</key>\n\t<string>" + xmlEsc(setName) + "</string>\n</dict>\n</plist>\n";
  files.push({ name: "brushset.plist", data: utf8(plist) });
  return zipStore(files);
}
