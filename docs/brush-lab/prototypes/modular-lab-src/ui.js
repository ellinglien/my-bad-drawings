// ===================================================================================================================
// Modular Brush Lab: the screen. Concept A, the semi-modular pen: a fixed column of part cards beside a big test area.
// ===================================================================================================================
const DPX = 1.875;                 // device pixels per dp on both Supernotes (300 ppi)
const DEVICES = { manta: { name: "Manta", w: 1024, h: 1365, px: "1920 × 2560" }, nomad: { name: "Nomad", w: 749, h: 998, px: "1404 × 1872" } };
Object.assign(ICON, {
  dice: '<path d="M192,28H64A36,36,0,0,0,28,64V192a36,36,0,0,0,36,36H192a36,36,0,0,0,36-36V64A36,36,0,0,0,192,28Zm12,164a12,12,0,0,1-12,12H64a12,12,0,0,1-12-12V64A12,12,0,0,1,64,52H192a12,12,0,0,1,12,12ZM104,88A16,16,0,1,1,88,72,16,16,0,0,1,104,88Zm40,40a16,16,0,1,1-16-16A16,16,0,0,1,144,128Zm40-40a16,16,0,1,1-16-16A16,16,0,0,1,184,88Zm-80,80a16,16,0,1,1-16-16A16,16,0,0,1,104,168Zm80,0a16,16,0,1,1-16-16A16,16,0,0,1,184,168Z"/>',
  "caret-left": '<path d="M168.49,199.51a12,12,0,0,1-17,17l-80-80a12,12,0,0,1,0-17l80-80a12,12,0,0,1,17,17L97,128Z"/>',
  "caret-right": '<path d="M184.49,136.49l-80,80a12,12,0,0,1-17-17L159,128,87.51,56.49a12,12,0,1,1,17-17l80,80A12,12,0,0,1,184.49,136.49Z"/>',
  "caret-down": '<path d="M216.49,104.49l-80,80a12,12,0,0,1-17,0l-80-80a12,12,0,0,1,17-17L128,159l71.51-71.52a12,12,0,0,1,17,17Z"/>',
  x: '<path d="M208.49,191.51a12,12,0,0,1-17,17L128,145,64.49,208.49a12,12,0,0,1-17-17L111,128,47.51,64.49a12,12,0,0,1,17-17L128,111l63.51-63.52a12,12,0,0,1,17,17L145,128Z"/>'
});
ICON.drop = "<path fill-rule=\"evenodd\" d=\"M128 16C100 56 48 108 48 158a80 80 0 0 0 160 0C208 108 156 56 128 16ZM128 58C106 90 74 126 74 158a54 54 0 0 0 108 0C182 126 150 90 128 58Z\"/>";
const svg = name => '<svg viewBox="0 0 256 256" aria-hidden="true">' + (ICON[name] || "") + "</svg>";
// Each part's glyph, so a card reads in grey by position, pattern and shape.
const GLYPH = {
  tip: '<path d="M12 2 L18 11 L13.2 22 h-2.4 L6 11 Z M12 9 v6" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><circle cx="12" cy="11" r="1.6"/>',
  medium: '<path d="M12 2.5 C8 8.5 5.5 11.5 5.5 15 a6.5 6.5 0 0 0 13 0 C18.5 11.5 16 8.5 12 2.5Z" fill="none" stroke="currentColor" stroke-width="2"/>',
  carry: '<path d="M3 15 C7 9 11 9 14 12 S19 15 21 9" fill="none" stroke="currentColor" stroke-width="2"/><path d="M17 8 l4 1 -1 4" fill="none" stroke="currentColor" stroke-width="2"/><path d="M3 20 h8" stroke="currentColor" stroke-width="2"/>',
  paper: '<rect x="4" y="3" width="16" height="18" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="9" cy="9" r="1.2"/><circle cx="15" cy="9" r="1.2"/><circle cx="12" cy="13" r="1.2"/><circle cx="9" cy="17" r="1.2"/><circle cx="15" cy="17" r="1.2"/>',
  life: '<path d="M6 3 h12 M6 21 h12 M7 3 C7 9 17 10 17 12 S7 15 7 21 M17 3 C17 9 7 10 7 12 S17 15 17 21" fill="none" stroke="currentColor" stroke-width="2"/>',
  hand: '<path d="M7 21 V11 a1.6 1.6 0 0 1 3.2 0 V5 a1.6 1.6 0 0 1 3.2 0 V10 a1.6 1.6 0 0 1 3.2 0 V12 a1.6 1.6 0 0 1 3.2 0 V16 c0 3 -2 5 -5 5Z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/>'
};
const glyph = part => `<span class="glyph"><svg viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">${GLYPH[part]}</svg></span>`;
const $ = id => document.getElementById(id);
const esc = t => String(t).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

// ---------- the viewer ----------
const V = { device: "manta", land: false, zoom: "width" };
function dims() { const d = DEVICES[V.device]; return V.land ? { W: d.h, H: d.w } : { W: d.w, H: d.h }; }
const BEZEL = 22;
function fitStage() {
  const { W, H } = dims(), NW = W + 2 * BEZEL, NH = H + 2 * BEZEL;
  const availW = Math.max(200, document.documentElement.clientWidth - 32), availH = Math.max(300, window.innerHeight - 24);
  let s = 1;
  if (V.zoom === "width") s = Math.min(1, availW / NW);
  else if (V.zoom === "screen") s = Math.min(1, availW / NW, availH / NH);
  else s = 1;
  const dev = $("device"), stage = $("stage");
  dev.style.width = NW + "px"; dev.style.height = NH + "px"; dev.style.transform = `scale(${s})`;
  $("screen").style.width = W + "px"; $("screen").style.height = H + "px";
  stage.style.width = NW * s + "px"; stage.style.height = NH * s + "px";
  $("caption").textContent = `${DEVICES[V.device].name}, ${V.land ? "landscape" : "portrait"}: ${W} × ${H} dp (${DEVICES[V.device].px} px). Shown at ${Math.round(s * 100)}%.`;
}
function viewerButtons() {
  const set = (id, v) => $(id).querySelectorAll("button").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.v === v)));
  set("vdev", V.device); set("vori", V.land ? "landscape" : "portrait"); set("vzoom", V.zoom);
}
function initViewer() {
  const on = (id, f) => $(id).addEventListener("click", e => { const b = e.target.closest("button"); if (b) { f(b.dataset.v); viewerButtons(); saveState(); } });
  on("vdev", v => { V.device = v; buildLab(); });
  on("vori", v => { V.land = v === "landscape"; buildLab(); });
  on("vzoom", v => { V.zoom = v; fitStage(); });
  $("vchk").addEventListener("click", e => { const b = e.target.closest("button"); if (b) b.dataset.v === "checks" ? runChecks() : runBench(); });
  let t = 0; window.addEventListener("resize", () => { clearTimeout(t); t = setTimeout(fitStage, 80); });
  viewerButtons();
}

// ---------- the lab's state: it always works on the picked slot ----------
const S = {
  mode: "simple", slots: [], sel: 0, history: [], library: [], vars: [], open: null, swap: null,
  locks: { parts: new Set(), dials: new Set() }, sizes: [4, 16, 120], sizeSel: 1, swatches: [1, .53, .3], swSel: 0,
  codeOpen: false, renaming: false, styleOpen: false, libPage: 0, picker: null, status: "", loadNote: ""
};
const NO_LOCKS = { parts: new Set(), dials: new Set() };
const locks = () => S.mode === "advanced" ? S.locks : NO_LOCKS;
const cur = () => S.slots[S.sel];
const setCur = b => { S.slots[S.sel] = b; };
let BUILTINS = [];
const library = () => [...BUILTINS, ...S.library];
const sameBrush = (a, b) => !!a && !!b && a.name === b.name && brushKey(a) === brushKey(b);
function remember() { S.history.push({ slots: S.slots.slice(), sel: S.sel }); if (S.history.length > 60) S.history.shift(); }
// Before a change: a built-in becomes the lab's own copy ("my-ink"). Returns the brush to change.
function edit() {
  remember();
  const b = cur(), c = cloneBrush(b);
  if (b.builtin) { delete c.builtin; delete c.blurb; c.name = "my-" + b.name.toLowerCase(); }
  setCur(c); return c;
}
function setStatus(t) { S.status = t; const s = $("status"); if (s) { s.textContent = t; s.title = t; } }
function changed(msg, keepVars) {
  setEraser(false);
  if (S.mode === "advanced" && !keepVars) S.vars = makeVars(cur(), locks());
  saveState(); renderLab(); redrawSample();
  if (msg) setStatus(msg);
}
// ---------- remembered between visits (localStorage, best effort) ----------
const STORE = "inkwash.modlab.v1";
function saveState() {
  try {
    localStorage.setItem(STORE, JSON.stringify({ lib: S.library.map(b => [encodeM(b), b.name]), slots: S.slots.map(b => b.builtin ? "@" + b.name : [encodeM(b), b.name]), sel: S.sel, mode: S.mode,
      sizes: S.sizes, sizeSel: S.sizeSel, swatches: S.swatches, swSel: S.swSel, device: V.device, land: V.land, zoom: V.zoom }));
  } catch (e) { /* storage off: the lab still works, it just won't remember */ }
}
function loadState() {
  let o = null;
  try { o = JSON.parse(localStorage.getItem(STORE) || "null"); } catch (e) { o = null; }
  if (!o) return false;
  try {
    // A code carries a slug of the name; the name as shown ("Karl Dandleton") is kept beside it.
    const read = c => { if (typeof c === "string" && c[0] === "@") return BUILTINS.find(b => b.name === c.slice(1)) || null;
      const [code, name] = Array.isArray(c) ? c : [c, null], r = readAnyCode(code); if (!r) return null; if (name) r.b.name = cleanName(String(name)); return r.b; };
    const lib = (o.lib || []).map(read).filter(Boolean), slots = (o.slots || []).map(read);
    if (slots.length !== 6 || slots.some(s => !s)) return false;
    S.library = lib; S.slots = slots; S.sel = clamp(o.sel | 0, 0, 5); S.mode = o.mode === "advanced" ? "advanced" : "simple";
    if (Array.isArray(o.sizes) && o.sizes.length === 3) S.sizes = o.sizes.map(v => clamp(+v || 16, 1, 360));
    if (Array.isArray(o.swatches) && o.swatches.length === 3) S.swatches = o.swatches.map(v => clamp(+v, 0, 1));
    S.sizeSel = clamp(o.sizeSel | 0, 0, 2); S.swSel = clamp(o.swSel | 0, 0, 2);
    if (o.device in DEVICES) V.device = o.device; V.land = !!o.land; if (["width", "screen", "real"].includes(o.zoom)) V.zoom = o.zoom;
    return true;
  } catch (e) { return false; }
}
// ---------- a starting library: the four built-ins and a couple of dozen rolled brushes, the same every time ----------
function seedLibrary() {
  const keep = Math.random;
  Math.random = rng(20261014);
  try {
    const plan = ["Fineliner", "Brush pen", "Dip pen", "Watercolor", "Gouache", "Charcoal", "Pencil", "Ink wash", "Oils", "Marker", "Hake", "Pastels",
      "Dry brush", "Dip pen", WILDCARD, "Watercolor", "Charcoal", "Brush pen", WILDCARD, "Oils", "Ink wash", "Hake"];
    S.library = plan.map(st => rollStyle(st));
    // A split nib, a Drying wash that takes ink and an oil with drag, so those are in reach from the start.
    S.library[2].tip = switchChoice("tip", S.library[2].tip, "split"); S.library[2].hand.d.pw = .9;
    Object.assign(S.library[3].life = freshPart("life", "dry"), {}); S.library[3].life.d.takes = 1; S.library[3].life.d.drying = .45;
    S.library.forEach(normBrush);
    S.slots = [S.library[0], S.library[1], S.library[2], S.library[3], S.library[4], S.library[5]];
    S.sel = 2;
  } finally { Math.random = keep; }
}

