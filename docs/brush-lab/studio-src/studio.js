// ===================================================================================================================
// Brush Studio: the page. The engine (the lab's, in #engine) and render.js are loaded before this; every picture is
// theirs. Heavy pictures go to a few workers (blob URLs), or run here when workers can't start; the test page and its
// live stroke always run here.
// ===================================================================================================================
const $ = id => document.getElementById(id);
const escH = t => String(t).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const DPX = 1.875;                       // device pixels per dp on the Supernotes (300 ppi): every picture is drawn at the Manta's scale
const PART_ORDER = ["tip", "medium", "paper", "life", "hand", "carry"];   // the word list's order: Tip, Medium, Paper, Drying, Control, Smudge
// Part cards show waves (one part or setting); anything that is a whole brush shows the loop (docs/DESIGN.md "Loop and wave").
const SWATCH_ROLE = { tip: "tip", medium: "medium", carry: "carry", paper: "paper", life: "life", hand: "hand" };
const GREY_STEPS = [1, .53, .3];
const STORE = "inkwash.studio.v1";
const NK = 41;                           // knots on each of the test stroke's curves
const TILT_MAX = 70, SPEED_MIN = 10, SPEED_MAX = 400;
const cap1s = t => t ? t.charAt(0).toUpperCase() + t.slice(1) : t;
const lc = t => String(t).toLowerCase();

// ---------- state ----------
const ST = {
  B: null, tab: "lab", focus: { part: "medium", key: "wet" }, locks: { parts: new Set(), dials: new Set() }, lib: [],
  prev: null, drag: null, sizeSel: 1, shadeSel: 0, showDiff: true, eraser: false, zoom: 16, cross: { row: "tip", col: "medium" },
  wild: [], comparePick: false, picked: [], qr: false, cursor: .5
};
const H = { nodes: [], cur: -1, next: 0 };
const TUNE = { geo: null, P: null, T: null, V: null, A: null, path: null, key: "", res: null, fit: null, size: 0 };

// ---------- brushes ----------
const BRUSHES = new Map();   // code|name → brush, so a history row or a tile doesn't decode twice
function fromCode(code, name) {
  const k = code + "|" + (name || "");
  if (BRUSHES.has(k)) return cloneBrush(BRUSHES.get(k));
  const r = readAnyCode(code); if (!r) return null;
  if (name) r.b.name = cleanName(String(name));
  if (BRUSHES.size > 400) BRUSHES.clear();
  BRUSHES.set(k, r.b);
  return cloneBrush(r.b);
}
const shade = () => GREY_STEPS[ST.shadeSel] ?? 1;
const isErase = (part, key) => part === "medium" && key === "erase";
// A choice's dials as a card shows them; with the brush, those it shows for it (Screen's own only with a screen: newmods.js dialShown).
const dialKeys = (part, k, B) => choiceOf(part, k).dials.filter(key => !DIALS[part][key].hide && !isErase(part, key) && (!B || typeof dialShown !== "function" || dialShown(B, part, key)));
function defOf(B, part, key) {
  if (isErase(part, key)) return realErase(B.style, B.medium.k);
  const d = freshPart(part, B[part].k).d[key];
  return d === undefined ? dialDefault(part, B[part].k, key) : d;
}
function rangeFor(part, k, key) { return isErase(part, key) ? [0, 1] : rangeOf(part, k, key); }
function isDef(B, part, key) {
  const v = B[part].d[key], d = defOf(B, part, key), sp = DIALS[part][key];
  if (sp.choices) return (v | 0) === (d | 0);
  const [lo, hi] = rangeFor(part, B[part].k, key);
  return Math.abs(v - d) <= (hi - lo) * .004 + 1e-9;
}
// A choice dial's option as shown (newmods.js optLabel: Opaque's own Blend is "cover"), or the dial's own list.
const optText = (part, k, key, i) => typeof optLabel === "function" ? optLabel(part, k, key, i) : DIALS[part][key].choices[i];
function fmtV(part, key, v, k) {
  if (DIALS[part][key].choices && k) return optText(part, k, key, v | 0);
  if (isErase(part, key)) return v <= .02 ? "permanent" : v >= .98 ? "erases clean" : Math.round(v * 100) + "%";
  return fmtDial(part, key, v);
}
const labelOf = (B, part, key) => isErase(part, key) ? "Erasable" : dialLabel(part, B[part].k, key);
function withDial(B, part, key, v) {
  const c = cloneBrush(B); c[part].d[key] = v;
  if (part === "hand" && HAND_CELLS.includes(key)) c.hand.k = matchHand(c.hand);
  return normBrush(c);
}
function withChoice(B, part, k) {
  if (B[part].k === k) return cloneBrush(B);
  const c = cloneBrush(B); c[part] = switchChoice(part, c[part], k); return normBrush(c);
}
const choiceLabel = (part, k) => choiceOf(part, k).label;
const cardName = (B, part) => part === "hand" ? choiceLabel("hand", B.hand.k) : choiceWords(B, part);
const captionOf = B => captionLines(B).join(", ");
// A tile's caption leaves out "Anything goes": every brush there is one.
const tileCaption = B => { const c = captionItems(B); return (c[0] === WILDCARD ? c.slice(1) : c).join(", "); };
const sizeList = B => { const s = Math.max(2, Math.min(240, styleSize(B.style || WILDCARD, B))); return [Math.max(1, Math.round(s * .5)), s, Math.min(360, Math.round(s * 2))]; };
const sizePx = () => sizeList(ST.B)[ST.sizeSel] ?? 16;
const mmText = px => { const m = px * 25.4 / 300; return (m < 10 ? m.toFixed(1) : Math.round(m)) + " mm"; };
const dialLocked = (part, key) => ST.locks.parts.has(part) || ST.locks.dials.has(part + "." + key);
// Anything goes: any choice of every part and any dial over its whole range (the styles' bands left aside), about four
// dials in ten left at their defaults so the marks still read; locked parts and dials kept.
function wildRoll(base, lk) {
  const B = { name: freshName(), seed: newSeed(), style: WILDCARD }, r = Math.random;
  for (const part of PARTS) {
    if (lk.parts.has(part)) { B[part] = cloneBrush(base[part]); continue; }
    let P;
    if (part === "hand") {
      P = freshPart("hand", "custom");
      for (const key of HAND_CELLS) P.d[key] = r() < .5 ? 0 : r();
      P.d.gain = lerp(.6, 1.5, r()); P.d.dir = r() < .5 ? 1 : 0; P.k = matchHand(P);
    } else {
      const list = CHOICES[part], c = list[(r() * list.length) | 0];
      P = freshPart(part, c.k);
      for (const key of c.dials) {
        const sp = DIALS[part][key]; if (sp.hide) continue;
        // Never rolled (parts.js keepErase, below): the brush's own.
        if (isErase(part, key)) { if (c.k === "resist") P.d.erase = r() < .7 ? 1 : 0; continue; }
        if (r() < .4) continue;
        const [lo, hi] = rangeOf(part, c.k, key);
        P.d[key] = sp.choices ? (r() * sp.choices.length) | 0 : lerp(lo, hi, r());
      }
    }
    for (const key of Object.keys(P.d)) if (lk.dials.has(part + "." + key) && base[part].k === P.k) P.d[key] = base[part].d[key];
    B[part] = P;
  }
  keepErase(B, base);
  normBrush(B);
  // Last: what stands out takes the adjective's place ("drip-plum"; parts.js describedName, from the seed, no draw).
  B.name = describedName(B, B.name);
  return B;
}
// The basic tools: the app's four, Float's two and Erasable's three (as the lab adds them).
function basicTools() {
  const out = builtins().map(b => { delete b.builtin; delete b.blurb; return b; });
  const make = (name, style, seed, tip, medium, md, carry, cd, life, hand) => {
    const B = { name, seed, style, tip: freshPart("tip", tip), medium: switchChoice("medium", null, medium), carry: freshPart("carry", carry),
      paper: freshPart("paper", "plain"), life: freshPart("life", life), hand: freshPart("hand", hand) };
    Object.assign(B.medium.d, md); Object.assign(B.carry.d, cd);
    return normBrush(B);
  };
  out.push(make("Sumi", "Ink wash", 0x5e1, "round", "ink", {}, "off", {}, "float", "darker"),
    make("Clear water", "Ink wash", 0xc1ea, "round", "ink", {}, "smudge", { load: 0, pick: .9 }, "float", "darker"),
    make("Sketch pencil", "Pencil", 0x5e7c, "round", "graphite", { grade: .3 }, "off", {}, "wet", "darker"),
    make("Steady line", "Fineliner", 0x51e, "round", "ink", {}, "off", {}, "wet", "steady"),
    make("Soft wash", "Watercolor", 0x50f7, "round", "wash", {}, "off", {}, "wet", "steady"));
  return out;
}
// A few brushes to fill Shared brushes until sharing opens: one roll in each of eight styles, the same every time.
function exampleBrushes() {
  const keep = Math.random; Math.random = rng(20261016);
  try { return ["Ink wash", "Dry brush", "Hake", "Watercolor", "Pencil", "Charcoal", "Gouache", "Dip pen"].map(s => rollStyle(s)); }
  finally { Math.random = keep; }
}
let BASICS = [], EXAMPLES = [];
// The owner's favourites (docs/brush-lab/favourites.txt, put in by the build): each added to the library once.
const FAVOURITES = /*@@FAVOURITES@@*/[];
function addFavourites() {
  ST.favs ??= [];
  for (const code of FAVOURITES) {
    if (ST.favs.includes(code)) continue;
    ST.favs.push(code);
    const r = readAnyCode(code); if (r && !ST.lib.some(b => b.name === r.b.name)) ST.lib.push(r.b);
  }
}

// ---------- the render pool: a few workers running the engine and render.js, or the page itself ----------
const POOL = { workers: [], idle: [], inPage: false, ready: false, q: [], flight: new Map(), latest: new Map(), cache: new Map(), bytes: 0, n: 0, pumping: false, done: 0 };
const hashStr = s => { let h = 0x811c9dc5; for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619); return (h >>> 0).toString(36); };
const jobKey = j => [j.k, j.bare && studioWhite(j.B) ? "white" : "", (j.role || "") + (j.tilt ? "+t" : "") + (j.drops ? "+d" : ""), j.w, j.h, j.shade ?? 1, j.size || 0, j.under ? 1 : 0, j.bare ? 1 : 0, j.pathKey || "", j.bare ? "" : brushKey(j.B)].join("|");
// Ask for a picture for a slot (one place on the page); a newer ask for the same slot replaces an older one still waiting.
function request(slot, job, cb, prio = 2) {
  const key = jobKey(job);
  POOL.latest.set(slot, key);
  const hit = POOL.cache.get(key);
  if (hit) { POOL.cache.delete(key); POOL.cache.set(key, hit); cb(hit); return; }
  for (const e of POOL.q) e.waiters = e.waiters.filter(w => w.slot !== slot);
  POOL.q = POOL.q.filter(e => e.waiters.length);
  const fl = POOL.flight.get(key); if (fl) { fl.waiters.push({ slot, cb }); return; }
  const queued = POOL.q.find(e => e.key === key);
  if (queued) { queued.waiters.push({ slot, cb }); queued.prio = Math.min(queued.prio, prio); return; }
  POOL.q.push({ key, job, prio, n: POOL.n++, waiters: [{ slot, cb }] });
  pumpPool();
}
function takeJob() {
  if (!POOL.q.length) return null;
  let bi = 0;
  for (let i = 1; i < POOL.q.length; i++) { const a = POOL.q[i], b = POOL.q[bi]; if (a.prio < b.prio || (a.prio === b.prio && a.n < b.n)) bi = i; }
  const e = POOL.q.splice(bi, 1)[0];
  POOL.flight.set(e.key, e);
  return e;
}
function pumpPool() {
  if (!POOL.ready) return;
  if (POOL.inPage) {
    if (POOL.pumping) return;
    POOL.pumping = true;
    const step = () => {
      const t0 = performance.now();
      let e;
      while ((e = takeJob())) { runHere(e); if (performance.now() - t0 > 30) break; }
      if (POOL.q.length) setTimeout(step, 0); else POOL.pumping = false;
    };
    setTimeout(step, 0);
    return;
  }
  while (POOL.idle.length && POOL.q.length) { const e = takeJob(), w = POOL.idle.pop(); w.job = e.key; w.postMessage(Object.assign({ id: e.key }, e.job)); }
}
function runHere(e) {
  let res;
  try { res = studioJob(Object.assign({ id: e.key }, e.job)); } catch (err) { res = { id: e.key, error: String(err && err.stack || err) }; }
  finishJob(res);
}
function finishJob(res) {
  const e = POOL.flight.get(res.id); if (!e) return;
  POOL.flight.delete(res.id); POOL.done++;
  if (res.error) { console.warn("Brush Studio: a picture failed: " + res.error); return; }
  const out = { w: res.w, h: res.h, gray: res.gray, ms: res.ms };
  POOL.cache.set(res.id, out); POOL.bytes += out.gray.length;
  for (const k of POOL.cache.keys()) { if (POOL.bytes < 96e6) break; POOL.bytes -= POOL.cache.get(k).gray.length; POOL.cache.delete(k); }
  for (const w of e.waiters) if (POOL.latest.get(w.slot) === res.id) { try { w.cb(out); } catch (err) { console.error(err); } }
}
function startPool() {
  POOL.ready = true;
  if (/[?&]inpage\b/.test(location.search) || typeof Worker === "undefined") { POOL.inPage = true; pumpPool(); return; }
  const src = $("engine").textContent + "\n;\n" + $("render").textContent + "\n;\n" +
    "self.onmessage = e => { const m = e.data; if (m.textures) { for (const k in m.textures) TEXTURES[k] = m.textures[k]; return; }\n" +
    "  let out; try { out = studioJob(m); } catch (err) { self.postMessage({ id: m.id, error: String(err && err.stack || err) }); return; }\n" +
    "  self.postMessage(out, [out.gray.buffer]); };";
  const n = Math.max(1, Math.min(4, (navigator.hardwareConcurrency || 2) - 1));
  try {
    const url = URL.createObjectURL(new Blob([src], { type: "text/javascript" }));
    for (let i = 0; i < n; i++) {
      const w = new Worker(url);
      w.onmessage = e => { w.job = null; POOL.idle.push(w); finishJob(e.data); pumpPool(); };
      w.onerror = e => { if (e.preventDefault) e.preventDefault(); poolFallBack(); };
      w.postMessage({ textures: TEXTURES });
      POOL.workers.push(w); POOL.idle.push(w);
    }
  } catch (e) { poolFallBack(); return; }
  pumpPool();
}
function poolFallBack() {
  if (POOL.inPage) return;
  POOL.inPage = true;
  for (const w of POOL.workers) try { w.terminate(); } catch (e) { /* gone already */ }
  POOL.workers = []; POOL.idle = [];
  for (const e of POOL.flight.values()) POOL.q.push(e);
  POOL.flight.clear();
  pumpPool();
}
// A picture on a canvas: its 16 greys as they are, or (diff) the changed pixels in ink over a faded copy.
function paint(canvas, res, diff) {
  if (!canvas) return;
  if (canvas.width !== res.w || canvas.height !== res.h) { canvas.width = res.w; canvas.height = res.h; }
  const ctx = canvas.getContext("2d"), img = ctx.createImageData(res.w, res.h), d = img.data, g = res.gray, o = diff && diff.w === res.w && diff.h === res.h ? diff.gray : null;
  for (let i = 0, j = 0; i < g.length; i++, j += 4) {
    let v = g[i];
    if (o) v = v !== o[i] ? 0 : 255 - ((255 - v) >> 2);
    d[j] = d[j + 1] = d[j + 2] = v; d[j + 3] = 255;
  }
  ctx.putImageData(img, 0, 0);
}
const qdim = v => Math.max(8, Math.round(v * DPX / 4) * 4);
// A sample in a frame: the size comes from the frame as laid out, so a hidden tab draws when it is shown.
function drawSample(canvas, slot, B, role, prio = 2, opts) {
  const fr = canvas && canvas.parentElement; if (!fr || !fr.clientWidth || !fr.clientHeight) return;
  request(slot, Object.assign({ k: "sample", B, role, w: qdim(fr.clientWidth), h: qdim(fr.clientHeight), shade: shade() }, opts), res => paint(canvas, res), prio);
}
function pathJob(B, fr, size, under) { return { k: "path", B, w: qdim(fr.clientWidth), h: qdim(fr.clientHeight), shade: shade(), path: TUNE.path, pathKey: TUNE.key, size, under }; }

