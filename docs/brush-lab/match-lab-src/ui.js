// ===================================================================================================================
// Match Lab: the page. Stroke (photo → analysis → fit in a Web Worker → brush, code, test pad) and Paper (photo →
// tile → previews, downloads). The core (engine, analysis, fit, paper, samples) is the <script id="core"> above; the
// worker is made from that same text.
// ===================================================================================================================
const $ = id => document.getElementById(id);
const S = { tab: "stroke", photo: null, rgb: null, crop: null, job: null, flip: false, res: null, busy: false, tip: "", category: "",
  sheet: null, sheetRGB: null, paperBox: null, grain: 0, paper: null, imported: null, padPaper: "own", set: [] };
const BUDGET_MS = 5000;
const DPR = Math.min(2, window.devicePixelRatio || 1);
const PART_NAMES = { tip: "Tip", medium: "Medium", paper: "Paper", life: "Drying", hand: "Control", carry: "Smudge" };
const TEX_OK = { done: false };
function showErr(id, msg) { const e = $(id); e.hidden = !msg; e.textContent = msg || ""; }

// ---------- images in ----------
// Any image (file, paste, drop, sample) → an RGB canvas at most 1600 px on its long side.
function canvasOf(src, w, h) {
  const k = Math.min(1, 1600 / Math.max(w, h)), c = document.createElement("canvas");
  c.width = Math.max(1, Math.round(w * k)); c.height = Math.max(1, Math.round(h * k));
  const x = c.getContext("2d"); x.fillStyle = "#fff"; x.fillRect(0, 0, c.width, c.height); x.drawImage(src, 0, 0, c.width, c.height);
  return c;
}
function loadFile(file, then) {
  if (!file || !/^image\//.test(file.type)) return;
  const url = URL.createObjectURL(file), img = new Image();
  img.onload = () => { URL.revokeObjectURL(url); then(canvasOf(img, img.naturalWidth, img.naturalHeight)); };
  img.onerror = () => { URL.revokeObjectURL(url); then(null); };
  img.src = url;
}
function canvasFromRGB(s) {
  const c = document.createElement("canvas"); c.width = s.w; c.height = s.h;
  const x = c.getContext("2d"), im = x.createImageData(s.w, s.h);
  for (let i = 0; i < s.w * s.h; i++) { im.data[i * 4] = s.px[i * 3]; im.data[i * 4 + 1] = s.px[i * 3 + 1]; im.data[i * 4 + 2] = s.px[i * 3 + 2]; im.data[i * 4 + 3] = 255; }
  x.putImageData(im, 0, 0);
  return c;
}
const grayFromCanvas = c => grayOf(c.getContext("2d").getImageData(0, 0, c.width, c.height));
function wireDrop(zone, onFile) {
  const z = $(zone);
  ["dragenter", "dragover"].forEach(t => document.addEventListener(t, e => { if (S.tab === (zone === "drop-stroke" ? "stroke" : "paper")) { e.preventDefault(); z.classList.add("over"); } }));
  ["dragleave", "drop"].forEach(t => document.addEventListener(t, () => z.classList.remove("over")));
  document.addEventListener("drop", e => {
    if (S.tab !== (zone === "drop-stroke" ? "stroke" : "paper")) return;
    e.preventDefault();
    const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
    if (f) onFile(f);
  });
}
document.addEventListener("paste", e => {
  const items = (e.clipboardData && e.clipboardData.items) || [];
  for (const it of items) if (it.type && it.type.startsWith("image/")) { const f = it.getAsFile(); if (f) { e.preventDefault(); (S.tab === "stroke" ? strokeFile : paperFile)(f); return; } }
});

// ---------- the fit's worker ----------
// The worker runs the core's text plus workerMain. If a worker can't be made (a strict page policy), the fit runs
// here in slices instead.
function workerMain() {
  self.onmessage = e => {
    const m = e.data;
    if (m.type === "textures") { for (const k in m.t) TEXTURES[k] = m.t[k]; return; }
    if (m.type !== "fit") return;
    try {
      const job = analyzeStroke(m.photo, m.crop, { flip: m.flip });
      const g = fitStroke(job, { budgetMs: m.budget, seed: m.seed, tip: m.tip, category: m.category });
      let r, last = 0;
      while (!(r = g.next()).done) { const t = Date.now(); if (t - last > 100) { last = t; self.postMessage({ type: "progress", phase: r.value.phase, evals: r.value.evals }); } }
      self.postMessage({ type: "done", res: packResult(r.value, job) });
    } catch (err) { self.postMessage({ type: "error", message: String(err && err.message || err) }); }
  };
}
// What the page needs from a finished fit (plain data: it crosses to the page).
function packResult(v, job) {
  const sc = v.score, F = job.F, G = sc.F;
  return { B: v.B, code: v.code, size: v.size, shade: v.shade, struct: v.struct, tried: v.tried, drawn: v.drawn, evals: v.evals, ms: v.ms, loss: v.loss,
    img: v.img, D: job.D, w: job.w, h: job.h, box: job.box,
    m: { iou: sc.iou, width: sc.width, tone: sc.tone, spec: sc.spec, roughP: F.rough, roughF: G.rough, dryP: F.dry, dryF: G.dry, haloP: F.halo, haloF: G.halo,
      runsP: F.runsMean, runsF: G.runsMean, rampP: F.ramp, rampF: G.ramp, lenIn: [F.l80In, G.l80In], lenOut: [F.l80Out, G.l80Out],
      endsP: job.Wb < 6 ? "fine" : job.ends.map(endWord).join(", "), endsF: job.Wb < 6 ? "fine" : (sc.ends || job.ends).map(endWord).join(", "), haloReal: job.paper.haloReal, paperTex: job.paper.texture, stretch: 1 / job.paper.b, inkLike: job.paper.inkLike } };
}
let WORKER = null;
function makeWorker() {
  try {
    const src = $("core").textContent + "\n;\n" + packResult.toString() + "\n;(" + workerMain.toString() + ")();";
    const w = new Worker(URL.createObjectURL(new Blob([src], { type: "text/javascript" })));
    w.postMessage({ type: "textures", t: TEXTURES });
    return w;
  } catch (e) { return null; }
}
// pick: the tip and category chosen ("" for Any).
function runFit(photo, crop, flip, pick, onProgress, onDone, onError) {
  const seed = 0x5eed;
  WORKER ??= makeWorker();
  if (WORKER) {
    WORKER.onmessage = e => { const m = e.data; if (m.type === "progress") onProgress(m); else if (m.type === "done") onDone(m.res); else if (m.type === "error") onError(m.message); };
    WORKER.onerror = e => { e.preventDefault(); WORKER = false; runFit(photo, crop, flip, pick, onProgress, onDone, onError); };
    WORKER.postMessage({ type: "fit", photo: { w: photo.w, h: photo.h, d: photo.d }, crop, flip, budget: BUDGET_MS, seed, tip: pick.tip, category: pick.category });
    return;
  }
  // Here, in slices of 40 ms.
  let job, g;
  try { job = analyzeStroke(photo, crop, { flip }); g = fitStroke(job, { budgetMs: BUDGET_MS, seed, tip: pick.tip, category: pick.category }); } catch (err) { onError(err.message); return; }
  const step = () => {
    const t0 = performance.now(); let r;
    try { while (!(r = g.next()).done && performance.now() - t0 < 40); } catch (err) { onError(err.message); return; }
    if (r.done) onDone(packResult(r.value, job)); else { onProgress(r.value); setTimeout(step, 0); }
  };
  setTimeout(step, 0);
}

// ---------- 1. the photo ----------
function strokeFile(f) { loadFile(f, c => c ? setStrokePhoto(c) : showErr("err-stroke", "That image couldn't be read.")); }
function setStrokePhoto(canvas) {
  showErr("err-stroke", "");
  S.rgb = canvas;
  try { S.photo = preparePhoto(grayFromCanvas(canvas)); }
  catch (e) { showErr("err-stroke", e.message); return; }
  S.crop = S.photo.crop; S.flip = false;
  $("photo-wrap").hidden = false; $("match-row").hidden = false;
  analyzeNow();
}
function analyzeNow() {
  showErr("err-stroke", "");
  try { S.job = analyzeStroke(S.photo, S.crop, { flip: S.flip }); }
  catch (e) { S.job = null; showErr("err-stroke", e.message); }
  $("match").disabled = !S.job || S.busy;
  drawPhoto();
}
// The photo, the box, the centre line and its start.
function drawPhoto(drag) {
  const c = $("photo"), P = S.photo; if (!P) return;
  c.width = P.w; c.height = P.h;
  const x = c.getContext("2d");
  x.drawImage(S.rgb, 0, 0, P.w, P.h);
  const lw = Math.max(2, Math.round(P.w / 400));
  x.lineWidth = lw;
  const b = drag || S.crop;
  if (b) { x.strokeStyle = "#000"; x.setLineDash([4 * lw, 3 * lw]); x.strokeRect(b.x + .5, b.y + .5, b.w, b.h); x.setLineDash([]); }
  const J = S.job;
  if (J && !drag) {
    const M = J.frame.M, s = J.scale, to = ([fx, fy]) => [S.crop.x + (fx - M) / s, S.crop.y + (fy - M) / s];
    x.beginPath(); J.path.forEach((p, i) => { const [px, py] = to(p); i ? x.lineTo(px, py) : x.moveTo(px, py); });
    x.strokeStyle = "#fff"; x.lineWidth = lw * 3; x.stroke(); x.strokeStyle = "#000"; x.lineWidth = lw; x.stroke();
    const [sx, sy] = to(J.path[0]);
    x.beginPath(); x.arc(sx, sy, lw * 4, 0, Math.PI * 2); x.fillStyle = "#000"; x.fill(); x.lineWidth = lw * 1.5; x.strokeStyle = "#fff"; x.stroke();
  }
}
function wirePhotoDrag() {
  const c = $("photo"); let a = null;
  const at = e => { const r = c.getBoundingClientRect(); return [clamp((e.clientX - r.left) / r.width) * c.width, clamp((e.clientY - r.top) / r.height) * c.height]; };
  c.addEventListener("pointerdown", e => { if (!S.photo || S.busy) return; c.setPointerCapture(e.pointerId); a = at(e); });
  c.addEventListener("pointermove", e => { if (!a) return; const b = at(e); drawPhoto(boxOf(a, b)); });
  const up = e => {
    if (!a) return; const b = at(e), r = boxOf(a, b); a = null;
    if (r.w >= 12 && r.h >= 12) { S.crop = r; S.flip = false; }
    analyzeNow();
  };
  c.addEventListener("pointerup", up); c.addEventListener("pointercancel", () => { a = null; drawPhoto(); });
}
const boxOf = (a, b) => { const x = Math.round(Math.min(a[0], b[0])), y = Math.round(Math.min(a[1], b[1])); return { x, y, w: Math.round(Math.abs(b[0] - a[0])), h: Math.round(Math.abs(b[1] - a[1])) }; };

// ---------- 2. the fit ----------
function startMatch() {
  if (!S.job || S.busy) return;
  S.busy = true; $("match").disabled = true; $("flip").disabled = true; $("rebox").disabled = true;
  $("prog-wrap").hidden = false; $("phase").textContent = "Starting"; $("prog").style.width = "0";
  const t0 = performance.now();
  const pick = { tip: S.tip, category: S.category }, span = BUDGET_MS * (pick.tip || pick.category ? CHOSEN_BUDGET : 1) * 1.15;
  const tick = setInterval(() => { $("prog").style.width = Math.min(97, (performance.now() - t0) / span * 100) + "%"; }, 100);
  const finish = () => { clearInterval(tick); S.busy = false; $("match").disabled = !S.job; $("flip").disabled = false; $("rebox").disabled = false; };
  runFit(S.photo, S.crop, S.flip, pick,
    m => { $("phase").textContent = m.phase || ""; },
    res => { finish(); $("prog").style.width = "100%"; $("phase").textContent = `Done: ${res.evals} brushes, ${(res.ms / 1000).toFixed(1)} s`; res.pick = pick; showResult(res); },
    msg => { finish(); $("prog-wrap").hidden = true; showErr("err-stroke", msg); });
}
// Darkness to grey: the photo as measured, the fit as the Manta shows it (16 greys).
function paintDark(c, D, w, h, box, quant) {
  const pad = 14, x0 = Math.max(0, box[0] - pad), y0 = Math.max(0, box[1] - pad), x1 = Math.min(w - 1, box[2] + pad), y1 = Math.min(h - 1, box[3] + pad);
  const cw = x1 - x0 + 1, ch = y1 - y0 + 1; c.width = cw; c.height = ch;
  const x = c.getContext("2d"), im = x.createImageData(cw, ch);
  for (let y = 0; y < ch; y++) for (let xx = 0; xx < cw; xx++) {
    let v = 255 * (1 - clamp(D[(y + y0) * w + xx + x0]));
    if (quant) v = Math.round(v / 17) * 17;
    const o = (y * cw + xx) * 4; im.data[o] = im.data[o + 1] = im.data[o + 2] = v; im.data[o + 3] = 255;
  }
  x.putImageData(im, 0, 0);
}
const fmt = (v, d = 2) => Number.isFinite(v) ? v.toFixed(d) : "–";
const bar = v => `<span class="bar"><i style="width:${Math.round(clamp(v) * 100)}%"></i></span>`;
function showResult(res) {
  S.res = res;
  $("result").hidden = false; $("try").hidden = false;
  paintDark($("cv-photo"), res.D, res.w, res.h, res.box, false);
  paintDark($("cv-fit"), res.img, res.w, res.h, res.box, true);
  const m = res.m, two = (label, a, b) => `<tr><td>${label}</td><td class="v">${a}</td><td class="v">${b}</td></tr>`, one = (label, v) => `<tr><td>${label}</td><td class="v" colspan="2">${v}</td></tr>`;
  $("nums").innerHTML = `<tr><th>Measure</th><th>Photo</th><th>Brush</th></tr>` +
    one("Overlap (IoU)", fmt(m.iou) + " " + bar(m.iou)) + one("Width error", fmt(m.width * 100, 1) + " %") +
    one("Tone difference", fmt(m.tone, 3)) + one("Grain difference", fmt(m.spec, 2)) +
    two("Ends", m.endsP, m.endsF) + two("Taper in, out (px)", Math.round(m.lenIn[0]) + ", " + Math.round(m.lenOut[0]), Math.round(m.lenIn[1]) + ", " + Math.round(m.lenOut[1])) +
    two("Edge roughness (px)", fmt(m.roughP), fmt(m.roughF)) + two("Edge softness", fmt(m.rampP), fmt(m.rampF)) +
    two("Dry-brush gaps", fmt(m.dryP), fmt(m.dryF)) + two("Halo", m.haloReal ? fmt(m.haloP) : "none", fmt(m.haloF)) +
    two("Parallel lines", fmt(m.runsP, 1), fmt(m.runsF, 1)) +
    one("Paper texture", fmt(m.paperTex * 100, 0) + " %, removed" + (m.inkLike ? "; read as crisp ink" : m.stretch > 1.05 ? "; ink darkened " + fmt(m.stretch, 1) + " times" : "")) +
    one("Score (0 is exact)", fmt(res.loss, 1));
  $("tried").innerHTML = res.tried.map(t => `<li>${t.words}: ${fmt(t.loss, 1)}</li>`).join("");
  const B = normBrush(res.B);
  $("bname").textContent = B.name + " · " + B.style;
  $("bparts").innerHTML = ["tip", "medium", "paper", "life", "hand", "carry"].map(p => `<dt>${PART_NAMES[p]}</dt><dd>${partText(B, p)}</dd>`).join("");
  $("bwords").textContent = brushWords(B);
  $("bdraw").innerHTML = `Draw at <b>size ${Math.round(res.size)}</b> and <b>${greyText(res.shade)}</b>. Sized for a stroke about 440 px long.`;
  const near = (res.drawn || []).slice(0, 3);
  $("bdrawn").textContent = near.length ? "Nearest drawn tools: " + near.map(d => d.name + " " + fmt(d.loss, 1)).join(", ") + " (this brush " + fmt(res.loss, 1) + ")" : "";
  $("tool-name").value = ""; nameCode(); $("copied").hidden = true;
  clearPad();
}
const greyText = shade => Math.round(shade * 100) + "% grey";
// The code under the name typed (a code carries its name as a slug: "Blackwing 602" reads back as "blackwing").
function codeNamed(B, name) { const C = cloneBrush(B); if (name.trim()) C.name = name.trim(); return encodeM(C); }
// Brush Studio opens the brush its link's # names (a share code: letters, digits, ".", "_" and "-" pass the host).
const STUDIO = "https://claude.ai/artifact/7UUw39J6FrpKLFqspxr7QF";
function nameCode() {
  if (!S.res) return;
  $("code").value = codeNamed(S.res.B, $("tool-name").value);
  $("studio").href = STUDIO + "#" + $("code").value; $("studio").hidden = false;
}

// ---------- 5. the pencil case: scans in a row, each named, out as one import file ----------
// Kept in this browser (a convenience: it can come back empty) as plain data: name, share code, size, grey, nearest
// drawn tool. A tool's brush is read from its code, so one stored by an older page that no longer reads is dropped.
const SET_KEY = "brush-scan-set";
const brushOfCode = code => { try { const r = readAnyCode(code); return r && r.b ? r.b : null; } catch (e) { return null; } };
function loadSet() {
  try {
    const v = JSON.parse(localStorage.getItem(SET_KEY) || "[]");
    return Array.isArray(v) ? v.filter(t => t && typeof t.name === "string" && typeof t.code === "string" && brushOfCode(t.code)) : [];
  } catch (e) { return []; }
}
function saveSet() { try { localStorage.setItem(SET_KEY, JSON.stringify(S.set)); } catch (e) { } }
function addTool() {
  if (!S.res) return;
  const r = S.res, name = $("tool-name").value.trim() || r.B.name;
  const pick = r.pick || {};
  S.set.push({ name, code: encodeM(r.B), size: Math.round(r.size), shade: r.shade, near: r.drawn && r.drawn.length ? r.drawn[0].name : "", tip: pick.tip || "", category: pick.category || "" });
  $("tool-name").value = ""; nameCode();
  saveSet(); showSet();
  $("set").scrollIntoView({ block: "nearest" });
}
// The import file BrushImport reads: one code a line, # lines skipped. The full name goes on the # line above its code.
function setText(set) {
  const out = ["# " + set.length + " brushes from Brush Scan, " + new Date().toISOString().slice(0, 10), "# Save as \"Scribulor import.txt\" beside the drawings: the app adds each brush once.", ""];
  for (const t of set) out.push("# " + t.name.replace(/\s+/g, " ") + ": size " + t.size + ", " + greyText(t.shade) + (t.near ? ", nearest drawn tool " + t.near : ""), codeNamed(brushOfCode(t.code), t.name), "");
  return out.join("\n");
}
function showSet() {
  $("set").hidden = !S.set.length;
  const host = $("setlist"); host.innerHTML = "";
  S.set.forEach((t, i) => {
    const li = document.createElement("li"), B = brushOfCode(t.code);
    li.innerHTML = `<span class="n">${i + 1}</span><input aria-label="Name of tool ${i + 1}"><button class="btn">Remove</button><span class="about"></span>`;
    const inp = li.querySelector("input"); inp.value = t.name;
    inp.addEventListener("input", () => { t.name = inp.value; saveSet(); $("set-text").value = setText(S.set); });
    li.querySelector("button").addEventListener("click", () => { S.set.splice(i, 1); saveSet(); showSet(); });
    li.querySelector(".about").textContent = [B.style, cap1(choiceWords(B, "tip")), cap1(choiceWords(B, "medium")), "size " + t.size, greyText(t.shade), t.near && "nearest " + t.near, pickWords(t)].filter(Boolean).join(" · ");
    host.appendChild(li);
  });
  $("set-text").value = setText(S.set); $("set-copied").hidden = true;
}
// The tip and category a tool was scanned with, as chosen ("" when both were Any).
const tipLabel = k => (CHOICES.tip.find(c => c.k === k) || { label: k }).label;
const pickWords = t => t.tip || t.category ? "scanned as " + [t.category, t.tip && tipLabel(t.tip)].filter(Boolean).join(", ") : "";
// The chip rows before the scan: Any, then each choice; a choice stays for the next photo.
function chipRow(host, key, items) {
  for (const [k, label] of [["", "Any"], ...items]) {
    const b = document.createElement("button");
    b.className = "chip"; b.textContent = label; b.setAttribute("aria-pressed", String(k === S[key]));
    b.addEventListener("click", () => { if (S.busy) return; S[key] = k; host.querySelectorAll(".chip").forEach(x => x.setAttribute("aria-pressed", String(x === b))); });
    host.appendChild(b);
  }
}
function copyText(text, input, done) {
  try { navigator.clipboard.writeText(text).then(done, () => { input.select(); document.execCommand("copy"); done(); }); }
  catch (e) { input.select(); try { document.execCommand("copy"); done(); } catch (e2) { } }
}
// A part in the app's words (the lab's choiceWords); the tip's and medium's dials that are off their defaults after it.
function partText(B, p) {
  const P = B[p], d = P.d, name = cap1(choiceWords(B, p));
  if (p === "paper") return name + (P.k !== "plain" ? ", tooth " + Math.round(d.tooth * 100) + " %" : "");
  if (p !== "tip" && p !== "medium") return name;
  const bits = [];
  for (const key of choiceOf(p, P.k).dials) {
    const s = DIALS[p][key]; if (s.choices || s.hide) continue;
    const v = d[key], def = dialDefault(p, P.k, key);
    if (Math.abs(v - def) < .03 && !(p === "tip" && (key === "aspect" || key === "angle")) && !(p === "medium" && (key === "grade" || key === "wet"))) continue;
    if (s.min === 0 && v < .02 && def < .02) continue;
    bits.push(dialLabel(p, P.k, key).toLowerCase() + " " + fmtDial(p, key, v));
  }
  return name + (bits.length ? ": " + bits.slice(0, 5).join(", ") : "");
}
function copyCode() { if ($("code").value) copyText($("code").value, $("code"), () => { $("copied").hidden = false; }); }

// ---------- 4. the test pad ----------
const PAD = { surf: null, ctx: null, water: null, live: null, ticking: false };
const PAD_HOST = { get water() { return PAD.water; }, getWater() { return PAD.water ??= new Water(PAD.surf, 2026); } };
function fitPad() {
  const c = $("pad"), w = Math.max(100, Math.round(c.clientWidth * DPR)), h = Math.max(100, Math.round(c.clientHeight * DPR));
  if (c.width !== w || c.height !== h || !PAD.surf) {
    // A new size keeps what's drawn (the overlap).
    const old = PAD.surf; c.width = w; c.height = h; PAD.surf = modSurface(w, h, 2026); PAD.water = null;
    if (old) { const cw = Math.min(w, old.w); for (let y = 0; y < Math.min(h, old.h); y++) PAD.surf.page.set(old.page.subarray(y * old.w, y * old.w + cw), y * w); }
  }
  PAD.ctx = c.getContext("2d");
  showPage(PAD.ctx, PAD.surf, [0, 0, w - 1, h - 1]);
}
function clearPad() { if ($("try").hidden) return; PAD.surf = null; fitPad(); }
// The imported paper stands in for cold press while the page draws with it (the share code keeps the brush's paper).
function withImported(surf, fn) {
  if (!S.imported) return fn();
  const keep = TEXTURES["cold press"];
  TEXTURES["cold press"] = S.imported; if (surf.tex) delete surf.tex["cold press"];
  try { return fn(); } finally { TEXTURES["cold press"] = keep; if (surf.tex) delete surf.tex["cold press"]; }
}
function padBrush() {
  const B = cloneBrush(S.res.B);
  if (S.padPaper === "imported" && S.imported) B.paper = { k: "cold press", d: { tooth: Math.max(.55, B.paper.d.tooth || 0) } };
  return normBrush(B);
}
function penSample(e, L) {
  const c = $("pad"), r = c.getBoundingClientRect(), x = (e.clientX - r.left) * c.width / r.width, y = (e.clientY - r.top) * c.height / r.height, isPen = e.pointerType === "pen";
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
function wirePad() {
  const c = $("pad");
  c.addEventListener("pointerdown", e => {
    if (!S.res || !PAD.surf) return;
    c.setPointerCapture(e.pointerId);
    const B = padBrush(), make = () => new Stroke(PAD.surf, B, S.res.size * DPR, S.res.shade, PAD_HOST);
    const st = S.padPaper === "imported" ? withImported(PAD.surf, make) : make();
    PAD.live = { st, sp: 0, d: 0 };
    const r = st.add([penSample(e, PAD.live)]); if (r) showPage(PAD.ctx, PAD.surf, r);
  });
  c.addEventListener("pointermove", e => {
    const L = PAD.live; if (!L) return;
    const evs = e.getCoalescedEvents ? e.getCoalescedEvents() : [e];
    const r = L.st.add((evs.length ? evs : [e]).map(ev => penSample(ev, L))); if (r) showPage(PAD.ctx, PAD.surf, r);
  });
  const end = () => {
    const L = PAD.live; if (!L) return; PAD.live = null;
    const r = L.st.end(); if (r) showPage(PAD.ctx, PAD.surf, r);
    if (PAD.water && PAD.water.wet && !PAD.ticking) { PAD.ticking = true; setTimeout(tickWater, 1000 / TPS); }
  };
  c.addEventListener("pointerup", end); c.addEventListener("pointercancel", end);
}
function tickWater() {
  const W = PAD.water;
  if (!W || !W.wet || W.s !== PAD.surf) { PAD.ticking = false; return; }
  W.step(); const d = W.takeDirty(); if (d && !PAD.live) showPage(PAD.ctx, PAD.surf, d);
  setTimeout(tickWater, 1000 / TPS);
}
function setPadPaper(k) {
  S.padPaper = k;
  document.querySelectorAll("#trypaper .chip").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.paper === k)));
}