// ---------- layout per screen, in dp: nothing inside the frame ever scrolls ----------
function metrics() {
  const { W, H } = dims(), adv = S.mode === "advanced", mainH = H - 56 - 28, compact = W < 900;
  const col = adv ? (W >= 1300 ? 400 : W >= 1000 ? 360 : 306) : (W >= 1300 ? 340 : W >= 1000 ? 300 : 256);
  const right = W - col - 3, randH = compact ? 50 : 58, stylerow = compact ? 27 : 31, plainMin = compact ? 140 : 124;
  const carryOn = cur().carry.k !== "off";
  let styleMode = "grid", cardH, carryH, rowh = mainH < 720 ? 34 : compact ? 38 : 42, openH = 0;
  const carryOf = h => carryOn ? Math.max(70, Math.round(h * .8)) : 40;
  if (!adv) {
    const gridH = 26 + 7 * stylerow;
    cardH = Math.floor((mainH - gridH - randH - 8 - plainMin - 40) / 5);
    if (cardH < 84) { styleMode = "button"; cardH = Math.floor((mainH - randH - plainMin - 40) / 5); }
    cardH = clamp(cardH, 64, 116);
    carryH = carryOf(cardH);
    if (carryOn) cardH = clamp(Math.floor((mainH - (styleMode === "grid" ? gridH + 8 : 0) - randH - plainMin - carryH) / 5), 64, 116);
  } else {
    styleMode = "button";
    cardH = clamp(Math.floor((mainH - randH - 96 - 40) / 5), 64, 116);
    carryH = carryOf(cardH);
    if (carryOn) cardH = clamp(Math.floor((mainH - randH - 60 - carryH) / 5), 64, 116);
    openH = mainH - randH - 5 * rowh;
  }
  const slotw = compact ? 60 : 74, sloth = compact ? 50 : 58, libCardW = compact ? 104 : 118, libCardH = mainH < 720 ? 92 : compact ? 98 : 116;
  const libCols = Math.max(2, Math.floor((right - 24) / libCardW)), libRows = mainH >= 1100 && !adv ? 2 : 1;
  const slotsW = 52 + 6 * (slotw - 2), oneRow = right - 24 >= slotsW + 330;
  const shelfH = 8 + (oneRow ? Math.max(sloth, 34) : sloth + 8 + 34) + 8 + libRows * libCardH + 10;
  const varcols = right < 560 ? 3 : 6, varsH = (6 / varcols) * (compact ? 84 : mainH < 720 ? 80 : 112);
  const underH = shelfH + (adv ? varsH + 10 : 0);
  const swapH = Math.max(underH, Math.round(mainH * .44));
  return { W, H, adv, mainH, compact, col, right, randH, stylerow, plainMin, styleMode, cardH, carryH, rowh, openH, slotw, sloth, libCardH, libCols, libRows,
    varcols, varsH, underH, swapH, smpw: Math.round(col * (compact ? .36 : .38)), smph: Math.max(30, cardH - 42) };
}

// ---------- building the lab ----------
let MET = null;
function buildLab() {
  fitStage();
  const root = $("screen");
  root.innerHTML = `<div class="app" id="app">
    <div class="bar" id="lbar"></div>
    <div class="main" id="main">
      <div class="col" id="col"></div>
      <div class="right">
        <div class="test" id="testbox"><canvas id="test" aria-label="Test area: draw here"></canvas>
          <div class="tleft"><button class="btn line clear" data-act="clear">Clear</button>
          <button class="btn line eraser" data-act="eraser" aria-pressed="${TEST.eraser}" aria-label="Eraser">Eraser</button>
          <button class="btn line cutbtn" data-act="cutstamp" hidden aria-pressed="false" aria-label="Cut Stamp: lasso a shape on the test area">Cut Stamp</button>
          <button class="btn line floatbtn wetdrop" data-act="printbath" hidden aria-label="Floating: tap to print it now">${svg("drop")}<span>Print</span></button>
          <button class="btn line floatbtn tiltbtn" data-act="tilt" hidden aria-label="Page tilt (for Float's drift)">Level</button></div>
          <div class="tools" id="tools"></div><div id="pick"></div>
        </div>
        <div class="under" id="under"></div>
      </div>
      <div class="sheet" id="sheet" hidden></div>
    </div>
    <div class="status" id="status"></div>
  </div>`;
  const app = $("app");
  app.addEventListener("click", onClick);
  app.addEventListener("input", onInput);
  app.addEventListener("change", onChange);
  app.addEventListener("keydown", e => { if (e.key === "Enter" && e.target.id === "rename") finishRename(); if (e.key === "Enter" && e.target.id === "paste") ACT.load(); });
  TEST.cv = $("test"); TEST.ctx = TEST.cv.getContext("2d");
  wireTest();
  renderLab();
  setStatus(S.status || "Slot " + (S.sel + 1) + ": " + cur().name + ".");
}
function renderLab() {
  if (!$("app")) return;
  MET = metrics();
  const app = $("app"), m = MET;
  app.classList.toggle("compact", m.compact);
  for (const [k, v] of Object.entries({ col: m.col, rowh: m.rowh, smpw: m.smpw, smph: m.smph, stylerow: m.stylerow, randh: m.randH, slotw: m.slotw, sloth: m.sloth,
    under: S.swap ? m.swapH : m.underH, varsh: m.varsH })) app.style.setProperty("--" + k, v + "px");
  app.style.setProperty("--varcols", m.varcols);
  renderBar(); renderCol(); renderTools(); renderUnder(); renderSheet();
  fitTest(); cutButton();
}
// A Stamp's Cut Stamp (newmods.js): shown for a Stamp only; on while the test area's next stroke is its lasso.
function cutButton() {
  const b = document.querySelector(".test .cutbtn"); if (!b) return;
  const stamp = !!cur() && cur().tip.k === "stamp";
  if (!stamp) TEST.cutting = false;
  b.hidden = !stamp; b.setAttribute("aria-pressed", String(!!TEST.cutting));
}

// ---------- the bar ----------
function renderBar() {
  const b = cur(), m = MET;
  const name = S.renaming
    ? `<input id="rename" value="${esc(b.name)}" maxlength="32" aria-label="Brush name"><button class="btn line" data-act="renamed">Done</button>`
    : `<b title="${esc(b.name)}">${esc(b.name)}</b><button class="btn" data-act="rename" aria-label="Rename">${svg("pencil-simple")}</button><button class="btn line load" data-act="code" aria-pressed="${S.codeOpen}">Load</button><button class="btn solid save" data-act="save">${svg("bookmark-simple")}Save</button>`;
  $("lbar").innerHTML = `<button class="btn" data-act="back" aria-label="Back to drawing">${svg("arrow-left")}</button>
    <div class="namebox">${name}</div>
    <div class="seg"><button data-act="mode" data-v="simple" aria-pressed="${S.mode === "simple"}">SIMPLE</button><button data-act="mode" data-v="advanced" aria-pressed="${S.mode === "advanced"}">${m.compact ? "ADV" : "ADVANCED"}</button></div>
    <button class="btn" data-act="undo" ${S.history.length ? "" : "disabled"} aria-label="Undo">${svg("arrow-u-up-left")}${m.compact ? "" : "<span>Undo</span>"}</button>`;
  if (S.renaming) { const f = $("rename"); f.focus(); f.select(); }
}
function finishRename() {
  const f = $("rename"); if (!f) return;
  const name = cleanName(f.value);
  S.renaming = false;
  if (name && name !== cur().name) { const c = edit(); c.name = name; changed("Renamed: " + name + "."); } else renderBar();
}

