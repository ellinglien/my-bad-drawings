// ===================================================================================================================
// Brush Lab Dials: the tiles, made from the lab's lists (parts.js CHOICES and DIALS, and what newmods.js appends): one
// tile a choice, sweeping its Basic slider, and one a dial, sweeping it from low to high. A dial several choices share
// under one label is one tile, drawn on the first of them (or the choice SETUP names). No DOM: the page and the
// check (check.cjs) both use it. Words follow docs/TEXT-STYLE.md: labels are the lab's; a choice says its blurb.
// ===================================================================================================================
const DEMO_SEED = 20261015;
// The brush every tile starts from: a round tip, dry ink, no smudge, smooth paper, Quick drying (the app's defaults),
// a hand that widens a little with pressure.
const DEMO_BASE = { tip: ["round"], medium: ["ink"], carry: ["off"], paper: ["plain"], life: ["wet"], hand: ["steady", { ps: .5 }] };
const WET_WASH = ["wash", { wet: .9, pool: .3, bloom: .3 }];
// Each part's tiles: Studio's wave for the part (render.js studioSample's roles) and what they're drawn with.
const PART_SETUP = {
  tip: { role: "tip" },
  medium: { role: "medium" },
  carry: { role: "carry", shade: .3, base: { medium: ["wash", { wet: .9 }], life: ["none"] } },
  paper: { role: "paper", base: { tip: ["round", { weight: 1.5 }], medium: ["graphite", { grade: .8 }] } },
  life: { role: "life", shade: .55, base: { medium: WET_WASH, paper: ["cold press", { tooth: .5 }] } },
  hand: { role: "hand" }
};
// Where a tile needs more than its part's setup, by "part.key" for a dial (or "part.key.choice" for one choice's), or
// "part:choice" for a choice. on: the choice a dial is drawn on. after: shown over time after lift (times in ms).
// at: a share of the stroke, the pen still down. cross: a pen line into the wet wash. rub: the eraser across it.
const AFTER = [0, 500, 1000, 2000, 4000, 8000];
const SLOW = { medium: ["wash", { wet: .95, pool: .3, bloom: .3 }], paper: ["cold press", { tooth: .6 }] };
// Float's tiles in ink: a wash's drops fill the tile.
const FLOAT = { medium: ["ink"], paper: ["plain"] };
const SCREENED = v => ({ medium: ["ink", { screen: v }], tip: ["round", { weight: 1.5 }] });
const SETUP = {
  "tip.soft": { base: { medium: WET_WASH } },
  "tip.flick": { pace: "fast" },
  "tip.dry.hake": { base: { medium: ["wash", { wet: .3 }] } },
  "tip.headTurn.hake": { base: { medium: ["wash", { wet: .3 }] } },
  "tip.hold": { rest: 900 },
  "tip.inkFade": { base: { tip: ["stamp", { spacing: .9 }] } },
  "tip.petals": { base: { tip: ["spiro", { curve: 2 }] } },
  "tip.ratio": { base: { tip: ["spiro", { curve: 3 }] } },
  "tip.phase": { base: { tip: ["spiro", { curve: 3 }] } },
  "tip.fades": { base: { tip: ["spiro", { curve: 4 }] } },
  "tip.mode": { base: { tip: ["spiro", { curve: 2 }] } },
  "tip:hake": { base: { medium: ["wash", { wet: .3 }] } },
  "tip:split": { base: { hand: ["steady", { ps: .5, pw: .6 }] } },
  "tip:multi": { base: { hand: ["steady", { ps: .5, pw: .5 }] } },
  "tip:stipple": { base: { hand: ["steady", { ps: .5 }] } },
  "tip:hatch": { base: { hand: ["steady", { ps: .9 }] } },
  "tip.layers": { base: { hand: ["steady", { ps: .9 }] } },
  "medium.line": { base: { medium: ["ink", { wet: .6 }] } },
  "medium.line.wash": { base: { medium: ["wash", { wet: .9, line: 0 }] } },
  "medium.pool": { base: { medium: ["ink", { wet: .3 }] } },
  "medium.splatter": { pace: "fast" },
  "medium.bleed": { on: "wash", base: { medium: ["wash", { wet: .9 }] } },
  "medium.bloom": { on: "wash", base: { medium: ["wash", { wet: .9, bloom: 0 }] } },
  "medium.lines": { base: { medium: ["ink", { wet: .6 }] } },
  "medium.offset": { base: { medium: ["ink", { wet: .6 }] } },
  "medium.chaos": { base: { medium: ["ink", { wet: .6 }] } },
  "medium.erase": { rub: true, base: { tip: ["round", { weight: 1.5 }] } },
  "medium.blend": { base: { tip: ["round", { weight: 1.5 }] } },
  "medium.screen": { base: SCREENED(0) },
  "medium.cell": { base: SCREENED(1) },
  "medium.sangle": { base: SCREENED(2) },
  "medium.mis": { base: SCREENED(1) },
  "medium.gain": { base: SCREENED(1) },
  "medium.uneven": { base: SCREENED(1) },
  "medium.fling": { pace: "stop" },
  "medium.cover": { shade: .25 }, "medium.impasto": { shade: .25 }, "medium:cover": { shade: .25 },
  "medium:wash": { base: { tip: ["round", { soft: .3 }] } },
  "medium:resist": { rub: true },
  "medium.hold": { base: { paper: ["cold press", { tooth: .5 }] } },
  "medium.spread": { base: { paper: ["cold press", { tooth: .5 }] } },
  "medium:charcoal": { base: { paper: ["cold press", { tooth: .5 }] } },
  "medium:graphite": { base: { paper: ["cold press", { tooth: .5 }] } },
  "medium:crayon": { base: { paper: ["cold press", { tooth: .6 }] } },
  "medium.wax": { base: { paper: ["cold press", { tooth: .6 }] } },
  "medium.scratch": { base: { paper: ["cold press", { tooth: .6 }] } },
  "carry.lay": { on: "drag" },
  "carry.load": { on: "smudge" },
  "carry.keep": { strokes: 2 },
  "carry.pload": { base: { carry: ["drag", { load: 1 }] }, shade: .6 },
  "carry.pmin": { base: { carry: ["drag", { load: 1, pload: .3 }] }, shade: .6 },
  "carry.flood": { box: true, shade: .5 },
  "carry:stayin": { box: true, shade: .5 },
  "life.window": { at: .8, base: { medium: ["wash", { wet: .9, pool: .5, bloom: .5 }] } },
  "life.drying": { after: AFTER, base: SLOW },
  "life.settle": { after: AFTER, base: Object.assign({}, SLOW, { life: ["dry", { drying: .4 }] }) },
  "life.takes": { after: AFTER, cross: true, base: { medium: ["wash", { wet: .95 }], life: ["dry", { drying: .5, settle: .3, plume: .7 }] } },
  "life.plume": { after: AFTER, cross: true, base: { medium: ["wash", { wet: .95 }], life: ["dry", { drying: .5, settle: .3, takes: 1 }] } },
  "life.drying.float": { after: [0, 1000, 2000, 4000, 8000, 12000], drops: true, base: FLOAT },
  "life.spread": { drops: true, base: FLOAT },
  "life.drift": { after: [0, 1000, 2000, 4000, 8000], drops: true, tilt: true, base: FLOAT },
  "life.reach": { after: [0, 500, 1000, 2000, 4000], base: { life: ["halo", { drying: .4 }] } },
  "life.growth": { after: [0, 500, 1000, 2000, 4000], base: { life: ["halo", { drying: .4 }] } },
  "life.fadeIn": { after: [0, 5000, 10000, 20000, 40000, 80000], base: { medium: ["ink"], paper: ["plain"] } },
  "life.dryIn": { after: [0, 1000, 2000, 4000, 8000], base: { medium: ["ink"], paper: ["plain"] } },
  "life:dry": { base: { life: ["dry", { drying: .4, settle: .7 }] } },
  "life:float": { drops: true, base: FLOAT },
  "life:fade": { base: { medium: ["ink"], paper: ["plain"], life: ["fade", { fadeIn: 10 }] } },
  "life:lemon": { base: { medium: ["ink"], paper: ["plain"] } },
  "hand.pw": { base: { tip: ["split"] } },
  "hand:steady": { still: true, say: "Pressure, tilt and speed change nothing." }, "hand:custom": { still: true, say: "Any mix of the dials below." },
  "hand:nib": { base: { tip: ["chisel"] } }, "hand:quick": { base: { tip: ["chisel"] } },
  "hand.tw": { base: { tip: ["chisel"] } },
  "hand.sw": { base: { tip: ["chisel"] } },
  "hand.pd": { base: { tip: ["round", { weight: 1.3 }] } },
  "hand.td": { base: { tip: ["round", { weight: 1.3 }] } },
  "hand.sd": { base: { tip: ["round", { weight: 1.3 }] } },
  "hand.gain": { base: { hand: ["brush"] } },
  "hand.dir": { base: { hand: ["steady", { ss: 1 }] } },
  "hand.trange": { base: { hand: ["steady", { ts: 1 }] } },
  "hand.pdrive": { base: { tip: ["spiro"], hand: ["steady", { pdrive: 0 }] } },
  "hand.sdrive": { base: { tip: ["spiro"] } },
  "hand.tdrive": { base: { tip: ["spiro"] } },
  "hand.shift": { strokes: 6 },
  "hand.sorder": { strokes: 8, base: { hand: ["steady", { shift: 3 }] } },
  "hand.sgreys": { strokes: 8, base: { hand: ["steady", { shift: 3 }] } },
  "hand.grad": { base: { tip: ["round", { weight: 1.5 }], hand: ["steady", { glen: 3 }] } },
  "hand.gto": { base: { tip: ["round", { weight: 1.5 }], hand: ["steady", { grad: 2, glen: 4 }] } },
  "hand.glen": { base: { tip: ["round", { weight: 1.5 }], hand: ["steady", { grad: 1 }] } },
  "hand.tshade": { base: { medium: ["graphite", { grade: .7 }], paper: ["cold press", { tooth: .5 }] } }
};
// What each dial does, in plain words (by "part.key", or "part.key.choice" for one choice's). The lab's dials carry
// labels and ranges only; these are the old page's words, kept to TEXT-STYLE (no "you", no narrating the pen).
const DIAL_SAY = {
  "tip.weight": "The brush's own size, smaller or bigger than the size in use.",
  "tip.taperIn": "The start of the line, blunt or from a fine point.",
  "tip.taperOut": "The end of the line, blunt or thinning to a point.",
  "tip.dry": "Scratchy gaps, as from a brush running out of ink.",
  "tip.dry.hake": "Pale streaks along the ribbon where the hairs run dry.",
  "tip.wobble": "The edge wavers, as in a shaky hand.",
  "tip.soft": "Crisp edge to soft. Paint and wet ink only.",
  "tip.flick": "A quick lift carries the line on past its end, thinning and running dry.",
  "tip.aspect": "A narrow edge to a broad one.",
  "tip.angle": "The angle the edge is held at, which decides where lines go thick or thin.",
  "tip.headTurn.hake": "Stroke turns the head with the line. Page holds one angle, so the ribbon thins on the turns.",
  "tip.gap": "How far the two tines part.",
  "tip.count": "Two to five lines.",
  "tip.reach": "How far apart the lines run.",
  "tip.hairs": "How many hairs.",
  "tip.splay": "How far the hairs fan out.",
  "tip.cohesion": "From loose strands to hairs that hold together.",
  "tip.dryness": "How soon the hairs run dry and skip.",
  "tip.dot": "Fine dots to coarse ones.",
  "tip.scatter": "How far the dots stray from the line.",
  "tip.jitter": "Dots all one size, or of many sizes.",
  "tip.hold": "Dots keep landing on a pause, as from a spray can.",
  "tip.pitch": "Tight lines to open ones.",
  "tip.lineW": "Hairline hatching to bold.",
  "tip.hangle": "The angle the lines run at.",
  "tip.headTurn.hatch": "Stroke turns the lines with the stroke. Page keeps one angle.",
  "tip.layers": "How many ways the lines cross under a firm press.",
  "tip.spacing": "Close prints make a trail. Far apart, separate prints.",
  "tip.headTurn.stamp": "On turns each print with the line. Off keeps it upright.",
  "tip.inkFade": "Each print a little lighter, as an inked stamp runs dry.",
  "tip.mode": "Loops along the line, or round a ring, an oval or a bar.",
  "tip.curve": "The figure: the toy's own, a rose, a swing or a coil.",
  "tip.loops": "How tightly the loops pack along the line.",
  "tip.teeth": "The ring's size, in teeth.",
  "tip.wheel": "The wheel's teeth: a different figure for each.",
  "tip.hole": "How near the rim the pen sits. Further out, wider loops.",
  "tip.petals": "The rose's petals.",
  "tip.ratio": "How the two swings beat against each other.",
  "tip.phase": "Where the second swing starts.",
  "tip.fades": "How many turns before the swing dies away.",
  "medium.wet": "A dry line loosens into a wash.",
  "medium.line": "How much crisp line stays inside the wash. Needs some Wet.",
  "medium.line.wash": "An ink line through the wash.",
  "medium.pool": "Ink pools darker where the line slows.",
  "medium.pool.wash": "Pigment gathers at the edges and leaves the middle pale.",
  "medium.splatter": "Drops flung off a fast line.",
  "medium.bleed": "Feathering out from a wet edge.",
  "medium.bloom": "Light and dark blotches as the paint dries.",
  "medium.backrun": "Pale, frilled blooms where water creeps back into drying paint.",
  "medium.lines": "Where the line sits in its wash: down the middle, along both edges or wandering.",
  "medium.offset": "The line sits off its wash, like an off-register print.",
  "medium.chaos": "The line breaks up and jumps about.",
  "medium.erase": "How much the eraser can take. Here the eraser runs down the middle.",
  "medium.blend": "Darkens, lays white, slips behind marks or inverts them.",
  "medium.screen": "The fill printed as dots, lines or another pattern.",
  "medium.cell": "The pattern's size, fine to coarse.",
  "medium.sangle": "The angle the pattern is printed at.",
  "medium.mis": "A second print, shifted, as from a press out of register.",
  "medium.gain": "Dots swell and run together, as ink soaks in.",
  "medium.uneven": "Patches where the press ran light.",
  "medium.fling": "A hard stop throws drops ahead of the line.",
  "medium.cover": "Thin paint to paint that hides what's under it.",
  "medium.impasto": "Thick paint that stands up in ridges, lit from the upper left.",
  "medium.spread": "A crisp edge to a smudgy, dusty one.",
  "medium.hold": "Vine charcoal, pale and grainy, to compressed, dark and solid.",
  "medium.grade": "Pencil grades, 6H to 8B.",
  "medium.pattern": "The screentone's pattern.",
  "medium.ttone": "A light tone to a dark one.",
  "medium.wax": "Chalky on the grain's peaks to waxy and solid.",
  "medium.scratch": "On scrapes back to white with a thin blade.",
  "carry.pick": "How much of the paint already there it takes up.",
  "carry.lay": "How soon it lays down what it took up. More gives a darker smear just past a line and a shorter tail.",
  "carry.load": "How much of its own paint goes down with the smear. None is a clean finger or stump.",
  "carry.keep": "What it took up stays on the brush for the next stroke, a dirty brush.",
  "carry.pload": "How much paint a full brush holds before it runs out. Off never runs out.",
  "carry.pmin": "How full each stroke starts.",
  "carry.soften": "Softens like a blending stump rather than pushing paint.",
  "carry.flood": "How far the paint floods on inside the lines.",
  "paper.tooth": "Smooth to rough: breaks up the edge of a line and brings out granulation.",
  "life.window": "How far behind the nib the mark stays wet. Shown with the pen still down.",
  "life.drying": "How long the wash stays wet after lift.",
  "life.settle": "How much the wash keeps flowing and settling into the grain until it's dry.",
  "life.takes": "A pen line drawn into the wet wash spreads, drifting or in veins.",
  "life.plume": "How far that line spreads into the wash.",
  "life.drying.float": "How long the paint floats before it prints onto the paper.",
  "life.spread": "How far each drop spreads, pushing older paint outward.",
  "life.drift": "How fast floating paint drifts down a tilted page.",
  "life.reach": "How far the veins reach toward nearby marks.",
  "life.growth": "How thick the veins grow.",
  "life.fadeIn": "How long until the line has faded away.",
  "life.dryIn": "How long the faint trace takes to darken in.",
  "life.stays": "How long it stays wet in the app. Drawn here as Instant.",
  "hand.ps": "Bigger with a firmer press.",
  "hand.pd": "Darker with a firmer press.",
  "hand.pw": "A firm press opens a shaped nib: tines part, lines spread.",
  "hand.ts": "Wider laid low.",
  "hand.td": "Lighter laid low.",
  "hand.tw": "The nib turns to follow the pen's lean.",
  "hand.ss": "Thinner when fast, or thicker with Fast Strokes.",
  "hand.sd": "Lighter when fast.",
  "hand.sw": "The nib turns to follow a fast line.",
  "hand.gain": "How strongly pressure, tilt and speed act, all at once.",
  "hand.dir": "Whether fast lines come out thinner or thicker, once speed changes size.",
  "hand.trange": "How much tilt can widen the mark. Wide is a fine point upright and nearly five times that laid low.",
  "hand.pdrive": "What pressure changes on a Spiro.",
  "hand.sdrive": "What speed changes on a Spiro.",
  "hand.tdrive": "What tilt changes on a Spiro.",
  "hand.shift": "Each stroke in another grey: at random, from a shuffled set or in a pattern.",
  "hand.sorder": "A pattern of greys in order, or there and back.",
  "hand.sgreys": "The toolbar's four greys, or any of the sixteen.",
  "hand.grad": "The grey changes along each stroke: fading out, to another grey or round the set.",
  "hand.gto": "The grey a To Grey stroke ends on.",
  "hand.glen": "How far the grey takes to change.",
  "hand.tshade": "Laid low, the side of the nib: a wider, lighter, softer line."
};
// Papers and Control presets carry no blurb in the lab: these are the old page's, and handWords for Control's.
const PAPER_SAY = { plain: "No grain until Texture goes up.", "cold press": "Watercolour paper: an even, pebbly grain.",
  laid: "The fine parallel lines of laid writing paper.", felt: "An irregular felt grain, softer than cold press.",
  crumpled: "A sheet crumpled and flattened out again.", canvas: "The over-and-under weave of canvas." };
