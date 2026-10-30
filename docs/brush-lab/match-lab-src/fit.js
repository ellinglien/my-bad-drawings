// ===================================================================================================================
// Match a stroke, part 2: the fit. Inverse rendering with the real engine: a candidate brush is drawn along the photo's
// own centre line (pressure solved from the photo's widths, a steady hand), softened and speckled like the camera, and
// scored against the photo. Coarse candidates (Tip × Medium × Life, from the analysis) each get a short look; the best
// two get a Nelder–Mead search over their dials; then the paper. No learning anywhere.
// ===================================================================================================================
const FIT = { pMid: .6, speed: 1, budgetMs: 5000 };
const LINE_MEDIA = { ink: 1, charcoal: 1, graphite: 1, crayon: 1 };
// Media that sit on the paper's tooth: cold press to start with (the paper step tries the rest).
const TOOTHY = { charcoal: 1, graphite: 1, crayon: 1 };

// ---------- a candidate: a structure (the choices) plus a vector of dial values ----------
// struct: { tip, medium, life, paper, count?, hairs? }   x: { key: value } over FREE dials, plus size and shade.
// Keys are "part.dial", "sizeK" (log of the size against the analysis's guess), "shade" and "hand.ps".
function brushOf(struct, x, seed) {
  const B = { name: "matched", seed: seed >>> 0, style: "Anything goes" };
  B.tip = freshPart("tip", struct.tip); B.medium = freshPart("medium", struct.medium); B.carry = freshPart("carry", "off");
  B.paper = freshPart("paper", struct.paper || "plain"); B.life = freshPart("life", struct.life || "wet"); B.hand = freshPart("hand", "custom");
  if (struct.count) B.tip.d.count = struct.count;
  if (struct.hairs) B.tip.d.hairs = struct.hairs;
  for (const [k, v] of Object.entries(struct.fixed || {})) { const [p, d] = k.split("."); B[p].d[d] = v; }
  for (const [k, v] of Object.entries(x)) {
    if (k === "sizeK" || k === "shade") continue;
    const [p, d] = k.split(".");
    if (B[p] && d in B[p].d) B[p].d[d] = v;
  }
  for (const key of ["ps", "pd", "pw", "ts", "td", "tw", "ss", "sd", "sw"]) B.hand.d[key] = key === "ps" ? (x["hand.ps"] ?? 0) : 0;
  B.hand.d.gain = 1; B.hand.d.dir = 0;
  return normBrush(B);
}
// What draws the visible width: the line (ink, charcoal, graphite while they stay mostly dry) or the body of paint.
function widthLaw(B) {
  const mk = B.medium.k, line = LINE_MEDIA[mk] && !(mk === "ink" && B.medium.d.wet > .55);
  const P = Math.min(1, B.hand.d.ps * B.hand.d.gain);
  if (B.tip.k === "hake") return { line: false, f: p => .8 + .2 * Math.min(1, Math.pow(clamp(p), 1.2)) };
  if (line) return { line: true, f: p => lerp(1 - .75 * P, 1 + .6 * P, p) };
  const tip = lerp(.6, .06, P);
  return { line: false, f: p => tip + (1 - tip) * Math.pow(clamp(p), 1.2) };
}
// The size the analysis expects for this brush, before the fit's own factor: the body's width at a usual pressure.
function baseSize(job, B) {
  const law = widthLaw(B), env = median(job.envelope.slice(job.body[0], job.body[1] + 1));
  let w = env / law.f(FIT.pMid);
  const tk = B.tip.k;
  if (law.line) {
    if (tk === "chisel" || tk === "flat") w /= Math.max(.35, median(job.theta.slice(job.body[0], job.body[1] + 1).map(t => nibFactor(t, (B.tip.d.angle) * Math.PI / 180, B.tip.d.aspect))));
    if (tk === "split") w /= 1.1 + .5 * B.tip.d.gap;
    if (tk === "multi") w /= (2 + 1.4 * B.tip.d.count) * (.25 + .45 * B.tip.d.reach);
    if (tk === "bristles") w /= 1.15;
  } else {
    w /= 1 - .25 * (B.tip.d.soft ?? .3);
  }
  return w;
}
// The pen's samples along the photo's centre line: pressure from the widths through the brush's own width law, about
// 1 px a millisecond, easing off over the last two samples, then held a moment (so the Fast-wet tail re-tapers).
function pointsFor(job, B) {
  const law = widthLaw(B), n = job.path.length, P = Math.min(1, B.hand.d.ps * B.hand.d.gain);
  const [b0, b1] = job.body, envMed = median(job.envelope.slice(b0, b1 + 1)), fMid = law.f(FIT.pMid);
  const every = job.Wb > 40 ? Math.max(1, Math.round(.05 * job.Wb / job.ds)) : 1, at = [];
  for (let i = 0; i < n - 1; i += every) at.push(i);
  at.push(n - 1);
  const pts = [];
  let d = 0, last = null, t = 0;
  at.forEach((i, k) => {
    const [x, y] = job.path[i];
    if (last) { const seg = Math.hypot(x - last[0], y - last[1]); d += seg; t += seg / FIT.speed; }
    last = job.path[i];
    let p = FIT.pMid;
    if (P > .02) {
      const want = fMid * job.envelope[i] / envMed;
      let lo = 0, hi = 1;
      for (let it = 0; it < 22; it++) { const m = (lo + hi) / 2; if (law.f(m) < want) lo = m; else hi = m; }
      p = (lo + hi) / 2;
    }
    // A hand eases off into a tapered or round end; a square end is lifted at full pressure.
    if (k >= at.length - 2 && !squareEnd(job, false)) p *= k === at.length - 1 ? .15 : .45;
    pts.push({ x, y, p: clamp(p, .02, 1), t, tx: NaN, ty: NaN });
  });
  // A flat head's ribbon stops where its last sample is: it is drawn out to each square end's edge (a round tip's
  // cap reaches past its last sample by itself).
  if (B.tip.k === "hake" && job.ends && pts.length > 1) {
    const reach = (E, from) => { const out = []; for (let t = 1; t < E.tip - .5; t += 1) out.push([from.x + E.dir[0] * t, from.y + E.dir[1] * t]); return out; };
    const head = reach(job.ends[0], pts[0]).reverse(), tail = reach(job.ends[1], pts[pts.length - 1]);
    const p0 = pts[0], pl = pts[pts.length - 1];
    pts.unshift(...head.map(([x, y]) => ({ x, y, p: p0.p, t: 0, tx: NaN, ty: NaN })));
    pts.push(...tail.map(([x, y]) => ({ x, y, p: pl.p, t: 0, tx: NaN, ty: NaN })));
    let tt = 0; pts.forEach((q, i) => { if (i) tt += Math.hypot(q.x - pts[i - 1].x, q.y - pts[i - 1].y) / FIT.speed; q.t = tt; });
  }
  if (pts.length > 1) { const L = pts[pts.length - 1]; L.t += 60; }
  return pts;
}
const squareEnd = (job, first) => !!(job.ends && job.ends[first ? 0 : 1].square > .65);
// The candidate drawn: darkness 0 (paper) to 1 (black), before the camera.
function renderFit(job, B, size, shade) {
  if (!job.surf) job.surf = modSurface(job.w, job.h, 2026);
  const s = job.surf;
  blankPage(s);
  layStroke(s, B, pointsFor(job, B), size, shade, null);
  const out = new Float32Array(job.w * job.h);
  for (let i = 0; i < out.length; i++) out[i] = 1 - s.page[i] / PAPER_TONE;
  return out;
}
// The camera's blur, from a first candidate: a crisp line is softened until its edges are as soft as the photo's.
// (Paint's soft edge is the brush's own, so paint is left as drawn.)
function cameraBlur(job, B, size, shade) {
  job.blurSigma = 0;
  if (!widthLaw(B).line) return 0;
  const img = camera(job, renderFit(job, B, size, shade), true), sp = edgeSigma(job, job.D), sr = edgeSigma(job, img);
  return job.blurSigma = clamp(Math.sqrt(Math.max(0, sp * sp - sr * sr)), 0, Math.min(2.5, .2 * job.Wb));
}
// The camera: the photo's blur and its sensor noise.
// Then the paper's floor, as the photo had it (paperModel): speckle at the paper's spread, 2.5 spreads taken off.
// For crisp ink the same near black-and-white curve as the photo had.
function camera(job, img, noNoise) {
  let out = job.blurSigma > .3 ? gauss(img, job.w, job.h, job.blurSigma) : Float32Array.from(img);
  const sg = job.noiseSigma || 0, f = 2.5 * sg, k = 1 / (1 - f);
  if (job.paper && job.paper.inkLike) { for (let i = 0; i < out.length; i++) out[i] = inkCurve(out[i] + (noNoise || sg <= .002 ? 0 : job.noise[i])); return out; }
  if (sg > .002) for (let i = 0; i < out.length; i++) out[i] = clamp((out[i] + (noNoise ? 0 : job.noise[i]) - f) * k, 0, 1.2);
  return out;
}