// ---------- the column of parts ----------
const isLocked = part => S.mode === "advanced" && S.locks.parts.has(part);
const dialLocked = (part, key) => S.mode === "advanced" && (S.locks.parts.has(part) || S.locks.dials.has(part + "." + key));
const lockBtn = part => `<button class="icn${isLocked(part) ? " on" : ""}" data-act="lockpart" data-part="${part}" aria-pressed="${isLocked(part)}" aria-label="${isLocked(part) ? "Unlock" : "Lock"} ${ROLE[part].label}">${svg(isLocked(part) ? "lock" : "lock-open")}</button>`;
const diceBtn = part => `<button class="icn" data-act="dicepart" data-part="${part}" ${isLocked(part) ? "disabled" : ""} aria-label="Roll ${ROLE[part].label}">${svg("dice")}</button>`;
function macroHtml(B, part) {
  const P = B[part], c = choiceOf(part, P.k);
  if (!c.macro) return `<div class="macro"><span class="none">${part === "life" ? "Nothing moves after the nib passes." : "Off. Own paint only."}</span></div>`;
  const [lo, hi] = rangeOf(part, P.k, c.macro), v = P.d[c.macro];
  return `<div class="macro"><span>${c.words[0]}</span><input type="range" data-part="${part}" data-key="${c.macro}" min="${lo}" max="${hi}" step="${(hi - lo) / 200}" value="${v}" ${dialLocked(part, c.macro) ? "disabled" : ""} aria-label="${ROLE[part].label}: ${c.words[0]} to ${c.words[1]}"><span>${c.words[1]}</span></div>`;
}
function cardFull(B, part) {
  const m = MET, P = B[part], adv = m.adv, sel = S.swap === part ? " sel" : "";
  if (part === "carry" && P.k === "off") return `<div class="card t-carry slim${sel}" data-act="card" data-part="carry" style="height:${m.carryH}px" role="button" aria-label="Carry: off. Tap to swap.">
    <span class="tab"></span><div class="cbody" style="flex-direction:row;align-items:center;gap:8px;padding-top:0;padding-bottom:0">${glyph("carry")}<span class="lab">Carry</span><b class="cname">Off</b><span class="sp" style="flex:1"></span>${adv ? lockBtn(part) + diceBtn(part) : ""}</div></div>`;
  const h = part === "carry" ? m.carryH : m.cardH;
  return `<div class="card t-${part}${sel}" data-act="card" data-part="${part}" style="height:${h}px" role="button" aria-label="${ROLE[part].label}: ${esc(choiceWords(B, part))}. Tap to swap.">
    <span class="tab"></span><div class="cbody"><div class="chead">${glyph(part)}<span class="lab">${ROLE[part].label}</span><span class="sp"></span>${adv ? lockBtn(part) + diceBtn(part) : ""}</div>
    <div class="cname">${esc(choiceWords(B, part))}</div><canvas class="csmp" data-smp="${part}" style="${adv ? "top:28px;height:" + Math.max(22, (part === "carry" ? m.carryH : m.cardH) - 62) + "px" : part === "carry" ? "height:" + Math.max(24, m.carryH - 42) + "px" : ""}"></canvas>${macroHtml(B, part)}</div></div>`;
}
function cardRow(B, part) {
  return `<div class="crow t-${part}${S.swap === part ? " sel" : ""}" data-act="card" data-part="${part}" role="button"><span class="tab"></span>${glyph(part)}<span class="lab">${ROLE[part].label}</span><b>${esc(choiceWords(B, part))}</b>${lockBtn(part)}</div>`;
}
// A dial that changes nothing right now is greyed, not hidden.
function dialActive(B, part, key) {
  const M = B.medium, T = B.tip;
  if (part === "medium") {
    if (M.k === "ink" && (key === "line" || key === "bleed" || key === "bloom") && M.d.wet < .02) return false;
    if (M.k === "ink" && key === "lines" && (T.k === "split" || T.k === "multi" || T.k === "hake" || T.k === "bristles")) return false;
    if (M.k === "wash" && (key === "bloom" || key === "backrun" || key === "pool" || key === "bleed") && M.d.wet < .5) return false;
  }
  if (part === "tip" && key === "soft") { const lineOnly = (M.k === "ink" && M.d.wet < .1) || M.k === "charcoal" || M.k === "graphite"; if (lineOnly) return false; }
  if (part === "tip" && key === "angle" && T.k === "hake" && !(T.d.headTurn | 0)) return false;
  if (part === "life" && key === "plume" && !(B.life.d.takes | 0)) return false;
  if (part === "life" && key === "takes" && M.k !== "wash" && !(M.k === "ink" && M.d.wet > .3) && M.k !== "cover") return false;
  return true;
}
function bandHtml(bd, lo, hi) {
  if (!bd) return "";
  const at = v => (v - lo) / (hi - lo || 1), l = at(bd[0]), w = Math.max(.025, at(bd[1]) - l);
  return `<span class="band" style="left: calc(5px + (100% - 10px) * ${l.toFixed(3)} - 5px); width: calc((100% - 10px) * ${w.toFixed(3)} + 10px)"></span>`;
}
function dialRow(B, part, key) {
  const P = B[part], s = DIALS[part][key], locked = dialLocked(part, key), on = dialActive(B, part, key), label = dialLabel(part, P.k, key);
  const lk0 = `<button class="icn${S.locks.dials.has(part + "." + key) ? " on" : ""}" data-act="lockdial" data-part="${part}" data-key="${key}" ${isLocked(part) ? "disabled" : ""} aria-label="${locked ? "Unlock" : "Lock"} ${esc(label)}">${svg(locked ? "lock" : "lock-open")}</button>`;
  // Erasable moves only by hand (no roll changes it): no lock or die, but Resist's (wax or fluid).
  const hand = part === "medium" && key === "erase" && P.k !== "resist";
  const lk = hand ? "" : lk0;
  const dc = hand ? "" : `<button class="icn" data-act="dicedial" data-part="${part}" data-key="${key}" ${locked ? "disabled" : ""} aria-label="Roll ${esc(label)}">${svg("dice")}</button>`;
  if (s.choices) return `<div class="drow${on ? "" : " off"}${locked ? " locked" : ""}"><span class="dname" title="${esc(label)}">${esc(label)}</span><div class="pseg">${s.choices.map((o, i) =>
    `<button data-act="dialchoice" data-part="${part}" data-key="${key}" data-i="${i}" aria-pressed="${(P.d[key] | 0) === i}" ${locked ? "disabled" : ""}>${esc(optLabel(part, P.k, key, i))}</button>`).join("")}</div>${lk}${dc}</div>`;
  const [lo, hi] = rangeOf(part, P.k, key);
  return `<div class="drow${on ? "" : " off"}${locked ? " locked" : ""}"><span class="dname" title="${esc(label)}">${esc(label)}</span>
    <div class="track">${on ? bandHtml(styleBand(B.style, part, P.k, key), lo, hi) : ""}<input type="range" data-part="${part}" data-key="${key}" min="${lo}" max="${hi}" step="${(hi - lo) / 400}" value="${P.d[key]}" ${locked ? "disabled" : ""} aria-label="${esc(label)}"></div>
    <span class="dval" data-val="${part}.${key}">${fmtDial(part, key, P.d[key])}</span>${lk}${dc}</div>`;
}
const HAND_ROWS = [["Pressure", "p", ["ps", "pd", "pw"]], ["Tilt", "t", ["ts", "td", "tw"]], ["Speed", "s", ["ss", "sd", "sw"]]];
const cellLevel = v => v < .17 ? "off" : v < .75 ? "some" : "full";
function handGrid(B, cellh) {
  const d = B.hand.d;
  let h = `<div class="hgrid" style="--cellh:${cellh}px"><span></span><span class="hh">Size</span><span class="hh">${typeof darkHead === "function" ? darkHead(B) : "Dark"}</span><span class="hh">Width·angle</span><span></span><span></span>`;
  for (const [name, r, keys] of HAND_ROWS) {
    const locked = keys.every(k => dialLocked("hand", k));
    h += `<span class="hr">${name}</span>` + keys.map(k => { const v = d[k] || 0, lv = cellLevel(v);
      return `<button class="cell ${lv}" data-act="cell" data-key="${k}" ${dialLocked("hand", k) ? "disabled" : ""} aria-label="${esc(DIALS.hand[k].label)}: ${lv}${k === "ss" && v > .1 ? ", " + ((d.dir | 0) ? "thicker" : "thinner") : ""}"><i style="height:${Math.round(v * 100)}%"></i><span>${k === "ss" && v > .1 ? ((d.dir | 0) ? "+" : "−") : ""}${lv}</span></button>`; }).join("")
      + `<button class="icn${locked ? " on" : ""}" data-act="lockrow" data-row="${r}" ${isLocked("hand") ? "disabled" : ""} aria-label="${locked ? "Unlock" : "Lock"} ${name}">${svg(locked ? "lock" : "lock-open")}</button><button class="icn" data-act="dicerow" data-row="${r}" ${locked ? "disabled" : ""} aria-label="Roll ${name}">${svg("dice")}</button>`;
  }
  return h + "</div>";
}
function cardOpen(B, part) {
  const m = MET, P = B[part], c = choiceOf(part, P.k);
  const chips = CHOICES[part].filter(ch => !(part === "hand" && ch.k === "custom" && P.k !== "custom")).map(ch =>
    `<button class="chip" data-act="choose" data-part="${part}" data-k="${ch.k}" aria-pressed="${ch.k === P.k}" ${isLocked(part) ? "disabled" : ""}>${esc(ch.label)}</button>`).join("");
  const blurb = part === "hand" ? "Rows: pressure, tilt, speed. Columns: size, darkness, nib width and angle. Tap a cell to step it: off, some, full." : (c.blurb || ROLE[part].blurb);
  const chipRows = Math.ceil(CHOICES[part].length / Math.max(2, Math.floor((m.col - 30) / 92)));
  const fixed = 30 + 34 + chipRows * 28 + 10;
  let body;
  if (part === "hand") {
    // A Spiro's drives under Response and Fast is: what pressure, speed and tilt set of its shape (newmods.js).
    const drives = B.tip.k === "spiro" && typeof DRIVES !== "undefined" ? DRIVES : [];
    // Then Grey Shift's rows: its rule, a Pattern's Order, its Greys and its set; then Gradient's (newmods.js).
    const shifts = [...(typeof shiftRows === "function" ? shiftRows(B) : []), ...(typeof gradRows === "function" ? gradRows(B) : [])];
    const cellh = clamp(Math.floor((m.openH - fixed - 26 - (2 + drives.length + shifts.length) * 40) / 3), 30, 46);
    body = handGrid(B, cellh) + `<div class="dials" style="--drowh:${Math.min(40, m.rowh)}px;--dname:${m.compact ? 70 : 92}px">${dialRow(B, "hand", "gain")}${dialRow(B, "hand", "dir")}${drives.map(k => dialRow(B, "hand", k)).join("")}${shifts.map(k => k === "set" ? greySetHtml(B, S.swatches, dialLocked("hand", "shift")) : dialRow(B, "hand", k)).join("")}</div>`;
  } else {
    const shown = c.dials.filter(k => !DIALS[part][k].hide && (typeof dialShown !== "function" || dialShown(B, part, k))), n = shown.length, rh = n ? clamp(Math.floor((m.openH - fixed) / n), 28, 44) : 0;
    const note = part === "life" && P.k === "wet" ? `<p class="blurb" style="margin-top:6px">Takes ink needs Slow: the line spreads only while the wash is wet.</p>` : "";
    body = n ? `<div class="dials" style="--drowh:${rh}px;--dname:${m.compact ? 86 : 104}px">${shown.map(k => dialRow(B, part, k)).join("")}${note}</div>`
      : `<p class="blurb" style="margin-top:10px">${part === "carry" ? "No dials. Lays its own paint only." : "No dials. Final as the nib passes."}</p>`;
  }
  return `<div class="card open t-${part}${S.swap === part ? " sel" : ""}" style="height:${m.openH}px"><span class="tab"></span><div class="cbody">
    <div class="chead">${glyph(part)}<span class="lab">${ROLE[part].label}</span><b style="font-size:13px;margin-left:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(choiceWords(B, part))}</b><span class="sp"></span>${lockBtn(part)}${diceBtn(part)}<button class="icn" data-act="closecard" aria-label="Close ${ROLE[part].label}">${svg("x")}</button></div>
    <p class="blurb">${esc(blurb)}</p><div class="chips">${chips}</div>${body}</div></div>`;
}
function renderCol() {
  const B = cur(), m = MET, col = $("col");
  const styleBtn = `<button class="btn stylebtn" data-act="styles" aria-expanded="${S.styleOpen}"><span>Style: ${esc(B.style || WILDCARD)}</span>${svg("caret-down")}</button>`;
  const rand = `<button class="btn randomize" data-act="randomize">${svg("dice")}<span>Randomize</span></button>`;
  const plain = `<div class="plain"><span class="lab">This brush</span><p>${esc(typeof brushWords === "function" ? brushWords(B) : plainWords(B))}</p></div>`;
  const flyout = S.styleOpen ? `<div class="flyout" style="left:8px;right:8px;top:${m.adv ? m.randH + 4 : 60}px"><span class="lab">Style</span><div class="chips">${STYLE_NAMES.map(n =>
    `<button class="chip" data-act="style" data-style="${esc(n)}" aria-pressed="${B.style === n}">${esc(n)}</button>`).join("")}</div></div>` : "";
  if (!m.adv) {
    const grid = m.styleMode === "grid" ? `<div class="stylebox"><span class="lab">Style</span><div class="stylegrid">${STYLE_NAMES.map(n =>
      `<button data-act="style" data-style="${esc(n)}" aria-pressed="${B.style === n}">${esc(n)}</button>`).join("")}</div></div>` : "";
    col.innerHTML = `<div class="cards">${PARTS.map(p => cardFull(B, p)).join("")}</div>${grid}
      ${m.styleMode === "grid" ? rand : `<div class="rowbtns" style="margin-top:0">${styleBtn}${rand}</div>`}${plain}${flyout}`;
  } else {
    const cards = S.open ? PARTS.map(p => p === S.open ? cardOpen(B, p) : cardRow(B, p)).join("") : PARTS.map(p => cardFull(B, p)).join("");
    col.innerHTML = `<div class="rowbtns">${styleBtn}${rand}</div><div class="cards"${S.open ? ' style="flex:1;min-height:0"' : ""}>${cards}</div>${S.open ? "" : plain}${flyout}`;
  }
  fitPlain(col.querySelector(".plain"));
  col.querySelectorAll("canvas[data-smp]").forEach(c => later(() => c.isConnected && renderSample(c, cur(), c.dataset.smp)));
}
// "This brush" keeps whole sentences: the last ones go when the box is short (never fewer than two).
function fitPlain(box) {
  if (!box) return;
  const p = box.querySelector("p"); if (!p) return;
  let parts = p.textContent.match(/[^.]+\./g) || [p.textContent];
  while (parts.length > 2 && box.scrollHeight > box.clientHeight + 1) { parts = parts.slice(0, -1); p.textContent = parts.join("").trim(); }
  // Advanced shows it only when there's room: it's Simple's description.
  if (S.mode === "advanced" && box.scrollHeight > box.clientHeight + 1) box.hidden = true;
}