// ---------- icons ----------
const ICON = {
  lock: '<svg viewBox="0 0 20 20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="miter"><rect x="4" y="9" width="12" height="8"/><path d="M7 9V6.5a3 3 0 0 1 6 0V9"/></svg>',
  open: '<svg viewBox="0 0 20 20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="miter"><rect x="4" y="9" width="12" height="8"/><path d="M7 9V6.5a3 3 0 0 1 5.8-1"/></svg>',
  die: '<svg viewBox="0 0 20 20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2"><rect x="3.5" y="3.5" width="13" height="13"/><g fill="currentColor" stroke="none"><rect x="6" y="6" width="2.6" height="2.6"/><rect x="8.7" y="8.7" width="2.6" height="2.6"/><rect x="11.4" y="11.4" width="2.6" height="2.6"/></g></svg>'
};
// The app's dice (Phosphor), an undo arrow and the menu caret, for the buttons that carry an icon.
ICON.dice = '<svg viewBox="0 0 256 256" aria-hidden="true" fill="currentColor"><path d="M192,28H64A36,36,0,0,0,28,64V192a36,36,0,0,0,36,36H192a36,36,0,0,0,36-36V64A36,36,0,0,0,192,28Zm12,164a12,12,0,0,1-12,12H64a12,12,0,0,1-12-12V64A12,12,0,0,1,64,52H192a12,12,0,0,1,12,12ZM104,88A16,16,0,1,1,88,72,16,16,0,0,1,104,88Zm40,40a16,16,0,1,1-16-16A16,16,0,0,1,144,128Zm40-40a16,16,0,1,1-16-16A16,16,0,0,1,184,88Zm-80,80a16,16,0,1,1-16-16A16,16,0,0,1,104,168Zm80,0a16,16,0,1,1-16-16A16,16,0,0,1,184,168Z"/></svg>';
ICON.undo = '<svg viewBox="0 0 20 20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2"><path d="M7 4L3 8l4 4"/><path d="M3 8h9a5 5 0 0 1 0 10H8"/></svg>';
ICON.pencil = '<svg viewBox="0 0 20 20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="miter"><path d="M4 16l1-4 8-8 3 3-8 8z"/><path d="M11 6l3 3"/></svg>';
ICON.check = '<svg viewBox="0 0 20 20" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2.4"><path d="M4 10.5l4 4 8-9"/></svg>';
ICON.caret = '<svg viewBox="0 0 20 20" aria-hidden="true" fill="currentColor"><path d="M5 7h10l-5 6z"/></svg>';
const lockBtn = (act, id, on, what) => `<button type="button" class="icn${on ? " on" : ""}" data-act="${act}" data-id="${id}" aria-pressed="${on}" aria-label="Lock ${escH(what)}">${on ? ICON.lock : ICON.open}</button>`;
const dieBtn = (act, part, key, off, what) => `<button type="button" class="icn" data-act="${act}" data-part="${part}"${key ? ` data-key="${key}"` : ""}${off ? " disabled" : ""} aria-label="Roll ${escH(what)}">${ICON.die}</button>`;

// ---------- status ----------
let statusTimer = 0;
function setStatus(t) { const s = $("status"); s.textContent = t; clearTimeout(statusTimer); statusTimer = setTimeout(() => { s.textContent = ""; }, 2200); }

// ---------- remembered between visits (this browser only, best effort) ----------
function save() {
  try {
    localStorage.setItem(STORE, JSON.stringify({
      v: 1, favs: ST.favs || [], cur: [encodeM(ST.B), ST.B.name], base: ST.base ? [ST.base.code, ST.base.name] : null, lib: ST.lib.map(b => [encodeM(b), b.name]),
      hist: { nodes: H.nodes.map(n => [n.id, n.parent, n.code, n.name, n.what, n.at]), cur: H.cur, next: H.next },
      focus: ST.focus, sizeSel: ST.sizeSel, shadeSel: ST.shadeSel, tab: ST.tab, cross: ST.cross, zoom: ST.zoom, showDiff: ST.showDiff,
      locks: { parts: [...ST.locks.parts], dials: [...ST.locks.dials] },
      tune: { geo: TUNE.geo.map(p => [+p.x.toFixed(4), +p.y.toFixed(4)]), P: [...TUNE.P].map(v => +v.toFixed(3)), T: [...TUNE.T].map(v => +v.toFixed(1)), V: [...TUNE.V].map(v => Math.round(v)), A: [...TUNE.A].map(v => +v.toFixed(2)) }
    }));
  } catch (e) { /* storage off: the studio still works, it just won't remember */ }
}
function loadSaved() {
  let o = null;
  try { o = JSON.parse(localStorage.getItem(STORE) || "null"); } catch (e) { o = null; }
  if (!o || o.v !== 1) return false;
  try {
    const cur = Array.isArray(o.cur) ? fromCode(o.cur[0], o.cur[1]) : null;
    if (!cur) return false;
    ST.B = cur;
    ST.lib = (o.lib || []).map(([c, n]) => fromCode(c, n)).filter(Boolean);
    if (Array.isArray(o.base) && readAnyCode(o.base[0])) ST.base = { code: String(o.base[0]), name: String(o.base[1] || "") };
    if (o.hist && Array.isArray(o.hist.nodes)) {
      H.nodes = o.hist.nodes.filter(a => Array.isArray(a) && readAnyCode(a[2])).map(([id, parent, code, name, what, at]) => ({ id: id | 0, parent: parent | 0, code, name: String(name || ""), what: String(what || ""), at: +at || Date.now() }));
      H.cur = o.hist.cur | 0; H.next = Math.max(o.hist.next | 0, ...H.nodes.map(n => n.id + 1), 0);
      if (!H.nodes.some(n => n.id === H.cur)) H.cur = H.nodes.length ? H.nodes[H.nodes.length - 1].id : -1;
    }
    if (o.focus && DIALS[o.focus.part] && DIALS[o.focus.part][o.focus.key]) ST.focus = { part: o.focus.part, key: o.focus.key };
    ST.sizeSel = clamp(o.sizeSel | 0, 0, 2); ST.shadeSel = clamp(o.shadeSel | 0, 0, 2);
    const tab = { understand: "lab", explore: "mix", share: "lab" }[o.tab] || o.tab;
    if (["community", "lab", "tune", "mix", "roll"].includes(tab)) ST.tab = tab;
    if (Array.isArray(o.favs)) ST.favs = o.favs.map(String);
    if (o.cross && PARTS.includes(o.cross.row) && PARTS.includes(o.cross.col) && o.cross.row !== o.cross.col) ST.cross = { row: o.cross.row, col: o.cross.col };
    if ([1, 4, 16].includes(o.zoom)) ST.zoom = o.zoom;
    ST.showDiff = o.showDiff !== false;
    if (o.locks) { ST.locks.parts = new Set((o.locks.parts || []).filter(p => PARTS.includes(p))); ST.locks.dials = new Set((o.locks.dials || []).map(String)); }
    const t = o.tune;
    if (t && Array.isArray(t.geo) && t.geo.length > 3 && [t.P, t.T, t.V, t.A].every(a => Array.isArray(a) && a.length === NK)) {
      TUNE.geo = t.geo.map(([x, y]) => ({ x: +x, y: +y }));
      TUNE.P = Float32Array.from(t.P, v => clamp(+v || 0)); TUNE.T = Float32Array.from(t.T, v => clamp(+v || 0, 0, TILT_MAX));
      TUNE.V = Float32Array.from(t.V, v => clamp(+v || 100, SPEED_MIN, SPEED_MAX)); TUNE.A = Float32Array.from(t.A, v => +v || 0);
    }
    return true;
  } catch (e) { return false; }
}

// ---------- history: every change a node; a change made after jumping back starts a branch ----------
function addNode(what) {
  const code = encodeM(ST.B), here = H.nodes.find(n => n.id === H.cur);
  if (here && here.code === code && here.name === ST.B.name) return;
  H.nodes.push({ id: H.next++, parent: here ? here.id : -1, code, name: ST.B.name, what, at: Date.now() });
  H.cur = H.next - 1;
  while (H.nodes.length > 240) {
    const old = H.nodes.shift();
    for (const n of H.nodes) if (n.parent === old.id) n.parent = old.parent;
  }
}
// Set the brush: before/after shows this change, history gains a node.
function commit(B, what, ba) {
  const from = ST.B;
  ST.B = normBrush(B);
  if (brushKey(from) === brushKey(ST.B) && from.name === ST.B.name) return;
  ST.prev = { B: from, what: ba || what };
  addNode(what);
  save();
  refresh();
}

// ---------- Randomize: the whole brush again in its style (or another), keeping every lock ----------
function rollBrush(style) {
  const old = ST.B, B = rollStyle(style || old.style || WILDCARD, old, ST.locks);
  // Erasable's lock holds across media (it means the same in each); rollStyle keeps a locked dial only on the same choice.
  if (ST.locks.dials.has("medium.erase") && !ST.locks.parts.has("medium")) B.medium.d.erase = old.medium.d.erase;
  return normBrush(B);
}
function randomize() {
  const B = rollBrush();
  commit(B, "Randomized", "Randomized: " + B.name);
  setStatus("Randomized.");
}
function pickStyle(style) {
  ST.stylesOpen = false;
  const B = rollBrush(style);
  commit(B, "Style: " + lc(STYLE_LABEL(style)), "Style: " + lc(STYLE_LABEL(style)));
}
// Undo: back to the history's step before this one (the same as clicking it there).
function undo() {
  const here = H.nodes.find(n => n.id === H.cur), up = here && H.nodes.find(n => n.id === here.parent);
  if (!up) return;
  const B = fromCode(up.code, up.name); if (!B) return;
  ST.prev = { B: ST.B, what: "Undo " + here.what };
  ST.B = B; H.cur = up.id; save(); refresh(); setStatus("Undone.");
}
function renderRollbar() {
  const here = H.nodes.find(n => n.id === H.cur), can = !!(here && H.nodes.some(n => n.id === here.parent));
  document.querySelectorAll("[data-undo]").forEach(b => { b.disabled = !can; });
  $("stylename").textContent = "Style: " + STYLE_LABEL(ST.B.style || WILDCARD);
  $("stylebtn").setAttribute("aria-expanded", String(!!ST.stylesOpen));
  const box = $("styles");
  box.hidden = !ST.stylesOpen;
  // In the app's order (ModularStyle.shown): Anything goes last, after Print.
  if (ST.stylesOpen) box.innerHTML = [...STYLE_NAMES.filter(n => n !== WILDCARD), WILDCARD].map(n => `<button type="button" class="chip" data-act="pickstyle" data-style="${escH(n)}" aria-pressed="${n === (ST.B.style || WILDCARD)}">${escH(STYLE_LABEL(n))}</button>`).join("");
  setTopH();
}