// ---------- the score: how unlike the photo a candidate is (0 is the photo itself) ----------
// Each term is in units of the difference that just shows, so they add up. Measured on the photo and on candidates the
// same way, through the same slight blur.
const huber = x => { const a = Math.abs(x); return a <= 1 ? a * a : 2 * a - 1; };
// Roughness and grain weigh less since 2026-10-27 (rough .35 and spec .35 before): a real pen's ragged edge and the
// camera's noise pushed fits toward textured brushes.
const SCORE_UNITS = { iou: .06, l1: .2, width: .08, tone: .06, rough: .6, spec: .5, dry: .05, halo: .05, haloOut: .04, hollow: .12, ramp: .1, streak: .02,
  sat: .35, runs: .35, bloom: .04, speck: .06, pool: .15, ends: .12, haloBand: .05 };
function scoreFit(job, img, detail) {
  const R = camera(job, img), Rf = gauss(R, job.w, job.h, job.commonBlur), F = features(job, Rf), P = job.F;
  // The halo band on the brush as drawn (before the camera's curve, which would hide a pale band), against the
  // photo's only where it has a halo.
  const hbRaw = haloFracOf(job, job.blurSigma > .3 ? gauss(img, job.w, job.h, job.blurSigma) : img, F);
  // Silhouette: the photo's threshold on both, over the whole frame.
  const thr = P.thr; let inter = 0, uni = 0;
  for (let i = 0; i < Rf.length; i++) { const a = job.Df[i] > thr, b = Rf[i] > thr; if (a && b) inter++; if (a || b) uni++; }
  const iou = uni ? inter / uni : 1;
  const Rb = blur(R, job.w, job.h, job.blurR);
  let l1 = 0; for (const i of job.band) l1 += Math.abs(Rb[i] - job.Db[i]);
  l1 = l1 / job.band.length / Math.max(.02, job.Dmean);
  let we = 0; for (let i = 0; i < P.widths.length; i++) we += Math.abs(F.widths[i] - P.widths[i]);
  const width = we / P.widths.length / Math.max(1, P.Wmed);
  let cp = 0, cc = 0, tone = 0; for (let k = 0; k < 24; k++) { cp += P.toneHist[k]; cc += F.toneHist[k]; tone += Math.abs(cp - cc) * .05; }
  const specC = spectrum(job, R); let spec = 0;
  for (let k = 0; k < specC.length; k++) spec += Math.abs(Math.log((specC[k] + .02) / (P.spec[k] + .02)));
  spec /= specC.length;
  const sat = satellites(job, R, thr), ends = job.Wb >= 6 ? [endShape(job, Rf, thr, true), endShape(job, Rf, thr, false)] : job.ends;
  const d = {
    iou: 1 - iou, l1, width, tone, rough: (roughExcess(F) - roughExcess(P)) / Math.max(1, P.Wmed * .1), spec,
    dry: F.dry - P.dry, halo: F.halo - P.halo, haloBand: hbRaw - (job.paper && job.paper.haloReal ? P.haloFrac : 0), haloOut: F.haloOut - P.haloOut, hollow: (F.hollow - P.hollow) / (1 + Math.abs(P.hollow)), ramp: F.ramp - P.ramp,
    streak: F.streak - P.streak, sat: (sat.n - P.satN) / Math.max(1, Math.sqrt(P.satN + 1)), runs: F.runsMean - P.runsMean, bloom: F.bloom - P.bloom,
    speck: F.speck - P.speck, pool: Math.max(F.poolS - P.poolS, P.poolS - F.poolS, 0) * .5 + Math.abs(F.poolE - P.poolE) * .5,
    // Ends count where either side is square: round against tapered is the width profile's business.
    ends: job.Wb >= 6 ? [0, 1].reduce((a, i) => a + (Math.max(ends[i].square, job.ends[i].square) > .65 ? Math.abs(ends[i].square - job.ends[i].square) : 0), 0) / 2 : 0
  };
  let total = 0; const parts = {};
  // A thin line's silhouette can't overlap better than its edge pixels allow: the unit grows as the line thins.
  const units = Object.assign({}, SCORE_UNITS, { iou: Math.max(.05, 1.2 / Math.max(1, P.Wmed / Math.max(1, P.runsMean))) });
  for (const k in units) { const e = huber(d[k] / units[k]) * (k === "iou" || k === "width" || k === "l1" || k === "ends" || k === "haloBand" ? 2 : 1); parts[k] = e; total += e; }
  if (!detail) return total;
  return { total, parts, raw: d, iou, l1, width, tone, spec, F, specC, sat, R, ends };
}

