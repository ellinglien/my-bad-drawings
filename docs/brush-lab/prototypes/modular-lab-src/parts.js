// ===================================================================================================================
// The part model. A brush is six parts, each holding one choice and that choice's dials:
//   Tip (the contact's shape) → Medium (what it lays) → Carry (what it picks up) → Paper → Life (when it is final) → Hand
// Every part always holds something; "Off" and "None" are real choices, so any combination draws.
// ===================================================================================================================
const PARTS = ["tip", "medium", "carry", "paper", "life", "hand"];
// The part descriptions, one set for the lab and Brush Studio (its card for Erasable, the medium's last dial, says ERASE_ROLE's).
const ROLE = {
  tip: { label: "Tip", blurb: "The shape that touches the paper." },
  medium: { label: "Medium", blurb: "What the mark is made of." },
  carry: { label: "Smudge", blurb: "How the brush moves paint already there." },
  paper: { label: "Paper", blurb: "The surface and its texture." },
  life: { label: "Drying", blurb: "How long the mark stays wet." },
  hand: { label: "Control", blurb: "How pressure, tilt and speed change the mark." }
};
const ERASE_ROLE = { label: "Erasable", blurb: "How much the eraser can take." };
const GRADES = ["6H", "5H", "4H", "3H", "2H", "H", "F", "HB", "B", "2B", "3B", "4B", "5B", "6B", "7B", "8B"];
const gradeOf = g => GRADES[Math.round(g < .4 ? g / .4 * 7 : 7 + (g - .4) / .6 * 8)];
const pct = v => Math.round(v * 100) + "";
// Every dial: range, default, and how it's shown. A choice dial holds an option index.
const DIALS = {
  tip: {
    weight: { label: "Size", min: .5, max: 1.5, def: 1, fmt: v => "×" + v.toFixed(2) },
    aspect: { label: "Nib Width", min: .14, max: .55, def: .4 },
    angle: { label: "Nib Angle", min: 20, max: 70, def: 40, fmt: v => Math.round(v) + "°" },
    taperIn: { label: "Taper In", min: 0, max: 1, def: 0 },
    taperOut: { label: "Taper Out", min: 0, max: 1, def: 0 },
    dry: { label: "Dry Brush", min: 0, max: 1, def: 0 },
    wobble: { label: "Wobble", min: 0, max: 1, def: 0 },
    soft: { label: "Soft Edge", min: 0, max: 1, def: .3 },
    headTurn: { label: "Head Follows", choices: ["Stroke", "Page"], def: 0 },
    gap: { label: "Tine Gap", min: 0, max: 1, def: .55 },
    count: { label: "Lines", min: 2, max: 5, def: 3, int: true, fmt: v => String(Math.round(v)) },
    reach: { label: "Spacing", min: 0, max: 1, def: .6 },
    hairs: { label: "Hairs", min: 6, max: 32, def: 26, int: true, fmt: v => String(Math.round(v)) },
    cohesion: { label: "Clump", min: 0, max: 1, def: .55 },
    splay: { label: "Splay", min: 0, max: 1, def: .45 },
    dryness: { label: "Dry Brush", min: 0, max: 1, def: .15 }
  },
  medium: {
    wet: { label: "Wet", min: 0, max: 1, def: 0 },
    line: { label: "Pen Line", min: 0, max: 1, def: 1 },
    pool: { label: "Pooling", min: 0, max: 1, def: 0 },
    splatter: { label: "Spatter", min: 0, max: 1, def: 0 },
    bleed: { label: "Bleed", min: 0, max: 1, def: 0 },
    bloom: { label: "Bloom", min: 0, max: 1, def: 0 },
    backrun: { label: "Backruns", min: 0, max: 1, def: 0 },
    lines: { label: "Line Style", choices: ["Centre", "Outline", "Squiggle"], def: 0 },
    offset: { label: "Line Shift", min: 0, max: 1, def: 0 },
    chaos: { label: "Breaks", min: 0, max: 1, def: 0 },
    cover: { label: "Opacity", min: 0, max: 1, def: 1 },
    impasto: { label: "Thick Paint", min: 0, max: 1, def: 0 },
    spread: { label: "Messy Edge", min: 0, max: 1, def: .3 },
    hold: { label: "Density", min: 0, max: 1, def: .4 },
    grade: { label: "Grade", min: 0, max: 1, def: .4, fmt: gradeOf },
    // Not the prototype's: the app's charcoal dust (its gravity), kept so codes line up with the app's; the lab draws none.
    dust: { label: "Dust When Tilted", min: 0, max: 1, def: 0, hide: true },
    // How much of a mark the eraser takes (spec 2026-10-16-erasable-design.md). A code without it reads 1, today's: all.
    erase: { label: "Erasable", min: 0, max: 1, def: 1 }
  },
  carry: {
    pick: { label: "Pick Up Paint", min: 0, max: 1, def: .4 },
    lay: { label: "Lay Down Paint", min: 0, max: 1, def: .5 },
    load: { label: "Own Paint", min: 0, max: 1, def: 1 },
    // Smear's Soften (the Stump spec, 2026-10-18-stump-design.md): how much of the smear is MonoPaint's blending stump.
    soften: { label: "Soften", min: 0, max: 1, def: 0 },
    // Drag's and Smear's Keeps Paint (the wet smudge spec, 2026-10-19-wet-smudge-design.md): how much of what the brush
    // picked up it keeps for the next stroke, a dirty brush. Off: every stroke starts clean, as before. Never rolled.
    keep: { label: "Keeps Paint", min: 0, max: 1, def: 0 },
    // Drag's and Smear's Paint Load and Starting Load (the oil paint spec, 2026-10-19-oil-paint-design.md; MonoPaint's oil
    // paint mode): how much paint a full brush holds (off: it never runs out, as before), and the share of it every
    // stroke starts with. Written only when on; a part's roll leaves them off (Oils rolls Paint Load from the seed).
    pload: { label: "Paint Load", min: 0, max: 1, def: 0 },
    pmin: { label: "Starting Load", min: 0, max: 1, def: .25 }
  },
  paper: { tooth: { label: "Texture", min: 0, max: 1, def: .5 } },
  life: {
    window: { label: "Wet Edge", min: 1, max: 10, def: 6, fmt: v => v.toFixed(1) + " mm" },
    drying: { label: "Drying Time", min: 0, max: 1, def: .35, fmt: v => (wetMsOf(v) / 1000).toFixed(1) + " s" },
    settle: { label: "Flow While Wet", min: 0, max: 1, def: .35 },
    takes: { label: "Takes Ink", choices: ["Off", "Drift", "Vein"], def: 0 },
    plume: { label: "Plume", min: 0, max: 1, def: .55 },
    spread: { label: "Ring Size", min: 0, max: 1, def: .5 },
    drift: { label: "Drift Downhill", min: 0, max: 1, def: .5 }
  },
  hand: {
    ps: { label: "Pressure Size", min: 0, max: 1, def: 0 }, pd: { label: "Pressure Dark", min: 0, max: 1, def: 0 }, pw: { label: "Pressure Nib", min: 0, max: 1, def: 0 },
    ts: { label: "Tilt Size", min: 0, max: 1, def: 0 }, td: { label: "Tilt Dark", min: 0, max: 1, def: 0 }, tw: { label: "Tilt Nib", min: 0, max: 1, def: 0 },
    ss: { label: "Speed Size", min: 0, max: 1, def: 0 }, sd: { label: "Speed Dark", min: 0, max: 1, def: 0 }, sw: { label: "Speed Nib", min: 0, max: 1, def: 0 },
    dir: { label: "Fast Strokes", choices: ["Thinner", "Thicker"], def: 0 },
    gain: { label: "Response", min: 0, max: 1.5, def: 1, fmt: v => "×" + v.toFixed(2) }
  }
};
const HAND_CELLS = ["ps", "pd", "pw", "ts", "td", "tw", "ss", "sd", "sw"];
const HAND_DIALS = [...HAND_CELLS, "gain", "dir"];
// Never reorder a part's choices or a choice's dials: share codes are written in this order. Append only.
const CHOICES = {
  tip: [
    { k: "round", label: "Round", dials: ["weight", "taperIn", "taperOut", "dry", "wobble", "soft"], macro: "weight", words: ["fine", "broad"], blurb: "A round point or a round brush." },
    { k: "chisel", label: "Chisel", dials: ["aspect", "angle", "weight", "taperIn", "taperOut", "dry", "wobble", "soft"], macro: "aspect", words: ["fine", "broad"], range: { aspect: [.3, .55] }, def: { aspect: .42 }, blurb: "A broad edge held at an angle: thick and thin." },
    { k: "flat", label: "Flat Nib", dials: ["aspect", "angle", "weight", "taperIn", "taperOut", "dry", "wobble", "soft"], macro: "aspect", words: ["fine", "broad"], range: { aspect: [.14, .28] }, def: { aspect: .2 }, blurb: "A thin italic edge: hairlines and full strokes." },
    { k: "hake", label: "Hake", dials: ["weight", "headTurn", "angle", "dry", "soft"], macro: "weight", words: ["fine", "broad"], def: { angle: 75, dry: .35, soft: .2 }, blurb: "A wide flat head swept as a ribbon; it pivots on tight turns." },
    { k: "split", label: "Split Nib", dials: ["gap", "aspect", "angle", "weight", "taperIn", "taperOut", "dry"], macro: "gap", words: ["closed", "open"], range: { aspect: [.3, .55] }, def: { aspect: .42 }, blurb: "Two tines that part under pressure." },
    { k: "multi", label: "Rake", dials: ["count", "reach", "weight", "taperIn", "taperOut", "dry", "wobble"], macro: "reach", words: ["close", "wide"], blurb: "Two to five lines that breathe apart and together with pressure." },
    { k: "bristles", label: "Bristle", dials: ["hairs", "splay", "cohesion", "dryness", "weight"], macro: "splay", words: ["tight", "splayed"], blurb: "Hairs that follow each other's trails, split and rejoin." }
  ],
  medium: [
    { k: "ink", label: "Ink", dials: ["wet", "line", "pool", "splatter", "bleed", "bloom", "lines", "offset", "chaos", "erase"], macro: "wet", words: ["dry", "wet"], blurb: "A line; Wet lets it loosen into a wash around it." },
    { k: "wash", label: "Wash", dials: ["wet", "pool", "bleed", "bloom", "backrun", "line", "erase"], macro: "wet", words: ["flat", "wet"], def: { wet: .85, line: 0, pool: .3, bloom: .2 }, labels: { pool: "Dark Edge" }, blurb: "Transparent paint: only darkens what's under it." },
    { k: "cover", label: "Opaque", dials: ["cover", "impasto", "erase"], macro: "cover", words: ["thin", "opaque"], blurb: "Opaque body colour: light covers dark. Thick paint is lit from the upper left." },
    { k: "charcoal", label: "Charcoal", dials: ["spread", "hold", "dust", "erase"], macro: "spread", words: ["crisp", "messy"], labels: { hold: "Density" }, blurb: "Dry dust: catches the high points first; fuzz and specks from the stick, not the paper." },
    { k: "graphite", label: "Graphite", dials: ["grade", "spread", "erase"], macro: "grade", words: ["hard", "soft"], def: { spread: 0 }, blurb: "Pencil, 6H to 8B: fills the texture with each pass." }
  ],
  carry: [
    { k: "off", label: "Off", dials: [], blurb: "Lays its own paint only." },
    { k: "lift", label: "Lift", dials: ["pick", "lay"], macro: "pick", words: ["gentle", "strong"], def: { pick: .2, lay: .3 }, blurb: "Takes paint off as it goes, like a damp brush or a tissue." },
    { k: "drag", label: "Drag", dials: ["pick", "lay", "load", "keep", "pload", "pmin"], macro: "pick", words: ["clean", "smeary"], def: { pick: .6, lay: .7, load: .85 }, blurb: "Drags the paint beneath along: wet into wet." },
    { k: "smudge", label: "Smear", dials: ["pick", "lay", "load", "soften", "keep", "pload", "pmin"], macro: "pick", words: ["clean", "smeary"], def: { pick: .7, lay: .55, load: .3 }, blurb: "Mostly smears what's there, like a finger or a stump." },
    // Clear Water (the wet smudge spec): Smudge's fifth choice, so older codes never hold it. Lays only water.
    { k: "water", label: "Clear Water", dials: ["pick", "lay"], macro: "pick", words: ["gentle", "strong"], def: { pick: .5, lay: .6 }, blurb: "Lays only water: lifts what it touches and lets it run through the wet." }
  ],
  paper: ["plain", "cold press", "laid", "felt", "crumpled", "canvas"].map(k => ({ k, label: { plain: "Smooth", "cold press": "Cold Press", laid: "Ribbed" }[k] || k[0].toUpperCase() + k.slice(1), dials: ["tooth"], macro: "tooth", words: ["smooth", "rough"], def: { tooth: k === "plain" ? 0 : .5 } })),
  life: [
    { k: "none", label: "Instant", dials: [], blurb: "Final the moment the nib passes. Fast." },
    { k: "wet", label: "Quick", dials: ["window"], macro: "window", words: ["short", "long"], blurb: "Only the last few mm behind the nib change while the pen is down; nothing after lift." },
    { k: "dry", label: "Slow", dials: ["drying", "settle", "takes", "plume"], macro: "drying", words: ["short", "long"], blurb: "Keeps changing for its drying time after lift." },
    { k: "float", label: "Float", dials: ["drying", "spread", "drift"], macro: "spread", words: ["small", "large"], def: { drying: .6 }, labels: { drying: "Floating Time" },
      blurb: "Suminagashi: the paint floats on water. A tap drops it in rings that push older paint outward; with Smudge it combs what floats. It prints onto the paper when its time is up." }
  ],
  hand: [
    { k: "steady", label: "Steady", cells: {} },
    { k: "wider", label: "Press Wide", cells: { ps: .8 } },
    { k: "darker", label: "Press Dark", cells: { pd: .8, ps: .15 } },
    { k: "both", label: "Press Both", cells: { ps: .7, pd: .6 } },
    { k: "brush", label: "Springy", cells: { ps: .9, pd: .3, pw: .45, ss: .4 } },
    { k: "side", label: "Tilt Shade", cells: { ps: .4, ts: .8, td: .5 } },
    { k: "nib", label: "Tilt Turn", cells: { ps: .4, tw: .9, pw: .4 } },
    { k: "quick", label: "Flick", cells: { ps: .3, ss: .7, sd: .5, sw: .4 } },
    { k: "custom", label: "Custom", cells: null }
  ]
};
CHOICES.hand.forEach(c => { c.dials = HAND_DIALS; c.macro = "gain"; c.words = ["steady", "responsive"]; });
const choiceOf = (part, k) => CHOICES[part].find(c => c.k === k) || CHOICES[part][0];
const choiceIndex = (part, k) => Math.max(0, CHOICES[part].findIndex(c => c.k === k));
const dialSpec = (part, key) => DIALS[part][key];
// A Title Case label in a sentence or a card's words (ModularWords.sentence): "Flat Nib" as "Flat nib".
const sentenceCase = t => t.charAt(0) + t.slice(1).toLowerCase();
const dialLabel = (part, k, key) => (choiceOf(part, k).labels || {})[key] || DIALS[part][key].label;
function rangeOf(part, k, key) { const c = choiceOf(part, k), s = DIALS[part][key]; return (c.range && c.range[key]) || [s.min, s.max]; }
function dialDefault(part, k, key) { const c = choiceOf(part, k); return c.def && c.def[key] !== undefined ? c.def[key] : DIALS[part][key].def; }
function fmtDial(part, key, v) {
  const s = DIALS[part][key];
  if (s.choices) return s.choices[v | 0];
  if (s.fmt) return s.fmt(v);
  if (s.min === 0 && v < .005) return "off";
  return v.toFixed(2);
}
// A part holding choice k, every dial at its default.
function freshPart(part, k) {
  const c = choiceOf(part, k), d = {};
  for (const key of c.dials) d[key] = dialDefault(part, k, key);
  if (part === "hand" && c.cells) for (const key of HAND_CELLS) d[key] = c.cells[key] || 0;
  return { k: c.k, d };
}
// Dials added after the prototype's, last in their choices' lists (the app's ModularCode.ADDED): written only when one is
// off its default, so a brush that doesn't use them keeps its code; never rolled, so every roll draws as before.
const ADDED = new Set(["medium.dust", "medium.erase", "carry.soften", "carry.keep", "carry.pload", "carry.pmin"]);
// Erasable (the Erasable spec): every medium starts at 1, erases clean (the owner, 2026-10-22: "per-tool things should be
// erasable unless the user specifies"; until then the real medium's: ink permanent, a wash .4, gouache .3, Oils' paint off).
// A code that doesn't say reads 1. Resist's dial is wax or fluid (newmods.js).
const REAL_ERASE = { ink: 1, wash: 1, cover: 1, charcoal: 1, graphite: 1 };
const realErase = (style, k) => REAL_ERASE[k] ?? 1;
// No roll changes Erasable (the app's ModularStyle.keepErasable; the owner: "the randomizer leaves that var alone unless the
// user moves it"): B's medium takes old's value, or 1 with no old brush or Resist on either side. Draws nothing.
function keepErase(B, old) {
  const M = B.medium, O = old && old.medium;
  if (M.k === "resist" || !choiceOf("medium", M.k).dials.includes("erase")) return;
  M.d.erase = O && O.k !== "resist" && O.d.erase !== undefined ? O.d.erase : 1;
}
// Swap a part's choice: the new choice's defaults, keeping dials that mean the same thing in both (a tip's tapers, say).
const KEEPS = { tip: ["weight", "taperIn", "taperOut", "dry", "wobble", "soft", "angle"], medium: ["pool", "bleed", "bloom", "splatter"], carry: ["pload", "pmin"], paper: ["tooth"], life: ["drying"], hand: ["gain", "dir"] };
function switchChoice(part, P, k) {
  const out = freshPart(part, k);
  if (P) for (const key of KEEPS[part]) if (key in out.d && key in P.d && !(part === "paper" && P.k === "plain")) out.d[key] = P.d[key];
  if (part === "tip" && P && (k === "chisel" || k === "flat" || k === "split")) { const [lo, hi] = rangeOf("tip", k, "aspect"); if ("aspect" in P.d && P.d.aspect >= lo && P.d.aspect <= hi) out.d.aspect = P.d.aspect; }
  if (part === "hand" && P && k === "custom") Object.assign(out.d, P.d);
  // A medium chosen keeps its Erasable (the app's ModularBrush.switchTo), 1 from or to Resist (wax or fluid).
  if (part === "medium") out.d.erase = P && P.k !== "resist" && k !== "resist" && P.d.erase !== undefined ? P.d.erase : REAL_ERASE[k];
  return out;
}
function normPart(part, P) {
  const c = choiceOf(part, P.k), d = {};
  P.k = c.k;
  for (const key of c.dials) {
    const s = DIALS[part][key], [lo, hi] = rangeOf(part, c.k, key);
    let v = P.d[key];
    if (v === undefined || !Number.isFinite(v)) v = dialDefault(part, c.k, key);
    v = s.choices ? clamp(Math.round(v), 0, s.choices.length - 1) : clamp(v, lo, hi);
    if (s.int) v = Math.round(v);
    d[key] = v;
  }
  P.d = d;
  return P;
}
function normBrush(B) { for (const p of PARTS) { B[p] ??= freshPart(p, CHOICES[p][0].k); normPart(p, B[p]); } B.seed = B.seed >>> 0; return B; }
const cloneBrush = B => JSON.parse(JSON.stringify(B));
const val = (B, part, key) => B[part].d[key] !== undefined ? B[part].d[key] : 0;
// The hand's cell in use: amount times the overall response, at most 1.
const handCell = (B, key) => Math.min(1, (B.hand.d[key] || 0) * (B.hand.d.gain ?? 1));