// ---------- refresh: what the brush shows everywhere ----------
function refresh() {
  renderIdent();
  renderRollbar();
  if (ST.tab === "lab") renderLab();
  else if (ST.tab === "tune") renderTune();
  else if (ST.tab === "mix") renderMix();
  else if (ST.tab === "roll") renderRoll();
  else if (ST.tab === "community") renderCommunity();
  if (ST.shareOpen) renderSharePanel();
}
function renderIdent() {
  const B = ST.B;
  $("idnametext").textContent = B.name; $("idname").title = "Rename " + B.name;
  $("words").textContent = brushWords(B);
  drawSample($("idloop"), "id", B, "loop", 0);
  renderSaveState();
}
// Save reads its state at a glance: solid while the brush has changes the library doesn't hold, "Saved" once it does.
const isSaved = () => { const code = encodeM(ST.B); return ST.lib.some(b => b.name === ST.B.name && encodeM(b) === code); };
const canRevert = () => !!ST.base && !(ST.base.code === encodeM(ST.B) && ST.base.name === ST.B.name);
function renderSaveState() {
  const saved = isSaved(), b = $("savebtn");
  b.classList.toggle("primary", !saved); b.classList.toggle("saved", saved);
  $("savetext").textContent = saved ? "Saved" : "Save"; $("saveico").hidden = !saved;
  $("revertbtn").disabled = !canRevert();
}
// What Revert returns to: the brush as last saved, or as it was loaded or opened.
function setBase(B) { ST.base = { code: encodeM(B), name: B.name }; }
function revert() {
  if (!canRevert()) return;
  const B = fromCode(ST.base.code, ST.base.name); if (!B) return;
  commit(B, "Reverted", "Reverted to " + B.name);
  setStatus("Reverted.");
}
function setTab(t) {
  ST.tab = t;
  document.querySelectorAll("[data-tab]").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.tab === t)));
  document.querySelectorAll(".screen").forEach(s => { s.hidden = s.dataset.screen !== t; });
  save();
  refresh();
  if (t === "lab") fitTest();
}