// The order the old page showed the parts in (the app's BrushPart.SHOWN), with a line for each part's tiles.
const DEMO_PARTS = ["tip", "medium", "paper", "life", "hand", "carry"];
const PART_INTRO = {
  paper: "Every paper has one dial, Texture. More breaks up the edge of a line and brings out granulation in paint.",
  life: "Each choice plays the stroke at drawing speed, then carries on after lift until nothing moves. The bar under it is the clock. Dials that act after lift show it again at each setting.",
  hand: "Each Control tile draws one stroke that starts light, upright and slow and ends firm, laid low and fast.",
  carry: "Each tile paints over a grey band and a darker line, so there's something to pick up."
};

// ---------- brushes ----------
// A brush from parts: { part: [choice, {dial: value}] }, every part at least DEMO_BASE's.
function demoBrush(parts) {
  const B = { name: "dial-demo", seed: DEMO_SEED };
  for (const p of PARTS) {
    const [k, d] = parts[p] || DEMO_BASE[p];
    B[p] = p === "medium" ? switchChoice("medium", null, k) : freshPart(p, k);
    if (d) Object.assign(B[p].d, d);
  }
  return normBrush(B);
}
const capital = t => t.charAt(0).toUpperCase() + t.slice(1);
const sayOf = (part, key, k) => DIAL_SAY[part + "." + key + "." + k] || DIAL_SAY[part + "." + key] || "";
const setupOf = (part, key, k) => Object.assign({}, SETUP[part + "." + key], SETUP[part + "." + key + "." + k]);
function valueName(part, k, key, v) {
  const s = DIALS[part][key];
  return s.choices ? optLabel(part, k, key, v | 0) : fmtDial(part, key, v);
}
function sweepValues(part, k, key, n) {
  const s = DIALS[part][key];
  if (s.choices) return (optOf(part, k, key)).map((_, i) => i);
  const [lo, hi] = rangeOf(part, k, key);
  if (s.int && hi - lo + 1 <= n) return Array.from({ length: hi - lo + 1 }, (_, i) => lo + i);
  return Array.from({ length: n }, (_, i) => { const v = lo + (hi - lo) * i / (n - 1); return s.int ? Math.round(v) : v; });
}
const optOf = (part, k, key) => (choiceOf(part, k).opts || {})[key] || DIALS[part][key].choices;