// ---------- the test area's tools: three size slots and three shade swatches ----------
const SIZE_MAX = 360, SIZE_STOPS = [2, 4, 8, 16, 32, 64, 120, 240, 360];
const sizeT = px => Math.log(px) / Math.log(SIZE_MAX);
const sizePx = t => { const v = Math.exp(t * Math.log(SIZE_MAX)); return v < 10 ? Math.max(1, Math.round(v * 2) / 2) : Math.round(v); };
const mmOf = px => { const m = px * 25.4 / 300; return (m < 10 ? m.toFixed(1) : Math.round(m)) + " mm"; };
const greyOf = d => Math.round(PAPER_TONE * (1 - d));
const GREYS = [1, .87, .73, .6, .47, .33, .2, .1];
function renderTools() {
  const el = $("tools"); if (!el) return;
  el.innerHTML = `<div class="tgroup" role="group" aria-label="Sizes">${S.sizes.map((px, i) => `<button class="tslot${i === S.sizeSel ? " on" : ""}" data-act="size" data-i="${i}" aria-label="Size ${i + 1}: ${px} px"><canvas></canvas><span class="more" data-act="sizepick" data-i="${i}" aria-label="Change size ${i + 1}">${svg("caret-down")}</span></button>`).join("")}</div>
    <div class="tgroup" role="group" aria-label="Shades">${S.swatches.map((d, i) => `<button class="tslot${i === S.swSel ? " on" : ""}" data-act="swatch" data-i="${i}" aria-label="Shade ${i + 1}: ${Math.round(d * 100)}%"><span class="face${greyOf(d) > 235 ? " paper" : ""}" style="background:rgb(${greyOf(d)},${greyOf(d)},${greyOf(d)})"></span><span class="more" data-act="shadepick" data-i="${i}" aria-label="Change shade ${i + 1}">${svg("caret-down")}</span></button>`).join("")}</div>`;
  el.querySelectorAll(".tgroup:first-child canvas").forEach((c, i) => drawSizeFace(c, S.sizes[i]));
  renderPicker();
}
function sizeCanvas(c) {
  const w = Math.max(8, Math.round(c.offsetWidth * DPX)), h = Math.max(8, Math.round(c.offsetHeight * DPX));
  if (c.width !== w || c.height !== h) { c.width = w; c.height = h; }
}
function drawSizeFace(c, px) {
  sizeCanvas(c);
  const ctx = c.getContext("2d"), w = c.width, h = c.height, cap = Math.min(w, h) * .8, big = px > cap;
  ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, w, h); ctx.fillStyle = "#000"; ctx.beginPath();
  const d = big ? cap * .5 : px, cy = big ? h * .36 : h / 2;
  ctx.arc(w / 2, cy, Math.max(.8, d / 2), 0, 2 * Math.PI); ctx.fill();
  if (big) { ctx.font = `700 ${8 * DPX}px "IBM Plex Mono", monospace`; ctx.textAlign = "center"; ctx.textBaseline = "bottom"; ctx.fillText(mmOf(px).replace(" ", ""), w / 2, h - 1); }
}
function renderPicker() {
  const host = $("pick"); if (!host) return;
  if (!S.picker) { host.innerHTML = ""; return; }
  const { kind, i } = S.picker, box = $("testbox"), left = Math.max(6, box.offsetWidth - 320);
  if (kind === "shade") {
    host.innerHTML = `<div class="picker" style="left:${Math.max(6, box.offsetWidth - 220)}px;top:58px"><div class="pkhead"><span class="lab">Shade ${i + 1}</span><span class="pkval">${Math.round(S.swatches[i] * 100)}%</span><button class="btn line" data-act="pickdone">Done</button></div>
      <div class="greys">${GREYS.map(d => `<button data-act="grey" data-d="${d}" aria-pressed="${Math.abs(d - S.swatches[i]) < .005}" style="background:rgb(${greyOf(d)},${greyOf(d)},${greyOf(d)})" aria-label="${Math.round(d * 100)}%"></button>`).join("")}</div></div>`;
    return;
  }
  host.innerHTML = `<div class="picker" style="left:${left}px;top:58px;width:300px"><div class="pkhead"><span class="lab">Size ${i + 1}</span><span class="pkval"></span><button class="btn line" data-act="pickdone">Done</button></div>
    <div class="pkview"><canvas></canvas></div>
    <input type="range" id="sizerange" min="0" max="1" step="0.001" value="${sizeT(S.sizes[i])}" aria-label="Size ${i + 1}, thin to big">
    <div class="pkscale"><span>thin</span><span>big brush</span></div>
    <div class="chips">${SIZE_STOPS.map(p => `<button class="chip" data-act="sizestop" data-px="${p}" aria-pressed="${p === S.sizes[i]}">${p}</button>`).join("")}</div></div>`;
  showSizePick();
}
function showSizePick() {
  const host = $("pick"); if (!host || !S.picker || S.picker.kind !== "size") return;
  const px = S.sizes[S.picker.i], view = host.querySelector(".pkview canvas"), val = host.querySelector(".pkval");
  sizeCanvas(view); const ctx = view.getContext("2d");
  ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, view.width, view.height); ctx.fillStyle = "#000";
  ctx.beginPath(); ctx.arc(view.width / 2, view.height / 2, Math.max(.8, px / 2), 0, 2 * Math.PI); ctx.fill();
  val.textContent = px + " px · " + mmOf(px);
  host.querySelectorAll(".chip").forEach(ch => ch.setAttribute("aria-pressed", String(+ch.dataset.px === px)));
}

// ---------- under the test area: the swap strip, or the variations, slots and library ----------
const VAR_LABEL = v => v.swap || null;
function renderUnder() {
  const el = $("under"); if (!el) return;
  if (S.swap) { renderSwap(el); return; }
  const m = MET, B = cur();
  let html = "";
  if (m.adv) {
    html += `<div class="vars">${S.vars.map((v, i) => { const sw = VAR_LABEL(v), rest = v.caption.filter(c => c !== sw);
      return `<button class="tcard" data-act="var" data-i="${i}" aria-label="Variation: ${esc(v.caption.join(", "))}"><div class="smp"><canvas data-role="loop"></canvas></div>${sw ? `<span class="swapflag">${esc(sw)}</span>` : ""}<span class="cap"><b>${esc(rest[0] || "")}</b><i>${esc(rest.slice(1).join(", "))}</i></span></button>`; }).join("")}
      <button class="vdice" data-act="vars" aria-label="Six new variations">${svg("dice")}</button></div>`;
  }
  const perPage = m.libCols * m.libRows, list = library(), pages = Math.max(1, Math.ceil(list.length / perPage));
  S.libPage = Math.min(S.libPage, pages - 1);
  const shown = list.slice(S.libPage * perPage, (S.libPage + 1) * perPage);
  const inSlots = b => S.slots.map((s, i) => sameBrush(s, b) ? i + 1 : 0).filter(Boolean);
  const cards = shown.map((b, i) => { const badges = inSlots(b).map(n => `<i>${n}</i>`).join(""), cap = b.builtin ? [b.style, b.blurb] : captionLines(b);
    return `<button class="tcard${sameBrush(b, B) ? " now" : ""}" data-act="libcard" data-i="${S.libPage * perPage + i}" aria-label="${esc(b.name)}"><div class="smp"><canvas data-role="loop"></canvas></div>
      <span class="cap"><b>${esc(cap[0] || "")}</b><i>${esc(cap[1] || "")}</i><i class="nm">${esc(b.name)}</i></span>${badges ? `<span class="badges">${badges}</span>` : ""}</button>`; });
  while (cards.length < perPage) cards.push(`<div class="empty"></div>`);
  html += `<div class="shelf"><div class="shelfhead">
      <div class="slots"><span class="lab">Toolbar<br>slots »</span>${S.slots.map((b, i) => `<button class="slot${i === S.sel ? " sel" : ""}" data-act="slot" data-i="${i}" aria-label="Slot ${i + 1}: ${esc(b.name)}"><div class="smp"><canvas data-role="loop"></canvas></div><i>${i + 1}</i></button>`).join("")}</div>
      <div class="libhead"><span class="lab">Library <span class="to">→ slot ${S.sel + 1}</span></span>
        <button class="btn line surprise" data-act="surprise" ${library().length > 1 ? "" : "disabled"} aria-label="Surprise me: a random library brush into slot ${S.sel + 1}">${svg("dice")}<span>Surprise me</span></button>
        <button class="btn line" data-act="prev" ${S.libPage ? "" : "disabled"} aria-label="Previous page">${svg("caret-left")}</button>
        <span class="pg">${S.libPage + 1}/${pages}</span>
        <button class="btn line" data-act="next" ${S.libPage < pages - 1 ? "" : "disabled"} aria-label="Next page">${svg("caret-right")}</button></div></div>
    <div class="lib" style="grid-template-columns: repeat(${m.libCols}, minmax(0, 1fr)); grid-template-rows: repeat(${m.libRows}, minmax(0, 1fr))">${cards.join("")}</div></div>`;
  el.innerHTML = html;
  if (m.adv) el.querySelectorAll(".vars canvas").forEach((c, i) => later(() => c.isConnected && S.vars[i] && renderSample(c, S.vars[i].b, "loop")));
  el.querySelectorAll(".slot canvas").forEach((c, i) => later(() => c.isConnected && renderSample(c, S.slots[i], "loop")));
  el.querySelectorAll(".lib canvas").forEach((c, i) => later(() => c.isConnected && shown[i] && renderSample(c, shown[i], "loop")));
}
// The swap strip: the part's choices, each drawn with the current brush, only that part changed.
function swapList(part) {
  const B = cur(), out = [];
  for (const ch of CHOICES[part]) {
    if (part === "hand" && ch.k === "custom") continue;
    const c = cloneBrush(B);
    if (part === "hand") { const P = freshPart("hand", ch.k); P.d.gain = B.hand.d.gain; P.d.dir = B.hand.d.dir; c.hand = P; }
    else if (B[part].k !== ch.k) c[part] = switchChoice(part, B[part], ch.k);
    if (part === "life" && ch.k === "dry" && B.life.k === "dry") c.life.d.takes = 0;
    out.push({ k: ch.k, label: ch.label, b: normBrush(c), now: part === "life" ? B.life.k === ch.k && !(ch.k === "dry" && (B.life.d.takes | 0)) : B[part].k === ch.k });
  }
  if (part === "life") {
    const c = cloneBrush(B); c.life = B.life.k === "dry" ? cloneBrush(B.life) : switchChoice("life", B.life, "dry"); c.life.d.takes = c.life.d.takes || 1;
    out.push({ k: "dry+ink", label: "Slow, takes ink", note: "a pen line spreads into it (tentative)", b: normBrush(c), now: B.life.k === "dry" && !!(B.life.d.takes | 0) });
  }
  return out;
}
function renderSwap(el) {
  const part = S.swap, list = swapList(part), n = list.length, m = MET;
  // Columns so each tile is a little wider than tall, whatever the panel's shape.
  const pw = m.right - 24, ph = m.swapH - 56;
  let cols = 1, best = Infinity;
  for (let c = 1; c <= n; c++) { const rows = Math.ceil(n / c), a = (pw / c) / (ph / rows), cost = Math.abs(Math.log(a / 1.35)) + (rows * c - n) * .08; if (cost < best) { best = cost; cols = c; } }
  const hint = part === "life" ? "Your brush, each tile shown dry." : "Your brush with only the " + ROLE[part].label.toLowerCase() + " changed.";
  el.innerHTML = `<div class="swap t-${part}"><div class="swaphead"><span class="tab"></span><div class="t">${glyph(part)}<b>${ROLE[part].label}: pick one</b><span>${esc(hint)}</span></div>
    <button class="btn" data-act="closeswap" aria-label="Close">${svg("x")}</button></div>
    <div class="swapgrid" style="--swcols:${cols}">${list.map((it, i) => `<button class="tcard${it.now ? " now" : ""}" data-act="swapto" data-k="${it.k}" aria-label="${esc(ROLE[part].label + ": " + it.label)}"><div class="smp"><canvas data-i="${i}"></canvas></div>
      <span class="cap"><b>${esc(it.label)}</b><i>${esc(it.note || (part === "hand" ? handWords(it.b) : part === "medium" ? (typeof mediumNote === "function" ? mediumNote(it.b) : choiceWords(it.b, "medium")) : part === "life" ? lifeNote(it.b) : ""))}</i></span></button>`).join("")}</div></div>`;
  el.querySelectorAll(".swapgrid canvas").forEach((c, i) => later(() => c.isConnected && renderSample(c, list[i].b, part)));
}