// ---------- 1 · Understand ----------
function renderLab() {
  validFocus();
  buildCards();
  renderTools();
  renderSweep();
  renderBA();
  scheduleReplay(false);
}
function validFocus() {
  const f = ST.focus, B = ST.B;
  if (isErase(f.part, f.key) || (B[f.part] && dialKeys(f.part, B[f.part].k).includes(f.key))) return;
  const keys = dialKeys(f.part, B[f.part].k);
  ST.focus = keys.length ? { part: f.part, key: keys[0] } : { part: "medium", key: dialKeys("medium", B.medium.k)[0] || "erase" };
}
function dialCtl(B, part, key) {
  const sp = DIALS[part][key], v = B[part].d[key], label = labelOf(B, part, key);
  if (sp.choices) return `<div class="opts" role="group" aria-label="${escH(label)}">` + sp.choices.map((o, i) =>
    `<button type="button" class="chip" data-act="opt" data-part="${part}" data-key="${key}" data-v="${i}" aria-pressed="${(v | 0) === i}">${escH(optText(part, B[part].k, key, i))}</button>`).join("") + `</div>`;
  const [lo, hi] = rangeFor(part, B[part].k, key), step = sp.int ? 1 : (hi - lo) / 1295;
  return `<input type="range" min="${lo}" max="${hi}" step="${step}" value="${v}" data-part="${part}" data-key="${key}" aria-label="${escH(label)}">`;
}
function dialHtml(B, part, key) {
  const sp = DIALS[part][key], f = ST.focus, foc = f.part === part && f.key === key, lk = dialLocked(part, key), label = labelOf(B, part, key);
  return `<div class="dial${isDef(B, part, key) ? " def" : ""}${foc ? " focus" : ""}" data-dial="${part}.${key}">` +
    `<button type="button" class="dname" data-act="focus" data-part="${part}" data-key="${key}">${escH(label)}</button>` +
    `<span class="dval" data-val="${part}.${key}">${sp.choices ? "" : escH(fmtV(part, key, B[part].d[key]))}</span>` +
    lockBtn("lockdial", part + "." + key, ST.locks.dials.has(part + "." + key), label) + dieBtn("rolldial", part, key, lk, label) +
    `<div class="ctl">${dialCtl(B, part, key)}</div></div>`;
}
// What each part does, one line under its header (docs/TEXT-STYLE.md: named, short, no "you").
// The part descriptions are the lab's (parts.js ROLE and ERASE_ROLE): one set for both.
const PART_DESC = Object.assign(Object.fromEntries(PARTS.map(p => [p, ROLE[p].blurb])), { erase: ERASE_ROLE.blurb });
function cardHtml(B, part) {
  const P = B[part], lockP = ST.locks.parts.has(part), name = ROLE[part].label;
  const choices = CHOICES[part].filter(c => !(part === "hand" && c.k === "custom" && P.k !== "custom"));
  const keys = dialKeys(part, P.k, B);
  return `<section class="card" data-part="${part}"><div class="chead"><span class="plabel">${escH(name)}</span><span class="cname">${escH(cardName(B, part))}</span><span class="grow"></span>` +
    lockBtn("lockpart", part, lockP, name) + dieBtn("rollpart", part, null, lockP, name) + `</div><p class="cdesc">${PART_DESC[part]}</p>` +
    `<div class="choices" role="group" aria-label="${escH(name)}">` + choices.map(c => `<button type="button" class="choice" data-act="choice" data-part="${part}" data-k="${escH(c.k)}" data-label="${escH(c.label)}" aria-pressed="${c.k === P.k}" aria-label="${escH(c.label)}" title="${escH(c.label)}">` +
      `<span class="frame"><canvas data-sw="${part}" data-k="${escH(c.k)}"></canvas></span><span class="clab">${escH(c.label)}</span></button>`).join("") + `</div>` +
    (keys.length ? `<div class="dials">${keys.map(k => dialHtml(B, part, k)).join("")}</div>` : "") + `</section>`;
}
// Erasable, the last dial of every medium, as a card of its own: its name focuses it.
function eraseCardHtml(B) {
  // Tone has no Erasable of its own: it always erases (the eraser scrapes it).
  if (!choiceOf("medium", B.medium.k).dials.includes("erase"))
    return `<section class="card" data-part="erase"><div class="chead"><span class="plabel">Erasable</span><span class="cname">Always</span><span class="grow"></span></div><p class="cdesc">${PART_DESC.erase}</p></section>`;
  const foc = ST.focus.part === "medium" && ST.focus.key === "erase", lk = dialLocked("medium", "erase");
  return `<section class="card" data-part="erase"><div class="chead"><button type="button" class="plabel dname" data-act="focus" data-part="medium" data-key="erase">Erasable</button>` +
    `<span class="cname" data-val="medium.erase">${escH(cap1s(fmtV("medium", "erase", B.medium.d.erase)))}</span><span class="grow"></span>` +
    lockBtn("lockdial", "medium.erase", ST.locks.dials.has("medium.erase"), "Erasable") + dieBtn("rolldial", "medium", "erase", lk, "Erasable") + `</div><p class="cdesc">${PART_DESC.erase}</p>` +
    `<div class="dials"><div class="dial solo${isDef(B, "medium", "erase") ? " def" : ""}${foc ? " focus" : ""}" data-dial="medium.erase">` +
    `<div class="ctl">${dialCtl(B, "medium", "erase")}</div></div></div></section>`;
}
// ---------- the Lab's layout: the cards packed into columns that fill the window's height, the rest for the page ----------
// The cards in the word list's order (Erasable after Medium, whose dial it is), cut into n columns in order so the
// tallest is as short as it can be; n is the fewest columns that fit the height, the test page column takes the rest.
const CARD_ORDER = ["tip", "medium", "erase", "paper", "life", "hand", "carry"];
const COL_GAP = 16, COL_W = 232, SIDE_MIN = 380;
function partition(hs, n) {
  const fits = T => { let k = 1, c = 0; for (const h of hs) { if (h > T) return false; if (c + h > T && c > 0) { k++; c = 0; } c += h; } return k <= n; };
  let lo = Math.max(...hs), hi = hs.reduce((a, b) => a + b, 0);
  while (hi - lo > 1) { const mid = (lo + hi) >> 1; if (fits(mid)) hi = mid; else lo = mid; }
  const T = fits(lo) ? lo : hi, cols = [[]]; let c = 0;
  hs.forEach((h, i) => { if (c + h > T && c > 0 && cols.length < n) { cols.push([]); c = 0; } cols[cols.length - 1].push(i); c += h; });
  return { cols, tallest: Math.max(...cols.map(col => col.reduce((a, i) => a + hs[i], 0))) };
}
// When the order can't be kept: the tallest card first into the shortest column, each column then in the order.
function balance(hs, n) {
  const cols = Array.from({ length: n }, () => []), tot = new Array(n).fill(0);
  [...hs.keys()].sort((a, b) => hs[b] - hs[a]).forEach(i => { const k = tot.indexOf(Math.min(...tot)); cols[k].push(i); tot[k] += hs[i]; });
  cols.forEach(c => c.sort((a, b) => a - b)); cols.sort((a, b) => a[0] - b[0]);
  return { cols: cols.filter(c => c.length), tallest: Math.max(...tot) };
}
function measureCards(html, w, labels) {
  const m = document.createElement("div");
  m.className = "cards" + (labels ? " labels" : "");
  m.style.cssText = `position:absolute;visibility:hidden;left:-10000px;top:0;display:block;width:${w}px`;
  m.innerHTML = `<div class="ccol">${html.join("")}</div>`;
  $("s-lab").appendChild(m);
  const hs = [...m.querySelectorAll(".card")].map(c => c.offsetHeight);
  m.remove();
  return hs;
}
function buildCards() {
  const B = ST.B, box = $("cards"), lab = $("s-lab");
  const html = CARD_ORDER.map(p => p === "erase" ? eraseCardHtml(B) : cardHtml(B, p));
  const W = lab.clientWidth || 1200, H = (lab.clientHeight || 700) - ($("adjhead").offsetHeight + 10), wide = innerWidth > 1180 && innerHeight > 620;
  let plan = null;
  if (wide) {
    // Labels under the swatches where there's room; the fewest columns whose tallest fits the height.
    // The fewest columns that hold every card within the height (so the cards stack to fill it), each as wide as
    // leaves the test page its room, with labels under the swatches where they fit; the test page column takes the
    // rest. Failing that, the plan whose tallest column is shortest.
    // A test page column much wider than the window is high is all sweep and no page: past 1.1 × the height the cards
    // take the width.
    const WIDTHS = [420, 380, 340, 300, 264, 232, 210, 192, 178], cache = new Map(), sideCap = Math.max(SIDE_MIN + 40, 1.1 * H);
    const hsAt = (w0, labels) => { const k = w0 + (labels ? "L" : ""); if (!cache.has(k)) cache.set(k, measureCards(html, w0, labels)); return cache.get(k); };
    let best = null;
    search: for (const side of [SIDE_MIN, 340]) for (let n = 2; n <= 8; n++) for (const w0 of WIDTHS) {
      const room = W - n * w0 - (n - 1) * COL_GAP - 24;
      if (room < side || room > sideCap) continue;
      for (const labels of innerWidth >= 1440 && w0 >= 230 ? [true, false] : [false]) {
        const hs = hsAt(w0, labels);
        let p = partition(hs, n);
        if (p.tallest > H) { const q = balance(hs, n); if (q.tallest < p.tallest) p = q; }
        const cand = { n, labels, w0, side, ...p };
        if (!best || cand.tallest < best.tallest) best = cand;
        if (p.tallest <= H) { plan = cand; break search; }
      }
    }
    plan ??= best || { n: 2, labels: false, w0: 178, side: SIDE_MIN, ...partition(measureCards(html, 178, false), 2) };
    const colW = plan.w0;
    plan.colW = colW;
    lab.style.gridTemplateColumns = `${plan.cols.length * colW + (plan.cols.length - 1) * COL_GAP}px minmax(${plan.side}px, 1fr)`;
  } else {
    lab.style.gridTemplateColumns = "";
    const w = box.clientWidth || W, n = w >= 700 ? Math.max(3, Math.min(5, Math.floor((w + COL_GAP) / 240))) : w >= 460 ? 2 : 1;
    plan = { n, labels: w / n >= 240, ...partition(measureCards(html, Math.floor((w - (n - 1) * COL_GAP) / n), false), n) };
  }
  box.classList.toggle("labels", plan.labels);
  box.style.gridTemplateColumns = `repeat(${plan.cols.length}, minmax(0, 1fr))`;
  box.innerHTML = plan.cols.map(col => `<div class="ccol">${col.map(i => html[i]).join("")}</div>`).join("");
  ST.layout = { n: plan.cols.length, labels: plan.labels, colW: plan.colW || 0 };
  drawSwatches();
}
function drawSwatches() {
  const B = ST.B;
  document.querySelectorAll("#cards canvas[data-sw]").forEach(c => {
    const part = c.dataset.sw, k = c.dataset.k;
    drawSample(c, "sw:" + part + ":" + k, withChoice(B, part, k), SWATCH_ROLE[part], 3);
  });
}
// During a drag, only the dial's own row changes on the page; the pictures follow.
function updateDialRow(part, key) {
  const B = ST.B, row = document.querySelector(`[data-dial="${part}.${key}"]`);
  if (row) row.classList.toggle("def", isDef(B, part, key));
  document.querySelectorAll(`[data-val="${part}.${key}"]`).forEach(v => { v.textContent = isErase(part, key) && v.classList.contains("cname") ? cap1s(fmtV(part, key, B[part].d[key])) : DIALS[part][key].choices ? "" : fmtV(part, key, B[part].d[key]); });
  const card = document.querySelector(`.card[data-part="${part}"] .cname`); if (card && !isErase(part, key)) card.textContent = cardName(B, part);
}
function setFocus(part, key, compare) {
  ST.focus = { part, key };
  document.querySelectorAll(".dial").forEach(d => d.classList.toggle("focus", d.dataset.dial === part + "." + key));
  // Focusing a dial without changing it: before is that dial at its default, or at the far end of its range when it
  // sits at its default; after is the brush as it is.
  if (compare) { focusCompare(); renderBA(); }
  renderSweep();
  save();
}
function focusCompare() {
  const { part, key } = ST.focus, B = ST.B, v = B[part].d[key], sp = DIALS[part][key], [lo, hi] = rangeFor(part, B[part].k, key);
  let b = defOf(B, part, key);
  if (isDef(B, part, key)) b = sp.choices ? ((v | 0) + 1) % sp.choices.length : (Math.abs(hi - v) > Math.abs(v - lo) ? hi : lo);
  const label = labelOf(B, part, key);
  ST.prev = { B: withDial(B, part, key, b), what: label + " " + fmtV(part, key, b, B[part].k) + " to " + fmtV(part, key, v, B[part].k) };
}
// ---------- the sweep: the focused dial in seven steps, the rest held still ----------
function sweepValues(B, part, key) {
  const sp = DIALS[part][key];
  if (sp.choices) return sp.choices.map((_, i) => i);
  const [lo, hi] = rangeFor(part, B[part].k, key);
  let vs = [0, 1, 2, 3, 4, 5, 6].map(i => lo + (hi - lo) * i / 6);
  if (sp.int) vs = [...new Set(vs.map(Math.round))];
  return vs;
}
// A sweep step is a brush to take, so a loop: tilting along it for a tilt dial (the loop is drawn upright otherwise), with
// Float's drops for a Float drying.
const sweepOpts = (B, part, key) => ({ tilt: part === "hand" && /^t/.test(key), drops: part === "life" && B.life.k === "float" });
function renderSweep() {
  const B = ST.B, { part, key } = ST.focus, vals = sweepValues(B, part, key), v = B[part].d[key], so = sweepOpts(B, part, key);
  $("sweeptitle").textContent = labelOf(B, part, key);
  $("sweeppart").textContent = isErase(part, key) ? "" : ROLE[part].label;
  const box = $("sweep"), sig = part + "." + key + ":" + vals.length;
  if (box.dataset.sig !== sig) {
    box.dataset.sig = sig;
    box.innerHTML = vals.map((x, i) => `<button type="button" class="tile" data-act="sweep" data-i="${i}"><span class="frame"><canvas></canvas></span><span class="tname"></span></button>`).join("");
  }
  const near = nearestStep(vals, v, DIALS[part][key].choices);
  box.querySelectorAll(".tile").forEach((t, i) => {
    t.dataset.v = vals[i]; t.setAttribute("aria-pressed", String(i === near));
    t.querySelector(".tname").textContent = fmtV(part, key, vals[i], B[part].k);
    t.setAttribute("aria-label", labelOf(B, part, key) + " " + fmtV(part, key, vals[i], B[part].k));
    drawSample(t.querySelector("canvas"), "sweep:" + i, withDial(B, part, key, vals[i]), "loop", 1, so);
  });
}
function nearestStep(vals, v, choices) {
  if (choices) return vals.indexOf(v | 0);
  let best = -1, bd = Infinity; const span = Math.abs(vals[vals.length - 1] - vals[0]) || 1;
  vals.forEach((x, i) => { const d = Math.abs(x - v); if (d < bd) { bd = d; best = i; } });
  return bd <= span / 12 + 1e-9 ? best : -1;
}
function updateSweepMarks() {
  const { part, key } = ST.focus, v = ST.B[part].d[key], tiles = [...document.querySelectorAll("#sweep .tile")], vals = tiles.map(t => +t.dataset.v);
  const near = nearestStep(vals, v, DIALS[part][key].choices);
  tiles.forEach((t, i) => t.setAttribute("aria-pressed", String(i === near)));
}
// ---------- before and after: the last change, on the test stroke, with the changed pixels ----------
const BA = { before: null, after: null, bare: null };
function renderBA() {
  const fb = $("before").parentElement, fa = $("after").parentElement; if (!fa.clientWidth) return;
  const P = ST.prev, before = P ? P.B : ST.B;
  $("bawhat").textContent = P ? P.what : "";
  request("ba:before", pathJob(before, fb, 0, true), r => { BA.before = r; drawBA(); }, 1);
  request("ba:after", pathJob(ST.B, fa, 0, true), r => { BA.after = r; drawBA(); }, 1);
  request("ba:bare", Object.assign(pathJob(ST.B, fa, 0, true), { bare: true }), r => { BA.bare = r; drawBA(); }, 1);
}
function drawBA() {
  const { before: b, after: a, bare } = BA; if (!b || !a) return;
  paint($("before"), b);
  const same = b.w === a.w && b.h === a.h;
  paint($("after"), a, ST.showDiff && same && ST.prev ? b : null);
  let changed = 0, marked = 0;
  if (same && bare && bare.w === a.w) for (let i = 0; i < a.gray.length; i++) {
    const m = a.gray[i] !== bare.gray[i] || b.gray[i] !== bare.gray[i];
    if (m) { marked++; if (a.gray[i] !== b.gray[i]) changed++; }
  }
  const pct = marked ? changed / marked * 100 : 0;
  $("aftercap").textContent = ST.prev ? "After: " + (changed && pct < 1 ? "under 1" : Math.round(pct)) + "% changed" : "After";
}
// ---------- the test page: draw on it with the brush; every stroke is drawn again when the brush changes ----------
const TP = { surf: null, ctx: null, log: [], live: null, water: null, bath: null, cost: 0, queued: false, last: null };
const TP_HOST = {
  halos: true,   // a Halo stroke's veins grow here ten times a second (newmods.js haloLift), not at once
  get water() { return TP.water; }, getWater() { return TP.water ??= new Water(TP.surf, 2026); },
  get bath() { return TP.bath; }, getBath() { if (!TP.bath || !TP.bath.wet) TP.bath = new Bath(TP.surf); return TP.bath; }
};
function fitTest() {
  const fr = $("testframe"), cv = $("test"); if (!fr.clientWidth) return;
  const w = Math.max(100, Math.round(fr.clientWidth * DPX)), h = Math.max(100, Math.round(fr.clientHeight * DPX));
  TP.ctx = cv.getContext("2d");
  if (TP.surf && TP.surf.w === w && TP.surf.h === h) return;
  cv.width = w; cv.height = h;
  TP.surf = modSurface(w, h, 2026);
  replayTest();
}
// Marks already on the page, so Opaque, Smudge and wet edges have something to work over (as the lab's test area).
function seedMarks(s) {
  const B0 = BASICS[0], wash = cloneBrush(BASICS[2]), w = s.w, h = s.h;
  wash.life = freshPart("life", "none"); normBrush(wash);
  const band = [], zig = [];
  for (let i = 0; i <= 60; i++) { const u = i / 60; band.push({ x: w * (.04 + .22 * u), y: h * .22 + Math.sin(u * 6) * 6, p: .8, t: i * 8, tx: NaN, ty: NaN }); }
  for (let i = 0; i <= 80; i++) { const u = i / 80; zig.push({ x: w * (.04 + .22 * u), y: h * .14 + (i % 20 < 10 ? (i % 10) : 10 - (i % 10)) * h * .009, p: .7, t: i * 6, tx: NaN, ty: NaN }); }
  layStroke(s, wash, band, Math.min(90, h * .12), .7, null);
  layStroke(s, B0, zig, 6, 1, null);
  s.base.set(s.page);
}
function printBath() { const b = TP.bath; if (b && b.wet) { const r = b.print(); if (r && TP.ctx) showPage(TP.ctx, TP.surf, r); } }
function replayTest() {
  const s = TP.surf; if (!s || !TP.ctx) return;
  const t0 = performance.now();
  blankPage(s); TP.water = null; TP.bath = null;
  seedMarks(s);
  for (const e of TP.log) {
    try {
      if (e.er) {
        printBath(); if (TP.water && TP.water.wet) TP.water.dryAll();
        const er = new Eraser(s, e.max, e.pen); for (const ev of e.evs) er.add(ev);
        continue;
      }
      if (ST.B.life.k !== "float") printBath();
      const st = new Stroke(s, ST.B, e.size, e.shade, TP_HOST);
      for (const ev of e.evs) st.add(ev);
      st.end();
    } catch (err) { console.error(err); }
  }
  if (TP.water && TP.water.wet) TP.water.dryAll();
  printBath();
  if (s.halos && s.halos.length && typeof haloDryAll === "function") haloDryAll(s);
  showPage(TP.ctx, s, [0, 0, s.w - 1, s.h - 1]);
  TP.cost = performance.now() - t0;
}
// Drawn again on the next frame; while a dial is being dragged, only if the last redraw was quick.
function scheduleReplay(dragging) {
  if (TP.live || (dragging && TP.cost > 140) || TP.queued) return;
  TP.queued = true;
  requestAnimationFrame(() => { TP.queued = false; if (!TP.live) replayTest(); });
}
// A real pen gives pressure and tilt; a mouse or finger gets pressure from speed (slower is heavier), upright (the lab's).
function penSample(e, L) {
  const cv = $("test"), r = cv.getBoundingClientRect(), x = (e.clientX - r.left) * cv.width / r.width, y = (e.clientY - r.top) * cv.height / r.height, isPen = e.pointerType === "pen";
  let p;
  if (isPen && e.pressure > 0) p = e.pressure;
  else {
    const dt = L.lastT ? Math.max(1, e.timeStamp - L.lastT) : 16, dd = L.lastX !== undefined ? Math.hypot(x - L.lastX, y - L.lastY) : 0;
    L.sp = L.sp * .8 + (dd / dt) * .2; L.d += dd;
    p = (.4 + .5 * smooth(L.d / 60)) * clamp(1.08 - .2 * L.sp, .45, 1);
  }
  L.lastT = e.timeStamp; L.lastX = x; L.lastY = y;
  const tilted = isPen && (e.tiltX || e.tiltY);
  return [x, y, p, e.timeStamp, tilted ? e.tiltX : NaN, tilted ? e.tiltY : NaN];
}
function wireTest() {
  const cv = $("test");
  cv.addEventListener("pointerdown", e => {
    if (!TP.surf) return;
    e.preventDefault();
    try { cv.setPointerCapture(e.pointerId); } catch (err) { /* synthetic events can't capture */ }
    const L = { sp: 0, d: 0 }, first = penSample(e, L), s = TP.surf;
    if (ST.eraser) {
      printBath(); if (TP.water && TP.water.wet) TP.water.dryAll();
      showPage(TP.ctx, s, [0, 0, s.w - 1, s.h - 1]);
      const max = ERASER_MAX[ST.sizeSel] || 120;
      L.er = new Eraser(s, max, e.pointerType === "pen"); L.entry = { er: true, max, pen: e.pointerType === "pen", evs: [[first]] };
      const r = L.er.add([first]); if (r) showPage(TP.ctx, s, r);
    } else {
      if (ST.B.life.k !== "float") printBath();
      if (ST.B.life.k === "float" && TP.water && TP.water.wet) { TP.water.dryAll(); showPage(TP.ctx, s, [0, 0, s.w - 1, s.h - 1]); }
      L.st = new Stroke(s, ST.B, sizePx(), shade(), TP_HOST); L.entry = { er: false, size: sizePx(), shade: shade(), evs: [[first]] };
      const r = L.st.add([first]); if (r) showPage(TP.ctx, s, r);
      // A resting pen sends nothing: Float's drop and Stipple's Build-up tick on their own while it is down.
      // Build-up's tick is the last sample again at the time, kept with the stroke's samples so a replay builds the same.
      if (L.st.fl || L.st.holds) L.hold = setInterval(() => {
        if (TP.live !== L) return;
        const q = L.st.holds && L.st.pts.lastRaw, t = performance.now();
        if (q) L.entry.evs.push([[q[0], q[1], q[2], t, q[4], q[5]]]);
        const rr = q ? L.st.add([[q[0], q[1], q[2], t, q[4], q[5]]]) : L.st.holdTick(t);
        if (rr) showPage(TP.ctx, s, rr);
      }, 50);
    }
    TP.live = L;
  });
  cv.addEventListener("pointermove", e => {
    const L = TP.live; if (!L) return;
    const evs = e.getCoalescedEvents ? e.getCoalescedEvents() : [e], samples = (evs.length ? evs : [e]).map(ev => penSample(ev, L));
    L.entry.evs.push(samples);
    const r = (L.er || L.st).add(samples); if (r) showPage(TP.ctx, TP.surf, r);
  });
  const end = () => {
    const L = TP.live; if (!L) return;
    TP.live = null; clearInterval(L.hold);
    TP.log.push(L.entry); if (TP.log.length > 60) TP.log.shift();
    if (L.er) return;
    const r = L.st.end(); if (r) showPage(TP.ctx, TP.surf, r);
    TP.last = L.entry; $("lastbtn").disabled = false;
    if (TP.water && TP.water.wet) tickWater(TP.water);
    if (TP.bath && TP.bath.wet) tickBath(TP.bath);
    if (TP.surf.halos && TP.surf.halos.length) tickHalos(TP.surf);
  };
  cv.addEventListener("pointerup", end); cv.addEventListener("pointercancel", end);
}
// Slow drying and Float keep working after lift, ten times a second.
function tickWater(W) {
  if (W !== TP.water || !W.wet) return;
  if (!TP.live) { W.step(); const d = W.takeDirty(); if (d) showPage(TP.ctx, TP.surf, d); }
  setTimeout(() => tickWater(W), 1000 / TPS);
}
function tickHalos(s) {
  if (s.studioTicking) return;
  s.studioTicking = true;
  const go = () => {
    if (TP.surf !== s || !(s.halos && s.halos.length)) { s.studioTicking = false; return; }
    if (!TP.live) {
      let r = null;
      for (const h of s.halos) { const d = h.step(); if (d) r = r ? union(r, d) : d; }
      s.halos = s.halos.filter(h => h.tick < h.N);
      if (r) showPage(TP.ctx, s, r);
    }
    setTimeout(go, 1000 / TPS);
  };
  setTimeout(go, 1000 / TPS);
}
function tickBath(b) {
  if (b !== TP.bath || !b.wet) return;
  if (!TP.live) { const r = b.tick(performance.now(), { x: 0, y: 0 }, b.drift ?? .5); if (r) showPage(TP.ctx, TP.surf, r); }
  setTimeout(() => tickBath(b), 1000 / TPS);
}
function renderTools() {
  const sizes = sizeList(ST.B);
  $("sizes").innerHTML = sizes.map((px, i) => `<button type="button" data-act="size" data-i="${i}" aria-pressed="${i === ST.sizeSel}">${mmText(px)}</button>`).join("");
  $("greys").innerHTML = GREY_STEPS.map((g, i) => { const v = quantTone(PAPER_TONE * (1 - g)); return `<button type="button" data-act="grey" data-i="${i}" aria-pressed="${i === ST.shadeSel}" aria-label="Grey ${Math.round(g * 100)}%" style="background: rgb(${v},${v},${v})"></button>`; }).join("");
  $("eraserbtn").setAttribute("aria-pressed", String(ST.eraser));
}
// ---------- dial edits ----------
function liveDial(part, key, v) {
  if (!ST.drag) {
    ST.drag = { from: cloneBrush(ST.B), part, key };
    if (ST.focus.part !== part || ST.focus.key !== key) setFocus(part, key, false);
  }
  const P = ST.B[part]; P.d[key] = v;
  if (part === "hand" && HAND_CELLS.includes(key)) P.k = matchHand(P);
  const label = labelOf(ST.B, part, key);
  ST.prev = { B: ST.drag.from, what: label + " " + fmtV(part, key, ST.drag.from[part].d[key], P.k) + " to " + fmtV(part, key, v, P.k) };
  updateDialRow(part, key); updateSweepMarks();
  $("words").textContent = brushWords(ST.B);
  renderIdent();
  scheduleReplay(true);
  renderBAThrottled();
}
let baTimer = 0, baLast = 0;
function renderBAThrottled() {
  const now = performance.now();
  if (now - baLast > 120) { baLast = now; renderBA(); return; }
  clearTimeout(baTimer); baTimer = setTimeout(() => { baLast = performance.now(); renderBA(); }, 130);
}
function commitDial() {
  const d = ST.drag; ST.drag = null; if (!d) return;
  const { part, key } = d, B = normBrush(ST.B), label = labelOf(B, part, key);
  ST.B = d.from;
  const k = B[part].k;
  commit(B, label + " " + fmtV(part, key, B[part].d[key], k), label + " " + fmtV(part, key, d.from[part].d[key], k) + " to " + fmtV(part, key, B[part].d[key], k));
}
function setDial(part, key, v) {
  const B = withDial(ST.B, part, key, v), label = labelOf(B, part, key);
  if (ST.focus.part !== part || ST.focus.key !== key) ST.focus = { part, key };
  const k = B[part].k;
  commit(B, label + " " + fmtV(part, key, B[part].d[key], k), label + " " + fmtV(part, key, ST.B[part].d[key], k) + " to " + fmtV(part, key, B[part].d[key], k));
}
function setChoice(part, k) {
  const old = ST.B[part].k; if (old === k) return;
  const B = withChoice(ST.B, part, k), name = ROLE[part].label;
  commit(B, name + ": " + lc(choiceLabel(part, k)), name + ": " + lc(choiceLabel(part, old)) + " to " + lc(choiceLabel(part, k)));
}