// ---------- Nelder–Mead in the unit cube (values clamped), adaptive coefficients for higher dimensions ----------
function* nelderMead(f, x0, step, maxEvals) {
  const n = x0.length, a = 1, g = 1 + 2 / n, r = .75 - 1 / (2 * n), s = 1 - 1 / n;
  const cl = v => v.map(t => clamp(t));
  let S = [cl(x0)];
  for (let i = 0; i < n; i++) { const v = x0.slice(); v[i] = v[i] + step[i] > 1 ? v[i] - step[i] : v[i] + step[i]; S.push(cl(v)); }
  let F = [];
  for (const v of S) { F.push(f(v)); yield; }
  let evals = S.length;
  while (evals < maxEvals) {
    const ord = F.map((v, i) => i).sort((i, j) => F[i] - F[j]);
    S = ord.map(i => S[i]); F = ord.map(i => F[i]);
    if (F[n] - F[0] < 1e-3 && Math.max(...S.map(v => Math.max(...v.map((t, k) => Math.abs(t - S[0][k]))))) < .01) break;
    const c = new Array(n).fill(0);
    for (let i = 0; i < n; i++) for (let k = 0; k < n; k++) c[k] += S[i][k] / n;
    const at = t => cl(c.map((ck, k) => ck + t * (S[n][k] - ck)));
    const xr = at(-a), fr = f(xr); evals++; yield;
    if (fr < F[0]) { const xe = at(-a * g), fe = f(xe); evals++; yield; if (fe < fr) { S[n] = xe; F[n] = fe; } else { S[n] = xr; F[n] = fr; } }
    else if (fr < F[n - 1]) { S[n] = xr; F[n] = fr; }
    else {
      const out = fr < F[n], xc = at(out ? -r : r), fc = f(xc); evals++; yield;
      if (fc < (out ? fr : F[n])) { S[n] = xc; F[n] = fc; }
      else { for (let i = 1; i <= n; i++) { S[i] = cl(S[i].map((t, k) => S[0][k] + s * (t - S[0][k]))); F[i] = f(S[i]); evals++; yield; } }
    }
  }
  let b = 0; for (let i = 1; i < F.length; i++) if (F[i] < F[b]) b = i;
  return { x: S[b], f: F[b] };
}

