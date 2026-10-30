// ===================================================================================================================
// New modules (spec docs/archive/superpowers/specs/2026-10-16-new-modules-design.md), kept in this one file:
//   Blend   a Medium dial: how a mark meets what's there (the medium's own way, Lighten, Behind, Invert);
//   Stipple and Hatch, Tip choices: dots scattered along the path, parallel lines that cross with pressure;
//   Resist  a Medium choice: masking fluid or wax, shown hatched, which nothing takes on until it is rubbed off;
//   Halo    a Drying choice, shown as Mold (the owner's name, 2026-10-18; its key stays halo): veins that reach from the
//           wet stroke to nearby marks and bridge them, then set.
// Everything is appended (choices, dials, codes), so every old code reads, draws and writes exactly as before. Built
// after checks.js: it extends parts.js's tables and wraps resolve, the compose and a few of the stroke's hooks.
// ===================================================================================================================
const NM = { rolls: true, coreRolls: true, last: null };

// ---------- the tables, appended ----------
Object.assign(DIALS.tip, {
  dot: { label: "Dot Size", min: 0, max: 1, def: .35 },
  scatter: { label: "Scatter", min: 0, max: 1, def: .4 },
  jitter: { label: "Uneven Dots", min: 0, max: 1, def: .5 },
  pitch: { label: "Spacing", min: 0, max: 1, def: .4 },
  lineW: { label: "Line Weight", min: 0, max: 1, def: .3 },
  hangle: { label: "Line Angle", min: 0, max: 180, def: 45, fmt: v => Math.round(v) + "°" },
  layers: { label: "Layers", min: 1, max: 3, def: 3, int: true, fmt: v => String(Math.round(v)) },
  // Stipple's Build-up (the owner: "can it randomize position on hold?"): dots keep landing in the tip while the pen rests
  // or lingers, as a spray does. Last of Stipple's dials, written only when on.
  hold: { label: "Build-Up", choices: ["Off", "Slow", "Fast"], def: 0 }
});
// Blend's first option is the medium's own way: darken for every medium but Opaque, which covers (its Opacity). The
// second (key lighten) is shown as White: what it lays.
DIALS.medium.blend = { label: "Blend Mode", choices: ["Darken", "White", "Behind", "Invert"], def: 0 };
Object.assign(DIALS.life, { reach: { label: "Mold Length", min: 0, max: 1, def: .5 }, growth: { label: "Mold Growth", min: 0, max: 1, def: .5 } });
CHOICES.tip.push(
  { k: "stipple", label: "Stipple", dials: ["dot", "scatter", "jitter", "weight", "hold"], macro: "dot", words: ["fine", "coarse"],
    blurb: "Dots scattered along the stroke. Pressure packs them closer." },
  { k: "hatch", label: "Hatch", dials: ["pitch", "lineW", "hangle", "headTurn", "layers", "weight"], macro: "pitch", words: ["tight", "open"],
    labels: { headTurn: "Lines Follow" }, def: { headTurn: 1 }, blurb: "Parallel lines instead of a solid mark. Pressure crosses them a second and a third way." });
for (const c of CHOICES.medium) c.dials.push("blend");
choiceOf("medium", "cover").opts = { blend: ["Cover", "White", "Behind", "Invert"] };
CHOICES.medium.push({ k: "resist", label: "Resist", dials: ["erase"], macro: "erase", words: ["wax", "fluid"],
  blurb: "Masking fluid or wax. Nothing takes on it until it is rubbed off. Shown hatched." });
CHOICES.life.push({ k: "halo", label: "Mold", dials: ["drying", "reach", "growth"], macro: "reach", words: ["short", "long"], def: { drying: .55 },
  blurb: "Veins reach from the wet stroke to nearby marks and bridge them, then set as it dries." });
ADDED.add("medium.blend"); ADDED.add("tip.hold");
REAL_ERASE.resist = 1;   // masking fluid rubs off; wax (Erasable off) stays
const BLENDS = ["own", "lighten", "behind", "invert"];
// A choice dial's option as shown: Opaque's own way is "cover".
function optLabel(part, k, key, i) { const o = (choiceOf(part, k).opts || {})[key]; return (o || DIALS[part][key].choices)[i]; }
const PATTERN_TIPS = new Set(["stipple", "hatch"]);
const isNew = B => PATTERN_TIPS.has(B.tip.k) || B.medium.k === "resist" || B.life.k === "halo" || B.carry.k === "water" || ((B.medium.d.blend | 0) > 0 && B.medium.k !== "resist")
  || B.medium.k === "tone" || (B.medium.d.screen | 0) > 0;

// ---------- resolve: the new choices as the engine's own, plus what only they need ----------
const resolve0 = resolve;
resolve = function (B, size, shade) {
  const T = B.tip, tk = T.k, mk = B.medium.k, pattern = PATTERN_TIPS.has(tk), resist = mk === "resist", halo = B.life.k === "halo";
  const blend = resist ? 0 : (B.medium.d.blend | 0);
  if (!pattern && !resist && !halo && !blend) return resolve0(B, size, shade);
  let A = B;
  if (pattern || resist || halo) {
    A = cloneBrush(B);
    // A pattern tip lays its dots or lines where a round tip of its weight would reach; the stampers are its own.
    if (pattern) A.tip = { k: "round", d: { weight: T.d.weight ?? 1, taperIn: 0, taperOut: 0, dry: 0, wobble: 0, soft: .3 } };
    // Resist is laid as a crisp ink line, into its own plane instead of the page.
    if (resist) A.medium = { k: "ink", d: { wet: 0, line: 1, pool: 0, splatter: 0, bleed: 0, bloom: 0, lines: 0, offset: 0, chaos: 0, erase: B.medium.d.erase ?? 1, blend: 0 } };
    // Resist goes on paper, never on water: on Float it lays as Quick. It smears nothing (the app's: Smudge does nothing on it).
    if (resist && A.life.k === "float") A.life = freshPart("life", "wet");
    if (resist) A.carry = freshPart("carry", "off");
    // Halo is Slow's lift (no settling of its own), then the veins grow for its drying time.
    if (halo) A.life = { k: "dry", d: { drying: B.life.d.drying ?? .55, settle: 0, takes: 0, plume: .55 } };
  }
  const R = resolve0(A, size, shade);
  R.blend = BLENDS[blend];
  if (blend === 1 || blend === 3) R.settle = 0;   // settling pigment only darkens
  if (resist) { R.resist = true; R.blend = "own"; }
  if (pattern) {
    const t = key => T.d[key] ?? DIALS.tip[key].def;
    R.lineTip = "nm"; R.linePlaced = false; R.lineStateful = true;
    if (R.body) { R.bodyTip = "nm"; R.bodyStateful = true; }
    R.pat = { kind: tk, seed: B.seed >>> 0, dot: t("dot"), scatter: t("scatter"), jitter: t("jitter"), hold: tk === "stipple" ? T.d.hold | 0 : 0, pitch: t("pitch"), lineW: t("lineW"),
      angle: T.d.hangle ?? 45, fixed: ((T.d.headTurn ?? 1) | 0) === 1, layers: Math.round(T.d.layers ?? 3), ps: handCell(B, "ps"),
      dark: { pDark: handCell(B, "pd"), sDark: handCell(B, "sd"), tDark: 0 } };
  }
  if (halo) {
    const L = B.life.d, big = clamp(Math.sqrt((R.line ? R.inkSize : R.bodySize) / 6), 1, 3);
    R.halo = { reach: Math.round((12 + 68 * (L.reach ?? .5)) * big), growth: L.growth ?? .5, ticks: wetTicksOf(L.drying ?? .55),
      dark: R.line ? clamp(R.inkK) : laid(R.washK * 1.5) };
  }
  return R;
};

// ---------- Stipple and Hatch: the stampers ----------
function nmDot(s, buf, cx, cy, r, val, d, rect) {
  const w = s.w, x0 = Math.max(0, Math.floor(cx - r - 1)), x1 = Math.min(w - 1, Math.ceil(cx + r + 1)), y0 = Math.max(0, Math.floor(cy - r - 1)), y1 = Math.min(s.h - 1, Math.ceil(cy + r + 1));
  if (x1 < x0 || y1 < y0) return;
  for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
    const dx = px + .5 - cx, dy = py + .5 - cy, cov = clamp(r + .5 - Math.sqrt(dx * dx + dy * dy)) * val;
    if (cov <= 0) continue;
    const i = py * w + px;
    if (cov > buf[i]) buf[i] = cov;
    if (WET) WET[i] = d;
  }
  grow(rect, [x0, y0, x1, y1]);
}
// Stipple: every cell of path (a dot and a bit long) holds a fixed set of candidate dots, from the stroke's seed and the
// cell's number; pressure decides how many of them land. Jitter 0 sets them on a staggered lattice across the band
// (an even stipple); 1 scatters them and varies their size (a sponge). Each pass of the pen adds dots, so tone builds.
function stampStipple(st, buf, size, from, M) {
  const P = st.R.pat, s = st.surf, pts = st.pts, rect = [s.w, s.h, -1, -1];
  M.seed ??= hashInts(P.seed ^ 0x57e1, Math.round(pts[0].x * 4), Math.round(pts[0].y * 4));
  // Dots up to two fifths of the tip across (a pen's dots, a sponge's pores); clumps of up to four as Jitter nears 1.
  const dotD = clamp(size * lerp(.05, .4, P.dot * P.dot), 1.6, 60), band = Math.max(dotD * .5, size / 2 * (1 + 1.6 * P.scatter));
  const clump = P.jitter > .5 ? (P.jitter - .5) * 2 : 0, step = Math.max(1.2, dotD * .9);
  const per = Math.max(1, Math.round(2 * band * step / (dotD * dotD) * 1.1 / (1 + 1.5 * clump)));
  M.next ??= 0; M.cell ??= 0;
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d, ux = seg > 0 ? (p1.x - p0.x) / seg : 1, uy = seg > 0 ? (p1.y - p0.y) / seg : 0;
    while (M.next <= p1.d) {
      const t = seg > 0 ? clamp((M.next - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t);
      const dens = lerp(.12, 1, smooth(pr)), dk = handDark(P.dark, pr, v, 0) * (p1.own === undefined ? 1 : lerp(p0.own, p1.own, t)), bw = band * (P.ps ? lerp(1, .35 + .65 * pr, P.ps) : 1), c = M.cell;
      for (let j = 0; j < per; j++) {
        const hs = hashInts(M.seed, c, j);
        if (hash01(hs, 0) >= dens) continue;
        const lat = ((j + .5 + (c & 1) * .5) / per) % 1 * 2 - 1, rnd = hash01(hs, 1) + hash01(hs, 2) - 1;
        const across = lerp(lat, rnd, P.jitter) * bw, along = (hash01(hs, 3) - .5) * step * (.3 + .7 * P.jitter);
        nmDotClump(s, buf, P, hs, x - uy * across + ux * along, y + ux * across + uy * along, dotD, clump, dk, M.next, rect);
      }
      M.next += step; M.cell++;
    }
  }
  // Build-up: each tick's dots at random in the band round the pen, as many as cover it once in its fill time, by pressure.
  const H = pts.holds;
  if (H && P.hold > 0) {
    M.holdsLaid ??= 0; M.holdAcc ??= 0;
    const fill = HOLD_FILL_MS[Math.min(2, P.hold)];
    for (; M.holdsLaid < H.ev.length; M.holdsLaid++) {
      const e = H.ev[M.holdsLaid], pr = e.p, bw = band * (P.ps ? lerp(1, .35 + .65 * pr, P.ps) : 1), dens = lerp(.12, 1, smooth(pr));
      M.holdAcc += Math.min(HOLD_MOST_DOTS, 4 * bw * bw / (dotD * dotD) * dens * HOLD_TICK / fill);
      const n = Math.floor(M.holdAcc); M.holdAcc -= n;
      const dk = handDark(P.dark, pr, 0, 0) * (pts[pts.length - 1].own ?? 1);
      for (let j = 0; j < n; j++) {
        const hs = hashInts(M.seed ^ 0x401d, e.tick, j), a = 2 * Math.PI * hash01(hs, 1), rad = bw * Math.sqrt(hash01(hs, 2));
        nmDotClump(s, buf, P, hs, e.x + rad * Math.cos(a), e.y + rad * Math.sin(a), dotD, clump, dk, e.d, rect);
      }
    }
  }
  return rect;
}
// A candidate's dot, its size jittered, and its clump of up to three more as Jitter nears 1.
function nmDotClump(s, buf, P, hs, cx, cy, dotD, clump, dk, d, rect) {
  const r = Math.max(.8, dotD / 2 * (1 + P.jitter * .9 * (hash01(hs, 4) - .5)));
  nmDot(s, buf, cx, cy, r, dk, d, rect);
  for (let n = Math.floor(clump * 3.99 * hash01(hs, 5)), m = 0; m < n; m++)
    nmDot(s, buf, cx + (hash01(hs, 6 + m) - .5) * dotD * 1.8, cy + (hash01(hs, 9 + m) - .5) * dotD * 1.8, r * lerp(.45, .9, hash01(hs, 12 + m)), dk, d, rect);
}
// Build-up's ticks: every HOLD_TICK ms of pen time since pen-down, the pen's place, pressure and distance at the sample
// that reached it. Every sample feeds it, kept or not (a resting pen's are too close to keep), and the test area ticks a
// resting pen as the app's pen-down ticker does (the last sample again, at the time).
const HOLD_TICK = 20, HOLD_FILL_MS = [0, 4000, 1200], HOLD_MOST_TICKS = 250, HOLD_MOST_DOTS = 400;
class Holds {
  constructor() { this.t0 = NaN; this.ticks = 0; this.ev = []; }
  feed(x, y, p, t, d) {
    if (!Number.isFinite(t)) return false;
    if (this.t0 !== this.t0) { this.t0 = t; return false; }
    const now = Math.max(this.ticks, Math.min(this.ticks + HOLD_MOST_TICKS, Math.floor((t - this.t0) / HOLD_TICK)));
    if (now === this.ticks) return false;
    for (let n = this.ticks + 1; n <= now; n++) this.ev.push({ x, y, p, d, tick: n });
    this.ticks = now; return true;
  }
}
const addPt0 = addPt;
addPt = function (pts, x, y, p, t, tx, ty) {
  const kept = addPt0(pts, x, y, p, t, tx, ty);
  if (pts.holds && pts.length) { pts.lastRaw = [x, y, p, t, tx, ty]; if (pts.holds.feed(x, y, p, t, pts[pts.length - 1].d)) pts.holdsFresh = true; }
  return kept;
};
// Hatch: inside the round band, each pixel takes the lines of its nearest dab: one set of parallel lines, a second set
// at 90° where the pressure passes .45 and a third at 45° past .72 (up to Layers). Fixed: the angle is on the page, so
// strokes of one brush line up and an overlap adds nothing; Follows: the angle is to the stroke, the lines bend with it.
const HATCH_AT = [0, .45, .72], HATCH_TURN = [0, 90, 45];
function stampHatch(st, buf, size, from, M, which) {
  const P = st.R.pat, s = st.surf, pts = st.pts, w = s.w, rect = [s.w, s.h, -1, -1];
  const best = M.best ??= lazyBuf(s, "nmBest" + which, Infinity);
  const k = Math.max(1, Math.sqrt(size / 30)), pitch = lerp(3, 18, P.pitch) * k, lw = Math.min(pitch * .75, lerp(.9, 4, P.lineW) * k);
  const sn = HATCH_TURN.map(a => Math.sin((P.angle + a) * DEG)), cs = HATCH_TURN.map(a => Math.cos((P.angle + a) * DEG));
  const r0 = size / 2, spacing = Math.max(1, r0 * .15);
  M.next ??= 0;
  for (let q = Math.max(1, from); q < pts.length; q++) {
    const p0 = pts[q - 1], p1 = pts[q], seg = p1.d - p0.d;
    let ux = p1.ux ?? (seg > 0 ? (p1.x - p0.x) / seg : 1), uy = p1.uy ?? (seg > 0 ? (p1.y - p0.y) / seg : 0);
    while (M.next <= p1.d) {
      const t = seg > 0 ? clamp((M.next - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t), d = M.next;
      const r = r0 * (P.ps ? lerp(1, .35 + .65 * pr, P.ps) : 1), dk = handDark(P.dark, pr, v, 0) * (p1.own === undefined ? 1 : lerp(p0.own, p1.own, t));
      let layers = 1; while (layers < P.layers && pr >= HATCH_AT[layers]) layers++;
      const x0 = Math.max(0, Math.floor(x - r - 1)), x1 = Math.min(w - 1, Math.ceil(x + r + 1)), y0 = Math.max(0, Math.floor(y - r - 1)), y1 = Math.min(s.h - 1, Math.ceil(y + r + 1));
      const R2 = (r + .5) * (r + .5);
      for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
        const dx = px + .5 - x, dy = py + .5 - y, d2 = dx * dx + dy * dy, i = py * w + px;
        if (d2 > R2 || d2 >= best[i]) continue;
        best[i] = d2;
        const X = P.fixed ? px + .5 : d + dx * ux + dy * uy, Y = P.fixed ? py + .5 : -dx * uy + dy * ux;
        let cov = 0;
        for (let L = 0; L < layers; L++) {
          const m = (P.fixed ? X * sn[L] + Y * cs[L] : -X * sn[L] + Y * cs[L]) / pitch + L * .37, f = Math.abs(m - Math.round(m)) * pitch, c = clamp(lw / 2 + .5 - f);
          if (c > cov) cov = c;
        }
        buf[i] = cov * clamp(r + .5 - Math.sqrt(d2)) * dk;
        if (WET && buf[i] > 0) WET[i] = d;
      }
      grow(rect, [x0, y0, x1, y1]);
      M.next += spacing;
    }
  }
  return rect;
}
function stampPattern(st, buf, size, from, which) {
  const M = st["nm" + which] ??= {};
  return st.R.pat.kind === "stipple" ? stampStipple(st, buf, size, from, M) : stampHatch(st, buf, size, from, M, which);
}

// ---------- the compose: Blend and Resist over the medium's own compose ----------
// Behind: how blank the page is, 1 on paper to 0 from mid grey down; a mark goes only where it is still blank.
const blankOf = u => smoothstep(.55, .96, u / PAPER_TONE), WAX_SHEEN = .07;
const composeOwn = composeStroke;
composeStroke = function (st, rect, phase) {
  const R = st.R, s = st.surf, blend = R.blend || "own", rp = s.resist;
  if (!R.resist && blend === "own" && !rp) return composeOwn(st, rect, phase);
  const w = s.w, x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(s.w - 1, rect[2]), y1 = Math.min(s.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const page = s.page, base = s.base, P = PAPER_TONE, kept = s.kept && st.e < 1 ? s.kept : null, kb = kept ? s.keptBase : null, ke = 1 - st.e;
  if (!R.resist && (blend === "own" || blend === "behind")) {
    composeOwn(st, rect, phase);
    for (let y = y0; y <= y1; y++) for (let i = y * w + x0, e = y * w + x1; i <= e; i++) {
      const f = (blend === "behind" ? blankOf(base[i]) : 1) * (rp ? 1 - rp[i] : 1);
      if (f === 1) continue;
      page[i] = base[i] + (page[i] - base[i]) * f;
      if (kept) kept[i] = kb[i] + (kept[i] - kb[i]) * f;
    }
    return;
  }
  // Lighten, Invert and Resist need the mark itself: the compose over blank paper gives its darkness a at each pixel.
  const paper = s.nmPaper ??= new Float32Array(s.w * s.h).fill(P), keepK = s.kept, keepKB = s.keptBase;
  s.base = paper; s.kept = null; s.keptBase = null;
  try { composeOwn(st, rect, phase); } finally { s.base = base; s.kept = keepK; s.keptBase = keepKB; }
  const rb = s.resistBase, rk = s.resistKept, rkb = s.resistKeptBase;
  const to = blend === "lighten" ? (u, a) => u + (P - u) * a : (u, a) => u + (P - 2 * u) * a;
  const tooth = R.resist ? R.lineTooth || 0 : 0, t0 = .25 + .5 * tooth;
  for (let y = y0; y <= y1; y++) for (let i = y * w + x0, e = y * w + x1; i <= e; i++) {
    let a = clamp(1 - page[i] / P); const u = base[i];
    if (R.resist) {
      // Wax (the part that can't lift) catches only the paper's high points; masking fluid, a liquid, fills the grain.
      if (tooth > 0 && ke > 0) a *= 1 - ke + ke * smoothstep(t0 - .1, t0 + .1, s.fine[i] * .65 + s.mid[i] * .35);
      // Into the resist planes: how strongly it shields, and the part the eraser can't lift. Masking fluid leaves the page
      // as it was (it shows hatched); wax lays a faint sheen of its own, one grey step, that stays in the drawing.
      // The part that can't lift adds to what was kept as the app's write rule does (ToneDocument: kept k + a·(1 − k)), at
      // most the whole shield.
      if (a > 0) { rp[i] = Math.max(rb[i], a); rk[i] = Math.min(rp[i], rkb[i] + a * ke * (1 - rkb[i])); }
      page[i] = u * (1 - WAX_SHEEN * a * ke);
      if (kept) kept[i] = kb[i] * (1 - WAX_SHEEN * a * ke);
      continue;
    }
    const f = rp ? 1 - rp[i] : 1;
    page[i] = u + (to(u, a) - u) * f;
    if (kept) { const k0 = kb[i]; kept[i] = k0 + (to(k0, a) - k0) * ke * f; }
  }
};
function resistBegin(s) {
  const n = s.w * s.h;
  s.resist ??= new Float32Array(n); s.resistKept ??= new Float32Array(n);
  (s.resistBase ??= new Float32Array(n)).set(s.resist); (s.resistKeptBase ??= new Float32Array(n)).set(s.resistKept);
}
// What else writes the page takes the shield too: Slow's settling, Float's print, a smudge's lay-down, the eraser.
const waterMul0 = Water.prototype.mul;
Water.prototype.mul = function (i, f, e) { const r = this.s.resist; if (r && r[i] > 0) f = 1 - (1 - f) * (1 - r[i]); return waterMul0.call(this, i, f, e); };
let NM_ENDING = null;
const addWet0 = Water.prototype.addWet;
Water.prototype.addWet = function (X0, Y0, rw, rh, D, ...rest) {
  const st = NM_ENDING;
  if (st && st.R.blend === "behind") {
    // A wash behind marks settles only where it landed: its suspended pigment scaled by how blank the page was.
    const base = st.surf.base, w = st.surf.w; D = Float32Array.from(D);
    for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) D[y * rw + x] *= blankOf(base[(y + Y0) * w + x + X0]);
  }
  return addWet0.call(this, X0, Y0, rw, rh, D, ...rest);
};
const bathPrint0 = Bath.prototype.print;
Bath.prototype.print = function () {
  const s = this.s, rp = s.resist;
  if (!rp) return bathPrint0.call(this);
  const was = Float32Array.from(s.page), r = bathPrint0.call(this);
  if (r) for (let y = Math.max(0, r[1]); y <= Math.min(s.h - 1, r[3]); y++) for (let i = y * s.w + Math.max(0, r[0]), e = y * s.w + Math.min(s.w - 1, r[2]); i <= e; i++)
    if (rp[i] > 0) s.page[i] = was[i] + (s.page[i] - was[i]) * (1 - rp[i]);
  s.base.set(s.page);
  return r;
};
const blankPage0 = blankPage;
// A clear page peels every resist and stops every halo.
blankPage = function (s) { blankPage0(s); s.resist = null; s.resistKept = null; if (s.halos) s.halos = []; };
const resistMarked = s => { let n = 0; if (s.resist) for (const v of s.resist) if (v >= .5) n++; return n; };

// ---------- the stroke: the new stampers, the shield on a smudge, Halo at lift ----------
const Stroke0 = Stroke;
Stroke = class extends Stroke0 {
  constructor(surf, B, size, shade, host) {
    // Any stroke that isn't Halo sets the halos still growing, where they are (as a new stroke lays a Slow wash).
    if (surf.halos && surf.halos.length && B.life.k !== "halo") haloSet(surf);
    super(surf, B, size, shade, host);
    if (this.R.resist) resistBegin(surf);
    // Stipple's Build-up: the ticks of pen time while the pen rests or lingers (the test area's ticker feeds a resting pen).
    if (this.R.pat && this.R.pat.hold > 0) this.holds = this.pts.holds = new Holds();
  }
  // A sample that only adds Build-up's ticks (a resting pen's, too close to keep) lays their dots, as the app's add does.
  add(samples) {
    if (this.holds) this.pts.holdsFresh = false;
    const r = super.add(samples);
    if (r || !this.holds || !this.pts.holdsFresh || !this.pts.length) return r;
    return this.restamp();
  }
  // Stamps what is new since the last samples, with no new sample of its own (Build-up's ticks, a Spiro's lift), as the
  // app's ModularStroke.stampNew.
  restamp() {
    const R = this.R, s = this.surf;
    let out = [s.w, s.h, -1, -1], rl = null;
    if (R.life === "wet") {
      if (R.line) { rl = this.wetLine(); out = union(out, rl); }
      if (R.body) { const rb = this.wetBody(rl); if (rb) out = union(out, rb); }
    } else {
      if (R.line) { rl = this.stampL(s.L, false, this.done); out = union(out, rl); }
      if (R.body) out = union(out, this.stampW(s.W, false, this.done));
      if (R.life === "none" && R.line && R.body) this.mergeLineWet(rl, null, 0);
    }
    if (emptyRect(out)) return null;
    grow(this.rect, out);
    if (this.fl) return null;
    composeStroke(this, out, R.life === "dry" ? "preview" : "final");
    return out;
  }
  // The test area's ticker: a resting pen's last sample again, at time t (Float's hold otherwise).
  holdTick(t) {
    if (!this.holds) return super.holdTick(t);
    const q = this.pts.lastRaw;
    return q ? this.add([[q[0], q[1], q[2], t, q[4], q[5]]]) : null;
  }
  stampL(buf, final, from, clip, win) { return this.R.lineTip === "nm" ? stampPattern(this, buf, this.R.inkSize, from, "L") : super.stampL(buf, final, from, clip, win); }
  stampW(buf, final, from) { return this.R.bodyTip === "nm" ? stampPattern(this, buf, this.R.bodySize, from, "W") : super.stampW(buf, final, from); }
  // A smudge's dab and a stump's (Smear's Soften) over masking fluid or wax: what it shields isn't there for them. Within
  // reach of the dab each shielded pixel shows them only its loose part (the page over paper by 1 − rp: the app's
  // CarryPlane keeps the shielded share in place, as it keeps a permanent one), so nothing is picked up or blended from
  // under the fluid, and what they change there lands by 1 − rp (the app's shield on the write).
  shieldDab(x, y, reach, dab) {
    const s = this.surf, rp = s.resist;
    if (!rp) return dab();
    const w = s.w, P = PAPER_TONE, held = [];
    for (let py = Math.max(0, Math.floor(y - reach)); py <= Math.min(s.h - 1, Math.ceil(y + reach)); py++)
      for (let i = py * w + Math.max(0, Math.floor(x - reach)), e = py * w + Math.min(w - 1, Math.ceil(x + reach)); i <= e; i++)
        if (rp[i] > 0) { const o = s.base[i], t = P - (P - o) * (1 - rp[i]); held.push(i, o, t); s.base[i] = t; }
    const out = dab();
    for (let k = 0; k < held.length; k += 3) { const i = held[k]; s.base[i] = held[k + 1] + (s.base[i] - held[k + 2]) * (1 - rp[i]); }
    return out;
  }
  carryDab(x, y, pr) { return this.shieldDab(x, y, this.R.carry.r + 1, () => super.carryDab(x, y, pr)); }
  stumpDab(x, y, pr, ux, uy) {
    const C = this.R.carry, tines = C.tines ? Math.max(...C.tines.map(Math.abs)) : 0;
    // The stump reads the page a blur and a quarter radius past where it writes, at each tine.
    return this.shieldDab(x, y, (C.tines ? C.tineReach + tines : C.r) + (C.blur || 0) + .25 * C.r + 2, () => super.stumpDab(x, y, pr, ux, uy));
  }
  end() {
    const R = this.R, s = this.surf;
    NM_ENDING = this;
    let r; try { r = super.end(); } finally { NM_ENDING = null; }
    if (R.pat) for (const n of ["nmBestL", "nmBestW"]) if (s[n]) clearBuf(s, s[n], this.rect, Infinity, 2);
    if (R.halo && r) { const h = haloLift(this, r); if (h) r = union(r, h); }
    return r;
  }
};
// The eraser lifts resist first: where it passes over masking fluid it takes the fluid and leaves the paint under it
// (the next pass erases as usual); the part that can't lift (wax) shields the paint from it, as it does from paint.
const Eraser0 = Eraser;
Eraser = class extends Eraser0 {
  constructor(surf, ...rest) { if (surf.halos && surf.halos.length) haloSet(surf); super(surf, ...rest); }
  disc(cx, cy, rad, ...rest) {
    const s = this.s, rp = s.resist;
    if (!rp) return super.disc(cx, cy, rad, ...rest);
    const rk = s.resistKept, w = s.w, held = [];
    for (let y = Math.max(0, Math.floor(cy - rad)); y <= Math.min(s.h - 1, Math.ceil(cy + rad)); y++) for (let x = Math.max(0, Math.floor(cx - rad)); x <= Math.min(w - 1, Math.ceil(cx + rad)); x++) {
      const i = y * w + x;
      if (this.seen[i] || (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 > rad * rad) continue;
      if (rp[i] > rk[i] + .004) { rp[i] = rk[i]; this.seen[i] = 1; }
      else if (rk[i] > 0) held.push(i, s.page[i]);
    }
    const out = super.disc(cx, cy, rad, ...rest);
    for (let k = 0; k < held.length; k += 2) { const i = held[k], o = held[k + 1]; s.page[i] = s.base[i] = o + (s.page[i] - o) * (1 - rk[i]); }
    return out;
  }
};

// ---------- the page on a canvas: the resist hatched over it, as MonoPaint shows masking fluid ----------
// MonoPaint's own (ToneDocument.maskHatch): diagonal lines 4 px wide every 12 px where the fluid shields at least 32 of
// 255, black over light paint and white over dark. On screen only: never part of the drawing.
const HATCH = 12, HATCH_LINE = 4, DASH = 24, DASH_ON = 16, DASH_SHIFT = 12; // dashed, unlike a Lines screentone (2026-10-22)
// Only what can still be lifted is hatched: wax shows by its sheen.
const hatchAt = (s, x, y, i) => s.resist && (x + y) % HATCH < HATCH_LINE && ((x - y + DASH_SHIFT) % DASH + DASH) % DASH < DASH_ON && s.resist[i] - s.resistKept[i] >= 32 / 255 ? (s.page[i] < 128 ? 255 : 0) : -1;
const showPage0 = showPage;
showPage = function (ctx, surf, rect) {
  if (!surf.resist) return showPage0(ctx, surf, rect);
  const x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(surf.w - 1, rect[2]), y1 = Math.min(surf.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const rw = x1 - x0 + 1, rh = y1 - y0 + 1, img = ctx.createImageData(rw, rh), data = img.data;
  for (let y = 0; y < rh; y++) for (let x = 0, i = (y + y0) * surf.w + x0, o = y * rw * 4; x < rw; x++, i++, o += 4) {
    const hv = hatchAt(surf, x + x0, y + y0, i), v = hv >= 0 ? hv : quantTone(surf.page[i]);
    data[o] = data[o + 1] = data[o + 2] = v; data[o + 3] = 255;
  }
  ctx.putImageData(img, x0, y0);
};
// The page's 16 greys as 0..1 (for pictures), the resist hatched when asked.
function pageGrays(s, hatch) {
  const g = new Float32Array(s.w * s.h);
  for (let y = 0; y < s.h; y++) for (let x = 0, i = y * s.w; x < s.w; x++, i++) { const hv = hatch ? hatchAt(s, x, y, i) : -1; g[i] = (hv >= 0 ? hv : quantTone(s.page[i])) / 255; }
  return g;
}

// ---------- Halo: Living Ink's Mould (physarum), grown from a Halo stroke's edge while it is wet ----------
// Scouts leave the stroke's edge, sense left, ahead and right, steer up the trail and toward other marks within Reach,
// and lay trail; the trail diffuses and fades over the band only. Where traffic runs well above the band's mean a vein
// darkens, so the veins that last are the ones between marks. It only darkens, and sets when its time is up.
function nmChamfer(own, w, h) {
  const d = new Float32Array(w * h);
  for (let i = 0; i < d.length; i++) d[i] = own[i] >= .5 ? 0 : 1e9;
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = y * w + x; let v = d[i];
    if (x > 0) v = Math.min(v, d[i - 1] + 1);
    if (y > 0) { v = Math.min(v, d[i - w] + 1); if (x > 0) v = Math.min(v, d[i - w - 1] + 1.414); if (x < w - 1) v = Math.min(v, d[i - w + 1] + 1.414); }
    d[i] = v;
  }
  for (let y = h - 1; y >= 0; y--) for (let x = w - 1; x >= 0; x--) {
    const i = y * w + x; let v = d[i];
    if (x < w - 1) v = Math.min(v, d[i + 1] + 1);
    if (y < h - 1) { v = Math.min(v, d[i + w] + 1); if (x < w - 1) v = Math.min(v, d[i + w + 1] + 1.414); if (x > 0) v = Math.min(v, d[i + w - 1] + 1.414); }
    d[i] = v;
  }
  return d;
}
// Living Ink's Mould, tuned for thin pen lines: sensors 8 px ahead at ±43°, a little wander, food worth five trail; a vein
// where traffic passes 3 times the band's mean. Scouts that reach another mark carry back to their own stroke (shuttle) and
// lay a bridge trail as well: bridges darken to .7, the rest of the halo only to .2.
const HALO_AGENTS = 2400, HALO_TUNE = { sd: 8, sa: 43, wander: .18, food: 5, vein: .7, faint: .2, shuttle: 1, thr: 3 };
class Halo {
  constructor(s, st, rect) {
    const H = st.R.halo, W = s.w, R = H.reach;
    const x0 = Math.max(0, rect[0] - R - 3), y0 = Math.max(0, rect[1] - R - 3), x1 = Math.min(s.w - 1, rect[2] + R + 3), y1 = Math.min(s.h - 1, rect[3] + R + 3);
    const w = x1 - x0 + 1, h = y1 - y0 + 1, n = w * h;
    Object.assign(this, { s, x0, y0, w, h, R, dark: H.dark, e: st.e, behind: st.R.blend === "behind", N: H.ticks, tick: 0, rect: [x0, y0, x1, y1] });
    // The stroke's own mark (its darkness over what it found, for its own darkness), and other marks as found.
    const own = this.own = new Float32Array(n), env = this.env = new Float32Array(n);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const g = (y + y0) * W + x + x0, b = s.base[g];
      own[y * w + x] = clamp((1 - s.page[g] / Math.max(1, b)) / Math.max(.05, H.dark));
      env[y * w + x] = 1 - b / PAPER_TONE;
    }
    const dist = this.dist = nmChamfer(own, w, h), band = [];
    for (let x = 0; x < w; x++) { dist[x] = 1e9; dist[(h - 1) * w + x] = 1e9; }
    for (let y = 0; y < h; y++) { dist[y * w] = 1e9; dist[y * w + w - 1] = 1e9; }
    for (let i = 0; i < n; i++) if (dist[i] <= R) band.push(i);
    this.band = Int32Array.from(band);
    this.T = new Float32Array(n); this.T2 = new Float32Array(n); this.F = new Float32Array(n); this.F2 = new Float32Array(n); this.acc = new Float32Array(n); this.lev = new Float32Array(n); this.app = new Float32Array(n);
    this.r = rng((st.R.pen.seed ^ hashPts(st.pts) ^ 0x51a3e01d) >>> 0);
    const edges = [];
    for (let y = 1; y < h - 1; y++) for (let x = 1; x < w - 1; x++) {
      const i = y * w + x;
      if (own[i] >= .5 && (own[i - 1] < .5 || own[i + 1] < .5 || own[i - w] < .5 || own[i + w] < .5)) edges.push(i);
    }
    const K = Math.min(HALO_AGENTS, Math.round(band.length * (.01 + .05 * H.growth)));
    this.rate = .04 * (.6 + .8 * H.growth); this.ag = [];
    for (let k = 0; k < K && edges.length; k++) {
      const i = edges[(this.r() * edges.length) | 0], x = i % w + .5, y = (i / w | 0) + .5, [nx, ny] = this.outward(x, y), out = 1.5 + this.r() * this.r() * R * .8;
      if (!this.inBand(x + nx * out, y + ny * out)) continue;
      this.ag.push({ x: x + nx * out, y: y + ny * out, h: Math.atan2(ny, nx) + (this.r() - .5) * 2.4, wait: Math.floor(this.r() * this.N * .15) });
    }
    this.ok = this.ag.length > 0;
  }
  inBand(x, y) { return x >= 0 && y >= 0 && x < this.w && y < this.h && this.dist[(y | 0) * this.w + (x | 0)] <= this.R; }
  outward(x, y) {
    const { w, h, dist: d } = this, xi = clamp(x | 0, 1, w - 2), yi = clamp(y | 0, 1, h - 2), i = yi * w + xi, gx = d[i + 1] - d[i - 1], gy = d[i + w] - d[i - w], m = Math.hypot(gx, gy);
    if (m < 1e-6 || m > 1e6) { const a = this.r() * 6.2832; return [Math.cos(a), Math.sin(a)]; }
    return [gx / m, gy / m];
  }
  // Trail, and other marks as food: the scouts find them and the veins between thicken. Its own stroke is home, not food.
  // A scout seeking smells other marks; one carrying back smells its own stroke (home), so the traffic shuttles between them.
  sense(x, y, back) {
    if (!this.inBand(x, y)) return -1;
    const i = (y | 0) * this.w + (x | 0), food = back ? this.dist[i] <= 2 : this.env[i] > .5 && this.dist[i] > 2;
    return this.T[i] + (food ? HALO_TUNE.food : 0);
  }
  // Another Halo stroke lifted while this one grows: its mark is food here too.
  feed(o) {
    for (let y = Math.max(this.y0, o.y0); y < Math.min(this.y0 + this.h, o.y0 + o.h); y++) for (let x = Math.max(this.x0, o.x0); x < Math.min(this.x0 + this.w, o.x0 + o.w); x++) {
      const i = (y - this.y0) * this.w + x - this.x0, j = (y - o.y0) * o.w + x - o.x0;
      if (o.own[j] > this.env[i]) this.env[i] = o.own[j];
    }
  }
  // One tick (ten a second): vigour falls as it dries, so growth slows to nothing. Returns the page rectangle changed.
  step() {
    if (this.tick >= this.N) return null;
    const v = Math.pow(1 - this.tick / this.N, 1.4), { T, w } = this, SD = HALO_TUNE.sd, SA = HALO_TUNE.sa * DEG;
    for (let sub = 0; sub < 2; sub++) for (const a of this.ag) {
      if (a.wait > 0) { if (sub === 0) a.wait--; continue; }
      const bk = HALO_TUNE.shuttle && a.back;
      const F = this.sense(a.x + Math.cos(a.h) * SD, a.y + Math.sin(a.h) * SD, bk);
      const Lv = this.sense(a.x + Math.cos(a.h - SA) * SD, a.y + Math.sin(a.h - SA) * SD, bk);
      const Rv = this.sense(a.x + Math.cos(a.h + SA) * SD, a.y + Math.sin(a.h + SA) * SD, bk);
      if (F >= Lv && F >= Rv) { } else if (Lv > Rv) a.h -= SA * .5; else if (Rv > Lv) a.h += SA * .5; else a.h += this.r() < .5 ? -SA * .5 : SA * .5;
      a.h += (this.r() - .5) * HALO_TUNE.wander;
      const sp = .3 + .8 * v, nx = a.x + Math.cos(a.h) * sp, ny = a.y + Math.sin(a.h) * sp;
      if (!this.inBand(nx, ny)) { a.h += Math.PI * (.6 + .8 * this.r()); continue; }
      a.x = nx; a.y = ny;
      const j = (ny | 0) * w + (nx | 0); T[j] += 1;
      // A scout that has found another mark lays the bridge trail too: only bridges darken in full.
      if (a.fed) this.F[j] += 1;
      if (HALO_TUNE.shuttle) { if (!a.back && this.env[j] > .5 && this.dist[j] > 2) { a.back = a.fed = true; a.h += Math.PI; } else if (a.back && this.dist[j] <= 2.5) { a.back = false; a.h += Math.PI; } }
    }
    const T2 = this.T2, F = this.F, F2 = this.F2, band = this.band, acc = this.acc, lev = this.lev, dist = this.dist;
    let sum = 0;
    for (let k = 0; k < band.length; k++) { const i = band[k]; sum += T2[i] = .88 * (T[i] * 3 + T[i - 1] + T[i + 1] + T[i - w] + T[i + w]) / 7; F2[i] = .88 * (F[i] * 3 + F[i - 1] + F[i + 1] + F[i - w] + F[i + w]) / 7; }
    const thr = .6 + HALO_TUNE.thr * sum / Math.max(1, band.length), rate = this.rate * v, changed = [];
    for (let k = 0; k < band.length; k++) {
      const i = band[k], t = T2[i]; T[i] = t; F[i] = F2[i];
      if (t > thr && dist[i] > 2) {
        acc[i] = Math.min(1, acc[i] + (t - thr) / thr * rate * smooth((dist[i] - 2) / 7));
        const a = smooth(acc[i]) * lerp(HALO_TUNE.faint, HALO_TUNE.vein, clamp(2 * F2[i] / t));
        if (a > lev[i] + .004) { lev[i] = a; changed.push(i); }
      }
    }
    this.tick++;
    return this.write(changed);
  }
  // A vein's new darkness onto the page: multiplied in (so it commutes with every glaze), shielded, kept by Erasable.
  write(changed) {
    if (!changed.length) return null;
    const s = this.s, W = s.w, P = PAPER_TONE, rp = s.resist, K = s.kept, KB = s.keptBase, e = this.e;
    let r = null;
    for (const i of changed) {
      const x = i % this.w + this.x0, y = (i / this.w | 0) + this.y0, g = y * W + x;
      let f = (1 - this.lev[i] * this.dark) / (1 - this.app[i] * this.dark);
      this.app[i] = this.lev[i];
      if (this.behind) f = 1 - (1 - f) * blankOf(s.base[g]);
      if (rp && rp[g] > 0) f = 1 - (1 - f) * (1 - rp[g]);
      s.page[g] *= f; s.base[g] *= f;
      if (K && e < 1) { const k = 1 - (1 - e) * (1 - f); K[g] = Math.min(P, K[g] * k); if (KB) KB[g] = Math.min(P, KB[g] * k); }
      r = r ? grow(r, [x, y, x, y]) : [x, y, x, y];
    }
    return r;
  }
  run() { let r = null; while (this.tick < this.N) { const d = this.step(); if (d) r = r ? union(r, d) : d; } return r; }
}
// Sets every halo still growing, where it is.
function haloSet(s) { if (s.halos) for (const h of s.halos) h.tick = h.N; s.halos = []; }
// Every growing halo, a tick at a time together, to the end (pictures and checks); returns the rectangle changed.
function haloDryAll(s) {
  let r = null;
  while (s.halos && s.halos.length) { for (const h of s.halos) { const d = h.step(); if (d) r = r ? union(r, d) : d; } s.halos = s.halos.filter(h => h.tick < h.N); }
  return r;
}
// At lift: a halo from the stroke's edge. With a host that keeps halos (the test area), it grows ten ticks a second,
// beside any other halos still growing; otherwise (a sample tile) it grows to the end at once.
function haloLift(st, r) {
  const R = st.R, s = st.surf;
  if (R.blend === "lighten" || R.blend === "invert") return null;   // veins only darken
  const h = new Halo(s, st, r);
  if (!h.ok) return null;
  for (const o of s.halos || []) o.feed(h);
  const host = st.host, live = typeof HOST !== "undefined" && host === HOST;
  if (live || (host && host.halos === true)) { (s.halos ??= []).push(h); if (live) haloTicker(s); return null; }
  return h.run();
}
function haloTicker(s) {
  if (s.nmTicking) return;
  s.nmTicking = true;
  const go = () => {
    if (TEST.surf !== s || (!(s.halos && s.halos.length) && !s.nmDirty)) { s.nmTicking = false; s.nmDirty = null; return; }
    let r = s.nmDirty || null;
    for (const h of s.halos || []) { const d = h.step(); if (d) r = r ? union(r, d) : d; }
    if (s.halos) s.halos = s.halos.filter(h => h.tick < h.N);
    if (r) { if (TEST.live) s.nmDirty = r; else { s.nmDirty = null; showPage(TEST.ctx, s, r); } }
    setTimeout(go, 1000 / TPS);
  };
  setTimeout(go, 1000 / TPS);
}

// ---------- rolls: rare, from the seed, after everything else ----------
// As Float's: each decided from the brush's seed (its own salt) rather than another draw from Random, so every other
// roll comes out exactly as before; a brush either keeps all its parts or changes one of them to the new choice.
const NM_ODDS = {
  stipple: { [WILDCARD]: 30, Fineliner: 40, Watercolor: 45 },
  hatch: { [WILDCARD]: 30, Fineliner: 35, "Dip pen": 30, Pencil: 35 },
  lighten: { [WILDCARD]: 40, Pastels: 30, Charcoal: 40, Gouache: 40 },
  behind: { Watercolor: 30, "Ink wash": 30, Gouache: 35 },
  invert: { [WILDCARD]: 80 },
  resist: { Watercolor: 60 },
  halo: { [WILDCARD]: 30, "Ink wash": 30, Fineliner: 50 }
};
const NM_SALT = { stipple: 0x5717e, hatch: 0x4a7c4, lighten: 0x119a7, behind: 0xbe41d, invert: 0x14e47, resist: 0x7e515, halo: 0x4a10, core: 0x3c0e, rake: 0x4a4e };
function rollNew(style, B, lk, old) {
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i);
  const hit = key => { const n = NM_ODDS[key][style]; return !!n && u(key, 0) < 1 / n; };
  const free = part => !(old && lk.parts.has(part));
  if (free("tip")) {
    if (hit("stipple")) {
      B.tip = switchChoice("tip", B.tip, "stipple"); const sponge = B.medium.k === "wash";
      Object.assign(B.tip.d, { dot: sponge ? lerp(.35, .55, u("stipple", 1)) : lerp(.35, .65, u("stipple", 1)), scatter: lerp(.2, .8, u("stipple", 2)), jitter: sponge ? lerp(.7, 1, u("stipple", 3)) : u("stipple", 3) });
    } else if (hit("hatch")) {
      B.tip = switchChoice("tip", B.tip, "hatch");
      Object.assign(B.tip.d, { pitch: lerp(.2, .6, u("hatch", 1)), lineW: lerp(.15, .45, u("hatch", 2)), hangle: lerp(30, 60, u("hatch", 3)), headTurn: u("hatch", 4) < .75 ? 1 : 0 });
    }
  }
  if (free("medium")) {
    if (hit("resist")) { B.medium = switchChoice("medium", B.medium, "resist"); B.medium.d.erase = u("resist", 1) < .7 ? 1 : 0; }
    else if (hit("lighten")) B.medium.d.blend = 1;
    else if (hit("behind")) B.medium.d.blend = 2;
    else if (hit("invert")) B.medium.d.blend = 3;
  }
  if (free("life") && B.life.k !== "float" && hit("halo")) {
    B.life = switchChoice("life", B.life, "halo"); Object.assign(B.life.d, { reach: lerp(.3, .8, u("halo", 1)), growth: lerp(.3, .8, u("halo", 2)) });
  }
  // The owner's "internal line, weighted less often" (ModularStyle.rollCore): a mark with a pen line through its heart keeps
  // it one roll in four (Ink wash two); without it a wash loses its line and wet ink goes .9–1 wet (up to its style's top).
  const md = B.medium.d;
  if (NM.coreRolls && free("medium") && !lk.dials.has("medium.line") && !(u("core", 0) < 1 / (style === "Ink wash" ? 2 : 4))) {
    if (B.medium.k === "wash" && md.line > 0) md.line = 0;
    else if (B.medium.k === "ink" && md.wet >= .35 && md.line > 0) {
      const st = MSTYLES[style] || MSTYLES[WILDCARD], spec = st.d && st.d["medium.wet"], hi = !spec ? 1 : Array.isArray(spec) ? spec[1] : spec.hi;
      md.line = 0; md.wet = Math.max(md.wet, lerp(Math.min(.9, hi), hi, u("core", 1)));
    }
  }
  // And multiline nibs (ModularStyle.rollRake): a rolled Rake stays one in three, else a Round tip.
  if (NM.coreRolls && free("tip") && B.tip.k === "multi" && !(u("rake", 0) < 1 / 3)) B.tip = switchChoice("tip", B.tip, "round");
}
const rollStyle0 = rollStyle;
rollStyle = function (style, old, locks) {
  const B = rollStyle0(style, old, locks);
  // The name said again with the new choices in: one that adds a Stipple or a Resist says so (parts.js describedName).
  if (NM.rolls) { rollNew(style, B, locks || { parts: new Set(), dials: new Set() }, old); normBrush(B); B.name = describedName(B, B.name); }
  return B;
};
// Variations never swap to a new choice (as they never pick Float's odds): the new choices are left out of the swap.
const makeVars0 = makeVars;
makeVars = function (B, locks) {
  const out = {}, cut = { tip: ["stipple", "hatch"], medium: ["resist"], life: ["halo"], carry: ["water"] };
  for (const [part, ks] of Object.entries(cut)) { const keep = CHOICES[part].slice(); out[part] = keep; CHOICES[part] = keep.filter(c => !ks.includes(c.k) || c.k === B[part].k); }
  try { return makeVars0(B, locks); } finally { for (const part of Object.keys(out)) CHOICES[part] = out[part]; }
};

// ---------- words (docs/TEXT-STYLE.md): only what is unusual ----------
const lc1 = t => t.charAt(0).toLowerCase() + t.slice(1);
const WHITE_WORDS = { ink: "White ink", wash: "White wash", cover: "White paint", charcoal: "Chalk", graphite: "White pencil" };
// Each medium's plain noun: what a stipple or hatching lays, and Behind's and Invert's card words, short enough for the
// app's module to keep one line ("Wash behind", "Invert ink").
const NM_BASE = { ink: "Ink", wash: "Wash", cover: "Paint", charcoal: "Charcoal", graphite: "Pencil" };
const choiceWords0 = choiceWords;
choiceWords = function (B, part) {
  if (part === "medium") {
    if (B.medium.k === "resist") return (B.medium.d.erase ?? 1) < .5 ? "Wax resist" : "Masking fluid";
    const bl = B.medium.d.blend | 0, w0 = choiceWords0(B, part);
    return bl === 1 ? WHITE_WORDS[B.medium.k] : bl === 2 ? NM_BASE[B.medium.k] + " behind" : bl === 3 ? "Invert " + NM_BASE[B.medium.k].toLowerCase() : w0;
  }
  if (part === "life" && B.life.k === "halo") return "Mold " + dryingSec(B) + " s";
  return choiceWords0(B, part);
};
// The brush as the old words know it: a round tip for a pattern, a quick line of ink for resist, Quick for Halo.
function nmPlain(B) {
  const A = cloneBrush(B);
  if (PATTERN_TIPS.has(B.tip.k)) A.tip = Object.assign(freshPart("tip", "round"), { d: Object.assign(freshPart("tip", "round").d, { weight: B.tip.d.weight ?? 1 }) });
  if (B.medium.k === "resist") { A.medium = freshPart("medium", "ink"); A.medium.d.erase = 0; }
  if (B.life.k === "halo") A.life = freshPart("life", "wet");
  A.medium.d.blend = 0;
  return normBrush(A);
}
const captionItems0 = captionItems;
captionItems = function (B) {
  if (!isNew(B)) return captionItems0(B);
  let items = captionItems0(nmPlain(B));
  // Resist is read as ink to word the rest: keep only the items an ink and a wash would both give (no medium tag).
  if (B.medium.k === "resist") { const A = nmPlain(B); A.medium = freshPart("medium", "wash"); const other = captionItems0(normBrush(A)); items = items.filter(t => other.includes(t)); }
  const bl = B.medium.k === "resist" ? 0 : B.medium.d.blend | 0, add = [];
  if (B.medium.k === "resist") add.push((B.medium.d.erase ?? 1) < .5 ? "wax" : "resist");
  if (bl) add.push(["", "white", "behind", "invert"][bl]);
  if (PATTERN_TIPS.has(B.tip.k)) add.push(B.tip.k);
  if (B.life.k === "halo") add.push("mold");
  items.splice(1, 0, ...add);
  return items;
};
const plainWords0 = plainWords;
plainWords = function (B) {
  if (!isNew(B)) return plainWords0(B);
  const A = nmPlain(B), mk = B.medium.k, tk = B.tip.k, bl = mk === "resist" ? 0 : B.medium.d.blend | 0;
  let text = plainWords0(A);
  const cut = text.indexOf(". "), first = cut < 0 ? text : text.slice(0, cut + 1), rest = cut < 0 ? [] : text.slice(cut + 2).split(/(?<=\.)\s+/).filter(Boolean);   // "Wet for 6.7 s." stays whole
  // The noun: what it lays, before " on …" or ", …".
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(first), noun0 = m ? m[1] : first.slice(0, -1), tail = m && m[2] ? m[2] : "";
  const base = NM_BASE[mk];
  let noun = noun0;
  if (mk === "resist") noun = (B.medium.d.erase ?? 1) < .5 ? "Wax resist" : "Masking fluid";
  else if (tk === "stipple") noun = mk === "wash" || mk === "cover" ? "Sponged " + lc1(base) : base + " stipple";
  else if (tk === "hatch") noun = base + " hatching";
  // A grade isn't a noun to whiten ("HB graphite"): a white pencil.
  if (bl === 1) noun = tk !== "stipple" && tk !== "hatch" && (mk === "charcoal" || mk === "graphite") ? (mk === "charcoal" ? "White chalk" : "White pencil") : "White " + lc1(noun);
  const out = [noun + tail + "."];
  if (tk === "stipple" && (B.tip.d.hold | 0) > 0) out.push("Builds up where it lingers.");
  if (mk === "resist") out.push((B.medium.d.erase ?? 1) < .5 ? "Nothing takes on it." : "Nothing takes on it until rubbed off.");
  if (bl === 2) out.push("Goes behind marks.");
  if (bl === 3) out.push("Inverts what's under it.");
  const hw = handWords(B, 2), control = hw !== "steady" ? cap1(hw) + "." : null;
  for (const t of rest) if (t !== control) out.push(t);
  if (B.life.k === "halo") out.push("Veins bridge nearby marks for " + dryingSec(B) + " s.");
  if (control) out.push(control);
  let t2 = out[0];
  for (let i = 1; i < out.length; i++) { if (i >= 2 && t2.length + 1 + out[i].length > 260) break; t2 += " " + out[i]; }
  return t2;
};

// ---------- presets to try them with, added once at the front of the lab's library ----------
function newModPresets() {
  const make = (name, style, seed, tip, td, medium, md, life, ld, hand, paper) => {
    const B = { name, seed, style, tip: freshPart("tip", tip), medium: switchChoice("medium", null, medium), carry: freshPart("carry", "off"),
      paper: freshPart("paper", paper || "plain"), life: freshPart("life", life), hand: freshPart("hand", hand || "steady") };
    Object.assign(B.tip.d, td); Object.assign(B.medium.d, md); Object.assign(B.life.d, ld);
    return normBrush(B);
  };
  return [
    make("White gel pen", "Fineliner", 0x9e1, "round", {}, "ink", { blend: 1 }, "wet", {}),
    make("Wash behind", "Watercolor", 0xbe41, "round", {}, "wash", { blend: 2 }, "wet", {}),
    make("Stipple sponge", "Watercolor", 0x5701, "stipple", { dot: .45, scatter: .4, jitter: .9 }, "wash", { wet: .35, pool: 0 }, "wet", {}),
    make("Stipple pen", "Fineliner", 0x5702, "stipple", { dot: .5, scatter: .35, jitter: .35 }, "ink", {}, "wet", {}),
    make("Hatch pen", "Fineliner", 0x4a7, "hatch", { pitch: .3, lineW: .22, hangle: 45, headTurn: 1 }, "ink", {}, "wet", {}),
    make("Halo ink", "Ink wash", 0x4a1, "round", { taperIn: .2, taperOut: .3 }, "ink", {}, "halo", { drying: .55, reach: .55, growth: .6 }, "darker"),
    make("Masking fluid", "Watercolor", 0x3a5, "round", { weight: 1 }, "resist", { erase: 1 }, "wet", {}),
    make("Wax resist", "Watercolor", 0x3a6, "round", { dry: .35 }, "resist", { erase: 0 }, "wet", {}, "steady", "cold press")
  ];
}
if (typeof addErasePresets === "function") {
  const addErase0 = addErasePresets;
  // Behind Erasable's three at the front.
  addErasePresets = function () { const add = newModPresets().filter(b => !S.library.some(x => x.name === b.name)); S.library.unshift(...add); addErase0(); };
}

// A white brush's samples (tiles, cards, the corner) draw on a dark ground: on paper they would show nothing. A resist's
// show by what refuses to take: no band under it, and a wash of the band's grey over the whole sample once it is laid,
// which takes only where it can (the app's WildSample.washedOver, on the wash layer under wax's sheen).
const RESIST_GROUND = 150;
function washOver(s, tone) {
  const rp = s.resist, k = 1 - tone / PAPER_TONE;
  for (let i = 0; i < s.page.length; i++) { s.page[i] *= 1 - k * (rp ? 1 - rp[i] : 1); s.base[i] = s.page[i]; }
}
if (typeof renderSample === "function" && typeof sampleSurf === "function") {
  const renderSample0 = renderSample, sampleSurf0 = sampleSurf, layStroke0 = layStroke; let darkGround = false, resistSample = false;
  renderSample = function (c, B, role) {
    darkGround = B.medium.k !== "resist" && (B.medium.d.blend | 0) === 1; resistSample = B.medium.k === "resist";
    try { return renderSample0(c, B, role); } finally { darkGround = false; resistSample = false; }
  };
  sampleSurf = function (w, h) { const s = sampleSurf0(w, h); if (darkGround) { s.page.fill(68); s.base.set(s.page); } return s; };
  layStroke = function (surf, B, pts, size, shade, host) {
    if (!resistSample || B.medium.k !== "resist") return layStroke0(surf, B, pts, size, shade, host);
    blankPage(surf);
    const out = layStroke0(surf, B, pts, size, shade, host);
    washOver(surf, RESIST_GROUND);
    return out;
  };
}

// Blend does nothing on water (every medium floats as a darkening film): greyed on a Float brush, as the lab greys any
// dial that does nothing.
if (typeof dialActive === "function") { const dialActive0 = dialActive; dialActive = (B, part, key) => !(part === "medium" && key === "blend" && B.life.k === "float") && dialActive0(B, part, key); }
// A choice dial whose options don't fit their row (four on the Nomad) becomes a menu button: nothing is clipped.
function fitSegs(root) {
  if (!root) return;
  for (const seg of root.querySelectorAll(".pseg")) {
    const bs = [...seg.querySelectorAll("button")];
    if (!bs.length || !bs.some(b => b.scrollWidth > b.clientWidth + 1)) continue;
    const { part, key } = bs[0].dataset, sel = document.createElement("select");
    sel.className = "psel"; sel.disabled = bs[0].disabled; sel.setAttribute("aria-label", dialLabel(part, cur()[part].k, key));
    bs.forEach((b, i) => { const o = document.createElement("option"); o.value = String(i); o.textContent = b.textContent; o.selected = b.getAttribute("aria-pressed") === "true"; sel.appendChild(o); });
    sel.addEventListener("change", () => ACT.dialchoice({ dataset: { part, key, i: sel.value } }));
    seg.replaceChildren(sel);
  }
}
if (typeof renderCol === "function" && typeof document !== "undefined") {
  const renderCol0 = renderCol;
  renderCol = function () { renderCol0(); fitSegs(document.getElementById("col")); };
  document.head.insertAdjacentHTML("beforeend", "<style>.psel { width: 100%; height: 24px; border: 2px solid var(--ink); border-radius: 0; background: var(--screen); color: var(--ink); font: inherit; font-size: 11px; font-weight: 700; text-transform: uppercase; padding: 0 4px; }</style>");
}

// ---------- checks (in RUN CHECKS and Node) ----------
function newModChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 360, H = 240, q = quantTone, P = PAPER_TONE;
  const mk = (tip, medium, md, life, td, hand) => {
    const B = { name: "n", seed: 17, style: "Fineliner", tip: freshPart("tip", tip || "round"), medium: switchChoice("medium", null, medium || "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", life || "wet"), hand: freshPart("hand", hand || "steady") };
    Object.assign(B.medium.d, md || {}); Object.assign(B.tip.d, td || {});
    return normBrush(B);
  };
  const line = (x0, y0, x1, y1, p = .8, n = 90) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p: typeof p === "function" ? p(k / n) : p, t: k * 6, tx: NaN, ty: NaN }));
  const band = (s, tone) => { for (let y = 80; y < 160; y++) for (let x = 0; x < W; x++) s.page[y * W + x] = tone; s.base.set(s.page); };
  const hashPage = s => { let h = 0x811c9dc5; for (let i = 0; i < s.page.length; i++) h = Math.imul(h ^ q(s.page[i]), 16777619); return h >>> 0; };
  const count = (s, f, x0 = 0, y0 = 0, x1 = W, y1 = H) => { let n = 0; for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) if (f(q(s.page[y * W + x]), y * W + x)) n++; return n; };
  // 1. Codes: every old one byte for byte; Blend written only when not the medium's own; the new choices round-trip.
  { const olds = [...LEGACY_CODES.map(c => encodeM(readAnyCode(c).b)), "steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud",
      "messy-stick.M51eueei000005bd4at_3nbcw00we_0_2tz_1jz_8syjs00y8j900000000nz0.t4mf73", "f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_0000000000000000000nz0.7"];
    t("old codes re-encode byte for byte", olds.every(c => encodeM(readAnyCode(c).b) === c));
    const ink = mk(), white = mk("round", "ink", { blend: 1 }), seg = c => c.split("_")[1];
    t("Blend is written only when not the medium's own (" + seg(encodeM(ink)) + " → " + seg(encodeM(white)) + ")", encodeM(ink) === encodeM(readAnyCode(encodeM(ink)).b) && seg(encodeM(white)).endsWith("01") && seg(encodeM(white)).length === seg(encodeM(ink)).length + 1);
    const news = [mk("stipple"), mk("hatch"), mk("round", "resist", { erase: 0 }), mk("round", "ink", {}, "halo"), mk("round", "cover", { blend: 2 }), white];
    t("the new choices round-trip (" + news.map((b, i) => encodeM(b).split("_")[[0, 0, 1, 4, 1, 1][i]].replace(/^.*\.M./, "")).join(" ") + ")", news.every(b => { const c = encodeM(b), r = readAnyCode(c); return r && encodeM(r.b) === c; })); }
  // 2. Rolls: with the new rolls off, every roll is as before; on, a brush either is the same or changes one part to a new choice.
  // The core line and the rake (not a new choice, but rolled here too) are off for both, and Flick and Fling (dials, below).
  { const roll = on => { const keep = Math.random, k0 = NM.rolls, k1 = NM.coreRolls, k2 = NM.flickRolls, k3 = NM.flingRolls, k4 = NM.oilRolls, k5 = NM.fadeRolls, k6 = NM.lemonRolls; Math.random = rng(9191); NM.rolls = on; NM.coreRolls = NM.flickRolls = NM.flingRolls = NM.oilRolls = NM.fadeRolls = NM.lemonRolls = false;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.rolls = k0; NM.coreRolls = k1; NM.flickRolls = k2; NM.flingRolls = k3; NM.oilRolls = k4; NM.fadeRolls = k5; NM.lemonRolls = k6; } };
    const a = roll(false), b = roll(true); let same = 0, hits = 0, odd = 0;
    for (let i = 0; i < a.length; i++) { if (encodeM(a[i]) === encodeM(b[i])) { same++; continue; } hits++; if (!isNew(b[i])) odd++; }
    t("rolls: " + same + " of " + a.length + " as before, " + hits + " with one new choice, none else changed", odd === 0 && hits > 10 && hits < a.length * .15); }
  // 3. Lighten: white over a dark band, nothing on paper; Darken (the default) is today's compose.
  { const s = modSurface(W, H, 7); band(s, 51); layStroke(s, mk("round", "ink", { blend: 1 }), line(20, 60, 340, 180), 8, 1, null);
    const lit = count(s, (v, i) => v >= 204 && Math.floor(i / W) >= 80 && Math.floor(i / W) < 160), onPaper = count(s, (v, i) => v < 238 && (Math.floor(i / W) < 76 || Math.floor(i / W) > 163));
    t("a white gel line over a dark wash (" + lit + " px near white), nothing on blank paper (" + onPaper + " px)", lit > 300 && onPaper === 0); }
  // 4. Behind: a wash behind a light ink line leaves the line's grey; the same wash glazed over it darkens it.
  { const draw = bl => { const s = modSurface(W, H, 7); layStroke(s, mk(), line(30, 120, 330, 120), 6, .53, null); layStroke(s, mk("round", "wash", { blend: bl }), line(20, 120, 340, 120), 90, 1, null); return s; };
    const own = draw(0), behind = draw(2), lineTone = s => q(s.page[120 * W + 180]), paper = s => q(s.page[95 * W + 60]);
    t("a wash behind keeps a light line's grey (" + lineTone(behind) + " vs " + lineTone(own) + " glazed), the wash as dark on paper (" + paper(behind) + " = " + paper(own) + ")", lineTone(behind) > lineTone(own) + 20 && paper(behind) === paper(own)); }
  // 5. Invert: paper turns dark, a dark band light.
  { const s = modSurface(W, H, 7); band(s, 34); layStroke(s, mk("round", "cover", { blend: 3 }), line(20, 40, 340, 200), 30, 1, null);
    t("invert: paper turns black (" + q(s.page[50 * W + 40]) + "), black turns white (" + q(s.page[120 * W + 180]) + ")", q(s.page[50 * W + 40]) <= 34 && q(s.page[120 * W + 180]) >= 204); }
  // 6. Resist: a wash leaves masking fluid's pixels as they were; one eraser pass lifts the fluid and leaves the paint; a
  //    second wash then takes there. Wax (Erasable off) stays, and shields what's under it from the eraser.
  { const s = modSurface(W, H, 7), fluid = mk("round", "resist", { erase: 1 }), wax = mk("round", "resist", { erase: 0 }), wash = mk("round", "wash", { wet: .3 });
    layStroke(s, fluid, line(40, 70, 320, 70), 24, 1, null); layStroke(s, wax, line(40, 170, 320, 170), 24, 1, null);
    const before = Float32Array.from(s.page); layStroke(s, wash, line(10, 120, 350, 120), 300, 1, null);
    const under = i => Math.abs(s.page[i] - before[i]) < .5, fl = 70 * W + 180, wx = 170 * W + 180, open = 120 * W + 180;
    t("a wash doesn't take on masking fluid or wax (" + (under(fl) && under(wx)) + "), it does beside them (" + q(s.page[open]) + ")", under(fl) && under(wx) && q(s.page[open]) < 230);
    const er = new Eraser(s, 60, false); for (let k = 0; k <= 60; k++) er.add([[20 + 5 * k, 70, 1, k * 6, NaN, NaN]]); for (let k = 0; k <= 60; k++) er.add([[20 + 5 * k, 170, 1, 400 + k * 6, NaN, NaN]]);
    t("one eraser pass lifts the fluid (" + s.resist[fl].toFixed(2) + ") and keeps the wax (" + s.resist[wx].toFixed(2) + ")", s.resist[fl] < .01 && s.resist[wx] > .99);
    layStroke(s, wash, line(10, 70, 350, 70), 80, 1, null);
    t("a wash after the lift takes where the fluid was (" + q(s.page[fl]) + ")", q(s.page[fl]) < 230); }
  // 6b. A smudge and a stump (Smear's Soften: Blend stump, Wash comb) over masking fluid move none of what it shields and
  //     draw nothing out from under it: a dark band under a strip of fluid stays, and the paper past the strip stays paper.
  { const codes = ["blend-stump.M50i00000000yat_3eqlj00we_3ssll00zz_1nl_1jz_8pthh00x9is00000000nz0.51j8ud", "wash-comb.M65zzlli000000000_1ulat00770000ee_3zzll00zz_000_1jz_0000000000000000000nz0.7"];
    const fluid = mk("round", "resist", { erase: 1 }), res = [];
    for (const c of codes) for (const masked of [false, true]) {
      const s = modSurface(W, H, 7), B = readAnyCode(c).b;
      for (let y = 0; y < H; y++) for (let x = 150; x < 190; x++) s.page[y * W + x] = s.base[y * W + x] = 40;
      if (masked) layStroke(s, fluid, line(170, 10, 170, 230), 70, 1, null);
      const before = Float32Array.from(s.page);
      layStroke(s, B, line(60, 120, 330, 120), 40, 1, null);
      let moved = 0, past = 0;
      for (let y = 105; y < 136; y++) for (let x = 0; x < W; x++) {
        const i = y * W + x;
        if (s.resist && s.resist[i] > .999 && Math.abs(s.page[i] - before[i]) > .01) moved++;
        if (x >= 215 && x < 255) past += before[i] - s.page[i];
      }
      res.push({ masked, moved, past });
    }
    const ok = res.every(r => r.masked ? r.moved === 0 && r.past < 1 : r.past > 100);
    t("a stump and a comb over masking fluid leave what it shields and draw nothing out from under it (" + res.map(r => (r.masked ? "fluid " : "bare ") + Math.round(r.past)).join(", ") + ")", ok); }
  // 7. Stipple: dots, more with pressure; the same samples batched differently lay the same dots.
  { const st = mk("stipple", "ink", {}, "wet", { dot: .3, scatter: .4, jitter: .5 }), dark = p => { const s = modSurface(W, H, 7); layStroke(s, st, line(20, 120, 340, 120, p), 16, 1, null); return count(s, v => v < 128); };
    const lo = dark(.2), hi = dark(.95);
    const once = (n) => { const s = modSurface(W, H, 7), S = new Stroke(s, st, 16, 1, null), pts = line(20, 120, 340, 120, u => .3 + .6 * u); for (let i = 0; i < pts.length; i += n) S.add(pts.slice(i, i + n).map(p => [p.x, p.y, p.p, p.t, p.tx, p.ty])); S.end(); return hashPage(s); };
    t("stipple: " + lo + " dark px at light pressure, " + hi + " pressed; batched 1 or 7 a time, the same dots", hi > lo * 2.5 && lo > 40 && once(1) === once(7)); }
  // 7b. Build-up: a pen resting a second lays more dots the faster it builds, none when off; the test area's tick (the
  //     last sample again) lays what the samples would.
  { const held = (hold, tick) => { const s = modSurface(W, H, 7), S = new Stroke(s, mk("stipple", "ink", {}, "wet", { dot: .5, scatter: .35, jitter: .35, hold }), 24, 1, null);
      S.add([[180, 120, .6, 1000, NaN, NaN]]); for (let t = 10; t <= 1000; t += 10) { if (tick) S.holdTick(1000 + t); else S.add([[180, 120, .6, 1000 + t, NaN, NaN]]); } S.end(); return count(s, v => v < 128); };
    const off = held(0, false), slow = held(1, false), fast = held(2, false);
    t("build-up: resting a second, " + off + " dark px off, " + slow + " slow, " + fast + " fast; ticked, the same", off > 0 && slow > 1.5 * off && fast > 1.5 * slow && held(2, true) === fast && held(0, true) === off); }
  // 8. Hatch: crisp lines (few in-between greys), pressure crosses them, a fixed angle lines strokes up.
  { const ht = mk("hatch", "ink", {}, "wet", { pitch: .35, lineW: .25, hangle: 45, headTurn: 1 });
    const lay1 = (p, ys) => { const s = modSurface(W, H, 7); for (const y of ys) layStroke(s, ht, line(20, y, 340, y, p), 60, 1, null); return s; };
    const a = lay1(.3, [120]), b = lay1(.95, [120]), da = count(a, v => v < 128), db = count(b, v => v < 128), grey = count(b, v => v > 34 && v < 221);
    const rows = (v, i) => v < 128 && Math.floor(i / W) > 100 && Math.floor(i / W) < 135, one = count(lay1(.3, [110]), rows), two = count(lay1(.3, [110, 125]), rows);
    const batched = n => { const s = modSurface(W, H, 7), S = new Stroke(s, mk("hatch", "ink", {}, "wet", { headTurn: 0 }), 40, 1, null), pts = line(20, 100, 340, 160, u => .2 + .75 * u);
      for (let i = 0; i < pts.length; i += n) S.add(pts.slice(i, i + n).map(p => [p.x, p.y, p.p, p.t, p.tx, p.ty])); S.end(); return hashPage(s); };
    t("hatch: " + da + " dark px light, " + db + " pressed (crossed), " + Math.round(grey / Math.max(1, db) * 100) + "% in-between greys; overlapping strokes line up (" + one + " → " + two + " dark px); batched 1 or 7 a time, the same lines",
      db > da * 1.6 && grey < db * .9 && two < one * 1.35 && batched(1) === batched(7)); }
  // 9. Halo: veins reach a mark within Reach and bridge to it; nothing past Reach; a stroke that isn't Halo sets it.
  { const halo = mk("round", "ink", {}, "halo", {}, "steady"); halo.life.d.reach = .6; halo.life.d.growth = .7;
    const make = gap => { const s = modSurface(W, H, 7); layStroke(s, mk(), line(20, 120 + gap, 340, 120 + gap), 6, 1, null); const before = Float32Array.from(s.page);
      layStroke(s, halo, line(20, 120, 340, 120), 6, 1, null); let between = 0, far = 0;
      for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = y * W + x; if (q(s.page[i]) < q(before[i]) - 16) { if (y > 124 && y < 120 + gap - 4) between++; if (y < 40) far++; } } return { between, far }; };
    const near = make(28);
    t("halo: veins bridge a line 28 px off (" + near.between + " px between) and stay within reach (" + near.far + " px far off)", near.between > 60 && near.far === 0);
    const s = modSurface(W, H, 7), host = { halos: true }; layStroke(s, halo, line(20, 120, 340, 120), 6, 1, host); const h = s.halos[0];
    for (let k = 0; k < 5; k++) h.step(); layStroke(s, mk(), line(20, 200, 340, 200), 6, 1, host);
    t("a stroke that isn't Halo sets the halo where it is (" + h.tick + " of " + h.N + " ticks)", !s.halos.length && h.tick === h.N); }
  return out;
}
const selfTest0 = selfTest;
selfTest = function () { const r = selfTest0(); r.newmods = newModChecks(); r.ok = r.ok && r.newmods.every(c => c.ok); NM.last = r.newmods; return r; };
// Erasable's check hashes rolls from before the new choices: it runs with their rolls off.
const eraseChecks0 = eraseChecks;
eraseChecks = function () { const k = NM.rolls; NM.rolls = false; try { return eraseChecks0(); } finally { NM.rolls = k; } };
if (typeof runChecks === "function") {
  const runChecks0 = runChecks;
  runChecks = function () {
    runChecks0();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.last) return;
      out.textContent += "\n\nNew modules (Blend, Stipple, Hatch, Resist, Mold):\n" + NM.last.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 30);
  };
}

// ===================================================================================================================
// Screen and Tone (the mischief modules spec, Stage 1; mischief-lab.html's buildScreen and bit1; the app's PrintScreen):
//   Screen  a Medium dial on every medium with Blend: none, halftone, line screen, dither or grain, with Cell, Angle and
//           the newsprint dials Misregister, Ink gain and Uneven. The mark's own darkness passes a threshold fixed to the
//           page: pure black (White's white, Invert's opposite) or the page as found (opaque paint lays white there).
//   Tone    a Medium choice, a screentone: the brush lays a fill, and what shows is dots, lines, sand or a gradient at
//           its Tone over it, in black; the eraser scrapes it in streaks. Laid as a crisp ink line, always erasable.
// Appended (dials after Blend, Tone the medium's seventh choice), written only when not default: old codes as before.
// ===================================================================================================================
// The Screen dial's name wherever the lab shows it (the app's PrintScreen.LABEL; its key stays "screen").
const SCREEN_LABEL = "Print Shading";
Object.assign(DIALS.medium, {
  screen: { label: SCREEN_LABEL, choices: ["None", "Halftone", "Engraving", "Dither", "Grain", "Rosette", "Ben-Day", "Crosshatch", "Wavy Lines", "Mezzotint", "Diamond Dots", "Stipple", "Woodcut", "Scribble", "Hatching"], def: 0 },
  cell: { label: "Pattern Size", min: 3, max: 16, def: 6, int: true, fmt: v => Math.round(v) + " px" },
  sangle: { label: "Angle", min: 0, max: 90, def: 45, fmt: v => Math.round(v) + "°" },
  mis: { label: "Double Print", min: 0, max: 8, def: 0, int: true, fmt: v => v < .5 ? "off" : Math.round(v) + " px" },
  gain: { label: "Dot Swell", min: 0, max: 1, def: 0 },
  uneven: { label: "Patchy Ink", min: 0, max: 1, def: 0 },
  pattern: { label: "Pattern", choices: ["Dots", "Lines", "Sand", "Gradient", "Crosshatch", "Wavy Lines", "Mezzotint", "Stipple", "Woodcut", "Scribble", "Hatching"], def: 0 },
  ttone: { label: "Darkness", min: .05, max: 1, def: .45 }
});
const SCREEN_DIALS = ["screen", "cell", "sangle", "mis", "gain", "uneven"], UNDER_SCREEN = new Set(SCREEN_DIALS.slice(1));
for (const c of CHOICES.medium) if (c.dials.includes("blend")) c.dials.push(...SCREEN_DIALS);
CHOICES.medium.push({ k: "tone", label: "Screentone", dials: ["pattern", "ttone", "cell", "sangle"], macro: "ttone", words: ["light", "dark"],
  blurb: "A screentone: dots, lines, sand or a gradient laid over the fill. Scrapes off in streaks." });
for (const k of [...SCREEN_DIALS, "pattern", "ttone"]) ADDED.add("medium." + k);
REAL_ERASE.tone = 1;
// Appended after the first four (the app's PrintScreen.Kind): rosette, Ben-Day, crosshatch, wavy lines, mezzotint, diamond
// dots, then stipple, woodcut, scribble and hatching; Screentone's last seven are the twins of crosshatch to hatching.
const SCREEN_KINDS = ["halftone", "lines", "dither", "grain", "rosette", "benday", "crosshatch", "wavy", "mezzotint", "diamond", "stipple", "woodcut", "scribble", "hatching"],
  TONE_KINDS = ["dots", "tlines", "sand", "gradient", "crosshatch", "wavy", "mezzotint", "stipple", "woodcut", "scribble", "hatching"], SCREEN_GHOST = .45;
const ROSETTE_OFF = [-30, 30, -45, 0];
// The screen a medium lays through, or null (the app's PrintScreen.of).
function screenOf(M) {
  const d = M.d, cell = d.cell ?? 6, angle = d.sangle ?? 45, tone = M.k === "tone", sc = tone ? 0 : d.screen | 0;
  if (!tone && !sc) return null;
  const a = angle * DEG, mis = tone ? 0 : d.mis || 0;
  return { kind: tone ? TONE_KINDS[d.pattern | 0] : SCREEN_KINDS[sc - 1], tone, cell, ca: Math.cos(a), sa: Math.sin(a), inv: 1 / Math.max(1, cell),
    misX: Math.round(mis * .8), misY: Math.round(mis * .6), gain: tone ? 0 : d.gain || 0, uneven: tone ? 0 : d.uneven || 0, level: tone ? d.ttone ?? .45 : 1,
    grain: Math.max(1, Math.round(cell / 6)),
    rot: ROSETTE_OFF.map(o => [Math.cos((angle + o) * DEG), Math.sin((angle + o) * DEG)]) };
}
// mischief-lab.html's hash: page pixel x, y and salt s, 0 to 1.
function screenHash(x, y, s) {
  let h = Math.imul(x | 0, 0x27d4eb2d) ^ Math.imul(y | 0, 0x165667b1) ^ Math.imul(s | 0, 0x9e3779b1);
  h = Math.imul(h ^ (h >>> 15), 0x85ebca6b); h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}
const BAYER8 = (() => { let m = [[0]]; while (m.length < 8) { const k = m.length, q = [[0, 2], [3, 1]], o = []; for (let y = 0; y < 2 * k; y++) { o.push([]); for (let x = 0; x < 2 * k; x++) o[y].push(4 * m[y % k][x % k] + q[(y / k) | 0][(x / k) | 0]); } m = o; } return m.flat(); })();
// Ben-Day (the app's PrintScreen): a round dot at every site of an offset (hexagonal) lattice BENDAY_PITCH cells apart, all
// one size, growing smoothly with the tone: a pixel inks once the tone passes the share of the page its site's dot covers
// when it reaches it (BENDAY_SHARE, made on first use), solid just short of full darkness.
// Java's Math.rint: halves to the even neighbour.
const rint = x => { const f = Math.floor(x), d = x - f; return d < .5 ? f : d > .5 ? f + 1 : f % 2 === 0 ? f : f + 1; };
const BENDAY_PITCH = 1.5, BENDAY_SOLID = .997, BENDAY_REACH = 1 / Math.sqrt(3) + 1e-9;
function benDayDistance(x, y) {
  const h = Math.sqrt(3) / 2, j0 = Math.floor(y / h); let best = 9;
  for (let j = j0; j <= j0 + 1; j++) { const shift = (j & 1) * .5, sx = rint(x - shift) + shift, dx = x - sx, dy = y - j * h; best = Math.min(best, dx * dx + dy * dy); }
  return Math.sqrt(best);
}
let BENDAY_SHARE = null;
function benDayShare() {
  if (BENDAY_SHARE) return BENDAY_SHARE;
  const S = new Float64Array(1024), within = new Int32Array(S.length), n = 256, hh = Math.sqrt(3) / 2;
  for (let a = 0; a < n; a++) for (let b = 0; b < n; b++) { const d = benDayDistance((a + .5) / n, (b + .5) / n * 2 * hh); within[Math.min(S.length - 1, (d * (S.length - 1) / BENDAY_REACH) | 0)]++; }
  let sum = 0; for (let i = 0; i < S.length; i++) { sum += within[i]; S[i] = BENDAY_SOLID * sum / (n * n); }
  return BENDAY_SHARE = S;
}
const screenTri = t => Math.abs(t - Math.floor(t) - .5) * 2;
// Mezzotint: dashes lying along the angle in rows a third of a cell wide, each row its own dash length and shift, each dash its own rank.
function screenMezzo(S, x, y) {
  const w = Math.max(1, S.cell / 3), px = x * S.ca + y * S.sa, py = -x * S.sa + y * S.ca, j = Math.floor(py / w), len = S.cell * (.6 + 1.2 * screenHash(j, 0, 51));
  return screenHash(Math.floor((px + screenHash(j, 0, 52) * len) / len), j, 53);
}
// Stipple, woodcut, scribble and hatching (the app's PrintScreen.raw and even), whose look changes as they darken: a raw
// field (lower inks first) at u, v cells in the angle's frame, evened by its own table so the share inked is the darkness.
const EVEN_FAMILY = { stipple: 0, woodcut: 1, scribble: 2, hatching: 3 }, EVEN_RAW_MOST = 2.5, EVEN_BINS = 1024, EVEN_TABLES = [];
// cos(2πt) from a 4096-step table, as the app's cos1, so every screen lands on the app's pixels.
const COS1 = Float64Array.from({ length: 4096 }, (_, i) => Math.cos(2 * Math.PI * i / 4096));
const cos1 = t => COS1[((t - Math.floor(t)) * 4096 | 0) & 4095], sin1 = t => cos1(t - .25);
const evMix = (a, b, c) => { let h = Math.imul(a, 0x27d4eb2d) ^ Math.imul(b, 0x165667b1) ^ Math.imul(c, 0x9e3779b1 | 0); h = Math.imul(h ^ (h >>> 15), 0x85ebca6b | 0); h = Math.imul(h ^ (h >>> 13), 0xc2b2ae35 | 0); return h ^ (h >>> 16); };
const evUnit = h => { let g = Math.imul(h ^ (h >>> 16), 0x45d9f3b); g ^= g >>> 16; return (g >>> 0) / 4294967296; };
// Hatching's three directions off the angle (the app's HatchStamper.TURN); where each scribble and hatching set starts.
const EV_TURN = HATCH_TURN.map(t => [Math.cos(t * DEG), Math.sin(t * DEG)]), SHADE_SCRIBBLE_AT = [0, .45, .8], SHADE_HATCH_AT = [0, .3, .55, .8, .95, 1.1], SHADE_SCRIBBLE_K = .55;
function evenRaw(k, u, v, cell) {
  const TAU = 2 * Math.PI;
  if (k === 0) {
    // Stipple: dots on a jittered lattice appear one by one, small, then grow and merge.
    const r0 = Math.max(.75, cell * .2) / cell, i0 = Math.floor(u - .5), j0 = Math.floor(v - .5); let best = 9;
    for (let i = i0; i <= i0 + 1; i++) for (let j = j0; j <= j0 + 1; j++) {
      const h = evMix(i, j, 61), sx = i + .5 + (evUnit(h) - .5) * .6, sy = j + .5 + (evUnit(Math.imul(h, 0x9e3779b1 | 0)) - .5) * .6;
      best = Math.min(best, .9 * evUnit(Math.imul(h, 0x85ebca6b | 0)) + 1.6 * Math.max(0, Math.sqrt((u - sx) * (u - sx) + (v - sy) * (v - sy)) - r0));
    }
    return best;
  }
  if (k === 1) {
    // Woodcut: a line a cell, wavering, broken at first, thickening until only broken white lines are left.
    let j = Math.floor(v), wav = 0;
    for (let pass = 0; pass < 2; pass++) {
      const h = evMix(j, 0, 71);
      wav = .2 * sin1(u * .05 + evUnit(h)) + .07 * sin1(u * .17 + evUnit(Math.imul(h, 0x85ebca6b | 0)));
      const jj = Math.floor(v + wav); if (jj === j) break; j = jj;
    }
    const h = evMix(j, 0, 73), b = .5 + .3 * sin1(u * .06 + evUnit(h)) + .2 * sin1(u * .19 + evUnit(Math.imul(h, 0x9e3779b1 | 0)));
    return screenTri(v + wav) + .55 * (b - .5) + .3;
  }
  if (k === 2) {
    // Scribble: three looping scribbles, each laid over the last, as far off a smooth field's levels as its slope says.
    let best = 9;
    for (let l = 0; l < 3; l++) {
      const uu = l === 1 ? v : u, vv = l === 1 ? -u : v, ph = l * .37, a = .07 + .015 * l;
      const p1 = a * 1.3 * vv + ph, A = a * uu + .45 * sin1(p1), p2 = a * .9 * uu + 2 * ph, B = a * 1.1 * vv + .45 * sin1(p2), C = a * .7 * (uu + vv) + ph * 3;
      const cA = TAU * cos1(A), cB = TAU * cos1(B), cC = .6 * TAU * cos1(C) * a * .7, F = sin1(A) + sin1(B) + .6 * sin1(C);
      const fu = cA * a + cB * .45 * TAU * cos1(p2) * a * .9 + cC, fv = cA * .45 * TAU * cos1(p1) * a * 1.3 + cB * a * 1.1 + cC;
      const kk = F * SHADE_SCRIBBLE_K, off = Math.abs(kk - rint(kk)) / Math.max(.12, SHADE_SCRIBBLE_K * Math.sqrt(fu * fu + fv * fv));
      best = Math.min(best, SHADE_SCRIBBLE_AT[l] + 1.8 * off);
    }
    return best;
  }
  // Hatching: short strokes, a little askew, one direction, then a second and a third, then each again between its rows.
  let best = 9;
  for (let s = 0; s < 6 && SHADE_HATCH_AT[s] < best; s++) {
    const [c, sn] = EV_TURN[s % 3], odd = (s / 3) | 0, hu = u * c + v * sn, hv = -u * sn + v * c, k2 = Math.floor((hv - odd) / 2 + .5);
    const row = evMix(k2, s, 83), seg = 6, shift = evUnit(row) * seg, m = Math.floor((hu + shift) / seg), h = evMix(row, m, 89);
    const mid = (m + .5) * seg - shift + (evUnit(h) - .5) * 1.2, x = hu - mid, half = seg * (.36 + .1 * evUnit(Math.imul(h, 0x27d4eb2d)));
    const centre = 2 * k2 + odd + (evUnit(Math.imul(h, 0x9e3779b1 | 0)) - .5) * .5 + (evUnit(Math.imul(h, 0x85ebca6b | 0)) - .5) * .16 * x;
    const e = Math.abs(x) / half, d = Math.abs(hv - centre) * (1 + 2 * Math.max(0, e - .55)) + 2 * Math.max(0, Math.abs(x) - half);
    best = Math.min(best, SHADE_HATCH_AT[s] + 1.4 * d);
  }
  return best;
}
// Field k's even table, from a page of the default Pattern Size at 45 degrees, made on first use.
function evenTable(k) {
  if (EVEN_TABLES[k]) return EVEN_TABLES[k];
  const n = 384, cell = DIALS.medium.cell.def, inv = 1 / cell, ca = Math.cos(Math.PI / 4), sa = ca, count = new Int32Array(EVEN_BINS), e = new Float64Array(EVEN_BINS);
  for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) count[clamp(Math.round(evenRaw(k, (x * ca + y * sa) * inv, (-x * sa + y * ca) * inv, cell) * (EVEN_BINS - 1) / EVEN_RAW_MOST), 0, EVEN_BINS - 1)]++;
  let sum = 0; for (let i = 0; i < EVEN_BINS; i++) { sum += count[i]; e[i] = .998 * (sum - count[i] / 2) / (n * n) + 1e-6 * i; }
  return EVEN_TABLES[k] = e;
}
function screenEvened(k, u, v, cell) {
  const e = evenTable(k), f = evenRaw(k, u, v, cell) * (EVEN_BINS - 1) / EVEN_RAW_MOST, i = clamp(Math.floor(f), 0, EVEN_BINS - 2);
  return Math.fround(e[i] + (e[i + 1] - e[i]) * clamp(f - i));
}
// The threshold at page pixel x, y (buildScreen): ink where the darkness is above it.
function screenT(S, x, y) {
  if (S.kind === "mezzotint") return screenMezzo(S, x, y);
  if (EVEN_FAMILY[S.kind] !== undefined) return screenEvened(EVEN_FAMILY[S.kind], (x * S.ca + y * S.sa) * S.inv, (-x * S.sa + y * S.ca) * S.inv, S.cell);
  if (S.kind === "dither") return (BAYER8[(y & 7) * 8 + (x & 7)] + .5) / 64;
  if (S.kind === "grain") return screenHash(Math.floor(x / S.grain), Math.floor(y / S.grain), 3);
  if (S.kind === "sand") return screenHash(x, y, 5);
  const u = (x * S.ca + y * S.sa) * S.inv, v = (-x * S.sa + y * S.ca) * S.inv;
  if (S.kind === "lines") { const vv = v + .22 * sin1(u / 9); return Math.abs(vv - Math.floor(vv) - .5) * 2; }
  if (S.kind === "tlines") return Math.abs(v - Math.floor(v) - .5) * 2;
  // Banknote waves; etching (one set to half grey, the crossing set from there to black); square dots in the screen's frame.
  if (S.kind === "wavy") return screenTri(v + .7 * sin1(u / 8));
  if (S.kind === "crosshatch") { const t1 = screenTri(v + .22 * sin1(u / 9)), t2 = screenTri(u + .22 * sin1(v / 9)); return Math.min(t1 < .5 ? t1 : 2, (1 + t2) / 2); }
  if (S.kind === "diamond") { const d = 2 * Math.max(Math.abs(u - Math.floor(u) - .5), Math.abs(v - Math.floor(v) - .5)); return d * d; }
  // Four halftones at the process-colour angles, all black: a pixel inks where any of them would.
  if (S.kind === "rosette") {
    let m = 1;
    for (const [c, s] of S.rot) m = Math.min(m, .5 - (cos1((x * c + y * s) * S.inv) + cos1((-x * s + y * c) * S.inv)) / 4);
    const q = (1 - m) * (1 - m); return 1 - q * q;
  }
  if (S.kind === "benday") { const sh = benDayShare(), d = benDayDistance(u / BENDAY_PITCH, v / BENDAY_PITCH); return sh[Math.min(sh.length - 1, Math.max(0, (d * (sh.length - 1) / BENDAY_REACH) | 0))]; }
  return .5 - (cos1(u) + cos1(v)) / 4;
}
// Whether darkness a (0 to 1) inks page pixel x, y of a page h tall, blot its soft noise (bit1 and the render's second pass).
function screenInk(S, a, x, y, h, blot) {
  if (!(a > 0)) return false;
  if (S.tone) return a * (S.kind === "gradient" ? clamp(S.level * 2 * (y + .5) / h) : S.level) > screenT(S, x, y);
  const b0 = S.uneven > 0 ? blot : 0, t = a * (1 - S.uneven * .55 * b0);
  let b = t > screenT(S, x, y);
  if (!b && S.gain > 0) {
    const n = (t > screenT(S, x - 1, y)) + (t > screenT(S, x + 1, y)) + (t > screenT(S, x, y - 1)) + (t > screenT(S, x, y + 1));
    if (n && screenHash(x, y, 17) < S.gain * (.22 + .18 * n)) b = true;
  }
  if (b && S.uneven > 0 && screenHash(x, y, 23) < S.uneven * (.04 + .14 * b0)) b = false;
  return b;
}
// Resolve: Tone as a crisp ink line (never on water, smearing nothing); a screen lays no grey after it.
const resolveNM = resolve;
resolve = function (B, size, shade) {
  const tone = B.medium.k === "tone";
  if (!tone && !(B.medium.d.screen | 0)) return resolveNM(B, size, shade);
  let A = B;
  if (tone) {
    A = cloneBrush(B);
    A.medium = { k: "ink", d: { wet: 0, line: 1, pool: 0, splatter: 0, bleed: 0, bloom: 0, lines: 0, offset: 0, chaos: 0, erase: 1, blend: 0 } };
    if (A.life.k === "float") A.life = freshPart("life", "wet");
    A.carry = freshPart("carry", "off");
  }
  const R = resolveNM(A, size, shade);
  if (!tone && B.life.k === "float") return R;
  R.screen = screenOf(B.medium); R.settle = 0; R.halo = null;
  return R;
};
// The compose: the mark's own darkness over blank paper (and its misregistered pass), through the screen.
const composeNoScreen = composeStroke;
composeStroke = function (st, rect, phase) {
  const R = st.R, S = R.screen, s = st.surf;
  if (!S) {
    composeNoScreen(st, rect, phase);
    // Paint over Tone's dots: they are paint now, which the eraser takes.
    if (s.dots) for (let y = Math.max(0, rect[1]); y <= Math.min(s.h - 1, rect[3]); y++) for (let i = y * s.w + Math.max(0, rect[0]), e = y * s.w + Math.min(s.w - 1, rect[2]); i <= e; i++) if (s.dots[i] && s.page[i] !== s.base[i]) s.dots[i] = 0;
    return;
  }
  const w = s.w, P = PAPER_TONE, dx = S.misX, dy = S.misY;
  const X0 = Math.max(0, rect[0] - dx), Y0 = Math.max(0, rect[1] - dy), X1 = Math.min(s.w - 1, rect[2] + dx), Y1 = Math.min(s.h - 1, rect[3] + dy);
  if (X1 < X0 || Y1 < Y0) return;
  const gw = X1 - X0 + 1, gh = Y1 - Y0 + 1, page = s.page, base = s.base, saved = new Float32Array(gw * gh), A = new Float32Array(gw * gh);
  for (let y = 0; y < gh; y++) for (let x = 0; x < gw; x++) saved[y * gw + x] = page[(y + Y0) * w + x + X0];
  const paper = s.nmPaper ??= new Float32Array(s.w * s.h).fill(P), keepK = s.kept, keepKB = s.keptBase;
  s.base = paper; s.kept = null; s.keptBase = null;
  try { composeOwn(st, [X0, Y0, X1, Y1], phase); } finally { s.base = base; s.kept = keepK; s.keptBase = keepKB; }
  for (let y = 0; y < gh; y++) for (let x = 0; x < gw; x++) { const i = (y + Y0) * w + x + X0; A[y * gw + x] = clamp(1 - page[i] / P); page[i] = saved[y * gw + x]; }
  const kept = s.kept && st.e < 1 ? s.kept : null, kb = kept ? s.keptBase : null, ke = 1 - st.e, rp = s.resist, blend = R.blend || "own";
  const lays = blend === "lighten" || blend === "invert", to = (u, bit) => bit ? (blend === "lighten" ? P : blend === "invert" ? P - u : 0) : u;
  if (S.tone) s.dots ??= new Uint8Array(s.w * s.h);
  // The print tone (the app's ToneDocument.Layer.print): what the screened marks would show unscreened, kept on the
  // page, made by the first screened stroke; each builds on it as it found it (st.printBase), so a second prints darker.
  const prints = !S.tone && !lays, pr = prints ? (s.print ??= new Float32Array(s.w * s.h).fill(P)) : null, pb = prints ? (st.printBase ??= Float32Array.from(pr)) : null;
  const ex = Math.min(s.w - 1, rect[2] + dx), ey = Math.min(s.h - 1, rect[3] + dy);
  for (let y = Math.max(0, rect[1]); y <= ey; y++) for (let x = Math.max(0, rect[0]); x <= ex; x++) {
    const i = y * w + x, gy = y - Y0, gx = x - X0, own = A[gy * gw + gx];
    let a = own;
    if ((dx || dy) && gx - dx >= 0 && gy - dy >= 0) a = Math.max(a, SCREEN_GHOST * A[(gy - dy) * gw + gx - dx]);
    if (blend === "behind") a *= blankOf(base[i]);
    if (prints) {
      const f0 = rp ? 1 - rp[i] : 1, p = printedTone(pb[i], a, R.cover > 0 && own > 0 ? R.cover : 0);
      pr[i] = pb[i] + (p - pb[i]) * f0;
      if (pb[i] < P) a = 1 - p / P;
    }
    const bit = screenInk(S, a, x, y, s.h, s.low[i]), u = base[i], f = rp ? 1 - rp[i] : 1;
    const target = bit ? to(u, true) : R.cover > 0 && !lays && own > 0 ? u + (P - u) * R.cover : u;
    page[i] = u + (target - u) * f;
    if (kept) { const k0 = kb[i], t0 = bit ? to(k0, true) : k0; kept[i] = k0 + (t0 - k0) * ke * f; }
    if (S.tone) s.dots[i] = bit ? 1 : 0;
  }
};
// The print tone a screened mark of darkness a over white leaves over print tone v (the app's PrintScreen.printed): the
// darker of the two, so strokes over strokes at one tone print the same dots; opaque paint, covering it by cover, moves it toward its own.
function printedTone(v, a, cover) { const t = Math.min(v, PAPER_TONE * (1 - a)); return cover > 0 ? t + (PAPER_TONE * (1 - a) - t) * cover : t; }
// The eraser scrapes Tone's dots in streaks along its stroke (the scrape branch of mischief-lab.html's dab1, a pass of
// the blade taken at once): a light streak takes about one in five, a heavy one nine in ten. Anything else erases.
const EraserNM = Eraser;
Eraser = class extends EraserNM {
  disc(cx, cy, rad, ...rest) {
    const s = this.s, dots = s.dots;
    // The print tone goes where the eraser leaves paper (once a stroke, as the eraser takes each pixel).
    if (s.print) for (let y = Math.max(0, Math.floor(cy - rad)); y <= Math.min(s.h - 1, Math.ceil(cy + rad)); y++) for (let x = Math.max(0, Math.floor(cx - rad)); x <= Math.min(s.w - 1, Math.ceil(cx + rad)); x++) {
      const i = y * s.w + x;
      if (!this.seen[i] && (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 <= rad * rad && (s.kept ? s.kept[i] : PAPER_TONE) >= PAPER_TONE - .5) s.print[i] = PAPER_TONE;
    }
    if (!dots) return super.disc(cx, cy, rad, ...rest);
    if (this.nmSalt === undefined) { this.nmSalt = (s.nmScrapes = (s.nmScrapes || 0) + 1) * 0x9e3779b1; this.nmHead = [1, 0]; }
    if (this.nmAt) { const ddx = cx - this.nmAt[0], ddy = cy - this.nmAt[1], d = Math.hypot(ddx, ddy); if (d > .5) this.nmHead = [ddx / d, ddy / d]; }
    this.nmAt = [cx, cy];
    const [hx, hy] = this.nmHead, w = s.w, held = [];
    for (let y = Math.max(0, Math.floor(cy - rad)); y <= Math.min(s.h - 1, Math.ceil(cy + rad)); y++) for (let x = Math.max(0, Math.floor(cx - rad)); x <= Math.min(w - 1, Math.ceil(cx + rad)); x++) {
      const i = y * w + x;
      if (this.seen[i] || !dots[i] || (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 > rad * rad) continue;
      const n = screenHash(Math.floor((-hy * x + hx * y) / 1.4), 0, 31), take = 1 - Math.pow(1 - (.08 + .5 * n * n), 3);
      if (screenHash(x, y, this.nmSalt) < take) dots[i] = 0; else held.push(i, s.page[i]);
    }
    const out = super.disc(cx, cy, rad, ...rest);
    for (let k = 0; k < held.length; k += 2) s.page[held[k]] = s.base[held[k]] = held[k + 1];
    return out;
  }
};
const blankPageScreen0 = blankPage;
blankPage = function (s) { blankPageScreen0(s); s.dots = null; s.print = null; };
// The lab shows Screen's own dials only with a screen; Cell and Angle greyed where its print has none.
function dialShown(B, part, key) { return !(part === "medium" && UNDER_SCREEN.has(key) && B.medium.k !== "tone" && !(B.medium.d.screen | 0)); }
if (typeof dialActive === "function") {
  const dialActiveNM = dialActive;
  dialActive = (B, part, key) => {
    if (part === "medium" && (key === "screen" || UNDER_SCREEN.has(key)) && B.medium.k !== "tone" && B.life.k === "float") return false;
    const sc = B.medium.d.screen | 0, pt = B.medium.d.pattern | 0, tone = B.medium.k === "tone";
    if (part === "medium" && key === "cell" && (tone ? pt === 2 : sc === 3)) return false;
    if (part === "medium" && key === "sangle" && (tone ? pt === 2 : sc === 3 || sc === 4)) return false;
    return dialActiveNM(B, part, key);
  };
}

// ---------- rolls: rare, from the seed, after every part (the app's ModularStyle.rollTone, rollScreen) ----------
Object.assign(NM_ODDS, { screen: { [WILDCARD]: 25, Marker: 25, "Ink wash": 25, Charcoal: 25, Fineliner: 40, Pencil: 40, Print: 1 }, tone: { [WILDCARD]: 40, Print: 5 } });
Object.assign(NM_SALT, { screen: 0x5c4ee, tone: 0x70e5, screen2: 0x5c4e6 });
NM.screenRolls = true; NM.toneRolls = true;
function rollScreen(style, B, lk, old) {
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i), odds = key => NM_ODDS[key][style] || 0;
  const free = !(old && lk.parts.has("medium"));
  if (free && NM.toneRolls && odds("tone") && B.medium.k !== "resist" && u("tone", 0) < 1 / odds("tone")) {
    B.medium = switchChoice("medium", B.medium, "tone");
    Object.assign(B.medium.d, { pattern: Math.min(3, (u("tone", 1) * 4) | 0), ttone: lerp(.25, .65, u("tone", 2)), cell: Math.round(lerp(4, 10, u("tone", 3))),
      sangle: u("tone", 4) < .5 ? 45 : Math.round(lerp(0, 90, u("tone", 5))) });
  }
  if (free && NM.screenRolls && !lk.dials.has("medium.screen") && odds("screen") && choiceOf("medium", B.medium.k).dials.includes("screen") && u("screen", 0) < 1 / odds("screen"))
    // Print picks any of the first ten alike; elsewhere one roll in six takes one of the six appended prints (the rate of a Screen stays as it was).
    Object.assign(B.medium.d, { screen: style === "Print" ? 1 + Math.min(9, (u("screen2", 1) * 10) | 0) : u("screen2", 0) < 1 / 6 ? 5 + Math.min(5, (u("screen2", 1) * 6) | 0) : 1 + Math.min(3, (u("screen", 1) * 4) | 0), cell: Math.round(lerp(4, 10, u("screen", 2))),
      sangle: u("screen", 3) < .5 ? 45 : Math.round(lerp(0, 90, u("screen", 4))), mis: u("screen", 5) < .25 ? Math.round(lerp(1, 5, u("screen", 6))) : 0,
      gain: u("screen", 7) < 1 / 3 ? lerp(.2, .6, u("screen", 8)) : 0, uneven: u("screen", 9) < 1 / 3 ? lerp(.2, .6, u("screen", 10)) : 0 });
}
const rollNewNM = rollNew;
rollNew = function (style, B, lk, old) { rollNewNM(style, B, lk, old); rollScreen(style, B, lk, old); };
const makeVarsNM = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.medium.slice();
  CHOICES.medium = keep.filter(c => c.k !== "tone" || B.medium.k === "tone");
  try { return makeVarsNM(B, locks); } finally { CHOICES.medium = keep; }
};

// ---------- words (docs/TEXT-STYLE.md): the print, or the tone, says what it lays ----------
const SCREEN_WORDS = ["", "Halftone", "Engraved", "Dithered", "Grained", "Rosette", "Ben-Day", "Crosshatch", "Wavy-line", "Mezzotint", "Diamond-dot", "Stippled", "Woodcut", "Scribbled", "Hatched"],
  SCREEN_TAGS = ["", "halftone", "engraved", "dithered", "grained", "rosette", "ben-day", "crosshatch", "wavy-line", "mezzotint", "diamond-dot", "stippled", "woodcut", "scribbled", "hatched"];
const TONE_WORDS = ["Dot screentone", "Line screentone", "Sand screentone", "Gradient screentone", "Crosshatch screentone", "Wavy-line screentone", "Mezzotint screentone",
  "Stipple screentone", "Woodcut screentone", "Scribble screentone", "Hatched screentone"];
// The app's LabText: a character .6 em, m and w .9; the Medium module's line holds 9 em (LabLayout.choiceEm).
const emsOf = t => [...t].reduce((n, c) => n + ("mwMW%@".includes(c) ? .9 : .6), 0);
const fitsMedium = t => emsOf(t) <= 9 + 1e-4;
const screenWords = (sc, base) => { const both = SCREEN_WORDS[sc] + " " + base.toLowerCase(); return fitsMedium(both) ? both : SCREEN_WORDS[sc]; };
const toneWords = B => TONE_WORDS[B.medium.d.pattern | 0];
// The Medium card: "Screentone" alone where its pattern's name would pass the line ("Gradient screentone").
const toneCard = B => fitsMedium(toneWords(B)) ? toneWords(B) : "Screentone";
// A Medium swap tile's note (ModularWords.swapNote): Screentone's pattern, as the tile says Screentone already.
const mediumNote = B => B.medium.k === "tone" ? DIALS.medium.pattern.choices[B.medium.d.pattern | 0].toLowerCase() : choiceWords(B, "medium");
// Tone worded as the ink line it is laid as (no Erasable of its own, no smudge).
const asToneInk = B => { const A = cloneBrush(B); A.medium = freshPart("medium", "ink"); A.medium.d.erase = 0; A.carry = freshPart("carry", "off"); return normBrush(A); };
// A screened mark lays no grey after it: no Slow settling to say (the app's: it doesn't settle).
const noSettle = t => t !== "slow" && !/^Wet for /.test(t);
const choiceWordsScreen = choiceWords;
choiceWords = function (B, part) {
  if (part === "medium" && B.medium.k === "tone") return toneCard(B);
  if (part === "medium" && (B.medium.d.screen | 0) && !(B.medium.d.blend | 0)) return screenWords(B.medium.d.screen | 0, NM_BASE[B.medium.k]);
  return choiceWordsScreen(B, part);
};
const captionItemsScreen = captionItems;
captionItems = function (B) {
  if (B.medium.k === "tone") {
    const A = asToneInk(B), W = cloneBrush(A); W.medium = freshPart("medium", "wash");
    const other = captionItemsScreen(normBrush(W)), items = captionItemsScreen(A).filter(t => other.includes(t));
    items.splice(1, 0, toneWords(B).toLowerCase());
    return items.filter(noSettle);
  }
  const sc = B.medium.d.screen | 0, items = sc ? captionItemsScreen(B).filter(noSettle) : captionItemsScreen(B);
  if (sc) items.splice(1 + ((B.medium.d.blend | 0) ? 1 : 0), 0, SCREEN_TAGS[sc]);
  return items;
};
// Control: a mark that reaches the page as dots (Print shading, Screentone; not on water) prints its darkness as dots,
// so the darkness cells say so (the app's ModularWords.handWords and darkHead): "pressure grows dots", the grid's Dots.
const printsDots = B => B.medium.k === "tone" || (B.medium.d.screen | 0) > 0 && B.life.k !== "float";
const darkHead = B => printsDots(B) ? "Dots" : "Dark";
const handWordsScreen = handWords;
handWords = function (B, n = 2) {
  const t = handWordsScreen(B, n);
  return printsDots(B) ? t.replace(/\bdarkens\b/g, "grows dots").replace(/\blightens\b/g, "shrinks dots").replace(/\bdarken\b/g, "grow dots").replace(/\blighten\b/g, "shrink dots") : t;
};
// Misregister, Ink gain and Uneven: the two strongest, one sentence.
function newsprintWords(M) {
  const on = [[(M.d.mis || 0) / 8 - .1, "doubled"], [(M.d.gain || 0) - .25, "dots swell"], [(M.d.uneven || 0) - .25, "patchy ink"]].filter(m => m[0] > 0).sort((a, b) => b[0] - a[0]);
  return on.length ? cap1(on[0][1]) + (on.length > 1 ? ", " + on[1][1] : "") + "." : null;
}
const plainWordsScreen = plainWords;
plainWords = function (B) {
  const tone = B.medium.k === "tone", sc = tone ? 0 : B.medium.d.screen | 0;
  if (!tone && !sc) return plainWordsScreen(B);
  const text = plainWordsScreen(tone ? asToneInk(B) : B), all = text.split(/(?<=\.)\s+/).filter(Boolean).filter(noSettle);
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(all[0]), noun = m ? m[1] : all[0].slice(0, -1), tail = m && m[2] ? m[2] : "";
  const out = [(tone ? toneWords(B) : SCREEN_WORDS[sc] + " " + lc1(noun)) + tail + "."];
  let k = 1;
  const SAID = ["Builds up where it lingers.", "Goes behind marks.", "Inverts what's under it."];
  if (all[k] === SAID[0]) out.push(all[k++]);
  if (tone) out.push("Scrapes off in streaks.");
  while (k < all.length && SAID.includes(all[k])) out.push(all[k++]);
  const news = sc ? newsprintWords(B.medium) : null;
  if (news) out.push(news);
  out.push(...all.slice(k));
  let t2 = out[0];
  for (let i = 1; i < out.length; i++) { if (i >= 2 && t2.length + 1 + out[i].length > 260) break; t2 += " " + out[i]; }
  return t2;
};

// ---------- checks ----------
function screenChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 360, H = 240, q = quantTone, P = PAPER_TONE;
  const mk = (medium, md, life) => {
    const B = { name: "s", seed: 23, style: "Fineliner", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium || "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", life || "wet"), hand: freshPart("hand", "steady") };
    Object.assign(B.medium.d, md || {});
    return normBrush(B);
  };
  const line = (x0, y0, x1, y1, p = .8, n = 90) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p, t: k * 6, tx: NaN, ty: NaN }));
  const seg = c => c.split("_")[1];
  // Codes: none leaves the code as it was; each screen one digit after Blend's; Tone is _6_ at its defaults.
  { const ink = mk(), codes = [1, 2, 3, 4].map(sc => encodeM(mk("ink", { screen: sc }))), tone = mk("tone");
    t("Screen is written only when not none (" + seg(codes[0]) + "), Tone is _" + seg(encodeM(tone)) + "_", encodeM(mk("ink", { screen: 0 })) === encodeM(ink)
      && codes.every((c, i) => seg(c).startsWith(seg(encodeM(ink))) && seg(c).endsWith("0" + (i + 1)) && encodeM(readAnyCode(c).b) === c) && seg(encodeM(tone)) === "6"); }
  // Pure black and white, overlapping strokes on the same dots.
  { const res = [];
    for (let sc = 1; sc <= 4; sc++) {
      const B = mk("wash", { screen: sc, wet: .3 }), a = modSurface(W, H, 7), c = modSurface(W, H, 7), both = modSurface(W, H, 7);
      layStroke(a, B, line(20, 100, 340, 100), 80, 1, null); layStroke(c, B, line(40, 140, 320, 140), 80, 1, null);
      layStroke(both, B, line(20, 100, 340, 100), 80, 1, null); layStroke(both, B, line(40, 140, 320, 140), 80, 1, null);
      // Exactly both strokes' dots, where one reached and where both did: the same tone prints the same dots (the app's
      // print tone is the darker of the two), so strokes over strokes fill evenly.
      let grey = 0, wrong = 0, inked = 0, more = 0;
      for (let i = 0; i < W * H; i++) {
        const v = both.page[i], either = a.page[i] < .5 || c.page[i] < .5, over = a.print[i] < P && c.print[i] < P;
        if (v > .5 && v < P - .5) grey++; if (v < .5) inked++;
        if (either && v >= .5 || !over && !either && v < .5) wrong++; else if (over && !either && v < .5) more++;
      }
      res.push(grey + "/" + wrong + "/" + inked + "/" + more);
      if (grey || wrong || inked < 300 || more) { t("screen " + sc + ": pure black and white, overlaps lay both's dots and no more (" + res.join(" ") + ")", false); break; }
    }
    if (res.length === 4) t("every screen lays pure black and white; two overlapping strokes lay both's dots and no more (grey/wrong/black/more " + res.join(" ") + ")", true); }
  // The dots follow the tone the brush lays unscreened: a grey swatch about half; a second stroke the same (the darker of the two).
  { const share = (shade, n, sc) => { const s = modSurface(W, H, 7), plain = modSurface(W, H, 7);
      for (let k = 0; k < n; k++) { layStroke(s, mk("ink", { screen: sc }), line(20, 120, 340, 120), 20, shade, null); layStroke(plain, mk(), line(20, 120, 340, 120), 20, shade, null); }
      let foot = 0, bl = 0, dark = 0; for (let i = 0; i < W * H; i++) if (plain.page[i] < P - 5) { foot++; dark += 1 - plain.page[i] / P; if (s.page[i] < .5) bl++; }
      return [bl / foot, dark / foot]; };
    const one = share(.5, 1, 1), two = share(.5, 2, 1), f = v => Math.round(100 * v) + "%";
    t("a grey halftone ink lays " + f(one[0]) + " dots for " + f(one[1]) + " grey, twice " + f(two[0]) + " for " + f(two[1]),
      Math.abs(one[0] - one[1]) < .12 && Math.abs(two[0] - one[0]) < .02); }
  // Tone: black dots over the fill, the pen line kept; the eraser scrapes some in streaks.
  { const s = modSurface(W, H, 7); layStroke(s, mk(), line(20, 120, 340, 120), 4, 1, null);
    const pen = Float32Array.from(s.page);
    for (let y = 60; y <= 180; y += 20) layStroke(s, mk("tone", { ttone: .6 }), line(20, y, 340, y), 40, 1, null);
    let dots = 0, kept = 0, lines = 0;
    for (let i = 0; i < W * H; i++) { if (s.dots[i]) dots++; if (pen[i] < 60) { lines++; if (s.page[i] <= pen[i] + .5) kept++; } }
    const er = new Eraser(s, 40, false); for (let k = 0; k <= 60; k++) er.add([[20 + 5 * k, 120, 1, k * 6, NaN, NaN]]);
    let after = 0; for (let i = 0; i < W * H; i++) if (s.dots[i]) after++;
    t("Tone lays " + dots + " black dots and keeps the pen line (" + kept + " of " + lines + "); the eraser scrapes some (" + after + " left)", dots > 3000 && kept === lines && after < dots && after > dots * .6); }
  // Rolls: with them off every roll is as before; on, a screen or Tone at the odds, nothing else changed.
  { const roll = on => { const keep = Math.random, k0 = NM.screenRolls, k1 = NM.toneRolls; Math.random = rng(4242); NM.screenRolls = NM.toneRolls = on;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.screenRolls = k0; NM.toneRolls = k1; } };
    const a = roll(false), b = roll(true); let screens = 0, tones = 0, odd = 0;
    // Codes without the name, which says what the brush is (a screen or Tone renames it).
    const code = B => encodeM(Object.assign(cloneBrush(B), { name: "n" }));
    for (let i = 0; i < a.length; i++) {
      if (code(a[i]) === code(b[i])) continue;
      if (b[i].medium.k === "tone") { tones++; if (b[i].style !== WILDCARD && b[i].style !== "Print") odd++; continue; }
      const c = cloneBrush(b[i]); for (const k of SCREEN_DIALS) c.medium.d[k] = DIALS.medium[k].def;
      if ((b[i].medium.d.screen | 0) && code(normBrush(c)) === code(a[i]) && b[i].style !== "Watercolor") screens++; else odd++;
    }
    // Print screens every roll (one in five Tone): about 1400 / 15 more.
    t("rolls: " + screens + " gain a Screen, " + tones + " become Tone, none else changed", odd === 0 && screens > 60 && screens < 260 && tones >= 1); }
  // Words.
  { const h = mk("ink", { screen: 1 }), tn = mk("tone");
    t("words: \"" + choiceWords(h, "medium") + "\", \"" + plainWords(h) + "\", \"" + choiceWords(tn, "medium") + "\", \"" + plainWords(tn) + "\"",
      choiceWords(h, "medium") === "Halftone ink" && plainWords(h).startsWith("Halftone ink line.") && captionItems(h).includes("halftone") && choiceWords(tn, "medium") === "Dot screentone"
      && plainWords(tn).startsWith("Dot screentone. Scrapes off in streaks.") && captionItems(tn)[1] === "dot screentone"); }
  // The ten appended prints (the app's ScreenChecks.newPrints): each inks about as much as the grey asks and more with more grey,
  // reads back from its code as the same choice, and says its name; the old four stay where they were.
  { const names = ["Rosette", "Ben-Day", "Crosshatch", "Wavy Lines", "Mezzotint", "Diamond Dots", "Stipple", "Woodcut", "Scribble", "Hatching"], pick = DIALS.medium.screen.choices;
    let ok = pick.length === 15 && pick.slice(0, 5).join() === "None,Halftone,Engraving,Dither,Grain" && pick.slice(5).join() === names.join(), worst = 0;
    for (let s = 5; s <= 14; s++) {
      const S = screenOf(mk("ink", { screen: s }).medium); let last = -1;
      for (const g of [.1, .25, .5, .75, .9]) {
        let n = 0; for (let y = 0; y < 200; y++) for (let x = 0; x < 200; x++) if (screenInk(S, g, x, y, 360, 0)) n++;
        const f = n / 40000; worst = Math.max(worst, Math.abs(f - g) - (s === 5 ? .06 : 0)); if (!(f > last)) ok = false; last = f;
      }
      const B = mk("ink", { screen: s }), back = readAnyCode(encodeM(B)).b;
      ok = ok && (back.medium.d.screen | 0) === s && encodeM(normBrush(back)) === encodeM(B) && choiceWords(B, "medium").length > 5;
    }
    t("the ten appended prints: coverage within " + Math.round(100 * worst) + "% of the grey, growing with it, codes and names", ok && worst < .06); }
  return out;
}
const selfTestScreen = selfTest;
selfTest = function () { const r = selfTestScreen(); r.screen = screenChecks(); r.ok = r.ok && r.screen.every(c => c.ok); NM.lastScreen = r.screen; return r; };
// Erasable's check hashes rolls from before the new choices: it runs with their rolls off.
const eraseChecksScreen = eraseChecks;
eraseChecks = function () { const k0 = NM.screenRolls, k1 = NM.toneRolls, k2 = NM.waterRolls; NM.screenRolls = NM.toneRolls = NM.waterRolls = false; try { return eraseChecksScreen(); } finally { NM.screenRolls = k0; NM.toneRolls = k1; NM.waterRolls = k2; } };
if (typeof runChecks === "function") {
  const runChecksScreen = runChecks;
  runChecks = function () {
    runChecksScreen();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastScreen) return;
      out.textContent += "\n\nScreen and Tone:\n" + NM.lastScreen.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 40);
  };
}

// ===================================================================================================================
// The wet smudges (spec docs/archive/superpowers/specs/2026-10-19-wet-smudge-design.md; the app's HeldPaint and ClearWater):
// Smudge's Keeps Paint (Drag's and Smear's, a dirty brush that keeps what it picks up for the next stroke) and Smudge's
// fifth choice, Clear Water (lays only water: lifts, and lets what it lifted run out through its wet). Their tables and
// words are parts.js's, their drawing stroke.js's; here Clear Water's rare roll from the seed and the checks.
// ===================================================================================================================
Object.assign(NM_ODDS, { water: { [WILDCARD]: 40, Watercolor: 50, "Ink wash": 50 } });
Object.assign(NM_SALT, { water: 0xc1ea4 });
NM.waterRolls = true;
// Rare (the app's ModularStyle.rollClearWater): after every part, from the seed with its salt, never on Float; Pick up
// .35–.75, Lay down .4–.8. Keeps Paint is never rolled.
function rollWater(style, B, lk, old) {
  const u = i => hash01((B.seed ^ NM_SALT.water) >>> 0, i), n = NM_ODDS.water[style] || 0;
  if (!NM.waterRolls || !n || (old && lk.parts.has("carry")) || B.life.k === "float" || !(u(0) < 1 / n)) return;
  B.carry = { k: "water", d: { pick: lerp(.35, .75, u(1)), lay: lerp(.4, .8, u(2)) } };
}
const rollNewScreen = rollNew;
rollNew = function (style, B, lk, old) { rollNewScreen(style, B, lk, old); rollWater(style, B, lk, old); };
function wetChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 360, H = 240, P = PAPER_TONE;
  const mk = (carry, cd, medium, life) => {
    const B = { name: "w", seed: 23, style: "Watercolor", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium || "wash"), carry: freshPart("carry", carry),
      paper: freshPart("paper", "plain"), life: freshPart("life", life || "wet"), hand: freshPart("hand", "steady") };
    Object.assign(B.carry.d, cd || {});
    return normBrush(B);
  };
  const line = (x0, y0, x1, y1, p = .8, n = 90) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p, t: k * 6, tx: NaN, ty: NaN }));
  const mean = (s, x0, x1, y0, y1) => { let a = 0, n = 0; for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { a += s.page[y * W + x]; n++; } return a / n; };
  const seg = c => c.split("_")[2];
  // Codes: Keeps Paint written only when on (after Soften), Clear Water is _4.
  { const smear = mk("smudge", { load: 0 }), dirty = mk("smudge", { load: 0, keep: .9 }), water = mk("water");
    t("Keeps Paint is written only when on (" + seg(encodeM(smear)) + " → " + seg(encodeM(dirty)) + "), Clear Water is _" + seg(encodeM(water)) + "_",
      encodeM(mk("smudge", { load: 0, keep: 0 })) === encodeM(smear) && seg(encodeM(dirty)).length === seg(encodeM(smear)).length + 4 && seg(encodeM(water)).startsWith("4")
      && [dirty, water].every(b => { const c = encodeM(b); return encodeM(readAnyCode(c).b) === c; })); }
  // Rolls: with them off every roll is as before; on, Clear Water now and then, nothing else changed.
  { const roll = on => { const keep = Math.random, k0 = NM.waterRolls; Math.random = rng(5151); NM.waterRolls = on;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.waterRolls = k0; } };
    const a = roll(false), b = roll(true); let water = 0, odd = 0, kept = 0;
    for (let i = 0; i < a.length; i++) {
      if ((b[i].carry.d.keep ?? 0) > 0) kept++;
      if (encodeM(a[i]) === encodeM(b[i])) continue;
      const c = cloneBrush(b[i]); c.carry = cloneBrush(a[i]).carry;
      if (b[i].carry.k === "water" && encodeM(normBrush(c)).split(".M")[1] === encodeM(a[i]).split(".M")[1]) water++; else odd++;
    }
    t("rolls: " + water + " become Clear Water, none else changed, Keeps Paint never rolled", odd === 0 && water >= 3 && water < 80 && kept === 0); }
  // Keeps Paint: a dirty brush lays what it picked up on the next stroke; off, nothing.
  { const draw = keep => { const s = modSurface(W, H, 7); for (let y = 40; y < 140; y++) for (let x = 60; x < 120; x++) s.page[y * W + x] = 60;
      const B = mk("smudge", { load: 0, pick: .75, lay: .5, keep });
      layStroke(s, B, line(30, 90, 150, 90), 60, .5, null); layStroke(s, B, line(180, 200, 340, 200), 60, .5, null); return mean(s, 185, 215, 185, 215); };
    const dirty = draw(.9), clean = draw(0);
    t("a dirty brush lays what it picked up on the next stroke (" + dirty.toFixed(1) + "; clean " + clean.toFixed(1) + ")", dirty < P - 8 && clean > P - .5); }
  // Clear Water: it lightens a wash where it goes and runs some of it out beside, onto the paper; on paper it lays nothing.
  { const s = modSurface(W, H, 7); for (let y = 0; y < H; y++) for (let x = 0; x < 180; x++) s.page[y * W + x] = 130;
    layStroke(s, mk("water"), line(180, 20, 180, 220), 60, .5, null);
    const inside = mean(s, 150, 172, 60, 180), beside = mean(s, 186, 205, 60, 180), blank = modSurface(W, H, 7);
    layStroke(blank, mk("water"), line(60, 120, 300, 120), 60, .5, null);
    t("Clear Water lifts a wash (" + inside.toFixed(1) + " from 130) and runs it out beside (" + beside.toFixed(1) + "), nothing on paper", inside > 135 && beside < P - .5 && mean(blank, 0, W - 1, 0, H - 1) > P - .01); }
  // Words.
  { const d = mk("smudge", { load: 0, keep: .9 }), w = mk("water");
    t("words: \"" + plainWords(d) + "\", \"" + plainWords(w) + "\", \"" + choiceWords(w, "carry") + "\"",
      plainWords(d).includes("Smears. Keeps paint for the next stroke.") && captionItems(d).includes("keeps paint") && plainWords(w).startsWith("Clear water. Lifts paint, lets it run.")
      && choiceWords(w, "carry") === "Water" && captionItems(w).includes("clear water") && isClearWater(mk("water", {}, "ink", "float"))); }
  return out;
}
const selfTestWet = selfTest;
selfTest = function () { const r = selfTestWet(); r.wet = wetChecks(); r.ok = r.ok && r.wet.every(c => c.ok); NM.lastWet = r.wet; return r; };
if (typeof runChecks === "function") {
  const runChecksWet = runChecks;
  runChecks = function () {
    runChecksWet();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastWet) return;
      out.textContent += "\n\nWet smudges:\n" + NM.lastWet.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 60);
  };
}

// ===================================================================================================================
// Flick and Fling (the mischief modules spec, Stage 2; mischief-lab.html's flick, fling, land2 and watch2; the app's
// FlickFling): ink that keeps going after the pen.
//   Flick  a Tip dial, last of every tip's: a stroke lifted while still moving carries on along its last heading and
//          turn, eased out, for about Flick × the speed: its own samples past the lift, narrowing and running dry.
//   Fling  a Medium dial, last of every medium with Blend: a hard stop (fast in the last 140 ms, then still 45 ms with
//          the pen down, or a lift right after) pools a little ink and throws drops ahead, nearest landing first.
// Both read only the samples and the seed, so a redraw lands the same. Appended, written only when on: old codes as before.
// Bristle's Paint Load (the app's, not drawn here) goes before Flick so the codes line up.
// ===================================================================================================================
Object.assign(DIALS.tip, { load: { label: "Paint Load", min: 0, max: 1, def: .5, hide: true }, flick: { label: "Flick", min: 0, max: 1, def: 0 } });
DIALS.medium.fling = { label: "Fling", min: 0, max: 1, def: 0 };
choiceOf("tip", "bristles").dials.push("load");
for (const c of CHOICES.tip) c.dials.push("flick");
for (const c of CHOICES.medium) if (c.dials.includes("blend")) c.dials.push("fling");
for (const k of ["tip.load", "tip.flick", "medium.fling"]) ADDED.add(k);
KEEPS.tip.push("flick"); KEEPS.medium.push("fling");
// The prototype's pad is CSS px (1/96 in), the Manta's page px 1/300 in: its lengths and speeds taken PROTO times.
const FF = { PROTO: 3, LIFT_MS: 40, FLICK_MS: 30, PEAK_MS: 140, STILL_MS: 45, AIM_MS: 40, STILL_PX: 4, FASTEST: 5, STEP: 1.5, REACH: 85, SALT: 0xf1196 };
Object.assign(FF, { FLICK_SPEED: .35 * FF.PROTO, MOST: 260 * FF.PROTO, LEAST: 3 * FF.PROTO, TURN: .012 / FF.PROTO, FLING_SPEED: FF.PROTO, AIM_PX: 3 * FF.PROTO });
const resolveFF = resolve;
resolve = function (B, size, shade) {
  const R = resolveFF(B, size, shade);
  R.flick = B.tip.d.flick || 0; R.fling = B.medium.k === "tone" || B.medium.k === "resist" ? 0 : B.medium.d.fling || 0;
  return R;
};
// velocity2: over the samples within win ms before sample k; the heading at k itself (its last AIM_PX), and the turn.
function ffMotion(pts, k, win) {
  const L = pts[k];
  let j = k; while (j > 0 && pts[j - 1].t >= L.t - win) j--;
  if (j === k && j > 0) j--;
  const P = pts[j], dt = Math.max(1, L.t - P.t), vx = (L.x - P.x) / dt, vy = (L.y - P.y) / dt, m = { vx, vy, speed: Math.hypot(vx, vy), dir: Math.atan2(vy, vx), turn: 0 };
  let e = k; while (e > 0 && L.d - pts[e].d < FF.AIM_PX) e--;
  m.end = e < k ? Math.atan2(L.y - pts[e].y, L.x - pts[e].x) : m.dir;
  let q = j; while (q > 0 && pts[q - 1].t >= P.t - win) q--;
  const len = Math.hypot(L.x - P.x, L.y - P.y);
  if (q < j && len > 2 * FF.PROTO) {
    let da = m.dir - Math.atan2(P.y - pts[q].y, P.x - pts[q].x);
    while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
    m.turn = clamp(da / len, -FF.TURN, FF.TURN);
  }
  return m;
}
// peak2: the fastest the pen went (over about 20 ms) in the win ms up to sample k.
function ffPeak(pts, k, win) {
  let most = 0; const lt = pts[k].t;
  for (let i = k, j = k; i > 0 && pts[i].t >= lt - win; i--) {
    if (j > i) j = i;
    while (j > 0 && pts[i].t - pts[j].t < 20) j--;
    const dt = pts[i].t - pts[j].t;
    if (dt > 0) most = Math.max(most, (pts[i].d - pts[j].d) / dt);
  }
  return most;
}
class FlickFling {
  constructor(R) { this.R = R; this.anchorT = null; this.lastT = null; this.checkedAt = null; this.anchorK = 0; this.flung = false; this.lifted = false; this.drops = []; this.landed = 0; }
  // Every sample, kept or not: where the pen rests and since when; a hard stop held STILL_MS throws the fling.
  sampled(pts, x, y, t) {
    if (t === undefined || this.lifted || !pts.length) return;
    this.lastT = t;
    if (this.anchorT === null || Math.hypot(x - this.ax, y - this.ay) > FF.STILL_PX) { this.ax = x; this.ay = y; this.anchorT = t; this.anchorK = pts.length - 1; return; }
    if (this.flung || !(this.R.fling > 0) || t - this.anchorT < FF.STILL_MS || this.checkedAt === this.anchorT) return;
    this.checkedAt = this.anchorT;
    const peak = ffPeak(pts, this.anchorK, FF.PEAK_MS);
    if (peak > FF.FLING_SPEED) this.fling(pts, peak, t);
  }
  // The lift: still moving, the flick's samples past it ([x, y, p, t, tx, ty] each), marking pts; just after a stop, the fling.
  lift(pts) {
    if (this.lifted) return null;
    this.lifted = true;
    if (this.flung || this.lastT === null || pts.length < 3) return null;
    const last = pts.length - 1, m = ffMotion(pts, last, FF.FLICK_MS);
    if (this.lastT - this.anchorT < FF.LIFT_MS && m.speed > FF.FLICK_SPEED) return this.R.flick > 0 ? this.carry(pts, m) : null;
    if (this.R.fling > 0) { const peak = ffPeak(pts, this.anchorK, FF.PEAK_MS); if (peak > FF.FLING_SPEED) this.fling(pts, peak, this.lastT); }
    return null;
  }
  carry(pts, m) {
    const len = Math.min(FF.MOST, this.R.flick * m.speed * FF.REACH);
    if (len < FF.LEAST) return null;
    const L = pts[pts.length - 1], ms = 150 + 150 * Math.min(1, len / FF.PROTO / 220), n = Math.ceil(len / FF.STEP), ds = len / n, out = [];
    let th = m.end, x = L.x, y = L.y;
    for (let i = 0; i < n; i++) {
      th += m.turn * ds * .85; x += Math.cos(th) * ds; y += Math.sin(th) * ds;
      const u = (i + 1) / n, e = 1 - Math.pow(1 - u, 1 / 2.2);
      out.push([x, y, L.p * (1 - u), L.t + Math.round(e * ms), L.tx, L.ty]);
    }
    pts.flick = { from: L.d, len };
    return out;
  }
  fling(pts, peak, now) {
    this.flung = true;
    const R = this.R, k = this.anchorK, S = pts[k], a = R.fling, v = Math.min(FF.FASTEST, peak / FF.PROTO), dir = ffMotion(pts, k, FF.AIM_MS).dir;
    const r0 = R.line ? Math.max(.6, R.inkSize * lerp(1 - .75 * R.pen.pressure, 1 + .6 * R.pen.pressure, S.p) / 2) : R.bodySize / 2;
    const grow = r0 <= 6.4 ? r0 / 3.2 : 2 * Math.sqrt(r0 / 6.4), pool = R.line ? 1.3 * r0 : 1.05 * r0, ahead = R.line ? .5 * r0 : .3 * r0;
    const n = Math.round(a * (1.5 + v * 2.2)), rnd = rng(((R.pen.seed | 0) ^ FF.SALT ^ Math.imul(k + 1, 2654435761) ^ Math.round(S.d * 16)) >>> 0);
    const out = [];
    for (let i = 0; i < n; i++) {
      const ang = dir + (rnd() - .5) * .45 + (rnd() - .5) * (rnd() - .5) * .6;
      const dist = (10 + v * 34) * (.35 + rnd() * .95) * (.6 + a * .8), rad = clamp((.8 + v * .8) * (.4 + rnd() * .9), .7, 6) * grow, at = dist * FF.PROTO + ahead;
      out.push({ x: S.x + Math.cos(ang) * at, y: S.y + Math.sin(ang) * at, along: rad * 1.5, across: rad, angle: ang, at: now + 30 + Math.round(dist / v * 1.6), speck: rnd() < .4 });
    }
    // Ink piles up a little where the nib stops dead; then the drops, nearest first.
    out.sort((p, q) => p.at - q.at);
    this.drops = [{ x: S.x + Math.cos(dir) * ahead, y: S.y + Math.sin(dir) * ahead, along: pool, across: pool, angle: dir, at: now, speck: false }, ...out]; this.landed = 0;
  }
  landing(now) { return this.landed < this.drops.length && this.drops[this.landed].at <= now; }
  land(now) { const out = []; while (this.landing(now)) out.push(this.drops[this.landed++]); return out; }
}
// A drop into buffer buf (the strongest kept), an ellipse along its flight and its speck beyond; wetD takes dist where it lays.
function ffDrop(s, buf, wetD, D, k, dist) {
  const ell = (cx, cy, ra, rb, ang) => {
    const R = Math.ceil(Math.max(ra, rb) + 1), x0 = Math.max(0, Math.floor(cx - R)), x1 = Math.min(s.w - 1, Math.ceil(cx + R)), y0 = Math.max(0, Math.floor(cy - R)), y1 = Math.min(s.h - 1, Math.ceil(cy + R));
    if (x1 < x0 || y1 < y0) return null;
    const ca = Math.cos(ang), sa = Math.sin(ang);
    for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
      const dx = px + .5 - cx, dy = py + .5 - cy, al = (dx * ca + dy * sa) / ra, ac = (-dx * sa + dy * ca) / rb, c = clamp((1 - Math.sqrt(al * al + ac * ac)) * rb + .5) * k, i = py * s.w + px;
      if (c > buf[i]) { buf[i] = c; if (wetD) wetD[i] = dist; }
    }
    return [x0, y0, x1, y1];
  };
  let r = ell(D.x, D.y, D.along, D.across, D.angle);
  if (D.speck) { const q = Math.max(.5, D.across * .35), sp = ell(D.x + Math.cos(D.angle) * D.across * 3, D.y + Math.sin(D.angle) * D.across * 3, q, q, 0); if (sp) r = r ? union(r, sp) : sp; }
  return r;
}
const StrokeFF = Stroke;
Stroke = class extends StrokeFF {
  constructor(surf, B, size, shade, host) {
    super(surf, B, size, shade, host);
    this.ff = this.R.flick > 0 || this.R.fling > 0 ? new FlickFling(this.R) : null;
  }
  // Sample by sample, so the watcher sees each one (kept or not); drops land as the samples' clock reaches them.
  add(samples) {
    if (!this.ff || this.ff.lifted) return super.add(samples);
    let out = null;
    for (const q of samples) {
      const r = super.add([q]);
      if (r) out = out ? union(out, r) : r;
      this.ff.sampled(this.pts, q[0], q[1], q[3]);
      if (this.ff.landing(q[3])) { const d = this.ffLand(q[3]); if (d) out = out ? union(out, d) : d; }
    }
    return out;
  }
  // The drops landing by now, where the stroke's marks are: its line, else its body (Fast-wet's as laid and as shown).
  ffLand(now) {
    let r = null;
    for (const D of this.ff.land(now)) { const b = this.ffLay(D, true); if (b) r = r ? union(r, b) : b; }
    if (!r) return null;
    grow(this.rect, r);
    if (!this.fl) composeStroke(this, r, this.R.life === "dry" ? "preview" : "final");
    return r;
  }
  ffLay(D, live) {
    const R = this.R, s = this.surf;
    if (R.line) { const r = ffDrop(s, s.L, null, D, 1, 0); if (live && R.life === "wet" && s.rawL) ffDrop(s, s.rawL, null, D, 1, 0); return r; }
    if (!R.body) return null;
    const amt = R.body.amount ?? 1;
    if (R.life !== "wet") return ffDrop(s, s.W, null, D, amt, 0);
    ffDrop(s, lazyBuf(s, "rawW"), lazyBuf(s, "wetD", -1), D, amt, this.pts[this.pts.length - 1].d);
    return ffDrop(s, s.D, null, D, amt, 0);
  }
  // A Slow lift lays the line and body again: the drops that landed go back in.
  stampL(buf, final, from, clip, win) {
    let r = super.stampL(buf, final, from, clip, win);
    if (final && this.ff && this.R.line && buf === this.surf.L) for (const D of this.ff.drops.slice(0, this.ff.landed)) { const b = this.ffLay(D, false); if (b) r = union(r, b); }
    return r;
  }
  stampW(buf, final, from) {
    let r = super.stampW(buf, final, from);
    if (final && this.ff && !this.R.line && buf === this.surf.W) for (const D of this.ff.drops.slice(0, this.ff.landed)) { const b = this.ffLay(D, false); if (b) r = union(r, b); }
    return r;
  }
  end() {
    if (this.ff && !this.ff.lifted) {
      const c = this.ff.lift(this.pts);
      if (c) for (let i = 0; i < c.length; i += 3) super.add(c.slice(i, i + 3));
      this.ffLand(Infinity);
    }
    return super.end();
  }
};

// ---------- rolls: rare, from the seed, after every part (the app's ModularStyle.rollFlick, rollFling) ----------
Object.assign(NM_ODDS, { flick: { [WILDCARD]: 12, "Brush pen": 12, "Ink wash": 12, Watercolor: 20 }, fling: { [WILDCARD]: 15, "Dip pen": 15, "Brush pen": 15 } });
Object.assign(NM_SALT, { flick: 0xf11c4, fling: 0xf1196 });
NM.flickRolls = true; NM.flingRolls = true;
function rollFlickFling(style, B, lk, old) {
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i), odds = key => NM_ODDS[key][style] || 0;
  if (NM.flickRolls && !(old && lk.parts.has("tip")) && !lk.dials.has("tip.flick") && odds("flick") && "flick" in B.tip.d && u("flick", 0) < 1 / odds("flick")) B.tip.d.flick = lerp(.35, .85, u("flick", 1));
  if (NM.flingRolls && !(old && lk.parts.has("medium")) && !lk.dials.has("medium.fling") && odds("fling") && "fling" in B.medium.d && u("fling", 0) < 1 / odds("fling")) B.medium.d.fling = lerp(.3, .8, u("fling", 1));
}
const rollNewWet = rollNew;
rollNew = function (style, B, lk, old) { rollNewWet(style, B, lk, old); rollFlickFling(style, B, lk, old); };

// ---------- words (docs/TEXT-STYLE.md): said plainly ----------
const mischiefWords = B => {
  const flick = (B.tip.d.flick || 0) >= .05, fling = (B.medium.d.fling || 0) >= .05;
  return flick && fling ? "Flicks on a quick lift, flings drops on a hard stop." : flick ? "Flicks on a quick lift." : fling ? "Flings drops on a hard stop." : null;
};
const choiceWordsFF = choiceWords;
choiceWords = function (B, part) {
  const w = choiceWordsFF(B, part);
  // Fling after the medium, as Slow's gravity ("Ink, fling"), where the module's line has room; the Tip's has none for Flick.
  if (part !== "medium" || !((B.medium.d.fling || 0) >= .05)) return w;
  const both = w + ", fling";
  return fitsMedium(both) ? both : w;
};
const plainWordsFF = plainWords;
plainWords = function (B) {
  const said = mischiefWords(B), text = plainWordsFF(B);
  if (!said) return text;
  const all = text.split(/(?<=\.)\s+/).filter(Boolean);
  all.splice(all[1] === "Builds up where it lingers." ? 2 : 1, 0, said);
  let t2 = all[0];
  for (let i = 1; i < all.length; i++) { if (i >= 2 && t2.length + 1 + all[i].length > 260) break; t2 += " " + all[i]; }
  return t2;
};

// ---------- checks ----------
function flickChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 900, H = 600, P = PAPER_TONE;
  const mk = (flick, fling, life) => {
    const B = { name: "f", seed: 23, style: "Brush pen", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", life || "none"), hand: freshPart("hand", "steady") };
    B.tip.d.flick = flick; B.medium.d.fling = fling;
    return normBrush(B);
  };
  const line = (x0, x1, y, v) => { const n = Math.ceil((x1 - x0) / (v * 2)); return Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y, p: .7, t: 1000 + 2 * k, tx: NaN, ty: NaN })); };
  const dash = (x0, x1, y, hold) => {
    const o = [];
    for (let i = 0; i <= 26; i++) o.push({ x: x0 + (x1 - x0) * Math.pow(i / 26, 1.7), y, p: .45 + .4 * i / 26, t: 1000 + 4 * i, tx: NaN, ty: NaN });
    for (let k = 1; k * 4 <= hold; k++) o.push({ x: x1 + .3 * Math.sin(k), y: y + .3 * Math.cos(k * 1.3), p: .85, t: 1104 + 4 * k, tx: NaN, ty: NaN });
    return o;
  };
  const draw = (B, pts) => { const s = modSurface(W, H, 7); layStroke(s, B, pts, 12, 1, null); return s; };
  const reach = (s, from) => { let m = from; for (let y = 0; y < H; y++) for (let x = from; x < W; x++) if (s.page[y * W + x] < P - 5) m = Math.max(m, x); return m; };
  const ink = (s, x0, x1) => { let n = 0; for (let y = 0; y < H; y++) for (let x = x0; x <= x1; x++) if (s.page[y * W + x] < P - 5) n++; return n; };
  const same = (a, b) => a.page.every((v, i) => v === b.page[i]);
  // Codes: off, as before; on, appended and read back.
  { const off = mk(0, 0), on = mk(.6, .4), c = encodeM(on), back = readAnyCode(c).b;
    t("Flick and Fling are written only when on and read back (" + c + ")", encodeM(off) !== c && Math.abs(back.tip.d.flick - .6) < 1e-3 && Math.abs(back.medium.d.fling - .4) < 1e-3 && encodeM(back) === c
      && CHOICES.tip.every(ch => ch.dials[ch.dials.length - 1] === "flick") && CHOICES.medium.filter(ch => ch.dials.includes("blend")).every(ch => ch.dials[ch.dials.length - 1] === "fling")); }
  // Flick: a quick lift carries on, a slow one doesn't.
  for (const life of ["none", "wet", "dry"]) {
    const a = draw(mk(.7, 0, life), line(100, 400, 300, 3)), b = draw(mk(0, 0, life), line(100, 400, 300, 3)), far = reach(a, 400);
    t(life + ": a quick lift flicks on to x " + far + " (without, " + reach(b, 400) + ")", far > 520 && far < 620 && reach(b, 400) < 430);
  }
  { const slow = Array.from({ length: 121 }, (_, i) => ({ x: 200 + i, y: 300, p: .7, t: 1000 + 4 * i, tx: NaN, ty: NaN }));
    t("a slow lift doesn't flick", same(draw(mk(1, 0), slow), draw(mk(0, 0), slow))); }
  // Fling: a dead stop throws drops ahead, the same again; a smooth stroke doesn't.
  { const a = draw(mk(0, .8), dash(100, 420, 300, 120)), b = draw(mk(0, 0), dash(100, 420, 300, 120)), c = draw(mk(0, .8), dash(100, 420, 300, 120));
    t("a dead stop flings drops ahead (" + ink(a, 432, W - 1) + " px), the same again", ink(a, 432, W - 1) > 0 && ink(b, 432, W - 1) === 0 && same(a, c));
    t("a smooth quick stroke throws none", same(draw(mk(0, 1), line(100, 400, 300, 3)), draw(mk(0, 0), line(100, 400, 300, 3)))); }
  // Rolls: with them off every roll is as before; on, Flick or Fling at the odds, nothing else changed.
  { const roll = on => { const keep = Math.random, k0 = NM.flickRolls, k1 = NM.flingRolls; Math.random = rng(6161); NM.flickRolls = NM.flingRolls = on;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.flickRolls = k0; NM.flingRolls = k1; } };
    const a = roll(false), b = roll(true); let flicks = 0, flings = 0, odd = 0;
    for (let i = 0; i < a.length; i++) {
      if (encodeM(a[i]) === encodeM(b[i])) continue;
      const c = cloneBrush(b[i]); if ("flick" in c.tip.d) c.tip.d.flick = 0; if ("fling" in c.medium.d) c.medium.d.fling = 0;
      if ((b[i].tip.d.flick || 0) > 0) flicks++; if ((b[i].medium.d.fling || 0) > 0) flings++;
      if (encodeM(normBrush(c)).split(".M")[1] !== encodeM(a[i]).split(".M")[1]) odd++;
    }
    t("rolls: " + flicks + " flick, " + flings + " fling, none else changed", odd === 0 && flicks > 10 && flicks < 90 && flings > 5 && flings < 70); }
  // Words.
  { const B = mk(.6, .6);
    t("words: \"" + plainWords(B) + "\", \"" + choiceWords(B, "medium") + "\"", plainWords(B).includes("Flicks on a quick lift, flings drops on a hard stop.") && choiceWords(B, "medium") === "Ink, fling"
      && !plainWords(mk(0, 0)).includes("Fling")); }
  return out;
}
const selfTestFF = selfTest;
selfTest = function () { const r = selfTestFF(); r.flick = flickChecks(); r.ok = r.ok && r.flick.every(c => c.ok); NM.lastFlick = r.flick; return r; };
// Erasable's check hashes rolls from before Flick and Fling: it runs with their rolls off.
const eraseChecksFF = eraseChecks;
eraseChecks = function () { const k0 = NM.flickRolls, k1 = NM.flingRolls; NM.flickRolls = NM.flingRolls = false; try { return eraseChecksFF(); } finally { NM.flickRolls = k0; NM.flingRolls = k1; } };
if (typeof runChecks === "function") {
  const runChecksFF = runChecks;
  runChecks = function () {
    runChecksFF();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastFlick) return;
      out.textContent += "\n\nFlick and Fling:\n" + NM.lastFlick.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 80);
  };
}

// ===================================================================================================================
// Oil paint (the oil paint spec, docs/archive/superpowers/specs/2026-10-19-oil-paint-design.md; MonoPaint's oil paint mode; the
// app's LoadedPaint): Smudge's Paint Load and Starting Load, Drag's and Smear's last dials (parts.js), whose own paint is
// laid solidly at first and runs out along the stroke, sooner the wider, until the brush only smudges (stroke.js
// oilSpend, every stamper's darkness); and the Filbert, Tip choice 9: a rounded oval that follows the pen's tilt, with
// Paint Load a crescent hollowed the way it moves, so a first touch is a sliver (engine.js stampBody). Every web stroke
// starts at its Starting Load: the app's rub on a grey has no twin here. Here the Filbert, its rare rolls and the checks.
// ===================================================================================================================
CHOICES.tip.push({ k: "filbert", label: "Filbert", dials: ["aspect", "angle", "weight", "taperIn", "taperOut", "dry", "wobble", "soft", "flick"], macro: "aspect", words: ["slim", "round"],
  range: { aspect: [.3, .55] }, def: { aspect: .5, soft: .15 }, blurb: "A rounded oval that follows the pen's tilt. With Paint Load a first touch is a crescent." });
// A variation never swaps to the Filbert (the app's ModularFeatures.seedOnly): only a roll's seed picks it.
const makeVarsOil = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.tip.slice();
  CHOICES.tip = keep.filter(c => c.k !== "filbert" || c.k === B.tip.k);
  try { return makeVarsOil(B, locks); } finally { CHOICES.tip = keep; }
};
// ---------- rolls: rare, from the seed, after every part (the app's ModularStyle.rollFilbert, rollPaintLoad) ----------
Object.assign(NM_ODDS, { filbert: { [WILDCARD]: 30, Oils: 3 }, pload: { Oils: 3 } });
Object.assign(NM_SALT, { filbert: 0xf11be, pload: 0x910ad });
NM.oilRolls = true;
function rollOil(style, B, lk, old) {
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i), odds = key => NM_ODDS[key][style] || 0;
  // An Oils (or Anything goes) round or flat nib becomes a Filbert, .35–.55 wide; an Oils Drag or Smear runs out, Paint Load .35–.75.
  if (NM.oilRolls && !(old && lk.parts.has("tip")) && odds("filbert") && (B.tip.k === "round" || B.tip.k === "flat") && u("filbert", 0) < 1 / odds("filbert")) {
    B.tip = switchChoice("tip", B.tip, "filbert"); B.tip.d.aspect = lerp(.35, .55, u("filbert", 1));
  }
  if (NM.oilRolls && !(old && lk.parts.has("carry")) && !lk.dials.has("carry.pload") && odds("pload") && "pload" in B.carry.d && u("pload", 0) < 1 / odds("pload")) B.carry.d.pload = lerp(.35, .75, u("pload", 1));
}
const rollNewFF = rollNew;
rollNew = function (style, B, lk, old) { rollNewFF(style, B, lk, old); rollOil(style, B, lk, old); };

// ---------- checks ----------
const OIL_PAINT = "oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l";
function oilChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 640, H = 240, P = PAPER_TONE;
  const oil = () => readAnyCode(OIL_PAINT).b;
  const line = (x0, y0, x1, y1, p = .8, tx = NaN, ty = NaN) => { const n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 2.5); return Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p, t: k * 4, tx, ty })); };
  const mean = (s, x0, x1, y0, y1) => { let a = 0, n = 0; for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) { a += s.page[y * W + x]; n++; } return a / n; };
  const reach = (s, y) => { let m = 0; for (let x = 0; x < W; x++) if (s.page[y * W + x] < P - 20) m = x; return m; };
  // Codes: Oil paint as the app writes it; old Drag and Smear codes as before; the Filbert is Tip 9, nothing past it.
  { const olds = ["oils-drag.M90i00000000042_2zz00_2llp600_000_0_0000000000000000000nz0.1vtkv3y", "stump.M50i00000000yat_3eqlj00we_3p6ll00_1nl_1jz_8pthh00x9is00000000nz0.51j8ud",
      "dirty-brush.M60i000000000at_1ulat00770000ee_3qzi00000we_000_1jz_0000000000000000000nz0.20j"];
    const b = oil(), off = cloneBrush(b); off.carry.d.pload = 0;
    t("Oil paint reads and writes as the app's (" + encodeM(b) + "); older Drag and Smear codes as before, Paint Load off",
      encodeM(b) === OIL_PAINT && b.tip.k === "filbert" && Math.abs(b.carry.d.pload - .55) < 1e-3 && b.carry.d.pmin === .25
      && olds.every(c => { const r = readAnyCode(c).b; return encodeM(r) === c && (r.carry.d.pload ?? 0) === 0 && !resolve(r, 60, 1).loaded; })
      && encodeM(normBrush(off)).split("_")[2].length === OIL_PAINT.split("_")[2].length - 4 && CHOICES.tip.findIndex(c => c.k === "filbert") === 9 && !readAnyCode(OIL_PAINT.replace(".M99", ".M9b"))); }
  // Rolls: with them off every roll is as before; on, a Filbert or Paint Load now and then (Oils, Anything goes), nothing else.
  { const roll = on => { const keep = Math.random, k0 = NM.oilRolls; Math.random = rng(7171); NM.oilRolls = on;
      try { return Array.from({ length: 1400 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; NM.oilRolls = k0; } };
    const a = roll(false), b = roll(true); let filberts = 0, loads = 0, odd = 0;
    for (let i = 0; i < a.length; i++) {
      if (encodeM(a[i]) === encodeM(b[i])) continue;
      const c = cloneBrush(b[i]), st = b[i].style;
      if (b[i].tip.k === "filbert") { filberts++; c.tip = cloneBrush(a[i]).tip; }
      if ((b[i].carry.d.pload ?? 0) > 0) { loads++; c.carry.d.pload = 0; }
      if (st !== "Oils" && st !== WILDCARD || (b[i].carry.d.pload ?? 0) > 0 && st !== "Oils" || encodeM(normBrush(c)).split(".M")[1] !== encodeM(a[i]).split(".M")[1]) odd++;
    }
    t("rolls: " + filberts + " Filberts, " + loads + " run out, none else changed", odd === 0 && filberts > 10 && loads > 10 && filberts < 140 && loads < 140); }
  // A loaded stroke runs out along its length, sooner pressed wider; past it, on paper, nothing.
  { const draw = (p, B = oil()) => { const s = modSurface(W, H, 7); layStroke(s, B, line(20, 120, 620, 120, p), 40, 1, null); return s; };
    const s = draw(.8), firm = draw(1), light = draw(.35), end = reach(s, 120);
    t("Oil paint lays solidly at first (" + mean(s, 25, 50, 115, 125).toFixed(0) + "), runs out by x " + end + " (firm " + reach(firm, 120) + ", light " + reach(light, 120) + "), nothing past it",
      mean(s, 25, 50, 115, 125) < 80 && end > 60 && end < 400 && mean(s, 560, 620, 100, 140) > P - .5 && reach(firm, 120) < reach(light, 120)); }
  // Empty (Starting Load 0), over paper it lays nothing.
  { const B = oil(); B.carry.d.pmin = 0; const s = modSurface(W, H, 7); layStroke(s, B, line(20, 120, 620, 120), 60, 1, null);
    t("an empty brush lays nothing on paper", mean(s, 0, W - 1, 0, H - 1) > P - .01); }
  // The Filbert: a tap leaning right is a crescent with Paint Load, its oval without.
  { const tap = B => { const s = modSurface(W, H, 7); layStroke(s, B, [{ x: 120, y: 120, p: .8, t: 0, tx: 40, ty: 0 }], 80, 1, null); let n = 0; for (const v of s.page) if (v < 128) n++; return n; };
    const on = oil(); on.carry.d.pmin = 1; const off = oil(); off.carry.d.pload = 0;
    const a = tap(on), b = tap(off);
    t("a Filbert's first touch: a crescent with Paint Load (" + a + " px), its oval without (" + b + " px)", a > 0 && a < .6 * b && resolve(on, 80, 1).body.crescent && !resolve(off, 80, 1).body.crescent); }
  // Words.
  { const B = oil();
    t("words: \"" + plainWords(B) + "\", \"" + captionItems(B).join(", ") + "\"", plainWords(B) === "Oil paint, filbert, on canvas. Runs out of paint, then only smudges. Rub a grey to load. Pressure widens."
      && captionItems(B).join(", ") === "Oils, filbert, runs out" && plainNameWords(B)[2] === "filbert" && ["oily", "loaded"].includes(plainNameWords(B)[1])); }
  return out;
}
const selfTestOil = selfTest;
selfTest = function () { const r = selfTestOil(); r.oil = oilChecks(); r.ok = r.ok && r.oil.every(c => c.ok); NM.lastOil = r.oil; return r; };
if (typeof runChecks === "function") {
  const runChecksOil = runChecks;
  runChecks = function () {
    runChecksOil();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastOil) return;
      out.textContent += "\n\nOil paint:\n" + NM.lastOil.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 100);
  };
}

// ===================================================================================================================
// Fading and Lemon Juice (the mischief modules spec, Stage 3; mischief-lab.html's dab3, shade3 and tick3; the app's
// FadingInk): Drying choices 5 and 6, laid as Quick lays them, then the mark changes by the clock. Fading fades away
// unevenly, thin parts and the paper's damp patches first, to nothing; Lemon Juice goes on as a faint trace and darkens
// in unevenly over its time, then is an ordinary dried mark. A mark is the page as the stroke found it and as it laid
// it, with when; each pixel shows a share of that change by its age (the app's FadingInk.share), stepped as coarsely as
// e-ink looks fine (Lemon Juice once a second, Fading a tenth of its time, 2 to 5 s). The test area keeps its marks and
// steps them on a timer; a sample tile, and Brush Studio's pages, show the stroke as laid. Here the choices, rolls,
// words, the test area's timer and the checks.
// ===================================================================================================================
Object.assign(DIALS.life, {
  fadeIn: { label: "Fades In", min: 10, max: 120, def: 20, fmt: v => Math.round(v) + " s" },
  dryIn: { label: "Dries In", min: 10, max: 600, def: 300, fmt: v => { const s = Math.round(v); return s < 60 ? s + " s" : Math.floor(s / 60) + ":" + String(s % 60).padStart(2, "0"); } }
});
CHOICES.life.push(
  { k: "fade", label: "Fading", dials: ["fadeIn"], macro: "fadeIn", words: ["fast", "slow"], blurb: "Fades away unevenly, thin parts first, until it's gone." },
  { k: "lemon", label: "Lemon Juice", dials: ["dryIn"], macro: "dryIn", words: ["fast", "slow"], blurb: "Goes on as a faint trace and darkens in unevenly as it dries." });
const FADES = new Set(["fade", "lemon"]);
// The clock (wall time, ms): the checks set their own.
const FADE_CLOCK = { now: () => Date.now() };
const FADE_TRACE = 26, FADE_MOST = 1.25, LEMON_MOST = 1.3;
const fadeTime = B => fmtDial("life", B.life.k === "lemon" ? "dryIn" : "fadeIn", B.life.d[B.life.k === "lemon" ? "dryIn" : "fadeIn"]);

// ---------- resolve: Quick, and what the clock needs ----------
const resolveFade = resolve;
resolve = function (B, size, shade) {
  const k = B.life.k;
  if (!FADES.has(k)) return resolveFade(B, size, shade);
  const A = cloneBrush(B);
  A.life = freshPart("life", "wet");
  const R = resolveFade(A, size, shade);
  R.fade = { lemon: k === "lemon", ms: Math.max(1, Math.round(1000 * (k === "lemon" ? B.life.d.dryIn ?? 300 : B.life.d.fadeIn ?? 20))) };
  return R;
};

// ---------- the clock's shares (FadingInk.share, stepMs, mottle) ----------
function fadeShare(lemon, ms, dark, m, age) {
  if (lemon) { const u = clamp(age / (ms * (.7 + .6 * m))); return u >= 1 ? 1 : (FADE_TRACE + (255 - FADE_TRACE) * u * u * (3 - 2 * u)) / 255; }
  const left = 1 - age / (ms * (.35 + .65 * dark) * (.75 + .5 * m));
  return left <= 0 ? 0 : Math.min(1, left * 2.2);
}
const fadeStepMs = (lemon, ms) => lemon ? Math.max(1000, Math.min(15000, ms / 20)) : Math.max(2000, Math.min(5000, ms / 10));
const fadeEnd = M => M.born + Math.ceil(M.ms * (M.lemon ? LEMON_MOST : FADE_MOST));
const fadeNext = M => M.shown < 0 ? M.born : Math.min(fadeEnd(M), M.shown + fadeStepMs(M.lemon, M.ms));
// The mottle fixed to the page: its soft 16 px blobs and a pixel of grit (the prototype's mott: .7 and .3).
const fadeMottle = (s, x, y) => .7 * s.blob[y * s.w + x] + .3 * hash01(hashInts(0x2b, x, y), 0);
// What pixel i of mark M shows at clock time t (-1: as laid).
function fadeValue(s, M, j, t) {
  const b = M.before[j], a = M.after[j];
  if (t < 0 || b === a) return a;
  const x = M.x0 + j % M.rw, y = M.y0 + (j / M.rw | 0);
  return b + (a - b) * fadeShare(M.lemon, M.ms, Math.abs(a - b) / PAPER_TONE, fadeMottle(s, x, y), t - M.born);
}
// Mark M steps to clock time t: each pixel still showing what it last showed shows its share now. Returns the rectangle changed, or null.
function fadeStep(s, M, t) {
  let r = null;
  for (let j = 0; j < M.before.length; j++) {
    if (M.before[j] === M.after[j]) continue;
    const x = M.x0 + j % M.rw, y = M.y0 + (j / M.rw | 0), i = y * s.w + x, was = fadeValue(s, M, j, M.shown), next = fadeValue(s, M, j, t);
    if (was === next || Math.abs(s.page[i] - was) > 1e-3) continue;
    s.page[i] = next;
    r = r ? union(r, [x, y, x, y]) : [x, y, x, y];
  }
  M.shown = t;
  return r;
}
// At lift: the stroke's rectangle as found and as laid becomes a mark (fresh ink over a mark of its kind still changing
// changes from what lay under that one); with the test area's host it is kept and ticks, else the stroke stays as laid.
function fadeLift(st, r, found) {
  const s = st.surf, F = st.R.fade, rw = r[2] - r[0] + 1, rh = r[3] - r[1] + 1, before = new Float32Array(rw * rh), after = new Float32Array(rw * rh);
  for (let y = 0; y < rh; y++) for (let x = 0; x < rw; x++) { const i = (y + r[1]) * s.w + x + r[0]; before[y * rw + x] = found[i]; after[y * rw + x] = s.page[i]; }
  const M = { lemon: F.lemon, ms: F.ms, born: FADE_CLOCK.now(), shown: -1, x0: r[0], y0: r[1], rw, rh, before, after };
  for (const O of s.fades || []) {
    if (O.lemon !== M.lemon) continue;
    for (let j = 0; j < before.length; j++) {
      const x = r[0] + j % rw, y = r[1] + (j / rw | 0), ox = x - O.x0, oy = y - O.y0;
      if (ox < 0 || oy < 0 || ox >= O.rw || oy >= O.rh) continue;
      const k = oy * O.rw + ox;
      if (O.before[k] !== O.after[k] && Math.abs(fadeValue(s, O, k, O.shown) - before[j]) < 1e-3) before[j] = O.before[k];
    }
  }
  (s.fades ??= []).push(M);
  return fadeStep(s, M, M.born);
}
// Every mark due by t steps there; those done leave the list. Returns the rectangle changed, or null.
function fadeAll(s, t) {
  let r = null;
  for (const M of s.fades || []) if (fadeNext(M) <= t) { const d = fadeStep(s, M, Math.min(t, fadeEnd(M))); if (d) r = r ? union(r, d) : d; }
  if (s.fades) s.fades = s.fades.filter(M => M.shown < fadeEnd(M));
  return r;
}
// The test area's timer: the next mark due, never while the pen is down; nothing runs without marks.
function fadeTicker(s) {
  if (s.fadeTicking) return;
  s.fadeTicking = true;
  const go = () => {
    if (TEST.surf !== s || !(s.fades && s.fades.length)) { s.fadeTicking = false; return; }
    if (!TEST.live) { const r = fadeAll(s, FADE_CLOCK.now()); if (r) showPage(TEST.ctx, s, r); }
    const due = s.fades.length ? Math.min(...s.fades.map(fadeNext)) : 0;
    setTimeout(go, Math.max(50, Math.min(1000, due - FADE_CLOCK.now())));
  };
  setTimeout(go, 50);
}
const StrokeFade = Stroke;
Stroke = class extends StrokeFade {
  end() {
    const s = this.surf, F = this.R.fade, live = F && typeof HOST !== "undefined" && this.host === HOST, keeps = F && (live || (this.host && this.host.fades === true));
    const found = keeps ? Float32Array.from(s.base) : null, r = super.end();
    if (!keeps || !r) return r;
    const d = fadeLift(this, r, found);
    if (live) fadeTicker(s);
    return d ? union(r, d) : r;
  }
};
// A clear page drops every mark.
const blankPageFade = blankPage;
blankPage = function (s) { blankPageFade(s); s.fades = []; };
// Variations never swap to them (the app's ModularFeatures.seedOnly).
const makeVarsFade = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.life.slice();
  CHOICES.life = keep.filter(c => !FADES.has(c.k) || c.k === B.life.k);
  try { return makeVarsFade(B, locks); } finally { CHOICES.life = keep; }
};

// ---------- rolls: rare, from the seed, after every part (the app's ModularStyle.rollFade) ----------
Object.assign(NM_ODDS, { fade: { [WILDCARD]: 40 }, lemon: { [WILDCARD]: 40, "Ink wash": 60 } });
Object.assign(NM_SALT, { fade: 0xfade5, lemon: 0x1e307 });
NM.fadeRolls = true; NM.lemonRolls = true;
function rollFade(style, B, lk, old) {
  if (old && lk.parts.has("life") || B.life.k === "float" || B.life.k === "halo") return;
  const u = (key, i) => hash01((B.seed ^ NM_SALT[key]) >>> 0, i), odds = key => NM_ODDS[key][style] || 0;
  if (NM.fadeRolls && odds("fade") && u("fade", 0) < 1 / odds("fade")) B.life = { k: "fade", d: { fadeIn: Math.round(lerp(15, 60, u("fade", 1))) } };
  else if (NM.lemonRolls && odds("lemon") && u("lemon", 0) < 1 / odds("lemon")) B.life = { k: "lemon", d: { dryIn: 30 * Math.round(lerp(6, 14, u("lemon", 1))) } };
}
const rollNewOil = rollNew;
rollNew = function (style, B, lk, old) { rollNewOil(style, B, lk, old); rollFade(style, B, lk, old); };

// ---------- words (docs/TEXT-STYLE.md): said plainly ----------
const choiceWordsFade = choiceWords;
choiceWords = function (B, part) {
  // Fading says how soon it goes ("Fading 20 s"); Lemon Juice's time would pass the module's line.
  if (part === "life" && B.life.k === "fade") return "Fading " + fadeTime(B);
  if (part === "life" && B.life.k === "lemon") return "Lemon juice";
  return choiceWordsFade(B, part);
};
const captionItemsFade = captionItems;
captionItems = function (B) {
  const items = captionItemsFade(B);
  if (!FADES.has(B.life.k)) return items;
  // After the style and the new choices' tags (resist, Blend, Print Shading, a pattern tip), as Mold's: "Anything goes, fading".
  const mk = B.medium.k, bl = mk === "resist" || mk === "tone" ? 0 : B.medium.d.blend | 0, sc = mk === "tone" ? 0 : B.medium.d.screen | 0;
  items.splice(1 + (mk === "resist" || mk === "tone" ? 1 : 0) + (bl ? 1 : 0) + (sc ? 1 : 0) + (PATTERN_TIPS.has(B.tip.k) ? 1 : 0), 0, B.life.k === "fade" ? "fading" : "lemon juice");
  return items;
};
const plainWordsFade = plainWords;
plainWords = function (B) {
  const text = plainWordsFade(B);
  if (!FADES.has(B.life.k)) return text;
  // In the drying's place, before how it answers the hand.
  const said = B.life.k === "fade" ? "Fades away in " + fadeTime(B) + "." : "Darkens in as it dries.", hw = handWords(B, 2), control = hw !== "steady" ? cap1(hw) + "." : null;
  const all = text.split(/(?<=\.)\s+/).filter(Boolean), at = control && all[all.length - 1] === control ? all.length - 1 : all.length;
  all.splice(at, 0, said);
  let t2 = all[0];
  for (let i = 1; i < all.length; i++) { if (i >= 2 && t2.length + 1 + all[i].length > 260) break; t2 += " " + all[i]; }
  return t2;
};

// ---------- checks ----------
function fadeChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 400, H = 240, P = PAPER_TONE, keep = FADE_CLOCK.now;
  const mk = (life, d) => {
    const B = { name: "f", seed: 23, style: "Brush pen", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", life), hand: freshPart("hand", "darker") };
    Object.assign(B.life.d, d || {});
    return normBrush(B);
  };
  const line = () => Array.from({ length: 61 }, (_, i) => ({ x: 40 + 320 * i / 60, y: 120 + 30 * Math.sin(i / 9), p: .3 + .6 * i / 60, t: 1000 + 8 * i, tx: NaN, ty: NaN }));
  const dark = s => { let n = 0; for (const v of s.page) n += P - v; return n; };
  let clock = 1e6; FADE_CLOCK.now = () => clock;
  try {
    // Codes: Drying 5 and 6, written and read back; every older code as before.
    { const f = mk("fade", { fadeIn: 45 }), l = mk("lemon", { dryIn: 150 }), cf = encodeM(f), cl = encodeM(l), bf = readAnyCode(cf).b, bl = readAnyCode(cl).b;
      t("Fading and Lemon Juice are Drying 5 and 6, read back (" + cf.split("_")[4] + ", " + cl.split("_")[4] + ")", cf.split("_")[4][0] === "5" && cl.split("_")[4][0] === "6"
        && encodeM(bf) === cf && encodeM(bl) === cl && Math.abs(bf.life.d.fadeIn - 45) < .1 && Math.abs(bl.life.d.dryIn - 150) < .5
        && CHOICES.life.findIndex(c => c.k === "fade") === 5 && CHOICES.life.findIndex(c => c.k === "lemon") === 6); }
    // Rolls: off, every roll as before; on, Fading or Lemon Juice now and then, nothing else changed.
    { const roll = on => { const k = Math.random, k0 = NM.fadeRolls, k1 = NM.lemonRolls; Math.random = rng(8181); NM.fadeRolls = NM.lemonRolls = on;
        try { return Array.from({ length: 2800 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = k; NM.fadeRolls = k0; NM.lemonRolls = k1; } };
      const a = roll(false), b = roll(true); let fades = 0, lemons = 0, odd = 0;
      for (let i = 0; i < a.length; i++) {
        if (encodeM(a[i]) === encodeM(b[i])) continue;
        if (b[i].life.k === "fade") fades++; else if (b[i].life.k === "lemon") lemons++; else { odd++; continue; }
        const c = cloneBrush(b[i]); c.life = cloneBrush(a[i]).life;
        if (brushKey(normBrush(c)) !== brushKey(a[i]) || (b[i].life.k === "fade" && b[i].style !== WILDCARD) || (b[i].life.k === "lemon" && b[i].style !== WILDCARD && b[i].style !== "Ink wash")) odd++;
      }
      t("rolls: " + fades + " fading, " + lemons + " lemon juice, none else changed", odd === 0 && fades > 0 && lemons > 0 && fades + lemons < 40); }
    // Words.
    { const f = mk("fade"), l = mk("lemon");
      t("words: \"" + plainWords(f) + "\", \"" + plainWords(l) + "\", " + captionItems(f).join(", ") + "; " + choiceWords(f, "life") + ", " + choiceWords(l, "life"),
        plainWords(f).includes("Fades away in 20 s.") && plainWords(l).includes("Darkens in as it dries.") && captionItems(f).includes("fading") && captionItems(l).includes("lemon juice")
        && choiceWords(f, "life") === "Fading 20 s" && choiceWords(l, "life") === "Lemon juice" && ["fading", "ghostly"].includes(plainNameWords(f)[1]) && ["secret", "sour"].includes(plainNameWords(l)[1])); }
    // The clock: Fading thins out to nothing, thin parts first; Lemon Juice starts faint and ends as Quick lays it.
    { const host = { fades: true }, s = modSurface(W, H, 7), q = modSurface(W, H, 7);
      layStroke(s, mk("fade"), line(), 14, 1, host); layStroke(q, mk("wet"), line(), 14, 1, null);
      const start = dark(s), laid = Float32Array.from(s.page);
      fadeAll(s, clock + 12000);
      let faint = 0, faintGone = 0, core = 0, coreGone = 0;
      for (let i = 0; i < laid.length; i++) { if (laid[i] >= P - 1) continue; const gone = s.page[i] >= P - 1; if (laid[i] > P * .65) { faint++; if (gone) faintGone++; } else if (laid[i] < P * .25) { core++; if (gone) coreGone++; } }
      fadeAll(s, clock + 26000);
      t("Fading lays as Quick (" + start.toFixed(0) + "), thin parts go first (at 12 s " + faintGone + " of " + faint + " faint px, " + coreGone + " of " + core + " dark), and reaches nothing",
        Math.abs(start - dark(q)) < 1e-6 && faint > 20 && core > 20 && faintGone / faint > coreGone / core + .2 && dark(s) < 1e-6 && !s.fades.length);
      const l = modSurface(W, H, 7); layStroke(l, mk("lemon"), line(), 14, 1, host);
      const trace = dark(l);
      fadeAll(l, clock + 2000); const mid = dark(l);
      fadeAll(l, clock + 6000);
      t("Lemon Juice goes on faint (" + (trace / dark(q) * 100).toFixed(0) + "%), darkens in (" + (mid / dark(q) * 100).toFixed(0) + "% at 2 s) and ends as Quick lays it",
        trace > dark(q) * .06 && trace < dark(q) * .16 && mid > trace && mid < dark(q) && l.page.every((v, i) => Math.abs(v - q.page[i]) < 1e-4) && !l.fades.length);
      const sample = modSurface(W, H, 7); layStroke(sample, mk("lemon"), line(), 14, 1, null);
      t("a sample tile shows it as laid, nothing ticking", !(sample.fades && sample.fades.length) && sample.page.every((v, i) => Math.abs(v - q.page[i]) < 1e-4)); }
  } finally { FADE_CLOCK.now = keep; }
  return out;
}
const selfTestFade = selfTest;
selfTest = function () { const r = selfTestFade(); r.fade = fadeChecks(); r.ok = r.ok && r.fade.every(c => c.ok); NM.lastFade = r.fade; return r; };
if (typeof runChecks === "function") {
  const runChecksFade = runChecks;
  runChecks = function () {
    runChecksFade();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastFade) return;
      out.textContent += "\n\nFading and Lemon Juice:\n" + NM.lastFade.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 120);
  };
}

// ===================================================================================================================
// Stamp (mischief-lab-2.html's second pad; the owner: "have it function like a regular brush … just with a potato stamp
// nib … so then the stamp and nib are the same thing"; the app's StampNib and StampStamper): Tip choice 10, a nib cut
// from the page by lasso, printed every Spacing along the line (close, a nib trail; apart, separate prints; a tap one
// print), turned with the line or not (Turns), potato-stamp inked: blotchy, lighter each print by Ink Fade until the pen
// rests about a second, darker with pressure. The medium lays it as any mark. Its nib is kept beside the code (NM.nibs
// by its id, the app's nib store; here localStorage too), the code carrying the id after the six (parts.js nibSeg). A
// Stamp whose nib isn't here prints this lab's default star (the app's prints round). Never rolled: it needs a cut.
// ===================================================================================================================
Object.assign(DIALS.tip, {
  spacing: { label: "Spacing", min: .05, max: 1.5, def: .12, fmt: v => Math.round(v * 100) + "%" },
  inkFade: { label: "Ink Fade", min: 0, max: .3, def: .22, fmt: v => v < .005 ? "None" : v > .295 ? "Strong" : Math.round(v * 100) + "%" }
});
CHOICES.tip.push({ k: "stamp", label: "Stamp", dials: ["weight", "spacing", "headTurn", "inkFade", "flick"], macro: "spacing", words: ["trail", "prints"],
  labels: { headTurn: "Turns" }, opts: { headTurn: ["On", "Off"] }, def: { headTurn: 0 }, blurb: "Stamps a shape cut from the page." });
PATTERN_TIPS.add("stamp");
const STAMP = { SIZE: 132, REST_MS: 1000, FLOOR: .25, TURN: .35, JIT: .04, MOST: 256, INK: .05 };
// A Stamp's Medium is its own in every style (the app's ModularSizes.STAMP).
const styleSizeSt = styleSize;
styleSize = function (name, B) { return B && B.tip && B.tip.k === "stamp" ? STAMP.SIZE : styleSizeSt(name, B); };
// A variation never swaps to the Stamp (the app's ModularFeatures.seedOnly), and no roll picks it.
const makeVarsSt = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.tip.slice();
  CHOICES.tip = keep.filter(c => c.k !== "stamp" || c.k === B.tip.k);
  try { return makeVarsSt(B, locks); } finally { CHOICES.tip = keep; }
};

// ---------- nibs: { w, h, a (coverage 0–255), id }, the id a hash of its size and pixels (the app's StampNib) ----------
NM.nibs = new Map();
function makeNib(w, h, a) {
  let id = 0x811c9dc5;
  id = Math.imul(id ^ w, 16777619); id = Math.imul(id ^ h, 16777619);
  for (let i = 0; i < a.length; i++) id = Math.imul(id ^ a[i], 16777619);
  return { w, h, a, id: (id >>> 0) || 1 };
}
function inLasso(x, y, poly) {
  let inside = false;
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) { const [xi, yi] = poly[i], [xj, yj] = poly[j]; if ((yi > y) !== (yj > y) && x < xj + (y - yj) / (yi - yj) * (xi - xj)) inside = !inside; }
  return inside;
}
// The default star: a solid five-pointed star 48 px across (mischief-lab-2.html's first stamp), each pixel 4 × 4 samples.
const STAR_NIB = (() => {
  const d = 48, a = new Uint8Array(d * d), poly = [];
  for (let k = 0; k < 10; k++) { const t = -Math.PI / 2 + k * Math.PI / 5, r = (k % 2 ? .45 : 1) * (d / 2 - .5); poly.push([d / 2 + r * Math.cos(t), d / 2 + r * Math.sin(t)]); }
  for (let y = 0; y < d; y++) for (let x = 0; x < d; x++) { let n = 0; for (let j = 0; j < 4; j++) for (let i = 0; i < 4; i++) if (inLasso(x + (i + .5) / 4, y + (j + .5) / 4, poly)) n++; a[y * d + x] = Math.round(255 * n / 16); }
  return makeNib(d, d, a);
})();
const NIB_STORE = "inkwash.modlab.nib.";
function keepNib(nib) {
  NM.nibs.set(nib.id, nib);
  try { localStorage.setItem(NIB_STORE + nib.id.toString(36), JSON.stringify({ w: nib.w, h: nib.h, a: Array.from(nib.a) })); } catch (e) { /* storage off: kept until the page closes */ }
  return nib.id;
}
// Nib id's pixels, or null: kept here, or in localStorage from an earlier visit.
function nibOf(id) {
  if (!id) return null;
  let n = NM.nibs.get(id >>> 0);
  if (!n) try { const o = JSON.parse(localStorage.getItem(NIB_STORE + (id >>> 0).toString(36)) || "null"); if (o) { n = makeNib(o.w, o.h, Uint8Array.from(o.a)); if (n.id !== id >>> 0) n = null; else NM.nibs.set(n.id, n); } } catch (e) { n = null; }
  return n || null;
}
// cut2 (the app's StampNib.cut): the ink inside the lasso pts ([x, y] page px, closed back to its start; even-odd) on
// surface s, trimmed to the pixels with ink, scaled down to 256 on its longer side if bigger; null for nothing to cut.
function cutNib(s, pts) {
  if (pts.length < 3) return null;
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]), P = PAPER_TONE;
  const x0 = Math.max(0, Math.floor(Math.min(...xs))), x1 = Math.min(s.w - 1, Math.ceil(Math.max(...xs))), y0 = Math.max(0, Math.floor(Math.min(...ys))), y1 = Math.min(s.h - 1, Math.ceil(Math.max(...ys)));
  if (x1 < x0 || y1 < y0) return null;
  const bw = x1 - x0 + 1, ink = new Float32Array(bw * (y1 - y0 + 1));
  let tx0 = Infinity, ty0 = Infinity, tx1 = -1, ty1 = -1;
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
    if (!inLasso(x + .5, y + .5, pts)) continue;
    const v = clamp((P - s.page[y * s.w + x]) / P);
    if (!(v > STAMP.INK)) continue;
    ink[(y - y0) * bw + x - x0] = v;
    tx0 = Math.min(tx0, x); tx1 = Math.max(tx1, x); ty0 = Math.min(ty0, y); ty1 = Math.max(ty1, y);
  }
  if (tx1 < 0) return null;
  const w = tx1 - tx0 + 1, h = ty1 - ty0 + 1, k = Math.max(1, Math.max(w, h) / STAMP.MOST), sw = Math.max(1, Math.round(w / k)), sh = Math.max(1, Math.round(h / k)), a = new Uint8Array(sw * sh);
  for (let y = 0; y < sh; y++) for (let x = 0; x < sw; x++) {
    const fx0 = Math.floor(x * k), fx1 = Math.max(fx0 + 1, Math.min(w, Math.floor((x + 1) * k))), fy0 = Math.floor(y * k), fy1 = Math.max(fy0 + 1, Math.min(h, Math.floor((y + 1) * k)));
    let sum = 0;
    for (let yy = fy0; yy < fy1; yy++) for (let xx = fx0; xx < fx1; xx++) sum += ink[(ty0 - y0 + yy) * bw + tx0 - x0 + xx];
    a[y * sw + x] = Math.round(255 * sum / ((fx1 - fx0) * (fy1 - fy0)));
  }
  return makeNib(sw, sh, a);
}
const nibAt = (n, x, y) => x < 0 || y < 0 || x >= n.w || y >= n.h ? 0 : n.a[y * n.w + x] / 255;
function nibSample(n, u, v) {
  const x = Math.floor(u), y = Math.floor(v), fx = u - x, fy = v - y;
  return (nibAt(n, x, y) * (1 - fx) + nibAt(n, x + 1, y) * fx) * (1 - fy) + (nibAt(n, x, y + 1) * (1 - fx) + nibAt(n, x + 1, y + 1) * fx) * fy;
}
function stampNoise(x, y, seed) {
  const ix = Math.floor(x), iy = Math.floor(y), fx = smoothstep(0, 1, x - ix), fy = smoothstep(0, 1, y - iy), c = (i, j) => hash01(hashInts(seed, i, j), 0);
  const top = c(ix, iy) + (c(ix + 1, iy) - c(ix, iy)) * fx;
  return top + (c(ix, iy + 1) + (c(ix + 1, iy + 1) - c(ix, iy + 1)) * fx - top) * fy;
}

// ---------- resolve: the pattern's stamp fields ----------
const resolveSt = resolve;
resolve = function (B, size, shade) {
  const R = resolveSt(B, size, shade);
  if (B.tip.k === "stamp" && R.pat) {
    const mk = B.medium.k, lineMedium = mk === "ink" || mk === "charcoal" || mk === "graphite" || mk === "resist" || mk === "tone";
    Object.assign(R.pat, { size: lineMedium ? R.inkSize : R.washSize, spacing: B.tip.d.spacing ?? .12, fade: B.tip.d.inkFade ?? .22, turns: ((B.tip.d.headTurn ?? 0) | 0) === 0, nib: nibOf(B.nib) || STAR_NIB });
  }
  return R;
};

// ---------- the stamper (print2 and place2; the app's StampStamper) ----------
function stampPrint(s, buf, nib, cx, cy, size, ang, amount, dk, seed, grit, d, rect) {
  if (!(size > 0) || !(dk > 0)) return;
  const w = s.w, k = size / Math.max(nib.w, nib.h), ca = Math.cos(ang), sa = Math.sin(ang), R = Math.hypot(nib.w, nib.h) * k / 2 + 2;
  const x0 = Math.max(0, Math.floor(cx - R)), x1 = Math.min(w - 1, Math.ceil(cx + R)), y0 = Math.max(0, Math.floor(cy - R)), y1 = Math.min(s.h - 1, Math.ceil(cy + R));
  if (x1 < x0 || y1 < y0) return;
  const cell = Math.max(2, Math.max(nib.w, nib.h) / 12), hw = nib.w / 2 - .5, hh = nib.h / 2 - .5;
  for (let py = y0; py <= y1; py++) for (let px = x0; px <= x1; px++) {
    const dx = px + .5 - cx, dy = py + .5 - cy, u = (dx * ca + dy * sa) / k + hw, q = (-dx * sa + dy * ca) / k + hh;
    if (u < -1 || q < -1 || u > nib.w || q > nib.h) continue;
    const cov = nibSample(nib, u, q);
    if (!(cov > .01)) continue;
    const n = .65 * stampNoise(u / cell, q / cell, seed) + .35 * hash01(hashInts(grit, Math.floor(u), Math.floor(q)), 0);
    const c = cov * clamp((amount * 1.1 - (1 - n)) * 3 + .1) * dk, i = py * w + px;
    if (c > buf[i]) { buf[i] = c; if (WET) WET[i] = d; }
  }
  grow(rect, [x0, y0, x1, y1]);
}
function stampStamp(st, buf, from, M) {
  const P = st.R.pat, s = st.surf, pts = st.pts, rect = [s.w, s.h, -1, -1], size = P.size;
  if (!pts.length) return rect;
  if (M.load === undefined) {
    M.seed = hashInts(P.seed ^ 0x57a3b, Math.round(pts[0].x * 4), Math.round(pts[0].y * 4));
    M.jit = (hash01(M.seed, 1) * 2 - 1) * STAMP.JIT; M.next = 0; M.prints = 0; M.dir = NaN;
    // The surface's last stamp left its ink; a rest of over a second since its last print inks it afresh.
    const left = s.stampInk, t0 = pts[0].t;
    M.load = !left || !(t0 - left.last <= STAMP.REST_MS) ? 1 : Math.max(STAMP.FLOOR, left.load);
  }
  const gap = Math.max(1, P.spacing * size);
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d, ux = seg > 0 ? (p1.x - p0.x) / seg : 1, uy = seg > 0 ? (p1.y - p0.y) / seg : 0;
    while (M.next <= p1.d) {
      const t = seg > 0 ? clamp((M.next - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t);
      const press = M.prints === 0;
      if (!press && seg > 0) {
        const head = Math.atan2(uy, ux);
        if (Number.isNaN(M.dir)) M.dir = head;
        let dd = head - M.dir; while (dd > Math.PI) dd -= 2 * Math.PI; while (dd < -Math.PI) dd += 2 * Math.PI;
        M.dir += dd * STAMP.TURN;
      }
      const ang = P.turns && !Number.isNaN(M.dir) ? M.dir : M.jit;
      const sz = size * (P.ps ? lerp(1, .35 + .65 * pr, P.ps) : 1) * (pts.flick ? flickWidth(pts.flick, M.next) : 1);
      const amount = M.load * (.55 + .6 * pr) * (p1.own === undefined ? 1 : lerp(p0.own, p1.own, t)), dk = handDark(P.dark, pr, v, 0);
      const seed = P.spacing >= 1 ? hashInts(M.seed, M.prints, 7) : M.seed, grit = hashInts(M.seed ^ 0x9e37, M.prints, 3);
      stampPrint(s, buf, P.nib, x, y, sz, ang, amount, dk, seed, grit, M.next, rect);
      M.load = Math.max(STAMP.FLOOR, M.load * Math.pow(1 - P.fade, press ? 1 : Math.min(1, P.spacing * P.spacing)));
      M.last = lerp(p0.t, p1.t, t); M.prints++;
      M.next += gap;
    }
  }
  // What the stamp has left goes with the surface, for its next stroke.
  if (M.prints) s.stampInk = { load: M.load, last: M.last };
  return rect;
}
const stampPatternSt = stampPattern;
stampPattern = function (st, buf, size, from, which) {
  return st.R.pat.kind === "stamp" ? stampStamp(st, buf, from, st["nm" + which] ??= {}) : stampPatternSt(st, buf, size, from, which);
};

// ---------- words: "Ink stamp. Stamps a shape cut from the page. Fades as it prints." ----------
const plainWordsSt = plainWords;
plainWords = function (B) {
  if (B.tip.k !== "stamp") return plainWordsSt(B);
  const sent = plainWordsSt(B).split(/(?<=\.)\s+/).filter(Boolean), first = sent.shift() || "";
  // The first sentence names it by what it lays (a stipple's way): "Ink stamp.", "Wash stamp on felt paper."
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(first), tail = m && m[2] ? m[2] : "";
  const out = [NM_BASE[B.medium.k] ? NM_BASE[B.medium.k] + " stamp" + tail + "." : first, "Stamps a shape cut from the page."];
  if ((B.tip.d.inkFade ?? .22) >= .005) out.push("Fades as it prints.");
  let t = out.join(" ");
  for (const x of sent) { if (t.length + 1 + x.length > 260) break; t += " " + x; }
  return t;
};
const captionItemsSt = captionItems;
captionItems = function (B) {
  if (B.tip.k !== "stamp") return captionItemsSt(B);
  const items = captionItemsSt(B).filter(t => t !== "stamp");
  items.splice(1, 0, "stamp");
  return items;
};

// ---------- checks ----------
function stampChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 640, H = 240, P = PAPER_TONE;
  const mk = (d, medium) => normBrush({ name: "potato", seed: 17, style: "Fineliner", tip: Object.assign(freshPart("tip", "stamp"), { d: Object.assign(freshPart("tip", "stamp").d, d || {}) }),
    medium: freshPart("medium", medium || "ink"), carry: freshPart("carry", "off"), paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: freshPart("hand", "steady") });
  const line = (x0, x1, y, t0) => { const n = Math.ceil((x1 - x0) / 3); return Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y, p: .7, t: t0 + 2 * k })); };
  const draw = (B, pts) => { const s = modSurface(W, H, 7); layStroke(s, B, pts, 40, 1, null); return s; };
  const inkAt = (s, x0, x1) => { let a = 0; for (let y = 60; y < 180; y++) for (let x = Math.max(0, x0); x <= Math.min(W - 1, x1); x++) a += P - s.page[y * W + x]; return a; };
  // Codes: Tip choice 10, its nib after the six, every other code as before; a Stamp with no nib writes six.
  { const B = mk({ spacing: .5 }); B.nib = keepNib(STAR_NIB);
    const c = encodeM(B), back = readAnyCode(c).b, bare = encodeM(mk());
    t("a Stamp's code carries its nib (" + c + ") and reads back", c.split(".")[1].split("_").length === 7 && back.nib === STAR_NIB.id && encodeM(back) === c
      && bare.split(".")[1].split("_").length === 6 && CHOICES.tip.findIndex(ch => ch.k === "stamp") === 10 && brushKey(B) !== brushKey(mk({ spacing: .5 }))); }
  // The lasso cut keeps the ink inside it, trimmed; nothing inside, nothing.
  { const s = modSurface(W, H, 7); for (let y = 30; y <= 43; y++) for (let x = 50; x <= 69; x++) s.page[y * W + x] = 0; for (let y = 30; y <= 50; y++) for (let x = 200; x <= 220; x++) s.page[y * W + x] = 0;
    const n = cutNib(s, [[40, 20], [90, 20], [90, 60], [40, 60]]);
    t("the lasso cut is the ink inside it, trimmed (" + (n ? n.w + " × " + n.h : "none") + "); an empty lasso cuts nothing", n && n.w === 20 && n.h === 14 && n.a[0] === 255 && !cutNib(s, [[300, 100], [340, 100], [340, 140]])); }
  // Prints at Spacing: apart, paper between; Ink Fade None keeps every print as dark; on, they fade.
  { const gap = 48, none = draw(mk({ spacing: 1.2, inkFade: 0 }), line(30, 630, 120, 1000)), on = draw(mk({ spacing: 1.2, inkFade: .22 }), line(30, 630, 120, 1000));
    const at = (s, k) => inkAt(s, 30 + k * gap - 23, 30 + k * gap + 23), between = inkAt(none, 30 + gap / 2 - 1, 30 + gap / 2 + 1);
    const n = [0, 3, 6, 9].map(k => at(none, k)), f = [0, 3, 6].map(k => at(on, k));
    t("prints every Spacing, paper between (" + between.toFixed(0) + "); Ink Fade None as dark each print (" + n.map(v => v.toFixed(0)).join(", ") + "), on fading (" + f.map(v => v.toFixed(0)).join(", ") + ")",
      between < 1 && Math.min(...n) > .9 * Math.max(...n) && f[0] > 1.5 * f[1] && f[1] >= f[2] * .99); }
  // A rest inks it afresh: two taps close together, the second lighter; a third after a rest as dark as the first.
  { const s = modSurface(W, H, 7), tap = (x, t0) => { layStroke(s, mk({ spacing: 1.2 }), [{ x, y: 120, p: .7, t: t0 }], 40, 1, null); return inkAt(s, x - 30, x + 30); };
    const a = tap(100, 1000), b = tap(300, 1400), c = tap(500, 3400);
    t("taps: soon after, lighter (" + b.toFixed(0) + " of " + a.toFixed(0) + "); after a rest, afresh (" + c.toFixed(0) + ")", b < .9 * a && c > .9 * a); }
  // Without its nib: this lab's star.
  { const B = mk(); B.nib = 0x5eed; t("a Stamp without its nib prints the default star", resolve(B, 60, 1).pat.nib === STAR_NIB); }
  // Words and rolls.
  { const B = mk(), none = mk({ inkFade: 0 }), roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).tip.k === "stamp") return false; return true; };
    t("words: \"" + plainWords(B) + "\"; never rolled", plainWords(B).startsWith("Ink stamp. Stamps a shape cut from the page. Fades as it prints.") && !plainWords(none).includes("Fades")
      && captionItems(B).includes("stamp") && roll()); }
  return out;
}
const selfTestSt = selfTest;
selfTest = function () { const r = selfTestSt(); r.stamp = stampChecks(); r.ok = r.ok && r.stamp.every(c => c.ok); NM.lastStamp = r.stamp; return r; };
if (typeof runChecks === "function") {
  const runChecksSt = runChecks;
  runChecks = function () {
    runChecksSt();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastStamp) return;
      out.textContent += "\n\nStamp:\n" + NM.lastStamp.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 120);
  };
}

// ===================================================================================================================
// Crayon (mischief-lab-2.html's fourth pad; spec its NOTES "## 4. Crayon"; the app's PartChoice.CRAYON): Medium choice
// 7, Wax and Scratch. Wax on toothy paper: a light touch catches only the grain's peaks, a hard one goes solid; wax
// already laid lifts the surface, so layers build up; the edge is ragged, from noise fixed to the page, so a stroke
// drawn again matches. Its wax goes into the resist planes (Resist's, above) all kept, so washes and the rest slide off
// it and the eraser doesn't lift it; a later crayon takes on it (only masking fluid shields a crayon). Scratch On: a thin
// blade that scrapes back to white. Drawn as a charcoal line (its code and dials its own), its compose rule "crayon".
// Never rolled, never a variation's swap; its Medium size 30 px in every style (the app's ModularSizes.CRAYON).
// ===================================================================================================================
Object.assign(DIALS.medium, {
  wax: { label: "Wax", min: 0, max: 1, def: .6 },
  scratch: { label: "Scratch", choices: ["Off", "On"], def: 0 }
});
CHOICES.medium.push({ k: "crayon", label: "Crayon", dials: ["wax", "scratch"], macro: "wax", words: ["chalky", "waxy"], blurb: "Wax on the paper's tooth. Washes slide off it." });
REAL_ERASE.crayon = 1;
const CRAYON = { SIZE: 30, SCRATCH: .3, REACH: 1.35, WAX: .6, MOST_MAPS: 4 };
const crayonScratch = B => (B.medium.d.scratch | 0) === 1;
// Its Medium size in every style; a Stamp's own first, as the app's ModularSizes.size.
const styleSizeCr = styleSize;
styleSize = function (name, B) { return B && B.medium && B.medium.k === "crayon" && !(B.tip && B.tip.k === "stamp") ? CRAYON.SIZE : styleSizeCr(name, B); };
// A variation never swaps to the Crayon, and no roll picks it (the app's ModularFeatures.seedOnly).
const makeVarsCr = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.medium.slice();
  CHOICES.medium = keep.filter(c => c.k !== "crayon" || c.k === B.medium.k);
  try { return makeVarsCr(B, locks); } finally { CHOICES.medium = keep; }
};

// ---------- the page's own noise, the same for every crayon: tooth, the ragged edge, grit (kept by page size) ----------
const CRAYON_MAPS = new Map();
function crayonMaps(w, h) {
  const key = w + "x" + h;
  let M = CRAYON_MAPS.get(key);
  if (M) return M;
  const n = w * h, tooth = new Float32Array(n), rag = new Float32Array(n), grit = new Float32Array(n);
  for (let y = 0, i = 0; y < h; y++) for (let x = 0; x < w; x++, i++) {
    // Tooth: soft bumps 3 px across with a pixel of grit. The edge: notches ~7 px apart, a waver ~2.5 px, a pixel of grit.
    tooth[i] = .55 * stampNoise(x / 3, y / 3, 83) + .45 * hash01(hashInts(89, x, y), 0);
    rag[i] = .55 * stampNoise(x / 7, y / 7, 91) + .3 * stampNoise(x / 2.5, y / 2.5, 97) + .15 * hash01(hashInts(103, x, y), 0);
    grit[i] = hash01(hashInts(107, x, y), 0);
  }
  CRAYON_MAPS.set(key, M = { tooth, rag, grit });
  if (CRAYON_MAPS.size > CRAYON.MOST_MAPS) CRAYON_MAPS.delete(CRAYON_MAPS.keys().next().value);
  return M;
}
// The width's wobble, ±10 % along the stroke: noise ~22 px across, read at each dab's centre.
const crayonWobble = (x, y) => .9 + .2 * stampNoise(x / 22, y / 22, 101);

// ---------- resolve: a charcoal line, crisp and as dark as the swatch, with the crayon's rule ----------
// The brush the engine draws a crayon as: on paper (Float laid as Quick), smearing nothing (as Resist's).
function crayonAs(B) {
  const A = cloneBrush(B);
  A.medium = freshPart("medium", "charcoal"); Object.assign(A.medium.d, { spread: 0, hold: 1, dust: 0, erase: 1 });
  if (A.life.k === "float") A.life = freshPart("life", "wet");
  A.carry = freshPart("carry", "off");
  return A;
}
const resolveCr = resolve;
resolve = function (B, size, shade) {
  if (B.medium.k !== "crayon") return resolveCr(B, size, shade);
  const R = resolveCr(crayonAs(B), size, shade), scratch = crayonScratch(B);
  R.rule = "crayon"; R.settle = 0;
  R.crayon = { wax: B.medium.d.wax ?? CRAYON.WAX, scratch, tooth: B.paper.d.tooth ?? 0 };
  // Scratch: a blade a third as wide, laying white by its darkness, with a plain crisp nib.
  if (scratch) { R.inkSize *= CRAYON.SCRATCH; R.blend = "lighten"; } else R.pen.crayon = true;
  return R;
};

// ---------- the line: each dab's rim wobbles; past .6 of it only the peaks take wax; the edge ragged, with flecks ----------
// V, what the line buffer holds: the pressure (less up to .22 toward the edge) times the hand's darkness, the strongest
// per pixel. Dabs on a fixed distance grid, a tenth of the size apart, so the Quick window's re-lay lands the same dabs.
function crayonLine(surf, b, pts, size, final, from, clip, meta, win) {
  const { w, h, scratch: buf } = surf, M = crayonMaps(w, h), rect = [w, h, 0, 0];
  if (!pts.length) return rect;
  const total = pts[pts.length - 1].d, [inLen, outLen] = taperLens(b, size), fl = pts.flick, sp = Math.max(.4, size * .1);
  const cosA = Math.cos((b.angle ?? 45) * DEG), sinA = Math.sin((b.angle ?? 45) * DEG), asp = b.aspect ?? 1, tiltOn = (b.tiltSize || 0) + (b.tDark || 0) > 0;
  const cl = clip ? clip[0] : 0, ct = clip ? clip[1] : 0, cr = clip ? clip[2] : w - 1, cb = clip ? clip[3] : h - 1;
  for (let k = Math.max(1, from); k < pts.length; k++) {
    const p0 = pts[k - 1], p1 = pts[k], seg = p1.d - p0.d;
    for (let j = k === 1 ? 0 : Math.floor(p0.d / sp) + 1, d = j * sp; d <= p1.d; j++, d = j * sp) {
      if (win && (d < win.dMin || d >= win.dMax)) continue;
      const t = seg > 0 ? clamp((d - p0.d) / seg) : 1, x = lerp(p0.x, p1.x, t), y = lerp(p0.y, p1.y, t), pr = lerp(p0.p, p1.p, t), v = lerp(p0.v, p1.v, t);
      let wd = size * lerp(1 - .75 * b.pressure, 1 + .6 * b.pressure, pr) * clamp(1 + b.speed * .55 * (clamp(v / 2.5) - .3), .35, 1.6), lean = 0;
      if (inLen > 0) wd *= Math.pow(clamp(d / inLen), .6) * .92 + .08;
      if (fl) wd *= flickWidth(fl, d);
      if (b.fast) { if (b.taperOut > 0) wd *= lerp(1, Math.pow(p0.pk > 0 ? clamp(pr / (p0.pk * .7)) : 1, .8) * .92 + .08, b.taperOut); }
      else if (final && outLen > 0) wd *= Math.pow(clamp((total - d) / outLen), .6) * .92 + .08;
      if (win && win.tail) wd *= lerp(1, Math.pow(clamp((total - d) / win.tail.len), .6) * .92 + .08, win.tail.amt);
      if (tiltOn) { const T = tiltOf(lerp(p0.tx, p1.tx, t), lerp(p0.ty, p1.ty, t)); if (T) { lean = T.lean; wd *= tiltWidth(b, lean); } }
      const dk = handDark(b, pr, v, lean) * (p1.own === undefined ? 1 : lerp(p0.own, p1.own, t));
      const a = Math.max(.6, wd / 2) * crayonWobble(x, y), bb = Math.max(.6, Math.min(a, Math.max(1, a * asp))), rimPx = Math.sqrt(a * bb);
      const R = Math.ceil(a * CRAYON.REACH + 1);
      if (meta && R > (meta.widest || 0)) meta.widest = R;
      const x0 = Math.max(cl, Math.floor(x - R)), x1 = Math.min(cr, Math.ceil(x + R)), y0 = Math.max(ct, Math.floor(y - R)), y1 = Math.min(cb, Math.ceil(y + R));
      if (x1 < x0 || y1 < y0) continue;
      for (let py = y0; py <= y1; py++) for (let px = x0, i = py * w + x0; px <= x1; px++, i++) {
        if (buf[i] >= 1) continue;
        const dx = px + .5 - x, dy = py + .5 - y, u = (dx * cosA + dy * sinA) / a, q0 = (-dx * sinA + dy * cosA) / bb, q = Math.sqrt(u * u + q0 * q0);
        if (q >= CRAYON.REACH) continue;
        const edge = .8 + .5 * M.rag[i];
        let V;
        // Past the edge a lone high grain catches a fleck; inside it, the pressure, less toward the edge, a pixel's antialias.
        if (q > edge) { if (!(M.tooth[i] > .62 && M.grit[i] > .9)) continue; V = .8 * pr - .22; }
        else V = clamp((edge - q) * rimPx + .5) * (pr - .22 * smoothstep(.6, 1, q / edge));
        V = clamp(V * dk);
        if (V > buf[i]) buf[i] = V;
      }
      if (x0 < rect[0]) rect[0] = x0; if (y0 < rect[1]) rect[1] = y0; if (x1 > rect[2]) rect[2] = x1; if (y1 > rect[3]) rect[3] = y1;
    }
  }
  return rect;
}
const stampLineCr = stampLine;
stampLine = function (surf, b, pts, size, final, from, clip, meta, win) { return b.crayon ? crayonLine(surf, b, pts, size, final, from, clip, meta, win) : stampLineCr(surf, b, pts, size, final, from, clip, meta, win); };

// ---------- the compose: wax on the tooth, building on what's there; Scratch scrapes to white ----------
// The crayon's darkness at tooth t, line V and darkness ex found there (wax laid lifts the surface by k × ex).
function crayonDark(t, k, ex, V) {
  let a = clamp((t + k * ex - 1.08 + V) * 3.5);
  if (V > .75) a = Math.max(a, (V - .75) * 2.4 * clamp(t * 5));
  return 1 - Math.pow(1 - .4 * a, 8);
}
function crayonCompose(st, rect) {
  const R = st.R, C = R.crayon, s = st.surf, w = s.w, P = PAPER_TONE, K = R.inkK;
  const x0 = Math.max(0, rect[0]), y0 = Math.max(0, rect[1]), x1 = Math.min(s.w - 1, rect[2]), y1 = Math.min(s.h - 1, rect[3]);
  if (x1 < x0 || y1 < y0) return;
  const M = crayonMaps(w, s.h), L = s.L, page = s.page, base = s.base, fine = s.fine, mid = s.mid, ph = .3 * C.tooth, kx = .35 * C.wax / CRAYON.WAX;
  const kept = s.kept && st.e < 1 ? s.kept : null, kb = kept ? s.keptBase : null, ke = 1 - st.e, dots = s.dots;
  // The shield as the stroke found it: masking fluid's share (what can lift) shields a crayon; wax takes wax.
  const rp = s.resist, rk = s.resistKept, rb = rp ? s.resistBase : null, rkb = rp ? s.resistKeptBase : null;
  const lays = !C.scratch && C.wax > 0 && rp, wk = 1.6 * C.wax / CRAYON.WAX;
  for (let y = y0; y <= y1; y++) for (let i = y * w + x0, e = y * w + x1; i <= e; i++) {
    const V = L[i], u = base[i];
    if (!(V > 0)) { page[i] = u; if (kept) kept[i] = kb[i]; if (lays) { rp[i] = rb[i]; rk[i] = rkb[i]; } continue; }
    const f = rb ? 1 - Math.max(0, rb[i] - rkb[i]) : 1;
    if (C.scratch) {
      const a = clamp(V * (M.grit[i] < .15 ? .5 : 1) * K) * f;
      page[i] = u + (P - u) * a;
      if (kept) kept[i] = kb[i] + (P - kb[i]) * a * ke;
    } else {
      const t = lerp(M.tooth[i], fine[i] * .65 + mid[i] * .35, ph);
      page[i] = u * (1 - crayonDark(t, kx, 1 - u / P, V) * K * f);
      if (kept) kept[i] = kb[i] * (1 - crayonDark(t, kx, 1 - kb[i] / P, V) * K * f * ke);
      // Its wax into the resist planes, all of it kept: washes slide off it, the eraser doesn't lift it, it isn't hatched.
      if (lays) { const m = clamp(wk * crayonDark(t, kx, 0, V) * K); rp[i] = Math.max(rb[i], m); rk[i] = Math.max(rkb[i], m); }
    }
    // Over Tone's dots: they are paint now (as any mark, the Screen compose's).
    if (dots && page[i] !== u) dots[i] = 0;
  }
}
const composeCr = composeStroke;
composeStroke = function (st, rect, phase) { return st.R.crayon ? crayonCompose(st, rect) : composeCr(st, rect, phase); };
const StrokeCr = Stroke;
Stroke = class extends StrokeCr {
  constructor(surf, B, size, shade, host) {
    super(surf, B, size, shade, host);
    // The planes as the stroke found them: the shield, and what its wax adds to.
    const C = this.R.crayon;
    if (C && (surf.resist || (!C.scratch && C.wax > 0))) resistBegin(surf);
  }
};

// ---------- words: "Wax crayon. Builds up, resists washes." ----------
const crayonNoun = B => crayonScratch(B) ? "Crayon scratch" : "Wax crayon";
// The brush as the old words know it: a line of ink that won't erase (as Resist's), the crayon's own said over it; in
// "This brush" and the caption without its smudge, which it never lays (the app's ModularWords). A card keeps its own.
function crayonPlain(B, card) { const A = cloneBrush(B); A.medium = freshPart("medium", "ink"); A.medium.d.erase = 0; if (!card) A.carry = freshPart("carry", "off"); return normBrush(A); }
const choiceWordsCr = choiceWords;
choiceWords = function (B, part) {
  if (B.medium.k !== "crayon") return choiceWordsCr(B, part);
  return part === "medium" ? (crayonScratch(B) ? "Scratch" : "Crayon") : choiceWordsCr(crayonPlain(B, true), part);
};
const captionItemsCr = captionItems;
captionItems = function (B) {
  if (B.medium.k !== "crayon") return captionItemsCr(B);
  // Only the items an ink and a wash would both give (no medium tag), its own in the medium's place.
  const A = crayonPlain(B), W = cloneBrush(A); W.medium = freshPart("medium", "wash");
  const other = captionItemsCr(normBrush(W)), items = captionItemsCr(A).filter(t => other.includes(t));
  items.splice(1, 0, crayonScratch(B) ? "scratch" : "crayon");
  return items;
};
const plainWordsCr = plainWords;
plainWords = function (B) {
  if (B.medium.k !== "crayon") return plainWordsCr(B);
  const sent = plainWordsCr(crayonPlain(B)).split(/(?<=\.)\s+/).filter(Boolean), first = sent.shift() || "";
  // The first sentence names it for itself, whatever its tip, keeping its tail (" on cold press").
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(first), tail = m && m[2] ? m[2] : "";
  const out = [crayonNoun(B) + tail + ".", crayonScratch(B) ? "Scrapes back to white." : (B.medium.d.wax ?? CRAYON.WAX) >= .05 ? "Builds up, resists washes." : "Builds up."];
  let t = out.join(" ");
  for (const x of sent) { if (t.length + 1 + x.length > 260) break; t += " " + x; }
  return t;
};

// ---------- checks ----------
function crayonChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 640, H = 240, q = quantTone, P = PAPER_TONE;
  const mk = (md, medium) => { const B = { name: "wax", seed: 29, style: "Pastels", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium || "crayon"), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "wet"), hand: freshPart("hand", "steady") }; Object.assign(B.medium.d, md || {}); return normBrush(B); };
  const line = (x0, x1, y, p, n = 120) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y, p, t: k * 6, tx: NaN, ty: NaN }));
  const draw = (B, ys, p, s) => { s = s || modSurface(W, H, 7); for (const y of ys) layStroke(s, B, line(40, 600, y, p), 30, 1, null); return s; };
  const count = (s, f, y0 = 0, y1 = H) => { let n = 0; for (let y = y0; y < y1; y++) for (let x = 60; x < 580; x++) if (f(q(s.page[y * W + x]), y * W + x)) n++; return n; };
  const darkSum = (s, y0, y1) => { let a = 0; for (let y = y0; y < y1; y++) for (let x = 60; x < 580; x++) a += P - s.page[y * W + x]; return a; };
  const hashPage = s => { let hh = 0x811c9dc5; for (let i = 0; i < s.page.length; i++) hh = Math.imul(hh ^ Math.round(s.page[i] * 64), 16777619); return hh >>> 0; };
  const seg = c => c.split("_")[1];
  // Codes: Medium choice 7, Wax two digits, Scratch one; they read back; every other code as before.
  { const c = encodeM(mk()), c2 = encodeM(mk({ wax: .25, scratch: 1 })), back = readAnyCode(c2).b;
    const olds = [...LEGACY_CODES.map(x => encodeM(readAnyCode(x).b)), OIL_PAINT, "f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_0000000000000000000nz0.7"];
    const tone = encodeM(mk({}, "tone")), resist = encodeM(mk({ erase: 0 }, "resist"));
    t("a Crayon's code is _" + seg(c) + "_ (Scratch On, Wax .25: _" + seg(c2) + "_), Medium choice " + CHOICES.medium.findIndex(ch => ch.k === "crayon") + "; it reads back; older codes as before",
      seg(c) === "7" + two36(Math.round(.6 * M_STEPS)) + "0" && seg(c2).length === 4 && seg(c2).endsWith("1") && encodeM(readAnyCode(c).b) === c && encodeM(back) === c2 && back.medium.k === "crayon"
      && Math.abs(back.medium.d.wax - .25) < 1e-3 && back.medium.d.scratch === 1 && CHOICES.medium.findIndex(ch => ch.k === "crayon") === 7
      && olds.every(x => encodeM(readAnyCode(x).b) === x) && readAnyCode(tone).b.medium.k === "tone" && readAnyCode(resist).b.medium.k === "resist" && encodeM(readAnyCode(resist).b) === resist); }
  // Light pressure catches the peaks only; hard goes near solid in the middle, the edge still broken.
  { const light = draw(mk(), [120], .25), hard = draw(mk(), [120], 1), lit = count(light, v => v < 238), hd = count(hard, v => v < 238);
    // The middle: rows within 4 px of the line; its outer band: 11 to 15 px off it, both sides.
    const mid = count(hard, v => v <= 51, 116, 125) / (9 * 520), outer = f => count(hard, f, 105, 109) + count(hard, f, 132, 136);
    const paperOut = outer(v => v >= 238), inkOut = outer(v => v < 238);
    t("light pressure inks " + lit + " px, hard " + hd + "; hard is " + Math.round(mid * 100) + "% near black in the middle, its edge broken (" + paperOut + " paper, " + inkOut + " inked px in its outer band)",
      hd > 3 * lit && mid > .85 && paperOut > 400 && inkOut > 400); }
  // Layers build up: a second stroke over the first darkens it (wax takes wax); masking fluid still shields a crayon.
  { const once = draw(mk(), [120], .55), twice = draw(mk(), [120, 120], .55), a = darkSum(once, 100, 140), b = darkSum(twice, 100, 140);
    const s = modSurface(W, H, 7); layStroke(s, mk({ erase: 1 }, "resist"), line(40, 600, 120, .8), 30, 1, null); const before = Float32Array.from(s.page), held = s.resist.map((r, i) => r > .999 && s.resistKept[i] < .001 ? 1 : 0); draw(mk(), [120], 1, s);
    let fluid = 0, moved = 0; for (let i = 0; i < W * H; i++) if (held[i]) { fluid++; if (Math.abs(s.page[i] - before[i]) > .5) moved++; }
    t("a second stroke darkens the first (" + Math.round(a / P) + " → " + Math.round(b / P) + "); masking fluid shields a crayon (" + moved + " of " + fluid + " px changed)", b > 1.15 * a && fluid > 1000 && moved === 0); }
  // A wash slides off the wax: it lays far less on the crayon than beside it.
  { const s = draw(mk(), [80], 1), before = Float32Array.from(s.page);
    layStroke(s, mk({ wet: .3 }, "wash"), line(10, 630, 120, .8), 300, 1, null);
    let on = 0, nOn = 0, off = 0, nOff = 0;
    for (let y = 60; y < 200; y++) for (let x = 100; x < 540; x++) { const i = y * W + x, k = 1 - s.page[i] / Math.max(1, before[i]); if (before[i] < 60) { on += k; nOn++; } else if (y > 140 && before[i] > P - .5) { off += k; nOff++; } }
    on /= Math.max(1, nOn); off /= Math.max(1, nOff);
    t("a wash lays " + Math.round(on * 100) + "% on the crayon, " + Math.round(off * 100) + "% beside it; the wax isn't hatched", nOn > 1000 && off > .2 && on < .25 * off && !s.resistKept.some((k, i) => k > .5 && hatchAt(s, i % W, (i / W) | 0, i) >= 0)); }
  // Scratch through a black crayon patch leaves near white.
  { const s = draw(mk(), [70, 85, 100, 115, 130, 145, 160, 175], 1); draw(mk(), [70, 85, 100, 115, 130, 145, 160, 175], 1, s);
    const black = count(s, v => v <= 34, 100, 140) / (40 * 520);
    layStroke(s, mk({ scratch: 1 }), line(40, 600, 120, .9), 30, 1, null);
    const white = count(s, v => v >= 204, 118, 123);
    t("Scratch through a black patch (" + Math.round(black * 100) + "% black) leaves " + white + " near-white px", black > .85 && white > 1200); }
  // The same stroke drawn twice on fresh pages matches exactly; batched differently, the same.
  { const one = hashPage(draw(mk(), [100, 140], .7)), two = hashPage(draw(mk(), [100, 140], .7));
    const batched = n => { const s = modSurface(W, H, 7), S = new Stroke(s, mk(), 30, 1, null), pts = line(40, 600, 120, .8); for (let i = 0; i < pts.length; i += n) S.add(pts.slice(i, i + n).map(p => [p.x, p.y, p.p, p.t, p.tx, p.ty])); S.end(); return hashPage(s); };
    t("the same strokes drawn twice match exactly; batched 1 or 7 a time, the same", one === two && batched(1) === batched(7)); }
  // Words, size, rolls and variations.
  { const B = mk(), lo = mk({ wax: .02 }), sc = mk({ scratch: 1 }), paper = mk(), smeared = mk(); paper.paper = freshPart("paper", "cold press"); smeared.carry = freshPart("carry", "smudge");
    const roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).medium.k === "crayon") return false; return true; };
    const vars = () => { const ink = mk({}, "ink"), lk = { parts: new Set(), dials: new Set() }; for (let i = 0; i < 200; i++) if (makeVars(ink, lk).some(v => v.b.medium.k === "crayon")) return false; return true; };
    t("words: \"" + plainWords(B) + "\", \"" + plainWords(lo) + "\", \"" + plainWords(sc) + "\", \"" + plainWords(paper) + "\"; " + choiceWords(B, "medium") + ", " + choiceWords(sc, "medium") + "; 30 px in every style; never rolled or swapped to",
      plainWords(B) === "Wax crayon. Builds up, resists washes." && plainWords(lo) === "Wax crayon. Builds up." && plainWords(sc) === "Crayon scratch. Scrapes back to white."
      && /^Wax crayon on [a-z -]+\. Builds up/.test(plainWords(paper)) && !/eras/i.test(plainWords(B) + plainWords(sc)) && choiceWords(B, "medium") === "Crayon" && choiceWords(sc, "medium") === "Scratch"
      && captionItems(B)[1] === "crayon" && captionItems(sc)[1] === "scratch" && plainWords(smeared) === plainWords(B) && captionItems(smeared).join() === captionItems(B).join() && STYLE_NAMES.every(n => styleSize(n, B) === 30) && roll() && vars()); }
  return out;
}
const selfTestCr = selfTest;
selfTest = function () { const r = selfTestCr(); r.crayon = crayonChecks(); r.ok = r.ok && r.crayon.every(c => c.ok); NM.lastCrayon = r.crayon; return r; };
if (typeof runChecks === "function") {
  const runChecksCr = runChecks;
  runChecks = function () {
    runChecksCr();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastCrayon) return;
      out.textContent += "\n\nCrayon:\n" + NM.lastCrayon.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 140);
  };
}

// ===================================================================================================================
// Spiro (mischief-lab-2.html's third pad; the owner: "more like the real toy", loops round the stroke and a guilloche
// within it, its variables in Control; the app's SpiroStamper): Tip choice 11, a fine line from two phases, th (the
// wheel's trip round the ring) and ps (its spin, q times as fast, summed step by step, so a setting changed mid-stroke
// bends the line). Curves: the toy's (a wheel inside a 96- or 105-tooth ring, the pen in a hole), Epi (the wheel
// outside), Rose, Lissajous, Harmonograph (dying away over Fade Turns), Coil (its far side lighter). Line: the curve
// rides the stroke, Loops per cm. Ring: the stroke's first point pins the ring; a second with no pressure PIN or more out
// is its rim (the app's ring setup: pen down pins the centre, the drag out sizes it), else the ring is the brush's own
// size; the pen's eased angle round it turns the wheel, only forward past a tremble; at the lift it catches up, and a tap
// draws the whole figure. Oval and Bar: the toy's oval and bar tracks, set up corner to corner, the wheel rolling round
// inside (spiroTrack), the other curves stretched to the box. Control's
// Pressure Sets, Speed Sets and Tilt Sets (the hand's last dials, after the app's Tilt Range and page tilt, kept here so
// codes line up) scale Size, Loops, Wheel, Hole or Petals by .4 to 1.3. Never rolled.
// ===================================================================================================================
const SPIRO = { SIZE: 132, PROTO: 3, PX_CM: 300 / 2.54, WHEELS: [24, 30, 32, 36, 40, 42, 45, 48, 50, 52, 56, 60, 63, 64, 72, 75, 80, 84], TEETH: [96, 105],
  RATIOS: [[1, 2], [2, 3], [3, 4], [3, 5]], RHO: .2, SIDE: Math.sin(50 * Math.PI / 180), RING: 80 / 22, PIN: 18, REV: 18, OVAL: 2, BAR: 3, ASPECT: .6, EASE: 25, EASE_HAND: 80,
  UNTIMED: 8, SPEED_FULL: 3.6, NEAR: .92, FAR: .47, STEP: 1.5, LINE_R: 2.4 };
// Curves and what a reading drives, in their options' order.
const SP_C = { spiro: 0, epi: 1, rose: 2, liss: 3, harmo: 4, coil: 5 }, SP_D = { size: 0, loops: 1, wheel: 2, hole: 3, petals: 4, none: 5 };
const DRIVE_OPTS = ["Size", "Loops", "Wheel", "Hole", "Petals", "Nothing"];
Object.assign(DIALS.tip, {
  mode: { label: "Mode", choices: ["Line", "Ring", "Oval", "Bar"], def: 0 },
  curve: { label: "Curve", choices: ["Spirograph", "Epi", "Rose", "Lissajous", "Harmonograph", "Coil"], def: 0 },
  loops: { label: "Loops per cm", min: .3, max: 4, def: 2, fmt: v => v.toFixed(1) },
  teeth: { label: "Ring Teeth", choices: ["96", "105"], def: 0 },
  wheel: { label: "Wheel", min: 0, max: 17, def: 4, int: true, fmt: v => String(SPIRO.WHEELS[clamp(Math.round(v), 0, 17)]) },
  hole: { label: "Hole", min: .2, max: .9, def: .8, fmt: v => Math.round(v * 100) + "%" },
  petals: { label: "Petals", min: 3, max: 12, def: 5, int: true, fmt: v => String(Math.round(v)) },
  ratio: { label: "Ratio", choices: ["1:2", "2:3", "3:4", "3:5"], def: 1 },
  phase: { label: "Phase", min: 0, max: 1, def: .5, fmt: v => Math.round(v * 90) + "°" },
  fades: { label: "Fade Turns", min: 3, max: 40, def: 12, int: true, fmt: v => String(Math.round(v)) }
});
CHOICES.tip.push({ k: "spiro", label: "Spiro", dials: ["weight", "mode", "curve", "loops", "teeth", "wheel", "hole", "petals", "ratio", "phase", "fades", "flick"],
  macro: "weight", words: ["small", "big"], blurb: "The toy's wheel and ring: loops along the line, or round a ring, an oval or a bar set up first." });
PATTERN_TIPS.add("spiro");
// The hand's last dials, as the app's: its Tilt Range (Wide: the Fude pen's, engine.js tiltWidth) and page tilt (which this lab
// doesn't draw: hidden, kept so codes line up), then a Spiro's drives, shown only for a Spiro (dialShown).
Object.assign(DIALS.hand, {
  trange: { label: "Tilt Range", choices: ["Normal", "Wide"], def: 0 },
  gs: { label: "Page Tilt Size", min: 0, max: 1, def: 0, hide: true }, gd: { label: "Page Tilt Dark", min: 0, max: 1, def: 0, hide: true },
  gw: { label: "Page Tilt Nib", min: 0, max: 1, def: 0, hide: true },
  pdrive: { label: "Pressure Sets", choices: DRIVE_OPTS, def: 0 }, sdrive: { label: "Speed Sets", choices: DRIVE_OPTS, def: 5 }, tdrive: { label: "Tilt Sets", choices: DRIVE_OPTS, def: 3 }
});
const DRIVES = ["pdrive", "sdrive", "tdrive"];
HAND_DIALS.push("trange", "gs", "gd", "gw", ...DRIVES);
for (const k of ["trange", "gs", "gd", "gw", ...DRIVES]) ADDED.add("hand." + k);
KEEPS.hand.push(...DRIVES);
// A Spiro's Medium is its own in every style (the app's ModularSizes.SPIRO).
const styleSizeSp = styleSize;
styleSize = function (name, B) { return B && B.tip && B.tip.k === "spiro" ? SPIRO.SIZE : styleSizeSp(name, B); };
// A variation never swaps to the Spiro, and no roll picks it.
const makeVarsSp = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.tip.slice();
  CHOICES.tip = keep.filter(c => c.k !== "spiro" || c.k === B.tip.k);
  try { return makeVarsSp(B, locks); } finally { CHOICES.tip = keep; }
};
// Whether driving `what` changes the tip's curve (the app's SpiroStamper.acts), and the dials a Spiro's curve shows (shows).
function spiroActs(T, what) {
  // Wheel drives the toy's wheel, or the swings' ratio.
  return what === SP_D.size ? true : what === SP_D.loops ? spiroShows(T, "loops") : what === SP_D.wheel ? spiroShows(T, "wheel") || spiroShows(T, "ratio")
    : what === SP_D.hole ? spiroShows(T, "hole") : what === SP_D.petals ? spiroShows(T, "petals") : false;
}
function spiroShows(T, key) {
  if (T.k !== "spiro") return true;
  const c = T.d.curve | 0, wheels = c === SP_C.spiro || c === SP_C.epi, swings = c === SP_C.liss || c === SP_C.harmo;
  switch (key) {
    case "loops": return (T.d.mode | 0) === 0 || c === SP_C.coil;
    case "teeth": case "wheel": case "hole": return wheels;
    case "petals": return c === SP_C.rose;
    case "ratio": case "phase": return swings;
    case "fades": return c === SP_C.harmo;
    default: return true;
  }
}
const dialShownSp = dialShown;
dialShown = function (B, part, key) {
  if (part === "hand" && DRIVES.includes(key)) return B.tip.k === "spiro";
  return dialShownSp(B, part, key) && (part !== "tip" || spiroShows(B.tip, key));
};

// ---------- resolve: the pattern's Spiro fields ----------
const resolveSp = resolve;
resolve = function (B, size, shade) {
  const R = resolveSp(B, size, shade);
  if (B.tip.k === "spiro" && R.pat) {
    const mk = B.medium.k, lineMedium = mk === "ink" || mk === "charcoal" || mk === "graphite" || mk === "resist" || mk === "tone";
    const T = B.tip.d, H = B.hand.d, t = k => T[k] ?? DIALS.tip[k].def, h = k => H[k] ?? DIALS.hand[k].def;
    R.pat.size = lineMedium ? R.inkSize : R.washSize;
    R.pat.spiro = { mode: t("mode") | 0, ring: (t("mode") | 0) !== 0, curve: t("curve") | 0, teeth: SPIRO.TEETH[t("teeth") | 0], wheel: clamp(Math.round(t("wheel")), 0, 17), petals: Math.round(t("petals")),
      ratio: t("ratio") | 0, perCm: t("loops"), hole: t("hole"), phase: t("phase"), fades: t("fades"), drives: DRIVES.map(k => h(k) | 0) };
  }
  return R;
};

// ---------- the stamper (the app's SpiroStamper) ----------
const spiroRadius = (S, size) => size / 2 * (S.ring ? SPIRO.RING : 1);
const spiroLineR = size => Math.max(1, SPIRO.LINE_R * Math.sqrt(size / SPIRO.SIZE));
const spGcd = (a, b) => b ? spGcd(b, a % b) : a;
// The curve's shape with what the pen drives now (f: Size, Loops, Wheel, Hole, Petals); A the ring's radius.
function spiroGear(S, f, ring, A) {
  const g = { c: S.curve, q: 1, a: 0, d: 0, ph: 0, lam: 0, th0: 0 };
  if (S.curve === SP_C.spiro || S.curve === SP_C.epi) {
    const R = S.teeth, r = clamp(SPIRO.WHEELS[S.wheel] * f[2], 12, S.curve === SP_C.spiro ? R - 8 : 120), d = clamp(S.hole * f[3], .05, .95) * r;
    if (S.curve === SP_C.spiro) { g.q = (R - r) / r; g.a = (R - r) / R; g.d = d / R; }
    else { const E = R + 2 * r; g.q = (R + r) / r; g.a = (R + r) / E; g.d = d / E; }
  } else if (S.curve === SP_C.rose) {
    const P = S.petals, n = Math.floor(P / 2);
    g.q = (P % 2 === 1 ? P : n % 2 === 1 ? n / 2 : n) * f[4];
  } else if (S.curve === SP_C.liss || S.curve === SP_C.harmo) {
    const [a, b] = SPIRO.RATIOS[S.ratio];
    g.q = a / b * f[2] + (S.curve === SP_C.harmo ? .008 : 0); g.ph = S.phase * Math.PI / 2; g.lam = Math.log(20) / (S.fades * 2 * Math.PI);
  } else g.q = ring ? Math.max(3, Math.round(S.perCm * f[1] * 2 * Math.PI * (1 - SPIRO.RHO) * A / SPIRO.PX_CM)) : 1;
  return g;
}
// The whole figure's turn, for a tap: until it closes (the harmonograph: until it is down to 3%).
function spiroSpan(S, g) {
  switch (S.curve) {
    case SP_C.spiro: case SP_C.epi: { const r = SPIRO.WHEELS[S.wheel]; return 2 * Math.PI * r / spGcd(g && g.tr ? g.tr.teeth : S.teeth, r); }
    case SP_C.rose: { const P = S.petals; return P % 2 === 1 ? Math.PI : Math.floor(P / 2) % 2 === 1 ? 4 * Math.PI : 2 * Math.PI; }
    case SP_C.liss: return 2 * Math.PI * SPIRO.RATIOS[S.ratio][1];
    case SP_C.harmo: return Math.log(1 / .03) / g.lam;
    default: return 2 * Math.PI;
  }
}
// The pen's hole at (th, ps) of a curve A across centred at (cx, cy), into out: x, y, tone. (nx, ny): the line's normal.
// On a track the wheel's centre keeps its radius a in from the line (the epitrochoid's out), the hole d from it; the other
// curves stretched to the track's box.
function spiroAt(g, th, ps, A, cx, cy, nx, ny, ring, out) {
  const tr = ring ? g.tr : null;
  if (!tr) return spiroCurve(g, th, ps, A, cx, cy, nx, ny, ring, out);
  if (spiroRolls(g)) {
    const p = tr.at(th), side = g.c === SP_C.epi ? 1 : -1, wx = p[0] + side * g.a * Math.cos(p[3]), wy = p[1] + side * g.a * Math.sin(p[3]);
    out[0] = cx + A * (wx - side * g.d * Math.cos(ps)); out[1] = cy + A * (wy - g.d * Math.sin(ps)); out[2] = SPIRO.NEAR;
    return;
  }
  spiroCurve(g, th, ps, 1, 0, 0, nx, ny, true, out);
  out[0] = cx + A * tr.ha * out[0]; out[1] = cy + A * tr.hb * out[1];
}
function spiroCurve(g, th, ps, A, cx, cy, nx, ny, ring, out) {
  let tone = SPIRO.NEAR, x, y;
  switch (g.c) {
    case SP_C.spiro: x = cx + A * (g.a * Math.cos(th) + g.d * Math.cos(ps)); y = cy + A * (g.a * Math.sin(th) - g.d * Math.sin(ps)); break;
    case SP_C.epi: x = cx + A * (g.a * Math.cos(th) - g.d * Math.cos(ps)); y = cy + A * (g.a * Math.sin(th) - g.d * Math.sin(ps)); break;
    case SP_C.rose: { const r = Math.cos(ps); x = cx + A * r * Math.cos(th); y = cy + A * r * Math.sin(th); break; }
    case SP_C.liss: x = cx + A * Math.sin(ps + g.ph); y = cy + A * Math.sin(th); break;
    case SP_C.harmo: { const e = A * Math.exp(-g.lam * Math.max(0, th - g.th0)); x = cx + e * Math.sin(ps + g.ph); y = cy + e * Math.sin(th); break; }
    default: {
      let rho = A, tx = ny, ty = -nx;
      if (ring) { rho = A * SPIRO.RHO; nx = Math.cos(th); ny = Math.sin(th); tx = -ny; ty = nx; cx += (A - rho) * nx; cy += (A - rho) * ny; }
      const c = Math.cos(ps), s = Math.sin(ps);
      x = cx + rho * (nx * c + tx * s * SPIRO.SIDE); y = cy + rho * (ny * c + ty * s * SPIRO.SIDE);
      if (s < 0) tone = SPIRO.FAR;
    }
  }
  out[0] = x; out[1] = y; out[2] = tone;
}
// An antialiased line r px each side, round ends, of darkness tone, each pixel keeping its strongest.
function spiroSeg(s, buf, x0, y0, x1, y1, r, tone, d, rect) {
  if (!(tone > 0)) return;
  const w = s.w, R = r + 1, bx0 = Math.max(0, Math.floor(Math.min(x0, x1) - R)), bx1 = Math.min(w - 1, Math.ceil(Math.max(x0, x1) + R));
  const by0 = Math.max(0, Math.floor(Math.min(y0, y1) - R)), by1 = Math.min(s.h - 1, Math.ceil(Math.max(y0, y1) + R));
  if (bx1 < bx0 || by1 < by0) return;
  const ex = x1 - x0, ey = y1 - y0, len2 = ex * ex + ey * ey;
  for (let py = by0; py <= by1; py++) {
    const qy = py + .5 - y0;
    for (let px = bx0; px <= bx1; px++) {
      const qx = px + .5 - x0, t = len2 > 0 ? clamp((qx * ex + qy * ey) / len2) : 0, dx = qx - t * ex, dy = qy - t * ey;
      const c = clamp(r + .5 - Math.sqrt(dx * dx + dy * dy)) * tone, i = py * w + px;
      if (c > buf[i]) { buf[i] = c; if (WET) WET[i] = d; }
    }
  }
  grow(rect, [bx0, by0, bx1, by1]);
}
// The readings at sample q eased into what the pen drives, dt ms since the last (NaN: at once). A tilt the pen doesn't give leaves its setting as set.
function spiroDrive(M, S, q, dt) {
  const want = [1, 1, 1, 1, 1], tl = tiltOf(q.tx, q.ty);
  const scale = (what, v) => { if (what >= 0 && what < 5) want[what] *= .4 + .9 * v; };
  scale(S.drives[0], q.p); scale(S.drives[1], clamp((q.v || 0) / SPIRO.SPEED_FULL)); if (tl) scale(S.drives[2], tl.lean);
  const e = Number.isNaN(dt) ? 1 : 1 - Math.exp(-dt / SPIRO.EASE_HAND);
  for (let i = 0; i < 5; i++) M.f[i] += (want[i] - M.f[i]) * e;
}
function spiroSince(M, q) {
  if (!Number.isFinite(q.t) || !Number.isFinite(M.t)) { M.t = q.t; return SPIRO.UNTIMED; }
  const dt = Math.max(1, q.t - M.t); M.t = q.t; return dt;
}
// The toy's ring as a share of the figure's radius: 1, but the epitrochoid's, inside its wheel (the app's ringShare).
const spiroShare = (S, f) => S.curve === SP_C.epi && S.mode < SPIRO.OVAL ? 2 * spiroGear(S, f, true, 1).a - 1 : 1;
// An Oval's or Bar's track (the app's SpiroStamper.Track), its longer half 1: an ellipse, or a rectangle whose corners are
// rounded to a quarter of its shorter side. Its wheels are sized to its tightest bend, so they roll round it, corners too.
// Sampled along its line from its right end: the pen's angle round its centre finds the point there, how far along it is
// and which way it faces.
const spUnwrap = (a, near) => a + 2 * Math.PI * Math.round((near - a) / (2 * Math.PI));
const spCorner = (ha, hb) => Math.min(ha, hb) / 2;
const spBarLength = (ha, hb) => { const c = spCorner(ha, hb); return 4 * (ha + hb - 2 * c) + 2 * Math.PI * c; };
// The point u of the way round a track of halves ha, hb from its right end, and its outward normal's angle, into out.
function spOutline(bar, ha, hb, u, out) {
  if (!bar) { const t = 2 * Math.PI * u; out[0] = ha * Math.cos(t); out[1] = hb * Math.sin(t); out[2] = Math.atan2(ha * Math.sin(t), hb * Math.cos(t)); return; }
  const c = spCorner(ha, hb), sx = ha - c, sy = hb - c;
  let s = u * spBarLength(ha, hb) + sy;
  // Side q (0 right, 1 bottom, 2 left, 3 top, then the right again), from its start, then the corner after it.
  for (let k = 0; ; k++) {
    const q = k & 3, nx = q === 0 ? 1 : q === 2 ? -1 : 0, ny = q === 1 ? 1 : q === 3 ? -1 : 0, half = q % 2 === 0 ? sy : sx, far = q % 2 === 0 ? ha : hb;
    if (s <= 2 * half || k === 4) { const o = Math.min(s, 2 * half) - half; out[0] = nx * far - ny * o; out[1] = ny * far + nx * o; out[2] = k * Math.PI / 2; return; }
    s -= 2 * half;
    if (s <= c * Math.PI / 2) { const a = k * Math.PI / 2 + s / c, along = q % 2 === 0 ? sx : sy; out[0] = nx * along - ny * half + c * Math.cos(a); out[1] = ny * along + nx * half + c * Math.sin(a); out[2] = a; return; }
    s -= c * Math.PI / 2;
  }
}
function spiroTrack(bar, ha, hb, ringTeeth) {
  const N = 2048, TAU = 2 * Math.PI, x = new Float64Array(N + 1), y = new Float64Array(N + 1), s = new Float64Array(N + 1), phi = new Float64Array(N + 1), n = new Float64Array(N + 1), o = [0, 0, 0];
  for (let i = 0; i <= N; i++) {
    spOutline(bar, ha, hb, i / N, o);
    x[i] = o[0]; y[i] = o[1];
    n[i] = i === 0 ? o[2] : spUnwrap(o[2], n[i - 1]);
    phi[i] = i === 0 ? 0 : spUnwrap(Math.atan2(o[1], o[0]), phi[i - 1]);
    s[i] = i === 0 ? 0 : s[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
  }
  phi[N] = n[N] = TAU;
  const length = s[N], bend = bar ? spCorner(ha, hb) : Math.min(ha, hb) * Math.min(ha, hb) / Math.max(ha, hb), p = [0, 0, 0, 0];
  // Its teeth: the ring's pitch round it, rounded up to a whole 24, as a kit's are.
  const teeth = 24 * Math.ceil(length * ringTeeth / (TAU * bend * 24) - 1e-9);
  // The line at the pen's angle th round the centre, any turn: x, y, how far along (laps counted), its normal's angle.
  const at = th => {
    const lap = Math.floor(th / TAU), f = th - lap * TAU;
    let lo = 0, hi = N;
    while (hi - lo > 1) { const m = (lo + hi) >>> 1; if (phi[m] <= f) lo = m; else hi = m; }
    const u = clamp((f - phi[lo]) / (phi[hi] - phi[lo]));
    p[0] = lerp(x[lo], x[hi], u); p[1] = lerp(y[lo], y[hi], u); p[2] = lap * length + lerp(s[lo], s[hi], u); p[3] = lap * TAU + lerp(n[lo], n[hi], u);
    return p;
  };
  return { ha, hb, length, teeth, at };
}
// The ring's radius a rim at (x, y) sets round the pin (cx, cy): 0 within PIN (the brush's own size).
const spiroRim = (cx, cy, x, y) => { const d = Math.hypot(x - cx, y - cy); return d >= SPIRO.PIN ? d : 0; };
// A track's centre and halves [cx, cy, ha, hb] set up from the pin to the pen at (x, y): a ring round the pin out to the
// pen, an Oval or Bar corner to corner (each half at least PIN); within the pin the brush's own radius, an Oval or Bar ASPECT as tall.
function spiroBox(mode, px, py, x, y, own) {
  const rim = spiroRim(px, py, x, y);
  if (mode < SPIRO.OVAL) { const r = rim > 0 ? rim : own; return [px, py, r, r]; }
  if (rim <= 0) return [px, py, own, own * SPIRO.ASPECT];
  return [(px + x) / 2, (py + y) / 2, Math.max(SPIRO.PIN, Math.abs(x - px) / 2), Math.max(SPIRO.PIN, Math.abs(y - py) / 2)];
}
// The curve on a track: the toy's wheel rolls round inside it (the epitrochoid's outside), its teeth's share of the track's.
function spiroOnTrack(g, S, f, tr) {
  g.tr = tr;
  if (S.curve !== SP_C.spiro && S.curve !== SP_C.epi) return;
  const w = clamp(SPIRO.WHEELS[S.wheel] * f[2], 12, S.curve === SP_C.spiro ? S.teeth - 8 : 120);
  g.a = tr.length * w / (2 * Math.PI * tr.teeth); g.d = clamp(S.hole * f[3], .05, .95) * g.a; g.q = tr.teeth / w + (S.curve === SP_C.epi ? 1 : -1);
}
const spiroRolls = g => !!g.tr && (g.c === SP_C.spiro || g.c === SP_C.epi);
// How far the wheel's spin turns as the pen goes on step from th0: q times as far; on a track as far as it rolls along it,
// less its line's own turning (the epitrochoid's, more).
function spiroSpin(g, th0, step) {
  if (!spiroRolls(g)) return g.q * step;
  let p = g.tr.at(th0); const s0 = p[2], n0 = p[3];
  p = g.tr.at(th0 + step);
  return (p[2] - s0) / g.a + (g.c === SP_C.epi ? 1 : -1) * (p[3] - n0);
}
function spiroRingGear(M, S) { M.A = M.base * M.f[0]; M.g = spiroGear(S, M.f, true, M.A); M.g.th0 = M.th0; if (M.tr) spiroOnTrack(M.g, S, M.f, M.tr); }
// The ring as its rim set it, at what the pen drives now; else the brush's own.
function spiroRingSet(M, S) { if (M.rim > 0) M.base = M.rim / (M.f[0] * spiroShare(S, M.f)); spiroRingGear(M, S); }
function spiroPlace(M) { const p = [0, 0, 0]; spiroAt(M.g, M.th, M.ps, M.A, M.cx, M.cy, 0, 0, true, p); M.hx = p[0]; M.hy = p[1]; M.htone = p[2]; M.was = M.g; M.wasA = M.A; M.fresh = true; }
function spiroStart(M, a) { M.on = true; M.th = a; M.th0 = a; M.g.th0 = a; M.ps = M.g.q * a; M.lastA = a; spiroPlace(M); }
// Turns the wheel on to th1 in steps of at most STEP of the pen's hole, a setting changed since the last point inked blended in.
function spiroTurn(s, buf, P, M, th1, q, r, rect) {
  const g = M.g, was = M.was, A = Math.max(M.wasA, M.A);
  let reach = Math.abs(th1 - M.th) * (A * (1 + Math.abs(g.q)) + 1);
  if (spiroRolls(g)) {
    // On a track: as far as the line goes and turns, the wheel's centre and its hole with it.
    let p = g.tr.at(M.th); const s0 = p[2], n0 = p[3];
    p = g.tr.at(th1);
    const a = Math.max(g.a, was.a), dd = Math.max(g.d, was.d);
    reach = A * (Math.abs(p[2] - s0) * (1 + dd / a) + (a + dd) * Math.abs(p[3] - n0)) + Math.abs(th1 - M.th);
  }
  const n = Math.ceil(reach / SPIRO.STEP);
  if (!(n > 0)) return;
  const dk = handDark(P.dark, q.p, q.v, 0) * (q.own === undefined ? 1 : q.own), d = q.d;
  if (M.fresh) { spiroSeg(s, buf, M.hx, M.hy, M.hx, M.hy, r, M.htone * dk, d, rect); M.fresh = false; }
  const step = (th1 - M.th) / n, p0 = [0, 0, 0], p1 = [0, 0, 0];
  for (let i = 1; i <= n; i++) {
    M.ps += spiroSpin(g, M.th, step); M.th += step;
    const u = i / n;
    spiroAt(was, M.th, M.ps, M.wasA, M.cx, M.cy, 0, 0, true, p0); spiroAt(g, M.th, M.ps, M.A, M.cx, M.cy, 0, 0, true, p1);
    const x = lerp(p0[0], p1[0], u), y = lerp(p0[1], p1[1], u);
    spiroSeg(s, buf, M.hx, M.hy, x, y, r, p1[2] * dk, d, rect);
    M.hx = x; M.hy = y; M.htone = p1[2];
  }
  M.was = g; M.wasA = M.A;
}
function spiroRing(s, buf, P, S, pts, M, k, r, rect) {
  const q = pts[k];
  // The setup's rim: no pressure, out from the pin (an Oval's or Bar's far corner, the pin its first). Nothing inked.
  if (k === 1 && q.p === 0 && (M.rim = spiroRim(M.cx, M.cy, q.x, q.y)) > 0) {
    if (M.tr) { const b = spiroBox(S.mode, M.cx, M.cy, q.x, q.y, 0); M.cx = b[0]; M.cy = b[1]; M.rim = Math.max(b[2], b[3]); M.tr = spiroTrack(S.mode === SPIRO.BAR, b[2] / M.rim, b[3] / M.rim, S.teeth); }
    return;
  }
  const dx = q.x - M.cx, dy = q.y - M.cy, dist = Math.hypot(dx, dy);
  M.far = Math.max(M.far, dist);
  if (dist < SPIRO.PIN) return;
  const ang = Math.atan2(dy, dx), dt = spiroSince(M, q);
  spiroDrive(M, S, q, dt);
  // The wheel starts where the pen first is round the pin.
  if (!M.on) { spiroRingSet(M, S); spiroStart(M, ang); M.raw = M.sm = ang; return; }
  spiroRingGear(M, S);
  let da = ang - M.lastA;
  while (da > Math.PI) da -= 2 * Math.PI; while (da < -Math.PI) da += 2 * Math.PI;
  M.lastA = ang; M.raw += da; M.sm += (M.raw - M.sm) * (1 - Math.exp(-dt / SPIRO.EASE));
  const lead = M.sm - M.th;
  if (!M.dir) { spiroPlace(M); if (Math.abs(lead) * dist < 1) return; M.dir = lead > 0 ? 1 : -1; }
  if (lead * M.dir < 0) { if (-lead * M.dir * dist < SPIRO.REV) return; M.dir = -M.dir; }
  spiroTurn(s, buf, P, M, M.sm, q, r, rect);
}
function spiroLine(s, buf, P, S, pts, M, k, r, rect) {
  const a = pts[k - 1], b = pts[k], seg = b.d - a.d, dt = spiroSince(M, b);
  spiroDrive(M, S, b, dt);
  const g = spiroGear(S, M.f, false, 0), A1 = M.base * M.f[0], rate = 2 * Math.PI * S.perCm * M.f[1] / SPIRO.PX_CM;
  let ux = b.ux, uy = b.uy;
  if (ux === undefined || Number.isNaN(ux)) { ux = seg > 0 ? (b.x - a.x) / seg : 1; uy = seg > 0 ? (b.y - a.y) / seg : 0; }
  const nx1 = -uy, ny1 = ux, p0 = [0, 0, 0], p1 = [0, 0, 0], fw = dd => pts.flick ? flickWidth(pts.flick, dd) : 1;
  if (!M.started) {
    M.started = true; M.nx = nx1; M.ny = ny1; M.was = g; M.wasA = A1;
    spiroAt(g, M.th, M.ps, A1 * fw(a.d), a.x, a.y, nx1, ny1, false, p0); M.hx = p0[0]; M.hy = p0[1]; M.htone = p0[2]; M.fresh = true;
  }
  const dth = rate * seg, A = Math.max(A1, M.wasA), n = Math.ceil((dth * A * (1 + Math.abs(g.q)) + seg) / SPIRO.STEP);
  const dk0 = handDark(P.dark, a.p, a.v, 0), dk1 = handDark(P.dark, b.p, b.v, 0), own = t => a.own === undefined ? 1 : lerp(a.own, b.own, t);
  if (M.fresh) { spiroSeg(s, buf, M.hx, M.hy, M.hx, M.hy, r, M.htone * dk0 * own(0), a.d, rect); M.fresh = false; }
  for (let i = 1; i <= n; i++) {
    const u = i / n;
    M.th += dth / n; M.ps += g.q * dth / n;
    const cx = lerp(a.x, b.x, u), cy = lerp(a.y, b.y, u), d = lerp(a.d, b.d, u);
    let nx = lerp(M.nx, nx1, u), ny = lerp(M.ny, ny1, u); const nl = Math.hypot(nx, ny);
    if (nl > 0) { nx /= nl; ny /= nl; } else { nx = nx1; ny = ny1; }
    spiroAt(M.was, M.th, M.ps, M.wasA * fw(d), cx, cy, nx, ny, false, p0); spiroAt(g, M.th, M.ps, A1 * fw(d), cx, cy, nx, ny, false, p1);
    const x = lerp(p0[0], p1[0], u), y = lerp(p0[1], p1[1], u);
    spiroSeg(s, buf, M.hx, M.hy, x, y, r, p1[2] * lerp(dk0, dk1, u) * own(u), d, rect);
    M.hx = x; M.hy = y; M.htone = p1[2];
  }
  M.nx = nx1; M.ny = ny1; M.was = g; M.wasA = A1;
}
// The curve from the samples not yet read; lifting: the ring catches up, or, never left, draws its whole figure.
function stampSpiro(st, buf, M, lifting) {
  const P = st.R.pat, S = P.spiro, s = st.surf, pts = st.pts, rect = [s.w, s.h, -1, -1];
  if (!pts.length) return rect;
  const r = spiroLineR(P.size), base = spiroRadius(S, P.size);
  if (M.k === undefined) {
    Object.assign(M, { k: 1, lifted: false, f: [1, 1, 1, 1, 1], t: pts[0].t, cx: pts[0].x, cy: pts[0].y, far: 0, th: 0, ps: 0, th0: 0, on: false, dir: 0, started: false,
      base, rim: 0, tr: S.mode >= SPIRO.OVAL ? spiroTrack(S.mode === SPIRO.BAR, 1, SPIRO.ASPECT, S.teeth) : null });
    spiroDrive(M, S, pts[0], NaN); spiroRingGear(M, S);
  }
  for (; M.k < pts.length; M.k++) (S.ring ? spiroRing : spiroLine)(s, buf, P, S, pts, M, M.k, r, rect);
  if (lifting && !M.lifted && S.ring) {
    M.lifted = true;
    const q = pts[pts.length - 1];
    if (M.on && M.dir && (M.raw - M.th) * M.dir > 0) spiroTurn(s, buf, P, M, M.raw, q, r, rect);
    if (M.far < SPIRO.PIN) { spiroRingSet(M, S); spiroStart(M, -Math.PI / 2); spiroTurn(s, buf, P, M, M.th + spiroSpan(S, M.g), q, r, rect); }
  }
  return rect;
}
const stampPatternSp = stampPattern;
stampPattern = function (st, buf, size, from, which) {
  return st.R.pat.kind === "spiro" ? stampSpiro(st, buf, st["nm" + which] ??= {}, !!st.spiroLift) : stampPatternSp(st, buf, size, from, which);
};
// The lift: a Spiro's ring catches up with the pen, or draws its whole figure (the app's ModularStroke.lastSamples).
const StrokeSp = Stroke;
Stroke = class extends StrokeSp {
  end() {
    const P = this.R.pat;
    if (P && P.kind === "spiro" && !this.fl && this.pts.length) {
      if (this.pts.length === 1) { const p = this.pts[0]; this.add([[p.x + .8, p.y, p.p, p.t + 16, p.tx, p.ty]]); }
      if (this.pts.length >= 2) { this.spiroLift = true; this.restamp(); }
    }
    return super.end();
  }
};

// The lift: a hake whose start still waits for its direction lays it now (the app's ModularStroke.lastSamples, hakeLift).
const StrokeHk = Stroke;
Stroke = class extends StrokeHk {
  end() {
    if (!this.fl && this.pts.length >= 2) {
      const waits = m => m && m.hake && m.hake.th === null;
      const line = this.R.lineTip === "hake" && waits(this.mL), body = this.R.body && this.R.body.head === "flat" && waits(this.mW);
      if (line || body) { if (line) this.mL.hake.lift = true; if (body) this.mW.hake.lift = true; this.restamp(); }
    }
    return super.end();
  }
};

// A Ring's guide (the app's SpiroStamper.guide): the pinned ring's radius at the touch-down q's pressure and tilt, as
// the stamper starts it (the epitrochoid's ring inside its wheel); 0 along the line. The test area draws it (ui.js).
function spiroGuide(R, q) {
  const P = R.pat;
  if (!P || P.kind !== "spiro" || !P.spiro.ring) return 0;
  const M = { f: [1, 1, 1, 1, 1] };
  spiroDrive(M, P.spiro, { p: q.p, v: 0, tx: q.tx, ty: q.ty }, NaN);
  return spiroRadius(P.spiro, P.size) * M.f[0] * spiroShare(P.spiro, M.f);
}

// ---------- words: "Ink spiro. Loops along the line. Pressure sets size, tilt sets hole." ----------
const SPIRO_WORDS = { line: "Loops along the line.", ring: "Loops round a ring pulled out from the start.", oval: "Loops round an oval drawn first.", bar: "Loops round a bar drawn first." };
const spiroModeWords = T => [SPIRO_WORDS.line, SPIRO_WORDS.ring, SPIRO_WORDS.oval, SPIRO_WORDS.bar][T.d.mode | 0];
// Control for a Spiro: what pressure, speed and tilt set of its shape (the app's ModularWords.driveWords).
function driveWords(B, n = 2) {
  const on = [];
  ["pressure", "speed", "tilt"].forEach((cause, i) => { const what = B.hand.d[DRIVES[i]] ?? DIALS.hand[DRIVES[i]].def; if (spiroActs(B.tip, what | 0)) on.push([cause, DRIVE_OPTS[what | 0].toLowerCase()]); });
  if (!on.length) return "steady";
  const [c1, e1] = on[0];
  if (n < 2 || on.length < 2) return c1 + " sets " + e1;
  const [c2, e2] = on[1];
  return e1 === e2 ? c1 + " and " + c2 + " set " + e1 : c1 + " sets " + e1 + ", " + c2 + " sets " + e2;
}
const handWordsSp = handWords;
handWords = function (B, n = 2) { return B.tip.k === "spiro" ? driveWords(B, n) : handWordsSp(B, n); };
const choiceWordsSp = choiceWords;
choiceWords = function (B, part) {
  if (part === "hand" && B.tip.k === "spiro") return driveWords(B);
  if (part !== "tip" || B.tip.k !== "spiro") return choiceWordsSp(B, part);
  return DIALS.tip.curve.choices[B.tip.d.curve | 0] + ((B.tip.d.mode | 0) ? " " + DIALS.tip.mode.choices[B.tip.d.mode | 0].toLowerCase() : "");
};
const plainWordsSp = plainWords;
plainWords = function (B) {
  if (B.tip.k !== "spiro") return plainWordsSp(B);
  const sent = plainWordsSp(B).split(/(?<=\.)\s+/).filter(Boolean), first = sent.shift() || "";
  // Named by what it lays, as a stamp is: "Ink spiro.", "Wash spiro on felt paper."; then where its loops go.
  const m = /^(.*?)( on .*|, .*)?\.$/.exec(first), tail = m && m[2] ? m[2] : "";
  const out = [NM_BASE[B.medium.k] ? NM_BASE[B.medium.k] + " spiro" + tail + "." : first, spiroModeWords(B.tip)];
  // Control's sentence is the drives' (handWords), last as ever.
  const hand = handWords(B, 2), said = cap1(hand) + ".", rest = sent.filter(x => x !== cap1(handWordsSp(B, 2)) + "." && x !== said);
  if (hand !== "steady") rest.push(said);
  let t = out.join(" ");
  for (const x of rest) { if (t.length + 1 + x.length > 260) break; t += " " + x; }
  return t;
};
const captionItemsSp = captionItems;
captionItems = function (B) {
  if (B.tip.k !== "spiro") return captionItemsSp(B);
  const items = captionItemsSp(B).filter(t => t !== "spiro");
  items.splice(1, 0, "spiro");
  return items;
};

// ---------- checks ----------
function spiroChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 900, H = 640, P = PAPER_TONE;
  const mk = (d, hand) => normBrush({ name: "rosette", seed: 17, style: "Fineliner", tip: Object.assign(freshPart("tip", "spiro"), { d: Object.assign(freshPart("tip", "spiro").d, d || {}) }),
    medium: freshPart("medium", "ink"), carry: freshPart("carry", "off"), paper: freshPart("paper", "plain"), life: freshPart("life", "none"),
    hand: Object.assign(freshPart("hand", "steady"), { d: Object.assign(freshPart("hand", "steady").d, hand || {}) }) });
  const line = (x0, x1, y, p) => { const n = Math.ceil((x1 - x0) / 3); return Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y, p: p ?? .65, t: 1000 + 2 * k })); };
  const circle = (cx, cy, R, turns) => { const o = [{ x: cx, y: cy, p: .7, t: 1000 }]; let tm = 1000; for (let i = 1; i <= 12; i++) o.push({ x: cx + R * i / 12, y: cy, p: .7, t: tm += 12 });
    for (let i = 1; i <= turns * 120; i++) { const a = i / 120 * 2 * Math.PI; o.push({ x: cx + R * Math.cos(a), y: cy + R * Math.sin(a), p: .7, t: tm += 8 }); } return o; };
  const draw = (B, pts) => { const s = modSurface(W, H, 7); layStroke(s, B, pts, SPIRO.SIZE, 1, null); return s; };
  const box = s => { let x0 = W, y0 = H, x1 = -1, y1 = -1, n = 0; for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (s.page[y * W + x] < P - 10) { n++; x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); } return [x0, y0, x1, y1, n]; };
  // Codes: Tip choice 11, the drives last of the hand after the app's Tilt Range and page tilt; at their defaults, nothing.
  { const B = mk({ mode: 1, curve: 2, petals: 7 }, { sdrive: 4 }), c = encodeM(B), back = readAnyCode(c).b, hand = encodeM(mk()).split(".")[1].split("_")[5];
    t("a Spiro's code (" + c + ") is Tip choice 11 and reads back, drives and all; at their defaults the hand writes as before (" + hand + ")",
      CHOICES.tip.findIndex(ch => ch.k === "spiro") === 11 && encodeM(back) === c && back.tip.d.petals === 7 && back.hand.d.sdrive === 4 && hand.length === 22
      && HAND_DIALS.slice(11, 18).join(" ") === "trange gs gd gw pdrive sdrive tdrive"); }
  // Along the line: within its width, Loops per cm setting how often it turns; pressure sets size.
  { const s = draw(mk(), line(100, 800, 320)), b = box(s), A = SPIRO.SIZE / 2, light = box(draw(mk(), line(100, 800, 320, .15))), firm = box(draw(mk(), line(100, 800, 320, 1)));
    t("along the line within its width (rows " + b[1] + " to " + b[3] + "); pressure sets size (light " + (light[3] - light[1]) + " px across, firm " + (firm[3] - firm[1]) + ")",
      b[4] > 3000 && b[1] >= 320 - 1.3 * A - 4 && b[3] <= 320 + 1.3 * A + 4 && firm[3] - firm[1] > 1.6 * (light[3] - light[1])); }
  // Ring: the rosette round the pin, within the ring; a tap draws the whole figure.
  { const R = SPIRO.SIZE / 2 * SPIRO.RING, s = draw(mk({ mode: 1 }), circle(450, 320, 150, 5)), b = box(s), tap = box(draw(mk({ mode: 1 }), [{ x: 450, y: 320, p: .7, t: 1000 }]));
    t("a ring's rosette round the pin (" + b.slice(0, 4).join(", ") + "); a tap draws the whole figure (" + tap[4] + " px, " + (tap[2] - tap[0] + 1) + " across)",
      b[4] > 5000 && b[0] >= 450 - 1.3 * R - 4 && b[2] <= 450 + 1.3 * R + 4 && tap[4] > 8000 && tap[2] - tap[0] > 1.2 * R && Math.abs((tap[0] + tap[2]) / 2 - 450) < 8); }
  // The setup's rim sizes the ring (a second sample with no pressure, 120 px out), and sets an Oval's or Bar's box corner
  // to corner: the rose reaches the ring, the toy's wheel rolls round inside the track.
  const setup = (cx, cy, rx, ry, R, turns) => { const o = circle(cx, cy, R, turns).slice(13); return [{ x: cx, y: cy, p: .7, t: 990 }, { x: rx, y: ry, p: 0, t: 995 }, ...o]; };
  { const b = box(draw(mk({ mode: 1, curve: 2 }), setup(450, 320, 570, 320, 120, 1.5)));
    const tracks = [2, 3].map(mode => { const pts = setup(300, 200, 600, 400, 150, 3).map((q, i) => i ? Object.assign(q, { x: q.x + (i > 1 ? 150 : 0), y: q.y + (i > 1 ? 100 : 0) }) : q); return box(draw(mk({ mode }), pts)); });
    const inside = b => b[4] > 2000 && b[0] >= 300 - 3 && b[2] <= 600 + 3 && b[1] >= 200 - 3 && b[3] <= 400 + 3 && b[2] - b[0] > 200;
    t("the rim sizes the ring: a rose set to 120 px reaches " + (b[2] + 1 - 450) + " px; an Oval and a Bar set corner to corner roll inside their boxes (" + tracks.map(b => b.slice(0, 4).join(",")).join("; ") + ")",
      Math.abs(b[2] + 1 - 450 - 120) < 5 && tracks.every(inside)); }
  // Every curve draws in every mode.
  { const bad = []; for (let c = 0; c < 6; c++) for (let mode = 0; mode < 4; mode++) if (box(draw(mk({ mode, curve: c }), mode ? circle(450, 320, 140, 3) : line(100, 800, 320)))[4] < 1500) bad.push(c + "/" + mode);
    t("every curve draws in every mode" + (bad.length ? ": not " + bad.join(", ") : ""), !bad.length); }
  // Words, the dials each curve shows, never rolled.
  { const B = mk(), rose = mk({ mode: 1, curve: 2 }), roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).tip.k === "spiro") return false; return true; };
    t("words: \"" + plainWords(B) + "\", \"" + plainWords(rose) + "\"; shows the toy's dials; never rolled",
      plainWords(B).startsWith("Ink spiro. " + SPIRO_WORDS.line) && plainWords(B).endsWith("Pressure sets size, tilt sets hole.") && plainWords(rose).endsWith("Pressure sets size.")
      && captionItems(B).includes("spiro") && choiceWords(rose, "tip") === "Rose ring" && dialShown(B, "tip", "wheel") && !dialShown(B, "tip", "petals") && dialShown(rose, "tip", "petals")
      && !dialShown(rose, "tip", "loops") && dialShown(B, "hand", "pdrive") && !dialShown(normBrush(Object.assign(cloneBrush(B), { tip: freshPart("tip", "round") })), "hand", "pdrive") && roll()); }
  return out;
}
const selfTestSp = selfTest;
selfTest = function () { const r = selfTestSp(); r.spiro = spiroChecks(); r.ok = r.ok && r.spiro.every(c => c.ok); NM.lastSpiro = r.spiro; return r; };
if (typeof runChecks === "function") {
  const runChecksSp = runChecks;
  runChecks = function () {
    runChecksSp();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastSpiro) return;
      out.textContent += "\n\nSpiro:\n" + NM.lastSpiro.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 160);
  };
}

// ===================================================================================================================
// Grey Shift (mischief-lab-2.html's Shifting swatches, section 5; the owner's "unpredictable swatch selection on pen
// touch"; the app's SwatchPicker): a brush with it on draws each touch in a grey picked at the touch from a set, by its
// rule: Random (repeats allowed), From a Set (a shuffled deck, each grey once before any again, never one twice running
// across a reshuffle) or Pattern, In Order or Ping-Pong. Greys: Toolbar (the test area's shade swatches here, the
// app's four grey swatches elsewhere) or Any (the panel's 16, paper white to black). Each palette keeps its own set as
// the greys left out, a bit each: sbar, and Any's sany (its first eight, paper white out at first) and sany2 (hidden,
// as the app shows them as swatches). The hand's last dials, after a Spiro's drives; written only when on. Never rolled.
// ===================================================================================================================
const SHIFT = { OFF: 0, RANDOM: 1, DECK: 2, PATTERN: 3, PING_PONG: 1, ANY: 1, LEVELS: 16,
  // The app's grey swatches at first (ToolSwatches.GREYS): black, then InkWashShades' 60, 120 and 175.
  GREYS: [1, 1 - 60 / 255, 1 - 120 / 255, 1 - 175 / 255],
  WORDS: { random: "Greys shift each stroke at random.", deck: "Greys shift each stroke, shuffled.", order: "Greys shift each stroke, in order.", pingpong: "Greys shift each stroke, there and back." } };
Object.assign(DIALS.hand, {
  shift: { label: "Grey Shift", choices: ["Off", "Random", "From a Set", "Pattern"], def: 0 },
  sorder: { label: "Order", choices: ["In Order", "Ping-Pong"], def: 0 },
  sgreys: { label: "Greys", choices: ["Toolbar", "Any"], def: 0 },
  sbar: { label: "Toolbar Greys", min: 0, max: 15, def: 0, int: true, hide: true },
  sany: { label: "Any Greys", min: 0, max: 255, def: 1, int: true, hide: true },
  sany2: { label: "Any Greys", min: 0, max: 255, def: 0, int: true, hide: true }
});
const SHIFTS = ["shift", "sorder", "sgreys", "sbar", "sany", "sany2"];
HAND_DIALS.push(...SHIFTS);
for (const k of SHIFTS) ADDED.add("hand." + k);
KEEPS.hand.push(...SHIFTS);
const shiftH = (B, k) => B.hand.d[k] ?? DIALS.hand[k].def;
const shiftRule = B => shiftH(B, "shift") | 0;
const shiftPalette = B => shiftH(B, "sgreys") | 0;
// How many greys B's palette offers: the toolbar's (`bar`, the app's four without it), or the panel's 16.
const shiftSize = (B, bar) => shiftPalette(B) === SHIFT.ANY ? SHIFT.LEVELS : (bar || SHIFT.GREYS).length;
// The greys of B's palette left out of its set, a bit each.
const shiftOut = B => shiftPalette(B) === SHIFT.ANY ? (Math.round(shiftH(B, "sany")) & 255) | (Math.round(shiftH(B, "sany2")) & 255) << 8 : Math.round(shiftH(B, "sbar")) & 15;
// Whether grey k is in B's set; with every grey left out, all are.
function shiftIn(B, k, bar) { const all = (1 << shiftSize(B, bar)) - 1, out = shiftOut(B); return (out & all) === all || !(out >> k & 1); }
const shiftSet = (B, bar) => Array.from({ length: shiftSize(B, bar) }, (_, k) => k).filter(k => shiftIn(B, k, bar));
// Grey k taken into B's set, or out of it (B changed); false when that would empty the set.
function shiftToggle(B, k, bar) {
  const all = (1 << shiftSize(B, bar)) - 1;
  let out = shiftOut(B);
  if ((out & all) === all) out = 0;
  const next = out ^ 1 << k;
  if ((next & all) === all) return false;
  if (shiftPalette(B) === SHIFT.ANY) { B.hand.d.sany = next & 255; B.hand.d.sany2 = next >> 8 & 255; } else B.hand.d.sbar = next;
  return true;
}
// A turn: the last grey picked, Pattern's step, what is left of the deck; begun again when the rule, order or set changes.
const shiftTurn = () => ({ last: -1, step: -1, deck: [], shape: null, random: () => Math.random() });
// The grey B picks at this touch, an index into its palette, the turn moving on; -1 when it is off (the app's SwatchPicker.pick).
function shiftPick(B, st, bar) {
  const rule = shiftRule(B);
  if (rule === SHIFT.OFF) return -1;
  const order = shiftH(B, "sorder") | 0, on = shiftSet(B, bar), n = on.length, shape = [rule, order, shiftPalette(B), shiftOut(B), n].join();
  if (shape !== st.shape) { st.shape = shape; st.step = -1; st.deck = []; }
  let k;
  if (rule === SHIFT.RANDOM) k = on[(st.random() * n) | 0];
  else if (rule === SHIFT.DECK) {
    if (!st.deck.length) {
      const d = st.deck = on.slice();
      for (let i = n - 1; i > 0; i--) { const j = (st.random() * (i + 1)) | 0; [d[i], d[j]] = [d[j], d[i]]; }
      // Never the same grey twice across a reshuffle: the deck is dealt from its end.
      if (n > 1 && d[n - 1] === st.last) [d[0], d[n - 1]] = [d[n - 1], d[0]];
    }
    k = st.deck.pop();
  } else {
    const period = order === SHIFT.PING_PONG ? Math.max(1, 2 * n - 2) : n;
    st.step = (st.step + 1) % period;
    k = on[st.step < n ? st.step : period - st.step];
  }
  st.last = k;
  return k;
}
// The darkness grey k of B's palette stands for: the toolbar's swatch, or Any's level k / 15.
const shiftDark = (B, k, bar) => shiftPalette(B) === SHIFT.ANY ? k / (SHIFT.LEVELS - 1) : (bar || SHIFT.GREYS)[k];
// The lab's test area: the shade a touch of B draws in ({ shade, k }: the grey picked, -1 for none), `shade` when it doesn't shift.
NM.shiftTurn = shiftTurn();
function shiftShade(B, shade, bar) { const k = shiftPick(B, NM.shiftTurn, bar); return { shade: k < 0 ? shade : shiftDark(B, k, bar), k }; }
// The Control card's Grey Shift rows (the app's BrushLab.handRows): its rule, a Pattern's Order, its Greys and its set.
const shiftRows = B => ["shift", ...(shiftRule(B) === SHIFT.PATTERN ? ["sorder"] : []), ...(shiftRule(B) ? ["sgreys", "set"] : [])];
// The set's swatches (the app's GreySet): in the set a full square with an ink edge, else small with a grey edge; a tap toggles.
function greySetHtml(B, bar, locked) {
  const n = shiftSize(B, bar);
  return `<div class="drow" style="gap:4px">` + Array.from({ length: n }, (_, k) => {
    const g = Math.round(255 * (1 - shiftDark(B, k, bar))), on = shiftIn(B, k, bar);
    return `<button data-act="greyset" data-i="${k}" aria-pressed="${on}" aria-label="${Math.round(100 * shiftDark(B, k, bar))}% grey${on ? "" : ", left out"}" ${locked ? "disabled" : ""}
      style="flex:${n > 4 ? "1 1 0" : "0 0 44px"};min-width:0;height:30px;padding:${on ? 2 : 8}px;border:2px solid ${on ? "#000" : "#888"};background:#fff"><span style="display:block;width:100%;height:100%;background:rgb(${g},${g},${g})"></span></button>`;
  }).join("") + `</div>`;
}
const dialShownGs = dialShown;
dialShown = function (B, part, key) {
  if (part === "hand" && key === "sorder") return shiftRule(B) === SHIFT.PATTERN;
  if (part === "hand" && key === "sgreys") return shiftRule(B) !== SHIFT.OFF;
  return dialShownGs(B, part, key);
};

// ---------- words: "Greys shift each stroke at random." after what the brush is (the app's SwatchPicker.words) ----------
function shiftWords(B) {
  const rule = shiftRule(B);
  return rule === SHIFT.RANDOM ? SHIFT.WORDS.random : rule === SHIFT.DECK ? SHIFT.WORDS.deck
    : rule === SHIFT.PATTERN ? ((shiftH(B, "sorder") | 0) === SHIFT.PING_PONG ? SHIFT.WORDS.pingpong : SHIFT.WORDS.order) : null;
}
// What comes before it, as the app's ModularWords: the noun, then Build-up's, a Stamp's, a Crayon's and a Spiro's own.
const SHIFT_AFTER = new Set(["Builds up where it lingers.", "Stamps a shape cut from the page.", "Fades as it prints.", "Builds up, resists washes.", "Builds up.",
  "Scrapes back to white.", SPIRO_WORDS.line, SPIRO_WORDS.ring]);
const plainWordsGs = plainWords;
plainWords = function (B) {
  const said = shiftWords(B), text = plainWordsGs(B);
  if (!said) return text;
  const all = text.split(/(?<=\.)\s+/).filter(Boolean);
  let i = 1;
  while (i < all.length && SHIFT_AFTER.has(all[i])) i++;
  all.splice(i, 0, said);
  let t2 = all[0];
  for (let j = 1; j < all.length; j++) { if (j >= 2 && t2.length + 1 + all[j].length > 260) break; t2 += " " + all[j]; }
  return t2;
};

// ---------- checks ----------
function shiftChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok });
  const mk = hand => normBrush({ name: "shifty", seed: 17, style: "Fineliner", tip: freshPart("tip", "round"), medium: freshPart("medium", "ink"), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: Object.assign(freshPart("hand", "steady"), { d: Object.assign(freshPart("hand", "steady").d, hand || {}) }) });
  const picks = (B, n, bar) => { const st = shiftTurn(); let s = 7; st.random = () => ((s = Math.imul(s ^ (s >>> 15), 0x2c1b3c6d) + 0x6d2b79f5 >>> 0) / 4294967296); return Array.from({ length: n }, () => shiftPick(B, st, bar)); };
  // Codes: the hand's last dials, after a Spiro's drives; off, nothing.
  { const B = mk({ shift: 3, sorder: 1, sgreys: 1, sany: 0x81, sany2: 0xc0 }), c = encodeM(B), back = readAnyCode(c).b, hand = encodeM(mk()).split(".")[1].split("_")[5];
    t("a code with Grey Shift (" + c + ") reads back, its set too; off, the hand writes as before (" + hand + ")",
      encodeM(back) === c && shiftSet(back).join() === shiftSet(B).join() && hand.length === 22 && HAND_DIALS.slice(HAND_DIALS.indexOf("shift"), HAND_DIALS.indexOf("shift") + 6).join(" ") === "shift sorder sgreys sbar sany sany2"
      && encodeM(mk({ shift: 1 })).split(".")[1].split("_")[5].length === 22 + 11); }
  // The rules.
  { const r = picks(mk({ shift: 1, sbar: 5 }), 200), deck = picks(mk({ shift: 2, sgreys: 1 }), 15 * 20), set = shiftSet(mk({ shift: 2, sgreys: 1 }));
    let dealt = true, twice = false;
    for (let d = 0; d < 20; d++) dealt = dealt && new Set(deck.slice(d * 15, d * 15 + 15)).size === 15;
    for (let i = 1; i < deck.length; i++) twice = twice || deck[i] === deck[i - 1];
    t("Random picks from the set only; From a Set deals each of Any's 15 (no paper white) once before any again, never one twice running",
      r.every(k => k === 1 || k === 3) && r.includes(1) && r.includes(3) && set.length === 15 && set[0] === 1 && dealt && !twice); }
  t("Pattern: In Order 0 1 2 3 0, Ping-Pong 0 1 2 3 2 1 0, over the set only",
    picks(mk({ shift: 3 }), 5).join() === "0,1,2,3,0" && picks(mk({ shift: 3, sorder: 1 }), 7).join() === "0,1,2,3,2,1,0" && picks(mk({ shift: 3, sbar: 2 }), 4).join() === "0,2,3,0");
  { const B = mk({ shift: 1 }), set = B => shiftSet(B).length;
    t("each palette its own set, the last grey stays; off picks nothing; Toolbar's greys the bar's",
      shiftToggle(B, 0) && shiftToggle(B, 1) && shiftToggle(B, 2) && !shiftToggle(B, 3) && set(B) === 1 && B.hand.d.sany === 1 && picks(mk(), 1)[0] === -1
      && shiftDark(B, 2, [.2, .5, .9]) === .9 && shiftSize(B, [.2, .5, .9]) === 3); }
  // Words, never rolled.
  { const roll = () => { for (let i = 0; i < 1400; i++) if (shiftRule(rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]))) return false; return true; };
    t("words: \"" + plainWords(mk({ shift: 1 })) + "\"; never rolled",
      plainWords(mk({ shift: 1 })).includes(SHIFT.WORDS.random) && plainWords(mk({ shift: 3, sorder: 1 })).includes(SHIFT.WORDS.pingpong) && !plainWords(mk()).includes("Greys")
      && dialShown(mk({ shift: 3 }), "hand", "sorder") && !dialShown(mk({ shift: 1 }), "hand", "sorder") && !dialShown(mk(), "hand", "sgreys") && roll()); }
  return out;
}
const selfTestGs = selfTest;
selfTest = function () { const r = selfTestGs(); r.shift = shiftChecks(); r.ok = r.ok && r.shift.every(c => c.ok); NM.lastShift = r.shift; return r; };
if (typeof runChecks === "function") {
  const runChecksGs = runChecks;
  runChecks = function () {
    runChecksGs();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastShift) return;
      out.textContent += "\n\nGrey Shift:\n" + NM.lastShift.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 180);
  };
}

// ===================================================================================================================
// Stay in Lines (mischief-lab-2.html's first pad, "Stay in the lines"; spec its NOTES "## 1. Stay in the lines"; the
// app's PartChoice.STAY_IN and StayIn): Smudge choice 5, Flood. Wet paint treats the crisp ink lines as walls, like a
// colouring book: the stroke keeps to the shape it starts in and floods on through it past the brush, 220 × flood² px
// (0 the brush alone, 1 the whole shape, "Fills"), about 3 px a frame by pen time. Only the ink layer's marks make walls:
// here, what strokes of an ink-layer medium laid (STAY.inks: ink, charcoal, graphite, resist, screentone, crayon), kept
// in the surface's walls plane; a wash or any soft mark never holds it. Its walls, margin, line under the edge and
// pockets are the prototype's walls1 and room1; open paper (a shape reaching the page's edge) is no shape, and the
// stroke a plain wash. Never rolled, never a variation's swap.
// ===================================================================================================================
DIALS.carry.flood = { label: "Flood", min: 0, max: 1, def: .35, fmt: v => v < .005 ? "Brush" : v > .995 ? "Fills" : Math.max(1, Math.round(220 * v * v * 1.875 * 25.4 / 300)) + " mm" };
CHOICES.carry.push({ k: "stayin", label: "Stay in Lines", dials: ["flood"], macro: "flood", words: ["brush", "fills"], blurb: "Keeps inside crisp ink lines, like a colouring book, and floods through them." });
// Filled Shape (the app's PartChoice.SHAPE and FilledShape, 2026-10-23): Smudge choice 6, no dials. In the app the
// outline closes at the lift and the area inside fills with the same brush (its FillTool, which has no twin here), so
// the lab and Studio say it and carry its code, and their pictures draw the outline alone. Never rolled or swapped to.
CHOICES.carry.push({ k: "shape", label: "Filled Shape", dials: [], blurb: "Closes its outline at the lift and fills the shape with the same paint." });
const STAY = { inks: new Set(["ink", "charcoal", "graphite", "resist", "tone", "crayon"]), LINE: .4, GEO_K: 14, GEO_MIN: 20, BOX: 6, RATE: 9 / (1000 / 60), UNTIMED: 9,
  BUDGET: 2000, ENDLESS: 1e9, MEET: 12, RIM: 7, RIM_FROM: 12, FADE_ALL: 1800 };
STAY.reach = flood => flood > .995 ? STAY.ENDLESS : Math.round(3 * 220 * flood * flood);
// A variation never swaps to it, and no roll picks it (the app's ModularFeatures.seedOnly).
const makeVarsLines = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.carry.slice();
  CHOICES.carry = keep.filter(c => (c.k !== "stayin" && c.k !== "shape") || c.k === B.carry.k);
  try { return makeVarsLines(B, locks); } finally { CHOICES.carry = keep; }
};
// Resolve: no smudge (it moves no paint along); its Flood, none on water. Filled Shape smudges nothing either.
const resolveLines = resolve;
resolve = function (B, size, shade) {
  if (B.carry.k !== "stayin" && B.carry.k !== "shape") return resolveLines(B, size, shade);
  const A = cloneBrush(B); A.carry = freshPart("carry", "off");
  const R = resolveLines(A, size, shade);
  if (B.carry.k === "shape") return R;
  if (R.life !== "float" && (R.line || R.body)) R.stayIn = B.carry.d.flood ?? .35;
  return R;
};

// ---------- the walls plane: what ink-layer strokes laid, the eraser takes back, a clear page clears ----------
const blankPageLines = blankPage;
blankPage = function (s) { blankPageLines(s); if (s.walls) s.walls.fill(0); };
const EraserLines = Eraser;
Eraser = class extends EraserLines {
  disc(cx, cy, rad, ...rest) {
    const r = super.disc(cx, cy, rad, ...rest), s = this.s, wl = s.walls;
    if (wl) {
      const R = Math.ceil(rad) + 2, x0 = Math.max(0, Math.floor(cx - R)), x1 = Math.min(s.w - 1, Math.ceil(cx + R)), y0 = Math.max(0, Math.floor(cy - R)), y1 = Math.min(s.h - 1, Math.ceil(cy + R));
      for (let y = y0; y <= y1; y++) for (let i = y * s.w + x0, e = y * s.w + x1; i <= e; i++) if (wl[i] > 0) wl[i] = Math.min(wl[i], clamp(1 - s.page[i] / PAPER_TONE));
    }
    return r;
  }
};

// ---------- the shape: the prototype's edge1, walls1 and room1 on the walls plane ----------
// Each pixel's distance to the nearest line pixel out to 6 px (Infinity beyond), and that pixel.
function stayEdges(w, h, ink) {
  const n = w * h, B = STAY.BOX, BW = 2 * B + 1, e = new Float32Array(n).fill(Infinity), near = new Int32Array(n).fill(-1);
  for (let i = 0; i < n; i++) {
    if (ink[i] <= STAY.LINE) continue;
    e[i] = 0; near[i] = i;
    const x = i % w, y = (i - x) / w;
    if (x > 0 && x < w - 1 && y > 0 && y < h - 1 && ink[i - 1] > STAY.LINE && ink[i + 1] > STAY.LINE && ink[i - w] > STAY.LINE && ink[i + w] > STAY.LINE) continue;
    for (let yy = Math.max(0, y - B); yy <= Math.min(h - 1, y + B); yy++) for (let xx = Math.max(0, x - B); xx <= Math.min(w - 1, x + B); xx++) {
      const j = yy * w + xx, d = Math.hypot(xx - x, yy - y);
      if (d < e[j]) { e[j] = d; near[j] = i; }
    }
  }
  return { e, near };
}
function stayDilate(m, w, h) {
  const t = new Uint8Array(w * h), o = new Uint8Array(w * h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * w + x; t[i] = m[i] | (x > 0 ? m[i - 1] : 0) | (x < w - 1 ? m[i + 1] : 0); }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const i = y * w + x; o[i] = t[i] | (y > 0 ? t[i - w] : 0) | (y < h - 1 ? t[i + w] : 0); }
  return o;
}
// The walls: line pixels, thickened 2 px only where the ink near by is two pieces or one that meets only a long way round.
function stayWalls(w, h, ink, near) {
  const n = w * h, B = STAY.BOX, BW = 2 * B + 1, core = new Uint8Array(n);
  for (let i = 0; i < n; i++) core[i] = ink[i] > STAY.LINE ? 1 : 0;
  const piece = new Int32Array(n), q = new Int32Array(n);
  for (let s = 0, np = 0; s < n; s++) {
    if (!core[s] || piece[s]) continue;
    piece[s] = ++np; let head = 0, tail = 0; q[tail++] = s;
    while (head < tail) {
      const i = q[head++], x = i % w, y = (i - x) / w;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
        const j = yy * w + xx;
        if (core[j] && !piece[j]) { piece[j] = np; q[tail++] = j; }
      }
    }
  }
  const band = stayDilate(stayDilate(core, w, h), w, h), wall = core.slice();
  const loc = new Int32Array(BW * BW), wq = new Int32Array(BW * BW), seen = new Int32Array(n), lev = new Int32Array(n);
  const repI = new Int32Array(BW * BW), repD = new Float32Array(BW * BW), want = new Int32Array(BW * BW);
  let gen = 0;
  for (let p = 0; p < n; p++) {
    if (!band[p] || core[p]) continue;
    const px = p % w, py = (p - px) / w, c = near[p], nx = c % w - px, ny = (c - c % w) / w - py, nl = Math.hypot(nx, ny);
    const x0 = Math.max(0, px - B), x1 = Math.min(w - 1, px + B), y0 = Math.max(0, py - B), y1 = Math.min(h - 1, py + B);
    let first = 0, two = false, far = false;
    for (let y = y0; y <= y1 && !two; y++) for (let x = x0; x <= x1; x++) {
      const k = piece[y * w + x];
      if (!k) continue;
      if (k !== first) { if (!first) first = k; else { two = true; break; } }
      const dx = x - px, dy = y - py;
      if (!far && dx * nx + dy * ny < -.2 * nl * Math.hypot(dx, dy)) far = true;
    }
    if (two) { wall[p] = 1; continue; }
    if (!far) continue;
    loc.fill(0); let parts = 0;
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) {
      const c0 = (y - y0) * BW + (x - x0);
      if (loc[c0] || !core[y * w + x]) continue;
      const id = ++parts; let head = 0, tail = 0, best = -1, bd = Infinity;
      loc[c0] = id; wq[tail++] = c0;
      while (head < tail) {
        const cc = wq[head++], cx = cc % BW, cy = (cc - cx) / BW, gx = x0 + cx, gy = y0 + cy, d = Math.hypot(gx - px, gy - py);
        if (d < bd) { bd = d; best = gy * w + gx; }
        for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
          const ax = gx + dx, ay = gy + dy;
          if (ax < x0 || ay < y0 || ax > x1 || ay > y1) continue;
          const c2 = (ay - y0) * BW + (ax - x0);
          if (!loc[c2] && core[ay * w + ax]) { loc[c2] = id; wq[tail++] = c2; }
        }
      }
      repI[id - 1] = best; repD[id - 1] = bd;
    }
    if (parts < 2) continue;
    let src = 0;
    for (let k = 1; k < parts; k++) if (repD[k] < repD[src]) src = k;
    let lim = 0, left = parts - 1;
    for (let k = 0; k < parts; k++) { want[k] = k === src ? -1 : Math.max(STAY.GEO_MIN, Math.ceil(STAY.GEO_K * (repD[src] + repD[k]))); lim = Math.max(lim, want[k]); }
    gen++; let head = 0, tail = 0; q[tail++] = repI[src]; seen[repI[src]] = gen; lev[repI[src]] = 0;
    while (head < tail && left) {
      const i = q[head++], li = lev[i];
      for (let k = 0; k < parts; k++) if (want[k] >= 0 && repI[k] === i) { want[k] = -1; left--; }
      if (li >= lim) continue;
      const x = i % w, y = (i - x) / w;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const xx = x + dx, yy = y + dy;
        if (xx < 0 || yy < 0 || xx >= w || yy >= h) continue;
        const j = yy * w + xx;
        if (core[j] && seen[j] !== gen) { seen[j] = gen; lev[j] = li + 1; q[tail++] = j; }
      }
    }
    if (left) wall[p] = 1;
  }
  return wall;
}
// The shape round x, y on a surface w × h whose walls are ink (0 to 1): { room, edge, pockets }, each pixel's kind 1
// open, 2 margin, 3 the line under the edge, 4 behind it, 5 a pocket, 0 elsewhere; null with no open pixel within 8 px,
// or open paper reaching the edge.
function stayShape(w, h, ink, x, y) {
  const n = w * h, { e: edge, near } = stayEdges(w, h, ink), wall = stayWalls(w, h, ink, near), L = STAY.LINE;
  let s = -1;
  for (let r = 0; r <= 8 && s < 0; r++) for (let dy = -r; dy <= r && s < 0; dy++) for (let dx = -r; dx <= r; dx++) {
    if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue;
    const px = Math.floor(x) + dx, py = Math.floor(y) + dy;
    if (px >= 0 && py >= 0 && px < w && py < h && !wall[py * w + px]) { s = py * w + px; break; }
  }
  if (s < 0) return null;
  const room = new Uint8Array(n), q = new Int32Array(n); let head = 0, tail = 0;
  room[s] = 1; q[tail++] = s;
  const go = j => { if (!wall[j] && !room[j]) { room[j] = 1; q[tail++] = j; } };
  while (head < tail) {
    const i = q[head++], px = i % w;
    if (px === 0 || px === w - 1 || i < w || i >= n - w) return null;
    go(i - 1); go(i + 1); go(i - w); go(i + w);
  }
  head = 0; tail = 0;
  for (let i = 0; i < n; i++) if (room[i] && edge[i] < 5) q[tail++] = i;
  const down = (i, j) => { if (!room[j] && wall[j] && ink[j] <= L && edge[j] <= edge[i]) { room[j] = 2; q[tail++] = j; } };
  while (head < tail) {
    const i = q[head++], px = i % w;
    if (px > 0) down(i, i - 1); if (px < w - 1) down(i, i + 1); if (i >= w) down(i, i - w); if (i < n - w) down(i, i + w);
  }
  for (let layer = 3; layer <= 4; layer++) {
    const add = [];
    for (let i = 0; i < n; i++) {
      if (room[i] || (ink[i] <= L && (layer === 4 || edge[i] > 1))) continue;
      const px = i % w, ok = v => layer === 3 ? v === 1 || v === 2 : v === 3;
      if ((px > 0 && ok(room[i - 1])) || (px < w - 1 && ok(room[i + 1])) || (i >= w && ok(room[i - w])) || (i < n - w && ok(room[i + w]))) add.push(i);
    }
    for (const i of add) room[i] = layer;
  }
  const pk = new Uint8Array(n), seen = new Int32Array(n), pockets = new Map(); let gen = 0;
  for (let s0 = 0; s0 < n; s0++) {
    if (pk[s0] || room[s0] || ink[s0] > L) continue;
    pk[s0] = 1; head = 0; tail = 0; q[tail++] = s0;
    let open = false, thick = 0;
    while (head < tail) {
      const i = q[head++], px = i % w;
      if (edge[i] > thick) thick = edge[i];
      const nb = j => { if (!room[j] && !pk[j] && ink[j] <= L) { pk[j] = 1; q[tail++] = j; } };
      if (px > 0) nb(i - 1); else open = true;
      if (px < w - 1) nb(i + 1); else open = true;
      if (i >= w) nb(i - w); else open = true;
      if (i < n - w) nb(i + w); else open = true;
    }
    if (open || !(tail <= 24 || (tail <= 1000 && thick <= 3.2))) continue;
    let anchor = -1; gen++;
    let ring = [];
    for (let k = 0; k < tail; k++) { ring.push(q[k]); seen[q[k]] = gen; }
    for (let step = 0; step <= 12 && anchor < 0 && ring.length; step++) {
      const next = [];
      for (const i of ring) {
        const px = i % w;
        for (const j of [px > 0 ? i - 1 : -1, px < w - 1 ? i + 1 : -1, i - w, i + w]) {
          if (j < 0 || j >= n || seen[j] === gen) continue;
          seen[j] = gen;
          const r = room[j];
          if (r >= 1 && r <= 4) { anchor = j; break; }
          if (ink[j] > L) next.push(j);
        }
        if (anchor >= 0) break;
      }
      ring = next;
    }
    if (anchor < 0) continue;
    let list = pockets.get(anchor); if (!list) pockets.set(anchor, list = []);
    for (let k = 0; k < tail; k++) { room[q[k]] = 5; list.push(q[k]); }
  }
  // The meeting the line rule's distance, in the flood's thirds of a pixel, capped as the app's.
  const e3 = new Float32Array(n);
  for (let i = 0; i < n; i++) e3[i] = Math.min(18, 3 * edge[i]);
  return { room, edge: e3, pockets };
}

// ---------- the flood: the prototype's bucket queue, by pen time ----------
class StayFlood {
  constructor(surf, shape, flood) {
    const n = surf.w * surf.h;
    Object.assign(this, { s: surf, room: shape.room, edge: shape.edge, pockets: shape.pockets, reach: STAY.reach(flood), dist: new Float64Array(n).fill(Infinity),
      at: new Int32Array(n).fill(-1), val: new Float32Array(n), queue: new Map(), queued: 0, key: 0, t0: null, tint: 0, walked: -1, next: 0, changed: [surf.w, surf.h, -1, -1] });
  }
  push(i, d, key) {
    if (d >= this.dist[i]) return;
    this.dist[i] = d; this.at[i] = key;
    let b = this.queue.get(key); if (!b) this.queue.set(key, b = []);
    b.push(i); this.queued++;
  }
  rim(x, y, r) {
    const { w, h } = this.s, put = (px, py) => { if (px < 0 || py < 0 || px >= w || py >= h) return; const i = py * w + px, k = this.room[i]; if (k && k !== 5) this.push(i, 0, this.key); };
    put(Math.floor(x), Math.floor(y));
    const m = Math.max(8, Math.ceil(2 * Math.PI * r * 1.5));
    for (let k = 0; k < m; k++) { const a = 2 * Math.PI * k / m; put(Math.floor(x + r * Math.cos(a)), Math.floor(y + r * Math.sin(a))); }
  }
  // The samples not yet walked wet their rims every .3 of radius; the flood runs on to pen time t, painting buf.
  sample(pts, t, radius, tint, buf) {
    this.tint = tint;
    const r = Math.max(1, radius), gap = Math.max(1, r * .3);
    for (let k = this.walked + 1; k < pts.length; k++, this.walked++) {
      if (k === 0) { this.rim(pts[0].x, pts[0].y, r); this.next = gap; continue; }
      const a = pts[k - 1], b = pts[k], seg = b.d - a.d;
      for (; this.next <= b.d; this.next += gap) { const u = seg > 0 ? (this.next - a.d) / seg : 1; this.rim(a.x + (b.x - a.x) * u, a.y + (b.y - a.y) * u, r); }
    }
    if (this.t0 === null && Number.isFinite(t)) this.t0 = t;
    const target = Number.isFinite(t) && this.t0 !== null ? (t - this.t0) * STAY.RATE : this.key + STAY.UNTIMED;
    this.run(target, STAY.BUDGET, buf);
    return this.took();
  }
  finish(buf) { this.run(Infinity, Infinity, buf); return this.took(); }
  run(target, most, buf) {
    let work = 0;
    while (this.queued && this.key < target && work < most) {
      const b = this.queue.get(this.key);
      if (b) { this.queue.delete(this.key); this.queued -= b.length; for (const i of b) { this.settle(i, this.key, buf); work++; } }
      this.key++;
    }
    if (!this.queued && target !== Infinity && this.key < target) this.key = Math.floor(target);
  }
  took() { const c = this.changed; this.changed = [this.s.w, this.s.h, -1, -1]; return c[2] < c[0] ? null : c; }
  wet(i, d, me) {
    const R = this.reach, u = R >= STAY.ENDLESS ? Math.min(1, d / STAY.FADE_ALL) : R ? Math.min(1, d / R) : 0;
    let v = this.tint * (1 - .4 * u * u) * (.88 + .24 * this.s.blob[i]);
    if (me >= 2) v *= 1.3;
    else if (R < STAY.ENDLESS && R > STAY.RIM_FROM && R - d < STAY.RIM) v *= 1.22;
    return Math.min(1, v);
  }
  settle(i, key, buf) {
    if (this.at[i] !== key) return;
    const { w, h } = this.s, room = this.room, edge = this.edge, d = this.dist[i], x = i % w, y = (i - x) / w, me = room[i], v = this.wet(i, d, me);
    if (v > this.val[i]) { this.val[i] = v; if (v > buf[i]) { buf[i] = v; grow(this.changed, [x, y, x, y]); } }
    const pocket = this.pockets.get(i);
    if (pocket) for (const j of pocket) this.push(j, d + 3, key + 3);
    if (me >= 4) return;
    const go = (j, step) => {
      const to = room[j];
      if (!to || to === 5 || (me === 3 ? to !== 4 : to === 4)) return;
      if (d + step <= this.reach || to >= 3 || edge[j] < edge[i] || (to === 2 && edge[j] <= edge[i] && d + step <= this.reach + STAY.MEET)) this.push(j, d + step, key + step);
    };
    if (x > 0) go(i - 1, 3); if (x < w - 1) go(i + 1, 3); if (y > 0) go(i - w, 3); if (y < h - 1) go(i + w, 3);
    if (me === 3 || d >= this.reach) return;
    const open = j => room[j] === 1 || room[j] === 2;
    const l = x > 0 && open(i - 1), r = x < w - 1 && open(i + 1), u = y > 0 && open(i - w), dn = y < h - 1 && open(i + w);
    if (l && u && open(i - w - 1)) go(i - w - 1, 4); if (r && u && open(i - w + 1)) go(i - w + 1, 4);
    if (l && dn && open(i + w - 1)) go(i + w - 1, 4); if (r && dn && open(i + w + 1)) go(i + w + 1, 4);
  }
  // Buffers kept to the shape over rect; the flood laid again where stronger.
  keep(buf, rect) {
    if (!buf) return;
    const s = this.s, x0 = Math.max(0, rect[0]), x1 = Math.min(s.w - 1, rect[2]);
    for (let y = Math.max(0, rect[1]); y <= Math.min(s.h - 1, rect[3]); y++) for (let i = y * s.w + x0, e = y * s.w + x1; i <= e; i++) if (!this.room[i] && buf[i]) buf[i] = 0;
  }
  relay(buf, rect) {
    const s = this.s, x0 = Math.max(0, rect[0]), x1 = Math.min(s.w - 1, rect[2]);
    for (let y = Math.max(0, rect[1]); y <= Math.min(s.h - 1, rect[3]); y++) for (let i = y * s.w + x0, e = y * s.w + x1; i <= e; i++) if (this.val[i] > buf[i]) buf[i] = this.val[i];
  }
}

// ---------- the stroke: its shape at the first sample, kept to it, flooding; an ink-layer stroke's walls ----------
let stayLifting = null;
const composeLines = composeStroke;
composeStroke = function (st, rect, phase) {
  const f = st.stay;
  if (f) { const s = st.surf; f.keep(s.L, rect); f.keep(s.W, rect); f.keep(s.D, rect); f.keep(s.relief, rect); if (st.R.life === "wet" && st.R.body) f.relay(s.D, rect); }
  return composeLines(st, rect, phase);
};
// A Slow lift settles the body laid afresh with the flood: kept in, so its settling after lift stays in too.
for (const name of ["settleBody", "settleBlend"]) {
  const prev = globalThis[name] || (name === "settleBody" ? settleBody : settleBlend);
  const wrapped = function (...a) { const r = prev(...a), st = stayLifting; if (st && st.stay && r) { st.stay.keep(st.surf.D, r); st.stay.keep(st.surf.relief, r); } return r; };
  if (name === "settleBody") settleBody = wrapped; else settleBlend = wrapped;
}
const StrokeLines = Stroke;
Stroke = class extends StrokeLines {
  constructor(surf, B, size, shade, host) {
    super(surf, B, size, shade, host);
    this.inks = STAY.inks.has(B.medium.k);
  }
  stayBuf() { return this.R.life === "wet" ? this.surf.D : this.surf.W; }
  stayTint() {
    const b = this.R.body, q = this.pts[this.pts.length - 1];
    return b && q ? (b.mode === "wet" ? lerp(.7, 1, q.p) : 1) * (b.amount ?? 1) * handDark(b, q.p, q.v || 0, 0) : 0;
  }
  stayFlood(t) {
    const f = this.stay;
    if (!f || !this.R.body) return null;
    const r = t === Infinity ? (f.sample(this.pts, NaN, .45 * this.R.bodySize, this.stayTint(), this.stayBuf()), f.finish(this.stayBuf()))
      : f.sample(this.pts, t, .45 * this.R.bodySize, this.stayTint(), this.stayBuf());
    if (r) { composeStroke(this, r, this.R.life === "dry" ? "preview" : "final"); grow(this.rect, r); }
    return r;
  }
  add(samples) {
    if (this.R.stayIn !== undefined && this.stayLooked === undefined && samples.length) {
      this.stayLooked = true;
      const s = this.surf, sh = s.walls ? stayShape(s.w, s.h, s.walls, samples[0][0], samples[0][1]) : null;
      if (sh) this.stay = new StayFlood(s, sh, this.R.stayIn);
    }
    const r = super.add(samples);
    if (this.stay && samples.length) { const f = this.stayFlood(samples[samples.length - 1][3]); if (f) return r ? union(r, f) : f; }
    return r;
  }
  stampW(buf, final, from) {
    const r = super.stampW(buf, final, from);
    // A Slow lift lays the body afresh: the flood with it.
    if (final && this.stay) this.stay.relay(buf, [0, 0, this.surf.w - 1, this.surf.h - 1]);
    return r;
  }
  end() {
    if (this.stay) this.stayFlood(Infinity);
    stayLifting = this;
    let r;
    try { r = super.end(); } finally { stayLifting = null; }
    // An ink-layer stroke's marks are walls from now on: how much it darkened the page as it found it.
    if (this.inks && r) {
      const s = this.surf, wl = s.walls || (s.walls = new Float32Array(s.w * s.h)), x0 = Math.max(0, r[0]), x1 = Math.min(s.w - 1, r[2]);
      for (let y = Math.max(0, r[1]); y <= Math.min(s.h - 1, r[3]); y++) for (let i = y * s.w + x0, e = y * s.w + x1; i <= e; i++) {
        const k = s.base[i] > 0 ? clamp(1 - s.page[i] / s.base[i]) : 0;
        if (k > wl[i]) wl[i] = k;
      }
    }
    return r;
  }
};

// ---------- words: "Floods inside ink lines." in the smudge's place ----------
const stayWords = B => { const f = B.carry.d.flood ?? .35; return f < .005 ? "Stays inside ink lines." : f > .995 ? "Fills the shape it starts in." : "Floods inside ink lines."; };
const choiceWordsLines = choiceWords;
choiceWords = function (B, part) { return part === "carry" && B.carry.k === "stayin" ? "Stay in" : part === "carry" && B.carry.k === "shape" ? "Shape" : choiceWordsLines(B, part); };
const plainWordsLines = plainWords;
plainWords = function (B) {
  if (B.carry.k !== "stayin" && B.carry.k !== "shape") return plainWordsLines(B);
  // As Lift says its smudge, in the same place: "Lifts paint." becomes its own.
  const A = cloneBrush(B); A.carry = freshPart("carry", "lift");
  const t = plainWordsLines(normBrush(A));
  return t.includes("Lifts paint.") ? t.replace("Lifts paint.", B.carry.k === "shape" ? "Fills the shape it outlines." : stayWords(B)) : t;
};

// ---------- checks ----------
function stayChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok });
  const mk = (flood, life) => { const B = { name: "lines", seed: 7, style: "Watercolor", tip: freshPart("tip", "round"), medium: freshPart("medium", "wash"), carry: freshPart("carry", "stayin"),
    paper: freshPart("paper", "plain"), life: freshPart("life", life || "none"), hand: freshPart("hand", "steady") }; B.carry.d.flood = flood; return normBrush(B); };
  const seg = c => c.split("_")[2];
  // Codes: Smudge choice 5, Flood two digits; they read back; older codes as before.
  { const c = encodeM(mk(.6)), back = readAnyCode(c).b;
    const olds = [...LEGACY_CODES.map(x => encodeM(readAnyCode(x).b)), OIL_PAINT];
    t("Stay in Lines' code is _" + seg(c) + "_, Smudge choice " + CHOICES.carry.findIndex(ch => ch.k === "stayin") + "; it reads back; older codes as before",
      seg(c) === "5" + two36(Math.round(.6 * M_STEPS)) && encodeM(back) === c && back.carry.k === "stayin" && CHOICES.carry.findIndex(ch => ch.k === "stayin") === 5 && olds.every(x => encodeM(readAnyCode(x).b) === x)); }
  // The prototype's ring: radius 120, a 4 px gap, drawn in ink; a tap inside at Fills fills it to the line and no farther.
  const W = 360, H = 360, ink = freshPart("medium", "ink");
  const pen = normBrush({ name: "pen", seed: 3, style: "Fineliner", tip: freshPart("tip", "round"), medium: Object.assign(ink, { d: Object.assign(ink.d, { wet: 0 }) }), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: freshPart("hand", "steady") });
  const arc = (cx, cy, r, a0, a1) => Array.from({ length: Math.ceil(Math.abs(a1 - a0) * r / 2) + 1 }, (_, k, n) => { const a = a0 + (a1 - a0) * k / (Math.ceil(Math.abs(a1 - a0) * r / 2)); return { x: cx + r * Math.cos(a), y: cy + r * Math.sin(a), p: .8, t: k * 4, tx: NaN, ty: NaN }; });
  const tap = (s, B, x, y) => layStroke(s, B, [{ x, y, p: .8, t: 0, tx: NaN, ty: NaN }], 30, .6, null);
  { const s = modSurface(W, H, 7); layStroke(s, pen, arc(180, 180, 120, -Math.PI / 2 + 4 / 120, 1.5 * Math.PI), 3, 1, null);
    const before = Float32Array.from(s.page), { st } = tap(s, mk(1), 180, 180);
    let outside = 0, dry = 0;
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = y * W + x, r = Math.hypot(x + .5 - 180, y + .5 - 180); if (r > 125 && s.page[i] !== before[i]) outside++; if (r < 115 && s.page[i] >= before[i]) dry++; }
    t("a ring with a 4 px gap holds the flood (" + outside + " px outside), Fills fills it (" + dry + " dry)", st.stay && outside === 0 && dry === 0); }
  // A 10° wedge, closed: a tap in it at Fills reaches its point.
  { const s = modSurface(W, H, 7), a = 5 * DEG, L = 300, ax = 20, ay = 180;
    const line = (x0, y0, x1, y1) => Array.from({ length: 151 }, (_, k) => ({ x: x0 + (x1 - x0) * k / 150, y: y0 + (y1 - y0) * k / 150, p: .8, t: k * 4, tx: NaN, ty: NaN }));
    layStroke(s, pen, line(ax, ay, ax + L * Math.cos(a), ay - L * Math.sin(a)), 3, 1, null); layStroke(s, pen, line(ax, ay, ax + L * Math.cos(a), ay + L * Math.sin(a)), 3, 1, null);
    layStroke(s, pen, line(ax + L * Math.cos(a) - 6, ay - L * Math.sin(a) - 6, ax + L * Math.cos(a) - 6, ay + L * Math.sin(a) + 6), 3, 1, null);
    const before = Float32Array.from(s.page), { st } = tap(s, mk(1), ax + L * .6, ay);
    let tip = Infinity, paper = Infinity;
    for (let y = ay - 20; y < ay + 20; y++) for (let x = ax; x < ax + L; x++) {
      if (Math.abs(y + .5 - ay) > (x + .5 - ax) * Math.tan(a)) continue;
      const i = y * W + x;
      if (s.page[i] < before[i]) tip = Math.min(tip, x);
      if (before[i] > PAPER_TONE * .8) paper = Math.min(paper, x);
    }
    t("a 10° wedge fills to its point: wet from x = " + tip + ", paper from x = " + paper, st.stay && tip <= paper); }
  // Flood 0 is the brush alone; on open paper a plain wash; a wash line is no wall.
  { const s = modSurface(W, H, 7); layStroke(s, pen, arc(180, 180, 120, 0, 2 * Math.PI + .05), 3, 1, null);
    const before = Float32Array.from(s.page); tap(s, mk(0), 180, 180);
    let far = 0; for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) if (s.page[y * W + x] < before[y * W + x]) far = Math.max(far, Math.hypot(x - 180, y - 180));
    const a = modSurface(W, H, 7), b = modSurface(W, H, 7), plain = mk(1); plain.carry = freshPart("carry", "off");
    tap(a, plain, 180, 180); const { st } = tap(b, mk(1), 180, 180);
    t("Flood 0 is the brush alone (wet to " + Math.round(far) + " px from a 30 px brush's centre); on open paper a plain wash", far < 30 && !st.stay && a.page.every((v, i) => v === b.page[i])); }
  // Words, rolls and swaps.
  { const B = mk(.5), roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).carry.k === "stayin") return false; return true; };
    const vars = () => { const lk = { parts: new Set(), dials: new Set() }, w = mk(.5); w.carry = freshPart("carry", "off"); for (let i = 0; i < 200; i++) if (makeVars(normBrush(w), lk).some(v => v.b.carry.k === "stayin")) return false; return true; };
    t("words: \"" + plainWords(B) + "\"; " + choiceWords(B, "carry") + "; " + captionItems(B).join(", ") + "; Flood " + fmtDial("carry", "flood", .35) + "; never rolled or swapped to",
      plainWords(B).includes("Floods inside ink lines.") && plainWords(mk(0)).includes("Stays inside ink lines.") && plainWords(mk(1)).includes("Fills the shape it starts in.")
      && choiceWords(B, "carry") === "Stay in" && captionItems(B).includes("stay in lines") && fmtDial("carry", "flood", 0) === "Brush" && fmtDial("carry", "flood", 1) === "Fills"
      && fmtDial("carry", "flood", .35) === "4 mm" && roll() && vars()); }
  // Filled Shape: Smudge choice 6, no dials, its code read back; its words; never rolled or swapped to.
  { const B = mk(.5); B.carry = freshPart("carry", "shape"); const S = normBrush(B), c = encodeM(S), back = readAnyCode(c).b;
    const roll = () => { for (let i = 0; i < 1400; i++) if (rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]).carry.k === "shape") return false; return true; };
    const vars = () => { const lk = { parts: new Set(), dials: new Set() }, w = mk(.5); w.carry = freshPart("carry", "off"); for (let i = 0; i < 200; i++) if (makeVars(normBrush(w), lk).some(v => v.b.carry.k === "shape")) return false; return true; };
    t("Filled Shape's code is _" + seg(c) + "_, Smudge choice " + CHOICES.carry.findIndex(ch => ch.k === "shape") + "; it reads back; \"" + plainWords(S) + "\"; " + choiceWords(S, "carry") + "; never rolled or swapped to",
      seg(c) === "6" && CHOICES.carry.findIndex(ch => ch.k === "shape") === 6 && encodeM(back) === c && back.carry.k === "shape" && !resolve(S, 30, 1).carry
      && plainWords(S).includes("Fills the shape it outlines.") && choiceWords(S, "carry") === "Shape" && captionItems(S).includes("filled shape") && roll() && vars()); }
  return out;
}
const selfTestLines = selfTest;
selfTest = function () { const r = selfTestLines(); r.stayin = stayChecks(); r.ok = r.ok && r.stayin.every(c => c.ok); NM.lastStay = r.stayin; return r; };
if (typeof runChecks === "function") {
  const runChecksLines = runChecks;
  runChecks = function () {
    runChecksLines();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastStay) return;
      out.textContent += "\n\nStay in Lines:\n" + NM.lastStay.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 200);
  };
}

// ===================================================================================================================
// Gradient (the owner's "fading color (or gradient shift)"; the app's GreyGradient): the grey changes along each stroke,
// by the distance drawn (cm at the page's 300 dpi, the Manta's and the Nomad's alike), where Grey Shift picks one between
// strokes. Fade lightens the stroke's grey to paper over its Length; To Grey takes it to its End Grey (one of the
// panel's 16: 0 paper white to 15 black); Cycle steps through Grey Shift's set (its Greys, Toolbar or Any) from the
// stroke's grey on, round and round, eased from one to the next over each Length, a rainbow pencil's way. Each stroke
// starts again at its own grey. The stroke is laid at the darkest grey it reaches, each point's share of that in its
// own (times Paint Load's), which every stamper scales its darkness by: a wash's share the ratio of its strengths, any
// other mark's the ratio of greys. Ink, Wash, Charcoal, Graphite and Crayon take it, not on water. The hand's last three
// dials, after Grey Shift's; written only when on. Never rolled.
// ===================================================================================================================
const GRAD = { OFF: 0, FADE: 1, TO_GREY: 2, CYCLE: 3, LEVELS: 16, PX_CM: 300 / 2.54, TAKES: new Set(["ink", "wash", "charcoal", "graphite", "crayon"]) };
Object.assign(DIALS.hand, {
  grad: { label: "Gradient", choices: ["Off", "Fade", "To Grey", "Cycle"], def: 0 },
  gto: { label: "End Grey", min: 0, max: 15, def: 5, int: true, fmt: v => gradGreyShown(Math.round(v)) },
  glen: { label: "Length", min: 1, max: 36, def: 10, int: true, fmt: v => Math.round(v) + " cm" }
});
const GRADIENTS = ["grad", "gto", "glen"];
HAND_DIALS.push(...GRADIENTS);
for (const k of GRADIENTS) ADDED.add("hand." + k);
KEEPS.hand.push(...GRADIENTS);
const gradRule = B => (B.hand.d.grad ?? 0) | 0;
// Whether B's medium takes a grey per dab: not Opaque (it covers at its own grey), Resist, Screentone or Float.
const gradTakes = B => GRAD.TAKES.has(B.medium.k) && B.life.k !== "float";
const gradOn = B => gradRule(B) !== GRAD.OFF && gradTakes(B);
const gradEnd = B => Math.round(B.hand.d.gto ?? DIALS.hand.gto.def);
const gradCm = B => Math.round(B.hand.d.glen ?? DIALS.hand.glen.def);
function gradGreyShown(k) { return k <= 0 ? "White" : k >= GRAD.LEVELS - 1 ? "Black" : Math.round(100 * k / (GRAD.LEVELS - 1)) + "%"; }
// A stroke of B from `start` darkness (bar: the toolbar's greys for a Cycle on Toolbar); null when its grey stays.
function gradientOf(B, start, bar) {
  if (!gradOn(B)) return null;
  start = clamp(start);
  const rule = gradRule(B);
  let stops;
  if (rule === GRAD.FADE) stops = [start, 0];
  else if (rule === GRAD.TO_GREY) stops = [start, gradEnd(B) / (GRAD.LEVELS - 1)];
  else {
    // Cycle: from the stroke's grey to the set's next, round and round; from the nearest when it isn't in the set.
    const greys = shiftSet(B, bar).map(k => shiftDark(B, k, bar)), n = greys.length;
    let near = 0;
    for (let i = 0; i < n; i++) if (Math.abs(greys[i] - start) < Math.abs(greys[near] - start)) near = i;
    const first = Math.abs(greys[near] - start) < 1e-6 ? near + 1 : near;
    stops = [start, ...greys.map((_, i) => greys[(first + i) % n])];
  }
  const cycles = rule === GRAD.CYCLE, length = gradCm(B) * GRAD.PX_CM, ref = Math.max(...stops), washes = B.medium.k === "wash";
  const strength = d => washes ? washStrength(d) : d, refK = strength(ref);
  const stop = m => m === 0 ? stops[0] : stops[1 + (m - 1) % (stops.length - 1)];
  const darkness = d => {
    const u = Math.max(0, d) / length;
    if (!cycles) return lerp(stops[0], stops[1], Math.min(1, u));
    const m = Math.floor(u);
    return lerp(stop(m), stop(m + 1), smooth(u - m));
  };
  const share = dk => refK > 0 ? strength(dk) / refK : 0;
  return { ref, length, darkness, share, shareAt: d => share(darkness(d)) };
}
// The stroke: laid at the darkest grey it reaches, each new point's share in its own (the app's GreyGradient.apply), over
// Paint Load's; its oilSpend is the app's per-sample hook, so a gradient alone gives it one.
const StrokeGd = Stroke;
Stroke = class extends StrokeGd {
  constructor(surf, B, size, shade, host) {
    const g = gradientOf(B, shade, host && host.swatches);
    super(surf, B, size, g ? g.ref : shade, host);
    this.grad = g;
    if (g && !this.oil) this.oil = { grad: true };
  }
  oilSpend() {
    if (this.R.loaded) super.oilSpend();
    if (!this.grad) return;
    const q = this.pts[this.pts.length - 1], s = this.grad.shareAt(q.d);
    q.own = this.R.loaded ? q.own * s : s;
  }
};
// The Control card's Gradient rows (the app's BrushLab.handRows): its rule, a To Grey's End Grey, its Length, and a
// Cycle's Greys and set when Grey Shift doesn't show them; none for a medium that doesn't take it.
const gradRows = B => !gradTakes(B) ? [] : ["grad", ...(gradRule(B) === GRAD.TO_GREY ? ["gto"] : []), ...(gradRule(B) ? ["glen"] : []),
  ...(gradRule(B) === GRAD.CYCLE && !shiftRule(B) ? ["sgreys", "set"] : [])];
const dialShownGd = dialShown;
dialShown = function (B, part, key) {
  if (part === "hand" && key === "grad") return gradTakes(B);
  if (part === "hand" && key === "gto") return gradTakes(B) && gradRule(B) === GRAD.TO_GREY;
  if (part === "hand" && key === "glen") return gradOn(B);
  if (part === "hand" && key === "sgreys" && gradOn(B) && gradRule(B) === GRAD.CYCLE) return true;
  return dialShownGd(B, part, key);
};

// ---------- words: "Fades to paper over 10 cm." after Grey Shift's (the app's GreyGradient.words) ----------
function gradWords(B) {
  if (!gradOn(B)) return null;
  const over = gradCm(B) + " cm.", rule = gradRule(B), k = gradEnd(B);
  if (rule === GRAD.FADE || rule === GRAD.TO_GREY && k <= 0) return "Fades to paper over " + over;
  if (rule === GRAD.TO_GREY) return "Shifts to " + (k >= GRAD.LEVELS - 1 ? "black" : gradGreyShown(k) + " grey") + " over " + over;
  return "Cycles through its greys every " + over;
}
const plainWordsGd = plainWords;
plainWords = function (B) {
  const said = gradWords(B), text = plainWordsGd(B);
  if (!said) return text;
  const all = text.split(/(?<=\.)\s+/).filter(Boolean), shifts = shiftWords(B);
  let i = shifts ? all.indexOf(shifts) + 1 : 0;
  if (i <= 0) { i = 1; while (i < all.length && SHIFT_AFTER.has(all[i])) i++; }
  all.splice(i, 0, said);
  let t2 = all[0];
  for (let j = 1; j < all.length; j++) { if (j >= 2 && t2.length + 1 + all[j].length > 260) break; t2 += " " + all[j]; }
  return t2;
};

// ---------- checks ----------
function gradChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 700, H = 100, Y = 50, X0 = 20, L = 2 * GRAD.PX_CM, P = PAPER_TONE;
  const mk = (hand, medium) => normBrush({ name: "graded", seed: 17, style: "Fineliner", tip: freshPart("tip", "round"), medium: freshPart("medium", medium || "ink"), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: Object.assign(freshPart("hand", "steady"), { d: Object.assign(freshPart("hand", "steady").d, { glen: 2 }, hand || {}) }) });
  const line = () => { const n = Math.ceil((640 - X0) / 3); return Array.from({ length: n + 1 }, (_, k) => ({ x: X0 + 3 * k, y: Y, p: .65, t: 1000 + 2 * k })); };
  const draw = (B, shade) => { const s = modSurface(W, H, 7); layStroke(s, B, line(), 12, shade, null); return s; };
  const tone = (s, at) => { const x = Math.round(X0 + at); let sum = 0; for (let dx = -4; dx <= 4; dx++) for (let dy = -2; dy <= 2; dy++) sum += s.page[(Y + dy) * W + x + dx]; return sum / 45; };
  // Codes: the hand's last dials, after Grey Shift's; off, nothing.
  { const B = mk({ grad: 2, gto: 9, glen: 7 }), c = encodeM(B), back = readAnyCode(c).b, hand = encodeM(mk({ glen: 10 })).split(".")[1].split("_")[5];
    t("a code with a Gradient (" + c + ") reads back, its End Grey and Length too; off, the hand writes as before (" + hand + ")",
      encodeM(back) === c && gradRule(back) === 2 && gradEnd(back) === 9 && gradCm(back) === 7 && hand.length === 22 && HAND_DIALS.slice(HAND_DIALS.indexOf("grad"), HAND_DIALS.indexOf("grad") + 3).join(" ") === "grad gto glen"
      && encodeM(mk({ grad: 3, glen: 10 })).split(".")[1].split("_")[5].length === 22 + 7 + 3 + 9 + 1); }
  // The greys along the stroke.
  { const f = gradientOf(mk({ grad: 1 }), .8), g = gradientOf(mk({ grad: 2, gto: 15 }), .2), c = gradientOf(mk({ grad: 3 }), SHIFT.GREYS[1]);
    t("Fade reaches paper at its Length; To Grey ends at its End Grey, laid at the darker; Cycle visits the toolbar's greys in order, round and round",
      f.darkness(0) === .8 && Math.abs(f.darkness(L / 2) - .4) < 1e-9 && f.darkness(L) === 0 && f.darkness(3 * L) === 0 && g.ref === 1 && g.darkness(2 * L) === 1
      && [2, 3, 0, 1, 2].every((k, m) => Math.abs(c.darkness((m + 1) * L) - SHIFT.GREYS[k]) < 1e-9) && gradientOf(mk(), 1) === null); }
  // Drawn: Fade lightens to paper; To Grey ends at a plain stroke of its grey; each stroke starts afresh.
  { const s = draw(mk({ grad: 1 }), 1), black = draw(mk(), 1), d = draw(mk({ grad: 2, gto: 5 }), 1), third = draw(mk(), 1 / 3), again = draw(mk({ grad: 1 }), 1);
    t("drawn: Fade from black (" + tone(s, 6).toFixed(0) + ") to paper past its Length (" + tone(s, L + 20).toFixed(0) + "); To Grey ends at its grey ("
      + tone(d, L + 100).toFixed(0) + " against " + tone(third, L + 100).toFixed(0) + "); a second stroke starts black again",
      Math.abs(tone(s, 6) - tone(black, 6)) < 12 && tone(s, L / 2) > tone(s, 6) + 40 && tone(s, L + 20) >= P - .5 && Math.abs(tone(d, L + 100) - tone(third, L + 100)) < 4
      && tone(again, 6) === tone(s, 6)); }
  // Media: ink, wash, charcoal, graphite and crayon take it; Opaque, Resist and Screentone don't.
  { const takes = ["ink", "wash", "charcoal", "graphite", "crayon"].every(m => { const s = draw(mk({ grad: 1 }, m), 1); return gradTakes(mk({}, m)) && tone(s, L + 30) >= P - 1 && tone(s, 8) < tone(s, L * .6) - 10; });
    t("ink, wash, charcoal, graphite and crayon fade along the stroke; opaque, resist and screentone take none (no rows, no words)",
      takes && ["cover", "resist", "tone"].every(m => !gradTakes(mk({ grad: 1 }, m)) && !gradientOf(mk({ grad: 1 }, m), 1) && !gradRows(mk({ grad: 1 }, m)).length && !gradWords(mk({ grad: 1 }, m)))); }
  // Words, rows, never rolled.
  { const roll = () => { for (let i = 0; i < 1400; i++) if (gradRule(rollStyle(STYLE_NAMES[i % STYLE_NAMES.length]))) return false; return true; };
    t("words: \"" + gradWords(mk({ grad: 2 })) + "\", after Grey Shift's; its rows; never rolled",
      gradWords(mk({ grad: 1 })) === "Fades to paper over 2 cm." && gradWords(mk({ grad: 2 })) === "Shifts to 33% grey over 2 cm." && gradWords(mk({ grad: 2, gto: 15 })) === "Shifts to black over 2 cm."
      && gradWords(mk({ grad: 3 })) === "Cycles through its greys every 2 cm." && plainWords(mk({ grad: 1, shift: 1 })).includes(SHIFT.WORDS.random + " Fades to paper over 2 cm.")
      && gradRows(mk({ grad: 3 })).join() === "grad,glen,sgreys,set" && gradRows(mk({ grad: 3, shift: 1 })).join() === "grad,glen" && gradRows(mk()).join() === "grad"
      && dialShown(mk({ grad: 2 }), "hand", "gto") && !dialShown(mk({ grad: 1 }), "hand", "gto") && dialShown(mk({ grad: 3 }), "hand", "sgreys") && roll()); }
  return out;
}
const selfTestGd = selfTest;
selfTest = function () { const r = selfTestGd(); r.grad = gradChecks(); r.ok = r.ok && r.grad.every(c => c.ok); NM.lastGrad = r.grad; return r; };
if (typeof runChecks === "function") {
  const runChecksGd = runChecks;
  runChecks = function () {
    runChecksGd();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastGrad) return;
      out.textContent += "\n\nGradient:\n" + NM.lastGrad.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 220);
  };
}

// ===================================================================================================================
// The owner's eight photo papers (2026-10-22): Paper choices 6 to 13 (the app's PartChoice COTTON to FINE_SPECK), so
// older codes never hold them; their scans are TEXTURE_SRC's, by key. No roll picks one and a variation never swaps to
// one (the app's ModularFeatures.seedOnly): chosen in the lab, kept by codes.
// ===================================================================================================================
const PHOTO_PAPERS = [["watercolour", "Cotton", "cotton paper"], ["rough", "Bumpy", "bumpy paper"], ["unryu", "Unryu", "unryu paper"],
  ["library", "Library", "library paper"], ["kraft", "Kraft", "kraft paper"], ["old buff", "Vintage", "vintage paper"],
  ["blue fibre", "Craft", "craft paper"], ["fine speck", "Fine", "fine paper"]];
for (const [k, label, words] of PHOTO_PAPERS) {
  CHOICES.paper.push({ k, label, dials: ["tooth"], macro: "tooth", words: ["smooth", "rough"], def: { tooth: .5 } });
  PAPER_WORDS[k] = words;
}
const makeVarsPp = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.paper.slice(), photo = new Set(PHOTO_PAPERS.map(p => p[0]));
  CHOICES.paper = keep.filter(c => !photo.has(c.k) || c.k === B.paper.k);
  try { return makeVarsPp(B, locks); } finally { CHOICES.paper = keep; }
};

// ===================================================================================================================
// Tilt Shading (the owner, 2026-10-22, of the pencils: "let's use the tilt so when the pencil is tilted it shades like
// an angled pencil"; the app's PartDial.SIDE_SHADE): how far a pen laid low lays the side of its nib, a wider, lighter,
// softer-edged line (graphite's catching only the paper's tooth), held upright a crisp line as ever (engine.js
// sideShade, stampLine). The hand's last dial, after Gradient's; written only when on. Shown for a nib's line of an
// ink-layer medium (Round, Chisel, Flat, Filbert). Never rolled.
// ===================================================================================================================
Object.assign(DIALS.hand, { tshade: { label: "Tilt Shading", min: 0, max: 1, def: 0 } });
HAND_DIALS.push("tshade");
ADDED.add("hand.tshade");
KEEPS.hand.push("tshade");
const tiltShades = B => STAY.inks.has(B.medium.k) && ["round", "chisel", "flat", "filbert"].includes(B.tip.k);
const dialShownTs = dialShown;
dialShown = function (B, part, key) { if (part === "hand" && key === "tshade") return tiltShades(B); return dialShownTs(B, part, key); };
function tiltShadeChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 400, H = 120, Y = 60;
  const mk = side => normBrush({ name: "shaded", seed: 17, style: "Pencil", tip: freshPart("tip", "round"), medium: freshPart("medium", "graphite"), carry: freshPart("carry", "off"),
    paper: freshPart("paper", "plain"), life: freshPart("life", "none"), hand: Object.assign(freshPart("hand", "steady"), { d: Object.assign(freshPart("hand", "steady").d, side ? { tshade: side } : {}) }) });
  const draw = (B, deg) => { const s = modSurface(W, H, 7), n = 110; layStroke(s, B, Array.from({ length: n + 1 }, (_, k) => ({ x: 40 + 3 * k, y: Y, p: .6, tx: 0, ty: deg, t: 1000 + 2 * k })), 4, 1, null); return s; };
  const across = s => { let rows = 0; for (let y = 0; y < H; y++) { let any = false; for (let x = 190; x < 210; x++) if (s.page[y * W + x] < PAPER_TONE - 8) any = true; if (any) rows++; } return rows; };
  const ink = s => { let sum = 0, n = 0; for (let y = 0; y < H; y++) for (let x = 150; x < 250; x++) { const v = PAPER_TONE - s.page[y * W + x]; if (v > 8) { sum += v; n++; } } return n ? sum / n : 0; };
  // Codes: the hand's last dial; off, the hand writes as before.
  { const c = encodeM(mk(1)), back = readAnyCode(c).b, hand = encodeM(mk(0)).split(".")[1].split("_")[5];
    t("a code with Tilt Shading (" + c + ") reads back; off, the hand writes as before (" + hand + ")",
      encodeM(back) === c && back.hand.d.tshade === 1 && hand.length === 22 && HAND_DIALS[HAND_DIALS.length - 1] === "tshade"); }
  // Drawn: upright and at 30 degrees exactly as without it; at 70, wider and lighter.
  { const off = draw(mk(0), 70), up = draw(mk(1), 0), up0 = draw(mk(0), 0), at30 = draw(mk(1), 30), low = draw(mk(1), 70);
    t("drawn: upright and at 30 degrees as without it (" + across(up) + " rows); laid at 70, " + across(low) + " rows wide and lighter (" + ink(low).toFixed(0) + " against " + ink(up).toFixed(0) + ")",
      up.page.every((v, i) => v === up0.page[i]) && at30.page.every((v, i) => v === up0.page[i]) && off.page.every((v, i) => v === up0.page[i])
      && across(low) >= 3 * across(up) && ink(low) < ink(up)); }
  t("shown for a nib's line of graphite, ink or charcoal; not a wash's or a hatch", tiltShades(mk(1)) && dialShown(mk(1), "hand", "tshade")
    && !tiltShades(Object.assign(mk(1), { medium: freshPart("medium", "wash") })) && !tiltShades(Object.assign(mk(1), { tip: freshPart("tip", "hatch") })));
  return out;
}
const selfTestTs = selfTest;
selfTest = function () { const r = selfTestTs(); r.tshade = tiltShadeChecks(); r.ok = r.ok && r.tshade.every(c => c.ok); NM.lastTshade = r.tshade; return r; };
if (typeof runChecks === "function") {
  const runChecksTs = runChecks;
  runChecks = function () {
    runChecksTs();
    setTimeout(() => { const out = document.getElementById("checks"); if (!out || !NM.lastTshade) return;
      out.textContent += "\n\nTilt Shading:\n" + NM.lastTshade.map(c => `  ${c.ok ? "ok  " : "FAIL"} ${c.what}`).join("\n"); }, 240);
  };
}

// ===================================================================================================================
// The app's catch-up (2026-10-27): what the app's ModularCode, PartChoice and PartDial have that this port didn't, so
// every share code the app writes reads here and writes back the same.
//   Gravity     Quick's Runs Downhill, Pools Downhill and Grainy; Slow's Runs, Drips, Pools, Grainy and Rock to Blend
//               (the Gravity Lab's). They move wet paint on a tilted page, which this page has none of: hidden, as the
//               hand's page tilt is, kept so codes line up. Written only when on; never rolled here.
//   Wet         Drying's eighth choice (the wet paint spec): Stays Wet and Runs Downhill. The app lays it into its wet
//               canvas, which mixes, evens out and stays wet across strokes; here it lays as Instant, as the app does
//               wherever it has no wet canvas (its samples, Copy Strokes' copies). Never rolled or swapped to.
//   Hand Hatch  cut from the app (2026-10-24): Tip choice 12 still reads, as Hatch with the dials the two share.
// ===================================================================================================================
Object.assign(DIALS.life, {
  runs: { label: "Runs Downhill", min: 0, max: 1, def: 0, hide: true }, drips: { label: "Drips", min: 0, max: 1, def: 0, hide: true },
  pool: { label: "Pools Downhill", min: 0, max: 1, def: 0, hide: true }, sink: { label: "Grainy", min: 0, max: 1, def: 0, hide: true },
  blend: { label: "Rock to Blend", min: 0, max: 1, def: 0, hide: true },
  stays: { label: "Stays Wet", min: 0, max: 1, def: .5 }
});
choiceOf("life", "wet").dials.push("runs", "pool", "sink");
choiceOf("life", "dry").dials.push("runs", "drips", "pool", "sink", "blend");
CHOICES.life.push({ k: "wetpaint", label: "Wet", dials: ["stays", "runs"], macro: "stays", words: ["short", "long"],
  blurb: "Stays wet across strokes and mixes into the wet paint under it. In the app; drawn here as Instant." });
for (const k of ["runs", "drips", "pool", "sink", "blend", "stays"]) ADDED.add("life." + k);
KEEPS.life.push("runs", "pool", "sink");
const resolveWetPaint = resolve;
resolve = function (B, size, shade) {
  if (B.life.k !== "wetpaint") return resolveWetPaint(B, size, shade);
  const A = cloneBrush(B);
  A.life = freshPart("life", "none");
  return resolveWetPaint(A, size, shade);
};
const makeVarsWet = makeVars;
makeVars = function (B, locks) {
  const keep = CHOICES.life.slice();
  CHOICES.life = keep.filter(c => c.k !== "wetpaint" || c.k === B.life.k);
  try { return makeVarsWet(B, locks); } finally { CHOICES.life = keep; }
};
// Hand Hatch: read with its own dials (the app's PartChoice.HAND_HATCH), then Hatch keeps the ones the two share
// (ModularCode.asHatch). Never in CHOICES, so nothing offers it.
Object.assign(DIALS.tip, {
  hlen: { label: "Line Length", min: 0, max: 1, def: .5, hide: true }, hwob: { label: "Wobble", min: 0, max: 1, def: .5, hide: true },
  hends: { label: "Taper Out", min: 0, max: 1, def: .7, hide: true }
});
const HAND_HATCH = { k: "handhatch", label: "Hand Hatch", dials: ["pitch", "lineW", "hangle", "headTurn", "hlen", "hwob", "hends", "weight", "flick"], def: { headTurn: 1 } }, HAND_HATCH_AT = 12;
const readPartHH = readPart;
readPart = function (part, seg) {
  if (part !== "tip" || !seg || parseInt(seg[0], 36) !== HAND_HATCH_AT || CHOICES.tip.length !== HAND_HATCH_AT) return readPartHH(part, seg);
  CHOICES.tip.push(HAND_HATCH);
  let P;
  try { P = readPartHH(part, seg); } finally { CHOICES.tip.length = HAND_HATCH_AT; }
  if (!P) return null;
  const H = freshPart("tip", "hatch");
  for (const key of choiceOf("tip", "hatch").dials) if (HAND_HATCH.dials.includes(key)) H.d[key] = P.d[key];
  return H;
};