// ===================================================================================================================
// resolve: parts → what the engine draws. The medium sets the blend of a line (the pen engine) and a body of paint
// (the paint engine) as pen-paint-blend's partsA; the tip picks the stampers; the hand feeds every stamper.
// ===================================================================================================================
// How dark a body lays (the app's ModularSizes): a body glazes at GLAZE of the swatch's darkness (ink's wet body, opaque
// paint's, a wash's until 2026-10-19); a wash, since washes reach black, at WASH_BLACK of it where its body is full. A
// body of strength x (its density times washK) lays x up to KNEE, as every body always laid, then eases toward black.
const GLAZE = .34, WASH_BLACK = .97, KNEE = .55;
function laid(x) { return x <= KNEE ? x : 1 - (1 - KNEE) * Math.exp(-(x - KNEE) / (1 - KNEE)); }
function strength(y) { return y <= KNEE ? y : KNEE - (1 - KNEE) * Math.log((1 - Math.min(y, .999)) / (1 - KNEE)); }
// A wash's strength at swatch darkness d: its full body lays WASH_BLACK of d.
function washStrength(d) { return strength(WASH_BLACK * clamp(d)); }
function resolve(B, size, shade) {
  const T = B.tip, M = B.medium, C = B.carry, P = B.paper, L = B.life, tk = T.k, mk = M.k;
  const t = key => T.d[key] ?? 0, m = key => M.d[key] ?? 0, h = key => handCell(B, key);
  const lineMedium = mk === "ink" || mk === "charcoal" || mk === "graphite";
  let w = 0, line = 1, mode = "wet", wetMul = 1, cover = 0, impasto = 0, rule = "ink", lineDark = 1;
  if (mk === "ink") { w = m("wet"); line = m("line"); }
  else if (mk === "wash") { w = 1; line = m("line"); mode = m("wet") >= .5 ? "wet" : "flat"; wetMul = mode === "wet" ? lerp(.4, 1, (m("wet") - .5) * 2) : 1; }
  else if (mk === "cover") { w = 1; line = 0; mode = "flat"; cover = m("cover"); impasto = m("impasto"); }
  else { rule = mk; }
  if (mk === "charcoal") lineDark = lerp(.72, 1, m("hold"));
  if (mk === "graphite") lineDark = 1 - lerp(185, 40, m("grade")) / 255;
  const weight = tk === "round" || T.d.weight !== undefined ? (T.d.weight ?? 1) : 1;
  let inkSize, washSize;
  if (lineMedium) { inkSize = size * weight; washSize = Math.min(360, inkSize * 8); }
  else { washSize = size * weight; inkSize = Math.max(1.5, washSize / 10); }
  // Charcoal is the costliest stamp (its fuzz never fills): capped at 240 px.
  if (mk === "charcoal") inkSize = Math.min(inkSize, 240);
  // A Filbert (the oil paint spec) is an oval nib at its width, as a chisel's; it follows the pen's tilt (pen.filbert).
  const nib = tk === "chisel" || tk === "split" || tk === "filbert" ? "chisel" : tk === "flat" ? "flat" : "round";
  const speedAmt = h("ss"), thicker = (B.hand.d.dir | 0) === 1;
  const bb = { seed: B.seed >>> 0, wet: w, line, nib, aspect: nib === "round" ? 1 : t("aspect"), angle: T.d.angle ?? 45,
    taperIn: t("taperIn"), taperOut: t("taperOut"), pressure: h("ps"), speed: speedAmt === 0 ? 0 : (thicker ? 1 : -1) * speedAmt, thicker,
    grain: P.d.tooth ?? 0, bristle: tk === "bristles" ? 0 : t("dry"), wobble: t("wobble"), pool: m("pool"), splatter: m("splatter"), mode,
    soft: T.d.soft ?? .3, bleed: m("bleed") * wetMul, bloom: m("bloom") * wetMul, backrun: m("backrun") * wetMul, drying: L.d.drying ?? .35,
    lines: "centre", count: Math.round(T.d.count ?? 3), spread: .6, breathe: 0, offset: m("offset"), chaos: m("chaos") };
  if (mk === "ink") bb.lines = ["centre", "outline", "squiggle"][M.d.lines | 0];
  if (tk === "split") { bb.lines = "split"; bb.spread = t("gap"); bb.breathe = Math.max(.25, h("pw")); }
  if (tk === "multi") { bb.lines = "spread"; bb.spread = t("reach"); bb.breathe = h("pw"); }
  const Pa = partsA(bb, inkSize, washSize);
  const life = L.k, fast = life === "none";
  const hand = { scaleAware: true, fast, tiltWide: (B.hand.d.trange | 0) === 1, tiltSize: h("ts"), tiltAngle: h("tw"), tDark: h("td"), pDark: h("pd"), sDark: h("sd"), pWidth: h("pw"), sAngle: h("sw") };
  const pen = Object.assign(Pa.pen, hand, { fuzz: rule === "ink" ? 0 : m("spread"), thicker });
  // The hand's Tilt Shading (newmods.js): a nib laid low lays its side.
  pen.side = B.hand.d.tshade ?? 0;
  if (tk === "split" || tk === "multi") pen.pWidth = 0;   // pressure → width is their breathing, inside the placement
  const body = Object.assign(Pa.body, hand, { cover, impasto, gran: Pa.settle.gran, head: tk === "hake" ? "flat" : "round", headTurn: ["follow", "fixed"][T.d.headTurn | 0],
    tilt: Math.max(h("ts"), h("tw")), speed: Pa.body.speed ?? 0 });
  // Paint's own speed and nib: a body follows the hand's speed too (0 for every old paint), and a shaped tip's ellipse.
  body.speed = lineMedium ? 0 : bb.speed;
  if (!lineMedium && (tk === "chisel" || tk === "flat")) { body.aspect = clamp(t("aspect") * 1.6, .25, 1); body.angle = bb.angle; }
  // A Filbert's paint is its own oval, at its width, following tilt (engine.js stampBody).
  if (tk === "filbert") { pen.filbert = true; body.filbert = true; if (!lineMedium) { body.aspect = t("aspect"); body.angle = bb.angle; } }
  const look = Object.assign({}, Pa.settle, { cover, impasto, scaleAware: true });
  const R = { parts: Pa, pen, body, look, rule, paper: P.k, life, inkSize, washSize, bodySize: lineMedium ? Pa.bodySize : washSize,
    line: Pa.stampLine, body: Pa.bodyAmt > 0, lineTooth: Pa.pen.tooth, cover, gray: PAPER_TONE * (1 - shade), shade,
    // Drying's light preview grey: the swatch's shade, a wash's as dark as its glaze is to the old 34% one.
    previewShade: mk === "wash" ? WASH_BLACK * shade / GLAZE : shade,
    hold: m("hold"), spread: m("spread"), grade: m("grade"), lineInk: mk === "ink",
    windowLen: (L.d.window ?? 6) * 12 + 0.5 * (lineMedium && w < .5 ? inkSize : washSize), drying: L.d.drying ?? .35, settle: life === "dry" ? (L.d.settle ?? 0) : 0,
    takes: life === "dry" ? ["", "drift", "vein"][L.d.takes | 0] : "", plume: L.d.plume ?? .55 };
  R.body = Pa.bodyAmt > 0 ? body : null;
  R.line = Pa.stampLine;
  // Which stamper draws each part.
  R.lineTip = tk === "hake" ? "hake" : tk === "bristles" ? "bristles" : "nib";
  R.linePlaced = R.lineTip === "nib" && Pa.placed;
  R.lineStateful = R.lineTip !== "nib";
  R.placeBody = Pa.body;
  // A multi-line tip spreads its lines over a band wider than one line: the line plus 1.4 lines' room per extra line.
  R.placeSize = tk === "multi" ? Math.max(Pa.bodySize, inkSize * (2 + 1.4 * bb.count)) : Pa.bodySize;
  R.lineReach = R.linePlaced ? Math.ceil(Math.max(Pa.bodySize, tk === "multi" ? inkSize * (2 + 1.4 * bb.count) : 0) * .9 + inkSize * 3 + (bb.offset > 0 ? Pa.bodySize * 1.4 + inkSize * 2.5 : 0) + (bb.chaos > 0 ? Pa.bodySize * .6 + inkSize : 0)) : 0;
  if (R.lineTip === "hake") R.lineHake = { seed: bb.seed, mode: "flat", pressure: h("ps"), soft: T.d.soft ?? .2, streaks: t("dry"), gran: 0, angle: bb.angle, headTurn: body.headTurn,
    tilt: body.tilt, fast: false, head: "flat", cover: 0, pDark: h("pd"), pWidth: h("pw"), softK: lerp(.012, .6, T.d.soft ?? .2) };
  if (R.lineTip === "bristles") R.lineSwarm = { seed: bb.seed, hairs: t("hairs"), cohesion: t("cohesion"), spreadH: t("splay"), dryness: t("dryness"), tiltSize: h("ts"), tDark: h("td"), pDark: h("pd"), sDark: h("sd") };
  R.bodyTip = tk === "bristles" ? "bristles" : (tk === "split" || tk === "multi") && !lineMedium ? "placed" : "body";
  R.bodyStateful = tk === "bristles" || tk === "hake";
  if (R.bodyTip === "bristles") R.bodySwarm = Object.assign({}, R.lineSwarm || { seed: bb.seed, hairs: t("hairs"), cohesion: t("cohesion"), spreadH: t("splay"), dryness: t("dryness"), tiltSize: h("ts"), tDark: h("td"), pDark: h("pd"), sDark: h("sd") }, { amount: Pa.bodyAmt });
  if (R.bodyTip === "placed") {
    R.bodyLineSize = washSize * (tk === "split" ? .55 : .22);
    R.bodyPen = Object.assign({}, pen, { lines: tk === "split" ? "split" : "spread", wetGap: 0, dry: t("dry"), aspect: tk === "split" ? t("aspect") : 1, skipStart: 0, skipEnd: 0, pool: 0, splatter: 0 });
    R.bodyGeom = Object.assign({}, Pa.body, { aspect: 1 });
    R.bodyPlaceSize = tk === "multi" ? washSize * 1.8 : washSize;
  }
  // How dark: the swatch's grey, the share of the line that stays crisp, charcoal's and graphite's darkest, and Carry's
  // own-paint share, opaque paint's cover too (Smudge spec F1). A body glazes at a third of the swatch, as the app's
  // paint shades (PAINT_DARK); a wash to near black (washStrength: its full body lays .97 of the swatch's grey).
  // Clear Water lays no paint of its own (the app's ModularBrush.ownPaint).
  const load = C.k === "off" ? 1 : C.k === "water" ? 0 : (C.d.load ?? 1);
  R.inkK = shade * Pa.inkShare * lineDark * load;
  R.washK = (mk === "wash" ? washStrength(shade) : GLAZE * shade) * load;
  R.cover = cover * load;
  R.erase = M.d.erase ?? 1;
  if (life === "float") R.float = floatParts(B, R, load);
  if (C.k !== "off") {
    // As wide as the mark, to 180 px, half the largest brush (F4); at most 63 × 63 cells.
    const r = Math.max(3, Math.min(180, (lineMedium && w < .4 ? inkSize : washSize) / 2));
    // Dabs 1 + .08 r apart (r / 4 below 15 px): closer than the lab's first r / 4, so a smear streaks smoothly, without combing.
    const spacing = Math.max(1.5, Math.min(r * .25, 1 + r * .08));
    R.carry = { kind: C.k, moves: !(C.k === "lift" && R.cover > 0), pick: C.d.pick ?? .4, lay: C.d.lay ?? .5, paper: PAPER_TONE, r, spacing, hits: r / spacing, stride: r > 30 ? r / 30 : 1, n: Math.ceil(r / (r > 30 ? r / 30 : 1)) + 1,
      keep: C.k === "drag" || C.k === "smudge" ? (C.d.keep ?? 0) : 0 };
    // Smear's Soften: part or all of it MonoPaint's blending stump (stroke.js stumpDab); a Rake or a Split nib combs, along
    // its tines (as floatParts places them, a Rake's over its lines' band).
    const soften = C.k === "smudge" ? (C.d.soften ?? 0) : 0;
    if (soften > 0) {
      const tn = tinesOf(B.tip, R.inkSize, R.placeSize / 2);
      Object.assign(R.carry, { soften, tines: tn ? tn.tines : null, tineW: tn ? tn.sig : 0 });
      R.carry.tineReach = 1.6 * R.carry.tineW;
      R.carry.blur = tn ? Math.max(1, Math.round(tn.sig)) : Math.max(2, Math.min(8, Math.round(r * .2)));
    } else R.carry.soften = 0;
  }
  // Paint Load (the oil paint spec; the app's LoadedPaint): a Drag's or Smear's own paint runs out along the stroke, only
  // one that lays some and never on water. A full load lays 3000 px × Paint Load², for a 60 px brush (the root of its
  // width: half to twice); every stroke starts with its Starting Load's share (stroke.js oilSpend). A Filbert's dabs are
  // crescents then.
  if ((C.k === "drag" || C.k === "smudge") && (C.d.pload ?? 0) > 0 && life !== "float" && load > 0 && (R.line || R.body)) {
    const pl = C.d.pload, sz = R.body ? R.bodySize : R.inkSize;
    R.loaded = { full: OIL_FULL_PX * pl * pl * clamp(Math.sqrt(Math.max(1, sz) / OIL_REF_PX), .5, 2), start: clamp(C.d.pmin ?? .25) };
    if (R.body && R.body.filbert) R.body.crescent = true;
  }
  return R;
}
const OIL_FULL_PX = 3000, OIL_REF_PX = 60;
// How much wider or narrower than its full width a stroke lays at pressure pr and speed v (the app's LoadedPaint.widthShare):
// its body's dab, else its line's, 1 at a full press. A wider dab spends paint faster.
function oilWidth(R, pr, v) {
  if (R.body) return paintDiameter(R.body, 1, pr, null);
  const P = R.pen.pressure, wd = (p, q) => lerp(1 - .75 * P, 1 + .6 * P, p) * clamp(1 + R.pen.speed * .55 * (clamp(q / 2.5) - .3), .35, 1.6);
  return wd(pr, v) / wd(1, 0);
}
// A Rake's or a Split nib's tines: each one's offset across the stroke (a Rake's spread over hw either side) and their
// half-width; null for a tip that drags with one point (the app's ResolvedBrush.tinesOf).
function tinesOf(T, inkSize, hw) {
  const t = key => T.d[key] ?? 0;
  if (T.k === "multi") {
    const n = Math.round(t("count") || 3), half = hw * lerp(.3, .95, t("reach")), gap = 2 * half / Math.max(1, n - 1), tines = [];
    for (let j = 0; j < n; j++) tines.push(-half + gap * j);
    return { tines, sig: Math.max(2.5, Math.min(gap / 3, inkSize * .4)) };
  }
  if (T.k === "split") { const g = Math.max(2, inkSize * .6 * (t("gap") + .3)); return { tines: [-g, g], sig: Math.max(2.5, inkSize * .4) }; }
  return null;
}
// Float: what the stroke does on the water. Own paint (Smudge's) is how much paint it puts on the water, 0 = clear water;
// Drag and Smudge drag what floats (Pick up, with pressure, as MonoPaint's brush pen's Strength); Off and Lift don't drag.
// The pen's tines: one stylus as wide as the tip (a hake's is broad), or one per line of a split nib or multi-line.
function floatParts(B, R, load) {
  const T = B.tip, C = B.carry, L = B.life, tk = T.k, t = key => T.d[key] ?? 0;
  const drags = C.k === "drag" || C.k === "smudge" || C.k === "water";
  const lineW = R.inkSize / 2 * (tk === "multi" ? Math.round(t("count") || 3) : tk === "split" ? 2 : 1);
  const filmW = Math.max(R.body ? R.bodySize / 2 : 0, R.line ? lineW : 0, 1);
  const hw = R.body ? R.bodySize / 2 : tk === "multi" ? R.placeSize / 2 : R.inkSize / 2;
  let tines = [0], sig = Math.max(3, hw * (tk === "hake" ? .7 : .8));
  const tn = tinesOf(T, R.inkSize, hw);
  if (tn) ({ tines, sig } = tn);
  return { spread: L.d.spread ?? .5, drift: L.d.drift ?? .5, dryMs: wetMsOf(L.d.drying ?? .6), load, pick: drags ? (C.d.pick ?? .4) : 0,
    filmW, tines, sig, lineDark: clamp(R.inkK), bodyDark: laid(R.washK * 1.2), dropDark: R.line ? clamp(R.inkK) : laid(R.washK * 1.2) };
}
// ---------- pen-paint-blend's A: two parts, a line and a wash, fed the same samples (copied) ----------
function partsA(bb, inkSize, washSize) {
  const w = bb.wet, s = smooth(w), mid = 4 * w * (1 - w), wetPaint = bb.mode === "wet";
  const pen = { seed: bb.seed, nib: bb.nib, aspect: bb.aspect, angle: bb.angle, taperIn: bb.taperIn, taperOut: bb.taperOut,
    pressure: bb.pressure, speed: bb.speed, dry: bb.bristle * (1 - w), tooth: bb.grain * (1 - w), wobble: bb.wobble,
    pool: bb.pool * (1 - w), splatter: bb.splatter,
    lines: bb.lines || "centre", count: bb.count, spread: bb.spread, breathe: bb.breathe, wetGap: clamp(w * 1.8),
    offset: bb.offset || 0, chaos: bb.chaos || 0, skipStart: 0, skipEnd: 0 };
  const inkShare = mix(1, bb.line, s);
  const lineWet = mid * 1.2 + w * bb.line;
  const bodyAmt = smooth((w - .08) / .75);
  const body = { seed: bb.seed, mode: bb.mode, pressure: bb.pressure, soft: mix(1, bb.soft, s), streaks: bb.bristle,
    aspect: mix(bb.aspect, 1, s), angle: bb.angle, taperIn: bb.taperIn * (1 - w), taperOut: bb.taperOut * (1 - w), amount: bodyAmt, skipStart: 0, skipEnd: 0 };
  const bodySize = mix(inkSize * 2.2, washSize, Math.pow(w, 1.4));
  if (pen.offset > 0) {
    const r = rng(bb.seed ^ 0x1a6), len = () => pen.offset * (14 + bodySize * .55) * (.4 + .6 * r());
    const a = len(), b = len();
    if (r() < .5) pen.skipStart = a; else body.skipStart = a;
    if (r() < .5) pen.skipEnd = b; else body.skipEnd = b;
  }
  const settle = { feather: clamp(bb.bleed * s + .3 * mid), hollow: wetPaint ? bb.pool * s : 0, bloom: wetPaint ? bb.bloom * s : 0,
    backrun: wetPaint ? bb.backrun * s : 0, gran: bb.grain * s, streaks: 0 };
  return { w, pen, inkShare, lineWet, body, bodyAmt, settle, lineSize: inkSize, bodySize, diffuse: mid, diffR: Math.round(1 + mid * (1.5 + inkSize * .5)),
    stampLine: inkShare > 0 || lineWet > 0, placed: pen.lines !== "centre" || pen.offset > 0 || pen.chaos > 0 };
}

