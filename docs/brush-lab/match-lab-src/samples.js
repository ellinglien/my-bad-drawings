// ===================================================================================================================
// Sample photos, made here (the page's "Try a sample" and the checks use the same ones):
//  - engine strokes: drawn by the engine with a known brush, then "photographed": rescaled, blurred, a warm uneven
//    light, paper grain and sensor noise, so a fit can be checked against the truth;
//  - drawn marks: made without the engine (a sumi stroke with hair streaks, a fountain pen that feathers, pencil on
//    cold press, an italic nib, two parallel lines, a watercolour stroke with a darker rim).
// ===================================================================================================================
// An S curve across a w × h box, every 1.5 px or more, light at the ends and firm in the middle, about 1 px a ms.
function samplePath(w, h, o = {}) {
  const raw = [], n = 400, x0 = o.x0 ?? w * .12, x1 = o.x1 ?? w * .88, amp = o.amp ?? h * .22;
  let d = 0, last = null;
  for (let i = 0; i <= n; i++) {
    const u = i / n, x = lerp(x0, x1, u), y = h / 2 + amp * Math.sin(u * Math.PI * (o.waves ?? 1.3) + (o.phase ?? .3));
    if (last) { const seg = Math.hypot(x - last[0], y - last[1]); if (seg < 1.5 && i < n) continue; d += seg; }
    last = [x, y]; raw.push({ x, y, u, d });
  }
  return raw.map(p => ({ x: p.x, y: p.y, p: o.press ? o.press(p.u) : .35 + .45 * Math.pow(Math.sin(Math.PI * p.u), .6), t: p.d, tx: NaN, ty: NaN, s: p.d / d }));
}
// A long, near-vertical stroke down a w × h box, gently bowed.
function verticalPath(w, h, o = {}) {
  const pts = []; let d = 0, last = null;
  for (let i = 0; i <= 300; i++) {
    const u = i / 300, x = w / 2 + w * .12 * Math.sin(u * Math.PI * .9 + .4) - w * .05, y = lerp(h * .08, h * .92, u);
    if (last) d += Math.hypot(x - last[0], y - last[1]);
    last = [x, y]; pts.push({ x, y, u, d });
  }
  return pts.map(p => ({ x: p.x, y: p.y, p: o.press ? o.press(p.u) : .6, t: p.d, tx: NaN, ty: NaN, s: p.d / d }));
}
// A soft ellipse into a coverage buffer: rx along the angle, ry across it.
function stampEllipse(buf, w, h, x, y, rx, ry, ang, amt) {
  const R = Math.ceil(Math.max(rx, ry) + 2), c = Math.cos(ang), s = Math.sin(ang);
  for (let py = Math.max(0, Math.floor(y - R)); py <= Math.min(h - 1, Math.ceil(y + R)); py++) for (let px = Math.max(0, Math.floor(x - R)); px <= Math.min(w - 1, Math.ceil(x + R)); px++) {
    const dx = px + .5 - x, dy = py + .5 - y, u = (dx * c + dy * s) / Math.max(.5, rx), v = (-dx * s + dy * c) / Math.max(.5, ry), r = Math.sqrt(u * u + v * v);
    const a = clamp((1 - r) * Math.max(.5, Math.min(rx, ry)) + .5) * amt, i = py * w + px;
    if (a > buf[i]) buf[i] = a;
  }
}
// A bar across the stroke (a flat brush's edge): half-length L across the travel, a pixel thick, anti-aliased.
function stampBar(buf, w, h, x, y, L, ang, amt) {
  const R = Math.ceil(L + 2), c = Math.cos(ang), s = Math.sin(ang);
  for (let py = Math.max(0, Math.floor(y - R)); py <= Math.min(h - 1, Math.ceil(y + R)); py++) for (let px = Math.max(0, Math.floor(x - R)); px <= Math.min(w - 1, Math.ceil(x + R)); px++) {
    const dx = px + .5 - x, dy = py + .5 - y, along = dx * c + dy * s, across = -dx * s + dy * c;
    const a = clamp(L - Math.abs(across) + .5) * clamp(1 - Math.abs(along) + .5) * amt, i = py * w + px;
    if (a > buf[i]) buf[i] = a;
  }
}
function alongPath(pts, every, fn) {
  for (let k = 1; k < pts.length; k++) {
    const a = pts[k - 1], b = pts[k], seg = Math.hypot(b.x - a.x, b.y - a.y), n = Math.max(1, Math.ceil(seg / every)), ang = Math.atan2(b.y - a.y, b.x - a.x);
    for (let j = 0; j < n; j++) { const t = j / n; fn(lerp(a.x, b.x, t), lerp(a.y, b.y, t), lerp(a.s, b.s, t), ang); }
  }
}
// The camera: darkness (0 paper .. 1 black) to a warm RGB photo under uneven light, with paper grain and noise.
function photographMark(dark, w, h, o = {}) {
  const r = rng(o.seed || 5), k = o.scale || 1, W = Math.round(w * k), H = Math.round(h * k);
  let d = resample(dark, w, h, W, H);
  // A real pen's ragged edge and a hand's wobble (the noise check): the mark pushed about by o.ragged px over a few px,
  // and by o.wobble px over about 60 px.
  if (o.ragged || o.wobble) {
    const f = [3, 3, 60, 60].map(c => valueNoise(W, H, c * k, r)), src = d; d = new Float32Array(W * H);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const i = y * W + x, a = 2 * k * (o.ragged || 0), b = 2 * k * (o.wobble || 0);
      d[i] = bilinear(src, W, H, clamp(x + a * (f[0][i] - .5) + b * (f[2][i] - .5), 0, W - 1.001), clamp(y + a * (f[1][i] - .5) + b * (f[3][i] - .5), 0, H - 1.001)); }
  }
  d = gauss(d, W, H, o.blur ?? 1.1);
  const grain = valueNoise(W, H, 2.2, r), lumps = valueNoise(W, H, 30, r), px = new Uint8Array(W * H * 3), ink = o.ink ?? .9, nz = o.noise ?? .018;
  // Textured paper: a coarser tooth, and faint grey speckle (fibres, flecks) all over the sheet.
  const tex = o.texture ? valueNoise(W, H, 5, r) : null, fleck = o.speckle ? new Float32Array(W * H) : null;
  if (fleck) for (let k = 0; k < W * H * o.speckle; k++) { const x = r() * W, y = r() * H, rr = .6 + r() * 1.4, a = .05 + r() * .12; for (let yy = Math.floor(y - 2); yy <= y + 2; yy++) for (let xx = Math.floor(x - 2); xx <= x + 2; xx++) { if (xx < 0 || yy < 0 || xx >= W || yy >= H) continue; const dd = Math.hypot(xx + .5 - x, yy + .5 - y); if (dd < rr) fleck[yy * W + xx] = Math.max(fleck[yy * W + xx], a * clamp(rr - dd)); } }
  const n = () => (r() + r() + r() - 1.5) * nz;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x, u = x / W - .5, v = y / H - .5;
    const light = (.92 - .22 * (u * .8 + v * .6) - .35 * (u * u + v * v)) * (1 + .03 * (lumps[i] - .5));
    const paper = (1 + (o.grain ?? .05) * (grain[i] - .5) + (tex ? o.texture * (tex[i] - .5) : 0)) * (1 - (fleck ? fleck[i] : 0));
    const t = paper * (1 - ink * clamp(d[i], 0, 1.2));
    px[i * 3] = clamp(light * t + n()) * 255; px[i * 3 + 1] = clamp(light * t * .96 + n()) * 255; px[i * 3 + 2] = clamp(light * t * .88 + n()) * 255;
  }
  return { w: W, h: H, px };
}
// A brush for a sample: parts over defaults, the hand's pressure → size.
function sampleBrush(parts, ps) {
  const B = { name: "truth", seed: 4242, style: "Anything goes" };
  for (const p of PARTS) B[p] = freshPart(p, CHOICES[p][0].k);
  B.life = freshPart("life", "wet");
  for (const [p, v] of Object.entries(parts)) { B[p] = freshPart(p, v.k); Object.assign(B[p].d, v.d || {}); }
  B.hand = freshPart("hand", "custom"); B.hand.d.ps = ps ?? 0;
  return normBrush(B);
}
const SAMPLE_ENGINE = {
  "e1-fineliner": { label: "Fineliner", parts: { tip: { k: "round", d: { taperIn: .05, taperOut: .1 } }, medium: { k: "ink" } }, ps: .2, size: 5, shade: 1, box: [640, 300], photo: { scale: 1.8 } },
  "e2-brush-pen": { label: "Brush pen", parts: { tip: { k: "round", d: { taperIn: .45, taperOut: .8, dry: .15, wobble: .05 } }, medium: { k: "ink" } }, ps: .9, size: 14, shade: 1, box: [640, 320], photo: { scale: 1.4 } },
  "e3-italic-nib": { label: "Italic nib", parts: { tip: { k: "flat", d: { aspect: .2, angle: 40, taperIn: 0, taperOut: .1 } }, medium: { k: "ink", d: { pool: .3 } } }, ps: .3, size: 16, shade: 1, box: [600, 340], photo: { scale: 1.2, waves: 1.6 } },
  "e4-split-nib": { label: "Split nib", parts: { tip: { k: "split", d: { gap: .7, aspect: .42, angle: 40 } }, medium: { k: "ink" } }, ps: .7, size: 9, shade: 1, box: [620, 300], photo: { scale: 2 } },
  "e5-multi-line": { label: "Three lines", parts: { tip: { k: "multi", d: { count: 3, reach: .55 } }, medium: { k: "ink" } }, ps: .3, size: 4, shade: 1, box: [620, 300], photo: { scale: 2.2 } },
  "e6-watercolour": { label: "Watercolour", parts: { tip: { k: "round", d: { soft: .4 } }, medium: { k: "wash", d: { wet: .85, pool: .45, bloom: .3 } } }, ps: .5, size: 70, shade: .85, box: [700, 380], photo: { scale: .9, amp: .18 } },
  "e7-charcoal": { label: "Charcoal", parts: { tip: { k: "round", d: { taperIn: .1, taperOut: .2 } }, medium: { k: "charcoal", d: { spread: .3, hold: .55 } }, paper: { k: "cold press", d: { tooth: .75 } } }, ps: .6, size: 26, shade: 1, box: [640, 340], photo: { scale: 1.3 } },
  "e9-flat-end": { label: "Flat end", parts: { tip: { k: "hake", d: { dry: 0, soft: .1 } }, medium: { k: "ink" } }, ps: 0, size: 30, shade: 1, box: [360, 700], photo: { scale: 1.2, ink: .55, texture: .08, speckle: .002, vertical: true } },
  "e8-dry-brush": { label: "Dry brush", parts: { tip: { k: "round", d: { dry: .7, soft: .2 } }, medium: { k: "wash", d: { wet: .3 } }, paper: { k: "cold press", d: { tooth: .5 } } }, ps: .4, size: 46, shade: 1, box: [660, 340], photo: { scale: 1.1 } }
};
const SAMPLE_DRAWN = {
  // A sumi stroke: 40 hairs, each its own line, running dry toward the end; a pressed, tapered body.
  "d1-sumi-stroke": { label: "Sumi brush", about: "sumi brush: hair streaks, runs dry toward the end", photo: { scale: 1.1, blur: 1 }, make() {
    const w = 760, h = 360, buf = new Float32Array(w * h), r = rng(11), pts = samplePath(w, h, { amp: 70, waves: 1.1 });
    const hairs = Array.from({ length: 40 }, () => ({ off: r() * 2 - 1, load: .7 + .3 * r(), dry: .55 + .45 * r(), wig: r() * 6 })), field = valueNoise(w, h, 5, r);
    alongPath(pts, .5, (x, y, s, a) => {
      const W = 46 * Math.pow(Math.sin(Math.PI * Math.min(1, s * 1.05 + .02)), .5) * (.75 + .25 * Math.sin(s * 3)), nx = -Math.sin(a), ny = Math.cos(a);
      for (const hr of hairs) {
        const o = hr.off * W / 2 * (1 + .15 * Math.sin(s * 9 + hr.wig)), X = x + nx * o, Y = y + ny * o, dryness = clamp((s - hr.dry * .7) / .3), i = (Y | 0) * w + (X | 0);
        if (i < 0 || i >= w * h || field[i] < dryness * .9) continue;
        stampEllipse(buf, w, h, X, Y, 2.2, 2.2, 0, hr.load * (1 - .5 * dryness));
      }
    });
    return { w, h, dark: buf };
  } },
  // A fountain pen that feathers on soft paper: a crisp line, a halo along the fibres, a few hairline spikes.
  "d2-feathered-pen": { label: "Feathered pen", about: "fountain pen on soft paper: feathered halo", photo: { scale: 1.3, blur: 1 }, make() {
    const w = 700, h = 300, buf = new Float32Array(w * h), r = rng(12), pts = samplePath(w, h, { amp: 60, press: () => .6 });
    alongPath(pts, .4, (x, y) => stampEllipse(buf, w, h, x, y, 2.6, 2.6, 0, 1));
    const fib = gauss(valueNoise(w, h, 1.3, r), w, h, .8), halo = gauss(buf, w, h, 3.5), out = new Float32Array(w * h);
    for (let i = 0; i < out.length; i++) out[i] = Math.max(buf[i], clamp(halo[i] * 1.2) * .55 * clamp((fib[i] - .3) * 2.2));
    for (let k = 0; k < 40; k++) { const p = pts[(r() * pts.length) | 0], a = r() * Math.PI * 2, L = 3 + r() * 7; for (let t = 0; t < L; t += .5) stampEllipse(out, w, h, p.x + Math.cos(a) * (3 + t), p.y + Math.sin(a) * (3 + t), .6, .6, 0, .5 * (1 - t / L)); }
    return { w, h, dark: out };
  } },
  // Pencil on cold press: the lead touches the high points; a grey, grainy line.
  "d3-pencil": { label: "Pencil", about: "pencil on cold-press paper", photo: { scale: 1.5, blur: .9 }, make() {
    const w = 680, h = 300, buf = new Float32Array(w * h), r = rng(13), pts = samplePath(w, h, { amp: 55 });
    alongPath(pts, .4, (x, y) => stampEllipse(buf, w, h, x, y, 3.2, 3.2, 0, .9));
    const paper = valueNoise(w, h, 1.8, r), mid = valueNoise(w, h, 6, r), out = new Float32Array(w * h);
    for (let i = 0; i < out.length; i++) out[i] = buf[i] * .62 * clamp((paper[i] * .6 + mid[i] * .4 - .32) * 3.2);
    return { w, h, dark: out };
  } },
  // An italic nib held at 140° (a left hand's angle): hairlines and full strokes.
  "d4-italic": { label: "Italic, left hand", about: "italic nib at 140° (outside the engine's 20–70°)", photo: { scale: 1.6, blur: 1.2 }, make() {
    const w = 620, h = 360, buf = new Float32Array(w * h), pts = samplePath(w, h, { amp: 90, waves: 1.8 });
    alongPath(pts, .3, (x, y) => stampEllipse(buf, w, h, x, y, 9, 1.4, -40 * Math.PI / 180, 1));
    return { w, h, dark: buf };
  } },
  // Two parallel lines, as a ruling pen or a split nib held open.
  "d5-two-lines": { label: "Two lines", about: "two parallel lines", photo: { scale: 1.8, blur: 1 }, make() {
    const w = 680, h = 300, buf = new Float32Array(w * h), pts = samplePath(w, h, { amp: 60 });
    alongPath(pts, .4, (x, y, s, a) => { const nx = -Math.sin(a), ny = Math.cos(a), g = 4.5 + 1.5 * Math.sin(Math.PI * s); for (const sg of [-1, 1]) stampEllipse(buf, w, h, x + nx * g * sg, y + ny * g * sg, 1.5, 1.5, 0, 1); });
    return { w, h, dark: buf };
  } },
  // A long brush line in black ink, near vertical, the start pressed in from a point and the end cut square: shot
  // dim on off-white textured paper with grey flecks (low contrast).
  "d7-low-contrast": { label: "Dim photo", about: "black brush line, square end, low contrast on textured, flecked paper", photo: { scale: 1, blur: 1.1, ink: .5, texture: .1, speckle: .003, grain: .08 }, make() {
    const w = 360, h = 900, buf = new Float32Array(w * h), pts = verticalPath(w, h);
    alongPath(pts, .4, (x, y, s, a) => { const W = 22 * Math.min(1, s / .12) ** .7 * (1 + .08 * Math.sin(s * 9)); stampBar(buf, w, h, x, y, Math.max(.6, W / 2), a, 1); });
    return { w, h, dark: buf };
  } },
  // A narrower black brush line, gently S-curved down the page, pressed in from a point and lifted with a short
  // square cut slanted 35° to the stroke; clean paper.
  "d9-slant-cut": { label: "Slant cut", about: "narrow black line, square end cut at 35°, clean paper", photo: { scale: 1, blur: 1 }, make() {
    const w = 300, h = 900, buf = new Float32Array(w * h), pts = [];
    let d = 0, last = null;
    for (let i = 0; i <= 320; i++) { const u = i / 300, x = w / 2 + 26 * Math.sin(u * Math.PI * 1.6), y = lerp(70, 830, u); if (last) d += Math.hypot(x - last[0], y - last[1]); last = [x, y]; pts.push({ x, y, d }); }
    const L = pts[300].d, end = pts[300], prev = pts[290], ex = end.x - prev.x, ey = end.y - prev.y, el = Math.hypot(ex, ey), ux = ex / el, uy = ey / el;
    const cut = 35 * Math.PI / 180, cx = ux * Math.cos(cut) - uy * Math.sin(cut), cy = ux * Math.sin(cut) + uy * Math.cos(cut);
    alongPath(pts.map(p => Object.assign(p, { s: p.d / L })), .4, (x, y, s, a) => stampBar(buf, w, h, x, y, 11 * Math.min(1, s / .1) ** .7, a, 1));
    // The cut: everything past a line through the end, slanted 35° from square across the stroke.
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { const t = (x + .5 - end.x) * cx + (y + .5 - end.y) * cy; if (t > -.5) buf[y * w + x] *= clamp(.5 - t); }
    return { w, h, dark: buf };
  } },
  // A long brush line pressed lightly for its first third (a third of its later width), then pressed down, widening
  // through the middle, lifted with a square cut; mid-contrast, textured paper.
  "d10-thin-top": { label: "Thin top", about: "thin top third, three times wider below, square end, textured paper", photo: { scale: 1, blur: 1.1, ink: .7, texture: .07, speckle: .002 }, make() {
    const w = 320, h = 960, buf = new Float32Array(w * h), pts = verticalPath(w, h);
    alongPath(pts, .4, (x, y, s, a) => { const W = 26 * (s < .03 ? s / .03 * .33 : s < .33 ? .33 : .33 + .67 * smooth((s - .33) / .3)); stampBar(buf, w, h, x, y, Math.max(.6, W / 2), a, 1); });
    return { w, h, dark: buf };
  } },
  // A thinner brush line whose top third wavers in width as the hand settles, then widens and ends square;
  // mid-contrast, textured paper.
  "d11-wobble-top": { label: "Wobbly top", about: "width wobbles in the top third, then widens; square end", photo: { scale: 1, blur: 1.1, ink: .65, texture: .08, speckle: .002 }, make() {
    const w = 300, h = 900, buf = new Float32Array(w * h), pts = verticalPath(w, h);
    alongPath(pts, .4, (x, y, s, a) => { const base = 7 + 9 * smooth((s - .25) / .4), wob = s < .38 ? 1 + .28 * Math.sin(s * Math.PI * 2 * 9) : 1, W = base * wob * Math.min(1, s / .05) ** .7; stampBar(buf, w, h, x, y, Math.max(.6, W / 2), a, 1); });
    return { w, h, dark: buf };
  } },
  // A flat brush pulled in one go: an even band, both ends cut square across it.
  "d8-flat-brush": { label: "Flat brush", about: "flat brush: even width, both ends cut square", photo: { scale: 1.1, blur: 1 }, make() {
    const w = 760, h = 340, buf = new Float32Array(w * h), pts = samplePath(w, h, { amp: 60, waves: 1.1 });
    alongPath(pts, .4, (x, y, s, a) => stampBar(buf, w, h, x, y, 17, a, .97));
    return { w, h, dark: buf };
  } },
  // A watercolour stroke: grey, soft edged, darker at its rim, with blooms.
  "d6-wash": { label: "Wash", about: "watercolour: soft edge, darker rim, blooms", photo: { scale: 1, blur: 1.2, grain: .07 }, make() {
    const w = 760, h = 400, buf = new Float32Array(w * h), r = rng(16), pts = samplePath(w, h, { amp: 80, waves: 1 });
    alongPath(pts, 1, (x, y, s) => stampEllipse(buf, w, h, x, y, 34 + 6 * Math.sin(s * 5), 34 + 6 * Math.sin(s * 5), 0, 1));
    const soft = gauss(buf, w, h, 2.5), inner = gauss(buf, w, h, 7), bloom = valueNoise(w, h, 40, r), out = new Float32Array(w * h);
    for (let i = 0; i < out.length; i++) out[i] = soft[i] * (.3 + .12 * (bloom[i] - .5)) + Math.max(0, soft[i] - inner[i]) * 2.2 * .22;
    return { w, h, dark: out };
  } }
};
// One sample as an RGB photo, with what made it; extra: more for the camera (ragged, wobble, noise).
function makeSample(name, extra) {
  const e = SAMPLE_ENGINE[name];
  if (e) {
    const B = sampleBrush(e.parts, e.ps), [w, h] = e.box, s = modSurface(w, h, 77);
    const pts = e.photo.vertical ? verticalPath(w, h) : samplePath(w, h, { waves: e.photo.waves, amp: e.photo.amp ? h * e.photo.amp : undefined });
    // A hand eases off at the end of a stroke: the last samples lighter, then held.
    pts[pts.length - 2].p *= .45; pts[pts.length - 1].p *= .15; pts[pts.length - 1].t += 60;
    layStroke(s, B, pts, e.size, e.shade, null);
    const dark = Float32Array.from(s.page, v => 1 - v / PAPER_TONE);
    return Object.assign(photographMark(dark, w, h, Object.assign({ seed: name.length * 31 }, e.photo, extra)), { truth: { kind: "engine", code: encodeM(B), struct: { tip: B.tip.k, medium: B.medium.k, paper: B.paper.k, life: B.life.k }, size: e.size, shade: e.shade, photoScale: e.photo.scale } });
  }
  const d = SAMPLE_DRAWN[name], m = d.make();
  return Object.assign(photographMark(m.dark, m.w, m.h, Object.assign({ seed: name.length * 17 }, d.photo, extra)), { truth: { kind: "drawn", about: d.about } });
}
const SAMPLE_NAMES = [...Object.keys(SAMPLE_ENGINE), ...Object.keys(SAMPLE_DRAWN)];
