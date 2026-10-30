// node noisecheck.cjs <node-bundle.js>
// Pass or fail (exit 1): a ragged edge, a hand's wobble and a noisy camera (samples.js's ragged, wobble, noise) leave
// the fit's tip and medium as they were on the clean photo, read as smooth and keep Wobble low (analyze.js's STEADY); a pencil's grainy
// edge still reads rough; a tip chosen is the only one tried; the categories hold every drawn tool once.
const path = require("path"), K = require("./testkit.cjs");
const E = K.engine(path.resolve(process.argv[2]));
const ROUGH = { ragged: 1.2, wobble: 1.5, noise: .035 }, BUDGET = 3000;
const gray = s => { const g = new Float32Array(s.w * s.h); for (let i = 0; i < g.length; i++) g[i] = (s.px[i * 3] * .299 + s.px[i * 3 + 1] * .587 + s.px[i * 3 + 2] * .114) / 255; return { w: s.w, h: s.h, g }; };
const jobOf = (name, extra) => { const p = E.preparePhoto(gray(E.makeSample(name, extra))); return E.analyzeStroke(p, p.crop); };
const words = st => st.tip + "/" + (st.wetInk ? "wet ink" : st.medium);
let bad = 0;
const check = (ok, msg) => { console.log((ok ? "ok    " : "FAIL  ") + msg); if (!ok) bad++; };
for (const name of ["e2-brush-pen", "e9-flat-end", "d7-low-contrast", "d8-flat-brush"]) {
  const a = jobOf(name), b = jobOf(name, ROUGH), fa = E.fitNow(a, { budgetMs: BUDGET }), fb = E.fitNow(b, { budgetMs: BUDGET });
  check(words(fa.struct) === words(fb.struct), `${name}: clean ${words(fa.struct)}, ragged ${words(fb.struct)}`);
  check(E.roughExcess(b.F) === 0, `${name}: ragged edge ${b.F.rough.toFixed(2)} px reads smooth`);
  const wob = fb.x["tip.wobble"] || 0;
  check(wob <= .1, `${name}: ragged, Wobble ${wob.toFixed(2)} (clean ${(fa.x["tip.wobble"] || 0).toFixed(2)})`);
}
const pencil = jobOf("d3-pencil");
check(E.roughExcess(pencil.F) > 0, `d3-pencil: grainy edge ${pencil.F.rough.toFixed(2)} px reads rough`);
const chosen = E.fitNow(jobOf("e2-brush-pen"), { budgetMs: BUDGET, tip: "chisel" });
check(chosen.tried.every(t => t.words.startsWith("chisel")) && chosen.struct.tip === "chisel", `e2-brush-pen, Chisel chosen: ${words(chosen.struct)}`);
const names = E.DRAWN_TOOLS.map(t => t[0]), inCats = E.CATEGORIES.flatMap(c => c.tools);
check(names.every(n => inCats.filter(m => m === n).length === 1) && inCats.length === names.length, "categories hold every drawn tool once");
process.exit(bad ? 1 : 0);