// ===================================================================================================================
// Styles: a preset of parts plus ranges. A range is [lo, hi] always, {p, lo, hi} on that often, or bands with weights.
// A dial a style leaves out rolls at its choice's default.
// ===================================================================================================================
const WILDCARD = "Anything goes";
const MSTYLES = {
  "Fineliner": { size: 4, parts: { tip: { round: 1 }, medium: { ink: 1 }, carry: { off: 1 }, paper: { plain: 3, "cold press": 1 }, life: { wet: 1 } },
    d: { "tip.taperIn": { p: .4, lo: 0, hi: .15 }, "tip.taperOut": { p: .4, lo: 0, hi: .2 }, "tip.wobble": { p: .2, lo: 0, hi: .1 }, "tip.weight": [.9, 1.1],
      "medium.wet": [0, 0], "medium.line": [1, 1], "medium.pool": { p: .4, lo: 0, hi: .3 }, "paper.tooth": { p: .3, lo: 0, hi: .15 }, "hand.ps": [0, .25], "hand.ss": { p: .4, lo: 0, hi: .2 } } },
  "Brush pen": { size: 8, parts: { tip: { round: 2, chisel: 1 }, medium: { ink: 1 }, carry: { off: 1 }, paper: { plain: 2, "cold press": 1, felt: 1 }, life: { wet: 1 } },
    d: { "tip.taperIn": [.3, .8], "tip.taperOut": [.4, 1], "tip.dry": { p: .3, lo: 0, hi: .3 }, "tip.wobble": { p: .3, lo: 0, hi: .15 }, "tip.aspect": [.3, .55],
      "medium.wet": { p: .2, lo: .1, hi: .35 }, "medium.line": [.75, 1], "medium.pool": { p: .4, lo: 0, hi: .4 }, "medium.splatter": { p: .15, lo: 0, hi: .3 },
      "paper.tooth": { p: .3, lo: 0, hi: .2 }, "hand.ps": [.7, 1], "hand.pd": { p: .5, lo: .2, hi: .5 }, "hand.ss": [0, .7], "hand.ts": { p: .4, lo: .3, hi: .8 } } },
  "Dip pen": { size: 6, parts: { tip: { flat: 2, chisel: 1, split: 2 }, medium: { ink: 1 }, carry: { off: 1 }, paper: { plain: 1, laid: 1, "cold press": 1 }, life: { wet: 1 } },
    d: { "tip.angle": [30, 60], "tip.gap": [.35, .8], "tip.taperIn": { p: .5, lo: 0, hi: .3 }, "tip.taperOut": { p: .5, lo: 0, hi: .4 }, "tip.dry": { p: .25, lo: 0, hi: .25 },
      "medium.wet": { p: .15, lo: .1, hi: .3 }, "medium.pool": { p: .6, lo: .1, hi: .6 }, "medium.splatter": { p: .3, lo: 0, hi: .35 }, "paper.tooth": { p: .3, lo: 0, hi: .2 },
      "hand.ps": [.4, .85], "hand.pw": [.5, 1], "hand.ss": { p: .5, lo: 0, hi: .3 }, "hand.tw": { p: .5, lo: .4, hi: .9 } } },
  "Marker": { size: 12, parts: { tip: { chisel: 2, round: 1 }, medium: { ink: 1 }, carry: { off: 1 }, paper: { plain: 1, felt: 1 }, life: { wet: 1 } },
    d: { "tip.aspect": [.35, .55], "medium.wet": { p: .3, lo: .05, hi: .25 }, "medium.pool": [.3, .8], "medium.bleed": { p: .3, lo: 0, hi: .3 }, "paper.tooth": { p: .3, lo: 0, hi: .2 },
      "hand.ps": [0, .2], "hand.ss": [0, .3], "hand.tw": { p: .3, lo: .3, hi: .7 } } },
  "Pencil": { size: 4, parts: { tip: { round: 1 }, medium: { graphite: 1 }, carry: { off: 6, smudge: 1 }, paper: { "cold press": 1, felt: 1, laid: 1 }, life: { wet: 1 } },
    d: { "medium.grade": [.2, .8], "medium.spread": { p: .3, lo: 0, hi: .15 }, "tip.taperIn": { p: .4, lo: 0, hi: .2 }, "tip.taperOut": { p: .4, lo: 0, hi: .25 }, "tip.wobble": { p: .3, lo: 0, hi: .12 },
      "paper.tooth": [.5, .9], "carry.pick": [.3, .6], "hand.ps": [.2, .5], "hand.pd": [.4, .8], "hand.ts": [.3, .7], "hand.td": [.3, .6] } },
  "Charcoal": { size: 30, parts: { tip: { chisel: 2, round: 1 }, medium: { charcoal: 1 }, carry: { off: 3, smudge: 1 }, paper: { "cold press": 2, laid: 1, plain: 1 }, life: { wet: 1 } },
    d: { "medium.spread": { bands: [[.7, .15, .45], [.15, 0, .1], [.15, .6, .9]] }, "medium.hold": [.1, .8], "tip.taperIn": { p: .4, lo: 0, hi: .3 }, "tip.taperOut": { p: .4, lo: 0, hi: .3 },
      "tip.dry": { p: .5, lo: .1, hi: .4 }, "tip.wobble": { p: .6, lo: 0, hi: .4 }, "tip.aspect": [.3, .55], "paper.tooth": [.6, 1],
      "hand.ps": [.5, .9], "hand.pd": [.3, .7], "hand.ts": [.6, 1], "hand.td": [.4, .7] } },
  "Watercolor": { size: 120, parts: { tip: { round: 1 }, medium: { wash: 1 }, carry: { off: 1 }, paper: { plain: 1, "cold press": 2 }, life: { wet: 4, dry: 1 } },
    d: { "tip.soft": [.2, .6], "medium.wet": [.7, 1], "medium.pool": { p: .6, lo: .2, hi: .7 }, "medium.bleed": { p: .3, lo: 0, hi: .3 }, "medium.bloom": { p: .6, lo: .2, hi: .6 },
      "medium.backrun": { p: .3, lo: 0, hi: .5 }, "paper.tooth": { p: .5, lo: 0, hi: .5 }, "life.drying": [.3, .7], "hand.ps": [.3, .8] } },
  "Ink wash": { size: { ink: 8, wash: 90 }, parts: { tip: { round: 1 }, medium: { ink: 3, wash: 2 }, carry: { off: 1 }, paper: { plain: 1, felt: 1 }, life: { wet: 3, dry: 1 } },
    d: { "tip.soft": [.5, 1], "tip.taperIn": { p: .5, lo: .1, hi: .5 }, "tip.taperOut": { p: .5, lo: .2, hi: .7 }, "medium.wet": [.45, .9], "medium.line": [.5, 1],
      "medium.bleed": [.3, .8], "medium.bloom": { p: .3, lo: 0, hi: .3 }, "medium.pool": { p: .3, lo: 0, hi: .3 }, "life.drying": [.5, .9], "hand.ps": [.4, .9] } },
  "Gouache": { size: 60, parts: { tip: { round: 2, flat: 1, hake: 1 }, medium: { cover: 1 }, carry: { off: 1, lift: 1 }, paper: { plain: 2, felt: 1, "cold press": 1 }, life: { wet: 1 } },
    d: { "medium.cover": [1, 1], "medium.impasto": [0, 0], "tip.soft": [0, .2], "tip.dry": { p: .3, lo: 0, hi: .3 }, "tip.weight": [.8, 1.1], "paper.tooth": { p: .3, lo: 0, hi: .2 }, "hand.ps": [0, .3] } },
  "Oils": { size: 60, parts: { tip: { round: 2, flat: 1, hake: 1 }, medium: { cover: 1 }, carry: { drag: 7, off: 3 }, paper: { canvas: 1 }, life: { wet: 1 } },
    d: { "medium.cover": [1, 1], "medium.impasto": [.4, .9], "tip.dry": [.5, .95], "tip.soft": [0, .15], "paper.tooth": { p: .4, lo: 0, hi: .3 }, "carry.pick": [.4, .8],
      "hand.ps": [.3, .7], "hand.ts": { p: .4, lo: .4, hi: .9 } } },
  "Pastels": { size: 24, parts: { tip: { chisel: 1, round: 1 }, medium: { charcoal: 1 }, carry: { off: 2, smudge: 1 }, paper: { "cold press": 1, felt: 1, crumpled: 1, laid: 1 }, life: { wet: 1 } },
    d: { "medium.spread": [.1, .35], "medium.hold": [.1, .5], "tip.dry": [.1, .3], "tip.aspect": [.35, .55], "paper.tooth": [.6, 1], "hand.ps": [.2, .6], "hand.ts": [.5, 1], "hand.td": [.3, .6] } },
  "Dry brush": { size: 60, parts: { tip: { round: 2, bristles: 2, flat: 1 }, medium: { wash: 7, cover: 3 }, carry: { off: 1 }, paper: { "cold press": 1, laid: 1, crumpled: 1 }, life: { wet: 1 } },
    d: { "medium.wet": [0, .4], "medium.cover": [.5, .9], "tip.dry": [.5, 1], "tip.soft": [0, .3], "tip.dryness": [.4, .8], "tip.splay": [.4, .9], "tip.hairs": [16, 32], "paper.tooth": [.3, .8], "hand.ps": [.3, .7] } },
  "Hake": { size: 180, parts: { tip: { hake: 1 }, medium: { wash: 7, cover: 3 }, carry: { off: 1 }, paper: { plain: 1, "cold press": 1 }, life: { wet: 1 } },
    d: { "tip.headTurn": { opts: [0, 0, 0, 1] }, "tip.angle": [70, 80], "tip.dry": [.2, .6], "tip.soft": [.1, .4], "medium.wet": [.3, 1], "medium.cover": [.5, .9], "hand.ps": [.1, .4] } },
  [WILDCARD]: { size: 16, wild: true },
  // Print (the app's own, after Anything goes as codes carry the index): every roll prints through a Print Shading, or
  // one in five is Screentone (newmods.js rollScreen). Pens a little broader than a Fineliner's; charcoal, pencil and wash broader still.
  "Print": { size: { ink: 12, charcoal: 30, graphite: 14, wash: 40 }, parts: { tip: { round: 2, chisel: 1 }, medium: { ink: 3, wash: 1, charcoal: 1, graphite: 1 }, carry: { off: 1 }, paper: { plain: 2, "cold press": 1 }, life: { wet: 1 } },
    d: { "tip.soft": [.3, .7], "tip.aspect": [.35, .55], "tip.taperIn": { p: .3, lo: 0, hi: .2 }, "tip.taperOut": { p: .3, lo: 0, hi: .3 }, "medium.grade": [.4, .8], "medium.spread": [.1, .4],
      "medium.hold": [.3, .7], "medium.wet": [0, 0], "medium.line": [1, 1], "paper.tooth": { p: .3, lo: 0, hi: .3 }, "hand.ps": [.1, .4], "hand.pd": [.4, .8] } }
};
const STYLE_NAMES = Object.keys(MSTYLES);
// The size a style draws at best, for the size slot nearest to it.
function styleSize(name, B) { const s = (MSTYLES[name] || MSTYLES[WILDCARD]).size; return typeof s === "number" ? s : s[B && B.medium.k] || Object.values(s)[0]; }
const rnd = () => Math.random();
function pickWeighted(map) { const e = Object.entries(map); let t = 0; for (const [, v] of e) t += v; let r = rnd() * t; for (const [k, v] of e) { r -= v; if (r <= 0) return k; } return e[e.length - 1][0]; }
function rollSpec(spec, lo, hi) {
  if (Array.isArray(spec)) return lerp(spec[0], spec[1], rnd());
  if (spec.bands) { const r = rnd(); let a = 0; for (const [p, l, h] of spec.bands) { a += p; if (r <= a) return lerp(l, h, rnd()); } const b = spec.bands[0]; return lerp(b[1], b[2], rnd()); }
  if (spec.opts) return spec.opts[(rnd() * spec.opts.length) | 0];
  return rnd() < spec.p ? lerp(spec.lo, spec.hi, rnd()) : 0;
}
// The range a style keeps a dial in (for the hatched band and for variations), or the dial's own range.
function styleBand(style, part, k, key) {
  const st = MSTYLES[style], spec = st && st.d && st.d[part + "." + key], [lo, hi] = rangeOf(part, k, key);
  if (!spec || DIALS[part][key].choices) return null;
  if (Array.isArray(spec)) return [clamp(spec[0], lo, hi), clamp(spec[1], lo, hi)];
  if (spec.bands) return [Math.min(...spec.bands.map(b => b[1])), Math.max(...spec.bands.map(b => b[2]))];
  if (spec.opts) return null;
  return [spec.p < 1 ? 0 : spec.lo, spec.hi];
}
// Wildcard: any choice of every part; a rough dial is off about half the time, so plain brushes still turn up.
const WILD_PARTS = { tip: { round: 3, chisel: 2, flat: 2, hake: 1, split: 1.5, multi: 1.5, bristles: 1 }, medium: { ink: 3, wash: 3, cover: 2, charcoal: 1.5, graphite: 1 },
  carry: { off: 6, lift: 1, drag: 1, smudge: 1 }, paper: { plain: 3, "cold press": 2, laid: 1, felt: 1, crumpled: 1, canvas: 1 }, life: { wet: 7, dry: 2, none: 1 } };
