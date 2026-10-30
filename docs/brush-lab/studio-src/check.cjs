// The click-through for studio.html (headless Chrome through puppeteer-core): no console errors, the workers draw, a
// dial changed moves the sweep and before/after, every tab works, codes round-trip, the page remembers, and it draws
// without workers too. node check.cjs → one line a check, then a count; exit 1 on any failure.
"use strict";
const path = require("path");
const PUP = [process.env.PUPPETEER_CORE, path.join(require("os").homedir(), "node_modules", "puppeteer-core"), "puppeteer-core"].filter(Boolean);
let puppeteer = null; for (const p of PUP) { try { puppeteer = require(p); break; } catch (e) { /* next */ } }
const CHROME = process.env.CHROME || "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const PAGE = "file://" + path.join(__dirname, "..", "studio.html");
const sleep = ms => new Promise(r => setTimeout(r, ms));
let n = 0, fail = 0;
const ok = (cond, what) => { n++; if (!cond) fail++; console.log((cond ? "ok   " : "FAIL ") + what); };
(async () => {
  const b = await puppeteer.launch({ executablePath: CHROME, headless: "new", args: ["--disable-gpu"] });
  const p = await b.newPage(), errs = [], net = [];
  p.on("console", m => { if (m.type() === "error" || m.type() === "warning") errs.push(m.type() + ": " + m.text()); });
  p.on("pageerror", e => errs.push("pageerror: " + e.message));
  p.on("request", r => { const u = r.url(); if (!/^(file|data|blob):/.test(u) && !/^https:\/\/fonts\.(googleapis|gstatic)\.com\//.test(u)) net.push(u); });
  await p.setViewport({ width: 1440, height: 940 });
  const open = async q => {
    await p.goto(PAGE + (q || ""), { waitUntil: "load" });
    await p.waitForFunction(() => document.documentElement.dataset.ready === "1", { timeout: 20000 });
  };
  const idle = () => p.waitForFunction(() => STUDIO.POOL.q.length === 0 && STUDIO.POOL.flight.size === 0, { timeout: 30000 }).then(() => sleep(150));
  const ev = (fn, ...a) => p.evaluate(fn, ...a);
  const click = async sel => { await ev(s => { const el = document.querySelector(s); if (!el) throw new Error("no " + s); el.click(); }, sel); await sleep(60); };
  // A canvas's pixels, hashed: to see that a picture was drawn again.
  const sig = sel => ev(s => [...document.querySelectorAll(s)].map(c => { if (!c.width) return "0"; const d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let h = 0x811c9dc5; for (let i = 0; i < d.length; i += 4) h = Math.imul(h ^ d[i], 16777619); return c.width + "x" + c.height + ":" + (h >>> 0).toString(36); }).join(" "), sel);
  const inked = sel => ev(s => { const c = document.querySelector(s); const d = c.getContext("2d").getImageData(0, 0, c.width, c.height).data; let k = 0; for (let i = 0; i < d.length; i += 4) if (d[i] < 200) k++; return k; }, sel);

  await open(); await ev(() => { try { localStorage.clear(); } catch (e) { } }); await open(); await idle();
  ok(await ev(() => STUDIO.POOL.workers.length > 0 && !STUDIO.POOL.inPage), "workers started (" + await ev(() => STUDIO.POOL.workers.length) + ")");
  ok(await ev(() => STUDIO.POOL.done > 20), "workers drew the tiles (" + await ev(() => STUDIO.POOL.done) + " pictures)");
  ok(await inked("#idloop") > 20, "the name's loop is drawn");
  ok(await ev(() => document.querySelectorAll("#cards .card").length) === 7, "all six parts and Erasable shown at once");
  ok(await ev(() => document.querySelectorAll("#cards input[type=range]").length) > 15, "every dial a slider");
  ok(await ev(() => document.querySelectorAll("#cards .dial.def").length > 5 && document.querySelectorAll("#cards .dial:not(.def)").length > 0), "dials at their defaults are quieter");
  ok(await ev(() => [...document.querySelectorAll("#cards canvas[data-sw]")].every(c => c.width > 0)), "every choice swatch drawn");
  ok(await ev(() => document.querySelectorAll("#sweep .tile").length) === 7, "the sweep has seven steps");
  ok(await ev(() => [...document.querySelectorAll("#tabs button")].map(b => b.textContent).join(",")) === "Community library,Random" && await ev(() => STUDIO.ST.tab) === "lab", "the top tabs: Community library, Random; Adjust first open");
  ok(await ev(() => [...document.querySelectorAll("#change button")].map(b => b.textContent + (b.getAttribute("aria-pressed") === "true" ? "*" : "")).join(",")) === "Adjust*,Close-up,Mix" && await ev(() => /^THIS BRUSH$/i.test(document.querySelector("#brushbar > h2").textContent) && document.querySelector("#s-lab h2").textContent === "Adjust" && !!document.querySelector("#s-lab [data-act=randomize]") && !!document.querySelector("#s-lab #stylebtn") && !document.querySelector("#brushbar [data-act=randomize]")), "the sidebar: This brush, then Adjust (filled), Close-up, Mix; Adjust heads the parts with Style and Randomize");
  ok(await ev(() => [...document.querySelectorAll("#cards .card")].every(c => { const d = c.querySelector(".cdesc"); return d && d.textContent.length > 10 && d.textContent.length < 50 && !/\byou/i.test(d.textContent); })), "each part card says what the part does, in one short line");
  ok(await ev(() => STUDIO.ST.lib.some(b => b.name === "zealous-shallot")), "the owner's favourite is in the library on first open");
  ok(await ev(() => STUDIO.ST.layout && STUDIO.ST.layout.n >= 2 && document.querySelectorAll("#cards .ccol").length === STUDIO.ST.layout.n), "the cards packed into " + await ev(() => STUDIO.ST.layout.n) + " columns" + (await ev(() => STUDIO.ST.layout.labels) ? ", swatches labelled" : ""));
  ok(await ev(() => { const l = document.querySelector(".partscol"); return l.scrollHeight <= l.clientHeight + 1; }), "every card in view, none scrolled");

  // ---------- a dial changed: the sweep follows it, before/after shows the change ----------
  const sweep0 = await sig("#sweep canvas");
  await ev(() => { const r = document.querySelector('#cards input[data-part="medium"][data-key="bloom"]'); r.value = String(+r.max * .9); r.dispatchEvent(new Event("input", { bubbles: true })); r.dispatchEvent(new Event("change", { bubbles: true })); });
  await idle();
  ok(await ev(() => STUDIO.ST.focus.key) === "bloom" && await ev(() => document.getElementById("sweeptitle").textContent) === "Bloom", "dragging a dial focuses it: the sweep is Bloom's");
  ok(await sig("#sweep canvas") !== sweep0, "the sweep tiles are drawn again");
  ok(/^Bloom .+ to .+/.test(await ev(() => document.getElementById("bawhat").textContent)), "before/after names the change: " + await ev(() => document.getElementById("bawhat").textContent));
  const pct = await ev(() => document.getElementById("aftercap").textContent);
  ok(/After: \d+% changed/.test(pct) && !/After: 0%/.test(pct), "changed pixels counted: " + pct);
  ok(await sig("#before") !== await sig("#after"), "before and after differ");
  const h0 = await ev(() => STUDIO.H.nodes.length);
  await click("#sweep .tile:first-child"); await idle();
  ok(await ev(() => STUDIO.ST.B.medium.d.bloom) === 0 && await ev(() => STUDIO.H.nodes.length) === h0 + 1, "a sweep tile takes its value, a history node");
  ok(await ev(() => document.querySelector("#sweep .tile:first-child").getAttribute("aria-pressed")) === "true", "the step in use is marked");
  // Another dial focused by its name: the sweep moves, before/after compares it with its default or far end.
  await click('#cards .dname[data-key="wet"]'); await idle();
  ok(await ev(() => document.getElementById("sweeptitle").textContent) === "Wet" && /^Wet /.test(await ev(() => document.getElementById("bawhat").textContent)), "a dial's name focuses it");
  await click('#cards .choice[data-part="paper"][data-k="cold press"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.paper.k) === "cold press" && /Paper: cold press/.test(await ev(() => STUDIO.H.nodes.at(-1).what)), "a choice swatch switches the part");
  await click('#cards .card[data-part="tip"] [data-act="rollpart"]'); await idle();
  ok(await ev(() => STUDIO.H.nodes.at(-1).what.startsWith("Tip:")), "a part's die rolls it");
  await click('#cards [data-act="lockpart"][data-id="tip"]');
  ok(await ev(() => STUDIO.ST.locks.parts.has("tip")), "a part's lock");
  ok(await ev(() => document.querySelector('#cards .card[data-part="tip"] [data-act="rollpart"]').disabled), "a locked part can't be rolled");
  await click('#cards [data-act="lockdial"][data-id="medium.erase"]');

  // ---------- Randomize, Undo, Style, and the dice, keeping every lock ----------
  const snap = () => ev(() => { const B0 = STUDIO.ST.B, B = STUDIO.readAnyCode(STUDIO.encodeM(B0)).b; B.name = B0.name; return { code: STUDIO.encodeM(B), name: B.name, style: B.style, parts: Object.fromEntries(["tip", "medium", "carry", "paper", "life", "hand"].map(p => [p, JSON.stringify(B[p])])), erase: B.medium.d.erase }; });
  ok(await ev(() => !!document.querySelector('#s-lab [data-act="randomize"] svg') && document.getElementById("stylename").textContent.startsWith("Style: ") && ["undo", "revert", "save", "library", "share"].every(a => document.querySelector(`#brushbar [data-act="${a}"]`)) && !!document.querySelector('#s-lab [data-act="styles"]') && !document.querySelector('#top [data-act="save"]')), "the sidebar: the loop and name, Undo, Revert, Save, Load, Share; Style and Randomize (with the dice) on Adjust");
  await ev(() => { STUDIO.ST.locks.parts.clear(); STUDIO.ST.locks.dials.clear(); STUDIO.ST.locks.parts.add("paper"); STUDIO.ST.locks.parts.add("hand"); STUDIO.ST.locks.dials.add("medium.erase"); STUDIO.refresh(); });
  let before = await snap(), hn = await ev(() => STUDIO.H.nodes.length), changedAll = true, keptAll = true;
  for (let i = 0; i < 6; i++) {
    const a = await snap();
    await click('#s-lab [data-act="randomize"]'); await idle();
    const b2 = await snap();
    if (b2.code === a.code) changedAll = false;
    if (b2.parts.paper !== a.parts.paper || b2.parts.hand !== a.parts.hand || b2.erase !== a.erase || b2.style !== a.style) keptAll = false;
  }
  ok(changedAll && await ev(() => STUDIO.H.nodes.length) === hn + 6, "Randomize rolls a new brush, one history step each");
  ok(keptAll, "Randomize keeps the style, locked parts (Paper, Control) and a locked dial (Erasable)");
  const r6 = await snap();
  await click('#brushbar [data-act="undo"]'); await idle();
  const u1 = await snap();
  ok(u1.code !== r6.code && await ev(() => STUDIO.H.nodes.find(n => n.id === STUDIO.H.cur).what) === "Randomized", "Undo steps back one roll");
  for (let i = 0; i < 5; i++) { await click('#brushbar [data-act="undo"]'); await idle(); }
  ok((await snap()).code === before.code, "Undo all the way back to before the rolls");
  await click("#stylebtn");
  ok(await ev(() => !document.getElementById("styles").hidden && document.querySelectorAll("#styles .chip").length === STYLE_NAMES.length), "the style picker lists every style");
  await click('#styles [data-style="Charcoal"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.style === "Charcoal" && STUDIO.ST.B.medium.k === "charcoal" && document.getElementById("styles").hidden), "picking a style rolls a brush in it");
  { const sp = await snap(); ok(sp.parts.paper === before.parts.paper, "a style's roll keeps the locked parts" + (sp.parts.paper === before.parts.paper ? "" : ": " + sp.parts.paper + " vs " + before.parts.paper)); }
  await click('#s-lab [data-act="randomize"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.style) === "Charcoal", "Randomize stays in the picked style");
  // A part's die: that part only, its dials rolled; a dial's die: that dial only.
  await ev(() => { STUDIO.ST.locks.parts.clear(); STUDIO.ST.locks.dials.clear(); STUDIO.setChoice("medium", "wash"); });
  await ev(() => STUDIO.commit(Object.assign(STUDIO.ST.B, {}), "x")); await idle();
  let partOnly = true, partMoved = 0;
  for (const part of ["tip", "medium", "life", "hand"]) {
    const a = await snap();
    await click(`#cards .card[data-part="${part}"] [data-act="rollpart"]`); await idle();
    const b2 = await snap();
    if (b2.parts[part] !== a.parts[part]) partMoved++;
    for (const q of Object.keys(a.parts)) if (q !== part && a.parts[q] !== b2.parts[q]) partOnly = false;
  }
  ok(partOnly && partMoved >= 3, "a part's die rolls that part only (" + partMoved + " of 4 moved)");
  const dkey = await ev(() => { const keys = choiceOf("medium", STUDIO.ST.B.medium.k).dials.filter(k => !DIALS.medium[k].choices && !DIALS.medium[k].hide && k !== "erase"); return keys[0]; });
  const da = await ev(() => JSON.parse(JSON.stringify(STUDIO.ST.B)));
  await click(`#cards [data-act="rolldial"][data-part="medium"][data-key="${dkey}"]`); await idle();
  ok(await ev((a, k) => { const B = STUDIO.ST.B; return Object.keys(B.medium.d).every(x => x === k || B.medium.d[x] === a.medium.d[x]) && ["tip", "carry", "paper", "life", "hand"].every(p => JSON.stringify(B[p]) === JSON.stringify(a[p])); }, da, dkey), "a dial's die rolls that dial only (" + dkey + ")");
  await click(`#cards [data-act="lockdial"][data-id="medium.${dkey}"]`);
  ok(await ev(k => document.querySelector(`#cards [data-act="rolldial"][data-part="medium"][data-key="${k}"]`).disabled, dkey), "a locked dial's die is off");
  const keptV = await ev(k => STUDIO.ST.B.medium.d[k], dkey);
  await click('#cards .card[data-part="medium"] [data-act="rollpart"]'); await idle();
  ok(await ev((k, v) => STUDIO.ST.B.medium.k !== "wash" || STUDIO.ST.B.medium.d[k] === v, dkey, keptV), "a part's die keeps its locked dial");
  await ev(() => { STUDIO.ST.locks.parts.clear(); STUDIO.ST.locks.dials.clear(); STUDIO.refresh(); });

  // ---------- Save shows its state; Revert goes back to what was saved ----------
  const saveTxt = () => ev(() => document.getElementById("savebtn").textContent.trim() + (document.getElementById("savebtn").classList.contains("primary") ? "|solid" : "|outline"));
  ok(await saveTxt() === "Save|solid", "Save is solid while there are changes to save");
  await click('#brushbar [data-act="save"]');
  ok(await saveTxt() === "Saved|outline" && await ev(() => STUDIO.isSaved()), "Saved, outlined, once saved");
  ok(await ev(() => document.getElementById("revertbtn").disabled), "Revert off with nothing to revert");
  const savedCode = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await ev(() => STUDIO.setChoice("paper", "felt")); await idle();
  ok(await saveTxt() === "Save|solid" && !(await ev(() => document.getElementById("revertbtn").disabled)), "a change: Save solid again, Revert on");
  const hr = await ev(() => STUDIO.H.nodes.length);
  await click('#brushbar [data-act="revert"]'); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === savedCode && await ev(() => STUDIO.H.nodes.length) === hr + 1 && await ev(() => STUDIO.H.nodes.at(-1).what) === "Reverted", "Revert returns to the saved brush, as a history step");
  await click('#brushbar [data-act="undo"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.paper.k) === "felt", "Revert can be undone");
  await click('#brushbar [data-act="revert"]'); await idle();

  // ---------- the test page ----------
  const r = await ev(() => { const b = document.getElementById("test").getBoundingClientRect(); return [b.x, b.y, b.width, b.height]; });
  const ink0 = await inked("#test");
  await p.mouse.move(r[0] + r[2] * .4, r[1] + r[3] * .7); await p.mouse.down();
  for (let i = 1; i <= 30; i++) { const u = i / 30; await p.mouse.move(r[0] + r[2] * (.4 + .5 * u), r[1] + r[3] * (.7 - .4 * Math.sin(u * 4))); await sleep(5); }
  await p.mouse.up(); await sleep(200);
  ok(await inked("#test") > ink0 + 200, "drawing on the test page");
  ok(await ev(() => STUDIO.TP.log.length) === 1 && !(await ev(() => document.getElementById("lastbtn").disabled)), "the stroke is kept for Close-up");
  const t1 = await sig("#test");
  await click('#cards .choice[data-part="medium"][data-k="ink"]'); await sleep(200);
  ok(await sig("#test") !== t1, "the test page is drawn again with the new brush");
  await click('[data-act="eraser"]');
  await p.mouse.move(r[0] + r[2] * .5, r[1] + r[3] * .2); await p.mouse.down();
  for (let i = 1; i <= 20; i++) { await p.mouse.move(r[0] + r[2] * (.5 + .02 * i), r[1] + r[3] * (.2 + .03 * i)); await sleep(5); }
  await p.mouse.up(); await sleep(100);
  ok(await ev(() => STUDIO.TP.log.at(-1).er === true), "the eraser");
  await click('[data-act="eraser"]');
  await click('[data-act="clear"]');
  ok(await ev(() => STUDIO.TP.log.length) === 0, "Clear");

  // ---------- Close-up ----------
  await click('[data-tab="tune"]'); await idle();
  ok(await inked("#strip") > 100, "the stroke is drawn");
  ok(await inked("#close") > 100, "the close-up shows its pixels");
  const k0 = await ev(() => STUDIO.TUNE.key), s0 = await sig("#strip");
  const cr = await ev(() => { const b = document.querySelector('.curve[data-c="P"] .frame').getBoundingClientRect(); return [b.x, b.y, b.width, b.height]; });
  await p.mouse.move(cr[0] + cr[2] * .5, cr[1] + cr[3] * .2); await p.mouse.down();
  for (let i = 1; i <= 8; i++) { await p.mouse.move(cr[0] + cr[2] * .5, cr[1] + cr[3] * (.2 + .08 * i)); await sleep(20); }
  await p.mouse.up(); await idle();
  ok(await ev(() => STUDIO.TUNE.key) !== k0 && await sig("#strip") !== s0, "dragging the pressure curve draws the stroke again");
  for (const z of [1, 4]) { const a = await sig("#close"); await click(`#zooms [data-z="${z}"]`); ok(await sig("#close") !== a, z + "× close-up"); }
  await click('[data-act="laststroke"]'); await idle();
  ok(await ev(() => STUDIO.TUNE.geo.length) > 10, "Use my last stroke");
  ok(await ev(() => document.querySelectorAll("#hist button").length) === await ev(() => STUDIO.H.nodes.length), "every change in the history");
  const firstCode = await ev(() => STUDIO.H.nodes[0].code);
  await click("#hist li:first-child button"); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === firstCode, "jumping back in the history");
  await click('#cards .choice[data-part="paper"][data-k="laid"]').catch(() => { });
  await ev(() => STUDIO.setChoice("paper", "laid")); await idle();
  ok(await ev(() => { const n = STUDIO.H.nodes.at(-1); return n.parent === STUDIO.H.nodes[0].id; }), "a change after jumping back branches");
  ok(await ev(() => [...document.querySelectorAll("#hist button")].some(b => parseInt(b.style.paddingLeft) > 10)), "the branch is indented");
  await click('[data-act="comparepick"]'); await click("#hist li:nth-child(1) button"); await click("#hist li:nth-child(2) button"); await idle();
  ok(await ev(() => !document.getElementById("comparesec").hidden && document.querySelectorAll("#compare figure").length === 2), "compare two side by side");
  const tn = await ev(() => STUDIO.H.nodes.length), tc = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await click('#s-lab [data-act="randomize"]'); await idle();
  ok(await ev(() => STUDIO.H.nodes.length) === tn + 1 && await ev(() => STUDIO.encodeM(STUDIO.ST.B)) !== tc && await ev(() => document.querySelector("#hist button.cur .what").textContent) === "Randomized", "a Randomize in Close-up's history: a new step, shown");
  await click('#brushbar [data-act="undo"]'); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === tc, "Close-up's Undo");
  const libN = await ev(() => STUDIO.ST.lib.length);
  await click('[data-act="saveasnew"]');
  ok(await ev(() => STUDIO.ST.lib.length) === libN + 1, "save as new: " + await ev(() => STUDIO.ST.B.name));

  // ---------- Mix ----------
  await click('[data-tab="mix"]'); await idle();
  ok(await ev(() => document.querySelectorAll("#cross .tile").length) === await ev(() => CHOICES.tip.length * CHOICES.medium.length), "every tip × every medium (" + await ev(() => CHOICES.tip.length + " × " + CHOICES.medium.length) + ")");
  ok(await ev(() => [...document.querySelectorAll("#cross canvas")].every(c => c.width > 0)), "every cross tile drawn");
  await ev(() => { const s = document.getElementById("crosscol"); s.value = "paper"; s.dispatchEvent(new Event("change", { bubbles: true })); }); await idle();
  ok(await ev(() => document.querySelectorAll("#cross .tile").length) === await ev(() => CHOICES.tip.length * CHOICES.paper.length), "rows and columns can be any two parts (tips × papers)");
  await click('#cross .tile[data-r="hake"][data-c="canvas"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.tip.k === "hake" && STUDIO.ST.B.paper.k === "canvas"), "a cross tile is tried");
  // ---------- Roll ----------
  await click('[data-tab="roll"]'); await idle();
  ok(await ev(() => document.querySelectorAll("#wild .tile").length === 12 && /12 random brushes/.test(document.querySelector("#s-roll h2").textContent)), "Roll: 12 random brushes, said so");
  ok(await ev(() => { const s = document.getElementById("s-roll"), btn = s.querySelector('[data-act="wild"]'); return btn.textContent === "Generate other random brushes" && btn.getBoundingClientRect().top > document.getElementById("wild").getBoundingClientRect().top && document.getElementById("wildlocks").getBoundingClientRect().top < document.getElementById("wild").getBoundingClientRect().top && !document.querySelector('#brushbar [data-act="randomize"], #brushbar [data-act="styles"]') && ![...document.querySelectorAll("#change button")].some(b => b.getAttribute("aria-pressed") === "true"); }), "Random: the button at the foot of the grid, locks above, no sidebar button filled");
  const tipNow = await ev(() => STUDIO.ST.B.tip.k);
  await ev(() => STUDIO.ST.locks.parts.clear()); await click('#wildlocks [data-part="tip"]');
  await click('[data-act="wild"]'); await idle();
  ok(await ev(t => STUDIO.ST.wild.every(b => b.tip.k === t), tipNow), "a locked part stays in every roll");
  ok(await ev(() => [...document.querySelectorAll("#wild canvas")].every(c => c.width > 0)), "every roll drawn");
  await click('#wild .tile[data-i="3"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.name === STUDIO.ST.wild[3].name), "a roll is tried");

  // ---------- Share (a panel from the brush bar) and the Community library ----------
  await click('#brushbar [data-act="share"]');
  ok(await ev(() => !document.getElementById("sharepanel").hidden && document.getElementById("qr").width > 100), "Share opens the code and its QR code");
  const code = await ev(() => document.getElementById("code").value);
  ok(await ev(c => { const r = STUDIO.readAnyCode(c); return !!r && STUDIO.encodeM(r.b) === c && c === STUDIO.encodeM(STUDIO.ST.B); }, code), "the brush code is this brush's and reads back to itself");
  ok(await ev(() => { const b = document.getElementById("pubbtn"); return /Publish to Community library/.test(b.textContent) && b.disabled && document.getElementById("pubnote").textContent === "Sign in to share brushes."; }), "without db: Publish to Community library off, sign in to share");
  ok(await ev(() => { const b = document.getElementById("dltablet"); return !!b && /Download for tablet/.test(b.textContent) && /Document\/Scribulor/.test(document.getElementById("sharepanel").textContent); }), "Share has Download for tablet, with where to save it");
  await click('#dltablet');
  ok(await ev(() => STUDIO.ST.lastDownload === STUDIO.ST.B.name.replace(/[\\/:*?"<>|]+/g, "").trim() + ".inkwash.txt"), "Download for tablet names the file <brush>.inkwash.txt");
  ok(await ev(() => document.getElementById("exportbtn").textContent === "Export" && /^A Procreate set of this brush and the saved ones\. Drying and smudge stay in Scribulor\.$/.test(document.getElementById("exportnote").textContent)), "Share has Export, a Procreate set, saying what stays behind");
  await click('#exportbtn');
  await p.waitForFunction(() => STUDIO.ST.lastExport, { timeout: 60000 });
  ok(await ev(() => { const x = STUDIO.ST.lastExport; return x.file === "Scribulor.brushset" && x.n === STUDIO.exportList().length && x.n >= 2 && x.bytes > 20000 && document.getElementById("status").textContent === "Set exported." && !document.getElementById("exportbtn").disabled; }),
    "Export downloads Scribulor.brushset: this brush and the saved ones (" + await ev(() => JSON.stringify(STUDIO.ST.lastExport).slice(0, 200)) + ")");
  await click('#sharepanel [data-act="share"]');
  ok(await ev(() => document.getElementById("sharepanel").hidden), "Share closes");
  await click('[data-act="library"]');
  await ev(() => { document.getElementById("paste").value = "nonsense"; }); await click('[data-act="loadcode"]');
  ok(await ev(() => document.getElementById("loadnote").textContent) === "That isn't a brush code.", "a bad code is refused");
  const other = await ev(() => STUDIO.encodeM(STUDIO.BASICS[1]));
  await ev(c => { document.getElementById("paste").value = c; }, other); await click('[data-act="loadcode"]'); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === other && await ev(() => document.getElementById("library").hidden), "Load a code, from the library");
  await click('[data-tab="community"]'); await idle();
  ok(await ev(() => document.querySelectorAll("#sharedgrid article").length === 8 && [...document.querySelectorAll("#sharedgrid .who")].every(w => w.textContent === "Example") && /Community library/.test(document.querySelector("#s-community h2").textContent) && !document.getElementById("commtag").hidden && document.getElementById("commtag").textContent === "Examples" && document.getElementById("commnote").textContent === "Sign in to see and share brushes."), "without db: the Community library shows eight examples, so labelled, and sign in");
  const ex = await ev(() => document.querySelector("#sharedgrid .tname").textContent);
  await click('#sharedgrid [data-act="extry"]'); await idle();
  ok(await ev(() => STUDIO.ST.tab) === "lab" && await ev(() => STUDIO.ST.B.name) === ex, "Try puts a community brush in the Lab");

  // ---------- the library and remembering ----------
  await click('[data-act="save"]');
  await click('[data-act="library"]');
  ok(await ev(() => !document.getElementById("library").hidden && document.querySelectorAll("#libbody .libitem").length >= 10), "the library opens");
  await click('#libbody [data-act="libload"][data-src="b"][data-i="0"]'); await idle();
  ok(await ev(() => STUDIO.ST.B.name) === "Ink", "a basic tool loads");
  const keep = await ev(() => STUDIO.encodeM(STUDIO.ST.B));
  await open(); await idle();
  ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === keep && await ev(() => STUDIO.ST.lib.length) >= 2 && await ev(() => STUDIO.H.nodes.length) > 5, "remembered after a reload");
  ok(await ev(() => STUDIO.ST.tab) === "lab", "the tab remembered");
  // The link's brush: studio.html#<share code> opens it (a Print tool), and a new hash opens that one (a Wet one).
  { const benDay = "ben-day-brush.Me1zzeezz00000000ll_0i0000000000000000zz065ji000at_0_000_0_25ess00000000000000nz0.2ef",
      wet = "wet-gouache.M80b70000000054_2zz00_0_000_7_83z0000000000000000nz0.2fj";
    await p.goto("about:blank"); await open("#" + benDay); await idle();
    ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === benDay && await ev(() => STUDIO.ST.B.style) === "Print", "the link's code opens: " + await ev(() => STUDIO.ST.B.name));
    await ev(c => { location.hash = c; }, wet); await sleep(300); await idle();
    ok(await ev(() => STUDIO.encodeM(STUDIO.ST.B)) === wet && await ev(() => STUDIO.ST.B.life.k) === "wetpaint", "a new hash opens its brush: " + await ev(() => STUDIO.ST.B.name));
    await p.goto("about:blank"); await open(); await idle(); }

  // ---------- without workers ----------
  await ev(() => STUDIO.setTab("lab"));
  await open("?inpage"); await idle();
  ok(await ev(() => STUDIO.POOL.inPage && STUDIO.POOL.workers.length === 0 && STUDIO.POOL.done > 10), "draws on the page without workers");
  ok(await ev(() => [...document.querySelectorAll("#sweep canvas")].every(c => c.width > 0)), "the sweep without workers");

  // ---------- narrow screens ----------
  for (const w of [1280, 1600, 1024, 768, 390]) {
    await p.setViewport({ width: w, height: 900 }); await sleep(400);
    for (const t of ["community", "roll", "lab", "tune", "mix"]) {
      await ev(x => STUDIO.setTab(x), t); await sleep(150);
      const over = await ev(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      if (over > 0) ok(false, `${t} at ${w} px: ${over} px too wide`);
    }
    ok(true, "no sideways scroll at " + w + " px");
  }

  // ---------- the Community library with the artifact's db: a fake window.claude.use (db and user) ----------
  const codes = await ev(() => [1, 2, 4].map(i => STUDIO.encodeM(STUDIO.BASICS[i])));
  const now = Date.now(), XSS = "<img src=x onerror=__xss=1>";
  const seed = {
    u_ann: { list: [{ code: codes[0], name: XSS, at: now - 1000 }, { code: "<script>alert(1)</script>", name: "bad", at: now }, { code: 12, at: now }, { code: codes[1], name: "Old one", at: now - 90000 }] },
    u_bob: { list: [{ code: codes[2], at: now - 5000 }] },
    u_zed: { list: "nope" }
  };
  const p2 = await b.newPage(), errs2 = [];
  p2.on("console", m => { if (m.type() === "error" || m.type() === "warning") errs2.push(m.type() + ": " + m.text()); });
  p2.on("pageerror", e => errs2.push("pageerror: " + e.message));
  await p2.setViewport({ width: 1440, height: 940 });
  await p2.evaluateOnNewDocument(seed => {
    const ME = "u_me", store = new Map(Object.entries(seed)), subs = new Set();
    window.__store = store; window.__subs = 0; window.__writes = 0;
    const meta = { fromCache: false, hasPendingWrites: false };
    const snap = () => { const docs = [...store.keys()].sort().map(id => { const body = JSON.parse(JSON.stringify(store.get(id))); return Object.freeze({ id, exists: true, data: () => body, metadata: meta }); }); return { docs, size: docs.length, empty: !docs.length, docChanges: () => [], metadata: meta }; };
    const notify = () => setTimeout(() => subs.forEach(f => f(snap())), 5);
    const fail = code => Promise.reject({ code, message: code });
    const ref = id => ({ id, path: "brushes/" + id,
      get: async () => ({ id, exists: store.has(id), data: () => store.get(id), metadata: meta }),
      set: async data => { window.__writes++; if (id !== ME || window.__readonly) return fail("invalid_argument"); store.set(id, JSON.parse(JSON.stringify(data))); notify(); },
      update: async () => fail("invalid_argument"),
      delete: async () => { window.__writes++; if (id !== ME) return fail("invalid_argument"); store.delete(id); notify(); } });
    const query = { where: () => query, orderBy: () => query, limit: () => query, get: async () => snap(),
      onSnapshot(next) { window.__subs++; subs.add(next); setTimeout(() => next(snap()), 5); return () => subs.delete(next); } };
    const coll = Object.assign({ path: "brushes", doc(id) { if (typeof id !== "string" || !/^[A-Za-z0-9_.~:@+-]+$/.test(id)) throw new TypeError("bad path"); return ref(id); } }, query);
    const db = Object.freeze({ collection: path => { if (path !== "brushes") throw new TypeError("unexpected " + path); return coll; }, doc: () => { throw new TypeError("unexpected"); } });
    const names = { u_me: "Elling", u_ann: "<b>Ann</b>" };
    const user = Object.freeze({ id: async () => ME, can: async () => true, isOwner: async () => false, canEdit: async () => false,
      profiles: async ids => Object.fromEntries([].concat(ids).map(id => [id, { id, name: names[id] || "", avatarUrl: "", color: "#888888", email: null, isMe: id === ME, guest: false }])) });
    window.claude = Object.freeze({ use: async name => name === "db" ? db : name === "user" ? user : null });
  }, seed);
  const ev2 = (fn, ...a) => p2.evaluate(fn, ...a);
  const click2 = async sel => { await ev2(s => { const el = document.querySelector(s); if (!el) throw new Error("no " + s); el.click(); }, sel); await sleep(80); };
  const idle2 = () => p2.waitForFunction(() => STUDIO.POOL.q.length === 0 && STUDIO.POOL.flight.size === 0, { timeout: 30000 }).then(() => sleep(150));
  await p2.goto(PAGE, { waitUntil: "load" });
  await p2.waitForFunction(() => document.documentElement.dataset.ready === "1" && STUDIO.COMM.state === "live" && STUDIO.COMM.items.length > 0, { timeout: 20000 });
  await ev2(() => STUDIO.setTab("community")); await sleep(100); await idle2();
  ok(await ev2(() => STUDIO.COMM.items.length === 3 && document.querySelectorAll("#sharedgrid article").length === 3), "db: the published brushes listed, rows that don't read as a brush left out");
  ok(await ev2(n => { const t = [...document.querySelectorAll("#sharedgrid .tname")].map(e => e.textContent), at = STUDIO.COMM.shown.map(x => x.at); return t[0] === n && t[2] === "Old one" && at[0] > at[1] && at[1] > at[2]; }, XSS), "db: newest first");
  ok(await ev2(x => !document.querySelector("#sharedgrid img, #sharedgrid b, #sharedgrid script") && window.__xss === undefined && document.querySelector("#sharedgrid .tname").textContent === x, XSS), "db: names and who shown as text, never as markup");
  ok(await ev2(() => [...document.querySelectorAll("#sharedgrid .who")].map(e => e.textContent).join("|") === "by <b>Ann</b>|by someone|by <b>Ann</b>"), "db: by whom, from user.profiles: " + await ev2(() => [...document.querySelectorAll("#sharedgrid .who")].map(e => e.textContent).join("|")));
  ok(await ev2(() => document.getElementById("commtag").hidden && document.getElementById("commnote").textContent === "" && !document.querySelector('#sharedgrid [data-act="commremove"]')), "db: no Examples tag; nobody else's card can be removed");
  ok(await ev2(() => [...document.querySelectorAll("#sharedgrid canvas[data-cm]")].every(c => c.width > 0)), "db: every published brush's loop drawn");
  const tried = await ev2(() => STUDIO.COMM.shown[0].name);
  await click2('#sharedgrid [data-act="commtry"][data-i="0"]'); await idle2();
  ok(await ev2(n => STUDIO.ST.tab === "lab" && STUDIO.ST.B.name === n && STUDIO.encodeM(STUDIO.ST.B) === STUDIO.encodeM(Object.assign(STUDIO.readAnyCode(STUDIO.COMM.shown[0].code).b, { name: n })), tried), "db: Try makes it This brush, on Adjust");
  await click2('#brushbar [data-act="share"]');
  ok(await ev2(() => { const b = document.getElementById("pubbtn"); return !b.disabled && !b.hidden && b.textContent === "Publish to Community library" && document.getElementById("pubconfirm").hidden && document.getElementById("pubnote").textContent === ""; }), "db: Publish to Community library is live");
  await click2("#pubbtn");
  ok(await ev2(() => !document.getElementById("pubconfirm").hidden && document.getElementById("pubbtn").hidden && window.__writes === 0), "db: Publish asks first, inline, nothing written yet");
  await click2('[data-act="pubno"]');
  ok(await ev2(() => document.getElementById("pubconfirm").hidden && !document.getElementById("pubbtn").hidden && window.__writes === 0), "db: Cancel writes nothing");
  await ev2(() => STUDIO.randomize()); await idle2();
  const pubCode = await ev2(() => STUDIO.encodeM(STUDIO.ST.B)), pubName = await ev2(() => STUDIO.ST.B.name);
  await click2("#pubbtn"); await click2('[data-act="pubyes"]');
  await p2.waitForFunction(() => window.__store.has("u_me") && !STUDIO.COMM.busy, { timeout: 5000 });
  ok(await ev2((c, nm) => { const d = window.__store.get("u_me"), x = d.list[0]; return window.__writes === 1 && d.list.length === 1 && x.code === c && x.name === nm && typeof x.at === "number" && Object.keys(d).join() === "list" && Object.keys(x).sort().join() === "at,code,name"; }, pubCode, pubName), "db: Publish writes one row in the viewer's own document (code, name, time; no names of people)");
  await sleep(100);
  ok(await ev2(() => { const b = document.getElementById("pubbtn"); return b.disabled && b.textContent === "Published" && document.getElementById("status").textContent === "Published."; }), "db: then Published, said so");
  await ev2(() => STUDIO.setTab("community")); await sleep(150);
  ok(await ev2(n => document.querySelector("#sharedgrid .tname").textContent === n && document.querySelector("#sharedgrid .who").textContent === "by Elling" && document.querySelectorAll('#sharedgrid [data-act="commremove"]').length === 1 && !!document.querySelector('#sharedgrid article:first-child [data-act="commremove"]'), pubName), "db: the viewer's brush first, by them, with Remove");
  ok(await ev2(() => window.__subs) === 1, "db: subscribed once, however often the page draws");
  await click2('#sharedgrid [data-act="commremove"]');
  await p2.waitForFunction(() => !window.__store.has("u_me") && !STUDIO.COMM.busy, { timeout: 5000 }); await sleep(100);
  ok(await ev2(() => window.__writes === 2 && document.querySelectorAll("#sharedgrid article").length === 3 && !document.querySelector('#sharedgrid [data-act="commremove"]') && document.getElementById("status").textContent === "Removed."), "db: Remove takes it out with one write");
  await ev2(() => { window.__readonly = true; }); await click2('#brushbar [data-act="share"]'); await click2('#brushbar [data-act="share"]');
  await click2("#pubbtn"); await click2('[data-act="pubyes"]');
  await p2.waitForFunction(() => !STUDIO.COMM.busy, { timeout: 5000 }); await sleep(100);
  ok(await ev2(() => STUDIO.COMM.readOnly && document.getElementById("pubbtn").disabled && document.getElementById("pubnote").textContent === "Contributor access needed." && document.getElementById("status").textContent === "Not published." && !window.__store.has("u_me")), "db: a refused write turns Publish off for the visit");
  for (const w of [1440, 390]) {
    await p2.setViewport({ width: w, height: 900 }); await ev2(() => STUDIO.setTab("community")); await sleep(300);
    ok(await ev2(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth && (innerWidth < 1180 || document.documentElement.scrollHeight <= innerHeight + 1)), "db: the Community library fits at " + w + " px");
  }
  ok(!errs2.length, "db: no console errors" + (errs2.length ? ": " + errs2.slice(0, 5).join(" | ") : ""));
  await p2.close();
  await idle();
  ok(!errs.length, "no console errors" + (errs.length ? ": " + errs.slice(0, 5).join(" | ") : ""));
  ok(!net.length, "nothing loaded from the network but the fonts" + (net.length ? ": " + net.join(", ") : ""));
  await b.close();
  console.log(`${n - fail} of ${n} passed`);
  process.exit(fail ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