// ---------- share codes ----------
function renderSheet() {
  const el = $("sheet"); if (!el) return;
  if (!S.codeOpen) { el.hidden = true; el.innerHTML = ""; return; }
  el.hidden = false;
  el.innerHTML = `<div class="shrow"><span class="lab">Load a code</span><input id="paste" placeholder="Paste a share code (M, or an old I/P/B code)"><button class="btn" data-act="load">Load</button></div>
    <div class="shrow"><span class="lab">Share code</span><input id="code" readonly value="${esc(encodeM(cur()))}"><button class="btn" data-act="copy">Copy</button></div>
    <small>${esc(S.loadNote || "Old app and prototype codes still load.")}</small>`;
}

// ---------- samples: every card, tile and slot is the brush drawn with the same engine ----------
const queue = [];
let pumping = false;
function later(fn) { queue.push(fn); if (!pumping) { pumping = true; setTimeout(pump, 0); } }
function pump() {
  const t0 = performance.now();
  while (queue.length && performance.now() - t0 < 20) { try { queue.shift()(); } catch (e) { console.error(e); } }
  if (queue.length) setTimeout(pump, 0); else pumping = false;
}
const SURFS = new Map();
function sampleSurf(w, h) {
  const key = w + "x" + h;
  let s = SURFS.get(key);
  if (!s) { if (SURFS.size > 24) SURFS.clear(); s = modSurface(w, h, 7); SURFS.set(key, s); }
  blankPage(s);
  return s;
}
// The paths a sample is drawn along, in a w × h tile: each role shows what that part adds.
function wavePath(w, h, pad, amp, opts = {}) {
  const pts = [], x0 = pad, x1 = w - pad, n = Math.max(30, Math.ceil((x1 - x0) / 2.5));
  let t = 0;
  for (let i = 0; i <= n; i++) {
    const u = i / n, env = Math.sin(Math.PI * u), x = lerp(x0, x1, u), y = h / 2 + (opts.flat ? .5 : 1) * amp * Math.sin(2 * Math.PI * (opts.waves || 1.25) * u);
    const p = opts.ramp ? .08 + .92 * Math.pow(u, 1.2) : .15 + .78 * Math.pow(env, .7);
    t += opts.ramp ? 2 + 5 * (1 - u) : 2 + 4 * (1 - env);
    const tilt = opts.tilt ? [Math.atan(Math.tan(u * 55 * DEG) * Math.cos((200 + u * 120) * DEG)) / DEG, Math.atan(Math.tan(u * 55 * DEG) * Math.sin((200 + u * 120) * DEG)) / DEG] : [NaN, NaN];
    pts.push({ x, y, p, t, tx: tilt[0], ty: tilt[1] });
  }
  return pts;
}
function loopPath(w, h, pad) { return loopPoints(w, h, pad).map(p => ({ x: p.x, y: p.y, p: p.p, t: p.t, tx: NaN, ty: NaN })); }
// The mark's size in a tile: a line reads at about a fourteenth of the tile's height, a body of paint at a sixth.
function sampleSize(B, h, role) {
  const mk = B.medium.k, tk = B.tip.k;
  let s;
  if (mk === "ink") s = h / (13 + 14 * B.medium.d.wet);
  else if (mk === "charcoal" || mk === "crayon") s = h / 9;
  else if (mk === "graphite") s = h / 16;
  else s = tk === "hake" ? h * .24 : h * .17;
  if (tk === "bristles" && (mk === "ink" || mk === "graphite")) s = h / 8;
  if (role === "paper") s *= 1.7;
  if (role === "hand" && s < h / 7) s *= 1.8;
  return Math.max(2, s);
}
const CROSS_LINE = (() => { let b = null; return () => b || (b = (() => { const x = fromOld(BUILT[0]); x.life = freshPart("life", "none"); x.hand.d.ps = .3; return normBrush(x); })()); })();
const sampleCache = new Map();
function renderSample(c, B, role) {
  sizeCanvas(c);
  const w = c.width, h = c.height, shade = S.swatches[S.swSel];
  const key = brushKey(B) + "|" + role + "|" + w + "x" + h + "|" + shade;
  const hit = sampleCache.get(key), ctx = c.getContext("2d");
  if (hit) { ctx.putImageData(hit, 0, 0); return; }
  // The mark is sized to how far the sample reaches: about the upright loop's width, or .73 of a wide tile's height (WildSample.reach);
  // a wave's the tile's height, or .62 of a narrow tile's width (WildSample.waveReach).
  const looped = role === "loop" || role === "medium";
  const ref = looped ? Math.min(w, h * .73) : Math.min(h, w * .62), s = sampleSurf(w, h), size = sampleSize(B, ref, role), lineish = size < ref / 7;
  // A grey band behind, so cover, carry and wet edges have something to show against.
  if (role === "medium" || role === "carry" || role === "life") {
    const bx0 = Math.round(w * .42), bx1 = Math.round(w * .58);
    for (let y = 0; y < h; y++) for (let x = bx0; x < bx1; x++) s.page[y * w + x] = role === "carry" ? 60 : 150;
    if (role === "carry") for (let y = 0; y < h; y++) for (let x = Math.round(w * .2); x < Math.round(w * .24); x++) s.page[y * w + x] = 40;
  }
  let pts;
  const pad = Math.max(size * (lineish ? 2 : .7) + 6, w * .06), amp = Math.max(3, Math.min(ref * .3, h / 2 - size * .6 - 6));
  if (role === "loop" || role === "medium") pts = loopPath(w, h, Math.max(4, lineish ? size * 2 + 6 : size * .55 + 4));
  else if (role === "hand") pts = wavePath(w, h, pad, amp * .6, { ramp: true, tilt: true, waves: 1 });
  else if (role === "carry") pts = wavePath(w, h, pad, amp * .4, { waves: .75 });
  else if (role === "paper") pts = wavePath(w, h, pad, amp * .35, { waves: .75 });
  else pts = wavePath(w, h, pad, amp);
  const host = B.life.k === "dry" && (role === "life" || role === "loop" || role === "medium") ? { water: null, getWater() { return this.water ??= new Water(s, 11); } } : null;
  try {
    if (B.life.k === "float" && (role === "life" || isClearWater(B))) { floatSample(s, B, pts, size, shade, ref); showPage(ctx, s, [0, 0, w - 1, h - 1]); sampleCache.set(key, ctx.getImageData(0, 0, w, h)); return; }
    layStroke(s, B, pts, size, shade, host);
    if (host && host.water) {
      if ((B.life.d.takes | 0) && role === "life") {
        const n = 40, line = [];
        for (let i = 0; i <= n; i++) { const u = i / n; line.push({ x: w * (.38 + .24 * u), y: h / 2 + ref * (-.42 + .84 * u), p: .7, t: i * 10, tx: NaN, ty: NaN }); }
        layStroke(s, CROSS_LINE(), line, Math.max(2, ref / 45), 1, host);
      }
      host.water.dryAll();
    }
  } catch (e) { console.error(e); }
  showPage(ctx, s, [0, 0, w - 1, h - 1]);
  if (sampleCache.size > 500) sampleCache.clear();
  sampleCache.set(key, ctx.getImageData(0, 0, w, h));
}

// Float's tile: its stroke, then drops of it and of clear water in turn at the middle, held a while each, then printed.
function floatSample(s, B, pts, size, shade, ref) {
  const host = { bath: null, getBath() { return this.bath ??= new Bath(s); } }, cw = cloneBrush(B);
  cw.carry = switchChoice("carry", B.carry, "smudge"); cw.carry.d.load = 0; normBrush(cw);
  const own = isClearWater(B) ? floatTwin(B) : B, small = Math.max(2, Math.min(size, ref / 9));
  if (!isClearWater(B)) layStroke(s, B, pts, size, shade, host);
  const cx = s.w / 2, cy = s.h / 2;
  for (let i = 0; i < 5; i++) for (const [b, hold] of [[own, 260 + 90 * i], [cw, 160]]) {
    const st = new Stroke(s, b, small, i === 2 ? shade * .5 : shade, host);
    st.add([[cx, cy, .8, 1000 * i, NaN, NaN]]); for (let t = 50; t <= hold; t += 50) st.holdTick(1000 * i + t); st.end();
  }
  if (host.bath && host.bath.wet) host.bath.print();
}
const floatTwin = B => { const c = cloneBrush(B); c.carry = freshPart("carry", "off"); return normBrush(c); };
// Two Float brushes to start from: sumi ink, and clear water (Smudge's Own paint 0) to alternate with it.
function floatPair() {
  const make = (name, carry) => {
    const B = { name, seed: name === "Sumi" ? 0x5e1 : 0xc1ea, style: "Ink wash", tip: freshPart("tip", "round"), medium: switchChoice("medium", null, "ink"), carry: freshPart("carry", carry),
      paper: freshPart("paper", "plain"), life: freshPart("life", "float"), hand: freshPart("hand", "darker") };
    if (carry === "smudge") { B.carry.d.load = 0; B.carry.d.pick = .9; }
    return normBrush(B);
  };
  return [make("Sumi", "off"), make("Clear water", "smudge")];
}
function addFloatPair() { for (const b of floatPair()) if (!S.library.some(x => x.name === b.name)) S.library.push(b); }
// Three brushes to try Erasable with, added once at the front: a pencil that erases clean, an ink line that stays,
// a wash the eraser lifts in part (named as the Tools page's; spec 2026-10-16-erasable-design.md).
function erasePresets() {
  const make = (name, style, seed, medium, md, hand) => {
    const B = { name, seed, style, tip: freshPart("tip", "round"), medium: switchChoice("medium", null, medium), carry: freshPart("carry", "off"),
      paper: freshPart("paper", "plain"), life: freshPart("life", "wet"), hand: freshPart("hand", hand) };
    Object.assign(B.medium.d, md);
    return normBrush(B);
  };
  return [make("Sketch pencil", "Pencil", 0x5e7c, "graphite", { grade: .3 }, "darker"), make("Steady line", "Fineliner", 0x51e, "ink", {}, "steady"),
    make("Soft wash", "Watercolor", 0x50f7, "wash", {}, "steady")];
}
function addErasePresets() { const add = erasePresets().filter(b => !S.library.some(x => x.name === b.name)); S.library.unshift(...add); }
// ---------- the test area: a page of its own, a few marks to work over, the corner sample ----------
const TEST = { surf: null, cv: null, ctx: null, water: null, live: null, ticking: false, last: null, bath: null, bathTicking: false, tilt: { x: 0, y: 0 }, tiltI: 0, hold: 0, eraser: false, cutting: false };
// The test area's eraser, as the app's: on until a brush is picked again.
function setEraser(on) {
  TEST.eraser = !!on;
  const b = document.querySelector(".test .eraser"); if (b) b.setAttribute("aria-pressed", String(TEST.eraser));
}
const HOST = { get swatches() { return S.swatches; }, get water() { return TEST.water; }, getWater() { return TEST.water ??= new Water(TEST.surf, 2026); },
  get bath() { return TEST.bath; }, getBath() { if (!TEST.bath || !TEST.bath.wet) TEST.bath = new Bath(TEST.surf); return TEST.bath; } };