// ---------- a tip and a category chosen before the scan (the owner, 2026-10-27: "it should allow you to select a nib
// first to start, and a starting point for what category it's in") ----------
// Tip: the engine's Tip choices the fit can draw; a hard limit. Category: the tool picker's groups, in DrawLayout.PICKER's
// order (the app shows no headers), each with its drawn tools and the tips and media they draw with: those are seeded
// and preferred by CATEGORY_PRIOR (a unit of loss each), and the drawn tools compared are the group's. Either one makes
// the search shorter (CHOSEN_BUDGET of the time). Oils' Loaded oils (wet paint) doesn't read in this engine: Opaque
// paint stands in. Odd Ones (a spirograph, screentone, fading inks) can't be told from one stroke: ink and wash.
const FIT_TIPS = ["round", "chisel", "flat", "split", "multi", "bristles", "hake"];
const CATEGORIES = [
  { label: "Pens", tools: ["Steady line", "Ballpoint", "Fountain pen", "Split nib", "Fude pen", "Stipple pen", "Engraving pen"], tips: ["round", "flat", "split"], media: ["ink"] },
  { label: "Markers", tools: ["Felt marker", "Chisel marker", "Highlighter"], tips: ["chisel", "round"], media: ["ink", "wash"] },
  { label: "Brushes", tools: ["Springy brush", "Swell brush", "Sumi", "Comb", "Flat hake"], tips: ["round", "bristles", "multi", "hake"], media: ["ink", "wash"] },
  { label: "Washes", tools: ["Water brush", "Soft wash", "Wet blooms", "Pooling wash", "Masking fluid", "Wax resist"], tips: ["round", "hake", "bristles"], media: ["wash"] },
  { label: "Dry Media", tools: ["HB pencil", "Soft charcoal", "Soft pastel", "Wax crayon"], tips: ["round", "chisel"], media: ["graphite", "charcoal", "crayon"] },
  { label: "Oils", tools: ["Loaded oils"], tips: ["bristles", "round"], media: ["cover"] },
  { label: "Odd Ones", tools: ["Spirograph", "Fill in", "Filled shape", "Dot tone", "Ben-Day brush", "Lemon juice", "Vanishing ink"], tips: ["round"], media: ["ink", "wash"] }
];
const CATEGORY_PRIOR = 3, CHOSEN_BUDGET = .6;
const categoryOf = label => CATEGORIES.find(c => c.label === label) || null;