// ---------- 2 · Close-up ----------
const interp = (A, u) => { const x = clamp(u) * (A.length - 1), i = Math.min(A.length - 2, Math.floor(x)), t = x - i; return A[i] + (A[i + 1] - A[i]) * t; };
function defaultTune() {
  const geo = [], n = 160;
  for (let i = 0; i <= n; i++) { const u = i / n; geo.push({ x: 4 * u, y: .5 + .3 * Math.sin(2 * Math.PI * 1.25 * u) }); }
  TUNE.geo = geo;
  TUNE.P = Float32Array.from({ length: NK }, (_, i) => .15 + .75 * Math.pow(Math.sin(Math.PI * i / (NK - 1)), .7));
  TUNE.T = new Float32Array(NK);
  TUNE.V = Float32Array.from({ length: NK }, (_, i) => 70 + 110 * Math.sin(Math.PI * i / (NK - 1)));
  TUNE.A = new Float32Array(NK).fill(200 * DEG);
}
// The stroke's points: the shape, and at each its pressure, tilt and speed from the curves, by distance along it.
function buildTunePath() {
  const g = TUNE.geo, arc = [0];
  for (let i = 1; i < g.length; i++) arc.push(arc[i - 1] + Math.hypot(g[i].x - g[i - 1].x, g[i].y - g[i - 1].y));
  const L = arc[arc.length - 1] || 1;
  TUNE.arc = arc.map(a => a / L);
  TUNE.path = g.map((p, i) => { const u = TUNE.arc[i]; return { x: p.x, y: p.y, p: interp(TUNE.P, u), tilt: interp(TUNE.T, u), az: interp(TUNE.A, u), v: interp(TUNE.V, u) }; });
  TUNE.key = hashStr(TUNE.path.map(p => p.x.toFixed(3) + "," + p.y.toFixed(3) + "," + p.p.toFixed(3) + "," + p.tilt.toFixed(1) + "," + p.az.toFixed(2) + "," + Math.round(p.v)).join(";"));
}
// From the test page's last stroke: its shape, and its pressure, tilt and speed resampled along it.
function useLastStroke() {
  const e = TP.last; if (!e) return;
  const S = e.evs.flat().filter((s, i, a) => i === 0 || Math.hypot(s[0] - a[i - 1][0], s[1] - a[i - 1][1]) > .5);
  if (S.length < 3) { setStatus("Too short."); return; }
  const arc = [0]; for (let i = 1; i < S.length; i++) arc.push(arc[i - 1] + Math.hypot(S[i][0] - S[i - 1][0], S[i][1] - S[i - 1][1]));
  const L = arc[arc.length - 1]; if (L < 10) { setStatus("Too short."); return; }
  const at = u => { const d = u * L; let k = 1; while (k < S.length - 1 && arc[k] < d) k++; return k; };
  const P = new Float32Array(NK), T = new Float32Array(NK), V = new Float32Array(NK), A = new Float32Array(NK);
  for (let i = 0; i < NK; i++) {
    const u = i / (NK - 1), k = at(u), s = S[k], a = Math.max(0, k - 3), b = Math.min(S.length - 1, k + 3);
    P[i] = clamp(s[2]);
    const tl = tiltOf(s[4], s[5]); T[i] = tl ? clamp(tl.incl, 0, TILT_MAX) : 0; A[i] = tl && tl.dir !== null ? tl.dir : 200 * DEG;
    const dt = S[b][3] - S[a][3], dd = arc[b] - arc[a];
    V[i] = clamp(dt > 0 ? dd / dt * 1000 * 25.4 / 300 : 100, SPEED_MIN, SPEED_MAX);
  }
  for (let pass = 0; pass < 2; pass++) for (let i = 1; i < NK - 1; i++) V[i] = (V[i - 1] + 2 * V[i] + V[i + 1]) / 4;
  const step = Math.max(1, Math.floor(S.length / 300));
  TUNE.geo = S.filter((_, i) => i % step === 0 || i === S.length - 1).map(s => ({ x: s[0] / 300, y: s[1] / 300 }));
  Object.assign(TUNE, { P, T, V, A });
  buildTunePath(); save(); renderTune(); setStatus("Stroke taken.");
}
const CURVES = [
  { k: "P", name: "Pressure", lo: 0, hi: 1, fmt: v => v.toFixed(2) },
  { k: "T", name: "Tilt", lo: 0, hi: TILT_MAX, fmt: v => v < 1 ? "upright" : Math.round(v) + "°" },
  { k: "V", name: "Speed", lo: SPEED_MIN, hi: SPEED_MAX, fmt: v => Math.round(v) + " mm/s" }];
