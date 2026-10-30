// ---------- the brush model, as the app's: BrushSetting, BrushStyle, BrushCode, BrushNames, BrushVariations, BrushCaptions ----------
// A brush: { kind: "ink" | "paint", seed, name, style, nib, aspect, angle, taperIn, taperOut, pressure, speed, thicker,
//   dry, tooth, wobble, pool, splatter, mode: "wet" | "flat", soft, gran, feather, hollow, bloom, streaks, backrun, drying,
//   texture, builtin? }. Every dial of both kinds is always there (0 when unused), as the app's WildBrush.
const TEXTURE_NAMES = ["plain", "cold press", "laid", "felt", "crumpled", "canvas"];
// Never reorder: share codes are written in this order (BrushSetting.java).
const SETTINGS = {
  nib: { label: "Nib", choices: ["round", "chisel", "flat"] },
  mode: { label: "Paint", choices: ["wet", "flat"] },
  texture: { label: "Texture", choices: TEXTURE_NAMES },
  aspect: { label: "Nib width", min: .14, max: .55 },
  angle: { label: "Nib angle", min: 20, max: 70 },
  taperIn: { label: "Taper in", min: 0, max: 1 },
  taperOut: { label: "Taper out", min: 0, max: 1 },
  pressure: { label: "Pressure", min: 0, max: 1 },
  speed: { label: "Speed", min: -1, max: 1 },
  dry: { label: "Dry brush", min: 0, max: 1 },
  tooth: { label: "Paper tooth", short: "tooth", min: 0, max: 1 },
  wobble: { label: "Wobble", min: 0, max: 1 },
  pool: { label: "Pooling", min: 0, max: 1 },
  splatter: { label: "Splatter", min: 0, max: 1 },
  soft: { label: "Soft edge", short: "softness", min: 0, max: 1 },
  gran: { label: "Granulation", short: "grain", min: 0, max: 1 },
  feather: { label: "Feathered bleed", short: "bleed", min: 0, max: 1 },
  hollow: { label: "Hollow edge", min: 0, max: 1 },
  bloom: { label: "Bloom", min: 0, max: 1 },
  streaks: { label: "Brush streaks", short: "streaks", min: 0, max: 1 },
  backrun: { label: "Backruns", min: 0, max: 1 },
  drying: { label: "Drying time", short: "drying", min: 0, max: 1 }
};
for (const [k, s] of Object.entries(SETTINGS)) { s.k = k; s.short ??= s.label.toLowerCase(); }
const ORDER = {
  ink: ["nib", "texture", "aspect", "angle", "taperIn", "taperOut", "pressure", "speed", "dry", "tooth", "wobble", "pool", "splatter"],
  paint: ["mode", "texture", "pressure", "soft", "gran", "feather", "hollow", "bloom", "streaks", "backrun", "drying"]
};
const ALL_KEYS = Object.keys(SETTINGS);
const isChoice = k => !!SETTINGS[k].choices;
const STRENGTH = { ink: "tooth", paint: "gran" };
const nibOnly = k => k === "aspect" || k === "angle";
const wetOnly = k => k === "hollow" || k === "bloom" || k === "backrun";
const activeSetting = (b, k) => !(nibOnly(k) && b.nib === "round") && !(wetOnly(k) && b.mode !== "wet");
// A choice's option index; a custom texture (not in the app yet) counts as plain.
function getIndex(b, k) {
  if (k === "mode") return b.mode === "wet" ? 0 : 1;
  const i = SETTINGS[k].choices.indexOf(b[k]);
  return i < 0 ? 0 : i;
}
function setIndex(b, k, i) { b[k] = SETTINGS[k].choices[i]; }
const wetMs = v => Math.round(750 + 4250 * v * v);
function shown(b, k) {
  const s = SETTINGS[k];
  if (s.choices) return k === "texture" ? b.texture : SETTINGS[k].choices[getIndex(b, k)];
  const v = b[k];
  if (k === "speed") return v === 0 ? "off" : Math.abs(v).toFixed(2);
  if (k === "drying") return (wetMs(v) / 1000).toFixed(1) + " s";
  if (k === "angle") return Math.round(v) + "°";
  if (s.min === 0 && v === 0) return "off";
  return v.toFixed(2);
}

