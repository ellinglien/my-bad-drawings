// ===================================================================================================================
// Checks, in the page (#selftest) and in Node: old codes load and draw as before; codes round-trip; every part's every
// choice draws something.
// ===================================================================================================================
// The app's own test codes (BrushCodeChecks.java) and one per paint style.
const LEGACY_CODES = ["dapper-fig.I610gavij3f7q16o0000006m00.e1", "bobson-dugnutt.I610zkyum2fbzsge00ej5k00e7.1", "x.I613zkyum2fbzsge00ej5k00e7.1",
  "mellow-plum.P600xjf400c4jzxh00aqhu.hd", "bouncy-moose.P610hh46000000000000wp.25"];
// The old engine (port.js, unchanged) and the new one, the same stroke on the same paper; differing pixels at 16 greys.
function legacyDiff(code, W = 420, H = 300) {
  const r = readAnyCode(code), old = decode(code);
  if (!r || !old) return { code, ok: false, why: "did not read" };
  const ink = old.kind === "ink", size = ink ? 8 : 120, pad = ink ? 22 : 70;
  const ps = surface(W, H, 7), pts = loopPoints(W, H, pad);
  lay(ps, old, pts, size, ink ? INK_DARK[0] : PAINT_DARK[0]);
  const ms = modSurface(W, H, 7);
  // An old paint (a wash) at the darkness that lays the old black's glaze since washes reach black (the app's ModularSizes.sinceBlack).
  layStroke(ms, r.b, pts, size, r.b.medium.k === "wash" ? GLAZE / WASH_BLACK : 1, null);
  let diff = 0, marked = 0;
  for (let i = 0; i < W * H; i++) {
    const a = Math.round(PAPER * (1 - ps.wash[i]) * (1 - ps.ink[i]) / 17) * 17, b = Math.round(ms.page[i] / 17) * 17;
    if (a !== b) diff++; if (a !== 238 + 17 * 0 && a < 238) marked++;
  }
  return { code, ok: diff === 0 && marked > 200, diff, marked, from: r.from };
}
function roundTrips(n = 300) {
  let bad = [];
  for (let i = 0; i < n; i++) {
    const style = STYLE_NAMES[i % STYLE_NAMES.length], B = rollStyle(style);
    const code = encodeM(B), back = readAnyCode(code);
    if (!back || encodeM(back.b) !== code) { bad.push(code); continue; }
    for (const p of PARTS) for (const [k, v] of Object.entries(B[p].d)) {
      const s = DIALS[p][k], tol = s.choices ? 0 : (s.max - s.min) / M_STEPS / 2 + 1e-9;
      if (Math.abs(back.b[p].d[k] - v) > tol && !(s.int)) bad.push(code + " " + p + "." + k);
    }
  }
  return bad;
}
// Codes from the prototypes: they load, into the parts they describe. B codes are written here as pen-paint-blend wrote them.
function writeSpec(o, order) {
  let out = "";
  for (const [k, spec] of order) {
    if (typeof spec[0] === "string") { out += Math.max(0, spec.indexOf(o[k])); continue; }
    out += two36(Math.round((clamp(o[k], spec[0], spec[1]) - spec[0]) / (spec[1] - spec[0]) * 1295));
  }
  return out;
}
function protoChecks() {
  const out = [];
  const base = "dapper-fig.I610gavij3f7q16o0000006m00";
  const t = (code, test, what) => { const r = readAnyCode(code); out.push({ code, what, ok: !!r && (!test || test(r.b)) }); };
  t(base + ".e1.x10zz00", b => b.hand.d.pd > .3 && b.hand.d.ps === 0 && b.hand.d.ts > .99, "lab v2 .x pen suffix");
  t(base + "8v001.e1", b => b.medium.k === "charcoal" && b.life.k === "none", "brush-experiments ink tail (spread, tilt, fast)");
  t(base + "1c7jsg7.e1", b => b.tip.k === "bristles", "marble-and-bristle swarm tail");
  t("mellow-plum.P600xjf400c4jzxh00aqhuzz001000.hd", b => b.medium.k === "cover" && b.tip.k === "hake", "brush-experiments paint tail (cover, head)");
  t("mellow-plum.P700xjf400c4jzxh00aqhu00001000.hd", b => b.style === "Hake" && b.tip.k === "hake", "brush-experiments Hake style");
  const bb = { nib: "chisel", mode: "wet", texture: "cold press", wet: .55, line: .8, aspect: .4, angle: 40, taperIn: .2, taperOut: .5, pressure: .7, speed: -.3,
    grain: .2, bristle: .1, wobble: 0, pool: .3, splatter: 0, soft: .5, bleed: .4, bloom: .2, backrun: 0, drying: .5,
    lines: "split", count: 3, spread: .6, breathe: .8, offset: 0, chaos: 0, plume: .7, pstyle: "vein", settle: .4 };
  const head = "brisk-quince.B6" + writeSpec(bb, BLEND_ORDER);
  t(head + ".7", b => b.medium.k === "ink" && Math.abs(b.medium.d.wet - .55) < .01 && b.paper.k === "cold press", "pen-paint-blend B code");
  t(head + writeSpec(bb, BLEND_TAIL) + ".7", b => b.tip.k === "split" && Math.abs(b.tip.d.gap - .6) < .01, "B code with a split-nib tail");
  t(head + writeSpec(bb, BLEND_TAIL.slice(0, 4)) + ".7", b => b.tip.k === "split", "B code from round 2 (tail before Offset and Chaos)");
  t(head + writeSpec(bb, PLUME_TAIL) + ".7", b => b.life.k === "dry" && b.life.d.takes === 2 && Math.abs(b.life.d.settle - .4) < .01, "B code with ink-and-water's plume tail");
  for (const c of ["hello", "x.M0_0_0.1", "dapper-fig.I610gavij3f7q16o0000006m0.e1", "a.M9z0000.1"]) out.push({ code: c, what: "a bad code reads as nothing", ok: readAnyCode(c) === null });
  return out;
}
// Float (suminagashi): drops keep area, rings print crisp, clear water lays nothing, the log replays, Undo while it
// floats, drift only when tilted, prints when its time is up, codes round-trip.
function floatChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok });
  const mk = (load, tip = "round") => {
    const B = { name: "f", seed: 7, style: "Ink wash", tip: freshPart("tip", tip), medium: freshPart("medium", "ink"), carry: freshPart("carry", load === undefined ? "off" : "smudge"),
      paper: freshPart("paper", "plain"), life: freshPart("life", "float"), hand: freshPart("hand", "steady") };
    if (load !== undefined) { B.carry.d.load = load; B.carry.d.pick = .9; }
    return normBrush(B);
  };
  const W = 360, H = 260, ink = mk(), cw = mk(0);
  const area = g => { let a = 0; for (const c of g.cs) { let s = 0; for (let i = 0, j = c.n - 1; i < c.n; j = i++) s += c.x[j] * c.y[i] - c.x[i] * c.y[j]; a += Math.abs(s / 2); } return a; };
  const hashRings = b => { let h = 0x811c9dc5; for (const g of b.rings) for (const c of g.cs) for (let i = 0; i < c.n; i++) { h = Math.imul(h ^ Math.round(c.x[i] * 64), 16777619); h = Math.imul(h ^ Math.round(c.y[i] * 64), 16777619); } return h >>> 0; };
  let T = 0;
  const tap = (s, host, B, x, y, hold, shade = 1) => { const st = new Stroke(s, B, 8, shade, host); st.add([[x, y, .7, T, NaN, NaN]]); for (let k = 50; k <= hold; k += 50) st.holdTick(T + k); T += hold + 100; st.end(); return st; };
  const drag = (s, host, B, x0, y0, x1, y1, size = 8) => { const st = new Stroke(s, B, size, 1, host), n = Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 2.5);
    for (let i = 0; i <= n; i += 3) { const sm = []; for (let k = i; k < Math.min(n + 1, i + 3); k++) sm.push([x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n, .7, (T += 4), NaN, NaN]); st.add(sm); }
    T += 100; st.end(); return st; };
  const bathOf = s => ({ bath: null, getBath() { if (!this.bath || !this.bath.wet) this.bath = new Bath(s); return this.bath; } });
  // 1. Drops keep every ring's area.
  { const s = modSurface(W, H, 7), host = bathOf(s); tap(s, host, ink, 90, 130, 200); const b = host.bath, a0 = area(b.rings[0]);
    tap(s, host, cw, 230, 128, 400); tap(s, host, ink, 236, 135, 300);
    t("a drop pushes older paint out without changing its area (" + ((area(b.rings[0]) / a0 - 1) * 100).toFixed(2) + "%)", Math.abs(area(b.rings[0]) / a0 - 1) < .005); }
  // 2. Ink and clear water in turn print as crisp rings: many dark/light changes along a ray, each edge at most ~2 px of grey.
  { const s = modSurface(W, H, 7), host = bathOf(s);
    for (let i = 0; i < 6; i++) { tap(s, host, ink, 180, 130, 200 + 60 * i); tap(s, host, cw, 180, 130, 150); }
    host.bath.print();
    let changes = 0, grey = 0, last = -1;
    for (let x = 180; x < W; x++) { const v = Math.round(s.page[130 * W + x] / 17) * 17, k = v < 100 ? 0 : v > 200 ? 1 : -1; if (k < 0) grey++; else { if (last >= 0 && k !== last) changes++; last = k; } }
    t("ink and clear water in turn print as rings: " + changes + " edges along a ray, " + (grey / Math.max(1, changes)).toFixed(1) + " grey px an edge", changes >= 10 && grey / changes <= 2.2); }
  // 3. Clear water lays nothing of its own: on a blank page, a drag and a tap leave it blank.
  { const s = modSurface(W, H, 7), host = bathOf(s); drag(s, host, cw, 40, 60, 320, 200); tap(s, host, cw, 100, 200, 300); host.bath.print();
    let marked = 0; for (let i = 0; i < W * H; i++) if (Math.round(s.page[i] / 17) * 17 < 238) marked++;
    t("clear water on blank paper lays nothing (" + marked + " px)", marked === 0); }
  // 4. The log is the bath: replaying it gives the same rings, bit for bit; 5. Undo while it floats takes back one stroke.
  { const s = modSurface(W, H, 7), host = bathOf(s); tap(s, host, ink, 150, 120, 300); tap(s, host, cw, 152, 121, 200);
    const b = host.bath, h2 = hashRings(b);
    drag(s, host, mk(0, "multi"), 40, 130, 320, 130, 12);
    const h3 = hashRings(b), keep = b.strokes.map(o => o.slice()), b2 = new Bath(s); for (const ops of keep) { b2.begin(); for (const op of ops) b2.apply(op); }
    t("replaying the log gives the same bath", hashRings(b2) === h3 && h3 !== h2);
    b.undoLast(); t("Undo while floating takes back the last stroke exactly", hashRings(b) === h2); }
  // 6. Drift: none on a level page or in the dead zone; downhill when tilted, area kept; 7. prints when its time is up.
  { const s = modSurface(W, H, 7), host = bathOf(s); tap(s, host, ink, 150, 90, 300); tap(s, host, cw, 150, 90, 200);
    const b = host.bath, h0 = hashRings(b), cy = g => (g.b[1] + g.b[3]) / 2, y0 = cy(b.rings[0]), a0 = area(b.rings[0]);
    b.tick(b.lastTouch + 100, { x: 0, y: 0 }, 1); b.tick(b.lastTouch + 200, { x: .05, y: .05 }, 1);
    t("a level page (and the dead zone) doesn't drift", hashRings(b) === h0);
    for (let k = 3; k < 23; k++) b.tick(b.lastTouch + 100 * k, { x: 0, y: .9 }, 1);
    t("a tilted page drifts it downhill (" + (cy(b.rings[0]) - y0).toFixed(1) + " px in 2 s), area kept (" + ((area(b.rings[0]) / a0 - 1) * 100).toFixed(2) + "%)", cy(b.rings[0]) - y0 > 20 && Math.abs(area(b.rings[0]) / a0 - 1) < .01);
    b.tick(b.lastTouch + b.dryMs + 1, { x: 0, y: 0 }, 1); t("it prints when its floating time is up", !b.wet); }
  // 8. Codes: Float is the fourth Drying choice; its code round-trips.
  { const code = encodeM(cw), back = readAnyCode(code);
    t("a Float code round-trips (" + code + ")", back && encodeM(back.b) === code && back.b.life.k === "float" && code.split("_")[4][0] === "3"); }
  return out;
}
// Erasable (spec docs/archive/superpowers/specs/2026-10-16-erasable-design.md): pencil, ink over it, the eraser, and the ink is
// as it was; a wash erases in part; paint that erases over paint that doesn't gives it back; a smear moves only what
// erases; pressed hard, the eraser still leaves permanent ink; old codes, and every roll, as before.
function eraseChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), W = 360, H = 240, q = v => Math.round(v / 17) * 17;
  const mk = (style, medium, md, carry) => {
    const B = { name: "e", seed: 11, style, tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium), carry: freshPart("carry", carry || "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", "wet"), hand: freshPart("hand", "steady") };
    Object.assign(B.medium.d, md || {});
    return normBrush(B);
  };
  const line = (x0, y0, x1, y1, n = 80) => Array.from({ length: n + 1 }, (_, k) => ({ x: x0 + (x1 - x0) * k / n, y: y0 + (y1 - y0) * k / n, p: .8, t: k * 6, tx: NaN, ty: NaN }));
  const rub = (s, x0, y0, x1, y1, max, p) => { const er = new Eraser(s, max); for (let k = 0; k <= 60; k++) er.add([[x0 + (x1 - x0) * k / 60, y0 + (y1 - y0) * k / 60, p, k * 6, NaN, NaN]]); };
  const eraseAll = s => { for (let y = 0; y <= s.h + 60; y += 60) rub(s, -40, y, s.w + 40, y, 220, 1); };
  const differ = (a, b) => { let n = 0; for (let i = 0; i < a.length; i++) if (q(a[i]) !== q(b[i])) n++; return n; };
  const dark = (s, x0, y0, x1, y1) => { let d = 0; for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) d += PAPER_TONE - s.page[y * s.w + x]; return d; };
  const pencil = mk("Pencil", "graphite", { grade: .55 }), ink = mk("Fineliner", "ink", { erase: 0 }); // permanent by hand: every medium starts at 1
  // 1. The sketch, inked, erased: the page is the ink alone.
  { const s = modSurface(W, H, 7), alone = modSurface(W, H, 7);
    layStroke(s, pencil, line(30, 40, 330, 200), 6, 1, null); layStroke(s, pencil, line(30, 130, 330, 110), 6, 1, null);
    const sketched = differ(s.page, alone.page);
    layStroke(s, ink, line(40, 200, 320, 50), 4, 1, null); layStroke(alone, ink, line(40, 200, 320, 50), 4, 1, null);
    eraseAll(s);
    t("pencil, ink over it, erased: " + sketched + " px of pencil gone, " + differ(s.page, alone.page) + " px differ from the ink alone", sketched > 1500 && differ(s.page, alone.page) === 0); }
  // 2. A brush from an old code erases wholly, as before.
  { const s = modSurface(W, H, 7), old = readAnyCode("steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud").b;
    layStroke(s, old, line(40, 120, 320, 120), 6, 1, null); eraseAll(s);
    t("an old ink code reads all erasable (" + old.medium.d.erase + ") and erases to paper", old.medium.d.erase === 1 && dark(s, 0, 0, W, H) === 0); }
  // 3. A wash (Erasable .4): the eraser takes about 40% of its darkness.
  { const s = modSurface(W, H, 7), wash = mk("Watercolor", "wash", { erase: .4 }); layStroke(s, wash, line(40, 120, 320, 120), 90, 1, null);
    const d0 = dark(s, 0, 0, W, H); eraseAll(s); const kept = dark(s, 0, 0, W, H) / d0;
    t("a wash at Erasable .4 keeps " + (kept * 100).toFixed(1) + "% of its darkness", Math.abs(kept - .6) < .03); }
  // 4. Erasable opaque paint over permanent ink: erased, the ink is back as it was.
  { const s = modSurface(W, H, 7), alone = modSurface(W, H, 7), white = mk("Gouache", "cover", { erase: 1 });
    layStroke(s, ink, line(40, 120, 320, 120), 6, 1, null); layStroke(alone, ink, line(40, 120, 320, 120), 6, 1, null);
    layStroke(s, white, line(40, 120, 320, 120), 60, .05, null);
    const hidden = differ(s.page, alone.page); eraseAll(s);
    t("erasable gouache over ink (" + hidden + " px), erased: " + differ(s.page, alone.page) + " px differ from the ink", hidden > 1000 && differ(s.page, alone.page) === 0); }
  // 5. A finger (Smear, Own paint 0) moves pencil, never permanent ink.
  { const finger = mk("Charcoal", "charcoal", { erase: 1 }, "smudge"); finger.carry.d.load = 0; finger.carry.d.pick = .9;
    const moved = B => { const s = modSurface(W, H, 7); layStroke(s, B, line(180, 20, 180, 220), 8, 1, null); const was = Float32Array.from(s.page);
      layStroke(s, finger, line(60, 120, 300, 125), 30, 1, null); let m = 0; for (let i = 0; i < was.length; i++) m += Math.abs(s.page[i] - was[i]); return m; };
    const mi = moved(ink), mp = moved(pencil);
    t("a finger smears pencil (" + mp.toFixed(0) + " tone) and leaves ink (" + mi.toFixed(1) + ")", mp > 2000 && mi < 1); }
  // 6. However hard it is pressed, the eraser leaves permanent ink as it was.
  { const s = modSurface(W, H, 7); layStroke(s, ink, line(40, 120, 320, 120), 12, 1, null); const d0 = dark(s, 0, 0, W, H);
    rub(s, 20, 120, 340, 120, 120, .6); const d1 = dark(s, 0, 0, W, H); rub(s, 20, 120, 340, 120, 120, 1); rub(s, 20, 120, 340, 120, 120, 1); const d2 = dark(s, 0, 0, W, H);
    t("pressed lightly or hard, twice, the eraser leaves the ink (" + (d1 / d0 * 100).toFixed(1) + "%, " + (d2 / d0 * 100).toFixed(1) + "%)", d1 === d0 && d2 === d0); }
  // 7. Codes: old ones byte for byte; the dial written only when not 1, after charcoal's dust; round trips.
  { const olds = ["steady-line.M00il00000000at_000zz0000000000000_0_00r_1jz_84w0000000000000000nz0.51j8ud", "messy-stick.M51eueei000005bd4at_3nbcw_0_2tz_1jz_8syjs00y8j900000000nz0.t4mf73",
      "soft-wash.M60i0000000007q_1wva200000000_0_0fk_1jz_8la0000000000000000nz0.t4mf73", "f.M70i000000000at_000zz0000000000000_3wejs00_000_3lli0i0_0000000000000000000nz0.7"];
    t("old codes re-encode byte for byte", olds.every(c => encodeM(readAnyCode(c).b) === c));
    const c0 = encodeM(ink), cc = encodeM(mk("Charcoal", "charcoal", { erase: .9 })), back = readAnyCode(c0).b;
    t("an ink at Erasable off writes it last (" + c0.split("_")[1] + "); a charcoal at .9 after its dust (" + cc.split("_")[1] + ")",
      back.medium.d.erase === 0 && encodeM(back) === c0 && /00$/.test(c0.split("_")[1]) && cc.split("_")[1].slice(-4, -2) === "00" && Math.abs(readAnyCode(cc).b.medium.d.erase - .9) < .001); }
  // 8. No roll changes Erasable (the owner, 2026-10-22): a new brush starts at 1, a roll over a brush keeps its value
  //    (Randomize, a part's dice), the dial's own die leaves it, and every other part rolls exactly as before (300 rolls).
  //    Names keep their adjective here (nameChecks: only the name changes).
  { const keep = Math.random; Math.random = rng(4242); TRAIT_NAMES.on = false; let h = 0x811c9dc5, fresh = true, kept = true;
    const none = { parts: new Set(), dials: new Set() };
    // The hash was taken over the fourteen styles before Print.
    const OLD14 = STYLE_NAMES.filter(n => n !== "Print");
    try { const all = [];
      for (let i = 0; i < 300; i++) { const st = OLD14[i % OLD14.length]; let B = rollStyle(st); all.push(B);
        if (B.medium.k !== "resist" && B.medium.d.erase !== 1) fresh = false;
        B = cloneBrush(B); B.medium.d.erase = 1; for (const ch of encodeM(B)) h = Math.imul(h ^ ch.charCodeAt(0), 16777619); }
      for (const B of all) if (B.medium.k !== "resist") { const p = cloneBrush(B); p.medium.d.erase = .25;
        const r = rollStyle(B.style, p, none), m = rollOnePart(p, "medium", none), d = rollOneDial(p, "medium", "erase");
        for (const x of [r, m, d]) if (x.medium.k !== "resist" && x.medium.d.erase !== .25) kept = false; } } finally { Math.random = keep; TRAIT_NAMES.on = true; }
    t("rolls start at Erasable 1 and keep a brush's own through Randomize, a part's dice and the dial's; with it at 1 they hash as before (" + (h >>> 0).toString(16) + ")",
      fresh && kept && (h >>> 0) === 0x3cfc3ac9); }
  return out;
}
// Names (parts.js describedName and kinOf, the app's BrushNamesChecks): a new brush is named for what it is, a fun
// word, a describer and its part, deadpan, from the brush and its seed; the same words, forms, made-up words and
// variations as the app's.
function nameChecks() {
  const out = [], t = (what, ok) => out.push({ what, ok: !!ok }), newMods = CHOICES.tip.some(c => c.k === "stipple");
  // A Fineliner (all its usual), then [part, choice, dials] changes.
  const mk = (style, seed, ...ch) => {
    const B = { name: "dapper-plum", seed, style, tip: freshPart("tip", "round"), medium: switchChoice("medium", null, "ink"), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", "wet"), hand: freshPart("hand", "steady") };
    for (const [part, k, d] of ch) { if (k) B[part] = switchChoice(part, B[part], k); Object.assign(B[part].d, d || {}); }
    return normBrush(B);
  };
  // [describer|either part|either] ("-" any describer), as BrushNamesChecks.traits has them.
  const F = "Fineliner", cases = [
    ["- pen|liner", mk(F, 1)], ["- nib", mk(F, 1, ["tip", "flat"], ["life", "none"])], ["- float", mk(F, 1, ["life", "float"])],
    ["- smear|smudge", mk(F, 3, ["carry", "smudge"])], ["- stump", mk(F, 1, ["carry", "smudge", { soften: .6 }])],
    ["- comb", mk(F, 1, ["tip", "multi"], ["carry", "smudge", { soften: .6 }])], ["- lift", mk(F, 1, ["carry", "lift"])], ["inky|inkish drag", mk(F, 2, ["carry", "drag"])],
    ["dusty|sooty charcoal", mk(F, 1, ["medium", "charcoal", { dust: .4 }])], ["dusty|sooty chisel", mk(F, 1, ["tip", "chisel"], ["medium", "charcoal", { dust: .4 }])],
    ["- rake", mk(F, 1, ["tip", "multi"])], ["- split", mk(F, 1, ["tip", "split"])], ["- bristle", mk(F, 1, ["tip", "bristles"])],
    ["- hake", mk(F, 1, ["tip", "hake"])], ["- chisel", mk(F, 1, ["tip", "chisel"])], ["- float", mk(F, 1, ["tip", "multi"], ["life", "float"])],
    ["ribbed|ribby pen|liner", mk(F, 1, ["paper", "laid", { tooth: .8 }])], ["- pen|liner", mk(F, 1, ["paper", "laid", { tooth: .5 }])],
    ["- pencil", mk(F, 1, ["medium", "graphite"])], ["- wash", mk(F, 1, ["medium", "wash"])], ["- paint", mk(F, 1, ["medium", "cover"])],
    ["- hake", mk("Hake", 1, ["tip", "hake"], ["medium", "wash"])], ["- pencil", mk("Pencil", 1, ["medium", "graphite"])]];
  if (newMods) cases.push(["- mask", mk(F, 1, ["medium", "resist", { erase: 1 }])], ["waxy wax|candle", mk(F, 1, ["medium", "resist", { erase: 0 }])],
    ["- dots|stipple", mk(F, 1, ["tip", "stipple"])], ["- hatch", mk(F, 1, ["tip", "hatch"])], ["chalky|pale|ghostly pen|liner", mk(F, 1, ["medium", null, { blend: 1 }])],
    ["- mold", mk(F, 1, ["life", "halo"])], ["bloomy|blossomy hake", mk(F, 1, ["tip", "hake"], ["medium", "wash", { bloom: .6 }])]);
  const wrong = cases.filter(([want, B]) => { const w = plainNameWords(B), [d, p] = want.split(" ");
    return !(w.length === 3 && p.split("|").includes(w[2]) && (d === "-" || d.split("|").includes(w[1])) && describedName(B, B.name) === brushNameOf(B)); })
    .map(([want, B]) => want + " not " + plainNameWords(B).join(" "));
  t("a name says what the brush is, most telling first (" + cases.length + " brushes" + (wrong.length ? ": " + wrong.join(", ") : "") + ")", !wrong.length);
  { const keep = TRAIT_NAMES.on; TRAIT_NAMES.on = false; const off = describedName(mk(F, 1, ["life", "float"]), "dapper-plum"); TRAIT_NAMES.on = keep;
    t("off, the name as it was; no family part, no new name", off === "dapper-plum" && describedName(mk(F, 1, ["life", "float"]), "") === ""); }
  t("traits.txt's five sections (" + [...TRAITS.keys()] + "), short words", TRAITS.size === 5 && ["part", "describer", "fun"].every(k => TRAITS.get(k).has("any"))
    && ["part", "describer", "fun"].every(k => [...TRAITS.get(k).values()].flat().every(w => /^[a-z]{3,8}$/.test(w))));
  t("an edited list reads what it can", JSON.stringify([...traitSections("# a\n#\ntip.multi:\trake raked\npaper.cold  press: grainy\ntip.multi: comb\nnonsense\ntip.split: Split 2 forky\ntip.hake: hake-y\n[fun]\nany: brisk\n[describer]\n# nothing\n")].map(([k, m]) => [k, [...m]])) ===
    JSON.stringify([["part", [["tip.multi", ["rake", "raked"]], ["paper.cold press", ["grainy"]], ["tip.split", ["forky"]]]], ["fun", [["any", ["brisk"]]]]]));
  // Made-up words: the app's BrushNamesChecks.nonsense.
  { let bad = 0, ok = true;
    for (const plain of [["giddy", "dusty", "chisel"], ["giddy", "shy", "chisel"]]) { const seen = new Set(); let m = 0;
      for (let seed = 0; seed < 1000; seed++) { const w = nonsenseName(plain, seed); if (w === plain) continue; m++; seen.add(w.join(" "));
        if (w[0] !== "giddy" || !(plain[1] === "dusty" ? w[1].startsWith("dust") && w[2] === "chisel" : w[1] === "shy" && w[2].startsWith("chisel"))) bad++; }
      ok = ok && m > 120 && m < 220 && (seen.has("giddy dustwhistle chisel") || seen.has("giddy shy chiselkins")); }
    t("about one name in six has a made-up word, still the brush's", !bad && ok
      && nameStem("wobbly") === "wobble" && nameStem("shaky") === "shake" && nameStem("ghostly") === "ghost" && nameStem("pale") === null
      && nameSuffixed("drag", "y") === "draggy" && nameSuffixed("smudge", "ery") === "smudgery" && nameSuffixed("drip", "ery") === "drippery" && nameSuffixed("pen", "kins") === "penkins"); }
  // Deadpan forms, every name saying its part (BrushNamesChecks.deadpan): Title Case, "of" and "for" between (BrushNamesChecks.FORM).
  const NAME_FORM = /^[A-Z][a-z]+( ([A-Z][a-z]+|of|for)){1,3}$/;
  { let n = 0, bad = 0; const seen = new Set();
    for (let seed = 0; seed < 500; seed++) { const B = mk(F, seed, ["tip", "hake"], ["medium", "wash", { bloom: .6 }]), w = brushNameOf(B); seen.add(w);
      if (!w.toLowerCase().includes("hake") || w.length > NAME_MAX || !nameTwoLines(w)) bad++;
      if ([nameWords(B), plainNameWords(B)].some(ws => ws.join(" ") === w.toLowerCase())) continue; n++;
      const kin = kinOf(w, seed);
      if (!NAME_FORM.test(w) || !kin.endsWith(w.slice(w.indexOf(" "))) && TRAIT_WORDS.has(w.slice(0, w.indexOf(" ")).toLowerCase())) bad++; }
    t("most names are deadpan forms, every one saying hake (" + n + ": " + [...seen].slice(0, 3).join(", ") + ")", !bad && n > 330 && n < 470 && [...seen].some(x => x.includes(" of ")) && [...seen].some(x => x.endsWith(" Situation"))
      && kinOf("Dusty Aunt Charcoal", 3) === "Sooty Aunt Charcoal" && kinOf("Smear of Mild Regret", 3) === "Smudge of Mild Regret"); }
  // Alike brushes differ, as in the app (BrushNamesChecks.SEEDS).
  { let picks = ""; const names = new Set();
    for (let seed = 0; seed < 64; seed++) { const n = describedName(mk(F, seed), "dapper-plum"); names.add(n); for (const w of n.split("-")) picks += w[0]; }
    t("alike brushes mostly differ (" + names.size + " of 64), as in the app", names.size >= 30 && picks === "UTIILPPSPOIBLSBIICIBILHDWIIIPBKIPPNSRISIBPLIIIPPNIDLCBIIPEIIPINI"); }
  // A variation keeps the rest under another fun word of its mood, an older drip-plum its drip (BrushNamesChecks.KIN, FUN_KIN), drawing as kinName does.
  { let kin = "", fun = ""; for (let seed = 0; seed < 8; seed++) { kin += kinOf("drip-plum", seed * 7919) + " "; fun += kinOf("sloppy-inky-drag", seed * 7919) + " "; }
    const keep = Math.random; let n = 0, m = 0;
    try { Math.random = () => (n++, .5); kinOf("drip-plum", 3); kinOf("sloppy-inky-drag", 3); kinOf("-", 3); Math.random = () => (m++, .5); kinName("drip-plum"); kinName("sloppy-inky-drag"); kinName("-"); } finally { Math.random = keep; }
    t("a variation keeps the describer and part, as in the app (" + fun.trim() + "), drawing as before (" + n + " " + m + ")",
      kin === "drip-yam drip-garlic drip-cherry drip-pear drip-garlic drip-peach drip-carrot drip-date " && fun === "loose-inky-drag gooey-inky-drag gooey-inky-drag loose-inky-drag gooey-inky-drag juicy-inky-drag lush-inky-drag loose-inky-drag "
      && n === m && /^[a-z]+-plum$/.test(kinOf("dapper-plum", 3))); }
  // 300 rolls with trait names on and off, the same Random: the same brush and seed; the name its words or a deadpan form, saying its part.
  { const roll = on => { const keep = Math.random, k0 = TRAIT_NAMES.on; Math.random = rng(5151); TRAIT_NAMES.on = on;
      try { return Array.from({ length: 300 }, (_, i) => rollStyle(STYLE_NAMES[i % STYLE_NAMES.length])); } finally { Math.random = keep; TRAIT_NAMES.on = k0; } };
    const a = roll(true), b = roll(false); let bad = 0; const names = new Set();
    for (let i = 0; i < a.length; i++) {
      names.add(a[i].name);
      if (encodeM(Object.assign(cloneBrush(a[i]), { name: b[i].name })) !== encodeM(b[i]) || !NAMES_ADJ.includes(b[i].name.split("-")[0]) || a[i].name !== brushNameOf(a[i]) || a[i].name.length > 32
        || !nameTwoLines(a[i].name) || !a[i].name.toLowerCase().includes(plainNameWords(a[i])[2].slice(0, 4))
        || !NAME_FORM.test(a[i].name)) bad++;
    }
    t("300 rolls are as before but for the name (" + names.size + " names: " + a.slice(0, 6).map(B => B.name).join(", ") + ")", !bad && names.size > 120); }
  // App codes the app names the same (BrushNamesChecks.LAB_NAMED).
  { const codes = [
      "wild-plum.M00j40p000000at_000zz6400000000000_0_044_1jz_82500000000005w0000nz0.1o028zx", "Inkish Pen Situation",
      "jumbo-okra.M10i0hpxm3d00at_000ve0000000000000_0_000_1jz_8zj0000000000i60000nz0.10hs76", "Regrettable Pen",
      "split-mango.M24fvokcxi06l9s00_0apzz0000000000000_0_100_1jz_8u700i8000000990000nz0.zwz2n7", "Inkish Split Situation",
      "smear-plum.M40i000000000at_4ht3b_3hmjsat_3sj_1jz_8egh500myeb00000000nz0.1154jqa", "Fuzzmonger Smear Junior",
      "float-quince.M60i0000000008t_1zued00000000ee_0_0h0_3lldii0_8p90000000000000000nz0.d21ddj", "Lazy Blotchy Float",
      "bristle-radish.Mb6vuj8jsqli0_2v400at_0_2qe_1jz_8dz0000000000000000nz0.171wrh6", "Ribbed Bristle",
      "lift-apple.M80fy000000000v_2zz00at_177at_100_1jz_8720000000000000000nz0.xgo4h0", "Painty Niece Lift",
      "lead-okra.Md22h84hw00rqs500ot_4ziua_0_000_1ul_8xb00dr00000000bz00nz1.qbqfn0", "Polite Scratchy Nib",
      "dust-bean.M51sgeei03s3fck00at_383lkdnwe_0_0xu_1jz_8uvlj00ufg700000000nz0.1eetlzr", "Chisel of Some Bother",
      "crease-parsnip.Mb0i00000yo007v_14hat00770000ee_0_4nj_1jz_8na0000000000000000nz0.ss1dt7", "Wash of Faint Worry",
      "hatch-bean.Md887dj671zzm3_2u900at_0_04u_1qe_80000ep008g00gy8z00nz0.4oy7gg", "Budget Creamy Hatch",
      "paint-beet.Md2a8qjeb006vik0066_2wp00at_0_26b_1re_8sx0000kf0000000000nz0.12l96hy", "Budget Scratchy Nib",
      "under-lychee.M70i000on0000uq_1om00f70000phee2_0_000_1jz_8o20000000000000000nz0.169plr8", "Wash of Minor Worry",
      "laid-shallot.Mb25aeei00000u100ak_19qat00770000ee_0_2ln_1jz_8fy0000000000000000nz0.gdoqm5", "Scratchy Cousin Nib",
      "rake-date.Md5c08gdu00j0df75_2v3dmat_0_100_1va_8pn000000000000d500nz0.mig41m", "Budget Ridged Rake",
      "white-quince.M80f6000000003k_2zz00at1_177at_04r_1jz_87c0000000000000000nz0.y41duu", "Chalky Lift Situation",
      "invert-kale.Md0bmh92cfc00aj_1htdc00008700ee3_0_500_1et_8eyoy00000000q60000nz0.yo9dw8", "Odd Flipped Wash",
      "mask-lychee.M60i000000000en_5_0_100_1jz_8em0000000000000000nz0.1qim1tm", "Prim Rubbery Mask",
      "grain-date.Md0ayfd000082yf_0g8ny0000000000000_0_1n6_1t2_8000000ky00pt000000nz0.rdle15", "Pen for Beginners"];
    const off = [];
    for (let i = 0; i < codes.length; i += 2) { const r = readAnyCode(codes[i]), w = r ? brushNameOf(r.b) : "?"; if (w !== codes[i + 1]) off.push(codes[i + 1] + " not " + w); }
    if (newMods) t("19 app codes name as in the app" + (off.length ? ": " + off.join(", ") : ""), !off.length); }
  return out;
}
function selfTest() {
  const legacy = LEGACY_CODES.map(c => legacyDiff(c));
  const trips = roundTrips();
  const proto = protoChecks();
  const float = floatChecks();
  const erase = eraseChecks();
  const names = nameChecks();
  const ok = legacy.every(l => l.ok) && !trips.length && proto.every(p => p.ok) && float.every(f => f.ok) && erase.every(f => f.ok) && names.every(f => f.ok);
  return { ok, legacy, trips, proto, float, erase, names };
}