const ROUGH = { "tip.dry": 1, "tip.wobble": 1, "paper.tooth": 1 };
function rollPart(style, part, old, locked) {
  const st = MSTYLES[style] || MSTYLES[WILDCARD];
  if (part === "hand") return rollHand(st);
  const weights = st.wild ? WILD_PARTS[part] : st.parts[part];
  const k = pickWeighted(weights);
  const P = freshPart(part, k);
  for (const key of choiceOf(part, k).dials) {
    if (locked && locked.has(part + "." + key) && old && old.k === k) { P.d[key] = old.d[key]; continue; }
    if (ADDED.has(part + "." + key)) { if (key === "erase") P.d.erase = realErase(style, k); continue; }
    P.d[key] = rollDial(style, part, k, key);
  }
  if (st.wild) {
    let rough = 0; for (const key of Object.keys(P.d)) if (ROUGH[part + "." + key]) rough += P.d[key];
    if (rough > 1.1) for (const key of Object.keys(P.d)) if (ROUGH[part + "." + key]) P.d[key] *= 1.1 / rough;
  }
  return normPart(part, P);
}
function rollDial(style, part, k, key) {
  const st = MSTYLES[style] || MSTYLES[WILDCARD], s = DIALS[part][key], spec = st.d && st.d[part + "." + key], [lo, hi] = rangeOf(part, k, key);
  if (s.choices) { if (spec && spec.opts) return spec.opts[(rnd() * spec.opts.length) | 0]; if (st.wild || !spec) return st.wild ? (rnd() * s.choices.length) | 0 : dialDefault(part, k, key); }
  if (spec) return clamp(rollSpec(spec), lo, hi);
  if (!st.wild) return dialDefault(part, k, key);
  if (part === "life" && key === "window") return lerp(3, 9, rnd());
  if (key === "weight") return lerp(.8, 1.2, rnd());
  if (key === "line" && part === "medium") return k === "ink" ? lerp(.6, 1, rnd()) : rnd() < .7 ? 0 : lerp(.3, .8, rnd());
  if (key === "cover") return lerp(.6, 1, rnd());
  if (key === "offset" || key === "chaos") return rnd() < .25 ? lerp(0, .7, rnd()) : 0;
  if (s.min === 0 && !["wet", "soft", "angle", "aspect", "gap", "reach", "splay", "cohesion", "spread", "hold", "grade", "pick", "lay", "load", "drying", "settle", "plume", "dryness"].includes(key)) return rnd() < .5 ? lerp(lo, Math.min(hi, lo + (hi - lo) * .8), rnd()) : 0;
  return lerp(lo, hi, rnd());
}
function rollHand(st) {
  const P = freshPart("hand", "custom");
  if (st.wild) {
    P.d.ps = rnd() < .75 ? lerp(.3, 1, rnd()) : 0; P.d.pd = rnd() < .35 ? lerp(.2, .8, rnd()) : 0; P.d.pw = rnd() < .3 ? lerp(.3, 1, rnd()) : 0;
    P.d.ts = rnd() < .3 ? lerp(.3, 1, rnd()) : 0; P.d.td = rnd() < .2 ? lerp(.2, .7, rnd()) : 0; P.d.tw = rnd() < .25 ? lerp(.3, 1, rnd()) : 0;
    P.d.ss = rnd() < .35 ? lerp(.2, .8, rnd()) : 0; P.d.sd = rnd() < .2 ? lerp(.2, .7, rnd()) : 0; P.d.sw = rnd() < .15 ? lerp(.2, .8, rnd()) : 0;
    P.d.dir = rnd() < .5 ? 1 : 0;
  } else for (const key of HAND_CELLS) { const spec = st.d && st.d["hand." + key]; P.d[key] = spec ? rollSpec(spec) : 0; }
  P.k = matchHand(P);
  return normPart("hand", P);
}
// Float is rare: decided after every other part, from the brush's seed rather than another random draw, so every other
// roll (and the lab's seeded starting library) comes out exactly as before. Anything goes 1 in 30, Ink wash 1 in 25,
// Watercolor 1 in 40; no other style.
const FLOAT_ODDS = { [WILDCARD]: 30, "Ink wash": 25, "Watercolor": 40 };
function rollFloat(style, B) {
  const n = FLOAT_ODDS[style]; if (!n) return;
  const h = Math.imul((B.seed >>> 0) ^ 0x5f10a7, 2654435761) >>> 0;
  if (h % n) return;
  const P = switchChoice("life", B.life, "float");
  P.d.spread = .3 + .5 * ((h >>> 8) % 1000) / 1000; P.d.drift = .5;
  B.life = P;
}
// A hand that is exactly one of the presets is named for it; anything else is Custom.
function matchHand(P) {
  for (const c of CHOICES.hand) if (c.cells && HAND_CELLS.every(k => Math.abs((P.d[k] || 0) - (c.cells[k] || 0)) < .005)) return c.k;
  return "custom";
}
// A new brush in a style. locks: { parts: Set of part names, dials: Set of "part.key" }, empty in Simple.
function rollStyle(style, old, locks) {
  const lk = locks || { parts: new Set(), dials: new Set() };
  const B = { name: freshName(), seed: newSeed(), style };
  for (const part of PARTS) {
    if (old && lk.parts.has(part)) { B[part] = cloneBrush(old[part]); continue; }
    B[part] = rollPart(style, part, old && old[part], lk.dials);
    if (old && old[part].k === B[part].k) for (const key of Object.keys(B[part].d)) if (lk.dials.has(part + "." + key)) B[part].d[key] = old[part].d[key];
  }
  keepErase(B, old);
  if (!(old && lk.parts.has("life"))) rollFloat(style, B);
  normBrush(B);
  // Last: what stands out takes the adjective's place ("drip-plum"), from the finished brush and its seed.
  B.name = describedName(B, B.name);
  return B;
}
// One part rolled again (a card's dice), in the brush's style, keeping locked dials.
function rollOnePart(B, part, locks) {
  const c = cloneBrush(B), old = B[part];
  c[part] = rollPart(B.style, part, old, locks.dials);
  if (old.k === c[part].k) for (const key of Object.keys(c[part].d)) if (locks.dials.has(part + "." + key)) c[part].d[key] = old.d[key];
  if (part === "medium") keepErase(c, B);
  return normBrush(c);
}
function rollOneDial(B, part, key) {
  const c = cloneBrush(B), P = c[part];
  // Erasable has no die (only Resist's, wax or fluid): it moves only by hand.
  if (part === "medium" && key === "erase" && P.k !== "resist") return c;
  if (part === "hand" && HAND_CELLS.includes(key)) { P.d[key] = rnd() < .3 ? 0 : lerp(.25, 1, rnd()); P.k = matchHand(P); }
  else { const v = rollDial(B.style, part, P.k, key), bd = styleBand(B.style, part, P.k, key), [lo, hi] = rangeOf(part, P.k, key);
    P.d[key] = bd || DIALS[part][key].choices ? v : lerp(lo, hi, rnd()); }
  return normBrush(c);
}