// A blank brush, every dial off, round, flat, plain, Wild: as new WildBrush(kind, seed, name).
function blank(kind, seed, name) {
  const b = { kind, seed: seed >>> 0, name, style: WILD, nib: "round", aspect: 1, angle: 0, thicker: false, mode: "flat", texture: "plain" };
  for (const k of ALL_KEYS) if (!isChoice(k) && k !== "aspect" && k !== "angle") b[k] = 0;
  return b;
}
const nibWidths = nib => nib === "chisel" ? [.3, .55] : [.14, .28];
function normalize(b) {
  if (b.kind === "ink") {
    if (b.nib === "round") b.aspect = 1;
    else { const [lo, hi] = nibWidths(b.nib); b.aspect = b.aspect === 1 ? (lo + hi) / 2 : clamp(b.aspect, lo, hi); }
  } else if (b.mode !== "wet") { b.hollow = 0; b.bloom = 0; b.backrun = 0; }
  return b;
}
function copyBrush(b, seed) {
  const c = { ...b }; delete c.builtin; delete c.blurb; if (seed !== undefined) c.seed = seed >>> 0;
  return c;
}
const speedDir = b => b.speed < 0 ? -1 : b.speed > 0 ? 1 : b.thicker ? 1 : -1;
// A texture needs some strength to show: picking one raises a faint strength to .5, unless that dial is locked.
function pickTexture(b, t, locks) {
  b.texture = t;
  const s = STRENGTH[b.kind];
  if (t !== "plain" && !locks.has(s) && b[s] < .35) b[s] = .5;
  return b;
}