function buildCurves() {
  $("curves").innerHTML = CURVES.map(c => `<figure class="curve" data-c="${c.k}"><figcaption class="lab">${c.name}</figcaption>` +
    `<span class="frame"><svg viewBox="0 0 300 120" preserveAspectRatio="none" aria-label="${c.name} along the stroke"><line x1="0" y1="60" x2="300" y2="60" stroke="#dddddd" stroke-width="2" vector-effect="non-scaling-stroke"/>` +
    `<path fill="none" stroke="#000" stroke-width="3" vector-effect="non-scaling-stroke"/><line class="cl" y1="0" y2="120" stroke="#000" stroke-width="2" stroke-dasharray="4 4" vector-effect="non-scaling-stroke"/>` +
    `</svg><span class="knob" aria-hidden="true"></span></span><span class="cap cv"></span></figure>`).join("");
  document.querySelectorAll(".curve .frame").forEach(fr => wireCurve(fr, CURVES.find(c => c.k === fr.parentElement.dataset.c)));
}
const curveY = (c, v) => 110 - (v - c.lo) / (c.hi - c.lo) * 100;
function drawCurves() {
  for (const c of CURVES) {
    const fig = document.querySelector(`.curve[data-c="${c.k}"]`); if (!fig) continue;
    const A = TUNE[c.k], d = [...A].map((v, i) => (i ? "L" : "M") + (i / (NK - 1) * 300).toFixed(1) + " " + curveY(c, v).toFixed(1)).join(" ");
    fig.querySelector("path").setAttribute("d", d);
    const x = ST.cursor * 300, cl = fig.querySelector(".cl"); cl.setAttribute("x1", x); cl.setAttribute("x2", x);
    const v = interp(A, ST.cursor), knob = fig.querySelector(".knob");
    knob.style.cssText = `position:absolute;width:14px;height:14px;background:#000;left:calc(${ST.cursor * 100}% - 7px);top:calc(${curveY(c, v) / 120 * 100}% - 7px);pointer-events:none`;
    fig.querySelector(".cv").textContent = c.fmt(v) + " here";
  }
}
// Dragging on a curve pulls it toward the pen around where it is (a soft brush on the curve), then the stroke is drawn again.
function wireCurve(fr, c) {
  let on = false;
  const at = e => { const r = fr.getBoundingClientRect(); return [clamp((e.clientX - r.left) / r.width), clamp(1 - ((e.clientY - r.top) / r.height * 120 - 10) / 100)]; };
  const pull = e => {
    const [u, t] = at(e), target = c.lo + t * (c.hi - c.lo), A = TUNE[c.k];
    for (let i = 0; i < NK; i++) { const w = Math.exp(-.5 * Math.pow((i / (NK - 1) - u) / .06, 2)); A[i] += (target - A[i]) * w; }
    ST.cursor = u; buildTunePath(); drawCurves(); scheduleTuneRender();
  };
  fr.addEventListener("pointerdown", e => { e.preventDefault(); on = true; try { fr.setPointerCapture(e.pointerId); } catch (err) { /* synthetic */ } pull(e); });
  fr.addEventListener("pointermove", e => { if (on) pull(e); else { ST.cursor = at(e)[0]; drawCurves(); placeCursor(); drawClose(); } });
  const up = () => { if (on) { on = false; save(); renderTuneStrip(); } };
  fr.addEventListener("pointerup", up); fr.addEventListener("pointercancel", up);
}
let tuneQueued = false;
function scheduleTuneRender() { if (tuneQueued) return; tuneQueued = true; requestAnimationFrame(() => { tuneQueued = false; renderTuneStrip(); }); }
function renderTune() {
  if (!TUNE.path) buildTunePath();
  $("lastbtn").disabled = !TP.last;
  drawCurves();
  renderTuneStrip();
  renderHistory();
  renderCompare();
  document.querySelectorAll("#zooms button").forEach(b => b.setAttribute("aria-pressed", String(+b.dataset.z === ST.zoom)));
}
function renderTuneStrip() {
  const fr = $("stripframe"); if (!fr.clientWidth) return;
  const job = pathJob(ST.B, fr, sizePx(), false);
  TUNE.size = job.size;
  request("tune:strip", job, res => { TUNE.res = res; TUNE.fit = studioFit(TUNE.path, res.w, res.h, job.size); TUNE.pts = studioPathPts(TUNE.path, TUNE.fit); paint($("strip"), res); TUNE.off = null; placeCursor(); drawClose(); }, 1);
}
function cursorPoint() {
  if (!TUNE.pts || !TUNE.arc) return null;
  let k = 0; while (k < TUNE.arc.length - 1 && TUNE.arc[k] < ST.cursor) k++;
  return TUNE.pts[Math.min(k, TUNE.pts.length - 1)];
}
function placeCursor() {
  const p = cursorPoint(), fr = $("stripframe"), c = $("stripcursor"); if (!p || !TUNE.res) return;
  c.style.left = (p.x / TUNE.res.w * fr.clientWidth) + "px";
}
function wireStrip() {
  const fr = $("stripframe");
  const pickAt = e => {
    if (!TUNE.pts || !TUNE.res) return;
    const r = fr.getBoundingClientRect(), x = (e.clientX - r.left) / r.width * TUNE.res.w, y = (e.clientY - r.top) / r.height * TUNE.res.h;
    let best = 0, bd = Infinity; TUNE.pts.forEach((p, i) => { const d = (p.x - x) ** 2 + (p.y - y) ** 2; if (d < bd) { bd = d; best = i; } });
    ST.cursor = TUNE.arc[best]; drawCurves(); placeCursor(); drawClose();
  };
  fr.addEventListener("pointermove", pickAt); fr.addEventListener("pointerdown", pickAt);
}
// The close-up: the stroke's own pixels around the cursor, 1, 4 or 16 screen pixels a Manta pixel.
function drawClose() {
  const res = TUNE.res, cv = $("close"), fr = $("closeframe"); if (!res || !fr.clientWidth) return;
  if (!TUNE.off) {
    const o = document.createElement("canvas"); o.width = res.w; o.height = res.h;
    paint(o, res); TUNE.off = o;
  }
  const dpr = Math.min(3, window.devicePixelRatio || 1), cw = fr.clientWidth, ch = fr.clientHeight;
  if (cv.width !== Math.round(cw * dpr) || cv.height !== Math.round(ch * dpr)) { cv.width = Math.round(cw * dpr); cv.height = Math.round(ch * dpr); }
  const z = ST.zoom, vw = cw / z, vh = ch / z, p = cursorPoint() || { x: res.w / 2, y: res.h / 2 };
  const sx = vw >= res.w ? (res.w - vw) / 2 : clamp(Math.round(p.x - vw / 2), 0, res.w - vw), sy = vh >= res.h ? (res.h - vh) / 2 : clamp(Math.round(p.y - vh / 2), 0, res.h - vh);
  const ctx = cv.getContext("2d");
  ctx.imageSmoothingEnabled = false;
  ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, cv.width, cv.height);
  ctx.drawImage(TUNE.off, sx, sy, vw, vh, 0, 0, cv.width, cv.height);
  if (z >= 16) {
    ctx.fillStyle = "#dddddd";
    const k = z * dpr, ox = (Math.ceil(sx) - sx) * k, oy = (Math.ceil(sy) - sy) * k;
    for (let x = ox; x < cv.width; x += k) ctx.fillRect(Math.round(x), 0, 1, cv.height);
    for (let y = oy; y < cv.height; y += k) ctx.fillRect(0, Math.round(y), cv.width, 1);
  }
}
// ---------- history ----------
const timeOf = t => { const d = new Date(t); return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0"); };
function historyRows() {
  const kids = new Map(), ids = new Set(H.nodes.map(n => n.id));
  for (const n of H.nodes) { const p = ids.has(n.parent) ? n.parent : -1; if (!kids.has(p)) kids.set(p, []); kids.get(p).push(n); }
  const rows = [];
  // A node, then its branches (indented), then the line it carries on.
  const walk = (n, depth) => {
    for (;;) {
      rows.push([n, depth]);
      const k = kids.get(n.id) || [];
      for (const b of k.slice(1)) walk(b, depth + 1);
      if (!k.length) return;
      n = k[0];
    }
  };
  for (const r of kids.get(-1) || []) walk(r, 0);
  return rows;
}
function renderHistory() {
  const rows = historyRows(), box = $("hist");
  box.innerHTML = rows.map(([n, d]) => `<li><button type="button" data-act="node" data-id="${n.id}" class="${n.id === H.cur ? "cur" : ""}${ST.picked.includes(n.id) ? " picked" : ""}" style="padding-left:${10 + 22 * Math.min(d, 6)}px"${ST.comparePick ? ` aria-pressed="${ST.picked.includes(n.id)}"` : ""}>` +
    `<span class="frame"><canvas data-node="${n.id}"></canvas></span><span class="txt"><span class="what">${escH(n.what)}</span><span class="when">${escH(n.name)} · ${timeOf(n.at)}</span></span></button></li>`).join("");
  box.querySelectorAll("canvas[data-node]").forEach(c => {
    const n = H.nodes.find(x => x.id === +c.dataset.node), B = n && fromCode(n.code, n.name);
    if (B) drawSample(c, "node:" + n.id, B, "loop", 4);
  });
  // Bring the current step into view inside the history list only: scrollIntoView would scroll the page too, hiding the header.
  const cur = box.querySelector("button.cur");
  if (cur) {
    const top = cur.offsetTop - box.offsetTop, bottom = top + cur.offsetHeight;
    if (top < box.scrollTop) box.scrollTop = top;
    else if (bottom > box.scrollTop + box.clientHeight) box.scrollTop = bottom - box.clientHeight;
  }
  $("comparebtn").setAttribute("aria-pressed", String(ST.comparePick));
}
function jumpTo(id) {
  const n = H.nodes.find(x => x.id === id); if (!n) return;
  const B = fromCode(n.code, n.name); if (!B) return;
  ST.prev = { B: ST.B, what: "Back to " + n.what };
  ST.B = B; H.cur = id; save(); refresh();
}
function brushDiffs(A, B) {
  const a = [], b = [];
  for (const part of PART_ORDER) {
    if (A[part].k !== B[part].k) { a.push(ROLE[part].label + ": " + lc(choiceLabel(part, A[part].k))); b.push(ROLE[part].label + ": " + lc(choiceLabel(part, B[part].k))); continue; }
    for (const key of choiceOf(part, A[part].k).dials) {
      if (DIALS[part][key].hide) continue;
      const va = A[part].d[key], vb = B[part].d[key];
      const k = A[part].k;
      if (fmtV(part, key, va, k) === fmtV(part, key, vb, k)) continue;
      const l = labelOf(A, part, key); a.push(l + " " + fmtV(part, key, va, k)); b.push(l + " " + fmtV(part, key, vb, k));
    }
  }
  return [a, b];
}
function renderCompare() {
  const sec = $("comparesec"), [i, j] = ST.picked;
  if (ST.picked.length < 2) { sec.hidden = true; return; }
  const n1 = H.nodes.find(x => x.id === i), n2 = H.nodes.find(x => x.id === j);
  if (!n1 || !n2) { ST.picked = []; sec.hidden = true; return; }
  const A = fromCode(n1.code, n1.name), B = fromCode(n2.code, n2.name), [da, db] = brushDiffs(A, B);
  sec.hidden = false;
  const fig = (n, d, k) => `<figure><div class="who"><span class="frame"><canvas data-cmp="${k}l"></canvas></span><span class="txt"><b>${escH(n.name)}</b><br><span class="cap">${escH(n.what)} · ${timeOf(n.at)}</span></span></div>` +
    `<span class="frame quiet pic"><canvas data-cmp="${k}p"></canvas></span><ul>${d.length ? d.map(x => `<li>${escH(x)}</li>`).join("") : "<li>Same settings</li>"}</ul></figure>`;
  $("compare").innerHTML = fig(n1, da, "a") + fig(n2, db, "b");
  [[A, "a"], [B, "b"]].forEach(([X, k]) => {
    drawSample(document.querySelector(`canvas[data-cmp="${k}l"]`), "cmp:" + k + "l", X, "loop", 1);
    const c = document.querySelector(`canvas[data-cmp="${k}p"]`);
    request("cmp:" + k + "p", pathJob(X, c.parentElement, 0, true), r => paint(c, r), 1);
  });
}
function saveAsNew() {
  const B = cloneBrush(ST.B); B.name = cleanName(kinOf(ST.B.name, B.seed));
  ST.lib = ST.lib.filter(b => b.name !== B.name); ST.lib.unshift(B); setBase(B);
  commit(B, "Saved as " + B.name, "Saved as " + B.name);
  setStatus("Saved.");
}

// ---------- 3 · Mix ----------
const crossChoices = part => CHOICES[part].filter(c => c.k !== "custom");
function renderMix() {
  const opts = PART_ORDER.map(p => `<option value="${p}">${escH(ROLE[p].label)}</option>`).join("");
  for (const [id, k] of [["crossrow", "row"], ["crosscol", "col"]]) { const s = $(id); if (!s.options.length) s.innerHTML = opts; s.value = ST.cross[k]; }
  const B = ST.B, { row, col } = ST.cross, R = crossChoices(row), C = crossChoices(col), box = $("cross");
  // Wide: the tiles sized so the grid fits the window's height; narrow: they share the width.
  if (innerWidth > 1180 && innerHeight > 620) {
    const fit = innerHeight - $("top").offsetHeight - 32 - 64, wide = innerWidth - 48 - 100 - $("brushbar").offsetWidth;
    // Each tile as high as the rows allow and as wide as the columns do (up to twice its height), so the grid fills the window.
    const xt = Math.max(36, Math.min(220, Math.floor(fit / (R.length + .5) - 4))), xw = Math.max(xt, Math.min(2 * xt, Math.floor(wide / C.length - 4)));
    box.style.gridTemplateColumns = `minmax(48px, 88px) repeat(${C.length}, ${xw}px)`;
    box.style.setProperty("--xth", xt + "px"); box.classList.add("fill");
  } else { box.style.gridTemplateColumns = `minmax(48px, 110px) repeat(${C.length}, minmax(0, 168px))`; box.classList.remove("fill"); }
  let h = `<span></span>` + C.map(c => `<span class="ch">${escH(c.label)}</span>`).join("");
  for (const r of R) {
    h += `<span class="rh">${escH(r.label)}</span>`;
    for (const c of C) {
      const on = B[row].k === r.k && B[col].k === c.k;
      h += `<button type="button" class="tile" data-act="crosstry" data-r="${escH(r.k)}" data-c="${escH(c.k)}" aria-pressed="${on}" aria-label="${escH(r.label + " and " + c.label)}"><span class="frame"><canvas data-x="${escH(r.k)}|${escH(c.k)}"></canvas></span></button>`;
    }
  }
  box.innerHTML = h;
  box.querySelectorAll("canvas[data-x]").forEach(cv => {
    const [rk, ck] = cv.dataset.x.split("|");
    drawSample(cv, "x:" + rk + ":" + ck, crossed(B, row, rk, col, ck), "loop", 2);
  });
}
// ---------- 4 · Roll: twelve random brushes from this one ----------
function renderRoll() {
  renderLocks();
  if (!ST.wild.length) rollWild(); else renderWild();
}
function crossed(B, row, rk, col, ck) { return withChoice(withChoice(B, row, rk), col, ck); }
function renderLocks() {
  const items = PART_ORDER.map(p => [p, ROLE[p].label, ST.locks.parts.has(p)]).concat([["erase", "Erasable", ST.locks.dials.has("medium.erase")]]);
  $("wildlocks").innerHTML = items.map(([p, l, on]) => `<button type="button" class="chip" data-act="wildlock" data-part="${p}" aria-pressed="${on}">${on ? ICON.lock : ICON.open}${escH(l)}</button>`).join("");
}
function rollWild() { ST.wild = Array.from({ length: 12 }, () => wildRoll(ST.B, ST.locks)); renderWild(); }
function renderWild() {
  const key = brushKey(ST.B);
  $("wild").innerHTML = ST.wild.map((b, i) => `<button type="button" class="tile" data-act="wildtry" data-i="${i}" aria-pressed="${brushKey(b) === key}"><span class="frame"><canvas data-w="${i}"></canvas></span>` +
    `<span class="tname">${escH(b.name)}</span><span class="tcap">${escH(tileCaption(b))}</span></button>`).join("");
  $("wild").querySelectorAll("canvas[data-w]").forEach(c => drawSample(c, "wild:" + c.dataset.w, ST.wild[+c.dataset.w], "loop", 2));
}

// ---------- 5 · Share ----------
// ---------- Share: a panel from the brush bar ----------
function renderSharePanel() {
  const code = encodeM(ST.B);
  $("sharepanel").hidden = !ST.shareOpen; $("sharebtn").setAttribute("aria-expanded", String(!!ST.shareOpen));
  if (!ST.shareOpen) { COMM.confirm = false; return; }
  $("code").value = code;
  drawQr(code);
  // Beside the sidebar, level with the Share button.
  const p = $("sharepanel"); p.style.top = Math.max(0, $("sharebtn").offsetTop - 8) + "px";
  renderPublish();
}
// Inside claude.ai the "downloads" capability offers the file; opened as a plain file, a link saves it.
let DOWNLOADS = null;
if (window.claude && typeof window.claude.use === "function") window.claude.use("downloads").then(d => { DOWNLOADS = d; }).catch(() => {});
async function saveFile(file, blob) {
  if (DOWNLOADS) { try { await DOWNLOADS.save({ filename: file, data: blob }); } catch (e) { /* declined or unavailable */ } return; }
  const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = file; document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}
async function downloadForTablet() {
  const name = (ST.B.name || "brush").replace(/[\\/:*?"<>|]+/g, "").trim() || "brush", file = name + ".inkwash.txt";
  ST.lastDownload = file;
  await saveFile(file, new Blob([encodeM(ST.B) + "\n"], { type: "text/plain" }));
}
// Export (export.js, the brush export spec): a Procreate set of This brush and the library's Saved brushes, each once,
// drawn and zipped here in the page.
const exportList = () => { const seen = new Set(); return [ST.B, ...ST.lib].filter(b => { const k = b.name + "|" + encodeM(b); return !seen.has(k) && seen.add(k); }); };
async function exportSet() {
  const btn = $("exportbtn"); if (btn.disabled) return;
  btn.disabled = true;
  try {
    const list = exportList(), file = "Scribulor.brushset";
    const bytes = await procreateSet(list, "Scribulor", () => new Promise(r => setTimeout(r, 0)));
    ST.lastExport = { file, bytes: bytes.length, n: list.length };
    await saveFile(file, new Blob([bytes], { type: "application/octet-stream" }));
    setStatus("Set exported.");
  } catch (e) { setStatus("Export failed."); ST.lastExport = { error: String(e && e.stack || e) }; }
  finally { btn.disabled = false; }
}
// ---------- Community library: brushes people published, kept in the artifact's db (studio.NOTES.md) ----------
// One document a person, brushes/<their user id>: { list: [{ code, name, at }] }, newest first. Who published a brush
// is the document's id (the db's rules let each person write only their own); names come from user.profiles() when
// drawn and are never stored. Rows are other people's input: every code must read as a brush before it is shown,
// and everything shown is escaped. Without db (signed out, a saved copy of the page) the eight examples stay.
const COMM_MAX = 50;
const COMM = { state: "wait", db: null, user: null, me: null, readOnly: false, docs: new Map(), items: [], shown: [], names: {}, show: 48, confirm: false, busy: false, sig: "" };
const COMM_FATAL = new Set(["invalid_argument", "resource_exhausted", "quota_exceeded", "revoked", "not_granted", "capability_disabled", "capability_removed", "transform_error"]);
async function startCommunity() {
  const c = window.claude;
  let db = null, user = null;
  try { if (c && typeof c.use === "function") [db, user] = await Promise.all([c.use("db"), c.use("user")]); } catch (e) { db = null; }
  if (!db) { COMM.state = "off"; commChanged(); return; }
  COMM.db = db; COMM.user = user || null;
  try { COMM.me = user ? await user.id() : null; } catch (e) { COMM.me = null; }
  try { if (user && await user.can("data.write") === false) COMM.readOnly = true; } catch (e) { /* null: let a refused write decide */ }
  COMM.state = "live";
  subscribeCommunity(0);
  commChanged();
}
// Subscribed once, for the page's life (a fresh one only after the bridge itself stops answering).
function subscribeCommunity(tries) {
  let q;
  try { q = COMM.db.collection("brushes").limit(1000); } catch (e) { COMM.state = "off"; commChanged(); return; }
  q.onSnapshot(snap => { readCommunity(snap); commChanged(); }, err => {
    const code = err && err.code;
    if (!COMM_FATAL.has(code) && tries < 3) setTimeout(() => subscribeCommunity(tries + 1), 2000 + Math.random() * 2000);
    else { COMM.state = "off"; COMM.items = []; commChanged(); }
  });
}
// A person's list as stored, kept only where it reads: a code that decodes, a short name, a time.
function cleanList(body) {
  const raw = body && Array.isArray(body.list) ? body.list.slice(0, COMM_MAX) : [], out = [];
  for (const x of raw) {
    if (!x || typeof x !== "object" || typeof x.code !== "string" || x.code.length > 240) continue;
    const name = typeof x.name === "string" ? cleanName(x.name.slice(0, 64)) : "";
    const B = fromCode(x.code, name); if (!B || !B.name) continue;
    out.push({ code: x.code, name: B.name, at: Number.isFinite(x.at) ? x.at : 0, B });
  }
  return out;
}
function readCommunity(snap) {
  const docs = new Map(), items = [];
  for (const d of snap.docs) {
    if (!d.exists) continue;
    const list = cleanList(d.data());
    docs.set(d.id, list);
    for (const x of list) items.push(Object.assign({ uid: d.id, mine: !!COMM.me && d.id === COMM.me }, x));
  }
  items.sort((a, b) => b.at - a.at);
  COMM.docs = docs; COMM.items = items;
}
function commChanged() {
  if (ST.tab === "community") renderCommunity();
  if (ST.shareOpen) renderPublish();
}
const whoText = x => "by " + (COMM.names[x.uid] || (x.mine ? "me" : "someone"));
function renderCommunity() {
  const sg = $("sharedgrid"), live = COMM.state === "live" && COMM.items.length > 0;
  $("commtag").hidden = live;
  $("commnote").textContent = COMM.state === "off" ? "Sign in to see and share brushes." : COMM.state === "live" && !live ? "No brushes shared yet." : "";
  sg.classList.toggle("live", live);
  const list = live ? COMM.items.slice(0, COMM.show) : [];
  const sig = live ? list.map(x => x.uid + "|" + x.at + "|" + x.code + "|" + x.name + (x.mine ? "*" : "")).join("\n") + (COMM.items.length > COMM.show ? "+" : "") + (COMM.busy || COMM.readOnly ? "!" : "") : "examples";
  if (sig !== COMM.sig) {
    COMM.sig = sig; COMM.shown = list;
    sg.innerHTML = live ? list.map((x, i) => `<article><span class="frame"><canvas data-cm="${i}"></canvas></span><span class="tname">${escH(x.name)}</span>` +
      `<span class="who" data-i="${i}">${escH(whoText(x))}</span><span class="tcap">${escH(captionOf(x.B))}</span><span class="cacts"><button type="button" class="btn" data-act="commtry" data-i="${i}">Try</button>` +
      (x.mine && !COMM.readOnly ? `<button type="button" class="del" data-act="commremove" data-i="${i}"${COMM.busy ? " disabled" : ""}>Remove</button>` : "") + `</span></article>`).join("") +
      (COMM.items.length > COMM.show ? `<button type="button" class="btn more" data-act="commmore">Show more</button>` : "")
      : EXAMPLES.map((b, i) => `<article><span class="frame"><canvas data-ex="${i}"></canvas></span><span class="tname">${escH(b.name)}</span>` +
      `<span class="who">Example</span><span class="tcap">${escH(captionOf(b))}</span><button type="button" class="btn" data-act="extry" data-i="${i}">Try</button></article>`).join("");
  }
  // Eight in a row where they fit, else four (two rows), two on a phone.
  const w = $("s-community").clientWidth || innerWidth;
  sg.style.gridTemplateColumns = `repeat(${w / 8 >= 140 ? 8 : w >= 560 ? 4 : 2}, minmax(0, 1fr))`;
  sg.querySelectorAll("canvas[data-ex]").forEach(c => drawSample(c, "ex:" + c.dataset.ex, EXAMPLES[+c.dataset.ex], "loop", 3));
  sg.querySelectorAll("canvas[data-cm]").forEach(c => { const x = COMM.shown[+c.dataset.cm]; if (x) drawSample(c, "cm:" + c.dataset.cm, x.B, "loop", 3); });
  // Who: resolved as drawn (cheap by contract), set as text.
  if (live && COMM.user) {
    const ids = [...new Set(list.map(x => x.uid))];
    Promise.resolve(COMM.user.profiles(ids)).then(ps => {
      let moved = false;
      for (const id of ids) { const n = ps && ps[id] && typeof ps[id].name === "string" ? ps[id].name : ""; if (COMM.names[id] !== n) { COMM.names[id] = n; moved = true; } }
      if (moved) sg.querySelectorAll(".who[data-i]").forEach(el => { const x = COMM.shown[+el.dataset.i]; if (x) el.textContent = whoText(x); });
    }, () => { });
  }
}
// The Share panel's Publish to Community library: live when this viewer can write their own row.
function renderPublish() {
  const b = $("pubbtn"), conf = $("pubconfirm"), code = encodeM(ST.B);
  const can = COMM.state === "live" && !!COMM.me && !COMM.readOnly;
  const done = can && (COMM.docs.get(COMM.me) || []).some(x => x.code === code);
  b.disabled = !can || done || COMM.busy;
  b.textContent = done ? "Published" : "Publish to Community library";
  $("pubnote").textContent = COMM.state === "off" || (COMM.state === "live" && !COMM.me) ? "Sign in to share brushes." : COMM.readOnly ? "Contributor access needed." : "";
  if (!can || done) COMM.confirm = false;
  conf.hidden = !COMM.confirm; b.hidden = COMM.confirm;
}
const rawItem = x => ({ code: x.code, name: x.name, at: x.at });
async function retryOnce(f) {
  try { return await f(); } catch (e) {
    if (!e || !e.code || COMM_FATAL.has(e.code)) throw e;
    await new Promise(r => setTimeout(r, 300 + Math.random() * 700));
    return f();
  }
}
// One write to this viewer's own document, one at a time.
async function writeMine(list, done, failed) {
  if (COMM.busy) return;
  COMM.busy = true; COMM.confirm = false; commChanged();
  try {
    const ref = COMM.db.collection("brushes").doc(COMM.me);
    await retryOnce(() => list.length ? ref.set({ list }) : ref.delete());
    setStatus(done);
  } catch (e) {
    const code = e && e.code;
    if (code === "invalid_argument") COMM.readOnly = true;
    setStatus(code === "quota_exceeded" ? "Library full." : failed);
  } finally { COMM.busy = false; COMM.sig = ""; commChanged(); }
}
function publishBrush() {
  if (COMM.state !== "live" || !COMM.me || COMM.readOnly) return;
  const code = encodeM(ST.B), mine = (COMM.docs.get(COMM.me) || []).filter(x => x.code !== code);
  if (mine.length >= COMM_MAX) { COMM.confirm = false; renderPublish(); setStatus("At most " + COMM_MAX + " each."); return; }
  writeMine([{ code, name: cleanName(String(ST.B.name || "")), at: Date.now() }, ...mine.map(rawItem)], "Published.", "Not published.");
}
function removeBrush(i) {
  const x = COMM.shown[i]; if (!x || !x.mine || COMM.state !== "live") return;
  writeMine((COMM.docs.get(COMM.me) || []).filter(y => !(y.at === x.at && y.code === x.code)).map(rawItem), "Removed.", "Not removed.");
}
function drawQr(text) {
  const q = qrMatrix(text), cv = $("qr"); if (!q) return;
  const quiet = 4, sc = 8, n = (q.size + 2 * quiet) * sc;
  cv.width = cv.height = n;
  const ctx = cv.getContext("2d"); ctx.fillStyle = "#ffffff"; ctx.fillRect(0, 0, n, n); ctx.fillStyle = "#000000";
  for (let y = 0; y < q.size; y++) for (let x = 0; x < q.size; x++) if (q.dark[y * q.size + x]) ctx.fillRect((x + quiet) * sc, (y + quiet) * sc, sc, sc);
}
function loadCode(text) {
  const r = readAnyCode(text);
  if (!r) { $("loadnote").textContent = "That isn't a brush code."; return false; }
  $("loadnote").textContent = ""; $("library").hidden = true;
  setBase(r.b);
  commit(r.b, r.b.name, "Loaded " + r.b.name);
  setStatus("Loaded.");
  return true;
}
// A brush given in the link (studio.html#<share code>) opens as This brush, over whatever the page remembered.
function openHash() {
  let c = "";
  try { c = decodeURIComponent(location.hash.slice(1)); } catch (e) { /* not a code */ }
  // The same brush again (the page reloaded) adds nothing to its history.
  if (c && encodeM(ST.B) !== c && !loadCode(c)) setStatus("That isn't a brush code.");
}
function copyCode() {
  const t = $("code").value, done = () => setStatus("Copied.");
  const fallback = () => { const i = $("code"); i.focus(); i.select(); try { document.execCommand("copy"); done(); } catch (e) { setStatus("Select and copy."); } };
  try { if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t).then(done, fallback); else fallback(); } catch (e) { fallback(); }
}

// ---------- the library ----------
function openLibrary() {
  const item = (b, i, saved) => `<div class="libitem"><button type="button" class="tile" data-act="libload" data-src="${saved ? "s" : "b"}" data-i="${i}"><span class="frame"><canvas data-lib="${saved ? "s" : "b"}${i}"></canvas></span>` +
    `<span class="tname">${escH(b.name)}</span><span class="tcap">${escH(captionOf(b))}</span></button>${saved ? `<button type="button" class="del" data-act="libdel" data-i="${i}">Delete</button>` : ""}</div>`;
  $("libbody").innerHTML = (ST.lib.length ? `<div class="libgroup"><span class="lab">Saved</span><div class="libgrid">${ST.lib.map((b, i) => item(b, i, true)).join("")}</div></div>` : "") +
    `<div class="libgroup"><span class="lab">Basic tools</span><div class="libgrid">${BASICS.map((b, i) => item(b, i, false)).join("")}</div></div>`;
  $("library").hidden = false;
  $("libbody").querySelectorAll("canvas[data-lib]").forEach(c => { const s = c.dataset.lib, list = s[0] === "s" ? ST.lib : BASICS, b = list[+s.slice(1)]; drawSample(c, "lib:" + s, b, "loop", 1); });
}
function saveToLibrary() {
  const B = cloneBrush(ST.B);
  ST.lib = ST.lib.filter(b => b.name !== B.name); ST.lib.unshift(B);
  setBase(B); save(); renderSaveState(); setStatus("Saved.");
}

// ---------- events ----------
const ACT = {
  randomize: () => randomize(),
  revert: () => revert(),
  undo: () => undo(),
  styles: () => { ST.stylesOpen = !ST.stylesOpen; renderRollbar(); },
  pickstyle: t => pickStyle(t.dataset.style),
  library: () => openLibrary(),
  libclose: () => { $("library").hidden = true; },
  libload: t => { const b = (t.dataset.src === "s" ? ST.lib : BASICS)[+t.dataset.i]; $("library").hidden = true; if (b) { setBase(b); commit(cloneBrush(b), b.name, "Loaded " + b.name); } },
  libdel: t => { ST.lib.splice(+t.dataset.i, 1); save(); openLibrary(); },
  save: () => saveToLibrary(),
  rename: () => { const i = $("idinput"); i.value = ST.B.name; i.hidden = false; $("idname").hidden = true; i.focus(); i.select(); },
  clear: () => { TP.log = []; TP.last = null; replayTest(); setStatus("Cleared."); },
  eraser: () => { ST.eraser = !ST.eraser; renderTools(); },
  size: t => { ST.sizeSel = +t.dataset.i; ST.eraser = false; renderTools(); save(); },
  grey: t => { ST.shadeSel = +t.dataset.i; ST.eraser = false; save(); refresh(); },
  focus: t => setFocus(t.dataset.part, t.dataset.key, true),
  choice: t => setChoice(t.dataset.part, t.dataset.k),
  opt: t => setDial(t.dataset.part, t.dataset.key, +t.dataset.v),
  sweep: t => setDial(ST.focus.part, ST.focus.key, +t.dataset.v),
  lockpart: t => { const p = t.dataset.id; ST.locks.parts.has(p) ? ST.locks.parts.delete(p) : ST.locks.parts.add(p); save(); buildCards(); },
  lockdial: t => { const id = t.dataset.id; ST.locks.dials.has(id) ? ST.locks.dials.delete(id) : ST.locks.dials.add(id); save(); buildCards(); },
  rollpart: t => { const p = t.dataset.part, B = rollOnePart(ST.B, p, ST.locks); commit(B, ROLE[p].label + ": " + lc(cardName(B, p)), ROLE[p].label + " rolled"); },
  rolldial: t => {
    const { part, key } = t.dataset;
    let B;
    if (isErase(part, key)) { B = cloneBrush(ST.B); B.medium.d.erase = Math.random(); normBrush(B); } else B = rollOneDial(ST.B, part, key);
    ST.focus = { part, key };
    const l = labelOf(B, part, key);
    const k = B[part].k;
    commit(B, l + " " + fmtV(part, key, B[part].d[key], k), l + " " + fmtV(part, key, ST.B[part].d[key], k) + " to " + fmtV(part, key, B[part].d[key], k));
  },
  laststroke: () => useLastStroke(),
  zoom: t => { ST.zoom = +t.dataset.z; document.querySelectorAll("#zooms button").forEach(b => b.setAttribute("aria-pressed", String(+b.dataset.z === ST.zoom))); save(); drawClose(); },
  node: t => {
    const id = +t.dataset.id;
    if (ST.comparePick) {
      ST.picked = ST.picked.includes(id) ? ST.picked.filter(x => x !== id) : [...ST.picked, id].slice(-2);
      if (ST.picked.length === 2) ST.comparePick = false;
      renderHistory(); renderCompare(); return;
    }
    jumpTo(id);
  },
  comparepick: () => { ST.comparePick = !ST.comparePick; if (ST.comparePick) ST.picked = []; renderHistory(); renderCompare(); },
  compareoff: () => { ST.picked = []; ST.comparePick = false; renderHistory(); renderCompare(); },
  saveasnew: () => saveAsNew(),
  crosstry: t => {
    const { row, col } = ST.cross, B = crossed(ST.B, row, t.dataset.r, col, t.dataset.c), bits = [];
    for (const p of [row, col]) if (B[p].k !== ST.B[p].k) bits.push(ROLE[p].label + ": " + lc(choiceLabel(p, B[p].k)));
    if (bits.length) commit(B, bits.join(", "), bits.join(", "));
  },
  wild: () => rollWild(),
  wildlock: t => { const p = t.dataset.part; if (p === "erase") { ST.locks.dials.has("medium.erase") ? ST.locks.dials.delete("medium.erase") : ST.locks.dials.add("medium.erase"); } else ST.locks.parts.has(p) ? ST.locks.parts.delete(p) : ST.locks.parts.add(p); save(); renderLocks(); },
  wildtry: t => { const b = ST.wild[+t.dataset.i]; if (b) { setBase(b); commit(cloneBrush(b), b.name, "Tried " + b.name); renderWild(); } },
  commtry: t => { const x = COMM.shown[+t.dataset.i]; if (x) { const B = cloneBrush(x.B); setBase(B); commit(B, B.name, "Tried " + B.name); setTab("lab"); } },
  commremove: t => removeBrush(+t.dataset.i),
  commmore: () => { COMM.show += 48; renderCommunity(); },
  publish: () => { COMM.confirm = true; renderPublish(); },
  pubyes: () => publishBrush(),
  pubno: () => { COMM.confirm = false; renderPublish(); },
  extry: t => { const b = EXAMPLES[+t.dataset.i]; if (b) { setBase(b); commit(cloneBrush(b), b.name, "Tried " + b.name); setTab("lab"); } },
  copy: () => copyCode(),
  download: () => downloadForTablet(),
  export: () => exportSet(),
  share: () => { ST.shareOpen = !ST.shareOpen; renderSharePanel(); },
  paste: () => { try { navigator.clipboard.readText().then(t => { $("paste").value = t; loadCode(t); }, () => setStatus("Paste the code.")); } catch (e) { setStatus("Paste the code."); } },
  loadcode: () => loadCode($("paste").value)
};
function finishRename() {
  const i = $("idinput"); if (i.hidden) return;
  const name = cleanName(i.value || "");
  i.hidden = true; $("idname").hidden = false;
  if (!name || name === ST.B.name) return;
  const B = cloneBrush(ST.B); B.name = name;
  commit(B, "Named " + name, "Named " + name);
}
function wire() {
  document.addEventListener("click", e => {
    const t = e.target.closest("[data-act]"); if (t && ACT[t.dataset.act] && !t.disabled) { ACT[t.dataset.act](t); return; }
    const tab = e.target.closest("[data-tab]"); if (tab) setTab(tab.dataset.tab);
    if (e.target === $("library")) $("library").hidden = true;
  });
  document.addEventListener("input", e => { const t = e.target; if (t.type === "range" && t.dataset.part) liveDial(t.dataset.part, t.dataset.key, +t.value); });
  document.addEventListener("change", e => {
    const t = e.target;
    if (t.type === "range" && t.dataset.part) commitDial();
    else if (t.id === "showdiff") { ST.showDiff = t.checked; save(); drawBA(); }
    else if (t.id === "crossrow" || t.id === "crosscol") {
      const k = t.id === "crossrow" ? "row" : "col", other = k === "row" ? "col" : "row";
      if (ST.cross[other] === t.value) ST.cross[other] = ST.cross[k];
      ST.cross[k] = t.value; save(); renderMix();
    }
  });
  document.addEventListener("keydown", e => {
    if (e.target.id === "idinput" && e.key === "Enter") finishRename();
    if (e.target.id === "idinput" && e.key === "Escape") { $("idinput").hidden = true; $("idname").hidden = false; }
    if (e.target.id === "paste" && e.key === "Enter") loadCode($("paste").value);
    if (e.key === "Escape" && !$("library").hidden) $("library").hidden = true;
    if (e.key === "Escape" && ST.shareOpen) { ST.shareOpen = false; renderSharePanel(); }
  });
  $("idinput").addEventListener("blur", finishRename);
  // A swatch under the pointer names itself in its card's header (grey), the choice in use comes back after.
  const peek = (e, on) => {
    const c = e.target.closest && e.target.closest(".choice"); if (!c) return;
    const name = c.closest(".card").querySelector(".cname"); if (!name) return;
    if (on && c.getAttribute("aria-pressed") !== "true") { name.textContent = c.dataset.label; name.classList.add("peek"); }
    else { name.textContent = cardName(ST.B, c.dataset.part); name.classList.remove("peek"); }
  };
  $("cards").addEventListener("pointerover", e => peek(e, true));
  $("cards").addEventListener("pointerout", e => peek(e, false));
  document.querySelectorAll(".ico[data-icon]").forEach(e => { e.innerHTML = ICON[e.dataset.icon] || ""; });
  $("zooms").innerHTML = [1, 4, 16].map(z => `<button type="button" data-act="zoom" data-z="${z}" aria-pressed="${z === ST.zoom}">${z}×</button>`).join("");
  $("showdiff").checked = ST.showDiff;
  wireTest(); wireStrip(); buildCurves();
  // Laid out again: pictures that depend on a frame's size are drawn again.
  let rt = 0;
  const relayout = () => { setTopH(); clearTimeout(rt); rt = setTimeout(() => { if (ST.tab === "lab") { fitTest(); renderSweep(); renderBA(); drawSwatches(); } refresh(); }, 150); };
  if (typeof ResizeObserver !== "undefined") new ResizeObserver(relayout).observe($("main")); else window.addEventListener("resize", relayout);
  setTopH();
}
// The height above a screen: the bar and the brush bar (each tab fits the window below them).
function setTopH() { document.documentElement.style.setProperty("--toph", $("top").offsetHeight + "px"); }

// ---------- start ----------
async function start() {
  BASICS = basicTools(); EXAMPLES = exampleBrushes();
  if (!loadSaved()) ST.B = cloneBrush(BASICS[3]);
  if (!ST.base) setBase(ST.B);
  addFavourites();
  if (!TUNE.geo) defaultTune();
  buildTunePath();
  if (!H.nodes.length) addNode(ST.B.name);
  wire();
  document.querySelectorAll("[data-tab]").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.tab === ST.tab)));
  document.querySelectorAll(".screen").forEach(s => { s.hidden = s.dataset.screen !== ST.tab; });
  $("lastbtn").disabled = true;
  validFocus(); focusCompare();
  await loadTextures();
  startPool();
  fitTest();
  openHash();
  window.addEventListener("hashchange", openHash);
  refresh();
  window.STUDIO = { ST, H, TP, TUNE, POOL, exportList, randomize, undo, revert, isSaved, setTab, setFocus, setDial, setChoice, loadCode, commit, refresh, encodeM, readAnyCode, wildRoll, replayTest, useLastStroke, BASICS };
  window.STUDIO.COMM = COMM;
  document.documentElement.dataset.ready = "1";
  startCommunity();
}
start().catch(e => console.error(e));
