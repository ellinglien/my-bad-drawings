// ===================================================================================================================
// Brush Lab Dials: the page. Every part, choice and dial of a Scribulor brush (tiles.js), each a tile where the same
// stroke is drawn again as one thing moves. Only the tiles on screen or near it are drawn, by a few workers, and only
// the tiles on screen are animated; frames for tiles far off are let go and drawn again when needed.
// ===================================================================================================================
const $ = id => document.getElementById(id);
const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text !== undefined) e.textContent = text; return e; };
const TILES = demoTiles();
const MOST_BYTES = 64e6, MOST_PUTS = 12;   // frames held, and canvases redrawn a screen frame

// ---------- the page ----------
function buildPage() {
  const nav = $("parts"), main = $("main");
  DEMO_PARTS.forEach((part, n) => {
    const a = el("a", "", ROLE[part].label); a.href = "#" + part; nav.appendChild(a);
    const sec = el("section", "part"); sec.id = part;
    const head = el("header", "part-head");
    head.appendChild(el("span", "num", String(n + 1).padStart(2, "0")));
    head.appendChild(el("h2", "", ROLE[part].label));
    sec.appendChild(head);
    sec.appendChild(el("p", "blurb", ROLE[part].blurb));
    if (PART_INTRO[part]) sec.appendChild(el("p", "intro", PART_INTRO[part]));
    for (const [choice, name] of [[true, "Choices"], [false, "Dials"]]) {
      const mine = TILES.filter(t => t.part === part && t.choice === choice);
      if (!mine.length) continue;
      sec.appendChild(el("h3", "group", name));
      const g = el("div", "grid"); sec.appendChild(g);
      for (const t of mine) g.appendChild(tileEl(t));
    }
    main.appendChild(sec);
  });
}
function tileEl(t) {
  const card = el("article", "tile" + (t.choice ? " choice" : "")); t.el = card; card.dataset.tile = t.id;
  const head = el("header", "tile-head");
  head.appendChild(el("h4", "", t.title));
  if (t.S.tilt) head.appendChild(el("span", "tag", "Page tilted"));
  t.valEl = el("span", "val", ""); head.appendChild(t.valEl);
  card.appendChild(head);
  if (t.sub) card.appendChild(el("div", "sub", t.sub));
  const frame = el("div", "frame");
  t.cv = el("canvas"); t.cv.width = DEMO_W; t.cv.height = DEMO_H; t.ctx = t.cv.getContext("2d");
  t.cv.setAttribute("role", "img"); t.cv.setAttribute("aria-label", t.title + ": " + t.say);
  frame.appendChild(t.cv);
  t.strip = el("div", "strip"); frame.appendChild(t.strip);
  t.wait = el("span", "wait", "Drawing…"); frame.appendChild(t.wait);
  card.appendChild(frame);
  card.appendChild(trackEl(t));
  if (t.say) card.appendChild(el("p", "say", t.say));
  const w = withWords(t);
  if (w) card.appendChild(el("div", "on", w));
  if (t.also && t.also.length > 1) card.appendChild(el("div", "on", "On " + t.also.join(", ")));
  t.frames = []; t.bytes = 0;
  return card;
}
// Under a tile: a rule with a thumb and its two ends for a range; segments for options; a clock for time.
function trackEl(t) {
  const box = el("div", "track");
  if (t.sweep === "still") { box.classList.add("blank"); return box; }
  if (t.sweep === "range" || (t.sweep === "after" && !DIALS[t.part][t.key].choices)) {
    const rule = el("div", "rule"); t.thumb = el("i"); rule.appendChild(t.thumb); box.appendChild(rule);
    const ends = el("div", "ends"); ends.appendChild(el("span", "", t.ends[0])); ends.appendChild(el("span", "", t.ends[t.ends.length - 1])); box.appendChild(ends);
  } else if (t.sweep === "options" || t.sweep === "after") {
    const seg = el("div", "segs"); t.segs = t.names.map(n => { const s = el("span", "", n); seg.appendChild(s); return s; }); box.appendChild(seg);
  }
  if (t.sweep === "time" || t.sweep === "after") {
    const bar = el("div", "clock"); t.clockBar = el("i"); bar.appendChild(t.clockBar);
    if (t.sweep === "time") { t.liftMark = el("b"); bar.appendChild(t.liftMark); }
    box.appendChild(bar);
    const ends = el("div", "ends center"); t.clockText = el("span", "now", ""); ends.appendChild(t.clockText); box.appendChild(ends);
  }
  return box;
}
const secs = ms => (ms / 1000).toFixed(ms < 9950 ? 1 : 0) + " s";