// ---------- the candidates: choices from the analysis, dials started where the measurements point ----------
// opts.tip and opts.category: the person's choices (see CATEGORIES); left out, the analysis chooses alone.
function candidates(job, opts = {}) {
  const F = job.F, out = [], cat = categoryOf(opts.category), tipOK = t => !opts.tip || t === opts.tip;
  const lines = F.runsMode >= 2 && F.runsFrac > .45;
  const soft = F.ramp > .28 || F.halo > .12, grey = F.Lb < .62, wide = job.Wb > 30, inkLike = !!(job.paper && job.paper.inkLike);
  // Tips, each with a prior (higher: tried first).
  const tips = [];
  if (lines) { if (F.runsMode === 2) tips.push([3, "split"], [2.5, "multi", { count: 2 }]); else tips.push([3, "multi", { count: Math.min(5, F.runsMode) }]); }
  if (job.nib.shaped) { if (job.nib.engA < .33) tips.push([2.8, "flat"]); if (job.nib.engA > .25) tips.push([2.6, "chisel"]); }
  // A square end: a flat head (Hake follows the stroke, so its ends are cut across it), or a chisel.
  const square = !lines && job.Wb >= 6 && Math.max(job.ends[0].square, job.ends[1].square) > .65;
  // Hake has no taper: with one end square and the other pointed, a flat nib turned across the stroke does it.
  const mixed = square && Math.min(job.ends[0].square, job.ends[1].square) < .3;
  if (square) tips.push([mixed ? 2.6 : 3.4, "hake"], [mixed ? 3.4 : 2.8, "flat", { squareCut: true }], [2.4, "chisel", { squareCut: true }]);
  tips.push([lines ? 1 : square ? 2 : 2.7, "round"]);
  if (F.streak > .05 || (F.dry > .08 && F.runsMean > 1.3) || (lines && F.runsFrac < .7)) tips.push([2.2, "bristles"]);
  if (!square && wide && (F.streak > .04 || F.dry > .05) && F.l80In < job.Wb) tips.push([1.5, "hake"]);
  // The category's tips and the tip chosen, where the analysis didn't offer them.
  for (const t of [...(cat ? cat.tips : []), opts.tip].filter(Boolean)) if (!tips.some(x => x[1] === t)) tips.push([2, t, t === "multi" ? { count: clamp(F.runsMode, 2, 5) } : undefined]);
  // Media, each with a prior.
  const media = [];
  const rough = roughExcess(F) > .1 || F.spec[0] > .08;
  // A wide, pale mark is a wash: grey ink would draw like it here but not on the page.
  if (!(F.Lb < .3 && job.Wb > 20)) media.push([grey && (soft || wide) ? 1 : grey ? 1.8 : rough ? 2.2 : 3, "ink"]);
  // Wet ink's wash round the line only where the photo has a halo darker than bare paper (paperModel's control).
  const halo = job.paper ? job.paper.haloReal : F.halo > .06;
  // Crisp ink (paperModel's best guess) gets no wash and no wet ink: their soft greys would be pressed out of the
  // score's black and white but show on the page.
  if (halo && !inkLike) media.push([grey ? 1.5 : 3.2, "ink", { wetInk: true }]);
  if (!inkLike) media.push([grey || soft || wide ? 2.8 : 1, "wash"]);
  const paleWide = F.Lb < .3 && job.Wb > 20;
  // Crisp ink first: charcoal only a fallback there, pencil not at all (the curve has pressed out its greys).
  if (!lines && !paleWide) { media.push([inkLike ? .9 : (roughExcess(F) > .05 || F.spec[0] > .06 || F.satN > .3) && F.Lb > .35 ? 2.4 : .8, "charcoal"]);
    // Wax crayon (the lab's new modules): a rough, toothy mark that isn't crisp ink.
    if (!inkLike) media.push([rough ? 2 : .6, "crayon"]);
    if (job.Wb < 30 && !inkLike) media.push([grey ? 2.4 : .9, "graphite"]); }
  if (cat) for (const m of cat.media) if (!media.some(x => x[1] === m && !x[2])) media.push([2, m]);
  for (const [pt, tip, te] of tips) for (const [pm, medium, me] of media) {
    if (!tipOK(tip)) continue;
    if (tip === "hake" && medium !== "wash" && medium !== "ink") continue;
    if ((tip === "split" || tip === "multi") && medium !== "ink") continue;
    if (medium === "crayon" && tip !== "round" && tip !== "chisel") continue;
    // Charcoal and pencil always sit on some paper's tooth: cold press to start with (the paper step tries the rest).
    const inCat = cat && cat.tips.includes(tip) && cat.media.includes(medium);
    const st = Object.assign({ tip, medium, life: "wet", paper: TOOTHY[medium] ? "cold press" : "plain", prior: pt + pm + (inCat ? CATEGORY_PRIOR : 0) }, te, me);
    out.push(st);
    if (medium === "wash" || st.wetInk) out.push(Object.assign({}, st, { life: "dry", prior: st.prior - 1.2 }));
  }
  // A tip chosen that draws with none of the media (a split nib in charcoal): in ink.
  if (!out.length) out.push({ tip: opts.tip || "round", medium: "ink", life: "wet", paper: "plain", prior: 3 });
  out.sort((a, b) => b.prior - a.prior);
  return out.slice(0, opts.tip || cat ? 6 : 10);
}
// The dials that make a structure what it is: searched already in the coarse look.
function keyDials(st) {
  const k = [];
  if (st.wetInk) k.push("medium.wet");
  if (st.medium === "wash") k.push("medium.wet", "tip.soft");
  if (st.medium === "charcoal") k.push("medium.spread", "medium.hold");
  if (st.medium === "graphite") k.push("medium.grade", "paper.tooth");
  if (st.medium === "crayon") k.push("medium.wax", "paper.tooth");
  if (st.tip === "split") k.push("tip.gap");
  if (st.tip === "multi") k.push("tip.reach");
  if (st.tip === "bristles") k.push("tip.splay", "tip.dryness");
  if (st.tip === "hake") k.push("tip.dry");
  if ((st.tip === "flat" || st.tip === "chisel") && st.squareCut) k.push("tip.angle");
  return k.slice(0, 3);
}
// The dials a structure frees for the search, each with its starting value.
function freeDials(job, st) {
  const F = job.F, x = {}, k = Math.sqrt(Math.max(1, (job.Wb || 8) / 16));
  x.sizeK = 0; x.shade = clamp(st.medium === "wash" ? F.Lb / .45 : F.Lb, .12, 1);
  // Pressure → size wide enough for the photo's widths: the least that covers its range (thin stretch to widest)
  // at a usual pressure of 0.6, under the brush's own width law.
  if (st.tip !== "hake" && job.widthRange) x["hand.ps"] = pressureFor(job.widthRange, LINE_MEDIA[st.medium] && !st.wetInk);
  const tip = st.tip, taperIn = clamp((job.zoneIn ?? F.l80In) / (.665 * 70 * k)), taperOut = clamp((job.zoneOut ?? F.l80Out) / (.665 * 90 * k));
  // A square end has no taper (the width runs right to the edge).
  if (tip !== "hake" && tip !== "bristles") { x["tip.taperIn"] = taperIn < .06 || squareEnd(job, true) ? 0 : taperIn; x["tip.taperOut"] = taperOut < .06 || squareEnd(job, false) ? 0 : taperOut; }
  // Dry brush from the gaps in the body (between parallel lines the gaps are the paper, not dryness).
  if (tip === "round" || tip === "chisel" || tip === "flat" || tip === "multi" || tip === "split") x["tip.dry"] = F.runsMode >= 2 ? 0 : clamp(F.dry * 2.2);
  if (tip === "round" || tip === "chisel" || tip === "flat" || tip === "multi") x["tip.wobble"] = clamp((F.wob - STEADY.wobFloor) * 6);
  // A square end cut by a flat nib: the nib held along the cut (the engine's angle runs 20–70°, the nearest of it).
  const sqE = job.ends ? (job.ends[1].square >= job.ends[0].square ? job.ends[1] : job.ends[0]) : null;
  if ((tip === "chisel" || tip === "flat") && !job.nib.shaped && sqE && sqE.square > .65) {
    let phi = (Math.atan2(sqE.dir[1], sqE.dir[0]) * 180 / Math.PI + 90 + sqE.slant) % 180; if (phi < 0) phi += 180;
    const dist = v => v < 20 ? 20 - v : v > 70 ? v - 70 : 0, opts = [phi, phi - 180, phi + 180].sort((a, b) => dist(a) - dist(b));
    x["tip.angle"] = clamp(opts[0], 20, 70); x["tip.aspect"] = rangeOf("tip", tip, "aspect")[0];
  }
  if ((tip === "chisel" || tip === "flat") && job.nib.shaped) { x["tip.aspect"] = clamp(job.nib.engA, ...rangeOf("tip", tip, "aspect")); x["tip.angle"] = job.nib.angle; }
  if (tip === "split") { x["tip.gap"] = .55; x["tip.aspect"] = .42; x["tip.angle"] = job.nib.shaped ? job.nib.angle : 40; }
  if (tip === "multi") x["tip.reach"] = .6;
  if (tip === "bristles") { x["tip.splay"] = .45; x["tip.cohesion"] = .55; x["tip.dryness"] = clamp(.1 + F.dry * 2); }
  if (tip === "hake") { x["tip.dry"] = clamp(F.streak * 8 + F.dry * 2); x["tip.soft"] = .2; x["tip.angle"] = 75; }
  const mk = st.medium;
  if (mk === "ink") {
    x["medium.pool"] = squareEnd(job, true) ? 0 : clamp((F.poolS - 1.08) / .9);
    if (F.satN > .3) x["medium.splatter"] = clamp(F.satN / 3);
    if (st.wetInk) { x["medium.wet"] = clamp(.2 + F.halo * 2, .1, .8); x["medium.line"] = .8; x["medium.bleed"] = clamp(F.halo * 4); x["tip.soft"] = .5; }
  }
  if (mk === "wash") { x["medium.wet"] = F.ramp > .2 ? .85 : .6; x["medium.pool"] = clamp(F.hollow * 2); x["medium.bleed"] = clamp(F.halo * 3); x["medium.bloom"] = clamp(F.bloom * 6); x["tip.soft"] = clamp(F.ramp * 1.5); }
  if (mk === "charcoal") { x["medium.spread"] = .3; x["medium.hold"] = clamp(F.Lb); }
  // Graphite's darkest is (70 + 145 grade) / 255; the paper's tooth lets about 60 % of it show.
  if (mk === "graphite") { x["medium.grade"] = clamp((F.Lb / .6 * 255 - 70) / 145, .1, 1); x["medium.spread"] = .12; }
  if (mk === "crayon") x["medium.wax"] = clamp(1 - F.dry * 3, .2, 1);
  if (st.paper !== "plain") x["paper.tooth"] = mk === "graphite" ? .4 : .5;
  if (st.life === "wet" && x["tip.taperOut"] > 0) x["life.window"] = clamp(F.l80Out / 12 + 2, 1, 10);
  // Only dials the chosen choices have.
  const kOf = { tip: st.tip, medium: st.medium, life: st.life, paper: st.paper || "plain" };
  for (const key of Object.keys(x)) { const [p, d] = key.split("."); if (kOf[p] && !CHOICES[p].find(c => c.k === kOf[p]).dials.includes(d)) delete x[key]; }
  return x;
}
function pressureFor([rmin, rmax], line) {
  const f = (P, p) => line ? lerp(1 - .75 * P, 1 + .6 * P, p) : (lerp(.6, .06, P) + (1 - lerp(.6, .06, P)) * Math.pow(p, 1.2));
  for (let P = 0; P <= 1.0001; P += .05) if (f(P, 0) / f(P, FIT.pMid) <= rmin * 1.02 && f(P, 1) / f(P, FIT.pMid) >= rmax * .98) return P;
  return 1;
}
// Packs a dial map into the unit cube and back.
function packer(st, x0) {
  const keys = Object.keys(x0), span = keys.map(key => {
    if (key === "sizeK") return [-.7, .7]; if (key === "shade") return [.12, 1]; if (key === "hand.ps") return [0, 1];
    const [p, d] = key.split("."); return rangeOf(p, p === "tip" ? st.tip : p === "medium" ? st.medium : p === "life" ? st.life : st.paper || "plain", d);
  });
  return {
    keys,
    toU: x => keys.map((k, i) => clamp((x[k] - span[i][0]) / (span[i][1] - span[i][0]))),
    fromU: u => Object.fromEntries(keys.map((k, i) => [k, lerp(span[i][0], span[i][1], clamp(u[i]))]))
  };
}