// ===================================================================================================================
// Variations (Advanced only): six "your brush, but…" cards. Each nudges two or three unlocked dials; at most one of the
// six also swaps one part's choice, and says so ("Split nib", "Instant drying").
// ===================================================================================================================
function makeVars(B, locks) {
  const out = [], swapAt = rnd() < .85 ? (rnd() * 6) | 0 : -1;
  const pool = [];
  for (const part of PARTS) {
    if (locks.parts.has(part)) continue;
    for (const key of choiceOf(part, B[part].k).dials) {
      if (locks.dials.has(part + "." + key) || DIALS[part][key].choices || key === "gain" || ADDED.has(part + "." + key)) continue;
      pool.push([part, key]);
    }
  }
  for (let i = 0; i < 6; i++) {
    const c = cloneBrush(B), caps = []; let swap = null;
    c.seed = newSeed(); c.name = kinOf(B.name, c.seed);
    if (i === swapAt) {
      const parts = PARTS.filter(p => !locks.parts.has(p) && p !== "hand");
      const part = parts[(rnd() * parts.length) | 0];
      if (part) {
        const others = CHOICES[part].filter(ch => ch.k !== B[part].k && !(part === "hand" && ch.k === "custom"));
        const ch = others[(rnd() * others.length) | 0];
        c[part] = switchChoice(part, B[part], ch.k);
        caps.push(part === "life" && ch.k !== "float" ? sentenceCase(ch.label) + " drying" : sentenceCase(ch.label)); swap = caps[0];
      }
    }
    const pl = pool.slice(), n = 2 + (rnd() < .4 ? 1 : 0);
    for (let j = 0; j < n && pl.length; j++) {
      const [part, key] = pl.splice((rnd() * pl.length) | 0, 1)[0];
      if (!(key in c[part].d)) continue;
      const [lo0, hi0] = rangeOf(part, c[part].k, key), bd = styleBand(B.style, part, c[part].k, key) || [lo0, hi0];
      const v = c[part].d[key], lo = Math.min(bd[0], v), hi = Math.max(bd[1], v), span = hi - lo || (hi0 - lo0) * .3;
      const rel = (v - lo) / (span || 1);
      let sign = rnd() < .5 ? -1 : 1; if (rel < .08) sign = 1; else if (rel > .92) sign = -1;
      let nv = clamp(v + sign * lerp(.35, .8, rnd()) * span, lo0, hi0);
      if (DIALS[part][key].min === 0 && nv < .04) nv = 0;
      c[part].d[key] = nv;
      if (part === "hand") c.hand.k = matchHand(c.hand);
      caps.push((nv > v ? "more " : "less ") + dialLabel(part, c[part].k, key).toLowerCase().replace(/ \(.*\)/, ""));
    }
    out.push({ b: normBrush(c), caption: caps, swap });
  }
  return out;
}

// ===================================================================================================================
// Words: a card's choice in words, the hand's summary, the library caption and the plain "This brush" description.
// ===================================================================================================================
// The house style (docs/TEXT-STYLE.md), as the app's ModularWords: named, not explained; only what is unusual.
function handWords(B, n = 2) {
  const d = B.hand.d, g = d.gain ?? 1, items = [
    ["ps", "pressure", "widens"], ["pd", "pressure", "darkens"], ["pw", "pressure", B.tip.k === "split" ? "opens tines" : B.tip.k === "multi" ? "spreads lines" : "widens nib"],
    ["ts", "tilt", (d.trange | 0) ? "broadens" : "widens"], ["td", "tilt", "lightens"], ["tw", "tilt", "turns nib"],
    ["ss", "speed", (d.dir | 0) ? "thickens" : "thins"], ["sd", "speed", "lightens"], ["sw", "speed", "turns nib"]];
  const on = items.map(([k, c, e]) => [Math.min(1, (d[k] || 0) * g), c, e]).filter(x => x[0] > .15).sort((a, b) => b[0] - a[0]);
  if (!on.length) return "steady";
  const [, c1, e1] = on[0];
  if (n < 2 || on.length < 2) return c1 + " " + e1;
  const [, c2, e2] = on[1], verb = e => e.split(" ")[0], plural = e => verb(e).slice(0, -1) + e.slice(verb(e).length);
  if (c1 === c2) return verb(e1) === verb(e2) ? c1 + " " + e1 : c1 + " " + e1 + " and " + e2;
  if (e1 === e2) return c1 + " and " + c2 + " " + plural(e1);
  return c1 + " " + e1 + ", " + c2 + " " + e2;
}
// A Float brush with no paint of its own: clear water (Smudge's Own paint 0).
// A Smear that is mostly MonoPaint's blending stump (Soften from .5): "Softens.", tagged "stump"; with tines, "Combs.", "comb".
const isStump = B => B.carry.k === "smudge" && (B.carry.d.soften ?? 0) >= .5;
const combs = B => B.tip.k === "multi" || B.tip.k === "split";
const isClearWater = B => B.life.k === "float" && (B.carry.k === "water" || (B.carry.k === "drag" || B.carry.k === "smudge") && (B.carry.d.load ?? 1) < .02);
// Smudge's Clear Water off the water, and Keeps Paint on (a dirty brush; not all stump): the wet smudge spec.
const isWater = B => B.carry.k === "water" && B.life.k !== "float";
const keepsPaint = B => (B.carry.k === "drag" || B.carry.k === "smudge") && (B.carry.d.keep ?? 0) > 0 && (B.carry.d.soften ?? 0) < 1;
// Paint Load on (the oil paint spec; ModularWords.runsOut): its own paint runs out along the stroke. Not on water, nor
// Resist or Screentone, laid as a crisp ink line with no smudge.
const runsOut = B => (B.carry.k === "drag" || B.carry.k === "smudge") && (B.carry.d.pload ?? 0) > 0 && B.life.k !== "float" && (B.carry.d.load ?? 1) > 0
  && B.medium.k !== "resist" && B.medium.k !== "tone";
const dryingSec = B => (wetMsOf(B.life.d.drying) / 1000).toFixed(1);
const floatSec = B => { const sec = wetMsOf(B.life.d.drying) / 1000; return sec.toFixed(sec >= 9.95 ? 0 : 1); };
function choiceWords(B, part) {
  const P = B[part], c = choiceOf(part, P.k), d = P.d;
  if (part === "hand") return handWords(B);
  if (part === "medium") {
    if (P.k === "ink") return d.wet < .05 ? "Ink" : d.wet < .6 ? "Wet ink" : "Ink wash";
    if (P.k === "wash") return d.wet < .5 ? "Flat wash" : "Wet wash";
    if (P.k === "cover") return d.impasto > .3 ? "Thick paint" : "Opaque";
    if (P.k === "charcoal") return d.spread > .5 ? "Messy charcoal" : d.spread < .12 ? "Crisp charcoal" : "Charcoal";
    if (P.k === "graphite") return "Graphite " + gradeOf(d.grade);
  }
  if (part === "life") {
    if (P.k === "dry") return "Slow " + dryingSec(B) + " s";
    if (P.k === "float") return "Float " + floatSec(B) + " s";
    if (P.k === "wet") return "Quick";
    return "Instant";
  }
  if (part === "tip" && P.k === "multi") return d.count + " lines";
  // Clear Water's module has room for one word, as Smudge's others.
  if (part === "carry" && P.k === "water") return "Water";
  return c.label;
}
// The note under a swap tile (ModularWords.swapNote): a drying's time or how it dries; nothing for Quick.
const lifeNote = b => b.life.k === "halo" ? "mold " + dryingSec(b) + " s" : b.life.k === "fade" ? "fades " + fmtDial("life", "fadeIn", b.life.d.fadeIn) : b.life.k === "lemon" ? "darkens " + fmtDial("life", "dryIn", b.life.d.dryIn) : b.life.k === "float" ? (isClearWater(b) ? "clear water" : "floats " + floatSec(b) + " s") : b.life.k === "dry" ? "wet " + dryingSec(b) + " s" : b.life.k === "wet" ? "" : "dries at once";
// The choice a style weighs most for a part (the first of equals), as ModularWords.usual.
function usual(style, part) {
  const w = (MSTYLES[style] || {}).parts?.[part] || {};
  let best = null, most = 0;
  for (const ch of CHOICES[part]) if ((w[ch.k] || 0) > most) { most = w[ch.k]; best = ch.k; }
  return best;
}
const STYLE_LABEL = s => s.replace("Watercolor", "Watercolour");
// The caption's items (ModularWords.caption): the style, then what is unusual for it, in display words.
function captionItems(B) {
  const style = B.style || WILDCARD, lc = t => t.toLowerCase(), items = [STYLE_LABEL(style)], M = B.medium, md = M.d;
  if (B.life.k === "float") items.push(isClearWater(B) ? "clear water" : "float");
  const tip = choiceOf("tip", B.tip.k).label;
  if (B.tip.k !== usual(style, "tip") && lc(tip) !== lc(style)) items.push(lc(tip));
  const tag = M.k === "ink" ? (md.wet < .05 ? "ink" : md.wet < .6 ? "wet ink" : "ink wash") : M.k === "wash" ? (md.wet < .5 ? "flat wash" : "wet wash")
    : M.k === "cover" ? (md.impasto > .3 ? "thick paint" : "opaque") : M.k === "charcoal" ? (md.spread > .5 ? "messy" : md.spread < .12 ? "crisp" : "charcoal")
    : gradeOf(md.grade) + (style === "Pencil" ? "" : " graphite");
  const plain = { ink: "ink", wash: "wet wash", cover: "opaque", charcoal: "charcoal" }[M.k];
  if (tag !== lc(style) && !(M.k === usual(style, "medium") && tag === plain)) items.push(tag);
  // Paint Load in the smudge's place: "Oils, filbert, runs out".
  if (B.carry.k !== "off" && !(B.carry.k === "water" && B.life.k === "float")) items.push(runsOut(B) ? "runs out" : isStump(B) ? (combs(B) ? "comb" : "stump") : lc(choiceOf("carry", B.carry.k).label));
  if (keepsPaint(B)) items.push("keeps paint");
  const er = eraseLabel(B); if (er) items.push(lc(er));
  if (B.paper.k !== "plain" && B.paper.k !== usual(style, "paper")) items.push(lc(choiceOf("paper", B.paper.k).label));
  if (B.life.k === "dry") items.push("slow"); else if (B.life.k === "none") items.push("instant");
  return items;
}
// Erasable, said only when it isn't the medium's own (ModularWords.erase): an ink that erases, a pencil that doesn't.
function eraseLabel(B) {
  const e = B.medium.d.erase ?? 1;
  if (Math.abs(e - realErase(B.style, B.medium.k)) < .05) return "";
  return e >= .95 ? "Erasable" : e <= .05 ? "Permanent" : "Part erasable";
}
// Two caption lines of 17 characters, comma-joined while they fit (BrushCaptions.twoLines).
function captionLines(B) {
  const lines = [];
  for (const item of captionItems(B)) {
    if (item.length > 17) continue;
    if (lines.length && (lines[lines.length - 1] + ", " + item).length <= 17) lines[lines.length - 1] += ", " + item;
    else if (lines.length < 2) lines.push(item);
  }
  return lines;
}
const PAPER_WORDS = { "cold press": "cold-press paper", laid: "ribbed paper", felt: "felt paper", crumpled: "crumpled paper", canvas: "canvas", plain: "smooth paper" };
const cap1 = t => t.charAt(0).toUpperCase() + t.slice(1);
// Up to five short sentences, in the order a painter notices (ModularWords.plainWords): what it is and what it's on;
// its two strongest marks; its smudge; its drying when not Quick; how it answers pressure, tilt and speed.
function plainWords(B) {
  const T = B.tip, M = B.medium, out = [], md = M.d, td = T.d, style = B.style || WILDCARD;
  if (isClearWater(B)) return "Clear water. Taps drop clear rings, dragging combs them" + (B.carry.d.pick > .05 ? "" : " a little") + ".";
  const tipNoun = { round: "round tip", chisel: "chisel tip", flat: "flat nib", hake: "hake", split: "split nib", multi: (td.count || 3) + "-line rake", bristles: "bristle brush", filbert: "filbert" }[T.k];
  let noun;
  if (M.k === "ink") noun = md.wet < .05 ? "Ink line" : md.wet < .45 ? "Line and wash" : "Ink wash";
  else if (M.k === "wash") noun = md.wet < .5 ? "Flat wash" : "Wet wash";
  else if (M.k === "cover") noun = md.impasto > .3 ? "Thick paint" : md.cover > .8 ? (style === "Oils" ? "Oil paint" : "Gouache") : "Semi-opaque paint";
  else if (M.k === "charcoal") noun = style === "Pastels" ? (md.hold > .6 ? "Hard pastel" : "Soft pastel") : md.hold > .6 ? "Compressed charcoal" : "Vine charcoal";
  else noun = gradeOf(md.grade) + " graphite";
  const floats = B.life.k === "float", tip = T.k === "round" ? "" : ", " + tipNoun, on = floats ? " on water" : B.paper.k === "plain" ? "" : " on " + PAPER_WORDS[B.paper.k];
  out.push((floats && M.k === "ink" ? "Sumi ink" : isWater(B) ? "Clear water" : noun) + tip + (tip && on ? "," : "") + on + ".");
  const marks = [];
  const add = (v, th, words) => { if (v > th) marks.push([v - th, words]); };
  add(td.taperIn + td.taperOut, .9, "tapers to a point"); add(td.dry || 0, .3, T.k === "hake" ? "dry-brush streaks" : "dry-brush texture");
  add(td.wobble || 0, .25, "slight wobble"); add(md.pool || 0, .35, M.k === "wash" ? "dark edges" : "pools when slow");
  add(md.splatter || 0, .3, "spatters on flicks"); add(md.bloom || 0, .3, "blooms"); add(md.backrun || 0, .3, "backruns");
  add(md.bleed || 0, .35, "bleeds"); add(md.impasto || 0, .3, "raised ridges");
  if (M.k === "charcoal") add(md.spread + .3, .35, md.spread > .5 ? "dusty broken edge" : "soft speckled edge");
  add(md.offset || 0, .25, "line shifted"); add(md.chaos || 0, .25, "broken line");
  if (T.k === "split") marks.push([.5, "opens into two lines"]);
  if (T.k === "bristles") marks.push([.4, "splits into strands"]);
  if (B.paper.d.tooth > .55) marks.push([B.paper.d.tooth - .5, "picks up the texture"]);
  const strong = marks.sort((a, b) => b[0] - a[0]).slice(0, 2).map(m => m[1]);
  if (strong.length) out.push(cap1(strong.join(", ")) + ".");
  // Smudge Off says nothing (not "undefined").
  const smudge = floats ? null : { lift: "Lifts paint.", drag: "Drags wet paint.", smudge: isStump(B) ? (combs(B) ? "Combs." : "Softens.") : "Smears.", water: "Lifts paint, lets it run." }[B.carry.k];
  // Paint Load says plainly what it does, and how to load it, in the smudge's place (the oil paint spec).
  const carry = smudge ? (runsOut(B) ? "Runs out of paint, then only smudges. Rub a grey to load." : smudge) + (keepsPaint(B) ? " Keeps paint for the next stroke." : "") : null;
  if (carry) out.push(carry);
  const er = eraseLabel(B); if (er) out.push(er + ".");
  if (B.life.k === "none") out.push("Dries at once.");
  else if (floats) out.push("Floats " + floatSec(B) + " s. Taps drop rings" + (B.carry.k === "drag" || B.carry.k === "smudge" || B.carry.k === "water" ? ", dragging combs them" : "") + ".");
  else if (B.life.k === "dry") out.push("Wet for " + dryingSec(B) + " s" + ((B.life.d.takes | 0) ? ", pen lines plume in it" : "") + ".");
  const hw = handWords(B, 2);
  if (hw !== "steady") out.push(cap1(hw) + ".");
  // At most 260 characters (the app's budget): sentences past the second that don't fit are left out.
  let text = out[0];
  for (let i = 1; i < out.length; i++) { if (i >= 2 && text.length + 1 + out[i].length > 260) break; text += " " + out[i]; }
  return text;
}