// ---------- drawing: a few workers running the engine, or the page itself ----------
const R = { workers: [], idle: [], inPage: false, done: 0, bytes: 0 };
function startWorkers() {
  const src = $("engine").textContent + "\n;\n" + $("render").textContent + "\n;\n" +
    "self.onmessage = e => { const m = e.data; if (m.textures) { for (const k in m.textures) TEXTURES[k] = m.textures[k]; return; }\n" +
    "  let out; try { out = demoJob(m); } catch (err) { self.postMessage({ id: m.id, i: m.i, error: String(err && err.stack || err) }); return; }\n" +
    "  self.postMessage(out, out.frames.map(f => f.buffer)); };";
  const n = Math.max(1, Math.min(3, (navigator.hardwareConcurrency || 2) - 1));
  try {
    const url = URL.createObjectURL(new Blob([src], { type: "text/javascript" }));
    for (let i = 0; i < n; i++) {
      const w = new Worker(url);
      w.onmessage = e => { w.job = null; R.idle.push(w); done(e.data); pump(); };
      w.onerror = e => { if (e.preventDefault) e.preventDefault(); fallBack(); };
      w.postMessage({ textures: TEXTURES });
      R.workers.push(w); R.idle.push(w);
    }
  } catch (e) { fallBack(); }
}
function fallBack() {
  if (R.inPage) return;
  R.inPage = true;
  for (const w of R.workers) try { w.terminate(); } catch (e) { /* gone already */ }
  R.workers = []; R.idle = [];
  for (const t of TILES) t.pending = null;   // what was out with a worker goes back on the queue
  pump();
}
const jobsOf = t => t.sweep === "time" ? 1 : t.values.length;
// The next job: a tile on screen first, then one near it; its frames in order first, last, then between.
function nextJob() {
  let best = null, bestScore = Infinity;
  for (const t of TILES) {
    if (!(t.near || STATE.all) || t.complete) continue;
    const have = t.frames.filter(Boolean).length + (t.pending ? t.pending.size : 0);
    if (have >= jobsOf(t)) continue;
    const score = (t.visible ? 0 : 1000) + have * 10 + t.id * .01;
    if (score < bestScore) { best = t; bestScore = score; }
  }
  if (!best) return null;
  const n = jobsOf(best), order = [...new Set([0, n - 1, ...Array.from({ length: n }, (_, i) => i)])];
  const i = order.find(j => !best.frames[j] && !(best.pending && best.pending.has(j)));
  (best.pending ??= new Set()).add(i);
  return tileJob(best, i);
}
let pumping = false;
function pump() {
  if (R.inPage) {
    if (pumping) return;
    pumping = true;
    const step = () => {
      const job = nextJob();
      if (!job) { pumping = false; return; }
      try { done(demoJob(job)); } catch (e) { done({ id: job.id, i: job.i, error: String(e && e.stack || e) }); }
      setTimeout(step, 0);
    };
    setTimeout(step, 0);
    return;
  }
  while (R.idle.length) { const job = nextJob(); if (!job) return; R.idle.pop().postMessage(job); }
}
function done(m) {
  const t = TILES[m.id];
  R.done++;
  if (t.pending) t.pending.delete(m.i);
  if (m.error) { console.error("tile " + t.title + ": " + m.error); t.complete = t.failed = true; t.wait.textContent = "Couldn't draw this one."; return; }
  t.frames[m.i] = m.frames;
  if (m.times) { (t.times ??= [])[m.i] = m.times; }
  if (t.sweep === "time") { t.lift = m.lift; t.dry = m.dry; }
  const b = m.frames.reduce((a, f) => a + f.length, 0); t.bytes += b; R.bytes += b;
  t.complete = t.frames.filter(Boolean).length === jobsOf(t);
  t.stored = performance.now();
  t.wait.hidden = true;
  if (!t.t0) t.t0 = performance.now();
  t.shown = null;
  if (STATE.strip) drawStrip(t); else drawTile(t, performance.now());
  evict();
}
// Frames of tiles far from the screen go first, oldest first, once more than MOST_BYTES are held.
function evict() {
  if (STATE.all || R.bytes <= MOST_BYTES) return;
  const far = TILES.filter(t => t.bytes && !t.near).sort((a, b) => a.stored - b.stored);
  for (const t of far) {
    if (R.bytes <= MOST_BYTES * .8) break;
    R.bytes -= t.bytes; t.bytes = 0; t.frames = []; t.times = null; t.complete = false; t.pending = null; t.t0 = 0; t.wait.hidden = false;
  }
}