// ---------- the search ----------
// A generator: each yield is a progress report; the caller (the worker, or the page in slices) runs it to the end.
function* fitStroke(job, opts = {}) {
  const seed = opts.seed ?? 0x5eed;
  const budget = (opts.budgetMs ?? FIT.budgetMs) * (opts.tip || opts.category ? CHOSEN_BUDGET : 1), t0 = Date.now(), elapsed = () => Date.now() - t0;
  let evals = 0, best = null;
  const byStruct = new Map();
  const evalX = (st, x) => {
    evals++;
    const B = brushOf(st, x, seed), size = clamp(baseSize(job, B) * Math.exp(x.sizeK || 0), 1, 360);
    // What the measurements expected counts a little: a unit per step of prior below the likeliest.
    if (job.paper && job.paper.inkLike) x.shade = 1;
    const l = scoreFit(job, renderFit(job, B, size, x.shade)) + simplicity(st, x) + Math.max(0, topPrior - (st.prior ?? topPrior));
    if (!best || l < best.l) best = { l, st, x: Object.assign({}, x), B, size, shade: x.shade };
    const key = structWords(st), b = byStruct.get(key);
    if (!b || l < b.l) byStruct.set(key, { l, st, x: Object.assign({}, x) });
    return l;
  };
  const cands = candidates(job, opts).filter(opts.filter || (() => true)), topPrior = Math.max(...cands.map(c => c.prior));
  { const st = cands[0], x = freeDials(job, st), B = brushOf(st, x, seed); cameraBlur(job, B, baseSize(job, B), x.shade); }
  // 1. Coarse: every structure, a short search over size, shade and the one or two dials that make it what it is.
  const tried = [];
  for (const st of cands) {
    const x0 = freeDials(job, st), sub = { sizeK: x0.sizeK, shade: x0.shade };
    for (const k of keyDials(st)) if (k in x0) sub[k] = x0[k];
    const n = Object.keys(sub).length, pk = packer(st, sub), f = u => evalX(st, Object.assign({}, x0, pk.fromU(u)));
    const g = nelderMead(f, pk.toU(sub), new Array(n).fill(.15), 3 + 2 * n); let r;
    while (!(r = g.next()).done) yield { phase: "Trying " + structWords(st), evals, loss: best.l, best };
    tried.push({ st, f: r.value.f, x: Object.assign({}, x0, pk.fromU(r.value.x)) });
    if (elapsed() > budget * .3 && tried.length >= 2) break;
  }
  tried.sort((a, b) => a.f - b.f);
  // 2. Closer: the best four (within twice the best), every free dial, a short search each.
  const closer = tried.filter((t, i) => i < 4 && t.f < tried[0].f * 2);
  for (const t of closer) {
    const pk = packer(t.st, t.x), n = pk.keys.length, f = u => evalX(t.st, pk.fromU(u)), tEnd = Math.max(elapsed() + 250, budget * .55);
    const g = nelderMead(f, pk.toU(t.x), new Array(n).fill(.12), 10 + 3 * n); let r;
    while (!(r = g.next()).done) { yield { phase: "Closer: " + structWords(t.st), evals, loss: best.l, best }; if (elapsed() > tEnd) break; }
  }
  // 3. Fine: the best two now (one if the second is far behind), every free dial, the rest of the time.
  const ranked = closer.map(t => byStruct.get(structWords(t.st))).sort((a, b) => a.l - b.l);
  const finalists = ranked.filter((t, i) => i === 0 || (i === 1 && t.l < ranked[0].l * 1.35));
  const share = (budget * .85 - elapsed()) / finalists.length;
  for (const t of finalists) {
    const pk = packer(t.st, t.x), n = pk.keys.length, f = u => evalX(t.st, pk.fromU(u));
    const tEnd = elapsed() + Math.max(300, share);
    const g = nelderMead(f, pk.toU(t.x), new Array(n).fill(.08), 40 + 14 * n); let r;
    while (!(r = g.next()).done) { yield { phase: "Refining " + structWords(t.st), evals, loss: best.l, best }; if (elapsed() > tEnd) break; }
  }
  // 4. Paper: where grain shows, each scan with its tooth searched.
  const F = job.F, grainy = F.spec[0] > .08 || F.dry > .04 || TOOTHY[best.st.medium];
  if (grainy && elapsed() < budget) {
    const base = best;
    for (const paper of ["cold press", "felt", "laid", "canvas", "crumpled"]) {
      const st = Object.assign({}, base.st, { paper }), x = Object.assign({}, base.x, { "paper.tooth": .5 });
      for (const tooth of [.3, .7]) { x["paper.tooth"] = tooth; evalX(st, x); yield { phase: "Paper: " + paper, evals, loss: best.l, best }; }
      if (elapsed() > budget * 1.1) break;
    }
    if (best.st.paper !== "plain") {
      const pk = packer(best.st, best.x), keys = ["paper.tooth", "sizeK", "shade"].filter(k => k in best.x);
      const sub = Object.fromEntries(keys.map(k => [k, best.x[k]])), pk2 = packer(best.st, sub), st = best.st, xb = best.x;
      const g = nelderMead(u => evalX(st, Object.assign({}, xb, pk2.fromU(u))), pk2.toU(sub), keys.map(() => .1), 14); let r;
      while (!(r = g.next()).done) yield { phase: "Paper tooth", evals, loss: best.l, best };
      void pk;
    }
  }
  // A near tie (within 8 %) goes to what the measurements expected.
  const near = [...byStruct.values()].filter(t => t.l <= best.l * 1.08).sort((a, b) => (b.st.prior ?? 0) - (a.st.prior ?? 0))[0];
  if (near && (near.st.prior ?? 0) > (best.st.prior ?? 0)) {
    const B = brushOf(near.st, near.x, seed);
    best = { l: near.l, st: near.st, x: near.x, B, size: clamp(baseSize(job, B) * Math.exp(near.x.sizeK || 0), 1, 360), shade: near.x.shade };
  }
  const res = finish(job, best, evals, elapsed());
  res.tried = [...byStruct.values()].sort((a, b) => a.l - b.l).map(t => ({ words: structWords(t.st), loss: t.l }));
  res.drawn = yield* nearestDrawn(job, res.shade, opts);
  return res;
}
// ---------- the app's drawn tools (DrawLayout.PICKER's Basic tools, DRAWN_TOOLS from the build) against the photo ----------
// Each as it is, only its size free (at the fit's grey: the app draws every brush in the one grey): three sizes each,
// then the closest five to a finer size. Lowest score first; tools whose codes this engine can't read are left out.
// A category chosen keeps to its tools, a tip to the tools with that tip (all of them if none is left).
function* nearestDrawn(job, shade, opts = {}) {
  let tools = [];
  for (const [name, code] of typeof DRAWN_TOOLS === "undefined" ? [] : DRAWN_TOOLS) {
    const r = readAnyCode(code); if (!r || !r.b) continue;
    tools.push({ name, B: r.b, base: baseSize(job, r.b), l: Infinity, k: 0 });
  }
  const cat = categoryOf(opts.category), kept = tools.filter(t => (!cat || cat.tools.includes(t.name)) && (!opts.tip || t.B.tip.k === opts.tip));
  if (kept.length) tools = kept;
  const tryK = (t, k) => { const l = scoreFit(job, renderFit(job, t.B, clamp(t.base * Math.exp(k), 1, 360), shade)); if (l < t.l) { t.l = l; t.k = k; } };
  for (const t of tools) { for (const k of [-.35, 0, .35]) tryK(t, k); yield { phase: "Drawn tools: " + t.name, loss: t.l }; }
  tools.sort((a, b) => a.l - b.l);
  for (const t of tools.slice(0, 5)) { const k0 = t.k; for (const k of [k0 - .18, k0 + .18, k0 - .09, k0 + .09]) tryK(t, k); yield { phase: "Drawn tools: " + t.name, loss: t.l }; }
  return tools.sort((a, b) => a.l - b.l).map(t => ({ name: t.name, loss: t.l }));
}
// A small cost for every effect that's on, so a dial the photo can't show stays off rather than wandering.
const SIMPLE = { "tip.dry": .8, "tip.wobble": 1.2, "medium.pool": .5, "medium.splatter": .8, "medium.bleed": .6, "medium.bloom": .6, "medium.backrun": .8,
  "medium.offset": 1, "medium.chaos": 1, "paper.tooth": .6, "tip.taperIn": .2, "tip.taperOut": .2, "hand.ps": .05 };