// ===================================================================================================================
// Names: adjective-produce from the app's lists (oldmodel.js's freshName); a new brush is then named for what it is, a
// fun word, a describer and its part, "sloppy-inky-drag" (the app's BrushNames.described), by the app's
// names/traits.txt: the copy below, kept the same by BrushNamesChecks. From the brush and its seed, no draw from
// Random, so every roll is as before.
// ===================================================================================================================
const NAMES_TRAITS = "# A new brush's name, said by what it is, deadpan and in Title Case (the owner, 2026-10-29: \"mostly deadpan\"; \"have the\n# brush names hint at what the brush does for sure\"). Every name has its part, the word for what makes it, and most its\n# describer too. About one in five is its three words: a fun word, a describer and the part (\"Sloppy Inky Drag\"). The\n# rest are one of eight forms from [deadpan] (\"Polite Smear\", \"Regrettable Inky Hake\", \"Dusty Aunt Charcoal\", \"Inky\n# Pen Junior\", \"Smear of Mild Regret\", \"Wash for Beginners\", \"Inky Pen Situation\", \"Budget Dusty Charcoal\"). About one\n# in six has a made-up word: the describer's stem or the part with an ending from [deadpan]'s nonsense line\n# (\"Wobblefuzz\", \"Smudgekins\"). A name that won't wrap to two lines of a card (12 letters a line) takes the next form,\n# then its words, then just the describer and part.\n# Each section below is a list of lines: something a brush can have (as brush codes name it), a colon, then its words,\n# short and lowercase, one word each (-y and -ish welcome). A brush takes the first line it has, top to bottom, and one\n# of its words picked by its seed, so two alike brushes still differ. Drying and smudges count when they aren't the\n# style's usual, a paper only with a strong grain. \"any\" is every brush. A line starting with # is a note; a word in\n# square brackets starts a section.\n\n[part]\n# The part that makes it, most telling first: special tips, then smudges, then tips, then the medium.\ntip.stamp: stamp\ntip.spiro: spiro loop\ntip.handhatch: hatch\ntip.hatch: hatch\nmedium.scratch: scraper blade\nmedium.crayon: crayon\ntip.stipple: dots stipple\nmedium.wax: wax candle\nmedium.resist: mask\nmedium.tone: tone\nmedium.screen: print\nlife.float: float\nlife.halo: mold\ncarry.water: water\ncarry.stayin: filler\ncarry.comb: comb\ncarry.stump: stump\ncarry.lift: lift\ncarry.drag: drag\ncarry.smudge: smear smudge\ntip.bristles: bristle\ntip.split: split\ntip.multi: rake\ntip.hake: hake\ntip.chisel: chisel\ntip.flat: nib\ntip.filbert: filbert\nmedium.ink: pen liner\nmedium.wash: wash\nmedium.cover: paint\nmedium.charcoal: charcoal\nmedium.graphite: pencil\nany: brush\n\n[describer]\n# What it does, most telling first: its mischief, its drying and marks, then its paper, then its medium.\nblend.invert: flipped topsy\nblend.behind: tucked shy\nblend.white: chalky pale ghostly\nlife.fade: fading ghostly\nlife.lemon: secret sour\ncarry.pload: oily loaded\ntip.flick: flicky whippy\nmedium.fling: flingy splatty\nmedium.dust: dusty sooty\nmedium.bloom: bloomy blossomy\nmedium.backrun: rippled frilly\nmedium.bleed: bleedy feathery\nlife.drips: drippy dribbly\nlife.runs: runny trickly\nlife.pool: puddly pooling\nlife.sink: silty sandy\nlife.blend: rocky swirly\nlife.wetpaint: wettish dewy\nlife.dry: damp dampish soggy\nmedium.splatter: spattery splotchy\nmedium.impasto: lumpy ridged thick\nmedium.pool: puddly blotchy\ntip.dry: scratchy streaky\npaper.cold press: grainy toothy\npaper.laid: ribbed ribby\npaper.felt: felty fuzzy\npaper.crumpled: crinkly creased\npaper.canvas: woven weavy\npaper.watercolour: cottony\npaper.rough: bumpy lumpy\npaper.unryu: wispy fibrous\npaper.library: bookish\npaper.kraft: brownish papery\npaper.old buff: oldish faded\npaper.blue fibre: fibrous flecked\npaper.fine speck: flecky specked\ntip.wobble: wobbly shaky\ntip.taper: pointy tapered\nmedium.crayon: waxy crayony\nmedium.wax: waxy\nmedium.resist: masky rubbery\nmedium.ink: inky inkish\nmedium.wash: washy watery\nmedium.cover: painty creamy\nmedium.charcoal: sooty powdery chalky\nmedium.graphite: greyish leady\nmedium.tone: dotty\nany: plain simple\n\n[fun]\n# A word for its mood, mostly so two names don't clash: playful, wet, rough, thick, soft, then neat.\nlife.fade: sly impish wistful shy fleeting coy\nlife.lemon: sly sneaky secret coy wily cagey\ntip.flick: giddy cheeky perky zippy frisky jolly\nmedium.fling: giddy zany rowdy wild madcap frisky\ntip.spiro: giddy loopy dizzy whirly jolly zany\ntip.stamp: cheeky perky chirpy bouncy jolly plucky\nlife.halo: creepy eerie spooky weird furtive odd\nblend.invert: sly topsy contrary rogue odd wily\nmedium.wax: sly secret sneaky hidden coy cagey\nmedium.resist: careful patient polite tidy shy prim\nlife.float: dreamy drifty floaty calm serene lazy\nlife.wetpaint: sloppy loose juicy lush gooey slick\nlife.dry: loose dreamy lazy patient slow mellow\nmedium.bloom: dreamy loose airy lush tender hazy\nmedium.wash: loose mellow breezy easy calm fluid gentle wistful lazy carefree\nlife.drips: sloppy messy leaky soggy clumsy gooey\nlife.runs: sloppy loose leaky restless runaway wayward\nmedium.dust: gruff rugged grubby rumpled crusty grimy\ntip.dry: scratchy gruff wiry gritty raspy rough thirsty parched crusty grainy\ntip.bristles: shaggy scruffy unkempt tousled wiry wild\nmedium.charcoal: gruff smoky grubby moody brooding rugged grumpy stormy dusky gloomy\nmedium.scratch: gruff edgy feisty prickly spiky sharp\nmedium.crayon: chunky sunny merry cheery bold plucky\nmedium.impasto: hearty burly chunky stout beefy robust\nmedium.cover: hearty bold stout sturdy brash plush jolly sunny merry earnest\ncarry.drag: sloppy bold brash pushy burly hasty\ncarry.pload: hearty plump greasy rich lavish slick\ncarry.smudge: hazy gentle dreamy soft fuzzy woolly\ncarry.stump: hazy gentle velvet soft mellow tender\ncarry.lift: gentle airy light polite careful mild\ntip.hatch: tidy prim patient orderly fussy steady\ntip.handhatch: tidy patient fussy diligent busy steady\ntip.stipple: patient fussy dotty finicky busy steady\ntip.flat: prim dapper formal stately elegant poised\ntip.chisel: patient tidy crisp sharp stately keen bold brisk upright proud\nmedium.graphite: tidy humble quiet modest sober plain shy pensive bookish studious\nmedium.ink: tidy prim neat crisp keen proper dainty earnest snappy perky\nany: brisk spry jaunty nimble bright swift\n\n[deadpan]\n# Not keyed by what a brush has: the deadpan forms' words, a line each. manner: \"Polite Smear\"; relative: \"Dusty Aunt\n# Charcoal\"; edition: \"Inky Pen Junior\"; mild and concern: \"Smear of Mild Regret\"; nonsense: a made-up word's ending\n# (\"wobblefuzz\", \"smudgekins\").\nmanner: polite nervous regrettable apologetic reluctant sensible earnest suspicious hopeful modest tired puzzled sincere decent humble cautious sheepish dutiful unbothered optimistic sleepy worried confident tentative bashful\nrelative: uncle aunt cousin nephew niece neighbour grandpa grandma landlord roommate\nedition: deluxe classic junior lite original\nmild: mild some minor modest slight faint passing\nconcern: regret concern doubt worry interest suspicion unease bother\nnonsense: fuzz monger whistle wump kins wick doodle ery\n\n# ---- A new notebook's title ----\n# Not a brush's: [title] names new notebooks, deadpan, in fourteen forms: \"Several Opinions About Soup\", \"A Brief Inventory\n# of Lint\", \"Notes Toward a Better Spoon\", \"The Pamphlet of Mild Concerns\", \"Doodles for a Goose\", \"Mistakes Volume\n# Three\", \"Probably Fine\", \"The Lukewarm Pamphlet\", \"Spoons I Have Known\", \"In Defence of Fog\", \"Advice from a Puffin\",\n# \"Minutes of the Pigeon Committee\", \"Field Notes on Scones\" and \"Letters to a Walrus\". About one in six has a made-up word instead, a lear word with a nonsense ending:\n# \"The Flimwhistle Papers\", \"Doodles for a Pobblewick\", \"Wobblefuzz and Other Concerns\". A title too long for a shelf\n# tile takes a shorter form. Only new notebooks: a name already given never changes.\n\n[title]\n# Its words, a line each. things: plural, or a heap of; thing and creature follow \"a\".\nthings: lint spoons crumbs socks puddles pigeons biscuits receipts buttons noodles pebbles mittens teaspoons potatoes soup toast gravel fog weather drizzle doorknobs envelopes pickles turnips sandwiches gulls kettles hats lunches parsnips napkins cushions clouds raisins sweaters crackers onions marbles lampshades staplers shoelaces paperclips thimbles scones hedges icebergs mussels rhubarb porridge\nthing: spoon kettle sandwich chair bench sock hat potato toaster mailbox teapot biscuit puddle tuesday doorknob lunchbox cardigan mitten bucket ladder pencil sofa parsnip thimble teacup napkin scone spatula cushion doormat saucepan stapler shoelace\ncreature: goose pigeon moth badger hedgehog walrus heron sheep duck newt snail puffin moose crab gull capelin lobster beetle pony hen squid caribou toad ferret llama pelican plover vole wombat mole lemming seal trout cod raven robin\nadj: mild minor modest lukewarm damp polite vague reasonable unofficial ordinary sensible tepid small quiet humble careful tentative soggy beige decent brief tidy lumpy plain overdue borrowed ongoing sincere\nbook: pamphlet ledger logbook binder handbook almanac folder report bulletin register leaflet dossier\nverdict: fine okay alright harmless edible waterproof sturdy accurate legal adequate enough upright dry fireproof finished correct square\nnumber: two three four five six seven eight nine eleven twelve\nlear: flim wobble quib splotch bumble pobble jumble quangle fizzle plonk gribble dimble wimble crumple flonk\n";
// Its sections (BrushNames.traits): "[fun]" starts one (lines before any go in part); every other line is a key, a
// colon and its words, in the file's order.
function traitSections(text) {
  const out = new Map(); let sec = "part";
  for (const raw of text.replace(/\uFEFF/g, "").split("\n")) {
    const line = raw.replace(/\t/g, " ").trim();
    if (!line || line.startsWith("#")) continue;
    if (/^\[[a-z]+\]$/.test(line)) { sec = line.slice(1, -1); continue; }
    const cut = line.indexOf(":");
    if (cut <= 0) continue;
    const key = line.slice(0, cut).trim().replace(/\s+/g, " "), words = [];
    for (const w of line.slice(cut + 1).trim().split(/\s+/)) if (/^[a-z]+$/.test(w) && !words.includes(w)) words.push(w);
    if (!out.has(sec)) out.set(sec, new Map());
    const m = out.get(sec);
    if (key && words.length && !m.has(key)) m.set(key, words);
  }
  for (const [k, m] of [...out]) if (!m.size) out.delete(k);
  return out;
}
const TRAITS = traitSections(NAMES_TRAITS), TRAIT_WORDS = new Set([...TRAITS.values()].flatMap(m => [...m.values()].flat()));
// TRAIT_NAMES.on false: names stay adjective-produce (the checks, to see every roll is as before).
const TRAIT_NAMES = { on: true }, TRAIT_SALT = 0x7a175, KIN_SALT = 0x4b1e5, STRONG_TOOTH = .6;
const nameFamily = name => name.slice(Math.max(name.lastIndexOf("-"), name.lastIndexOf(" ")) + 1).trim().toLowerCase();
// The choice a style rolls most (ModularWords.usual, Anything goes' too).
function nameUsual(style, part) {
  const st = MSTYLES[style] || MSTYLES[WILDCARD];
  if (!st.wild) return usual(style, part);
  let best = null, most = 0;
  for (const [k, w] of Object.entries(WILD_PARTS[part])) if (w > most) { most = w; best = k; }
  return best;
}
// The marks a name can say and from what strength, "This brush"'s (BrushNames.MARKS).
const TRAIT_MARKS = [["medium", "bloom", .3], ["medium", "backrun", .3], ["medium", "bleed", .35], ["medium", "splatter", .3], ["medium", "impasto", .3],
  ["medium", "pool", .35], ["medium", "fling", .05], ["tip", "flick", .05], ["tip", "dry", .3], ["tip", "wobble", .25]];