// ---------- styles, as BrushStyle.java: a range per setting; [lo, hi] always, {p, lo, hi} on that often, a list of options ----------
const WILD = "Wild";
const STYLES = {
  ink: {
    "Fineliner": { nib: ["round"], texture: ["plain", "plain", "cold press"], pressure: [0, .25], taperIn: { p: .4, lo: 0, hi: .15 }, taperOut: { p: .4, lo: 0, hi: .2 }, speed: [-.2, .2], tooth: { p: .3, lo: 0, hi: .15 }, wobble: { p: .2, lo: 0, hi: .1 }, pool: { p: .4, lo: 0, hi: .3 } },
    "Brush pen": { nib: ["round", "chisel"], texture: ["plain", "plain", "cold press", "felt"], pressure: [.7, 1], taperIn: [.3, .8], taperOut: [.4, 1], speed: [0, .7], dry: { p: .3, lo: 0, hi: .3 }, tooth: { p: .3, lo: 0, hi: .2 }, wobble: { p: .3, lo: 0, hi: .15 }, pool: { p: .4, lo: 0, hi: .4 }, splatter: { p: .15, lo: 0, hi: .3 } },
    "Dip pen": { nib: ["flat", "chisel"], texture: ["plain", "laid", "cold press"], angle: [30, 60], pressure: [.4, .85], taperIn: { p: .5, lo: 0, hi: .3 }, taperOut: { p: .5, lo: 0, hi: .4 }, speed: [-.3, .3], dry: { p: .25, lo: 0, hi: .25 }, pool: { p: .6, lo: .1, hi: .6 }, splatter: { p: .3, lo: 0, hi: .35 } },
    "Marker": { nib: ["chisel", "round"], texture: ["plain", "felt"], pressure: [0, .2], speed: [-.3, 0], pool: [.3, .8], tooth: { p: .3, lo: 0, hi: .2 } },
    "Pencil": { nib: ["round"], texture: ["cold press", "felt", "laid"], pressure: [.2, .5], taperIn: { p: .4, lo: 0, hi: .2 }, taperOut: { p: .4, lo: 0, hi: .25 }, dry: [.1, .3], tooth: [.5, .9], wobble: { p: .3, lo: 0, hi: .12 } },
    "Charcoal": { nib: ["chisel", "round"], texture: ["crumpled", "cold press", "laid"], pressure: [.5, .9], taperIn: { p: .4, lo: 0, hi: .3 }, taperOut: { p: .4, lo: 0, hi: .3 }, dry: [.3, .6], tooth: [.6, 1], wobble: { p: .6, lo: 0, hi: .4 } }
  },
  paint: {
    "Watercolor": { mode: ["wet"], texture: ["plain", "cold press"], pressure: [.3, .8], soft: [.2, .6], gran: { p: .5, lo: 0, hi: .5 }, feather: { p: .3, lo: 0, hi: .3 }, hollow: { p: .6, lo: .2, hi: .7 }, bloom: { p: .6, lo: .2, hi: .6 }, backrun: { p: .3, lo: 0, hi: .5 }, drying: [.3, .7] },
    "Ink wash": { mode: ["wet"], texture: ["plain", "felt"], pressure: [.4, .9], soft: [.5, 1], feather: [.3, .8], hollow: { p: .3, lo: 0, hi: .3 }, bloom: { p: .3, lo: 0, hi: .3 }, drying: [.5, .9] },
    "Gouache": { mode: ["flat"], texture: ["plain", "plain", "felt"], pressure: [0, .3], soft: [0, .2], gran: { p: .3, lo: 0, hi: .2 }, streaks: { p: .3, lo: 0, hi: .3 }, drying: [0, .3] },
    "Oils": { mode: ["flat"], texture: ["canvas"], pressure: [.3, .7], soft: [0, .15], gran: { p: .4, lo: 0, hi: .3 }, streaks: [.5, .95], drying: [.8, 1] },
    "Pastels": { mode: ["flat"], texture: ["cold press", "felt", "crumpled", "laid"], pressure: [.2, .6], soft: [.1, .4], gran: [.6, 1], streaks: { p: .4, lo: 0, hi: .4 }, drying: [0, .1] },
    "Dry brush": { mode: ["flat", "flat", "wet"], texture: ["cold press", "laid", "crumpled"], pressure: [.3, .7], soft: [0, .3], gran: [.3, .8], streaks: [.5, 1], drying: [0, .3] }
  }
};
const styleNames = kind => [...Object.keys(STYLES[kind]), WILD];
const recipe = b => STYLES[b.kind][b.style] || null;
// A style's range for a dial, as BrushStyle.band: null in Wild and for choices; a dial the style leaves out is its off value.
function band(b, k) {
  const rec = recipe(b), s = SETTINGS[k];
  if (!rec || s.choices) return null;
  if (k === "aspect") return nibWidths(b.nib);
  const spec = rec[k];
  if (!spec) { if (k === "angle") return [20, 70]; const off = Math.max(0, s.min); return [off, off]; }
  return Array.isArray(spec) ? spec : [spec.p < 1 ? 0 : spec.lo, spec.hi];
}
function amountBand(b) {
  const bd = band(b, "speed");
  if (!bd) return null;
  if (bd[0] <= 0 && bd[1] >= 0) return [0, Math.max(-bd[0], bd[1])];
  const a = Math.abs(bd[0]), c = Math.abs(bd[1]);
  return [Math.min(a, c), Math.max(a, c)];
}
const optionsOf = (b, k) => { const rec = recipe(b); return rec && rec[k] ? rec[k].map(o => SETTINGS[k].choices.indexOf(o)) : null; };

// ---------- names: adjective-produce (app/src/main/assets/names) ----------
const NAMES_ADJ = "bold\nbrave\nbrisk\nbashful\nchipper\ncosmic\ndapper\ndramatic\nferal\nfierce\nfrantic\ngallant\nhumble\njumbo\nmellow\nnimble\npeculiar\nplump\npompous\nregal\nrogue\nrustic\nsleek\nsmug\nsnug\nstout\nstubborn\nsuave\nswift\nupbeat\nvivid\nwild\nzealous\n".split("\n").map(s => s.trim()).filter(Boolean);
const NAMES_PRODUCE = "plum\npear\nfig\nlime\nleek\nkale\nyam\nbean\npea\nbeet\ncorn\ndate\ngrape\npeach\nquince\nchard\nsquash\ngourd\ncress\nradish\nturnip\nparsnip\ncarrot\nlemon\nmelon\nmango\napple\ncherry\nolive\nonion\ngarlic\npepper\nokra\nfennel\nlychee\nguava\nshallot\ncaper\ncurrant\n".split("\n").map(s => s.trim()).filter(Boolean);
const pick = list => list[(Math.random() * list.length) | 0];
const freshName = () => pick(NAMES_ADJ) + "-" + pick(NAMES_PRODUCE);
function kinName(name) {
  const space = name.indexOf(" ");
  const family = (space > 0 ? name.slice(space + 1) : name.slice(name.lastIndexOf("-") + 1)).trim();
  if (!family) return freshName();
  return pick(NAMES_ADJ) + "-" + family.toLowerCase();
}
const newSeed = () => (Math.random() * 4294967296) >>> 0;