// ---------- Paper ----------
function paperFile(f) { loadFile(f, c => c ? setSheet(c) : showErr("err-paper", "That image couldn't be read.")); }
function setSheet(canvas) {
  showErr("err-paper", "");
  S.sheetRGB = canvas; S.sheet = grayFromCanvas(canvas); S.paperBox = null;
  $("sheet-wrap").hidden = false;
  buildPaper();
}
function buildPaper() {
  if (!S.sheet) return;
  try { S.paper = makePaper(S.sheet, { box: S.paperBox, grain: Math.pow(2, S.grain) }); }
  catch (e) { showErr("err-paper", e.message); return; }
  S.paperBox = S.paper.box;
  drawSheet();
  $("paper-out").hidden = false;
  const N = PAPER_SIDE, t = $("tile").getContext("2d"), im = t.createImageData(N, N);
  for (let i = 0; i < N * N; i++) { const v = S.paper.bytes[i]; im.data[i * 4] = im.data[i * 4 + 1] = im.data[i * 4 + 2] = v; im.data[i * 4 + 3] = 255; }
  t.putImageData(im, 0, 0);
  const t4 = $("tile4").getContext("2d"); for (let y = 0; y < 2; y++) for (let x = 0; x < 2; x++) t4.putImageData(im, x * N, y * N);
  const st = S.paper.stats, app = appGrain(), vals = Object.values(app);
  const nearest = Object.entries(app).sort((a, b) => Math.abs(a[1] - st.grain) - Math.abs(b[1] - st.grain))[0];
  $("paper-nums").innerHTML = `<tr><th>Measure</th><th>This paper</th><th>App papers</th></tr>
    <tr><td>Mean / spread</td><td class="v">${fmt(st.mean, 1)} / ${fmt(st.spread, 1)}</td><td class="v">128 / 52</td></tr>
    <tr><td>Grain size (px)</td><td class="v">${fmt(st.grain)}</td><td class="v">${vals.length ? fmt(Math.min(...vals)) + "–" + fmt(Math.max(...vals)) : "–"}</td></tr>
    <tr><td>Closest app paper</td><td class="v" colspan="2">${nearest ? nearest[0] : "–"}</td></tr>
    <tr><td>Seam (1 is seamless)</td><td class="v" colspan="2">${fmt(st.seam)}</td></tr>
    <tr><td>Photo px per tile px</td><td class="v" colspan="2">${fmt(1 / S.paper.k)}</td></tr>`;
  renderPreviews();
}
function drawSheet() {
  const c = $("sheet"), g = S.sheet; c.width = g.w; c.height = g.h;
  const x = c.getContext("2d"); x.drawImage(S.sheetRGB, 0, 0, g.w, g.h);
  const lw = Math.max(2, Math.round(g.w / 400)); x.lineWidth = lw; x.strokeStyle = "#000";
  if (S.paperBox) { const b = S.paperBox; x.setLineDash([4 * lw, 3 * lw]); x.strokeRect(b.x, b.y, b.s, b.s); x.setLineDash([]); }
  if (S.paper) { const r = S.paper.region; x.strokeStyle = "#fff"; x.lineWidth = lw * 3; x.strokeRect(r.x, r.y, r.s, r.s); x.strokeStyle = "#000"; x.lineWidth = lw; x.strokeRect(r.x, r.y, r.s, r.s); }
}
function wireSheetDrag() {
  const c = $("sheet"); let a = null;
  const at = e => { const r = c.getBoundingClientRect(); return [clamp((e.clientX - r.left) / r.width) * c.width, clamp((e.clientY - r.top) / r.height) * c.height]; };
  c.addEventListener("pointerdown", e => { if (!S.sheet) return; c.setPointerCapture(e.pointerId); a = at(e); });
  const sq = (a, b) => { const s = Math.max(16, Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]))); return { x: b[0] < a[0] ? a[0] - s : a[0], y: b[1] < a[1] ? a[1] - s : a[1], s: Math.min(s, S.sheet.w, S.sheet.h) }; };
  c.addEventListener("pointermove", e => { if (!a) return; S.paperBox = sq(a, at(e)); const keep = S.paper; S.paper = null; drawSheet(); S.paper = keep; });
  const up = e => { if (!a) return; const b = at(e); if (Math.hypot(b[0] - a[0], b[1] - a[1]) > 16) S.paperBox = sq(a, b); a = null; buildPaper(); };
  c.addEventListener("pointerup", up); c.addEventListener("pointercancel", () => { a = null; drawSheet(); });
}
// Four brushes on the new paper.
const PREVIEWS = [
  ["Pencil", { tip: { k: "round" }, medium: { k: "graphite", d: { grade: .55 } } }, 6, 1, .85],
  ["Charcoal", { tip: { k: "round", d: { taperIn: .1, taperOut: .2 } }, medium: { k: "charcoal", d: { spread: .3, hold: .5 } } }, 26, 1, .8],
  ["Dry brush", { tip: { k: "round", d: { dry: .6, soft: .2 } }, medium: { k: "wash", d: { wet: .3 } } }, 36, 1, .6],
  ["Watercolour", { tip: { k: "round", d: { soft: .4 } }, medium: { k: "wash", d: { wet: .9, pool: .3 } } }, 54, 1, .9]
];
function renderPreviews() {
  const host = $("previews"); host.innerHTML = "";
  for (const [label, parts, size, shade, tooth] of PREVIEWS) {
    const fig = document.createElement("figure"), c = document.createElement("canvas"), w = 300, h = 120;
    c.width = w; c.height = h; fig.appendChild(c);
    const cap = document.createElement("figcaption"); cap.textContent = label; fig.appendChild(cap); host.appendChild(fig);
    const B = sampleBrush(Object.assign({ paper: { k: "cold press", d: { tooth } } }, parts), .5), s = modSurface(w, h, 9);
    const pts = []; for (let i = 0; i <= 60; i++) { const u = i / 60; pts.push({ x: 20 + (w - 40) * u, y: h / 2 + h * .18 * Math.sin(u * Math.PI * 1.6), p: .4 + .4 * Math.sin(Math.PI * u), t: i * 6, tx: NaN, ty: NaN }); }
    const keep = S.imported; S.imported = S.paper.floats;
    try { withImported(s, () => layStroke(s, B, pts, size, shade, null)); } finally { S.imported = keep; }
    showPage(c.getContext("2d"), s, [0, 0, w - 1, h - 1]);
  }
}
// A grey PNG (8-bit, one channel) of the tile: zlib through the browser's CompressionStream, else the canvas's RGBA PNG.
async function greyPNG(bytes, N) {
  if (typeof CompressionStream === "undefined") return new Promise(r => $("tile").toBlob(r, "image/png"));
  const raw = new Uint8Array((N + 1) * N); for (let y = 0; y < N; y++) raw.set(bytes.subarray(y * N, y * N + N), y * (N + 1) + 1);
  const z = new Uint8Array(await new Response(new Blob([raw]).stream().pipeThrough(new CompressionStream("deflate"))).arrayBuffer());
  const crcT = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
  const crc = b => { let c = -1; for (const v of b) c = crcT[(c ^ v) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; };
  const chunk = (type, data) => { const o = new Uint8Array(12 + data.length), dv = new DataView(o.buffer); dv.setUint32(0, data.length); for (let i = 0; i < 4; i++) o[4 + i] = type.charCodeAt(i); o.set(data, 8); dv.setUint32(8 + data.length, crc(o.subarray(4, 8 + data.length))); return o; };
  const ihdr = new Uint8Array(13), dv = new DataView(ihdr.buffer); dv.setUint32(0, N); dv.setUint32(4, N); ihdr[8] = 8; ihdr[9] = 0;
  return new Blob([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", z), chunk("IEND", new Uint8Array(0))], { type: "image/png" });
}
// Inside claude.ai a page can't start a download itself: the viewer's "downloads" capability offers the file (PNG only;
// a .bin isn't on its list, so that button hides there). Opened as a plain file, a link saves it as before.
let DOWNLOADS = null;
if (window.claude && typeof window.claude.use === "function") window.claude.use("downloads").then(d => { DOWNLOADS = d; const b = document.getElementById("dl-bin"); if (d && b) b.hidden = true; }).catch(() => {});
async function download(blob, name) {
  if (DOWNLOADS) { try { await DOWNLOADS.save({ filename: name, data: blob }); } catch (e) { /* declined or unavailable: nothing to do */ } return; }
  const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = name; document.body.appendChild(a); a.click(); setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 1000);
}
// A sample sheet: fibres and lumps under a lamp's falloff (made here, so the page needs no image files).
function sampleSheet() {
  const w = 900, h = 700, r = rng(41), px = new Uint8Array(w * h * 3), fine = valueNoise(w, h, 1.6, r), lump = fbm(w, h, 26, r), fib = new Float32Array(w * h);
  for (let k = 0; k < 2600; k++) {
    let x = r() * w, y = r() * h, a = r() * Math.PI, L = 6 + r() * 30, amt = (r() - .4) * .5;
    for (let t = 0; t < L; t++) { const i = (y | 0) * w + (x | 0); if (i >= 0 && i < fib.length) fib[i] += amt; x += Math.cos(a); y += Math.sin(a); a += (r() - .5) * .2; }
  }
  const fb = gauss(fib, w, h, .8);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = y * w + x, u = x / w - .25, v = y / h - .2, light = .97 - .42 * Math.sqrt(u * u + v * v), hgt = .3 * fine[i] + .5 * lump[i] + .9 * fb[i];
    const t = clamp(light * (.8 + .16 * hgt) + (r() - .5) * .006);
    px[i * 3] = t * 255; px[i * 3 + 1] = t * 248; px[i * 3 + 2] = t * 234;
  }
  return { w, h, px };
}

// ---------- wiring ----------
function setTab(t) {
  S.tab = t;
  document.querySelectorAll(".tabs button").forEach(b => b.setAttribute("aria-pressed", String(b.dataset.tab === t)));
  $("tab-stroke").hidden = t !== "stroke"; $("tab-paper").hidden = t !== "paper";
  if (t === "stroke" && !$("try").hidden) fitPad();
}
function init() {
  document.querySelectorAll(".tabs button").forEach(b => b.addEventListener("click", () => setTab(b.dataset.tab)));
  $("file-stroke").addEventListener("change", e => { strokeFile(e.target.files[0]); e.target.value = ""; });
  $("file-paper").addEventListener("change", e => { paperFile(e.target.files[0]); e.target.value = ""; });
  wireDrop("drop-stroke", strokeFile); wireDrop("drop-paper", paperFile);
  chipRow($("pick-tip"), "tip", FIT_TIPS.map(k => [k, tipLabel(k)]));
  chipRow($("pick-category"), "category", CATEGORIES.map(c => [c.label, c.label]));
  const host = $("samples");
  for (const name of ["e2-brush-pen", "e4-split-nib", "e6-watercolour", "e7-charcoal", "d1-sumi-stroke", "d2-feathered-pen", "d3-pencil", "d5-two-lines", "d7-low-contrast", "d8-flat-brush", "d9-slant-cut", "d10-thin-top"]) {
    const spec = SAMPLE_ENGINE[name] || SAMPLE_DRAWN[name], b = document.createElement("button");
    b.className = "chip"; b.textContent = spec.label; b.dataset.sample = name;
    b.addEventListener("click", () => { if (S.busy) return; document.querySelectorAll("#samples .chip").forEach(x => x.setAttribute("aria-pressed", String(x === b))); setStrokePhoto(canvasFromRGB(makeSample(name))); });
    host.appendChild(b);
  }
  $("flip").addEventListener("click", () => { S.flip = !S.flip; analyzeNow(); });
  $("rebox").addEventListener("click", () => { if (!S.photo) return; S.crop = { x: 0, y: 0, w: S.photo.w, h: S.photo.h }; S.flip = false; analyzeNow(); });
  $("match").addEventListener("click", startMatch);
  $("copy").addEventListener("click", copyCode);
  $("tool-name").addEventListener("input", nameCode);
  $("tool-name").addEventListener("keydown", e => { if (e.key === "Enter") addTool(); });
  $("add").addEventListener("click", addTool);
  $("set-copy").addEventListener("click", () => copyText($("set-text").value, $("set-text"), () => { $("set-copied").hidden = false; }));
  $("set-dl").addEventListener("click", () => download(new Blob([$("set-text").value], { type: "text/plain" }), "brush-scan.scribulor.txt"));
  // A second tap within 3 s clears (an artifact's frame refuses confirm()).
  let clearArmed = 0;
  $("set-clear").addEventListener("click", () => {
    const b = $("set-clear");
    if (Date.now() - clearArmed < 3000) { clearArmed = 0; b.textContent = "Clear"; S.set = []; saveSet(); showSet(); return; }
    clearArmed = Date.now(); b.textContent = "Clear all " + S.set.length + "?";
    setTimeout(() => { if (Date.now() - clearArmed >= 3000) b.textContent = "Clear"; }, 3100);
  });
  S.set = loadSet(); showSet();
  $("clear").addEventListener("click", clearPad);
  document.querySelectorAll("#trypaper .chip").forEach(b => b.addEventListener("click", () => { if (!b.disabled) setPadPaper(b.dataset.paper); }));
  wirePhotoDrag(); wireSheetDrag(); wirePad();
  $("paper-sample").addEventListener("click", () => setSheet(canvasFromRGB(sampleSheet())));
  $("grain").addEventListener("input", e => { S.grain = +e.target.value; $("grain-v").textContent = "×" + Math.pow(2, S.grain).toFixed(2); });
  $("grain").addEventListener("change", () => buildPaper());
  $("dl-png").addEventListener("click", async () => { if (S.paper) download(await greyPNG(S.paper.bytes, PAPER_SIDE), "paper-256.png"); });
  $("dl-bin").addEventListener("click", () => { if (S.paper) download(new Blob([S.paper.bytes], { type: "application/octet-stream" }), "paper.bin"); });
  $("use-paper").addEventListener("click", () => {
    if (!S.paper) return;
    S.imported = S.paper.floats;
    const chip = document.querySelector('#trypaper [data-paper="imported"]'); chip.disabled = false;
    setPadPaper("imported");
    if (S.res) setTab("stroke");
    $("use-paper").textContent = "In Try it";
  });
  window.addEventListener("resize", () => { if (!$("try").hidden && S.tab === "stroke") { const c = $("pad"); if (Math.round(c.clientWidth * DPR) !== c.width) fitPad(); } });
}
init();
loadTextures().then(() => { TEX_OK.done = true; WORKER = makeWorker(); if (location.hash === "#selftest") pageSelfTest(); });
// ---------- a quick check from the page: #selftest fits two samples, adds them to an empty pencil case under names, and
// checks every code in its import file reads back to itself; the numbers go into the page (the set is put back after) ----------
async function pageSelfTest() {
  const out = document.createElement("pre"); out.id = "selftest"; out.className = "note"; document.querySelector(".wrap").appendChild(out);
  const kept = S.set; S.set = [];
  // The pencil with Dry Media chosen: its medium one of the category's, and the pencil case keeps the choice.
  for (const [name, tool, pick] of [["e2-brush-pen", "Pentel Brush Pen", {}], ["d3-pencil", "Blackwing 602", { category: "Dry Media" }]]) {
    setStrokePhoto(canvasFromRGB(makeSample(name)));
    const res = await new Promise((ok, bad) => runFit(S.photo, S.crop, false, pick, () => { }, ok, bad));
    res.pick = pick; showResult(res); $("tool-name").value = tool; nameCode(); addTool();
    const cat = categoryOf(pick.category), kept = S.set[S.set.length - 1].category === (pick.category || "");
    const ok = kept && (!cat || (cat.media.includes(res.struct.medium) && res.drawn.every(d => cat.tools.includes(d.name))));
    out.textContent += `${name}${pick.category ? " (" + pick.category + ")" : ""}: ${res.struct.tip}/${res.struct.medium} IoU ${fmt(res.m.iou)} width ${fmt(res.m.width * 100, 1)}% in ${(res.ms / 1000).toFixed(1)} s; nearest drawn ${res.drawn.slice(0, 2).map(d => d.name).join(", ")}; choice ${ok ? "kept" : "FAILED"}\n`;
  }
  const codes = $("set-text").value.split("\n").filter(l => l.trim() && !l.startsWith("#"));
  const back = codes.map(c => { const r = readAnyCode(c); return r && r.b ? encodeM(r.b) : "unread"; });
  out.textContent += `import file: ${codes.length} codes, ${back.filter((b, i) => b === codes[i]).length} read back the same\n` + codes.join("\n") + "\n";
  S.set = kept; saveSet(); showSet();
  out.dataset.done = "1";
}