// What a name can say about B, as traits.txt keys it (BrushNames.traitKeys): "any"; its medium and any tip but Round;
// a smudge or drying only when it isn't the style's usual, a paper only with a strong grain; a stump or a comb; Resist's
// wax, Crayon's scratch; Blend; gravity's dials and dust past .15; a taper; the marks.
function traitKeys(B) {
  const style = B.style || WILDCARD, keys = new Set(["any"]), T = B.tip, M = B.medium, P = B.paper, L = B.life;
  if (T.k !== "round") keys.add("tip." + T.k);
  if ((T.d.taperIn ?? 0) + (T.d.taperOut ?? 0) >= .9) keys.add("tip.taper");
  keys.add("medium." + M.k);
  if (M.k === "resist" && (M.d.erase ?? 1) < .5) keys.add("medium.wax");
  if (M.k === "crayon" && (M.d.scratch | 0) === 1) keys.add("medium.scratch");
  if (M.k !== "resist") { const bl = M.d.blend | 0; if (bl > 0) keys.add("blend." + ["darken", "white", "behind", "invert"][bl]); }
  if ((M.d.dust ?? 0) > .15) keys.add("medium.dust");
  if ((M.d.screen | 0) > 0) keys.add("medium.screen");
  for (const [part, d, at] of TRAIT_MARKS) if ((B[part].d[d] ?? 0) >= at) keys.add(part + "." + d);
  if (isStump(B)) keys.add(combs(B) ? "carry.comb" : "carry.stump");
  else if (B.carry.k !== "off" && B.carry.k !== nameUsual(style, "carry")) keys.add("carry." + B.carry.k);
  if (runsOut(B)) keys.add("carry.pload");
  if (P.k !== "plain" && P.k !== nameUsual(style, "paper") && (P.d.tooth ?? 0) >= STRONG_TOOTH) keys.add("paper." + P.k);
  if (L.k !== nameUsual(style, "life")) keys.add("life." + L.k);
  for (const k of ["runs", "drips", "pool", "sink", "blend"]) if ((L.d[k] ?? 0) > .15) keys.add("life." + k);
  return keys;
}
// Its name's words (BrushNames.words): from each section the first line B has, a word picked by the seed; the part
// first, then the describer, then the fun word, no word twice; said fun, describer, part; maybe one made up.
function nameWords(B) { const w = plainNameWords(B); return w.length === 3 ? nonsenseName(w, B.seed >>> 0) : w; }
function plainNameWords(B) {
  const has = traitKeys(B), part = namePick("part", has, B.seed, 0), describer = namePick("describer", has, B.seed, 1, part), fun = namePick("fun", has, B.seed, 2, part, describer);
  return [fun, describer, part].filter(w => w !== null);
}
// From a section, the first line B has, one of its words by the seed (hash n), the next where it echoes one of not (BrushNames.pick).
// Two words echo when they start alike, their first four letters or all of the shorter (BrushNames.echoes).
const nameEchoes = (a, b) => a !== null && b !== null && a.slice(0, Math.min(4, a.length, b.length)) === b.slice(0, Math.min(4, a.length, b.length));
function namePick(sec, has, seed, n, ...not) { const all = namePicks(sec, has, seed, n, ...not); return all.length ? all[0] : null; }
// namePick's word and the rest of its line after it, in the seed's order (BrushNames.picks).
function namePicks(sec, has, seed, n, ...not) {
  const m = TRAITS.get(sec), out = [];
  if (!m) return out;
  for (const [k, line] of m) {
    if (!has.has(k)) continue;
    const at = (hash01(((seed >>> 0) ^ TRAIT_SALT) >>> 0, n) * line.length) | 0;
    for (let i = 0; i < line.length; i++) { const w = line[(at + i) % line.length]; if (!not.some(t => nameEchoes(w, t))) out.push(w); }
    if (out.length) return out;
  }
  return out;
}
// B's name (BrushNames.name), deadpan, Title Case: about one in five its words, the rest a deadpan form (the next where
// one won't wrap to two lines of a card), then its words, its plain words, its describer and part; null with none.
const PLAIN = .2, capWord = w => w[0].toUpperCase() + w.slice(1);
function brushNameOf(B) {
  const w = nameWords(B), plain = plainNameWords(B);
  if (!w.length) return null;
  const s = ((B.seed >>> 0) ^ TRAIT_SALT) >>> 0, tries = w.length === 3 && hash01(s, 7) >= PLAIN ? deadpanForms(w, plain, s) : [];
  tries.push(nameTitled(w), nameTitled(plain));
  for (const name of tries) if (name.length <= NAME_MAX && nameTwoLines(name)) return name;
  return nameTitled(plain.length < 3 ? plain : plain.slice(1));
}
// Words with spaces, each capitalized: names are Title Case (BrushNames.titled).
const nameTitled = ws => ws.map(capWord).join(" ");
// The deadpan forms (BrushNames.deadpan), from [deadpan], the seed's first, then the next ones round: "Polite Smear",
// "Regrettable Inky Hake", "Dusty Aunt Charcoal", "Inky Pen Junior", "Smear of Mild Regret", "Wash for Beginners", "Inky
// Pen Situation", "Budget Dusty Charcoal"; each says the part; a made-up describer leaves out the forms without it; the
// two short forms only when picked.
const nameWord = (line, s, n) => line ? line[(hash01(s, n) * line.length) | 0] : null;
const nameJoin = (...ws) => ws.some(w => w === null) ? null : ws.map(w => w.startsWith(" ") ? w : capWord(w)).join("");
function deadpanForms(w, plain, s) {
  const d = TRAITS.get("deadpan"), out = [];
  if (!d) return out;
  const describer = w[1], part = w[2], manner = nameWord(d.get("manner"), s, 13), madeUp = describer !== plain[1];
  const forms = [madeUp ? null : nameJoin(manner, " ", part), nameJoin(manner, " ", describer, " ", part),
    nameJoin(describer, " ", nameWord(d.get("relative"), s, 14), " ", part), nameJoin(describer, " ", part, " ", nameWord(d.get("edition"), s, 17)),
    madeUp ? null : nameJoin(part, " of ", nameWord(d.get("mild"), s, 15), " ", nameWord(d.get("concern"), s, 16)),
    madeUp ? null : nameJoin(part, " for Beginners"), nameJoin(describer, " ", part, " Situation"), nameJoin("Budget ", describer, " ", part)];
  const at = (hash01(s, 12) * forms.length) | 0;
  for (let i = 0; i < forms.length; i++) { const k = (at + i) % forms.length; if (forms[k] !== null && (i === 0 || k !== 0 && k !== 5)) out.push(forms[k]); }
  return out;
}
// Whether a name wraps to two lines of LINE_LETTERS as a card wraps it (BrushNames.twoLines).
const LINE_LETTERS = 12;
function nameTwoLines(name) {
  const pieces = [];
  for (let word of name.trim().split(/ +/)) {
    while (word.length > LINE_LETTERS) {
      let cut = -1;
      for (let i = word.indexOf("-", 1); i > 0 && i < word.length - 1; i = word.indexOf("-", i + 1)) if (cut < 0 || i + 1 <= LINE_LETTERS) cut = i;
      if (cut < 0) break;
      pieces.push(word.slice(0, cut + 1)); word = word.slice(cut + 1);
    }
    pieces.push(word);
  }
  let lines = 0, line = -1;
  for (const p of pieces) {
    if (p.length > LINE_LETTERS) return false;
    if (line >= 0 && line + 1 + p.length <= LINE_LETTERS) { line += 1 + p.length; continue; }
    lines++; line = p.length;
  }
  return lines <= 2;
}
// Made-up words (BrushNames.nonsense), about one name in six: a -y or -ish describer's stem, else the part, with an
// ending from [deadpan]'s nonsense line ("wobblefuzz", "smudgekins"); none past 12 letters, never the word twice.
const NONSENSE = 1 / 6, NONSENSE_MOST = 12, isVowel = c => "aeiou".includes(c);
function nameStem(w) {
  const s = w.endsWith("ish") ? w.slice(0, -3) : w.endsWith("y") ? w.slice(0, -1) : null;
  if (s === null || s.length < 3) return null;
  const n = s.length, last = s[n - 1], before = s[n - 2];
  if (last === before && "bdgmnpt".includes(last)) return s.slice(0, -1);
  if (last === "l" && !isVowel(before) && before !== "l") return s.endsWith("stl") ? s.slice(0, -1) : s + "e";
  if (last === "v" || last === "k" && isVowel(before) && n >= 3 && !isVowel(s[n - 3])) return s + "e";
  return s;
}
function nameSuffixed(w, suffix) {
  const soft = isVowel(suffix[0]) || suffix[0] === "y", n = w.length;
  if (soft && w.endsWith("e")) return w.slice(0, -1) + suffix;
  if (soft && n <= 4 && n >= 3 && !isVowel(w[n - 1]) && !"wxy".includes(w[n - 1]) && isVowel(w[n - 2]) && !isVowel(w[n - 3])) return w + w[n - 1] + suffix;
  return w + suffix;
}
function nonsenseName(words, seed) {
  const s = ((seed | 0) ^ TRAIT_SALT) >>> 0, d = TRAITS.get("deadpan"), ends = d ? d.get("nonsense") : null;
  if (!ends || hash01(s, 3) >= NONSENSE) return words;
  const [fun, describer, part] = words, stem = nameStem(describer), base = stem !== null ? stem : part, at = (hash01(s, 6) * ends.length) | 0;
  for (let i = 0; i < ends.length; i++) {
    const end = ends[(at + i) % ends.length], w = nameSuffixed(base, end);
    if (w.length > NONSENSE_MOST || nameEchoes(base, end)) continue;
    return stem !== null ? [fun, w, part] : [fun, describer, w];
  }
  return words;
}
// A new brush's name said by what it is; as it is with no family part or no words.
function describedName(B, name) {
  if (!TRAIT_NAMES.on || !nameFamily(name)) return name;
  const n = brushNameOf(B);
  return n === null ? name : n;
}
// A variation's name (BrushNames.kin): kinName draws first, so every variation rolls as before; a capitalized name led by
// a word of [deadpan], [fun], [describer] or [part] keeps the rest under another word of that line; a fun word's name keeps the
// rest (its describer and part) under another word of the fun line, both from the variation's seed; an older
// trait-produce name ("drip-plum") keeps its first word under another produce; any other as kinName says.
function kinOther(list, not, seed) {
  let i = (hash01(((seed >>> 0) ^ KIN_SALT) >>> 0, 0) * list.length) | 0;
  if (list[i] === not) i = (i + 1) % list.length;
  return list[i];
}
function kinOf(name, seed) {
  const kin = kinName(name), f = nameFamily(name);
  if (!f) return kin;
  const words = name.trim().toLowerCase().split(/[- ]+/), t = name.trim();
  // A deadpan name keeps all but its first word, under another of the same line ("Polite Smear", "Nervous Smear").
  if (words.length >= 2 && t[0] !== t[0].toLowerCase())
    for (const sec of ["deadpan", "fun", "describer", "part"]) if (TRAITS.has(sec))
      for (const line of TRAITS.get(sec).values()) if (line.includes(words[0])) return capWord(kinOther(line, words[0], seed)) + t.slice(words[0].length);
  if (words.length >= 2 && TRAITS.has("fun") && !NAMES_PRODUCE.includes(f))
    for (const line of TRAITS.get("fun").values()) if (line.includes(words[0])) return kinOther(line, words[0], seed) + name.trim().toLowerCase().slice(words[0].length);
  if (words.length === 2 && words[0] && !NAMES_ADJ.includes(words[0]) && NAMES_PRODUCE.includes(f)) return words[0] + "-" + kinOther(NAMES_PRODUCE, f, seed);
  return kin;
}