// ---------- the tiles ----------
// A tile: { id, part, k, key, title, say, also, sweep, values, names, ends, setup, jobs(i) } where sweep is "range"
// (low to high), "options" (each in turn), "time" (the stroke as it happens and after), "after" (each setting over time
// after lift) or "still".
function demoTiles() {
  const tiles = [];
  for (const part of DEMO_PARTS) {
    const P = PART_SETUP[part];
    for (const c of CHOICES[part]) tiles.push(choiceTile(part, c, P));
    const seen = new Map();
    for (const c of CHOICES[part]) for (const key of c.dials) {
      if (DIALS[part][key].hide) continue;
      const label = dialLabel(part, c.k, key), id = key + "|" + label;
      if (!seen.has(id)) seen.set(id, { key, label, on: [] });
      seen.get(id).on.push(c);
    }
    for (const { key, label, on } of seen.values()) tiles.push(dialTile(part, key, label, on, P));
  }
  tiles.forEach((t, i) => { t.id = i; });
  return tiles;
}
function tileBrush(part, k, P, S) {
  const parts = Object.assign({}, DEMO_BASE, P.base, S.base), mine = parts[part];
  parts[part] = [k, mine && mine[0] === k ? mine[1] : null];
  return demoBrush(parts);
}
function choiceTile(part, c, P) {
  const S = Object.assign({}, SETUP[part + ":" + c.k]);
  const B0 = tileBrush(part, c.k, P, S);
  if (part === "hand") B0.hand = normPart("hand", freshPart("hand", c.k));
  const life = part === "life" && c.k !== "none" && c.k !== "wetpaint";
  const t = { part, k: c.k, key: null, title: c.label, choice: true, S, P, B0,
    say: S.say || c.blurb || (part === "paper" ? PAPER_SAY[c.k] || capital(PAPER_WORDS[c.k] || c.label) + "." : part === "hand" ? capital(handWords(B0)) + "." : "") };
  if (part === "life") { t.sweep = life ? "time" : "still"; t.values = [null]; t.names = [""]; }
  else if (c.macro && !S.still) {
    t.dialKey = c.macro; t.sweep = "range"; t.values = sweepValues(part, c.k, c.macro, 9);
    t.names = t.values.map(v => valueName(part, c.k, c.macro, v)); t.ends = c.words || [t.names[0], t.names[t.names.length - 1]];
    t.sub = "Basic slider: " + dialLabel(part, c.k, c.macro);
  } else { t.sweep = "still"; t.values = [null]; t.names = [""]; }
  return t;
}
function dialTile(part, key, label, on, P) {
  // Drawn on the choice the setup names, or whose part the setup gives, else the first that has the dial.
  const S0 = SETUP[part + "." + key] || {}, want = S0.on || (S0.base && S0.base[part] && S0.base[part][0]);
  const c = on.find(x => x.k === want) || on[0], S = setupOf(part, key, c.k);
  const B0 = tileBrush(part, c.k, P, S);
  // A Control dial's tile: a steady hand with only its own dial (and what the setup adds) on.
  if (part === "hand" && !(S.base && S.base.hand)) B0.hand = normPart("hand", freshPart("hand", "steady"));
  const s = DIALS[part][key], after = !!S.after;
  const t = { part, k: c.k, key, dialKey: key, title: label, choice: false, S, P, B0, say: sayOf(part, key, c.k),
    also: on.length === CHOICES[part].length ? null : on.map(x => x.label) };
  t.sweep = after ? "after" : s.choices ? "options" : "range";
  t.values = sweepValues(part, c.k, key, after ? 3 : 9);
  t.names = t.values.map(v => valueName(part, c.k, key, v));
  t.ends = [t.names[0], t.names[t.names.length - 1]];
  return t;
}
// The job for frame i of tile t (a value's picture, or with "after" its pictures over time; "time" has one job).
function tileJob(t, i) {
  const B = cloneBrush(t.B0), S = t.S, v = t.values[i];
  if (t.dialKey && v !== null && v !== undefined) B[t.part].d[t.dialKey] = v;
  normBrush(B);
  const k = t.sweep === "time" ? "time" : t.sweep === "after" ? "after" : "frame";
  return { k, id: t.id, i, B, B0: t.B0, role: t.P.role, shade: S.shade ?? t.P.shade, times: S.after, at: S.at, cross: S.cross, rub: S.rub,
    drops: S.drops, tilt: S.tilt, pace: S.pace, rest: S.rest, strokes: S.strokes, box: S.box };
}
// What a tile is drawn with, where it isn't the plain start (round tip, dry ink, smooth paper, Quick): two at most.
function withWords(t) {
  const out = [];
  for (const p of ["tip", "medium", "paper", "life"]) {
    if (p === t.part) continue;
    const P = t.B0[p];
    if (P.k === DEMO_BASE[p][0] && !(p === "medium" && P.d.wet > .05)) continue;
    const c = choiceOf(p, P.k);
    out.push(p === "medium" && P.k === "ink" ? "wet ink" : p === "life" ? c.label.toLowerCase() + " drying" : p === "tip" ? c.label.toLowerCase() + " tip" : c.label.toLowerCase());
  }
  return out.length ? "Drawn with " + out.slice(0, 2).join(" and ") : "";
}
