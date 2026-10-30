// Screenshots of every tab at a few widths: node shots.cjs <out dir> [widths…]. Needs puppeteer-core and Chrome.
"use strict";
const path = require("path"), fs = require("fs");
const PUP = [process.env.PUPPETEER_CORE, path.join(require("os").homedir(), "node_modules", "puppeteer-core"), "puppeteer-core"].filter(Boolean);
let puppeteer = null; for (const p of PUP) { try { puppeteer = require(p); break; } catch (e) { /* next */ } }
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const sleep = ms => new Promise(r => setTimeout(r, ms));
const out = process.argv[2] || path.join(__dirname, "out", "shots");
// Sizes as widths (a height to suit) or WxH.
const sizes = process.argv.slice(3).map(a => { const [w, h] = a.split("x").map(Number); return w ? [w, h || (w < 600 ? 844 : w < 1000 ? 1180 : w <= 1280 ? 800 : 900)] : null; }).filter(Boolean);
(async () => {
  fs.mkdirSync(out, { recursive: true });
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu"] });
  const p = await b.newPage(), errs = [];
  p.on("console", m => { if (m.type() === "error" || m.type() === "warning") errs.push(m.type() + ": " + m.text()); });
  p.on("pageerror", e => errs.push("pageerror: " + e.message));
  for (const [w, h] of sizes.length ? sizes : [[1440, 900], [1280, 800], [1560, 970], [1920, 1080], [2240, 1060], [900, 1180], [390, 844]]) {
    await p.setViewport({ width: w, height: h });
    await p.goto("file://" + path.join(__dirname, "..", "studio.html"), { waitUntil: "load" });
    await p.evaluate(() => { try { localStorage.clear(); } catch (e) { } });
    await p.reload({ waitUntil: "load" });
    await p.waitForFunction(() => document.documentElement.dataset.ready === "1", { timeout: 20000 });
    const idle = () => p.waitForFunction(() => STUDIO.POOL.q.length === 0 && STUDIO.POOL.flight.size === 0, { timeout: 30000 }).then(() => sleep(250));
    // A stroke on the test page first, so Close-up has one to take.
    const r = await p.evaluate(() => { const b = document.getElementById("test").getBoundingClientRect(); return [b.x, b.y, b.width, b.height]; });
    if (r[2] > 0) {
      await p.mouse.move(r[0] + r[2] * .35, r[1] + r[3] * .6); await p.mouse.down();
      for (let i = 1; i <= 30; i++) { const u = i / 30; await p.mouse.move(r[0] + r[2] * (.35 + .55 * u), r[1] + r[3] * (.6 - .3 * Math.sin(u * 5))); await sleep(6); }
      await p.mouse.up();
    }
    for (const tab of ["community", "roll", "lab", "tune", "mix"]) {
      await p.evaluate(t => STUDIO.setTab(t), tab); await idle();
      // Laptops: the window as it is (each tab should fit it); narrower: the whole page.
      await p.screenshot({ path: path.join(out, `${tab}-${w}x${h}.png`), fullPage: w < 1200 });
      const [over, down, inner] = await p.evaluate(() => [document.documentElement.scrollWidth - window.innerWidth, document.documentElement.scrollHeight - window.innerHeight,
        [...document.querySelectorAll(".screen:not([hidden]) .partscol, .screen:not([hidden]) .tunemain, .screen:not([hidden]) .side, .screen:not([hidden]) .hist")].map(e => e.scrollHeight - e.clientHeight).filter(d => d > 1)]);
      if (over > 0) errs.push(`${tab} at ${w}: ${over} px wider than the window`);
      if (w >= 1200) console.log(`${tab} at ${w} × ${h}: ${down > 0 ? down + " px to scroll" : "fits"}${inner.length ? " (a column scrolls " + inner.join(", ") + " px)" : ""}`);
    }
  }
  // The Share panel open over the Lab, once at the first size.
  await p.evaluate(() => STUDIO.setTab("lab")); await sleep(300);
  await p.evaluate(() => document.querySelector('#brushbar [data-act="share"]').click()); await sleep(300);
  await p.screenshot({ path: path.join(out, "share-panel.png") });
  await b.close();
  console.log(errs.length ? errs.join("\n") : "no console errors");
})().catch(e => { console.error(e); process.exit(1); });
