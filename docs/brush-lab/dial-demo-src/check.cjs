// Checks Brush Lab Dials: node docs/brush-lab/dial-demo-src/build.cjs && node docs/brush-lab/dial-demo-src/check.cjs
//   1. In Node (out/node-engine.cjs): every choice and every shown dial of the lab's lists has a tile, and every
//      tile's every job draws without throwing. Tiles whose ends look the same are listed (not a failure).
//   2. With puppeteer-core and Chrome, when there (as studio-src/check.cjs): dial-demo.html?all draws every tile with
//      no console errors and no network fetch, and fits a phone's width.
// One line a check; exit 1 on any failure.
"use strict";
const path = require("path"), E = require("./out/node-engine.cjs");
let n = 0, fail = 0;
const ok = (cond, what) => { n++; if (!cond) fail++; console.log((cond ? "ok   " : "FAIL ") + what); };
const tiles = E.demoTiles();

// ---------- 1. every choice and dial a tile ----------
const missing = [], hidden = [];
for (const p of E.PARTS) for (const c of E.CHOICES[p]) {
  if (!tiles.some(t => t.choice && t.part === p && t.k === c.k)) missing.push(p + ":" + c.k);
  for (const key of c.dials) {
    if (E.DIALS[p][key].hide) { hidden.push(p + "." + key); continue; }
    if (!tiles.some(t => !t.choice && t.part === p && t.key === key && t.title === E.dialLabel(p, c.k, key))) missing.push(p + "." + key + " on " + c.k);
  }
}
ok(!missing.length, "every choice and shown dial has a tile (" + tiles.length + " tiles)" + (missing.length ? ": missing " + missing.join(", ") : ""));
console.log("     hidden in the lab, so no tile: " + [...new Set(hidden)].join(", "));
const unshown = tiles.filter(t => !t.choice && !E.dialShown(t.B0, t.part, t.key));
ok(!unshown.length, "every dial's tile is drawn on a brush that shows it" + (unshown.length ? ": " + unshown.map(t => t.title).join(", ") : ""));
ok(tiles.every(t => t.say && !/\byou(r)?\b/i.test(t.say)), "every tile says what it does, with no \"you\"" + tiles.filter(t => !t.say).map(t => ": " + t.title).join(""));

// ---------- 2. every tile draws ----------
const threw = [], same = [];
const t0 = Date.now();
for (const t of tiles) {
  const n = t.sweep === "time" ? 1 : t.values.length, outs = [];
  for (let i = 0; i < n; i++) {
    try { outs.push(E.demoJob(E.tileJob(t, i))); } catch (e) { threw.push(t.part + " " + t.title + " [" + i + "]: " + String(e && e.message || e)); }
  }
  if (outs.length < n || (t.sweep !== "range" && t.sweep !== "options")) continue;
  const a = t.sweep === "time" ? outs[0].frames[0] : outs[0].frames[outs[0].frames.length - 1];
  const b = outs[outs.length - 1].frames[outs[outs.length - 1].frames.length - 1];
  let d = 0; for (let k = 0; k < a.length; k++) if (a[k] !== b[k]) d++;
  if (d < 40) same.push(t.part + " " + t.title);
}
ok(!threw.length, "every tile's frames draw without throwing (" + ((Date.now() - t0) / 1000).toFixed(1) + " s)" + (threw.length ? "\n     " + threw.join("\n     ") : ""));
console.log("     sliders whose ends look alike: " + (same.join(", ") || "none"));

// ---------- 3. the page in Chrome ----------
const PUP = [process.env.PUPPETEER_CORE, path.join(require("os").homedir(), "node_modules", "puppeteer-core"), "puppeteer-core"].filter(Boolean);
let puppeteer = null; for (const p of PUP) { try { puppeteer = require(p); break; } catch (e) { /* next */ } }
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
(async () => {
  if (!puppeteer || !require("fs").existsSync(CHROME)) { console.log("     no puppeteer-core or Chrome: the page itself not checked"); return; }
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu"] });
  try {
    const p = await b.newPage(), errs = [], net = [];
    p.on("console", m => { if (m.type() === "error" || m.type() === "warning") errs.push(m.type() + ": " + m.text()); });
    p.on("pageerror", e => errs.push("pageerror: " + e.message));
    p.on("request", r => { if (!/^(file|data|blob):/.test(r.url())) net.push(r.url()); });
    const page = "file://" + path.join(__dirname, "..", "dial-demo.html");
    await p.setViewport({ width: 1280, height: 900 });
    await p.goto(page + "?all", { waitUntil: "load" });
    await p.waitForFunction(() => document.documentElement.dataset.ready === "1", { timeout: 20000 });
    ok(await p.evaluate(() => DEMO.R.workers.length > 0 && !DEMO.R.inPage), "workers draw the tiles");
    await p.waitForFunction(() => DEMO.TILES.every(t => t.complete), { timeout: 240000, polling: 500 });
    ok(await p.evaluate(() => DEMO.TILES.every(t => !t.failed)), "every tile drawn in the page (" + await p.evaluate(() => DEMO.TILES.length) + ")");
    ok(await p.evaluate(() => document.querySelectorAll("#main .tile").length) === tiles.length, "a card for every tile");
    await p.evaluate(() => DEMO.TILES.find(t => t.sweep === "after").el.scrollIntoView()); await new Promise(r => setTimeout(r, 1500));
    const clock = await p.evaluate(() => { const t = DEMO.TILES.find(t => t.visible && t.sweep === "after"); return t ? t.title + ": " + t.clockText.textContent : ""; });
    ok(/: .+/.test(clock), "an after-lift tile on screen runs its clock (" + clock + ")");
    await p.setViewport({ width: 390, height: 844 }); await new Promise(r => setTimeout(r, 400));
    ok(await p.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), "fits a phone's width, no sideways scroll");
    ok(!errs.length, "no console errors" + (errs.length ? ": " + errs.slice(0, 5).join(" | ") : ""));
    ok(!net.length, "no network fetches" + (net.length ? ": " + net.slice(0, 5).join(", ") : ""));
  } finally { await b.close(); }
})().catch(e => { console.error(e); fail++; }).finally(() => { console.log(n - fail + " of " + n + " checks passed"); process.exit(fail ? 1 : 0); });