// Float: the page tilts for drift (the tablet's own tilt in the app); here a button steps through a few tilts.
const TILTS = [["Level", 0, 0], ["Tilt ↓", 0, .8], ["Tilt ↘", .55, .6], ["Tilt →", .8, 0], ["Tilt ↙", -.55, .6]];
function floatButtons() {
  const wet = !!(TEST.bath && TEST.bath.wet), fl = cur() && cur().life.k === "float";
  const d = document.querySelector(".test .wetdrop"), t = document.querySelector(".test .tiltbtn");
  if (d) d.hidden = !wet;
  if (t) { t.hidden = !(wet || fl); t.textContent = TILTS[TEST.tiltI][0]; }
}
function printBath(why) {
  const b = TEST.bath; if (!b || !b.wet) return;
  const r = b.print(); if (r) showPage(TEST.ctx, TEST.surf, r);
  floatButtons(); if (why) setStatus(why);
}
function tickBath() {
  const b = TEST.bath;
  if (!b || !b.wet || b.s !== TEST.surf) { TEST.bathTicking = false; floatButtons(); return; }
  if (!TEST.live) {
    const r = b.tick(performance.now(), TEST.tilt, b.drift ?? .5);
    if (r) showPage(TEST.ctx, TEST.surf, r);
    if (!b.wet) { floatButtons(); setStatus("Printed."); }
  }
  setTimeout(tickBath, 1000 / TPS);
}
function fitTest() {
  const cv = $("test"); if (!cv) return;
  TEST.cv = cv; TEST.ctx = cv.getContext("2d");
  const w = Math.max(100, Math.round(cv.offsetWidth * DPX)), h = Math.max(100, Math.round(cv.offsetHeight * DPX));
  if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h; }
  if (TEST.surf && TEST.surf.w === w && TEST.surf.h === h) { showPage(TEST.ctx, TEST.surf, [0, 0, w - 1, h - 1]); return; }
  const old = TEST.surf, next = modSurface(w, h, 2026);
  TEST.water = null;
  if (old) {
    const cw = Math.min(w, old.w);
    if (old.kept) next.kept = new Float32Array(w * h).fill(PAPER_TONE);
    for (let y = 0; y < Math.min(h, old.h); y++) for (const k of old.kept ? ["page", "kept"] : ["page"]) next[k].set(old[k].subarray(y * old.w, y * old.w + cw), y * w);
  }
  TEST.surf = next;
  if (!old) seedMarks();
  redrawSample();
}
// Marks already on the page, so Cover, Carry and wet edges have something to work over.
function seedMarks() {
  const s = TEST.surf, w = s.w, h = s.h, ink = BUILTINS[0], wash = cloneBrush(BUILTINS[2]);
  wash.life = freshPart("life", "none"); normBrush(wash);
  const band = [], zig = [];
  for (let i = 0; i <= 60; i++) { const u = i / 60; band.push({ x: w * (.05 + .38 * u), y: h * .3 + Math.sin(u * 6) * 6, p: .8, t: i * 8, tx: NaN, ty: NaN }); }
  for (let i = 0; i <= 80; i++) { const u = i / 80; zig.push({ x: w * (.05 + .38 * u), y: h * .22 + (i % 20 < 10 ? (i % 10) : 10 - (i % 10)) * h * .012, p: .7, t: i * 6, tx: NaN, ty: NaN }); }
  layStroke(s, wash, band, 90, .7, null);
  layStroke(s, ink, zig, 8, 1, null);
  s.base.set(s.page);
}
// As high as a box a third of the page wide (300 px at most) and .66 of that, and .9 as wide as high: the loop stands upright (TestArea).
const loopBox = () => { const s = TEST.surf, bh = Math.round(Math.min(Math.round(300 * DPX), Math.round(s.w * .34)) * .66), bw = Math.round(bh * .9); return [s.w - bw - Math.round(10 * DPX), Math.round(58 * DPX), bw, bh]; };
// The corner sample: the app icon's loop with the current brush at the test size (capped to its box).
function redrawSample() {
  const s = TEST.surf; if (!s || !TEST.ctx) return;
  const [x0, y0, bw, bh] = loopBox(), B = cur();
  const wipe = (X0, Y0, W0, H0) => { for (let y = Math.max(0, Y0 - 12); y < Math.min(s.h, Y0 + H0 + 12); y++) if (X0 - 12 < s.w) for (const plane of [s.page, s.kept]) if (plane) plane.fill(PAPER_TONE, y * s.w + Math.max(0, X0 - 12), y * s.w + Math.min(s.w, X0 + W0 + 12)); };
  if (TEST.loopRect) wipe(...TEST.loopRect);
  wipe(x0, y0, bw, bh); TEST.loopRect = [x0, y0, bw, bh];
  // Capped to the loop's width as it stands in the box (159 of its 186 high), as the wide loop was to the box's height.
  const fit = Math.min(bw, bh * 159 / 186), px = S.sizes[S.sizeSel], sz = Math.min(px, fit / 4.5), lineish = sz < fit / 7;
  const pts = loopPoints(bw, bh, lineish ? sz * 2 + 8 : sz * .55 + 6, x0, y0).map(p => ({ x: p.x, y: p.y, p: p.p, t: p.t, tx: NaN, ty: NaN }));
  try { layStroke(s, B, pts, sz, S.swatches[S.swSel], null); } catch (e) { console.error(e); }
  showPage(TEST.ctx, s, [0, 0, s.w - 1, s.h - 1]);
  floatButtons();
}
// A real pen gives pressure and tilt; a mouse or finger gets pressure from speed (slower is heavier), upright.
function penSample(e, L) {
  const cv = TEST.cv, r = cv.getBoundingClientRect(), x = (e.clientX - r.left) * cv.width / r.width, y = (e.clientY - r.top) * cv.height / r.height, isPen = e.pointerType === "pen";
  let p;
  if (isPen && e.pressure > 0) p = e.pressure;
  else {
    const dt = L.lastT ? Math.max(1, e.timeStamp - L.lastT) : 16, dd = L.lastX !== undefined ? Math.hypot(x - L.lastX, y - L.lastY) : 0;
    L.sp = L.sp * .8 + (dd / dt) * .2; L.d += dd;
    p = (.4 + .5 * smooth(L.d / 60)) * clamp(1.08 - .2 * L.sp, .45, 1);
  }
  L.lastT = e.timeStamp; L.lastX = x; L.lastY = y;
  const tx = isPen && (e.tiltX || e.tiltY) ? e.tiltX : NaN, ty = isPen && (e.tiltX || e.tiltY) ? e.tiltY : NaN;
  return [x, y, p, e.timeStamp, tx, ty];
}
function wireTest() {
  const cv = TEST.cv;
  cv.addEventListener("pointerdown", e => {
    if (!TEST.surf) return;
    if (S.picker) { S.picker = null; renderPicker(); }
    cv.setPointerCapture(e.pointerId);
    // Cut Stamp: the stroke is a lasso, in marching ants; it draws nothing.
    if (TEST.cutting) { const L = { sp: 0, d: 0, len: 0 }, q = penSample(e, L); L.lasso = [[q[0], q[1]]]; TEST.live = L; return; }
    if (TEST.eraser) {
      // Whatever floats or settles is laid first, then the eraser goes back to what can't be erased.
      printBath();
      if (TEST.water && TEST.water.wet) TEST.water.dryAll();
      showPage(TEST.ctx, TEST.surf, [0, 0, TEST.surf.w - 1, TEST.surf.h - 1]);
      TEST.live = { er: new Eraser(TEST.surf, ERASER_MAX[S.sizeSel] || 120), sp: 0, d: 0 };
      const r = TEST.live.er.add([penSample(e, TEST.live)]);
      if (r) showPage(TEST.ctx, TEST.surf, r);
      return;
    }
    // Anything still floating or settling is laid first: a brush that doesn't float prints the bath; a Float stroke lays
    // a settling wash where it is.
    const floats = cur().life.k === "float";
    if (!floats) printBath();
    if (floats && TEST.water && TEST.water.wet) { TEST.water.dryAll(); showPage(TEST.ctx, TEST.surf, [0, 0, TEST.surf.w - 1, TEST.surf.h - 1]); }
    // Grey Shift (newmods.js): a brush with it picks its grey at each touch, the toolbar's marked as the shade in use.
    let shade = S.swatches[S.swSel];
    if (typeof shiftShade === "function") {
      const p = shiftShade(cur(), shade, S.swatches); shade = p.shade;
      if (p.k >= 0 && shiftPalette(cur()) !== SHIFT.ANY && p.k !== S.swSel) { S.swSel = p.k; renderTools(); }
    }
    const st = new Stroke(TEST.surf, cur(), S.sizes[S.sizeSel], shade, HOST);
    TEST.live = { st, sp: 0, d: 0 };
    const r = st.add([penSample(e, TEST.live)]);
    if (r) showPage(TEST.ctx, TEST.surf, r);
    // A Spiro in Ring: its pinned ring dashed in light grey over the page (never in it), pulled out as the pen goes,
    // until the lift (newmods.js spiroGuide, spiroPull).
    const q0 = st.pts[0], ring = q0 && typeof spiroGuide === "function" ? spiroGuide(st.R, q0) : 0;
    if (ring > 0) { TEST.live.guide = { x: q0.x, y: q0.y, r: ring, pull: { cx: q0.x, cy: q0.y, r: 0, a0: 0, done: false } }; drawGuide(TEST.live.guide); }
    // A resting pen sends nothing: Float's drop and Stipple's Build-up tick on their own while it is down.
    if (st.fl || st.holds) { clearInterval(TEST.hold); TEST.hold = setInterval(() => { const L = TEST.live; if (!L || !(L.st.fl || L.st.holds)) return; const rr = L.st.holdTick(performance.now()); if (rr) showPage(TEST.ctx, TEST.surf, rr); }, 50); }
  });
  cv.addEventListener("pointermove", e => {
    const L = TEST.live; if (!L) return;
    const evs = e.getCoalescedEvents ? e.getCoalescedEvents() : [e];
    if (L.lasso) { for (const ev of evs.length ? evs : [e]) antsTo(L, penSample(ev, L)); return; }
    const qs = (evs.length ? evs : [e]).map(ev => penSample(ev, L)), G = L.guide;
    // The ring pulled out: the old one goes (its box drawn again from the page), the new one is drawn below.
    let pulled = false;
    if (G) for (const q of qs) if (spiroPull(G.pull, q[0], q[1]) && Math.abs(G.pull.r - G.r) >= .5) { if (!pulled) clearGuide(G); pulled = true; G.r = G.pull.r; }
    const r = (L.er || L.st).add(qs);
    if (r) showPage(TEST.ctx, TEST.surf, r);
    if ((r || pulled) && G) drawGuide(G);
  });
  const end = () => {
    const L = TEST.live; if (!L) return;
    TEST.live = null; clearInterval(TEST.hold);
    if (L.er) { setStatus("Erased."); return; }
    if (L.lasso) { lassoCut(L.lasso); return; }
    const r = L.st.end();
    if (r) showPage(TEST.ctx, TEST.surf, r);
    // The Spiro's ring goes.
    if (L.guide) clearGuide(L.guide);
    const st = L.st, t = st.times.slice().sort((a, b) => a - b), p95 = t[Math.floor(t.length * .95)] || 0;
    TEST.last = st;
    setStatus(`${st.samples} samples · ${(st.ms / Math.max(1, st.samples)).toFixed(2)} ms each (p95 ${p95.toFixed(2)}) · lift ${st.lift.toFixed(1)} ms · ${choiceWords(cur(), "life")}`);
    if (TEST.water && TEST.water.wet && !TEST.ticking) { TEST.ticking = true; setTimeout(tickWater, 1000 / TPS); }
    if (st.fl) {
      const b = st.fl.bath, c = b.cost;
      setStatus(`Floating: ${b.rings.length} rings, ${b.verts} points · ${isClearWater(cur()) ? "clear water" : "paint"} · prints ${(b.dryMs / 1000).toFixed(1)} s after your last touch, or tap Print · stroke ${(st.ms / Math.max(1, st.samples)).toFixed(2)} ms a sample, lift ${st.lift.toFixed(1)} ms · show ${(c.quick / Math.max(1, c.nQuick)).toFixed(1)} ms quick, ${(c.render / Math.max(1, c.nRender)).toFixed(1)} ms full`);
      if (!TEST.bathTicking) { TEST.bathTicking = true; setTimeout(tickBath, 1000 / TPS); }
      floatButtons();
    }
  };
  cv.addEventListener("pointerup", end); cv.addEventListener("pointercancel", end);
}
// A Spiro's pinned ring on the canvas over the page (never in it): light grey, 1.5 px, in the lasso's 12 px dashes,
// darker marks showing through; clearGuide draws its box again from the page.
function drawGuide(G) {
  const c = TEST.ctx;
  c.save(); c.globalCompositeOperation = "darken"; c.lineWidth = 1.5; c.strokeStyle = "#999"; c.setLineDash([12, 12]);
  c.beginPath(); c.arc(G.x, G.y, G.r, 0, 2 * Math.PI); c.stroke(); c.restore();
}
function clearGuide(G) { const k = Math.ceil(G.r) + 3; showPage(TEST.ctx, TEST.surf, [Math.floor(G.x) - k, Math.floor(G.y) - k, Math.ceil(G.x) + k, Math.ceil(G.y) + k]); }
// The lasso's marching ants to q, on the canvas over the page (never in it): dashes of 12 px in ink, 3 px wide.
function antsTo(L, q) {
  const [x0, y0] = L.lasso[L.lasso.length - 1], c = TEST.ctx;
  L.lasso.push([q[0], q[1]]);
  c.save(); c.setLineDash([12, 12]); c.lineDashOffset = -L.len; c.lineWidth = 3; c.strokeStyle = "#000"; c.lineCap = "butt";
  c.beginPath(); c.moveTo(x0, y0); c.lineTo(q[0], q[1]); c.stroke(); c.restore();
  L.len += Math.hypot(q[0] - x0, q[1] - y0);
}
// The lasso lifted: its ants go, and the ink inside it, trimmed, is the Stamp's nib (an Undo step), or nothing to cut.
function lassoCut(pts) {
  const xs = pts.map(q => q[0]), ys = pts.map(q => q[1]);
  showPage(TEST.ctx, TEST.surf, [Math.floor(Math.min(...xs)) - 4, Math.floor(Math.min(...ys)) - 4, Math.ceil(Math.max(...xs)) + 4, Math.ceil(Math.max(...ys)) + 4]);
  const nib = cutNib(TEST.surf, pts);
  if (!nib) { setStatus("Nothing to cut"); return; }
  TEST.cutting = false;
  const b = edit(); b.nib = keepNib(nib);
  changed("Stamp cut: " + nib.w + " × " + nib.h + " px.");
}
// Drying: the water keeps working after lift, ten ticks a second, until it is dry.
function tickWater() {
  const W = TEST.water;
  if (!W || !W.wet || W.s !== TEST.surf) { TEST.ticking = false; return; }
  W.step();
  const d = W.takeDirty();
  if (d && !TEST.live) showPage(TEST.ctx, TEST.surf, d);
  setTimeout(tickWater, 1000 / TPS);
}