// ===================================================================================================================
// Share codes. New: name.M<style><tip>_<medium>_<carry>_<paper>_<life>_<hand>.<seed>: each part its choice as one
// base-36 digit, then its choice's dials in order (a choice dial one digit, a dial two base-36 digits over its range).
// Dials added later go on the end; a code that stops early reads them as defaults. Old app codes (K = I or P) and the
// prototypes' I/P tails, ".x" pen suffix and B codes still load and draw as before.
// ===================================================================================================================
const M_STEPS = 1295;
const two36 = n => (n < 36 ? "0" : "") + n.toString(36);
function writePart(part, P) {
  let s = choiceIndex(part, P.k).toString(36), keys = choiceOf(part, P.k).dials, end = keys.length;
  while (end > 0 && ADDED.has(part + "." + keys[end - 1]) && P.d[keys[end - 1]] === dialDefault(part, P.k, keys[end - 1])) end--;
  for (const key of keys.slice(0, end)) {
    const sp = DIALS[part][key], v = P.d[key];
    if (sp.choices) { s += (v | 0).toString(36); continue; }
    const [lo, hi] = [sp.min, sp.max];
    s += two36(Math.round((clamp(v, lo, hi) - lo) / (hi - lo) * M_STEPS));
  }
  return s;
}
function readPart(part, seg) {
  if (!seg) return null;
  const ci = parseInt(seg[0], 36);
  if (!(ci >= 0 && ci < CHOICES[part].length)) return null;
  const c = CHOICES[part][ci], P = freshPart(part, c.k);
  let at = 1;
  for (const key of c.dials) {
    const sp = DIALS[part][key];
    if (sp.choices) { if (at >= seg.length) break; const o = parseInt(seg[at++], 36); if (!(o >= 0 && o < sp.choices.length)) return null; P.d[key] = o; continue; }
    if (at + 2 > seg.length) break;
    const step = parseInt(seg.slice(at, at + 2), 36); at += 2;
    if (!(step >= 0 && step <= M_STEPS)) return null;
    P.d[key] = sp.min + step / M_STEPS * (sp.max - sp.min);
  }
  return P;
}
const codeSlug = n => String(n).toLowerCase().replace(/[^a-z]+/g, "-").replace(/^-|-$/g, "") || "brush";
// A Stamp's nib (newmods.js; the app's ModularCode): its id in base 36 after the six, only for a Stamp with one, so
// every other code is as before.
const nibSeg = B => B.tip && B.tip.k === "stamp" && B.nib ? "_" + (B.nib >>> 0).toString(36) : "";
function encodeM(B) {
  return codeSlug(B.name) + ".M" + Math.max(0, STYLE_NAMES.indexOf(B.style)).toString(36) + PARTS.map(p => writePart(p, B[p])).join("_") + nibSeg(B) + "." + (B.seed >>> 0).toString(36);
}
// What makes two brushes draw alike: the parts, the seed and a Stamp's nib, not the name or style.
const brushKey = B => PARTS.map(p => writePart(p, B[p])).join("_") + nibSeg(B) + "." + (B.seed >>> 0).toString(36);
// Any code: M, B, I/P (with the prototypes' tails and ".x"). Returns { b, from } or null.
function readAnyCode(text) {
  if (text == null) return null;
  let c = javaTrim(String(text)).toLowerCase();
  const dot = c.indexOf(".") + 1;
  if (dot > 0 && dot < c.length) c = c.slice(0, dot) + c[dot].toUpperCase() + c.slice(dot + 1);
  let m = /^([a-z]+(?:-[a-z]+)*)\.M([0-9a-z])([0-9a-z_]+)\.([0-9a-z]{1,7})$/.exec(c);
  if (m) {
    const style = parseInt(m[2], 36), seed = parseInt(m[4], 36), segs = m[3].split("_");
    if (style >= STYLE_NAMES.length || seed > 0xffffffff || segs.length < PARTS.length) return null;
    const B = { name: m[1], seed, style: STYLE_NAMES[style] };
    for (let i = 0; i < PARTS.length; i++) { const P = readPart(PARTS[i], segs[i]); if (!P) return null; B[PARTS[i]] = P; }
    // A Stamp's nib: a seventh segment (one that doesn't read is no nib).
    if (B.tip.k === "stamp" && segs.length > PARTS.length && /^[0-9a-z]{1,7}$/.test(segs[PARTS.length])) { const id = parseInt(segs[PARTS.length], 36); if (id <= 0xffffffff) B.nib = id; }
    return { b: normBrush(B), from: "M" };
  }
  m = /^([a-z]+(?:-[a-z]+)*)\.B([0-9a-z])([0-9a-z]+)\.([0-9a-z]{1,7})$/.exec(c);
  if (m) { const b = readB(m); return b ? { b, from: "B" } : null; }
  // ".x": the lab v2 mockup's pen-input suffix.
  const x = /^(.*)\.x([0-2])([01])([0-9a-z]{2})([0-9a-z]{2})$/.exec(c);
  if (x) { const r = readAnyCode(x[1]); if (!r || r.from === "M" || r.from === "B") return null; applyPenSuffix(r.b, r.old, x); r.from += ".x"; return r; }
  m = /^([a-z]+(?:-[a-z]+)*)\.([IP])(\d)([0-9a-z]+)\.([0-9a-z]{1,7})$/.exec(c);
  if (!m) return null;
  const old = decode(c);
  if (old) return { b: fromOld(old), old, from: m[2] };
  return readTailed(m);
}
// I/P codes with the prototypes' appended dials (brush-experiments: ink SPREAD TILT FAST, paint COVER IMPASTO HEAD TILT
// FAST, then LIFE and WET WINDOW; marble-and-bristle: ink SWARM BRISTLES COHESION SPREAD; living-ink: 9 digits, read as still).
function readTailed(m) {
  const kind = m[2] === "I" ? "ink" : "paint", baseLen = kind === "ink" ? 24 : 20, digits = m[4];
  if (digits.length <= baseLen) return null;
  let style = +m[3];
  const hake = kind === "paint" && style === 7;
  const old = decode(m[1] + "." + m[2] + (hake ? 6 : style) + digits.slice(0, baseLen) + "." + m[5]);
  if (!old) return null;
  const tail = digits.slice(baseLen), d2 = i => parseInt(tail.slice(i, i + 2), 36) / 1295;
  const B = fromOld(old);
  if (hake) { B.style = "Hake"; }
  if (kind === "ink") {
    if (tail.length === 5 || tail.length === 8) {
      const spread = d2(0), tilt = d2(2), fast = tail[4] === "1";
      if (spread > 0) { B.medium = freshPart("medium", "charcoal"); B.medium.d.spread = spread; B.medium.d.hold = .5; }
      if (tilt > 0) { B.hand.d.ts = tilt * .6; B.hand.d.tw = tilt; B.hand.k = "custom"; }
      if (fast) B.life = freshPart("life", "none");
      if (tail.length === 8) { const L = ["none", "wet", "dry"][+tail[5]] || "wet"; B.life = freshPart("life", L); if (L === "wet") B.life.d.window = 10 * d2(6); }
    } else if (tail.length === 7) {
      if (tail[0] === "1") { const d = B.tip.d; B.tip = freshPart("tip", "bristles"); Object.assign(B.tip.d, { hairs: 6 + 58 * d2(1), cohesion: d2(3), splay: d2(5), dryness: old.dry }); }
    } else if (tail.length !== 9) return null;
  } else {
    if (tail.length !== 8 && tail.length !== 11) return null;
    const cover = d2(0), impasto = d2(2), head = tail[4] === "1", tilt = d2(5), fast = tail[7] === "1";
    if (cover > 0) { const keep = B.medium.d; B.medium = freshPart("medium", "cover"); B.medium.d.cover = cover; B.medium.d.impasto = impasto; }
    if (head || hake) { const d = B.tip.d; B.tip = switchChoice("tip", B.tip, "hake"); B.tip.d.dry = d.dry; }
    if (tilt > 0) { B.hand.d.ts = tilt; B.hand.k = "custom"; }
    if (fast) B.life = freshPart("life", "none");
    if (tail.length === 11) { const L = ["none", "wet", "dry"][+tail[8]] || "wet"; B.life = freshPart("life", L); if (L === "wet") B.life.d.window = 10 * d2(9); }
  }
  return { b: normBrush(B), old, from: m[2] + "+" };
}
function applyPenSuffix(B, old, x) {
  const to = ["size", "dark", "both"][+x[2]], tilt = parseInt(x[4], 36) / 1295, sp = parseInt(x[5], 36) / 1295 * 2 - 1, p = old ? old.pressure : B.hand.d.ps;
  B.hand.d.ps = to === "dark" ? 0 : p; B.hand.d.pd = to === "size" ? 0 : p;
  if (tilt > 0) { if (x[3] === "1") B.hand.d.tw = tilt; else B.hand.d.ts = tilt; }
  if (old && old.kind === "paint" && Math.abs(sp) > .001) { B.hand.d.ss = Math.abs(sp); B.hand.d.dir = sp > 0 ? 1 : 0; }
  B.hand.k = matchHand(B.hand); normBrush(B);
}
// An app brush (I or P, as oldmodel.js's decode gives it) as parts. Life is Drying: the app re-tapers and settles at lift.
const OLD_STYLE = { "Wild": WILDCARD, "Wildcard": WILDCARD };
function fromOld(o) {
  const B = { name: o.name, seed: o.seed >>> 0, style: OLD_STYLE[o.style] || (MSTYLES[o.style] ? o.style : WILDCARD) };
  if (o.kind === "ink") {
    B.tip = { k: o.nib, d: { aspect: o.aspect, angle: o.angle, weight: 1, taperIn: o.taperIn, taperOut: o.taperOut, dry: o.dry, wobble: o.wobble, soft: .3 } };
    B.medium = { k: "ink", d: { wet: 0, line: 1, pool: o.pool, splatter: o.splatter, bleed: 0, bloom: 0, lines: 0, offset: 0, chaos: 0 } };
    B.paper = { k: o.texture, d: { tooth: o.tooth } };
  } else {
    B.tip = { k: "round", d: { weight: 1, taperIn: 0, taperOut: 0, dry: o.streaks, wobble: 0, soft: o.soft } };
    B.medium = { k: "wash", d: { wet: o.mode === "wet" ? 1 : 0, pool: o.hollow, bleed: o.feather, bloom: o.bloom, backrun: o.backrun, line: 0 } };
    B.paper = { k: o.texture, d: { tooth: o.gran } };
  }
  B.carry = freshPart("carry", "off");
  B.life = { k: "dry", d: { drying: o.drying || 0, settle: 0, takes: 0, plume: .55 } };
  B.hand = freshPart("hand", "custom");
  B.hand.d.ps = o.pressure; B.hand.d.ss = Math.abs(o.speed || 0); B.hand.d.dir = (o.speed > 0 || (o.speed === 0 && o.thicker)) ? 1 : 0;
  B.hand.k = matchHand(B.hand);
  return normBrush(B);
}
// pen-paint-blend's B codes (and ink-and-water's 5-digit plume tail after them).
const B_TEX = ["plain", "cold press", "laid", "felt", "crumpled", "canvas"], R01 = [0, 1];
const BLEND_ORDER = [["nib", ["round", "chisel", "flat"]], ["mode", ["wet", "flat"]], ["texture", B_TEX], ["wet", R01], ["line", R01],
  ["aspect", [.14, .55]], ["angle", [20, 70]], ["taperIn", R01], ["taperOut", R01], ["pressure", R01], ["speed", [-1, 1]],
  ["grain", R01], ["bristle", R01], ["wobble", R01], ["pool", R01], ["splatter", R01], ["soft", R01], ["bleed", R01],
  ["bloom", R01], ["backrun", R01], ["drying", R01]];
const BLEND_TAIL = [["lines", PLACEMENTS], ["count", [1, 5]], ["spread", R01], ["breathe", R01], ["offset", R01], ["chaos", R01]];
const PLUME_TAIL = [["plume", R01], ["pstyle", ["drift", "vein"]], ["settle", R01]];
const BLEND_STYLES = ["Fineliner", "Brush pen", "Dip pen", "Marker", "Pencil", "Charcoal", "Pen and wash", "Wet line", "Ink wash", "Watercolor", "Gouache", "Oils", "Pastels", "Dry brush", "Wild"];
function readSpec(digits, order, into) {
  let at = 0;
  for (const [k, spec] of order) {
    if (typeof spec[0] === "string") { if (at >= digits.length) return false; const o = digits.charCodeAt(at++) - 48; if (o < 0 || o >= spec.length) return false; into[k] = spec[o]; continue; }
    if (at + 2 > digits.length) return false;
    const step = parseInt(digits.slice(at, at + 2), 36); at += 2;
    if (k === "speed" && (step === 647 || step === 648)) { into.speed = 0; into.thicker = step === 648; continue; }
    into[k] = spec[0] + step / 1295 * (spec[1] - spec[0]);
  }
  return at === digits.length;
}
function readB(m) {
  const seed = parseInt(m[4], 36), style = parseInt(m[2], 36), digits = m[3];
  if (style >= BLEND_STYLES.length || seed > 0xffffffff) return null;
  const o = { lines: "centre", count: 3, spread: .6, breathe: .8, offset: 0, chaos: 0, plume: 0, pstyle: "drift", settle: 0 };
  if (digits.length < 39 || !readSpec(digits.slice(0, 39), BLEND_ORDER, o)) return null;
  const tail = digits.slice(39);
  const blendLen = { 0: 0, 5: 0, 7: 7, 11: 11, 12: 7, 16: 11 }[tail.length];
  if (blendLen === undefined) return null;
  if (blendLen && !readSpec(tail.slice(0, blendLen), BLEND_TAIL.slice(0, blendLen === 7 ? 4 : 6), o)) return null;
  if (tail.length - blendLen === 5 && !readSpec(tail.slice(blendLen), PLUME_TAIL, o)) return null;
  const sty = BLEND_STYLES[style], B = { name: m[1], seed, style: sty === "Wild" ? WILDCARD : sty === "Pen and wash" || sty === "Wet line" ? "Ink wash" : sty };
  const tipD = { aspect: o.aspect, angle: o.angle, weight: 1, taperIn: o.taperIn, taperOut: o.taperOut, dry: o.bristle, wobble: o.wobble, soft: o.soft };
  if (o.lines === "split") B.tip = { k: "split", d: Object.assign(tipD, { gap: o.spread }) };
  else if (o.lines === "spread") B.tip = { k: "multi", d: Object.assign(tipD, { count: o.count, reach: o.spread }) };
  else B.tip = { k: o.nib, d: tipD };
  const paint = o.wet >= .999 && o.line <= .001 && o.lines === "centre" && !o.offset && !o.chaos;
  if (paint) { B.tip.k = "round"; B.medium = { k: "wash", d: { wet: o.mode === "wet" ? 1 : 0, pool: o.pool, bleed: o.bleed, bloom: o.bloom, backrun: o.backrun, line: 0 } }; }
  else B.medium = { k: "ink", d: { wet: o.wet, line: o.line, pool: o.pool, splatter: o.splatter, bleed: o.bleed, bloom: o.bloom, lines: Math.max(0, ["centre", "outline", "squiggle"].indexOf(o.lines)), offset: o.offset, chaos: o.chaos } };
  B.paper = { k: o.texture, d: { tooth: o.grain } };
  B.carry = freshPart("carry", "off");
  B.life = { k: "dry", d: { drying: o.drying, settle: o.settle, takes: o.plume > 0 ? (o.pstyle === "vein" ? 2 : 1) : 0, plume: o.plume || .55 } };
  B.hand = freshPart("hand", "custom");
  Object.assign(B.hand.d, { ps: o.pressure, ss: Math.abs(o.speed), dir: (o.speed > 0 || (o.speed === 0 && o.thicker)) ? 1 : 0, pw: o.lines === "split" || o.lines === "spread" ? o.breathe : 0 });
  B.hand.k = matchHand(B.hand);
  return normBrush(B);
}

// ---------- the built-ins: the app's four tools, as old codes, so they draw exactly as in the app today ----------
const BUILTIN_CODES = [
  ["Ink", "Fineliner", "steady round line"], ["Fude", "Brush pen", "swells and tapers"], ["Wash", "Gouache", "flat, even tone"], ["Watercolor", "Watercolor", "soft blooms"]];
function builtins() {
  return BUILT.map((o, i) => { const b = fromOld(o); b.name = o.name; b.builtin = true; b.blurb = BUILTIN_CODES[i][2]; return b; });
}
