// node shots.cjs <out dir> [stroke photo.png] [paper photo.png]
// Drives ../match-lab.html in headless Chrome (puppeteer-core in ~/node_modules): a sample match, an uploaded photo,
// a few strokes on the test pad, the paper import; desktop and phone widths. Screenshots and errors into <out dir>.
const path = require("path"), fs = require("fs");
const puppeteer = require(require("os").homedir() + "/node_modules/puppeteer-core");
const sleep = ms => new Promise(r => setTimeout(r, ms));
const out = path.resolve(process.argv[2] || "."), strokePhoto = process.argv[3], paperPhoto = process.argv[4];
fs.mkdirSync(out, { recursive: true });
(async () => {
  const b = await puppeteer.launch({ executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", headless: "new", args: ["--disable-gpu", "--allow-file-access-from-files"] });
  const errs = [], log = [];
  async function open(vp) {
    const p = await b.newPage();
    p.on("pageerror", e => errs.push(e.message)); p.on("console", m => { if (m.type() === "error" || m.type() === "warning") errs.push(m.text()); });
    await p.setViewport(vp);
    await p.goto("file://" + path.join(__dirname, "..", "match-lab.html"), { waitUntil: "networkidle0" });
    await sleep(500);
    return p;
  }
  const waitDone = async p => { const t0 = Date.now(); await p.waitForFunction(() => /^Done/.test(document.getElementById("phase").textContent) || !document.getElementById("err-stroke").hidden, { timeout: 60000 }); return Date.now() - t0; };
  async function draw(p, pts) {
    const r = await p.evaluate(() => { const c = document.getElementById("pad"); c.scrollIntoView(); const b = c.getBoundingClientRect(); return [b.x, b.y, b.width, b.height]; });
    await p.mouse.move(r[0] + r[2] * pts[0][0], r[1] + r[3] * pts[0][1]); await p.mouse.down();
    for (let i = 1; i < pts.length; i++) { await p.mouse.move(r[0] + r[2] * pts[i][0], r[1] + r[3] * pts[i][1]); await sleep(8); }
    await p.mouse.up(); await sleep(300);
  }
  const wave = (x0, x1, y, a, n = 40) => Array.from({ length: n + 1 }, (_, i) => [x0 + (x1 - x0) * i / n, y + a * Math.sin(i / n * Math.PI * 2)]);
  // Desktop: a sample.
  let p = await open({ width: 1280, height: 900 });
  await p.click('[data-sample="e2-brush-pen"]'); await sleep(400);
  await p.screenshot({ path: path.join(out, "01-desktop-photo.png"), fullPage: true });
  await p.click("#match");
  log.push("brush pen sample, match ms: " + await waitDone(p));
  await draw(p, wave(.08, .9, .45, .25)); await draw(p, wave(.1, .85, .75, .1));
  await p.screenshot({ path: path.join(out, "02-desktop-result.png"), fullPage: true });
  log.push("code: " + await p.$eval("#code", e => e.value));
  // An uploaded photo.
  if (strokePhoto) {
    const inp = await p.$("#file-stroke"); await inp.uploadFile(path.resolve(strokePhoto)); await sleep(800);
    await p.click("#match"); log.push(path.basename(strokePhoto) + " match ms: " + await waitDone(p));
    await draw(p, wave(.08, .9, .5, .25));
    await p.screenshot({ path: path.join(out, "03-desktop-upload.png"), fullPage: true });
    log.push("code: " + await p.$eval("#code", e => e.value));
  }
  // Paper.
  await p.click('[data-tab="paper"]'); await sleep(200);
  if (paperPhoto) { const inp = await p.$("#file-paper"); await inp.uploadFile(path.resolve(paperPhoto)); }
  else await p.click("#paper-sample");
  await sleep(1500);
  await p.screenshot({ path: path.join(out, "04-desktop-paper.png"), fullPage: true });
  log.push("paper: " + await p.$eval("#paper-nums", e => e.innerText.replace(/\s+/g, " ")));
  await p.click("#use-paper"); await sleep(300);
  await p.click('[data-paper="imported"]').catch(() => { });
  await draw(p, wave(.1, .9, .3, .12));
  await p.screenshot({ path: path.join(out, "05-desktop-try-imported.png"), fullPage: true });
  // Overflow: nothing wider than the page.
  const wide = async q => q.evaluate(() => [...document.querySelectorAll("body *")].filter(e => e.getBoundingClientRect().right > document.documentElement.clientWidth + 1 && e.offsetParent !== null).map(e => e.tagName + "#" + e.id + "." + e.className).slice(0, 8));
  log.push("desktop overflow: " + JSON.stringify(await wide(p)));
  await p.close();
  // Phone.
  p = await open({ width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  await p.screenshot({ path: path.join(out, "06-phone-start.png"), fullPage: true });
  await p.click('[data-sample="d1-sumi-stroke"]'); await sleep(400);
  // Brushes chosen first (the category chips; the fourth button: Any, Pens, Markers, Brushes).
  await p.click("#pick-category button:nth-of-type(4)");
  await p.click("#match"); log.push("phone sumi, " + await p.$eval('#pick-category [aria-pressed="true"]', e => e.textContent) + " chosen, match ms: " + await waitDone(p) + ", " + await p.$eval("#bname", e => e.textContent));
  await p.screenshot({ path: path.join(out, "07-phone-result.png"), fullPage: true });
  log.push("phone overflow: " + JSON.stringify(await wide(p)));
  await p.click('[data-tab="paper"]'); await p.click("#paper-sample"); await sleep(1500);
  await p.screenshot({ path: path.join(out, "08-phone-paper.png"), fullPage: true });
  log.push("phone paper overflow: " + JSON.stringify(await wide(p)));
  await p.close();
  // The page's own self-test.
  p = await b.newPage(); p.on("pageerror", e => errs.push(e.message));
  await p.goto("file://" + path.join(__dirname, "..", "match-lab.html") + "#selftest", { waitUntil: "networkidle0" });
  await p.waitForFunction(() => document.getElementById("selftest") && document.getElementById("selftest").dataset.done, { timeout: 90000 });
  log.push("selftest:\n" + await p.$eval("#selftest", e => e.textContent));
  await b.close();
  log.push("errors: " + JSON.stringify(errs));
  fs.writeFileSync(path.join(out, "shots.log"), log.join("\n") + "\n");
  console.log(log.join("\n"));
})().catch(e => { console.error(e); process.exit(1); });