function simplicity(st, x) {
  let s = (st.paper && st.paper !== "plain" && !TOOTHY[st.medium] ? .6 : 0) + (st.life === "dry" ? .4 : 0) + (st.wetInk ? .6 : 0);
  for (const k in SIMPLE) if (x[k] > 0) s += SIMPLE[k] * x[k];
  // A pale ink line or a pale charcoal mark is more likely a wash or a pencil.
  if (st.medium === "ink" && x.shade < .55) s += (.55 - x.shade) * 10;
  if (st.medium === "charcoal" && x.shade < .5) s += (.5 - x.shade) * 10;
  return s;
}
// Faint dials off, a style that fits, a name; the size and shade to draw it with; the scores against the photo.
function finish(job, best, evals, ms) {
  const B = cloneBrush(best.B);
  for (const p of ["tip", "medium"]) for (const [k, v] of Object.entries(B[p].d)) { const s = DIALS[p][k]; if (s && !s.choices && s.min === 0 && v < .04 && !(p === "medium" && (k === "line" || k === "wet"))) B[p].d[k] = 0; }
  B.style = styleFor(B);
  normBrush(B);
  // Named for what stands out in it ("rake-fig"; parts.js describedName), else adjective-produce.
  B.name = describedName(B, freshName());
  const img = renderFit(job, B, best.size, best.shade), sc = scoreFit(job, img, true);
  return { B, code: encodeM(B), size: Math.round(best.size * 10) / 10, shade: best.shade, struct: best.st, x: best.x, loss: sc.total, score: sc, img, evals, ms };
}
function styleFor(B) {
  const t = B.tip.k, m = B.medium.k;
  if (m === "charcoal") return "Charcoal";
  if (m === "graphite") return "Pencil";
  if (m === "crayon") return "Pastels";
  if (m === "cover") return t === "bristles" ? "Oils" : "Gouache";
  if (t === "hake") return "Hake";
  if (t === "bristles") return "Dry brush";
  if (m === "wash") return "Watercolor";
  if (m === "ink" && B.medium.d.wet > .4) return "Ink wash";
  if (t === "split" || t === "flat") return "Dip pen";
  if (t === "chisel") return "Marker";
  if (t === "multi") return "Anything goes";
  return B.tip.d.taperIn + B.tip.d.taperOut > .4 ? "Brush pen" : "Fineliner";
}
const TIP_WORDS = { round: "round", chisel: "chisel", flat: "flat nib", hake: "hake", split: "split nib", bristles: "bristle brush" };
const MEDIUM_WORDS = { ink: "ink", wash: "wash", cover: "gouache", charcoal: "charcoal", graphite: "graphite", crayon: "crayon" };
const structWords = st => [st.tip === "multi" ? (st.count || 3) + "-line rake" : TIP_WORDS[st.tip], st.wetInk ? "wet ink" : MEDIUM_WORDS[st.medium],
  st.life === "dry" ? "slow drying" : "", st.paper && st.paper !== "plain" ? st.paper : ""].filter(Boolean).join(", ");