// ---------- the clock: every tile on screen shows its frame for now ----------
const STATE = { playing: true, strip: false, pausedAt: 0, all: /[?&]all\b/.test(location.search) };
const IMG = new ImageData(DEMO_W, DEMO_H), PIX = new Uint32Array(IMG.data.buffer);
const LUT = new Uint32Array(16).map((_, v) => (0xff000000 | (v * 17) * 0x10101) >>> 0);
function putFrame(ctx, lv) { for (let i = 0; i < lv.length; i++) PIX[i] = LUT[lv[i]]; ctx.putImageData(IMG, 0, 0); }
const ease = f => f * f * (3 - 2 * f);
// Which frame at ms since the tile started: { i (job), j (frame in it), f (thumb, 0 to 1), clock words }.
function frameAt(t, ms) {
  const n = t.values.length;
  if (t.sweep === "still") return { i: 0, j: 0 };
  if (t.sweep === "range") {
    const run = 3000, hold = 1100, x = ms % (2 * (run + hold));
    const f = ease(x < run ? x / run : x < run + hold ? 1 : x < 2 * run + hold ? 1 - (x - run - hold) / run : 0);
    return { i: Math.round(f * (n - 1)), j: 0, f };
  }
  if (t.sweep === "options") { const step = 1100, i = Math.min(n - 1, Math.floor(ms % (n * step + 600) / step)); return { i, j: 0, f: i / Math.max(1, n - 1) }; }
  if (t.sweep === "time") {
    // The pen at drawing speed, then what follows lift played in about six seconds at most, then a pause.
    const times = t.times && t.times[0]; if (!times) return { i: 0, j: 0 };
    const after = t.dry - t.lift, rate = Math.max(1, after / 6000), play = t.lift + after / rate, x = ms % (play + 1500);
    const at = x < t.lift ? x : Math.min(t.dry, t.lift + (x - t.lift) * rate);
    let j = 0; while (j + 1 < times.length && times[j + 1] <= at) j++;
    const words = at < t.lift ? "Pen down " + secs(at) : at >= t.dry ? (t.dry > t.lift + 1 ? "Done " + secs(t.dry - t.lift) + " after lift" : "Done at lift") : secs(at - t.lift) + " after lift";
    return { i: 0, j, clock: [Math.min(1, at / Math.max(1, t.dry)), t.lift / Math.max(1, t.dry)], words };
  }
  // after: each setting in turn, its pictures over time after lift (the last once it has settled).
  const per = (t.S.after.length) * 600 + 1200, i = Math.floor(ms % (n * per) / per), x = ms % per, j = Math.min(t.S.after.length, Math.floor(x / 600));
  const times = t.times && t.times[i], at = times ? times[Math.min(j, times.length - 1)] : 0, end = times ? times[times.length - 1] : 1;
  const words = j >= t.S.after.length ? "Done " + secs(end) + " after lift" : at ? secs(at) + " after lift" : "At lift";
  return { i, j, f: i / Math.max(1, n - 1), clock: [j >= t.S.after.length ? 1 : at / Math.max(1, end)], words };
}
let puts = 0;
function drawTile(t, now) {
  if (!t.frames.length || STATE.strip) return;
  const ms = Math.max(0, (STATE.playing ? now : STATE.pausedAt) - (t.t0 || now)), at = frameAt(t, ms);
  // A frame not drawn yet: the nearest one that is.
  let i = at.i;
  if (!t.frames[i]) { let d = 1; while (d < 20 && !t.frames[i - d] && !t.frames[i + d]) d++; i = t.frames[i - d] ? i - d : i + d; }
  const fr = t.frames[i]; if (!fr) return;
  const j = Math.min(at.j, fr.length - 1), key = i + "/" + j;
  if (key !== t.shown && puts < MOST_PUTS) { putFrame(t.ctx, fr[j]); t.shown = key; puts++; }
  const name = t.names[at.i] || "";
  if (t.valEl.textContent !== name) t.valEl.textContent = name;
  if (t.thumb && at.f !== undefined) t.thumb.style.left = (at.f * 100).toFixed(2) + "%";
  if (t.segs) t.segs.forEach((s, k) => s.classList.toggle("on", k === at.i));
  if (at.clock) {
    t.clockBar.style.width = (at.clock[0] * 100).toFixed(1) + "%";
    if (t.liftMark) t.liftMark.style.left = (at.clock[1] * 100).toFixed(1) + "%";
    if (t.clockText.textContent !== at.words) t.clockText.textContent = at.words;
  }
}
function tick(now) {
  puts = 0;
  for (const t of TILES) if (t.visible) drawTile(t, now);
  requestAnimationFrame(tick);
}