// ---------- rolls, as BrushStyle.roll and rollSetting ----------
// Wild: a pen or a paint at random, the roll tester's roll of a new seed, a random texture half the time.
function rollIn(kind, style) {
  if (style === WILD) {
    const k = Math.random() < .5 ? "ink" : "paint";
    const b = Object.assign(blank(k, 0, ""), roll(k, newSeed()));
    b.style = WILD; b.name = freshName();
    b.mode = b.kind === "paint" ? b.mode : "flat";
    pickTexture(b, Math.random() < .5 ? "plain" : TEXTURE_NAMES[1 + ((Math.random() * (TEXTURE_NAMES.length - 1)) | 0)], new Set());
    return normalize(b);
  }
  const rec = STYLES[kind][style], b = blank(kind, newSeed(), freshName());
  b.style = style;
  for (const k of ORDER[kind]) {
    const spec = rec[k], s = SETTINGS[k];
    if (s.choices) { const opts = spec || [s.choices[0]]; b[k] = opts[(Math.random() * opts.length) | 0]; }
    else if (k === "aspect") b.aspect = 1;
    else if (!spec) b[k] = k === "angle" ? lerp(20, 70, Math.random()) : Math.max(0, s.min);
    else if (Array.isArray(spec)) b[k] = lerp(spec[0], spec[1], Math.random());
    else b[k] = Math.random() < spec.p ? lerp(spec.lo, spec.hi, Math.random()) : 0;
  }
  normalize(b);
  if (kind === "ink" && b.nib !== "round") { const [lo, hi] = nibWidths(b.nib); b.aspect = lerp(lo, hi, Math.random()); }
  return b;
}
function rollSetting(b, k) {
  const s = SETTINGS[k];
  if (s.choices) { const o = optionsOf(b, k); setIndex(b, k, o ? o[(Math.random() * o.length) | 0] : (Math.random() * s.choices.length) | 0); }
  else {
    const bd = band(b, k);
    if (!bd && s.min === 0 && Math.random() < .3) b[k] = 0;
    else b[k] = lerp(bd ? bd[0] : s.min, bd ? bd[1] : s.max, Math.random());
  }
  return normalize(b);
}