// ---------- what the lab does ----------
function newRoll(style, old) {
  const b = rollStyle(style, old, locks());
  if (old && locks().parts.has("hand")) b.hand = cloneBrush(old.hand);
  return b;
}
function nearestSize(px) { let best = 0; S.sizes.forEach((s, i) => { if (Math.abs(Math.log(s / px)) < Math.abs(Math.log(S.sizes[best] / px))) best = i; }); return best; }
const ACT = {
  back: () => setStatus("Back to the drawing, with slot " + (S.sel + 1) + " picked."),
  mode: t => { S.mode = t.dataset.v; S.open = null; S.styleOpen = false; S.vars = S.mode === "advanced" ? makeVars(cur(), locks()) : []; saveState(); renderLab(); setStatus(S.mode === "simple" ? "Simple: one slider per part, styles and Randomize." : "Advanced: lock parts, open a part's dials, six variations."); },
  code: () => { S.codeOpen = !S.codeOpen; S.loadNote = ""; renderBar(); renderSheet(); },
  copy: () => { const v = $("code").value; try { navigator.clipboard.writeText(v).then(() => setStatus("Copied the share code."), () => { $("code").select(); setStatus("Select and copy the code."); }); } catch (e) { $("code").select(); } },
  load: () => {
    const r = readAnyCode($("paste").value);
    if (!r) { S.loadNote = "That isn't a brush code."; renderSheet(); setStatus("That isn't a brush code."); return; }
    remember(); setCur(r.b); S.loadNote = "";
    const from = { M: "an M code", I: "an app pen code; it draws as before", P: "an app paint code; it draws as before", B: "a pen-paint-blend code" }[r.from[0]] || "a prototype code";
    S.codeOpen = false; changed("Loaded " + r.b.name + " (" + from + ") into slot " + (S.sel + 1) + ".");
  },
  undo: () => { const h = S.history.pop(); if (!h) return; S.slots = h.slots; S.sel = h.sel; changed("Undone."); },
  save: () => {
    const b = cur();
    if (b.builtin || library().some(s => sameBrush(s, b))) { setStatus(b.name + " is in the library already."); return; }
    S.library.unshift(cloneBrush(b)); S.libPage = 0; saveState(); renderUnder(); setStatus("Saved " + b.name + " to the library.");
  },
  rename: () => { S.renaming = true; renderBar(); },
  renamed: () => finishRename(),
  clear: () => { blankPage(TEST.surf); TEST.water = null; TEST.bath = null; seedMarks(); redrawSample(); floatButtons(); setStatus("Cleared."); },
  printbath: () => printBath("Printed."),
  cutstamp: () => { TEST.cutting = !TEST.cutting; setEraser(false); cutButton(); setStatus(TEST.cutting ? "Draw round the shape to cut." : "Slot " + (S.sel + 1) + ": " + cur().name + "."); },
  eraser: () => { setEraser(!TEST.eraser); setStatus(TEST.eraser ? "Eraser, " + (ERASER_MAX[S.sizeSel] || 120) + " px." : "Slot " + (S.sel + 1) + ": " + cur().name + "."); },
  tilt: () => { TEST.tiltI = (TEST.tiltI + 1) % TILTS.length; const [, x, y] = TILTS[TEST.tiltI]; TEST.tilt = { x, y }; floatButtons();
    setStatus(TEST.tiltI ? "Page tilted." : "Page level."); },
  card: t => {
    const part = t.dataset.part;
    if (S.mode === "advanced") { const same = S.open === part; S.open = same ? null : part; S.swap = same ? null : part; }
    else S.swap = S.swap === part ? null : part;
    S.styleOpen = false; renderLab();
    setStatus(S.swap ? ROLE[S.swap].label + ": pick one below. " + ROLE[S.swap].blurb : "Slot " + (S.sel + 1) + ".");
  },
  closecard: () => { S.open = null; S.swap = null; renderLab(); },
  closeswap: () => { S.swap = null; if (S.mode === "advanced") S.open = null; renderLab(); },
  swapto: t => {
    const part = S.swap, k = t.dataset.k, it = swapList(part).find(x => x.k === k);
    if (!it || it.now) return;
    if (isLocked(part)) { setStatus(ROLE[part].label + " is locked."); return; }
    remember(); const c = it.b; c.name = cur().builtin ? "my-" + cur().name.toLowerCase() : cur().name; delete c.builtin; delete c.blurb; setCur(c);
    changed(ROLE[part].label + ": " + it.label + ".");
  },
  choose: t => {
    const part = t.dataset.part, k = t.dataset.k, B = cur(); if (B[part].k === k) return;
    const c = edit();
    if (part === "hand") { const P = freshPart("hand", k); if (k === "custom") Object.assign(P.d, B.hand.d); P.d.gain = B.hand.d.gain; P.d.dir = B.hand.d.dir; c.hand = normPart("hand", P); }
    else c[part] = switchChoice(part, B[part], k);
    normBrush(c); changed(ROLE[part].label + ": " + choiceOf(part, k).label + ".");
  },
  dialchoice: t => { const { part, key } = t.dataset, c = edit(); c[part].d[key] = +t.dataset.i; normBrush(c); changed(dialLabel(part, c[part].k, key) + ": " + optLabel(part, c[part].k, key, +t.dataset.i) + "."); },
  // Grey Shift's set (newmods.js): a grey taken in or left out; the last one stays.
  greyset: t => { const b = cloneBrush(cur()); if (!shiftToggle(b, +t.dataset.i, S.swatches)) return; const c = edit(); c.hand.d = b.hand.d; normBrush(c); changed(shiftSet(c, S.swatches).length + " of " + shiftSize(c, S.swatches) + " greys."); },
  cell: t => {
    const key = t.dataset.key, c = edit(), v = c.hand.d[key] || 0, nv = v < .17 ? .5 : v < .75 ? 1 : 0;
    c.hand.d[key] = nv; c.hand.k = matchHand(c.hand); normBrush(c);
    changed(DIALS.hand[key].label + ": " + cellLevel(nv) + ".");
  },
  lockpart: t => { const p = t.dataset.part; S.locks.parts.has(p) ? S.locks.parts.delete(p) : S.locks.parts.add(p); renderCol(); setStatus(S.locks.parts.has(p) ? ROLE[p].label + " is kept when you roll." : ROLE[p].label + " rolls again."); },
  lockdial: t => { const k = t.dataset.part + "." + t.dataset.key; S.locks.dials.has(k) ? S.locks.dials.delete(k) : S.locks.dials.add(k); renderCol(); },
  lockrow: t => { const keys = HAND_ROWS.find(r => r[1] === t.dataset.row)[2].map(k => "hand." + k), all = keys.every(k => S.locks.dials.has(k)); keys.forEach(k => all ? S.locks.dials.delete(k) : S.locks.dials.add(k)); renderCol(); },
  dicepart: t => { const p = t.dataset.part; if (isLocked(p)) return; remember(); setCur(rollOnePart(cur(), p, S.locks)); changed(ROLE[p].label + " rolled: " + choiceWords(cur(), p) + "."); },
  dicedial: t => { const { part, key } = t.dataset; if (dialLocked(part, key)) return; remember(); setCur(rollOneDial(cur(), part, key)); changed(dialLabel(part, cur()[part].k, key) + " rolled."); },
  dicerow: t => {
    const keys = HAND_ROWS.find(r => r[1] === t.dataset.row)[2];
    remember(); let b = cur(); for (const k of keys) if (!dialLocked("hand", k)) b = rollOneDial(b, "hand", k); setCur(b); changed("Hand rolled.");
  },
  randomize: () => { const old = cur(); remember(); setCur(newRoll(old.style || WILDCARD, old)); changed("Randomized: " + cur().name + (S.mode === "advanced" && (S.locks.parts.size || S.locks.dials.size) ? ", keeping what's locked." : ".")); },
  styles: () => { S.styleOpen = !S.styleOpen; renderCol(); },
  style: t => {
    const name = t.dataset.style, old = cur();
    S.styleOpen = false; remember(); setCur(newRoll(name, old));
    S.sizeSel = nearestSize(styleSize(name, cur()));
    changed("A new " + name + ": " + cur().name + ".");
  },
  var: t => { const v = S.vars[+t.dataset.i]; if (!v) return; remember(); setCur(cloneBrush(v.b)); changed(v.b.name + " is in slot " + (S.sel + 1) + "."); },
  vars: () => { S.vars = makeVars(cur(), locks()); renderUnder(); setStatus("Six new variations."); },
  slot: t => { const i = +t.dataset.i; if (TEST.eraser) { setEraser(false); if (i === S.sel) { setStatus("Slot " + (i + 1) + ": " + cur().name + "."); return; } } if (i === S.sel) return; S.sel = i; S.swap = null; S.open = null; changed("Slot " + (i + 1) + ": " + cur().name + "."); },
  libcard: t => {
    const b = library()[+t.dataset.i]; if (!b) return;
    if (sameBrush(b, cur())) { setStatus(b.name + " is in slot " + (S.sel + 1) + " already."); return; }
    remember(); setCur(b); changed(b.name + " is in slot " + (S.sel + 1) + ".");
  },
  // A random library brush into the selected slot, never the one it holds; the library turns to its page.
  surprise: () => {
    const list = library(), pool = list.map((b, i) => i).filter(i => !sameBrush(list[i], cur()));
    if (!pool.length) return;
    const i = pool[(Math.random() * pool.length) | 0], m = MET, perPage = m.libCols * m.libRows;
    S.libPage = Math.floor(i / perPage); remember(); setCur(list[i]);
    changed("Surprise: " + list[i].name + " is in slot " + (S.sel + 1) + ".");
  },
  prev: () => { S.libPage--; renderUnder(); },
  next: () => { S.libPage++; renderUnder(); },
  size: t => { S.sizeSel = +t.dataset.i; renderTools(); redrawSample(); saveState(); setStatus("Test size: " + S.sizes[S.sizeSel] + " px, " + mmOf(S.sizes[S.sizeSel]) + "."); },
  swatch: t => { S.swSel = +t.dataset.i; renderTools(); renderCol(); renderUnder(); redrawSample(); saveState(); setStatus("Shade: " + Math.round(S.swatches[S.swSel] * 100) + "%."); },
  sizepick: t => { S.picker = { kind: "size", i: +t.dataset.i }; S.sizeSel = +t.dataset.i; renderTools(); },
  shadepick: t => { S.picker = { kind: "shade", i: +t.dataset.i }; S.swSel = +t.dataset.i; renderTools(); },
  pickdone: () => { S.picker = null; renderTools(); renderCol(); renderUnder(); redrawSample(); saveState(); },
  sizestop: t => { const px = +t.dataset.px; S.sizes[S.picker.i] = px; const r = $("sizerange"); if (r) r.value = sizeT(px); showSizePick(); drawFaces(); redrawSample(); },
  grey: t => { S.swatches[S.picker.i] = +t.dataset.d; renderTools(); redrawSample(); }
};
function drawFaces() { document.querySelectorAll("#tools .tgroup:first-child canvas").forEach((c, i) => drawSizeFace(c, S.sizes[i])); }
function onClick(e) {
  const t = e.target.closest("[data-act]");
  if (e.target.closest("input")) return;
  if (!t || t.disabled) return;
  if (t.dataset.act === "sizepick" || t.dataset.act === "shadepick") e.stopPropagation();
  ACT[t.dataset.act]?.(t, e);
}
let dragging = null;
function onInput(e) {
  const t = e.target; if (t.type !== "range") return;
  if (t.id === "sizerange") { const px = sizePx(+t.value); S.sizes[S.picker.i] = px; showSizePick(); drawFaces(); return; }
  const { part, key } = t.dataset; if (!part) return;
  if (dragging !== t) { dragging = t; edit(); const u = document.querySelector('[data-act="undo"]'); if (u) u.disabled = false; }
  const c = cur(); c[part].d[key] = +t.value;
  if (part === "hand") c.hand.k = matchHand(c.hand);
  const v = document.querySelector(`[data-val="${part}.${key}"]`); if (v) v.textContent = fmtDial(part, key, +t.value);
}
function onChange(e) {
  const t = e.target; if (t.type !== "range") return;
  if (t.id === "sizerange") { redrawSample(); saveState(); return; }
  const { part, key } = t.dataset; if (!part) return;
  dragging = null; normBrush(cur());
  changed(dialLabel(part, cur()[part].k, key) + " set to " + fmtDial(part, key, cur()[part].d[key]) + ".");
}