// How blurred the edges are, as a Gaussian's sigma in px: from the steepest slope across each edge, row by row.
function edgeSigma(job, img) {
  const F = features(job, img), S = sampleStrip(job.ts, img, job.w), C = job.ts.cols, T = job.ts.T, [b0, b1] = job.body, wd = F.widths, slopes = [];
  for (let i = b0; i <= b1; i++) {
    if (wd[i] < Math.max(2.5, .8 * F.Wmed)) continue;
    for (const [e, sgn] of [[F.wl[i], -1], [F.wr[i], 1]]) {
      let best = 0;
      for (let t = Math.floor(Math.abs(e) - 3); t <= Math.ceil(Math.abs(e) + 3); t++) {
        const j = T + sgn * t;
        if (j < 1 || j >= C - 1) continue;
        best = Math.max(best, Math.abs(S[i * C + j + 1] - S[i * C + j - 1]) / 2);
      }
      if (best > 0) slopes.push(best);
    }
  }
  const slope = median(slopes);
  return slope > 0 ? F.Lb / (slope * Math.sqrt(2 * Math.PI)) : 0;
}
// Runs a fit to the end at once (the checks, and Node).
function fitNow(job, opts) { const g = fitStroke(job, opts); let r; while (!(r = g.next()).done); return r.value; }
// A brush and size from a finished fit, to draw with elsewhere.
function brushFromFit(r) { return { B: r.B, size: r.size, shade: r.shade }; }