// ---------- share codes, byte for byte as BrushCode.java: name.K S DIALS.SEED ----------
const STEPS = 1295, THINNER_AT_ZERO = 647, THICKER_AT_ZERO = 648;
const slug = name => name.toLowerCase().replace(/[^a-z]+/g, "-").replace(/^-|-$/g, "") || "brush";
function dials(b) {
  let out = "";
  for (const k of ORDER[b.kind]) {
    const s = SETTINGS[k];
    if (s.choices) { out += getIndex(b, k); continue; }
    let step;
    if (k === "speed" && b.speed === 0) step = b.thicker ? THICKER_AT_ZERO : THINNER_AT_ZERO;
    else { const v = Math.max(s.min, Math.min(s.max, b[k])); step = Math.round((v - s.min) / (s.max - s.min) * STEPS); }
    const d = step.toString(36);
    out += (d.length < 2 ? "0" : "") + d;
  }
  return out;
}
const styleIndex = b => Math.max(0, styleNames(b.kind).indexOf(b.style));
function encode(b) { return slug(b.name) + "." + (b.kind === "ink" ? "I" : "P") + styleIndex(b) + dials(b) + "." + (b.seed >>> 0).toString(36); }
// Java's String.trim: only characters up to space.
const javaTrim = s => s.replace(/^[\u0000- ]+|[\u0000- ]+$/g, "");
function decode(code) {
  if (code == null) return null;
  let c = javaTrim(String(code)).toLowerCase();
  const dot = c.indexOf(".") + 1;
  if (dot > 0 && dot < c.length) c = c.slice(0, dot) + c[dot].toUpperCase() + c.slice(dot + 1);
  const m = /^([a-z]+(?:-[a-z]+)*)\.([IP])(\d)([0-9a-z]+)\.([0-9a-z]{1,7})$/.exec(c);
  if (!m) return null;
  const kind = m[2] === "I" ? "ink" : "paint", styles = styleNames(kind), style = m[3].charCodeAt(0) - 48;
  const seed = parseInt(m[5], 36);
  if (style >= styles.length || seed > 0xffffffff) return null;
  const b = blank(kind, seed, m[1]);
  b.style = styles[style];
  const digits = m[4];
  let at = 0;
  for (const k of ORDER[kind]) {
    const s = SETTINGS[k];
    if (s.choices) {
      if (at >= digits.length) return null;
      const o = digits.charCodeAt(at++) - 48;
      if (o < 0 || o >= s.choices.length) return null;
      setIndex(b, k, o); continue;
    }
    if (at + 2 > digits.length) return null;
    const step = parseInt(digits.slice(at, at + 2), 36);
    at += 2;
    if (k === "speed" && (step === THINNER_AT_ZERO || step === THICKER_AT_ZERO)) { b.speed = 0; b.thicker = step === THICKER_AT_ZERO; continue; }
    b[k] = s.min + step / STEPS * (s.max - s.min);
  }
  return at === digits.length ? normalize(b) : null;
}
// What makes two brushes draw alike: the code without name or style.
const settingsKey = b => (b.kind === "ink" ? "I" : "P") + dials(b) + "." + (b.seed >>> 0).toString(36) + (TEXTURE_NAMES.includes(b.texture) ? "" : "+" + b.texture);
const NAME_MAX = 32;
function cleanName(name) { const c = name.replace(/\s+/g, " ").trim(); return c.length > NAME_MAX ? c.slice(0, NAME_MAX).trim() : c; }

// ---------- captions, as BrushCaptions.java ----------
const LINE = 17;
function twoLines(items) {
  const lines = [];
  for (const it of items) {
    if (it.length > LINE) continue;
    const last = lines.length - 1;
    if (last >= 0 && (lines[last] + ", " + it).length <= LINE) lines[last] += ", " + it;
    else if (lines.length < 2) lines.push(it);
  }
  return lines;
}
function describeTool(b) {
  if (b.builtin) return twoLines([b.style, b.blurb]);
  const t = [b.style], add = (on, s) => { if (on) t.push(s); };
  if (b.kind === "ink") {
    add(b.nib !== "round", b.nib + " nib");
    add(b.splatter > .3, "splatters"); add(b.dry > .3, "dry brush"); add(b.tooth > .4, "toothy");
    add(b.wobble > .2, "wobbly"); add(b.pool > .4, "pools ink"); add(b.taperIn + b.taperOut > 1, "long tapers");
    add(b.pressure > .75, "pressure-hungry"); add(Math.abs(b.speed) > .4, b.speed > 0 ? "thicker when fast" : "thinner when fast");
  } else {
    const before = t.length;
    add(b.bloom > .35, "blooms"); add(b.hollow > .4, "hollow edge"); add(b.feather > .4, "feathered");
    add(b.streaks > .4, "streaky"); add(b.gran > .5, "grainy"); add(b.backrun > .3, "backruns");
    add(b.soft > .6, "soft edge"); add(b.drying > .75, "slow drying"); add(b.drying < .15, "dries fast");
    add(t.length === before, b.mode === "wet" ? "wet" : "flat");
  }
  add(b.texture !== "plain", b.texture);
  return twoLines(t);
}