// ---------- checks and the benchmark, outside the frame ----------
function runChecks() {
  const out = $("checks"); $("checksect").hidden = false; out.textContent = "Running…";
  setTimeout(() => {
    const t0 = performance.now(), r = selfTest(), lines = [];
    lines.push((r.ok ? "PASS" : "FAIL") + " · " + Math.round(performance.now() - t0) + " ms");
    lines.push("", "Old app codes draw as before (the unchanged port vs this engine, same stroke and paper; pixels that differ at 16 greys):");
    for (const l of r.legacy) lines.push(`  ${l.ok ? "ok  " : "FAIL"} ${l.code}  differ ${l.diff} of ${l.marked} marked`);
    lines.push("", "Share codes: 300 rolled brushes, M code out and back: " + (r.trips.length ? "FAIL " + r.trips.slice(0, 3).join(" ") : "ok"));
    lines.push("", "Prototype codes load into the parts they describe:");
    for (const p of r.proto) lines.push(`  ${p.ok ? "ok  " : "FAIL"} ${p.what}: ${p.code}`);
    lines.push("", "Float (suminagashi):");
    for (const f of r.float) lines.push(`  ${f.ok ? "ok  " : "FAIL"} ${f.what}`);
    lines.push("", "Erasable:");
    for (const f of r.erase) lines.push(`  ${f.ok ? "ok  " : "FAIL"} ${f.what}`);
    lines.push("", "Names:");
    for (const f of r.names) lines.push(`  ${f.ok ? "ok  " : "FAIL"} ${f.what}`);
    const sw = sweepCheck();
    lines.push("", `Every combination draws (Tip × Medium × Carry × Life, papers and hands rotating): ${sw.n} combinations, ${sw.bad.length ? sw.bad.length + " FAIL: " + sw.bad.slice(0, 4).join("; ") : "all draw"}`);
    out.textContent = lines.join("\n");
    console.log((r.ok && !sw.bad.length ? "SELFTEST PASS" : "SELFTEST FAIL") + "\n" + lines.join("\n"));
  }, 30);
}
function sweepCheck() {
  const W = 360, H = 200, s = modSurface(W, H, 7), bad = [], base = rollStyle("Fineliner");
  let n = 0;
  for (const tip of CHOICES.tip) for (const med of CHOICES.medium) for (const car of CHOICES.carry) for (const life of CHOICES.life) {
    const B = cloneBrush(base);
    B.tip = switchChoice("tip", B.tip, tip.k); B.medium = switchChoice("medium", B.medium, med.k); B.carry = switchChoice("carry", B.carry, car.k); B.life = switchChoice("life", B.life, life.k);
    B.paper = freshPart("paper", CHOICES.paper[n % 6].k); B.hand = freshPart("hand", CHOICES.hand[n % 8].k); normBrush(B); n++;
    blankPage(s);
    for (let y = 80; y < 120; y++) for (let x = 150; x < 190; x++) s.page[y * W + x] = 60;
    const size = med.k === "ink" || med.k === "charcoal" || med.k === "graphite" ? 10 : 40;
    layStroke(s, B, wavePath(W, H, 30, 50, { tilt: n % 2 === 0 }), size, 1, null);
    let marked = 0, nan = 0;
    for (let i = 0; i < W * H; i++) { const v = s.page[i]; if (!(v === v)) nan++; else if (quantTone(v) < 238) marked++; }
    // Resist marks its own plane, not the page: it counts where it shields.
    if (nan || marked < 100 && resistMarked(s) < 100) bad.push([tip.k, med.k, car.k, life.k].join("/"));
  }
  return { n, bad };
}
function runBench() {
  const out = $("checks"); $("checksect").hidden = false; out.textContent = "Benchmarking… (a few seconds)";
  setTimeout(() => {
    const W = 1400, H = 900, s = modSurface(W, H, 3), rows = [];
    for (const style of STYLE_NAMES) {
      const keep = Math.random; Math.random = rng(77); let B; try { B = rollStyle(style); } finally { Math.random = keep; }
      for (const px of [S.sizes[0], S.sizes[1], S.sizes[2], 360]) {
        blankPage(s);
        const pts = wavePath(W, H, 200, 220, { tilt: true });
        const { st } = layStroke(s, B, pts, px, 1, null);
        const t = st.times.slice().sort((a, b) => a - b);
        rows.push(`${style.padEnd(11)} ${(choiceWords(B, "tip") + " · " + choiceWords(B, "medium")).padEnd(30)} ${String(px).padStart(4)} px  ${(st.ms / st.samples * 1000).toFixed(0).padStart(5)} µs/sample  p95 ${(t[Math.floor(t.length * .95)] * 1000).toFixed(0).padStart(5)} µs  lift ${st.lift.toFixed(1).padStart(5)} ms`);
      }
    }
    out.textContent = "Per-sample cost in this browser, one 1400 × 900 wave per brush (Fast-wet unless the style says otherwise):\n" + rows.join("\n");
  }, 30);
}

// ---------- start ----------
async function start() {
  initViewer();
  await loadTextures();
  BUILTINS = builtins();
  if (!loadState()) { seedLibrary(); if (document.documentElement.clientWidth < 600) V.device = "nomad"; }
  addFloatPair(); addErasePresets();
  if (S.mode === "advanced") S.vars = makeVars(cur(), locks());
  viewerButtons();
  buildLab();
  if (/selftest/.test(location.hash + location.search)) runChecks();
  window.LAB = { S, cur, ACT, TEST, encodeM, readAnyCode, renderLab, metrics, library, floatPair, saveState };
}
start();