// ---------- the step strip (reduced motion, or Steps): a few moments side by side ----------
function stripPicks(t) {
  if (t.sweep === "still") return [{ i: 0, j: 0, label: "" }];
  if (t.sweep === "time") {
    const times = t.times[0], last = times.length - 1, want = [t.lift * .5, t.lift, t.lift + 1000, (t.lift + t.dry) / 2, t.dry];
    return want.map((ms, k) => { let j = 0; while (j < last && times[j + 1] <= ms) j++; return { i: 0, j, label: ["Pen down", "At lift", "1 s after", "Halfway", "Done"][k] }; });
  }
  if (t.sweep === "after") return t.values.flatMap((_, i) => t.frames[i] ? [{ i, j: 0, label: t.names[i] + ", at lift" }, { i, j: t.frames[i].length - 1, label: t.names[i] + ", done" }] : []);
  const n = t.values.length, picks = n <= 5 ? t.values.map((_, i) => i) : [0, .25, .5, .75, 1].map(f => Math.round(f * (n - 1)));
  return picks.map(i => ({ i, j: 0, label: t.names[i] }));
}
function drawStrip(t) {
  if (!t.complete) return;
  t.strip.textContent = "";
  for (const { i, j, label } of stripPicks(t)) {
    const fig = el("figure"), c = el("canvas"); c.width = DEMO_W; c.height = DEMO_H;
    putFrame(c.getContext("2d"), t.frames[i][j]);
    fig.appendChild(c); if (label) fig.appendChild(el("figcaption", "", label));
    t.strip.appendChild(fig);
  }
}
function setStrip(on) {
  STATE.strip = on;
  document.body.classList.toggle("stepped", on);
  $("steps").setAttribute("aria-pressed", String(on));
  $("steps").textContent = on ? "Animate" : "Steps";
  $("play").hidden = on;
  for (const t of TILES) { if (on) drawStrip(t); else { t.shown = null; drawTile(t, performance.now()); } }
}
function setPlaying(on) {
  const now = performance.now();
  if (!on) STATE.pausedAt = now;
  else for (const t of TILES) if (t.t0) t.t0 += now - STATE.pausedAt;
  STATE.playing = on;
  $("play").textContent = on ? "Pause" : "Play";
  $("play").setAttribute("aria-pressed", String(!on));
}

// ---------- start ----------
async function start() {
  buildPage();
  if (/[?#&]steps\b/.test(location.search + location.hash) || matchMedia("(prefers-reduced-motion: reduce)").matches) setStrip(true);
  $("play").onclick = () => setPlaying(!STATE.playing);
  $("steps").onclick = () => setStrip(!STATE.strip);
  await loadTextures();
  if (/[?&]inpage\b/.test(location.search) || typeof Worker === "undefined") R.inPage = true; else startWorkers();
  const near = new IntersectionObserver(es => { for (const e of es) TILES[+e.target.dataset.tile].near = e.isIntersecting; pump(); }, { rootMargin: "600px 0px" });
  const vis = new IntersectionObserver(es => { for (const e of es) { const t = TILES[+e.target.dataset.tile]; t.visible = e.isIntersecting; t.shown = null; } pump(); });
  for (const t of TILES) { near.observe(t.el); vis.observe(t.el); }
  // The part being read is filled in the bar.
  const links = [...$("parts").querySelectorAll("a")], parts = DEMO_PARTS.map(p => $(p));
  const here = () => {
    const top = $("parts").getBoundingClientRect().bottom + 40;
    let k = -1; parts.forEach((sec, i) => { if (sec.getBoundingClientRect().top <= top) k = i; });
    links.forEach((a, i) => a.classList.toggle("here", i === k));
  };
  addEventListener("scroll", here, { passive: true }); here();
  requestAnimationFrame(tick);
  if (STATE.all) pump();
  document.documentElement.dataset.ready = "1";
}
window.DEMO = { TILES, R, STATE };
start();