// ---------- variations, as BrushVariations.java ----------
function holdsLocked(b, k, locks) {
  if (k === "nib") return locks.has("aspect");
  if (k !== "mode") return false;
  for (const l of locks) if (wetOnly(l) && b[l] !== 0) return true;
  return false;
}
function varies(b, k) {
  const rec = recipe(b);
  if (!rec) return true;
  if (isChoice(k)) { const o = optionsOf(b, k); return !!o && o.length > 1; }
  if (k === "aspect") return true;
  const bd = band(b, k); return bd[1] > bd[0];
}
function direction(b, locks) {
  const pool = ORDER[b.kind].filter(k => !locks.has(k) && !holdsLocked(b, k, locks) && (isChoice(k) || activeSetting(b, k)) && varies(b, k));
  const pushes = [], n = 2 + (Math.random() < .4 ? 1 : 0);
  while (pushes.length < n && pool.length) {
    const k = pool.splice((Math.random() * pool.length) | 0, 1)[0], s = SETTINGS[k];
    if (s.choices) {
      if (Math.random() < .5) continue;
      const cur = getIndex(b, k), opts = optionsOf(b, k) || s.choices.map((_, i) => i), others = opts.filter(o => o !== cur);
      if (!others.length) continue;
      pushes.push({ k, to: others[(Math.random() * others.length) | 0] }); continue;
    }
    const bd = band(b, k), v = b[k], lo = Math.min(bd ? bd[0] : s.min, v), hi = Math.max(bd ? bd[1] : s.max, v), span = hi - lo;
    const rel = (v - lo) / (span === 0 ? 1 : span);
    let sign = Math.random() < .5 ? -1 : 1;
    if (rel < .08) sign = 1; else if (rel > .92) sign = -1;
    pushes.push({ k, d: sign * lerp(.35, .8, Math.random()) * span, lo, hi });
  }
  return pushes;
}
function applyPushes(b, pushes, locks, seed) {
  const c = copyBrush(b, seed);
  for (const p of pushes) {
    if (p.k === "texture") { pickTexture(c, TEXTURE_NAMES[p.to], locks); continue; }
    if (isChoice(p.k)) { setIndex(c, p.k, p.to); continue; }
    let v = Math.max(p.lo, Math.min(p.hi, b[p.k] + p.d));
    if (SETTINGS[p.k].min === 0 && v < .04) v = 0;
    c[p.k] = v;
  }
  return normalize(c);
}
function describePushes(pushes) {
  return twoLines(pushes.map(p => {
    if (!isChoice(p.k)) return (p.d > 0 ? "more " : "less ") + SETTINGS[p.k].short;
    const o = SETTINGS[p.k].choices[p.to];
    return p.k === "nib" ? o + " nib" : p.k === "texture" ? o : o + " paint";
  }));
}
function makeVariations(b, locks) {
  const out = [];
  for (let i = 0; i < 6; i++) {
    const pushes = direction(b, locks), v = applyPushes(b, pushes, locks, newSeed());
    v.name = b.builtin ? pick(NAMES_ADJ) + "-" + b.name.toLowerCase() : kinName(b.name);
    out.push({ b: v, caption: describePushes(pushes) });
  }
  return out;
}

// ---------- built-ins: the app's four tools, as their Wild-engine approximations (BuiltinBrush.java) ----------
function builtin(kind, name, style, blurb, seed, dials) {
  const b = Object.assign(blank(kind, seed, name), dials);
  b.style = style; b.builtin = true; b.blurb = blurb;
  return normalize(b);
}
const BUILT = [
  builtin("ink", "Ink", "Fineliner", "steady round line", 11, { nib: "round", angle: 45, pressure: .55, taperOut: .12 }),
  builtin("ink", "Fude", "Brush pen", "swells and tapers", 12, { nib: "chisel", aspect: .34, angle: 40, pressure: 1, taperIn: .35, taperOut: .6, speed: .3 }),
  builtin("paint", "Wash", "Gouache", "flat, even tone", 13, { mode: "flat", pressure: .1, soft: .25 }),
  builtin("paint", "Watercolor", "Watercolor", "soft blooms", 14, { mode: "wet", pressure: .6, soft: .45, gran: .2, bloom: .35, drying: .55 })
];
